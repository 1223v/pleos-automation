package com.pleos.routine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import com.pleos.routine.R
import com.pleos.routine.ui.theme.AstryxRadius
import com.pleos.routine.ui.theme.Hue
import com.pleos.routine.ui.theme.astryx
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pleos.routine.core.*
import com.pleos.routine.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID

@Composable fun RunHistoryCard(row:RunRecord,partial:List<ExecutionEvent>,editable:Boolean,client:PlannerClient,evidenceReady:Boolean,onEdit:(String?)->Unit) {
    val snapshot=remember(row) { RunHistory.definition(row) }
    val events=RunHistory.events(row,partial)
    var expanded by remember(row.id) { mutableStateOf(false) }
    var explanation by remember(row,events) { mutableStateOf<RunExplanation?>(null) }
    var error by remember(row,events) { mutableStateOf("") }
    var explaining by remember(row,events) { mutableStateOf(false) }
    var omitted by remember(row,events) { mutableIntStateOf(0) }
    val scope=rememberCoroutineScope()
    val latest by rememberUpdatedState(row)
    val latestEvidence by rememberUpdatedState(events)
    val c=astryx
    val (icon,hue)=when {
        row.simulation -> R.drawable.ic_lucide_flask_conical to Hue.BLUE
        row.state=="COMPLETED" -> R.drawable.ic_lucide_circle_check to Hue.GREEN
        row.state=="RUNNING" -> R.drawable.ic_lucide_activity to Hue.YELLOW
        row.state in setOf("FAILED","UNKNOWN","BLOCKED") -> R.drawable.ic_lucide_triangle_alert to Hue.RED
        else -> R.drawable.ic_lucide_history to Hue.GRAY
    }
    Card(onClick={expanded=!expanded},shape=RoundedCornerShape(AstryxRadius.container),colors=CardDefaults.cardColors(containerColor=c.surface),
        border=BorderStroke(1.dp,c.borderEmphasized)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                IconTile(icon,hue,48.dp)
                Column(Modifier.weight(1f)) {
                    Text(snapshot?.title ?: row.routineId,style=MaterialTheme.typography.titleMedium)
                    Text("${if(row.simulation)"시뮬레이션" else "실제 실행"} · ${row.state} · 버전 ${row.revision ?: RunHistory.result(row)?.revision ?: "미기록"}",color=c.textSecondary)
                }
                LucideIcon(R.drawable.ic_lucide_chevron_down,size=24.dp,tint=c.iconSecondary)
            }
            RunHistory.unexecuted(snapshot,events)?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Text("시작 원인: ${RunHistory.cause(row)}")
            Text(java.time.Instant.ofEpochMilli(row.createdAt).atZone(java.time.ZoneId.of("Asia/Seoul")).toString())
            if(expanded) {
                if(row.state=="RUNNING")Text("실행 중 · 현재까지 저장된 단계입니다.")
                else if(row.result==null)Text("종료 결과 미기록 · 결과 불명 · 과거 명령은 재전송하지 않습니다.")
                if(snapshot==null)Text("실행 당시 규칙이 없거나 일치하지 않습니다. 최신 규칙으로 대신 설명하지 않습니다.")
                snapshot?.let { w ->
                    Text("당시 시작 조건: ${RuleDescriptions.trigger(w.trigger)}")
                    Text("규칙 해시: ${row.semanticHash}",style=MaterialTheme.typography.labelSmall)
                }
                events.forEach { Text(RunHistory.eventDescription(snapshot,it)) }
                if(!evidenceReady)Text("저장된 단계를 읽는 중입니다.")
                else if(events.isEmpty())Text("저장된 단계가 없어 개별 동작의 실행 여부를 확인할 수 없습니다.")
                snapshot?.let { w ->
                    TextButton(enabled=editable && evidenceReady,onClick={onEdit(null)}) { ButtonIcon(R.drawable.ic_lucide_zap);Text("시작 조건 편집") }
                    w.nodes.filter { it.kind==NodeKind.IF }.forEach { n ->
                        TextButton(enabled=editable && evidenceReady,onClick={onEdit(n.id)}) { ButtonIcon(R.drawable.ic_lucide_split);Text("조건 편집 · ${n.label}") }
                    }
                }
                Text("AI 설명은 참고용입니다. 위 실행 상태와 관찰 기록이 기준이며 규칙을 자동 변경하지 않습니다.")
                OutlinedButton(enabled=editable && evidenceReady && snapshot!=null && row.state!="RUNNING" && !explaining,onClick={
                    explaining=true;error="";explanation=null
                    scope.launch {
                        try {
                            val request=RunHistory.explanationRequest(row,partial,UUID.randomUUID().toString())
                            val response=client.request("/v1/run-explanations",request)
                            val answer=RunExplanation.read(response,request)
                            if(latest==row && latestEvidence==events) { explanation=answer;omitted=request.getValue("omittedEventCount").jsonPrimitive.int }
                        } catch(e:CancellationException) { throw e } catch(e:Exception) { if(latest==row && latestEvidence==events)error=e.message ?: "설명을 가져오지 못했습니다." }
                        finally { if(latest==row && latestEvidence==events)explaining=false }
                    }
                }) { ButtonIcon(R.drawable.ic_lucide_sparkles);Text(if(explaining)"설명 요청 중" else "AI 실행 설명 요청") }
                if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                explanation?.let { answer ->
                    Text("AI 설명 · ${answer.summary}")
                    if(omitted>0)Text("이벤트 ${omitted}개 생략 · 원본 기록은 이력에 보존됩니다.")
                    answer.details.forEach { Text("${it.explanation}\n근거 단계: ${it.eventSequences.joinToString()}") }
                    answer.limitations.forEach { Text(it) }
                }
            }
        }
    }
}
