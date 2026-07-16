import type { SubtitlesDataResponse, Params, RequestMetadata, ContentType } from "./types.ts"
import { CONTENT_DETAIL_URL_REGEX, local_http, local_utility } from "./constants.ts"
import { get_local_state, get_local_settings } from "./state.ts"


// TODO this structure isn't ideal because each pager will make http requests
// it would be ideal if all of those http requests were combined into a single batch request
// we could still reuse the code somehow but it will be trickier
export class CompositeContentPager extends ContentPager {
    constructor(private pagers: ContentPager[]) {
        const results = interleave(pagers.map((pager) => pager.results))
        const no_more_results = pagers.every((pager) => !pager.hasMore)
        super(results, !no_more_results)
    }
    override nextPage(this: CompositeContentPager): CompositeContentPager {
        this.pagers = this.pagers.flatMap((pager) => {
            if (pager.hasMore) {
                pager.nextPage()
                return pager
            }
            return []
        })
        const results = interleave(this.pagers.map((pager) => pager.results))
        const no_more_results = this.pagers.every((pager) => !pager.hasMore)
        this.results = results
        this.hasMore = !no_more_results
        return this
    }
    override hasMorePagers(this: CompositeContentPager): boolean {
        return this.hasMore
    }
}
export class CompositePlaylistPager extends PlaylistPager {
    constructor(private pagers: PlaylistPager[]) {
        const results = interleave(pagers.map((pager) => pager.results))
        const no_more_results = pagers.every((pager) => !pager.hasMore)
        super(results, !no_more_results)
    }
    override nextPage(this: CompositePlaylistPager): CompositePlaylistPager {
        this.pagers = this.pagers.flatMap((pager) => {
            if (pager.hasMore) {
                pager.nextPage()
                return pager
            }
            return []
        })
        const results = interleave(this.pagers.map((pager) => pager.results))
        const no_more_results = this.pagers.every((pager) => !pager.hasMore)
        this.results = results
        this.hasMore = !no_more_results
        return this
    }
    override hasMorePagers(this: CompositePlaylistPager): boolean {
        return this.hasMore
    }
}

export function assert_exhaustive(value: never): void
export function assert_exhaustive(value: never, exception_message: string): ScriptException
export function assert_exhaustive(value: never, exception_message?: string): ScriptException | undefined {
    log(["BiliBili log:", value])
    if (exception_message !== undefined) {
        return new ScriptException(exception_message)
    }
    return
}

export function string_to_bytes(str: string): Uint8Array {
    const result = []
    for (let i = 0; i < str.length; i++) {
        result.push(str.charCodeAt(i))
    }
    return new Uint8Array(result)
}

export function get_random_int_inclusive(min: number, max: number) {
    const minCeiled = Math.ceil(min)
    const maxFloored = Math.floor(max)
    return Math.floor(Math.random() * (maxFloored - minCeiled + 1) + minCeiled) // The maximum is inclusive and the minimum is inclusive
}

/**
 * Parses a time in minutes and seconds into a unix epoch timestamp
 * @param minutes_seconds "20:45"
 * @returns
 */
export function parse_minutes_seconds(minutes_seconds: string): number {
    const parsed_length = minutes_seconds.match(/^(\d+):(\d+)/)
    if (parsed_length === null) {
        throw new ScriptException("unreachable regex error")
    }
    const minutes = parsed_length[1]
    const seconds = parsed_length[2]
    if (minutes === undefined || seconds === undefined) {
        throw new ScriptException("unreachable regex error")
    }
    const duration = parseInt(minutes) * 60 + parseInt(seconds)
    return duration
}

/**
 * Converts subtitle data to the WebVTT format
 * @param subtitles_data
 * @param name
 * @returns
 */
export function convert_subtitles(subtitles_data: SubtitlesDataResponse, name: string) {
    let text = `WEBVTT ${name}\n`
    text += "\n"
    for (const item of subtitles_data.body) {
        text += `${item.sid}\n`
        text += `${seconds_to_WebVTT_timestamp(item.from)} --> ${seconds_to_WebVTT_timestamp(item.to)}\n`
        text += `${item.content}\n`
        text += "\n"
    }
    return text
}

/**
 * Converts seconds to the timestamp format used in WebVTT
 * @param seconds
 * @returns
 */
export function seconds_to_WebVTT_timestamp(seconds: number) {
    return new Date(seconds * 1000).toISOString().substring(11, 23)
}

