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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import com.pleos.routine.ui.theme.*
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

private data class ProposalReview(
    val base:WorkflowDefinition,val proposal:PlannerReply.Proposal,val selectedNodeId:String?,val gleoReviewId:String?,
)
private data class EditorSnapshot(val editor:WorkingEditor,val clarification:String?,val gleoReviewId:String?)
@Composable private fun RoutineScreen(app:RoutineApplication,initialRequest:String,initialAction:String?,initialId:String?,initialOperation:String?,requestSaveFailed:Boolean,initialRequestId:String,freshIntent:Boolean,entryVersion:Int) {
    RoutineTheme {
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
        val c=astryx
        // Inserts after the selected node when there is a single exit; otherwise asks which connection.
        fun insertKind(kind:NodeKind) {
            if(!canEdit())return
            val exits=w.edges.filter { selected==null || it.source==selected }
            if(exits.size==1)runCatching { changed(GraphEditing.insert(w,exits[0].id,kind));step=2 }.onFailure { message=it.message.orEmpty() } else add=true
        }
        @Composable fun TriggerPanel() = Panel(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                IconTile(NodeVisuals.triggerIcon(w.trigger.type),Hue.ORANGE,52.dp)
                Column(Modifier.weight(1f)) { Text("시작 조건",style=MaterialTheme.typography.titleMedium);Text(NodeVisuals.triggerLabel(w.trigger.type),style=MaterialTheme.typography.labelMedium,color=c.textSecondary) }
            }
            Text(RuleDescriptions.trigger(w.trigger),style=MaterialTheme.typography.bodyMedium)
            Text("추가 조건은 시작할 때 확인하고, 흐름 안의 조건 분기는 해당 동작 앞에서 평가합니다.",style=MaterialTheme.typography.bodySmall,color=c.textSecondary)
            OutlinedButton(onClick={triggerEditor=true},enabled=editable) { ButtonIcon(R.drawable.ic_lucide_square_pen);Text("조건 바꾸기") }
        }
        @Composable fun PaletteBar() = Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(AstryxRadius.container)).background(c.surface)
            .border(1.dp,c.borderEmphasized,RoundedCornerShape(AstryxRadius.container)).padding(horizontal=12.dp,vertical=8.dp),
            horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            LucideIcon(R.drawable.ic_lucide_list_plus,size=22.dp,tint=c.iconPrimary)
            Text(if(selected==null)"노드 추가" else "선택 뒤에 추가",style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(end=6.dp))
            listOf(NodeKind.ACTION,NodeKind.IF,NodeKind.WAIT,NodeKind.TIMER,NodeKind.REPEAT,NodeKind.PARALLEL).forEach { kind ->
                val v=NodeVisuals.of(WorkflowNode("palette",kind,""))
                Row(Modifier.clip(RoundedCornerShape(AstryxRadius.element)).clickable(enabled=editable) { insertKind(kind) }.padding(horizontal=8.dp,vertical=4.dp),
                    horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconTile(v.icon,v.hue,40.dp);Text(v.kindLabel,style=MaterialTheme.typography.labelLarge)
                }
            }
        }
        @Composable fun EditorHeader(wide:Boolean) = Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(w.title,style=MaterialTheme.typography.headlineMedium,maxLines=1)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Pill("버전 ${w.revision}");Pill("${w.nodes.size}개 노드",icon=R.drawable.ic_lucide_workflow)
                    Pill(NodeVisuals.triggerLabel(w.trigger.type),icon=NodeVisuals.triggerIcon(w.trigger.type))
                }
            }
            TextButton(onClick={rename=true}) { ButtonIcon(R.drawable.ic_lucide_pencil);Text("이름 변경") }
            if(!wide)TextButton(onClick={add=true}) { ButtonIcon(R.drawable.ic_lucide_list_plus);Text("+ 노드") }
            TextButton(onClick={graphEditor=true}) { ButtonIcon(R.drawable.ic_lucide_link);Text("연결 편집") }
        }
        @Composable fun Inspector() {
            val n=selected?.let { id -> w.nodes.find { it.id==id } }
            if(n==null) Panel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    LucideIcon(R.drawable.ic_lucide_mouse_pointer_click,size=28.dp,tint=c.iconSecondary)
                    Text("캔버스에서 노드를 누르면 여기에서 고칠 수 있어요.",color=c.textSecondary)
                }
            } else Panel(Modifier.fillMaxWidth()) {
                val v=NodeVisuals.of(n)
                Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconTile(v.icon,v.hue,56.dp)
                    Column(Modifier.weight(1f)) { Text(v.kindLabel,style=MaterialTheme.typography.labelMedium,color=c.textSecondary);Text(n.label,style=MaterialTheme.typography.titleMedium) }
                }
                Text(nodeSummary(n),style=MaterialTheme.typography.bodyMedium)
                if(v.badge!=null)StatusBanner("시트는 실행할 때마다 확인합니다. 이동·정지 검증 전에는 움직이지 않습니다.",Tone.WARNING,icon=R.drawable.ic_lucide_hand)
                w.edges.filter { it.source==n.id }.forEach { e ->
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Pill(NodeVisuals.portLabel(n.kind,e.port) ?: "다음",if(e.port=="true")Tone.SUCCESS else Tone.NEUTRAL)
                        LucideIcon(R.drawable.ic_lucide_arrow_right,size=20.dp,tint=c.iconSecondary)
                        Text(w.nodes.find { it.id==e.target }?.label ?: e.target,style=MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={editNode=n}) { ButtonIcon(R.drawable.ic_lucide_square_pen);Text("규칙 수정") }
                    OutlinedButton(enabled=!planning,onClick={scopeEditor=n}) { ButtonIcon(R.drawable.ic_lucide_wand_sparkles);Text("AI에게 수정 요청") }
                }
                if(n.kind in setOf(NodeKind.ACTION,NodeKind.WAIT,NodeKind.TIMER))TextButton(onClick={runCatching { changed(GraphEditing.remove(w,n.id));selected=null }.onFailure { message=it.message.orEmpty() }},
                    colors=ButtonDefaults.textButtonColors(contentColor=c.error)) { ButtonIcon(R.drawable.ic_lucide_trash_2);Text("삭제") }
            }
        }
        @Composable fun IssuesPanel() { if(issues.isNotEmpty())Panel(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                LucideIcon(R.drawable.ic_lucide_triangle_alert,size=24.dp,tint=c.error);Text("확인할 항목 ${issues.size}개",color=c.error,style=MaterialTheme.typography.titleSmall)
            }
            issues.forEach { Text("${it.nodeId?.let { id -> w.nodes.find { n -> n.id==id }?.label ?: id } ?: "흐름"} · ${it.message}",style=MaterialTheme.typography.bodyMedium) }
        } }
        @Composable fun StepTools(wide:Boolean) {
            if(step==2)Panel(Modifier.fillMaxWidth()) {
                SectionTitle(R.drawable.ic_lucide_wand_sparkles,"루틴 전체를 AI로 고치기")
                OutlinedTextField(request,::updateRequest,Modifier.fillMaxWidth(),label={Text("루틴 전체 수정 요청")})
                Button(enabled=loaded&&!planning&&request.isNotBlank(),onClick={askPlanner(request,true)}) { ButtonIcon(R.drawable.ic_lucide_sparkles);Text("변경 제안") }
            }
            if(step==3)Panel(Modifier.fillMaxWidth()) {
                SectionTitle(R.drawable.ic_lucide_flask_conical,"시뮬레이션 · 차량을 변경하지 않습니다")
                val time=@Composable { m:Modifier -> OutlinedTextField(testTime,{testTime=it},m,label={Text("가상 날짜·시간 (서울)")}) }
                val outside=@Composable { m:Modifier -> OutlinedTextField(temp,{temp=it},m,label={Text("외부 온도 °C")}) }
                if(wide) { time(Modifier.fillMaxWidth());outside(Modifier.fillMaxWidth()) } else Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { time(Modifier.weight(2f));outside(Modifier.weight(1f)) }
                FilterChip(testOccupied,{testOccupied=!testOccupied},label={Text("좌석 점유")},leadingIcon={LucideIcon(R.drawable.ic_lucide_armchair,size=20.dp)},colors=routineChipColors())
                Button(enabled=issues.isEmpty(),onClick={ val tested=w;scope.launch { try { val at=LocalDateTime.parse(testTime).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli();val value=temp.toDouble();require(value.isFinite());val result=WorkflowSimulator.simulate(tested,ExecutionContext.simulated(at,value,occupied=testOccupied));val row=RunHistory.simulation(tested,result);withContext(Dispatchers.IO) { dao.putRun(row) };test=result;testRecord=row } catch(e:CancellationException) { throw e } catch(e:Exception) { message="테스트 입력을 확인하세요: ${e.message}" } } }) { ButtonIcon(R.drawable.ic_lucide_play);Text("이 상황으로 테스트") }
                test?.let { result ->
                    val ok=result.status==RunStatus.COMPLETED
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        LucideIcon(if(ok)R.drawable.ic_lucide_circle_check else R.drawable.ic_lucide_triangle_alert,size=24.dp,tint=if(ok)c.success else c.error)
                        Text("${result.status} · 가상 경과 ${result.elapsedMillis/1000}초",color=if(ok)c.success else c.error,style=MaterialTheme.typography.titleSmall)
                    }
                    if(result.semanticHash!=WorkflowJson.semanticHash(w))StatusBanner("규칙이 변경되어 이전 테스트 결과가 무효화되었습니다.",Tone.ERROR)
                    result.events.forEach { e -> Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top=10.dp).size(8.dp).clip(CircleShape).background(c.iconSecondary))
                        Text(RunHistory.eventDescription(testRecord?.let(RunHistory::definition),e),style=MaterialTheme.typography.bodyMedium)
                    } }
                    TextButton(enabled=testRecord!=null,onClick={testRecord?.let { row -> val snapshot=RunHistory.definition(row);editFromHistory(row,result.events.lastOrNull { event->snapshot?.nodes?.any { it.id==event.nodeId && it.kind==NodeKind.IF }==true }?.nodeId) }}) { ButtonIcon(R.drawable.ic_lucide_square_pen);Text("조건을 확인하고 수정하기") }
                }
            }
            if(busy)Panel(Modifier.fillMaxWidth()) {
                SectionTitle(R.drawable.ic_lucide_activity,"실행 상태")
                liveEvents.takeLast(8).forEach { Text("${it.status} · ${it.message}",style=MaterialTheme.typography.bodyMedium) }
            }
        }
        @Composable fun RoutineActions() = Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(enabled=loaded&&issues.isEmpty()&&!planning&&!saving,onClick={saveReview=w}) { ButtonIcon(R.drawable.ic_lucide_save);Text("검토한 루틴 저장") }
            OutlinedButton(onClick={load(WorkflowEdits.clone(w,UUID.randomUUID().toString()));message="원본을 유지하고 복사본을 편집합니다."}) { ButtonIcon(R.drawable.ic_lucide_copy);Text("복제") }
            OutlinedButton(enabled=issues.isEmpty()&&!busy,onClick={val tested=w;scope.launch { if(app.runtime.confirm("실제 기능 테스트","저장된 규칙과 지원 상태를 다시 확인합니다. 실제 차량·연결된 서비스가 변경될 수 있습니다."))app.runtime.requestRun(tested,RunStartCause.ACTUAL_TEST) }}) { ButtonIcon(R.drawable.ic_lucide_car);Text("실제 기능 테스트") }
        }
        val visited=test?.takeIf { step==3 && it.semanticHash==WorkflowJson.semanticHash(w) }?.events?.mapNotNull { it.nodeId }?.toSet().orEmpty()
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(horizontal=28.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                AppHeader("${vehicle.gear.value ?: "—"}  ·  ${vehicle.speedMps.value?.let { "%.1f m/s".format(it) } ?: "속도 미확인"}",editable,busy) { app.runtime.cancel() }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    listOf("루틴 만들기" to R.drawable.ic_lucide_wand_sparkles,"내 루틴" to R.drawable.ic_lucide_list_checks,"연결 둘러보기" to R.drawable.ic_lucide_blocks,
                        "실행 이력" to R.drawable.ic_lucide_history,"연결 설정" to R.drawable.ic_lucide_settings_2).forEachIndexed { i,(label,icon) ->
                        FilterChip(selected=page==i,onClick={if(i!=page)cancelPlanner();page=i},label={Text(label)},leadingIcon={LucideIcon(icon,size=20.dp)},colors=routineChipColors())
                    }
                    Spacer(Modifier.weight(1f))
                    if(draftStatus.isNotBlank())Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                        LucideIcon(if(draftStatus=="초안 저장됨")R.drawable.ic_lucide_circle_check else R.drawable.ic_lucide_save,size=18.dp,tint=c.iconSecondary)
                        Text(draftStatus,style=MaterialTheme.typography.labelMedium,color=c.textSecondary)
                    }
                }
                if(message.isNotBlank())StatusBanner(message,Tone.INFO)
                if(runtimeNotice.isNotBlank())StatusBanner(runtimeNotice,Tone.ERROR)
                if(busy && audioState.isNotBlank())StatusBanner(audioState,Tone.INFO,icon=R.drawable.ic_lucide_music)
                val visibleTimers=timers.filterNot { it.completed }+timers.filter { it.completed }.takeLast(3)
                if(visibleTimers.isNotEmpty())StatusBanner(visibleTimers.joinToString(" · ") {
                    "${it.label}: "+if(it.completed)"타이머 완료" else "남은 ${(it.remainingMillis+999)/1000}초"
                },Tone.INFO,icon=R.drawable.ic_lucide_timer)
                if(requestSaveFailed)StatusBanner("Gleo 요청을 기기에 보관하지 못했습니다. 앱을 닫기 전에 요청을 확인해 주세요.",Tone.ERROR)
                pendingRequest?.let { retained ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(AstryxRadius.element)).background(c.warningMuted).padding(horizontal=16.dp,vertical=6.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        LucideIcon(R.drawable.ic_lucide_mic,size=22.dp,tint=c.onWarningMuted)
                        Text("보관된 Gleo 요청 · 정차 후 검토 · 자동 실행되지 않음",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=c.onWarningMuted)
                        TextButton(enabled=editable,onClick={scope.launch {
                            val cleared=withContext(Dispatchers.IO) { app.gleoReviews.dismiss(retained.id) }
                            if(cleared && pendingRequest?.id==retained.id) { pendingRequest=null;gleoReviewId=null } else message="보관 요청이 변경되었거나 삭제하지 못했습니다. 다시 열어 확인해 주세요."
                        }}) { Text("보관 요청 지우기") }
                    }
                }
                if(editable && controlReview) {
                    val target=saved.find { it.id==initialId }
                    fun finishControl(accepted:Boolean) {
                        scope.launch {
                            val exists=initialId!=null && withContext(Dispatchers.IO) { dao.routine(initialId)!=null }
                            if(canEdit() && app.gleoReviews.reviewControl(initialRequestId,initialId,initialOperation,accepted,exists)) {
                                controlReview=false;message=if(accepted)"Gleo 요청을 검토했습니다." else "Gleo 요청을 거절했습니다."
                            }
                        }
                    }
                    Panel(Modifier.fillMaxWidth()) {
                        SectionTitle(R.drawable.ic_lucide_mic,"Gleo 요청 검토")
                        Text("Gleo 요청 검토 · ${target?.let { WorkflowJson.decode(it.definition).title } ?: "저장된 대상 없음"} · ${initialOperation.orEmpty()}\n요청 확인 후 실제 동작은 내 루틴에서 선택하세요.")
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(enabled=target!=null,onClick={finishControl(true)}) { Text("요청 확인") }
                            TextButton(onClick={finishControl(false)}) { Text("거절") }
                        }
                    }
                }
                when(page) {
                    0 -> {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            listOf("01 생성" to R.drawable.ic_lucide_sparkles,"02 확인" to R.drawable.ic_lucide_eye,"03 수정" to R.drawable.ic_lucide_square_pen,"04 테스트" to R.drawable.ic_lucide_flask_conical).forEachIndexed { i,(label,icon) ->
                                if(i>0)Box(Modifier.width(24.dp).height(2.dp).background(c.borderEmphasized))
                                FilterChip(selected=step==i,onClick={if(i!=step)cancelPlanner();step=i},label={Text(label)},leadingIcon={LucideIcon(icon,size=20.dp)},colors=routineChipColors())
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick={resetPlanner();w=history.undo()},enabled=editable&&history.canUndo) { ButtonIcon(R.drawable.ic_lucide_undo_2);Text("실행 취소") }
                            TextButton(onClick={resetPlanner();w=history.redo()},enabled=editable&&history.canRedo) { ButtonIcon(R.drawable.ic_lucide_redo_2);Text("다시 적용") }
                        }
                        if(!editable) {
                            Panel(Modifier.fillMaxWidth(),padding=28.dp) {
                                Row(horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically) {
                                    IconTile(R.drawable.ic_lucide_lock,Hue.GRAY,80.dp)
                                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Text("지금은 짧은 요청을 초안으로 남길 수 있습니다. 상세 편집과 활성화는 정차 후 이어집니다.",style=MaterialTheme.typography.titleLarge)
                                        Text("자동 실행과 편집 제한은 별도로 적용됩니다. 현재 활성 루틴 ${saved.count { it.enabled }}개",color=c.textSecondary)
                                    }
                                }
                            }
                        } else if(step==0) BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val wide=maxWidth>=1100.dp
                            @Composable fun CreateForm(m:Modifier) = Column(m.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                    IconTile(R.drawable.ic_lucide_wand_sparkles,Hue.PURPLE,60.dp)
                                    Text("어떤 순간을 만들까요?",style=MaterialTheme.typography.headlineLarge)
                                }
                                Text("상황과 원하는 동작을 말하듯 적어 주세요. AI가 만든 규칙은 확인한 뒤 사용할 수 있습니다.",color=c.textSecondary)
                                OutlinedTextField(request,::updateRequest,Modifier.fillMaxWidth(),label={Text("나의 요청")},placeholder={Text("겨울 차박을 준비해 줘. 바깥이 5도 이하면 공조를 23도로 맞춰 줘.")},minLines=3)
                                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
                                    Button(enabled=loaded&&request.isNotBlank()&&!planning,onClick={askPlanner(request,false)}) { ButtonIcon(R.drawable.ic_lucide_sparkles);Text(if(planning)"규칙을 만드는 중…" else "AI로 초안 만들기") }
                                    VoiceButton(app.voice,"request",editable&&!planning)
                                }
                                Text("예시로 시작",style=MaterialTheme.typography.titleSmall,color=c.textSecondary)
                                @Composable fun Example(icon:Int,hue:Hue,title:String,note:String,modifier:Modifier,onClick:()->Unit) =
                                    Row(modifier.clip(RoundedCornerShape(AstryxRadius.container)).background(c.surface).border(1.dp,c.borderEmphasized,RoundedCornerShape(AstryxRadius.container))
                                        .clickable(onClick=onClick).padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                        IconTile(icon,hue,48.dp)
                                        Column { Text(title,style=MaterialTheme.typography.titleSmall);Text(note,style=MaterialTheme.typography.labelSmall,color=c.textSecondary) }
                                    }
                                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Example(R.drawable.ic_lucide_thermometer,Hue.BLUE,"겨울 차박 예시","외기·공조·시트·조명",Modifier.weight(1f)) { load(WorkflowExamples.winterCamping().copy(id=UUID.randomUUID().toString()));message="겨울 차박 예시입니다. 좌석과 조명 영역은 차량 지원 조회로 확인해 주세요." }
                                    Example(R.drawable.ic_lucide_navigation,Hue.GREEN,"출근 예시로 시작","탑승·길 안내·열선",Modifier.weight(1f)) { load(WorkflowExamples.commute());message="예시 템플릿입니다. 회사 좌표와 좌석 영역을 직접 확인해 주세요." }
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Example(R.drawable.ic_lucide_music,Hue.PINK,"휴식 예시","공조·조명·음원",Modifier.weight(1f)) { load(WorkflowExamples.rest());message="휴식 예시입니다. 실제 지원 범위와 시트 STOP 검증이 필요합니다." }
                                    Example(R.drawable.ic_lucide_plus,Hue.GRAY,"빈 흐름","처음부터 직접",Modifier.weight(1f)) { load(WorkflowExamples.blank(UUID.randomUUID().toString())) }
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                    LucideIcon(R.drawable.ic_lucide_info,size=20.dp,tint=c.iconSecondary)
                                    Text("AI 연결 전에도 직접 만들고 테스트할 수 있어요.",color=c.textSecondary,style=MaterialTheme.typography.bodySmall)
                                }
                                if(!wide)FlowCanvas(w,selected,false,::selectNode,{_,_->},Modifier.fillMaxWidth().height(320.dp))
                            }
                            if(wide)Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                                CreateForm(Modifier.weight(0.46f).fillMaxHeight())
                                Column(Modifier.weight(0.54f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                        LucideIcon(R.drawable.ic_lucide_eye,size=20.dp,tint=c.iconSecondary)
                                        Text("현재 초안 · ${w.title}",style=MaterialTheme.typography.titleSmall,color=c.textSecondary,maxLines=1)
                                    }
                                    FlowCanvas(w,selected,false,::selectNode,{_,_->},Modifier.fillMaxWidth().weight(1f))
                                }
                            } else CreateForm(Modifier.fillMaxSize())
                        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val wide=maxWidth>=1100.dp
                            val canvasHeight=(maxHeight-170.dp).coerceAtLeast(420.dp)
                            val onMove={ id:String,pos:NodePosition -> changed(w.copy(nodes=w.nodes.map { if(it.id==id)it.copy(position=pos) else it })) }
                            if(wide)Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                    EditorHeader(true)
                                    if(step==2)PaletteBar()
                                    FlowCanvas(w,selected,step==2,::selectNode,onMove,Modifier.fillMaxWidth().height(if(step==2)canvasHeight-64.dp else canvasHeight),highlight=visited)
                                    RoutineActions()
                                }
                                Column(Modifier.width(400.dp).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) { Inspector();IssuesPanel();StepTools(true);TriggerPanel() }
                            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                EditorHeader(false);TriggerPanel()
                                FlowCanvas(w,selected,step==2,::selectNode,onMove,Modifier.fillMaxWidth().height(400.dp),highlight=visited)
                                Inspector();IssuesPanel();StepTools(false);RoutineActions()
                            }
                        }
                    }
                    1 -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(saved.isEmpty())item { EmptyState(R.drawable.ic_lucide_list_checks,"아직 저장한 루틴이 없습니다. 예시로 시작해 보세요.") }
                        items(saved,key={it.id}) { row -> val routine=remember(row.definition) { WorkflowJson.decode(row.definition) }
                            Panel(Modifier.fillMaxWidth()) {
                                Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                    IconTile(NodeVisuals.triggerIcon(routine.trigger.type),Hue.ORANGE,56.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(routine.title,style=MaterialTheme.typography.titleLarge)
                                        Text("${NodeVisuals.triggerLabel(routine.trigger.type)} · 버전 ${routine.revision} · 우선순위 ${row.priority}",color=c.textSecondary)
                                    }
                                    Pill(if(row.enabled)"활성" else "비활성",if(row.enabled)Tone.SUCCESS else Tone.NEUTRAL,R.drawable.ic_lucide_power)
                                }
                                MiniFlow(routine)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Button(onClick={load(routine)},enabled=editable,modifier=Modifier.testTag("open-routine-${routine.id}")) { ButtonIcon(R.drawable.ic_lucide_square_pen);Text("열기") }
                                    OutlinedButton(onClick={scope.launch {
                                        if(row.enabled)withContext(Dispatchers.IO) { dao.enable(row.id,false) }
                                        else if(row.testedHash!=WorkflowJson.semanticHash(routine))message="현재 규칙의 시뮬레이션을 통과하고 저장한 뒤 활성화하세요."
                                        else if(WorkflowValidator.validate(routine).isNotEmpty())message="규칙 오류를 먼저 수정하세요."
                                        else {
                                            val conflicts=saved.filter { it.enabled && it.id!=row.id && it.priority==row.priority }.filter { other -> val keys=WorkflowJson.decode(other.definition).nodes.mapNotNull { it.action }.filter(ActionResources::isWrite).map(ActionResources::key);routine.nodes.mapNotNull { it.action }.any { ActionResources.key(it) in keys } }
                                            if(conflicts.isNotEmpty())message="같은 우선순위의 활성 루틴과 제어 자원이 겹칩니다. 우선순위를 변경하세요."
                                            else if(app.runtime.confirm("자동 실행 활성화","${routine.title}\n새 조건 진입부터 감시합니다. 처음부터 참인 조건과 과거 실행은 재생하지 않습니다. 시트 이동은 실행할 때마다 확인합니다.")) { withContext(Dispatchers.IO) { dao.enable(row.id,true) };app.runtime.startMonitoring() }
                                        }
                                    }},enabled=editable) { ButtonIcon(R.drawable.ic_lucide_power);Text(if(row.enabled)"끄기" else "활성화") }
                                    OutlinedButton(onClick={app.runtime.requestRun(routine)},enabled=!busy&&editable) { ButtonIcon(R.drawable.ic_lucide_play);Text("지금 실행") }
                                    TextButton(onClick={scope.launch { withContext(Dispatchers.IO) { dao.put(row.copy(priority=row.priority+1)) } }},enabled=editable) { ButtonIcon(R.drawable.ic_lucide_chevrons_up);Text("우선순위 +1") }
                                }
                            }
                        }
                    }
                    2 -> CatalogPanel(app)
                    3 -> LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) { if(runs.isEmpty())item { EmptyState(R.drawable.ic_lucide_history,"시뮬레이션과 실제 실행 결과가 이곳에 표시됩니다.") };items(runs,key={it.id}) { row ->
                        val eventFlow:Flow<List<RunEventRecord>?> = remember(row.id) { dao.observeEvents(row.id) }
                        val journalEvents by eventFlow.collectAsState(initial=null)
                        val partial=remember(journalEvents) { journalEvents.orEmpty().mapNotNull { runCatching { WorkflowJson.format.decodeFromString<ExecutionEvent>(it.eventJson) }.getOrNull() } }
                        RunHistoryCard(row,partial,editable,app.planner,evidenceReady=journalEvents!=null,onEdit={ nodeId->editFromHistory(row,nodeId,partial) })
                    } }
                    4 -> if(editable)SettingsPanel(app,connection) else StatusBanner("연결 설정은 정차 후 확인할 수 있습니다.",Tone.WARNING,icon=R.drawable.ic_lucide_lock)
                }
            }
        }
        approval?.takeIf { editable }?.let { q -> AlertDialog(onDismissRequest={q.answer.complete(false)},icon={LucideIcon(R.drawable.ic_lucide_shield_check,size=32.dp)},title={Text(q.title)},text={Text(q.detail)},confirmButton={Button(enabled=editable,onClick={q.answer.complete(true)}) { Text("확인") }},dismissButton={TextButton(onClick={q.answer.complete(false)}) { Text("취소") }}) }
        candidate?.takeIf { editable }?.let { review ->
            val next=review.proposal.workflow
            AlertDialog(onDismissRequest={candidate=null},icon={LucideIcon(R.drawable.ic_lucide_wand_sparkles,size=32.dp)},title={Text("AI 변경 제안 확인")},
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
        if(editable && rename) { var name by remember { mutableStateOf(w.title) };AlertDialog(onDismissRequest={rename=false},icon={LucideIcon(R.drawable.ic_lucide_pencil,size=32.dp)},title={Text("루틴 이름")},text={OutlinedTextField(name,{name=it.take(100)},label={Text("이름")})},confirmButton={Button(enabled=name.isNotBlank(),onClick={changed(w.copy(title=name,revision=w.revision+1));rename=false}) { Text("이름 저장") }},dismissButton={TextButton(onClick={rename=false}) { Text("취소") }}) }
        if(editable && add)AlertDialog(onDismissRequest={add=false},icon={LucideIcon(R.drawable.ic_lucide_list_plus,size=32.dp)},title={Text("선택한 연결에 노드 삽입")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val edges=w.edges.filter { selected==null || it.source==selected }
            fun label(id:String)=w.nodes.find { it.id==id }?.label ?: id
            if(edges.isEmpty())Text("나가는 연결이 있는 노드를 선택하세요.")
            edges.forEach { edge ->
                val from=w.nodes.find { it.id==edge.source }
                Text("${label(edge.source)} · ${from?.let { NodeVisuals.portLabel(it.kind,edge.port) } ?: "다음"} → ${label(edge.target)}",style=MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf(NodeKind.ACTION,NodeKind.IF,NodeKind.WAIT,NodeKind.TIMER,NodeKind.REPEAT,NodeKind.PARALLEL).forEach { kind ->
                        val v=NodeVisuals.of(WorkflowNode("palette",kind,""))
                        OutlinedButton(onClick={runCatching { changed(GraphEditing.insert(w,edge.id,kind));add=false;step=2 }.onFailure { message=it.message.orEmpty() }},contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) { ButtonIcon(v.icon);Text(v.kindLabel) }
                    }
                }
            }
        }},confirmButton={TextButton(onClick={add=false}) { Text("닫기") }})
    }
}

