package com.shari.autocut;

import java.util.ArrayList;
import java.util.Locale;

public class AutoPlanner {
 public static void apply(ArrayList<CaptionSegment> segs,String preset) {
  int idx=0;
  for(CaptionSegment s:segs) {
   String t=s.text.toLowerCase(Locale.ROOT);
   if(t.contains("?")||t.contains("why")||t.contains("kaise")||t.contains("kyun")||t.contains("راز")||t.contains("क्यों")) s.style="mystery";
   else if(t.contains("warning")||t.contains("danger")||t.contains("beware")||t.contains("dangerous")||t.contains("shock")||t.contains("खतरा"))s.style="warning";
   else if(t.contains("million")||t.contains("billion")||t.contains("percent")||t.matches(".*[0-9].*"))s.style="numbers";
   else if("documentary".equals(preset))s.style=idx%3==0?"title":"cinema";
   else if("explainer".equals(preset))s.style=idx%3==0?"title":"pop";
   else s.style=idx%4==0?"title":"pop";
   idx++;
  }
 }

 private static class Beat {
  long start,end;
  StringBuilder narration=new StringBuilder();
  String style="cinema";
  Beat(CaptionSegment s){start=s.startMs;end=s.endMs;append(s);}
  void append(CaptionSegment s){
   end=Math.max(end,s.endMs);
   if(narration.length()>0)narration.append(" ");
   narration.append(s.text.trim());
   if("warning".equals(s.style) || ("mystery".equals(s.style)&&!"warning".equals(style)))style=s.style;
   else if("numbers".equals(s.style)&&!"warning".equals(style)&&!"mystery".equals(style))style=s.style;
  }
 }

 // Subtitle cues are short (often 1–2 seconds). This creates visual beats instead of
 // generating a distinct PNG for every caption fragment.
 public static String assetPrompts(ArrayList<CaptionSegment> segs,String preset) {
  StringBuilder sb=new StringBuilder();
  if(segs==null||segs.isEmpty())
   return "No captions found. Transcribe or enter text first, then review accuracy.";

  ArrayList<Beat> beats=new ArrayList<>();
  Beat current=null;
  for(CaptionSegment s:segs) {
   if(s.text==null||s.text.trim().isEmpty())continue;
   boolean split=current==null;
   if(current!=null){
    long length=Math.max(0,current.end-current.start);
    long nextLength=Math.max(0,s.endMs-current.start);
    boolean silence=s.startMs-current.end>5000;
    split=(silence&&length>=12000)||(nextLength>30000&&length>=14000);
   }
   if(split){
    current=new Beat(s);beats.add(current);
   }else current.append(s);
  }
  sb.append("CINEZEHN AI STUDIO — COMPACT VISUAL SHOT LIST\n");
  sb.append("Visual beats: ").append(beats.size()).append(" (captions: ").append(segs.size()).append(")\n");
  sb.append("You do NOT need a separate PNG for every caption. Reuse visual assets.\n");
  sb.append("IMPORTANT: Check Urdu/Hindi speech-to-text and correct transcript before using these suggestions. The offline planner does not understand or fact-check meaning.\n\n");
  int index=0;
  for(Beat b:beats){
   index++;
   String words=b.narration.toString().replaceAll("\\s+"," ").trim();
   String excerpt=words.length()>260?words.substring(0,260).trim()+"…":words;
   sb.append("Visual beat ").append(index)
    .append(" [").append(CaptionSegment.timestamp(b.start)).append("–").append(CaptionSegment.timestamp(b.end)).append("]\n");
   sb.append("Narration excerpt: ").append(excerpt).append("\n");
   sb.append("Visual guidance: Create ONE reusable ").append("documentary".equals(preset)?"cinematic documentary frame or relevant b-roll shot":"clean explainer graphic or b-roll shot");
   sb.append(" for the corrected meaning of the narration above; match the actual subject and time period. Avoid literal visualizing misheard words. No logos or watermark.\n");
   sb.append("Motion: ").append(b.style).append("; use subtitles across the entire beat; add zoom/reveal only when it helps the story.\n");
   sb.append("Asset required? Optional. Reuse existing imagery or use animated text instead.\n\n");
  }
  return sb.toString();
 }
}
