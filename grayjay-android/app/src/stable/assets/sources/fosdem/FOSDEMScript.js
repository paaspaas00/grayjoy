// ==========================================================================
// Constants
// ==========================================================================

const PLATFORM = "FOSDEM";
const BASE_URL = "https://video.fosdem.org";
const SCHEDULE_BASE_URL = "https://fosdem.org";
const SEARCH_API_BASE_URL = "https://api.opensource-tv.com";
const CHANNEL_THUMBNAIL_URL = "https://plugins.grayjay.app/FOSDEM/FOSDEMIcon.png";
const EDITIONS_TTL_MS = 1 * 60 * 60 * 1000; // Cache editions for 1 hour
const CHANNEL_DESCRIPTION = "FOSDEM is a free event for software developers to meet, share ideas and collaborate. Every year, thousands of developers of free and open source software from all over the world gather at the event in Brussels.\n\nContent by FOSDEM (fosdem.org), licensed under CC-BY-2.0-BE.";

const REQUEST_HEADERS = {
    "Content-Type": "application/json",
    "User-Agent": `grayjay.app/${bridge.buildPlatform}/${bridge.buildVersion}`
};

const ITEMS_PER_PAGE = 20;
const CHANNEL_SEARCH_MAX_FETCHES = 5;
const HEIGHT_1080 = 1080;
const IS_ANDROID = bridge.buildPlatform === "android";


const FILTER_TRACK = "track";
const FILTER_ROOM = "room";
const FILTER_TYPE = "type";
const FILTER_SPEAKER = "speaker";
const FILTER_YEAR = "year";
const CATEGORY_EDITION = "edition";
const LINK_TYPE_FEEDBACK = "feedback";

Type.Feed.Playlists = "Playlists";

// ==========================================================================
// Regex Patterns
// ==========================================================================

/**
 * Matches FOSDEM event page URLs on fosdem.org or archive.fosdem.org.
 * Captures: Group 1 = year, Group 2 = event slug
 * @example https://fosdem.org/2025/schedule/event/fosdem-2025-6712-welcome-to-fosdem-2025/
 * @example https://archive.fosdem.org/2025/schedule/event/fosdem-2025-6712-welcome-to-fosdem-2025/
 */
const CONTENT_DETAILS_URL_REGEX = /(?:archive\.)?fosdem\.org\/(\d{4})\/schedule\/event\/([^\/]+)/;

/**
 * Matches direct video file URLs on video.fosdem.org.
 * Captures: Group 1 = year, Group 2 = room directory, Group 3 = slug (without extension)
 * @example https://video.fosdem.org/2025/janson/fosdem-2025-6712-welcome-to-fosdem-2025.mp4
 */
const VIDEO_FILE_URL_REGEX = /video\.fosdem\.org\/(\d{4})\/([^\/]+)\/([^\/]+?)(?:\.av1)?\.(?:mp4|webm|vtt)/;

/**
 * Matches the FOSDEM channel URL.
 * @example https://video.fosdem.org or https://video.fosdem.org/
 */
const CHANNEL_URL_REGEX = /^https?:\/\/video\.fosdem\.org\/?$/;

/**
 * Matches a FOSDEM speaker profile URL, with or without year prefix.
 * Captures: Group 1 = speaker slug
 * @example https://fosdem.org/schedule/speaker/michiel_leenaars/
 * @example https://fosdem.org/2026/schedule/speaker/michiel_leenaars/
 */
const SPEAKER_URL_REGEX = /fosdem\.org\/(?:\d{4}\/)?schedule\/speaker\/([^\/]+)/;

/**
 * Matches FOSDEM edition (year) playlist URLs, with optional query params for filtered playlists.
 * Captures: Group 1 = year, Group 2 = optional query string (e.g., "?track=Python")
 * @example https://video.fosdem.org/2025/
 * @example https://video.fosdem.org/2026/?track=Python
 * @example https://video.fosdem.org/2026/?room=Janus
 * @example https://video.fosdem.org/2026/?type=devroom
 */
