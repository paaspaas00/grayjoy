// ==========================================================================
// Constants
// ==========================================================================

const PLATFORM = "NASA+";
const BASE_URL = "https://plus.nasa.gov";
const API_BASE_URL = "https://plus.nasa.gov/wp-json/wp/v2";
const CUSTOM_API_BASE_URL = "https://plus.nasa.gov/wp-json/nasaplus/v1";
const CHANNEL_DESCRIPTION = "NASA+ is NASA's on-demand streaming service. Watch original series, live coverage, and explore NASA's missions across air and space.";
const PLUGIN_ICON_URL = "https://plugins.grayjay.app/NASA-Plus/NASA-PlusIcon.png";

const REQUEST_HEADERS = {
    "Accept": "application/json",
    "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36"
};

const ITEMS_PER_PAGE = 20;
const MAX_PER_PAGE = 100;
const CACHE_TTL_MS = 2 * 60 * 60 * 1000;

Type.Feed.Playlists = "Playlists";

// ==========================================================================
// Regex Patterns
// ==========================================================================

/**
 * Matches NASA+ video page URLs.
 * @example https://plus.nasa.gov/video/moonbound-for-all-humanity/
 */
const VIDEO_URL_REGEX = /plus\.nasa\.gov\/video\/([^\/\?#]+)/;

/**
 * Matches NASA+ series page URLs.
 * @example https://plus.nasa.gov/series/moonbound/
 */
const SERIES_URL_REGEX = /plus\.nasa\.gov\/series\/([^\/\?#]+)/;

/**
 * Matches NASA+ topic page URLs.
 * @example https://plus.nasa.gov/topics/earth/
 */
const TOPIC_URL_REGEX = /plus\.nasa\.gov\/topics\/([^\/\?#]+)/;

/**
 * Matches NASA+ scheduled video/event page URLs.
 * @example https://plus.nasa.gov/scheduled-video/nasas-spacex-crew-12-launch/
 */
const SCHEDULED_VIDEO_URL_REGEX = /plus\.nasa\.gov\/scheduled-video\/([^\/\?#]+)/;

/**
 * Matches the NASA+ main channel URL.
 * @example https://plus.nasa.gov or https://plus.nasa.gov/
 */
const CHANNEL_URL_REGEX = /^https?:\/\/plus\.nasa\.gov\/?$/;

// ==========================================================================
// State
// ==========================================================================

let config = {};
let state = {
    topics: [],
    series: [],
    topicsFetchedAt: 0,
    seriesFetchedAt: 0
};

// ==========================================================================
// Source Functions
// ==========================================================================

/**
 * Initializes the plugin with config and restored state.
 * @param {SourceConfig} conf - Plugin configuration
 * @param {Object} sett - Unused plugin settings object
 * @param {string} savedStateStr - Previously saved state as JSON string
 */
source.enable = function (conf, sett, savedStateStr) {
    config = conf ?? {};

    if (savedStateStr) {
        try {
            const parsed = JSON.parse(savedStateStr);
            if (parsed.topics) {
                state.topics = parsed.topics;
            }

            if (parsed.series) {
                state.series = parsed.series;
            }

            if (parsed.topicsFetchedAt) {
                state.topicsFetchedAt = parsed.topicsFetchedAt;
            }

            if (parsed.seriesFetchedAt) {
                state.seriesFetchedAt = parsed.seriesFetchedAt;
            }
        } catch (e) {
            log("Failed to parse saved state: " + e.message);
        }
    }
};

/**
 * Returns the home feed with the most recent NASA+ videos.
 * @returns {NASAVideoPager}
 */
source.getHome = function () {
    return fetchMainChannelVideos();
};

/**
 * Checks if the given URL is a NASA+ channel URL (series or topic page).
 * @param {string} url
 * @returns {boolean}
 */
source.isChannelUrl = function (url) {
    return CHANNEL_URL_REGEX.test(url) || SERIES_URL_REGEX.test(url) || TOPIC_URL_REGEX.test(url);
};

/**
 * Returns channel information for a NASA+ series or topic.
 * @param {string} url - Channel URL
 * @returns {PlatformChannel}
 */
source.getChannel = function (url) {
    return getChannelForContext(resolveChannelContext(url));
};

/**
 * Returns the capabilities of a NASA+ channel.
 * @returns {Object}
 */
source.getChannelCapabilities = function () {
    return {
        types: [Type.Feed.Videos, Type.Feed.Playlists],
        sorts: [Type.Order.Chronological]
    };
};

/**
 * Returns channel contents (videos or playlists) for a series or topic.
 * @param {string} url - Channel URL
 * @param {string} type - Content type (Videos or Playlists)
 * @param {string} order - Sort order
 * @param {Object} filters - Content filters
 * @returns {NASAVideoPager|NASAPlaylistPager|NASATopicPlaylistPager}
 */
source.getChannelContents = function (url, type, order, filters) {
    const context = resolveChannelContext(url);
    if (type === Type.Feed.Playlists) {
        return getChannelPlaylistsForContext(context);
    }

    return getChannelVideosForContext(context);
};

/**
 * Returns playlists for the NASA+ channel (all series).
 * @param {string} url - Channel URL
 * @returns {NASAPlaylistPager|NASATopicPlaylistPager}
 */
source.getChannelPlaylists = function (url) {
    return getChannelPlaylistsForContext(resolveChannelContext(url));
};

/**
 * Checks if the given URL is a NASA+ playlist (series) URL.
 * @param {string} url
 * @returns {boolean}
 */
source.isPlaylistUrl = function (url) {
    return SERIES_URL_REGEX.test(url);
};

/**
 * Returns playlist details for a NASA+ series.
 * @param {string} url - Series URL
 * @returns {PlatformPlaylistDetails}
 */
source.getPlaylist = function (url) {
    const match = url.match(SERIES_URL_REGEX);
    if (!match) {
        throw new ScriptException("Invalid playlist URL: " + url);
    }

    const slug = match[1];
    const seriesObj = findSeriesBySlug(slug);

    if (!seriesObj) {
        throw new ScriptException("Series not found: " + slug);
    }

    const contentsPager = fetchVideosPage(1, { series: seriesObj.id, order: "asc" });

    return new PlatformPlaylistDetails({
        id: new PlatformID(PLATFORM, "series-" + slug, config.id),
        name: seriesObj.name,
        author: nasaAuthor(),
        datetime: Math.floor(Date.now() / 1000),
        url: BASE_URL + "/series/" + slug + "/",
        thumbnail: "",
        videoCount: seriesObj.count || -1,
        contents: contentsPager
    });
};

/**
 * Checks if the given URL points to a NASA+ video.
 * @param {string} url
 * @returns {boolean}
 */
source.isContentDetailsUrl = function (url) {
    return VIDEO_URL_REGEX.test(url) || SCHEDULED_VIDEO_URL_REGEX.test(url);
};

/**
 * Returns detailed content information for a specific NASA+ video.
 * @param {string} url - Video page URL
 * @returns {PlatformVideoDetails}
 */
source.getContentDetails = function (url) {
    const scheduledMatch = url.match(SCHEDULED_VIDEO_URL_REGEX);
    if (scheduledMatch) {
        const slug = scheduledMatch[1];
        const scheduledVideo = fetchScheduledVideoBySlug(slug);
        if (!scheduledVideo) {
            throw new ScriptException("Scheduled event not found: " + slug);
        }

        return buildScheduledVideoDetails(scheduledVideo);
    }

    const match = url.match(VIDEO_URL_REGEX);
    if (!match) {
        throw new ScriptException("Invalid video URL: " + url);
    }

    const slug = match[1];
    const video = fetchVideoBySlug(slug);
    if (!video) {
        throw new ScriptException("Video not found: " + slug);
    }

    return buildVideoDetails(video);
};

/**
 * Returns content recommendations for a NASA+ video.
 * Shows other videos from the same series, or latest videos if no series.
 * @param {string} url - Content URL
 * @returns {NASAVideoPager}
 */
source.getContentRecommendations = function (url) {
    const match = url.match(VIDEO_URL_REGEX);
    if (!match) {
        return createEmptyVideoPager();
    }

    const slug = match[1];
    const video = fetchVideoBySlug(slug);
    if (!video) {
        return createEmptyVideoPager();
    }

    const relatedVideos = buildRelatedVideos(video);

    const seriesIds = video.series;
    if (seriesIds && seriesIds.length > 0) {
        const seriesPage = fetchVideosPage(1, { series: seriesIds[0], exclude: video.id });
        const relatedVideoIds = new Set(relatedVideos.map(function (v) { return v.id.value; }));
        const deduped = seriesPage.results.filter(function (v) { return !relatedVideoIds.has(v.id.value); });
        const combined = relatedVideos.concat(deduped);
        return new NASAVideoPager(combined, seriesPage.hasMore, 1, { series: seriesIds[0], exclude: video.id });
    }

    if (relatedVideos.length > 0) {
        const latestPage = fetchVideosPage(1, { exclude: video.id });
        const existingRecommendationIds = new Set(relatedVideos.map(function (v) { return v.id.value; }));
        const deduped = latestPage.results.filter(function (v) { return !existingRecommendationIds.has(v.id.value); });
        const combined = relatedVideos.concat(deduped);
        return new NASAVideoPager(combined, latestPage.hasMore, 1, { exclude: video.id });
    }

    return fetchVideosPage(1, { exclude: video.id });
};

// ==========================================================================
// Search
// ==========================================================================

/**
 * Returns search capabilities.
 * @returns {ResultCapabilities}
 */
source.getSearchCapabilities = function () {
    return new ResultCapabilities(
        [Type.Feed.Videos],
        [Type.Order.Chronological],
        []
    );
};

/**
 * Searches for NASA+ videos.
 * @param {string} query - Search query
 * @param {string} type - Content type
 * @param {string} order - Sort order
 * @param {Object} filters - Search filters
 * @returns {NASASearchPager}
 */
source.search = function (query, type, order, filters) {
    return searchVideos(query, 1);
};

/**
 * Returns search channel contents capabilities.
 * @returns {ResultCapabilities}
 */
source.getSearchChannelContentsCapabilities = function () {
    return new ResultCapabilities(
        [Type.Feed.Videos],
        [Type.Order.Chronological],
        []
    );
};

/**
 * Searches for videos within a channel.
 * @param {string} channelUrl - Channel URL
 * @param {string} query - Search query
 * @param {string} type - Content type
 * @param {string} order - Sort order
 * @param {Object} filters - Search filters
 * @returns {NASASearchPager}
 */
source.searchChannelContents = function (channelUrl, query, type, order, filters) {
    const trimmed = (query || "").trim();
    if (trimmed.length === 0) {
        return new NASASearchPager([], false, trimmed, 1);
    }

    return searchChannelVideos(resolveChannelContext(channelUrl), trimmed);
};

/**
 * Returns search suggestions from the NASA+ API.
 * @param {string} query - Search query
 * @returns {string[]}
 */
source.searchSuggestions = function (query) {
    if (!query || query.trim().length === 0) {
        return [];
    }

    const resp = httpGET({
        url: CUSTOM_API_BASE_URL + "/searchSuggestions/" + encodeURIComponent(query),
        parseResponse: true
    });

    if (!resp.isOk || !resp.body || !Array.isArray(resp.body)) {
        return [];
    }

    return resp.body.map(function (item) { return item.name; });
};

/**
 * Searches for playlists (series) matching the query.
 * @param {string} query - Search query
 * @returns {NASAPlaylistPager}
 */
source.searchPlaylists = function (query) {
    if (!query || query.trim().length === 0) {
        return new NASAPlaylistPager([], false, []);
    }

    const lowerQuery = query.toLowerCase();
    const playlists = fetchAllSeries()
        .filter(function (s) { return s.count > 0 && taxonomyMatchesQuery(s, lowerQuery); })
        .sort(function (a, b) { return (b.count || 0) - (a.count || 0); })
        .map(function (s) { return buildSeriesPlaylist(s); });

    return new NASAPlaylistPager(playlists, false, []);
};

/**
 * Searches for channels (series and topics) matching the query.
 * @param {string} query - Search query
 * @returns {NASASeriesSearchPager}
 */
source.searchChannels = function (query) {
    if (!query || query.trim().length === 0) {
        return new NASASeriesSearchPager([], false);
    }

    const lowerQuery = query.toLowerCase();

    const seriesChannels = fetchAllSeries()
        .filter(function (s) { return s.count > 0 && taxonomyMatchesQuery(s, lowerQuery); })
        .map(function (s) { return buildSeriesChannelFromTaxonomy(s); });

    const topicChannels = fetchAllTopics()
        .filter(function (t) { return t.count > 0 && taxonomyMatchesQuery(t, lowerQuery); })
        .map(function (t) { return buildTopicChannelFromTaxonomy(t); });

    return new NASASeriesSearchPager(seriesChannels.concat(topicChannels), false);
};

/**
 * Serializes plugin state for persistence across sessions.
 * @returns {string} JSON string of the current state
 */
source.saveState = function () {
    if (IS_TESTING) {
        return "";
    }

    return JSON.stringify(state);
};

// ==========================================================================
// Pager Classes
// ==========================================================================

/**
 * Pager for NASA+ video listings with server-side pagination.
 * @extends VideoPager
 */
class NASAVideoPager extends VideoPager {
    /**
     * @param {PlatformVideo[]} results - Current page of video results
     * @param {boolean} hasMore - Whether more pages are available
     * @param {number} page - Current page number (1-based)
     * @param {Object} params - Query parameters for fetching next pages
     */
    constructor(results, hasMore, page, params) {
        super(results, hasMore);
        this.page = page;
        this.params = params;
    }

    nextPage() {
        return fetchVideosPage(this.page + 1, this.params);
    }
}

/**
 * Pager for NASA+ search results with server-side pagination.
 * @extends VideoPager
 */
class NASASearchPager extends VideoPager {
    /**
     * @param {PlatformVideo[]} results - Current page of search results
     * @param {boolean} hasMore - Whether more pages are available
     * @param {string} query - The search query
     * @param {number} page - Current page number (1-based)
     * @param {Object} [params] - Filter parameters (series, topic)
     */
    constructor(results, hasMore, query, page, params) {
        super(results, hasMore);
        this.query = query;
        this.page = page;
        this.params = params;
    }

    nextPage() {
        return searchVideos(this.query, this.page + 1, this.params);
    }
}

/**
 * Pager for channel search results (series and topics, non-paginated).
 * @extends ChannelPager
 */
class NASASeriesSearchPager extends ChannelPager {
    /**
     * @param {PlatformChannel[]} results - Series and topic channels matching the query
     * @param {boolean} hasMore - Whether more pages are available
     */
    constructor(results, hasMore) {
        super(results, hasMore);
    }

    nextPage() {
        return new NASASeriesSearchPager([], false);
    }
}

/**
 * Pager for series playlists with client-side pagination.
 * @extends PlaylistPager
 */
class NASAPlaylistPager extends PlaylistPager {
    /**
     * @param {PlatformPlaylist[]} results - Current page of playlist results
     * @param {boolean} hasMore - Whether more pages are available
     * @param {PlatformPlaylist[]} pendingItems - Pre-built playlists not yet returned
     */
    constructor(results, hasMore, pendingItems) {
        super(results, hasMore);
        this.pendingItems = pendingItems;
    }

    nextPage() {
        const page = this.pendingItems.slice(0, ITEMS_PER_PAGE);
        const remaining = this.pendingItems.slice(ITEMS_PER_PAGE);
        return new NASAPlaylistPager(page, remaining.length > 0, remaining);
    }
}

/**
 * Pager for topic-filtered series playlists, discovered incrementally from video pages.
 * @extends PlaylistPager
 */
class NASATopicPlaylistPager extends PlaylistPager {
    /**
     * @param {PlatformPlaylist[]} results - Playlists discovered on the current video page
     * @param {boolean} hasMore - Whether more video pages remain to scan
     * @param {number} topicId - Topic taxonomy ID
     * @param {number} videoPage - Current video page number
     * @param {Object} seenSeriesIds - Set-like object keyed by already-returned series IDs
     */
    constructor(results, hasMore, topicId, videoPage, seenSeriesIds) {
        super(results, hasMore);
        this.topicId = topicId;
        this.videoPage = videoPage;
        this.seenSeriesIds = seenSeriesIds;
    }

    nextPage() {
        return fetchTopicSeriesPage(this.topicId, this.videoPage + 1, this.seenSeriesIds);
    }
}

// ==========================================================================
// Helper Functions
// ==========================================================================

/**
 * Performs an HTTP GET request with default headers and error handling.
 * @param {Object} options - Request options
 * @param {string} options.url - The URL to fetch
 * @param {boolean} [options.parseResponse=false] - If true, parse response body as JSON
 * @returns {Object} Response object with isOk, code, body, and headers
 */
function httpGET(options) {
    const {
        url,
        parseResponse = false
    } = options;

    try {
        const response = http.GET(url, REQUEST_HEADERS, false);

        if (!response.isOk) {
            log("HTTP request failed: " + url + " (code: " + response.code + ")");
            return response;
        }

        if (parseResponse) {
            try {
                const parsed = JSON.parse(response.body);
                return {
                    code: response.code,
                    isOk: true,
                    body: parsed
                };
            } catch (e) {
                log("Failed to parse JSON from: " + url + " - " + e.message);
                return { isOk: false, code: response.code, body: null };
            }
        }

        return response;
    } catch (e) {
        log("HTTP request error: " + url + " - " + e.message);
        return { isOk: false, code: 0, body: "", headers: {} };
    }
}

/**
 * Resolves a channel URL into a reusable context object.
 * @param {string} url - Channel URL
 * @returns {Object} Channel context with kind and slug
 */
function resolveChannelContext(url) {
    if (CHANNEL_URL_REGEX.test(url)) {
        return { kind: "main", slug: "" };
    }

    const seriesMatch = url.match(SERIES_URL_REGEX);
    if (seriesMatch) {
        return { kind: "series", slug: seriesMatch[1] };
    }

    const topicMatch = url.match(TOPIC_URL_REGEX);
    if (topicMatch) {
        return { kind: "topic", slug: topicMatch[1] };
    }

    return { kind: "unknown", slug: "" };
}

/**
 * Resolves the taxonomy ID for a channel context.
 * @param {Object} context - Channel context
 * @returns {number|null} Taxonomy ID or null when unavailable
 */
function getChannelContextId(context) {
    if (context.kind === "series") {
        const series = findSeriesBySlug(context.slug);
        return series ? series.id : null;
    }

    if (context.kind === "topic") {
        return findTopicIdBySlug(context.slug);
    }

    return null;
}

/**
 * Creates an empty video pager with the given pagination state.
 * @param {number} [page=1] - Current page number
 * @param {Object} [params={}] - Pager query parameters
 * @returns {NASAVideoPager}
 */
function createEmptyVideoPager(page, params) {
    return new NASAVideoPager([], false, page || 1, params || {});
}

/**
 * Returns the channel object for a resolved channel context.
 * @param {Object} context - Channel context
 * @returns {PlatformChannel}
 */
function getChannelForContext(context) {
    if (context.kind === "series") {
        return getSeriesChannel(context.slug);
    }

    if (context.kind === "topic") {
        return getTopicChannel(context.slug);
    }

    return getNASAChannel();
}

/**
 * Returns the video feed for a resolved channel context.
 * @param {Object} context - Channel context
 * @returns {NASAVideoPager}
 */
function getChannelVideosForContext(context) {
    if (context.kind === "main") {
        return fetchMainChannelVideos();
    }

    if (context.kind === "series") {
        const seriesId = getChannelContextId(context);
        if (!seriesId) {
            return createEmptyVideoPager();
        }

        return fetchVideosPage(1, { series: seriesId });
    }

    if (context.kind === "topic") {
        const topicId = getChannelContextId(context);
        if (!topicId) {
            return createEmptyVideoPager();
        }

        return fetchVideosPage(1, { topic: topicId });
    }

    return fetchVideosPage(1, {});
}

/**
 * Returns the playlists feed for a resolved channel context.
 * @param {Object} context - Channel context
 * @returns {NASAPlaylistPager|NASATopicPlaylistPager}
 */
function getChannelPlaylistsForContext(context) {
    if (context.kind === "series") {
        return getSeriesAsPlaylist(context.slug);
    }

    if (context.kind === "topic") {
        return getTopicSeriesPlaylists(context.slug);
    }

    return getSeriesPlaylists();
}

/**
 * Searches for videos within a resolved channel context.
 * @param {Object} context - Channel context
 * @param {string} query - Search query
 * @returns {NASASearchPager}
 */
function searchChannelVideos(context, query) {
    if (context.kind === "series") {
        const seriesId = getChannelContextId(context);
        if (seriesId) {
            return searchVideos(query, 1, { series: seriesId });
        }
    }

    if (context.kind === "topic") {
        const topicId = getChannelContextId(context);
        if (topicId) {
            return searchVideos(query, 1, { topic: topicId });
        }
    }

    return searchVideos(query, 1);
}

/**
 * Returns a metadata value as a string.
 * @param {Object} item - WordPress REST API object
 * @param {string} key - Metadata key
 * @returns {string}
 */
function getMetaString(item, key) {
    const value = item.meta?.[key];
    if (value === undefined || value === null) {
        return "";
    }

    return String(value);
}

/**
 * Returns a metadata value parsed as an integer.
 * @param {Object} item - WordPress REST API object
 * @param {string} key - Metadata key
 * @returns {number}
 */
function getMetaNumber(item, key) {
    const parsed = parseInt(getMetaString(item, key) || "0", 10);
    if (isNaN(parsed)) {
        return 0;
    }

    return parsed;
}

/**
 * Builds the canonical video page URL for a video item.
 * @param {Object} item - WordPress video REST API object
 * @returns {string}
 */
function buildVideoPageUrl(item) {
    return item.link || (BASE_URL + "/video/" + item.slug + "/");
}

/**
 * Builds the canonical scheduled video page URL for an event item.
 * @param {Object} item - WordPress scheduled_video REST API object
 * @returns {string}
 */
function buildScheduledVideoPageUrl(item) {
    return item.link || (BASE_URL + "/scheduled-video/" + item.slug + "/");
}

/**
 * Returns the primary HLS URL for a video.
 * @param {Object} item - WordPress video REST API object
 * @returns {string}
 */
function getVideoStreamUrl(item) {
    return getMetaString(item, "video-url");
}

/**
 * Returns the preview HLS URL for a video.
 * @param {Object} item - WordPress video REST API object
 * @returns {string}
 */
function getPreviewStreamUrl(item) {
    return getMetaString(item, "preview-url");
}

/**
 * Builds a single HLS source object.
 * @param {string} name - Source label
 * @param {number} duration - Source duration in seconds
 * @param {string} url - HLS playlist URL
 * @returns {HLSSource}
 */
function buildHlsSource(name, duration, url) {
    return new HLSSource({
        name: name,
        duration: duration,
        url: url
    });
}

/**
 * Builds an on-demand video source descriptor with an optional fallback URL.
 * @param {string} primaryUrl - Primary HLS URL
 * @param {string} fallbackUrl - Fallback HLS URL
 * @param {number} duration - Source duration in seconds
 * @returns {VideoSourceDescriptor}
 */
function buildOnDemandVideoDescriptor(primaryUrl, fallbackUrl, duration) {
    const sourceUrl = primaryUrl || fallbackUrl;
    if (!sourceUrl) {
        return new VideoSourceDescriptor([]);
    }

    return new VideoSourceDescriptor([
        buildHlsSource("HLS", duration, sourceUrl)
    ]);
}

/**
 * Fetches a single video by slug.
 * @param {string} slug - Video slug
 * @returns {Object|null}
 */
function fetchVideoBySlug(slug) {
    const resp = httpGET({
        url: API_BASE_URL + "/video?slug=" + encodeURIComponent(slug),
        parseResponse: true
    });

    if (!resp.isOk || !resp.body || !Array.isArray(resp.body) || resp.body.length === 0) {
        return null;
    }

    return resp.body[0];
}

/**
 * Fetches a single scheduled video by slug.
 * @param {string} slug - Scheduled video slug
 * @returns {Object|null}
 */
function fetchScheduledVideoBySlug(slug) {
    const resp = httpGET({
        url: API_BASE_URL + "/scheduled_video?slug=" + encodeURIComponent(slug) + "&_embed=wp:featuredmedia",
        parseResponse: true
    });

    if (!resp.isOk || !resp.body || !Array.isArray(resp.body) || resp.body.length === 0) {
        return null;
    }

    return resp.body[0];
}

/**
 * Builds the shared display fields for a video item.
 * @param {Object} item - WordPress video REST API object
 * @returns {Object}
 */
function getVideoDisplayData(item) {
    return {
        id: new PlatformID(PLATFORM, String(item.id), config.id),
        title: extractTitle(item),
        thumbnails: buildThumbnails(item),
        uploadDate: parseDate(item.date_gmt || item.date),
        duration: getMetaNumber(item, "runtime"),
        url: buildVideoPageUrl(item)
    };
}

/**
 * Builds the shared display fields for a scheduled video item.
 * @param {Object} item - WordPress scheduled_video REST API object
 * @param {boolean} isLive - Whether the event is currently live
 * @returns {Object}
 */
function getScheduledDisplayData(item, isLive) {
    const startTime = getMetaNumber(item, "first_aired_date");
    const endTime = getMetaNumber(item, "end_aired_date");
    const eventDuration = (startTime > 0 && endTime > 0) ? endTime - startTime : 0;

    return {
        id: new PlatformID(PLATFORM, "scheduled-" + String(item.id), config.id),
        title: extractTitle(item),
        thumbnails: buildThumbnails({ featured_image: getScheduledThumbnailUrl(item) }),
        uploadDate: startTime,
        duration: isLive ? 0 : eventDuration,
        eventDuration: eventDuration,
        url: buildScheduledVideoPageUrl(item),
        startTime: startTime,
        endTime: endTime
    };
}

/**
 * Classifies a scheduled event relative to the current time.
 * @param {Object} item - WordPress scheduled_video REST API object
 * @param {number} [nowSeconds] - Current time in Unix seconds
 * @returns {Object}
 */
function getScheduledEventStatus(item, nowSeconds) {
    const currentTime = nowSeconds || Math.floor(Date.now() / 1000);
    const startTime = getMetaNumber(item, "first_aired_date");
    const endTime = getMetaNumber(item, "end_aired_date");
    const hasVideoUrl = getVideoStreamUrl(item).length > 0;
    const isLive = startTime > 0 && currentTime >= startTime && (endTime === 0 || currentTime <= endTime);
    const isUpcoming = startTime > 0 && currentTime < startTime;
    const isPast = startTime > 0 && endTime > 0 && currentTime > endTime;

    return {
        startTime: startTime,
        endTime: endTime,
        hasVideoUrl: hasVideoUrl,
        isLive: isLive,
        isUpcoming: isUpcoming,
        isPast: isPast
    };
}

/**
 * Fetches the main channel feed: scheduled events merged with regular videos.
 * Scheduled events are capped at ITEMS_PER_PAGE (single non-paginated request) since NASA only schedules a handful at a time.
 * @returns {NASAVideoPager}
 */
function fetchMainChannelVideos() {
    const scheduledUrl = API_BASE_URL + "/scheduled_video?per_page=" + ITEMS_PER_PAGE + "&orderby=date&order=desc&_embed=wp:featuredmedia";
    const videosUrl = API_BASE_URL + "/video?per_page=" + ITEMS_PER_PAGE + "&page=1&orderby=date&order=desc&_envelope=1";

    const batchResults = http.batch()
        .GET(scheduledUrl, REQUEST_HEADERS, false)
        .GET(videosUrl, REQUEST_HEADERS, false)
        .execute();

    const liveVideos = parseScheduledResponse(batchResults[0]);
    const regularPage = parseVideosResponse(batchResults[1], 1, {});

    if (liveVideos.length > 0) {
        const combined = liveVideos.concat(regularPage.results);
        return new NASAVideoPager(combined, regularPage.hasMore, 1, {});
    }

    return regularPage;
}

/**
 * Fetches a page of videos from the WordPress REST API.
 * @param {number} page - Page number (1-based)
 * @param {Object} params - Query parameters (series, topic, exclude)
 * @returns {NASAVideoPager}
 */
function fetchVideosPage(page, params) {
    const sortOrder = params.order || "desc";
    let url = API_BASE_URL + "/video?per_page=" + ITEMS_PER_PAGE
        + "&page=" + page + "&orderby=date&order=" + sortOrder + "&_envelope=1";

    if (params.series) {
        url += "&series=" + params.series;
    }

    if (params.topic) {
        url += "&topic=" + params.topic;
    }

    if (params.exclude) {
        url += "&exclude[]=" + params.exclude;
    }

    const resp = httpGET({ url: url });
    return parseVideosResponse(resp, page, params);
}

/**
 * Reads X-WP-TotalPages from an envelope, defaulting to 1 when unavailable.
 * @param {Object} envelope - Parsed envelope with shape { body, status, headers }
 * @returns {number}
 */
function getEnvelopeTotalPages(envelope) {
    if (!envelope || !envelope.headers) {
        return 1;
    }

    const headers = envelope.headers;
    const matchingKey = Object.keys(headers).find(function (k) {
        return k.toLowerCase() === "x-wp-totalpages";
    });
    if (!matchingKey) {
        return 1;
    }

    const parsed = parseInt(headers[matchingKey], 10);
    return isNaN(parsed) || parsed < 1 ? 1 : parsed;
}

/**
 * Extracts items and total page count from a WordPress _envelope=1 response.
 *
 * The _envelope global parameter (https://developer.wordpress.org/rest-api/using-the-rest-api/global-parameters/#_envelope)
 * embeds the original response headers in the JSON body, bypassing the Grayjay header whitelist that would otherwise drop X-WP-TotalPages.
 * Accepts both pre-parsed (parseResponse: true) and raw (string body) responses.
 *
 * @param {Object} resp - HTTP response carrying an envelope-wrapped body
 * @returns {{items: Array|null, totalPages: number}}
 */
function unwrapEnvelope(resp) {
    let envelope = resp ? resp.body : null;
    if (typeof envelope === "string") {
        try {
            envelope = JSON.parse(envelope);
        } catch (e) {
            log("Failed to parse envelope: " + e.message);
            return { items: null, totalPages: 1 };
        }
    }

    const items = envelope && Array.isArray(envelope.body) ? envelope.body : null;
    return { items: items, totalPages: getEnvelopeTotalPages(envelope) };
}

/**
 * Parses a raw HTTP response (with _envelope=1) into a NASAVideoPager.
 * @param {Object} resp - Raw HTTP response carrying an envelope-wrapped body
 * @param {number} page - Current page number
 * @param {Object} params - Query parameters for pagination
 * @returns {NASAVideoPager}
 */
function parseVideosResponse(resp, page, params) {
    if (!resp.isOk) {
        log("HTTP request failed (videos): code " + resp.code);
        return createEmptyVideoPager(page, params);
    }

    const unwrapped = unwrapEnvelope(resp);
    if (!unwrapped.items) {
        return createEmptyVideoPager(page, params);
    }

    const videos = unwrapped.items.map(function (item) { return buildPlatformVideo(item); });
    return new NASAVideoPager(videos, page < unwrapped.totalPages, page, params);
}

/**
 * Searches for videos using the WordPress REST API search parameter.
 * @param {string} query - Search query
 * @param {number} page - Page number (1-based)
 * @param {Object} [params] - Filter parameters (series, topic)
 * @returns {NASASearchPager}
 */
function searchVideos(query, page, params) {
    params = params || {};

    if (!query || query.trim().length === 0) {
        return new NASASearchPager([], false, query, page, params);
    }

    let url = API_BASE_URL + "/video?per_page=" + ITEMS_PER_PAGE
        + "&page=" + page + "&search=" + encodeURIComponent(query)
        + "&orderby=relevance&order=desc&_envelope=1";

    if (params.series) {
        url += "&series=" + params.series;
    }

    if (params.topic) {
        url += "&topic=" + params.topic;
    }

    const resp = httpGET({ url: url, parseResponse: true });
    if (!resp.isOk) {
        return new NASASearchPager([], false, query, page, params);
    }

    const unwrapped = unwrapEnvelope(resp);
    if (!unwrapped.items) {
        return new NASASearchPager([], false, query, page, params);
    }

    const videos = unwrapped.items.map(function (item) { return buildPlatformVideo(item); });
    return new NASASearchPager(videos, page < unwrapped.totalPages, query, page, params);
}

/**
 * Builds a PlatformVideo from a WordPress video object.
 * @param {Object} item - WordPress video REST API object
 * @returns {PlatformVideo}
 */
function buildPlatformVideo(item) {
    const videoData = getVideoDisplayData(item);

    return new PlatformVideo({
        id: videoData.id,
        name: videoData.title,
        thumbnails: videoData.thumbnails,
        author: nasaAuthor(),
        datetime: videoData.uploadDate,
        duration: videoData.duration,
        viewCount: 0,
        url: videoData.url,
        isLive: false
    });
}

/**
 * Builds a PlatformVideoDetails from a WordPress video object.
 * @param {Object} item - WordPress video REST API object
 * @returns {PlatformVideoDetails}
 */
function buildVideoDetails(item) {
    const videoData = getVideoDisplayData(item);
    const description = buildDescription(item);

    const fields = {
        id: videoData.id,
        name: videoData.title,
        thumbnails: videoData.thumbnails,
        author: nasaAuthor(),
        datetime: videoData.uploadDate,
        duration: videoData.duration,
        viewCount: 0,
        url: videoData.url,
        isLive: false,
        description: description,
        video: buildOnDemandVideoDescriptor(getVideoStreamUrl(item), getPreviewStreamUrl(item), videoData.duration)
    };

    const result = new PlatformVideoDetails(fields);
    result.getContentRecommendations = function () {
        return source.getContentRecommendations(videoData.url);
    };

    return result;
}

/**
 * Extracts plain text content from a WordPress item's content field.
 * @param {Object} item - WordPress REST API object
 * @returns {string} Plain text content
 */
function extractContent(item) {
    const rawContent = typeof item.content === "object" ? (item.content.rendered || "") : (item.content || "");
    return decodeHtmlEntities(rawContent)
        .replace(/<\/p\s*>|<br\s*\/?>/gi, "\n")
        .replace(/<[^>]*>/g, "")
        .replace(/\n{3,}/g, "\n\n")
        .trim();
}

/**
 * Builds a description string from video metadata.
 * @param {Object} item - WordPress video REST API object
 * @returns {string}
 */
function buildDescription(item) {
    const parts = [];

    const content = extractContent(item);
    if (content) {
        parts.push(content);
    }

    appendNonEmptyLines(parts, [
        buildLabelValueLine("Rating", getMetaString(item, "rating")),
        buildEpisodeLine(item),
        buildLabelValueLine("Featuring", getMetaString(item, "featuring")),
        buildLabelValueLine("Narrated by", getMetaString(item, "narrated-by")),
        buildLabelValueLine("Language", getMetaString(item, "language") || getMetaString(item, "Language"))
    ]);

    const primarySeriesLine = buildPrimarySeriesLine(item.series);
    if (primarySeriesLine) {
        parts.push("");
        parts.push(primarySeriesLine);
    }

    return parts.join("\n");
}

/**
 * Appends all non-empty lines to a description parts array.
 * @param {string[]} parts - Target description parts
 * @param {string[]} lines - Optional lines to append
 */
function appendNonEmptyLines(parts, lines) {
    for (let i = 0; i < lines.length; i++) {
        if (lines[i]) {
            parts.push(lines[i]);
        }
    }
}

/**
 * Builds a "Label: Value" line when a value is present.
 * @param {string} label - Display label
 * @param {string} value - Display value
 * @returns {string}
 */
function buildLabelValueLine(label, value) {
    if (!value) {
        return "";
    }

    return label + ": " + value;
}

/**
 * Builds a season/episode line from video metadata.
 * @param {Object} item - WordPress video REST API object
 * @returns {string}
 */
function buildEpisodeLine(item) {
    const episode = getMetaString(item, "episode-num");
    const season = getMetaString(item, "season");
    if (season && episode) {
        return "Season " + season + ", Episode " + episode;
    }

    if (episode) {
        return "Episode " + episode;
    }

    return "";
}

/**
 * Builds the first matching series link line for a video.
 * @param {number[]} seriesIds - Series taxonomy IDs
 * @returns {string}
 */
function buildPrimarySeriesLine(seriesIds) {
    if (!seriesIds || seriesIds.length === 0) {
        return "";
    }

    const allSeries = fetchAllSeries();
    for (let i = 0; i < allSeries.length; i++) {
        if (allSeries[i].id === seriesIds[0]) {
            return allSeries[i].name + " - " + BASE_URL + "/series/" + allSeries[i].slug + "/";
        }
    }

    return "";
}

/**
 * Builds a Thumbnails object from a video item.
 * @param {Object} item - WordPress video REST API object
 * @returns {Thumbnails}
 */
function buildThumbnails(item) {
    const imageUrl = item.featured_image || "";
    if (!imageUrl) {
        return new Thumbnails([]);
    }

    return new Thumbnails([new Thumbnail(imageUrl, 0)]);
}

/**
 * Parses an ISO 8601 date string to a Unix timestamp in seconds.
 * @param {string} dateStr - ISO 8601 date string
 * @returns {number} Unix timestamp in seconds, or 0 if parsing fails
 */
function parseDate(dateStr) {
    if (!dateStr) {
        return 0;
    }

    const normalized = dateStr.replace(" ", "T");
    const hasTimezone = /[Zz]|[+-]\d{2}:?\d{2}$/.test(normalized);
    const ts = new Date(hasTimezone ? normalized : normalized + "Z").getTime();
    if (isNaN(ts)) {
        log("parseDate: failed to parse date string: " + dateStr);
        return 0;
    }

    return Math.floor(ts / 1000);
}

/**
 * Extracts the title string from a WordPress video object.
 * Handles both string and {rendered: "..."} formats.
 * @param {Object} item - WordPress video REST API object
 * @returns {string} Decoded title string
 */
function extractTitle(item) {
    const raw = typeof item.title === "object" ? (item.title.rendered || "") : (item.title || "");
    return decodeHtmlEntities(raw);
}

/**
 * Decodes HTML entities in a string.
 * @param {string} text - Text with HTML entities
 * @returns {string} Decoded text
 */
function decodeHtmlEntities(text) {
    if (!text) {
        return "";
    }

    return text
        .replace(/&#(\d+);/g, function (match, dec) { return String.fromCharCode(dec); })
        .replace(/&#x([0-9a-fA-F]+);/g, function (match, hex) { return String.fromCharCode(parseInt(hex, 16)); })
        .replace(/&amp;/g, "&")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&quot;/g, '"')
        .replace(/&#039;/g, "'")
        .replace(/&apos;/g, "'")
        .replace(/&nbsp;/g, " ")
        .replace(/&mdash;/g, "—")
        .replace(/&ndash;/g, "–")
        .replace(/&hellip;/g, "…");
}

/**
 * Creates a PlatformAuthorLink for the NASA+ channel.
 * @returns {PlatformAuthorLink}
 */
function nasaAuthor() {
    return new PlatformAuthorLink(
        new PlatformID(PLATFORM, "nasa-plus", config.id),
        PLATFORM,
        BASE_URL,
        PLUGIN_ICON_URL
    );
}

/**
 * Returns the main NASA+ channel.
 * @returns {PlatformChannel}
 */
function getNASAChannel() {
    return new PlatformChannel({
        id: new PlatformID(PLATFORM, "nasa-plus", config.id),
        name: PLATFORM,
        thumbnail: PLUGIN_ICON_URL,
        banner: "",
        subscribers: 0,
        description: CHANNEL_DESCRIPTION,
        url: BASE_URL,
        links: {
            "Website": "https://www.nasa.gov",
            "YouTube": "https://www.youtube.com/nasa",
            "X": "https://x.com/nasa",
            "Instagram": "https://www.instagram.com/nasa/",
            "Facebook": "https://www.facebook.com/NASA/",
            "Twitch": "https://www.twitch.tv/nasa"
        }
    });
}

/**
 * Returns a channel for a NASA+ series.
 * @param {string} slug - Series slug
 * @returns {PlatformChannel}
 */
function getSeriesChannel(slug) {
    const series = findSeriesBySlug(slug);
    return buildSeriesChannelFromTaxonomy(series || { slug: slug, name: slug, description: "" });
}

/**
 * Builds a PlatformChannel from a series taxonomy object.
 * @param {Object} series - WordPress taxonomy term object
 * @returns {PlatformChannel}
 */
function buildSeriesChannelFromTaxonomy(series) {
    return new PlatformChannel({
        id: new PlatformID(PLATFORM, "series-" + series.slug, config.id),
        name: series.name,
        thumbnail: PLUGIN_ICON_URL,
        banner: "",
        subscribers: 0,
        description: series.description || "",
        url: BASE_URL + "/series/" + series.slug + "/",
        links: {}
    });
}

/**
 * Builds a PlatformChannel from a topic taxonomy object.
 * @param {Object} topic - WordPress taxonomy term object
 * @returns {PlatformChannel}
 */
function buildTopicChannelFromTaxonomy(topic) {
    return new PlatformChannel({
        id: new PlatformID(PLATFORM, "topic-" + topic.slug, config.id),
        name: topic.name,
        thumbnail: PLUGIN_ICON_URL,
        banner: "",
        subscribers: 0,
        description: topic.description || "",
        url: BASE_URL + "/topics/" + topic.slug + "/",
        links: {}
    });
}

/**
 * Case-insensitive match against a taxonomy term's name or description.
 * @param {Object} item - Taxonomy term with name and optional description
 * @param {string} lowerQuery - Pre-lowercased query string
 * @returns {boolean}
 */
function taxonomyMatchesQuery(item, lowerQuery) {
    return item.name.toLowerCase().indexOf(lowerQuery) !== -1 ||
        (item.description && item.description.toLowerCase().indexOf(lowerQuery) !== -1);
}

/**
 * Returns a channel for a NASA+ topic.
 * @param {string} slug - Topic slug
 * @returns {PlatformChannel}
 */
function getTopicChannel(slug) {
    const allTopics = fetchAllTopics();
    let topic = null;
    for (let i = 0; i < allTopics.length; i++) {
        if (allTopics[i].slug === slug) {
            topic = allTopics[i];
            break;
        }
    }

    return buildTopicChannelFromTaxonomy(topic || { slug: slug, name: slug, description: "" });
}

/**
 * Fetches all series taxonomy terms with in-memory caching.
 * @returns {Object[]} Array of series taxonomy objects
 */
function fetchAllSeries() {
    if (state.series.length > 0 && (Date.now() - state.seriesFetchedAt < CACHE_TTL_MS)) {
        return state.series;
    }

    const resp = httpGET({ url: API_BASE_URL + "/series?per_page=" + MAX_PER_PAGE, parseResponse: true });
    if (!resp.isOk || !resp.body || !Array.isArray(resp.body)) {
        return state.series;
    }

    state.series = resp.body;
    state.seriesFetchedAt = Date.now();
    return state.series;
}

/**
 * Fetches all topic taxonomy terms with in-memory caching.
 * @returns {Object[]} Array of topic taxonomy objects
 */
function fetchAllTopics() {
    if (state.topics.length > 0 && (Date.now() - state.topicsFetchedAt < CACHE_TTL_MS)) {
        return state.topics;
    }

    const resp = httpGET({ url: API_BASE_URL + "/topic?per_page=" + MAX_PER_PAGE, parseResponse: true });
    if (!resp.isOk || !resp.body || !Array.isArray(resp.body)) {
        return state.topics;
    }

    state.topics = resp.body;
    state.topicsFetchedAt = Date.now();
    return state.topics;
}

/**
 * Finds a series taxonomy object by its slug.
 * @param {string} slug - Series slug
 * @returns {Object|null} Series taxonomy object or null if not found
 */
function findSeriesBySlug(slug) {
    const allSeries = fetchAllSeries();
    for (let i = 0; i < allSeries.length; i++) {
        if (allSeries[i].slug === slug) {
            return allSeries[i];
        }
    }

    return null;
}

/**
 * Finds a topic taxonomy ID by its slug.
 * @param {string} slug - Topic slug
 * @returns {number|null} Topic ID or null if not found
 */
function findTopicIdBySlug(slug) {
    const allTopics = fetchAllTopics();
    for (let i = 0; i < allTopics.length; i++) {
        if (allTopics[i].slug === slug) {
            return allTopics[i].id;
        }
    }

    return null;
}

/**
 * Returns a single playlist for a series channel.
 * @param {string} slug - Series slug
 * @returns {NASAPlaylistPager}
 */
function getSeriesAsPlaylist(slug) {
    const series = findSeriesBySlug(slug);
    if (series) {
        return new NASAPlaylistPager([buildSeriesPlaylist(series)], false, []);
    }

    return new NASAPlaylistPager([], false, []);
}

/**
 * Returns all series as playlists, sorted by video count descending.
 * @returns {NASAPlaylistPager}
 */
function getSeriesPlaylists() {
    const allSeries = fetchAllSeries();
    const sorted = allSeries.slice().sort(function (a, b) { return (b.count || 0) - (a.count || 0); });
    const playlists = sorted
        .filter(function (s) { return s.count > 0; })
        .map(function (s) { return buildSeriesPlaylist(s); });

    const firstPage = playlists.slice(0, ITEMS_PER_PAGE);
    const remaining = playlists.slice(ITEMS_PER_PAGE);
    return new NASAPlaylistPager(firstPage, remaining.length > 0, remaining);
}

/**
 * Returns series playlists filtered by topic.
 * Fetches videos for the topic to discover which series they belong to.
 * @param {string} topicSlug - Topic slug
 * @returns {NASATopicPlaylistPager}
 */
function getTopicSeriesPlaylists(topicSlug) {
    const topicId = findTopicIdBySlug(topicSlug);
    if (!topicId) {
        return new NASATopicPlaylistPager([], false, topicId, 1, {});
    }
    return fetchTopicSeriesPage(topicId, 1, {});
}

/**
 * Fetches one page of videos for a topic, extracts new series, and returns them as playlists.
 * Carries forward already-seen series IDs to avoid duplicates across pages.
 * @param {number} topicId - Topic taxonomy ID
 * @param {number} page - Current video page (1-based)
 * @param {Object} seenSeriesIds - Set-like object keyed by already-returned series IDs
 * @returns {NASATopicPlaylistPager}
 */
function fetchTopicSeriesPage(topicId, page, seenSeriesIds) {
    const resp = httpGET({
        url: API_BASE_URL + "/video?per_page=" + MAX_PER_PAGE + "&page=" + page + "&topic=" + topicId + "&_envelope=1",
        parseResponse: true
    });

    if (!resp.isOk) {
        return new NASATopicPlaylistPager([], false, topicId, page, seenSeriesIds);
    }

    const unwrapped = unwrapEnvelope(resp);
    if (!unwrapped.items || unwrapped.items.length === 0) {
        return new NASATopicPlaylistPager([], false, topicId, page, seenSeriesIds);
    }

    const newlyAdded = {};
    for (let i = 0; i < unwrapped.items.length; i++) {
        const seriesIds = unwrapped.items[i].series;
        if (seriesIds) {
            for (let j = 0; j < seriesIds.length; j++) {
                const id = seriesIds[j];
                if (!seenSeriesIds[id]) {
                    seenSeriesIds[id] = true;
                    newlyAdded[id] = true;
                }
            }
        }
    }

    const playlists = fetchAllSeries()
        .filter(function (s) { return newlyAdded[s.id] && s.count > 0; })
        .sort(function (a, b) { return (b.count || 0) - (a.count || 0); })
        .map(function (s) { return buildSeriesPlaylist(s); });

    const hasMoreVideoPages = page < unwrapped.totalPages;
    return new NASATopicPlaylistPager(playlists, hasMoreVideoPages, topicId, page, seenSeriesIds);
}

/**
 * Builds a PlatformPlaylist from a series taxonomy object.
 * @param {Object} series - WordPress series taxonomy term
 * @returns {PlatformPlaylist}
 */
function buildSeriesPlaylist(series) {
    return new PlatformPlaylist({
        id: new PlatformID(PLATFORM, "series-" + series.slug, config.id),
        name: series.name,
        author: nasaAuthor(),
        datetime: Math.floor(Date.now() / 1000),
        thumbnail: PLUGIN_ICON_URL,
        url: BASE_URL + "/series/" + series.slug + "/",
        videoCount: series.count || -1
    });
}

/**
 * Parses a raw HTTP response into an array of scheduled PlatformVideo objects.
 * Sorted: live events first, upcoming events second, past events third.
 * @param {Object} resp - Raw HTTP response
 * @returns {PlatformVideo[]}
 */
function parseScheduledResponse(resp) {
    if (!resp.isOk) {
        log("HTTP request failed (scheduled): code " + resp.code);
        return [];
    }

    let items;
    try {
        items = JSON.parse(resp.body);
    } catch (e) {
        log("Failed to parse scheduled response: " + e.message);
        return [];
    }

    if (!Array.isArray(items)) {
        return [];
    }

    const nowSeconds = Math.floor(Date.now() / 1000);
    const entries = [];

    for (let i = 0; i < items.length; i++) {
        const item = items[i];
        const status = getScheduledEventStatus(item, nowSeconds);
        const shouldInclude = status.isLive || status.isUpcoming || (status.isPast && status.hasVideoUrl);

        if (shouldInclude) {
            entries.push({
                video: buildScheduledPlatformVideo(item, status),
                status: status
            });
        }
    }

    entries.sort(function (a, b) {
        if (a.status.isLive && !b.status.isLive) {
            return -1;
        }

        if (!a.status.isLive && b.status.isLive) {
            return 1;
        }

        if (a.status.isUpcoming && !b.status.isUpcoming) {
            return -1;
        }

        if (!a.status.isUpcoming && b.status.isUpcoming) {
            return 1;
        }

        if (a.status.isUpcoming && b.status.isUpcoming) {
            return a.status.startTime - b.status.startTime;
        }

        if (a.status.isPast && !b.status.isPast) {
            return 1;
        }

        if (!a.status.isPast && b.status.isPast) {
            return -1;
        }

        if (a.status.isPast && b.status.isPast) {
            return b.status.endTime - a.status.endTime;
        }

        return (a.video.datetime || 0) - (b.video.datetime || 0);
    });

    return entries.map(function (entry) { return entry.video; });
}

/**
 * Extracts the thumbnail URL from a scheduled video's embedded featured media.
 * @param {Object} item - WordPress scheduled_video REST API object with _embedded
 * @returns {string} Thumbnail URL or empty string
 */
function getScheduledThumbnailUrl(item) {
    return item._embedded?.["wp:featuredmedia"]?.[0]?.source_url || "";
}

/**
 * Builds a PlatformVideo from a scheduled video object.
 * @param {Object} item - WordPress scheduled_video REST API object
 * @param {Object} status - Scheduled event status object
 * @returns {PlatformVideo}
 */
function buildScheduledPlatformVideo(item, status) {
    const scheduledData = getScheduledDisplayData(item, status.isLive);

    return new PlatformVideo({
        id: scheduledData.id,
        name: scheduledData.title,
        thumbnails: scheduledData.thumbnails,
        author: nasaAuthor(),
        datetime: scheduledData.uploadDate,
        duration: scheduledData.duration,
        viewCount: 0,
        url: scheduledData.url,
        isLive: status.isLive
    });
}

/**
 * Builds a PlatformVideoDetails from a scheduled video object.
 * @param {Object} item - WordPress scheduled_video REST API object
 * @returns {PlatformVideoDetails}
 */
function buildScheduledVideoDetails(item) {
    const status = getScheduledEventStatus(item);
    const videoUrl = getVideoStreamUrl(item);
    const scheduledData = getScheduledDisplayData(item, status.isLive);

    const fields = {
        id: scheduledData.id,
        name: scheduledData.title,
        thumbnails: scheduledData.thumbnails,
        author: nasaAuthor(),
        datetime: scheduledData.uploadDate,
        duration: scheduledData.duration,
        viewCount: 0,
        url: scheduledData.url,
        isLive: status.isLive,
        description: extractContent(item),
        video: new VideoSourceDescriptor([])
    };

    if (status.isLive && videoUrl) {
        fields.live = buildHlsSource("Live", 0, videoUrl);
    } else if (videoUrl) {
        fields.video = buildOnDemandVideoDescriptor(videoUrl, "", scheduledData.eventDuration);
    }

    return new PlatformVideoDetails(fields);
}

/**
 * Builds PlatformVideo objects from a video's related_videos array.
 * @param {Object} video - WordPress video REST API object with related_videos
 * @returns {PlatformVideo[]}
 */
function buildRelatedVideos(video) {
    if (!video.related_videos || !Array.isArray(video.related_videos)) {
        return [];
    }

    return video.related_videos
        .filter(function (rv) { return rv.ID !== video.id && rv.permalink; })
        .map(function (rv) {
            const runtime = getMetaNumber(rv, "runtime");
            const title = decodeHtmlEntities(rv.post_title || "");
            const relatedThumbnailUrl = getMetaString(rv, "featured-image");

            return new PlatformVideo({
                id: new PlatformID(PLATFORM, String(rv.ID), config.id),
                name: title,
                thumbnails: relatedThumbnailUrl
                    ? new Thumbnails([new Thumbnail(relatedThumbnailUrl, 0)])
                    : new Thumbnails([]),
                author: nasaAuthor(),
                datetime: parseDate(rv.post_date_gmt),
                duration: runtime,
                viewCount: 0,
                url: rv.permalink,
                isLive: false
            });
        });
}

log("loaded");
