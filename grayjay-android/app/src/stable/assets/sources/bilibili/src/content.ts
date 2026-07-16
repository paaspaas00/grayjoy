import type {
    VideoDetailResponse,
    VideoPlayResponse,
    Params,
    ContentType,
    EpisodePlayResponse,
    CourseEpisodePlayResponse,
    EpisodeInfoResponse,
    CourseResponse,
    SeasonResponse,
    PostResponse,
    LiveResponse,
    TextNode,
    Major,
    RequestMetadata,
    SubtitlesMetadataResponse,
    SubtitlesDataResponse,
    PlayDataDash,
    IdObj
} from "./types.ts"
import {
    PLATFORM,
    CONTENT_DETAIL_URL_REGEX,
    VIDEO_URL_PREFIX,
    LIVE_ROOM_URL_PREFIX,
    SPACE_URL_PREFIX,
    EPISODE_URL_PREFIX,
    COURSE_URL_PREFIX,
    COURSE_EPISODE_URL_PREFIX,
    POST_URL_PREFIX,
    HARDCODED_THUMBNAIL_QUALITY,
    HARDCODED_ZERO,
    MISSING_NAME,
    MISSING_RATING,
    EMPTY_AUTHOR,
    PREMIUM_CONTENT_MESSAGE,
    local_http,
    USER_AGENT
} from "./constants.ts"
import { get_local_state, get_local_storage_cache, get_local_settings } from "./state.ts"
import {
    parse_content_details_url,
    create_signed_url,
    create_url,
    get_requested_video_page,
    execute_requests,
    convert_subtitles,
    log_network_call,
    assert_exhaustive
} from "./utilities.ts"

