import type {
    Params,
    SpaceVideosSearchResponse,
    MaybeSpaceVideosSearchResponse,
    SpacePostsResponse,
    MaybeSpacePostsResponse,
    SpacePostsSearchResponse,
    SpaceCollectionsResponse,
    SpaceCoursesResponse,
    SpaceFavoritesResponse,
    SpaceBangumiResponse,
    RequestMetadata,
    ChannelTypeCapabilities,
    ChannelSearchTypeCapabilities,
    OrderOptions,
    CoreSpaceInfo,
    Card,
    SpaceResponse,
    FilterGroupIDs
} from "./types.ts"
import {
    PLATFORM,
    SPACE_URL_PREFIX,
    VIDEO_URL_PREFIX,
    POST_URL_PREFIX,
    LIVE_ROOM_URL_PREFIX,
    COLLECTION_URL_PREFIX,
    SERIES_URL_PREFIX,
    SEASON_URL_PREFIX,
    COURSE_URL_PREFIX,
    FAVORITES_URL_PREFIX,
    HARDCODED_THUMBNAIL_QUALITY,
    HARDCODED_ZERO,
    MISSING_NAME,
    USER_AGENT,
    local_http,
    IS_IMPERSONATION_AVAILABLE
} from "./constants.ts"
import { get_local_state, get_local_storage_cache } from "./state.ts"
import {
    create_signed_url,
    create_url,
    execute_requests,
    log_network_call,
    assert_exhaustive,
    session_cookie,
    trace,
    parse_minutes_seconds,
    CompositeContentPager,
    CompositePlaylistPager
} from "./utilities.ts"
import { parse_space_url, space_request, fan_count_request } from "./channel.ts"
import { format_text_node, format_major } from "./content.ts"

declare const httpimp: HTTP | undefined

