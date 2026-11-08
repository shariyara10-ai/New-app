/* CineZehn core: deterministic, UI-agnostic timeline and planner. */
export const uid = (p='id') => `${p}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2,8)}`;
export const clamp=(n,a,b)=>Math.min(b,Math.max(a,n));
export function seconds(stamp) {
  if (typeof stamp === 'number') return Math.max(0,stamp);
  const s=String(stamp??'').trim().replace(',','.');
  if (!/^\d+(?::\d{1,2}){0,2}(?:\.\d{1,3})?$/.test(s)) return null;
  let seg=s.split(':').map(Number); let v=0;
  for(const x of seg) v=v*60+x;
  return Number.isFinite(v)?v:null;
}
export function timestamp(sec) {let n=Math.max(0,Number(sec)||0);return `${String(Math.floor(n/60)).padStart(2,'0')}:${(n%60).toFixed(2).padStart(5,'0')}`;}
export function formatSrt(t) {const ms=Math.round(Math.max(0,t)*1000), h=Math.floor(ms/3600000),m=Math.floor(ms/60000)%60,s=Math.floor(ms/1000)%60;return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')},${String(ms%1000).padStart(3,'0')}`;}
export const clip=(kind,start,duration,mediaId=null,extra={})=>({id:uid('clip'),kind,start,duration,mediaId,srcIn:0,track:kind==='video'?0:kind==='audio'?6:kind==='caption'?3:1,name:kind.toUpperCase(),x:.5,y:.5,scale:1,opacity:1,motion:'Slow Push',transition:'Cut',text:'',fontSize:52,color:'#ffffff',...extra});
export function blankProject(name='Untitled Project', ratio='9:16'){
 return {version:2,id:uid('project'),name,ratio,duration:60,fps:30,created:Date.now(),updated:Date.now(),style:'Cinematic Documentary',assetCount:20,masterPrompt:'',transcript:[],assets:[],clips:[],media:{},flow:[],selectedId:null,watermark:false};
}
const Chocolate=[
 [0,1.5,'Mystery hook','Extreme macro of a ripe cocoa pod sliced open','Crash Zoom','CHOCOLATE','Deep boom and whoosh'],
 [1.5,3,'Mystery hook','Fresh white cocoa beans inside freshly opened pod','Slow Push','EK PHAL SE?!','Reverse whoosh'],
 [3,5,'Mystery hook','Glossy unbranded chocolate bar snapping apart in macro','Impact Zoom','HOW IS IT MADE?','Chocolate snap'],
 [5,7,'Harvesting','Wide cocoa plantation in humid tropical morning sunlight','Parallax','STEP 01 / HARVESTING','Air sweep'],
 [7,9,'Harvesting','Farmer cuts ripe yellow cocoa pod using safe harvesting tool','Punch In','HARVESTING','Cut'],
 [9,11,'Harvesting','Freshly split cocoa pod and white beans, high detail','Slow Push','COCOA BEANS','Whoosh'],
 [11,13,'Fermentation','Raw cocoa beans put into clean wooden fermentation boxes','Slide Right','STEP 02 / FERMENTATION','Wood movement'],
 [13,15.5,'Fermentation','Cocoa beans covered with banana leaves, natural time lapse','Slow Push','SEVERAL DAYS','Soft ticking'],
 [15.5,18,'Fermentation','Rich detailed macro of fermented cocoa beans','Orbit','FLAVOUR DEVELOPS','Riser'],
 [18,20,'Drying','Overhead view of cocoa beans drying on racks in sun','Zoom Out','STEP 03 / DRYING','Air'],
 [20,22,'Drying','Dry cocoa beans with warm sunlight and long shadows','Pan Left','MOISTURE ↓','Subtle sweep'],
 [22,24,'Drying','Dried cocoa beans collected into sacks','Punch In','READY FOR FACTORY','Cloth rustle'],
 [24,26,'Roasting and winnowing','Rotating industrial cocoa roasting drum with real machinery','Slow Push','STEP 04 / ROASTING','Machinery rumble'],
 [26,28.5,'Roasting and winnowing','Roasted cocoa beans on industrial conveyor','Slide Right','ROASTED BEANS','Mechanical click'],
 [28.5,31,'Roasting and winnowing','Cocoa bean cracking and shell separation showing nibs','Impact Zoom','SHELLS OUT / NIBS IN','Crack'],
 [31,33.5,'Grinding and mixing','Cocoa nibs entering an industrial grinder','Slow Push','STEP 05 / GRINDING','Grinder'],
 [33.5,36,'Grinding and mixing','Cocoa mass liquid flowing from industrial machine, macro','Slow Push','COCOA MASS','Thick flow'],
 [36,39,'Grinding and mixing','Sugar milk powder and cocoa butter carefully added to mixture','Punch In','SUGAR • MILK • COCOA BUTTER','3 pops'],
 [39,41.5,'Refining and conching','Factory chocolate refining equipment close-up','Slide Left','STEP 06 / REFINING','Machine hum'],
 [41.5,44,'Refining and conching','Industrial chocolate conching machine kneading rich cocoa','Slow Push','CONCHING','Machine loop'],
 [44,47,'Refining and conching','Smooth glossy chocolate ribbons flowing through machine','Slow Push','SILKY SMOOTH','Liquid swirl'],
 [47,49.5,'Tempering molding cooling','Controlled chocolate tempering equipment','Slow Push','STEP 07 / TEMPERING','Mechanical hum'],
 [49.5,52,'Tempering molding cooling','Liquid milk chocolate poured into bar molds overhead','Pan Right','MOLDING','Pouring'],
 [52,55,'Tempering molding cooling','Cooled chocolate bars released onto conveyor','Punch In','SHINE + SNAP','Release click'],
 [55,57,'Packaging and CTA','Unbranded chocolate bar packed on industrial line','Slow Push','FROM COCOA TO CHOCOLATE','Musical payoff'],
 [57,59,'Packaging and CTA','A beautifully glossy chocolate bar snapping in half','Impact Zoom',"WHAT'S MADE NEXT?",'Snap'],
 [59,60,'Packaging and CTA','Final macro of delicious milk chocolate bar with warm background','Zoom Out','COMMENT BELOW 🍫','Ending tone']
];
export const chocolateShots=()=>Chocolate.map(([start,end,scene,visual,motion,text,sfx],i)=>({id:`choc_${i+1}`,start,end,scene,visual,motion,text,sfx,transition:i===0?'Cut':(i%5===0?'Whip':'Match Cut')}));
export const STYLES=['Cinematic Documentary','Fast Retention','Kinetic Explainer','Motion Graphic Explainer','Clean Minimal','Product Commercial','Islamic History','Tech','Gaming','News','Finance','Tutorial','Custom'];
export const MOTIONS=['None','Slow Push','Zoom Out','Crash Zoom','Impact Zoom','Pan Left','Pan Right','Slide Left','Slide Right','Float','Orbit','Shake','Punch In','Parallax','Swipe In','Swipe Out','Point'];
export const TRANSITIONS=['Cut','Match Cut','Dissolve','Whip','Slide','Blur','Fade','Wipe'];
export const CAPTION_STYLES=['Karaoke Yellow','Pop Bold','Typewriter','Floating','Shaking','Masked Reveal','Cinematic Lower Third','Outline White','Minimal Subtitles','Bounce','Slide In','Static'];
export function parseEditBrief(raw) {
 const text=String(raw||'').replace(/\r/g,'');let output=[],scene='Untitled Scene';
 const lines=text.split('\n');let current=null;
 const sceneRe=/^\s*(?:SCENE|SHOT)\s*\d+\s*[—–:\-]\s*(.+?)(?:\s*\[.*)?$/i;
 const rangeRe=/(\d{1,2}(?::\d{1,2}){1,2}(?:[.,]\d{1,3})?)\s*(?:[–—\-]|→|to)\s*(\d{1,2}(?::\d{1,2}){1,2}(?:[.,]\d{1,3})?)/i;
 const push=()=>{if(current){current.visual=current.visual||`${current.scene} scene`;current.text=current.text||'';output.push(current);current=null;}};
 for(const line of lines){const lineTrim=line.trim();if(!lineTrim)continue;const m=lineTrim.match(sceneRe);if(m){push();scene=m[1].replace(/\s*\[.*/, '').trim();continue;}
 const range=lineTrim.match(rangeRe);if(range){const a=seconds(range[1]),b=seconds(range[2]);if(a!==null&&b!==null&&b>a){push();current={id:uid('shot'),scene,start:a,end:b,visual:'',motion:'Slow Push',text:'',sfx:'',transition:'Cut',graphics:'',bgm:''};continue;}}
 const field=lineTrim.match(/^(VISUAL|MOTION|GRAPHICS|TEXT|CAPTIONS|SFX|BGM|TRANSITION|EDITING)\s*:\s*(.*)/i);
 if(field&&current){const k=field[1].toUpperCase(),v=field[2];if(k==='GRAPHICS'||k==='CAPTIONS'||k==='TEXT'){current.text=v;current.graphics=v;}else if(k==='EDITING')current.editing=v;else current[k.toLowerCase()]=v;}
 }
 push();
 if(output.length===0 && raw.trim().startsWith('{')){try{const data=JSON.parse(raw);if(Array.isArray(data.cues)){output=data.cues.map(c=>({id:uid('shot'),start:seconds(c.start),end:seconds(c.end),scene:c.scene||'Scene',visual:c.visual||'',motion:c.motion||'Slow Push',text:c.text||c.graphics||'',sfx:c.sfx||'',transition:c.transition||'Cut'})).filter(c=>c.start!==null&&c.end>c.start);}}catch{}}
 const warnings=[];
 for(let i=0;i<output.length;i++){const c=output[i];if(c.end>36000)warnings.push(`Shot ${i+1}: exceeds 10h`);if(i&&c.start<output[i-1].end-0.01)warnings.push(`Shots ${i}–${i+1} overlap`);}
 return {shots:output,warnings};
}
export function slotsFromShots(shots,count=20,ratio='9:16',old=[]){
 const n=clamp(Math.round(Number(count)||20),1,100),dur=Math.max(1,...shots.map(s=>s.end||1)),preserved=new Map(old.map(x=>[x.id,x]));
 const a=[];
 for(let i=0;i<n;i++){
  const start=(i/n)*dur,end=((i+1)/n)*dur;let match=shots.find(s=>(s.start+s.end)/2>=start && (s.start+s.end)/2<end) || shots.find(s=>s.start<=start&&s.end>start) || shots[Math.min(shots.length-1,Math.floor(i/n*shots.length))];
  const id=`slot_${i+1}`;const prompt=`Professional ${ratio} cinematic documentary ${match?.scene||'scene'}. ${match?.visual||'Provide scene-accurate unbranded educational visual'}. Precise composition, natural texture, realistic lighting, clean background, no captions, no watermark, retain transparent alpha only for independent PNG overlays.`;
  const initial={id,index:i+1,start,end,scene:match?.scene||`Scene ${i+1}`,shotIds:shots.filter(s=>s.end>start&&s.start<end).map(s=>s.id),prompt,mediaId:null,hostEnabled:false,hostMediaId:null,hostMotion:'Float',primaryMotion:match?.motion||'Slow Push',captionStyle:'Karaoke Yellow',caption:match?.text||'',transition:match?.transition||'Cut',extras:Array.from({length:3},(_,j)=>({id:`${id}_extra${j+1}`,enabled:false,prompt:'',mediaId:null,motion:'Slide Left',opacity:1,overlays:Array.from({length:5},(_,k)=>({id:`${id}_ex${j+1}_ov${k+1}`,enabled:false,prompt:'',mediaId:null,motion:'Float',opacity:1}))})),overlays:Array.from({length:5},(_,j)=>({id:`${id}_ov${j+1}`,enabled:false,prompt:'',mediaId:null,motion:'Float',opacity:1}))};
  const prev=preserved.get(id);a.push(prev?{...initial,...prev,start,end,shotIds:initial.shotIds,prompt:prev.prompt||prompt}:initial);
 }
 return a;
}
export function timelineFromShots(shots,assets=[]){const clips=[];
 for(const [i,s] of shots.entries()){
  const a=assets.find(x=>s.start>=x.start&&s.start<x.end)||assets[i%Math.max(assets.length,1)];
  const pri=clip('video',s.start,s.end-s.start,a?.mediaId||null,{id:uid('shot'),name:s.scene,track:0,motion:s.motion,transition:s.transition,assetId:a?.id,description:s.visual});clips.push(pri);
  if(s.text)clips.push(clip('caption',s.start,s.end-s.start,null,{name:'Caption',text:s.text,track:3,motion:'None',style:'Karaoke Yellow'}));
  if(a?.hostEnabled&&a.hostMediaId)clips.push(clip('avatar',s.start,s.end-s.start,a.hostMediaId,{name:'Host',track:2,motion:a.hostMotion,x:.81,y:.73,scale:.42}));
  if(a){for(const [j,ov] of a.overlays.entries())if(ov.enabled&&ov.mediaId)clips.push(clip('overlay',s.start,s.end-s.start,ov.mediaId,{name:`Overlay ${j+1}`,track:1+j%2,motion:ov.motion}));
   for(const [j,ex] of a.extras.entries()){if(ex.enabled&&ex.mediaId)clips.push(clip('overlay',s.start,s.end-s.start,ex.mediaId,{name:`Extra ${j+1}`,track:1+j%2,motion:ex.motion,opacity:ex.opacity}));for(const [k,ov] of ex.overlays.entries())if(ov.enabled&&ov.mediaId)clips.push(clip('overlay',s.start,s.end-s.start,ov.mediaId,{name:`Extra ${j+1} • Overlay ${k+1}`,track:1+(j+k)%2,motion:ov.motion,opacity:ov.opacity}));}
  }
 }
 return clips;
}
export function parseSrt(source){const blocks=String(source).replace(/\r/g,'').trim().split(/\n\s*\n/);return blocks.flatMap((block,i)=>{const l=block.split('\n');const time=l.findIndex(x=>x.includes('-->'));if(time<0)return [];const t=l[time].split('-->').map(x=>seconds(x.trim()));if(t.length!==2||t.includes(null)||t[1]<=t[0])return [];return [{id:uid('cue'),start:t[0],end:t[1],text:l.slice(time+1).join('\n'),index:i+1}];});}
export function serializeSrt(cues){return [...cues].sort((a,b)=>a.start-b.start).map((c,i)=>`${i+1}\n${formatSrt(c.start)} --> ${formatSrt(c.end)}\n${c.text}`).join('\n\n')+'\n';}
export function serializeVtt(cues){return 'WEBVTT\n\n'+serializeSrt(cues).replace(/(\d{2}:\d{2}:\d{2}),(\d{3})/g,'$1.$2');}
export function clampProject(p){p.duration=clamp(Number(p.duration)||60,1,36000);p.assetCount=clamp(Number(p.assetCount)||20,1,100);p.clips=p.clips||[];p.assets=p.assets||[];p.flow=p.flow||[];p.transcript=p.transcript||[];p.media=p.media||{};return p;}
