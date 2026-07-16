/**
 * Curiosity Stream Plugin for Grayjay
 * 
 * This plugin enables browsing, searching, and viewing Curiosity Stream content.
 * It provides access to categories, media items, and collections.
 */

// ---------------------- Constants ----------------------

const PLATFORM = 'CuriosityStream';

// Base URLs
const BASE_URLS = {
    MAIN: 'https://curiositystream.com',
    API: 'https://api.curiositystream.com',
    API_USE1: 'https://api-use1.curiositystream.com',
    CDN: 'https://cdn.curiositystream.com'
};

BASE_URLS.API_V1 = `${BASE_URLS.API_USE1}/v1`;
BASE_URLS.API_V2 = `${BASE_URLS.API}/v2`;

// Then define URLS using BASE_URLS
const URLS = {
    // Base URLs
    BASE: BASE_URLS.MAIN,
    API_V1: BASE_URLS.API_V1,
    API_V2: BASE_URLS.API_V2,
    CDN: BASE_URLS.CDN,
    // Static resources
    MISSING_IMG: `${BASE_URLS.CDN}/missing.png`,

    // Web URLs
    WEB_VIDEO: (id) => `${BASE_URLS.MAIN}/video/${id}`,

    // API endpoints
    MEDIA: (id) => `${BASE_URLS.API_V1}/media/${id}`,
    MEDIA_HLS: (id) => `${BASE_URLS.API_V1}/media/${id}?encodingsNew=true&encodingsFormat=m3u8`,
    MEDIA_DASH: (id) => `${BASE_URLS.API_V1}/media/${id}?encodingsNew=true&encodingsFormat=mpd`,
    COLLECTION: (id) => `${BASE_URLS.API_V1}/series/${id}`,
    SEARCH_V2: `${BASE_URLS.API_V2}/search`,
    SEARCH: `${BASE_URLS.API}/search`,
    FREE_PAGE: `${BASE_URLS.MAIN}/free`,
    PLAYBACK_PROGRESS: `${BASE_URLS.API_V1}/user_media`,

    // Content URLs
    SERIES_PAGE: (id) => `${BASE_URLS.MAIN}/series/${id}`,
    CATEGORY_PAGE: (slug) => `${BASE_URLS.MAIN}/categories/${slug}`,

    // API query endpoints
    MEDIA_QUERY: `${BASE_URLS.API_V1}/media`,
    COLLECTIONS_QUERY: `${BASE_URLS.API_V1}/collections`,
    PLUGIN_ICON: 'https://plugins.grayjay.app/CuriosityStream/CuriosityStreamIcon.png'
};

// Regular expressions for URL matching
const REGEX = {
    CONTENT_URL: new RegExp(`^${BASE_URLS.MAIN}/(?:title|video|media|documentary|episode)/(\\d+)/?`),
    CATEGORY_URL: new RegExp(`^${BASE_URLS.MAIN}/categories/([a-zA-Z0-9_-]+)/?`),
    SERIES_URL: new RegExp(`^${BASE_URLS.MAIN}/series/(\\d+)/?`),
    CHANNEL_URL: new RegExp(`^${BASE_URLS.MAIN}/(?:categories/([a-zA-Z0-9_-]+)|series/(\\d+))/?`),
    // Video format detection
    IS_HLS: /\.m3u8($|\?)|[?&].*m3u8/i,
    IS_DASH: /\.mpd($|\?)|[?&].*mpd/i,

    // Script extraction patterns
    SCRIPT_URL: /src=["'](\/_next\/static\/chunks\/pages\/free-[^"']*?)["']/i,
    FREE_VIDEOS_DATA: /t\.Z\s*=\s*(\[.*?\])/s,
};

// API settings
const API = {
    VIDEOS_PER_PAGE: 10,
    PROGRESS_REPORT_INTERVAL: 15, // Report progress every 15 seconds
    MIN_TRACKING_DURATION: 5 // Minimum playback duration to track (5 seconds)
};

// Default HTTP headers
const DEFAULT_HEADERS = {
    'content-type': 'application/json',
    'user-agent': 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36'
};

// Plugin state
let config = {};
let state = {
    buildId: '',
    has_subscription: false,
    categories: {},
    collections: {} // Cache for collection data
};
let settings = {};

// ====================== PLUGIN ENTRY POINTS ======================

/**
 * Initialize the plugin with configuration
 * @param {Object} conf - Plugin configuration
 * @param {Object} setting - User settings
 * @param {string} saveStateStr - Saved state from previous sessions
 */
source.enable = function(conf, setting, saveStateStr) {
    config = conf;
    settings = setting;
    
    if (saveStateStr) {
        try {
            state = JSON.parse(saveStateStr);
            // Ensure collections object exists
            if (!state.collections) {
                state.collections = {};
            }
        } catch (e) {
            log(e);
            bridge.log('Failed to restore state');
        }
    } else {
        loadInitialState();
    }
};

/**
 * Save the current plugin state
 * @returns {string} State data as JSON string
 */
source.saveState = () => {
    return JSON.stringify(state);
};

/**
 * Get home page content
 * @returns {ContentPager} Paged results for home page
 */
source.getHome = function () {
    // Fetch regular content
    const result = fetchContentDataWithCollections({
        url: `${URLS.API_V1}/media?collections=true&limit=${API.VIDEOS_PER_PAGE}`
    });

    // Get combined content (with free videos first for non-subscribers)
    const combinedResults = getCombinedContent(
        result.items || [],
        state.has_subscription
    );

    return new CuriosityStreamHomePager(
        combinedResults,
        result.hasMore,
        { page: 1, hasShownFreeVideos: true }
    );
};

/**
 * Check if a URL is a Curiosity Stream content page
 * @param {string} url - URL to check
 * @returns {boolean} True if URL points to content
 */
source.isContentDetailsUrl = function (url) {
    return REGEX.CONTENT_URL.test(url);
};

/**
 * Get detailed information for content
 * @param {string} url - Content URL
 * @returns {PlatformVideoDetails} Video details
 */
source.getContentDetails = function (url) {

    const contentId = getContentIdFromUrl(url);
    if (!contentId) {
        throw new ScriptException('Invalid Curiosity Stream URL');
    }

    if (url.includes('free=true')) {
        const freeVideo = findFreeVideoById(contentId);
        if (freeVideo) {
            return createPlatformVideoDetails({
                media: freeVideo,
                manifest: freeVideo.manifest,
                isFreeVideo: true
            });
        }
    }

    // Get regular content details
    const isLoggedIn = bridge.isLoggedIn();
    const details = fetchMediaDetails(contentId, isLoggedIn);

    if (!details.media) {
        throw new ScriptException('Content not found');
    }
    
    // If the media is part of a collection, fetch collection details
    const media = details.media;
    let collection = null;
    
    if (media.collection_id) {
        try {
            collection = fetchCollectionDetails(media.collection_id);
            // Cache the collection for future use
            if (collection) {
                state.collections[media.collection_id] = collection;
            }
        } catch (e) {
            log(`Failed to fetch collection details: ${e.message}`);
        }
    }
    
    return createPlatformVideoDetails({
        media,
        collection,
        isFreeVideo: false
    });
};

