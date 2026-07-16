/**
 * RadioBrowser Plugin for Grayjay
 * 
 * This plugin enables browsing, searching, and listening to radio stations worldwide.
 * It interfaces with the radio-browser.info API to provide access to thousands of stations.
 * API DOCS: https://api.radio-browser.info/ https://all.api.radio-browser.info/
 */

// ---------------------- Constants ----------------------
const PLATFORM = "RadioBrowser";
const DEFAULT_LIMIT = 20;
const RELATED_DEFAULT_LIMIT = 50;
const DEFAULT_HEADERS = {
    'Content-Type': 'application/json',
    'User-Agent': `grayjay.app/${bridge.buildVersion}`
};

const REGEX = {
    COUNTRY_URL: /^radiobrowser:\/\/country\/([a-zA-Z0-9]+)$/,
    WEB_COUNTRY_URL: /^https?:\/\/(?:www\.)?radio-browser\.info\/search\?.*countrycode=([a-zA-Z0-9]+)/,
    STATION_URL: /^radiobrowser:\/\/station\/([a-zA-Z0-9-]+)$/,
    WEB_STATION_URL: /^https?:\/\/(?:www\.)?radio-browser\.info\/history\/([a-zA-Z0-9-]+)$/
};

// URLs constants
const URLS = {
    BASE: "https://radio-browser.info",
    WEB_CONTRIBUTE_NEW_STATION: "https://radio-browser.info/add",
    WEB_COUNTRY_SEARCH: "https://www.radio-browser.info/search?page=1&order=clickcount&reverse=true&hidebroken=true&countrycode={countrycode}",
    API_SERVERS: "https://all.api.radio-browser.info/json/servers",
    DEFAULT_API_BASE: "https://fi1.api.radio-browser.info/json",
    FLAGS_CDN: "https://flagcdn.com",
    PLUGIN_THUMBNAIL: "https://plugins.grayjay.app/RadioBrowser/media/DefaultThumbnail.png",
    STATION_HISTORY: "https://www.radio-browser.info/history/{uuid}",
    MAPS: {
        OPENSTREETMAP: "https://www.openstreetmap.org/?mlat={lat}&mlon={lon}&zoom=14",
        GOOGLE_MAPS: "https://www.google.com/maps?q={lat},{lon}",
        BING_MAPS: "https://www.bing.com/maps?cp={lat}~{lon}&lvl=14",
        WAZE: "https://www.waze.com/ul?ll={lat}%2C{lon}&navigate=yes",
        APPLE_MAPS: "https://maps.apple.com/?ll={lat},{lon}"
    }
};

// Known API servers as fallback
const FALLBACK_SERVERS = [
    "all.api.radio-browser.info",
    "de1.api.radio-browser.info",
    "de2.api.radio-browser.info",
    "api.radiodb.fr"
];

// Plugin configuration and state
let _config = {};
let _settings = {
    thumbnailMode: 0 // Default: 0 (Plugin icon)
};

let state = {
    countries: {},
    stationCache: {},
    api_base_url: URLS.DEFAULT_API_BASE,
    api_servers: [], // Will store all available API servers
    serverIndex: 0 // Track current server index for rotation
};

// Thumbnail mode constants
const THUMBNAIL_MODE = {
    SOUNDBAR: 0,
    PLATFORM_LOGO: 1,
    COUNTRY_FLAG: 2,
    STATION_FAVICON: 3
};

// ====================== PLUGIN ENTRY POINTS ======================

/**
 * Initialize the plugin with configuration
 * @param {Object} conf - Plugin configuration object
 * @param {Object} settings - User settings object
 * @param {string} saveStateStr - Saved state as JSON string from previous session
 */
