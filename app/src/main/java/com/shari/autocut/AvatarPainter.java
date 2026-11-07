package com.shari.autocut;

import android.graphics.*;
import java.util.Locale;

/**
 * Built-in ORIGINAL vector 2D explainer host. Works fully offline.
 * Uses scene-level transcript keywords for simple procedural gestures.
 * Mouth animation is a visual approximation, NOT phoneme/voice lip sync.
 */
public final class AvatarPainter {
 private static final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 private AvatarPainter(){}
 private static void fill(Canvas c,int color,float x,float y,float w,float h,float corner){
  p.setStyle(Paint.Style.FILL);p.setColor(color);
  c.drawRoundRect(x,y,x+w,y+h,corner,corner,p);
 }
 private static void line(Canvas c,int color,float width,float x1,float y1,float x2,float y2){
  p.setColor(color);p.setStrokeWidth(width);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
  c.drawLine(x1,y1,x2,y2,p);p.setStyle(Paint.Style.FILL);
 }
 public static void draw(Canvas canvas,long localTimeMs, FlowScene scene,String style){
  if(canvas==null||scene==null)return;
  float w=canvas.getWidth(),h=canvas.getHeight();
  float scale=Math.min(w/720f,h/1280f);
  scale=Math.max(.45f,scale);
  float unit=Math.min(w,h)*.00165f;
  if(w>h)unit=Math.min(w,h)*.0024f;
  unit=Math.max(.46f,unit);
  String words=scene.caption==null?"":scene.caption.toLowerCase(Locale.ROOT);
  boolean counting=words.matches("(?s).*(three|teen|3|تین|तीन).*");
  boolean pointing=words.matches("(?s).*(youtube|video|chart|screen|logo|earning|money|پیसे|पैसे).*");
  boolean speaking=scene.hasAudio && !words.trim().isEmpty();
  float pulse=(float)Math.sin(localTimeMs/190.0);
  canvas.save();
  float left=w*.69f,top=h*.30f;
  if(w>h){left=w*.76f;top=h*.27f;}
  float size=unit;
  canvas.translate(left,top);
  canvas.scale(size,size);
  final int skin=Color.rgb(223,174,136);
  final int jacket=Color.rgb(48,65,117);
  final int highlight=Color.rgb(89,124,220);
  final int outline=Color.rgb(23,40,73);
  // Accessible translucent backdrop separating host from footage.
  fill(canvas,Color.argb(175,15,25,48),-15,-17,157,219,21);
  // Torso with animated breathing.
  float rise=pulse*1.8f;
  fill(canvas,jacket,17,100+rise,101,91,21);
  fill(canvas,highlight,59,108+rise,16,72,6);
  // Neck and face.
  fill(canvas,skin,57,90,24,22,8);
  p.setColor(skin);canvas.drawOval(28,19+rise,103,103+rise,p);
  // Hair.
  p.setColor(Color.rgb(38,37,54));canvas.drawArc(29,15+rise,102,73+rise,178,190,true,p);
  fill(canvas,Color.rgb(38,37,54),26,37+rise,13,28,5);
  // Eyes/eyebrows
  p.setColor(outline);canvas.drawCircle(51,63+rise,3.2f,p);
  canvas.drawCircle(82,63+rise,3.2f,p);
  line(canvas,outline,2.6f,46,56+rise,56,55+rise);
  line(canvas,outline,2.6f,78,55+rise,87,56+rise);
  // Mouth moves only for text-present scenes; not phonetic accuracy.
  float open=speaking?2f+Math.abs((float)Math.sin(localTimeMs/160.0))*5.0f:1.5f;
  fill(canvas,Color.rgb(121,55,72),57,81+rise,18,open,6);
  // Left arm; props and hand motions reflect topic when available.
  line(canvas,jacket,17,30,121+rise,4,151+rise);
  p.setColor(skin);canvas.drawCircle(4,151+rise,9,p);
  if(pointing){
   line(canvas,jacket,15,107,122+rise,138,100+rise+pulse*4);
   line(canvas,skin,6,137,100+rise+pulse*4,149,86+rise+pulse*4);
   p.setColor(skin);canvas.drawCircle(137,100+rise+pulse*4,8,p);
  }else if(counting){
   line(canvas,jacket,15,108,122+rise,135,111+rise);
   p.setColor(skin);canvas.drawCircle(135,111+rise,11,p);
   for(int finger=0;finger<3;finger++)
    line(canvas,skin,5,128+finger*7,105+rise,124+finger*7,83+rise-(finger%2)*4);
  }else{
   line(canvas,jacket,15,107,122+rise,133,132+rise+pulse*5);
   p.setColor(skin);canvas.drawCircle(134,130+rise+pulse*5,9,p);
  }
  // Simple lower-third ID.
  fill(canvas,Color.argb(225,38,68,132),0,192,127,20,7);
  p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(11);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);
  canvas.drawText("CINEZEHN HOST",64,206,p);
  canvas.restore();
 }
}