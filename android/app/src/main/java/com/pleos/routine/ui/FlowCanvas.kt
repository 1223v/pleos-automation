package com.pleos.routine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import com.pleos.routine.core.*

@Composable fun FlowCanvas(w:WorkflowDefinition,selected:String?,editable:Boolean,onSelect:(String)->Unit,onMove:(String,NodePosition)->Unit,modifier:Modifier=Modifier) {
    var zoom by remember { mutableFloatStateOf(0.95f) }
    var pan by remember { mutableStateOf(Offset(20f,20f)) }
    val density=LocalDensity.current.density
    val positions=remember(w) { mutableStateMapOf<String,NodePosition>().apply { w.nodes.forEach { put(it.id,it.position) } } }
    Box(modifier.background(Color(0xFF101E2B),RoundedCornerShape(16.dp)).pointerInput(w.id) { detectTransformGestures { _,p,z,_ -> pan+=p;zoom=(zoom*z).coerceIn(0.25f,1.7f) } }) {
        Canvas(Modifier.fillMaxSize()) {
            for(x in 0..(size.width/24).toInt())for(y in 0..(size.height/24).toInt())drawCircle(Color(0xFF2D4354),1f,Offset(x*24f,y*24f))
            w.edges.forEach { e ->
                val a=positions[e.source] ?: return@forEach;val b=positions[e.target] ?: return@forEach
                val start=Offset((a.x+230)*density*zoom+pan.x,(a.y+55)*density*zoom+pan.y)
                val end=Offset(b.x*density*zoom+pan.x,(b.y+55)*density*zoom+pan.y)
                val bend=(kotlin.math.abs(end.x-start.x)/2).coerceAtLeast(40f)
                val color=when(e.port) { "true"->Color(0xFF79CCA9);"false"->Color(0xFFEBC477);else->Color(0xFF76BCEB) }
                drawPath(Path().apply { moveTo(start.x,start.y);cubicTo(start.x+bend,start.y,end.x-bend,end.y,end.x,end.y) },color,style=Stroke(2*density))
                drawCircle(color,4*density,end)
            }
        }
        w.nodes.forEach { node ->
            val pos=positions[node.id]!!
            Column(Modifier.offset { IntOffset((pos.x*density*zoom+pan.x).toInt(),(pos.y*density*zoom+pan.y).toInt()) }
                .graphicsLayer(scaleX=zoom,scaleY=zoom,transformOrigin=androidx.compose.ui.graphics.TransformOrigin(0f,0f))
                .width(230.dp).height(110.dp).background(Color(0xFF233747),RoundedCornerShape(12.dp))
                .border(if(node.id==selected)2.dp else 1.dp,if(node.id==selected)Color(0xFF76BCEB) else Color(0xFF466073),RoundedCornerShape(12.dp))
                .clickable { onSelect(node.id) }
                .pointerInput(node.id,editable,zoom) { if(editable)detectDragGestures(onDragEnd={ onMove(node.id,positions[node.id]!!) },onDragCancel={positions[node.id]=node.position}) { change,delta -> change.consume();val p=positions[node.id]!!;positions[node.id]=NodePosition((p.x+delta.x/density/zoom).coerceAtLeast(0f),(p.y+delta.y/density/zoom).coerceAtLeast(0f)) } }
                .padding(12.dp),verticalArrangement=Arrangement.SpaceBetween) {
                Text(node.kind.name,color=Color(0xFF76BCEB),style=MaterialTheme.typography.labelSmall)
                Text(node.label,maxLines=1,style=MaterialTheme.typography.titleSmall)
                Text(when(node.kind) { NodeKind.IF->"초록 = 충족 · 노랑 = 미충족";NodeKind.REPEAT->"${node.repeatCount}회";NodeKind.WAIT,NodeKind.TIMER->"${node.durationSeconds}초";else->node.action?.type?.name ?: node.id },maxLines=1,style=MaterialTheme.typography.labelSmall,color=Color(0xFFB6C9D5))
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            FilledTonalButton(onClick={zoom=(zoom-0.15f).coerceAtLeast(.25f)}) { Text("−") }
            FilledTonalButton(onClick={zoom=(zoom+0.15f).coerceAtMost(1.7f)}) { Text("+") }
            FilledTonalButton(onClick={zoom=.95f;pan=Offset(20f,20f)}) { Text("처음 위치") }
        }
    }
}
