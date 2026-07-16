let config: Config;
let _settings: IDailymotionPluginSettings;

const state = {
  anonymousUserAuthorizationToken: '',
  anonymousUserAuthorizationTokenExpirationDate: 0,
  commentWebServiceToken: '',
  channelsCache: {} as Record<string, PlatformChannel>,
  maintenanceMode: false,
  visitorId: '',
  visitId: '',
  apiEndpoint: '',
  apiAuthEndpoint: ''
};

import {
  BASE_URL,
  SEARCH_CAPABILITIES,
  BASE_URL_PLAYLIST,
  BASE_URL_API,
  BASE_URL_SEARCH_API,
  BASE_URL_METADATA,
  ERROR_TYPES,
  LikedMediaSort,
  PLATFORM,
  BASE_URL_COMMENTS,
  BASE_URL_COMMENTS_AUTH,
  BASE_URL_COMMENTS_THUMBNAILS,
  FAVORITE_VIDEOS_PLAYLIST_ID,
  LIKED_VIDEOS_PLAYLIST_ID,
  RECENTLY_WATCHED_VIDEOS_PLAYLIST_ID,
  REGEX_VIDEO_CHANNEL_URL,
  REGEX_VIDEO_PLAYLIST_URL,
  REGEX_VIDEO_URL,
  REGEX_VIDEO_URL_1,
  REGEX_VIDEO_URL_EMBED,
  PRIVATE_PLAYLIST_QUERY_PARAM_FLAGGER,
  FALLBACK_SPOT_ID,
  IS_IMPERSONATION_AVAILABLE,
  IMPERSONATION_TARGET,
} from './constants';

import {
  AUTOCOMPLETE_QUERY,
  CHANNEL_QUERY_DESKTOP,
  PLAYLIST_DETAILS_QUERY,
  GET_USER_SUBSCRIPTIONS,
  SEARCH_QUERY,
  SEACH_DISCOVERY_QUERY,
  CHANNEL_VIDEOS_QUERY,
  WATCHING_VIDEO,
  SEARCH_CHANNEL,
  CHANNEL_PLAYLISTS_QUERY,
  SUBSCRIPTIONS_QUERY,
  GET_CHANNEL_PLAYLISTS_XID,
  USER_LIKED_VIDEOS_QUERY,
  USER_WATCHED_VIDEOS_QUERY,
  USER_WATCH_LATER_VIDEOS_QUERY,
  DISCOVERY_QUEUE_QUERY,
  playerVideosDataQuery,
  GET_SHORTS_FEED_QUERY,
} from './gqlQueries';

import {
  getChannelNameFromUrl,
  getQuery,
  generateUUIDv4,
  applyCommonHeaders,
  notifyMaintenanceMode
} from './util';

import {
  Channel,
  Collection,
  CollectionConnection,
  Live,
  LiveConnection,
  LiveEdge,
  Maybe,
  SuggestionConnection,
  User,
  Video,
  VideoConnection,
  VideoEdge,
} from '../types/CodeGenDailymotion';

import {
  SearchPagerAll,
  SearchChannelPager,
  ChannelVideoPager,
  SearchPlaylistPager,
  ChannelPlaylistPager,
} from './Pagers';

import {
  SourceChannelToGrayjayChannel,
  SourceCollectionToGrayjayPlaylist,
  SourceCollectionToGrayjayPlaylistDetails,
  SourceVideoToGrayjayVideo,
  SourceVideoToPlatformVideoDetailsDef,
} from './Mappers';

import {
  IDailymotionPluginSettings,
  IPlatformSystemPlaylist,
} from '../types/types';
import {
  extractApiEndpoint,
  extractClientCredentials,
  getTokenFromClientCredentials,
} from './extraction';

source.setSettings = function (settings) {
  _settings = settings;
};

let COUNTRY_NAMES_TO_CODE: string[] = [];
let VIDEOS_PER_PAGE_OPTIONS: number[] = [];
let PLAYLISTS_PER_PAGE_OPTIONS: number[] = [];
let CREATOR_AVATAR_HEIGHT: string[] = [];
let THUMBNAIL_HEIGHT: string[] = [];

let webclient;

if (IS_IMPERSONATION_AVAILABLE) {
    
    const httpImpClient = httpimp.getDefaultClient(true);

    if(httpImpClient.setDefaultImpersonateTarget) {
        httpImpClient.setDefaultImpersonateTarget(IMPERSONATION_TARGET);
    }

    webclient = httpimp;
} 
else{
    webclient = http;
}

