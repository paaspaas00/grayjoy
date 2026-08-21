const URL_CLAIM_SEARCH = "https://api.na-backend.odysee.com/api/v1/proxy?m=claim_search"
const URL_RESOLVE = "https://api.na-backend.odysee.com/api/v1/proxy?m=resolve";
const URL_PREFERENCES = "https://api.na-backend.odysee.com/api/v1/proxy?m=preference_get"
const URL_CONTENT = "https://odysee.com/\$/api/content/v2/get";
const URL_REACTIONS = "https://api.odysee.com/reaction/list";
const URL_VIEW_COUNT = "https://api.odysee.com/file/view_count";
const URL_USER_NEW = "https://api.odysee.com/user/new";
const URL_COMMENTS_LIST = "https://comments.odysee.tv/api/v2?m=comment.List";
const URL_CHANNEL_LIST = "https://api.na-backend.odysee.com/api/v1/proxy?m=channel_list"
const URL_COLLECTION_LIST = "https://api.na-backend.odysee.com/api/v1/proxy?m=collection_list"
const URL_GET = "https://api.na-backend.odysee.com/api/v1/proxy?m=get";
const URL_CHANNEL_SIGN = "https://api.na-backend.odysee.com/api/v1/proxy?m=channel_sign";
const URL_STATUS = "https://api.na-backend.odysee.com/api/v2/status"
const URL_REPORT_PLAYBACK = "https://watchman.na-backend.odysee.com/reports/playback"
const URL_BASE = "https://odysee.com";
const URL_API_SUB_COUNT = 'https://api.odysee.com/subscription/sub_count';
const PLAYLIST_URL_BASE = "https://odysee.com/$/playlist/"
const URL_LIVE_STREAMS = "https://api.odysee.live/livestream/all"
const URL_LIVE_IS_LIVE = "https://api.odysee.live/livestream/is_live"

const CLAIM_TYPE_STREAM = "stream";
const CLAIM_TYPE_REPOST = "repost";
const ORDER_BY_RELEASETIME = "release_time";