export function interleave<T>(arrays: T[][]): T[] {
    const maxLength = Math.max(...arrays.map(arr => arr.length))
    const result: T[] = []
    for (let i = 0; i < maxLength; i++) {
        arrays.forEach((array) => {
            if (i < array.length) {
                const val = array[i]
                if (val === undefined) {
                    throw new ScriptException("unreachable")
                }
                result.push(val)
            }
        })
    }
    return result
}

/**
 * Interleaves two arrays starting with values from the longer array or from a if a and b are the same length
 * @param a
 * @param b
 * @returns
 */
export function interleave_two<T, U>(a: T[], b: U[]): Array<T | U> {
    const [first, second] = b.length > a.length ? [b, a] : [a, b]
    return first.flatMap(function (a_value, index) {
        const b_value = second[index]
        if (second[index] === undefined) {
            return a_value
        }
        return b_value !== undefined ? [a_value, b_value] : a_value
    })
}

export function log_network_call(before_run_timestamp: number) {
    trace(`made 1 network request taking ${Date.now() - before_run_timestamp} milliseconds`)
}

export function create_signed_url(base_url: string, params: Params, special_params?: {
    readonly wts: number,
    readonly dm_img_list: string,
    readonly dm_img_inter: string,
    readonly dm_img_str: string,
    readonly dm_cover_img_str: string
}): URL {
    const local_state = get_local_state()
    const augmented_params: Params = special_params === undefined ? {
        ...params,
        // timestamp
        wts: Math.round(Date.now() / 1e3).toString(),
        // device fingerprint values
        dm_img_inter: local_state.dm_img_inter,
        dm_img_str: local_state.dm_img_str,
        dm_cover_img_str: local_state.dm_cover_img_str,
        dm_img_list: "[]",
    } : {
        ...params,
        // timestamp
        wts: special_params.wts.toString(),
        // device fingerprint values
        dm_img_inter: special_params.dm_img_inter,
        dm_img_str: special_params.dm_img_str,
        dm_cover_img_str: special_params.dm_cover_img_str,
        dm_img_list: special_params.dm_img_list,
    }

    const sorted_query_string = Object
        .entries(augmented_params)
        .sort(function (a, b) { return a[0].localeCompare(b[0]) })
        .map(function ([name, value]) {
            return `${name}=${encodeURIComponent(value)}`
        })
        .join("&")
    const w_rid = local_utility.md5String(sorted_query_string + local_state.mixin_key)
    return new URL(`${base_url}?${sorted_query_string}&w_rid=${w_rid}`)
}

export function create_url(base_url: string, params: Params): URL {
    const url = new URL(base_url)
    for (const [name, value] of Object.entries(params)) {
        url.searchParams.set(name, value)
    }
    return url
}

export function get_requested_video_page(url: string): number | undefined {
    const page = new URL(url).searchParams.get("p")
    if (page === null) {
        return undefined
    }

    const parsed_page = parseInt(page)
    return Number.isNaN(parsed_page) || parsed_page < 1 ? undefined : parsed_page
}

export function trace(msg: string, { showToast = false } = {}) {
    if (get_local_settings()?.verboseNotifications || showToast) {
        bridge.toast(msg)
    }
    log(msg)
}

export function session_cookie(): string {
    const local_state = get_local_state()
    return `buvid3=${local_state.buvid3}; buvid4=${local_state.buvid4}; buvid_fp=${local_state.buvid_fp}; b_nut=${local_state.b_nut}`
}

export function execute_requests<T, U>(
    requests: [RequestMetadata<T>, RequestMetadata<U>]
): [T, U]
export function execute_requests<T, U, V>(
    requests: [RequestMetadata<T>, RequestMetadata<U>, RequestMetadata<V>]
): [T, U, V]
export function execute_requests<T, U, V, W>(
    requests: [RequestMetadata<T>, RequestMetadata<U>, RequestMetadata<V>, RequestMetadata<W>]
): [T, U, V, W]
export function execute_requests<T, U, V, W, X>(
    requests: [RequestMetadata<T>, RequestMetadata<U>, RequestMetadata<V>, RequestMetadata<W>, RequestMetadata<X>]
): [T, U, V, W, X]
export function execute_requests<T, U, V, W, X>(
    requests: [
        RequestMetadata<T> | undefined,
        RequestMetadata<U> | undefined,
        RequestMetadata<V> | undefined,
        RequestMetadata<W> | undefined,
        RequestMetadata<X> | undefined
    ]
): [T | undefined, U | undefined, V | undefined, W | undefined, X | undefined]
export function execute_requests<T, U, V, W, X, Y>(
    requests: [
        RequestMetadata<T>,
        RequestMetadata<U>,
        RequestMetadata<V>,
        RequestMetadata<W>,
        RequestMetadata<X>,
        RequestMetadata<Y>,
    ]
): [T, U, V, W, X, Y]
export function execute_requests<T, U, V, W, X, Y, Z>(
    requests: [
        RequestMetadata<T>,
        RequestMetadata<U>,
        RequestMetadata<V>,
        RequestMetadata<W>,
        RequestMetadata<X>,
        RequestMetadata<Y>,
        RequestMetadata<Z>,
    ]
): [T, U, V, W, X, Y, Z]
/**
 * Execute requests in parallel processes each of the results and return a tuple of results
 * @param requests
 * @returns
 */
