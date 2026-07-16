export const BASE_URL = 'https://www.dailymotion.com';

// Use graphql-ix7 subdomain which has consistent SSL certificates
// The main graphql.api.dailymotion.com has misconfigured certs on ~50% of load balancer nodes
export const BASE_URL_API = 'https://graphql-ix7.api.dailymotion.com';

// Search uses a dedicated endpoint (observed from browser behavior)
// This helps avoid rate limiting on the main GraphQL API
export const BASE_URL_SEARCH_API = 'https://search.dailymotion.com';

export const BASE_URL_COMMENTS =
  'https://api-2-0.spot.im/v1.0.0/conversation/read';

export const BASE_URL_COMMENTS_AUTH =
  'https://api-2-0.spot.im/v1.0.0/authenticate';

export const BASE_URL_COMMENTS_THUMBNAILS =
  'https://images.spot.im/image/upload';

export const BASE_URL_VIDEO = `${BASE_URL}/video`;

export const BASE_URL_PLAYLIST = `${BASE_URL}/playlist`;

export const BASE_URL_METADATA = `${BASE_URL}/player/metadata/video`;

export const REGEX_VIDEO_URL =
  /^https:\/\/(?:www\.)?dailymotion\.com\/video\/[a-zA-Z0-9]+$/i;

export const REGEX_VIDEO_URL_1 = /^https:\/\/dai\.ly\/[a-zA-Z0-9]+$/i;

export const REGEX_VIDEO_URL_EMBED =
  /^https:\/\/(?:www\.)?dailymotion\.com\/embed\/video\/[a-zA-Z0-9]+(\?.*)?$/i;


export const REGEX_VIDEO_CHANNEL_URL =
 /^https:\/\/(?:www\.)?dailymotion\.com\/[a-z0-9][a-z0-9._-]{2,26}(?:\?[a-zA-Z0-9=&._-]*)?$/i;
  
export const REGEX_VIDEO_PLAYLIST_URL =
  /^https:\/\/(?:www\.)?dailymotion\.com\/playlist\/[a-zA-Z0-9]+(?:[?&][a-zA-Z0-9_\-=&%]*)?$/i;

export const REGEX_INITIAL_DATA_API_AUTH_1 =
  /(?<=window\.__LOADABLE_LOADED_CHUNKS__=.*)\b[a-f0-9]{20}\b|\b[a-f0-9]{40}\b/g;

export const REGEX_API_CLIENT_ID = /get apiClientId\(\)\{return"([a-f0-9]{20})"\}/;
export const REGEX_API_CLIENT_SECRET = /get apiClientSecret\(\)\{return"([a-f0-9]{40})"\}/;

// Pattern to find the app.js URL in the homepage HTML
export const REGEX_APP_JS_URL = /static\/app\.[a-f0-9]+\.js/;

// Pattern to extract API_ENDPOINT from homepage (e.g., "API_ENDPOINT: 'https://graphql-ix7.api.dailymotion.com'")
export const REGEX_API_ENDPOINT = /API_ENDPOINT:\s*'(https:\/\/[^']+)'/;

export const createAuthRegexByTextLength = (length: number) =>
  new RegExp(`\\b\\w+\\s*=\\s*"([a-zA-Z0-9]{${length}})"`);

export const USER_AGENT =
  'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36';

export const FALLBACK_SPOT_ID = 'sp_vWPN1lBu';
export const PLATFORM = 'Dailymotion';
export const PLATFORM_CLAIMTYPE = 27;

// search capabilities - upload date
export const LESS_THAN_MINUTE = 'LESS_THAN_MINUTE';
export const ONE_TO_FIVE_MINUTES = 'ONE_TO_FIVE_MINUTES';
export const FIVE_TO_THIRTY_MINUTES = 'FIVE_TO_THIRTY_MINUTES';
export const THIRTY_TO_ONE_HOUR = 'THIRTY_TO_ONE_HOUR';
export const MORE_THAN_ONE_HOUR = 'MORE_THAN_ONE_HOUR';

