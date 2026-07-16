const PLATFORM_CLAIMTYPE = 12;
const IS_DESKTOP = bridge.buildPlatform === "desktop";

const ARTICLES_ENABLED_ANDROID = bridge.buildVersion >= 381; // Enabled on Android builds >= 381, where ChannelFragment.onContentClicked handles IPlatformArticle. Older builds had no branch for it, so tapping articles in channel content did nothing.
const ARTICLES_ENABLED_DESKTOP = false; // Desktop has no PlatformArticle UI, no C# model, and ContentGrid ignores contentType=3
const ARTICLES_ENABLED = IS_DESKTOP ? ARTICLES_ENABLED_DESKTOP : ARTICLES_ENABLED_ANDROID;
const USER_AGENT_FALLBACK = IS_DESKTOP
	? 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Safari/537.36'
	: 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36';
const getUserAgent = () => bridge.authUserAgent ?? bridge.captchaUserAgent ?? USER_AGENT_FALLBACK;

const BASE_URL = "https://www.patreon.com";
const BASE_URL_API = "https://www.patreon.com/api";
const URL_SEARCH_CREATORS = BASE_URL_API + "/search";
const URL_SEARCH_POSTS = BASE_URL_API + "/search_feed/v1/post";
const URL_USER = BASE_URL_API + "/current_user";
const URL_LAUNCHER_CARDS = BASE_URL_API + "/launcher/cards";

// Retry policy for transient HTTP failures (429 rate-limit, 5xx server errors).
const HTTP_MAX_RETRIES = 3;
const HTTP_RETRY_BASE_DELAY_MS = 1000;
const HTTP_STATUS_TOO_MANY_REQUESTS = 429;
const HTTP_STATUS_SERVER_ERROR_MIN = 500;
const HTTP_STATUS_FORBIDDEN = 403;

// Page sizes per endpoint.
const COMMENTS_PAGE_SIZE = 10;
const HOME_PAGE_SIZE = 10;
// launcher/cards returns ~1 card per page and ignores page[count]; merge several
// links.next pages into one Grayjay page for a fuller Home feed.
const HOME_PAGES_PER_FETCH = 3;
const SEARCH_PAGE_SIZE = 20;
const RECOMMENDATIONS_COUNT = 5;
const COLLECTION_COUNT_PROBE_SIZE = 1;

// Max characters of a post description before truncation.
const MAX_DESCRIPTION_LENGTH = 500;

// Campaign fields needed to build a PlatformChannel (campaignToPlatformChannel) INCLUDING vanity, which
// default campaign fields omit. Required so campaignChannelUrl can emit a canonical patreon.com URL for
// creators whose campaign url is a custom domain (unknown fields are ignored by the API, not errored).
const CAMPAIGN_CHANNEL_FIELDS = "name,summary,url,vanity,patron_count,image_url,cover_photo_url,cwh_cover_image_urls,avatar_photo_url,avatar_photo_image_urls";

const REGEX_CHANNEL_DETAILS = /Object\.assign\(window\.patreon\.bootstrap, ({.*?})\);/s
const REGEX_CHANNEL_DETAILS2 = /window\.patreon = ({.*?});/s
const REGEX_CHANNEL_DETAILS3 = /id="__NEXT_DATA__" type="application\/json">(.*?)<\/script>/s
const REGEX_CHANNEL_URL = /https:\/\/(?:www\.)?patreon\.com\/(.+)/s
const REGEX_PROFILE_URL = /https:\/\/(?:www\.)?patreon\.com\/profile(?:\/creators)?\?u=(\d+)/s

const REGEX_URL_ID = /https:\/\/(?:www\.)?patreon\.com\/(?:[^\/]+\/)?posts\/.*-(.*)\/?/s

// Grayjay content type for media (video/audio). Playlist contents must be PlatformVideo only
// (the app deserializes playlist.contents via JSVideoPager and requires Duration), so collection
// posts that aren't video/audio (text, image, nested embeds, locked) are filtered out.
const CONTENT_TYPE_MEDIA = 1;

