import type { Params, BiliBiliCommentContext, CommentsResponse, SubCommentsResponse, SeasonResponse, VideoDetailResponse } from "./types.ts"
import { PLATFORM, SPACE_URL_PREFIX, local_http, VIDEO_URL_PREFIX, EPISODE_URL_PREFIX, COURSE_EPISODE_URL_PREFIX, POST_URL_PREFIX, USER_AGENT } from "./constants.ts"
import { get_local_storage_cache } from "./state.ts"
import { create_signed_url, create_url, log_network_call, trace, assert_exhaustive, parse_content_details_url } from "./utilities.ts"
import { download_post, season_request, video_detail_request } from "./content.ts"

// TODO when we load comments we actually download all the replies.
// we should cache them so that when getSubComments is called we don't have to make any networks requests
export function getComments(url: string): CommentPager<BiliBiliCommentContext> {
    const { subdomain, content_type, content_id } = parse_content_details_url(url)
    const reduced_subdomain = subdomain === "m." || subdomain === "" || subdomain === "bilibili.tv" ? "www." : subdomain

    if (reduced_subdomain === "live.") {
        return new CommentPager([], false)
    }
    const [oid, type, context_url] = (function (): [number, 1 | 33 | 11, string] {
        switch (reduced_subdomain) {
            case "t.": {
                const post_id = content_id
                const post_response = download_post(post_id)
                return [parseInt(post_response.data.item.basic.comment_id_str), 11, `${POST_URL_PREFIX}${post_id}`]
            }
            case "www.":
                switch (content_type) {
                    case "bangumi/play/ep": {
                        const episode_id = parseInt(content_id)
                        const season_response: SeasonResponse = JSON.parse(season_request({ id: episode_id, type: "episode" }).body)
                        const episode_info = season_response.result.episodes.find(function (episode) { return episode.ep_id === episode_id })
                        if (episode_info === undefined) {
                            throw new ScriptException("season missing episode")
                        }
                        return [episode_info.aid, 1, `${EPISODE_URL_PREFIX}${episode_id}`]
                    }
                    case "cheese/play/ep": {
                        const episode_id = parseInt(content_id)
                        return [episode_id, 33, `${COURSE_EPISODE_URL_PREFIX}${episode_id}`]
                    }
                    case "opus/": {
                        const post_id = content_id
                        const post_response = download_post(post_id)
                        return [parseInt(post_response.data.item.basic.comment_id_str), 11, `${POST_URL_PREFIX}${post_id}`]
                    }
                    case "video/": {
                        const video_id = content_id
                        const video_info: VideoDetailResponse = JSON.parse(video_detail_request(video_id).body)
                        return [video_info.data.View.aid, 1, `${VIDEO_URL_PREFIX}${video_id}`]
                    }
                    default:
                        throw assert_exhaustive(content_type, "unreachable")
                }
            default:
                throw assert_exhaustive(reduced_subdomain, "unreachable")
        }
    })()

    const pager = new BiliBiliCommentPager(context_url, oid, type, 1)
    return pager
}
export class BiliBiliCommentPager extends CommentPager<BiliBiliCommentContext> {
    private readonly type: 1 | 33 | 11
    private readonly oid: number
    private readonly context_url: string
    private next_page: number
    constructor(context_url: string, oid: number, type: 1 | 33 | 11, initial_page: number) {
        const comments_response = get_comments(oid, type, initial_page)
        switch (comments_response.code) {
            case -404:
                super([], false)
                break
            case 0: {
                const more = !comments_response.data.cursor.is_end
                super(format_comments(comments_response, context_url, oid, type, initial_page === 1), more)
                break
            }
            default:
                throw assert_exhaustive(comments_response, "unreachable")
        }

        this.next_page = initial_page + 1
        this.oid = oid
        this.type = type
        this.context_url = context_url
    }
    override nextPage(this: BiliBiliCommentPager): BiliBiliCommentPager {
        const comments_response = get_comments(this.oid, this.type, this.next_page)
        switch (comments_response.code) {
            case -404:
                this.hasMore = false
                this.results = []
                break
            case 0:
                this.hasMore = !comments_response.data.cursor.is_end
                this.results = format_comments(comments_response, this.context_url, this.oid, this.type, this.next_page === 1)
                break
            default:
                throw assert_exhaustive(comments_response, "unreachable")
        }

        this.next_page += 1
        return this
    }
    override hasMorePagers(this: BiliBiliCommentPager): boolean {
        return this.hasMore
    }
}
export function get_comments(oid: number, type: 1 | 33 | 11, page: number): CommentsResponse {
    const comments_preix = "https://api.bilibili.com/x/v2/reply/wbi/main"
    const params: Params = {
        type: type.toString(),
        mode: "3",
        pagination_str: JSON.stringify({
            offset: JSON.stringify({
                type: 1,
                direction: 1,
                data: {
                    pn: page
                }
            })
        }),
        oid: oid.toString()
    }
    const comment_url = create_signed_url(comments_preix, params).toString()

    const now = Date.now()
    const json = local_http.GET(comment_url, {}, false).body
    log_network_call(now)

    const results: CommentsResponse = JSON.parse(json)
    return results
}
/**
 * Converts raw comment data into a Grayjay PlatformComments
 * @param comments_response
 * @param context_url
 * @param oid
 * @param type
 * @param include_pinned_comment
 * @returns
 */
