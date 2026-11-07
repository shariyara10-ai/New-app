package com.shari.autocut;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.effect.*;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

@androidx.media3.common.util.UnstableApi
public final class FlowJoinActivity extends Activity {
 private static final int REQUEST_CLIPS=71;
 private final ArrayList<FlowScene> scenes=new ArrayList<>();
 private final int PRIMARY=Color.rgb(72,91,245),INK=Color.rgb(25,35,58),SECONDARY=Color.rgb(95,112,141),PAGE=Color.rgb(248,250,255);
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final Handler handler=new Handler(Looper.getMainLooper());
 private LinearLayout root, list;
 private TextView summary,status;
 private EditText endText;
 private CheckBox addEnd,addCaptions,addGraphics,cropToFill,addHost,addReactions;
 private Spinner endLength,styleSpinner,formatSpinner;
 private EditText directorPrompt;
 private Button cancelButton,diagnosticButton;
 private File activeExport;
 private ProgressBar progress;
 private VideoView preview;
 private Button joinButton;
 private Transformer transformer;
 private Runnable polling;
 private boolean busy;
 private Uri saved;
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView title(String s,int sz,boolean b){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(sz);t.setTextColor(b?INK:SECONDARY);
  if(b)t.setTypeface(null,Typeface.BOLD);return t;
 }
 private android.graphics.drawable.GradientDrawable rounded(int color,int radius,int stroke){
  android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
  bg.setColor(color);bg.setCornerRadius(dp(radius));if(stroke!=0)bg.setStroke(dp(1),stroke);return bg;
 }
 private Button button(String s){
  Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(12);
  boolean primary=s.contains("RENDER")||s.contains("Select multiple")||s.contains("TEST EXPORT");
  b.setTextColor(primary?Color.WHITE:INK);
  b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  b.setBackground(rounded(primary?PRIMARY:Color.rgb(237,241,252),13,0));
  b.setMinHeight(dp(48));b.setElevation(0);return b;
 }
 private void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
 private LinearLayout panel(String heading){
  LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);
  p.setPadding(dp(16),dp(17),dp(16),dp(18));
  p.setBackground(rounded(Color.WHITE,21,Color.rgb(231,236,245)));p.setElevation(dp(1));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
  lp.topMargin=dp(13);root.addView(p,lp);add(p,title(heading,17,true));return p;
 }
 @Override public void onCreate(Bundle savedState){
  super.onCreate(savedState);getWindow().setStatusBarColor(Color.WHITE);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(PAGE);
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(22),dp(16),dp(30));
  sc.addView(root);sc.setOnApplyWindowInsetsListener((v,i)->{sc.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
  setContentView(sc);
  LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
  hero.setPadding(dp(21),dp(21),dp(21),dp(23));
  android.graphics.drawable.GradientDrawable hg=new android.graphics.drawable.GradientDrawable(
    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
    new int[]{Color.rgb(48,69,207),Color.rgb(116,77,224)});
  hg.setCornerRadius(dp(24));hero.setBackground(hg);
  TextView eyebrow=title("CINEZEHN  /  CREATOR PRO",11,true);eyebrow.setTextColor(Color.rgb(216,221,255));
  add(hero,eyebrow);
  TextView heading=title("Flow Scene Studio",26,true);heading.setTextColor(Color.WHITE);add(hero,heading);
  TextView desc=title("Import clips  •  Clean cuts  •  Captions  •  Offline motion graphics",13,false);
  desc.setTextColor(Color.rgb(229,229,255));add(hero,desc);add(root,hero);
  LinearLayout input=panel("1  Select videos");
  Button pick=button("Select multiple Flow clips");add(input,pick);pick.setOnClickListener(v->select());
  summary=title("No clips selected",13,false);add(input,summary);
  list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);add(input,list);
  Button clear=button("Clear all clips");add(input,clear);clear.setOnClickListener(v->{if(!busy){scenes.clear();refresh();}});
  Button saveProjectButton=button("Save project on phone");add(input,saveProjectButton);
  saveProjectButton.setOnClickListener(v->{persistProject();Toast.makeText(this,"Project saved locally",Toast.LENGTH_SHORT).show();});
  LinearLayout pv=panel("2  Preview individual clip");
  preview=new VideoView(this);preview.setBackgroundColor(Color.BLACK);
  pv.addView(preview,new LinearLayout.LayoutParams(-1,dp(190)));
  MediaController controls=new MediaController(this);controls.setAnchorView(preview);preview.setMediaController(controls);
  LinearLayout creative=panel("3  Offline Creative Director");
  add(creative,title("AI-assisted planning uses built-in rules. It is not a ChatGPT-style language model.",12,false));
  styleSpinner=new Spinner(this);
  ArrayAdapter<String> styleAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,CreativeDirector.STYLES);
  styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  styleSpinner.setAdapter(styleAdapter);add(creative,styleSpinner);
  directorPrompt=new EditText(this);directorPrompt.setMinLines(2);directorPrompt.setMaxLines(4);
  directorPrompt.setHint("Describe editing style: e.g. dramatic historical story with cinematic captions");add(creative,directorPrompt);
  Button plan=button("Generate offline editing plan");add(creative,plan);plan.setOnClickListener(v->planOffline());
  addGraphics=new CheckBox(this);addGraphics.setText("Animated progress, scene numbers and contextual graphics");addGraphics.setChecked(true);add(creative,addGraphics);
  addCaptions=new CheckBox(this);addCaptions.setText("Burn-in edited / transcribed captions");addCaptions.setChecked(true);add(creative,addCaptions);
  addHost=new CheckBox(this);addHost.setText("Add original animated 2D explainer host");addHost.setChecked(false);add(creative,addHost);
  add(creative,title("Host has procedural face/hand gestures based on supplied text, not neural lip-sync or realistic AI animation.",12,false));
  addReactions=new CheckBox(this);addReactions.setText("Original comic reaction overlays (comedy keywords)");addReactions.setChecked(false);add(creative,addReactions);
  add(creative,title("Reactions are original vector animations, not movie meme clips or automatic copyrighted downloads.",12,false));
  Button exportSrt=button("Export subtitle SRT to Downloads");add(creative,exportSrt);exportSrt.setOnClickListener(v->exportSrt());
  LinearLayout outputLayout=panel("4  Video shape and export quality");
  formatSpinner=new Spinner(this);
  ArrayAdapter<String> formats=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,
   new String[]{"9:16 Shorts / Reels 720x1280","16:9 Landscape 1280x720","1:1 Square 720x720","Keep source dimensions"});
  formats.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  formatSpinner.setAdapter(formats);add(outputLayout,formatSpinner);
  cropToFill=new CheckBox(this);cropToFill.setText("Fill frame by cropping (off = safe letterbox)");cropToFill.setChecked(false);add(outputLayout,cropToFill);
  Button analyzeAll=button("Check scene metadata and compatibility");add(outputLayout,analyzeAll);
  analyzeAll.setOnClickListener(v->analyzeAll());
  LinearLayout ending=panel("5  Optional animated ending");
  addEnd=new CheckBox(this);addEnd.setText("Append animated ending");addEnd.setTextColor(Color.BLACK);addEnd.setChecked(false);add(ending,addEnd);
  endText=new EditText(this);endText.setSingleLine(true);endText.setText("THANKS FOR WATCHING!");
  endText.setTextColor(Color.BLACK);add(ending,endText);
  endLength=new Spinner(this);
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"3 sec","4 sec","5 sec","6 sec"});
  adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  endLength.setAdapter(adapter);endLength.setSelection(1);add(ending,endLength);
  add(ending,title("Like • Comment • Subscribe. Outro contains no music or memes.",12,false));
  LinearLayout output=panel("6  Render and share");
  joinButton=button("RENDER FULL VIDEO TO MP4");add(output,joinButton);joinButton.setOnClickListener(v->render(false));
  diagnosticButton=button("TEST EXPORT — FIRST 15 SECONDS");add(output,diagnosticButton);diagnosticButton.setOnClickListener(v->render(true));
  cancelButton=button("Cancel active video render");add(output,cancelButton);cancelButton.setEnabled(false);cancelButton.setOnClickListener(v->cancelRender());
  Button share=button("Share last video");add(output,share);share.setOnClickListener(v->share());
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);add(output,progress);
  status=title("Ready — add your clips.",13,false);add(output,status);
  add(root,title("All processing on-device. Vosk transcription requires a separately installed model. Complex effects and mixed codecs/HDR may fail on some phones; the 15-second test helps diagnose.",12,false));
  restoreProject();
  String incoming=getIntent().getStringExtra("director_prompt");
  String presetName=getIntent().getStringExtra("director_style");
  if(incoming!=null&&!incoming.trim().isEmpty())directorPrompt.setText(incoming);
  if(presetName!=null)for(int i=0;i<styleSpinner.getCount();i++)
    if(String.valueOf(styleSpinner.getItemAtPosition(i)).equalsIgnoreCase(presetName))
      styleSpinner.setSelection(i);
 }
 private void select(){
  if(busy)return;
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("video/*");
  i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
  i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  startActivityForResult(i,REQUEST_CLIPS);
 }
 @Override protected void onActivityResult(int request,int result,Intent intent){
  super.onActivityResult(request,result,intent);
  if(request!=REQUEST_CLIPS||result!=RESULT_OK||intent==null)return;
  ArrayList<Uri> files=new ArrayList<>();
  ClipData cd=intent.getClipData();
  if(cd!=null){for(int i=0;i<cd.getItemCount();i++)files.add(cd.getItemAt(i).getUri());}
  else if(intent.getData()!=null)files.add(intent.getData());
  StringBuilder skipped=new StringBuilder();
  for(Uri uri:files){
   boolean duplicate=false;for(FlowScene s:scenes)if(s.uri.equals(uri))duplicate=true;
   if(duplicate)continue;
   try{
    getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
   }catch(Exception ignored){}
   try{
    MediaMetadataRetriever m=new MediaMetadataRetriever();
    try{
     m.setDataSource(this,uri);
     if(!"yes".equalsIgnoreCase(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)))throw new Exception("Not a video");
     String d=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
     long len=Long.parseLong(d==null?"0":d);
     if(len<100)throw new Exception("Invalid duration");
     String name=uri.getLastPathSegment();
     try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
      if(c!=null&&c.moveToFirst())name=c.getString(0);
     }catch(Exception ignored){}
     boolean hasAudio="yes".equalsIgnoreCase(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
     scenes.add(new FlowScene(uri,name,len,hasAudio));
    }finally{m.release();}
   }catch(Exception e){skipped.append(e.getMessage()).append("\n");}
  }
  refresh();
  if(skipped.length()>0)new AlertDialog.Builder(this).setTitle("Skipped files").setMessage(skipped.toString()).setPositiveButton("OK",null).show();
 }
 private void refresh(){
  list.removeAllViews();long total=0;int aud=0;
  for(int j=0;j<scenes.size();j++){
   final int idx=j;FlowScene s=scenes.get(j);total+=s.editingDurationMs();if(s.hasAudio)aud++;
   LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(4),dp(8),dp(4),dp(8));add(list,l);
   LinearLayout mediaRow=new LinearLayout(this);mediaRow.setGravity(Gravity.CENTER_VERTICAL);add(l,mediaRow);
   ImageView thumbnail=new ImageView(this);thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
   thumbnail.setBackground(rounded(Color.rgb(22,33,54),10,0));
   mediaRow.addView(thumbnail,new LinearLayout.LayoutParams(dp(78),dp(53)));
   TextView name=title((j+1)+". "+s.fileName+"\n"+String.format(Locale.US,"%.1fs",s.editingDurationMs()/1000f)+(s.hasAudio?"  ·  Audio":"  ·  Silent"),12,true);
   name.setPadding(dp(11),0,0,0);mediaRow.addView(name,new LinearLayout.LayoutParams(0,-2,1));
   showThumbnail(s,thumbnail);
   LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);add(l,actions);
   Button play=button("Preview"),up=button("↑"),down=button("↓"),remove=button("✕");
   for(Button b:new Button[]{play,up,down,remove})actions.addView(b,new LinearLayout.LayoutParams(0,dp(43),1));
   play.setOnClickListener(v->{preview.setVideoURI(s.uri);preview.setOnPreparedListener(mp->{preview.seekTo((int)s.trimStartMs);preview.start();});});
   up.setEnabled(j>0);down.setEnabled(j<scenes.size()-1);
   up.setOnClickListener(v->{if(!busy){Collections.swap(scenes,idx,idx-1);refresh();}});
   down.setOnClickListener(v->{if(!busy){Collections.swap(scenes,idx,idx+1);refresh();}});
   remove.setOnClickListener(v->{if(!busy){scenes.remove(idx);refresh();}});
   LinearLayout advanced=new LinearLayout(this);advanced.setOrientation(LinearLayout.HORIZONTAL);add(l,advanced);
   Button captions=button("Captions"),transcribe=button("Transcribe"),trim=button("Trim"),inspect=button("Inspect");
   for(Button b:new Button[]{captions,transcribe,trim,inspect}){b.setAllCaps(false);b.setTextSize(10);advanced.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}
   captions.setOnClickListener(v->editCaptions(s));
   transcribe.setOnClickListener(v->transcribeScene(s));
   trim.setOnClickListener(v->trimScene(s));
   inspect.setOnClickListener(v->inspectScene(s));
  }
  summary.setText(scenes.size()+" scenes • "+String.format(Locale.US,"%.1f",total/1000f)+" sec • "+aud+" with audio. Rearrange using arrows.");
  persistProject();
 }

 private void editCaptions(FlowScene scene){
  if(busy)return;
  EditText edit=new EditText(this);edit.setMinLines(4);edit.setMaxLines(12);
  edit.setText(scene.caption);edit.setTextSize(14);
  edit.setHint("Type dialogue or timed lines: [00:00.00-00:02.00] narration");
  new AlertDialog.Builder(this).setTitle("Captions — "+scene.fileName)
   .setMessage("Manual text is divided into caption beats. Timecodes are relative to the original clip.")
   .setView(edit).setPositiveButton("Save captions",(d,w)->{
    scene.caption=edit.getText().toString().trim();scene.subtitles.clear();
    if(!scene.caption.isEmpty())scene.subtitles.addAll(CaptionSegment.parse(scene.caption,scene.durationMs));
    refresh();status.setText("Captions saved. Export with Burn-in captions enabled.");
   }).setNegativeButton("Cancel",null).show();
 }
 private void transcribeScene(FlowScene scene){
  if(busy)return;
  File model=ModelImporter.active(this);
  if(model==null){new AlertDialog.Builder(this).setTitle("Offline speech model needed")
    .setMessage("Import or download a Vosk model from the main editor first. English/Hindi models do not guarantee accurate Urdu or Hinglish.")
    .setPositiveButton("OK",null).show();return;}
  if(!scene.hasAudio){status.setText("This clip has no audio track.");return;}
  busy=true;joinButton.setEnabled(false);diagnosticButton.setEnabled(false);
  status.setText("Transcribing "+scene.fileName+" offline. Please review all words.");
  io.execute(()->{
   try{
    ArrayList<CaptionSegment> recognized=OfflineTranscriber.transcribe(this,scene.uri,model,
      percent->runOnUiThread(()->progress.setProgress(Math.max(0,Math.min(99,percent)))));
    runOnUiThread(()->{
      scene.subtitles.clear();scene.subtitles.addAll(recognized);
      scene.caption=CaptionSegment.format(recognized);
      busy=false;joinButton.setEnabled(true);diagnosticButton.setEnabled(true);
      progress.setProgress(100);refresh();
      status.setText(recognized.isEmpty()?"No speech detected; enter captions manually.":"Offline transcription done. Edit and correct captions before export.");
    });
   }catch(Exception e){runOnUiThread(()->{
      busy=false;joinButton.setEnabled(true);diagnosticButton.setEnabled(true);
      status.setText("Speech recognition failed: "+e.getMessage());
   });}
  });
 }
 private EditText numberBox(String value){
  EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);
  e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
  return e;
 }
 private void trimScene(FlowScene s){
  if(busy)return;
  LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);wrap.setPadding(dp(18),0,dp(18),0);
  add(wrap,title("Start seconds",13,false));
  EditText start=numberBox(String.format(Locale.US,"%.2f",s.trimStartMs/1000.0));add(wrap,start);
  add(wrap,title("End seconds — max "+String.format(Locale.US,"%.2f",s.durationMs/1000.0),13,false));
  EditText end=numberBox(String.format(Locale.US,"%.2f",s.trimEndMs/1000.0));add(wrap,end);
  new AlertDialog.Builder(this).setTitle("Non-destructive scene trim").setView(wrap)
   .setPositiveButton("Apply",(d,w)->{
    try{
      long a=Math.round(Double.parseDouble(start.getText().toString())*1000);
      long b=Math.round(Double.parseDouble(end.getText().toString())*1000);
      if(a<0||b>a+s.durationMs||b<=a+100||b>s.durationMs)throw new IllegalArgumentException("Invalid range");
      s.trimStartMs=a;s.trimEndMs=b;refresh();
    }catch(Exception e){status.setText("Trim rejected. Use 0 ≤ start < end ≤ clip length.");}
   }).setNegativeButton("Cancel",null).show();
 }
 private void inspectScene(FlowScene s){
  if(busy)return;
  status.setText("Inspecting "+s.fileName+"...");
  io.execute(()->{
   String report;
   MediaMetadataRetriever r=new MediaMetadataRetriever();
   try{
    r.setDataSource(this,s.uri);
    String width=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
    String height=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
    String fps=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
    String rotation=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
    StringBuilder b=new StringBuilder();
    b.append(s.fileName).append("\nSize: ").append(width).append(" x ").append(height);
    b.append("\nFPS (if available): ").append(fps==null?"unknown":fps);
    b.append("\nRotation: ").append(rotation==null?"0":rotation);
    b.append("\nOriginal length: ").append(s.durationMs/1000.0).append(" sec");
    b.append("\nSelected range: ").append(s.trimStartMs/1000.0).append(" – ").append(s.trimEndMs/1000.0).append(" sec");
    b.append("\nAudio: ").append(s.hasAudio?"present":"none");
    b.append("\nTimed captions: ").append(s.subtitles.size());
    try{
      Bitmap frame=r.getScaledFrameAtTime(s.durationMs*500L,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,40,40);
      if(frame!=null){
       long sum=0;int count=0;
       for(int y=0;y<frame.getHeight();y+=2)for(int x=0;x<frame.getWidth();x+=2){
         int rgb=frame.getPixel(x,y);sum+=(Color.red(rgb)*299+Color.green(rgb)*587+Color.blue(rgb)*114)/1000;count++;
       }
       int avg=(int)(sum/Math.max(1,count));
       b.append("\nMid-frame brightness: ").append(avg).append("/255");
       if(avg<45)b.append(" (dark — check caption contrast)");
       frame.recycle();
      }
    }catch(Exception ignored){}
    b.append("\n\nThis is technical media inspection, not object or semantic AI analysis.");
    report=b.toString();
   }catch(Exception e){report="Cannot inspect this media: "+e.getMessage();}
   finally{try{r.release();}catch(Exception ignored){}}
   final String response=report;
   runOnUiThread(()->{
    status.setText("Technical inspection complete.");
    new AlertDialog.Builder(this).setTitle("Offline scene inspection").setMessage(response).setPositiveButton("OK",null).show();
   });
  });
 }
 private void analyzeAll(){
  if(scenes.isEmpty()){status.setText("Select clips first.");return;}
  StringBuilder sb=new StringBuilder("OFFLINE MEDIA CHECK\n");
  long time=0;int noAudio=0;
  for(FlowScene s:scenes){
   sb.append("• ").append(s.fileName).append(" — ").append(String.format(Locale.US,"%.2f",s.editingDurationMs()/1000.0)).append("s ");
   if(!s.hasAudio){sb.append("(silent)");noAudio++;}
   sb.append("\n");time+=s.editingDurationMs();
  }
  sb.append("\nTotal: ").append(String.format(Locale.US,"%.2f",time/1000.0)).append(" seconds");
  sb.append("\nSilent clips: ").append(noAudio);
  sb.append("\nMixing HDR, unusual codecs or audio formats can cause export failure. Test 15 seconds first.");
  new AlertDialog.Builder(this).setTitle("Project analysis").setMessage(sb.toString()).setPositiveButton("OK",null).show();
 }
 private void planOffline(){
  if(scenes.isEmpty()){status.setText("Import clips before planning.");return;}
  String prompt=directorPrompt.getText().toString();
  String requested=String.valueOf(styleSpinner.getSelectedItem());
  String chosen=CreativeDirector.choose(prompt,requested);
  for(int i=0;i<styleSpinner.getCount();i++)if(chosen.equals(styleSpinner.getItemAtPosition(i)))styleSpinner.setSelection(i);
  persistProject();
  String plan=CreativeDirector.describe(scenes,chosen,prompt);
  ScrollView scroll=new ScrollView(this);TextView out=title(plan,13,false);
  out.setPadding(dp(16),dp(12),dp(16),dp(20));scroll.addView(out);
  new AlertDialog.Builder(this).setTitle("Offline Creative Director — recipe plan")
    .setView(scroll).setPositiveButton("Apply selected style",(d,w)->{
     status.setText("Style selected: "+chosen+". Enable graphics/captions and render to apply.");
     persistProject();
    }).setNegativeButton("Close",null).show();
 }
 private String srtTime(long ms){
  long h=ms/3600000, m=(ms/60000)%60, sec=(ms/1000)%60;
  return String.format(Locale.US,"%02d:%02d:%02d,%03d",h,m,sec,ms%1000);
 }
 private void exportSrt(){
  if(scenes.isEmpty()){status.setText("Import clips first.");return;}
  StringBuilder b=new StringBuilder();long timeline=0;int index=0;
  for(FlowScene s:scenes){
   for(CaptionSegment cap:s.subtitles){
    long a=Math.max(cap.startMs,s.trimStartMs),z=Math.min(cap.endMs,s.trimEndMs);
    if(z<=a||cap.text.trim().isEmpty())continue;
    b.append(++index).append("\n").append(srtTime(timeline+a-s.trimStartMs))
     .append(" --> ").append(srtTime(timeline+z-s.trimStartMs))
     .append("\n").append(cap.text.trim()).append("\n\n");
   }
   timeline+=s.editingDurationMs();
  }
  if(index==0){status.setText("No timed captions. Use Captions or Transcribe on a clip first.");return;}
  try{
   ContentValues values=new ContentValues();
   values.put(MediaStore.Downloads.DISPLAY_NAME,"CineZehn_Captions_"+System.currentTimeMillis()+".srt");
   values.put(MediaStore.Downloads.MIME_TYPE,"application/x-subrip");
   values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/CineZehn");
   Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
   if(uri==null)throw new IOException("Downloads destination unavailable");
   try(OutputStream out=getContentResolver().openOutputStream(uri)){
    if(out==null)throw new IOException("Cannot write SRT file");
    out.write(b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
   }
   status.setText("Exported "+index+" subtitles to Downloads/CineZehn.");
  }catch(Exception e){status.setText("Cannot save subtitle file: "+e.getMessage());}
 }
 private void persistProject(){
  if(styleSpinner==null||formatSpinner==null||scenes==null)return;
  try{
   JSONObject data=new JSONObject();JSONArray items=new JSONArray();
   for(FlowScene s:scenes){
    JSONObject obj=new JSONObject();obj.put("uri",s.uri.toString());obj.put("name",s.fileName);
    obj.put("duration",s.durationMs);obj.put("audio",s.hasAudio);
    obj.put("start",s.trimStartMs);obj.put("end",s.trimEndMs);
    obj.put("captions",s.caption);
    items.put(obj);
   }
   data.put("scenes",items);
   data.put("style",styleSpinner.getSelectedItemPosition());
   data.put("format",formatSpinner.getSelectedItemPosition());
   data.put("graphics",addGraphics.isChecked());data.put("subtitles",addCaptions.isChecked());
   data.put("crop",cropToFill.isChecked());data.put("host",addHost.isChecked());data.put("reactions",addReactions.isChecked());data.put("outro",addEnd.isChecked());
   data.put("outroText",endText.getText().toString());data.put("outroLength",endLength.getSelectedItemPosition());
   data.put("prompt",directorPrompt.getText().toString());
   try(FileOutputStream out=openFileOutput("cinezehn_last_flow_project.json",MODE_PRIVATE)){
    out.write(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
   }
  }catch(Exception e){android.util.Log.w("CineZehn","Project autosave failed",e);}
 }
 private void restoreProject(){
  try(FileInputStream in=openFileInput("cinezehn_last_flow_project.json")){
   ByteArrayOutputStream buffer=new ByteArrayOutputStream();
   byte[] tmp=new byte[8192];int n;while((n=in.read(tmp))!=-1)buffer.write(tmp,0,n);
   JSONObject data=new JSONObject(buffer.toString("UTF-8"));
   styleSpinner.setSelection(data.optInt("style",0));
   formatSpinner.setSelection(data.optInt("format",0));
   addGraphics.setChecked(data.optBoolean("graphics",true));
   addCaptions.setChecked(data.optBoolean("subtitles",true));
   cropToFill.setChecked(data.optBoolean("crop",false));
   addHost.setChecked(data.optBoolean("host",false));
   addReactions.setChecked(data.optBoolean("reactions",false));
   addEnd.setChecked(data.optBoolean("outro",false));
   endText.setText(data.optString("outroText","THANKS FOR WATCHING!"));
   endLength.setSelection(data.optInt("outroLength",1));
   directorPrompt.setText(data.optString("prompt",""));
   JSONArray arr=data.optJSONArray("scenes");
   if(arr!=null)for(int i=0;i<arr.length();i++){
    JSONObject o=arr.getJSONObject(i);
    String uri=o.optString("uri","");
    if(uri.isEmpty())continue;
    long dur=o.optLong("duration",0);if(dur<100)continue;
    FlowScene s=new FlowScene(Uri.parse(uri),o.optString("name","Clip"),dur,o.optBoolean("audio",true));
    s.trimStartMs=Math.max(0,o.optLong("start",0));
    s.trimEndMs=Math.min(dur,o.optLong("end",dur));
    if(s.trimEndMs<=s.trimStartMs+100){s.trimStartMs=0;s.trimEndMs=dur;}
    s.caption=o.optString("captions","");
    if(!s.caption.isEmpty())s.subtitles.addAll(CaptionSegment.parse(s.caption,dur));
    scenes.add(s);
   }
   refresh();status.setText("Last project restored. Confirm the imported files are still available.");
  }catch(FileNotFoundException ignored){}catch(Exception e){status.setText("Saved project could not be restored: "+e.getMessage());}
 }

 private void showThumbnail(FlowScene scene, ImageView view){
  io.execute(()->{
   Bitmap bitmap=null;
   MediaMetadataRetriever retriever=new MediaMetadataRetriever();
   try{
    retriever.setDataSource(this,scene.uri);
    bitmap=retriever.getScaledFrameAtTime(scene.trimStartMs*1000+100000L,
     MediaMetadataRetriever.OPTION_CLOSEST_SYNC,160,100);
   }catch(Exception ignored){}
   finally{try{retriever.release();}catch(Exception ignored){}}
   Bitmap ready=bitmap;
   if(ready!=null)runOnUiThread(()->{if(!isFinishing())view.setImageBitmap(ready);});
  });
 }
 private File bgImage()throws Exception{
  int w=720,h=1280;
  if(!scenes.isEmpty()){
   MediaMetadataRetriever r=new MediaMetadataRetriever();
   try{
    r.setDataSource(this,scenes.get(0).uri);
    int rw=Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
    int rh=Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
    String rotate=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
    if("90".equals(rotate)||"270".equals(rotate)){int t=rw;rw=rh;rh=t;}
    if(rw>rh){w=1280;h=720;}
   }catch(Exception ignored){}finally{r.release();}
  }
  Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
  Canvas c=new Canvas(b);Paint p=new Paint(3);
  p.setShader(new LinearGradient(0,0,w,h,new int[]{Color.rgb(8,14,26),Color.rgb(29,44,75),Color.rgb(6,12,24)},null,Shader.TileMode.CLAMP));
  c.drawRect(0,0,w,h,p);p.setShader(null);
  File f=new File(getCacheDir(),"flow-outro-bg.png");
  try(FileOutputStream o=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,o);}
  b.recycle();return f;
 }
 private void render(boolean test){
  if(busy)return;
  if(scenes.isEmpty()){status.setText("Select at least one clip first.");return;}
  if(new StatFs(getCacheDir().getPath()).getAvailableBytes()<300L*1024L*1024L){status.setText("Free 300 MB+ storage for export (long projects need more).");return;}
  persistProject();
  ArrayList<FlowScene> selected=new ArrayList<>();
  long remaining=test?15000L:Long.MAX_VALUE;
  for(FlowScene s:scenes){
   if(remaining<=0)break;
   FlowScene copy=s.copyWithEnd(s.trimStartMs+Math.min(s.editingDurationMs(),remaining));
   if(copy.editingDurationMs()<100)continue;
   selected.add(copy);remaining-=copy.editingDurationMs();
  }
  if(selected.isEmpty()){status.setText("Your current trims contain no exportable video.");return;}
  busy=true;joinButton.setEnabled(false);diagnosticButton.setEnabled(false);cancelButton.setEnabled(true);
  progress.setProgress(2);saved=null;
  File file=new File(getCacheDir(),"cinezehn-full-"+System.currentTimeMillis()+".mp4");activeExport=file;
  try{
   ArrayList<EditedMediaItem> timeline=new ArrayList<>();
   final int fmt=formatSpinner.getSelectedItemPosition();
   final int layout=cropToFill.isChecked()?Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP:Presentation.LAYOUT_SCALE_TO_FIT;
   for(FlowScene s:selected){
    MediaItem.ClippingConfiguration clip=new MediaItem.ClippingConfiguration.Builder()
     .setStartPositionMs(s.trimStartMs).setEndPositionMs(s.trimEndMs).build();
    MediaItem input=new MediaItem.Builder().setUri(s.uri).setClippingConfiguration(clip).build();
    ArrayList<Effect> videoEffects=new ArrayList<>();
    if(fmt==0)videoEffects.add(Presentation.createForWidthAndHeight(720,1280,layout));
    else if(fmt==1)videoEffects.add(Presentation.createForWidthAndHeight(1280,720,layout));
    else if(fmt==2)videoEffects.add(Presentation.createForWidthAndHeight(720,720,layout));
    Effects effects=new Effects(Collections.emptyList(),videoEffects);
    timeline.add(new EditedMediaItem.Builder(input).setEffects(effects).build());
   }
   if(!test&&addEnd.isChecked()){
    long lengthMs=(endLength.getSelectedItemPosition()+3)*1000L;
    MediaItem image=new MediaItem.Builder().setUri(Uri.fromFile(bgImage())).setImageDurationMs(lengthMs).build();
    String txt=endText.getText().toString().trim();
    if(txt.isEmpty())txt="THANKS FOR WATCHING!";
    ArrayList<Effect> endingFx=new ArrayList<>();
    if(fmt==0)endingFx.add(Presentation.createForWidthAndHeight(720,1280,layout));
    else if(fmt==1)endingFx.add(Presentation.createForWidthAndHeight(1280,720,layout));
    else if(fmt==2)endingFx.add(Presentation.createForWidthAndHeight(720,720,layout));
    endingFx.add(new OverlayEffect(Collections.singletonList(new EndOverlay(txt,lengthMs))));
    timeline.add(new EditedMediaItem.Builder(image).setFrameRate(15)
      .setEffects(new Effects(Collections.emptyList(),endingFx)).build());
   }
   Composition.Builder builder=new Composition.Builder(Collections.singletonList(
    EditedMediaItemSequence.withAudioAndVideoFrom(timeline)));
   if(addGraphics.isChecked()||addCaptions.isChecked()||addHost.isChecked()||addReactions.isChecked()){
    String style=CreativeDirector.choose(directorPrompt.getText().toString(),String.valueOf(styleSpinner.getSelectedItem()));
    FlowGraphicsOverlay overlay=new FlowGraphicsOverlay(selected,style,addGraphics.isChecked(),addCaptions.isChecked(),addHost.isChecked(),addReactions.isChecked());
    builder.setEffects(new Effects(Collections.emptyList(),Collections.singletonList(
     new OverlayEffect(Collections.singletonList(overlay)))));
   }
   Composition composition=builder.build();
   transformer=new Transformer.Builder(this).setAudioMimeType(MimeTypes.AUDIO_AAC)
    .setVideoMimeType(MimeTypes.VIDEO_H264).setMaxDelayBetweenMuxerSamplesMs(120000L)
    .addListener(new Transformer.Listener(){
     @Override public void onCompleted(Composition composition,ExportResult result){
      if(!busy)return;
      stopTicker();status.setText("Encoded. Saving video to Gallery...");
      io.execute(()->{
       try{Uri u=save(file);runOnUiThread(()->{
         saved=u;finishBusy();progress.setProgress(100);
         status.setText("Saved to Movies/CineZehn. Test audio, captions and timing before publishing.");
       });}catch(Exception e){failure("Encoded but Gallery save failed: "+e.getMessage());}
       finally{file.delete();}
      });
     }
     @Override public void onError(Composition composition,ExportResult result,ExportException e){
      if(!busy)return;
      file.delete();failure("Export code "+e.errorCode+": "+e.getMessage()+
        (e.getCause()==null?"":"\nCause: "+e.getCause()));
     }
    }).build();
   transformer.start(composition,file.getAbsolutePath());
   status.setText(test?"Rendering 15s diagnostic clip...":"Rendering edited Flow clips locally...");
   poll();
  }catch(Exception e){file.delete();failure("Cannot start export: "+e.getClass().getSimpleName()+": "+e.getMessage());}
 }
 private void finishBusy(){
  stopTicker();busy=false;joinButton.setEnabled(true);diagnosticButton.setEnabled(true);
  cancelButton.setEnabled(false);transformer=null;activeExport=null;
 }
 private void cancelRender(){
  if(!busy||transformer==null)return;
  Transformer old=transformer;
  File incomplete=activeExport;
  finishBusy();
  try{old.cancel();}catch(Exception ignored){}
  if(incomplete!=null)incomplete.delete();
  status.setText("Render cancelled. Original clips and project are unchanged.");
  progress.setProgress(0);
 }
 private void poll(){
  stopTicker();
  polling=new Runnable(){@Override public void run(){
   if(!busy||transformer==null)return;
   try{ProgressHolder p=new ProgressHolder();if(transformer.getProgress(p)==Transformer.PROGRESS_STATE_AVAILABLE){
    progress.setProgress(Math.min(92,4+p.progress*88/100));status.setText("Exporting "+p.progress+"%");
   }}catch(Exception ignored){}
   handler.postDelayed(this,1100);
  }};
  handler.postDelayed(polling,700);
 }
 private void stopTicker(){if(polling!=null){handler.removeCallbacks(polling);polling=null;}}
 private void failure(String message){runOnUiThread(()->{
  finishBusy();status.setText(message);
  new AlertDialog.Builder(this).setTitle("Flow export diagnostic").setMessage(message).setPositiveButton("Copy error",(d,w)->{
   android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
   cm.setPrimaryClip(ClipData.newPlainText("Flow error",message));
  }).setNegativeButton("Close",null).show();
 });}
 private Uri save(File file)throws Exception{
  ContentValues cv=new ContentValues();cv.put(MediaStore.Video.Media.DISPLAY_NAME,"CineZehn_Flow_"+System.currentTimeMillis()+".mp4");
  cv.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
  cv.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/CineZehn");
  cv.put(MediaStore.Video.Media.IS_PENDING,1);
  Uri u=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cv);
  if(u==null)throw new Exception("Gallery insert failed");
  try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(u)){
   if(out==null)throw new Exception("Cannot write Gallery");
   byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
  }catch(Exception e){getContentResolver().delete(u,null,null);throw e;}
  cv.clear();cv.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(u,cv,null,null);return u;
 }
 private void share(){
  if(saved==null){status.setText("Export video first.");return;}
  Intent i=new Intent(Intent.ACTION_SEND);i.setType("video/mp4");i.putExtra(Intent.EXTRA_STREAM,saved);
  i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share Flow video"));
 }
 @Override protected void onDestroy(){
  stopTicker();if(transformer!=null&&busy)transformer.cancel();
  persistProject();
  if(preview!=null)preview.stopPlayback();io.shutdown();super.onDestroy();
 }

 private static final class EndOverlay extends CanvasOverlay {
  private final String title;private final long duration;
  private final Paint paint=new Paint(3);
  EndOverlay(String t,long d){super(true);title=t;duration=d;}
  @Override public void onDraw(Canvas c,long presentationTimeUs){
   float ms=presentationTimeUs/1000f;
   float w=c.getWidth(),h=c.getHeight(),u=Math.min(w/720f,h/1280f);
   float fade=Math.max(0,Math.min(1,Math.min(ms/450f,(duration-ms)/350f)));
   float cx=w*.5f,cy=h*.31f;
   paint.setStyle(Paint.Style.FILL);paint.setColor(Color.argb((int)(100*fade),66,170,247));
   c.drawCircle(cx,cy,(70+8*(float)Math.sin(ms/430f))*u,paint);
   paint.setTextAlign(Paint.Align.CENTER);
   paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(47*u);
   paint.setColor(Color.argb((int)(255*fade),255,255,255));
   drawWrapped(c,title,cx,h*.48f,Math.max(180,(int)(w*.85f)),paint);
   String[] a={"LIKE","COMMENT","SUBSCRIBE"};
   for(int i=0;i<a.length;i++){
    float f=Math.max(0,Math.min(1,(ms-650-i*270)/400f))*fade;if(f<=0)continue;
    float x=w*(.19f+i*.31f),y=h*.67f;
    paint.setColor(i==2?Color.argb((int)(235*f),232,61,77):Color.argb((int)(220*f),52,75,111));
    c.drawRoundRect(x-74*u,y-29*u,x+74*u,y+29*u,18*u,18*u,paint);
    paint.setColor(Color.WHITE);paint.setTextSize((i==2?18:22)*u);
    c.drawText(a[i],x,y+8*u,paint);
   }
  }
  private void drawWrapped(Canvas c,String s,float x,float y,int width,Paint p){
   String[] words=s.trim().split("\\s+");StringBuilder line=new StringBuilder();
   ArrayList<String> lines=new ArrayList<>();
   for(String word:words){
    String candidate=line.length()==0?word:line+" "+word;
    if(p.measureText(candidate)>width && line.length()>0){lines.add(line.toString());line.setLength(0);}
    if(line.length()>0)line.append(" ");line.append(word);
   }
   if(line.length()>0)lines.add(line.toString());
   for(int i=0;i<Math.min(3,lines.size());i++)c.drawText(lines.get(i),x,y+(i-1)*54*(width/720f),p);
  }
 }
}