export const DURATION_THRESHOLDS = {};
DURATION_THRESHOLDS[LESS_THAN_MINUTE] = { min: 0, max: 60 };
DURATION_THRESHOLDS[ONE_TO_FIVE_MINUTES] = { min: 60, max: 300 };
DURATION_THRESHOLDS[FIVE_TO_THIRTY_MINUTES] = { min: 300, max: 1800 };
DURATION_THRESHOLDS[THIRTY_TO_ONE_HOUR] = { min: 1800, max: 3600 };
DURATION_THRESHOLDS[MORE_THAN_ONE_HOUR] = { min: 3600, max: null };

export const LIKED_VIDEOS_PLAYLIST_ID = 'LIKE_PLAYLIST';
export const FAVORITE_VIDEOS_PLAYLIST_ID = 'FAVORITES_PLAYLIST';
export const RECENTLY_WATCHED_VIDEOS_PLAYLIST_ID = 'RECENTLY_WATCHED_PLAYLIST';

/** The possible values which liked media connections can be sorted by. */
export const LikedMediaSort = {
  /** Sort liked medias by most recent. */
  Recent: 'recent',
  /** Sort liked medias by most viewed. */
  Visited: 'visited',
};

// This platform uses a scale system for rating the videos.
// Ratings are grouped into positive and negative to calculate likes and dislikes.
export const POSITIVE_RATINGS_LABELS = [
  'STAR_STRUCK', // amazing
  'SMILING_FACE_WITH_SUNGLASSES', // cool
  'WINKING_FACE', // interesting
];

export const NEGATIVE_RATINGS_LABELS = [
  'SLEEPING_FACE', // boring
  'FISHING_POLE', // waste of time
];

export const ERROR_TYPES = {
  DM001: 'No video has been specified, you need to specify one.',
  DM002: 'Content has been deleted.',
  DM003: 'Live content is not available, i.e. it may not have started yet.',
  DM004: 'Copyrighted content, access forbidden.',
  DM005:
    'Content rejected (this video may have been removed due to a breach of the terms of use, a copyright claim or an infringement upon third party rights).',
  DM006: 'Publishing in progress…',
  DM007: 'Video geo-restricted by its owner.',
  DM008:
    'Explicit content. Explicit content can be enabled using the plugin settings',
  DM009: 'Explicit content (offsite embed)',
  DM010: 'Private content',
  DM011: 'An encoding error occurred',
  DM012: 'Encoding in progress',
  DM013: 'This video has no preset (no video stream)',
  DM014: 'This video has not been made available on your device by its owner',
  DM015: 'Kids host error',
  DM016:
    'Content not available on this website, it can only be watched on Dailymotion',
  DM019:
    'This content has been uploaded by an inactive channel and its access is limited',
};

export const SEARCH_CAPABILITIES = {
  types: [Type.Feed.Mixed],
  sorts: ['Most Recent', 'Most Viewed', 'Most Relevant'],
  filters: [
    {
      id: 'uploaddate',
      name: 'Upload Date',
      isMultiSelect: false,
      filters: [
        { name: 'Today', value: 'today' },
        { name: 'Past week', value: 'thisweek' },
        { name: 'Past month', value: 'thismonth' },
        { name: 'Past year', value: 'thisyear' },
      ],
    },
    {
      id: 'duration',
      name: 'Duration',
      isMultiSelect: false,
      filters: [
        { name: '< 1 min', value: LESS_THAN_MINUTE },
        { name: '1 - 5 min', value: ONE_TO_FIVE_MINUTES },
        { name: '5 - 30 min', value: FIVE_TO_THIRTY_MINUTES },
        { name: '30 min - 1 hour', value: THIRTY_TO_ONE_HOUR },
        { name: '> 1 hour', value: MORE_THAN_ONE_HOUR },
      ],
    },
  ],
};

// Used to on source.getUserPlaylists to specify if the playlist is private or not. This is read by source.getPlaylist to enable the authentication context.
export const PRIVATE_PLAYLIST_QUERY_PARAM_FLAGGER = '&private=1';

export const DEFAULT_HEADERS:Record<string, string> = {
  'User-Agent': USER_AGENT,
  Origin: BASE_URL,
  'X-DM-AppInfo-Id': 'com.dailymotion.neon',
  'Referer':'https://www.dailymotion.com/'
};

export const IS_DESKTOP = bridge.buildPlatform === "desktop";

export const IMPERSONATION_TARGET = IS_DESKTOP ? 'chrome136' : 'chrome131_android';

export const IS_IMPERSONATION_AVAILABLE = (typeof httpimp !== 'undefined');