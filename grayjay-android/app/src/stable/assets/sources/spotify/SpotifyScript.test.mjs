import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import vm from "node:vm";

const source = {};
class Model { constructor(value) { Object.assign(this, value); } }
class PlatformID { constructor(platform, value, pluginId) { this.platform = platform; this.value = value; this.pluginId = pluginId; } }
class PlatformAuthorLink { constructor(id, name, url, thumbnail) { Object.assign(this, { id, name, url, thumbnail }); } }
class Thumbnail { constructor(url, quality) { Object.assign(this, { url, quality }); } }
class Thumbnails { constructor(sources) { this.sources = sources; } }
class ContentPager {
    constructor(results = [], hasMore = false, context = {}) { Object.assign(this, { results, hasMore, context }); }
    hasMorePagers() { return this.hasMore; }
}
class VideoPager extends ContentPager {}
class PlaylistPager extends ContentPager {}
class ScriptException extends Error {}

const track = {
    uri: "spotify:track:track1", name: "No Surprises",
    artists: { items: [{ uri: "spotify:artist:artist1", profile: { name: "Radiohead" } }] },
    albumOfTrack: { name: "OK Computer", coverArt: { sources: [{ width: 300, url: "https://i.scdn.co/cover.jpg" }] } },
    duration: { totalMilliseconds: 229000 }
};
const artist = {
    uri: "spotify:artist:artist1", profile: { name: "Radiohead" },
    visuals: { avatarImage: { sources: [{ width: 300, url: "https://i.scdn.co/artist.jpg" }] } }
};
const album = {
    uri: "spotify:album:album1", name: "OK Computer",
    artists: { items: [{ uri: artist.uri, profile: artist.profile }] },
    coverArt: { sources: [{ width: 300, url: "https://i.scdn.co/album.jpg" }] }
};
const playlist = {
    uri: "spotify:playlist:playlist1", name: "Radiohead essentials",
    ownerV2: { data: { name: "Listener" } }, images: { items: [{ sources: [{ width: 300, url: "https://i.scdn.co/list.jpg" }] }] }
};

const nextPage = entity => `<script id="__NEXT_DATA__" type="application/json">${JSON.stringify({
    props: { pageProps: { state: { data: { entity }, settings: { session: {
        accessToken: "anonymous-token", accessTokenExpirationTimestampMs: Date.now() + 3_600_000, isAnonymous: true
    } } } } }
})}</script>`;

const http = {
    GET(url) {
        if (url.endsWith("/search")) return { isOk: true, code: 200, body: '<link href="https://open.spotifycdn.com/cdn/build/web-player/xpui-routes-search.1234abcd.js">' };
        if (url.includes("xpui-routes-search")) return { isOk: true, code: 200, body: 'new q("searchDesktop","query","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",null)' };
        if (url.includes("/embed/album/album1")) return { isOk: true, code: 200, body: nextPage({ type: "album", id: "album1", title: "OK Computer", subtitle: "Radiohead", coverArt: album.coverArt, trackList: [{ uri: track.uri, title: track.name, subtitle: "Radiohead", duration: 229000 }] }) };
        if (url.includes("/embed/playlist/")) return { isOk: true, code: 200, body: nextPage({ type: "playlist", id: "home", title: "Home", coverArt: playlist.images.items[0], trackList: [{ uri: track.uri, title: track.name, subtitle: "Radiohead", duration: 229000 }] }) };
        if (url.includes("/embed/track/track1")) return { isOk: true, code: 200, body: nextPage({ ...track, artists: [{ name: "Radiohead", uri: "spotify:artist:artist1" }], type: "track", id: "track1", title: track.name, duration: 229000, visualIdentity: { image: track.albumOfTrack.coverArt.sources }, audioPreview: { url: "https://p.scdn.co/preview.mp3" } }) };
        if (url.includes("/embed/artist/artist1")) return { isOk: true, code: 200, body: nextPage({ type: "artist", id: "artist1", title: "Radiohead", visualIdentity: { image: artist.visuals.avatarImage.sources }, trackList: [{ uri: track.uri, title: track.name, subtitle: "Radiohead", duration: 229000 }] }) };
        throw new Error(`Unexpected GET ${url}`);
    },
    POST(url, body, headers) {
        assert.equal(url, "https://api-partner.spotify.com/pathfinder/v2/query");
        assert.equal(headers.Authorization, "Bearer anonymous-token");
        const request = JSON.parse(body);
        if (request.operationName === "fetchPlaylistContents") {
            assert.equal(request.extensions.persistedQuery.sha256Hash, "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf");
            return { isOk: true, code: 200, body: JSON.stringify({ data: { playlistV2: { content: {
                items: [{ itemV2: { data: track } }], totalCount: 1
            } } } }) };
        }
        assert.match(request.extensions.persistedQuery.sha256Hash, /^[a-f0-9]{64}$/);
        return { isOk: true, code: 200, body: JSON.stringify({ data: { searchV2: {
            tracksV2: { items: [{ item: { data: track } }], totalCount: 1 },
            artists: { items: [{ data: artist }], totalCount: 1 },
            albumsV2: { items: [{ data: album }], totalCount: 1 },
            playlists: { items: [{ data: playlist }], totalCount: 1 }
        } } }) };
    }
};

const context = vm.createContext({
    source, http, console, log() {}, Date, JSON, Math, Map, Set, Error,
    encodeURIComponent, decodeURIComponent,
    Type: { Feed: { Videos: "Videos" } },
    ResultCapabilities: Model, PlatformID, PlatformAuthorLink, Thumbnail, Thumbnails,
    PlatformVideo: Model, PlatformVideoDetails: Model, PlatformChannel: Model,
    PlatformPlaylist: Model, PlatformPlaylistDetails: Model, AudioUrlSource: Model,
    UnMuxVideoSourceDescriptor: class { constructor(videoSources, audioSources) { Object.assign(this, { videoSources, audioSources }); } },
    ContentPager, VideoPager, PlaylistPager, ScriptException
});
const script = fs.readFileSync(new URL("./SpotifyScript.js", import.meta.url), "utf8");
vm.runInContext(script, context);
source.enable({ id: "plugin-id" }, {}, "");

test("search exposes tracks, artists, albums and playlists without a login", () => {
    const videos = source.search("Radiohead").results;
    assert.equal(videos[0].name, "No Surprises");
    assert.equal(videos[0].url, "https://open.spotify.com/track/track1");
    assert.equal(videos[0].duration, 229);

    const creators = source.searchChannels("Radiohead").results;
    assert.equal(creators[0].name, "Radiohead");
    assert.equal(creators[0].url, "https://open.spotify.com/artist/artist1");

    const playlists = source.searchPlaylists("Radiohead").results;
    assert.deepEqual(playlists.map(item => item.name), ["Radiohead essentials", "OK Computer"]);
});

test("albums and track details preserve Spotify identity", () => {
    const details = source.getPlaylist("https://open.spotify.com/album/album1");
    assert.equal(details.name, "OK Computer");
    assert.equal(details.contents.results[0].url, "https://open.spotify.com/track/track1");

    const trackDetails = source.getContentDetails("https://open.spotify.com/track/track1");
    assert.equal(trackDetails.name, "No Surprises");
    assert.equal(trackDetails.author.name, "Radiohead");
    assert.equal(trackDetails.video.audioSources[0].url, "https://p.scdn.co/preview.mp3");

    const playlistDetails = source.getPlaylist("https://open.spotify.com/playlist/playlist1");
    assert.equal(playlistDetails.contents.results[0].name, "No Surprises");
});
