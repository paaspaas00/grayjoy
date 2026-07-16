# Dailymotion plugin for Grayjay

Click [here](https://plugins.grayjay.app/) to install into Grayjay

## Features
- [x] - Home
- [x] - Home search
- [x] - Live videos from home search
- [x] - Search autocomplete
- [x] - Video details (likes, dislikes, views, publish date)
- [x] - Subscriptions
- [x] - Channel search
- [x] - Channel details
- [x] - Channel playlist tab
- [x] - Downloads
- [x] - Playlists search
- [x] - Settings / Thumbnail resolution
- [x] - Settings / Creator avatar resolution
- [x] - Settings / Hide sensitive content
- [x] - Settings / Prefered Country
- [x] - Sign in (import subscriptions and playlists (and Likes, Favorites, Recently Watched))
- [x] - Polycentric Comments
- [x] - Subtitles
- [x] - Platform comments (not generally available on Platform) - (WIP)

## Work in progress (WIP)

- [ ] - Harbor account verification


## Grayjay
- [ ] - Live filter in Subscriptions tab
- [ ] - Thumbnails for vertical videos

## Dailymotion
- [ ] - API fields deprecated with replacement not available yet (createdAt - createDate requires authentication, video?.viewCount, video?.stats?.views?.total);
- [ ] - Platform comments

## Install
npm install

## Build

npm run build

npm start - will build and watch for changes

## Notes
- Content of the 'build' folder should not be manually changed since it's recreated for each build. 
- Used [rollup](https://rollupjs.org/) to bundle the scripts.