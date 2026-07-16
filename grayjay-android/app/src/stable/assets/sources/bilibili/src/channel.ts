import type { Params, SpaceResponse, SearchResultItem, RequestMetadata } from "./types.ts"
import { PLATFORM, PLAYLIST_URL_REGEX, SPACE_URL_PREFIX, SPACE_URL_REGEX, NAME_LOAD_FAILED, USER_AGENT, local_http } from "./constants.ts"
import { get_local_storage_cache } from "./state.ts"
import { create_signed_url, create_url, execute_requests, log_network_call, session_cookie, trace } from "./utilities.ts"
import { search_request, extract_search_results } from "./search.ts"

export function searchChannels(query: string) {
    return new SpacePager(query, 1, 36)
}
export class SpacePager extends ChannelPager {
    private readonly query: string
    private next_page: number
    private readonly page_size: number
    constructor(query: string, initial_page: number, page_size: number) {
        const raw_response = search_request(query, initial_page, page_size, "bili_user", undefined, undefined)
        const { search_results, more } = extract_search_results(raw_response, "bili_user", initial_page, page_size)
        if (search_results === null) {
            super([], false)
        } else {
            super(format_space_results(search_results), more)
        }
        this.next_page = initial_page + 1
        this.page_size = page_size
        this.query = query
    }
    override nextPage(this: SpacePager): SpacePager {
        const raw_response = search_request(this.query, this.next_page, this.page_size, "bili_user", undefined, undefined)
        const { search_results, more } = extract_search_results(raw_response, "bili_user", this.next_page, this.page_size)
        if (search_results === null) {
            throw new ScriptException("unreachable")
        }
        this.hasMore = more
        this.results = format_space_results(search_results)
        this.next_page += 1
        return this
    }
    override hasMorePagers(this: SpacePager): boolean {
        return this.hasMore
    }
}
export function format_space_results(space_search_results: SearchResultItem[]): PlatformChannel[] {
    return space_search_results.map(function (result) {
        if (result.type !== "bili_user") {
            throw new ScriptException("unreachable")
        }
        return new PlatformChannel({
            id: new PlatformID(PLATFORM, result.mid.toString(), plugin.config.id),
            name: result.uname,
            thumbnail: `https:${result.upic}`,
            subscribers: result.fans,
            description: result.usign,
            url: `${SPACE_URL_PREFIX}${result.mid}`,
            links: {
                'BiliBili': `${SPACE_URL_PREFIX}${result.mid}`
            }
        })
    })
}
// example of handled urls
// https://space.bilibili.com/491461718
export function isChannelUrl(url: string) {
    // Some playlist urls are also Space urls
    // for example
    // https://space.bilibili.com/491461718/favlist?fid=3153093518
    if (PLAYLIST_URL_REGEX.test(url)) {
        return false
    }
    return SPACE_URL_REGEX.test(url)
}
export function getChannel(url: string) {
    const space_id = parse_space_url(url)

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
        return new PlatformChannel({
            id: new PlatformID(PLATFORM, space_id.toString(), plugin.config.id),
            name: NAME_LOAD_FAILED,
            thumbnail: "",
            url: `${SPACE_URL_PREFIX}${space_id}`,
            links: {
                'BiliBili': `${SPACE_URL_PREFIX}${space_id}`
            }
        })
    }

    // cache results
    get_local_storage_cache().space_cache.set(space_id, {
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
    })

    const is_default_banner = new RegExp(/cb1c3ef50e22b6096fde67febe863494caefebad/).test(space.data.top_photo)

    const channel = new PlatformChannel({
        id: new PlatformID(PLATFORM, space_id.toString(), plugin.config.id),
        name: space.data.name,
        thumbnail: space.data.face,
        subscribers: fan_count_response.data.follower,
        description: space.data.sign,
        url: `${SPACE_URL_PREFIX}${space_id}`,
        links: {
            'BiliBili': `${SPACE_URL_PREFIX}${space_id}`
        }
    })

    return is_default_banner ? channel : {
        ...channel, banner: space.data.top_photo
    }
}
export function parse_space_url(url: string) {
    const match_results = url.match(SPACE_URL_REGEX)
    if (match_results === null) {
        throw new ScriptException(`malformed space url: ${url}`)
    }
    // Group 1: space.bilibili.com/ID, Group 3: www.bilibili.com/space/ID, Group 5: m.bilibili.com/space/ID, Group 7: bilibili.tv/lang/space/ID
    const maybe_space_id = match_results[1] !== undefined ? match_results[1] : (match_results[3] !== undefined ? match_results[3] : (match_results[5] !== undefined ? match_results[5] : match_results[7]))
    if (maybe_space_id === undefined) {
        throw new ScriptException("unreachable regex error")
    }
    const space_id = parseInt(maybe_space_id)
    return space_id
}
export function fan_count_request(space_id: number, builder: BatchBuilder): BatchBuilder
export function fan_count_request(space_id: number): BridgeHttpResponse<string>
export function fan_count_request(space_id: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
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
export function space_request(space_id: number, builder: BatchBuilder): BatchBuilder
export function space_request(space_id: number): BridgeHttpResponse<string>
export function space_request(space_id: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
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
