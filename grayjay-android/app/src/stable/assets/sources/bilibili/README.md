# BiliBili

## What does logging in get you?

- Subtitles
- Greater than 480p quality
- Premium content
- View and save private playlists
- View and save subscriptions list
- Prevents some rate limiting/captcha scenarios

## Development

1. `npm run npm-dev` or `bun run bun-dev`
2. load `BiliBiliConfig.json` into Grayjay

## TO-DO

- [ ] Figure out captcha verification for space posts and space videos or at least proper rate
  limiting error messages The captcha only shows up for posts. for videos there is just a login
  prompt that shows up whenever taking an action However once the posts captcha is done the login
  prompts stop Clear site data and go to `https://space.bilibili.com/<space id>/dynamic` and scroll
  down until the captcha shows up
- [ ] Write tests for regex matching and testing code
- [ ] Write tests for the all of the request functions to continually verify that they are
  retrieving expected data
    - bangumi with free and premium content <https://www.bilibili.com/bangumi/play/ss43366>
    - course with free and premium content <https://www.bilibili.com/cheese/play/ss410>
    - creator premium video <https://www.bilibili.com/video/BV12z421y7Lw>
- [ ] Write tests for all the utility functions
- [ ] Review TODOs in the code
- [ ] Review the hardcoded costants
- [ ] We might need to create id prefixes there are a lot of number ids and the different types
  probably overlap I don't quite understand the usefulness of the ids though
- [ ] Add in function and property documentation
- [ ] Add playback tracker so that the homepage reflects and adapts to what you watch
- [ ] Add a logging option that will log the urls, headers, and results of all network requests
- [ ] Implement live chat websocket protocol
- [ ] Fix the local caching to use saveState
- [ ] Reduce the lines of code somehow
- [ ] Show a locked content object for inaccesible premium content
- [ ] Investigate and handle other url types (mobile video links?)
- [ ] Handle correctly formatted links that don't actually point to any content
- [ ] there is additional content on the home page that we can consider loading in the future
- [ ] consider switching from like rating to 0-10 scale rating for bangumi
- [ ] playlist search should be redone but there needs to be better support for playlist search in Grayjay
- [ ] this is a sort of playlist that we might consider adding support for <https://www.bilibili.com/blackboard/era/YyCI1Zsg5iUjelX2.html>
- [ ] consider switching from using JSON apis to HTML with regex. this could improve performance
  because the latency of the html is less than the json
- [ ] remove bad post search results that are simply duplicates to video search results
- [ ] bangumi should have a creator page per show based off <https://www.bilibili.com/bangumi/media/md28339940>
- [ ] implement monitization/merch features
- [x] (should be fixed now) there is an issue where space_request doesn't work right. it might be ip
  address allow list related. like watching a video seems like it might make things work in the
  future
- [ ] update to use the new playlist api to get channel playlists
- [ ] <https://github.com/futo-org/grayjay-android/issues/1273>

## Pending Fixed Grayjay Bugs

## Grayjay Bugs

- [ ] HLS for live streaming is a little wonky in the UI
- [ ] Something glitchy with the posts tab on the subscriptions page
- [ ] Opening direct links to posts doesn't work
- [ ] When making a live PlatformVideoDetails the video option needs to be a blank
  VideoSourceDescriptor which is kinda weird
- [ ] the getSubtitles call is wrapped in an exception swalling thing so when i was hitting a non
  allow listed domain it was just silently failing
- [ ] Support for playlists, posts, movies, shows, and custom playlist types as search feed type
- [ ] The removal of elements from the getlivechatwindow doesn't work likely because it runs before
  the elements have been created
- [ ] Searching from the subscriptions page doesn't searchyour subscriptions it searches everything
- [ ] Ordering options and filters can not be specified on a per feed type basis
- [ ] Issues with passing search feed type to search api calls
    - when searching from the main home page search type in the search function call is always null
      regardless of what is set in getsearchcapabilities
    - when searching channel contents from a channel page search type in the searchChannelContents
      function call is always null regardless of what is set in getsearchchannelcontentscapabilities
    - getchannelcapabilities and getchannelcontents don't ask for LIVE or POSTS types
- [ ] the first time when trying to open a channel link the app will say the link isn't supported
- [ ] non utf characters don't get saved to the filename when exporting. this is really bad for
  simplified chinese characters
- [ ] when playling a playlist the UnavailableException doesn't display and the playlist simply
  skips the video
- [ ] removing a downloaded playlist doesn't remove the downloads (essentially a storage leak)
