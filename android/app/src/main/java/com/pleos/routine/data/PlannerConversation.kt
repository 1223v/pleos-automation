package com.pleos.routine.data

import com.pleos.routine.core.*
import com.pleos.routine.vehicle.Capability
import com.pleos.routine.vehicle.editableVehicleAreas
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class AnsweredQuestions(val requestText: String, val questions: List<String>, val answers: List<String>) {
    init {
        require(requestText.isNotBlank() && requestText.length <= 4000)
        require(questions.size in 1..5 && questions.size == answers.size)
        require((questions + answers).all { it.isNotBlank() && it.length <= 1000 })
    }
}

@Serializable
data class ClarificationDraft(
    val requestText: String,
    val baseFingerprint: String,
    val edit: Boolean,
    val selectedNodeId: String?,
    val previous: AnsweredQuestions?,
    val questions: List<String>,
    val answers: List<String>,
    val preview: WorkflowDefinition?,
    val explanation: String,
) {
    init {
        require(questions.isNotEmpty() && questions.size + (previous?.questions?.size ?: 0) <= 5)
        require(questions.all { it.isNotBlank() && it.length <= 1000 })
        require(answers.size == questions.size && answers.all { it.length <= 1000 })
        require(previous == null || previous.requestText == requestText)
    }

    fun withAnswer(index: Int, value: String): ClarificationDraft =
        copy(answers = answers.toMutableList().also { it[index] = value.take(1000) })

    fun answered() = AnsweredQuestions(requestText, previous?.questions.orEmpty() + questions,
        previous?.answers.orEmpty() + answers.map { it.trim() })
}

sealed interface PlannerReply {
    data class Ask(val draft: ClarificationDraft) : PlannerReply
    data class Proposal(val workflow: WorkflowDefinition, val explanation: String) : PlannerReply
    data class Unsupported(val explanation: String) : PlannerReply
}

object PlannerConversation {
    private fun fingerprint(w: WorkflowDefinition): String =
        MessageDigest.getInstance("SHA-256").digest(WorkflowJson.encode(w).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun encode(draft: ClarificationDraft) = WorkflowJson.format.encodeToString(ClarificationDraft.serializer(), draft)

    fun restore(encoded: String?, working: WorkflowDefinition, requestText: String): ClarificationDraft? =
        encoded?.takeIf { it.isNotBlank() }?.let {
            runCatching { WorkflowJson.format.decodeFromString<ClarificationDraft>(it) }.getOrNull()
        }?.takeIf { it.requestText == requestText && it.baseFingerprint == fingerprint(working) }

    fun request(text: String, base: WorkflowDefinition?, selectedNodeId: String?, answers: AnsweredQuestions?,
                capabilities: List<Capability> = emptyList()) = buildJsonObject {
        require(text.isNotBlank() && text.length <= 4000)
        require(answers == null || answers.requestText == text)
        require(selectedNodeId == null || base?.nodes?.any { it.id == selectedNodeId } == true)
        put("requestId", UUID.randomUUID().toString())
        put("mode", if (base == null) "create" else "edit")
        put("requestText", text)
        put("locale", "ko-KR")
        if (base != null) {
            put("baseWorkflow", WorkflowJson.format.encodeToJsonElement(base))
            put("baseRevision", base.revision)
        }
        selectedNodeId?.let { put("selectedNodeId", it) }
        answers?.let { put("clarification", WorkflowJson.format.encodeToJsonElement(it)) }
        val areas=buildJsonArray {
            val offered=mutableSetOf<Pair<ActionType,SeatAxis?>>()
            for(cap in capabilities) {
                if(cap.type !in setOf(ActionType.SET_HVAC_TARGET,ActionType.SET_AMBIENT_BRIGHTNESS,
                        ActionType.SET_AMBIENT_COLOR,ActionType.SET_SEAT_HEAT,ActionType.MOVE_SEAT_LIMITED))continue
                if((cap.type==ActionType.MOVE_SEAT_LIMITED)!=(cap.seatAxis!=null))continue
                val ids=editableVehicleAreas(cap).map { it.id }.filter { it.length<=160 }.take(32)
                if(ids.isEmpty() || !offered.add(cap.type to cap.seatAxis))continue
                add(buildJsonObject {
                    put("type",cap.type.name)
                    put("areas",JsonArray(ids.map(::JsonPrimitive)))
                    cap.seatAxis?.let { put("axis",it.name) }
                })
            }
        }
        if(areas.isNotEmpty())put("vehicleAreas",areas)
    }

    fun read(response: JsonObject, working: WorkflowDefinition, text: String, edit: Boolean,
             selectedNodeId: String?, previous: AnsweredQuestions?): PlannerReply {
        val workflow = response["workflow"]?.takeUnless { it == JsonNull }
        val patch = response["patch"]?.takeUnless { it == JsonNull }
        val explanation = response["explanation"]?.jsonPrimitive?.content.orEmpty()
        require(selectedNodeId == null || edit && working.nodes.any { it.id == selectedNodeId })
        return when (response["status"]?.jsonPrimitive?.content) {
            "needs_clarification" -> {
                require(patch == null && (!edit || workflow == null))
                val questions = response.getValue("questions").jsonArray.map {
                    require(it.jsonPrimitive.isString)
                    it.jsonPrimitive.content
                }
                val preview = workflow?.let { decodeWorkflow(it, partial = true) }
                PlannerReply.Ask(ClarificationDraft(text, fingerprint(working), edit, selectedNodeId,
                    previous, questions, List(questions.size) { "" }, preview, explanation))
            }
            "draft" -> {
                val next = if (edit) {
                    require(workflow == null && patch != null)
                    val changes = WorkflowJson.format.decodeFromJsonElement<WorkflowPatch>(patch)
                    if (selectedNodeId == null) WorkflowEdits.applyPatch(working, changes)
                    else WorkflowEdits.applyScopedPatch(working, changes, selectedNodeId)
                } else {
                    require(patch == null && workflow != null)
                    // The app owns local IDs. Creating a draft must never overwrite an existing routine.
                    decodeWorkflow(workflow, partial = false).copy(id = UUID.randomUUID().toString(), revision = 1)
                }
                PlannerReply.Proposal(next, explanation)
            }
            "unsupported" -> {
                require(workflow == null && patch == null)
                PlannerReply.Unsupported(explanation)
            }
            else -> throw IllegalArgumentException("AI 응답 상태를 확인할 수 없습니다.")
        }
    }

    private fun decodeWorkflow(json: JsonElement, partial: Boolean): WorkflowDefinition {
        val w = WorkflowJson.format.decodeFromJsonElement<WorkflowDefinition>(json)
        require(w.nodes.size in 2..64 && w.edges.size <= 192)
        // Only a seat awaiting an area answer may omit its area in a read-only preview.
        val validationCopy = if (!partial) w else w.copy(nodes = w.nodes.map { node ->
            val action = node.action
            if (action?.type == ActionType.MOVE_SEAT_LIMITED && action.area == "")
                node.copy(action = action.copy(area = "pending-seat"))
            else node
        })
        val issues = WorkflowValidator.validate(validationCopy)
        require(issues.isEmpty()) { issues.first().message }
        return w
    }
}
