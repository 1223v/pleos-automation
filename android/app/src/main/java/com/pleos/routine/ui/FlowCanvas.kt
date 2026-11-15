package com.pleos.routine.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.pleos.routine.R
import com.pleos.routine.core.*
import com.pleos.routine.ui.theme.AstryxColors
import com.pleos.routine.ui.theme.AstryxRadius
import com.pleos.routine.ui.theme.astryx

// Node geometry in canvas dp. A node occupies a 230dp column (GraphEditing places nodes 250dp apart);
// the coloured square sits centred at the top and the label underneath, as in n8n.
private const val COLUMN = 230f
private const val SQUARE = 88f
private const val SQUARE_LEFT = (COLUMN - SQUARE) / 2
private const val HANDLE = 6f
private const val GRID = 24f
private const val NODE_HEIGHT = 170f   // square + two label lines + badge

private fun portFraction(index: Int, count: Int) = if (count <= 1) 0.5f else (index + 1f) / (count + 1f)

private fun nodeShape(kind: NodeKind): Shape = when (kind) {
    NodeKind.START -> RoundedCornerShape(topStart = SQUARE.dp / 2, bottomStart = SQUARE.dp / 2, topEnd = AstryxRadius.container, bottomEnd = AstryxRadius.container)
    NodeKind.END -> CircleShape
    else -> RoundedCornerShape(AstryxRadius.container)
}

/**
 * n8n-style routine canvas: pan/zoom, a dot grid that moves with the view, coloured icon nodes,
 * connection handles, curved edges with arrowheads and port labels. [highlight] marks a run path or
 * changed nodes; nothing animates (Pleos driver distraction guidance).
 */
