const PLATFORM = "Spotify";
const BASE_URL = "https://open.spotify.com";
const GRAPHQL_URL = "https://api-partner.spotify.com/pathfinder/v2/query";
const HOME_PLAYLIST_ID = "37i9dQZF1DXcBWIGoYBM5M";
const SEARCH_LIMIT = 30;
const FALLBACK_SEARCH_HASHES = [
    "eef7cc54888d91bdd6802623477873caa3948ae173a0c34fd86827b267e94c03",
    "d9f785900f0710b31c07818d617f4f7600c1e21217e80f5b043d1e78d74e6026"
];
const FALLBACK_PLAYLIST_HASHES = [
    "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf",
    "a65e12194ed5fc443a1cdebed5fabe33ca5b07b987185d63c72483867ad13cb4"
];

let config = {};
let runtime = {
    token: "", tokenExpiresAt: 0,
    searchHash: FALLBACK_SEARCH_HASHES[0],
    playlistHash: FALLBACK_PLAYLIST_HASHES[0]
};

source.enable = function (conf, settings, savedState) {
    config = conf || {};
    if (!savedState) return;
    try {
        const saved = JSON.parse(savedState);
        if (/^[a-f0-9]{64}$/.test(saved.searchHash || "")) runtime.searchHash = saved.searchHash;
        if (/^[a-f0-9]{64}$/.test(saved.playlistHash || "")) runtime.playlistHash = saved.playlistHash;
    } catch (_) { }
};

source.saveState = function () {
    return JSON.stringify({ searchHash: runtime.searchHash, playlistHash: runtime.playlistHash });
};

source.getSearchCapabilities = function () {
    return new ResultCapabilities([Type.Feed.Videos], [], []);
};

source.getSearchChannelContentsCapabilities = function () {
    return new ResultCapabilities([Type.Feed.Videos], [], []);
};

source.getChannelCapabilities = function () {
    return new ResultCapabilities([Type.Feed.Videos], [], []);
};

source.getHome = function () {
    return fetchPlaylistPage(HOME_PLAYLIST_ID, 0);
};

source.search = function (query) {
    return searchPage("tracks", query, 0);
};

source.searchChannels = function (query) {
    return searchPage("artists", query, 0);
};

source.searchPlaylists = function (query) {
    return searchPage("playlists", query, 0);
};

source.searchSuggestions = function (query) {
    const trimmed = (query || "").trim();
    return trimmed ? [trimmed] : [];
};

source.isContentDetailsUrl = function (url) {
    return spotifyUrl(url, "track") !== null;
};

source.getContentDetails = function (url) {
    const parsed = spotifyUrl(url, "track");
    if (!parsed) throw new ScriptException("Invalid Spotify track URL");
    const entity = fetchEmbedEntity("track", parsed.id);
    const video = trackToVideo(entity, bestImage(entity.visualIdentity?.image));
    // Grayjoy replaces this descriptor with a matching YouTube source before playback.
    // The catalogue URL is a metadata-only fallback for tracks without a public preview.
    const preview = entity.audioPreview?.url || url;
    const audio = [new AudioUrlSource({
        name: "Spotify preview",
        bitrate: 96000,
        container: "audio/mp3",
        codecs: "mp3",
        duration: Math.floor((entity.duration || 0) / 1000),
        url: preview,
        language: ""
    })];
    return new PlatformVideoDetails({
        id: video.id,
        name: video.name,
        thumbnails: video.thumbnails,
        author: video.author,
        uploadDate: video.uploadDate,
        duration: video.duration,
        viewCount: video.viewCount,
        url: video.url,
        isLive: false,
        description: entity.albumOfTrack?.name ? "Album: " + entity.albumOfTrack.name : "",
        video: new UnMuxVideoSourceDescriptor([], audio)
    });
};

source.isChannelUrl = function (url) {
    return spotifyUrl(url, "artist") !== null || syntheticArtistName(url) !== null;
};

