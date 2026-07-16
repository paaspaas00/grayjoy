//#region custom types
export type BiliBiliCommentContext = {
    /** the id of the content */
    readonly oid: string
    /** the parent/root comment id */
    readonly rpid: string
    /** the type of comment "33" for course episode comments and "1" for everything else */
    readonly type: "33" | "1" | "11"
}

export type RequestMetadata<X> = {
    request(builder: BatchBuilder): BatchBuilder
    process(http_response: BridgeHttpResponse<string>): X
}

export type Params = {
    readonly [key: string]: string
}

export type IdObj = {
    readonly id: number,
    readonly type: "season"
} | {
    readonly id: number,
    readonly type: "episode"
}

export type OrderOptions = "click" | "pubdate" | "stow"

/**
 * Regex capture group options that correspond to different content types
 */
export type ContentType = "bangumi/play/ep" | "video/" | "opus/" | "cheese/play/ep"

/**
 * Regex capture group options that correspond to different playlist types
 */
export type PlaylistType = "bangumi/play/ss"
    | "cheese/play/ss"
    | "/channel/collectiondetail?sid="
    | "/channel/seriesdetail?sid="
    | "/favlist?fid="
    | "medialist/detail/ml"
    | "festival/"
    | "watchlater/"

export type Settings = {
    readonly verboseNotifications?: boolean | string
    readonly multipartNextOnlyRecommendations?: boolean | string
}

export type PluginSettings = {
    readonly verboseNotifications?: boolean
    readonly multipartNextOnlyRecommendations?: boolean
}

export type BiliBiliSource = Required<
    Omit<
        Source<
            BiliBiliCommentContext,
            FilterGroupIDs,
            ChannelTypeCapabilities,
            SearchTypeCapabilities,
            ChannelSearchTypeCapabilities,
            Settings
        >,
        "getPlaybackTracker"
        | "getShorts"
    >
>
export type FilterGroupIDs = "DURATION_FILTER"

export type ChannelTypeCapabilities = Exclude<FeedType, typeof Type.Feed.Shorts>

export type ChannelSearchTypeCapabilities = Exclude<ChannelTypeCapabilities, typeof Type.Feed.Videos | typeof Type.Feed.Live | typeof Type.Feed.Shorts>

export type SearchTypeCapabilities = Exclude<FeedType, typeof Type.Feed.Videos | typeof Type.Feed.Live | typeof Type.Feed.Shorts>

export type Wbi = { readonly wbi_img_key: string, readonly wbi_sub_key: string }

export type State = {
    readonly buvid3: string
    readonly buvid4: string
    readonly buvid_fp: string
    readonly b_nut: number
    readonly mixin_key: string
    readonly dm_cover_img_str: string
    readonly dm_img_str: string
    readonly dm_img_inter: string
}

export type LocalCache = {
    readonly cid_cache: Map<string, number>
    readonly space_cache: Map<number, CoreSpaceInfo>
}

export type CoreSpaceInfo = {
    readonly name: string
    readonly face: string
    readonly num_fans: number
    readonly live_room: null | {
        readonly cover: string
        readonly roomid: number
        readonly title: string
        readonly live_status: boolean
        readonly watched_show: {
            readonly num: number
        }
    }
}
//#endregion

//#region JSON types
export type FingerSpiResponse = {
    readonly data: {
        readonly b_3: string
        readonly b_4: string
    }
}

export type NavResponse = {
    readonly data: {
        readonly isLogin: false
        readonly wbi_img: {
            readonly img_url: string,
            readonly sub_url: string
        }
    }
}

export type LoggedInNavResponse = {
    readonly data: {
        readonly isLogin: true
        readonly mid: number
        readonly face: string
        readonly uname: string
    }
}

export type UserSubscriptionsResponse = {
    readonly data: {
        readonly list: {
            readonly mid: number
        }[]
        readonly total: number
    }
}

