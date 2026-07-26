package com.spacebrowser.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebrowser.core.AppContainer
import com.spacebrowser.core.settings.SearchEngines
import com.spacebrowser.core.settings.SpaceSettings
import com.spacebrowser.core.util.UrlUtil
import com.spacebrowser.ui.ActivityActions
import com.spacebrowser.ui.LibrarySection
import com.spacebrowser.ui.ai.AiSheet
import com.spacebrowser.ui.components.AddressBar
import com.spacebrowser.ui.components.glass
import com.spacebrowser.ui.home.StartPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class Suggestion(val text: String, val url: String?, val fromHistory: Boolean)

@Composable
fun BrowserScreen(
    container: AppContainer,
    settings: SpaceSettings,
    actions: ActivityActions,
    onOpenTabs: () -> Unit,
    onOpenLibrary: (LibrarySection) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tabManager = container.tabManager
    val tab = tabManager.activeTab
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Address field state ------------------------------------------------------
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var requestFocusTick by remember { mutableIntStateOf(0) }
    val addressFocus = remember { FocusRequester() }
    LaunchedEffect(requestFocusTick) {
        if (requestFocusTick > 0) addressFocus.requestFocus()
    }

    // Data flows ---------------------------------------------------------------
    val quickLinksFlow = remember { container.browsingRepository.quickLinks() }
    val quickLinks by quickLinksFlow.collectAsState(initial = emptyList())

    // Keep the field mirroring the page URL whenever the user isn't typing.
    LaunchedEffect(tab?.id, tab?.url, tab?.showHome, editing) {
        if (!editing) {
            field = TextFieldValue(if (tab == null || tab.showHome) "" else tab.url.orEmpty())
        }
    }

    fun submit(text: String) {
        val t = tab ?: return
        if (text.isBlank()) return
        editing = false
        focusManager.clearFocus()
        tabManager.submitInput(t, text)
    }

    // Suggestions --------------------------------------------------------------
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    LaunchedEffect(field.text, editing) {
        if (!editing || field.text.length < 2 || tab?.isPrivate == true) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(200) // debounce
        val q = field.text
        val history = container.browsingRepository.searchHistory(q, 3)
            .map { Suggestion(it.title.ifBlank { it.url }, it.url, fromHistory = true) }
        val engine = SearchEngines.resolveSuggestUrl(settings)?.let { template ->
            container.suggestionClient.fetch(template, q, 5)
                .map { Suggestion(it, null, fromHistory = false) }
        } ?: emptyList()
        suggestions = (history + engine).distinctBy { it.text }.take(7)
    }

    // Find in page -------------------------------------------------------------
    var findVisible by rememberSaveable { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var findMatches by remember { mutableStateOf(0 to 0) }

    fun closeFind() {
        tab?.webView?.clearMatches()
        findVisible = false
        findQuery = ""
        findMatches = 0 to 0
    }

    // Sheets & dialogs ---------------------------------------------------------
    var menuVisible by remember { mutableStateOf(false) }
    var aiVisible by remember { mutableStateOf(false) }
    var sitePanelVisible by remember { mutableStateOf(false) }
    var passwordGenVisible by remember { mutableStateOf(false) }

    val currentUrl = tab?.url
    val bookmarkFlow = remember(currentUrl) {
        currentUrl?.let { container.browsingRepository.isBookmarked(it) } ?: flowOf(0)
    }
    val bookmarkCount by bookmarkFlow.collectAsState(initial = 0)

    // Back handling ------------------------------------------------------------
    val webCanGoBack = tab != null && tab.canGoBack
    val handlesBack = editing || findVisible || webCanGoBack ||
        (tab != null && !tab.showHome) || tabManager.tabs.size > 1
    BackHandler(enabled = handlesBack) {
        val t = tab
        when {
            editing -> { editing = false; focusManager.clearFocus() }
            findVisible -> closeFind()
            t == null -> Unit
            t.webView?.canGoBack() == true -> t.webView?.goBack()
            !t.showHome -> tabManager.goHome(t)
            tabManager.tabs.size > 1 -> tabManager.close(t)
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        AddressBar(
            value = field,
            onValueChange = { field = it },
            onSubmit = ::submit,
            onFocusChanged = { focused ->
                editing = focused
                if (focused) {
                    val full = if (tab?.showHome == false) tab.url.orEmpty() else field.text
                    field = TextFieldValue(full, selection = TextRange(0, full.length))
                }
            },
            focusRequester = addressFocus,
            isEditing = editing,
            isSecure = tab?.isSecure != false,
            isPrivateTab = tab?.isPrivate == true,
            isLoading = tab != null && !tab.showHome && tab.isLoading,
            progress = tab?.progress ?: 0,
            blockedCount = tab?.blockedOnPage ?: 0,
            shieldActive = settings.adBlockEnabled &&
                !container.adBlocker.isSiteAllowlisted(UrlUtil.hostOf(tab?.url)),
            tabCount = tabManager.tabs.size,
            onShieldClick = { if (tab != null && !tab.showHome) sitePanelVisible = true },
            onTabsClick = {
                tab?.let { tabManager.captureThumbnail(it) }
                onOpenTabs()
            },
            onMenuClick = { menuVisible = true },
            onClearClick = { field = TextFieldValue("") },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                tab == null -> Unit
                tab.showHome -> {
                    StartPage(
                        isPrivate = tab.isPrivate,
                        trackersBlockedTotal = settings.trackersBlockedTotal,
                        quickLinks = quickLinks,
                        onSearchClick = { requestFocusTick++ },
                        onOpenUrl = { tabManager.load(tab, it) },
                        onRemoveQuickLink = {
                            scope.launch { container.browsingRepository.removeQuickLink(it.id) }
                        },
                    )
                }
                tab.errorMessage != null -> ErrorView(
                    tab = tab,
                    onRetry = { tabManager.reload(tab) },
                    onGoHome = { tabManager.goHome(tab) },
                )
                else -> WebViewHost(tab = tab, tabManager = tabManager, modifier = Modifier.fillMaxSize())
            }

            // Suggestion dropdown ---------------------------------------------
            if (editing && suggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .glass(RoundedCornerShape(18.dp), MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primary, alpha = 0.95f),
                ) {
                    items(suggestions) { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { submit(s.url ?: s.text) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Icon(
                                if (s.fromHistory) Icons.Filled.History else Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    s.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                s.url?.let {
                                    Text(
                                        it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Find-in-page bar ------------------------------------------------
            if (findVisible && tab?.webView != null) {
                val wv = tab.webView!!
                LaunchedEffect(findVisible) {
                    wv.setFindListener { active, total, _ ->
                        findMatches = (if (total == 0) 0 else active + 1) to total
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .imePadding()
                        .fillMaxWidth()
                        .padding(12.dp)
                        .glass(RoundedCornerShape(18.dp), MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primary, alpha = 0.95f)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = {
                            findQuery = it
                            wv.findAllAsync(it)
                        },
                        placeholder = { Text("Find in page") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${findMatches.first}/${findMatches.second}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = { wv.findNext(false) }) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
                    }
                    IconButton(onClick = { wv.findNext(true) }) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                    }
                    IconButton(onClick = { closeFind() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close find bar")
                    }
                }
            }
        }
    }

    // Menu sheet ---------------------------------------------------------------
    if (menuVisible) {
        BrowserMenuSheet(
            tab = tab,
            isBookmarked = bookmarkCount > 0,
            onDismiss = { menuVisible = false },
            callbacks = MenuCallbacks(
                onNewTab = { tabManager.newTab() },
                onNewPrivateTab = { tabManager.newTab(isPrivate = true) },
                onReload = { tab?.let { tabManager.reload(it) } },
                onForward = { tab?.webView?.goForward() },
                onHome = { tab?.let { tabManager.goHome(it) } },
                onToggleBookmark = {
                    val t = tab ?: return@MenuCallbacks
                    val url = t.url ?: return@MenuCallbacks
                    scope.launch {
                        if (bookmarkCount > 0) container.browsingRepository.removeBookmark(url)
                        else container.browsingRepository.addBookmark(url, t.title)
                    }
                },
                onAddQuickLink = {
                    val t = tab ?: return@MenuCallbacks
                    val url = t.url ?: return@MenuCallbacks
                    scope.launch {
                        container.browsingRepository.addQuickLink(url, t.title.ifBlank { UrlUtil.prettyHost(url) })
                        container.browserEvents.toast("Added to start page")
                    }
                },
                onFindInPage = { findVisible = true },
                onToggleDesktop = { tab?.let { tabManager.toggleDesktopMode(it) } },
                onShare = {
                    val t = tab ?: return@MenuCallbacks
                    actions.shareText(t.displayTitle, t.url ?: return@MenuCallbacks)
                },
                onPrintPdf = { tab?.let { actions.printPage(it) } },
                onScreenshot = { tab?.let { actions.capturePageAndShare(it) } },
                onAi = { aiVisible = true },
                onLibrary = { onOpenLibrary(LibrarySection.BOOKMARKS) },
                onPasswordGenerator = { passwordGenVisible = true },
                onSettings = onOpenSettings,
                onExit = {
                    tabManager.runExitCleanup()
                    actions.exitApp()
                },
            ),
        )
    }

    if (aiVisible) {
        AiSheet(container = container, settings = settings, tab = tab, onDismiss = { aiVisible = false })
    }

    if (sitePanelVisible && tab != null) {
        val host = UrlUtil.hostOf(tab.url)?.removePrefix("www.")
        val shieldsUp = host != null && host !in settings.adBlockAllowlist
        SitePanelDialog(
            tab = tab,
            shieldsUpForSite = shieldsUp,
            onToggleShields = {
                if (host == null) return@SitePanelDialog
                scope.launch {
                    val next = if (shieldsUp) settings.adBlockAllowlist + host
                    else settings.adBlockAllowlist - host
                    container.settingsRepository.setAdBlockAllowlist(next)
                }
            },
            onCopyUrl = { actions.copyToClipboard("URL", tab.url.orEmpty()) },
            onDismiss = { sitePanelVisible = false },
        )
    }

    if (passwordGenVisible) {
        PasswordGeneratorDialog(
            onCopy = { actions.copyToClipboard("Password", it) },
            onDismiss = { passwordGenVisible = false },
        )
    }
}