/**
 * Check if a URL is a Curiosity Stream channel
 * @param {string} url - URL to check
 * @returns {boolean} True if URL points to a channel
 */
source.isChannelUrl = function(url) {
    return REGEX.CHANNEL_URL.test(url);
};

/**
 * Get channel information
 * @param {string} url - Channel URL
 * @returns {PlatformChannel} Channel information
 */
source.getChannel = function(url) {
    // Check if this is a category URL
    const categoryMatch = url.match(REGEX.CATEGORY_URL);
    if (categoryMatch) {
        const categorySlug = categoryMatch[1];
        return getCategoryChannel(categorySlug, url);
    }
    
    // Check if this is a series URL
    const seriesMatch = url.match(REGEX.SERIES_URL);
    if (seriesMatch) {
        const seriesId = seriesMatch[1];
        const collection = fetchCollectionDetails(seriesId);
        return createChannelFromCollection(collection);
    }
    
    throw new ScriptException('Invalid Channel URL');
};

/**
 * Get channel contents
 * @param {string} url - Channel URL
 * @returns {ContentPager} Paged results for channel content
 */
source.getChannelContents = function(url) {
    // Check if this is a category URL
    const categoryMatch = url.match(REGEX.CATEGORY_URL);
    if (categoryMatch) {
        const categorySlug = categoryMatch[1];
        
        // Get the category from state to ensure we have correct data
        const category = Object.values(state.categories)
            .find(cat => cat.name === categorySlug);
        
        const result = fetchContentDataWithCollections({
            url: `${URLS.API_V1}/media?filterBy=category&term=${categorySlug}&collections=true&limit=${API.VIDEOS_PER_PAGE}&sort=creation_date_desc`,
            processingInfo: { 
                categorySlug, 
                categoryName: category.label 
            }
        });
        
        return new CategoryContentPager(
            result.items,
            result.hasMore,
            { page: 1, category }
        );
    }
    
    // Check if this is a series URL
    const seriesMatch = url.match(REGEX.SERIES_URL);
    if (seriesMatch) {
        const seriesId = seriesMatch[1];
        const collection = fetchCollectionDetails(seriesId);
        
        if (!collection) {
            throw new ScriptException('Collection not found');
        }
        
        // For collections, we already have all the videos in the collection object
        // Sort in reverse order to show recent episodes first
        const videos = collection.media
            .sort((a, b) => b.collection_order - a.collection_order)
            .map(media => createVideoFromMedia(media, collection));
        
        // Return a content pager with all videos (no paging needed for collections)
        return new ContentPager(videos, false);
    }
    
    throw new ScriptException('Invalid Channel URL');
};

/**
 * Get search capabilities
 * @returns {Object} Search capabilities
 */
source.getSearchCapabilities = () => {
    return {
        types: [Type.Feed.Mixed],
        sorts: [Type.Order.Chronological],
        filters: []
    };
};

/**
 * Search for content
 * @param {string} query - Search query
 * @returns {ContentPager} Paged search results
 */
source.search = function(query, type, order, filters) {
    const result = fetchSearchData(query);
    
    return new SearchPager(
        result.items,
        result.hasMore,
        { query, page: 1, totalPages: result.body.nbPages }
    );
};

/**
 * Search for channels
 * @param {string} query - Search query
 * @returns {ContentPager} Paged search results
 */
source.searchChannels = function(query) {
    const channels = [];
    const searchQuery = query.toLowerCase().trim();
    const addedChannelIds = new Set(); // Track channel IDs to avoid duplicates
    
    // Search in categories
    Object.values(state.categories)
        .forEach(category => {
            const fullName = category.label.toLowerCase();
            if (fullName.includes(searchQuery)) {
                const channelId = category.name;
                if (!addedChannelIds.has(channelId)) {
                    channels.push(createChannelFromCategory(category));
                    addedChannelIds.add(channelId);
                }
            }
        });
    
    // Search for series/collections that match the query
    try {
        const searchBody = {
            query: searchQuery,
            hitsPerPage: 20,
            page: 0,
            filters: {
                obj_type: ["collection"] // Only search for collections
            }
        };
        
        const headers = {
            "Content-Type": "application/json"
        };

        const res = http.POST(URLS.SEARCH_V2, JSON.stringify(searchBody), headers);

        if (res.isOk) {
            const body = JSON.parse(res.body);
            const seriesHits = body.hits || [];
            
            // Process each series and add to channels
            seriesHits.forEach(hit => {
                // Always use collection_id as the series identifier
                const collectionId = hit.collection_id;
                
                // Skip entries without a collection_id
                if (!collectionId) {
                    return;
                }
                
                const seriesChannelId = `series_${collectionId}`;
                
                // Skip if we've already added this channel
                if (addedChannelIds.has(seriesChannelId)) {
                    return;
                }
                
                // Check if we have complete collection data in cache
                let collection = null;
                
                // Try to get from cache first using the correct ID
                if (collectionId && state.collections[collectionId]) {
                    collection = state.collections[collectionId];
                } 
                
                // If not in cache, create minimal collection object from search result
                if (!collection) {
                    collection = {
                        id: collectionId,
                        title: hit.title,
                        description: hit.description || hit.short_description || "",
                        image_large: hit.image_large,
                        image_medium: hit.image_medium,
                        media_count: hit.media_count || 0
                    };
                }
                
                channels.push(createChannelFromCollection(collection));
                addedChannelIds.add(seriesChannelId);
            });
        }
    } catch (error) {
        log(`Error searching for series channels: ${error.message}`);
    }
    
    return new ContentPager(channels, false);
};

/**
 * Check if a URL is a playlist
 * @param {string} url - URL to check
 * @returns {boolean} True if URL points to a playlist
 */
source.isPlaylistUrl = function(url) {
    return REGEX.SERIES_URL.test(url);
};

/**
 * Get playlist details
 * @param {string} url - Playlist URL
 * @returns {PlatformPlaylistDetails} Playlist details
 */
