const PLATFORM = "Apple Podcasts";
const PLATFORM_BASE_URL = "https://podcasts.apple.com";
const PLATFORM_SAVED_EPISODES_URL = "https://podcasts.apple.com/us/library/saved-episodes";
const PLATFORM_BASE_URL_API = 'https://amp-api.podcasts.apple.com'
const PLATFORM_BASE_ASSETS_URL = "https://podcasts.apple.com/assets/";
const URL_CHANNEL = "https://podcasts.apple.com/us/podcast/";

const API_SEARCH_URL_TEMPLATE = 'https://amp-api.podcasts.apple.com/v1/catalog/us/search/groups?groups=episode&l=en-US&offset=25&term={0}&types=podcast-episodes&platform=web&extend[podcast-channels]=availableShowCount&include[podcast-episodes]=channel,podcast&limit=25&with=entitlements';
const API_SEARCH_PODCASTS_URL_TEMPLATE = 'https://itunes.apple.com/search?media=podcast&term={query}';
const API_SEARCH_PODCAST_CHANNELS_URL_TEMPLATE = 'https://amp-api.podcasts.apple.com/v1/catalog/{country}/search/suggestions?platform=web&types=podcast-channels&limit%5Bresults%3AtopResults%5D=10&kinds=topResults&term={query}';
const API_SEARCH_AUTOCOMPLETE_URL_TEMPLATE = 'https://amp-api.podcasts.apple.com/v1/catalog/{country}/search/suggestions?kinds=terms&term={query}';
const API_GET_PODCAST_EPISODES_URL_TEMPLATE = 'https://amp-api.podcasts.apple.com/v1/catalog/us/podcasts/{podcast-id}/episodes?l=en-US&offset={offset}';
const API_GET_EPISODE_DETAILS_URL_TEMPLATE = 'https://amp-api.podcasts.apple.com/v1/catalog/us/podcast-episodes/{episode-id}?include=channel,podcast&include[podcasts]=episodes,podcast-seasons,trailers&include[podcast-seasons]=episodes&fields=artistName,artwork,assetUrl,contentRating,description,durationInMilliseconds,episodeNumber,guid,isExplicit,kind,mediaKind,name,offers,releaseDateTime,season,seasonNumber,storeUrl,summary,title,url&with=entitlements&l=en-US';
const API_GET_PUBLISHER_CHANNEL_PODCASTS_URL_TEMPLATE = 'https://amp-api.podcasts.apple.com/v1/catalog/us/podcast-channels/{channel-id}/view/top-shows?l=en-US&offset={offset}&extend[podcast-channels]=isSubscribed,subscriptionOffers,title&include[podcasts]=channel&include[podcast-episodes]=channel,podcast&limit=20&with=entitlements';
const API_GET_PUBLISHER_CHANNEL_EPISODES_URL_TEMPLATE = 'https://amp-api.podcasts.apple.com/v1/catalog/us/podcast-channels/{channel-id}/view/top-episodes?l=en-US&offset={offset}&extend[podcast-channels]=isSubscribed,subscriptionOffers,title&include[podcasts]=channel&include[podcast-episodes]=channel,podcast&limit=20&with=entitlements';
const API_GET_PUBLISHER_CHANNEL_URL_TEMPLATE = 'https://amp-api.podcasts.apple.com/v1/catalog/us/podcast-channels/{channel-id}?l=en-US';
const API_ITUNES_LOOKUP_URL_TEMPLATE = 'https://itunes.apple.com/lookup?id={id}';
const API_ITUNES_LOOKUP_EPISODES_URL_TEMPLATE = 'https://itunes.apple.com/lookup?id={id}&entity=podcastEpisode&limit=200';
const API_V2_TOP_PODCASTS_URL_TEMPLATE = 'https://rss.marketingtools.apple.com/api/v2/{country}/podcasts/top/{limit}/podcasts.json';
const API_V2_TOP_EPISODES_URL_TEMPLATE = 'https://rss.marketingtools.apple.com/api/v2/{country}/podcasts/top/{limit}/podcast-episodes.json';

// URL construction templates
const URL_PODCAST_TEMPLATE = 'https://podcasts.apple.com/{country}/podcast/{podcast-id}';
const URL_PODCAST_WITH_ID_PREFIX_TEMPLATE = 'https://podcasts.apple.com/{country}/podcast/id{podcast-id}';
const URL_PODCAST_NO_COUNTRY_TEMPLATE = 'https://podcasts.apple.com/podcast/{podcast-id}';
const URL_PODCAST_NO_COUNTRY_WITH_ID_PREFIX_TEMPLATE = 'https://podcasts.apple.com/podcast/id{podcast-id}';
const URL_CHANNEL_TEMPLATE = 'https://podcasts.apple.com/{country}/channel/{channel-id}';
const URL_EPISODE_TEMPLATE = 'https://podcasts.apple.com/{country}/podcast/episode/id{podcast-id}?i={episode-id}';
const URL_APPLE_SUPPORT_SUBSCRIBER_CONTENT = 'https://support.apple.com/en-us/108378';

// Fallback API URLs (used when templates aren't suitable)
const API_ITUNES_SEARCH_POPULAR_FALLBACK = 'https://itunes.apple.com/search?media=podcast&limit=25&term=popular';

const API_GET_TRENDING_EPISODES_URL_PATH_TEMPLATE = '/v1/catalog/{country}/charts?chart=top&genre=26&l=en-US&limit=10&offset=0&types=podcast-episodes'
const API_GET_TRENDING_EPISODES_URL_QUERY_PARAMS = 'extend[podcasts]=editorialArtwork,feedUrl&include[podcast-episodes]=podcast&types=podcast-episodes&with=entitlements';

const API_GET_SUBSCRIPTIONS_FIRST_PAGE_PATH = '/v1/me/library/podcasts?limit=30&relate[podcasts]=channel&with=entitlements&l=en-US';//next pages are gotten from the next field (cursor) in the response
const API_GET_SAVED_EPISODES_FIRST_PAGE_PATH = '/v1/me/library/podcast-episodes?include[podcast-episodes]=channel,playback-position,podcast&limit=30&fields[podcast-channels]=subscriptionName,isSubscribed&with=entitlements&l=en-US';//next pages are gotten from the next field (cursor) in the response

