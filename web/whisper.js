/** Real Whisper Tiny inference (browser ONNX). First use downloads model files; no demo transcript substitution. */
let cachedPipeline=null;
export async function whisperTranscribe(blob,{language='auto',onStatus=()=>{}}={}){
 onStatus('Loading Whisper Tiny runtime…');
 let lib;
 try{lib=await import('https://cdn.jsdelivr.net/npm/@huggingface/transformers@3.8.0/+esm');}
 catch(e){throw Error('Whisper runtime cannot load. Connect to the internet for first-time model setup, or import SRT manually. '+e.message);}
 lib.env.allowLocalModels=false;lib.env.useBrowserCache=true;
 const device='wasm';
 if(!cachedPipeline){onStatus('Downloading / checking Whisper Tiny multilingual model (~75 MB quantized, varies by runtime)…');
 cachedPipeline=await lib.pipeline('automatic-speech-recognition','onnx-community/whisper-tiny',{device,dtype:'q8',progress_callback:p=>{if(p.status==='progress')onStatus(`Whisper model ${Math.round(p.progress||0)}%: ${p.file||''}`);else if(p.status==='ready')onStatus('Whisper model ready');}});}
 onStatus('Decoding audio and resampling to 16 kHz…');
 const ctx=new (window.AudioContext||window.webkitAudioContext)();let decoded;
 try{decoded=await ctx.decodeAudioData(await blob.arrayBuffer());}catch(e){await ctx.close();throw Error('Audio decode failed: '+e.message);}
 const frames=Math.ceil(decoded.duration*16000);const offline=new OfflineAudioContext(1,frames,16000);const source=offline.createBufferSource();source.buffer=decoded;source.connect(offline.destination);source.start();const PCM=(await offline.startRendering()).getChannelData(0);await ctx.close();
 onStatus('Running on-device Whisper inference (time depends on length and phone CPU)…');
 const opts={return_timestamps:true,chunk_length_s:25,stride_length_s:5,task:'transcribe'};
 if(language!=='auto')opts.language=language;
 const out=await cachedPipeline(PCM,opts);
 const chunks=Array.isArray(out.chunks)?out.chunks:[];
 const cues=chunks.filter(c=>Array.isArray(c.timestamp)&&c.timestamp[0]!==null).map((c,i)=>({id:`whisper_${Date.now()}_${i}`,start:Math.max(0,c.timestamp[0]),end:Math.min(decoded.duration,c.timestamp[1]??Math.min(decoded.duration,c.timestamp[0]+3)),text:String(c.text||'').trim(),source:'Whisper Tiny'}));
 if(!cues.length&&out.text)cues.push({id:'whisper_full',start:0,end:decoded.duration,text:String(out.text).trim(),source:'Whisper Tiny — segment timing approximate'});
 return {text:out.text||'',cues,model:'onnx-community/whisper-tiny (q8)',inference:'local browser WASM; initial model download required',duration:decoded.duration};
}
