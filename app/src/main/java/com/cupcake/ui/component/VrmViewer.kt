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
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    android.util.Log.i(
                        "VrmViewerConsole",
                        "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})"
                    )
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?
                ): android.webkit.WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    return handleVrmAsset(context, url) ?: super.shouldInterceptRequest(view, request)
                }
            }
            loadUrl("https://cupcake.local/assets/vrm/vrm_viewer.html")
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

fun handleVrmAsset(context: android.content.Context, url: String): android.webkit.WebResourceResponse? {
    val cleanPath = when {
        url.contains("cupcake.local/assets/") -> url.substringAfter("cupcake.local/assets/")
        url.contains("cupcake.local/") -> url.substringAfter("cupcake.local/")
        url.contains("android_asset/") -> url.substringAfter("android_asset/")
        url.contains("marin.vrm") -> "marin/marin.vrm"
        else -> return null
    }.substringBefore("?").removePrefix("/")

    return try {
        val stream = context.assets.open(cleanPath)
        val mimeType = when {
            cleanPath.endsWith(".html") -> "text/html"
            cleanPath.endsWith(".js") -> "application/javascript"
            cleanPath.endsWith(".css") -> "text/css"
            cleanPath.endsWith(".json") -> "application/json"
            cleanPath.endsWith(".png") -> "image/png"
            cleanPath.endsWith(".gif") -> "image/gif"
            cleanPath.endsWith(".vrm") || cleanPath.endsWith(".glb") -> "model/gltf-binary"
            else -> "application/octet-stream"
        }
        val encoding = if (mimeType.startsWith("text/") || mimeType == "application/javascript" || mimeType == "application/json") "UTF-8" else null
        val response = android.webkit.WebResourceResponse(mimeType, encoding, stream)
        response.responseHeaders = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, OPTIONS, HEAD",
            "Access-Control-Allow-Headers" to "*"
        )
        response
    } catch (e: Exception) {
        android.util.Log.e("VrmViewer", "Failed to stream asset: $cleanPath (url: $url)", e)
        null
    }
}
