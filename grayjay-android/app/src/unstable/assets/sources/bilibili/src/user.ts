import type {
    Params,
    LoggedInNavResponse,
    UserSubscriptionsResponse,
    WatchLaterResponse,
    SpaceFavoritesResponse,
    RequestMetadata
} from "./types.ts"
import { SPACE_URL_PREFIX, FAVORITES_URL_PREFIX, WATCH_LATER_URL, local_http } from "./constants.ts"
import { create_url, execute_requests, log_network_call, trace } from "./utilities.ts"
import { nav_request } from "./enable.ts"
import { watch_later_request } from "./playlists.ts"
import { space_favorites_request } from "./channel_contents.ts"

export function getUserSubscriptions() {
    if (!bridge.isLoggedIn()) {
        throw new ScriptException("unreachable")
    }
    const nav_response: LoggedInNavResponse = JSON.parse(nav_request(true).body)
    const subscriptions: string[] = []
    let total_pages: number | null = null
    let page = 1
    const page_size = 20
    while (total_pages === null || page <= total_pages) {
        try {
            const subscriptions_response: UserSubscriptionsResponse = JSON.parse(user_subscriptions_request(nav_response.data.mid, page, page_size).body)
            total_pages = Math.ceil(subscriptions_response.data.total / page_size)
            subscriptions.push(...subscriptions_response.data.list.map(function (subscription) { return `${SPACE_URL_PREFIX}${subscription.mid}` }))
        } catch {
            trace(`Failed to load subscriptions page ${page}`, { showToast: true })
            if (total_pages === null) {
                break
            }
        }
        page += 1
    }

    return subscriptions
}
function user_subscriptions_request(mid: number, page: number, page_size: number, builder: BatchBuilder): BatchBuilder
function user_subscriptions_request(mid: number, page: number, page_size: number): BridgeHttpResponse<string>
function user_subscriptions_request(mid: number, page: number, page_size: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const following_url = "https://api.bilibili.com/x/relation/followings"
    const params: Params = {
        vmid: mid.toString(),
        pn: page.toString(),
        ps: page_size.toString()
    }
    const runner = builder === undefined ? local_http : builder
    const url = create_url(following_url, params).toString()
    const now = Date.now()
    const result = runner.GET(url, {}, true)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function getUserPlaylists() {
    if (!bridge.isLoggedIn()) {
        throw new ScriptException("unreachable")
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
    const favorites_response: SpaceFavoritesResponse = JSON.parse(space_favorites_request(nav_response.data.mid).body)

    const playlists: string[] = favorites_response.data?.list?.map(function (list) {
        return `${FAVORITES_URL_PREFIX}${list.id}`
    }) ?? []
    if (watch_later_response.data.count > 0) {
        playlists.push(WATCH_LATER_URL)
    }
    return playlists
}
