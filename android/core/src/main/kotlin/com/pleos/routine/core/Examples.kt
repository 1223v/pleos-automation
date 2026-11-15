package com.pleos.routine.core

object WorkflowExamples {
    fun commute(): WorkflowDefinition = WorkflowDefinition(
        id = "commute", title = "평일 아침 출근", trigger = TriggerDefinition(TriggerType.SEAT_OCCUPIED,
            filters = listOf(ConditionSpec(ConditionField.WEEKDAY, weekdays = listOf(1,2,3,4,5)), ConditionSpec(ConditionField.TIME_WINDOW, startMinute = 420, endMinute = 540))),
        nodes = listOf(
            WorkflowNode("start", NodeKind.START, "운전석 탑승", NodePosition(40f, 160f)),
            WorkflowNode("navigation", NodeKind.ACTION, "회사로 길 안내", NodePosition(300f, 160f), action = ActionSpec(ActionType.NAVIGATE, text = "회사 주소를 등록하세요")),
            WorkflowNode("cold", NodeKind.IF, "외기 온도 ≤ 5°C", NodePosition(560f, 160f), condition = ConditionSpec(ConditionField.OUTSIDE_TEMPERATURE, value = 5.0)),
            WorkflowNode("heat", NodeKind.ACTION, "운전석 열선 2단계", NodePosition(820f, 60f), action = ActionSpec(ActionType.SET_SEAT_HEAT, area = "ROW_1_LEFT", value = 2.0)),
            WorkflowNode("end", NodeKind.END, "완료", NodePosition(1080f, 160f))),
        edges = listOf(WorkflowEdge("e1","start","navigation"), WorkflowEdge("e2","navigation","cold"), WorkflowEdge("e3","cold","heat","true"), WorkflowEdge("e4","cold","end","false"), WorkflowEdge("e5","heat","end")))

    /** The application's winter camping scenario: only the HVAC target depends on the outside temperature. */
    fun winterCamping(): WorkflowDefinition {
        val cabin = "ROW_1_LEFT+ROW_1_RIGHT+ROW_2_LEFT+ROW_2_CENTER+ROW_2_RIGHT"
        return WorkflowDefinition(id = "winter-camping", title = "겨울 차박",
            nodes = listOf(
                WorkflowNode("start", NodeKind.START, "주차 후 시작", NodePosition(40f, 155f)),
                WorkflowNode("cold", NodeKind.IF, "외기 5°C 이하", NodePosition(280f, 155f),
                    condition = ConditionSpec(ConditionField.OUTSIDE_TEMPERATURE, Comparison.LTE, value = 5.0)),
                WorkflowNode("hvac23", NodeKind.ACTION, "공조 목표 23°C", NodePosition(520f, 30f),
                    action = ActionSpec(ActionType.SET_HVAC_TARGET, area = "ROW_1_LEFT", value = 23.0)),
                WorkflowNode("seat", NodeKind.ACTION, "선택한 운전석 등받이", NodePosition(760f, 155f),
                    action = ActionSpec(ActionType.MOVE_SEAT_LIMITED, area = "ROW_1_LEFT", axis = SeatAxis.BACKREST, direction = MoveDirection.BACKWARD)),
                WorkflowNode("light30", NodeKind.ACTION, "조명 30%", NodePosition(1000f, 155f),
                    action = ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS, area = cabin, value = 30.0)),
                WorkflowNode("end", NodeKind.END, "완료", NodePosition(1240f, 155f))),
            edges = listOf(WorkflowEdge("start-cold", "start", "cold"), WorkflowEdge("cold-hvac23", "cold", "hvac23", "true"),
                WorkflowEdge("cold-seat", "cold", "seat", "false"), WorkflowEdge("hvac23-seat", "hvac23", "seat"),
                WorkflowEdge("seat-light30", "seat", "light30"), WorkflowEdge("light30-end", "light30", "end")))
    }

    fun rest(): WorkflowDefinition {
        // Areas are the SDK SeatArea names Connect v2.1.0 reported; the "DRIVER" default is never executable.
        val cabin = "ROW_1_LEFT+ROW_1_RIGHT+ROW_2_LEFT+ROW_2_CENTER+ROW_2_RIGHT"
        val actions = listOf(
            "공조 목표 23°C" to ActionSpec(ActionType.SET_HVAC_TARGET, area = "ROW_1_LEFT", value = 23.0),
            "앰비언트 밝기 30%" to ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS, area = cabin, value = 30.0),
            "앰비언트 파랑" to ActionSpec(ActionType.SET_AMBIENT_COLOR, area = cabin, text = "#FF4B85DB"),
            "시트 앞뒤 · 뒤로" to ActionSpec(ActionType.MOVE_SEAT_LIMITED, area = "ROW_1_LEFT", axis = SeatAxis.FORE_AFT, direction = MoveDirection.BACKWARD),
            "등받이 · 뒤로" to ActionSpec(ActionType.MOVE_SEAT_LIMITED, area = "ROW_1_LEFT", axis = SeatAxis.BACKREST, direction = MoveDirection.BACKWARD),
            "휴식 음원" to ActionSpec(ActionType.PLAY_LOCAL_AUDIO, targetId = "rest-tone"))
        val nodes = listOf(WorkflowNode("start",NodeKind.START,"지금 시작",NodePosition(40f,150f))) + actions.mapIndexed { index, (label, action) -> WorkflowNode("action$index",NodeKind.ACTION,label,NodePosition(300f+260*index,150f),action=action) } + listOf(
            WorkflowNode("timer",NodeKind.TIMER,"30분 휴식",NodePosition(1860f,150f),durationSeconds=1800), WorkflowNode("end",NodeKind.END,"완료",NodePosition(2120f,150f)))
        return WorkflowDefinition(id="rest",title="잠깐 쉬어가기",nodes=nodes,edges=nodes.zipWithNext { a,b -> WorkflowEdge("${a.id}-${b.id}",a.id,b.id) })
    }
    fun blank(id: String) = WorkflowDefinition(id=id,title="새 루틴",nodes=listOf(WorkflowNode("start",NodeKind.START,"시작",NodePosition(60f,160f)),WorkflowNode("end",NodeKind.END,"완료",NodePosition(620f,160f))),edges=listOf(WorkflowEdge("start-end","start","end")))
}