export function format_comments(
    comments_response: CommentsResponse,
    context_url: string,
    oid: number,
    type: 1 | 33 | 11,
    include_pinned_comment: boolean
): PlatformComment<BiliBiliCommentContext>[] {
    if (comments_response.code === -404) {
        return []
    }
    const replies = comments_response.data.replies
    if (include_pinned_comment && comments_response.data.top.upper !== null) {
        replies.unshift(comments_response.data.top.upper)
    }
    const local_storage_cache = get_local_storage_cache()
    const comments = replies.map(function (data) {
        const author_id = new PlatformID(PLATFORM, data.member.mid.toString(), plugin.config.id)
        return new PlatformComment<BiliBiliCommentContext>({
            author: new PlatformAuthorLink(
                author_id,
                data.member.uname,
                `${SPACE_URL_PREFIX}${data.member.mid}`,
                data.member.avatar,
                local_storage_cache.space_cache.get(data.member.mid)?.num_fans),
            message: data.content.message,
            rating: new RatingLikes(data.like),
            replyCount: data.rcount,
            date: data.ctime,
            contextUrl: context_url,
            context: {
                oid: oid.toString(), rpid: data.rpid.toString(), type: (function (type): "1" | "33" | "11" {
                    switch (type) {
                        case 1:
                            return "1"
                        case 33:
                            return "33"
                        case 11:
                            return "11"
                        default:
                            throw assert_exhaustive(type, "unreachable")
                    }
                })(type)
            }
        })
    })
    return comments
}
export function getSubComments(parent_comment: PlatformComment<BiliBiliCommentContext>): CommentPager<BiliBiliCommentContext> {
    const oid = parseInt(parent_comment.context.oid)
    const rpid = parseInt(parent_comment.context.rpid)
    const type = parent_comment.context.type
    return new SubCommentPager(rpid, oid, type, parent_comment.contextUrl, 1, 20)
}
export class SubCommentPager extends CommentPager<BiliBiliCommentContext> {
    private readonly type: "1" | "33" | "11"
    private readonly oid: number
    private readonly root: number
    private readonly context_url: string
    private next_page: number
    private readonly page_size: number
    constructor(root: number, oid: number, type: "1" | "33" | "11", context_url: string, initial_page: number, page_size: number) {
        const replies_response = get_replies(oid, root, type, initial_page, page_size)
        const more = replies_response.data.page.count > initial_page * page_size
        super(format_replies(replies_response, type, oid, context_url), more)
        this.next_page = initial_page + 1
        this.oid = oid
        this.type = type
        this.root = root
        this.context_url = context_url
        this.page_size = page_size
    }
    override nextPage(this: SubCommentPager): SubCommentPager {
        const replies_response = get_replies(this.oid, this.root, this.type, this.next_page, this.page_size)
        this.hasMore = replies_response.data.page.count > this.next_page * this.page_size
        this.results = format_replies(replies_response, this.type, this.oid, this.context_url)
        this.next_page += 1
        return this
    }
    override hasMorePagers(this: SubCommentPager): boolean {
        return this.hasMore
    }
}
/**
 *
 * @param oid The root context for the comments (the aid for bangumi and videos, the episode id for courses, and basic->comment_id_str for posts
 * @param root_rpid The parent comment id
 * @param type The type of base content to retrieve replies about (33 for courses and 1 for everything else)
 * @param page
 * @param page_size
 * @returns
 */
