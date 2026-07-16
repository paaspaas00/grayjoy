/**
 * Mixcloud Plugin for Grayjay
 * 
 * This plugin enables browsing, searching, and streaming Mixcloud content.
 */

// ====================== CONSTANTS ======================
const PLATFORM = {
  name: "Mixcloud"
};

const URLS = {
  GRAPHQL_URL: "https://app.mixcloud.com/graphql",
  BASE_URL: "https://www.mixcloud.com",
  PLUGIN_ICON_URL: "https://plugins.grayjay.app/Mixcloud/MixcloudIcon.png"
};

// Default request headers for API requests
const DEFAULT_REST_HEADERS = {
  "Origin": URLS.BASE_URL,
  "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:138.0) Gecko/20100101 Firefox/138.0",
  "Content-Type": "application/json"
};

// Regular expressions for URL matching
const REGEX = {
  // Content URLs
  LIVE_STREAM_URL: /^https:\/\/www\.mixcloud\.com\/live\/([a-zA-Z0-9_-]+)\/?$/,
  CONTENT_DETAILS_URL: /^https:\/\/www\.mixcloud\.com\/([^\/]+)\/([^\/\?]+)\/?(\?.*)?$/, // Permissive pattern to handle multiple scripts such as Mandarin, updated to allow query params
  CHANNEL_URL: /^https:\/\/www\.mixcloud\.com\/([a-zA-Z0-9_-]+)\/?$/,
  
  // Playlist URLs
  PLAYLIST_URL: /^https:\/\/www\.mixcloud\.com\/([^\/]+)\/playlists\/([^\/]+)\/?$/,
  PLAYLISTS_URL: /^https:\/\/www\.mixcloud\.com\/([^\/]+)\/playlists\/?$/,
  FAVORITES_URL: /^https:\/\/www\.mixcloud\.com\/([^\/]+)\/favorites\/?$/,
  HISTORY_URL: /^https:\/\/www\.mixcloud\.com\/([^\/]+)\/listens\/?$/,
  LISTEN_LATER_URL: /^https:\/\/www\.mixcloud\.com\/listen-later\/?$/,
  REPOSTS_URL: /^https:\/\/www\.mixcloud\.com\/([^\/]+)\/reposts\/?$/,
  TRACKS_URL: /^https:\/\/www\.mixcloud\.com\/([^\/]+)\/tracks\/?$/,
  MIXCLOUD_INTERNAL_CREATOR_OWN_CONTENT: /[?&]isMixCloudOwnChannelContent=true(&|$)/
};

Type.Feed.Playlists = "PLAYLISTS";

// List of URLs that should not be matched on source.isChannelUrl, source.isContentDetailsUrl, source.isPlaylistUrl
// since mixcloud does not have a dedicated perfix url for channels and published content
// this way those URLs are not matched as channel or content details URL and fail gracefully
const RESERVED_URL_LIST = [
  "https://www.mixcloud.com/genres/",
  "https://www.mixcloud.com/pro/monetization/",
  "https://www.mixcloud.com/pro/host-tagging/",
  "https://www.mixcloud.com/pro/live/",
  "https://www.mixcloud.com/pro/unlimited-uploads/",
  "https://www.mixcloud.com/pro/posts/",
  "https://www.mixcloud.com/pro/customization/",
  "https://www.mixcloud.com/pro/stats/",
  "https://www.mixcloud.com/pro/hq-audio/",
  "https://www.mixcloud.com/premium/",
  "https://www.mixcloud.com/login/",
  "https://www.mixcloud.com/brand/",
  "https://www.mixcloud.com/privacy/",
  "https://www.mixcloud.com/terms/",
  "https://www.mixcloud.com/dmca/",
  "https://www.mixcloud.com/developers/",
  "https://www.mixcloud.com/subscription-terms/",
  "https://www.mixcloud.com/select-terms/",
  "https://www.mixcloud.com/about/",
  "https://www.mixcloud.com/settings/",
  "https://www.mixcloud.com/settings/applications/",
  "https://www.mixcloud.com/settings/notifications/",
  "https://www.mixcloud.com/settings/profile/",
  "https://www.mixcloud.com/settings/account/",
  "https://www.mixcloud.com/pro/tips/",
  "https://www.mixcloud.com/home/new-uploads/",
  "https://www.mixcloud.com/settings/monetization/tips/",
  "https://www.mixcloud.com/settings/payments/",
  "https://www.mixcloud.com/settings/monetization/subscriptions/",
  "https://www.mixcloud.com/notifications/",
  "https://www.mixcloud.com/upload/",
  "https://www.mixcloud.com/pro/",
  "https://www.mixcloud.com/plans/",
  "https://www.mixcloud.com/dashboard/posts/",
  "https://www.mixcloud.com/dashboard/earnings/",
  "https://www.mixcloud.com/dashboard/my-shows/",
  "https://www.mixcloud.com/dashboard/my-tracks/",
  "https://www.mixcloud.com/dashboard/stats/",
  "https://www.mixcloud.com/home/for-you/",
  "https://www.mixcloud.com/home/feed/",
  "https://www.mixcloud.com/live/",
  "https://www.mixcloud.com/discover/",
  "https://www.mixcloud.com/playlists/",
  "https://www.mixcloud.com/trending/",
  "https://www.mixcloud.com/favorites/",
  "https://www.mixcloud.com/history/",
  "https://www.mixcloud.com/dashboard/playlists/",
  "https://www.mixcloud.com/jobs/",
  "https://www.mixcloud.com/press/",
  "https://www.mixcloud.com/advertise/",
  "https://www.mixcloud.com/faq/",
  "https://www.mixcloud.com/categories/",
  "https://www.mixcloud.com/signup/",
  "https://www.mixcloud.com/contact/",
  "https://www.mixcloud.com/apps/",
  "https://www.mixcloud.com/featured/",
  "https://www.mixcloud.com/popular/",
  "https://www.mixcloud.com/new/",
  "https://www.mixcloud.com/search/"
];

// Plugin configuration storage
let config = {};

let state = {
  hasPremiumFeatures: false,
  hasProFeatures: false
};

// ====================== PLUGIN INITIALIZATION ======================

source.enable = function (conf, settings, saveStateStr) {
  config = conf ?? {};

  if (saveStateStr) {
    state = JSON.parse(saveStateStr);
  }
  else if (bridge.isLoggedIn()) {

    const useAuth = true;
    const cloudcastInfo = makeGraphQLRequest("getViewerDataQuery", {}, null, useAuth);

    if (cloudcastInfo?.data?.viewer?.me) {
      state.hasPremiumFeatures = cloudcastInfo?.data?.viewer?.me?.hasPremiumFeatures ?? false;
      state.hasProFeatures = cloudcastInfo?.data?.viewer?.me?.hasProFeatures ?? false;
    }
  }

};


source.saveState = () => {
    return JSON.stringify(state);
};

// ====================== PLUGIN ENTRY POINTS ======================

/**
 * Gets the home feed (currently live streams)
 * @returns {ContentPager} A pager for live streams
 */
source.getHome = function () {
  return new LiveStreamPager();
};

/**
 * Checks if a URL is a content details URL
 * @param {string} url URL to check
 * @returns {boolean} True if URL is a content details URL
 */
source.isContentDetailsUrl = function (url) {

  // Check if query param isMixCloudOwnChannelContent=true
  if (REGEX.MIXCLOUD_INTERNAL_CREATOR_OWN_CONTENT.test(url)) {
    return true;
  }

  // Check if URL is in the reserved list
  if (isPlatformReservedUrl(url)) {
    return false;
  }

  // Check if it's a playlist-type URL (favorites, reposts, playlists, etc.)
  if (source.isPlaylistUrl(url)) {
    return false;
  }

  // Match live streams
  if (urlMatches(url, REGEX.LIVE_STREAM_URL)) {
    return true;
  }

  // Match regular mixes (format: https://www.mixcloud.com/username/mix-name/)
  if (urlMatches(url, REGEX.CONTENT_DETAILS_URL)) {
    return true;
  }

  return false;
};

/**
 * Gets details for a content URL
 * @param {string} url Content URL
 * @returns {PlatformVideoDetails} Details about the content
 */
source.getContentDetails = function (encodedUrl) {

  const url = decodeURIComponent(encodedUrl);

  const isLive = urlMatches(url, REGEX.LIVE_STREAM_URL);

  if (isLive) {
    return getContentDetailsLiveStream(url);
  } else {
    return getContentDetailsCloudcast(url);
  }
};


/**
 * Returns chat window information for live Mixcloud streams with chat
 * @param {string} url - The video URL
 * @returns {Object|null} Chat window configuration or null if chat not available
 */
source.getLiveChatWindow = function (url) {
  const usernameMatch = url.match(REGEX.LIVE_STREAM_URL);

  if (usernameMatch && usernameMatch[1]) {
    // Extract username from live stream URL using regex capture group
    const username = usernameMatch[1];

    return {
      url: `${createMixcloudUrl(username, null, true)}/chat/`,
      removeElements: [],
      removeElementsInterval: []
    };
  }
};

/**
 * Checks if a URL is a channel URL
 * @param {string} url URL to check
 * @returns {boolean} True if URL is a channel URL
 */
source.isChannelUrl = function (url) {
  // Check if URL is in the reserved list
  if (isPlatformReservedUrl(url)) {
    return false;
  }

  // Check if it's a playlist-type URL (favorites, reposts, playlists, etc.)
  if (source.isPlaylistUrl(url)) {
    return false;
  }

  return urlMatches(url, REGEX.CHANNEL_URL);
};

/**
 * Gets channel information
 * @param {string} url Channel URL
 * @returns {PlatformChannel} Channel information
 */
source.getChannel = function (url) {

  if(url.endsWith('/')) {
    url = url.slice(0, -1);
  }

  const usernameMatch = url.match(REGEX.CHANNEL_URL);

  if (!usernameMatch || !usernameMatch[1]) {
    throw new UnavailableException("Not able to access channel information");
  }

  // Extract username from channel URL using regex capture group
  const username = usernameMatch[1];

  const channelInfo = makeGraphQLRequest("ChannelInfoByUsername", {
    "lookup": {
      "username": username
    }
  });

  if (!channelInfo?.data?.user) {
    throw new UnavailableException("Channel not found");
  }

  const links = {
    Mixcloud: url
  };

  if (Array.isArray(channelInfo?.data?.user?.socialMediaLinks)) {
    channelInfo.data.user.socialMediaLinks.forEach(link => {
      links[capitalizeFirstLetter(link.platform) || link.deeplink] = link.deeplink;
    });
  }
  else {
    bridge.log("socialMediaLinks is not an array");
  }

  if (Array.isArray(channelInfo?.data?.user?.customLinks)) {
    channelInfo.data.user.customLinks.forEach(link => {
      links[capitalizeFirstLetter(link.title) || link.url] = link.url;
    });
  } else {
    bridge.log("customLinks is not an array");
  }

  let description = channelInfo?.data?.user?.biog ?? '';
  const location = [channelInfo?.data?.user?.country, channelInfo?.data?.user?.city].filter(Boolean).join(', ');
  
  if (location) {
    description += `\n\n${location}`;
  }

  return new PlatformChannel({
    id: new PlatformID(PLATFORM.name, channelInfo.data.user.id, config.id),
    name: channelInfo.data.user.displayName,
    description: description,
    url: url,
    thumbnail: channelInfo.data.user.picture?.urlRoot ? createThumbnailUrl(channelInfo.data.user.picture.urlRoot, 272, 272) : null,
    banner: channelInfo.data.user.coverPicture?.urlRoot ? createThumbnailUrl(channelInfo.data.user.coverPicture.urlRoot, 1460, 370) : null,
    subscribers: channelInfo.data.user.followers?.totalCount || 0,
    links: links,
    urlAlternatives: [
      `https://www.mixcloud.com/${username}/`
    ]
  });
};

/**
 * Gets channel capabilities
 * @returns {object} Object with content types and sorts
 */
source.getChannelCapabilities = () => {
	return {
		types: [Type.Feed.Mixed, Type.Feed.Playlists],
		sorts: [Type.Order.Chronological]
	};
};

/**
 * Gets channel content
 * @param {string} url Channel URL
 * @param {string} type Content type
 * @param {string} order Order
 * @param {object} filters Filters
 * @returns {ContentPager} A pager for channel content
 */
