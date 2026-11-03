package com.pleos.routine.execution

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.constants.NaviErrorCode
import com.pleos.routine.*
import com.pleos.routine.core.*
import com.pleos.routine.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID

data class Approval(val title:String,val detail:String,val answer:CompletableDeferred<Boolean>)
class RoutineRuntime(private val app:RoutineApplication) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val dao get()=app.database.routines()
    private val journal by lazy { RunJournalRepository(app.database) }
    private val recovery by lazy { scope.async(Dispatchers.IO) { journal.recover() } }
    private var vehicleEffectsStarted=false
    private var running:Job?=null
    private var monitoring:Job?=null
    private val trigger=TriggerMonitor()
    val busy=MutableStateFlow(false)
    val approval=MutableStateFlow<Approval?>(null)
    val events=MutableStateFlow<List<ExecutionEvent>>(emptyList())
    val lastResult=MutableStateFlow<RunResult?>(null)
    val notice=MutableStateFlow("")
    private val timerState=MutableStateFlow<List<TimerProgress>>(emptyList())
    val timers=timerState.asStateFlow()
    var visible=false
    private var service:RoutineService?=null
    private var pending:Pair<WorkflowDefinition,RunStartCause>?=null
    val audioState=MutableStateFlow("")
    private val audio=OwnedAudio({ service?.mediaType(true);Media3OwnedAudioPort(app,onState={ audioState.value=it }) },SystemClock::elapsedRealtime)
    internal val isOwnAudioPlaying:Boolean get()=audio.isPlaying
    private var nav:NaviHelper?=null
    private var navReady=false
    fun recover() { recovery.start() }
    fun requestRun(w:WorkflowDefinition,cause:RunStartCause=RunStartCause.MANUAL) {
        if(busy.value || pending!=null) { notice.value="다른 루틴이 실행 중입니다.";return }
        pending=w to cause
        try { ContextCompat.startForegroundService(app,Intent(app,RoutineService::class.java)) } catch(_:Exception) { pending=null;notice.value="앱을 열고 실행해 주세요. 백그라운드 시작이 허용되지 않았습니다." }
    }
    fun startMonitoring() { try { ContextCompat.startForegroundService(app,Intent(app,RoutineService::class.java)) } catch(_:Exception) { notice.value="앱을 열어 자동 감시를 다시 시작하세요." } }
    fun attach(s:RoutineService) {
        service=s
        if(monitoring==null)monitoring=scope.launch {
            while(isActive) {
                val x=app.platform.snapshot()
                val enabled=withContext(Dispatchers.IO) { dao.enabled() }
                val eligible=enabled.mapNotNull { row -> runCatching { WorkflowJson.decode(row.definition) }.getOrNull()?.takeIf {
                    row.testedHash==WorkflowJson.semanticHash(it) && WorkflowValidator.validate(it).isEmpty()
                }?.let { row to it } }
                trigger.syncActive(eligible.map { it.second })
                val candidates=eligible.filter { trigger.evaluate(it.second,x) }
                if(candidates.isNotEmpty() && !busy.value) {
                    val highest=candidates.maxOf { it.first.priority }
                    val top=candidates.filter { it.first.priority==highest }
                    if(top.size==1)launchRun(top.single().second,RunStartCause.AUTOMATIC) else notice.value="같은 우선순위의 루틴이 동시에 충족되었습니다. 우선순위를 정해 주세요."
                }
                delay(500)
            }
        }
        pending?.let { pending=null;launchRun(it.first,it.second) }
    }
    fun detach() { service=null;monitoring?.cancel();monitoring=null;trigger.reset();cancel() }
    fun cancel() { pending=null;approval.value?.answer?.complete(false);running?.cancel();scope.launch { app.platform.stopSeat() } }
    suspend fun confirm(title:String,detail:String):Boolean {
        if(!visible) { notice.value="앱을 열고 $title 요청을 다시 실행하세요.";return false }
        val q=Approval(title,detail,CompletableDeferred())
        approval.value=q
        return try { withTimeoutOrNull(30_000) { q.answer.await() } ?: false } finally { if(approval.value===q)approval.value=null }
    }
    private fun launchRun(w:WorkflowDefinition,cause:RunStartCause) {
        if(busy.value)return
        busy.value=true;events.value=emptyList();timerState.value=emptyList();notice.value="";vehicleEffectsStarted=false
        trigger.markStarted(w,SystemClock.elapsedRealtime())
        running=scope.launch {
            val journalId=UUID.randomUUID().toString()
            var guard:Job?=null
            var terminalPersisted=false
            try {
                recovery.await()
                withContext(Dispatchers.IO) { journal.begin(w,journalId,cause) }
                guard=launch { while(isActive) { delay(150);if(vehicleEffectsStarted && !app.platform.snapshot().parkedAndStationary()) { running?.cancel();break } } }
                val result=WorkflowEngine(Adapter(w,journalId),{app.platform.snapshot()},nowMillis=SystemClock::elapsedRealtime,
                    onTimerProgress={ progress ->
                        timerState.value=timerState.value.filterNot { it.key==progress.key }+progress
                        service?.refreshTimerNotification()
                        if(progress.completed && service?.timerCompleted(w.title,progress)!=true)
                            notice.value="타이머가 완료되었습니다. 알림 권한 또는 채널 설정을 확인하세요."
                    }).run(w,journalId) {
                    withContext(Dispatchers.IO) { journal.append(it) }
                    events.value=events.value+it
                }
                lastResult.value=result
                withContext(NonCancellable+Dispatchers.IO) {
                    journal.finish(result)
                    terminalPersisted=true
                }
            } catch(_:Exception) {
                // Returning from non-cancellable IO into a cancelled Main job throws again.
                // Preserve the terminal result already committed by the engine cleanup.
                if(!terminalPersisted) {
                    notice.value="실행 결과를 확인하지 못했습니다. 과거 명령은 재전송하지 않습니다."
                    withContext(NonCancellable+Dispatchers.IO) { runCatching { journal.unknown(journalId) } }
                }
            } finally {
                guard?.cancel();timerState.value=timerState.value.filter { it.completed }
                service?.refreshTimerNotification();busy.value=false;running=null
            }
        }
    }
    private inner class Adapter(private val workflow:WorkflowDefinition,private val runId:String):ActionAdapter {
        override fun activeFailure()=audio.activeFailure()
        private val authorization=SeatRunAuthorization.Session(workflow,runId,app.platform::connectionGeneration,
            app.platform::snapshot,app.platform::seatApprovalPlan,app.platform::seatApprovalKey,::confirm)
        override suspend fun authorizeRun(workflow:WorkflowDefinition,runId:String,context:ExecutionContext):ActionResult? {
            if(workflow!=this.workflow || runId!=this.runId)return ActionResult(ActionStatus.BLOCKED,"실행 승인 대상이 일치하지 않습니다.")
            return authorization.authorize(context)
        }
        override suspend fun preflight(action:ActionSpec,context:ExecutionContext):ActionResult? {
            authorization.validate(workflow,runId)?.let { return it }
            app.platform.preflight(action,context)?.let { return it }
            if(context.source!=InputSource.REAL)return ActionResult(ActionStatus.BLOCKED,"가상 입력으로 실제 기능을 호출할 수 없습니다.")
            // Driving allowance requires a separately verified provider/app capability.
            if(context.uxRestricted)return ActionResult(ActionStatus.BLOCKED,"주행 허용이 검증된 연결이 없어 실제 실행을 보류합니다.")
            if(action.type in setOf(ActionType.OPEN_APP,ActionType.MEDIA_CONTROL) && action.targetId.isNullOrBlank())return ActionResult(ActionStatus.BLOCKED,"연결할 앱을 선택하세요.")
            return null
        }
        override suspend fun execute(request:ActionRequest,onProgress:suspend (ActionProgress)->Unit):ActionResult {
            authorization.validate(workflow,request.runId)?.let { return it }
            val a=request.action
            if(a.type in ActionResources.parkedTypes) {
                vehicleEffectsStarted=true
                return app.platform.execute(a,beforeDispatch={ authorization.beforeDispatch(request) },progress=onProgress)
            }
            return when(a.type) {
                ActionType.NAVIGATE,ActionType.CANCEL_NAVIGATION -> navigation(a,onProgress) { authorization.beforeDispatch(request) }
                ActionType.PLAY_LOCAL_AUDIO -> {
                    authorization.beforeDispatch(request)?.let { return it }
                    audio.play(request.nodeId)
                }
                ActionType.STOP_LOCAL_AUDIO -> { authorization.beforeDispatch(request)?.let { return it };releaseAudio();ActionResult(ActionStatus.VERIFIED,"앱 소유 음원 중지") }
                ActionType.OPEN_APP -> {
                    if(!visible)ActionResult(ActionStatus.BLOCKED,"앱 열기는 루틴 화면이 보일 때 실행할 수 있습니다.")
                    else {
                        val target=app.packageManager.getLaunchIntentForPackage(a.targetId!!)
                        if(target==null)ActionResult(ActionStatus.BLOCKED,"설치된 실행 가능 앱을 찾을 수 없습니다.")
                        else { authorization.beforeDispatch(request)?.let { return it };app.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ActionResult(ActionStatus.UNKNOWN,"앱 열기 요청 전송 · 대상 앱 표시 여부는 확인할 수 없습니다.") }
                    }
                }
                ActionType.MEDIA_CONTROL -> ActionResult(ActionStatus.BLOCKED,"대상 MediaSession의 연결 권한·허용 명령을 아직 검증하지 않았습니다.")
                ActionType.CALENDAR_READ,ActionType.HA_CALL,ActionType.PLACE_SEARCH,ActionType.WHAT3WORDS_RESOLVE -> {
                    val result=try {
                        app.planner.request("/v1/connector-actions",buildJsonObject { put("action",WorkflowJson.format.encodeToJsonElement(a)) }) {
                            authorization.beforeDispatch(request)?.let { throw ActionDispatchRejected(it) }
                        }
                    } catch(e:ActionDispatchRejected) { return e.result }
                    WorkflowJson.format.decodeFromJsonElement<ActionResult>(result.getValue("result"))
                }
                else -> ActionResult(ActionStatus.BLOCKED,"실행 가능한 연결을 확인하지 못했습니다.")
            }
        }
        override suspend fun stop(runId:String):List<ActionResult> { authorization.invalidate();releaseAudio();return listOfNotNull(app.platform.stopSeat()) }
    }
    private fun releaseAudio() { try { audio.stop() } finally { service?.mediaType(false) } }
    private suspend fun navigation(a:ActionSpec,progress:suspend (ActionProgress)->Unit,beforeDispatch:suspend()->ActionResult?):ActionResult {
        if(nav==null) { nav=NaviHelper(app);nav!!.initialize({navReady=true},{navReady=false}) }
        if(withTimeoutOrNull(4000) { while(!navReady)delay(100);true }!=true)return ActionResult(ActionStatus.BLOCKED,"Pleos 기본 내비 서비스에 연결하지 못했습니다.")
        val done=CompletableDeferred<ActionResult>()
        val listener=object:NaviHelperEventListener {
            override fun onDestinationInfo(d:DestinationInfo) { if(a.type==ActionType.NAVIGATE && kotlin.math.abs(d.latitude-a.latitude!!)<0.00001 && kotlin.math.abs(d.longitude-a.longitude!!)<0.00001)done.complete(ActionResult(ActionStatus.VERIFIED,"내비 목적지 좌표 반영 확인")) }
            override fun onRoutingCompleted() { nav?.getDestinationInfo() }
            override fun onRouteCancelled() { if(a.type==ActionType.CANCEL_NAVIGATION)done.complete(ActionResult(ActionStatus.VERIFIED,"내비 경로 취소 이벤트 확인")) }
            override fun onError(errorCode:NaviErrorCode) { done.complete(ActionResult(ActionStatus.FAILED,"내비에서 경로 오류를 반환했습니다.")) }
        }
        val n=nav!!;n.addListener(listener)
        return try {
            beforeDispatch()?.let { return it }
            if(a.type==ActionType.NAVIGATE)n.requestRoute(RouteInfo(a.latitude!!,a.longitude!!,poiName=a.text!!,poiId="",address="",poiSubId="")) else n.cancelRoute()
            progress(ActionProgress(ActionStatus.REQUESTED,"내비 요청 전송 · 목적지 이벤트 확인 중"))
            withTimeoutOrNull(20_000) { done.await() } ?: ActionResult(ActionStatus.UNKNOWN,"내비 요청의 적용 결과를 확인하지 못했습니다.")
        } finally { n.removeListener(listener) }
    }
}