// examples of handled urls
// https://www.bilibili.com/bangumi/play/ep510760
// https://live.bilibili.com/26386397
// https://www.bilibili.com/video/BV1M84y1d7S1
// https://www.bilibili.com/opus/916396341363474468
// https://t.bilibili.com/915034213991841801
// https://www.bilibili.com/cheese/play/ep1027
export function isContentDetailsUrl(url: string) {
    return CONTENT_DETAIL_URL_REGEX.test(url)
}
export function getContentDetails(url: string) {
    const page_number = get_requested_video_page(url)
    const { subdomain, content_type, content_id } = parse_content_details_url(url)

    switch (subdomain) {
        case "live.": {
            // Uses the Bilibili Live API endpoints directly
            // https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo
            // https://api.live.bilibili.com/room/v1/Room/get_info

            const room_id = parseInt(content_id)

            const response = livestream_request(room_id)

            const space_id = response.roomInitRes.data.uid

            let source: IVideoSource

            // Note: while the there is always the http_hls ts option this is currently not usable and 404s
            // even when forcing it on the website live.bilibili.com my changing the return value of hasHLSPlayerSupportStream
            const codec = response.roomInitRes.data.playurl_info.playurl.stream
                .find(function (stream) { return stream.protocol_name === "http_hls" })?.format
                .find(function (format) { return format.format_name === "fmp4" })?.codec[0]
            if (codec !== undefined) {
                const url_info = codec.url_info[0]
                const name = response.roomInitRes.data.playurl_info.playurl.g_qn_desc
                    .find(function (item) { return item.qn === codec.current_qn })?.desc
                if (url_info === undefined || name === undefined) {
                    throw new ScriptException("unreachable")
                }
                const video_url = `${url_info.host}${codec.base_url}${url_info.extra}`
                source = new HLSSource({
                    url: video_url,
                    name,
                })
            } else {
                const codec = response.roomInitRes.data.playurl_info.playurl.stream
                    .find(function (stream) { return stream.protocol_name === "http_stream" })?.format
                    .find(function (format) { return format.format_name === "flv" })?.codec[0]
                if (codec === undefined) {
                    throw new ScriptException("unreachable")
                }

                const name = response.roomInitRes.data.playurl_info.playurl.g_qn_desc
                    .find(function (item) { return item.qn === codec.current_qn })?.desc

                let video_url: string | undefined
                let hostname: string | undefined
                for (const url_info of codec.url_info) {
                    const url_host = new URL(url_info.host).hostname
                    const url = `${url_info.host}${codec.base_url}${url_info.extra}`
                    const now = Date.now()
                    // if this request returns 404 it takes like 6 seconds to do so.
                    // however if we remove the Referer header then it returns 403 quickly
                    // even though the browser uses the Referer header and gets a 404 we might consider leaving it
                    // off to get the 403 quicker. i'm not doing it now because 200 is expected in the
                    // vast majority of cases
                    const code = http.request("HEAD", url, {
                        Referer: "https://live.bilibili.com"
                    }, false).code
                    log_network_call(now)
                    if (code === 200) {
                        hostname = url_host
                        video_url = url
                        break
                    }
                }
                if (video_url === undefined || hostname === undefined || name === undefined) {
                    throw new ScriptException("unreachable")
                }

                source = new VideoUrlSource({
                    url: video_url,
                    name,
                    width: HARDCODED_ZERO,
                    height: HARDCODED_ZERO,
                    container: "flv",
                    codec: "avc",
                    bitrate: HARDCODED_ZERO,
                    duration: HARDCODED_ZERO,
                })
            }

            // TODO handle the case where the room is inactive
            // response.roomInfoRes.data.live_status !== 1
            return new PlatformVideoDetails({
                description: response.roomInfoRes.data.news_info.content,
                video: new VideoSourceDescriptor([]),
                rating: new RatingLikes(response.roomInfoRes.data.like_info_v3?.total_likes ?? 0),
                thumbnails: new Thumbnails([
                    new Thumbnail(response.roomInfoRes.data.room_info.cover, HARDCODED_THUMBNAIL_QUALITY)
                ]),
                author: new PlatformAuthorLink(
                    new PlatformID(PLATFORM, space_id.toString(), plugin.config.id),
                    response.roomInfoRes.data.anchor_info.base_info.uname,
                    `${SPACE_URL_PREFIX}${space_id}`,
                    response.roomInfoRes.data.anchor_info.base_info.face,
                    response.roomInfoRes.data.anchor_info.relation_info.attention
                ),
                viewCount: response.roomInfoRes.data.watched_show.num,
                isLive: true,
                shareUrl: `${LIVE_ROOM_URL_PREFIX}${room_id}`,
                datetime: Number(response.roomInfoRes.data.room_info.live_start_time),
                name: response.roomInfoRes.data.room_info.title,
                url: `${LIVE_ROOM_URL_PREFIX}${room_id}`,
                id: new PlatformID(PLATFORM, room_id.toString(), plugin.config.id),
                live: source,
            })
        }
        case "t.": {
            const post_id = content_id
            return get_post(post_id)
        }
        case "www.": return get_video_details(content_type, content_id, page_number)
        case "m.": return get_video_details(content_type, content_id, page_number)
        case "": return get_video_details(content_type, content_id, page_number)
        case "bilibili.tv": return get_video_details(content_type, content_id, page_number)
        default:
            throw assert_exhaustive(subdomain, "unreachable")
    }
}
export function get_video_details(content_type: ContentType, content_id: string, page_number?: number) {
    switch (content_type) {
        // TODO as far as i can tell bangumi don't have subtitles
        case "bangumi/play/ep": {
            const episode_id = parseInt(content_id)

            const requests: [
                RequestMetadata<EpisodePlayResponse>,
                RequestMetadata<SeasonResponse>,
                RequestMetadata<EpisodeInfoResponse>
            ] = [{
                request(builder) { return episode_play_request(episode_id, builder) },
                process(response) { return JSON.parse(response.body) }
            }, {
                request(builder) { return season_request({ type: "episode", id: episode_id }, builder) },
                process(response) { return JSON.parse(response.body) }
            }, {
                request(builder) { return episode_info_request(episode_id, builder) },
                process(response) { return JSON.parse(response.body) }
            }]

            const [episode_response, season_response, episode_info_response] = execute_requests(requests)

            // region restricted
            if (episode_response.code === -10403) {
                let message = "非常抱歉，根据版权方要求\n"
                message += "您所在的地区无法观看本片"
                throw new UnavailableException(message)
            }
            // premium content
            if ("durl" in episode_response.result.video_info) {
                throw new UnavailableException(PREMIUM_CONTENT_MESSAGE)
            }

            const { video_sources, audio_sources } = format_sources(episode_response.result.video_info)

            const upload_info = episode_info_response.data.related_up[0]
            if (upload_info === undefined) {
                throw new ScriptException("missing upload information")
            }
            const owner_id = upload_info.mid

            const episode_season_meta = season_response.result.episodes.find(function (episode) { return episode.ep_id === episode_id })
            if (episode_season_meta === undefined) {
                throw new ScriptException("episode missing from season")
            }

            const platform_video_ID = new PlatformID(PLATFORM, episode_id.toString(), plugin.config.id)
            const platform_creator_ID = new PlatformID(PLATFORM, owner_id.toString(), plugin.config.id)
            const details: PlatformContentDetails = new PlatformVideoDetails({
                id: platform_video_ID,
                name: episode_season_meta.long_title,
                thumbnails: new Thumbnails([new Thumbnail(episode_season_meta.cover, HARDCODED_THUMBNAIL_QUALITY)]),
                author: new PlatformAuthorLink(
                    platform_creator_ID,
                    upload_info.uname,
                    `${SPACE_URL_PREFIX}${owner_id}`,
                    upload_info.avatar,
                    get_local_storage_cache().space_cache.get(owner_id)?.num_fans
                ),
                duration: episode_response.result.video_info.dash.duration,
                viewCount: episode_info_response.data.stat.view,
                url: `${EPISODE_URL_PREFIX}${episode_id}`,
                isLive: false,
                // TODO this will include HTML tags and render poorly
                description: season_response.result.evaluate,
                video: new UnMuxVideoSourceDescriptor(video_sources, audio_sources),
                rating: new RatingLikes(episode_info_response.data.stat.like),
                shareUrl: `${EPISODE_URL_PREFIX}${episode_id}`,
                datetime: Number(episode_season_meta.pub_time)
                // the recommendations are other series which in Grayjay are playlists
                // the recommendations section doesn't currently support playlists
                // the implementation would be similar to courses for the playlists
                // getContentRecommendations
            })
            return details
        }
        case "cheese/play/ep": {
            const episode_id = parseInt(content_id)

            const requests: [RequestMetadata<CourseEpisodePlayResponse>, RequestMetadata<CourseResponse>] = [{
                request(builder) { return course_play_request(episode_id, builder) },
                process(response) { return JSON.parse(response.body) }
            }, {
                request(builder) { return course_request({ type: "episode", id: episode_id }, builder) },
                process(response) { return JSON.parse(response.body) }
            }]

            const [episode_play_response, season_response] = execute_requests(requests)

            // premium content
            if (episode_play_response.code === -403) {
                throw new UnavailableException("Purchase Course")
            }
            if ("durl" in episode_play_response.data) {
                throw new UnavailableException(PREMIUM_CONTENT_MESSAGE)
            }

            const { video_sources, audio_sources } = format_sources(episode_play_response.data)

            const upload_info = season_response.data.up_info
            if (upload_info === undefined) {
                throw new ScriptException("missing upload information")
            }
            const owner_id = upload_info.mid

            const episode_season_metadata = season_response.data.episodes.find(function (episode) { return episode.id === episode_id })
            if (episode_season_metadata === undefined) {
                throw new ScriptException("episode missing from season")
            }

            let subtitles: ISubtitleSource[] | undefined = undefined
            if (bridge.isLoggedIn()) {
                const subtitles_response: SubtitlesMetadataResponse = JSON.parse(subtitles_request(
                    { aid: episode_season_metadata.aid },
                    episode_season_metadata.cid).body
                )
                subtitles = subtitles_response.data.subtitle.subtitles.map(function (subtitle): ISubtitleSource {
                    const url = `https:${subtitle.subtitle_url}`
                    return {
                        url,
                        name: subtitle.lan_doc,
                        getSubtitles() {
                            const json = local_http.GET(url, {}, false).body
                            const response: SubtitlesDataResponse = JSON.parse(json)
                            return convert_subtitles(response, subtitle.lan_doc)
                        },
                        format: "text/vtt",
                    }
                })
            }

            const platform_video_ID = new PlatformID(PLATFORM, episode_id.toString(), plugin.config.id)
            const platform_creator_ID = new PlatformID(PLATFORM, owner_id.toString(), plugin.config.id)
            const platform_video_details_def: IPlatformVideoDetailsDef = {
                id: platform_video_ID,
                name: episode_season_metadata.title,
                thumbnails: new Thumbnails([new Thumbnail(episode_season_metadata.cover, HARDCODED_THUMBNAIL_QUALITY)]),
                author: new PlatformAuthorLink(
                    platform_creator_ID,
                    upload_info.uname,
                    `${SPACE_URL_PREFIX}${owner_id}`,
                    upload_info.avatar,
                    upload_info.follower
                ),
                duration: episode_play_response.data.dash.duration,
                viewCount: episode_season_metadata.play,
                url: `${COURSE_EPISODE_URL_PREFIX}${episode_id}`,
                isLive: false,
                // TODO this will include HTML tags and render poorly
                description: `${season_response.data.title}\n${season_response.data.subtitle}`,
                video: new UnMuxVideoSourceDescriptor(video_sources, audio_sources),
                // TODO figure out a rating to use. courses/course episodes don't have likes
                rating: new RatingLikes(MISSING_RATING),
                shareUrl: `${COURSE_EPISODE_URL_PREFIX}${episode_id}`,
                datetime: Number(episode_season_metadata.release_date),
                // Note Grayjay doesn't support playlists as content recommendations so this does currently do anything
                getContentRecommendations: function () {
                    return new PlaylistPager(season_response.data.recommend_seasons.map((season) => {
                        return new PlatformPlaylist({
                            id: new PlatformID(PLATFORM, season.id.toString(), plugin.config.id),
                            name: season.title,
                            thumbnails: new Thumbnails([new Thumbnail(season.cover, HARDCODED_THUMBNAIL_QUALITY)]),
                            author: EMPTY_AUTHOR,
                            url: `${COURSE_URL_PREFIX}${season.id}`,
                            thumbnail: season.cover
                        })
                    }), false)
                }
            }
            const details: PlatformContentDetails = new PlatformVideoDetails(subtitles === undefined ? platform_video_details_def : {
                ...platform_video_details_def,
                subtitles
            })
            return details
        }
        case "opus/": {
            const post_id = content_id
            return get_post(post_id)
        }
        case "video/": {
            const video_id = content_id
            let video_info: VideoDetailResponse
            let play_info: VideoPlayResponse
            let subtitle_response: SubtitlesMetadataResponse | undefined

            const target_page = page_number ?? 1

            if (target_page > 1) {
                video_info = JSON.parse(video_detail_request(video_id).body)
                if (video_info.code === -404) {
                    throw new UnavailableException("Invalid video URL or the video has been removed")
                }

                const page_entry = video_info.data.View.pages[target_page - 1]
                const target_cid = page_entry !== undefined ? page_entry.cid : video_info.data.View.cid

                if (bridge.isLoggedIn()) {
                    const requests: [RequestMetadata<VideoPlayResponse>, RequestMetadata<SubtitlesMetadataResponse>] = [
                        {
                            request(builder) { return video_play_request(video_id, target_cid, builder) },
                            process(response) { return JSON.parse(response.body) }
                        }, {
                            request(builder) { return subtitles_request({ bvid: video_id }, target_cid, builder) },
                            process(response) { return JSON.parse(response.body) }
                        }
                    ]

                    ;[play_info, subtitle_response] = execute_requests(requests)
                } else {
                    play_info = JSON.parse(video_play_request(video_id, target_cid).body)
                }
            } else if (bridge.isLoggedIn()) {
                ;[video_info, play_info, subtitle_response] = load_video_details(video_id, true)
            } else {
                ;[video_info, play_info] = load_video_details(video_id)
            }

            // premium content
            if ("durl" in play_info.data) {
                throw new UnavailableException(PREMIUM_CONTENT_MESSAGE)
            }
            const { video_sources, audio_sources } = format_sources(play_info.data)

            const subtitles = subtitle_response?.data.subtitle.subtitles.map(function (subtitle): ISubtitleSource {
                const url = `https:${subtitle.subtitle_url}`
                return {
                    url,
                    name: subtitle.lan_doc,
                    getSubtitles() {
                        const json = local_http.GET(url, {}, false).body
                        const response: SubtitlesDataResponse = JSON.parse(json)
                        return convert_subtitles(response, subtitle.lan_doc)
                    },
                    format: "text/vtt",
                }
            })

            const description = video_info.data.View.desc_v2 === null
                ? { raw_text: "" }
                : video_info.data.View.desc_v2[0]

            if (description === undefined) {
                throw new ScriptException("missing description")
            }

            const owner_id = video_info.data.View.owner.mid.toString()
            const is_multipart = video_info.data.View.videos > 1
            const target_page_info = is_multipart ? video_info.data.View.pages[target_page - 1] : undefined
            const video_url = page_number !== undefined
                ? `${VIDEO_URL_PREFIX}${video_id}?p=${page_number}`
                : `${VIDEO_URL_PREFIX}${video_id}`

            const platform_video_ID = new PlatformID(PLATFORM, video_id, plugin.config.id)
            const platform_creator_ID = new PlatformID(PLATFORM, owner_id, plugin.config.id)
            const author = new PlatformAuthorLink(
                platform_creator_ID,
                video_info.data.View.owner.name,
                `${SPACE_URL_PREFIX}${video_info.data.View.owner.mid}`,
                video_info.data.View.owner.face,
                video_info.data.Card.card.fans
            )
            const platform_video_details_def: IPlatformVideoDetailsDef = {
                id: platform_video_ID,
                name: target_page_info !== undefined
                    ? `${video_info.data.View.title} - ${target_page_info.part}`
                    : video_info.data.View.title,
                thumbnails: new Thumbnails([
                    new Thumbnail(video_info.data.View.pic, HARDCODED_THUMBNAIL_QUALITY)
                ]),
                author,
                duration: play_info.data.dash.duration,
                viewCount: video_info.data.View.stat.view,
                url: video_url,
                isLive: false,
                description: description.raw_text,
                video: new UnMuxVideoSourceDescriptor(video_sources, audio_sources),
                rating: new RatingLikes(video_info.data.View.stat.like),
                shareUrl: video_url,
                datetime: Number(video_info.data.View.pubdate)
            }
            const details: PlatformContentDetails = subtitles === undefined
                ? new PlatformVideoDetails(platform_video_details_def)
                : new PlatformVideoDetails({ ...platform_video_details_def, subtitles })
                // @ts-expect-error attach getContentRecommendations post-construction so the desktop's PlatformVideoDetails constructor (which strips it from the def object) does not drop it
                details.getContentRecommendations = () => build_video_content_recommendations(video_info, video_id, target_page)
                //TODO: Desktop - support getContentRecommendations in PlatformVideoDetails construtor
            return details
        }
        default:
            throw assert_exhaustive(content_type, "unreachable")
    }
}
export function livestream_request(room_id: number, builder: BatchBuilder): BatchBuilder
export function livestream_request(room_id: number): LiveResponse
export function livestream_request(room_id: number, builder?: BatchBuilder): BatchBuilder | LiveResponse {
    // Use the API endpoints directly instead of parsing HTML
    // https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo
    // https://api.live.bilibili.com/room/v1/Room/get_info
    const room_play_info_url = `https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?room_id=${room_id}&protocol=0,1&format=0,1,2&codec=0,1&qn=10000&platform=web&ptype=8`
    const room_info_url = `https://api.live.bilibili.com/room/v1/Room/get_info?room_id=${room_id}`

    if (builder !== undefined) {
        // For batch mode, we still need to implement batch support
        // For now, return the builder as-is (this path might need future enhancement)
        return builder
    }

    const now = Date.now()
    const play_info_response = local_http.GET(room_play_info_url, {}, false)
    const room_info_response = local_http.GET(room_info_url, {}, false)

    log_network_call(now)
    log_network_call(now)

    return livestream_process(play_info_response, room_info_response)
}