source.getChannel = function (url) {
    let parsed = spotifyUrl(url, "artist");
    if (!parsed) {
        const name = syntheticArtistName(url);
        if (!name) throw new ScriptException("Invalid Spotify artist URL");
        parsed = findArtist(name);
    }
    const entity = fetchEmbedEntity("artist", parsed.id);
    const thumbnail = bestImage(entity.visualIdentity?.image);
    return new PlatformChannel({
        id: spotifyId(parsed.id),
        name: entity.name || entity.title || parsed.name || "Spotify artist",
        thumbnail: thumbnail,
        banner: thumbnail,
        subscribers: -1,
        description: "Spotify artist",
        url: artistUrl(parsed.id),
        links: { Spotify: artistUrl(parsed.id) }
    });
};

source.getChannelContents = function (url) {
    const channel = source.getChannel(url);
    const id = spotifyUrl(channel.url, "artist").id;
    const entity = fetchEmbedEntity("artist", id);
    const art = bestImage(entity.visualIdentity?.image);
    return new VideoPager((entity.trackList || []).map(track => trackToVideo(track, art)), false, {});
};

source.searchChannelContents = function (channelUrl, query) {
    const channel = source.getChannel(channelUrl);
    const words = normalize(query).split(" ").filter(Boolean);
    const pager = source.getChannelContents(channel.url);
    pager.results = pager.results.filter(video => words.every(word => normalize(video.name).indexOf(word) >= 0));
    return pager;
};

source.getChannelPlaylists = function (url) {
    const channel = source.getChannel(url);
    const artist = spotifyUrl(channel.url, "artist");
    const data = requestSearch(channel.name, 0, SEARCH_LIMIT);
    const albums = unwrapItems(data.albumsV2 || data.albums)
        .filter(album => artistUris(album).indexOf("spotify:artist:" + artist.id) >= 0)
        .map(albumToPlaylist);
    return new PlaylistPager(albums, false, {});
};

source.isPlaylistUrl = function (url) {
    return spotifyUrl(url, "album") !== null || spotifyUrl(url, "playlist") !== null;
};

source.getPlaylist = function (url) {
    const parsed = spotifyUrl(url, "album") || spotifyUrl(url, "playlist");
    if (!parsed) throw new ScriptException("Invalid Spotify album or playlist URL");
    const entity = fetchEmbedEntity(parsed.type, parsed.id);
    const art = bestImage(entity.coverArt?.sources || entity.visualIdentity?.image);
    const authorName = entity.subtitle || entity.authors?.map(x => x.name).join(", ") || "Spotify";
    const author = authorLink("", authorName, syntheticArtistUrl(authorName), art);
    const contents = parsed.type === "playlist"
        ? fetchPlaylistPage(parsed.id, 0)
        : new VideoPager((entity.trackList || []).map(track => trackToVideo(track, art)), false, {});
    return new PlatformPlaylistDetails({
        id: spotifyId(parsed.id),
        name: entity.name || entity.title || "Spotify playlist",
        author: author,
        thumbnail: art,
        url: spotifyEntityUrl(parsed.type, parsed.id),
        videoCount: contents.totalCount || contents.results.length,
        contents: contents
    });
};

function searchPage(kind, query, offset) {
    const data = requestSearch(query, offset, SEARCH_LIMIT);
    let results = [];
    let total = 0;
    if (kind === "tracks") {
        const page = data.tracksV2 || data.tracks || {};
        results = unwrapItems(page).map(trackToVideo);
        total = page.totalCount || results.length;
    } else if (kind === "artists") {
        const page = data.artists || {};
        results = unwrapItems(page).map(artistToChannel);
        total = page.totalCount || results.length;
    } else {
        const playlistPage = data.playlists || {};
        const albumPage = data.albumsV2 || data.albums || {};
        results = unwrapItems(playlistPage).map(playlistToPlaylist)
            .concat(unwrapItems(albumPage).map(albumToPlaylist));
        total = (playlistPage.totalCount || 0) + (albumPage.totalCount || 0);
    }
    const next = offset + SEARCH_LIMIT;
    return new SpotifySearchPager(results, next < total, { kind: kind, query: query, offset: next });
}

class SpotifySearchPager extends ContentPager {
    constructor(results, hasMore, context) {
        super(results, hasMore, context);
        this.context = context;
    }
    nextPage() {
        if (!this.hasMore) return new ContentPager([], false, this.context);
        return searchPage(this.context.kind, this.context.query, this.context.offset);
    }
}

