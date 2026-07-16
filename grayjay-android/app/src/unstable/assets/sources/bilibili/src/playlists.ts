import type {
    Params,
    CollectionResponse,
    SeasonResponse,
    SeriesResponse,
    CourseResponse,
    FavoritesResponse,
    FestivalResponse,
    WatchLaterResponse,
    PlaylistType,
    RequestMetadata,
    SpaceResponse,
    LoggedInNavResponse
} from "./types.ts"
import {
    PLATFORM,
    SPACE_URL_PREFIX,
    VIDEO_URL_PREFIX,
    EPISODE_URL_PREFIX,
    COURSE_EPISODE_URL_PREFIX,
    COLLECTION_URL_PREFIX,
    SERIES_URL_PREFIX,
    SEASON_URL_PREFIX,
    COURSE_URL_PREFIX,
    FAVORITES_URL_PREFIX,
    FESTIVAL_URL_PREFIX,
    WATCH_LATER_URL,
    WATCH_LATER_ID,
    HARDCODED_THUMBNAIL_QUALITY,
    HARDCODED_ZERO,
    EMPTY_AUTHOR,
    local_http,
    PLAYLIST_URL_REGEX,
    MISSING_NAME,
    USER_AGENT
} from "./constants.ts"
import { get_local_state, get_local_storage_cache } from "./state.ts"
import { create_signed_url, create_url, execute_requests, log_network_call, assert_exhaustive, session_cookie } from "./utilities.ts"
import { season_request, course_request } from "./content.ts"
import { nav_request } from "./enable.ts"

