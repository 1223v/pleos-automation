package com.pleos.routine.data

import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The app schema opens a control request for review; it grants no authority to execute it. */
class GleoReviews(
    private val read: () -> PendingRoutineRequest?,
    private val store: (PendingRoutineRequest) -> Boolean,
    private val clear: (String) -> Boolean,
    private val reply: (String, String, String) -> Unit,
) {
    private data class Control(val id: String, val routineId: String, val operation: String)
    private var control: Control? = null

    @Synchronized fun retain(request: PendingRoutineRequest): Boolean {
        require(request.isValid())
        val previous = read()
        if (!store(request)) return false
        if (previous != null && previous.id != request.id) {
            reply(previous.action, "fail", "A newer request replaced the pending draft.")
        }
        discardControl()
        return true
    }

    @Synchronized fun dismiss(id: String): Boolean {
        val request = read()?.takeIf { it.id == id } ?: return false
        if (!clear(id)) return false
        reply(request.action, "fail", "The user dismissed the pending routine request.")
        return true
    }

    /** Called only after the confirmed save transaction has committed. */
    @Synchronized fun saved(id: String, routineId: String): Boolean {
        val request = read()?.takeIf { it.id == id } ?: return false
        if (routineId.isBlank() || (request.action == "EDIT_ROUTINE" && request.routineId != routineId)) return false
        if (!clear(id)) return false
        reply(request.action, "success", "The user saved the reviewed routine. No vehicle command was sent.")
        return true
    }

    @Synchronized fun openControl(id: String, routineId: String, operation: String) {
        require(id.isNotBlank() && routineId.isNotBlank() && operation in controlOperations)
        discardControl()
        control = Control(id, routineId, operation)
    }

    @Synchronized fun matchesControl(id: String, routineId: String?, operation: String?): Boolean {
        val current = control ?: return false
        return current.id == id && current.routineId == routineId && current.operation == operation
    }

    @Synchronized fun reviewControl(id: String, routineId: String?, operation: String?, accepted: Boolean, targetExists: Boolean): Boolean {
        if (!matchesControl(id, routineId, operation)) return false
        control = null
        when {
            !accepted -> reply("CONTROL_ROUTINE", "fail", "The user declined the control request.")
            !targetExists -> reply("CONTROL_ROUTINE", "fail", "The requested routine is unavailable.")
            else -> reply("CONTROL_ROUTINE", "success", "The user reviewed the control request. No vehicle command was sent.")
        }
        return true
    }

    private fun discardControl() {
        if (control != null) {
            control = null
            reply("CONTROL_ROUTINE", "fail", "A newer request replaced the control review.")
        }
    }

    companion object {
        val controlOperations = setOf("start", "stop", "enable", "disable")
    }
}

internal fun Context.replyToGleo(action: String, status: String, message: String) {
    val response = buildJsonObject {
        put("functionName", action)
        put("status", status)
        put("message", message)
        put("mode", "voice")
    }
    sendBroadcast(Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
        .putExtra("packageName", packageName).putExtra("response", response.toString()))
}
