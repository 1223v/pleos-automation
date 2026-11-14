package com.pleos.routine.core

import kotlinx.serialization.Serializable
import kotlin.math.abs

enum class InputSource { REAL, SIMULATION, UNKNOWN }
data class StateValue<T>(
    val value: T? = null,
    val observedAtMillis: Long = 0,
    val valid: Boolean = false,
    val source: InputSource = InputSource.UNKNOWN,
    val sourceTimestampNanos: Long? = null,
) {
    fun isUsable(nowMillis: Long, maxAgeMillis: Long, expectedSource: InputSource): Boolean =
        valid && value != null && source == expectedSource && observedAtMillis <= nowMillis && nowMillis - observedAtMillis <= maxAgeMillis
}

/** monotonicMillis and observedAtMillis share one monotonic clock; epochMillis is only for calendar rules. */
data class ExecutionContext(
    val epochMillis: Long,
    val monotonicMillis: Long,
    val source: InputSource = InputSource.REAL,
    val gear: StateValue<String> = StateValue(),
    val speedMps: StateValue<Double> = StateValue(),
    val outsideTempC: StateValue<Double> = StateValue(),
    val seatOccupied: StateValue<Boolean> = StateValue(),
    val uxRestricted: Boolean = true,
    val maxStateAgeMillis: Long = 2_000,
) {
    fun parkedAndStationary(): Boolean =
        !uxRestricted && gear.isUsable(monotonicMillis, maxStateAgeMillis, source) && gear.value == "P" &&
            speedMps.isUsable(monotonicMillis, maxStateAgeMillis, source) &&
            speedMps.value?.let { it.isFinite() && abs(it) == 0.0 } == true

    companion object {
        fun simulated(epochMillis: Long, outsideTempC: Double? = 3.0, gear: String = "P", speedMps: Double = 0.0, occupied: Boolean = true): ExecutionContext =
            ExecutionContext(epochMillis, 0L, InputSource.SIMULATION,
                StateValue(gear, 0, true, InputSource.SIMULATION),
                StateValue(speedMps, 0, true, InputSource.SIMULATION),
                StateValue(outsideTempC, 0, outsideTempC != null, InputSource.SIMULATION),
                StateValue(occupied, 0, true, InputSource.SIMULATION),
                uxRestricted = gear != "P" || speedMps != 0.0)
    }
}

@Serializable enum class ActionStatus { PENDING, REQUESTED, ACKNOWLEDGED, VERIFIED, SIMULATED, SKIPPED, BLOCKED, FAILED, UNKNOWN, CANCELLED }
@Serializable data class ActionResult(val status: ActionStatus, val message: String, val observedValue: String? = null)
data class ActionProgress(val status: ActionStatus, val message: String)
/** Presentation only; it is not a command or a durable journal event. */
data class TimerKey(val runId:String,val nodeId:String,val iterations:List<Int>,val branches:List<String>)
data class TimerProgress(val runId:String,val nodeId:String,val label:String,val remainingMillis:Long,val completed:Boolean,
    val iterations:List<Int> = emptyList(),val branches:List<String> = emptyList()) {
    val key get()=TimerKey(runId,nodeId,iterations,branches)
}
data class ActionRequest(
    val runId: String,
    val nodeId: String,
    val action: ActionSpec,
    val context: ExecutionContext,
    val iterations: List<Int> = emptyList(),
    val branches: List<String> = emptyList(),
)

/** Approval covers one immutable run; adapters still recheck live safety before each physical command. */
interface ActionAdapter {
    /** Failure of a still-owned resource after its initial start was verified. */
    fun activeFailure():ActiveActionFailure? = null
    suspend fun authorizeRun(workflow: WorkflowDefinition, runId: String, context: ExecutionContext): ActionResult? = null
    /**
     * null means eligible. SKIPPED means this one step is left unexecuted on purpose, with no command sent,
     * and the run continues. Any other result stops the run before any side effect of this step.
     */
    suspend fun preflight(action: ActionSpec, context: ExecutionContext): ActionResult? = null
    suspend fun execute(request: ActionRequest, onProgress: suspend (ActionProgress) -> Unit): ActionResult
    /** Must not share the normal move queue or movement gates; best-effort STOP and owned-resource cleanup. */
    suspend fun stop(runId: String): List<ActionResult>
}
data class ActiveActionFailure(val nodeId:String,val message:String)

@Serializable data class ExecutionEvent(
    val runId: String,
    val nodeId: String?,
    val status: ActionStatus,
    val message: String,
    val elapsedMillis: Long,
    val iterations: List<Int> = emptyList(),
    val branches: List<String> = emptyList(),
    val sequence: Long = 0,
    val observedValue: String? = null,
)
@Serializable enum class RunStatus { COMPLETED, BLOCKED, FAILED, UNKNOWN, CANCELLED }
@Serializable data class RunResult(
    val runId: String,
    val workflowId: String,
    val revision: Int,
    val semanticHash: String,
    val simulation: Boolean,
    val status: RunStatus,
    val events: List<ExecutionEvent>,
    val elapsedMillis: Long,
) {
    val actionEvents get() = events.filter { it.status in setOf(ActionStatus.VERIFIED, ActionStatus.SIMULATED, ActionStatus.FAILED, ActionStatus.UNKNOWN, ActionStatus.BLOCKED) && it.nodeId != null }
}

object ActionResources {
    val parkedTypes = setOf(ActionType.SET_HVAC_TARGET, ActionType.SET_AMBIENT_BRIGHTNESS,
        ActionType.SET_AMBIENT_COLOR, ActionType.SET_SEAT_HEAT, ActionType.MOVE_SEAT_LIMITED)
    fun key(action: ActionSpec): String = when (action.type) {
        ActionType.NAVIGATE, ActionType.CANCEL_NAVIGATION -> "navigation"
        // Grouped SDK areas can overlap individual seats. Conservatively lock each subsystem.
        ActionType.SET_HVAC_TARGET -> "hvac"
        ActionType.SET_AMBIENT_BRIGHTNESS -> "ambient-brightness"
        ActionType.SET_AMBIENT_COLOR -> "ambient-color"
        ActionType.SET_SEAT_HEAT -> "seat-heat"
        ActionType.MOVE_SEAT_LIMITED -> "seat-motion"
        ActionType.PLAY_LOCAL_AUDIO, ActionType.STOP_LOCAL_AUDIO, ActionType.MEDIA_CONTROL -> "media"
        ActionType.OPEN_APP -> "foreground-app"
        ActionType.HA_CALL -> "home:${action.targetId}"
        ActionType.CALENDAR_READ, ActionType.PLACE_SEARCH, ActionType.WHAT3WORDS_RESOLVE -> "read:${action.type}:${action.targetId.orEmpty()}"
    }
    fun isWrite(action: ActionSpec) = action.type !in setOf(ActionType.CALENDAR_READ, ActionType.PLACE_SEARCH, ActionType.WHAT3WORDS_RESOLVE)
}