source.getChannelContents = function (url, type, order, filters) {
  const usernameMatch = url.match(REGEX.CHANNEL_URL);

  if (!usernameMatch || !usernameMatch[1]) {
    throw new UnavailableException("Not able to access channel information");
  }

  // Extract username from channel URL using regex capture group
  const username = usernameMatch[1];

  // Handle different content type requests
  if (type === Type.Feed.Playlists) {
    // For playlists from a channel
    return source.getChannelPlaylists(url);
  } else {
    // For video/mixed content
    // First get the channel ID which is needed for pagination queries
    const channelInfo = makeGraphQLRequest("ChannelInfoByUsername", {
      "lookup": {
        "username": username
      }
    });

    if (!channelInfo?.data?.user?.id) {
      throw new UnavailableException("Channel not found");
    }

    const initialData = [];

    if (channelInfo?.data?.user?.liveStream?.streamStatus === 'LIVE') {
      initialData.push(source.getContentDetails(createMixcloudUrl(username, null, true)));
    }

    // Return a pager that will load channel uploads with pagination
    return new ChannelContentPager(channelInfo, username, initialData);
  }
};

/**
 * Gets channel playlists
 * @param {string} url Channel URL
 * @returns {PlaylistPager} A pager for channel playlists
 */
source.getChannelPlaylists = function (url) {
  const usernameMatch = url.match(REGEX.CHANNEL_URL);

  if (!usernameMatch || !usernameMatch[1]) {
    throw new UnavailableException("Not able to access channel information");
  }

  const username = usernameMatch[1];
  return new PlaylistsPager(username);
};

/**
 * Checks if a URL is a playlist URL
 * @param {string} url URL to check
 * @returns {boolean} True if URL is a playlist URL
 */
source.isPlaylistUrl = function (url) {
  return urlMatches(url, REGEX.PLAYLIST_URL) || 
         urlMatches(url, REGEX.PLAYLISTS_URL) || 
         urlMatches(url, REGEX.FAVORITES_URL) || 
         urlMatches(url, REGEX.HISTORY_URL) || 
         urlMatches(url, REGEX.LISTEN_LATER_URL) || 
         urlMatches(url, REGEX.REPOSTS_URL) || 
         urlMatches(url, REGEX.TRACKS_URL);
};

/**
 * Gets playlist contents
 * @param {string} url Playlist URL
 * @returns {ContentPager} A pager for playlist content
 */
function getPlaylistContents(url) {
  // Check if it's a favorites URL
  const favoritesMatch = url.match(REGEX.FAVORITES_URL);
  
  if (favoritesMatch && favoritesMatch[1]) {
    const username = favoritesMatch[1];
    return new FavoritesPager(username);
  }
  
  // Check if it's a history URL
  const historyMatch = url.match(REGEX.HISTORY_URL);
  
  if (historyMatch && historyMatch[1]) {
    const username = historyMatch[1];
    return new HistoryPager(username);
  }
  
  // Check if it's a reposts URL
  const repostsMatch = url.match(REGEX.REPOSTS_URL);
  
  if (repostsMatch && repostsMatch[1]) {
    const username = repostsMatch[1];
    return new RepostsPager(username);
  }
  
  // Check if it's a channel playlists URL
  const playlistsMatch = url.match(REGEX.PLAYLISTS_URL);
  
  if (playlistsMatch && playlistsMatch[1]) {
    const username = playlistsMatch[1];
    return new PlaylistsPager(username);
  }
  
  // Check if it's a tracks URL
  const tracksMatch = url.match(REGEX.TRACKS_URL);
  
  if (tracksMatch && tracksMatch[1]) {
    const username = tracksMatch[1];
    return new TracksPager(username);
  }
  
  // Check if it's a listen-later URL (global, no username)
  const listenLaterMatch = url.match(REGEX.LISTEN_LATER_URL);
  
  if (listenLaterMatch) {
    return new ListenLaterPager();
  }
  
  // Otherwise, it's a specific playlist
  const match = url.match(REGEX.PLAYLIST_URL);
  
  if (!match || !match[1] || !match[2]) {
    throw new UnavailableException("Invalid playlist URL");
  }
  
  const username = match[1];
  const playlistSlug = match[2];
  
  return new PlaylistContentPager(username, playlistSlug);
}

/**
 * Gets playlist details
 * @param {string} url Playlist URL
 * @returns {PlatformPlaylistDetails} Playlist details
 */
source.getPlaylist = function (url) {
  // Handle different playlist types
  const favoritesMatch = url.match(REGEX.FAVORITES_URL);
  if (favoritesMatch && favoritesMatch[1]) {
    return createSpecialPlaylistDetails(favoritesMatch[1], "favorites", "Favorites");
  }
  
  const historyMatch = url.match(REGEX.HISTORY_URL);
  if (historyMatch && historyMatch[1]) {
    return createSpecialPlaylistDetails(historyMatch[1], "history", "Listening History");
  }
  
  const repostsMatch = url.match(REGEX.REPOSTS_URL);
  if (repostsMatch && repostsMatch[1]) {
    return createSpecialPlaylistDetails(repostsMatch[1], "reposts", "Reposts");
  }
  
  const tracksMatch = url.match(REGEX.TRACKS_URL);
  if (tracksMatch && tracksMatch[1]) {
    return createSpecialPlaylistDetails(tracksMatch[1], "tracks", "Tracks");
  }
  
  const listenLaterMatch = url.match(REGEX.LISTEN_LATER_URL);
  if (listenLaterMatch) {
    const userInfo = makeGraphQLRequest("getViewerDataQuery", {}, null, true);
    
    if (!userInfo?.data?.viewer?.me) {
      throw new UnavailableException("User not authenticated");
    }
    
    const user = userInfo.data.viewer.me;
    
    return new PlatformPlaylistDetails({
      id: new PlatformID(PLATFORM.name, "listen_later", config.id),
      name: "Listen Later",
      url: url,
      thumbnail: URLS.PLUGIN_ICON_URL,  // Listen Later doesn't have a specific thumbnail
      author: new PlatformAuthorLink(
        new PlatformID(PLATFORM.name, user.id || "me", config.id),
        user.username || "Me",
        createMixcloudUrl(user.username || ""),
        null
      ),
      videoCount: -1,  // Unknown count for listen later
      contents: getPlaylistContents(url)
    });
  }
  
  // Check if it's a channel playlists URL
  const playlistsMatch = url.match(REGEX.PLAYLISTS_URL);
  
  if (playlistsMatch && playlistsMatch[1]) {
    throw new UnavailableException("Cannot get details for playlists listing page");
  }
  
  // Otherwise, it's a specific playlist
  const match = url.match(REGEX.PLAYLIST_URL);
  
  if (!match || !match[1] || !match[2]) {
    throw new UnavailableException("Invalid playlist URL");
  }
  
  const username = match[1];
  const playlistSlug = match[2];
  
  // Fetch playlist details
  const playlistInfo = makeGraphQLRequest("PlaylistDetailsQuery", {
    lookup: {
      username: username,
      slug: playlistSlug
    }
  }, "PlaylistDetailsQuery");
  
  if (!playlistInfo?.data?.playlist) {
    logError(`PlaylistDetailsQuery failed. Response: ${JSON.stringify(playlistInfo)}`);
    throw new UnavailableException("Playlist not found");
  }
  
  const playlist = playlistInfo.data.playlist;
  const owner = playlist.owner || { username: username, displayName: username };
  
  return new PlatformPlaylistDetails({
    id: new PlatformID(PLATFORM.name, playlist.id, config.id),
    name: playlist.name,
    url: url,
    thumbnail: playlist.picture?.urlRoot ? 
      createThumbnailUrl(playlist.picture.urlRoot, 272, 272) : null,
    author: new PlatformAuthorLink(
      new PlatformID(PLATFORM.name, owner.id || username, config.id),
      owner.displayName || username,
      createMixcloudUrl(owner.username || username),
      owner.picture?.urlRoot ? createThumbnailUrl(owner.picture.urlRoot, 272, 272) : null
    ),
    videoCount: playlist.items?.totalCount || 0,
    contents: getPlaylistContents(url)
  });
};

/**
 * Searches for content
 * @param {string} query Search query
 * @returns {ContentPager} A pager for search results
 */
source.search = function (query) {
  return new SearchPager(query);
};

/**
 * Searches for channels
 * @param {string} query Search query
 * @returns {ContentPager} A pager for channel search results
 */
source.searchChannels = function (query) {
  return new ChannelSearchPager(query);
};

/**
 * Gets comments for a content URL
 * @param {string} url Content URL
 * @returns {CommentPager} A pager for comments
 */
source.getComments = function (url) {
  return new MixcloudCommentPager(url);
}

/**
 * Get user playlists
 * @returns {string[]} Array of playlist URLs
 */
source.getUserPlaylists = function () {
  try {
    // Get current user info to build system playlist URLs
    const userResponse = makeGraphQLRequest("getViewerDataQuery", {}, null, true);
    
    if (!userResponse?.data?.viewer?.me?.username) {
      logError("Unable to get current user info for playlists");
      return [];
    }
    
    const username = userResponse.data.viewer.me.username;
    
    // System playlists for current user
    const allPlaylistUrls = [
      `${URLS.BASE_URL}/${username}/favorites/`,
      `${URLS.BASE_URL}/${username}/listens/`,  // History
      `${URLS.BASE_URL}/${username}/reposts/`,  // User reposts
      `${URLS.BASE_URL}/listen-later/`  // Global listen-later URL
    ];
    let hasMore = true;
    let cursor = null;
    
    // Paginate through all playlists
    while (hasMore) {
      const variables = {};
      if (cursor) {
        variables.cursor = cursor;
      }
      
      const playlistsResponse = makeGraphQLRequest("PlaylistsQuery", variables, null, true);
      
      if (!playlistsResponse?.data?.viewer?.me?.playlists) {
        break;
      }
      
      const playlists = playlistsResponse.data.viewer.me.playlists;
      const edges = playlists.edges || [];
      const pageInfo = playlists.pageInfo || {};
      
      // Extract playlists and map to URLs
      const playlistUrls = edges.map(edge => {
        const playlist = edge.node;
        const owner = playlist.owner;
        
        // Return the full playlist URL
        return `${URLS.BASE_URL}/${owner.username}/playlists/${playlist.slug}/`;
      });
      
      // Add to all playlists
      allPlaylistUrls.push(...playlistUrls);
      
      // Update pagination
      hasMore = pageInfo.hasNextPage || false;
      cursor = pageInfo.endCursor || null;
    }
    
    return allPlaylistUrls;
  } catch (error) {
    logError(`Exception in getUserPlaylists: ${error.message}`);
    return [];
  }
}

/**
 * Gets the authenticated user's subscriptions (following list)
 * @returns {string[]} Array of channel URLs for subscribed channels
 */
source.getUserSubscriptions = function () {
  try {
    // Get current user info
    const userResponse = makeGraphQLRequest("getViewerDataQuery", {}, null, true);
    
    if (!userResponse?.data?.viewer?.me?.username) {
      logError("Unable to get current user info for subscriptions");
      return [];
    }
    
    const username = userResponse.data.viewer.me.username;
    const allSubscriptionUrls = [];
    let hasMore = true;
    let cursor = null;
    
    // Paginate through all followings
    while (hasMore) {
      const variables = {
        lookup: { username: username },
        count: 20
      };
      
      if (cursor) {
        variables.cursor = cursor;
      }
      
      const followingsResponse = makeGraphQLRequest("UserFollowingsQuery", variables, null, true);
      
      if (!followingsResponse?.data?.user?.followings) {
        break;
      }
      
      const followings = followingsResponse.data.user.followings;
      const edges = followings.edges || [];
      const pageInfo = followings.pageInfo || {};
      
      // Extract followings and map to channel URLs
      const subscriptionUrls = edges.map(edge => {
        const user = edge.node;
        return `${URLS.BASE_URL}/${user.username}`;
      });
      
      // Add to all subscriptions
      allSubscriptionUrls.push(...subscriptionUrls);
      
      // Update pagination
      hasMore = pageInfo.hasNextPage || false;
      cursor = pageInfo.endCursor || null;
    }
    
    return allSubscriptionUrls;
  } catch (error) {
    logError(`Exception in getUserSubscriptions: ${error.message}`);
    return [];
  }
}

// ====================== HELPER FUNCTIONS ======================

/**
 * Creates special playlist details for system playlists
 * @param {string} username Username
 * @param {string} type Playlist type
 * @param {string} displayName Display name
 * @returns {PlatformPlaylistDetails} Playlist details
 */