source.enable = function (conf, settings, saveStateStr) {
    _config = conf ?? {};
    _settings = settings ?? {};

    if(_settings.thumbnailMode === undefined) {
        _settings.thumbnailMode = 0; // Default to Plugin icon
    }

    try {
        if (saveStateStr) {
            state = JSON.parse(saveStateStr);
            
            // Make sure we have the api_servers property even if loading from saved state
            if (!state.api_servers || !Array.isArray(state.api_servers)) {
                state.api_servers = [];
            }
            
            // Ensure serverIndex exists
            if (state.serverIndex === undefined || state.serverIndex === null) {
                state.serverIndex = 0;
            }
        } else {
            // Fresh start - fetch server list
            const serverList = fetchApiServers();
            if (serverList) {
                state.api_base_url = "https://" + serverList[0] + "/json";
                state.api_servers = serverList;
            } else {
                state.api_servers = [...FALLBACK_SERVERS];
            }
        }
        
        // If we have no servers (or failed to fetch them), use fallback servers
        if (!state.api_servers || state.api_servers.length === 0) {
            LogIfTesting("No servers available, using fallback servers");
            state.api_servers = [...FALLBACK_SERVERS];
        } else {
            // Ensure the server list contains only unique entries
            state.api_servers = [...new Set(state.api_servers)];
        }
        
    } catch (ex) {
        LogIfTesting('Failed to parse saveState or fetch API servers: ' + ex);
    }
    
    LogIfTesting("Available API servers: " + JSON.stringify(state.api_servers));
};

/**
 * Save the plugin state
 * @returns {string} JSON stringified state object for persistence
 */
source.saveState = function() {
    return JSON.stringify(state);
};

/**
 * Get home page content - returns popular stations, filtered by country if specified
 * @returns {VideoPager} Paged results for home page
 */
source.getHome = function() {
    // Default search parameters
    const params = {
        limit: DEFAULT_LIMIT,
        hidebroken: "true",
        order: "clickcount",
        reverse: "true"
    };

    // Check if a specific country is selected in settings
    if (_settings.homeCountry !== undefined) {
        // Get the country selection from the dropdown
        const selectedOption = _config.settings.find(s => s.variable === "homeCountry")?.options[_settings.homeCountry];
        
        if (selectedOption) {
            // Parse the country code and name from the option format (e.g., "US - United States")
            const parts = selectedOption.split(' - ');
            const countryCode = parts[0].trim();
            const countryName = parts.length > 1 ? parts[1].split(' (')[0].trim() : "Unknown";
            
            // Only add country filter if a specific country is selected (not "all")
            if (countryCode && countryCode !== "all") {
                params.countrycode = countryCode;
                LogIfTesting(`Filtering home page by country: ${countryCode} (${countryName})`);
            }
        }
    }
    
    // Return filtered stations for home page
    return searchStations(params);
};

// Define popularity sort option
Type.Order.Popularity = "Popularity";

/**
 * Define search capabilities
 * @returns {Object} Search capabilities
 */
source.getSearchCapabilities = () => {
    return {
        types: [Type.Feed.Mixed],
        sorts: [
            Type.Order.Chronological,
            Type.Order.Popularity
        ],
        filters: []
    };
};

/**
 * Search for radio stations
 * @param {string} query Search query
 * @param {string} type Content type
 * @param {string} order Sort order
 * @param {Object} filters Search filters
 * @returns {VideoPager} Paged search results
 */
source.search = function(query, type, order, filters) {
    const orderParam = order === Type.Order.Popularity ? "clickcount" : "name";
    
    return searchStations({
        name: query,
        limit: DEFAULT_LIMIT,
        hidebroken: "true",
        order: orderParam,
        reverse: "true"
    });
};

/**
 * Check if a URL is a radio browser country channel
 * @param {string} url URL to check
 * @returns {boolean} True if URL is a country channel
 */
source.isChannelUrl = function(url) {
    return REGEX.COUNTRY_URL.test(url) || REGEX.WEB_COUNTRY_URL.test(url);
};

/**
 * Get a country channel by URL
 * @param {string} url Country channel URL
 * @returns {PlatformChannel} Country channel
 */
