package com.pleos.routine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pleos.routine.core.*
import com.pleos.routine.data.ClarificationDraft

@Composable
fun ClarificationDialog(draft: ClarificationDraft, planning: Boolean, feedback: String,
                        onAnswer: (Int,String)->Unit, onSend: ()->Unit, onCancel: ()->Unit) {
    AlertDialog(onDismissRequest=onCancel,title={Text("초안을 완성할 질문")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(draft.explanation)
            draft.preview?.let { w ->
                Text("아직 결정하지 않은 부분이 있는 미리보기")
                FlowCanvas(w,null,false,{},{_,_->},Modifier.fillMaxWidth().height(200.dp))
                w.nodes.filter { it.kind==NodeKind.IF || it.kind==NodeKind.ACTION }.forEach { node ->
                    val action=node.action
                    Text("${node.label} · "+if(action?.type==ActionType.MOVE_SEAT_LIMITED && action.area.isBlank())
                        "좌석 미정" else RuleDescriptions.node(node))
                }
            }
            draft.questions.forEachIndexed { index,question ->
                OutlinedTextField(draft.answers[index],{onAnswer(index,it)},Modifier.fillMaxWidth(),
                    enabled=!planning,label={Text(question)})
            }
            if(feedback.isNotBlank())Text(feedback,color=MaterialTheme.colorScheme.error)
        }},confirmButton={Button(onClick=onSend,enabled=!planning && draft.answers.all { it.isNotBlank() }) {
            Text(if(planning)"답변 확인 중…" else "답변 보내기")
        }},dismissButton={TextButton(onClick=onCancel){Text("요청 취소")}})
}

@Composable
fun ScopedRequestDialog(node: WorkflowNode,onSubmit: (String)->Unit,onClose: ()->Unit) {
    var text by remember(node) { mutableStateOf("") }
    AlertDialog(onDismissRequest=onClose,title={Text("${node.label} 수정 요청")},text={Column {
        Text(RuleDescriptions.node(node))
        OutlinedTextField(text,{text=it.take(4000)},Modifier.fillMaxWidth(),label={Text("이 노드에서 바꿀 내용")})
    }},confirmButton={Button(enabled=text.isNotBlank(),onClick={onSubmit(text)}) { Text("수정안 보기") }},
        dismissButton={TextButton(onClick=onClose) { Text("취소") }})
}

@Composable
fun NamedSaveDialog(workflow: WorkflowDefinition,saving: Boolean,onSave: (String)->Unit,onClose: ()->Unit) {
    var name by remember(workflow) { mutableStateOf(workflow.title) }
    AlertDialog(onDismissRequest={if(!saving)onClose()},title={Text("이대로 ‘${name.trim()}’ 루틴을 저장할까요?")},
        text={Column {
            OutlinedTextField(name,{name=it.take(100)},Modifier.fillMaxWidth(),enabled=!saving,label={Text("저장할 이름")})
            Text("내 루틴에 비활성으로 저장합니다. 실행은 내 루틴에서 시작할 수 있습니다.")
        }},confirmButton={Button(enabled=!saving && name.isNotBlank(),onClick={onSave(name.trim())}) { Text("저장") }},
        dismissButton={TextButton(enabled=!saving,onClick=onClose) { Text("계속 편집") }})
}
