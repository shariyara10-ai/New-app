package com.shari.autocut;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class CaptionSegment {
 public long startMs, endMs;
 public String text, style;
 public CaptionSegment(long start,long end,String t,String sty) {startMs=start;endMs=end;text=t;style=sty;}
 public static String timestamp(long ms) {return String.format(Locale.US,"%02d:%05.2f",ms/60000, (ms%60000)/1000d);}
 public static String format(ArrayList<CaptionSegment> segments) {
  StringBuilder sb=new StringBuilder();
  for(CaptionSegment s:segments) sb.append("[").append(timestamp(s.startMs)).append("-").append(timestamp(s.endMs)).append("] ").append(s.text.replace("\n"," ")).append("\n");
  return sb.toString();
 }
 public static long time(String value) {String[] v=value.trim().split(":");return (long)(Integer.parseInt(v[0])*60000 + Double.parseDouble(v[1])*1000);}
 public static ArrayList<CaptionSegment> parse(String lines,long duration) {
  ArrayList<CaptionSegment> result=new ArrayList<>();
  Pattern p=Pattern.compile("^\\s*\\[(\\d+:\\d+(?:\\.\\d+)?)-(\\d+:\\d+(?:\\.\\d+)?)\\]\\s*(.+)$");
  for(String line:lines.split("\\r?\\n")) {
   Matcher m=p.matcher(line);
   if(m.matches()) {long a=time(m.group(1)),b=time(m.group(2));if(b>a && a>=0 && a<duration)result.add(new CaptionSegment(a,Math.min(duration,b),m.group(3).trim(),"pop"));}
  }
  if(result.isEmpty() && !lines.trim().isEmpty()) {
   String[] words=lines.replace("\n"," ").trim().split("\\s+");
   int groups=Math.max(1,(int)Math.ceil(words.length/6d));
   for(int i=0;i<groups;i++) {
    StringBuilder text=new StringBuilder();
    for(int j=i*6;j<Math.min(words.length,(i+1)*6);j++){if(text.length()>0)text.append(" ");text.append(words[j]);}
    long a=Math.round(i*duration/(double)groups),b=Math.round((i+1)*duration/(double)groups);
    result.add(new CaptionSegment(a,b,text.toString(),"pop"));
   }
  }
  return result;
 }
}