source.getChannel = function(url) {
    const countryCode = extractCountryCode(url);
    if (!countryCode) {
        throw new ScriptException("Invalid country URL format");
    }

    if (state.countries[countryCode]) {
        return state.countries[countryCode];
    }

    // Get country information from the server using callUrl
    const countries = callUrl(`${state.api_base_url}/countries`, {}, true);

    const countryInfo = countries.find(c => c.iso_3166_1 === countryCode);

    if (!countryInfo) {
        throw new ScriptException(`Country not found: ${countryCode}`);
    }

    const channel = new PlatformChannel({
        id: new PlatformID(PLATFORM, countryCode, _config.id),
        name: `Radios from ${countryInfo.name}`,
        thumbnail: getCountryFlagUrl(countryCode, "w320"),
        banner: getCountryFlagUrl(countryCode, "w1280"),
        subscribers: 0,
        description: `Radio stations from ${countryInfo.name}`,
        url: getChannelUrl(countryCode),
        urlAlternatives: [`radiobrowser://country/${countryCode}`]
    });

    state.countries[countryCode] = channel;
    return channel;
};

/**
 * Search for content within a specific channel (country)
 * @param {string} url - Country channel URL
 * @param {string} query - Search query string
 * @returns {VideoPager} Paged search results filtered by country
 * @throws {ScriptException} If URL format is invalid
 */
source.searchChannelContents = function(url, query) {
    const countryCode = extractCountryCode(url);
    if (!countryCode) {
        throw new ScriptException("Invalid country URL format");
    }

    return searchStations({
        countrycode: countryCode,
        name: query,
        limit: DEFAULT_LIMIT,
        hidebroken: "true",
        order: "clickcount",
        reverse: "true"
    });
};

/**
 * Get radio stations for a country
 * @param {string} url Country channel URL
 * @returns {VideoPager} Paged radio stations
 */
source.getChannelContents = function(url) {
    const countryCode = extractCountryCode(url);
    if (!countryCode) {
        throw new ScriptException("Invalid country URL format");
    }

    // Make sure we're creating a fresh RadioBrowserPager for this country
    return searchStations({
        countrycode: countryCode,
        limit: DEFAULT_LIMIT,
        hidebroken: "true",
        order: "clickcount",
        reverse: "true"
    });
};

/**
 * Check if a URL is a radio station
 * @param {string} url URL to check
 * @returns {boolean} True if URL is a radio station
 */
source.isContentDetailsUrl = function(url) {
    return REGEX.STATION_URL.test(url) || REGEX.WEB_STATION_URL.test(url);
};

/**
 * Get detailed information about a radio station
 * @param {string} url Radio station URL
 * @returns {PlatformVideoDetails} Radio station details
 */
source.getContentDetails = function(url) {
    let match = url.match(REGEX.STATION_URL);
    
    // If not matching internal URL format, try web URL format
    if (!match) {
        match = url.match(REGEX.WEB_STATION_URL);
    }
    
    if (!match) {
        throw new ScriptException("Invalid station URL format");
    }
    
    const stationUuid = match[1];
    
    if (state.stationCache[stationUuid]) {
        return stationToVideo(state.stationCache[stationUuid]);
    }
    
    // Use callUrl to get station data with retries and server rotation
    const stations = callUrl(`${state.api_base_url}/stations/byuuid/${stationUuid}`, null, true);
    
    if (!stations || stations.length === 0) {
        throw new ScriptException(`Station not found: ${stationUuid}`);
    }
    
    const station = stations[0];
    state.stationCache[stationUuid] = station;
    
    return stationToVideo(station);
};

/**
 * Search for countries (channels)
 * @param {string} query Search query
 * @returns {ChannelPager} Paged country results
 */
