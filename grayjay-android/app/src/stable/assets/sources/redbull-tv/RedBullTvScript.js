/**
 * Red Bull TV Grayjay Plugin
 * 
 */

const PLATFORM = "Red Bull TV";
const BASE_URL = "https://www.redbull.com";
const DEFAULT_THUMBNAIL_URL = `https://www.redbull.com/favicon.ico`;
const API_BASE_URL = "https://www.redbull.com/v3/api/graphql/v1/v3";
const PLAYER_API_URL = "https://api-player.redbull.com/rbcom/videoresource";
const PODCAST_PLAYER_API_URL = "https://api-player.redbull.com/rbcom/podcast";

const HEADERS = {
    'Accept': 'application/vnd.rbgemc+json,application/json;q=0.8',
    'Accept-Language': 'en-US,en;q=0.9'
};

// Browser TLS impersonation support (optional package)
const IS_IMPERSONATION_AVAILABLE = (typeof httpimp !== 'undefined');
const IS_DESKTOP = bridge.buildPlatform === "desktop";
const IMPERSONATION_TARGET = IS_DESKTOP ? 'chrome136' : 'chrome131_android';

if (IS_IMPERSONATION_AVAILABLE) {
    const httpImpClient = httpimp.getDefaultClient(true);
    if (httpImpClient.setDefaultImpersonateTarget) {
        httpImpClient.setDefaultImpersonateTarget(IMPERSONATION_TARGET);
    }
}

/**
 * Content types that cannot be played directly through the player APIs.
 * These are typically profile pages, articles, or container types.
 */
const NON_PLAYABLE_TYPES = [
    'event-profiles',
    'team-profiles', 
    'person-profiles',
    'stories',
    'audio-series',
    'collections'
];

/**
 * Entity types that represent non-video content (athletes, teams, articles).
 * These should be filtered out when creating video content items.
 */
const NON_VIDEO_ENTITY_TYPES = ['athlete', 'team', 'story'];

// Pagination
const ITEMS_PER_PAGE = 20;
const CHANNELS_PER_PAGE = 100;
const PREFERRED_LOCALE = 'en-INT';

// ============================================================================
// ATHLETE AUTHOR OVERRIDE FLOW
// ============================================================================
//
// When browsing an athlete's channel, videos should show
// that athlete as the author. However, some videos (like "Winter Heroes" episodes)
// have multiple related athletes, making it ambiguous which one to display.
//
// Solution: Pass the athlete ID as a URL query param hint from channel to video.
//
// Flow:
// 1. getChannelContents() fetches athlete metadata and creates a PlatformAuthorLink
// 2. fetchAthleteVideos() receives this author and passes it to createPlatformVideo()
// 3. createPlatformVideo() appends ?athlete=<uuid> to video URLs when author is provided
// 4. When user opens video, getContentDetails() extracts the athlete UUID from URL
// 5. getVideoTypeContentDetails() passes UUID to fetchPlayerAndAthlete()
// 6. fetchPlayerAndAthlete() fetches that specific athlete instead of guessing
//
// Without the hint (e.g., from getHome), videos show "Red Bull TV" as author.
//
// Key functions:
// - createPlatformVideo(): Adds ?athlete=<uuid> to URL
// - getContentDetails(): Extracts athlete UUID from URL
// - fetchPlayerAndAthlete(): Fetches specific athlete if UUID provided
//
// ============================================================================

// ============================================================================
// URL MATCHING REGEXES
// ============================================================================

/**
 * Matches content detail URLs (videos, films, episodes, live events, podcasts).
 * Pattern: /redbull.com/{locale}/{content-type}/{slug}
 * 
 * Supported content types:
 *   - videos, films, episodes, live-events, events, live, podcast-episodes
 * 
 * @example
 *   - https://www.redbull.com/int-en/films/the-fourth-phase
 *   - https://www.redbull.com/gr-el/episodes/lisbon-highlights
 */
const CONTENT_DETAILS_URL_REGEX = /redbull\.com\/.*?\/(?:videos|films|episodes|live-events|events|live|podcast-episodes)\//;

/**
 * Matches channel URLs (events, athletes).
 * Pattern: /redbull.com/{locale}/(events|athlete)/{slug}
 */
const CHANNEL_URL_REGEX = /redbull\.com\/.*?\/(?:events|athlete)\//;

/**
 * Matches playlist URLs (shows, discover pages).
 * Pattern: /redbull.com/{locale}/(shows|discover)/{slug}
 */
const PLAYLIST_URL_REGEX = /redbull\.com\/.*?\/(?:shows|discover)\//;

/**
 * Extracts locale and slug from podcast episode URLs.
 * Captures:
 *   Group 1: Locale (2-3 chars, hyphen, 2-3 chars) e.g., 'int-en', 'gr-el'
 *   Group 2: Episode slug e.g., 'save-your-game-s1-trailer'
 */
const PODCAST_EPISODE_REGEX = /\/([a-z]{2,3}-[a-z]{2,3})\/podcast-episodes\/([^\/\?]+)/i;

/**
 * Extracts locale and slug from show episode URLs.
 * Captures:
 *   Group 1: Locale e.g., 'fr-ca', 'int-en'
 *   Group 2: Episode slug e.g., 'quebec-city-rampage'
 */
const SHOW_EPISODE_REGEX = /\/([a-z]{2,3}-[a-z]{2,3})\/episodes\/([^\/\?]+)/i;

/**
 * Extracts locale and slug from standard video URLs.
 * Captures:
 *   Group 1: Locale e.g., 'lat-es', 'int-de'
 *   Group 2: Video slug e.g., 'futbol-freestyle-tricks'
 */
const VIDEO_URL_REGEX = /\/([a-z]{2,3}-[a-z]{2,3})\/videos\/([^\/\?]+)/i;

/**
 * Matches film detail URLs and extracts locale and slug.
 * Captures:
 *   Group 1: Locale e.g., 'int-en', 'de-de'
 *   Group 2: Film slug e.g., 'where-the-trail-ends'
 */
const FILM_URL_REGEX = /\/([a-z]{2,3}-[a-z]{2,3})\/films\/([^\/\?]+)/i;

/**
 * Parses channel URLs to extract locale, type, and slug.
 * Captures:
 *   Group 1: Locale prefix e.g., 'int-en'
 *   Group 2: Channel type e.g., 'shows', 'events', 'athlete'
 *   Group 3: Slug e.g., 'red-bull-rampage'
 */
const CHANNEL_PARSE_REGEX = /redbull\.com\/([^\/]+)\/(shows|events|athlete)\/([^\/]+)/;

/**
 * Extracts slug from show playlist URLs.
 * Captures:
 *   Group 1: Show slug e.g., 'red-bull-signature-series'
 */
const SHOW_PLAYLIST_REGEX = /redbull\.com\/.*?\/shows\/([^\/]+)/;

/**
 * Extracts slug from discover/collection URLs.
 * Captures:
 *   Group 1: Collection slug e.g., 'best-of-2024'
 */
const DISCOVER_PLAYLIST_REGEX = /redbull\.com\/.*?\/discover\/([^\/]+)/;

Type.Feed.Playlists = "Playlists";

let config = {};
let state = {};

/**
 * @param {SourceConfig} conf 
 * @param {any} settings 
 * @param {string} saveStateStr 
 */
source.enable = function (conf, settings, saveStateStr) {
    config = conf ?? {};
    if (!saveStateStr) {
        state = {};
    } else {
        try {
            state = JSON.parse(saveStateStr);
        } catch (e) {
            log("Failed to parse saved state: " + formatError(e));
            state = {};
        }
    }
};


/**
 * @returns {VideoPager}
 */
source.getHome = function () {
    const liveEvents = fetchLiveEvents();
    const discoverPager = fetchFeed("discover", 0);
    
    // Combine live events with the first page of discover feed
    const combined = liveEvents.concat(discoverPager.results);
    
    // Deduplicate based on ID to avoid showing the same video twice if it appears in both feeds
    const seenIds = new Set();
    const uniqueVideos = combined.filter(video => {
        if (seenIds.has(video.id.value)) {
            return false;
        }
        seenIds.add(video.id.value);
        return true;
    });
    
    return new RedBullPager(uniqueVideos, discoverPager.hasMore, "discover", 1, null);
};

/**
 * @returns {string}
 */
source.saveState = function () {
    return JSON.stringify(state);
};

// --- Channel Support ---

source.isChannelUrl = function(url) {
    return CHANNEL_URL_REGEX.test(url) || url === "https://www.redbull.com";
};

source.getChannel = function(url) {
    if (url === "https://www.redbull.com") {

        const links = {
            "Soundcloud": "https://soundcloud.com/redbull",
            "Mixcloud": "https://www.mixcloud.com/redbullradio",
            "Dailymotion": "https://www.dailymotion.com/RedBull",
            "Twitch": "https://www.twitch.tv/redbull",
            "Tiktok": "https://www.tiktok.com/@redbull",
            "Facebook": "https://facebook.com/redbull",
            "Instagram": "https://instagram.com/redbull",
            "X": "https://twitter.com/redbull",
            "Spotify": "https://open.spotify.com/user/redbull",
            "YouTube": "https://www.youtube.com/@redbull",
            "Vimeo": "https://vimeo.com/redbull",
            "Pinterest": "https://www.pinterest.com/redbull",
            "Website": "https://www.redbull.com",
            "RedBull Racing YouTube": "https://www.youtube.com/@redbullracing",
            "RedBull Racing Twitter": "https://twitter.com/redbullracing",
            "RedBull Racing Instagram": "https://instagram.com/redbullracing",
            "RedBull Racing TikTok": "https://www.tiktok.com/@redbullracing",
            "RedBull USA TikTok": "https://www.tiktok.com/@redbullusa",
            "Apple Podcasts": "https://podcasts.apple.com/us/channel/id6442454287",
            "RedBull Gaming Facebook": "https://www.facebook.com/gaming/RedBull",
            "RedBull Gaming YouTube": "https://www.youtube.com/@redbullgaming",
            "RedBull Gaming Twitter": "https://twitter.com/redbullgaming",
            "RedBull Gaming Instagram": "https://instagram.com/redbullgaming",
            "RedBull Gaming Tiktok": "https://tiktok.com/@redbullgaming"
        }

        return new PlatformChannel({
            id: new PlatformID(PLATFORM, "RedBull", config.id || ""),
            name: "Red Bull TV",
            thumbnail: DEFAULT_THUMBNAIL_URL,
            banner: "",
            subscribers: 0,
            description: "Red Bull TV",
            url: url,
            links: links
        });
    }

    const { type, slug, locale } = parseChannelUrl(url);
    if (!type || !slug) throw new ScriptException("Invalid channel URL");

    // For athletes, batch both metadata and social links requests together
    if (type === "person-profiles") {
        const result = fetchAthleteChannelData(slug, locale);
        if (!result.metadata) throw new ScriptException("Channel not found");
        
        let links = Object.assign({}, result.socialLinks, { "Red Bull TV": url });
        
        return new PlatformChannel({
            id: new PlatformID(PLATFORM, result.metadata.id, config.id || ""),
            name: result.metadata.title,
            thumbnail: result.metadata.image,
            banner: result.metadata.image,
            subscribers: 0,
            description: result.metadata.description,
            url: url,
            links: links
        });
    }

    // For non-athlete channels, just fetch metadata
    const metadata = fetchChannelMetadata(type, slug, locale);
    if (!metadata) throw new ScriptException("Channel not found");

    return new PlatformChannel({
        id: new PlatformID(PLATFORM, metadata.id, config.id || ""),
        name: metadata.title,
        thumbnail: metadata.image,
        banner: metadata.image,
        subscribers: 0,
        description: metadata.description,
        url: url,
        links: { "Red Bull TV": url }
    });
};

source.getChannelCapabilities = function () {
    return {
        types: [Type.Feed.Videos, Type.Feed.Playlists],
        sorts: [Type.Order.Chronological]
    };
};

