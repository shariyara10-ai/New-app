package com.shari.autocut;
import android.content.Context;
import android.net.Uri;
import java.io.*;

public final class WhisperModelStore {
 private WhisperModelStore(){}
 private static File modelDir(Context c){return new File(c.getFilesDir(),"whisper");}
 public static File current(Context c){
  File m=new File(modelDir(c),"current-ggml.bin");
  return m.isFile()&&m.length()>10000000?m:null;
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
  if(dest.exists()&&!dest.delete()){staging.delete();throw new IOException("Cannot replace previous model");}
  if(!staging.renameTo(dest)){staging.delete();throw new IOException("Cannot activate model");}
  return dest;
 }
}