source.searchChannels = function(query) {
    // Use callUrl with an empty object as post data
    const countries = callUrl(`${state.api_base_url}/countries`, {}, true);
    
    // Filter by query if provided
    let filteredCountries = countries;
    if (query) {
        const queryLower = query.toLowerCase();
        filteredCountries = countries.filter(c => 
            c.name.toLowerCase().includes(queryLower)
        );
    }
    
    // Sort by station count (most popular first)
    filteredCountries.sort((a, b) => b.stationcount - a.stationcount);
    
    // Return top 50 countries
    const channels = filteredCountries.slice(0, 50).map(country =>
        new PlatformAuthorLink(
            new PlatformID(PLATFORM, country.iso_3166_1, _config.id),
            `Radios from ${country.name}`,
            getChannelUrl(country.iso_3166_1),
            getCountryFlagUrl(country.iso_3166_1, "w320")
        )
    );

    return new ChannelPager(channels, false);
};


/**
 * Get playback tracker for activity submission to Radio Browser API
 * @param {string} url - Station URL in format radiobrowser://station/{uuid}
 * @returns {RadioBrowserPlaybackTracker|null} Playback tracker instance or null if disabled
 */
source.getPlaybackTracker = function (url) {

    if (!_settings.submitActivity) {
       return null; 
    }

    try {
        const match = url.match(REGEX.STATION_URL);
        if (match) {
            const stationUuid = match[1];
            if (stationUuid) {
                return new RadioBrowserPlaybackTracker(stationUuid);
            }
        }
    } catch (error) {
        log(`Error creating playback tracker: ${error.message}`);
    }

    return null;
};

// ====================== PAGER IMPLEMENTATION ======================

/**
 * Search for radio stations with pagination
 * @param {Object} params Search parameters
 * @returns {RadioBrowserPager} Pager with search results
 */
function searchStations(params) {
    // Create a proper pager with pagination support
    return new RadioBrowserPager(params);
}

/**
 * Custom pager implementation for radio stations
 */
class RadioBrowserPager extends VideoPager {
    /**
     * Constructor for RadioBrowserPager
     * @param {Object} params - Search parameters
     * @param {number} params.limit - Number of results per page
     * @param {string} params.hidebroken - Whether to hide broken stations
     * @param {string} params.order - Sort order field
     * @param {string} params.reverse - Whether to reverse sort order
     * @param {string} [params.name] - Station name search query
     * @param {string} [params.countrycode] - Country code filter
     */
    constructor(params) {
        // Initialize with empty results, set hasMore to true to trigger first load
        super([], true, { 
            params: params,
            offset: 0,
            limit: params.limit || DEFAULT_LIMIT
        });
        
        // Load the first page immediately in the constructor
        this.nextPage();
    }
    
    /**
     * Load the next page of results
     * @returns {RadioBrowserPager} This pager with updated results
     */
    nextPage() {
        // Update offset for pagination
        const params = {
            ...this.context.params,
            offset: this.context.offset,
            limit: this.context.limit
        };
        
        LogIfTesting("RadioBrowser search params: " + JSON.stringify(params));
        
        // Use callUrl with the params as POST data
        const stations = callUrl(
            `${state.api_base_url}/stations/search`, 
            params, 
            true,
            3  // Use 2 retries for search operations
        );
        
        LogIfTesting("Found " + stations.length + " stations");
        
        // Map stations to videos
        this.results = stations.map(e => stationToVideo(e));
        
        // Cache stations for quick access
        stations.forEach(station => {
            state.stationCache[station.stationuuid] = station;
        });
        
        // Set hasMore based on results count
        this.hasMore = this.results.length >= this.context.limit;
        
        // Update offset for next page
        this.context.offset += this.context.limit;
        
        return this;
    }
}
// ====================== IMAGE QUALITY FUNCTIONS ======================
/**
 * Generate country flag URL based on country code and size
 * @param {string} countryCode Country code (ISO 3166-1 alpha-2)
 * @param {string} size Size specification (e.g., "w320", w640, "w1280")
 * @returns {string} Flag URL
 */