class RoutineService:Service() {
    private val runtime get()=(application as RoutineApplication).runtime
    private var audioActive=false
    private val notifications get()=getSystemService(NotificationManager::class.java)
    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel("routine","루틴 실행·감시",NotificationManager.IMPORTANCE_LOW))
        notifications.createNotificationChannel(NotificationChannel("routine-timer","타이머 완료",NotificationManager.IMPORTANCE_DEFAULT))
        mediaType(false);runtime.attach(this)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int { if(intent?.action=="STOP")runtime.cancel() else runtime.attach(this);return START_STICKY }
    fun mediaType(audio:Boolean) {
        audioActive=audio
        val notification=ongoingNotification()
        if(Build.VERSION.SDK_INT>=34)startForeground(10,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or if(audioActive)ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0) else startForeground(10,notification)
    }
    private fun ongoingNotification():Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,1,Intent(this,RoutineService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val active=runtime.timers.value.filterNot { it.completed }
        val text=if(active.isEmpty())"실행 중지는 언제든 가능합니다" else active.joinToString(" · ") { "${it.label}: ${(it.remainingMillis+999)/1000}초" }
        return Notification.Builder(this,"routine").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Pleos 루틴 · 실행 및 새 이벤트 감시").setContentText(text).setContentIntent(open).addAction(Notification.Action.Builder(null,"실행 중지",stop).build()).setOnlyAlertOnce(true).setOngoing(true).build()
    }
    fun refreshTimerNotification() { runCatching { notifications.notify(10,ongoingNotification()) } }
    fun timerCompleted(title:String,progress:TimerProgress):Boolean=runCatching {
        if(!notifications.areNotificationsEnabled() || notifications.getNotificationChannel("routine-timer")?.importance==NotificationManager.IMPORTANCE_NONE)return false
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // One notification per run shows its latest completion; every invocation remains in UI/journal.
        notifications.notify("routine-timer:${progress.runId}",11,Notification.Builder(this,"routine-timer")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("타이머 완료")
            .setContentText("$title · ${progress.label}").setContentIntent(open).setAutoCancel(true).build())
        true
    }.getOrDefault(false)
    override fun onDestroy() { runtime.detach();super.onDestroy() }
    override fun onBind(intent:Intent?):IBinder?=null
}
class BootReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=Intent.ACTION_BOOT_COMPLETED)return
        val pending=goAsync();val app=context.applicationContext as RoutineApplication
        CoroutineScope(Dispatchers.IO).launch { try { if(app.database.routines().enabled().isNotEmpty())app.runtime.startMonitoring() } finally { pending.finish() } }
    }
}
