/**
 * BitChute Plugin for GrayJay
 * This plugin enables GrayJay to integrate with BitChute's video platform
 */

//================ CONSTANTS ================//

// Platform identifiers
const PLATFORM = 'Bitchute';
const PLATFORM_CLAIMTYPE = 30;

// API endpoints
const URL_API = {
  VIDEOS_BETA9: 'https://api.bitchute.com/api/beta9/videos',
  VIDEOS_BETA: 'https://api.bitchute.com/api/beta/videos',
  CHANNEL: 'https://api.bitchute.com/api/beta/channel',
  PROFILE: 'https://api.bitchute.com/api/beta/profile',
  PROFILE_VIDEOS: 'https://api.bitchute.com/api/beta/profile/videos',
  PROFILE_PLAYLISTS: 'https://api.bitchute.com/api/beta/profile/playlists',
  LINKS: 'https://api.bitchute.com/api/beta/profile/links',
  SEARCH_VIDEOS: 'https://api.bitchute.com/api/beta/search/videos',
  CHANNEL_VIDEOS: 'https://api.bitchute.com/api/beta/channel/videos',
  SEARCH_CHANNELS: 'https://api.bitchute.com/api/beta/search/channels',
  MEDIA_INFO: 'https://api.bitchute.com/api/beta/video/media',
  VIDEO_INFO: 'https://api.bitchute.com/api/beta9/video',
  VIDEO_COUNTS: 'https://api.bitchute.com/api/beta/video/counts',
  VIDEO_COMMENTS: 'https://commentfreely.bitchute.com/api/get_comments/',
  VIDEO_COMMENTS_AUTH: 'https://api.bitchute.com/api/beta/apps/commentfreely/video',
  LIVES: 'https://api.bitchute.com/api/beta9/cache/livestreams',
  PLAYLIST: 'https://api.bitchute.com/api/beta/playlist',
  PLAYLIST_VIDEOS: 'https://api.bitchute.com/api/beta/playlist/videos'
};

// Web URLs
const URL_WEB = {
  BASE: 'https://www.bitchute.com',
  BASE_OLD: 'https://old.bitchute.com',
  LOGIN_OLD: 'https://old.bitchute.com/accounts/login/',
  VIDEOS: 'https://www.bitchute.com/video/',
  SUBSCRIPTIONS_OLD: 'https://old.bitchute.com/subscriptions/',
  PLAYLISTS_OLD: 'https://old.bitchute.com/playlists/'
};