function getCountryFlagUrl(countryCode, size = "w640") {
    return `${URLS.FLAGS_CDN}/${size}/${countryCode.toLowerCase()}.png`;
}

/**
 * Get best thumbnail URL for a station based on the selected thumbnail mode
 * @param {Object} station Radio station data
 * @returns {string} Best thumbnail URL
 */
function getThumbnailUrl(station) {
    // Use plugin icon if that mode is selected
    if (_settings.thumbnailMode === THUMBNAIL_MODE.PLATFORM_LOGO) {
        // Return plugin thumbnail
        return URLS.PLUGIN_THUMBNAIL;
    }

    if (_settings.thumbnailMode === THUMBNAIL_MODE.SOUNDBAR) {
        return 'https://plugins.grayjay.app/RadioBrowser/media/soundbar.png';
    }

    // Always use country flag if that mode is selected
    if (_settings.thumbnailMode === THUMBNAIL_MODE.COUNTRY_FLAG) {
        if (station.countrycode) {
            return getCountryFlagUrl(station.countrycode);
        }
        return "";
    }
    
    // Otherwise use station favicon (STATION_FAVICON mode)
    if (station.favicon && station.favicon.trim() !== "") {
        return station.favicon;
    }
    
    // If station has no favicon, fallback to country flag
    if (station.countrycode) {
        return getCountryFlagUrl(station.countrycode);
    }
    
    // Final fallback returns empty string (which will use plugin icon)
    return "";
}
// ====================== CONVERSION FUNCTIONS ======================

/**
 * Convert a radio station to a PlatformVideo object
 * @param {Object} station Radio station data
 * @returns {PlatformVideo} Platform video object
 */
function stationToVideo(station) {

    // Create a proper platform author (country)
    const authorThumb = station.countrycode ?
        getCountryFlagUrl(station.countrycode) : null;

    let video;

    if (station.hls === 1) {
        // HLS stream
        video = new UnMuxVideoSourceDescriptor([],
            [
                new HLSSource({
                    name: "HLS Stream",
                    url: station.url_resolved || station.url,
                    duration: -1, // Live content
                    language: station.countrycode,
                    original: true
                })
            ]
        );
    } else {
        // Direct audio stream
        video = new UnMuxVideoSourceDescriptor([], [
            new AudioUrlSource({
                codec: station.codec,
                container: 'audio/mp3',
                name: formatQualityLabel(station),
                url: station.url_resolved || station.url,
                duration: -1 // Live content
            })]);
    }

    const stationDetails = new PlatformVideoDetails({
        id: new PlatformID(PLATFORM, station.stationuuid, _config.id),
        name: station.name,
        thumbnails: new Thumbnails([
            new Thumbnail(getThumbnailUrl(station), 0)
        ]),
        author: new PlatformAuthorLink(
            new PlatformID(PLATFORM, station.countrycode, _config.id),
            `Radios from ${station.country || "Unknown"}`,
            getChannelUrl(station.countrycode),
            authorThumb
        ),
        duration: 0,
        url: `radiobrowser://station/${station.stationuuid}`,
        description: formatStationDescription(station),
        isLive: true,
        video: video,
        shareUrl: URLS.STATION_HISTORY.replace(/{uuid}/g, station.stationuuid)
    });

    // Add content recommendations capability
    stationDetails.getContentRecommendations = function () {
        return getRelatedStations(station);
    };

    return stationDetails;
}

/**
 * Get related stations based on a station's language and tags
 * @param {Object} station Radio station data
 * @returns {VideoPager} Pager with related stations
 */