private fun nodeSummary(n:WorkflowNode):String = RuleDescriptions.node(n)

/** App title, vehicle state and edit availability, with the stop control while a run is active. */
@Composable private fun AppHeader(vehicleText:String,editable:Boolean,busy:Boolean,onStop:()->Unit) {
    val c=astryx
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(AstryxRadius.container)).background(c.accent),contentAlignment=Alignment.Center) {
            LucideIcon(R.drawable.ic_lucide_workflow,size=30.dp,tint=c.onAccent)
        }
        Column(Modifier.weight(1f)) {
            Text("PLEOS  /  ROUTINE",color=c.textSecondary,style=MaterialTheme.typography.labelLarge)
            Text("나의 이동을, 나의 방식으로",style=MaterialTheme.typography.headlineSmall)
        }
        Pill(vehicleText,Tone.NEUTRAL,R.drawable.ic_lucide_gauge,large=true)
        Pill(if(editable)"편집 가능" else "주행 제한 · 초안 보관",if(editable)Tone.SUCCESS else Tone.WARNING,if(editable)R.drawable.ic_lucide_circle_check else R.drawable.ic_lucide_lock,large=true)
        if(busy)Button(onClick=onStop,colors=ButtonDefaults.buttonColors(containerColor=c.error,contentColor=c.onError)) { ButtonIcon(R.drawable.ic_lucide_circle_stop);Text("실행 중지") }
    }
}

