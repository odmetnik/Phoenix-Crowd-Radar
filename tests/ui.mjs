import {createRequire} from 'node:module';import fs from 'node:fs';import vm from 'node:vm';import assert from 'node:assert/strict';
class Element{constructor(){this.textContent='';this.hidden=false;this.children=[];this.dataset={};this.classList={toggle(){}};this.style={setProperty(){}};}append(...v){this.children.push(...v);}replaceChildren(){this.children=[];}setAttribute(){}getContext(){return {};}}
const ids=['scope','badge','event','status','start','demo','version','gps','hardware','groupInfo','pingTarget','scale','members','radarPage','groupPage','pingPage','mailStatus','pingLabel'];const elements=Object.fromEntries(ids.map(id=>[id,new Element()]));const tabs=['radar','group','ping'].map(tab=>Object.assign(new Element(),{dataset:{tab}}));let click;let sent=[];
const ops=[];elements.scope.clientWidth=400;elements.scope.getContext=()=>new Proxy({},{get:(o,key)=>o[key]??((...args)=>ops.push([key,...args])),set:(o,k,v)=>{o[k]=v;return true;}});let scheduled;let clock=1000;
const document={getElementById:id=>elements[id],createElement:()=>new Element(),querySelectorAll:()=>tabs,addEventListener:(event,fn)=>{if(event==='click')click=fn;}};
const context=vm.createContext({document,window:{Phoenix:{action:(a,v)=>sent.push([a,v])},scrollTo(){}},matchMedia:()=>({matches:false}),performance:{now:()=>clock},requestAnimationFrame:fn=>{scheduled=fn;return 0;},cancelAnimationFrame(){},console});
vm.runInContext(fs.readFileSync('app/src/main/assets/ui/geometry.js','utf8'),context);
vm.runInContext(fs.readFileSync('app/src/main/assets/ui/radar.js','utf8'),context);
function state(update){context.window.receiveState({group:'Test',hasGroup:true,demo:false,running:true,members:[],status:'Tražim',version:'test',...update});scheduled(clock);}
state({members:[{id:3,name:'<img src=x onerror=bad()>',source:'UWB',meters:0,angle:0}]});assert.equal(elements.members.children[0].children[1].textContent,'<img src=x onerror=bad()>');assert.match(elements.members.children[0].children[2].children[1].textContent,/0 m/);assert.match(elements.scale.textContent,/RADIJUS 0,5 m/);
state({members:[{id:3,name:'Ana',source:'GPS',meters:38,angle:null,stale:true}]});assert.equal(elements.members.children[0].children[2].children[1].textContent,'ZADNJE VIĐEN');assert.doesNotMatch(elements.scale.textContent,/RADIJUS/);
state({members:[{id:2,name:'Ivan',source:'BLE',meters:null,angle:null}]});assert.match(elements.members.children[0].children[2].children[1].textContent,/SMJER NEPOZNAT/);
for(const tab of tabs){click({target:{closest:()=>tab}});assert.equal(elements[tab.dataset.tab+'Page'].hidden,false);assert.equal(ids.filter(i=>i.endsWith('Page')&&!elements[i].hidden).length,1);}
for(const action of ['radar','invite','scan','host','join','create','gps','help','message','inbox']){click({target:{closest:()=>({dataset:{action}})}});assert.equal(sent.at(-1)[0],action);}
state({demo:true,members:[]});assert.equal(elements.badge.textContent,'DEMO / KONCEPT');state({running:false,demo:false});assert.equal(elements.badge.textContent,'RADAR ZAUSTAVLJEN');console.log('PASS: UI navigation, native actions, measured zero, stale state, unknown bearing and safe member names');

const geo=createRequire(import.meta.url)('../app/src/main/assets/ui/geometry.js');
for(const [distance,expected] of [[0,.5],[.1,.5],[.6,1],[1,2],[3,5],[12,20],[38,50],[120,200],[1200,2000]])assert.equal(geo.niceRange(distance),expected);
assert.equal(geo.fit([{id:1,meters:.6},{id:2,meters:120}],1).target,1);
assert.equal(geo.fit([{id:1,meters:.6},{id:2,meters:120}],null).target,200);
assert.equal(geo.fit([{id:1,meters:600,stale:true}],1).metric,false);
assert.equal(geo.position(.6,null,1,100),null);assert.equal(geo.position(-1,0,1,100),null);
const p=geo.position(.6,90,1,100);assert.equal(p.x,60);assert.ok(Math.abs(p.y)<.00001);assert.equal(p.outside,false);
assert.equal(geo.position(100,0,1,100).outside,true);
const zoom=new geo.Zoom();zoom.set(1,null,0);assert.equal(zoom.tick(0),1);zoom.set(20,null,100);assert.ok(zoom.tick(100)>1&&zoom.value<20);
zoom.set(1,null,200);assert.equal(zoom.target,20);zoom.set(1,null,2100);assert.equal(zoom.target,1);
ops.length=0;state({members:[{id:1,name:'Vlado',source:'UWB',meters:.6,angle:0}]});
let hub=ops.findIndex(op=>op[0]==='arc'&&op[1]===200&&op[2]===200&&Math.abs(op[3]-400*.412*.22)<.01);
let marker=ops.findIndex(op=>op[0]==='arc'&&Math.abs(op[3]-400*.018)<.01);assert.ok(hub>=0&&marker>hub,'Peer marker must be painted after TI hub');
state({members:[{id:1,name:'Vlado',source:'UWB',meters:.6,angle:null}]});assert.equal(elements.members.children[0].children[2].children[1].children[0].textContent,'SMJER NIJE IZMJEREN');
console.log('PASS: dynamic 0.5–2000 m scale, focus, zoom hysteresis, geometry, close-marker paint order and unknown direction');

state({unread:2});assert.equal(elements.pingLabel.textContent,"PING (2)");