function getRelatedStations(station) {
    // Create search parameters for related stations
    const params = {
        limit: RELATED_DEFAULT_LIMIT,
        hidebroken: "true",
        order: "clickcount",
        reverse: "true"
    };
    
    // Find stations in the same language
    if (station.language && station.language.trim() !== "") {
        const mainLanguage = station.language.split(",")[0].trim();
        params.language = mainLanguage;
        
        // If we have a main tag, refine the search to include that tag
        if (station.tags && station.tags.trim() !== "") {
            const tags = station.tags.split(",").map(tag => tag.trim());
            if (tags.length > 0) {
                params.tag = tags[0]; // Use tag (partial match) not tagExact for more results
            }
        }
    } else if (station.countrycode) {
        // Last resort fallback to country if no language available
        params.countrycode = station.countrycode;
    }
    
    // Get related stations
    const pager = searchStations(params);
    
    // Filter out the current station from recommendations
    if (pager && pager.results) {
        pager.results = pager.results.filter(
            video => video.id.value !== station.stationuuid
        );
    }
    
    return pager;
}

// ====================== UTILITY FUNCTIONS ======================

/**
 * Extract country code from a channel URL (supports both internal and web formats)
 * @param {string} url - Channel URL
 * @returns {string|null} Country code or null if URL format is invalid
 */
function extractCountryCode(url) {
    let match = url.match(REGEX.COUNTRY_URL);
    if (match) {
        return match[1];
    }

    match = url.match(REGEX.WEB_COUNTRY_URL);
    if (match) {
        return match[1];
    }

    return null;
}

/**
 * Generate the web URL for a country channel
 * @param {string} countryCode - ISO 3166-1 alpha-2 country code
 * @returns {string} Web URL for the country channel
 */
function getChannelUrl(countryCode) {
    return URLS.WEB_COUNTRY_SEARCH.replace(/{countrycode}/g, countryCode);
}

/**
 * Format station description with all available information
 * @param {Object} station Radio station data
 * @returns {string} Formatted description
 */
function formatStationDescription(station) {
    let description = "";
    
    if (station.country) {
        description += `Country: ${station.country}\n`;
    }
    
    if (station.language) {
        description += `Language: ${station.language}\n`;
    }
    
    if (station.tags) {
        description += `Tags: ${station.tags}\n`;
    }
    
    if (station.codec) {
        description += `Format: ${station.codec}`;
        
        if (station.bitrate) {
            description += ` ${station.bitrate} kbps`;
        }
        
        description += "\n";
    }
    
    if (station.homepage) {
        description += `Website: ${station.homepage}\n`;
    }

    description += `\nThis is a community-driven project. Contribute by adding new stations: <a href="${URLS.WEB_CONTRIBUTE_NEW_STATION}">${URLS.WEB_CONTRIBUTE_NEW_STATION}</a>\n`;
    
    // Add location links if coordinates are available
    if (station.geo_lat && station.geo_long) {
        description += "\nSee location:\n";
        
        // Replace placeholders with actual coordinates
        const replaceCoordinates = (url) => {
            return url.replace(/{lat}/g, station.geo_lat).replace(/{lon}/g, station.geo_long);
        };
        
        description += `<a href="${replaceCoordinates(URLS.MAPS.OPENSTREETMAP)}">OpenStreetMap</a>\n`;
        description += `<a href="${replaceCoordinates(URLS.MAPS.GOOGLE_MAPS)}">Google Maps</a>\n`;
        description += `<a href="${replaceCoordinates(URLS.MAPS.BING_MAPS)}">Bing Maps</a>\n`;
        description += `<a href="${replaceCoordinates(URLS.MAPS.WAZE)}">Waze</a>\n`;
        description += `<a href="${replaceCoordinates(URLS.MAPS.APPLE_MAPS)}">Apple Maps</a>\n`;
    }
    
    return description;
}

/**
 * Format quality label for an audio stream
 * @param {Object} station Radio station data
 * @returns {string} Formatted quality label
 */
function formatQualityLabel(station) {
    let label = "Audio";
    
    if (station.codec) {
        label += ` (${station.codec}`;
        
        if (station.bitrate) {
            label += ` ${station.bitrate} kbps`;
        }
        
        label += ")";
    }
    
    return label;
}


// ====================== PLAYBACK TRACKING ======================

