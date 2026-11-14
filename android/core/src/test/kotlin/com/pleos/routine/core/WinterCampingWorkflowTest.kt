package com.pleos.routine.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WinterCampingWorkflowTest {
    private val base = WorkflowJson.decode(File("../../contracts/fixtures/winter-camping.json").readText())
    private val light = base.nodes.single { it.id == "light30" }
    private val endEdge = base.edges.single { it.source == light.id }

    private fun splice(): WorkflowPatch {
        val wait = WorkflowNode("wait600", NodeKind.WAIT, "10분 대기", durationSeconds = 600)
        val dim = light.copy(id = "light20", label = "조명 20%", action = light.action!!.copy(value = 20.0))
        return WorkflowPatch(base.revision, listOf(
            PatchOperation(PatchType.ADD_NODE, node = wait),
            PatchOperation(PatchType.ADD_NODE, node = dim),
            PatchOperation(PatchType.DISCONNECT, edgeId = endEdge.id),
            PatchOperation(PatchType.CONNECT, edge = WorkflowEdge("to-wait", light.id, wait.id)),
            PatchOperation(PatchType.CONNECT, edge = WorkflowEdge("to-dim", wait.id, dim.id)),
            PatchOperation(PatchType.CONNECT, edge = WorkflowEdge("to-end", dim.id, endEdge.target))
        ))
    }

    @Test fun onlyTheSelectedLightGainsTheDelayAndLowerBrightness() = runBlocking {
        val edited = WorkflowEdits.applyScopedPatch(base, splice(), light.id)
        assertEquals(base.nodes, edited.nodes.filter { it.id in base.nodes.map(WorkflowNode::id) })
        for (temp in listOf(3.0, 5.0, 8.0)) {
            val run = WorkflowSimulator.simulate(edited, ExecutionContext.simulated(0, temp))
            assertEquals(RunStatus.COMPLETED, run.status)
            assertEquals(600_000L, run.elapsedMillis)
            val events = run.events.filter { it.status == ActionStatus.SIMULATED }
            assertEquals(temp <= 5, events.any { it.nodeId == "hvac23" })
            assertEquals(0L, events.single { it.nodeId == "light30" }.elapsedMillis)
            assertEquals(600_000L, events.single { it.nodeId == "light20" }.elapsedMillis)
            assertTrue(events.any { it.nodeId == "seat" })
        }
        val unknown = WorkflowSimulator.simulate(edited, ExecutionContext.simulated(0, null))
        assertEquals(RunStatus.BLOCKED, unknown.status)
        assertFalse(unknown.events.any { it.status == ActionStatus.SIMULATED })
    }

    @Test fun scopedPatchRejectsChangesToOtherNodesAndGlobalProperties() {
        val heating = base.nodes.single { it.id == "hvac23" }
        val changed = heating.copy(action = heating.action!!.copy(value = 19.0))
        val attacks = listOf(
            PatchOperation(PatchType.CHANGE_TITLE, title = "다른 제목"),
            PatchOperation(PatchType.CHANGE_TRIGGER, trigger = TriggerDefinition(TriggerType.VOICE)),
            PatchOperation(PatchType.REPLACE_NODE, nodeId = heating.id, node = changed),
            PatchOperation(PatchType.REPLACE_NODE, nodeId = light.id, node = light.copy(action = heating.action)),
            PatchOperation(PatchType.REPLACE_NODE, nodeId = light.id, node = light.copy(action = light.action!!.copy(area = "ROW_2_LEFT")))
        )
        for (op in attacks) assertThrows(IllegalArgumentException::class.java) {
            WorkflowEdits.applyScopedPatch(base, WorkflowPatch(base.revision, listOf(op)), light.id)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkflowEdits.applyScopedPatch(base.copy(revision = 4), splice(), light.id)
        }
    }

    @Test fun cannotInsertIntoOtherBranchOrReplaceNewLightWithASeatMove() {
        val valid = splice()
        val redirected = valid.operations.map { op -> when {
            op.edgeId == "light30-end" -> op.copy(edgeId = "hvac23-seat")
            op.edge?.source == "light30" -> op.copy(edge = op.edge.copy(source = "hvac23"))
            op.edge?.target == "end" -> op.copy(edge = op.edge.copy(target = "seat"))
            else -> op
        } }
        val foreignAction = valid.operations.map { op ->
            if (op.node?.id == "light20") op.copy(node = op.node.copy(action = base.nodes.single { it.id == "seat" }.action)) else op
        }
        for (ops in listOf(redirected, foreignAction)) assertThrows(IllegalArgumentException::class.java) {
            WorkflowEdits.applyScopedPatch(base, WorkflowPatch(base.revision, ops), light.id)
        }
    }

    @Test fun reusedOutgoingEdgeIdStaysWithinTheOriginalPath() {
        val ops = splice().operations
        val reconnect = ops.last().edge!!
        val reuse = ops.dropLast(1) + ops.last().copy(edge = reconnect.copy(id = endEdge.id))
        val edited = WorkflowEdits.applyScopedPatch(base, WorkflowPatch(base.revision, reuse), light.id)
        assertEquals(reconnect.target, edited.edges.single { it.id == endEdge.id }.target)
        val detached = reuse + PatchOperation(PatchType.DISCONNECT, edgeId = endEdge.id)
        val hijack = ops.dropLast(1) + ops.last().copy(edge = reconnect.copy(id = "cold-seat"))
        for (bad in listOf(detached, hijack)) assertThrows(IllegalArgumentException::class.java) {
            WorkflowEdits.applyScopedPatch(base, WorkflowPatch(base.revision, bad), light.id)
        }
    }
}