//Source Methods
source.enable = function (conf, settings, saveStateStr) {
  config = conf ?? {};

  COUNTRY_NAMES_TO_CODE =
    config?.settings?.find((s) => s.variable == 'preferredCountryOptionIndex')
      ?.options ?? [];

  VIDEOS_PER_PAGE_OPTIONS =
    config?.settings
      ?.find((s) => s.variable == 'videosPerPageOptionIndex')
      ?.options?.map((s) => parseInt(s)) ?? [];

  PLAYLISTS_PER_PAGE_OPTIONS =
    config?.settings
      ?.find((s) => s.variable == 'playlistsPerPageOptionIndex')
      ?.options?.map((s) => parseInt(s)) ?? [];

  CREATOR_AVATAR_HEIGHT =
    config?.settings
      ?.find((s) => s.variable == 'avatarSizeOptionIndex')
      ?.options?.map((s) => `SQUARE_${s.replace('px', '')}`) ?? [];

  THUMBNAIL_HEIGHT =
    config?.settings
      ?.find((s) => s.variable == 'thumbnailResolutionOptionIndex')
      ?.options?.map((s) => `PORTRAIT_${s.replace('px', '')}`) ?? [];

  const DEFAULT_SETTINGS = {
    hideSensitiveContent: true,
    avatarSizeOptionIndex: 8, // 720px
    thumbnailResolutionOptionIndex: 7, // 1080px
    preferredCountryOptionIndex: 0, // empty
    videosPerPageOptionIndex: 3, // 20
    playlistsPerPageOptionIndex: 0, // 5
  };

  _settings = { ...DEFAULT_SETTINGS, ...settings };

  if (IS_TESTING) {
    config.id = '9c87e8db-e75d-48f4-afe5-2d203d4b95c5';
  }

  let didSaveState = false;

  try {
    if (saveStateStr) {
      const saveState = JSON.parse(saveStateStr);
      if (saveState) {

        Object
          .keys(state)
          .forEach((key) => {
            state[key] = saveState[key];
          });

        if (!isTokenValid()) {
          log('Token expired. Fetching a new one.');
        } else {
          didSaveState = true;
          log('Using save state');
        }

        // Ensure visitor IDs are set (regenerate if missing from old save states)
        if (!state.visitorId) {
          state.visitorId = generateUUIDv4();
        }
        if (!state.visitId) {
          state.visitId = Date.now().toString();
        }
      }
    }
  } catch (ex) {
    log('Failed to parse saveState:' + ex);
    didSaveState = false;
  }

  if (!didSaveState) {
    if(IS_TESTING){
      log('Getting a new tokens');
    }

    let detailsRequestHtml;
    
    try {

      detailsRequestHtml = webclient.GET(BASE_URL, applyCommonHeaders(), false);
      
      if (!detailsRequestHtml.isOk) {
        if (detailsRequestHtml.code >= 500 && detailsRequestHtml.code < 600) {
          state.maintenanceMode = true;
          notifyMaintenanceMode();
        } else {
          throw new ScriptException('Failed to fetch page to extract auth details');
        }
        return;
      }
    } catch(e) {
      state.maintenanceMode = true;
      notifyMaintenanceMode();
      return;
    }

    state.maintenanceMode = false;

    // Initialize visitor tracking IDs BEFORE getting token
    // so the same visitor ID is used in both the token request and subsequent requests
    state.visitorId = generateUUIDv4();
    state.visitId = Date.now().toString();

    // Extract API endpoint from homepage (dynamic to avoid hardcoding subdomain like graphql-ix7)
    state.apiEndpoint = extractApiEndpoint(detailsRequestHtml.body);
    state.apiAuthEndpoint = `${state.apiEndpoint}/oauth/token`;

    const clientCredentials = extractClientCredentials(detailsRequestHtml, webclient);

    const {
      anonymousUserAuthorizationToken,
      anonymousUserAuthorizationTokenExpirationDate,
      isValid,
    } = getTokenFromClientCredentials(webclient, clientCredentials, state.visitorId, state.apiAuthEndpoint);

    if (!isValid) {
      console.error('Failed to get token');
      throw new ScriptException('Failed to get authentication token');
    }

    state.channelsCache = {};

    state.anonymousUserAuthorizationToken =
      anonymousUserAuthorizationToken ?? '';
    state.anonymousUserAuthorizationTokenExpirationDate =
      anonymousUserAuthorizationTokenExpirationDate ?? 0;

    if (config.allowAllHttpHeaderAccess) {
      // get token for message service api-2-0.spot.im
      try {
        const authenticateIm = webclient.POST(
          BASE_URL_COMMENTS_AUTH,
          '',
          applyCommonHeaders({
            'x-spot-id': FALLBACK_SPOT_ID,//
            'x-post-id': 'no$post',
          }),
          false,
        );

        if (!authenticateIm.isOk) {
          log('Failed to authenticate to comments service');
        }

        state.commentWebServiceToken = authenticateIm?.headers?.['x-access-token']?.[0];
      } catch (error) {
        log('Failed to authenticate to comments service:' + error);
      }
    }
  }
};

source.getHome = function () {

  if (state.maintenanceMode) {
    return new ContentPager([]);
  }

  return getHomePager({}, 0);
};

source.getShorts = function () {

  if (state.maintenanceMode) {
    return new ContentPager([]);
  }

  return getShortsPager({}, 0);
};

source.searchSuggestions = function (query): string[] {
  try {
    const [error, gqlResponse] = executeGqlQuery(http, {
      operationName: 'AUTOCOMPLETE_QUERY',
      variables: {
        query,
      },
      query: AUTOCOMPLETE_QUERY,
    });

    if (error) {
      log(`Failed to get search suggestions: [${error.code}]} (${error.operationName})`);
      return [];
    }

    return (
      (
        gqlResponse?.data?.search?.suggestedVideos as SuggestionConnection
      )?.edges?.map((edge) => edge?.node?.name ?? '') ?? []
    );
  } catch (error: any) {
    log('Failed to get search suggestions:' + error?.message);
    return [];
  }
};

source.getSearchCapabilities = (): ResultCapabilities => SEARCH_CAPABILITIES;

source.search = function (query: string, type: string, order: string, filters) {
  return getSearchPagerAll({ q: query, page: 1, type, order, filters });
};

source.searchChannels = function (query) {
  return getSearchChannelPager({ q: query, page: 1 });
};

//Channel
source.isChannelUrl = function (url) {
  return REGEX_VIDEO_CHANNEL_URL.test(url);
};

source.getChannel = function (url) {

  if(!state?.channelsCache){
    state.channelsCache = {};
  }

  if(state.channelsCache[url]){
    return state.channelsCache[url];
  }

  const channel_name = getChannelNameFromUrl(url);

  try {

    const gqlParams = {
      operationName: 'CHANNEL_QUERY_DESKTOP',
      variables: {
        channel_name,
        avatar_size: CREATOR_AVATAR_HEIGHT[_settings?.avatarSizeOptionIndex],
      },
      query: CHANNEL_QUERY_DESKTOP,
    };

    let channelDetails;

    const [error1, channelDetails1] = executeGqlQuery(http, gqlParams);

    if (error1) {
      log(`Failed to get channel: [${error1.code}] (${error1.operationName})`);
 
      if(error1.code === 'GQL_ERROR') {
        const [err] = error1.errors;

        if(err.type === 'moved_permanently') {

          gqlParams.variables.channel_name = err.redirect_id;
          const [error2, channelDetails2] = executeGqlQuery(http, gqlParams);

          if (error2) {
            log(`Failed to get channel: [${error2.code}] (${error2.operationName})`);
            throw new ScriptException('Failed to get channel');
          }

          channelDetails = channelDetails2;
  
        } else {
          throw new ScriptException('Failed to get channel');
        }
      } else {
        throw new ScriptException('Failed to get channel');
      }
    } else {
      channelDetails = channelDetails1;
    }

    if(!channelDetails) {
      throw new ScriptException('Failed to get channel');
    }

    state.channelsCache[url] = SourceChannelToGrayjayChannel(
      config.id,
      channelDetails.data.channel as Channel,
      url,
    );

    return state.channelsCache[url];
  } catch (error) {
    log('Failed to get channel:' + error);
    return null;
  }
};

source.getChannelContents = function (url, type, order, filters) {

  if (state.maintenanceMode) {
    return new ContentPager([]);
  }

  const page = 1;

  const parsedUrl = new URL(url);
  const sortQuery = parsedUrl.searchParams.get('sort');

  if(sortQuery){
    switch(sortQuery) {
      case 'visited':
        order = 'Popular';
        break;
    }
  }

  return getChannelContentsPager(url, page, type, order, filters);
};