source.getPlaylist = function(url) {
    const match = url.match(REGEX.SERIES_URL);
    if (!match) {
        throw new ScriptException('Invalid Collection URL');
    }
    
    const seriesId = match[1];
    const collection = fetchCollectionDetails(seriesId);
    
    return createPlaylistDetails(collection);
};

/**
 * Get comments for content
 * @returns {CommentPager} Empty comment pager (comments not supported)
 */
source.getComments = function(url) {
    return new CommentPager([], false);
};

/**
 * Get playback tracker for tracking video progress
 * @param {string} url - Content URL
 * @returns {PlaybackTracker|null} Playback tracker if available
 */
source.getPlaybackTracker = function(url) {
    // Only track playback if user is logged in and setting is enabled
    if (!bridge.isLoggedIn() || !settings.curiositystreamActivity) {
        return null;
    }
    
    try {
        const contentId = getContentIdFromUrl(url);
        if (contentId) {
            return new CuriosityStreamPlaybackTracker(contentId, API.PROGRESS_REPORT_INTERVAL);
        }
    } catch (error) {
        log(`Error creating playback tracker: ${error.message}`);
    }
    
    return null;
};


/**
 * Gets playlists for a channel (currently only for series)
 * @param {string} url - Channel URL
 * @returns {PlaylistPager} Paged results for channel playlists
 */
source.getChannelPlaylists = function(url) {

    const seriesMatch = url.match(REGEX.SERIES_URL);
    if (seriesMatch) {
        const seriesId = seriesMatch[1];
        const collection = fetchCollectionDetails(seriesId);
        
        if (!collection) {
            return new PlaylistPager([], false);
        }
        
        const playlist = createPlaylistFromCollection(collection);
        
        if (collection.media && Array.isArray(collection.media)) {
            collection.media = collection.media.sort((a, b) => b.collection_order - a.collection_order);
            state.collections[seriesId] = collection;
        }
        
        // Return a playlist pager with just this series
        return new PlaylistPager([playlist], false);
    }
    
    // For categories or other channel types, return empty pager
    return new PlaylistPager([], false);
};

// ====================== CORE FUNCTIONALITY ======================

/**
 * Loads initial state data, including categories and subscription status
 */
function loadInitialState() {
    // Initialize collections cache
    state.collections = {};
    
    // Check subscription status if logged in
    if (bridge.isLoggedIn()) {
        const res = http.GET(`${URLS.API_V1}/user`, DEFAULT_HEADERS, true);
        if (res.isOk) {
            const body = JSON.parse(res.body);
            state.has_subscription = body.data.is_active && !body.data.is_free;
        }
    }
    
    // Load categories
    const url = `${URLS.API_V1}/categories`;
    const res = http.GET(url, DEFAULT_HEADERS);
    if (res.isOk) {
        const data = JSON.parse(res.body)?.data ?? [];
        processCategories(data);
    }
}

/**
 * Process categories data and store in state
 * @param {Array} categories - Category data from API
 */
function processCategories(categories) {
    categories.forEach(category => {
        const { background_url, description, image_url, label, name } = category;
        const parentCategory = {
            background_url: background_url !== URLS.MISSING_IMG ? background_url : '',
            description,
            image_url: image_url !== URLS.MISSING_IMG ? image_url : '',
            label,
            name
        };

        state.categories[name] = parentCategory;
        
        // Process subcategories
        if (category.subcategories) {
            category.subcategories.forEach(subCategory => {
                
                const existingCategoryNames = Object.values(state.categories).map(cat => cat.label);

                let { background_url, description, image_url, label, name } = subCategory;

                if(image_url === URLS.MISSING_IMG || !image_url) {
                    image_url = URLS.PLUGIN_ICON;
                }

                if(existingCategoryNames.includes(label)) {
                    label = `${parentCategory.label} - ${label}`
                }
                
                state.categories[name] = {
                    background_url: background_url !== URLS.MISSING_IMG ? background_url : '',
                    description,
                    image_url: image_url !== URLS.MISSING_IMG ? image_url : '',
                    label,
                    name
                };
            });
        }
    });
}

/**
 * Fetches media details from the API
 * @param {string} contentId - Content ID
 * @param {boolean} isLoggedIn - Whether user is logged in
 * @returns {Object} Media details
 */
function fetchMediaDetails(contentId, isLoggedIn) {
    const batch = http.batch();
    batch.GET(URLS.MEDIA_HLS(contentId), DEFAULT_HEADERS, isLoggedIn);
    batch.GET(URLS.MEDIA_DASH(contentId), DEFAULT_HEADERS, isLoggedIn);

    const [hlsResponse, dashResponse] = batch.execute();

    if (!hlsResponse.isOk && !dashResponse.isOk) {
        throw new ScriptException('Failed to fetch content details');
    }
    
    // Prefer HLS if available, since dash is not supported on desktop yet
    const mainDetails = hlsResponse.isOk ? 
        JSON.parse(hlsResponse.body) : 
        JSON.parse(dashResponse.body);
    
    return {
        media: mainDetails.data
    };
}

/**
 * Creates a PlatformVideoDetails object with consistent handling for both regular and free videos
 * 
 * @param {Object} options - Options for creating the video details
 * @param {Object} options.media - Media data (API or free video format)
 * @param {boolean} options.isFreeVideo - Whether this is a free video
 * @param {Object} options.manifest - Manifest data for free videos
 * @param {Object} options.collection - Collection data if part of a collection
 * @returns {PlatformVideoDetails} Video details object
 */
