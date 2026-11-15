package com.pleos.routine

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.pleos.routine.core.*
import com.pleos.routine.data.*
import com.pleos.routine.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class MainActivity:ComponentActivity() {
    private val app get()=application as RoutineApplication
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private var entryVersion by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val current=intent
            val fresh=savedInstanceState==null || entryVersion>0
            val action=current.getStringExtra("routineAction")
            val requestId=current.getStringExtra("requestId") ?: UUID.randomUUID().toString().also { current.putExtra("requestId",it) }
            RoutineScreen(app,current.getStringExtra("requestText").orEmpty().take(4000),
                action.takeUnless { it=="CONTROL_ROUTINE" && !fresh },current.getStringExtra("routineId")?.take(80),
                current.getStringExtra("operation")?.take(30),current.getBooleanExtra("requestSaveFailed",false),requestId,fresh,entryVersion)
        }
    }
    override fun onNewIntent(next:Intent) {
        super.onNewIntent(next)
        if(next.getStringExtra("routineAction") !in setOf("CREATE_ROUTINE","EDIT_ROUTINE","CONTROL_ROUTINE"))return
        val same=next.getStringExtra("requestId")!=null && next.getStringExtra("requestId")==intent.getStringExtra("requestId")
        setIntent(next)
        if(!same)entryVersion++
    }
    override fun onResume() { super.onResume();app.runtime.visible=true }
    override fun onPause() { app.runtime.visible=false;super.onPause() }
    fun requestVehiclePermissions() { permissions.launch((listOf("android.car.permission.CAR_SPEED","android.car.permission.CAR_EXTERIOR_ENVIRONMENT")+if(Build.VERSION.SDK_INT>=33)listOf(Manifest.permission.POST_NOTIFICATIONS)else emptyList()).toTypedArray()) }
}

/** External intents are untrusted requests. They can open a draft, never grant execution approval. */
class GleoActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val raw=intent.getStringExtra("actionParameters") ?: intent.getStringExtra("ACTION_PARAM") ?: ""
        val action=intent.action ?: run { finish();return }
        if(action !in setOf("CREATE_ROUTINE","EDIT_ROUTINE","CONTROL_ROUTINE")) { finish();return }
        val parsed=runCatching { val element=Json.parseToJsonElement(raw.take(8192));if(element is JsonPrimitive)Json.parseToJsonElement(element.content).jsonObject else element.jsonObject }.getOrNull()
        fun field(name:String)=intent.getStringExtra(name) ?: (parsed?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        val request=field("requestText").orEmpty()
        val id=field("routineId")
        val operation=field("operation")
        val valid=when(action) {
            "CREATE_ROUTINE" -> request.isNotBlank() && request.length<=4000
            "EDIT_ROUTINE" -> !id.isNullOrBlank() && id.length<=80 && request.isNotBlank() && request.length<=4000
            else -> !id.isNullOrBlank() && id.length<=80 && operation in GleoReviews.controlOperations
        }
        if(!valid) {
            replyToGleo(action,"fail","The routine request contains missing or invalid parameters.")
            finish();return
        }
        // Reply before disk IO; asking is neither a persistence nor an execution success.
        val app=application as RoutineApplication
        val requestId=UUID.randomUUID().toString()
        replyToGleo(action,"asking","Please review and confirm the routine request in the app.")
        lifecycleScope.launch {
            val saved=withContext(intakeDispatcher) {
                if(action=="CONTROL_ROUTINE") {
                    app.gleoReviews.openControl(requestId,id!!,operation!!)
                    true
                } else runCatching {
                    app.gleoReviews.retain(PendingRoutineRequest(requestId,action,request,if(action=="EDIT_ROUTINE")id else null))
                }.getOrDefault(false)
            }
            if(!saved)replyToGleo(action,"fail","The app could not retain the routine request.")
            startActivity(Intent(this@GleoActivity,MainActivity::class.java).putExtra("requestText",request.take(4000)).putExtra("routineAction",action).putExtra("routineId",id?.take(80)).putExtra("operation",operation?.take(30)).putExtra("requestSaveFailed",!saved).putExtra("requestId",requestId))
            finish()
        }
    }
    private companion object { val intakeDispatcher=Dispatchers.IO.limitedParallelism(1) }
}