const REGEX_CONTENT_URL = /https:\/\/podcasts\.apple\.com\/[a-zA-Z]*\/podcast\/.*?\/id([0-9]*)\?i=([0-9]*).*?/s
const REGEX_CHANNEL_URL = /https:\/\/(podcasts|embed\.podcasts)\.apple\.com\/[a-zA-Z]{2}\/podcast(?:\/[^/]+)?\/(?:id)?([0-9]+)/si;
const REGEX_CHANNEL_SHOW = /<script id=schema:show type="application\/ld\+json">(.*?)<\/script>/s
const REGEX_CHANNEL_SERVER_DATA = /<script\s+(?:[^>]*?\s+)?(?:id=["']serialized-server-data["']\s+type=["']application\/(?:ld\+)?json["']|type=["']application\/(?:ld\+)?json["']\s+id=["']serialized-server-data["'])\s*>(.*?)<\/script>/s;
const REGEX_EPISODE_ID = /[?&]i=([^&]+)/;
const REGEX_IMAGE = /<meta property="og:image" content="(.*?)">/s
const REGEX_MAIN_SCRIPT_FILENAME = /index[~-]\w+\.js/;
// Match a JWT regardless of header field ordering. Apple's MusicKit token header was
// {"alg":...} (encodes to eyJhbGci...) but is now {"typ":"JWT","alg":...} (eyJ0eXAi...),
// so anchor on the generic "eyJ" prefix and rely on the 3-part structure plus the long
// signature segment ({43,}) to avoid false matches.
const REGEX_JWT = /\beyJ[A-Za-z0-9-_]+?\.[A-Za-z0-9-_]+?\.[A-Za-z0-9-_]{43,}\b/;
const REGEX_PUBLISHER_CHANNEL_URL = /https:\/\/podcasts\.apple\.com\/([a-z]{2})\/channel\/(?:([^\/]+)\/)?(?:id)?([0-9]+)/si;

const SAVED_EPISODES_KEY  = 'applepodcasts:playlist:savedepisodes';

const IS_DESKTOP = bridge.buildPlatform === "desktop";
const IMPERSONATION_TARGET = IS_DESKTOP ? 'chrome136' : 'chrome131_android';

// API pagination constants
const PODCAST_EPISODES_PAGE_SIZE = 10;  // Episodes per page for podcast episode listings
const PUBLISHER_CHANNEL_PAGE_SIZE = 20; // Items per page for publisher channel content

// Toast messages
const TOAST_MSG_FALLBACK_GENERIC = 'using fallback.';
const TOAST_MSG_FALLBACK_TOP_PODCASTS = 'Using fallback: Top Podcasts';
const TOAST_MSG_FALLBACK_ITUNES_SEARCH = 'Using fallback: iTunes Search';

// API limits
const DEFAULT_HOME_LIMIT = 25;

// Time conversion constants
const MS_PER_SECOND = 1000;

// Default country code
const DEFAULT_COUNTRY_CODE = 'us';

// Content ratings
const CONTENT_RATING_EXPLICIT = 'Explicit';
const CONTENT_RATING_EXPLICIT_LOWER = 'explicit';

// Media kinds
const MEDIA_KIND_AUDIO = 'audio';
const MEDIA_KIND_VIDEO = 'video';

// Subscriber offer kind
const OFFER_KIND_SUBSCRIBE = 'subscribe';

// Pre-compiled regex for URL replacement
const REGEX_URL_COUNTRY_CODE_REPLACEMENT = /https:\/\/podcasts\.apple\.com\/[a-z]{2}/;

let state = {
	headers: {},
	channel: {},
	episodeDetails: {}  // Cache for episode details from iTunes API fallbacks
};

let COUNTRY_CODES = [];


let config = {};
let _settings = {
	countryIndex: 0,
	allowExplicit: false,
	contentRecommendationOptionIndex: 0,
	hideSubscriberOnly: false
};

//Source Methods
source.enable = function(conf, settings, savedState){
	try {
		config = conf ?? {};
		_settings = settings ?? {};

		if(_settings.countryIndex == undefined) {
			_settings.countryIndex = 0;
		}

		if(_settings.allowExplicit == undefined) {
			_settings.allowExplicit = false;
		}

		if(IS_TESTING) {
			_settings.allowExplicit = true;
		}

		if(_settings.hideSubscriberOnly == undefined) {
			_settings.hideSubscriberOnly = false;
		}

		if(_settings.contentRecommendationOptionIndex == undefined) {
			_settings.contentRecommendationOptionIndex = 0;
		}
		
		COUNTRY_CODES = loadOptionsForSetting('countryIndex').map((c) => c.toLowerCase().split(' - ')[0]);

		let didSaveState = false;
	  
		try {
		  if (savedState) {
			state = JSON.parse(savedState);
			didSaveState = true;
		  }
		} catch (ex) {
		  log('Failed to parse saveState:' + ex);
		}
	  
		if (!didSaveState) {
		  // init state
		  try {
			  const indexHtml = makeGetRequest(PLATFORM_BASE_URL, {
				  parseResponse: false,
				  customHeaders: { 'User-Agent': config.authentication.userAgent }
			  });

			  if (indexHtml) {
				  // Extract the main script file name from the index page
				  const scriptFileName = extractScriptFileName(indexHtml);
				  if(scriptFileName) {
					  // Get the main script file content
					  const scriptContent = makeGetRequest(`${PLATFORM_BASE_ASSETS_URL}${scriptFileName}`, {
						  parseResponse: false,
						  customHeaders: { 'User-Agent': config.authentication.userAgent }
					  });

					  if (scriptContent) {
						  // Extract the JWT token from the main script content
						  const token = extractJWT(scriptContent);

						  if(token) {
							  state.headers = { Authorization: `Bearer ${token}`, Origin: PLATFORM_BASE_URL, 'User-Agent': config.authentication.userAgent };
							  log("Successfully extracted JWT token");
						  } else {
							  log("Failed to extract JWT token from script content");
						  }
					  } else {
						  log("Failed to fetch script content");
					  }
				  } else {
					  log("Failed to extract script file name from HTML");
				  }
			  } else {
				  log("Failed to fetch index HTML");
			  }
		  } catch (e) {
			  log("Error during JWT token extraction: " + e.message);
		  }

		  // Set default headers even if JWT extraction failed
		  if (!state.headers || Object.keys(state.headers).length === 0) {
			  state.headers = { 'User-Agent': config.authentication.userAgent };
			  log("Using fallback headers without JWT token");
		  }
		}
	} catch(e) {
		console.error(e);
	}
}

source.getHome = function () {

    const selectedCountry = getUserCountry();
    const requestPath = API_GET_TRENDING_EPISODES_URL_PATH_TEMPLATE.replace("{country}", selectedCountry);

    class RecommendedVideoPager extends VideoPager {
        constructor({ media = [], hasMore = true, context = { requestPath } } = {}) {
            super(media, hasMore, context);
            this.url = `${PLATFORM_BASE_URL_API}${context.requestPath}&${API_GET_TRENDING_EPISODES_URL_QUERY_PARAMS}`;
        }

        nextPage() {
            const data = makeGetRequest(this.url, { throwOnError: false });

            if (!data) {
                const userCountry = getUserCountry().toLowerCase();

                // Fallback chain: v2 top episodes -> v2 top podcasts -> iTunes Search
                log("Main trending API failed, trying v2 Marketing Tools top episodes fallback");
                const v2EpisodesUrl = API_V2_TOP_EPISODES_URL_TEMPLATE
                    .replace('{country}', userCountry)
                    .replace('{limit}', DEFAULT_HOME_LIMIT.toString());
                const v2EpisodesResult = makeGetRequest(v2EpisodesUrl, { throwOnError: false });

                if (v2EpisodesResult && v2EpisodesResult.feed && v2EpisodesResult.feed.results) {
                    log("v2 top episodes fallback successful");
                    const contents = v2EpisodesResult.feed.results
                        .map(episode => v2EpisodeToPlatformVideo(episode))
                        .filter(Boolean);
                    if (contents.length > 0) {
                        return new ContentPager(contents, false);
                    }
                }

                log("v2 top episodes failed, trying v2 Marketing Tools top podcasts fallback");
                const v2Url = API_V2_TOP_PODCASTS_URL_TEMPLATE
                    .replace('{country}', userCountry)
                    .replace('{limit}', DEFAULT_HOME_LIMIT.toString());
                const v2Result = makeGetRequest(v2Url, { throwOnError: false });

                if (v2Result && v2Result.feed && v2Result.feed.results) {
                    log("v2 API fallback successful");
                    bridge.toast(TOAST_MSG_FALLBACK_TOP_PODCASTS);
                    const contents = v2Result.feed.results
                        .map(podcast => v2PodcastToPlatformPlaylist(podcast))
                        .filter(Boolean);
                    return new ContentPager(contents, false);
                }

                // If v2 fails, fall back to iTunes Search API
                log("v2 API failed, trying iTunes Search API fallback");
                const itunesUrl = API_ITUNES_SEARCH_POPULAR_FALLBACK;
                const itunesResult = makeGetRequest(itunesUrl, { throwOnError: false });

                if (itunesResult && itunesResult.results) {
                    log("iTunes Search fallback successful");
                    bridge.toast(TOAST_MSG_FALLBACK_ITUNES_SEARCH);
                    const contents = itunesResult.results
                        .map(x => itunesPodcastToPlatformPlaylist(x))
                        .filter(Boolean);
                    return new ContentPager(contents, false);
                }

                return new ContentPager([], false);
            }

            const episodes = data?.results?.['podcast-episodes']?.find(x => x.chart == "top");

            const contents = (episodes?.data ?? [])
                .map(x => podcastToPlatformVideo(x))
				.filter(Boolean)
                .sort((a, b) => b.datetime - a.datetime);

            return new RecommendedVideoPager({
                media: contents,
                hasMore: !!episodes.next,
                context: { requestPath: episodes.next },
            });
        }
    }

    return new RecommendedVideoPager({ context: { requestPath } }).nextPage();

};

source.searchSuggestions = function (query) {
    try {
		const selectedCountry = getUserCountry();
    	
		const requestPath = API_SEARCH_AUTOCOMPLETE_URL_TEMPLATE
			.replace("{country}", selectedCountry)
			.replace("{query}", encodeURIComponent(query))

		const res = makeGetRequest(requestPath, { throwOnError: false });

		if (!res) {
			return [];
		}

		return res?.results?.suggestions?.map(e => e.searchTerm) ?? [];
    }
    catch (error) {
        log('Failed to get search suggestions:' + error?.message);
        return [];
    }
};
source.getSearchCapabilities = () => {
	return {
		types: [Type.Feed.Mixed],
		sorts: [Type.Order.Chronological],
		filters: [ ]
	};
};
source.search = function (query, type, order, filters) {
	const url = API_SEARCH_URL_TEMPLATE.replace("{0}", encodeURIComponent(query));

	const result = makeGetRequest(url, { throwOnError: false });
	if (!result) {
		// Fallback to iTunes API when main API fails
		log("Main search API failed, trying iTunes API fallback");
		const itunesUrl = API_SEARCH_PODCASTS_URL_TEMPLATE.replace("{query}", encodeURIComponent(query));
		const itunesResult = makeGetRequest(itunesUrl, { throwOnError: false });

		if (itunesResult && itunesResult.results) {
			const results = itunesResult.results
				.map(x => itunesPodcastToPlatformVideo(x))
				.filter(Boolean);
			return new ContentPager(results, false);
		}

        return new ContentPager([], false);
    }

    const episodes = result.results.groups
	.find(x => x.groupId == "episode")?.data || [];

	const results = episodes
	.map(x => podcastToPlatformVideo(x))
	.filter(Boolean)

	return new ContentPager(results, false);
};
source.getSearchChannelContentsCapabilities = function () {
	return {
		types: [Type.Feed.Mixed],
		sorts: [Type.Order.Chronological],
		filters: []
	};
};
source.searchChannels = function(query) {
	// Prepare URLs for both API requests
	const encodedQuery = encodeURIComponent(query);
	const urlRequestPodcasts = API_SEARCH_PODCASTS_URL_TEMPLATE.replace("{query}", encodedQuery);

	const selectedCountry = getUserCountry();
	const urlRequestPodcastChannel = API_SEARCH_PODCAST_CHANNELS_URL_TEMPLATE
		.replace("{country}", selectedCountry)
		.replace("{query}", encodedQuery);

	// Function to process iTunes podcast results
	function processPodcastResults(podcastRes) {
		if (!podcastRes) {
			return [];
		}
		return podcastRes?.results?.map(x => {
			const podcastId = extractPodcastId(x.collectionViewUrl);
			const podcastUrl = buildPodcastUrl(podcastId);
			return createPlatformAuthor(
				podcastId,
				x?.collectionName ?? x?.trackName ?? x?.collectionCensoredName ?? '',
				podcastUrl,
				x.artworkUrl100 ?? ""
			);
		});
	}

	// Function to process podcast channel results
	function processPodcastChannelResults(result) {
		if (!result || !result.results || !result.results.suggestions) {
			return [];
		}
		
		const podcastChannels = [];
		result?.results?.suggestions?.forEach(suggestion => {
			if (suggestion.kind === 'topResults' && suggestion.content) {
				const content = suggestion.content;
				if (content.type === 'podcast-channels') {
					const channel = content;
					podcastChannels.push(new PlatformAuthorLink(
						new PlatformID(PLATFORM, channel.id, config.id),
						channel.attributes.name,
						channel.attributes.url,
						getArtworkUrl(channel.attributes.artwork.url)
					));
				}
			}
		});
		return podcastChannels;
	}

	// Try batch request approach
	try {
		// Set up batch request parameters for both API calls
		const batchResults = http.batch()
			.GET(urlRequestPodcasts, state.headers)
			.GET(urlRequestPodcastChannel, state.headers)
			.execute();
		
		// Process response from iTunes podcast API
		let podcastsResults = [];
		if (batchResults[0].isOk) {
			try {
				const podcastResponse = JSON.parse(batchResults[0].body);
				podcastsResults = processPodcastResults(podcastResponse);
			} catch (e) {
				log("Error processing podcast results: " + e.message);
			}
		}
		
		// Process response from Apple Podcasts channels API
		let podcastChannels = [];
		if (batchResults[1].isOk) {
			try {
				const channelResponse = JSON.parse(batchResults[1].body);
				podcastChannels = processPodcastChannelResults(channelResponse);
			} catch (e) {
				log("Error processing podcast channel results: " + e.message);
			}
		}
		
		// Combine and return results
		return new ChannelPager([...podcastChannels, ...podcastsResults], false);
	} catch (error) {
		// Fallback to sequential requests if batch fails
		log("Batch request failed, falling back to sequential: " + error.message);
		try {
			// Make sequential requests
			const podcastResponse = makeGetRequest(urlRequestPodcasts, { throwOnError: false });
			const channelResponse = makeGetRequest(urlRequestPodcastChannel, { throwOnError: false });
			
			// Check for errors in each response
			if (!podcastResponse && !channelResponse) {
				log("Both fallback requests failed");
				return new ChannelPager([], false);
			}
			
			// Process podcast results
			let podcastsResults = [];
			if (podcastResponse) {
				try {
					podcastsResults = processPodcastResults(podcastResponse);
				} catch (e) {
					log("Error processing fallback podcast results: " + e.message);
				}
			} else {
				log("Fallback podcast request failed");
			}
			
			// Process channel results
			let podcastChannels = [];
			if (channelResponse) {
				try {
					podcastChannels = processPodcastChannelResults(channelResponse);
				} catch (e) {
					log("Error processing fallback channel results: " + e.message);
				}
			} else {
				log("Fallback channel request failed");
			}
			
			// Combine and return results (even if one of them failed)
			return new ChannelPager([...podcastChannels, ...podcastsResults], false);
		} catch (fallbackError) {
			log("Sequential fallback also failed: " + fallbackError.message);
			return new ChannelPager([], false);
		}
	}
};

//Channel
source.isChannelUrl = function(url) {
	return REGEX_CHANNEL_URL.test(url) || REGEX_PUBLISHER_CHANNEL_URL.test(url);
};

source.getChannel = function(url) {
    // Check if it's a publisher channel URL
    const publisherMatch = url.match(REGEX_PUBLISHER_CHANNEL_URL);
    if (publisherMatch) {
        const channelId = publisherMatch[3];

        // If already cached, return it
        if (state.channel[channelId]) {
            return state.channel[channelId];
        }

        const apiUrl = API_GET_PUBLISHER_CHANNEL_URL_TEMPLATE.replace('{channel-id}', channelId);

        const channelData = makeGetRequest(apiUrl, { throwOnError: false });
        if (!channelData) {
            // Fallback to iTunes API for basic podcast info
            // Note: iTunes API typically doesn't support publisher channels (networks),
            // so this fallback will only work if the ID happens to be a regular podcast
            log(`Main publisher channel API failed for channel ${channelId}, trying iTunes API fallback`);
            const itunesUrl = API_ITUNES_LOOKUP_URL_TEMPLATE.replace('{id}', channelId);
            const itunesData = makeGetRequest(itunesUrl, { throwOnError: false });

            if (itunesData && itunesData.results && itunesData.results.length > 0) {
                const podcastInfo = itunesData.results[0];
                log(`iTunes API fallback successful for channel ${channelId}`);

				bridge.toast(TOAST_MSG_FALLBACK_GENERIC)

                state.channel[channelId] = new PlatformChannel({
                    id: new PlatformID(PLATFORM, channelId, config.id),
                    name: podcastInfo.collectionName || podcastInfo.trackName || '',
                    thumbnail: podcastInfo.artworkUrl600 || podcastInfo.artworkUrl100 || "",
                    banner: podcastInfo.artworkUrl600 || podcastInfo.artworkUrl100 || "",
                    subscribers: -1,
                    description: podcastInfo.description || '',
                    url: url,
                    urlAlternatives: [url],
                    links: { website: '' }
                });

                return state.channel[channelId];
            }

            log(`iTunes API fallback failed for channel ${channelId} - iTunes API does not support publisher channels`);
            throw new ScriptException("Failed to get publisher channel");
        }

        const attributes = channelData.data[0].attributes;

        state.channel[channelId] = new PlatformChannel({
            id: new PlatformID(PLATFORM, channelId, config.id),
            name: attributes.name,
            thumbnail: getArtworkUrl(attributes.artwork.url),
            banner: attributes.logoArtwork ? getArtworkUrl(attributes.logoArtwork.url) : null,
            subscribers: -1,
            description: attributes.description?.standard || '',
            url: url,
            urlAlternatives: [url],
            links: { website: attributes.websiteUrl || '' }
        });

        return state.channel[channelId];
    }
    
    // Regular podcast channel handling
    const matchUrl = url.match(REGEX_CHANNEL_URL);
    if (!matchUrl) {
        throw new ScriptException(`Invalid channel URL: ${url}`);
    }
    const podcastId = matchUrl[2];

    // check if channel is cached and return it
    if(state.channel[podcastId]) {
        return state.channel[podcastId];
    }

	let channelUrl = removeQueryParams(url);
    
    // Convert embed URLs to regular podcast URLs for fetching HTML content
    if (channelUrl.includes('embed.podcasts.apple.com')) {
        channelUrl = channelUrl.replace('embed.podcasts.apple.com', 'podcasts.apple.com');
    }

    const htmlContent = makeGetRequest(channelUrl, {
        parseResponse: false,
        throwOnError: false
    });

    if (!htmlContent) {
        // Fallback to iTunes API for basic podcast info
        log(`Main channel page failed for podcast ${podcastId}, trying iTunes API fallback`);
        const itunesUrl = API_ITUNES_LOOKUP_URL_TEMPLATE.replace('{id}', podcastId);
        const itunesData = makeGetRequest(itunesUrl, { throwOnError: false });

        if (itunesData && itunesData.results && itunesData.results.length > 0) {
            const podcastInfo = itunesData.results[0];
            log(`iTunes API fallback successful for podcast ${podcastId}`);
			bridge.toast(TOAST_MSG_FALLBACK_GENERIC);

            const urlAlternatives = generatePodcastUrlAlternatives(podcastId, url);

            state.channel[podcastId] = new PlatformChannel({
                id: new PlatformID(PLATFORM, podcastId, config.id),
                name: podcastInfo.collectionName || podcastInfo.trackName || '',
                thumbnail: podcastInfo.artworkUrl600 || podcastInfo.artworkUrl100 || "",
                banner: podcastInfo.artworkUrl600 || podcastInfo.artworkUrl100 || "",
                subscribers: -1,
                description: podcastInfo.description || '',
                url: buildPodcastUrl(podcastId, 'us'),
                links: {},
                urlAlternatives
            });

            return state.channel[podcastId];
        }

        throw new ScriptException("Failed to get channel page");
    }

    const showMatch = htmlContent.match(REGEX_CHANNEL_SHOW);
    if(!showMatch || showMatch.length != 2) {
        throw new ScriptException("Could not find show data");
    }
    const showData = JSON.parse(showMatch[1]);

	const serverDataMatch = htmlContent.match(REGEX_CHANNEL_SERVER_DATA);
	let serverData;
	if(serverDataMatch && serverDataMatch.length == 2) {
		serverData = JSON.parse(serverDataMatch[1]);
	}

	let description = showData.description ?? '';
	const links = {};

	let items = serverData?.[0]?.data?.shelves?.find(x => x.contentType === 'showHeaderRegular')?.items?.[0];
	const informationItems = serverData?.[0]?.data?.shelves?.find(x => x.contentType === "information")?.items;

	let metaObj;

	try {
		metaObj = (items?.metadata ?? []).reduce((acc, item) => {
			const [key] = Object.keys(item);
			acc[key] = item[key];
			return acc;
		}, {});
	} catch(e) {
		log(`failed to parse metadata: ${e}`);
	}

	let copyrightDescription = '';

	if(informationItems?.length) {
		
		const websiteItem = informationItems.find(item => item.title === 'Show Website');
		
		if(websiteItem) {
			links.website = websiteItem.action.url;
		}

		const episodeCount = informationItems.find(item => item.id === 'InformationShelfEpisodeCount');
		if(episodeCount) {
			description += `<p>${episodeCount.title}: ${episodeCount.description}</p>`;
		}

		const yearActive = informationItems.find(item => item.title === 'Years Active');
		if(yearActive) {
			description += `<p>${yearActive.title}: ${yearActive.description}</p>`;
		}

		const copyright = informationItems.find(item => item.title === 'Copyright');
		if(copyright) {
			copyrightDescription += `<p>${copyright.title}: ${copyright.description}</p>`;
		}
	}

	if(metaObj?.category) {
		const category = metaObj?.category?.title ?? metaObj?.category ?? '';
		if(category) {
			description += `<p>Category: ${category}</p>`;
		}
	}

	if(metaObj?.explicit) {
		description += `<p>Explicit: ${metaObj.explicit ? 'Yes' : 'No'}</p>`;
	};

	if(metaObj?.updateFrequency) {
		description += `<p>Frequency: ${metaObj.updateFrequency}</p>`;
	}

	if(metaObj?.ratings?.ratingAverage) {
		description += `<p>Average Rating: ${metaObj?.ratings?.ratingAverage ?? 0} (votes: ${metaObj?.ratings?.totalNumberOfRatings ?? 0})</p>`;
	}

	description += `<p>Show (${showData.name}): ` + channelUrl + '</p>';

	if(items?.providerAction?.title && items?.providerAction?.pageUrl) {
		description += `<p>Channel (${items.providerAction.title}): ` + items.providerAction.pageUrl+ '</p>';
	}

	description += `${copyrightDescription}`;

    const banner = matchFirstOrDefault(htmlContent, REGEX_IMAGE);

	// Generate URL alternatives, including showData.url and its regionalized versions
	const urlAlternatives = generatePodcastUrlAlternatives(podcastId, url);

	// Add showData.url and its regionalized versions
	urlAlternatives.push(showData.url);
	COUNTRY_CODES.forEach(countryCode => {
		urlAlternatives.push(showData.url.replace(REGEX_URL_COUNTRY_CODE_REPLACEMENT, `https://podcasts.apple.com/${countryCode}`));
	});

    // save channel info to state (cache)
    state.channel[podcastId] = new PlatformChannel({
        id: new PlatformID(PLATFORM, podcastId, config.id),
        name: showData.name,
        thumbnail: banner,
        banner,
        subscribers: -1,
        description,
        url: buildPodcastUrl(podcastId, 'us'),
        links,
		urlAlternatives: Array.from(new Set(urlAlternatives))
    });

    return state.channel[podcastId];
};

source.getChannelContents = function(url, type, order, filters, isPlaylist) {
    // Check if it's a publisher channel URL
    if (REGEX_PUBLISHER_CHANNEL_URL.test(url)) {
        return new ApplePublisherChannelEpisodesPager(url);
    }
    
    // Otherwise, handle regular podcast channels
    const id = removeRemainingQuery(url.match(REGEX_CHANNEL_URL)[2]);
    return new AppleChannelContentPager(id, url, isPlaylist);
};

source.getChannelPlaylists = function(url) {
    // Check if it's a publisher channel URL
    if (REGEX_PUBLISHER_CHANNEL_URL.test(url)) {
        return new PublisherChannelPlaylistsPager(url);
    }
    
    // Check if it's a regular podcast channel URL
    if (REGEX_CHANNEL_URL.test(url)) {
        return new PodcastEpisodesPlaylistPager(url);
    }
    
    // For other URLs, return empty pager
    return new PlaylistPager([], false);
};

class AppleChannelContentPager extends ContentPager {
	constructor(id, channelUrl, isPlaylist) {
		super(fetchEpisodesPage(id, channelUrl, 0, isPlaylist), true);
		this.offset = PODCAST_EPISODES_PAGE_SIZE; // Start at next page offset (first page is always offset 0)
		this.id = id;
		this.channelUrl = channelUrl;
		this.isPlaylist = isPlaylist;
	}

	nextPage() {
		this.results = fetchEpisodesPage(this.id, this.channelUrl, this.offset, this.isPlaylist);
		this.hasMore = this.results.length > 0;
		this.offset += PODCAST_EPISODES_PAGE_SIZE; // Always increment by API page size, not by filtered results count
		return this;
	}
}
function fetchEpisodesPage(id, channelUrl, offset=0, isPlaylist=false) {

	const urlEpisodes = API_GET_PODCAST_EPISODES_URL_TEMPLATE
	.replace("{podcast-id}", id)
	.replace("{offset}", offset);
	const resp = makeGetRequest(urlEpisodes, { throwOnError: false });

	if(!resp) {
		// Fallback to iTunes API for podcast episodes
		// Note: iTunes API doesn't support pagination and has a max limit of 200 episodes
		// Only use for first page as a fallback
		if (offset === 0) {
			log(`Main episodes API failed for podcast ${id}, trying iTunes API fallback`);
			const itunesUrl = API_ITUNES_LOOKUP_EPISODES_URL_TEMPLATE.replace('{id}', id);
			const itunesResp = makeGetRequest(itunesUrl, { throwOnError: false });

			if (itunesResp && itunesResp.results && itunesResp.results.length > 1) {
				// First result is the podcast itself, rest are episodes (max 200)
				const podcastInfo = itunesResp.results[0];
				const episodes = itunesResp.results.slice(1);
				bridge.toast(TOAST_MSG_FALLBACK_GENERIC)
				// Create author from podcast info
				const author = new PlatformAuthorLink(
					new PlatformID(PLATFORM, id, config.id),
					podcastInfo.collectionName || podcastInfo.artistName || '',
					channelUrl,
					podcastInfo.artworkUrl600 || podcastInfo.artworkUrl100 || ""
				);

				return episodes
					.map(episode => itunesEpisodeToPlatformVideo(episode, author))
					.filter(Boolean);
			}
		}
		return [];
	}

	const channel = source.getChannel(channelUrl); 	// cached request
	const author = new PlatformAuthorLink(
		new PlatformID(PLATFORM, id, config.id),
		channel.name,
		channel.url,
		channel.thumbnail
	);

	return resp.data
	.map(x => podcastToPlatformVideo(x, author, isPlaylist))
	.filter(Boolean)
}

//Video
source.isContentDetailsUrl = function(url) {
	return REGEX_CONTENT_URL.test(url);
};

source.getContentDetails = function(url) {

	const episodeId = extractEpisodeId(url);

	if(!episodeId) {
		throw new ScriptException(`Failed to extract episode id from url ${url}`);
	}

	const episodeApiUrl = API_GET_EPISODE_DETAILS_URL_TEMPLATE
	.replace("{episode-id}", episodeId);

	const responseData = makeGetRequest(episodeApiUrl, { useAuth: false, throwOnError: false });

	if(!responseData)
	{
		// Check if we have cached episode details from iTunes API fallback (from episode listings)
		const cachedEpisode = state.episodeDetails[episodeId];
		if (cachedEpisode && cachedEpisode.episode.episodeUrl) {
			log(`Using cached episode details from iTunes API for episode ${episodeId}`);
			bridge.toast(TOAST_MSG_FALLBACK_GENERIC)

			const episode = cachedEpisode.episode;
			const author = cachedEpisode.author;

			// Filter explicit content
			if (episode.contentAdvisoryRating === CONTENT_RATING_EXPLICIT && !_settings.allowExplicit) {
				throw new UnavailableException("Explicit videos can be allowed using the plugin settings");
			}

			const uploadDate = episode.releaseDate
				? parseInt(new Date(episode.releaseDate).getTime() / MS_PER_SECOND)
				: parseInt(Date.now() / MS_PER_SECOND);

			const duration = episode.trackTimeMillis
				? parseInt(episode.trackTimeMillis / MS_PER_SECOND)
				: 0;

			let description = episode.description || '';

			// Try to get podcast info for description
			const podcastId = episode.collectionId.toString();
			const podcastUrl = buildPodcastUrl(podcastId, 'us');
			let show;
			try {
				show = source.getChannel(podcastUrl);
				if (show) {
					description += '<h1>Podcast Information</h1>';
					description += show.description;
				}
			} catch (e) {
				log("Could not get podcast channel details for episode, using basic info");
			}

			// Determine media kind from file extension
			const episodeContentType = episode.episodeContentType || MEDIA_KIND_AUDIO;
			const mediaKind = episodeContentType === MEDIA_KIND_VIDEO ? MEDIA_KIND_VIDEO : MEDIA_KIND_AUDIO;

			// Create a compatible episodeData structure for getVideoSource
			const episodeData = {
				id: episodeId,
				attributes: {
					name: episode.trackName || '',
					description: { standard: episode.description || '' },
					artwork: { url: (episode.artworkUrl600 || episode.artworkUrl100 || '').replace('600x600bb', '{w}x{h}bb').replace('100x100bb', '{w}x{h}bb') },
					assetUrl: episode.episodeUrl,
					mediaKind: mediaKind,
					url: episode.trackViewUrl || url,
					releaseDateTime: episode.releaseDate,
					durationInMilliseconds: episode.trackTimeMillis || 0
				}
			};

			const result = new PlatformVideoDetails({
				id: new PlatformID(PLATFORM, episodeId, config?.id),
				name: episode.trackName || '',
				thumbnails: new Thumbnails([new Thumbnail(episode.artworkUrl600 || episode.artworkUrl160 || episode.artworkUrl60 || "", 0)]),
				author: author,
				uploadDate: uploadDate,
				duration: duration,
				viewCount: -1,
				url: episode.trackViewUrl || url,
				isLive: false,
				description: description,
				video: getVideoSource(episodeData)
			});

			// Add content recommendations if we have podcast info
			if (show) {
				result.getContentRecommendations = function () {
					const pager = source.getChannelContents(show.url, null, null, null, true);
					pager.results = pager.results.filter(x => x.datetime != result.datetime);
					return pager;
				};
			}

			return result;
		}

		// No cached data available and main API failed
		log(`Main episode details API failed for episode ${episodeId} and no cached data available`);
		throw new ScriptException("Failed to get content details");
	}

	const episodeData = responseData.data.find(x => x.type == "podcast-episodes");

	if(!episodeData?.attributes?.assetUrl) {
		throw new UnavailableException("This episode is not available yet");
	}

	if(episodeData.attributes.contentRating == CONTENT_RATING_EXPLICIT_LOWER && !_settings["allowExplicit"]) {
		throw new UnavailableException("Explicit videos can be allowed using the plugin settings");
	}

	const podcastData = episodeData.relationships.podcast.data.find(r => r.type == 'podcasts');

	let description = episodeData.attributes.description?.standard ?? '';

	description += '<h1>Podcast Information</h1>';
	const show = source.getChannel(podcastData.attributes.url);
	description += show.description;

	const result = new PlatformVideoDetails({
		id: new PlatformID(PLATFORM, episodeData.id, config?.id),
		name: episodeData.attributes.name,
		thumbnails: new Thumbnails([new Thumbnail(getArtworkUrl(episodeData.attributes.artwork.url), 0)]),
		author: new PlatformAuthorLink(
			new PlatformID(PLATFORM, podcastData.id, config.id), 
			podcastData.attributes.name, 
			show.url, 
			getArtworkUrl(podcastData.attributes.artwork.url)
		),
		uploadDate: parseInt(new Date(episodeData.attributes.releaseDateTime).getTime() / MS_PER_SECOND),
		duration: parseInt(episodeData.attributes.durationInMilliseconds / MS_PER_SECOND),
		viewCount: -1,
		url: episodeData.attributes.url,
		isLive: false,
		description: description,
		video: getVideoSource(episodeData)
	});

	result.getContentRecommendations = function () {

		const contentRecommendationOptionIndex = _settings["contentRecommendationOptionIndex"];

		let noPublisher = false;

		// Content from channel publisher (if any)
		if (contentRecommendationOptionIndex == 0) {
			const channel = episodeData?.relationships?.channel?.data?.[0];

			if (!channel?.attributes?.url) {
				noPublisher = true;
			} else {
				const pager = source.getChannelContents(channel.attributes.url, null, null, null, true);
				pager.results = pager.results.filter(x => x.datetime != result.datetime);
				return pager;
			}
		}
		// Content from podcast channel
		if (contentRecommendationOptionIndex == 1 || noPublisher) {
			const pager = source.getChannelContents(podcastData.attributes.url, null, null, null, true);
			pager.results = pager.results.filter(x => x.datetime != result.datetime);
			return pager;
		}

	};

	if(IS_TESTING) {
		result.getContentRecommendations();
	}

	return result;
};

source.saveState = () => {
	return JSON.stringify(state);
};

source.getUserSubscriptions = () => {
	
	if (!bridge.isLoggedIn()) {
	  log('Failed to retrieve subscriptions page because not logged in.');
	  throw new ScriptException('Not logged in');
	}

	let next = API_GET_SUBSCRIPTIONS_FIRST_PAGE_PATH;
	let hasMore = false;
	const subscriptionUrlList = [];

	do {

		const podcasts = makeGetRequest(`${PLATFORM_BASE_URL_API}${next}`, { 
			useAuth: true,
			throwOnError: false 
		});
		
		if (!podcasts)
			return [];

		podcasts.data.forEach(podcast => {
			const podcastId = extractPodcastId(podcast.attributes.url);
			const subscriptionUrl = buildPodcastUrl(podcastId, 'us');
			subscriptionUrlList.push(subscriptionUrl);
		});

		hasMore = !!podcasts.next;
		next = podcasts.next;

	} while(hasMore);

	return subscriptionUrlList;
}

source.isPlaylistUrl = function(url) {
    // Return true for saved episodes or podcast URLs
    return url == SAVED_EPISODES_KEY || REGEX_CHANNEL_URL.test(url);
}

source.getUserPlaylists = function () {
	// currently only playlists are saved episodes
	return [SAVED_EPISODES_KEY];
}

source.getPlaylist = function (url) {
	// Check if it's a podcast URL
	if (REGEX_CHANNEL_URL.test(url)) {
		const id = removeRemainingQuery(url.match(REGEX_CHANNEL_URL)[2]);

		// Get the podcast metadata
		const channel = source.getChannel(url);

		const isPlaylist = true;

		const episodesPager = source.getChannelContents(url, null, null, null, isPlaylist);

		return new PlatformPlaylistDetails({
			url: url,
			id: new PlatformID(PLATFORM, id, config.id),
			author: new PlatformAuthorLink(
				new PlatformID(PLATFORM, id, config.id),
				channel.name,
				channel.url,
				channel.thumbnail
			),
			name: channel.name,
			thumbnail: channel.thumbnail,
			// videoCount: episodes.length,
			contents: episodesPager,
		});
	}

	// Handle saved episodes
	if (url == SAVED_EPISODES_KEY) {

		if (!bridge.isLoggedIn()) {
			log('Failed to retrieve saved episodes because not logged in.');
			throw new ScriptException('Not logged in');
		}

		let next = API_GET_SAVED_EPISODES_FIRST_PAGE_PATH;
		let hasMore = false;
		const playlistItems = [];

		do {

			const podcasts = makeGetRequest(`${PLATFORM_BASE_URL_API}${next}`, { 
				useAuth: true,
				throwOnError: false 
			});
			
			if (!podcasts)
				return [];
			podcasts.data.forEach(podcast => {
				playlistItems.push(podcast);
			});

			hasMore = !!podcasts.next;
			next = podcasts.next;

		} while (hasMore);
		const all = playlistItems
			.map(x => podcastToPlatformVideo(x, null, true))
			.filter(Boolean)
			.sort((a, b) => b.datetime - a.datetime);
		const thumbnailUrl = all.length ? (all?.[0]?.thumbnails?.sources?.[0].url ?? '') : '';

		return new PlatformPlaylistDetails({
			url: PLATFORM_SAVED_EPISODES_URL,
			id: new PlatformID(PLATFORM, 'playlistid', config.id),
			author: new PlatformAuthorLink(
				new PlatformID(PLATFORM, '', config.id),
				'',// author name
				'',// author url
			),
			name: 'Saved Episodes',// playlist name
			thumbnail: thumbnailUrl,
			videoCount: all.length,
			contents: new VideoPager(all),
		});

	}

	throw new ScriptException('Invalid playlist url');
}


// Helper functions

/**
 * Create a PlatformID for this plugin
 * @param {string} id - The platform-specific ID
 * @returns {PlatformID} Platform ID object
 */
function createPlatformID(id) {
	return new PlatformID(PLATFORM, id, config?.id);
}

/**
 * Create a PlatformAuthorLink
 * @param {string} authorId - The author/podcast ID
 * @param {string} authorName - The author/podcast name
 * @param {string} authorUrl - The author/podcast URL
 * @param {string} thumbnail - The thumbnail URL
 * @returns {PlatformAuthorLink} Platform author link object
 */
function createPlatformAuthor(authorId, authorName, authorUrl, thumbnail) {
	return new PlatformAuthorLink(
		createPlatformID(authorId),
		authorName,
		authorUrl,
		thumbnail
	);
}

/**
 * Extract best quality artwork URL from iTunes API response
 * @param {Object} data - iTunes API response object
 * @returns {string} Best quality artwork URL
 */
function getBestArtworkUrl(data) {
	return data.artworkUrl600 || data.artworkUrl160 || data.artworkUrl100 || data.artworkUrl60 || "";
}

/**
 * Convert date string or Date object to Unix timestamp
 * @param {string|Date} date - Date string or Date object
 * @returns {number} Unix timestamp in seconds
 */
function toUnixTimestamp(date) {
	if (!date) return parseInt(Date.now() / MS_PER_SECOND);
	return parseInt(new Date(date).getTime() / MS_PER_SECOND);
}

/**
 * Convert milliseconds to seconds
 * @param {number} millis - Duration in milliseconds
 * @returns {number} Duration in seconds
 */
function millisToSeconds(millis) {
	return millis ? parseInt(millis / MS_PER_SECOND) : 0;
}

/**
 * Build a podcast URL with the given ID
 * @param {string} podcastId - The podcast ID
 * @param {string} country - Country code (default: 'us')
 * @returns {string} Podcast URL
 */
function buildPodcastUrl(podcastId, country = DEFAULT_COUNTRY_CODE) {
	return URL_PODCAST_TEMPLATE
		.replace('{country}', country)
		.replace('{podcast-id}', podcastId);
}

/**
 * Build a podcast URL with ID prefix (e.g., id123456)
 * @param {string} podcastId - The podcast ID
 * @param {string} country - Country code (default: 'us')
 * @returns {string} Podcast URL with id prefix
 */
function buildPodcastUrlWithIdPrefix(podcastId, country = DEFAULT_COUNTRY_CODE) {
	return URL_PODCAST_WITH_ID_PREFIX_TEMPLATE
		.replace('{country}', country)
		.replace('{podcast-id}', podcastId);
}

/**
 * Build a podcast URL without country code
 * @param {string} podcastId - The podcast ID
 * @returns {string} Podcast URL without country
 */
function buildPodcastUrlNoCountry(podcastId) {
	return URL_PODCAST_NO_COUNTRY_TEMPLATE.replace('{podcast-id}', podcastId);
}

/**
 * Build a podcast URL without country code, with ID prefix
 * @param {string} podcastId - The podcast ID
 * @returns {string} Podcast URL without country, with id prefix
 */
function buildPodcastUrlNoCountryWithIdPrefix(podcastId) {
	return URL_PODCAST_NO_COUNTRY_WITH_ID_PREFIX_TEMPLATE.replace('{podcast-id}', podcastId);
}

/**
 * Build a channel URL with the given ID
 * @param {string} channelId - The channel ID
 * @param {string} country - Country code (default: 'us')
 * @returns {string} Channel URL
 */
function buildChannelUrl(channelId, country = DEFAULT_COUNTRY_CODE) {
	return URL_CHANNEL_TEMPLATE
		.replace('{country}', country)
		.replace('{channel-id}', channelId);
}

/**
 * Build an episode URL with the given podcast and episode IDs
 * @param {string} podcastId - The podcast ID
 * @param {string} episodeId - The episode ID
 * @param {string} country - Country code (default: 'us')
 * @returns {string} Episode URL
 */
function buildEpisodeUrl(podcastId, episodeId, country = DEFAULT_COUNTRY_CODE) {
	return URL_EPISODE_TEMPLATE
		.replace('{country}', country)
		.replace('{podcast-id}', podcastId)
		.replace('{episode-id}', episodeId);
}

/**
 * Enrich episode description with podcast information
 * @param {string} baseDescription - The base episode description
 * @param {string} podcastId - The podcast ID
 * @returns {string} Enriched description with podcast info appended
 */
function enrichDescriptionWithPodcastInfo(baseDescription, podcastId) {
	let description = baseDescription || '';

	if (!podcastId) {
		return description;
	}

	try {
		const podcastUrl = buildPodcastUrl(podcastId);
		const show = source.getChannel(podcastUrl);
		if (show) {
			description += '<h1>Podcast Information</h1>';
			description += show.description;
		}
	} catch (e) {
		log("Could not get podcast channel details for episode enrichment: " + e.message);
	}

	return description;
}

/**
 * Get the user's selected country code from settings
 * @returns {string} The country code (e.g., 'us', 'gb', 'ca')
 */
function getUserCountry() {
	return COUNTRY_CODES[_settings.countryIndex] ?? DEFAULT_COUNTRY_CODE;
}

/**
 * Generate all URL alternatives for a podcast ID
 * This is used to ensure subscription consistency across different URL formats
 * @param {string} podcastId - The podcast ID
 * @param {string} baseUrl - The base URL (optional, for additional alternatives)
 * @returns {string[]} Array of URL alternatives
 */
function generatePodcastUrlAlternatives(podcastId, baseUrl = null) {
	const uniqueUrlAlternatives = new Set([
		URL_CHANNEL + podcastId,
		buildPodcastUrlNoCountryWithIdPrefix(podcastId),
		buildPodcastUrlNoCountry(podcastId),
		buildPodcastUrlWithIdPrefix(podcastId, DEFAULT_COUNTRY_CODE),
		buildPodcastUrl(podcastId, DEFAULT_COUNTRY_CODE),
	]);

	// Add the base URL if provided
	if (baseUrl) {
		uniqueUrlAlternatives.add(baseUrl);
		uniqueUrlAlternatives.add(removeQueryParams(baseUrl));
	}

	// Add all supported regionalized URLs
	COUNTRY_CODES.forEach(countryCode => {
		uniqueUrlAlternatives.add(buildPodcastUrlWithIdPrefix(podcastId, countryCode));
		uniqueUrlAlternatives.add(buildPodcastUrl(podcastId, countryCode));
	});

	return Array.from(uniqueUrlAlternatives);
}

/**
 * Generates a video or audio source descriptor based on the provided episode data.
 * 
 * @param {Object} episodeData - The data object containing episode attributes.
 * @param {Object} episodeData.attributes - The attributes of the episode.
 * @param {string} episodeData.attributes.mediaKind - Type of media, either "audio" or "video".
 * @param {string} episodeData.attributes.assetUrl - The URL of the media asset.
 * @param {number} episodeData.attributes.durationInMilliseconds - The duration of the audio in milliseconds.
 * 
 * @returns {(UnMuxVideoSourceDescriptor|VideoSourceDescriptor)} - A descriptor for audio or video sources.
 * 
 * @throws {ScriptException} Throws an error if the media kind is not supported.
 * 
 * @example
 * const episodeData = {
 *   attributes: {
 *     mediaKind: "audio",
 *     assetUrl: "https://example.com/audio.mp3",
 *     durationInMilliseconds: 300000
 *   }
 * };
 * const source = getVideoSource(episodeData);
 * // Returns an UnMuxVideoSourceDescriptor for audio or a VideoSourceDescriptor for video
 */
function getVideoSource(episodeData) {
	if (!episodeData?.attributes?.mediaKind) {
		throw new ScriptException("Media kind not found");
	}
	
	const duration = episodeData.attributes.durationInMilliseconds 
		? parseInt(episodeData.attributes.durationInMilliseconds / MS_PER_SECOND)
		: 0;

	const sourceDef = {
		url: episodeData.attributes.assetUrl,
		duration: duration,
		requestModifier: {
			options: {
				applyAuthClient: "",
				applyCookieClient: "",
				applyOtherHeaders: false,
				impersonateTarget: IMPERSONATION_TARGET
			}
		}
	};
		
	switch(episodeData.attributes.mediaKind) {
		case MEDIA_KIND_AUDIO:
			return new UnMuxVideoSourceDescriptor([], [
				new AudioUrlSource({
					name: "audio/mp3",
					container: "audio/mp3",
					bitrate: 0,
					...sourceDef
				})
			]);
		case MEDIA_KIND_VIDEO:
			return new VideoSourceDescriptor([
				new VideoUrlSource({
					name: "video/mp4",
					container: "video/mp4",
					...sourceDef
				})
			]);
		default:
			throw new ScriptException(`Unsupported media kind: "${episodeData.attributes.mediaKind}" for url: ${episodeData.attributes.assetUrl}`);
	}	
}


/**
 * Prepare the artwork URL by replacing the placeholders with the actual values
 * @param {string} url
 * @returns {string}
 */
function getArtworkUrl(url) {
	return url
	.replace("{w}", "500")
	.replace("{h}", "500")
	.replace("{f}", "png");
}

/**
 * Match the first group of the regex and return the default value if not found
 * @param {string} data
 * @param {any} regex
 * @param {string} def
 * @returns {string}
 */
function matchFirstOrDefault(data, regex, def) {
	const match = data.match(regex);
	if(match && match.length > 0)
		return match[1];
	return def;
}

/**
 * Remove the remaining query from the URL
 * @param {string} query
 * @returns {string}
 */
function removeRemainingQuery(query) {
	const indexSlash = query.indexOf("/");
	if(indexSlash >= 0)
		return query.substring(0, indexSlash);
	const indexQuestion = query.indexOf("?");
	if(indexQuestion >= 0)
		return query.substring(0, indexQuestion);
	const indexAnd = query.indexOf("&");
	if(indexAnd >= 0)
		return query.substring(0, indexAnd);
	return query;
}
/**
 * Remove the query from the URL
 * @param {string} query
 * @returns {string}
 */
function removeQueryParams(query) {
	const indexQuestion = query.indexOf("?");
	if(indexQuestion >= 0)
		return query.substring(0, indexQuestion);
	return query;
}

/**
 * Extract the main script file name from the HTML content
 * @param {string} htmlContent
 * @returns {string}
 */
function extractScriptFileName(htmlContent) {
    // Define the regex pattern to match 'index-*.js'
    const match = htmlContent.match(REGEX_MAIN_SCRIPT_FILENAME);
    
    // Return the matched file name if found, otherwise return null
    return match ? match[0] : null;
}

/**
 * Extract the JWT token from the main script
 * Looks for a string that starts with 'eyJhbGci' representing the encoded header
 * @param {string} scriptContent
 * @returns {string}
 */
function extractJWT(scriptContent) {
    // Use the match method to find the JWT token in the script content
    const match = scriptContent.match(REGEX_JWT);
    
    // Return the matched JWT if found, otherwise return null
    return match ? match[0] : null;
}

/**
 * Extract the episode ID from the URL
 * @param {string} url
 * @returns {string}
 */
function extractEpisodeId(url) {
    const match = url.match(REGEX_EPISODE_ID);
    return match ? match[1] : null;
}

/**
 * Extract the podcast ID from the URL
 * @param {string} url
 * @returns {string}
 */
function extractPodcastId(url) {
    // Regular expression to match the podcast ID in the URL
    const regex = /\/id(\d+)/;
    
    // Match the URL against the regex
    const match = url.match(regex);
    
    // If a match is found, return the podcast ID (without the 'id' prefix)
    if (match) {
        return match[1];
    }
    
    // If no match is found, return null
    return null;
}

/**
 * Returs the options values for a setting. If the setting is not found, an empty array is returned.
 * @param {string} settingKey
 * @returns {string[]}
 */
function loadOptionsForSetting(settingKey) {
	return config?.settings?.find((s) => s.variable == settingKey)
	  ?.options ?? [];
}



function podcastToPlatformVideo(x, author, isPlaylistParent = false) {
	const podcast = x.relationships?.podcast?.data?.find(p => p.type == 'podcasts');
	const podcastAttributes = podcast?.attributes;

	let durationInMilliseconds = x.attributes.durationInMilliseconds;

	let isSubscriberOnly = false;

	// Only mark as subscriber-only if there's no asset URL (unplayable) and subscription offers exist
	// Episodes without duration but with asset URL are regular episodes with missing metadata
	if (!x.attributes.assetUrl) {
		isSubscriberOnly = (x?.attributes?.offers ?? []).some(e => e.kind == OFFER_KIND_SUBSCRIBE);
	}

	let duration = durationInMilliseconds ? durationInMilliseconds / MS_PER_SECOND : 0;

	if (!author) {
		const podcastUrl = buildPodcastUrl(podcast.id);
		author = createPlatformAuthor(
			podcast.id,
			podcastAttributes?.name,
			podcastUrl,
			getArtworkUrl(podcastAttributes.artwork.url) ?? ""
		);
	}

	const id = new PlatformID(PLATFORM, x.id + "", config?.id);
	const name = x.attributes.itunesTitle ?? x.attributes.name ?? '';
	const uploadDate = parseInt(new Date(x.attributes.releaseDateTime).getTime() / MS_PER_SECOND);

	if (isSubscriberOnly) {

		if(_settings.hideSubscriberOnly || isPlaylistParent) {
			return null;
		}

		return new PlatformLockedContent({
			id,
			name,
			author,
			datetime: uploadDate,
			lockDescription: 'Subscriber only content',
			unlockUrl: URL_APPLE_SUPPORT_SUBSCRIBER_CONTENT,
		});
	}

	// If we have the asset URL (audio/video file), return PlatformVideoDetails for immediate playback
	if (x.attributes.assetUrl) {
		// Enrich description with podcast information
		const description = enrichDescriptionWithPodcastInfo(
			x.attributes.description?.standard || '',
			podcast?.id
		);

		return new PlatformVideoDetails({
			id,
			name,
			thumbnails: new Thumbnails([new Thumbnail(getArtworkUrl(x.attributes.artwork.url), 0)]),
			author,
			uploadDate,
			duration: duration,
			viewCount: -1,
			url: x.attributes.url,
			isLive: false,
			description: description,
			video: getVideoSource(x)
		});
	}

	// No asset URL - return PlatformVideo (will require getContentDetails)
	return new PlatformVideo({
		id,
		name,
		thumbnails: new Thumbnails([new Thumbnail(getArtworkUrl(x.attributes.artwork.url), 0)]),
		author,
		uploadDate,
		duration: duration,
		viewCount: -1,
		url: x.attributes.url,
		isLive: false
	})
}

function itunesPodcastToPlatformVideo(x) {
	// iTunes API returns podcast information, not individual episodes
	// We'll create a "video" entry that represents the podcast itself
	try {
		const podcastId = x.collectionId?.toString() || x.trackId?.toString();
		if (!podcastId) return null;

		const podcastUrl = buildPodcastUrl(podcastId);
		const author = createPlatformAuthor(
			podcastId,
			x.artistName || x.collectionName || '',
			podcastUrl,
			getBestArtworkUrl(x)
		);

		return new PlatformVideo({
			id: createPlatformID(podcastId),
			name: (x.collectionName || x.trackName || '') + " (Podcast)",
			description: x.description || '',
			thumbnails: new Thumbnails([new Thumbnail(getBestArtworkUrl(x), 0)]),
			author,
			uploadDate: toUnixTimestamp(x.releaseDate),
			duration: -1, // trackTimeMillis for podcasts represents preview/intro duration, not episode length
			viewCount: -1,
			url: podcastUrl,
			isLive: false
		});
	} catch (e) {
		log("Error converting iTunes podcast to platform video: " + e.message);
		return null;
	}
}

function itunesPodcastToPlatformPlaylist(x) {
	// Convert iTunes podcast to PlatformPlaylist for use in getHome fallback
	try {
		const podcastId = x.collectionId?.toString() || x.trackId?.toString();
		if (!podcastId) return null;

		const podcastUrl = buildPodcastUrl(podcastId);
		const author = createPlatformAuthor(
			podcastId,
			x.artistName || x.collectionName || '',
			podcastUrl,
			getBestArtworkUrl(x)
		);

		return new PlatformPlaylist({
			id: createPlatformID(podcastId),
			name: x.collectionName || x.trackName || '',
			author,
			thumbnail: getBestArtworkUrl(x),
			videoCount: x.trackCount || -1,
			url: podcastUrl
		});
	} catch (e) {
		log("Error converting iTunes podcast to platform playlist: " + e.message);
		return null;
	}
}

function v2PodcastToPlatformPlaylist(podcast) {
	// Convert v2 API podcast object to PlatformPlaylist
	try {
		const podcastId = podcast.id;
		if (!podcastId) return null;

		const author = createPlatformAuthor(
			podcastId,
			podcast.artistName || podcast.name || '',
			podcast.url,
			podcast.artworkUrl100 || ""
		);

		return new PlatformPlaylist({
			id: createPlatformID(podcastId),
			name: podcast.name || '',
			author: author,
			thumbnail: podcast.artworkUrl100 || "",
			videoCount: -1,  // Not provided by v2 API
			url: podcast.url
		});
	} catch (e) {
		log("Error converting v2 podcast to playlist: " + e.message);
		return null;
	}
}

function v2EpisodeToPlatformVideo(episode) {
	// Convert v2 API episode object to PlatformVideo
	try {
		if (!episode.id || episode.kind !== 'podcast-episodes') {
			return null;
		}

		const episodeId = episode.id;
		const episodeUrl = episode.url;

		// Extract podcast ID from episode URL
		const urlMatch = episodeUrl.match(REGEX_CONTENT_URL);
		if (!urlMatch) {
			log(`v2 episode URL doesn't match regex: ${episodeUrl}`);
			return null;
		}

		const podcastId = urlMatch[1];

		// Create author from episode data
		const author = createPlatformAuthor(
			podcastId,
			episode.artistName || '',
			buildPodcastUrl(podcastId),
			episode.artworkUrl100 || ""
		);

		return new PlatformVideo({
			id: createPlatformID(episodeId),
			name: episode.name || '',
			thumbnails: new Thumbnails([new Thumbnail(episode.artworkUrl100 || "", 0)]),
			author: author,
			uploadDate: toUnixTimestamp(null), // v2 doesn't provide release date
			duration: 0,  // v2 API doesn't provide duration for episode listings
			viewCount: -1,
			url: episodeUrl,
			isLive: false
		});
	} catch (e) {
		log("Error converting v2 episode to platform video: " + e.message);
		return null;
	}
}

function itunesEpisodeToPlatformVideo(episode, author) {
	// Convert iTunes episode data to PlatformVideo or PlatformVideoDetails
	try {
		if (!episode.trackId || episode.wrapperType !== 'podcastEpisode') {
			return null;
		}

		const episodeId = episode.trackId.toString();
		// iTunes API always provides trackViewUrl in the correct format, but provide a fallback just in case
		const fallbackUrl = episode.collectionId
			? buildEpisodeUrl(episode.collectionId.toString(), episodeId)
			: buildEpisodeUrl(episodeId, episodeId);
		const episodeUrl = episode.trackViewUrl || fallbackUrl;

		// Filter out explicit content if needed
		if (episode.contentAdvisoryRating === CONTENT_RATING_EXPLICIT && !_settings.allowExplicit) {
			return null;
		}

		const uploadDate = toUnixTimestamp(episode.releaseDate);
		const duration = millisToSeconds(episode.trackTimeMillis);

		// If we have the audio file (episodeUrl), cache it and return PlatformVideoDetails so it's immediately playable
		if (episode.episodeUrl) {
			// Cache the episode for potential use in getContentDetails fallback
			state.episodeDetails[episodeId] = {
				episode: episode,
				author: author
			};

			// Determine media kind from file extension
			const episodeContentType = episode.episodeContentType || MEDIA_KIND_AUDIO;
			const mediaKind = episodeContentType === MEDIA_KIND_VIDEO ? MEDIA_KIND_VIDEO : MEDIA_KIND_AUDIO;

			// Create a compatible episodeData structure for getVideoSource
			const episodeData = {
				id: episodeId,
				attributes: {
					name: episode.trackName || '',
					description: { standard: episode.description || '' },
					artwork: { url: (episode.artworkUrl600 || episode.artworkUrl100 || '').replace('600x600bb', '{w}x{h}bb').replace('100x100bb', '{w}x{h}bb') },
					assetUrl: episode.episodeUrl,
					mediaKind: mediaKind,
					url: episodeUrl,
					releaseDateTime: episode.releaseDate,
					durationInMilliseconds: episode.trackTimeMillis || 0
				}
			};

			// Enrich description with podcast information
			const description = enrichDescriptionWithPodcastInfo(
				episode.description || '',
				episode.collectionId?.toString()
			);

			return new PlatformVideoDetails({
				id: createPlatformID(episodeId),
				name: episode.trackName || '',
				thumbnails: new Thumbnails([new Thumbnail(getBestArtworkUrl(episode), 0)]),
				author: author,
				uploadDate: uploadDate,
				duration: duration,
				viewCount: -1,
				url: episodeUrl,
				isLive: false,
				description: description,
				video: getVideoSource(episodeData)
			});
		}

		// If no audio file URL, return PlatformVideo (will require getContentDetails to play)
		return new PlatformVideo({
			id: createPlatformID(episodeId),
			name: episode.trackName || '',
			description: episode.description || '',
			thumbnails: new Thumbnails([new Thumbnail(getBestArtworkUrl(episode), 0)]),
			author: author,
			uploadDate: uploadDate,
			duration: duration,
			viewCount: -1,
			url: episodeUrl,
			isLive: false
		});
	} catch (e) {
		log("Error converting iTunes episode to platform video: " + e.message);
		return null;
	}
}

class PublisherChannelPlaylistsPager extends PlaylistPager {
    constructor(url, offset = 0) {
        const result = PublisherChannelPlaylistsPager.fetchChannelPlaylists(url, offset);
        super(result.playlists, result.hasMore);
        this.url = url;
        this.offset = offset + PUBLISHER_CHANNEL_PAGE_SIZE;
    }

    static fetchChannelPlaylists(url, offset) {
        const match = url.match(REGEX_PUBLISHER_CHANNEL_URL);
        if (!match) {
            return { playlists: [], hasMore: false };
        }

        const channelId = match[3];

        const apiUrl = API_GET_PUBLISHER_CHANNEL_PODCASTS_URL_TEMPLATE
            .replace('{channel-id}', channelId)
            .replace('{offset}', offset);

        const result = makeGetRequest(apiUrl, { throwOnError: false });
        if (!result) {
            // Fallback to iTunes API for publisher channel podcasts
            if (offset === 0) {
                log(`Main publisher channel podcasts API failed for channel ${channelId}, trying iTunes API fallback`);
                const itunesUrl = API_ITUNES_LOOKUP_URL_TEMPLATE.replace('{id}', channelId);
                const itunesResp = makeGetRequest(itunesUrl, { throwOnError: false });

                if (itunesResp && itunesResp.results && itunesResp.results.length > 0) {
                    // First result is the channel/podcast itself
                    const channelInfo = itunesResp.results[0];
					bridge.toast(TOAST_MSG_FALLBACK_GENERIC)
                    // Create a single playlist for this podcast
                    const playlist = new PlatformPlaylist({
                        id: new PlatformID(PLATFORM, channelId, config.id),
                        name: channelInfo.collectionName || channelInfo.trackName || '',
                        author: new PlatformAuthorLink(
                            new PlatformID(PLATFORM, channelId, config.id),
                            channelInfo.artistName || channelInfo.collectionName || '',
                            buildChannelUrl(channelId, 'us'),
                            channelInfo.artworkUrl600 || channelInfo.artworkUrl100 || ""
                        ),
                        thumbnail: channelInfo.artworkUrl600 || channelInfo.artworkUrl100 || "",
                        videoCount: channelInfo.trackCount || -1,
                        url: buildPodcastUrl(channelId, 'us')
                    });

                    return { playlists: [playlist], hasMore: false };
                }
            }
            return { playlists: [], hasMore: false };
        }

        const podcasts = result.data || [];

        // Convert each podcast to a PlatformPlaylist object
        const playlists = podcasts.map(podcast => {
            const attributes = podcast.attributes;

            return new PlatformPlaylist({
                id: new PlatformID(PLATFORM, podcast.id, config.id),
                name: attributes.name,
                author: new PlatformAuthorLink(
                    new PlatformID(PLATFORM, podcast.id, config.id),
                    attributes.name,  // Use podcast name as the author name
                    attributes.url,
                    getArtworkUrl(attributes.artwork.url)
                ),
                thumbnail: getArtworkUrl(attributes.artwork.url),
                videoCount: attributes.trackCount || -1,
                url: attributes.url
            });
        });

        return { playlists, hasMore: result.next !== undefined };
    }

    nextPage() {
        const result = PublisherChannelPlaylistsPager.fetchChannelPlaylists(this.url, this.offset);
        this.results = result.playlists;
        this.hasMore = result.hasMore;
        this.offset += PUBLISHER_CHANNEL_PAGE_SIZE;
        return this;
    }
}

class PodcastEpisodesPlaylistPager extends PlaylistPager {
    constructor(url, offset = 0) {
        const result = PodcastEpisodesPlaylistPager.fetchPodcastPlaylist(url, offset);
        super(result.playlists, result.hasMore);
        this.url = url;
        this.offset = offset + PUBLISHER_CHANNEL_PAGE_SIZE;
        this.id = result.id;
        this.podcastData = result.podcastData;
    }

    static fetchPodcastPlaylist(url, offset = 0) {
        const match = url.match(REGEX_CHANNEL_URL);
        if (!match) {
            return { playlists: [], hasMore: false };
        }

        const podcastId = match[2];  // match[1] is domain, match[2] is podcast ID
        
        // First, get the podcast metadata
        let podcastData = null;
        if (state.channel[podcastId]) {
            podcastData = state.channel[podcastId];
        } else {
            // If not in cache, fetch the podcast data
            podcastData = source.getChannel(url);
        }
        
        // Create a single playlist from this podcast
        const playlist = new PlatformPlaylist({
            id: new PlatformID(PLATFORM, podcastId, config.id),
            name: podcastData.name,
            author: new PlatformAuthorLink(
                new PlatformID(PLATFORM, podcastId, config.id),
                podcastData.name,
                podcastData.url,
                podcastData.thumbnail
            ),
            thumbnail: podcastData.thumbnail,
            videoCount: -1, // Unknown count
            url: url
        });
        
        return { 
            playlists: [playlist], 
            hasMore: false,   // No pagination for podcast itself
            id: podcastId,
            podcastData: podcastData
        };
    }

    nextPage() {
        // We only return a single playlist for a podcast, so no more pages
        this.hasMore = false;
        return this;
    }
}

class ApplePublisherChannelEpisodesPager extends ContentPager {
    constructor(url) {
        const match = url.match(REGEX_PUBLISHER_CHANNEL_URL);
        if (!match) {
            super([], false);
            return;
        }

        const channelId = match[3];

        super(fetchPublisherChannelEpisodesPage(channelId, 0), true);
        this.channelId = channelId;
        this.offset = PUBLISHER_CHANNEL_PAGE_SIZE; // Start next page offset
    }

    nextPage() {
        this.results = fetchPublisherChannelEpisodesPage(this.channelId, this.offset);
        this.hasMore = this.results.length > 0;
        this.offset += PUBLISHER_CHANNEL_PAGE_SIZE;
        return this;
    }
}

function fetchPublisherChannelEpisodesPage(channelId, offset=0) {
    const apiUrl = API_GET_PUBLISHER_CHANNEL_EPISODES_URL_TEMPLATE
        .replace('{channel-id}', channelId)
        .replace('{offset}', offset);

    const episodesData = makeGetRequest(apiUrl, { throwOnError: false });
    if (!episodesData) {
        // Fallback to iTunes API for podcast episodes
        // Note: iTunes API doesn't support pagination or publisher channels directly, and has max limit of 200 episodes
        // We'll try to get basic podcast info and episodes if available
        if (offset === 0) {
            log(`Main publisher channel episodes API failed for channel ${channelId}, trying iTunes API fallback`);
            const itunesUrl = API_ITUNES_LOOKUP_EPISODES_URL_TEMPLATE.replace('{id}', channelId);
            const itunesResp = makeGetRequest(itunesUrl, { throwOnError: false });

            if (itunesResp && itunesResp.results && itunesResp.results.length > 1) {
                // First result is the podcast/channel itself, rest are episodes (max 200)
                const channelInfo = itunesResp.results[0];
                const episodes = itunesResp.results.slice(1);
				bridge.toast(TOAST_MSG_FALLBACK_GENERIC)
                // Create author from channel info
                const author = new PlatformAuthorLink(
                    new PlatformID(PLATFORM, channelId, config.id),
                    channelInfo.collectionName || channelInfo.artistName || '',
                    buildChannelUrl(channelId, 'us'),
                    channelInfo.artworkUrl600 || channelInfo.artworkUrl100 || ""
                );

                return episodes
                    .map(episode => itunesEpisodeToPlatformVideo(episode, author))
                    .filter(Boolean);
            }
        }
        return [];
    }
    return (episodesData.data || [])
        .map(episode => podcastToPlatformVideo(episode))
        .filter(Boolean);
}

/**
 * Makes an API request to the specified URL with automatic retries and error handling
 * 
 * @param {string} url - The URL to make the request to
 * @param {Object} options - Configuration options
 * @param {boolean} [options.useAuth=false] - Whether to use authentication for the request
 * @param {boolean} [options.parseResponse=true] - Whether to parse the response as JSON
 * @param {number} [options.maxRetries=3] - Maximum number of retry attempts
 * @param {Object} [options.customHeaders={}] - Additional headers to include in the request
 * @param {boolean} [options.throwOnError=true] - Whether to throw an exception on error
 * @returns {Object|string|null} - Parsed JSON object, response body string, or null on error if not throwing
 * @throws {ScriptException} - If the request fails after all retry attempts and throwOnError is true
 */
function makeGetRequest(url, options = {}) {
	const {
		useAuth = false,
		parseResponse = true,
		maxRetries = 3,
		customHeaders = {},
		throwOnError = true
	} = options;

	// Testing blocker - uncomment to test fallback behavior
	// if(url.indexOf("podcasts.apple.com") > -1 ) {
	// 	return null;
	// }

	let remainingAttempts = maxRetries + 1; // +1 for the initial attempt
	let lastError;
	
	while (remainingAttempts > 0) {
		try {
			// Combine default headers from state with any custom headers
			const headers = {
				...state.headers,
				...customHeaders
			};
			
			const resp = http.GET(url, headers, useAuth);
			
			// Handle non-200 responses
			if (!resp.isOk) {
				const errorMsg = `Request failed with status ${resp.code}: ${url}`;
				if (throwOnError) {
					throw new ScriptException(errorMsg);
				} else {
					log(errorMsg);
					return parseResponse ? null : resp.body;
				}
			}
			
			// Parse response if needed
			if (parseResponse) {
				try {
					const json = JSON.parse(resp.body);
					
					// Check for API error responses that might be in a 200 response
					if (json.errors) {
						const errorMsg = `API returned error: ${JSON.stringify(json.errors)}`;
						if (throwOnError) {
							throw new ScriptException(errorMsg);
						} else {
							log(errorMsg);
							return null;
						}
					}
					
					return json;
				} catch (parseError) {
					const errorMsg = `Failed to parse response as JSON: ${parseError.message}`;
					if (throwOnError) {
						throw new ScriptException(errorMsg);
					} else {
						log(errorMsg);
						return null;
					}
				}
			}
			
			return resp.body;
		} catch (error) {
			lastError = error;
			remainingAttempts--;
			
			if (remainingAttempts > 0) {
				// Log retry attempt but continue
				log(`Request to ${url} failed, retrying... (${maxRetries - remainingAttempts + 1}/${maxRetries})`);
			} else {
				// All retry attempts have failed
				log(`Request failed after ${maxRetries + 1} attempts: ${url}`);
				if (throwOnError) {
					throw lastError;
				} else {
					return parseResponse ? null : null;
				}
			}
		}
	}
}

log("LOADED");
