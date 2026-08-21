/**
 * TED Talks Plugin for Grayjay
 * 
 * This plugin enables browsing, searching, and viewing TED Talk videos.
 * It interfaces with TED's GraphQL API and web interface to provide content.
 */

// ---------------------- Constants ----------------------
const PLATFORM = {
    NAME: 'TEDTalks',
    BASE_URL: 'https://www.ted.com',
    TALKS_URL: 'https://www.ted.com/talks',
    GRAPHQL_URL: 'https://www.ted.com/graphql',
    SEARCH_URL: 'https://zenith-prod-alt.ted.com/api/search',
    SPEAKER_URL: 'https://www.ted.com/speakers',
    PLAYLIST_URL: 'https://www.ted.com/playlists',
    FALLBACK_AVATAR: 'https://plugins.grayjay.app/TedTalks/media/speaker.png'
};

// Regular expressions for URL matching
const REGEX = {
    SPEAKER_URL: /^https?:\/\/(www\.)?ted\.com\/speakers\/[^\/\s]+(\?.*)?$/i,
    TALK_URL: /^https?:\/\/(www\.)?ted\.com\/talks\/[^\/\s]+(\?.*)?$/i,
    PLAYLIST_URL: /^https?:\/\/(www\.)?ted\.com\/playlists\/\d+(\/[a-zA-Z0-9_-]+)?$/i,
    TOPIC_URL: /^https?:\/\/(www\.)?ted\.com\/topics\/[a-zA-Z0-9_+-]+$/i,
    TOPIC_SLUG: /ted\.com\/topics\/([a-zA-Z0-9_+-]+)/i,
    PLAYLIST_ID_SLUG: /ted\.com\/playlists\/(\d+)(?:\/([a-zA-Z0-9_-]+))?/i,
    SPEAKER_SLUG: /ted\.com\/speakers\/([a-zA-Z0-9_-]+)(?:\?|$)/,
    SPEAKER_ID: /speakerId=(\d+)/,
    TALK_SLUG: /ted\.com\/talks\/([^?#]+)/i,
    HLS_URL: /https:\/\/.*\.m3u8/i,
    MPEG_URL: /https:\/\/.*\.mp4/i,
    HLS_PROJECT_ID: /project_masters\/(\d+)\//i,
    HLS_INTRO_ID: /intro_master_id=(\d+)/i
};

// Default request headers for TED API
const DEFAULT_HEADERS = {
    'Content-Type': 'application/json',
    Origin: PLATFORM.BASE_URL,
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0'
};

// Video quality settings
const VIDEO_QUALITY = {
    LOW: {
        width: 320,
        height: 180,
        name: 'Low - video/mp4'
    },
    MEDIUM: {
        width: 854,
        height: 480,
        name: 'Medium - video/mp4'
    },
    HIGH: {
        width: 1920,
        height: 1080,
        name: 'High - video/mp4'
    }
};

// Hits requested when recovering a speaker's talks from the search index (API caps this at 24)
const SPEAKER_TALKS_SEARCH_LIMIT = 24;

// Bounds the fallback search so an ambiguous speaker name cannot walk the whole index
const SPEAKER_TALKS_MAX_PAGES = 5;

// Client errors that are worth another attempt: a timeout or an early-data rejection can succeed
// on retry, and 429 is paced. 429 is a safety net only: ~6000 requests at up to 100 concurrency
// never produced one, since a CDN absorbs the load ahead of TED's origin.
const HTTP_TOO_MANY_REQUESTS = 429;
const HTTP_RETRYABLE_CLIENT_ERRORS = [408, 425, HTTP_TOO_MANY_REQUESTS];
const HTTP_RETRY_BASE_DELAY_MS = 1000;

// Bot blocking presents as 403, which a different TLS fingerprint can clear
const HTTP_FORBIDDEN = 403;

// TED reports a missing id as a 404 carried inside the GraphQL error payload, not as an empty result
const HTTP_NOT_FOUND = 404;

// VIDEOS_INFO_BY_SLUG returns at most this many nodes per request
const VIDEO_DETAILS_BATCH_SIZE = 50;

// Plugin configuration storage
let config = {};

// Plugin settings storage
let _settings = {
    showExternalContent: true,
    useBrowserImpersonation: true,
    verboseNotifications: false
};

const IS_ANDROID = bridge.buildPlatform === "android";
const IS_DESKTOP = bridge.buildPlatform === "desktop";

// Android intercepts any plugin_type ending in "Details" inside JSContentPager feeds
// (IJSContent.fromV8) and VideoDetailFragment reuses it without calling getContentDetails.
// It advertises no capability for this, so the platform itself is the signal.
const DETAILS_IN_FEED_ANDROID = true;
// Desktop reuses feed details only once its pagers stamp a refPager BackendUrl, and it
// advertises no capability to detect that, so this stays off until a version floor exists.
const DETAILS_IN_FEED_DESKTOP = false;
const DETAILS_IN_FEED = IS_DESKTOP ? DETAILS_IN_FEED_DESKTOP : DETAILS_IN_FEED_ANDROID;

const IMPERSONATION_TARGET = IS_DESKTOP ? 'chrome136' : 'chrome131_android';
const IS_IMPERSONATION_AVAILABLE = (typeof httpimp !== 'undefined');

// ====================== PLUGIN ENTRY POINTS ======================

/**
 * Initialize the plugin with configuration
 */
source.enable = (conf, settings, saveStateStr) => {
    config = conf;
    if (settings) {
        _settings.showExternalContent = settings.showExternalContent === true || settings.showExternalContent === "true";
        _settings.useBrowserImpersonation = settings.useBrowserImpersonation !== false && settings.useBrowserImpersonation !== "false";
        _settings.verboseNotifications = settings.verboseNotifications === true || settings.verboseNotifications === "true";
    }
    if (IS_IMPERSONATION_AVAILABLE) {
        const httpImpClient = httpimp.getDefaultClient(true);
        if (httpImpClient.setDefaultImpersonateTarget) {
            httpImpClient.setDefaultImpersonateTarget(IMPERSONATION_TARGET);
        }
    }
};

/**
 * Get home page content
 * @returns {ContentPager} Paged results for home page
 */
source.getHome = () => {
    // 1. GraphQL: POST to www.ted.com/graphql with HOME_VIDEOS query.
    //    Reliable, proper cursor-based pagination, no SSL issues.
    try {
        const pager = new TedTalksHomePager();
        if (!pager.loadFailed) {
            return pager;
        }
        trace('HomePager failed: Home feed unavailable via GraphQL.');
    } catch (error) {
        trace(`HomePager failed: ${error.message}`);
    }

    // 2. Search API: POST to zenith-prod-alt.ted.com/api/search.
    //    This endpoint requests SSL/TLS renegotiation which can cause intermittent failures.
    try {
        const pager = new TedTalksSearchPager("");
        if (!pager.loadFailed) {
            return pager;
        }
        trace('SearchPager failed: TED search is unavailable.');
    } catch (error) {
        trace(`SearchPager failed: ${error.message}`);
    }

    // 3. Web scraping: fetches /talks?sort=newest HTML and parses __NEXT_DATA__.
    //    Last resort. Only returns a single page (no pagination) but works on some devices/networks
    //    where the other approaches fail.
    try {
        return new TedTalksWebPager();
    } catch (error) {
        trace(`All home pagers failed: ${error.message}`);
        return createEmptyPager();
    }
};

/**
 * Search for TED Talks
 * @param {string} query Search query
 * @returns {ContentPager} Paged results for search
 */
source.search = (query) => {
    return new TedTalksSearchPager(query);
};

/**
 * Search for TED Speakers
 * @param {string} query Search query
 * @returns {ContentPager} Paged results for search
 */
source.searchChannels = (query) => {
    return searchSpeakers(query);
};

/**
 * Check if a URL is a TED speaker channel
 * @param {string} url URL to check
 * @returns {boolean} True if the URL is a TED speaker channel
 */
source.isChannelUrl = (url) => {
    return urlMatches(url, REGEX.SPEAKER_URL);
};

/**
 * Get content from a TED speaker channel
 * @param {string} url Speaker channel URL
 * @returns {ContentPager} Paged results for channel content
 */
source.getChannelContents = (url) => {
    return getChannelContentsByUrl(url);
};

/**
 * Get content from a TED speaker channel
 * @param {string} url Speaker channel URL
 * @returns {ContentPager} Paged results for channel content
 */
source.getChannel = (url) => {
    return getChannelByUrl(url);
};

/**
 * Check if a URL is a TED Talk content page
 * @param {string} url URL to check
 * @returns {boolean} True if the URL is a TED Talk content page
 */
source.isContentDetailsUrl = (url) => {
    return urlMatches(url, REGEX.TALK_URL);
};

/**
 * Get detailed content information for a TED Talk
 * @param {string} url TED Talk URL
 * @returns {PlatformVideoDetails} Video details
 */
source.getContentDetails = (url) => {
    return getVideoDetailsByUrl(url);
};

/**
 * Get recommended content based on a TED Talk
 * @param {string} url TED Talk URL
 * @returns {ContentPager} Paged results with recommended content
 */
source.getContentRecommendations = (url, relatedVideos) => {
    return getVideoRecommendations(url, relatedVideos);
};

/**
 * Check if a URL is a TED playlist or topic
 * @param {string} url URL to check
 * @returns {boolean} True if the URL is a TED playlist or topic
 */
source.isPlaylistUrl = (url) => {
    return urlMatches(url, REGEX.PLAYLIST_URL) || urlMatches(url, REGEX.TOPIC_URL);
};

/**
 * Get playlist details from a TED playlist or topic URL
 * @param {string} url Playlist or topic URL
 * @returns {PlatformPlaylistDetails} Playlist details
 */
source.getPlaylist = (url) => {
    // Check if this is a topic URL
    if (urlMatches(url, REGEX.TOPIC_URL)) {
        return getTopicAsPlaylist(url);
    }
    return getPlaylistByUrl(url);
};

// ====================== CUSTOM PAGER IMPLEMENTATIONS ======================
/**
 * Custom pager implementation for TED Talks home page
 */
class TedTalksHomePager extends ContentPager {
    /**
     * Creates a new TED Talks home pager
     */
    constructor() {
        super([], true);
        this.cursor = "0";
        this.loadFailed = false;
        this.nextPage();
    }

    /**
     * Fetches the next page of results
     * @returns {ContentPager} Pager with the current page results
     */
    nextPage() {
        this.loadFailed = false;
        try {
            const queryInfo = {
                query: GQL_QUERIES.HOME_VIDEOS,
                variables: {
                    first: 20,
                    after: this.cursor
                }
            };

            const response = makeRequestWithRetry('POST', PLATFORM.GRAPHQL_URL, JSON.stringify(queryInfo), DEFAULT_HEADERS);

            if (!response.isOk) {
                trace(`GraphQL request failed for home videos. Status: ${response.code}`);
                this.loadFailed = true;
                this.results = [];
                this.hasMore = false;
                return this;
            }

            const responseData = safeJsonParse(response.body);
            if (!responseData?.data?.videos?.nodes) {
                trace('Invalid response format for home videos');
                this.loadFailed = true;
                this.results = [];
                this.hasMore = false;
                return this;
            }

            const videos = responseData.data.videos.nodes
                .filter(video => shouldShowContent(video, {}))
                .map(video => convertToFeedEntry(video, {}));

            this.cursor = responseData.data.videos.pageInfo.endCursor;
            this.results = videos;
            this.hasMore = responseData.data.videos.pageInfo.hasNextPage;

            return this;
        } catch (error) {
            trace(`Exception in TedTalksHomePager.nextPage: ${error.message}`);
            this.loadFailed = true;
            this.results = [];
            this.hasMore = false;
            return this;
        }
    }
}

/**
 * Last resort pager that fetches talks from TED's web page (__NEXT_DATA__).
 * Returns a single page only (TED's __NEXT_DATA__ ignores the page parameter).
 * Used when both GraphQL and Search API fail on some devices/networks.
 */
class TedTalksWebPager extends ContentPager {
    constructor() {
        super([], true);
        this.nextPage();
    }

    nextPage() {
        try {
            const url = `${PLATFORM.TALKS_URL}?sort=newest`;
            const response = makeRequestWithRetry('GET', url, null, { 'User-Agent': DEFAULT_HEADERS["User-Agent"] });

            if (!response.isOk) {
                trace(`Web page request failed. Status: ${response.code}`);
                this.results = [];
                this.hasMore = false;
                return this;
            }

            const talksData = extractNextDataFromHtml(response.body, 'talks');
            if (!talksData) {
                trace('Could not extract talks from __NEXT_DATA__');
                this.results = [];
                this.hasMore = false;
                return this;
            }

            // talksData is an object with numeric keys ("0", "1", ...) not a standard array
            // (verified via Playwright browser testing)
            const talksArray = Array.isArray(talksData)
                ? talksData
                : Object.keys(talksData)
                    .filter(k => !isNaN(k))
                    .sort((a, b) => parseInt(a) - parseInt(b))
                    .map(k => talksData[k]);

            if (talksArray.length === 0) {
                this.results = [];
                this.hasMore = false;
                return this;
            }

            // Search-style speaker strings are supported by extractSpeakerInfo.
            const normalizedTalks = talksArray.map(talk => ({
                ...talk,
                speakers: talk.presenterDisplayName || 'TED'
            }));

            const slugs = normalizedTalks.map(t => t.slug).filter(Boolean);
            const extraMetadata = slugs.length > 0 ? (getVideoDetailsBySlugList(slugs) || {}) : {};

            const videos = normalizedTalks
                .filter(talk => shouldShowContent(talk, extraMetadata[talk.slug] || {}))
                .map(talk => convertToFeedEntry(talk, extraMetadata[talk.slug] || {}));

            this.results = videos;
            this.hasMore = false;

            return this;
        } catch (error) {
            trace(`Exception in TedTalksWebPager.nextPage: ${error.message}`);
            throw error;
        }
    }
}

/**
 * Custom pager implementation for TED Talks search results
 */
class TedTalksSearchPager extends ContentPager {
    /**
     * Creates a new TED Talks search pager
     * @param {string} query Search query
     */
    constructor(query) {
        super([], true);
        this.query = query;
        this.currentPage = 0;
        this.hitsPerPage = 24;
        this.loadFailed = false;
        this.nextPage();
    }

    /**
     * Fetches the next page of results
     * @returns {ContentPager} Pager with the current page results
     */
    nextPage() {
        this.loadFailed = false;
        try {
            const searchParams = {
                "attributeForDistinct": "objectID",
                "distinct": 1,
                "facets": ["subtitle_languages", "tags"],
                "highlightPostTag": "__/ais-highlight__",
                "highlightPreTag": "__ais-highlight__",
                "hitsPerPage": this.hitsPerPage,
                "maxValuesPerFacet": 500,
                "page": this.currentPage,
                "query": this.query
            };

            const request = [{ "indexName": "newest", "params": searchParams }];
            const response = makeRequestWithRetry('POST', PLATFORM.SEARCH_URL, JSON.stringify(request), DEFAULT_HEADERS);

            if (!response.isOk) {
                trace(`Search API request failed. Status: ${response.code}, Query: "${this.query}", Page: ${this.currentPage}`);
                this.loadFailed = true;
                this.results = [];
                this.hasMore = false;
                return this;
            }

            const searchResults = safeJsonParse(response.body)?.results?.[0];
            if (!searchResults?.hits) {
                trace(`Invalid search response format for query: "${this.query}", Page: ${this.currentPage}`);
                this.loadFailed = true;
                this.results = [];
                this.hasMore = false;
                return this;
            }

            const hits = searchResults.hits;
            if (hits.length === 0) {
                this.results = [];
                this.hasMore = false;
                return this;
            }

            const slugs = hits.map(hit => hit.slug).filter(Boolean);

            const extraMetadata = getVideoDetailsBySlugList(slugs) || {};
            const videos = hits
                .filter(hit => shouldShowContent(hit, extraMetadata[hit.slug] || {}))
                .map(hit => convertToFeedEntry(hit, extraMetadata[hit.slug] || {}));

            const totalHits = searchResults.nbHits || 0;
            const totalPages = Math.ceil(totalHits / this.hitsPerPage);
            const hasNextPage = this.currentPage < totalPages - 1;

            this.currentPage++;
            this.results = videos;
            this.hasMore = hasNextPage;

            return this;
        } catch (error) {
            trace(`Exception in TedTalksSearchPager.nextPage: ${error.message}`);
            this.loadFailed = true;
            this.results = [];
            this.hasMore = false;
            return this;
        }
    }
}

/**
 * Custom pager implementation for TED Talks topic results (paginated)
 */
class TedTalksTopicPager extends ContentPager {
    /**
     * Creates a new TED Talks topic pager
     * @param {string} topicSlug Topic slug (e.g., "technology")
     * @param {string} topicName Topic display name (e.g., "TED-Ed") for search filtering
     */
    constructor(topicSlug, topicName) {
        super([], true);
        this.topicSlug = topicSlug;
        this.topicName = topicName || topicSlug.replace(/\+/g, ' ');
        this.currentPage = 0;
        this.hitsPerPage = 24;
        this.nextPage();
    }

    /**
     * Fetches the next page of results
     * @returns {ContentPager} Pager with the current page results
     */
    nextPage() {
        try {
            const searchParams = {
                "attributeForDistinct": "objectID",
                "distinct": 1,
                "facets": ["subtitle_languages", "tags"],
                "filters": `tags:"${this.topicName}"`,
                "highlightPostTag": "__/ais-highlight__",
                "highlightPreTag": "__ais-highlight__",
                "hitsPerPage": this.hitsPerPage,
                "maxValuesPerFacet": 500,
                "page": this.currentPage,
                "query": ""
            };

            const request = [{ "indexName": "newest", "params": searchParams }];
            const response = makeRequestWithRetry('POST', PLATFORM.SEARCH_URL, JSON.stringify(request), DEFAULT_HEADERS);

            if (!response.isOk) {
                trace(`Search API request failed for topic. Status: ${response.code}, Topic: "${this.topicSlug}", Page: ${this.currentPage}`);
                this.results = [];
                this.hasMore = false;
                return this;
            }

            const searchResults = safeJsonParse(response.body)?.results?.[0];
            if (!searchResults?.hits) {
                trace(`Invalid search response format for topic: "${this.topicSlug}", Page: ${this.currentPage}`);
                this.results = [];
                this.hasMore = false;
                return this;
            }

            const hits = searchResults.hits;
            if (hits.length === 0) {
                this.results = [];
                this.hasMore = false;
                return this;
            }

            const slugs = hits.map(hit => hit.slug).filter(Boolean);
            const extraMetadata = getVideoDetailsBySlugList(slugs) || {};

            const videos = hits
                .filter(hit => shouldShowContent(hit, extraMetadata[hit.slug] || {}))
                .map(hit => convertToFeedEntry(hit, extraMetadata[hit.slug] || {}));

            const totalHits = searchResults.nbHits || 0;
            const totalPages = Math.ceil(totalHits / this.hitsPerPage);
            const hasNextPage = this.currentPage < totalPages - 1;

            this.currentPage++;
            this.results = videos;
            this.hasMore = hasNextPage;

            return this;
        } catch (error) {
            trace(`Exception in TedTalksTopicPager.nextPage: ${error.message}`);
            this.results = [];
            this.hasMore = false;
            return this;
        }
    }
}

// ====================== CORE FUNCTIONALITY ======================

/**
 * Search for TED speakers
 * @param {string} query Search query
 * @returns {ContentPager} Paged results for search
 */
function searchSpeakers(query) {
    const url = `${PLATFORM.SPEAKER_URL}?sort=first&q=${encodeURIComponent(query)}`;
    const response = makeRequestWithRetry('GET', url, null, { 'User-Agent': DEFAULT_HEADERS["User-Agent"] });

    if (!response.isOk) {
        trace(`Failed to get speakers search page. Status: ${response.code}, URL: ${url}`);
        trace('Failed to load speaker search results.');
        return createEmptyPager();
    }

    try {
        const speakerElements = domParser
            .parseFromString(response.body, 'text/html')
            .querySelectorAll('div#browse-results .col');

        const channels = Array.from(speakerElements).map(element => {
            // The name is split across a <br> (\nAaron<br>Bastani), which .text would join
            // without a separator, so read the markup and turn the break into a space.
            const nameHtml = element.querySelector('div.media__message h4')?.innerHTML || '';
            const name = nameHtml
                .replace(/<br\s*\/?>/gi, ' ')
                .replace(/<[^>]+>/g, '')
                .replace(/\s+/g, ' ')
                .trim();

            const photoUrl = element.querySelector('img.thumb__image')?.getAttribute('src');
            const profileRelativeUrl = element.querySelector('a.results__result')?.getAttribute('href');
            const profileUrl = profileRelativeUrl ? `${PLATFORM.BASE_URL}${profileRelativeUrl}` : '';
            const speakerSlug = profileUrl ? extractSpeakerSlug(profileUrl) : '';
            const speakerId = speakerSlug || name;

            return new PlatformChannel({
                id: new PlatformID(PLATFORM.NAME, speakerId, config.id),
                name: name,
                thumbnail: photoUrl ?? '',
                subscribers: -1,
                url: profileUrl
            });
        });

        return new ContentPager(channels, false);
    } catch (error) {
        trace(`Exception in searchSpeakers: ${error.message}`);
        return createEmptyPager();
    }
}

/**
 * Gets recommended videos for a TED Talk
 * @param {string} url TED Talk URL
 * @param {Array} relatedVideos Optional array of related videos
 * @returns {ContentPager} Paged results with recommended content
 */
function getVideoRecommendations(url, relatedVideos) {
    try {
        const slug = extractTedSlug(url);
        if (!slug) {
            trace(`Could not extract TED talk slug from URL: ${url}`);
            return createEmptyPager();
        }

        // If related videos aren't provided, fetch them
        if (!relatedVideos || !relatedVideos.length) {
            const responseBody = makeGraphQLRequest(
                'RELATED',
                { slug: slug, language: "en" },
                "related"
            );
            
            if (!responseBody?.data?.videos?.nodes || responseBody.data.videos.nodes.length === 0) {
                trace(`No video data found for slug: ${slug}`);
                return createEmptyPager();
            }
            
            relatedVideos = responseBody?.data?.videos?.nodes?.[0]?.relatedVideos ?? [];
            
        }

        if (!relatedVideos?.length) {
            return createEmptyPager();
        }

        // Get additional metadata for the videos
        const slugs = relatedVideos.map(video => video.slug);
        const metaSet = getVideoDetailsBySlugList(slugs);

        // Convert related videos to platform videos, filtering out podcast-only content (unless external content handling is "Show")
        const videos = relatedVideos
            .filter(relatedVideo => shouldShowContent(relatedVideo, metaSet[relatedVideo.slug]))
            .map(relatedVideo => convertToFeedEntry(relatedVideo, metaSet[relatedVideo.slug]));

        return new ContentPager(videos, false);
    } catch (error) {
        trace(`Exception in getVideoRecommendations: ${error.message}`);
        return createEmptyPager();
    }
}

/**
 * Gets playlist details from a TED playlist URL
 * @param {string} url Playlist URL
 * @returns {PlatformPlaylistDetails} Playlist details
 */
function getPlaylistByUrl(url) {
    try {
        const { id, slug } = extractPlaylistIdAndSlug(url);
        if (!id) {
            // Returning null here would surface as an engine cast error rather than a clean message
            throw new UnavailableException('Could not determine playlist from URL.');
        }

        // Fetch the playlist page to get data from __NEXT_DATA__
        const response = makeRequestWithRetry('GET', url, null, { 'User-Agent': DEFAULT_HEADERS["User-Agent"] }, { throwOnError: false });
        if (!response.isOk) {
            trace(`Failed to get playlist page. Status: ${response.code}, URL: ${url}`);
            throw new UnavailableException('This playlist is unavailable.');
        }

        // Extract playlist data from __NEXT_DATA__
        const playlistData = extractNextDataFromHtml(response.body, 'playlist');
        if (!playlistData) {
            trace(`Could not extract playlist data from page: ${url}`);
            throw new UnavailableException('This playlist could not be read.');
        }

        // Get thumbnail from primaryImageSet
        const thumbnailUrl = playlistData.primaryImageSet?.[0]?.url || '';

        // Get video count
        const videoCount = playlistData.videos?.totalCount || playlistData.videos?.nodes?.length || 0;

        // Get video slugs for fetching full metadata
        const videoSlugs = (playlistData.videos?.nodes || []).map(v => v.slug).filter(Boolean);
        const extraMetadata = videoSlugs.length > 0 ? getVideoDetailsBySlugList(videoSlugs) : {};

        // Convert playlist videos to platform videos, filtering out podcast-only content (unless external content handling is "Show")
        const videos = (playlistData.videos?.nodes || [])
            .filter(video => shouldShowContent(video, extraMetadata[video.slug] || {}))
            .map(video => {
                const extra = extraMetadata[video.slug] || {};
                if (isKnownExternalContent(video, extra)) {
                    return convertToNestedMediaContent(video, extra);
                }
                return convertPlaylistVideoToPlatformVideo(video, extra);
            });

        return new PlatformPlaylistDetails({
            id: new PlatformID(PLATFORM.NAME, playlistData.id || id, config.id),
            name: playlistData.title || 'TED Playlist',
            thumbnail: thumbnailUrl,
            author: new PlatformAuthorLink(
                new PlatformID(PLATFORM.NAME, 'TED', config.id),
                playlistData.author || 'TED',
                PLATFORM.BASE_URL,
                PLATFORM.FALLBACK_AVATAR
            ),
            datetime: null, // Playlists don't have a specific date
            url: url,
            videoCount: videoCount,
            contents: new ContentPager(videos, false)
        });
    } catch (error) {
        trace(`Exception in getPlaylistByUrl: ${error.message}`);

        if (error instanceof UnavailableException) {
            throw error;
        }

        // The caller is source.getPlaylist, whose result must be an object
        throw new ScriptException(`Could not load this playlist: ${error.message}`);
    }
}

/**
 * Gets a TED topic as a playlist
 * @param {string} url Topic URL (e.g., https://www.ted.com/topics/technology)
 * @returns {PlatformPlaylistDetails} Playlist details for the topic
 */
function getTopicAsPlaylist(url) {
    try {
        const topicSlug = extractTopicSlug(url);
        if (!topicSlug) {
            // Returning null here would surface as an engine cast error rather than a clean message
            throw new UnavailableException('Could not determine topic from URL.');
        }

        // Get topic info from GraphQL
        const topicInfo = getTopicInfo(topicSlug);
        const topicName = topicInfo?.name || formatTopicName(topicSlug);

        // Create a paginated pager for the topic videos (pass both slug and name)
        const topicPager = new TedTalksTopicPager(topicSlug, topicName);

        return new PlatformPlaylistDetails({
            id: new PlatformID(PLATFORM.NAME, `topic-${topicSlug}`, config.id),
            name: topicName,
            thumbnail: '', // Topics don't have thumbnails
            author: new PlatformAuthorLink(
                new PlatformID(PLATFORM.NAME, 'TED', config.id),
                'TED',
                PLATFORM.BASE_URL,
                PLATFORM.FALLBACK_AVATAR
            ),
            datetime: null,
            url: url,
            videoCount: -1, // Unknown total count, will be paginated
            contents: topicPager
        });
    } catch (error) {
        trace(`Exception in getTopicAsPlaylist: ${error.message}`);

        if (error instanceof UnavailableException) {
            throw error;
        }

        // The caller is source.getPlaylist, whose result must be an object
        throw new ScriptException(`Could not load this topic: ${error.message}`);
    }
}

/**
 * Gets topic information from GraphQL API
 * @param {string} topicSlug Topic slug
 * @returns {Object|null} Topic info or null on error
 */
function getTopicInfo(topicSlug) {
    try {
        const queryInfo = {
            query: GQL_QUERIES.TOPIC_INFO,
            operationName: "topicInfo",
            variables: { slug: topicSlug }
        };

        const response = makeRequestWithRetry('POST', PLATFORM.GRAPHQL_URL, JSON.stringify(queryInfo), DEFAULT_HEADERS, { throwOnError: false });

        if (!response.isOk) {
            return null;
        }

        const data = safeJsonParse(response.body);
        return data?.data?.topic || null;
    } catch (error) {
        trace(`Exception in getTopicInfo: ${error.message}`);
        return null;
    }
}

/**
 * Extracts topic slug from a TED topic URL
 * @param {string} url TED topic URL
 * @returns {string|null} Topic slug or null if not found
 */
function extractTopicSlug(url) {
    try {
        const match = REGEX.TOPIC_SLUG.exec(url);
        return match ? match[1] : null;
    } catch (error) {
        trace(`Exception in extractTopicSlug: ${error.message}`);
        return null;
    }
}

/**
 * Formats a topic slug into a display name
 * @param {string} slug Topic slug (e.g., "personal+growth")
 * @returns {string} Formatted name (e.g., "Personal Growth")
 */
function formatTopicName(slug) {
    return slug
        .replace(/\+/g, ' ')
        .replace(/-/g, ' ')
        .split(' ')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');
}

/**
 * Builds video description with topic links appended
 * @param {string} description Original video description
 * @param {Array} topics Array of topic objects with { name, slug }
 * @returns {string} Description with topic links
 */
function buildDescriptionWithTopics(description, topics) {
    if (!topics || topics.length === 0) {
        return description;
    }

    if (IS_ANDROID) {
        // Android: Add clickable topic links (matching TED's display format)
        const topicLinks = topics.map(topic => {
            // Use slug directly - TED URLs use + for spaces, not %2B
            const url = `${PLATFORM.BASE_URL}/topics/${topic.slug}`;
            return `<a href="${url}">${topic.name}</a>`;
        }).join(' • ');
        return description + '\n\nTopics: ' + topicLinks;
    } else {
        // Desktop: Add plain text topics (links not yet supported)
        const topicText = topics.map(topic => topic.name).join(', ');
        return description + '\n\nTopics: ' + topicText;
    }
}

/**
 * Extracts __NEXT_DATA__ from HTML and returns the requested pageProps property
 * @param {string} html HTML content
 * @param {string} propName Property name to extract from pageProps
 * @returns {Object|null} Extracted data or null if not found
 */
function extractNextDataFromHtml(html, propName) {
    try {
        const doc = domParser.parseFromString(html, 'text/html');
        const scriptElement = doc.querySelector('script#__NEXT_DATA__');
        if (!scriptElement) {
            trace('Could not find __NEXT_DATA__ script element');
            return null;
        }

        const jsonData = safeJsonParse(scriptElement.text);
        if (!jsonData?.props?.pageProps?.[propName]) {
            trace(`Could not find ${propName} in __NEXT_DATA__`);
            return null;
        }

        return jsonData.props.pageProps[propName];
    } catch (error) {
        trace(`Error extracting __NEXT_DATA__: ${error.message}`);
        return null;
    }
}

/**
 * Converts a playlist video object to PlatformVideo
 * @param {Object} video Video data from playlist
 * @param {Object} extraData Additional metadata
 * @returns {PlatformVideo} Formatted platform video object
 */
function convertPlaylistVideoToPlatformVideo(video, extraData) {
    const slug = video.slug || '';
    const videoUrl = `${PLATFORM.TALKS_URL}/${slug}`;

    // Get thumbnail from primaryImageSet
    let thumbnailUrl = '';
    if (video.primaryImageSet && video.primaryImageSet.length > 0) {
        thumbnailUrl = video.primaryImageSet[0].url || '';
    }

    // Fall back to extraData thumbnail if available
    if (!thumbnailUrl && extraData?.playerData) {
        thumbnailUrl = extractThumbnailUrl(extraData);
    }

    // Parse presenter name for author
    const presenterName = video.presenterDisplayName || 'Unknown Speaker';
    
    // Speakers arrive as a { nodes: [...] } connection, which the shared extractor handles
    const speaker = extractSpeakerInfo(video, extraData);

    const author = (speaker.id || speaker.slug) ? createAuthorLink(speaker) : new PlatformAuthorLink(
        new PlatformID(PLATFORM.NAME, presenterName, config.id),
        presenterName,
        PLATFORM.BASE_URL,
        PLATFORM.FALLBACK_AVATAR
    );

    return new PlatformVideo({
        id: new PlatformID(PLATFORM.NAME, video.canonicalUrl || extraData?.canonicalUrl || slug || video.id, config.id),
        name: video.title || 'Unknown TED Talk',
        thumbnails: new Thumbnails(thumbnailUrl ? [new Thumbnail(thumbnailUrl, 0)] : []),
        author: author,
        datetime: dateToUnixSeconds(video.publishedAt),
        duration: safeParseInt(video.duration),
        viewCount: video.viewedCount || 0,
        url: videoUrl,
        isLive: false
    });
}

/**
 * Extracts playlist ID and slug from a TED playlist URL
 * @param {string} url TED playlist URL
 * @returns {Object} Object with id and slug properties
 */
function extractPlaylistIdAndSlug(url) {
    try {
        const match = REGEX.PLAYLIST_ID_SLUG.exec(url);
        if (match) {
            return {
                id: match[1] || null,
                slug: match[2] || null
            };
        }
        return { id: null, slug: null };
    } catch (error) {
        trace(`Exception in extractPlaylistIdAndSlug: ${error.message}`);
        return { id: null, slug: null };
    }
}

/**
 * Gets channel content from a TED speaker URL
 * @param {string} url Speaker URL
 * @returns {ContentPager} Paged results for channel content
 */
function getChannelContentsByUrl(url) {
    try {
        const channelSlug = extractSpeakerSlug(url);
        if (!channelSlug) {
            trace('Could not determine speaker from URL.');
            return createEmptyPager();
        }

        // Construct clean speaker URL without query parameters (some URLs have ?speakerId= which returns 404)
        const cleanUrl = `${PLATFORM.BASE_URL}/speakers/${channelSlug}`;

        // Fetch the speaker page (don't throw on error, handle gracefully)
        const response = makeRequestWithRetry('GET', cleanUrl, null, { 'User-Agent': DEFAULT_HEADERS["User-Agent"] }, { throwOnError: false });

        let talkSlugs = [];
        let verifyBySpeakerSlug = false;
        let speakerNameTokens = [];
        if (response.isOk) {
            talkSlugs = extractTalkSlugsFromHtml(response.body) || [];
        } else {
            // Many speakers have no profile page even though their talks exist, so fall back to
            // the search index. Match on the speaker name it reports rather than the talk slug,
            // because the slug may name a co-speaker first or differ from the speaker slug
            // (jennifer_golbeck vs "Jen Golbeck").
            trace(`Speaker page unavailable. Status: ${response.code}, URL: ${cleanUrl}`);

            // Some speaker slugs carry an event suffix (lisa_monteggia_TEDxNashvilleWomen20250821-64681).
            // Those tokens are not part of the name and stop the search from matching.
            const speakerId = extractSpeakerId(url);
            const nameTokens = channelSlug.split('_').filter(token => token && !/\d/.test(token));
            let speakerName = (nameTokens.length ? nameTokens : channelSlug.split('_')).join(' ');
            if (speakerId) {
                const speaker = makeGraphQLRequest('SPEAKER_BY_ID', { id: speakerId }, "acmeSpeaker")?.data?.acmeSpeaker;
                if (speaker) {
                    speakerName = formatName(speaker.firstname, speaker.lastname);
                }
            }

            // Every hit is taken as a candidate rather than matched on name here: display names and
            // slugs disagree too often (larissa_may is credited "Larz May"). They are verified
            // against the authoritative speaker slug after enrichment instead.
            verifyBySpeakerSlug = true;
            speakerNameTokens = speakerName.toLowerCase().split(/\s+/).filter(Boolean);
            let totalPages = 1;
            for (let page = 0; page < Math.min(totalPages, SPEAKER_TALKS_MAX_PAGES); page++) {
                const searchParams = {
                    "hitsPerPage": SPEAKER_TALKS_SEARCH_LIMIT,
                    "page": page,
                    "query": speakerName
                };
                const searchRequest = [{ "indexName": "newest", "params": searchParams }];
                const searchResponse = makeRequestWithRetry('POST', PLATFORM.SEARCH_URL, JSON.stringify(searchRequest), DEFAULT_HEADERS, { throwOnError: false });
                if (!searchResponse.isOk) {
                    break;
                }

                const searchResults = safeJsonParse(searchResponse.body)?.results?.[0];
                totalPages = searchResults?.nbPages || 1;
                talkSlugs = talkSlugs.concat((searchResults?.hits || []).map(hit => hit?.slug).filter(Boolean));
            }
        }

        if (!talkSlugs.length) {
            trace('Speaker does not have any talks yet.');
            return createEmptyPager();
        }

        // Fetch metadata for the talks
        const extraMetadata = getVideoDetailsBySlugList(talkSlugs);
        if (!extraMetadata) {
            return createEmptyPager();
        }

        // Search candidates are unverified, so drop any talk this speaker is not credited on. Match
        // the canonical speaker slug, or the display name when the url slug is itself an alias
        // (jen_golbeck resolves to the canonical jennifer_golbeck). The page path needs no check.
        const candidates = verifyBySpeakerSlug
            ? Object.values(extraMetadata).filter(video => {
                // Some talks name a contributor only in their slug, not in the speakers list
                // (pazit_cahlon_and_alex_gendler_... credits Pazit Cahlon alone).
                if ((video?.slug || '').includes(channelSlug)) {
                    return true;
                }
                return (video?.speakers?.nodes || []).some(speaker => {
                    if (speaker?.slug === channelSlug) {
                        return true;
                    }
                    const speakerTokens = formatName(speaker?.firstname, speaker?.lastname).toLowerCase().split(/\s+/).filter(Boolean);
                    return speakerTokens.length > 0
                        && (speakerNameTokens.every(token => speakerTokens.includes(token))
                            || speakerTokens.every(token => speakerNameTokens.includes(token)));
                });
            })
            : Object.values(extraMetadata);

        // Process videos and sort by date, filtering out podcast-only content (unless external content handling is "Show")
        const videos = candidates
            .filter(video => shouldShowContent(video, {}))
            .sort((a, b) => new Date(b.publishedAt || 0) - new Date(a.publishedAt || 0))
            .map(video => {
                // Filter speakers to only show the current channel speaker
                if (video?.speakers?.nodes) {
                    video.speakers.nodes = video.speakers.nodes.filter(s => s.slug === channelSlug);
                }
                return convertToFeedEntry(video, {});
            });

        return new ContentPager(videos, false);
    } catch (error) {
        trace(`Exception in getChannelContentsByUrl: ${error.message}`);
        trace('Failed to load speaker content.');
        return createEmptyPager();
    }
}

/**
 * Gets channel information for a TED speaker
 * @param {string} url Speaker URL
 * @returns {PlatformChannel} Channel information
 * @throws {UnavailableException|ScriptException} When the speaker is unavailable or cannot be loaded
 */
function getChannelByUrl(url) {
    try {
        const speakerId = extractSpeakerId(url);

        if (speakerId) {
            return getChannelByGraphQL(speakerId, url);
        }

        return getChannelByHTML(url);
    } catch (error) {
        trace(`Exception in getChannelByUrl: ${error.message}`);

        if (error instanceof UnavailableException || error instanceof ScriptException) {
            throw error;
        }

        throw new ScriptException(`Could not load this speaker: ${error.message}`);
    }
}

/**
 * Gets channel information using GraphQL API
 * @param {string} speakerId Speaker ID
 * @param {string} url Speaker URL
 * @returns {PlatformChannel} Channel information
 * @throws {UnavailableException|ScriptException} When the speaker is unavailable or TED fails
 */
function getChannelByGraphQL(speakerId, url) {
    const responseBody = makeGraphQLRequest(
        'SPEAKER_BY_ID',
        { id: speakerId },
        "acmeSpeaker"
    );

    if (!responseBody) {
        throw new ScriptException('Could not reach TED to load this speaker.');
    }

    // TED carries missing IDs as 404s inside an otherwise successful GraphQL response.
    if (Array.isArray(responseBody.errors) && responseBody.errors.length) {
        if (isNotFoundGraphQLError(responseBody.errors)) {
            throw new UnavailableException('This speaker could not be found. They may have been removed.');
        }
        throw new ScriptException(`TED could not load this speaker: ${responseBody.errors[0]?.message || 'unknown error'}`);
    }

    const speaker = responseBody?.data?.acmeSpeaker;
    if (!speaker) {
        trace(`Speaker data not found in response for ID: ${speakerId}`);
        throw new UnavailableException('This speaker could not be found. They may have been removed.');
    }

    const description = formatSpeakerDescription(speaker);
    const name = formatName(speaker.firstname, speaker.lastname);
    const channelId = speaker.slug || name || '';

    return new PlatformChannel({
        id: new PlatformID(PLATFORM.NAME, channelId, config.id),
        name: name,
        thumbnail: speaker.photoUrl || PLATFORM.FALLBACK_AVATAR,
        description: description,
        url: url,
    });
}

/**
 * Gets channel information by parsing HTML
 * @param {string} url Speaker URL
 * @returns {PlatformChannel} Channel information
 * @throws {UnavailableException|ScriptException} When the profile is unavailable or malformed
 */
function getChannelByHTML(url) {
    trace(`Could not extract speaker ID from URL: ${url}. Extracting info from page`);

    // The status is handled locally below, so the request must not throw a generic error first
    const response = makeRequestWithRetry('GET', url, null, { 'User-Agent': DEFAULT_HEADERS["User-Agent"] }, { throwOnError: false });
    if (!response.isOk) {
        trace(`Failed to get channel page. Status: ${response.code}, URL: ${url}`);
        trace('Speaker profile not found. This TED speaker may not have a public profile yet.', { showToast: true });
        throw new UnavailableException("Not able to access speaker page.");
    }

    try {
        const doc = domParser.parseFromString(response.body, 'text/html');
        const mainElement = doc.querySelector('div.main');
        if (!mainElement) {
            throw new UnavailableException("Not able to read speaker page.");
        }

        const name = mainElement.querySelector('.profile-header__name')?.text;
        const photoUrl = mainElement.querySelector('img.thumb__image')?.getAttribute('src');

        let description = mainElement.querySelector('.profile-intro')?.innerHTML ?? '';
        description += mainElement.querySelector('div.section')?.innerHTML ?? '';

        const speakerSlug = extractSpeakerSlug(url);
        const speakerId = speakerSlug || name || '';

        return new PlatformChannel({
            id: new PlatformID(PLATFORM.NAME, speakerId, config.id),
            name: name,
            thumbnail: photoUrl ?? '',
            description: description,
            url: url
        });
    } catch (error) {
        trace(`Exception parsing HTML for channel: ${error.message}`);

        if (error instanceof UnavailableException) {
            throw error;
        }

        throw new ScriptException(`Could not read the speaker page: ${error.message}`);
    }
}

/**
 * Gets detailed video information for a TED Talk
 * @param {string} url TED Talk URL
 * @returns {PlatformVideoDetails} Video details
 * @throws {UnavailableException|ScriptException} When the talk is unavailable or cannot be loaded
 */
function getVideoDetailsByUrl(url) {
    try {
        const slug = extractTedSlug(url);
        if (!slug) {
            throw new UnavailableException("This does not look like a TED talk link.");
        }

        const responseBody = makeGraphQLRequest(
            'VIDEO_DETAILS',
            { slug: slug, language: "en" },
            "shareLinks"
        );

        if (!responseBody) {
            throw new ScriptException('Could not reach TED to load this talk.');
        }

        // TED carries missing records as 404s inside an otherwise successful GraphQL response.
        if (Array.isArray(responseBody.errors) && responseBody.errors.length) {
            if (isNotFoundGraphQLError(responseBody.errors)) {
                throw new UnavailableException('This talk could not be found. It may have been removed.');
            }
            throw new ScriptException(`TED could not load this talk: ${responseBody.errors[0]?.message || 'unknown error'}`);
        }

        const nodes = responseBody?.data?.videos?.nodes;
        if (!Array.isArray(nodes)) {
            throw new ScriptException('Unexpected response from TED while loading this talk.');
        }

        if (nodes.length === 0) {
            throw new UnavailableException('This talk could not be found. It may have been removed.');
        }

        const video = nodes[0];
        
        if (!hasPlayableVideoSources(video, null)) {
            const externalUrl = getExternalContentUrl(video, null);
            if (externalUrl) {
                const provider = getContentProviderFromUrl(externalUrl);
                throw new UnavailableException(`This talk is hosted on ${provider}. Opening external content via shared links is not supported for now. Try browsing TED Talks using the Grayjay plugin.`);
            }
            throw new UnavailableException("This content is unavailable.");
        }
        
        const speaker = video.speakers?.nodes?.[0];
        const thumbnailUrl = extractThumbnailUrl(video);
        
        // Build description with topic links
        const description = buildDescriptionWithTopics(video.description || '', video.topics?.nodes || []);

        // Fetch subtitles
        let subtitles = [];
        if (video.playerData) {
            const playerData = safeJsonParse(video.playerData);
            const metadataUrl = playerData?.resources?.hls?.metadata;
            if (metadataUrl) {
                subtitles = fetchSubtitles(metadataUrl);
            }
        }

        const details = new PlatformVideoDetails({
            id: new PlatformID(PLATFORM.NAME, video.canonicalUrl || slug, config.id),
            name: video.title || 'Unknown TED Talk',
            author: speaker ? createAuthorLink(speaker) : null,
            datetime: dateToUnixSeconds(video.publishedAt),
            description: description,
            url: url,
            video: createVideoSources(video),
            duration: safeParseInt(video.duration),
            thumbnails: new Thumbnails(thumbnailUrl ? [new Thumbnail(thumbnailUrl, 0)] : []),
            viewCount: video.viewedCount || 0,
            subtitles: subtitles
        });

        const relatedVideos = Array.isArray(video?.relatedVideos) ? video.relatedVideos : [];

        // Add method to get recommendations without refetching video data
        details.getContentRecommendations = () => {
            return source.getContentRecommendations(url, relatedVideos);
        };

        return details;
    } catch (error) {
        trace(`Exception in getVideoDetailsByUrl: ${error.message}`);

        if (error instanceof UnavailableException || error instanceof ScriptException) {
            throw error;
        }

        throw new ScriptException(`Could not load this talk: ${error.message}`);
    }
}

// ====================== DATA FETCHING ======================

/**
 * Reports whether GraphQL errors mean the record is missing rather than the request failing.
 * @param {Array<Object>} errors GraphQL errors array
 * @returns {boolean} True when the errors indicate a missing record
 */
function isNotFoundGraphQLError(errors) {
    return errors.some(error => error?.extensions?.response?.status === HTTP_NOT_FOUND
        || /^404\b/.test(error?.message || ''));
}

/**
 * Makes a GraphQL request to TED's API
 * @param {string} queryName Name of the query from GQL_QUERIES
 * @param {Object} variables Variables for the GraphQL query
 * @param {string} operationName Operation name for the GraphQL request
 * @returns {Object|null} Response body or null on error
 */
function makeGraphQLRequest(queryName, variables, operationName) {
    try {
        if (!GQL_QUERIES[queryName]) {
            trace(`Unknown GraphQL query: ${queryName}`);
            return null;
        }

        const queryInfo = {
            query: GQL_QUERIES[queryName],
            operationName: operationName,
            variables: variables
        };

        const response = makeRequestWithRetry('POST', PLATFORM.GRAPHQL_URL, JSON.stringify(queryInfo), DEFAULT_HEADERS);

        if (!response.isOk) {
            trace(`GraphQL request failed. Query: ${queryName}, Status: ${response.code}`);
            return null;
        }

        return safeJsonParse(response.body);
    } catch (error) {
        trace(`Exception in makeGraphQLRequest: ${error.message}`);
        return null;
    }
}

/**
 * Fetches video details by slug from TED's GraphQL API
 * @param {Array<string>} slugs Array of video slugs to fetch
 * @returns {Object|null} Processed video data or null on error
 */
function getVideoDetailsBySlugList(slugs) {
    try {
        const detailsBySlug = {};

        // The query caps results at VIDEO_DETAILS_BATCH_SIZE, so longer lists are sent in batches
        // rather than silently losing everything past the cap.
        for (let offset = 0; offset < slugs.length; offset += VIDEO_DETAILS_BATCH_SIZE) {
            const query = {
                query: GQL_QUERIES.VIDEOS_INFO_BY_SLUG,
                variables: {
                    slug: slugs.slice(offset, offset + VIDEO_DETAILS_BATCH_SIZE)
                }
            };

            const response = makeRequestWithRetry('POST', PLATFORM.GRAPHQL_URL, JSON.stringify(query), DEFAULT_HEADERS, { throwOnError: false });
            if (!response.isOk) {
                continue;
            }

            const videos = safeJsonParse(response.body)?.data?.videos?.nodes || [];
            videos.forEach(video => {
                if (video.slug) {
                    detailsBySlug[video.slug] = video;
                }
            });
        }

        return detailsBySlug;
    } catch (error) {
        trace(`Exception in getVideoDetailsBySlugList: ${error.message}`);
        return {};
    }
}

/**
 * Builds subtitle sources from playerData without any HTTP request.
 * playerData.languages already lists exactly the languages metadata.json reports, and the
 * webvtt URLs are derivable from the master ids, so feeds avoid a request per item.
 * @param {Object} playerData Parsed playerData object
 * @returns {Array<SubtitleSource>|null} Subtitle sources, empty when the talk has none,
 *   or null when they exist but could not be derived so the caller can fall back
 */
function deriveSubtitles(playerData) {
    const languages = playerData?.languages;
    if (!Array.isArray(languages) || !languages.length) {
        return [];
    }

    const stream = playerData?.resources?.hls?.stream;
    const projectId = stream?.match(REGEX.HLS_PROJECT_ID)?.[1];
    const introId = stream?.match(REGEX.HLS_INTRO_ID)?.[1];
    if (!projectId || !introId) {
        return null;
    }

    return languages
        .filter(language => language?.languageCode)
        .map(language => ({
            format: "text/vtt",
            url: `https://hls.ted.com/project_masters/${projectId}/subtitles/${language.languageCode}/full.vtt?intro_master_id=${introId}&preview=`,
            name: language.languageName || language.endonym || 'Unknown'
        }));
}

/**
 * Fetches subtitles from the HLS metadata URL
 * @param {string} metadataUrl URL to the HLS metadata JSON
 * @returns {Array<SubtitleSource>} Array of subtitle sources
 */
function fetchSubtitles(metadataUrl) {
    try {
        const response = makeRequestWithRetry('GET', metadataUrl, null, {});
        if (!response.isOk) {
            trace(`Subtitle request failed: ${response.code}`);
            return [];
        }
        
        const metadata = safeJsonParse(response.body);
        if (!metadata?.subtitles) {
            trace(`No subtitles found in metadata`);
            return [];
        }
        
        return metadata.subtitles
            .filter(sub => sub && sub.webvtt)
            .map(sub => ({
                format: "text/vtt",
                url: sub.webvtt,
                name: sub.name || 'Unknown'
            }));
    } catch (error) {
        trace(`Failed to fetch subtitles: ${error.message}`);
        return [];
    }
}

/**
 * Makes an HTTP request with automatic retries and httpimp fallback.
 *
 * @param {string} method - HTTP method ('GET', 'POST', etc.)
 * @param {string} url - The URL to make the request to
 * @param {string|Object|null} body - Request body for POST/PUT requests
 * @param {Object} headers - HTTP headers for the request
 * @param {Object} options - Additional options
 * @param {boolean} [options.useAuth=false] - Whether to use authentication for the request
 * @param {number} [options.maxRetries=1] - Maximum number of retry attempts
 * @param {boolean} [options.throwOnError=true] - Whether to throw an exception on error
 * @returns {Object} - Response object with isOk, code, and body properties
 */
function makeRequestWithRetry(method, url, body = null, headers = {}, options = {}) {
    const {
        useAuth = false,
        maxRetries = 1,
        throwOnError = true
    } = options;

    const useImp = _settings.useBrowserImpersonation !== false && IS_IMPERSONATION_AVAILABLE;

    // Strip User-Agent for httpimp - curl-impersonate sets its own to match TLS fingerprint
    const impHeaders = Object.fromEntries(
        Object.entries(headers).filter(([k]) => k.toLowerCase() !== 'user-agent')
    );

    function executeRequest(client, reqHeaders) {
        let response;
        if (method.toUpperCase() === 'GET') {
            response = {...client.GET(url, reqHeaders, useAuth)};
        } else if (method.toUpperCase() === 'POST') {
            response = {...client.POST(url, body, reqHeaders, useAuth)};
        } else {
            throw new ScriptException(`Unsupported HTTP method: ${method}`);
        }
        return response;
    }

    function tryClient(client, label, attempts, reqHeaders) {
        let lastResponse = null;
        let lastError = null;

        while (attempts > 0) {
            try {
                const response = executeRequest(client, reqHeaders);
                if (response.isOk) {
                    trace(`[${label}] OK: ${method} ${url}`);
                    return response;
                }

                lastResponse = response;
                trace(`[${label}] ${method} ${url} failed with status ${response.code}. Attempts left: ${attempts - 1}`);

                // Most client errors are deterministic, so repeating them only wastes requests.
                // Timeouts, early-data rejections and rate limits can still succeed on another try.
                if (response.code >= 400 && response.code < 500) {
                    if (!HTTP_RETRYABLE_CLIENT_ERRORS.includes(response.code)) {
                        return { lastResponse, lastError };
                    }
                    // Only pace a rate limit, and only when a further attempt actually follows
                    if (response.code === HTTP_TOO_MANY_REQUESTS && attempts > 1) {
                        bridge.sleep(HTTP_RETRY_BASE_DELAY_MS * (maxRetries + 2 - attempts));
                    }
                }
            } catch (error) {
                lastError = error;
                trace(`[${label}] Exception in ${method} ${url}: ${error.message}. Attempts left: ${attempts - 1}`);
            }
            attempts--;
        }
        return { lastResponse, lastError };
    }

    // 1. Try standard http with retries
    const httpResult = tryClient(http, 'http', maxRetries + 1, headers);
    if (httpResult.isOk !== undefined) {
        return httpResult;
    }

    // 2. Try httpimp if available (single attempt). A deterministic client error will not change
    // with a different TLS fingerprint, so only escalate on 403, which is how bot blocking presents.
    const httpCode = httpResult.lastResponse?.code;
    const isTerminalClientError = httpCode >= 400 && httpCode < 500
        && httpCode !== HTTP_FORBIDDEN
        && !HTTP_RETRYABLE_CLIENT_ERRORS.includes(httpCode);
    if (useImp && !isTerminalClientError) {
        const impResult = tryClient(httpimp, 'httpimp', 1, impHeaders);
        if (impResult.isOk !== undefined) {
            return impResult;
        }

        // Both failed
        if (throwOnError) {
            throw impResult.lastError || httpResult.lastError ||
                new ScriptException(`${method} request to ${url} failed after all attempts (http + httpimp)`);
        }

        return impResult.lastResponse || httpResult.lastResponse || {
            isOk: false, code: 0,
            body: JSON.stringify({ error: `${method} request failed after all retry attempts` })
        };
    }

    // httpimp not available - handle http-only failure
    if (throwOnError) {
        throw httpResult.lastError ||
            new ScriptException(`${method} request to ${url} failed after all attempts`);
    }

    return httpResult.lastResponse || {
        isOk: false, code: 0,
        body: JSON.stringify({ error: `${method} request failed after all retry attempts` })
    };
}

// ====================== CONVERSION FUNCTIONS ======================

function mapSource(source, key, duration) {
    let sources = [];
    if (REGEX.HLS_URL.test(source.stream)) {
        sources.push(
            new HLSSource({
                name: key,
                url: source.stream,
                duration: duration,
                priority: true,
            }),
        );
    } else if (REGEX.MPEG_URL.test(source.file)) {
        sources.push(
            new VideoUrlSource({
                name: key,
                duration: duration,
                url: source.file,
                container: "video/mp4"
            }),
        );
    }

    return sources;
} 

/**
 * Checks whether TED supplies a native playable source for a video.
 * @param {Object} videoData Video data from GraphQL or search API
 * @param {Object} [extraData] Extra metadata for the video
 * @returns {boolean} True if video has playable sources
 */
function hasPlayableVideoSources(videoData, extraData) {
    const hlsUrl = videoData?.hlsUrl || extraData?.hlsUrl || '';
    if (hlsUrl && typeof hlsUrl === 'string' && hlsUrl.trim() !== '') {
        return true;
    }
    
    const nativeDownloads = videoData?.nativeDownloads || extraData?.nativeDownloads;
    if (nativeDownloads && (nativeDownloads.low || nativeDownloads.medium || nativeDownloads.high)) {
        return true;
    }
    
    const playerData = videoData?.playerData ? safeJsonParse(videoData.playerData) : null;
    if (playerData?.resources?.hls?.stream) {
        return true;
    }
    
    return false;
}

/**
 * Extracts the external URL (e.g., YouTube) from video data if it's externally hosted content
 * @param {Object} videoData Video data from GraphQL or search API
 * @param {Object} [extraData] Extra metadata for the video
 * @returns {string|null} External URL or null if not externally hosted
 */
function getExternalContentUrl(videoData, extraData) {
    const playerDataStr = videoData?.playerData || extraData?.playerData;
    const playerData = playerDataStr ? safeJsonParse(playerDataStr) : null;

    // TED stores external content as { service: "YouTube", code: "VIDEO_ID" }
    if (playerData?.external?.service && playerData?.external?.code) {
        const service = playerData.external.service.toLowerCase();
        const code = playerData.external.code;
        
        if (service === 'youtube') {
            return `https://www.youtube.com/watch?v=${code}`;
        }
        if (service === 'vimeo') {
            return `https://vimeo.com/${code}`;
        }
    }

    if (playerData?.external?.uri) {
        return playerData.external.uri;
    }
    
    return null;
}

/**
 * Tests whether TED positively identifies a talk as externally hosted.
 * Missing playback metadata alone is inconclusive during API outages.
 * @param {Object} videoData Video data from GraphQL or search API
 * @param {Object} [extraData] Extra metadata for the video
 * @returns {boolean} True for known external content
 */
function isKnownExternalContent(videoData, extraData) {
    return !hasPlayableVideoSources(videoData, extraData)
        && Boolean(getExternalContentUrl(videoData, extraData));
}

function shouldShowContent(videoData, extraData) {
    return _settings.showExternalContent || !isKnownExternalContent(videoData, extraData);
}

/**
 * Builds a nested entry only for known external content. Native and temporarily unenriched
 * talks remain normal video entries whose details are resolved when opened.
 * @param {Object} videoData Video data from GraphQL, search API or web page
 * @param {Object} [extraData] Extra metadata for the video
 * @returns {PlatformVideo|PlatformVideoDetails|PlatformNestedMediaContent} Feed object
 */
function convertToFeedEntry(videoData, extraData) {
    if (isKnownExternalContent(videoData, extraData)) {
        return convertToNestedMediaContent(videoData, extraData);
    }

    return convertToFeedItem(videoData, extraData);
}

/**
 * Extracts the content provider name from a URL
 * @param {string} url The URL to analyze
 * @returns {string} Provider name (e.g., "YouTube", "Vimeo") or "External Platform"
 */
function getContentProviderFromUrl(url) {
    if (!url) return 'External Platform';
    
    const urlLower = url.toLowerCase();
    if (urlLower.includes('youtube.com') || urlLower.includes('youtu.be')) {
        return 'YouTube';
    }
    if (urlLower.includes('vimeo.com')) {
        return 'Vimeo';
    }
    if (urlLower.includes('dailymotion.com')) {
        return 'Dailymotion';
    }
    
    return 'External Platform';
}

/**
 * Converts video data to PlatformNestedMediaContent for externally hosted content
 * @param {Object} videoData Video data from GraphQL or search API
 * @param {Object} [extraData] Extra metadata for the video
 * @returns {PlatformNestedMediaContent} Nested media content object
 */
function convertToNestedMediaContent(videoData, extraData) {
    extraData = extraData && typeof extraData === 'object' ? extraData : {};
    
    const slug = videoData.slug || '';
    const tedUrl = `${PLATFORM.TALKS_URL}/${slug}`;
    const thumbnailUrl = extractThumbnailUrl(videoData, extraData);
    
    const externalUrl = getExternalContentUrl(videoData, extraData);

    // Grayjay can hand an external contentUrl to the matching plugin.
    const contentUrl = externalUrl || tedUrl;
    const contentProvider = getContentProviderFromUrl(externalUrl);

    const speaker = extractSpeakerInfo(videoData, extraData);
    
    const author = createAuthorLink({
        id: speaker.id,
        firstname: speaker.firstname,
        lastname: speaker.lastname,
        slug: speaker.slug,
        photoUrl: speaker.photoUrl
    });
    
    const publishedAt = videoData?.publishedAt || extraData?.publishedAt || null;
    
    const thumbnailSources = thumbnailUrl ? [new Thumbnail(thumbnailUrl, 1)] : [];
    
    return new PlatformNestedMediaContent({
        id: new PlatformID(PLATFORM.NAME, videoData.canonicalUrl || extraData?.canonicalUrl || slug, config.id),
        name: videoData.title || 'Unknown TED Talk',
        author: author,
        datetime: dateToUnixSeconds(publishedAt),
        url: tedUrl,                            // The TED page where content is embedded
        contentUrl: contentUrl,                 // The actual external URL (YouTube, etc.) or TED URL
        contentName: videoData.title || 'Unknown TED Talk',
        contentDescription: videoData.description || '',
        contentProvider: contentProvider,       // e.g., "YouTube" or "External Platform"
        contentThumbnails: new Thumbnails(thumbnailSources)
    });
}

/**
 * Converts TED Talk video data to a feed item.
 * Returns a complete PlatformVideoDetails where the client reuses feed details and skips
 * getContentDetails, otherwise a lightweight PlatformVideo. See DETAILS_IN_FEED.
 * @param {Object} videoData TED Talk video data
 * @param {Object} [extraData] Extra metadata for the video
 * @returns {PlatformVideo|PlatformVideoDetails} Formatted platform video object
 * @throws {ScriptException} If videoData is invalid or missing required properties
 */
function convertToFeedItem(videoData, extraData) {

    if (!videoData || typeof videoData !== 'object') {
        throw new ScriptException('Invalid videoData: Expected an object');
    }
    
    // Ensure extraData is an object if provided
    extraData = extraData && typeof extraData === 'object' ? extraData : {};
    
    // Get the video slug with validation
    const slug = videoData.slug || '';
    if (!slug) {
        trace('Video slug is missing or empty');
    }
    
    const videoUrl = `${PLATFORM.TALKS_URL}/${slug}`;
    
    // Get speaker information, with fallbacks
    const speaker = extractSpeakerInfo(videoData, extraData);

    const viewedCount = videoData?.viewedCount || extraData?.viewedCount || 0;
    const publishedAt = videoData?.publishedAt || extraData?.publishedAt || null;
    const duration = safeParseInt(videoData.duration);

    // Create author link
    const author = createAuthorLink({
        id: speaker.id,
        firstname: speaker.firstname,
        lastname: speaker.lastname,
        slug: speaker.slug,
        photoUrl: speaker.photoUrl
    });
    
    // Extract thumbnails
    const thumbnails = extractThumbnails(videoData, extraData) || [];
    if (!thumbnails || !thumbnails.length) {
       trace('No thumbnails found for video');
    }
    
    const base = {
        id: new PlatformID(PLATFORM.NAME, videoData.canonicalUrl || extraData?.canonicalUrl || slug || 'unknown', config.id),
        name: videoData.title || 'Unknown TED Talk',
        thumbnails: new Thumbnails(thumbnails),
        duration: duration,
        viewCount: viewedCount,
        url: videoUrl,
        uploadDate: dateToUnixSeconds(publishedAt),
        shareUrl: videoUrl,
        author: author
    };

    // Clients that do not reuse feed details call getContentDetails on open, so a lightweight
    // item is enough and avoids building sources for rows that may never be opened.
    if (!DETAILS_IN_FEED) {
        return new PlatformVideo(base);
    }

    // Merge videoData and extraData for video sources - extraData contains hlsUrl from GraphQL
    const mergedVideoData = {
        ...videoData,
        hlsUrl: videoData.hlsUrl || extraData?.hlsUrl,
        nativeDownloads: videoData.nativeDownloads || extraData?.nativeDownloads,
        playerData: videoData.playerData || extraData?.playerData
    };

    // The client reuses this object verbatim and never calls getContentDetails for it, so an
    // incomplete one is unrecoverable. Fall back to a lightweight item instead of shipping one.
    const playerData = mergedVideoData.playerData ? safeJsonParse(mergedVideoData.playerData) : null;
    const subtitles = deriveSubtitles(playerData);
    if (!playerData || subtitles === null) {
        return new PlatformVideo(base);
    }

    // Build description with topic links
    const topics = videoData.topics?.nodes || extraData?.topics?.nodes || [];
    const description = buildDescriptionWithTopics(videoData.description || extraData?.description || '', topics);

    const details = new PlatformVideoDetails({
        ...base,
        description: description,
        video: createVideoSources(mergedVideoData),
        subtitles: subtitles
    });

    const relatedVideos = Array.isArray(videoData?.relatedVideos) ? videoData.relatedVideos : [];
    
    details.getContentRecommendations = () => {
        return source.getContentRecommendations(videoUrl, relatedVideos);
    };

    return details;
}

/**
 * Extracts speaker information from video data
 * @param {Object} videoData Video data
 * @param {Object} extraData Additional metadata
 * @returns {Object} Speaker information with firstname, lastname, slug, id, and photoUrl
 */
function extractSpeakerInfo(videoData, extraData) {
    const isValidSpeaker = (obj) => obj && typeof obj === 'object' && (obj.firstname || obj.lastname || obj.slug);

    const speakerSources = [
        videoData.speakers?.nodes?.[0],
        extraData?.speakers?.nodes?.[0],
        extraData?.speakers?.[0],
        extraData?.speaker,
        Array.isArray(videoData.speakers) ? videoData.speakers[0] : null
    ].filter(source => isValidSpeaker(source));

    const speakerSource = speakerSources[0];

    if (speakerSource) {
        // Playlist pages carry names and slug only, the enrichment carries id and photo. Matched
        // on slug so a co-speaker's photo can never end up on someone else's name.
        const details = speakerSources.find(source => source !== speakerSource
            && source.slug === speakerSource.slug
            && (source.id || source.photoUrl)) || {};

        return {
            firstname: speakerSource.firstname || '',
            lastname: speakerSource.lastname || '',
            slug: speakerSource.slug || '',
            id: speakerSource.id || details.id || '',
            photoUrl: speakerSource.photoUrl || details.photoUrl || PLATFORM.FALLBACK_AVATAR
        };
    }
    
    if (typeof videoData.speakers === 'string' && videoData.speakers.trim()) {
        return {
            firstname: videoData.speakers.trim(),
            lastname: '',
            slug: '',
            id: '',
            photoUrl: PLATFORM.FALLBACK_AVATAR
        };
    }
    
    return {
        firstname: 'Unknown Speaker',
        lastname: '',
        slug: '',
        id: '',
        photoUrl: PLATFORM.FALLBACK_AVATAR
    };
}

/**
 * Extracts thumbnails from video data
 * @param {Object} videoData Video data
 * @param {Object} extraData Additional metadata
 * @returns {Array} Array of Thumbnail objects
 */
function extractThumbnails(videoData, extraData) {
    // Try to get thumbnails from photo_sizes - filter out items without valid URLs
    let thumbnails = (videoData?.photos?.[0]?.photo_sizes || [])
        .filter(size => size && size.url)
        .map(size => new Thumbnail(size.url, safeParseInt(size.height)));

    // Try to add a thumbnail from playerData if none found
    if (thumbnails.length === 0) {
        const thumbUrl = extractThumbnailUrl(videoData) || extractThumbnailUrl(extraData);
        if (thumbUrl) {
            thumbnails.push(new Thumbnail(thumbUrl, 0));
        }
    }

    return thumbnails;
}


/**
 * Creates a PlatformAuthorLink for a speaker
 * @param {Object} speaker Speaker data
 * @returns {PlatformAuthorLink} Author link object
 */
function createAuthorLink(speaker) {
    if (!speaker) return null;

    return new PlatformAuthorLink(
        new PlatformID(
            PLATFORM.NAME,
            speaker.id || '',
            config.id
        ),
        formatName(speaker.firstname, speaker.lastname),
        `${PLATFORM.SPEAKER_URL}/${speaker.slug || ''}${speaker.id ? `?speakerId=${speaker.id}` : ''}`,
        speaker.photoUrl || PLATFORM.FALLBACK_AVATAR
    );
}

/**
 * Creates video source objects for a video
 * @param {Object} video Video data
 * @returns {Array} Array of video source objects
 */
function createVideoSources(video) {
    const videoSources = [];

    const duration = safeParseInt(video.duration);

    // Add HLS source if available
    if (video.hlsUrl) {
        videoSources.push(
            new HLSSource({
                name: 'HLS',
                url: video.hlsUrl,
                duration: duration,
                priority: true
            })
        );
    }

    // Add MP4 sources for different qualities
    if (video?.nativeDownloads?.low) {
        videoSources.push(
            new VideoUrlSource({
                name: VIDEO_QUALITY.LOW.name,
                duration: duration,
                url: video?.nativeDownloads?.low,
                width: VIDEO_QUALITY.LOW.width,
                height: VIDEO_QUALITY.LOW.height,
                container: "video/mp4"
            })
        );
    }

    // Medium quality
    if (video?.nativeDownloads?.medium) {
        videoSources.push(
            new VideoUrlSource({
                name: VIDEO_QUALITY.MEDIUM.name,
                duration: duration,
                url: video?.nativeDownloads?.medium,
                width: VIDEO_QUALITY.MEDIUM.width,
                height: VIDEO_QUALITY.MEDIUM.height,
                container: "video/mp4"
            })
        );
    }

    // High quality
    if (video?.nativeDownloads?.high) {
        videoSources.push(
            new VideoUrlSource({
                name: VIDEO_QUALITY.HIGH.name,
                duration: duration,
                url: video?.nativeDownloads?.high,
                width: VIDEO_QUALITY.HIGH.width,
                height: VIDEO_QUALITY.HIGH.height,
                container: "video/mp4"
            })
        );
    }

    const playerDataStr = video?.playerData;

    if (!videoSources.length && playerDataStr) {

        try {
            const playerData = safeJsonParse(playerDataStr);
            if (playerData?.resources) {
                for (let key in playerData.resources) {
                    const sourceType = playerData.resources[key];
                    if (Array.isArray(sourceType)) {
                        sourceType.forEach(source => {
                            videoSources.push(...mapSource(source, key, duration));
                        });
                    } else {
                        videoSources.push(...mapSource(sourceType, key, duration));
                    }
                }
            }
        } catch (playerDataError) {
            trace(`Error processing playerData: ${playerDataError.message}`);
        }
    }

    // Audio source has diferent duration, and includes some narration in the beginning. Could be used in an audio only mode 
    // if(video.audioDownload){ 
    //     audioSources.push( 
    //         new AudioUrlSource({ 
    //             name: "audio/mp3", 
    //             duration: duration, 
    //             url: video.audioDownload, 
    //             container: "audio/mp3" 
    //         }) 
    //     ); 
    // } 
 
    return new VideoSourceDescriptor(videoSources);
}

// ====================== UTILITY FUNCTIONS ======================

/**
 * Safely parses JSON and returns null on error
 * @param {string} jsonString JSON string to parse
 * @returns {Object|null} Parsed object or null on error
 */
function safeJsonParse(jsonString) {
    try {
        return JSON.parse(jsonString);
    } catch (error) {
        trace(`JSON parse error: ${error.message}`);
        return null;
    }
}

/**
 * Safely parses an integer with fallback to 0
 * @param {*} value Value to parse
 * @returns {number} Parsed integer or 0 if invalid
 */
function safeParseInt(value) {
    try {
        const parsed = parseInt(value);
        return isNaN(parsed) ? 0 : parsed;
    } catch (error) {
        trace(`Integer parse error: ${error.message}`);
        return 0;
    }
}

/**
 * Checks if a URL matches a given pattern
 * @param {string} url URL to check
 * @param {RegExp} pattern Regular expression pattern
 * @returns {boolean} True if URL matches pattern
 */
function urlMatches(url, pattern) {
    try {
        return pattern.test(url);
    } catch (error) {
        trace(`URL pattern matching error: ${error.message}`);
        return false;
    }
}

/**
 * Extracts the speaker slug from a TED speaker URL
 * @param {string} url TED speaker URL
 * @returns {string|null} Speaker slug or null if not found
 */
function extractSpeakerSlug(url) {
    try {
        const match = REGEX.SPEAKER_SLUG.exec(url);
        return match ? match[1] : null;
    } catch (error) {
        trace(`Exception in extractSpeakerSlug: ${error.message}`);
        return null;
    }
}

/**
 * Extracts the speaker ID from a TED speaker URL
 * @param {string} url TED speaker URL
 * @returns {string|null} Speaker ID or null if not found
 */
function extractSpeakerId(url) {
    try {
        const match = REGEX.SPEAKER_ID.exec(url);
        return match ? match[1] : null;
    } catch (error) {
        trace(`Exception in extractSpeakerId: ${error.message}`);
        return null;
    }
}

/**
 * Extracts the video slug from a TED Talk URL
 * @param {string} url TED Talk URL
 * @returns {string|null} Video slug or null if not found
 */
function extractTedSlug(url) {
    try {
        const match = REGEX.TALK_SLUG.exec(url);
        return match ? match[1] : null;
    } catch (error) {
        trace(`Exception in extractTedSlug: ${error.message}`);
        return null;
    }
}

/**
 * Extracts talk slugs from HTML content
 * @param {string} html HTML content
 * @param {string} channelSlug Channel slug for filtering
 * @returns {Array<string>} Array of talk slugs
 */
function extractTalkSlugsFromHtml(html, channelSlug) {
    try {
        // Use regex to extract talk URLs - more reliable than DOM parsing
        const talkUrlRegex = /href=['"]\/talks\/([a-z0-9_]+)['"]/gi;
        const slugs = [];
        let match;
        while ((match = talkUrlRegex.exec(html)) !== null) {
            const slug = match[1];
            // Avoid duplicates
            if (slug && !slugs.includes(slug)) {
                slugs.push(slug);
            }
        }
        
        if (slugs.length > 0) {
            return slugs;
        }
        
        // Fallback to DOM parsing if regex fails
        const doc = domParser.parseFromString(html, 'text/html');
        const talkElements = doc.querySelectorAll('div.profile-talks__talk');
        
        if (!talkElements || talkElements.length === 0) {
            return [];
        }
        
        return Array
            .from(talkElements)
            .map(el => {
                // Try multiple selectors to find the link
                const a = el.querySelector('a[href*="/talks/"]') || el.querySelector('a.ga-link');
                if (!a) return null;
                
                const relativelinkUrl = a.getAttribute('href');
                if (!relativelinkUrl || !relativelinkUrl.includes('/talks/')) return null;
                
                return extractTedSlug(`${PLATFORM.BASE_URL}${relativelinkUrl}`);
            })
            .filter(Boolean);
    } catch (error) {
        trace(`Error extracting talk slugs from HTML: ${error.message}`);
        return [];
    }
}

/**
 * Formats a speaker's description combining multiple fields
 * @param {Object} speaker Speaker data
 * @returns {string} Formatted description
 */
function formatSpeakerDescription(speaker) {
    let description = speaker.description || '';
    
    if (speaker.whoTheyAre) {
        description += `<p>${speaker.whoTheyAre}</p>`;
    }
    
    if (speaker.whyListen) {
        description += '<p><b>Why Listen</b></p>';
        description += `${speaker.whyListen}\n`;
    }
    
    if (speaker.whatOthersSay) {
        description += '<p><b>What Others Say</b></p>';
        description += `${speaker.whatOthersSay}\n`;
    }
    
    return description;
}

/**
 * Formats a full name from first and last name
 * @param {string} firstName First name
 * @param {string} lastName Last name
 * @returns {string} Formatted full name
 */
function formatName(firstName, lastName) {
    return `${firstName || ''} ${lastName || ''}`.trim() || 'Unknown';
}

/**
 * Extracts thumbnail URL from video data
 * @param {Object} video Video data
 * @param {Object} [extraData] Extra metadata for the video
 * @returns {string} Thumbnail URL or empty string
 */
function extractThumbnailUrl(video, extraData) {
    try {
        // Check playerData.thumb first (most reliable, includes CDN optimization)
        const playerDataStr = video?.playerData || extraData?.playerData;
        if (playerDataStr) {
            const playerData = safeJsonParse(playerDataStr);
            if (playerData?.thumb) {
                return playerData.thumb;
            }
        }
        
        // Check primaryImageSet from GraphQL
        const primaryImageSet = video?.primaryImageSet || extraData?.primaryImageSet;
        if (primaryImageSet && primaryImageSet.length > 0 && primaryImageSet[0].url) {
            // Add TED CDN prefix for optimization
            return `https://pi.tedcdn.com/r/${primaryImageSet[0].url.replace(/^https?:\/\//, '')}?quality=89&w=600`;
        }
        
        return '';
    } catch (error) {
        trace(`Error extracting thumbnail: ${error.message}`);
        return '';
    }
}

/**
 * Converts a date string to Unix timestamp in seconds
 * @param {string} date Date string
 * @returns {number|null} Unix timestamp or null if invalid
 */
function dateToUnixSeconds(date) {
    try {
        if (!date) {
            return null;
        }
        return Math.round(Date.parse(date) / 1000);
    } catch (error) {
        trace(`Date conversion error: ${error.message}`);
        return null;
    }
}

/**
 * Creates an empty content pager
 * @returns {ContentPager} Empty pager
 */
function createEmptyPager() {
    return new ContentPager([], false);
}

/**
 * Logs a message and optionally shows a toast notification (when verbose setting is enabled)
 * @param {string} msg Message
 * @param {Object} [options] Options
 * @param {boolean} [options.showToast=false] Whether to show a toast notification
 */
function trace(msg, { showToast = false } = {}) {
    if (_settings.verboseNotifications || showToast) {
        bridge.toast(msg);
    }
    log(msg);
}

// ====================== GRAPHQL QUERIES ======================

const GQL_QUERIES = {
    HOME_VIDEOS: `
query Videos($first: Int, $after: String) {
	videos(first: $first, after: $after) {
		nodes {
			nativeDownloads {
				high
				low
				medium
			}
            canonicalUrl
			audioDownload
			playerData
			duration
			description
			hlsUrl
			slug
			id
			language
			publishedAt
			title
			viewedCount
			speakers {
				nodes {
					id
					firstname
					lastname
					slug
					photoUrl
				}
			}
		}
		pageInfo {
			endCursor
			hasNextPage
			startCursor
		}
	}
}

`,
    VIDEO_DETAILS: `
    query shareLinks($slug: String!, $language: String) {
        videos(
            slug: [$slug]
            language: $language
            first: 1
            isPublished: [true, false]
            channel: ALL
        ) {
            nodes {
                id
                viewedCount
                playerData
                publishedAt
                hlsUrl
                canonicalUrl
                title
                description
                topics {
                    nodes {
                        id
                        name
                        slug
                    }
                }
                relatedVideos {
                    title
                    slug
                    id
                    duration
                    hlsUrl
                    canonicalUrl
                    videoDownloads {
                        nodes {
                            url
                        }
                    }
                    viewedCount
                    publishedAt
                    recordedOn
                    speakers {
                        nodes {
                            id
                            firstname
                            lastname
                            slug
                            photoUrl
                        }
                    }
                }
                duration
                speakers {
                    nodes {
                        id
                        firstname
                        lastname
                        slug
                        photoUrl
                    }
                }
                nativeDownloads {
                    low
                    medium
                    high
                }
            }
        }
    }
    `,
    VIDEOS_INFO_BY_SLUG: `
    query videosInfo($slug: [String!]) {
        videos(slug: $slug, first: 50, isPublished: [true, false], channel: ALL) {
            nodes {
                id
                title
                slug
                hlsUrl
                description
                canonicalUrl
                viewedCount
                publishedAt
                duration
                playerData
                speakers {
                    nodes {
                        id
                        firstname
                        lastname
                        slug
                        photoUrl
                    }
                }
                topics {
                    nodes {
                        id
                        name
                        slug
                    }
                }
                nativeDownloads {
                    low
                    medium
                    high
                }
            }
            totalCount
        }
    }
    `,
    SPEAKER_BY_ID: `
    query acmeSpeaker($id: ID!) {
        acmeSpeaker(id: $id) {
            id
            cite
            firstname
            lastname
            title
            photoUrl
            description
            slug
            whyListen
            whatOthersSay
            whoTheyAre
        }
    }
    `,
    RELATED: `
    query related($slug: String!, $language: String) {
	videos(
		slug: [$slug]
		language: $language
		first: 1
		isPublished: [true]
		channel: ALL
	) {
		nodes {
			relatedVideos {
				title
				slug
				id
				duration
				hlsUrl
                canonicalUrl
				videoDownloads {
					nodes {
						url
					}
				}
				viewedCount
				publishedAt
				recordedOn
				speakers {
					nodes {
						id
						firstname
						lastname
						slug
						photoUrl
					}
				}
			}
		}
	}
}
    `,
    TOPIC_INFO: `
    query topicInfo($slug: String!) {
        topic(slug: $slug) {
            id
            name
            slug
        }
    }
    `
};

log('LOADED');
