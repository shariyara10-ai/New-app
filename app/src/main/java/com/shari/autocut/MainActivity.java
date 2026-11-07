package com.shari.autocut;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.icu.text.Transliterator;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.ProgressHolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.media3.common.util.UnstableApi
public class MainActivity extends Activity {
 private static final int PICK_MEDIA=15,PICK_MODEL=16,PICK_PNG=17;
 private final int bg=Color.rgb(248,250,255),card=Color.WHITE,aqua=Color.rgb(72,91,245),ink=Color.rgb(24,33,57),muted=Color.rgb(99,112,137);
 private final ExecutorService jobs=Executors.newSingleThreadExecutor();
 private Uri source;
 private boolean isAudioOnly=false;
 private long durationMs=30000L;
 private Bitmap selectedPng=null;
 private ArrayList<CaptionSegment> scenes=new ArrayList<>();
 private VideoView preview;
 private TextView status,mediaInfo,modelStatus;
 private ProgressBar progress;
 private EditText transcript,startField,endField;
 private Spinner preset,format;
 private Button transcribeButton,exportButton,autoButton;
 private Uri savedVideo=null;
 private Transformer transformer=null;
 private Button testExportButton;
 private final android.os.Handler renderHandler=new android.os.Handler(android.os.Looper.getMainLooper());
 private Runnable renderProgressTask=null;
 private boolean busy=false;
 private LinearLayout root;
 private int d(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  showEditor();
 }
 private TextView label(String msg,int size,int color,boolean bold) {
  TextView v=new TextView(this);v.setText(msg);v.setTextSize(size);
  int resolved=color==Color.WHITE?ink:color==aqua?aqua:(Color.red(color)+Color.green(color)+Color.blue(color)>390?muted:color);
  v.setTextColor(resolved);
  if(bold)v.setTypeface(null,android.graphics.Typeface.BOLD);return v;
 }
 private Button btn(String text){
  Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);b.setMinHeight(d(51));
  b.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
  b.setTextColor(Color.WHITE);
  android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
  shape.setColor(aqua);shape.setCornerRadius(d(14));b.setBackground(shape);b.setElevation(0);
  return b;
 }
 private void add(LinearLayout target,View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=d(top);target.addView(v,p);}
 private LinearLayout panel(){
  LinearLayout p=new LinearLayout(this);p.setPadding(d(17),d(17),d(17),d(20));p.setOrientation(LinearLayout.VERTICAL);
  android.graphics.drawable.GradientDrawable white=new android.graphics.drawable.GradientDrawable();
  white.setColor(card);white.setCornerRadius(d(22));white.setStroke(d(1),Color.rgb(230,235,245));
  p.setBackground(white);p.setElevation(d(1));add(root,p,15);return p;
 }
 private void showEditor(){
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(d(16),d(25),d(16),d(30));scroll.addView(root);
  scroll.setOnApplyWindowInsetsListener((v,insets)->{
   scroll.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
   return insets;
  });
  setContentView(scroll);
  Button home=btn("‹  Back to CineZehn Dashboard");add(root,home,0);
  home.setOnClickListener(v->finish());
  add(root,label("CINEZEHN / SINGLE VIDEO STUDIO",13,aqua,true),18);
  add(root,label("AI Director + Video Editor",26,Color.WHITE,true),7);
  add(root,label("100% phone-based media editing. No desktop server, account or API key required. Offline Vosk model imported separately.",13,Color.rgb(177,191,210),false),9);
  LinearLayout flow=panel();
  add(flow,label("FLOW / VEO SCENE JOINER",17,Color.WHITE,true),0);
  add(flow,label("Join your generated 8-second clips, keep their original dialogue, then add an animated Like, Comment, Subscribe ending. Memes disabled in this mode.",12,Color.rgb(187,202,216),false),6);
  Button flowOpen=btn("Open Flow Clip Joiner");
  add(flow,flowOpen,8);flowOpen.setOnClickListener(v->startActivity(new Intent(this,FlowJoinActivity.class)));
  LinearLayout media=panel();
  add(media,label("1  Import video or narration",17,Color.WHITE,true),0);
  Button pick=btn("Choose Video / Audio");add(media,pick,12);pick.setOnClickListener(v->choose("video/*\n audio/*",PICK_MEDIA));
  mediaInfo=label("No media selected",13,Color.rgb(197,206,223),false);add(media,mediaInfo,9);
  preview=new VideoView(this);preview.setBackgroundColor(Color.BLACK);
  LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,d(205));vp.topMargin=d(12);media.addView(preview,vp);
  MediaController mc=new MediaController(this);mc.setAnchorView(preview);preview.setMediaController(mc);
  Button playPreview=btn("▶ Play / Pause Video Preview");
  add(media,playPreview,7);
  playPreview.setOnClickListener(v->{
   if(source==null||isAudioOnly){error("Select a video file first.");return;}
   try{if(preview.isPlaying())preview.pause();else preview.start();}
   catch(Exception e){error("Preview playback failed: "+e.getMessage());}
  });
  LinearLayout trimRow=new LinearLayout(this);trimRow.setOrientation(LinearLayout.HORIZONTAL);
  startField=smallField("Start sec", "0");endField=smallField("End sec","30");
  trimRow.addView(startField,new LinearLayout.LayoutParams(0,d(52),1f));
  LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(d(10),1);trimRow.addView(new View(this),gap);
  trimRow.addView(endField,new LinearLayout.LayoutParams(0,d(52),1f));
  add(media,label("Trim range (seconds)",12,Color.rgb(174,191,207),true),12);add(media,trimRow,6);

  LinearLayout model=panel();
  add(model,label("2  Optional offline speech-to-text",17,Color.WHITE,true),0);
  modelStatus=label("No speech model installed. Import a Vosk ZIP once; transcription can then run without internet.",13,Color.rgb(176,194,213),false);
  add(model,modelStatus,7);
  Button getModels=btn("Open official Vosk models page");add(model,getModels,8);
  getModels.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://alphacephei.com/vosk/models"));startActivity(i);});
  Button downloadEnglish=btn("Download & install English model (~40 MB)");add(model,downloadEnglish,9);downloadEnglish.setOnClickListener(v->downloadModel("english"));
  Button downloadHindi=btn("Download & install Hindi model (~42 MB)");add(model,downloadHindi,7);downloadHindi.setOnClickListener(v->downloadModel("hindi"));
  Button downloadIndian=btn("Download & install Indian English model (~36 MB)");add(model,downloadIndian,7);downloadIndian.setOnClickListener(v->downloadModel("indian_english"));
  Button mirror=btn("Open alternate Vosk model mirror (browser)");add(model,mirror,8);
  mirror.setOnClickListener(v->{
   Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://huggingface.co/localstack/vosk-models/tree/main"));
   startActivity(i);
  });
  Button importModel=btn("Or import existing Vosk model ZIP");add(model,importModel,9);importModel.setOnClickListener(v->choose("application/zip",PICK_MODEL));
  transcribeButton=btn("Transcribe selected audio OFFLINE");add(model,transcribeButton,9);transcribeButton.setOnClickListener(v->transcribe());
  add(model,label("Models can be downloaded once and then work offline. Official download retries from a verified model mirror if connection fails. Urdu has no matching small-model guarantee. Hinglish code-switching and Roman spelling may be inaccurate; always correct captions.",12,Color.rgb(164,178,196),false),11);

  LinearLayout edit=panel();
  add(edit,label("3  Transcript & caption timeline",17,Color.WHITE,true),0);
  transcript=new EditText(this);transcript.setTextColor(ink);transcript.setHintTextColor(muted);transcript.setTextSize(14);
  transcript.setHint("[00:00.00-00:02.20] Your first sentence\n[00:02.20-00:04.70] Your second sentence\n\nOr paste plain text.");
  transcript.setMinLines(6);transcript.setGravity(Gravity.TOP|Gravity.START);transcript.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
  transcript.setBackgroundColor(Color.rgb(242,245,252));transcript.setPadding(d(12),d(12),d(12),d(12));add(edit,transcript,10);
  Button romanize=btn("Approx. Romanize Urdu/Hindi → Latin");add(edit,romanize,9);romanize.setOnClickListener(v->romanizeText());
  add(edit,label("Auto planner style",12,Color.rgb(176,194,213),true),11);
  preset=new Spinner(this);preset.setAdapter(spinnerAdapter(DirectorEngine.STYLES));add(edit,preset,6);
  autoButton=btn("Plan caption motion from transcript");add(edit,autoButton,8);autoButton.setOnClickListener(v->plan());
  Button prompt=btn("Generate visual/PNG asset prompts");add(edit,prompt,8);prompt.setOnClickListener(v->showPrompts());
  Button director=btn("AI Director Chat (offline commands)");add(edit,director,9);director.setOnClickListener(v->showDirector());
  Button library=btn("Browse built-in creative recipe library");add(edit,library,8);library.setOnClickListener(v->showLibrary());
  Button png=btn("Add one optional PNG overlay image");add(edit,png,8);png.setOnClickListener(v->choose("image/png",PICK_PNG));

  LinearLayout out=panel();
  add(out,label("4  Export video on this phone",17,Color.WHITE,true),0);
  format=new Spinner(this);format.setAdapter(spinnerAdapter(new String[]{"9:16 Shorts • 720×1280 target","16:9 YouTube • 1280×720 target","1:1 Square"}));add(out,format,10);
  exportButton=btn("Render and save MP4 to Gallery");add(out,exportButton,10);exportButton.setOnClickListener(v->export());
  testExportButton=btn("TEST EXPORT: First 15 Seconds");add(out,testExportButton,8);testExportButton.setOnClickListener(v->export(true));
  add(out,label("For long audio-only videos, test 15 seconds before rendering the entire recording. Audio-only exports use phone-friendly 15 FPS settings.",12,Color.rgb(171,193,212),false),7);
  Button share=btn("Share last export");add(out,share,6);share.setOnClickListener(v->shareExport());
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);add(out,progress,9);
  status=label("Ready. Import a media file; paste transcript or add an offline speech model.",13,Color.rgb(191,211,226),false);add(out,status,9);
  add(root,label("Local processing: Media3 Transformer + Vosk. Offline Director is rule-based; no LLM is bundled. This experimental release may fail on unsupported codecs and large videos.",12,Color.rgb(146,161,182),false),22);
  refreshModel();
 }
 private ArrayAdapter<String> spinnerAdapter(String[] items){
  return new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,items){
   @Override public View getView(int position,View reused,android.view.ViewGroup parent){
    TextView t=(TextView)super.getView(position,reused,parent);
    t.setTextColor(ink);t.setTextSize(15);return t;
   }
   @Override public View getDropDownView(int position,View reused,android.view.ViewGroup parent){
    TextView t=(TextView)super.getDropDownView(position,reused,parent);
    t.setTextColor(Color.BLACK);t.setTextSize(15);return t;
   }
  };
 }
 private EditText smallField(String hint,String value){
  EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(value);
  e.setTextColor(ink);e.setTextSize(14);e.setPadding(d(8),0,d(8),0);
  e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
  e.setBackgroundColor(Color.rgb(241,245,251));return e;
 }
 private void choose(String mime,int request) {
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  i.addCategory(Intent.CATEGORY_OPENABLE);
  if(request==PICK_MEDIA){i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/mp4","video/*","audio/*"});}
  else {i.setType(mime);}
  i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  startActivityForResult(i,request);
 }
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(result!=RESULT_OK||data==null||data.getData()==null)return;
  Uri uri=data.getData();
  if(req==PICK_MEDIA){loadMedia(uri);}
  if(req==PICK_MODEL){importModel(uri);}
  if(req==PICK_PNG){loadPng(uri);}
 }
 private void loadMedia(Uri uri){
  if(busy)return;
  try{
   source=uri;
   getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
   MediaMetadataRetriever retriever=new MediaMetadataRetriever();
   retriever.setDataSource(this,uri);
   String dur=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
   String video=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
   durationMs=dur==null?30000:Long.parseLong(dur);
   if(durationMs<=0)durationMs=30000;
   isAudioOnly=!"yes".equalsIgnoreCase(video);
   retriever.release();
   startField.setText("0");
   endField.setText(String.format(Locale.US,"%.1f",durationMs/1000.0));
   mediaInfo.setText((isAudioOnly?"AUDIO":"VIDEO")+" selected • "+CaptionSegment.timestamp(durationMs)+" • local file");
   preview.setVisibility(isAudioOnly?View.GONE:View.VISIBLE);
   preview.setVideoURI(uri);
   scenes.clear();transcript.setText("");
   update("Media selected. You can transcribe or paste text.");progress.setProgress(0);
  }catch(Exception ex){source=null;error("Could not open media: "+ex.getMessage());}
 }
 private void loadPng(Uri u) {
  try(InputStream in=getContentResolver().openInputStream(u)){
   BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;
   selectedPng=BitmapFactory.decodeStream(in,null,opts);
   if(selectedPng==null)throw new Exception("Invalid image");
   update("PNG image selected. Overlay will appear at top-right while captions display.");
  }catch(Exception e){error("PNG import failed: "+e.getMessage());}
 }
 private void downloadModel(String language){
  if(busy)return;
  setBusy(true);
  modelStatus.setText("Downloading from official server; alternate mirror will be tried automatically...");
  update("Downloading model; keep this app open and use a stable connection.");
  jobs.execute(()->{
   try{
    File m=ModelImporter.downloadAndInstall(this,language,p->runOnUiThread(()->{
     if(modelStatus!=null)modelStatus.setText("Downloading "+language+" model (official/mirror): "+p+"%");
     if(progress!=null)progress.setProgress(p);
    }));
    runOnUiThread(()->{
     setBusy(false);refreshModel();update("Model download verified and installed. Now tap Transcribe selected audio OFFLINE.");
     if(progress!=null)progress.setProgress(100);
    });
   }catch(Exception e){
    runOnUiThread(()->{
     setBusy(false);refreshModel();
     error("Model download failed: "+e.getMessage());
    });
   }
  });
 }
 private void importModel(Uri u){
  if(busy)return;setBusy(true);update("Validating and extracting Vosk model ZIP on phone...");
  jobs.execute(()->{
   try{File m=ModelImporter.install(this,u);runOnUiThread(()->{setBusy(false);refreshModel();update("Offline model ready: "+m.getName());});}
   catch(Exception e){runOnUiThread(()->{setBusy(false);refreshModel();error("Model import failed: "+e.getMessage());});}
  });
 }
 private void refreshModel(){if(modelStatus!=null){File f=ModelImporter.active(this);modelStatus.setText(f==null?"No offline model. Download small English/Hindi ZIP from official page, then import.":"Installed offline model: "+f.getName()+" • transcription ready.");}}
 private void transcribe(){
  if(busy)return;
  if(source==null){error("First select a video or audio file.");return;}
  File model=ModelImporter.active(this);
  if(model==null){error("First download and import a Vosk speech model ZIP.");return;}
  setBusy(true);update("Transcribing locally. Long files may take time; keep app open.");
  jobs.execute(()->{
   try{
    ArrayList<CaptionSegment> found=OfflineTranscriber.transcribe(this,source,model,p->runOnUiThread(()->progress.setProgress(p)));
    runOnUiThread(()->{
     scenes=found;transcript.setText(CaptionSegment.format(found));
     setBusy(false);
     if(found.isEmpty())error("Transcription finished but no words detected. Try another speech model or paste transcript.");
     else{update("Offline transcription finished: "+found.size()+" caption segments. Review spelling and timestamps.");progress.setProgress(100);}
    });
   }catch(Exception e){runOnUiThread(()->{setBusy(false);error("Transcription failed: "+e.getClass().getSimpleName()+": "+e.getMessage());});}
  });
 }
 private void romanizeText(){
  try{
   Transliterator t=Transliterator.getInstance("Any-Latin; Latin-ASCII");
   transcript.setText(t.transliterate(transcript.getText().toString()));
   update("Rough Latin transliteration generated. Edit Hinglish pronunciation manually.");
  }catch(Exception ex){error("Romanization not supported on this device.");}
 }
 private String presetValue(){return String.valueOf(preset.getSelectedItem()).toLowerCase(Locale.ROOT);}
 private ArrayList<CaptionSegment> parsed() {
  long clipDuration=Math.max(1000,durationMs);
  return CaptionSegment.parse(transcript.getText().toString(),clipDuration);
 }
 private void plan(){
  ArrayList<CaptionSegment> parsed=parsed();
  if(parsed.isEmpty()){error("Enter transcript with timestamp lines, or paste plain text.");return;}
  scenes=parsed;AutoPlanner.apply(scenes,presetValue());
  update("Caption motion ready. The asset-prompt tool now groups nearby captions into broader ~30-second visual beats, not one PNG for every sentence.");
 }
 private void showPrompts(){
  plan();if(scenes.isEmpty())return;
  final String text=AutoPlanner.assetPrompts(scenes,presetValue());
  ScrollView sv=new ScrollView(this);
  TextView t=label(text,13,Color.DKGRAY,false);t.setPadding(d(20),d(10),d(20),d(10));sv.addView(t);
  new AlertDialog.Builder(this).setTitle("Scene PNG & motion prompts").setView(sv)
   .setPositiveButton("Copy all",(dialog,which)->{
    android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    cm.setPrimaryClip(android.content.ClipData.newPlainText("CineZehn prompts",text));update("Prompts copied to clipboard.");
   }).setNegativeButton("Close",null).show();
 }
 private void showDirector(){
  EditText command=new EditText(this);command.setHint("Example: Netflix-style documentary, strong captions, cinematic zooms");command.setMinLines(3);
  new AlertDialog.Builder(this).setTitle("Offline AI Director — rule-based beta")
   .setMessage("Runs without internet. This version interprets a limited set of style commands; it is not a local ChatGPT model.")
   .setView(command).setPositiveButton("Apply to captions",(dlg,w)->{
     ArrayList<CaptionSegment> current=parsed();
     String report=DirectorEngine.run(command.getText().toString(),current,presetValue());
     if(!current.isEmpty()){scenes=current;transcript.setText(CaptionSegment.format(current));}
     new AlertDialog.Builder(this).setTitle("Director plan").setMessage(report).setPositiveButton("OK",null).show();
   }).setNegativeButton("Cancel",null).show();
 }
 private void showLibrary(){
  StringBuilder b=new StringBuilder("Installed procedural recipe catalog (not downloadable stock assets):\\n\\n");
  for(String asset:DirectorEngine.ASSETS)b.append("• ").append(asset).append("\\n");
  b.append("\\nOnly caption overlays and one imported PNG can currently be rendered. These recipes are planning suggestions, not complete animations.");
  new AlertDialog.Builder(this).setTitle("Offline Creative Library").setMessage(b.toString()).setPositiveButton("OK",null).show();
 }
 private long[] trimTimes(){
  try{
   long start=Math.round(Double.parseDouble(startField.getText().toString())*1000);
   long end=Math.round(Double.parseDouble(endField.getText().toString())*1000);
   if(start<0||end<=start||end>durationMs+500)throw new Exception("Out-of-range trim");
   return new long[]{start,Math.min(end,durationMs)};
  }catch(Exception e){return null;}
 }
 private File backgroundForAudio(long lenMs) throws Exception {
  int w=720,h=1280;
  Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
  Canvas c=new Canvas(b);Paint p=new Paint(3);
  p.setShader(new LinearGradient(0,0,w,h,new int[]{Color.rgb(8,15,37),Color.rgb(27,48,83),Color.rgb(5,11,30)},null,Shader.TileMode.CLAMP));
  c.drawRect(0,0,w,h,p);p.setShader(null);
  p.setTextSize(36f);p.setColor(Color.rgb(210,216,224));p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);
  c.drawText("CINEZEHN  •  AUDIO STORY",w/2f,140f,p);
  p.setTextSize(23f);p.setColor(Color.rgb(121,192,226));c.drawText("AUTO-CUT OFFLINE",w/2f,1220f,p);
  File f=new File(getCacheDir(),"cinezehn_story_background.png");
  try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,95,out);}
  b.recycle();return f;
 }
 private void export(){export(false);}
 private void export(boolean diagnosticTest){
  if(busy)return;
  if(source==null){error("Import video or audio first.");return;}
  long[] trim=trimTimes();
  if(trim==null){error("Invalid start/end. End must be after start and within media duration.");return;}
  if(diagnosticTest)trim[1]=Math.min(trim[1],trim[0]+15000L);
  long available=new StatFs(getCacheDir().getAbsolutePath()).getAvailableBytes();
  if(available<200L*1024*1024) {
   error("Not enough free storage for MP4 export. Free at least 200 MB, preferably 1 GB for long clips.");return;
  }
  ArrayList<CaptionSegment> parsed=parsed();
  if(parsed.isEmpty()){error("Enter a transcript for motion captions, or use transcription.");return;}
  AutoPlanner.apply(parsed,presetValue());scenes=parsed;
  setBusy(true);progress.setProgress(8);update("Preparing offline video render...");
  final long start=trim[0],end=trim[1];
  final boolean audioOnlyExport=isAudioOnly;
  File out=new File(getCacheDir(),"cinezehn_export_"+System.currentTimeMillis()+".mp4");
  try {
   final List<Effect> videoFx=new ArrayList<>();
   int f=format.getSelectedItemPosition();
   float ratio=f==0?9f/16f:f==1?16f/9f:1f;
   int height=f==0?1280:f==1?720:720;
   if(audioOnlyExport && (end-start)>60000)height=f==0?960:540;
   videoFx.add(Presentation.createForAspectRatio(ratio,Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP));
   videoFx.add(Presentation.createForHeight(height));
   videoFx.add(new OverlayEffect(Collections.singletonList(new CaptionOverlay(parsed,presetValue(),selectedPng,start))));
   Effects fx=new Effects(Collections.emptyList(),videoFx);
   transformer=new Transformer.Builder(this)
    .setVideoMimeType(MimeTypes.VIDEO_H264)
    .setAudioMimeType(MimeTypes.AUDIO_AAC)
    .setMaxDelayBetweenMuxerSamplesMs(120000L)
    .addListener(new Transformer.Listener() {
     @Override public void onCompleted(Composition composition,ExportResult result) {
      stopProgressPolling();
      update("Encoding complete. Saving MP4 to phone Gallery...");progress.setProgress(92);
      jobs.execute(()->{
       try{Uri saved=saveVideo(out);runOnUiThread(()->{savedVideo=saved;setBusy(false);progress.setProgress(100);update("Export saved successfully to Movies/CineZehn. Open Gallery or tap Share.");});}
       catch(Exception ex){runOnUiThread(()->{setBusy(false);error("Video encoded but Gallery saving failed: "+ex.getMessage());});}
       finally{out.delete();}
      });
     }
     @Override public void onError(Composition composition,ExportResult result,ExportException ex) {
      stopProgressPolling();
      setBusy(false);out.delete();reportExportError(ex,start,end,audioOnlyExport);
     }
    }).build();
   MediaItem.ClippingConfiguration clipping=new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(end).build();
   if(!isAudioOnly) {
    MediaItem item=new MediaItem.Builder().setUri(source).setClippingConfiguration(clipping).build();
    transformer.start(new EditedMediaItem.Builder(item).setEffects(fx).build(),out.getAbsolutePath());
   }else{
    File background=backgroundForAudio(end-start);
    MediaItem image=new MediaItem.Builder().setUri(Uri.fromFile(background)).setImageDurationMs(end-start).build();
    EditedMediaItem video=new EditedMediaItem.Builder(image).setFrameRate(15).setEffects(fx).build();
    MediaItem audio=new MediaItem.Builder().setUri(source).setClippingConfiguration(clipping).build();
    EditedMediaItem sound=new EditedMediaItem.Builder(audio).build();
    Composition composition=new Composition.Builder(Arrays.asList(
     EditedMediaItemSequence.withVideoFrom(Collections.singletonList(video)),
     EditedMediaItemSequence.withAudioFrom(Collections.singletonList(sound))
    )).build();
    transformer.start(composition,out.getAbsolutePath());
   }
   progress.setProgress(20);
   update(diagnosticTest?"Rendering 15-second diagnostic video...":"Rendering MP4 locally. Audio-only export uses 15 fps for compatibility.");
   startProgressPolling();
  }catch(Exception e){stopProgressPolling();setBusy(false);out.delete();error("Render cannot start: "+e.getClass().getSimpleName()+": "+e.getMessage());}
 }

 private void startProgressPolling(){
  stopProgressPolling();
  renderProgressTask=new Runnable(){
   @Override public void run(){
    if(transformer==null || !busy)return;
    try{
     ProgressHolder holder=new ProgressHolder();
     int state=transformer.getProgress(holder);
     if(state==Transformer.PROGRESS_STATE_AVAILABLE){
      int percentage=Math.max(20,Math.min(90,20+(int)(holder.progress*0.70f)));
      progress.setProgress(percentage);
      update("Rendering: "+holder.progress+"% • Keep the app open.");
     }
     if(state==Transformer.PROGRESS_STATE_NOT_STARTED)return;
    }catch(Exception ignored){}
    renderHandler.postDelayed(this,1200);
   }
  };
  renderHandler.postDelayed(renderProgressTask,1000);
 }
 private void stopProgressPolling(){
  if(renderProgressTask!=null){renderHandler.removeCallbacks(renderProgressTask);renderProgressTask=null;}
 }

 private void reportExportError(ExportException exception,long start,long end,boolean audioOnly){
  StringBuilder details=new StringBuilder();
  details.append("Export failed. Error code: ").append(exception.errorCode).append("\n");
  details.append("Message: ").append(exception.getMessage()).append("\n");
  Throwable cause=exception.getCause();int level=0;
  while(cause!=null&&level++<4){
   details.append("Cause ").append(level).append(": ").append(cause.getClass().getSimpleName());
   if(cause.getMessage()!=null)details.append(" — ").append(cause.getMessage());
   details.append("\n");cause=cause.getCause();
  }
  details.append("Media type: ").append(audioOnly?"AUDIO ONLY":"VIDEO");
  details.append("\nRange: ").append(CaptionSegment.timestamp(start)).append("–").append(CaptionSegment.timestamp(end));
  details.append("\nPhone: ").append(android.os.Build.MODEL).append(" / Android ").append(android.os.Build.VERSION.RELEASE);
  details.append("\nFree cache space: ").append(new StatFs(getCacheDir().getAbsolutePath()).getAvailableBytes()/1024/1024).append(" MB");
  String diagnostic=details.toString();
  android.util.Log.e("CineZehn","Export diagnostic: "+diagnostic,exception);
  status.setText("Export failed (code "+exception.errorCode+"). Use Copy Diagnostics below; try TEST EXPORT: First 15 Seconds.");
  String advice=exception.errorCode==ExportException.ERROR_CODE_MUXING_TIMEOUT
   ?"Muxer stopped receiving samples. Try exporting a 15-second sample or use a shorter section. A longer watchdog cannot repair a permanently stalled decoder."
   :exception.errorCode==ExportException.ERROR_CODE_MUXING_FAILED
   ?"Media muxer failed. Try the 15-second export; if that also fails, test another audio file (for example standard MP3/M4A) and check free storage."
   :"Export pipeline failed. Note the code/cause and test a shorter segment.";
  new AlertDialog.Builder(this)
   .setTitle("MP4 export diagnostic")
   .setMessage(advice+"\n\n"+diagnostic)
   .setPositiveButton("Copy diagnostics",(dialog,which)->{
    android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CineZehn export diagnostic",diagnostic));
    Toast.makeText(this,"Details copied. Share them for targeted troubleshooting.",Toast.LENGTH_LONG).show();
   })
   .setNegativeButton("Close",null)
   .show();
 }
 private Uri saveVideo(File src) throws Exception{
  String name="CineZehn_"+System.currentTimeMillis()+".mp4";
  ContentValues cv=new ContentValues();cv.put(MediaStore.Video.Media.DISPLAY_NAME,name);cv.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
  cv.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/CineZehn");
  cv.put(MediaStore.Video.Media.IS_PENDING,1);
  Uri u=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cv);
  if(u==null)throw new IllegalStateException("Cannot create video in MediaStore");
  try(InputStream in=new java.io.FileInputStream(src);OutputStream output=getContentResolver().openOutputStream(u)){
   if(output==null)throw new IllegalStateException("Cannot write output video");
   byte[] data=new byte[128*1024];int n;while((n=in.read(data))!=-1)output.write(data,0,n);
  }catch(Exception e){getContentResolver().delete(u,null,null);throw e;}
  cv.clear();cv.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(u,cv,null,null);
  return u;
 }
 private void shareExport(){
  if(savedVideo==null){error("Export a video before sharing.");return;}
  Intent i=new Intent(Intent.ACTION_SEND);i.setType("video/mp4");i.putExtra(Intent.EXTRA_STREAM,savedVideo);
  i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share edited MP4"));
 }
 private void setBusy(boolean yes){busy=yes;if(transcribeButton!=null)transcribeButton.setEnabled(!yes);if(exportButton!=null)exportButton.setEnabled(!yes);if(testExportButton!=null)testExportButton.setEnabled(!yes);if(autoButton!=null)autoButton.setEnabled(!yes);}
 private void update(String message){if(status!=null)status.setText(message);}
 private void error(String message){update(message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
 @Override protected void onDestroy(){stopProgressPolling();if(transformer!=null&&busy)transformer.cancel();if(preview!=null)preview.stopPlayback();jobs.shutdown();super.onDestroy();}
}
