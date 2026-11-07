package com.shari.autocut;
import android.net.Uri;
import java.util.ArrayList;

/** Non-destructive editing state for one user-selected video source. */
public final class FlowScene {
 public final Uri uri;
 public final String fileName;
 public final long durationMs;
 public final boolean hasAudio;
 public long trimStartMs=0;
 public long trimEndMs;
 public String caption="";
 public final ArrayList<CaptionSegment> subtitles=new ArrayList<>();
 public FlowScene(Uri uri,String fileName,long durationMs,boolean hasAudio){
  this.uri=uri;this.fileName=fileName;this.durationMs=durationMs;this.hasAudio=hasAudio;
  this.trimEndMs=durationMs;
 }
 public long editingDurationMs(){return Math.max(100,trimEndMs-trimStartMs);}
 public FlowScene copyWithEnd(long newEnd){
  FlowScene other=new FlowScene(uri,fileName,durationMs,hasAudio);
  other.trimStartMs=trimStartMs;other.trimEndMs=Math.min(trimEndMs,newEnd);
  other.caption=caption;other.subtitles.addAll(subtitles);return other;
 }
}