function createSpecialPlaylistDetails(username, type, displayName) {
  const userInfo = makeGraphQLRequest("ChannelInfoByUsername", {
    lookup: { username: username }
  });
  
  const user = userInfo?.data?.user;

  if (!user) {
    throw new UnavailableException("User not found");
  }


  let playlistThumbnail = null;
  if (user.coverPicture?.urlRoot) {
    playlistThumbnail = createThumbnailUrl(user.coverPicture.urlRoot, 1460, 370);
  } else if (user.picture?.urlRoot) {
    playlistThumbnail = createThumbnailUrl(user.picture.urlRoot, 272, 272);
  }

  const urlMap = {
    "favorites": `${username}/favorites`,
    "history": `${username}/listens`,
    "reposts": `${username}/reposts`,
    "tracks": `${username}/tracks`
  };

  return new PlatformPlaylistDetails({
    id: new PlatformID(PLATFORM.name, `${username}_${type}`, config.id),
    name: `${user.displayName}'s ${displayName}`,
    url: createMixcloudUrl(urlMap[type]),
    thumbnail: playlistThumbnail,
    author: new PlatformAuthorLink(
      new PlatformID(PLATFORM.name, user.id, config.id),
      user.displayName,
      createMixcloudUrl(username),
      user.picture?.urlRoot ? createThumbnailUrl(user.picture.urlRoot, 272, 272) : null
    ),
    videoCount: -1,
    contents: getPlaylistContents(createMixcloudUrl(urlMap[type]))
  });
}

// ====================== DECRYPTION FUNCTIONS ======================

/**
 * Decrypts Mixcloud audio URLs using XOR cipher
 * @param {string} encryptedUrl Base64 encoded encrypted URL
 * @returns {string} Decrypted URL
 */
function decryptMixcloudUrl(encryptedUrl) {
  const key = 'IFYOUWANTTHEARTISTSTOGETPAIDDONOTDOWNLOADFROMMIXCLOUD';
  
  try {
    // Decode base64
    const ciphertext = atob(encryptedUrl);
    
    // XOR decrypt
    let decrypted = '';
    for (let i = 0; i < ciphertext.length; i++) {
      const ch = ciphertext.charCodeAt(i);
      const k = key.charCodeAt(i % key.length);
      decrypted += String.fromCharCode(ch ^ k);
    }
    
    return decrypted;
  } catch (e) {
    log(`Failed to decrypt URL: ${e.message}`);
    return null;
  }
}

// ====================== CONTENT DETAILS FUNCTIONS ======================

/**
 * Gets details for a regular Mixcloud mix
 * @param {string} url URL of the cloudcast
 * @returns {PlatformVideoDetails} Details about the cloudcast
 */
function getContentDetailsCloudcast(url) {
  const mixMatch = url.match(REGEX.CONTENT_DETAILS_URL);

  const isMixCloudOwnChannelContent = new URL(url)?.searchParams?.get("isMixCloudOwnChannelContent") === "true";

  if (!mixMatch || !mixMatch[1] || !mixMatch[2]) {
    throw new UnavailableException("Not able to access cloudcast information");
  }

  // Extract username and slug using regex capture groups
  const username = mixMatch[1];
  const slug = mixMatch[2];

  const cloudcastInfo = makeGraphQLRequest("CloudcastLookup", {
    "lookup": {
      "username": username,
      "slug": slug
    }
  }, null, true);

  if (!cloudcastInfo?.data?.cloudcast) {
    throw new UnavailableException("Cloudcast not found");
  }

  const cloudcast = cloudcastInfo.data.cloudcast;

  const sources = [];

  const isPreview = isLimitedPreview(cloudcast);

  if (cloudcast?.streamInfo?.hlsUrl) {

    let hlsUrl;

    if (isValidURL(cloudcast?.streamInfo?.hlsUrl)) {
      hlsUrl = cloudcast?.streamInfo?.hlsUrl;
    } else {

      const decryptedUrl = decryptMixcloudUrl(cloudcast?.streamInfo?.hlsUrl);

      if (decryptedUrl) {
        hlsUrl = decryptedUrl;
      }

    }

    if (hlsUrl) {
      sources.push(new HLSSource({
        name: "HLS",
        url: hlsUrl,
        duration: cloudcast.audioLength,
        language: "Unknown",
        original: true
      }));
    }

  }
  else if (cloudcast?.streamInfo?.url) {
    log(`Attempting to decrypt URL: ${cloudcast.streamInfo.url}`);
    const decryptedUrl = decryptMixcloudUrl(cloudcast.streamInfo.url);
    
    if (decryptedUrl) {
      log(`Decrypted URL: ${decryptedUrl}`);
      // Determine format from decrypted URL
      if (decryptedUrl.includes('.m4a') || decryptedUrl.includes('.mp4')) {
        sources.push(new AudioUrlSource({
          name: "Audio",
          url: decryptedUrl,
          bitrate: 128000,
          container: "m4a",
          duration: cloudcast.audioLength
        }));
      } else if (decryptedUrl.includes('.mp3')) {
        sources.push(new AudioUrlSource({
          name: "Audio",
          url: decryptedUrl,
          bitrate: 128000,
          container: "mp3",
          duration: cloudcast.audioLength
        }));
      } else {
        // Try as m4a by default if no extension detected
        sources.push(new AudioUrlSource({
          name: "Audio",
          url: decryptedUrl,
          bitrate: 128000,
          container: "m4a",
          duration: cloudcast.audioLength
        }));
      }
    }
  }
  // Fallback to HLS DRM URL
  else if (cloudcast?.streamInfo?.hlsDrmUrl) {
    const playlistUrl = cloudcast.streamInfo.hlsDrmUrl.replace("hls_drm/", "hls/");

    sources.push(new HLSSource({
      name: "HLS",
      url: playlistUrl,
      duration: cloudcast.audioLength,
      language: "Unknown",
      original: true
    }));
  }
  else if (isPreview) {
    sources.push(new AudioUrlSource({
      name: "Audio",
      url: cloudcast.previewUrl,
      container: "mp3",
      duration: -1
    }));
  }

  if (!sources.length) {
    throw new UnavailableException("No sources available for cloudcast");
  }

    let description = cloudcast.description || "";

  if (!isMixCloudOwnChannelContent) {
    description += `\n\n<a href="${cloudcast.owner.url}">${cloudcast.owner.displayName}</a>`;
  } else {
    
    const creator = cloudcast?.creatorAttributions?.edges?.[0]?.node;
    
    if (creator) {
      description += `\n\n<a href="${creator.url}">${creator.displayName}</a>`;
    }
  }
  

  const platformVideo = cloudcastToPlatformVideo(cloudcast);

  // Extract tags from the cloudcast
  // const tags = extractTags(cloudcastInfo?.data?.cloudcast?.tagList);

  const platformVideoDetails = new PlatformVideoDetails({
    id: platformVideo.id,
    name: platformVideo.name,
    description: description,
    author: platformVideo.author,
    url: platformVideo.url,
    duration: platformVideo.duration,
    thumbnails: platformVideo.thumbnails,
    viewCount: platformVideo.viewCount,
    datetime: platformVideo.datetime,
    video: new UnMuxVideoSourceDescriptor([], sources),
    isLive: false,
  });

  platformVideoDetails.getContentRecommendations = function () {

    const moreFromOwner = makeGraphQLRequest("MoreFromOwnerQuery", {
      "lookup": {
        "username": username,
        "slug": slug
      }
    });

    const contentList = [];

    moreFromOwner?.data?.cloudcast?.moreFromOwner?.edges.forEach(edge => {
      if (edge.node.isPlayable) {
        contentList.push(cloudcastToPlatformVideo(edge.node));
      };
    });

    return new ContentPager(contentList, false);
  };

  if (isPreview) {
    bridge.toast('Preview - Subscribe to the artist to access the full content.')
  }

  return platformVideoDetails;
}

/**
 * Gets details for a Mixcloud live stream
 * @param {string} url URL of the live stream
 * @returns {PlatformVideoDetails} Details about the live stream
 */
function getContentDetailsLiveStream(url) {
  const liveMatch = url.match(REGEX.LIVE_STREAM_URL);

  if (!liveMatch || !liveMatch[1]) {
    throw new UnavailableException("Not able to access live stream information");
  }

  // Extract username from live stream URL using regex capture group
  const username = liveMatch[1];

  const liveStreamInfo = makeGraphQLRequest("MobileLiveStreamContainerQuery", {
    lookup: {
      username
    }
  });

  const liveStream = liveStreamInfo?.data?.user?.liveStream ?? null;

  if (!liveStream) {
    throw new UnavailableException("Not able to access live stream");
  }

  const sources = [];

  if (liveStream.hlsUrl) {
    sources.push(new HLSSource({
      name: "HLS",
      url: liveStream.hlsUrl,
      duration: 0
    }));
  }

  if (!sources.length) {
    throw new UnavailableException("No sources available for live stream");
  }

  const platformVideo = liveStreamToPlatformVideo(liveStream);

  const platformVideoDetails = new PlatformVideoDetails({
    id: platformVideo.id,
    name: platformVideo.name,
    description: liveStream.description,
    author: platformVideo.author,
    url: platformVideo.url,
    viewCount: platformVideo.viewCount,
    thumbnails: platformVideo.thumbnails,
    duration: 0,
    video: new VideoSourceDescriptor(sources),
    isLive: true,
  });

  // Add recommendations getter that fetches channel videos
  platformVideoDetails.getContentRecommendations = function () {
    // Get channel info to get the channel ID
    const channelInfo = makeGraphQLRequest("ChannelInfoByUsername", {
      "lookup": {
        "username": username
      }
    });

    if (!channelInfo?.data?.user?.id) {
      return new ContentPager([], false);
    }

    // Return a pager without the live stream in initialData (empty array instead)
    return new ChannelContentPager(channelInfo, username, []);
  };

  return platformVideoDetails;
}

// ====================== CONTENT CONVERSION HELPERS ======================


/**
 * Checks if a cloudcast is a limited preview (exclusive content without subscription)
 * 
 * @param {Object} cloudcast - The cloudcast object to check
 * @param {boolean} cloudcast.isExclusive - Whether the cloudcast is exclusive content
 * @param {Object} cloudcast.owner - The cloudcast owner object
 * @param {boolean} cloudcast.owner.isSubscribedTo - Whether user is subscribed to the owner
 * @returns {boolean} True if the cloudcast is a limited preview, false otherwise
 */
function isLimitedPreview(cloudcast) {
  return cloudcast.isExclusive && !cloudcast?.owner.isSubscribedTo;
}

/**
 * Converts a Mixcloud Cloudcast object to a PlatformVideo
 * 
 * @param {Object} cloudcast - The Mixcloud Cloudcast object from API
 * @returns {PlatformVideo} A PlatformVideo representation of the Cloudcast
 */
function cloudcastToPlatformVideo(cloudcast) {

  const thumbnailUrl = cloudcast.picture?.urlRoot ?
    createThumbnailUrl(cloudcast.picture.urlRoot, 600, 600) :
    '';

  // Format publish date - return 0 if no date available
  const publishDate = cloudcast.publishDate ? dateToUnixSeconds(cloudcast.publishDate) : 0;


  // Use channel username if provided (for channel content)
  // This handles cases where the content URL uses a different username than the owner
  const urlUsername = cloudcast._channelUsername || cloudcast.owner.username;

  let contentUrl = cloudcast.url || createMixcloudUrl(urlUsername, cloudcast.slug);

  const isPreview = isLimitedPreview(cloudcast);

  let name = cloudcast.name;
  let duration = cloudcast.audioLength || 0;

  if (isPreview) {
    name = '(preview) ' + name
    duration = 20;
  }
  else if(cloudcast.isExclusive) {
    name = '(exclusive) ' + name;
  }

  const isMixCloudOwnChannelContent = cloudcast._channelUsername === cloudcast.owner?.username;

  if (isMixCloudOwnChannelContent) {
    const tmpUrl = new URL(contentUrl);
    tmpUrl.searchParams.set('isMixCloudOwnChannelContent', isMixCloudOwnChannelContent);
    contentUrl = tmpUrl.toString();
  }

  // When viewing content in the creator's own channel, don't show creator attributions
  const creator = (isMixCloudOwnChannelContent || !cloudcast?.creatorAttributions?.edges?.[0]?.node) 
    ? cloudcast.owner 
    : cloudcast.creatorAttributions.edges[0].node;

    const authorThumbnailUrl = creator.picture?.urlRoot ?
    createThumbnailUrl(creator.picture.urlRoot, 272, 272) :
    '';

  return new PlatformVideo({
    id: new PlatformID(PLATFORM.name, cloudcast.id, config.id),
    name,
    thumbnails: new Thumbnails([new Thumbnail(thumbnailUrl)]),
    author: new PlatformAuthorLink(
      new PlatformID(PLATFORM.name, creator.id, config.id),
      creator.displayName,
      createMixcloudUrl(creator.username),
      authorThumbnailUrl || thumbnailUrl  // Use author thumbnail if available, otherwise use content thumbnail
    ),
    duration,
    viewCount: cloudcast.plays || 0,
    url: contentUrl,
    uploadDate: publishDate,
    isLive: false
  });
}

