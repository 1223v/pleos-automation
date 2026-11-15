package com.pleos.routine.ui

import androidx.annotation.DrawableRes
import com.pleos.routine.R
import com.pleos.routine.core.*
import com.pleos.routine.ui.theme.Hue

/**
 * How a node looks on the canvas: icon, Astryx palette family and the short Korean words drivers read.
 * The same table drives the application figures (design/flow-visuals.mjs); keep both in step.
 */
data class NodeVisual(@DrawableRes val icon: Int, val hue: Hue, val kindLabel: String, val subtitle: String, val badge: String? = null)

object NodeVisuals {
    fun of(n: WorkflowNode): NodeVisual = when (n.kind) {
        NodeKind.START -> NodeVisual(R.drawable.ic_lucide_zap, Hue.ORANGE, "시작", "시작")
        NodeKind.IF -> NodeVisual(R.drawable.ic_lucide_split, Hue.YELLOW, "조건", n.condition?.let { conditionLabel(it.field) } ?: "조건")
        NodeKind.WAIT -> NodeVisual(R.drawable.ic_lucide_hourglass, Hue.GRAY, "대기", "${duration(n.durationSeconds)} 대기")
        NodeKind.TIMER -> NodeVisual(R.drawable.ic_lucide_timer, Hue.GRAY, "타이머", "${duration(n.durationSeconds)} 타이머")
        NodeKind.REPEAT -> NodeVisual(R.drawable.ic_lucide_repeat, Hue.YELLOW, "반복", "${n.repeatCount ?: "?"}회 반복")
        NodeKind.PARALLEL -> NodeVisual(R.drawable.ic_lucide_git_fork, Hue.YELLOW, "병렬", "동시에 실행")
        NodeKind.JOIN -> NodeVisual(R.drawable.ic_lucide_git_merge, Hue.YELLOW, "합류", "분기 합류")
        NodeKind.END -> NodeVisual(R.drawable.ic_lucide_flag, Hue.GRAY, "종료", "종료")
        NodeKind.ACTION -> n.action?.let { action(it.type) } ?: NodeVisual(R.drawable.ic_lucide_blocks, Hue.GRAY, "동작", "동작 미설정")
    }

    fun action(type: ActionType): NodeVisual {
        val (icon, hue) = when (type) {
            ActionType.NAVIGATE -> R.drawable.ic_lucide_navigation to Hue.GREEN
            ActionType.CANCEL_NAVIGATION -> R.drawable.ic_lucide_navigation_off to Hue.GREEN
            ActionType.PLACE_SEARCH -> R.drawable.ic_lucide_map_pin to Hue.GREEN
            ActionType.WHAT3WORDS_RESOLVE -> R.drawable.ic_lucide_map_pinned to Hue.GREEN
            ActionType.SET_HVAC_TARGET -> R.drawable.ic_lucide_thermometer to Hue.BLUE
            ActionType.SET_AMBIENT_BRIGHTNESS -> R.drawable.ic_lucide_lightbulb to Hue.PURPLE
            ActionType.SET_AMBIENT_COLOR -> R.drawable.ic_lucide_palette to Hue.PURPLE
            ActionType.SET_SEAT_HEAT -> R.drawable.ic_lucide_flame to Hue.TEAL
            ActionType.MOVE_SEAT_LIMITED -> R.drawable.ic_lucide_armchair to Hue.TEAL
            ActionType.PLAY_LOCAL_AUDIO -> R.drawable.ic_lucide_music to Hue.PINK
            ActionType.STOP_LOCAL_AUDIO -> R.drawable.ic_lucide_volume_x to Hue.PINK
            ActionType.MEDIA_CONTROL -> R.drawable.ic_lucide_circle_play to Hue.PINK
            ActionType.OPEN_APP -> R.drawable.ic_lucide_app_window to Hue.CYAN
            ActionType.CALENDAR_READ -> R.drawable.ic_lucide_calendar to Hue.CYAN
            ActionType.HA_CALL -> R.drawable.ic_lucide_house to Hue.CYAN
        }
        val kind = when (hue) { Hue.GREEN -> "길 안내"; Hue.BLUE -> "공조"; Hue.PURPLE -> "조명"; Hue.TEAL -> "시트"; Hue.PINK -> "음원·미디어"; else -> "앱·서비스" }
        val badge = if (type == ActionType.MOVE_SEAT_LIMITED) "실행마다 확인" else null
        return NodeVisual(icon, hue, kind, actionName(type).substringBefore(" (").trim(), badge)
    }

    fun conditionLabel(f: ConditionField) = when (f) {
        ConditionField.OUTSIDE_TEMPERATURE -> "외기 온도"; ConditionField.GEAR -> "기어"; ConditionField.SPEED -> "속도"
        ConditionField.SEAT_OCCUPIED -> "좌석 점유"; ConditionField.TIME_WINDOW -> "시간 구간"; ConditionField.WEEKDAY -> "요일"
    }

    @DrawableRes fun triggerIcon(t: TriggerType): Int = when (t) {
        TriggerType.MANUAL -> R.drawable.ic_lucide_hand; TriggerType.VOICE -> R.drawable.ic_lucide_mic
        TriggerType.SEAT_OCCUPIED -> R.drawable.ic_lucide_armchair; TriggerType.TIME -> R.drawable.ic_lucide_clock
        TriggerType.CONDITION_ENTER -> R.drawable.ic_lucide_activity
    }

    fun triggerLabel(t: TriggerType) = when (t) {
        TriggerType.MANUAL -> "직접 실행"; TriggerType.VOICE -> "음성 요청"; TriggerType.SEAT_OCCUPIED -> "탑승할 때"
        TriggerType.TIME -> "정해진 시간"; TriggerType.CONDITION_ENTER -> "조건 충족 시"
    }

    /** Output handles in drawing order, matching the ports Validation.kt requires. */
    fun outputPorts(kind: NodeKind, used: List<String>): List<String> = when (kind) {
        NodeKind.END -> emptyList()
        NodeKind.IF -> listOf("true", "false")
        NodeKind.REPEAT -> listOf("body", "next")
        NodeKind.PARALLEL -> used.filter { it.startsWith("branch:") }.distinct().sortedBy { it.removePrefix("branch:").toIntOrNull() ?: 99 }
            .ifEmpty { listOf("branch:0", "branch:1") }
        else -> listOf("next")
    }

    /** Words on a port; plain `next` after a single-exit node needs none. */
    fun portLabel(kind: NodeKind, port: String): String? = when {
        port == "true" -> "충족"
        port == "false" -> "미충족"
        port == "body" -> "반복"
        port == "next" && kind == NodeKind.REPEAT -> "다음"
        port.startsWith("branch:") -> "분기 ${(port.removePrefix("branch:").toIntOrNull() ?: 0) + 1}"
        else -> null
    }

    fun duration(seconds: Long?): String {
        if (seconds == null) return "미설정"
        val h = seconds / 3600; val m = seconds % 3600 / 60; val s = seconds % 60
        return listOfNotNull(h.takeIf { it > 0 }?.let { "${it}시간" }, m.takeIf { it > 0 }?.let { "${it}분" },
            s.takeIf { it > 0 || seconds == 0L }?.let { "${it}초" }).joinToString(" ")
    }
}
