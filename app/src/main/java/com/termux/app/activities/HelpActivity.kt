package com.termux.app.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.termux.shared.termux.TermuxConstants

class HelpActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val progressLayout = RelativeLayout(this)
        val lParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lParams.addRule(RelativeLayout.CENTER_IN_PARENT)
        val progressBar = ProgressBar(this)
        progressBar.isIndeterminate = true
        progressBar.layoutParams = lParams
        progressLayout.addView(progressBar)

        webView = WebView(this)
        val settings: WebSettings = webView.settings
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        setContentView(progressLayout)
        webView.clearCache(true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url == TermuxConstants.TERMUX_WIKI_URL || url.startsWith(TermuxConstants.TERMUX_WIKI_URL + "/")) {
                    // Inline help.
                    setContentView(progressLayout)
                    return false
                }

                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: ActivityNotFoundException) {
                    // Android TV does not have a system browser.
                    setContentView(progressLayout)
                    return false
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                setContentView(webView)
            }
        }
        webView.loadUrl(TermuxConstants.TERMUX_WIKI_URL)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

}
