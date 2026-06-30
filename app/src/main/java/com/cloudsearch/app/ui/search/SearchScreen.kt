package me.fulltxt.app.ui.search

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import me.fulltxt.app.domain.model.CloudFile
import me.fulltxt.app.domain.model.CloudProvider
import me.fulltxt.app.domain.model.DateFilter
import me.fulltxt.app.domain.model.FileTypeFilter
import me.fulltxt.app.domain.model.SearchFilter
import me.fulltxt.app.domain.model.SearchResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSettingsClick: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val connectedProviders by viewModel.connectedProviders.collectAsState()
    val fileTypeIcons by viewModel.fileTypeIcons.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.openUrl.collect { url ->
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    // Re-check connected accounts on resume so the empty-state hint disappears right after
    // the user links an account or local folder in settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshConnectedProviders()
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState
        ) {
            FilterSheetContent(
                filter = filter,
                connectedProviders = connectedProviders,
                onFilterChange = viewModel::updateFilter,
                onClearAll = {
                    viewModel.updateFilter(SearchFilter())
                    showFilterSheet = false
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Suchen…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Löschen")
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.saveRecentSearch(query)
                        focusManager.clearFocus()
                    })
                )
                BadgedBox(
                    modifier = Modifier.padding(start = 4.dp),
                    badge = { if (filter.isActive) Badge() }
                ) {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filtern")
                    }
                }
            }

            if (connectedProviders.isEmpty()) {
                NoAccountsHint(onSettingsClick = onSettingsClick)
            } else {
                if (query.isEmpty() && recentSearches.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    recentSearches.forEach { term ->
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.onQueryChange(term)
                                focusManager.clearFocus()
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            headlineContent = { Text(term) }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                }

                if (filter.isActive) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filter.providers.forEach { provider ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.updateFilter(filter.copy(providers = filter.providers - provider)) },
                                label = { Text(provider.label) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                        filter.fileTypes.forEach { ft ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.updateFilter(filter.copy(fileTypes = filter.fileTypes - ft)) },
                                label = { Text(ft.label) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                        filter.dateFilter?.let { df ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.updateFilter(filter.copy(dateFilter = null)) },
                                label = { Text(df.label) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }

                if (query.trim().length >= 2) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (results.size) {
                            0 -> "Keine Ergebnisse"
                            1 -> "1 Ergebnis"
                            else -> "${results.size} Ergebnisse"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(results, key = { it.file.fileId }) { result ->
                        SearchResultItem(
                            result = result,
                            coloredIcon = fileTypeIcons,
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.openFile(result.file)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoAccountsHint(onSettingsClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Noch nichts zum Durchsuchen",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Verknüpfe zuerst in den Einstellungen einen Cloud-Account oder einen " +
                "lokalen Ordner und starte eine Indexierung. Danach kannst du deine Dateien " +
                "durchsuchen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Zu den Einstellungen")
        }
    }
}

@Composable
private fun FilterSheetContent(
    filter: SearchFilter,
    connectedProviders: List<CloudProvider>,
    onFilterChange: (SearchFilter) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filter", style = MaterialTheme.typography.titleMedium)
            if (filter.isActive) {
                TextButton(onClick = onClearAll) { Text("Zurücksetzen") }
            }
        }

        if (connectedProviders.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text("Cloud-Anbieter", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                connectedProviders.forEach { provider ->
                    FilterChip(
                        selected = provider in filter.providers,
                        onClick = {
                            val updated = if (provider in filter.providers)
                                filter.providers - provider
                            else filter.providers + provider
                            onFilterChange(filter.copy(providers = updated))
                        },
                        label = { Text(provider.label) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Dateityp", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FileTypeFilter.entries.forEach { ft ->
                FilterChip(
                    selected = ft in filter.fileTypes,
                    onClick = {
                        val updated = if (ft in filter.fileTypes)
                            filter.fileTypes - ft
                        else filter.fileTypes + ft
                        onFilterChange(filter.copy(fileTypes = updated))
                    },
                    label = { Text(ft.label) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Geändert", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateFilter.entries.forEach { df ->
                FilterChip(
                    selected = df == filter.dateFilter,
                    onClick = {
                        onFilterChange(filter.copy(dateFilter = if (df == filter.dateFilter) null else df))
                    },
                    label = { Text(df.label) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(result: SearchResult, coloredIcon: Boolean, onClick: () -> Unit) {
    val snippet = result.snippet.trim()
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            val visual = fileTypeVisual(result.file.fileName)
            Icon(
                imageVector = if (coloredIcon) visual.icon else Icons.Default.Description,
                contentDescription = null,
                tint = if (coloredIcon) visual.color else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        headlineContent = {
            Text(
                text = result.file.fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                // Google Drive's cloudPath is built from opaque folder IDs (e.g. "/1-h577…/name"),
                // which has no value for the user, so we hide it for that provider. Other providers
                // expose a readable path worth showing.
                val showPath = result.file.cloudProvider != CloudProvider.GOOGLE_DRIVE &&
                    result.file.cloudPath.isNotBlank()
                if (showPath) {
                    Text(
                        text = result.file.cloudPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (snippet.isNotEmpty()) {
                    if (showPath) Spacer(Modifier.height(2.dp))
                    SnippetText(raw = snippet)
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = result.file.cloudProvider.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val otherProviders = result.duplicateProviders
                    .filter { it != result.file.cloudProvider }
                if (otherProviders.isNotEmpty()) {
                    Text(
                        text = "Auch: " + otherProviders.joinToString(", ") { it.label },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

@Composable
private fun SnippetText(raw: String, modifier: Modifier = Modifier) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val annotated = remember(raw, highlightColor) { buildSnippetAnnotated(raw, highlightColor) }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

private fun buildSnippetAnnotated(raw: String, highlightColor: Color): AnnotatedString {
    val pattern = Regex("""\[\[(.+?)]]""")
    val framed = reframeSnippet(raw)
    return buildAnnotatedString {
        var cursor = 0
        pattern.findAll(framed).forEach { match ->
            append(framed.substring(cursor, match.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
                append(match.groupValues[1])
            }
            cursor = match.range.last + 1
        }
        if (cursor < framed.length) append(framed.substring(cursor))
    }
}

/**
 * FTS' snippet() often places the matched term at the end of the fragment, with a lot of
 * leading context. Since the snippet is truncated to two lines, that pushes the highlighted
 * match out of view. Move the first match near the start by trimming excess leading context.
 */
private fun reframeSnippet(raw: String): String {
    val maxLead = 24
    val markerStart = raw.indexOf("[[")
    if (markerStart <= maxLead) return raw
    val start = markerStart - maxLead
    val space = raw.indexOf(' ', start)
    val cut = if (space in (start + 1) until markerStart) space + 1 else start
    return "… " + raw.substring(cut)
}

private data class FileVisual(val icon: ImageVector, val color: Color)

/** Maps a file name's extension to a colored type icon for search results. */
@Composable
private fun fileTypeVisual(fileName: String): FileVisual =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> FileVisual(Icons.Default.PictureAsPdf, Color(0xFFD32F2F))
        "doc", "docx", "odt", "rtf" -> FileVisual(Icons.Default.Description, Color(0xFF1565C0))
        "xls", "xlsx", "ods" -> FileVisual(Icons.Default.TableChart, Color(0xFF2E7D32))
        "ppt", "pptx", "odp" -> FileVisual(Icons.Default.Slideshow, Color(0xFFE64A19))
        "txt", "md", "csv", "log" -> FileVisual(Icons.Default.Article, Color(0xFF546E7A))
        else -> FileVisual(Icons.Default.InsertDriveFile, MaterialTheme.colorScheme.onSurfaceVariant)
    }

private val CloudProvider.label get() = when (this) {
    CloudProvider.GOOGLE_DRIVE   -> "Drive"
    CloudProvider.ONE_DRIVE      -> "OneDrive"
    CloudProvider.NEXTCLOUD      -> "Nextcloud"
    CloudProvider.OWNCLOUD       -> "ownCloud"
    CloudProvider.DROPBOX        -> "Dropbox"
    CloudProvider.MAGENTA_CLOUD  -> "MagentaCloud"
    CloudProvider.STRATO_HIDRIVE -> "HiDrive"
    CloudProvider.YANDEX_DISK    -> "Yandex"
    CloudProvider.LOCAL          -> "Lokal"
}