function requestSearch(query, offset, limit) {
    const response = requestPersistedQuery(
        "searchDesktop",
        {
            includePreReleases: false,
            numberOfTopResults: 10,
            searchTerm: query,
            offset: offset,
            limit: limit,
            includeAudiobooks: false,
            includeAuthors: false
        },
        unique([runtime.searchHash].concat(FALLBACK_SEARCH_HASHES))
    );
    if (!response.data?.searchV2) throw new ScriptException("Spotify search is temporarily unavailable");
    runtime.searchHash = response.hash;
    return response.data.searchV2;
}

function fetchPlaylistPage(id, offset) {
    const response = requestPersistedQuery(
        "fetchPlaylistContents",
        { uri: "spotify:playlist:" + id, offset: offset, limit: SEARCH_LIMIT },
        unique([runtime.playlistHash].concat(FALLBACK_PLAYLIST_HASHES))
    );
    const content = response.data?.playlistV2?.content;
    if (!content) throw new ScriptException("Spotify playlist is temporarily unavailable");
    runtime.playlistHash = response.hash;
    const videos = (content.items || []).map(item => item?.itemV2?.data).filter(Boolean).map(trackToVideo);
    const next = offset + SEARCH_LIMIT;
    const pager = new SpotifyPlaylistPager(videos, next < (content.totalCount || videos.length), { id: id, offset: next });
    pager.totalCount = content.totalCount || videos.length;
    return pager;
}

class SpotifyPlaylistPager extends VideoPager {
    constructor(results, hasMore, context) {
        super(results, hasMore, context);
        this.context = context;
    }
    nextPage() {
        if (!this.hasMore) return new VideoPager([], false, this.context);
        return fetchPlaylistPage(this.context.id, this.context.offset);
    }
}

function requestPersistedQuery(operationName, variables, hashes) {
    ensureRuntime(false);
    for (let authAttempt = 0; authAttempt < 2; authAttempt++) {
        for (let i = 0; i < hashes.length; i++) {
            const body = JSON.stringify({
                variables: variables,
                operationName: operationName,
                extensions: { persistedQuery: { version: 1, sha256Hash: hashes[i] } }
            });
            const response = http.POST(GRAPHQL_URL, body, {
                Authorization: "Bearer " + runtime.token,
                "Content-Type": "application/json",
                "App-Platform": "WebPlayer",
                Origin: BASE_URL,
                Referer: BASE_URL + "/"
            }, false);
            if (response.code === 401 || response.code === 403) break;
            if (!response.isOk) continue;
            const parsed = parseJson(response.body, "Spotify search response");
            if (parsed.data) return { data: parsed.data, hash: hashes[i] };
        }
        ensureRuntime(true);
        hashes = operationName === "searchDesktop"
            ? unique([runtime.searchHash].concat(FALLBACK_SEARCH_HASHES))
            : unique([runtime.playlistHash].concat(FALLBACK_PLAYLIST_HASHES));
    }
    throw new ScriptException("Spotify catalogue request is temporarily unavailable");
}

function ensureRuntime(force) {
    const now = Date.now();
    if (!force && runtime.token && runtime.tokenExpiresAt > now + 60000) return;
    const bootstrap = fetchNextData(BASE_URL + "/embed/playlist/" + HOME_PLAYLIST_ID);
    const session = bootstrap.props?.pageProps?.state?.settings?.session;
    if (!session?.accessToken) throw new ScriptException("Spotify did not provide an anonymous catalogue token");
    runtime.token = session.accessToken;
    runtime.tokenExpiresAt = session.accessTokenExpirationTimestampMs || now + 900000;
    discoverQueryHashes();
}