export function get_replies(oid: number, root_rpid: number, type: "1" | "33" | "11", page: number, page_size: number) {
    const thread_prefix = "https://api.bilibili.com/x/v2/reply/reply"
    const params: Params = {
        type: type,
        pn: page.toString(),
        ps: page_size.toString(),
        oid: oid.toString(),
        root: root_rpid.toString()
    }

    const url = create_url(thread_prefix, params).toString()
    const now = Date.now()
    const json = local_http.GET(url, { "User-Agent": USER_AGENT }, false).body
    log_network_call(now)

    const results: SubCommentsResponse = JSON.parse(json)
    return results
}
/**
 * Converts raw subcomment data into a Grayjay PlatformComments
 * @param comment_data
 * @param type
 * @param oid
 * @param context_url
 * @returns
 */
export function format_replies(
    comment_data: SubCommentsResponse,
    type: "1" | "33" | "11",
    oid: number,
    context_url: string
): PlatformComment<BiliBiliCommentContext>[] {
    const local_storage_cache = get_local_storage_cache()
    const comments = comment_data.data.replies.flatMap(function (comment) {
        if (comment.replies?.length) {
            trace(`unexpected sub-sub-comments on rpid ${comment.rpid}, skipping`)
            return []
        }
        const author_id = new PlatformID(PLATFORM, comment.member.mid.toString(), plugin.config.id)
        return [new PlatformComment<BiliBiliCommentContext>({
            author: new PlatformAuthorLink(
                author_id,
                comment.member.uname,
                `${SPACE_URL_PREFIX}${comment.member.mid}`,
                comment.member.avatar,
                local_storage_cache.space_cache.get(comment.member.mid)?.num_fans),
            message: comment.content.message,
            rating: new RatingLikes(comment.like),
            // as far as we know BiliBili doesn't support subsubcomments
            replyCount: 0,
            date: comment.ctime,
            contextUrl: context_url,
            context: { oid: oid.toString(), rpid: comment.rpid.toString(), type }
        })]
    })
    return comments
}
/**
 * this doesn't really work. we probably need to use getLiveEvents instead
 * the elements don't get removed for some reason
 * and there is weird height code such that even if we were able to delete the elements the comments
 * likely wouldn't fill the whole screen
 * we should load the chat history from
 * (mobile browser)
 * https://api.live.bilibili.com/AppRoom/msg?room_id=26386397
 * or
 * (desktop browser)
 * https://api.live.bilibili.com/xlive/web-room/v1/dM/gethistory?roomid=26386397
 * or figure out how to use the websockets to load chat in realtime
 * https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo?id=5050&type=0
 * wss://hw-sg-live-comet-02.chat.bilibili.com/sub
 * @param url
 * @returns
 */
export function getLiveChatWindow(url: string) {
    trace("live chatting")
    return {
        url,
        removeElements: [".head-info", ".bili-btn-warp", "#app__player-area"]
    }
}
