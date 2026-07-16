package com.futo.futopay.views

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.RelativeLayout
import com.futo.futopay.R

class PaymentWebView : RelativeLayout {
    private var _completed = false
    private val successUrlPrefix: String
    private val webView: WebView
    private val progressBar: ProgressBar
    private var successCallback: ((String) -> Unit)? = null

    constructor(context: Context, successUrlPrefix: String, onSuccess: (String) -> Unit) : super(context) {
        this.successUrlPrefix = successUrlPrefix
        this.successCallback = onSuccess

        inflate(context, R.layout.payment_webview, this)

        val minHeightPx = (resources.displayMetrics.heightPixels * 0.7f).toInt()
        minimumHeight = minHeightPx

        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progress_bar)
        progressBar.visibility = View.VISIBLE

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    android.util.Log.d("PolarWebView", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                checkUrl(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                checkUrl(url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val urlStr = request?.url?.toString()
                return checkUrl(urlStr)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return checkUrl(url)
            }
        }
    }

    fun loadCheckoutUrl(url: String) {
        progressBar.visibility = View.VISIBLE
        webView.loadUrl(url)
    }

    private fun checkUrl(url: String?): Boolean {
        if (!_completed && url != null && url.startsWith(successUrlPrefix)) {
            _completed = true
            progressBar.visibility = View.GONE
            successCallback?.invoke(url)
            return true
        }
        return false
    }
}
