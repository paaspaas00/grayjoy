package com.futo.platformplayer.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class YoutubeSubscriptionFetchMode { Fast, Complete }

/** Full YouTube content adapter backed by NewPipeExtractor and no V8 runtime. */
class NewPipeYoutubeContentBackend(
    private val initializeNewPipe: () -> Unit,
) {
    private val service: StreamingService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        initializeNewPipe()
        ServiceList.YouTube
    }
    private val httpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder().build()
    }
    private val pagers = ConcurrentHashMap<String, PagerSession>()
    private val channelTabs = ConcurrentHashMap<String, List<ListLinkHandler>>()
    private val commentHandles = ConcurrentHashMap<String, CommentHandle>()

    suspend fun search(query: String, type: GrayjaySearchType): GrayjayPluginSearchResult =
        withContext(Dispatchers.IO) {
            val filter = when (type) {
                GrayjaySearchType.Videos -> YoutubeSearchQueryHandlerFactory.VIDEOS
                GrayjaySearchType.Creators -> YoutubeSearchQueryHandlerFactory.CHANNELS
                GrayjaySearchType.Playlists -> YoutubeSearchQueryHandlerFactory.PLAYLISTS
            }
            val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
            val extractor = service.getSearchExtractor(handler)
            extractor.fetchPage()
            val page = extractor.initialPage
            page.toSearchResult(
                registerPager(
                    kind = PagerKind.Search,
                    nextPage = page.nextPage,
                    loader = extractor::getPage,
                ),
            )
        }

    suspend fun loadMoreSearch(continuationId: String): GrayjayPluginSearchResult =
        loadPage(continuationId).toSearchResult()

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        service.suggestionExtractor.suggestionList(query).distinct().take(20)
    }

    suspend fun loadTrending(liveOnly: Boolean = false): GrayjayVideoPage =
        withContext(Dispatchers.IO) {
            val extractor = service.kioskList.defaultKioskExtractor
            extractor.fetchPage()
            val page = extractor.initialPage
            val items = page.items.filterIsInstance<StreamInfoItem>()
                .filter { !liveOnly || it.isLiveStream() }
            GrayjayVideoPage(
                videos = items.map { it.toGrayjaySearchItem() },
                continuationId = registerPager(
                    kind = PagerKind.Kiosk,
                    nextPage = page.nextPage,
                    loader = extractor::getPage,
                    liveOnly = liveOnly,
                ),
                hasMore = Page.isValid(page.nextPage),
            )
        }

    suspend fun loadMoreVideos(continuationId: String): GrayjayVideoPage =
        loadPage(continuationId).toVideoPage()

    suspend fun subscriptionFeed(
        requests: List<GrayjayChannelRequest>,
        mode: YoutubeSubscriptionFetchMode,
        onProgress: (Int, Int) -> Unit,
        perChannelLimit: Int = 5,
        resultLimit: Int = 80,
    ): GrayjayVideoPage = withContext(Dispatchers.IO) {
        if (requests.isEmpty()) return@withContext GrayjayVideoPage()
        onProgress(0, requests.size)
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val slots = Semaphore(if (mode == YoutubeSubscriptionFetchMode.Fast) 6 else 3)
        val pages = coroutineScope {
            requests.map { request ->
                async {
                    val videos = slots.withPermit {
                        runCatching {
                            when (mode) {
                                YoutubeSubscriptionFetchMode.Fast ->
                                    loadAtomFeed(request).take(perChannelLimit)
                                YoutubeSubscriptionFetchMode.Complete ->
                                    loadCompleteChannelFeed(request).take(perChannelLimit)
                            }
                        }.getOrDefault(emptyList())
                    }
                    onProgress(completed.incrementAndGet(), requests.size)
                    videos
                }
            }.awaitAll()
        }
        GrayjayVideoPage(
            videos = pages.flatten()
                .distinctBy(GrayjaySearchItem::url)
                .sortedByDescending(GrayjaySearchItem::datetime)
                .take(resultLimit),
        )
    }

    suspend fun loadChannel(
        sourceId: String,
        channelUrl: String,
        videoLimit: Int = 30,
    ): GrayjayChannelDetails = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(service, channelUrl)
        val canonicalUrl = info.url.ifBlank { channelUrl }
        channelTabs[canonicalUrl] = info.tabs
        channelTabs[channelUrl] = info.tabs
        val videosHandler = info.tabs.firstOrNull { it.contentFilter() == ChannelTabs.VIDEOS }
        val firstPage = videosHandler?.let(::loadChannelTabFirstPage)
        GrayjayChannelDetails(
            url = canonicalUrl,
            name = info.name,
            thumbnailUrl = info.avatars.bestImageUrl(),
            bannerUrl = info.banners.bestImageUrl(),
            subscribers = info.subscriberCount,
            description = cleanNewPipeText(info.description),
            links = emptyMap(),
            videos = firstPage?.items
                ?.filterIsInstance<StreamInfoItem>()
                ?.take(videoLimit)
                ?.map { it.toGrayjaySearchItem() }
                .orEmpty(),
            continuationId = firstPage?.continuationId,
            hasMore = firstPage?.hasMore == true,
            supportsShorts = info.tabs.any { it.contentFilter() == ChannelTabs.SHORTS },
            supportsPlaylists = info.tabs.any { it.contentFilter() == ChannelTabs.PLAYLISTS },
            liveContentType = ChannelTabs.LIVESTREAMS.takeIf {
                info.tabs.any { handler -> handler.contentFilter() == ChannelTabs.LIVESTREAMS }
            },
            supportsPopularSort = false,
        )
    }

    suspend fun loadChannelPage(
        channelUrl: String,
        type: String,
    ): GrayjayChannelPage = withContext(Dispatchers.IO) {
        val handlers = channelTabs[channelUrl] ?: ChannelInfo.getInfo(service, channelUrl)
            .tabs
            .also { channelTabs[channelUrl] = it }
        val normalized = type.toNewPipeChannelTab()
        val handler = handlers.firstOrNull { it.contentFilter() == normalized }
            ?: return@withContext GrayjayChannelPage()
        val page = loadChannelTabFirstPage(handler)
        page.toChannelPage()
    }

    suspend fun loadMoreChannelPage(continuationId: String): GrayjayChannelPage =
        loadPage(continuationId).toChannelPage()

    suspend fun loadPlaylist(
        sourceId: String,
        playlistUrl: String,
        videoLimit: Int = 30,
    ): GrayjayPlaylistDetails = withContext(Dispatchers.IO) {
        val info = PlaylistInfo.getInfo(service, playlistUrl)
        val videos = info.relatedItems.take(videoLimit).map { it.toGrayjaySearchItem() }
        val continuationId = registerPager(
            kind = PagerKind.Playlist,
            nextPage = info.nextPage,
            loader = { page -> PlaylistInfo.getMoreItems(service, info.url, page) },
        )
        GrayjayPlaylistDetails(
            playlist = GrayjaySearchPlaylist(
                id = info.id,
                url = info.url.ifBlank { playlistUrl },
                sourceId = sourceId,
                pluginId = YOUTUBE_PLUGIN_ID,
                title = info.name,
                author = info.uploaderName.orEmpty(),
                thumbnailUrl = info.thumbnails.bestImageUrl(),
                videoCount = info.streamCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            ),
            videos = videos,
            continuationId = continuationId,
            hasMore = Page.isValid(info.nextPage),
        )
    }

    suspend fun loadMorePlaylist(continuationId: String): GrayjayPlaylistDetails {
        val page = loadPage(continuationId)
        return GrayjayPlaylistDetails(
            playlist = GrayjaySearchPlaylist(
                id = "",
                url = "",
                sourceId = YOUTUBE_SOURCE_ID,
                pluginId = YOUTUBE_PLUGIN_ID,
                title = "",
                author = "",
                thumbnailUrl = null,
                videoCount = 0,
            ),
            videos = page.items.filterIsInstance<StreamInfoItem>()
                .map { it.toGrayjaySearchItem() },
            continuationId = page.continuationId,
            hasMore = page.hasMore,
        )
    }

    suspend fun routeUrl(url: String): GrayjayUrlRoute? = withContext(Dispatchers.IO) {
        val kind = when (service.getLinkTypeByUrl(url)) {
            StreamingService.LinkType.STREAM -> GrayjayUrlKind.Video
            StreamingService.LinkType.CHANNEL -> GrayjayUrlKind.Channel
            StreamingService.LinkType.PLAYLIST -> GrayjayUrlKind.Playlist
            else -> return@withContext null
        }
        GrayjayUrlRoute(YOUTUBE_SOURCE_ID, YOUTUBE_PLUGIN_ID, kind)
    }

    suspend fun loadExtras(contentUrl: String): GrayjayContentExtras = withContext(Dispatchers.IO) {
        val streamInfo = StreamInfo.getInfo(service, contentUrl)
        val recommendations = streamInfo.relatedStreams
            .filterIsInstance<StreamInfoItem>()
            .map { it.toGrayjaySearchItem() }
        val commentsInfo = runCatching { CommentsInfo.getInfo(service, contentUrl) }.getOrNull()
        val comments = commentsInfo?.relatedItems.orEmpty().map { it.toGrayjayComment(contentUrl) }
        GrayjayContentExtras(
            recommendations = recommendations,
            comments = comments,
            recommendationsAvailable = recommendations.isNotEmpty(),
            commentsAvailable = commentsInfo?.isCommentsDisabled == false,
            commentsContinuationId = commentsInfo?.nextPage?.let { page ->
                registerPager(
                    kind = PagerKind.Comments,
                    nextPage = page,
                    loader = { next -> CommentsInfo.getMoreItems(service, contentUrl, next) },
                )
            },
            hasMoreComments = Page.isValid(commentsInfo?.nextPage),
        )
    }

    suspend fun loadMoreComments(continuationId: String): GrayjayCommentPage {
        val page = loadPage(continuationId)
        return GrayjayCommentPage(
            comments = page.items.filterIsInstance<CommentsInfoItem>()
                .map { it.toGrayjayComment("") },
            continuationId = page.continuationId,
            hasMore = page.hasMore,
        )
    }

    suspend fun loadCommentReplies(commentId: String): GrayjayCommentPage =
        withContext(Dispatchers.IO) {
            val handle = commentHandles[commentId] ?: return@withContext GrayjayCommentPage()
            val extractor = service.getCommentsExtractor(handle.contentUrl)
            val page = extractor.getPage(handle.repliesPage)
            val nextId = registerPager(
                kind = PagerKind.Comments,
                nextPage = page.nextPage,
                loader = extractor::getPage,
            )
            GrayjayCommentPage(
                comments = page.items.map { it.toGrayjayComment(handle.contentUrl) },
                continuationId = nextId,
                hasMore = Page.isValid(page.nextPage),
            )
        }

    private fun loadChannelTabFirstPage(handler: ListLinkHandler): LoadedPage {
        val extractor = service.getChannelTabExtractor(handler)
        extractor.fetchPage()
        val page = extractor.initialPage
        return LoadedPage(
            items = page.items,
            continuationId = registerPager(
                kind = if (handler.contentFilter() == ChannelTabs.PLAYLISTS) {
                    PagerKind.ChannelPlaylists
                } else {
                    PagerKind.ChannelVideos
                },
                nextPage = page.nextPage,
                loader = extractor::getPage,
            ),
            hasMore = Page.isValid(page.nextPage),
        )
    }

    private suspend fun loadPage(continuationId: String): LoadedPage =
        withContext(Dispatchers.IO) {
            val session = pagers[continuationId] ?: return@withContext LoadedPage()
            val next = session.nextPage ?: return@withContext LoadedPage()
            val page = session.loader(next)
            session.nextPage = page.nextPage
            if (!Page.isValid(page.nextPage)) pagers.remove(continuationId)
            LoadedPage(
                kind = session.kind,
                items = if (session.liveOnly) {
                    page.items.filterIsInstance<StreamInfoItem>().filter { it.isLiveStream() }
                } else {
                    page.items
                },
                continuationId = continuationId.takeIf { Page.isValid(page.nextPage) },
                hasMore = Page.isValid(page.nextPage),
            )
        }

    private fun registerPager(
        kind: PagerKind,
        nextPage: Page?,
        loader: (Page) -> ListExtractor.InfoItemsPage<out InfoItem>,
        liveOnly: Boolean = false,
    ): String? {
        if (!Page.isValid(nextPage)) return null
        val id = "np:${UUID.randomUUID()}"
        pagers[id] = PagerSession(kind, nextPage, loader, liveOnly)
        return id
    }

    private fun ListExtractor.InfoItemsPage<out InfoItem>.toSearchResult(
        continuationId: String? = registerPager(
            kind = PagerKind.Search,
            nextPage = nextPage,
            loader = { ListExtractor.InfoItemsPage.emptyPage() },
        ),
    ): GrayjayPluginSearchResult = GrayjayPluginSearchResult(
        videos = items.filterIsInstance<StreamInfoItem>().map { it.toGrayjaySearchItem() },
        channels = items.filterIsInstance<ChannelInfoItem>().map { it.toGrayjayChannel() },
        playlists = items.filterIsInstance<PlaylistInfoItem>().map { it.toGrayjayPlaylist() },
        continuationId = continuationId,
        hasMore = continuationId != null,
    )

    private fun LoadedPage.toSearchResult() = GrayjayPluginSearchResult(
        videos = items.filterIsInstance<StreamInfoItem>().map { it.toGrayjaySearchItem() },
        channels = items.filterIsInstance<ChannelInfoItem>().map { it.toGrayjayChannel() },
        playlists = items.filterIsInstance<PlaylistInfoItem>().map { it.toGrayjayPlaylist() },
        continuationId = continuationId,
        hasMore = hasMore,
    )

    private fun LoadedPage.toVideoPage() = GrayjayVideoPage(
        videos = items.filterIsInstance<StreamInfoItem>().map { it.toGrayjaySearchItem() },
        continuationId = continuationId,
        hasMore = hasMore,
    )

    private fun LoadedPage.toChannelPage() = GrayjayChannelPage(
        videos = items.filterIsInstance<StreamInfoItem>().map { it.toGrayjaySearchItem() },
        playlists = items.filterIsInstance<PlaylistInfoItem>().map { it.toGrayjayPlaylist() },
        continuationId = continuationId,
        hasMore = hasMore,
    )

    private fun StreamInfoItem.toGrayjaySearchItem() = GrayjaySearchItem(
        id = url,
        url = url,
        sourceId = YOUTUBE_SOURCE_ID,
        pluginId = YOUTUBE_PLUGIN_ID,
        title = name,
        authorName = uploaderName.orEmpty(),
        authorUrl = uploaderUrl.orEmpty(),
        authorThumbnailUrl = uploaderAvatars.bestImageUrl(),
        thumbnailUrl = thumbnails.bestImageUrl(),
        durationSeconds = duration.coerceAtLeast(0L),
        viewCount = viewCount.coerceAtLeast(0L),
        datetime = uploadDate?.offsetDateTime(),
        isLive = isLiveStream(),
    )

    private fun ChannelInfoItem.toGrayjayChannel() = GrayjaySearchChannel(
        id = url,
        url = url,
        sourceId = YOUTUBE_SOURCE_ID,
        pluginId = YOUTUBE_PLUGIN_ID,
        name = name,
        thumbnailUrl = thumbnails.bestImageUrl(),
        subscribers = subscriberCount.takeIf { it >= 0L },
    )

    private fun PlaylistInfoItem.toGrayjayPlaylist() = GrayjaySearchPlaylist(
        id = url,
        url = url,
        sourceId = YOUTUBE_SOURCE_ID,
        pluginId = YOUTUBE_PLUGIN_ID,
        title = name,
        author = uploaderName.orEmpty(),
        thumbnailUrl = thumbnails.bestImageUrl(),
        videoCount = streamCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
    )

    private fun CommentsInfoItem.toGrayjayComment(contentUrl: String): GrayjayComment {
        val rawId = commentId.takeIf(String::isNotBlank)
            ?: url.takeIf(String::isNotBlank)
            ?: "${name.hashCode()}:${commentText.content.hashCode()}"
        val id = "np-comment:$rawId"
        replies?.let { commentHandles[id] = CommentHandle(contentUrl, it) }
        return GrayjayComment(
            id = id,
            author = uploaderName.orEmpty(),
            authorThumbnailUrl = uploaderAvatars.bestImageUrl(),
            message = cleanNewPipeDescription(commentText),
            age = textualUploadDate.orEmpty(),
            likeCount = likeCount.takeIf { it >= 0 }?.toLong(),
            replyCount = replyCount.takeIf { it >= 0 },
        )
    }

    private fun StreamInfoItem.isLiveStream(): Boolean =
        streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM

    private fun ListLinkHandler.contentFilter(): String =
        contentFilters.firstOrNull().orEmpty().lowercase(Locale.ROOT)

    private fun String.toNewPipeChannelTab(): String = when (lowercase(Locale.ROOT)) {
        "shorts" -> ChannelTabs.SHORTS
        "live", "streams", "livestreams" -> ChannelTabs.LIVESTREAMS
        "playlists", "__grayjoy_playlists__" -> ChannelTabs.PLAYLISTS
        else -> ChannelTabs.VIDEOS
    }

    private fun loadCompleteChannelFeed(request: GrayjayChannelRequest): List<GrayjaySearchItem> {
        val info = ChannelInfo.getInfo(service, request.url)
        val handler = info.tabs.firstOrNull { it.contentFilter() == ChannelTabs.VIDEOS }
            ?: return emptyList()
        val extractor = service.getChannelTabExtractor(handler)
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { it.toGrayjaySearchItem() }
    }

    private fun loadAtomFeed(request: GrayjayChannelRequest): List<GrayjaySearchItem> {
        val channelId = youtubeChannelId(request.url) ?: return emptyList()
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        val response = httpClient.newCall(Request.Builder().url(feedUrl).get().build()).execute()
        response.use {
            if (!it.isSuccessful) return emptyList()
            return parseAtomFeed(it.body.string(), channelId, request)
        }
    }

    private fun parseAtomFeed(
        xml: String,
        channelId: String,
        request: GrayjayChannelRequest,
    ): List<GrayjaySearchItem> {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(xml))
        }
        val videos = mutableListOf<GrayjaySearchItem>()
        var inEntry = false
        var inAuthor = false
        var videoId = ""
        var title = ""
        var authorName = ""
        var authorUrl = ""
        var videoUrl = ""
        var thumbnailUrl: String? = null
        var published: OffsetDateTime? = null
        var views = 0L
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = true; videoId = ""; title = ""; authorName = ""
                        authorUrl = ""; videoUrl = ""; thumbnailUrl = null
                        published = null; views = 0L
                    }
                    "author" -> if (inEntry) inAuthor = true
                    "videoId" -> if (inEntry) videoId = parser.nextText().trim()
                    "title" -> if (inEntry) title = parser.nextText().trim()
                    "name" -> if (inEntry && inAuthor) authorName = parser.nextText().trim()
                    "uri" -> if (inEntry && inAuthor) authorUrl = parser.nextText().trim()
                    "published" -> if (inEntry) {
                        published = runCatching { OffsetDateTime.parse(parser.nextText().trim()) }.getOrNull()
                    }
                    "link" -> if (inEntry && parser.getAttributeValue(null, "rel") == "alternate") {
                        videoUrl = parser.getAttributeValue(null, "href").orEmpty()
                    }
                    "thumbnail" -> if (inEntry) thumbnailUrl = parser.getAttributeValue(null, "url")
                    "statistics" -> if (inEntry) {
                        views = parser.getAttributeValue(null, "views")?.toLongOrNull() ?: 0L
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "author" -> inAuthor = false
                    "entry" -> {
                        inEntry = false
                        val url = videoUrl.ifBlank {
                            videoId.takeIf(String::isNotBlank)
                                ?.let { "https://www.youtube.com/watch?v=$it" }.orEmpty()
                        }
                        if (url.isNotBlank() && title.isNotBlank()) {
                            videos += GrayjaySearchItem(
                                id = url,
                                url = url,
                                sourceId = requestSourceId,
                                pluginId = YOUTUBE_PLUGIN_ID,
                                title = title,
                                authorName = authorName.ifBlank { request.name },
                                authorUrl = authorUrl.ifBlank {
                                    "https://www.youtube.com/channel/$channelId"
                                },
                                authorThumbnailUrl = request.thumbnailUrl.takeIf(String::isNotBlank),
                                thumbnailUrl = thumbnailUrl,
                                durationSeconds = 0L,
                                viewCount = views,
                                datetime = published,
                                isLive = false,
                            )
                        }
                    }
                }
            }
            parser.next()
        }
        return videos
    }

    private fun youtubeChannelId(url: String): String? = Regex(
        "(?:channel/|channel_id=)(UC[A-Za-z0-9_-]{20,})",
    ).find(url)?.groupValues?.getOrNull(1)

    private data class PagerSession(
        val kind: PagerKind,
        var nextPage: Page?,
        val loader: (Page) -> ListExtractor.InfoItemsPage<out InfoItem>,
        val liveOnly: Boolean,
    )

    private enum class PagerKind { Search, Kiosk, ChannelVideos, ChannelPlaylists, Playlist, Comments }

    private data class LoadedPage(
        val kind: PagerKind? = null,
        val items: List<InfoItem> = emptyList(),
        val continuationId: String? = null,
        val hasMore: Boolean = false,
    )

    private data class CommentHandle(val contentUrl: String, val repliesPage: Page)

    private data class ChannelTabPage(
        val items: List<InfoItem>,
        val continuationId: String?,
        val hasMore: Boolean,
    )

    private val requestSourceId: String get() = YOUTUBE_SOURCE_ID

    companion object {
        const val YOUTUBE_SOURCE_ID = "youtube"
        const val YOUTUBE_PLUGIN_ID = "youtube"
    }
}

