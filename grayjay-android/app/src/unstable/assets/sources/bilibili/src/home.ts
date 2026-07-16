import type { HomeFeedResponse, Params } from "./types.ts"
import { PLATFORM, SPACE_URL_PREFIX, LIVE_ROOM_URL_PREFIX, HARDCODED_THUMBNAIL_QUALITY, HARDCODED_ZERO, local_http } from "./constants.ts"
import { get_local_state, get_local_storage_cache } from "./state.ts"
import { create_url, log_network_call, trace, assert_exhaustive } from "./utilities.ts"

export function getHome() {
    return new HomePager(0, 12)
}
export class HomePager extends VideoPager {
    private next_page: number
    private readonly page_size: number
    constructor(initial_page: number, page_size: number) {
        super(format_home(get_home(initial_page, page_size)), true)
        this.next_page = initial_page + 1
        this.page_size = page_size
    }
    override nextPage(this: HomePager): HomePager {
        this.results = format_home(get_home(this.next_page, this.page_size))
        this.hasMore = true
        this.next_page += 1
        return this
    }
    override hasMorePagers(this: HomePager): boolean {
        return true
    }
}
/**
 *
 * @param page The page to load (starts at 0)
 * @param page_size
 * @returns
 */
export function get_home(page: number, page_size: number): HomeFeedResponse {
    const home_api_url = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd"
    const fresh_type = "4"
    const feed_version = "V_WATCHLATER_PIP_WINDOW3"
    const params: Params = {
        fresh_idx: page.toString(),
        ps: page_size.toString(),
        fresh_type,
        feed_version,
        fresh_idx_1h: page.toString(),
        brush: page.toString(),
    }

    const url = create_url(home_api_url, params).toString()
    const now = Date.now()
    // use auth client so that logged in users get a personalized home feed
    const home_json = local_http.GET(
        url,
        { Referer: "https://www.bilibili.com", Cookie: `buvid3=${get_local_state().buvid3}` },
        true).body

    log_network_call(now)
    const home_response: HomeFeedResponse = JSON.parse(home_json)
    return home_response
}
export function format_home(home: HomeFeedResponse): PlatformVideo[] {
    if (home === null) {
        trace("home is null please investigate")
        return []
    }
    return home.data.item.flatMap(function (item): PlatformVideo[] {
        switch (item.goto) {
            case "ad":
                return []
            case "av": {
                // update cid cache
                get_local_storage_cache().cid_cache.set(item.bvid, item.cid)

                const fan_count = get_local_storage_cache().space_cache.get(item.owner.mid)?.num_fans
                const video_id = new PlatformID(PLATFORM, item.bvid, plugin.config.id)
                const author_id = new PlatformID(PLATFORM, item.owner.mid.toString(), plugin.config.id)
                return [new PlatformVideo({
                    id: video_id,
                    name: item.title,
                    url: item.uri,
                    thumbnails: new Thumbnails([new Thumbnail(item.pic, HARDCODED_THUMBNAIL_QUALITY)]),
                    author: new PlatformAuthorLink(
                        author_id,
                        item.owner.name,
                        `${SPACE_URL_PREFIX}${item.owner.mid}`,
                        item.owner.face, fan_count),
                    duration: item.duration,
                    viewCount: item.stat.view,
                    isLive: false,
                    shareUrl: item.uri,
                    datetime: Number(item.pubdate)
                })]
            }
            case "live": {
                const fan_count = get_local_storage_cache().space_cache.get(item.owner.mid)?.num_fans
                const room_id = new PlatformID(PLATFORM, item.id.toString(), plugin.config.id)
                const author_id = new PlatformID(PLATFORM, item.owner.mid.toString(), plugin.config.id)
                return [new PlatformVideo({
                    id: room_id,
                    name: item.title,
                    url: `${LIVE_ROOM_URL_PREFIX}${item.id}`,
                    thumbnails: new Thumbnails([new Thumbnail(item.pic, HARDCODED_THUMBNAIL_QUALITY)]),
                    author: new PlatformAuthorLink(
                        author_id,
                        item.owner.name,
                        `${SPACE_URL_PREFIX}${item.owner.mid}`,
                        item.owner.face, fan_count),
                    viewCount: item.room_info.watched_show.num,
                    isLive: true,
                    shareUrl: `${LIVE_ROOM_URL_PREFIX}${item.id}`,
                    // TODO load from cache
                    datetime: HARDCODED_ZERO
                })]
            }
            default:
                throw assert_exhaustive(item, `unhandled type on home page item ${item}`)
        }
    })
}
