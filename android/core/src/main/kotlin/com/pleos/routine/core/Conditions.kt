package com.pleos.routine.core

import java.time.Instant
import java.time.ZoneId

enum class Truth { TRUE, FALSE, UNKNOWN }
data class ConditionEvaluation(val truth: Truth, val reason: String)

object Conditions {
    /** Current eligibility only; observing TRUE does not establish a new trigger event. */
    fun evaluateTrigger(trigger: TriggerDefinition, x: ExecutionContext): ConditionEvaluation = when (trigger.type) {
        TriggerType.MANUAL, TriggerType.VOICE -> ConditionEvaluation(Truth.TRUE, "명시적인 실행 요청")
        TriggerType.SEAT_OCCUPIED -> evaluate(ConditionSpec(ConditionField.SEAT_OCCUPIED, Comparison.EQ, flag = true), x, trigger.zoneId)
        TriggerType.CONDITION_ENTER -> trigger.condition?.let { evaluate(it, x, trigger.zoneId) }
            ?: ConditionEvaluation(Truth.UNKNOWN, "시작 조건을 확인할 수 없습니다.")
        TriggerType.TIME -> {
            val time = Instant.ofEpochMilli(x.epochMillis).atZone(ZoneId.of(trigger.zoneId))
            val matches = time.hour * 60 + time.minute == trigger.minuteOfDay && time.dayOfWeek.value in trigger.weekdays
            ConditionEvaluation(if (matches) Truth.TRUE else Truth.FALSE,
                "${trigger.zoneId} 현재 요일 ${time.dayOfWeek.value} · ${time.hour}:${time.minute.toString().padStart(2, '0')} · 지정 시각·요일 확인")
        }
    }

    fun evaluate(c: ConditionSpec, x: ExecutionContext, zone: String = "Asia/Seoul"): ConditionEvaluation {
        val time = Instant.ofEpochMilli(x.epochMillis).atZone(ZoneId.of(zone))
        fun result(v: Boolean, reason: String) = ConditionEvaluation(if (v) Truth.TRUE else Truth.FALSE, reason)
        fun compare(v: Double, target: Double): Boolean = when (c.operator) {
            Comparison.EQ -> v == target; Comparison.NE -> v != target; Comparison.LT -> v < target
            Comparison.LTE -> v <= target; Comparison.GT -> v > target; Comparison.GTE -> v >= target
        }
        return when (c.field) {
            ConditionField.TIME_WINDOW -> {
                val minute = time.hour * 60 + time.minute
                result(minute >= (c.startMinute ?: 0) && minute < (c.endMinute ?: 0), "현재 ${time.hour}:${time.minute.toString().padStart(2, '0')} · 설정 시간 구간 확인")
            }
            ConditionField.WEEKDAY -> result(time.dayOfWeek.value in c.weekdays, "요일 ${time.dayOfWeek.value} · 선택 요일 ${c.weekdays.joinToString()}")
            ConditionField.OUTSIDE_TEMPERATURE, ConditionField.SPEED -> {
                val state = if (c.field == ConditionField.SPEED) x.speedMps else x.outsideTempC
                if (!state.isUsable(x.monotonicMillis, x.maxStateAgeMillis, x.source) || state.value?.isFinite() != true || c.value == null) ConditionEvaluation(Truth.UNKNOWN, "센서값을 확인할 수 없습니다.")
                else result(compare(state.value, c.value), "관찰값 ${state.value} ${if (c.field == ConditionField.SPEED) "m/s" else "°C"} · 기준 ${c.operator} ${c.value}")
            }
            ConditionField.GEAR -> if (!x.gear.isUsable(x.monotonicMillis, x.maxStateAgeMillis, x.source)) ConditionEvaluation(Truth.UNKNOWN, "기어 상태를 확인할 수 없습니다.")
                else result((x.gear.value == c.text) == (c.operator != Comparison.NE), "기어 ${x.gear.value} · 기준 ${c.text}")
            ConditionField.SEAT_OCCUPIED -> if (!x.seatOccupied.isUsable(x.monotonicMillis, x.maxStateAgeMillis, x.source)) ConditionEvaluation(Truth.UNKNOWN, "좌석 점유 상태를 확인할 수 없습니다.")
                else result((x.seatOccupied.value == c.flag) == (c.operator != Comparison.NE), "좌석 점유 ${x.seatOccupied.value} · 운전자 신원 확인과 별개")
        }
    }
}

/** Per-process state: restore enabled definitions, never prior transitions or commands. */
class TriggerMonitor(private val cooldownMillis: Long = 300_000) {
    private data class Memory(var previous: Truth, var lastRun: Long? = null)
    private val states = mutableMapOf<String, Memory>()
    fun reset() = states.clear()
    private fun key(w: WorkflowDefinition) = w.id + ":" + w.revision + ":" + WorkflowJson.semanticHash(w)
    fun syncActive(workflows: List<WorkflowDefinition>) {
        val active = workflows.map(::key).toSet()
        states.keys.retainAll(active)
    }
    fun markStarted(w: WorkflowDefinition, monotonicMillis: Long) { states[key(w)]?.lastRun = monotonicMillis }
    fun evaluate(w: WorkflowDefinition, x: ExecutionContext): Boolean {
        if (w.trigger.type in setOf(TriggerType.MANUAL, TriggerType.VOICE)) return false
        val base = Conditions.evaluateTrigger(w.trigger, x).truth
        val filters = w.trigger.filters.map { Conditions.evaluate(it, x, w.trigger.zoneId).truth }
        // Filters qualify an event; they must never manufacture a new boarding event.
        val values = listOf(base) + filters
        val current = if(w.trigger.type != TriggerType.CONDITION_ENTER) base else when {
            Truth.FALSE in values -> Truth.FALSE
            Truth.UNKNOWN in values -> Truth.UNKNOWN
            else -> Truth.TRUE
        }
        val key = key(w)
        val memory = states[key] ?: run { states[key] = Memory(current); return false }
        val fire = memory.previous == Truth.FALSE && current == Truth.TRUE && filters.all { it==Truth.TRUE } && (memory.lastRun == null || x.monotonicMillis - memory.lastRun!! >= cooldownMillis)
        memory.previous = current
        return fire
    }
}
