'use strict';
const $=id=>document.getElementById(id),cyan='#31f1f6',green='#65f8b2',amber='#ffcb4d';
let state={group:'MOJA GRUPA',hasGroup:false,demo:false,running:false,members:[],status:'Kreiraj grupu ili se pridruži kolegama.',heading:null,version:''},tab='radar';
function act(action,id){if(window.Phoenix)window.Phoenix.action(action,id==null?'':String(id));else{state.status='Pregled sučelja: ova radnja koristi Android na telefonu.';if(action==='demo'){state.demo=!state.demo;state.running=false;state.members=state.demo?demoMembers():[];}if(action==='radar')state.status='Pokretanje Bluetootha dostupno je u Android aplikaciji.';render();}}
function demoMembers(){return [{id:1,name:'Marko',source:'UWB',meters:12,angle:50,sim:true},{id:2,name:'Ana',source:'GPS',meters:38,angle:224,sim:true},{id:3,name:'Ivan',source:'BLE',meters:null,angle:null,sim:true}];}
function switchTab(next){tab=next;for(const name of ['radar','group','ping'])$(name+'Page').hidden=name!==tab;document.querySelectorAll('[data-tab]').forEach(b=>b.classList.toggle('active',b.dataset.tab===tab));window.scrollTo(0,0);}
document.addEventListener('click',e=>{const t=e.target.closest('button');if(!t)return;if(t.dataset.tab)switchTab(t.dataset.tab);else if(t.dataset.action)act(t.dataset.action);});
window.receiveState=next=>{state=next;render();};
function render(){
 $('badge').textContent=state.demo?'DEMO / KONCEPT':state.running?'RADAR AKTIVAN':'RADAR ZAUSTAVLJEN';$('badge').classList.toggle('demo',state.demo);
 $('event').textContent=(state.demo?'INTERLIBER':state.hasGroup?state.group:'NEMA GRUPE')+' · '+state.members.length+' ČLANA';
 $('status').textContent=state.status;$('start').textContent=state.running?'ZAUSTAVI RADAR':'POKRENI RADAR';$('start').classList.toggle('running',state.running);$('demo').textContent=state.demo?'ISKLJUČI DEMO':'DEMO IZGLEDA';
 $('version').textContent=state.version?'v'+state.version:'';$('gps').textContent=state.gps?'ISKLJUČI GPS DIJELJENJE':'UKLJUČI GPS DIJELJENJE';
 $('hardware').textContent=state.hardware||'';$('groupInfo').textContent=state.hasGroup?state.group+'\n'+(state.own||''):'Još nisi u grupi. Kreiraj je ili se pridruži pozivnicom.';
 $('pingTarget').textContent=state.selectedName?'Odabrano: '+state.selectedName:'Na radaru prvo odaberi člana.';
 $('scale').textContent=state.demo?'SIMULACIJA · NEMA STVARNIH MJERENJA':(state.heading==null?'KOMPAS —':'NAPRIJED '+String(Math.round(state.heading)).padStart(3,'0')+'° MAG')+' · '+(state.members.some(m=>Number.isFinite(m.meters)&&!m.stale)?'RADIJUS '+Math.max(10,Math.ceil(Math.max(0,...state.members.filter(m=>!m.stale).map(m=>Number.isFinite(m.meters)?m.meters:0))/10)*10)+' m':'BLE BEZ METARA I SMJERA');
 const list=$('members');list.replaceChildren();if(!state.members.length){const p=document.createElement('div');p.className='empty';p.textContent=state.running?'Tražim članove iste grupe… Na drugom telefonu pokreni radar.':'Pokreni radar za traženje članova. GRUPA otvara pozivnice i uparivanje.';list.append(p);}
 state.members.forEach((m,i)=>{const b=document.createElement('button');b.className='member'+(state.selected===m.id?' selected':'');b.style.setProperty('--tone',color(m));b.setAttribute('aria-label',m.name+', '+details(m)+(m.stale?', zadnje viđen':''));
 const num=document.createElement('span');num.className='number';num.textContent=String(i+1).padStart(2,'0');const name=document.createElement('span');name.className='name';name.textContent=m.name;
 const detail=document.createElement('span');detail.className='measure';const icon=document.createElement('img');icon.alt='';icon.src='icons/'+(m.source==='UWB'?'broadcast':m.source.startsWith('GPS')?'map-pin':'bluetooth')+'.svg';const text=document.createElement('span');text.textContent=m.stale?'ZADNJE VIĐEN':details(m);detail.append(icon,text);b.append(num,name,detail);b.onclick=()=>act('select',m.id);list.append(b);});
}
function details(m){return m.source+' · '+(Number.isFinite(m.meters)?(m.source.startsWith('GPS')?'~ ':'')+Number(m.meters.toFixed(1))+' m': 'SMJER NEPOZNAT');}
function color(m){return m.stale?'#5b808b':m.source==='UWB'?cyan:m.source.startsWith('GPS')?green:amber;}
const canvas=$('scope'),ctx=canvas.getContext('2d');let frame=0,anim=true;const reduced=matchMedia('(prefers-reduced-motion: reduce)');
function draw(time){
 const w=canvas.clientWidth,dpr=Math.min(window.devicePixelRatio||1,3);if(canvas.width!==Math.round(w*dpr)){canvas.width=Math.round(w*dpr);canvas.height=Math.round(w*dpr);}ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,w,w);
 const x=w/2,y=w/2,r=w*.412;const line=(a,b,c,d,col,width=1)=>{ctx.beginPath();ctx.strokeStyle=col;ctx.lineWidth=width;ctx.moveTo(a,b);ctx.lineTo(c,d);ctx.stroke();};const ring=(radius,col,width=1,glow=0)=>{ctx.beginPath();ctx.strokeStyle=col;ctx.lineWidth=width;ctx.shadowColor=col;ctx.shadowBlur=glow;ctx.arc(x,y,radius,0,Math.PI*2);ctx.stroke();ctx.shadowBlur=0;};
 ctx.save();ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.clip();ctx.fillStyle='#04232c66';ctx.fillRect(x-r,y-r,r*2,r*2);
 for(let i=-20;i<=20;i++){const d=i*r/14;line(x+d,y-r,x+d,y+r,'#1a69721c',.5);line(x-r,y+d,x+r,y+d,'#1a69721c',.5);}
 for(let i=0;i<12;i++){const a=i*Math.PI/6;line(x,y,x+r*Math.sin(a),y-r*Math.cos(a),'#24929b66',.6);}
 if(state.running||state.demo){const angle=reduced.matches?.7:(time%5500)/5500*Math.PI*2;for(let i=0;i<70;i++){const a=angle-i*.012;ctx.beginPath();ctx.moveTo(x,y);ctx.arc(x,y,r,a-Math.PI/2-.014,a-Math.PI/2);ctx.closePath();ctx.fillStyle='rgba(0,238,249,'+(.004+(1-i/70)*.026)+')';ctx.fill();}line(x,y,x+Math.sin(angle)*r,y-Math.cos(angle)*r,'#26eff5aa',1.2);}
 ctx.restore();for(let i=1;i<=4;i++)ring(r*i/4,i===4?cyan:'#32d6df88',i===4?1.5:.6,i===4?7:0);line(x-r,y,x+r,y,'#42e0e9aa',.7);line(x,y-r,x,y+r,'#42e0e9aa',.7);
 ctx.font=(w*.038)+'px Arial';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillStyle='#b8f9ff';
 for(let i=0;i<72;i++){const a=i*Math.PI/36,outer=r-(i%6===0?8:4);line(x+Math.sin(a)*r,y-Math.cos(a)*r,x+Math.sin(a)*outer,y-Math.cos(a)*outer,'#54f5fa',i%6===0?1.1:.6);if(i%6===0)ctx.fillText(i*5+'°',x+Math.sin(a)*(r+w*.044),y-Math.cos(a)*(r+w*.044));}
 const metric=state.members.some(m=>Number.isFinite(m.meters)&&!m.stale),scale=Math.max(10,Math.ceil(Math.max(0,...state.members.filter(m=>!m.stale).map(m=>Number.isFinite(m.meters)?m.meters:0))/10)*10);
 state.members.forEach((m,i)=>{if(m.stale)return;const col=color(m);if(Number.isFinite(m.meters)&&m.angle!=null){const rad=state.demo?(i===0?r*.51:r*.77):r*Math.min(.94,m.meters/scale),a=m.angle*Math.PI/180,px=x+Math.sin(a)*rad,py=y-Math.cos(a)*rad;ctx.shadowColor=col;ctx.shadowBlur=14;ctx.fillStyle=col;ctx.beginPath();ctx.arc(px,py,w*.018,0,Math.PI*2);ctx.fill();ctx.shadowBlur=0;ctx.strokeStyle=col;ctx.lineWidth=1.2;const bw=w*.075,bh=w*.045;ctx.beginPath();if(ctx.roundRect)ctx.roundRect(px+6,py-bh-7,bw,bh,4);else ctx.rect(px+6,py-bh-7,bw,bh);ctx.stroke();ctx.fillStyle=col;ctx.font='bold '+(w*.038)+'px Arial';ctx.fillText(String(i+1).padStart(2,'0'),px+6+bw/2,py-bh/2-7);}else if(Number.isFinite(m.meters)||!metric||state.demo){ring(state.demo?r*.84:Number.isFinite(m.meters)?r*Math.min(.94,m.meters/scale):r*.84,Number.isFinite(m.meters)?col:amber,1.8,14);}});
 ctx.fillStyle='#02151b';ctx.beginPath();ctx.arc(x,y,r*.22,0,Math.PI*2);ctx.fill();ring(r*.22,'#43e7ee',.8);
 ctx.save();ctx.translate(x,y-2);ctx.fillStyle=cyan;ctx.shadowColor=cyan;ctx.shadowBlur=15;ctx.beginPath();ctx.moveTo(0,-r*.12);ctx.lineTo(r*.08,r*.065);ctx.lineTo(0,r*.035);ctx.lineTo(-r*.08,r*.065);ctx.closePath();ctx.fill();ctx.restore();ctx.fillStyle='#bafaff';ctx.font='bold '+(w*.050)+'px Arial';ctx.fillText('TI',x,y+r*.14);
 if(!state.running&&!state.demo){ctx.fillStyle='#7fa7af';ctx.font=(w*.025)+'px monospace';ctx.fillText('RADAR ZAUSTAVLJEN',x,y-r*.45);}
 if(anim)frame=requestAnimationFrame(draw);
}
document.addEventListener('visibilitychange',()=>{anim=!document.hidden;cancelAnimationFrame(frame);if(anim)frame=requestAnimationFrame(draw);});render();frame=requestAnimationFrame(draw);
if(window.Phoenix)act('ready');