source.getChannelContents = function(url, type, order, filters) {
    if (type === Type.Feed.Playlists) {
        return source.getChannelPlaylists(url);
    }

    // Get channel info to use as author for videos
    let author = null;
    try {
        const channel = source.getChannel(url);
        if (channel) {
            author = new PlatformAuthorLink(
                channel.id,
                channel.name,
                channel.url,
                channel.thumbnail
            );
        }
    } catch (e) {
        log("Failed to build channel author: " + formatError(e));
        // Fall back to default author if channel fetch fails
    }

    return getChannelVideos(url, null, author);
};

source.getChannelPlaylists = function(url, pager) {
    if (url === "https://www.redbull.com") {
        if (pager && pager instanceof RedBullPlaylistPager) {
            return pager.nextPage();
        }
        return fetchPlaylists(0);
    }

    const { type, slug, locale } = parseChannelUrl(url);

    if (pager && pager instanceof RedBullPlaylistPager) {
        return pager.nextPage();
    }

    if (type === "event-profiles" || type === "person-profiles") {
        const metadata = fetchChannelMetadata(type, slug, locale);
        if (!metadata) return new RedBullPlaylistPager([], false, 0);
        return fetchRelatedPlaylists(metadata.id, 0);
    }

    return new RedBullPlaylistPager([], false, 0);
};


// --- Playlist Support ---

source.isPlaylistUrl = function(url) {
    return PLAYLIST_URL_REGEX.test(url);
};

source.getPlaylist = function(url) {
    // Try to match shows URL
    let match = url.match(SHOW_PLAYLIST_REGEX);
    if (match) {
        return getShowPlaylistDetails(match[1]);
    }
    
    // Try to match discover/collection URL
    match = url.match(DISCOVER_PLAYLIST_REGEX);
    if (match) {
        return getCollectionPlaylistDetails(match[1]);
    }
    
    throw new ScriptException("Invalid playlist URL");
};

// --- Search Suggestions and Capabilities ---

source.searchSuggestions = function (query) {
    return [];
};

source.getSearchCapabilities = () => {
    return {
        types: [Type.Feed.Mixed, Type.Feed.Videos],
        sorts: [Type.Order.Chronological]
    };
};

source.search = function (query, type, order, filters) {
    return fetchSearch(query, 0);
};

source.searchChannels = function (query) {
    const encodedQuery = encodeURIComponent(query);
    const url = `${API_BASE_URL}/feed/en-INT?rb3SearchString=${encodedQuery}&rb3SearchTab=athletes&rb3Schema=v1:searchResults&page[limit]=${CHANNELS_PER_PAGE}`;

    const response = httpGET({ url: url });
    if (!response.isOk) return new ChannelPager([], false);

    const items = response.body?.data?.results || [];
    const channels = deduplicateChannelItems(items);

    // The athlete search API ignores page[offset], so all results come in one request
    return new ChannelPager(channels, false);
};

/**
 * Determines if a URL points to a playable content details page.
 * Matches URLs containing content type paths like /videos/, /films/, /episodes/, etc.
 * 
 * Regex Pattern: /redbull\.com\/.*?\/(?:videos|films|episodes|live-events|events|live|podcast-episodes)\//
 * Components:
 *   - redbull\.com - Domain literal
 *   - \/.*?\/ - Minimal match for locale path segment (e.g., /int-en/)
 *   - (?:...) - Non-capturing group of content types
 *   - videos|films|episodes|... - Content type alternation
 * 
 * @param {string} url - URL to check
 * @returns {boolean} True if URL matches content details pattern
 * 
 * Example matches:
 *   - https://www.redbull.com/int-en/films/the-fourth-phase
 *   - https://www.redbull.com/gr-el/episodes/lisbon-highlights
 *   - https://www.redbull.com/int-en/podcast-episodes/save-your-game
 */
source.isContentDetailsUrl = function(url) {
    return CONTENT_DETAILS_URL_REGEX.test(url);
};

/**
 * Fetches detailed information about a specific piece of content (video, episode, film, podcast).
 * 
 * For locale-specific URLs (with 2-3 char locale prefix), bypasses the config/pages API
 * which returns "Page Not Found" errors, and instead queries the GraphQL API directly.
 * 
 * @param {string} url - Full Red Bull content URL
 * @returns {PlatformVideoDetails} Detailed content information including streams, subtitles, metadata
 * @throws {ScriptException} If content is not found or page config fails
 * 
 * Handled URL patterns:
 *   - Podcast episodes: /{locale}/podcast-episodes/{slug} → audio-episodes
 *   - Show episodes: /{locale}/episodes/{slug} → episode-videos
 *   - Standard videos: /{locale}/videos/{slug} → videos
 *   - Films/events/live: Uses config/pages API (works for these types)
 * 
 * Locale format in URLs: {country}-{language} (e.g., gr-el, int-en, lat-es)
 * Converted to API format: {language}-{COUNTRY} (e.g., el-GR, en-INT, es-LAT)
 */
source.getContentDetails = function(url) {
    const path = url.replace(BASE_URL, "");

    // Extract athlete UUID hint from query param if present
    // URL format: ...?athlete=<uuid> or ...&athlete=<uuid>
    const athleteMatch = url.match(/[?&]athlete=([^&]+)/);
    const athleteUuid = athleteMatch ? athleteMatch[1] : null;

    const podcastMatch = path.match(PODCAST_EPISODE_REGEX);
    if (podcastMatch) {
        return getPodcastContentDetails(url, podcastMatch[1], podcastMatch[2]);
    }

    const episodeMatch = path.match(SHOW_EPISODE_REGEX);
    if (episodeMatch) {
        return getVideoTypeContentDetails(url, episodeMatch[1], episodeMatch[2], 'episode-videos', athleteUuid);
    }

    const videosMatch = path.match(VIDEO_URL_REGEX);
    if (videosMatch) {
        return getVideoTypeContentDetails(url, videosMatch[1], videosMatch[2], 'videos', athleteUuid);
    }

    const filmsMatch = path.match(FILM_URL_REGEX);
    if (filmsMatch) {
        return getVideoTypeContentDetails(url, filmsMatch[1], filmsMatch[2], 'films', athleteUuid);
    }

    // Fallback: fetch page config for other content types (live events, etc.)
    const configUrl = `${BASE_URL}/v3/config/pages?url=${path}`;
    const configResp = httpGET({ url: configUrl });
    
    if (!configResp.isOk) {
        throw new ScriptException("Failed to fetch page config");
    }
    
    const panels = configResp.body.data?.panels || [];
    const pageConfigPanel = panels.find(p => p.panelModule === "rbgemc-rb3/page-config/page-config-panel");
    
    if (pageConfigPanel?.config?.pageTitle?.includes("Page Not Found")) {
        throw new ScriptException("Page not found");
    }

    let endpoint = pageConfigPanel?.config?.endpoint;
    if (!endpoint) {
        // Fallback: look for any panel with an endpoint that looks like a feed or content
        const fallbackPanel = panels.find(p => p.config?.endpoint && (p.config.endpoint.includes("/feed/") || p.config.endpoint.includes("/content/")));
        if (fallbackPanel) {
            endpoint = fallbackPanel.config.endpoint;
        }
    }

    if (!endpoint) {
        const panelNames = panels.map(p => p.panelModule).join(", ");
        throw new ScriptException(`No page config endpoint found. Available panels: ${panelNames}`);
    }
    
    // Construct the full GraphQL URL
    // Endpoint from config is like /v3/feed/en-INT...
    // We need https://www.redbull.com/v3/api/graphql/v1/v3/feed/en-INT...
    // So we prepend https://www.redbull.com/v3/api/graphql/v1
    const fullEndpoint = `https://www.redbull.com/v3/api/graphql/v1${endpoint}&rb3Schema=v1:cardList`;
    
    const feedResp = httpGET({ url: fullEndpoint });
    if (!feedResp.isOk) throw new ScriptException("Failed to fetch content details from feed");
    
    const item = feedResp.body.data?.[0];
    if (!item) throw new ScriptException("No content data found");
    
    let contentId = item.id;
    
    // Check if the item points to another video (e.g. live event badge)
    if (item.badge?.reference?.id) {
        contentId = item.badge.reference.id;
    }

    const title = item.content?.title || item.title;
    const description = item.content?.standfirst || item.content?.subHeading || "";
    const image = extractImage(item);
    const thumbnails = createThumbnails(image);
    const duration = item.duration || 0;
    
    let publishedDate = item.content?.publishedDate ? new Date(item.content.publishedDate).getTime() / 1000 : 0;
    if (item.event?.startDate) {
        publishedDate = new Date(item.event.startDate).getTime() / 1000;
    } else if (item.startDate) {
        publishedDate = new Date(item.startDate).getTime() / 1000;
    }

    // Check if this is audio content (podcast episode)
    const isAudioContent = contentId.includes('audio-episodes') || item.type === 'audioEpisode';
    
    if (isAudioContent) {
        return getAudioContentDetails(url, contentId, title, description, thumbnails, duration, publishedDate);
    }

    // Video content - fetch player data (and optionally athlete) from APIs
    const playerUrl = `${PLAYER_API_URL}?videoId=${contentId}`;
    const locale = getLocaleFromId(contentId);

    let playerData = null;
    let author = defaultAuthor();

    // Batch player + athlete fetch when athlete hint is provided
    if (athleteUuid) {
        const athleteUrl = `${API_BASE_URL}/feed/${locale}?filter[id]=rrn:content:person-profiles:${athleteUuid}:${locale}&page[limit]=1&rb3Schema=v1:cardList`;

        const [playerResp, athleteResp] = batchGET([
            { url: playerUrl, headers: {} },
            { url: athleteUrl }
        ]);

        if (!playerResp.isOk) throw new ScriptException("Failed to fetch video sources");

        try {
            playerData = JSON.parse(playerResp.body);
        } catch (e) {
            throw new ScriptException("Failed to parse video sources");
        }

        // Parse athlete data
        if (athleteResp.isOk) {
            let athleteData = null;
            try {
                athleteData = JSON.parse(athleteResp.body);
            } catch (e) {
                log("Failed to parse athlete data: " + formatError(e));
            }
            const athleteItem = athleteData?.data?.[0];
            if (athleteItem) {
                author = createAthleteAuthorLink(athleteItem);
            }
        }
    } else {
        const playerResp = httpGET({ url: playerUrl });
        if (!playerResp.isOk) throw new ScriptException("Failed to fetch video sources");
        playerData = playerResp.body;
    }

    // If the player API redirects us to a different video ID (e.g. event -> live video), follow it
    if (!playerData.videoUrl && playerData.videoId && playerData.videoId !== contentId) {
        const retryUrl = `${PLAYER_API_URL}?videoId=${playerData.videoId}`;
        const retryResp = httpGET({ url: retryUrl });
        if (retryResp.isOk) {
            playerData = retryResp.body;
            contentId = playerData.videoId;
        }
    }

    const isLive = contentId.includes("live-videos") || (item.content?.status === 'live' || item.badge?.type === 'live' || item.badge?.type === 'upcoming');

    const hlsUrl = playerData.videoUrl;
    const sources = [];
    if (hlsUrl) {
        sources.push(new HLSSource({
            name: "HLS",
            url: hlsUrl,
            duration: duration
        }));
    }

    const subtitles = extractSubtitles(playerData);

    const result = new PlatformVideoDetails({
        id: new PlatformID(PLATFORM, contentId, config.id || ""),
        name: title,
        thumbnails: thumbnails,
        author: author,
        uploadDate: publishedDate,
        duration: duration,
        viewCount: 0,
        url: url,
        isLive: isLive,
        description: description,
        subtitles: subtitles,
        video: new VideoSourceDescriptor(sources)
    });

    return attachContentRecommendations(result, url);
};


// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

