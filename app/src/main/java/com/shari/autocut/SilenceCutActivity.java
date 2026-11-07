package com.shari.autocut;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.transformer.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Offline energy-based silence cutter. Not a neural speech VAD.
 * Source files remain unchanged. User reviews and selects the edit before export.
 */
@androidx.media3.common.util.UnstableApi
public final class SilenceCutActivity extends Activity {
 private static final int REQUEST_MEDIA=301;
 private final ExecutorService workers=Executors.newSingleThreadExecutor();
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final int BLUE=Color.rgb(72,91,245),INK=Color.rgb(24,34,57),GREY=Color.rgb(98,113,140);
 private Uri source;private boolean hasVideo;private long durationMs;
 private final ArrayList<Range> ranges=new ArrayList<>();
 private Spinner sensitivity;private CheckBox trimLeadingTrailing;
 private TextView mediaInfo,result,status;
 private ProgressBar progress;private Button analyzeButton,renderButton,testButton,cancelButton;
 private VideoView preview;private Transformer transformer;private Runnable progressTask;
 private File outputFile;private Uri lastSaved;private boolean busy;
 private static final class Range {
  long a,b;Range(long a,long b){this.a=a;this.b=b;}long length(){return b-a;}
 }
 private static final class SampleWindow {
  long time;double rms;
  SampleWindow(long time,double rms){this.time=time;this.rms=rms;}
 }
 private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
 private TextView text(String s,int size,boolean bold,int color){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
  if(bold)t.setTypeface(null,Typeface.BOLD);return t;
 }
 private void add(LinearLayout to,View view,int margin){
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(margin);to.addView(view,lp);
 }
 private GradientDrawable bg(int color,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
 }
 private Button action(String title,boolean primary){
  Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(13);b.setElevation(0);
  b.setTextColor(primary?Color.WHITE:INK);
  b.setBackground(bg(primary?BLUE:Color.rgb(237,241,251),13));b.setMinHeight(dp(50));return b;
 }
 private LinearLayout card(LinearLayout root,String name){
  LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);
  l.setPadding(dp(16),dp(17),dp(16),dp(19));l.setBackground(bg(Color.WHITE,22));
  add(root,l,14);add(l,text(name,17,true,INK),0);return l;
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(248,250,255));
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(248,250,255));
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
  root.setPadding(dp(17),dp(19),dp(17),dp(28));scroll.addView(root);setContentView(scroll);
  Button home=action("‹  Dashboard",false);add(root,home,0);home.setOnClickListener(v->finish());
  add(root,text("Silence Cutter",29,true,INK),22);
  add(root,text("Analyze real audio locally, remove long quiet gaps, and export a tighter edit. No Internet or AI account required.",13,false,GREY),8);
  LinearLayout importCard=card(root,"1  Import Video or Audio");
  Button pick=action("Choose recording or video",true);add(importCard,pick,12);pick.setOnClickListener(v->choose());
  mediaInfo=text("No file selected",13,false,GREY);add(importCard,mediaInfo,10);
  preview=new VideoView(this);preview.setBackgroundColor(Color.rgb(15,21,33));
  importCard.addView(preview,new LinearLayout.LayoutParams(-1,dp(178)));
  MediaController ctl=new MediaController(this);ctl.setAnchorView(preview);preview.setMediaController(ctl);
  Button play=action("Play / Pause original",false);add(importCard,play,9);
  play.setOnClickListener(v->{if(source!=null&&hasVideo){if(preview.isPlaying())preview.pause();else preview.start();}});
  LinearLayout settings=card(root,"2  Silence Detection");
  add(settings,text("Sensitivity",12,true,GREY),9);
  sensitivity=new Spinner(this);
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
   new String[]{"Gentle: remove only very quiet gaps","Balanced: standard voice pauses","Strong: remove more low-volume sections"});
  sensitivity.setAdapter(adapter);sensitivity.setSelection(1);add(settings,sensitivity,8);
  trimLeadingTrailing=new CheckBox(this);trimLeadingTrailing.setText("Add padding around detected sound to avoid cutting words");
  trimLeadingTrailing.setTextColor(INK);trimLeadingTrailing.setChecked(true);add(settings,trimLeadingTrailing,8);
  analyzeButton=action("Analyze audio OFFLINE",true);add(settings,analyzeButton,13);
  analyzeButton.setOnClickListener(v->analyze());
  add(settings,text("Audio-volume detection only — music, wind, background noise and quiet speech can affect cuts. Review results before export.",12,false,GREY),9);
  LinearLayout previewCard=card(root,"3  Detected Edit");
  result=text("Import a file and analyse it to preview the proposed cuts.",13,false,GREY);add(previewCard,result,10);
  LinearLayout output=card(root,"4  Export");
  renderButton=action("Render edited file",true);add(output,renderButton,12);renderButton.setEnabled(false);
  renderButton.setOnClickListener(v->render(false));
  testButton=action("15-second test export",false);add(output,testButton,9);testButton.setEnabled(false);
  testButton.setOnClickListener(v->render(true));
  cancelButton=action("Cancel active render",false);add(output,cancelButton,9);cancelButton.setEnabled(false);
  cancelButton.setOnClickListener(v->cancel());
  Button share=action("Share last export",false);add(output,share,9);share.setOnClickListener(v->share());
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);add(output,progress,10);
  status=text("Ready to import your recording.",12,false,GREY);add(output,status,9);
 }
 private void choose(){
  if(busy)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/*","audio/*"});
  i.addCategory(Intent.CATEGORY_OPENABLE);
  i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  startActivityForResult(i,REQUEST_MEDIA);
 }
 @Override protected void onActivityResult(int request,int resultCode,Intent intent){
  super.onActivityResult(request,resultCode,intent);
  if(request!=REQUEST_MEDIA||resultCode!=RESULT_OK||intent==null||intent.getData()==null)return;
  source=intent.getData();
  try{getContentResolver().takePersistableUriPermission(source,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
  MediaMetadataRetriever r=new MediaMetadataRetriever();
  try{
   r.setDataSource(this,source);
   String d=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
   durationMs=d==null?0:Long.parseLong(d);
   if(durationMs<500)throw new IllegalStateException("Recording duration unavailable");
   hasVideo="yes".equalsIgnoreCase(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
   mediaInfo.setText((hasVideo?"Video":"Audio")+" • "+String.format(Locale.US,"%.1f seconds",durationMs/1000.0));
   preview.setVisibility(hasVideo?View.VISIBLE:View.GONE);preview.setVideoURI(source);
   ranges.clear();result.setText("Ready to analyse sound levels.");
   status.setText("Source imported; original file will not be modified.");
   renderButton.setEnabled(false);testButton.setEnabled(false);
  }catch(Exception e){
   source=null;status.setText("Cannot read media: "+e.getMessage());
  }finally{try{r.release();}catch(Exception ignored){}}
 }
 private void busy(boolean value){
  busy=value;analyzeButton.setEnabled(!value);
  renderButton.setEnabled(!value&&!ranges.isEmpty());
  testButton.setEnabled(!value&&!ranges.isEmpty());
  cancelButton.setEnabled(value&&transformer!=null);
 }
 private void analyze(){
  if(busy)return;if(source==null){status.setText("Choose a file first.");return;}
  busy(true);progress.setProgress(0);result.setText("Analyzing audio energy...");
  int choice=sensitivity.getSelectedItemPosition();
  boolean padded=trimLeadingTrailing.isChecked();
  workers.execute(()->{
   try{
    ArrayList<SampleWindow> windows=decodeWindows(source);
    double limit=choice==0?.009:choice==1?.019:.032;
    long padding=padded?180L:20L;
    ArrayList<Range> found=makeRanges(windows,limit,padding,durationMs);
    runOnUiThread(()->{
     ranges.clear();ranges.addAll(found);busy(false);progress.setProgress(100);
     long total=0;for(Range r:found)total+=r.length();
     long removed=Math.max(0,durationMs-total);
     result.setText(found.size()+" retained sections\n"+String.format(Locale.US,"Remaining %.1fs • Removed %.1fs (%.0f%%)",
      total/1000.0,removed/1000.0,100.0*removed/Math.max(1,durationMs))+
      "\n\nPreview the original with Play; use a test export to listen for clipped words.");
     status.setText(found.isEmpty()?"No sound sections passed this threshold. Try Gentle sensitivity.":"Audio scan complete. Review before exporting.");
    });
   }catch(Exception e){runOnUiThread(()->{
      busy(false);progress.setProgress(0);
      result.setText("Analysis failed: "+e.getClass().getSimpleName()+" — "+e.getMessage());
      status.setText("Audio decoding may be unsupported for this file.");
   });}
  });
 }
 private ArrayList<Range> makeRanges(ArrayList<SampleWindow> windows,double threshold,long padding,long duration){
  ArrayList<Range> found=new ArrayList<>();
  for(SampleWindow w:windows){
   if(w.rms<threshold)continue;
   long a=Math.max(0,w.time-padding),b=Math.min(duration,w.time+120+padding);
   if(found.size()>0&&found.get(found.size()-1).b+250>=a)
    found.get(found.size()-1).b=Math.max(found.get(found.size()-1).b,b);
   else found.add(new Range(a,b));
  }
  found.removeIf(x->x.length()<170);
  return found;
 }
 private ArrayList<SampleWindow> decodeWindows(Uri media)throws Exception{
  ArrayList<SampleWindow> result=new ArrayList<>();
  MediaExtractor extractor=new MediaExtractor();MediaCodec decoder=null;
  try{
   extractor.setDataSource(this,media,null);
   int track=-1;MediaFormat fmt=null;
   for(int i=0;i<extractor.getTrackCount();i++){
    MediaFormat f=extractor.getTrackFormat(i);
    String mime=f.getString(MediaFormat.KEY_MIME);
    if(mime!=null&&mime.startsWith("audio/")){track=i;fmt=f;break;}
   }
   if(track<0||fmt==null)throw new IOException("No audio track found.");
   extractor.selectTrack(track);
   String mime=fmt.getString(MediaFormat.KEY_MIME);
   if(mime==null)throw new IOException("Unsupported audio track MIME");
   decoder=MediaCodec.createDecoderByType(mime);
   decoder.configure(fmt,null,null,0);decoder.start();
   int rate=fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)?fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE):44100;
   int channels=fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1;
   boolean inputDone=false,outputDone=false;
   MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
   double sumSq=0;int count=0;long windowStart=0;
   while(!outputDone){
    if(!inputDone){
     int idx=decoder.dequeueInputBuffer(15000);
     if(idx>=0){
      ByteBuffer input=decoder.getInputBuffer(idx);if(input==null)throw new IOException("No decoder input");
      input.clear();int n=extractor.readSampleData(input,0);
      if(n<0){decoder.queueInputBuffer(idx,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
      else{decoder.queueInputBuffer(idx,0,n,Math.max(0,extractor.getSampleTime()),0);extractor.advance();}
     }
    }
    int idx=decoder.dequeueOutputBuffer(info,15000);
    if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
     MediaFormat out=decoder.getOutputFormat();
     if(out.containsKey(MediaFormat.KEY_PCM_ENCODING)&&out.getInteger(MediaFormat.KEY_PCM_ENCODING)!=2)
      throw new IOException("Decoder output is not PCM 16-bit; conversion required.");
     if(out.containsKey(MediaFormat.KEY_SAMPLE_RATE))rate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE);
     if(out.containsKey(MediaFormat.KEY_CHANNEL_COUNT))channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    }else if(idx>=0){
     if(info.size>0){
      ByteBuffer output=decoder.getOutputBuffer(idx);
      if(output!=null){
       output.order(ByteOrder.LITTLE_ENDIAN);output.position(info.offset);output.limit(info.offset+info.size);
       int samples=output.remaining()/(2*Math.max(1,channels));
       for(int f=0;f<samples;f++){
        double amplitude=0;
        for(int ch=0;ch<channels;ch++)amplitude+=output.getShort()/32768.0;
        amplitude/=channels;sumSq+=amplitude*amplitude;count++;
        if(count>=Math.max(1,rate/10)){
         result.add(new SampleWindow(Math.max(0,windowStart),Math.sqrt(sumSq/count)));
         windowStart=Math.round(info.presentationTimeUs/1000.0+(f*1000.0)/rate);
         sumSq=0;count=0;
        }
       }
      }
     }
     decoder.releaseOutputBuffer(idx,false);
     if(info.flags%8>=4)outputDone=true; // END_OF_STREAM flag bit
     if(durationMs>0){
      int p=(int)Math.max(0,Math.min(95,100d*info.presentationTimeUs/(durationMs*1000)));
      runOnUiThread(()->progress.setProgress(p));
     }
    }
   }
   if(count>0)result.add(new SampleWindow(windowStart,Math.sqrt(sumSq/count)));
   return result;
  }finally{
   if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}try{decoder.release();}catch(Exception ignored){}}
   extractor.release();
  }
 }
 private void render(boolean test){
  if(busy)return;if(source==null||ranges.isEmpty()){status.setText("Analyze a recording before export.");return;}
  if(new StatFs(getCacheDir().getAbsolutePath()).getAvailableBytes()<250L*1024*1024){
   status.setText("Free at least 250 MB storage before rendering.");return;
  }
  busy(true);cancelButton.setEnabled(true);progress.setProgress(3);
  outputFile=new File(getCacheDir(),"cinezehn_silence_"+System.currentTimeMillis()+(hasVideo?".mp4":".m4a"));
  try{
   ArrayList<EditedMediaItem> clips=new ArrayList<>();long remaining=test?15000:Long.MAX_VALUE;
   for(Range r:ranges){
    if(remaining<=0||clips.size()>120)break;
    long end=Math.min(r.b,r.a+remaining);
    if(end<=r.a+90)continue;
    MediaItem.ClippingConfiguration clip=new MediaItem.ClippingConfiguration.Builder()
      .setStartPositionMs(r.a).setEndPositionMs(end).build();
    clips.add(new EditedMediaItem.Builder(new MediaItem.Builder().setUri(source)
      .setClippingConfiguration(clip).build()).build());
    remaining-=end-r.a;
   }
   if(clips.isEmpty())throw new IOException("No exportable segments found.");
   EditedMediaItemSequence seq=hasVideo?EditedMediaItemSequence.withAudioAndVideoFrom(clips):
      EditedMediaItemSequence.withAudioFrom(clips);
   Composition composition=new Composition.Builder(Collections.singletonList(seq)).build();
   transformer=new Transformer.Builder(this)
    .setAudioMimeType(MimeTypes.AUDIO_AAC)
    .addListener(new Transformer.Listener(){
     @Override public void onCompleted(Composition composition,ExportResult result){
      if(!busy)return;stopPolling();status.setText("Encoding done. Saving...");
      File f=outputFile;
      workers.execute(()->{
       try{Uri saved=save(f);runOnUiThread(()->{
        lastSaved=saved;stopBusy();progress.setProgress(100);
        status.setText("Saved to "+(hasVideo?"Movies":"Music")+"/CineZehn — review before uploading.");
       });}catch(Exception e){runOnUiThread(()->{stopBusy();status.setText("Save error: "+e.getMessage());});}
       finally{f.delete();}
      });
     }
     @Override public void onError(Composition composition,ExportResult result,ExportException e){
      if(!busy)return;
      stopBusy();if(outputFile!=null)outputFile.delete();
      status.setText("Export failed: "+e.errorCode+" • "+e.getMessage());
     }
    }).build();
   transformer.start(composition,outputFile.getAbsolutePath());
   status.setText(test?"Rendering first 15 seconds after cuts...":"Exporting edited media...");
   startPolling();
  }catch(Exception e){
   stopBusy();if(outputFile!=null)outputFile.delete();
   status.setText("Cannot export: "+e.getMessage());
  }
 }
 private Uri save(File file)throws Exception{
  boolean video=hasVideo;
  ContentValues values=new ContentValues();
  values.put(MediaStore.MediaColumns.DISPLAY_NAME,"CineZehn_Cut_"+System.currentTimeMillis()+(video?".mp4":".m4a"));
  values.put(MediaStore.MediaColumns.MIME_TYPE,video?"video/mp4":"audio/mp4");
  values.put(MediaStore.MediaColumns.RELATIVE_PATH,(video?Environment.DIRECTORY_MOVIES:Environment.DIRECTORY_MUSIC)+"/CineZehn");
  values.put(MediaStore.MediaColumns.IS_PENDING,1);
  Uri base=video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
  Uri target=getContentResolver().insert(base,values);
  if(target==null)throw new IOException("Could not create media file");
  try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(target)){
   if(out==null)throw new IOException("Cannot write destination");
   byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
  }catch(Exception e){getContentResolver().delete(target,null,null);throw e;}
  values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);
  getContentResolver().update(target,values,null,null);return target;
 }
 private void startPolling(){
  stopPolling();progressTask=new Runnable(){
   @Override public void run(){
    if(!busy||transformer==null)return;
    try{
     ProgressHolder holder=new ProgressHolder();
     if(transformer.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE){
      progress.setProgress(Math.min(90,10+holder.progress*80/100));
     }
    }catch(Exception ignored){}
    handler.postDelayed(this,1000);
   }
  };
  handler.postDelayed(progressTask,950);
 }
 private void stopPolling(){if(progressTask!=null){handler.removeCallbacks(progressTask);progressTask=null;}}
 private void stopBusy(){
  busy=false;stopPolling();transformer=null;cancelButton.setEnabled(false);analyzeButton.setEnabled(true);
  renderButton.setEnabled(!ranges.isEmpty());testButton.setEnabled(!ranges.isEmpty());
 }
 private void cancel(){
  if(transformer==null)return;
  Transformer old=transformer;File file=outputFile;
  stopBusy();try{old.cancel();}catch(Exception ignored){}
  if(file!=null)file.delete();progress.setProgress(0);status.setText("Render cancelled; original media unchanged.");
 }
 private void share(){
  if(lastSaved==null){status.setText("Export a video/audio file first.");return;}
  Intent send=new Intent(Intent.ACTION_SEND);send.setType(hasVideo?"video/mp4":"audio/mp4");
  send.putExtra(Intent.EXTRA_STREAM,lastSaved);send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  startActivity(Intent.createChooser(send,"Share edited media"));
 }
 @Override protected void onDestroy(){
  stopPolling();if(transformer!=null&&busy)try{transformer.cancel();}catch(Exception ignored){}
  if(preview!=null)preview.stopPlayback();
  workers.shutdownNow();super.onDestroy();
 }
}