/** The routine at a glance: node icons in flow order (left to right), without labels. */
@Composable private fun MiniFlow(w:WorkflowDefinition) {
    val c=astryx
    val ordered=w.nodes.sortedWith(compareBy({ it.position.x },{ it.position.y }))
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
        ordered.take(10).forEachIndexed { i,n ->
            if(i>0)Box(Modifier.width(14.dp).height(2.dp).background(c.borderEmphasized))
            val v=NodeVisuals.of(n);IconTile(v.icon,v.hue,36.dp)
        }
        if(ordered.size>10)Text("+${ordered.size-10}",color=c.textSecondary,style=MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun EmptyState(icon:Int,text:String) {
    Panel(Modifier.fillMaxWidth(),padding=32.dp) {
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            IconTile(icon,Hue.GRAY,56.dp);Text(text,style=MaterialTheme.typography.titleSmall,color=astryx.textSecondary)
        }
    }
}

@Composable private fun SettingsPanel(app:RoutineApplication,connection:String) {
    val activity=LocalActivity.current as? MainActivity
    val scope=rememberCoroutineScope();val caps by app.platform.capabilities.collectAsState()
    var url by remember { mutableStateOf(app.planner.endpoint) };var token by remember { mutableStateOf(app.planner.token) };var result by remember { mutableStateOf("") }
    val c=astryx
    Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("연결과 지원 상태",style=MaterialTheme.typography.headlineMedium)
        StatusBanner(connection,Tone.NEUTRAL,icon=R.drawable.ic_lucide_link)
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle(R.drawable.ic_lucide_sparkles,"AI 서버")
            Text("AI 제공자 키는 서버에서만 관리합니다. 앱에는 개발 서버 접속 토큰을 입력하세요.",color=c.textSecondary)
            OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text("개발 서버 주소")})
            OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),label={Text("서버 접속 토큰 · 32자 이상")},visualTransformation=PasswordVisualTransformation())
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick={try { require(token.length>=32);app.planner.endpoint=url;app.planner.token=token;result="연결 설정을 저장했습니다." } catch(_:Exception) { result="주소 형식과 32자 이상의 접속 토큰을 확인하세요." }}) { ButtonIcon(R.drawable.ic_lucide_save);Text("연결 저장") }
                OutlinedButton(onClick={scope.launch { result=try { val s=app.planner.request("/v1/status");"AI 호출: ${s["aiEnabled"]} · ${s["message"]?.jsonPrimitive?.content.orEmpty()}" } catch(e:Exception) { e.message.orEmpty() } }}) { ButtonIcon(R.drawable.ic_lucide_activity);Text("서버 확인") }
            }
            if(result.isNotBlank())Text(result)
        }
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle(R.drawable.ic_lucide_car,"차량 지원")
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick={scope.launch { app.platform.refreshCapabilities() }}) { ButtonIcon(R.drawable.ic_lucide_search);Text("차량 지원 조회") }
                OutlinedButton(onClick={activity?.requestVehiclePermissions()}) { ButtonIcon(R.drawable.ic_lucide_shield_check);Text("속도·알림 권한") }
            }
            caps.forEach { cap -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(AstryxRadius.element)).background(c.body).padding(12.dp)) {
                Text("${cap.type} · ${cap.access}",style=MaterialTheme.typography.titleSmall);Text(cap.areas.joinToString(),color=c.textSecondary);Text(cap.ranges.toString(),style=MaterialTheme.typography.bodySmall)
                if(cap.message.isNotBlank())Text(cap.message,color=c.textSecondary)
            } }
        }
        StatusBanner("시트 제한 이동·STOP 및 좌석 점유 유효성은 대상 AVD 증거가 확보되기 전까지 잠깁니다. 시트가 든 루틴은 실행할 때 동의하면 시트 단계만 미실행으로 기록하고 나머지를 실행합니다. 읽기 값은 운전자 신원 확인이 아닙니다.",Tone.WARNING,icon=R.drawable.ic_lucide_armchair)
    }
}

