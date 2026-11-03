package com.pleos.routine.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Regression cases from the approved-plan audit (A01 / R05,R14,R15,R17). */
class PlanComplianceTest {
    private val monday = Instant.parse("2026-10-25T23:00:00Z").toEpochMilli() // Monday 08:00 KST
    private val cold = ConditionSpec(ConditionField.OUTSIDE_TEMPERATURE, value = 5.0)

    private fun linear(vararg body: WorkflowNode): WorkflowDefinition {
        val nodes = listOf(WorkflowNode("start", NodeKind.START, "Start")) + body +
            WorkflowNode("end", NodeKind.END, "End")
        return WorkflowDefinition(id = "audit", title = "Audit", nodes = nodes,
            edges = nodes.zipWithNext { a, b -> WorkflowEdge("${a.id}-${b.id}", a.id, b.id) })
    }

    private fun actionGraph(trigger: TriggerDefinition) = linear(
        WorkflowNode("action", NodeKind.ACTION, "Read", action = ActionSpec(ActionType.CALENDAR_READ))
    ).copy(trigger = trigger)

    private fun assertNotExecuted(result: RunResult) {
        assertEquals(RunStatus.BLOCKED, result.status)
        assertFalse(result.events.any { it.status in setOf(ActionStatus.REQUESTED, ActionStatus.SIMULATED, ActionStatus.VERIFIED) })
        assertTrue("A blocked trigger needs an explanation", result.events.any { it.status == ActionStatus.BLOCKED && it.message.isNotBlank() })
    }

    @Test fun simulationRejectsUnoccupiedAndUnknownBoardingState() = runBlocking {
        val w = actionGraph(TriggerDefinition(TriggerType.SEAT_OCCUPIED))
        val empty = ExecutionContext.simulated(monday, occupied = false)
        assertNotExecuted(WorkflowSimulator.simulate(w, empty))
        assertNotExecuted(WorkflowSimulator.simulate(w, empty.copy(seatOccupied = StateValue())))
    }

    @Test fun simulationChecksConditionEnterBaseAsWellAsFilters() = runBlocking {
        val w = actionGraph(TriggerDefinition(TriggerType.CONDITION_ENTER, condition = cold,
            filters = listOf(ConditionSpec(ConditionField.SEAT_OCCUPIED, Comparison.EQ, flag = true))))
        assertNotExecuted(WorkflowSimulator.simulate(w, ExecutionContext.simulated(monday, 12.0)))
        assertNotExecuted(WorkflowSimulator.simulate(w, ExecutionContext.simulated(monday, null)))
        assertNotExecuted(WorkflowSimulator.simulate(w, ExecutionContext.simulated(monday, 3.0, occupied = false)))
        assertEquals(RunStatus.COMPLETED, WorkflowSimulator.simulate(w, ExecutionContext.simulated(monday, 3.0)).status)
    }

    @Test fun simulationChecksScheduledMinuteAndWeekdayInTriggerZone() = runBlocking {
        val w = actionGraph(TriggerDefinition(TriggerType.TIME, minuteOfDay = 480, weekdays = listOf(1)))
        assertNotExecuted(WorkflowSimulator.simulate(w, ExecutionContext.simulated(monday - 60_000)))
        assertNotExecuted(WorkflowSimulator.simulate(w, ExecutionContext.simulated(monday + 86_400_000)))
        assertNotExecuted(WorkflowSimulator.simulate(w.copy(trigger = w.trigger.copy(zoneId = "UTC")), ExecutionContext.simulated(monday)))
        assertEquals(RunStatus.COMPLETED, WorkflowSimulator.simulate(w, ExecutionContext.simulated(monday)).status)
    }

    @Test fun simulationUsesStaticEligibilityWithoutInventingOrRequiringAnEventEdge() = runBlocking {
        val w = actionGraph(TriggerDefinition(TriggerType.SEAT_OCCUPIED))
        val x = ExecutionContext.simulated(monday, occupied = true)
        repeat(2) {
            val result = WorkflowSimulator.simulate(w, x)
            assertEquals(RunStatus.COMPLETED, result.status)
            assertEquals(listOf("action"), result.events.filter { it.status == ActionStatus.SIMULATED }.map { it.nodeId })
        }
        val monitor = TriggerMonitor()
        assertFalse("Static simulation eligibility must not change real initial-true semantics", monitor.evaluate(w, x))
        assertFalse(monitor.evaluate(w, x))
    }

    @Test fun explicitRunDoesNotRequireAnAutomaticTriggerEvent() = runBlocking {
        val context = ExecutionContext(monday, 0)
        for (trigger in listOf(
            TriggerDefinition(TriggerType.SEAT_OCCUPIED),
            TriggerDefinition(TriggerType.CONDITION_ENTER, condition = cold),
            TriggerDefinition(TriggerType.TIME, minuteOfDay = 1, weekdays = listOf(7))
        )) {
            var calls = 0
            val adapter = object : ActionAdapter {
                override suspend fun execute(request: ActionRequest, onProgress: suspend (ActionProgress) -> Unit): ActionResult {
                    calls++; return ActionResult(ActionStatus.VERIFIED, "read complete")
                }
                override suspend fun stop(runId: String) = emptyList<ActionResult>()
            }
            val result = WorkflowEngine(adapter, { context }).run(actionGraph(trigger))
            assertEquals(trigger.type.name, RunStatus.COMPLETED, result.status)
            assertEquals(1, calls)
        }
    }