/**
 * Converts a Mixcloud LiveStream object to a PlatformVideo
 * 
 * @param {Object} stream - The Mixcloud LiveStream object from API
 * @returns {PlatformVideo} A PlatformVideo representation of the LiveStream
 */
function liveStreamToPlatformVideo(stream) {

  const thumbnailUrl = stream.thumbnailUrl ?
    createThumbnailUrl(stream.thumbnailUrl, 600, 355) :
    '';

  const authorThumbnailUrl = stream.owner.picture?.urlRoot ?
    createThumbnailUrl(stream.owner.picture.urlRoot, 272, 272) :
    '';

  const contentUrl = createMixcloudUrl(stream.owner.username, null, true);

  return new PlatformVideo({
    id: new PlatformID(PLATFORM.name, stream.id, config.id),
    name: stream.name,
    thumbnails: new Thumbnails([new Thumbnail(thumbnailUrl)]),
    author: new PlatformAuthorLink(
      new PlatformID(PLATFORM.name, stream.owner.id, config.id),
      stream.owner.displayName,
      createMixcloudUrl(stream.owner.username),
      authorThumbnailUrl || thumbnailUrl // Use author thumbnail if available, otherwise use content thumbnail
    ),
    duration: 0, // Live streams don't have a fixed duration
    viewCount: stream.plays,
    uploadDate: parseInt(new Date().getTime() / 1000),
    isLive: true,
    url: contentUrl
  });
}

// ====================== DATA FETCHING ======================

/**
 * Makes a GraphQL request to Mixcloud's API
 * @param {string} queryName Name of the query from GQL_QUERIES
 * @param {Object} variables Variables for the GraphQL query
 * @param {string} operationName Operation name for the GraphQL request
 * @param {boolean} useAuth Whether to use authentication
 * @returns {Object|null} Response body or null on error
 */
function makeGraphQLRequest(queryName, variables, operationName, useAuth = false) {
  try {
    if (!GQL_QUERIES[queryName]) {
      logError(`Unknown GraphQL query: ${queryName}`);
      return null;
    }

    if (!operationName) {
      operationName = queryName;
    }

    const queryInfo = {
      id: operationName,
      query: GQL_QUERIES[queryName],
      operationName: operationName,
      variables: variables
    };

    const response = makeRequestWithRetry('POST', URLS.GRAPHQL_URL, JSON.stringify(queryInfo), DEFAULT_REST_HEADERS, { parseResponse: true, useAuth: useAuth });

    if (!response.isOk) {
      logError(`GraphQL request failed. Query: ${queryName}, Status: ${response.code}`);
      return null;
    }

    return safeJsonParse(response.body);
  } catch (error) {
    logError(`Exception in makeGraphQLRequest: ${error.message}`);
    return null;
  }
}

/**
 * Makes an HTTP request with automatic retries and error handling
 * 
 * @param {string} method - HTTP method ('GET', 'POST', etc.)
 * @param {string} url - The URL to make the request to
 * @param {string|Object|null} body - Request body for POST/PUT requests
 * @param {Object} headers - HTTP headers for the request
 * @param {Object} options - Additional options
 * @param {boolean} [options.useAuth=false] - Whether to use authentication for the request
 * @param {boolean} [options.parseResponse=false] - Whether to parse the response as JSON
 * @param {number} [options.maxRetries=3] - Maximum number of retry attempts
 * @param {boolean} [options.throwOnError=false] - Whether to throw an exception on error
 * @returns {Object} - Response object with isOk, code, and body properties
 */
function makeRequestWithRetry(method, url, body = null, headers = {}, options = {}) {
  const {
    useAuth = false,
    parseResponse = false,
    maxRetries = 4,
    throwOnError = true
  } = options;

  let remainingAttempts = maxRetries + 1; // +1 for the initial attempt
  let lastResponse = null;
  let lastError = null;

  while (remainingAttempts > 0) {
    try {
      let response;

      // Make the appropriate type of request based on method
      if (method.toUpperCase() === 'GET') {
        // using object spread due to issue on desktop when modifying the object with response.parsedBody
        response = { ...http.GET(url, headers, useAuth) };
      } else if (method.toUpperCase() === 'POST') {
        // using object spread due to issue on desktop when modifying the object with response.parsedBody
        response = { ...http.POST(url, body, headers, useAuth) };
      } else {
        throw new ScriptException(`Unsupported HTTP method: ${method}`);
      }

      // If request was successful, optionally parse the response
      if (response.isOk && parseResponse) {
        try {
          response.parsedBody = JSON.parse(response.body);

          // Check for API error responses that might be in a 200 response
          if (response.parsedBody.errors) {
            const errorMsg = `API returned error: ${JSON.stringify(response.parsedBody.errors)}`;
            logError(errorMsg);
            if (throwOnError) {
              throw new ScriptException(errorMsg);
            }
          }
        } catch (parseError) {
          const errorMsg = `Failed to parse response as JSON: ${parseError.message}`;
          logError(errorMsg);
          if (throwOnError) {
            throw new ScriptException(errorMsg);
          }
        }
      }

      // Return on success
      if (response.isOk) {
        return response;
      }

      // Save the failed response and log the error
      lastResponse = response;
      const errorMsg = `${method} request to ${url} failed with status ${response.code}`;
      logError(`${errorMsg}. Attempts left: ${remainingAttempts - 1}`);

      if (throwOnError) {
        throw new ScriptException(errorMsg);
      }

      remainingAttempts--;

    } catch (error) {
      // Save the error and log it
      lastError = error;
      logError(`Exception in ${method} request to ${url}: ${error.message}. Attempts left: ${remainingAttempts - 1}`);

      if (throwOnError && remainingAttempts <= 1) {
        throw error;
      }

      remainingAttempts--;
    }
  }

  // All retry attempts have failed, return last response or construct a dummy response
  if (lastResponse) {
    return lastResponse;
  }

  // Create a dummy response if we don't have a real one
  return {
    isOk: false,
    code: 0,
    status: 0,
    body: JSON.stringify({ error: lastError ? lastError.message : `${method} request failed after all retry attempts` })
  };
}

// ====================== UTILITY FUNCTIONS ======================

/**
 * Checks if a string is a valid URL
 * @param {string} str The string to validate
 * @returns {boolean} True if the string is a valid URL, false otherwise
 */
function isValidURL(str) {
  try {
    return str?.startsWith('https://');
  } catch (_) {
    return false;
  }
}

/**
 * Creates a Mixcloud URL for a channel, mix, or live stream
 * @param {string} username The username for the URL
 * @param {string} [slug] Optional slug for a specific mix
 * @param {boolean} [isLive=false] Whether this is a live stream URL
 * @returns {string} The formatted Mixcloud URL
 */
function createMixcloudUrl(username, slug, isLive = false) {
  if (isLive) {
    return `https://www.mixcloud.com/live/${username}`;
  } else if (slug) {
    return `https://www.mixcloud.com/${username}/${slug}`;
  } else {
    return `https://www.mixcloud.com/${username}`;
  }
}

/**
 * Creates a thumbnail URL with specified dimensions
 * @param {string} urlRoot The base URL for the image
 * @param {number} width The desired width
 * @param {number} height The desired height
 * @returns {string} The formatted thumbnail URL
 */
function createThumbnailUrl(urlRoot, width = 600, height = 600) {
  return `https://thumbnailer.mixcloud.com/unsafe/${width}x${height}/${urlRoot}`;
}

/**
 * Extracts tag names from Mixcloud tagList object
 * @param {Object} tagList The tagList object from a Mixcloud API response
 * @returns {Array} Array of tag names
 */
function extractTags(tagList) {
  const tags = [];
  if (tagList?.edges?.length) {
    tagList.edges.forEach(tagEdge => {
      if (tagEdge.node?.name) {
        tags.push(tagEdge.node.name);
      }
    });
  }
  return tags;
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
    logError(`Date conversion error: ${error.message}`);
    return null;
  }
}

/**
 * Safely parses JSON and returns null on error
 * @param {string} jsonString JSON string to parse
 * @returns {Object|null} Parsed object or null on error
 */
