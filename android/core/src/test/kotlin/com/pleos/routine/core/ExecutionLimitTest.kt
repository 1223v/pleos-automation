package com.pleos.routine.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** User confirmed 4096 visits as a pre-save/pre-test contract on 2026-10-31. */
class ExecutionLimitTest {
    private fun nested(padding: Int, counts:List<Int> = listOf(18,14,15)): WorkflowDefinition {
        val nodes=mutableListOf(WorkflowNode("start",NodeKind.START,"start"),WorkflowNode("end",NodeKind.END,"end"))
        val edges=mutableListOf(WorkflowEdge("entry","start","r0"))
        counts.forEachIndexed { i,count ->
            nodes+=WorkflowNode("r$i",NodeKind.REPEAT,"repeat",repeatCount=count,joinId="j$i")
            nodes+=WorkflowNode("j$i",NodeKind.JOIN,"join")
            edges+=WorkflowEdge("body$i","r$i",if(i==counts.lastIndex)"read" else "r${i+1}","body")
            edges+=WorkflowEdge("next$i","r$i",if(i==0)if(padding>0)"p0" else "end" else "j${i-1}")
        }
        nodes+=WorkflowNode("read",NodeKind.ACTION,"read",action=ActionSpec(ActionType.CALENDAR_READ))
        edges+=WorkflowEdge("read-end","read","j${counts.lastIndex}")
        repeat(padding) { i ->
            nodes+=WorkflowNode("p$i",NodeKind.ACTION,"padding",action=ActionSpec(ActionType.CALENDAR_READ))
            edges+=WorkflowEdge("p-edge$i","p$i",if(i+1==padding)"end" else "p${i+1}")
        }
        return WorkflowDefinition(id="visit-limit",title="Visit limit",nodes=nodes,edges=edges)
    }
    @Test fun exact4096VisitsRemainValidBut4097AreRejectedBeforeSimulation()=runBlocking {
        // START + END + 1 + 18*(1 + 14*(1 + 15)) = 4053; joins stop repeat bodies and are not visited.
        val allowed=nested(43)
        assertTrue(WorkflowValidator.validate(allowed).toString(),WorkflowValidator.validate(allowed).isEmpty())
        assertEquals(RunStatus.COMPLETED,WorkflowSimulator.simulate(allowed,ExecutionContext.simulated(0)).status)
        val rejected=nested(44)
        assertTrue(WorkflowValidator.validate(rejected).any { it.message.contains("4,096") })
        val run=WorkflowSimulator.simulate(rejected,ExecutionContext.simulated(0))
        assertEquals(RunStatus.BLOCKED,run.status)
        assertFalse(run.events.any { it.status==ActionStatus.REQUESTED })
    }
    @Test fun nestedRepeatPotentialIsRejectedWithoutExecutingAnyActions()=runBlocking {
        val w=nested(0).copy(nodes=nested(0).nodes.map { if(it.kind==NodeKind.REPEAT)it.copy(repeatCount=20) else it })
        var commands=0
        val adapter=object:ActionAdapter {
            override suspend fun execute(request:ActionRequest,onProgress:suspend(ActionProgress)->Unit):ActionResult { commands++;return ActionResult(ActionStatus.VERIFIED,"unexpected") }
            override suspend fun stop(runId:String)=emptyList<ActionResult>()
        }
        assertEquals(RunStatus.BLOCKED,WorkflowEngine(adapter,{ExecutionContext(0,0)}).run(w).status)
        assertEquals(0,commands)
    }
    @Test fun branchWorstCaseIncludesTheLongerIfPath() {
        val base=nested(44)
        val condition=WorkflowNode("if",NodeKind.IF,"condition",condition=ConditionSpec(ConditionField.OUTSIDE_TEMPERATURE,value=5.0))
        val w=base.copy(nodes=base.nodes+condition,edges=base.edges.filterNot { it.id=="entry" }+listOf(
            WorkflowEdge("new-entry","start","if"),WorkflowEdge("yes","if","r0","true"),WorkflowEdge("no","if","end","false")))
        assertTrue(WorkflowValidator.validate(w).any { it.message.contains("4,096") })
    }
    @Test fun alternativeIfPathsAreNotAddedTogether() {
        val base=nested(0)
        val w=base.copy(nodes=base.nodes+WorkflowNode("if",NodeKind.IF,"condition",condition=ConditionSpec(ConditionField.OUTSIDE_TEMPERATURE,value=5.0)),
            edges=base.edges.filterNot { it.id=="entry" }+listOf(WorkflowEdge("enter-if","start","if"),
                WorkflowEdge("yes","if","r0","true"),WorkflowEdge("no","if","r0","false")))
        assertTrue(WorkflowValidator.validate(w).toString(),WorkflowValidator.validate(w).isEmpty())
        assertEquals(4054,ExecutionLimits.worstCaseVisits(w))
    }
    @Test fun parallelBranchesAreAddedEvenWhenEachAloneFits() {
        val base=nested(0)
        val branchNodes=base.nodes.filter { it.id !in setOf("start","end") }
        val branchEdges=base.edges.filterNot { it.id=="entry" }
        val w=base.copy(nodes=listOf(base.nodes[0],base.nodes[1],WorkflowNode("parallel",NodeKind.PARALLEL,"parallel",joinId="joined"),WorkflowNode("joined",NodeKind.JOIN,"joined"))+
            (0..1).flatMap { b -> branchNodes.map { it.copy(id="b$b-${it.id}",joinId=it.joinId?.let { j -> "b$b-$j" }) } },
            edges=listOf(WorkflowEdge("start-parallel","start","parallel"),WorkflowEdge("joined-end","joined","end"))+
                (0..1).flatMap { b -> listOf(WorkflowEdge("enter$b","parallel","b$b-r0","branch:$b"))+branchEdges.map {
                    it.copy(id="b$b-${it.id}",source="b$b-${it.source}",target=if(it.target=="end")"joined" else "b$b-${it.target}") } })
        assertTrue(WorkflowValidator.validate(w).toString(),WorkflowValidator.validate(w).any { it.message.contains("4,096") })
    }
    @Test fun deeplyNestedRepeatsDoNotOverflowIntoAnAllowedCount() {
        assertTrue(WorkflowValidator.validate(nested(0,List(15) { 20 })).any { it.message.contains("4,096") })
    }
}
