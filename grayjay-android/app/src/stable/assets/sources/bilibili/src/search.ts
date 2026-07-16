import type {
    Params,
    SuggestionsResponse,
    SearchResponse,
    SearchResultItem,
    SearchResultQueryType,
    SearchTypeCapabilities,
    FilterGroupIDs,
    OrderOptions,
    RequestMetadata,
    LiveSearchResponse,
    CourseResponse,
} from "./types.ts"

import {
    PLATFORM,
    SPACE_URL_PREFIX,
    HARDCODED_THUMBNAIL_QUALITY,
    HARDCODED_ZERO,
    local_http,
    SEASON_URL_PREFIX,
    EPISODE_URL_PREFIX,
    VIDEO_URL_PREFIX,
    LIVE_ROOM_URL_PREFIX,
    EMPTY_AUTHOR,
    USER_AGENT,
} from "./constants.ts"

import { get_local_storage_cache } from "./state.ts"

import {
    create_signed_url,
    create_url,
    parse_minutes_seconds,
    assert_exhaustive,
    log_network_call,
    session_cookie,
    CompositeContentPager,
    parse_content_details_url,
    interleave_two,
    execute_requests,
} from "./utilities.ts"

import { course_request } from "./content.ts"
import { format_course } from "./playlists.ts"