function safeJsonParse(jsonString) {
  try {
    return JSON.parse(jsonString);
  } catch (error) {
    logError(`JSON parse error: ${error.message}`);
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
    logError(`Integer parse error: ${error.message}`);
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
    logError(`URL pattern matching error: ${error.message}`);
    return false;
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
 * Logs an error message
 * @param {string} message Error message
 */
function logError(message) {
  bridge.log(`[Mix Cloud Plugin] ${message}`);
}

/**
 * Shows a toast message to the user
 * @param {string} message Toast message
 */
function showToast(message) {
  bridge.toast(message);
}

/**
 * Capitalizes the first letter of a string
 * @param {string} string Input string
 * @returns {string} String with first letter capitalized
 */
function capitalizeFirstLetter(string) {
  if (!string) return string; // Handle empty or null strings
  return string.charAt(0).toUpperCase() + string.slice(1).toLowerCase();
}

/**
 * Converts seconds to HH:MM:SS format
 * @param {number} seconds Seconds to convert
 * @returns {string} Formatted time string
 */
function secondsToHMS(seconds) {
  // Calculate hours, minutes, and remaining seconds
  const hours = Math.floor(seconds / 3600);
  const remainingSecondsAfterHours = seconds % 3600;
  const minutes = Math.floor(remainingSecondsAfterHours / 60);
  const remainingSeconds = remainingSecondsAfterHours % 60;

  // Format each component to ensure two digits
  const formattedHours = String(hours).padStart(2, '0');
  const formattedMinutes = String(minutes).padStart(2, '0');
  const formattedSeconds = String(remainingSeconds).padStart(2, '0');

  // Return the formatted string
  return `${formattedHours}:${formattedMinutes}:${formattedSeconds}`;
}

/**
 * Checks if a URL is in the platform reserved list
 * @param {string} url URL to check
 * @returns {boolean} True if URL is reserved
 */
function isPlatformReservedUrl(url) {

  if (!url?.trim()) {
    return false;
  }

  // remove fragment
  url = url.split("#")[0];

  if (!url.endsWith("/")) {
    url += "/";
  }

  return RESERVED_URL_LIST.includes(url?.toLowerCase()?.trim());
}

// ====================== CUSTOM PAGER IMPLEMENTATIONS ======================

/**
 * Custom pager implementation for Mixcloud live streams
 */
class LiveStreamPager extends ContentPager {
  /**
   * Creates a new LiveStream pager
   */
  constructor() {
    super([], true);
    this.cursor = null;
    this.hasMore = true;
    if (typeof IS_TESTING !== 'undefined' && IS_TESTING) {
      this.nextPage();
    }
  }

  /**
   * Fetches the next page of results
   * @returns {ContentPager} Pager with the current page results
   */
  nextPage() {

    try {
      const variables = {
        "orderBy": "CURRENT_SPECTATORS",
        "tag": "",
        "count": 20
      };

      // Add cursor for pagination if we have one
      if (this.cursor) {
        variables.after = this.cursor;
      }

      const liveStreamInfo = makeGraphQLRequest("LiveListPageQuery", variables);

      // Check if we have valid data
      if (!liveStreamInfo?.data?.viewer?.live?.currentLiveStreams?.edges?.length) {
        this.hasMore = false;
        return this;
      }

      // Extract streams, pagination info
      const edges = liveStreamInfo.data.viewer.live.currentLiveStreams.edges;
      const pageInfo = liveStreamInfo.data.viewer.live.currentLiveStreams.pageInfo;

      // Update cursor and hasNextPage for pagination
      this.cursor = pageInfo.endCursor;
      this.hasMore = pageInfo.hasNextPage;

      // Map each stream to a PlatformVideo using the helper function
      const videos = edges.map(edge => liveStreamToPlatformVideo(edge.node));

      // Update results
      this.results = videos;
      return this;
    } catch (error) {
      logError(`Exception in LiveStreamPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }
}

/**
 * Custom pager implementation for channel content
 */
class ChannelContentPager extends ContentPager {
  /**
   * Creates a new channel content pager
   * @param {string} channelId The channel ID
   * @param {string} username The channel username
   * @param {Array} initialData Optional initial data
   */
  constructor(channelInfo, username, initialData = []) {
    super(initialData, true);

    this.channelInfo = channelInfo;

    this.channelId = channelInfo.data.user.id;
    this.username = username;
    this.cursor = null;
    this.hasMore = true;

    if(!initialData.length || IS_TESTING) {
      this.nextPage();
    }
  }

  /**
   * Fetches the next page of results
   * @returns {ContentPager} Pager with the current page results
   */
  nextPage() {

    try {
      const variables = {
        audioTypes: ["SHOW"],
        count: 20,
        id: this.channelId,
        orderBy: "LATEST",
        cursor: this.cursor
      };

      const channelInfo = makeGraphQLRequest("UserUploadsPagePaginationQuery", variables, null, true);

      // Check if we have valid data
      if (!channelInfo?.data?.node?.uploads?.edges?.length) {
        this.hasMore = false;
        return this;
      }

      // Extract uploads, pagination info
      const edges = channelInfo.data.node.uploads.edges || [];
      const pageInfo = channelInfo.data.node.uploads.pageInfo || {};

      // Update cursor and hasNextPage for pagination
      this.cursor = pageInfo.endCursor;
      this.hasMore = pageInfo.hasNextPage;

      this.results = edges
        .filter(edge => edge.node.isPlayable)
        .map(edge => {
          // Ensure the cloudcast has the correct username for URL generation
          // (Use channel username if needed)
          const cloudcast = edge.node;          

          cloudcast._channelUsername = this.username;

          if (this.username && this.username !== cloudcast.owner.username) {
            
            // Store original username for URL generation
            
            cloudcast.owner = this.channelInfo.data.user;

          }

          return cloudcastToPlatformVideo(cloudcast);
        });

      return this;

    } catch (error) {
      logError(`Exception in ChannelContentPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }
}

/**
 * Custom pager implementation for search results
 */
class SearchPager extends ContentPager {
  /**
   * Creates a new search pager
   * @param {string} query Search query
   */
  constructor(query) {
    super([], true);
    this.query = query;
    this.cursor = null;
    this.hasMore = true;
    this.nextPage();
  }

  /**
   * Fetches the next page of results
   * @returns {ContentPager} Pager with the current page results
   */
  nextPage() {

    try {
      const variables = {
        "count": 20,
        "term": this.query,
        "cursor": this.cursor
      };

      const searchResults = makeGraphQLRequest("SearchResultsCloudcastsQuery", variables);

      if (!searchResults?.data?.viewer?.search?.searchQuery?.cloudcasts?.edges?.length) {
        this.hasMore = false;
        return this;
      }

      // Update pagination information
      const pageInfo = searchResults.data.viewer.search.searchQuery.cloudcasts.pageInfo;
      this.hasMore = pageInfo.hasNextPage;
      this.cursor = pageInfo.endCursor;

      // Map results to platform videos using the helper function
      const videos = searchResults.data.viewer.search.searchQuery.cloudcasts.edges
        .filter(edge => edge.node.isPlayable)
        .map(edge => cloudcastToPlatformVideo(edge.node));

      // Update results
      this.results = videos;

      return this;
    } catch (error) {
      logError(`Exception in SearchPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }
}

/**
 * Custom pager implementation for channel search results
 */
class ChannelSearchPager extends ContentPager {
  /**
   * Creates a new channel search pager
   * @param {string} query Search query
   */
  constructor(query) {
    super([], true);
    this.query = query;
    this.cursor = null;
    this.hasMore = true;
    this.nextPage();
  }

  /**
   * Fetches the next page of results
   * @returns {ContentPager} Pager with the current page results
   */
  nextPage() {

    try {
      const variables = {
        "count": 20,
        "dateJoinedAfter": null,
        "isUploader": null,
        "term": this.query,
        "cursor": this.cursor
      };

      const searchResults = makeGraphQLRequest("SearchResultsUsersQuery", variables);

      if (!searchResults?.data?.viewer?.search?.searchQuery?.users?.edges?.length) {
        this.hasMore = false;
        return this;
      }

      // Update pagination information
      const pageInfo = searchResults.data.viewer.search.searchQuery.users.pageInfo;
      this.hasMore = pageInfo.hasNextPage;
      this.cursor = pageInfo.endCursor;

      // Map results to platform channels
      const channels = searchResults.data.viewer.search.searchQuery.users.edges.map(edge => {
        const channel = edge.node;

        return new PlatformChannel({
          id: new PlatformID(PLATFORM.name, channel.id, config.id),
          name: channel.displayName,
          description: channel.biog,
          url: createMixcloudUrl(channel.username),
          thumbnail: channel.picture?.urlRoot ?
            createThumbnailUrl(channel.picture.urlRoot, 272, 272) : null,
          banner: channel.coverPicture?.urlRoot ?
            createThumbnailUrl(channel.coverPicture.urlRoot, 1460, 370) : null,
          subscribers: channel.followers?.totalCount || 0,
          links: {}
        });
      });

      // Update results
      this.results = channels;

      return this;
    } catch (error) {
      logError(`Exception in ChannelSearchPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }
}


/**
 * Custom pager implementation for comments
 */
class MixcloudCommentPager extends CommentPager {
  /**
   * Creates a new comment pager
   * @param {string} url Content URL
   */
  constructor(url) {
    super([], true);
    this.url = url;
    this.hasMore = true;
    this.commentId = null;
    this.nextPage();
  }

  /**
   * Fetches the next page of results
   * @returns {CommentPager} Pager with the current page results
   */
  nextPage() {

    try {
      const mixMatch = this.url.match(REGEX.CONTENT_DETAILS_URL);

      if (!mixMatch || !mixMatch[1] || !mixMatch[2]) {
        throw new UnavailableException("Not able to access comments");
      }

      // Extract username and slug using regex capture groups
      const username = mixMatch[1];
      const slug = mixMatch[2];

      const variables = {
        lookup: { username, slug }
      };

      // Add comment ID for pagination if we have one
      if (this.commentId) {
        variables.commentId = this.commentId;
      }

      const commentsResponse = makeGraphQLRequest("CloudcastCommentsQuery", variables);

      if (!commentsResponse?.data?.cloudcast?.comments?.edges?.length) {
        this.hasMore = false;
        return this;
      }

      // Extract comments and pagination info
      const edges = commentsResponse.data.cloudcast.comments.edges;
      const pageInfo = commentsResponse.data.cloudcast.comments.pageInfo;

      // Update pagination information
      this.hasMore = pageInfo.hasNextPage;
      if (edges.length > 0) {
        this.commentId = edges[edges.length - 1].node.id;
      }

      const comments = edges.map(edge => this.transformComment(edge.node));
      this.results = comments;
      return this;
    } catch (error) {
      logError(`Exception in MixcloudCommentPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }

  transformComment(comment) {
    const user = comment.user;

    // Create replies pager for nested comments if they exist
    let replies = [];
    if (comment?.children?.edges?.length) {
      replies = comment.children.edges.map(childEdge => {
        const childComment = childEdge.node;
        const childUser = childComment.user;

        return new Comment({
          contextUrl: this.url,
          author: new PlatformAuthorLink(
            new PlatformID(PLATFORM.name, childUser.id, config.id),
            childUser.displayName,
            createMixcloudUrl(childUser.username),
            childUser.picture?.urlRoot ? createThumbnailUrl(childUser.picture.urlRoot, 272, 272) : null
          ),
          message: childComment.comment,
          rating: new RatingLikes(childComment.likeCount),
          date: dateToUnixSeconds(childComment.created),
          replyCount: 0
        });
      });
    }

    let message = comment.comment;
    if (comment.audioPosition) {
      message = `${secondsToHMS(comment.audioPosition)} - ${message}`;
    }

    // Create main comment
    const platformComment = new Comment({
      contextUrl: this.url,
      author: new PlatformAuthorLink(
        new PlatformID(PLATFORM.name, user.id, config.id),
        user.displayName,
        createMixcloudUrl(user.username),
        user.picture?.urlRoot ? createThumbnailUrl(user.picture.urlRoot, 272, 272) : null
      ),
      message: message,
      date: dateToUnixSeconds(comment.created),
      replyCount: comment?.children?.edges?.length ?? 0,
      rating: new RatingLikes(comment.likeCount),
    });

    // Add replies if any
    if (replies.length > 0) {
      platformComment.getReplies = function () {
        return new CommentPager(replies, false);
      };
    }

    return platformComment;
  }
}

/**
 * Custom pager implementation for user playlists
 */
class PlaylistsPager extends ContentPager {
  constructor(username) {
    super([], true);
    this.username = username;
    this.cursor = null;
    this.hasMore = true;
    this.nextPage();
  }

  nextPage() {

    try {
      const playlists = [];
      
      // Only fetch on first page
      if (!this.cursor) {
        // Get user info for thumbnails, author, and navigation items
        const userInfo = makeGraphQLRequest("ChannelInfoByUsername", {
          lookup: { username: this.username }
        });
        
        let playlistThumbnail = null;
        let author = null;
        
        if (userInfo?.data?.user) {
          const user = userInfo.data.user;
          // Use banner if available, otherwise use channel thumbnail
          if (user.coverPicture?.urlRoot) {
            playlistThumbnail = createThumbnailUrl(user.coverPicture.urlRoot, 1460, 370);
          } else if (user.picture?.urlRoot) {
            playlistThumbnail = createThumbnailUrl(user.picture.urlRoot, 272, 272);
          }
          
          // Create author information
          author = new PlatformAuthorLink(
            new PlatformID(PLATFORM.name, user.id, config.id),
            user.displayName,
            createMixcloudUrl(this.username),
            user.picture?.urlRoot ? createThumbnailUrl(user.picture.urlRoot, 272, 272) : null
          );
          
          // Add channel playlists from profileNavigation
          if (user.profileNavigation?.menuItems) {
            user.profileNavigation.menuItems.forEach(item => {
              if (item.__typename === 'PlaylistNavigationItem' && item.playlist) {
                playlists.push(new PlatformPlaylist({
                  id: new PlatformID(PLATFORM.name, item.playlist.id, config.id),
                  name: item.playlist.name,
                  url: `https://www.mixcloud.com/${this.username}/playlists/${item.playlist.slug}/`,
                  thumbnail: playlistThumbnail,
                  author: author,
                  videoCount: item.count || 0
                }));
              }
            });
          }
        }
        
        const specialPlaylists = [
          { id: `${this.username}_favorites`, name: "Favorites", url: `${this.username}/favorites` },
          { id: `${this.username}_history`, name: "Listening History", url: `${this.username}/listens` },
          { id: `${this.username}_reposts`, name: "Reposts", url: `${this.username}/reposts` },
          { id: `${this.username}_tracks`, name: "Tracks", url: `${this.username}/tracks` }
        ];

        specialPlaylists.forEach(playlist => {
        playlists.unshift(new PlatformPlaylist({
            id: new PlatformID(PLATFORM.name, playlist.id, config.id),
            name: playlist.name,
            url: createMixcloudUrl(playlist.url),
          thumbnail: playlistThumbnail,
          author: author,
            videoCount: -1
          }));
        });
      }

      if (!this.cursor) {
        this.hasMore = false;
      }

      this.results = playlists;
      return this;
    } catch (error) {
      logError(`Exception in PlaylistsPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }
}

/**
 * Custom pager implementation for playlist contents
 */
class PlaylistContentPager extends ContentPager {
  constructor(username, playlistSlug) {
    super([], true);
    this.username = username;
    this.playlistSlug = playlistSlug;
    this.cursor = null;
    this.hasMore = true;
    this.nextPage();
  }

  nextPage() {

    try {
      const variables = {
        lookup: {
          username: this.username,
          slug: this.playlistSlug
        },
        count: 20,
        cursor: this.cursor
      };

      const response = makeGraphQLRequest("PlaylistContentsQuery", variables);

      if (!response?.data?.playlist?.items?.edges?.length) {
        this.hasMore = false;
        return this;
      }

      const pageInfo = response.data.playlist.items.pageInfo;
      this.hasMore = pageInfo.hasNextPage;
      this.cursor = pageInfo.endCursor;

      const videos = response.data.playlist.items.edges
        .filter(edge => edge.node.cloudcast && edge.node.cloudcast.isPlayable)
        .map(edge => cloudcastToPlatformVideo(edge.node.cloudcast));

      this.results = videos;
      return this;
    } catch (error) {
      logError(`Exception in PlaylistContentPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }
}

/**
 * Custom pager implementation for user favorites
 */
class FavoritesPager extends ContentPager {
  constructor(username) {
    super([], true);
    this.username = username;
    this.cursor = null;
    this.hasMore = true;
    this.nextPage();
  }

