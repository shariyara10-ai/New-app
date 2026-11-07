package com.shari.autocut;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.media3.effect.CanvasOverlay;
import java.util.ArrayList;
import java.util.Locale;
public final class CaptionOverlay extends CanvasOverlay {
 private final ArrayList<CaptionSegment> segments;
 private final String preset;
 private final Bitmap sticker;
 private final long startMs;
 private final Paint paint=new Paint(3);
 public CaptionOverlay(ArrayList<CaptionSegment> segments,String preset,Bitmap sticker,long clipStart) {
  super(true);
  this.segments=segments;this.preset=preset;this.sticker=sticker;this.startMs=clipStart;
 }
 @Override public void onDraw(Canvas c,long ptsUs) {
  long now=ptsUs/1000L+startMs;
  CaptionSegment active=null;
  for(CaptionSegment s:segments)if(s.startMs<=now&&now<s.endMs){active=s;break;}
  if(active==null)return;
  float w=c.getWidth(),h=c.getHeight();
  float unit=Math.min(w/720f,h/1280f);
  String style=active.style;
  long elapsed=now-active.startMs;
  float punch=(float)Math.max(0,1-elapsed/280f);
  float scale=1f+0.12f*punch;
  float font= "documentary".equals(preset) ? 49f : 59f;
  font*=unit;
  if("cinema".equals(style))font=48f*unit;
  if("title".equals(style))font=70f*unit;
  if("numbers".equals(style))font=67f*unit;
  String[] lines=wrap(active.text,Math.max(12,(int)(w/(font*0.53f))));
  float lineHeight=font*1.18f;
  float block=lines.length*lineHeight;
  float y="documentary".equals(preset)?h*0.77f:h*0.78f;
  if("title".equals(style))y=h*0.52f;
  float top=y-block/2f-font*0.72f;
  paint.setStyle(Paint.Style.FILL);
  paint.setColor(Color.argb(210,9,15,28));
  float pad=24f*unit;
  c.drawRoundRect(new RectF(Math.max(14f,w*0.04f),top-pad,Math.min(w-14f,w*0.96f),top+block+pad),23f*unit,23f*unit,paint);
  float underline=w*0.36f;
  paint.setColor(("warning".equals(style))?Color.rgb(255,70,73):Color.rgb(255,204,74));
  c.drawRoundRect(new RectF((w-underline)/2f,top+block+pad-5f*unit,(w+underline)/2f,top+block+pad+3f*unit),4,4,paint);

  if(sticker!=null && (now-active.startMs)<Math.min(1900,active.endMs-active.startMs)) {
    float maxW=w*0.37f,maxH=h*0.27f;
    float ratio=Math.min(maxW/sticker.getWidth(),maxH/sticker.getHeight());
    float bw=ratio*sticker.getWidth(),bh=ratio*sticker.getHeight();
    paint.setAlpha(245);
    c.drawBitmap(sticker,null,new RectF(w-bw-20f*unit,h*0.08f,w-20f*unit,h*0.08f+bh),paint);
    paint.setAlpha(255);
  }

  c.save();c.scale(scale,scale,w/2f,top+block/2f);
  paint.setTypeface(android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD));
  paint.setTextAlign(Paint.Align.CENTER);
  paint.setTextSize(font);
  paint.setStrokeJoin(Paint.Join.ROUND);
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(7f*unit);paint.setColor(Color.BLACK);
  for(int i=0;i<lines.length;i++) c.drawText(lines[i],w/2f,top+(i+0.86f)*lineHeight,paint);
  paint.setStyle(Paint.Style.FILL);
  paint.setColor("warning".equals(style)?Color.rgb(255,107,102):"numbers".equals(style)?Color.rgb(255,216,66):Color.WHITE);
  for(int i=0;i<lines.length;i++) c.drawText(lines[i],w/2f,top+(i+0.86f)*lineHeight,paint);
  c.restore();
  if("documentary".equals(preset)){
    paint.setTextAlign(Paint.Align.LEFT);paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    paint.setTextSize(20f*unit);paint.setColor(Color.argb(220,247,208,99));
    c.drawText("CINEZEHN / AI STUDIO",26f*unit,39f*unit,paint);
  }
 }
 private String[] wrap(String s,int approxMax) {
  ArrayList<String> res=new ArrayList<>();
  StringBuilder line=new StringBuilder();
  for(String word:s.trim().split("\\s+")) {
    if(line.length()>0 && line.length()+word.length()+1>approxMax){res.add(line.toString());line.setLength(0);}
    if(line.length()>0)line.append(" ");
    line.append(word);
  }
  if(line.length()>0)res.add(line.toString());
  if(res.size()>3) {
    StringBuilder last=new StringBuilder(res.get(2));
    for(int i=3;i<res.size();i++)last.append(" ").append(res.get(i));
    return new String[]{res.get(0),res.get(1),last.toString()};
  }
  return res.toArray(new String[0]);
 }
}
