package com.pleos.routine.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

@Serializable
data class WorkflowDefinition(
    val schemaVersion: Int = 2,
    val id: String,
    val title: String,
    val revision: Int = 1,
    val trigger: TriggerDefinition = TriggerDefinition(),
    val nodes: List<WorkflowNode>,
    val edges: List<WorkflowEdge>,
)

@Serializable enum class NodeKind { START, ACTION, IF, WAIT, TIMER, REPEAT, PARALLEL, JOIN, END }
@Serializable data class NodePosition(val x: Float = 0f, val y: Float = 0f)
@Serializable data class WorkflowNode(
    val id: String,
    val kind: NodeKind,
    val label: String,
    val position: NodePosition = NodePosition(),
    val action: ActionSpec? = null,
    val condition: ConditionSpec? = null,
    val durationSeconds: Long? = null,
    val repeatCount: Int? = null,
    val joinId: String? = null,
)
@Serializable data class WorkflowEdge(val id: String, val source: String, val target: String, val port: String = "next")

@Serializable enum class ActionType {
    NAVIGATE, CANCEL_NAVIGATION, SET_HVAC_TARGET, SET_AMBIENT_BRIGHTNESS,
    SET_AMBIENT_COLOR, SET_SEAT_HEAT, MOVE_SEAT_LIMITED, PLAY_LOCAL_AUDIO,
    STOP_LOCAL_AUDIO, OPEN_APP, MEDIA_CONTROL, CALENDAR_READ, HA_CALL,
    PLACE_SEARCH, WHAT3WORDS_RESOLVE,
}
@Serializable enum class SeatAxis { FORE_AFT, BACKREST }
@Serializable enum class MoveDirection { FORWARD, BACKWARD }

/** Parameters describe intent. Physical movement duration and approval are never model input. */
@Serializable data class ActionSpec(
    val type: ActionType,
    val area: String = "DRIVER",
    val value: Double? = null,
    val text: String? = null,
    val targetId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val axis: SeatAxis? = null,
    val direction: MoveDirection? = null,
)

@Serializable enum class ConditionField { OUTSIDE_TEMPERATURE, GEAR, SPEED, SEAT_OCCUPIED, TIME_WINDOW, WEEKDAY }
@Serializable enum class Comparison { EQ, NE, LT, LTE, GT, GTE }
@Serializable data class ConditionSpec(
    val field: ConditionField,
    val operator: Comparison = Comparison.LTE,
    val value: Double? = null,
    val text: String? = null,
    val flag: Boolean? = null,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val weekdays: List<Int> = emptyList(),
)

@Serializable enum class TriggerType { MANUAL, VOICE, SEAT_OCCUPIED, TIME, CONDITION_ENTER }
@Serializable data class TriggerDefinition(
    val type: TriggerType = TriggerType.MANUAL,
    val condition: ConditionSpec? = null,
    val filters: List<ConditionSpec> = emptyList(),
    val minuteOfDay: Int? = null,
    val weekdays: List<Int> = emptyList(),
    val zoneId: String = "Asia/Seoul",
)

@Serializable enum class PatchType { REPLACE_NODE, ADD_NODE, REMOVE_NODE, CONNECT, DISCONNECT, CHANGE_TITLE, CHANGE_TRIGGER }
@Serializable data class PatchOperation(
    val type: PatchType,
    val node: WorkflowNode? = null,
    val nodeId: String? = null,
    val edge: WorkflowEdge? = null,
    val edgeId: String? = null,
    val title: String? = null,
    val trigger: TriggerDefinition? = null,
)
@Serializable data class WorkflowPatch(val baseRevision: Int, val operations: List<PatchOperation>)

