package com.pleos.routine.vehicle

import com.pleos.routine.core.ActionType
import com.pleos.routine.core.ActionSpec
import com.pleos.routine.core.NodeKind
import com.pleos.routine.core.SeatAxis
import com.pleos.routine.core.WorkflowDefinition
import com.pleos.routine.core.WorkflowNode
import org.junit.Assert.*
import org.junit.Test

class EditableVehicleAreasTest {
    @Test fun groupedLightAreaIsOneWritableChoiceWithItsExactSdkId() {
        val grouped="ROW_1_LEFT+ROW_1_RIGHT+ROW_2_CENTER"
        val cap=Capability(ActionType.SET_AMBIENT_BRIGHTNESS,"READ_WRITE",listOf(grouped,grouped))
        val options=editableVehicleAreas(cap)
        assertEquals(listOf(grouped),options.map { it.id })
        assertEquals("앞줄 왼쪽 · 앞줄 오른쪽 · 2열 가운데",options.single().label)
    }

    @Test fun unknownAndReadOnlyCapabilitiesCannotSupplySelectableAreas() {
        for(access in listOf("UNKNOWN","READ","WRITE","NONE")) {
            assertTrue(editableVehicleAreas(Capability(ActionType.MOVE_SEAT_LIMITED,access,listOf("ROW_1_LEFT"))).isEmpty())
        }
        assertTrue(editableVehicleAreas(null).isEmpty())
    }

    @Test fun rawUnrecognizedAreasArePreservedWithoutAssumingADriverSeat() {
        assertEquals("앞줄 왼쪽",vehicleAreaLabel("ROW_1_LEFT"))
        assertEquals("3열 오른쪽",vehicleAreaLabel("ROW_3_RIGHT"))
        assertEquals("FUTURE_AREA",vehicleAreaLabel("FUTURE_AREA"))
    }

    @Test fun backrestAndForeAftUseTheirOwnCapabilityAndNeverOfferTheLegacyAlias() {
        val type=ActionType.MOVE_SEAT_LIMITED
        val capabilities=listOf(
            Capability(type,"READ_WRITE",listOf("ROW_1_LEFT"),seatAxis=SeatAxis.FORE_AFT),
            Capability(type,"READ_WRITE",listOf("DRIVER","ROW_1_RIGHT"),seatAxis=SeatAxis.BACKREST),
        )
        val legacy=ActionSpec(type,area="DRIVER",axis=SeatAxis.BACKREST)
        assertEquals(listOf("ROW_1_RIGHT"),editableVehicleAreas(legacy,capabilities).map { it.id })
        assertEquals(listOf("ROW_1_LEFT"),editableVehicleAreas(legacy.copy(axis=SeatAxis.FORE_AFT),capabilities).map { it.id })
        assertTrue(editableVehicleAreas(legacy,capabilities.take(1)).isEmpty())
        assertEquals("DRIVER",legacy.area)
    }

    private fun routine(vararg actions:ActionSpec)=WorkflowDefinition(id="r",title="r",
        nodes=listOf(WorkflowNode("start",NodeKind.START,"시작"))+actions.mapIndexed { i,a -> WorkflowNode("a$i",NodeKind.ACTION,"동작$i",action=a) }+WorkflowNode("end",NodeKind.END,"완료"),edges=emptyList())

    @Test fun reviewFlagsTheDriverAliasEvenBeforeCapabilitiesAreKnown() {
        val issues=vehicleAreaIssues(routine(ActionSpec(ActionType.SET_HVAC_TARGET,value=23.0)),emptyList())
        assertEquals(listOf("a0"),issues.map { it.nodeId })
    }

    @Test fun reviewFlagsAnAreaTheVehicleDidNotReportAndAcceptsReportedOnes() {
        val cabin="ROW_1_LEFT+ROW_1_RIGHT"
        val caps=listOf(Capability(ActionType.SET_HVAC_TARGET,"READ_WRITE",listOf("ROW_1_LEFT","ROW_1_RIGHT")),
            Capability(ActionType.SET_AMBIENT_BRIGHTNESS,"READ_WRITE",listOf(cabin)))
        val w=routine(ActionSpec(ActionType.SET_HVAC_TARGET,area="ROW_1_LEFT",value=23.0),
            ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS,area="ROW_1_LEFT",value=30.0),
            ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS,area=cabin,value=30.0),
            ActionSpec(ActionType.PLAY_LOCAL_AUDIO,targetId="rest-tone"))
        assertEquals(listOf("a1"),vehicleAreaIssues(w,caps).map { it.nodeId })
    }

    @Test fun unreportedOrReadOnlyCapabilitiesLeaveTheDecisionToPreflight() {
        val w=routine(ActionSpec(ActionType.SET_HVAC_TARGET,area="ROW_1_LEFT",value=23.0))
        assertTrue(vehicleAreaIssues(w,emptyList()).isEmpty())
        assertTrue(vehicleAreaIssues(w,listOf(Capability(ActionType.SET_HVAC_TARGET,"READ",listOf("ROW_1_RIGHT")))).isEmpty())
    }
}