const REGEX_DETAILS_URL = /^(https:\/\/(?:odysee\.com|open\.lbry\.com)\/|lbry:\/\/)((@[^\/@]+)(:|#)([a-fA-F0-9]+)\/)?([^\/@]+)(:|#)([a-fA-F0-9]+)(\?|$)/
const REGEX_CHANNEL_URL = /^(https:\/\/(?:odysee\.com|open\.lbry\.com)\/|lbry:\/\/)(@[^\/@]+)(:|#)([a-fA-F0-9]+)(\?|$)/
const REGEX_LBRY_EMBED_URL = /^https:\/\/lbry\.tv\/\$\/embed\/([^\/]+)\/([a-fA-F0-9]+)(\?|$)/
const REGEX_ODYSEE_EMBED_URL = /^https:\/\/odysee\.com\/\$\/embed\/([^\/]+)\/([a-fA-F0-9]+)(\?|$)/
const REGEX_PLAYLIST = /^https:\/\/odysee\.com\/\$\/playlist\/([0-9a-fA-F]+?)$/
const REGEX_COLLECTION = /^https:\/\/odysee\.com\/\$\/playlist\/([0-9a-fA-F-]+?)$/
const REGEX_FAVORITES = /^https:\/\/odysee\.com\/\$\/playlist\/favorites$/
const REGEX_WATCH_LATER = /^https:\/\/odysee\.com\/\$\/playlist\/watchlater$/
const REGEX_TAG_DISCOVER = /^https:\/\/odysee\.com\/\$\/discover\?t=(.+)$/

const CLAIM_ID_LENGTH = 40
// How much of a claim id an odysee.com URL carries, by claim type
const CLAIM_ID_PREFIX_CONTENT = 1
const CLAIM_ID_PREFIX_CHANNEL = 2

// The batch cache only ever holds live metrics (view, subscriber and reaction counts),
// so entries have to expire or those numbers freeze for as long as the app runs.
const BATCH_CACHE_TTL_MS = 5 * 60 * 1000
const BATCH_CACHE_MAX_ENTRIES = 500

// Spacing between attempts to re-mint an anonymous token after the endpoint fails
const AUTH_TOKEN_RETRY_COOLDOWN_MS = 60 * 1000
let lastAuthTokenAttempt = 0

const PLATFORM = "Odysee";
const PLATFORM_CLAIMTYPE = 3;

const EMPTY_AUTHOR = new PlatformAuthorLink(new PlatformID(PLATFORM, "", plugin.config.id), "Anonymous", "","https://plugins.grayjay.app/Odysee/OdyseeIcon.png")

let localState = {
	batch_response_cache: {}
};
let localSettings
let localConfig = {};
let shortContentThresholdOptions = [];

const headersToAdd = {
    'origin': 'https://odysee.com',
    'referer': 'https://odysee.com/',
    'user-agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36'
}

const JSON_HEADERS = { "Content-Type": "application/json" };
const FORM_URLENCODED_HEADERS = { "Content-Type": "application/x-www-form-urlencoded" };

const TEXT_DOC_TYPES = [
    'text/plain',
    'text/markdown',
    'text/html',
    'text/css',
    'text/javascript',
    'text/csv',
    'text/xml',
    'application/json'
];

const IS_ANDROID = bridge.buildPlatform === "android";

//Source Method
source.enable = function (config, settings, savedState) {
	localSettings = settings;
	localConfig = config;

	shortContentThresholdOptions = loadOptionsForSetting('shortContentThresholdIndex');

	const restoredState = parseSavedState(savedState);

	if (!restoredState) {
		if (bridge.isLoggedIn()) {
			const response = http
				.batch()
				.POST(
					URL_CHANNEL_LIST,
					JSON.stringify(
						{ jsonrpc: "2.0", method: "channel_list", params: { page: 1, page_size: 99999, resolve: true }, id: 1719338082805 }
					),
					{},
					true
				)
				.GET(
					URL_STATUS,
					{},
					true
				)
				.execute()

			const channelListResp = response[0];
			const userResp = response[1];

			const isSessionExpired = userResp.code === 401 || channelListResp.code === 401;
			const sessionToast = localSettings.showSessionWarnings ? { showToast: true } : {};
			if (isSessionExpired) {
				trace(`Odysee session expired. Please log in again. Disable this warning in Odysee plugin settings > Session Warnings.`, sessionToast);
			}

			let userId = null;
			if (!userResp.isOk) {
				if (!isSessionExpired) {
					trace(`Odysee user check failed (${userResp.code}). Playback tracking and content recommendations will be limited. Disable this warning in Odysee plugin settings > Session Warnings.`, sessionToast);
				}
			} else {
				userId = JSON.parse(userResp.body).user?.id?.toString() ?? null;
			}

			let channel = undefined;
			if (!channelListResp.isOk) {
				if (!isSessionExpired) {
					trace(`Odysee channel list failed (${channelListResp.code}). Members-only comments will be unavailable. Disable this warning in Odysee plugin settings > Session Warnings.`, sessionToast);
				}
			} else {
				const items = JSON.parse(channelListResp.body).result?.items ?? [];
				if (items.length > 0) {
					channel = {
						channelId: items[0].claim_id,
						// Signing goes against the @handle, which is the claim name and not
						// value.title, so keep both instead of letting one stand in for the other.
						handle: (items[0].name ?? "").replace(/^@/, ""),
						name: items[0].value?.title ?? items[0].name ?? "",
						thumbnail: items[0].value?.thumbnail?.url,
						url: items[0].permanent_url,
					};
				}
			}

			let auth_token;
			if (channel) {
				const channelName = `@${channel.handle}`;
				let hexdata = '';
				for (let i = 0; i < channelName.length; i++) {
					hexdata += channelName.charCodeAt(i).toString(16).padStart(2, '0');
				}
				const signBody = JSON.stringify({
					jsonrpc: "2.0",
					method: "channel_sign",
					params: { channel_id: channel.channelId, hexdata }
				});
				const [signResp, newUserResp] = http.batch()
					.POST(URL_CHANNEL_SIGN, signBody, headersToAdd, true)
					.GET(URL_USER_NEW, headersToAdd, false)
					.execute();
				// Signing is best effort: without it only members-only playback degrades,
				// so a failure here must never break the rest of the login.
				let signature;
				try {
					signature = signResp?.isOk ? JSON.parse(signResp.body).result : undefined;
				} catch (e) {
					trace(`Channel sign returned malformed JSON (${e})`);
				}
				if (signature) {
					channel.signatureData = signature;
				} else {
					trace(`Channel sign failed (${signResp?.code ?? ""})${signResp?.body ? ` - ${signResp.body}` : ""}`);
				}
				auth_token = parseNewUser(newUserResp)?.auth_token;
			} else {
				auth_token = parseNewUser(http.GET(URL_USER_NEW, headersToAdd))?.auth_token;
			}

			localState = { channel, userId, auth_token };

		} else {

			const userData = parseNewUser(http.GET(URL_USER_NEW, headersToAdd));
			if (userData) {
				localState.auth_token = userData.auth_token;
				localState.userId = userData.id.toString();
			}

		}
	} else {
		localState = restoredState
	}
}
source.saveState = function saveState() {
	// The batch cache is a request deduplicator for live counters. Persisting it would carry
	// stale view and reaction counts across restarts and grow the saved state without bound.
	const persisted = Object.assign({}, localState)
	delete persisted.batch_response_cache
	return JSON.stringify(persisted)
}
source.getHome = function () {
	const wantLive = !!localSettings.showLiveStreamsInHome;

	// Batch 1: content config + (optional) livestream list
	const batch1 = http.batch()
	
	batch1.GET(URL_CONTENT, {}, false);
	if (wantLive) {
		batch1.POST(URL_LIVE_STREAMS, "", FORM_URLENCODED_HEADERS, false);
	}
	const [contentResp, liveResp] = batch1.execute();

	const contentData    = parseOdyseeContentData(contentResp);
	let liveStreamPrep = null;
	if (wantLive) {
		const liveOptions = loadOptionsForSetting('liveStreamsInHomeIndex');
		const maxLive = parseInt(liveOptions[localSettings.liveStreamsInHomeIndex] ?? 10);
		liveStreamPrep = parseLiveStreamsResponse(liveResp, maxLive);
	}

	const featured = contentData.categories["PRIMARY_CONTENT"];
	const query = {
		channel_ids: featured.channelIds,
		claim_type: featured.claimType,
		order_by: ["trending_group", "trending_mixed"],
		page: 1,
		page_size: getSettingPageSize(),
		has_source: true,
		...(localSettings.showShortsInHome ? {} : { exclude_shorts: true }),
		no_totals: true,
		remove_duplicates: true,
		limit_claims_per_channel: 1,
		not_tags: buildNotTags()
	};

	// Batch 2: home feed claim_search + (optional) live-stream resolve
	const batch2 = http.batch().POST(URL_CLAIM_SEARCH, claimSearchBody(query),
		JSON_HEADERS);
	if (liveStreamPrep) {
		batch2.POST(URL_RESOLVE, resolveClaimsBody(liveStreamPrep.urls), JSON_HEADERS);
	}
	const [claimSearchResp, resolveResp] = batch2.execute();

	const initialResults = parseClaimSearchResponse(claimSearchResp);
	let liveStreams = [];
	if (liveStreamPrep && resolveResp) {
		try {
			liveStreams = resolvedLiveStreamsToPlatformVideos(
				parseResolveClaimsResponse(resolveResp, liveStreamPrep.urls),
				liveStreamPrep.viewerMap
			);
		} catch (e) {
			trace(`Live streams unavailable: ${e}`);
		}
	}

	return new QueryPager(query, [...liveStreams, ...initialResults]);
};

source.getShorts = function () {
	const contentData = parseOdyseeContentData(http.GET(URL_CONTENT, {}));
	const featured = contentData.categories["PRIMARY_CONTENT"];
	const shortContentThreshold = parseInt(shortContentThresholdOptions[localSettings.shortContentThresholdIndex] || 60);
	const query = {
		channel_ids: featured.channelIds,
		claim_type: featured.claimType,
		stream_types: ["video"],
		order_by: ["trending_group", "trending_mixed"],
		page: 1,
		page_size: getSettingPageSize(),
		has_source: true,
		no_totals: true,
		duration: `<=${shortContentThreshold}`,
		fee_amount: "<=0",
		limit_claims_per_channel: 1,
		not_tags: buildNotTags()
	};

	const shortOnly = true;
	return getQueryPager(query, shortOnly);
};

source.getSearchCapabilities = () => {
	return {
		types: [Type.Feed.Mixed],
		sorts: [Type.Order.Chronological, "^release_time"],
		filters: [
			{
				id: "date",
				name: "Date",
				isMultiSelect: false,
				filters: [
					{ id: Type.Date.Today, name: "Last 24 hours", value: "today" },
					{ id: Type.Date.LastWeek, name: "Last week", value: "thisweek" },
					{ id: Type.Date.LastMonth, name: "Last month", value: "thismonth" },
					{ id: Type.Date.LastYear, name: "Last year", value: "thisyear" }
				]
			},
		]
	};
};
source.search = function (query, type, order, filters) {

	query = query?.trim();

	if(!query || query.length === 0) {
		return new VideoPager([], false);
	}

	if(query && source.isContentDetailsUrl(query)) {
		return new VideoPager([source.getContentDetails(query)], false);
	}

	let sort = order;
	if (sort === Type.Order.Chronological) {
		sort = "release_time";
	}

	let date = null;
	if (filters && filters["date"]) {
		date = filters["date"][0];
	}

	return getSearchPagerVideos(query, false, 4, null, sort, date);
};
source.getSearchChannelContentsCapabilities = function () {
	return {
		types: [Type.Feed.Mixed],
		sorts: [Type.Order.Chronological],
		filters: []
	};
};
source.searchChannelContents = function (channelUrl, query, type, order, filters) {
	let { id: channel_id } = parseChannelUrl(channelUrl)

	if (channel_id.length !== CLAIM_ID_LENGTH) {
		const platform_channel = source.getChannel(channelUrl)
		channel_id = platform_channel.id.value
	}

	// Android passes the advertised sort through, desktop calls with url and query only,
	// so order is undefined there and the default claim_search ordering applies.
	let sort = order;
	if (sort === Type.Order.Chronological) {
		sort = "release_time";
	}

	return getSearchPagerVideos(query, false, 4, channel_id, sort);
};

source.searchChannels = function (query) {
	const pageSize = 10;
	const results = searchAndResolveChannels(query, 0, pageSize, false);
	return new SearchPagerChannels(query, results, pageSize, false);
};

//Channel
// examples
// https://odysee.com/@switchedtolinux:0
// https://odysee.com/@switchedtolinux:0?r=CpwgsVwZ2JEgHpGZZcUZGBPSMdKfZWyH
// lbry://@dubdigital#c2079078fa907da862f99c549ecf507d5caeffd3
source.isChannelUrl = function (url) {
	return REGEX_CHANNEL_URL.test(url)
};
source.getChannel = function (url) {
	const { slug, id } = parseChannelUrl(url)

	url = `lbry://${slug}#${id}`

	let [channel] = resolveClaimsChannel([url])
	return channel
};

source.getChannelCapabilities = () => {
	return {
		types: [Type.Feed.Videos, Type.Feed.Shorts],
		sorts: []
	};
}

source.getChannelContents = function (url, type) {
    let { id: channel_id } = parseChannelUrl(url)
    if (channel_id.length !== CLAIM_ID_LENGTH) {
        const platform_channel = source.getChannel(url)
        channel_id = platform_channel.id.value
    }
    
    const shortContentThreshold = parseInt(shortContentThresholdOptions[localSettings.shortContentThresholdIndex] || 60);
    
    // Base query parameters common to all queries
    const baseQuery = {
        channel_ids: [channel_id],
        claim_type: [CLAIM_TYPE_STREAM, CLAIM_TYPE_REPOST],
        order_by: [ORDER_BY_RELEASETIME],
        has_source: true,
        release_time: `<${Math.floor(Date.now() / 1000)}`, // Add current timestamp as release_time upper bound
        page: 1,
        page_size: getSettingPageSize()
    };
    
    baseQuery.not_tags = buildNotTags();
    
    let sourceConfig;
    switch(type) {
        case Type.Feed.Shorts:
            sourceConfig = {
                request_body: {
                    ...baseQuery,
                    stream_types: ["video"],
                    duration: [`<=${shortContentThreshold}`]
                },
                feedType: type
            };
            break;
        case Type.Feed.Videos:
        case Type.Feed.Mixed:
        case undefined:
        case null:
        case "":
        default:
            sourceConfig = { request_body: baseQuery, feedType: type };
    }

    const pager = createMultiSourcePager([sourceConfig]).nextPage();

    if (type !== Type.Feed.Shorts && channel_id) {
        try {
            const isLiveResp = http.POST(URL_LIVE_IS_LIVE,
                `channel_claim_id=${encodeURIComponent(channel_id)}`,
				FORM_URLENCODED_HEADERS, false);
            const prep = parseIsLiveResponse(isLiveResp);
            if (prep) {
                const [lbry] = resolveClaims([prep.canonicalUrl]);
                const live = resolvedLiveStreamToPlatformVideo(lbry, prep.viewerCount);
				if (live) {
					pager.results = [live, ...pager.results];
				}
            }
		} catch (e) {
			trace(`Channel live stream check failed: ${e}`);
		}
    }

    return pager;
};

source.getChannelPlaylists = function (url) {
	let { id: channel_id } = parseChannelUrl(url)

	if (channel_id.length !== CLAIM_ID_LENGTH) {
		const platform_channel = source.getChannel(url)
		channel_id = platform_channel.id.value
	}

	// TODO load the first video of each playlist to grab thumbnails
	return new ChannelPlaylistsPager(channel_id, 1, 24)
}

source.getChannelTemplateByClaimMap = () => {
	return {
		//Odysee
		3: {
			0: "lbry://{{CLAIMVALUE}}"
		}
	};
};

//Video
// examples
// https://odysee.com/We-Are-Anonymous:e
// https://odysee.com/@Anonymous:17/FTS:9
// https://odysee.com/@dubdigital:c/bitcoin-diamond-hands:3e
// https://odysee.com/@switchedtolinux:0/clearing-the-alpine-forest-weekly-news:2?r=CpwgsVwZ2JEgHpGZZcUZGBPSMdKfZWyH
// lbry://bitcoin-diamond-hands#3ef3d55066b9bee1419b538b371b463069c1f1a5
// lbry://@dubdigital#c/bitcoin-diamond-hands#3e
// https://odysee.com/@Questgenics:f/we-are-anonymous.....:9?r=CpwgsVwZ2JEgHpGZZcUZGBPSMdKfZWyH
source.isContentDetailsUrl = function (url) {
	return REGEX_DETAILS_URL.test(url) 
	|| REGEX_LBRY_EMBED_URL.test(url) 
	|| REGEX_ODYSEE_EMBED_URL.test(url)
};
/**
 * 
 * @param {*} url 
 * @returns channel_slug and channel_id might be undefined
 */
function parseDetailsUrl(url) {
	const match_result = url.match(REGEX_DETAILS_URL)
	if (!match_result) {
		throw new ScriptException(`Unrecognized content URL: ${url}`);
	}

	const channel_slug = decodeClaimNameFromUrl(match_result[3])
	const channel_id = match_result[5]

	const video_slug = decodeClaimNameFromUrl(match_result[6])
	const video_id = match_result[8]

	return { video_slug, video_id, channel_slug, channel_id, }
}
source.getContentDetails = function (url) {
	let video_slug, video_id;
	
	// Check if it's an embed URL
	if (REGEX_LBRY_EMBED_URL.test(url) || REGEX_ODYSEE_EMBED_URL.test(url)) {
		const embedMatch = url.match(REGEX_LBRY_EMBED_URL) ?? url.match(REGEX_ODYSEE_EMBED_URL);
		video_slug = decodeClaimNameFromUrl(embedMatch[1]);
		video_id = embedMatch[2];
	} else {
		({ video_slug, video_id } = parseDetailsUrl(url));
	}

	const claim_short_url = `lbry://${video_slug}#${video_id}`

	const [claim] = resolveClaims([claim_short_url]);
	
	if (!claim) {
		throw new ScriptException(`Failed to resolve content: ${claim_short_url}`);
	}

	if (!localSettings.allowMatureContent) {
		claim.value?.tags?.forEach((tag) => {
			if (MATURE_TAGS.includes(tag)) {
				throw new AgeException("Mature content is not supported on Odysee");
			}
		})
	}

	// Check if video is scheduled for future release
	const releaseDateTime = parseInt(claim?.value?.release_time ?? claim?.timestamp ?? 0);
	const currentDateTime = Math.floor(Date.now() / 1000);

	if (releaseDateTime > currentDateTime) {
		throw new UnavailableException("This content is not yet available");
	}

	// Text posts resolve to a document claim. Only Desktop accepts a non-media return here;
	// Android routes every content url to the video detail view, which rejects a post.
	if (claim.value?.stream_type === 'document') {
		if (IS_ANDROID) {
			throw new UnavailableException("Opening text posts from a link is not supported on Android. Open the post from the channel feed instead.");
		}
		return TEXT_DOC_TYPES.includes(claim.value?.source?.media_type)
			? lbryDocumentToPlatformPost(claim)
			: lbryBinaryDocToPlatformPost(claim);
	}

	let result = lbryVideoDetailToPlatformVideoDetails(claim);

	result.getContentRecommendations = function () {
		return source.getContentRecommendations(claim_short_url, { claim_id: result.id.value, title: result.name });
	};

	return result;
};

source.getContentRecommendations = (url, initialData) => {

	let claim_id = '';
	let query = '';

	if(initialData && initialData.claim_id && initialData.title) {
		claim_id = initialData.claim_id;
		query = initialData.title;
	} else {
		const [result] = resolveClaims([url]);
		if (!result) {
			return new ContentPager([], false);
		}
		claim_id = result.claim_id;
		query = result.value?.title ?? result.name ?? "";
	}

	const params = Object.entries({
		s: query,
		related_to: claim_id,
		from: 0,
		size: 10,
		free_only: true,
		nsfw: localSettings.allowMatureContent,
		...(localState.userId && { user_id: localState.userId, uid: localState.userId })
	}).map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&');

	const relatedResponse = http.GET(`https://recsys.odysee.tv/search?${params}`, {});

	if(relatedResponse.isOk) {
		const body = JSON.parse(relatedResponse.body);
		const claim_ids = body.map(e => e.claimId);

		const contentPager = claimSearch({
			claim_ids: claim_ids,
			no_totals: true,
			page: 1,
			page_size: 20
		});

		return new ContentPager(contentPager, false)
	}
	trace(`Load recommendations failed (${relatedResponse.code})${relatedResponse.body ? ` - ${relatedResponse.body}` : ""}`);

	return new ContentPager([], false);
}

source.getComments = function (url, isMembersOnly = null) {
	let claimId = url.includes('#') ? url.split('#')[1] : null;
	const claimIdValid = !!claimId && claimId.length === CLAIM_ID_LENGTH;

	let claim = null;
	// Resolve only if we actually need the claim id, or to auto-detect members-only.
	// Detection is best-effort: a resolve/parse failure must not break comment
	// loading when we already have a valid claim id straight from the URL.
	if (!claimIdValid || isMembersOnly === null) {
		try {
			const { video_slug, video_id } = parseDetailsUrl(url);
			[claim] = resolveClaims([`lbry://${video_slug}#${video_id}`]);
			if (!claimIdValid) {
				claimId = claim?.claim_id;
			}
		} catch (e) {
			// No claim id to fall back on - preserve original throwing behaviour.
			if (!claimIdValid) {
				throw e;
			}
			// Otherwise keep the valid claimId and skip members-only detection.
		}
	}

	if (isMembersOnly === null) {
		isMembersOnly = getIsMemberOnlyClaim(claim);
	}

	return getCommentsPager(url, claimId, 1, true, null, isMembersOnly);
}
source.getSubComments = function (comment) {
	if (typeof comment === 'string') {
		comment = JSON.parse(comment);
	}

	return getCommentsPager(comment.contextUrl, comment.context.claimId, 1, false, comment.context.commentId, comment.context.isMembersOnly == "true");
}
source.isPlaylistUrl = function (url) {
	return REGEX_PLAYLIST.test(url) || 
	REGEX_COLLECTION.test(url) || 
	REGEX_FAVORITES.test(url) || 
	REGEX_WATCH_LATER.test(url) || 
	REGEX_TAG_DISCOVER.test(url)
}
// TODO return a playlist thumbnail to show on the playlist import screen
source.getPlaylist = function (url) {
	if (REGEX_TAG_DISCOVER.test(url)) {
		const matchResult = url.match(REGEX_TAG_DISCOVER)
		const tagParam = matchResult[1]
		const tags = decodeURIComponent(tagParam).split(',').map(t => t.trim())

		const query = {
			any_tags: tags,
			not_tags: buildNotTags(),
			claim_type: [CLAIM_TYPE_STREAM],
			stream_types: ["video", "audio"],
			has_source: true,
			page_size: getSettingPageSize(),
			page: 1,
			order_by: [ORDER_BY_RELEASETIME],
			limit_claims_per_channel: 1
		}

		const initialResults = claimSearch(query);

		const thumbnail = initialResults.length > 0 ? initialResults[0].thumbnails?.sources?.[0]?.url : null

		return new PlatformPlaylistDetails({
			id: new PlatformID(PLATFORM, `tag_${tagParam}`, plugin.config.id, PLATFORM_CLAIMTYPE),
			name: `#${tags.join(', #')}`,
			author: EMPTY_AUTHOR,
			thumbnail: thumbnail,
			datetime: Date.now() / 1000,
			url,
			videoCount: -1,
			contents: new QueryPager(query, initialResults)
		})
	}
	if (REGEX_FAVORITES.test(url)) {
		const playlistId = "favorites"

		const playlist = parseSharedPreferences(loadPreferences()).builtinCollections?.favorites

		if (!playlist) {
			throw new ScriptException(`Playlist not found: ${playlistId}`)
		}

		return formatUserPlaylist(playlist, playlistId)
	}
	if (REGEX_WATCH_LATER.test(url)) {
		const playlistId = "watchlater"

		const playlist = parseSharedPreferences(loadPreferences()).builtinCollections?.watchlater

		if (!playlist) {
			throw new ScriptException(`Playlist not found: ${playlistId}`)
		}

		return formatUserPlaylist(playlist, playlistId)
	}

	const matchResult = url.match(REGEX_PLAYLIST)
	if (matchResult === null) {
		const playlistId = url.match(REGEX_COLLECTION)[1]

		const playlist = parseSharedPreferences(loadPreferences()).unpublishedCollections?.[playlistId]

		if (!playlist) {
			throw new ScriptException(`Playlist not found: ${playlistId}`)
		}

		return formatUserPlaylist(playlist, playlistId)
	} else {
		const playlistId = matchResult[1]

		const response = http.POST(
			URL_CLAIM_SEARCH,
			JSON.stringify({ jsonrpc: "2.0", method: "claim_search", params: { include_is_my_output: true, claim_ids: [playlistId], page: 1, page_size: 1, no_totals: true }, id: 1719268918154 }),
			{},
			false
		)
		const playlistMetadata = parseLbryResult(response, "Load playlist").items?.[0];
		if (!playlistMetadata) {
			throw new ScriptException(`Playlist not found: ${playlistId}`);
		}

		const signing = playlistMetadata.signing_channel;
		const claims = playlistMetadata.value?.claims ?? [];
		let resolvedVideos = [];
		if (claims.length) {
			const claimSearchResp = http.POST(URL_RESOLVE, JSON.stringify({
				jsonrpc: "2.0", method: "claim_search",
				params: { claim_ids: claims, page: 1, page_size: claims.length, no_totals: true },
				id: 1719330225903
			}), JSON_HEADERS);
			const ordered = [];
			(parseLbryResult(claimSearchResp, "Resolve playlist claims").items ?? []).forEach(c => {
				ordered[claims.indexOf(c.claim_id)] = c;
			});
			resolvedVideos = lbryVideosToPlatformVideos(ordered);
		}
		return new PlatformPlaylistDetails({
			id: new PlatformID(PLATFORM, playlistId, plugin.config.id, PLATFORM_CLAIMTYPE),
			name: playlistMetadata.value?.title ?? playlistMetadata.name ?? "",
			author: signing ? new PlatformAuthorLink(
				new PlatformID(PLATFORM, signing.claim_id, plugin.config.id, PLATFORM_CLAIMTYPE),
				signing.value?.title ?? signing.name ?? "",
				signing.permanent_url ?? "",
				signing.value?.thumbnail?.url ?? null
			) : EMPTY_AUTHOR,
			datetime: playlistMetadata.meta?.creation_timestamp ?? 0,
			url,
			videoCount: claims.length,
			contents: new VideoPager(resolvedVideos, false)
		})
	}
}

source.getLiveChatWindow = function(url) {
	let { video_slug, video_id, channel_slug, channel_id } = parseDetailsUrl(url);

	if (!channel_slug || !channel_id) {
		const [claim] = resolveClaims([`lbry://${video_slug}#${video_id}`]);
		if (claim?.signing_channel) {
			channel_slug = claim.signing_channel.name;
			channel_id = claim.signing_channel.claim_id;
		}
	}

	const path = format_odysee_path(channel_slug, channel_id, video_slug, video_id);
	return { url: `${URL_BASE}/$/popout${path}` };
};

function getSettingPageSize() {
	return localSettings.extraRequestToLoadViewCount ? 10 : 20
}

function loadPreferences() {
	return http.POST(
		URL_PREFERENCES,
		JSON.stringify({ jsonrpc: "2.0", method: "preference_get", params: { key: "shared" }, id: 1719254704333 }),
		{},
		true
	)
}
function formatUserPlaylist(playlist, playlistId) {
	return new PlatformPlaylistDetails({
		id: new PlatformID(PLATFORM, playlistId, plugin.config.id, PLATFORM_CLAIMTYPE),
		name: playlist.name,
		author: localState.channel === undefined ? EMPTY_AUTHOR : new PlatformAuthorLink(
			new PlatformID(PLATFORM, localState.channel.channelId, plugin.config.id, PLATFORM_CLAIMTYPE),
			localState.channel.name,
			localState.channel.url,
			localState.channel.thumbnail
		),
		datetime: playlist.createdAt,
		url: `${PLAYLIST_URL_BASE}${playlistId}`,
		videoCount: playlist.itemCount,
		contents: new VideoPager(resolveClaimsVideo(playlist.items), false)
	})
}
// The LBRY proxy reports RPC failures as HTTP 200 with an {error} body and no result
// key, so resp.isOk on its own is not enough to assume result exists.
function parseLbryResult(resp, what) {
	if (!resp?.isOk) {
		trace(`${what} failed (${resp?.code ?? ""})${resp?.body ? ` - ${resp.body}` : ""}`, { showToast: true })
		throw new ScriptException(`${what} failed (${resp?.code ?? "no response"})`)
	}
	let body
	try {
		body = JSON.parse(resp.body)
	} catch (e) {
		trace(`${what} returned malformed JSON (${e}) - ${resp.body}`, { showToast: true })
		throw new ScriptException(`${what} failed: malformed response`)
	}
	if (!body?.result) {
		const reason = body?.error?.message ?? body?.error?.name ?? "no result in response"
		trace(`${what} returned no result - ${resp.body}`, { showToast: true })
		throw new ScriptException(`${what} failed: ${reason}`)
	}
	return body.result
}
// Anonymous sessions get {error: "authentication required"} with no result key.
function parseSharedPreferences(response) {
	let body
	try {
		body = JSON.parse(response?.body)
	} catch (e) {
		trace(`Load preferences returned malformed JSON (${e}) - ${response?.body}`, { showToast: true })
		throw new ScriptException("Load preferences failed: malformed response")
	}
	const shared = body?.result?.shared?.value
	if (!shared) {
		throw new LoginRequiredException("Please log in to your Odysee account to access your subscriptions and playlists.")
	}
	return shared
}
source.getUserSubscriptions = function () {
	return parseSharedPreferences(loadPreferences()).subscriptions ?? []
}
source.getUserPlaylists = function () {
	const collections = Object.keys(parseSharedPreferences(loadPreferences()).unpublishedCollections ?? {})
		.map(function (collectionId) {
			return `${PLAYLIST_URL_BASE}${collectionId}`
		})

	const publicPlaylistsResponse = http.POST(
		URL_COLLECTION_LIST,
		JSON.stringify({ jsonrpc: "2.0", method: "collection_list", params: { resolve: true, page: 1, page_size: 200 }, id: 1719352987252 }),
		{},
		true
	)
	const playlists = (parseLbryResult(publicPlaylistsResponse, "Load published playlists").items ?? []).map(function (playlist) {
		return `${PLAYLIST_URL_BASE}${playlist.claim_id}`
	})

	return [...collections, ...playlists, ...["https://odysee.com/$/playlist/watchlater", "https://odysee.com/$/playlist/favorites"]]
}
source.getPlaybackTracker = function (url) {
	if (!localSettings.odyseeActivity || !localState.userId) {
		return null
	}
	return new OdyseePlaybackTracker(url)
}


// Inverse of encodeClaimNameForUrl. decodeURI is not usable here: it leaves %2C, %3B,
// %26 and friends encoded, and LBRY rejects those names as "not a valid url".
function decodeClaimNameFromUrl(name) {
	if (name === undefined) {
		return name;
	}
	try {
		return decodeURIComponent(name);
	} catch (error) {
		return name;
	}
}

function parseChannelUrl(url) {
	const match_result = url.match(REGEX_CHANNEL_URL)
	if (!match_result) {
		throw new ScriptException(`Unrecognized channel URL: ${url}`);
	}

	const slug = decodeClaimNameFromUrl(match_result[2])
	const id = match_result[4]

	return { slug, id }
}


class OdyseePlaybackTracker extends PlaybackTracker {
	constructor(url) {
		const intervalSeconds = 10
		super(intervalSeconds * 1000)

		const { video_slug, video_id } = parseDetailsUrl(url)

		const claim_short_url = `lbry://${video_slug}#${video_id}`

		const [claim] = resolveClaims([claim_short_url]);

		if (!claim?.canonical_url) {
			throw new ScriptException(`Failed to resolve content for playback tracking: ${claim_short_url}`);
		}

		this.url = claim.canonical_url.replace('lbry://','');

		this.duration = (claim?.value?.video?.duration ?? 0) * 1000;

		this.lastMessage = Date.now()
	}
	onInit(_seconds) {


	}
	postPlayback(position, rel_position) {
		const resp = http.POST(
			URL_REPORT_PLAYBACK,
			traceJson({
				rebuf_count: 0,
				rebuf_duration: 0,
				url: this.url,
				device: "web",
				duration: Date.now() - this.lastMessage,
				// hardcoded because there isn't a way in grayjay to know this value
				protocol: "stb",
				// not really sure what this means
				player: "player-v10",
				...(localState.userId && { user_id: localState.userId }),
				position,
				rel_position,
				// hardcoded because there isn't a way in grayjay to know the quality playing
				bitrate: 2890800
			}),
			JSON_HEADERS,
			false
		)
		trace(`Playback report (${resp.code})${resp.body ? ` - ${resp.body}` : ""}`)
		this.lastMessage = Date.now()
	}
	onProgress(seconds, isPlaying) {
		if (!isPlaying || seconds === 0) {
			return
		}
		this.postPlayback(seconds * 1000, Math.round(seconds * 1000 / this.duration * 100))
	}
	onConcluded() {
		this.postPlayback(this.duration, 100)
	}
}

function getCommentsPager(contextUrl, claimId, page, topLevel, parentId = null, isMembersOnly) {

	if(!isMembersOnly) {
		isMembersOnly = false;
	}
	
	const query = {
		"jsonrpc": "2.0",
		"id": 1,
		"method": "comment.List",
		"params": {
			"page": page,
			"claim_id": claimId,
			"page_size": 10,
			"top_level": topLevel,
			"sort_by": 3,
			... (parentId ? { "parent_id": parentId } : {})
		}
	};

	// currently only accounts with channels can see and add comments on members only content
	if(isMembersOnly && bridge.isLoggedIn() && localState.channel) {
		// signatureData is only set when channel_sign succeeded at login; without it the
		// request cannot be signed, so load the public list instead of crashing.
		if (localState.channel.signatureData) {
			//required for members-only content
			query.params.is_protected = true;
			query.params.requestor_channel_id = localState.channel.channelId;
			// State saved before handle existed only carries the display name.
			const requestorHandle = localState.channel.handle ?? (localState.channel.name ?? "").replace(/^@/, "");
			query.params.requestor_channel_name	 = `@${requestorHandle}`;
			query.params.signature = localState.channel.signatureData.signature;
			query.params.signing_ts	= localState.channel.signatureData.signing_ts;
		} else {
			trace("Channel signature unavailable, loading comments without members-only access");
		}
	}
	
	const body = JSON.stringify(query);

	const resp = http.POST(URL_COMMENTS_LIST, body, JSON_HEADERS, isMembersOnly);

	if (!resp.isOk) {
		trace(`Load comments failed (${resp.code}) - ${resp.body}`);
		return new CommentPager([], false, {});
	}

	const result = JSON.parse(resp.body);

	//Make optional thumbnail map
	let claimsToQuery = result.result?.items?.map(i => i.channel_id) ?? [];
	claimsToQuery = [...new Set(claimsToQuery)]; //Deduplicate list
	const claimsResp = http.POST(URL_CLAIM_SEARCH, JSON.stringify({
		"jsonrpc": "2.0",
		method: "claim_search",
		params: {
			claim_ids: claimsToQuery,
			no_totals: true,
			page: 1,
			page_size: 20
		}
	}), JSON_HEADERS, isMembersOnly);

	// Thumbnails are cosmetic, so a bad response here must never break the comment list.
	// The parse used to run before the isOk check, which threw on any non-JSON body.
	const thumbnailMap = {};
	if (claimsResp.isOk) {
		try {
			for (const i of (JSON.parse(claimsResp.body)?.result?.items ?? [])) {
				const url = i.value?.thumbnail?.url;
				if (url) {
					thumbnailMap[i.claim_id] = url;
				}
			}
		} catch (e) {
			trace(`Load comment thumbnails returned malformed JSON (${e})`);
		}
	} else {
		trace(`Load comment thumbnails failed (${claimsResp.code})${claimsResp.body ? ` - ${claimsResp.body}` : ""}`);
	}

	//Map comments
	const comments = result.result?.items?.map(i => {
		const c = new Comment({
			contextUrl: contextUrl,
			author: new PlatformAuthorLink(new PlatformID(PLATFORM, i.channel_id, plugin.config.id, PLATFORM_CLAIMTYPE),
				i.channel_name ?? "",
				i.channel_url,
				thumbnailMap[i.channel_id] ?? ""),
			message: i.comment ?? "",
			date: i.timestamp,
			replyCount: i.replies,
			context: { claimId: i.claim_id, commentId: i.comment_id, isMembersOnly: isMembersOnly.toString() }
		});

		return c;
	}) ?? [];

	const hasMore = (result?.result?.page ?? 0) < (result?.result?.total_pages ?? 1);
	return new OdyseeCommentPager(comments, hasMore, { contextUrl, claimId, page, topLevel, parentId, isMembersOnly: isMembersOnly });
}

//Internals
function parseOdyseeContentData(resp) {
	if (!resp?.isOk) {
		trace(`Request ${URL_CONTENT} failed (${resp?.code ?? ""})${resp?.body ? ` - ${resp.body}` : ""}`, { showToast: true });
		throw new ScriptException("Failed request [" + URL_CONTENT + "] (" + resp?.code + ")");
	}
	// Both callers immediately read .categories.PRIMARY_CONTENT, so validate that far
	// here: otherwise a shape change breaks Home and Shorts with a raw TypeError.
	let data;
	try {
		data = JSON.parse(resp.body)?.data?.["en"];
	} catch (e) {
		trace(`Request ${URL_CONTENT} returned malformed JSON (${e})`, { showToast: true });
		throw new ScriptException(`Failed request [${URL_CONTENT}]: malformed response`);
	}
	if (!data?.categories?.["PRIMARY_CONTENT"]) {
		trace(`Request ${URL_CONTENT} returned no content configuration - ${resp.body}`, { showToast: true });
		throw new ScriptException(`Failed request [${URL_CONTENT}]: no content configuration`);
	}
	return data;
}
function parseLiveStreamsResponse(resp, maxCount) {
	if (!resp?.isOk) {
		trace(`Load live streams failed (${resp?.code ?? ""})${resp?.body ? ` - ${resp.body}` : ""}`);
		return null;
	}
	const streams = (JSON.parse(resp.body)?.data ?? [])
		.filter(s => s.Live && s.ActiveClaim)
		.sort((a, b) => (b.ViewerCount ?? 0) - (a.ViewerCount ?? 0))
		.slice(0, maxCount);
	if (!streams.length) {
		return null;
	}
	const viewerMap = {};
	streams.forEach(s => { viewerMap[s.ActiveClaim.ClaimID] = s.ViewerCount ?? 0; });
	return { urls: streams.map(s => s.ActiveClaim.CanonicalURL), viewerMap };
}

function resolvedLiveStreamsToPlatformVideos(lbryClaims, viewerMap) {
	const notTags = buildNotTags();
	return lbryClaims
		.filter(lbry => lbry && !lbry.error)
		.filter(lbry => !(lbry.value?.tags ?? []).some(t => notTags.includes(t)))
		.map(lbry => liveClaimToPlatformVideo(lbry, viewerMap[lbry.claim_id]));
}


function liveClaimToPlatformVideo(lbry, viewerCount) {
	const claimId = lbry.claim_id;
	const shareUrl = lbry.signing_channel?.claim_id
		? format_odysee_share_url(lbry.signing_channel.name, lbry.signing_channel.claim_id, lbry.name, claimId)
		: format_odysee_share_url_anonymous(lbry.name, claimId);

	return new PlatformVideo({
		id: new PlatformID(PLATFORM, claimId, plugin.config.id),
		name: lbry.value?.title ?? lbry.name,
		thumbnails: new Thumbnails([new Thumbnail(lbry.value?.thumbnail?.url ?? "", 0)]),
		author: channelToPlatformAuthorLink(lbry),
		datetime: lbryVideoToDateTime(lbry),
		duration: 0,
		viewCount: viewerCount ?? 0,
		url: lbry.permanent_url,
		shareUrl,
		isLive: true,
		isShort: false,
		links: {}
	});
}

function parseIsLiveResponse(resp) {
	if (!resp?.isOk) {
		trace(`Check live status failed (${resp?.code ?? ""})${resp?.body ? ` - ${resp.body}` : ""}`);
		return null;
	}
	const data = JSON.parse(resp.body)?.data;
	if (!data?.Live || !data.ActiveClaim?.CanonicalURL) {
		return null;
	}
	return { canonicalUrl: data.ActiveClaim.CanonicalURL, viewerCount: data.ViewerCount };
}
function resolvedLiveStreamToPlatformVideo(lbry, viewerCount) {
	if (!lbry || lbry.error) {
		return null;
	}
	const notTags = buildNotTags();
	if ((lbry.value?.tags ?? []).some(t => notTags.includes(t))) {
		return null;
	}
	return liveClaimToPlatformVideo(lbry, viewerCount);
}
function getQueryPager(query, shortsOnly=false) {
	const initialResults = claimSearch(query, shortsOnly);
	return new QueryPager(query, initialResults);
}
function getSearchPagerVideos(query, nsfw = false, maxRetry = 0, channelId = null, sortBy = null, timeFilter = null) {
	const pageSize = 10;
	const results = searchAndResolveVideos(query, 0, pageSize, nsfw, maxRetry, channelId, sortBy, timeFilter);
	return new SearchPagerVideos(query, results, pageSize, nsfw, channelId, sortBy, timeFilter);
}


//Pagers
class QueryPager extends VideoPager {
	constructor(query, results) {
		// updated Hasmore condition since some unsupported content types may be hidden and would break the pagination
		super(results, !!results.length, { query });
	}

	nextPage() {
		this.context.query.page = (this.context.query.page || 0) + 1;
		return getQueryPager(this.context.query);
	}
}
function getPlaylists(channelId, nextPageToLoad, pageSize) {
	const params = {
		page_size: pageSize,
		page: nextPageToLoad,
		claim_type: ["collection"],
		no_totals: true,
		order_by: ["release_time"],
		has_source: true,
		channel_ids: [channelId],
		// release_time is in seconds; Date.now() milliseconds made this bound a no-op
		release_time: `<${Math.floor(Date.now() / 1000)}`
	}

	const response = http.POST(
		URL_CLAIM_SEARCH,
		JSON.stringify(
			{
				jsonrpc: "2.0",
				method: "claim_search",
				params: { ...params, not_tags: buildNotTags() },
				id: Date.now()
			}
		),
		{ "Content-Type": "application/json" },
		false
	)

	const formattedPlaylists = (parseLbryResult(response, "Load channel playlists").items ?? []).map(function (playlist) {
		return new PlatformPlaylist({
			id: new PlatformID(PLATFORM, playlist.claim_id, plugin.config.id, PLATFORM_CLAIMTYPE),
			name: playlist.value.title,
			author: new PlatformAuthorLink(
				new PlatformID(PLATFORM, playlist.signing_channel.claim_id, plugin.config.id, PLATFORM_CLAIMTYPE),
				playlist.signing_channel.value.title,
				playlist.signing_channel.permanent_url,
				// A channel without an avatar has no thumbnail node; null lets the app draw its placeholder.
				playlist.signing_channel.value?.thumbnail?.url ?? null
			),
			datetime: playlist.meta.creation_timestamp,
			url: `${PLAYLIST_URL_BASE}${playlist.claim_id}`,
			// A collection can be published with a title and no claims array at all.
			videoCount: playlist.value.claims?.length ?? 0,
			// thumbnail: string
		})
	})
	return formattedPlaylists
}
class ChannelPlaylistsPager extends PlaylistPager {
	constructor(channelId, firstPage, pageSize) {
		const formatted_playlists = getPlaylists(channelId, firstPage, pageSize)

		// odysee doesn't tell us if there are more we just need to try and return none if there are none
		super(formatted_playlists, formatted_playlists.length === pageSize)

		this.channelId = channelId
		this.nextPageToLoad = firstPage + 1
		this.pageSize = pageSize
	}
	nextPage() {
		const formatted_playlists = getPlaylists(this.channelId, this.nextPageToLoad, this.pageSize)

		this.results = formatted_playlists
		this.nextPageToLoad += 1
		this.hasMore = formatted_playlists.length === this.pageSize

		return this
	}
}
class SearchPagerVideos extends VideoPager {
	constructor(searchStr, results, pageSize, nsfw = false, channelId = null, sortBy = null, timeFilter = null) {
		super(results, !!results.length, {
			query: searchStr,
			page_size: pageSize,
			nsfw: nsfw,
			page: 0,
			channelId,
			sortBy,
			timeFilter
		});
	}

	nextPage() {
		this.context.page = this.context.page + 1;
		const start = (this.context.page - 1) * this.context.page_size;

		this.results = searchAndResolveVideos(this.context.query, start, this.context.page_size, this.context.nsfw, 5, this.context.channelId, this.context.sortBy, this.context.timeFilter);
		this.hasMore = this.results.length != 0;

		return this;
	}
}
class SearchPagerChannels extends ChannelPager {
	constructor(searchStr, results, pageSize, nsfw = false) {
		super(results, !!results.length, {
			query: searchStr,
			page_size: pageSize,
			nsfw: nsfw,
			page: 0
		});
	}

	nextPage() {
		this.context.page = this.context.page + 1;
		const start = (this.context.page - 1) * this.context.page_size;

		this.results = searchAndResolveChannels(this.context.query, start, this.context.page_size, this.context.nsfw);
		this.hasMore = this.results.length != 0;

		return this;
	}
}

class OdyseeCommentPager extends CommentPager {
	constructor(results, hasMore, context) {
		super(results, hasMore, context);
	}

	nextPage() {
		return getCommentsPager(this.context.contextUrl, this.context.claimId, this.context.page + 1, this.context.topLevel, this.context.parentId, this.context.isMembersOnly);
	}
}

//Internal methods
function searchAndResolveVideos(search, from, size, nsfw = false, maxRetry = 0, channelId = null, sortBy = null, timeFilter = null) {
	const claimUrls = searchClaims(search, from, size, "file", nsfw, maxRetry, 0, channelId, sortBy, timeFilter);
	return resolveClaimsVideo(claimUrls);
}
function searchAndResolveChannels(search, from, size, nsfw = false) {
	const claimUrls = searchClaims(search, from, size, "channel", nsfw, 4);
	return resolveClaimsChannel(claimUrls);
}
function searchClaims(search, from, size, type = "file", nsfw = false, maxRetry = 0, ittRetry = 0, channelId = null, sortBy = null, timeFilter = null) {
	let url = "https://lighthouse.odysee.tv/search?s=" + encodeURIComponent(search) +
		"&from=" + from + "&size=" + size + "&nsfw=" + nsfw;// + "&claimType=file&mediaType=video"

	if (type == "file") {
		url += "&claimType=file&mediaType=video";
	} else {
		url += "&claimType=" + type;
	}

	if (channelId) {
		url += "&channel_id=" + channelId;
	}

	if (sortBy) {
		url += "&sort_by=" + sortBy;
	}

	if (timeFilter) {
		url += "&time_filter=" + timeFilter;
	}

	const respSearch = http.GET(url, {});

	if (!respSearch.isOk) {
		if (respSearch.code == 502 || (respSearch.body && respSearch.body.indexOf("1020") > 0)) {
			if (ittRetry < maxRetry) {
				trace("Retry searchClaims [" + ittRetry + "]");
				return searchClaims(search, from, size, type, nsfw, maxRetry, ittRetry + 1);
			}
			else {
				trace("Retrying searchClaims failed after " + ittRetry + " attempts", { showToast: true });
				return [];
			}
		}

		if (respSearch.code == 408) {
			trace("Odysee failed with timeout after retries", { showToast: true });
			return [];
		}
		else {
			trace(`Search claims failed (${respSearch.code}) - ${respSearch.body}`, { showToast: true });
			throw new ScriptException("Failed to search with code " + respSearch.code + "\n" + respSearch.body);
		}
	}
	if (respSearch.body == null || respSearch.body == "") {
		trace("Search claims failed due to empty response body", { showToast: true });
		throw new ScriptException("Failed to search with code " + respSearch.code + " due to empty body")
	}

	const claims = JSON.parse(respSearch.body);
	const claimUrls = claims.map(x => x.name + "#" + x.claimId);
	return claimUrls;
}

/**
 * Converts LBRY claim search results to platform content objects
 * @param {Array} items - The items returned from a claim_search API call
 * @returns {Array} Array of platform content objects (videos, audio, documents)
 */
function claimSearchItemsToPlatformContent(items) {
    // Define stream types to process
    const media_stream_types = ['audio', 'video'];
    const docs_stream_types = ['document'];
    
    // Process media types (audio, video)
    let mediaItems = items.filter(z => z.value && media_stream_types.includes(z.value.stream_type));
    let media = lbryVideosToPlatformVideos(mediaItems);
    
    // Process documents
    let documents = [];
    let documentItems = items.filter(z => z.value && docs_stream_types.includes(z.value.stream_type));

    if (documentItems.length) {
        // Separate text documents from binary documents
        const textDocItems = documentItems.filter(z => 
            z.value?.source?.media_type && 
            TEXT_DOC_TYPES.includes(z.value.source.media_type)
        );
        
        // All other document types are treated as binary
        const binaryDocItems = documentItems.filter(z => 
            !z.value?.source?.media_type || 
            !TEXT_DOC_TYPES.includes(z.value.source.media_type)
        );
        
        // Process binary documents with the binary handler
        const binaryDocs = binaryDocItems.map(item => {
            return lbryBinaryDocToPlatformPost(item);
        });
        
        // Process text document types normally
        let documents_body_batch_request = http.batch();
        textDocItems.forEach(lbry => {
            const sdHash = lbry.value?.source?.sd_hash;
            if (sdHash) {
                const sdHashPrefix = sdHash.substring(0, 6);
                documents_body_batch_request.GET(`https://player.odycdn.com/v6/streams/${lbry.claim_id}/${sdHashPrefix}.mp4`, headersToAdd);
            }
        });
        
        let textDocs = [];
        if (textDocItems.length > 0) {
            let documents_response = documents_body_batch_request.execute();
            textDocs = textDocItems.map((x, index) => {
                if (x.value?.source?.sd_hash) {
					if (!documents_response[index].isOk) {
						trace(`Load document body failed (${documents_response[index].code})${documents_response[index].body ? ` - ${documents_response[index].body}` : ""}`);
					}
					const postContent = documents_response[index].isOk ? documents_response[index].body : undefined;
                    return lbryDocumentToPlatformPost(x, postContent);
                }
                // If there's no sd_hash, return an empty document with error message
                return lbryDocumentToPlatformPost(x, "Content unavailable");
            });
        }
        
        // Combine all document types
        documents = [...binaryDocs, ...textDocs];
    }
    
    // Combine all results and sort by date
    return [...media, ...documents].sort((a, b) => b.datetime - a.datetime);
}

function claimSearchBody(query) {
    return JSON.stringify({ jsonrpc: "2.0", method: "claim_search", params: query, id: Date.now() });
}
function parseClaimSearchResponse(resp, shortsOnly) {
    const items = parseLbryResult(resp, "Claim search").items ?? [];
    return claimSearchItemsToResults(items, shortsOnly);
}
function claimSearch(query, shortsOnly) {
	const resp = http.POST(URL_CLAIM_SEARCH, claimSearchBody(query), JSON_HEADERS);
    return parseClaimSearchResponse(resp, shortsOnly);
}
function claimSearchItemsToResults(items, shortsOnly) {
    // Define stream types to process
    const media_stream_types = ['audio', 'video'];
    const docs_stream_types = ['document'];
    
    // Process media types (audio, video)
    let mediaItems = items.filter(z => z.value && media_stream_types.includes(z.value.stream_type));
    if (shortsOnly) {
        mediaItems = mediaItems.filter(z => (z.value?.video?.height ?? 0) > (z.value?.video?.width ?? 0));
    }
    let media = lbryVideosToPlatformVideos(mediaItems);
    
    // Process documents
    let documents = [];
    let documentItems = items.filter(z => z.value && docs_stream_types.includes(z.value.stream_type));
    
    if (documentItems.length) {
        // Separate text documents from binary documents
        const textDocItems = documentItems.filter(z => 
            z.value?.source?.media_type && 
            TEXT_DOC_TYPES.includes(z.value.source.media_type)
        );
        
        // All other document types are treated as binary
        const binaryDocItems = documentItems.filter(z => 
            !z.value?.source?.media_type || 
            !TEXT_DOC_TYPES.includes(z.value.source.media_type)
        );
        
        // Process binary documents with the binary handler
        const binaryDocs = binaryDocItems.map(item => {
            return lbryBinaryDocToPlatformPost(item);
        });
        
        // Process text document types normally
        let documents_body_batch_request = http.batch();
        textDocItems.forEach(lbry => {
            const sdHash = lbry.value?.source?.sd_hash;
            if (sdHash) {
                const sdHashPrefix = sdHash.substring(0, 6);
                documents_body_batch_request.GET(`https://player.odycdn.com/v6/streams/${lbry.claim_id}/${sdHashPrefix}.mp4`, headersToAdd);
            }
        });
        
        let textDocs = [];
        if (textDocItems.length > 0) {
            let documents_response = documents_body_batch_request.execute();
            textDocs = textDocItems.map((x, index) => {
                if (x.value?.source?.sd_hash) {
					if (!documents_response[index].isOk) {
						trace(`Load document body failed (${documents_response[index].code})${documents_response[index].body ? ` - ${documents_response[index].body}` : ""}`);
					}
					const postContent = documents_response[index].isOk ? documents_response[index].body : undefined;
                    return lbryDocumentToPlatformPost(x, postContent);
                }
                // If there's no sd_hash, return an empty document with error message
                return lbryDocumentToPlatformPost(x, "Content unavailable");
            });
        }
        
        // Combine all document types
        documents = [...binaryDocs, ...textDocs];
    }
    
    // Combine all results and sort by date
    return [...media, ...documents].sort((a, b) => b.datetime - a.datetime);
}

/**
 * Converts binary documents to a platform post with an embedded viewer
 * @param {Object} lbry - The LBRY claim object for a PDF file
 * @returns {PlatformPostDetails} Platform post with embedded PDF viewer
 */
function lbryBinaryDocToPlatformPost(lbry) {
	
	const claimId = lbry.claim_id;
	const name = lbry.name;

    const shareUrl = lbry.signing_channel?.claim_id !== undefined
        ? format_odysee_share_url(lbry.signing_channel.name, lbry.signing_channel.claim_id, name, claimId)
        : format_odysee_share_url_anonymous(name, claimId);
    
    const sdHash = lbry.value?.source?.sd_hash;
    const sdHashPrefix = sdHash ? sdHash.substring(0, 6) : "";
    
    // Create a direct download URL for the document
    const downloadUrl = `https://player.odycdn.com/v6/streams/${claimId}/${sdHashPrefix}.mp4`;
    
    // Create HTML content with download link
    const htmlContent = `
        <div>
			<a href="${downloadUrl}" target="_blank" style="display: inline-block; background-color: #2196F3; color: white; padding: 10px 15px; text-decoration: none; border-radius: 4px; margin-right: 10px;">
				<strong>View/Download File</strong>
			</a>
        </div>
    `;
    
    const {
        rating,
        subCount
    } = lbryToMetrics(lbry, { loadSubCount: true, loadRating: true });
    
    return new PlatformPostDetails({
        id: new PlatformID(PLATFORM, claimId, plugin.config.id),
        name: lbry.value?.title ?? name,
        author: channelToPlatformAuthorLink(lbry, subCount),
        datetime: lbryVideoToDateTime(lbry),
        url: shareUrl,
        rating: rating,
        textType: Type.Text.HTML,
        content: htmlContent,
        images: lbry.value?.thumbnail?.url ? [lbry.value?.thumbnail?.url] : [],
        thumbnails: lbry.value?.thumbnail?.url ? [new Thumbnails([new Thumbnail(lbry.value?.thumbnail?.url, 0)])] : [],
    });
}

function resolveClaimsChannel(claims) {
	if (!Array.isArray(claims) || claims.length === 0) {
		return [];
	}
	const results = resolveClaims(claims);

	// getsub count using batch request
	const authToken = ensureAuthToken();
	const requests = results.map(claim => {
		return {
			url: `${URL_API_SUB_COUNT}?claim_id=${claim.claim_id}`,
			body: `auth_token=${authToken}&claim_id=${claim.claim_id}`,
			headers: FORM_URLENCODED_HEADERS,
		};
	});

	const responses = batchRequest(requests, { useStateCache: true });

	const responseMap = responses.reduce((map, resp) => {
		try {
			const url = new URL(resp.url);
			const claimId = url.searchParams.get('claim_id');
			if (claimId) {
				if (resp.isOk) {
					map[claimId] = resp;
				} else {
					trace(`Load channel subscriber count failed for ${claimId} (${resp?.code ?? ""})${resp?.body ? ` - ${resp.body}` : ""}`);
				}
			}
		} catch (error) {
			trace(`Error parsing response URL: ${error}`);
		}
		return map;
	}, {});

	// A channel that cannot be converted is dropped rather than retried; the previous
	// retry called the same converter that had just thrown, so one bad entry killed the pager.
	return results.map(channel => {
		try {
			const response = responseMap[channel.claim_id];
			const subCount = response
				? JSON.parse(response.body)?.data?.[0] ?? 0
				: 0;
			return lbryChannelToPlatformChannel(channel, subCount);
		} catch (error) {
			trace(`Error processing channel ${channel.claim_id}: ${error}`);
			return null;
		}
	}).filter(channel => channel !== null);
}
function resolveClaimsVideo(claims) {
	if (!claims || claims.length == 0) {
		return [];
	}
	const results = resolveClaims(claims);
	return lbryVideosToPlatformVideos(results);
}
function resolveClaimsBody(claims) {
	return JSON.stringify({ method: "resolve", params: { urls: claims } });
}
function parseResolveClaimsResponse(resp, claims) {
	const claimResults = parseLbryResult(resp, "Resolve claims");
	const results = [];
	for (let i = 0; i < claims.length; i++) {
		const claim = claimResults[claims[i]];
		// Per-claim failures come back as {error: {...}} objects, which are truthy but
		// carry no claim fields; passing them on crashes every downstream converter.
		if (!claim) {
			continue;
		}
		if (claim.error) {
			trace(`Claim did not resolve: ${claims[i]} (${claim.error.name ?? "unknown error"})`);
			continue;
		}
		results.push(claim);
	}
	return results;
}
function resolveClaims(claims) {
	const resp = http.POST(URL_RESOLVE, resolveClaimsBody(claims), JSON_HEADERS);
	return parseResolveClaimsResponse(resp, claims);
}


//Convert a LBRY Channel (claim) to a PlatformChannel
function lbryChannelToPlatformChannel(lbry, subs = 0) {

	let description = lbry.value?.description ?? "";

	// workaround for desktop since currently it does not support new line characters or html breaks/formatting in channel description
	const lineSeparator = IS_ANDROID ? "\n\n" : " | ";

	if(lbry?.value?.email) {
		description += `${lineSeparator}Contact: ${lbry.value.email}`;
	}

	if(lbry?.value?.website_url)
	{
		description += `${lineSeparator}Site: ${lbry.value.website_url}`;	
	}

	if(lbry?.value?.tags) {
		description += `${lineSeparator}Tags: ${lbry.value.tags.join(", ")}`;
	}

	if(lbry?.value?.languages?.length) {
		
		let languages = lbry.value.languages.map(languageCode => {
			return LANGUAGE_CODES[languageCode] ?? languageCode;
		});

		if(languages.length) {
			description += `${lineSeparator}Languages: ${languages.join(", ")}`;
		}
	}

	if(lbry?.meta?.claims_in_channel) {
		description += `${lineSeparator}Total Uploads: ${lbry.meta.claims_in_channel}`;
	}

	if(lbry?.meta?.creation_timestamp) {
		description += `${lineSeparator}Created At: ${new Date(lbry.meta.creation_timestamp * 1000).toLocaleDateString()}`;
	}

	if(lbry.canonical_url) {
		description += `${lineSeparator}URL: ${lbry.canonical_url}`;
	}
	
	if(lbry.claim_id) {
		description += `${lineSeparator}Claim ID: ${lbry.claim_id}`;
	}

	if(lbry?.meta?.effective_amount) {
		description += `${lineSeparator}Staked Credits: ${lbry.meta.effective_amount} LBC`;
	}

	const odyseeUrl = format_odysee_channel_url(lbry.normalized_name, lbry.claim_id);

	return new PlatformChannel({
		id: new PlatformID(PLATFORM, lbry.claim_id, plugin.config.id, PLATFORM_CLAIMTYPE),
		name: lbry.value?.title ?? lbry?.name ?? '',
		thumbnail: lbry.value?.thumbnail?.url ?? "",
		banner: lbry.value?.cover?.url,
		subscribers: subs,
		description,
		url: lbry.permanent_url,
		urlAlternatives: [
			lbry.canonical_url,
			odyseeUrl
		],
		links: {}
	});
}

//Convert a LBRY Video (claim) to a PlatformVideo
function lbryVideoToPlatformVideo(lbry, viewCountMap = null) {
	const shareUrl = lbry.signing_channel?.claim_id !== undefined
		? format_odysee_share_url(lbry.signing_channel.name, lbry.signing_channel.claim_id, lbry.name, lbry.claim_id)
		: format_odysee_share_url_anonymous(lbry.name, lbry.claim_id)

	let viewCount = 0;
	
	// Use batch view count if provided, otherwise fall back to individual request
	if (viewCountMap && viewCountMap.has(lbry.claim_id)) {
		viewCount = viewCountMap.get(lbry.claim_id);
	} else if (localSettings.extraRequestToLoadViewCount && viewCountMap === null) {
		const metrics = lbryToMetrics(lbry, { loadViewCount: true });
		viewCount = metrics.viewCount;
	}

	return new PlatformVideo({
		id: new PlatformID(PLATFORM, lbry.claim_id, plugin.config.id),
		name: lbry.value?.title ?? "",
		thumbnails: new Thumbnails([new Thumbnail(lbry.value?.thumbnail?.url, 0)]),
		author: channelToPlatformAuthorLink(lbry),
		datetime: lbryVideoToDateTime(lbry),
		duration: lbryToDuration(lbry),
		viewCount: viewCount,
		url: lbry.permanent_url,
		shareUrl,
		isLive: false,
		isShort: lbry.value?.video?.height > lbry.value?.video?.width,
		links: {}
	});
}

function lbryVideosToPlatformVideos(lbryVideos) {
	if (!lbryVideos || lbryVideos.length === 0) {
		return [];
	}

	let viewCountMap = null;
	if (localSettings.extraRequestToLoadViewCount) {
		const claimIds = lbryVideos.map(lbry => lbry.claim_id);
		const authToken = ensureAuthToken();
		const requests = claimIds.map(claimId => ({
			url: URL_VIEW_COUNT,
			headers: FORM_URLENCODED_HEADERS,
			body: `auth_token=${authToken}&claim_id=${claimId}`
		}));
		const responses = batchRequest(requests, { useStateCache: true });
		viewCountMap = new Map();
		responses.forEach((response, index) => {
			if (!response?.isOk) {
				trace(`Load view count failed for ${claimIds[index]} (${response?.code ?? ""})${response?.body ? ` - ${response.body}` : ""}`);
				return;
			}
			try {
				const viewCountObj = JSON.parse(response.body);
				if (viewCountObj?.success && viewCountObj?.data) {
					viewCountMap.set(claimIds[index], viewCountObj.data[0] ?? 0);
				}
			} catch (error) {
				trace(`Error parsing view count for ${claimIds[index]}: ${error}`);
			}
		});
	}

	return lbryVideos.map(lbry => lbryVideoToPlatformVideo(lbry, viewCountMap));
}

function lbryDocumentToPlatformPost(lbry, postContent) {
	const shareUrl = lbry.signing_channel?.claim_id !== undefined
		? format_odysee_share_url(lbry.signing_channel.name, lbry.signing_channel.claim_id, lbry.name, lbry.claim_id)
		: format_odysee_share_url_anonymous(lbry.name, lbry.claim_id);

	const sdHash = lbry.value?.source?.sd_hash;
	const sdHashPrefix = sdHash ? sdHash.substring(0, 6) : "";

	if (!postContent) {
		// Odysee get the markdown content like this...
		const res = http.GET(`https://player.odycdn.com/v6/streams/${lbry.claim_id}/${sdHashPrefix}.mp4`, headersToAdd);
		if (res.isOk) {
			postContent = res.body;
		} else {
			trace(`Load document content failed (${res.code})${res.body ? ` - ${res.body}` : ""}`);
		}
	}

	let content;
	const mediaType = lbry?.value?.source?.media_type;

	let images = [];

	switch (mediaType) {
		case 'text/markdown':
			content = markdownToHtml(postContent);
			images = extractImagesFromMarkdown(postContent);
			break;
		case 'text/plain':
			// Delivered to the app as Type.Text.HTML, so plain text has to be escaped or
			// its markup is interpreted instead of shown.
			content = escapeHtml(postContent);
			break;
		case 'text/html':
			content = postContent; // Already HTML
			images = extractImagesFromMarkdown(postContent);
			break;
		default:
			trace(`Unhandled media type: ${mediaType}, treating as plain text`);
			content = escapeHtml(postContent);
			break;
	}

	const {
		rating,
		subCount
	} = lbryToMetrics(lbry, { loadSubCount: true, loadRating: true });

	const platformPostDef = {
		id: new PlatformID(PLATFORM, lbry.claim_id, plugin.config.id),
		name: lbry.value?.title ?? "",
		author: channelToPlatformAuthorLink(lbry, subCount),
		datetime: lbryVideoToDateTime(lbry),
		url: shareUrl,
		rating: rating,
		textType: Type.Text.HTML,
		content: content,
		thumbnails: []
	};

	if (!images.length && lbry.value?.thumbnail?.url) {
		images.push(lbry.value?.thumbnail?.url);
	}

	images.forEach((imageUrl, idx) => {
		platformPostDef.thumbnails.push(new Thumbnails([new Thumbnail(imageUrl, idx)]));
	})

	platformPostDef.images = images;

	return new PlatformPostDetails(platformPostDef);
}

// Percent-encode a claim name to RFC 3986 unreserved; encodeURIComponent exempts !'()*
function encodeClaimNameForUrl(name) {
	return encodeURIComponent(name)
		.replace(/[!'()*]/g, char => `%${char.charCodeAt(0).toString(16).toUpperCase()}`);
}
// Odysee addresses a claim by a prefix of its 40-character id, and the length depends on
// what the claim is: one character for content, two for a channel. Pass the full id.
function contentClaimIdPrefix(claim_id) {
	return claim_id.slice(0, CLAIM_ID_PREFIX_CONTENT)
}
function channelClaimIdPrefix(claim_id) {
	return claim_id.slice(0, CLAIM_ID_PREFIX_CHANNEL)
}
// Single source of truth for the odysee.com channel segment shape: /@handle:cc
function format_odysee_channel_path(channel_slug, channel_id) {
	const channel_handle = encodeClaimNameForUrl(channel_slug.replace(/^@/, ''))
	return `/@${channel_handle}:${channelClaimIdPrefix(channel_id)}`
}
// Single source of truth for the odysee.com path shape: /@handle:cc/name:v
function format_odysee_path(channel_slug, channel_id, video_slug, video_id) {
	const video_segment = `${encodeClaimNameForUrl(video_slug)}:${contentClaimIdPrefix(video_id)}`
	if (!channel_slug || !channel_id) {
		return `/${video_segment}`
	}
	return `${format_odysee_channel_path(channel_slug, channel_id)}/${video_segment}`
}
function format_odysee_channel_url(channel_slug, channel_id) {
	return `${URL_BASE}${format_odysee_channel_path(channel_slug, channel_id)}`
}
function format_odysee_share_url_anonymous(video_name, video_claim_id) {
	return `${URL_BASE}${format_odysee_path(null, null, video_name, video_claim_id)}`
}
function format_odysee_share_url(channel_name, channel_claim_id, video_name, video_claim_id) {
	return `${URL_BASE}${format_odysee_path(channel_name, channel_claim_id, video_name, video_claim_id)}`
}
//Convert an LBRY Video to a PlatformVideoDetail
function buildUrlTestRequests(urls) {
	return urls.map(url => ({
		method: 'GET',
		url,
		headers: { ...headersToAdd, 'Range': 'bytes=0-0' },
		auth: false
	}));
}
function parseUrlTestResponses(responses, urls) {
	const results = new Map();
	urls.forEach((url, i) => {
		results.set(url, { accessible: responses[i]?.isOk ?? false, response: responses[i] });
	});
	return results;
}
function lbryVideoDetailToPlatformVideoDetails(lbry) {

	let isLive = false;
	const sdHash = lbry.value?.source?.sd_hash;
	const claimId = lbry.claim_id;
	const videoHeight = lbry.value?.video?.height ?? 0;
	const videoWidth = lbry.value?.video?.width ?? 0;
	const streamType = lbry.value?.stream_type;
	const mediaType = lbry.value?.source?.media_type;
	const name = lbry.name;
	let video = null;

	// Helper function to get video duration
	const getVideoDuration = () => lbryToDuration(lbry);

	// Metrics (reactions, sub count, view count) run independently of URL tests,
	// so we piggy-back them on whichever URL-test batch we end up issuing.
	const metricsOpts = { loadViewCount: true, loadSubCount: true, loadRating: true };
	const metricsReqs = buildMetricsRequests(lbry, metricsOpts);
	let prefetchedMetrics = null;
	const batchTestUrlsWithMetrics = (urls) => {
		const urlReqs = buildUrlTestRequests(urls);
		const combined = batchRequest([...urlReqs, ...metricsReqs]);
		prefetchedMetrics = parseMetricsResponses(combined.slice(urls.length), lbry, metricsOpts);
		return parseUrlTestResponses(combined.slice(0, urls.length), urls);
	};
	const testUrlAccessibilityWithMetrics = (url) => batchTestUrlsWithMetrics([url]).get(url);

	if (!sdHash) {
		// Handle case with no sdHash
		if (streamType === 'video') {
			// Legacy URL format without sdHash
			const legacyUrl = `https://cdn.lbryplayer.xyz/content/claims/${name}/${claimId}/stream`;

			const urlTest = testUrlAccessibilityWithMetrics(legacyUrl);
			if (!urlTest.accessible) {
				throw new UnavailableException("Video source is not accessible");
			}

			video = new VideoSourceDescriptor([
				new VideoUrlSource({
					name: `Original ${videoHeight}P`,
					url: legacyUrl,
					width: videoWidth,
					height: videoHeight,
					duration: getVideoDuration(),
					container: mediaType ?? "",
					requestModifier: { headers: headersToAdd }
				})
			]);
		} else if (lbry.value?.video === undefined) {
			const channelClaimId = lbry.signing_channel?.claim_id;
				if (!channelClaimId) {
					throw new UnavailableException("Odysee live streams are not currently supported");
				}
			const liveHlsUrl = `https://cloud.odysee.live/content/${channelClaimId}/master.m3u8`;
			const urlTest = testUrlAccessibilityWithMetrics(liveHlsUrl);
				if (!urlTest?.accessible) {
					throw new UnavailableException("This live stream is currently offline");
				}
			isLive = true;
			video = new VideoSourceDescriptor([
				new HLSSource({
					name: "Live",
					url: liveHlsUrl,
					duration: 0,
					requestModifier: { headers: headersToAdd }
				})
			]);
		}
	} else {
		// With sdHash present, handle both audio and video
		if (streamType === 'audio') {
			const audioUrl = `https://player.odycdn.com/v6/streams/${claimId}/${sdHash}.mp4`;

			const urlTest = testUrlAccessibilityWithMetrics(audioUrl);
			if (!urlTest.accessible) {
				throw new UnavailableException("Audio source is not accessible");
			}

			const sources = [
				new AudioUrlSource({
					name: mediaType,
					url: audioUrl,
					container: mediaType,
					duration: getVideoDuration(),
					requestModifier: { headers: headersToAdd }
				})
			];
			video = new UnMuxVideoSourceDescriptor([], sources);
		}
		else if (streamType === 'video') {
			const sources = [];
			const sdHashPrefix = sdHash.substring(0, 6);

			// Prepare all URLs to test
			const urlsToTest = [];
			const downloadUrlV6 = `https://player.odycdn.com/v6/streams/${claimId}/${sdHashPrefix}.mp4`;
			const downloadUrlV4 = `https://player.odycdn.com/api/v4/streams/free/${name}/${claimId}/${sdHashPrefix}`;
			const downloadUrlV3 = `https://player.odycdn.com/api/v3/streams/free/${name}/${claimId}/${sdHashPrefix}`;
			const hlsUrlV6 = `https://player.odycdn.com/v6/streams/${claimId}/${sdHash}/master.m3u8`;
			const hlsUrlV4 = `https://player.odycdn.com/api/v4/streams/tc/${name}/${claimId}/${sdHash}/master.m3u8`;

			// Members-only content
			if(getIsMemberOnlyClaim(lbry)) {
				const membersOnlyUrl = getStreamingSourceUrl(lbry);
				urlsToTest.push(membersOnlyUrl);
			}

			// Test HLS and MP4 URLs (each gated by its user-controlled setting)
			const enableHlsV6 = localSettings.enableHlsV6Source ?? true;
			const enableHlsV4 = localSettings.enableHlsV4Source ?? false;
			const enableMp4V6 = localSettings.enableMp4V6Source ?? true;
			const enableMp4V4 = localSettings.enableMp4V4Source ?? false;
			const enableMp4V3 = localSettings.enableMp4V3Source ?? true;
			if (enableHlsV6) urlsToTest.push(hlsUrlV6);
			if (enableHlsV4) urlsToTest.push(hlsUrlV4);
			if (enableMp4V6) urlsToTest.push(downloadUrlV6);
			if (enableMp4V4) urlsToTest.push(downloadUrlV4);
			if (enableMp4V3) urlsToTest.push(downloadUrlV3);

			const urlTests = batchTestUrlsWithMetrics(urlsToTest);

			// Add members-only source if accessible
			if(getIsMemberOnlyClaim(lbry)) {
				const membersOnlyUrl = getStreamingSourceUrl(lbry);
				const urlTest = urlTests.get(membersOnlyUrl);
				if (!urlTest || !urlTest.accessible) {
					trace("Members-only video source is not accessible");
				} else {
					sources.push(new VideoUrlSource({
						name: mediaType ?? "video/mp4",
						url: membersOnlyUrl,
						width: videoWidth,
						height: videoHeight,
						duration: getVideoDuration(),
						container: mediaType ?? "video/mp4",
						requestModifier: { headers: headersToAdd }
					}));
				}
			}

			// Add HLS sources only if enabled and accessible (older videos may not have HLS)
			const hlsTestV6 = enableHlsV6 ? urlTests.get(hlsUrlV6) : null;
			const hlsTestV4 = enableHlsV4 ? urlTests.get(hlsUrlV4) : null;
			const hasWorkingHLS = !!((hlsTestV6 && hlsTestV6.accessible) || (hlsTestV4 && hlsTestV4.accessible));

			// Preferred-source priority: 0 = Auto (HLS preferred when available),
			// 1 = Fastest start (MP4 preferred over HLS)
			// 0 = Adaptive (HLS when available), 1 = Fixed quality (MP4 v6)
			const fixedQuality = parseInt(localSettings.preferredSourcePriorityIndex ?? 0) === 1;
			const adaptivePriority = !fixedQuality && hasWorkingHLS;
			const fixedPriority = fixedQuality;

			// Each probe response exposes `response.url` which equals the final URL after
			// any CDN redirects (or the requested URL if no redirect). Using it as the
			// source URL means the player skips the 308 round-trip on every subsequent
			// byte/segment request (mp4_v4 is the only endpoint that redirects today).
			if (enableHlsV6 && hlsTestV6 && hlsTestV6.accessible) {
				sources.push(new HLSSource({
					name: "HLS (v6)",
					url: hlsTestV6.response.url ?? hlsUrlV6,
					duration: getVideoDuration(),
					priority: adaptivePriority,
					requestModifier: { headers: headersToAdd }
				}));
			}

			if (enableHlsV4 && hlsTestV4 && hlsTestV4.accessible) {
				sources.push(new HLSSource({
					name: "HLS (v4)",
					url: hlsTestV4.response.url ?? hlsUrlV4,
					duration: getVideoDuration(),
					priority: adaptivePriority,
					requestModifier: { headers: headersToAdd }
				}));
			}

			// Add MP4 v6 source if enabled and accessible
			if (enableMp4V6) {
				const urlTestV6 = urlTests.get(downloadUrlV6);
				if (urlTestV6 && urlTestV6.accessible) {
					const srcV6 = new VideoUrlSource({
						name: `Original ${videoHeight}P (v6)`,
						url: urlTestV6.response.url ?? downloadUrlV6,
						width: videoWidth,
						height: videoHeight,
						duration: getVideoDuration(),
						container: urlTestV6.response.headers["content-type"]?.[0] ?? "video/mp4",
						requestModifier: { headers: headersToAdd }
					});
					srcV6.priority = fixedPriority;
					sources.push(srcV6);
				}
			}

			// Add MP4 v4 source if enabled and accessible.
			// The api/v4/streams/free endpoint 308-redirects to an HLS playlist when the
			// video has HLS encoding available. We detect the HLS case via Content-Type
			// and emit an HLSSource pointing at the final (redirected) URL so the player
			// bypasses the 308.
			if (enableMp4V4) {
				const urlTestV4 = urlTests.get(downloadUrlV4);
				if (urlTestV4 && urlTestV4.accessible) {
					const finalUrlV4 = urlTestV4.response.url ?? downloadUrlV4;
					const ctV4 = urlTestV4.response.headers["Content-Type"]?.[0]
						?? urlTestV4.response.headers["content-type"]?.[0]
						?? "";
					const isHlsV4 = /mpegurl|m3u8/i.test(ctV4);
					if (isHlsV4) {
						sources.push(new HLSSource({
							name: "HLS (v4, auto)",
							url: finalUrlV4,
							duration: getVideoDuration(),
							priority: adaptivePriority,
							requestModifier: { headers: headersToAdd }
						}));
					} else {
						const srcV4 = new VideoUrlSource({
							name: `Original ${videoHeight}P (v4)`,
							url: finalUrlV4,
							width: videoWidth,
							height: videoHeight,
							duration: getVideoDuration(),
							container: ctV4 || "video/mp4",
							requestModifier: { headers: headersToAdd }
						});
						srcV4.priority = fixedPriority;
						sources.push(srcV4);
					}
				}
			}

			// Add MP4 v3 source if enabled and accessible
			if (enableMp4V3) {
				const urlTestV3 = urlTests.get(downloadUrlV3);
				if (urlTestV3 && urlTestV3.accessible) {
					const srcV3 = new VideoUrlSource({
						name: `Original ${videoHeight}P (v3)`,
						url: urlTestV3.response.url ?? downloadUrlV3,
						width: videoWidth,
						height: videoHeight,
						duration: getVideoDuration(),
						container: urlTestV3.response.headers["content-type"]?.[0] ?? "video/mp4",
						requestModifier: { headers: headersToAdd }
					});
					srcV3.priority = fixedPriority;
					sources.push(srcV3);
				}
			}

			// Zero sources has three distinct causes; reporting all of them as
			// members-only sends users looking for a membership they do not need.
			if (sources.length === 0) {
				if (getIsMemberOnlyClaim(lbry)) {
					throw new UnavailableException("Members Only Content Is Not Currently Supported");
				}
				if (urlsToTest.length === 0) {
					throw new UnavailableException("No video sources are enabled. Enable at least one under Odysee plugin settings > Video Sources.");
				}
				const probeCodes = urlsToTest.map(url => urlTests.get(url)?.response?.code ?? "no response");
				trace(`No accessible source for ${claimId}: ${urlsToTest.map((url, index) => `${url} -> ${probeCodes[index]}`).join(", ")}`);
				// A claim carrying a fee answers 402 until it is bought on Odysee. Both halves are
				// required: a paid claim that 404s is genuinely missing, not purchasable.
				const fee = lbry.value?.fee;
				const paymentRequired = probeCodes.some((code) => Number(code) === 402);
				if (paymentRequired && parseFloat(fee?.amount ?? "0") > 0 && !lbry.purchase_receipt) {
					throw new UnavailableException(`This video must be purchased on Odysee (${fee.amount} ${fee.currency}) before it can be played.`);
				}
				const distinctCodes = probeCodes.filter((code, index) => probeCodes.indexOf(code) === index);
				throw new UnavailableException(`This video has no playable source right now (CDN responded ${distinctCodes.join(", ")}).`);
			}

			video = new VideoSourceDescriptor(sources);
		}
		else if (lbry.value?.video === undefined) {
			const channelClaimId = lbry.signing_channel?.claim_id;
			if (!channelClaimId) {
				throw new UnavailableException("Odysee live streams are not currently supported");
			}
			const liveHlsUrl = `https://cloud.odysee.live/content/${channelClaimId}/master.m3u8`;
			const urlTest = testUrlAccessibilityWithMetrics(liveHlsUrl);
			if (!urlTest?.accessible) {
				throw new UnavailableException("This live stream is currently offline");
			}
			isLive = true;
			video = new VideoSourceDescriptor([
				new HLSSource({
					name: "Live",
					url: liveHlsUrl,
					duration: 0,
					requestModifier: { headers: headersToAdd }
				})
			]);
		}
	}

	const { rating, viewCount, subCount } = prefetchedMetrics ?? lbryToMetrics(lbry, metricsOpts);


	// Generate share URL
	const shareUrl = lbry?.signing_channel?.claim_id
		? format_odysee_share_url(lbry.signing_channel.name, lbry.signing_channel?.claim_id, name, claimId)
		: format_odysee_share_url_anonymous(name, claimId);

	// Build description with tags
	let description = lbry.value?.description ?? "";
	const tags = lbry.value?.tags ?? [];

	if (tags.length > 0) {
		if (IS_ANDROID) {
			// Android: Add clickable tag links
			const tagLinks = tags.map(tag => `<a href="https://odysee.com/$/discover?t=${encodeURIComponent(tag)}">#${tag}</a>`).join(' ');
			description += `\n\n${tagLinks}`;
		} else {
			// Desktop: Add plain text tags (links not yet supported)
			const tagText = tags.map(tag => `#${tag}`).join(' ');
			description += `\n\n${tagText}`;
		}
	}

	// Return the final video details object
	return new PlatformVideoDetails({
		id: new PlatformID(PLATFORM, claimId, plugin.config.id),
		name: lbry.value?.title ?? "",
		thumbnails: new Thumbnails([new Thumbnail(lbry.value?.thumbnail?.url, 0)]),
		author: channelToPlatformAuthorLink(lbry, subCount),
		datetime: lbryVideoToDateTime(lbry),
		duration: getVideoDuration(),
		viewCount,
		url: lbry.permanent_url,
		shareUrl,
		isLive,
		description: description,
		rating,
		video
	});
}

function channelToPlatformAuthorLink(lbry, subCount) {
	if (lbry.signing_channel?.claim_id) {
		return new PlatformAuthorLink(
			new PlatformID(PLATFORM, lbry.signing_channel?.claim_id, plugin.config.id, PLATFORM_CLAIMTYPE),
			lbry.signing_channel?.value?.title ?? lbry.signing_channel?.name ?? '',
			lbry.signing_channel?.permanent_url ?? "",
			lbry.signing_channel?.value?.thumbnail?.url ?? "",
			subCount
		)
	} else {
		return EMPTY_AUTHOR;
	}
}

function lbryVideoToDateTime(lbry) {
	return parseInt(lbry?.value?.release_time ?? lbry?.timestamp ?? 0)
}

function lbryToDuration(lbry){
	return lbry.value?.video?.duration ?? lbry.value?.audio?.duration ?? 0;
}

function buildMetricsRequests(lbry, opts = {}) {
	const claimId = lbry.claim_id;
	const authToken = ensureAuthToken();
	const channelClaimId = lbry.signing_channel?.claim_id;
	const req = (url, body) => ({ url, headers: FORM_URLENCODED_HEADERS, body });

	return [
		opts.loadRating	? req(URL_REACTIONS,  `claim_ids=${claimId}`) : null,
		opts.loadSubCount && channelClaimId ? req(URL_API_SUB_COUNT, `auth_token=${authToken}&claim_id=${channelClaimId}`) : null,
		opts.loadViewCount ? req(URL_VIEW_COUNT, `auth_token=${authToken}&claim_id=${claimId}`) : null,
	];
}

function parseMetricsResponses(responses, lbry, opts = {}) {
	const [reactionResp, subCountResp, viewCountResp] = responses;
	const claimId = lbry.claim_id;
	let rating = null, viewCount = 0, subCount = 0;

	if (opts.loadRating) {
		if (reactionResp?.isOk) {
			const obj = JSON.parse(reactionResp.body);
			const data = obj?.data?.others_reactions?.[claimId];
			if (obj?.success && data) {
				rating = new RatingLikesDislikes(data.like ?? 0, data.dislike ?? 0);
			}
		} else {
			trace(`Load reactions failed (${reactionResp?.code ?? ""})${reactionResp?.body ? ` - ${reactionResp.body}` : ""}`);
		}
	}
	if (opts.loadViewCount) {
		if (viewCountResp?.isOk) {
			const obj = JSON.parse(viewCountResp.body);
			if (obj?.success && obj?.data) {
				viewCount = obj.data[0] ?? 0;
			}
		} else {
			trace(`Load detail view count failed (${viewCountResp?.code ?? ""})${viewCountResp?.body ? ` - ${viewCountResp.body}` : ""}`);
		}
	}
	if (opts.loadSubCount && subCountResp !== null) {
		if (subCountResp?.isOk) {
			const obj = JSON.parse(subCountResp.body);
			if (obj?.success && obj?.data) {
				subCount = obj.data[0] ?? 0;
			}
		} else {
			trace(`Load subscriber count failed (${subCountResp?.code ?? ""})${subCountResp?.body ? ` - ${subCountResp.body}` : ""}`);
		}
	}
	return { rating, viewCount, subCount };
}

function lbryToMetrics(lbry, opts = { loadViewCount: false, loadSubCount: false, loadRating: false }) {
	const requests = buildMetricsRequests(lbry, opts);
	const responses = batchRequest(requests, { useStateCache: true });
	return parseMetricsResponses(responses, lbry, opts);
}

// Escape text that is about to be placed in an HTML context. markdownToHtml re-adds a
// controlled subset of markup after escaping; every other text type stays escaped.
function escapeHtml(text) {
	return String(text ?? '')
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
		.replace(/'/g, '&#039;');
}

/**
 * Converts Markdown text to HTML
 * @param {string} markdown - The markdown text to convert
 * @returns {string} The converted HTML
 */
function markdownToHtml(markdown) {
	if (!markdown) {
		return '';
	}

	// Preprocessing - normalize line endings
	let html = markdown.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

	// First, escape all HTML to prevent injection attacks
	html = escapeHtml(html);

	// The sentinel below marks stashed links, so it must not survive from the source text.
	html = html.replace(/%%MDLINK\d+%%/g, '');

	// Process code blocks (need to handle these first)
	html = html.replace(/```([a-z]*)\n([\s\S]*?)\n```/g, function(match, language, code) {
		return `<pre><code class="language-${language}">${code}</code></pre>`;
	});

	// Process inline code (already escaped)
	html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

	// Process headings (# Heading, ## Heading, etc)
	html = html.replace(/^(#{1,6})\s+(.*?)$/gm, function(match, hashes, content) {
		const level = hashes.length;
		return `<h${level}>${content.trim()}</h${level}>`;
	});

	// Links and images are stashed before emphasis runs, otherwise a URL holding two
	// underscores has an <em> spliced into its own href and the link breaks.
	const stashedLinks = [];
	const stashLink = function (replacement) {
		stashedLinks.push(replacement);
		return `%%MDLINK${stashedLinks.length - 1}%%`;
	};

	// Process images ![alt](url) - must run before links, which would otherwise consume
	// the [alt](url) half of the same match and leave a stray "!" behind
	html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, function(match, alt, url) {
		if (isValidUrl(url)) {
			return stashLink(`<img src="${sanitizeUrl(url)}" alt="${alt}" loading="lazy">`);
		} else {
			return `[Image: ${alt}]`; // Fallback for invalid URLs
		}
	});

	// Process links [text](url) - with URL validation
	html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, function(match, text, url) {
		// Validate and sanitize URLs
		if (isValidUrl(url)) {
			return stashLink(`<a href="${sanitizeUrl(url)}" rel="noopener noreferrer">${text}</a>`);
		} else {
			return text; // If URL is invalid, just show the text
		}
	});

	// Process automatic links (bare URLs) with validation. The lookbehind keeps this off
	// the (url) half of a markdown link, which the two passes above already consumed.
	html = html.replace(/(?<!["\(])(https?:\/\/[^\s<]+)(?!["\)])/g, function(match, url) {
		if (isValidUrl(url)) {
			return stashLink(`<a href="${sanitizeUrl(url)}" rel="noopener noreferrer">${url}</a>`);
		} else {
			return url; // If URL is invalid, just show the text
		}
	});

	// Process bold (** or __)
	html = html.replace(/(\*\*|__)(.*?)\1/g, '<strong>$2</strong>');

	// Process italic (* or _)
	html = html.replace(/(\*|_)(.*?)\1/g, '<em>$2</em>');

	// Process horizontal rules
	html = html.replace(/^([-*_])\1\1+$/gm, '<hr>');

	// Process unordered lists
	let inList = false;
	let listHtml = '';

	html = html.split('\n').map(line => {
		const listMatch = line.match(/^[\*\-\+]\s+(.*)$/);
		if (listMatch) {
			if (!inList) {
				inList = true;
				listHtml = '<ul>';
			}
			listHtml += `<li>${listMatch[1]}</li>`;
			return null; // Mark for removal
		} else if (inList && line.trim() === '') {
			inList = false;
			const result = listHtml + '</ul>';
			listHtml = '';
			return result;
		} else if (inList) {
			inList = false;
			const result = listHtml + '</ul>';
			listHtml = '';
			return result + '\n' + line;
		}
		return line;
	}).filter(line => line !== null).join('\n');

	// Clean up any remaining list
	if (inList) {
		html += listHtml + '</ul>';
	}

	// Process ordered lists (similar approach to unordered lists)
	inList = false;
	listHtml = '';

	html = html.split('\n').map(line => {
		const listMatch = line.match(/^\d+\.\s+(.*)$/);
		if (listMatch) {
			if (!inList) {
				inList = true;
				listHtml = '<ol>';
			}
			listHtml += `<li>${listMatch[1]}</li>`;
			return null; // Mark for removal
		} else if (inList && line.trim() === '') {
			inList = false;
			const result = listHtml + '</ol>';
			listHtml = '';
			return result;
		} else if (inList) {
			inList = false;
			const result = listHtml + '</ol>';
			listHtml = '';
			return result + '\n' + line;
		}
		return line;
	}).filter(line => line !== null).join('\n');

	// Clean up any remaining list
	if (inList) {
		html += listHtml + '</ol>';
	}

	// Process blockquotes - matches the escaped form, since the > was replaced above
	html = html.replace(/^&gt;\s+(.*)$/gm, '<blockquote>$1</blockquote>');

	// Process paragraphs (any text between blank lines that isn't a special element)
	let inParagraph = false;
	let paragraphContent = '';

	html = html.split('\n').map(line => {
		if (line.trim() === '') {
			if (inParagraph) {
				inParagraph = false;
				const result = `<p>${paragraphContent}</p>`;
				paragraphContent = '';
				return result;
			}
			return '';
		} else if (line.startsWith('<') && !inParagraph) {
			// Skip lines that already have HTML tags
			return line;
		} else {
			if (!inParagraph) {
				inParagraph = true;
				paragraphContent = line;
			} else {
				paragraphContent += ' ' + line;
			}
			return null; // Mark for removal
		}
	}).filter(line => line !== null).join('\n');

	// Clean up any remaining paragraph
	if (inParagraph) {
		html += `<p>${paragraphContent}</p>`;
	}

	// Put back the links and images stashed before emphasis processing. A link whose text
	// holds an image nests one sentinel inside another, so restore until none are left.
	for (let pass = 0; pass <= stashedLinks.length && html.includes('%%MDLINK'); pass++) {
		html = html.replace(/%%MDLINK(\d+)%%/g, function(match, index) {
			return stashedLinks[Number(index)] ?? '';
		});
	}

	return html;
}

/**
 * Validates a URL to ensure it uses an acceptable protocol
 * @param {string} url - The URL to validate
 * @returns {boolean} Whether the URL is valid
 */
function isValidUrl(url) {
	// Basic URL validation
	try {
		const parsedUrl = new URL(url);
		// Only allow http, https protocols (no javascript:, data:, etc.)
		return ['http:', 'https:','lbry:'].includes(parsedUrl.protocol);
	} catch (e) {
		// If URL is malformed, consider it invalid
		return false;
	}
}

/**
 * Sanitizes a URL to prevent XSS attacks
 * @param {string} url - The URL to sanitize
 * @returns {string} The sanitized URL
 */
function sanitizeUrl(url) {
	// Ensure URL is a string
	url = String(url);

	try {
		// Parse the URL to get components
		const parsedUrl = new URL(url);

		// Check for potentially dangerous protocols
		if (!['http:', 'https:','lbry:'].includes(parsedUrl.protocol)) {
			return '#'; // Return harmless link
		}

		// Return the original URL if it passed our checks
		return url;
	} catch (e) {
		// If URL parsing fails, return a harmless link
		return '#';
	}
}

function loadOptionsForSetting(settingKey) {
	return localConfig?.settings?.find((s) => s.variable == settingKey)
		?.options ?? [];
}


/**
 * Executes multiple HTTP requests in a batch with optional caching
 * @param {Array} requests - Array of request objects
 * @param {Object} opts - Options object
 * @param {boolean} [opts.useStateCache=false] - Whether to use state caching
 * @returns {Array} - Array of responses corresponding to the requests
 */
function batchRequest(requests, opts = {}) {
	// Default to using cache if not specified
	const useStateCache = opts.useStateCache !== undefined ? opts.useStateCache : false;

	// Initialize cache if it doesn't exist
	if (!localState.batch_response_cache) {
		localState.batch_response_cache = {};
	}

	let batch = http.batch();
	let cacheHits = {};
	let batchRequestIndices = [];
	let batchRequestCount = 0;

	// First pass: identify cache hits and prepare batch for non-cached requests
	for (let i = 0; i < requests.length; i++) {
		const request = requests[i];

		// null entries are placeholders; treat as a null response without making a request
		if (request === null) {
			cacheHits[i] = null;
			continue;
		}

		// Validate request
		if (!request.url) {
			throw new ScriptException('An HTTP request must have a URL');
		}

		// Determine method and create request key
		const hasBody = !!request.body;
		const method = request.method || (hasBody ? 'POST' : 'GET');
		const requestKey = hasBody ?
			`${method}${request.url}${JSON.stringify(request.body)}` :
			`${method}${request.url}`;

		// Store the request key for later use
		request.requestKey = requestKey;

		// Check cache if caching is enabled. Entries restored from a state saved by an older
		// version carry no ts, so the NaN comparison below correctly treats them as expired.
		const cached = useStateCache ? localState.batch_response_cache[requestKey] : null;
		if (cached && Date.now() - cached.ts < BATCH_CACHE_TTL_MS) {
			cacheHits[i] = cached.response;
		} else {
			// Add to batch if not in cache or caching is disabled
			if (!hasBody) {
				batch = batch.request(
					method,
					request.url,
					request.headers || {},
					request.auth || false
				);
			} else {
				batch = batch.requestWithBody(
					method,
					request.url,
					request.body,
					request.headers || {},
					request.auth || false
				);
			}
			// Map the original request index to the batch index
			batchRequestIndices[batchRequestCount] = i;
			batchRequestCount++;
		}
	}

	// Execute batch request only if there are non-cached requests
	let batchResponses = [];
	if (batchRequestCount > 0) {
		try {
			batchResponses = batch.execute();
		} catch (error) {
			throw new ScriptException(`Batch execution failed: ${error.message}`);
		}
	}

	// Prepare final response array
	const finalResponses = new Array(requests.length);

	// Add cache hits to final responses
	for (const [index, response] of Object.entries(cacheHits)) {
		finalResponses[parseInt(index)] = response;
	}

	// Add batch responses to final responses and update cache
	for (let i = 0; i < batchResponses.length; i++) {
		const originalIndex = batchRequestIndices[i];
		const response = batchResponses[i];
		finalResponses[originalIndex] = response;

		// Update cache with new responses if caching is enabled (only cache successful responses)
		if (useStateCache && response?.isOk) {
			const requestKey = requests[originalIndex].requestKey;
			localState.batch_response_cache[requestKey] = { response, ts: Date.now() };
		}
	}

	if (useStateCache) {
		pruneBatchCache();
	}

	return finalResponses;
}

/**
 * Drops expired entries from the batch cache, then the oldest ones if it is still over cap.
 */
function pruneBatchCache() {
	const cache = localState.batch_response_cache;
	const now = Date.now();
	const live = [];

	for (const key of Object.keys(cache)) {
		// Entries with no ts predate the timestamped format and cannot be aged, so drop them
		if (!(now - cache[key]?.ts < BATCH_CACHE_TTL_MS)) {
			delete cache[key];
		} else {
			live.push(key);
		}
	}

	if (live.length <= BATCH_CACHE_MAX_ENTRIES) {
		return;
	}

	live.sort(function (a, b) {
		return cache[a].ts - cache[b].ts;
	});
	const excess = live.length - BATCH_CACHE_MAX_ENTRIES;
	for (let i = 0; i < excess; i++) {
		delete cache[live[i]];
	}
}

function createMultiSourcePager(sourcesConfig = []) {
    class MultiSourceVideoPager extends VideoPager {
        constructor({
            videos = [],
            hasMore = true,
            contexts = {},
            currentSources = new Set()
        } = {}) {
            super(videos, hasMore, { page: 0 });
            this.contexts = contexts;
            this.currentSources = currentSources;
        }
        
        addSource(sourceConfig) {
            // Create a unique identifier for this source based on the request parameters
            const streamTypes = sourceConfig.request_body.stream_types || ["all"];
            const sourceId = `source_${streamTypes.join('_')}_${Date.now() + Math.random()}`;

            if (!this.contexts[sourceId]) {
                // Initialize context for this source if it doesn't exist
                this.contexts[sourceId] = {
                    page: 1, // Start with page 1 for LBRY API
                    page_size: sourceConfig.request_body.page_size || 20,
                    config: sourceConfig,
                    hasMore: true,
                    feedType: sourceConfig.feedType // Store the feed type for filtering
                };
                this.currentSources.add(sourceId);
            }
        }
        
        nextPage() {
            // Clone states to avoid mutation
            const newContexts = {};
            const newCurrentSources = new Set(this.currentSources);
            for (const sourceId of this.currentSources) {
                newContexts[sourceId] = { ...this.contexts[sourceId] };
            }
            
            const batch = http.batch();
            const sourcesToFetch = [];
            
            // Prepare batch requests for sources that have more content
            for (const sourceId of newCurrentSources) {
                const context = newContexts[sourceId];
				if (!context.hasMore) {
					continue;
				}
                
                const { config } = context;
                
                // Create a new request body with updated pagination
                const updatedRequestBody = {
                    ...config.request_body,
                    page: context.page,
                    page_size: context.page_size
                };
                
                const body = JSON.stringify({
                    jsonrpc: "2.0",
                    method: "claim_search",
                    params: updatedRequestBody,
                    id: Date.now() + Math.floor(Math.random() * 1000) // Unique ID for each request
                });
                
                // Add to batch
                batch.POST(URL_CLAIM_SEARCH, body, { "Content-Type": "application/json" });
                sourcesToFetch.push({ 
                    sourceId, 
                    context, 
                    updatedRequestBody,
                    feedType: context.feedType // Include feed type for filtering
                });
            }
            
            // Execute batch requests if there are any
            let responses = [];
            if (sourcesToFetch.length > 0) {
                responses = batch.execute();
                if (responses.length !== sourcesToFetch.length) {
                    throw new ScriptException("Batch response count mismatch");
                }
            }
            
            // Process responses and collect videos
            const allNewVideos = [];
            let hasMoreOverall = false;
            for (let i = 0; i < sourcesToFetch.length; i++) {
                const { sourceId, context, updatedRequestBody, feedType } = sourcesToFetch[i];
                const res = responses[i];
                
                if (!res.isOk) {
					trace(`Request for source ${sourceId} failed (${res.code}) - ${res.body}`);
                    context.hasMore = false; // Stop trying this source
                    continue;
                }
                
                try {
                    const responseBody = JSON.parse(res.body);
                    
                    // Check for errors in the response
                    if (responseBody.error) {
						trace(`API error for source ${sourceId}: ${JSON.stringify(responseBody.error)}`);
                        context.hasMore = false;
                        continue;
                    }
                    
                    if (!responseBody.result || !responseBody.result.items) {
						trace(`Unexpected response format for source ${sourceId}`);
                        context.hasMore = false;
                        continue;
                    }
                    
                    // Get items from the response
                    const items = responseBody.result.items;
                    
                    if (items.length === 0) {
                        // No more items for this source
                        context.hasMore = false;
                        continue;
                    }
                    
                    // Apply client-side filtering based on feed type
                    let filteredItems = items;
                    const shortContentThreshold = parseInt(shortContentThresholdOptions[localSettings.shortContentThresholdIndex] || 60);
                    
                    if (feedType === Type.Feed.Videos) {
                        // For Videos feed, filter out short videos but keep audios and documents
                        filteredItems = items.filter(item => {
                            // If it's not a video, keep it (audio, document, etc.)
                            if (item.value?.stream_type !== "video") {
                                return true;
                            }
                            
                            // For videos, only keep ones longer than the threshold
                            const duration = item.value?.video?.duration || 0;
                            return duration > shortContentThreshold;
                        });
                    }
                    
                    // Process items into platform content
                    const processedContent = claimSearchItemsToPlatformContent(filteredItems);
                    
                    // Log information about the items for debugging
                    if (processedContent.length === 0 && filteredItems.length > 0) {
						trace(`Warning: No content processed from ${filteredItems.length} items for source ${sourceId}`);
						trace(`Stream types in response: ${filteredItems.map(item => item.value?.stream_type).join(', ')}`);
                    }
                    
                    allNewVideos.push(...processedContent);
                    
                    // Determine if this source has more pages
                    const totalPages = responseBody.result.total_pages || 1;
                    const currentPage = updatedRequestBody.page;
                    const hasMoreForSource = currentPage < totalPages && items.length > 0;
                    
                    // Update context for next pagination
                    context.page++;
                    context.hasMore = hasMoreForSource;
                    hasMoreOverall = hasMoreOverall || hasMoreForSource;
                    
                } catch (error) {
					trace(`Error processing response for source ${sourceId}: ${error.message}`);
                    context.hasMore = false; // Stop trying this source on error
                }
            }
            
            // Sort videos by datetime (newest first) if they have datetime
            if (allNewVideos.length > 0 && allNewVideos[0].datetime) {
                allNewVideos.sort((a, b) => b.datetime - a.datetime);
            }
            
            // If no sources have more content, mark as complete
            if (!hasMoreOverall) {
                return new MultiSourceVideoPager({
                    videos: allNewVideos,
                    hasMore: false,
                    contexts: newContexts,
                    currentSources: newCurrentSources
                });
            }
            
            // Return a new pager with the updated state
            return new MultiSourceVideoPager({
                videos: allNewVideos,
                hasMore: hasMoreOverall,
                contexts: newContexts,
                currentSources: newCurrentSources
            });
        }
    }
    
    // Initialize pager and add sources
    const pager = new MultiSourceVideoPager();
    sourcesConfig.forEach(config => pager.addSource(config));
    return pager;
}

function getIsMemberOnlyClaim(lbry) {
	return lbry?.value?.tags?.includes("c:members-only") ?? false;
}

function getStreamingSourceUrl(lbry) {

	const request = JSON.stringify({ 
		"jsonrpc": "2.0", 
		"method": "get", 
		"params": { 
			"uri": lbry.short_url, 
			"environment": "live" 
		}
	});

	const is_member_only_claim = getIsMemberOnlyClaim(lbry);
	const is_logged_in = bridge.isLoggedIn();

	if(is_member_only_claim && !is_logged_in) {
		throw new LoginRequiredException("This content is for members only. Please log in with an account that has an active membership to this channel to view this content.")
	}

	const use_auth = is_member_only_claim && is_logged_in;

	const contentResponse = http.POST(URL_GET, request, JSON_HEADERS, use_auth);

	if(contentResponse.isOk) {
		const body = JSON.parse(contentResponse.body);

		if(!body.error) {
			return body?.result?.streaming_url;
		}
		trace(`Get streaming source returned API error: ${JSON.stringify(body.error)}`);
	} else {
		trace(`Get streaming source failed (${contentResponse.code})${contentResponse.body ? ` - ${contentResponse.body}` : ""}`);
	}
}
/**
 * Extracts image URLs from markdown text
 * @param {string} markdown - The markdown text to parse
 * @returns {string[]} - Array of extracted image URLs
 */
function extractImagesFromMarkdown(content) {
	if (!content) {
		return [];
	}
    
    // Regular expression to match markdown image syntax
    const markdownImageRegex = /!\[.*?\]\((.*?)\)/g;
    
    // Regular expression to match HTML img tags
    const htmlImageRegex = /<img[^>]+src="([^">]+)"/g;
    
    const markdownMatches = [];
    const htmlMatches = [];
    
    // Extract markdown images
    let match;
    while ((match = markdownImageRegex.exec(content)) !== null) {
        markdownMatches.push(match[1]);
    }
    
    // Extract HTML images
    while ((match = htmlImageRegex.exec(content)) !== null) {
        htmlMatches.push(match[1]);
    }
    
    // Combine and deduplicate image URLs
    return [...new Set([...markdownMatches, ...htmlMatches])];
}

/**
 * Logs a message and optionally shows a toast notification.
 * @param {string} msg Message
 * @param {Object} [options] Options
 * @param {boolean} [options.showToast=false] Whether to show a toast notification
 */
function trace(msg, { showToast = false } = {}) {
	if (localSettings?.verboseNotifications || showToast) {
		bridge.toast(msg);
	}
	log(msg);
}

function traceJson(value) {
	const json = JSON.stringify(value);
	trace(json);
	return json;
}

const LANGUAGE_CODES = {
    "en": "English",
    "es": "Spanish",
    "fr": "French",
    "de": "German",
    "it": "Italian",
    "pt": "Portuguese",
    "ru": "Russian",
    "ja": "Japanese",
    "ko": "Korean",
    "zh": "Chinese",
    "ar": "Arabic",
    "hi": "Hindi",
    "bn": "Bengali",
    "ur": "Urdu",
    "tr": "Turkish",
    "fa": "Persian",
    "vi": "Vietnamese",
    "id": "Indonesian",
    "th": "Thai",
    "pl": "Polish",
    "nl": "Dutch",
    "sv": "Swedish",
    "da": "Danish",
    "fi": "Finnish",
    "el": "Greek",
    "hu": "Hungarian",
    "ro": "Romanian",
    "cs": "Czech",
    "sk": "Slovak",
    "no": "Norwegian",
    "nb": "Norwegian Bokmål",
    "nn": "Norwegian Nynorsk",
    "hr": "Croatian",
    "lt": "Lithuanian",
    "lv": "Latvian",
    "et": "Estonian",
    "sl": "Slovenian",
    "bg": "Bulgarian",
    "mk": "Macedonian",
    "sr": "Serbian",
    "uk": "Ukrainian",
    "he": "Hebrew",
    "ka": "Georgian",
    "hy": "Armenian",
    "az": "Azerbaijani",
    "kk": "Kazakh",
    "uz": "Uzbek",
    "tg": "Tajik",
    "mn": "Mongolian",
    "gl": "Galician",
    "ca": "Catalan",
    "eu": "Basque",
    "ga": "Irish",
    "is": "Icelandic",
    "mt": "Maltese",
    "cy": "Welsh",
    "gd": "Scottish Gaelic",
    "fo": "Faroese",
    "yi": "Yiddish",
    "lb": "Luxembourgish",
    "jv": "Javanese",
    "su": "Sundanese",
    "ay": "Aymara",
    "gn": "Guarani",
    "to": "Tongan",
    "sm": "Samoan",
    "st": "Sotho",
    "ts": "Tsonga",
    "ve": "Venda",
    "xh": "Xhosa",
    "zu": "Zulu",
    "tn": "Tswana",
    "ss": "Swati",
    "nr": "Ndebele",
    "ny": "Chewa",
    "mg": "Malagasy",
    "ml": "Malayalam",
    "ta": "Tamil",
    "te": "Telugu",
    "kn": "Kannada",
    "mr": "Marathi",
    "pa": "Punjabi",
    "gu": "Gujarati",
    "or": "Odia",
    "as": "Assamese",
    "ne": "Nepali",
    "si": "Sinhala",
    "ku": "Kurdish",
    "ps": "Pashto",
    "sd": "Sindhi",
    "km": "Khmer",
    "ms": "Malay",
    "ha": "Hausa",
    "am": "Amharic",
    "yo": "Yoruba",
    "ig": "Igbo",
    "sw": "Swahili",
    "af": "Afrikaans",
    "be": "Belarusian",
    "la": "Latin",
    "eo": "Esperanto",
    "aa": "Afar",
    "ab": "Abkhazian",
    "ae": "Avestan",
    "ak": "Akan",
    "an": "Aragonese",
    "av": "Avaric",
    "ba": "Bashkir",
    "bh": "Bihari",
    "bi": "Bislama",
    "bm": "Bambara",
    "bo": "Tibetan",
    "br": "Breton",
    "bs": "Bosnian",
    "ce": "Chechen",
    "ch": "Chamorro",
    "co": "Corsican",
    "cr": "Cree",
    "cu": "Church Slavic",
    "cv": "Chuvash",
    "dv": "Maldivian",
    "dz": "Dzongkha",
    "ee": "Ewe",
    "ff": "Fulah",
    "fj": "Fijian",
    "fy": "Western Frisian",
    "gv": "Manx",
    "ho": "Hiri Motu",
    "ht": "Haitian Creole",
    "hz": "Herero",
    "ia": "Interlingua",
    "ie": "Interlingue",
    "ii": "Sichuan Yi",
    "ik": "Inupiaq",
    "io": "Ido",
    "iu": "Inuktitut",
    "kg": "Kongo",
    "ki": "Kikuyu",
    "kj": "Kuanyama",
    "kl": "Kalaallisut",
    "kr": "Kanuri",
    "ks": "Kashmiri",
    "kv": "Komi",
    "kw": "Cornish",
    "ky": "Kyrgyz",
    "lg": "Ganda",
    "li": "Limburgan",
    "ln": "Lingala",
    "lo": "Lao",
    "lu": "Luba-Katanga",
    "mh": "Marshallese",
    "mi": "Maori",
    "my": "Burmese",
    "na": "Nauru",
    "nd": "North Ndebele",
    "ng": "Ndonga",
    "nv": "Navajo",
    "oc": "Occitan",
    "oj": "Ojibwa",
    "om": "Oromo",
    "os": "Ossetic",
    "pi": "Pali",
    "qu": "Quechua",
    "rm": "Romansh",
    "rn": "Rundi",
    "rw": "Kinyarwanda",
    "sa": "Sanskrit",
    "sc": "Sardinian",
    "se": "Northern Sami",
    "sg": "Sango",
    "sn": "Shona",
    "so": "Somali",
    "sq": "Albanian",
    "ti": "Tigrinya",
    "tk": "Turkmen",
    "tl": "Tagalog",
    "tt": "Tatar",
    "tw": "Twi",
    "ty": "Tahitian",
    "ug": "Uighur",
    "vo": "Volapük",
    "wa": "Walloon",
    "wo": "Wolof",
    "za": "Zhuang"
};


/** From https://github.com/OdyseeTeam/odysee-frontend/blob/master/ui/constants/tags.ts */
const MEMBERS_ONLY_TAG = "c:members-only";

const MATURE_TAGS = [
	"porn",
	"porno",
	"nsfw",
	"mature",
	"xxx",
	"sex",
	"creampie",
	"blowjob",
	"handjob",
	"vagina",
	"boobs",
	"big boobs",
	"big dick",
	"pussy",
	"cumshot",
	"anal",
	"hard fucking",
	"ass",
	"fuck",
	"hentai",
]

/** Always exclude members-only from feeds; optionally add mature tags. */
function buildNotTags() {
	const tags = [MEMBERS_ONLY_TAG];
	if (!localSettings.allowMatureContent) {
		tags.push(...MATURE_TAGS);
	}
	return tags;
}


// A corrupt or wrong-shaped saved state must not break plugin startup: return null so
// enable falls through to building a fresh session, exactly as on a first run.
function parseSavedState(savedState) {
	if (!savedState) {
		return null;
	}
	try {
		const parsed = JSON.parse(savedState);
		if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
			return parsed;
		}
		trace("Saved state was not a state object, starting a fresh session");
	} catch (e) {
		trace(`Discarding unreadable saved state (${e})`);
	}
	return null;
}


