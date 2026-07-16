package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.ui.ChannelUiModel
import com.futo.platformplayer.compose.ui.PlaylistUiModel
import com.futo.platformplayer.compose.ui.SearchUiState
import com.futo.platformplayer.compose.ui.SearchContentType
import com.futo.platformplayer.compose.ui.SourceAvailability
import com.futo.platformplayer.compose.ui.SourceUiModel
import com.futo.platformplayer.compose.ui.VideoUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    search: SearchUiState,
    sources: List<SourceUiModel>,
    onQueryChange: (String) -> Unit,
    onSubmit: (String, SearchContentType, Set<String>) -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoUiModel) -> Unit,
    onVideoLongClick: (VideoUiModel) -> Unit,
    onChannelClick: (ChannelUiModel) -> Unit,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
) {
    val listState = rememberLazyListState()
    var typeName by rememberSaveable { mutableStateOf(SearchContentType.Videos.name) }
    var selectedSourceIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var sourcesInitialized by rememberSaveable { mutableStateOf(false) }
    var showSourcePicker by rememberSaveable { mutableStateOf(false) }
    val type = SearchContentType.valueOf(typeName)
    val activeSources = sources.filter {
        it.isEnabled && it.availability != SourceAvailability.MissingPlugin
    }
    val selectedSources = selectedSourceIds.toSet().intersect(activeSources.map(SourceUiModel::id).toSet())
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var queryFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = search.query,
                selection = TextRange(search.query.length),
            ),
        )
    }
    val resultCount = when (type) {
        SearchContentType.Creators -> search.channels.size
        SearchContentType.Playlists -> search.playlists.size
        SearchContentType.Videos -> search.videos.size
    }
    val showingResults = search.isLoading || search.hasSearched || search.errorMessage != null
    RequestNextPageEffect(
        listState = listState,
        canLoadMore = showingResults && search.hasMore && !search.isLoading && !search.isLoadingMore,
        onLoadMore = onLoadMore,
    )
    val submit: (String) -> Unit = { value ->
        value.trim().takeIf(String::isNotEmpty)?.let { query ->
            onSubmit(query, type, selectedSources)
            keyboardController?.hide()
        }
    }
    val appendSuggestion: (String) -> Unit = { suggestion ->
        val currentQuery = search.query.trimEnd()
        val suggestionText = suggestion.trim()
        val appendedQuery = when {
            currentQuery.isEmpty() -> suggestionText
            suggestionText.startsWith(currentQuery, ignoreCase = true) ->
                currentQuery + suggestionText.drop(currentQuery.length)
            else -> "$currentQuery $suggestionText"
        }
        queryFieldValue = TextFieldValue(
            text = appendedQuery,
            selection = TextRange(appendedQuery.length),
        )
        onQueryChange(appendedQuery)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(search.query) {
        if (queryFieldValue.text != search.query) {
            queryFieldValue = TextFieldValue(
                text = search.query,
                selection = TextRange(search.query.length),
            )
        }
    }

    LaunchedEffect(activeSources.map(SourceUiModel::id)) {
        val activeIds = activeSources.map(SourceUiModel::id)
        selectedSourceIds = if (!sourcesInitialized) {
            sourcesInitialized = true
            activeIds
        } else {
            selectedSourceIds.filter { it in activeIds }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.testTag("search-results"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = queryFieldValue,
                onValueChange = { value ->
                    queryFieldValue = value
                    if (value.text != search.query) {
                        onQueryChange(value.text)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("search-field"),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                supportingText = {
                    Text(
                        pluralStringResource(
                            R.plurals.searching_active_sources,
                            selectedSources.size,
                            selectedSources.size,
                        ),
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                queryFieldValue = TextFieldValue("", selection = TextRange.Zero)
                                onQueryChange("")
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.clear_search),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(search.query) }),
            )
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchContentType.entries.forEach { filter ->
                    FilterChip(
                        selected = type == filter,
                        onClick = {
                            typeName = filter.name
                            if (search.hasSearched && search.query.isNotBlank()) {
                                onSubmit(search.query, filter, selectedSources)
                            }
                        },
                        modifier = Modifier.testTag("search-filter-${filter.name.lowercase()}"),
                        label = { Text(stringResource(filter.labelRes)) },
                    )
                }
                FilterChip(
                    selected = showSourcePicker,
                    onClick = { showSourcePicker = true },
                    leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                    label = { Text(stringResource(R.string.sources_with_count, selectedSources.size)) },
                    modifier = Modifier.testTag("search-source-picker"),
                )
            }
        }

        if (!showingResults) {
            when {
                search.query.isBlank() -> item {
                    SearchMessage(
                        title = stringResource(R.string.search_question),
                        body = stringResource(R.string.search_start_typing),
                    )
                }
                search.isLoadingSuggestions -> item {
                    androidx.compose.foundation.layout.Box(
                        Modifier.testTag("search-suggestions-loading"),
                    ) {
                        SuggestionListSkeleton()
                    }
                }
                search.suggestions.isNotEmpty() -> items(
                    items = search.suggestions,
                    key = { suggestion -> suggestion.lowercase() },
                ) { suggestion ->
                    SuggestionRow(
                        suggestion = suggestion,
                        onClick = {
                            onQueryChange(suggestion)
                            submit(suggestion)
                        },
                        onAppend = { appendSuggestion(suggestion) },
                    )
                }
                else -> item {
                    SuggestionRow(
                        suggestion = search.query,
                        label = stringResource(R.string.search_for_query, search.query.trim()),
                        onClick = { submit(search.query) },
                    )
                }
            }
        } else {
            if (search.isLoading) {
                item {
                    androidx.compose.foundation.layout.Box(
                        Modifier.testTag("search-loading"),
                    ) {
                        if (type == SearchContentType.Videos) {
                            VideoListSkeleton(count = 4, modifier = Modifier.fillMaxWidth())
                        } else {
                            SuggestionListSkeleton(count = 5)
                        }
                    }
                }
            }
            item {
                Text(
                    if (search.isLoading && !search.hasSearched) {
                        stringResource(R.string.searching)
                    } else {
                        pluralStringResource(R.plurals.result_count, resultCount, resultCount)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            when {
                search.errorMessage != null -> item {
                    SearchMessage(
                        title = stringResource(R.string.search_unavailable),
                        body = search.errorMessage,
                    )
                }
                search.hasSearched && !search.isLoading && resultCount == 0 -> item {
                    SearchMessage(
                        title = stringResource(R.string.no_matches),
                        body = stringResource(R.string.search_no_matches_body),
                    )
                }
                else -> when (type) {
                    SearchContentType.Creators -> items(search.channels, key = ChannelUiModel::id) { channel ->
                        ChannelRow(channel = channel, onClick = { onChannelClick(channel) })
                    }
                    SearchContentType.Playlists -> items(search.playlists, key = PlaylistUiModel::id) { playlist ->
                        PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                    }
                    SearchContentType.Videos -> {
                        if (search.videos.isNotEmpty()) {
                            item { SectionHeading(stringResource(R.string.videos)) }
                        }
                        itemsIndexed(search.videos, key = { _, video -> video.id }) { index, video ->
                            CompactVideoCard(
                                video = video,
                                index = index,
                                onClick = { onVideoClick(video) },
                                onLongClick = { onVideoLongClick(video) },
                            )
                        }
                    }
                }
            }
        }
        if (search.isLoadingMore) {
            item {
                if (type == SearchContentType.Videos) {
                    VideoListSkeleton(count = 2, modifier = Modifier.fillMaxWidth())
                } else {
                    SuggestionListSkeleton(count = 2)
                }
            }
        }
    }

    if (showSourcePicker) {
        ModalBottomSheet(
            onDismissRequest = {
                showSourcePicker = false
                if (search.hasSearched && search.query.isNotBlank() && selectedSources.isNotEmpty()) {
                    onSubmit(search.query, type, selectedSources)
                }
            },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.search_sources), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.search_sources_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                activeSources.forEach { source ->
                    FilterChip(
                        selected = source.id in selectedSources,
                        onClick = {
                            selectedSourceIds = if (source.id in selectedSources) {
                                selectedSourceIds - source.id
                            } else {
                                selectedSourceIds + source.id
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            SourceIconImage(
                                name = source.name,
                                iconUrl = source.iconUrl,
                                accentColor = androidx.compose.ui.graphics.Color(source.accentColor),
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = { Text(source.name) },
                    )
                }
                Button(
                    onClick = {
                        selectedSourceIds = activeSources.map(SourceUiModel::id)
                    },
                ) { Text(stringResource(R.string.select_all)) }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: String,
    label: String = suggestion,
    onClick: () -> Unit,
    onAppend: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingContent = onAppend?.let { append ->
            {
                IconButton(
                    onClick = append,
                    modifier = Modifier.testTag("search-suggestion-append-${suggestion.hashCode()}"),
                ) {
                    Icon(
                        Icons.Outlined.NorthWest,
                        contentDescription = stringResource(R.string.append_suggestion, suggestion),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("search-suggestion-${suggestion.hashCode()}"),
    )
}

@Composable
private fun SearchMessage(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