    @Test fun manualAndVoiceSimulationKeepWholeRunFilters() = runBlocking {
        for (type in listOf(TriggerType.MANUAL, TriggerType.VOICE)) {
            val w = actionGraph(TriggerDefinition(type, filters = listOf(cold)))
            assertNotExecuted(WorkflowSimulator.simulate(w, ExecutionContext.simulated(monday, 12.0)))
            assertEquals(RunStatus.COMPLETED, WorkflowSimulator.simulate(w, ExecutionContext.simulated(monday, 3.0)).status)
        }
    }

    @Test fun authorizationWaitRechecksExpiredTimeFilterBeforeFirstAction() = runBlocking {
        val window = ConditionSpec(ConditionField.TIME_WINDOW, startMinute = 420, endMinute = 540)
        val w = actionGraph(TriggerDefinition(filters = listOf(window)))
        var live = ExecutionContext(monday, 0)
        var calls = 0
        val adapter = object : ActionAdapter {
            override suspend fun authorizeRun(workflow: WorkflowDefinition, runId: String, context: ExecutionContext): ActionResult? {
                live = live.copy(epochMillis = monday + 3_600_000)
                return null
            }
            override suspend fun execute(request: ActionRequest, onProgress: suspend (ActionProgress) -> Unit): ActionResult {
                calls++; return ActionResult(ActionStatus.VERIFIED, "must not execute")
            }
            override suspend fun stop(runId: String) = emptyList<ActionResult>()
        }
        val result = WorkflowEngine(adapter, { live }).run(w)
        assertNotExecuted(result)
        assertEquals(0, calls)
    }

    @Test fun authorizationWaitRechecksUnknownSensorFilterBeforeFirstAction() = runBlocking {
        val w = actionGraph(TriggerDefinition(filters = listOf(cold)))
        var live = ExecutionContext(monday, 0, outsideTempC = StateValue(3.0, 0, true, InputSource.REAL))
        var calls = 0
        val adapter = object : ActionAdapter {
            override suspend fun authorizeRun(workflow: WorkflowDefinition, runId: String, context: ExecutionContext): ActionResult? {
                live = live.copy(outsideTempC = StateValue())
                return null
            }
            override suspend fun execute(request: ActionRequest, onProgress: suspend (ActionProgress) -> Unit): ActionResult {
                calls++; return ActionResult(ActionStatus.VERIFIED, "must not execute")
            }
            override suspend fun stop(runId: String) = emptyList<ActionResult>()
        }
        assertNotExecuted(WorkflowEngine(adapter, { live }).run(w))
        assertEquals(0, calls)
    }

    @Test fun blockedSimulationRetainsTimeAlreadySpentWaiting() = runBlocking {
        val w = linear(WorkflowNode("wait", NodeKind.WAIT, "Wait", durationSeconds = 7200),
            WorkflowNode("timer", NodeKind.TIMER, "Timer", durationSeconds = 1))
        val result = WorkflowSimulator.simulate(w, ExecutionContext.simulated(monday))
        assertEquals(RunStatus.BLOCKED, result.status)
        assertEquals(7_200_000L, result.elapsedMillis)
        assertEquals(7_200_000L, result.events.single { it.nodeId == "wait" && it.status == ActionStatus.SIMULATED }.elapsedMillis)
    }

    @Test fun unknownConditionAfterWaitRetainsSimulatedElapsedTime() = runBlocking {
        val b = linear(WorkflowNode("wait", NodeKind.WAIT, "Wait", durationSeconds = 10))
        val w = GraphEditing.insert(b, b.edges.single { it.source == "wait" }.id, NodeKind.IF)
        val result = WorkflowSimulator.simulate(w, ExecutionContext.simulated(monday, null))
        assertEquals(RunStatus.BLOCKED, result.status)
        assertEquals(10_000L, result.elapsedMillis)
    }

    @Test fun insertionUsesTheResultingNodeCountForEveryStructuredFragment() {
        fun graph(count: Int) = linear(*Array(count - 2) {
            WorkflowNode("wait$it", NodeKind.WAIT, "Wait", durationSeconds = 1)
        })
        for ((kind, added) in listOf(NodeKind.ACTION to 1, NodeKind.IF to 1, NodeKind.WAIT to 1,
            NodeKind.TIMER to 1, NodeKind.REPEAT to 3, NodeKind.PARALLEL to 4)) {
            val base = graph(64 - added)
            val inserted = GraphEditing.insert(base, base.edges.first().id, kind)
            assertEquals(kind.name, 64, inserted.nodes.size)
            assertTrue(kind.name, WorkflowValidator.validate(inserted).isEmpty())
            val overflow = graph(65 - added)
            assertThrows(kind.name, IllegalArgumentException::class.java) {
                GraphEditing.insert(overflow, overflow.edges.first().id, kind)
            }
            assertEquals(65 - added, overflow.nodes.size)
        }
    }

    @Test fun seatDescriptionExplainsApprovalBeforeTheWholeRunOnce() {
        for (axis in SeatAxis.values()) {
            val description = RuleDescriptions.action(ActionSpec(ActionType.MOVE_SEAT_LIMITED,
                axis = axis, direction = MoveDirection.BACKWARD))
            assertTrue(description, description.contains("전체 실행 전 1회 확인"))
            assertFalse(description.contains("매 이동 확인"))
        }
    }
}
