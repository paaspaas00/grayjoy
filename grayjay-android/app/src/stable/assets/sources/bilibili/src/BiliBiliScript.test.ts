import { before, describe, test } from "node:test"
import assert from "node:assert"
import "@kaidelorenzo/grayjay-polyfill"
import "./BiliBiliScript.ts"

import { getMixinKey, mixin_constant_request, process_mixin_constant, process_wbi_keys, nav_request, init_local_storage } from "./enable.ts"
import { interleave_two, create_signed_url } from "./utilities.ts"
import { get_collection_recommendation_plan, get_multipart_recommendation_plan, load_video_details } from "./content.ts"
import { get_local_settings, set_local_settings } from "./state.ts"
import type { Params, VideoDetailResponse } from "./types.ts"

const MIXIN_CONSTANT = [46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52] as const
const MULTIPART_PAGES_FIXTURE: VideoDetailResponse["data"]["View"]["pages"] = [
    { cid: 1, page: 1, part: "Part 1", duration: 60, first_frame: "https://example.com/1.jpg" },
    { cid: 2, page: 2, part: "Part 2", duration: 60, first_frame: "https://example.com/2.jpg" },
    { cid: 3, page: 3, part: "Part 3", duration: 60, first_frame: "https://example.com/3.jpg" },
    { cid: 4, page: 4, part: "Part 4", duration: 60, first_frame: "https://example.com/4.jpg" },
    { cid: 5, page: 5, part: "Part 5", duration: 60, first_frame: "https://example.com/5.jpg" }
]
type UgcSeasonEpisode = NonNullable<VideoDetailResponse["data"]["View"]["ugc_season"]>["sections"][number]["episodes"][number]
const COLLECTION_EPISODES_FIXTURE: UgcSeasonEpisode[] = [
    { id: 1, aid: 101, cid: 1001, bvid: "BV1aaaaaaaaa", title: "Episode 1", arc: { pic: "https://example.com/a.jpg", pubdate: 1700000000, duration: 600, stat: { view: 100 } } },
    { id: 2, aid: 102, cid: 1002, bvid: "BV1bbbbbbbbb", title: "Episode 2", arc: { pic: "https://example.com/b.jpg", pubdate: 1700000100, duration: 600, stat: { view: 200 } } },
    { id: 3, aid: 103, cid: 1003, bvid: "BV1cccccccccc", title: "Episode 3", arc: { pic: "https://example.com/c.jpg", pubdate: 1700000200, duration: 600, stat: { view: 300 } } },
    { id: 4, aid: 104, cid: 1004, bvid: "BV1dddddddddd", title: "Episode 4", arc: { pic: "https://example.com/d.jpg", pubdate: 1700000300, duration: 600, stat: { view: 400 } } }
]

