package com.shari.autocut;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic offline editing plan library. THIS IS NOT A GENERATIVE LLM.
 * Plans are always labeled as recipe-based and can be previewed before export.
 */
public final class CreativeDirector {
 public static final String[] STYLES={
  "Documentary","Modern Explainer","High Energy","Historical","Finance",
  "Tech","Gaming","Comedy","Whiteboard","Minimal","Cinematic","Vlog"
 };
 private CreativeDirector(){}
 public static String choose(String prompt,String requested){
  String p=(prompt==null?"":prompt).toLowerCase(Locale.ROOT);
  if(p.contains("netflix")||p.contains("documentary")||p.contains("cinematic")||p.contains("film"))return "Documentary";
  if(p.contains("funny")||p.contains("meme")||p.contains("comedy"))return "Comedy";
  if(p.contains("whiteboard")||p.contains("draw"))return "Whiteboard";
  if(p.contains("money")||p.contains("finance")||p.contains("paise"))return "Finance";
  if(p.contains("gaming")||p.contains("gameplay"))return "Gaming";
  if(p.contains("fast")||p.contains("energetic")||p.contains("shorts"))return "High Energy";
  if(p.contains("explain")||p.contains("education"))return "Modern Explainer";
  return requested;
 }
 public static String topic(String caption){
  String s=(caption==null?"":caption).toLowerCase(Locale.ROOT);
  if(s.matches("(?s).*(youtube|یوٹیوب|यूट्यूब).*"))return "YouTube creator topic";
  if(s.matches("(?s).*(money|paise|earning|income|monetiz|پیسے|पैसे).*"))return "Earnings";
  if(s.matches("(?s).*(three|teen|3|تین|तीन).*"))return "Three-point explanation";
  if(s.matches("(?s).*(history|hazrat|musa|firon|khaybar|فرعون|حضرت).*"))return "Historical narration";
  if(s.matches("(?s).*(danger|warning|khatra|خطرہ|खतरा).*"))return "Warning";
  if(s.contains("?")||s.matches("(?s).*(kaise|kyun|how|why).*"))return "Question";
  if(s.trim().isEmpty())return "No transcript supplied";
  return "General narration";
 }
 public static String describe(List<FlowScene> scenes,String selectedStyle,String prompt){
  String style=choose(prompt,selectedStyle);
  long total=0;for(FlowScene s:scenes)total+=s.durationMs;
  StringBuilder sb=new StringBuilder("OFFLINE DIRECTOR — EDITING RECIPE PREVIEW\n");
  sb.append("Mode: ").append(style).append("\n");
  sb.append("Scenes: ").append(scenes.size()).append(" • Duration: ").append(total/1000).append("s\n");
  sb.append("Actual AI model: not installed yet. This is a curated offline recipe engine, NOT human-level interpretation.\n");
  sb.append("Media3 will preserve embedded scene audio, sequence clips, apply a graphic/caption overlay and export MP4.\n\n");
  int i=0;
  for(FlowScene s:scenes){
   sb.append("SCENE ").append(++i).append(" — ").append(s.fileName).append(" (").append(s.durationMs/1000.0).append("s)\n");
   sb.append("Narration category: ").append(topic(s.caption)).append("\n");
   sb.append("Graphic: ").append("No transcript supplied".equals(topic(s.caption))?"minimal scene index and cinematic line":"timed lower-third caption, topic-specific original vector ornament").append("\n");
   sb.append("Motion: lightweight animated accent/entrance and progress indicator. Cuts between scenes are clean, not crossfaded.\n\n");
  }
  sb.append("LIMITS: No automatic Urdu ASR, internet research, stock downloader, lip-sync, AI image generation or semantic fact checking in this Flow tool. Validate audio and captions before publishing.");
  return sb.toString();
 }
}