export function isPlaylistUrl(url: string) {
    return PLAYLIST_URL_REGEX.test(url)
}
export function getPlaylist(url: string) {
    const regex_match_result = url.match(PLAYLIST_URL_REGEX)
    if (regex_match_result === null) {
        throw new ScriptException(`malformed space url: ${url}`)
    }
    const maybe_playlist_type: PlaylistType | undefined = regex_match_result[3] as PlaylistType | undefined
    if (maybe_playlist_type === undefined) {
        throw new ScriptException("unreachable regex error")
    }
    const playlist_type = maybe_playlist_type
    const maybe_playlist_id: string | undefined = regex_match_result[4]
    if (maybe_playlist_id === undefined) {
        throw new ScriptException("unreachable regex error")
    }
    switch (playlist_type) {
        case "/channel/collectiondetail?sid=": {
            const maybe_space_id: string | undefined = regex_match_result[2]
            if (maybe_space_id === undefined) {
                throw new ScriptException("unreachable regex error")
            }
            const space_id = parseInt(maybe_space_id)
            const collection_id = parseInt(maybe_playlist_id)

            const page_size = 30
            const initial_page = 1

            let collection_response: CollectionResponse
            let space_info = get_local_storage_cache().space_cache.get(space_id)
            if (space_info === undefined) {
                const requests: [
                    RequestMetadata<SpaceResponse>,
                    RequestMetadata<{ data: { follower: number } }>,
                    RequestMetadata<CollectionResponse>
                ] = [{
                    request(builder) { return space_request(space_id, builder) },
                    process(response) { return JSON.parse(response.body) }
                }, {
                    request(builder) { return fan_count_request(space_id, builder) },
                    process(response) { return JSON.parse(response.body) }
                }, {
                    request(builder) { return collection_request(space_id, collection_id, initial_page, page_size, builder) },
                    process(response) { return JSON.parse(response.body) }
                }]
                const results = execute_requests(requests)
                const [space, fan_info] = [results[0], results[1]]
                if (space.code !== 0) {
                    throw new ScriptException("Failed to load space info")
                }
                collection_response = results[2]
                space_info =
                    space_info = {
                        num_fans: fan_info.data.follower,
                        name: space.data.name,
                        face: space.data.face,
                        live_room: space.data.live_room === null ? null : {
                            title: space.data.live_room.title,
                            roomid: space.data.live_room.roomid,
                            live_status: space.data.live_room.liveStatus === 1,
                            cover: space.data.live_room.cover, watched_show: {
                                num: space.data.live_room.watched_show.num
                            }
                        }
                    }
                get_local_storage_cache().space_cache.set(space_id, space_info)
            } else {
                const raw_response = collection_request(space_id, collection_id, initial_page, page_size)
                collection_response = JSON.parse(raw_response.body)
            }

            const author_id = new PlatformID(PLATFORM, space_id.toString(), plugin.config.id)
            const author = new PlatformAuthorLink(
                author_id,
                space_info.name,
                `${SPACE_URL_PREFIX}${space_id}`,
                space_info.face, space_info.num_fans
            )
            const contents = new CollectionContentsPager(
                space_id,
                author,
                collection_id,
                collection_response,
                initial_page,
                page_size
            )

            return new PlatformPlaylistDetails({
                id: new PlatformID(PLATFORM, collection_id.toString(), plugin.config.id),
                name: collection_response.data.meta.name,
                author,
                url: `${SPACE_URL_PREFIX}${space_id}${COLLECTION_URL_PREFIX}${collection_id}`,
                contents,
                videoCount: collection_response.data.meta.total,
            })
        }
        case "bangumi/play/ss": {
            const season_id = parseInt(maybe_playlist_id)
            const season_response: SeasonResponse = JSON.parse(season_request({ id: season_id, type: "season" }).body)
            return format_season(season_id, season_response)
        }
        case "/channel/seriesdetail?sid=": {
            const maybe_space_id: string | undefined = regex_match_result[2]
            if (maybe_space_id === undefined) {
                throw new ScriptException("unreachable regex error")
            }
            const space_id = parseInt(maybe_space_id)
            const series_id = parseInt(maybe_playlist_id)

            const initial_page = 1
            const page_size = 30

            let series_response: SeriesResponse

            let space_info = get_local_storage_cache().space_cache.get(space_id)
            if (space_info === undefined) {
                const requests: [
                    RequestMetadata<SpaceResponse>,
                    RequestMetadata<{ data: { follower: number } }>,
                    RequestMetadata<SeriesResponse>
                ] = [{
                    request(builder) { return space_request(space_id, builder) },
                    process(response) { return JSON.parse(response.body) }
                }, {
                    request(builder) { return fan_count_request(space_id, builder) },
                    process(response) { return JSON.parse(response.body) }
                }, {
                    request(builder) { return series_request(space_id, series_id, initial_page, page_size, builder) },
                    process(response) { return JSON.parse(response.body) }
                }]
                const results = execute_requests(requests)
                const [space, fan_info] = [results[0], results[1]]
                if (space.code !== 0) {
                    throw new ScriptException("Failed to load space info")
                }
                series_response = results[2]
                space_info =
                    space_info = {
                        num_fans: fan_info.data.follower,
                        name: space.data.name,
                        face: space.data.face,
                        live_room: space.data.live_room === null ? null : {
                            title: space.data.live_room.title,
                            roomid: space.data.live_room.roomid,
                            live_status: space.data.live_room.liveStatus === 1,
                            cover: space.data.live_room.cover, watched_show: {
                                num: space.data.live_room.watched_show.num
                            }
                        }
                    }
                get_local_storage_cache().space_cache.set(space_id, space_info)
            } else {
                const raw_response = series_request(space_id, series_id, initial_page, page_size)
                series_response = JSON.parse(raw_response.body)
            }

            const author_id = new PlatformID(PLATFORM, space_id.toString(), plugin.config.id)
            const author = new PlatformAuthorLink(
                author_id,
                space_info.name,
                `${SPACE_URL_PREFIX}${space_id}`,
                space_info.face,
                space_info.num_fans)

            return new PlatformPlaylistDetails({
                id: new PlatformID(PLATFORM, series_id.toString(), plugin.config.id),
                name: MISSING_NAME,
                author,
                url: `${SPACE_URL_PREFIX}${space_id}${SERIES_URL_PREFIX}${series_id}`,
                contents: new SeriesContentsPager(space_id, author, series_id, series_response, initial_page, page_size),
                videoCount: series_response.data.page.total
            })
        }
        case "cheese/play/ss": {
            const season_id = parseInt(maybe_playlist_id)
            const course_response: CourseResponse = JSON.parse(course_request({ type: "season", id: season_id }).body)
            return format_course(season_id, course_response)
        }
        case "medialist/detail/ml": {
            const favorites_id = parseInt(maybe_playlist_id)
            return format_favorites(load_favorites(favorites_id, 1, 20))
        }
        case "/favlist?fid=": {
            const favorites_id = parseInt(maybe_playlist_id)
            return format_favorites(load_favorites(favorites_id, 1, 20))
        }
        case "festival/": {
            const festival_id = maybe_playlist_id
            return format_festival(festival_id, festival_parse(festival_request(festival_id)))
        }
        case "watchlater/": {
            if (!bridge.isLoggedIn()) {
                throw new LoginRequiredException("Login to view watch later")
            }
            const requests: [RequestMetadata<LoggedInNavResponse>, RequestMetadata<WatchLaterResponse>] = [
                {
                    request(builder) { return nav_request(true, builder) },
                    process(response) { return JSON.parse(response.body) }
                }, {
                    request(builder) { return watch_later_request(true, builder) },
                    process(response) { return JSON.parse(response.body) }
                }
            ]
            const [nav_response, watch_later_response] = execute_requests(requests)
            const videos = watch_later_response.data.list.map(function (video) {
                const url = `${VIDEO_URL_PREFIX}${video.bvid}`

                // update cid cache
                get_local_storage_cache().cid_cache.set(video.bvid, video.cid)

                const video_id = new PlatformID(PLATFORM, video.bvid.toString(), plugin.config.id)
                const author = new PlatformAuthorLink(
                    new PlatformID(PLATFORM, video.owner.mid.toString(), plugin.config.id),
                    video.owner.name,
                    `${SPACE_URL_PREFIX}${video.owner.mid}`,
                    video.owner.face,
                    get_local_storage_cache().space_cache.get(video.owner.mid)?.num_fans)
                return new PlatformVideo({
                    id: video_id,
                    name: video.title,
                    url: url,
                    thumbnails: new Thumbnails([new Thumbnail(video.pic, HARDCODED_THUMBNAIL_QUALITY)]),
                    author,
                    duration: video.duration,
                    viewCount: video.stat.view,
                    isLive: false,
                    shareUrl: url,
                    datetime: Number(video.pubdate)
                })
            })
            const first_video = watch_later_response.data.list[0]
            if (first_video === undefined) {
                throw new ScriptException("unreachable")
            }

            const author = new PlatformAuthorLink(
                new PlatformID(PLATFORM, nav_response.data.mid.toString(), plugin.config.id),
                nav_response.data.uname,
                `${SPACE_URL_PREFIX}${nav_response.data.mid}`,
                nav_response.data.face,
                get_local_storage_cache().space_cache.get(nav_response.data.mid)?.num_fans)
            return new PlatformPlaylistDetails({
                id: new PlatformID(PLATFORM, WATCH_LATER_ID, plugin.config.id),
                name: "稍后再看", // Watch Later
                author,
                url: WATCH_LATER_URL,
                contents: new VideoPager(videos, false),
                videoCount: watch_later_response.data.count,
            })
        }
        default:
            throw assert_exhaustive(playlist_type, "unreachable")
    }
}
export class CollectionContentsPager extends VideoPager {
    private readonly space_id: number
    private readonly author: PlatformAuthorLink
    private readonly collection_id: number
    private next_page: number
    private readonly page_size: number
    constructor(space_id: number, author: PlatformAuthorLink, collection_id: number, collection_response: CollectionResponse, initial_page: number, page_size: number) {
        const more = collection_response.data.meta.total > initial_page * page_size
        super(format_collection(author, collection_response), more)
        this.next_page = initial_page + 1
        this.page_size = page_size
        this.author = author
        this.collection_id = collection_id
        this.space_id = space_id
    }
    override nextPage(this: CollectionContentsPager): CollectionContentsPager {
        const raw_response = collection_request(this.space_id, this.collection_id, this.next_page, this.page_size)
        const collection_response: CollectionResponse = JSON.parse(raw_response.body)
        this.hasMore = collection_response.data.meta.total > this.next_page * this.page_size
        this.results = format_collection(this.author, collection_response)
        this.next_page += 1
        return this
    }
    override hasMorePagers(this: CollectionContentsPager): boolean {
        return this.hasMore
    }
}
export function format_collection(author: PlatformAuthorLink, collection_response: CollectionResponse): PlatformVideo[] {
    const videos = collection_response.data.archives.map(function (video) {
        const url = `${VIDEO_URL_PREFIX}${video.bvid}`
        const video_id = new PlatformID(PLATFORM, video.bvid, plugin.config.id)

        return new PlatformVideo({
            id: video_id,
            name: video.title,
            url: url,
            thumbnails: new Thumbnails([new Thumbnail(video.pic, HARDCODED_THUMBNAIL_QUALITY)]),
            author,
            duration: video.duration,
            viewCount: video.stat.view,
            isLive: false,
            shareUrl: url,
            datetime: Number(video.pubdate)
        })
    })
    return videos
}
export function collection_request(space_id: number, collection_id: number, page: number, page_size: number, builder: BatchBuilder): BatchBuilder
export function collection_request(space_id: number, collection_id: number, page: number, page_size: number): BridgeHttpResponse<string>
export function collection_request(space_id: number, collection_id: number, page: number, page_size: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const collection_prefix = "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list"
    const params: Params = {
        mid: space_id.toString(),
        season_id: collection_id.toString(),
        page_num: page.toString(),
        page_size: page_size.toString()
    }
    const playlist_url = create_url(collection_prefix, params)
    const buvid3 = get_local_state().buvid3

    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(
        playlist_url.toString(),
        { Cookie: `buvid3=${buvid3}` },
        false
    )
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function format_season(season_id: number, season_response: SeasonResponse): PlatformPlaylistDetails {
    const episodes = season_response.result.episodes.map(function (episode) {
        const url = `${EPISODE_URL_PREFIX}${episode.ep_id}`
        const video_id = new PlatformID(PLATFORM, episode.ep_id.toString(), plugin.config.id)

        // update cid cache
        get_local_storage_cache().cid_cache.set(episode.bvid, episode.cid)

        return new PlatformVideo({
            id: video_id,
            name: episode.long_title,
            url: url,
            thumbnails: new Thumbnails([new Thumbnail(episode.cover, HARDCODED_THUMBNAIL_QUALITY)]),
            author: EMPTY_AUTHOR,
            duration: episode.duration / 1000,
            viewCount: season_response.result.stat.views,
            isLive: false,
            shareUrl: url,
            datetime: Number(episode.pub_time)
        })
    })
    return new PlatformPlaylistDetails({
        id: new PlatformID(PLATFORM, season_id.toString(), plugin.config.id),
        name: season_response.result.title,
        author: EMPTY_AUTHOR,
        url: `${SEASON_URL_PREFIX}${season_id}`,
        contents: new VideoPager(episodes, false),
        videoCount: season_response.result.episodes.length,
    })
}
export class SeriesContentsPager extends VideoPager {
    private readonly space_id: number
    private readonly author: PlatformAuthorLink
    private readonly series_id: number
    private next_page: number
    private readonly page_size: number
    constructor(
        space_id: number,
        author: PlatformAuthorLink,
        series_id: number,
        initial_series_response: SeriesResponse,
        initial_page: number,
        page_size: number
    ) {
        const more = initial_series_response.data.page.total > initial_page * page_size
        super(format_series(author, initial_series_response), more)
        this.next_page = initial_page + 1
        this.page_size = page_size
        this.author = author
        this.series_id = series_id
        this.space_id = space_id
    }
    override nextPage(this: SeriesContentsPager): SeriesContentsPager {
        const raw_response = series_request(this.space_id, this.series_id, this.next_page, this.page_size)
        const series_response: SeriesResponse = JSON.parse(raw_response.body)
        this.hasMore = series_response.data.page.total > this.next_page * this.page_size
        this.results = format_series(this.author, series_response)
        this.next_page += 1
        return this
    }
    override hasMorePagers(this: SeriesContentsPager): boolean {
        return this.hasMore
    }
}
export function format_series(author: PlatformAuthorLink, series_response: SeriesResponse): PlatformVideo[] {
    const videos = series_response.data.archives.map(function (video) {
        const url = `${VIDEO_URL_PREFIX}${video.bvid}`
        const video_id = new PlatformID(PLATFORM, video.bvid, plugin.config.id)

        return new PlatformVideo({
            id: video_id,
            name: video.title,
            url: url,
            thumbnails: new Thumbnails([new Thumbnail(video.pic, HARDCODED_THUMBNAIL_QUALITY)]),
            author,
            duration: video.duration,
            viewCount: video.stat.view,
            isLive: false,
            shareUrl: url,
            datetime: Number(video.pubdate)
        })
    })
    return videos
}
export function series_request(space_id: number, series_id: number, page: number, page_size: number, builder: BatchBuilder): BatchBuilder
export function series_request(space_id: number, series_id: number, page: number, page_size: number): BridgeHttpResponse<string>
export function series_request(space_id: number, series_id: number, page: number, page_size: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const series_prefix = "https://api.bilibili.com/x/series/archives"
    const params: Params = {
        mid: space_id.toString(),
        series_id: series_id.toString(),
        page_num: page.toString(),
        page_size: page_size.toString()
    }
    const playlist_url = create_url(series_prefix, params)
    const buvid3 = get_local_state().buvid3
    const now = Date.now()
    const runner = builder === undefined ? local_http : builder
    const result = runner.GET(
        playlist_url.toString(),
        { Cookie: `buvid3=${buvid3}` },
        false
    )
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function format_course(season_id: number, course_response: CourseResponse): PlatformPlaylistDetails {
    const author = new PlatformAuthorLink(
        new PlatformID(PLATFORM, course_response.data.up_info.mid.toString(), plugin.config.id),
        course_response.data.up_info.uname,
        `${SPACE_URL_PREFIX}${course_response.data.up_info.mid}`,
        course_response.data.up_info.avatar,
        course_response.data.up_info.follower)

    const episodes = course_response.data.episodes.map(function (episode) {
        const url = `${COURSE_EPISODE_URL_PREFIX}${episode.id}`
        const video_id = new PlatformID(PLATFORM, episode.id.toString(), plugin.config.id)

        return new PlatformVideo({
            id: video_id,
            name: episode.title,
            url: url,
            thumbnails: new Thumbnails([new Thumbnail(episode.cover, HARDCODED_THUMBNAIL_QUALITY)]),
            author,
            // TODO missing duration
            // duration:
            viewCount: episode.play,
            isLive: false,
            shareUrl: url,
            datetime: Number(episode.release_date)
        })
    })
    return new PlatformPlaylistDetails({
        id: new PlatformID(PLATFORM, season_id.toString(), plugin.config.id),
        name: course_response.data.title,
        author,
        url: `${COURSE_URL_PREFIX}${season_id}`,
        contents: new VideoPager(episodes, false),
        videoCount: course_response.data.ep_count,
    })
}
export function load_favorites(favorites_id: number, page: number, page_size: number): FavoritesResponse {
    const series_prefix = "https://api.bilibili.com/x/v3/fav/resource/list"
    const params: Params = {
        media_id: favorites_id.toString(),
        pn: page.toString(),
        ps: page_size.toString()
    }
    const url = create_url(series_prefix, params)
    const buvid3 = get_local_state().buvid3
    const now = Date.now()
    // use the authenticated client so logged in users can view their private favorites lists
    const json = local_http.GET(
        url.toString(),
        { Cookie: `buvid3=${buvid3}` },
        true
    ).body
    log_network_call(now)
    const results: FavoritesResponse = JSON.parse(json)
    return results
}
export function format_favorites(response: FavoritesResponse) {
    const favorites_id = response.data.info.id
    return new PlatformPlaylistDetails({
        id: new PlatformID(PLATFORM, favorites_id.toString(), plugin.config.id),
        name: response.data.info.title,
        author: new PlatformAuthorLink(
            new PlatformID(PLATFORM, response.data.info.upper.mid.toString(), plugin.config.id),
            response.data.info.upper.name,
            `${SPACE_URL_PREFIX}${response.data.info.upper.mid}`,
            response.data.info.upper.face,
            get_local_storage_cache().space_cache.get(response.data.info.upper.mid)?.num_fans),
        url: `${FAVORITES_URL_PREFIX}${favorites_id}`,
        contents: new FavoritesContentsPager(favorites_id, response, 1, 20),
        videoCount: response.data.info.media_count,
    })
}
export class FavoritesContentsPager extends VideoPager {
    private readonly favorites_id: number
    private next_page: number
    private readonly page_size: number
    constructor(favorites_id: number, favorites_response: FavoritesResponse, initial_page: number, page_size: number) {
        const more = favorites_response.data.has_more
        super(format_favorites_videos(favorites_response), more)
        this.next_page = initial_page + 1
        this.page_size = page_size
        this.favorites_id = favorites_id
    }
    override nextPage(this: FavoritesContentsPager): FavoritesContentsPager {
        const favorites_response = load_favorites(this.favorites_id, this.next_page, this.page_size)
        this.hasMore = favorites_response.data.has_more
        this.results = format_favorites_videos(favorites_response)
        this.next_page += 1
        return this
    }
    override hasMorePagers(this: FavoritesContentsPager): boolean {
        return this.hasMore
    }
}
export function format_favorites_videos(favorites_response: FavoritesResponse): PlatformVideo[] {
    if (!favorites_response.data?.medias) {
        log("WARNING: favorites response has no data or medias: " + JSON.stringify(favorites_response))
        return []
    }
    const videos = favorites_response.data.medias.map(function (video) {
        const url = `${VIDEO_URL_PREFIX}${video.bvid}`
        const video_id = new PlatformID(PLATFORM, video.bvid, plugin.config.id)

        return new PlatformVideo({
            id: video_id,
            name: video.title,
            url: url,
            thumbnails: new Thumbnails([new Thumbnail(video.cover, HARDCODED_THUMBNAIL_QUALITY)]),
            author: new PlatformAuthorLink(
                new PlatformID(PLATFORM, video.upper.mid.toString(), plugin.config.id),
                video.upper.name,
                `${SPACE_URL_PREFIX}${video.upper.mid}`,
                video.upper.face,
                get_local_storage_cache().space_cache.get(video.upper.mid)?.num_fans),
            duration: video.duration,
            viewCount: video.cnt_info.play,
            isLive: false,
            shareUrl: url,
            datetime: Number(video.pubtime)
        })
    })
    return videos
}
export function festival_request(festival_id: string, builder: BatchBuilder): BatchBuilder
export function festival_request(festival_id: string): BridgeHttpResponse<string>
export function festival_request(festival_id: string, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const festival_url = `${FESTIVAL_URL_PREFIX}${festival_id}`
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(festival_url.toString(), {}, false)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function festival_parse(festival_html: BridgeHttpResponse<string>): FestivalResponse {
    const festival_html_regex = /<script>window\.__INITIAL_STATE__=({.*?});\(function\(\){var s;\(s=document\.currentScript\|\|document\.scripts\[document\.scripts\.length-1\]\)\.parentNode\.removeChild\(s\);}\(\)\);<\/script>/
    const match_result = festival_html.body.match(festival_html_regex)
    if (match_result === null) {
        throw new ScriptException("unreachable")
    }
    const json = match_result[1]
    if (json === undefined) {
        throw new ScriptException("unreachable")
    }
    const results: FestivalResponse = JSON.parse(json)
    return results
}
export function format_festival(festival_id: string, festival_response: FestivalResponse): PlatformPlaylistDetails {
    const episodes = festival_response.sectionEpisodes.map(function (episode) {
        const url = `${VIDEO_URL_PREFIX}${episode.bvid}`
        const video_id = new PlatformID(PLATFORM, episode.bvid, plugin.config.id)

        // cache cids
        get_local_storage_cache().cid_cache.set(episode.bvid, episode.cid)

        return new PlatformVideo({
            id: video_id,
            name: episode.title,
            url: url,
            thumbnails: new Thumbnails([new Thumbnail(episode.cover, HARDCODED_THUMBNAIL_QUALITY)]),
            author: new PlatformAuthorLink(
                new PlatformID(PLATFORM, episode.author.mid.toString(), plugin.config.id),
                episode.author.name,
                `${SPACE_URL_PREFIX}${episode.author.mid}`,
                episode.author.face,
                get_local_storage_cache().space_cache.get(episode.author.mid)?.num_fans),
            // TODO potentially load this some other way
            // duration: episode.duration / 1000,
            // TODO load this some other way
            viewCount: 0,
            isLive: false,
            shareUrl: url,
            // TODO load this some other way
            datetime: HARDCODED_ZERO
        })
    })
    return new PlatformPlaylistDetails({
        id: new PlatformID(PLATFORM, festival_id.toString(), plugin.config.id),
        name: festival_response.title,
        author: EMPTY_AUTHOR,
        url: `${FESTIVAL_URL_PREFIX}${festival_id}`,
        contents: new VideoPager(episodes, false),
        videoCount: festival_response.sectionEpisodes.length,
    })
}
export function watch_later_request(logged_in: true, builder: BatchBuilder): BatchBuilder
export function watch_later_request(logged_in: true): BridgeHttpResponse<string>
export function watch_later_request(logged_in: true, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const watch_later_url = "https://api.bilibili.com/x/v2/history/toview/web"
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    // use the authenticated client because watch later is only available when logged in
    const result = runner.GET(
        watch_later_url,
        {},
        logged_in
    )
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}

function fan_count_request(space_id: number, builder: BatchBuilder): BatchBuilder
function fan_count_request(space_id: number): BridgeHttpResponse<string>
function fan_count_request(space_id: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const space_stat_url_prefix = "https://api.bilibili.com/x/relation/stat"
    const url = create_url(
        space_stat_url_prefix,
        {
            vmid: space_id.toString()
        }).toString()
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(url, {}, false)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
function space_request(space_id: number, builder: BatchBuilder): BatchBuilder
function space_request(space_id: number): BridgeHttpResponse<string>
function space_request(space_id: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const space_stat_url_prefix = "https://api.bilibili.com/x/space/wbi/acc/info"
    const params: Params = {
        mid: space_id.toString(),
    }
    const url = create_signed_url(space_stat_url_prefix, params).toString()
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(
        url,
        {
            Referer: "https://space.bilibili.com",
            Host: "api.bilibili.com",
            "User-Agent": USER_AGENT,
            Cookie: session_cookie()
        },
        true)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