  nextPage() {
    try {
      const variables = {
        lookup: { username: this.username },
        count: 20,
        cursor: this.cursor
      };

      const response = makeGraphQLRequest("UserFavoritesQuery", variables);

      if (!response?.data?.user?.favorites?.edges?.length) {
        this.hasMore = false;
        return this;
      }

      const pageInfo = response.data.user.favorites.pageInfo;
      this.hasMore = pageInfo.hasNextPage;
      this.cursor = pageInfo.endCursor;

      const videos = response.data.user.favorites.edges
        .filter(edge => edge.node.isPlayable)
        .map(edge => cloudcastToPlatformVideo(edge.node));

      this.results = videos;
      return this;
    } catch (error) {
      logError(`Exception in FavoritesPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }
}

/**
 * Custom pager implementation for user listening history
 */
class HistoryPager extends ContentPager {
  constructor(username) {
    super([], true);
    this.username = username;
    this.cursor = null;
    this.hasMore = true;
    this.nextPage();
  }

  nextPage() {

    try {
      const variables = {
        lookup: { username: this.username },
        count: 20,
        cursor: this.cursor
      };

      const response = makeGraphQLRequest("UserListeningHistoryQuery", variables);

      if (!response?.data?.user?.listeningHistory?.edges?.length) {
        this.hasMore = false;
        return this;
      }

      const pageInfo = response.data.user.listeningHistory.pageInfo;
      this.hasMore = pageInfo.hasNextPage;
      this.cursor = pageInfo.endCursor;

      const videos = response.data.user.listeningHistory.edges
        .filter(edge => edge.node.cloudcast && edge.node.cloudcast.isPlayable)
        .map(edge => cloudcastToPlatformVideo(edge.node.cloudcast));

      this.results = videos;
      return this;
    } catch (error) {
      logError(`Exception in HistoryPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }
}

/**
 * Custom pager implementation for listen later
 */
class ListenLaterPager extends ContentPager {
  constructor() {
    super([], true);
    this.cursor = null;
    this.hasMore = true;
    this.nextPage();
  }

  nextPage() {

    try {
      
      // Add cursor for pagination if we have one
      const variables = {
        count: 20,
        cursor: this.cursor
      };

      const response = makeGraphQLRequest("ListenLaterQuery", variables, null, true);

      if (!response?.data?.viewer?.listenLaters?.edges?.length) {
        this.hasMore = false;
        return this;
      }

      const pageInfo = response.data.viewer.listenLaters.pageInfo;
      this.hasMore = pageInfo.hasNextPage;
      this.cursor = pageInfo.endCursor;

      const videos = response.data.viewer.listenLaters.edges
        .filter(edge => edge.node && edge.node.isPlayable)
        .map(edge => cloudcastToPlatformVideo(edge.node));

      this.results = videos;
      return this;
    } catch (error) {
      logError(`Exception in ListenLaterPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }
}

/**
 * Custom pager implementation for user reposts
 */
class RepostsPager extends ContentPager {
  constructor(username) {
    super([], true);
    this.username = username;
    this.cursor = null;
    this.hasMore = true;
    this.nextPage();
  }

  nextPage() {

    try {
      const variables = {
        lookup: { username: this.username },
        count: 20,
        cursor: this.cursor
      };

      const response = makeGraphQLRequest("UserRepostsQuery", variables);

      if (!response?.data?.user?.reposted?.edges?.length) {
        this.hasMore = false;
        return this;
      }

      const pageInfo = response.data.user.reposted.pageInfo;
      this.hasMore = pageInfo.hasNextPage;
      this.cursor = pageInfo.endCursor;

      const videos = response.data.user.reposted.edges
        .filter(edge => edge.node.isPlayable)
        .map(edge => cloudcastToPlatformVideo(edge.node));

      this.results = videos;
      return this;
    } catch (error) {
      logError(`Exception in RepostsPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }
}

/**
 * Custom pager implementation for user tracks
 */
class TracksPager extends ContentPager {
  constructor(username) {
    super([], true);
    this.username = username;
    this.cursor = null;
    this.hasMore = true;
    this.nextPage();
  }

  nextPage() {

    try {
      const variables = {
        lookup: { username: this.username },
        count: 20,
        cursor: this.cursor
      };

      const response = makeGraphQLRequest("UserTracksQuery", variables);

      if (!response?.data?.user?.uploads?.edges?.length) {
        this.hasMore = false;
        return this;
      }

      const pageInfo = response.data.user.uploads.pageInfo;
      this.hasMore = pageInfo.hasNextPage;
      this.cursor = pageInfo.endCursor;

      const videos = response.data.user.uploads.edges
        .filter(edge => edge.node.isPlayable)
        .map(edge => cloudcastToPlatformVideo(edge.node));

      this.results = videos;
      return this;
    } catch (error) {
      logError(`Exception in TracksPager.nextPage: ${error.message}`);
      this.hasMore = false;
      return this;
    }
  }
}

// ====================== GRAPHQL QUERIES ======================

const GQL_QUERIES = {
  PlaylistContentsQuery: `
query PlaylistContentsQuery(
  $lookup: PlaylistLookup!
  $count: Int = 20
  $cursor: String
) {
  playlist: playlistLookup(lookup: $lookup) {
    id
    name
    description
    items(first: $count, after: $cursor) {
      edges {
        node {
          cloudcast {
            id
            name
            slug
            url
            publishDate
            audioLength
            plays
            owner {
              id
              username
              displayName
              picture {
                urlRoot
              }
            }
            picture {
              urlRoot
            }
            isExclusive
            isExclusivePreviewOnly
            isPlayable
          }
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
}`,

  PlaylistDetailsQuery: `
query PlaylistDetailsQuery(
  $lookup: PlaylistLookup!
) {
  playlist: playlistLookup(lookup: $lookup) {
    id
    name
    description
    items {
      totalCount
    }
    picture {
      urlRoot
    }
    owner {
      id
      username
      displayName
      picture {
        urlRoot
      }
    }
  }
  viewer {
    id
  }
}`,

  UserFavoritesQuery: `
query UserFavoritesQuery(
  $lookup: UserLookup!
  $count: Int = 20
  $cursor: String
) {
  user: userLookup(lookup: $lookup) {
    id
    displayName
    favorites(first: $count, after: $cursor) {
      edges {
        node {
          id
          name
          slug
          url
          publishDate
          audioLength
          plays
          owner {
            id
            username
            displayName
            picture {
              urlRoot
            }
          }
          picture {
            urlRoot
          }
          isExclusive
          isExclusivePreviewOnly
          isPlayable
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
}`,

  UserListeningHistoryQuery: `
query UserListeningHistoryQuery(
  $lookup: UserLookup!
  $count: Int = 20
  $cursor: String
) {
  user: userLookup(lookup: $lookup) {
    id
    displayName
    listeningHistory(first: $count, after: $cursor) {
      edges {
        node {
          id
          cloudcast {
            id
            name
            slug
            url
            publishDate
            audioLength
            plays
            owner {
              id
              username
              displayName
              picture {
                urlRoot
              }
            }
            picture {
              urlRoot
            }
            isExclusive
            isExclusivePreviewOnly
            isPlayable
          }
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
}`,

  ListenLaterQuery: `
query ListenLaterQuery(
  $cursor: String
) {
  viewer {
    id
    listenLaters(first: 10, after: $cursor) {
      edges {
        cursor
        node {
          __typename
          id
          name
          slug
          url
          publishDate
          audioLength
          plays
          owner {
            id
            username
            displayName
            picture {
              urlRoot
            }
          }
          picture {
            urlRoot
          }
          isExclusive
          isExclusivePreviewOnly
          isPlayable
        }
      }
      pageInfo {
        endCursor
        hasNextPage
      }
    }
  }
}`,

  UserRepostsQuery: `
query UserRepostsQuery(
  $lookup: UserLookup!
  $count: Int = 20
  $cursor: String
) {
  user: userLookup(lookup: $lookup) {
    id
    displayName
    reposted(first: $count, after: $cursor, audioTypes: [SHOW, TRACK]) {
      edges {
        node {
          id
          name
          slug
          url
          publishDate
          audioLength
          plays
          owner {
            id
            username
            displayName
            picture {
              urlRoot
            }
          }
          picture {
            urlRoot
          }
          isExclusive
          isExclusivePreviewOnly
          isPlayable
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
}`,

  UserTracksQuery: `
query UserTracksQuery(
  $lookup: UserLookup!
  $count: Int = 20
  $cursor: String
) {
  user: userLookup(lookup: $lookup) {
    id
    displayName
    uploads(first: $count, after: $cursor, audioTypes: [TRACK], isPublic: true) {
      edges {
        node {
          id
          name
          slug
          url
          publishDate
          audioLength
          plays
          owner {
            id
            username
            displayName
            picture {
              urlRoot
            }
          }
          picture {
            urlRoot
          }
          isExclusive
          isExclusivePreviewOnly
          isPlayable
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
}`,

  MobileLiveStreamContainerQuery: `
query MobileLiveStreamContainerQuery($lookup: UserLookup!) {
	viewer {
		...MobileLiveStream_viewer
		id
	}
	user: userLookup(lookup: $lookup) {
		liveStream(isPublic: false) {
			...MobileLiveStream_liveStream
			id
		}
		...useLiveSubscriptions_user
		id
	}
}

fragment ImageUser_user on User {
	picture {
		urlRoot
		primaryColor
	}
	displayName
}

fragment LiveVideoElement_liveStream on LiveStream {
	id
	name
	streamStatus
	hlsUrl
	thumbnailUrl
	
}

fragment LiveVideoPlayer_liveStream on LiveStream {
	streamStatus
	...LiveVideoElement_liveStream
	...StreamStatusOverlay_liveStream
}

fragment MobileFollowButtonContainer_user on User {
	id
	isFollowing
}

fragment MobileFollowButtonContainer_viewer on Viewer {
	me {
		id
	}
}

fragment MobileLiveStreamMetadata_liveStream on LiveStream {
	name
	description
	plays
	currentSpectators
	isUnlisted
	owner {
		username
		displayName
		isSelect
		isViewer
		...ImageUser_user
		...MobileFollowButtonContainer_user
		id
	}
	secondaryTags {
		name
		id
	}
}

fragment MobileLiveStreamMetadata_viewer on Viewer {
	me {
		isStaff
		id
	}
	...MobileFollowButtonContainer_viewer
}

fragment MobileLiveStream_liveStream on LiveStream {
	slug
	streamStatus
	owner {
		isViewer
		id
	}
	...LiveVideoPlayer_liveStream
	...MobileLiveStreamMetadata_liveStream
}

fragment MobileLiveStream_viewer on Viewer {
	me {
		__typename
		id
	}
	...MobileLiveStreamMetadata_viewer
}

fragment StreamStatusOverlay_liveStream on LiveStream {
	streamStatus
	owner {
		isViewer
		id
	}
}

fragment useLiveSubscriptions_user on User {
	id
	liveStream(isPublic: false) {
		id
	}
}`,

  LiveListPageQuery: `
query LiveListPageQuery($orderBy: LiveStreamsOrderByEnum!, $tag: String, $after: String) {
	viewer {
		live {
			id
			currentLiveStreams(first: 20, orderBy: $orderBy, tag: $tag, after: $after) {
				edges {
					node {
						name
						plays
						thumbnailUrl
						owner {
							username
							displayName
							picture {
								urlRoot
								primaryColor
							}
							id
						}
						tagList(first: 10, country: "GLOBAL") {
							edges {
								node {
									name
									slug
									id
								}
							}
						}
						id
						__typename
					}
					cursor
				}
				totalCount
				pageInfo {
					endCursor
					hasNextPage
				}
			}
		}
		id
	}
}`,

  UserUploadsPagePaginationQuery: `
query UserUploadsPagePaginationQuery(
	$audioTypes: [AudioTypeEnum] = [SHOW]
	$count: Int = 20
	$cursor: String
	$onlyAttributedTo: ID
	$orderBy: CloudcastOrderByEnum = LATEST
	$id: ID!
) {
	node(id: $id) {
		__typename
		...UserUploadsPage_user_2uzeCj
		id
	}
}

fragment Actions_cloudcast on Cloudcast {
	audioType
	...CardFavoriteButton_cloudcast
	...CardShareButton_cloudcast
	...CardAddToButton_cloudcast
	...CardHighlightButton_cloudcast
	...CardBoostButton_cloudcast
	...CardStats_cloudcast
	...CardMoreOptions_cloudcast
	...CardPlayButton_cloudcast
	...DisappearingTags_taggableInterface
}

fragment Artwork_cloudcast on Cloudcast {
	slug
  url
	audioQuality
	qualityScore
	listenerMinutes
	isPublic
	owner {
		username
		id
    picture {
      urlRoot
    }
	}
	...ImageCloudcast_cloudcast
}

fragment AudioCard_cloudcast on Cloudcast {
	audioType
	isAwaitingAudio
	isDraft
	...CardDetails_cloudcast
	...DisappearingTags_taggableInterface
	...StatusOrActions_cloudcast
	...Artwork_cloudcast
	...Waveform_cloudcast
}

fragment CardAddToButton_cloudcast on Cloudcast {
	id
	isUnlisted
	isPublic
}

fragment CardBoostButton_cloudcast on Cloudcast {
	id
	isPublic
	owner {
		id
		isViewer
	}
}

fragment CardDetails_cloudcast on Cloudcast {
	slug
	name
	isDraft
	isExclusive
	publishDate
	audioType
	owner {
		username
		id
	}
	creatorAttributions(first: 2) {
		totalCount
	}
	...Owners_cloudcast
	...CardPlayButton_cloudcast
	...ExclusiveCloudcastBadgeContainer_cloudcast
}

fragment CardFavoriteButton_cloudcast on Cloudcast {
	id
	isFavorited
	isPublic
	hiddenStats
	favorites {
		totalCount
	}
	slug
	owner {
		id
		isFollowing
		username
		displayName
	}
}

fragment CardHighlightButton_cloudcast on Cloudcast {
	id
	isPublic
	isHighlighted
	owner {
		isViewer
		id
	}
}

fragment CardMoreOptions_cloudcast on Cloudcast {
	id
	isPublic
	slug
	isUnlisted
	isScheduled
	isDraft
	audioType
	isDisabledCopyright
	viewerAttribution {
		status
		id
	}
	viewerAttributionRequest {
		id
	}
	creatorAttributions(first: 2) {
		totalCount
	}
	owner {
		id
		username
		isViewer
		viewerIsAffiliate
		displayName
	}
}

fragment CardPlayButton_cloudcast on Cloudcast {
	id
	restrictedReason
	proportionListened
	owner {
		isSubscribedTo
		isViewer
		id
	}
	isAwaitingAudio
	isDraft
	isPlayable
	currentPosition
	...playbackPositionCloudcastFragment
	previewUrl
	isExclusive
	isDisabledCopyright
	...CardStaticPlayButton_cloudcast
	...useAudioPreview_cloudcast
	...usePlayWithRestrictions_cloudcast
}

fragment CardShareButton_cloudcast on Cloudcast {
	id
	isUnlisted
	isPublic
	slug
	description
	audioType
	picture {
		urlRoot
	}
	owner {
		displayName
		isViewer
		username
		id
	}
}

fragment CardStaticPlayButton_cloudcast on Cloudcast {
	owner {
		username
		id
	}
	slug
	id
	restrictedReason
}

fragment CardStats_cloudcast on Cloudcast {
	isDraft
	hiddenStats
	plays
	audioLength
}

fragment CopyrightSupport_cloudcast on Cloudcast {
	slug
	name
	owner {
		username
		id
	}
}

fragment DisappearingTags_taggableInterface on TaggableInterface {
	__isTaggableInterface: __typename
	tagList(first: 10, country: "GLOBAL") {
		edges {
			node {
				name
				slug
				id
			}
		}
	}
}

fragment Duration_cloudcast on Cloudcast {
	audioType
	audioLength
	picture {
		isLight
		primaryColor
		darkPrimaryColor: primaryColor(darken: 60)
	}
}

fragment ExclusiveCloudcastBadgeContainer_cloudcast on Cloudcast {
	isExclusive
	isExclusivePreviewOnly
	slug
	id
	owner {
		username
		id
	}
}

fragment Hovercard_user on User {
	id
}

fragment ImageCloudcast_cloudcast on Cloudcast {
	name
	picture {
		urlRoot
		primaryColor
	}
}

fragment Owners_cloudcast on Cloudcast {
	owner {
		...Username_user
		id
	}
	creatorAttributions(first: 2) {
		totalCount
		edges {
			node {
				...Username_user
				id
        url
        picture {
          urlRoot
        }
			}
		}
	}
}

fragment ShareAudioCardList_user on User {
	biog
	username
	displayName
	id
	isUploader
	picture {
		urlRoot
	}
}

fragment StatusOrActions_cloudcast on Cloudcast {
	slug
	publishDate
	audioType
	isAwaitingAudio
	isDraft
	isScheduled
	isLiveRecording
	isDisabledCopyright
	restrictedReason
	owner {
		username
		isViewer
		id
	}
	...CopyrightSupport_cloudcast
	...Actions_cloudcast
	...CardMoreOptions_cloudcast
}

fragment UserBadge_user on User {
	hasProFeatures
	isStaff
	hasPremiumFeatures
}

fragment UserUploadsPage_user_2uzeCj on User {
	id
	displayName
	username
	isViewer
	hasProFeatures
	viewerIsAffiliate
	...ShareAudioCardList_user
	uploads(
		first: $count
		isPublic: true
		after: $cursor
		orderBy: $orderBy
		audioTypes: $audioTypes
		onlyAttributedTo: $onlyAttributedTo
	) {
		edges {
			node {
				...AudioCard_cloudcast
				id
				__typename
			}
			cursor
		}
		pageInfo {
			endCursor
			hasNextPage
		}
	}
}

fragment Username_user on User {
	username
	displayName
	...UserBadge_user
	...Hovercard_user
}

fragment WaveformDataFragment on Cloudcast {
	waveformUrl
}

fragment WaveformPath_cloudcast_3lzM42 on Cloudcast {
	id
	audioLength
	...isNowPlayingCloudcastFragment
	...playbackPositionCloudcastFragment
	isPlayable
	restrictedReason
	seekRestriction
	...WaveformDataFragment
	...hasAudioCloudcastFragment_3lzM42
	...usePlayerSlider_cloudcast_4vCdM7
}

fragment Waveform_cloudcast on Cloudcast {
	id
	owner {
		username
		id
	}
	slug
	...WaveformPath_cloudcast_3lzM42
	...Duration_cloudcast
}

fragment hasAudioCloudcastFragment_3lzM42 on Cloudcast {
	streamInfo(timestamper: false) {
		__typename
	}
}

fragment isNowPlayingCloudcastFragment on Cloudcast {
	id
}

fragment playbackPositionCloudcastFragment on Cloudcast {
	id
	currentPosition
}

fragment useAudioPreview_cloudcast on Cloudcast {
	id
	previewUrl
}

fragment useExclusiveCloudcastModal_cloudcast on Cloudcast {
	id
	isExclusive
	owner {
		username
		id
	}
}

fragment useExclusivePreviewModal_cloudcast on Cloudcast {
	id
	isExclusivePreviewOnly
	owner {
		username
		id
	}
}

fragment usePlayWithRestrictions_cloudcast on Cloudcast {
	id
	owner {
		isSubscribedTo
		isViewer
		id
	}
	isPlayable
	...hasAudioCloudcastFragment_3lzM42
	currentPosition
	repeatPlayAmount
	hasPlayCompleted
	seekRestriction
	isExclusive
	...useExclusivePreviewModal_cloudcast
	...useExclusiveCloudcastModal_cloudcast
}

fragment usePlayerSlider_cloudcast_4vCdM7 on Cloudcast {
	seekRestriction
	audioLength
	isExclusive
	owner {
		isSubscribedTo
		isViewer
		id
	}
}`,

  ChannelInfoByUsername: `
query ChannelInfoByUsername($lookup: UserLookup!) {
	user: userLookup(lookup: $lookup) {
		id
		displayName
		biog
		isViewer
		city
		country
		hasCoverPicture
		hasPicture
		url
		liveStream {
			id
			name
			streamStatus
			hlsUrl
			thumbnailUrl
			slug
			owner {
				isViewer
				id
			}
		}
		picture {
			urlRoot
		}
		coverPicture {
			urlRoot
		}
		socialMediaLinks {
			platform
			deeplink
		}
		customLinks: customProfileLinks {
			title
			url
		}
		followers {
			totalCount
		}
		profileNavigation {
			menuItems {
				__typename
				... on PlaylistNavigationItem {
					playlist {
						id
						name
						slug
					}
					count
				}
			}
		}
	}
}`,

  CloudcastLookup: `
query CloudcastLookup($lookup: CloudcastLookup!) {
	cloudcast: cloudcastLookup(lookup: $lookup) {
		id
    name
		slug
		isUnlisted
		isShortLength
    isExclusive
		audioType
		audioLength
		description
		url
    previewUrl
    plays
    publishDate
		streamInfo(timestamper: false) {
			url
			hlsUrl
			dashDrmUrl
			hlsDrmUrl
		}
		owner {
      isSubscribedTo
			hasProFeatures
			username
			isBranded
			displayName
			isViewer
			id
			url
			picture {
				urlRoot
			}
		}
		picture {
			urlRoot
		}
		viewerAttribution {
			id
		}
    creatorAttributions(first: 2) {
      edges {
        node {
          id
          url
          picture {
            urlRoot
          }
          displayName
          followers {
            totalCount
          }
          username
          hasProFeatures
          isStaff
          hasPremiumFeatures
        }
      }
    }
	}
}`,

  SearchResultsUsersQuery: `
query SearchResultsUsersQuery(
	$count: Int = 5
	$cursor: String
	$dateJoinedAfter: DateJoinedAfterFilter
	$isUploader: IsUploaderFilter
	$term: String!
) {
	viewer {
		...SearchResultsUsers_viewer_4die2G
		id
	}
}
fragment Hovercard_user on User {
	id
}
fragment ImageUser_user on User {
	picture {
		urlRoot
		primaryColor
	}
	displayName
}
fragment ItemUserCardList_user on User {
	isViewer
	...RebrandUserCard_user
}
fragment ItemUserCardList_viewer on Viewer {
	...RebrandUserCard_viewer
}
fragment RebrandFollowButton_user on User {
	id
	isFollowed
	isFollowing
	isViewer
	username
	displayName
}
fragment RebrandFollowButton_viewer on Viewer {
	me {
		id
	}
}
fragment RebrandUserCardDisplayName_user on User {
	displayName
	username
}
fragment RebrandUserCardUserImage_user on User {
	username
	...ImageUser_user
}
fragment RebrandUserCard_user on User {
	id
	displayName
	username
  picture {
    urlRoot
  }
	...UsercardFollowerCount_user
	...ImageUser_user
	...Hovercard_user
	...RebrandFollowButton_user
	...UserBadge_user
	...RebrandUserCardDisplayName_user
	...RebrandUserCardUserImage_user
}
fragment RebrandUserCard_viewer on Viewer {
	id
	...RebrandFollowButton_viewer
}
fragment SearchResultsUsers_viewer_4die2G on Viewer {
	search {
		searchQuery(term: $term) {
			users(
				first: $count
				after: $cursor
				dateJoinedAfter: $dateJoinedAfter
				isUploader: $isUploader
			) {
				edges {
					...UserCardList_userEdges
					cursor
					node {
						__typename
						id
						biog
					}
				}
				pageInfo {
					endCursor
					hasNextPage
				}
			}
		}
	}
	...UserCardList_viewer
}
fragment UserBadge_user on User {
	hasProFeatures
	isStaff
	hasPremiumFeatures
}
fragment UserCardList_userEdges on UserEdgeInterface {
	__isUserEdgeInterface: __typename
	node {
		...ItemUserCardList_user
		id
	}
}
fragment UserCardList_viewer on Viewer {
	...ItemUserCardList_viewer
}
fragment UsercardFollowerCount_user on User {
	followers {
		totalCount
	}
}`,

  SearchResultsCloudcastsQuery: `
query SearchResultsCloudcastsQuery(
	$count: Int = 10
	$createdAfter: CreatedAfterFilter
	$cursor: String
	$isTimestamped: IsTimestampedFilter
	$term: String!
) {
	viewer {
		...SearchResultsCloudcasts_viewer_4jbhrQ
		id
	}
}

fragment AttributionRequestButton_cloudcast on Cloudcast {
	id
	viewerCanRequestAttribution
	owner {
		hasProFeatures
      picture {
    urlRoot
  }
		id
	}
	viewerAttribution {
		id
	}
	viewerAttributionRequest {
		id
	}
	creatorAttributions(first: 2) {
		edges {
			__typename
		}
	}
}

fragment AudioCardDetails_cloudcast on Cloudcast {
	audioLength
	plays
	publishDate
	tags(country: "GLOBAL") {
		...AudioCardTagsPreviewer_tag
	}
	...AudioCardTags_cloudcast
}

fragment AudioCardTagsPreviewer_tag on CloudcastTag {
	tag {
		name
		slug
		id
	}
}

fragment AudioCardTags_cloudcast on Cloudcast {
	tags(country: "GLOBAL") {
		tag {
			name
			slug
			id
		}
	}
}

fragment ImageCloudcast_cloudcast on Cloudcast {
	name
	picture {
		urlRoot
		primaryColor
	}
}

fragment ItemSearchAudioCardListCloudcast_cloudcast on Cloudcast {
	id
	...SearchAudioCard_cloudcast
}

fragment ItemSearchAudioCardListUser_cloudcast on Cloudcast {
	id
	...SearchAudioCard_cloudcast
}

fragment PlayButton_cloudcast on Cloudcast {
	restrictedReason
	owner {
		isSubscribedTo
		isViewer
		id
	}
	id
	isDraft
	isPlayable
	...hasAudioCloudcastFragment_3lzM42
	currentPosition
	proportionListened
	previewUrl
	isExclusive
	...StaticPlayButton_cloudcast
	...useAudioPreview_cloudcast
	...usePlayWithRestrictions_cloudcast
}

fragment SearchAudioCardList_edges on CloudcastEdgeInterface {
	__isCloudcastEdgeInterface: __typename
	node {
		...ItemSearchAudioCardListCloudcast_cloudcast
		...ItemSearchAudioCardListUser_cloudcast
		id
	}
}

fragment SearchAudioCard_cloudcast on Cloudcast {
	name
	slug
  url
	isExclusive
	owner {
		displayName
		username
		...UserBadge_user
		id
	}
	...ImageCloudcast_cloudcast
	...AudioCardDetails_cloudcast
	...PlayButton_cloudcast
	...AttributionRequestButton_cloudcast
}

fragment SearchResultsCloudcasts_viewer_4jbhrQ on Viewer {
	search {
		searchQuery(term: $term) {
			cloudcasts(
				first: $count
				after: $cursor
				createdAfter: $createdAfter
				isTimestamped: $isTimestamped
			) {
				edges {
					...SearchAudioCardList_edges
					cursor
					node {
						__typename
						id
					}
				}
				pageInfo {
					endCursor
					hasNextPage
				}
			}
		}
	}
}

fragment StaticPlayButton_cloudcast on Cloudcast {
	owner {
		username
		id
	}
	slug
	isAwaitingAudio
	restrictedReason
}

fragment UserBadge_user on User {
	hasProFeatures
	isStaff
	hasPremiumFeatures
}

fragment hasAudioCloudcastFragment_3lzM42 on Cloudcast {
	streamInfo(timestamper: false) {
		__typename
	}
}

fragment useAudioPreview_cloudcast on Cloudcast {
	id
	previewUrl
}

fragment useExclusiveCloudcastModal_cloudcast on Cloudcast {
	id
	isExclusive
	owner {
		username
		id
	}
}

fragment useExclusivePreviewModal_cloudcast on Cloudcast {
	id
	isExclusivePreviewOnly
	owner {
		username
		id
	}
}

fragment usePlayWithRestrictions_cloudcast on Cloudcast {
	id
	owner {
		isSubscribedTo
		isViewer
		id
	}
	isPlayable
	...hasAudioCloudcastFragment_3lzM42
	currentPosition
	repeatPlayAmount
	hasPlayCompleted
	seekRestriction
	isExclusive
	...useExclusivePreviewModal_cloudcast
	...useExclusiveCloudcastModal_cloudcast
}`,

  CloudcastCommentsQuery: `
query CloudcastCommentsQuery(
  $lookup: CloudcastLookup!
  $commentId: ID
) {
  cloudcast: cloudcastLookup(lookup: $lookup) {
    ...CloudcastComments_cloudcast_4wMsyD
    id
  }
  viewer {
    ...CloudcastComments_viewer
    id
  }
}

fragment CloudcastCommentForm_cloudcast on Cloudcast {
  __typename
  id
  restrictedReason
  owner {
    isViewer
    id
  }
  ...playbackPositionCloudcastFragment
  ...useCreateComment_commentObject
}

fragment CloudcastCommentForm_viewer on Viewer {
  ...CommentForm_viewer
}

fragment CloudcastCommentTime_comment on Comment {
  audioPosition
  commentObject {
    __typename
    ... on Cloudcast {
      id
      ...isNowPlayingCloudcastFragment
      ...usePlayWithRestrictions_cloudcast
    }
    ... on Node {
      __isNode: __typename
      id
    }
  }
}

fragment CloudcastComments_cloudcast_4wMsyD on Cloudcast {
  id
  isExclusive
  restrictedReason
  audioType
  owner {
    id
    isSubscribedTo
    isViewer
  }
  comments(first: 10, promotedCommentId: $commentId) {
    commentsDisabled
    edges {
      ...CommentList_commentEdges
      cursor
      node {
        __typename
        id
      }
    }
    pageInfo {
      endCursor
      hasNextPage
    }
  }
  creatorAttributions(first: 100) {
    edges {
      node {
        id
      }
    }
  }
  ...CloudcastCommentForm_cloudcast
}

fragment CloudcastComments_viewer on Viewer {
  ...CloudcastCommentForm_viewer
}

fragment CommentActions_comment on Comment {
  id
  canDelete
  canBlock
  user {
    id
    isViewer
    isBlocked
  }
  commentObject {
    __typename
    ... on Node {
      __isNode: __typename
      id
    }
  }
  ...CommentReplyButton_comment
  ...CommentLikeButton_comment
}

fragment CommentForm_viewer on Viewer {
  me {
    displayName
    ...ImageUser_user
    id
  }
}

fragment CommentLikeButton_comment on Comment {
  id
  isLiked
  likeCount
}

fragment CommentList_commentEdges on CommentEdge {
  cursor
  node {
    ...ItemCommentList_comment
    id
  }
}

fragment CommentReplyButton_comment on Comment {
  id
}

fragment CommentRow_comment on Comment {
  id
  comment
  created
  isDeleted
  isSpam
  user {
    id
    username
    displayName
    ...ImageUser_user
    ...Hovercard_user
    ...Username_user
  }
  ...CommentTime_comment
  ...CommentActions_comment
  ...SelectCommentBadge_comment
  ...useGetCommentUrl_comment
}

fragment CommentTime_comment on Comment {
  commentObject {
    __typename
    ... on Node {
      __isNode: __typename
      id
    }
  }
  ...CloudcastCommentTime_comment
}

fragment Hovercard_user on User {
  id
}

fragment ImageUser_user on User {
  picture {
    urlRoot
    primaryColor
  }
  displayName
}

fragment ItemCommentList_comment on Comment {
  ...TopLevelComment_comment
}

fragment SelectCommentBadge_comment on Comment {
  isFromSelectSubscriber
}

fragment TopLevelComment_comment on Comment {
  ...CommentRow_comment
  isDeleted
  children(first: 100) {
    edges {
      node {
        ...CommentRow_comment
        id
        __typename
      }
      cursor
    }
    pageInfo {
      endCursor
      hasNextPage
    }
  }
}

fragment UserBadge_user on User {
  hasProFeatures
  isStaff
  hasPremiumFeatures
}

fragment Username_user on User {
  username
  displayName
  ...UserBadge_user
  ...Hovercard_user
}

fragment hasAudioCloudcastFragment_3lzM42 on Cloudcast {
  streamInfo(timestamper: false) {
    __typename
  }
}

fragment isNowPlayingCloudcastFragment on Cloudcast {
  id
}

fragment playbackPositionCloudcastFragment on Cloudcast {
  id
  currentPosition
}

fragment useCreateComment_commentObject on Node {
  __isNode: __typename
  __typename
  ... on Cloudcast {
    audioLength
  }
}

fragment useExclusiveCloudcastModal_cloudcast on Cloudcast {
  id
  isExclusive
  owner {
    username
    id
  }
}

fragment useExclusivePreviewModal_cloudcast on Cloudcast {
  id
  isExclusivePreviewOnly
  owner {
    username
    id
  }
}

fragment useGetCommentUrl_comment on Comment {
  id
  commentObject {
    __typename
    ... on Cloudcast {
      owner {
        username
        id
      }
      slug
    }
    ... on Post {
      owner {
        username
        id
      }
      slug
    }
    ... on Node {
      __isNode: __typename
      id
    }
  }
}

fragment usePlayWithRestrictions_cloudcast on Cloudcast {
  id
  owner {
    isSubscribedTo
    isViewer
    id
  }
  isPlayable
  ...hasAudioCloudcastFragment_3lzM42
  currentPosition
  repeatPlayAmount
  hasPlayCompleted
  seekRestriction
  isExclusive
  ...useExclusivePreviewModal_cloudcast
  ...useExclusiveCloudcastModal_cloudcast
}`,

  MoreFromOwnerQuery: `
query MoreFromOwnerQuery(
  $lookup: CloudcastLookup!
) {
  cloudcast: cloudcastLookup(lookup: $lookup) {
    owner {
      displayName
      id
			
    }
    moreFromOwner(first: 10) {
      edges {
        node {
          ...MoreFromUserCard_cloudcast
          id
          audioLength
          url
          plays
        }
      }
    }
    id
  }
  viewer {
    id
  }
}

fragment CardPlayButton_cloudcast on Cloudcast {
  id
  restrictedReason
  proportionListened
  owner {
    isSubscribedTo
    isViewer
    id
  }
  isAwaitingAudio
  isDraft
  isPlayable
  currentPosition
  ...playbackPositionCloudcastFragment
  previewUrl
  isExclusive
  isDisabledCopyright
  ...CardStaticPlayButton_cloudcast
  ...useAudioPreview_cloudcast
  ...usePlayWithRestrictions_cloudcast
}

fragment CardStaticPlayButton_cloudcast on Cloudcast {
  owner {
    username
    id
		picture {
				urlRoot
			}
  }
  slug
  id
  restrictedReason
}

fragment ImageCloudcast_cloudcast on Cloudcast {
  name
  picture {
    urlRoot
    primaryColor
  }
}

fragment MoreFromUserCard_cloudcast on Cloudcast {
  name
  publishDate
  slug
  owner {
    username
    id
  }
  ...ImageCloudcast_cloudcast
  ...CardPlayButton_cloudcast
}

fragment hasAudioCloudcastFragment_3lzM42 on Cloudcast {
  streamInfo(timestamper: false) {
    __typename
  }
}

fragment playbackPositionCloudcastFragment on Cloudcast {
  id
  currentPosition
}

fragment useAudioPreview_cloudcast on Cloudcast {
  id
  previewUrl
}

fragment useExclusiveCloudcastModal_cloudcast on Cloudcast {
  id
  isExclusive
  owner {
    username
    id
  }
}

fragment useExclusivePreviewModal_cloudcast on Cloudcast {
  id
  isExclusivePreviewOnly
  owner {
		displayName
    username
    id
  }
}

fragment usePlayWithRestrictions_cloudcast on Cloudcast {
  id
  owner {
    isSubscribedTo
    isViewer
    id
  }
  isPlayable
  ...hasAudioCloudcastFragment_3lzM42
  currentPosition
  repeatPlayAmount
  hasPlayCompleted
  seekRestriction
  isExclusive
  ...useExclusivePreviewModal_cloudcast
  ...useExclusiveCloudcastModal_cloudcast
}`,

PlaylistsQuery: `
query PlaylistsQuery(
  $cursor: String
) {
  viewer {
    me {
      playlists(first: 21, orderBy: ALPHABETICAL, after: $cursor) {
        edges {
          node {
            name
            slug
            owner {
              username
              displayName
              id
            }
            picture(height: 160) {
              urlRoot
              primaryColor
            }
            items {
              totalCount
            }
            id
          }
          cursor
        }
        pageInfo {
          endCursor
          hasNextPage
        }
      }
      id
    }
    id
  }
}`,

getViewerDataQuery: `
query getViewerDataQuery {
  viewer {
    me {
      id
      email
      username
      hasProFeatures
      hasPremiumFeatures
      isSelfDeclaredCreator
      isStaff
      isUploader
    }
    id
  }
}`,

  UserFollowingsQuery: `
query UserFollowingsQuery(
  $lookup: UserLookup!
  $count: Int = 20
  $cursor: String
) {
  user: userLookup(lookup: $lookup) {
    id
    username
    displayName
    followings(first: $count, after: $cursor) {
      edges {
        node {
          id
          username
          displayName
          picture {
            urlRoot
          }
          followers {
            totalCount
          }
          biog
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
}`

};

log("LOADED");