export function livestream_process(play_info_response: BridgeHttpResponse<string>, room_info_response: BridgeHttpResponse<string>): LiveResponse {
    const play_info = JSON.parse(play_info_response.body)
    const room_info = JSON.parse(room_info_response.body)

    if (play_info.code !== 0 || room_info.code !== 0) {
        throw new ScriptException("Failed to fetch live stream info")
    }

    // Handle offline streams - when the stream is offline, playurl_info is null
    if (play_info.data.playurl_info === null) {
        throw new ScriptException("Stream is currently offline")
    }

    // Construct the LiveResponse format from the API responses
    const response: LiveResponse = {
        roomInitRes: {
            data: {
                playurl_info: play_info.data.playurl_info,
                uid: play_info.data.uid
            }
        },
        roomInfoRes: {
            data: {
                room_info: {
                    description: room_info.data.description,
                    live_status: room_info.data.live_status,
                    title: room_info.data.title,
                    live_start_time: new Date(room_info.data.live_time).getTime() / 1000,
                    cover: room_info.data.user_cover
                },
                anchor_info: {
                    base_info: {
                        uname: "",  // Not available in this API response
                        face: ""    // Not available in this API response
                    },
                    relation_info: {
                        attention: room_info.data.attention
                    }
                },
                news_info: {
                    content: ""  // Not available in this API response
                },
                watched_show: {
                    num: room_info.data.online,
                    text_large: `${room_info.data.online}`
                }
            }
        }
    }

    return response
}
/**
 * Downloads and formats a post
 * @param post_id
 * @returns
 */
