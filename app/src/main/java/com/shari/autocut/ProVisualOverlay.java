package com.shari.autocut;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.text.*;
import android.util.LruCache;
import androidx.media3.effect.CanvasOverlay;
import java.io.*;
import java.util.*;

/**
 * Actual GPU Canvas composition: one editable main visual and up to five independently
 * animated user-imported PNG overlays per shot, optional host PNG or procedural host.
 * Frame composition is applied to video export AND canvas preview.
 */
public final class ProVisualOverlay extends CanvasOverlay {
 private final Context ctx;
 private final StudioProject project;
 private final LruCache<String,Bitmap> images;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final TextPaint type=new TextPaint(Paint.ANTI_ALIAS_FLAG);
 private final HashSet<String> missing=new HashSet<>();
 public ProVisualOverlay(Context ctx,StudioProject project){
  super(true);
  this.ctx=ctx.getApplicationContext();this.project=project;
  images=new LruCache<String,Bitmap>(50*1024*1024){
   @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
   @Override protected void entryRemoved(boolean evicted,String key,Bitmap old,Bitmap replacement){
    // Canvas may still be drawing an evicted bitmap; let GC handle lifecycle safely.
   }
  };
 }
 private Bitmap get(Uri uri){
  if(uri==null)return null;
  String key=uri.toString();
  Bitmap cached=images.get(key);
  if(cached!=null&&!cached.isRecycled())return cached;
  if(missing.contains(key))return null;
  try(InputStream in=ctx.getContentResolver().openInputStream(uri)){
   if(in==null){missing.add(key);return null;}
   BitmapFactory.Options opt=new BitmapFactory.Options();
   opt.inJustDecodeBounds=true;
   BitmapFactory.decodeStream(in,null,opt);
   int max=Math.max(opt.outWidth,opt.outHeight);
   int sample=1;while(max/sample>1200)sample*=2;
   try(InputStream raw=ctx.getContentResolver().openInputStream(uri)){
    BitmapFactory.Options sized=new BitmapFactory.Options();
    sized.inSampleSize=sample;sized.inPreferredConfig=Bitmap.Config.ARGB_8888;
    Bitmap out=BitmapFactory.decodeStream(raw,null,sized);
    if(out!=null){images.put(key,out);return out;}
   }
  }catch(Exception ignored){}
  missing.add(key);return null;
 }
 @Override public void onDraw(Canvas canvas,long timeUs){drawAt(canvas,timeUs/1000);}
 public void drawAt(Canvas canvas,long t){
  int w=canvas.getWidth(),h=canvas.getHeight();
  if(w<=0||h<=0)return;
  paint.setShader(new LinearGradient(0,0,w,h,
   new int[]{Color.rgb(10,12,24),Color.rgb(29,34,58),Color.rgb(10,12,25)},null,Shader.TileMode.CLAMP));
  paint.setAlpha(255);canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
  StudioProject.Shot active=null;int idx=0;
  for(int i=0;i<project.shots.size();i++){
   StudioProject.Shot s=project.shots.get(i);
   if(t>=s.startMs&&t<s.endMs){active=s;idx=i;break;}
  }
  if(active==null){
   // Playback can seek exactly to the end; do not invent extra media frames.
   return;
  }
  long local=t-active.startMs;
  float fraction=Math.min(1f,Math.max(0f,local/(float)active.duration()));
  Bitmap visual=get(active.visual);
  if(visual!=null){
   String transition=active.transition==null?"":active.transition.toLowerCase(Locale.ROOT);
   float transitionProgress=Math.min(1f,local/380f);
   boolean transitional=idx>0&&local<380&&
       (transition.contains("fade")||transition.contains("dissolve")||transition.contains("wipe")||transition.contains("swipe"));
   if(transitional){
    StudioProject.Shot previous=project.shots.get(idx-1);
    Bitmap previousImage=get(previous.visual);
    if(previousImage!=null)drawBackground(canvas,previousImage,previous.motion,1f);
    canvas.save();
    if(transition.contains("wipe")||transition.contains("swipe")){
     canvas.clipRect(0,0,w*transitionProgress,h);
     drawBackground(canvas,visual,active.motion,fraction);
    }else{
     paint.setAlpha((int)(255*transitionProgress));
     drawBackground(canvas,visual,active.motion,fraction,transitionProgress);
    }
    canvas.restore();
   }else drawBackground(canvas,visual,active.motion,fraction);
  }else drawAssetNeeded(canvas,idx,w,h);
  for(int layer=0;layer<active.overlaySlots;layer++){
   Bitmap overlay=get(active.overlays[layer]);
   if(overlay!=null)drawLayer(canvas,overlay,active.overlayX[layer],active.overlayY[layer],
     active.overlayScale[layer],active.overlayMotion[layer],fraction,layer);
  }
  if(active.hostEnabled){
   Bitmap host=get(active.host);
   if(host!=null)drawLayer(canvas,host,active.hostX,active.hostY,active.hostScale,
     active.hostMotion,fraction,6);
   else{
    FlowScene mock=new FlowScene(Uri.EMPTY,"Procedural Host",active.duration(),true);
    mock.caption=active.caption;
    AvatarPainter.draw(canvas,local,mock,project.style);
   }
  }
  if(project.addCaptions){
   String spoken=active.caption;
   if(!active.manualCaption&&!project.timedCaptions.isEmpty()){
    spoken="";
    for(CaptionSegment cue:project.timedCaptions)
     if(cue.startMs<=t&&t<cue.endMs){spoken=cue.text;break;}
   }
   drawCaption(canvas,spoken,fraction,active.captionStyle);
  }
  // Scene number overlay is a functional visual, not source footage replacement.
  if(!active.prompt.trim().isEmpty()){
   paint.setColor(0xAA151A30);canvas.drawRoundRect(14,16,160,51,13,13,paint);
   type.setTypeface(Typeface.DEFAULT_BOLD);type.setTextSize(16*Math.min(w/720f,h/1280f)+10);
   type.setTextAlign(Paint.Align.LEFT);type.setColor(Color.WHITE);
   canvas.drawText(String.format(Locale.US,"ASSET %02d / %02d",idx+1,project.shots.size()),24,40,type);
  }
 }
 private void drawAssetNeeded(Canvas c,int scene,int w,int h){
  paint.setColor(0xFF343A5E);c.drawRoundRect(w*.10f,h*.24f,w*.90f,h*.67f,22,22,paint);
  type.setColor(Color.WHITE);type.setTypeface(Typeface.DEFAULT_BOLD);
  type.setTextAlign(Paint.Align.CENTER);type.setTextSize(Math.max(18,w*.042f));
  c.drawText("ASSET "+(scene+1)+" NOT UPLOADED",w*.5f,h*.43f,type);
  type.setTextSize(Math.max(12,w*.026f));c.drawText("Import an image or return to the planner",w*.5f,h*.47f,type);
 }
 private void drawBackground(Canvas c,Bitmap bitmap,String motion,float t){
  drawBackground(c,bitmap,motion,t,1f);
 }
 private void drawBackground(Canvas c,Bitmap bitmap,String motion,float t,float opacity){
  float w=c.getWidth(),h=c.getHeight();
  float bw=bitmap.getWidth(),bh=bitmap.getHeight();
  float base=Math.max(w/bw,h/bh);
  float zoom=1f;
  if("Slow Zoom".equals(motion))zoom=1+.13f*t;
  else if("Zoom Out".equals(motion))zoom=1.16f-.16f*t;
  else if("Punch Zoom".equals(motion))zoom=1+.24f*(float)Math.exp(-t*10);
  else if("Shake".equals(motion))zoom=1.09f;
  else if("Pan Left".equals(motion)||"Pan Right".equals(motion))zoom=1.12f;
  float sizeW=bw*base*zoom,sizeH=bh*base*zoom;
  float shiftX=0,shiftY=0;
  if("Pan Left".equals(motion))shiftX=(t-.5f)*w*.10f;
  if("Pan Right".equals(motion))shiftX=(.5f-t)*w*.10f;
  if("Shake".equals(motion)&&t<.25f){
   shiftX=(float)Math.sin(t*130)*w*.014f;shiftY=(float)Math.cos(t*112)*h*.009f;
  }
  if("Float".equals(motion))shiftY=(float)Math.sin(t*6)*h*.007f;
  paint.setColor(Color.WHITE);paint.setAlpha((int)(255*opacity*("Fade".equals(motion)?Math.min(1,t*5):1)));
  RectF destination=new RectF((w-sizeW)*.5f+shiftX,(h-sizeH)*.5f+shiftY,
     (w+sizeW)*.5f+shiftX,(h+sizeH)*.5f+shiftY);
  c.save();c.clipRect(0,0,w,h);
  if("Rotate".equals(motion))c.rotate((t-.5f)*4,w*.5f,h*.5f);
  c.drawBitmap(bitmap,null,destination,paint);c.restore();paint.setAlpha(255);
 }
 private void drawLayer(Canvas c,Bitmap bitmap,float x,float y,float scale,String motion,float t,int layer){
  float w=c.getWidth(),h=c.getHeight();
  float desiredW=w*Math.max(.03f,Math.min(1.5f,scale));
  float desiredH=desiredW*bitmap.getHeight()/Math.max(1f,bitmap.getWidth());
  float centerX=Math.max(-.5f,Math.min(1.5f,x))*w;
  float centerY=Math.max(-.5f,Math.min(1.5f,y))*h;
  float alpha=1,zoom=1,rotation=0,shiftX=0,shiftY=0;
  if("Float".equals(motion))shiftY=(float)Math.sin(t*9+layer)*h*.012f;
  else if("Slow Zoom".equals(motion))zoom=.93f+.13f*t;
  else if("Zoom Out".equals(motion))zoom=1.17f-.17f*t;
  else if("Punch Zoom".equals(motion))zoom=1+.31f*(float)Math.exp(-t*15);
  else if("Bounce".equals(motion))zoom=1+.17f*(float)Math.sin(t*13)*(1-t);
  else if("Shake".equals(motion)&&t<.35f)shiftX=(float)Math.sin(t*150)*w*.012f;
  else if("Slide In".equals(motion))shiftX=-w*.4f*(1-Math.min(1,t*6));
  else if("Swipe Out".equals(motion))shiftX=w*.5f*Math.max(0,(t-.72f)/.28f);
  else if("Pan Left".equals(motion))shiftX=-w*.12f*t;
  else if("Pan Right".equals(motion))shiftX=w*.12f*t;
  else if("Rotate".equals(motion))rotation=(t-.5f)*18;
  else if("Fade".equals(motion))alpha=Math.min(1,t*6);
  else if("Point".equals(motion))shiftX=(float)Math.sin(t*12)*w*.025f;
  desiredW*=zoom;desiredH*=zoom;
  paint.setAlpha((int)(255*alpha));
  RectF dest=new RectF(centerX-desiredW*.5f+shiftX,centerY-desiredH*.5f+shiftY,
    centerX+desiredW*.5f+shiftX,centerY+desiredH*.5f+shiftY);
  c.save();if(rotation!=0)c.rotate(rotation,centerX,centerY);
  c.drawBitmap(bitmap,null,dest,paint);c.restore();paint.setAlpha(255);
 }
 private void drawCaption(Canvas c,String raw,float t,String style){
  if(raw==null||raw.trim().isEmpty())return;
  float w=c.getWidth(),h=c.getHeight();
  float unit=Math.max(.55f,Math.min(w/720f,h/1280f));
  String value=raw.trim().replace('\n',' ');
  if("Typing".equals(style)){
   int chars=Math.max(1,Math.min(value.length(),(int)(value.length()*t*1.6f)));
   value=value.substring(0,chars);
  }
  type.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
  type.setTextSize(47f*unit);type.setTextAlign(Paint.Align.CENTER);
  type.setColor(Color.WHITE);
  float maxWidth=w*.88f;
  ArrayList<String> lines=new ArrayList<>();StringBuilder current=new StringBuilder();
  for(String word:value.split("\\s+")){
   String candidate=current.length()==0?word:current+" "+word;
   if(type.measureText(candidate)>maxWidth&&current.length()>0){
    lines.add(current.toString());current=new StringBuilder(word);
   }else{if(current.length()>0)current.append(" ");current.append(word);}
  }
  if(current.length()>0)lines.add(current.toString());
  if(lines.size()>3)lines=new ArrayList<>(lines.subList(0,3));
  if(lines.isEmpty())return;
  float y=h*.82f;
  float up="Floating".equals(style)?(1-t)*18*unit:0;
  float scale="Pop".equals(style)?1+.10f*(1-Math.min(1,t*8)):1;
  float horizontal="Shake".equals(style)&&t<.17f?(float)Math.sin(t*120)*w*.015f:0;
  float lineHeight=type.getTextSize()*1.2f;
  c.save();c.translate(horizontal,-up);c.scale(scale,scale,w*.5f,y);
  paint.setColor(0xC90B101E);
  c.drawRoundRect(w*.04f,y-lines.size()*lineHeight-19*unit,w*.96f,y+22*unit,18*unit,18*unit,paint);
  type.setStyle(Paint.Style.STROKE);type.setStrokeWidth(6*unit);type.setColor(Color.BLACK);
  for(int i=0;i<lines.size();i++)
   c.drawText(lines.get(i),w*.5f,y-(lines.size()-i-1)*lineHeight,type);
  type.setStyle(Paint.Style.FILL);
  int primary="White Minimal".equals(style)?Color.WHITE:Color.rgb(255,218,73);
  type.setColor(primary);
  for(int i=0;i<lines.size();i++)
   c.drawText(lines.get(i),w*.5f,y-(lines.size()-i-1)*lineHeight,type);
  c.restore();
 }
}