private val appColors=darkColorScheme(primary=Color(0xFF76BCEB),onPrimary=Color(0xFF142330),background=Color(0xFF142330),surface=Color(0xFF233747),onSurface=Color(0xFFF3F6F8),secondary=Color(0xFF79CCA9),error=Color(0xFFF08D87))
private data class ProposalReview(
    val base:WorkflowDefinition,val proposal:PlannerReply.Proposal,val selectedNodeId:String?,val gleoReviewId:String?,
)
private data class EditorSnapshot(val editor:WorkingEditor,val clarification:String?,val gleoReviewId:String?)
@Composable private fun RoutineScreen(app:RoutineApplication,initialRequest:String,initialAction:String?,initialId:String?,initialOperation:String?,requestSaveFailed:Boolean,initialRequestId:String,freshIntent:Boolean,entryVersion:Int) {
    val type=Typography()
    MaterialTheme(colorScheme=appColors,typography=Typography(
        bodyLarge=type.bodyLarge.copy(fontSize=22.sp,lineHeight=30.sp),bodyMedium=type.bodyMedium.copy(fontSize=20.sp,lineHeight=28.sp),
        labelLarge=type.labelLarge.copy(fontSize=18.sp,lineHeight=24.sp),labelSmall=type.labelSmall.copy(fontSize=14.sp,lineHeight=20.sp),
        titleSmall=type.titleSmall.copy(fontSize=20.sp,lineHeight=28.sp),titleMedium=type.titleMedium.copy(fontSize=24.sp,lineHeight=32.sp),
        titleLarge=type.titleLarge.copy(fontSize=30.sp,lineHeight=38.sp),headlineMedium=type.headlineMedium.copy(fontSize=36.sp,lineHeight=44.sp))) {
        val scope=rememberCoroutineScope()
        val dao=app.database.routines()
        val saved by dao.observeRoutines().collectAsState(initial=emptyList())
        val runs by dao.observeRuns().collectAsState(initial=emptyList())
        val vehicle by app.platform.state.collectAsState()
        val connection by app.platform.connection.collectAsState()
        val capabilities by app.platform.capabilities.collectAsState()
        val busy by app.runtime.busy.collectAsState()
        val approval by app.runtime.approval.collectAsState()
        val liveEvents by app.runtime.events.collectAsState()
        val runtimeNotice by app.runtime.notice.collectAsState()
        val timers by app.runtime.timers.collectAsState()
        val audioState by app.runtime.audioState.collectAsState()
        var page by remember { mutableIntStateOf(0) }
        var step by remember { mutableIntStateOf(0) }
        var w by remember { mutableStateOf(WorkflowExamples.commute()) }
        var history by remember { mutableStateOf(WorkflowHistory(w)) }
        var selected by remember { mutableStateOf<String?>(null) }
        var request by remember { mutableStateOf(initialRequest) }
        var message by remember { mutableStateOf("") }
        var planning by remember { mutableStateOf(false) }
        var planningJob by remember { mutableStateOf<Job?>(null) }
        var candidate by remember { mutableStateOf<ProposalReview?>(null) }
        var clarification by remember { mutableStateOf<ClarificationDraft?>(null) }
        var scopeEditor by remember { mutableStateOf<WorkflowNode?>(null) }
        var saveReview by remember { mutableStateOf<WorkflowDefinition?>(null) }
        var saving by remember { mutableStateOf(false) }
        var gleoReviewId by remember { mutableStateOf<String?>(null) }
        var controlReview by remember { mutableStateOf(false) }
        var editNode by remember { mutableStateOf<WorkflowNode?>(null) }
        var triggerEditor by remember { mutableStateOf(false) }
        var graphEditor by remember { mutableStateOf(false) }
        var rename by remember { mutableStateOf(false) }
        var add by remember { mutableStateOf(false) }
        var test by remember { mutableStateOf<RunResult?>(null) }
        var testRecord by remember { mutableStateOf<RunRecord?>(null) }
        var temp by remember { mutableStateOf("3") }
        var testTime by remember { mutableStateOf("2026-10-26T08:00") }
        var testOccupied by remember { mutableStateOf(true) }
        var loaded by remember { mutableStateOf(false) }
        var draftStatus by remember { mutableStateOf("") }
        var pendingRequest by remember { mutableStateOf<PendingRoutineRequest?>(null) }
        var originRequestId by remember { mutableStateOf<String?>(null) }
        val editable=!app.platform.automotive || !vehicle.uxRestricted
        val issues=remember(w) { WorkflowValidator.validate(w) }
        fun canEdit() = !app.platform.automotive || !app.platform.state.value.uxRestricted
        fun cancelPlanner() {
            planningJob?.cancel();planningJob=null;planning=false
            candidate=null;scopeEditor=null
        }
        fun resetPlanner() { cancelPlanner();clarification=null;saveReview=null }
        fun changed(next:WorkflowDefinition) {
            if(!canEdit()) { message="주행 UX 제한 중에는 편집할 수 없습니다.";return }
            resetPlanner();history.replace(next);w=next
        }
        fun load(next:WorkflowDefinition) {
            resetPlanner();gleoReviewId=null;w=next;history=WorkflowHistory(next)
            selected=null;test=null;testRecord=null;page=0;step=1
        }
        fun updateRequest(text:String) { resetPlanner();request=text.take(4000) }
        fun selectNode(id:String) {
            if(selected!=id) { resetPlanner();selected=id }
        }
        fun askPlanner(text:String,edit:Boolean,nodeId:String?=null,answers:AnsweredQuestions?=null) {
            if(!canEdit() || planning || !loaded || text.isBlank())return
            val base=w
            val reviewOrigin=if(edit)gleoReviewId else pendingRequest?.takeIf {
                it.action=="CREATE_ROUTINE" && it.id==originRequestId && it.text==text
            }?.id
            request=text
            message=""
            planning=true
            planningJob=scope.launch {
                try {
                    val answer=app.planner.plan(text,base.takeIf { edit },nodeId,answers,app.platform.capabilities.value)
                    ensureActive()
                    if(!canEdit() || w!=base || request!=text || (nodeId!=null && selected!=nodeId))return@launch
                    when(val result=PlannerConversation.read(answer,base,text,edit,nodeId,answers)) {
                        is PlannerReply.Ask -> { candidate=null;clarification=result.draft;message="" }
                        is PlannerReply.Proposal -> { clarification=null;candidate=ProposalReview(base,result,nodeId,reviewOrigin);message="" }
                        is PlannerReply.Unsupported -> { clarification=null;message=result.explanation.ifBlank { "이 요청을 지원하지 않습니다." } }
                    }
                } catch(e:CancellationException) { throw e }
                catch(e:Exception) { message=e.message ?: "AI 응답을 확인할 수 없습니다." }
                finally { if(planningJob===coroutineContext[Job]) { planning=false;planningJob=null } }
            }
        }
        fun editFromHistory(row:RunRecord,nodeId:String?,partial:List<ExecutionEvent> = emptyList()) {
            try {
                // This UI decision must be synchronous: an older DAO completion could overwrite a newer edit.
                // If saved rows have not loaded, edit() conservatively creates a copy.
                val current=saved.firstOrNull { it.id==row.routineId }?.definition?.let(WorkflowJson::decode)
                if(app.platform.automotive && app.platform.state.value.uxRestricted)return
                val edit=RunHistory.edit(row,nodeId,current,UUID.randomUUID().toString())
                planningJob?.cancel();planningJob=null;planning=false;candidate=null
                load(edit.workflow);step=2;selected=edit.nodeId
                request="실행 ${row.id} · ${if(row.simulation)"가상" else "실제"} · ${row.state} · 당시 버전 ${row.revision}. "+
                    "이 기록의 조건을 검토해줘: "+RunHistory.events(row,partial).filter { nodeId==null || it.nodeId==nodeId }.takeLast(4).joinToString { it.message }.take(2500)
                message=if(edit.copied)"실행 당시 규칙을 복사해 열었습니다. 최신 저장본은 유지됩니다." else "실행 당시 규칙의 조건을 열었습니다. 변경 후 테스트하고 저장하세요."
                if(edit.nodeId==null)triggerEditor=true else editNode=edit.workflow.nodes.first { it.id==edit.nodeId }
            } catch(e:CancellationException) { throw e } catch(e:Exception) { message=e.message.orEmpty() }
        }
        LaunchedEffect(entryVersion) {
            // A new entry must not dispose live edits or cancel their pending autosave.
            val persisted=if(!loaded)withContext(Dispatchers.IO) { app.database.withTransaction {
                EditorSnapshot(WorkingEditor(dao.working()?.definition?.let { runCatching { WorkflowJson.decode(it) }.getOrNull() },
                    dao.workingRequest()?.definition.orEmpty().take(4000),dao.workingOrigin()?.definition?.ifBlank { null }),
                    dao.workingClarification()?.definition,dao.workingGleoReview()?.definition?.ifBlank { null })
            } } else null
            val retained=withContext(Dispatchers.IO) { app.pendingRequests.read() }
            pendingRequest=retained
            val fromIntent=initialAction?.let { PendingRoutineRequest(initialRequestId,it,initialRequest,initialId) }?.takeIf { it.isValid() }
            val working=persisted?.editor ?: WorkingEditor(w,request,originRequestId)
            val incoming=EditorRestore.incoming(working,fromIntent,retained,freshIntent,requestSaveFailed)
            val target=if(incoming?.action=="EDIT_ROUTINE")incoming.routineId?.let { id -> withContext(Dispatchers.IO) { dao.routine(id) } }
                ?.definition?.let { runCatching { WorkflowJson.decode(it) }.getOrNull() } else null
            val restored=EditorRestore.restore(if(loaded)WorkingEditor(w,request,originRequestId) else working,incoming,target)
            if(!loaded || restored.applied) {
                if(restored.applied) {
                    resetPlanner();editNode=null;triggerEditor=false;graphEditor=false;rename=false;add=false
                }
                restored.working.workflow?.let { load(it) }
                request=restored.working.text;originRequestId=restored.working.originRequestId
                gleoReviewId=if(restored.applied)incoming?.takeIf { it.action=="EDIT_ROUTINE" && target!=null }?.id
                    else persisted?.gleoReviewId
                if(!restored.applied) {
                    clarification=PlannerConversation.restore(persisted?.clarification,w,request)
                    clarification?.let { selected=it.selectedNodeId;step=if(it.edit)2 else 0 }
                }
            }
            if(restored.applied)when(incoming?.action) {
                "CREATE_ROUTINE" -> { page=0;step=0;message="Gleo 생성 요청을 검토한 뒤 초안을 만들어 주세요." }
                "EDIT_ROUTINE" -> if(restored.missingTarget) { page=1;message="수정할 루틴을 선택한 뒤 요청을 확인하세요: $request" } else step=2
            }
            controlReview=app.gleoReviews.matchesControl(initialRequestId,initialId,initialOperation)
            if(initialAction=="CONTROL_ROUTINE") { page=1;message="Gleo 요청: ${initialOperation ?: "실행"} · ${initialId ?: "루틴 선택 필요"}." }
            loaded=true
        }
        LaunchedEffect(w,request,originRequestId,clarification,gleoReviewId,loaded,editable) {
            if(loaded) {
                draftStatus="초안 저장 중…"
                if(editable)delay(350)
                try {
                    val definition=WorkflowJson.encode(w)
                    val text=request
                    val origin=originRequestId.orEmpty()
                    val questions=clarification?.let(PlannerConversation::encode).orEmpty()
                    val review=gleoReviewId.orEmpty()
                    withContext(Dispatchers.IO) { app.database.withTransaction {
                        dao.draft(DraftRecord("working",definition))
                        dao.draft(DraftRecord("working-request",text))
                        dao.draft(DraftRecord("working-origin",origin))
                        dao.draft(DraftRecord("working-clarification",questions))
                        dao.draft(DraftRecord("working-gleo-review",review))
                    } }
                    draftStatus="초안 저장됨"
                } catch(e:CancellationException) { throw e }
                catch(_:Exception) { draftStatus="초안 저장 실패 · 앱을 닫기 전에 다시 확인해 주세요." }
            }
        }
        // Heard text fills its own field only; the driver still presses the existing button to send it.
        LaunchedEffect(Unit) { app.voiceResults.collect { (target,text) -> when {
            target=="request" -> updateRequest(joinHeard(request,text))
            target.startsWith("answer:") -> clarification?.let { d -> target.removePrefix("answer:").toIntOrNull()?.takeIf { it in d.answers.indices }?.let { i -> clarification=d.withAnswer(i,joinHeard(d.answers[i],text)) } }
        } } }
        LaunchedEffect(editable) { if(!editable) { app.voice.cancel();cancelPlanner();saveReview=null;editNode=null;triggerEditor=false;graphEditor=false;rename=false;add=false;message="주행 UX 제한이 적용되었습니다. 상세 편집은 정차 후 이어갈 수 있습니다." } }
        LaunchedEffect(editable,approval) { if(!editable)approval?.answer?.complete(false) }
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Column { Text("PLEOS  /  ROUTINE",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge);Text("나의 이동을, 나의 방식으로",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold) }
                    Column(horizontalAlignment=Alignment.End) { Text("${vehicle.gear.value ?: "—"}  ·  ${vehicle.speedMps.value?.let { "%.1f m/s".format(it) } ?: "속도 미확인"}",style=MaterialTheme.typography.titleMedium);Text(if(editable)"편집 가능" else "주행 제한 · 초안 보관",color=MaterialTheme.colorScheme.secondary);if(busy)Button(onClick={app.runtime.cancel()},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)) { Text("실행 중지") } }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("루틴 만들기","내 루틴","연결 둘러보기","실행 이력","연결 설정").forEachIndexed { i,label -> FilterChip(selected=page==i,onClick={if(i!=page)cancelPlanner();page=i},label={Text(label)}) } }
                if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.bodyMedium)
                if(draftStatus.isNotBlank())Text(draftStatus,style=MaterialTheme.typography.labelSmall)
                if(runtimeNotice.isNotBlank())Text(runtimeNotice,color=MaterialTheme.colorScheme.error)
                if(busy && audioState.isNotBlank())Text(audioState,color=MaterialTheme.colorScheme.secondary)
                val visibleTimers=timers.filterNot { it.completed }+timers.filter { it.completed }.takeLast(3)
                if(visibleTimers.isNotEmpty())Text(visibleTimers.joinToString(" · ") {
                    "${it.label}: "+if(it.completed)"타이머 완료" else "남은 ${(it.remainingMillis+999)/1000}초"
                },color=MaterialTheme.colorScheme.secondary)
                if(requestSaveFailed)Text("Gleo 요청을 기기에 보관하지 못했습니다. 앱을 닫기 전에 요청을 확인해 주세요.",color=MaterialTheme.colorScheme.error)
                pendingRequest?.let { retained ->
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("보관된 Gleo 요청 · 정차 후 검토 · 자동 실행되지 않음",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                        TextButton(enabled=editable,onClick={scope.launch {
                            val cleared=withContext(Dispatchers.IO) { app.gleoReviews.dismiss(retained.id) }
                            if(cleared && pendingRequest?.id==retained.id) { pendingRequest=null;gleoReviewId=null } else message="보관 요청이 변경되었거나 삭제하지 못했습니다. 다시 열어 확인해 주세요."
                        }}) { Text("보관 요청 지우기") }
                    }
                }
                if(editable && controlReview) {
                    val target=saved.find { it.id==initialId }
                    Text("Gleo 요청 검토 · ${target?.let { WorkflowJson.decode(it.definition).title } ?: "저장된 대상 없음"} · ${initialOperation.orEmpty()}\n요청 확인 후 실제 동작은 내 루틴에서 선택하세요.")
                    fun finishControl(accepted:Boolean) {
                        scope.launch {
                            val exists=initialId!=null && withContext(Dispatchers.IO) { dao.routine(initialId)!=null }
                            if(canEdit() && app.gleoReviews.reviewControl(initialRequestId,initialId,initialOperation,accepted,exists)) {
                                controlReview=false;message=if(accepted)"Gleo 요청을 검토했습니다." else "Gleo 요청을 거절했습니다."
                            }
                        }
                    }
                    Row {
                        Button(enabled=target!=null,onClick={finishControl(true)}) { Text("요청 확인") }
                        TextButton(onClick={finishControl(false)}) { Text("거절") }
                    }
                }
                when(page) {
                    0 -> {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("01 생성","02 확인","03 수정","04 테스트").forEachIndexed { i,label -> FilterChip(selected=step==i,onClick={if(i!=step)cancelPlanner();step=i},label={Text(label)}) };Spacer(Modifier.weight(1f));TextButton(onClick={resetPlanner();w=history.undo()},enabled=editable&&history.canUndo) { Text("실행 취소") };TextButton(onClick={resetPlanner();w=history.redo()},enabled=editable&&history.canRedo) { Text("다시 적용") } }
                        if(!editable) {
                            Text("지금은 짧은 요청을 초안으로 남길 수 있습니다. 상세 편집과 활성화는 정차 후 이어집니다.",style=MaterialTheme.typography.titleLarge)
                            Text("자동 실행과 편집 제한은 별도로 적용됩니다. 현재 활성 루틴 ${saved.count { it.enabled }}개")
                        } else if(step==0) {
                            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Text("어떤 순간을 만들까요?",style=MaterialTheme.typography.headlineLarge)
                                Text("상황과 원하는 동작을 말하듯 적어 주세요. AI가 만든 규칙은 확인한 뒤 사용할 수 있습니다.")
                                OutlinedTextField(request,::updateRequest,Modifier.fillMaxWidth(),label={Text("나의 요청")},placeholder={Text("겨울 차박을 준비해 줘. 바깥이 5도 이하면 공조를 23도로 맞춰 줘.")},minLines=3)
                                VoiceButton(app.voice,"request",editable&&!planning)
                                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Button(enabled=loaded&&request.isNotBlank()&&!planning,onClick={askPlanner(request,false)}) { Text(if(planning)"규칙을 만드는 중…" else "AI로 초안 만들기") }
                                    OutlinedButton(onClick={load(WorkflowExamples.winterCamping().copy(id=UUID.randomUUID().toString()));message="겨울 차박 예시입니다. 좌석과 조명 영역은 차량 지원 조회로 확인해 주세요."}) { Text("겨울 차박 예시") }
                                    OutlinedButton(onClick={load(WorkflowExamples.commute());message="예시 템플릿입니다. 회사 좌표와 좌석 영역을 직접 확인해 주세요."}) { Text("출근 예시로 시작") }
                                    OutlinedButton(onClick={load(WorkflowExamples.rest());message="휴식 예시입니다. 실제 지원 범위와 시트 STOP 검증이 필요합니다."}) { Text("휴식 예시") }
                                    TextButton(onClick={load(WorkflowExamples.blank(UUID.randomUUID().toString()))}) { Text("빈 흐름") }
                                }
                                Text("AI 연결 전에도 직접 만들고 테스트할 수 있어요.",color=MaterialTheme.colorScheme.primary)
                                FlowCanvas(w,selected,false,::selectNode,{_,_->},Modifier.fillMaxWidth().height(300.dp))
                            }
                        } else {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                    Column { Text(w.title,style=MaterialTheme.typography.headlineMedium);Text("버전 ${w.revision}  ·  ${w.nodes.size}개 노드  ·  ${w.trigger.type}",color=MaterialTheme.colorScheme.primary) }
                                    Row { TextButton(onClick={rename=true}) { Text("이름 변경") };TextButton(onClick={triggerEditor=true}) { Text("시작 조건") };TextButton(onClick={add=true}) { Text("+ 노드") };TextButton(onClick={graphEditor=true}) { Text("연결 편집") } }
                                }
                                Card { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Text("시작 조건",style=MaterialTheme.typography.titleMedium)
                                    Text(RuleDescriptions.trigger(w.trigger))
                                    Text("추가 조건은 시작할 때 확인하고, 흐름 안의 조건 분기는 해당 동작 앞에서 평가합니다.",style=MaterialTheme.typography.bodyMedium)
                                } }
                                FlowCanvas(w,selected,step==2,::selectNode,{id,pos->changed(w.copy(nodes=w.nodes.map { if(it.id==id)it.copy(position=pos) else it }))},Modifier.fillMaxWidth().height(400.dp))
                                selected?.let { id -> w.nodes.find { it.id==id }?.let { n ->
                                    Card { Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) { Text(n.label,style=MaterialTheme.typography.titleMedium);Text(nodeSummary(n));w.edges.filter { it.source==id }.forEach { e -> Text("${e.port} → ${w.nodes.find { it.id==e.target }?.label ?: e.target}",color=MaterialTheme.colorScheme.primary) } }
                                        TextButton(onClick={editNode=n}) { Text("규칙 수정") }
                                        TextButton(enabled=!planning,onClick={scopeEditor=n}) { Text("AI에게 수정 요청") }
                                        if(n.kind in setOf(NodeKind.ACTION,NodeKind.WAIT,NodeKind.TIMER))TextButton(onClick={runCatching { changed(GraphEditing.remove(w,id));selected=null }.onFailure { message=it.message.orEmpty() }}) { Text("삭제") }
                                    } }
                                } }
                                if(issues.isNotEmpty())Card { Column(Modifier.padding(16.dp)) { Text("확인할 항목 ${issues.size}개",color=MaterialTheme.colorScheme.error);issues.forEach { Text("${it.nodeId ?: "흐름"} · ${it.message}") } } }
                                if(step==2) {
                                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                        OutlinedTextField(request,::updateRequest,Modifier.weight(1f),label={Text("루틴 전체 수정 요청")})
                                        Button(enabled=loaded&&!planning&&request.isNotBlank(),onClick={askPlanner(request,true)}) { Text("변경 제안") }
                                    }
                                }
                                if(step==3) {
                                    Text("시뮬레이션 · 차량을 변경하지 않습니다",style=MaterialTheme.typography.titleLarge)
                                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { OutlinedTextField(testTime,{testTime=it},Modifier.weight(2f),label={Text("가상 날짜·시간 (서울)")});OutlinedTextField(temp,{temp=it},Modifier.weight(1f),label={Text("외부 온도 °C")});FilterChip(testOccupied,{testOccupied=!testOccupied},label={Text("좌석 점유")}) }
                                    Button(enabled=issues.isEmpty(),onClick={ val tested=w;scope.launch { try { val at=LocalDateTime.parse(testTime).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();val value=temp.toDouble();require(value.isFinite());val result=WorkflowSimulator.simulate(tested,ExecutionContext.simulated(at,value,occupied=testOccupied));val row=RunHistory.simulation(tested,result);withContext(Dispatchers.IO) { dao.putRun(row) };test=result;testRecord=row } catch(e:CancellationException) { throw e } catch(e:Exception) { message="테스트 입력을 확인하세요: ${e.message}" } } }) { Text("이 상황으로 테스트") }
                                    test?.let { result ->
                                        Text("${result.status} · 가상 경과 ${result.elapsedMillis/1000}초",color=MaterialTheme.colorScheme.secondary)
                                        if(result.semanticHash!=WorkflowJson.semanticHash(w))Text("규칙이 변경되어 이전 테스트 결과가 무효화되었습니다.",color=MaterialTheme.colorScheme.error)
                                        result.events.forEach { e -> Text(RunHistory.eventDescription(testRecord?.let(RunHistory::definition),e)) }
                                        TextButton(enabled=testRecord!=null,onClick={testRecord?.let { row -> val snapshot=RunHistory.definition(row);editFromHistory(row,result.events.lastOrNull { event->snapshot?.nodes?.any { it.id==event.nodeId && it.kind==NodeKind.IF }==true }?.nodeId) }}) { Text("조건을 확인하고 수정하기") }
                                    }
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Button(enabled=loaded&&issues.isEmpty()&&!planning&&!saving,onClick={saveReview=w}) { Text("검토한 루틴 저장") }
                                    OutlinedButton(onClick={load(WorkflowEdits.clone(w,UUID.randomUUID().toString()));message="원본을 유지하고 복사본을 편집합니다."}) { Text("복제") }
                                    OutlinedButton(enabled=issues.isEmpty()&&!busy,onClick={val tested=w;scope.launch { if(app.runtime.confirm("실제 기능 테스트","저장된 규칙과 지원 상태를 다시 확인합니다. 실제 차량·연결된 서비스가 변경될 수 있습니다."))app.runtime.requestRun(tested,RunStartCause.ACTUAL_TEST) }}) { Text("실제 기능 테스트") }
                                }
                                if(busy)liveEvents.takeLast(8).forEach { Text("${it.status} · ${it.message}") }
                            }
                        }
                    }
                    1 -> LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(saved.isEmpty())item { Text("아직 저장한 루틴이 없습니다. 예시로 시작해 보세요.") }
                        items(saved,key={it.id}) { row -> val routine=remember(row.definition) { WorkflowJson.decode(row.definition) }
                            Card { Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(routine.title,style=MaterialTheme.typography.titleLarge);Text(if(row.enabled)"활성" else "비활성",color=MaterialTheme.colorScheme.primary) }
                                Text("${routine.trigger.type} · 버전 ${routine.revision} · 우선순위 ${row.priority}")
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Button(onClick={load(routine)},enabled=editable,modifier=Modifier.testTag("open-routine-${routine.id}")) { Text("열기") }
                                    OutlinedButton(onClick={scope.launch {
                                        if(row.enabled)withContext(Dispatchers.IO) { dao.enable(row.id,false) }
                                        else if(row.testedHash!=WorkflowJson.semanticHash(routine))message="현재 규칙의 시뮬레이션을 통과하고 저장한 뒤 활성화하세요."
                                        else if(WorkflowValidator.validate(routine).isNotEmpty())message="규칙 오류를 먼저 수정하세요."
                                        else {
                                            val conflicts=saved.filter { it.enabled && it.id!=row.id && it.priority==row.priority }.filter { other -> val keys=WorkflowJson.decode(other.definition).nodes.mapNotNull { it.action }.filter(ActionResources::isWrite).map(ActionResources::key);routine.nodes.mapNotNull { it.action }.any { ActionResources.key(it) in keys } }
                                            if(conflicts.isNotEmpty())message="같은 우선순위의 활성 루틴과 제어 자원이 겹칩니다. 우선순위를 변경하세요."
                                            else if(app.runtime.confirm("자동 실행 활성화","${routine.title}\n새 조건 진입부터 감시합니다. 처음부터 참인 조건과 과거 실행은 재생하지 않습니다. 시트 이동은 실행할 때마다 확인합니다.")) { withContext(Dispatchers.IO) { dao.enable(row.id,true) };app.runtime.startMonitoring() }
                                        }
                                    }},enabled=editable) { Text(if(row.enabled)"끄기" else "활성화") }
                                    OutlinedButton(onClick={app.runtime.requestRun(routine)},enabled=!busy&&editable) { Text("지금 실행") }
                                    TextButton(onClick={scope.launch { withContext(Dispatchers.IO) { dao.put(row.copy(priority=row.priority+1)) } }},enabled=editable) { Text("우선순위 +1") }
                                }
                            } }
                        }
                    }
                    2 -> CatalogPanel(app)
                    3 -> LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) { if(runs.isEmpty())item { Text("시뮬레이션과 실제 실행 결과가 이곳에 표시됩니다.") };items(runs,key={it.id}) { row ->
                        val eventFlow:Flow<List<RunEventRecord>?> = remember(row.id) { dao.observeEvents(row.id) }
                        val journalEvents by eventFlow.collectAsState(initial=null)
                        val partial=remember(journalEvents) { journalEvents.orEmpty().mapNotNull { runCatching { WorkflowJson.format.decodeFromString<ExecutionEvent>(it.eventJson) }.getOrNull() } }
                        RunHistoryCard(row,partial,editable,app.planner,evidenceReady=journalEvents!=null,onEdit={ nodeId->editFromHistory(row,nodeId,partial) })
                    } }
                    4 -> if(editable)SettingsPanel(app,connection) else Text("연결 설정은 정차 후 확인할 수 있습니다.")
                }
            }
        }
        approval?.takeIf { editable }?.let { q -> AlertDialog(onDismissRequest={q.answer.complete(false)},title={Text(q.title)},text={Text(q.detail)},confirmButton={Button(enabled=editable,onClick={q.answer.complete(true)}) { Text("확인") }},dismissButton={TextButton(onClick={q.answer.complete(false)}) { Text("취소") }}) }
        candidate?.takeIf { editable }?.let { review ->
            val next=review.proposal.workflow
            AlertDialog(onDismissRequest={candidate=null},title={Text("AI 변경 제안 확인")},
                text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(review.proposal.explanation)
                    review.selectedNodeId?.let { id->
                        val originalIds=review.base.nodes.map { it.id }.toSet()
                        fun path(flow:WorkflowDefinition):String {
                            var current=flow.nodes.first { it.id==id }
                            val labels=mutableListOf(current.label)
                            while(true) {
                                val edge=flow.edges.singleOrNull { it.source==current.id && it.port=="next" } ?: break
                                current=flow.nodes.first { it.id==edge.target }
                                labels+=current.label
                                if(current.id in originalIds)break
                            }
                            return labels.joinToString(" → ")
                        }
                        Text("수정 전 · "+path(review.base))
                        Text("수정 후 · "+path(next),color=MaterialTheme.colorScheme.primary)
                    }
                    RuleDescriptions.changes(review.base,next).forEach { Text(it) }
                }},confirmButton={Button(enabled=w==review.base,onClick={
                    if(canEdit() && w==review.base) { changed(next);gleoReviewId=review.gleoReviewId;step=1 }
                    candidate=null
                }) { Text("초안에 적용") }},dismissButton={TextButton(onClick={candidate=null}) { Text("취소") }})
        }
        scopeEditor?.takeIf { editable }?.let { node ->
            ScopedRequestDialog(node,{text->
                scopeEditor=null
                if(selected==node.id && w.nodes.contains(node))askPlanner(text,true,node.id)
            },{scopeEditor=null},app.voice,app.voiceResults)
        }
        clarification?.takeIf { editable }?.let { draft ->
            ClarificationDialog(draft,planning,message,{i,value->clarification=draft.withAnswer(i,value)},
                {askPlanner(draft.requestText,draft.edit,draft.selectedNodeId,draft.answered())},
                {resetPlanner();message=""},app.voice)
        }
        saveReview?.takeIf { editable }?.let { reviewed ->
            NamedSaveDialog(reviewed,saving,{name->
                if(!saving && canEdit() && reviewed==w && issues.isEmpty()) {
                    saving=true
                    val next=if(name==reviewed.title)reviewed else reviewed.copy(title=name,revision=reviewed.revision+1)
                    val origin=gleoReviewId
                    val hash=WorkflowJson.semanticHash(next)
                    val tested=test?.takeIf { it.status==RunStatus.COMPLETED && it.semanticHash==hash }?.semanticHash
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { app.database.withTransaction {
                                val old=dao.routine(next.id)
                                dao.put(RoutineRecord(next.id,WorkflowJson.encode(next),false,
                                    tested ?: old?.testedHash?.takeIf { it==hash },old?.priority ?: 0))
                            } }
                            if(w==reviewed) {
                                if(next!=w) { history.replace(next);w=next }
                                saveReview=null;message="‘${next.title}’ 루틴을 비활성으로 저장했습니다."
                            }
                            if(origin!=null && withContext(Dispatchers.IO) { app.gleoReviews.saved(origin,next.id) }) {
                                if(pendingRequest?.id==origin)pendingRequest=null
                                if(gleoReviewId==origin)gleoReviewId=null
                            }
                        } catch(e:CancellationException) { throw e }
                        catch(_:Exception) { saveReview=null;message="저장 결과를 내 루틴에서 확인해 주세요." }
                        finally { saving=false }
                    }
                }
            },{saveReview=null})
        }
        editNode?.takeIf { editable }?.let { node -> NodeEditor(node,capabilities,{editNode=null}) { n -> changed(w.copy(revision=w.revision+1,nodes=w.nodes.map { if(it.id==n.id)n else it }));editNode=null } }
        if(editable && triggerEditor)TriggerEditor(w.trigger,{triggerEditor=false}) { t -> changed(w.copy(revision=w.revision+1,trigger=t));triggerEditor=false }
        if(editable && graphEditor)EdgeEditor(w,{graphEditor=false}) { edges -> changed(w.copy(revision=w.revision+1,edges=edges));graphEditor=false }
        if(editable && rename) { var name by remember { mutableStateOf(w.title) };AlertDialog(onDismissRequest={rename=false},title={Text("루틴 이름")},text={OutlinedTextField(name,{name=it.take(100)},label={Text("이름")})},confirmButton={Button(enabled=name.isNotBlank(),onClick={changed(w.copy(title=name,revision=w.revision+1));rename=false}) { Text("이름 저장") }},dismissButton={TextButton(onClick={rename=false}) { Text("취소") }}) }
        if(editable && add)AlertDialog(onDismissRequest={add=false},title={Text("선택한 연결에 노드 삽입")},text={Column(Modifier.verticalScroll(rememberScrollState())) { val edges=w.edges.filter { selected==null || it.source==selected };if(edges.isEmpty())Text("나가는 연결이 있는 노드를 선택하세요.");edges.forEach { edge -> Text("${edge.source} · ${edge.port} → ${edge.target}");listOf(NodeKind.ACTION,NodeKind.IF,NodeKind.WAIT,NodeKind.TIMER,NodeKind.REPEAT,NodeKind.PARALLEL).forEach { kind -> TextButton(onClick={runCatching { changed(GraphEditing.insert(w,edge.id,kind));add=false;step=2 }.onFailure { message=it.message.orEmpty() }}) { Text(kind.name) } } } }},confirmButton={TextButton(onClick={add=false}) { Text("닫기") }})
    }
}

