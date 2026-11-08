package com.shari.autocut;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.icu.text.Transliterator;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Actual offline Whisper speech-to-text studio using whisper.cpp native JNI. */
public final class WhisperStudioActivity extends Activity {
 private static final int PICK_MODEL=761,PICK_MEDIA=762;
 private static final int BG=0xFF0C101C,SURFACE=0xFF1C2031,PURPLE=0xFF7957EF,
    WHITE=0xFFF7F6FF,MUTED=0xFFB4BBD1;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final ArrayList<CaptionSegment> result=new ArrayList<>();
 private Uri source;
 private TextView modelStatus,mediaStatus,status;
 private EditText transcript;
 private ProgressBar progress;
 private Spinner language;
 private Button transcribe;
 private boolean busy;
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private GradientDrawable shape(int color,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
 }
 private TextView title(String s,int size,boolean bold,int color){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
  if(bold)t.setTypeface(null,Typeface.BOLD);return t;
 }
 private Button button(String label,boolean primary){
  Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);
  b.setTextColor(primary?WHITE:MUTED);b.setBackground(shape(primary?PURPLE:0xFF2B3047,12));
  b.setElevation(0);b.setMinHeight(dp(49));return b;
 }
 private void add(LinearLayout container,View widget,int top){
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);container.addView(widget,p);
 }
 private LinearLayout section(LinearLayout root,String name){
  LinearLayout card=new LinearLayout(this);card.setOrientation(1);
  card.setPadding(dp(16),dp(17),dp(16),dp(17));card.setBackground(shape(SURFACE,18));
  add(root,card,14);add(card,title(name,16,true,WHITE),0);return card;
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setStatusBarColor(BG);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);
  root.setPadding(dp(15),dp(18),dp(15),dp(26));scroll.addView(root);setContentView(scroll);
  Button back=button("‹  CineZehn Dashboard",false);add(root,back,0);back.setOnClickListener(v->finish());
  add(root,title("WHISPER TRANSCRIPT",26,true,WHITE),21);
  add(root,title("Real local whisper.cpp speech recognition • import a multilingual GGML model once • no cloud required.",12,false,MUTED),10);
  LinearLayout models=section(root,"01 • Offline Whisper model");
  modelStatus=title("Checking model...",13,false,MUTED);add(models,modelStatus,10);
  Button importModel=button("Import Whisper .bin model from Downloads",true);
  add(models,importModel,10);importModel.setOnClickListener(v->pick(PICK_MODEL));
  Button browse=button("Open official model repository",false);add(models,browse,8);
  browse.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,
    Uri.parse("https://huggingface.co/ggerganov/whisper.cpp/tree/main"))));
  add(models,title("Recommended for Pixel 7 Pro: multilingual tiny/base GGML model; bigger models may run slowly or run out of memory. Model license and disk requirements apply.",12,false,MUTED),12);
  LinearLayout audio=section(root,"02 • Choose narration or video");
  mediaStatus=title("No media selected",13,false,MUTED);add(audio,mediaStatus,10);
  Button choose=button("Import audio / video file",true);add(audio,choose,10);
  choose.setOnClickListener(v->pick(PICK_MEDIA));
  add(audio,title("Spoken Urdu, Hindi, English and mixed-language results depend on model/recording; always review transcription.",12,false,MUTED),10);
  LinearLayout settings=section(root,"03 • Transcribe");
  language=new Spinner(this);
  String[] languages={"Auto Detect","English","Urdu","Hindi","Arabic"};
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,languages);
  language.setAdapter(adapter);add(settings,language,10);
  transcribe=button("TRANSCRIBE WITH OFFLINE WHISPER",true);add(settings,transcribe,11);
  transcribe.setOnClickListener(v->runInference());
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
  progress.setMax(100);add(settings,progress,10);
  status=title("Ready when model + audio are selected",12,false,MUTED);add(settings,status,8);
  LinearLayout transcriptCard=section(root,"04 • Edit transcript & export");
  transcript=new EditText(this);transcript.setMinLines(7);transcript.setMaxLines(18);
  transcript.setTextColor(WHITE);transcript.setHintTextColor(MUTED);transcript.setTextSize(13);
  transcript.setHint("Timestamped transcription will appear here.\n[00:00.00-00:03.00] Spoken words ...");
  transcript.setPadding(dp(12),dp(12),dp(12),dp(12));transcript.setBackground(shape(0xFF262B40,13));
  add(transcriptCard,transcript,10);
  Button latin=button("Approx. Romanize Hindi / Urdu → Latin",false);add(transcriptCard,latin,10);
  latin.setOnClickListener(v->romanize());
  Button srt=button("Export SRT subtitle file",false);add(transcriptCard,srt,10);
  srt.setOnClickListener(v->exportCaptions("srt"));
  Button vtt=button("Export VTT subtitle file",false);add(transcriptCard,vtt,8);
  vtt.setOnClickListener(v->exportCaptions("vtt"));
  Button copy=button("Copy transcript",false);add(transcriptCard,copy,8);
  copy.setOnClickListener(v->{
   android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
   cm.setPrimaryClip(ClipData.newPlainText("Whisper transcript",transcript.getText()));
   Toast.makeText(this,"Transcript copied",Toast.LENGTH_SHORT).show();
  });
  Button apply=button("Apply transcript captions to Pro project",true);
  add(transcriptCard,apply,12);apply.setOnClickListener(v->applyToProject());
  add(root,title("Model inference runs entirely on-device. Word-level forced alignment, diarization and 100% accurate Hinglish are not guaranteed by this engine.",11,false,MUTED),16);
  refresh();
 }
 private void refresh(){
  File m=WhisperModelStore.current(this);
  modelStatus.setText(m==null?"No Whisper GGML model installed":("Ready • "+Math.round(m.length()/1048576d)+" MB model installed"));
  if(!WhisperBridge.INSTALLED)modelStatus.append("\nNative engine unavailable on this device/build");
  if(source!=null)mediaStatus.setText("Media: "+source.getLastPathSegment());
 }
 private void pick(int request){
  if(busy)return;
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
  i.setType(request==PICK_MODEL?"*/*":"*/*");
  if(request==PICK_MEDIA)i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"});
  i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  startActivityForResult(i,request);
 }
 @Override protected void onActivityResult(int request,int resultCode,Intent data){
  super.onActivityResult(request,resultCode,data);
  if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
  Uri picked=data.getData();
  try{getContentResolver().takePersistableUriPermission(picked,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
  catch(Exception ignored){}
  if(request==PICK_MEDIA){
   source=picked;refresh();return;
  }
  if(request==PICK_MODEL){
   status.setText("Importing model into private offline storage...");
   transcribe.setEnabled(false);
   worker.execute(()->{
    try{
     WhisperModelStore.importBin(this,picked);
     runOnUiThread(()->{refresh();transcribe.setEnabled(true);status.setText("Model imported. Choose audio and tap Transcribe.");});
    }catch(Exception e){runOnUiThread(()->{transcribe.setEnabled(true);status.setText("Model import failed: "+e.getMessage());});}
   });
  }
 }
 private void runInference(){
  if(busy)return;
  File model=WhisperModelStore.current(this);
  if(model==null){status.setText("Import a Whisper GGML model first.");return;}
  if(source==null){status.setText("Import audio/video first.");return;}
  if(!WhisperBridge.INSTALLED){status.setText("Whisper native engine missing in this APK.");return;}
  busy=true;transcribe.setEnabled(false);progress.setProgress(0);
  String[] codes={"auto","en","ur","hi","ar"};
  String lang=codes[Math.max(0,Math.min(4,language.getSelectedItemPosition()))];
  status.setText("Decoding speech and running local Whisper model; keep app open.");
  worker.execute(()->{
   try{
    ArrayList<CaptionSegment> recognized=WhisperTranscriber.transcribe(this,source,model,lang,
     v->runOnUiThread(()->progress.setProgress(v)));
    runOnUiThread(()->{
     busy=false;transcribe.setEnabled(true);
     result.clear();result.addAll(recognized);
     transcript.setText(CaptionSegment.format(recognized));
     status.setText("Whisper finished: "+recognized.size()+" timed segments. Check every word before using captions.");
     progress.setProgress(100);
    });
   }catch(Exception e){runOnUiThread(()->{
     busy=false;transcribe.setEnabled(true);
     status.setText("Whisper failed: "+e.getClass().getSimpleName()+" • "+e.getMessage());
     progress.setProgress(0);
   });}
  });
 }
 private void romanize(){
  String original=transcript.getText().toString();
  if(original.trim().isEmpty())return;
  try{
   Transliterator transliterator=Transliterator.getInstance("Any-Latin; Latin-ASCII");
   transcript.setText(transliterator.transliterate(original));
   status.setText("Approximate transliteration. Correct the Roman Urdu/Hinglish words manually.");
  }catch(Exception e){status.setText("Romanize error: "+e.getMessage());}
 }
 private String srtTime(long ms){
  return String.format(Locale.US,"%02d:%02d:%02d,%03d",ms/3600000,ms/60000%60,ms/1000%60,ms%1000);
 }
 private void exportCaptions(String format){
  try{
   ArrayList<CaptionSegment> segments=CaptionSegment.parse(transcript.getText().toString(),15*60*1000L);
   if(segments.isEmpty())throw new IOException("No timestamped transcript to export.");
   StringBuilder out=new StringBuilder();
   if(format.equals("vtt"))out.append("WEBVTT\n\n");
   int n=0;
   for(CaptionSegment s:segments){
    if(format.equals("srt"))out.append(++n).append("\n");
    String begin=srtTime(s.startMs),end=srtTime(s.endMs);
    if(format.equals("vtt")){begin=begin.replace(',','.');end=end.replace(',','.');}
    out.append(begin).append(" --> ").append(end).append("\n").append(s.text).append("\n\n");
   }
   String body=out.toString();
   if(format.equals("vtt"))body=body.replace(',', '.');
   ContentValues values=new ContentValues();
   values.put(MediaStore.Downloads.DISPLAY_NAME,"CineZehn_Whisper_"+System.currentTimeMillis()+"."+format);
   values.put(MediaStore.Downloads.MIME_TYPE,format.equals("srt")?"application/x-subrip":"text/vtt");
   values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/CineZehn");
   Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
   if(uri==null)throw new IOException("Cannot create Downloads file");
   try(OutputStream file=getContentResolver().openOutputStream(uri)){
    if(file==null)throw new IOException("File output stream unavailable");
    file.write(body.getBytes(StandardCharsets.UTF_8));
   }
   status.setText("Exported "+segments.size()+" captions to Downloads/CineZehn.");
  }catch(Exception e){status.setText("Subtitle export failed: "+e.getMessage());}
 }
 private void applyToProject(){
  ArrayList<CaptionSegment> segments=CaptionSegment.parse(transcript.getText().toString(),15*60*1000L);
  if(segments.isEmpty()){status.setText("No valid timestamped lines to apply.");return;}
  try{
   StudioProject project=StudioProject.load(this);
   project.timedCaptions.clear();
   project.timedCaptions.addAll(segments);
   for(StudioProject.Shot shot:project.shots){
    StringBuilder b=new StringBuilder();
    for(CaptionSegment cue:segments)
     if(cue.startMs<shot.endMs&&cue.endMs>shot.startMs){
      if(b.length()>0)b.append(" ");b.append(cue.text);
     }
    shot.caption=b.toString();shot.manualCaption=false;
   }
   if(source!=null)project.audioUri=source;
   project.save(this);
   status.setText("Applied transcript to "+project.shots.size()+" storyboard shots.");
  }catch(Exception e){status.setText("Could not apply captions: "+e.getMessage());}
 }
 @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