describe("script module", { skip: false }, () => {
    before(() => init_local_storage())
    test("test disable", { skip: false }, () => {
        if (source.disable === undefined) {
            throw new Error("Missing disable method")
        }
        source.disable()
    })
    test("get video details", { skip: false }, () => {
        const result = load_video_details("BV1ZW4y1Q7Y4")
        assert.strictEqual(result[0].data.View.title, "被elo机制制裁的号到底有多难打\u{ff1f}",)
        if ("durl" in result[1].data) {
            assert.strictEqual("durl" in result[1].data, false)
        } else {
            assert.strictEqual(result[1].data.dash.duration, 164)
        }
    })
    test("multipart next-only setting excludes earlier parts and related videos", { skip: false }, () => {
        const recommendation_plan = get_multipart_recommendation_plan(MULTIPART_PAGES_FIXTURE, 3, true)

        assert.deepStrictEqual(recommendation_plan.pages.map((page) => page.page), [4, 5])
        assert.strictEqual(recommendation_plan.includeRelatedVideos, false)
    })
    test("multipart recommendations include all other parts and related videos by default", { skip: false }, () => {
        const recommendation_plan = get_multipart_recommendation_plan(MULTIPART_PAGES_FIXTURE, 3, false)

        assert.deepStrictEqual(recommendation_plan.pages.map((page) => page.page), [1, 2, 4, 5])
        assert.strictEqual(recommendation_plan.includeRelatedVideos, true)
    })
    test("collection next-only setting excludes earlier episodes and related videos", { skip: false }, () => {
        const recommendation_plan = get_collection_recommendation_plan(COLLECTION_EPISODES_FIXTURE, "BV1bbbbbbbbb", true)

        assert.deepStrictEqual(recommendation_plan.episodes.map((episode) => episode.bvid), ["BV1cccccccccc", "BV1dddddddddd"])
        assert.strictEqual(recommendation_plan.includeRelatedVideos, false)
    })
    test("collection recommendations include all other episodes and related videos by default", { skip: false }, () => {
        const recommendation_plan = get_collection_recommendation_plan(COLLECTION_EPISODES_FIXTURE, "BV1bbbbbbbbb", false)

        assert.deepStrictEqual(recommendation_plan.episodes.map((episode) => episode.bvid), ["BV1aaaaaaaaa", "BV1cccccccccc", "BV1dddddddddd"])
        assert.strictEqual(recommendation_plan.includeRelatedVideos, true)
    })
})
describe("utility functions", () => {
    before(() => init_local_storage())
    test("compute hash", { skip: false }, () => {
        {
            const url = "https://google.com"
            const wts = 1711726858
            const dm_img_inter = `{"ds":[{"t":0,"c":"","p":[246,82,82],"s":[56,5149,-1804]}],"wh":[4533,2116,69],"of":[461,922,461]}`
            const dm_img_str = "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ"
            const dm_cover_img_str = "QU5HTEUgKEludGVsLCBNZXNhIEludGVsKFIpIEhEIEdyYXBoaWNzIDUyMCAoU0tMIEdUMiksIE9wZW5HTCA0LjYpR29vZ2xlIEluYy4gKEludGVsKQ"
            const dm_img_list = "[]"
            const params: Params = {
                mid: "1939254464",
                platform: "web",
                token: "",
                web_location: "1550101"
            }
            assert.strictEqual(
                create_signed_url(url, params, {
                    wts,
                    dm_img_inter,
                    dm_img_str,
                    dm_cover_img_str,
                    dm_img_list
                }).toString(),
                "https://google.com/?dm_cover_img_str=QU5HTEUgKEludGVsLCBNZXNhIEludGVsKFIpIEhEIEdyYXBoaWNzIDUyMCAoU0tMIEdUMiksIE9wZW5HTCA0LjYpR29vZ2xlIEluYy4gKEludGVsKQ&dm_img_inter=%7B%22ds%22%3A%5B%7B%22t%22%3A0%2C%22c%22%3A%22%22%2C%22p%22%3A%5B246%2C82%2C82%5D%2C%22s%22%3A%5B56%2C5149%2C-1804%5D%7D%5D%2C%22wh%22%3A%5B4533%2C2116%2C69%5D%2C%22of%22%3A%5B461%2C922%2C461%5D%7D&dm_img_list=%5B%5D&dm_img_str=V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ&mid=1939254464&platform=web&token=&web_location=1550101&wts=1711726858&w_rid=8b43929329835b4a05707dc89b248ada")
        }
    })
    test("getMixinKey", { skip: false }, () => {
        const wbi_img_key = "7cd084941338484aae1ad9425b84077c"
        const wbi_sub_key = "4932caff0ff746eab6f01bf08b70ac45"
        const mixin_key = getMixinKey(wbi_img_key + wbi_sub_key, MIXIN_CONSTANT)
        assert.strictEqual(mixin_key, "ea1db124af3c7062474693fa704f4ff8")
    })
    test("get_mixin_constant", { skip: false }, () => {
        assert.deepStrictEqual(process_mixin_constant(mixin_constant_request()), MIXIN_CONSTANT)
    })
    test("get wbi keys", { skip: false }, () => {
        assert.deepStrictEqual(process_wbi_keys(nav_request(false)), {
            wbi_img_key: "7cd084941338484aae1ad9425b84077c",
            wbi_sub_key: "4932caff0ff746eab6f01bf08b70ac45"
        })
    })
    test("interleave same length", { skip: false }, () => {
        const a = [1, 2, 3]
        const b = ["a", "b", "c"]
        assert.deepStrictEqual(interleave_two(a, b), [1, "a", 2, "b", 3, "c"])
    })
    test("interleave first longer", { skip: false }, () => {
        const a = [1, 2, 3, 4]
        const b = ["a", "b", "c"]
        assert.deepStrictEqual(interleave_two(a, b), [1, "a", 2, "b", 3, "c", 4])
    })
    test("multipart recommendation setting is normalized to boolean", { skip: false }, () => {
        set_local_settings({ verboseNotifications: false, multipartNextOnlyRecommendations: true })
        assert.strictEqual(get_local_settings().multipartNextOnlyRecommendations, true)
    })
})