/**
 * Custom playback tracker for Radio Browser
 */
class RadioBrowserPlaybackTracker extends PlaybackTracker {
    /**
     * Constructor for playback tracker
     * @param {string} stationUuid - Station UUID to track
     * @param {number} interval - Report interval in seconds (default: 1)
     */
    constructor(stationUuid, interval = 1) {
        super(interval * 1000); // Convert to milliseconds
        this.stationUuid = stationUuid;
    }
    
    /**
     * Called when playback starts - submits station click to Radio Browser API
     */
    onInit() {
        // Use callUrl with parseResponse=false since we don't need the response
        callUrl(`${state.api_base_url}/url/${this.stationUuid}`, null, false);     
    }
    
    /**
     * Called periodically during playback
     * @param {number} position - Current position in seconds
     * @param {boolean} isPlaying - Whether playback is active
     */
    onProgress(position, isPlaying) {}
    
    /**
     * Called when playback concludes
     */
    onConcluded() {}

}

// ====================== NETWORK UTILITY FUNCTIONS ======================

/**
 * Fetch API servers from the discovery endpoint.
 * Attempts to parse the response body regardless of HTTP status code,
 * so that valid JSON returned with e.g. a 502 status is still usable.
 * @returns {string[]|null} Array of unique server hostnames, or null on failure
 */
function fetchApiServers() {
    if (state.api_servers && state.api_servers.length > 0) {
        return state.api_servers;
    }
    try {
        const res = http.GET(URLS.API_SERVERS, DEFAULT_HEADERS);
        let servers = null;
        if (res.body) {
            try {
                servers = JSON.parse(res.body);
            } catch (parseError) {
                LogIfTesting("Failed to parse server response (HTTP " + res.code + "): " + parseError);
            }
        }
        if (servers && servers.length) {
            const uniqueServers = new Set();
            servers.forEach(function(server) { uniqueServers.add(server.name); });
            var result = Array.from(uniqueServers);
            LogIfTesting("Fetched " + result.length + " API servers");
            return result;
        }
        LogIfTesting("No valid servers in response (HTTP " + res.code + ")");
        return null;
    } catch (e) {
        LogIfTesting("Failed to fetch server list: " + e);
        return null;
    }
}

/**
 * Gets the requested url and returns the response body either as a string or as a parsed json object
 * Implements automatic server rotation and retry logic for Radio Browser API requests
 * @param {string} url - The url to call
 * @param {Object|string} [postData=null] - If provided, makes a POST request with this data
 * @param {boolean} [parseResponse=true] - If true, will parse the response as json
 * @param {number} [retries=5] - Number of retries if the request fails
 * @returns {string|Object} The response body as a string or the parsed json object
 * @throws {ScriptException} If all retry attempts fail
 */
