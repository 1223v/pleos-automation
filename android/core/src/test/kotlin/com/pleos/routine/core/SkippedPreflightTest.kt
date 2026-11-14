package com.pleos.routine.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** A preflight may leave one step unexecuted on purpose; that is neither a request nor a failure. */
class SkippedPreflightTest {
    private fun real() = ExecutionContext.simulated(0).let {
        it.copy(source = InputSource.REAL, gear = it.gear.copy(source = InputSource.REAL),
            speedMps = it.speedMps.copy(source = InputSource.REAL),
            outsideTempC = it.outsideTempC.copy(source = InputSource.REAL),
            seatOccupied = it.seatOccupied.copy(source = InputSource.REAL))
    }
    private val light = ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS, area = "ROW_1_LEFT", value = 30.0)
    private val seat = ActionSpec(ActionType.MOVE_SEAT_LIMITED, area = "ROW_1_LEFT", axis = SeatAxis.BACKREST, direction = MoveDirection.BACKWARD)
    private val graph = WorkflowDefinition(id = "skip", title = "skip", nodes = listOf(
        WorkflowNode("start", NodeKind.START, "start"),
        WorkflowNode("before", NodeKind.ACTION, "before", action = light),
        WorkflowNode("seat", NodeKind.ACTION, "seat", action = seat),
        WorkflowNode("after", NodeKind.ACTION, "after", action = light.copy(value = 20.0)),
        WorkflowNode("end", NodeKind.END, "end")),
        edges = listOf(WorkflowEdge("1", "start", "before"), WorkflowEdge("2", "before", "seat"),
            WorkflowEdge("3", "seat", "after"), WorkflowEdge("4", "after", "end")))

    private class Recorder(private val seatPreflight: ActionResult?) : ActionAdapter {
        val executed = mutableListOf<String>()
        override suspend fun preflight(action: ActionSpec, context: ExecutionContext) =
            if (action.type == ActionType.MOVE_SEAT_LIMITED) seatPreflight else null
        override suspend fun execute(request: ActionRequest, onProgress: suspend (ActionProgress) -> Unit): ActionResult {
            executed += request.nodeId
            return ActionResult(ActionStatus.VERIFIED, "done")
        }
        override suspend fun stop(runId: String) = emptyList<ActionResult>()
    }

    @Test fun graphIsValidSoOnlyThePreflightDecidesTheOutcome() {
        assertEquals(emptyList<ValidationIssue>(), WorkflowValidator.validate(graph))
    }

    @Test fun skippedPreflightIsRecordedWithoutARequestAndLaterStepsStillRun() = runTest {
        val adapter = Recorder(ActionResult(ActionStatus.SKIPPED, "not executed"))
        val result = WorkflowEngine(adapter, { real() }).run(graph)
        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals(listOf("before", "after"), adapter.executed)
        assertEquals(listOf(ActionStatus.SKIPPED), result.events.filter { it.nodeId == "seat" }.map { it.status })
        assertEquals("not executed", result.events.single { it.nodeId == "seat" }.message)
    }

    @Test fun blockedPreflightStillStopsBeforeLaterSteps() = runTest {
        val adapter = Recorder(ActionResult(ActionStatus.BLOCKED, "locked"))
        val result = WorkflowEngine(adapter, { real() }).run(graph)
        assertEquals(RunStatus.BLOCKED, result.status)
        assertEquals(listOf("before"), adapter.executed)
    }
}