source.getChannelPlaylists = (url): SearchPlaylistPager => {
  try {
    return getChannelPlaylists(url, 1);
  } catch (error) {
    log('Failed to get channel playlists:' + error);
    return new ChannelPlaylistPager([]);
  }
};

source.getChannelCapabilities = (): ResultCapabilities => {
  return {
    types: [Type.Feed.Mixed],
    sorts: [Type.Order.Chronological, 'Popular'],
    filters: [],
  };
};

//Video
source.isContentDetailsUrl = function (url) {
  return [REGEX_VIDEO_URL, REGEX_VIDEO_URL_1, REGEX_VIDEO_URL_EMBED].some((r) =>
    r.test(url),
  );
};

source.getContentDetails = function (url) {
  return getSavedVideo(url, false);
};

source.saveState = () => {
  return JSON.stringify(state);
};

source.getSubComments = (comment) => {
  const params = {
    count: 5,
    offset: 0,
    parent_id: comment.context.id,
    sort_by: 'best',
    child_count: comment.replyCount,
  };
  return getCommentPager(comment.contextUrl, params, 0);
};

source.getComments = (url) => {
  if (!config.allowAllHttpHeaderAccess) {
    return new PlatformCommentPager([], false, url, {}, 0);
  }

  const params = {
    sort_by: 'best',
    offset: 0,
    count: 10,
    message_id: null,
    depth: 2,
    child_count: 2,
  };
  return getCommentPager(url, params, 0);
};

function getCommentPager(url, params, page) {
  try {
    const xid = url.split('/').pop();

    const commentsHeaders = applyCommonHeaders({
      'x-access-token': state.commentWebServiceToken,
      'x-spot-id': FALLBACK_SPOT_ID,
      'x-post-id': xid,
    });

    const commentRequest = webclient.POST(
      BASE_URL_COMMENTS,
      JSON.stringify(params),
      commentsHeaders,
      false,
    );

    if (!commentRequest.isOk) {
      throw new UnavailableException(
        'Failed to authenticate to comments service',
      );
    }

    const comments = JSON.parse(commentRequest.body);

    const users = comments.conversation.users;

    const results = comments.conversation.comments.map((v) => {
      const user = users[v.user_id];

      return new Comment({
        contextUrl: url,
        author: new PlatformAuthorLink(
          new PlatformID(PLATFORM, user.id ?? '', config.id),
          user.display_name ?? '',
          '',
          `${BASE_URL_COMMENTS_THUMBNAILS}/${user.image_id}`,
        ),
        message: v.content[0].text,
        rating: new RatingLikes(v.stars),
        date: v.written_at,
        replyCount: v.total_replies_count ?? 0,
        context: { id: v.id },
      });
    });

    return new PlatformCommentPager(
      results,
      comments.conversation.has_next,
      url,
      params,
      ++page,
    );
  } catch (error) {
    bridge.log('Failed to get comments:' + error);
    return new PlatformCommentPager([], false, url, params, 0);
  }
}

class PlatformCommentPager extends CommentPager {
  constructor(results, hasMore, path, params, page) {
    super(results, hasMore, { path, params, page });
  }

  nextPage() {
    return getCommentPager(
      this.context.path,
      this.context.params,
      (this.context.page ?? 0) + 1,
    );
  }
}

//Playlist
source.isPlaylistUrl = (url): boolean => {
  return (
    REGEX_VIDEO_PLAYLIST_URL.test(url) || [
      LIKED_VIDEOS_PLAYLIST_ID,
      FAVORITE_VIDEOS_PLAYLIST_ID,
      RECENTLY_WATCHED_VIDEOS_PLAYLIST_ID
    ].includes(url)
  );
};

source.searchPlaylists = (query, type, order, filters) => {
  return searchPlaylists({ q: query, type, order, filters });
};

source.getPlaylist = (url: string): PlatformPlaylistDetails => {

  const thumbnailResolutionIndex = _settings.thumbnailResolutionOptionIndex;

  if (url === LIKED_VIDEOS_PLAYLIST_ID) {
    return getLikePlaylist(
      config.id,
      http,
      true, //usePlatformAuth,
      thumbnailResolutionIndex,
    );
  }

  if (url === FAVORITE_VIDEOS_PLAYLIST_ID) {
    return getFavoritesPlaylist(
      config.id,
      http,
      true, //usePlatformAuth,
      thumbnailResolutionIndex,
    );
  }

  if (url === RECENTLY_WATCHED_VIDEOS_PLAYLIST_ID) {
    return getRecentlyWatchedPlaylist(
      config.id,
      http,
      true, //usePlatformAuth,
      thumbnailResolutionIndex,
    );
  }

  const isPrivatePlaylist = url.includes(PRIVATE_PLAYLIST_QUERY_PARAM_FLAGGER);

  if(isPrivatePlaylist){
    url = url.replace(PRIVATE_PLAYLIST_QUERY_PARAM_FLAGGER, '');  //remove the private flag
  }

  const xid = url.split('/').pop();

  const variables = {
    xid,
    avatar_size: CREATOR_AVATAR_HEIGHT[_settings.avatarSizeOptionIndex],
    thumbnail_resolution: THUMBNAIL_HEIGHT[thumbnailResolutionIndex],
  };

  const [error, gqlResponse] = executeGqlQuery(http, {
    operationName: 'PLAYLIST_VIDEO_QUERY',
    variables,
    query: PLAYLIST_DETAILS_QUERY,
    usePlatformAuth: isPrivatePlaylist,
  });

  if (error) {
    log(`Failed to get playlist: [${error.code}] (${error.operationName})`);
    
    // Check if the error is a "not_found" type
    if (error.code === 'GQL_ERROR' && error.errors) {
      const notFoundError = error.errors.find((e) => e.type === 'not_found');
      if (notFoundError) {
        throw new UnavailableException('The playlist was not found. It may have been deleted, made private, or the URL is incorrect.');
      }
    }

    throw new UnavailableException(`Failed to get playlist - ${error.code}`);
  }

  const videos: PlatformVideo[] =
    gqlResponse?.data?.collection?.videos?.edges.map((edge) => {
      return SourceVideoToGrayjayVideo(config.id, edge.node as Video);
    });

  return SourceCollectionToGrayjayPlaylistDetails(
    config.id,
    gqlResponse?.data?.collection as Collection,
    videos,
  );
};

