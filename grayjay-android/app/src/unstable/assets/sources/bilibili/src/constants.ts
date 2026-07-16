export const PLATFORM = "BiliBili" as const
export const CONTENT_DETAIL_URL_REGEX = /^https:\/\/(www\.|live\.|t\.|m\.|)(bilibili\.com|b23\.tv)\/(bangumi\/play\/ep|video\/|opus\/|cheese\/play\/ep|)(\d+|[0-9a-zA-Z]{7,12}|av[0-9]{15})(\/|\?|$)|^https:\/\/www\.bilibili\.tv\/[a-z]{2}\/video\/(\d+)(\/|\?|$)/
export const PLAYLIST_URL_REGEX = /^https:\/\/(www|space)\.bilibili.com\/(\d+|)(bangumi\/play\/ss|cheese\/play\/ss|medialist\/detail\/ml|festival\/|\/channel\/collectiondetail\?sid=|\/channel\/seriesdetail\?sid=|\/favlist\?fid=|watchlater\/)(\d+|[0-9a-zA-Z]+|.*#\/list)(\/|\?|$)/
export const SPACE_URL_REGEX = /^https:\/\/space\.bilibili\.com\/(\d+)(\/|\?|$)|^https:\/\/www\.bilibili\.com\/space\/(\d+)(\/|\?|$)|^https:\/\/m\.bilibili\.com\/space\/(\d+)(\/|\?|$)|^https:\/\/www\.bilibili\.tv\/[a-z]{2}\/space\/(\d+)(\/|\?|$)/

export const VIDEO_URL_PREFIX = "https://www.bilibili.com/video/" as const
export const LIVE_ROOM_URL_PREFIX = "https://live.bilibili.com/" as const
export const SPACE_URL_PREFIX = "https://space.bilibili.com/" as const
export const COLLECTION_URL_PREFIX = "/channel/collectiondetail?sid=" as const
export const SERIES_URL_PREFIX = "/channel/seriesdetail?sid=" as const
export const SEASON_URL_PREFIX = "https://www.bilibili.com/bangumi/play/ss" as const
export const EPISODE_URL_PREFIX = "https://www.bilibili.com/bangumi/play/ep" as const
export const COURSE_URL_PREFIX = "https://www.bilibili.com/cheese/play/ss" as const
export const COURSE_EPISODE_URL_PREFIX = "https://www.bilibili.com/cheese/play/ep" as const
export const FAVORITES_URL_PREFIX = "https://www.bilibili.com/medialist/detail/ml" as const
export const FESTIVAL_URL_PREFIX = "https://www.bilibili.com/festival/" as const
export const POST_URL_PREFIX = "https://t.bilibili.com/" as const
export const WATCH_LATER_URL = "https://www.bilibili.com/watchlater/#/list" as const
export const PREMIUM_CONTENT_MESSAGE = "本片是大会员专享内容" as const

export const USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0" as const
export const OS = "Linux x86_64" as const // others ["Windows", "MacIntel", "Android", "iOS", "Chromium OS", "Ubuntu", "Linux", "Fedora"]
export const WEBGL = "WebGL 1.0" as const
//TODO i think these are named backwards
export const WEBGL_VENDOR = "Intel(R) HD Graphics 400, or similar" as const
export const WEBGL_RENDERER = "Intel" as const
export const post_body_for_ExClimbWuzhi = JSON.stringify({
    payload: JSON.stringify({
        "39c8": "333.1368.fp.risk",
        "3c43": {
            "adca": OS,
            "6bc5": WEBGL_VENDOR,
            // PNG on the triangle rendered by the webgl fingerprint library
            "bfe9": "data:image/png;base64,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"
                .slice(-50),
            "b8ce": USER_AGENT
        },
    })
})

export const WATCH_LATER_ID = "WATCH_LATER"

declare const httpimp: HTTP | undefined

export const local_http = http
export const local_utility = utility
export const IMPERSONATION_TARGET = "chrome136" as const
export const IS_IMPERSONATION_AVAILABLE = typeof httpimp !== "undefined"

// TODO review hardcoded values
export const HARDCODED_THUMBNAIL_QUALITY = 1080 as const
export const EMPTY_AUTHOR = new PlatformAuthorLink(new PlatformID(PLATFORM, "", plugin.config.id), "", "")
export const MISSING_NAME = "" as const
export const HARDCODED_ZERO = 0 as const
export const MISSING_RATING = 0 as const
export const NAME_LOAD_FAILED = "Name Load Failed" as const

// set missing constants
Type.Order.Chronological = "Latest releases"
Type.Order.Views = "Most played"
Type.Order.Favorites = "Most favorited"

// align with the rest of the plugin use Simplified Chinese
Type.Order.Chronological = "最新发布"
Type.Order.Views = "最多播放"
Type.Order.Favorites = "最多收藏"
