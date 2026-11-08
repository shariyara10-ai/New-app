#include <jni.h>
#include <string>
#include <algorithm>
#include "whisper.h"
extern "C"
JNIEXPORT jstring JNICALL Java_com_shari_autocut_WhisperBridge_transcribeNative(
    JNIEnv* env,jclass,jstring modelPath,jfloatArray audio,jstring language,jint threads) {
  if(!modelPath||!audio||!language)return env->NewStringUTF("ERROR\tMissing model/audio/language");
  const char* path=env->GetStringUTFChars(modelPath,nullptr);
  const char* lang=env->GetStringUTFChars(language,nullptr);
  whisper_context_params cp=whisper_context_default_params();cp.use_gpu=false;
  whisper_context* ctx=whisper_init_from_file_with_params(path,cp);
  env->ReleaseStringUTFChars(modelPath,path);
  if(!ctx){env->ReleaseStringUTFChars(language,lang);
    return env->NewStringUTF("ERROR\tCould not load Whisper GGML model");}
  whisper_full_params p=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
  p.n_threads=std::max(1,std::min(4,(int)threads));
  p.language=lang; // Whisper understands explicit "auto" multilingual detection mode
  p.translate=false;p.print_progress=false;p.print_realtime=false;
  p.print_timestamps=false;p.print_special=false;p.no_context=true;
  jsize count=env->GetArrayLength(audio);
  jfloat* samples=env->GetFloatArrayElements(audio,nullptr);
  std::string result;
  if(!samples||count<1600)result="ERROR\tInsufficient 16 kHz samples";
  else if(whisper_full(ctx,p,samples,count)!=0)result="ERROR\tWhisper inference failed";
  else {
    int size=whisper_full_n_segments(ctx);
    for(int i=0;i<size;i++){
      long a=whisper_full_get_segment_t0(ctx,i)*10;
      long b=whisper_full_get_segment_t1(ctx,i)*10;
      const char* txt=whisper_full_get_segment_text(ctx,i);
      result+=std::to_string(a)+"\t"+std::to_string(b)+"\t";
      if(txt)for(const char* q=txt;*q;q++){char c=*q;result+=(c=='\n'||c=='\r'||c=='\t')?' ':c;}
      result+="\n";
    }
  }
  if(samples)env->ReleaseFloatArrayElements(audio,samples,JNI_ABORT);
  whisper_free(ctx);env->ReleaseStringUTFChars(language,lang);
  return env->NewStringUTF(result.c_str());
}