function defaultAuthor(name = "Red Bull TV") {
    return new PlatformAuthorLink(
        new PlatformID(PLATFORM, "RedBull", config.id || ""),
        name,
        BASE_URL,
        DEFAULT_THUMBNAIL_URL
    );
}

/**
 * Extracts subtitle tracks from video player API response.
 * Creates VTT subtitle URLs for all available subtitle languages.
 * 
 * @param {Object} playerData - Response from video player API containing subtitle metadata
 * @param {Object} playerData.availableSubtitleLanguages - Map of locale codes to language codes
 * @param {string} playerData.assetId - Asset ID used to construct VTT URLs
 * @returns {Array<{name: string, url: string, format: string}>} Array of subtitle objects
 * 
 * @example
 * const playerData = {
 *   assetId: "abc123",
 *   availableSubtitleLanguages: { "en": "eng", "es": "spa" }
 * };
 * const subs = extractSubtitles(playerData);
 * // Returns: [{ name: "en", url: "https://...", format: "text/vtt" }, ...]
 */
function extractSubtitles(playerData) {
    const subtitles = [];
    if (playerData.availableSubtitleLanguages && playerData.assetId) {
        for (const [locale, langCode] of Object.entries(playerData.availableSubtitleLanguages)) {
            const vttUrl = `https://play.redbull.com/vtt/v1/rbtv/${playerData.assetId}/vod/fmp4/${locale}/0/1/CC.vtt?category=personal_computer&os_family=http&device_group=group_3`;
            subtitles.push({
                name: locale,
                url: vttUrl,
                format: "text/vtt"
            });
        }
    }
    return subtitles;
}

/**
 * Fetches video playback data from the Red Bull video player API.
 * Handles API errors and validates that a playable video URL exists.
 * 
 * @param {string} videoId - Full content ID in RRN format (e.g., rrn:content:videos:...)
 * @returns {Object} Player API response containing videoUrl, assetId, and subtitle data
 * @throws {ScriptException} If API request fails or no video URL is returned
 * 
 * API Endpoint: https://api-player.redbull.com/rbcom/videoresource?videoId={videoId}
 * Response includes:
 *   - videoUrl: HLS stream URL
 *   - assetId: Asset identifier for subtitle retrieval
 *   - availableSubtitleLanguages: Map of available subtitle locales
 */
function fetchVideoPlayerData(videoId) {
    const playerUrl = `${PLAYER_API_URL}?videoId=${videoId}`;
    const playerResp = httpGET({ url: playerUrl });
    if (!playerResp.isOk) {
        throw new ScriptException("Failed to fetch video sources");
    }
    const playerData = playerResp.body;
    if (!playerData.videoUrl) {
        throw new ScriptException("No video URL found");
    }
    return playerData;
}

/**
 * Extracts the published/start date from a content item.
 * Prioritizes event start dates over general publish dates.
 * 
 * @param {Object} item - Content item from GraphQL API
 * @param {Object} [item.event] - Event data if item is event-related
 * @param {string} [item.event.startDate] - ISO 8601 event start date (highest priority)
 * @param {string} [item.startDate] - ISO 8601 start date (medium priority)
 * @param {Object} [item.content] - Content metadata
 * @param {string} [item.content.publishedDate] - ISO 8601 published date (lowest priority)
 * @returns {number} Unix timestamp in seconds, or 0 if no date available
 */
function extractPublishedDate(item) {
    if (item.event?.startDate) {
        return new Date(item.event.startDate).getTime() / 1000;
    }
    if (item.startDate) {
        return new Date(item.startDate).getTime() / 1000;
    }
    if (item.content?.publishedDate) {
        return new Date(item.content.publishedDate).getTime() / 1000;
    }
    return 0;
}

/**
 * Attaches the getContentRecommendations method to a PlatformVideoDetails result.
 * In testing mode, immediately fetches recommendations. Otherwise, attaches a lazy loader.
 * 
 * @param {PlatformVideoDetails} result - The video details object to enhance
 * @param {string} url - The content URL to fetch recommendations for
 * @returns {PlatformVideoDetails} The enhanced result object (same as input)
 */
function attachContentRecommendations(result, url) {
    if (IS_TESTING) {
        source.getContentRecommendations(url);
    } else {
        result.getContentRecommendations = function() {
            return source.getContentRecommendations(url);
        };
    }
    return result;
}

/**
 * Fetches podcast content directly from URL components, bypassing config/pages.
 * Uses the audio-episodes filter type and returns audio-only content.
 * 
 * @param {string} url - The full URL
 * @param {string} urlLocale - The locale from URL path (e.g., 'gr-el', 'int-en')
 * @param {string} slug - The episode slug from URL
 * @returns {PlatformVideoDetails} Audio content details
 * 
 * URL Pattern: /podcast-episodes/ regex
 * Matches: /int-en/podcast-episodes/save-your-game-s1-trailer
 * Captures: Group 1 = locale (int-en), Group 2 = slug (save-your-game-s1-trailer)
 */
function getPodcastContentDetails(url, urlLocale, slug) {
    // Convert URL locale (e.g., 'gr-el') to API locale format (e.g., 'el-GR')
    const apiLocale = convertUrlLocaleToApiLocale(urlLocale);
    
    // Query the GraphQL API directly for this podcast episode
    const queryUrl = `${API_BASE_URL}/query/${apiLocale}?filter[type]=audio-episodes&filter[uriSlug]=${encodeURIComponent(slug)}&page[limit]=1&rb3Schema=v1:cardList`;
    
    const queryResp = httpGET({ url: queryUrl });
    if (!queryResp.isOk || !queryResp.body.data || queryResp.body.data.length === 0) {
        throw new ScriptException("Podcast episode not found");
    }
    
    const item = queryResp.body.data[0];
    const audioId = item.id;
    const title = item.content?.title || item.title || "Unknown Podcast";
    const description = item.content?.standfirst || "";
    const image = extractImage(item);
    const thumbnails = createThumbnails(image);
    const duration = item.duration || 0;
    const publishedDate = extractPublishedDate(item);
    
    return getAudioContentDetails(url, audioId, title, description, thumbnails, duration, publishedDate);
}

/**
 * Fetches audio content details for podcast episodes.
 * Retrieves audio source URLs from the podcast player API.
 * 
 * @param {string} url - Full content URL
 * @param {string} audioId - Audio content ID in RRN format
 * @param {string} title - Episode title
 * @param {string} description - Episode description
 * @param {Thumbnails} thumbnails - Thumbnail images
 * @param {number} duration - Duration in seconds
 * @param {number} publishedDate - Published timestamp
 * @returns {PlatformVideoDetails} Audio content details (using VideoDetails for compatibility)
 */
function getAudioContentDetails(url, audioId, title, description, thumbnails, duration, publishedDate) {
    const playerUrl = `${PODCAST_PLAYER_API_URL}?audioId=${audioId}`;
    const playerResp = httpGET({ url: playerUrl });
    
    if (!playerResp.isOk) {
        throw new ScriptException("Failed to fetch audio sources");
    }
    
    const playerData = playerResp.body;
    const audioUrl = playerData.audioUrl;
    
    const sources = [];
    if (audioUrl) {
        sources.push(new AudioUrlSource({
            name: "Audio",
            url: audioUrl,
            duration: duration,
            container: "audio/mpeg"
        }));
    }
    
    const result = new PlatformVideoDetails({
        id: new PlatformID(PLATFORM, audioId, config.id || ""),
        name: title,
        thumbnails: thumbnails,
        author: defaultAuthor("Red Bull Podcasts"),
        uploadDate: publishedDate,
        duration: duration,
        viewCount: 0,
        url: url,
        isLive: false,
        description: description,
        video: new VideoSourceDescriptor(sources)
    });
    
    return attachContentRecommendations(result, url);
}

/**
 * Unified function to fetch video content (episodes or standard videos) directly from URL components.
 * Bypasses config/pages API which fails for locale-specific URLs.
 * 
 * @param {string} url - The full URL
 * @param {string} urlLocale - The locale from URL path (e.g., 'fr-ca', 'lat-es', 'int-de')
 * @param {string} slug - The content slug from URL
 * @param {string} contentType - GraphQL content type: 'episode-videos' or 'videos'
 * @returns {PlatformVideoDetails} Video content details with HLS sources and subtitles
 * 
 * URL Patterns:
 *   Episodes: /episodes/ regex
 *     Matches: /fr-ca/episodes/quebec-city-rampage
 *     Captures: Group 1 = locale (fr-ca), Group 2 = slug (quebec-city-rampage)
 *   
 *   Videos: /videos/ regex
 *     Matches: /lat-es/videos/futbol-freestyle-tricks
 *     Captures: Group 1 = locale (lat-es), Group 2 = slug (futbol-freestyle-tricks)
 * 
 * Why this function exists:
 *   The /v3/config/pages API returns "Page Not Found" for locale-specific URLs
 *   (e.g., /gr-el/episodes/..., /lat-es/videos/...) but the GraphQL query API
 *   works correctly when we convert the locale format and query directly.
 */
function getVideoTypeContentDetails(url, urlLocale, slug, contentType, athleteUuid) {
    // Convert URL locale (e.g., 'fr-ca', 'lat-es') to API locale format (e.g., 'fr-CA', 'es-LAT')
    const apiLocale = convertUrlLocaleToApiLocale(urlLocale);

    // Query the GraphQL API directly
    const queryUrl = `${API_BASE_URL}/query/${apiLocale}?filter[type]=${contentType}&filter[uriSlug]=${encodeURIComponent(slug)}&page[limit]=1&rb3Schema=v1:cardList`;

    const queryResp = httpGET({ url: queryUrl });
    if (!queryResp.isOk || !queryResp.body.data || queryResp.body.data.length === 0) {
        throw new ScriptException(`Content not found for type '${contentType}'`);
    }

    const item = queryResp.body.data[0];
    const videoId = item.id;
    const title = item.content?.title || item.title || "Unknown Title";
    const description = item.content?.standfirst || item.content?.subHeading || "";
    const image = extractImage(item);
    const thumbnails = createThumbnails(image);
    const duration = item.duration || 0;
    const publishedDate = extractPublishedDate(item);

    // Batch fetch player data and related athlete in parallel for better performance
    // Pass athleteUuid hint to fetch the correct athlete when video has multiple related athletes
    const { playerData, athlete } = fetchPlayerAndAthlete(videoId, athleteUuid);
    if (!playerData?.videoUrl) {
        throw new ScriptException("Failed to fetch video sources");
    }

    const sources = [
        new HLSSource({
            name: "HLS",
            url: playerData.videoUrl,
            duration: duration
        })
    ];

    const subtitles = extractSubtitles(playerData);

    // Use athlete as author if found, otherwise fall back to Red Bull TV
    const author = athlete || defaultAuthor();

    const result = new PlatformVideoDetails({
        id: new PlatformID(PLATFORM, videoId, config.id || ""),
        name: title,
        thumbnails: thumbnails,
        author: author,
        uploadDate: publishedDate,
        duration: duration,
        viewCount: 0,
        url: url,
        isLive: false,
        description: description,
        subtitles: subtitles,
        video: new VideoSourceDescriptor(sources)
    });

    return attachContentRecommendations(result, url);
}

/**
 * Converts URL locale format to API locale format for GraphQL queries.
 * 
 * Red Bull uses two different locale formats:
 *   - URLs use: {country}-{language} in lowercase (e.g., gr-el, int-en, lat-es)
 *   - API uses: {language}-{COUNTRY} with uppercase country (e.g., el-GR, en-INT, es-LAT)
 * 
 * @param {string} urlLocale - Locale from URL path (e.g., 'gr-el', 'int-en', 'fr-ca')
 * @returns {string} API locale format (e.g., 'el-GR', 'en-INT', 'fr-CA')
 * 
 * Special cases:
 *   - 'int-en' → 'en-INT' (international uses uppercase INT)
 *   - Invalid/missing input → 'en-INT' (default fallback)
 * 
 * @example
 * convertUrlLocaleToApiLocale('gr-el') // Returns: 'el-GR'
 * convertUrlLocaleToApiLocale('int-en') // Returns: 'en-INT'
 * convertUrlLocaleToApiLocale('lat-es') // Returns: 'es-LAT'
 * convertUrlLocaleToApiLocale('fr-ca') // Returns: 'fr-CA'
 */
