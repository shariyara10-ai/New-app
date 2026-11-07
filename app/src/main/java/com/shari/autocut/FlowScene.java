package com.shari.autocut;

import android.net.Uri;

public final class FlowScene {
 public final Uri uri;
 public final String fileName;
 public final long durationMs;
 public final boolean hasAudio;
 public String caption = "";
 public FlowScene(Uri uri,String fileName,long durationMs,boolean hasAudio){
  this.uri=uri;this.fileName=fileName;this.durationMs=durationMs;this.hasAudio=hasAudio;
 }
}
