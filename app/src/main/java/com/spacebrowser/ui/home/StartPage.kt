package com.spacebrowser.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spacebrowser.core.db.QuickLink
import com.spacebrowser.core.util.UrlUtil
import com.spacebrowser.ui.components.LetterAvatar
import com.spacebrowser.ui.components.glass
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartPage(
    isPrivate: Boolean,
    trackersBlockedTotal: Long,
    quickLinks: List<QuickLink>,
    onSearchClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onRemoveQuickLink: (QuickLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }

    var linkToRemove by remember { mutableStateOf<QuickLink?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = timeFmt.format(now),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = dateFmt.format(now),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "S P A C E",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        if (isPrivate) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .glass(RoundedCornerShape(16.dp), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.secondary)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Filled.VisibilityOff, contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Private tab — no history, no cache. Session cookies are dropped when the last private tab closes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Search pill (focuses the real address bar) ---------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .glass(RoundedCornerShape(26.dp), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary)
                .combinedClickable(onClick = onSearchClick),
        ) {
            Icon(
                Icons.Filled.Search, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 18.dp, end = 10.dp),
            )
            Text(
                if (isPrivate) "Search privately" else "Search the cosmos",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))

        if (quickLinks.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((quickLinks.size + 3) / 4 * 92).dp),
            ) {
                items(quickLinks, key = { it.id }) { link ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.combinedClickable(
                            onClick = { onOpenUrl(link.url) },
                            onLongClick = { linkToRemove = link },
                        ),
                    ) {
                        LetterAvatar(text = link.title.ifBlank { UrlUtil.prettyHost(link.url) })
                        Text(
                            text = link.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Privacy stats --------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(20.dp), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary)
                .padding(16.dp),
        ) {
            Icon(
                Icons.Filled.Shield, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "%,d trackers blocked".format(trackersBlockedTotal),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Since you started flying with SPACE",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    linkToRemove?.let { link ->
        AlertDialog(
            onDismissRequest = { linkToRemove = null },
            title = { Text("Remove shortcut") },
            text = { Text("Remove \"${link.title}\" from your start page?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveQuickLink(link)
                    linkToRemove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { linkToRemove = null }) { Text("Cancel") } },
        )
    }
}
