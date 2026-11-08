import {clamp} from './core.js';
const DB_NAME='cinezehn_v2_media',STORE='blobs';
export function openDatabase(){return new Promise((resolve,reject)=>{const req=indexedDB.open(DB_NAME,2);req.onupgradeneeded=()=>{const d=req.result;if(!d.objectStoreNames.contains(STORE))d.createObjectStore(STORE);};req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);});}
export async function putBlob(id,blob){const d=await openDatabase();return new Promise((resolve,reject)=>{const tx=d.transaction(STORE,'readwrite');tx.objectStore(STORE).put(blob,id);tx.oncomplete=()=>{d.close();resolve();};tx.onerror=()=>{d.close();reject(tx.error);};});}
export async function getBlob(id){const d=await openDatabase();return new Promise((resolve,reject)=>{const tx=d.transaction(STORE);const r=tx.objectStore(STORE).get(id);r.onsuccess=()=>{d.close();resolve(r.result||null);};r.onerror=()=>{d.close();reject(r.error);};});}
export async function deleteBlob(id){const d=await openDatabase();return new Promise((resolve,reject)=>{const tx=d.transaction(STORE,'readwrite');tx.objectStore(STORE).delete(id);tx.oncomplete=()=>{d.close();resolve();};tx.onerror=()=>{d.close();reject(tx.error);};});}
export function probeRecordFormat(){return ['video/mp4;codecs="avc1.42E01E,mp4a.40.2"','video/mp4','video/webm;codecs="vp9,opus"','video/webm;codecs="vp8,opus"','video/webm'].find(s=>window.MediaRecorder?.isTypeSupported(s))||null;}
export function downloadData(blob,filename){if(window.CineZehnNative?.beginSave){saveToAndroid(blob,filename).catch(e=>alert(`Save failed: ${e.message}`));return;}const link=document.createElement('a');link.href=URL.createObjectURL(blob);link.download=filename;document.body.append(link);link.click();setTimeout(()=>{URL.revokeObjectURL(link.href);link.remove();},3000);}
async function saveToAndroid(blob,filename){const bridge=window.CineZehnNative;const mime=blob.type||'application/octet-stream';const key=bridge.beginSave(filename,mime);if(!key)throw Error('Android output file could not open');const slice=180*1024;try{for(let p=0;p<blob.size;p+=slice){const chunk=blob.slice(p,p+slice);const buf=new Uint8Array(await chunk.arrayBuffer());let binary='';for(let j=0;j<buf.length;j+=8192)binary+=String.fromCharCode(...buf.slice(j,j+8192));if(!bridge.appendSave(key,btoa(binary)))throw Error('Android storage write failed');}bridge.endSave(key,true);}catch(e){bridge.endSave(key,false);throw e;}}
const mediaType = m => m?.type?.startsWith('audio/')?'audio':m?.type?.startsWith('video/')?'video':'image';
export class MediaEngine{
 constructor(canvas){this.canvas=canvas;this.ctx=canvas.getContext('2d',{alpha:false});this.project=null;this.urls=new Map();this.elements=new Map();this.playing=false;this.t=0;this.lastFrame=0;this.timer=0;this.audctx=null;this.audioDestination=null;this.gains=new Map();this.onTime=null;this.onEnd=null;this.onProgress=null;this.exporting=false;this.recording=null;this.exportAbort=false;}
 setProject(p){this.pause();this.project=p;const [w,h]=this.dims(480);this.canvas.width=w;this.canvas.height=h;this.canvas.classList.toggle('landscape',w>h);this.draw();}
 dims(longest=1080){const ratio=this.project?.ratio||'9:16';const [a,b]=ratio.split(':').map(Number);if(a>=b)return [longest,Math.round(longest*b/a)];return [Math.round(longest*a/b),longest];}
 async loadMedia(p){this.project=p;const ids=Object.keys(p.media||{});for(const id of ids){if(this.urls.has(id))continue;const blob=await getBlob(id);if(blob)this.urls.set(id,URL.createObjectURL(blob));}this.elements.forEach(e=>e.pause?.());this.elements.clear();this.draw();}
 async ingest(file){const id=`media_${Date.now()}_${Math.random().toString(36).slice(2,7)}`;await putBlob(id,file);this.urls.set(id,URL.createObjectURL(file));this.project.media[id]={id,name:file.name||'Imported media',type:file.type||'image/png',size:file.size,created:Date.now()};return id;}
 elementFor(c){if(!c.mediaId||!this.urls.has(c.mediaId))return null;let el=this.elements.get(c.id);if(el)return el;const m=this.project.media[c.mediaId];const type=mediaType(m);if(type==='image'){el=new Image();el.src=this.urls.get(c.mediaId);el.onload=()=>this.draw();}
 else{el=document.createElement(type==='audio'?'audio':'video');el.src=this.urls.get(c.mediaId);el.preload='auto';el.playsInline=true;el.setAttribute('playsinline','true');el.crossOrigin='anonymous';el.muted=false;el.onloadeddata=()=>this.draw();}this.elements.set(c.id,el);return el;}
 get active(){return (this.project?.clips||[]).filter(c=>this.t>=c.start&&this.t<c.start+c.duration).sort((a,b)=>(a.track-b.track)||(a.start-b.start));}
 async prepareAudio(){if(!this.audctx)this.audctx=new (window.AudioContext||window.webkitAudioContext)();if(this.audctx.state==='suspended')await this.audctx.resume();if(!this.audioDestination)this.audioDestination=this.audctx.createMediaStreamDestination();for(const c of this.project.clips){if(!c.mediaId)continue;const m=this.project.media[c.mediaId];if(mediaType(m)==='image')continue;const el=this.elementFor(c);if(this.gains.has(c.id)||!el)continue;try{const node=this.audctx.createMediaElementSource(el),g=this.audctx.createGain();node.connect(g);g.connect(this.audioDestination);this.gains.set(c.id,g);}catch(e){console.warn('Audio routing limited',e);}}this.syncAudio();}
 syncAudio(){for(const c of this.project?.clips||[]){const g=this.gains.get(c.id);if(g)g.gain.value=(this.t>=c.start&&this.t<c.start+c.duration&&!c.muted)?clamp(Number(c.volume??1),0,3):0;}}
 syncMedia(){if(!this.project)return;const t=this.t;
 for(const c of this.project.clips){if(!c.mediaId)continue;const meta=this.project.media[c.mediaId];if(mediaType(meta)==='image')continue;const el=this.elementFor(c);if(!el)continue;const active=t>=c.start&&t<c.start+c.duration;const local=clamp((c.srcIn||0)+(t-c.start)*(c.speed||1),0,Math.max(.1,el.duration||99999));if(active){if(Number.isFinite(el.duration)&&Math.abs(el.currentTime-local)>.28){try{el.currentTime=local;}catch{}}if(this.playing&&el.paused)el.play().catch(()=>{});if(!this.playing&&!el.paused)el.pause();}else if(!el.paused)el.pause();}
 this.syncAudio();}
 motion(c,progress){const mot=(c.motion||'None').toLowerCase();let sc=c.scale??1,x=c.x??.5,y=c.y??.5,rot=c.rotation??0;const p=clamp(progress,0,1);
 if(/slow push|parallax/.test(mot))sc*=1+.13*p;
 if(/zoom out/.test(mot))sc*=1.14-.14*p;
 if(/crash zoom/.test(mot))sc*=1+.25* Math.min(1,p*4);
 if(/impact zoom|punch in/.test(mot))sc*=1+.16*Math.max(0,1-p*4);
 if(/slide left|pan left/.test(mot))x+=.11*(1-p);
 if(/slide right|pan right/.test(mot))x-=.11*(1-p);
 if(/swipe in/.test(mot))x+=.55*(1-Math.min(1,p*6));
 if(/swipe out/.test(mot))x-=.55*Math.max(0,(p-.84)/.16);
 if(/float/.test(mot))y+=.012*Math.sin(p*2*Math.PI);
 if(/shake/.test(mot))x+=.006*Math.sin(p*173);
 if(/orbit/.test(mot))rot+=.6*Math.sin(p*Math.PI*2);
 return {sc,x,y,rot};}
 drawAsset(el,c,t){const ctx=this.ctx;const W=this.canvas.width,H=this.canvas.height;let w=el?.videoWidth||el?.naturalWidth||0,h=el?.videoHeight||el?.naturalHeight||0;if(!w||!h)return;
 const prog=(t-c.start)/Math.max(.1,c.duration),{sc,x,y,rot}=this.motion(c,prog);
 const layer=(c.kind==='video')?1:(c.kind==='avatar'?.48:.68);
 const fw=W*layer,fh=H*layer;const cover=Math.max(fw/w,fh/h)*sc;const iw=w*cover,ih=h*cover;ctx.save();ctx.globalAlpha=clamp(Number(c.opacity??1),0,1);ctx.translate(x*W,y*H);ctx.rotate(rot*Math.PI/180);ctx.drawImage(el,-iw/2,-ih/2,iw,ih);ctx.restore();}
 drawCaption(c,t){const ctx=this.ctx,W=this.canvas.width,H=this.canvas.height;textDraw(ctx,c,W,H,(t-c.start)/Math.max(.1,c.duration));}
 draw(t=this.t){if(!this.project)return;const ctx=this.ctx,W=this.canvas.width,H=this.canvas.height;ctx.fillStyle='#100d18';ctx.fillRect(0,0,W,H);
 const base=ctx.createLinearGradient(0,0,W,H);base.addColorStop(0,'#302049');base.addColorStop(.45,'#291936');base.addColorStop(1,'#131525');ctx.fillStyle=base;ctx.fillRect(0,0,W,H);
 let primary=false;let active=(this.project.clips||[]).filter(c=>t>=c.start&&t<c.start+c.duration).sort((a,b)=>a.track-b.track);
 for(const c of active){if(c.kind==='caption'||c.kind==='text'){this.drawCaption(c,t);continue;}if(c.kind==='audio'||c.kind==='sfx')continue;const el=this.elementFor(c);if(!el)continue;if(c.kind==='video')primary=true;this.drawAsset(el,c,t);}
 if(!primary){ctx.fillStyle='#ffffff13';ctx.textAlign='center';ctx.font=`bold ${Math.round(W*.09)}px system-ui`;ctx.fillText('CINEZEHN',W/2,H/2-30);ctx.fillStyle='#ffffff88';ctx.font=`${Math.round(W*.042)}px system-ui`;ctx.fillText('Import visual media for this scene',W/2,H/2+14);}
 this.ctx.textAlign='left';}
 seek(t){if(!this.project)return;this.t=clamp(t,0,this.project.duration||60);this.syncMedia();this.draw();this.onTime?.(this.t);}
 async play(){if(this.playing)return;await this.prepareAudio().catch(()=>{});this.playing=true;this.lastFrame=performance.now();this.syncMedia();this.tick();}
 tick=()=>{if(!this.playing)return;const now=performance.now();const elapsed=Math.min(.12,(now-this.lastFrame)/1000);this.lastFrame=now;this.t+=elapsed;if(this.t>=(this.project?.duration||60)){this.t=this.project.duration;this.draw();this.pause();this.onEnd?.();return;}this.syncMedia();this.draw();this.onTime?.(this.t);this.timer=requestAnimationFrame(this.tick);}
 pause(){this.playing=false;cancelAnimationFrame(this.timer);for(const el of this.elements.values())el.pause?.();this.syncAudio();}
 async export(output={}){if(!this.project)throw Error('No project');if(!window.MediaRecorder)throw Error('MediaRecorder unavailable on this device');if(!this.canvas.captureStream)throw Error('Canvas stream unavailable');const mime=probeRecordFormat();if(!mime)throw Error('This browser does not support video export');if(this.exporting)throw Error('Already exporting');
 this.pause();this.exporting=true;this.exportAbort=false;const originalT=this.t;let chunks=[],recorder=null;const size=output.resolution==='1080p'?1080:720;const [w,h]=this.dims(size);this.canvas.width=w;this.canvas.height=h;this.seek(0);await this.prepareAudio();const videoStream=this.canvas.captureStream(this.project.fps||30);const audioTracks=this.audioDestination?.stream.getAudioTracks()||[];const stream=new MediaStream([...videoStream.getVideoTracks(),...audioTracks]);
 try{recorder=new MediaRecorder(stream,{mimeType:mime,videoBitsPerSecond:size===1080?9500000:4800000,audioBitsPerSecond:160000});this.recording=recorder;const complete=new Promise((resolve,reject)=>{recorder.ondataavailable=e=>{if(e.data?.size)chunks.push(e.data);};recorder.onerror=e=>reject(e.error||Error('Recorder error'));recorder.onstop=()=>resolve(new Blob(chunks,{type:mime.split(';')[0]}));});recorder.start(1000);await this.play();const start=performance.now(),dur=this.project.duration;while(this.playing&&!this.exportAbort){await new Promise(r=>setTimeout(r,220));this.onProgress?.(Math.round(this.t/dur*100));if(performance.now()-start>(dur+7)*1000){this.pause();break;}}if(this.exportAbort)this.pause();recorder.stop();const blob=await complete;if(this.exportAbort)throw Error('Export cancelled');const ext=mime.startsWith('video/mp4')?'mp4':'webm';this.onProgress?.(100);return {blob,ext,mime};}
 finally{this.pause();for(const track of stream.getTracks())if(track.kind==='video')track.stop();this.canvas.width=this.dims(480)[0];this.canvas.height=this.dims(480)[1];this.seek(originalT);this.exporting=false;this.recording=null;}}
 abortExport(){this.exportAbort=true;}
}
function textDraw(ctx,c,W,H,p){let text=String(c.text||'').trim();if(!text)return;const fs=clamp(Number(c.fontSize)||52,18,160)*W/480;const lines=text.length>25?wrap(text,22):[text];const y=clamp(c.y??.78,.1,.95)*H,x=clamp(c.x??.5,.1,.9)*W;const motion=(c.style||c.motion||'').toLowerCase();let alpha=1,scale=1,dx=0,dy=0;
 if(/typing|typewriter/.test(motion))text=text.slice(0,Math.max(1,Math.floor(text.length*Math.min(1,p*4))));
 if(/pop|bounce/.test(motion))scale=p<.22?.87+.13*Math.sin(Math.min(1,p/.22)*Math.PI/2):1;
 if(/floating/.test(motion))dy=H*.012*Math.sin(p*Math.PI*2);
 if(/shaking/.test(motion))dx=W*.004*Math.sin(p*142);
 if(/fade|masked/.test(motion))alpha=Math.min(1,p*7,(1-p)*8);
 ctx.save();ctx.translate(x+dx,y+dy);ctx.scale(scale,scale);ctx.globalAlpha=alpha;ctx.textAlign='center';ctx.textBaseline='middle';ctx.font=`900 ${fs}px system-ui, sans-serif`;ctx.lineJoin='round';ctx.miterLimit=2;ctx.strokeStyle='#110e1c';ctx.lineWidth=Math.max(3,fs*.15);const lineHeight=fs*1.12;const printed=wrap(text,Math.max(7,Math.round(400/(fs/(W/480)))));for(let i=0;i<printed.length;i++){const yy=(i-(printed.length-1)/2)*lineHeight;ctx.strokeText(printed[i],0,yy);ctx.fillStyle=c.style==='Karaoke Yellow'?'#ffd735':(c.color||'#ffffff');ctx.fillText(printed[i],0,yy);}ctx.restore();}
function wrap(text,max){const words=text.split(/\s+/);let lines=[],line='';for(const word of words){if((line+' '+word).trim().length>max&&line){lines.push(line);line=word;}else line=(line+' '+word).trim();}if(line)lines.push(line);return lines.slice(0,3);}
