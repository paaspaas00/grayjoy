import type { BiliBiliSource } from "./types.ts"
import { enable, disable, saveState } from "./enable.ts"
import { getHome } from "./home.ts"
import { searchSuggestions, search, getSearchCapabilities, searchPlaylists } from "./search.ts"
import { isContentDetailsUrl, getContentDetails, getContentRecommendations } from "./content.ts"
import { isChannelUrl, getChannel, searchChannels } from "./channel.ts"
import { getChannelContents, getChannelCapabilities, searchChannelContents, getSearchChannelContentsCapabilities, getChannelPlaylists } from "./channel_contents.ts"
import { isPlaylistUrl, getPlaylist } from "./playlists.ts"
import { getComments, getSubComments, getLiveChatWindow } from "./comments.ts"
import { getUserSubscriptions, getUserPlaylists } from "./user.ts"

const local_source: BiliBiliSource = {
    enable,
    disable,
    saveState,
    getHome,
    searchSuggestions,
    search,
    getSearchCapabilities,
    isContentDetailsUrl,
    getContentDetails,
    getContentRecommendations,
    isChannelUrl,
    getChannel,
    getChannelContents,
    getChannelCapabilities,
    searchChannelContents,
    getSearchChannelContentsCapabilities,
    getChannelPlaylists,
    searchChannels,
    getComments,
    getSubComments,
    isPlaylistUrl,
    getPlaylist,
    searchPlaylists,
    getLiveChatWindow,
    getUserPlaylists,
    getUserSubscriptions
}

init_source(local_source)

function init_source<
    T extends { readonly [key: string]: string },
    S extends string,
    ChannelTypes extends FeedType,
    SearchTypes extends FeedType,
    ChannelSearchTypes extends FeedType,
    SettingsType
>(local_source: Source<T, S, ChannelTypes, SearchTypes, ChannelSearchTypes, SettingsType>) {
    for (const method_key of Object.keys(local_source)) {
        // @ts-expect-error assign to readonly constant source object
        source[method_key] = local_source[method_key]
    }
}
