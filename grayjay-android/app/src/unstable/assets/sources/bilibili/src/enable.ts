import type { State, Wbi, FingerSpiResponse, RequestMetadata, Settings, PluginSettings, NavResponse } from "./types.ts"
import { USER_AGENT, WEBGL_VENDOR, WEBGL_RENDERER, WEBGL, OS, local_http, local_utility, post_body_for_ExClimbWuzhi, IS_IMPERSONATION_AVAILABLE, IMPERSONATION_TARGET } from "./constants.ts"
import { get_local_state, set_local_state, set_local_settings, set_local_storage_cache } from "./state.ts"
import { string_to_bytes, get_random_int_inclusive, execute_requests, trace, log_network_call } from "./utilities.ts"

declare const IS_TESTING: boolean
declare const httpimp: HTTP | undefined

function normalize_settings(settings: Settings): PluginSettings {
    return {
        verboseNotifications: settings.verboseNotifications === true || settings.verboseNotifications === "true",
        multipartNextOnlyRecommendations: settings.multipartNextOnlyRecommendations === true || settings.multipartNextOnlyRecommendations === "true"
    }
}

export function enable(conf: SourceConfig, settings: Settings, savedState?: string | null) {
    set_local_settings(normalize_settings(settings))
    if (IS_TESTING) {
        log("IS_TESTING true")
        log("logging configuration")
        log(conf)
        log("logging settings")
        log(settings)
        log("logging savedState")
        log(savedState)
    }

    if (IS_IMPERSONATION_AVAILABLE && httpimp !== undefined) {
        const impClient = httpimp.getDefaultClient(true) as HTTPClient & { setDefaultImpersonateTarget?: (target: string) => void }
        if (impClient.setDefaultImpersonateTarget) {
            impClient.setDefaultImpersonateTarget(IMPERSONATION_TARGET)
        }
    }

    if (!savedState) {
        init_local_storage()
    } else {
        const state: State = JSON.parse(savedState)
        init_local_storage(state)
    }
}
export function init_session_info(): State {
    const vendor_and_renderer = WEBGL_VENDOR + WEBGL_RENDERER + "g"

    let dm_cover_img_str = local_utility.toBase64(string_to_bytes(vendor_and_renderer))
    {
        // add missing padding
        const missing_padding = (4 - dm_cover_img_str.length % 4) % 4
        dm_cover_img_str += "=".repeat(missing_padding)
    }
    // chop the end off
    dm_cover_img_str = dm_cover_img_str.slice(0, dm_cover_img_str.length - 2)

    let dm_img_str = local_utility.toBase64(string_to_bytes(WEBGL))
    {
        // add missing padding
        const missing_padding = (4 - dm_img_str.length % 4) % 4
        dm_img_str += "=".repeat(missing_padding)
    }
    // chop the end off
    dm_img_str = dm_img_str.slice(0, dm_img_str.length - 2)

    const value_one = get_random_int_inclusive(100, 1000)
    const winWidth = get_random_int_inclusive(50, 5000)
    const winHeight = get_random_int_inclusive(50, 5000)
    const value_two = get_random_int_inclusive(5, 500)
    const wh = [2 * winWidth + 2 * winHeight + 3 * value_two, 4 * winWidth - winHeight + value_two, value_two]

    const dm_img_inter = `{"ds":[],"wh":[${wh[0]},${wh[1]},${wh[2]}],"of":[${value_one},${value_one * 2},${value_one}]}`

    const b_nut = create_b_nut()
    const requests: [
        RequestMetadata<Wbi>,
        RequestMetadata<FingerSpiResponse>,
        RequestMetadata<readonly number[]>] = [{
            request(builder) { return nav_request(false, builder) },
            process: process_wbi_keys
        }, {
            request: cookie_request,
            process(response) { return JSON.parse(response.body) }
        }, {
            request: mixin_constant_request,
            process: process_mixin_constant
        }]
    const [{ wbi_img_key, wbi_sub_key }, finger_spi_response, mixin_constant] = execute_requests(requests)
    const buvid3 = finger_spi_response.data.b_3
    const buvid4 = finger_spi_response.data.b_4
    const buvid_fp = local_utility.md5String(`${buvid3}${USER_AGENT}${WEBGL_VENDOR}${WEBGL_RENDERER}${OS}`)

    // required to access space posts
    activate_cookies(b_nut, buvid3, buvid4, buvid_fp)

    return {
        buvid3,
        buvid4,
        buvid_fp,
        b_nut,
        mixin_key: getMixinKey(wbi_img_key + wbi_sub_key, mixin_constant),
        dm_cover_img_str,
        dm_img_str,
        dm_img_inter
    }
}
export function init_local_storage(state?: State) {
    // these caches don't work that well because they aren't shared between plugin instances
    // saveState is what we need
    set_local_storage_cache({
        cid_cache: new Map(),
        space_cache: new Map()
    })
    set_local_state(state === undefined ? init_session_info() : state)
}
export function nav_request(useAuthClient: boolean, builder: BatchBuilder): BatchBuilder
export function nav_request(useAuthClient: boolean): BridgeHttpResponse<string>
export function nav_request(useAuthClient: boolean, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const url = "https://api.bilibili.com/x/web-interface/nav"
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(url, {}, useAuthClient)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function mixin_constant_request(builder: BatchBuilder): BatchBuilder
export function mixin_constant_request(): BridgeHttpResponse<string>
export function mixin_constant_request(builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const url = "https://s1.hdslb.com/bfs/seed/laputa-header/bili-header.umd.js"

    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(url, {}, false)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function process_mixin_constant(html: BridgeHttpResponse<string>): readonly number[] {
    // Match pattern: array of numbers followed by forEach with charAt pattern
    // Form A: var=[numbers],var=[];[return ]var.forEach(function(n){e.charAt(n)
    // Form B: var=[],[numbers].forEach((function(e){n.charAt(e)  (inline array, reversed order)
    const mixin_constant_regex = /(?:\w+=(\[\d+(?:,\d+){60,}\]),\w+=\[\];(?:return )?\w+\.forEach|\w+=\[\],(\[\d+(?:,\d+){60,}\])\.forEach)\(\(?function\(\w+\)\{\w+\.charAt\(\w+\)/
    const mixin_constant_match = html.body.match(mixin_constant_regex)
    const mixin_constant_json = mixin_constant_match?.[1] ?? mixin_constant_match?.[2]
    if (mixin_constant_json === undefined) {
        throw new ScriptException("failed to acquire mixin_constant")
    }
    const mixin_constant: readonly number[] = JSON.parse(mixin_constant_json)
    return mixin_constant
}
export function process_wbi_keys(raw_response: BridgeHttpResponse<string>): Wbi {
    const response: NavResponse = JSON.parse(raw_response.body)

    return {
        wbi_img_key: response.data.wbi_img.img_url.slice(29, 61),
        wbi_sub_key: response.data.wbi_img.sub_url.slice(29, 61)
    }
}
// TODO buvid4 is working along with b_nut. we should switch everything from buvid3 to buvid4 plus b_nut
// this will make things simpler
export function cookie_request(builder: BatchBuilder): BatchBuilder
export function cookie_request(): BridgeHttpResponse<string>
export function cookie_request(builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const finger_spi_url = "https://api.bilibili.com/x/frontend/finger/spi"
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(finger_spi_url, {}, false)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
/**
 * Activates cookies to be usable to load channel posts
 * @param b_nut
 * @param buvid3
 * @param buvid4
 */
export function activate_cookies(b_nut: number, buvid3: string, buvid4: string, buvid_fp: string) {
    const cookie_activation_url = "https://api.bilibili.com/x/internal/gaia-gateway/ExClimbWuzhi"
    const body = post_body_for_ExClimbWuzhi

    const now = Date.now()
    local_http.POST(
        cookie_activation_url,
        body,
        {
            Cookie: `buvid3=${buvid3}; buvid4=${buvid4}; buvid_fp=${buvid_fp}; b_nut=${b_nut}`,
            "User-Agent": USER_AGENT,
            Host: "api.bilibili.com",
            "Content-Length": body.length.toString(),
            "Content-Type": "application/json"
        },
        false
    )

    trace(`buvid3=${buvid3}; buvid4=${buvid4}; b_nut=${b_nut}`)

    log_network_call(now)
}
/**
 * https://s1.hdslb.com/bfs/seed/laputa-header/bili-header.umd.js
 * @param e
 * @param encryption_info
 * @returns
 */
export function getMixinKey(e: string, encryption_info: readonly number[]) {
    return encryption_info.filter(function (value) {
        return e[value] !== undefined
    }).map(function (value) {
        return e[value]
    }).join("").slice(0, 32)
}
export function create_b_nut() {
    return Math.floor((new Date).getTime() / 1e3)
}

export function disable() {
    trace("disabling")
}

export function saveState() {
    return JSON.stringify(get_local_state())
}
