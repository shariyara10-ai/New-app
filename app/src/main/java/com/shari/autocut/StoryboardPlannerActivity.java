package com.shari.autocut;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Premium storyboard planner: import ChatGPT scene directions, choose asset count,
 * show one image prompt/upload box per asset and up to five overlay/host slots.
 * Every chosen visual is passed to the real manual editor and renderer.
 */
public final class StoryboardPlannerActivity extends Activity {
 private static final int PICK_ASSET=812,PICK_AUDIO=813,MAKE_PNG=814;
 private static final int BASE=0xFF0C0E18,SURFACE=0xFF191C2C,ACCENT=0xFF835BF6,TEXT=0xFFF9F8FF,MUTED=0xFFAAAEC5;
 private StudioProject project;
 private int selected=0,assetType=-1,tab=0;
 private TextView status,planSummary;
 private EditText masterPrompt;
 private Spinner countSelect,styleSelect;
 private LinearLayout body,root,sceneStrip;
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private GradientDrawable shape(int color,int rad){
  GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(rad));return b;
 }
 private TextView text(String s,int font,boolean bold,int color){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(font);t.setTextColor(color);
  t.setIncludeFontPadding(false);if(bold)t.setTypeface(null,Typeface.BOLD);return t;
 }
 private Button button(String title,boolean primary){
  Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(13);
  b.setTextColor(primary?TEXT:MUTED);b.setMinHeight(dp(48));b.setElevation(0);
  b.setBackground(shape(primary?ACCENT:0xFF282B3F,13));return b;
 }
 private void add(LinearLayout parent,View child,int top){
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);
 }
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private LinearLayout card(String title){
  LinearLayout c=col();c.setPadding(dp(15),dp(16),dp(15),dp(18));c.setBackground(shape(SURFACE,19));
  add(c,text(title,16,true,TEXT),0);add(body,c,13);return c;
 }
 private EditText edit(String hint,String value,int lines){
  EditText e=new EditText(this);e.setTextColor(TEXT);e.setHintTextColor(0xFF717791);
  e.setTextSize(13);e.setHint(hint);e.setText(value);e.setMinLines(lines);e.setMaxLines(Math.max(lines,lines+2));
  e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setGravity(Gravity.TOP);
  e.setBackground(shape(0xFF24283C,12));return e;
 }
 private Spinner spinner(String[] values,int selected){
  Spinner s=new Spinner(this);
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);
  s.setAdapter(adapter);s.setSelection(Math.max(0,Math.min(values.length-1,selected)));return s;
 }
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setStatusBarColor(BASE);
  try{project=StudioProject.load(this);}
  catch(Exception ignored){project=StudioProject.plan("",20,60000);}
  getWindow().setNavigationBarColor(BASE);
  build();
 }
 private void save(){
  try{project.save(this);}catch(Exception e){toast("Save failed: "+e.getMessage());}
 }
 private void build(){
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BASE);
  root=col();root.setPadding(dp(15),dp(16),dp(15),dp(32));scroll.addView(root);setContentView(scroll);
  LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);add(root,top,0);
  TextView title=text("✦  AI AUTO PLANNER",19,true,TEXT);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  Button back=button("Home",false);top.addView(back);back.setOnClickListener(v->finish());
  add(root,text("ChatGPT master prompt → exact asset slots → editable timeline",12,false,MUTED),7);
  LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);
  String[] tabs={"01  Plan","02  Assets","03  Layers"};
  for(int i=0;i<3;i++){
   int target=i;Button b=button(tabs[i],tab==i);b.setTextSize(12);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(45),1);lp.rightMargin=dp(5);nav.addView(b,lp);
   b.setOnClickListener(v->{captureDraft();tab=target;build();});
  }
  add(root,nav,17);
  planSummary=text("Ready",12,false,0xFFBFC6E2);add(root,planSummary,14);
  ScrollView shots=new ScrollView(this);shots.setFillViewport(false);
  body=col();shots.addView(body);add(root,shots,6);
  if(tab==0)planScreen();else if(tab==1)assetsScreen();else layersScreen();
  LinearLayout actions=new LinearLayout(this);
  Button editor=button("OPEN MANUAL PRO EDITOR  →",true);
  actions.addView(editor,new LinearLayout.LayoutParams(-1,dp(53)));add(root,actions,15);
  editor.setOnClickListener(v->{captureDraft();save();startActivity(new Intent(this,ProTimelineEditorActivity.class));});
  status=text("Project autosaves locally • Imported media stays on device",11,false,MUTED);add(root,status,8);
  updateSummary();
 }
 private void updateSummary(){
  int done=project.completeVisuals();int overlays=0,hosts=0;
  for(StudioProject.Shot s:project.shots){
   for(int j=0;j<s.overlaySlots;j++)if(s.overlays[j]!=null)overlays++;
   if(s.hostEnabled&&s.host!=null)hosts++;
  }
  planSummary.setText(project.shots.size()+" planned scenes   •   "+done+" images   •   "+overlays+" PNG overlays   •   "+hosts+" hosts   •   "+(project.durationMs/1000)+" sec");
 }
 private void planScreen(){
  LinearLayout promptCard=card("MASTER EDITING PROMPT");
  add(promptCard,text("Paste the timestamped video-editing instructions generated by ChatGPT. The parser extracts VISUAL, MOTION, GRAPHICS, SFX and timestamps. It does not generate images by itself.",12,false,MUTED),11);
  masterPrompt=edit("Paste your complete chocolate factory master prompt here...",project.rawPrompt,9);
  add(promptCard,masterPrompt,12);
  LinearLayout controls=card("AUTOMATIC STORYBOARD SETTINGS");
  add(controls,text("Editing style",12,true,MUTED),13);
  String[] styles=CreativeDirector.STYLES;
  int styleIndex=Arrays.asList(styles).indexOf(project.style);if(styleIndex<0)styleIndex=0;
  styleSelect=spinner(styles,styleIndex);add(controls,styleSelect,7);
  add(controls,text("Number of assets (each receives its own visual prompt and upload section)",12,true,MUTED),15);
  String[] nums=new String[StudioProject.counts().length];int choice=0;
  for(int i=0;i<nums.length;i++){int val=StudioProject.counts()[i];nums[i]=val+" assets";if(val==project.shots.size())choice=i;}
  countSelect=spinner(nums,choice);add(controls,countSelect,9);
  add(controls,text("Video length comes from the narration file if provided; otherwise the current project's length is used.",12,false,MUTED),10);
  Button whisperButton=button("♫  WHISPER • Transcribe audio/video to timed captions",false);
  add(controls,whisperButton,11);
  whisperButton.setOnClickListener(v->{captureDraft();save();startActivity(new Intent(this,WhisperStudioActivity.class));});
  Button audio=button(project.audioUri==null?"♫  Import narration / voiceover":"✓  Voiceover selected — change file",false);
  add(controls,audio,12);audio.setOnClickListener(v->pick(PICK_AUDIO));
  Button parse=button("✦  ANALYZE MASTER PROMPT & CREATE ASSET SLOTS",true);
  add(controls,parse,13);
  parse.setOnClickListener(v->{
   String content=masterPrompt.getText().toString().trim();
   if(content.isEmpty()){toast("Paste an editing prompt first.");return;}
   int count=StudioProject.counts()[countSelect.getSelectedItemPosition()];
   int oldAttached=project.completeVisuals();
   Runnable generate=()->{
    StudioProject next=StudioProject.plan(content,count,project.durationMs);
    next.audioUri=project.audioUri;next.style=String.valueOf(styleSelect.getSelectedItem());
    next.width=project.width;next.height=project.height;next.addCaptions=project.addCaptions;
    project=next;selected=0;tab=1;save();build();
    toast(count+" visual slots created. Upload source images/PNGs for the exact scenes.");
   };
   if(oldAttached>0)new AlertDialog.Builder(this).setTitle("Recreate storyboard?")
     .setMessage("Regenerating will replace current asset slots and remove their links from this project. Original files will remain untouched.")
     .setPositiveButton("Recreate",(d,w)->generate.run()).setNegativeButton("Cancel",null).show();
   else generate.run();
  });
  LinearLayout information=card("HOW THE ASSET WORKFLOW OPERATES");
  add(information,text("1. Paste timestamped master prompt.\n2. Choose 10, 20, 30, 40, 50 or more assets.\n3. Generate the editable scene/asset plan.\n4. Each scene provides a visual prompt, source upload, host PNG and up to five overlay PNG slots.\n5. Choose motion/effect per element.\n6. Send the project to Manual Pro Editor for preview, changes and MP4 export.",13,false,TEXT),12);
  add(information,text("No fabricated photorealistic images. You can use an external generator and import the real resulting files.",12,false,MUTED),13);
 }
 private void captureDraft(){
  if(tab==0){
   if(masterPrompt!=null)project.rawPrompt=masterPrompt.getText().toString();
   if(styleSelect!=null)project.style=String.valueOf(styleSelect.getSelectedItem());
  }
  save();
 }
 private void chooserScene(String label){
  if(project.shots.isEmpty())return;
  String[] labels=new String[project.shots.size()];
  for(int i=0;i<labels.length;i++)labels[i]=String.format(Locale.US,
   "Asset %02d  •  %s–%s",i+1,time(project.shots.get(i).startMs),time(project.shots.get(i).endMs));
  new AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(labels,selected,(d,n)->{
   selected=n;d.dismiss();build();
  }).setNegativeButton("Cancel",null).show();
 }
 private static String time(long ms){return String.format(Locale.US,"%02d:%05.2f",ms/60000,(ms%60000)/1000.0);}
 private StudioProject.Shot chosen(){return project.shots.get(Math.max(0,Math.min(selected,project.shots.size()-1)));}
 private void assetsScreen(){
  if(project.shots.isEmpty()){add(card("No project"),text("Create an asset plan in the first tab.",14,false,MUTED),10);return;}
  LinearLayout select=card("ASSET SELECTOR");
  Button pickScene=button("ASSET "+(selected+1)+" / "+project.shots.size()+"  •  "+time(chosen().startMs)+"–"+time(chosen().endMs)+"  ▾",true);
  add(select,pickScene,12);pickScene.setOnClickListener(v->chooserScene("Choose asset"));
  LinearLayout pager=new LinearLayout(this);
  Button prev=button("← Previous",false),next=button("Next →",false);
  pager.addView(prev,new LinearLayout.LayoutParams(0,dp(44),1));
  pager.addView(next,new LinearLayout.LayoutParams(0,dp(44),1));
  add(select,pager,9);
  prev.setOnClickListener(v->{selected=(selected+project.shots.size()-1)%project.shots.size();build();});
  next.setOnClickListener(v->{selected=(selected+1)%project.shots.size();build();});
  StudioProject.Shot shot=chosen();
  LinearLayout main=card("ASSET "+(selected+1)+" — MAIN VISUAL");
  add(main,text("ASSET GENERATION PROMPT",11,true,0xFFD1BCFF),13);
  EditText visualPrompt=edit("Visual description",shot.prompt,4);
  add(main,visualPrompt,8);
  visualPrompt.addTextChangedListener(watch(s->{shot.prompt=s;save();}));
  Button make=button("✦  CREATE PNG IN OFFLINE ASSET STUDIO",false);add(main,make,7);
  make.setOnClickListener(v->{assetType=-1;startActivityForResult(new Intent(this,AssetGeneratorStudioActivity.class),MAKE_PNG);});
  Button copy=button("Copy visual generation prompt",false);add(main,copy,8);
  copy.setOnClickListener(v->copy(shot.prompt));
  add(main,text("MAIN IMAGE / VIDEO SOURCE",11,true,0xFFD1BCFF),17);
  assetBox(main,shot.visual,-1,"Upload generated scene image (PNG / JPG / WEBP)");
  add(main,text("ANIMATION",11,true,MUTED),14);
  motionSelect(main,shot.motion,s->{shot.motion=s;save();});
  EditText captions=edit("Caption text for this shot",shot.caption,2);
  add(main,text("CAPTION TEXT",11,true,MUTED),14);add(main,captions,7);
  captions.addTextChangedListener(watch(s->{shot.caption=s;save();}));
  add(main,text("Narration: "+(shot.narration.isEmpty()?"Use imported voiceover":shot.narration),11,false,MUTED),12);
  Button layer=button("EDIT HOST + OVERLAYS FOR THIS ASSET →",true);add(main,layer,14);
  layer.setOnClickListener(v->{tab=2;build();});
  LinearLayout all=card("ASSET PROMPTS — BATCH COPY");
  Button batch=button("Copy prompts for ALL "+project.shots.size()+" assets",false);add(all,batch,9);
  batch.setOnClickListener(v->{
   StringBuilder s=new StringBuilder();
   for(int i=0;i<project.shots.size();i++)
    s.append("ASSET ").append(i+1).append(" [").append(time(project.shots.get(i).startMs)).append("–")
     .append(time(project.shots.get(i).endMs)).append("]\n").append(project.shots.get(i).prompt).append("\n\n");
   copy(s.toString());
  });
 }
 private void layersScreen(){
  if(project.shots.isEmpty())return;
  LinearLayout choose=card("EDIT PER-ASSET LAYERS");
  Button ch=button("Asset "+(selected+1)+"  •  Change selected asset ▾",true);add(choose,ch,10);ch.setOnClickListener(v->chooserScene("Choose asset"));
  StudioProject.Shot shot=chosen();
  LinearLayout host=card("OPTIONAL HOST PNG — ASSET "+(selected+1));
  Switch enabled=new Switch(this);enabled.setText("Show host in this scene");enabled.setTextColor(TEXT);
  enabled.setChecked(shot.hostEnabled);add(host,enabled,10);
  enabled.setOnCheckedChangeListener((b,v)->{shot.hostEnabled=v;save();});
  add(host,text("HOST IMAGE GENERATION PROMPT",11,true,0xFFD1BCFF),13);
  String hostPrompt="Transparent PNG explainer YouTube host, professional outfit, clear full-body/torso, suitable for: "+shot.prompt+
    "; natural speaking and pointing pose, no background, clean edges";
  add(host,text(hostPrompt,12,false,TEXT),9);
  Button cp=button("Copy host prompt",false);add(host,cp,7);cp.setOnClickListener(v->copy(hostPrompt));
  assetBox(host,shot.host,-2,"Upload HOST PNG (transparent image recommended)");
  add(host,text("Host motion",11,true,MUTED),14);
  motionSelect(host,shot.hostMotion,s->{shot.hostMotion=s;save();});
  add(host,text("A static PNG can move, zoom, float or rotate. Accurate hand articulation and neural lip-sync require a rigged avatar/model and are not present in this build.",11,false,MUTED),11);
  LinearLayout overlays=card("UP TO FIVE PNG OVERLAYS PER ASSET");
  String[] options={"1 overlay","2 overlays","3 overlays","4 overlays","5 overlays"};
  Spinner slots=spinner(options,shot.overlaySlots-1);add(overlays,slots,10);
  slots.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int index,long id){
    if(shot.overlaySlots!=index+1){shot.overlaySlots=index+1;save();build();}
   }
   @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
  });
  add(overlays,text("Each overlay has its own prompt, file upload and independent motion effect.",11,false,MUTED),10);
  for(int j=0;j<shot.overlaySlots;j++){
   final int layer=j;
   LinearLayout item=col();item.setBackground(shape(0xFF26293E,14));
   item.setPadding(dp(12),dp(12),dp(12),dp(12));add(overlays,item,12);
   add(item,text("OVERLAY "+(j+1)+"/"+shot.overlaySlots,13,true,TEXT),0);
   EditText prompt=edit("Describe image/logo/PNG",shot.overlayPrompts[j],2);add(item,prompt,9);
   prompt.addTextChangedListener(watch(s->{shot.overlayPrompts[layer]=s;save();}));
   Button generate=button("Create PNG graphics for this overlay",false);add(item,generate,7);
   generate.setOnClickListener(v->{assetType=layer;startActivityForResult(new Intent(this,AssetGeneratorStudioActivity.class),MAKE_PNG);});
   Button cpLayer=button("Copy PNG prompt",false);add(item,cpLayer,7);
   cpLayer.setOnClickListener(v->copy(shot.overlayPrompts[layer]));
   assetBox(item,shot.overlays[j],layer,"Upload transparent PNG / illustration");
   add(item,text("Overlay motion / effect",11,true,MUTED),12);
   motionSelect(item,shot.overlayMotion[j],s->{shot.overlayMotion[layer]=s;save();});
  }
 }
 private interface ValueChanged{void set(String value);}
 private android.text.TextWatcher watch(ValueChanged changed){
  return new android.text.TextWatcher(){
   @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
   @Override public void onTextChanged(CharSequence s,int st,int before,int count){changed.set(s.toString());}
   @Override public void afterTextChanged(android.text.Editable e){}
  };
 }
 private void motionSelect(LinearLayout target,String selectedMotion,ValueChanged changed){
  int index=Arrays.asList(StudioProject.MOTIONS).indexOf(selectedMotion);
  Spinner list=spinner(StudioProject.MOTIONS,Math.max(0,index));add(target,list,7);
  list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int i,long id){changed.set(StudioProject.MOTIONS[i]);}
   @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
  });
 }
 private void assetBox(LinearLayout container,Uri uri,int type,String buttonLabel){
  if(uri!=null){
   ImageView image=new ImageView(this);image.setBackground(shape(0xFF101320,12));
   image.setScaleType(ImageView.ScaleType.FIT_CENTER);
   try(InputStream in=getContentResolver().openInputStream(uri)){
    BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;
    Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);
    if(bitmap!=null)image.setImageBitmap(bitmap);
   }catch(Exception ignored){}
   container.addView(image,new LinearLayout.LayoutParams(-1,dp(137)));
  }
  Button importBtn=button((uri==null?"+  ":"✓  Replace  ")+buttonLabel,false);
  add(container,importBtn,9);importBtn.setOnClickListener(v->{assetType=type;pick(PICK_ASSET);});
  if(uri!=null){
   Button remove=button("Remove attached visual",false);add(container,remove,6);
   remove.setOnClickListener(v->{
    StudioProject.Shot shot=chosen();
    if(type==-1)shot.visual=null;else if(type==-2)shot.host=null;else shot.overlays[type]=null;
    save();build();
   });
  }
 }
 private void copy(String text){
  android.content.ClipboardManager board=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
  board.setPrimaryClip(ClipData.newPlainText("CineZehn asset prompt",text));toast("Prompt copied");
 }
 private void pick(int request){
  Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  intent.addCategory(Intent.CATEGORY_OPENABLE);
  intent.setType(request==PICK_AUDIO?"audio/*":"image/*");
  if(request==PICK_AUDIO)intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"audio/*","video/*"});
  intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  startActivityForResult(intent,request);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(result!=RESULT_OK||data==null||data.getData()==null)return;
  Uri uri=data.getData();
  try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
  if(request==PICK_AUDIO){
   long duration=0;
   MediaMetadataRetriever reader=new MediaMetadataRetriever();
   try{
    reader.setDataSource(this,uri);
    String d=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
    if(d!=null)duration=Long.parseLong(d);
   }catch(Exception ignored){}
   finally{try{reader.release();}catch(Exception ignored){}}
   project.audioUri=uri;
   if(duration>1000)project.durationMs=duration;
  }else if((request==PICK_ASSET||request==MAKE_PNG)&&!project.shots.isEmpty()){
   StudioProject.Shot shot=chosen();
   if(assetType==-1)shot.visual=uri;
   else if(assetType==-2){shot.host=uri;shot.hostEnabled=true;}
   else if(assetType>=0&&assetType<5)shot.overlays[assetType]=uri;
  }
  save();build();
 }
 @Override protected void onPause(){super.onPause();captureDraft();}
}
