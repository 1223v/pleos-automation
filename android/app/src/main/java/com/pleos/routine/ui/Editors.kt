package com.pleos.routine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pleos.routine.core.*
import com.pleos.routine.vehicle.Capability
import com.pleos.routine.vehicle.editableVehicleAreas
import java.util.UUID

fun actionName(type:ActionType)=when(type) {
    ActionType.NAVIGATE->"목적지 길 안내";ActionType.CANCEL_NAVIGATION->"길 안내 취소"
    ActionType.SET_HVAC_TARGET->"공조 목표 온도";ActionType.SET_AMBIENT_BRIGHTNESS->"앰비언트 밝기";ActionType.SET_AMBIENT_COLOR->"앰비언트 색상"
    ActionType.SET_SEAT_HEAT->"시트 열선";ActionType.MOVE_SEAT_LIMITED->"시트 제한 이동"
    ActionType.PLAY_LOCAL_AUDIO->"휴식 음원 재생";ActionType.STOP_LOCAL_AUDIO->"휴식 음원 중지"
    ActionType.OPEN_APP->"앱 열기";ActionType.MEDIA_CONTROL->"미디어 앱 제어 (지원 확인 필요)"
    ActionType.CALENDAR_READ->"일정 읽기";ActionType.HA_CALL->"스마트홈 동작";ActionType.PLACE_SEARCH->"장소 찾기";ActionType.WHAT3WORDS_RESOLVE->"세 단어 주소 찾기"
}
@Composable private fun <T> Choice(label:String,value:T,options:List<T>,name:(T)->String={it.toString()},onChange:(T)->Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()) { Text("$label · ${name(value)} ▾") };DropdownMenu(expanded=open,onDismissRequest={open=false}) { options.forEach { item -> DropdownMenuItem(text={Text(name(item))},onClick={onChange(item);open=false}) } } }
}
@Composable private fun NumberInput(label:String,value:Number?,onChange:(Double?)->Unit) {
    var raw by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(raw,{raw=it;onChange(it.toDoubleOrNull()?.takeIf(Double::isFinite))},label={Text(label)},modifier=Modifier.fillMaxWidth(),isError=raw.isNotBlank()&&raw.toDoubleOrNull()==null)
}
@Composable private fun ConditionEditor(c:ConditionSpec,onChange:(ConditionSpec)->Unit) {
    Choice("비교할 상태",c.field,ConditionField.entries.toList(),name={when(it) { ConditionField.OUTSIDE_TEMPERATURE->"외부 온도 °C";ConditionField.GEAR->"기어";ConditionField.SPEED->"실제 속도 m/s";ConditionField.SEAT_OCCUPIED->"좌석 점유";ConditionField.TIME_WINDOW->"시간 구간";ConditionField.WEEKDAY->"요일" }}) { f -> onChange(ConditionSpec(f,if(f in setOf(ConditionField.GEAR,ConditionField.SEAT_OCCUPIED))Comparison.EQ else Comparison.LTE,value=5.0,text="P",flag=true,startMinute=420,endMinute=540,weekdays=listOf(1,2,3,4,5))) }
    when(c.field) {
        ConditionField.OUTSIDE_TEMPERATURE,ConditionField.SPEED -> { Choice("비교",c.operator,Comparison.entries.toList(),name={when(it) { Comparison.LTE->"이하 ≤";Comparison.LT->"미만 <";Comparison.GTE->"이상 ≥";Comparison.GT->"초과 >";Comparison.EQ->"같음 =";Comparison.NE->"다름 ≠" }}) { onChange(c.copy(operator=it)) };NumberInput("기준값",c.value) { onChange(c.copy(value=it)) } }
        ConditionField.GEAR -> Choice("기어",c.text ?: "P",listOf("P","R","N","D")) { onChange(c.copy(text=it,operator=Comparison.EQ)) }
        ConditionField.SEAT_OCCUPIED -> { Switch(c.flag ?: true,{onChange(c.copy(flag=it,operator=Comparison.EQ))});Text("${if(c.flag!=false)"탑승" else "비어 있음"} · 사용자 신원과 별개이며 유효한 센서 입력이 필요합니다.") }
        ConditionField.TIME_WINDOW -> { NumberInput("시작 · 자정부터 분 (07:00 = 420)",c.startMinute) { onChange(c.copy(startMinute=it?.takeIf { v -> v%1==0.0 && v in 0.0..1439.0 }?.toInt())) };NumberInput("종료 · 자정부터 분 (09:00 = 540)",c.endMinute) { onChange(c.copy(endMinute=it?.takeIf { v -> v%1==0.0 && v in 0.0..1439.0 }?.toInt())) };Text("시작 포함, 종료 제외 · 같은 날의 구간") }
        ConditionField.WEEKDAY -> Days(c.weekdays) { onChange(c.copy(weekdays=it)) }
    }
}
@Composable private fun Days(days:List<Int>,onChange:(List<Int>)->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) { listOf("월","화","수","목","금","토","일").forEachIndexed { i,s -> FilterChip(i+1 in days,{onChange(if(i+1 in days)days-(i+1) else (days+(i+1)).sorted())},label={Text(s)}) } }
}
@Composable fun NodeEditor(initial:WorkflowNode,capabilities:List<Capability>,onClose:()->Unit,onApply:(WorkflowNode)->Unit) {
    var n by remember(initial) { mutableStateOf(initial) };var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onClose,title={Text("${initial.label} 수정")},text={Column(Modifier.heightIn(max=600.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(n.label,{n=n.copy(label=it)},label={Text("카드 이름")},modifier=Modifier.fillMaxWidth())
        if(n.kind==NodeKind.ACTION) {
            val a=n.action ?: ActionSpec(ActionType.SET_HVAC_TARGET)
            fun update(next:ActionSpec) { n=n.copy(action=next) }
            Choice("동작",a.type,ActionType.entries.toList(),::actionName) { type -> if(type!=a.type) update(ActionSpec(type,area=if(type in ActionResources.parkedTypes)"" else ActionSpec(type).area,value=when(type) { ActionType.SET_HVAC_TARGET->23.0;ActionType.SET_AMBIENT_BRIGHTNESS->30.0;ActionType.SET_SEAT_HEAT->2.0;else->null },text=if(type==ActionType.SET_AMBIENT_COLOR)"#FF4B85DB" else null,targetId=if(type==ActionType.PLAY_LOCAL_AUDIO)"rest-tone" else null,axis=if(type==ActionType.MOVE_SEAT_LIMITED)SeatAxis.FORE_AFT else null,direction=if(type==ActionType.MOVE_SEAT_LIMITED)MoveDirection.BACKWARD else null)) }
            if(a.type in ActionResources.parkedTypes) {
                val options=editableVehicleAreas(a,capabilities)
                val current=options.find { it.id==a.area }
                if(options.isNotEmpty()) {
                    Choice("차량 영역",a.area,options.map { it.id },name={ id -> options.find { it.id==id }?.label ?: "영역 선택" }) {
                        update(a.copy(area=it))
                    }
                } else Text("연결 설정에서 차량 지원을 조회하면 이 동작의 영역을 선택할 수 있습니다.")
                if(current==null && a.area.isNotBlank())Text("현재 초안의 영역 ${a.area} · 차량 지원 미확인")
                if(a.type==ActionType.MOVE_SEAT_LIMITED)Text("앞뒤·등받이 이동과 정지 검증이 끝날 때까지 시트는 실제로 움직이지 않습니다. 실제 실행 때 동의하면 이 단계만 ‘미실행’으로 기록하고 나머지 동작을 실행합니다.")
            }
            when(a.type) {
                ActionType.SET_HVAC_TARGET,ActionType.SET_AMBIENT_BRIGHTNESS,ActionType.SET_SEAT_HEAT -> NumberInput(when(a.type) { ActionType.SET_HVAC_TARGET->"목표 온도 °C";ActionType.SET_AMBIENT_BRIGHTNESS->"밝기 %";else->"열선 단계" },a.value) { update(a.copy(value=it)) }
                ActionType.MOVE_SEAT_LIMITED -> { Choice("축",a.axis ?: SeatAxis.FORE_AFT,SeatAxis.entries.toList(),name={if(it==SeatAxis.FORE_AFT)"앞뒤" else "등받이"}) { update(a.copy(axis=it)) };Choice("방향",a.direction ?: MoveDirection.BACKWARD,MoveDirection.entries.toList(),name={if(it==MoveDirection.FORWARD)"앞으로" else "뒤로"}) { update(a.copy(direction=it)) };Text("전체 실행 전 1회 확인 · 검증된 시간 한도 · 취소 시 STOP 우선") }
                ActionType.NAVIGATE -> { OutlinedTextField(a.text.orEmpty(),{update(a.copy(text=it))},label={Text("목적지 이름")});NumberInput("위도",a.latitude) { update(a.copy(latitude=it)) };NumberInput("경도",a.longitude) { update(a.copy(longitude=it)) } }
                ActionType.SET_AMBIENT_COLOR,ActionType.PLACE_SEARCH,ActionType.WHAT3WORDS_RESOLVE -> OutlinedTextField(a.text.orEmpty(),{update(a.copy(text=it))},label={Text(if(a.type==ActionType.SET_AMBIENT_COLOR)"색상 #FFRRGGBB" else "검색할 장소")})
                ActionType.OPEN_APP -> InstalledAppPicker(a.targetId) { update(a.copy(targetId=it.packageName)) }
                ActionType.MEDIA_CONTROL,ActionType.HA_CALL -> { OutlinedTextField(a.targetId.orEmpty(),{update(a.copy(targetId=it))},label={Text("공식 연결 대상 ID")});if(a.type==ActionType.HA_CALL) { Choice("스마트홈 동작",a.text ?: "light.turn_on",listOf("light.turn_on","light.turn_off","climate.set_temperature")) { update(a.copy(text=it)) };if(a.text=="climate.set_temperature")NumberInput("목표 온도 (기기 단위)",a.value) { update(a.copy(value=it)) } };Text("서버 허용 목록·계정 권한과 차량 앱 지원을 별도로 확인합니다.") }
                else -> Text(if(a.type==ActionType.PLAY_LOCAL_AUDIO)"앱에 포함된 자체 휴식 음원을 반복 재생합니다." else "연결 상태와 권한은 실행 전에 다시 확인합니다.")
            }
        }
        n.condition?.let { c -> ConditionEditor(c) { n=n.copy(condition=it) } }
        if(n.kind in setOf(NodeKind.WAIT,NodeKind.TIMER))NumberInput("대기 시간 · 초",n.durationSeconds) { n=n.copy(durationSeconds=it?.takeIf { v -> v%1==0.0 }?.toLong()) }
        if(n.kind==NodeKind.REPEAT)NumberInput("반복 횟수 · 1~20",n.repeatCount) { n=n.copy(repeatCount=it?.takeIf { v -> v%1==0.0 }?.toInt()) }
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
    }},confirmButton={Button(onClick={
        val a=n.action
        val options=a?.let { editableVehicleAreas(it,capabilities) }.orEmpty()
        val errors=a?.let(WorkflowValidator::actionErrors).orEmpty()
        when {
            a!=null && a.type in ActionResources.parkedTypes && options.isNotEmpty() && options.none { it.id==a.area } -> error="조회된 차량 영역에서 선택해 주세요."
            errors.isNotEmpty() -> error=errors.joinToString("\n")
            else -> onApply(n)
        }
    }) { Text("변경 적용") }},dismissButton={TextButton(onClick=onClose) { Text("취소") }})
}
@Composable fun TriggerEditor(initial:TriggerDefinition,onClose:()->Unit,onApply:(TriggerDefinition)->Unit) {
    var t by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest=onClose,title={Text("언제 시작할까요?")},text={Column(Modifier.heightIn(max=600.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Choice("시작",t.type,TriggerType.entries.toList(),name={when(it) { TriggerType.MANUAL->"직접 실행";TriggerType.VOICE->"음성 요청 후 확인";TriggerType.SEAT_OCCUPIED->"좌석이 비었다가 탑승할 때";TriggerType.TIME->"정해진 시간";TriggerType.CONDITION_ENTER->"상태 조건이 새로 충족될 때" }}) { type -> t=t.copy(type=type,condition=if(type==TriggerType.CONDITION_ENTER)ConditionSpec(ConditionField.OUTSIDE_TEMPERATURE,value=5.0) else null,minuteOfDay=if(type==TriggerType.TIME)480 else null,weekdays=if(type==TriggerType.TIME)listOf(1,2,3,4,5) else emptyList()) }
        if(t.type==TriggerType.TIME) { NumberInput("시작 시각 · 자정부터 분",t.minuteOfDay) { t=t.copy(minuteOfDay=it?.takeIf { v -> v%1==0.0 && v in 0.0..1439.0 }?.toInt()) };Days(t.weekdays) { t=t.copy(weekdays=it) } }
        t.condition?.let { c -> ConditionEditor(c) { t=t.copy(condition=it) } }
        Text("함께 만족해야 하는 조건")
        t.filters.forEachIndexed { index,c -> Card { Column(Modifier.padding(12.dp)) { ConditionEditor(c) { changed -> t=t.copy(filters=t.filters.mapIndexed { i,old -> if(i==index)changed else old }) };TextButton(onClick={t=t.copy(filters=t.filters.filterIndexed { i,_ -> i!=index })}) { Text("조건 삭제") } } } }
        OutlinedButton(onClick={t=t.copy(filters=t.filters+ConditionSpec(ConditionField.TIME_WINDOW,startMinute=420,endMinute=540))}) { Text("+ 추가 조건") }
        Text("시간대: ${t.zoneId}\n앱 재시작 시 이미 참인 조건은 새 이벤트가 아닙니다.")
    }},confirmButton={Button(onClick={onApply(t)}) { Text("시작 조건 적용") }},dismissButton={TextButton(onClick=onClose) { Text("취소") }})
}
@Composable fun EdgeEditor(w:WorkflowDefinition,onClose:()->Unit,onApply:(List<WorkflowEdge>)->Unit) {
    var edges by remember(w) { mutableStateOf(w.edges) }
    var source by remember { mutableStateOf(w.nodes.first().id) };var target by remember { mutableStateOf(w.nodes.last().id) };var port by remember { mutableStateOf("next") }
    AlertDialog(onDismissRequest=onClose,title={Text("카드 연결 편집")},text={Column(Modifier.heightIn(max=600.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("IF는 true/false, 반복은 body/next, 병렬은 branch:0 등으로 연결합니다. 저장 전 전체 경로를 검증합니다.")
        edges.forEach { e -> Row { Text("${w.nodes.find { it.id==e.source }?.label} [${e.port}] → ${w.nodes.find { it.id==e.target }?.label}",Modifier.weight(1f));TextButton(onClick={edges=edges.filterNot { it.id==e.id }}) { Text("삭제") } } }
        Choice("출발 카드",source,w.nodes.map { it.id },name={id->w.nodes.find { it.id==id }!!.label}) { source=it }
        Choice("출구",port,listOf("next","true","false","body","branch:0","branch:1","branch:2","branch:3")) { port=it }
        Choice("도착 카드",target,w.nodes.map { it.id },name={id->w.nodes.find { it.id==id }!!.label}) { target=it }
        OutlinedButton(onClick={edges=edges+WorkflowEdge("e"+UUID.randomUUID().toString().take(8),source,target,port)}) { Text("연결 추가") }
    }},confirmButton={Button(onClick={onApply(edges)}) { Text("연결 적용") }},dismissButton={TextButton(onClick=onClose) { Text("취소") }})
}
