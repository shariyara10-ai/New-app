package com.shari.autocut;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.*;

public class ModelImporter {
 public interface Progress { void update(int percent); }
 private static final long MAX_ARCHIVE=150L*1024L*1024L;
 private static final long MAX_UNPACKED=650L*1024L*1024L;
 private static final String MODEL_URL="https://alphacephei.com/vosk/models/";
 private static final String MIRROR_URL="https://huggingface.co/localstack/vosk-models/resolve/main/";
 private static final String EN_US_SHA="30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498";
 private static final String EN_IN_SHA="20663dcac4d5cb783a579c54d98339344a688e4ec6e1b4a4b059fd1235454cc7";

 public static File downloadAndInstall(Context ctx,String language,Progress progress)throws Exception{
  final String filename;
  final String expectedSha;
  if("hindi".equals(language)){filename="vosk-model-small-hi-0.22.zip";expectedSha=null;}
  else if("indian_english".equals(language)){filename="vosk-model-small-en-in-0.4.zip";expectedSha=EN_IN_SHA;}
  else{filename="vosk-model-small-en-us-0.15.zip";expectedSha=EN_US_SHA;}
  String[] hosts=new String[]{MODEL_URL+filename,MIRROR_URL+filename};
  IOException last=null;
  for(int attempt=0;attempt<hosts.length;attempt++){
   File cache=new File(ctx.getCacheDir(),"vosk-download-"+System.currentTimeMillis()+"-"+attempt+".zip");
   HttpURLConnection connection=null;
   try{
    if(progress!=null)progress.update(0);
    URL address=new URL(hosts[attempt]);
    connection=(HttpURLConnection)address.openConnection();
    connection.setConnectTimeout(12000);
    connection.setReadTimeout(45000);
    connection.setInstanceFollowRedirects(true);
    connection.setRequestProperty("User-Agent","CineZehnAIStudio/0.4.2");
    connection.setRequestProperty("Accept","application/octet-stream,application/zip,*/*");
    connection.connect();
    int code=connection.getResponseCode();
    if(code!=200)throw new IOException("Model source "+(attempt+1)+" returned HTTP "+code);
    long length=connection.getContentLengthLong();
    if(length>MAX_ARCHIVE)throw new IOException("Model exceeds supported archive size.");
    long got=0;int lastProgress=-1;
    MessageDigest digest=MessageDigest.getInstance("SHA-256");
    try(InputStream in=new BufferedInputStream(connection.getInputStream());
        OutputStream out=new BufferedOutputStream(new FileOutputStream(cache))){
     byte[] data=new byte[65536];int n;
     while((n=in.read(data))!=-1){
      got+=n;
      if(got>MAX_ARCHIVE)throw new IOException("Download exceeded safe 150MB limit.");
      digest.update(data,0,n);out.write(data,0,n);
      if(length>0&&progress!=null){
       int p=(int)Math.min(99,100d*got/length);
       if(p>=lastProgress+5){lastProgress=p;progress.update(p);}
      }
     }
    }
    if(got<5L*1024*1024)throw new IOException("Received non-model or incomplete ZIP; file too small.");
    if(length>0&&got!=length)throw new IOException("Incomplete ZIP download.");
    String sha=toHex(digest.digest());
    if(expectedSha!=null&&!expectedSha.equalsIgnoreCase(sha))
      throw new IOException("Model SHA-256 check failed; refused untrusted or damaged download.");
    File valid=installFile(ctx,cache);
    if(progress!=null)progress.update(100);
    return valid;
   }catch(IOException e){
    last=e;
    android.util.Log.w("CineZehn","Model source "+(attempt+1)+" failed: "+e.getClass().getSimpleName());
   }finally{
    if(connection!=null)connection.disconnect();
    cache.delete();
   }
  }
  throw new IOException("Both model servers failed ("+(last==null?"unknown":last.getClass().getSimpleName())+
   "). Check Wi-Fi/mobile data, Private DNS or VPN. Or download the original model ZIP in Chrome from huggingface.co/localstack/vosk-models and tap Import ZIP.");
 }
 private static String toHex(byte[] bytes) {
  StringBuilder sb=new StringBuilder();
  for(byte b:bytes)sb.append(String.format(Locale.US,"%02x",b&255));
  return sb.toString();
 }
 public static File install(Context context,Uri zipUri) throws Exception {
  InputStream file=context.getContentResolver().openInputStream(zipUri);
  if(file==null)throw new IOException("Selected ZIP could not be opened. Choose a file from Downloads.");
  try(InputStream in=file){return installStream(context,in);}
 }
 private static File installFile(Context context,File file) throws Exception{
  try(InputStream in=new FileInputStream(file)){return installStream(context,in);}
 }
 private static File installStream(Context context,InputStream input) throws Exception{
  File root=new File(context.getFilesDir(),"vosk-models");
  if(!root.exists()&&!root.mkdirs())throw new IOException("Cannot prepare model folder.");
  File stage=new File(root,"installing");
  delete(stage);
  if(!stage.mkdirs())throw new IOException("Cannot create temporary model folder.");
  long uncompressed=0;int count=0;boolean foundZip=false;
  try(BufferedInputStream inputBuffer=new BufferedInputStream(input)) {
   inputBuffer.mark(4);
   int b1=inputBuffer.read(),b2=inputBuffer.read();
   inputBuffer.reset();
   if(b1!='P'||b2!='K')throw new IOException("Selected file is not a ZIP. Download the actual Vosk small model .zip, not the web page.");
   try(ZipInputStream zin=new ZipInputStream(inputBuffer)) {
    ZipEntry entry;byte[] buffer=new byte[32768];
    while((entry=zin.getNextEntry())!=null){
     foundZip=true;
     if(++count>4000)throw new IOException("Too many files in model archive.");
     String name=entry.getName().replace('\\','/');
     if(name.startsWith("/")||name.startsWith("../")||name.contains("/../"))
      throw new IOException("Unsafe ZIP directory path.");
     File target=new File(stage,name);
     if(!target.getCanonicalPath().startsWith(stage.getCanonicalPath()+File.separator))
      throw new IOException("Unsafe model ZIP path.");
     if(entry.isDirectory()){
      if(!target.exists()&&!target.mkdirs())throw new IOException("Cannot create directory.");
     }else{
      File parent=target.getParentFile();
      if(!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create model subdirectory.");
      try(FileOutputStream out=new FileOutputStream(target)){
       int n;
       while((n=zin.read(buffer))!=-1) {
        uncompressed+=n;
        if(uncompressed>MAX_UNPACKED)throw new IOException("Unpacked model exceeds 650MB; choose a small model.");
        out.write(buffer,0,n);
       }
      }
     }
     zin.closeEntry();
    }
   }
  }catch(ZipException e){
   delete(stage);
   throw new IOException("Model ZIP is damaged or incomplete (decompression failed). Re-download the original ZIP on stable Wi-Fi; do not unzip/recompress it.",e);
  }catch(Exception e){
   delete(stage);throw e;
  }
  if(!foundZip){delete(stage);throw new IOException("ZIP contains no model files. Download the original official ZIP.");}
  File valid=findModel(stage,3);
  if(valid==null){delete(stage);throw new IOException("ZIP extracted, but it is not a valid Vosk recognition model (am/ and conf/ missing).");}
  File active=new File(root,"active");
  File backup=new File(root,"previous");
  delete(backup);
  if(active.exists()&&!active.renameTo(backup)){delete(stage);throw new IOException("Cannot back up existing model.");}
  if(!stage.renameTo(active)){
   if(backup.exists())backup.renameTo(active);
   delete(stage);throw new IOException("Cannot activate new model.");
  }
  delete(backup);
  File selected=findModel(active,3);
  if(selected==null)throw new IOException("Model install failed final check.");
  return selected;
 }
 public static File active(Context c){return findModel(new File(c.getFilesDir(),"vosk-models/active"),3);}
 private static File findModel(File f,int depth){
  if(f==null||!f.exists()||depth<0)return null;
  if(new File(f,"am").isDirectory()&&new File(f,"conf").isDirectory())return f;
  File[] children=f.listFiles();
  if(children!=null)for(File child:children)if(child.isDirectory()){
   File match=findModel(child,depth-1);if(match!=null)return match;
  }
  return null;
 }
 private static void delete(File f){
  if(f==null||!f.exists())return;
  if(f.isDirectory()){
   File[] children=f.listFiles();
   if(children!=null)for(File c:children)delete(c);
  }
  f.delete();
 }
}
