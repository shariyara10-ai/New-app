package com.shari.autocut;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;

/**
 * Modern, accessible, 100% native Android dashboard.
 * Real routes: single-file editor, multi-clip editor, offline rule director,
 * model management, installed recipe/preview library.
 * No cloud LLM or non-functional AI generation buttons.
 */
public final class HomeActivity extends Activity {
 private final int BLUE=Color.rgb(72,91,245),PURPLE=Color.rgb(117,63,228),TEAL=Color.rgb(30,169,171);
 private boolean dark=false;
 private int page=0;
 private LinearLayout screen,body,navigation,content;
 private EditText instruction,scriptInput;
 private TextView planOutput;
 private Spinner styleChoice;
 private EditText searchInput;
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private int background(){return dark?Color.rgb(14,19,34):Color.rgb(248,250,255);}
 private int surface(){return dark?Color.rgb(27,35,55):Color.WHITE;}
 private int titleColor(){return dark?Color.WHITE:Color.rgb(24,33,57);}
 private int secondary(){return dark?Color.rgb(171,188,211):Color.rgb(98,113,138);}
 private int borderColor(){return dark?Color.rgb(56,68,94):Color.rgb(231,236,245);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  dark=getPreferences(0).getBoolean("dark",true);
  page=state==null?0:Math.max(0,Math.min(4,state.getInt("page",0)));
  display(page);
 }
 @Override protected void onSaveInstanceState(Bundle out){
  out.putInt("page",page);super.onSaveInstanceState(out);
 }
 private GradientDrawable shape(int color,float radius, int stroke){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
  if(stroke!=0)d.setStroke(dp(1),stroke);return d;
 }
 private GradientDrawable gradient(){
  GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
   new int[]{Color.rgb(47,69,205),Color.rgb(104,74,223),Color.rgb(120,77,224)});
  d.setCornerRadius(dp(26));return d;
 }
 private LinearLayout column(){
  LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
 }
 private TextView text(String s,float size,boolean bold,int color){
  TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
  t.setIncludeFontPadding(false);
  if(bold)t.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
  return t;
 }
 private LinearLayout.LayoutParams space(int top){
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);return p;
 }
 private void add(LinearLayout l,View view,int top){l.addView(view,space(top));}
 private LinearLayout roundedCard(){
  LinearLayout c=column();c.setBackground(shape(surface(),22,borderColor()));
  c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setElevation(dp(1));return c;
 }
 private Button button(String label,boolean primary){
  Button b=new Button(this);
  b.setText(label);b.setAllCaps(false);b.setTextSize(14);
  b.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
  b.setMinHeight(dp(50));
  b.setTextColor(primary?Color.WHITE:titleColor());
  b.setBackground(shape(primary?BLUE:(dark?Color.rgb(43,52,75):Color.rgb(239,243,251)),14,0));
  b.setElevation(0);b.setPadding(dp(16),dp(6),dp(16),dp(6));
  return b;
 }
 private void display(int tab){
  page=tab;
  getWindow().setStatusBarColor(background());getWindow().setNavigationBarColor(background());
  int flags=dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
  getWindow().getDecorView().setSystemUiVisibility(flags);
  screen=column();screen.setBackgroundColor(background());
  setContentView(screen);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
  screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  content=column();content.setPadding(dp(18),dp(14),dp(18),dp(30));scroll.addView(content);
  header();
  if(tab==0)home();
  else if(tab==1)studios();
  else if(tab==2)director();
  else if(tab==3)library();
  else settings();
  navbar();
 }
 private void header(){
  LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
  TextView badge=text("C",22,true,Color.WHITE);badge.setGravity(Gravity.CENTER);
  badge.setBackground(shape(BLUE,13,0));top.addView(badge,new LinearLayout.LayoutParams(dp(44),dp(44)));
  LinearLayout names=column();names.setPadding(dp(11),0,0,0);
  add(names,text("CineZehn",23,true,titleColor()),0);
  add(names,text("AI STUDIO  •  PRO BETA",9,true,secondary()),3);
  top.addView(names,new LinearLayout.LayoutParams(0,-2,1));
  TextView offline=text("● OFFLINE",10,true,TEAL);offline.setGravity(Gravity.CENTER);
  offline.setPadding(dp(10),dp(9),dp(10),dp(9));offline.setBackground(shape(dark?Color.rgb(27,61,63):Color.rgb(229,248,244),22,0));
  top.addView(offline);
  add(content,top,0);
 }
 private void title(String heading,String detail){
  add(content,text(heading,26,true,titleColor()),28);
  if(detail!=null)add(content,text(detail,13,false,secondary()),8);
 }
 private void smallHeading(String label){
  add(content,text(label,17,true,titleColor()),22);
 }
 private void featureCard(String icon,String heading,String detail,String action,Runnable click){
  LinearLayout card=roundedCard();
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);
  TextView ico=text(icon,23,true,BLUE);ico.setGravity(Gravity.CENTER);
  ico.setBackground(shape(dark?Color.rgb(39,51,82):Color.rgb(234,239,255),14,0));
  row.addView(ico,new LinearLayout.LayoutParams(dp(54),dp(54)));
  LinearLayout words=column();words.setPadding(dp(14),0,0,0);
  add(words,text(heading,16,true,titleColor()),1);
  TextView info=text(detail,12,false,secondary());info.setLineSpacing(dp(2),1);add(words,info,6);
  row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
  add(card,row,0);
  if(action!=null){
   Button b=button(action,false);add(card,b,15);b.setOnClickListener(v->click.run());
  }
  add(content,card,12);
 }
 private void hero(){
  LinearLayout card=column();card.setBackground(gradient());card.setPadding(dp(22),dp(24),dp(22),dp(22));
  add(card,text("YOUR CREATIVE AI WORKSPACE",10,true,Color.rgb(212,216,255)),0);
  add(card,text("Edit smarter.\nCreate faster.",30,true,Color.WHITE),12);
  add(card,text("Flow/Veo clips, cinematic captions, offline tools & professional MP4 exports — right on your phone.",13,false,Color.rgb(230,229,255)),11);
  Button make=button("+  NEW AI VIDEO PROJECT",true);make.setBackground(shape(Color.WHITE,14,0));make.setTextColor(Color.rgb(50,59,181));
  add(card,make,19);make.setOnClickListener(v->startActivity(new Intent(this,StoryboardPlannerActivity.class)));
  add(content,card,20);
 }
 private void home(){
  hero();
  smallHeading("Your studios");
  featureCard("✦","AI AUTO PLANNER","Paste a ChatGPT timestamped master editing prompt; choose 10–100 visual slots and import scene PNGs, up to 5 overlays and custom host per shot.","Open Auto Planner",()->startActivity(new Intent(this,StoryboardPlannerActivity.class)));
  featureCard("✂","VIDEO EDITOR PRO","Full manual storyboard finishing: scene timeline, layer positions, real keyframe-style effects, caption changes and final MP4 export.","Open Pro Editor",()->startActivity(new Intent(this,ProTimelineEditorActivity.class)));
  featureCard("▣","FLOW CLIP MERGER — SEPARATE","A dedicated quick tool only for joining Google Flow/Veo-generated clips. Never mixed with the Pro Editor.","Open Flow Merger",()->openFlow(null,null));
  featureCard("◈","PNG ASSET GENERATOR","Create real transparent text, icons, arrows, labels, badges and infographic PNGs offline, then insert into video layers.","Open Asset Generator",()->startActivity(new Intent(this,AssetGeneratorStudioActivity.class)));
  featureCard("♫","WHISPER TRANSCRIPT • OFFLINE","Install an official GGML multilingual model and transcribe Urdu, Hindi, English or mixed speech on-device; export SRT/VTT and apply to Pro scenes.","Open Whisper Studio",()->startActivity(new Intent(this,WhisperStudioActivity.class)));
  featureCard("▶","LEGACY SINGLE-VIDEO TOOL","Basic source trimming, Vosk recognition and captioned media output. Advanced workflow is Pro Editor.","Open legacy tool",()->startActivity(new Intent(this,MainActivity.class)));
  featureCard("✧","Offline Creative Director","Give a natural-language instruction; receive a local rule-based editing plan you can apply to a Flow project.","Open AI Director",()->display(2));
  featureCard("◈","Motion & Asset Library","Browse installed procedural presets, styles and animated original graphics.","Explore library",()->display(3));
  featureCard("✂","Offline Silence Cutter","Analyze audio volume, remove long quiet gaps and export a new MP4/M4A without changing your original.","Open silence cutter",()->startActivity(new Intent(this,SilenceCutActivity.class)));
  quickStatus();
 }
 private void quickStatus(){
  LinearLayout c=roundedCard();
  add(c,text("OFFLINE READINESS",11,true,secondary()),0);
  File model=ModelImporter.active(this);
  add(c,text(model==null?"Speech model not installed":"Offline speech model available",15,true,titleColor()),10);
  add(c,text(model==null?"Use Settings to install a Vosk English/Hindi model once. Editing tools remain usable without it.":"Speech-to-text can run on your phone without Internet. Review transcription accuracy before export.",12,false,secondary()),6);
  add(content,c,17);
 }
 private void studios(){
  title("Creation studios","Choose your source and start a real editing workflow.");
  featureCard("✦","AUTO PLANNER & ASSET UPLOADS","Choose a storyboard asset count, receive one prompt/upload slot per visual, 5 optional overlays each and a selectable PNG host.","Create AI edit plan",()->startActivity(new Intent(this,StoryboardPlannerActivity.class)));
  featureCard("✂","VIDEO EDITOR PRO","Dedicated manual finishing timeline, layer transforms, independent scene timing and MP4 export.","Open professional editor",()->startActivity(new Intent(this,ProTimelineEditorActivity.class)));
  featureCard("▣","FLOW CLIP MERGER (SEPARATE)","Join independent Google Flow/Veo clips without forcing effects, host or captions; manual reorder and MP4 export.","Open clip merger",()->openFlow(null,null));
  featureCard("▶","LEGACY VIDEO / AUDIO TOOL","Basic imported video/voiceover, captions and optional PNG. Use Pro Editor for new projects.","Open legacy editor",()->startActivity(new Intent(this,MainActivity.class)));
  featureCard("♫","WHISPER SPEECH & CAPTIONS","Real whisper.cpp native offline transcription with imported multilingual model and project caption alignment.","Open Whisper Transcript",()->startActivity(new Intent(this,WhisperStudioActivity.class)));
  featureCard("◍","Offline Voice & Caption Tools","Import a Vosk ZIP, transcribe a selected audio/video clip, approximate Romanization and correct subtitles manually.","Open transcription tools",()->startActivity(new Intent(this,MainActivity.class)));
  featureCard("◈","OFFLINE PNG ASSET GENERATOR","Generate real text, arrow, callout, highlight ring and infographic PNG assets without Internet.","Open PNG generator",()->startActivity(new Intent(this,AssetGeneratorStudioActivity.class)));
  featureCard("⌁","Cinematic Effects Library","Animated scene numbering, context graphics, highlighted text, intro/outro and 12 story styles.","See real effect options",()->display(3));
  featureCard("✂","Silence & Pauses","Offline energy analysis to identify quiet sections, adjustable sensitivity, non-destructive clipping and export.","Open silence cutter",()->startActivity(new Intent(this,SilenceCutActivity.class)));
  add(content,text("Coming later: reliable semantic LLM reasoning, photorealistic avatars, 3D gesture rigs, online asset research and automatic meme downloads. These are not in this APK.",12,false,secondary()),18);
 }
 private void director(){
  title("Offline AI Director","Describe your edit. Current engine is an offline rule-based assistant, not ChatGPT or a local neural language model.");
  LinearLayout panel=roundedCard();
  add(panel,text("EDITING INSTRUCTION",11,true,secondary()),0);
  instruction=new EditText(this);instruction.setMinLines(3);instruction.setTextSize(15);
  instruction.setTextColor(titleColor());instruction.setHintTextColor(secondary());
  instruction.setHint("Example: suspenseful Islamic documentary with energetic captions and subtle cinematic graphics");
  instruction.setBackground(shape(dark?Color.rgb(37,46,68):Color.rgb(245,247,252),13,0));
  instruction.setPadding(dp(14),dp(12),dp(14),dp(12));add(panel,instruction,12);
  String saved=getPreferences(0).getString("director_cmd","");
  if(!saved.isEmpty())instruction.setText(saved);
  add(panel,text("YOUR NARRATION / SCENE CONTEXT (OPTIONAL)",11,true,secondary()),15);
  scriptInput=new EditText(this);scriptInput.setMinLines(2);scriptInput.setMaxLines(7);scriptInput.setTextSize(14);
  scriptInput.setTextColor(titleColor());scriptInput.setHintTextColor(secondary());
  scriptInput.setHint("Paste 1–4 script sentences to generate a more relevant visual plan.");
  scriptInput.setPadding(dp(14),dp(12),dp(14),dp(12));scriptInput.setBackground(shape(dark?Color.rgb(37,46,68):Color.rgb(245,247,252),13,0));
  add(panel,scriptInput,9);
  add(panel,text("STYLE",11,true,secondary()),15);
  styleChoice=new Spinner(this);
  ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,DirectorEngine.STYLES);
  styleChoice.setAdapter(adapter);add(panel,styleChoice,8);
  Button generate=button("✧  Generate offline creative plan",true);add(panel,generate,16);
  generate.setOnClickListener(v->generatePlan());
  Button open=button("Open Flow Studio with this brief",false);add(panel,open,9);
  open.setOnClickListener(v->openFlow(instruction.getText().toString(),String.valueOf(styleChoice.getSelectedItem())));
  add(content,panel,20);
  LinearLayout result=roundedCard();add(result,text("DIRECTOR OUTPUT",11,true,secondary()),0);
  planOutput=text("Your scene plan will appear here. The existing Flow editor can apply selected category styles and timed graphic overlays.",13,false,titleColor());
  planOutput.setTextIsSelectable(true);planOutput.setLineSpacing(dp(3),1.1f);add(result,planOutput,12);
  Button copy=button("Copy creative plan",false);add(result,copy,12);
  copy.setOnClickListener(v->{
   android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
   clipboard.setPrimaryClip(ClipData.newPlainText("CineZehn plan",planOutput.getText()));
   Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();
  });
  add(content,result,12);
 }
 private void generatePlan(){
  String command=instruction.getText().toString().trim();
  String narration=scriptInput.getText().toString().trim();
  if(command.isEmpty()&&narration.isEmpty()){planOutput.setText("Add an instruction or narration to generate a plan.");return;}
  getPreferences(0).edit().putString("director_cmd",command).apply();
  String style=String.valueOf(styleChoice.getSelectedItem());
  String chosen=CreativeDirector.choose(command,style);
  ArrayList<CaptionSegment> cues=CaptionSegment.parse(narration,30000L);
  String plan;
  if(cues.isEmpty()){
   plan="OFFLINE DIRECTOR\nSelected style: "+chosen+"\n\n"+styleGuidance(chosen)+
    "\n\nImport Flow clips, review original audio, choose appropriate style and enable contextual graphics. Without a script, semantic visuals cannot be selected reliably.";
  }else{
   AutoPlanner.apply(cues,chosen.toLowerCase(Locale.ROOT));
   plan=DirectorEngine.run(command,cues,chosen)+"\n"+AutoPlanner.assetPrompts(cues,chosen.toLowerCase(Locale.ROOT));
  }
  planOutput.setText(plan);
 }
 private String styleGuidance(String style){
  return "Recommended visual treatment: "+style+
   ". Use short purposeful edits, captions only where the audio supports them, accessible contrast, original graphics and clean transition timing. Keep speech intelligible and do not invent historical facts.";
 }
 private void openFlow(String prompt,String style){
  Intent i=new Intent(this,FlowJoinActivity.class);
  if(prompt!=null)i.putExtra("director_prompt",prompt);
  if(style!=null)i.putExtra("director_style",style);
  startActivity(i);
 }
 private void library(){
  title("Creative library","Installed recipes and original vector graphics; no Internet stock library is bundled.");
  LinearLayout note=roundedCard();
  add(note,text("12 Editing Styles  •  "+DirectorEngine.ASSETS.length+" Core Recipes",15,true,titleColor()),0);
  add(note,text("These are local creative recipes. The Flow editor currently renders scene indicators, topic graphics and captions; not every library concept is a separate finished effect.",12,false,secondary()),9);
  add(content,note,18);
  smallHeading("Editing collections");
  for(String name:CreativeDirector.STYLES){
   LinearLayout c=roundedCard();
   LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
   TextView tag=text("✦",18,true,BLUE);tag.setGravity(Gravity.CENTER);
   tag.setBackground(shape(dark?Color.rgb(38,52,82):Color.rgb(234,239,255),12,0));
   row.addView(tag,new LinearLayout.LayoutParams(dp(42),dp(42)));
   LinearLayout desc=column();desc.setPadding(dp(14),0,0,0);
   add(desc,text(name,15,true,titleColor()),0);
   add(desc,text("Offline preset / creative direction",11,false,secondary()),5);
   row.addView(desc,new LinearLayout.LayoutParams(0,-2,1));
   add(c,row,0);
   c.setOnClickListener(v->openFlow("Create a professional "+name+" video edit",name));
   add(content,c,9);
  }
  smallHeading("Built-in creative recipe index");
  for(String asset:DirectorEngine.ASSETS){
   TextView t=text("◈  "+asset,14,false,titleColor());
   t.setPadding(dp(15),dp(15),dp(15),dp(15));t.setBackground(shape(surface(),14,borderColor()));
   add(content,t,7);
   t.setOnClickListener(v->new AlertDialog.Builder(this)
     .setTitle(asset).setMessage("Creative recipe reference. Current APK may not render this exact effect automatically; check the Flow editor's available graphic overlays.")
     .setPositiveButton("Open Flow Studio",(d,w)->openFlow("Use "+asset+" where contextually relevant",null))
     .setNegativeButton("Close",null).show());
  }
 }
 private void settings(){
  title("Settings & models","Manage app appearance and offline speech resources.");
  LinearLayout theme=roundedCard();add(theme,text("APPEARANCE",12,true,secondary()),0);
  Switch toggle=new Switch(this);toggle.setText("Dark dashboard");toggle.setChecked(dark);toggle.setTextColor(titleColor());
  add(theme,toggle,12);toggle.setOnCheckedChangeListener((v,checked)->{
   dark=checked;getPreferences(0).edit().putBoolean("dark",checked).apply();display(4);
  });
  add(theme,text("The Flow Studio currently uses its own light editor theme; this toggle controls the new dashboard.",12,false,secondary()),8);
  add(content,theme,17);
  quickStatus();
  featureCard("↓","Install speech models","Download once on a network, then run English/Hindi Vosk speech-to-text offline. Urdu and code switching need review.","Open model manager",()->startActivity(new Intent(this,MainActivity.class)));
  featureCard("✧","Clear saved Director instruction","Remove your last local editing brief without deleting projects.","Clear instruction",()->{
   getPreferences(0).edit().remove("director_cmd").apply();
   Toast.makeText(this,"Saved instruction cleared",Toast.LENGTH_SHORT).show();
  });
  LinearLayout about=roundedCard();
  add(about,text("BUILD / LIMITATIONS",12,true,secondary()),0);
  add(about,text("CineZehn Creator Studio • v0.5 modern UI",16,true,titleColor()),8);
  add(about,text("Offline media editing + procedural creative tools. Not a ChatGPT model. No online video generation, automatic licensed stock acquisition, real video-object intelligence, neural avatar animation or guaranteed Urdu recognition.",12,false,secondary()),9);
  add(content,about,15);
 }
 private void navbar(){
  navigation=new LinearLayout(this);navigation.setGravity(Gravity.CENTER);
  navigation.setBackground(shape(surface(),0,0));
  String[] names={"Home","Studios","Director","Library","Settings"};
  String[] icons={"⌂","▣","✧","◈","⚙"};
  for(int i=0;i<names.length;i++){
   final int item=i;boolean active=i==page;
   LinearLayout nav=column();nav.setGravity(Gravity.CENTER);
   TextView symbol=text(icons[i],21,true,active?BLUE:secondary());symbol.setGravity(Gravity.CENTER);
   TextView label=text(names[i],10,active,active?BLUE:secondary());label.setGravity(Gravity.CENTER);
   add(nav,symbol,0);add(nav,label,3);
   navigation.addView(nav,new LinearLayout.LayoutParams(0,dp(65),1));
   nav.setOnClickListener(v->display(item));
  }
  screen.addView(navigation,new LinearLayout.LayoutParams(-1,dp(68)));
 }
}
