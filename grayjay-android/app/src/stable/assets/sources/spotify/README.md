# spoti-grayjoy

This Grayjay-compatible source exposes Spotify's public catalogue without requesting a Spotify login. Search results, public artists, albums, playlists, artwork, titles, artists, and durations come from Spotify's public web and Embed surfaces.

Grayjoy does not stream the Spotify recording. When a track is opened, the app searches YouTube using the track title, artist, and duration, rejects likely covers or altered versions, and resolves the selected result with the configured YouTube playback engine. Spotify links and catalogue metadata remain visible in the library and player.

The anonymous web token is short-lived and kept only in memory. The plugin discovers the current persisted search and playlist query identifiers from Spotify's web assets and retains previous identifiers as bounded fallbacks. Public playlist contents are paginated.

## Install

Add this configuration URL as a source:

`https://raw.githubusercontent.com/paaspaas00/spoti-grayjoy/main/SpotifyConfig.json`

Grayjoy 3.1.0 and later also include the source in the built-in source catalogue.

## Test

Run `node --test SpotifyScript.test.mjs` with Node.js 20 or later.

Spotify is a trademark of Spotify AB. This source is not affiliated with or endorsed by Spotify.
