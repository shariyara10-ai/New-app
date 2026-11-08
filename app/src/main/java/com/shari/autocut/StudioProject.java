package com.shari.autocut;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * Editable project persisted on device. Every shot has one background visual,
 * five optional user-imported overlays and an optional user-imported host PNG.
 * Plan parsing is deterministic: a pasted ChatGPT storyboard is NOT an AI model.
 */
public final class StudioProject {
 public static final String FILENAME="cinezehn_pro_edit_project.json";
 public String name="CineZehn Auto Edit";
 public String rawPrompt="";
 public String style="Cinematic Documentary";
 public String captionPreset="Bold Yellow Highlight";
 public String sourceLanguage="Hinglish";
 public Uri audioUri=null;
 public int width=720,height=1280;
 public long durationMs=60000L;
 public boolean addCaptions=true;
 public boolean enableHost=false;
 public final ArrayList<Shot> shots=new ArrayList<>();

 public static final class Shot {
  public long startMs,endMs;
  public String prompt="",caption="",narration="",motion="Slow Zoom",graphics="",sfx="",transition="Cut",captionStyle="Bold Pop";
  public Uri visual=null,host=null;
  public String hostMotion="Float";
  public boolean hostEnabled=false;
  public int overlaySlots=3;
  public final Uri[] overlays=new Uri[5];
  public final String[] overlayPrompts=new String[5];
  public final String[] overlayMotion=new String[5];
  public Shot(long a,long b){
   startMs=a;endMs=b;
   for(int i=0;i<5;i++){overlayPrompts[i]="";overlayMotion[i]="Float";}
  }
  public long duration(){return Math.max(1,endMs-startMs);}
 }
 private static final class Beat {
  long start,end;String prompt="",caption="",narration="",motion="",sfx="",graphics="",transition="";
 }
 public static int[] counts(){return new int[]{5,8,10,15,20,25,30,40,50,60,75,100};}
 public static final String[] MOTIONS={
  "None","Slow Zoom","Zoom Out","Punch Zoom","Pan Left","Pan Right","Float",
  "Shake","Slide In","Swipe Out","Fade","Rotate","Bounce","Typewriter","Point"
 };
 public static final String[] CAPTIONS={
  "Bold Yellow Highlight","White Minimal","Typing","Floating","Shake","Pop","Slide Up","Karaoke-style"
 };
 private static final Pattern RANGE=Pattern.compile("(?i)^\\s*(\\d{1,2}:\\d{2}(?:\\.\\d+)?)\\s*[–—-]\\s*(\\d{1,2}:\\d{2}(?:\\.\\d+)?)(?:\\s+.*)?$");
 private static final Pattern SCENE_RANGE=Pattern.compile("(?i)^\\s*SCENE\\s+\\d+[^\\[]*\\[(\\d{1,2}:\\d{2}(?:\\.\\d+)?)\\s*[–—-]\\s*(\\d{1,2}:\\d{2}(?:\\.\\d+)?)\\]");
 private static long ms(String time){
  try{
   String[] pair=time.split(":");
   return Math.round((Long.parseLong(pair[0])*60+Double.parseDouble(pair[1]))*1000);
  }catch(Exception ignored){return 0;}
 }
 private static String after(String line,String prefix){
  return line.substring(prefix.length()).trim().replaceAll("^\\*+|\\*+$","");
 }
 private static void attach(Beat beat,String key,String value){
  if(key.equals("VISUAL"))beat.prompt=value;
  else if(key.equals("MOTION"))beat.motion=value;
  else if(key.equals("GRAPHICS"))beat.graphics=value;
  else if(key.equals("SFX"))beat.sfx=value;
  else if(key.equals("VOICEOVER"))beat.narration=value;
  else if(key.equals("TRANSITION"))beat.transition=value;
  else if(key.equals("CAPTION")||key.equals("TEXT"))beat.caption=value;
 }
 public static StudioProject plan(String prompt,int requestedCount,long userDuration){
  StudioProject project=new StudioProject();
  project.rawPrompt=prompt==null?"":prompt;
  project.durationMs=Math.max(1000,userDuration);
  ArrayList<Beat> beats=new ArrayList<>();
  Beat active=null;String narration="";
  for(String raw:project.rawPrompt.split("\\r?\\n")){
   String line=raw.trim();
   if(line.isEmpty())continue;
   Matcher shot=RANGE.matcher(line);
   Matcher scene=SCENE_RANGE.matcher(line);
   if(shot.matches()){
    active=new Beat();active.start=ms(shot.group(1));active.end=ms(shot.group(2));
    if(active.end>active.start){beats.add(active);}else active=null;
    continue;
   }
   if(scene.find()){
    active=null;continue;
   }
   String clean=line.replaceFirst("^[-*\\s]+","");
   int colon=clean.indexOf(':');
   if(colon>0&&colon<20){
    String k=clean.substring(0,colon).trim().toUpperCase(Locale.ROOT);
    String v=clean.substring(colon+1).trim();
    if(k.equals("VOICEOVER")){
     narration=v;
     if(active!=null)active.narration=v;
    }else if(active!=null)attach(active,k,v);
   }
  }
  if(beats.isEmpty()){
   Beat one=new Beat();one.start=0;one.end=project.durationMs;
   one.prompt="Visualize the supplied narration using a relevant high-quality scene.";
   beats.add(one);
  }
  beats.sort(Comparator.comparingLong(a->a.start));
  int count=Math.max(1,Math.min(100,requestedCount));
  for(int i=0;i<count;i++){
   long a=Math.round(i*project.durationMs/(double)count);
   long b=Math.round((i+1)*project.durationMs/(double)count);
   Shot s=new Shot(a,b);
   Beat closest=null;long best=Long.MAX_VALUE;
   long mid=(a+b)/2;
   for(Beat beat:beats){
    long diff;
    if(mid>=beat.start&&mid<beat.end)diff=0;
    else diff=Math.min(Math.abs(mid-beat.start),Math.abs(mid-beat.end));
    if(diff<best){best=diff;closest=beat;}
   }
   if(closest!=null){
    s.prompt=closest.prompt.isEmpty()?"Cinematic relevant visual for timeline position "+(i+1):closest.prompt;
    s.motion=guessMotion(closest.motion);
    s.graphics=closest.graphics;
    s.sfx=closest.sfx;
    s.transition=closest.transition.isEmpty()?"Cut":closest.transition;
    s.caption=closest.caption.isEmpty()?closest.graphics:closest.caption;
    s.narration=closest.narration.isEmpty()?narration:closest.narration;
   }
   if(s.prompt.isEmpty())s.prompt="Cinematic scene matching the narration, no logos, no watermark, 9:16";
   for(int x=0;x<5;x++){
    if(x==0&&!s.graphics.isEmpty())s.overlayPrompts[x]="Transparent PNG: "+s.graphics+", clean readable overlay, no background";
    else s.overlayPrompts[x]="Optional transparent PNG overlay "+(x+1)+" for asset "+(i+1)+": "+s.prompt;
   }
   project.shots.add(s);
  }
  return project;
 }
 private static String guessMotion(String description){
  String s=description.toLowerCase(Locale.ROOT);
  if(s.contains("shake"))return "Shake";
  if(s.contains("punch")||s.contains("crash")||s.contains("impact"))return "Punch Zoom";
  if(s.contains("zoom-out")||s.contains("zoom out"))return "Zoom Out";
  if(s.contains("swipe out"))return "Swipe Out";
  if(s.contains("slide"))return "Slide In";
  if(s.contains("float"))return "Float";
  if(s.contains("left"))return "Pan Left";
  if(s.contains("right"))return "Pan Right";
  if(s.contains("rotate")||s.contains("orbit"))return "Rotate";
  if(s.contains("fade"))return "Fade";
  return "Slow Zoom";
 }
 public int completeVisuals(){
  int n=0;for(Shot s:shots)if(s.visual!=null)n++;return n;
 }
 public String toJson()throws JSONException{
  JSONObject obj=new JSONObject();
  obj.put("name",name).put("rawPrompt",rawPrompt).put("style",style)
   .put("captionPreset",captionPreset).put("sourceLanguage",sourceLanguage)
   .put("audioUri",audioUri==null?"":audioUri.toString())
   .put("width",width).put("height",height).put("durationMs",durationMs)
   .put("addCaptions",addCaptions).put("enableHost",enableHost);
  JSONArray all=new JSONArray();
  for(Shot s:shots){
   JSONObject x=new JSONObject();
   x.put("startMs",s.startMs).put("endMs",s.endMs)
    .put("prompt",s.prompt).put("caption",s.caption).put("narration",s.narration)
    .put("motion",s.motion).put("graphics",s.graphics).put("sfx",s.sfx)
    .put("transition",s.transition).put("captionStyle",s.captionStyle)
    .put("visual",s.visual==null?"":s.visual.toString())
    .put("host",s.host==null?"":s.host.toString()).put("hostMotion",s.hostMotion)
    .put("hostEnabled",s.hostEnabled).put("overlaySlots",s.overlaySlots);
   JSONArray overlayUris=new JSONArray(),overlayPrompts=new JSONArray(),overlayMotions=new JSONArray();
   for(int i=0;i<5;i++){
    overlayUris.put(s.overlays[i]==null?"":s.overlays[i].toString());
    overlayPrompts.put(s.overlayPrompts[i]);
    overlayMotions.put(s.overlayMotion[i]);
   }
   x.put("overlays",overlayUris).put("overlayPrompts",overlayPrompts)
    .put("overlayMotion",overlayMotions);
   all.put(x);
  }
  obj.put("shots",all);
  return obj.toString();
 }
 public static StudioProject fromJson(String data)throws JSONException{
  JSONObject obj=new JSONObject(data);StudioProject project=new StudioProject();
  project.name=obj.optString("name","CineZehn Auto Edit");
  project.rawPrompt=obj.optString("rawPrompt","");
  project.style=obj.optString("style","Cinematic Documentary");
  project.captionPreset=obj.optString("captionPreset","Bold Yellow Highlight");
  project.sourceLanguage=obj.optString("sourceLanguage","Hinglish");
  String audio=obj.optString("audioUri","");
  if(!audio.isEmpty())project.audioUri=Uri.parse(audio);
  project.width=obj.optInt("width",720);project.height=obj.optInt("height",1280);
  project.durationMs=obj.optLong("durationMs",60000);
  project.addCaptions=obj.optBoolean("addCaptions",true);
  project.enableHost=obj.optBoolean("enableHost",false);
  JSONArray all=obj.optJSONArray("shots");
  if(all!=null)for(int n=0;n<all.length();n++){
   JSONObject x=all.getJSONObject(n);
   Shot s=new Shot(x.optLong("startMs",0),x.optLong("endMs",1000));
   s.prompt=x.optString("prompt","");s.caption=x.optString("caption","");
   s.narration=x.optString("narration","");s.motion=x.optString("motion","Slow Zoom");
   s.graphics=x.optString("graphics","");s.sfx=x.optString("sfx","");
   s.transition=x.optString("transition","Cut");s.captionStyle=x.optString("captionStyle","Bold Pop");
   String image=x.optString("visual","");if(!image.isEmpty())s.visual=Uri.parse(image);
   String host=x.optString("host","");if(!host.isEmpty())s.host=Uri.parse(host);
   s.hostMotion=x.optString("hostMotion","Float");
   s.hostEnabled=x.optBoolean("hostEnabled",false);
   s.overlaySlots=Math.max(1,Math.min(5,x.optInt("overlaySlots",3)));
   JSONArray u=x.optJSONArray("overlays"),p=x.optJSONArray("overlayPrompts"),m=x.optJSONArray("overlayMotion");
   for(int i=0;i<5;i++){
    if(u!=null){String uri=u.optString(i,"");if(!uri.isEmpty())s.overlays[i]=Uri.parse(uri);}
    if(p!=null)s.overlayPrompts[i]=p.optString(i,"");
    if(m!=null)s.overlayMotion[i]=m.optString(i,"Float");
   }
   project.shots.add(s);
  }
  return project;
 }
 public void save(Context context)throws IOException{
  try(FileOutputStream out=context.openFileOutput(FILENAME,Context.MODE_PRIVATE)){
   out.write(toJson().getBytes(StandardCharsets.UTF_8));
  }catch(JSONException e){throw new IOException("Unable to serialize editing project",e);}
 }
 public static StudioProject load(Context context)throws IOException{
  try(InputStream in=context.openFileInput(FILENAME);
      ByteArrayOutputStream data=new ByteArrayOutputStream()){
   byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)data.write(b,0,n);
   return fromJson(data.toString("UTF-8"));
  }catch(JSONException e){throw new IOException("Saved project invalid",e);}
 }
}