function callUrl(url, postData = null, parseResponse = true, retries = 5) {
    let lastError;
    let attempts = retries + 1; // +1 for the initial attempt
    let attemptCount = 0;
    
    // Ensure we have fetched servers if this is an API call
    // Also fetch if we only have 1 server (likely the default)
    if (url.includes("/json/") && (!state.api_servers || state.api_servers.length <= 1)) {
        LogIfTesting(`API servers in state: ${state.api_servers?.length || 0}. Fetching server list...`);
        const serverList = fetchApiServers();
        if (serverList) {
            state.api_servers = serverList;
            state.serverIndex = 0;
        } else {
            state.api_servers = [...FALLBACK_SERVERS];
            state.serverIndex = 0;
        }
    }
    
    // Update totalServers after potentially fetching the list
    let totalServers = state.api_servers.length || 1;
    
    // Log current state for debugging
    LogIfTesting(`Starting callUrl with ${totalServers} servers available. Current serverIndex: ${state.serverIndex}`);
    
    // Function to get the current API server URL
    const getServerUrl = (originalUrl) => {
        // If we don't have multiple servers yet or this isn't an API URL, return the original
        if (state.api_servers.length <= 1 || !originalUrl.includes("/json/")) {
            return originalUrl;
        }
        
        // For API requests, use the current server index
        const currentServerIndex = state.serverIndex % state.api_servers.length;
        const baseServerUrl = `https://${state.api_servers[currentServerIndex]}/json`;
        
        // Replace the API base URL in the original URL
        return originalUrl.replace(/https:\/\/[^\/]+\/json/, baseServerUrl);
    };

    while (attempts > 0) {
        try {
            // For retries (not the first attempt), rotate to next server
            if (attemptCount > 0 && totalServers > 1) {
                state.serverIndex = (state.serverIndex + 1) % totalServers;
                LogIfTesting(`Rotating to next server. New serverIndex: ${state.serverIndex}`);
            }

            // Get the URL for this attempt
            const currentUrl = getServerUrl(url);

            const currentServerIndex = state.api_servers.length > 0 ? state.serverIndex % state.api_servers.length : 0;
            const serverName = state.api_servers.length > 0 ? state.api_servers[currentServerIndex] : 'default';
            const msg = `Attempt ${attemptCount + 1}/${retries + 1} with server: ${serverName} (URL: ${currentUrl})`;
            LogIfTesting(msg);

            let response;

            // Make either GET or POST request
            if (postData !== null) {
                // For POST requests
                const postDataStr = typeof postData === 'string' ? postData : JSON.stringify(postData);
                response = http.POST(currentUrl, postDataStr, DEFAULT_HEADERS);
            } else {
                // For GET requests
                response = http.GET(currentUrl, DEFAULT_HEADERS);
            }

            if (!response.isOk) {
                throw new ScriptException(`Request [${currentUrl}] failed with code [${response.code}]`);
            }

            if (parseResponse) {
                const json = JSON.parse(response.body);
                return json;
            }

            return response.body;
        } catch (error) {
            lastError = error;
            attempts--;
            attemptCount++;

            // Don't rotate here - rotation happens at the beginning of the loop

            if (attempts === 0) {
                // All retry attempts failed
                LogIfTesting(`Request failed after ${retries + 1} attempts`);
                log(lastError);

                // If this was an API call and we've tried all servers at least once, try refreshing the server list
                if (url.includes("/json/") && attemptCount >= totalServers) {
                    LogIfTesting("Attempting to refresh server list...");
                    const refreshedServers = fetchApiServers();
                    if (refreshedServers) {
                        state.api_servers = refreshedServers;
                        state.serverIndex = Math.floor(Math.random() * state.api_servers.length);
                        LogIfTesting("Refreshed server list. Found " + state.api_servers.length + " servers");

                        // Try one more time with the fresh server list
                        const freshUrl = getServerUrl(url);
                        try {
                            const finalResponse = postData !== null
                                ? http.POST(freshUrl, typeof postData === 'string' ? postData : JSON.stringify(postData), DEFAULT_HEADERS)
                                : http.GET(freshUrl, DEFAULT_HEADERS);

                            if (finalResponse.isOk) {
                                if (parseResponse) {
                                    return JSON.parse(finalResponse.body);
                                }
                                return finalResponse.body;
                            }
                        } catch (finalError) {
                            LogIfTesting("Final attempt after refresh failed: " + finalError);
                        }
                    }
                }

                throw new ScriptException(`All servers failed. Last error: ${lastError.message || lastError}`);
            }

            if (attempts > 0 && attemptCount > 0 && attemptCount % totalServers === 0) {
                LogIfTesting(`Completed a full rotation through all ${totalServers} servers. Continuing with remaining ${attempts} attempts...`);
            }
        }
    }
}

/**
 * Log message only when IS_TESTING flag is enabled
 * @param {string} msg - Message to log for debugging
 */
function LogIfTesting(msg) {
    if (IS_TESTING) {
        log(msg);
    }
    // bridge.toast(msg);
}

log('LOADED');