object WorkflowJson {
    val format = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }
    fun encode(workflow: WorkflowDefinition): String = format.encodeToString(workflow)
    fun decode(json: String): WorkflowDefinition = format.decodeFromString(json)

    /** Labels and geometry do not invalidate a simulation; execution semantics do. */
    fun semanticHash(workflow: WorkflowDefinition): String {
        val root = format.encodeToJsonElement(WorkflowDefinition.serializer(), workflow).jsonObject
        val content = JsonObject(root.filterKeys { it !in setOf("id", "title", "revision") }.toMutableMap().apply {
            put("nodes", JsonArray(workflow.nodes.sortedBy { it.id }.map { node ->
                JsonObject(format.encodeToJsonElement(WorkflowNode.serializer(), node).jsonObject.filterKeys { it !in setOf("label", "position") })
            }))
            put("edges", JsonArray(workflow.edges.sortedWith(compareBy({ it.source }, { it.port }, { it.target })).map { edge ->
                JsonObject(format.encodeToJsonElement(WorkflowEdge.serializer(), edge).jsonObject.filterKeys { it != "id" })
            }))
        })
        fun canonical(value: JsonElement): JsonElement = when (value) {
            is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
            is JsonArray -> JsonArray(value.map(::canonical))
            else -> value
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical(content).toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

object WorkflowEdits {
    fun applyScopedPatch(workflow: WorkflowDefinition, patch: WorkflowPatch, selectedNodeId: String): WorkflowDefinition {
        val selected = requireNotNull(workflow.nodes.singleOrNull { it.id == selectedNodeId }) { "선택한 노드가 없습니다." }
        val candidate = applyPatch(workflow, patch)
        validateScopedOperations(workflow, selected, patch)
        return candidate
    }

    fun applyPatch(workflow: WorkflowDefinition, patch: WorkflowPatch): WorkflowDefinition {
        require(patch.baseRevision == workflow.revision) { "수정 기준 버전이 다릅니다. 최신 루틴을 다시 확인하세요." }
        require(patch.operations.isNotEmpty() && patch.operations.size <= 128) { "변경 항목 수가 올바르지 않습니다." }
        var next = workflow
        patch.operations.forEach { op ->
            next = when (op.type) {
                PatchType.REPLACE_NODE -> {
                    val node = requireNotNull(op.node)
                    require(op.nodeId == node.id && next.nodes.any { it.id == node.id }) { "교체할 노드 ID가 일치하지 않습니다." }
                    next.copy(nodes = next.nodes.map { if (it.id == node.id) node else it })
                }
                PatchType.ADD_NODE -> {
                    val node = requireNotNull(op.node)
                    require(next.nodes.none { it.id == node.id }) { "노드 ID가 중복됩니다." }
                    next.copy(nodes = next.nodes + node)
                }
                PatchType.REMOVE_NODE -> {
                    val id = requireNotNull(op.nodeId)
                    require(next.nodes.any { it.id == id }) { "삭제할 노드가 없습니다." }
                    next.copy(nodes = next.nodes.filterNot { it.id == id }, edges = next.edges.filterNot { it.source == id || it.target == id })
                }
                PatchType.CONNECT -> {
                    val edge = requireNotNull(op.edge)
                    require(next.edges.none { it.id == edge.id }) { "연결 ID가 중복됩니다." }
                    next.copy(edges = next.edges + edge)
                }
                PatchType.DISCONNECT -> {
                    val id = requireNotNull(op.edgeId)
                    require(next.edges.any { it.id == id }) { "삭제할 연결이 없습니다." }
                    next.copy(edges = next.edges.filterNot { it.id == id })
                }
                PatchType.CHANGE_TITLE -> next.copy(title = requireNotNull(op.title))
                PatchType.CHANGE_TRIGGER -> next.copy(trigger = requireNotNull(op.trigger))
            }
        }
        val issues = WorkflowValidator.validate(next)
        require(issues.isEmpty()) { issues.joinToString { it.message } }
        return next.copy(revision = workflow.revision + 1)
    }

    fun clone(workflow: WorkflowDefinition, id: String): WorkflowDefinition = workflow.copy(id = id, title = "${workflow.title} 복사", revision = 1)
}

/** A local working copy. Persist/activate separately; undo never changes an executing run. */
class WorkflowHistory(initial: WorkflowDefinition) {
    var current: WorkflowDefinition = initial
        private set
    private val past = ArrayDeque<WorkflowDefinition>()
    private val future = ArrayDeque<WorkflowDefinition>()
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    fun replace(workflow: WorkflowDefinition) { past.addLast(current); future.clear(); current = workflow }
    fun undo(): WorkflowDefinition { if (canUndo) { future.addLast(current); current = past.removeLast() }; return current }
    fun redo(): WorkflowDefinition { if (canRedo) { past.addLast(current); current = future.removeLast() }; return current }
}
