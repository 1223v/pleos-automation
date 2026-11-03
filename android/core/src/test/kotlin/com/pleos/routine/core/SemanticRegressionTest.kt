package com.pleos.routine.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SemanticRegressionTest {
    private val monday = Instant.parse("2026-10-25T23:00:00Z").toEpochMilli()
    private fun real(temp: Double = 12.0, gear: String = "P") = ExecutionContext.simulated(monday, temp, gear).let {
        it.copy(source = InputSource.REAL, gear = it.gear.copy(source = InputSource.REAL),
            speedMps = it.speedMps.copy(source = InputSource.REAL), outsideTempC = it.outsideTempC.copy(source = InputSource.REAL),
            seatOccupied = it.seatOccupied.copy(source = InputSource.REAL))
    }
    private fun commute() = WorkflowExamples.commute().let { w -> w.copy(nodes = w.nodes.map {
        if (it.id == "navigation") it.copy(action = it.action!!.copy(latitude = 37.0, longitude = 127.0, text = "office")) else it
    }) }

    @Test fun conditionEnterFiresWhenLastFilterBecomesTrue() {
        val w = WorkflowExamples.blank("conditions").copy(trigger = TriggerDefinition(TriggerType.CONDITION_ENTER,
            condition = ConditionSpec(ConditionField.GEAR, Comparison.EQ, text = "P"),
            filters = listOf(ConditionSpec(ConditionField.SEAT_OCCUPIED, Comparison.EQ, flag = true))))
        val monitor = TriggerMonitor()
        val x = ExecutionContext.simulated(monday, occupied = false)
        assertFalse(monitor.evaluate(w, x))
        assertTrue("combined false -> true is an entry", monitor.evaluate(w, x.copy(seatOccupied = x.seatOccupied.copy(value = true))))
    }

    @Test fun discardedCandidateDoesNotStartCooldown() {
        val w = WorkflowExamples.blank("candidate").copy(trigger = TriggerDefinition(TriggerType.SEAT_OCCUPIED))
        val monitor = TriggerMonitor()
        val x = ExecutionContext.simulated(monday, occupied = false)
        assertFalse(monitor.evaluate(w, x))
        assertTrue(monitor.evaluate(w, x.copy(seatOccupied = x.seatOccupied.copy(value = true))))
        // A busy runtime discarded that candidate, so no run actually started.
        assertFalse(monitor.evaluate(w, x))
        assertTrue(monitor.evaluate(w, x.copy(seatOccupied = x.seatOccupied.copy(value = true))))
    }

    @Test fun warmBranchNeverPreflightsUnavailableHeat() = runBlocking {
        val preflight = mutableListOf<ActionType>()
        val executed = mutableListOf<ActionType>()
        val adapter = object : ActionAdapter {
            override suspend fun preflight(action: ActionSpec, context: ExecutionContext): ActionResult? {
                preflight += action.type
                return if (action.type == ActionType.SET_SEAT_HEAT) ActionResult(ActionStatus.BLOCKED, "heat unavailable") else null
            }
            override suspend fun execute(request: ActionRequest, onProgress: suspend (ActionProgress) -> Unit): ActionResult {
                executed += request.action.type
                return ActionResult(ActionStatus.VERIFIED, "observed")
            }
            override suspend fun stop(runId: String) = emptyList<ActionResult>()
        }
        val result = WorkflowEngine(adapter, { real(gear = "D") }).run(commute())
        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals(listOf(ActionType.NAVIGATE), preflight)
        assertEquals(listOf(ActionType.NAVIGATE), executed)
    }

    @Test fun coldBranchRunsNavigationBeforeHeatPreflightFails() = runBlocking {
        val order = mutableListOf<String>()
        val adapter = object : ActionAdapter {
            override suspend fun preflight(action: ActionSpec, context: ExecutionContext): ActionResult? {
                order += "preflight:${action.type}"
                return if (action.type == ActionType.SET_SEAT_HEAT) ActionResult(ActionStatus.BLOCKED, "heat unavailable") else null
            }
            override suspend fun execute(request: ActionRequest, onProgress: suspend (ActionProgress) -> Unit): ActionResult {
                order += "execute:${request.action.type}"
                return ActionResult(ActionStatus.VERIFIED, "observed")
            }
            override suspend fun stop(runId: String) = emptyList<ActionResult>()
        }
        val result = WorkflowEngine(adapter, { real(temp = 3.0) }).run(commute())
        assertEquals(RunStatus.BLOCKED, result.status)
        assertEquals(listOf("preflight:NAVIGATE", "execute:NAVIGATE", "preflight:SET_SEAT_HEAT"), order)
    }
}
