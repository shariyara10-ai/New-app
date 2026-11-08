package com.cinezehn.pro

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Offline UI served from app assets; imports use Android's Storage Access Framework. */
class MainActivity : ComponentActivity() {
 private lateinit var browser: WebView
 private var chooser: ValueCallback<Array<Uri>>? = null
 private val saves = ConcurrentHashMap<String, Pair<Uri, OutputStream>>()
 private val pickFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  val intent = result.data
  val paths: Array<Uri>? = if(result.resultCode == Activity.RESULT_OK) {
   val clip = intent?.clipData
   when {
    clip != null -> Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
    intent?.data != null -> arrayOf(intent.data!!)
    else -> null
   }
  } else null
  chooser?.onReceiveValue(paths)
  chooser = null
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor = android.graphics.Color.parseColor("#111118")
  window.navigationBarColor = android.graphics.Color.parseColor("#111118")
  browser=WebView(this)
  browser.layoutParams=FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
  setContentView(browser)
  browser.settings.javaScriptEnabled = true
  browser.settings.domStorageEnabled = true
  browser.settings.allowFileAccess = false
  browser.settings.allowContentAccess = true
  browser.settings.mediaPlaybackRequiresUserGesture = true
  browser.settings.setSupportMultipleWindows(false)
  browser.settings.useWideViewPort = true
  browser.settings.loadWithOverviewMode = true
  val assetLoader = WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(this)).build()
  browser.webViewClient = object : WebViewClientCompat() {
   override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
    return assetLoader.shouldInterceptRequest(request.url)
   }
   override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
    val url=request.url.toString()
    if(url.startsWith("https://appassets.androidplatform.net/assets/")) return false
    if(request.isForMainFrame) {startActivity(Intent(Intent.ACTION_VIEW,request.url));return true}
    return false
   }
  }
  browser.webChromeClient = object : WebChromeClient() {
   override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
    chooser?.onReceiveValue(null)
    chooser=filePathCallback
    val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
     addCategory(Intent.CATEGORY_OPENABLE)
     type="*/*"
     putExtra(Intent.EXTRA_ALLOW_MULTIPLE,fileChooserParams?.mode==FileChooserParams.MODE_OPEN_MULTIPLE)
     addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
     val acc=fileChooserParams?.acceptTypes?.flatMap { it.split(',') }?.filter { it.isNotBlank() }?.toTypedArray()
     if(!acc.isNullOrEmpty())putExtra(Intent.EXTRA_MIME_TYPES,acc)
    }
    return try {pickFiles.launch(i);true} catch(e:Exception){chooser?.onReceiveValue(null);chooser=null;Toast.makeText(this@MainActivity,e.message,Toast.LENGTH_LONG).show();false}
   }
  }
  // Only trusted bundled UI should call this interface; top-level external navigation is blocked.
  browser.addJavascriptInterface(object {
   @JavascriptInterface fun beginSave(filename: String, mime: String): String {
    return try {
     val safe=filename.replace(Regex("[^a-zA-Z0-9_.-]"),"_").take(120)
     val values=ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME,safe)
      put(MediaStore.MediaColumns.MIME_TYPE,mime)
      put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CineZehn")
      put(MediaStore.MediaColumns.IS_PENDING,1)
     }
     val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:return ""
     val stream=contentResolver.openOutputStream(uri)?:return ""
     val key=UUID.randomUUID().toString()
     saves[key]=Pair(uri,stream)
     key
    } catch(e:Exception){""}
   }
   @JavascriptInterface fun appendSave(key:String,data:String):Boolean {
    return try {
     val item=saves[key]?:return false
     item.second.write(Base64.decode(data,Base64.DEFAULT))
     true
    } catch(e:Exception){false}
   }
   @JavascriptInterface fun endSave(key:String,success:Boolean) {
    val item=saves.remove(key)?:return
    try {item.second.flush();item.second.close()} catch(_:Exception) {}
    if(success){
     val values=ContentValues().apply {put(MediaStore.MediaColumns.IS_PENDING,0)}
     contentResolver.update(item.first,values,null,null)
     runOnUiThread {Toast.makeText(this@MainActivity,"Saved to Downloads/CineZehn",Toast.LENGTH_LONG).show()}
    } else contentResolver.delete(item.first,null,null)
   }
  },"CineZehnNative")
  if(savedInstanceState==null)browser.loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
  else browser.restoreState(savedInstanceState)
 }
 override fun onSaveInstanceState(outState:Bundle){browser.saveState(outState);super.onSaveInstanceState(outState)}
 @Deprecated("Legacy back handler for the embedded editor")
 override fun onBackPressed(){if(browser.canGoBack())browser.goBack()else super.onBackPressed()}
 override fun onDestroy(){chooser?.onReceiveValue(null);for((_,entry) in saves){try{entry.second.close()}catch(_:Exception){}};saves.clear();browser.destroy();super.onDestroy()}
}
