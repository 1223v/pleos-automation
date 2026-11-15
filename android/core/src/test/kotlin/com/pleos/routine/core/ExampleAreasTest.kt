package com.pleos.routine.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleAreasTest {
    private val examples = listOf(WorkflowExamples.commute(), WorkflowExamples.winterCamping(), WorkflowExamples.rest())

    /** "DRIVER" is the legacy default, not an SDK SeatArea; a vehicle step carrying it is always blocked at preflight. */
    @Test fun vehicleControlExamplesUseSdkAreasInsteadOfTheDriverAlias() {
        val aliased = examples.flatMap { w ->
            w.nodes.filter { it.action?.type in ActionResources.parkedTypes && it.action?.area == "DRIVER" }.map { "${w.id}/${it.id}" }
        }
        assertEquals(emptyList<String>(), aliased)
    }

    @Test fun restExampleTargetsTheDriverSideClimateZoneAndTheWholeCabinLighting() {
        val actions = WorkflowExamples.rest().nodes.mapNotNull { it.action }.associateBy { it.type }
        val cabin = "ROW_1_LEFT+ROW_1_RIGHT+ROW_2_LEFT+ROW_2_CENTER+ROW_2_RIGHT"
        assertEquals("ROW_1_LEFT", actions.getValue(ActionType.SET_HVAC_TARGET).area)
        assertEquals(cabin, actions.getValue(ActionType.SET_AMBIENT_BRIGHTNESS).area)
        assertEquals(cabin, actions.getValue(ActionType.SET_AMBIENT_COLOR).area)
    }
}
