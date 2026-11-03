package com.pleos.routine.core

data class ValidationIssue(val nodeId: String?, val message: String)

object WorkflowValidator {
    fun validate(w: WorkflowDefinition): List<ValidationIssue> = buildList {
        fun issue(id: String?, message: String) { add(ValidationIssue(id, message)) }
        if (w.schemaVersion != 2) issue(null, "지원하지 않는 루틴 형식입니다.")
        if (w.id.isBlank() || w.title.isBlank() || w.title.length > 100 || w.revision < 1) issue(null, "루틴 이름과 버전을 확인하세요.")
        if (w.nodes.size !in 2..64 || w.edges.size > 192) issue(null, "루틴은 2~64개 노드로 구성하세요.")
        if (w.nodes.map { it.id }.distinct().size != w.nodes.size || w.edges.map { it.id }.distinct().size != w.edges.size) issue(null, "노드 또는 연결 ID가 중복됩니다.")
        val map = w.nodes.associateBy { it.id }
        val outgoing = w.edges.groupBy { it.source }
        if (w.nodes.count { it.kind == NodeKind.START } != 1) issue(null, "시작 노드는 하나여야 합니다.")
        if (w.nodes.none { it.kind == NodeKind.END }) issue(null, "종료 노드가 필요합니다.")
        w.edges.forEach { if (it.source !in map || it.target !in map) issue(it.source, "연결할 노드가 없습니다.") }
        val owners = w.nodes.filter { it.kind in setOf(NodeKind.REPEAT, NodeKind.PARALLEL) }
        if (owners.mapNotNull { it.joinId }.distinct().size != owners.size) issue(null, "각 반복·병렬 블록에는 독립적인 합류 노드가 필요합니다.")
        fun checkCondition(c: ConditionSpec?, id: String?) {
            if (c == null) { issue(id, "조건을 설정하세요."); return }
            when (c.field) {
                ConditionField.OUTSIDE_TEMPERATURE, ConditionField.SPEED -> if (c.value?.isFinite() != true) issue(id, "유효한 비교값을 입력하세요.")
                ConditionField.GEAR -> if (c.text !in setOf("P", "R", "N", "D") || c.operator !in setOf(Comparison.EQ, Comparison.NE)) issue(id, "기어 조건을 확인하세요.")
                ConditionField.SEAT_OCCUPIED -> if (c.flag == null || c.operator !in setOf(Comparison.EQ, Comparison.NE)) issue(id, "좌석 점유 조건을 확인하세요.")
                ConditionField.TIME_WINDOW -> if (c.startMinute !in 0..1439 || c.endMinute !in 1..1440 || (c.startMinute ?: 0) >= (c.endMinute ?: 0)) issue(id, "시간 구간은 같은 날의 시작 이전·종료 이후가 아닌 [시작, 종료)로 설정하세요.")
                ConditionField.WEEKDAY -> if (c.weekdays.isEmpty() || c.weekdays.any { it !in 1..7 }) issue(id, "요일을 선택하세요.")
            }
        }
        try { java.time.ZoneId.of(w.trigger.zoneId) } catch (_: Exception) { issue(null, "시간대가 올바르지 않습니다.") }
        w.trigger.filters.forEach { checkCondition(it, null) }
        if (w.trigger.type == TriggerType.CONDITION_ENTER) checkCondition(w.trigger.condition, null)
        if (w.trigger.type == TriggerType.TIME && (w.trigger.minuteOfDay !in 0..1439 || w.trigger.weekdays.isEmpty() || w.trigger.weekdays.any { it !in 1..7 })) issue(null, "자동 실행 시간과 요일을 설정하세요.")
        w.nodes.forEach { n ->
            if (!n.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) || !n.position.x.isFinite() || !n.position.y.isFinite()) issue(n.id, "노드 ID 또는 위치가 잘못되었습니다.")
            val edges = outgoing[n.id].orEmpty()
            val ports = edges.map { it.port }
            when (n.kind) {
                NodeKind.END -> if (edges.isNotEmpty()) issue(n.id, "종료 노드에는 다음 연결이 없습니다.")
                NodeKind.IF -> { checkCondition(n.condition, n.id); if (ports.sorted() != listOf("false", "true")) issue(n.id, "조건의 예·아니오 경로를 모두 연결하세요.") }
                NodeKind.REPEAT -> {
                    if (n.repeatCount !in 1..20 || ports.sorted() != listOf("body", "next")) issue(n.id, "반복은 1~20회이며 본문과 다음 경로가 필요합니다.")
                    if (map[n.joinId]?.kind != NodeKind.JOIN) issue(n.id, "반복 종료 노드를 지정하세요.")
                }
                NodeKind.PARALLEL -> {
                    if (ports.size !in 2..4 || ports.distinct().size != ports.size || ports.any { !it.startsWith("branch:") }) issue(n.id, "병렬은 서로 다른 2~4개 분기로 연결하세요.")
                    if (map[n.joinId]?.kind != NodeKind.JOIN) issue(n.id, "병렬 합류 노드를 지정하세요.")
                }
                NodeKind.JOIN -> {
                    val owner = owners.singleOrNull { it.joinId == n.id }
                    if (owner == null) issue(n.id, "소속 블록이 없는 합류 노드입니다.")
                    if (owner?.kind == NodeKind.REPEAT && edges.isNotEmpty()) issue(n.id, "반복 종료의 다음 경로는 반복 노드에서 연결하세요.")
                    if (owner?.kind == NodeKind.PARALLEL && ports != listOf("next")) issue(n.id, "병렬 합류 뒤의 경로를 연결하세요.")
                }
                else -> if (ports != listOf("next")) issue(n.id, "다음 경로 하나를 연결하세요.")
            }
            if (n.kind == NodeKind.ACTION) {
                val a = n.action
                if (a == null) issue(n.id, "실행할 동작을 선택하세요.") else actionErrors(a).forEach { issue(n.id, it) }
            }
            if (n.kind in setOf(NodeKind.WAIT, NodeKind.TIMER) && n.durationSeconds !in 1L..7200L) issue(n.id, "대기 시간은 1~7200초로 설정하세요.")
        }
        val visited = mutableSetOf<String>(); val stack = mutableSetOf<String>()
        fun visit(id: String) {
            if (id in stack) { issue(id, "순환 연결은 반복 노드로 표현하세요."); return }
            if (!visited.add(id)) return
            stack.add(id); outgoing[id].orEmpty().forEach { visit(it.target) }; stack.remove(id)
        }
        w.nodes.firstOrNull { it.kind == NodeKind.START }?.let { visit(it.id) }
        w.nodes.filter { it.id !in visited }.forEach { issue(it.id, "시작과 연결되지 않은 노드입니다.") }
        if (any { it.message.contains("순환") }) return@buildList
        // Verify every branch ends at its own JOIN and branches have no cross-entry.
        fun branchNodes(id: String, stop: String, seen: MutableSet<String>): Boolean {
            if (id == stop) return true
            if (!seen.add(id)) return true
            val node = map[id] ?: return false
            if (node.kind == NodeKind.END) return false
            val next = outgoing[id].orEmpty()
            if (node.kind == NodeKind.REPEAT) {
                val body = next.find { it.port == "body" } ?: return false
                val tail = next.find { it.port == "next" } ?: return false
                val inner = mutableSetOf<String>()
                if (!branchNodes(body.target, node.joinId ?: return false, inner)) return false
                seen.addAll(inner); seen.add(node.joinId)
                return branchNodes(tail.target, stop, seen)
            }
            return next.isNotEmpty() && next.all { branchNodes(it.target, stop, seen) }
        }
        owners.forEach { owner ->
            val stop = owner.joinId ?: return@forEach
            val entries = outgoing[owner.id].orEmpty().filter { it.port != "next" }
            val branches = entries.map { e ->
                val members = mutableSetOf<String>()
                if (!branchNodes(e.target, stop, members)) issue(owner.id, "블록의 모든 경로가 지정한 합류 노드로 끝나야 합니다.")
                members
            }
            branches.forEachIndexed { index, members ->
                // Only this branch's declared entry may enter its body. In particular,
                // REPEAT.next must not re-enter the body after the requested repetitions.
                w.edges.filter { it.target in members && it.source !in members && it != entries[index] }.forEach { issue(it.target, "블록 외부에서 내부 분기로 연결할 수 없습니다.") }
            }
            val members = branches.flatten().toSet()
            w.edges.filter { it.target == stop && it.source !in members && it !in entries }
                .forEach { issue(stop, "블록 외부에서 합류 노드로 진입할 수 없습니다.") }
            if (owner.kind == NodeKind.PARALLEL) for (i in branches.indices) for (j in 0 until i) {
                if (branches[i].intersect(branches[j]).isNotEmpty()) issue(owner.id, "병렬 분기는 지정한 합류 전까지 독립적이어야 합니다.")
                fun resources(ids: Set<String>) = ids.mapNotNull { map[it]?.action }.filter(ActionResources::isWrite).map(ActionResources::key).toSet()
                if (resources(branches[i]).intersect(resources(branches[j])).isNotEmpty()) issue(owner.id, "같은 장치를 병렬로 변경할 수 없습니다.")
            }
        }
        if (isEmpty() && ExecutionLimits.worstCaseVisits(w) > ExecutionLimits.MAX_NODE_VISITS)
            issue(null, "반복·병렬·조건의 최대 실행 경로가 4,096회 노드 방문을 초과할 수 있습니다. 반복 횟수나 동작을 줄이세요.")
    }

    fun actionErrors(a: ActionSpec): List<String> = buildList {
        if (a.area.isBlank() || a.area.length > 160) add("영역을 선택하세요.")
        if (a.value != null && !a.value.isFinite()) add("유효한 값을 입력하세요.")
        when (a.type) {
            ActionType.NAVIGATE -> if (a.latitude == null || a.longitude == null || a.latitude !in -90.0..90.0 || a.longitude !in -180.0..180.0 || a.text.isNullOrBlank()) add("목적지 이름과 유효한 좌표를 등록하세요.")
            ActionType.SET_HVAC_TARGET -> if (a.value == null) add("목표 온도를 입력하세요.")
            ActionType.SET_AMBIENT_BRIGHTNESS -> if (a.value == null || a.value !in 0.0..100.0) add("밝기는 0~100%로 입력하세요.")
            ActionType.SET_AMBIENT_COLOR -> if (a.text?.matches(Regex("#FF[0-9A-Fa-f]{6}")) != true) add("색상은 불투명 ARGB(#FFRRGGBB)로 입력하세요.")
            ActionType.SET_SEAT_HEAT -> if (a.value == null || a.value < 0 || a.value != a.value.toInt().toDouble()) add("열선 단계를 입력하세요.")
            ActionType.MOVE_SEAT_LIMITED -> if (a.axis == null || a.direction == null) add("시트 축과 이동 방향을 선택하세요.")
            ActionType.PLAY_LOCAL_AUDIO -> if (a.targetId != "rest-tone") add("앱에 포함된 음원을 선택하세요.")
            ActionType.OPEN_APP, ActionType.MEDIA_CONTROL, ActionType.HA_CALL -> if (a.targetId.isNullOrBlank()) add("연결할 대상을 선택하세요.")
            ActionType.PLACE_SEARCH, ActionType.WHAT3WORDS_RESOLVE -> if (a.text.isNullOrBlank()) add("검색할 장소를 입력하세요.")
            else -> Unit
        }
    }
}