function convertUrlLocaleToApiLocale(urlLocale) {
    if (!urlLocale) return "en-INT";
    
    const parts = urlLocale.toLowerCase().split('-');
    if (parts.length !== 2) return "en-INT";
    
    const [country, language] = parts;
    
    // Special case for 'int' (international)
    if (country === 'int') {
        return `${language.toLowerCase()}-INT`;
    }
    
    // Standard format: swap and uppercase country
    return `${language.toLowerCase()}-${country.toUpperCase()}`;
}

source.getContentRecommendations = function(url, initialData) {
    let videoId;
    if (initialData && initialData.id) {
        videoId = initialData.id.value;
    } else {
        try {
            const details = source.getContentDetails(url);
            videoId = details.id.value;
        } catch (e) {
            log("getContentDetails failed for recommendations: " + formatError(e));
            return new RedBullPager([], false, "related_videos", 0);
        }
    }
    
    if (!videoId) return new RedBullPager([], false, "related_videos", 0);
    
    return fetchRelatedVideos(videoId, 0);
};

// --- Pagers ---

class RedBullPager extends VideoPager {
    constructor(results, hasMore, type, page, context = null, videosOnlyOrAuthor = false, author = null) {
        super(results, hasMore);
        this.type = type;
        this.page = page;
        this.context = context;
        // Support both old signature (videosOnly as boolean) and new (author as 6th param)
        if (typeof videosOnlyOrAuthor === 'boolean') {
            this.videosOnly = videosOnlyOrAuthor;
            this.author = author;
        } else {
            this.videosOnly = false;
            this.author = videosOnlyOrAuthor;
        }
    }

    nextPage() {
        if (this.type === "related_videos") {
            return fetchRelatedVideos(this.context, this.page);
        } else if (this.type === "playlists") {
            return fetchPlaylists(this.page);
        } else if (this.type === "search") {
            return fetchSearch(this.context.query, this.page);
        } else if (this.type === "athlete_videos") {
            return fetchAthleteVideos(this.context, this.page, this.author);
        }
        return fetchFeed(this.type, this.page, this.context, this.videosOnly);
    }
}

class RedBullPlaylistPager extends PlaylistPager {
    constructor(results, hasMore, page, context = null) {
        super(results, hasMore);
        this.page = page;
        this.context = context;
    }
    
    nextPage() {
        if (this.context) {
            return fetchRelatedPlaylists(this.context, this.page);
        }
        return fetchPlaylists(this.page);
    }
}


function fetchLiveEvents() {
    const configUrl = `${BASE_URL}/v3/config/pages?url=/int-en/live-events`;
    const configResp = httpGET({ url: configUrl });
    
    if (!configResp.isOk) {
        return [];
    }

    const panels = configResp.body.data?.panels || [];
    const collectionIds = extractLiveEventCollectionIds(panels);

    if (collectionIds.size === 0) {
        return [];
    }

    // Batch all collection requests together for better performance
    const allEvents = batchFetchCollections(collectionIds);
    return allEvents
        .map(item => createPlatformVideo(item))
        .filter(v => v !== null);
}

/**
 * Helper function to extract collection IDs from live event panels.
 * @param {Array} panels - Config panels from API
 * @returns {Set<string>} Set of collection IDs
 */
function extractLiveEventCollectionIds(panels) {
    const collectionIds = new Set();
    const liveEventCaptions = ["Upcoming live events", "Live now"];
    
    for (const panel of panels) {
        const caption = panel.config?.caption;
        const descriptiveName = panel.descriptiveName || '';
        
        const isLiveEventPanel = liveEventCaptions.includes(caption) || 
                                 descriptiveName.includes("Hero - next upcoming live event");
        
        if (!isLiveEventPanel) {
            continue;
        }
        
        const endpoint = panel.config?.endpoint || panel.config?.contentEndpoint;
        if (!endpoint) {
            continue;
        }
        
        const match = /rrn:content:collections:[a-f0-9-]+/.exec(endpoint);
        if (match) {
            collectionIds.add(match[0]);
        }
    }
    
    return collectionIds;
}

/**
 * Helper function to batch fetch multiple collections.
 * @param {Set<string>} collectionIds - Set of collection IDs
 * @returns {Array} Array of all event items
 */
function batchFetchCollections(collectionIds) {
    const collectionIdsArray = Array.from(collectionIds);
    const requests = collectionIdsArray.map(collectionId => {
        const locale = getLocaleFromId(collectionId);
        return { url: `${API_BASE_URL}/feed/${locale}/related-to/${collectionId}?scoring=featured&page[limit]=10&rb3Schema=v1:cardList` };
    });

    const responses = batchGET(requests);
    const allEvents = [];
    
    for (const resp of responses) {
        if (!resp.isOk) {
            continue;
        }
        
        try {
            const data = JSON.parse(resp.body);
            if (data?.data) {
                allEvents.push(...data.data);
            }
        } catch (e) {
            log("Failed to parse live event collection: " + formatError(e));
        }
    }
    
    return allEvents;
}

function fetchFeed(type, page, context = null, videosOnly = false) {
    let url;
    if (type === "discover") {
        const offset = page * ITEMS_PER_PAGE;
        url = `${API_BASE_URL}/feed/en-INT?filter[type]=videos,shows,films,episode-videos,live-videos&page[limit]=${ITEMS_PER_PAGE}&page[offset]=${offset}&scoring=freshness&spaces=rbtv&rb3Schema=v1:cardList&rb3UseEditorialTitle=true`;
    } else if (type === "search") {
        // Placeholder for search
        return new RedBullPager([], false, type, page, context, videosOnly);
    } else {
        return new RedBullPager([], false, type, page, context, videosOnly);
    }
    
    const response = httpGET({ url: url });
    if (!response.isOk) {
        throw new ScriptException(`Failed to fetch feed: ${response.code}`);
    }
    
    const items = response.body?.data || [];
    
    // Convert items to video/playlist objects
    const content = items
        .map(item => convertItemToContent(item, videosOnly))
        .filter(v => v !== null);
    
    // Deduplicate and sort
    const deduplicatedContent = deduplicateById(content);
    const sortedContent = sortLiveVideosFirst(deduplicatedContent);
    
    return new RedBullPager(sortedContent, items.length >= ITEMS_PER_PAGE, type, page + 1, context, videosOnly);
}

/**
 * Converts an API item to a PlatformVideo or PlatformPlaylist.
 * @param {Object} item - API content item
 * @param {boolean} videosOnly - If true, skip playlist items
 * @returns {PlatformVideo|PlatformPlaylist|null}
 */
function convertItemToContent(item, videosOnly) {
    const id = item.id;
    if (!id) {
        return null;
    }

    const isShow = item.type === 'shows' || item.type === 'show' || id.includes('shows');
    if (isShow) {
        return videosOnly ? null : createPlatformPlaylist(item);
    }

    return createPlatformVideo(item);
}

/**
 * Deduplicates content array by ID value.
 * @param {Array} content - Array of content items
 * @returns {Array} Deduplicated array
 */
function deduplicateById(content) {
    const seenIds = new Set();
    return content.filter(item => {
        if (seenIds.has(item.id.value)) {
            return false;
        }
        seenIds.add(item.id.value);
        return true;
    });
}

/**
 * Sorts content array to place live videos first.
 * @param {Array} content - Array of content items
 * @returns {Array} Sorted array
 */
function sortLiveVideosFirst(content) {
    return content.sort((a, b) => {
        const aLive = a instanceof PlatformVideo && a.isLive;
        const bLive = b instanceof PlatformVideo && b.isLive;
        
        if (aLive && !bLive) return -1;
        if (!aLive && bLive) return 1;
        return 0;
    });
}

function fetchPlaylists(page) {
    const offset = page * ITEMS_PER_PAGE;
    const url = `${API_BASE_URL}/feed/en-INT?filter[type]=shows&page[limit]=${ITEMS_PER_PAGE}&page[offset]=${offset}&scoring=freshness&spaces=rbtv&rb3Schema=v1:cardList&rb3UseEditorialTitle=true`;
    
    const response = httpGET({ url: url });
    if (!response.isOk) {
        throw new ScriptException(`Failed to fetch playlists: ${response.code}`);
    }
    
    const items = response.body?.data || [];
    const playlists = items
        .map(item => createPlatformPlaylist(item))
        .filter(p => p !== null);
    
    return new RedBullPlaylistPager(playlists, items.length >= ITEMS_PER_PAGE, page + 1);
}

function getChannelVideos(url, pager, author) {

    if (url === "https://www.redbull.com") {
        if (pager && pager instanceof RedBullPager) {
            return pager.nextPage();
        }
        return fetchFeed("discover", 0, null, true);
    }

    const { type, slug, locale } = parseChannelUrl(url);

    if (pager && pager instanceof RedBullPager) {
        return pager.nextPage();
    }

    if (type === "shows") {
        return fetchShowVideos(slug);
    } else if (type === "person-profiles") {
        // For athletes, use slug-based filtering to get videos specifically tagged with the athlete
        return fetchAthleteVideos(slug, 0, author);
    } else if (type === "event-profiles") {
        // For events, use the ID-based related-to API
        const metadata = fetchChannelMetadata(type, slug, locale);
        if (!metadata) throw new ScriptException("Channel not found");
        return fetchRelatedVideos(metadata.id, 0);
    } else {
        return new RedBullPager([], false, "channel", 0);
    }
};


function parseChannelUrl(url) {
    const match = url.match(CHANNEL_PARSE_REGEX);
    
    if (!match) {
        return {};
    }
    
    const localePrefix = match[1];
    const channelType = match[2];
    const slug = match[3];
    
    const locale = convertUrlLocaleToApiLocale(localePrefix);
    
    // Map URL type to API filter type
    const typeMapping = {
        'shows': 'shows',
        'events': 'event-profiles',
        'athlete': 'person-profiles'
    };
    
    const apiType = typeMapping[channelType] || channelType;
    
    return { type: apiType, slug, locale };
}

function fetchChannelMetadata(type, slug, locale = "en-INT") {
    // Type is already in API format from parseChannelUrl
    const url = `${API_BASE_URL}/feed/${locale}?filter[type]=${type}&filter[uriSlug]=${slug}&page[limit]=1&rb3Schema=v1:cardList`;
    const response = httpGET({ url: url });
    
    if (!response.isOk) {
        return null;
    }
    
    const data = response.body?.data?.[0];
    if (!data) return null;
    
    const title = data.content?.title || data.title;
    const description = data.content?.standfirst || data.content?.description || "";
    const imageBase = extractImage(data);
    const image = processImageUrl(imageBase, 640, 360);
    
    return {
        id: data.id,
        title: title,
        description: description,
        image: image,
        videoCount: data.nrOfEpisodes || -1
    };
}

/**
 * Fetches both metadata and social links for an athlete in a single batched request.
 * This is more efficient than making two separate requests sequentially.
 * 
 * @param {string} slug - The athlete's URL slug
 * @param {string} locale - API locale format (e.g., "en-INT")
 * @returns {Object} Object containing { metadata, socialLinks }
 */