export function get_post(post_id: string) {
    const post_response = download_post(post_id)
    const space_post = post_response.data.item
    const desc = space_post.modules.module_dynamic.desc
    const images: string[] = []
    const thumbnails: Thumbnails[] = []

    const primary_content = desc?.rich_text_nodes
        .map(function (node) { return format_text_node(node, images, thumbnails) })
        .join("")

    const major = space_post.modules.module_dynamic.major
    const major_links = major !== null ? format_major(major, thumbnails, images) : undefined

    const topic = space_post.modules.module_dynamic.topic
    const topic_string = topic ? `<a href="${topic?.jump_url}">${topic.name}</a>\n` : undefined

    const content = (primary_content ? primary_content + "\n" : "") + (topic_string ?? "") + (major_links ?? "")

    return new PlatformPostDetails({
        thumbnails,
        images,
        description: content,
        name: MISSING_NAME,
        url: `${POST_URL_PREFIX}${space_post.id_str}`,
        id: new PlatformID(PLATFORM, space_post.id_str, plugin.config.id),
        rating: new RatingLikes(space_post.modules.module_stat.like.count),
        textType: Type.Text.HTML,
        author: new PlatformAuthorLink(
            new PlatformID(PLATFORM, space_post.modules.module_author.mid.toString(), plugin.config.id),
            space_post.modules.module_author.name,
            `${SPACE_URL_PREFIX}${space_post.modules.module_author.mid}`,
            space_post.modules.module_author.face,
            get_local_storage_cache().space_cache.get(space_post.modules.module_author.mid)?.num_fans
        ),
        content,
        datetime: Number(space_post.modules.module_author.pub_ts)
    })
}
export function download_post(post_id: string): PostResponse {
    const single_post_prefix = "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail"
    const params: Params = {
        id: post_id
    }
    const url = create_url(single_post_prefix, params).toString()
    const now = Date.now()
    const json = local_http.GET(url, { Cookie: `buvid3=${get_local_state().buvid3}`, "User-Agent": USER_AGENT, Host: "api.bilibili.com" }, false).body
    log_network_call(now)
    const post_response: PostResponse = JSON.parse(json)
    return post_response
}
/**
 * Formats a text node of a post into HTML
 * @param node
 * @param images Output array for images in the post that corresponds to thumbnails
 * @param thumbnails Output array for thumbnails for the images in the post
 * @returns HTML string
 */
