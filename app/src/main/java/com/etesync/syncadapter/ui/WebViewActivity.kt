package com.etesync.syncadapter.ui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import androidx.appcompat.app.ActionBar
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.R

class WebViewActivity : BaseActivity() {

    private var mWebView: WebView? = null
    private var mProgressBar: ProgressBar? = null
    private var mToolbar: ActionBar? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        mToolbar = supportActionBar
        mToolbar!!.setDisplayHomeAsUpEnabled(true)

        var uri = intent.getParcelableExtra<Uri>(KEY_URL)
        uri = addQueryParams(uri)
        mWebView = findViewById<View>(R.id.webView) as WebView
        mProgressBar = findViewById<View>(R.id.progressBar) as ProgressBar

        mWebView!!.settings.javaScriptEnabled = true
        if (savedInstanceState == null) {
            mWebView!!.loadUrl(uri.toString())
        }

        mWebView!!.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                title = view.title
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return shouldOverrideUrl(Uri.parse(url))
            }

            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return shouldOverrideUrl(request.url)
            }

            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                loadErrorPage(failingUrl)
            }

            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                loadErrorPage(request.url.toString())
            }
        }

        mWebView!!.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, progress: Int) {
                if (progress == 100) {
                    mToolbar!!.title = view.title
                    mProgressBar!!.visibility = View.INVISIBLE
                } else {
                    mToolbar!!.setTitle(R.string.loading)
                    mProgressBar!!.visibility = View.VISIBLE
                    mProgressBar!!.progress = progress
                }
            }
        }
    }

    private fun addQueryParams(uri: Uri): Uri {
        return uri.buildUpon().appendQueryParameter(QUERY_KEY_EMBEDDED, "1").build()
    }

    private fun loadErrorPage(failingUrl: String) {
        val htmlData = "<html><title>" +
                getString(R.string.loading_error_title) +
                "</title>" +
                "<style>" +
                ".btn {" +
                "    display: inline-block;" +
                "    padding: 6px 12px;" +
                "    font-size: 20px;" +
                "    font-weight: 400;" +
                "    line-height: 1.42857143;" +
                "    text-align: center;" +
                "    white-space: nowrap;" +
                "    vertical-align: middle;" +
                "    touch-action: manipulation;" +
                "    cursor: pointer;" +
                "    user-select: none;" +
                "    border: 1px solid #ccc;" +
                "    border-radius: 4px;" +
                "    color: #333;" +
                "    text-decoration: none;" +
                "    margin-top: 50px;" +
                "}" +
                "</style>" +
                "<body>" +
                "<div align=\"center\">" +
                "<a class=\"btn\" href=\"" + failingUrl + "\">" + getString(R.string.loading_error_content) +
                "</a>" +
                "</form></body></html>"

        mWebView!!.loadDataWithBaseURL("about:blank", htmlData, "text/html", "UTF-8", null)
        mWebView!!.invalidate()
    }

    private fun shouldOverrideUrl(_uri: Uri): Boolean {
        var uri = _uri
        if (isAllowedUrl(uri)) {
            if (uri.getQueryParameter(QUERY_KEY_EMBEDDED) != null) {
                return false
            } else {
                uri = addQueryParams(uri)
                mWebView!!.loadUrl(uri.toString())
                return true
            }
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            return true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mWebView!!.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mWebView!!.restoreState(savedInstanceState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mWebView!!.canGoBack()) {
                mWebView!!.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {

        private val KEY_URL = "url"
        private val QUERY_KEY_EMBEDDED = "embedded"

        fun openUrl(context: Context, uri: Uri) {
            if (isAllowedUrl(uri)) {
                val intent = Intent(context, WebViewActivity::class.java)
                intent.putExtra(WebViewActivity.KEY_URL, uri)
                context.startActivity(intent)
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }

        private fun uriEqual(uri1: Uri, uri2: Uri): Boolean {
            return uri1.host == uri2.host && uri1.path == uri2.path
        }

        private fun allowedUris(allowedUris: Array<Uri>, uri2: Uri): Boolean {
            for (uri in allowedUris) {
                if (uriEqual(uri, uri2)) {
                    return true
                }
            }
            return false
        }

        private fun isAllowedUrl(uri: Uri): Boolean {
            val allowedUris = arrayOf(Constants.faqUri, Constants.helpUri, Constants.registrationUrl, Constants.dashboard, Constants.webUri.buildUpon().appendEncodedPath("tos/").build(), Constants.webUri.buildUpon().appendEncodedPath("about/").build())
            val accountsUri = Constants.webUri.buildUpon().appendEncodedPath("accounts/").build()

            return allowedUris(allowedUris, uri) || uri.host == accountsUri.host && uri.path!!.startsWith(accountsUri.path!!)
        }
    }
}