source.getUserSubscriptions = (): string[] => {
  if (!bridge.isLoggedIn()) {
    log('Failed to retrieve subscriptions page because not logged in.');
    throw new ScriptException('Not logged in');
  }

  const usePlatformAuth = true;

  const fetchSubscriptions = (page, first): string[] => {
    const [error, gqlResponse] = executeGqlQuery(http, {
      operationName: 'SUBSCRIPTIONS_QUERY',
      variables: {
        first: first,
        page: page,
      },
      headers: applyCommonHeaders(),
      query: GET_USER_SUBSCRIPTIONS,
      usePlatformAuth,
    });

    if (error) {
      log(`Failed to fetch subscriptions: [${error.code}]} (${error.operationName})`);
      return [];
    }

    return (
      (gqlResponse?.data?.me?.channel as Channel)?.followings?.edges?.map(
        (edge) => edge?.node?.creator?.name ?? '',
      ) ?? []
    );
  };

  const first = 100; // Number of records to fetch per page
  let page = 1;
  const subscriptions: string[] = [];

  // There is a totalCount ($.data.me.channel.followings.totalCount) property but it's not reliable.
  // For example, it may return 0 even if there are subscriptions, or it may return a number that is not the actual number of subscriptions.
  // For now, it's better to fetch until no more results are returned

  let items: string[] = [];

  do {
    const response = fetchSubscriptions(page, first);

    items = response.map((creatorName) => `${BASE_URL}/${creatorName}`);

    subscriptions.push(...items);
    page++;
  } while (items.length);

  return subscriptions;
};

source.getUserPlaylists = (): string[] => {
  if (!bridge.isLoggedIn()) {
    log('Failed to retrieve subscriptions page because not logged in.');
    throw new ScriptException('Not logged in');
  }

  const headers = applyCommonHeaders();

  const [error, gqlResponse] = executeGqlQuery(http, {
    operationName: 'SUBSCRIPTIONS_QUERY',
    headers,
    query: SUBSCRIPTIONS_QUERY,
    usePlatformAuth: true,
  });

  if (error) {
    log(`Failed to get user playlists: [${error.code}]} (${error.operationName})`);
    return [];
  }

  const userName = (gqlResponse?.data?.me?.channel as Channel)?.name;
  
  if (!userName) {
    log('Failed to get username from response');
    return [];
  }

  const playlists = getPlaylistsByUsername(userName, headers, true);

  // Used to trick migration "Import Playlists" to import "Favorites", "Recently Watched" and "Liked Videos"
  [
    LIKED_VIDEOS_PLAYLIST_ID,
    FAVORITE_VIDEOS_PLAYLIST_ID,
    RECENTLY_WATCHED_VIDEOS_PLAYLIST_ID,
  ]
    .forEach((playlistId) => {
      if (!playlists.includes(playlistId)) {
        playlists.push(playlistId);
      }
    });

  return playlists;
};

source.getChannelTemplateByClaimMap = () => {
  return {
    //Dailymotion claim type
    27: {
      0: BASE_URL + '/{{CLAIMVALUE}}',
    },
  };
};

source.getContentRecommendations = (url, initialData) => {

  try {
    const videoXid = url.split('/').pop();

    const [error1, gqlResponse] = executeGqlQuery(http, {
      operationName: 'DISCOVERY_QUEUE_QUERY',
      variables: {
        videoXid,
        videoCountPerSection: 25
      },
      query: DISCOVERY_QUEUE_QUERY,
      usePlatformAuth: false,
    });

    if (error1) {
      log(`Failed to get video recommendations: [${error1.code}] ${error1.status || 'Unknown error'} (${error1.operationName})`);
      return new VideoPager([], false);
    }

    const videoXids: string[] = gqlResponse?.data?.views?.neon?.sections?.edges?.[0]?.node?.components?.edges?.map(e => e.node.xid) ?? [];

    if (!videoXids.length) {
      log('No video recommendations found');
      return new VideoPager([], false);
    }

    const [error2, gqlResponse1] = executeGqlQuery(http, {
      operationName: 'playerVideosDataQuery',
      variables: {
        first: 30,
        avatar_size: CREATOR_AVATAR_HEIGHT[_settings.avatarSizeOptionIndex],
        thumbnail_resolution: THUMBNAIL_HEIGHT[_settings.thumbnailResolutionOptionIndex],
        videoXids
      },
      query: playerVideosDataQuery,
      usePlatformAuth: false,
    });

    if (error2) {
      log('Failed to get video details:' + error2.message);
      return new VideoPager([], false);
    }

    const results =
      gqlResponse1.data.videos.edges
        ?.map((edge) => {
          return SourceVideoToGrayjayVideo(config.id, edge.node as Video);
        });

    return new VideoPager(results, false);
  } catch(error){
    log('Failed to get recommendations:' + error);
    return new VideoPager([], false);
  }

}

function getPlaylistsByUsername(
  userName,
  headers,
  usePlatformAuth = false,
): string[] {
  const [error, collections] = executeGqlQuery(http, {
    operationName: 'CHANNEL_PLAYLISTS_QUERY',
    variables: {
      channel_name: userName,
      sort: 'recent',
      page: 1,
      first: 99,
      avatar_size: CREATOR_AVATAR_HEIGHT[_settings.avatarSizeOptionIndex],
      thumbnail_resolution:
        THUMBNAIL_HEIGHT[_settings.thumbnailResolutionOptionIndex],
    },
    headers,
    query: GET_CHANNEL_PLAYLISTS_XID,
    usePlatformAuth,
  });

  if (error) {
    log('Failed to get playlists by username:' + error.message);
    return [];
  }

  const playlists: string[] = (collections.data.channel as Maybe<Channel>)?.collections?.edges?.map(
    (edge) => {

      let playlistUrl = `${BASE_URL_PLAYLIST}/${edge?.node?.xid}`;

      const isPrivatePlaylist = edge?.node?.isPrivate ?? false;

      if(isPrivatePlaylist){
        playlistUrl += PRIVATE_PLAYLIST_QUERY_PARAM_FLAGGER;
      }

      return playlistUrl;
    },
  ) || [];

  return playlists;
}

