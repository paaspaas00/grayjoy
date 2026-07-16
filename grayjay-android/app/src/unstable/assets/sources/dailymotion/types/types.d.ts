import { Video, Live } from "./CodeGenDailymotion";

type DailymotionStreamingContent = Video | Live | null;

interface IDailymotionPluginSettings {
  hideSensitiveContent: boolean;
  preferredCountryOptionIndex: number;
  avatarSizeOptionIndex: number;
  thumbnailResolutionOptionIndex: number;
  videosPerPageOptionIndex: number;
  playlistsPerPageOptionIndex: number;
}

interface IDailymotionSubtitle {
  data: Map<string, string, { urls: string[]; label: string }>;
  enable: boolean;
}

interface IPlatformSystemPlaylist {
  pluginId: string;
  httpClient: IHttp;
  query: string;
  operationName: string;
  rootObject: string;
  playlistName: string;
  usePlatformAuth: boolean;
  thumbnailResolutionIndex: number;
}

type AnonymousUserAuthorization = {
  anonymousUserAuthorizationToken?: string,
  anonymousUserAuthorizationTokenExpirationDate?: number,
  isValid: boolean
} 