function fetchAthleteChannelData(slug, locale = "en-INT") {
    const metadataUrl = `${API_BASE_URL}/feed/${locale}?filter[type]=person-profiles&filter[uriSlug]=${slug}&page[limit]=1&rb3Schema=v1:cardList`;
    const socialLinksUrl = `${API_BASE_URL}/feed/${locale}?filter[type]=person-profiles&filter[uriSlug]=${slug}&page[limit]=1&disableUsageRestrictions=true&rb3Schema=v2:personHero`;
    
    const [metadataResp, socialLinksResp] = batchGET([
        { url: metadataUrl },
        { url: socialLinksUrl }
    ]);

    // Parse metadata
    let metadata = null;
    if (metadataResp.isOk) {
        try {
            const metadataData = JSON.parse(metadataResp.body);
            const data = metadataData?.data?.[0];
            if (data) {
                const title = data.content?.title || data.title;
                const description = data.content?.standfirst || data.content?.description || "";
                const imageBase = extractImage(data);
                const image = processImageUrl(imageBase, 640, 360);
                
                metadata = {
                    id: data.id,
                    title: title,
                    description: description,
                    image: image,
                    videoCount: data.nrOfEpisodes || -1
                };
            }
        } catch (e) {
            log("Failed to parse athlete metadata: " + formatError(e));
        }
    }
    
    // Parse social links
    const socialLinks = {};
    if (socialLinksResp.isOk) {
        try {
            const socialData = JSON.parse(socialLinksResp.body);
            const data = socialData?.data;
            if (data && data.socialLinks) {
                const platformNames = {
                    'x': 'X (Twitter)',
                    'twitter': 'X (Twitter)',
                    'facebook': 'Facebook',
                    'instagram': 'Instagram',
                    'youtube': 'YouTube',
                    'tiktok': 'TikTok',
                    'twitch': 'Twitch',
                    'linkedin': 'LinkedIn',
                    'snapchat': 'Snapchat',
                    'threads': 'Threads',
                    'spotify': 'Spotify',
                    'soundcloud': 'SoundCloud',
                    'web': 'Website',
                    'website': 'Website',
                    'discord': 'Discord',
                    'reddit': 'Reddit',
                    'strava': 'Strava'
                };
                
                for (const link of data.socialLinks) {
                    if (link.platform && link.href) {
                        const displayName = platformNames[link.platform.toLowerCase()] || capitalizeFirst(link.platform);
                        socialLinks[displayName] = link.href;
                    }
                }
            }
        } catch (e) {
            log("Failed to parse athlete social links: " + formatError(e));
        }
    }
    
    return { metadata, socialLinks };
}

/**
 * Capitalizes the first letter of a string.
 * @param {string} str - The string to capitalize
 * @returns {string} The string with first letter capitalized
 */
function capitalizeFirst(str) {
    if (!str) return '';
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}

function fetchShowVideos(slug) {
    const url = `${API_BASE_URL}/feed/en-INT?filter[type]=shows&filter[uriSlug]=${slug}&page[limit]=1&disableUsageRestrictions=true&rb3Schema=v1:tvEpisodesTabs`;
    const response = httpGET({ url: url });
    
    if (!response.isOk) {
        return new RedBullPager([], false, "show_videos", 0);
    }
    
    const seasons = response.body?.data || [];
    const videos = [];
    
    for (const season of seasons) {
        if (!season.content) {
            continue;
        }
        
        for (const episode of season.content) {
            const video = createPlatformVideo(episode);
            if (video) {
                videos.push(video);
            }
        }
    }
    
    return new RedBullPager(videos, false, "show_videos", 0);
}

function fetchRelatedVideos(id, page) {
    const offset = page * ITEMS_PER_PAGE;
    const locale = getLocaleFromId(id);
    const url = `${API_BASE_URL}/feed/${locale}/related-to/${id}?filter[type]=episode-videos,live-videos,recap-videos,videos&scoring=freshness&page[limit]=${ITEMS_PER_PAGE}&page[offset]=${offset}&rb3Schema=v1:cardList`;
    
    const response = httpGET({ url: url });
    if (!response.isOk) {
        return new RedBullPager([], false, "related_videos", page);
    }
    
    const items = response.body?.data || [];
    const videos = items
        .map(item => createPlatformVideo(item))
        .filter(v => v !== null);
    
    const hasMore = items.length >= ITEMS_PER_PAGE;
    return new RedBullPager(videos, hasMore, "related_videos", page + 1, id);
}

/**
 * Creates a PlatformAuthorLink from an athlete API response item.
 * Extracts athlete metadata and constructs the author link with proper thumbnail.
 *
 * @param {Object} athleteItem - Athlete data from API response
 * @param {string} athleteItem.id - Athlete ID in RRN format
 * @param {Object} athleteItem.content - Content metadata
 * @param {string} athleteItem.content.title - Athlete name
 * @param {Object} athleteItem.reference - Reference data
 * @param {string} athleteItem.reference.uriSlug - URL slug for athlete page
 * @returns {PlatformAuthorLink} Author link for the athlete
 */
function createAthleteAuthorLink(athleteItem) {
    const athleteId = athleteItem.id;
    const athleteName = athleteItem.content?.title || "Unknown";
    const athleteSlug = athleteItem.reference?.uriSlug;
    const athleteImageTemplate = extractImage(athleteItem);
    const athleteUrl = athleteSlug ? `${BASE_URL}/int-en/athlete/${athleteSlug}` : BASE_URL;
    const athleteThumbnail = athleteImageTemplate
        ? processImageUrl(athleteImageTemplate, 400, 400)
        : DEFAULT_THUMBNAIL_URL;

    return new PlatformAuthorLink(
        new PlatformID(PLATFORM, athleteId, config.id || ""),
        athleteName,
        athleteUrl,
        athleteThumbnail
    );
}

/**
 * Fetches video player data and optionally an athlete in parallel using batched HTTP requests.
 *
 * @param {string} contentId - The video content ID in RRN format
 * @param {string} [athleteUuid] - Optional athlete UUID hint from URL query param.
 *   Only when provided will the athlete be fetched. Without a hint, athlete is null
 *   and the caller should use the default "Red Bull TV" author.
 * @returns {{ playerData: Object|null, athlete: PlatformAuthorLink|null }}
 *   - playerData: Video player API response with videoUrl, subtitles, etc.
 *   - athlete: PlatformAuthorLink for the specified athlete, or null if no hint provided
 *
 * @example
 * const { playerData, athlete } = fetchPlayerAndAthlete("rrn:content:episode-videos:...", "772693fd-...");
 * // athlete is PlatformAuthorLink for the specified athlete UUID
 * const { playerData, athlete } = fetchPlayerAndAthlete("rrn:content:episode-videos:...");
 * // athlete is null, use default Red Bull TV author
 */
function fetchPlayerAndAthlete(contentId, athleteUuid) {
    const locale = getLocaleFromId(contentId);
    const playerUrl = `${PLAYER_API_URL}?videoId=${contentId}`;

    // Only fetch athlete if UUID hint is provided
    // Without a hint, we don't know which athlete to show, so default to Red Bull TV
    if (!athleteUuid) {
        const playerResp = httpGET({ url: playerUrl, headers: {} });
        let playerData = null;
        if (playerResp.isOk) {
            playerData = playerResp.body;
        }
        return { playerData, athlete: null };
    }

    // Fetch player and specific athlete in parallel
    const athleteUrl = `${API_BASE_URL}/feed/${locale}?filter[id]=rrn:content:person-profiles:${athleteUuid}:${locale}&page[limit]=1&rb3Schema=v1:cardList`;

    const [playerResp, athleteResp] = batchGET([
        { url: playerUrl, headers: {} },
        { url: athleteUrl }
    ]);

    // Parse player data
    let playerData = null;
    if (playerResp.isOk) {
        try {
            playerData = JSON.parse(playerResp.body);
        } catch (e) {
            log("Failed to parse player data: " + formatError(e));
        }
    }

    // Parse athlete data
    let athlete = null;
    if (athleteResp.isOk) {
        let data = null;
        try {
            data = JSON.parse(athleteResp.body);
        } catch (e) {
            log("Failed to parse athlete data: " + formatError(e));
        }
        const athleteItem = data?.data?.[0];
        if (athleteItem) {
            athlete = createAthleteAuthorLink(athleteItem);
        }
    }

    return { playerData, athlete };
}

/**
 * Fetches a specific athlete by UUID.
 * Used when athlete UUID hint is provided in the URL query param.
 *
 * @param {string} athleteUuid - The athlete's UUID (without locale suffix)
 * @param {string} locale - API locale format (e.g., "en-INT")
 * @returns {PlatformAuthorLink|null} PlatformAuthorLink for the athlete, or null if not found
 */
function fetchAthleteByUuid(athleteUuid, locale) {
    const url = `${API_BASE_URL}/feed/${locale}?filter[id]=rrn:content:person-profiles:${athleteUuid}:${locale}&page[limit]=1&rb3Schema=v1:cardList`;
    const response = httpGET({ url: url });

    if (!response.isOk) return null;

    const athletes = response.body?.data || [];
    if (athletes.length === 0) return null;

    return createAthleteAuthorLink(athletes[0]);
}

/**
 * Fetches videos specifically tagged with an athlete using tag-based filtering.
 * This uses two approaches:
 * 1. First tries relationships.tags filter to get videos tagged with the athlete
 * 2. If that returns empty, falls back to the editorsPick tile endpoint
 *
 * On page 0, both requests are batched for better performance.
 *
 * @param {string} slug - The athlete's URL slug
 * @param {number} page - Page number for pagination
 * @param {PlatformAuthorLink|null} [author=null] - Optional author to use for videos.
 *   When provided, videos will show this author instead of the default "Red Bull TV".
 *   This is passed from getChannelContents() to display the athlete as the video author.
 * @returns {RedBullPager} Pager containing athlete's videos
 */
function fetchAthleteVideos(slug, page, author) {
    const offset = page * ITEMS_PER_PAGE;

    const tagUrl = `${API_BASE_URL}/feed/en-INT?filter[relationships.tags]=rrn:slug:person-profiles:${slug}&filter[type]=rrn:content:shows,rrn:content:films,rrn:content:episode-videos,rrn:content:videos&scoring=featuredFresh&score.featuredFresh.subType=rail2&page[limit]=${ITEMS_PER_PAGE}&page[offset]=${offset}&rb3Schema=v1:cardList`;

    // On page 0, batch both tag-based and editorsPick requests for better performance
    if (page === 0) {
        const tileUrl = `${API_BASE_URL}/feed/en-INT/related-to/rrn:slug:person-profiles:${slug}?scoring=featured&score.featured.subType=editorsPick&score.featured.localeMixing=en-INT&disableUsageRestrictions=true&filter[type]=episode-videos,films,videos,live-videos,recap-videos&page[limit]=${ITEMS_PER_PAGE}&rb3Schema=v1:cardList`;

        const [tagResp, tileResp] = batchGET([
            { url: tagUrl },
            { url: tileUrl }
        ]);

        // Safe JSON parse helper so a bad response does not crash the channel load
        const safeParse = (resp) => {
            try {
                return JSON.parse(resp.body);
            } catch (e) {
                log("Failed to parse athlete feed response: " + formatError(e));
                return null;
            }
        };

        // Prefer tag-based results if available
        if (tagResp.isOk) {
            const items = safeParse(tagResp)?.data || [];
            if (items.length > 0) {
                const videos = items
                    .map(item => createPlatformVideo(item, author))
                    .filter(v => v !== null);
                const hasMore = items.length >= ITEMS_PER_PAGE;
                return new RedBullPager(videos, hasMore, "athlete_videos", page + 1, slug, author);
            }
        }

        // Fall back to editorsPick results
        if (tileResp.isOk) {
            const items = safeParse(tileResp)?.data || [];
            const videos = items
                .map(item => createPlatformVideo(item, author))
                .filter(v => v !== null);
            // editorsPick typically returns just a few items, so no pagination
            return new RedBullPager(videos, false, "athlete_videos", page + 1, slug, author);
        }
        return new RedBullPager([], false, "athlete_videos", page, slug, author);
    }

    // For subsequent pages, only tag-based filtering supports pagination
    const tagResponse = httpGET({ url: tagUrl });
    if (tagResponse.isOk) {
        const items = tagResponse.body?.data || [];
        if (items.length > 0) {
            const videos = items
                .map(item => createPlatformVideo(item, author))
                .filter(v => v !== null);
            const hasMore = items.length >= ITEMS_PER_PAGE;
            return new RedBullPager(videos, hasMore, "athlete_videos", page + 1, slug, author);
        }
    }

    return new RedBullPager([], false, "athlete_videos", page, slug, author);
}

