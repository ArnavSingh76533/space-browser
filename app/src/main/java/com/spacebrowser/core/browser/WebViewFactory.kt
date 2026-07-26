package com.spacebrowser.core.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.spacebrowser.core.settings.SpaceSettings

/**
 * Creates and (re)configures WebViews. Android's WebView is Chromium-based;
 * SPACE layers privacy defaults on top of it.
 */
class WebViewFactory(
    private val appContext: Context,
    private val events: BrowserEvents,
) {

    /** The engine's default UA, captured once so it can be restored. */
    private val defaultUa: String by lazy { WebSettings.getDefaultUserAgent(appContext) }

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        tab: Tab,
        settings: SpaceSettings,
        webViewClient: SpaceWebViewClient,
        chromeClient: SpaceWebChromeClient,
    ): WebView {
        val wv = WebView(context)
        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        wv.webViewClient = webViewClient
        wv.webChromeClient = chromeClient
        wv.isVerticalScrollBarEnabled = true

        with(wv.settings) {
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            // Multiple-window popups open in the same tab; combined with the
            // popup-blocking default this keeps navigation predictable.
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            cacheMode = if (tab.isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            if (tab.isPrivate) {
                saveFormData = false
            }
        }

        applyDynamic(wv, tab, settings)
        installDownloadListener(wv, tab)
        return wv
    }

    /** Settings that may change at runtime; safe to re-apply to live WebViews. */
    fun applyDynamic(wv: WebView, tab: Tab, s: SpaceSettings) {
        with(wv.settings) {
            javaScriptEnabled = s.javascriptEnabled
            loadsImagesAutomatically = !s.blockImages
            blockNetworkImage = s.blockImages
            userAgentString = userAgentFor(tab, s)
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, !s.blockThirdPartyCookies && !tab.isPrivate)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(wv.settings, s.safeBrowsing)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, s.webDarkMode)
        }
    }

    fun userAgentFor(tab: Tab, s: SpaceSettings): String = when {
        tab.isDesktop -> DESKTOP_UA
        s.uaPrivacyMode -> PRIVACY_UA
        else -> defaultUa
    }

    private fun installDownloadListener(wv: WebView, tab: Tab) {
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                // Blob/data downloads need a JS bridge; out of scope for v0.1.
                return@setDownloadListener
            }
            try {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    setTitle(fileName)
                    setDescription(Uri.parse(url).host ?: "SPACE download")
                    addRequestHeader("User-Agent", userAgent)
                    CookieManager.getInstance().getCookie(url)?.let {
                        addRequestHeader("Cookie", it)
                    }
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }
                val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                events.toast("Downloading $fileName")
            } catch (_: Exception) {
                // Malformed URL or missing DownloadManager; nothing sensible to do.
            }
        }
    }

    companion object {
        /**
         * Reduced, Chrome-style generic UA (frozen model "K", stable versions):
         * every SPACE user in privacy mode presents the same string, shrinking
         * the UA's fingerprinting value.
         */
        const val PRIVACY_UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
    }
}
