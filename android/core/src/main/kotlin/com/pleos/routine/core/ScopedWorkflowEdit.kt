package com.pleos.routine.core

/** Only the selected node's settings and a linear insertion before its original successor may change. */
internal fun validateScopedOperations(workflow: WorkflowDefinition, selected: WorkflowNode, patch: WorkflowPatch) {
    val scopeError = "선택한 노드의 수정 범위를 벗어납니다."
    val outgoing = workflow.edges.filter { it.source == selected.id }.singleOrNull()?.takeIf { it.port == "next" }
    val originalEdges = workflow.edges.map { it.id }.toSet()
    val addedIds = mutableSetOf<String>()
    val newEdges = mutableListOf<WorkflowEdge>()
    var disconnected = false
    for (op in patch.operations) {
        when (op.type) {
            PatchType.REPLACE_NODE -> {
                val node = requireNotNull(op.node)
                require(op.nodeId == selected.id && selected.permitsReplacement(node)) { scopeError }
            }
            PatchType.ADD_NODE -> {
                val node = requireNotNull(op.node)
                require(workflow.nodes.none { it.id == node.id } && addedIds.add(node.id)) { scopeError }
                val allowed = when (node.kind) {
                    NodeKind.WAIT -> node == WorkflowNode(node.id, NodeKind.WAIT, node.label, node.position, durationSeconds = node.durationSeconds)
                    NodeKind.ACTION -> selected.kind == NodeKind.ACTION && selected.action?.type in editableSetters &&
                        WorkflowNode(node.id, NodeKind.ACTION, node.label, node.position, action = selected.action)
                            .permitsReplacement(node)
                    else -> false
                }
                require(allowed) { scopeError }
            }
            PatchType.DISCONNECT -> {
                require(outgoing != null && op.edgeId == outgoing.id && !disconnected) { scopeError }
                disconnected = true
            }
            PatchType.CONNECT -> {
                val edge = requireNotNull(op.edge)
                require(edge.port == "next" && (edge.id !in originalEdges || disconnected && edge.id == outgoing?.id)) { scopeError }
                newEdges += edge
            }
            else -> throw IllegalArgumentException(scopeError)
        }
    }
    if (addedIds.isEmpty()) {
        require(!disconnected && newEdges.isEmpty()) { scopeError }
        return
    }
    require(outgoing != null && disconnected && newEdges.size == addedIds.size + 1) { scopeError }
    val edgesBySource = newEdges.associateBy { it.source }
    require(edgesBySource.size == newEdges.size) { scopeError }
    val remaining = addedIds.toMutableSet()
    var at = selected.id
    while (remaining.isNotEmpty()) {
        at = requireNotNull(edgesBySource[at]) { scopeError }.target
        require(remaining.remove(at)) { scopeError }
    }
    require(edgesBySource[at]?.target == outgoing.target) { scopeError }
}

private val editableSetters = setOf(ActionType.SET_HVAC_TARGET, ActionType.SET_AMBIENT_BRIGHTNESS,
    ActionType.SET_AMBIENT_COLOR, ActionType.SET_SEAT_HEAT)

private fun WorkflowNode.permitsReplacement(next: WorkflowNode): Boolean {
    val renamed = copy(label = next.label)
    val allowed = when (kind) {
        NodeKind.IF -> renamed.copy(condition = next.condition)
        NodeKind.WAIT, NodeKind.TIMER -> renamed.copy(durationSeconds = next.durationSeconds)
        NodeKind.REPEAT -> renamed.copy(repeatCount = next.repeatCount)
        NodeKind.ACTION -> when (action?.type) {
            ActionType.SET_AMBIENT_COLOR -> renamed.copy(action = action.copy(text = next.action?.text))
            ActionType.SET_AMBIENT_BRIGHTNESS, ActionType.SET_HVAC_TARGET, ActionType.SET_SEAT_HEAT ->
                renamed.copy(action = action.copy(value = next.action?.value))
            else -> renamed
        }
        else -> renamed
    }
    return next == allowed
}
