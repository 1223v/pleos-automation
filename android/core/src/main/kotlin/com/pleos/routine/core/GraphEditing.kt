package com.pleos.routine.core

import java.util.UUID

object GraphEditing {
    /** Insert a complete structured fragment into one selected edge. */
    fun insert(w:WorkflowDefinition,edgeId:String,kind:NodeKind,action:ActionSpec?=null):WorkflowDefinition {
        val edge=w.edges.single { it.id==edgeId }
        val prefix="n"+UUID.randomUUID().toString().take(8)
        val previous=w.nodes.single { it.id==edge.source }
        val x=previous.position.x+250;val y=previous.position.y
        val node=WorkflowNode(prefix,kind,when(kind) { NodeKind.ACTION->"새 동작";NodeKind.IF->"조건 분기";NodeKind.REPEAT->"반복";NodeKind.PARALLEL->"동시에 실행";else->"대기" },NodePosition(x,y),
            action=if(kind==NodeKind.ACTION)action ?: ActionSpec(ActionType.SET_HVAC_TARGET,area="ROW_1_LEFT",value=23.0) else null,
            condition=if(kind==NodeKind.IF)ConditionSpec(ConditionField.OUTSIDE_TEMPERATURE,value=5.0) else null,
            durationSeconds=if(kind in setOf(NodeKind.WAIT,NodeKind.TIMER))10 else null,
            repeatCount=if(kind==NodeKind.REPEAT)2 else null,
            joinId=if(kind in setOf(NodeKind.REPEAT,NodeKind.PARALLEL))prefix+"join" else null)
        val nodes=mutableListOf(node)
        val edges=mutableListOf(edge.copy(target=prefix))
        fun connect(source:String,target:String,port:String="next") { edges+=WorkflowEdge("e"+UUID.randomUUID().toString().take(8),source,target,port) }
        when(kind) {
            NodeKind.IF -> { connect(prefix,edge.target,"true");connect(prefix,edge.target,"false") }
            NodeKind.REPEAT,NodeKind.PARALLEL -> {
                val join=node.joinId!!
                nodes+=WorkflowNode(join,NodeKind.JOIN,"합류",NodePosition(x+500,y))
                val count=if(kind==NodeKind.PARALLEL)2 else 1
                repeat(count) { i -> val child=prefix+"b$i";nodes+=WorkflowNode(child,NodeKind.WAIT,"분기 ${i+1}",NodePosition(x+250,y+i*150),durationSeconds=1);connect(prefix,child,if(kind==NodeKind.REPEAT)"body" else "branch:$i");connect(child,join) }
                if(kind==NodeKind.REPEAT)connect(prefix,edge.target) else connect(join,edge.target)
            }
            else -> connect(prefix,edge.target)
        }
        require(w.nodes.size + nodes.size <= 64) { "추가할 분기·합류를 포함해 최대 64개 노드로 구성하세요." }
        return w.copy(revision=w.revision+1,nodes=w.nodes+nodes,edges=w.edges.filterNot { it.id==edgeId }+edges)
    }
    fun remove(w:WorkflowDefinition,id:String):WorkflowDefinition {
        val node=w.nodes.single { it.id==id }
        require(node.kind in setOf(NodeKind.ACTION,NodeKind.WAIT,NodeKind.TIMER)) { "분기·합류 삭제는 연결 편집에서 구조 전체를 수정하세요." }
        val target=w.edges.single { it.source==id }.target
        return w.copy(revision=w.revision+1,nodes=w.nodes.filterNot { it.id==id },edges=w.edges.filterNot { it.source==id }.map { if(it.target==id)it.copy(target=target) else it })
    }
}
