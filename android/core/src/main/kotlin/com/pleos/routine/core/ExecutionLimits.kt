package com.pleos.routine.core

object ExecutionLimits {
    const val MAX_NODE_VISITS = 4096
    /** Call only after structural validation. Saturation avoids overflow in nested repeats. */
    fun worstCaseVisits(w: WorkflowDefinition): Int {
        val nodes=w.nodes.associateBy { it.id }
        val outgoing=w.edges.groupBy { it.source }
        val memo=mutableMapOf<Pair<String,String?>,Int>()
        fun cap(value:Long)=value.coerceAtMost(MAX_NODE_VISITS+1L).toInt()
        fun walk(id:String,stop:String?):Int {
            if(id==stop)return 0 // Repeat/parallel bodies do not visit their terminating JOIN.
            val key=id to stop
            memo[key]?.let { return it }
            val node=nodes.getValue(id)
            val edges=outgoing[id].orEmpty()
            fun target(port:String)=edges.single { it.port==port }.target
            val following=when(node.kind) {
                NodeKind.END -> 0L
                NodeKind.IF -> edges.maxOf { walk(it.target,stop) }.toLong()
                NodeKind.REPEAT -> walk(target("body"),node.joinId).toLong()*node.repeatCount!! + walk(target("next"),stop)
                NodeKind.PARALLEL -> edges.sumOf { walk(it.target,node.joinId).toLong() } + walk(node.joinId!!,stop)
                else -> walk(target("next"),stop).toLong()
            }
            return cap(1+following).also { memo[key]=it }
        }
        return walk(w.nodes.single { it.kind==NodeKind.START }.id,null)
    }
}
