package com.pleos.routine.core

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ExecutionSafetyTest {
    private val monday = Instant.parse("2026-10-25T23:00:00Z").toEpochMilli()
    private fun real(gear: String = "P") = ExecutionContext.simulated(monday, gear = gear).let {
        it.copy(source = InputSource.REAL, gear = it.gear.copy(source = InputSource.REAL),
            speedMps = it.speedMps.copy(source = InputSource.REAL),
            outsideTempC = it.outsideTempC.copy(source = InputSource.REAL),
            seatOccupied = it.seatOccupied.copy(source = InputSource.REAL))
    }
    private fun repeated(): WorkflowDefinition {
        val base = WorkflowExamples.blank("repeat-safety")
        return GraphEditing.insert(base, base.edges.single().id, NodeKind.REPEAT)
    }

    @Test fun repeatExitCannotEnterItsOwnBodyOrJoin() = runTest {
        val original = repeated()
        val owner = original.nodes.single { it.kind == NodeKind.REPEAT }
        val start = original.nodes.single { it.kind == NodeKind.START }
        val end = original.nodes.single { it.kind == NodeKind.END }
        val gate = WorkflowNode("gate", NodeKind.IF, "branch", condition = ConditionSpec(ConditionField.OUTSIDE_TEMPERATURE, value = 5.0))
        // Keep END reachable through the other IF path: this must be rejected by
        // scope validation, not accidentally by the unreachable-node check.
        val w = original.copy(nodes = original.nodes + gate, edges = original.edges.map {
            if (it.source == start.id) it.copy(target = gate.id) else it
        } + listOf(WorkflowEdge("gate-in", gate.id, owner.id, "true"), WorkflowEdge("gate-end", gate.id, end.id, "false")))
        val body = w.edges.single { it.source == owner.id && it.port == "body" }.target
        for (badTarget in listOf(body, owner.joinId!!)) {
            val malformed = w.copy(edges = w.edges.map {
                if (it.source == owner.id && it.port == "next") it.copy(target = badTarget) else it
            })
            assertTrue(WorkflowValidator.validate(malformed).any { it.message.contains("블록 외부") })
            var calls = 0
            val adapter = object : ActionAdapter {
                override suspend fun execute(request: ActionRequest, onProgress: suspend (ActionProgress) -> Unit): ActionResult {
                    calls++; return ActionResult(ActionStatus.VERIFIED, "should not run")
                }
                override suspend fun stop(runId: String) = emptyList<ActionResult>()
            }
            assertEquals(RunStatus.BLOCKED, WorkflowEngine(adapter, { real() }).run(malformed).status)
            assertEquals(0, calls)
        }
    }

    @Test fun externalBranchCannotBypassRepeatIntoItsJoin() {
        val w = repeated()
        val owner = w.nodes.single { it.kind == NodeKind.REPEAT }
        val start = w.nodes.single { it.kind == NodeKind.START }
        val gate = WorkflowNode("gate", NodeKind.IF, "branch", condition = ConditionSpec(ConditionField.OUTSIDE_TEMPERATURE, value = 5.0))
        val malformed = w.copy(nodes = w.nodes + gate, edges = w.edges.map {
            if (it.source == start.id) it.copy(target = gate.id) else it
        } + listOf(WorkflowEdge("gate-in", gate.id, owner.id, "true"), WorkflowEdge("gate-bypass", gate.id, owner.joinId!!, "false")))
        assertTrue(WorkflowValidator.validate(malformed).any { it.nodeId == owner.joinId && it.message.contains("블록 외부") })
    }

    @Test fun nestedStructuredScopesStillExecuteTheirDeclaredCount() = runTest {
        val repeated = repeated()
        val bodyEdge = repeated.edges.single { it.port == "body" }
        val nested = GraphEditing.insert(repeated, bodyEdge.id, NodeKind.PARALLEL)
        assertTrue(WorkflowValidator.validate(nested).toString(), WorkflowValidator.validate(nested).isEmpty())
        val result = WorkflowSimulator.simulate(nested, ExecutionContext.simulated(monday))
        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals(4000L, result.elapsedMillis)
    }

    @Test fun cancellationStopsAfterParkingIsLostAndNeverRunsNextAction() = runTest {
        val base = WorkflowExamples.blank("cancel-safety")
        val first = GraphEditing.insert(base, base.edges.single().id, NodeKind.ACTION,
            ActionSpec(ActionType.MOVE_SEAT_LIMITED, area = "ROW_1_LEFT", axis = SeatAxis.FORE_AFT, direction = MoveDirection.BACKWARD))
        val move = first.nodes.single { it.kind == NodeKind.ACTION }
        val w = GraphEditing.insert(first, first.edges.single { it.source == move.id }.id, NodeKind.ACTION,
            ActionSpec(ActionType.SET_HVAC_TARGET, area = "ROW_1_LEFT", value = 23.0))
        var context = real()
        val started = CompletableDeferred<Unit>()
        val cleanup = mutableListOf<String>()
        val calls = mutableListOf<ActionType>()
        var result: RunResult? = null
        val adapter = object : ActionAdapter {
            override suspend fun execute(request: ActionRequest, onProgress: suspend (ActionProgress) -> Unit): ActionResult {
                calls += request.action.type
                onProgress(ActionProgress(ActionStatus.ACKNOWLEDGED, "move accepted"))
                started.complete(Unit)
                awaitCancellation()
            }
            override suspend fun stop(runId: String): List<ActionResult> {
                assertFalse(context.parkedAndStationary())
                cleanup += "requested"
                delay(10) // A cancelled caller must still await STOP observation.
                cleanup += "observed"
                return listOf(ActionResult(ActionStatus.VERIFIED, "STOP observed"))
            }
        }
        val run = launch { result = WorkflowEngine(adapter, { context }).run(w) }
        started.await()
        context = real("D")
        run.cancel()
        run.join()
        assertEquals(listOf(ActionType.MOVE_SEAT_LIMITED), calls)
        assertEquals(listOf("requested", "observed"), cleanup)
        assertEquals(RunStatus.CANCELLED, result!!.status)
        assertFalse(result!!.events.any { it.nodeId == move.id && it.status == ActionStatus.VERIFIED })
    }

    @Test fun failingParallelBranchCancelsSiblingBeforeGlobalCleanup() = runTest {
        val base = WorkflowExamples.blank("parallel-failure")
        val p = GraphEditing.insert(base, base.edges.single().id, NodeKind.PARALLEL)
        val waits = p.nodes.filter { it.kind == NodeKind.WAIT }
        val w = p.copy(nodes = p.nodes.map { n -> when (n.id) {
            waits[0].id -> n.copy(kind = NodeKind.ACTION, durationSeconds = null, action = ActionSpec(ActionType.CALENDAR_READ))
            waits[1].id -> n.copy(kind = NodeKind.ACTION, durationSeconds = null, action = ActionSpec(ActionType.PLACE_SEARCH, text = "test"))
            else -> n
        } })
        assertTrue(WorkflowValidator.validate(w).toString(), WorkflowValidator.validate(w).isEmpty())
        val started = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val adapter = object : ActionAdapter {
            override suspend fun execute(request: ActionRequest, onProgress: suspend (ActionProgress) -> Unit): ActionResult {
                if (request.action.type == ActionType.CALENDAR_READ) {
                    started.complete(Unit)
                    try { awaitCancellation() } finally { order += "sibling-cancelled" }
                }
                started.await()
                return ActionResult(ActionStatus.FAILED, "branch failed")
            }
            override suspend fun stop(runId: String): List<ActionResult> { order += "cleanup"; return emptyList() }
        }
        val result = WorkflowEngine(adapter, { real() }).run(w)
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals(listOf("sibling-cancelled", "cleanup"), order)
        assertFalse(result.events.any { it.nodeId == p.nodes.single { n -> n.kind == NodeKind.JOIN }.id })
    }

    @Test fun cooldownRequiresNewEdgeAtBoundaryAndResetDoesNotReplay() {
        val w = WorkflowExamples.blank("cooldown").copy(trigger = TriggerDefinition(TriggerType.SEAT_OCCUPIED))
        val monitor = TriggerMonitor()
        fun state(at: Long, occupied: Boolean) = ExecutionContext.simulated(monday + at, occupied = occupied).let {
            it.copy(monotonicMillis = at, seatOccupied = it.seatOccupied.copy(observedAtMillis = at))
        }
        assertFalse(monitor.evaluate(w, state(0, false)))
        assertTrue(monitor.evaluate(w, state(1, true)))
        monitor.markStarted(w, 1) // Cooldown begins when the coordinator actually starts execution.
        assertFalse(monitor.evaluate(w, state(299999, false)))
        assertFalse(monitor.evaluate(w, state(300000, true)))
        assertFalse(monitor.evaluate(w, state(300001, true))) // elapsed cooldown is not an event
        assertFalse(monitor.evaluate(w, state(300001, false)))
        assertTrue(monitor.evaluate(w, state(300001, true))) // exact 300000ms boundary, new edge
        monitor.reset()
        assertFalse(monitor.evaluate(w, state(600001, true))) // process restart never replays
    }
}
