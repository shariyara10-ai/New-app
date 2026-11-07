package com.shari.autocut;
import java.util.ArrayList;
import java.util.Locale;
/** Offline deterministic command interpreter. No LLM is bundled in this release. */
public final class DirectorEngine {
 public static final String[] STYLES={"Documentary","Shorts","Explainer","Cinematic","Gaming","Comedy","Educational","Vlog","Finance","Islamic History","Tech","Whiteboard"};
 public static final String[] ASSETS={"Black background","White background","Gradient background","Gold title","Red warning","Typing captions","Zoom punch","Slide reveal","Number counter","Arrow callout","Money illustration","YouTube icon placeholder","Timeline graphic","Question card","Impact beat","Whoosh beat","Whiteboard line","Focus vignette","Progress bar","Lower third"};
 public static String run(String command,ArrayList<CaptionSegment> cues,String selectedStyle){
  String c=command.toLowerCase(Locale.ROOT);
  String style=selectedStyle.toLowerCase(Locale.ROOT);
  StringBuilder report=new StringBuilder("OFFLINE DIRECTOR — LOCAL RULE ENGINE\n");
  report.append("Mode: ").append(selectedStyle).append("\n");
  if(cues==null||cues.isEmpty())return report.append("Import media and enter or transcribe captions first.\n").toString();
  int changed=0;
  for(CaptionSegment s:cues){
   String t=s.text.toLowerCase(Locale.ROOT);
   String next="cinema";
   if(c.contains("minimal")||c.contains("simple"))next="cinema";
   else if(t.contains("?")||t.contains("kaise")||t.contains("کیسے")||t.contains("क्यों")||t.contains("why"))next="mystery";
   else if(t.matches(".*[0-9].*")||t.contains("million")||t.contains("lakh")||t.contains("لاکھ"))next="numbers";
   else if(t.contains("warning")||t.contains("خطر")||t.contains("danger"))next="warning";
   else if(c.contains("energetic")||c.contains("viral")||c.contains("fast")||style.contains("short")||style.contains("gaming"))next="pop";
   else if(c.contains("documentary")||c.contains("netflix")||style.contains("cinematic"))next="cinema";
   else if(style.contains("explainer")||style.contains("educational")||style.contains("whiteboard"))next="title";
   if(!next.equals(s.style)){s.style=next;changed++;}
  }
  report.append("Applied caption motion to ").append(changed).append(" segments.\n");
  report.append("Suggested visuals (not automatically downloaded):\n");
  int n=0;
  for(CaptionSegment s:cues){
   if(n++>=8)break;
   String t=s.text.toLowerCase(Locale.ROOT);
   String asset=t.contains("youtube")?"YouTube icon placeholder + earning diagram":t.contains("money")||t.contains("paise")||t.contains("پیسے")?"Money illustration + number counter":t.contains("history")||t.contains("تاریخ")?"Historical map + cinematic title":t.contains("game")?"Gaming HUD + zoom punch":t.contains("why")||t.contains("kaise")||t.contains("کیسے")?"Question card + arrow callout":"Gradient background + kinetic text";
   report.append(CaptionSegment.timestamp(s.startMs)).append(" → ").append(asset).append(" (").append(s.style).append(")\n");
  }
  report.append("\nLimitations: deterministic offline planning; no semantic LLM, automatic footage retrieval, or asset compositing. Review Urdu/Hinglish text manually.\n");
  return report.toString();
 }
 private DirectorEngine(){}
}