function createPlatformVideoDetails(options) {
    const { 
        media, 
        isFreeVideo = false,
        manifest = null,
        collection = null 
    } = options;
    
    if (!media) {
        throw new ScriptException("Media object is required");
    }

    const isPreview = !isFreeVideo && isMediaPreview(media) && !state.has_subscription;

    function getDuration() {
        const rawDuration = isFreeVideo ? parseDuration(media.duration) : (media.duration || 0);
        // 5 minutes (300 seconds) is the standard preview length for Curiosity Stream videos
        return isPreview ? 300 : rawDuration;
    }
    
    function getMediaId() {
        return isFreeVideo ? media.media.toString() : media.id.toString();
    }
    
    // Extract basic properties based on video type
    const mediaId = getMediaId();

    let title = isFreeVideo 
        ? (media.subtitle ? `${media.title}: ${media.subtitle}` : media.title) 
        : formatMediaTitle(media);

    if(isPreview) {
        title += ' (preview)';
    }

    const description = isFreeVideo 
        ? `${media.subtitle || ''}\n\nCategory: ${media.category}\nYear: ${media.year_produced}\n\nFree video from Curiosity Stream`
        : formatMediaDescription(media, isMediaPreview(media) && !state.has_subscription, collection);
    
    // Create appropriate thumbnails
    const thumbnails = isFreeVideo 
        ? new Thumbnails([new Thumbnail(media.thumbnail || '', 0)])
        : createThumbnails(media);
    
    // Determine the video URL
    const videoUrl = isFreeVideo 
        ? `${URLS.BASE}/video/${mediaId}?free=true`
        : constructMediaUrl(media);
    
    // Determine share URL
    const shareUrl = isFreeVideo 
        ? `${BASE_URLS.MAIN}/free/title/${media.id}`
        : constructMediaUrl(media);
    
    // Set up author/channel link
    let author;
    if (isFreeVideo) {
        // For free videos, use category
        const categoryObj = getCategoryByLabel(media.category);
        author = categoryObj
            ? createChannelLinkFromCategory(categoryObj)
            : createCategoryAuthorLink(media.category.toLowerCase(), media.category);
    } else if (collection) {
        // For collection videos, use collection
        author = createAuthorLinkFromCollection(collection);
    } else {
        // For standalone videos, use category
        const category = getCategoryByLabel(media.primary_category);
        author = category ? createChannelLinkFromCategory(category) : createDefaultChannelLink();
    }
    
    // Create video sources with validation and fallbacks
    const sources = [];
    const duration = getDuration();
    
    if (isFreeVideo) {
        // Handle free video sources
        if (manifest && manifest.m3u8) {     
            sources.push(new HLSSource({
                name: "HLS",
                url: manifest.m3u8,
                duration: duration,
                priority: true
            }));
        } else if (manifest && manifest.mpd) {
            // Add DASH as fallback
            sources.push(new DashSource({
                name: "DASH",
                url: manifest.mpd,
                duration: duration
            }));
            log("Warning: Only DASH format available for free video, which may not work on desktop");
        }
    } else {
        // Handle regular video sources
        if (media.encodings?.length) {
            for (const encoding of media.encodings) {
                const url = encoding.master_playlist_url;
                if (!url) continue;
                
                if (REGEX.IS_HLS.test(url)) {
                    sources.push(new HLSSource({
                        name: "HLS",
                        url: url,
                        duration: duration,
                        priority: true
                    }));
                } else if (REGEX.IS_DASH.test(url)) {
                    sources.push(new DashSource({
                        name: "DASH",
                        url: url,
                        duration: duration
                    }));
                }
            }
        }
    }
    
    // If we couldn't get any sources, log the issue
    if (sources.length === 0) {
        throw new UnavailableException(`No playable sources found for ${isFreeVideo ? 'free ' : ''}video ${mediaId}`);
    }
    
    const rating = isFreeVideo 
        ? null
        : createMediaRating(media);
    
    const viewCount = isFreeVideo ? -1 : (media.num_views || media.view_count || -1);
    
    // Create and return the PlatformVideoDetails object
    const videoDetails = new PlatformVideoDetails({
        id: new PlatformID(PLATFORM, mediaId, config.id),
        name: title,
        author: author,
        thumbnails: thumbnails,
        description: description,
        duration: duration,
        url: videoUrl,
        shareUrl: shareUrl,
        isLive: false,
        video: sources.length > 0 ? new VideoSourceDescriptor(sources) : null,
        rating: rating,
        viewCount: viewCount,
        datetime: 0 // Without additional metadata request, we don't have this
    });
    
    if(!isFreeVideo) {
        videoDetails.getContentRecommendations = function() {
            return getRecommendations(mediaId);
        };
    }
    
    return videoDetails;
}

/**
 * Create video sources based on available encodings
 * @param {Object} media - Media data
 * @returns {Array} Array of video sources
 */
function createVideoSources(media) {
    const sources = [];
    
    // Set duration, limiting to 5 minutes for preview videos without subscription
    const duration = (isMediaPreview(media) && !state.has_subscription) ? 300 : (media.duration || 0);
    
    if (media.encodings?.length) {
        
        for (const encoding of media.encodings) {
            const url = encoding.master_playlist_url;

            if (!url) continue;
            
            if (REGEX.IS_HLS.test(url)) {
                sources.push(new HLSSource({
                    name: "HLS",
                    url,
                    duration,
                    priority: true
                }));
            } else if (REGEX.IS_DASH.test(url)) {
                sources.push(new DashSource({
                    name: "DASH",
                    url,
                    duration
                }));
            } else {
                log(`Unknown streaming format for URL: ${url}`);
            }
        }
    }
    
    return sources;
}

/**
 * Parse duration string to seconds
 * @param {string} durationStr - Duration string (e.g., "50m", "1h 30m")
 * @returns {number} Duration in seconds
 */
function parseDuration(durationStr) {
    if (!durationStr) return 0;

    // Regular expressions to extract hours, minutes and seconds
    const hoursMatch = durationStr.match(/(\d+)h/);
    const minutesMatch = durationStr.match(/(\d+)m/);
    const secondsMatch = durationStr.match(/(\d+)s/);

    let hours = hoursMatch ? parseInt(hoursMatch[1]) : 0;
    let minutes = minutesMatch ? parseInt(minutesMatch[1]) : 0;
    let seconds = secondsMatch ? parseInt(secondsMatch[1]) : 0;

    return (hours * 3600) + (minutes * 60) + seconds;
}

/**
 * Gets free videos from the Curiosity Stream free page
 * @returns {Array} Array of free video objects
 */
function getFreeVideos() {
    try {
        const scriptUrl = getFreePageScriptUrl();
        if (!scriptUrl) return [];
        const freeVideos = extractFreeVideosFromScript(scriptUrl);
        log(`Loaded ${freeVideos.length} free videos`);
        return freeVideos;
    } catch (error) {
        log(`Error fetching free videos: ${error.message}`);
        return [];
    }
}

/**
 * Gets the script URL containing free videos data
 * @returns {string|null} Script URL if found
 */
function getFreePageScriptUrl() {
    try {
        const res = http.GET(URLS.FREE_PAGE, DEFAULT_HEADERS);
        if (!res.isOk) {
            log(`Failed to fetch free page: HTTP ${res.code}`);
            return null;
        }
        
        const scriptUrl = extractScriptUrl(res.body);
        if (!scriptUrl) {
            log("Script URL pattern not found in page content");
        }
        return scriptUrl;
    } catch (error) {
        log(`Error in getFreePageScriptUrl: ${error.message}`);
        return null;
    }
}
/**
 * Extracts free videos data from script content
 * @param {string} scriptUrl - URL of the script file
 * @returns {Array} Array of free video objects
 */