function discoverQueryHashes() {
    try {
        const page = getText(BASE_URL + "/search");
        const route = firstMatch(page, /https:\/\/open\.spotifycdn\.com\/cdn\/build\/web-player\/xpui-routes-search\.[a-f0-9]+\.js/);
        if (!route) return;
        const script = getText(route);
        const hash = firstGroup(script, /["']searchDesktop["']\s*,\s*["']query["']\s*,\s*["']([a-f0-9]{64})["']/);
        if (hash) runtime.searchHash = hash;
        const mainRoute = firstMatch(page, /https:\/\/open\.spotifycdn\.com\/cdn\/build\/web-player\/web-player\.[a-f0-9]+\.js/);
        if (mainRoute) {
            const mainScript = getText(mainRoute);
            const playlistHash = firstGroup(mainScript, /["']fetchPlaylistContents["']\s*,\s*["']query["']\s*,\s*["']([a-f0-9]{64})["']/);
            if (playlistHash) runtime.playlistHash = playlistHash;
        }
    } catch (error) {
        log("Spotify search query discovery failed; using the last known query: " + error);
    }
}

function fetchEmbedEntity(type, id) {
    const data = fetchNextData(BASE_URL + "/embed/" + type + "/" + id);
    const entity = data.props?.pageProps?.state?.data?.entity;
    if (!entity) throw new ScriptException("Spotify " + type + " is unavailable");
    const session = data.props?.pageProps?.state?.settings?.session;
    if (session?.accessToken) {
        runtime.token = session.accessToken;
        runtime.tokenExpiresAt = session.accessTokenExpirationTimestampMs || Date.now() + 900000;
    }
    return entity;
}

function fetchNextData(url) {
    const html = getText(url);
    const marker = 'id="__NEXT_DATA__"';
    const start = html.indexOf(marker);
    if (start < 0) throw new ScriptException("Spotify page data was not found");
    const bodyStart = html.indexOf(">", start) + 1;
    const end = html.indexOf("</script>", bodyStart);
    if (end < 0) throw new ScriptException("Spotify page data was incomplete");
    return parseJson(html.substring(bodyStart, end), "Spotify page data");
}

function getText(url) {
    const response = http.GET(url, {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/145 Safari/537.36",
        Accept: "text/html,application/json;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.8"
    }, false);
    if (!response.isOk) throw new ScriptException("Spotify request failed with HTTP " + response.code);
    return response.body || "";
}

function trackToVideo(track, coverHint) {
    const uri = track.uri || "";
    const id = spotifyUriId(uri) || track.id || track.uid || normalize(track.title || track.name);
    const artists = trackArtists(track);
    const firstArtist = artists[0] || { name: track.subtitle || "Spotify", uri: "" };
    const image = bestImage(track.albumOfTrack?.coverArt?.sources || track.visualIdentity?.image) || coverHint || "";
    return new PlatformVideo({
        id: spotifyId(id),
        name: track.name || track.title || "Spotify track",
        thumbnails: new Thumbnails(image ? [new Thumbnail(image, 0)] : []),
        author: authorLink(spotifyUriId(firstArtist.uri), firstArtist.name, artistUrlOrSynthetic(firstArtist), ""),
        uploadDate: 0,
        url: trackUrl(id),
        duration: Math.floor(durationMs(track) / 1000),
        viewCount: -1,
        isLive: false
    });
}

function artistToChannel(artist) {
    const id = spotifyUriId(artist.uri) || artist.id;
    const name = artist.profile?.name || artist.name || "Spotify artist";
    const image = bestImage(artist.visuals?.avatarImage?.sources || artist.visualIdentity?.image);
    return new PlatformChannel({
        id: spotifyId(id), name: name, thumbnail: image, banner: image,
        subscribers: artist.stats?.followers || -1, description: "Spotify artist",
        url: artistUrl(id), links: { Spotify: artistUrl(id) }
    });
}

function albumToPlaylist(album) {
    const id = spotifyUriId(album.uri) || album.id;
    const artists = album.artists?.items || [];
    const artist = artists[0] || {};
    const name = artist.profile?.name || artist.name || "Spotify";
    const image = bestImage(album.coverArt?.sources);
    return new PlatformPlaylist({
        id: spotifyId(id), name: album.name || "Spotify album",
        author: authorLink(spotifyUriId(artist.uri), name, artistUrlOrSynthetic({ uri: artist.uri, name: name }), ""),
        thumbnail: image, url: spotifyEntityUrl("album", id), videoCount: album.tracks?.totalCount || -1
    });
}