function fetchRelatedPlaylists(id, page) {
    const offset = page * ITEMS_PER_PAGE;
    const locale = getLocaleFromId(id);
    const url = `${API_BASE_URL}/feed/${locale}/related-to/${id}?filter[type]=shows&scoring=freshness&page[limit]=${ITEMS_PER_PAGE}&page[offset]=${offset}&rb3Schema=v1:cardList`;
    
    const response = httpGET({ url: url });
    if (!response.isOk) {
        return new RedBullPlaylistPager([], false, page, id);
    }
    
    const items = response.body?.data || [];
    const playlists = items
        .map(item => createPlatformPlaylist(item))
        .filter(p => p !== null);
    
    const hasMore = items.length >= ITEMS_PER_PAGE;
    return new RedBullPlaylistPager(playlists, hasMore, page + 1, id);
}

function getShowPlaylistDetails(slug) {
    // Batch both requests together for better performance
    const metadataUrl = `${API_BASE_URL}/feed/en-INT?filter[type]=shows&filter[uriSlug]=${slug}&page[limit]=1&rb3Schema=v1:cardList`;
    const videosUrl = `${API_BASE_URL}/feed/en-INT?filter[type]=shows&filter[uriSlug]=${slug}&page[limit]=1&disableUsageRestrictions=true&rb3Schema=v1:tvEpisodesTabs`;
    
    const [metadataResp, videosResp] = batchGET([
        { url: metadataUrl },
        { url: videosUrl }
    ]);

    // Parse metadata
    if (!metadataResp.isOk) {
        throw new ScriptException("Show not found");
    }

    let metadataData;
    try {
        metadataData = JSON.parse(metadataResp.body);
    } catch (e) {
        log("Failed to parse show metadata: " + formatError(e));
        throw new ScriptException("Failed to parse show metadata");
    }
    
    const data = metadataData?.data?.[0];
    if (!data) {
        throw new ScriptException("Show not found");
    }
    
    const title = data.content?.title || data.title;
    const imageBase = extractImage(data);
    const image = processImageUrl(imageBase, 640, 360);
    const videoCount = data.nrOfEpisodes || -1;
    
    // Parse videos
    const videos = extractShowVideos(videosResp);
    
    const pager = new RedBullPager(videos, false, "show_videos", 0);
    
    return new PlatformPlaylistDetails({
        id: new PlatformID(PLATFORM, data.id, config.id || ""),
        name: title,
        author: defaultAuthor(),
        url: `${BASE_URL}/int-en/shows/${slug}`,
        thumbnail: image,
        videoCount: videoCount,
        contents: pager
    });
}

/**
 * Helper function to extract videos from a show episodes response.
 * @param {Object} videosResp - HTTP response containing show episodes
 * @returns {Array<PlatformVideo>} Array of video objects
 */
function extractShowVideos(videosResp) {
    const videos = [];

    if (!videosResp.isOk) {
        return videos;
    }

    try {
        const videosData = JSON.parse(videosResp.body);
        const seasons = videosData?.data || [];

        for (const season of seasons) {
            if (!season.content) {
                continue;
            }

            for (const episode of season.content) {
                // The tvEpisodesTabs schema stores id/uriSlug/type under a
                // "reference" object instead of at the top level.  Normalize
                // these fields so createPlatformVideo can handle the item.
                const normalized = Object.assign({}, episode);
                if (episode.reference) {
                    if (!normalized.id && episode.reference.id) {
                        normalized.id = episode.reference.id;
                    }
                    if (!normalized.type && episode.reference.type) {
                        normalized.type = episode.reference.type;
                    }
                }
                // Some episodes (especially newly published ones) have
                // duration 0 across all APIs.  Use -1 (unknown) so
                // createPlatformVideo doesn't discard them as "coming soon"
                // content.
                if (!normalized.duration) {
                    normalized.duration = -1;
                }
                const video = createPlatformVideo(normalized);
                if (video) {
                    videos.push(video);
                }
            }
        }
    } catch (e) {
        log("Failed to parse show videos: " + formatError(e));
        // Ignore parse errors, return empty array
    }

    return videos;
}

function getCollectionPlaylistDetails(slug) {
    // Fetch collection metadata by slug - collections can be of various types (discipline, etc.)
    const metadataUrl = `${API_BASE_URL}/feed/en-INT?filter[uriSlug]=${slug}&page[limit]=1&rb3Schema=v1:cardList`;
    const metadataResp = httpGET({ url: metadataUrl });
    
    if (!metadataResp.isOk) throw new ScriptException("Collection not found");
    
    const data = metadataResp.body?.data?.[0];
    if (!data) throw new ScriptException("Collection not found");
    
    const collectionId = data.id;
    const title = data.content?.title || data.title || "Collection";
    const imageBase = extractImage(data);
    const image = processImageUrl(imageBase, 640, 360);
    
    // Fetch videos related to this collection
    const locale = getLocaleFromId(collectionId);
    const videosUrl = `${API_BASE_URL}/feed/${locale}/related-to/${collectionId}?filter[type]=episode-videos,videos,films,live-videos&scoring=freshness&page[limit]=50&rb3Schema=v1:cardList`;
    const videosResp = httpGET({ url: videosUrl });
    
    let videos = [];
    if (videosResp.isOk) {
        const items = videosResp.body?.data || [];
        videos = items.map(item => createPlatformVideo(item)).filter(v => v !== null);
    }
    
    const pager = new RedBullPager(videos, false, "collection_videos", 0);
    
    return new PlatformPlaylistDetails({
        id: new PlatformID(PLATFORM, collectionId, config.id || ""),
        name: title,
        author: defaultAuthor(),
        url: `${BASE_URL}/int-en/discover/${slug}`,
        thumbnail: image,
        videoCount: videos.length,
        contents: pager
    });
}

// --- Helpers ---

/**
 * Performs HTTP GET request with automatic JSON parsing.
 * Wrapper around http.GET that handles JSON parsing and error handling.
 * 
 * @param {Object} options - Request configuration
 * @param {string} options.url - URL to fetch
 * @param {boolean} [options.useAuthenticated=false] - Whether to use authenticated request (reserved for future use)
 * @param {boolean} [options.parseResponse=true] - Whether to parse JSON response
 * @param {Object} [options.headers=null] - Custom headers (defaults to HEADERS constant)
 * @returns {{code: number, isOk: boolean, body: Object|string, headers: Object}}
 *   Response with parsed JSON body when parseResponse=true, otherwise raw response
 */
function httpGET(options) {
    const {
        url,
        useAuthenticated = false,
        parseResponse = true,
        headers = null
    } = options;

    const localHeaders = headers ?? HEADERS;

    function parseResp(response) {
        if (response.isOk && parseResponse) {
            try {
                const parsed = JSON.parse(response.body);
                return {
                    code: response.code,
                    isOk: response.isOk,
                    body: parsed,
                    headers: response.headers
                };
            } catch (e) {
                log("httpGET parse failed: " + formatError(e));
            }
        }
        return response;
    }

    // Try standard HTTP client first
    const response = http.GET(url, localHeaders, useAuthenticated);
    if (response.isOk) {
        return parseResp(response);
    }

    // Fall back to httpimp (browser TLS impersonation) on any failure
    if (IS_IMPERSONATION_AVAILABLE) {
        log("httpGET falling back to httpimp for: " + url);
        const impResponse = httpimp.GET(url, localHeaders, useAuthenticated);
        return parseResp(impResponse);
    }

    return parseResp(response);
}

/**
 * Performs multiple GET requests in parallel using http.batch().
 * Falls back to sequential httpimp requests if any response fails
 * (Akamai bot detection can return 403, 429, 503, etc.).
 *
 * @param {Array<{url: string, headers?: Object}>} requests - Array of request descriptors
 * @returns {Array<BridgeHttpResponse>} Array of responses (raw, not JSON-parsed)
 */
function batchGET(requests) {
    // Try http.batch() first
    let batch = http.batch();
    for (const req of requests) {
        batch = batch.GET(req.url, req.headers ?? HEADERS);
    }
    const responses = batch.execute();

    // Check if any response failed (bot detection can return 403, 429, 503, etc.)
    const hasBlock = responses.some(r => !r.isOk);

    if (!hasBlock || !IS_IMPERSONATION_AVAILABLE) {
        return responses;
    }

    // Fall back to sequential httpimp requests
    log("batchGET falling back to httpimp for " + requests.length + " requests");
    return requests.map(req => {
        try {
            return httpimp.GET(req.url, req.headers ?? HEADERS, false);
        } catch (e) {
            log("httpimp GET failed: " + formatError(e));
            return { code: 0, isOk: false, body: "", headers: {} };
        }
    });
}

/**
 * Extracts image URL from a content item with multiple fallback paths.
 * Red Bull API returns images in various nested structures depending on content type.
 * This function tries all known paths in priority order.
 * 
 * @param {Object} item - Content item from API response
 * @param {Object} [item.media] - Media container
 * @param {Object} [item.media.mainImage] - Primary image object
 * @param {string} [item.media.mainImage.imageURL] - Direct image URL (highest priority)
 * @param {Object} [item.media.mainImage.imageEssence] - Image essence container
 * @param {Object} [item.image] - Alternative image location
 * @param {Object} [item.featuredImage] - Featured image for some content types
 * @param {Object} [item.mainImage] - Another alternative location
 * @returns {string|null} Image URL template with {op} placeholder, or null if no image found
 * 
 * The returned URL contains a {op} placeholder that should be replaced with
 * Cloudinary transformation parameters using processImageUrl().
 * 
 * @example
 * const imageUrl = extractImage(item);
 * // Returns: 'https://img.redbullcontentpool.com/.../{op}/...' or null
 */
function extractImage(item) {
    return item.media?.mainImage?.imageURL || 
           item.image?.imageEssence?.imageURL || 
           item.media?.mainImage?.imageEssence?.imageURL ||
           item.featuredImage?.imageEssence?.imageURL || 
           item.image?.imageURL ||
           item.mainImage?.imageEssence?.imageURL;
}

/**
 * Processes Red Bull image URL with Cloudinary transformations.
 * Replaces the {op} placeholder with specific width, height, and quality parameters.
 * 
 * @param {string} url - Image URL template containing {op} placeholder
 * @param {number} width - Desired image width in pixels
 * @param {number} height - Desired image height in pixels
 * @returns {string} Processed URL with Cloudinary parameters
 * 
 * Cloudinary transformation parameters used:
 *   - c_fill: Crop/resize mode (fill to exact dimensions)
 *   - w_{width}: Width in pixels
 *   - h_{height}: Height in pixels
 *   - g_auto: Automatic gravity/focus detection
 *   - q_auto: Automatic quality optimization
 *   - f_auto: Automatic format selection (WebP, etc.)
 * 
 * @example
 * processImageUrl('https://img.../path/{op}/file.jpg', 1920, 1080)
 * // Returns: 'https://img.../path/c_fill,w_1920,h_1080,g_auto,q_auto,f_auto/file.jpg'
 * 
 * If url is null/empty, returns Red Bull favicon as fallback.
 */
function processImageUrl(url, width, height) {
    if (!url) return DEFAULT_THUMBNAIL_URL;
    return url.replace("{op}", `c_fill,w_${width},h_${height},g_auto,q_auto,f_auto`);
}