const PLAYLIST_URL_REGEX = /video\.fosdem\.org\/(\d{4})\/?(\?[^#]*)?$/;

/**
 * Query string marker that flags a /{year}/ URL as a year-channel rather than the
 * year-overview playlist (which uses the same path with no query).
 */
const YEAR_CHANNEL_QUERY = "channel=1";


// ==========================================================================
// State
// ==========================================================================

let config = {};
let settings = {};
let state = {
    editions: { years: [], banners: {}, fetchedAt: 0 }, // Cache of available editions
    categories: {} // Cache of categories per year: { [year]: { totalEvents, tracks, rooms, types } }
};

// ==========================================================================
// Source Functions
// ==========================================================================

/**
 * Initializes the plugin with config, settings, and restored state.
 * @param {SourceConfig} conf - Plugin configuration
 * @param {Object} sett - Plugin settings
 * @param {string} savedStateStr - Previously saved state as JSON string
 */
source.enable = function (conf, sett, savedStateStr) {
    config = conf ?? {};
    settings = sett ?? {};

    if (savedStateStr) {
        try {
            const parsed = JSON.parse(savedStateStr);
            if (parsed.editions) state.editions = parsed.editions;
            if (parsed.categories) state.categories = parsed.categories;
        } catch (e) {
            log("Failed to parse saved state: " + e.message);
        }
    }

    fetchEditions();
};

/**
 * Serializes plugin state for persistence across sessions.
 * @returns {string} JSON string of the current state
 */
source.saveState = function () {
    return JSON.stringify({
        editions: state.editions,
        categories: state.categories
    });
};

/**
 * Returns the home feed with the most recent FOSDEM talks that have video recordings.
 * @returns {FOSDEMApiPager}
 */
source.getHome = function () {
    const years = getAvailableYears();
    if (years.length === 0) {
        return new FOSDEMApiPager([], false, [], 0, 1, 0);
    }

    for (let i = 0; i < years.length; i++) {
        const pager = fetchApiEventsPage(years[i], 1, years, i);
        if (pager.results.length > 0) return pager;
    }

    return new FOSDEMApiPager([], false, years, 0, 1, 0);
};

/**
 * Checks if the given URL is the FOSDEM channel URL.
 * @param {string} url
 * @returns {boolean}
 */
source.isChannelUrl = function (url) {
    return CHANNEL_URL_REGEX.test(url)
        || !!getYearFromChannelUrl(url)
        || !!getSpeakerSlugFromUrl(url);
};

/**
 * Returns the FOSDEM channel information.
 * @param {string} url - Channel URL
 * @returns {PlatformChannel}
 */
source.getChannel = function (url) {
    const speakerSlug = getSpeakerSlugFromUrl(url);
    if (speakerSlug) {
        return getSpeakerChannel(speakerSlug);
    }

    const year = getYearFromChannelUrl(url);
    if (year) {
        return createYearChannel(year);
    }

    return createMainChannel();
};

/**
 * Returns the capabilities of the FOSDEM channel.
 * @returns {Object}
 */
source.getChannelCapabilities = function () {
    return {
        types: [Type.Feed.Videos, Type.Feed.Playlists],
        sorts: [Type.Order.Chronological]
    };
};


/**
 * Returns channel contents - either videos or playlists depending on the requested type.
 * @param {string} url - Channel URL
 * @param {string} type - Content type (Videos or Playlists)
 * @param {string} order - Sort order
 * @param {Object} filters - Content filters
 * @returns {VideoPager|PlaylistPager}
 */
source.getChannelContents = function (url, type, order, filters) {
    const speakerSlug = getSpeakerSlugFromUrl(url);
    if (speakerSlug) {
        if (type === Type.Feed.Playlists) {
            return getSpeakerPlaylists(speakerSlug);
        }
        return getSpeakerEvents(speakerSlug, 1);
    }

    const year = getYearFromChannelUrl(url);
    if (year) {
        if (type === Type.Feed.Playlists) {
            return getYearChannelPlaylists(year);
        }
        return fetchApiEventsPage(year, 1, [year], 0);
    }

    if (type === Type.Feed.Playlists) {
        return source.getChannelPlaylists(url);
    }
    return source.getHome();
};

/**
 * Returns playlists (editions) for the FOSDEM channel.
 * @param {string} url - Channel URL
 * @returns {FOSDEMPlaylistPager}
 */
source.getChannelPlaylists = function (url) {
    const speakerSlug = getSpeakerSlugFromUrl(url);
    if (speakerSlug) {
        return getSpeakerPlaylists(speakerSlug);
    }
    const year = getYearFromChannelUrl(url);
    if (year) {
        return getYearChannelPlaylists(year);
    }
    return getChannelPlaylists();
};

/**
 * Checks if the given URL is a FOSDEM edition playlist URL.
 * @param {string} url
 * @returns {boolean}
 */
source.isPlaylistUrl = function (url) {
    return PLAYLIST_URL_REGEX.test(url) && !getYearFromChannelUrl(url);
};

/**
 * Returns playlist details for a specific FOSDEM edition (year).
 * @param {string} url - Playlist URL (e.g., https://video.fosdem.org/2025/)
 * @returns {PlatformPlaylistDetails}
 */
source.getPlaylist = function (url) {
    const parsed = parsePlaylistUrl(url);
    if (!parsed) {
        throw new ScriptException("Invalid playlist URL: " + url);
    }

    const year = parsed.year;
    const firstPage = fetchFilteredEventsPage(year, 1, {
        track: parsed.track, room: parsed.room, type: parsed.type, speaker: parsed.speaker
    });

    let name = "FOSDEM " + year;
    let playlistId = "fosdem-" + year;
    if (parsed.speaker) { playlistId += "-" + FILTER_SPEAKER + "-" + parsed.speaker; }
    if (parsed.track) { name += " - Track " + parsed.track; playlistId += "-" + FILTER_TRACK + "-" + parsed.track; }
    else if (parsed.room) { name += " - Room " + parsed.room; playlistId += "-" + FILTER_ROOM + "-" + parsed.room; }
    else if (parsed.type) { name += " - Type " + parsed.type; playlistId += "-" + FILTER_TYPE + "-" + parsed.type; }

    return new PlatformPlaylistDetails({
        id: fosdemId(playlistId),
        name: name,
        author: firstPage.speakerAuthor || fosdemAuthor(),
        url: url,
        thumbnail: getYearThumbnailUrl(year),
        videoCount: firstPage.totalResults,
        contents: firstPage
    });
};

/**
 * Checks if the given URL points to a FOSDEM talk (event page or video file).
 * @param {string} url
 * @returns {boolean}
 */
source.isContentDetailsUrl = function (url) {
    return CONTENT_DETAILS_URL_REGEX.test(url) || VIDEO_FILE_URL_REGEX.test(url);
};

/**
 * Returns detailed content information for a specific FOSDEM talk,
 * including video sources and subtitles.
 * @param {string} url - Event page URL or direct video URL
 * @returns {PlatformVideoDetails}
 */
source.getContentDetails = function (url) {
    const parsed = parseContentUrl(url);
    if (!parsed) {
        throw new ScriptException("Invalid content URL: " + url);
    }

    const event = getEventFromApi(parsed.year, parsed.slug);
    if (!event) {
        throw new ScriptException("Event not found: " + parsed.slug);
    }

    if (!event.url) event.url = url;

    if (!event.mp4Url && !event.webmUrl) {
        throw new ScriptException("No video sources available for this event");
    }

    const author = resolveSpeakerAuthor([event], parsed.speakerSlug);
    return createPlatformVideoDetailsFromApiResult(event, author);
};

/**
 * Returns content recommendations for a FOSDEM talk.
 * Shows other talks from the same track, then from the same year.
 * @param {string} url - Content URL
 * @returns {FOSDEMApiPager}
 */
source.getContentRecommendations = function (url) {
    const parsed = parseContentUrl(url);
    if (!parsed) {
        return new FOSDEMApiPager([], false, [], 0, 1, 0);
    }

    const recUrl = SEARCH_API_BASE_URL + "/events/" + parsed.year + "/" + encodeURIComponent(parsed.slug)
        + "/recommendations?pageSize=" + ITEMS_PER_PAGE;
    const resp = httpGET(recUrl);
    if (!resp.isOk || !resp.body || !resp.body.items) {
        return new FOSDEMApiPager([], false, [], 0, 1, 0);
    }

    // Wrapper prevents .map() from passing (item, index, array) — index would be used as the author param
    const videos = resp.body.items.map(function (item) { return createPlatformVideoDetailsFromApiResult(item); });
    return new FOSDEMApiPager(videos, false, [], 0, 1, 0);
};

// ==========================================================================
// Search
// ==========================================================================

source.getSearchCapabilities = function () {
    return new ResultCapabilities(
        [Type.Feed.Videos, Type.Feed.Playlists],
        [],
        buildSearchFilterGroups()
    );
};

source.search = function (query, type, order, filters) {
    return searchApi(query, 1, filters);
};

source.searchPlaylists = function (query) {
    return searchPlaylistsApi(query, 1);
};

source.searchChannels = function (query) {
    const trimmed = (query || "").trim();
    if (trimmed.length === 0) {
        return searchSpeakersApi(query, 1);
    }

    const fosdemChannels = buildMatchingFosdemChannels(trimmed);
    const speakerPager = searchSpeakersApi(trimmed, 1);
    const speakerResults = speakerPager.results || [];
    const combinedResults = fosdemChannels.concat(speakerResults);
    const firstPage = combinedResults.slice(0, ITEMS_PER_PAGE);
    const pendingItems = combinedResults.slice(ITEMS_PER_PAGE);

    return new FOSDEMChannelSearchPager(firstPage, pendingItems.length > 0 || !!speakerPager.hasMore, {
        query: trimmed,
        totalResults: (speakerPager.totalResults || 0) + fosdemChannels.length,
        pendingItems: pendingItems,
        nextSpeakerPage: 2,
        speakerHasMore: !!speakerPager.hasMore
    });
};

source.getSearchChannelContentsCapabilities = function () {
    return new ResultCapabilities(
        [Type.Feed.Videos],
        [],
        buildSearchFilterGroups({ excludeYear: true })
    );
};

source.searchChannelContents = function (channelUrl, query, type, order, filters) {
    const year = getYearFromChannelUrl(channelUrl);
    if (year) {
        const merged = Object.assign({}, filters || {});
        merged[FILTER_YEAR] = [String(year)];
        return searchApi(query, 1, merged);
    }
    return searchApi(query, 1, filters);
};

source.searchSuggestions = function (query) {
    if (!query || query.trim().length === 0) return [];

    const response = httpGET(SEARCH_API_BASE_URL + "/autocomplete?q=" + encodeURIComponent(query) + "&limit=10");

    if (!response.isOk || !response.body || !response.body.suggestions) return [];

    return response.body.suggestions.map(function (s) { return s.text; });
};

/**
 * Fetches a single event from the search API by year and slug.
 * @param {number|string} year - FOSDEM edition year
 * @param {string} slug - Event slug
 * @returns {Object|null} Event object or null if not found
 */
function getEventFromApi(year, slug) {
    const url = SEARCH_API_BASE_URL + "/events/" + year + "/" + encodeURIComponent(slug);
    const resp = httpGET(url);
    if (!resp.isOk || !resp.body) return null;
    return resp.body;
}

/**
 * Fetches a page of events from the search API for a given year.
 * Returns an API pager with cross-year pagination support.
 * @param {number} year - FOSDEM edition year
 * @param {number} page - Page number (1-based)
 * @param {number[]} years - All available years for cross-year pagination
 * @param {number} yearIndex - Current index in years array
 * @returns {FOSDEMApiPager}
 */
function fetchApiEventsPage(year, page, years, yearIndex) {
    const url = SEARCH_API_BASE_URL + "/events?year=" + year
        + "&page=" + page + "&pageSize=" + ITEMS_PER_PAGE;
    const resp = httpGET(url);
    if (!resp.isOk || !resp.body || !resp.body.items) {
        return new FOSDEMApiPager([], false, years, yearIndex, page, 0);
    }
    const data = resp.body;
    // Wrapper prevents .map() from passing (item, index, array) — index would be used as the author param
    const videos = data.items.map(function (item) { return createPlatformVideoDetailsFromApiResult(item); });
    const hasMore = page < data.totalPages || yearIndex + 1 < years.length;
    return new FOSDEMApiPager(videos, hasMore, years, yearIndex, page, data.totalPages);
}

/**
 * Fetches a single page of filtered events from the API.
 * @param {number|string} year - FOSDEM edition year
 * @param {number} page - Page number (1-based)
 * @param {Object} filters - Filter options { track, room, type, speaker }
 * @returns {FOSDEMPlaylistContentsPager}
 */
function fetchFilteredEventsPage(year, page, filters) {
    filters = filters || {};
    const { track, room, type, speaker } = filters;
    const sortAsc = settings.playlistSort === 0 || settings.playlistSort === "0";
    let url = SEARCH_API_BASE_URL + "/events?year=" + year
        + "&page=" + page + "&pageSize=" + ITEMS_PER_PAGE
        + "&sort=" + (sortAsc ? "asc" : "desc");
    if (track) url += "&" + FILTER_TRACK + "=" + encodeURIComponent(track);
    if (room) url += "&" + FILTER_ROOM + "=" + encodeURIComponent(room);
    if (type) url += "&" + FILTER_TYPE + "=" + encodeURIComponent(type);
    if (speaker) url += "&" + FILTER_SPEAKER + "=" + encodeURIComponent(speaker);

    const resp = httpGET(url);
    if (!resp.isOk || !resp.body || !resp.body.items) {
        return new FOSDEMPlaylistContentsPager([], false, year, page, 0, 0, filters);
    }
    const data = resp.body;
    const author = speaker ? resolveSpeakerAuthor(data.items, speaker) : null;
    const videos = data.items.map(function (item) {
        if (speaker) item.url = appendSpeakerHint(item.url, speaker);
        return createPlatformVideoDetailsFromApiResult(item, author);
    });
    const hasMore = page < data.totalPages;
    return new FOSDEMPlaylistContentsPager(videos, hasMore, year, page, data.totalPages, data.totalResults, filters, author);
}

/**
 * Fetches category lists (tracks, rooms, types) for a year, with in-memory caching.
 * @param {number|string} year - FOSDEM edition year
 * @returns {Object} Object with tracks, rooms, types arrays
 */
function fetchCategoriesForYear(year) {
    const yearStr = String(year);
    if (state.categories[yearStr]) return state.categories[yearStr];

    const resp = httpGET(SEARCH_API_BASE_URL + "/categories?year=" + year);
    if (!resp.isOk || !resp.body) {
        return { totalEvents: 0, tracks: [], rooms: [], types: [] };
    }
    
    const data = resp.body;
    const result = {
        totalEvents: data.totalEvents || 0,
        tracks: data.tracks || [],
        rooms: data.rooms || [],
        types: data.types || []
    };

    state.categories[yearStr] = result;
    return result;
}

/**
 * Fetches global filter lists (all tracks, rooms, types across all years), with in-memory caching.
 * @returns {Object} Object with tracks, rooms, types string arrays
 */
function fetchSearchFilters() {
    const resp = httpGET(SEARCH_API_BASE_URL + "/categories");
    if (!resp.isOk || !resp.body) {
        return { tracks: [], rooms: [], types: [] };
    }
    const data = resp.body;
    return {
        tracks: (data.tracks || []).map(function (t) { return t.name; }),
        rooms: (data.rooms || []).map(function (r) { return r.name; }),
        types: (data.types || []).map(function (t) { return t.name; })
    };
}

/**
 * Builds search filter groups for ResultCapabilities.
 * @param {Object} [opts]
 * @param {boolean} [opts.excludeYear=false] - Omit the Year filter group (used by year-channel search where year is implicit)
 * @returns {FilterGroup[]}
 */
function buildSearchFilterGroups(opts) {
    const filters = fetchSearchFilters();
    const groups = [];

    if (!opts || !opts.excludeYear) {
        const years = getAvailableYears();
        groups.push(new FilterGroup("Year",
            years.map(function (y) { return new FilterCapability(String(y), String(y), String(y)); }),
            false, FILTER_YEAR));
    }

    groups.push(new FilterGroup("Type",
        filters.types.map(function (t) { return new FilterCapability(t, t, t); }),
        false, FILTER_TYPE));

    if (filters.tracks.length > 0) {
        groups.push(new FilterGroup("Track",
            filters.tracks.map(function (t) { return new FilterCapability(t, t, t); }),
            false, FILTER_TRACK));
    }
    if (filters.rooms.length > 0) {
        groups.push(new FilterGroup("Room",
            filters.rooms.map(function (r) { return new FilterCapability(r, r, r); }),
            false, FILTER_ROOM));
    }

    return groups;
}

/**
 * Creates a PlatformPlaylist for a filtered view (track, room, or type).
 * @param {number} year - FOSDEM edition year
 * @param {string} filterType - Filter type: "track", "room", or "type"
 * @param {string} filterValue - Filter value (e.g., "Python", "Janus")
 * @param {number} [videoCount=-1] - Number of videos in the playlist
 * @returns {PlatformPlaylist}
 */
function createFilteredPlaylist(year, filterType, filterValue, videoCount) {
    return new PlatformPlaylist({
        id: fosdemId("fosdem-" + year + "-" + filterType + "-" + filterValue),
        name: "FOSDEM " + year + " - " + capitalize(filterType) + " " + filterValue,
        author: fosdemAuthor(),
        thumbnail: getYearThumbnailUrl(year),
        url: BASE_URL + "/" + year + "/?" + filterType + "=" + encodeURIComponent(filterValue),
        videoCount: videoCount != null ? videoCount : -1
    });
}

/**
 * Builds all playlists for a year: year playlist + track/room/type playlists.
 * @param {number} year - FOSDEM edition year
 * @returns {PlatformPlaylist[]} Array of playlists
 */
function buildPlaylistsForYear(year) {
    const playlists = [];
    const categories = fetchCategoriesForYear(year);

    playlists.push(createPlaylistForYear(year, categories.totalEvents));

    for (let i = 0; i < categories.tracks.length; i++) {
        const t = categories.tracks[i];
        playlists.push(createFilteredPlaylist(year, FILTER_TRACK, t.name, t.count));
    }
    for (let i = 0; i < categories.rooms.length; i++) {
        const r = categories.rooms[i];
        playlists.push(createFilteredPlaylist(year, FILTER_ROOM, r.name, r.count));
    }
    for (let i = 0; i < categories.types.length; i++) {
        const tp = categories.types[i];
        playlists.push(createFilteredPlaylist(year, FILTER_TYPE, tp.name, tp.count));
    }

    return playlists;
}

/**
 * Calls the search API and returns a pager.
 * @param {string} query - Search query
 * @param {number} page - Page number (1-based)
 * @param {Object} [filters] - Optional filter values keyed by filter group id
 * @returns {FOSDEMSearchPager}
 */
function searchApi(query, page, filters) {
    if (!query || query.trim().length === 0) {
        return new FOSDEMSearchPager([], false, query, 1, 0, filters);
    }

    let url = SEARCH_API_BASE_URL + "/search?q=" + encodeURIComponent(query)
        + "&page=" + page + "&pageSize=" + ITEMS_PER_PAGE;
    if (filters) {
        if (filters[FILTER_YEAR] && filters[FILTER_YEAR].length > 0) url += "&" + FILTER_YEAR + "=" + encodeURIComponent(filters[FILTER_YEAR][0]);
        if (filters[FILTER_TRACK] && filters[FILTER_TRACK].length > 0) url += "&" + FILTER_TRACK + "=" + encodeURIComponent(filters[FILTER_TRACK][0]);
        if (filters[FILTER_ROOM] && filters[FILTER_ROOM].length > 0) url += "&" + FILTER_ROOM + "=" + encodeURIComponent(filters[FILTER_ROOM][0]);
        if (filters[FILTER_TYPE] && filters[FILTER_TYPE].length > 0) url += "&" + FILTER_TYPE + "=" + encodeURIComponent(filters[FILTER_TYPE][0]);
    }

    const response = httpGET(url);

    if (!response.isOk || !response.body) {
        return new FOSDEMSearchPager([], false, query, page, 0, filters);
    }

    const data = response.body;
    // Wrapper prevents .map() from passing (item, index, array) — index would be used as the author param
    const videos = (data.items || []).map(function (item) { return createPlatformVideoDetailsFromApiResult(item); });
    const hasMore = page * ITEMS_PER_PAGE < data.totalResults;

    return new FOSDEMSearchPager(videos, hasMore, query, page, data.totalResults, filters);
}

/**
 * Calls the category search API and returns a playlist pager.
 * @param {string} query - Search query
 * @param {number} page - Page number (1-based)
 * @returns {FOSDEMSearchPlaylistPager}
 */
function searchPlaylistsApi(query, page) {
    if (!query || query.trim().length === 0) {
        return new FOSDEMSearchPlaylistPager([], false, query, 1, 0);
    }

    const url = SEARCH_API_BASE_URL + "/categories/search?q=" + encodeURIComponent(query)
        + "&page=" + page + "&pageSize=" + ITEMS_PER_PAGE;

    const response = httpGET(url);

    if (!response.isOk || !response.body) {
        return new FOSDEMSearchPlaylistPager([], false, query, page, 0);
    }

    const data = response.body;
    const playlists = (data.items || []).map(function (item) {
        if (item.category === CATEGORY_EDITION) {
            return createPlaylistForYear(item.year, item.count);
        }
        return createFilteredPlaylist(item.year, item.category, item.name, item.count);
    });
    const hasMore = page * ITEMS_PER_PAGE < data.totalResults;

    return new FOSDEMSearchPlaylistPager(playlists, hasMore, query, page, data.totalResults);
}

/**
 * Calls the speaker search API and returns a channel pager.
 * @param {string} query - Search query
 * @param {number} page - Page number (1-based)
 * @returns {FOSDEMSpeakerSearchPager}
 */
function searchSpeakersApi(query, page) {
    if (!query || query.trim().length === 0) {
        return new FOSDEMSpeakerSearchPager([], false, query, 1, 0);
    }

    const url = SEARCH_API_BASE_URL + "/speakers/search?q=" + encodeURIComponent(query)
        + "&page=" + page + "&pageSize=" + ITEMS_PER_PAGE;

    const response = httpGET(url);

    if (!response.isOk || !response.body) {
        return new FOSDEMSpeakerSearchPager([], false, query, page, 0);
    }

    const data = response.body;
    const channels = (data.items || []).map(function (item) { return createPlatformChannelFromSpeaker(item); });
    const hasMore = page * ITEMS_PER_PAGE < data.totalResults;

    return new FOSDEMSpeakerSearchPager(channels, hasMore, query, page, data.totalResults);
}

// ==========================================================================
// Pager Classes
// ==========================================================================

/**
 * Pager for API-based event browsing with server-side pagination and cross-year support.
 * @extends VideoPager
 */
class FOSDEMApiPager extends VideoPager {
    constructor(results, hasMore, years, yearIndex, page, totalPages) {
        super(results, hasMore);
        this.years = years;
        this.yearIndex = yearIndex;
        this.page = page;
        this.totalPages = totalPages;
    }

    nextPage() {
        if (this.page < this.totalPages) {
            return fetchApiEventsPage(this.years[this.yearIndex], this.page + 1, this.years, this.yearIndex);
        }
        if (this.yearIndex + 1 < this.years.length) {
            return fetchApiEventsPage(this.years[this.yearIndex + 1], 1, this.years, this.yearIndex + 1);
        }
        return new FOSDEMApiPager([], false, this.years, this.yearIndex, this.page, this.totalPages);
    }
}

/**
 * Pager for search API results with server-side pagination.
 * @extends VideoPager
 */
class FOSDEMSearchPager extends VideoPager {
    constructor(results, hasMore, query, page, totalResults, filters) {
        super(results, hasMore);
        this.query = query;
        this.page = page;
        this.totalResults = totalResults;
        this.filters = filters;
    }

    nextPage() {
        return searchApi(this.query, this.page + 1, this.filters);
    }
}

/**
 * Pager for playlist search results with server-side pagination.
 * @extends PlaylistPager
 */
class FOSDEMSearchPlaylistPager extends PlaylistPager {
    constructor(results, hasMore, query, page, totalResults) {
        super(results, hasMore);
        this.query = query;
        this.page = page;
        this.totalResults = totalResults;
    }

    nextPage() {
        return searchPlaylistsApi(this.query, this.page + 1);
    }
}

/**
 * Pager for speaker search results with server-side pagination.
 * @extends ChannelPager
 */
class FOSDEMSpeakerSearchPager extends ChannelPager {
    constructor(results, hasMore, query, page, totalResults) {
        super(results, hasMore);
        this.query = query;
        this.page = page;
        this.totalResults = totalResults;
    }

    nextPage() {
        return searchSpeakersApi(this.query, this.page + 1);
    }
}

/**
 * Pager for channel search results that prepends synthetic FOSDEM channels
 * while keeping pagination consistent with speaker search results.
 * @extends ChannelPager
 */
class FOSDEMChannelSearchPager extends ChannelPager {
    constructor(results, hasMore, opts) {
        super(results, hasMore);
        this.query = opts.query;
        this.totalResults = opts.totalResults;
        this.pendingItems = opts.pendingItems || [];
        this.nextSpeakerPage = opts.nextSpeakerPage;
        this.speakerHasMore = opts.speakerHasMore;
    }

    nextPage() {
        let items = this.pendingItems;
        let nextSpeakerPage = this.nextSpeakerPage;
        let speakerHasMore = this.speakerHasMore;

        // Bounded loop guards against an API that keeps reporting hasMore with non-empty pages.
        for (let i = 0; i < CHANNEL_SEARCH_MAX_FETCHES && items.length < ITEMS_PER_PAGE && speakerHasMore; i++) {
            const speakerPager = searchSpeakersApi(this.query, nextSpeakerPage);
            const speakerResults = speakerPager.results || [];
            nextSpeakerPage += 1;
            speakerHasMore = !!speakerPager.hasMore;
            if (speakerResults.length === 0) {
                speakerHasMore = false;
                break;
            }
            items = items.concat(speakerResults);
        }

        const page = items.slice(0, ITEMS_PER_PAGE);
        const remaining = items.slice(ITEMS_PER_PAGE);
        return new FOSDEMChannelSearchPager(page, remaining.length > 0 || speakerHasMore, {
            query: this.query,
            totalResults: this.totalResults,
            pendingItems: remaining,
            nextSpeakerPage: nextSpeakerPage,
            speakerHasMore: speakerHasMore
        });
    }
}

/**
 * Pager for FOSDEM playlists with multi-phase pagination.
 * Yields playlists per year: year playlist, then track/room/type playlists.
 * @extends PlaylistPager
 */
class FOSDEMPlaylistPager extends PlaylistPager {
    /**
     * @param {PlatformPlaylist[]} results - Current page of playlist results
     * @param {boolean} hasMore - Whether more pages are available
     * @param {number[]} years - Full list of available years
     * @param {number} yearIndex - Current year index
     * @param {PlatformPlaylist[]} pendingItems - Pre-built playlists not yet returned
     */
    constructor(results, hasMore, years, yearIndex, pendingItems) {
        super(results, hasMore);
        this.years = years;
        this.yearIndex = yearIndex;
        this.pendingItems = pendingItems;
    }

    nextPage() {
        if (this.pendingItems.length > 0) {
            const page = this.pendingItems.slice(0, ITEMS_PER_PAGE);
            const remaining = this.pendingItems.slice(ITEMS_PER_PAGE);
            const hasMore = remaining.length > 0 || this.yearIndex + 1 < this.years.length;
            return new FOSDEMPlaylistPager(page, hasMore, this.years, this.yearIndex, remaining);
        }

        const nextYearIndex = this.yearIndex + 1;
        if (nextYearIndex >= this.years.length) {
            return new FOSDEMPlaylistPager([], false, this.years, nextYearIndex, []);
        }

        const items = buildPlaylistsForYear(this.years[nextYearIndex]);
        const page = items.slice(0, ITEMS_PER_PAGE);
        const remaining = items.slice(ITEMS_PER_PAGE);
        const hasMore = remaining.length > 0 || nextYearIndex + 1 < this.years.length;
        return new FOSDEMPlaylistPager(page, hasMore, this.years, nextYearIndex, remaining);
    }
}

/**
 * Pager for speaker events with server-side pagination.
 * @extends VideoPager
 */
class FOSDEMSpeakerEventsPager extends VideoPager {
    constructor(results, hasMore, slug, page, totalPages) {
        super(results, hasMore);
        this.slug = slug;
        this.page = page;
        this.totalPages = totalPages;
    }

    nextPage() {
        return getSpeakerEvents(this.slug, this.page + 1);
    }
}

/**
 * Pager for filtered playlist contents with server-side pagination.
 * @extends VideoPager
 */
class FOSDEMPlaylistContentsPager extends VideoPager {
    constructor(results, hasMore, year, page, totalPages, totalResults, filters, speakerAuthor) {
        super(results, hasMore);
        this.year = year;
        this.page = page;
        this.totalPages = totalPages;
        this.totalResults = totalResults;
        this.filters = filters || {};
        this.speakerAuthor = speakerAuthor || null;
    }

    nextPage() {
        return fetchFilteredEventsPage(this.year, this.page + 1, this.filters);
    }
}

/**
 * Pager for a pre-built list of playlists with client-side pagination.
 * @extends PlaylistPager
 */
class FOSDEMStaticPlaylistPager extends PlaylistPager {
    constructor(results, hasMore, pendingItems) {
        super(results, hasMore);
        this.pendingItems = pendingItems;
    }

    nextPage() {
        const page = this.pendingItems.slice(0, ITEMS_PER_PAGE);
        const remaining = this.pendingItems.slice(ITEMS_PER_PAGE);
        return new FOSDEMStaticPlaylistPager(page, remaining.length > 0, remaining);
    }
}

// ==========================================================================
// Helper Functions
// ==========================================================================

/**
 * Performs an HTTP GET request with default headers and JSON parsing.
 * @param {string} url - The URL to fetch
 * @returns {Object} Response object with isOk, code, body, and headers
 */
function httpGET(url) {
    try {
        const response = http.GET(url, REQUEST_HEADERS, false);

        if (!response.isOk) {
            log("HTTP request failed: " + url + " (code: " + response.code + ")");
            return response;
        }

        try {
            const parsed = JSON.parse(response.body);
            return {
                code: response.code,
                isOk: response.isOk,
                body: parsed,
                headers: response.headers
            };
        } catch (e) {
            log("Failed to parse JSON from: " + url + " - " + e.message);
            return { isOk: false, code: response.code, body: null, headers: response.headers };
        }
    } catch (e) {
        log("HTTP request error: " + url + " - " + e.message);
        return { isOk: false, code: 0, body: null, headers: {} };
    }
}

/**
 * Safely extracts URL search parameters.
 * @param {string} url - Absolute URL
 * @returns {URLSearchParams|null} Parsed search parameters, or null for invalid URLs
 */
function getSearchParams(url) {
    try {
        return new URL(url).searchParams;
    } catch (e) {
        return null;
    }
}

/**
 * Safely extracts one URL search parameter.
 * @param {string} url - Absolute URL
 * @param {string} name - Search parameter name
 * @returns {string|null} Parameter value, or null when missing or invalid
 */
function getSearchParam(url, name) {
    const params = getSearchParams(url);
    return params ? params.get(name) : null;
}

/**
 * Fetches available editions from the API and caches in state (1h TTL).
 * Skips the fetch if a valid cache exists.
 */
function fetchEditions() {
    if (state.editions.years.length > 0 && (Date.now() - state.editions.fetchedAt < EDITIONS_TTL_MS)) {
        return;
    }

    const resp = httpGET(SEARCH_API_BASE_URL + "/editions");
    if (!resp.isOk || !resp.body || !resp.body.editions) {
        log("Failed to fetch editions from API");
        return;
    }

    const editions = resp.body.editions;
    const years = editions.map(function (e) { return e.year; });
    const banners = {};
    editions.forEach(function (e) { if (e.bannerUrl) banners[e.year] = e.bannerUrl; });
    state.editions = { years: years, banners: banners, fetchedAt: Date.now() };
}

/**
 * Returns available FOSDEM edition years, newest first.
 * @returns {number[]}
 */
function getAvailableYears() {
    fetchEditions();
    return state.editions.years;
}

/**
 * Returns the channel banner URL using the latest available year's thumbnail.
 * @returns {string} Banner URL
 */
function getChannelBannerUrl() {
    const years = getAvailableYears();
    return getYearThumbnailUrl(years.length > 0 ? years[0] : 2026);
}

/**
 * Returns the channel thumbnail URL using the latest available year's icon.
 * @returns {string} Thumbnail URL
 */
function getChannelThumbnailUrl() {
    const years = getAvailableYears();
    if (years.length === 0) {
        return CHANNEL_THUMBNAIL_URL;
    }
    return SCHEDULE_BASE_URL + "/" + years[0] + "/apple-touch-icon.png";
}

/**
 * Returns the year thumbnail URL (1280x720).
 * @param {number|string} year - FOSDEM edition year
 * @returns {string} Thumbnail URL
 */
function getYearThumbnailUrl(year) {
    fetchEditions();
    return state.editions.banners[year] || "";
}

/**
 * Parses an ISO 8601 date string to a Unix timestamp in seconds.
 * @param {string} dateStr - ISO 8601 date string (e.g., "2025-02-01T09:30:00+01:00")
 * @returns {number} Unix timestamp in seconds, or 0 if parsing fails
 */
function parseEventDate(dateStr) {
    if (!dateStr) return 0;
    const ts = new Date(dateStr).getTime();
    return isNaN(ts) ? 0 : Math.floor(ts / 1000);
}

/**
 * Parses a content URL (event page or video file) to extract year and slug.
 * @param {string} url - Content URL
 * @returns {Object|null} Object with year, slug, and optionally room properties
 */
function parseContentUrl(url) {
    let match = url.match(CONTENT_DETAILS_URL_REGEX);
    if (match) {
        const result = { year: match[1], slug: match[2] };
        const speakerSlug = getSearchParam(url, "speakerSlug");
        if (speakerSlug) result.speakerSlug = speakerSlug;
        return result;
    }

    match = url.match(VIDEO_FILE_URL_REGEX);
    if (match) {
        return { year: match[1], slug: match[3] };
    }

    return null;
}

/**
 * Extracts and decodes a speaker slug from a channel URL.
 * Handles percent-encoded speaker slugs from links embedded in descriptions.
 * @param {string} url - Channel URL
 * @returns {string|null} Decoded speaker slug or null if the URL is not a speaker URL
 */
function getSpeakerSlugFromUrl(url) {
    const match = url ? url.match(SPEAKER_URL_REGEX) : null;
    if (!match) return null;

    try {
        return decodeURIComponent(match[1]);
    } catch (e) {
        // Malformed percent-encoding; use raw slug as-is
        return match[1];
    }
}

/**
 * Extracts the year from a year-channel URL (requires channel=1 query param).
 * @param {string} url - Channel URL
 * @returns {number|null} Year if URL is a year-channel URL, null otherwise
 */
function getYearFromChannelUrl(url) {
    if (!url) return null;
    const m = url.match(PLAYLIST_URL_REGEX);
    if (!m || !m[2] || m[2].indexOf("channel=") < 0) return null;
    if (getSearchParam(url, "channel") !== "1") return null;
    return parseInt(m[1], 10);
}

/**
 * Parses a playlist URL into year and optional filter parameters.
 * @param {string} url - Playlist URL
 * @returns {Object|null} Object with year and optional track, room, type properties
 */
function parsePlaylistUrl(url) {
    const match = url.match(PLAYLIST_URL_REGEX);
    if (!match) return null;

    const result = { year: parseInt(match[1]) };
    const params = getSearchParams(url);
    if (!params) return null;
    if (params.has(FILTER_TRACK)) result.track = params.get(FILTER_TRACK);
    if (params.has(FILTER_ROOM)) result.room = params.get(FILTER_ROOM);
    if (params.has(FILTER_TYPE)) result.type = params.get(FILTER_TYPE);
    if (params.has(FILTER_SPEAKER)) result.speaker = params.get(FILTER_SPEAKER);

    return result;
}

/**
 * Builds video source objects from an event's video links.
 * Adds sources based on plugin settings (MP4, WebM).
 * @param {Object} event - Parsed event object
 * @param {number} durationSeconds - Duration in seconds
 * @returns {VideoUrlSource[]} Array of video sources
 */
function buildVideoSources(event, durationSeconds) {
    const sources = [];
    const wantMp4 = settings.enableMp4 !== false;
    const wantWebm = settings.enableWebm !== false;
    const priorityWebm = settings.prioritySource === 1 || settings.prioritySource === "1";

    if (event.mp4Url && wantMp4) {
        const mp4Source = new VideoUrlSource({
            name: "MP4",
            url: event.mp4Url,
            width: 1920, height: 1080,
            container: "video/mp4",
            codec: "h264",
            duration: durationSeconds
        });
        mp4Source.priority = !priorityWebm;
        sources.push(mp4Source);
    }

    if (event.webmUrl && wantWebm) {
        const isAv1 = event.webmUrl.endsWith(".av1.webm");
        const webmSource = new VideoUrlSource({
            name: isAv1 ? "AV1/WebM" : "WebM",
            url: event.webmUrl,
            width: 1920,
            height: 1080,
            container: "video/webm",
            codec: isAv1 ? "av1" : "vp9",
            duration: durationSeconds
        });
        webmSource.priority = priorityWebm;
        sources.push(webmSource);
    }

    return sources;
}

/**
 * Builds subtitle objects from an event's subtitles list.
 * @param {Object} event - Parsed event object
 * @returns {Object[]} Array of subtitle objects
 */
function buildSubtitles(event) {
    if (!event.subtitles || event.subtitles.length === 0) return [];
    return event.subtitles
        .slice()
        .sort(function (a, b) {
            return (b.priority || 0) - (a.priority || 0);
        })
        .map(function (sub) {
            return {
                name: sub.label || sub.language,
                url: sub.url,
                format: "text/vtt",
                language: sub.language
            };
        });
}

/**
 * Builds a description string from event metadata including speakers, track, and abstract.
 * @param {Object} event - Parsed event object
 * @returns {string} Formatted description
 */
function buildDescription(event) {
    const parts = [];

    if (event.speakers && event.speakers.length > 0) {
        const speakerNames = event.speakers.map(function (s) {
            if (IS_ANDROID && s.slug) {
                return '<a href="' + SCHEDULE_BASE_URL + '/schedule/speaker/' + encodeURIComponent(s.slug) + '/">' + escapeHtml(s.name) + '</a>';
            }
            return escapeHtml(s.name);
        });
        parts.push("Speakers: " + speakerNames.join(", "));
    }
    if (event.track) parts.push(formatMetadataLink("Track", FILTER_TRACK, event.track, event.year));
    if (event.type) parts.push(formatMetadataLink("Type", FILTER_TYPE, event.type, event.year));
    if (event.room) parts.push(formatMetadataLink("Room", FILTER_ROOM, event.room, event.year));
    if (event.links && event.links.length > 0) {
        const nonVideoLinks = [];
        let feedbackLink = null;
        for (let i = 0; i < event.links.length; i++) {
            const link = event.links[i];
            if (link.type === LINK_TYPE_FEEDBACK) {
                feedbackLink = link;
                continue;
            }
            if (IS_ANDROID) {
                nonVideoLinks.push('<a href="' + escapeHtml(link.href) + '">' + escapeHtml(link.text || link.type) + '</a>');
            } else {
                nonVideoLinks.push(escapeHtml(link.text || link.type));
            }
        }
        if (nonVideoLinks.length > 0) {
            parts.push("Links: " + nonVideoLinks.join(" | "));
        }
        if (feedbackLink) {
            if (IS_ANDROID) {
                parts.push('<a href="' + escapeHtml(feedbackLink.href) + '">Submit Feedback</a>');
            } else {
                parts.push("Submit Feedback");
            }
        }
    }

    if (event.abstract) {
        parts.push("");
        parts.push(event.abstract);
    }
    if (event.description && event.description !== event.abstract) {
        parts.push("");
        parts.push(event.description);
    }

    parts.push("");
    parts.push("Content by FOSDEM (fosdem.org), licensed under CC-BY-2.0-BE.");

    return parts.join("\n");
}

/**
 * Escapes HTML special characters to prevent injection.
 * @param {string} text - Raw text
 * @returns {string} HTML-safe text
 */
function escapeHtml(text) {
    if (!text) return "";
    return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

/**
 * Capitalizes the first letter of a string.
 * @param {string} str - Input string
 * @returns {string} String with first letter uppercased
 */
function capitalize(str) {
    if (!str) return "";
    return str.charAt(0).toUpperCase() + str.slice(1);
}

/**
 * Formats a metadata label with an optional link for Android.
 * @param {string} label - Display label (e.g., "Track", "Room")
 * @param {string} filterType - Filter query parameter name
 * @param {string} value - The metadata value
 * @param {number|string} year - FOSDEM edition year
 * @returns {string} Formatted label string, with HTML link on Android
 */
function formatMetadataLink(label, filterType, value, year) {
    if (IS_ANDROID && year) {
        return label + ': <a href="' + BASE_URL + '/' + year + '/?' + filterType + '=' + encodeURIComponent(value) + '">' + escapeHtml(value) + '</a>';
    }
    return label + ": " + escapeHtml(value);
}

/**
 * Appends a speakerSlug query parameter to a URL as a context hint.
 * @param {string} url - Original event URL
 * @param {string} slug - Speaker slug to embed
 * @returns {string} URL with speakerSlug parameter appended
 */
function appendSpeakerHint(url, slug) {
    if (!url || !slug) return url;
    try {
        const u = new URL(url);
        u.searchParams.set("speakerSlug", slug);
        return u.toString();
    } catch (e) {
        return url;
    }
}

/**
 * Finds a speaker's display name by slug from a list of events.
 * @param {Object[]} events - Array of event objects with speakers arrays
 * @param {string} slug - Speaker slug to search for
 * @returns {string|null} Speaker name or null if not found
 */
function findSpeakerName(events, slug) {
    for (let i = 0; i < events.length; i++) {
        const speakers = events[i].speakers;
        if (!speakers) continue;
        for (let j = 0; j < speakers.length; j++) {
            if (speakers[j].slug === slug) return speakers[j].name;
        }
    }
    return null;
}

/**
 * Builds a PlatformID under the FOSDEM platform/config.
 * @param {string} suffix - Stable per-entity ID suffix
 * @returns {PlatformID}
 */
function fosdemId(suffix) {
    return new PlatformID(PLATFORM, suffix, config.id);
}

/**
 * Creates a PlatformAuthorLink for the FOSDEM channel.
 * @returns {PlatformAuthorLink}
 */
function fosdemAuthor() {
    return new PlatformAuthorLink(
        fosdemId("fosdem"),
        PLATFORM,
        BASE_URL,
        getChannelThumbnailUrl()
    );
}

/**
 * Returns the synthetic FOSDEM channels that match a channel-search query.
 * @param {string} query - Channel search query
 * @returns {PlatformChannel[]}
 */
function buildMatchingFosdemChannels(query) {
    const lower = query.toLowerCase();
    const channels = [];

    if (PLATFORM.toLowerCase().includes(lower)) {
        channels.push(createMainChannel());
    }

    const years = getAvailableYears();
    for (let i = 0; i < years.length; i++) {
        const name = PLATFORM + " " + years[i];
        if (name.toLowerCase().includes(lower)) {
            channels.push(createYearChannel(years[i]));
        }
    }

    return channels;
}

/**
 * Creates a PlatformAuthorLink for a specific FOSDEM edition (year channel).
 * @param {number|string} year - FOSDEM edition year
 * @returns {PlatformAuthorLink}
 */
function fosdemYearAuthor(year) {
    return new PlatformAuthorLink(
        fosdemId("fosdem-channel-" + year),
        PLATFORM + " " + year,
        buildYearChannelUrl(year),
        getYearThumbnailUrl(year) || getChannelThumbnailUrl()
    );
}

/** Builds the canonical year-channel URL. */
function buildYearChannelUrl(year) {
    return BASE_URL + "/" + year + "/?" + YEAR_CHANNEL_QUERY;
}

/**
 * Creates a PlatformAuthorLink for a speaker channel.
 * @param {string} slug - Speaker slug
 * @param {string} name - Speaker display name
 * @returns {PlatformAuthorLink}
 */
function buildSpeakerAuthor(slug, name) {
    return new PlatformAuthorLink(
        fosdemId(FILTER_SPEAKER + "-" + slug),
        name,
        SCHEDULE_BASE_URL + "/schedule/speaker/" + encodeURIComponent(slug) + "/",
        getChannelThumbnailUrl()
    );
}

/**
 * Resolves a speaker author from a list of events by slug.
 * @param {Object[]} events - Array of event objects with speakers arrays
 * @param {string} slug - Speaker slug to search for
 * @returns {PlatformAuthorLink|null} Speaker author or null if not found
 */
function resolveSpeakerAuthor(events, slug) {
    if (!slug || !events || events.length === 0) return null;
    const name = findSpeakerName(events, slug);
    return name ? buildSpeakerAuthor(slug, name) : null;
}

/**
 * Returns a formatted speaker suffix for video titles (e.g., " - John Doe, Jane Smith").
 * @param {Object} event - Event object with speakers array
 * @returns {string} Speaker suffix or empty string if no speakers
 */
function getSpeakerSuffix(event) {
    if (!event.speakers || event.speakers.length === 0) return "";
    return " - " + event.speakers.map(function (s) { return s.name; }).join(", ");
}

/**
 * Builds the shared field set for PlatformVideo and PlatformVideoDetails.
 * @param {Object} item - Search API result item
 * @param {PlatformAuthorLink} [author] - Optional author override
 * @returns {Object} Fields object
 */
function buildBaseVideoFields(item, author) {
    const defaultAuthor = item.year ? fosdemYearAuthor(item.year) : fosdemAuthor();
    return {
        id: fosdemId(String(item.id)),
        name: item.title + getSpeakerSuffix(item),
        thumbnails: item.thumbnailUrl ? new Thumbnails([new Thumbnail(item.thumbnailUrl, HEIGHT_1080)]) : new Thumbnails([]),
        author: author || defaultAuthor,
        uploadDate: parseEventDate(item.date),
        duration: item.duration || -1,
        viewCount: 0,
        url: item.url,
        isLive: false
    };
}

/**
 * Creates a PlatformVideoDetails from a search API result item when video URLs are available.
 * Falls back to PlatformVideo if no video sources can be built.
 * @param {Object} item - Search API result item (full Event model)
 * @param {PlatformAuthorLink} [author] - Optional author override, defaults to fosdemAuthor()
 * @returns {PlatformVideoDetails|PlatformVideo}
 */
function createPlatformVideoDetailsFromApiResult(item, author) {
    const sources = buildVideoSources(item, item.duration || -1);
    if (sources.length === 0) {
        return new PlatformVideo(buildBaseVideoFields(item, author));
    }

    const fields = buildBaseVideoFields(item, author);
    fields.description = buildDescription(item);
    fields.video = new VideoSourceDescriptor(sources);
    fields.subtitles = buildSubtitles(item);

    const result = new PlatformVideoDetails(fields);
    result.getContentRecommendations = function () {
        return source.getContentRecommendations(item.url);
    };
    return result;
}

/**
 * Creates a PlatformPlaylist for a given year.
 * @param {number} year - FOSDEM edition year
 * @param {number} [videoCount=-1] - Number of videos in the playlist
 * @returns {PlatformPlaylist}
 */
function createPlaylistForYear(year, videoCount) {
    return new PlatformPlaylist({
        id: fosdemId("fosdem-" + year),
        name: "FOSDEM " + year,
        author: fosdemAuthor(),
        thumbnail: getYearThumbnailUrl(year),
        url: BASE_URL + "/" + year + "/",
        videoCount: videoCount != null ? videoCount : -1
    });
}

/**
 * Creates the main FOSDEM PlatformChannel (cross-year).
 * @returns {PlatformChannel}
 */
function createMainChannel() {
    return new PlatformChannel({
        id: fosdemId("fosdem"),
        name: PLATFORM,
        thumbnail: getChannelThumbnailUrl(),
        banner: getChannelBannerUrl(),
        subscribers: 0,
        description: CHANNEL_DESCRIPTION,
        url: BASE_URL,
        links: {
            "Website": "https://fosdem.org",
            "Mastodon": "https://fosstodon.org/@fosdem",
            "Bluesky": "https://bsky.app/profile/fosdem.org",
            "LinkedIn": "https://www.linkedin.com/company/fosdem/",
            "Matrix": "https://matrix.to/#/#fosdem:fosdem.org"
        }
    });
}

/**
 * Creates a PlatformChannel for a specific FOSDEM edition (year).
 * @param {number} year - FOSDEM edition year
 * @returns {PlatformChannel}
 */
function createYearChannel(year) {
    const yearThumb = getYearThumbnailUrl(year);
    return new PlatformChannel({
        id: fosdemId("fosdem-channel-" + year),
        name: PLATFORM + " " + year,
        thumbnail: yearThumb || getChannelThumbnailUrl(),
        banner: yearThumb || getChannelBannerUrl(),
        subscribers: 0,
        description: "FOSDEM " + year + " talks.\n\n" + CHANNEL_DESCRIPTION,
        url: buildYearChannelUrl(year),
        links: { "Website": SCHEDULE_BASE_URL + "/" + year + "/" }
    });
}

/**
 * Returns a paginated list of playlists for a single FOSDEM edition.
 * @param {number} year - FOSDEM edition year
 * @returns {FOSDEMStaticPlaylistPager}
 */
function getYearChannelPlaylists(year) {
    const items = buildPlaylistsForYear(year);
    const firstPage = items.slice(0, ITEMS_PER_PAGE);
    const remaining = items.slice(ITEMS_PER_PAGE);
    return new FOSDEMStaticPlaylistPager(firstPage, remaining.length > 0, remaining);
}

/**
 * Creates a PlatformChannel from a speaker API object.
 * @param {Object} speaker - Speaker object with slug, name, biography
 * @returns {PlatformChannel}
 */
function createPlatformChannelFromSpeaker(speaker) {
    return new PlatformChannel({
        id: fosdemId(FILTER_SPEAKER + "-" + speaker.slug),
        name: speaker.name,
        thumbnail: getChannelThumbnailUrl(),
        banner: getChannelBannerUrl(),
        subscribers: 0,
        description: (speaker.biography || "") + "\n\nContent by FOSDEM (fosdem.org), licensed under CC-BY-2.0-BE.",
        url: SCHEDULE_BASE_URL + "/schedule/speaker/" + encodeURIComponent(speaker.slug) + "/",
        links: {}
    });
}

/**
 * Returns a PlatformChannel for a FOSDEM speaker.
 * @param {string} slug - Speaker slug
 * @returns {PlatformChannel}
 */
function getSpeakerChannel(slug) {
    const resp = httpGET(SEARCH_API_BASE_URL + "/speakers/" + encodeURIComponent(slug));
    if (!resp.isOk || !resp.body) {
        throw new ScriptException("Speaker not found: " + slug);
    }
    return createPlatformChannelFromSpeaker(resp.body);
}

/**
 * Fetches paginated events for a speaker and returns a pager.
 * @param {string} slug - Speaker slug
 * @param {number} page - Page number (1-based)
 * @returns {FOSDEMSpeakerEventsPager}
 */
function getSpeakerEvents(slug, page) {
    const url = SEARCH_API_BASE_URL + "/speakers/" + encodeURIComponent(slug)
        + "/events?page=" + page + "&pageSize=" + ITEMS_PER_PAGE;
    const resp = httpGET(url);
    if (!resp.isOk || !resp.body || !resp.body.items) {
        return new FOSDEMSpeakerEventsPager([], false, slug, 1, 0);
    }

    const data = resp.body;
    const author = data.speakerName ? buildSpeakerAuthor(data.speakerSlug || slug, data.speakerName) : null;
    const videos = data.items.map(function (item) {
        item.url = appendSpeakerHint(item.url, slug);
        return createPlatformVideoDetailsFromApiResult(item, author);
    });
    const hasMore = page < data.totalPages;
    return new FOSDEMSpeakerEventsPager(videos, hasMore, slug, page, data.totalPages);
}

/**
 * Builds playlists for a speaker channel: by year, track, room, and type.
 * Uses the /speakers/{slug}/categories API to get aggregated data in a single call.
 * @param {string} slug - Speaker slug
 * @returns {FOSDEMStaticPlaylistPager}
 */
function getSpeakerPlaylists(slug) {
    const resp = httpGET(SEARCH_API_BASE_URL + "/speakers/" + encodeURIComponent(slug) + "/categories");
    if (!resp.isOk || !resp.body || !resp.body.years || resp.body.years.length === 0) {
        return new FOSDEMStaticPlaylistPager([], false, []);
    }

    const data = resp.body;
    const speakerParam = "&" + FILTER_SPEAKER + "=" + encodeURIComponent(slug);
    const author = buildSpeakerAuthor(slug, data.speakerName || slug);

    const playlists = [];

    // Year playlists (already sorted newest first by API)
    for (let i = 0; i < data.years.length; i++) {
        const y = data.years[i];
        playlists.push(new PlatformPlaylist({
            id: fosdemId("fosdem-" + y.name + "-" + FILTER_SPEAKER + "-" + slug),
            name: "FOSDEM " + y.name,
            author: author,
            thumbnail: getYearThumbnailUrl(parseInt(y.name)),
            url: BASE_URL + "/" + y.name + "/?" + FILTER_SPEAKER + "=" + encodeURIComponent(slug),
            videoCount: y.count
        }));
    }

    // Category playlists (tracks, rooms, types - already sorted by year desc, name asc by API)
    const categoryGroups = [
        { items: data.tracks, filter: FILTER_TRACK },
        { items: data.rooms, filter: FILTER_ROOM },
        { items: data.types, filter: FILTER_TYPE }
    ];
    for (let g = 0; g < categoryGroups.length; g++) {
        const group = categoryGroups[g];
        for (let i = 0; i < group.items.length; i++) {
            const item = group.items[i];
            playlists.push(new PlatformPlaylist({
                id: fosdemId("fosdem-" + item.year + "-" + FILTER_SPEAKER + "-" + slug + "-" + group.filter + "-" + item.name),
                name: "FOSDEM " + item.year + " - " + capitalize(group.filter) + " " + item.name,
                author: author,
                thumbnail: getYearThumbnailUrl(item.year),
                url: BASE_URL + "/" + item.year + "/?" + group.filter + "=" + encodeURIComponent(item.name) + speakerParam,
                videoCount: item.count
            }));
        }
    }

    const firstPage = playlists.slice(0, ITEMS_PER_PAGE);
    const remaining = playlists.slice(ITEMS_PER_PAGE);
    const hasMore = remaining.length > 0;
    return new FOSDEMStaticPlaylistPager(firstPage, hasMore, remaining);
}

/**
 * Returns the channel playlists (year + track/room/type playlists per year).
 * @returns {FOSDEMPlaylistPager}
 */
function getChannelPlaylists() {
    const years = getAvailableYears();
    if (years.length === 0) {
        return new FOSDEMPlaylistPager([], false, [], 0, []);
    }

    const items = buildPlaylistsForYear(years[0]);
    const firstPage = items.slice(0, ITEMS_PER_PAGE);
    const remaining = items.slice(ITEMS_PER_PAGE);
    const hasMore = remaining.length > 0 || years.length > 1;
    return new FOSDEMPlaylistPager(firstPage, hasMore, years, 0, remaining);
}

log("loaded");