// Collections (exposed as channel playlists). Single collection uses the query-param form
// (https://www.patreon.com/cw/{vanity}/collections?collection_id={id});
const REGEX_COLLECTION_URL = /https:\/\/(?:www\.)?patreon\.com\/cw\/([^\/?#]+)\/collections\?(?:[^#]*&)?collection_id=(\d+)/s
const REGEX_COLLECTIONS_PATH = /https:\/\/(?:www\.)?patreon\.com\/cw\/[^\/?#]+\/collections(?:[\/?#]|$)/s

// Sort options for playlist (collection) contents, indexed by the "playlistSortOrderIndex" Dropdown.
// collection_order = creator's curated order; -published_at = newest first; published_at = oldest first.
const PLAYLIST_SORTS = ["collection_order", "-published_at", "published_at"];

// MIME container types by audio file extension, so the player can pick a decoder.
// Patreon serves rendered audio as .mp3/.wav; the rest cover original uploads.
const AUDIO_CONTAINER_BY_EXTENSION = {
	mp3: "audio/mpeg",
	m4a: "audio/mp4",
	mp4: "audio/mp4",
	aac: "audio/aac",
	ogg: "audio/ogg",
	oga: "audio/ogg",
	flac: "audio/flac",
	wav: "audio/wav"
};

const DEFAULT_AUDIO_CONTAINER = "audio/mpeg";

// Common request modifier for all Patreon requests
const PATREON_REQUEST_MODIFIER = {
	headers: {
		"Referer": "https://www.patreon.com/",
		"Origin": "https://www.patreon.com"
	}
};

let config = {};
let _settings = {};

let state = {
	channelCache: {}
};


//Source Methods
source.enable = function (conf, settings, savedState) {
	config = conf ?? {};
	_settings = settings ?? {};

	PATREON_REQUEST_MODIFIER.headers["User-Agent"] = getUserAgent();

	if (savedState) {
		try {
			const restored = JSON.parse(savedState);
			if (restored && typeof restored === "object") {
				state = Object.assign(state, restored);
				if (!state.channelCache || typeof state.channelCache !== "object") {
					state.channelCache = {};
				}
			}
		} catch (e) {
			trace("Patreon: failed to parse savedState, starting fresh: " + e);
		}
	}
}

source.saveState = function () {
	return JSON.stringify(state);
}

source.getHome = function () {

	if (!bridge.isLoggedIn()) {
		return new ContentPager([], false);
	}

	return new HomePager();
};

source.searchSuggestions = function (query) {
	return [];
};
source.getSearchCapabilities = () => {
	return {
		types: [Type.Feed.Mixed],
		sorts: [Type.Order.Chronological],
		filters: []
	};
};
source.search = function (query, type, order, filters) {
	return new SearchContentPager(query);
};
source.getSearchChannelContentsCapabilities = function () {
	return {
		types: [Type.Feed.Mixed],
		sorts: [Type.Order.Chronological],
		filters: []
	};
};

source.searchChannels = function (query) {
	return new SearchChannelPager(query);
};
class SearchChannelPager extends ChannelPager {
	constructor(query) {
		const results = searchChannels(query, 1);
		super(results, results.length > 0);
		this.query = query;
		this.page = 1;
	}
	nextPage() {
		this.page = this.page + 1;
		this.results = searchChannels(this.query, this.page);
		this.hasMore = this.results.length > 0;
		return this;
	}
}

class SearchContentPager extends ContentPager {
	constructor(query) {
		const initial = searchPosts(query, "");
		super(initial.results, initial.hasMore);
		this.query = query;
		this.cursor = initial.cursor;
	}
	nextPage() {
		if (!this.cursor) {
			this.hasMore = false;
			return this;
		}
		const page = searchPosts(this.query, this.cursor);
		this.results = page.results;
		this.cursor = page.cursor;
		this.hasMore = page.hasMore;
		return this;
	}
}

//Home Pager
class HomePager extends ContentPager {
	constructor() {
		const initialData = getMergedHomeContent();
		super(initialData.results, initialData.hasMore);
		this.nextPageUrl = initialData.nextPageUrl;
	}

	nextPage() {
		if (!this.nextPageUrl) {
			this.hasMore = false;
			return this;
		}

		const newData = getMergedHomeContent(this.nextPageUrl);
		this.results = newData.results;
		this.hasMore = newData.hasMore;
		this.nextPageUrl = newData.nextPageUrl;
		return this;
	}
}

//Channel
source.isChannelUrl = function (url) {
	if (REGEX_COLLECTIONS_PATH.test(url)) return false; // collection URLs route to getPlaylist, not channel
	return REGEX_CHANNEL_URL.test(url);
};

// Function to get channel information from vanity URL
function getChannelFromVanityUrl(url, htmlContent) {
	// First, try to extract campaign ID from HTML content
	let campaignId = null;
	
	if (htmlContent) {
		// Look for campaign ID in various places in the HTML
		const campaignIdPatterns = [
			/campaign\/(\d+)/g,
			/p\/campaign\/(\d+)/g,
			/campaign_id['":\s]*(\d+)/g,
			/"id":\s*"?(\d+)"?[,}]/g
		];
		
		for (const pattern of campaignIdPatterns) {
			let match;
			while ((match = pattern.exec(htmlContent)) !== null) {
				// Look for IDs that are likely campaign IDs (5+ digits)
				if (match[1] && match[1].length >= 5) {
					campaignId = match[1];
					break;
				}
			}
			if (campaignId) break;
		}
	}
	
	if (campaignId) {
		// Try to get campaign data via campaigns API
		const apiUrl = BASE_URL_API + "/campaigns/" + campaignId + "?" + SOCIAL_LINKS_QUERY + "&" + buildQuery({ "fields[campaign]": CAMPAIGN_CHANNEL_FIELDS });
		
		try {
			const apiData = httpGET({
				url: apiUrl,
				useAuthenticated: true,
				parseResponse: true
			});
			if (apiData.data && apiData.data.attributes) {
				// Convert API response to expected format
				const channel = {
					campaign: {
						data: {
							id: apiData.data.id,
							attributes: {
								name: apiData.data.attributes.name,
								description: apiData.data.attributes.description || apiData.data.attributes.summary,
								url: apiData.data.attributes.url,
								vanity: apiData.data.attributes.vanity,
								patron_count: apiData.data.attributes.patron_count,
								avatar_photo_url: apiData.data.attributes.avatar_photo_url,
								image_url: apiData.data.attributes.image_url || apiData.data.attributes.cover_photo_url
							}
						}
					}
				};
				
				const links = extractSocialLinks(apiData.included);
				return campaignToPlatformChannel(channel, links);
			}
		} catch (error) {
			// Continue to error handling below
		}
	}

	throw new ScriptException("Failed to get channel from HTML content. Campaign ID not found or API call failed.");
}

// Function to get channel information from user ID (for profile/creators URLs)
function getChannelFromUserId(userId, originalUrl) {
	// First, try to get user information from the API
	const userApiUrl = BASE_URL_API + "/user/" + userId + "?" + buildQuery({ "include": "campaign", "fields[campaign]": CAMPAIGN_CHANNEL_FIELDS });
	const userData = httpGET({
		url: userApiUrl,
		useAuthenticated: true,
		parseResponse: true
	});

	if (userData) {

		// Check if user has a campaign (is a creator) via relationships
		if (userData.data && userData.data.relationships && userData.data.relationships.campaign) {
			const campaignId = userData.data.relationships.campaign.data.id;
			const campaign = userData.included?.find(item => item.type === 'campaign' && item.id === campaignId);

			if (campaign) {
				const links = fetchSocialLinks(campaignId);
				const channel = campaignToPlatformChannel({ campaign: { data: campaign }}, links);
				state.channelCache[originalUrl] = channel;
				return channel;
			}
		}

		// If we have user data, create a basic channel from the user information
		if (userData.data && userData.data.attributes) {
			const userAttrs = userData.data.attributes;

			// Create a minimal channel object from user data
			const basicChannel = {
				campaign: {
					data: {
						id: userId, // Use user ID as campaign ID
						attributes: {
							name: userAttrs.full_name || userAttrs.vanity || "Unknown Creator",
							description: userAttrs.about || "",
							url: userAttrs.url || (BASE_URL + "/" + userAttrs.vanity),
							patron_count: 0, // We don't have this info
							avatar_photo_url: userAttrs.image_url || userAttrs.thumb_url,
							image_url: userAttrs.image_url || userAttrs.thumb_url
						}
					}
				}
			};

			return campaignToPlatformChannel(basicChannel);
		}
	}

	throw new ScriptException("Failed to get channel from user ID: " + userId);
}
source.getChannel = function (url) {
	if (state.channelCache[url]) return state.channelCache[url];

	// Profile URLs (patreon.com/profile/creators?u=ID) resolve via the user API.
	const profileMatch = REGEX_PROFILE_URL.exec(url);
	if (profileMatch) {
		const userId = profileMatch[1];
		const profileChannel = getChannelFromUserId(userId, url);
		profileChannel.urlAlternatives = Array.from(new Set([url, `https://www.patreon.com/profile/creators?u=${userId}`]));
		state.channelCache[url] = profileChannel;
		return profileChannel;
	}

	// Vanity URLs resolve the campaign through the API. The HTML creator page sits behind a
	// Cloudflare challenge that raw http.GET have issues, so the API is the primary path.
	const vanity = getVanityFromUrl(url);
	if (vanity) {
		try {
			const channel = getChannelFromVanity(vanity);
			if (channel) {
				channel.urlAlternatives = Array.from(new Set([url, channel.url]));
				state.channelCache[url] = channel;
				return channel;
			}
		} catch (error) {
			if (error instanceof CaptchaRequiredException) throw error;
			if (error.code === 404) throw new UnavailableException("Channel not found");
			// Any other error: fall through to the legacy HTML scraper below.
		}
	}

	// Fallback: legacy HTML scraping (reachable only when the API resolver finds no campaign).
	return getChannelFromHtml(url);
};

// Resolves a creator's campaign directly from the API by vanity slug, avoiding the
// Cloudflare-challenged HTML page. Returns null when no campaign matches the vanity.
function getChannelFromVanity(vanity) {
	const data = httpGET({
		url: BASE_URL_API + "/campaigns?" + buildQuery({
			"filter[vanity]": vanity,
			"json-api-version": "1.0"
		}) + "&" + SOCIAL_LINKS_QUERY,
		useAuthenticated: true,
		parseResponse: true
	});
	const campaign = data?.data?.[0];
	if (!campaign) return null;
	// Inject the known vanity (default campaign fields omit it) so campaignChannelUrl can emit a canonical
	// patreon.com URL for creators whose campaign url is a custom domain (e.g. members.frame-lines.com).
	const attributes = { ...campaign.attributes, vanity: campaign.attributes.vanity ?? vanity };
	const channel = { campaign: { data: { id: campaign.id, attributes } } };
	return campaignToPlatformChannel(channel, extractSocialLinks(data.included));
}

// Legacy fallback: scrape the creator's HTML page and parse the bootstrap blob. Only used when
// the API vanity resolver returns nothing; the HTML page is usually behind a Cloudflare challenge.
function getChannelFromHtml(url) {
	let channelResp;
	let finalUrl;

	try {
		const resp = httpRequest({ url: url, useAuthenticated: false });
		channelResp = { body: resp.body };
		finalUrl = resp.url ?? url; // final URL after any redirect (canonical creator url)
	} catch (error) {
		if (error.code === 404)
			throw new UnavailableException("Channel not found");
		throw new ScriptException("Failed to get channel");
	}

	const urlSet = new Set([url, finalUrl]);

	let channelJson = REGEX_CHANNEL_DETAILS.exec(channelResp.body);
	let channel = null;
	if (!channelJson || channelJson.length != 2) {
		channelJson = REGEX_CHANNEL_DETAILS2.exec(channelResp.body);
		if (channelJson && channelJson.length == 2) {
			channel = JSON.parse(channelJson[1]);

			if (channel && channel.bootstrap)
				channel = channel.bootstrap;
			else
				throw new ScriptException("Failed to parse channel");
		}
		else {
			channelJson = REGEX_CHANNEL_DETAILS3.exec(channelResp.body);
			if (channelJson && channelJson.length == 2) {
				const channelWrapperObj = JSON.parse(channelJson[1]);
				const envelope = channelWrapperObj?.props?.pageProps?.bootstrapEnvelope;
				channel = envelope?.bootstrap ?? envelope?.pageBootstrap;

				if (!channel)
					throw new ScriptException("Failed to parse channel");

				if (channel.curtainType === "campaign_removed")
					throw new UnavailableException("This page has been removed.");
			}
			else {
				// Try API approach
				const channel = getChannelFromVanityUrl(url, channelResp.body);
				channel.urlAlternatives = Array.from(urlSet);
				state.channelCache[url] = channel;
				return channel;
			}
		}
	}
	else
		channel = JSON.parse(channelJson[1]);

	const campaignId = channel?.campaign?.data?.id;
	const links = campaignId ? fetchSocialLinks(campaignId) : {};
	state.channelCache[url] = campaignToPlatformChannel(channel, links);
	return state.channelCache[url];
};
source.getChannelContents = function (url) {
	const channel = source.getChannel(url);
	return new ChannelContentPager(channel.id.value, channel);
};
class ChannelContentPager extends ContentPager {
	constructor(campaignId, channel) {
		const initialResults = getPosts(campaignId, channel);
		super(initialResults.results, true);
		this.nextPageUrl = initialResults.nextPage;
		this.hasMore = !!this.nextPageUrl;
		this.campaignId = campaignId;
		this.channel = channel;
	}
	nextPage() {
		if (!this.nextPageUrl) {
			this.hasMore = false;
			return this;
		}
		const newResults = getPosts(this.campaignId, this.channel, this.nextPageUrl) ?? [];
		this.results = newResults.results;
		this.nextPageUrl = newResults.nextPage;
		this.hasMore = !!newResults.nextPage;
		return this;
	}
}

source.searchChannelContents = function (url, query, type, order, filters) {
	const channel = source.getChannel(url);
	return new SearchChannelContentPager(channel.id.value, channel, query);
};

source.getChannelPlaylists = function (url) {
	const channel = source.getChannel(url);

	const campaignId = channel?.id?.value;
	const vanity = getVanityFromChannel(channel);
	if (!campaignId || !vanity) {
		return new PlaylistPager([], false);
	}

	try {
		const data = httpGET({
			url: BASE_URL_API + "/campaigns/" + campaignId + "?" + buildQuery({ "include": "collections" }),
			useAuthenticated: true,
			parseResponse: true
		});

		const author = new PlatformAuthorLink(
			new PlatformID(config.name, campaignId, config.id, PLATFORM_CLAIMTYPE),
			channel.name, 
			channel.url, 
			channel.thumbnail, 
			channel.subscribers || 0
		);

		const collections = (data?.included ?? []).filter(i => i.type === "collection");
		if (collections.length === 0) {
			return new PlaylistPager([], false);
		}

		// Default (cheap) path: no extra requests. List all collections with unknown count and
		// without hiding empty/non-media ones. Enable "Load extra playlist info" for accurate data.
		if (!isSettingEnabled(_settings?.playlistFetchExtraInfo)) {
			return new PlaylistPager(
				collections.map(c => collectionToPlatformPlaylist(c, vanity, author, -1)),
				false);
		}

		// Probe each collection's playable-media count in one parallel batch (page[size]=1 ->
		// meta.pagination.total). Lets us set an accurate videoCount and drop collections that
		// contain no playable media (e.g. polls/links), which would otherwise be empty playlists.
		const batch = http.batch();
		collections.forEach(c => batch.GET(collectionMediaPostsUrl(campaignId, c.id, COLLECTION_COUNT_PROBE_SIZE), PATREON_REQUEST_MODIFIER.headers, true));
		const resps = batch.execute();

		const playlists = [];
		collections.forEach((c, i) => {
			let total = -1; // unknown -> keep the collection (fail-open)
			try {
				const r = resps[i];
				if (r && r.isOk) total = JSON.parse(r.body)?.meta?.pagination?.total ?? -1;
			} catch (e) { /* keep as unknown */ }

			if (total === 0) return; // no playable media -> hide
			playlists.push(collectionToPlatformPlaylist(c, vanity, author, total));
		});

		return new PlaylistPager(playlists, false);
	} catch (error) {
		if (error instanceof CaptchaRequiredException) throw error;
		trace("Failed to get channel playlists: " + error.message);
		return new PlaylistPager([], false);
	}
};

source.isPlaylistUrl = function (url) {
	return REGEX_COLLECTION_URL.test(url);
};

source.getPlaylist = function (url) {
	const match = REGEX_COLLECTION_URL.exec(url);
	if (!match) {
		throw new ScriptException("Invalid collection url");
	}
	const vanity = match[1];
	const collectionId = match[2];

	const channelUrl = BASE_URL + "/" + vanity;
	const channel = source.getChannel(channelUrl);
	const campaignId = channel?.id?.value;

	const data = httpGET({
		url: BASE_URL_API + "/campaigns/" + campaignId + "?" + buildQuery({ "include": "collections" }),
		useAuthenticated: true,
		parseResponse: true
	});
	const col = (data?.included ?? []).find(i => i.type === "collection" && i.id === collectionId);
	if (!col) {
		throw new UnavailableException("Collection not found");
	}

	const author = new PlatformAuthorLink(
		new PlatformID(config.name, campaignId, config.id, PLATFORM_CLAIMTYPE),
		channel.name, 
		channel.url, 
		channel.thumbnail, 
		channel.subscribers || 0
	);

	const context = { name: channel.name, url: channel.url, thumbnail: channel.thumbnail, subscribers: channel.subscribers || 0 };

	const contents = new CollectionVideoPager(campaignId, collectionId, context);
	const base = collectionToPlatformPlaylist(col, vanity, author, contents.total);

	return new PlatformPlaylistDetails({
		id: base.id,
		name: base.name,
		author: base.author,
		url: base.url,
		thumbnail: base.thumbnail,
		videoCount: base.videoCount,
		datetime: base.datetime,
		contents: contents
	});
};

class CollectionVideoPager extends VideoPager {
	constructor(campaignId, collectionId, context) {
		const initialResults = getPosts(campaignId, context, undefined, collectionId);
		super(initialResults.results.filter(onlyMedia), !!initialResults.nextPage);
		this.nextPageUrl = initialResults.nextPage;
		this.campaignId = campaignId;
		this.collectionId = collectionId;
		this.context = context;
		this.total = (typeof initialResults.total === "number") ? initialResults.total : -1;
	}
	nextPage() {
		if (!this.nextPageUrl) {
			this.hasMore = false;
			return this;
		}
		const newResults = getPosts(this.campaignId, this.context, this.nextPageUrl, this.collectionId) ?? { results: [] };
		this.results = (newResults.results ?? []).filter(onlyMedia);
		this.nextPageUrl = newResults.nextPage;
		this.hasMore = !!newResults.nextPage;
		return this;
	}
}

// Playlist contents must be PlatformVideo (the app reads Duration on each item); keep only video/audio posts.
function onlyMedia(content) {
	return content != null && content.contentType === CONTENT_TYPE_MEDIA;
}

source.getChannelTemplateByClaimMap = () => {
	return {
		//Patreon
		12: {
			0: BASE_URL + "/{{CLAIMVALUE}}"
		}
	};
};

//Video
source.isContentDetailsUrl = function (url) {

	if (!url) return false;

	// Android can't open post/text/image details, so only the media hint is valid there;
	// desktop routes all detail types through isContentDetailsUrl/getContentDetails.
	if (
		hasQueryParam(url, 'isPatreonMediaContent') ||
		(IS_DESKTOP && hasQueryParam(url, 'isPatreonPostContent'))
	) {
		return true;
	}

	if (REGEX_COLLECTIONS_PATH.test(url)) return false; // collection URLs are playlists, not content
	return REGEX_URL_ID.test(url);
};

source.getContentDetails = function (url) {
    const postId = getPostIdFromUrl(url);

    try {
        const postBody = httpGET({
            // campaign is needed for the author; images resolves image_file posts (image_order -> media)
            // so they open on desktop. A bare fetch omits images; specifying include overrides defaults.
            url: BASE_URL_API + "/posts/" + postId + "?" + buildQuery({
                "include": "campaign,images,attachments_media",
                // vanity lets campaignChannelUrl emit a canonical patreon.com author URL for custom-domain creators.
                "fields[campaign]": CAMPAIGN_CHANNEL_FIELDS,
                "fields[media]": "id,image_urls,display,download_url,metadata,file_name,mimetype,size_bytes,state"
            }),
            useAuthenticated: true,
            parseResponse: true
        });

        if (!postBody.data.attributes.current_user_can_view) {
            throw new UnavailableException("This content is exclusive for members.");
        }

        const campaign = postBody.included.find(a => a.type == 'campaign');
        const channel = campaignToPlatformChannel({ campaign: { data : campaign }});
        return parseSinglePost(postBody.data, postBody, channel, true);
    } catch (error) {
        if (error instanceof CaptchaRequiredException) throw error;
        if (error instanceof UnavailableException) throw error;
        throw new ScriptException("Failed to get post details");
    }
};

//Comments
source.getComments = function (url, page = 0) {

	const id = getPostIdFromUrl(url);
	if (!id)
		return new CommentPager([], false);

	try {
		const commentsData = httpGET({
			url: BASE_URL_API + "/posts/" + id + "/comments?" + buildQuery({
				"include": "include_replies,commenter,replies,replies.commenter",
				"fields[comment]": "body,created,vote_sum,reply_count",
				"fields[post]": "comment_count",
				"fields[user]": "image_url,full_name,url",
				"fields[flair]": "image_tiny_url,name",
				"page[count]": COMMENTS_PAGE_SIZE,
				"sort": "-created",
				"json-api-use-default-includes": "false",
				"json-api-version": "1.0"
			}),
			useAuthenticated: true,
			parseResponse: true
		});
		return new PatreonCommentPager(url, commentsData);
	} catch (error) {
		if (error instanceof CaptchaRequiredException) throw error;
		throw new ScriptException("Failed to get comments");
	}
}

source.getSubComments = function (comment) {
	if (typeof comment === 'string')
		comment = JSON.parse(comment);

	// replies were stored as a JSON string in context (see parseComment)
	let replyData = [];
	
	try {
		replyData = JSON.parse(comment?.context?.replies ?? "[]");
	}
	catch (e) {
		replyData = [];
	}

	const replies = replyData.map(r => new Comment({
		contextUrl: comment.contextUrl,
		author: new PlatformAuthorLink(
			new PlatformID(config.name, r.id, config.id, PLATFORM_CLAIMTYPE),
			r.authorName,
			r.authorUrl, 
			r.authorImage
		),
		message: r.message ?? "",
		rating: new RatingLikes(r.voteSum ?? 0),
		date: r.date,
		replyCount: r.replyCount ?? 0
	}));

	return new CommentPager(replies, false);
}

class PatreonCommentPager extends CommentPager {

	constructor(url, resp) {
		if (IS_TESTING)
			trace("CommentPager resp: " + JSON.stringify(resp));

		const nextUrl = resp?.links?.next;
		super([], !!nextUrl);
		this.contextUrl = url;
		this.results = this.parseResponse(resp);
		this.nextPageUrl = nextUrl;
		this.hasMore = !!nextUrl;
	}

	nextPage() {
		try {
			const responseBody = httpGET({
				url: this.nextPageUrl,
				useAuthenticated: true,
				parseResponse: true
			});
			this.results = this.parseResponse(responseBody);
			this.nextPageUrl = responseBody?.links?.next;
			
			this.hasMore = !!this.nextPageUrl;
			
			return this;
		} catch (error) {
			if (error instanceof CaptchaRequiredException) throw error;
			throw new ScriptException("Failed to get next comment page");
		}
	}

	parseResponse(resp) {
		return resp.data.map(x => this.parseComment(x, resp)).filter(x => x != null)
	}
	parseComment(comment, resp) {
		const commenterId = comment?.relationships?.commenter?.data?.id;
		if (!commenterId)
			return null;
		const commenter = resp.included?.find(y => y.id == commenterId);
		if (!commenter)
			return null;

		const replies = (comment.relationships?.replies?.data ?? [])
			.map(y => resp.included?.find(z => z.id == y.id))
			.filter(y => y != null)
			.map(y => {
				const rCommenter = resp.included?.find(z => z.id == y?.relationships?.commenter?.data?.id);
				if (!rCommenter)
					return null;
				return {
					id: y.id,
					authorName: rCommenter.attributes.full_name,
					authorUrl: rCommenter.attributes.url,
					authorImage: rCommenter.attributes.image_url,
					message: y.attributes.body ?? "",
					voteSum: y.attributes.vote_sum ?? 0,
					date: toUnixSeconds(y.attributes.created),
					replyCount: y.attributes.reply_count ?? 0
				};
			})
			.filter(y => y != null);

		return new Comment({
			contextUrl: this.contextUrl,
			author: new PlatformAuthorLink(new PlatformID(config.name, comment.id, config.id, PLATFORM_CLAIMTYPE), commenter.attributes.full_name, commenter.attributes.url, commenter.attributes.image_url),
			message: comment.attributes.body ?? "",
			rating: new RatingLikes(comment.attributes.vote_sum ?? 0),
			date: toUnixSeconds(comment.attributes.created),
			replyCount: comment.attributes.reply_count ?? 0,
			// Must be a string: the app deserializes context into a string->string map
			// (Android Map<String,String> / Desktop Dictionary<string,string>); a non-string value throws at deserialization.
			context: { replies: JSON.stringify(replies) }
		});
	}
}

source.getUserSubscriptions = function () {
	try {
		const response = httpGET({
			url: URL_USER + "?" + buildQuery({ "include": "active_memberships.campaign", "fields[campaign]": "name,url,vanity" }),
			useAuthenticated: true,
			parseResponse: true
		});

		return response.data.relationships.active_memberships.data.map((membership) => {
			const channel_id = response.included.find((extra) => extra.id === membership.id).relationships.campaign.data.id
			const campaignAttrs = response.included.find((extra) => extra.id === channel_id).attributes
			return campaignChannelUrl(campaignAttrs)
		});
	} catch (error) {
		if (error instanceof CaptchaRequiredException) throw error;
		throw new ScriptException("Failed to get subscriptions");
	}
}

function getPosts(campaign, context, nextPage, collectionId, searchQuery) {
	try {
		const data = httpGET({
			// nextPage is a verbatim links.next cursor URL — never rebuild it.
			url: nextPage ? nextPage : BASE_URL_API + "/posts?" + buildQuery({
				"filter[campaign_id]": campaign,
				// Collections only list playable media (filter[media_types], server-side) and
				// collection_order preserves the creator's intended order.
				"filter[collection_id]": collectionId,
				"filter[media_types]": collectionId ? "video,audio" : undefined,
				// Channel content search (server-side, scoped to the campaign).
				"filter[search_query]": searchQuery,
				"include": "images,media,attachments_media",
				"filter[contains_exclusive_posts]": "true",
				"sort": collectionId ? getPlaylistSort() : "-published_at",
				"fields[post]": "title,content,content_json_string,teaser_text,post_type,post_file,embed,image,post_metadata,published_at,url,like_count,current_user_can_view,thumbnail",
				"fields[media]": "id,image_urls,display,download_url,metadata,file_name,state"
			}),
			useAuthenticated: true,
			parseResponse: true
		});

		if (IS_TESTING)
			trace("getPosts data: " + JSON.stringify(data));

		// Map all posts to Platform content using the reusable mapping functions
		const contents = data?.data
			?.map(post => mapPostToPlatformContent(post, context, data))
			?.filter(content => content != null) ?? [];

		return {
			results: contents,
			nextPage: data?.links?.next,
			total: data?.meta?.pagination?.total
		};
	} catch (error) {
		if (error instanceof CaptchaRequiredException) throw error;
		trace("Failed to get posts: " + error.message);
		return {
			results: [],
			nextPage: null,
			hasMore: false
		};
	}
}

const SOCIAL_LINKS_QUERY = buildQuery({
	"include": "connected_socials,creator",
	"fields[connected_socials]": "app_name,external_profile_url,is_public",
	"fields[user]": "youtube,twitter,twitch,facebook",
	"json-api-use-default-includes": "false"
});

const SOCIAL_APP_NAME_MAP = {
	"youtube": "YouTube",
	"twitter": "Twitter",
	"twitch": "Twitch",
	"facebook": "Facebook",
	"instagram": "Instagram",
	"tiktok": "TikTok",
	"discord": "Discord",
	"spotify": "Spotify",
	"reddit": "Reddit",
	"vimeo": "Vimeo"
};

function extractSocialLinks(included) {
	const links = {};
	if (!included) return links;

	// Extract from connected_socials (OAuth-connected, shown on about page)
	for (const item of included) {
		if (item.type === "social-connection" && item.attributes?.is_public && item.attributes?.external_profile_url) {
			const name = SOCIAL_APP_NAME_MAP[item.attributes.app_name] || item.attributes.app_name;
			links[name] = item.attributes.external_profile_url;
		}
	}

	// Extract legacy user fields as fallback
	const user = included.find(item => item.type === "user");
	if (user?.attributes) {
		if (user.attributes.youtube && !links["YouTube"])
			links["YouTube"] = user.attributes.youtube;
		if (user.attributes.twitter && !links["Twitter"])
			links["Twitter"] = "https://twitter.com/" + user.attributes.twitter;
		if (user.attributes.twitch && !links["Twitch"])
			links["Twitch"] = user.attributes.twitch;
		if (user.attributes.facebook && !links["Facebook"])
			links["Facebook"] = user.attributes.facebook;
	}

	return links;
}

function fetchSocialLinks(campaignId) {
	try {
		const data = httpGET({
			url: BASE_URL_API + "/campaigns/" + campaignId + "?" + SOCIAL_LINKS_QUERY,
			useAuthenticated: true,
			parseResponse: true
		});
		return extractSocialLinks(data.included);
	} catch (error) {
		return {};
	}
}

// Canonical patreon.com channel URL for a campaign. Some creators use a custom domain as their campaign
// url (e.g. members.frame-lines.com); prefer the vanity-based patreon.com URL so the rest of the plugin
// (isChannelUrl / getChannel / getChannelContents) can resolve it. Falls back to the raw url if no vanity.
function campaignChannelUrl(attrs) {
	return attrs?.vanity ? (BASE_URL + "/" + attrs.vanity) : attrs?.url;
}

function campaignToPlatformChannel(channel, links) {

	return new PlatformChannel({
		id: new PlatformID(config.name, channel?.campaign?.data?.id, config.id, PLATFORM_CLAIMTYPE),
		name: channel?.campaign?.data?.attributes?.name,
		description: channel?.campaign?.data?.attributes?.description ?? channel?.campaign?.data?.attributes?.summary,
		url: campaignChannelUrl(channel?.campaign?.data?.attributes),
		subscribers: channel?.campaign?.data?.attributes?.patron_count,
		banner: channel?.campaign?.data?.attributes?.image_url ?? channel?.campaign?.data?.attributes?.cover_photo_url ?? channel?.campaign?.data?.attributes?.cwh_cover_image_urls?.large,
		thumbnail: channel?.campaign?.data?.attributes?.avatar_photo_url ?? channel?.campaign?.data?.attributes?.avatar_photo_image_urls?.thumbnail,
		links: links ?? {}
	})
}

function parseSinglePost(item, data, context, isDetails=false) {
    if (!item) return null;

    if (isDetails && !IS_DESKTOP) {
        // Android deep links don't support embeds, text-only, or image-file posts (no PlatformPostDetails / PlatformNestedMediaContent UI).
        // Exception: text/image posts with a file attachment render as PlatformPostDetails so the download link is reachable.
        const hasAttachment = (item?.relationships?.attachments_media?.data?.length ?? 0) > 0;
        if (item?.attributes?.embed ||
            ((item?.attributes?.post_type == 'text_only' || item?.attributes?.post_type == 'image_file') && !hasAttachment)) {
            throw new UnavailableException("Unsupported content type while deep linking. Consider opening it from inside the channel.");
        }
    }

    return mapPostToPlatformContent(item, context, data);
}


function searchChannels(query, page) {
	try {
		const data = httpGET({
			url: URL_SEARCH_CREATORS + "?" + buildQuery({
				"q": query,
				"page[number]": page,
				"json-api-version": "1.0",
				"includes": "[]"
			}),
			useAuthenticated: false,
			parseResponse: true
		});

		const channels = [];
		for (const item of data.data) {
			const id = item.id;
			if (id.startsWith("campaign_"))
				channels.push(new PlatformAuthorLink(new PlatformID(config.name, id.substring("campaign_".length), config.id, PLATFORM_CLAIMTYPE),
					item.attributes.name,
					item.attributes.url,
					item.attributes.avatar_photo_url,
					item.attributes.patron_count));
		}

		return channels.filter(x => x != null);
	} catch (error) {
		if (error instanceof CaptchaRequiredException) throw error;
		throw new ScriptException("Failed to search creators");
	}
}

function searchPosts(query, cursor) {
	try {
		const data = httpGET({
			url: URL_SEARCH_POSTS + "?" + buildQuery({
				"sort": "relevance",
				"filter[query]": query,
				"filter[is_for_preview]": "false",
				"filter[filter_by_user_subscription]": "false",
				"filter[include_nsfw]": "true",
				"fields[campaign]": "name,url,patron_count,avatar_photo_url,avatar_photo_image_urls",
				"fields[post]": "comment_count,content,content_json_string,content_teaser_text,created_at,current_user_can_view,embed,image,like_count,post_file,post_metadata,published_at,post_type,teaser_text,thumbnail,thumbnail_url,title,url,video,video_preview",
				"fields[media]": "id,image_urls,display,download_url,metadata,file_name,state",
				"include": "card_post,card_post.post,card_post.post.campaign,card_post.post.images,card_post.post.media,card_post.post.attachments_media",
				"page[size]": SEARCH_PAGE_SIZE,
				"page[cursor]": cursor || "",
				"json-api-version": "1.0",
				"json-api-use-default-includes": "false"
			}),
			useAuthenticated: true,
			parseResponse: true
		});

		const includedMap = createIncludedLookupMap(data.included);
		const contents = [];

		for (const feedItem of (data.data || [])) {
			const cardPostRef = feedItem.relationships?.card_post?.data;
			if (!cardPostRef) continue;

			const cardPost = includedMap.get(cardPostRef.type + ":" + cardPostRef.id);
			const postRef = cardPost?.relationships?.post?.data;
			if (!postRef) continue;

			const post = includedMap.get("post:" + postRef.id);
			if (!post) continue;

			const campaignRef = post.relationships?.campaign?.data;
			const campaign = campaignRef ? includedMap.get("campaign:" + campaignRef.id) : null;

			const campaignContext = {
				name: campaign?.attributes?.name,
				url: campaign?.attributes?.url,
				thumbnail: campaign?.attributes?.avatar_photo_image_urls?.thumbnail || campaign?.attributes?.avatar_photo_url,
				subscribers: campaign?.attributes?.patron_count || 0
			};

			const content = mapPostToPlatformContent(post, campaignContext, includedMap);
			if (content) {
				contents.push(content);
			}
		}

		const nextCursor = data.meta?.pagination?.cursors?.next;
		return {
			results: contents,
			cursor: nextCursor || null,
			hasMore: !!nextCursor
		};
	} catch (error) {
		if (error instanceof CaptchaRequiredException) throw error;
		trace("Failed to search posts: " + error.message);
		return { results: [], cursor: null, hasMore: false };
	}
}

function getPostIdFromUrl(url) {
	const match = url.match(/\/posts\/(?:[\w-]+-)?(\d+)/);
    return match ? match[1] : null;
}

/**
 * Converts an ISO date string to a Unix timestamp in whole seconds.
 * @param {string} dateString - e.g. attrs.published_at; may be null/undefined/invalid.
 * @returns {number} Unix time in seconds, or 0 when missing/unparseable.
 */
function toUnixSeconds(dateString) {
	if (!dateString) return 0;
	const ms = Date.parse(dateString);
	return Number.isNaN(ms) ? 0 : Math.floor(ms / 1000);
}


// Merges up to HOME_PAGES_PER_FETCH launcher/cards pages (followed via links.next)
// into one Grayjay page; page[count] is server-ignored, so each page is ~1 card.
function getMergedHomeContent(url) {
	const results = [];
	let nextPageUrl = url;
	let hasMore = false;
	for (let i = 0; i < HOME_PAGES_PER_FETCH; i++) {
		const page = getHomeContent(nextPageUrl);
		results.push(...page.results);
		hasMore = page.hasMore;
		nextPageUrl = page.nextPageUrl;
		if (!nextPageUrl) break; // reached the end of the feed
	}
	return { results, hasMore, nextPageUrl };
}

// Function to get home content from launcher/cards API
function getHomeContent(url) {
	// url, when present, is a verbatim links.next cursor URL — never rebuild it.
	const requestUrl = url || (URL_LAUNCHER_CARDS + "?" + buildQuery({
		"include": "campaign.null,latest_posts,latest_posts.images.null,latest_posts.audio.null,latest_posts.attachments_media.null",
		"fields[campaign]": "id,avatar_photo_image_urls,name,url,vanity,cover_photo_url_sizes,creation_count,currency,current_user_is_free_member,main_video_embed,main_video_url,offers_paid_membership,one_liner,pay_per_name,post_count,pledge_url,primary_theme_color,summary,is_monthly",
		"fields[post]": "id,attachments_media,change_visibility_at,comment_count,content,content_json_string,content_teaser_text,current_user_can_comment,current_user_can_report,current_user_can_view,current_user_has_liked,created_at,current_user_comment_disallowed_reason,embed,is_new_to_current_user,image,images,is_paid,like_count,likes,patreon_url,pledge_url,post_file,post_type,post_metadata,preview_asset_type,published_at,teaser_text,thumbnail,title,upgrade_url,url,was_posted_by_campaign_owner,video_preview",
		"fields[media]": "id,image_urls,display,download_url,metadata,file_name,state",
		"filter[show_shop_posts]": "false",
		"json-api-version": "1.0",
		"json-api-use-default-includes": "false",
		"page[count]": HOME_PAGE_SIZE
	}));
	
	try {
		const data = httpGET({
			url: requestUrl,
			useAuthenticated: true,
			parseResponse: true
		});
	const contents = [];

	// Build lookup maps for efficient access
	const includedMap = createIncludedLookupMap(data.included);
	
	// Extract campaigns and their posts
	if (data.data) {
		for (const card of data.data) {
			if (card.type === "launcher-card" && card.attributes?.card_type === "campaign") {
				// Get campaign from included data
				const campaignId = card.relationships?.campaign?.data?.id;
				const campaign = includedMap.get(`campaign:${campaignId}`);
				
				if (campaign) {
					// Create campaign context for posts
					const campaignContext = {
						name: campaign.attributes?.name,
						url: campaign.attributes?.url,
						thumbnail: campaign.attributes?.avatar_photo_image_urls?.thumbnail,
						subscribers: campaign.attributes?.patron_count || 0
					};
					
					// Get latest posts for this campaign
					const postIds = card.relationships?.latest_posts?.data || [];
					for (const postRef of postIds) {
						const post = includedMap.get(`post:${postRef.id}`);
						if (post && post.attributes?.current_user_can_view) {
							// Process post based on type
							const content = processPost(post, includedMap, campaignContext);
							if (content) {
								contents.push(content);
							}
						}
					}
				}
			}
		}
	}
	
		return {
			results: contents,
			hasMore: !!data.links?.next,
			nextPageUrl: data.links?.next || null
		};
	} catch (error) {
		if (error instanceof CaptchaRequiredException) throw error;
		trace("Failed to get home content: " + error.message);
		return { results: [], hasMore: false, nextPageUrl: null };
	}
}

// Unified post processing function
function processPost(post, includedMap, context) {
	return mapPostToPlatformContent(post, context, includedMap);
}

// Helper to create author from post and context
function createAuthor(post, context) {
	return new PlatformAuthorLink(
		new PlatformID(config.name, post.relationships?.campaign?.data?.id, config.id, PLATFORM_CLAIMTYPE),
		context.name,
		context.url,
		context.thumbnail,
		context.subscribers || 0
	);
}

// ===== PROSEMIRROR → HTML CONVERTER =====

function proseMirrorToHtml(doc) {
	if (!doc || doc.type !== 'doc') return '';
	return (doc.content || []).map(pmNodeToHtml).join('');
}

function pmNodeToHtml(node) {
	if (!node) return '';
	const children = () => (node.content || []).map(pmNodeToHtml).join('');
	switch (node.type) {
		case 'paragraph':     return '<p>' + children() + '</p>';
		case 'hardBreak':     return '<br>';
		case 'heading':       return '<h' + (node.attrs?.level || 1) + '>' + children() + '</h' + (node.attrs?.level || 1) + '>';
		case 'bulletList':    return '<ul>' + children() + '</ul>';
		case 'orderedList':   return '<ol>' + children() + '</ol>';
		case 'listItem':      return '<li>' + children() + '</li>';
		case 'blockquote':    return '<blockquote>' + children() + '</blockquote>';
		case 'codeBlock':     return '<pre><code>' + children() + '</code></pre>';
		case 'horizontalRule': return '<hr>';
		case 'image': {
			const src = node.attrs?.src || '';
			const alt = node.attrs?.alt || '';
			return '<img src="' + src + '" alt="' + alt + '">';
		}
		case 'text': {
			let text = node.text || '';
			if (node.marks) {
				for (const mark of node.marks) {
					text = pmApplyMark(mark, text);
				}
			}
			return text;
		}
		default: return children();
	}
}

function pmApplyMark(mark, text) {
	switch (mark.type) {
		case 'strong':    return '<strong>' + text + '</strong>';
		case 'bold':      return '<strong>' + text + '</strong>';
		case 'em':        return '<em>' + text + '</em>';
		case 'italic':    return '<em>' + text + '</em>';
		case 'underline': return '<u>' + text + '</u>';
		case 'strike':    return '<s>' + text + '</s>';
		case 'code':      return '<code>' + text + '</code>';
		case 'link': {
			const href = mark.attrs?.href || '';
			const target = mark.attrs?.target || '_blank';
			return '<a href="' + href + '" target="' + target + '">' + text + '</a>';
		}
		default: return text;
	}
}

// Returns post body content as HTML. Desktop renders Type.Text.HTML via innerHTML; Android renders HTML in PlatformPostDetails / segments.
function getPostHtmlContent(attrs) {
	let html = '';
	if (attrs?.content) {
		html = attrs.content;
	} else if (attrs?.content_json_string) {
		try {
			html = proseMirrorToHtml(JSON.parse(attrs.content_json_string));
		} catch (e) { /* fall through */ }
	}
	return html;
}

// Returns plain text from a ProseMirror inline node array (for heading content)
function inlineNodesToText(content) {
	if (!content) return '';
	return content.map(node => node.text || '').join('');
}

// Converts a ProseMirror document to an ArticleSegment array (Android only).
// Groups consecutive non-heading/non-image blocks into a single ArticleTextSegment.
function proseMirrorToSegments(doc) {
	if (!doc || doc.type !== 'doc') return [];
	const segments = [];
	let htmlAccum = '';

	const flushText = () => {
		if (htmlAccum) {
			segments.push(new ArticleTextSegment(htmlAccum, 1));
			htmlAccum = '';
		}
	};

	for (const node of (doc.content || [])) {
		if (node.type === 'heading') {
			flushText();
			segments.push(new ArticleHeaderSegment(inlineNodesToText(node.content), node.attrs?.level || 1));
		} else if (node.type === 'image') {
			flushText();
			const src = node.attrs?.src || '';
			if (src) {
				segments.push(new ArticleImagesSegment([src], node.attrs?.caption || ''));
			}
		} else {
			const html = pmNodeToHtml(node);
			if (html) {
				htmlAccum += html;
				if (node.type === 'paragraph') htmlAccum += '<br>';
			}
		}
	}

	flushText();
	return segments;
}

// ===== REUSABLE MAPPING FUNCTIONS =====

// Maps a Patreon post to the appropriate Platform content class
function mapPostToPlatformContent(post, context, includedLookup) {
	const attrs = post?.attributes;
	if (!attrs) return null;
	
	// Handle locked content
	if (!attrs.current_user_can_view) {
		return mapToLockedContent(post, context);
	}
	
	// Handle embedded content first
	if (attrs.embed?.url) {
		return mapToNestedMediaContent(post, context);
	}
	
	// Handle different post types
	switch (attrs.post_type) {
		case "video_external_file":
		case "video_embed":
			{
				const contentDetails = mapToVideoContent(post, context, includedLookup);

				if (contentDetails) {
					contentDetails.getContentRecommendations = function () {
						return source.getContentRecommendations(contentDetails.url);
					};
				}

				return contentDetails;
			}

		case "podcast":
		case "audio_file":
			{
				const isVideo = attrs.post_file && (attrs.post_file.width > 0 || attrs.post_file.url?.includes('.m3u8'));
				const contentDetails = isVideo ? mapToVideoContent(post, context, includedLookup) : mapToAudioContent(post, context, includedLookup);

				if (contentDetails) {
					contentDetails.getContentRecommendations = function () {
						return source.getContentRecommendations(contentDetails.url);
					};
				}

				return contentDetails;
			}

		case "text_only":
			return mapToTextContent(post, context, includedLookup);

		case "image_file":
			return mapToImageContent(post, context, includedLookup);

		default:
			return null;
	}
}

source.getContentRecommendations = function (url) {
	const postId = getPostIdFromUrl(url);

	if (!postId) {
		return new ContentPager([], false);
	}

	try {
		const postBody = httpGET({
			url: `https://www.patreon.com/api/posts/${postId}`,
			useAuthenticated: true,
			parseResponse: true
		});
		const campaign = postBody.included?.find(a => a.type == 'campaign');

		if (!campaign) {
			return new ContentPager([], false);
		}

		// Create campaign context for posts
		const campaignContext = {
			name: campaign.attributes?.name,
			url: campaign.attributes?.url,
			thumbnail: campaign.attributes?.avatar_photo_image_urls?.thumbnail,
			subscribers: campaign.attributes?.patron_count || 0
		};

		const requestUrl = BASE_URL_API + "/related_posts/" + campaign.id + "/" + postId + "?" + buildQuery({
			"include": "campaign,access_rules,access_rules.tier.null,attachments_media,audio,audio_preview.null,custom_thumbnail_media.null,drop,images,media,native_video_insights,poll.choices,poll.current_user_responses.user,poll.current_user_responses.choice,poll.current_user_responses.poll,shows.null,user,user_defined_tags,video.null,content_unlock_options.product_variant.null,content_unlock_options.reward.null,content_unlock_options.product_variant.collection.null,livestream,livestream.state,livestream.display,rss_synced_feed,post_new_comment_identity,post_new_comment_identity.avatar,post_new_comment_identity.community_profile,post_new_comment_identity.identity_badges",
			"fields[campaign]": "currency,show_audio_post_download_links,avatar_photo_url,avatar_photo_image_urls,earnings_visibility,is_nsfw,is_monthly,name,url,patron_count,primary_theme_color",
			"fields[post]": "attachments_preview_metadata,change_visibility_at,comment_count,commenter_count,content,content_json_string,created_at,current_user_can_comment,current_user_can_delete,current_user_can_report,current_user_can_view,current_user_comment_disallowed_reason,current_user_has_liked,embed,image,insights_last_updated_at,is_paid,is_preview_blurred,has_custom_thumbnail,like_count,meta_image_url,min_cents_pledged_to_view,monetization_ineligibility_reason,post_file,post_metadata,published_at,patreon_url,post_type,pledge_url,preview_asset_type,thumbnail,thumbnail_url,teaser_text,teaser_text_json_string,content_teaser_text,cleaned_teaser_text,title,upgrade_url,url,was_posted_by_campaign_owner,has_ti_violation,moderation_status,post_level_suspension_removal_date,pls_one_liners_by_category,video,video_preview,view_count,content_unlock_options,is_new_to_current_user,watch_state",
			"fields[post_tag]": "tag_type,value",
			"fields[user]": "image_url,full_name,url",
			"fields[access_rule]": "access_rule_type,amount_cents",
			"fields[livestream]": "display,state",
			"fields[media]": "id,image_urls,display,download_url,metadata,file_name,state",
			"fields[native_video_insights]": "average_view_duration,average_view_pct,has_preview,id,last_updated_at,num_views,preview_views,video_duration",
			"fields[content-unlock-option]": "content_unlock_type,is_current_user_eligible,reward_benefit_categories",
			"fields[product-variant]": "price_cents,currency_code,checkout_url,is_hidden,published_at_datetime,content_type,orders_count,access_metadata,live_sale_discounted_price_cents,live_sale_discounted_price_info",
			"fields[shows]": "id,title,description,thumbnail",
			"fields[display-identity]": "name,link_url",
			"fields[primary-image]": "image_icon",
			"fields[identity-badge]": "badge_type",
			"page[cursor]": "null",
			"page[count]": RECOMMENDATIONS_COUNT,
			"filter[is_by_creator]": "true",
			"filter[contains_exclusive_posts]": "true",
			"json-api-use-default-includes": "false",
			"json-api-version": "1.0"
		});
		
		const data = httpGET({
			url: requestUrl,
			useAuthenticated: true,
			parseResponse: true
		});

		const content = data?.data
			?.map(post => mapPostToPlatformContent(post, campaignContext, data))
			?.filter(content => content != null) ?? [];

		return new ContentPager(content, false);
	} catch (error) {
		if (error instanceof CaptchaRequiredException) throw error;
		return new ContentPager([], false);
	}
};

// Maps post to PlatformVideoDetails for video content
function mapToVideoContent(post, context, includedLookup) {
	const attrs = post?.attributes;
	if (!attrs?.post_file?.url) return null;

	const contentUrl = addUrlHint(attrs.url || (BASE_URL + "/posts/" + post.id), 'isPatreonMediaContent');

	return new PlatformVideoDetails({
		id: new PlatformID(config.name, post.id, config.id),
		name: attrs.title,
		author: createAuthor(post, context),
		datetime: toUnixSeconds(attrs.published_at),
		url: contentUrl,
		duration: attrs.post_file.duration || 0,
		description: (getPostHtmlContent(attrs) || attrs.teaser_text || "") + buildAttachmentsHtml(post, includedLookup),
		rating: new RatingLikes(attrs.like_count || 0),
		thumbnails: new Thumbnails([
			new Thumbnail(attrs.thumbnail?.url || attrs.image?.thumb_url, 1)
		].filter(t => t.url)),
		video: createVideoDescriptor(attrs.post_file)
	});
}

// Maps post to PlatformVideoDetails for audio content
function mapToAudioContent(post, context, includedLookup) {
	const attrs = post?.attributes;
	if (!attrs?.post_file?.url) return null;

	const contentUrl = addUrlHint(attrs.url || (BASE_URL + "/posts/" + post.id), 'isPatreonMediaContent');

	return new PlatformVideoDetails({
		id: new PlatformID(config.name, post.id, config.id),
		name: attrs.title,
		author: createAuthor(post, context),
		datetime: toUnixSeconds(attrs.published_at),
		url: contentUrl,
		duration: attrs.post_file.duration || 0,
		description: (getPostHtmlContent(attrs) || attrs.teaser_text || "") + buildAttachmentsHtml(post, includedLookup),
		rating: new RatingLikes(attrs.like_count || 0),
		thumbnails: new Thumbnails([
			new Thumbnail(attrs.thumbnail?.url || attrs.image?.thumb_url || attrs.post_file?.default_thumbnail?.url, 1)
		].filter(t => t.url)),
		video: new UnMuxVideoSourceDescriptor([], [
			new AudioUrlSource({
				name: "Audio",
				container: getAudioContainer(attrs.post_file.url),
				url: attrs.post_file.url,
				duration: attrs.post_file.duration,
				requestModifier: PATREON_REQUEST_MODIFIER
			})
		])
	});
}

// Builds an HTML block of download links for a post's file attachments (attachments_media),
// resolved from the included lookup (Map from createIncludedLookupMap or raw {included} body).
// Returns '' when the post has no downloadable attachments.
function buildAttachmentsHtml(post, includedLookup) {
	const refs = post?.relationships?.attachments_media?.data || [];
	const links = [];
	for (const ref of refs) {
		const media = includedLookup instanceof Map
			? includedLookup.get(`media:${ref.id}`)
			: includedLookup?.included?.find(m => m.id === ref.id && m.type === "media");
		const attrs = media?.attributes;
		if (!attrs?.download_url) continue;
		const name = (attrs.file_name || "Attachment").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
		const mb = attrs.size_bytes ? attrs.size_bytes / 1048576 : 0;
		const size = mb >= 1 ? mb.toFixed(1) + " MB" : (attrs.size_bytes ? Math.round(attrs.size_bytes / 1024) + " KB" : "");
		links.push('<p><a href="' + attrs.download_url + '" target="_blank">' + name + '</a>' + (size ? " (" + size + ")" : "") + '</p>');
	}
	return links.length ? '<p>Attachments:</p>' + links.join("") : '';
}

// Maps post to PlatformPostDetails (Desktop) or PlatformArticleDetails (Android) for text content
function mapToTextContent(post, context, includedLookup) {
	const attrs = post?.attributes;
	const htmlContent = getPostHtmlContent(attrs);
	if (!attrs?.title && !htmlContent) return null;

	let description = attrs?.teaser_text || "";
	if (htmlContent) {
		const text = domParser.parseFromString(htmlContent).text;
		description = text.length > MAX_DESCRIPTION_LENGTH
			? text.substring(0, MAX_DESCRIPTION_LENGTH) + "..."
			: text;
	}

	const contentUrl = addUrlHint(attrs.url || (BASE_URL + "/posts/" + post.id), 'isPatreonPostContent');
	const attachmentsHtml = buildAttachmentsHtml(post, includedLookup);
	// Attachment posts render as PlatformPostDetails (not article): Android's ArticleTextBlock installs
	// no link handler, so the download <a> is inert there. PostDetailFragment makes it tappable.
	const hasAttachment = (post?.relationships?.attachments_media?.data?.length ?? 0) > 0;

	const base = {
		id: new PlatformID(config.name, post.id, config.id),
		name: attrs.title || "Text Post",
		author: createAuthor(post, context),
		datetime: toUnixSeconds(attrs.published_at),
		url: contentUrl,
		rating: new RatingLikes(attrs.like_count || 0)
	};

	if (ARTICLES_ENABLED && !hasAttachment) {
		let segments = [];
		if (attrs?.content_json_string) {
			try {
				segments = proseMirrorToSegments(JSON.parse(attrs.content_json_string));
			} catch (e) { /* fall through */ }
		}
		if (segments.length === 0 && htmlContent) {
			segments = [new ArticleTextSegment(htmlContent, 1)];
		}
		if (segments.length > 0) {
			const article = new PlatformArticleDetails({
				...base,
				summary: description,
				thumbnails: new Thumbnails([
					new Thumbnail(attrs.thumbnail?.url || attrs.image?.thumb_url, 1)
				].filter(t => t.url)),
				segments
			});
			article.getContentRecommendations = function () {
				return source.getContentRecommendations(base.url);
			};
			return article;
		}
	}

	return new PlatformPostDetails({
		...base,
		description,
		textType: Type.Text.HTML,
		content: (htmlContent || attrs?.teaser_text || "") + attachmentsHtml,
		images: [],
		thumbnails: []
	});
}

// Maps post to PlatformPostDetails (Desktop) or PlatformArticleDetails (Android) for image content
function mapToImageContent(post, context, includedLookup) {
	const attrs = post?.attributes;
	if (!attrs?.post_metadata?.image_order) return null;

	// Use provided lookup or search in included data
	const images = attrs.post_metadata.image_order
		.map(id => {
			if (includedLookup instanceof Map) {
				return includedLookup.get(`media:${id}`);
			} else if (includedLookup?.included) {
				return includedLookup.included.find(item => item.id === id);
			}
			return null;
		})
		.filter(item => item && item.attributes?.image_urls);

	if (images.length === 0) return null;

	const htmlContent = getPostHtmlContent(attrs);
	let description = attrs?.teaser_text || "";
	if (htmlContent) {
		const text = domParser.parseFromString(htmlContent).text;
		description = text.length > MAX_DESCRIPTION_LENGTH
			? text.substring(0, MAX_DESCRIPTION_LENGTH) + "..."
			: text;
	}

	const imageUrls = images.map(img => img.attributes.image_urls.original);

	const contentUrl = addUrlHint(attrs.url || (BASE_URL + "/posts/" + post.id), 'isPatreonPostContent');
	const attachmentsHtml = buildAttachmentsHtml(post, includedLookup);
	// Attachment posts render as PlatformPostDetails (not article) so the download link is tappable on
	// Android (ArticleTextBlock installs no link handler).
	const hasAttachment = (post?.relationships?.attachments_media?.data?.length ?? 0) > 0;

	const base = {
		id: new PlatformID(config.name, post.id, config.id),
		name: attrs.title || "Image Post",
		author: createAuthor(post, context),
		datetime: toUnixSeconds(attrs.published_at),
		url: contentUrl,
		rating: new RatingLikes(attrs.like_count || 0)
	};

	// Post thumbnails is a list (one Thumbnails per image); article is a single representative
	// image. Each Thumbnails is an ascending-size ladder (px width; original = max).
	const imageToThumbnails = (img) => {
		const iu = img?.attributes?.image_urls ?? {};
		return new Thumbnails([
			new Thumbnail(iu.thumbnail, 360),
			new Thumbnail(iu.default_large, 1080),
			new Thumbnail(iu.original, 9999)
		].filter(t => t.url));
	};

	if (ARTICLES_ENABLED && !hasAttachment) {
		const segments = [new ArticleImagesSegment(imageUrls, "")];
		if (attrs?.content_json_string) {
			try {
				const textSegments = proseMirrorToSegments(JSON.parse(attrs.content_json_string));
				segments.push(...textSegments);
			} catch (e) { /* fall through */ }
		} else if (htmlContent) {
			segments.push(new ArticleTextSegment(htmlContent, 1));
		}
		const article = new PlatformArticleDetails({
			...base,
			summary: description,
			thumbnails: imageToThumbnails(images[0]),
			segments
		});
		article.getContentRecommendations = function () {
			return source.getContentRecommendations(base.url);
		};
		return article;
	}

	return new PlatformPostDetails({
		...base,
		description,
		textType: Type.Text.HTML,
		content: (htmlContent || "") + attachmentsHtml,
		images: imageUrls,
		thumbnails: images.map(imageToThumbnails)
	});
}

// Maps post to PlatformNestedMediaContent for embedded content
function mapToNestedMediaContent(post, context) {
	const attrs = post?.attributes;
	if (!attrs?.embed?.url) return null;
	
	return new PlatformNestedMediaContent({
		id: new PlatformID(config.name, post.id, config.id),
		name: attrs.title,
		author: createAuthor(post, context),
		datetime: toUnixSeconds(attrs.published_at),
		url: attrs.url || (BASE_URL + "/posts/" + post.id),
		contentUrl: attrs.embed.url,
		contentName: attrs.embed.subject,
		contentDescription: attrs.embed.description,
		contentProvider: attrs.embed.provider,
		contentThumbnails: new Thumbnails([
			new Thumbnail(attrs.thumbnail?.url || attrs.image?.url, 1)
		].filter(x => x.url))
	});
}

// Maps post to PlatformLockedContent for locked content
function mapToLockedContent(post, context) {
	const attrs = post?.attributes;
	if (_settings?.hideUnpaidContent) return null;
	
	return new PlatformLockedContent({
		id: new PlatformID(config.name, post.id, config.id),
		name: attrs.title,
		author: createAuthor(post, context),
		datetime: toUnixSeconds(attrs.published_at),
		url: attrs.url || (BASE_URL + "/posts/" + post.id),
		contentName: attrs.embed?.subject,
		contentThumbnails: new Thumbnails([
			new Thumbnail(attrs.thumbnail?.url || attrs.image?.thumb_url || attrs.image?.url || attrs.meta_image_url, 1)
		].filter(x => x.url)),
		lockDescription: "Exclusive for members",
		unlockUrl: attrs.url || (BASE_URL + "/posts/" + post.id),
	});
}

// Maps an audio file URL to its MIME container type. Patreon puts the real extension
// as the final path segment (e.g. .../1.mp3?token=...); falls back to audio/mpeg.
function getAudioContainer(url) {
	const path = (url || "").split('?')[0];
	const ext = path.split('.').pop().toLowerCase();
	return AUDIO_CONTAINER_BY_EXTENSION[ext] || DEFAULT_AUDIO_CONTAINER;
}

// Creates appropriate video descriptor based on file type
function createVideoDescriptor(postFile) {
	if (!postFile?.url) return null;
	
	if (postFile.url.includes('.m3u8')) {
		return new VideoSourceDescriptor([
			new HLSSource({
				name: "Original",
				duration: postFile.duration,
				url: postFile.url,
				requestModifier: PATREON_REQUEST_MODIFIER
			})
		]);
	} else {
		return new VideoSourceDescriptor([
			new VideoUrlSource({
				name: "Original",
				url: postFile.url,
				duration: postFile.duration,
				requestModifier: PATREON_REQUEST_MODIFIER
			})
		]);
	}
}

// Creates a Map for efficient lookup of included data
function createIncludedLookupMap(includedData) {
	const map = new Map();
	if (includedData) {
		for (const item of includedData) {
			const key = `${item.type}:${item.id}`;
			map.set(key, item);
		}
	}
	return map;
}

function throwIfCaptcha(resp) {
    if (resp != null && resp.body != null && resp.code == HTTP_STATUS_FORBIDDEN) {

        const body = resp.body.toLowerCase();

		// Check for Cloudflare captcha
        if (body.includes('/cdn-cgi/challenge-platform')) {
            throw new CaptchaRequiredException(resp.url, resp.body);
        }
    }
    return true;
}


/**
 * Builds a query string (without leading "?") from a params object.
 * Encodes values only (keys stay literal, so Patreon's bracketed keys like filter[campaign_id],
 * fields[post], page[cursor] are preserved). Array values become repeated keys (key=a&key=b).
 * undefined/null params are skipped (use for conditional params); "" and 0 are kept.
 */
function buildQuery(params) {
	const parts = [];
	for (const key in params) {
		const v = params[key];
		if (v === undefined || v === null) continue;
		if (Array.isArray(v)) {
			for (const item of v)
				if (item !== undefined && item !== null) parts.push(key + "=" + encodeURIComponent(item));
		} else {
			parts.push(key + "=" + encodeURIComponent(v));
		}
	}
	return parts.join("&");
}

/**
 * Gets the requested url and returns the response body either as a string or as a parsed json object
 * @param {Object} options - The options object
 * @param {string} options.url - The URL to call
 * @param {boolean} [options.useAuthenticated=false] - If true, will use the authenticated headers
 * @param {boolean} [options.parseResponse=true] - If true, will parse the response as json and check for errors
 * @param {Object} [options.headers=null] - Custom headers to use for the request
 * @returns {string | Object} the response body as a string or the parsed json object
 * @throws {ScriptException}
 */
// Performs the GET with retry + captcha detection + error handling; returns the raw response.
// resp.url is the final URL after redirects (also resp.body / resp.code / resp.headers / resp.isOk).
function httpRequest({ url, useAuthenticated = false, headers = null }) {
	const localHeaders = headers ?? PATREON_REQUEST_MODIFIER.headers;

	// Advanced setting: when disabled, make a single attempt (no retries).
	const maxRetries = isSettingEnabled(_settings?.useRequestRetries ?? true) ? HTTP_MAX_RETRIES : 0;

	let resp;
	for (let attempt = 0; attempt <= maxRetries; attempt++) {
		resp = http.GET(url, localHeaders, useAuthenticated);

		if (resp.isOk) break;

		// Surface Cloudflare challenges immediately; retry only transient 429/5xx.
		throwIfCaptcha(resp);
		const retriable = resp.code === HTTP_STATUS_TOO_MANY_REQUESTS || resp.code >= HTTP_STATUS_SERVER_ERROR_MIN;
		if (!retriable || attempt === maxRetries) {
			const error = new ScriptException("Request [" + url + "] failed with code [" + resp.code + "]");
			error.code = resp.code;
			throw error;
		}

		bridge.sleep(HTTP_RETRY_BASE_DELAY_MS * (attempt + 1));
	}

	return resp;
}

// Convenience wrapper over httpRequest: parses the JSON body; parseResponse:false returns the raw body.
function httpGET(options) {
	const resp = httpRequest(options);
	if (options.parseResponse === false) {
		return resp.body;
	}
	const json = JSON.parse(resp.body);
	if (json.errors && json.errors.length > 0) {
		throw new ScriptException(json.errors[0].message || "API returned errors");
	}
	return json;
}


// Extracts the vanity slug from a resolved channel's url (channel.url == https://www.patreon.com/{vanity})
function getVanityFromChannel(channel) {
	const m = (channel?.url || "").match(/patreon\.com\/([^\/?#]+)/);
	return m ? m[1] : null;
}

// Extracts the creator vanity from a channel URL for API resolution. Handles bare (/vanity) and
// prefixed (/c/vanity, /cw/vanity) forms. Returns null for reserved paths that aren't creator
// vanities (posts, profile, api, etc.) so those fall back to other handling.
const RESERVED_VANITY_SEGMENTS = new Set(["posts", "post", "profile", "api", "login", "search", "home", "messages", "settings", "user"]);
function getVanityFromUrl(url) {
	const m = (url || "").match(/patreon\.com\/(?:(?:c|cw)\/)?([^\/?#]+)/i);
	if (!m) return null;
	const vanity = m[1];
	return RESERVED_VANITY_SEGMENTS.has(vanity.toLowerCase()) ? null : vanity;
}

// Boolean settings arrive as real booleans from the app; coerce defensively (string "true" in tests).
function isSettingEnabled(v) {
	return v === true || v === "true";
}

// When enabled, surface trace/error messages as on-screen toasts for debugging; always logs.
function trace(msg, { showToast = false } = {}) {
	if (showToast || isSettingEnabled(_settings?.verboseNotifications)) {
		bridge.toast(msg);
	}
	log(msg);
}

// Resolves the configured playlist sort param from the "playlistSortOrderIndex" Dropdown setting.
function getPlaylistSort() {
	return PLAYLIST_SORTS[parseInt(_settings?.playlistSortOrderIndex ?? 0)] || PLAYLIST_SORTS[0];
}

// videoCount: count of playable media in the collection (from filter[media_types]=video,audio,
// meta.pagination.total). Pass -1 when unknown. Patreon's num_posts counts ALL post types
// (polls/images/text), so it can't be used here.
function collectionToPlatformPlaylist(col, vanity, author, videoCount) {
	const a = col.attributes || {};
	return new PlatformPlaylist({
		id: new PlatformID(config.name, col.id, config.id, PLATFORM_CLAIMTYPE),
		name: a.title || "Collection",
		author: author,
		url: BASE_URL + "/cw/" + vanity + "/collections?collection_id=" + col.id,
		thumbnail: a.thumbnail?.thumbnail_large || a.thumbnail?.original || a.thumbnail?.thumbnail || null,
		videoCount: (typeof videoCount === "number") ? videoCount : -1,
		datetime: toUnixSeconds(a.created_at)
	});
}

// URL that returns only the playable-media posts of a collection (cheap count probe with page[size]=1).
function collectionMediaPostsUrl(campaignId, collectionId, pageSize) {
	return BASE_URL_API + "/posts?" + buildQuery({
		"filter[campaign_id]": campaignId,
		"filter[collection_id]": collectionId,
		"filter[media_types]": "video,audio",
		"filter[contains_exclusive_posts]": "true",
		"sort": "collection_order",
		"fields[post]": "post_type",
		"page[size]": pageSize
	});
}

class SearchChannelContentPager extends ContentPager {
	constructor(campaignId, channel, query) {
		const initialResults = getPosts(campaignId, channel, undefined, undefined, query);
		super(initialResults.results, !!initialResults.nextPage);
		this.nextPageUrl = initialResults.nextPage;
		this.campaignId = campaignId;
		this.channel = channel;
		this.query = query;
	}
	nextPage() {
		if (!this.nextPageUrl) {
			this.hasMore = false;
			return this;
		}
		const newResults = getPosts(this.campaignId, this.channel, this.nextPageUrl, undefined, this.query) ?? { results: [] };
		this.results = newResults.results;
		this.nextPageUrl = newResults.nextPage;
		this.hasMore = !!newResults.nextPage;
		return this;
	}
}


/**
 * Adds a URL hint parameter to a URL if it doesn't already have one
 * @param {string} url - The URL to add the hint to
 * @param {string} hintParam - The hint parameter name (without the value)
 * @param {string} hintValue - The value for the hint parameter
 * @returns {string} - The URL with the hint parameter added
 */

function addUrlHint(url, hintParam, hintValue = '1') {
	if (!url) {
		return url;
	}

	// Check if hint already exists
	if (url.includes(`${hintParam}=${hintValue}`)) {
		return url;
	}

	try {
		const urlObj = new URL(url);
		urlObj.searchParams.append(hintParam, hintValue);
		return urlObj.toString();
	} catch (error) {
		// If URL parsing fails, return the original url unchanged.
		trace(`Error adding URL hint to ${url}: ${error}`);
		return url;
	}
}

/**
 * Checks whether a URL carries a given query parameter.
 * @param {string} url - The URL to inspect
 * @param {string} queryParamToCheck - The query parameter name to look for
 * @returns {boolean} true if present; false if absent or the URL is unparseable
 */
function hasQueryParam(url, queryParamToCheck) {
	try {
		return new URL(url).searchParams.has(queryParamToCheck);
	} catch (error) {
		// If URL parsing fails, treat the parameter as absent.
		trace(`Error checking URL hint in ${url}: ${error}`);
		return false;
	}
}


log("loaded");