function extractFreeVideosFromScript(scriptUrl) {
    try {
        const scriptRes = http.GET(`${BASE_URLS.MAIN}${scriptUrl}`, DEFAULT_HEADERS);
        
        if (!scriptRes.isOk) {
            log(`Failed to fetch script: HTTP ${scriptRes.code}`);
            return [];
        }
        
        const match = scriptRes.body.match(REGEX.FREE_VIDEOS_DATA);
        if (!match || !match[1]) {
            log("Free videos data pattern not found in script content");
            return [];
        }
        
        // Try to parse the content
        try {
            const freeVideos = parseJsObject(match[1]);
            return Array.isArray(freeVideos) ? freeVideos : [];
        } catch (parseError) {
            log(`Error parsing free videos data: ${parseError.message}`);
            return [];
        }
    } catch (error) {
        log(`Error in extractFreeVideosFromScript: ${error.message}`);
        return [];
    }
}
/**
 * Finds a free video by its media ID
 * @param {string} mediaId - Media ID to search for
 * @returns {Object|undefined} Free video object if found
 */
function findFreeVideoById(mediaId) {
    const freeVideos = getFreeVideos();
    return freeVideos.find(video => video.media.toString() === mediaId);
}

/**
 * Creates a video object from a free video
 * @param {Object} freeVideo - Free video data
 * @returns {PlatformVideo} Video object
 */
function createVideoFromFreeVideo(freeVideo) {
    // Extract information from the free video object
    const {
        media,
        title,
        subtitle,
        thumbnail,
        rating_percentage,
        year_produced,
        category,
        duration
    } = freeVideo;

    // Parse the duration string (e.g., "50m" to seconds)
    const durationSeconds = parseDuration(duration);

    // Create a category-based author
    const categoryObj = getCategoryByLabel(category);
    const author = categoryObj
        ? createChannelLinkFromCategory(categoryObj)
        : createCategoryAuthorLink(category.toLowerCase(), category);

    return new PlatformVideo({
        id: new PlatformID(PLATFORM, media.toString(), config.id),
        name: subtitle ? `${title}: ${subtitle}` : title,
        author,
        thumbnails: new Thumbnails([new Thumbnail(thumbnail, 0)]),
        duration: durationSeconds,
        viewCount: -1, // We don't have view count for free videos
        // datetime: year_produced ? new Date(`${year_produced}-01-01`).getTime() / 1000 : 0,
        url: `${URLS.BASE}/video/${media}?free=true`,
        isLive: false,
        description: subtitle || title
    });
}

/**
 * Get a category channel by slug
 * @param {string} categorySlug - Category slug
 * @param {string} url - Full channel URL
 * @returns {PlatformChannel} Channel object
 */
function getCategoryChannel(categorySlug, url) {
    let category = Object.values(state.categories)
        .find(category => category.name === categorySlug);
    
    if (!category) {
        category = {
            id: new PlatformID(PLATFORM, '', config.id),
            name: categorySlug,
            thumbnail: '',
            banner: '',
            subscribers: 0,
            description: '',
            url: url
        };
    }
    
    return createChannelFromCategory(category);
}

/**
 * Creates playlist details from collection data
 * @param {Object} collection - Collection data
 * @returns {PlatformPlaylistDetails} Playlist details
 */
function createPlaylistDetails(collection) {
    if (!collection) {
        throw new ScriptException('Collection not found');
    }
    
    // Get media (videos) in the collection
    const videos = collection.media
        .sort((a, b) => a.collection_order - b.collection_order)
        .map(media => createVideoFromMedia(media, collection));
    
    // Create PlatformPlaylistDetails
    return new PlatformPlaylistDetails({
        id: new PlatformID(PLATFORM, collection.id.toString(), config.id),
        name: collection.title,
        author: createAuthorLinkFromCollection(collection),
        description: collection.description || '',
        thumbnail: collection.image_large || collection.image_medium || '',
        url: constructCollectionUrl(collection),
        videoCount: collection.media_count || videos.length,
        contents: new VideoPager(videos, false)
    });
}

/**
 * Fetches collection details from the API
 * @param {string} seriesId - Series ID
 * @returns {Object} Collection data
 */
function fetchCollectionDetails(seriesId) {
    // Check if collection is already in state cache
    if (state.collections[seriesId]) {
        return state.collections[seriesId];
    }
    
    const res = http.GET(`${URLS.API_V2}/series/${seriesId}`, DEFAULT_HEADERS);
    
    if (!res.isOk) {
        throw new ScriptException('Failed to fetch collection');
    }
    
    const collection = JSON.parse(res.body).data;
    
    // Cache the collection
    if (collection) {
        state.collections[seriesId] = collection;
    }
    
    return collection;
}

/**
 * Fetches collection details in batch
 * @param {Array} collectionIds - Array of collection IDs
 * @returns {Object} Map of collection ID to collection data
 */
function fetchCollectionDetailsBatch(collectionIds) {
    if (!collectionIds || collectionIds.length === 0) {
        return {};
    }
    
    // Filter out collection IDs that are already cached
    const uncachedIds = collectionIds.filter(id => !state.collections[id]);
    
    // If all collections are cached, return the cache
    if (uncachedIds.length === 0) {
        return collectionIds.reduce((result, id) => {
            result[id] = state.collections[id];
            return result;
        }, {});
    }
    
    // Create batch request for uncached collections
    const batch = http.batch();
    uncachedIds.forEach(id => {
        batch.GET(`${URLS.API_V2}/series/${id}`, DEFAULT_HEADERS);
    });
    
    const responses = batch.execute();
    
    // Process the responses
    const collections = {};
    for (let i = 0; i < uncachedIds.length; i++) {
        const id = uncachedIds[i];
        const response = responses[i];
        
        if (response && response.isOk) {
            try {
                const collection = JSON.parse(response.body).data;
                if (collection) {
                    collections[id] = collection;
                    // Cache the collection
                    state.collections[id] = collection;
                }
            } catch (e) {
                log(`Error parsing collection ${id}: ${e.message}`);
            }
        }
    }
    
    // Add cached collections
    collectionIds.forEach(id => {
        if (state.collections[id] && !collections[id]) {
            collections[id] = state.collections[id];
        }
    });
    
    return collections;
}

/**
 * Get content recommendations for a video
 * @param {string} videoId - Video ID
 * @returns {ContentPager} Paged results with recommendations
 */
function getRecommendations(videoId) {
    const result = fetchContentDataWithCollections({
        url: `${URLS.API_V1}/media?filterBy=recommended&term=${videoId}&limit=${API.VIDEOS_PER_PAGE}`
    });

    return new RecommendationsPager(
        result.items,
        result.hasMore,
        { videoId: videoId, page: 1 }
    );
}

// ====================== DATA MAPPING FUNCTIONS ======================

