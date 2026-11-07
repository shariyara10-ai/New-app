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
import androidx.media3.transformer.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.media3.common.util.UnstableApi
public final class FlowJoinActivity extends Activity {
 private static final int REQUEST_CLIPS=71;
 private final ArrayList<FlowScene> scenes=new ArrayList<>();
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final Handler handler=new Handler(Looper.getMainLooper());
 private LinearLayout root, list;
 private TextView summary,status;
 private EditText endText;
 private CheckBox addEnd;
 private Spinner endLength;
 private ProgressBar progress;
 private VideoView preview;
 private Button joinButton;
 private Transformer transformer;
 private Runnable polling;
 private boolean busy;
 private Uri saved;
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView title(String s,int sz,boolean b){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(sz);t.setTextColor(b?Color.BLACK:Color.DKGRAY);
  if(b)t.setTypeface(null,Typeface.BOLD);return t;
 }
 private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);return b;}
 private void add(LinearLayout p,View v){p.addView(v,new LinearLayout.LayoutParams(-1,-2));}
 private LinearLayout panel(String heading){
  LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);
  p.setPadding(dp(13),dp(14),dp(13),dp(15));
  p.setBackgroundColor(Color.rgb(245,246,248));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
  lp.topMargin=dp(13);root.addView(p,lp);add(p,title(heading,17,true));return p;
 }
 @Override public void onCreate(Bundle savedState){
  super.onCreate(savedState);getWindow().setStatusBarColor(Color.WHITE);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(Color.WHITE);
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(22),dp(16),dp(30));
  sc.addView(root);sc.setOnApplyWindowInsetsListener((v,i)->{sc.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
  setContentView(sc);
  add(root,title("CINEZEHN / AI STUDIO",12,false));add(root,title("Flow Scene Joiner",27,true));
  add(root,title("Sequence your Flow/Veo clips with original built-in voiceovers. No meme insertions or extra scene effects. Optional animated outro.",13,false));
  LinearLayout input=panel("1  Select videos");
  Button pick=button("Select multiple Flow clips");add(input,pick);pick.setOnClickListener(v->select());
  summary=title("No clips selected",13,false);add(input,summary);
  list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);add(input,list);
  Button clear=button("Clear all clips");add(input,clear);clear.setOnClickListener(v->{if(!busy){scenes.clear();refresh();}});
  LinearLayout pv=panel("2  Preview individual clip");
  preview=new VideoView(this);preview.setBackgroundColor(Color.BLACK);
  pv.addView(preview,new LinearLayout.LayoutParams(-1,dp(190)));
  MediaController controls=new MediaController(this);controls.setAnchorView(preview);preview.setMediaController(controls);
  LinearLayout ending=panel("3  Thanks for watching outro");
  addEnd=new CheckBox(this);addEnd.setText("Append animated ending");addEnd.setTextColor(Color.BLACK);addEnd.setChecked(true);add(ending,addEnd);
  endText=new EditText(this);endText.setSingleLine(true);endText.setText("THANKS FOR WATCHING!");
  endText.setTextColor(Color.BLACK);add(ending,endText);
  endLength=new Spinner(this);
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"3 sec","4 sec","5 sec","6 sec"});
  adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  endLength.setAdapter(adapter);endLength.setSelection(1);add(ending,endLength);
  add(ending,title("Like • Comment • Subscribe. Outro contains no music or memes.",12,false));
  LinearLayout output=panel("4  Export");
  joinButton=button("JOIN CLIPS & EXPORT MP4");add(output,joinButton);joinButton.setOnClickListener(v->render());
  Button share=button("Share last video");add(output,share);share.setOnClickListener(v->share());
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);add(output,progress);
  status=title("Ready — add your clips.",13,false);add(output,status);
  add(root,title("All processing on this phone, no account or API. Scene joining uses clean cuts. Mixed codecs/HDR may require compatible source files.",12,false));
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
   final int idx=j;FlowScene s=scenes.get(j);total+=s.durationMs;if(s.hasAudio)aud++;
   LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(4),dp(8),dp(4),dp(8));add(list,l);
   add(l,title((j+1)+". "+s.fileName+" • "+String.format(Locale.US,"%.1fs",s.durationMs/1000f)+(s.hasAudio?" • Voice/audio":" • Silent"),12,true));
   LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);add(l,actions);
   Button play=button("Preview"),up=button("↑"),down=button("↓"),remove=button("✕");
   for(Button b:new Button[]{play,up,down,remove})actions.addView(b,new LinearLayout.LayoutParams(0,dp(43),1));
   play.setOnClickListener(v->{preview.setVideoURI(s.uri);preview.start();});
   up.setEnabled(j>0);down.setEnabled(j<scenes.size()-1);
   up.setOnClickListener(v->{if(!busy){Collections.swap(scenes,idx,idx-1);refresh();}});
   down.setOnClickListener(v->{if(!busy){Collections.swap(scenes,idx,idx+1);refresh();}});
   remove.setOnClickListener(v->{if(!busy){scenes.remove(idx);refresh();}});
  }
  summary.setText(scenes.size()+" scenes • "+String.format(Locale.US,"%.1f",total/1000f)+" sec • "+aud+" with audio. Use arrows to reorder.");
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
 private void render(){
  if(busy)return;if(scenes.isEmpty()){status.setText("Import at least one clip.");return;}
  if(new StatFs(getCacheDir().getPath()).getAvailableBytes()<250*1024*1024L){status.setText("Low storage: free 250 MB or more.");return;}
  busy=true;joinButton.setEnabled(false);progress.setProgress(3);saved=null;
  File file=new File(getCacheDir(),"flow-join-"+System.currentTimeMillis()+".mp4");
  try{
   ArrayList<EditedMediaItem> timeline=new ArrayList<>();
   for(FlowScene s:scenes)timeline.add(new EditedMediaItem.Builder(MediaItem.fromUri(s.uri)).build());
   if(addEnd.isChecked()){
    long lengthMs=(endLength.getSelectedItemPosition()+3)*1000L;
    MediaItem image=new MediaItem.Builder().setUri(Uri.fromFile(bgImage())).setImageDurationMs(lengthMs).build();
    String text=endText.getText().toString().trim();
    if(text.isEmpty())text="THANKS FOR WATCHING!";
    Effects effects=new Effects(Collections.emptyList(),Collections.singletonList(new OverlayEffect(
     Collections.singletonList(new EndOverlay(text,lengthMs)))));
    timeline.add(new EditedMediaItem.Builder(image).setFrameRate(15).setEffects(effects).build());
   }
   Composition comp=new Composition.Builder(Collections.singletonList(EditedMediaItemSequence.withAudioAndVideoFrom(timeline))).build();
   transformer=new Transformer.Builder(this).setAudioMimeType(MimeTypes.AUDIO_AAC).setVideoMimeType(MimeTypes.VIDEO_H264)
    .setMaxDelayBetweenMuxerSamplesMs(120000L)
    .addListener(new Transformer.Listener(){
     @Override public void onCompleted(Composition composition,ExportResult r){
      stopTicker();status.setText("Video encoded. Saving...");
      io.execute(()->{
       try{Uri u=save(file);runOnUiThread(()->{saved=u;busy=false;joinButton.setEnabled(true);progress.setProgress(100);status.setText("Saved to Movies/CineZehn.");});}
       catch(Exception e){runOnUiThread(()->failure("Encoded but could not save: "+e.getMessage()));}
       finally{file.delete();}
      });
     }
     @Override public void onError(Composition composition,ExportResult r,ExportException e){
      file.delete();failure("MP4 export error "+e.errorCode+": "+e.getMessage()+(e.getCause()==null?"":"\nCause: "+e.getCause()));
     }
    }).build();
   transformer.start(comp,file.getAbsolutePath());
   status.setText("Joining original Flow clips, please keep app open.");poll();
  }catch(Exception e){file.delete();failure("Export setup failed: "+e.getMessage());}
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
  stopTicker();busy=false;joinButton.setEnabled(true);status.setText(message);
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
