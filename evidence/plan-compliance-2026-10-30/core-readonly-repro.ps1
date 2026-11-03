$ErrorActionPreference = 'Stop'
$auditRoot = 'C:\pleos-automation'
Set-Location -LiteralPath $auditRoot
$auditJars = @(
    'org.jetbrains.kotlin/kotlin-stdlib/2.1.10',
    'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.9.0',
    'org.jetbrains.kotlinx/kotlinx-serialization-core-jvm/1.7.3',
    'org.jetbrains.kotlinx/kotlinx-serialization-json-jvm/1.7.3'
) | ForEach-Object {
    Get-ChildItem -LiteralPath (Join-Path '.tools/gradle-home/caches/modules-2/files-2.1' $_) -Recurse -Filter '*.jar' |
        Select-Object -ExpandProperty FullName
}
$auditClassPath = ((Resolve-Path 'android/core/build/classes/kotlin/main').Path + ';' + ($auditJars -join ';'))
# Run existing compiled core classes only; no Gradle, compilation, or source changes.
@'
import com.pleos.routine.core.*;
import kotlinx.coroutines.*;
import kotlin.coroutines.*;
import java.nio.file.*;
import java.util.*;
var w=WorkflowJson.INSTANCE.decode(Files.readString(Path.of("contracts/fixtures/commute.json")));
long monday=java.time.Instant.parse("2026-10-25T23:00:00Z").toEpochMilli();
var x=ExecutionContext.Companion.simulated(monday,12.0,"P",0.0,false);
RunResult r=BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,(scope,c)->WorkflowSimulator.INSTANCE.simulate(w,x,c));
System.out.println("OCCUPIED_FALSE="+r.getStatus()+" "+r.getEvents().stream().filter(e->e.getStatus()==ActionStatus.SIMULATED).map(e->e.getNodeId()).toList());
System.out.println("WARM_HEAT_EVENTS="+r.getEvents().stream().filter(e->"heat".equals(e.getNodeId())).count()+" SKIPPED_EVENTS="+r.getEvents().stream().filter(e->e.getStatus()==ActionStatus.SKIPPED).count());
var condition=new ConditionSpec(ConditionField.OUTSIDE_TEMPERATURE,Comparison.LTE,5.0,null,null,null,null,List.of());
var trigger=new TriggerDefinition(TriggerType.CONDITION_ENTER,condition,List.of(),null,List.of(),"Asia/Seoul");
var b=WorkflowExamples.INSTANCE.blank("false-trigger");
var timer=new WorkflowNode("timer",NodeKind.TIMER,"timer",new NodePosition(0,0),null,null,1L,null,null);
var t=new WorkflowDefinition(2,b.getId(),b.getTitle(),1,trigger,List.of(b.getNodes().get(0),timer,b.getNodes().get(1)),List.of(new WorkflowEdge("a","start","timer","next"),new WorkflowEdge("b","timer","end","next")));
RunResult r2=BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,(scope,c)->WorkflowSimulator.INSTANCE.simulate(t,x,c));
System.out.println("FALSE_CONDITION_TRIGGER="+Conditions.INSTANCE.evaluate(condition,x,"Asia/Seoul").getTruth()+" RESULT="+r2.getStatus()+" SIMULATED_NODES="+r2.getEvents().stream().filter(e->e.getStatus()==ActionStatus.SIMULATED).map(e->e.getNodeId()).toList());
var wait=new WorkflowNode("wait",NodeKind.WAIT,"wait",new NodePosition(0,0),null,null,7200L,null,null);
var over=new WorkflowDefinition(2,b.getId(),b.getTitle(),1,b.getTrigger(),List.of(b.getNodes().get(0),wait,timer,b.getNodes().get(1)),List.of(new WorkflowEdge("a","start","wait","next"),new WorkflowEdge("b","wait","timer","next"),new WorkflowEdge("c","timer","end","next")));
RunResult r3=BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,(scope,c)->WorkflowSimulator.INSTANCE.simulate(over,x,c));
System.out.println("PARTIAL_ELAPSED="+r3.getElapsedMillis()+" MAX_EVENT_ELAPSED="+r3.getEvents().stream().mapToLong(e->e.getElapsedMillis()).max().orElse(-1)+" STATUS="+r3.getStatus());
WorkflowDefinition sixty=b;
for(int i=0;i<58;i++) sixty=GraphEditing.INSTANCE.insert(sixty,sixty.getEdges().get(0).getId(),NodeKind.WAIT,null);
System.out.println("EDITOR_NODES="+sixty.getNodes().size()+" VALIDATION_ERRORS="+WorkflowValidator.INSTANCE.validate(sixty).size());
try { GraphEditing.INSTANCE.insert(sixty,sixty.getEdges().get(0).getId(),NodeKind.WAIT,null); System.out.println("EDITOR_ADD_61=ACCEPTED"); } catch(IllegalArgumentException e) { System.out.println("EDITOR_ADD_61=REJECTED "+e.getMessage()); }
/exit
'@ | & '.tools/corretto-17.0.21.0.11/bin/jshell.exe' --class-path $auditClassPath -q
if ($LASTEXITCODE -ne 0) { throw "JShell exited with $LASTEXITCODE" }