export type SpaceVideos = {
    readonly data: {
        readonly list: {
            readonly vlist: {
                readonly title: string
                readonly bvid: string
                readonly pic: string
                readonly length: string
                readonly created: number
                readonly play: number
            }[]
        }
    }
}

export type PostResponse = {
    readonly data: {
        readonly item: {
            readonly basic: {
                readonly comment_id_str: string
            }
            readonly id_str: string
            readonly modules: {
                readonly module_dynamic: {
                    readonly desc: null | {
                        readonly rich_text_nodes: TextNode[]
                    }
                    readonly major: null | Major
                    readonly topic: null | {
                        readonly id: number
                        readonly jump_url: string
                        readonly name: string
                    }
                }
                readonly module_author: {
                    readonly pub_ts: number
                    readonly face: string
                    readonly name: string
                    readonly mid: number
                }
                readonly module_stat: {
                    readonly like: {
                        readonly count: number
                    }
                }
            }
        }
    }
}

export type LiveResponse = {
    readonly roomInitRes: {
        readonly data: {
            readonly playurl_info: {
                readonly playurl: {
                    readonly g_qn_desc: {
                        readonly qn: number
                        readonly desc: string
                    }[]
                    readonly stream: {
                        readonly format: {
                            readonly codec: {
                                readonly current_qn: number
                                readonly base_url: string
                                readonly url_info: {
                                    readonly host: string
                                    readonly extra: string
                                }[]
                            }[]
                            readonly format_name: "ts" | "fmp4" | "flv"
                        }[]
                        readonly protocol_name: "http_stream" | "http_hls"
                    }[]
                }
            }
            readonly uid: number
        }
    }
    readonly roomInfoRes: {
        readonly data: {
            readonly room_info: {
                readonly description: string
                readonly live_status: number
                readonly title: string
                readonly live_start_time: number
                readonly cover: string
            }
            readonly anchor_info: {
                readonly base_info: {
                    readonly uname: string
                    readonly face: string
                }
                readonly relation_info: {
                    readonly attention: number
                }
            }
            readonly news_info: {
                readonly content: string
            }
            readonly watched_show: {
                readonly num: number
                readonly text_large?: string
            }
            readonly like_info_v3?: {
                readonly total_likes: number
            }
        }
    }
}

export type SpaceResponse = {
    readonly code: 0
    readonly data: {
        readonly name: string
        readonly face: string
        readonly top_photo: string
        readonly sign: string
        readonly live_room: {
            readonly cover: string
            readonly roomid: number
            readonly title: string
            readonly liveStatus: number
            readonly watched_show: {
                readonly num: number
            }
        }
    }
} | {
    readonly code: -352
}

export type SubCommentsResponse = {
    readonly data: {
        readonly page: {
            readonly count: number
        }
        readonly replies: Comment[]
    }
}

export type Comment = {
    readonly rpid: number
    readonly ctime: number
    readonly like: number
    readonly rcount: number
    readonly member: {
        readonly mid: number
        readonly uname: string
        readonly avatar: string
    }
    readonly content: {
        readonly message: string
    }
    readonly replies: {
        readonly rpid: number
    }[]
}

export type CommentsResponse = {
    readonly data: {
        readonly cursor: {
            is_end: boolean
        }
        readonly replies: Comment[]
        readonly top: {
            readonly upper: Comment | null
        }
    }
    readonly code: 0
} | {
    readonly code: -404
}

export type WatchLaterResponse = {
    readonly data: {
        readonly count: number
        readonly list: {
            readonly pic: string
            readonly title: string
            readonly pubdate: number
            readonly owner: {
                readonly mid: number
                readonly name: string
                readonly face: string
            }
            readonly duration: number
            readonly bvid: string
            readonly cid: number
            readonly stat: {
                readonly view: number
                readonly like: number
            }
        }[]
    }
}

