package com.cupcake.ui.component

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VrmViewer(
    modifier: Modifier = Modifier,
    isGenerating: Boolean = false,
    emotion: String = "neutral",
    isWalking: Boolean = false,
    isDancing: Boolean = false,
    audioBeat: Float = 0f
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?
                ): android.webkit.WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    if (url.contains("marin.vrm")) {
                        try {
                            val stream = context.assets.open("marin/marin.vrm")
                            return android.webkit.WebResourceResponse(
                                "application/octet-stream",
                                "UTF-8",
                                stream
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("VrmViewer", "Failed to stream marin.vrm from assets", e)
                        }
                    } else if (url.contains("android_asset/")) {
                        val path = url.substringAfter("android_asset/")
                        try {
                            val stream = context.assets.open(path)
                            val mime = when {
                                path.endsWith(".js") -> "application/javascript"
                                path.endsWith(".html") -> "text/html"
                                path.endsWith(".png") -> "image/png"
                                path.endsWith(".gif") -> "image/gif"
                                path.endsWith(".vrm") -> "application/octet-stream"
                                else -> "application/octet-stream"
                            }
                            return android.webkit.WebResourceResponse(mime, "UTF-8", stream)
                        } catch (e: Exception) {
                            android.util.Log.e("VrmViewer", "Failed to stream asset: $path", e)
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            loadUrl("file:///android_asset/vrm/vrm_viewer.html")
        }
    }

    // React to streaming / speech
    LaunchedEffect(isGenerating) {
        webView.evaluateJavascript("window.setTalking && window.setTalking($isGenerating);", null)
    }

    // React to emotion changes
    LaunchedEffect(emotion) {
        val exp = when (emotion.lowercase()) {
            "flirty", "wink" -> "flirty"
            "lovely" -> "lovely"
            "excited" -> "excited"
            "pout", "jealous" -> "pout"
            "happy", "laugh" -> "happy"
            "angry" -> "angry"
            "sad", "sorrow" -> "sad"
            "relaxed", "confident", "thinking" -> "relaxed"
            "surprised", "shock" -> "surprised"
            else -> "neutral"
        }
        webView.evaluateJavascript("window.setExpression && window.setExpression('$exp', 1.0);", null)
    }

    // React to walking animation
    LaunchedEffect(isWalking) {
        webView.evaluateJavascript("window.setWalking && window.setWalking($isWalking);", null)
    }

    // React to dancing animation
    LaunchedEffect(isDancing) {
        webView.evaluateJavascript("window.setDancing && window.setDancing($isDancing);", null)
    }

    // React to audio beats / music motions
    LaunchedEffect(audioBeat) {
        webView.evaluateJavascript("window.setAudioReactive && window.setAudioReactive($audioBeat);", null)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