export function execute_requests<T, U, V, W, X, Y, Z>(
    requests:
        [RequestMetadata<T>, RequestMetadata<U>]
        | [RequestMetadata<T>, RequestMetadata<U>, RequestMetadata<V>]
        | [RequestMetadata<T>, RequestMetadata<U>, RequestMetadata<V>, RequestMetadata<W>]
        | [RequestMetadata<T>, RequestMetadata<U>, RequestMetadata<V>, RequestMetadata<W>, RequestMetadata<X>]
        | [RequestMetadata<T> | undefined,
            RequestMetadata<U> | undefined,
            RequestMetadata<V> | undefined,
            RequestMetadata<W> | undefined,
            RequestMetadata<X> | undefined]
        | [RequestMetadata<T>,
            RequestMetadata<U>,
            RequestMetadata<V>,
            RequestMetadata<W>,
            RequestMetadata<X>,
            RequestMetadata<Y>]
        | [RequestMetadata<T>,
            RequestMetadata<U>,
            RequestMetadata<V>,
            RequestMetadata<W>,
            RequestMetadata<X>,
            RequestMetadata<Y>,
            RequestMetadata<Z>]
): [T, U]
    | [T, U, V]
    | [T, U, V, W]
    | [T, U, V, W, X]
    | [T | undefined, U | undefined, V | undefined, W | undefined, X | undefined]
    | [T, U, V, W, X, Y]
    | [T, U, V, W, X, Y, Z] {

    const batch = local_http.batch()

    for (const request of requests) {
        if (request !== undefined) {
            request.request(batch)
        }
    }

    const now = Date.now()
    const responses = batch.execute()
    trace(`made ${responses.length} network request(s) in parallel taking ${Date.now() - now} milliseconds`)
    switch (requests.length) {
        case 2: {
            const response_0 = responses[0]
            const response_1 = responses[1]
            if (response_0 === undefined || response_1 === undefined) {
                throw new ScriptException("unreachable")
            }
            return [requests[0].process(response_0), requests[1].process(response_1)]
        }
        case 3: {
            const response_0 = responses[0]
            const response_1 = responses[1]
            const response_2 = responses[2]
            if (response_0 === undefined || response_1 === undefined || response_2 === undefined) {
                throw new ScriptException("unreachable")
            }
            return [requests[0].process(response_0), requests[1].process(response_1), requests[2].process(response_2)]
        }
        case 4: {
            const response_0 = responses[0]
            const response_1 = responses[1]
            const response_2 = responses[2]
            const response_3 = responses[3]
            if (response_0 === undefined || response_1 === undefined || response_2 === undefined || response_3 === undefined) {
                throw new ScriptException("unreachable")
            }
            return [
                requests[0].process(response_0),
                requests[1].process(response_1),
                requests[2].process(response_2),
                requests[3].process(response_3)
            ]
        }
        case 5: {
            let next_response = 0
            let result_0: T | undefined
            let result_1: U | undefined
            let result_2: V | undefined
            let result_3: W | undefined
            let result_4: X | undefined
            if (requests[0] === undefined) {
                result_0 = undefined
            } else {
                const response = responses[next_response]
                if (response === undefined) {
                    throw new ScriptException("unreachable")
                }
                result_0 = requests[0].process(response)
                next_response += 1
            }
            if (requests[1] === undefined) {
                result_1 = undefined
            } else {
                const response = responses[next_response]
                if (response === undefined) {
                    throw new ScriptException("unreachable")
                }
                result_1 = requests[1].process(response)
                next_response += 1
            } if (requests[2] === undefined) {
                result_2 = undefined
            } else {
                const response = responses[next_response]
                if (response === undefined) {
                    throw new ScriptException("unreachable")
                }
                result_2 = requests[2].process(response)
                next_response += 1
            } if (requests[3] === undefined) {
                result_3 = undefined
            } else {
                const response = responses[next_response]
                if (response === undefined) {
                    throw new ScriptException("unreachable")
                }
                result_3 = requests[3].process(response)
                next_response += 1
            } if (requests[4] === undefined) {
                result_4 = undefined
            } else {
                const response = responses[next_response]
                if (response === undefined) {
                    throw new ScriptException("unreachable")
                }
                result_4 = requests[4].process(response)
                next_response += 1
            }
            return [result_0, result_1, result_2, result_3, result_4]
        }
        case 6: {
            const response_0 = responses[0]
            const response_1 = responses[1]
            const response_2 = responses[2]
            const response_3 = responses[3]
            const response_4 = responses[4]
            const response_5 = responses[5]
            if (response_0 === undefined || response_1 === undefined || response_2 === undefined || response_3 === undefined || response_4 === undefined || response_5 === undefined) {
                throw new ScriptException("unreachable")
            }
            return [
                requests[0].process(response_0),
                requests[1].process(response_1),
                requests[2].process(response_2),
                requests[3].process(response_3),
                requests[4].process(response_4),
                requests[5].process(response_5)
            ]
        }
        case 7: {
            const response_0 = responses[0]
            const response_1 = responses[1]
            const response_2 = responses[2]
            const response_3 = responses[3]
            const response_4 = responses[4]
            const response_5 = responses[5]
            const response_6 = responses[6]
            if (response_0 === undefined || response_1 === undefined || response_2 === undefined || response_3 === undefined || response_4 === undefined || response_5 === undefined || response_6 === undefined) {
                throw new ScriptException("unreachable")
            }
            return [
                requests[0].process(response_0),
                requests[1].process(response_1),
                requests[2].process(response_2),
                requests[3].process(response_3),
                requests[4].process(response_4),
                requests[5].process(response_5),
                requests[6].process(response_6)
            ]
        }
        default:
            throw assert_exhaustive(requests, "unreachable")
    }
}