function searchPlaylists(contextQuery) {
  const context = getQuery(contextQuery);

  const variables = {
    query: context.q,
    sortByVideos: context.sort,
    durationMaxVideos: context.filters?.durationMaxVideos,
    durationMinVideos: context.filters?.durationMinVideos,
    createdAfterVideos: context.filters?.createdAfterVideos, //Represents a DateTime value as specified by iso8601
    shouldIncludePlaylists: true,
    shouldIncludeVideos: false,
    shouldIncludeLives: false,
    page: context.page,
    limit: VIDEOS_PER_PAGE_OPTIONS[_settings.videosPerPageOptionIndex],
    thumbnail_resolution:
      THUMBNAIL_HEIGHT[_settings?.thumbnailResolutionOptionIndex],
    avatar_size: CREATOR_AVATAR_HEIGHT[_settings?.avatarSizeOptionIndex],
  };

  // Use dedicated search endpoint to avoid rate limiting
  const [error, gqlResponse] = executeSearchQuery(http, {
    operationName: 'SEARCH_QUERY',
    variables: variables,
    query: SEARCH_QUERY,
    headers: undefined,
  });

  if (error) {
    log('Failed to search playlists:' + error.message);
    return new PlaylistPager([]);
  }

  const playlistConnection = gqlResponse?.data?.search
    ?.playlists as CollectionConnection;

  const searchResults = playlistConnection?.edges?.map((edge) => {
    return SourceCollectionToGrayjayPlaylist(config.id, edge?.node);
  });

  const hasMore = playlistConnection?.pageInfo?.hasNextPage;

  if (!searchResults || searchResults.length === 0) {
    return new PlaylistPager([]);
  }

  const params = {
    query: context.q,
    sort: context.sort,
    filters: context.filters,
  };

  return new SearchPlaylistPager(
    searchResults,
    hasMore,
    params,
    context.page,
    searchPlaylists,
  );
}

//Internals

function getHomePager(params, page) {
  const count = VIDEOS_PER_PAGE_OPTIONS[_settings.videosPerPageOptionIndex];

  if (!params) {
    params = {};
  }

  params = { ...params, count };

  const headersToAdd = applyCommonHeaders({
    'X-DM-Preferred-Country': getPreferredCountry(_settings?.preferredCountryOptionIndex),
  });

  let obj;

  try {
    const [error, response] = executeGqlQuery(http, {
      operationName: 'SEACH_DISCOVERY_QUERY',
      variables: {
        avatar_size: CREATOR_AVATAR_HEIGHT[_settings?.avatarSizeOptionIndex ?? 0] ?? 'SQUARE_720',
        thumbnail_resolution:
          THUMBNAIL_HEIGHT[_settings?.thumbnailResolutionOptionIndex ?? 0] ?? 'THUMBNAIL_720',
      },
      query: SEACH_DISCOVERY_QUERY,
      headers: headersToAdd,
    });

    if (error) {
      log('Failed to get home page:' + error.message);
      return new VideoPager([], false, { params });
    }

    obj = response;
  } catch (error) {
    log('Exception in getHomePager:' + error);
    return new VideoPager([], false, { params });
  }

  const results =
    obj?.data?.home?.neon?.sections?.edges?.[0]?.node?.components?.edges
      ?.filter((edge) => edge?.node?.id)
      ?.map((edge) => {
        return SourceVideoToGrayjayVideo(config.id, edge.node as Video);
      });

  const hasMore =
    obj?.data?.home?.neon?.sections?.edges?.[0]?.node?.components?.pageInfo
      ?.hasNextPage ?? false;

  return new SearchPagerAll(results, hasMore, params, page, getHomePager);
}

function getShortsPager(params, page) {
  const count = VIDEOS_PER_PAGE_OPTIONS[_settings.videosPerPageOptionIndex] || 4;

  if (!params) {
    params = {};
  }

  params = { ...params, count };

  // Use the same headers as the provided curl request
  const headersToAdd = applyCommonHeaders({
    // 'accept': 'multipart/mixed; deferSpec=20220824, application/json',
    // 'accept-encoding': 'gzip',
    // 'accept-language': 'en',
    // 'x-apollo-operation-id': '196d45847508b833c87b04bd9b24bd231a046f5d820b8a3659be1d116dd9bc7f',
    // 'x-apollo-operation-name': 'GetHomeFeed',
    // 'x-dm-appinfo-id': 'com.dailymotion.dailymotion',
    // 'x-dm-appinfo-type': 'androidapp',
    // 'x-dm-appinfo-version': '3.15.22',
    'X-DM-Preferred-Country': getPreferredCountry(_settings?.preferredCountryOptionIndex),
  });

  let obj;

  try {
    const [error, response] = executeGqlQuery(http, {
      operationName: 'GetHomeFeed',
      variables: {
        thumbnailHeight: 'PORTRAIT_240',
        channelLogoSize: 'SQUARE_240',
        watchedVideoIds: [],
        first: count,
        personalizationOptOut: true,
      },
      query: GET_SHORTS_FEED_QUERY,
      headers: headersToAdd,
    });

    if (error) {
      log('Failed to get shorts feed:' + error.message);
      return new VideoPager([], false, { params });
    }

    obj = response;
  } catch (error) {
    log('Exception in getShortsPager:' + error);
    return new VideoPager([], false, { params });
  }

  const results =
    obj?.data?.conversations?.edges
      ?.filter((edge) => {
        const story = edge?.node?.story;
        if (!story?.xid) return false;

        // Filter to only include shorts (vertical videos with aspect ratio < 1)
        // Aspect ratio < 1 means height > width (portrait/vertical orientation)
        return story.aspectRatio && story.aspectRatio < 1;
      })
      ?.map((edge) => {
        return SourceVideoToGrayjayVideo(config.id, edge.node.story as Video);
      }) ?? [];

  // Note: The conversations API doesn't seem to provide hasNextPage in the same way
  // For now, we'll set hasMore to false. This may need adjustment based on API behavior
  const hasMore = false;

  return new SearchPagerAll(results, hasMore, params, page, getShortsPager);
}

