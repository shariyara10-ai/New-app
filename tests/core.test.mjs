import test from 'node:test';
import assert from 'node:assert/strict';
import {seconds,timestamp,chocolateShots,slotsFromShots,timelineFromShots,parseEditBrief,parseSrt,serializeSrt,blankProject} from '../web/core.js';
test('timestamps: decimal seconds and SRT comma milliseconds',()=>{
 assert.equal(seconds('00:01.5'),1.5);
 assert.equal(seconds('00:00:01,500'),1.5);
 assert.equal(seconds('01:02.025'),62.025);
 assert.equal(seconds('garbage'),null);
 assert.equal(timestamp(3.25),'00:03.25');
});
test('Chocolate acceptance: exactly 27 shots + 20 primary slots',()=>{
 const shots=chocolateShots();assert.equal(shots.length,27);
 assert.equal(shots[0].start,0);assert.equal(shots.at(-1).end,60);
 const slots=slotsFromShots(shots,20,'9:16');assert.equal(slots.length,20);
 for(const asset of slots){assert.equal(asset.extras.length,3);assert.equal(asset.overlays.length,5);asset.extras.forEach(extra=>assert.equal(extra.overlays.length,5));assert.ok(asset.prompt.includes('9:16'));}
 const clips=timelineFromShots(shots,slots);
 assert.equal(clips.filter(x=>x.kind==='video').length,27);
 assert.equal(clips.filter(x=>x.kind==='caption').length,27);
});
test('planner input parses timestamped natural brief and typed JSON',()=>{
 const brief='SCENE 1 — HOOK [00:00–00:05]\n00:00–00:01.5\nVISUAL: Fresh cocoa pod\nMOTION: Crash Zoom 100% to 125%\nGRAPHICS: CHOCOLATE\nSFX: Boom\n\n00:01.5–00:03\nVISUAL: Cocoa beans\nMOTION: Slow Push';
 const result=parseEditBrief(brief);
 assert.equal(result.shots.length,2);
 assert.equal(result.shots[0].start,0);
 assert.equal(result.shots[0].end,1.5);
 assert.equal(result.shots[0].text,'CHOCOLATE');
 assert.equal(result.shots[0].sfx,'Boom');
 const typed=parseEditBrief(JSON.stringify({cues:[{start:'00:00',end:'00:05',visual:'Factory scene'}]}));
 assert.equal(typed.shots.length,1);
});
test('regen preserves imported PNG/host/overlay on stable slot ids',()=>{
 const shots=chocolateShots(),a=slotsFromShots(shots,20);
 a[4].mediaId='png_4';a[4].hostMediaId='host_4';a[4].hostEnabled=true;a[4].extras[0].overlays[2].mediaId='overlay_4';
 const b=slotsFromShots(shots,20,'9:16',a);
 assert.equal(b[4].mediaId,'png_4');assert.equal(b[4].hostEnabled,true);assert.equal(b[4].extras[0].overlays[2].mediaId,'overlay_4');
});
test('SRT parsing and export is time correct',()=>{
 const input='1\n00:00:01,250 --> 00:00:02,400\nYeh chocolate hai\n\n2\n00:00:03,500 --> 00:00:04,050\nKya aap jaante hain?';
 const cues=parseSrt(input);
 assert.equal(cues.length,2);assert.equal(cues[0].start,1.25);
 assert.ok(serializeSrt(cues).includes('00:00:01,250 --> 00:00:02,400'));
});
test('project model separates flow and editor collections',()=>{
 let x=blankProject();assert.notEqual(x.flow,x.clips);x.flow.push({mediaId:'clip_a'});assert.equal(x.clips.length,0);
});