export type FavoritesResponse = {
    readonly data: {
        readonly info: {
            readonly upper: {
                readonly mid: number
                readonly name: string
                readonly face: string
            }
            readonly id: number
            readonly title: string
            readonly cover: string
            readonly media_count: number
        }
        readonly medias: {
            readonly title: string
            readonly cover: string
            readonly duration: number
            readonly pubtime: number
            readonly cnt_info: {
                readonly play: number
            }
            readonly bvid: string
            readonly ugc: {
                readonly first_cid: number
            }
            readonly upper: {
                readonly mid: number
                readonly name: string
                readonly face: string
            }
        }[] | null
        readonly has_more: boolean
    }
}

export type FestivalResponse = {
    readonly themeConfig: {
        readonly page_bg_img: string
    }
    readonly title: string
    readonly sectionEpisodes: {
        readonly bvid: string
        readonly cid: number
        readonly title: string
        readonly author: {
            readonly mid: number
            readonly name: string
            readonly face: string
        }
        readonly cover: string
    }[]
}

export type CourseEpisodePlayResponse = {
    readonly data: PlayData
    readonly code: 0
} | InsufficientCourseEpisodePlayResponse

export type InsufficientCourseEpisodePlayResponse = {
    readonly code: -403
}

export type CourseResponse = {
    readonly data: {
        readonly episodes: {
            readonly id: number
            readonly aid: number
            readonly cid: number
            readonly cover: string
            readonly title: string
            readonly release_date: number
            readonly play: number
        }[]
        readonly cover: string
        readonly title: string
        readonly subtitle: string
        readonly ep_count: number
        readonly up_info: {
            readonly avatar: string
            readonly mid: number
            readonly follower: number
            readonly uname: string
        }
        readonly recommend_seasons: {
            readonly cover: "https://archive.biliimg.com/bfs/archive/0ab2d492ba085a0a7b67ea766097420eedbe5118.jpg",
            readonly ep_count: "共40课时",
            readonly id: 1279,
            readonly title: "李梦娇时政专项班（清华同款）",
            readonly view: 274174
        }[]
    }
}

export type SeasonResponse = {
    readonly result: {
        readonly evaluate: string
        readonly episodes: {
            readonly ep_id: number
            readonly aid: number
            readonly bvid: string
            readonly cid: number
            readonly cover: string
            readonly duration: number
            readonly long_title: string
            readonly pub_time: number
        }[]
        readonly cover: string
        readonly title: string
        readonly stat: {
            readonly views: number
        }
    }
}

export type CollectionResponse = {
    readonly data: {
        readonly archives: {
            readonly bvid: string
            readonly duration: number
            readonly pic: string
            readonly pubdate: number
            readonly title: string
            readonly stat: {
                readonly view: number
            }
        }[]
        readonly meta: {
            readonly cover: string
            readonly name: string
            readonly total: number
        }
    }
}

export type SeriesResponse = {
    readonly data: {
        readonly archives: {
            readonly bvid: string
            readonly duration: number
            readonly pic: string
            readonly pubdate: number
            readonly title: string
            readonly stat: {
                readonly view: number
            }
        }[]
        readonly page: {
            readonly total: number
        }
    }
}

export type TextNode = {
    readonly text: string
    readonly jump_url: string
    readonly type: "RICH_TEXT_NODE_TYPE_TOPIC" | "RICH_TEXT_NODE_TYPE_WEB" | "RICH_TEXT_NODE_TYPE_GOODS" | "RICH_TEXT_NODE_TYPE_AV"
} | {
    readonly text: string,
    readonly type: "RICH_TEXT_NODE_TYPE_EMOJI" | "RICH_TEXT_NODE_TYPE_LOTTERY" | "RICH_TEXT_NODE_TYPE_TEXT" | "RICH_TEXT_NODE_TYPE_MAIL" | "RICH_TEXT_NODE_TYPE_VOTE"
} | {
    readonly pics: {
        readonly height: number
        readonly src: string
        readonly width: number
    }[]
    readonly rid: string
    readonly text: string
    readonly type: "RICH_TEXT_NODE_TYPE_VIEW_PICTURE"
} | {
    readonly type: "RICH_TEXT_NODE_TYPE_BV" | "RICH_TEXT_NODE_TYPE_AT" | "RICH_TEXT_NODE_TYPE_CV" | "RICH_TEXT_NODE_TYPE_OGV_EP"
    readonly rid: string
    readonly text: string
}

