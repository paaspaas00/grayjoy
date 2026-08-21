'use strict';

const BASE_URL = 'https://www.dailymotion.com';
// Fallbacks for the API_ENDPOINT / AUTH_ENDPOINT values in window.__RUNTIME_CONFIG__.
// The live values are extracted from the homepage at runtime; these two hosts differ.
const BASE_URL_API = 'https://api.dailymotion.com/v1/graphql';
const BASE_URL_API_AUTH = 'https://graphql.api.dailymotion.com/oauth/token';
// Search uses a dedicated endpoint (observed from browser behavior)
// This helps avoid rate limiting on the main GraphQL API
const BASE_URL_SEARCH_API = 'https://search.dailymotion.com';
const BASE_URL_COMMENTS = 'https://api-2-0.spot.im/v1.0.0/conversation/read';
const BASE_URL_COMMENTS_AUTH = 'https://api-2-0.spot.im/v1.0.0/authenticate';
const BASE_URL_COMMENTS_THUMBNAILS = 'https://images.spot.im/image/upload';
const BASE_URL_VIDEO = `${BASE_URL}/video`;
const BASE_URL_PLAYLIST = `${BASE_URL}/playlist`;
const BASE_URL_METADATA = `${BASE_URL}/player/metadata/video`;
const REGEX_VIDEO_URL = /^https:\/\/(?:www\.)?dailymotion\.com\/video\/[a-zA-Z0-9]+$/i;
const REGEX_VIDEO_URL_1 = /^https:\/\/dai\.ly\/[a-zA-Z0-9]+$/i;
const REGEX_VIDEO_URL_EMBED = /^https:\/\/(?:www\.)?dailymotion\.com\/embed\/video\/[a-zA-Z0-9]+(\?.*)?$/i;
const REGEX_VIDEO_CHANNEL_URL = /^https:\/\/(?:www\.)?dailymotion\.com\/[a-z0-9][a-z0-9._-]{2,26}(?:\?[a-zA-Z0-9=&._-]*)?$/i;
const REGEX_VIDEO_PLAYLIST_URL = /^https:\/\/(?:www\.)?dailymotion\.com\/playlist\/[a-zA-Z0-9]+(?:[?&][a-zA-Z0-9_\-=&%]*)?$/i;
const REGEX_INITIAL_DATA_API_AUTH_1 = /(?<=window\.__LOADABLE_LOADED_CHUNKS__=.*)\b[a-f0-9]{20}\b|\b[a-f0-9]{40}\b/g;
const REGEX_API_CLIENT_ID = /get apiClientId\(\)\{return"([a-f0-9]{20})"\}/;
const REGEX_API_CLIENT_SECRET = /get apiClientSecret\(\)\{return"([a-f0-9]{40})"\}/;
// Pattern to find the app.js URL in the homepage HTML
const REGEX_APP_JS_URL = /static\/app\.[a-f0-9]+\.js/;
// Patterns for window.__RUNTIME_CONFIG__ on the homepage. The leading boundary stops
// API_ENDPOINT from also matching SEARCH_API_ENDPOINT or REPORT_API_ENDPOINT.
const REGEX_API_ENDPOINT = /(?:^|[^A-Z_])API_ENDPOINT:\s*'(https:\/\/[^']+)'/;
const REGEX_AUTH_ENDPOINT = /(?:^|[^A-Z_])AUTH_ENDPOINT:\s*'(https:\/\/[^']+)'/;
const createAuthRegexByTextLength = (length) => new RegExp(`\\b\\w+\\s*=\\s*"([a-zA-Z0-9]{${length}})"`);
const USER_AGENT = 'Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36';
const FALLBACK_SPOT_ID = 'sp_vWPN1lBu';
const PLATFORM = 'Dailymotion';
const PLATFORM_CLAIMTYPE = 27;
// search capabilities - upload date
const LESS_THAN_MINUTE = 'LESS_THAN_MINUTE';
const ONE_TO_FIVE_MINUTES = 'ONE_TO_FIVE_MINUTES';
const FIVE_TO_THIRTY_MINUTES = 'FIVE_TO_THIRTY_MINUTES';
const THIRTY_TO_ONE_HOUR = 'THIRTY_TO_ONE_HOUR';
const MORE_THAN_ONE_HOUR = 'MORE_THAN_ONE_HOUR';
const DURATION_THRESHOLDS = {};
DURATION_THRESHOLDS[LESS_THAN_MINUTE] = { min: 0, max: 60 };
DURATION_THRESHOLDS[ONE_TO_FIVE_MINUTES] = { min: 60, max: 300 };
DURATION_THRESHOLDS[FIVE_TO_THIRTY_MINUTES] = { min: 300, max: 1800 };
DURATION_THRESHOLDS[THIRTY_TO_ONE_HOUR] = { min: 1800, max: 3600 };
DURATION_THRESHOLDS[MORE_THAN_ONE_HOUR] = { min: 3600, max: null };
const LIKED_VIDEOS_PLAYLIST_ID = 'LIKE_PLAYLIST';
const FAVORITE_VIDEOS_PLAYLIST_ID = 'FAVORITES_PLAYLIST';
const RECENTLY_WATCHED_VIDEOS_PLAYLIST_ID = 'RECENTLY_WATCHED_PLAYLIST';
/** The possible values which liked media connections can be sorted by. */
const LikedMediaSort = {
    /** Sort liked medias by most recent. */
    Recent: 'recent',
    /** Sort liked medias by most viewed. */
    Visited: 'visited',
};
// This platform uses a scale system for rating the videos.
// Ratings are grouped into positive and negative to calculate likes and dislikes.
const POSITIVE_RATINGS_LABELS = [
    'STAR_STRUCK', // amazing
    'SMILING_FACE_WITH_SUNGLASSES', // cool
    'WINKING_FACE', // interesting
];
const NEGATIVE_RATINGS_LABELS = [
    'SLEEPING_FACE', // boring
    'FISHING_POLE', // waste of time
];
const ERROR_TYPES = {
    DM001: 'No video has been specified, you need to specify one.',
    DM002: 'Content has been deleted.',
    DM003: 'Live content is not available, i.e. it may not have started yet.',
    DM004: 'Copyrighted content, access forbidden.',
    DM005: 'Content rejected (this video may have been removed due to a breach of the terms of use, a copyright claim or an infringement upon third party rights).',
    DM006: 'Publishing in progress…',
    DM007: 'Video geo-restricted by its owner.',
    DM008: 'Explicit content. Explicit content can be enabled using the plugin settings',
    DM009: 'Explicit content (offsite embed)',
    DM010: 'Private content',
    DM011: 'An encoding error occurred',
    DM012: 'Encoding in progress',
    DM013: 'This video has no preset (no video stream)',
    DM014: 'This video has not been made available on your device by its owner',
    DM015: 'Kids host error',
    DM016: 'Content not available on this website, it can only be watched on Dailymotion',
    DM019: 'This content has been uploaded by an inactive channel and its access is limited',
};
const SEARCH_CAPABILITIES = {
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
const PRIVATE_PLAYLIST_QUERY_PARAM_FLAGGER = '&private=1';
const DEFAULT_HEADERS = {
    'User-Agent': USER_AGENT,
    Origin: BASE_URL,
    'X-DM-AppInfo-Id': 'com.dailymotion.neon',
    'Referer': 'https://www.dailymotion.com/'
};
const IS_DESKTOP = bridge.buildPlatform === "desktop";
const IMPERSONATION_TARGET = IS_DESKTOP ? 'chrome136' : 'chrome131_android';
const IS_IMPERSONATION_AVAILABLE = (typeof httpimp !== 'undefined');

const AUTOCOMPLETE_QUERY = `
query AUTOCOMPLETE_QUERY($query: String!) {
  search {
    suggestedVideos: autosuggestions(
      query: { eq: $query }
      filter: { story: { eq: VIDEO } }
    ) {
      edges {
        node {
          name
        }
      }
    }
  }
}`;
const CHANNEL_QUERY_DESKTOP = `
query CHANNEL_QUERY_DESKTOP(
	$channel_name: String!
	$avatar_size: AvatarHeight!
) {
	channel(name: $channel_name) {
		id
		xid
		name
		displayName
		description
		avatar(height:$avatar_size) {
			url
		}
		banner(width:LANDSCAPE_1920) {
			url
		}
		tagline
		metrics {
			engagement {
				followers {
					edges {
						node {
							total
						}
					}
				}
				followings {
					edges {
						node {
							total
						}
					}
				}
			}
		}
		stats {
			views {
				total
			}
			videos {
				total
			}
		}
		externalLinks {
			facebookURL
			twitterURL
			websiteURL
			instagramURL
			pinterestURL
		}
	}
}`;
const SEACH_DISCOVERY_QUERY = `
fragment SEARCH_DISCOVERY_VIDEO_FRAGMENT on Video {
	id
	xid
	title
	thumbnail(height:$thumbnail_resolution) {
		url
	}
	createDate
	createdAt
	creator {
		id
		xid
		name
		displayName
		avatar(height:$avatar_size) {
			url
		}
	}
	duration
	viewCount
	stats {
		views {
			total
		}
	}
}

query SEACH_DISCOVERY_QUERY($avatar_size: AvatarHeight!, $thumbnail_resolution: ThumbnailHeight!) {
	home: views {
		neon {
			sections(space: "home") {
				edges {
					node {
						id
						name
						title
						description
						components {
							pageInfo {
								hasNextPage
							}
							edges {
								node {
									... on Media {
										...SEARCH_DISCOVERY_VIDEO_FRAGMENT
									}
								}
							}
						}
					}
				}
			}
		}
	}
}`;
const CHANNEL_VIDEOS_QUERY = `
query CHANNEL_VIDEOS_QUERY(
  $channel_name: String!
  $first: Int!
  $sort: String
  $page: Int!
  $allowExplicit: Boolean
  $avatar_size: AvatarHeight!
  $thumbnail_resolution: ThumbnailHeight!
  $shouldLoadLives: Boolean!
  $shouldLoadVideos: Boolean!
) {
  channel(name: $channel_name) {
    id
    xid
    lives(
      page: $page
      first: $first
      allowExplicit: $allowExplicit
    ) @include(if: $shouldLoadLives) {
      pageInfo {
        hasNextPage
        nextPage
      }
      totalCount
      edges {
        node {
          id
          xid
          title
          thumbnail(height: $thumbnail_resolution) {
            url
          }
          description
          metrics {
            engagement {
              audience {
                totalCount
              }
            }
          }
          audienceCount
          isOnAir
          stats {
            views {
              total
            }
          }
          creator {
            id
            xid
            name
            displayName
            avatar(height: $avatar_size) {
              url
            }
          }
        }
      }
    }
    videos(
      page: $page
      first: $first
      allowExplicit: $allowExplicit
      sort: $sort
    ) @include(if: $shouldLoadVideos) {
      pageInfo {
        hasNextPage
        nextPage
      }
      edges {
        node {
          id
          xid
          title
          thumbnail(height: $thumbnail_resolution) {
            url
          }
          duration
          createDate
          createdAt
          creator {
            id
            name
            displayName
            avatar(height: $avatar_size) {
              url
            }
          }
          metrics {
            engagement {
              likes {
                totalCount
              }
            }
          }
          viewCount
          stats {
            views {
              total
            }
          }
        }
      }
    }
  }
}`;
const SEARCH_QUERY = `
fragment VIDEO_BASE_FRAGMENT on Video {
	id
	xid
	title
	createDate
	createdAt
	metrics {
		engagement {
			likes {
				edges {
					node {
						rating
						total
					}
				}
			}
		}
	}
	stats {
		views {
			total
		}
	}
	creator {
		id
		xid
		name
		displayName
		description
		avatar(height:$avatar_size) {
			url
		}
	}
	duration
	thumbnail(height:$thumbnail_resolution) {
		url
	}
}

fragment PLAYLIST_BASE_FRAG on Collection {
	id
	xid
	name
	description
	thumbnail(height:$thumbnail_resolution) {
		url
	}
	creator {
		id
		xid
		name
		displayName
		avatar(height:$avatar_size) {
			url
		}
	}
	description
	stats {
		videos {
			total
		}
	}
	metrics {
		engagement {
			videos {
				edges {
					node {
						total
					}
				}
			}
		}
	}
}

query SEARCH_QUERY(
	$query: String!
	$shouldIncludeVideos: Boolean!
	$shouldIncludePlaylists: Boolean!
	$shouldIncludeLives: Boolean!
	$page: Int
	$limit: Int
	$sortByVideos: SearchVideoSort
	$durationMinVideos: Int
	$durationMaxVideos: Int
	$createdAfterVideos: DateTime
	$avatar_size: AvatarHeight!
	$thumbnail_resolution: ThumbnailHeight!
) {
	search {
		videos(
			query: $query
			first: $limit
			page: $page
			sort: $sortByVideos
			durationMin: $durationMinVideos
			durationMax: $durationMaxVideos
			createdAfter: $createdAfterVideos
		) @include(if: $shouldIncludeVideos) {
			pageInfo {
				hasNextPage
				nextPage
			}
			totalCount
			edges {
				node {
					id
					...VIDEO_BASE_FRAGMENT
				}
			}
		}
		lives(query: $query, first: $limit, page: $page)
			@include(if: $shouldIncludeLives) {
			pageInfo {
				hasNextPage
				nextPage
			}
			totalCount
			edges {
				node {
					id
					xid
					title
					thumbnail(height:$thumbnail_resolution) {
						url
					}
					description
					metrics {
						engagement {
							audience {
								totalCount
							}
						}
					}
					audienceCount
					isOnAir
					creator {
						id
						xid
						name
						displayName
						avatar(height:$avatar_size){
							url
						}
					}
				}
			}
		}
		playlists: collections(query: $query, first: $limit, page: $page)
			@include(if: $shouldIncludePlaylists) {
			pageInfo {
				hasNextPage
				nextPage
			}
			totalCount
			edges {
				node {
					id
					...PLAYLIST_BASE_FRAG
				}
			}
		}
	}
}`;
const WATCHING_VIDEO = `
fragment VIDEO_FRAGMENT on Video {
	id
	xid
	duration
	title
	description
	thumbnail(height:$thumbnail_resolution) {
		url
	}
	createDate
	createdAt
	metrics {
		engagement {
			likes {
				totalCount
				edges {
					node {
						rating
						total
					}
				}
			}
		}
	}
	stats {
		views {
			total
		}
	}
	creator {
		id
		xid
		name
		displayName
		avatar(height:$avatar_size) {
			url
			height
			width
		}
		metrics {
			engagement {
				followers {
					totalCount
					edges {
						node {
							total
						}
					}
				}
			}
		}
		stats {
			views {
				total
			}
			followers {
				total
			}
			videos {
				total
			}
		}
	}
}

fragment LIVE_FRAGMENT on Live {
	id
	xid
	startAt
	endAt
	title
	description
	audienceCount
	isOnAir
	thumbnail(height:$thumbnail_resolution){
		url
	}
	createDate
	createdAt
	videoWidth: width
	videoHeight: height
	metrics {
		engagement {
			likes {
				edges {
					node {
						rating
						total
					}
				}
			}
		}
	}
	stats {
		views {
			total
		}
	}
	creator {
		id
		xid
		name
		displayName
		avatar(height:$avatar_size) {
			url
			height
			width
		}
		stats {
			views {
				total
			}
			followers {
				total
			}
			videos {
				total
			}
		}
	}
}

query WATCHING_VIDEO(
	$xid: String!
	$avatar_size: AvatarHeight!
	$thumbnail_resolution: ThumbnailHeight!
) {
	video: media(xid: $xid) {
		... on Video {
			id
			...VIDEO_FRAGMENT
		}
		... on Live {
			id
			...LIVE_FRAGMENT
		}
	}
}`;
const SEARCH_CHANNEL = `
query SEARCH_QUERY($query: String!, $page: Int, $limit: Int, $avatar_size: AvatarHeight!) {
	search {
		channels(query: $query, first: $limit, page: $page) {
			pageInfo {
				hasNextPage
				nextPage
			}
			totalCount
			edges {
				node {
					id
					xid
					name
					displayName
					description
					avatar(height:$avatar_size) {
						url
					}
					metrics {
						engagement {
							followers {
								edges {
									node {
										total
									}
								}
							}
						}
					}
				}
			}
		}
	}
}`;
const PLAYLIST_DETAILS_QUERY = `
query PLAYLIST_VIDEO_QUERY($xid: String!, $numberOfVideos: Int = 100, $avatar_size: AvatarHeight!, $thumbnail_resolution: ThumbnailHeight!) {
	collection(xid: $xid) {
		id
		xid
		name
		thumbnail(height:$thumbnail_resolution) {
			url
		}
		creator {
			id
			name
			displayName
			xid
			avatar(height:$avatar_size) {
				url
			}
			metrics {
				engagement {
					followers {
						edges {
							node {
								total
							}
						}
					}
				}
			}
		}
		metrics {
			engagement {
				videos {
					edges {
						node {
							total
						}
					}
				}
			}
		}
		videos(first: $numberOfVideos) {
			edges {
				node {
					id
					xid
					duration
					title
					description
					url
					createDate
					createdAt
					thumbnail(height:$thumbnail_resolution) {
						url
					}
					creator {
						id
						name
						displayName
						xid
						avatar(height:$avatar_size) {
							url
						}
						metrics {
							engagement {
								followers {
									edges {
										node {
											total
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}`;
const GET_USER_SUBSCRIPTIONS = `
query SUBSCRIPTIONS_QUERY($first: Int, $page: Int) {
	me {
		channel {
			followings(first: $first, page: $page) {
				totalCount
				edges {
					node {
						creator {
							name
						}
					}
				}
			}
		}
	}
}`;
const GET_CHANNEL_PLAYLISTS_XID = `
query CHANNEL_PLAYLISTS_QUERY(
	$channel_name: String!
	$sort: String
	$page: Int!
	$first: Int!
) {
	channel(name: $channel_name) {
		collections(
			sort: $sort
			page: $page
			first: $first
		) {
			pageInfo {
				hasNextPage
				nextPage
			}
			edges {
				node {
					xid
					isPrivate
						}
					}
				}
			}
}`;
const SUBSCRIPTIONS_QUERY = `
query SUBSCRIPTIONS_QUERY {
	me {
		xid
		channel {
			name
		}
	}
}
`;
const CHANNEL_PLAYLISTS_QUERY = `
query CHANNEL_PLAYLISTS_QUERY(
	$channel_name: String!
	$sort: String
	$page: Int!
	$first: Int!
	$avatar_size: AvatarHeight!,
	$thumbnail_resolution: ThumbnailHeight!
) {
	channel(name: $channel_name) {
		id
		xid
		collections(sort: $sort, page: $page, first: $first) {
			pageInfo {
				hasNextPage
				nextPage
			}
			edges {
				node {
					id
					xid
					createDate
					createdAt
					name
					description
					metrics {
						engagement {
							videos {
								edges {
									node {
										total
									}
								}
								totalCount
							}
						}
					}
					thumbnail(height:$thumbnail_resolution) {
						url
					}
					stats {
						videos {
							total
						}
					}
					videos {
						edges {
							node {
								createDate
								createdAt
								creator {
									id
									name
									xid
									avatar(height:$avatar_size) {
										url
									}
									displayName
								}
							}
						}
					}
				}
			}
		}
	}
}

`;
const USER_LIKED_VIDEOS_QUERY = `
query USER_LIKED_VIDEOS_QUERY($page: Int!, $thumbnail_resolution: ThumbnailHeight!) {
	me {
		likedMedias(first: 100, page: $page) {
			edges {
				node {
					... on Video {
						id
						xid
						title
						duration
						thumbnail(height:$thumbnail_resolution) {
							url
						}
						channel {
							displayName
						}
					}
					... on Live {

						id
						xid
						title
						isOnAir
						thumbnail(height:$thumbnail_resolution) {
							url
						}
						channel {
							displayName
						}
					}
				}
			}
			pageInfo {
				hasNextPage
				nextPage
			}
		}
	}
}`;
const USER_WATCH_LATER_VIDEOS_QUERY = `
	query USER_WATCH_LATER_VIDEOS_QUERY($page: Int!, $thumbnail_resolution: ThumbnailHeight!) {
	me {
		id
		watchLaterMedias(first: 100, page: $page) {
			edges {
				node {
					... on Video {
						id
						xid
						title
						duration
						thumbnail(height:$thumbnail_resolution) {
							url
						}
						channel {
							displayName
						}
					}
					... on Live {
						id
						xid
						title
						isOnAir
						thumbnail(height:$thumbnail_resolution) {
							url
						}
						channel {
							displayName
						}
					}
				}
			}
			pageInfo {
				hasNextPage
				nextPage
			}
		}
	}
}`;
const USER_WATCHED_VIDEOS_QUERY = `
	query USER_WATCHED_VIDEOS_QUERY($page: Int!, $thumbnail_resolution: ThumbnailHeight!) {
	me {
		id
		watchedVideos(first: 100, page: $page) {
			edges {
				node {
					id
					xid
					title
					duration
					thumbnail(height:$thumbnail_resolution) {
						url
					}
					channel {
						displayName
					}
				}
			}
			pageInfo {
				hasNextPage
				nextPage
			}
		}
	}
}`;
const DISCOVERY_QUEUE_QUERY = `
query DISCOVERY_QUEUE_QUERY($videoXid: String!, $videoCountPerSection: Int) {
  views {
    neon {
      sections(
        space: "watching"
        context: {mediaXid: $videoXid}
        first: 20
      ) {
        edges {
          node {
            name
            components(first: $videoCountPerSection) {
              edges {
                node {
                  ... on Video {
                    xid
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

`;
const playerVideosDataQuery = `
query playerVideosDataQuery(
	$videoXids: [String!],
	$first: Int,
	$avatar_size: AvatarHeight!,
	$thumbnail_resolution: ThumbnailHeight!
) {
  videos(videoXids: $videoXids, first: $first) {
    edges {
      node {
        ...VideoFields
      }
    }
  }
}
fragment VideoFields on Video {
	id
	xid
	title
	createDate
	createdAt
	metrics {
		engagement {
			likes {
				edges {
					node {
						rating
						total
					}
				}
			}
		}
	}
	stats {
		views {
			total
		}
	}
	creator {
		id
		xid
		name
		displayName
		description
		avatar(height:$avatar_size) {
			url
		}
	}
	duration
	thumbnail(height:$thumbnail_resolution) {
		url
	}
}

`;
const GET_SHORTS_FEED_QUERY = `
fragment FeedVideoFields on Video {
	xid
	title
	thumbnail(height: $thumbnailHeight) {
		url
	}
	duration
	shareUrls {
		permalink
	}
	createDate
	aspectRatio
	claimer {
		xid
		displayName
	}
	creator {
		__typename
		xid
		displayName
		name
		avatar(height: $channelLogoSize) {
			url
		}
		account
		followerEngagement {
			notifications {
				uploads
			}
		}
		followerEngagement {
			followDate
		}
	}
	paidPartnership
	metrics {
		views {
			visits {
				totalCount
			}
		}
		engagement {
			hearts {
				edges {
					node {
						emoji
						total
					}
				}
			}
			threads {
				edges {
					node {
						total
					}
				}
			}
		}
	}
	audience
	visibility
	status
	streamUrls {
		hls
	}
	hasPerspective
	id
	hashtags {
		edges {
			node {
				id
				name
			}
		}
	}
	subtitles(autoGenerated: true) {
		edges {
			node {
				id
			}
		}
	}
	audience
	settings {
		threadsDisabled
	}
	restriction {
		code
	}
	spritesheet {
		url
	}
	spritesheetSeeker {
		url
	}
	viewerEngagement {
		hearts {
			emoji
			amount
		}
		favorited
		reacted
	}
	visibility
	audience
	height
	width
}

query GetHomeFeed(
	$thumbnailHeight: ThumbnailHeight!
	$channelLogoSize: AvatarHeight!
	$watchedVideoIds: [ID!]
	$first: Int!
	$personalizationOptOut: Boolean!
) {
	conversations(
		first: $first
		context: {
			personalizationOptOut: $personalizationOptOut
			viewer: { history: { watched: { in: $watchedVideoIds } } }
		}
		filter: { story: { in: [VIDEO] }, algorithm: { eq: PERSONALIZED } }
	) {
		metadata {
			algorithm {
				uuid
			}
		}
		edges {
			node {
				algorithm {
					version
					name
					source
					percentage
				}
				story {
					__typename
					...FeedVideoFields
				}
			}
		}
	}
}
`;

const objectToUrlEncodedString = (obj) => {
    const encodedParams = [];
    for (const key in obj) {
        if (obj.hasOwnProperty(key)) {
            const encodedKey = encodeURIComponent(key);
            const encodedValue = encodeURIComponent(obj[key]);
            encodedParams.push(`${encodedKey}=${encodedValue}`);
        }
    }
    return encodedParams.join('&');
};
function getChannelNameFromUrl(url) {
    const match = url.match(/dailymotion\.com\/([^?&#]+)/);
    const channel_slug = match ? match[1] : null;
    return channel_slug;
}
const parseUploadDateFilter = (filter) => {
    let createdAfterVideos = null;
    const now = new Date();
    switch (filter) {
        case 'today': {
            // Last 24 hours from now
            const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
            createdAfterVideos = yesterday.toISOString();
            break;
        }
        case 'thisweek': {
            // Adjusts to the start of the current week (assuming week starts on Sunday)
            const startOfWeek = new Date(now.getTime());
            startOfWeek.setDate(startOfWeek.getDate() - startOfWeek.getDay());
            createdAfterVideos = new Date(startOfWeek.getFullYear(), startOfWeek.getMonth(), startOfWeek.getDate()).toISOString();
            break;
        }
        case 'thismonth': {
            // Adjusts to the start of the month
            createdAfterVideos = new Date(now.getFullYear(), now.getMonth(), 1).toISOString();
            break;
        }
        case 'thisyear': {
            // Adjusts to the start of the year
            createdAfterVideos = new Date(now.getFullYear(), 0, 1).toISOString();
            break;
        }
    }
    return createdAfterVideos;
};
const parseSort = (order) => {
    let sort;
    switch (order) {
        //TODO: refact this to use constants
        case 'Most Recent':
            sort = 'RECENT';
            break;
        case 'Most Viewed':
            sort = 'VIEW_COUNT';
            break;
        case 'Most Relevant':
            sort = 'RELEVANCE';
            break;
        default:
            sort = order; // Default to the original order if no match
    }
    return sort;
};
const getQuery = (context) => {
    context.sort = parseSort(context.order);
    if (!context.filters) {
        context.filters = {};
    }
    if (!context.page) {
        context.page = 1;
    }
    if (context?.filters.duration) {
        context.filters.durationMinVideos =
            DURATION_THRESHOLDS[context.filters.duration].min;
        context.filters.durationMaxVideos =
            DURATION_THRESHOLDS[context.filters.duration].max;
    }
    else {
        context.filters.durationMinVideos = null;
        context.filters.durationMaxVideos = null;
    }
    if (context.filters.uploaddate) {
        context.filters.createdAfterVideos = parseUploadDateFilter(context.filters.uploaddate[0]);
    }
    return context;
};
function generateUUIDv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
    });
}
function applyCommonHeaders(headers = {}) {
    return { ...DEFAULT_HEADERS, ...headers };
}
function notifyMaintenanceMode() {
    bridge.toast('Dailymotion is currently offline for maintenance. Thanks for your patience.');
}

class SearchPagerAll extends VideoPager {
    cb;
    constructor(results, hasMore, params, page, cb) {
        super(results, hasMore, { params, page });
        this.cb = cb;
    }
    nextPage() {
        this.context.page += 1;
        const opts = {
            q: this.context.params.query,
            sort: this.context.params.sort,
            page: this.context.page,
            filters: this.context.params.filters,
        };
        return this.cb(opts);
    }
}
class SearchChannelPager extends ChannelPager {
    cb;
    constructor(results, hasNextPage, params, page, cb) {
        super(results, hasNextPage, { params, page });
        this.cb = cb;
    }
    nextPage() {
        const page = (this.context.page += 1);
        const opts = {
            q: this.context.params.query,
            page,
        };
        return this.cb(opts);
    }
}
class ChannelVideoPager extends VideoPager {
    cb;
    constructor(results, hasNextPage, params, cb) {
        super(results, hasNextPage, { ...params });
        this.cb = cb;
    }
    nextPage() {
        this.context.page += 1;
        return this.cb(this.context.url, this.context.page, this.context.type, this.context.order);
    }
}
class ChannelPlaylistPager extends PlaylistPager {
    cb;
    constructor(results, hasMore, params, page, cb) {
        super(results, hasMore, { params, page });
        this.cb = cb;
    }
    nextPage() {
        this.context.page += 1;
        return this.cb(this.context.params.url, this.context.page);
    }
}
class SearchPlaylistPager extends PlaylistPager {
    cb;
    constructor(results, hasMore, params, page, cb) {
        super(results, hasMore, { params, page });
        this.cb = cb;
    }
    nextPage() {
        this.context.page = this.context.page + 1;
        const opts = {
            q: this.context.params.query,
            sort: this.context.params.sort,
            page: this.context.page,
            filters: this.context.params.filters,
        };
        return this.cb(opts);
    }
}

// TODO: createDate requires authentication, so we fallback to deprecated createdAt for unauthenticated requests
const toUnixTimestamp = (dateStr) => {
    if (!dateStr)
        return 0;
    const timestamp = new Date(dateStr).getTime();
    return isNaN(timestamp) ? 0 : Math.floor(timestamp / 1000);
};
const SourceChannelToGrayjayChannel = (pluginId, sourceChannel, url) => {
    const externalLinks = sourceChannel?.externalLinks ?? {};
    const links = Object.keys(externalLinks).reduce((acc, key) => {
        if (externalLinks[key]) {
            acc[key.replace('URL', '')] = externalLinks[key];
        }
        return acc;
    }, {});
    let description = '';
    if (sourceChannel?.tagline &&
        sourceChannel?.tagline != sourceChannel?.description) {
        description = `${sourceChannel?.tagline}\n\n`;
    }
    description += `${sourceChannel?.description ?? ''}`;
    return new PlatformChannel({
        id: new PlatformID(PLATFORM, sourceChannel?.id ?? '', pluginId, PLATFORM_CLAIMTYPE),
        name: sourceChannel?.displayName ?? '',
        thumbnail: sourceChannel?.avatar?.url ?? '',
        banner: sourceChannel.banner?.url ?? '',
        subscribers: sourceChannel?.metrics?.engagement?.followers?.edges?.[0]?.node?.total ??
            0,
        description,
        url: url ?? `${BASE_URL}/${sourceChannel.name}`,
        urlAlternatives: [
            `${BASE_URL}/${sourceChannel.name}`
        ],
        links,
    });
};
const SourceAuthorToGrayjayPlatformAuthorLink = (pluginId, creator) => {
    return new PlatformAuthorLink(new PlatformID(PLATFORM, creator?.id ?? '', pluginId, PLATFORM_CLAIMTYPE), creator?.displayName ?? '', creator?.name ? `${BASE_URL}/${creator?.name}` : '', creator?.avatar?.url ?? '', creator?.followers?.totalCount ??
        creator?.metrics?.engagement?.followers?.edges?.[0]?.node?.total ??
        0);
};
const SourceVideoToGrayjayVideo = (pluginId, sourceVideo) => {
    const isLive = getIsLive(sourceVideo);
    const viewCount = getViewCount(sourceVideo);
    // Determine if this is a short based on aspect ratio
    // Aspect ratio < 1 means height > width (portrait/vertical orientation)
    const aspectRatio = sourceVideo?.aspectRatio;
    const isShort = aspectRatio != null && aspectRatio < 1;
    const video = {
        id: new PlatformID(PLATFORM, sourceVideo?.id ?? '', pluginId, PLATFORM_CLAIMTYPE),
        description: sourceVideo?.description ?? '',
        name: sourceVideo?.title ?? '',
        thumbnails: new Thumbnails([
            new Thumbnail(sourceVideo?.thumbnail?.url ?? '', 0),
        ]),
        author: SourceAuthorToGrayjayPlatformAuthorLink(pluginId, sourceVideo?.creator),
        uploadDate: toUnixTimestamp(sourceVideo?.createDate ?? sourceVideo?.createdAt),
        datetime: toUnixTimestamp(sourceVideo?.createDate ?? sourceVideo?.createdAt),
        url: `${BASE_URL_VIDEO}/${sourceVideo?.xid}`,
        duration: sourceVideo?.duration ?? 0,
        viewCount,
        isLive,
        isShort,
    };
    return new PlatformVideo(video);
};
const SourceCollectionToGrayjayPlaylistDetails = (pluginId, sourceCollection, videos = []) => {
    return new PlatformPlaylistDetails({
        url: sourceCollection?.xid
            ? `${BASE_URL_PLAYLIST}/${sourceCollection?.xid}`
            : '',
        id: new PlatformID(PLATFORM, sourceCollection?.xid ?? '', pluginId, PLATFORM_CLAIMTYPE),
        author: sourceCollection?.creator
            ? SourceAuthorToGrayjayPlatformAuthorLink(pluginId, sourceCollection?.creator)
            : {},
        name: sourceCollection.name,
        thumbnail: sourceCollection?.thumbnail?.url,
        videoCount: videos.length ?? 0,
        contents: new VideoPager(videos),
    });
};
const SourceCollectionToGrayjayPlaylist = (pluginId, sourceCollection) => {
    return new PlatformPlaylist({
        url: `${BASE_URL_PLAYLIST}/${sourceCollection?.xid}`,
        id: new PlatformID(PLATFORM, sourceCollection?.xid ?? '', pluginId, PLATFORM_CLAIMTYPE),
        author: SourceAuthorToGrayjayPlatformAuthorLink(pluginId, sourceCollection?.creator),
        name: sourceCollection?.name,
        thumbnail: sourceCollection?.thumbnail?.url,
        videoCount: sourceCollection?.metrics?.engagement?.videos?.edges?.[0]?.node?.total,
    });
};
const getIsLive = (sourceVideo) => {
    return (sourceVideo?.isOnAir === true ||
        sourceVideo?.duration == undefined);
};
const getViewCount = (sourceVideo) => {
    let viewCount = 0;
    if (getIsLive(sourceVideo)) {
        const live = sourceVideo;
        //TODO: live?.audienceCount and live.stats.views.total are deprecated
        //live?.metrics?.engagement?.audience?.edges?.[0]?.node?.total is still empty
        viewCount =
            live?.metrics?.engagement?.audience?.edges?.[0]?.node?.total ??
                live?.audienceCount ??
                live?.stats?.views?.total ??
                0;
    }
    else {
        const video = sourceVideo;
        // TODO: both fields are deprecated.
        // video?.stats?.views?.total replaced video?.viewCount
        // now video?.viewCount is deprecated too but there replacement is not accessible yet
        viewCount = video?.viewCount ?? video?.stats?.views?.total ?? 0;
    }
    return viewCount;
};
const SourceVideoToPlatformVideoDetailsDef = (pluginId, sourceVideo, player_metadata) => {
    let positiveRatingCount = 0;
    let negativeRatingCount = 0;
    const ratings = sourceVideo?.metrics?.engagement?.likes?.edges ?? [];
    for (const edge of ratings) {
        const ratingName = edge?.node?.rating;
        const ratingTotal = edge?.node?.total;
        if (POSITIVE_RATINGS_LABELS.includes(ratingName)) {
            positiveRatingCount += ratingTotal;
        }
        else if (NEGATIVE_RATINGS_LABELS.includes(ratingName)) {
            negativeRatingCount += ratingTotal;
        }
    }
    const isLive = getIsLive(sourceVideo);
    const viewCount = getViewCount(sourceVideo);
    const duration = isLive ? 0 : (sourceVideo?.duration ?? 0);
    const source = new HLSSource({
        name: 'HLS',
        duration,
        url: player_metadata?.qualities?.auto[0]?.url,
        requestModifier: {
            options: {
                applyAuthClient: "",
                applyCookieClient: "",
                applyOtherHeaders: false,
                impersonateTarget: IMPERSONATION_TARGET
            }
        }
    });
    const sources = [source];
    const platformVideoDetails = {
        id: new PlatformID(PLATFORM, sourceVideo?.id ?? '', pluginId, PLATFORM_CLAIMTYPE),
        name: sourceVideo?.title ?? '',
        thumbnails: new Thumbnails([
            new Thumbnail(sourceVideo?.thumbnail?.url ?? '', 0),
        ]),
        author: SourceAuthorToGrayjayPlatformAuthorLink(pluginId, sourceVideo?.creator),
        uploadDate: toUnixTimestamp(sourceVideo?.createDate ?? sourceVideo?.createdAt),
        datetime: toUnixTimestamp(sourceVideo?.createDate ?? sourceVideo?.createdAt),
        duration,
        viewCount,
        url: sourceVideo?.xid ? `${BASE_URL_VIDEO}/${sourceVideo.xid}` : '',
        isLive,
        description: sourceVideo?.description ?? '',
        video: new VideoSourceDescriptor(sources),
        rating: new RatingLikesDislikes(positiveRatingCount, negativeRatingCount),
        dash: null,
        live: null,
        hls: null,
        subtitles: [],
    };
    const sourceSubtitle = player_metadata?.subtitles;
    if (sourceSubtitle?.enable && sourceSubtitle?.data) {
        Object.keys(sourceSubtitle.data).forEach((key) => {
            const subtitleData = sourceSubtitle.data[key];
            if (subtitleData) {
                const subtitleUrl = subtitleData.urls[0];
                platformVideoDetails.subtitles.push({
                    name: subtitleData.label,
                    url: subtitleUrl,
                    format: 'text/vtt',
                    getSubtitles() {
                        try {
                            const subResp = httpimp.GET(subtitleUrl, {});
                            if (!subResp.isOk) {
                                if (IS_TESTING) {
                                    bridge.log(`Failed to fetch subtitles from ${subtitleUrl}`);
                                }
                                return '';
                            }
                            return convertSRTtoVTT(subResp.body);
                        }
                        catch (error) {
                            if (IS_TESTING) {
                                bridge.log(`Error fetching subtitles: ${error?.message}`);
                            }
                            return '';
                        }
                    },
                });
            }
        });
    }
    return platformVideoDetails;
};
/**
 * Converts SRT subtitle format to VTT format.
 *
 * @param {string} srt - The SRT subtitle string.
 * @returns {string} - The converted VTT subtitle string.
 */
const convertSRTtoVTT = (srt) => {
    // Initialize the VTT output with the required header
    const vtt = ['WEBVTT\n\n'];
    // Split the SRT input into blocks based on double newlines
    const srtBlocks = srt.split('\n\n');
    // Process each block individually
    srtBlocks.forEach((block) => {
        // Split each block into lines
        const lines = block.split('\n');
        if (lines.length >= 3) {
            // Extract and convert the timestamp line
            const timestamp = lines[1].replace(/,/g, '.');
            // Extract the subtitle text lines
            const subtitleText = lines.slice(2).join('\n');
            // Add the converted block to the VTT output
            vtt.push(`${timestamp}\n${subtitleText}\n\n`);
        }
    });
    // Join the VTT array into a single string and return it
    return vtt.join('');
};

function oauthClientCredentialsRequest(httpClient, url, clientId, secret, visitorId, throwOnInvalid = false) {
    if (!httpClient || !url || !clientId || !secret) {
        throw new ScriptException('Invalid parameters provided to oauthClientCredentialsRequest');
    }
    const body = objectToUrlEncodedString({
        client_id: clientId,
        client_secret: secret,
        grant_type: 'client_credentials',
        visitor_id: visitorId
    });
    try {
        return httpClient.POST(url, body, {
            'User-Agent': USER_AGENT,
            'Content-Type': 'application/x-www-form-urlencoded',
            Origin: BASE_URL,
            DNT: '1',
            'Sec-GPC': '1',
            Connection: 'keep-alive',
            'Sec-Fetch-Dest': 'empty',
            'Sec-Fetch-Mode': 'cors',
            'Sec-Fetch-Site': 'same-site',
            Priority: 'u=4',
            Pragma: 'no-cache',
            'Cache-Control': 'no-cache',
        }, false);
    }
    catch (error) {
        log('OAuth request exception: ' + (error instanceof Error ? error.message : String(error)));
        if (throwOnInvalid) {
            throw new ScriptException('Failed to obtain OAuth client credentials');
        }
        return null;
    }
}
function extractClientCredentials(detailsRequestHtml, httpClient) {
    const result = [];
    // Try new regex patterns on homepage first (credentials might be inlined)
    let clientIdMatch = detailsRequestHtml.body.match(REGEX_API_CLIENT_ID);
    let clientSecretMatch = detailsRequestHtml.body.match(REGEX_API_CLIENT_SECRET);
    if (clientIdMatch && clientSecretMatch && clientIdMatch[1] && clientSecretMatch[1]) {
        result.unshift({
            clientId: clientIdMatch[1],
            secret: clientSecretMatch[1],
        });
        log('Successfully extracted API credentials from homepage');
        return result;
    }
    // Credentials not in homepage HTML - fetch app.js where they are typically located
    if (httpClient) {
        const appJsMatch = detailsRequestHtml.body.match(REGEX_APP_JS_URL);
        if (appJsMatch) {
            const appJsUrl = `https://static.neon-ssr.dailymotion.com/neon-user-ssr/${appJsMatch[0]}`;
            log(`Fetching app.js from ${appJsUrl}`);
            try {
                const appJsResponse = httpClient.GET(appJsUrl, {
                    'User-Agent': USER_AGENT,
                }, false);
                if (appJsResponse?.isOk) {
                    clientIdMatch = appJsResponse.body.match(REGEX_API_CLIENT_ID);
                    clientSecretMatch = appJsResponse.body.match(REGEX_API_CLIENT_SECRET);
                    if (clientIdMatch && clientSecretMatch && clientIdMatch[1] && clientSecretMatch[1]) {
                        result.unshift({
                            clientId: clientIdMatch[1],
                            secret: clientSecretMatch[1],
                        });
                        log('Successfully extracted API credentials from app.js');
                        return result;
                    }
                    else {
                        log('Credentials not found in app.js content');
                    }
                }
                else {
                    log(`Failed to fetch app.js: ${appJsResponse?.code}`);
                }
            }
            catch (error) {
                log(`Error fetching app.js: ${error}`);
            }
        }
        else {
            log('Could not find app.js URL in homepage');
        }
    }
    // Fallback to old regex pattern on homepage
    const match = detailsRequestHtml.body.match(REGEX_INITIAL_DATA_API_AUTH_1);
    if (match?.length === 2 && match[0] && match[1]) {
        result.unshift({
            clientId: match[0],
            secret: match[1],
        });
        log('Successfully extracted API credentials using old regex pattern');
    }
    else {
        log('Failed to extract API credentials using regex. Trying DOM parsing.');
        const htmlElement = domParser.parseFromString(detailsRequestHtml.body, 'text/html');
        const extractedId = getScriptVariableByTextLength(htmlElement, 20);
        const extractedSecret = getScriptVariableByTextLength(htmlElement, 40);
        if (extractedId && extractedSecret) {
            result.unshift({
                clientId: extractedId,
                secret: extractedSecret,
            });
            log(`Successfully extracted API credentials using DOM parsing`);
        }
        else {
            log('Failed to extract API credentials using all methods');
        }
    }
    return result;
}
function getScriptVariableByTextLength(htmlElement, length) {
    const scriptTags = htmlElement.querySelectorAll('script[type="text/javascript"]');
    if (!scriptTags.length) {
        console.error('No script tags found.');
        return null; // or throw an error, depending on your use case
    }
    let pageContent = '';
    scriptTags.forEach((tag) => {
        pageContent += tag.outerHTML;
    });
    let matches = createAuthRegexByTextLength(length).exec(pageContent);
    if (matches?.length == 2) {
        return matches[1];
    }
}
function extractApiEndpoint(homepageHtml) {
    const match = homepageHtml.match(REGEX_API_ENDPOINT);
    if (match && match[1]) {
        log(`Extracted API endpoint: ${match[1]}`);
        return match[1];
    }
    log(`Could not extract API endpoint from homepage, using fallback: ${BASE_URL_API}`);
    return BASE_URL_API;
}
// The auth host is not derivable from the API endpoint; they are separate values
// in window.__RUNTIME_CONFIG__ and currently point at different hosts.
function extractAuthEndpoint(homepageHtml) {
    const match = homepageHtml.match(REGEX_AUTH_ENDPOINT);
    if (match && match[1]) {
        log(`Extracted auth endpoint: ${match[1]}`);
        return match[1];
    }
    log(`Could not extract auth endpoint from homepage, using fallback: ${BASE_URL_API_AUTH}`);
    return BASE_URL_API_AUTH;
}
function getTokenFromClientCredentials(httpClient, credentials, visitorId, authUrl, throwOnInvalid = false) {
    let result = {
        isValid: false,
    };
    const tokenUrl = authUrl || BASE_URL_API_AUTH;
    for (const credential of credentials) {
        const res = oauthClientCredentialsRequest(httpClient, tokenUrl, credential.clientId, credential.secret, visitorId);
        if (res?.isOk) {
            const anonymousTokenResponse = JSON.parse(res.body);
            if (!anonymousTokenResponse.token_type ||
                !anonymousTokenResponse.access_token) {
                log('Invalid token response body: ' + res.body);
                if (throwOnInvalid) {
                    throw new ScriptException('', 'Invalid token response: ' + res.body);
                }
            }
            result = {
                anonymousUserAuthorizationToken: `${anonymousTokenResponse.token_type} ${anonymousTokenResponse.access_token}`,
                anonymousUserAuthorizationTokenExpirationDate: Date.now() + anonymousTokenResponse.expires_in * 1000,
                isValid: true,
            };
            break;
        }
        else {
            log(`Token request failed: code=${res?.code}, body=${res?.body?.substring(0, 200)}`);
        }
    }
    return result;
}

let config;
let _settings;
const state = {
    anonymousUserAuthorizationToken: '',
    anonymousUserAuthorizationTokenExpirationDate: 0,
    commentWebServiceToken: '',
    channelsCache: {},
    maintenanceMode: false,
    visitorId: '',
    visitId: '',
    apiEndpoint: '',
    apiAuthEndpoint: ''
};
source.setSettings = function (settings) {
    _settings = settings;
};
let COUNTRY_NAMES_TO_CODE = [];
let VIDEOS_PER_PAGE_OPTIONS = [];
let PLAYLISTS_PER_PAGE_OPTIONS = [];
let CREATOR_AVATAR_HEIGHT = [];
let THUMBNAIL_HEIGHT = [];
let webclient;
if (IS_IMPERSONATION_AVAILABLE) {
    const httpImpClient = httpimp.getDefaultClient(true);
    if (httpImpClient.setDefaultImpersonateTarget) {
        httpImpClient.setDefaultImpersonateTarget(IMPERSONATION_TARGET);
    }
    webclient = httpimp;
}
else {
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
                }
                else {
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
    }
    catch (ex) {
        log('Failed to parse saveState:' + ex);
        didSaveState = false;
    }
    if (!didSaveState) {
        if (IS_TESTING) {
            log('Getting a new tokens');
        }
        let detailsRequestHtml;
        try {
            detailsRequestHtml = webclient.GET(BASE_URL, applyCommonHeaders(), false);
            if (!detailsRequestHtml.isOk) {
                if (detailsRequestHtml.code >= 500 && detailsRequestHtml.code < 600) {
                    state.maintenanceMode = true;
                    notifyMaintenanceMode();
                }
                else {
                    throw new ScriptException('Failed to fetch page to extract auth details');
                }
                return;
            }
        }
        catch (e) {
            state.maintenanceMode = true;
            notifyMaintenanceMode();
            return;
        }
        state.maintenanceMode = false;
        // Initialize visitor tracking IDs BEFORE getting token
        // so the same visitor ID is used in both the token request and subsequent requests
        state.visitorId = generateUUIDv4();
        state.visitId = Date.now().toString();
        // Both endpoints come from the homepage runtime config. They are read separately
        // because the auth host is not a prefix of the API endpoint.
        state.apiEndpoint = extractApiEndpoint(detailsRequestHtml.body);
        state.apiAuthEndpoint = extractAuthEndpoint(detailsRequestHtml.body);
        const clientCredentials = extractClientCredentials(detailsRequestHtml, webclient);
        const { anonymousUserAuthorizationToken, anonymousUserAuthorizationTokenExpirationDate, isValid, } = getTokenFromClientCredentials(webclient, clientCredentials, state.visitorId, state.apiAuthEndpoint);
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
                const authenticateIm = webclient.POST(BASE_URL_COMMENTS_AUTH, '', applyCommonHeaders({
                    'x-spot-id': FALLBACK_SPOT_ID, //
                    'x-post-id': 'no$post',
                }), false);
                if (!authenticateIm.isOk) {
                    log('Failed to authenticate to comments service');
                }
                state.commentWebServiceToken = authenticateIm?.headers?.['x-access-token']?.[0];
            }
            catch (error) {
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
source.searchSuggestions = function (query) {
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
        return (gqlResponse?.data?.search?.suggestedVideos?.edges?.map((edge) => edge?.node?.name ?? '') ?? []);
    }
    catch (error) {
        log('Failed to get search suggestions:' + error?.message);
        return [];
    }
};
source.getSearchCapabilities = () => SEARCH_CAPABILITIES;
source.search = function (query, type, order, filters) {
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
    if (!state?.channelsCache) {
        state.channelsCache = {};
    }
    if (state.channelsCache[url]) {
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
            if (error1.code === 'GQL_ERROR') {
                const [err] = error1.errors;
                if (err.type === 'moved_permanently') {
                    gqlParams.variables.channel_name = err.redirect_id;
                    const [error2, channelDetails2] = executeGqlQuery(http, gqlParams);
                    if (error2) {
                        log(`Failed to get channel: [${error2.code}] (${error2.operationName})`);
                        throw new ScriptException('Failed to get channel');
                    }
                    channelDetails = channelDetails2;
                }
                else {
                    throw new ScriptException('Failed to get channel');
                }
            }
            else {
                throw new ScriptException('Failed to get channel');
            }
        }
        else {
            channelDetails = channelDetails1;
        }
        if (!channelDetails) {
            throw new ScriptException('Failed to get channel');
        }
        state.channelsCache[url] = SourceChannelToGrayjayChannel(config.id, channelDetails.data.channel, url);
        return state.channelsCache[url];
    }
    catch (error) {
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
    if (sortQuery) {
        switch (sortQuery) {
            case 'visited':
                order = 'Popular';
                break;
        }
    }
    return getChannelContentsPager(url, page, type, order, filters);
};
source.getChannelPlaylists = (url) => {
    try {
        return getChannelPlaylists(url, 1);
    }
    catch (error) {
        log('Failed to get channel playlists:' + error);
        return new ChannelPlaylistPager([]);
    }
};
source.getChannelCapabilities = () => {
    return {
        types: [Type.Feed.Mixed],
        sorts: [Type.Order.Chronological, 'Popular'],
        filters: [],
    };
};
//Video
source.isContentDetailsUrl = function (url) {
    return [REGEX_VIDEO_URL, REGEX_VIDEO_URL_1, REGEX_VIDEO_URL_EMBED].some((r) => r.test(url));
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
        const commentRequest = webclient.POST(BASE_URL_COMMENTS, JSON.stringify(params), commentsHeaders, false);
        if (!commentRequest.isOk) {
            throw new UnavailableException('Failed to authenticate to comments service');
        }
        const comments = JSON.parse(commentRequest.body);
        const users = comments.conversation.users;
        const results = comments.conversation.comments.map((v) => {
            const user = users[v.user_id];
            return new Comment({
                contextUrl: url,
                author: new PlatformAuthorLink(new PlatformID(PLATFORM, user.id ?? '', config.id), user.display_name ?? '', '', `${BASE_URL_COMMENTS_THUMBNAILS}/${user.image_id}`),
                message: v.content[0].text,
                rating: new RatingLikes(v.stars),
                date: v.written_at,
                replyCount: v.total_replies_count ?? 0,
                context: { id: v.id },
            });
        });
        return new PlatformCommentPager(results, comments.conversation.has_next, url, params, ++page);
    }
    catch (error) {
        bridge.log('Failed to get comments:' + error);
        return new PlatformCommentPager([], false, url, params, 0);
    }
}
class PlatformCommentPager extends CommentPager {
    constructor(results, hasMore, path, params, page) {
        super(results, hasMore, { path, params, page });
    }
    nextPage() {
        return getCommentPager(this.context.path, this.context.params, (this.context.page ?? 0) + 1);
    }
}
//Playlist
source.isPlaylistUrl = (url) => {
    return (REGEX_VIDEO_PLAYLIST_URL.test(url) || [
        LIKED_VIDEOS_PLAYLIST_ID,
        FAVORITE_VIDEOS_PLAYLIST_ID,
        RECENTLY_WATCHED_VIDEOS_PLAYLIST_ID
    ].includes(url));
};
source.searchPlaylists = (query, type, order, filters) => {
    return searchPlaylists({ q: query, type, order, filters });
};
source.getPlaylist = (url) => {
    const thumbnailResolutionIndex = _settings.thumbnailResolutionOptionIndex;
    if (url === LIKED_VIDEOS_PLAYLIST_ID) {
        return getLikePlaylist(config.id, http, true, //usePlatformAuth,
        thumbnailResolutionIndex);
    }
    if (url === FAVORITE_VIDEOS_PLAYLIST_ID) {
        return getFavoritesPlaylist(config.id, http, true, //usePlatformAuth,
        thumbnailResolutionIndex);
    }
    if (url === RECENTLY_WATCHED_VIDEOS_PLAYLIST_ID) {
        return getRecentlyWatchedPlaylist(config.id, http, true, //usePlatformAuth,
        thumbnailResolutionIndex);
    }
    const isPrivatePlaylist = url.includes(PRIVATE_PLAYLIST_QUERY_PARAM_FLAGGER);
    if (isPrivatePlaylist) {
        url = url.replace(PRIVATE_PLAYLIST_QUERY_PARAM_FLAGGER, ''); //remove the private flag
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
    const videos = gqlResponse?.data?.collection?.videos?.edges.map((edge) => {
        return SourceVideoToGrayjayVideo(config.id, edge.node);
    });
    return SourceCollectionToGrayjayPlaylistDetails(config.id, gqlResponse?.data?.collection, videos);
};
source.getUserSubscriptions = () => {
    if (!bridge.isLoggedIn()) {
        log('Failed to retrieve subscriptions page because not logged in.');
        throw new ScriptException('Not logged in');
    }
    const usePlatformAuth = true;
    const fetchSubscriptions = (page, first) => {
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
        return (gqlResponse?.data?.me?.channel?.followings?.edges?.map((edge) => edge?.node?.creator?.name ?? '') ?? []);
    };
    const first = 100; // Number of records to fetch per page
    let page = 1;
    const subscriptions = [];
    // There is a totalCount ($.data.me.channel.followings.totalCount) property but it's not reliable.
    // For example, it may return 0 even if there are subscriptions, or it may return a number that is not the actual number of subscriptions.
    // For now, it's better to fetch until no more results are returned
    let items = [];
    do {
        const response = fetchSubscriptions(page, first);
        items = response.map((creatorName) => `${BASE_URL}/${creatorName}`);
        subscriptions.push(...items);
        page++;
    } while (items.length);
    return subscriptions;
};
source.getUserPlaylists = () => {
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
    const userName = gqlResponse?.data?.me?.channel?.name;
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
        const videoXids = gqlResponse?.data?.views?.neon?.sections?.edges?.[0]?.node?.components?.edges?.map(e => e.node.xid) ?? [];
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
        const results = gqlResponse1.data.videos.edges
            ?.map((edge) => {
            return SourceVideoToGrayjayVideo(config.id, edge.node);
        });
        return new VideoPager(results, false);
    }
    catch (error) {
        log('Failed to get recommendations:' + error);
        return new VideoPager([], false);
    }
};
function getPlaylistsByUsername(userName, headers, usePlatformAuth = false) {
    const [error, collections] = executeGqlQuery(http, {
        operationName: 'CHANNEL_PLAYLISTS_QUERY',
        variables: {
            channel_name: userName,
            sort: 'recent',
            page: 1,
            first: 99,
            avatar_size: CREATOR_AVATAR_HEIGHT[_settings.avatarSizeOptionIndex],
            thumbnail_resolution: THUMBNAIL_HEIGHT[_settings.thumbnailResolutionOptionIndex],
        },
        headers,
        query: GET_CHANNEL_PLAYLISTS_XID,
        usePlatformAuth,
    });
    if (error) {
        log('Failed to get playlists by username:' + error.message);
        return [];
    }
    const playlists = collections.data.channel?.collections?.edges?.map((edge) => {
        let playlistUrl = `${BASE_URL_PLAYLIST}/${edge?.node?.xid}`;
        const isPrivatePlaylist = edge?.node?.isPrivate ?? false;
        if (isPrivatePlaylist) {
            playlistUrl += PRIVATE_PLAYLIST_QUERY_PARAM_FLAGGER;
        }
        return playlistUrl;
    }) || [];
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
        thumbnail_resolution: THUMBNAIL_HEIGHT[_settings?.thumbnailResolutionOptionIndex],
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
        ?.playlists;
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
    return new SearchPlaylistPager(searchResults, hasMore, params, context.page, searchPlaylists);
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
                thumbnail_resolution: THUMBNAIL_HEIGHT[_settings?.thumbnailResolutionOptionIndex ?? 0] ?? 'THUMBNAIL_720',
            },
            query: SEACH_DISCOVERY_QUERY,
            headers: headersToAdd,
        });
        if (error) {
            log('Failed to get home page:' + error.message);
            return new VideoPager([], false, { params });
        }
        obj = response;
    }
    catch (error) {
        log('Exception in getHomePager:' + error);
        return new VideoPager([], false, { params });
    }
    const results = obj?.data?.home?.neon?.sections?.edges?.[0]?.node?.components?.edges
        ?.filter((edge) => edge?.node?.id)
        ?.map((edge) => {
        return SourceVideoToGrayjayVideo(config.id, edge.node);
    });
    const hasMore = obj?.data?.home?.neon?.sections?.edges?.[0]?.node?.components?.pageInfo
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
    }
    catch (error) {
        log('Exception in getShortsPager:' + error);
        return new VideoPager([], false, { params });
    }
    const results = obj?.data?.conversations?.edges
        ?.filter((edge) => {
        const story = edge?.node?.story;
        if (!story?.xid)
            return false;
        // Filter to only include shorts (vertical videos with aspect ratio < 1)
        // Aspect ratio < 1 means height > width (portrait/vertical orientation)
        return story.aspectRatio && story.aspectRatio < 1;
    })
        ?.map((edge) => {
        return SourceVideoToGrayjayVideo(config.id, edge.node.story);
    }) ?? [];
    // Note: The conversations API doesn't seem to provide hasNextPage in the same way
    // For now, we'll set hasMore to false. This may need adjustment based on API behavior
    const hasMore = false;
    return new SearchPagerAll(results, hasMore, params, page, getShortsPager);
}
function getChannelContentsPager(url, page, type, order, filters) {
    if (IS_TESTING && !type) {
        type = Type.Feed.Mixed;
    }
    const channel_name = getChannelNameFromUrl(url);
    const shouldLoadVideos = type === Type.Feed.Mixed || type === Type.Feed.Videos;
    const shouldLoadLives = type === Type.Feed.Mixed ||
        type === Type.Feed.Streams ||
        type === Type.Feed.Live;
    if (IS_TESTING) {
        log(`Getting channel contents for ${url}, page: ${page}, type: ${type}, order: ${order}, shouldLoadVideos: ${shouldLoadVideos}, shouldLoadLives: ${shouldLoadLives}, filters: ${JSON.stringify(filters)}`);
    }
    /**
      Recent = Sort liked medias by most recent.
      Visited - Sort liked medias by most viewed
    */
    let sort;
    if (order == Type.Order.Chronological) {
        sort = LikedMediaSort.Recent;
    }
    else if (order == 'Popular') {
        sort = LikedMediaSort.Visited;
    }
    else {
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
            thumbnail_resolution: THUMBNAIL_HEIGHT[_settings?.thumbnailResolutionOptionIndex],
            shouldLoadLives,
            shouldLoadVideos,
        },
        query: CHANNEL_VIDEOS_QUERY,
    });
    if (error) {
        log('Failed to get channel contents:' + error.message);
        return new ChannelVideoPager([], false, { url, type, order, page, filters }, getChannelContentsPager);
    }
    const channel = gqlResponse?.data?.channel;
    const all = [
        ...(channel?.lives?.edges
            ?.filter((e) => e?.node?.isOnAir)
            ?.map((e) => e?.node) ?? []),
        ...(channel?.videos?.edges?.map((e) => e?.node) ?? []),
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
    return new ChannelVideoPager(videos, hasNext, params, getChannelContentsPager);
}
function getSearchPagerAll(contextQuery) {
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
        thumbnail_resolution: THUMBNAIL_HEIGHT[_settings?.thumbnailResolutionOptionIndex],
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
    const videoConnection = gqlResponse?.data?.search?.videos;
    const liveConnection = gqlResponse?.data?.search?.lives;
    const all = [
        ...(videoConnection?.edges ?? []),
        ...(liveConnection?.edges ?? []),
    ];
    const results = all.map((edge) => SourceVideoToGrayjayVideo(config.id, edge?.node));
    const params = {
        query: context.q,
        sort: context.sort,
        filters: context.filters,
    };
    return new SearchPagerAll(results, videoConnection?.pageInfo?.hasNextPage, params, context.page, getSearchPagerAll);
}
function getSavedVideo(url, usePlatformAuth = false) {
    const id = url.split('/').pop();
    const player_metadata_url = `${BASE_URL_METADATA}/${id}?embedder=https%3A%2F%2Fwww.dailymotion.com%2Fvideo%2Fx8yb2e8&geo=1&player-id=xjnde&locale=en-GB&dmV1st=ce2035cd-bdca-4d7b-baa4-127a17490ca5&dmTs=747022&is_native_app=0&app=com.dailymotion.neon&client_type=webapp&section_type=player&component_style=_`;
    const headers1 = applyCommonHeaders();
    if (_settings.hideSensitiveContent) {
        headers1['Cookie'] = 'ff=on';
    }
    else {
        headers1['Cookie'] = 'ff=off';
    }
    const videoDetailsRequestBody = JSON.stringify({
        operationName: 'WATCHING_VIDEO',
        variables: {
            xid: id,
            avatar_size: CREATOR_AVATAR_HEIGHT[_settings?.avatarSizeOptionIndex],
            thumbnail_resolution: THUMBNAIL_HEIGHT[_settings?.thumbnailResolutionOptionIndex],
        },
        query: WATCHING_VIDEO,
    });
    const videoDetailsRequestHeaders = applyCommonHeaders();
    if (!usePlatformAuth) {
        videoDetailsRequestHeaders.Authorization = state.anonymousUserAuthorizationToken;
    }
    const [player_metadataResponse, video_details_response] = http
        .batch()
        .GET(player_metadata_url, headers1, usePlatformAuth)
        .POST(state.apiEndpoint || BASE_URL_API, videoDetailsRequestBody, videoDetailsRequestHeaders, usePlatformAuth)
        .execute();
    if (!player_metadataResponse.isOk) {
        throw new UnavailableException('Unable to get player metadata');
    }
    const player_metadata = JSON.parse(player_metadataResponse.body);
    if (player_metadata.error) {
        if (player_metadata.error.code &&
            ERROR_TYPES[player_metadata.error.code] !== undefined) {
            throw new UnavailableException(ERROR_TYPES[player_metadata.error.code]);
        }
        throw new UnavailableException('This content is not available');
    }
    if (video_details_response.code != 200) {
        throw new UnavailableException('Failed to get video details');
    }
    const video_details = JSON.parse(video_details_response.body);
    const video = video_details?.data?.video;
    const platformVideoDetails = SourceVideoToPlatformVideoDetailsDef(config.id, video, player_metadata);
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
        const channel = edge.node;
        return SourceChannelToGrayjayChannel(config.id, channel);
    });
    const params = {
        query: context.q,
    };
    return new SearchChannelPager(results, searchResponse?.data?.search?.channels?.pageInfo?.hasNextPage, params, context.page, getSearchChannelPager);
}
function getChannelPlaylists(url, page = 1) {
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
            thumbnail_resolution: THUMBNAIL_HEIGHT[_settings.thumbnailResolutionOptionIndex],
        },
        headers,
        query: CHANNEL_PLAYLISTS_QUERY,
        usePlatformAuth,
    });
    if (error) {
        log('Failed to get channel playlists:' + error.message);
        return new PlaylistPager([], false);
    }
    const channel = gqlResponse.data.channel;
    const content = (channel?.collections?.edges ?? [])
        .filter((e) => e?.node?.metrics?.engagement?.videos?.edges?.[0]?.node?.total) //exclude empty playlists. could be empty doe to geographic restrictions
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
    return new ChannelPlaylistPager(content, hasMore, params, page, getChannelPlaylists);
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
    const usePlatformAuth = requestOptions.usePlatformAuth == undefined
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
        }
        catch (parseError) {
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
    }
    catch (error) {
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
    const usePlatformAuth = requestOptions.usePlatformAuth == undefined
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
        }
        catch (parseError) {
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
    }
    catch (error) {
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
function getPages(httpClient, query, operationName, variables, usePlatformAuth, setRoot, hasNextCallback, getNextPage, map) {
    let all = [];
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
function getLikePlaylist(pluginId, httpClient, usePlatformAuth = false, thumbnailResolutionIndex = 0) {
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
function getFavoritesPlaylist(pluginId, httpClient, usePlatformAuth = false, thumbnailResolutionIndex = 0) {
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
function getRecentlyWatchedPlaylist(pluginId, httpClient, usePlatformAuth = false, thumbnailResolutionIndex = 0) {
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
function getPlatformSystemPlaylist(opts) {
    const videos = getPages(opts.httpClient, opts.query, opts.operationName, {
        page: 1,
        thumbnail_resolution: THUMBNAIL_HEIGHT[opts.thumbnailResolutionIndex],
    }, opts.usePlatformAuth, (gqlResponse) => gqlResponse?.data?.me, //set root
    (me) => (me?.[opts.rootObject]?.edges?.length ?? 0) > 0, //hasNextCallback
    (me, currentPage) => ++currentPage, //getNextPage
    (me) => me?.[opts.rootObject]?.edges.map((edge) => {
        return SourceVideoToGrayjayVideo(opts.pluginId, edge.node);
    }));
    const collection = {
        id: generateUUIDv4(),
        name: opts.playlistName,
        creator: {},
    };
    return SourceCollectionToGrayjayPlaylistDetails(opts.pluginId, collection, videos);
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