export function parse_content_details_url(url: string) {
    const regex_match_result = url.match(CONTENT_DETAIL_URL_REGEX)
    if (regex_match_result === null) {
        throw new ScriptException(`malformed content url: ${url}`)
    }

    // Check if this is a bilibili.tv URL (group 6 will be populated)
    if (regex_match_result[6] !== undefined) {
        return {
            subdomain: "bilibili.tv" as const,
            content_type: "video/" as const,
            content_id: regex_match_result[6]
        }
    }

    const maybe_subdomain: "live." | "t." | "www." | "m." | "" | undefined = regex_match_result[1] as "live." | "t." | "www." | "m." | "" | undefined
    if (maybe_subdomain === undefined) {
        throw new ScriptException("unreachable regex error")
    }
    const subdomain = maybe_subdomain
    const maybe_domain: "bilibili.com" | "b23.tv" | undefined = regex_match_result[2] as "bilibili.com" | "b23.tv" | undefined
    if (maybe_domain === undefined) {
        throw new ScriptException("unreachable regex error")
    }
    const domain = maybe_domain
    const maybe_content_type: ContentType | undefined = regex_match_result[3] as ContentType | undefined
    if (maybe_content_type === undefined) {
        throw new ScriptException("unreachable regex error")
    }
    let content_type = maybe_content_type
    const maybe_content_id: string | undefined = regex_match_result[4]
    if (maybe_content_id === undefined) {
        throw new ScriptException("unreachable regex error")
    }
    const content_id = maybe_content_id

    // handle b23.tv short URLs by following the redirect
    if (domain === "b23.tv") {
        const response = local_http.GET(url, {}, false)
        const new_url = response.body.match(/<meta data-vue-meta="true" itemprop="url" content="(.*?)">/)?.[1]
        if (new_url !== undefined) {
            return parse_content_details_url(new_url)
        }
        // fallback: try to extract from Location header or other meta tags
        const redirect_url = response.body.match(/<a href="(https:\/\/www\.bilibili\.com\/video\/[^"]+)">/)?.[1]
        if (redirect_url !== undefined) {
            return parse_content_details_url(redirect_url)
        }
        throw new ScriptException(`failed to resolve b23.tv short URL: ${url}`)
    }

    // handle weird url format
    if (content_type === "video/" && /^av[0-9]{15}$/.test(content_id)) {
        const new_url = local_http.GET(url, {}, false).body.match(/<meta data-vue-meta="true" itemprop="url" content="(.*?)">/)?.[1]
        if (new_url === undefined) {
            throw new ScriptException("unreachable regex error")
        }
        return parse_content_details_url(new_url)
    }
    return { subdomain, content_type, content_id }
}
