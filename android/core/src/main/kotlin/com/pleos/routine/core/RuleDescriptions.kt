package com.pleos.routine.core

/** Human review is derived from the same typed rules that the engine executes. */
object RuleDescriptions {
    private fun number(value:Number?)=value?.toString()?.removeSuffix(".0") ?: "미설정"
    private fun time(minute:Int?)=minute?.let { "%02d:%02d".format(it/60,it%60) } ?: "미설정"
    private fun days(values:List<Int>)=values.sorted().joinToString("·") { listOf("월","화","수","목","금","토","일").getOrNull(it-1) ?: "?" }.ifBlank { "요일 미설정" }
    private fun compare(op:Comparison)=when(op) { Comparison.EQ->"=";Comparison.NE->"≠";Comparison.LT->"<";Comparison.LTE->"≤";Comparison.GT->">";Comparison.GTE->"≥" }
    fun condition(c:ConditionSpec):String=when(c.field) {
        ConditionField.OUTSIDE_TEMPERATURE->"외부 온도 ${compare(c.operator)} ${number(c.value)}°C"
        ConditionField.SPEED->"실제 속도 ${compare(c.operator)} ${number(c.value)} m/s"
        ConditionField.GEAR->"기어 ${compare(c.operator)} ${c.text ?: "미설정"}"
        ConditionField.SEAT_OCCUPIED->"좌석 점유 ${compare(c.operator)} ${when(c.flag) { true->"탑승";false->"비어 있음";null->"미설정" }} (신원 확인 아님)"
        ConditionField.TIME_WINDOW->"${time(c.startMinute)} 이상 · ${time(c.endMinute)} 미만"
        ConditionField.WEEKDAY->days(c.weekdays)
    }
    fun trigger(t:TriggerDefinition):String {
        val start=when(t.type) {
            TriggerType.MANUAL->"직접 실행"
            TriggerType.VOICE->"음성 요청 후 확인"
            TriggerType.SEAT_OCCUPIED->"좌석이 비었다가 탑승할 때"
            TriggerType.TIME->"${days(t.weekdays)} ${time(t.minuteOfDay)}에 새로 진입할 때"
            TriggerType.CONDITION_ENTER->"${t.condition?.let(::condition) ?: "조건 미설정"}에 새로 진입할 때"
        }
        return "$start · ${t.zoneId}"+if(t.filters.isEmpty())"" else "\n추가 조건 모두 충족: "+t.filters.joinToString(" / ",transform=::condition)
    }
    fun action(a:ActionSpec):String=when(a.type) {
        ActionType.NAVIGATE->"${a.text ?: "목적지 미설정"}로 길 안내 · ${number(a.latitude)}, ${number(a.longitude)}"
        ActionType.CANCEL_NAVIGATION->"길 안내 취소"
        ActionType.SET_HVAC_TARGET->"${a.area} · 공조 목표 ${number(a.value)}°C"
        ActionType.SET_AMBIENT_BRIGHTNESS->"${a.area} · 앰비언트 밝기 ${number(a.value)}%"
        ActionType.SET_AMBIENT_COLOR->"${a.area} · 앰비언트 색상 ${a.text ?: "미설정"}"
        ActionType.SET_SEAT_HEAT->"${a.area} · 열선 ${number(a.value)}단계"
        ActionType.MOVE_SEAT_LIMITED->"${a.area} · ${when(a.axis) { SeatAxis.FORE_AFT->"시트 앞뒤";SeatAxis.BACKREST->"등받이";null->"축 미설정" }} · ${when(a.direction) { MoveDirection.FORWARD->"앞으로";MoveDirection.BACKWARD->"뒤로";null->"방향 미설정" }} · 전체 실행 전 1회 확인"
        ActionType.PLAY_LOCAL_AUDIO->"자체 휴식 음원 재생"
        ActionType.STOP_LOCAL_AUDIO->"자체 음원 중지"
        ActionType.OPEN_APP->"앱 열기 · ${a.targetId ?: "대상 미설정"}"
        ActionType.MEDIA_CONTROL->"미디어 제어 · ${a.targetId ?: "대상 미설정"} · 지원 검증 필요"
        ActionType.CALENDAR_READ->"앞으로 24시간 일정 읽기"
        ActionType.HA_CALL->"스마트홈 ${a.targetId ?: "대상 미설정"} · ${a.text ?: "동작 미설정"}"+(a.value?.let { " · 목표 ${number(it)} (기기 단위)" } ?: "")
        ActionType.PLACE_SEARCH->"장소 검색 · ${a.text ?: "미설정"}"
        ActionType.WHAT3WORDS_RESOLVE->"세 단어 주소 조회 · ${a.text ?: "미설정"}"
    }
    fun node(n:WorkflowNode):String=when(n.kind) {
        NodeKind.START->"시작 조건 확인"
        NodeKind.ACTION->n.action?.let(::action) ?: "동작 미설정"
        NodeKind.IF->n.condition?.let(::condition) ?: "조건 미설정"
        NodeKind.WAIT->"${number(n.durationSeconds)}초 대기"
        NodeKind.TIMER->"${number(n.durationSeconds)}초 타이머"
        NodeKind.REPEAT->"${number(n.repeatCount)}회 반복 · 합류 ${n.joinId ?: "미설정"}"
        NodeKind.PARALLEL->"분기들을 동시에 실행 · 합류 ${n.joinId ?: "미설정"}"
        NodeKind.JOIN->"분기 합류"
        NodeKind.END->"실행 종료 · 앱 소유 음원 정리"
    }
    fun changes(before:WorkflowDefinition,after:WorkflowDefinition):List<String> = buildList {
        if(before.title!=after.title)add("이름: ${before.title} → ${after.title}")
        if(before.trigger!=after.trigger)add("시작 조건 변경\n${trigger(before.trigger)}\n→ ${trigger(after.trigger)}")
        after.nodes.forEach { next ->
            val previous=before.nodes.find { it.id==next.id }
            if(previous==null)add("노드 추가: ${next.label}\n${node(next)}")
            else if(previous.copy(position=next.position)!=next)add("노드 변경: ${previous.label} → ${next.label}\n${node(previous)}\n→ ${node(next)}")
        }
        before.nodes.filter { previous -> after.nodes.none { it.id==previous.id } }.forEach { add("노드 삭제: ${it.label}\n${node(it)}") }
        fun edgeKey(e:WorkflowEdge)=Triple(e.source,e.port,e.target)
        fun edgeLabel(w:WorkflowDefinition,e:WorkflowEdge):String {
            fun name(id:String)=w.nodes.find { it.id==id }?.label ?: id
            val port=when(e.port) { "true"->"조건 충족";"false"->"조건 미충족";"body"->"반복 본문";"next"->"다음";else->e.port }
            return "${name(e.source)} [$port] → ${name(e.target)}"
        }
        before.edges.filter { e -> after.edges.none { edgeKey(it)==edgeKey(e) } }.forEach { add("연결 삭제: ${edgeLabel(before,it)}") }
        after.edges.filter { e -> before.edges.none { edgeKey(it)==edgeKey(e) } }.forEach { add("연결 추가: ${edgeLabel(after,it)}") }
        if(isEmpty())add("실행 규칙 변경 없음 · 카드 배치나 버전만 달라졌습니다.")
    }
}
