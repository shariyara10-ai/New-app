package com.shari.autocut;
import android.graphics.*;
import java.util.Locale;

/** Original procedural comic reactions. No copyrighted meme videos are embedded. */
public final class ReactionPainter {
 private ReactionPainter(){}
 private static final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 public static void draw(Canvas c,long elapsed,FlowScene scene){
  if(scene==null||scene.caption==null||elapsed>1800)return;
  String s=scene.caption.toLowerCase(Locale.ROOT);
  boolean surprise=s.matches("(?s).*(wow|shock|unbelievable|seriously|really|what|crazy|omg| حیران |धक्का).*");
  boolean funny=s.matches("(?s).*(haha|funny|laugh|comedy|joke|meme|😂|lol|ہاہا).*");
  if(!funny&&!surprise)return;
  float w=c.getWidth(),h=c.getHeight(),unit=Math.min(w/720f,h/1280f);
  float reveal=Math.min(1f,elapsed/250f);
  float angle=(float)Math.sin(elapsed/150.0)*.07f;
  c.save();c.translate(w*.20f,h*.25f);
  c.scale(unit*Math.max(.45f,reveal),unit*Math.max(.45f,reveal));
  c.rotate(angle*25);
  p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(215,19,27,47));
  c.drawRoundRect(-95,-96,95,106,28,28,p);
  p.setColor(funny?Color.rgb(255,211,74):Color.rgb(255,170,92));c.drawCircle(0,-14,70,p);
  p.setColor(Color.rgb(40,37,56));
  if(funny){
   c.drawOval(-42,-31,-13,-4,p);c.drawOval(13,-31,42,-4,p);
   p.setColor(Color.rgb(120,40,54));c.drawArc(-38,-10,38,43,0,180,true,p);
  }else{
   c.drawOval(-37,-47,-14,-5,p);c.drawOval(14,-47,37,-5,p);
   p.setColor(Color.rgb(120,40,54));c.drawOval(-17,1,17,43,p);
  }
  p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);
  p.setColor(Color.WHITE);p.setTextSize(22);
  c.drawText(funny?"LOL!":"WHAT?!",0,84,p);
  c.restore();
 }
}