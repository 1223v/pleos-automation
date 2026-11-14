package com.pleos.routine.vehicle

import com.pleos.routine.core.ActionSpec
import com.pleos.routine.core.ActionType

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

fun editableVehicleAreas(action: ActionSpec, capabilities: List<Capability>): List<VehicleAreaOption> =
    editableVehicleAreas(capabilities.firstOrNull {
        it.type == action.type && (action.type != ActionType.MOVE_SEAT_LIMITED || action.axis == it.seatAxis)
    })