private fun nodeSummary(n:WorkflowNode):String = RuleDescriptions.node(n)

@Composable private fun SettingsPanel(app:RoutineApplication,connection:String) {
    val activity=LocalActivity.current as? MainActivity
    val scope=rememberCoroutineScope();val caps by app.platform.capabilities.collectAsState()
    var url by remember { mutableStateOf(app.planner.endpoint) };var token by remember { mutableStateOf(app.planner.token) };var result by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("연결과 지원 상태",style=MaterialTheme.typography.headlineMedium);Text(connection)
        Text("AI 제공자 키는 서버에서만 관리합니다. 앱에는 개발 서버 접속 토큰을 입력하세요.")
        OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text("개발 서버 주소")})
        OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),label={Text("서버 접속 토큰 · 32자 이상")},visualTransformation=PasswordVisualTransformation())
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(onClick={try { require(token.length>=32);app.planner.endpoint=url;app.planner.token=token;result="연결 설정을 저장했습니다." } catch(_:Exception) { result="주소 형식과 32자 이상의 접속 토큰을 확인하세요." }}) { Text("연결 저장") }
            OutlinedButton(onClick={scope.launch { result=try { val s=app.planner.request("/v1/status");"AI 호출: ${s["aiEnabled"]} · ${s["message"]?.jsonPrimitive?.content.orEmpty()}" } catch(e:Exception) { e.message.orEmpty() } }}) { Text("서버 확인") }
            OutlinedButton(onClick={scope.launch { app.platform.refreshCapabilities() }}) { Text("차량 지원 조회") }
            OutlinedButton(onClick={activity?.requestVehiclePermissions()}) { Text("속도·알림 권한") }
        }
        Text(result)
        caps.forEach { c -> Card { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text("${c.type} · ${c.access}");Text(c.areas.joinToString());Text(c.ranges.toString());if(c.message.isNotBlank())Text(c.message) } } }
        Text("시트 제한 이동·STOP 및 좌석 점유 유효성은 대상 AVD 증거가 확보되기 전까지 잠깁니다. 시트가 든 루틴은 실행할 때 동의하면 시트 단계만 미실행으로 기록하고 나머지를 실행합니다. 읽기 값은 운전자 신원 확인이 아닙니다.")
    }
}

