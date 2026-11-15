package com.pleos.routine.vehicle

import com.pleos.routine.core.ActionResources
import com.pleos.routine.core.ActionSpec
import com.pleos.routine.core.ActionType
import com.pleos.routine.core.ValidationIssue
import com.pleos.routine.core.WorkflowDefinition

data class VehicleAreaOption(val id: String, val label: String)

fun vehicleAreaLabel(id: String): String = id.split('+').joinToString(" · ") { raw ->
    val parts = raw.split('_')
    val row = when (parts.getOrNull(1)) {
        "1" -> "앞줄"
        "2" -> "2열"
        "3" -> "3열"
        else -> null
    }
    val side = when (parts.getOrNull(2)) {
        "LEFT" -> "왼쪽"
        "CENTER" -> "가운데"
        "RIGHT" -> "오른쪽"
        else -> null
    }
    if (parts.size == 3 && parts.first() == "ROW" && row != null && side != null) "$row $side" else raw
}

fun editableVehicleAreas(capability: Capability?): List<VehicleAreaOption> {
    if (capability?.access != "READ_WRITE") return emptyList()
    return capability.areas.filter { it.isNotBlank() && it != "DRIVER" }.distinct().map { VehicleAreaOption(it, vehicleAreaLabel(it)) }
}

private fun matchingCapability(action: ActionSpec, capabilities: List<Capability>): Capability? = capabilities.firstOrNull {
    it.type == action.type && (action.type != ActionType.MOVE_SEAT_LIMITED || action.axis == it.seatAxis)
}

fun editableVehicleAreas(action: ActionSpec, capabilities: List<Capability>): List<VehicleAreaOption> =
    editableVehicleAreas(matchingCapability(action, capabilities))

/**
 * Review-time counterpart of VehiclePlatform.preflight's area check, so an unexecutable area is
 * reported before saving instead of as a BLOCKED run. "DRIVER" is never an SDK SeatArea. Other
 * areas are judged only against a READ_WRITE capability the vehicle actually reported; otherwise
 * the decision stays with preflight.
 */
fun vehicleAreaIssues(w: WorkflowDefinition, capabilities: List<Capability>): List<ValidationIssue> = w.nodes.mapNotNull { node ->
    val action = node.action?.takeIf { it.type in ActionResources.parkedTypes } ?: return@mapNotNull null
    if (action.area == "DRIVER") return@mapNotNull ValidationIssue(node.id, "운전석 별칭 대신 차량이 알려준 좌석 영역을 선택하세요.")
    val capability = matchingCapability(action, capabilities)?.takeIf { it.access == "READ_WRITE" } ?: return@mapNotNull null
    if (editableVehicleAreas(capability).none { it.id == action.area })
        ValidationIssue(node.id, "차량이 지원하지 않는 영역입니다(${vehicleAreaLabel(action.area)}). 영역을 다시 선택하세요.")
    else null
}