export function searchSuggestions(query: string) {
    return get_suggestions(query)
}
export function get_suggestions(query: string): string[] {
    const suggestions_url = "https://s.search.bilibili.com/main/suggest"
    const params: Params = {
        func: "suggest",
        suggest_type: "accurate",
        sub_type: "tag",
        term: query
    }

    const url = create_url(suggestions_url, params).toString()
    const now = Date.now()
    const suggestions_json = local_http.GET(
        url,
        {},
        false).body
    log_network_call(now)
    const suggestions_response: SuggestionsResponse = JSON.parse(suggestions_json)
    return suggestions_response.result.tag.map(function (entry) { return entry.term })
}
export function getSearchCapabilities() {
    return new ResultCapabilities<FilterGroupIDs, SearchTypeCapabilities>(
        [Type.Feed.Mixed],
        [Type.Order.Chronological, Type.Order.Views, Type.Order.Favorites],
        // TODO implement category filtering
        [new FilterGroup(
            "期间", // Duration
            [
                new FilterCapability("全部时长", "0", "全部时长"), // full duration
                new FilterCapability("10分钟以下", "1", "10分钟以下"), // Under 10 minutes
                new FilterCapability("10-30分钟", "2", "10-30分钟"), // 10-30 minutes
                new FilterCapability("30-60分钟", "3", "30-60分钟"), // 30-60 minutes
                new FilterCapability("60分钟以上", "4", "60分钟以上"), // More than 60 minutes
            ],
            false,
            "DURATION_FILTER"
        )]
    )
}
export function search(query: string, type: SearchTypeCapabilities | null, order: Order | null, filters: FilterQuery<FilterGroupIDs> | null) {
    if (!type) {
        type = Type.Feed.Mixed
    }

    const query_order: OrderOptions | undefined = (function (order) {
        if (!order) return undefined
        switch (order) {
            case Type.Order.Chronological:
                return "pubdate"
            case Type.Order.Views:
                return "click"
            case Type.Order.Favorites:
                return "stow"
            default:
                throw new ScriptException(`unhandled feed order ${order}`)
        }
    })(order)

    const duration = (function (filters) {
        if (!filters) {
            return undefined
        }

        const filter = filters["DURATION_FILTER"]
        if (filter === undefined) {
            return undefined
        }
        const value = filter[0]
        if (value === undefined) {
            return undefined
        }
        switch (value) {
            case "0":
                return undefined
            case "1":
                return 1
            case "2":
                return 2
            case "3":
                return 3
            case "4":
                return 4
            default:
                throw new ScriptException(`unhandled feed filter ${filters}`)
        }
    })(filters)

    switch (type) {
        case Type.Feed.Mixed: {
            const live_pager = new SearchPager(query, 1, 42, "live", query_order, duration)
            const video_pager = new SearchPager(query, 1, 42, "video", query_order, duration)
            const movie_pager = new SearchPager(query, 1, 42, "media_ft", query_order, duration)
            const show_pager = new SearchPager(query, 1, 42, "media_bangumi", query_order, duration)

            return new CompositeContentPager([live_pager, video_pager, movie_pager, show_pager])
        }
        default:
            throw assert_exhaustive(type, "unreachable")
    }
}
export class SearchPager extends VideoPager {
    private next_page: number
    private readonly page_size: number
    private readonly query: string
    private readonly type: "live" | "video" | "media_bangumi" | "media_ft"
    private readonly order?: OrderOptions
    private readonly duration?: 1 | 2 | 3 | 4
    /**
     * Whole site search pager supporting many different content types
     * @param query
     * @param initial_page
     * @param page_size
     * @param type
     * @param order
     * @param duration
     */
    constructor(
        query: string,
        initial_page: number,
        page_size: number,
        type: "live" | "video" | "media_bangumi" | "media_ft",
        order?: OrderOptions,
        duration?: 1 | 2 | 3 | 4,
    ) {
        const raw_response = search_request(query, initial_page, page_size, type, order, duration)
        const { search_results, more } = extract_search_results(raw_response, type, initial_page, page_size)
        if (search_results === null) {
            super([], false)
        } else {
            super(format_search_results(search_results), more)
        }
        this.next_page = initial_page + 1
        this.page_size = page_size
        this.query = query
        if (order !== undefined) {
            this.order = order
        }
        if (duration !== undefined) {
            this.duration = duration
        }
        this.type = type
    }
    override nextPage(this: SearchPager): SearchPager {
        const raw_response = search_request(this.query, this.next_page, this.page_size, this.type, this.order, this.duration)
        const { search_results, more } = extract_search_results(raw_response, this.type, this.next_page, this.page_size)
        if (search_results === null) {
            this.results = []
            this.hasMore = false
        } else {
            this.results = format_search_results(search_results)
            this.hasMore = more
        }
        this.next_page += 1
        return this
    }
    override hasMorePagers(this: SearchPager): boolean {
        return this.hasMore
    }
}
export function search_request(
    query: string,
    page: number,
    page_size: number,
    type: SearchResultQueryType,
    order: undefined | OrderOptions,
    duration: undefined | 1 | 2 | 3 | 4,
    builder: BatchBuilder
): BatchBuilder
export function search_request(query: string,
    page: number,
    page_size: number,
    type: SearchResultQueryType,
    order: undefined | OrderOptions,
    duration: undefined | 1 | 2 | 3 | 4
): BridgeHttpResponse<string>
export function search_request(query: string,
    page: number,
    page_size: number,
    type: SearchResultQueryType,
    order: undefined | OrderOptions,
    duration: undefined | 1 | 2 | 3 | 4,
    builder?: BatchBuilder
): BatchBuilder | BridgeHttpResponse<string> {
    const search_prefix = "https://api.bilibili.com/x/web-interface/wbi/search/type"
    let params: Params = {
        search_type: type,
        page: page.toString(),
        page_size: page_size.toString(),
        keyword: query,
    }
    if (order !== undefined) {
        params = { ...params, order }
    }
    if (duration !== undefined) {
        params = { ...params, duration: duration.toString() }
    }
    const search_url = create_signed_url(search_prefix, params).toString()
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(
        search_url,
        { "User-Agent": USER_AGENT, Cookie: session_cookie() },
        false)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
/**
 *
 * @param raw_response
 * @param type
 * @param page
 * @param page_size
 * @returns SearchResultItems and whether there are more results
 */
export function extract_search_results(
    raw_response: BridgeHttpResponse<string>,
    type: SearchResultQueryType,
    page: number,
    page_size: number,
): { search_results: SearchResultItem[] | null, more: boolean } {
    if (type === "live") {
        const results: LiveSearchResponse = JSON.parse(raw_response.body)
        if ("v_voucher" in results.data) {
            throw new ScriptException("unable to load live video search results")
        }
        return {
            search_results: results.data.result === undefined ? null : results.data.result.live_room,
            more: results.data.pageinfo.live_room.total > page * page_size
        }
    }

    const results: SearchResponse = JSON.parse(raw_response.body)
    return {
        search_results: results.data.result === undefined ? null : results.data.result,
        more: results.data.numResults > page * page_size
    }
}
export function format_search_results(results: SearchResultItem[]): PlatformVideo[] {
    return results.map(function (item) {
        switch (item.type) {
            case "video": {
                const url = `${VIDEO_URL_PREFIX}${item.bvid}`
                const video_id = new PlatformID(PLATFORM, item.bvid, plugin.config.id)
                const author_id = new PlatformID(PLATFORM, item.mid.toString(), plugin.config.id)

                const duration = parse_minutes_seconds(item.duration)
                return new PlatformVideo({
                    id: video_id,
                    name: item.title,
                    url: url,
                    thumbnails: new Thumbnails([new Thumbnail(`https:${item.pic}`, HARDCODED_THUMBNAIL_QUALITY)]),
                    author: new PlatformAuthorLink(
                        author_id,
                        item.author,
                        `${SPACE_URL_PREFIX}${item.mid}`,
                        item.upic,
                        get_local_storage_cache().space_cache.get(item.mid)?.num_fans),
                    duration,
                    viewCount: item.play,
                    isLive: false,
                    shareUrl: url,
                    datetime: Number(item.pubdate)
                })
            }
            case "live_room": {
                const url = `${LIVE_ROOM_URL_PREFIX}${item.roomid}`
                const video_id = new PlatformID(PLATFORM, item.roomid.toString(), plugin.config.id)
                const author_id = new PlatformID(PLATFORM, item.uid.toString(), plugin.config.id)
                return new PlatformVideo({
                    id: video_id,
                    name: item.title,
                    url: url,
                    thumbnails: new Thumbnails([new Thumbnail(`https:${item.user_cover}`, HARDCODED_THUMBNAIL_QUALITY)]),
                    author: new PlatformAuthorLink(
                        author_id,
                        item.uname,
                        `${SPACE_URL_PREFIX}${item.uid}`,
                        `https:${item.uface}`,
                        get_local_storage_cache().space_cache.get(item.uid)?.num_fans),
                    viewCount: item.watched_show.num,
                    isLive: true,
                    shareUrl: url,
                    // TODO assumes China timezone
                    datetime: (() => {
                        const liveTime = new Date(`${item.live_time} UTC+8`).getTime()
                        return isNaN(liveTime) ? HARDCODED_ZERO : Math.floor(liveTime / 1000)
                    })()
                })
            }
            // TODO once the main search results support playlists courses and shows should return playlists
            case "ketang": {
                const season_id = item.id
                const course_response: CourseResponse = JSON.parse(course_request({ type: "season", id: season_id }).body)
                const season = format_course(season_id, course_response)
                const episode = season.contents.results[0]
                if (episode === undefined) {
                    throw new ScriptException("missing episodes")
                }
                return episode
            }
            case "media_bangumi": {
                const first_episode = item.eps[0]
                if (first_episode === undefined) {
                    throw new ScriptException("unreachable")
                }
                const url = `${EPISODE_URL_PREFIX}${first_episode.id}`
                const video_id = new PlatformID(PLATFORM, first_episode.id.toString(), plugin.config.id)
                return new PlatformVideo({
                    id: video_id,
                    name: item.title,
                    url: url,
                    // TODO figure out if we should include both thumbnails
                    thumbnails: new Thumbnails([
                        new Thumbnail(first_episode.cover, HARDCODED_THUMBNAIL_QUALITY),
                        new Thumbnail(item.cover, HARDCODED_THUMBNAIL_QUALITY)
                    ]),
                    author: EMPTY_AUTHOR,
                    viewCount: HARDCODED_ZERO,
                    isLive: false,
                    shareUrl: url,
                    // TODO assumes China timezone
                    datetime: Number(item.pubtime)
                })
            }
            case "media_ft": {
                let first_episode
                if (item.eps === null) {
                    if (item.ep_size !== 0) {
                        throw new ScriptException("unreachable")
                    }
                    const url = item.url
                    const { content_id } = parse_content_details_url(url)

                    first_episode = {
                        cover: undefined,
                        id: parseInt(content_id)
                    }
                } else {
                    first_episode = item.eps[0]
                }
                if (first_episode === undefined) {
                    throw new ScriptException("unreachable")
                }
                const url = `${EPISODE_URL_PREFIX}${first_episode.id}`
                const video_id = new PlatformID(PLATFORM, first_episode.id.toString(), plugin.config.id)
                const thumbnails = [new Thumbnail(item.cover, HARDCODED_THUMBNAIL_QUALITY)]
                if (first_episode.cover !== undefined) {
                    thumbnails.push(new Thumbnail(first_episode.cover, HARDCODED_THUMBNAIL_QUALITY))
                }
                return new PlatformVideo({
                    id: video_id,
                    name: item.title,
                    url: url,
                    // TODO figure out if we should include both thumbnails
                    thumbnails: new Thumbnails(thumbnails),
                    author: EMPTY_AUTHOR,
                    viewCount: HARDCODED_ZERO,
                    isLive: false,
                    shareUrl: url,
                    // TODO assumes China timezone
                    datetime: Number(item.pubtime)
                })
            }
            case "bili_user":
                throw new ScriptException("unreachable")
            default:
                throw assert_exhaustive(item, "unreachable")
        }
    })
}

export function searchPlaylists(query: string) {
    return new BangumiPager(query, 1, 12)
}
export class BangumiPager extends PlaylistPager {
    private readonly query: string
    private next_page: number
    private readonly page_size: number
    constructor(query: string, initial_page: number, page_size: number) {
        const requests: [
            RequestMetadata<{ search_results: SearchResultItem[] | null, more: boolean }>,
            RequestMetadata<{ search_results: SearchResultItem[] | null, more: boolean }>
        ] = [{
            request(builder) { return search_request(query, initial_page, page_size, "media_bangumi", undefined, undefined, builder) },
            process(response) { return extract_search_results(response, "media_bangumi", initial_page, page_size) }
        },
        {
            request(builder) { return search_request(query, initial_page, page_size, "media_ft", undefined, undefined, builder) },
            process(response) { return extract_search_results(response, "media_ft", initial_page, page_size) }
        },]
        const results = execute_requests(requests)
        const shows = results[0].search_results
        const movies = results[1].search_results
        if (movies === null && shows === null) {
            super([], false)
        } else {
            super(format_bangumi_search(shows, movies), results[0].more || results[1].more)
        }
        this.next_page = initial_page + 1
        this.page_size = page_size
        this.query = query
    }
    override nextPage(this: BangumiPager): BangumiPager {
        const requests: [
            RequestMetadata<{ search_results: SearchResultItem[] | null, more: boolean }>,
            RequestMetadata<{ search_results: SearchResultItem[] | null, more: boolean }>
        ] = [{
            request: (builder) => { return search_request(this.query, this.next_page, this.page_size, "media_bangumi", undefined, undefined, builder) },
            process: (response) => { return extract_search_results(response, "media_bangumi", this.next_page, this.page_size) }
        },
        {
            request: (builder) => { return search_request(this.query, this.next_page, this.page_size, "media_ft", undefined, undefined, builder) },
            process: (response) => { return extract_search_results(response, "media_ft", this.next_page, this.page_size) }
        },]
        const results = execute_requests(requests)
        const shows = results[0].search_results
        const movies = results[1].search_results
        this.hasMore = results[0].more || results[1].more
        this.results = format_bangumi_search(shows, movies)
        this.next_page += 1
        return this
    }
    override hasMorePagers(this: BangumiPager): boolean {
        return this.hasMore
    }
}
export function format_bangumi_search(shows: SearchResultItem[] | null, movies: SearchResultItem[] | null): PlatformPlaylist[] {
    return interleave_two(shows ?? [], movies ?? []).map(function (item) {
        if (item.type === "ketang" || item.type === "video" || item.type === "live_room" || item.type === "bili_user") {
            throw new ScriptException("unreachable")
        }

        return new PlatformPlaylist({
            id: new PlatformID(PLATFORM, item.season_id.toString(), plugin.config.id),
            name: item.title,
            author: EMPTY_AUTHOR,
            url: `${SEASON_URL_PREFIX}${item.season_id}`,
            videoCount: item.ep_size === 0 ? 1 : item.ep_size,
            thumbnail: item.cover,
            datetime: Number(item.pubtime),
        })
    })
}