/**
 * Creates a channel from category data
 * @param {Object} category - Category data
 * @returns {PlatformChannel} Channel object
 */
function createChannelFromCategory(category) {
    return new PlatformChannel({
        id: new PlatformID(PLATFORM, category.name, config.id),
        name: category.label,
        thumbnail: category.image_url || '',
        banner: category.background_url || '',
        subscribers: 0,
        description: `Documentaries in the ${category.label} category`,
        url: URLS.CATEGORY_PAGE(category.name)
    });
}

/**
 * Creates a channel from collection data
 * @param {Object} collection - Collection data
 * @returns {PlatformChannel} Channel object
 */
function createChannelFromCollection(collection) {
    return new PlatformChannel({
        id: new PlatformID(PLATFORM, `series_${collection.id}`, config.id),
        name: collection.title,
        thumbnail: collection.image_large || collection.image_medium || '',
        banner: collection.background_url || '',
        subscribers: 0,
        description: collection.description || `Documentary series "${collection.title}"`,
        url: constructCollectionUrl(collection)
    });
}

/**
 * Creates an author link from category data
 * @param {Object} category - Category data
 * @returns {PlatformAuthorLink} Author link
 */
function createChannelLinkFromCategory(category) {
    return new PlatformAuthorLink(
        new PlatformID(PLATFORM, category?.name ?? '', config.id),
        category?.label ?? '',
        category?.name ? URLS.CATEGORY_PAGE(category.name) : '',
        category?.image_url ?? ''
    );
}

/**
 * Creates an author link from collection data
 * @param {Object} collection - Collection data
 * @returns {PlatformAuthorLink} Author link
 */
function createAuthorLinkFromCollection(collection) {
    return new PlatformAuthorLink(
        new PlatformID(PLATFORM, `series_${collection.id}`, config.id),
        collection.title,
        constructCollectionUrl(collection),
        collection.image_large || collection.image_medium || ''
    );
}

/**
 * Creates a video object from media data
 * @param {Object} media - Media data
 * @param {Object} collection - Collection data if media is part of a collection
 * @returns {PlatformVideo} Video object
 */
function createVideoFromMedia(media, collection) {
    // Check if media is part of a collection
    const hasCollection = !!collection;
    
    // Use collection/series as author if available, otherwise use category
    let author;
    if (hasCollection) {
        author = createAuthorLinkFromCollection(collection);
    } else {
        const creatorId = media.primary_category || 'CuriosityStream';
        const category = getCategoryByLabel(creatorId);
        author = createChannelLinkFromCategory(category);
    }
    
    // Check for preview content
    const is_preview = isMediaPreview(media) && !state.has_subscription;
    
    // Format title and duration
    let name = formatMediaTitle(media);
    let duration = media.duration || 0;
    
    if (is_preview) {
        name += ' (preview)';
        duration = 300; // Preview videos are limited to 5 minutes (300 seconds)
    }
    
    return new PlatformVideo({
        id: new PlatformID(PLATFORM, media.id.toString(), config.id),
        name,
        thumbnails: createThumbnails(media),
        description: media.description || media.short_description,
        duration,
        viewCount: media.view_count || -1,
        datetime: 0, // Without metadata request, we don't have this
        url: constructMediaUrl(media),
        isLive: false,
        author: author
    });
}

/**
 * Creates a playlist from collection data
 * @param {Object} collection - Collection data
 * @returns {PlatformPlaylist} Playlist object
 */
function createPlaylistFromCollection(collection) {
    return new PlatformPlaylist({
        id: new PlatformID(PLATFORM, collection.id.toString(), config.id),
        name: collection.title,
        url: constructCollectionUrl(collection),
        thumbnail: collection.image_large || collection.image_medium || '',
        author: createAuthorLinkFromCollection(collection),
        videoCount: collection.media_count || 0
    });
}

// ====================== UTILITY FUNCTIONS ======================

/**
 * Gets a category by label
 * @param {string} label - Category label
 * @returns {Object} Category data
 */
function getCategoryByLabel(label) {
    return Object.values(state.categories)
        .find(category => category && category.label === label);
}

/**
 * Creates thumbnails for media
 * @param {Object} media - Media data
 * @returns {Thumbnails} Thumbnails object
 */
function createThumbnails(media) {

    const thumbnailUrls = [
        media.image_medium, 
        media.image_large
    ].filter(Boolean);

    if(!thumbnailUrls.length) {
        thumbnailUrls.push(URLS.PLUGIN_ICON);
    }

    return new Thumbnails(thumbnailUrls.map((url, index) => new Thumbnail(url, index)));
}

/**
 * Creates a rating object for media
 * @param {Object} media - Media data
 * @returns {RatingLikesDislikes} Rating object
 */
function createMediaRating(media) {
    return new RatingLikesDislikes(
        Math.round((media.rating || 0) * (media.num_ratings || 0)), // Likes (approximation)
        Math.round((media.num_ratings || 0) * (1 - (media.rating_percentage || 0) / 100)) // Dislikes (approximation)
    );
}

/**
 * Formats media title
 * @param {Object} media - Media data
 * @returns {string} Formatted title
 */
function formatMediaTitle(media) {
    return media.episode_number_display 
        ? `${media.episode_number_display} - ${media.title}` 
        : media.title;
}

/**
 * Formats media description
 * @param {Object} media - Media data
 * @param {boolean} is_preview - Whether the media is preview only
 * @param {Object} collection - Collection data if available
 * @returns {string} Formatted description
 */
function formatMediaDescription(media, is_preview, collection) {
    let description = media.description || media.short_description || '';
    
    if (media.collection_id) {
        const collectionInfo = collection || state.collections[media.collection_id];
        const collectionTitle = collectionInfo ? collectionInfo.title : "series";
        description += `<p><a href="${URLS.BASE}/series/${media.collection_id}">View complete "${collectionTitle}" series</a></p>`;
    }
    
    if (is_preview) {
        description += `<p>This is a preview video. Login with a valid subscription to watch the full content.</p>`;
    }
    
    return description;
}

/**
 * Checks if media is preview-only
 * @param {Object} media - Media data
 * @returns {boolean} True if media is preview-only
 */
function isMediaPreview(media) {
    return media?.status === 'preview';
}

/**
 * Constructs a media URL
 * @param {Object} media - Media data
 * @returns {string} Media URL
 */
function constructMediaUrl(media) {
    return URLS.WEB_VIDEO(media.title_id || media.id);
}

/**
 * Constructs a collection URL
 * @param {Object} collection - Collection data
 * @returns {string} Collection URL
 */
function constructCollectionUrl(collection) {
    return URLS.SERIES_PAGE(collection.id);
}

