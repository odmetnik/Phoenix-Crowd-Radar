/* Pure radar geometry shared by the packaged UI and regression tests. */
(function(root){
 'use strict';
 const measured=m=>!m.stale&&Number.isFinite(m.meters)&&m.meters>=0;
 function niceRange(distance){
  const need=Math.max(.5,distance*1.25),power=10**Math.floor(Math.log10(need));
  for(const step of [1,2,5,10])if(step*power>=need-1e-10)return step*power;
 }
 function fit(members,selected){
  const values=members.filter(measured),focus=values.find(m=>m.id===selected);
  return {metric:values.length>0,focused:!!focus,target:niceRange(focus?focus.meters:Math.max(0,...values.map(m=>m.meters)))};
 }
 function position(meters,angle,scale,radius){
  if(!Number.isFinite(meters)||meters<0||!Number.isFinite(angle)||!Number.isFinite(scale)||scale<=0)return null;
  const a=angle*Math.PI/180,d=Math.min(.96,meters/scale)*radius,label=Math.max(radius*.28,d);
  return {x:Math.sin(a)*d,y:-Math.cos(a)*d,lx:Math.sin(a)*label,ly:-Math.cos(a)*label,outside:meters>scale};
 }
 const numberFormats=[0,1,2].map(n=>new Intl.NumberFormat('hr-HR',{maximumFractionDigits:n}));
 function format(value){return numberFormats[value<1?2:value<10?1:0].format(value);}
 class Zoom{
  constructor(){this.value=10;this.target=10;this.ready=false;this.candidate=null;this.since=0;this.last=null;this.focus=null;}
  set(target,focus,now){
   if(!this.ready){this.ready=true;this.value=this.target=target;}
   if(focus!==this.focus||target>this.target){this.target=target;this.candidate=null;}
   else if(target<this.target){if(this.candidate!==target){this.candidate=target;this.since=now;}else if(now-this.since>=1800){this.target=target;this.candidate=null;}}
   else this.candidate=null;
   this.focus=focus;
  }
  tick(now){const dt=this.last==null?16:Math.max(0,Math.min(100,now-this.last));this.last=now;this.value+=(this.target-this.value)*(1-Math.exp(-dt/240));if(Math.abs(this.value-this.target)<this.target*.0001)this.value=this.target;return this.value;}
 }
 const api={measured,niceRange,fit,position,format,Zoom};if(typeof module==='object')module.exports=api;else root.RadarGeometry=api;
})(typeof window==='object'?window:globalThis);