function playlistToPlaylist(playlist) {
    const id = spotifyUriId(playlist.uri) || playlist.id;
    const owner = playlist.ownerV2?.data || playlist.owner || {};
    const image = bestImage(playlist.images?.items?.[0]?.sources || playlist.images?.items || playlist.coverArt?.sources);
    return new PlatformPlaylist({
        id: spotifyId(id), name: playlist.name || "Spotify playlist",
        author: authorLink(owner.username || owner.id || "", owner.name || owner.displayName || "Spotify", BASE_URL, ""),
        thumbnail: image, url: spotifyEntityUrl("playlist", id), videoCount: playlist.content?.totalCount || -1
    });
}

function findArtist(name) {
    const data = requestSearch(name, 0, 10);
    const artists = unwrapItems(data.artists || {});
    if (!artists.length) throw new ScriptException("Spotify artist not found");
    const wanted = normalize(name);
    const exact = artists.find(artist => normalize(artist.profile?.name || artist.name) === wanted) || artists[0];
    return { id: spotifyUriId(exact.uri) || exact.id, name: exact.profile?.name || exact.name };
}

function unwrapItems(page) {
    return (page?.items || []).map(item => item?.item?.data || item?.data || item).filter(Boolean);
}

function trackArtists(track) {
    const items = Array.isArray(track.artists) ? track.artists : track.artists?.items;
    if (items?.length) return items.map(item => ({ name: item.profile?.name || item.name || "Spotify", uri: item.uri || "" }));
    return (track.subtitle || "Spotify").split(/,|\u00a0/).map(name => ({ name: name.trim(), uri: "" })).filter(x => x.name);
}

function artistUris(album) {
    return (album.artists?.items || []).map(item => item.uri).filter(Boolean);
}

function durationMs(track) {
    return track.duration?.totalMilliseconds || track.trackDuration?.totalMilliseconds || track.duration || 0;
}

function bestImage(sources) {
    if (!sources?.length) return "";
    return sources.slice().sort((a, b) => ((b.width || b.maxWidth || 0) - (a.width || a.maxWidth || 0)))[0]?.url || "";
}

function spotifyUrl(url, expectedType) {
    const match = String(url || "").match(/open\.spotify\.com\/(?:intl-[a-z-]+\/)?(track|artist|album|playlist)\/([A-Za-z0-9]+)/i);
    if (!match || (expectedType && match[1].toLowerCase() !== expectedType)) return null;
    return { type: match[1].toLowerCase(), id: match[2] };
}

function syntheticArtistName(url) {
    const match = String(url || "").match(/open\.spotify\.com\/grayjoy\/artist\/([^?#]+)/i);
    return match ? decodeURIComponent(match[1]) : null;
}

function spotifyUriId(uri) { return String(uri || "").split(":").pop() || ""; }
function spotifyId(id) { return new PlatformID(PLATFORM, id || "unknown", config.id || ""); }
function trackUrl(id) { return spotifyEntityUrl("track", id); }
function artistUrl(id) { return spotifyEntityUrl("artist", id); }
function spotifyEntityUrl(type, id) { return BASE_URL + "/" + type + "/" + id; }
function syntheticArtistUrl(name) { return BASE_URL + "/grayjoy/artist/" + encodeURIComponent(name || "Spotify"); }
function artistUrlOrSynthetic(artist) { return spotifyUriId(artist.uri) ? artistUrl(spotifyUriId(artist.uri)) : syntheticArtistUrl(artist.name); }
function authorLink(id, name, url, thumbnail) { return new PlatformAuthorLink(spotifyId(id || normalize(name)), name || "Spotify", url || BASE_URL, thumbnail || ""); }
function normalize(value) { return String(value || "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim(); }
function unique(items) { return items.filter((item, index) => item && items.indexOf(item) === index); }
function firstMatch(text, regex) { const match = String(text || "").match(regex); return match ? match[0] : ""; }
function firstGroup(text, regex) { const match = String(text || "").match(regex); return match ? match[1] : ""; }
function parseJson(value, label) { try { return JSON.parse(value); } catch (_) { throw new ScriptException(label + " was invalid"); } }

log("Spotify source loaded");