// Regular expressions for URL parsing
const REGEX = {
  // Playlist URL patterns split by type
  PLAYLIST_SYSTEM: /^https:\/\/(old|www)\.bitchute\.com\/playlist\/(favorites|watch-later|recently-viewed)\/?(?:[?&][^#]*)?$/,
  PLAYLIST_USER: /^https:\/\/(old|www)\.bitchute\.com\/playlist\/([a-zA-Z0-9_\-]+)\/?(?:[?&][^#]*)?$/,
  PLAYLIST_QUERY: /^https:\/\/(old|www)\.bitchute\.com\/video\/playlist\?playlistId=([a-zA-Z0-9_\-]+)$/,

  CHANNEL_URL: /bitchute\.com\/channel\//,
  PROFILE_URL: /bitchute\.com\/profile\//,
  VIDEO_URL: /bitchute\.com\/video\//,
  HLS_URL: /https:\/\/.*\.m3u8/,
  MPEG_URL: /https:\/\/.*\.mp4/,

  PLAYLIST_PRIVATE: /\/playlist\/(favorites|watch-later|recently-viewed)/,
  IS_PRIVATE: /[?&]is-private=true(&|$)/,
  EXTRACT_PROFILE_ID: /\/profile\/([A-Za-z0-9_\-]+)\/?/,
  EXTRACT_CHANNEL_ID: /\/channel\/([A-Za-z0-9_\-]+)\/?/,
  EXTRACT_VIDEO_ID: /bitchute\.com\/video\/([a-zA-Z0-9\-_]+)/
};

// Default HTTP headers
const REQUEST_HEADERS = {
  'Content-Type': 'application/json'
};

// Private URL handling
const IS_PRIVATE_SUFFIX = 'is-private=true';

// BitChute category slug -> human-readable display name
// Source: bitchute.com homepage sidebar (/category/<slug>/ links)
const CATEGORY_DISPLAY_NAMES = {
  animation: 'Anime & Animation',
  arts: 'Arts & Literature',
  vehicles: 'Auto & Vehicles',
  beauty: 'Beauty & Fashion',
  finance: 'Business & Finance',
  cuisine: 'Cuisine',
  diy: 'DIY & Gardening',
  education: 'Education',
  entertainment: 'Entertainment',
  gaming: 'Gaming',
  health: 'Health & Medical',
  music: 'Music',
  news: 'News & Politics',
  family: 'People & Family',
  animals: 'Pets & Wildlife',
  science: 'Science & Technology',
  spirituality: 'Spirituality & Faith',
  sport: 'Sports & Fitness',
  travel: 'Travel',
  vlogging: 'Vlogging'
};

//================ PLUGIN STATE ================//

let _config = {};
let _settings = {};

let state = {
  channel: {},
  channelMeta: {},
  channelContent: {},
  profileLinks: {},
  channelContentTimeToLive: {}
}


let CHANNEL_CONTENT_TTL_OPTIONS = [];
let CONTENT_SENSITIVITY_OPTIONS = [];
let RECOMMENDED_CONTEXT_OPTIONS = [];
let HOME_CONTENT_FEED_OPTIONS = [];

//================ SOURCE API IMPLEMENTATION ================//

/**
 * Initializes the plugin with configuration and settings
 */
source.enable = function (conf, settings, saveStateStr) {
  _config = conf ?? {};
  _settings = settings ?? {};

  if (IS_TESTING) {
    _settings.showLiveVideosOnHome = false;
    _settings.cacheChannelContent = true;
    _settings.cacheChannelContentTimeToLiveIndex = 5; // 10 minutes
    _settings.contentSensitivityIndex = 1; // Normal
    _settings.RecommendedContentIndex = 0; // More from same channel
    _settings.homeContentFeedIndex = 0; // Popular
  }
  

  CHANNEL_CONTENT_TTL_OPTIONS = loadOptionsForSetting('cacheChannelContentTimeToLiveIndex', (s) => parseInt(s))
            
  CONTENT_SENSITIVITY_OPTIONS = loadOptionsForSetting('contentSensitivityIndex', (s) => {
    return s.split('-')?.[0]?.trim()?.toLowerCase();
  })

  RECOMMENDED_CONTEXT_OPTIONS = loadOptionsForSetting('RecommendedContentIndex');

  HOME_CONTENT_FEED_OPTIONS = loadOptionsForSetting('homeContentFeedIndex', (s) => s.toLowerCase().replace(/\s+/g, '-'));

  try {
    if (saveStateStr) {
      state = JSON.parse(saveStateStr);
    }
  } catch (ex) {
    log('Failed to parse saveState:' + ex);
  }
};

/**
 * Saves plugin state for persistence between sessions
 */
source.saveState = () => {
  //no caching while testing
  return IS_TESTING ? JSON.stringify({}) : JSON.stringify(state);
};

/**
 * Gets home page content
 */
source.getHome = function() {
  const sensitivity_id = CONTENT_SENSITIVITY_OPTIONS[_settings.contentSensitivityIndex];
  const feedType = HOME_CONTENT_FEED_OPTIONS[_settings.homeContentFeedIndex ?? 0] || 'popular';
  
  // Get live videos if enabled
  let liveVideos = [];
  if (_settings.showLiveVideosOnHome) {
    const liveConfig = {
      url: URL_API.LIVES,
      root: 'videos',
      total_property: 'video_count',
      request_body: {},
      transform: BitchuteVideoToPlatformVideo
    };
    
    try {
      const livePager = createContentPager(liveConfig);
      liveVideos = livePager.results;
    } catch (e) {
      log('Error loading live videos: ' + e);
    }
  }
  
  // Configure main content feed based on settings
  let config;
  if (feedType === 'all' || feedType === 'suggested') {
    config = {
      url: URL_API.VIDEOS_BETA9,
      root: 'videos',
      total_property: 'video_count',
      cacheKey: 'home',
      cacheValue: feedType,
      request_body: {
        selection: feedType,
        offset: 0,
        limit: 20,
        advertisable: true,
        sensitivity_id
      },
      transform: BitchuteVideoToPlatformVideo
    };
  } else {
    config = {
      url: URL_API.VIDEOS_BETA,
      root: 'videos',
      total_property: 'video_count',
      cacheKey: 'home',
      cacheValue: feedType,
      request_body: {
        selection: feedType,
        offset: 0,
        limit: 20,
        advertisable: true,
        sensitivity_id
      },
      transform: BitchuteVideoToPlatformVideo
    };
  }
  
  // Return pager with combined results
  return createContentPager(config, liveVideos);
};

/**
 * Gets search capabilities
 */
source.getSearchCapabilities = () => {
  return {
    types: [Type.Feed.Mixed],
    sorts: [Type.Order.Chronological],
    filters: [],
  };
};

/**
 * Searches for videos
 */
source.search = function (query) {
  const config = {
    url: URL_API.SEARCH_VIDEOS,
    root: 'videos',
    total_property: 'video_count',
    cacheKey: 'search',
    cacheValue: query,
    request_body: {
      offset: 0,
      limit: 50,
      query: query,
      sensitivity_id: CONTENT_SENSITIVITY_OPTIONS[_settings.contentSensitivityIndex],
      sort: 'new',
    },
    transform: BitchuteVideoToPlatformVideo
  };
  
  return createContentPager(config);
};

/**
 * Gets search channel contents capabilities
 */
source.getSearchChannelContentsCapabilities = function () {
  return {
    types: [Type.Feed.Mixed],
    sorts: [Type.Order.Chronological],
    filters: [],
  };
};

/**
 * Searches for channels
 */
source.searchChannels = function (query) {
  class SearchChannelsPager extends VideoPager {
    constructor({ videos = [], hasMore = true, context = {} } = {}) {
      super(videos, hasMore, context);
    }

    nextPage() {
      const response = makeApiRequest(
        URL_API.SEARCH_CHANNELS,
        {
          offset: this.context.offset,
          limit: 50,
          query: query,
          sensitivity_id: CONTENT_SENSITIVITY_OPTIONS[_settings.contentSensitivityIndex],
          sort: 'new',
        }
      );

      const searchResultChannels = response.channels;

      const channels = searchResultChannels.map((sourceChannel) => {
        return new PlatformChannel({
          id: new PlatformID(
            PLATFORM,
            sourceChannel?.channel_id ?? '',
            _config.id,
            PLATFORM_CLAIMTYPE,
          ),
          name: sourceChannel?.channel_name ?? '',
          thumbnail: sourceChannel?.thumbnail_url ?? '',
          banner: '',
          subscribers: sourceChannel.subscriber_count,
          description: sourceChannel.description,
          url: `${URL_WEB.BASE}${sourceChannel.channel_url}`,
        });
      });

      return new SearchChannelsPager({
        videos: channels,
        hasMore: false,
        context: { offset: (this?.context?.offset ?? 0) + 50 },
      });
    }
  }

  return new SearchChannelsPager().nextPage();
};

/**
 * Checks if a URL is a channel URL
 */
source.isChannelUrl = function (url) {
  return [REGEX.CHANNEL_URL, REGEX.PROFILE_URL].some((r) => r.test(url));
};

/**
 * Gets channel information
 */
source.getChannel = function (url) {
  if(state.channel[url]){
    return state.channel[url];
  }

  if(REGEX.CHANNEL_URL.test(url)) {
    state.channel[url] = getChannelWithBatch(url);
  }
  else if (REGEX.PROFILE_URL.test(url)) {
    state.channel[url] = getProfile(url);
  }

  return state.channel[url];
};

/**
 * Gets channel playlists
 */
source.getChannelPlaylists = (url) => {
  const isChannelUrl = REGEX.CHANNEL_URL.test(url);
  const isProfileUrl = REGEX.PROFILE_URL.test(url);

  if(isChannelUrl) {
    // Not supported yet on source platform
    return new ContentPager([]);
  } else if(isProfileUrl) {
    const profile_id = extractProfileId(url);
    const profileMeta = getProfile(url);

    // Direct API request for playlists to ensure proper data handling
    try {
      const responseBody = makeApiRequest(
        URL_API.PROFILE_PLAYLISTS,
        {
          offset: 0,
          limit: 50,
          profile_id: profile_id,
        }
      );

      if (!responseBody || !responseBody.playlists) {
        log(`No playlists found for profile ${profile_id}`);
        return new ContentPager([]);
      }

      // Transform playlists into platform objects
      const playlists = responseBody.playlists.map(playlist => {
        return new PlatformPlaylist({
          id: new PlatformID(
            PLATFORM,
            playlist.playlist_id,
            _config.id,
            PLATFORM_CLAIMTYPE,
          ),
          author: new PlatformAuthorLink(
            new PlatformID(PLATFORM, profile_id, _config.id),
            profileMeta.name,
            profileMeta.url,
            profileMeta.thumbnail,
          ),
          name: playlist.playlist_name,
          thumbnail: playlist.thumbnail_url,
          videoCount: playlist.video_count,
          url: `${URL_WEB.BASE}/playlist/${playlist.playlist_id}`,
        });
      });

      // Check if there are more pages (unlikey, but just in case)
      const hasMore = responseBody.playlists && responseBody.playlists.length >= 50;

      return new ContentPager(playlists, hasMore);
    } catch (error) {
      log(`Error fetching playlists: ${error.message}`);
      return new ContentPager([]);
    }
  }

  return new ContentPager([]);
};

/**
 * Gets channel contents
 */
source.getChannelContents = function (url) {
  if(REGEX.CHANNEL_URL.test(url)) {
    return getChannelContents(url);
  }
  else if (REGEX.PROFILE_URL.test(url)) {
    return getProfileContents(url);
  }
  else {
    throw new ScriptException(`Invalid channel URL: ${url}`);
  }

};

/**
 * Checks if a URL is a content details URL
 */
source.isContentDetailsUrl = function (url) {
  return REGEX.VIDEO_URL.test(url);
};

/**
 * Gets content details
 */
source.getContentDetails = function (url) {
  const videoId = extractVideoIDFromUrl(url);

  const body = JSON.stringify({ video_id: videoId });

  const [
    mediaDetailsResponse,
    videoDetailsResponse,
    countsResponse,
    embedResponse,
  ] = batchRequest([
    {
      url: URL_API.MEDIA_INFO,
      method: 'POST',
      headers: REQUEST_HEADERS,
      body: body,
    },
    {
      url: URL_API.VIDEO_INFO,
      method: 'POST',
      headers: REQUEST_HEADERS,
      body: body,
    },
    {
      url: URL_API.VIDEO_COUNTS,
      method: 'POST',
      headers: REQUEST_HEADERS,
      body: body,
    },
    {
      url: `${URL_WEB.BASE}/api/beta9/embed/${videoId}?videoID=${videoId}&startTime=0&autoPlay=true&theaterMode=true&showAds=true`,
    },
  ]);

  if (!mediaDetailsResponse.isOk) {
    throw new ScriptException(
      `Failed request mediaDetailsResponse (${mediaDetailsResponse.code})`,
    );
  }

  if (!videoDetailsResponse.isOk) {
    throw new ScriptException(
      `Failed request videoDetailsResponse (${videoDetailsResponse.code})`,
    );
  }

  if (!countsResponse.isOk) {
    throw new ScriptException(
      `Failed request countsResponse (${countsResponse.code})`,
    );
  }

  const mediaDetails = JSON.parse(mediaDetailsResponse.body);
  const videoDetails = JSON.parse(videoDetailsResponse.body);
  const countDetails = JSON.parse(countsResponse.body);

  const duration = convertToSeconds(videoDetails.duration);

  let media_url;

  let urlMatch = embedResponse.body.match(/var\s+media_url\s*=\s*'(.*?)'/);
  // Extracting the URL
  if (urlMatch && urlMatch[1]) {
    media_url = urlMatch[1];
  }

  media_url = media_url || mediaDetails.media_url;

  if (!media_url) {
    throw new ScriptException('Failed to get media url');
  }

  const sources = [];

  if (REGEX.HLS_URL.test(media_url)) {
    sources.push(
      new HLSSource({
        name: 'HLS',
        url: media_url,
        duration: duration,
        priority: true,
      }),
    );
  } else if (REGEX.MPEG_URL.test(media_url)) {
    sources.push(
      new VideoUrlSource({
        name: mediaDetails.media_type,
        duration: duration,
        url: media_url,
        container: "video/mp4"
      }),
    );
  }

  const result = new PlatformVideoDetails({
    id:
      videoId &&
      new PlatformID(PLATFORM, videoId, _config.id, PLATFORM_CLAIMTYPE),
    name: videoDetails.video_name,
    author: new PlatformAuthorLink(
      new PlatformID(
        PLATFORM,
        videoDetails.channel.channel_id,
        _config.id,
        PLATFORM_CLAIMTYPE,
      ),
      videoDetails.channel.channel_name,
      `${URL_WEB.BASE}${videoDetails.channel.channel_url}`,
      videoDetails.channel.thumbnail_url,
    ),
    datetime: dateToUnixSeconds(videoDetails.date_published),
    description: videoDetails.description,
    shareUrl: url,
    url: url,
    video: new VideoSourceDescriptor(sources),
    rating: new RatingLikesDislikes(
      countDetails.like_count,
      countDetails.dislike_count,
    ),
    isLive: videoDetails.state_id == 'live',
    duration: duration,
    thumbnails:
      videoDetails.thumbnail_url &&
      new Thumbnails([new Thumbnail(videoDetails.thumbnail_url, 0)]),
    viewCount: countDetails.view_count,
  });

  result.getContentRecommendations = function () {
    return source.getContentRecommendations(url, videoDetails);
  };

  return result;
};

/**
 * Gets content recommendations
 */
source.getContentRecommendations = (url, initialData) => {
  const selectedRecommendedContent =
    RECOMMENDED_CONTEXT_OPTIONS[_settings.RecommendedContentIndex];
  const isMoreFromSameChannel =
    selectedRecommendedContent === 'More from same channel';
  const isRelated = selectedRecommendedContent === 'Related';
  const isForYou = selectedRecommendedContent === 'For you';
  const isRecentUploads = selectedRecommendedContent === 'Recent Uploads';

  let requestUrl = '';
  let body = {};

  const videoId = extractVideoIDFromUrl(url);

  if (!initialData) {
    const [videoDetailsResponse] = batchRequest([
      {
        url: URL_API.VIDEO_INFO,
        method: 'POST',
        headers: REQUEST_HEADERS,
        body: JSON.stringify({ video_id: videoId }),
      },
    ]);

    if (!videoDetailsResponse.isOk) {
      throw new ScriptException(
        `Failed request videoDetailsResponse (${videoDetailsResponse.code})`,
      );
    }

    initialData = JSON.parse(videoDetailsResponse.body);
  }

  if (isMoreFromSameChannel) {
    body = { channel_id: initialData.channel.channel_id, offset: 0, limit: 50 };
    requestUrl = URL_API.CHANNEL_VIDEOS;
  } else if (isRelated || isRecentUploads) { //currently they have same behavior as "Related"
    body = {
      selection: 'popular',
      offset: 0,
      limit: 10,
      category_id: initialData.category_id,
    };
    requestUrl = URL_API.VIDEOS_BETA;
  } else if (isForYou) {
    body = { selection: 'popular', offset: 0, limit: 10, category_id: 'news' };
    requestUrl = URL_API.VIDEOS_BETA;
  }

  let videos = makeApiRequest(
    requestUrl,
    JSON.stringify(body),
    REQUEST_HEADERS,
    false
  ).videos;

  if (isMoreFromSameChannel) {
    const channel = initialData?.channel ?? {
      channel_id: '',
      channel_name: '',
      channel_url: '',
      thumbnail_url: '',
    };

    videos = videos.map((v) => {
      v.channel = channel;
      return v;
    });
  }

  const platformVideos = videos
    .filter((v) => v.video_id !== videoId) // remove current video from recommendations
    .map(v => BitchuteVideoToPlatformVideo(v));

  return new VideoPager(platformVideos ?? [], false);
};

/**
 * Gets comments for a video
 */
source.getComments = function (url) {
  const videoId = extractVideoIDFromUrl(url);

  const obj = getCommentAuthForVideo(videoId);

  const formData = objectToUrlEncodedString({
    cf_auth: obj.auth,
    commentCount: '0',
    isNameValuesArrays: 'true',
  });

  const headers = {
    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
  };


  const comments = makeApiRequest(
    URL_API.VIDEO_COMMENTS,
    formData,
    headers,
    false
  );

  const allComments = convertToObjects(comments.names, comments.values);

  const results = allComments
    .filter((c) => !c.parent)
    .map((comment) => ToComment(url, comment, allComments));

  return new CommentPager(results, false);
};

/**
 * Checks if a URL is a playlist URL
 */
source.isPlaylistUrl = function (url) {
  return REGEX.PLAYLIST_SYSTEM.test(url) ||
         REGEX.PLAYLIST_USER.test(url) ||
         REGEX.PLAYLIST_QUERY.test(url);
};

/**
 * Gets a playlist by URL
 * Supports all playlist URL formats:
 * - System playlists: https://(old|www).bitchute.com/playlist/(favorites|watch-later|recently-viewed)/
 * - User playlists: https://(old|www).bitchute.com/playlist/PLAYLIST_ID/
 * - Query playlists: https://(old|www).bitchute.com/video/playlist?playlistId=PLAYLIST_ID
 */
source.getPlaylist = function (url) {
  const isPrivate = REGEX.IS_PRIVATE.test(url);
  const isSystemGeneratedPlaylist = REGEX.PLAYLIST_PRIVATE.test(url);

  if (isPrivate) {

    if(!bridge.isLoggedIn()) {
      throw new LoginRequiredException('Login to import Subscriptions');
    }
    url = url.replace(`&${IS_PRIVATE_SUFFIX}`, '');
  }

  // Extract playlist ID using our improved extraction functions
  const playlist_id = extractPlaylistId(url);
  // If that fails, try the query parameter extractor as a fallback
  if (!playlist_id) {
    throw new ScriptException(`Failed to extract playlist ID from URL: ${url}`);
  }
  
  let playlistInfo;
  let channel = {};
  let playlistName = playlist_id;

  if(!isSystemGeneratedPlaylist) {
    const playlistInfoResponse = http.POST(
      URL_API.PLAYLIST,
      JSON.stringify({ playlist_id }),
      REQUEST_HEADERS,
      true
    );
    if(playlistInfoResponse.isOk) {
      playlistInfo = JSON.parse(playlistInfoResponse.body);
      playlistName = playlistInfo?.playlist_name || playlist_id;
      
      if(playlistInfo.profile_id) {
        channel = {
          channel_id: playlistInfo.profile_id,
          channel_name: playlistInfo.profile_name,
          channel_url: `${URL_WEB.BASE}/profile/${playlistInfo.profile_id}`,
          thumbnail_url: playlistInfo.thumbnail_url,
        };
      } else {
        channel = {
          channel_id: playlistInfo.channel_id,
          channel_name: playlistInfo.channel_name,
          channel_url: `${URL_WEB.BASE}/channel/${playlistInfo.channel_id}`,
          thumbnail_url: playlistInfo.thumbnail_url,
        };
      }
    }
  }

  const config = {
    cacheKey: 'playlist_id',
    cacheValue: playlist_id,
    url: URL_API.PLAYLIST_VIDEOS,
    root: 'videos',
    total: playlistInfo?.video_count ?? -1,
    auth: isPrivate,
    request_body: {
      offset: 0,
      limit: 50,
      playlist_id: playlist_id,
    },
    transform: (v) => {
      if(!isSystemGeneratedPlaylist && playlistInfo?.profile_id) {
        v.channel = channel;
      }
      return BitchuteVideoToPlatformVideo(v.video);
    },
  };

  
  const contentPager = createContentPager(config);

  return new PlatformPlaylistDetails({
    url: url,
    id: new PlatformID(PLATFORM, channel.channel_id, _config.id, PLATFORM_CLAIMTYPE),
    author: new PlatformAuthorLink(
      new PlatformID(PLATFORM, '', _config.id, PLATFORM_CLAIMTYPE),
      channel.channel_name,
      channel.channel_url
    ),
    name: playlistName,
    thumbnail: '',
    videoCount: playlistInfo?.video_count ?? -1,
    contents: contentPager,
  });
};

/**
 * Gets user subscriptions
 */
source.getUserSubscriptions = () => {
  if (!bridge.isLoggedIn()) {
    log('Failed to retrieve subscriptions page because not logged in.');
    throw new ScriptException('Not logged in');
  }

  const response = http.GET(URL_WEB.SUBSCRIPTIONS_OLD, {}, true);

  if (!response.isOk) {
    throw new ScriptException(`Failed request [${URL_WEB.SUBSCRIPTIONS_OLD}] (${response.code})`);
  }

  if (response.url.startsWith(URL_WEB.LOGIN_OLD)) {
    throw new LoginRequiredException(
      'Invalid session. login to import Subscriptions',
    );
  }

  const detailsDocument = domParser
    .parseFromString(response.body, 'text/html')
    .querySelector('body');

  const subscriptions = Array.from(
    detailsDocument.querySelectorAll('#page-detail a.spa[href^="/channel/"]'),
  ).map((e) => `${URL_WEB.BASE}${e.getAttribute('href')}`);

  return subscriptions;
};

/**
 * Gets user playlists
 */
source.getUserPlaylists = function () {
  if (!bridge.isLoggedIn()) {
    log('Failed to retrieve subscriptions page because not logged in.');
    throw new ScriptException('Not logged in');
  }

  const response = http.GET(URL_WEB.PLAYLISTS_OLD, {}, true);

  if (!response.isOk) {
    throw new ScriptException(`Failed request [${URL_WEB.PLAYLISTS_OLD}] (${response.code})`);
  }

  if (response.url.startsWith(URL_WEB.LOGIN_OLD)) {
    throw new LoginRequiredException(
      'Invalid session. login to import Subscriptions',
    );
  }

  const detailsDocument = domParser
    .parseFromString(response.body, 'text/html')
    .querySelector('body');

  const playlists = Array.from(
    detailsDocument.querySelectorAll('#page-detail a.spa[href^="/playlist/"]'),
  ).map((e) => `${URL_WEB.BASE_OLD}${trimEndSlash(e.getAttribute('href'))}&${IS_PRIVATE_SUFFIX}`);

  return playlists;
};


/**
 * Returns chat window information for live bitchute streams with chat
 * @param {string} url - The video URL
 * @returns {Object|null} Chat window configuration or null if chat not available
 */
source.getLiveChatWindow = function (url) {
  
  const videoId = extractVideoIDFromUrl(url);

  if (videoId) {
    return {
      url: `https://www.bitchute.com/popChat/${videoId}`,
      removeElements: [],
      removeElementsInterval: []
    };
  }
};

//================ HELPER FUNCTIONS ================//

/**
 * Parses settings options and applies transformations
 * 
 * @param {string} settingKey - The key of the setting to load options for
 * @param {Function} [transformCallback] - Optional function to transform each option
 * @returns {Array} - Array of setting options, possibly transformed
 */
function loadOptionsForSetting(settingKey, transformCallback) {
  transformCallback ??= (o) => o;
  const setting = _config?.settings?.find((s) => s.variable == settingKey);
  return setting?.options?.map(transformCallback) ?? [];
}

/**
 * Removes the trailing slash from a string
 * 
 * @param {string} str - The string to process
 * @returns {string} - The string without trailing slash
 */
function trimEndSlash(str) {
  return str.replace(/\/$/, "");
}

/**
 * Converts a time string (HH:MM:SS or MM:SS) to seconds
 * 
 * @param {string} time - Time string in format HH:MM:SS or MM:SS
 * @returns {number} - Total seconds
 */
function convertToSeconds(time) {
  if (!time || time.indexOf(':') === -1) {
    return 0;
  }

  // Split the time string by the colon
  const parts = time.split(':').map(part => parseInt(part, 10));
  
  if (parts.length === 3) {
    // Format is hh:mm:ss
    const [hours, minutes, seconds] = parts;
    return (hours * 3600) + (minutes * 60) + seconds;
  } else if (parts.length === 2) {
    // Format is mm:ss
    const [minutes, seconds] = parts;
    return (minutes * 60) + seconds;
  } 
  
  // Invalid format
  return 0;
}

/**
 * Converts a date string to Unix timestamp in seconds
 * 
 * @param {string} date - Date string
 * @returns {number|null} - Unix timestamp in seconds or null if date is invalid
 */
function dateToUnixSeconds(date) {
  if (!date) {
    return null;
  }

  return Math.round(Date.parse(date) / 1000);
}

/**
 * Extracts video ID from a BitChute video URL
 * 
 * @param {string} url - BitChute video URL
 * @returns {string|null} - Video ID or null if not found
 */
function extractVideoIDFromUrl(url) {
  // Use regular expression to capture video IDs with letters, numbers, dashes, and underscores
  const match = url.match(REGEX.EXTRACT_VIDEO_ID);
  return match && match[1] ? match[1] : null;
}

/**
 * Extracts channel ID from a BitChute channel URL
 * 
 * @param {string} url - BitChute channel URL
 * @returns {string|null} - Channel ID or null if not found
 */
function extractChannelId(url) {
  const match = url.match(REGEX.EXTRACT_CHANNEL_ID);
  return match && match[1] ? match[1] : null;
}

/**
 * Extracts profile ID from a BitChute profile URL
 * 
 * @param {string} url - BitChute profile URL
 * @returns {string|null} - Profile ID or null if not found
 */
function extractProfileId(url) {
  const match = url.match(REGEX.EXTRACT_PROFILE_ID);
  return match && match[1] ? match[1] : null;
}

/**
 * Extracts playlist ID from a BitChute playlist URL
 * Supports all URL formats:
 * - System playlists: https://www.bitchute.com/playlist/favorites/
 * - User playlists: https://www.bitchute.com/playlist/PLAYLIST_ID/
 * - Query playlists: https://www.bitchute.com/video/playlist?playlistId=PLAYLIST_ID
 *
 * @param {string} url - BitChute playlist URL
 * @returns {string|null} - Playlist ID or null if not found
 */
function extractPlaylistId(url) {
  // Check for system playlists (favorites, watch-later, recently-viewed)
  let match = url.match(REGEX.PLAYLIST_SYSTEM);
  if (match) {
    return match[2]; // Return the system playlist name as the ID
  }

  // Check for user playlists
  match = url.match(REGEX.PLAYLIST_USER);
  if (match) {
    return match[2]; // Return the user playlist ID
  }

  // Check for query parameter playlists
  match = url.match(REGEX.PLAYLIST_QUERY);
  if (match) {
    return match[2]; // Return the playlist ID from query
  }

  return null;
}

/**
 * Converts an object to URL encoded string format
 * 
 * @param {Object} obj - Object to convert
 * @returns {string} - URL encoded string
 */
function objectToUrlEncodedString(obj) {
  return Object.entries(obj)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&');
}

/**
 * Converts arrays of names and values to an array of objects
 * 
 * @param {Array<string>} names - Array of property names
 * @param {Array<Array>} values - Array of value arrays
 * @returns {Array<Object>} - Array of objects with names as keys and values as values
 */
function convertToObjects(names, values) {
  return values.map(valueArray => {
    const obj = {};
    names.forEach((name, index) => {
      obj[name] = valueArray[index];
    });
    return obj;
  });
}

/**
 * Executes a batch of HTTP requests
 * 
 * @param {Array<Object>} requests - Array of request configurations
 * @param {Object} [options] - Options for processing responses
 * @param {boolean} [options.parseBody=false] - Whether to parse response bodies as JSON
 * @returns {Array<Object>} - Array of responses
 */
function batchRequest(requests, options = { parseBody: false }) {
  let batch = http.batch();

  for (const request of requests) {
    if (!request.url) {
      throw new ScriptException('An HTTP request must have a URL');
    }

    if (!request.body) {
      batch = batch.request(
        request.method || 'GET',
        request.url,
        request.headers || {},
        request.auth || false,
      );
    } else {
      batch = batch.requestWithBody(
        request.method || 'GET',
        request.url,
        request.body,
        request.headers || {},
        request.auth || false,
      );
    }
  }

  const responses = batch.execute();

  if (!options.parseBody) {
    return responses;
  }
  
  return responses.map((response) => {
    return {
      isOk: response.isOk,
      code: response.code,
      body: JSON.parse(response.body),
    };
  });
}

//================ DATA TRANSFORMATION ================//

/**
 * Converts a BitChute video object to a PlatformVideo object
 * 
 * @param {Object} v - BitChute video object
 * @param {Object} [authorContent] - Optional channel information for the author
 * @returns {PlatformVideo} - Platform video object
 */
function BitchuteVideoToPlatformVideo(v, authorContent) {
  const videoUrl = `${URL_WEB.BASE}${v.video_url}`;

  const author = authorContent || new PlatformAuthorLink(
    new PlatformID(
      PLATFORM,
      v.channel?.channel_id ?? '',
      _config.id,
      PLATFORM_CLAIMTYPE,
    ),
    v.channel?.channel_name ?? '',
    `${URL_WEB.BASE}${v.channel?.channel_url ?? ''}`,
    v.channel?.thumbnail_url ?? '',
  );

  return new PlatformVideo({
    id: v.video_id && new PlatformID(PLATFORM, v.video_id, _config.id, PLATFORM_CLAIMTYPE),
    name: v.video_name,
    thumbnails: v.thumbnail_url && new Thumbnails([new Thumbnail(v.thumbnail_url, 0)]),
    duration: convertToSeconds(v.duration),
    viewCount: v.view_count,
    url: videoUrl,
    isLive: v.state_id === 'live',
    uploadDate: dateToUnixSeconds(v.date_published),
    shareUrl: videoUrl,
    author: author
  });
}

function formatThousands(n) {
  if (typeof n !== 'number' || !isFinite(n)) return null;
  return n.toLocaleString('en-US');
}

function formatChannelJoinedDate(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  if (isNaN(d.getTime())) return null;
  const yyyy = d.getUTCFullYear();
  const mm = String(d.getUTCMonth() + 1).padStart(2, '0');
  const dd = String(d.getUTCDate()).padStart(2, '0');
  return `${yyyy}.${mm}.${dd}`;
}

function categoryDisplayName(categoryId) {
  if (!categoryId) return null;
  if (CATEGORY_DISPLAY_NAMES[categoryId]) return CATEGORY_DISPLAY_NAMES[categoryId];
  return categoryId.charAt(0).toUpperCase() + categoryId.slice(1);
}

function buildChannelDescription(channelMeta) {
  const rows = [];
  if (channelMeta?.subscriber_count > 0) rows.push(['Subscribers', formatThousands(channelMeta.subscriber_count)]);
  const joined = formatChannelJoinedDate(channelMeta?.date_created);
  if (joined) rows.push(['Created at', joined]);
  const lastUpload = formatChannelJoinedDate(channelMeta?.last_video_published);
  if (lastUpload) rows.push(['Last upload', lastUpload]);
  if (channelMeta?.view_count > 0) rows.push(['Total Video Viewers', formatThousands(channelMeta.view_count)]);
  if (channelMeta?.video_count > 0) rows.push(['Video Uploaded', formatThousands(channelMeta.video_count)]);
  const cat = categoryDisplayName(channelMeta?.category_id);
  if (cat) rows.push(['Category', cat]);

  const original = (channelMeta?.description ?? '').trim();
  if (rows.length === 0) return original;

  const stats = 'Channel Detail\n\n' + rows.map(([label, value]) => `${label}: ${value}`).join('\n');
  return original ? `${stats}\n\n${original}` : stats;
}

/**
 * Transforms channel metadata to a PlatformChannel object
 *
 * @param {Object} channelMeta - Channel metadata from API
 * @returns {PlatformChannel} - Platform channel object
 */
function channelMetaToPlatformChannel(channelMeta) {
  const linksData = getChannelLinksByProfileId(channelMeta.profile_id);

  const links = {};

  (linksData.links || [])
    .sort((a, b) => a.position - b.position)
    .forEach((l) => {
      links[l.social_media_name] = l.url;
    });

  return new PlatformChannel({
    id: new PlatformID(
      PLATFORM,
      channelMeta?.channel_id ?? '',
      _config.id,
      PLATFORM_CLAIMTYPE,
    ),
    name: channelMeta?.channel_name ?? '',
    thumbnail: channelMeta?.thumbnail_url ?? '',
    banner: '',
    subscribers: channelMeta.subscriber_count,
    description: buildChannelDescription(channelMeta),
    url: `${URL_WEB.BASE}${channelMeta.channel_url}`,
    links,
  });
}

/**
 * Converts a comment to a BitchuteComment object with replies
 * 
 * @param {string} url - The URL context
 * @param {Object} c - Comment data
 * @param {Array<Object>} allComments - All comments for finding replies
 * @returns {BitchuteComment} - Comment with replies
 */
function ToComment(url, c, allComments) {
  const replies = allComments.filter((z) => z.parent === c.id);
  const replyCount = replies?.length ?? 0;

  return new BitchuteComment({
    contextUrl: url,
    author: new PlatformAuthorLink(
      new PlatformID(PLATFORM, c.creator ?? '', _config.id),
      c.fullname ?? '',
      '',
      c.profile_picture_url,
    ),
    message: c.content,
    rating: new RatingLikesDislikes(c.up_vote_count, c.down_vote_count),
    date: dateToUnixSeconds(c.created),
    replyCount: replyCount,
    context: { id: c.id },
    replies: replies.map((r) => ToComment(url, r, allComments)),
  });
}

//================ API FUNCTIONS ================//

/**
 * Gets authentication information for video comments
 * 
 * @param {string} videoId - ID of the video to get comment auth for
 * @returns {Object} - Authentication information
 */
function getCommentAuthForVideo(videoId) {
  return makeApiRequest(
    URL_API.VIDEO_COMMENTS_AUTH, 
    { video_id: videoId }
  );
}

/**
 * Throws a typed exception for a failed channel fetch based on the HTTP status code.
 *
 * @param {number} code - HTTP status code returned by the channel API
 * @param {string} channelUrl - Public channel URL, embedded in the error message for context
 * @throws {UnavailableException} when the channel is gone (404, 410)
 * @throws {ScriptException} for rate limits (429), server errors (5xx), and any other non-OK status
 */
function throwChannelFetchError(code, channelUrl) {
  const tag = `[${channelUrl}] (${code})`;
  if (code === 404 || code === 410) {
    throw new UnavailableException(`Channel is unavailable or does not exist ${tag}`);
  }
  if (code === 429) {
    throw new ScriptException(`Rate limited while fetching channel ${tag}`);
  }
  if (code >= 500) {
    throw new ScriptException(`BitChute server error while fetching channel ${tag}`);
  }
  throw new ScriptException(`Failed to fetch channel ${tag}`);
}

/**
 * Gets metadata for a channel by ID
 * 
 * @param {string} channelId - ID of the channel
 * @returns {Object} - Channel metadata
 */
function getChannelMeta(channelId) {
  if (state.channelMeta[channelId]) {
    return state.channelMeta[channelId];
  }

  const res = http.POST(URL_API.CHANNEL, JSON.stringify({ channel_id: channelId }), REQUEST_HEADERS, false);
  if (!res.isOk) {
    throwChannelFetchError(res.code, `${URL_WEB.BASE}/channel/${channelId}/`);
  }

  const response = JSON.parse(res.body);
  state.channelMeta[channelId] = response;
  return response;
}

/**
 * Gets links associated with a profile ID
 * 
 * @param {string} profileId - ID of the profile
 * @returns {Object} - Links data
 */
/**
 * Gets links associated with a profile ID with caching
 * 
 * @param {string} profileId - ID of the profile
 * @returns {Object} - Links data
 */
function getChannelLinksByProfileId(profileId) {
  // Initialize the profile links cache if it doesn't exist
  if (!state.profileLinks) {
    state.profileLinks = {};
  }
  
  if (!state.profileLinksTimeToLive) {
    state.profileLinksTimeToLive = {};
  }
  
  // Check if we have cached data for this profile
  if (state.profileLinks[profileId]) {
    const cachedTTL = state.profileLinksTimeToLive[profileId];
    if (cachedTTL && Date.now() < cachedTTL) {
      // Return cached data if still valid
      return state.profileLinks[profileId];
    }
  }
  
  // If no cache or expired, make the API request
  const body = JSON.stringify({ profile_id: profileId, offset: 0, limit: 10 });

  const linksData = makeApiRequest(URL_API.LINKS, body, REQUEST_HEADERS, false);
  
  // Cache the result if caching is enabled
  if (_settings.cacheChannelContent) {
    state.profileLinks[profileId] = linksData;
    
    // Use the same TTL as channel content
    const ttlMinutes = CHANNEL_CONTENT_TTL_OPTIONS[_settings.cacheChannelContentTimeToLiveIndex] || 10;
    state.profileLinksTimeToLive[profileId] = Date.now() + 1000 * 60 * ttlMinutes;
  }
  
  return linksData;
}


/**
 * Gets a channel from a URL with optimized batch loading
 * 
 * @param {string} url - Channel URL
 * @returns {PlatformChannel} - Platform channel
 */
function getChannelWithBatch(url) {
  const channelId = extractChannelId(url);
  
  // If we already have the channel data in cache, use it directly
  if (state.channel[url]) {
    return state.channel[url];
  }
  
  // Check if we have channel metadata cached
  let needsChannelRequest = true;
  let channelMeta;
  
  if (state.channelMeta[channelId]) {
    channelMeta = state.channelMeta[channelId];
    needsChannelRequest = false;
  }
  
  // Check if profile links are cached (if we have channel metadata)
  let needsLinksRequest = true;
  let profileId = channelMeta?.profile_id;
  
  if (profileId && state.profileLinks && state.profileLinks[profileId]) {
    const cachedTTL = state.profileLinksTimeToLive?.[profileId];
    if (cachedTTL && Date.now() < cachedTTL) {
      needsLinksRequest = false;
    }
  }
  
  // If everything is cached, we can return directly
  if (!needsChannelRequest && !needsLinksRequest) {
    // Build the channel directly from cache
    state.channel[url] = channelMetaToPlatformChannel(channelMeta);
    return state.channel[url];
  }
  
  // We need to make at least one request, so prepare a batch
  const batch = http.batch();
  
  // Add channel request if needed
  if (needsChannelRequest) {
    batch.POST(
      URL_API.CHANNEL, 
      JSON.stringify({ channel_id: channelId }),
      REQUEST_HEADERS,
      false
    );
  }
  
  // Add links request if we have profileId and need to update links
  if (profileId && needsLinksRequest) {
    batch.POST(
      URL_API.LINKS,
      JSON.stringify({ profile_id: profileId, offset: 0, limit: 10 }),
      REQUEST_HEADERS,
      false
    );
  }
  
  // Execute the batch request
  const responses = batch.execute();
  
  // Process the responses
  let responseIndex = 0;
  
  // Process channel response if we requested it
  if (needsChannelRequest) {
    const channelResponse = responses[responseIndex++];
    
    if (!channelResponse.isOk) {
      throwChannelFetchError(channelResponse.code, url);
    }
    
    channelMeta = JSON.parse(channelResponse.body);
    state.channelMeta[channelId] = channelMeta;
    profileId = channelMeta.profile_id;
    
    // If we didn't know the profileId before, we need to check if links are cached
    if (profileId && state.profileLinks && state.profileLinks[profileId]) {
      const cachedTTL = state.profileLinksTimeToLive?.[profileId];
      if (cachedTTL && Date.now() < cachedTTL) {
        needsLinksRequest = false;
      } else {
        // We need to make a separate request for links now that we have profileId
        needsLinksRequest = true;
      }
    } else {
      needsLinksRequest = true;
    }
  }
  
  // Process links response if we requested it
  let linksData;
  if (needsLinksRequest) {
    // If we just got the profileId from the channel request and didn't include
    // a links request in the batch, make a separate request now
    if (responseIndex >= responses.length) {
      linksData = getChannelLinksByProfileId(profileId);
    } else {
      // We included links in the batch
      const linksResponse = responses[responseIndex++];
      
      if (!linksResponse.isOk) {
        throw new ScriptException(`Failed request [${URL_API.LINKS}] (${linksResponse.code})`);
      }
      
      linksData = JSON.parse(linksResponse.body);
    }
    
    // Cache the links data
    if (_settings.cacheChannelContent) {
      if (!state.profileLinks) {
        state.profileLinks = {};
      }
      
      if (!state.profileLinksTimeToLive) {
        state.profileLinksTimeToLive = {};
      }
      
      state.profileLinks[profileId] = linksData;
      
      const ttlMinutes = CHANNEL_CONTENT_TTL_OPTIONS[_settings.cacheChannelContentTimeToLiveIndex] || 10;
      state.profileLinksTimeToLive[profileId] = Date.now() + 1000 * 60 * ttlMinutes;
    }
  } else {
    // Use cached links data
    linksData = state.profileLinks[profileId];
  }
  
  // Build the links object
  const links = {};
  
  (linksData.links || [])
    .sort((a, b) => a.position - b.position)
    .forEach((l) => {
      links[l.social_media_name] = l.url;
    });
  
  // Create and cache the channel
  state.channel[url] = new PlatformChannel({
    id: new PlatformID(
      PLATFORM,
      channelMeta?.channel_id ?? '',
      _config.id,
      PLATFORM_CLAIMTYPE,
    ),
    name: channelMeta?.channel_name ?? '',
    thumbnail: channelMeta?.thumbnail_url ?? '',
    banner: '',
    subscribers: channelMeta.subscriber_count,
    description: buildChannelDescription(channelMeta),
    url: `${URL_WEB.BASE}${channelMeta.channel_url}`,
    links,
  });

  return state.channel[url];
}

/**
 * Gets a profile from a URL
 * 
 * @param {string} url - Profile URL
 * @returns {PlatformChannel} - Platform channel for profile
 */
function getProfile(url) {
  const profile_id = extractProfileId(url);

  const profileMeta = makeApiRequest(URL_API.PROFILE, { profile_id }, REQUEST_HEADERS, false);

  return new PlatformChannel({
    id: new PlatformID(
      PLATFORM,
      profile_id,
      _config.id,
      PLATFORM_CLAIMTYPE,
    ),
    name: profileMeta?.profile_name ?? '',
    thumbnail: profileMeta?.thumbnail_url ?? '',
    banner: '',
    subscribers: 0,
    description: '',
    url: url
  });
}

//================ PAGER HELPERS ================//

/**
 * Creates a simple content pager for a single source
 * 
 * @param {Object} config - Configuration for the content request
 * @returns {VideoPager} - A pager that can load more content when needed
 */
function createContentPager(config, initialVideos = []) {
  class ContentPager extends VideoPager {
    constructor(results, hasMore, context) {
      super(results, hasMore, context);
    }

    nextPage() {
      // Calculate next offset
      const nextOffset = this.context.offset + this.context.limit;
      
      // Create cache key
      const cacheKey = this.context.cacheKey && this.context.cacheValue ? 
        `${this.context.cacheKey}:${this.context.cacheValue}:${nextOffset}` : null;
      
      // Check cache first if enabled
      if (_settings.cacheChannelContent && cacheKey && state.channelContent[cacheKey]) {
        const cachedTTL = state.channelContentTimeToLive[cacheKey];
        if (cachedTTL && Date.now() < cachedTTL) {
          const cachedData = state.channelContent[cacheKey];
          this.results = cachedData.videos;
          this.hasMore = cachedData.hasMore;
          this.context.offset = nextOffset;
          return this;
        }
      }
      
      // Build request body
      const requestBody = {
        ...this.context.requestBody,
        offset: nextOffset
      };
      
      // Make the request
      const responseBody = makeApiRequest(
        this.context.url,
        requestBody,
        REQUEST_HEADERS,
        this.context.auth || false
      );
      const transform = this.context.transform || (v => v);
      
      // Get videos from response
      let videos = [];
      if (responseBody[this.context.root]) {
        videos = responseBody[this.context.root].map(e => transform(e));
      }
      
      // Check if there are more pages
      const total = this.context.total || 
        (this.context.totalProperty ? responseBody[this.context.totalProperty] : 0);
      
      const hasMore = videos.length > 0 && (
        videos.length >= this.context.limit || 
        nextOffset + videos.length < total
      );
      
      // Update context
      this.context.offset = nextOffset;
      
      // Store in cache if enabled
      if (_settings.cacheChannelContent && cacheKey) {
        state.channelContent[cacheKey] = { videos, hasMore };
        const ttlMinutes = CHANNEL_CONTENT_TTL_OPTIONS[_settings.cacheChannelContentTimeToLiveIndex] || 10;
        state.channelContentTimeToLive[cacheKey] = Date.now() + 1000 * 60 * ttlMinutes;
      }
      
      this.results = videos;
      this.hasMore = hasMore;
      
      return this;
    }
  }

  // Prepare simplified context
  const context = {
    url: config.url,
    root: config.root,
    totalProperty: config.total_property,
    total: config.total,
    cacheKey: config.cacheKey,
    cacheValue: config.cacheValue,
    requestBody: {...config.request_body},
    offset: config.request_body.offset,
    limit: config.request_body.limit,
    auth: config.auth || false,
    transform: config.transform
  };
  
  // Create cache key for initial data
  const initialCacheKey = context.cacheKey && context.cacheValue ? 
    `${context.cacheKey}:${context.cacheValue}:${context.offset}` : null;
  
  // Check for cached initial data
  if (_settings.cacheChannelContent && initialCacheKey && state.channelContent[initialCacheKey]) {
    const cachedTTL = state.channelContentTimeToLive[initialCacheKey];
    if (cachedTTL && Date.now() < cachedTTL) {
      const cachedData = state.channelContent[initialCacheKey];
      return new ContentPager(
        [...initialVideos, ...cachedData.videos], 
        cachedData.hasMore, 
        context
      );
    }
  }
  
  // Make initial request if no cache hit
  const responseBody = makeApiRequest(
    config.url,
    config.request_body,
    REQUEST_HEADERS,
    config.auth || false
  );

  const transform = config.transform || (v => v);

  // Get videos or playlists from response
  let videos = [...initialVideos];
  if (responseBody[config.root]) {
    videos = [...videos, ...responseBody[config.root].map(e => transform(e))];
  }
  
  // Check if there are more pages
  const total = config.total || (config.total_property ? responseBody[config.total_property] : 0);
  const hasMore = responseBody[config.root] && responseBody[config.root].length > 0 && (
    responseBody[config.root].length >= config.request_body.limit || 
    config.request_body.offset + responseBody[config.root].length < total
  );
  
  // Store in cache if enabled
  if (_settings.cacheChannelContent && initialCacheKey) {
    const videosToCache = responseBody[config.root] ? 
      responseBody[config.root].map(e => transform(e)) : [];
    
    state.channelContent[initialCacheKey] = { 
      videos: videosToCache, 
      hasMore 
    };
    
    const ttlMinutes = CHANNEL_CONTENT_TTL_OPTIONS[_settings.cacheChannelContentTimeToLiveIndex] || 10;
    state.channelContentTimeToLive[initialCacheKey] = Date.now() + 1000 * 60 * ttlMinutes;
  }
  
  return new ContentPager(videos, hasMore, context);
}

//================ CUSTOM CLASSES ================//

/**
 * Comment class specific to BitChute with reply handling
 */
class BitchuteComment extends Comment {
  constructor(obj) {
    super(obj);
    this.replies = obj.replies;
  }

  getReplies() {
    return new BitchuteCommentPager(this.replies, 20);
  }
}

/**
 * Pager for BitChute comment replies
 */
class BitchuteCommentPager extends CommentPager {
  constructor(allResults, pageSize) {
    const end = Math.min(pageSize, allResults.length);
    const results = allResults.slice(0, end);
    const hasMore = pageSize < allResults.length;
    super(results, hasMore, {});

    this.offset = end;
    this.allResults = allResults;
    this.pageSize = pageSize;
  }

  nextPage() {
    const end = Math.min(this.offset + this.pageSize, this.allResults.length);
    this.results = this.allResults.slice(this.offset, end);
    this.offset = end;
    this.hasMore = end < this.allResults.length;
    return this;
  }
}

//================ CONTENT RETRIEVAL FUNCTIONS ================//

/**
 * Gets channel contents from a channel ID
 * 
 * @param {string} url - Channel URL
 * @returns {VideoPager} - Pager for channel videos
 */
function getChannelContents(url) {
  const channelId = extractChannelId(url);
  const sourceChannel = getChannelMeta(channelId);
  const channel = channelMetaToPlatformChannel(sourceChannel);

  const config = {
    cacheKey: 'channel_id',
    cacheValue: channelId,
    url: URL_API.CHANNEL_VIDEOS,
    root: 'videos',
    total_property: 'video_count',
    total: sourceChannel.video_count,
    request_body: {
      offset: 0,
      limit: 10,
      channel_id: channelId,
    },
    transform: (r) => BitchuteVideoToPlatformVideo(r, channel)
  };

  return createContentPager(config);
}

/**
 * Gets profile contents from a profile ID
 * 
 * @param {string} url - Profile URL
 * @returns {VideoPager} - Pager for profile videos
 */
function getProfileContents(url) {
  const profile_id = extractProfileId(url);

  const config = {
    cacheKey: 'profile_id',
    cacheValue: profile_id,
    url: URL_API.PROFILE_VIDEOS,
    root: 'videos',
    total_property: 'video_count',
    request_body: {
      offset: 0,
      limit: 10,
      profile_id: profile_id
    },
    transform: BitchuteVideoToPlatformVideo
  };

  return createContentPager(config);
}

function makeApiRequest(url, body, headers = REQUEST_HEADERS, requiresAuth = false) {
  try {
    // only stringify if body is an object
    if (typeof body === 'object') {
      body = JSON.stringify(body);
    }

    const res = http.POST(url, body, headers, requiresAuth);

    if (!res.isOk) {
      throw new ScriptException(`API request failed: ${url} (${res.code})`);
    }

    try {
      return JSON.parse(res.body);
    } catch (parseError) {
      log(`Error parsing JSON from ${url}: ${parseError}`);
      log(`Response body: ${res.body.substring(0, 200)}...`);
      throw new ScriptException(`Invalid JSON response from ${url}: ${parseError.message}`);
    }
  } catch (error) {
    if (error instanceof ScriptException) {
      throw error;
    }
    throw new ScriptException(`API request error for ${url}: ${error.message}`);
  }
}

log('LOADED');