export type Major = {
    readonly type: "MAJOR_TYPE_DRAW"
    readonly draw: {
        readonly items: {
            src: string // a url
        }[]
    }
} | {
    readonly type: "MAJOR_TYPE_ARCHIVE"
    readonly archive: {
        bvid: string
        title: string
        cover: string
    }
} | {
    readonly type: "MAJOR_TYPE_OPUS"
    readonly opus: {
        readonly pics: {
            readonly url: string
        }[]
        readonly summary: {
            readonly rich_text_nodes: TextNode[]
        }
    }
} | {
    readonly type: "MAJOR_TYPE_LIVE_RCMD"
    readonly live_rcmd: {
        /** A JSON string */
        readonly content: string
    }
} | {
    readonly type: "MAJOR_TYPE_COMMON"
    readonly common: {
        readonly title: string
        readonly jump_url: string
        readonly cover: string
    }
} | {
    readonly type: "MAJOR_TYPE_ARTICLE"
    readonly article: {
        readonly covers: string[]
        readonly title: string
        readonly id: number
    }
} | {
    readonly type: "MAJOR_TYPE_COURSES"
    readonly courses: {
        readonly cover: string
        readonly title: string
        readonly id: number
    }
}

export type SpaceFavoritesResponse = {
    /** Null if there is no favorites section */
    readonly data: null | {
        /** Null if the count of favorites lists is 0 */
        list: null | {
            readonly media_count: number
            readonly title: string
            readonly id: number
        }[]
        readonly count: number
    }
}

export type SpaceCollectionsResponse = {
    readonly data: {
        readonly items_lists: {
            readonly seasons_list: {
                readonly meta: {
                    readonly season_id: number
                    readonly name: string
                    readonly description: string
                    readonly cover: string
                    readonly total: number
                }
            }[]
            readonly series_list: {
                readonly meta: {
                    readonly series_id: number
                    readonly name: string
                    readonly description: string
                    readonly cover: string
                    readonly total: number
                }
            }[]
            readonly page: {
                readonly total: number
            }
        }
    }
}
export type SpaceCoursesResponse = {
    readonly data: {
        readonly items: {
            readonly cover: string
            readonly ep_count: number
            readonly season_id: number
            readonly title: string
        }[]
        readonly page: {
            readonly next: boolean
            readonly total: number
        }
    }
}
export type SpaceBangumiResponse = {
    readonly data: {
        readonly list : {
            readonly season_id: number
            readonly cover: string
            readonly publish: {
                readonly release_date: string
            }
            readonly title: string
            readonly formal_ep_count: number
        }[]
        readonly pn: number,
        readonly ps: number,
        readonly total: number
    }
}
export type SpacePost = {
    readonly id_str: string
    readonly modules: {
        readonly module_dynamic: {
            readonly desc: null | {
                readonly rich_text_nodes: TextNode[]
            }
            readonly major: null | Major
            readonly topic: null | {
                readonly id: number
                readonly jump_url: string
                readonly name: string
            }
        }
        readonly module_author: {
            readonly pub_ts: number
            readonly face: string
            readonly name: string
            readonly mid: number
        }
        readonly module_stat: {
            readonly like: {
                readonly count: number
            }
        }
    }
    /** A reference to a different post */
    readonly orig: SpacePost
    readonly type: "DYNAMIC_TYPE_FORWARD" | "DYNAMIC_TYPE_AV"
}

export type SpacePostsResponse = {
    readonly data: {
        readonly items: SpacePost[]
        readonly has_more: boolean
        readonly offset: number
    }
    readonly code: 0
}

export type MaybeSpacePostsResponse = SpacePostsResponse | {
    readonly code: -352
}

