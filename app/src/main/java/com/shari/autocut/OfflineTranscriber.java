package com.shari.autocut;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public final class OfflineTranscriber {
 public interface Progress {void update(int percentage);}
 private static final int INPUT_TIMEOUT_US=10000;
 private static class Word {
  String text;long start,end;
  Word(String t,long a,long b){text=t;start=a;end=b;}
 }
 public static ArrayList<CaptionSegment> transcribe(Context ctx,Uri media,File modelDir,Progress progress) throws Exception {
  ArrayList<Word> words=new ArrayList<>();
  try(Model model=new Model(modelDir.getAbsolutePath());
      Recognizer rec=new Recognizer(model,16000f)) {
   rec.setWords(true);
   decodeAudio(ctx,media,rec,words,progress);
   append(rec.getFinalResult(),words);
  }
  ArrayList<CaptionSegment> out=new ArrayList<>();
  StringBuilder phrase=new StringBuilder();long start=0,end=0;int count=0;
  for(Word w:words){
   if(count>0 && (count>=6 || w.end-start>2600 || w.start-end>550)){
    out.add(new CaptionSegment(start,end,phrase.toString(),"pop"));phrase.setLength(0);count=0;
   }
   if(count==0)start=w.start;
   else phrase.append(" ");
   phrase.append(w.text);end=w.end;count++;
  }
  if(count>0)out.add(new CaptionSegment(start,end,phrase.toString(),"pop"));
  return out;
 }
 private static void append(String raw,List<Word> out) {
  try {
   JSONObject obj=new JSONObject(raw);
   JSONArray a=obj.optJSONArray("result");
   if(a==null)return;
   for(int i=0;i<a.length();i++){
    JSONObject w=a.getJSONObject(i);
    String word=w.optString("word","").trim();
    if(!word.isEmpty())out.add(new Word(word,Math.max(0,Math.round(w.optDouble("start",0)*1000)),Math.max(0,Math.round(w.optDouble("end",0)*1000))));
   }
  }catch(Exception ignore){}
 }
 private static void decodeAudio(Context ctx,Uri uri,Recognizer rec,List<Word> words,Progress listener) throws Exception {
  MediaExtractor extractor=new MediaExtractor();
  MediaCodec decoder=null;
  try{
   extractor.setDataSource(ctx,uri,null);
   int audioTrack=-1;
   for(int i=0;i<extractor.getTrackCount();i++){
    MediaFormat f=extractor.getTrackFormat(i);
    String mime=f.getString(MediaFormat.KEY_MIME);
    if(mime!=null && mime.startsWith("audio/")){audioTrack=i;break;}
   }
   if(audioTrack<0)throw new IllegalArgumentException("This media does not contain a supported audio track.");
   extractor.selectTrack(audioTrack);
   MediaFormat inputFormat=extractor.getTrackFormat(audioTrack);
   String mime=inputFormat.getString(MediaFormat.KEY_MIME);
   if(mime==null)throw new IllegalArgumentException("Unsupported audio format");
   long durationUs=inputFormat.containsKey(MediaFormat.KEY_DURATION)?inputFormat.getLong(MediaFormat.KEY_DURATION):0;
   decoder=MediaCodec.createDecoderByType(mime);
   decoder.configure(inputFormat,null,null,0);
   decoder.start();
   MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
   boolean inputDone=false,outputDone=false;
   int sampleRate=inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)?inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE):44100;
   int channels=inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1;
   double position=0, totalFrames=0;
   byte[] pcm=new byte[16*1024];int filled=0;
   while(!outputDone) {
    if(!inputDone) {
     int index=decoder.dequeueInputBuffer(INPUT_TIMEOUT_US);
     if(index>=0) {
      ByteBuffer data=decoder.getInputBuffer(index);
      if(data==null)throw new IllegalStateException("Cannot access decoder input");
      data.clear();
      int n=extractor.readSampleData(data,0);
      if(n<0){decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
      else {long pts=extractor.getSampleTime();decoder.queueInputBuffer(index,0,n,Math.max(0,pts),0);extractor.advance();}
     }
    }
    int outputIndex=decoder.dequeueOutputBuffer(info,INPUT_TIMEOUT_US);
    if(outputIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
     MediaFormat f=decoder.getOutputFormat();
     if(f.containsKey(MediaFormat.KEY_SAMPLE_RATE))sampleRate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
     if(f.containsKey(MediaFormat.KEY_CHANNEL_COUNT))channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
     if(f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING)!=2)
      throw new IllegalArgumentException("Decoder produced non-16-bit PCM; this audio format needs conversion first.");
    } else if(outputIndex>=0) {
     if(info.size>0){
      ByteBuffer output=decoder.getOutputBuffer(outputIndex);
      if(output!=null) {
       output.order(ByteOrder.LITTLE_ENDIAN);
       output.position(info.offset);output.limit(info.offset+info.size);
       int frames=output.remaining()/(2*channels);
       short[] mono=new short[frames];
       for(int i=0;i<frames;i++){
        int value=0;
        for(int ch=0;ch<channels;ch++)value+=output.getShort();
        mono[i]=(short)(value/channels);
       }
       double step=sampleRate/16000d;
       while(position<totalFrames+frames){
        int source=(int)Math.min(frames-1,Math.max(0,Math.floor(position-totalFrames)));
        short s=mono[source];
        if(filled+2>pcm.length){
         if(rec.acceptWaveForm(pcm,filled))append(rec.getResult(),words);
         filled=0;
        }
        pcm[filled++]=(byte)(s&255);
        pcm[filled++]=(byte)((s>>>8)&255);
        position+=step;
       }
       totalFrames+=frames;
      }
     }
     decoder.releaseOutputBuffer(outputIndex,false);
     if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)outputDone=true;
     if(durationUs>0 && listener!=null)listener.update((int)Math.min(99,Math.max(0,100d*info.presentationTimeUs/durationUs)));
    }
   }
   if(filled>0 && rec.acceptWaveForm(pcm,filled))append(rec.getResult(),words);
  }finally{
   if(decoder!=null){try{decoder.stop();}catch(Exception ignore){}decoder.release();}
   extractor.release();
  }
 }
}
