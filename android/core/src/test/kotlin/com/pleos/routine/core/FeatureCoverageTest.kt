package com.pleos.routine.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Pure core contracts (R04/R05/R10/R15/R16, T14/T15/T18/T20/T40/T41), without device or AI IO. */
class FeatureCoverageTest {
    private val monday = Instant.parse("2026-10-25T23:00:00Z").toEpochMilli()

    private fun linear(vararg body: WorkflowNode): WorkflowDefinition {
        val nodes = listOf(WorkflowNode("start", NodeKind.START, "Start")) + body +
            WorkflowNode("end", NodeKind.END, "End")
        return WorkflowDefinition(id = "feature", title = "Feature contract", nodes = nodes,
            edges = nodes.zipWithNext { from, to -> WorkflowEdge("${from.id}-${to.id}", from.id, to.id) })
    }

    private fun actionGraph(action: ActionSpec) = linear(WorkflowNode("action", NodeKind.ACTION, "Action", action = action))

    private fun assertValidAction(action: ActionSpec) = runBlocking {
        val graph = actionGraph(action)
        val issues = WorkflowValidator.validate(graph)
        assertTrue("${action.type}: $issues", issues.isEmpty())
        val result = WorkflowSimulator.simulate(graph, ExecutionContext.simulated(monday))
        assertEquals(action.type.name, RunStatus.COMPLETED, result.status)
        assertEquals(listOf("action"), result.events.filter { it.status == ActionStatus.SIMULATED }.map { it.nodeId })
        assertFalse(result.events.any { it.status == ActionStatus.VERIFIED })
    }

    private fun assertRejectedAction(action: ActionSpec) {
        assertTrue("Invalid ${action.type} was accepted: $action",
            WorkflowValidator.validate(actionGraph(action)).any { it.nodeId == "action" })
    }

    @Test fun navigationRequiresNamedDestinationWithinCoordinateBounds() {
        val destination = ActionSpec(ActionType.NAVIGATE, text = "Destination", latitude = 90.0, longitude = 180.0)
        assertValidAction(destination)
        assertValidAction(destination.copy(latitude = -90.0, longitude = -180.0))
        for (invalid in listOf(destination.copy(latitude = null), destination.copy(longitude = null),
            destination.copy(latitude = 90.0001), destination.copy(latitude = -90.0001),
            destination.copy(longitude = 180.0001), destination.copy(longitude = -180.0001),
            destination.copy(latitude = Double.NaN), destination.copy(longitude = Double.POSITIVE_INFINITY),
            destination.copy(text = " "))) assertRejectedAction(invalid)
    }

    @Test fun cancelNavigationNeedsNoDestinationButStillValidatesArea() {
        assertValidAction(ActionSpec(ActionType.CANCEL_NAVIGATION))
        assertRejectedAction(ActionSpec(ActionType.CANCEL_NAVIGATION, area = " "))
    }

    @Test fun hvacRequiresFiniteTargetWithoutInventingDeviceRange() {
        assertValidAction(ActionSpec(ActionType.SET_HVAC_TARGET, area = "ROW_1_LEFT", value = 23.5))
        for (value in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertRejectedAction(ActionSpec(ActionType.SET_HVAC_TARGET, value = value))
        }
        // Supported temperature range belongs to device capability preflight, not this schema.
    }