export function format_text_node(node: TextNode, images: string[], thumbnails: Thumbnails[]): string {
    switch (node.type) {
        case "RICH_TEXT_NODE_TYPE_TEXT":
            return node.text
        case "RICH_TEXT_NODE_TYPE_TOPIC":
            return `<a href="${node.jump_url}">${node.text}</a>`
        case "RICH_TEXT_NODE_TYPE_AV":
            return `<a href="https:${node.jump_url}">${node.text}</a>`
        // TODO handle image emojis
        case "RICH_TEXT_NODE_TYPE_EMOJI":
            return node.text
        // TODO handle lotteries
        case "RICH_TEXT_NODE_TYPE_LOTTERY":
            return node.text
        // TODO handle voting
        case "RICH_TEXT_NODE_TYPE_VOTE":
            return node.text
        case "RICH_TEXT_NODE_TYPE_VIEW_PICTURE": {
            for (const pic of node.pics) {
                images.push(pic.src)
                thumbnails.push(new Thumbnails([new Thumbnail(pic.src, pic.height)]))
            }
            return ""
        }
        case "RICH_TEXT_NODE_TYPE_AT":
            return `<a href="${SPACE_URL_PREFIX}${node.rid}">${node.text}</a>`
        case "RICH_TEXT_NODE_TYPE_CV":
            return `<a href="https://www.bilibili.com/read/cv${node.rid}">${node.text}</a>`
        case "RICH_TEXT_NODE_TYPE_WEB":
            return `<a href="${node.jump_url}">${node.text}</a>`
        case "RICH_TEXT_NODE_TYPE_GOODS":
            return `<a href="${node.jump_url}">${node.text}</a>`
        case "RICH_TEXT_NODE_TYPE_MAIL":
            return `<a href="mailto:${node.text}">${node.text}</a>`
        case "RICH_TEXT_NODE_TYPE_BV":
            return `<a href="${VIDEO_URL_PREFIX}${node.rid}">${node.text}</a>`
        case "RICH_TEXT_NODE_TYPE_OGV_EP":
            return `<a href="https://www.bilibili.com/bangumi/play/${node.rid}">${node.text}</a>`
        default:
            throw assert_exhaustive(node, `unhandled type on node ${node}`)
    }
}
export function format_major(major: Major, thumbnails: Thumbnails[], images: string[]): string | undefined {
    switch (major.type) {
        case "MAJOR_TYPE_ARCHIVE":
            images.push(major.archive.cover)
            thumbnails.push(new Thumbnails([new Thumbnail(major.archive.cover, HARDCODED_THUMBNAIL_QUALITY)]))
            return `<a href="${VIDEO_URL_PREFIX}${major.archive.bvid}">${major.archive.title}</a>`
        case "MAJOR_TYPE_DRAW":
            for (const pic of major.draw.items) {
                images.push(pic.src)
                thumbnails.push(new Thumbnails([new Thumbnail(pic.src, HARDCODED_THUMBNAIL_QUALITY)]))
            }
            return undefined
        case "MAJOR_TYPE_OPUS":
            for (const pic of major.opus.pics) {
                images.push(pic.url)
                thumbnails.push(new Thumbnails([new Thumbnail(pic.url, HARDCODED_THUMBNAIL_QUALITY)]))
            }
            return major.opus.summary.rich_text_nodes.map(function (node) {
                return format_text_node(node, images, thumbnails)
            }
            ).join("")
        case "MAJOR_TYPE_LIVE_RCMD": {
            const live_rcmd: {
                readonly live_play_info: {
                    readonly cover: string
                    readonly room_id: number
                    readonly title: string
                }
            } = JSON.parse(major.live_rcmd.content)
            images.push(live_rcmd.live_play_info.cover)
            thumbnails.push(new Thumbnails([new Thumbnail(live_rcmd.live_play_info.cover, HARDCODED_THUMBNAIL_QUALITY)]))
            return `<a href="${LIVE_ROOM_URL_PREFIX}${live_rcmd.live_play_info.room_id}">${live_rcmd.live_play_info.title}</a>`
        }
        case "MAJOR_TYPE_COMMON": {
            images.push(major.common.cover)
            thumbnails.push(new Thumbnails([new Thumbnail(major.common.cover, HARDCODED_THUMBNAIL_QUALITY)]))
            return `<a href="${major.common.jump_url}">${major.common.title}</a>`
        }
        case "MAJOR_TYPE_ARTICLE": {
            for (const cover of major.article.covers) {
                images.push(cover)
                thumbnails.push(new Thumbnails([new Thumbnail(cover, HARDCODED_THUMBNAIL_QUALITY)]))
            }
            return `<a href="https://www.bilibili.com/read/cv${major.article.id}">${major.article.title}</a>`
        }
        case "MAJOR_TYPE_COURSES":
            images.push(major.courses.cover)
            thumbnails.push(new Thumbnails([new Thumbnail(major.courses.cover, HARDCODED_THUMBNAIL_QUALITY)]))
            return `<a href="${COURSE_URL_PREFIX}${major.courses.id}">${major.courses.title}</a>`
        default:
            throw assert_exhaustive(major, `unhandled type on major ${major}`)
    }
}
export function episode_play_request(episode_id: number, builder: BatchBuilder): BatchBuilder
export function episode_play_request(episode_id: number): BridgeHttpResponse<string>
export function episode_play_request(episode_id: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const play_url_prefix = "https://api.bilibili.com/pgc/player/web/v2/playurl"
    const params: Params = {
        fnval: "4048",
        ep_id: episode_id.toString()
    }
    const runner = builder === undefined ? local_http : builder
    const url = create_url(play_url_prefix, params).toString()
    const now = Date.now()
    const result = runner.GET(
        url,
        { "User-Agent": USER_AGENT, Host: "api.bilibili.com" },
        false
    )
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function season_request(id_obj: IdObj, builder: BatchBuilder): BatchBuilder
export function season_request(id_obj: IdObj): BridgeHttpResponse<string>
export function season_request(id_obj: IdObj, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const season_prefix = "https://api.bilibili.com/pgc/view/web/season"
    const params: Params = (function (id_obj: IdObj): Params {
        switch (id_obj.type) {
            case "season":
                return {
                    season_id: id_obj.id.toString()
                }
            case "episode":
                return {
                    ep_id: id_obj.id.toString()
                }
            default:
                throw assert_exhaustive(id_obj, "unreachable")
        }
    })(id_obj)
    const season_url = create_url(season_prefix, params)
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(
        season_url.toString(),
        {},
        false
    )
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function episode_info_request(episode_id: number, builder: BatchBuilder): BatchBuilder
export function episode_info_request(episode_id: number): BridgeHttpResponse<string>
export function episode_info_request(episode_id: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const episode_info_prefix = "https://api.bilibili.com/pgc/season/episode/web/info"
    const info_params: Params = {
        ep_id: episode_id.toString()
    }
    const runner = builder === undefined ? local_http : builder
    const url = create_url(episode_info_prefix, info_params).toString()
    const now = Date.now()
    const result = runner.GET(
        url,
        {},
        false
    )
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function course_play_request(episode_id: number, builder: BatchBuilder): BatchBuilder
export function course_play_request(episode_id: number): BridgeHttpResponse<string>
export function course_play_request(episode_id: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const play_url_prefix = "https://api.bilibili.com/pugv/player/web/playurl"
    const params: Params = {
        fnval: "4048",
        ep_id: episode_id.toString()
    }
    const url = create_url(play_url_prefix, params).toString()
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(
        url,
        { "User-Agent": USER_AGENT, Host: "api.bilibili.com" },
        false
    )
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function course_request(id_obj: IdObj, builder: BatchBuilder): BatchBuilder
export function course_request(id_obj: IdObj): BridgeHttpResponse<string>
export function course_request(id_obj: IdObj, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const season_prefix = "https://api.bilibili.com/pugv/view/web/season"
    const params: Params = (function (id_obj: IdObj): Params {
        switch (id_obj.type) {
            case "season":
                return {
                    season_id: id_obj.id.toString()
                }
            case "episode":
                return {
                    ep_id: id_obj.id.toString()
                }
            default:
                throw assert_exhaustive(id_obj, "unreachable")
        }
    })(id_obj)
    const season_url = create_url(season_prefix, params)
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(season_url.toString(), {}, false)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function load_video_details(video_id: string): [VideoDetailResponse, VideoPlayResponse]
export function load_video_details(video_id: string, is_logged_in: true): [VideoDetailResponse, VideoPlayResponse, SubtitlesMetadataResponse]
export function load_video_details(video_id: string, is_logged_in: boolean = false): [VideoDetailResponse, VideoPlayResponse] | [VideoDetailResponse, VideoPlayResponse, SubtitlesMetadataResponse] {
    const cid = get_local_storage_cache().cid_cache.get(video_id)

    if (cid === undefined) {
        const detail_response: VideoDetailResponse = JSON.parse(video_detail_request(video_id).body)

        if (detail_response.code !== 0) {
            throw new UnavailableException(`Video unavailable (code: ${detail_response.code})`)
        }

        if (is_logged_in) {
            const requests: [RequestMetadata<VideoPlayResponse>, RequestMetadata<SubtitlesMetadataResponse>] = [
                {
                    request(builder) {
                        return video_play_request(video_id, detail_response.data.View.cid, builder)
                    },
                    process(response) { return JSON.parse(response.body) }
                }, {
                    request(builder) {
                        return subtitles_request({ bvid: video_id }, detail_response.data.View.cid, builder)
                    },
                    process(response) { return JSON.parse(response.body) }
                }
            ]

            const [play_response, subtitles_response] = execute_requests(requests)

            return [detail_response, play_response, subtitles_response]
        }



        const play_response: VideoPlayResponse = JSON.parse(video_play_request(video_id, detail_response.data.View.cid).body)

        return [detail_response, play_response]
    } else {
        if (is_logged_in) {
            const requests: [
                RequestMetadata<VideoDetailResponse>,
                RequestMetadata<VideoPlayResponse>,
                RequestMetadata<SubtitlesMetadataResponse>
            ] = [
                    {
                        request(builder) { return video_detail_request(video_id, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }, {
                        request(builder) { return video_play_request(video_id, cid, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }, {
                        request(builder) { return subtitles_request({ bvid: video_id }, cid, builder) },
                        process(response) { return JSON.parse(response.body) }
                    }
                ]

            return execute_requests(requests)
        }
        const requests: [RequestMetadata<VideoDetailResponse>, RequestMetadata<VideoPlayResponse>] = [
            {
                request(builder) { return video_detail_request(video_id, builder) },
                process(response) { return JSON.parse(response.body) }
            }, {
                request(builder) { return video_play_request(video_id, cid, builder) },
                process(response) { return JSON.parse(response.body) }
            }
        ]

        return execute_requests(requests)
    }
}
export function video_detail_request(bvid: string, builder: BatchBuilder): BatchBuilder
export function video_detail_request(bvid: string): BridgeHttpResponse<string>
export function video_detail_request(bvid: string, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const detail_prefix = "https://api.bilibili.com/x/web-interface/wbi/view/detail"
    const params: Params = {
        bvid
    }
    const url = create_signed_url(detail_prefix, params)
    const buvid3 = get_local_state().buvid3
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    const result = runner.GET(
        url.toString(),
        {
            Host: "api.bilibili.com",
            "User-Agent": USER_AGENT,
            Referer: "https://www.bilibili.com",
            Cookie: `buvid3=${buvid3}`
        },
        false
    )
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}
export function video_play_request(bvid: string, cid: number, builder: BatchBuilder): BatchBuilder
export function video_play_request(bvid: string, cid: number): BridgeHttpResponse<string>
export function video_play_request(bvid: string, cid: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const play_prefix = "https://api.bilibili.com/x/player/wbi/playurl"
    const params: Params = {
        bvid,
        fnval: "4048",
        cid: cid.toString(),
    }
    const url = create_signed_url(play_prefix, params)
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    // use the authenticated client to get higher resolution videos for logged in users
    const result = runner.GET(url.toString(), { "User-Agent": USER_AGENT }, true)
    if (builder === undefined) {
        log_network_call(now)
    }

    return result
}
export function subtitles_request(id: { bvid: string } | { aid: number }, cid: number, builder: BatchBuilder): BatchBuilder
export function subtitles_request(id: { bvid: string } | { aid: number }, cid: number): BridgeHttpResponse<string>
export function subtitles_request(id: { bvid: string } | { aid: number }, cid: number, builder?: BatchBuilder | HTTP): BatchBuilder | BridgeHttpResponse<string> {
    const subtitles_prefix = "https://api.bilibili.com/x/player/wbi/v2"
    const params: Params = "bvid" in id ? {
        bvid: id.bvid,
        cid: cid.toString(),
    } : {
        aid: id.aid.toString(),
        cid: cid.toString(),
    }
    const url = create_signed_url(subtitles_prefix, params)
    const runner = builder === undefined ? local_http : builder
    const now = Date.now()
    // use the authenticated client because login is required to view subtitles
    const result = runner.GET(url.toString(), { "User-Agent": USER_AGENT }, true)
    if (builder === undefined) {
        log_network_call(now)
    }
    return result
}

type MultipartPage = VideoDetailResponse["data"]["View"]["pages"][number]
type UgcSeasonEpisode = NonNullable<VideoDetailResponse["data"]["View"]["ugc_season"]>["sections"][number]["episodes"][number]

export function get_multipart_recommendation_plan(pages: readonly MultipartPage[], target_page: number, next_only_recommendations: boolean) {
    return {
        pages: pages.filter((page) => next_only_recommendations ? page.page > target_page : page.page !== target_page),
        includeRelatedVideos: !next_only_recommendations
    }
}

export function get_ugc_season_episodes(view: VideoDetailResponse["data"]["View"]): readonly UgcSeasonEpisode[] {
    const season = view.ugc_season
    if (season === undefined || season === null) {
        return []
    }
    const sections = season.sections
    if (!Array.isArray(sections)) {
        return []
    }
    return sections.flatMap((section) => Array.isArray(section?.episodes) ? section.episodes : [])
        .filter((episode): episode is UgcSeasonEpisode => episode !== null && episode !== undefined && typeof episode.bvid === "string" && episode.bvid.length > 0)
}

export function get_collection_recommendation_plan(episodes: readonly UgcSeasonEpisode[], current_bvid: string, next_only_recommendations: boolean) {
    const current_index = episodes.findIndex((episode) => episode.bvid === current_bvid)
    return {
        episodes: episodes.filter((episode, index) => {
            if (episode.bvid === current_bvid) {
                return false
            }
            if (next_only_recommendations && current_index !== -1) {
                return index > current_index
            }
            return true
        }),
        includeRelatedVideos: !next_only_recommendations
    }
}

export function build_video_content_recommendations(video_info: VideoDetailResponse, video_id: string, target_page: number): VideoPager {
    const view = video_info.data.View
    const owner_mid = view.owner.mid
    const author = new PlatformAuthorLink(
        new PlatformID(PLATFORM, owner_mid.toString(), plugin.config.id),
        view.owner.name,
        `${SPACE_URL_PREFIX}${owner_mid}`,
        view.owner.face,
        video_info.data.Card.card.fans
    )
    const related_videos = (video_info.data.Related ?? []).map((video) => new PlatformVideo({
        id: new PlatformID(PLATFORM, video.bvid, plugin.config.id),
        name: video.title,
        thumbnails: new Thumbnails([new Thumbnail(video.pic, HARDCODED_THUMBNAIL_QUALITY)]),
        author: new PlatformAuthorLink(
            new PlatformID(PLATFORM, video.owner.mid.toString(), plugin.config.id),
            video.owner.name,
            `${SPACE_URL_PREFIX}${owner_mid}`,
            video.owner.face
        ),
        datetime: Number(video.pubdate),
        url: `${VIDEO_URL_PREFIX}${video.bvid}`,
        duration: video.duration,
        viewCount: video.stat.view,
        isLive: false,
        shareUrl: `${VIDEO_URL_PREFIX}${video.bvid}`,
    }))

    const next_only_recommendations = get_local_settings()?.multipartNextOnlyRecommendations === true
    const is_multipart = view.videos > 1

    if (is_multipart) {
        const recommendation_plan = get_multipart_recommendation_plan(view.pages, target_page, next_only_recommendations)
        const part_videos = recommendation_plan.pages.map((page) => new PlatformVideo({
            id: new PlatformID(PLATFORM, `${video_id}:p${page.page}`, plugin.config.id),
            name: page.part,
            url: `${VIDEO_URL_PREFIX}${video_id}?p=${page.page}`,
            thumbnails: new Thumbnails([new Thumbnail(page.first_frame, HARDCODED_THUMBNAIL_QUALITY)]),
            author,
            duration: page.duration,
            viewCount: view.stat.view,
            isLive: false,
            shareUrl: `${VIDEO_URL_PREFIX}${video_id}?p=${page.page}`,
            datetime: Number(view.pubdate),
        }))
        return new VideoPager(recommendation_plan.includeRelatedVideos ? [...part_videos, ...related_videos] : part_videos, false)
    }

    const ugc_season_episodes = get_ugc_season_episodes(view)
    if (ugc_season_episodes.length > 1) {
        const recommendation_plan = get_collection_recommendation_plan(ugc_season_episodes, video_id, next_only_recommendations)
        const episode_videos = recommendation_plan.episodes.map((episode) => {
            const arc = episode.arc
            const thumbnail_url = arc?.pic ?? view.pic
            return new PlatformVideo({
                id: new PlatformID(PLATFORM, episode.bvid, plugin.config.id),
                name: episode.title ?? "",
                url: `${VIDEO_URL_PREFIX}${episode.bvid}`,
                thumbnails: new Thumbnails([new Thumbnail(thumbnail_url, HARDCODED_THUMBNAIL_QUALITY)]),
                author,
                duration: arc?.duration ?? episode.page?.duration ?? 0,
                viewCount: arc?.stat?.view ?? 0,
                isLive: false,
                shareUrl: `${VIDEO_URL_PREFIX}${episode.bvid}`,
                datetime: Number(arc?.pubdate ?? view.pubdate),
            })
        })
        return new VideoPager(recommendation_plan.includeRelatedVideos ? [...episode_videos, ...related_videos] : episode_videos, false)
    }

    return new VideoPager(related_videos, false)
}

export function getContentRecommendations(url: string): VideoPager {
    const target_page = get_requested_video_page(url) ?? 1
    const { subdomain, content_type, content_id } = parse_content_details_url(url)

    if (content_type === "video/" && (subdomain === "www." || subdomain === "m." || subdomain === "" || subdomain === "bilibili.tv")) {
        const video_id = content_id
        const video_info: VideoDetailResponse = JSON.parse(video_detail_request(video_id).body)
        if (video_info.code === -404) {
            throw new UnavailableException("Invalid video URL or the video has been removed")
        }
        return build_video_content_recommendations(video_info, video_id, target_page)
    }

    return new VideoPager([], false)
}

export function format_sources(play_data: PlayDataDash) {
    const video_sources: VideoUrlRangeSource[] = play_data.dash.video.map(function (video) {
        const name = play_data.accept_description[
            play_data.accept_quality.findIndex(function (value) {
                return value === video.id
            })
        ]
        const [initStart, initEnd] = video.segment_base.initialization.split("-").map(function (val) { return parseInt(val) })
        const [indexStart, indexEnd] = video.segment_base.index_range.split("-").map(function (val) { return parseInt(val) })
        if (name === undefined || initStart === undefined || initEnd === undefined || indexStart === undefined || indexEnd === undefined) {
            throw new ScriptException("can't load content details")
        }
        const video_url_hostname = new URL(video.base_url).hostname

        return new VideoUrlRangeSource({
            width: video.width,
            height: video.height,
            container: video.mime_type,
            codec: video.codecs,
            // frameRate: parseInt(video.frame_rate),
            name: name,
            bitrate: video.bandwidth,
            duration: play_data.dash.duration,
            url: video.base_url,
            itagId: video.id,
            initStart,
            initEnd,
            indexStart,
            indexEnd,
            requestModifier: {
                headers: {
                    "Referer": "https://www.bilibili.com",
                    "Host": video_url_hostname,
                    "User-Agent": USER_AGENT
                }
            }
        })
    })

    const audio_sources: AudioUrlRangeSource[] = play_data.dash.audio.map(function (audio) {
        const audio_url_hostname = new URL(audio.base_url).hostname
        const [initStart, initEnd] = audio.segment_base.initialization.split("-").map(function (val) { return parseInt(val) })
        const [indexStart, indexEnd] = audio.segment_base.index_range.split("-").map(function (val) { return parseInt(val) })
        if (initStart === undefined || initEnd === undefined || indexStart === undefined || indexEnd === undefined) {
            throw new ScriptException("can't load content details")
        }
        return new AudioUrlRangeSource({
            container: audio.mime_type,
            codec: audio.codecs,
            name: `${audio.codecs} at ${audio.bandwidth}`,
            bitrate: audio.bandwidth,
            duration: play_data.dash.duration,
            url: audio.base_url,
            language: Language.UNKNOWN,
            itagId: audio.id,
            initStart,
            initEnd,
            indexStart,
            indexEnd,
            audioChannels: 2,
            requestModifier: {
                headers: {
                    "Referer": "https://www.bilibili.com",
                    "Host": audio_url_hostname,
                    "User-Agent": USER_AGENT
                }
            }
        })
    })
    return { audio_sources, video_sources }
}