export type Card = {
    readonly item?: {
        readonly content?: string
        readonly description?: string
    }
    readonly dynamic?: string
}

export type SpacePostsSearchResponse = {
    readonly data: {
        readonly total: number
        readonly cards: null | {
            readonly desc: {
                readonly like: number
                readonly dynamic_id_str: string
                readonly timestamp: number
                readonly user_profile: {
                    readonly info: {
                        readonly face: string
                        readonly uid: number
                        readonly uname: string
                    }
                }
            }
            /** A JSON string see Card type */
            readonly card: string
        }[]
    }
}

export type MaybeSpaceVideosSearchResponse = SpaceVideosSearchResponse | { readonly code: -352 }

export type SpaceVideosSearchResponse = {
    readonly data: {
        readonly list: {
            readonly vlist: {
                readonly bvid: string
                readonly title: string
                readonly length: string
                readonly pic: string
                /** Can be "--" for removed videos */
                readonly play: number | "--"
                readonly author: string
                readonly created: number
            }[]
        }
        readonly page: {
            readonly count: number
            readonly pn: number
        }
    }
    readonly code: 0
}

export type LiveSearchResponse = {
    readonly data: {
        readonly result?: {
            readonly live_room: SearchResultItem[] | null
        }
        readonly pageinfo: {
            readonly live_room: {
                readonly total: number
            }
        }
    } | {
        readonly v_voucher: string
    }
}
export type SearchResultQueryType = "live" | "video" | "media_bangumi" | "media_ft" | "bili_user"
export type SearchResultItemType = SearchResultItem["type"]

export type SearchResultItem = {
    readonly bvid: string
    readonly title: string
    readonly duration: string
    readonly pic: string
    readonly upic: string
    readonly play: number
    readonly pubdate: number
    readonly mid: number
    readonly author: string
    readonly type: "video"
} | {
    readonly type: "media_ft"
    readonly season_id: number
    readonly title: string
    readonly cover: string
    readonly ep_size: number
    readonly eps: null | {
        readonly id: number
        readonly cover: string
    }[]
    readonly pubtime: number
    readonly url: string
} | {
    readonly type: "media_bangumi"
    readonly season_id: number
    readonly title: string
    readonly cover: string
    readonly pubtime: number
    readonly ep_size: number
    readonly eps: {
        readonly id: number
        readonly cover: string
    }[]
} | {
    readonly type: "live_room"
    readonly roomid: number
    readonly title: string
    readonly uid: number
    readonly uname: string
    readonly uface: string
    /** Livestream thumbnail */
    readonly user_cover: string
    readonly watched_show: {
        /** Current viewers */
        readonly num: number
    }
    readonly live_time: string
} | {
    readonly type: "ketang"
    readonly title: string
    readonly pic: string
    readonly play: number
    readonly mid: number
    readonly author: string
    readonly id: number
    readonly episode_count_text: string
    /** I think this is always 0 :( */
    readonly pubdate: never
} | {
    readonly mid: number
    readonly uname: string
    readonly upic: string
    readonly fans: number
    readonly usign: string
    readonly type: "bili_user"
}

export type SearchResponse = {
    readonly data: {
        readonly result?: SearchResultItem[]
        readonly numResults: number
    }
}