    @Test fun ambientBrightnessAcceptsBothEndpointsAndRejectsValuesOutsidePercentRange() {
        for (value in listOf(0.0, 30.5, 100.0)) assertValidAction(ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS, value = value))
        for (value in listOf(null, -0.001, 100.001, Double.NaN)) {
            assertRejectedAction(ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS, value = value))
        }
    }

    @Test fun ambientColorRequiresOpaqueArgbIndependentlyFromBrightness() {
        for (color in listOf("#FF000000", "#FFFFFFFF", "#FFaBcDeF")) {
            assertValidAction(ActionSpec(ActionType.SET_AMBIENT_COLOR, text = color))
        }
        for (color in listOf(null, "", "#000000", "#804B85DB", "#FF12345G", "#FF1234567")) {
            assertRejectedAction(ActionSpec(ActionType.SET_AMBIENT_COLOR, text = color))
        }
    }

    @Test fun seatHeatAcceptsOffAndWholeLevelsButRejectsFractionsAndNegativeLevels() {
        for (value in listOf(0.0, 2.0)) assertValidAction(ActionSpec(ActionType.SET_SEAT_HEAT, value = value))
        for (value in listOf(null, -1.0, 0.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertRejectedAction(ActionSpec(ActionType.SET_SEAT_HEAT, value = value))
        }
    }

    @Test fun seatMovementRequiresExplicitAxisAndDirectionForBothAxes() {
        for (axis in SeatAxis.values()) for (direction in MoveDirection.values()) {
            assertValidAction(ActionSpec(ActionType.MOVE_SEAT_LIMITED, axis = axis, direction = direction))
        }
        assertRejectedAction(ActionSpec(ActionType.MOVE_SEAT_LIMITED, direction = MoveDirection.BACKWARD))
        assertRejectedAction(ActionSpec(ActionType.MOVE_SEAT_LIMITED, axis = SeatAxis.BACKREST))
    }

    @Test fun localAudioRejectsExternalOrUnbundledSources() {
        assertValidAction(ActionSpec(ActionType.PLAY_LOCAL_AUDIO, targetId = "rest-tone"))
        for (target in listOf(null, "", "other-tone", "https://example.invalid/audio.mp3", "../rest-tone")) {
            assertRejectedAction(ActionSpec(ActionType.PLAY_LOCAL_AUDIO, targetId = target))
        }
    }

    @Test fun stopLocalAudioNeedsNoTrackButStillValidatesEnvelope() {
        assertValidAction(ActionSpec(ActionType.STOP_LOCAL_AUDIO))
        assertRejectedAction(ActionSpec(ActionType.STOP_LOCAL_AUDIO, area = "x".repeat(161)))
    }

    @Test fun openAppRequiresSelectedTarget() {
        assertValidAction(ActionSpec(ActionType.OPEN_APP, targetId = "com.example.selected"))
        for (target in listOf(null, "", " ")) assertRejectedAction(ActionSpec(ActionType.OPEN_APP, targetId = target))
        // PackageManager eligibility is an app boundary; a core simulation never opens this package.
    }

    @Test fun mediaControlRequiresConnectedTarget() {
        assertValidAction(ActionSpec(ActionType.MEDIA_CONTROL, targetId = "selected-media"))
        for (target in listOf(null, "", " ")) assertRejectedAction(ActionSpec(ActionType.MEDIA_CONTROL, targetId = target))
    }

    @Test fun calendarReadNeedsNoPhysicalTargetButStillRejectsInvalidEnvelope() {
        assertValidAction(ActionSpec(ActionType.CALENDAR_READ))
        assertRejectedAction(ActionSpec(ActionType.CALENDAR_READ, area = ""))
    }

    @Test fun homeAssistantCallRequiresSelectedTarget() {
        assertValidAction(ActionSpec(ActionType.HA_CALL, targetId = "light.selected"))
        for (target in listOf(null, "", " ")) assertRejectedAction(ActionSpec(ActionType.HA_CALL, targetId = target))
    }

    @Test fun placeSearchRequiresNonblankQuery() {
        assertValidAction(ActionSpec(ActionType.PLACE_SEARCH, text = "서울역"))
        for (query in listOf(null, "", " \n")) assertRejectedAction(ActionSpec(ActionType.PLACE_SEARCH, text = query))
    }

    @Test fun what3wordsResolveRequiresNonblankAddress() {
        assertValidAction(ActionSpec(ActionType.WHAT3WORDS_RESOLVE, text = "filled.count.soap"))
        for (address in listOf(null, "", " \t")) assertRejectedAction(ActionSpec(ActionType.WHAT3WORDS_RESOLVE, text = address))
    }

    @Test fun nonfiniteRejectedActionReturnsBlockedInsteadOfThrowingWhileBuildingResult() = runBlocking {
        val graph = actionGraph(ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS, value = Double.NaN))
        val result = WorkflowSimulator.simulate(graph, ExecutionContext.simulated(monday))
        assertEquals(RunStatus.BLOCKED, result.status)
        assertFalse(result.events.any { it.status == ActionStatus.SIMULATED || it.status == ActionStatus.REQUESTED })
    }

    @Test fun numericConditionsDistinguishStrictAndInclusiveBoundaries() {
        val expected = mapOf(
            Comparison.EQ to listOf(Truth.FALSE, Truth.TRUE, Truth.FALSE),
            Comparison.NE to listOf(Truth.TRUE, Truth.FALSE, Truth.TRUE),
            Comparison.LT to listOf(Truth.TRUE, Truth.FALSE, Truth.FALSE),
            Comparison.LTE to listOf(Truth.TRUE, Truth.TRUE, Truth.FALSE),
            Comparison.GT to listOf(Truth.FALSE, Truth.FALSE, Truth.TRUE),
            Comparison.GTE to listOf(Truth.FALSE, Truth.TRUE, Truth.TRUE))
        for (field in listOf(ConditionField.OUTSIDE_TEMPERATURE, ConditionField.SPEED)) {
            for ((operator, truths) in expected) {
                val condition = ConditionSpec(field, operator, value = 5.0)
                val actual = listOf(4.999, 5.0, 5.001).map { value ->
                    val context = ExecutionContext.simulated(monday, outsideTempC = value, speedMps = value)
                    Conditions.evaluate(condition, context).truth
                }
                assertEquals("$field $operator", truths, actual)
            }
        }
    }

    @Test fun numericConditionsKeepStaleMissingInvalidAndForeignInputsUnknown() {
        val context = ExecutionContext.simulated(monday).copy(monotonicMillis = 2_000)
        val fresh = StateValue(3.0, 0, true, InputSource.SIMULATION)
        val invalid = listOf(fresh.copy(observedAtMillis = -1), fresh.copy(observedAtMillis = 2_001),
            fresh.copy(value = null), fresh.copy(valid = false), fresh.copy(source = InputSource.REAL),
            fresh.copy(source = InputSource.UNKNOWN), fresh.copy(value = Double.NaN),
            fresh.copy(value = Double.POSITIVE_INFINITY), fresh.copy(value = Double.NEGATIVE_INFINITY))
        for (field in listOf(ConditionField.OUTSIDE_TEMPERATURE, ConditionField.SPEED)) {
            val condition = ConditionSpec(field, value = 5.0)
            assertEquals(Truth.TRUE, Conditions.evaluate(condition, context.copy(outsideTempC = fresh, speedMps = fresh)).truth)
            for (state in invalid) {
                assertEquals("$field with $state", Truth.UNKNOWN,
                    Conditions.evaluate(condition, context.copy(outsideTempC = state, speedMps = state)).truth)
            }
            assertEquals(Truth.UNKNOWN, Conditions.evaluate(condition.copy(value = null), context).truth)
        }
    }

    @Test fun gearAndOccupancyComparisonsKeepUnknownSeparateFromFalse() {
        val context = ExecutionContext.simulated(monday)
        val gear = ConditionSpec(ConditionField.GEAR, Comparison.EQ, text = "P")
        val occupied = ConditionSpec(ConditionField.SEAT_OCCUPIED, Comparison.EQ, flag = true)
        assertEquals(Truth.TRUE, Conditions.evaluate(gear, context).truth)
        assertEquals(Truth.FALSE, Conditions.evaluate(gear.copy(operator = Comparison.NE), context).truth)
        assertEquals(Truth.FALSE, Conditions.evaluate(gear, context.copy(gear = context.gear.copy(value = "D"))).truth)
        assertEquals(Truth.TRUE, Conditions.evaluate(occupied, context).truth)
        assertEquals(Truth.FALSE, Conditions.evaluate(occupied.copy(operator = Comparison.NE), context).truth)
        assertEquals(Truth.FALSE, Conditions.evaluate(occupied, context.copy(seatOccupied = context.seatOccupied.copy(value = false))).truth)
        for (invalid in listOf(context.copy(monotonicMillis = 2_001),
            context.copy(gear = StateValue(), seatOccupied = StateValue()),
            context.copy(gear = context.gear.copy(source = InputSource.REAL), seatOccupied = context.seatOccupied.copy(source = InputSource.REAL)),
            context.copy(gear = context.gear.copy(observedAtMillis = 1), seatOccupied = context.seatOccupied.copy(observedAtMillis = 1)))) {
            assertEquals(Truth.UNKNOWN, Conditions.evaluate(gear, invalid).truth)
            assertEquals(Truth.UNKNOWN, Conditions.evaluate(occupied.copy(operator = Comparison.NE), invalid).truth)
        }
    }

    @Test fun timeWindowIncludesStartExcludesEndAndUsesConfiguredZone() {
        val window = ConditionSpec(ConditionField.TIME_WINDOW, startMinute = 420, endMinute = 540)
        val cases = listOf("2026-10-25T21:59:59Z" to Truth.FALSE, "2026-10-25T22:00:00Z" to Truth.TRUE,
            "2026-10-25T23:59:59Z" to Truth.TRUE, "2026-10-26T00:00:00Z" to Truth.FALSE)
        for ((instant, expected) in cases) assertEquals(instant, expected,
            Conditions.evaluate(window, ExecutionContext.simulated(Instant.parse(instant).toEpochMilli()), "Asia/Seoul").truth)
        assertEquals(Truth.FALSE, Conditions.evaluate(window, ExecutionContext.simulated(monday), "UTC").truth)
        val fullDay = window.copy(startMinute = 0, endMinute = 1440)
        assertEquals(Truth.TRUE, Conditions.evaluate(fullDay, ExecutionContext.simulated(Instant.parse("2026-10-26T14:59:59Z").toEpochMilli())).truth)
    }

    @Test fun weekdayUsesLocalDayAcrossUtcMidnightBoundary() {
        val mondayOnly = ConditionSpec(ConditionField.WEEKDAY, weekdays = listOf(1))
        assertEquals(Truth.TRUE, Conditions.evaluate(mondayOnly, ExecutionContext.simulated(monday), "Asia/Seoul").truth)
        assertEquals(Truth.FALSE, Conditions.evaluate(mondayOnly, ExecutionContext.simulated(monday), "UTC").truth)
        assertEquals(Truth.FALSE, Conditions.evaluate(mondayOnly, ExecutionContext.simulated(monday + 86_400_000)).truth)
    }

    @Test fun malformedConditionsAreRejectedBeforeTheyCanBecomeExecutionRules() {
        val conditions = listOf(
            ConditionSpec(ConditionField.OUTSIDE_TEMPERATURE, value = Double.NaN),
            ConditionSpec(ConditionField.SPEED, value = null),
            ConditionSpec(ConditionField.GEAR, Comparison.LTE, text = "P"),
            ConditionSpec(ConditionField.GEAR, Comparison.EQ, text = "UNKNOWN"),
            ConditionSpec(ConditionField.SEAT_OCCUPIED, Comparison.GT, flag = true),
            ConditionSpec(ConditionField.SEAT_OCCUPIED, Comparison.EQ),
            ConditionSpec(ConditionField.TIME_WINDOW, startMinute = 1380, endMinute = 60),
            ConditionSpec(ConditionField.TIME_WINDOW, startMinute = 420, endMinute = 420),
            ConditionSpec(ConditionField.TIME_WINDOW, startMinute = -1, endMinute = 1440),
            ConditionSpec(ConditionField.TIME_WINDOW, startMinute = 0, endMinute = 1441),
            ConditionSpec(ConditionField.WEEKDAY), ConditionSpec(ConditionField.WEEKDAY, weekdays = listOf(0, 8)))
        for (condition in conditions) {
            val graph = WorkflowExamples.blank("condition-validation").copy(
                trigger = TriggerDefinition(TriggerType.CONDITION_ENTER, condition = condition))
            assertTrue("Accepted $condition", WorkflowValidator.validate(graph).isNotEmpty())
        }
    }

    @Test fun sixtyFourNodeGraphRunsButSixtyFifthNodeIsRejected() = runBlocking {
        fun graph(waits: Int) = linear(*Array(waits) { WorkflowNode("wait$it", NodeKind.WAIT, "Wait", durationSeconds = 1) })
        val atLimit = graph(62)
        assertTrue(WorkflowValidator.validate(atLimit).isEmpty())
        val result = WorkflowSimulator.simulate(atLimit, ExecutionContext.simulated(monday))
        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals(62_000L, result.elapsedMillis)
        assertEquals(62, result.events.count { it.status == ActionStatus.SIMULATED })
        val overLimit = graph(63)
        assertTrue(WorkflowValidator.validate(overLimit).isNotEmpty())
        assertEquals(RunStatus.BLOCKED, WorkflowSimulator.simulate(overLimit, ExecutionContext.simulated(monday)).status)
    }

    private fun parallel(vararg actions: ActionSpec?): WorkflowDefinition {
        val nodes = listOf(WorkflowNode("start", NodeKind.START, "Start"),
            WorkflowNode("parallel", NodeKind.PARALLEL, "Parallel", joinId = "join"),
            WorkflowNode("join", NodeKind.JOIN, "Join"), WorkflowNode("end", NodeKind.END, "End")) +
            actions.mapIndexed { index, action ->
                if (action == null) WorkflowNode("branch$index", NodeKind.WAIT, "Wait", durationSeconds = index + 1L)
                else WorkflowNode("branch$index", NodeKind.ACTION, "Action", action = action)
            }
        val edges = listOf(WorkflowEdge("start-parallel", "start", "parallel"), WorkflowEdge("join-end", "join", "end")) +
            actions.indices.flatMap { index -> listOf(WorkflowEdge("entry$index", "parallel", "branch$index", "branch:$index"),
                WorkflowEdge("exit$index", "branch$index", "join")) }
        return WorkflowDefinition(id = "parallel-features", title = "Parallel features", nodes = nodes, edges = edges)
    }

    @Test fun fourParallelBranchesJoinAtLongestDurationButFifthBranchIsRejected() = runBlocking {
        val graph = parallel(null, null, null, null)
        assertTrue(WorkflowValidator.validate(graph).isEmpty())
        val result = WorkflowSimulator.simulate(graph, ExecutionContext.simulated(monday))
        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals(4_000L, result.elapsedMillis)
        assertEquals(listOf(listOf("branch:0"), listOf("branch:1"), listOf("branch:2"), listOf("branch:3")),
            result.events.filter { it.status == ActionStatus.SIMULATED }.sortedBy { it.nodeId }.map { it.branches })
        assertTrue(WorkflowValidator.validate(parallel(null, null, null, null, null)).isNotEmpty())
    }

    @Test fun twentyRepeatIterationsRunExactlyOnceEachButZeroAndTwentyOneAreRejected() = runBlocking {
        val base = WorkflowExamples.blank("repeat-limit")
        val inserted = GraphEditing.insert(base, base.edges.single().id, NodeKind.REPEAT)
        fun count(value: Int) = inserted.copy(nodes = inserted.nodes.map { if (it.kind == NodeKind.REPEAT) it.copy(repeatCount = value) else it })
        val result = WorkflowSimulator.simulate(count(20), ExecutionContext.simulated(monday))
        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals(20_000L, result.elapsedMillis)
        assertEquals((1..20).map { listOf(it) }, result.events.filter { it.status == ActionStatus.SIMULATED }.map { it.iterations })
        for (invalid in listOf(0, 21)) assertTrue(WorkflowValidator.validate(count(invalid)).isNotEmpty())
    }

    @Test fun waitAndTimerAllowFullTwoHoursButRejectZeroNegativeAndOverflowDurations() = runBlocking {
        for (kind in listOf(NodeKind.WAIT, NodeKind.TIMER)) {
            val full = linear(WorkflowNode("delay", kind, "Delay", durationSeconds = 7_200))
            val result = WorkflowSimulator.simulate(full, ExecutionContext.simulated(monday))
            assertEquals(kind.name, RunStatus.COMPLETED, result.status)
            assertEquals(7_200_000L, result.elapsedMillis)
            for (seconds in listOf(null, -1L, 0L, 7_201L, Long.MAX_VALUE)) {
                assertTrue("$kind $seconds", WorkflowValidator.validate(linear(WorkflowNode("delay", kind, "Delay", durationSeconds = seconds))).isNotEmpty())
            }
        }
    }

    @Test fun cumulativeWaitLimitBlocksBeforeOverrunningNodeAndItsFollowingAction() = runBlocking {
        val graph = linear(WorkflowNode("wait", NodeKind.WAIT, "Wait", durationSeconds = 7_200),
            WorkflowNode("timer", NodeKind.TIMER, "Timer", durationSeconds = 1),
            WorkflowNode("after", NodeKind.ACTION, "After", action = ActionSpec(ActionType.CALENDAR_READ)))
        assertTrue(WorkflowValidator.validate(graph).isEmpty())
        val result = WorkflowSimulator.simulate(graph, ExecutionContext.simulated(monday))
        assertEquals(RunStatus.BLOCKED, result.status)
        assertEquals(listOf("wait"), result.events.filter { it.status == ActionStatus.SIMULATED }.map { it.nodeId })
        assertTrue(result.events.any { it.nodeId == "timer" && it.status == ActionStatus.BLOCKED })
        assertFalse(result.events.any { it.nodeId == "after" })
    }

    @Test fun parallelLocksCrossActionResourceConflictsButAllowsIndependentReads() {
        val conflicts = listOf(
            ActionSpec(ActionType.NAVIGATE, text = "Destination", latitude = 37.0, longitude = 127.0) to ActionSpec(ActionType.CANCEL_NAVIGATION),
            ActionSpec(ActionType.PLAY_LOCAL_AUDIO, targetId = "rest-tone") to ActionSpec(ActionType.STOP_LOCAL_AUDIO),
            ActionSpec(ActionType.PLAY_LOCAL_AUDIO, targetId = "rest-tone") to ActionSpec(ActionType.MEDIA_CONTROL, targetId = "selected-media"),
            ActionSpec(ActionType.MOVE_SEAT_LIMITED, area = "ROW_1_LEFT", axis = SeatAxis.FORE_AFT, direction = MoveDirection.FORWARD) to
                ActionSpec(ActionType.MOVE_SEAT_LIMITED, area = "ROW_1_RIGHT", axis = SeatAxis.BACKREST, direction = MoveDirection.BACKWARD),
            ActionSpec(ActionType.HA_CALL, targetId = "light.same") to ActionSpec(ActionType.HA_CALL, targetId = "light.same"))
        for ((first, second) in conflicts) assertTrue("${first.type}/${second.type}",
            WorkflowValidator.validate(parallel(first, second)).any { it.nodeId == "parallel" })
        assertTrue(WorkflowValidator.validate(parallel(ActionSpec(ActionType.CALENDAR_READ), ActionSpec(ActionType.CALENDAR_READ))).isEmpty())
        assertTrue(WorkflowValidator.validate(parallel(ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS, value = 30.0),
            ActionSpec(ActionType.SET_AMBIENT_COLOR, text = "#FF123456"))).isEmpty())
        assertTrue(WorkflowValidator.validate(parallel(ActionSpec(ActionType.HA_CALL, targetId = "light.one"),
            ActionSpec(ActionType.HA_CALL, targetId = "light.two"))).isEmpty())
    }

    @Test fun graphInsertionAndRemovalPreserveConditionalPortsAndOtherPaths() {
        val base = WorkflowExamples.blank("edit")
        val branched = GraphEditing.insert(base, base.edges.single().id, NodeKind.IF)
        val trueEdge = branched.edges.single { it.port == "true" }
        val falseEdge = branched.edges.single { it.port == "false" }
        val inserted = GraphEditing.insert(branched, trueEdge.id, NodeKind.TIMER)
        val timer = inserted.nodes.single { it.kind == NodeKind.TIMER }
        assertTrue(WorkflowValidator.validate(inserted).isEmpty())
        assertEquals(falseEdge, inserted.edges.single { it.id == falseEdge.id })
        assertEquals(timer.id, inserted.edges.single { it.id == trueEdge.id }.target)
        assertEquals("true", inserted.edges.single { it.id == trueEdge.id }.port)
        val removed = GraphEditing.remove(inserted, timer.id)
        assertTrue(WorkflowValidator.validate(removed).isEmpty())
        assertEquals(WorkflowJson.semanticHash(branched), WorkflowJson.semanticHash(removed))
        assertEquals(branched.revision + 2, removed.revision)
        assertThrows(IllegalArgumentException::class.java) { GraphEditing.remove(branched, trueEdge.source) }
    }

    @Test fun patchCanReplaceAnEntireConnectionAtomicallyAndRemoveAttachedEdges() {
        val original = WorkflowExamples.blank("patch")
        val action = WorkflowNode("action", NodeKind.ACTION, "Read calendar", action = ActionSpec(ActionType.CALENDAR_READ))
        val revised = WorkflowEdits.applyPatch(original, WorkflowPatch(1, listOf(
            PatchOperation(PatchType.DISCONNECT, edgeId = "start-end"),
            PatchOperation(PatchType.ADD_NODE, node = action),
            PatchOperation(PatchType.CONNECT, edge = WorkflowEdge("start-action", "start", "action")),
            PatchOperation(PatchType.CONNECT, edge = WorkflowEdge("action-end", "action", "end")),
            PatchOperation(PatchType.REPLACE_NODE, nodeId = "action", node = action.copy(action = ActionSpec(ActionType.PLACE_SEARCH, text = "Station"))),
            PatchOperation(PatchType.CHANGE_TITLE, title = "Find station"),
            PatchOperation(PatchType.CHANGE_TRIGGER, trigger = TriggerDefinition(TriggerType.VOICE)))))
        assertEquals(2, revised.revision)
        assertEquals("Find station", revised.title)
        assertEquals(TriggerType.VOICE, revised.trigger.type)
        assertEquals(ActionType.PLACE_SEARCH, revised.nodes.single { it.id == "action" }.action!!.type)
        val removed = WorkflowEdits.applyPatch(revised, WorkflowPatch(2, listOf(
            PatchOperation(PatchType.REMOVE_NODE, nodeId = "action"),
            PatchOperation(PatchType.CONNECT, edge = WorkflowEdge("restored", "start", "end")))))
        assertEquals(3, removed.revision)
        assertEquals(listOf("start", "end"), removed.nodes.map { it.id })
        assertEquals(listOf(WorkflowEdge("restored", "start", "end")), removed.edges)
        assertEquals(listOf("start", "end"), original.nodes.map { it.id })
    }

    @Test fun invalidFinalPatchAndLateOperationFailureLeaveOriginalAndHistoryUntouched() {
        val original = WorkflowExamples.blank("rollback")
        val history = WorkflowHistory(original)
        val invalidPatches = listOf(
            listOf(PatchOperation(PatchType.CHANGE_TITLE, title = "Partial"), PatchOperation(PatchType.DISCONNECT, edgeId = "start-end")),
            listOf(PatchOperation(PatchType.CHANGE_TITLE, title = "Partial"), PatchOperation(PatchType.REMOVE_NODE, nodeId = "missing")))
        for (operations in invalidPatches) {
            assertThrows(IllegalArgumentException::class.java) {
                history.replace(WorkflowEdits.applyPatch(history.current, WorkflowPatch(1, operations)))
            }
            assertEquals(original, history.current)
            assertFalse(history.canUndo)
            assertFalse(history.canRedo)
            assertEquals("새 루틴", original.title)
            assertEquals(listOf(WorkflowEdge("start-end", "start", "end")), original.edges)
        }
    }

    @Test fun patchRejectsDuplicateIdentityMismatchedReplacementAndOperationCountOverflow() {
        val original = WorkflowExamples.blank("identity")
        val start = original.nodes.single { it.id == "start" }
        val badOperations = listOf(
            listOf(PatchOperation(PatchType.ADD_NODE, node = start)),
            listOf(PatchOperation(PatchType.CONNECT, edge = original.edges.single())),
            listOf(PatchOperation(PatchType.REPLACE_NODE, nodeId = "end", node = start)),
            emptyList(), List(129) { PatchOperation(PatchType.CHANGE_TITLE, title = "Too many") })
        for (operations in badOperations) assertThrows(IllegalArgumentException::class.java) {
            WorkflowEdits.applyPatch(original, WorkflowPatch(1, operations))
        }
        val atLimit = WorkflowEdits.applyPatch(original, WorkflowPatch(1,
            List(128) { PatchOperation(PatchType.CHANGE_TITLE, title = "Accepted") }))
        assertEquals(2, atLimit.revision)
        assertEquals("Accepted", atLimit.title)
    }

    @Test fun editingAfterUndoDiscardsRedoWithoutChangingSavedSnapshots() {
        val original = WorkflowExamples.blank("history")
        val first = original.copy(title = "First", revision = 2)
        val second = original.copy(title = "Second", revision = 3)
        val replacement = original.copy(title = "Different branch", revision = 3)
        val history = WorkflowHistory(original)
        history.replace(first)
        history.replace(second)
        assertEquals(first, history.undo())
        assertTrue(history.canRedo)
        history.replace(replacement)
        assertFalse(history.canRedo)
        assertEquals(replacement, history.redo())
        assertEquals(first, history.undo())
        assertEquals(original, history.undo())
        assertEquals(original, history.undo())
        assertEquals(first, history.redo())
        assertEquals(replacement, history.redo())
        assertEquals("Second", second.title)
    }

    @Test fun cloningAndSerializationOrderKeepSemanticHashStable() {
        val original = WorkflowExamples.rest()
        val reordered = original.copy(nodes = original.nodes.reversed(),
            edges = original.edges.reversed().mapIndexed { index, edge -> edge.copy(id = "new$index") })
        assertEquals(WorkflowJson.semanticHash(original), WorkflowJson.semanticHash(reordered))
        val clone = WorkflowEdits.clone(reordered.copy(revision = 7), "rest-copy")
        assertEquals("rest-copy", clone.id)
        assertEquals(1, clone.revision)
        assertEquals(WorkflowJson.semanticHash(original), WorkflowJson.semanticHash(clone))
    }

    @Test fun actionTimingTriggerAndRoutingChangesInvalidateSimulationHash() {
        val original = WorkflowExamples.rest()
        val changes = listOf(
            original.copy(nodes = original.nodes.map { if (it.id == "action0") it.copy(action = it.action!!.copy(value = 24.0)) else it }),
            original.copy(nodes = original.nodes.map { if (it.id == "action3") it.copy(action = it.action!!.copy(direction = MoveDirection.FORWARD)) else it }),
            original.copy(nodes = original.nodes.map { if (it.id == "timer") it.copy(durationSeconds = 1_200) else it }),
            original.copy(trigger = TriggerDefinition(TriggerType.VOICE)),
            original.copy(trigger = original.trigger.copy(filters = listOf(ConditionSpec(ConditionField.WEEKDAY, weekdays = listOf(1))))))
        for (changed in changes) assertNotEquals(WorkflowJson.semanticHash(original), WorkflowJson.semanticHash(changed))
        val commute = WorkflowExamples.commute()
        val rerouted = commute.copy(edges = commute.edges.map {
            if (it.source == "cold") it.copy(port = if (it.port == "true") "false" else "true") else it
        })
        assertNotEquals(WorkflowJson.semanticHash(commute), WorkflowJson.semanticHash(rerouted))
    }
}