function getChannelContentsPager(url, page, type, order, filters) {

  if(IS_TESTING && !type){
    type = Type.Feed.Mixed;
  }

  const channel_name = getChannelNameFromUrl(url);

  const shouldLoadVideos =
    type === Type.Feed.Mixed || type === Type.Feed.Videos;

  const shouldLoadLives =
    type === Type.Feed.Mixed ||
    type === Type.Feed.Streams ||
    type === Type.Feed.Live;

  if (IS_TESTING) {
    log(
      `Getting channel contents for ${url}, page: ${page}, type: ${type}, order: ${order}, shouldLoadVideos: ${shouldLoadVideos}, shouldLoadLives: ${shouldLoadLives}, filters: ${JSON.stringify(filters)}`,
    );
  }

  /** 
    Recent = Sort liked medias by most recent.
    Visited - Sort liked medias by most viewed
  */
  let sort: string;

  if (order == Type.Order.Chronological) {
    sort = LikedMediaSort.Recent;
  } else if (order == 'Popular') {
    sort = LikedMediaSort.Visited;
  } else {
    sort = LikedMediaSort.Recent;
  }

  const [error, gqlResponse] = executeGqlQuery(http, {
    operationName: 'CHANNEL_VIDEOS_QUERY',
    variables: {
      channel_name,
      sort,
      page: page ?? 1,
      allowExplicit: !_settings.hideSensitiveContent,
      first: VIDEOS_PER_PAGE_OPTIONS[_settings.videosPerPageOptionIndex],
      avatar_size: CREATOR_AVATAR_HEIGHT[_settings?.avatarSizeOptionIndex],
      thumbnail_resolution:
        THUMBNAIL_HEIGHT[_settings?.thumbnailResolutionOptionIndex],
      shouldLoadLives,
      shouldLoadVideos,
    },
    query: CHANNEL_VIDEOS_QUERY,
  });

  if (error) {
    log('Failed to get channel contents:' + error.message);
    return new ChannelVideoPager([], false, { url, type, order, page, filters }, getChannelContentsPager);
  }

  const channel = gqlResponse?.data?.channel as Channel;

  const all: (Live | Video)[] = [
    ...(channel?.lives?.edges
      ?.filter((e) => e?.node?.isOnAir)
      ?.map((e) => e?.node as Live) ?? []),
    ...(channel?.videos?.edges?.map((e) => e?.node as Video) ?? []),
  ];

  const videos = all.map((node) => SourceVideoToGrayjayVideo(config.id, node));

  const videosHasNext = channel?.videos?.pageInfo?.hasNextPage;
  const livesHasNext = channel?.lives?.pageInfo?.hasNextPage;
  const hasNext = videosHasNext || livesHasNext || false;

  const params = {
    url,
    type,
    order,
    page,
    filters,
  };

  return new ChannelVideoPager(
    videos,
    hasNext,
    params,
    getChannelContentsPager,
  );
}

function getSearchPagerAll(contextQuery): VideoPager {
  const context = getQuery(contextQuery);

  const variables = {
    query: context.q,
    sortByVideos: context.sort,
    durationMaxVideos: context.filters?.durationMaxVideos,
    durationMinVideos: context.filters?.durationMinVideos,
    createdAfterVideos: context.filters?.createdAfterVideos, //Represents a DateTime value as specified by iso8601
    shouldIncludePlaylists: false,
    shouldIncludeVideos: true,
    shouldIncludeLives: true,
    page: context.page ?? 1,
    limit: VIDEOS_PER_PAGE_OPTIONS[_settings.videosPerPageOptionIndex],
    avatar_size: CREATOR_AVATAR_HEIGHT[_settings?.avatarSizeOptionIndex],
    thumbnail_resolution:
      THUMBNAIL_HEIGHT[_settings?.thumbnailResolutionOptionIndex],
  };

  // Use dedicated search endpoint to avoid rate limiting
  const [error, gqlResponse] = executeSearchQuery(http, {
    operationName: 'SEARCH_QUERY',
    variables: variables,
    query: SEARCH_QUERY,
    headers: undefined,
  });

  if (error) {
    // Don't use partial data when rate-limited - it returns discovery content, not search results
    if (error.status?.includes('Maximum attempts')) {
      log('Search rate limited: ' + error.status);
      throw new ScriptException('Search temporarily unavailable - rate limited');
    }
    log('Failed to search: [' + error.code + '] ' + error.status);
    return new VideoPager([], false);
  }

  const videoConnection = gqlResponse?.data?.search?.videos as VideoConnection;
  const liveConnection = gqlResponse?.data?.search?.lives as LiveConnection;

  const all: (VideoEdge | LiveEdge | null)[] = [
    ...(videoConnection?.edges ?? []),
    ...(liveConnection?.edges ?? []),
  ];

  const results: PlatformVideo[] = all.map((edge) =>
    SourceVideoToGrayjayVideo(config.id, edge?.node),
  );

  const params = {
    query: context.q,
    sort: context.sort,
    filters: context.filters,
  };

  return new SearchPagerAll(
    results,
    videoConnection?.pageInfo?.hasNextPage,
    params,
    context.page,
    getSearchPagerAll,
  );
}

function getSavedVideo(url, usePlatformAuth = false) {
  const id = url.split('/').pop();

  const player_metadata_url = `${BASE_URL_METADATA}/${id}?embedder=https%3A%2F%2Fwww.dailymotion.com%2Fvideo%2Fx8yb2e8&geo=1&player-id=xjnde&locale=en-GB&dmV1st=ce2035cd-bdca-4d7b-baa4-127a17490ca5&dmTs=747022&is_native_app=0&app=com.dailymotion.neon&client_type=webapp&section_type=player&component_style=_`;

  const headers1 = applyCommonHeaders();

  if (_settings.hideSensitiveContent) {
    headers1['Cookie'] = 'ff=on';
  } else {
    headers1['Cookie'] = 'ff=off';
  }

  const videoDetailsRequestBody = JSON.stringify({
    operationName: 'WATCHING_VIDEO',
    variables: {
      xid: id,
      avatar_size: CREATOR_AVATAR_HEIGHT[_settings?.avatarSizeOptionIndex],
      thumbnail_resolution:
        THUMBNAIL_HEIGHT[_settings?.thumbnailResolutionOptionIndex],
    },
    query: WATCHING_VIDEO,
  });

  const videoDetailsRequestHeaders: Record<string, string> = applyCommonHeaders();

  if (!usePlatformAuth) {
    videoDetailsRequestHeaders.Authorization = state.anonymousUserAuthorizationToken;
  }

  const [player_metadataResponse, video_details_response] = http
    .batch()
    .GET(player_metadata_url, headers1, usePlatformAuth)
    .POST(
      state.apiEndpoint || BASE_URL_API,
      videoDetailsRequestBody,
      videoDetailsRequestHeaders,
      usePlatformAuth,
    )
    .execute();


  if (!player_metadataResponse.isOk) {
    throw new UnavailableException('Unable to get player metadata');
  }

  const player_metadata = JSON.parse(player_metadataResponse.body);

  if (player_metadata.error) {
    if (
      player_metadata.error.code &&
      ERROR_TYPES[player_metadata.error.code] !== undefined
    ) {
      throw new UnavailableException(ERROR_TYPES[player_metadata.error.code]);
    }

    throw new UnavailableException('This content is not available');
  }

  if (video_details_response.code != 200) {
    throw new UnavailableException('Failed to get video details');
  }

  const video_details = JSON.parse(video_details_response.body);

  const video = video_details?.data?.video as Video;

  const platformVideoDetails: PlatformVideoDetailsDef =
    SourceVideoToPlatformVideoDetailsDef(config.id, video, player_metadata);

  const videoDetails = new PlatformVideoDetails(platformVideoDetails);

  videoDetails.getContentRecommendations = function () {
    return source.getContentRecommendations(url, videoDetails);
  };

  return videoDetails;
}

