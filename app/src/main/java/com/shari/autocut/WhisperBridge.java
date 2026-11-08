package com.shari.autocut;
public final class WhisperBridge {
 public static final boolean INSTALLED;
 static {
  boolean ok=false;
  try{System.loadLibrary("cinezehn_whisper");ok=true;}
  catch(UnsatisfiedLinkError e){android.util.Log.e("CineZehnWhisper","Native library not available",e);}
  INSTALLED=ok;
 }
 private WhisperBridge(){}
 public static native String transcribeNative(String model,float[] samples,String language,int threads);
}