internal fun cleanNewPipeDescription(description: Description?): String = when (description?.type) {
    Description.HTML -> cleanNewPipeHtml(description.content)
    Description.MARKDOWN, Description.PLAIN_TEXT -> description.content
    null -> ""
    else -> description.content
}.trim()

internal fun cleanNewPipeText(value: String?): String {
    val text = value.orEmpty()
    return if (Regex("<[/a-zA-Z][^>]*>").containsMatchIn(text)) cleanNewPipeHtml(text) else text.trim()
}

private fun cleanNewPipeHtml(html: String): String {
    val document = Jsoup.parseBodyFragment(html)
    document.select("br").after("\\n")
    document.select("li").forEach { item -> item.prepend("• ") }
    document.select("p,div,li,h1,h2,h3,h4,blockquote").forEach { element ->
        element.before("\\n")
        element.after("\\n")
    }
    document.select("a[href]").forEach { link ->
        val href = link.attr("href").trim()
        if (href.isNotBlank() && !link.text().contains(href)) link.after(" ($href)")
    }
    return document.wholeText()
        .replace("\\n", "\n")
        .lineSequence()
        .map(String::trim)
        .fold(mutableListOf<String>()) { lines, line ->
            if (line.isNotBlank() || lines.lastOrNull()?.isNotBlank() == true) lines += line
            lines
        }
        .joinToString("\n")
        .trim()
}

private fun List<Image>?.bestImageUrl(): String? = this
    ?.filter { it.url.isNotBlank() }
    ?.maxByOrNull { image -> image.width.coerceAtLeast(1).toLong() * image.height.coerceAtLeast(1) }
    ?.url