/**
 * Constructs a category URL
 * @param {string} category - Category name or object
 * @returns {string} Category URL
 */
function constructCategoryUrl(category) {
    // Convert to slug, replacing spaces and special characters
    const categorySlug = typeof category === 'string'
        ? category.toLowerCase().replace(/[&\\s]+/g, '-').replace(/[^a-z0-9-]/g, '')
        : '';
    return URLS.CATEGORY_PAGE(categorySlug);
}

/**
 * Creates a default channel link
 * @returns {PlatformAuthorLink} Default author link
 */
function createDefaultChannelLink() {
    return new PlatformAuthorLink(
        new PlatformID(PLATFORM, "", config.id),
        "Curiosity Stream",
        "",
        ""
    );
}

/**
 * Creates a category author link
 * @param {string} categorySlug - Category slug
 * @param {string} categoryName - Category name
 * @returns {PlatformAuthorLink} Author link
 */
function createCategoryAuthorLink(categorySlug, categoryName) {
    // Find the category object in state to get its image
    const category = Object.values(state.categories)
        .find(cat => cat.name === categorySlug);
    
    // Get the image URL from the category, or use empty string if not found
    const imageUrl = category?.image_url || '';
    
    return new PlatformAuthorLink(
        new PlatformID(PLATFORM, categorySlug, config.id),
        categoryName,
        constructCategoryUrl(categoryName),
        imageUrl
    );
}

/**
 * Extract unique collection IDs from a list of videos
 * @param {Array} videos - Array of video items
 * @returns {Array} Array of unique collection IDs
 */
function extractCollectionIds(videos) {
    if (!videos || !Array.isArray(videos)) {
        return [];
    }
    
    // Filter out videos with collection_id and get unique IDs
    return [...new Set(
        videos
            .filter(video => video && video.collection_id)
            .map(video => video.collection_id)
    )];
}

/**
 * Maps content items to the appropriate platform object type
 * @param {Object} item - Content item
 * @param {Object} collectionsMap - Map of collection ID to collection data
 * @param {Object} processingInfo - Additional processing information
 * @returns {PlatformVideo|PlatformPlaylist} Mapped content
 */
function mapContent(item, collectionsMap = {}, processingInfo = {}) {
    if (item.obj_type === 'collection') {
        return createPlaylistFromCollection(item);
    } else {
        // For media items, check if it's part of a collection
        const collection = item.collection_id ? collectionsMap[item.collection_id] : null;
        
        // If part of a category page and not overridden by collection, use category as author
        if (!collection && processingInfo.categorySlug) {
            const video = createVideoFromMedia(item);
            video.author = createCategoryAuthorLink(processingInfo.categorySlug, processingInfo.categoryName);
            return video;
        }
        
        return createVideoFromMedia(item, collection);
    }
}

// ====================== DATA FETCHING ======================

/**
 * Fetches and processes content data with collection information
 * @param {Object} params - Request parameters
 * @returns {Object} Processed data
 */
function fetchContentDataWithCollections(params) {
    const { 
        url, 
        page = 1,
        processingInfo = {}
    } = params;
    
    const fullUrl = `${url}&page=${page}`;
    const res = http.GET(fullUrl, DEFAULT_HEADERS, state.has_subscription);
    
    if (!res.isOk) {
        return {
            items: [],
            hasMore: false,
            nextPage: page
        };
    }
    
    try {
        const body = JSON.parse(res.body);
        
        if (!body.data || !Array.isArray(body.data)) {
            return {
                items: [],
                hasMore: false,
                nextPage: page
            };
        }
        
        // Extract collection IDs and fetch collection details
        const collectionIds = extractCollectionIds(body.data);
        const collectionMap = fetchCollectionDetailsBatch(collectionIds);
        
        // Map items with collection data
        let items = body.data.map(item => mapContent(item, collectionMap, processingInfo));
        
        return {
            items,
            hasMore: body.paginator && body.paginator.current_page < body.paginator.total_pages,
            nextPage: page + 1,
            body
        };
    } catch (error) {
        log(`Error fetching content data: ${error.message}`);
        return {
            items: [],
            hasMore: false,
            nextPage: page
        };
    }
}

/**
 * Fetches and processes search data
 * @param {string} query - Search query
 * @param {number} page - Page number
 * @returns {Object} Processed search data
 */
function fetchSearchData(query, page = 1) {
    const searchBody = {
        query: query,
        hitsPerPage: 50,
        page: page - 1, // API is 0-indexed but our pagers use 1-indexed
        filters: {
            duration: ""
        }
    };
    
    const headers = {
        "Content-Type": "application/json"
    };
    
    try {
        const res = http.POST(URLS.SEARCH, JSON.stringify(searchBody), headers);
        
        if (!res.isOk) {
            return {
                items: [],
                hasMore: false,
                nextPage: page
            };
        }
        
        const body = JSON.parse(res.body);
        
        // Process search results from the hits array
        const hits = (body.hits || []).map(item => {
            // Create a standardized item that works with our mapContent function
            return {
                id: item.id || item.title_id,
                title: item.title,
                title_id: item.title_id || item.id,
                image_medium: item.image_medium,
                image_large: item.image_large,
                description: item.description || item.short_description,
                short_description: item.short_description,
                duration: item.duration,
                view_count: -1, // Not provided in search results
                year_produced: item.year_produced,
                primary_category: item.primary_category,
                obj_type: item.obj_type || "media",
                media_count: item.episodes_count,
                status: item.status, // Ensure status field is passed along for preview detection
                collection_id: item.collection_id // Include collection ID if present
            };
        });
        
        // Extract collection IDs and fetch collection details
        const collectionIds = extractCollectionIds(hits);
        const collectionMap = fetchCollectionDetailsBatch(collectionIds);
        
        // Map items with collection data
        const items = hits.map(item => mapContent(item, collectionMap));
        
        return {
            items,
            hasMore: body.page < body.nbPages - 1, // Account for 0-indexing
            nextPage: page + 1,
            body
        };
    } catch (error) {
        log(`Error in search: ${error.message}`);
        return {
            items: [],
            hasMore: false,
            nextPage: page
        };
    }
}

/**
 * Extracts a script URL from HTML
 * @param {string} html - The HTML content to search
 * @returns {string|null} - The extracted script URL or null if not found
 */
function extractScriptUrl(html) {
    try {
        const match = REGEX.SCRIPT_URL.exec(html);
        return match ? match[1] : null;
    } catch (error) {
        log("Error extracting script URL:", error);
        return null;
    }
}