/**
 * Creates Thumbnails object with multiple resolutions from an image URL.
 * Generates both standard and high-resolution thumbnails for Grayjay.
 * 
 * @param {string} imageUrl - Image URL template (with {op} placeholder) or null
 * @returns {Thumbnails} Thumbnails object containing multiple resolution options
 * 
 * Generated resolutions:
 *   - 1920x1080 (1080p) - High resolution for detail view
 *   - 640x360 (360p) - Standard resolution for lists/grids
 * 
 * @example
 * const imageUrl = 'https://img.../path/{op}/file.jpg';
 * const thumbnails = createThumbnails(imageUrl);
 * // Returns: Thumbnails([Thumbnail(1920x1080), Thumbnail(640x360)])
 */
function createThumbnails(imageUrl) {
    const thumbUrl = processImageUrl(imageUrl, 640, 360);
    const highResThumbUrl = processImageUrl(imageUrl, 1920, 1080);
    return new Thumbnails([
        new Thumbnail(highResThumbUrl, 1080),
        new Thumbnail(thumbUrl, 360)
    ]);
}


/**
 * Builds a video URL from item data.
 *
 * When explicit params (uriSlug, type, id) are provided, uses the item's
 * actual locale. When only item is provided, extracts slug/type from
 * item.reference and defaults to 'int-en' locale.
 *
 * @param {Object} item - Content item from API response
 * @param {string} [uriSlug] - URL slug (if omitted, uses item.reference.uriSlug)
 * @param {string} [type] - Content type (if omitted, uses item.type or item.reference.type)
 * @param {string} [id] - Content ID (RRN string)
 * @returns {string} Full video URL
 *
 * URL patterns by type:
 *   - episode / episode-videos → /{locale}/episodes/{slug}
 *   - film → /{locale}/films/{slug}
 *   - show → /{locale}/shows/{slug}
 *   - event → /{locale}/events/{slug}
 *   - live-videos, videoLive → /{locale}/live/{slug}
 *   - default (videos) → /{locale}/videos/{slug}
 *
 * @example
 * buildVideoUrl(item, 'rampage', 'film', 'rrn:content:films:abc:en-INT')
 * // Returns: 'https://www.redbull.com/int-en/films/rampage'
 *
 * buildVideoUrl({ reference: { uriSlug: 'rampage', type: 'film' } })
 * // Returns: 'https://www.redbull.com/int-en/films/rampage'
 */
function buildVideoUrl(item, uriSlug, type, id) {
    let useDefaultLocale = false;

    if (!uriSlug) {
        uriSlug = item.reference?.uriSlug;
        if (!uriSlug) return BASE_URL;
        type = item.type || item.reference?.type;
        useDefaultLocale = true;
    }

    const urlPrefix = useDefaultLocale
        ? 'int-en'
        : convertApiLocaleToUrlLocale(getLocaleFromItem(item));

    // Determine URL path based on content type
    let pathSegment = 'videos'; // default
    
    if (type === 'episode' || (id && id.includes('episode-videos'))) {
        pathSegment = 'episodes';
    } else if (type === 'film') {
        pathSegment = 'films';
    } else if (type === 'show') {
        pathSegment = 'shows';
    } else if (type === 'event') {
        pathSegment = 'events';
    } else if (type === 'live-videos' || type === 'videoLive') {
        pathSegment = 'live';
    }

    return `${BASE_URL}/${urlPrefix}/${pathSegment}/${uriSlug}`;
}

/**
 * Creates a PlatformVideo object from a Red Bull API content item.
 * Handles type detection, filtering, live status, and routing to appropriate handlers.
 * 
 * @param {Object} item - Content item from API response
 * @param {string} [item.id] - Content ID in RRN format
 * @param {Object} [item.reference] - Reference container
 * @param {string} [item.reference.id] - Alternative ID location
 * @param {string} [item.reference.uriSlug] - URL slug
 * @param {string} [item.type] - Content type
 * @param {Object} [item.content] - Content metadata
 * @param {string} [item.content.title] - Content title
 * @param {Object} [item.badge] - Live/upcoming badge for events
 * @param {PlatformAuthorLink|null} [authorOverride=null] - Optional author to use instead of default "Red Bull TV".
 *   When provided, this author will be used for the video. This is used when fetching channel contents
 *   to display the channel owner (e.g., an athlete) as the video author instead of the generic platform author.
 * @returns {PlatformVideo|null} PlatformVideo object, or null if content should be filtered
 *
 * Filtering logic:
 *   1. Returns null if no ID found
 *   2. Returns null for show/collection types (handled as playlists)
 *   3. Routes audio-episodes to createPlatformAudio()
 *   4. Filters out non-playable types using NON_PLAYABLE_TYPES constant:
 *      - event-profiles, team-profiles, person-profiles (profile pages)
 *      - stories (articles)
 *      - audio-series (series containers, not episodes)
 *      - collections (collection containers)
 *   5. Filters out NON_VIDEO_ENTITY_TYPES: athlete, team, story
 *
 * Live status detection:
 *   - Events with live badge → isLive=true, uses badge.reference for stream
 *   - Events with upcoming badge → isLive=true (scheduled)
 *   - Content with 'live-videos' in ID + status='live' → isLive=true
 *
 * @example
 * // Basic usage - uses default "Red Bull TV" author
 * const video = createPlatformVideo(apiItem);
 *
 * @example
 * // With author override - used for athlete channel contents
 * const athleteAuthor = new PlatformAuthorLink(channelId, "Athlete Name", channelUrl, thumbnail);
 * const video = createPlatformVideo(apiItem, athleteAuthor);
 */
function createPlatformVideo(item, authorOverride = null) {
    let id = item.id || item.reference?.id;
    let title = item.content?.title || item.title || "Unknown Title";
    let imageBase = extractImage(item);
    let uriSlug = item.reference?.uriSlug;
    let type = item.type || item.reference?.type;
    let isLive = false;
    
    // Add locale suffix to title
    const locale = getLocaleFromItem(item);
    if (locale && locale !== "en-INT") {
        title = `${title} (${locale})`;
    }
    
    if (!id) return null;

    // Handle Live Events
    if (item.type === 'event' && item.badge?.type === 'live' && item.badge?.reference) {
        const ref = item.badge.reference;
        id = ref.id;
        uriSlug = ref.uriSlug;
        type = 'live-videos';
        isLive = true;
    } else if (item.type === 'event' && item.badge?.type === 'upcoming') {
        isLive = true;
    } else {
        isLive = (id.includes("live-videos") || item.type === 'live-videos') &&
                   (item.content?.status === 'live' || item.liveBroadcast?.status === 'live');
    }

    // Check for playlist types
    if (item.type === 'show' || item.type === 'shows' || id.includes('content:shows')) {
        return null;
    }

    // Handle audio episodes separately - they're playable via podcast API
    const isAudioContent = id.includes('audio-episodes') || type === 'audioEpisode';
    if (isAudioContent) {
        return createPlatformAudio(item, id, title, imageBase, uriSlug, type);
    }

    // Skip non-playable content types using NON_PLAYABLE_TYPES constant
    // These are profile pages, articles, or series containers that the player API doesn't support
    const isNonPlayableType = NON_PLAYABLE_TYPES.some(pattern => id.includes(pattern));
    if (isNonPlayableType) {
        return null;
    }

    // Skip non-video entity types using NON_VIDEO_ENTITY_TYPES constant
    const isNonVideoEntity = NON_VIDEO_ENTITY_TYPES.includes(type);
    if (isNonVideoEntity) {
        return null;
    }

    // Skip content without duration that isn't live (likely "coming soon" or unavailable content)
    // Live videos don't have duration, but non-live content without duration usually has no videoUrl
    const duration = item.duration || 0;
    if (duration === 0 && !isLive) {
        return null;
    }

    const thumbnails = createThumbnails(imageBase);

    // Build video URL with correct locale
    let videoUrl = buildVideoUrl(item, uriSlug, type, id);

    // If we have an author override (e.g., from athlete channel), add athlete UUID hint to URL
    // This allows getContentDetails to fetch the correct athlete when multiple are related to the video
    if (authorOverride?.id?.value) {
        const athleteUuid = extractMasterIdFromId(authorOverride.id.value);
        if (athleteUuid) {
            try {
                const u = new URL(videoUrl);
                // Avoid duplicate query entries
                if (!u.searchParams.has('athlete')) {
                    u.searchParams.append('athlete', athleteUuid);
                }
                videoUrl = u.toString();
            } catch (e) {
                log("Failed to append athlete query param: " + formatError(e));
                // Fallback for any unexpected URL parse issues
                const separator = videoUrl.includes('?') ? '&' : '?';
                if (!videoUrl.includes('athlete=')) {
                    videoUrl += `${separator}athlete=${athleteUuid}`;
                }
            }
        }
    }

    const publishedDate = extractPublishedDate(item);

    // Use provided author override (e.g., athlete for channel contents) or default to Red Bull TV
    const author = authorOverride || defaultAuthor();

    return new PlatformVideo({
        id: new PlatformID(PLATFORM, id, config.id || ""),
        name: title,
        thumbnails: thumbnails,
        author: author,
        uploadDate: publishedDate,
        duration: duration,
        viewCount: 0,
        url: videoUrl,
        isLive: isLive
    });
}

/**
 * Creates a PlatformVideo object for audio content (podcast episodes).
 * Constructs locale-aware URLs for podcast episodes.
 * 
 * @param {Object} item - Content item from API
 * @param {string} id - Full audio content ID in RRN format (e.g., rrn:content:audio-episodes:UUID:el-GR)
 * @param {string} title - Episode title
 * @param {string} imageBase - Image URL template with {op} placeholder
 * @param {string} uriSlug - URL-friendly slug for the episode
 * @param {string} type - Content type (typically 'audioEpisode')
 * @returns {PlatformVideo} PlatformVideo object representing the podcast episode
 * 
 * URL construction:
 *   - Extracts locale from ID (e.g., 'el-GR')
 *   - Converts to URL format (e.g., 'gr-el')
 *   - Builds URL: /{urlLocale}/podcast-episodes/{slug}
 * 
 * Note: Returns PlatformVideo (not PlatformAudio) because Grayjay requires
 * PlatformVideoDetails for playback, which we return from getContentDetails.
 * 
 * @example
 * createPlatformAudio(item, 'rrn:content:audio-episodes:abc:en-INT', 'Episode 1', imageUrl, 'ep1', 'audioEpisode')
 * // Returns: PlatformVideo with url='/int-en/podcast-episodes/ep1'
 */
function createPlatformAudio(item, id, title, imageBase, uriSlug, type) {
    const thumbnails = createThumbnails(imageBase);
    
    // Extract locale from ID (e.g., 'rrn:content:audio-episodes:UUID:el-GR' -> 'el-GR')
    const locale = getLocaleFromId(id);
    const urlPrefix = convertApiLocaleToUrlLocale(locale);
    
    // Add locale suffix to title
    if (locale && locale !== "en-INT") {
        title = `${title} (${locale})`;
    }
    
    // Build podcast episode URL with correct locale
    let audioUrl = `${BASE_URL}/${urlPrefix}/podcast-episodes/${uriSlug}`;
    
    const duration = item.duration || 0;
    const publishedDate = extractPublishedDate(item);
    
    return new PlatformVideo({
        id: new PlatformID(PLATFORM, id, config.id || ""),
        name: title,
        thumbnails: thumbnails,
        author: defaultAuthor("Red Bull Podcasts"),
        uploadDate: publishedDate,
        duration: duration,
        viewCount: 0,
        url: audioUrl,
        isLive: false
    });
}


