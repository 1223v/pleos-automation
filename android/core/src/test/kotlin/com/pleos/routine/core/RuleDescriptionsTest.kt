package com.pleos.routine.core

import org.junit.Assert.*
import org.junit.Test

class RuleDescriptionsTest {
    @Test fun triggerOnlyEditShowsBothBoundariesAndPreservesNavigation() {
        val original=WorkflowExamples.commute()
        val revised=original.copy(trigger=original.trigger.copy(filters=original.trigger.filters.map { if(it.field==ConditionField.TIME_WINDOW)it.copy(endMinute=480) else it }))
        val summary=RuleDescriptions.changes(original,revised).joinToString("\n")
        assertTrue(summary.contains("09:00 미만"))
        assertTrue(summary.contains("08:00 미만"))
        assertTrue(summary.contains("시작 조건 변경"))
        assertFalse(summary.contains("노드 변경"))
    }
    @Test fun reroutingSameNodesIsIncludedInHumanReview() {
        val original=WorkflowExamples.commute()
        val branch=original.nodes.single { it.kind==NodeKind.IF }
        val edges=original.edges.filter { it.source==branch.id }
        val revised=original.copy(edges=original.edges.map { if(it.source==branch.id)it.copy(port=if(it.port=="true")"false" else "true") else it })
        assertEquals(2,edges.size)
        val summary=RuleDescriptions.changes(original,revised).joinToString("\n")
        assertTrue(summary.contains("연결 삭제:"))
        assertTrue(summary.contains("연결 추가:"))
        assertTrue(summary.contains("조건 충족"))
        assertTrue(summary.contains("조건 미충족"))
    }
    @Test fun seatIdentityAndConditionalHeatAreNeverImplied() {
        val w=WorkflowExamples.commute()
        val heat=w.nodes.single { it.action?.type==ActionType.SET_SEAT_HEAT }
        val branch=w.nodes.single { it.kind==NodeKind.IF }
        assertTrue(RuleDescriptions.node(branch).contains("≤ 5°C"))
        assertTrue(RuleDescriptions.node(heat).contains("열선 2단계"))
        assertTrue(RuleDescriptions.condition(ConditionSpec(ConditionField.SEAT_OCCUPIED,Comparison.NE,flag=true)).contains("≠ 탑승 (신원 확인 아님)"))
        assertTrue(RuleDescriptions.changes(w,w.copy(nodes=w.nodes.map { it.copy(position=NodePosition(15f,15f)) })).single().contains("실행 규칙 변경 없음"))
    }
}