function parseNewUser(resp) {
	if (!resp?.isOk) {
		trace(`Create anonymous user failed (${resp?.code ?? ""})${resp?.body ? ` - ${resp.body}` : ""}`);
		return null;
	}
	try {
		const obj = JSON.parse(resp.body);
		return obj?.success && obj.data ? obj.data : null;
	} catch (e) {
		trace(`Create anonymous user returned malformed JSON (${e})`);
		return null;
	}
}

/**
 * Returns the anonymous auth token, minting one if enable could not.
 * @returns {string|undefined} The token, or undefined while the retry is on cooldown.
 */
function ensureAuthToken() {
	if (localState.auth_token) {
		return localState.auth_token;
	}
	// Without this retry a single failure of the new-user endpoint at enable time leaves
	// every view and subscriber count at zero for the rest of the session.
	if (Date.now() - lastAuthTokenAttempt < AUTH_TOKEN_RETRY_COOLDOWN_MS) {
		return undefined;
	}
	lastAuthTokenAttempt = Date.now();
	const userData = parseNewUser(http.GET(URL_USER_NEW, headersToAdd));
	if (userData) {
		localState.auth_token = userData.auth_token;
		localState.userId = localState.userId ?? userData.id?.toString();
	}
	return localState.auth_token;
}

trace("LOADED");