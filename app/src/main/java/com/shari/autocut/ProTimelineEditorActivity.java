package com.shari.autocut;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.effect.*;
import androidx.media3.transformer.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Separate manual Pro Editor opened after automatic storyboard planning.
 * Real scene timeline, selected-layer editing, split/reorder, scene duration,
 * scrubable composite preview, undo checkpoint and Media3 MP4 export.
 */
@androidx.media3.common.util.UnstableApi
public final class ProTimelineEditorActivity extends Activity {
 private static final int BG=0xFF0A0D17,PANEL=0xFF191D2D,ACCENT=0xFF825AF3,
   WHITE=0xFFFFFFFF,SECONDARY=0xFFAAB4D0;
 private StudioProject project;
 private final ExecutorService jobs=Executors.newSingleThreadExecutor();
 private final Handler tick=new Handler(Looper.getMainLooper());
 private ProVisualOverlay renderer;
 private Preview view;
 private SeekBar scrub;
 private TextView timeText,status;
 private LinearLayout timeline,controls,root;
 private ScrollView scroll;
 private int selected=0;private long playheadMs=0;
 private MediaPlayer audioPlayer;
 private boolean playing=false,rendering=false;
 private Transformer transformer;
 private File exportFile;
 private Uri exported;
 private ProgressBar progress;
 private Button exportBtn;
 private StudioProject undoSnapshot;
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private GradientDrawable shape(int color,int radius){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
 }
 private TextView label(String text,int size,boolean bold,int color){
  TextView t=new TextView(this);t.setText(text);t.setTextColor(color);t.setTextSize(size);
  if(bold)t.setTypeface(null,Typeface.BOLD);return t;
 }
 private Button btn(String text,boolean primary){
  Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextSize(12);
  b.setTextColor(primary?WHITE:SECONDARY);b.setBackground(shape(primary?ACCENT:0xFF292E44,12));
  b.setMinHeight(dp(43));b.setElevation(0);return b;
 }
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private void add(LinearLayout col,View child,int margin){
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(margin);col.addView(child,p);
 }
 private void warn(String text){new AlertDialog.Builder(this).setMessage(text).setPositiveButton("OK",null).show();}
 private void store(){
  try{project.save(this);}catch(Exception e){status.setText("Save failed: "+e.getMessage());}
 }
 private void checkpoint(){
  try{undoSnapshot=StudioProject.fromJson(project.toJson());}
  catch(Exception ignored){}
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  try{project=StudioProject.load(this);}
  catch(Exception e){project=StudioProject.plan("",8,60000);}
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  renderer=new ProVisualOverlay(this,project);
  build();
 }
 private long total(){
  long sum=0;for(StudioProject.Shot s:project.shots)sum+=s.duration();
  return Math.max(1000,sum);
 }
 private void retime(){
  long start=0;
  for(StudioProject.Shot s:project.shots){
   long dur=s.duration();s.startMs=start;s.endMs=start+dur;start+=dur;
  }
  project.durationMs=total();store();
 }
 private String format(long ms){
  return String.format(Locale.US,"%02d:%02d",ms/60000,(ms/1000)%60);
 }
 private final class Preview extends View{
  private final Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  private Bitmap buffer;
  Preview(Context c){super(c);setBackgroundColor(Color.BLACK);}
  @Override protected void onDraw(Canvas c){
   super.onDraw(c);
   float availableW=getWidth(),availableH=getHeight();
   if(availableW<=0||availableH<=0)return;
   float scale=Math.min(availableW/project.width,availableH/project.height);
   float drawW=project.width*scale,drawH=project.height*scale;
   c.save();c.translate((availableW-drawW)/2,(availableH-drawH)/2);
   c.scale(scale,scale);
   if(buffer==null||buffer.getWidth()!=project.width||buffer.getHeight()!=project.height){
    if(buffer!=null)buffer.recycle();
    buffer=Bitmap.createBitmap(project.width,project.height,Bitmap.Config.ARGB_8888);
   }
   Canvas inner=new Canvas(buffer);inner.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
   renderer.drawAt(inner,playheadMs);
   c.drawBitmap(buffer,null,new RectF(0,0,project.width,project.height),brush);
   c.restore();
  }
 }
 private void build(){
  scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
  root=column();root.setPadding(dp(12),dp(14),dp(12),dp(26));scroll.addView(root);setContentView(scroll);
  LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);add(root,top,0);
  Button back=btn("‹",false);top.addView(back,new LinearLayout.LayoutParams(dp(45),dp(42)));
  back.setOnClickListener(v->finish());
  TextView title=label("PRO TIMELINE EDITOR",16,true,WHITE);title.setPadding(dp(7),0,0,0);
  top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  Button undo=btn("Undo",false);top.addView(undo);undo.setOnClickListener(v->{
   if(undoSnapshot!=null){project=undoSnapshot;undoSnapshot=null;retime();recreatePreview();build();}
  });
  exportBtn=btn("EXPORT",true);top.addView(exportBtn);exportBtn.setOnClickListener(v->render(false));
  add(root,label("Manual finishing • captions • assets • keyframe-style motion • MP4",11,false,SECONDARY),9);
  view=new Preview(this);
  int h=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?dp(200):dp(310);
  LinearLayout.LayoutParams video=new LinearLayout.LayoutParams(-1,h);video.topMargin=dp(11);
  root.addView(view,video);
  LinearLayout transport=new LinearLayout(this);transport.setGravity(Gravity.CENTER_VERTICAL);
  Button play=btn("▶ Play",true);transport.addView(play,new LinearLayout.LayoutParams(dp(96),dp(48)));
  play.setOnClickListener(v->toggle());
  timeText=label("00:00 / "+format(total()),12,false,WHITE);
  timeText.setGravity(Gravity.CENTER);transport.addView(timeText,new LinearLayout.LayoutParams(0,dp(43),1));
  Button retry=btn("⟲",false);transport.addView(retry,new LinearLayout.LayoutParams(dp(48),dp(43)));
  retry.setOnClickListener(v->{pause();seek(0);});
  add(root,transport,9);
  scrub=new SeekBar(this);scrub.setMax((int)Math.min(Integer.MAX_VALUE,total()));
  scrub.setProgress((int)Math.min(total(),playheadMs));add(root,scrub,4);
  scrub.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   @Override public void onProgressChanged(SeekBar b,int v,boolean fromUser){if(fromUser)seek(v);}
   @Override public void onStartTrackingTouch(SeekBar b){pause();}
   @Override public void onStopTrackingTouch(SeekBar b){}
  });
  LinearLayout timelineCard=column();timelineCard.setPadding(dp(11),dp(12),dp(11),dp(11));
  timelineCard.setBackground(shape(PANEL,17));add(root,timelineCard,9);
  add(timelineCard,label("MULTI-LAYER TIMELINE  •  select a shot to edit",12,true,WHITE),0);
  HorizontalScrollView hscroll=new HorizontalScrollView(this);hscroll.setHorizontalScrollBarEnabled(false);
  timeline=new LinearLayout(this);timeline.setOrientation(1);hscroll.addView(timeline);
  add(timelineCard,hscroll,9);
  drawTimeline();
  controls=column();controls.setBackground(shape(PANEL,18));controls.setPadding(dp(12),dp(12),dp(12),dp(16));
  add(root,controls,12);
  buildTools();
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);
  add(root,progress,12);
  status=label("Project saved locally • Select asset/image layers to make manual changes",11,false,SECONDARY);add(root,status,9);
  Button planner=btn("← Open Prompt Planner / Upload More Assets",false);
  add(root,planner,11);planner.setOnClickListener(v->{
   pause();store();startActivity(new Intent(this,StoryboardPlannerActivity.class));finish();
  });
  Button silence=btn("Open audio cleanup / silence cutter",false);add(root,silence,8);
  silence.setOnClickListener(v->startActivity(new Intent(this,SilenceCutActivity.class)));
  Button share=btn("Share last exported video",false);add(root,share,8);
  share.setOnClickListener(v->share());
  timeText.setText(format(playheadMs)+" / "+format(total()));
 }
 private void recreatePreview(){renderer=new ProVisualOverlay(this,project);if(view!=null)view.invalidate();}
 private void drawTimeline(){
  timeline.removeAllViews();
  String[] names={"VIDEO","PNG 1–5","HOST","CAPTIONS","VOICE / SFX"};
  int[] colors={0xFF615CD0,0xFF96773E,0xFF368B8E,0xFFAD6191,0xFF2E986C};
  for(int row=0;row<names.length;row++){
   LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
   TextView label=label(names[row],10,true,SECONDARY);
   line.addView(label,new LinearLayout.LayoutParams(dp(91),dp(39)));
   for(int i=0;i<project.shots.size();i++){
    final int chosen=i;StudioProject.Shot shot=project.shots.get(i);
    int width=Math.max(dp(65),(int)(shot.duration()/1000f*dp(17)));
    TextView chip=label(row==0?"ASSET "+(i+1):row==1?"＋ PNG":row==2?"HOST":row==3?"TEXT":"AUDIO",
     10,true,WHITE);
    chip.setGravity(Gravity.CENTER);chip.setBackground(shape(selected==i?ACCENT:colors[row],7));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,dp(32));lp.rightMargin=dp(3);
    line.addView(chip,lp);
    chip.setOnClickListener(v->{selected=chosen;seek(shot.startMs);buildTools();drawTimeline();});
   }
   add(timeline,line,row==0?0:3);
  }
 }
 private void tool(LinearLayout parent,String title,Runnable action){
  Button b=btn(title,false);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(43),1);lp.rightMargin=dp(5);
  parent.addView(b,lp);b.setOnClickListener(v->action.run());
 }
 private void buildTools(){
  if(controls==null)return;controls.removeAllViews();
  StudioProject.Shot shot=project.shots.get(Math.max(0,Math.min(selected,project.shots.size()-1)));
  add(controls,label("SELECTED: ASSET "+(selected+1)+" • "+format(shot.startMs)+"–"+format(shot.endMs),14,true,WHITE),0);
  LinearLayout editRow=new LinearLayout(this);
  tool(editRow,"Split",()->split());tool(editRow,"Duplicate",()->duplicate());
  tool(editRow,"←",()->move(-1));tool(editRow,"→",()->move(1));add(controls,editRow,10);
  LinearLayout row2=new LinearLayout(this);
  tool(row2,"Duration",()->durationDialog());tool(row2,"Caption",()->captionDialog());
  tool(row2,"Motion",()->motionDialog());tool(row2,"Layers",()->layerDialog());add(controls,row2,7);
  LinearLayout row3=new LinearLayout(this);
  tool(row3,"Delete",()->delete());tool(row3,"Aspect",()->aspectDialog());
  tool(row3,"Settings",()->exportSettings());tool(row3,"Edit prompts",()->{
   store();startActivity(new Intent(this,StoryboardPlannerActivity.class));finish();
  });add(controls,row3,7);
  add(controls,label("Tap tracks to select shot. Changes affect the actual composition and saved export.",11,false,SECONDARY),10);
 }
 private void split(){
  if(project.shots.size()>=100){warn("100 shot limit reached.");return;}
  StudioProject.Shot old=project.shots.get(selected);
  long at=Math.max(old.startMs+350,Math.min(old.endMs-350,playheadMs));
  if(at<=old.startMs+300||at>=old.endMs-300){warn("Move playhead inside the selected shot to split.");return;}
  checkpoint();
  StudioProject.Shot second=copyShot(old);
  long first=at-old.startMs;
  long dur=old.duration();
  old.endMs=old.startMs+first;
  second.startMs=old.endMs;second.endMs=second.startMs+(dur-first);
  project.shots.add(selected+1,second);selected++;
  retime();recreatePreview();build();
 }
 private StudioProject.Shot copyShot(StudioProject.Shot old){
  StudioProject.Shot copy=new StudioProject.Shot(old.startMs,old.endMs);
  copy.prompt=old.prompt;copy.caption=old.caption;copy.narration=old.narration;
  copy.graphics=old.graphics;copy.sfx=old.sfx;copy.transition=old.transition;
  copy.motion=old.motion;copy.captionStyle=old.captionStyle;copy.visual=old.visual;
  copy.host=old.host;copy.hostEnabled=old.hostEnabled;copy.hostMotion=old.hostMotion;
  copy.hostX=old.hostX;copy.hostY=old.hostY;copy.hostScale=old.hostScale;
  copy.overlaySlots=old.overlaySlots;
  for(int j=0;j<5;j++){
   copy.overlays[j]=old.overlays[j];copy.overlayPrompts[j]=old.overlayPrompts[j];
   copy.overlayMotion[j]=old.overlayMotion[j];copy.overlayX[j]=old.overlayX[j];
   copy.overlayY[j]=old.overlayY[j];copy.overlayScale[j]=old.overlayScale[j];
  }
  return copy;
 }
 private void duplicate(){
  if(project.shots.size()>=100){warn("100 shot limit reached.");return;}
  checkpoint();project.shots.add(selected+1,copyShot(project.shots.get(selected)));
  selected++;retime();recreatePreview();build();
 }
 private void move(int d){
  int next=selected+d;if(next<0||next>=project.shots.size())return;
  checkpoint();Collections.swap(project.shots,selected,next);selected=next;
  retime();recreatePreview();build();
 }
 private void delete(){
  if(project.shots.size()<=1){warn("A video requires at least one shot.");return;}
  new AlertDialog.Builder(this).setTitle("Remove selected shot?")
   .setMessage("The source visual stays on your phone. This only removes its timeline placement.")
   .setPositiveButton("Remove",(dialog,w)->{
    checkpoint();project.shots.remove(selected);selected=Math.min(selected,project.shots.size()-1);
    retime();recreatePreview();build();
   }).setNegativeButton("Cancel",null).show();
 }
 private void durationDialog(){
  StudioProject.Shot s=project.shots.get(selected);
  EditText value=new EditText(this);value.setInputType(8194);
  value.setText(String.format(Locale.US,"%.2f",s.duration()/1000d));
  new AlertDialog.Builder(this).setTitle("Selected shot duration in seconds")
   .setView(value).setPositiveButton("Apply",(d,w)->{
    try{
     long newDuration=Math.round(Double.parseDouble(value.getText().toString())*1000);
     if(newDuration<350||newDuration>120000)throw new IllegalArgumentException("Shot must be 0.35–120 seconds");
     checkpoint();s.endMs=s.startMs+newDuration;retime();recreatePreview();build();
    }catch(Exception e){warn("Invalid duration: "+e.getMessage());}
   }).setNegativeButton("Cancel",null).show();
 }
 private void captionDialog(){
  StudioProject.Shot s=project.shots.get(selected);
  EditText value=new EditText(this);value.setMinLines(3);value.setText(s.caption);
  new AlertDialog.Builder(this).setTitle("Edit caption for asset "+(selected+1)).setView(value)
   .setPositiveButton("Apply",(d,w)->{checkpoint();s.caption=value.getText().toString();store();view.invalidate();})
   .setNegativeButton("Cancel",null).show();
 }
 private void motionDialog(){
  StudioProject.Shot s=project.shots.get(selected);
  int idx=Arrays.asList(StudioProject.MOTIONS).indexOf(s.motion);
  new AlertDialog.Builder(this).setTitle("Animated main visual motion").setSingleChoiceItems(
   StudioProject.MOTIONS,idx,(d,n)->{
    checkpoint();s.motion=StudioProject.MOTIONS[n];d.dismiss();store();view.invalidate();
   }).setNegativeButton("Cancel",null).show();
 }
 private void layerDialog(){
  StudioProject.Shot s=project.shots.get(selected);
  String[] names=new String[7];names[0]="Main visual — "+s.motion;
  for(int i=0;i<5;i++)names[i+1]="Overlay "+(i+1)+(s.overlays[i]==null?" (no asset)":" ✓")+": "+s.overlayMotion[i];
  names[6]="Host PNG"+(s.hostEnabled?" (enabled)":" (off)")+": "+s.hostMotion;
  new AlertDialog.Builder(this).setTitle("Layer properties").setItems(names,(dialog,item)->{
   if(item==0){motionDialog();return;}
   layerSettings(s,item==6?-1:item-1);
  }).setNegativeButton("Close",null).show();
 }
 private void layerSettings(StudioProject.Shot s,int layer){
  boolean host=layer==-1;
  float x=host?s.hostX:s.overlayX[layer],y=host?s.hostY:s.overlayY[layer],
        scale=host?s.hostScale:s.overlayScale[layer];
  String motion=host?s.hostMotion:s.overlayMotion[layer];
  LinearLayout form=column();form.setPadding(dp(17),dp(9),dp(17),dp(7));
  TextView xTitle=label("Horizontal position: "+Math.round(x*100)+"%",12,false,Color.DKGRAY);
  TextView yTitle=label("Vertical position: "+Math.round(y*100)+"%",12,false,Color.DKGRAY);
  TextView sizeTitle=label("Size: "+Math.round(scale*100)+"%",12,false,Color.DKGRAY);
  SeekBar posX=new SeekBar(this);posX.setMax(100);posX.setProgress(Math.round(x*100));
  SeekBar posY=new SeekBar(this);posY.setMax(100);posY.setProgress(Math.round(y*100));
  SeekBar size=new SeekBar(this);size.setMax(100);size.setProgress(Math.round(scale*100));
  add(form,xTitle,0);add(form,posX,3);add(form,yTitle,8);add(form,posY,3);add(form,sizeTitle,8);add(form,size,3);
  Spinner effects=new Spinner(this);
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,StudioProject.MOTIONS);
  effects.setAdapter(adapter);
  effects.setSelection(Math.max(0,Arrays.asList(StudioProject.MOTIONS).indexOf(motion)));
  add(form,label("Motion effect",12,true,Color.DKGRAY),12);add(form,effects,6);
  if(host)add(form,label("Host can be a user PNG or the basic procedural 2D host. Accurate lip-sync is not implemented.",12,false,Color.DKGRAY),12);
  new AlertDialog.Builder(this).setTitle(host?"Avatar host layer":"Overlay "+(layer+1)+" transform").setView(form)
   .setPositiveButton("Apply",(d,w)->{
    checkpoint();
    if(host){s.hostX=posX.getProgress()/100f;s.hostY=posY.getProgress()/100f;
     s.hostScale=Math.max(.05f,size.getProgress()/100f);
     s.hostMotion=String.valueOf(effects.getSelectedItem());s.hostEnabled=true;
    }else{s.overlayX[layer]=posX.getProgress()/100f;s.overlayY[layer]=posY.getProgress()/100f;
     s.overlayScale[layer]=Math.max(.05f,size.getProgress()/100f);
     s.overlayMotion[layer]=String.valueOf(effects.getSelectedItem());}
    store();view.invalidate();
   }).setNegativeButton("Cancel",null).show();
 }
 private void aspectDialog(){
  String[] choices={"9:16 Shorts • 720×1280","16:9 Landscape • 1280×720","1:1 Square • 720×720","4:5 Portrait • 720×900"};
  new AlertDialog.Builder(this).setTitle("Output aspect ratio").setItems(choices,(d,n)->{
   checkpoint();
   project.width=n==1?1280:720;
   project.height=n==0?1280:n==1?720:n==2?720:900;
   store();view.requestLayout();build();
  }).setNegativeButton("Cancel",null).show();
 }
 private void exportSettings(){
  String[] captions={"Bold Yellow Highlight","White Minimal","Typing","Floating","Shake","Pop","Slide Up"};
  new AlertDialog.Builder(this).setTitle("Caption settings").setSingleChoiceItems(captions,
   Math.max(0,Arrays.asList(captions).indexOf(project.captionPreset)),
   (dialog,n)->{
    checkpoint();project.captionPreset=captions[n];
    for(StudioProject.Shot s:project.shots)s.captionStyle=captions[n];
    dialog.dismiss();store();view.invalidate();
   }).setNeutralButton(project.addCaptions?"Hide all captions":"Show all captions",(d,w)->{
    project.addCaptions=!project.addCaptions;store();view.invalidate();
   }).setNegativeButton("Cancel",null).show();
 }
 private void seek(long time){
  playheadMs=Math.max(0,Math.min(total()-1,time));
  if(view!=null)view.invalidate();
  if(timeText!=null)timeText.setText(format(playheadMs)+" / "+format(total()));
  if(scrub!=null)scrub.setProgress((int)playheadMs);
  if(audioPlayer!=null)try{audioPlayer.seekTo((int)playheadMs);}catch(Exception ignored){}
 }
 private void toggle(){if(playing){pause();return;}if(project.audioUri==null){warn("Import narration in Auto Planner before playback.");return;}
  try{
   if(audioPlayer==null){
    audioPlayer=new MediaPlayer();audioPlayer.setDataSource(this,project.audioUri);
    audioPlayer.prepare();
    audioPlayer.setOnCompletionListener(mp->pause());
   }
   audioPlayer.seekTo((int)playheadMs);audioPlayer.start();playing=true;
   tick.post(frameTicker);
  }catch(Exception e){warn("Cannot play narration: "+e.getMessage());}
 }
 private final Runnable frameTicker=new Runnable(){
  @Override public void run(){
   if(!playing||audioPlayer==null)return;
   try{
    playheadMs=Math.min(total()-1,audioPlayer.getCurrentPosition());
    if(view!=null)view.invalidate();
    if(scrub!=null)scrub.setProgress((int)playheadMs);
    if(timeText!=null)timeText.setText(format(playheadMs)+" / "+format(total()));
    if(playheadMs>=total()-1){pause();return;}
   }catch(Exception e){pause();return;}
   tick.postDelayed(this,90);
  }
 };
 private void pause(){
  playing=false;tick.removeCallbacks(frameTicker);
  if(audioPlayer!=null)try{audioPlayer.pause();}catch(Exception ignored){}
 }
 private File blankFrame()throws IOException{
  Bitmap bmp=Bitmap.createBitmap(project.width,project.height,Bitmap.Config.ARGB_8888);
  Canvas c=new Canvas(bmp);c.drawColor(BG);
  File out=new File(getCacheDir(),"cinezehn_pro_source_"+System.currentTimeMillis()+".png");
  try(OutputStream stream=new FileOutputStream(out)){bmp.compress(Bitmap.CompressFormat.PNG,100,stream);}
  bmp.recycle();return out;
 }
 private void render(boolean test){
  if(rendering)return;
  if(project.shots.isEmpty()){warn("Plan at least one scene first.");return;}
  if(project.audioUri==null){
   new AlertDialog.Builder(this).setTitle("Voiceover missing")
    .setMessage("This renderer needs an imported narration/audio/video file for sound. Import it in the Auto Planner.")
    .setPositiveButton("Open Planner",(d,w)->startActivity(new Intent(this,StoryboardPlannerActivity.class)))
    .setNegativeButton("Cancel",null).show();return;
  }
  int complete=project.completeVisuals();
  if(complete<project.shots.size()){
   new AlertDialog.Builder(this).setTitle("Some images are missing")
    .setMessage((project.shots.size()-complete)+" scenes have no uploaded main visual. They will show an explicit 'ASSET NOT UPLOADED' placeholder. For professional export, upload each visual.")
    .setPositiveButton("Render with placeholders",(d,w)->startRender(test))
    .setNegativeButton("Cancel",null).show();
  }else startRender(test);
 }
 private void startRender(boolean test){
  pause();store();rendering=true;exportBtn.setEnabled(false);
  progress.setProgress(2);
  long duration=test?Math.min(15000,total()):total();
  exportFile=new File(getCacheDir(),"cinezehn_pro_edit_"+System.currentTimeMillis()+".mp4");
  try{
   File sourceImage=blankFrame();
   MediaItem image=new MediaItem.Builder().setUri(Uri.fromFile(sourceImage)).setImageDurationMs(duration).build();
   ProVisualOverlay overlay=new ProVisualOverlay(this,project);
   Effects fx=new Effects(Collections.emptyList(),Collections.singletonList(
    new OverlayEffect(Collections.singletonList(overlay))));
   EditedMediaItem video=new EditedMediaItem.Builder(image).setFrameRate(24).setEffects(fx).build();
   MediaItem soundItem=new MediaItem.Builder().setUri(project.audioUri)
    .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(duration).build()).build();
   EditedMediaItem sound=new EditedMediaItem.Builder(soundItem).build();
   Composition composition=new Composition.Builder(Arrays.asList(
    EditedMediaItemSequence.withVideoFrom(Collections.singletonList(video)),
    EditedMediaItemSequence.withAudioFrom(Collections.singletonList(sound))
   )).build();
   transformer=new Transformer.Builder(this).setVideoMimeType(MimeTypes.VIDEO_H264)
    .setAudioMimeType(MimeTypes.AUDIO_AAC).setMaxDelayBetweenMuxerSamplesMs(120000L)
    .addListener(new Transformer.Listener(){
     @Override public void onCompleted(Composition c,ExportResult result){
      File file=exportFile;
      status.setText("Encoding complete. Saving MP4...");
      jobs.execute(()->{
       try{
        Uri saved=saveVideo(file);
        runOnUiThread(()->{
         exported=saved;rendering=false;exportBtn.setEnabled(true);
         progress.setProgress(100);status.setText("MP4 saved to Gallery / CineZehn. Verify video and narration before sharing.");
        });
       }catch(Exception e){runOnUiThread(()->failed("Save failed: "+e.getMessage()));}
       finally{file.delete();sourceImage.delete();}
      });
     }
     @Override public void onError(Composition c,ExportResult result,ExportException e){
      sourceImage.delete();if(exportFile!=null)exportFile.delete();
      failed("Render error code "+e.errorCode+": "+e.getMessage());
     }
    }).build();
   transformer.start(composition,exportFile.getAbsolutePath());
   status.setText("Rendering "+format(duration)+" at "+project.width+"×"+project.height+"...");
   poll();
  }catch(Exception e){failed("Cannot start export: "+e.getMessage());}
 }
 private void poll(){
  if(!rendering||transformer==null)return;
  try{
   ProgressHolder p=new ProgressHolder();
   if(transformer.getProgress(p)==Transformer.PROGRESS_STATE_AVAILABLE){
    progress.setProgress(Math.min(95,5+p.progress*90/100));
   }
  }catch(Exception ignored){}
  tick.postDelayed(()->poll(),1100);
 }
 private void failed(String message){
  runOnUiThread(()->{
   rendering=false;exportBtn.setEnabled(true);
   status.setText(message);
   new AlertDialog.Builder(this).setTitle("Video export diagnostic").setMessage(message)
    .setPositiveButton("OK",null).show();
  });
 }
 private Uri saveVideo(File file)throws IOException{
  ContentValues values=new ContentValues();
  values.put(MediaStore.Video.Media.DISPLAY_NAME,"CineZehn_PRO_"+System.currentTimeMillis()+".mp4");
  values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
  values.put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/CineZehn");
  values.put(MediaStore.Video.Media.IS_PENDING,1);
  Uri out=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
  if(out==null)throw new IOException("Gallery insert failed");
  try(InputStream in=new FileInputStream(file);OutputStream dest=getContentResolver().openOutputStream(out)){
   if(dest==null)throw new IOException("Cannot write Gallery");
   byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)dest.write(bytes,0,n);
  }catch(Exception e){getContentResolver().delete(out,null,null);throw e;}
  values.clear();values.put(MediaStore.Video.Media.IS_PENDING,0);
  getContentResolver().update(out,values,null,null);return out;
 }
 private void share(){
  if(exported==null){warn("Export the current project first.");return;}
  Intent i=new Intent(Intent.ACTION_SEND);i.setType("video/mp4");
  i.putExtra(Intent.EXTRA_STREAM,exported);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  startActivity(Intent.createChooser(i,"Share exported CineZehn video"));
 }
 @Override protected void onPause(){pause();store();super.onPause();}
 @Override protected void onDestroy(){
  pause();if(audioPlayer!=null){audioPlayer.release();audioPlayer=null;}
  if(transformer!=null&&rendering)transformer.cancel();
  jobs.shutdownNow();super.onDestroy();
 }
}