@Composable private fun CatalogPanel(app:RoutineApplication) {
    var query by remember { mutableStateOf("") }
    var installed by remember { mutableStateOf(false) }
    val entries=remember { runCatching { Json.parseToJsonElement(app.assets.open("connector-catalog.json").bufferedReader().use { it.readText() }).jsonObject.getValue("entries").jsonArray }.getOrDefault(JsonArray(emptyList())) }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("연결 가능한 기능을 먼저 확인하세요",style=MaterialTheme.typography.headlineMedium)
        Text("공개 문서, 계정 권한, 이 차량에서의 동작 여부는 각각 다릅니다. 목록에 있다는 이유로 연결·실행이 보장되지는 않습니다.")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!installed,{installed=false},label={Text("공식 연동 목록")})
            FilterChip(installed,{installed=true},label={Text("이 기기의 앱")})
        }
        if(installed) { InstalledAppPicker(null);return@Column }
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("앱·기능 검색")})
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) { items(entries.filter { it.toString().contains(query,true) }) { item -> val e=item.jsonObject
            Card { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(e.getValue("name").jsonPrimitive.content,style=MaterialTheme.typography.titleMedium);Text(e.getValue("actions").jsonArray.joinToString(" · ") { it.jsonPrimitive.content });Text(e.getValue("status").jsonPrimitive.content,color=MaterialTheme.colorScheme.primary);Text(e.getValue("note").jsonPrimitive.content);Text(e.getValue("source").jsonPrimitive.content,style=MaterialTheme.typography.labelSmall) } }
        } }
    }
}
