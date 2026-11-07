package com.shari.autocut;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import androidx.media3.effect.CanvasOverlay;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Composition-wide, GPU-rendered procedural overlay for sequential Flow clips. */
public final class FlowGraphicsOverlay extends CanvasOverlay {
 private final List<FlowScene> items;
 private final String style;
 private final boolean graphics,captions,host,reaction;
 private final Paint p=new Paint(3);
 private final TextPaint type=new TextPaint(3);
 private final ArrayList<StaticLayout> captionLayouts=new ArrayList<>();
 private long totalDurationMs;
 public FlowGraphicsOverlay(List<FlowScene> scenes,String style){
  this(scenes,style,true,true,false,false);
 }
 public FlowGraphicsOverlay(List<FlowScene> scenes,String style,boolean graphics,boolean captions){
  this(scenes,style,graphics,captions,false,false);
 }
 public FlowGraphicsOverlay(List<FlowScene> scenes,String style,boolean graphics,boolean captions,boolean host){
  this(scenes,style,graphics,captions,host,false);
 }
 public FlowGraphicsOverlay(List<FlowScene> scenes,String style,boolean graphics,boolean captions,boolean host,boolean reaction){
  super(true);
  items=new ArrayList<>(scenes);this.style=style;this.graphics=graphics;this.captions=captions;this.host=host;this.reaction=reaction;
  for(FlowScene s:items)totalDurationMs+=s.editingDurationMs();
 }
 private int color(){
  switch(style){
   case "Historical":return Color.rgb(222,175,83);
   case "Finance":return Color.rgb(78,213,150);
   case "Tech":return Color.rgb(57,188,237);
   case "Gaming":return Color.rgb(165,107,243);
   case "Comedy":return Color.rgb(255,194,63);
   case "High Energy":return Color.rgb(255,88,105);
   case "Whiteboard":return Color.rgb(24,106,201);
   case "Minimal":return Color.rgb(195,210,223);
   default:return Color.rgb(238,192,88);
  }
 }
 @Override public void onDraw(Canvas c,long presentationTimeUs){
  long t=Math.max(0,presentationTimeUs/1000);
  if(items.isEmpty()||t>=totalDurationMs||(!graphics&&!captions&&!host&&!reaction))return;
  long from=0;int idx=0;
  while(idx<items.size()-1 && t>=from+items.get(idx).editingDurationMs()){from+=items.get(idx).editingDurationMs();idx++;}
  FlowScene s=items.get(idx);
  long inScene=t-from;
  float w=c.getWidth(),h=c.getHeight(),u=Math.max(.35f,Math.min(w/720f,h/1280f));
  float entrance=Math.min(1f,inScene/370f);
  float exit=Math.min(1f,Math.max(0,s.editingDurationMs()-inScene)/300f);
  float appear=Math.max(0,Math.min(entrance,exit));
  int accent=color();
  if(graphics) {
  // Thin animated timeline across top; independent of screen ratio.
  p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(120,16,20,26));
  c.drawRect(0,0,w,Math.max(5f,7f*u),p);
  p.setColor(accent);c.drawRect(0,0,w*Math.min(1,t/(float)Math.max(1,totalDurationMs)),Math.max(5f,7f*u),p);
  // Scene counter.
  p.setColor(Color.argb(160,5,11,20));
  float pad=24*u;
  c.drawRoundRect(new RectF(pad,43*u,182*u,99*u),18*u,18*u,p);
  type.setColor(Color.WHITE);type.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);type.setTextSize(23*u);
  c.drawText(String.format(Locale.US,"SCENE %02d/%02d",idx+1,items.size()),39*u,78*u,type);
  // Animated topic ornament, not stock asset. Only draw when text meaning maps to known tags.
  String topic=CreativeDirector.topic(s.caption);
  float cx=w*.78f,cy=h*.27f+12*u*(float)Math.sin(inScene/440.0);
  p.setColor(Color.argb((int)(70*appear),Color.red(accent),Color.green(accent),Color.blue(accent)));
  c.drawCircle(cx,cy,78*u+8*u*(float)Math.sin(inScene/700.0),p);
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5*u);p.setColor(accent);
  if(topic.equals("YouTube creator topic")){
   p.setStyle(Paint.Style.FILL);
   p.setColor(Color.rgb(225,42,50));
   c.drawRoundRect(new RectF(cx-47*u,cy-31*u,cx+47*u,cy+31*u),14*u,14*u,p);
   p.setColor(Color.WHITE);
   Path play=new Path();play.moveTo(cx-9*u,cy-18*u);play.lineTo(cx+18*u,cy);play.lineTo(cx-9*u,cy+18*u);play.close();
   c.drawPath(play,p);
  }else if(topic.equals("Earnings")){
   c.drawCircle(cx,cy,32*u,p);
   type.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);type.setTextSize(45*u);type.setColor(accent);
   c.drawText("$",cx-14*u,cy+15*u,type);
  }else if(topic.equals("Three-point explanation")){
   for(int z=0;z<3;z++)c.drawCircle(cx+(z-1)*35*u,cy,13*u,p);
  }else if(topic.equals("Question")){
   type.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);type.setTextSize(80*u);type.setColor(accent);
   c.drawText("?",cx-24*u,cy+25*u,type);
  }else if(topic.equals("Historical narration")){
   c.drawLine(cx-40*u,cy+30*u,cx+40*u,cy+30*u,p);
   c.drawLine(cx,cy-40*u,cx,cy+30*u,p);
  }else if(topic.equals("Warning")){
   Path triangle=new Path();triangle.moveTo(cx,cy-40*u);triangle.lineTo(cx+40*u,cy+35*u);triangle.lineTo(cx-40*u,cy+35*u);triangle.close();
   c.drawPath(triangle,p);
  }
  p.setStyle(Paint.Style.FILL);
  }
  if(host) AvatarPainter.draw(c,inScene,s,style);
  if(reaction) ReactionPainter.draw(c,inScene,s);
  if(!captions)return;
  String activeText=s.caption==null?"":s.caption.trim();
  if(!s.subtitles.isEmpty()){
   activeText="";
   long position=s.trimStartMs+inScene;
   for(CaptionSegment sub:s.subtitles)
    if(sub.startMs<=position&&position<sub.endMs){activeText=sub.text;break;}
  }
  if(activeText.isEmpty())return;
  float captionAt=h*.79f;
  float space=w*.86f;
  type.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
  type.setTextSize(46*u);type.setColor(Color.WHITE);
  int maxWidth=Math.max(100,(int)space);
  // Build dynamic text layout for bi-directional Urdu/English captions.
  StaticLayout layout=StaticLayout.Builder.obtain(activeText,0,activeText.length(),type,maxWidth)
   .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(3).build();
  float ch=layout.getHeight();
  float top=Math.min(h-25*u-ch,captionAt-ch/2f);
  p.setColor(Color.argb((int)(196*appear),7,12,23));
  c.drawRoundRect(new RectF((w-space)/2f-8*u,top-17*u,(w+space)/2f+8*u,top+ch+22*u),20*u,20*u,p);
  p.setColor(accent);c.drawRoundRect(new RectF(w*.34f,top+ch+13*u,w*.66f,top+ch+19*u),3*u,3*u,p);
  c.save();c.translate((w-space)/2f,top);
  layout.draw(c);c.restore();
 }
}