/**
 * Parses JavaScript object notation that's not valid JSON
 * 
 * @param {string} jsObjectString - The JavaScript object string to parse
 * @returns {object|array} - The parsed JavaScript object or array
 */
function parseJsObject(jsObjectString) {
    try {
        // First attempt: try direct JSON.parse (in case it's already valid JSON)
        try {
            return JSON.parse(jsObjectString);
        } catch (e) {
            // Not valid JSON, continue with conversion
        }
        
        // Try to sanitize the JavaScript object string
        const sanitized = jsObjectString
            .replace(/undefined/g, 'null')
            .replace(/\bfunction\s*\([^)]*\)\s*{[^}]*}/g, 'null');
            
        // Use Function to parse the JavaScript object string
        return Function('return ' + sanitized)();
    } catch (error) {
        throw new ScriptException("Could not parse the JavaScript object string");
    }
}

/**
 * Gets content ID from URL
 * @param {string} url - Content URL
 * @returns {string|null} Content ID if found
 */
function getContentIdFromUrl(url) {
    const match = url.match(REGEX.CONTENT_URL);
    return match?.[1] ?? null;
}

/**
 * Combines regular content with free videos
 * @param {Array} regularContent - Regular content
 * @param {boolean} hasSubscription - Has subscription flag
 * @returns {Array} Combined content
 */
function getCombinedContent(regularContent, hasSubscription) {
    if (hasSubscription) return regularContent;

    const freeVideos = getFreeVideos();
    const freeVideoPlatformVideos = freeVideos.map(createVideoFromFreeVideo);
    return [...freeVideoPlatformVideos, ...regularContent];
}

// ====================== PAGER CLASSES ======================

/**
 * Custom pager for home page content
 */
class CuriosityStreamHomePager extends VideoPager {
    constructor(initialResults, hasMore, context) {
        super(initialResults, hasMore, context);
    }
    
    nextPage() {
        const result = fetchContentDataWithCollections({
            url: `${URLS.API_V1}/media?collections=true&limit=${API.VIDEOS_PER_PAGE}`,
            page: this.context.page + 1
        });
        
        this.results = result.items;
        this.context.page = result.nextPage - 1;
        this.hasMore = result.hasMore;
        return this;
    }
}

/**
 * Custom pager for category content
 */
class CategoryContentPager extends VideoPager {
    constructor(initialResults, hasMore, context) {
        super(initialResults, hasMore, context);
    }
    
    nextPage() {
        const categorySlug = this.context.category.name;
        const categoryName = this.context.category.label;
        
        const result = fetchContentDataWithCollections({
            url: `${URLS.API_V1}/media?filterBy=category&term=${categorySlug}&collections=true&limit=${API.VIDEOS_PER_PAGE}&sort=creation_date_desc`,
            page: this.context.page + 1,
            processingInfo: {
                categorySlug,
                categoryName: categoryName
            }
        });
        
        this.results = result.items;
        this.context.page = result.nextPage - 1;
        this.hasMore = result.hasMore;
        return this;
    }
}

/**
 * Custom pager for search results
 */
class SearchPager extends VideoPager {
    constructor(initialResults, hasMore, context) {
        super(initialResults, hasMore, context);
    }
    
    nextPage() {
        const result = fetchSearchData(this.context.query, this.context.page + 1);
        
        this.results = result.items;
        this.context.page = result.nextPage - 1;
        this.hasMore = result.hasMore;
        return this;
    }
}

/**
 * Custom pager for recommendations
 */
class RecommendationsPager extends VideoPager {
    constructor(initialResults, hasMore, context) {
        super(initialResults, hasMore, context);
    }
    
    nextPage() {
        const result = fetchContentDataWithCollections({
            url: `${URLS.API_V1}/media?filterBy=recommended&term=${this.context.videoId}&limit=${API.VIDEOS_PER_PAGE}&collections=true`,
            page: this.context.page + 1
        });
        
        this.results = result.items;
        this.context.page = result.nextPage - 1;
        this.hasMore = result.hasMore;
        return this;
    }
}

// ====================== PLAYBACK TRACKING ======================

/**
 * Custom playback tracker for Curiosity Stream
 */
class CuriosityStreamPlaybackTracker extends PlaybackTracker {
    /**
     * Constructor for playback tracker
     * @param {string} contentId - Content ID
     * @param {number} interval - Report interval in seconds
     */
    constructor(contentId, interval = 15) {
        super(interval * 1000); // Convert to milliseconds
        this.contentId = contentId;
        this.lastReportedPosition = 0;
        this.duration = 0;
        this.isStarted = false;
    }
    
    /**
     * Called when playback starts
     * @param {number} position - Start position in seconds
     */
    onInit(position) {
        this.isStarted = true;
        
        // Fetch media details to get duration if needed
        try {
            if (!this.duration) {
                const details = fetchMediaDetails(this.contentId, true);
                if (details && details.media) {
                    this.duration = details.media.duration || 0;
                }
            }
        } catch (error) {
            log(`Error fetching media details for tracker: ${error.message}`);
        }
        
        // Report initial position
        this.reportProgress(position);
    }
    
    /**
     * Called periodically during playback
     * @param {number} position - Current position in seconds
     * @param {boolean} isPlaying - Whether playback is active
     */
    onProgress(position, isPlaying) {
        if (!isPlaying || !this.isStarted) {
            return;
        }
        
        // Only report if position has changed significantly
        if (Math.abs(position - this.lastReportedPosition) >= API.MIN_TRACKING_DURATION) {
            this.reportProgress(position);
        }
    }
    
    /**
     * Called when playback concludes
     */
    onConcluded() {
        // Report completed viewing
        this.reportProgress(this.duration);
    }
    
    /**
     * Reports playback progress to the API
     * @param {number} position - Current position in seconds
     * @param {boolean} completed - Whether playback is completed
     */
    reportProgress(position) {
        try {
            if (!bridge.isLoggedIn()) {
                return;
            }
            
            // Ensure position is within valid range
            position = Math.max(0, position);
            if (this.duration > 0) {
                position = Math.min(position, this.duration);
            }
            
            // Prepare data for the progress report
            const progressData = {
                media_id: parseInt(this.contentId),
                progress_in_seconds: position
            };
            
            // Send progress to API
            const res = http.POST(
                URLS.PLAYBACK_PROGRESS,
                JSON.stringify(progressData),
                DEFAULT_HEADERS,
                true
            );
            
            if (res.isOk) {
                this.lastReportedPosition = position;
                log(`Progress reported for ${this.contentId}: ${position}s`);
            } else {
                log(`Failed to report progress: ${res.code}`);
            }
        } catch (error) {
            log(`Error reporting progress: ${error.message}`);
        }
    }
}

log('LOADED');