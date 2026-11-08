package com.shari.autocut;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/**
 * Fully OFFLINE procedural PNG graphics generator.
 * Generates actual transparent text/arrow/badge/number/diagram assets; it is
 * not a neural diffusion model and never claims photorealistic scene generation.
 */
public final class AssetGeneratorStudioActivity extends Activity {
 private final int BG=0xFF0C0F1A,SURFACE=0xFF1C2033,ACCENT=0xFF825AF3,TXT=Color.WHITE,MUTED=0xFFAFB8D0;
 private Spinner type,color,ratio;
 private EditText caption,prompt;
 private Switch transparent;
 private ImageView preview;
 private Bitmap bitmap;
 private Uri saved;
 private TextView status;
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private TextView text(String s,int f,boolean bold,int col){TextView t=new TextView(this);t.setText(s);t.setTextColor(col);t.setTextSize(f);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
 private Button btn(String s,boolean active){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setElevation(0);b.setMinHeight(dp(50));
  b.setBackground(shape(active?ACCENT:0xFF303449,12));b.setTextColor(TXT);return b;}
 private void add(LinearLayout root,View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);root.addView(v,p);}
 private Spinner select(String[] values){
  Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(a);return s;
 }
 private LinearLayout card(LinearLayout root,String name){
  LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(16),dp(16),dp(16),dp(17));
  c.setBackground(shape(SURFACE,20));add(root,c,13);add(c,text(name,16,true,TXT),0);return c;
 }
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setStatusBarColor(BG);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);
  root.setPadding(dp(15),dp(18),dp(15),dp(27));scroll.addView(root);setContentView(scroll);
  add(root,text("AI ASSET / PNG STUDIO",24,true,TXT),0);
  add(root,text("Original procedural graphics entirely offline. Real image assets you can upload onto Pro Editor layers.",12,false,MUTED),8);
  LinearLayout settings=card(root,"01  Generate a custom visual asset");
  add(settings,text("Asset type",12,true,MUTED),10);
  type=select(new String[]{"Animated-text-ready PNG","Arrow pointer","Number counter","Speech callout","Rounded badge","Process step label","Transparent highlight ring","Infographic comparison"});
  add(settings,type,8);
  add(settings,text("Color theme",12,true,MUTED),12);
  color=select(new String[]{"Bright Yellow","Violet","Neon Cyan","White","Red","Green","Orange"});
  add(settings,color,7);
  add(settings,text("Canvas",12,true,MUTED),12);
  ratio=select(new String[]{"Square 1024×1024","Landscape 1280×720","Portrait 720×1280","Wide title strip 1280×400"});
  add(settings,ratio,7);
  transparent=new Switch(this);transparent.setText("Transparent PNG background");
  transparent.setTextColor(TXT);transparent.setChecked(true);add(settings,transparent,9);
  caption=new EditText(this);caption.setTextColor(TXT);caption.setText("CHOCOLATE FACTORY");
  caption.setHint("Text / label / number");caption.setBackground(shape(0xFF30344C,12));
  caption.setPadding(dp(12),dp(12),dp(12),dp(12));add(settings,caption,11);
  Button create=btn("✦  CREATE PNG ASSET OFFLINE",true);add(settings,create,14);
  create.setOnClickListener(v->generate());
  LinearLayout output=card(root,"02  Asset preview and export");
  preview=new ImageView(this);preview.setBackground(shape(0xFF35394D,12));
  preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
  output.addView(preview,new LinearLayout.LayoutParams(-1,dp(240)));
  Button save=btn("Save PNG to Gallery / CineZehn",true);add(output,save,12);
  save.setOnClickListener(v->saveImage());
  Button use=btn("Insert into current Pro storyboard asset slot",false);add(output,use,9);
  use.setOnClickListener(v->returnImage());
  status=text("Generate a shape or title to preview it.",12,false,MUTED);add(output,status,9);
  LinearLayout references=card(root,"03  Neural AI image generation (optional)");
  add(references,text("Photorealistic factory shots, character portraits and full-motion videos require an installed compatible neural model or an external generation provider. This screen only generates real procedural PNG graphics; it does not fake neural output.",12,false,MUTED),9);
  Button open=btn("Open image-generation prompt planner",false);add(references,open,11);
  open.setOnClickListener(v->startActivity(new Intent(this,StoryboardPlannerActivity.class)));
 }
 private int accent(){
  switch(color.getSelectedItemPosition()){
   case 1:return 0xFF9A7BFF;case 2:return 0xFF47E4E9;case 3:return Color.WHITE;
   case 4:return 0xFFFF6476;case 5:return 0xFF4CE5A5;case 6:return 0xFFFFAE54;
   default:return 0xFFFFD941;
  }
 }
 private void generate(){
  if(bitmap!=null){bitmap.recycle();bitmap=null;}
  int w=1024,h=1024;
  switch(ratio.getSelectedItemPosition()){
   case 1:w=1280;h=720;break;case 2:w=720;h=1280;break;case 3:w=1280;h=400;break;
  }
  bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
  Canvas c=new Canvas(bitmap);Paint p=new Paint(3);
  if(transparent.isChecked())c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
  else c.drawColor(0xFF141A2C);
  float cx=w*.5f,cy=h*.5f,unit=Math.min(w,h)/1000f;
  int main=accent(),kind=type.getSelectedItemPosition();
  String words=caption.getText().toString().trim();if(words.isEmpty())words="CINEZEHN";
  p.setColor(main);p.setStyle(Paint.Style.FILL);
  if(kind==0||kind==5){
   p.setColor(0xDB090E1D);c.drawRoundRect(w*.06f,h*.35f,w*.94f,h*.66f,45*unit,45*unit,p);
   p.setColor(main);c.drawRoundRect(w*.06f,h*.35f,w*.94f,h*.38f,4,4,p);
  }else if(kind==1){
   p.setStrokeWidth(38*unit);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
   c.drawLine(w*.13f,h*.80f,w*.74f,h*.27f,p);p.setStyle(Paint.Style.FILL);
   Path arrow=new Path();arrow.moveTo(w*.74f,h*.27f);arrow.lineTo(w*.51f,h*.32f);
   arrow.lineTo(w*.69f,h*.48f);arrow.close();c.drawPath(arrow,p);
  }else if(kind==2){
   p.setColor(0xD6121627);c.drawRoundRect(w*.25f,h*.25f,w*.75f,h*.75f,62*unit,62*unit,p);
   p.setColor(main);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(25*unit);
   c.drawCircle(cx,cy,w*.18f,p);p.setStyle(Paint.Style.FILL);
  }else if(kind==3){
   c.drawRoundRect(w*.05f,h*.21f,w*.95f,h*.72f,70*unit,70*unit,p);
   Path tail=new Path();tail.moveTo(w*.45f,h*.71f);tail.lineTo(w*.30f,h*.91f);
   tail.lineTo(w*.62f,h*.71f);tail.close();c.drawPath(tail,p);
  }else if(kind==4){
   c.drawRoundRect(w*.11f,h*.30f,w*.89f,h*.70f,90*unit,90*unit,p);
  }else if(kind==6){
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(32*unit);
   c.drawCircle(cx,cy,Math.min(w,h)*.32f,p);p.setStyle(Paint.Style.FILL);
  }else if(kind==7){
   p.setColor(0xC9151B30);c.drawRoundRect(w*.07f,h*.22f,w*.93f,h*.78f,35*unit,35*unit,p);
   p.setColor(main);c.drawRect(w*.49f,h*.27f,w*.51f,h*.73f,p);
   c.drawRoundRect(w*.13f,h*.32f,w*.43f,h*.64f,20*unit,20*unit,p);
   c.drawRoundRect(w*.57f,h*.37f,w*.86f,h*.69f,20*unit,20*unit,p);
  }
  if(kind!=1&&kind!=6){
   p.setTextAlign(Paint.Align.CENTER);
   p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
   float font=Math.min(h*.15f,w*.12f);
   p.setTextSize(font);
   while(font>18&&p.measureText(words)>w*.78f){font*=.91f;p.setTextSize(font);}
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5*unit);p.setColor(0xE8101520);
   float baseline=cy+font*.3f;c.drawText(words,cx,baseline,p);
   p.setStyle(Paint.Style.FILL);p.setColor(kind==3||kind==4?0xFF151629:main);
   c.drawText(words,cx,baseline,p);
  }
  preview.setImageBitmap(bitmap);
  status.setText("Offline PNG generated: "+w+"×"+h+". Save to Gallery or insert into a storyboard slot.");
 }
 private void saveImage(){
  if(bitmap==null){status.setText("Create an asset first.");return;}
  try{
   ContentValues cv=new ContentValues();
   cv.put(MediaStore.Images.Media.DISPLAY_NAME,"CineZehn_PNG_"+System.currentTimeMillis()+".png");
   cv.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
   cv.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/CineZehn/Assets");
   cv.put(MediaStore.Images.Media.IS_PENDING,1);
   Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
   if(uri==null)throw new IOException("Gallery insert failed");
   try(OutputStream out=getContentResolver().openOutputStream(uri)){
    if(out==null)throw new IOException("Cannot write asset");
    if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG compression failed");
   }catch(Exception e){getContentResolver().delete(uri,null,null);throw e;}
   cv.clear();cv.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,cv,null,null);
   saved=uri;status.setText("Saved actual PNG asset to Pictures/CineZehn/Assets.");
  }catch(Exception e){status.setText("PNG export failed: "+e.getMessage());}
 }
 private void returnImage(){
  if(saved==null){saveImage();if(saved==null)return;}
  Intent response=new Intent();response.setData(saved);
  response.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  setResult(RESULT_OK,response);finish();
 }
 @Override protected void onDestroy(){super.onDestroy();}
}