function getSearchChannelPager(context) {
  // Use dedicated search endpoint to avoid rate limiting
  const [error, searchResponse] = executeSearchQuery(http, {
    operationName: 'SEARCH_QUERY',
    variables: {
      query: context.q,
      page: context.page ?? 1,
      limit: VIDEOS_PER_PAGE_OPTIONS[_settings.videosPerPageOptionIndex],
      avatar_size: CREATOR_AVATAR_HEIGHT[_settings?.avatarSizeOptionIndex],
    },
    query: SEARCH_CHANNEL,
  });

  if (error) {
    log('Failed to search channels:' + error.message);
    return new SearchChannelPager([], false, { query: context.q }, context.page, getSearchChannelPager);
  }

  const results = searchResponse?.data?.search?.channels?.edges.map((edge) => {
    const channel = edge.node as Channel;

    return SourceChannelToGrayjayChannel(config.id, channel);
  });

  const params = {
    query: context.q,
  };

  return new SearchChannelPager(
    results,
    searchResponse?.data?.search?.channels?.pageInfo?.hasNextPage,
    params,
    context.page,
    getSearchChannelPager,
  );
}

function getChannelPlaylists(
  url: string,
  page: number = 1,
): SearchPlaylistPager | PlaylistPager   {

  const headers = applyCommonHeaders();

  const usePlatformAuth = false;
  const channel_name = getChannelNameFromUrl(url);

  const [error, gqlResponse] = executeGqlQuery(http, {
    operationName: 'CHANNEL_PLAYLISTS_QUERY',
    variables: {
      channel_name,
      sort: 'recent',
      page,
      first: PLAYLISTS_PER_PAGE_OPTIONS[_settings.playlistsPerPageOptionIndex],
      avatar_size: CREATOR_AVATAR_HEIGHT[_settings.avatarSizeOptionIndex],
      thumbnail_resolution:
        THUMBNAIL_HEIGHT[_settings.thumbnailResolutionOptionIndex],
    },
    headers,
    query: CHANNEL_PLAYLISTS_QUERY,
    usePlatformAuth,
  });

  if (error) {
    log('Failed to get channel playlists:' + error.message);
    return new PlaylistPager([], false);
  }

  const channel = gqlResponse.data.channel as Channel;

  const content: PlatformPlaylist[] = (channel?.collections?.edges ?? [])
    .filter(
      (e) => e?.node?.metrics?.engagement?.videos?.edges?.[0]?.node?.total,
    ) //exclude empty playlists. could be empty doe to geographic restrictions
    .map((edge) => {
      return SourceCollectionToGrayjayPlaylist(config.id, edge?.node);
    });

  if (content?.length === 0) {
    return new ChannelPlaylistPager([]);
  }

  const params = {
    url,
  };

  const hasMore = channel?.collections?.pageInfo?.hasNextPage ?? false;

  return new ChannelPlaylistPager(
    content,
    hasMore,
    params,
    page,
    getChannelPlaylists,
  );
}

function isTokenValid() {
  const currentTime = Date.now();
  return state.anonymousUserAuthorizationTokenExpirationDate > currentTime;
}

function executeGqlQuery(httpClient, requestOptions) {
  const headersToAdd = requestOptions.headers || applyCommonHeaders({
    'X-DM-Preferred-Country': getPreferredCountry(_settings?.preferredCountryOptionIndex) ?? 'us',
  });

  const gql = JSON.stringify({
    operationName: requestOptions.operationName,
    variables: requestOptions.variables,
    query: requestOptions.query,
  });

  const usePlatformAuth =
    requestOptions.usePlatformAuth == undefined
      ? false
      : requestOptions.usePlatformAuth;


  if (!usePlatformAuth) {
    headersToAdd.Authorization = state.anonymousUserAuthorizationToken;
  }

  try {
    const res = httpClient.POST(state.apiEndpoint || BASE_URL_API, gql, headersToAdd, usePlatformAuth);

    if (!res.isOk) {
      const errorInfo = {
        code: res.code,
        status: `HTTP ${res.code}`,
        operationName: requestOptions.operationName,
        body: res.body ? (typeof res.body === 'string' ? res.body : JSON.stringify(res.body)) : 'No response body',
        variables: requestOptions.variables
      };
      
      console.error('Failed to execute request', errorInfo);
      
      return [errorInfo, null];
    }

    let body;
    try {
      body = JSON.parse(res.body);
    } catch (parseError) {
      const errorInfo = {
        code: 'PARSE_ERROR',
        status: 'Failed to parse response body',
        operationName: requestOptions.operationName,
        body: res.body ? res.body.substring(0, 500) : 'No response body', // Limit response size
        parseError: String(parseError),
        variables: requestOptions.variables
      };
      
      return [errorInfo, null];
    }

    // some errors may be returned in the body with a status code 200
    if (body.errors) {
      const message = body.errors.map((e) => e.message).join(', ');
      const errorInfo = {
        code: 'GQL_ERROR',
        status: message,
        operationName: requestOptions.operationName,
        errors: body.errors,
        variables: requestOptions.variables,
        data: body.data
      };
      
      return [errorInfo, body.data ? body : null]; // Return partial data if available
    }

    return [null, body];
  } catch (error) {
    const errorInfo = {
      code: 'EXCEPTION',
      status: error instanceof Error ? error.message : String(error),
      stack: error instanceof Error ? error.stack : undefined,
      operationName: requestOptions.operationName,
      variables: requestOptions.variables
    };
    
    return [errorInfo, null];
  }
}