/**
 * Creates a PlatformPlaylist object from a Red Bull collection/show item.
 * Handles shows, collections, and discover pages.
 * 
 * @param {Object} item - Playlist/collection item from API
 * @param {string} item.id - Collection ID in RRN format (e.g., rrn:content:shows:UUID:en-INT)
 * @param {Object} [item.content] - Content metadata
 * @param {string} [item.content.title] - Playlist title
 * @param {string} [item.title] - Alternative title location
 * @param {string} [item.type] - Content type (show, collection, discipline)
 * @param {Object} [item.reference] - Reference container
 * @param {string} [item.reference.uriSlug] - URL slug
 * @returns {PlatformPlaylist|null} PlatformPlaylist object, or null if required data missing
 * 
 * Supported types:
 *   - shows: TV series/show playlists
 *   - collections: Curated content collections
 *   - discipline: Sport discipline collections
 * 
 * URL patterns:
 *   - Shows: /int-en/shows/{slug}
 *   - Discover/Collections: /int-en/discover/{slug}
 * 
 * Returns null if ID or title is missing.
 */
function createPlatformPlaylist(item) {
    const id = item.id;
    if (!id) return null;
    
    const slug = item.reference?.uriSlug || item.uriSlug;
    if (!slug) return null;

    const title = item.content?.title || item.title || "Unknown Title";
    const image = extractImage(item);
    const highResThumbUrl = processImageUrl(image, 1920, 1080);
    const videoCount = item.nrOfEpisodes || -1;
    
    // Handle different playlist types
    let playlistUrl;
    const type = item.type || '';
    const isCollection = id.includes('collections') || type === 'discipline' || type === 'collection';
    
    if (isCollection) {
        // Collections use a different URL pattern - link to discipline/collection page
        playlistUrl = `${BASE_URL}/int-en/discover/${slug}`;
    } else {
        // Shows use /shows/ URL
        playlistUrl = `${BASE_URL}/int-en/shows/${slug}`;
    }
    
    return new PlatformPlaylist({
        id: new PlatformID(PLATFORM, id, config.id || ""),
        name: title,
        author: defaultAuthor(),
        thumbnail: highResThumbUrl,
        url: playlistUrl,
        videoCount: videoCount
    });
}

function fetchSearch(query, page) {
    const encodedQuery = encodeURIComponent(query);
    const offset = page * ITEMS_PER_PAGE;
    const url = `${API_BASE_URL}/feed/${PREFERRED_LOCALE}?rb3SearchString=${encodedQuery}&rb3SearchTab=all&rb3Schema=v1:searchResults&page[limit]=${ITEMS_PER_PAGE}&page[offset]=${offset}`;
    
    const response = httpGET({ url: url });
    if (!response.isOk) {
        return new RedBullPager([], false, "search", page, { query });
    }
    
    const items = response.body?.data?.results || [];
    
    // Deduplicate items by masterId - same content appears multiple times for different locales
    // Prefer en-INT locale, then any other locale
    const seenMasterIds = new Map(); // masterId -> item
    const deduplicatedItems = [];
    
    for (const item of items) {
        const id = item.id || '';
        const masterId = extractMasterIdFromId(id);
        
        if (!masterId) {
            // No masterId, include as-is
            deduplicatedItems.push(item);
            continue;
        }
        
        const existingItem = seenMasterIds.get(masterId);
        if (!existingItem) {
            // First occurrence of this masterId
            seenMasterIds.set(masterId, item);
            deduplicatedItems.push(item);
            continue;
        }
        
        // Check if new item has preferred locale (en-INT)
        const existingLocale = getLocaleFromItem(existingItem);
        const newLocale = getLocaleFromItem(item);
        const shouldReplaceWithPreferred = (newLocale === PREFERRED_LOCALE && existingLocale !== PREFERRED_LOCALE);
        
        if (shouldReplaceWithPreferred) {
            // Replace with en-INT version
            const itemIndex = deduplicatedItems.indexOf(existingItem);
            if (itemIndex !== -1) {
                deduplicatedItems[itemIndex] = item;
                seenMasterIds.set(masterId, item);
            }
        }
        // Otherwise keep existing item (skip duplicate)
    }
    
    const results = [];
    
    deduplicatedItems.forEach(item => {
        const id = item.id || '';
        const type = item.type || '';
        
        // Handle shows and collections as playlists
        if (type === 'shows' || id.includes('shows') || 
            id.includes('collections') || type === 'discipline' || type === 'collection') {
            const playlist = createPlatformPlaylist(item);
            if (playlist) results.push(playlist);
        } else if (type === 'athlete') {
            // Skip athletes in content search
        } else {
            const video = createPlatformVideo(item);
            if (video) results.push(video);
        }
    });
    
    return new RedBullPager(results, items.length >= 20, "search", page + 1, { query });
}

/**
 * Extracts the master ID (UUID) from a Red Bull content ID.
 * The master ID is used for deduplication - the same content can have
 * multiple locale-specific IDs but they share the same master UUID.
 * 
 * Content ID format: rrn:content:TYPE:UUID:LOCALE
 * This function extracts the UUID component.
 * 
 * @param {string} id - Full content ID in RRN format
 * @returns {string|null} UUID portion (e.g., 'abc123-def456-..'), or null if not found
 * 
 * @example
 * extractMasterIdFromId('rrn:content:videos:abc-123:en-INT') // Returns: 'abc-123'
 * extractMasterIdFromId('rrn:content:videos:abc-123:el-GR') // Returns: 'abc-123' (same UUID)
 * 
 * Used for deduplication: Items with the same master ID are considered
 * the same content in different locales.
 */
function extractMasterIdFromId(id) {
    if (!id) return null;
    const parts = id.split(':');
    if (parts.length >= 4) {
        return parts[3]; // UUID part
    }
    return null;
}

/**
 * Extracts locale from a content item, with fallback logic.
 * Tries multiple sources in order:
 *   1. item.reference.locale
 *   2. item.locale
 *   3. Locale from item.reference.id (RRN format)
 *   4. Locale from item.id (RRN format)
 *   5. 'en-INT' (default fallback)
 *
 * @param {Object} item - Content item from API response
 * @returns {string} Locale code (e.g., 'en-INT', 'el-GR')
 */
function getLocaleFromItem(item) {
    return item.reference?.locale
        || item.locale
        || extractLocaleFromId(item.reference?.id)
        || extractLocaleFromId(item.id)
        || "en-INT";
}

/**
 * Extracts the locale from an RRN ID, returning null if not found.
 * Unlike getLocaleFromId(), does not fall back to a default.
 *
 * @param {string} id - Full content ID in RRN format
 * @returns {string|null} Locale code or null
 */
function extractLocaleFromId(id) {
    if (!id) return null;
    const parts = id.split(':');
    return parts.length >= 5 ? parts[4] : null;
}

/**
 * Extracts the locale component from a Red Bull content ID.
 * 
 * Content IDs use RRN format: rrn:content:TYPE:UUID:LOCALE
 * This function extracts the final LOCALE component.
 * 
 * @param {string} id - Full content ID in RRN format
 * @returns {string} Locale code (e.g., 'en-INT', 'el-GR'), defaults to 'en-INT'
 *
 * @example
 * getLocaleFromId('rrn:content:videos:abc123:en-INT') // Returns: 'en-INT'
 * getLocaleFromId('rrn:content:episode-videos:xyz789:el-GR') // Returns: 'el-GR'
 * getLocaleFromId('invalid-id') // Returns: 'en-INT'
 */
function getLocaleFromId(id) {
    if (!id) return "en-INT";
    const parts = id.split(':');
    if (parts.length >= 5) {
        return parts[4]; // Last part is the locale
    }
    return "en-INT";
}

/**
 * Converts API locale format back to URL path format.
 * Inverse operation of convertUrlLocaleToApiLocale.
 * 
 * @param {string} apiLocale - API locale format (e.g., 'el-GR', 'en-INT', 'fr-CA')
 * @returns {string} URL locale format (e.g., 'gr-el', 'int-en', 'fr-ca')
 * 
 * Used when constructing content URLs from API responses.
 * 
 * Special cases:
 *   - 'en-INT' → 'int-en' (international uses lowercase int)
 *   - Invalid/missing input → 'int-en' (default fallback)
 * 
 * @example
 * convertApiLocaleToUrlLocale('el-GR') // Returns: 'gr-el'
 * convertApiLocaleToUrlLocale('en-INT') // Returns: 'int-en'
 * convertApiLocaleToUrlLocale('es-LAT') // Returns: 'lat-es'
 */
function convertApiLocaleToUrlLocale(apiLocale) {
    if (!apiLocale) return "int-en";
    if (apiLocale === "en-INT") return "int-en";
    
    const parts = apiLocale.split('-');
    if (parts.length !== 2) return "int-en";
    
    const [language, country] = parts;
    
    // Swap and lowercase: el-GR -> gr-el
    return `${country.toLowerCase()}-${language.toLowerCase()}`;
}

/**
 * Creates a PlatformChannel object for athlete/event channels.
 * Channels represent athletes, events, or other content creators on Red Bull TV.
 * 
 * @param {Object} item - Channel item from API
 * @param {string} item.id - Channel ID in RRN format
 * @param {Object} [item.content] - Content metadata
 * @param {string} [item.content.title] - Channel name
 * @param {string} [item.content.standfirst] - Channel description
 * @param {string} [item.title] - Alternative title location
 * @param {Object} [item.reference] - Reference container
 * @param {string} [item.reference.uriSlug] - URL slug
 * @param {string} [item.uriSlug] - Alternative slug location
 * @returns {PlatformChannel|null} PlatformChannel object, or null if no ID found
 * 
 * URL construction:
 *   - Extracts locale from item
 *   - Converts to URL prefix
 *   - Builds URL: /{urlPrefix}/athlete/{slug}
 * 
 * Note: Currently only supports athlete channels. Event channels might have
 * different URL patterns (/events/ instead of /athlete/).
 */
function createPlatformChannel(item) {
    const id = item.id;
    if (!id) return null;
    
    const slug = item.reference?.uriSlug || item.uriSlug;
    const locale = getLocaleFromItem(item);
    
    const title = item.content?.title || item.title || "Unknown";
    const image = extractImage(item);
    const thumb = processImageUrl(image, 200, 200);
    
    let url = `${BASE_URL}`;
    if (slug) {
        const urlPrefix = convertApiLocaleToUrlLocale(locale);
        url = `${BASE_URL}/${urlPrefix}/athlete/${slug}`;
    }
    
    return new PlatformChannel({
        id: new PlatformID(PLATFORM, id, config.id || ""),
        name: title,
        thumbnail: thumb,
        banner: thumb,
        subscribers: 0,
        description: item.content?.standfirst || "",
        url: url,
        links: {}
    });
}

/**
 * Deduplicates channel search results by master ID (UUID).
 * The same athlete appears multiple times for different locales; this keeps
 * only one entry per athlete, preferring the en-INT locale variant.
 *
 * @param {Array} items - Raw search result items from the API
 * @returns {Array<PlatformChannel>} Deduplicated channel objects
 */
function deduplicateChannelItems(items) {
    const seenMasterIds = new Map();
    const deduplicatedItems = [];

    for (const item of items) {
        const id = item.id || '';
        const masterId = extractMasterIdFromId(id);

        if (!masterId) {
            deduplicatedItems.push(item);
            continue;
        }

        const existingItem = seenMasterIds.get(masterId);
        if (!existingItem) {
            seenMasterIds.set(masterId, item);
            deduplicatedItems.push(item);
            continue;
        }

        // Prefer en-INT locale over others
        const existingLocale = getLocaleFromItem(existingItem);
        const newLocale = getLocaleFromItem(item);

        if (newLocale === 'en-INT' && existingLocale !== 'en-INT') {
            const itemIndex = deduplicatedItems.indexOf(existingItem);
            if (itemIndex !== -1) {
                deduplicatedItems[itemIndex] = item;
                seenMasterIds.set(masterId, item);
            }
        }
    }

    return deduplicatedItems.map(item => createPlatformChannel(item)).filter(c => c !== null);
}

/**
 * Formats an error for logging with type and message.
 * Handles non-Error objects gracefully.
 *
 * @param {*} e - The error to format
 * @returns {string} Formatted error string
 */
function formatError(e) {
    if (e instanceof Error) {
        return e.name + ": " + e.message;
    }
    return String(e);
}

log("loaded");