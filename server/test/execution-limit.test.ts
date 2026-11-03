import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validateWorkflow, type ObjectValue } from '../src/contracts.js';

const example=JSON.parse(readFileSync(new URL('../../../contracts/fixtures/commute.json',import.meta.url),'utf8')) as ObjectValue;
const template=(example.nodes as ObjectValue[])[0]!;
function nested(padding:number,counts=[18,14,15]):ObjectValue {
  const node=(id:string,kind:string,extra:ObjectValue={})=>({...structuredClone(template),id,kind,label:id,...extra});
  const action={...structuredClone((example.nodes as ObjectValue[]).find(n=>n.id==='heat')!.action as ObjectValue),type:'CALENDAR_READ',value:null};
  const nodes:ObjectValue[]=[node('start','START'),node('end','END')];
  const edges:ObjectValue[]=[{id:'entry',source:'start',target:'r0',port:'next'}];
  counts.forEach((count,i)=>{
    nodes.push(node(`r${i}`,'REPEAT',{repeatCount:count,joinId:`j${i}`}),node(`j${i}`,'JOIN'));
    edges.push({id:`body${i}`,source:`r${i}`,target:i===counts.length-1?'read':`r${i+1}`,port:'body'},
      {id:`next${i}`,source:`r${i}`,target:i===0?(padding?'p0':'end'):`j${i-1}`,port:'next'});
  });
  nodes.push(node('read','ACTION',{action}));
  edges.push({id:'read-end',source:'read',target:`j${counts.length-1}`,port:'next'});
  for(let i=0;i<padding;i++){
    nodes.push(node(`p${i}`,'ACTION',{action}));
    edges.push({id:`p-edge${i}`,source:`p${i}`,target:i+1===padding?'end':`p${i+1}`,port:'next'});
  }
  return {...structuredClone(example),id:'visit-limit',nodes,edges};
}
test('workflow contract accepts exactly4096 visits and rejects4097 before provider compilation',()=>{
  assert.doesNotThrow(()=>validateWorkflow(nested(43)));
  assert.throws(()=>validateWorkflow(nested(44)),/4,096/);
});
test('deep nested repeats saturate rather than overflow the visit limit',()=>{
  assert.throws(()=>validateWorkflow(nested(0,Array(15).fill(20))),/4,096/);
});
test('IF alternatives use the maximum path instead of summing both',()=>{
  const w=nested(0);
  const condition=(example.nodes as ObjectValue[]).find(n=>n.kind==='IF')!.condition;
  (w.nodes as ObjectValue[]).push({...structuredClone(template),id:'choose',kind:'IF',label:'choose',condition});
  w.edges=(w.edges as ObjectValue[]).filter(e=>e.id!=='entry').concat([
    {id:'entry',source:'start',target:'choose',port:'next'},
    {id:'true',source:'choose',target:'r0',port:'true'},
    {id:'false',source:'choose',target:'r0',port:'false'},
  ]);
  assert.doesNotThrow(()=>validateWorkflow(w));
});
test('parallel branches add their visits even when each branch alone fits',()=>{
  const w=nested(0);
  const nodes=w.nodes as ObjectValue[],edges=w.edges as ObjectValue[];
  w.nodes=[nodes[0]!,nodes[1]!,{...structuredClone(template),id:'parallel',kind:'PARALLEL',label:'parallel',joinId:'joined'},
    {...structuredClone(template),id:'joined',kind:'JOIN',label:'joined'},
    ...[0,1].flatMap(b=>nodes.filter(n=>!['start','end'].includes(String(n.id))).map(n=>({...n,id:`b${b}-${n.id}`,joinId:n.joinId===null?null:`b${b}-${n.joinId}`})))];
  w.edges=[{id:'entry',source:'start',target:'parallel',port:'next'},{id:'exit',source:'joined',target:'end',port:'next'},
    ...[0,1].flatMap(b=>[{id:`branch${b}`,source:'parallel',target:`b${b}-r0`,port:`branch:${b}`},
      ...edges.filter(e=>e.id!=='entry').map(e=>({...e,id:`b${b}-${e.id}`,source:`b${b}-${e.source}`,target:e.target==='end'?'joined':`b${b}-${e.target}`}))])];
  assert.throws(()=>validateWorkflow(w),/4,096/);
});