function executeSearchQuery(httpClient, requestOptions) {
  // Use dedicated search endpoint with visitor tracking headers
  const headersToAdd = requestOptions.headers || applyCommonHeaders({
    'X-DM-Preferred-Country': getPreferredCountry(_settings?.preferredCountryOptionIndex) ?? 'us',
  });

  // Add visitor tracking headers that the browser uses
  headersToAdd['X-DM-Visit-Id'] = state.visitId || Date.now().toString();
  headersToAdd['X-DM-Visitor-Id'] = state.visitorId || generateUUIDv4();
  headersToAdd['X-DM-Neon-SSR'] = '0';

  const gql = JSON.stringify({
    operationName: requestOptions.operationName,
    variables: requestOptions.variables,
    query: requestOptions.query,
  });

  const usePlatformAuth =
    requestOptions.usePlatformAuth == undefined
      ? false
      : requestOptions.usePlatformAuth;

  if (!usePlatformAuth) {
    headersToAdd.Authorization = state.anonymousUserAuthorizationToken;
  }

  try {
    const res = httpClient.POST(BASE_URL_SEARCH_API, gql, headersToAdd, usePlatformAuth);

    if (!res.isOk) {
      const errorInfo = {
        code: res.code,
        status: `HTTP ${res.code}`,
        operationName: requestOptions.operationName,
        body: res.body ? (typeof res.body === 'string' ? res.body : JSON.stringify(res.body)) : 'No response body',
        variables: requestOptions.variables
      };

      console.error('Failed to execute search request', errorInfo);

      return [errorInfo, null];
    }

    let body;
    try {
      body = JSON.parse(res.body);
    } catch (parseError) {
      const errorInfo = {
        code: 'PARSE_ERROR',
        status: 'Failed to parse response body',
        operationName: requestOptions.operationName,
        body: res.body ? res.body.substring(0, 500) : 'No response body',
        parseError: String(parseError),
        variables: requestOptions.variables
      };

      return [errorInfo, null];
    }

    if (body.errors) {
      const message = body.errors.map((e) => e.message).join(', ');
      const isRateLimited = message.includes('Maximum attempts');
      const errorInfo = {
        code: 'GQL_ERROR',
        status: message,
        operationName: requestOptions.operationName,
        errors: body.errors,
        variables: requestOptions.variables,
        data: body.data
      };

      // Don't return partial data when rate limited - it's discovery content, not search results
      return [errorInfo, isRateLimited ? null : (body.data ? body : null)];
    }

    return [null, body];
  } catch (error) {
    const errorInfo = {
      code: 'EXCEPTION',
      status: error instanceof Error ? error.message : String(error),
      stack: error instanceof Error ? error.stack : undefined,
      operationName: requestOptions.operationName,
      variables: requestOptions.variables
    };

    return [errorInfo, null];
  }
}

function getPages<TI, TO>(
  httpClient: IHttp,
  query: string,
  operationName: string,
  variables: any,
  usePlatformAuth: boolean,
  setRoot: (gqlResponse: any) => TI,
  hasNextCallback: (page: TI) => boolean,
  getNextPage: (page: TI, currentPage) => number,
  map: (page: any) => TO[],
): TO[] {
  let all: TO[] = [];

  if (!hasNextCallback) {
    hasNextCallback = () => false;
  }

  let hasNext = true;
  let nextPage = 1;

  do {
    variables = { ...variables, page: nextPage };

    const [error, gqlResponse] = executeGqlQuery(httpClient, {
      operationName,
      variables,
      query,
      usePlatformAuth,
    });

    if (error) {
      log('Failed in getPages:' + error.message);
      return all; // Return what we have so far
    }

    const root = setRoot(gqlResponse);

    nextPage = getNextPage(root, nextPage);

    const items = map(root);

    hasNext = hasNextCallback(root);

    all = all.concat(items);
  } while (hasNext);

  return all;
}

function getLikePlaylist(
  pluginId: string,
  httpClient: IHttp,
  usePlatformAuth: boolean = false,
  thumbnailResolutionIndex: number = 0,
): PlatformPlaylistDetails {
  return getPlatformSystemPlaylist({
    pluginId,
    httpClient,
    query: USER_LIKED_VIDEOS_QUERY,
    operationName: 'USER_LIKED_VIDEOS_QUERY',
    rootObject: 'likedMedias',
    playlistName: 'Liked Videos',
    usePlatformAuth,
    thumbnailResolutionIndex,
  });
}

function getFavoritesPlaylist(
  pluginId: string,
  httpClient: IHttp,
  usePlatformAuth: boolean = false,
  thumbnailResolutionIndex: number = 0,
): PlatformPlaylistDetails {
  return getPlatformSystemPlaylist({
    pluginId,
    httpClient,
    query: USER_WATCH_LATER_VIDEOS_QUERY,
    operationName: 'USER_WATCH_LATER_VIDEOS_QUERY',
    rootObject: 'watchLaterMedias',
    playlistName: 'Favorites',
    usePlatformAuth,
    thumbnailResolutionIndex,
  });
}

function getRecentlyWatchedPlaylist(
  pluginId: string,
  httpClient: IHttp,
  usePlatformAuth: boolean = false,
  thumbnailResolutionIndex: number = 0,
): PlatformPlaylistDetails {
  return getPlatformSystemPlaylist({
    pluginId,
    httpClient,
    query: USER_WATCHED_VIDEOS_QUERY,
    operationName: 'USER_WATCHED_VIDEOS_QUERY',
    rootObject: 'watchedVideos',
    playlistName: 'Recently Watched',
    usePlatformAuth,
    thumbnailResolutionIndex,
  });
}

function getPlatformSystemPlaylist(
  opts: IPlatformSystemPlaylist,
): PlatformPlaylistDetails {
  const videos: PlatformVideo[] = getPages<Maybe<User>, PlatformVideo>(
    opts.httpClient,
    opts.query,
    opts.operationName,
    {
      page: 1,
      thumbnail_resolution: THUMBNAIL_HEIGHT[opts.thumbnailResolutionIndex],
    },
    opts.usePlatformAuth,
    (gqlResponse) => gqlResponse?.data?.me, //set root
    (me) => (me?.[opts.rootObject]?.edges?.length ?? 0) > 0, //hasNextCallback
    (me, currentPage) => ++currentPage, //getNextPage
    (me) =>
      me?.[opts.rootObject]?.edges.map((edge) => {
        return SourceVideoToGrayjayVideo(opts.pluginId, edge.node as Video);
      }),
  );

  const collection = {
    id: generateUUIDv4(),
    name: opts.playlistName,
    creator: {},
  };

  return SourceCollectionToGrayjayPlaylistDetails(
    opts.pluginId,
    collection as Collection,
    videos,
  );
}

function getPreferredCountry(preferredCountryIndex) {
  const country = COUNTRY_NAMES_TO_CODE[preferredCountryIndex];
  if (!country || typeof country !== 'string') {
    return '';
  }
  const parts = country.split('-');
  const code = parts[0] ?? '';
  return (code || '').toLowerCase();
}

log('LOADED');
