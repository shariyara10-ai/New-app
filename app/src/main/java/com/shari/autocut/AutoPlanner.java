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
 public static String assetPrompts(ArrayList<CaptionSegment> segs,String preset) {
  StringBuilder sb=new StringBuilder("Offline creative suggestions — verify all factual details manually.\n\n");
  int idx=0;
  for(CaptionSegment s:segs) {
   idx++;
   sb.append("Scene ").append(idx).append(" [").append(CaptionSegment.timestamp(s.startMs)).append("–").append(CaptionSegment.timestamp(s.endMs)).append("]\n");
   sb.append("Narration: ").append(s.text).append("\n");
   sb.append("PNG/image prompt: High-detail ").append("documentary".equals(preset)?"cinematic documentary":"short-form motion graphics");
   sb.append(" visual representing \"").append(s.text).append("\"; clean composition, dramatic lighting, realistic subject, no logos, no text, transparent-background cutout when appropriate, 16:9 and 9:16 variants. Check historical and scientific claims.\n");
   sb.append("Suggested motion: ").append(s.style).append("; caption timed to speech; subtle zoom with sound design if useful.\n\n");
  }
  return sb.toString();
 }
}