@Composable fun FlowCanvas(w:WorkflowDefinition,selected:String?,editable:Boolean,onSelect:(String)->Unit,onMove:(String,NodePosition)->Unit,
                           modifier:Modifier=Modifier,highlight:Set<String> = emptySet()) {
    var zoom by remember { mutableFloatStateOf(0.95f) }
    var pan by remember { mutableStateOf(Offset(20f,20f)) }
    val density=LocalDensity.current.density
    val positions=remember(w) { mutableStateMapOf<String,NodePosition>().apply { w.nodes.forEach { put(it.id,it.position) } } }
    val c=astryx
    val measurer=rememberTextMeasurer()
    val ports=remember(w) { w.nodes.associate { n -> n.id to NodeVisuals.outputPorts(n.kind,w.edges.filter { it.source==n.id }.map { it.port }) } }
    fun screen(x:Float,y:Float)=Offset(x*density*zoom+pan.x,y*density*zoom+pan.y)
    fun input(id:String)=positions[id]?.let { screen(it.x+SQUARE_LEFT,it.y+SQUARE/2) }
    fun output(id:String,port:String):Offset? {
        val p=positions[id] ?: return null
        val list=ports[id].orEmpty();val i=list.indexOf(port).takeIf { it>=0 } ?: 0
        return screen(p.x+SQUARE_LEFT+SQUARE,p.y+SQUARE*portFraction(i,list.size))
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember(w.id) { mutableStateOf(false) }
    // Shows the whole flow: zoom so every node column fits, then centre it above the zoom controls.
    fun fit() {
        if(canvasSize==IntSize.Zero || w.nodes.isEmpty())return
        val minX=w.nodes.minOf { it.position.x };val maxX=w.nodes.maxOf { it.position.x }+COLUMN
        val minY=w.nodes.minOf { it.position.y };val maxY=w.nodes.maxOf { it.position.y }+NODE_HEIGHT
        val pad=28f*density;val controls=64f*density
        val room=Size(canvasSize.width-pad*2,canvasSize.height-pad*2-controls)
        val z=minOf(room.width/((maxX-minX)*density),room.height/((maxY-minY)*density),1f).coerceAtLeast(0.3f)
        zoom=z
        pan=Offset(pad-minX*density*z+((room.width-(maxX-minX)*density*z)/2).coerceAtLeast(0f),
            pad-minY*density*z+((room.height-(maxY-minY)*density*z)/2).coerceAtLeast(0f))
    }
    LaunchedEffect(w.id,canvasSize) { if(!fitted && canvasSize!=IntSize.Zero) { fit();fitted=true } }
    val shape=RoundedCornerShape(AstryxRadius.page/2)
    Box(modifier.clip(shape).background(c.body).border(1.dp,c.borderEmphasized,shape).onSizeChanged { canvasSize=it }
        .pointerInput(w.id) { detectTransformGestures { _,p,z,_ -> pan+=p;zoom=(zoom*z).coerceIn(0.25f,1.7f) } }) {
        Canvas(Modifier.fillMaxSize()) {
            val step=GRID*density*zoom
            if(step>=8f) {
                val dot=(1.3f*density*zoom).coerceAtLeast(1f)
                var x=((pan.x%step)+step)%step
                while(x<size.width) { var y=((pan.y%step)+step)%step;while(y<size.height) { drawCircle(c.borderEmphasized,dot,Offset(x,y));y+=step };x+=step }
            }
            w.edges.forEach { e ->
                val start=output(e.source,e.port) ?: return@forEach;val end=input(e.target) ?: return@forEach
                val hot=e.source in highlight && e.target in highlight
                val colour=when { hot->c.accent;e.port=="true"->c.success;else->c.iconSecondary }
                drawEdge(start,end,colour,(if(hot)3.2f else 2.4f)*density*zoom.coerceAtLeast(.6f),dashed=e.port=="false",density*zoom)
            }
        }
        w.nodes.forEach { node ->
            val pos=positions[node.id]!!
            val v=NodeVisuals.of(node);val hue=c.hue(v.hue)
            val isSelected=node.id==selected;val isHot=node.id in highlight
            Column(Modifier.offset { IntOffset((pos.x*density*zoom+pan.x).toInt(),(pos.y*density*zoom+pan.y).toInt()) }
                .graphicsLayer(scaleX=zoom,scaleY=zoom,transformOrigin=TransformOrigin(0f,0f))
                .width(COLUMN.dp)
                .clickable { onSelect(node.id) }
                .pointerInput(node.id,editable,zoom) { if(editable)detectDragGestures(onDragEnd={ onMove(node.id,positions[node.id]!!) },onDragCancel={positions[node.id]=node.position}) { change,delta -> change.consume();val p=positions[node.id]!!;positions[node.id]=NodePosition((p.x+delta.x/density/zoom).coerceAtLeast(0f),(p.y+delta.y/density/zoom).coerceAtLeast(0f)) } },
                horizontalAlignment=Alignment.CenterHorizontally) {
                val nodeShape=nodeShape(node.kind)
                val ring=when { isSelected->c.accent;isHot->c.success;else->null }
                // The ring is drawn outside the square so selecting a node never moves its handles.
                Box(Modifier.size(SQUARE.dp)
                    .drawBehind { ring?.let { colour -> val inset=6.dp.toPx();translate(-inset,-inset) {
                        drawOutline(nodeShape.createOutline(Size(size.width+inset*2,size.height+inset*2),layoutDirection,this),colour,style=Stroke(3.dp.toPx())) } } }
                    .shadow(2.dp,nodeShape).background(hue.background,nodeShape).border(1.dp,c.border,nodeShape),contentAlignment=Alignment.Center) {
                    LucideIcon(v.icon,size=40.dp,tint=hue.icon)
                }
                Spacer(Modifier.height(12.dp))
                Text(node.label,style=MaterialTheme.typography.titleSmall,color=c.textPrimary,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(if(node.kind==NodeKind.START)NodeVisuals.triggerLabel(w.trigger.type) else v.subtitle,style=MaterialTheme.typography.labelMedium,color=c.textSecondary,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
                v.badge?.let { Spacer(Modifier.height(6.dp));Pill(it,Tone.WARNING,R.drawable.ic_lucide_hand) }
            }
        }
        // Handles and port words sit above the nodes, like n8n's connection points.
        Canvas(Modifier.fillMaxSize()) {
            val r=HANDLE*density*zoom
            w.nodes.forEach { node ->
                if(node.kind!=NodeKind.START)input(node.id)?.let { drawHandle(it,r,c) }
                ports[node.id].orEmpty().forEach { port ->
                    val at=output(node.id,port) ?: return@forEach
                    drawHandle(at,r,c)
                    NodeVisuals.portLabel(node.kind,port)?.let { label -> drawPortLabel(measurer,label,at+Offset(r*2.2f,0f),port,c,zoom) }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(CircleShape).background(c.surface).border(1.dp,c.borderEmphasized,CircleShape).padding(4.dp),
            horizontalArrangement=Arrangement.spacedBy(2.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={zoom=(zoom-0.15f).coerceAtLeast(.25f)},colors=ButtonDefaults.textButtonColors(contentColor=c.textPrimary)) { Text("−",style=MaterialTheme.typography.titleMedium) }
            Text("${(zoom*100).toInt()}%",style=MaterialTheme.typography.labelMedium,color=c.textSecondary)
            TextButton(onClick={zoom=(zoom+0.15f).coerceAtMost(1.7f)},colors=ButtonDefaults.textButtonColors(contentColor=c.textPrimary)) { Text("+",style=MaterialTheme.typography.titleMedium) }
            TextButton(onClick={fit()},colors=ButtonDefaults.textButtonColors(contentColor=c.textPrimary)) { ButtonIcon(R.drawable.ic_lucide_maximize);Text("전체 보기") }
        }
    }
}

private fun DrawScope.drawEdge(start:Offset,end:Offset,colour:Color,width:Float,dashed:Boolean,scale:Float) {
    val tip=Offset(end.x-HANDLE*scale-1f,end.y)
    val arrow=9f*scale
    val bend=(kotlin.math.abs(tip.x-start.x)/2).coerceAtLeast(48f*scale)
    val stem=Offset(tip.x-arrow,tip.y)
    drawPath(Path().apply { moveTo(start.x,start.y);cubicTo(start.x+bend,start.y,stem.x-bend,stem.y,stem.x,stem.y) },colour,
        style=Stroke(width,pathEffect=if(dashed)PathEffect.dashPathEffect(floatArrayOf(7f*scale,6f*scale)) else null))
    drawPath(Path().apply { moveTo(tip.x,tip.y);lineTo(stem.x,tip.y-arrow*0.55f);lineTo(stem.x,tip.y+arrow*0.55f);close() },colour)
}

private fun DrawScope.drawHandle(at:Offset,r:Float,c:AstryxColors) {
    drawCircle(c.surface,r,at)
    drawCircle(c.iconSecondary,r,at,style=Stroke(r*0.42f))
}

private fun DrawScope.drawPortLabel(measurer:TextMeasurer,label:String,at:Offset,port:String,c:AstryxColors,zoom:Float) {
    val yes=port=="true"
    val text=measurer.measure(label,TextStyle(fontSize=(14*zoom.coerceAtLeast(.5f)).sp,fontWeight=FontWeight.Bold,color=if(yes)c.onSuccessMuted else c.textSecondary))
    val padX=8f*zoom.coerceAtLeast(.5f)*density;val padY=2f*zoom.coerceAtLeast(.5f)*density
    val box=Size(text.size.width+padX*2,text.size.height+padY*2)
    val topLeft=Offset(at.x,at.y-box.height/2)
    drawRoundRect(if(yes)c.successMuted else c.surface,topLeft,box,CornerRadius(box.height/2))
    drawRoundRect(if(yes)c.success else c.borderEmphasized,topLeft,box,CornerRadius(box.height/2),style=Stroke(1.2f*density))
    drawText(text,topLeft=topLeft+Offset(padX,padY))
}
