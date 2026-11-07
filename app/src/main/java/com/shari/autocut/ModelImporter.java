package com.shari.autocut;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.zip.*;
public class ModelImporter {
 public static File install(Context context,Uri zipUri) throws Exception {
  File root=new File(context.getFilesDir(),"vosk-models");
  if(!root.exists()&&!root.mkdirs())throw new IOException("Cannot create model folder");
  File dest=new File(root,"active");
  delete(dest);
  if(!dest.mkdirs())throw new IOException("Cannot prepare model folder");
  long size=0;int count=0;
  try(InputStream in=context.getContentResolver().openInputStream(zipUri);ZipInputStream zin=new ZipInputStream(new BufferedInputStream(in))) {
   ZipEntry entry;
   while((entry=zin.getNextEntry())!=null) {
    if(++count>4000)throw new IOException("Too many files in model ZIP");
    String name=entry.getName().replace('\\','/');
    if(name.startsWith("/")||name.contains("../"))throw new IOException("Unsafe path in ZIP");
    File out=new File(dest,name);
    String base=dest.getCanonicalPath()+File.separator;
    if(!out.getCanonicalPath().startsWith(base))throw new IOException("Unsafe ZIP entry");
    if(entry.isDirectory()) {if(!out.exists()&&!out.mkdirs())throw new IOException("Cannot create folder");continue;}
    File parent=out.getParentFile();if(!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create folder");
    try(FileOutputStream fout=new FileOutputStream(out)) {
     byte[] b=new byte[32768];int n;
     while((n=zin.read(b))!=-1) {size+=n;if(size>650L*1024*1024)throw new IOException("Model too large (>650MB)");fout.write(b,0,n);}
    }
   }
  } catch(Exception e) {delete(dest);throw e;}
  File found=findModel(dest,2);
  if(found==null){delete(dest);throw new IOException("This ZIP does not contain a Vosk model (am and conf folders).");}
  return found;
 }
 public static File active(Context c) {return findModel(new File(c.getFilesDir(),"vosk-models/active"),2);}
 private static File findModel(File f,int depth) {
  if(f==null||!f.exists()||depth<0)return null;
  if(new File(f,"am").isDirectory() && new File(f,"conf").isDirectory())return f;
  File[] children=f.listFiles();if(children!=null)for(File child:children)if(child.isDirectory()){File match=findModel(child,depth-1);if(match!=null)return match;}
  return null;
 }
 private static void delete(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] cs=f.listFiles();if(cs!=null)for(File c:cs)delete(c);}f.delete();}
}
