package com.shari.autocut;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;

public final class WhisperModelStore {
 private WhisperModelStore(){}
 private static File modelDir(Context c){return new File(c.getFilesDir(),"whisper");}
 public static File current(Context c){
  File m=new File(modelDir(c),"current-ggml.bin");
  return m.isFile()&&m.length()>10000000?m:null;
 }
 public interface Progress{void update(int percent);}
 /** Optional first-run direct download of the official multilingual tiny model. */
 public static File downloadTiny(Context c,Progress listener)throws Exception{
  final String url="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin";
  final String sha="be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21";
  File dir=modelDir(c);if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create model folder");
  File temp=new File(dir,"download-tiny.bin");File dest=new File(dir,"current-ggml.bin");
  HttpURLConnection conn=null;
  try{
   conn=(HttpURLConnection)new URL(url).openConnection();
   conn.setConnectTimeout(18000);conn.setReadTimeout(60000);
   conn.setInstanceFollowRedirects(true);
   conn.setRequestProperty("User-Agent","CineZehn-Whisper/0.8");
   if(conn.getResponseCode()!=200)throw new IOException("Model download HTTP "+conn.getResponseCode());
   long length=conn.getContentLengthLong();
   if(length>160L*1024*1024)throw new IOException("Unexpected model size.");
   MessageDigest digest=MessageDigest.getInstance("SHA-256");
   long total=0;
   try(InputStream in=new BufferedInputStream(conn.getInputStream());
       OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
    byte[] data=new byte[65536];int count;
    while((count=in.read(data))!=-1){
     total+=count;if(total>160L*1024*1024)throw new IOException("Model exceeded expected size.");
     digest.update(data,0,count);out.write(data,0,count);
     if(listener!=null&&length>0)listener.update((int)Math.min(99,100.0*total/length));
    }
   }
   if(total<50L*1024*1024)throw new IOException("Downloaded model too small.");
   if(length>0&&total!=length)throw new IOException("Incomplete model download.");
   StringBuilder digestHex=new StringBuilder();
   for(byte b:digest.digest())digestHex.append(String.format(java.util.Locale.US,"%02x",b&255));
   if(!sha.equalsIgnoreCase(digestHex.toString()))
    throw new IOException("Downloaded model did not pass official SHA-256 verification.");
   replaceSafely(temp,dest);
   if(listener!=null)listener.update(100);
   return dest;
  }catch(Exception e){temp.delete();throw new IOException(
   "Automatic model download failed: "+e.getMessage()+
   ". Alternative: download ggml-tiny.bin in Chrome and tap Import Model.",e);
  }finally{if(conn!=null)conn.disconnect();}
 }
 private static void replaceSafely(File staging,File active)throws IOException{
  File backup=new File(active.getParentFile(),"backup-model.bin");
  if(backup.exists())backup.delete();
  if(active.exists()&&!active.renameTo(backup))throw new IOException("Unable to back up existing model.");
  if(!staging.renameTo(active)){
   if(backup.exists())backup.renameTo(active);
   throw new IOException("Unable to install new model.");
  }
  backup.delete();
 }
 public static File importBin(Context c,Uri file)throws Exception {
  File dir=modelDir(c);
  if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create Whisper model folder");
  File staging=new File(dir,"importing.bin");File dest=new File(dir,"current-ggml.bin");
  long amount=0;
  try(InputStream in=c.getContentResolver().openInputStream(file);
      OutputStream out=new BufferedOutputStream(new FileOutputStream(staging))){
   if(in==null)throw new IOException("Cannot open selected GGML model file");
   byte[] buffer=new byte[65536];int n;
   while((n=in.read(buffer))!=-1){
    amount+=n;if(amount>1200L*1024*1024)throw new IOException("Model exceeds the 1.2 GB safety limit");
    out.write(buffer,0,n);
   }
  }catch(Exception e){staging.delete();throw e;}
  if(amount<10000000){staging.delete();throw new IOException("File is too small. Choose a real ggml-*.bin Whisper model.");}
  try{replaceSafely(staging,dest);}catch(IOException e){staging.delete();throw e;}
  return dest;
 }
}
