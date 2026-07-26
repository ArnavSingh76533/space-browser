package com.spacebrowser.core.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.spacebrowser.core.adblock.AdBlocker
import com.spacebrowser.core.db.BrowsingRepository
import com.spacebrowser.core.settings.SearchEngines
import com.spacebrowser.core.settings.SettingsRepository
import com.spacebrowser.core.settings.SpaceSettings
import com.spacebrowser.core.util.UrlUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/** What WebView clients need from the app; kept narrow for testability. */
class TabManagerDeps(
    val events: BrowserEvents,
    val settings: () -> SpaceSettings,
    val recordVisit: (url: String, title: String) -> Unit,
    val onNavigated: (Tab) -> Unit,
    val httpAllowedHosts: MutableSet<String>,
)

class TabManager(
    private val appContext: Context,
    private val settingsRepo: SettingsRepository,
    private val browsingRepo: BrowsingRepository,
    private val adBlocker: AdBlocker,
    val events: BrowserEvents,
    initialSettings: SpaceSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val factory = WebViewFactory(appContext, events)

    /**
     * The current Activity, when one exists. WebViews created with an Activity
     * context can show `<select>` dropdowns, date pickers and autofill; the
     * app context is only a fallback and should rarely be used in practice.
     */
    @Volatile var hostContext: Context? = null

    @Volatile var settings: SpaceSettings = initialSettings
        private set

    /** Hosts where the https upgrade failed this session; retried as http. */
    private val httpAllowedHosts: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf<String>())

    val tabs = mutableStateListOf<Tab>()
    var activeTabId by mutableStateOf<String?>(null)
        private set
    val activeTab: Tab? get() = tabs.firstOrNull { it.id == activeTabId }

    private val recentlyClosedBacking = mutableStateListOf<ClosedTab>()
    val recentlyClosed: List<ClosedTab> get() = recentlyClosedBacking

    private var appInForeground = true
    private var persistJob: Job? = null

    private val deps = TabManagerDeps(
        events = events,
        settings = { settings },
        recordVisit = { url, title -> scope.launch(Dispatchers.IO) { browsingRepo.recordVisit(url, title) } },
        onNavigated = { schedulePersist() },
        httpAllowedHosts = httpAllowedHosts,
    )

    // Lifecycle -----------------------------------------------------------------

    fun start() {
        restoreSession()
        if (tabs.isEmpty()) newTab()
        startStatsFlusher()
    }

    fun applySettings(s: SpaceSettings) {
        settings = s
        adBlocker.updateUserRules(s.adBlockCustomRules, s.adBlockAllowlist)
        tabs.forEach { tab -> tab.webView?.let { factory.applyDynamic(it, tab, s) } }
    }

    /** Pause/resume engines with the app: big battery win for background use. */
    fun setAppForeground(foreground: Boolean) {
        appInForeground = foreground
        tabs.forEach { tab ->
            val wv = tab.webView ?: return@forEach
            if (foreground && tab.id == activeTabId) wv.onResume() else wv.onPause()
        }
    }

    // Tab operations ------------------------------------------------------------

    fun newTab(url: String? = null, isPrivate: Boolean = false, select: Boolean = true): Tab {
        val tab = Tab(isPrivate = isPrivate)
        tabs += tab
        if (select) select(tab.id)
        if (url != null) load(tab, url)
        schedulePersist()
        return tab
    }

    fun select(id: String) {
        if (activeTabId == id) return
        activeTab?.let { old ->
            captureThumbnail(old)
            old.webView?.onPause()
        }
        activeTabId = id
        activeTab?.let { now ->
            if (appInForeground) now.webView?.onResume()
            // Restore a session-persisted URL lazily on first selection.
            val pending = now.pendingUrl
            if (pending != null && now.webView == null) {
                now.pendingUrl = null
                load(now, pending)
            }
        }
    }

    fun close(tab: Tab) {
        val wasActive = tab.id == activeTabId
        val index = tabs.indexOf(tab)
        tab.url?.let { u ->
            if (!tab.showHome) {
                recentlyClosedBacking.add(0, ClosedTab(u, tab.displayTitle, tab.isPrivate))
                while (recentlyClosedBacking.size > 20) recentlyClosedBacking.removeAt(recentlyClosedBacking.lastIndex)
            }
        }
        destroy(tab)
        tabs.remove(tab)
        if (tab.isPrivate && tabs.none { it.isPrivate }) clearPrivateTraces()
        if (tabs.isEmpty()) {
            newTab()
        } else if (wasActive) {
            select(tabs[index.coerceIn(0, tabs.lastIndex)].id)
        }
        schedulePersist()
    }

    fun closeAll(privateOnly: Boolean = false) {
        val toClose = tabs.filter { !privateOnly || it.isPrivate }.toList()
        toClose.forEach { destroy(it) }
        tabs.removeAll(toClose)
        if (privateOnly || toClose.any { it.isPrivate }) clearPrivateTraces()
        if (tabs.isEmpty()) newTab() else select(tabs.first().id)
        schedulePersist()
    }

    fun reopenLastClosed() {
        val closed = recentlyClosedBacking.firstOrNull() ?: return
        recentlyClosedBacking.removeAt(0)
        newTab(url = closed.url, isPrivate = closed.isPrivate)
    }

    fun duplicate(tab: Tab) {
        newTab(url = tab.url, isPrivate = tab.isPrivate)
    }

    // Loading -------------------------------------------------------------------

    /** Load address-bar input (URL or search) into [tab]. */
    fun submitInput(tab: Tab, input: String) {
        val template = SearchEngines.resolveTemplate(settings)
        load(tab, UrlUtil.toLoadable(input, template))
    }

    fun load(tab: Tab, url: String) {
        ensureWebView(tab).also { wv ->
            tab.showHome = false
            wv.loadUrl(url)
        }
    }

    fun goHome(tab: Tab) {
        tab.showHome = true
        tab.errorMessage = null
        schedulePersist()
    }

    fun reload(tab: Tab) {
        if (tab.showHome) return
        tab.errorMessage = null
        tab.webView?.reload()
    }

    fun toggleDesktopMode(tab: Tab) {
        tab.isDesktop = !tab.isDesktop
        tab.webView?.let {
            factory.applyDynamic(it, tab, settings)
            it.reload()
        }
    }

    fun ensureWebView(tab: Tab): android.webkit.WebView {
        tab.webView?.let { return it }
        val client = SpaceWebViewClient(tab, adBlocker, deps)
        val chrome = SpaceWebChromeClient(tab, deps)
        val wv = factory.create(hostContext ?: appContext, tab, settings, client, chrome)
        tab.webView = wv
        return wv
    }

    fun captureThumbnail(tab: Tab) {
        val wv = tab.webView ?: return
        if (wv.width <= 0 || wv.height <= 0 || tab.showHome) return
        try {
            val h = minOf(wv.height, (wv.width * 1.1f).toInt())
            val full = Bitmap.createBitmap(wv.width, h, Bitmap.Config.RGB_565)
            wv.draw(Canvas(full))
            tab.thumbnail = Bitmap.createScaledBitmap(full, wv.width / 3, h / 3, true)
            if (full != tab.thumbnail) full.recycle()
        } catch (_: Throwable) {
            // Never let a thumbnail crash the browser.
        }
    }

    private fun destroy(tab: Tab) {
        tab.webView?.let {
            it.stopLoading()
            it.destroy()
        }
        tab.webView = null
        tab.thumbnail = null
    }

    // Privacy -------------------------------------------------------------------

    /**
     * Best-effort private-mode cleanup. WebView's cookie jar is process-global,
     * so private tabs cannot get a fully isolated store; SPACE compensates by
     * disabling cache/history for them and dropping all session cookies when
     * the last private tab closes. Documented honestly in the README.
     */
    private fun clearPrivateTraces() {
        val cm = CookieManager.getInstance()
        cm.removeSessionCookies(null)
        cm.flush()
    }

    fun clearCookies() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
    }

    fun clearCache() {
        tabs.forEach { it.webView?.clearCache(true) }
        WebStorage.getInstance().deleteAllData()
    }

    fun clearHistoryData() {
        scope.launch(Dispatchers.IO) { browsingRepo.clearHistory() }
        tabs.forEach { it.webView?.clearHistory() }
    }

    /** Runs the user's clear-on-exit choices; used by the Exit action. */
    fun runExitCleanup() {
        val s = settings
        if (s.clearHistoryOnExit) clearHistoryData()
        if (s.clearCookiesOnExit) clearCookies()
        if (s.clearCacheOnExit) clearCache()
        flushStatsNow()
        persistNow()
    }

    // Stats ---------------------------------------------------------------------

    private fun startStatsFlusher() {
        scope.launch {
            while (isActive) {
                delay(10_000)
                flushStatsNow()
            }
        }
    }

    private fun flushStatsNow() {
        val delta = adBlocker.sessionBlocked.getAndSet(0)
        if (delta > 0) scope.launch(Dispatchers.IO) { settingsRepo.addTrackersBlocked(delta) }
    }

    // Session persistence -------------------------------------------------------

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(800)
            persistNow()
        }
    }

    private fun persistNow() {
        // Private tabs are intentionally never written to disk.
        val arr = JSONArray()
        tabs.filter { !it.isPrivate && !it.showHome && it.url != null }.forEach { t ->
            arr.put(JSONObject().put("url", t.url).put("title", t.title))
        }
        val json = arr.toString()
        scope.launch(Dispatchers.IO) { settingsRepo.saveSession(json) }
    }

    private fun restoreSession() {
        val json = kotlinx.coroutines.runBlocking { settingsRepo.sessionJson() }
        if (json.isBlank()) return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val url = o.optString("url").takeIf { it.isNotBlank() } ?: continue
                val tab = Tab()
                tab.pendingUrl = url
                tab.showHome = false
                tab.url = url
                tab.title = o.optString("title")
                tabs += tab
            }
            // Select the first tab but DON'T load yet: at this point only the
            // Application context exists. WebViewHost consumes pendingUrl once
            // an Activity is attached, so restored engines get a proper context.
            tabs.firstOrNull()?.let { activeTabId = it.id }
        } catch (_: Exception) {
            tabs.clear()
        }
    }
}
