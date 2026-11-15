package com.pleos.routine.ui

import com.pleos.routine.core.*
import com.pleos.routine.ui.theme.Hue
import org.junit.Assert.*
import org.junit.Test

class NodeVisualsTest {
    private val hangul = Regex("[가-힣]")

    @Test fun everyNodeKindHasAnIconAndAKoreanLabel() {
        NodeKind.entries.forEach { kind ->
            val node = WorkflowNode("n", kind, "카드", action = if (kind == NodeKind.ACTION) ActionSpec(ActionType.SET_HVAC_TARGET, value = 23.0) else null,
                durationSeconds = 600, repeatCount = 3)
            val v = NodeVisuals.of(node)
            assertNotEquals(kind.name, 0, v.icon)
            assertTrue("$kind label ${v.kindLabel}", hangul.containsMatchIn(v.kindLabel))
            assertFalse("raw enum must not be shown for $kind", v.subtitle.contains(kind.name))
        }
    }

    @Test fun everyActionHasItsOwnIconCategoryAndNoRawEnumName() {
        ActionType.entries.forEach { type ->
            val v = NodeVisuals.of(WorkflowNode("n", NodeKind.ACTION, "카드", action = ActionSpec(type)))
            assertNotEquals(type.name, 0, v.icon)
            assertTrue("$type subtitle ${v.subtitle}", hangul.containsMatchIn(v.subtitle))
            assertFalse(v.subtitle.contains(type.name))
            assertFalse("no parenthetical notes on the canvas: ${v.subtitle}", v.subtitle.contains("("))
        }
        assertEquals(Hue.BLUE, NodeVisuals.of(WorkflowNode("a", NodeKind.ACTION, "공조", action = ActionSpec(ActionType.SET_HVAC_TARGET))).hue)
        assertEquals(Hue.PURPLE, NodeVisuals.of(WorkflowNode("b", NodeKind.ACTION, "조명", action = ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS))).hue)
        assertEquals(Hue.TEAL, NodeVisuals.of(WorkflowNode("c", NodeKind.ACTION, "시트", action = ActionSpec(ActionType.MOVE_SEAT_LIMITED))).hue)
        assertEquals(Hue.ORANGE, NodeVisuals.of(WorkflowNode("d", NodeKind.START, "시작")).hue)
        assertEquals(Hue.YELLOW, NodeVisuals.of(WorkflowNode("e", NodeKind.IF, "조건")).hue)
    }

    @Test fun actionWithoutSpecStillGetsAVisual() {
        val v = NodeVisuals.of(WorkflowNode("x", NodeKind.ACTION, "빈 동작"))
        assertNotEquals(0, v.icon)
        assertEquals("동작 미설정", v.subtitle)
    }

    @Test fun seatMovementIsMarkedForPerRunConfirmation() {
        val seat = NodeVisuals.of(WorkflowNode("s", NodeKind.ACTION, "등받이", action = ActionSpec(ActionType.MOVE_SEAT_LIMITED)))
        assertEquals("실행마다 확인", seat.badge)
        assertNull(NodeVisuals.of(WorkflowNode("l", NodeKind.ACTION, "조명", action = ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS))).badge)
    }

    @Test fun portsReadAsDriverWords() {
        assertEquals("충족", NodeVisuals.portLabel(NodeKind.IF, "true"))
        assertEquals("미충족", NodeVisuals.portLabel(NodeKind.IF, "false"))
        assertEquals("반복", NodeVisuals.portLabel(NodeKind.REPEAT, "body"))
        assertEquals("다음", NodeVisuals.portLabel(NodeKind.REPEAT, "next"))
        assertEquals("분기 1", NodeVisuals.portLabel(NodeKind.PARALLEL, "branch:0"))
        assertNull(NodeVisuals.portLabel(NodeKind.ACTION, "next"))
    }

    @Test fun outputPortsFollowValidationPortOrder() {
        assertEquals(listOf("true", "false"), NodeVisuals.outputPorts(NodeKind.IF, emptyList()))
        assertEquals(listOf("body", "next"), NodeVisuals.outputPorts(NodeKind.REPEAT, emptyList()))
        assertEquals(listOf("branch:0", "branch:1", "branch:2"), NodeVisuals.outputPorts(NodeKind.PARALLEL, listOf("branch:2", "branch:0", "branch:1")))
        assertEquals(emptyList<String>(), NodeVisuals.outputPorts(NodeKind.END, emptyList()))
        assertEquals(listOf("next"), NodeVisuals.outputPorts(NodeKind.ACTION, emptyList()))
    }

    @Test fun durationsUseMinutesAndHours() {
        assertEquals("45초", NodeVisuals.duration(45))
        assertEquals("10분", NodeVisuals.duration(600))
        assertEquals("1시간", NodeVisuals.duration(3600))
        assertEquals("1시간 5분", NodeVisuals.duration(3900))
        assertEquals("1분 30초", NodeVisuals.duration(90))
        assertEquals("미설정", NodeVisuals.duration(null))
    }

    @Test fun triggersHaveIconsAndKoreanNames() {
        TriggerType.entries.forEach { t ->
            assertNotEquals(0, NodeVisuals.triggerIcon(t))
            assertTrue(hangul.containsMatchIn(NodeVisuals.triggerLabel(t)))
        }
    }
}