export type VideoDetailResponse = {

    readonly code: number

    readonly data: {
        readonly View: {
            readonly title: string
            readonly pic: string
            readonly pubdate: number
            readonly cid: number
            readonly aid: number
            readonly videos: number
            readonly pages: {
                readonly cid: number
                readonly page: number
                readonly part: string
                readonly duration: number
                readonly first_frame: string
            }[]
            readonly owner: {
                readonly mid: number
                readonly name: string
                readonly face: string
            }
            readonly stat: {
                readonly view: number
                readonly like: number
            }
            readonly desc_v2: null | {
                readonly raw_text: string
            }[]
            readonly ugc_season?: {
                readonly id: number
                readonly title: string
                readonly cover: string
                readonly mid: number
                readonly sections: {
                    readonly season_id: number
                    readonly id: number
                    readonly title: string
                    readonly episodes: {
                        readonly id: number
                        readonly aid: number
                        readonly cid: number
                        readonly bvid: string
                        readonly title: string
                        readonly arc: {
                            readonly pic: string
                            readonly pubdate: number
                            readonly duration: number
                            readonly stat: {
                                readonly view: number
                            }
                        }
                        readonly page?: {
                            readonly cid: number
                            readonly part: string
                            readonly duration: number
                            readonly first_frame?: string
                        }
                    }[]
                }[]
            }
        }
        readonly Card: {
            readonly card: {
                readonly fans: number
            }
        }
        readonly Related: {
            readonly title: string
            readonly pic: string
            readonly pubdate: number
            readonly cid: number
            readonly aid: number
            readonly bvid: string
            readonly owner: {
                readonly mid: number
                readonly name: string
                readonly face: string
            }
            readonly stat: {
                readonly view: number
                readonly like: number
            }
            readonly duration: number
        }[]
    }
}

export type EpisodeInfoResponse = {
    readonly data: {
        readonly related_up: {
            readonly avatar: string
            readonly mid: number
            readonly uname: string
        }[]
        readonly stat: {
            readonly view: number
            readonly like: number
        }
    }
}

export type PlayDataDash = {
    readonly accept_description: string[]
    readonly accept_quality: number[]
    readonly video_codecid: number
    readonly dash: {
        readonly duration: number
        readonly video: {
            readonly base_url: string
            readonly mime_type: string
            readonly codecs: string
            readonly bandwidth: number
            readonly width: number
            readonly height: number
            readonly frame_rate: string
            readonly codecid: number
            readonly id: number
            readonly segment_base: {
                /** of the form "0-974" */
                readonly initialization: string
                /** of the form "975-1270" */
                readonly index_range: string
            }
        }[]
        readonly audio: {
            readonly id: number
            readonly base_url: string
            readonly mime_type: string
            readonly codecs: string
            readonly bandwidth: number
            readonly segment_base: {
                /** of the form "0-974" */
                readonly initialization: string
                /** of the form "975-1270" */
                readonly index_range: string
            }
        }[]
    }
}

export type PlayData = PlayDataDash | {
    readonly durl: []
}

export type EpisodePlayResponse = {
    readonly result: {
        readonly video_info: PlayData
    }
    readonly code: 0
    readonly message: "success"
} | {
    readonly code: -10403,
    readonly message: "抱歉您所在地区不可观看！"
}

export type VideoPlayResponse = {
    readonly data: PlayData
}

export type SubtitlesMetadataResponse = {
    readonly data: {
        readonly subtitle: {
            readonly subtitles: {
                lan: string
                lan_doc: string
                subtitle_url: string
            }[]
        }
    }
}

export type SubtitlesDataResponse = {
    readonly body: {
        readonly from: number
        readonly to: number
        readonly sid: number
        readonly location: number
        readonly content: string
        readonly string: number
    }[]
}

type HomeFeedItem = {
    readonly goto: "av"
    readonly bvid: string
    readonly cid: number
    readonly uri: string
    readonly pic: string
    readonly title: string
    readonly duration: number
    readonly pubdate: number
    readonly stat: {
        readonly view: number
    }
    readonly owner: HomeFeedItemOwner
} | {
    readonly goto: "ad"
} | {
    readonly goto: "live"
    readonly pic: string
    readonly id: number
    readonly title: string
    readonly room_info: {
        readonly watched_show: {
            readonly num: number
        }
    }
    readonly owner: HomeFeedItemOwner
}

type HomeFeedItemOwner = {
    readonly mid: number
    readonly name: string
    readonly face: string
}

export type HomeFeedResponse = {
    readonly data: {
        readonly item: HomeFeedItem[]
    }
} | null

export type SuggestionsResponse = {
    readonly result: {
        readonly tag: {
            readonly term: string
        }[]
    }
}
//#endregion
