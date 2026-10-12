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
    emotion: String = "neutral"
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
            webViewClient = object : WebViewClient() {}
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
            "happy", "laugh", "excited", "lovely" -> "happy"
            "angry" -> "angry"
            "sad" -> "sad"
            "relaxed", "confident" -> "relaxed"
            "surprised", "shock" -> "surprised"
            else -> "neutral"
        }
        webView.evaluateJavascript("window.setExpression && window.setExpression('$exp', 1.0);", null)
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
