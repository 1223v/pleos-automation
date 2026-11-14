package com.pleos.routine.data

import com.pleos.routine.core.*
import com.pleos.routine.vehicle.Capability
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PlannerConversationTest {
    private val working = WorkflowExamples.blank("working")
    private val original = "겨울 차박 루틴을 만들어 줘"

    private fun question(text: String, preview: WorkflowDefinition? = null) = buildJsonObject {
        put("status", "needs_clarification")
        put("explanation", "좌석을 확인해 주세요.")
        put("questions", JsonArray(listOf(JsonPrimitive(text))))
        put("workflow", preview?.let { WorkflowJson.format.encodeToJsonElement(it) } ?: JsonNull)
        put("patch", JsonNull)
    }

    @Test fun completeLookingPartialIsOnlyAQuestionAndRoundTripKeepsAnswerInput() {
        val first = PlannerConversation.read(question("어느 좌석인가요?", working), working, original, false, null, null)
        val asking = (first as PlannerReply.Ask).draft.withAnswer(0, "앞줄 왼쪽")
        assertEquals(working, asking.preview)
        val restored = PlannerConversation.restore(PlannerConversation.encode(asking), working, original)
        assertEquals(asking, restored)
        assertNull(PlannerConversation.restore(PlannerConversation.encode(asking), working.copy(title="다른 이름"), original))
        assertNull(PlannerConversation.restore(PlannerConversation.encode(asking), working, "새 요청"))
        val wire = PlannerConversation.request(original, null, null, asking.answered())
        assertEquals(original, wire["requestText"]!!.jsonPrimitive.content)
        val history = wire["clarification"]!!.jsonObject
        assertEquals("어느 좌석인가요?", history["questions"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("앞줄 왼쪽", history["answers"]!!.jsonArray.single().jsonPrimitive.content)
        val second = PlannerConversation.read(question("어느 방향인가요?"), working, original, false, null, asking.answered())
        val answered = (second as PlannerReply.Ask).draft.withAnswer(0, "뒤로").answered()
        assertEquals(listOf("어느 좌석인가요?", "어느 방향인가요?"), answered.questions)
        assertEquals(listOf("앞줄 왼쪽", "뒤로"), answered.answers)
    }

    @Test fun selectedEditSendsIdAndRevisionAndRefusesOutsidePatch() {
        val base = WorkflowExamples.rest()
        val selected = "action1"
        val wire = PlannerConversation.request("이것만 바꿔 줘", base, selected, null)
        assertEquals(selected, wire["selectedNodeId"]!!.jsonPrimitive.content)
        assertEquals(base.revision, wire["baseRevision"]!!.jsonPrimitive.int)
        val other = base.nodes.first { it.id == "action0" }
        val edit = WorkflowPatch(base.revision,listOf(PatchOperation(PatchType.REPLACE_NODE,nodeId=other.id,node=other.copy(label="다른 동작"))))
        val response = buildJsonObject {
            put("status","draft");put("patch",WorkflowJson.format.encodeToJsonElement(edit))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlannerConversation.read(response,base,"이것만 바꿔 줘",true,selected,null)
        }
        assertThrows(IllegalArgumentException::class.java) { PlannerConversation.request("요청",null,selected,null) }
    }

    @Test fun unsupportedOrMalformedRepliesCannotBecomeProposals() {
        val bad = buildJsonObject {
            put("status","unsupported");put("workflow",WorkflowJson.format.encodeToJsonElement(working))
        }
        assertThrows(IllegalArgumentException::class.java) { PlannerConversation.read(bad,working,original,false,null,null) }
        val onlyPreview = buildJsonObject { put("workflow",WorkflowJson.format.encodeToJsonElement(working)) }
        assertThrows(IllegalArgumentException::class.java) { PlannerConversation.read(onlyPreview,working,original,false,null,null) }
        val asking = PlannerConversation.read(question("좌석은?"),working,original,false,null,null) as PlannerReply.Ask
        assertThrows(IllegalArgumentException::class.java) { asking.draft.answered() }
    }

    @Test fun onlyActualWritableAreaIdsAndSeatAxisAreShared() {
        val combined="ROW_1_LEFT+ROW_1_RIGHT"
        val light=Capability(ActionType.SET_AMBIENT_BRIGHTNESS,"READ_WRITE",listOf("DRIVER",combined,combined))
        val seat=Capability(ActionType.MOVE_SEAT_LIMITED,"READ_WRITE",listOf("ROW_1_LEFT"),message="private",seatAxis=SeatAxis.BACKREST)
        val caps=listOf(seat.copy(seatAxis=null),light.copy(access="READ_ONLY"),light,seat,light.copy(type=ActionType.OPEN_APP))
        val request=PlannerConversation.request(original,null,null,null,caps)
        assertEquals(Json.parseToJsonElement("""[{"type":"SET_AMBIENT_BRIGHTNESS","areas":["ROW_1_LEFT+ROW_1_RIGHT"]},{"type":"MOVE_SEAT_LIMITED","areas":["ROW_1_LEFT"],"axis":"BACKREST"}]"""),request["vehicleAreas"])
        assertNull(PlannerConversation.request(original,null,null,null)["vehicleAreas"])
    }
}
