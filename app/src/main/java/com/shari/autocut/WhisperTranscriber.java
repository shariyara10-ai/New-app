package com.shari.autocut;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.*;
import java.util.*;

/**
 * Decode Android supported video/audio into 16 kHz mono float PCM and perform
 * real local whisper.cpp inference in <=28 sec windows with overlap.
 * Memory and duration caps prevent excessive OOM on phone.
 */
public final class WhisperTranscriber {
 public interface Progress{void onProgress(int percent);}
 private static final int RATE=16000;
 private static final int MAX_SAMPLES=RATE*15*60;
 private WhisperTranscriber(){}
 private static final class Floats {
  float[] values=new float[RATE*20];int size=0;
  void add(float value){
   if(size>=MAX_SAMPLES)throw new IllegalArgumentException("Speech over 15 minutes: split into shorter recordings.");
   if(size>=values.length)values=Arrays.copyOf(values,Math.min(MAX_SAMPLES,values.length*2));
   values[size++]=value;
  }
  float[] array(){return Arrays.copyOf(values,size);}
 }
 public static ArrayList<CaptionSegment> transcribe(Context ctx,Uri media,File model,
   String language,Progress callback)throws Exception {
  if(!WhisperBridge.INSTALLED)throw new IOException("Whisper native library is not installed in this APK.");
  if(model==null||!model.isFile()||model.length()<10000000)
   throw new IOException("Import a valid multilingual Whisper GGML .bin model first.");
  float[] pcm=decode16k(ctx,media,p->{
   if(callback!=null)callback.onProgress(Math.min(32,p/3));
  });
  if(pcm.length<RATE/3)throw new IOException("Too little audio for recognition.");
  int chunk=RATE*28,overlap=RATE;
  long lastEnd=0;
  ArrayList<CaptionSegment> result=new ArrayList<>();
  for(int at=0;at<pcm.length;at+=chunk-overlap){
   int until=Math.min(pcm.length,at+chunk);
   if(until-at<RATE/3)break;
   float[] part=Arrays.copyOfRange(pcm,at,until);
   String raw=WhisperBridge.transcribeNative(model.getAbsolutePath(),part,language,4);
   if(raw==null||raw.startsWith("ERROR\t"))
    throw new IOException(raw==null?"No Whisper result":raw.substring(Math.min(6,raw.length())));
   for(String line:raw.split("\\r?\\n")){
    String[] fields=line.split("\\t",3);
    if(fields.length!=3)continue;
    try{
     long a=Long.parseLong(fields[0])+(at*1000L)/RATE;
     long b=Long.parseLong(fields[1])+(at*1000L)/RATE;
     String text=fields[2].trim();
     if(text.isEmpty()||b<=a||b<=lastEnd)continue;
     if(a<lastEnd) a=lastEnd;
     result.add(new CaptionSegment(a,b,text,"pop"));
     lastEnd=b;
    }catch(NumberFormatException ignored){}
   }
   if(callback!=null)callback.onProgress(Math.min(99,33+(int)(66d*until/pcm.length)));
   if(until==pcm.length)break;
  }
  if(callback!=null)callback.onProgress(100);
  return result;
 }
 private static float[] decode16k(Context ctx,Uri uri,Progress cb)throws Exception {
  MediaExtractor extractor=new MediaExtractor();MediaCodec decoder=null;
  Floats pcm=new Floats();
  try{
   extractor.setDataSource(ctx,uri,null);
   int track=-1;
   for(int i=0;i<extractor.getTrackCount();i++){
    MediaFormat f=extractor.getTrackFormat(i);
    String mime=f.getString(MediaFormat.KEY_MIME);
    if(mime!=null&&mime.startsWith("audio/")){track=i;break;}
   }
   if(track<0)throw new IOException("No audio stream in the selected file.");
   extractor.selectTrack(track);
   MediaFormat src=extractor.getTrackFormat(track);
   String mime=src.getString(MediaFormat.KEY_MIME);
   if(mime==null)throw new IOException("Unknown audio codec");
   decoder=MediaCodec.createDecoderByType(mime);
   decoder.configure(src,null,null,0);decoder.start();
   int rate=src.containsKey(MediaFormat.KEY_SAMPLE_RATE)?src.getInteger(MediaFormat.KEY_SAMPLE_RATE):44100;
   int channels=src.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?src.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1;
   long duration=src.containsKey(MediaFormat.KEY_DURATION)?src.getLong(MediaFormat.KEY_DURATION):0;
   MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
   boolean endInput=false,endOutput=false;
   double sourcePos=0,totalDecoded=0;
   while(!endOutput){
    if(!endInput){
     int index=decoder.dequeueInputBuffer(12000);
     if(index>=0){
      ByteBuffer data=decoder.getInputBuffer(index);
      if(data==null)throw new IOException("Audio decoder input unavailable");
      data.clear();int n=extractor.readSampleData(data,0);
      if(n<0){decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);endInput=true;}
      else{decoder.queueInputBuffer(index,0,n,Math.max(0,extractor.getSampleTime()),0);extractor.advance();}
     }
    }
    int index=decoder.dequeueOutputBuffer(info,12000);
    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
     MediaFormat out=decoder.getOutputFormat();
     if(out.containsKey(MediaFormat.KEY_SAMPLE_RATE))rate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE);
     if(out.containsKey(MediaFormat.KEY_CHANNEL_COUNT))channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
     if(out.containsKey(MediaFormat.KEY_PCM_ENCODING)&&out.getInteger(MediaFormat.KEY_PCM_ENCODING)!=2)
      throw new IOException("Audio output is not 16-bit PCM; transcode the file first.");
    }else if(index>=0){
     if(info.size>0){
      ByteBuffer out=decoder.getOutputBuffer(index);
      if(out!=null){
       out.order(ByteOrder.LITTLE_ENDIAN);out.position(info.offset);out.limit(info.offset+info.size);
       int frames=out.remaining()/(Math.max(1,channels)*2);
       float[] mono=new float[frames];
       for(int f=0;f<frames;f++){
        float sum=0;for(int ch=0;ch<channels;ch++)sum+=out.getShort()/32768f;
        mono[f]=sum/channels;
       }
       double step=rate/(double)RATE;
       while(sourcePos<totalDecoded+frames){
        int local=(int)Math.max(0,Math.min(frames-1,Math.floor(sourcePos-totalDecoded)));
        pcm.add(mono[local]);
        sourcePos+=step;
       }
       totalDecoded+=frames;
      }
     }
     decoder.releaseOutputBuffer(index,false);
     if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)endOutput=true;
     if(duration>0&&cb!=null)cb.onProgress((int)Math.min(99,Math.max(0,100.0*info.presentationTimeUs/duration)));
    }
   }
   return pcm.array();
  }finally{
   if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}
   extractor.release();
  }
 }
}