export function getChannelCapabilities() {
    return new ResultCapabilities<FilterGroupIDs, ChannelTypeCapabilities>([
        Type.Feed.Mixed,
        Type.Feed.Live,
        Type.Feed.Videos,
    ], [
        Type.Order.Chronological,
        Type.Order.Favorites,
        Type.Order.Views
    ], [])
}
export function getChannelContents(
    url: string,
    type: ChannelTypeCapabilities | null,
    order: Order | null,
    filters: FilterQuery<FilterGroupIDs> | null
) {
    if (!type) {
        type = Type.Feed.Mixed
    }
    if (filters) {
        throw new ScriptException("unreachable")
    }
    if (order && type !== Type.Feed.Videos) {
        trace("order only applies to videos")
    }

    const space_id = parse_space_url(url)
    const effective_order = order || Type.Order.Chronological

    switch (type) {
        case Type.Feed.Videos:
            return new SpaceVideosContentPager(space_id, 1, 25, effective_order)
        case Type.Feed.Mixed: {
            const posts_pager = new SpacePostsContentPager(space_id)
            const videos_pager = new SpaceVideosContentPager(space_id, 1, 25, effective_order)
            const live_pager = get_space_live_pager(space_id)
            return new CompositeContentPager([live_pager, videos_pager, posts_pager])
        }

        case Type.Feed.Live: {
            return get_space_live_pager(space_id)
        }
        default:
            throw assert_exhaustive(type, "unreachable")
    }
}
export function get_space_live_pager(space_id: number): VideoPager {
    let space_info = get_local_storage_cache().space_cache.get(space_id)
    if (space_info === undefined) {
        const requests: [
            RequestMetadata<SpaceResponse>,
            RequestMetadata<{ data: { follower: number } }>
        ] = [{
            request(builder) { return space_request(space_id, builder) },
            process(response) { return JSON.parse(response.body) }
        }, {
            request(builder) { return fan_count_request(space_id, builder) },
            process(response) { return JSON.parse(response.body) }
        }]

        const [space, fan_count_response] = execute_requests(requests)

        if (space.code !== 0) {
            trace("Failed loading space info")
            return new VideoPager([], false)
        }

        space_info = {
            num_fans: fan_count_response.data.follower,
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

        // cache results
        get_local_storage_cache().space_cache.set(space_id, space_info)
    }
    const author_id = new PlatformID(PLATFORM, space_id.toString(), plugin.config.id)
    const author = new PlatformAuthorLink(
        author_id,
        space_info.name,
        `${SPACE_URL_PREFIX}${space_id}`,
        space_info.face,
        space_info.num_fans
    )
    const live_room = space_info.live_room !== null
        && space_info.live_room.live_status === true
        ? [new PlatformVideo({
            id: new PlatformID(PLATFORM, space_info.live_room.roomid.toString(), plugin.config.id),
            name: space_info.live_room.title,
            url: `${LIVE_ROOM_URL_PREFIX}${space_info.live_room.roomid}`,
            thumbnails: new Thumbnails([new Thumbnail(space_info.live_room.cover, HARDCODED_THUMBNAIL_QUALITY)]),
            author,
            viewCount: space_info.live_room.watched_show.num,
            isLive: true,
            shareUrl: `${LIVE_ROOM_URL_PREFIX}${space_info.live_room.roomid}`,
            // TODO load from cache. "now" is incorrect but it does result in sorting to the top
            // It would be better however to load the actual stream start time
            datetime: Date.now() / 1000
        })]
        : []
    return new VideoPager(live_room, false)
}
export class SpaceCollectionsContentPager extends PlaylistPager {
    private next_page: number
    private readonly page_size: number
    private readonly space_info: CoreSpaceInfo
    private readonly space_id: number
    constructor(space_id: number, initial_page: number, page_size: number,) {
        let space_info = get_local_storage_cache().space_cache.get(space_id)
        let space_collections_response: SpaceCollectionsResponse
        if (space_info === undefined) {
            const requests: [
                RequestMetadata<SpaceCollectionsResponse>,
                RequestMetadata<SpaceResponse>,
                RequestMetadata<{ data: { follower: number } }>
            ] = [
                    {
                        request(builder) {
                            return space_collections_request(space_id, initial_page, page_size, builder)
                        },
                        process(response) { return JSON.parse(response.body) }
                    }, {
                        request(builder) { return space_request(space_id, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }, {
                        request(builder) { return fan_count_request(space_id, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }
                ]
            const results = execute_requests(requests)
            const space = results[1]
            if (space.code !== 0) {
                throw new ScriptException("Failed to load space info")
            }
            space_info = {
                num_fans: results[2].data.follower,
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
            space_collections_response = results[0]
        } else {
            space_collections_response = JSON.parse(space_collections_request(space_id, initial_page, page_size).body)
        }

        const has_more = space_collections_response.data.items_lists.page.total > initial_page * page_size
        super(
            format_space_collections(space_collections_response, space_id, space_info),
            has_more
        )
        this.next_page = initial_page + 1
        this.page_size = page_size
        this.space_id = space_id
        this.space_info = space_info
    }
    override nextPage(this: SpaceCollectionsContentPager): SpaceCollectionsContentPager {
        const space_collections_response: SpaceCollectionsResponse = JSON.parse(space_collections_request(this.space_id, this.next_page, this.page_size).body)

        this.results = format_space_collections(space_collections_response, this.space_id, this.space_info)

        this.hasMore = space_collections_response.data.items_lists.page.total > this.next_page * this.page_size
        this.next_page += 1

        return this
    }
    override hasMorePagers(this: SpaceCollectionsContentPager): boolean {
        return this.hasMore
    }
}
export function space_collections_request(space_id: number, page: number, page_size: number, builder: BatchBuilder): BatchBuilder
export function space_collections_request(space_id: number, page: number, page_size: number): BridgeHttpResponse<string>
export function space_collections_request(space_id: number, page: number, page_size: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const collection_prefix = "https://api.bilibili.com/x/polymer/web-space/seasons_series_list"
    const params: Params = {
        mid: space_id.toString(),
        page_num: page.toString(),
        page_size: page_size.toString()
    }
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(
        create_signed_url(collection_prefix, params).toString(),
        { Cookie: `buvid3=${get_local_state().buvid3}` },
        false)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function format_space_collections(space_collections_response: SpaceCollectionsResponse, space_id: number, space_info: CoreSpaceInfo): PlatformPlaylist[] {
    const author_id = new PlatformID(PLATFORM, space_id.toString(), plugin.config.id)
    const author = new PlatformAuthorLink(
        author_id,
        space_info.name,
        `${SPACE_URL_PREFIX}${space_id}`,
        space_info.face,
        space_info.num_fans
    )

    return space_collections_response.data.items_lists.seasons_list.map(function (season) {
        return new PlatformPlaylist({
            id: new PlatformID(PLATFORM, season.meta.season_id.toString(), plugin.config.id),
            name: season.meta.name,
            author,
            url: `${SPACE_URL_PREFIX}${space_id}${COLLECTION_URL_PREFIX}${season.meta.season_id}`,
            videoCount: season.meta.total,
            thumbnail: season.meta.cover
        })
    }).concat(
        space_collections_response.data.items_lists.series_list.map(function (series) {
            return new PlatformPlaylist({
                id: new PlatformID(PLATFORM, series.meta.series_id.toString(), plugin.config.id),
                name: series.meta.name,
                author,
                url: `${SPACE_URL_PREFIX}${space_id}${SERIES_URL_PREFIX}${series.meta.series_id}`,
                videoCount: series.meta.total,
                thumbnail: series.meta.cover
            })
        }))
}

export class SpaceBangumiContentPager extends PlaylistPager {
    private next_page: number
    private readonly page_size: number
    private readonly space_info: CoreSpaceInfo
    private readonly space_id: number
    /**
     *
     * @param space_id
     * @param initial_page
     * @param page_size
     * @param type i'm not entirely sure what this does i think it's a different type of bangumi
     */
    constructor(space_id: number, initial_page: number, page_size: number, private readonly type: 1 | 2) {
        let space_info = get_local_storage_cache().space_cache.get(space_id)
        let space_bangumi_response: SpaceBangumiResponse
        if (space_info === undefined) {
            const requests: [
                RequestMetadata<SpaceBangumiResponse>,
                RequestMetadata<SpaceResponse>,
                RequestMetadata<{ data: { follower: number } }>
            ] = [
                    {
                        request(builder) { return space_bangumi_request(space_id, initial_page, page_size, type, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }, {
                        request(builder) { return space_request(space_id, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }, {
                        request(builder) { return fan_count_request(space_id, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }
                ]
            const results = execute_requests(requests)
            const space = results[1]
            if (space.code !== 0) {
                throw new ScriptException("Failed to load space info")
            }
            space_info = {
                num_fans: results[2].data.follower,
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
            space_bangumi_response = results[0]
        } else {
            space_bangumi_response = JSON.parse(space_bangumi_request(space_id, initial_page, page_size, type).body)
        }

        log(space_bangumi_response)
        const has_more = space_bangumi_response.data.total > space_bangumi_response.data.ps * space_bangumi_response.data.pn
        super(
            format_space_bangumi(space_bangumi_response, space_id, space_info),
            has_more
        )
        this.next_page = initial_page + 1
        this.page_size = page_size
        this.space_id = space_id
        this.space_info = space_info
    }
    override nextPage(this: SpaceBangumiContentPager): SpaceBangumiContentPager {
        const space_bangumi_response: SpaceBangumiResponse = JSON.parse(space_bangumi_request(this.space_id, this.next_page, this.page_size, this.type).body)
        log(space_bangumi_response)
        this.results = format_space_bangumi(space_bangumi_response, this.space_id, this.space_info)

        this.hasMore = space_bangumi_response.data.total > space_bangumi_response.data.ps * space_bangumi_response.data.pn
        this.next_page += 1

        return this
    }
    override hasMorePagers(this: SpaceBangumiContentPager): boolean {
        return this.hasMore
    }
}
export function space_bangumi_request(space_id: number, page: number, page_size: number, type: 1 | 2, builder: BatchBuilder): BatchBuilder
export function space_bangumi_request(space_id: number, page: number, page_size: number, type: 1 | 2): BridgeHttpResponse<string>
export function space_bangumi_request(space_id: number, page: number, page_size: number, type: 1 | 2, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const course_prefix = "https://api.bilibili.com/x/space/bangumi/follow/list"
    const params: Params = {
        vmid: space_id.toString(),
        pn: page.toString(),
        ps: page_size.toString(),
        type: type.toString()
    }
    const url = create_url(course_prefix, params).toString()
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(url, {}, false)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function format_space_bangumi(space_courses_response: SpaceBangumiResponse, space_id: number, space_info: CoreSpaceInfo): PlatformPlaylist[] {
    const author_id = new PlatformID(PLATFORM, space_id.toString(), plugin.config.id)
    const author = new PlatformAuthorLink(
        author_id,
        space_info.name,
        `${SPACE_URL_PREFIX}${space_id}`,
        space_info.face,
        space_info.num_fans
    )

    return space_courses_response.data.list.map(function (season) {
        const releaseTime = new Date(season.publish.release_date).getTime()
        return new PlatformPlaylist({
            id: new PlatformID(PLATFORM, season.season_id.toString(), plugin.config.id),
            name: season.title,
            author,
            url: `${SEASON_URL_PREFIX}${season.season_id}`,
            videoCount: season.formal_ep_count,
            thumbnail: season.cover,
            thumbnails: new Thumbnails([new Thumbnail(season.cover, HARDCODED_THUMBNAIL_QUALITY)]),
            datetime: isNaN(releaseTime) ? HARDCODED_ZERO : Math.floor(releaseTime / 1000)
        })
    })
}
export class SpaceCoursesContentPager extends PlaylistPager {
    private next_page: number
    private readonly page_size: number
    private readonly space_info: CoreSpaceInfo
    private readonly space_id: number
    constructor(space_id: number, initial_page: number, page_size: number,) {
        let space_info = get_local_storage_cache().space_cache.get(space_id)
        let space_courses_response: SpaceCoursesResponse
        if (space_info === undefined) {
            const requests: [
                RequestMetadata<SpaceCoursesResponse>,
                RequestMetadata<SpaceResponse>,
                RequestMetadata<{ data: { follower: number } }>
            ] = [
                    {
                        request(builder) { return space_courses_request(space_id, initial_page, page_size, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }, {
                        request(builder) { return space_request(space_id, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }, {
                        request(builder) { return fan_count_request(space_id, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }
                ]
            const results = execute_requests(requests)
            const space = results[1]
            if (space.code !== 0) {
                throw new ScriptException("Failed to load space info")
            }
            space_info = {
                num_fans: results[2].data.follower,
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
            space_courses_response = results[0]
        } else {
            space_courses_response = JSON.parse(space_courses_request(space_id, initial_page, page_size).body)
        }

        const has_more = space_courses_response.data.page.next
        super(
            format_space_courses(space_courses_response, space_id, space_info),
            has_more
        )
        this.next_page = initial_page + 1
        this.page_size = page_size
        this.space_id = space_id
        this.space_info = space_info
    }
    override nextPage(this: SpaceCoursesContentPager): SpaceCoursesContentPager {
        const space_courses_response: SpaceCoursesResponse = JSON.parse(space_courses_request(this.space_id, this.next_page, this.page_size).body)

        this.results = format_space_courses(space_courses_response, this.space_id, this.space_info)

        this.hasMore = space_courses_response.data.page.next
        this.next_page += 1

        return this
    }
    override hasMorePagers(this: SpaceCoursesContentPager): boolean {
        return this.hasMore
    }
}
export function space_courses_request(space_id: number, page: number, page_size: number, builder: BatchBuilder): BatchBuilder
export function space_courses_request(space_id: number, page: number, page_size: number): BridgeHttpResponse<string>
export function space_courses_request(space_id: number, page: number, page_size: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const course_prefix = "https://api.bilibili.com/pugv/app/web/season/page"
    const params: Params = {
        mid: space_id.toString(),
        pn: page.toString(),
        ps: page_size.toString()
    }
    const url = create_url(course_prefix, params).toString()
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(url, {}, false)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function format_space_courses(space_courses_response: SpaceCoursesResponse, space_id: number, space_info: CoreSpaceInfo): PlatformPlaylist[] {
    const author_id = new PlatformID(PLATFORM, space_id.toString(), plugin.config.id)
    const author = new PlatformAuthorLink(
        author_id,
        space_info.name,
        `${SPACE_URL_PREFIX}${space_id}`,
        space_info.face,
        space_info.num_fans
    )

    return space_courses_response.data.items.map(function (course) {
        return new PlatformPlaylist({
            id: new PlatformID(PLATFORM, course.season_id.toString(), plugin.config.id),
            name: course.title,
            author,
            url: `${COURSE_URL_PREFIX}${course.season_id}`,
            videoCount: course.ep_count,
            thumbnail: course.cover
        })
    })
}
export class SpaceVideosContentPager extends VideoPager {
    private readonly page_size: number
    private next_page: number
    private readonly space_info: CoreSpaceInfo
    private readonly space_id: number
    constructor(space_id: number, initial_page: number, page_size: number, order: Order) {
        let space_info = get_local_storage_cache().space_cache.get(space_id)
        if (space_info === undefined) {
            const requests: [
                RequestMetadata<SpaceResponse>,
                RequestMetadata<{ data: { follower: number } }>
            ] = [{
                request(builder) { return space_request(space_id, builder) },
                process(response) { return JSON.parse(response.body) }
            }, {
                request(builder) { return fan_count_request(space_id, builder) },
                process(response) { return JSON.parse(response.body) }
            }]
            const [space, fan_count] = execute_requests(requests)
            if (space.code !== 0) {
                throw new ScriptException("Failed to load space info")
            }
            space_info = {
                num_fans: fan_count.data.follower,
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
        }
        const maybe_space_videos_response: MaybeSpaceVideosSearchResponse = JSON.parse(
            space_videos_request(space_id, initial_page, page_size, undefined, order).body)
        if (maybe_space_videos_response.code === -352) {
            throw new ScriptException("rate limited")
        }
        const space_videos_response: SpaceVideosSearchResponse = maybe_space_videos_response

        const has_more = space_videos_response.data.page.count > initial_page * page_size
        super(
            format_space_videos(space_videos_response, space_id, space_info),
            has_more
        )
        this.next_page = 2
        this.space_id = space_id
        this.page_size = page_size
        this.space_info = space_info
    }
    override nextPage(this: SpaceVideosContentPager): SpaceVideosContentPager {
        const maybe_space_videos_response: MaybeSpaceVideosSearchResponse = JSON.parse(
            space_videos_request(this.space_id, this.next_page, this.page_size, undefined, undefined).body)
        if (maybe_space_videos_response.code === -352) {
            throw new ScriptException("rate limited")
        }
        const space_search_response: SpaceVideosSearchResponse = maybe_space_videos_response

        this.results = format_space_videos(space_search_response, this.space_id, this.space_info)

        this.hasMore = space_search_response.data.page.count > this.next_page * this.page_size
        this.next_page += 1

        return this
    }
    override hasMorePagers(this: SpaceVideosContentPager): boolean {
        return this.hasMore
    }
}
// TODO if we can get cid caching working then use this api to load the cids for all videos and cache them
// https://api.bilibili.com/x/v3/fav/resource/infos
// it's used when viewing a favorites list
export function space_videos_request(space_id: number, page: number, page_size: number, keyword: string | undefined, order: Order | undefined, builder: BatchBuilder): BatchBuilder
export function space_videos_request(space_id: number, page: number, page_size: number, keyword: string | undefined, order: Order | undefined): BridgeHttpResponse<string>
export function space_videos_request(space_id: number, page: number, page_size: number, keyword: string | undefined, order: Order | undefined, builder?: BatchBuilder): BatchBuilder | BridgeHttpResponse<string> {
    const space_contents_search_prefix = "https://api.bilibili.com/x/space/wbi/arc/search"
    let params: Params = {
        mid: space_id.toString(),
        pn: page.toString(),
        ps: page_size.toString(),
        platform: "web",
        web_location: "333.1387"
    }
    if (order !== undefined) {
        params = {
            ...params,
            order: (function (order): OrderOptions {
                switch (order) {
                    case Type.Order.Chronological:
                        return "pubdate"
                    case Type.Order.Favorites:
                        return "stow"
                    case Type.Order.Views:
                        return "click"
                    case "CHRONOLOGICAL":
                        return "pubdate"
                    default:
                        throw new ScriptException(`unhandled ordering ${order}`)
                }
            })(order)
        }
    }
    if (keyword !== undefined) {
        params = { ...params, keyword }
    }
    const baseHeaders = {
        Cookie: session_cookie(),
        Host: "api.bilibili.com",
        Referer: "https://space.bilibili.com"
    }

    if (builder !== undefined) {
        const url = create_signed_url(space_contents_search_prefix, params).toString()
        return builder.GET(url, { "User-Agent": USER_AGENT, ...baseHeaders }, true)
    }

    const MAX_ATTEMPTS = 3
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        const url = create_signed_url(space_contents_search_prefix, params).toString()
        const now = Date.now()
        const result = local_http.GET(url, { "User-Agent": USER_AGENT, ...baseHeaders }, true)
        log_network_call(now)

        if (result.isOk) {
            return result
        }

        const status = result.code as number
        if ((status === 412 || status === 429 || status >= 500) && attempt < MAX_ATTEMPTS) {
            const delay = attempt * 1000
            trace(`space_videos_request attempt ${attempt} failed (HTTP ${status}), retrying in ${delay}ms`)
            bridge.sleep(delay)
            continue
        }

        if (IS_IMPERSONATION_AVAILABLE && httpimp !== undefined) {
            trace(`space_videos_request falling back to httpimp after HTTP ${status}`)
            const impUrl = create_signed_url(space_contents_search_prefix, params).toString()
            const impNow = Date.now()
            const impResult = httpimp.GET(impUrl, baseHeaders, true)
            log_network_call(impNow)
            if (impResult.isOk) {
                return impResult
            }
        }

        throw new ScriptException(`Failed to load channel videos (HTTP ${status})`)
    }

    throw new ScriptException("unreachable")
}
export function format_space_videos(space_videos_response: SpaceVideosSearchResponse, space_id: number, space_info: CoreSpaceInfo): PlatformVideo[] {
    const author_id = new PlatformID(PLATFORM, space_id.toString(), plugin.config.id)
    const author = new PlatformAuthorLink(
        author_id,
        space_info.name,
        `${SPACE_URL_PREFIX}${space_id}`,
        space_info.face,
        space_info.num_fans
    )

    return space_videos_response.data.list.vlist.map(function (space_video) {
        const url = `${VIDEO_URL_PREFIX}${space_video.bvid}`
        const video_id = new PlatformID(PLATFORM, space_video.bvid, plugin.config.id)

        const duration = parse_minutes_seconds(space_video.length)

        return new PlatformVideo({
            id: video_id,
            name: space_video.title,
            url: url,
            thumbnails: new Thumbnails([new Thumbnail(space_video.pic, HARDCODED_THUMBNAIL_QUALITY)]),
            author,
            duration,
            viewCount: space_video.play === "--" ? 0 : space_video.play,
            isLive: false,
            shareUrl: url,
            datetime: Number(space_video.created)
        })
    })
}
export class SpacePostsContentPager extends ContentPager {
    private posts_offset: number
    private readonly space_info: CoreSpaceInfo
    private readonly space_id: number
    constructor(space_id: number) {
        let space_info = get_local_storage_cache().space_cache.get(space_id)
        let space_posts_response: MaybeSpacePostsResponse
        if (space_info === undefined) {
            const requests: [
                RequestMetadata<MaybeSpacePostsResponse>,
                RequestMetadata<SpaceResponse>,
                RequestMetadata<{ data: { follower: number } }>
            ] = [
                    {
                        request(builder) { return space_posts_request(space_id, undefined, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }, {
                        request(builder) { return space_request(space_id, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }, {
                        request(builder) { return fan_count_request(space_id, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }
                ]
            const results = execute_requests(requests)
            const space = results[1]
            if (space.code !== 0) {
                throw new ScriptException("Failed to load space info")
            }
            space_info = {
                num_fans: results[2].data.follower,
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
            space_posts_response = results[0]
        } else {
            space_posts_response = JSON.parse(space_posts_request(space_id, undefined).body)
        }
        if (space_posts_response.code === -352) {
            throw new LoginRequiredException("rate limited: login or wait to view more posts")
        }

        const has_more = space_posts_response.data.has_more
        super(
            format_space_posts(space_posts_response, space_id, space_info),
            has_more
        )
        this.posts_offset = space_posts_response.data.offset
        this.space_id = space_id
        this.space_info = space_info
    }
    override nextPage(this: SpacePostsContentPager): SpacePostsContentPager {
        const space_posts_response: MaybeSpacePostsResponse = JSON.parse(space_posts_request(this.space_id, this.posts_offset).body)
        if (space_posts_response.code === -352) {
            throw new LoginRequiredException("rate limited: login or wait to view more posts")
        }

        this.results = format_space_posts(space_posts_response, this.space_id, this.space_info)

        this.hasMore = space_posts_response.data.has_more
        this.posts_offset = space_posts_response.data.offset

        return this
    }
    override hasMorePagers(this: SpacePostsContentPager): boolean {
        return this.hasMore
    }
}

export function space_posts_request(space_id: number, offset: number | undefined, builder: BatchBuilder): BatchBuilder
export function space_posts_request(space_id: number, offset: number | undefined): BridgeHttpResponse<string>
export function space_posts_request(space_id: number, offset: number | undefined, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const space_post_feed_prefix = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space"
    const params: Params = offset ? {
        host_mid: space_id.toString(),
        offset: offset.toString()
    } : {
        host_mid: space_id.toString()
    }
    const url = create_signed_url(space_post_feed_prefix, params).toString()

    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(
        url,
        {
            Host: "api.bilibili.com",
            Cookie: session_cookie(),
            Referer: "https://space.bilibili.com",
            "User-Agent": USER_AGENT
        },
        true)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function format_space_posts(space_posts_response: SpacePostsResponse, space_id: number, space_info: CoreSpaceInfo): PlatformPost[] {
    const author_id = new PlatformID(PLATFORM, space_id.toString(), plugin.config.id)
    const author = new PlatformAuthorLink(
        author_id,
        space_info.name,
        `${SPACE_URL_PREFIX}${space_id}`,
        space_info.face,
        space_info.num_fans
    )
    return space_posts_response.data.items.flatMap(function (space_post) {
        // ignore video posts (because it creates duplicate items in the combined feed)
        if (space_post.type === "DYNAMIC_TYPE_AV") {
            return []
        }

        const desc = space_post.modules.module_dynamic.desc
        const images: string[] = []
        const thumbnails: Thumbnails[] = []

        const primary_content = desc?.rich_text_nodes.map(
            function (node) { return format_text_node(node, images, thumbnails) }
        ).join("")

        const major = space_post.modules.module_dynamic.major
        const major_links = major !== null ? format_major(major, thumbnails, images) : undefined

        const topic = space_post.modules.module_dynamic.topic
        const topic_string = topic ? `<a href="${topic?.jump_url}">${topic.name}</a>\n` : undefined

        const reference = space_post.orig
        const reference_string = reference ? `<a href="${`${POST_URL_PREFIX}${reference.id_str}`}">${POST_URL_PREFIX}${reference.id_str}</a>` : undefined

        const content = (primary_content ? primary_content + "\n" : "") + (topic_string ?? "") + (major_links ?? "") + (reference_string ?? "")

        return [new PlatformPostDetails({
            thumbnails,
            images,
            description: content,
            // as far as i can tell posts don't have names
            name: MISSING_NAME,
            url: `${POST_URL_PREFIX}${space_post.id_str}`,
            id: new PlatformID(PLATFORM, space_post.id_str, plugin.config.id),
            rating: new RatingLikes(space_post.modules.module_stat.like.count),
            textType: Type.Text.HTML,
            author,
            content,
            datetime: Number(space_post.modules.module_author.pub_ts)
        })]
    })
}
// note there is another section on the page https://space.bilibili.com/<space_id>/favlist
// that has the users collected playlists. those are playlists created by others that the user has saved
// we won't load these into the feed because they aren't their playlists
export function space_favorites_request(space_id: number, builder: BatchBuilder): BatchBuilder
export function space_favorites_request(space_id: number): BridgeHttpResponse<string>
export function space_favorites_request(space_id: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const favorites_prefix = "https://api.bilibili.com/x/v3/fav/folder/created/list-all"
    const params: Params = {
        up_mid: space_id.toString()
    }
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    // use the authenticated client so logged in users can view their private favorites lists
    const result = runner.GET(
        create_url(favorites_prefix, params).toString(),
        { "User-Agent": USER_AGENT },
        true
    )
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function format_space_favorites(space_favorites_response: SpaceFavoritesResponse, space_id: number, space_info: CoreSpaceInfo): PlatformPlaylist[] {
    const author_id = new PlatformID(PLATFORM, space_id.toString(), plugin.config.id)
    const author = new PlatformAuthorLink(
        author_id,
        space_info.name,
        `${SPACE_URL_PREFIX}${space_id}`,
        space_info.face,
        space_info.num_fans
    )

    if (space_favorites_response.data !== null && space_favorites_response.data.list !== null) {
        return space_favorites_response.data.list.map(function (favorite_list) {
            return new PlatformPlaylist({
                id: new PlatformID(PLATFORM, favorite_list.id.toString(), plugin.config.id),
                name: favorite_list.title,
                author,
                url: `${FAVORITES_URL_PREFIX}${favorite_list.id}`,
                videoCount: favorite_list.media_count,
                // thumbnail: TODO MISSING_THUMBNAIL
            })
        })
    }
    return []
}
// TODO the order and filtering only applies to videos not posts but there is not a way of specifying that
export function getSearchChannelContentsCapabilities() {
    // TODO there are filter options but they only show up after a search has been returned
    return new ResultCapabilities<FilterGroupIDs, ChannelSearchTypeCapabilities>(
        [Type.Feed.Mixed],
        [Type.Order.Chronological, Type.Order.Views, Type.Order.Favorites],
        []
    )
}
export function searchChannelContents(space_url: string, query: string, type: ChannelSearchTypeCapabilities | null, order: Order | null, filters: FilterQuery<FilterGroupIDs> | null) {
    if (!type) {
        type = Type.Feed.Mixed
    }
    if (!order) {
        order = Type.Order.Chronological
    }
    if (filters && Object.keys(filters).length !== 0) {
        throw new ScriptException("unreachable")
    }
    const space_id = parse_space_url(space_url)

    const page_size = 30 as const
    const initial_page = 1 as const

    switch (type) {
        case Type.Feed.Mixed: {
            const posts_pager = new ChannelPostsResultsPager(query, space_id, initial_page, page_size)
            const videos_pager = new ChannelVideoResultsPager(query, space_id, initial_page, page_size, order)
            return new CompositeContentPager([videos_pager, posts_pager])
        }
        default:
            throw new ScriptException("unreachable")
    }
}
export class ChannelPostsResultsPager extends ContentPager {
    private next_page: number
    private readonly page_size: number
    private readonly space_id: number
    private readonly query: string
    constructor(query: string, space_id: number, initial_page: number, page_size: number) {
        const response = search_space_posts(query, space_id, initial_page, page_size)
        const more = response.data.total > initial_page * page_size
        super(format_post_search_result(response), more)
        this.next_page = initial_page + 1
        this.page_size = page_size
        this.space_id = space_id
        this.query = query
    }
    override nextPage(this: ChannelPostsResultsPager): ChannelPostsResultsPager {
        const response = search_space_posts(this.query, this.space_id, this.next_page, this.page_size)
        this.results = format_post_search_result(response)
        this.hasMore = response.data.total > this.next_page * this.page_size
        this.next_page += 1
        return this
    }
    override hasMorePagers(this: ChannelPostsResultsPager): boolean {
        return this.hasMore
    }
}
export function search_space_posts(query: string, space_id: number, page: number, page_size: number): SpacePostsSearchResponse {
    const space_contents_search_prefix = "https://api.bilibili.com/x/space/dynamic/search"
    const params: Params = {
        mid: space_id.toString(),
        keyword: query,
        pn: page.toString(),
        ps: page_size.toString(),
    }
    const url = create_url(space_contents_search_prefix, params).toString()

    const now = Date.now()
    const json = local_http.GET(
        url,
        {},
        false).body
    log_network_call(now)

    const search_response: SpacePostsSearchResponse = JSON.parse(json)
    return search_response
}
// TODO the post search results are really hard to parse. might be best to just load whole posts
// directly
export function format_post_search_result(response: SpacePostsSearchResponse): PlatformPost[] {
    const space_posts_response = response
    if (space_posts_response.data.cards === null) {
        return []
    }
    return space_posts_response.data.cards.map(function (card) {
        const post: Card = JSON.parse(card.card)
        const space_id = card.desc.user_profile.info.uid
        const author_id = new PlatformID(PLATFORM, space_id.toString(), plugin.config.id)
        const author = new PlatformAuthorLink(
            author_id,
            card.desc.user_profile.info.uname,
            `${SPACE_URL_PREFIX}${space_id}`,
            card.desc.user_profile.info.face,
            get_local_storage_cache().space_cache.get(space_id)?.num_fans
        )
        return new PlatformPost({
            thumbnails: [new Thumbnails([])],
            images: [],
            description: (post.dynamic ?? "") + (post.item?.content ?? "") + (post.item?.description ?? ""),
            // as far as i can tell posts don't have names
            name: MISSING_NAME,
            url: `${POST_URL_PREFIX}${card.desc.dynamic_id_str}`,
            id: new PlatformID(PLATFORM, card.desc.dynamic_id_str, plugin.config.id),
            author,
            datetime: Number(card.desc.timestamp)
        })
    })
}
export class ChannelVideoResultsPager extends ContentPager {
    private next_page: number
    private page_size: number
    private readonly space_id: number
    private readonly query: string
    private readonly order: Order
    private readonly space_info: CoreSpaceInfo
    constructor(query: string, space_id: number, initial_page: number, page_size: number, order: Order) {
        let space_info = get_local_storage_cache().space_cache.get(space_id)
        if (space_info === undefined) {
            const requests: [
                RequestMetadata<SpaceResponse>,
                RequestMetadata<{ data: { follower: number } }>
            ] = [{
                request(builder) { return space_request(space_id, builder) },
                process(response) { return JSON.parse(response.body) }
            }, {
                request(builder) { return fan_count_request(space_id, builder) },
                process(response) { return JSON.parse(response.body) }
            }]
            const [space, fan_count_response] = execute_requests(requests)
            if (space.code !== 0) {
                throw new ScriptException("Failed to load space info")
            }
            space_info = {
                num_fans: fan_count_response.data.follower,
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
        }
        const local_search_response: MaybeSpaceVideosSearchResponse = JSON.parse(
            space_videos_request(space_id, initial_page, page_size, query, order).body)
        if (local_search_response.code === -352) {
            throw new ScriptException("rate limited")
        }
        const search_response: SpaceVideosSearchResponse = local_search_response

        const more = search_response.data.page.count > initial_page * page_size
        super(format_space_videos(search_response, space_id, space_info), more)
        this.next_page = initial_page + 1
        this.page_size = page_size
        this.space_id = space_id
        this.query = query
        this.order = order
        this.space_info = space_info
    }
    override nextPage(this: ChannelVideoResultsPager): ChannelVideoResultsPager {
        const search_response: MaybeSpaceVideosSearchResponse = JSON.parse(
            space_videos_request(this.space_id, this.next_page, this.page_size, this.query, this.order).body)
        if (search_response.code === -352) {
            throw new ScriptException("rate limited")
        }
        this.results = format_space_videos(search_response, this.space_id, this.space_info)
        this.hasMore = search_response.data.page.count > this.next_page * this.page_size
        this.next_page += 1
        return this
    }
    override hasMorePagers(this: ChannelVideoResultsPager): boolean {
        return this.hasMore
    }
}

export function getChannelPlaylists(url: string): PlaylistPager {
    const space_id = parse_space_url(url)

    let space_info = get_local_storage_cache().space_cache.get(space_id)
    let space_favorites_response: SpaceFavoritesResponse
    if (space_info === undefined) {
        const requests: [
            RequestMetadata<SpaceFavoritesResponse>,
            RequestMetadata<SpaceResponse>,
            RequestMetadata<{ data: { follower: number } }>
        ] = [
                {
                    request(builder) { return space_favorites_request(space_id, builder) },
                    process(response) { return JSON.parse(response.body) }
                }, {
                    request(builder) { return space_request(space_id, builder) },
                    process(response) { return JSON.parse(response.body) }
                }, {
                    request(builder) { return fan_count_request(space_id, builder) },
                    process(response) { return JSON.parse(response.body) }
                }
            ]
        const results = execute_requests(requests)
        const space = results[1]
        if (space.code !== 0) {
            trace("Failed loading space info")
            return new PlaylistPager([], false)
        }
        space_info = {
            num_fans: results[2].data.follower,
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
        space_favorites_response = results[0]
    } else {
        space_favorites_response = JSON.parse(space_favorites_request(space_id).body)
    }
    const formatted_favorites: PlatformPlaylist[] = format_space_favorites(space_favorites_response, space_id, space_info)
    const favorites_pager = new PlaylistPager(formatted_favorites, false)

    const collections_pager = new SpaceCollectionsContentPager(space_id, 1, 20)
    const courses_pager = new SpaceCoursesContentPager(space_id, 1, 15)
    // const bangumi_pager_1 = new SpaceBangumiContentPager(space_id, 1, 24, 1)
    // const bangumi_pager_2 = new SpaceBangumiContentPager(space_id, 1, 24, 2)

    return new CompositePlaylistPager([
        // bangumi_pager_1, bangumi_pager_2,
        favorites_pager, collections_pager, courses_pager])
}