@Composable private fun CatalogPanel(app:RoutineApplication) {
    var query by remember { mutableStateOf("") }
    var installed by remember { mutableStateOf(false) }
    val entries=remember { runCatching { Json.parseToJsonElement(app.assets.open("connector-catalog.json").bufferedReader().use { it.readText() }).jsonObject.getValue("entries").jsonArray }.getOrDefault(JsonArray(emptyList())) }
    val c=astryx
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("연결 가능한 기능을 먼저 확인하세요",style=MaterialTheme.typography.headlineMedium)
        Text("공개 문서, 계정 권한, 이 차량에서의 동작 여부는 각각 다릅니다. 목록에 있다는 이유로 연결·실행이 보장되지는 않습니다.",color=c.textSecondary)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!installed,{installed=false},label={Text("공식 연동 목록")},leadingIcon={LucideIcon(R.drawable.ic_lucide_blocks,size=20.dp)},colors=routineChipColors())
            FilterChip(installed,{installed=true},label={Text("이 기기의 앱")},leadingIcon={LucideIcon(R.drawable.ic_lucide_app_window,size=20.dp)},colors=routineChipColors())
        }
        if(installed) { InstalledAppPicker(null);return@Column }
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("앱·기능 검색")},leadingIcon={LucideIcon(R.drawable.ic_lucide_search,size=22.dp)})
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) { items(entries.filter { it.toString().contains(query,true) }) { item -> val e=item.jsonObject
            Panel(Modifier.fillMaxWidth(),padding=16.dp) {
                Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconTile(R.drawable.ic_lucide_blocks,Hue.CYAN,48.dp)
                    Column(Modifier.weight(1f)) { Text(e.getValue("name").jsonPrimitive.content,style=MaterialTheme.typography.titleMedium);Text(e.getValue("actions").jsonArray.joinToString(" · ") { it.jsonPrimitive.content },color=c.textSecondary) }
                    Pill(e.getValue("status").jsonPrimitive.content,Tone.INFO)
                }
                Text(e.getValue("note").jsonPrimitive.content,style=MaterialTheme.typography.bodyMedium)
                Text(e.getValue("source").jsonPrimitive.content,style=MaterialTheme.typography.labelSmall,color=c.textSecondary)
            }
        } }
    }
}
