export type Maybe<T> = T | null;
export type InputMaybe<T> = Maybe<T>;
export type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
export type MakeOptional<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]?: Maybe<T[SubKey]> };
export type MakeMaybe<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]: Maybe<T[SubKey]> };
export type MakeEmpty<T extends { [key: string]: unknown }, K extends keyof T> = { [_ in K]?: never };
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
  ID: { input: string; output: string; }
  String: { input: string; output: string; }
  Boolean: { input: boolean; output: boolean; }
  Int: { input: number; output: number; }
  Float: { input: number; output: number; }
  Any: { input: any; output: any; }
  BigInt: { input: any; output: any; }
  Date: { input: any; output: any; }
  DateTime: { input: any; output: any; }
  Time: { input: any; output: any; }
};

/** The possible account values for a channel. */
export enum Account {
  /** A partner account. */
  Partner = 'PARTNER',
  /** A UGC account. */
  Ugc = 'UGC',
  /** A verified partner account. */
  VerifiedPartner = 'VERIFIED_PARTNER'
}

/** The available input fields of a story operator. */
export type AccountOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<Account>;
};

/** The possible account types for a channel. */
export enum AccountType {
  /** A partner account type. */
  Partner = 'PARTNER',
  /** A verified partner account type. */
  VerifiedPartner = 'VERIFIED_PARTNER'
}

/** The possible values for an ActionGesture. */
export enum ActionGesture {
  /** The app is opened. For example: a user arrives from Google search. */
  AppEnter = 'APP_ENTER',
  /** The next video is automatically played. */
  AutoNext = 'AUTO_NEXT',
  /** A click action is performed. */
  Click = 'CLICK'
}

/** The input fields to activate a user. */
export type ActivateUserInput = {
  /** The activation key received in the email. */
  activationKey: Scalars['String']['input'];
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The email of the user to activate. */
  email: Scalars['String']['input'];
};

/** The return fields from activating a user. */
export type ActivateUserPayload = {
  __typename?: 'ActivateUserPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The possible values for an Activity. */
export enum Activity {
  /** An activity that is `favorited`. */
  Favorited = 'FAVORITED',
  /** An activity that is `hearted`. */
  Hearted = 'HEARTED',
  /** An activity that is `liked`. */
  Liked = 'LIKED',
  /**
   * An activity that is `saved`.
   * @deprecated Use `bookmarks` with `filter: { bookmark: { eq: SAVE }}`.
   */
  Saved = 'SAVED',
  /** An activity that is `watched`. */
  Watched = 'WATCHED'
}

/** The notification settings on activities to receive. */
export type ActivityNotificationSettings = Node & {
  __typename?: 'ActivityNotificationSettings';
  /** Receive notifications when a creator you are following starts a live. */
  followingCreatorStartsLive?: Maybe<Scalars['Boolean']['output']>;
  /** Receive notifications when a creator you are following uploads a video. */
  followingCreatorUploadsVideo?: Maybe<Scalars['Boolean']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The notifications settings on activities to receive. */
export type ActivityNotificationSettingsInput = {
  /** Indicate whether to receive notifications when a creator you are following starts a live. */
  followingCreatorStartsLive?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to receive notifications when a creator you are following uploads a video. */
  followingCreatorUploadsVideo?: InputMaybe<Scalars['Boolean']['input']>;
};

/** The available input fields of an Activity operator. */
export type ActivityOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<Activity>;
};

/** The input fields to add a creator to the blocklist. */
export type AddBlockedInput = {
  /** The ID of the creator to add to the blocklist. */
  id: Scalars['ID']['input'];
};

/** The return fields from adding a creator to the blocklist. */
export type AddBlockedPayload = {
  __typename?: 'AddBlockedPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to add a boost. */
export type AddBoostInput = {
  /** The new boost event. */
  event: BoostEvent;
};

/** The return fields from adding a boost. */
export type AddBoostPayload = {
  __typename?: 'AddBoostPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to add a video to a collection. */
export type AddCollectionVideoInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the collection. */
  collectionXid: Scalars['String']['input'];
  /** The Dailymotion ID of the video. */
  videoXid: Scalars['String']['input'];
};

/** The return fields from adding a video to a collection. */
export type AddCollectionVideoPayload = {
  __typename?: 'AddCollectionVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to add a video to the `WatchLater` list of the connected user. */
export type AddWatchLaterVideoInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the video. */
  videoXid: Scalars['String']['input'];
};

/** The return fields from adding a video to the `WatchLater` list of the connected user. */
export type AddWatchLaterVideoPayload = {
  __typename?: 'AddWatchLaterVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to add a `Watched` to the watched list of the connected user. */
export type AddWatchedInput = {
  /** Indicates whether a `Watched` is completely watched. */
  completed?: InputMaybe<Scalars['Boolean']['input']>;
  /** The Dailymotion ID of the `Watched` to add. */
  id: Scalars['ID']['input'];
};

/** Represents an algorithm. */
export type Algorithm = {
  /** The version of the algorithm. */
  version?: Maybe<Scalars['String']['output']>;
};

/** The possible values for the name of algorithm. */
export enum AlgorithmName {
  /** An algorithm to discover content based on similar content viewed. */
  Discover = 'DISCOVER',
  /** An algorithm to explore content based on location. */
  Explore = 'EXPLORE',
  /** An algorithm that considers what content to feature. */
  Featured = 'FEATURED',
  /** An algorithm to discover content based on a hashtag. */
  Hashtag = 'HASHTAG',
  /** A personalized algorithm. */
  Personalized = 'PERSONALIZED',
  /** An algorithm that provides perspective. */
  Perspective = 'PERSPECTIVE',
  /** A sponsored algorithm. */
  Sponsored = 'SPONSORED'
}

/** The available input fields of a post operator. */
export type AlgorithmNameOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<AlgorithmName>;
};

/** Represents the various forms of analytics. */
export type Analytics = Node & {
  __typename?: 'Analytics';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Analytics for a key performance indicator metric. */
  kpi: AnalyticsFlatPayload;
  kpiForList: AnalyticsFlatPayload;
  /** The share URLs of the analytics. */
  shareUrls?: Maybe<AnalyticsShareUrls>;
  /** A selection of analytics aggregated over time. */
  timeSeries: AnalyticsPayload;
  /** A selection of top values of analytics aggregated over some dimensions. */
  topValues: AnalyticsPayload;
};


/** Represents the various forms of analytics. */
export type AnalyticsKpiArgs = {
  cumulative?: InputMaybe<Scalars['Boolean']['input']>;
  filter: AnalyticsFilter;
  metric: AnalyticsMetric;
  percentageChange?: InputMaybe<Scalars['Boolean']['input']>;
  timePeriod: AnalyticsTimePeriod;
};


/** Represents the various forms of analytics. */
export type AnalyticsKpiForListArgs = {
  filter: AnalyticsFilter;
  items: PayloadItemsInput;
  limit: Scalars['Int']['input'];
  metrics: Array<AnalyticsMetric>;
  timePeriod: AnalyticsTimePeriod;
};


/** Represents the various forms of analytics. */
export type AnalyticsTimeSeriesArgs = {
  cumulative?: InputMaybe<Scalars['Boolean']['input']>;
  dimension?: InputMaybe<Scalars['String']['input']>;
  filter: AnalyticsFilter;
  limit?: InputMaybe<Scalars['Int']['input']>;
  metrics: Array<AnalyticsMetric>;
  previousTimePeriod?: InputMaybe<Scalars['Boolean']['input']>;
  timePeriod: AnalyticsTimePeriod;
};


/** Represents the various forms of analytics. */
export type AnalyticsTopValuesArgs = {
  cumulative?: InputMaybe<Scalars['Boolean']['input']>;
  dimensions: Array<Scalars['String']['input']>;
  filter: AnalyticsFilter;
  limit: Scalars['Int']['input'];
  metrics: Array<AnalyticsMetric>;
  orderBy?: InputMaybe<AnalyticsOrderBy>;
  timePeriod: AnalyticsTimePeriod;
};

/** The input fields of an analytics filter. */
export type AnalyticsFilter = {
  /** Filter analytics by the name of the field. */
  field?: InputMaybe<Scalars['String']['input']>;
  /** Filter analytics by the logical operator. */
  operator: AnalyticsFilterOperator;
  /** Filter analytics by the value of the field. */
  value?: InputMaybe<Scalars['String']['input']>;
  /** Filter analtyics by a combination of OR/AND operators. */
  values?: InputMaybe<Array<AnalyticsFilter>>;
};

/** The possible filter operators for analytics. */
export enum AnalyticsFilterOperator {
  /** Identify values that meet all criteria for a set of filters. */
  And = 'AND',
  /** Identify values that meet at least one criteria for a set of filters. */
  Or = 'OR',
  /** Identify values equal to the value provided in a filter. */
  Selector = 'SELECTOR'
}

/** Analytics not grouped by a dimension. */
export type AnalyticsFlatPayload = AnalyticsPayload & {
  __typename?: 'AnalyticsFlatPayload';
  /** The names of the analytics fields. */
  fields: Array<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The values associated with each analytic field. */
  values: Array<Maybe<Array<Maybe<Scalars['Any']['output']>>>>;
};

/** Analytics grouped by a dimension. */
export type AnalyticsGroupedPayload = AnalyticsPayload & {
  __typename?: 'AnalyticsGroupedPayload';
  /** The names of the analytics fields. */
  fields: Array<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Analytics for each dimension value. */
  values: Array<AnalyticsGroupedPayloadItem>;
};

/** Analytics for a single dimension value. */
export type AnalyticsGroupedPayloadItem = Node & {
  __typename?: 'AnalyticsGroupedPayloadItem';
  /** The value of the selected dimension. */
  field: Scalars['String']['output'];
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The values associated with the given dimension value. */
  values: Array<Maybe<Array<Maybe<Scalars['Any']['output']>>>>;
};

/** The input fields of an analytics metric. */
export type AnalyticsMetric = {
  /** The field selection for the metric. */
  field: Scalars['String']['input'];
  /** The aggregate function for the metric. */
  function: AnalyticsMetricFunction;
};

/** The possible functions available to aggregate a metric. */
export enum AnalyticsMetricFunction {
  /** Calculates the average value of a field. */
  Avg = 'AVG',
  /** Calculates the maximum value of a field. */
  Max = 'MAX',
  /** Calculates the minimum value of a field. */
  Min = 'MIN',
  /** Calculates the total value of a field. */
  Sum = 'SUM'
}

/** The input fields of an analytics order by. */
export type AnalyticsOrderBy = {
  /** Order by ascending or descending direction. Defaults to desc. */
  direction?: InputMaybe<OrderDirection>;
  /** Order analytics by a given metric. */
  field: Scalars['String']['input'];
};

/** Represents a generic analytics payload. */
export type AnalyticsPayload = {
  /** The names of the analytic fields. */
  fields: Array<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** An analytics report of a channel or an organization. */
export type AnalyticsReport = Node & {
  __typename?: 'AnalyticsReport';
  /** The Dailymotion ID of the channel in the report. */
  channelXid?: Maybe<Scalars['String']['output']>;
  /** The date and time (ISO 8601 format) when the report was created. */
  createDate: Scalars['DateTime']['output'];
  /**
   * The creation date of the report.
   * @deprecated Use `createDate` field.
   */
  createdAt?: Maybe<Scalars['Date']['output']>;
  /** The Dailymotion user who created the report. */
  creator?: Maybe<User>;
  /** The download links of the report. */
  downloadLinks?: Maybe<ReportFileDownloadLinkConnection>;
  /** The end date of the data analyzed in the report. */
  endDate?: Maybe<Scalars['Date']['output']>;
  /** Indicates whether the report has revenue info. */
  hasRevenueInfo?: Maybe<Scalars['Boolean']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the report. */
  name?: Maybe<Scalars['String']['output']>;
  /** The Dailymotion ID of the organization in the report. */
  organizationXid?: Maybe<Scalars['String']['output']>;
  /** The token identifying the report. */
  reportToken?: Maybe<Scalars['String']['output']>;
  /** The start date of the data to be analyzed in the report. */
  startDate?: Maybe<Scalars['Date']['output']>;
  /** The status of the report. */
  status?: Maybe<AnalyticsReportStatus>;
};


/** An analytics report of a channel or an organization. */
export type AnalyticsReportDownloadLinksArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The connection type for an Analytics Report. */
export type AnalyticsReportConnection = {
  __typename?: 'AnalyticsReportConnection';
  /** A list of edges. */
  edges: Array<Maybe<AnalyticsReportEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** The input fields of creating an analytics report. */
export type AnalyticsReportCreateInput = {
  /** The Dailymotion ID of the channel for the report. */
  channelXid: Scalars['String']['input'];
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /**
   * The dimensions used to aggregate data.
   *   Each row in the report will be unique based on combination of these dimensions.
   */
  dimensions: Array<InputMaybe<PartnerReportDimension>>;
  /** The end date of the data to be analyzed for the report. */
  endDate: Scalars['Date']['input'];
  /** The filters to create the report. */
  filters?: InputMaybe<AnalyticsReportFilters>;
  /** The measurements to aggregate the data based on the selected dimensions. */
  metrics: Array<InputMaybe<PartnerReportMetric>>;
  /** The name of the report. */
  name: Scalars['String']['input'];
  /** Indicate whether or not to notify when a report has been created. */
  notify?: InputMaybe<Scalars['Boolean']['input']>;
  /** Order the result of the report. Defaults to desc. */
  orderBy?: InputMaybe<AnalyticsReportOrderBy>;
  /** The Dailymotion ID of the organization for the report. */
  organizationXid: Scalars['String']['input'];
  /** The Dailymotion product that the data is collected and attributed against. */
  product?: InputMaybe<PartnerReportProduct>;
  /** The start date of the data to be analyzed for the report. */
  startDate: Scalars['Date']['input'];
};

/** The return fields from creating a custom report. */
export type AnalyticsReportCreatePayload = {
  __typename?: 'AnalyticsReportCreatePayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The analytics report that is being created. */
  report?: Maybe<AnalyticsReport>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** An edge in a connection. */
export type AnalyticsReportEdge = {
  __typename?: 'AnalyticsReportEdge';
  /** The item at the end of the edge. */
  node?: Maybe<AnalyticsReport>;
};

/** The input fields of an analytics report filter. */
export type AnalyticsReportFilters = {
  /** Filter analytics reports by a channel slug. */
  channelSlug?: InputMaybe<Scalars['String']['input']>;
  /** Filter analytics reports by a media type. */
  mediaType?: InputMaybe<MediaType>;
  /** Filter analytics reports by a monetization type. */
  monetizationType?: InputMaybe<PartnerReportFilterMonetizationType>;
  /** Filter analytics reports by a video owner channel slug. */
  videoOwnerChannelSlug?: InputMaybe<Scalars['String']['input']>;
  /** Filter analytics reports by a visitor domain group. */
  visitorDomainGroup?: InputMaybe<Scalars['String']['input']>;
};

/** The input fields of an analytics report order by. */
export type AnalyticsReportOrderBy = {
  /** Order by ascending or descending direction. Defaults to desc. */
  direction?: InputMaybe<OrderDirection>;
  /** Order analytics report by a given metric. */
  field: PartnerReportMetric;
};

/** The possible values for the status of generating a report. */
export enum AnalyticsReportStatus {
  /** The report link is no longer available. */
  Expired = 'EXPIRED',
  /** The report generation has failed. */
  Failed = 'FAILED',
  /** The report generation is finished. */
  Finished = 'FINISHED',
  /** The report generation is in progress. */
  Processing = 'PROCESSING'
}

/** Information about the share urls of the Analytics. */
export type AnalyticsShareUrls = Node & ShareUrls & {
  __typename?: 'AnalyticsShareUrls';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The permalink share url of the analtyics. */
  permalink: Scalars['String']['output'];
};

/** The input fields of an analytics time period. */
export type AnalyticsTimePeriod = {
  /** The end time of the data to be selected. */
  endTime: Scalars['DateTime']['input'];
  /** The time frequency of the data to be selected. This is either an ISO 8601 duration or one of (`MINUTE`, `HOUR`, `DAY`, `MONTH`). */
  frequency: Scalars['String']['input'];
  /** The start time of the data to be selected. */
  startTime: Scalars['DateTime']['input'];
};

/** Information about an Android / AOSP app. */
export type Android = Node & {
  __typename?: 'Android';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The current minimum version. */
  minimum_version: Scalars['String']['output'];
};

/** The notification settings on announcements to receive. */
export type AnnouncementNotificationSettings = Node & {
  __typename?: 'AnnouncementNotificationSettings';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Receive notifications about product tips. */
  tips?: Maybe<Scalars['Boolean']['output']>;
  /** Receive notifications about product updates. */
  updates?: Maybe<Scalars['Boolean']['output']>;
};

/** The notifications settings on announcements to receive. */
export type AnnouncementNotificationSettingsInput = {
  /** Indicate whether to receive notifications about product tips. */
  tips?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to receive notifications about product updates. */
  updates?: InputMaybe<Scalars['Boolean']['input']>;
};

/**
 *
 * Informations used to submit an appeal
 *
 */
export type AppealApplication = {
  __typename?: 'AppealApplication';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Target moderation action for the appeal */
  moderationAction: ModerationAction;
  /** List of available appeal reasons related to the action */
  reasons: Array<AppealReason>;
};

/**
 *
 * The possible values for an appeal reason, based on requester's information
 *
 */
export enum AppealReason {
  /** Available if requester is content owner and related content is channel. */
  ReinstateAccount = 'REINSTATE_ACCOUNT',
  /** Available if requester is content owner and related content is media. */
  ReinstateContent = 'REINSTATE_CONTENT',
  /** Available if requester is an reporter and related content is media. */
  RemoveContent = 'REMOVE_CONTENT',
  /** Available if requester is content owner and related content is media. */
  RestoreMonetization = 'RESTORE_MONETIZATION',
  /** Available if requester is an reporter and related content is media. */
  RestrictContent = 'RESTRICT_CONTENT',
  /** Available if requester is content owner and related content is media. */
  UnrestrictContent = 'UNRESTRICT_CONTENT'
}

/** The input fields to ask a partner report file. */
export type AskPartnerReportFileInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /**
   * The dimensions used to aggregate data.
   *   Each row in the report will be unique based on combination of these dimensions.
   */
  dimensions: Array<InputMaybe<PartnerReportDimension>>;
  /** The end date of the data to be analyzed for the report. */
  endDate: Scalars['DateTime']['input'];
  /** The filters to create the report. */
  filters?: InputMaybe<PartnerReportFilters>;
  /** The measurements to aggregate the data based on the selected dimensions. */
  metrics: Array<InputMaybe<PartnerReportMetric>>;
  /** The Dailymotion product that the data is collected and attributed against. */
  product?: InputMaybe<PartnerReportProduct>;
  /** The start date of the data to be analyzed for the report. */
  startDate: Scalars['DateTime']['input'];
};

/** The return fields from asking a partner report file. */
export type AskPartnerReportFilePayload = {
  __typename?: 'AskPartnerReportFilePayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** Custom report in progress of generation. */
  reportFile?: Maybe<PartnerReportFile>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Information about an attribute of a metadata. */
export type Attribute = Node & {
  __typename?: 'Attribute';
  /** The content of the attribute. */
  content?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the attribute. */
  name?: Maybe<Scalars['String']['output']>;
};

/** The connection type for Attribute. */
export type AttributeConnection = {
  __typename?: 'AttributeConnection';
  /** A list of edges. */
  edges: Array<Maybe<AttributeEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type AttributeEdge = {
  __typename?: 'AttributeEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Attribute>;
};

/** The possible values for an Audience Guide. */
export enum AudienceGuide {
  /** Created for the geneal public (all ages). */
  General = 'GENERAL',
  /** Created for Kids (targets an audience of age 16 and under). */
  Kids = 'KIDS',
  /** Contains adult material (targets an audience that is 17 and older). */
  Restricted = 'RESTRICTED'
}

/** The available input fields of an audience guide operator. */
export type AudienceGuideOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<AudienceGuide>;
  /** Short for not equal, must be different from the given data. */
  ne?: InputMaybe<AudienceGuide>;
};

/** Represents the audiovisual work type of the copyrighted content. */
export enum AudiovisualWork {
  /** Represents a live broadcast. */
  Livestream = 'LIVESTREAM',
  /** Represents a movie. */
  Movie = 'MOVIE',
  /** Represents an online video. */
  OnlineVideo = 'ONLINE_VIDEO',
  /** A sports event. */
  SportsEvent = 'SPORTS_EVENT',
  /** Represents a tv show (or series). */
  TvShow = 'TV_SHOW'
}

/**
 *
 * The input fields to authorize a device.
 *
 */
export type AuthorizeDeviceInput = {
  /**
   *
   *   The user's authorization consent for the device.
   *
   */
  consent?: InputMaybe<DeviceAuthorizationConsent>;
  /**
   *
   *   The 6-digit user code for device authorization.
   *
   */
  user_code: Scalars['String']['input'];
};

/**
 *
 * The return fields from authorizing a device.
 *
 */
export type AuthorizeDevicePayload = {
  __typename?: 'AuthorizeDevicePayload';
  /**
   *
   *   The consent status of the device authorization.
   *
   */
  consent?: Maybe<DeviceAuthorizationConsent>;
  /**
   *
   *   The status of the mutation.
   *
   */
  status?: Maybe<Status>;
};

/** The available input fields of an AutoSuggestion filter. */
export type AutoSuggestionFilter = {
  /** Filter suggestions by story type, allowed values = `VIDEO`. */
  story?: InputMaybe<StoryOperator>;
};

/** The available height sizes for an Avatar. */
export enum AvatarHeight {
  /** A square image of 25px. */
  Square_25 = 'SQUARE_25',
  /** A square image of 60px. */
  Square_60 = 'SQUARE_60',
  /** A square image of 80px. */
  Square_80 = 'SQUARE_80',
  /** A square image of 120px. */
  Square_120 = 'SQUARE_120',
  /** A square image of 190px. */
  Square_190 = 'SQUARE_190',
  /** A square image of 240px. */
  Square_240 = 'SQUARE_240',
  /** A square image of 360px. */
  Square_360 = 'SQUARE_360',
  /** A square image of 480px. */
  Square_480 = 'SQUARE_480',
  /** A square image of 720px. */
  Square_720 = 'SQUARE_720'
}

/** The available height for an Banner. */
export enum BannerHeight {
  /** A portrait image with 100px */
  Portrait_100 = 'PORTRAIT_100',
  /** A portrait image with 150px */
  Portrait_150 = 'PORTRAIT_150',
  /** A portrait image with 200px */
  Portrait_200 = 'PORTRAIT_200',
  /** A portrait image with 210px */
  Portrait_210 = 'PORTRAIT_210',
  /** A portrait image with 250px */
  Portrait_250 = 'PORTRAIT_250',
  /** A portrait image with 375px */
  Portrait_375 = 'PORTRAIT_375'
}

/** The available width for an Banner. */
export enum BannerWidth {
  /** A landscape image with 375px */
  Landscape_375 = 'LANDSCAPE_375',
  /** A landscape image with 720px */
  Landscape_720 = 'LANDSCAPE_720',
  /** A landscape image with 1024px */
  Landscape_1024 = 'LANDSCAPE_1024',
  /** A landscape image with 1920px */
  Landscape_1920 = 'LANDSCAPE_1920'
}

/** Represents the interface to Flipper. */
export type Behavior = Node & {
  __typename?: 'Behavior';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The experiments (A/B testing) that are matched/enabled for the connected client. */
  matchedExperiments?: Maybe<ExperimentMatchConnection>;
  /** The features that are matched/enabled for the connected client. */
  matchedFeatures?: Maybe<FeatureMatchConnection>;
  /** The available rules. */
  rules?: Maybe<RuleConnection>;
};


/** Represents the interface to Flipper. */
export type BehaviorMatchedExperimentsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


/** Represents the interface to Flipper. */
export type BehaviorMatchedFeaturesArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


/** Represents the interface to Flipper. */
export type BehaviorRulesArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  names?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  page?: InputMaybe<Scalars['Int']['input']>;
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};

/** Represents a tag added to a media. */
export type BehaviorRuleTag = Node & {
  __typename?: 'BehaviorRuleTag';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The label of the tag. */
  label?: Maybe<Scalars['String']['output']>;
};

/** The connection type for BehaviorRuleTag. */
export type BehaviorRuleTagConnection = {
  __typename?: 'BehaviorRuleTagConnection';
  /** A list of edges. */
  edges: Array<Maybe<BehaviorRuleTagEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type BehaviorRuleTagEdge = {
  __typename?: 'BehaviorRuleTagEdge';
  /** The item at the end of the edge. */
  node?: Maybe<BehaviorRuleTag>;
};

/** Represents information about a blocked. */
export type Blocked = Node & {
  __typename?: 'Blocked';
  /** The creator that is blocked. */
  creator?: Maybe<Channel>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The connection type for a Blocked. */
export type BlockedConnection = {
  __typename?: 'BlockedConnection';
  /** A list of edges. */
  edges: Array<Maybe<BlockedEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type BlockedEdge = {
  __typename?: 'BlockedEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Blocked>;
};

/** The available input fields for filtering blocked items. */
export type BlockedFilter = {
  /** Filter blocked items by the chatroom id. */
  chatroom?: InputMaybe<IdOperator>;
  /** Filter blocked items by the creator id. */
  creator?: InputMaybe<IdOperator>;
};

/** Represents a Bookmark. */
export type Bookmark = {
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The post bookmarked by the channel. */
  post: Post;
  /**
   * Indicates the like rating of the post bookmarked from the channel.
   * @deprecated Use an inline fragment of `Like`.
   */
  rating?: Maybe<LikeRating>;
};

/** The connection type for Bookmark. */
export type BookmarkConnection = {
  __typename?: 'BookmarkConnection';
  /** A list of edges. */
  edges: Array<Maybe<BookmarkEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type BookmarkEdge = {
  __typename?: 'BookmarkEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Bookmark>;
};

/** The available input fields of a Bookmark filter. */
export type BookmarkFilter = {
  /** Filter bookmarks by bookmark. */
  bookmark: BookmarkOperator;
  /** Filter bookmarks by post. */
  post?: InputMaybe<PostOperator>;
};

/** The node at the end of a BookmarksMetricEdge. */
export type BookmarkMetric = Metric & Node & {
  __typename?: 'BookmarkMetric';
  /** The bookmark metric being measured. */
  bookmark: BookmarkTypename;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the bookmark metric. A null value indicates that it is hidden or not available. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a BookmarkMetric. */
export type BookmarkMetricConnection = {
  __typename?: 'BookmarkMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<BookmarkMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type BookmarkMetricEdge = {
  __typename?: 'BookmarkMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<BookmarkMetric>;
};

/** The available input fields of a bookmark operator. */
export type BookmarkOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<BookmarkTypename>;
};

/** The available typenames for a Bookmark. */
export enum BookmarkTypename {
  /** A bookmark that represents a `favorite`. */
  Favorite = 'FAVORITE',
  /** A bookmark that represents a `like`. */
  Like = 'LIKE',
  /** A bookmark that represents a `save`. */
  Save = 'SAVE'
}

/** The available input fields of for a Boolean operator. */
export type BooleanOperator = {
  /** Short for equal, must match the given data exactly. */
  eq: Scalars['Boolean']['input'];
};

/** Represents the boost information of the channel (aka creator). */
export type Boost = Node & {
  __typename?: 'Boost';
  /** The current amount of boosts available to use. */
  balance?: Maybe<Scalars['Int']['output']>;
  /** The unique identifier for the boost. */
  id: Scalars['ID']['output'];
  /** The next available date and time (ISO 8601 format) the channel is able to collect boost events. */
  nextAvailable?: Maybe<BoostNextAvailable>;
  /** The boost transactions. */
  transactions?: Maybe<BoostTransactionConnection>;
};


/** Represents the boost information of the channel (aka creator). */
export type BoostTransactionsArgs = {
  filter?: InputMaybe<BoostTransactionFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The possible values for a BoostEvent. */
export enum BoostEvent {
  /** An adjustment was made to the boost balance for administrative reasons. */
  Adjustment = 'ADJUSTMENT',
  /** A boost event triggered when a user boosts a story. */
  BoostStory = 'BOOST_STORY',
  /** A boost event triggered daily. */
  DailyStreak = 'DAILY_STREAK',
  /** A boost event triggered at signup. */
  WelcomeBoost = 'WELCOME_BOOST'
}

/** The available input fields of a BoostTransaction operator. */
export type BoostEventOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<BoostEvent>;
};

/** Represents the next available date and time to collect boost events. */
export type BoostNextAvailable = {
  __typename?: 'BoostNextAvailable';
  /** The daily-streak event. */
  dailyStreak?: Maybe<Scalars['DateTime']['output']>;
};

/** Represents a BoostTransaction. */
export type BoostTransaction = Node & {
  __typename?: 'BoostTransaction';
  /** The amount of the transaction. */
  amount?: Maybe<Scalars['Int']['output']>;
  /** The date and time (ISO 8601 format) of the boost transaction. */
  createDate?: Maybe<Scalars['DateTime']['output']>;
  /** The event of the transaction. */
  event?: Maybe<BoostEvent>;
  /** The unique identifier for the boost transaction. */
  id: Scalars['ID']['output'];
};

/** The connection type for a BoostTransaction. */
export type BoostTransactionConnection = {
  __typename?: 'BoostTransactionConnection';
  /** A list of edges. */
  edges: Array<Maybe<BoostTransactionEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type BoostTransactionEdge = {
  __typename?: 'BoostTransactionEdge';
  /** The item at the end of the edge. */
  node?: Maybe<BoostTransaction>;
};

/** The available input fields of a BoostTransaction filter. */
export type BoostTransactionFilter = {
  /** Filter by boost event. */
  event: BoostEventOperator;
};

/** Information about a user that has boosted the requested user */
export type Booster = Node & {
  __typename?: 'Booster';
  /** The Channel information about the booster. */
  creator?: Maybe<Channel>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The connection type for Booster. */
export type BoosterConnection = {
  __typename?: 'BoosterConnection';
  /** A list of edges. */
  edges: Array<Maybe<BoosterEdge>>;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type BoosterEdge = {
  __typename?: 'BoosterEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Booster>;
};

/** The possible sort options for boosters. */
export type BoosterSort = {
  /** Sort by when the booster boosted the channel last. */
  createDate?: InputMaybe<OrderDirection>;
};

/** Represents a caption in a transcript. */
export type Caption = Node & {
  __typename?: 'Caption';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The text of the caption. */
  text: Scalars['String']['output'];
  /** The timecode of the caption. */
  timecode: Scalars['String']['output'];
};

/** The connection type for a Caption. */
export type CaptionConnection = {
  __typename?: 'CaptionConnection';
  /** A list of edges. */
  edges: Array<Maybe<CaptionEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type CaptionEdge = {
  __typename?: 'CaptionEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Caption>;
};

/** Information about a category. */
export type Category = {
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the category. */
  name?: Maybe<Scalars['String']['output']>;
  /** The human-readable unique ID of the category. */
  slug: Scalars['String']['output'];
};

/** The connection type for Category. */
export type CategoryConnection = {
  __typename?: 'CategoryConnection';
  /** A list of edges. */
  edges: Array<Maybe<CategoryEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type CategoryEdge = {
  __typename?: 'CategoryEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Category>;
};

/** The available input fields of a Category filter. */
export type CategoryFilter = {
  /** Filter by category. */
  category: CategoryOperator;
  /** Filter by the Dailymotion match rating of the `story` to the category. */
  percentage?: InputMaybe<IntOperator>;
};

/** The available input fields of a Category operator. */
export type CategoryOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<CategoryTypename>;
};

/** The available types of categories. */
export enum CategoryTypename {
  /** A content category. */
  ContentCategory = 'CONTENT_CATEGORY',
  /** A curated category. */
  CuratedCategory = 'CURATED_CATEGORY',
  /** An iab category. */
  IabCategory = 'IAB_CATEGORY',
  /**
   * An interest category.
   * @deprecated No longer supported.
   */
  InterestCategory = 'INTEREST_CATEGORY'
}

/** The input fields to request an email change. */
export type ChangeEmailInput = {
  /** The new email for the connected user. */
  email: Scalars['String']['input'];
  /** The password of the connected user. */
  password: Scalars['String']['input'];
};

/** The return fields from requesting an email change. */
export type ChangeEmailPayload = {
  __typename?: 'ChangeEmailPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** A channel manages medias and collections. */
export type Channel = Node & {
  __typename?: 'Channel';
  /** The account of the channel. */
  account?: Maybe<Account>;
  /**
   * The account type of the channel. Its value is one of the following: verified-partner, partner or viewer.
   * @deprecated Use `account`.
   */
  accountType?: Maybe<Scalars['String']['output']>;
  /** The required updates the channel must perform. */
  alerts?: Maybe<ChannelAlertConnection>;
  /** The URL of the avatar image. */
  avatar?: Maybe<Image>;
  /** The URL of the banner image. */
  banner?: Maybe<Image>;
  /** The list of creators blocked by the channel. */
  blocked?: Maybe<BlockedConnection>;
  /**
   * The bookmarked posts of the channel.
   * @deprecated Use `channel.history`.
   */
  bookmarks?: Maybe<BookmarkConnection>;
  /** The boost information of the channel. */
  boost?: Maybe<Boost>;
  /** The boosters that have boosted the channel. */
  boosters?: Maybe<BoosterConnection>;
  /** Indicates whether the channel name can be changed. */
  canChangeName?: Maybe<Scalars['Boolean']['output']>;
  /** The collections of the channel. */
  collections?: Maybe<CollectionConnection>;
  /** The comments created by the channel. */
  comments?: Maybe<CommentConnection>;
  /** The country of the channel. */
  country?: Maybe<Country>;
  /**
   * The URL of the cover image.
   * @deprecated Use `banner` field.
   */
  coverURL?: Maybe<Scalars['String']['output']>;
  /** The date and time (ISO 8601 format) when the channel was created. */
  createDate?: Maybe<Scalars['DateTime']['output']>;
  /** The description of the channel. */
  description?: Maybe<Scalars['String']['output']>;
  /** The display name of the channel. */
  displayName?: Maybe<Scalars['String']['output']>;
  /**
   * The external links of the channel.
   * @deprecated Use `socialUrls` field.
   */
  externalLinks?: Maybe<ChannelExternalLinks>;
  /** The follower engagement information of the channel. */
  followerEngagement?: Maybe<FollowerEngagement>;
  /** The users that are following the channel. */
  followers?: Maybe<FollowerConnection>;
  /** The users the channel is following. */
  followings?: Maybe<FollowingConnection>;
  /** The history of the posts interacted by the channel. */
  history?: Maybe<HistoryConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the channel is associated to an artist. */
  isArtist?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the channel is available.
   * @deprecated Use `user.channel`.
   */
  isAvailable?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the channel is followed by the user connected. Returns `False` if no user is connected.
   * @deprecated Use `channel.followerEngagement` field.
   */
  isFollowed?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the channel's notification is enabled the user connected. Returns `False` if no user is connected.
   * @deprecated Use `followerEngagement.notifications` field.
   */
  isNotificationEnabled?: Maybe<Scalars['Boolean']['output']>;
  /** The language of the channel. */
  language?: Maybe<Language>;
  /** The lives of the channel. */
  lives?: Maybe<LiveConnection>;
  /** The date and time (ISO 8601 format) when the channel last logged in. */
  loginDate?: Maybe<Scalars['DateTime']['output']>;
  /**
   * The URL of the logo image.
   * @deprecated Use `avatar` field.
   */
  logoURL?: Maybe<Scalars['String']['output']>;
  /**
   * The medias of the channel.
   * @deprecated Use `videos` or `lives` respectively.
   */
  medias?: Maybe<MediaConnection>;
  /**
   * The metabase iframe url of the non-verified channel.
   * @deprecated No longer supported.
   */
  metabaseIframeURL?: Maybe<Scalars['String']['output']>;
  /** The metrics of the channel. */
  metrics?: Maybe<ChannelMetrics>;
  /**
   * The username of the channel.
   * @deprecated Use `username`.
   */
  name?: Maybe<Scalars['String']['output']>;
  /** The network channels of the channel. */
  networkChannels?: Maybe<ChannelConnection>;
  /** The organization the channel belongs to. */
  organization?: Maybe<Organization>;
  /** The reactions created by the channel. */
  reactions?: Maybe<ReactionConnection>;
  /** The settings of the channel. */
  settings?: Maybe<ChannelSettings>;
  /** The share urls of the channel. */
  shareUrls?: Maybe<ChannelShareUrls>;
  /** The social urls of the channel. */
  socialUrls?: Maybe<SocialUrls>;
  /**
   * The stats of the channel.
   * @deprecated Use `metrics` field.
   */
  stats?: Maybe<ChannelStats>;
  /** The tagline of the channel. */
  tagline?: Maybe<Scalars['String']['output']>;
  /**
   * The thumbnails associated to the channel.
   * @deprecated Use `logolURL` field.
   */
  thumbnails?: Maybe<Thumbnails>;
  /** The username of the channel. */
  username?: Maybe<Scalars['String']['output']>;
  /** The videos of the channel. */
  videos?: Maybe<VideoConnection>;
  /**
   * The total number of views of the channel.
   * @deprecated Use `stats.views.total` field.
   */
  viewCount?: Maybe<Scalars['BigInt']['output']>;
  /** The viewer engagement information of the channel. */
  viewerEngagement: ChannelViewerEngagement;
  /** The Dailymotion ID of the channel. */
  xid: Scalars['String']['output'];
};


/** A channel manages medias and collections. */
export type ChannelAvatarArgs = {
  height: AvatarHeight;
};


/** A channel manages medias and collections. */
export type ChannelBannerArgs = {
  height?: InputMaybe<BannerHeight>;
  width?: InputMaybe<BannerWidth>;
};


/** A channel manages medias and collections. */
export type ChannelBlockedArgs = {
  filter: BlockedFilter;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A channel manages medias and collections. */
export type ChannelBookmarksArgs = {
  filter?: InputMaybe<BookmarkFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A channel manages medias and collections. */
export type ChannelBoostersArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  orderBy?: InputMaybe<BoosterSort>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A channel manages medias and collections. */
export type ChannelCollectionsArgs = {
  createdAfter?: InputMaybe<Scalars['Date']['input']>;
  createdBefore?: InputMaybe<Scalars['Date']['input']>;
  filter?: InputMaybe<CollectionFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  hasPublicVideos?: InputMaybe<Scalars['Boolean']['input']>;
  isPrivate?: InputMaybe<Scalars['Boolean']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  search?: InputMaybe<Scalars['String']['input']>;
  sort?: InputMaybe<Scalars['String']['input']>;
};


/** A channel manages medias and collections. */
export type ChannelCommentsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  orderBy?: InputMaybe<CommentSort>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A channel manages medias and collections. */
export type ChannelCoverUrlArgs = {
  size: Scalars['String']['input'];
};


/** A channel manages medias and collections. */
export type ChannelFollowersArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A channel manages medias and collections. */
export type ChannelFollowingsArgs = {
  filter?: InputMaybe<FollowingFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A channel manages medias and collections. */
export type ChannelHistoryArgs = {
  filter: HistoryFilter;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A channel manages medias and collections. */
export type ChannelLivesArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  filter?: InputMaybe<LiveFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  isOnAir?: InputMaybe<Scalars['Boolean']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  startIn?: InputMaybe<Scalars['Int']['input']>;
};


/** A channel manages medias and collections. */
export type ChannelLogoUrlArgs = {
  size: Scalars['String']['input'];
};


/** A channel manages medias and collections. */
export type ChannelMediasArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  isFeatured?: InputMaybe<Scalars['Boolean']['input']>;
  isOnAir?: InputMaybe<Scalars['Boolean']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<ChannelMediasSort>;
  startIn?: InputMaybe<Scalars['Int']['input']>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  topicXids?: InputMaybe<Array<Scalars['String']['input']>>;
  types?: InputMaybe<Array<InputMaybe<MediaType>>>;
};


/** A channel manages medias and collections. */
export type ChannelMetabaseIframeUrlArgs = {
  dashboardId: Scalars['Int']['input'];
};


/** A channel manages medias and collections. */
export type ChannelNetworkChannelsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  hasPublicVideos?: InputMaybe<Scalars['Boolean']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<NetworkChannelsSort>;
};


/** A channel manages medias and collections. */
export type ChannelReactionsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A channel manages medias and collections. */
export type ChannelVideosArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  filter?: InputMaybe<VideoFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  isFeatured?: InputMaybe<Scalars['Boolean']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<Scalars['String']['input']>;
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  topicXids?: InputMaybe<Array<Scalars['String']['input']>>;
};

/** Represents a Channel Alert. */
export type ChannelAlert = Node & {
  __typename?: 'ChannelAlert';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the channel alert. */
  name?: Maybe<ChannelAlertName>;
};

/** The connection type for ChannelAlert. */
export type ChannelAlertConnection = {
  __typename?: 'ChannelAlertConnection';
  /** A list of edges. */
  edges: Array<Maybe<ChannelAlertEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type ChannelAlertEdge = {
  __typename?: 'ChannelAlertEdge';
  /** The item at the end of the edge. */
  node?: Maybe<ChannelAlert>;
};

/** The possible names of an alert for a channel. */
export enum ChannelAlertName {
  /** Requires the creator to update its name (aka @username). */
  CreatorNameUpdate = 'CREATOR_NAME_UPDATE'
}

/** The connection type for Channel. */
export type ChannelConnection = {
  __typename?: 'ChannelConnection';
  /** A list of edges. */
  edges: Array<Maybe<ChannelEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** The input fields to create a channel. */
export type ChannelCreateInput = {
  /** @deprecated(reason: "Use `avatarUrl` input field.") - The URL of the avatar of the channel. */
  avatarURL?: InputMaybe<Scalars['String']['input']>;
  /** The url of the avatar of the channel. */
  avatarUrl?: InputMaybe<Scalars['String']['input']>;
  /** The URL of the banner image of the channel. */
  bannerURL?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The country of the channel. */
  country: Scalars['String']['input'];
  /** The URL of the cover image of the channel. */
  coverURL?: InputMaybe<Scalars['String']['input']>;
  /** The display name of the channel. */
  displayName: Scalars['String']['input'];
  /** The language of the channel. */
  language: Scalars['String']['input'];
  /** The URL of the logo image of the channel. */
  logoURL?: InputMaybe<Scalars['String']['input']>;
  /** The username of the channel. */
  name?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the organization creating the channel. */
  organizationXid: Scalars['String']['input'];
  /** The settings on a channel. */
  settings?: InputMaybe<ChannelSettingsInput>;
  /** The username of the channel. */
  username?: InputMaybe<Scalars['String']['input']>;
};

/** The return fields from creating a channel. */
export type ChannelCreatePayload = {
  __typename?: 'ChannelCreatePayload';
  /** The new channel. */
  channel?: Maybe<Channel>;
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** An edge in a connection. */
export type ChannelEdge = {
  __typename?: 'ChannelEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Channel>;
  /** Information about the permission for the user connected to the channel. */
  permission?: Maybe<ChannelPermission>;
};

/** The engagement metrics of a Channel. */
export type ChannelEngagementMetrics = Node & {
  __typename?: 'ChannelEngagementMetrics';
  /**
   * The bookmark metrics of the channel.
   * @deprecated Use `metrics.engagement.history`.
   */
  bookmarks?: Maybe<BookmarkMetricConnection>;
  /** The collection metrics of the channel. */
  collections?: Maybe<CollectionMetricConnection>;
  /** The follower metrics of the channel. */
  followers?: Maybe<FollowerMetricConnection>;
  /** The following metrics of the channel. */
  followings?: Maybe<FollowingMetricConnection>;
  /** The history metrics of the channel. */
  history?: Maybe<PostMetricConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The live metrics of the channel. */
  lives?: Maybe<LiveMetricConnection>;
  /** The reaction metrics of the channel. */
  reactions?: Maybe<ReactionMetricConnection>;
  /** The video metrics of the channel. */
  videos?: Maybe<VideoMetricConnection>;
};


/** The engagement metrics of a Channel. */
export type ChannelEngagementMetricsBookmarksArgs = {
  filter?: InputMaybe<BookmarkFilter>;
};


/** The engagement metrics of a Channel. */
export type ChannelEngagementMetricsCollectionsArgs = {
  filter?: InputMaybe<CollectionFilter>;
};


/** The engagement metrics of a Channel. */
export type ChannelEngagementMetricsFollowingsArgs = {
  filter?: InputMaybe<FollowingFilter>;
};


/** The engagement metrics of a Channel. */
export type ChannelEngagementMetricsHistoryArgs = {
  filter: HistoryFilter;
};


/** The engagement metrics of a Channel. */
export type ChannelEngagementMetricsLivesArgs = {
  filter?: InputMaybe<LiveFilter>;
};


/** The engagement metrics of a Channel. */
export type ChannelEngagementMetricsVideosArgs = {
  filter?: InputMaybe<VideoFilter>;
};

/** The external links of the channel. */
export type ChannelExternalLinks = Node & {
  __typename?: 'ChannelExternalLinks';
  /** The Facebook profile URL of the channel. */
  facebookURL?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The Instagram profile URL of the channel. */
  instagramURL?: Maybe<Scalars['String']['output']>;
  /** The Pinterest profile URL of the channel. */
  pinterestURL?: Maybe<Scalars['String']['output']>;
  /** The Twitter profile URL of the channel. */
  twitterURL?: Maybe<Scalars['String']['output']>;
  /** The website URL of the channel. */
  websiteURL?: Maybe<Scalars['String']['output']>;
};

/** The input fields to update the external links of a channel. */
export type ChannelExternalLinksInput = {
  /** The Facebook profile URL of the channel. */
  facebookURL?: InputMaybe<Scalars['String']['input']>;
  /** The Instagram profile URL of the channel. */
  instagramURL?: InputMaybe<Scalars['String']['input']>;
  /** The Pinterest profile URL of the channel. */
  pinterestURL?: InputMaybe<Scalars['String']['input']>;
  /** The Twitter profile URL of the channel. */
  twitterURL?: InputMaybe<Scalars['String']['input']>;
  /** The website URL of the channel. */
  websiteURL?: InputMaybe<Scalars['String']['input']>;
};

/** The possible values which channel media connections can be sorted by. */
export enum ChannelMediasSort {
  /** Sort channel medias by most recent. */
  Recent = 'RECENT',
  /** Sort channel medias by most viewed. */
  Visited = 'VISITED'
}

/** The node at the end of a ChannelMetricEdge. */
export type ChannelMetric = Metric & Node & {
  __typename?: 'ChannelMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the channel metric. A null value indicates that it is hidden or not available. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a ChannelMetric. */
export type ChannelMetricConnection = {
  __typename?: 'ChannelMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<ChannelMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type ChannelMetricEdge = {
  __typename?: 'ChannelMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<ChannelMetric>;
};

/** The metrics of a Channel. */
export type ChannelMetrics = Node & {
  __typename?: 'ChannelMetrics';
  /** The engagement metrics of a channel. */
  engagement?: Maybe<ChannelEngagementMetrics>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The view metrics of a channel. */
  views?: Maybe<ChannelViewMetrics>;
};

/** Information about the permission of the connected user to the channel. */
export type ChannelPermission = {
  __typename?: 'ChannelPermission';
  /** The permission level of connected user to the channel. */
  level?: Maybe<ChannelPermissionLevel>;
};

/** The possible permissions for a user connected to a channel in an organization. */
export enum ChannelPermissionLevel {
  /** The user is owner of the channel. */
  Owner = 'OWNER',
  /** The user is a reader of the channel. */
  Reader = 'READER'
}

/** Information about the settings of a Channel. */
export type ChannelSettings = Node & {
  __typename?: 'ChannelSettings';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The notifications settings of the Channel. */
  notifications?: Maybe<NotificationSettings>;
  /** Indicates whether the Channel has been banned to create a thread (comment or reaction). */
  threadsBanned: Scalars['Boolean']['output'];
  /** Indicates the default settings of the Channel when uploading a video. */
  video?: Maybe<VideoSettings>;
};

/** The settings on a Channel. */
export type ChannelSettingsInput = {
  /** The notification settings of the Channel. */
  notifications?: InputMaybe<NotificationSettingsInput>;
  /** The default settings when creating a video. */
  video?: InputMaybe<VideoSettingsInput>;
};

/** The return fields from updating the settings of the connected Channel. */
export type ChannelSettingsPayload = {
  __typename?: 'ChannelSettingsPayload';
  /** The updated settings. */
  settings?: Maybe<ChannelSettings>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Information about the share urls of a Channel. */
export type ChannelShareUrls = Node & ShareUrls & {
  __typename?: 'ChannelShareUrls';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The permalink share url of the channel. */
  permalink: Scalars['String']['output'];
};

/** Information about the stats of a channel. */
export type ChannelStats = Node & {
  __typename?: 'ChannelStats';
  /**
   * The follower stats of the channel.
   * @deprecated Use `channel.metrics.engagement.followers`.
   */
  followers?: Maybe<ChannelStatsFollowers>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The reaction stats of the channel.
   * @deprecated Use `channel.metrics.engagement.reactions`.
   */
  reactions?: Maybe<ChannelStatsReactions>;
  /**
   * The video stats of the channel.
   * @deprecated Use `channel.metrics.engagement.videos`.
   */
  videos?: Maybe<ChannelStatsVideos>;
  /**
   * The view stats of the channel.
   * @deprecated Use `channel.metrics.views.visits`.
   */
  views?: Maybe<ChannelStatsViews>;
};

/** The follower stats of the channel. */
export type ChannelStatsFollowers = Node & {
  __typename?: 'ChannelStatsFollowers';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of followers of the channel. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The reaction stats of the channel. */
export type ChannelStatsReactions = Node & {
  __typename?: 'ChannelStatsReactions';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of reactions of the channel. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The video stats of the channel. */
export type ChannelStatsVideos = Node & {
  __typename?: 'ChannelStatsVideos';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of videos of the channel. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The view stats of the channel. */
export type ChannelStatsViews = Node & {
  __typename?: 'ChannelStatsViews';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of views of the channel. */
  total?: Maybe<Scalars['BigInt']['output']>;
};

/** The views metrics of a Channel. */
export type ChannelViewMetrics = Node & {
  __typename?: 'ChannelViewMetrics';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The visitor metrics of the channel. */
  visits?: Maybe<ChannelMetricConnection>;
};

/** Information about the viewer engagement of a Channel. */
export type ChannelViewerEngagement = Node & {
  __typename?: 'ChannelViewerEngagement';
  /** Indicates whether the viewer appears on the blocklist of the channel. */
  blocked: Scalars['Boolean']['output'];
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The possible sort options for channels. */
export enum ChannelsSort {
  /** Sort by popular. */
  Popular = 'POPULAR',
  /** Sort by recent. */
  Recent = 'RECENT'
}

/** Represents a chapter in a video. */
export type Chapter = Node & {
  __typename?: 'Chapter';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The start timecode of the chapter. */
  timecode: Scalars['String']['output'];
  /** The title of the chapter. */
  title: Scalars['String']['output'];
};

/** The connection type for a Chapter. */
export type ChapterConnection = {
  __typename?: 'ChapterConnection';
  /** A list of edges. */
  edges: Array<Maybe<ChapterEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type ChapterEdge = {
  __typename?: 'ChapterEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Chapter>;
};

/** Information about a comment. */
export type Chatroom = Node & {
  __typename?: 'Chatroom';
  /** The creation date (DateTime ISO8601) of the chatroom. */
  createDate: Scalars['DateTime']['output'];
  /** The global ID of the object. */
  id: Scalars['ID']['output'];
  /** The metrics of the chatroom. */
  metrics?: Maybe<ChatroomMetrics>;
  /** The ID of the chatroom. */
  roomId: Scalars['String']['output'];
  /** The status of the chatroom. */
  status: ChatroomStatus;
  /** The viewer engagement information of the chatroom. */
  viewerEngagement: ChatroomViewerEngagement;
};

/** The engagement metrics of a Chatroom. */
export type ChatroomEngagementMetrics = Node & {
  __typename?: 'ChatroomEngagementMetrics';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The member metrics of the chatroom. */
  members?: Maybe<ChatroomMetricConnection>;
  /** The message metrics of the chatroom. */
  messages?: Maybe<ChatroomMetricConnection>;
};

/** The node at the end of a ChatroomMetricEdge. */
export type ChatroomMetric = Metric & Node & {
  __typename?: 'ChatroomMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the metric. A null value indicates that it is hidden or not available. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a ChatroomMetric. */
export type ChatroomMetricConnection = {
  __typename?: 'ChatroomMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<ChatroomMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type ChatroomMetricEdge = {
  __typename?: 'ChatroomMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<ChatroomMetric>;
};

/** The metrics of a Chatroom. */
export type ChatroomMetrics = Node & {
  __typename?: 'ChatroomMetrics';
  /** The engagement metrics of the chatroom. */
  engagement?: Maybe<ChatroomEngagementMetrics>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The possible values for a chatroom status. */
export enum ChatroomStatus {
  /** The chatroom is inactive and cannot be accessed by users. */
  Disabled = 'DISABLED',
  /** The chatroom is active and users can send and receive messages. */
  Enabled = 'ENABLED',
  /** The chatroom is visible but message sending is temporarily restricted. */
  Frozen = 'FROZEN'
}

export type ChatroomTokenPayload = {
  __typename?: 'ChatroomTokenPayload';
  /** Access token for the chatroom. */
  accessToken: Scalars['String']['output'];
  /** Lifetime in seconds before the token expires. */
  expiresIn: Scalars['Int']['output'];
  /** Mutation execution status. */
  status?: Maybe<Status>;
  /** Type of the token (typically 'Bearer'). */
  tokenType: Scalars['String']['output'];
};

/** Information about the viewer engagement of a Chatroom. */
export type ChatroomViewerEngagement = Node & {
  __typename?: 'ChatroomViewerEngagement';
  /** Indicates whether the viewer appears on the blocklist of the creator of the chatroom. */
  blocked: Scalars['Boolean']['output'];
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The input fields to clear the medias of a collection. */
export type ClearCollectionMediasInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the collection. */
  collectionXid: Scalars['String']['input'];
};

/** The return fields from clearing all medias from a collection. */
export type ClearCollectionMediasPayload = {
  __typename?: 'ClearCollectionMediasPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to clear the liked videos of the connected user. */
export type ClearLikedVideosInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
};

/** The return fields from clearing the liked videos of the connected user. */
export type ClearLikedVideosPayload = {
  __typename?: 'ClearLikedVideosPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to clear the `WatchLater` list of the connected user. */
export type ClearWatchLaterVideosInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
};

/** The return fields from clearing the `WatchLater` list of the connected user. */
export type ClearWatchLaterVideosPayload = {
  __typename?: 'ClearWatchLaterVideosPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to clear the `Watched` list of the connected user. */
export type ClearWatchedVideosInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
};

/** The return fields from clearing the `Watched` list of the connected user. */
export type ClearWatchedVideosPayload = {
  __typename?: 'ClearWatchedVideosPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** A collection manages medias. */
export type Collection = Content & Node & {
  __typename?: 'Collection';
  /**
   * The channel that created the collection.
   * @deprecated Use `creator` field.
   */
  channel?: Maybe<Channel>;
  /** The date and time (ISO 8601 format) when the collection was created. */
  createDate: Scalars['DateTime']['output'];
  /**
   * The creation date (DateTime ISO8601) of the collection.
   * @deprecated Use `createDate` field.
   */
  createdAt?: Maybe<Scalars['DateTime']['output']>;
  /** The creator that created the Collection. */
  creator?: Maybe<Channel>;
  /** The description of the collection. */
  description?: Maybe<Scalars['String']['output']>;
  /** The hashtags of the collection. */
  hashtags?: Maybe<HashtagConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether this collection is featured in `daily_picks`. */
  isFeatured?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the collection is private.
   * @deprecated Use `visibility` field.
   */
  isPrivate?: Maybe<Scalars['Boolean']['output']>;
  /** The lives of the channel. */
  lives?: Maybe<LiveConnection>;
  /**
   * The medias of the collection.
   * @deprecated Use `videos` or `lives` field.
   */
  medias?: Maybe<MediaConnection>;
  /** The metrics of the collection. */
  metrics?: Maybe<CollectionMetrics>;
  /** The name of the collection. */
  name?: Maybe<Scalars['String']['output']>;
  /**
   * The stats of the collection.
   * @deprecated Use `metrics` field.
   */
  stats?: Maybe<CollectionStats>;
  /** The URL of the thumbnail image. */
  thumbnail?: Maybe<Image>;
  /**
   * The URL of the thumbnail image.
   * @deprecated Use `thumbnail` field.
   */
  thumbnailURL?: Maybe<Scalars['String']['output']>;
  /**
   * The thumbnails of the collection.
   * @deprecated Use `thumbnailURL` field.
   */
  thumbnails?: Maybe<Thumbnails>;
  /** The date and time (ISO 8601 format) when the collection was updated. */
  updateDate: Scalars['DateTime']['output'];
  /**
   * The updated date (DateTime ISO8601) of the collection.
   * @deprecated Use `updateDate` field.
   */
  updatedAt?: Maybe<Scalars['DateTime']['output']>;
  /** The videos of the collection. */
  videos?: Maybe<VideoConnection>;
  /** The visibility of the collection. */
  visibility?: Maybe<Visibility>;
  /** The Dailymotion ID of the collection. */
  xid: Scalars['String']['output'];
};


/** A collection manages medias. */
export type CollectionHashtagsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A collection manages medias. */
export type CollectionLivesArgs = {
  filter?: InputMaybe<LiveFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A collection manages medias. */
export type CollectionMediasArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  types?: InputMaybe<Array<InputMaybe<MediaType>>>;
};


/** A collection manages medias. */
export type CollectionThumbnailArgs = {
  height: ThumbnailHeight;
};


/** A collection manages medias. */
export type CollectionThumbnailUrlArgs = {
  size: Scalars['String']['input'];
};


/** A collection manages medias. */
export type CollectionVideosArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  filter?: InputMaybe<VideoFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The connection type for Collection. */
export type CollectionConnection = {
  __typename?: 'CollectionConnection';
  /** A list of edges. */
  edges: Array<Maybe<CollectionEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type CollectionEdge = {
  __typename?: 'CollectionEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Collection>;
};

/** The engagement metrics of a Collection. */
export type CollectionEngagementMetrics = Node & {
  __typename?: 'CollectionEngagementMetrics';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The video metrics of the collection. */
  videos?: Maybe<VideoMetricConnection>;
};


/** The engagement metrics of a Collection. */
export type CollectionEngagementMetricsVideosArgs = {
  filter?: InputMaybe<VideoFilter>;
};

/** The available input fields of a Collection filter. */
export type CollectionFilter = {
  /** Filter collections by visibility. */
  visibility?: InputMaybe<VisibilityOperator>;
};

/** The input fields to clear/delete a collection. */
export type CollectionInput = {
  /** The ID of the collection. */
  id: Scalars['ID']['input'];
};

/** The node at the end of a CollectionMetricEdge. */
export type CollectionMetric = Metric & Node & {
  __typename?: 'CollectionMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the collection metric. A null value indicates that it is hidden or not available. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a CollectionMetric. */
export type CollectionMetricConnection = {
  __typename?: 'CollectionMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<CollectionMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type CollectionMetricEdge = {
  __typename?: 'CollectionMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<CollectionMetric>;
};

/** The metrics of a Collection. */
export type CollectionMetrics = Node & {
  __typename?: 'CollectionMetrics';
  /** The engagement metrics of the collection. */
  engagement?: Maybe<CollectionEngagementMetrics>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The return fields from modifying a collection. */
export type CollectionPayload = {
  __typename?: 'CollectionPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The settings when creating/updating a collection. */
export type CollectionSettingsInput = {
  /** Indicates the visibility of the collection. */
  visibility?: InputMaybe<Visibility>;
};

/** Represents the stats of a collection. */
export type CollectionStats = Node & {
  __typename?: 'CollectionStats';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The video stats of the collection. */
  videos?: Maybe<CollectionStatsVideos>;
};

/** The video stats of the collection. */
export type CollectionStatsVideos = Node & {
  __typename?: 'CollectionStatsVideos';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of videos of the collection. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** Information about a comment. */
export type Comment = Content & Node & Thread & {
  __typename?: 'Comment';
  /** The chatroom associated with the comment. */
  chatroom?: Maybe<Chatroom>;
  /** The creation date (DateTime ISO8601) of the comment. */
  createDate: Scalars['DateTime']['output'];
  /** The creator of the comment. */
  creator?: Maybe<Channel>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The metrics of the comment. */
  metrics?: Maybe<CommentMetrics>;
  /** The commented story. */
  opener?: Maybe<Story>;
  /** Indicates whether the creator of the story has liked the comment. */
  openerCreatorLiked: Scalars['Boolean']['output'];
  /** The share URLs of the comment. */
  shareUrls?: Maybe<CommentShareUrls>;
  /** The human-readable unique ID of the comment. */
  slug: Scalars['String']['output'];
  /** The content of the comment. */
  text: Scalars['String']['output'];
  /** The last update date (DateTime ISO8601) of the comment. */
  updateDate: Scalars['DateTime']['output'];
  /** The viewer engagement information of the comment. */
  viewerEngagement?: Maybe<CommentViewerEngagement>;
};

/** The connection type for Comment. */
export type CommentConnection = {
  __typename?: 'CommentConnection';
  /** A list of edges. */
  edges: Array<Maybe<CommentEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type CommentEdge = {
  __typename?: 'CommentEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Comment>;
};

/** The engagement metrics of a Comment. */
export type CommentEngagementMetrics = Node & {
  __typename?: 'CommentEngagementMetrics';
  /** The bookmark metrics of the comment. */
  bookmarks?: Maybe<BookmarkMetricConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The like metrics of the comment. */
  likes?: Maybe<LikeMetricConnection>;
};


/** The engagement metrics of a Comment. */
export type CommentEngagementMetricsBookmarksArgs = {
  filter?: InputMaybe<BookmarkFilter>;
};

/** The node at the end of a CommentMetricEdge. */
export type CommentMetric = Metric & Node & {
  __typename?: 'CommentMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the comment metric. A null value indicates that it is hidden or not available. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a CommentMetric. */
export type CommentMetricConnection = {
  __typename?: 'CommentMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<CommentMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type CommentMetricEdge = {
  __typename?: 'CommentMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<CommentMetric>;
};

/** The metrics of a Comment. */
export type CommentMetrics = Node & {
  __typename?: 'CommentMetrics';
  /** The engagement metrics of the comment. */
  engagement?: Maybe<CommentEngagementMetrics>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** Information about the share urls of a Comment. */
export type CommentShareUrls = Node & ShareUrls & {
  __typename?: 'CommentShareUrls';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The permalink share url of the comment. */
  permalink: Scalars['String']['output'];
};

/** The possible sort options for comment. */
export type CommentSort = {
  /** Sort by when the comment was created. */
  createDate?: InputMaybe<OrderDirection>;
};

/** Information about the viewer engagement of a Comment. */
export type CommentViewerEngagement = Node & ViewerEngagement & {
  __typename?: 'CommentViewerEngagement';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates the like rating of the comment from the viewer. */
  likeRating?: Maybe<LikeRating>;
  /** Indicates whether the viewer has liked the comment. Returns False if the viewer is not connected. */
  liked?: Maybe<Scalars['Boolean']['output']>;
};

/** The violation reasons to report the `Comment`. */
export enum CommentViolation {
  /** Content that contains child abuse. */
  ChildAbuse = 'CHILD_ABUSE',
  /** Content that is copyrighted. */
  CopyrightInfringement = 'COPYRIGHT_INFRINGEMENT',
  /** Content that misrepresents the owner. */
  CopyrightOwner = 'COPYRIGHT_OWNER',
  /** Content that is against humanity, such as genocide. */
  CrimesAgainstHumanity = 'CRIMES_AGAINST_HUMANITY',
  /** Content that contains child sexual abuse material. */
  Csam = 'CSAM',
  /** Content that contains false information or is misleading on purpose. */
  Disinformation = 'DISINFORMATION',
  /** Content that is harmful for children. */
  HarmfulContent = 'HARMFUL_CONTENT',
  /** Content that is hateful. */
  HatefulContent = 'HATEFUL_CONTENT',
  /** Content that contains personal or confidential information. */
  Privacy = 'PRIVACY',
  /** Content that contains nudity. */
  SexualContent = 'SEXUAL_CONTENT',
  /** Content that contains spam. */
  Spam = 'SPAM',
  /** Content that contains terrorism. */
  Terrorism = 'TERRORISM',
  /** Content that contains violence. */
  Violence = 'VIOLENCE'
}

/** Types that can be a Component. */
export type Component = Channel | Collection | Live | Poll | Reaction | ReactionVideo | Topic | Video;

/** The connection type for Component. */
export type ComponentConnection = {
  __typename?: 'ComponentConnection';
  /** A list of edges. */
  edges: Array<Maybe<ComponentEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type ComponentEdge = {
  __typename?: 'ComponentEdge';
  /** The metadata of the edge. */
  metadata?: Maybe<Metadata>;
  /** The item at the end of the edge. */
  node?: Maybe<Component>;
};

/** The input fields to confirm an email change. */
export type ConfirmEmailInput = {
  /** The confirmation code received from the email change request. */
  code: Scalars['String']['input'];
};

/** The return fields from confirming an email change. */
export type ConfirmEmailPayload = {
  __typename?: 'ConfirmEmailPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to confirm the report. */
export type ConfirmReportInput = {
  /** The token received in the email. */
  token: Scalars['String']['input'];
};

/** The return fields for confirming the report. */
export type ConfirmReportPayload = {
  __typename?: 'ConfirmReportPayload';
  /** The status of the mutation. */
  status: Status;
};

/** Represents a Content. */
export type Content = {
  /** The channel that created the content. */
  creator?: Maybe<Channel>;
};

/** Information about a content category. */
export type ContentCategory = Category & Node & {
  __typename?: 'ContentCategory';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the content category. */
  name: Scalars['String']['output'];
  /** The human-readable unique ID of the content category. */
  slug: Scalars['String']['output'];
};

/** The violation reason to report the story. */
export enum ContentViolation {
  /** Content that is copyrighted. */
  CopyrightInfringement = 'COPYRIGHT_INFRINGEMENT'
}

/** Information about a conversation. */
export type Conversation = Node & {
  __typename?: 'Conversation';
  /** The algorithm that suggested the conversation. */
  algorithm?: Maybe<ConversationAlgorithm>;
  /** Information about the DailymotionAd of the conversation. */
  dailymotionAd?: Maybe<DailymotionAd>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The interactions on the conversation. */
  interactions?: Maybe<InteractionConnection>;
  /** The story that started a conversation. */
  story?: Maybe<Story>;
};

/** Information about the conversation algorithm. */
export type ConversationAlgorithm = Algorithm & {
  __typename?: 'ConversationAlgorithm';
  /** The name of the algorithm. */
  name?: Maybe<AlgorithmName>;
  /** The match percentage of the conversation to the algorithm. */
  percentage?: Maybe<Scalars['Int']['output']>;
  /** The source. */
  source?: Maybe<Scalars['String']['output']>;
  /** The version. */
  version?: Maybe<Scalars['String']['output']>;
};

/** The connection type for Conversation. */
export type ConversationConnection = {
  __typename?: 'ConversationConnection';
  /** A list of edges. */
  edges: Array<Maybe<ConversationEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** The input fields of a conversations context argument. */
export type ConversationContext = {
  /** The action gesture performed by the user. */
  actionGesture?: InputMaybe<ActionGesture>;
  /** Indicate whether the user wants to opt out of personalized content. Defaults to true. */
  personalizationOptOut?: InputMaybe<Scalars['Boolean']['input']>;
  /** The ID generated by the player (each time it loads a recording). */
  viewId?: InputMaybe<Scalars['String']['input']>;
  /** The conversation context of the viewer. */
  viewer?: InputMaybe<ViewerContext>;
};

/** An edge in a connection. */
export type ConversationEdge = {
  __typename?: 'ConversationEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Conversation>;
};

/** The available input fields of a Poll filter. */
export type ConversationFilter = {
  /** Filter conversations by algorithm name. */
  algorithm?: InputMaybe<AlgorithmNameOperator>;
  /** Filter conversations by id. */
  id?: InputMaybe<IdOperator>;
  /** Filter conversations by slug. */
  slug?: InputMaybe<StringOperator>;
  /** Filter conversations by story. */
  story?: InputMaybe<StoryOperator>;
  /** Filter conversations by story ID. */
  storyId?: InputMaybe<IdOperator>;
};

/** Sort conversation by the available values. */
export type ConversationSort = {
  /** Sort conversations by when the stories were created. */
  createDate?: InputMaybe<OrderDirection>;
  /** Sort conversations by the number of views on the story. */
  views?: InputMaybe<OrderDirection>;
};

/** Represents a response from the convert speech from audio to text query */
export type ConvertSpeechFromAudioToTextResponse = {
  __typename?: 'ConvertSpeechFromAudioToTextResponse';
  /** The detected language of the response */
  detectedLanguage?: Maybe<Scalars['String']['output']>;
  /** The duration of the response */
  duration?: Maybe<Scalars['Float']['output']>;
  /** The full text of the response */
  fullText: Scalars['String']['output'];
  /** The provider of the response */
  provider: SpeechToTextProvider;
  /** The sentences of the response */
  sentences: Array<Maybe<SentenceWithSegments>>;
};

/** The input fields of the copyrighted content for submitting the report. */
export type CopyrightedContent = {
  /** The audiovisual work type of the copyrighted content. */
  audiovisualWork?: InputMaybe<AudiovisualWork>;
  /** Relationship to the owner of the copyrighted content. */
  claimant: ReporterClaimant;
  /** The name of the copyrighted content owner. */
  owner?: InputMaybe<Scalars['String']['input']>;
  /** The title of the copyrighted content. */
  title: Scalars['String']['input'];
  /** The work type of the copyrighted content. */
  typeOfWork?: CopyrightedWorkType;
  /** The url of the copyrighted content. */
  url: Scalars['String']['input'];
};

export enum CopyrightedWorkType {
  /** Represents a motion picture or an audiovisual work -- Movies, TV Shows, Video Games, Animation, Videos. */
  Audiovisual = 'AUDIOVISUAL',
  /** Represents a literary work -- Fiction, Non-Fiction, Poetry, Articles, Periodicals. */
  Literary = 'LITERARY',
  /** Represents a sound recording -- A series of musical or other sounds, but not including the sounds accompanying a motion picture or other audiovisual work. */
  SoundRecording = 'SOUND_RECORDING',
  /** Represents a visual art -- Artwork, Illustrations, Jewelry, Fabric, Architecture. */
  VisualArt = 'VISUAL_ART'
}

/** Information about a country. */
export type Country = Node & {
  __typename?: 'Country';
  /**
   * The ISO-3166-1 country code.
   * @deprecated Use `codeAlpha2` field.
   */
  code?: Maybe<Scalars['String']['output']>;
  /** The ISO 3166-2 country code. */
  codeAlpha2?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the country. */
  name?: Maybe<Scalars['String']['output']>;
};

/** The connection type for Country. */
export type CountryConnection = {
  __typename?: 'CountryConnection';
  /** A list of edges. */
  edges: Array<Maybe<CountryEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type CountryEdge = {
  __typename?: 'CountryEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Country>;
};

/** The input fields to create a behavior rule. */
export type CreateBehaviorRuleInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** Condition to apply the feature or experiment segmentation (contains JSON). */
  condition: Scalars['String']['input'];
  /** Description of the new rule. */
  description?: InputMaybe<Scalars['String']['input']>;
  /** Indicates whether the rule is enabled. */
  enabled?: InputMaybe<Scalars['Boolean']['input']>;
  /** @deprecated(reason: "Use `endDate` input field.") - The end date and time (DateTime ISO8601) of the rule if enabled. */
  endAt?: InputMaybe<Scalars['DateTime']['input']>;
  /** The date and time (ISO 8601 format) when the rule ends (if enabled). */
  endDate?: InputMaybe<Scalars['DateTime']['input']>;
  /** Experiment configuration. If set, the rule will be an experiment (contains JSON). */
  experiment?: InputMaybe<Scalars['String']['input']>;
  /** The name of the new rule. */
  name: Scalars['String']['input'];
  /** @deprecated(reason: "Use `endDate` input field.") - Start date and time (DateTime ISO8601) of the rule if enabled. */
  startAt?: InputMaybe<Scalars['DateTime']['input']>;
  /** The date and time (ISO 8601 format) when the rule starts (if enabled). */
  startDate?: InputMaybe<Scalars['DateTime']['input']>;
  /** The tags associated with the rule. Useful for filtering. */
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};

/** The return fields from creating a rule for feature flipping or AB experiments. */
export type CreateBehaviorRulePayload = {
  __typename?: 'CreateBehaviorRulePayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The new rule. */
  rule?: Maybe<Rule>;
};

/** The input fields to create a chatroom. */
export type CreateChatroomInput = {
  /** The ID of the Thread to create a chatroom for. */
  id?: InputMaybe<Scalars['ID']['input']>;
};

/** The return fields from creating a chatroom. */
export type CreateChatroomPayload = {
  __typename?: 'CreateChatroomPayload';
  /** The new chatroom. */
  chatroom?: Maybe<Chatroom>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to create a collection. */
export type CreateCollectionInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The description of the collection. */
  description?: InputMaybe<Scalars['String']['input']>;
  /** The name of the new collection. */
  name: Scalars['String']['input'];
  /** @deprecated(reason: "settings.visibility` input arg.") - Indicate whether the collection is private. */
  private?: InputMaybe<Scalars['Boolean']['input']>;
  /** The settings when creating a collection. */
  settings?: InputMaybe<CollectionSettingsInput>;
};

/** The return fields from creating a collection. */
export type CreateCollectionPayload = {
  __typename?: 'CreateCollectionPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The new collection. */
  collection?: Maybe<Collection>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to create a comment. */
export type CreateCommentInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the Video to create a comment for. */
  id?: InputMaybe<Scalars['ID']['input']>;
  /** @deprecated(reason: "Use `id` input arg.") - The ID of the post that the comment is created for. */
  postId?: InputMaybe<Scalars['ID']['input']>;
  /** @deprecated(reason: "Use `id` input arg.") - The ID of the story that the comment is created for. */
  storyId?: InputMaybe<Scalars['ID']['input']>;
  /** The text on the comment. */
  text: Scalars['String']['input'];
};

/** The return fields from creating a comment. */
export type CreateCommentPayload = {
  __typename?: 'CreateCommentPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The new comment. */
  comment?: Maybe<Comment>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to create a reaction. */
export type CreateReactionInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the opener that the reaction is created for. */
  openerId: Scalars['ID']['input'];
  /** The URL of the thumbnail image. */
  thumbnailURL?: InputMaybe<Scalars['String']['input']>;
  /** The title of the reaction. */
  title?: InputMaybe<Scalars['String']['input']>;
  /** The URL of the reaction to get the upload file from. */
  url: Scalars['String']['input'];
};

/** The input fields to create a user. */
export type CreateUserInput = {
  /** The URL of the avatar image of the user. */
  avatarURL?: InputMaybe<Scalars['String']['input']>;
  /** The birthday (DateTime ISO8601) of the user. */
  birthday?: InputMaybe<Scalars['DateTime']['input']>;
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** Indicate whether or not to test the creation workflow without actually creating the user. */
  dryRun?: InputMaybe<Scalars['Boolean']['input']>;
  /** The email address of the user. */
  email: Scalars['String']['input'];
  /** The first name of the user. */
  firstName?: InputMaybe<Scalars['String']['input']>;
  /** The gender of the user. */
  gender?: InputMaybe<Gender>;
  lastName?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "Use `firstName` and `lastName` respectively.") - The name of the user. */
  name?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "No longer supported.") - The nickname of the user. */
  nickname?: InputMaybe<Scalars['String']['input']>;
  /** The organization activation key to validate the user creation. */
  organizationActivationKey?: InputMaybe<Scalars['String']['input']>;
  /** The password for the user. */
  password?: InputMaybe<Scalars['String']['input']>;
  /** The user response token provided by reCAPTCHA. */
  recaptchaToken?: InputMaybe<Scalars['String']['input']>;
  /** The user response token provided by turnstile. */
  turnstileToken?: InputMaybe<Scalars['String']['input']>;
  /** The mutation version. */
  version?: InputMaybe<Scalars['Int']['input']>;
};

/** The return fields from creating a user. */
export type CreateUserPayload = {
  __typename?: 'CreateUserPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
  /** The new Dailymotion user. */
  user?: Maybe<User>;
};

/** The input fields to create a video. */
export type CreateVideoInput = {
  /** Indicates whether the video is AI-altered content. */
  aiAltered?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicates the target audience the video is created for. */
  audience?: InputMaybe<AudienceGuide>;
  /** The category of the video. */
  category?: InputMaybe<MediaCategory>;
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The description of the video. */
  description?: InputMaybe<Scalars['String']['input']>;
  /** Indicates whether the video is exclusive to Dailymotion. */
  exclusive?: InputMaybe<Scalars['Boolean']['input']>;
  /** The hashtags of the video */
  hashtags?: InputMaybe<Array<Scalars['String']['input']>>;
  /** @deprecated(reason: "Use `settings.audience` input arg.") - Indicates whether the video is created for kids. */
  isCreatedForKids?: InputMaybe<Scalars['Boolean']['input']>;
  /** The language of the video. */
  language?: InputMaybe<Scalars['String']['input']>;
  /** Indicate whether the video has paid partnership. */
  paidPartnership?: InputMaybe<Scalars['Boolean']['input']>;
  /** The password of the video. When setting a value on this field, the video visibility changes to `password protected`. */
  password?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "Use `settings.visibility` input arg.") - Indicates whether the video is private. */
  private?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicates whether the video is published. */
  published?: InputMaybe<Scalars['Boolean']['input']>;
  /** The default settings when creating a video. */
  settings?: InputMaybe<VideoSettingsInput>;
  /** The list of tags to associate to the video. */
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  /** The URL of the thumbnail image. */
  thumbnailURL?: InputMaybe<Scalars['String']['input']>;
  /** The title of the video. */
  title?: InputMaybe<Scalars['String']['input']>;
  /** The URL of the video. */
  url?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "Use `settings.visibility` input arg.") - The visibility of the Video. */
  visibility?: InputMaybe<Visibility>;
};

/** The return fields from creating a new Video. */
export type CreateVideoPayload = {
  __typename?: 'CreateVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
  /** The new video. */
  video?: Maybe<Video>;
};

/** The violation reasons to report the `Creator`. */
export enum CreatorViolation {
  /** Content that violates the community guidelines. */
  InappropriateContent = 'INAPPROPRIATE_CONTENT'
}

/** Information of a curated category. */
export type CuratedCategory = Category & Node & {
  __typename?: 'CuratedCategory';
  /** The ID of the category. */
  categoryId: Scalars['Int']['output'];
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the curated category. */
  name: Scalars['String']['output'];
  /** The human-readable unique ID of the curated category. */
  slug: Scalars['String']['output'];
};

/** The connection type for CuratedCategory. */
export type CuratedCategoryConnection = {
  __typename?: 'CuratedCategoryConnection';
  /** A list of edges. */
  edges: Array<Maybe<CuratedCategoryEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type CuratedCategoryEdge = {
  __typename?: 'CuratedCategoryEdge';
  /** The item at the end of the edge. */
  node?: Maybe<CuratedCategory>;
};

/** Information about a DailymotionAd. */
export type DailymotionAd = Node & {
  __typename?: 'DailymotionAd';
  /** The channel associated to the DailymotionAd. */
  channel?: Maybe<Channel>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The promotion of the DailymotionAd. */
  promotion: Promotion;
};

/** The available input fields of a datetime operator. */
export type DateTimeOperator = {
  /** Short for greater than or equal to. */
  gte?: InputMaybe<Scalars['DateTime']['input']>;
  /** Short for lower than or equal to. */
  lte?: InputMaybe<Scalars['DateTime']['input']>;
};

/** The input fields to delete a behavior rule. */
export type DeleteBehaviorRuleInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The name of the rule to delete. */
  name: Scalars['String']['input'];
};

/** The return fields from deleting a rule used for feature flipping or AB experiments. */
export type DeleteBehaviorRulePayload = {
  __typename?: 'DeleteBehaviorRulePayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** Indicates whether the mutation was successful. */
  success?: Maybe<Scalars['Boolean']['output']>;
};

/** The input fields to delete a comment. */
export type DeleteCommentInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the Comment to delete. */
  id: Scalars['ID']['input'];
};

/** The return fields from deleting a comment. */
export type DeleteCommentPayload = {
  __typename?: 'DeleteCommentPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to delete a reaction. */
export type DeleteReactionInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the reaction to delete. */
  id: Scalars['ID']['input'];
};

/** The return fields from deleting a reaction. */
export type DeleteReactionPayload = {
  __typename?: 'DeleteReactionPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to delete a user. */
export type DeleteUserInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The password of the user. */
  password?: InputMaybe<Scalars['String']['input']>;
};

/** The return fields from deleting a user. */
export type DeleteUserPayload = {
  __typename?: 'DeleteUserPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to delete a video. */
export type DeleteVideoInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion of the video. */
  xid: Scalars['String']['input'];
};

/** The return fields from deleting a video. */
export type DeleteVideoPayload = {
  __typename?: 'DeleteVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/**
 *
 * The possible values for device authorization consent.
 *
 */
export enum DeviceAuthorizationConsent {
  /**
   *
   *   The authorization for the device has been approved.
   *
   */
  Approved = 'APPROVED',
  /**
   *
   *   The authorization for the device has been denied.
   *
   */
  Denied = 'DENIED',
  /**
   *
   *   The authorization for the device is still pending.
   *
   */
  Pending = 'PENDING'
}

/** Information about the email change request of the user. */
export type EmailChangeRequest = Node & {
  __typename?: 'EmailChangeRequest';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The new email the user has requested to change to. */
  newEmail: Scalars['String']['output'];
};

/** The settings to receive email notifications. */
export type EmailNotificationSettings = Node & {
  __typename?: 'EmailNotificationSettings';
  /** The notifications on activities to receive. */
  activity?: Maybe<ActivityNotificationSettings>;
  /** The notifications on announcements to receive. */
  announcements?: Maybe<AnnouncementNotificationSettings>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The notifications on insights to receive. */
  insights?: Maybe<InsightNotificationSettings>;
  /** The notifications on recommendations to receive. */
  recommendations?: Maybe<RecommendationNotificationSettings>;
};

/** The notification settings to receive via email. */
export type EmailNotificationSettingsInput = {
  /** The notifications on activities to receive. */
  activity?: InputMaybe<ActivityNotificationSettingsInput>;
  /** The notifications on announcements to receive. */
  announcements?: InputMaybe<AnnouncementNotificationSettingsInput>;
  /** The notifications on insights to receive. */
  insights?: InputMaybe<InsightNotificationsSettingsInput>;
  /** The notifications on recommendations to receive. */
  recommendations?: InputMaybe<RecommendationNotificationSettingsInput>;
};

/** Represents the details of an embed. */
export type Embed = Node & {
  __typename?: 'Embed';
  /** The html of the embed for the video. */
  html?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The url of the embed for the video. */
  url?: Maybe<Scalars['String']['output']>;
};

/** The different embed formats for a Player. */
export enum EmbedFormat {
  /** A classic embed which uses a recording. */
  Classic = 'CLASSIC',
  /** A contextual embed. */
  Contextual = 'CONTEXTUAL'
}

/** Represents an emoji enriched element */
export type EmojiEnrichedElement = {
  __typename?: 'EmojiEnrichedElement';
  /** The emoji */
  emoji?: Maybe<Scalars['String']['output']>;
  /** The highlighted word */
  highlightedWord?: Maybe<Scalars['String']['output']>;
};

/** Represents an enriched audio element */
export type EnrichedAudioElement = {
  __typename?: 'EnrichedAudioElement';
  /** The category */
  category?: Maybe<Scalars['String']['output']>;
};

/** Represents an enriched broll element url */
export type EnrichedBRollElementUrl = {
  __typename?: 'EnrichedBRollElementUrl';
  /** The landscape url */
  landscape: Scalars['String']['output'];
  /** The large url */
  large: Scalars['String']['output'];
  /** The large2x url */
  large2x: Scalars['String']['output'];
  /** The medium url */
  medium: Scalars['String']['output'];
  /** The original url */
  original: Scalars['String']['output'];
  /** The portrait url */
  portrait: Scalars['String']['output'];
  /** The small url */
  small: Scalars['String']['output'];
  /** The tiny url */
  tiny: Scalars['String']['output'];
};

/** Represents an enriched broll element */
export type EnrichedBrollElement = {
  __typename?: 'EnrichedBrollElement';
  /**
   * The highlighted word
   * @deprecated old broll property
   */
  highlightedWord?: Maybe<Scalars['String']['output']>;
  /** @deprecated old broll property */
  keywords?: Maybe<Array<Maybe<Scalars['String']['output']>>>;
  /** The keywords string */
  keywordsString: Scalars['String']['output'];
  /** The segment */
  segment: Scalars['String']['output'];
  /** The urls */
  urls: EnrichedBRollElementUrl;
};

/** Represents an enriched element title */
export type EnrichedElementTitle = {
  __typename?: 'EnrichedElementTitle';
  /** The emoji */
  emoji?: Maybe<Scalars['String']['output']>;
  /** The highlighted word */
  highlightedWord?: Maybe<Scalars['String']['output']>;
  /** The title */
  title: Scalars['String']['output'];
};

/** Represents an enriched element topic */
export type EnrichedElementTopic = {
  __typename?: 'EnrichedElementTopic';
  /** The text section */
  textSection: Scalars['String']['output'];
  /** The title */
  title: Scalars['String']['output'];
};

/** Represents enriched elements */
export type EnrichedElements = {
  __typename?: 'EnrichedElements';
  /** The bRolls */
  bRolls?: Maybe<Array<EnrichedBrollElement>>;
  /** The emojis */
  emojis?: Maybe<Array<EmojiEnrichedElement>>;
  /** The sound effects */
  soundFxs?: Maybe<Array<SoundEffectElement>>;
};

/** Represents the global context enriched elements */
export type EnrichedElementsForContext = {
  __typename?: 'EnrichedElementsForContext';
  /** The audio */
  audio?: Maybe<EnrichedAudioElement>;
  /** The detected data */
  detectedData?: Maybe<Scalars['String']['output']>;
  /** The titles */
  titles: Array<EnrichedElementTitle>;
  /** The topics */
  topics?: Maybe<Array<EnrichedElementTopic>>;
};

/** Represents a response from the get enriched elements for sentences query */
export type EnrichedElementsForSentences = {
  __typename?: 'EnrichedElementsForSentences';
  /** The global context elements */
  globalContextElements: EnrichedElementsForContext;
  /** The sentences with enriched elements */
  sentences: Array<Maybe<SentenceWithEnrichedElements>>;
};

/** Represents an experiment (A/B testing) matched/enabled for a client. */
export type ExperimentMatch = Node & {
  __typename?: 'ExperimentMatch';
  /** The date and time (ISO 8601 format) when the experiment ends (if enabled). */
  endDate?: Maybe<Scalars['DateTime']['output']>;
  /**
   * The end date and time (DateTime ISO8601) of the experiment if enabled.
   * @deprecated Use `endDate` field.
   */
  endingAt?: Maybe<Scalars['DateTime']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the condition is matched. */
  matched?: Maybe<Scalars['Boolean']['output']>;
  /** A unique name for the experiment. */
  name?: Maybe<Scalars['String']['output']>;
  /**
   * The reviewed date (DateTime ISO8601) of the media.
   * @deprecated Use `reviewDate` field.
   */
  reviewedAt?: Maybe<Scalars['DateTime']['output']>;
  /** The tags associated to the experiment. Useful for filtering. */
  tags?: Maybe<BehaviorRuleTagConnection>;
  /** A unique uuid for the experiment. */
  uuid?: Maybe<Scalars['String']['output']>;
  /** Variation assigned. */
  variation?: Maybe<Scalars['String']['output']>;
};


/** Represents an experiment (A/B testing) matched/enabled for a client. */
export type ExperimentMatchTagsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The connection type for Experiment Match. */
export type ExperimentMatchConnection = {
  __typename?: 'ExperimentMatchConnection';
  /** A list of edges. */
  edges: Array<Maybe<ExperimentMatchEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type ExperimentMatchEdge = {
  __typename?: 'ExperimentMatchEdge';
  /** The item at the end of the edge. */
  node?: Maybe<ExperimentMatch>;
};

/** Information about a fallback country. */
export type FallbackCountry = Node & {
  __typename?: 'FallbackCountry';
  /** The country to fallback from. */
  country?: Maybe<Country>;
  /** The country to fallback to. */
  fallbackCountry?: Maybe<Country>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The connection type for Fallback Country. */
export type FallbackCountryConnection = {
  __typename?: 'FallbackCountryConnection';
  /** A list of edges. */
  edges: Array<Maybe<FallbackCountryEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type FallbackCountryEdge = {
  __typename?: 'FallbackCountryEdge';
  /** The item at the end of the edge. */
  node?: Maybe<FallbackCountry>;
};

/** Represents a Favorite (an activity). */
export type Favorite = Bookmark & History & Node & {
  __typename?: 'Favorite';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The post favorited by the channel. */
  post: Post;
  /**
   * Indicates the like rating of the favorite on the post.
   * @deprecated Not supported.
   */
  rating?: Maybe<LikeRating>;
};

/** The input fields to add/remove a `Favorite` to/from the favorites list of the connected user. */
export type FavoriteInput = {
  /** The Dailymotion ID of the `favorite` to add/remove. */
  id: Scalars['ID']['input'];
};

/** The return fields from performing an action on the favorites list of the connected user. */
export type FavoritePayload = {
  __typename?: 'FavoritePayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Represents a feature object matched/enabled for a client. */
export type FeatureMatch = Node & {
  __typename?: 'FeatureMatch';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the feature is matched. */
  matched?: Maybe<Scalars['Boolean']['output']>;
  /** A unique name for the feature. */
  name?: Maybe<Scalars['String']['output']>;
  /** The tags associated with the rule. Useful for filtering. */
  tags?: Maybe<BehaviorRuleTagConnection>;
};


/** Represents a feature object matched/enabled for a client. */
export type FeatureMatchTagsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The connection type for FeatureMatch. */
export type FeatureMatchConnection = {
  __typename?: 'FeatureMatchConnection';
  /** A list of edges. */
  edges: Array<Maybe<FeatureMatchEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type FeatureMatchEdge = {
  __typename?: 'FeatureMatchEdge';
  /** The item at the end of the edge. */
  node?: Maybe<FeatureMatch>;
};

/** Content featured by Dailymotion. */
export type FeaturedContent = Node & {
  __typename?: 'FeaturedContent';
  /** The featured channels. */
  channels?: Maybe<ChannelConnection>;
  /** The featured collections. */
  collections?: Maybe<CollectionConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The featured medias. */
  medias?: Maybe<MediaConnection>;
};


/** Content featured by Dailymotion. */
export type FeaturedContentChannelsArgs = {
  category?: InputMaybe<FeaturedContentCategory>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Content featured by Dailymotion. */
export type FeaturedContentCollectionsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Content featured by Dailymotion. */
export type FeaturedContentMediasArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  category?: InputMaybe<FeaturedContentCategory>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The possible categories for a featured content. */
export enum FeaturedContentCategory {
  /** The music category. */
  Music = 'MUSIC',
  /** The news category. */
  News = 'NEWS',
  /** The sport category. */
  Sport = 'SPORT'
}

/** The available input fields of a Feed filter. */
export type FeedFilter = {
  /** The ID of the feed. */
  id?: InputMaybe<IdOperator>;
  /** The unique name of the feed. */
  name?: InputMaybe<StringOperator>;
  /** The post of the feed. */
  post?: InputMaybe<PostOperator>;
  /** The post ID of the feed. */
  postId?: InputMaybe<IdOperator>;
  /** The post status of the feed. */
  postStatus?: InputMaybe<PostStatusOperator>;
};

/** The possible values for feed name */
export enum FeedName {
  /** Hashtag. */
  Hashtag = 'HASHTAG',
  /** Perspective posts. */
  Perspective = 'PERSPECTIVE'
}

/** A feed post. */
export type FeedPost = {
  __typename?: 'FeedPost';
  /** Indicates whether the post is featured. */
  featured?: Maybe<Scalars['Boolean']['output']>;
  /** Information about the post. */
  post?: Maybe<Post>;
};

/** The connection type for FeedPost. */
export type FeedPostConnection = {
  __typename?: 'FeedPostConnection';
  /** A list of edges. */
  edges?: Maybe<Array<Maybe<FeedPostEdge>>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type FeedPostEdge = {
  __typename?: 'FeedPostEdge';
  /** The item at the end of the edge. */
  node?: Maybe<FeedPost>;
};

/** The available sort options for feeds. */
export type FeedSort = {
  /** Sort by when the post was created. */
  create?: InputMaybe<OrderDirection>;
  /** Sort by post popularity based on number of views. */
  popular?: InputMaybe<OrderDirection>;
};

/** Information about the URLs of a file upload. */
export type FileUpload = Node & {
  __typename?: 'FileUpload';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The URL to use in order to get info about the file upload progress. */
  progressURL?: Maybe<Scalars['String']['output']>;
  /** The URL to upload the file to. */
  uploadURL?: Maybe<Scalars['String']['output']>;
};

/** The input fields to follow a channel for the connected user. */
export type FollowChannelInput = {
  /** The Dailymotion ID of the channel. */
  channelXid: Scalars['String']['input'];
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
};

/** The return fields from following a channel for the connected user. */
export type FollowChannelPayload = {
  __typename?: 'FollowChannelPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to follow channels for the connected user. */
export type FollowChannelsInput = {
  /** The Dailymotion IDs of the channels to follow. */
  channelXids: Array<Scalars['String']['input']>;
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
};

/** The return fields from following channels for the connected user. */
export type FollowChannelsPayload = {
  __typename?: 'FollowChannelsPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to follow a channel for the connected user. */
export type FollowTopicInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the topic to follow. */
  topicXid: Scalars['String']['input'];
};

/** The return fields from following a topic for the connected user. */
export type FollowTopicPayload = {
  __typename?: 'FollowTopicPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to follow topics for the connected user. */
export type FollowTopicsInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion IDs of the topics to follow. */
  topicXids: Array<Scalars['String']['input']>;
};

/** The return fields from follow topics for the connected user. */
export type FollowTopicsPayload = {
  __typename?: 'FollowTopicsPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to follow a user for the connected user. */
export type FollowUserInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the user. */
  xid: Scalars['String']['input'];
};

/** The return fields from following a user for the connected user. */
export type FollowUserPayload = {
  __typename?: 'FollowUserPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Information about a channel that is being followed by a user. */
export type FollowedChannel = Node & {
  __typename?: 'FollowedChannel';
  /** The channel that is being followed. */
  channel?: Maybe<Channel>;
  /** The date and time (ISO 8601 format) when the channel was followed. */
  followDate: Scalars['DateTime']['output'];
  /**
   * The date and time (DateTime ISO8601) the channel was followed at.
   * @deprecated Use `followDate` field
   */
  followedAt?: Maybe<Scalars['DateTime']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the push notification settings is enabled. */
  isNotificationEnabled?: Maybe<Scalars['Boolean']['output']>;
};

/** The connection type for FollowedChannel. */
export type FollowedChannelConnection = {
  __typename?: 'FollowedChannelConnection';
  /** A list of edges. */
  edges: Array<Maybe<FollowedChannelEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type FollowedChannelEdge = {
  __typename?: 'FollowedChannelEdge';
  /** The item at the end of the edge. */
  node?: Maybe<FollowedChannel>;
};

/** The possible sort values to order the channels followed by a user. */
export enum FollowedChannelsSort {
  /** Sort followed channels by last video uploaded. */
  Activity = 'ACTIVITY',
  /** Sort followed channels by display name ascending. */
  Alphaaz = 'ALPHAAZ',
  /** Sort followed channels by recently followed. */
  Recent = 'RECENT'
}

/** Information about a topic that is being followed by a user. */
export type FollowedTopic = Node & {
  __typename?: 'FollowedTopic';
  /** The date and time (ISO 8601 format) when the topic was followed. */
  followDate: Scalars['DateTime']['output'];
  /** The date and time (Date ISO8601) the topic was followed at. */
  followedAt?: Maybe<Scalars['DateTime']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The topic that is being followed. */
  topic?: Maybe<Topic>;
};

/** The connection type for FollowedTopic. */
export type FollowedTopicConnection = {
  __typename?: 'FollowedTopicConnection';
  /** A list of edges. */
  edges: Array<Maybe<FollowedTopicEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type FollowedTopicEdge = {
  __typename?: 'FollowedTopicEdge';
  /** The item at the end of the edge. */
  node?: Maybe<FollowedTopic>;
};

/** The possible sort values to order the topics followed by a user. */
export enum FollowedTopicsSort {
  /** Sort followed topics by last video uploaded. */
  Activity = 'ACTIVITY',
  /** Sort followed topics by name ascending. */
  Alphaaz = 'ALPHAAZ',
  /** Sort followed topics by recently followed. */
  Recent = 'RECENT'
}

/** Information about a user that is following the requested user */
export type Follower = Node & {
  __typename?: 'Follower';
  /** The Channel information about the follower. */
  creator?: Maybe<Channel>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The User information about the follower.
   * @deprecated Use `creator` field.
   */
  user?: Maybe<User>;
};

/** The connection type for Follower. */
export type FollowerConnection = {
  __typename?: 'FollowerConnection';
  /** A list of edges. */
  edges: Array<Maybe<FollowerEdge>>;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type FollowerEdge = {
  __typename?: 'FollowerEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Follower>;
};

/** Information about the follower engagement on a Channel. */
export type FollowerEngagement = Node & {
  __typename?: 'FollowerEngagement';
  /** The datetime the follower started following the Channel. */
  followDate?: Maybe<Scalars['DateTime']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates the notifications the follower wants to receive about the Channel. */
  notifications?: Maybe<FollowerEngagementNotifications>;
};

/** Information about the follower engagement notifications on a Channel. */
export type FollowerEngagementNotifications = Node & {
  __typename?: 'FollowerEngagementNotifications';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the follower wants to received notifications when the channel uploads content. */
  uploads: Scalars['Boolean']['output'];
};

/** The node at the end of a FollowerMetricEdge. */
export type FollowerMetric = Metric & Node & {
  __typename?: 'FollowerMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the channel metric. A null value indicates that it is hidden or not available. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a FollowerMetric. */
export type FollowerMetricConnection = {
  __typename?: 'FollowerMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<FollowerMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type FollowerMetricEdge = {
  __typename?: 'FollowerMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<FollowerMetric>;
};

/** Information about a user, who the requested user is following. */
export type Following = Node & {
  __typename?: 'Following';
  /**
   * The Channel information of the user the requested user follows.
   * @deprecated Use `story` field.
   */
  creator?: Maybe<Channel>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Information about the story the creator is following. */
  story?: Maybe<Story>;
  /**
   * The information of the user the requested user follows.
   * @deprecated Use `story` field.
   */
  user?: Maybe<User>;
};

/** Following channel starts live notification settings. */
export type FollowingChannelStartsLive = Node & {
  __typename?: 'FollowingChannelStartsLive';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the email notification setting is enabled. */
  isEmailEnabled?: Maybe<Scalars['Boolean']['output']>;
};

/** Following channel uploads video notification settings. */
export type FollowingChannelUploadsVideo = Node & {
  __typename?: 'FollowingChannelUploadsVideo';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the email notification setting is enabled. */
  isEmailEnabled?: Maybe<Scalars['Boolean']['output']>;
};

/** The connection type for Following. */
export type FollowingConnection = {
  __typename?: 'FollowingConnection';
  /** A list of edges. */
  edges: Array<Maybe<FollowingEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** The following context of the viewer. */
export type FollowingContext = {
  creator?: InputMaybe<IdOperator>;
};

/** An edge in a connection. */
export type FollowingEdge = {
  __typename?: 'FollowingEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Following>;
};

/** The available input fields of a Following filter. */
export type FollowingFilter = {
  /** Filter followings by story. Defaults to `channel`. */
  story?: InputMaybe<StoryOperator>;
};

/** The input fields to follow/unfollow a story for the connected creator. */
export type FollowingInput = {
  /** The ID of the story. */
  storyId: Scalars['ID']['input'];
};

/** The node at the end of a FollowingMetricEdge. */
export type FollowingMetric = Metric & Node & {
  __typename?: 'FollowingMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the following metric. A null value indicates that it is hidden or not available. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a FollowingMetric. */
export type FollowingMetricConnection = {
  __typename?: 'FollowingMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<FollowingMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type FollowingMetricEdge = {
  __typename?: 'FollowingMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<FollowingMetric>;
};

/** The return fields from following/unfollowing a story for the connected creator. */
export type FollowingPayload = {
  __typename?: 'FollowingPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Following channel or topic starts live notification settings. */
export type FollowingStartsLive = Node & {
  __typename?: 'FollowingStartsLive';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the push notification setting is enabled. */
  isPushEnabled?: Maybe<Scalars['Boolean']['output']>;
};

/** The possible genders for a user. */
export enum Gender {
  /** A gender identifying as a female. */
  Female = 'female',
  /** A gender identifying as a male. */
  Male = 'male',
  /** A gender identifying as other. */
  Other = 'other',
  /** A value the user prefers not to answer. */
  PreferNotToAnswer = 'prefer_not_to_answer'
}

/** The input fields to generate a new username for a channel. */
export type GenerateChannelUsernameInput = {
  /** Indicate whether to update the channel username with the result. */
  upsert?: InputMaybe<Scalars['Boolean']['input']>;
};

/** The return fields from generating a channel username. */
export type GenerateChannelUsernamePayload = {
  __typename?: 'GenerateChannelUsernamePayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
  /** The generated channel username. */
  username: Scalars['String']['output'];
};

/** The input fields to generate a file upload url. */
export type GenerateFileUploadUrlInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
};

/** The return fields from generating a file url upload. */
export type GenerateFileUploadUrlPayload = {
  __typename?: 'GenerateFileUploadUrlPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** Information about the file upload. */
  fileUpload?: Maybe<FileUpload>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to generate a token to verify the email. */
export type GenerateVerifyEmailTokenInput = {
  /** The email address of the user. */
  email: Scalars['String']['input'];
};

/** The return fields to generate a token to verify the email. */
export type GenerateVerifyEmailTokenPayload = {
  __typename?: 'GenerateVerifyEmailTokenPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
  /** The token generated to send a code to verify the email. */
  token: Scalars['String']['output'];
};

/** The geoblocked countries of a media. */
export type GeoblockedCountries = Node & {
  __typename?: 'GeoblockedCountries';
  /** The list of allowed countries. */
  allowed?: Maybe<Array<Scalars['String']['output']>>;
  /** The list of denied countries. */
  denied?: Maybe<Array<Scalars['String']['output']>>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The geoblocking information. */
export type Geoblocking = Node & {
  __typename?: 'Geoblocking';
  /** The country code (ISO 3166-1 alpha-2) of the geoblocking. */
  country?: Maybe<Country>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the geoblocking is allowed. */
  isAllowed?: Maybe<Scalars['Boolean']['output']>;
};

/** The connection type for Geoblocking. */
export type GeoblockingConnection = {
  __typename?: 'GeoblockingConnection';
  /** A list of edges. */
  edges: Array<Maybe<GeoblockingEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type GeoblockingEdge = {
  __typename?: 'GeoblockingEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Geoblocking>;
};

/** The possible values of Hearted. */
export enum Hearted {
  /** Indicates that it is boosted. */
  Boosted = 'BOOSTED',
  /** Indicates that it is liked. */
  Liked = 'LIKED'
}

/** Information of a Hashtag. */
export type Hashtag = Node & {
  __typename?: 'Hashtag';
  /** The follower engagement information of the hashtag. */
  followerEngagement?: Maybe<FollowerEngagement>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The metrics of the hashtag. */
  metrics?: Maybe<HashtagMetrics>;
  /** The name of the hashtag. */
  name: Scalars['String']['output'];
  /** The share urls of the hashtag. */
  shareUrls?: Maybe<HashtagShareUrls>;
  /** The slug of the hashtag. */
  slug: Scalars['String']['output'];
  /** The Dailymotion ID of the hashtag. */
  xid: Scalars['String']['output'];
};

/** The connection type for Hashtag. */
export type HashtagConnection = {
  __typename?: 'HashtagConnection';
  /** A list of edges. */
  edges: Array<Maybe<HashtagEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type HashtagEdge = {
  __typename?: 'HashtagEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Hashtag>;
};

/** The engagement metrics of a Hashtag. */
export type HashtagEngagementMetrics = Node & {
  __typename?: 'HashtagEngagementMetrics';
  /** The follower metrics of the hashtag. */
  followers?: Maybe<ChannelMetricConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The video metrics of the hashtag. */
  videos?: Maybe<VideoMetricConnection>;
};


/** The engagement metrics of a Hashtag. */
export type HashtagEngagementMetricsVideosArgs = {
  filter?: InputMaybe<VideoFilter>;
};

/** The metrics of a Hashtag. */
export type HashtagMetrics = Node & {
  __typename?: 'HashtagMetrics';
  /** The engagement metrics of a hashtag. */
  engagement?: Maybe<HashtagEngagementMetrics>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The share urls of the hashtag. */
export type HashtagShareUrls = Node & ShareUrls & {
  __typename?: 'HashtagShareUrls';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The permalink share url of the hashtag. */
  permalink: Scalars['String']['output'];
};

/** Represents a Heart (an activity). */
export type Heart = History & Node & {
  __typename?: 'Heart';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The post watched by the channel. */
  post: Post;
};

/** The possible values for a HeartEmoji. */
export enum HeartEmoji {
  /** A like rating that represents the emoji ❤️‍🔥. */
  HeartOnFire = 'HEART_ON_FIRE',
  /** A like rating that represents the emoji 🩷. */
  PinkHeart = 'PINK_HEART'
}

/** The available input fields of a heart emoji operator. */
export type HeartEmojiOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<HeartEmoji>;
};

/** The available input fields for the Heart filter. */
export type HeartFilter = {
  /** Filter hearts by the emoji. */
  emoji?: InputMaybe<HeartEmojiOperator>;
};

/** The node at the end of a HeartMetricEdge. */
export type HeartMetric = Metric & Node & {
  __typename?: 'HeartMetric';
  /** The emoji metric being measured. */
  emoji: HeartEmoji;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the heart metric. A null value indicates that it is hidden or not available. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a HeartMetric. */
export type HeartMetricConnection = {
  __typename?: 'HeartMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<HeartMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type HeartMetricEdge = {
  __typename?: 'HeartMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<HeartMetric>;
};

/** Represents a heart rating. */
export type HeartRating = Node & {
  __typename?: 'HeartRating';
  /** The amount of the heart rating. */
  amount: Scalars['Int']['output'];
  /** The emoji of the heart rating. */
  emoji: HeartEmoji;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

export type HeartRatingInput = {
  /** The number of hearts to use for the rating. */
  amount: Scalars['Int']['input'];
  /** The heart emoji to use for the rating. */
  emoji: HeartEmoji;
};

/** Represents a History. */
export type History = {
  /** The post interacted by the channel. */
  post: Post;
};

/** The connection type for a History. */
export type HistoryConnection = {
  __typename?: 'HistoryConnection';
  /** A list of edges. */
  edges: Array<Maybe<HistoryEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** The history context of the viewer. */
export type HistoryContext = {
  watched?: InputMaybe<IdOperator>;
};

/** An edge in a connection. */
export type HistoryEdge = {
  __typename?: 'HistoryEdge';
  /** The item at the end of the edge. */
  node?: Maybe<History>;
};

/** The available input fields of a History filter. */
export type HistoryFilter = {
  /** Filter history by the activity. */
  activity: ActivityOperator;
  /** Filter history by the post. */
  post: PostOperator;
};

/** The available input fields of a HtmlPage. */
export type HtmlPage = {
  /** The content of the html page. */
  content?: InputMaybe<Scalars['String']['input']>;
  /** The language of the html page. */
  language?: InputMaybe<Scalars['String']['input']>;
  /** The title of the html page. */
  title?: InputMaybe<Scalars['String']['input']>;
  /** The url of the html page. */
  url?: InputMaybe<Scalars['String']['input']>;
};

/** The available input fields of an ID operator. */
export type IdOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<Scalars['ID']['input']>;
  /** Short for in array, must be an element of the array. */
  in?: InputMaybe<Array<Scalars['ID']['input']>>;
  /** Short for not equal, must be different from the given data. */
  ne?: InputMaybe<Scalars['ID']['input']>;
  /** Short for not in array, must NOT be an element of the array. */
  nin?: InputMaybe<Array<Scalars['ID']['input']>>;
};

/** Information about an Apple iOS app. */
export type Ios = Node & {
  __typename?: 'IOS';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The current minimum version. */
  minimum_version: Scalars['String']['output'];
};

/** Information about an iab category. */
export type IabCategory = Category & Node & {
  __typename?: 'IabCategory';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the iab category. */
  name?: Maybe<Scalars['String']['output']>;
  /** The match percentage of the category to the story. */
  percentage?: Maybe<Scalars['Int']['output']>;
  /** The human-readable unique ID of the iab category. */
  slug: Scalars['String']['output'];
};

/** Information of an Image. */
export type Image = Node & {
  __typename?: 'Image';
  /** The height of the image in pixels. If null, the value is unknown. */
  height?: Maybe<Scalars['Int']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The url of the image. */
  url?: Maybe<Scalars['String']['output']>;
  /** The width of the image in pixels. If null, the value is unknown. */
  width?: Maybe<Scalars['Int']['output']>;
};

/** The notification settings on insights to receive. */
export type InsightNotificationSettings = Node & {
  __typename?: 'InsightNotificationSettings';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Receive notifications on your monetization insights. */
  monetization?: Maybe<Scalars['Boolean']['output']>;
};

/** The notifications settings on insights to receive. */
export type InsightNotificationsSettingsInput = {
  /** Indicate whether to Receive notifications on your monetization insights. */
  monetization?: InputMaybe<Scalars['Boolean']['input']>;
};

/** The available input fields of an int operator. */
export type IntOperator = {
  /** Short for greater than or equal to. */
  gte?: InputMaybe<Scalars['Int']['input']>;
  /** Short for lower than or equal to. */
  lte?: InputMaybe<Scalars['Int']['input']>;
};

/** Types that can be an Interaction. */
export type Interaction = Comment | Reaction;

/** A connection to a list of items. */
export type InteractionConnection = {
  __typename?: 'InteractionConnection';
  /** A list of edges. */
  edges: Array<Maybe<InteractionEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type InteractionEdge = {
  __typename?: 'InteractionEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Interaction>;
};

/** Information of an interest. */
export type Interest = Category & Node & {
  __typename?: 'Interest';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The ID of the interest. */
  interestId: Scalars['Int']['output'];
  /** Indicates whether the interest is enabled. */
  isEnabled?: Maybe<Scalars['Boolean']['output']>;
  /** The name of the interest. */
  name: Scalars['String']['output'];
  /** The human-readable unique ID of the interest category. */
  slug: Scalars['String']['output'];
};

/** The connection type for Interest. */
export type InterestConnection = {
  __typename?: 'InterestConnection';
  /** A list of edges. */
  edges: Array<Maybe<InterestEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type InterestEdge = {
  __typename?: 'InterestEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Interest>;
};

/** Information about a language. */
export type Language = Node & {
  __typename?: 'Language';
  /** The ISO 639-1 language code. */
  codeAlpha2?: Maybe<Scalars['String']['output']>;
  /** The ISO 639-2 language code. */
  codeAlpha3?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the language. */
  name?: Maybe<Scalars['String']['output']>;
};

/** The possible sources of a language. */
export enum LanguageSource {
  /** Automatic language detection. */
  Auto = 'AUTO',
  /** The declared language. */
  Custom = 'CUSTOM'
}

/** The node at the end of a LikeEdge. */
export type Like = Bookmark & History & Node & {
  __typename?: 'Like';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The post liked by the channel. */
  post: Post;
  /** Indicates the like rating of the post liked by the channel. */
  rating?: Maybe<LikeRating>;
};

/** The node at the end of a LikesMetricEdge. */
export type LikeMetric = Metric & Node & {
  __typename?: 'LikeMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The like rating metric being measured. */
  rating: LikeRating;
  /** The total count of the like metric. A null value indicates that it is hidden or not available. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a LikesMetric. */
export type LikeMetricConnection = {
  __typename?: 'LikeMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<LikeMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type LikeMetricEdge = {
  __typename?: 'LikeMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<LikeMetric>;
};

/** The available input fields for the Likes engagement metrics filter. */
export type LikeMetricFilter = {
  /** The Likes engagement metrics filter to filter by like. */
  rating?: InputMaybe<LikeRatingOperator>;
};

/** The return fields from adding/removing a like to/from the likes list of the connected user. */
export type LikePayload = {
  __typename?: 'LikePayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The possible values for a LikeRating. */
export enum LikeRating {
  /** A like rating that represents the emoji 🎣. */
  FishingPole = 'FISHING_POLE',
  /**
   * A like rating that represents the emoji 🩷.
   * @deprecated No longer supported.
   */
  PinkHeart = 'PINK_HEART',
  /** A like rating that represents the emoji 😴. */
  SleepingFace = 'SLEEPING_FACE',
  /** A like rating that represents the emoji 😎. */
  SmilingFaceWithSunglasses = 'SMILING_FACE_WITH_SUNGLASSES',
  /** A like rating that represents the emoji 🤩. */
  StarStruck = 'STAR_STRUCK',
  /** A like rating that represents the emoji 😉. */
  WinkingFace = 'WINKING_FACE'
}

/** The available input fields of a like rating operator. */
export type LikeRatingOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<LikeRating>;
};

/** The input fields to like a video for the connected user. */
export type LikeVideoInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the video to like. */
  videoXid: Scalars['String']['input'];
};

/** The return fields from liking a video for the connected user. */
export type LikeVideoPayload = {
  __typename?: 'LikeVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The possible values which liked media connections can be sorted by. */
export enum LikedMediaSort {
  /** Sort liked medias by most recent. */
  Recent = 'RECENT',
  /** Sort liked medias by most viewed. */
  Visited = 'VISITED'
}

/** A live represents a media that is streamed. */
export type Live = Content & Node & Recording & {
  __typename?: 'Live';
  /**
   * Indicates whether the live can be embedded outside of Dailymotion.
   * @deprecated Use `settings.embeddable` field.
   */
  allowEmbed?: Maybe<Scalars['Boolean']['output']>;
  /** The aspect ratio of the media (e.g. 1.33333 for 4/3, 1.77777 for 16/9). */
  aspectRatio?: Maybe<Scalars['Float']['output']>;
  /** Indicates the target audience the live is created for. */
  audience?: Maybe<AudienceGuide>;
  /**
   * The total number of users currently viewing the live. A null value indicates that it is hidden.
   * @deprecated Use `metrics.engagement.audience` field.
   */
  audienceCount?: Maybe<Scalars['Int']['output']>;
  /**
   * The best available quality of the live.
   * @deprecated Use `quality` field.
   */
  bestAvailableQuality?: Maybe<MediaQuality>;
  /** Indicates whether advertisements are allowed on the live. */
  canDisplayAds?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the total number of viewers can be displayed.
   * @deprecated Use `metrics.engagement.audience` field.
   */
  canDisplayAudience?: Maybe<Scalars['Boolean']['output']>;
  /** The categories of the live. */
  categories?: Maybe<CategoryConnection>;
  /**
   * The category of the live.
   * @deprecated Use `categories` field.
   */
  category?: Maybe<MediaCategory>;
  /**
   * The channel that created the live.
   * @deprecated Use `creator` field.
   */
  channel?: Maybe<Channel>;
  /** The channel claiming revenue sharing on the live. */
  claimer?: Maybe<Channel>;
  /** The date and time (ISO 8601 format) when the live was last aired (if never aired, then when it was created). */
  createDate: Scalars['DateTime']['output'];
  /**
   * The last aired date (DateTime ISO8601), if never aired, then the creation date (DateTime ISO8601) of the live.
   * @deprecated Use `createDate` field.
   */
  createdAt?: Maybe<Scalars['DateTime']['output']>;
  /** The creator that created the Live. */
  creator?: Maybe<Channel>;
  /**
   * The curated categories associated to the live.
   * @deprecated Use `interests` field.
   */
  curatedCategories?: Maybe<CuratedCategoryConnection>;
  /**
   * The description of the media in utf8.
   *   Clients are expected to handle '<br/>' tags and detect 'http(s)://' links.
   *   No other HTML tag should be present.
   */
  description?: Maybe<Scalars['String']['output']>;
  /** The embed details of the live. */
  embed?: Maybe<Embed>;
  /**
   * The HTML embedding code to embed the live outside of Dailymotion.
   * @deprecated Use `embed.html` field.
   */
  embedHtml?: Maybe<Scalars['String']['output']>;
  /**
   * The URL to embed the live outside of Dailymotion.
   * @deprecated Use `embed.url` field.
   */
  embedURL?: Maybe<Scalars['String']['output']>;
  /**
   * The date (DateTime ISO8601) the live ends.
   * @deprecated Use `endDate` field.
   */
  endAt?: Maybe<Scalars['DateTime']['output']>;
  /** The date and time (ISO 8601 format) when the live ends. */
  endDate?: Maybe<Scalars['DateTime']['output']>;
  /** The geoblocked countries of the live. */
  geoblockedCountries?: Maybe<GeoblockedCountries>;
  /** The country codes (ISO 3166-1 alpha-2) that are allowed or denied by the live. */
  geoblocking?: Maybe<GeoblockingConnection>;
  /** The height of the live (px). */
  height?: Maybe<Scalars['Int']['output']>;
  /**
   * The URL of the adaptative bitrate manifest using the Apple HTTP Live Streaming
   *   protocol. Without an access token this field contains null, the Dailymotion
   *   user associated with the access token must be the owner of the video. This
   *   field is rate limited. The returned url is secured: it can only be consumed by
   *   the user who made the query and it expires after a certain time.
   * @deprecated Use `hlsUrl` field.
   */
  hlsURL?: Maybe<Scalars['String']['output']>;
  /**
   * The URL of the adaptative bitrate manifest using the Apple HTTP Live Streaming
   *   protocol. Without an access token this field contains null, the Dailymotion
   *   user associated with the access token must be the owner of the video. This
   *   field is rate limited. The returned url is secured: it can only be consumed by
   *   the user who made the query and it expires after a certain time.
   * @deprecated Use `streamUrls.hls` field.
   */
  hlsUrl?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The interests associated to the live.
   * @deprecated No longer supported.
   */
  interests?: Maybe<InterestConnection>;
  /**
   * Indicates whether the live is bookmarked by the connected user.
   *   Returns False if the user is not connected.
   * @deprecated Use `viewerEngagement.bookmarked` field.
   */
  isBookmarked?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the live is "Created for Kids" (intends to target an audience of age 16 and under).
   * @deprecated Use `audience` field.
   */
  isCreatedForKids?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the live is explicit.
   * @deprecated Use `audience` field.
   */
  isExplicit?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the live is in the specified collection. */
  isInCollection?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the live is in the watch later list of the connected user.
   *   Returns False if the user is not connected.
   * @deprecated Use `viewerEngagement.favorited` field.
   */
  isInWatchLater?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the connected user has liked the live.
   * @deprecated Use `viewerEngagement.liked` field.
   */
  isLiked?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the live is on air. */
  isOnAir?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the live is password-protected. */
  isPasswordProtected?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the live is private.
   * @deprecated Use `visibility` field.
   */
  isPrivate?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the live is published. */
  isPublished?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the connected user has reacted to the live.
   *   Returns False if the user is not connected.
   * @deprecated Use `viewerEngagement.reacted` field.
   */
  isReacted?: Maybe<Scalars['Boolean']['output']>;
  /** The language of the live. */
  language?: Maybe<Language>;
  /** The metrics of the live. */
  metrics?: Maybe<LiveMetrics>;
  /** The moderation information of the live. */
  moderation?: Maybe<MediaModeration>;
  /** Indicates whether the live is on air. */
  onair?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the live has paid partnership. */
  paidPartnership?: Maybe<Scalars['Boolean']['output']>;
  /** The quality of the the live. */
  quality?: Maybe<Quality>;
  /** The reactions created on the live. */
  reactions?: Maybe<ReactionConnection>;
  /** The restriction information of the live. */
  restriction?: Maybe<Restriction>;
  /** The settings of the live. */
  settings?: Maybe<LiveSettings>;
  /** The share urls of the live. */
  shareUrls?: Maybe<LiveShareUrls>;
  /**
   * The sharing URLs of the live.
   * @deprecated Use `shareUrls` field.
   */
  sharingURLs?: Maybe<SharingUrlConnection>;
  /**
   * The date (DateTime ISO8601) the live started.
   * @deprecated Use `startDate` field.
   */
  startAt?: Maybe<Scalars['DateTime']['output']>;
  /** The date and time (ISO 8601 format) when the live starts. */
  startDate?: Maybe<Scalars['DateTime']['output']>;
  /**
   * The stats of the live.
   * @deprecated Use `metrics` field.
   */
  stats?: Maybe<LiveStats>;
  /** The stream urls of the live. */
  streamUrls?: Maybe<LiveStreamUrls>;
  /** The subtitles of the live. */
  subtitles?: Maybe<SubtitleConnection>;
  /** The tags of the live. */
  tags?: Maybe<MediaTagConnection>;
  /** The URL of the thumbnail image. */
  thumbnail?: Maybe<Image>;
  /**
   * The URL of the thumbnail image.
   * @deprecated Use `thumbnail` field.
   */
  thumbnailURL?: Maybe<Scalars['String']['output']>;
  /**
   * The thumbnails of the live.
   * @deprecated Use `thumbnailURL` field.
   */
  thumbnails?: Maybe<Thumbnails>;
  /** The title of the live. */
  title?: Maybe<Scalars['String']['output']>;
  /**
   * The topics associated to the live.
   * @deprecated No longer supported.
   */
  topics?: Maybe<TopicConnection>;
  /** The date and time (ISO 8601 format) when the live was updated. */
  updateDate: Scalars['DateTime']['output'];
  /**
   * The updated date (DateTime ISO8601) of the live.
   * @deprecated Use `updateDate` field.
   */
  updatedAt?: Maybe<Scalars['DateTime']['output']>;
  /**
   * The URL of the live.
   * @deprecated Use `shareUrls.permalink` field.
   */
  url?: Maybe<Scalars['String']['output']>;
  /** The viewer engagement information of the live. */
  viewerEngagement?: Maybe<LiveViewerEngagement>;
  /** The visibility of the Live. */
  visibility?: Maybe<Visibility>;
  /** The width of the live (px). */
  width?: Maybe<Scalars['Int']['output']>;
  /** The Dailymotion ID of the live. */
  xid: Scalars['String']['output'];
};


/** A live represents a media that is streamed. */
export type LiveCategoriesArgs = {
  filter: CategoryFilter;
};


/** A live represents a media that is streamed. */
export type LiveCuratedCategoriesArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A live represents a media that is streamed. */
export type LiveGeoblockingArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  isAllowed?: InputMaybe<Scalars['Boolean']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A live represents a media that is streamed. */
export type LiveInterestsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A live represents a media that is streamed. */
export type LiveIsInCollectionArgs = {
  collectionXid: Scalars['String']['input'];
};


/** A live represents a media that is streamed. */
export type LiveLanguageArgs = {
  auto?: Scalars['Boolean']['input'];
};


/** A live represents a media that is streamed. */
export type LiveQualityArgs = {
  auto?: InputMaybe<Scalars['Boolean']['input']>;
};


/** A live represents a media that is streamed. */
export type LiveReactionsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A live represents a media that is streamed. */
export type LiveSharingUrLsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A live represents a media that is streamed. */
export type LiveSubtitlesArgs = {
  auto?: Scalars['Boolean']['input'];
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A live represents a media that is streamed. */
export type LiveTagsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A live represents a media that is streamed. */
export type LiveThumbnailArgs = {
  height: ThumbnailHeight;
};


/** A live represents a media that is streamed. */
export type LiveThumbnailUrlArgs = {
  size: Scalars['String']['input'];
};


/** A live represents a media that is streamed. */
export type LiveTopicsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  whitelistedOnly?: InputMaybe<Scalars['Boolean']['input']>;
};

/** The connection type for Live. */
export type LiveConnection = {
  __typename?: 'LiveConnection';
  /** A list of edges. */
  edges: Array<Maybe<LiveEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type LiveEdge = {
  __typename?: 'LiveEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Live>;
};

/** The engagement metrics of a Live. */
export type LiveEngagementMetrics = Node & PostEngagementMetrics & {
  __typename?: 'LiveEngagementMetrics';
  /** The audience metrics of the live. */
  audience?: Maybe<ChannelMetricConnection>;
  /** The bookmark metrics of the live. */
  bookmarks?: Maybe<BookmarkMetricConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The like metrics of the live. */
  likes?: Maybe<LikeMetricConnection>;
  /** The reaction metrics of the live. */
  reactions?: Maybe<ReactionMetricConnection>;
};


/** The engagement metrics of a Live. */
export type LiveEngagementMetricsBookmarksArgs = {
  filter?: InputMaybe<BookmarkFilter>;
};


/** The engagement metrics of a Live. */
export type LiveEngagementMetricsLikesArgs = {
  filter?: InputMaybe<LikeMetricFilter>;
};

/** The available input fields of a Live filter. */
export type LiveFilter = {
  /** Filter lives by onair. */
  onair?: InputMaybe<BooleanOperator>;
};

/** The node at the end of a LiveMetricEdge. */
export type LiveMetric = Metric & Node & {
  __typename?: 'LiveMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the live metric. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a LiveMetric. */
export type LiveMetricConnection = {
  __typename?: 'LiveMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<LiveMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type LiveMetricEdge = {
  __typename?: 'LiveMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<LiveMetric>;
};

/** The metrics of a Live. */
export type LiveMetrics = Node & PostMetrics & {
  __typename?: 'LiveMetrics';
  /** The engagement metrics of the live. */
  engagement?: Maybe<LiveEngagementMetrics>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

export type LiveSettings = {
  __typename?: 'LiveSettings';
  /** Indicates whether the video can be embedded. */
  embeddable?: Maybe<Scalars['Boolean']['output']>;
  /** Id of the LiveSettings. */
  id?: Maybe<Scalars['ID']['output']>;
};

/** Information about the share urls of a Live. */
export type LiveShareUrls = Node & ShareUrls & {
  __typename?: 'LiveShareUrls';
  /** The facebook share url of the live. */
  facebook?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The permalink share url of the live. */
  permalink: Scalars['String']['output'];
  /** The twitter share url of the live. */
  twitter?: Maybe<Scalars['String']['output']>;
};

/** Information about the live stats. */
export type LiveStats = Node & {
  __typename?: 'LiveStats';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The view stats of the live.
   * @deprecated Use `live.metrics.engagement.audience`.
   */
  views?: Maybe<LiveStatsViews>;
};

/** The view stats of the video. */
export type LiveStatsViews = Node & {
  __typename?: 'LiveStatsViews';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of views of the live. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** Information about the stream urls of a Live. */
export type LiveStreamUrls = Node & StreamUrls & {
  __typename?: 'LiveStreamUrls';
  /** The chromecast url of the video stream. */
  chromecast?: Maybe<Scalars['String']['output']>;
  /** The hls url of the video stream. */
  hls: Scalars['String']['output'];
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The stream urls of a live. */
export type LiveStreams = Node & {
  __typename?: 'LiveStreams';
  /** The chromecast URL of the live stream. */
  chromecastURL?: Maybe<Scalars['String']['output']>;
  /**
   * The URL of the live stream source using the HTTP Live Streaming protocol. Without
   *   an access token this field contains null. The Dailymotion user associated with
   *   the access token must be the owner of the video. This field is rate limited.
   *   The returned url is secured: it can only be consumed by the user who made the
   *   query and it expires after a certain time.
   */
  hlsSourceURL?: Maybe<Scalars['String']['output']>;
  /**
   * The URL of the adaptative bitrate manifest using the Apple HTTP Live Streaming
   *   protocol. Without an access token this field contains null, the Dailymotion
   *   user associated with the access token must be the owner of the video. This
   *   field is rate limited. The returned url is secured: it can only be consumed by
   *   the user who made the query and it expires after a certain time.
   */
  hlsURL?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The restriction information of the live stream.
   * @deprecated Use `live.restriction`.
   */
  restriction?: Maybe<Restriction>;
  /** The Dailymotion ID of a live stream. */
  xid: Scalars['String']['output'];
};

/** The connection type for Live Stream. */
export type LiveStreamsConnection = {
  __typename?: 'LiveStreamsConnection';
  /** A list of edges. */
  edges: Array<Maybe<LiveStreamsEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type LiveStreamsEdge = {
  __typename?: 'LiveStreamsEdge';
  /** The item at the end of the edge. */
  node?: Maybe<LiveStreams>;
};

/** Information about the viewer engagement of a Live. */
export type LiveViewerEngagement = Node & ViewerEngagement & {
  __typename?: 'LiveViewerEngagement';
  /** Indicates whether the live is bookmarked by the viewer. Returns False if the viewer is not connected. */
  bookmarked?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the viewer has the live in its watch later list. Returns False if the viewer is not connected. */
  favorited?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates the heart likeness the viewer has given to the Live. */
  hearted?: Maybe<Hearted>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * Indicates the like rating of the live from the viewer.
   * @deprecated Use `hearted` with `points`.
   */
  likeRating?: Maybe<LikeRating>;
  /** Indicates whether the viewer has liked the comment. Returns False if the viewer is not connected. */
  liked?: Maybe<Scalars['Boolean']['output']>;
  /** The amount of points given from the viewer to the Live. */
  points?: Maybe<Scalars['Int']['output']>;
  /** Indicates whether the viewer has reacted to the live. Returns False if the viewer is not connected. */
  reacted?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the viewer has added the live to one of its collections. Returns False if the viewer is not connected. */
  saved?: Maybe<Scalars['Boolean']['output']>;
};

/** Information about the localization. */
export type Localization = Node & {
  __typename?: 'Localization';
  /** The list of countries that have fallback. */
  fallbackCountries?: Maybe<FallbackCountryConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The localization of the connected user. */
  me?: Maybe<LocalizationMe>;
  /** The countries that are supported. */
  supportedCountries?: Maybe<SupportedCountryConnection>;
  /** The languages that are supported. */
  supportedLanguages?: Maybe<SupportedLanguageConnection>;
};


/** Information about the localization. */
export type LocalizationFallbackCountriesArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about the localization. */
export type LocalizationSupportedCountriesArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about the localization. */
export type LocalizationSupportedLanguagesArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** Information about the localization of the connected user. */
export type LocalizationMe = Node & {
  __typename?: 'LocalizationMe';
  /** The country of the connected user. */
  country?: Maybe<Country>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The subdivision of the connected user. */
  subdivision?: Maybe<Subdivision>;
};

/** Types that can be a Media. */
export type Media = Live | Video;

/** The possible categories for a media. */
export enum MediaCategory {
  /** The animals category. */
  Animals = 'ANIMALS',
  /** The auto category. */
  Auto = 'AUTO',
  /** The creation category. */
  Creation = 'CREATION',
  /** The fun category. */
  Fun = 'FUN',
  /** The kids category. */
  Kids = 'KIDS',
  /** The lifestyle category. */
  Lifestyle = 'LIFESTYLE',
  /** The music category. */
  Music = 'MUSIC',
  /** The news category. */
  News = 'NEWS',
  /** The people category. */
  People = 'PEOPLE',
  /** The school category. */
  School = 'SCHOOL',
  /** The shortfilms category. */
  Shortfilms = 'SHORTFILMS',
  /** The sport category. */
  Sport = 'SPORT',
  /** The tech category. */
  Tech = 'TECH',
  /** The travel category. */
  Travel = 'TRAVEL',
  /** The tv category. */
  Tv = 'TV',
  /** The videogames category. */
  Videogames = 'VIDEOGAMES',
  /** The webcam category. */
  Webcam = 'WEBCAM'
}

/** The connection type for Media. */
export type MediaConnection = {
  __typename?: 'MediaConnection';
  /** A list of edges. */
  edges: Array<Maybe<MediaEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type MediaEdge = {
  __typename?: 'MediaEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Media>;
};

/** The moderation information of a media. */
export type MediaModeration = Node & {
  __typename?: 'MediaModeration';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The date and time (ISO 8601 format) when the media was reviewed. */
  reviewDate?: Maybe<Scalars['DateTime']['output']>;
  /**
   * The reviewed date (DateTime ISO8601) of the media.
   * @deprecated Use `reviewDate` field.
   */
  reviewedAt?: Maybe<Scalars['DateTime']['output']>;
};

/** Information about the media publishing. */
export type MediaPublishingInfo = Node & {
  __typename?: 'MediaPublishingInfo';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The percentage of media publishing progress. */
  percentage?: Maybe<Scalars['Int']['output']>;
};

/** The possible qualities for a media. */
export enum MediaQuality {
  /** HD 720p quality. */
  Hd720P = 'HD720P',
  /** HD 720p60 quality. */
  Hd720P60 = 'HD720P60',
  /** HD 1080p quality. */
  Hd1080P = 'HD1080P',
  /** HD 1080p60 quality. */
  Hd1080P60 = 'HD1080P60',
  /** HQ 480p quality. */
  Hq480P = 'HQ480P',
  /** LD 240p quality. */
  Ld240P = 'LD240P',
  /** SD 384p quality. */
  Sd384P = 'SD384P',
  /** UHD 1440p quality. */
  Uhd1440P = 'UHD1440P',
  /** UHD 1440p60 quality. */
  Uhd1440P60 = 'UHD1440P60',
  /** UHD 2160p quality. */
  Uhd2160P = 'UHD2160P',
  /** UHD 2160p60 quality. */
  Uhd2160P60 = 'UHD2160P60'
}

/** Types that can be a MediaStreams. */
export type MediaStreams = LiveStreams | VideoStreams;

/** The connection type for MediaStreams. */
export type MediaStreamsConnection = {
  __typename?: 'MediaStreamsConnection';
  /** A list of edges. */
  edges: Array<Maybe<MediaStreamsEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type MediaStreamsEdge = {
  __typename?: 'MediaStreamsEdge';
  /** The item at the end of the edge. */
  node?: Maybe<MediaStreams>;
};

/** Information about the tag of a media. */
export type MediaTag = Node & {
  __typename?: 'MediaTag';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The label of the tag. */
  label?: Maybe<Scalars['String']['output']>;
};

/** The connection type for Media Tag. */
export type MediaTagConnection = {
  __typename?: 'MediaTagConnection';
  /** A list of edges. */
  edges: Array<Maybe<MediaTagEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type MediaTagEdge = {
  __typename?: 'MediaTagEdge';
  /** The item at the end of the edge. */
  node?: Maybe<MediaTag>;
};

/** The possible types for a media. */
export enum MediaType {
  /** A media that represents a `Live`. */
  Live = 'LIVE',
  /** A media that represents a `Video`. */
  Video = 'VIDEO'
}

/** Information about the media uploading. */
export type MediaUploadInfo = Node & {
  __typename?: 'MediaUploadInfo';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Information about the media publishing. */
  publishing?: Maybe<MediaPublishingInfo>;
};

/** Body of a message. */
export type MessageBody = {
  /** ID of the story to thank. */
  storyId?: InputMaybe<Scalars['ID']['input']>;
};

/** The possible values for a Message Subject. */
export enum MessageSubject {
  /** A thank you message. */
  ThankYou = 'THANK_YOU'
}

/** Information about a metadata. */
export type Metadata = Node & {
  __typename?: 'Metadata';
  /** Information about the algorithm used to retrieve data. */
  algorithm?: Maybe<MetadataAlgorithm>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** Information about the metadata algorithm. */
export type MetadataAlgorithm = Algorithm & {
  __typename?: 'MetadataAlgorithm';
  /** The name of the algorithm. */
  name?: Maybe<Scalars['String']['output']>;
  /** The unique ID of the algorithm. */
  uuid?: Maybe<Scalars['String']['output']>;
  /** The version of the algorithm. */
  version?: Maybe<Scalars['String']['output']>;
};

/** Information about a metric. */
export type Metric = {
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the metric being measured. */
  total?: Maybe<Scalars['Int']['output']>;
};

/**
 *
 * Information about an performed moderation action
 *
 */
export type ModerationAction = {
  __typename?: 'ModerationAction';
  /** The date when the moderation action was performed. */
  date: Scalars['Date']['output'];
  /** The reference number of the moderation action. */
  referenceNumber: Scalars['String']['output'];
};

/** Input for < Mutation.moderationActionAppeal > */
export type ModerationActionAppealInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** Extra user comments (limited to 1024 chars). */
  comment?: InputMaybe<Scalars['String']['input']>;
  /** Reason of appeal. */
  reason: AppealReason;
  /** Appeal request token came from moderation notification emails */
  token: Scalars['String']['input'];
};

/** Response of < Mutation.moderationActionAppeal > */
export type ModerationActionAppealPayload = {
  __typename?: 'ModerationActionAppealPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status: Status;
};

/** MojoKit queries for AI-powered content enhancement features. */
export type MojoKitQueries = {
  __typename?: 'MojoKitQueries';
  /** Convert speech from audio to text with timings. */
  convertSpeechFromAudioToTextWithTimings: ConvertSpeechFromAudioToTextResponse;
  /** Generate enriched elements for sentences. */
  generateEnrichedElementsForSentences: EnrichedElementsForSentences;
};


/** MojoKit queries for AI-powered content enhancement features. */
export type MojoKitQueriesConvertSpeechFromAudioToTextWithTimingsArgs = {
  input: SpeechAudioInput;
  timeoutMs?: InputMaybe<Scalars['Int']['input']>;
  withProvider?: InputMaybe<SpeechToTextProvider>;
};


/** MojoKit queries for AI-powered content enhancement features. */
export type MojoKitQueriesGenerateEnrichedElementsForSentencesArgs = {
  sentences: Array<Scalars['String']['input']>;
  targetLanguage: Scalars['String']['input'];
};

/** Monetization insights notification settings. */
export type MonetizationInsights = Node & {
  __typename?: 'MonetizationInsights';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the email notification setting is enabled. */
  isEmailEnabled?: Maybe<Scalars['Boolean']['output']>;
};

/** The mutation root of Dailymotion's GraphQL API. */
export type Mutation = {
  __typename?: 'Mutation';
  /**
   * Activate a user by its validation key.
   * @deprecated Use `/oauth/token` endpoint with grant_type `validate_code`.
   */
  activateUser?: Maybe<ActivateUserPayload>;
  /** Add a creator to the blocklist. */
  addBlocked?: Maybe<AddBlockedPayload>;
  /** Add a boost event. */
  addBoost?: Maybe<AddBoostPayload>;
  /**
   * Add a video to a collection.
   * @deprecated Use mutation `addSave`.
   */
  addCollectionVideo?: Maybe<AddCollectionVideoPayload>;
  /** Add a `Favorite` to the favorites list of the connected user. */
  addFavorite?: Maybe<FavoritePayload>;
  /** Follow a story for the connected creator. */
  addFollowing?: Maybe<FollowingPayload>;
  /** Add a post with a rating to the likes list of the connected user. If the post is already rated, it updates the rating. */
  addLike?: Maybe<LikePayload>;
  /** Save a recording to a collection of the authenticated creator. */
  addSave?: Maybe<SavePayload>;
  /**
   * Add a video to the `Watch Later` list of the connected user.
   * @deprecated Use mutation `addFavorite`.
   */
  addWatchLaterVideo?: Maybe<AddWatchLaterVideoPayload>;
  /** Add a `Watched` to the watched list of the connected user. */
  addWatched?: Maybe<WatchedPayload>;
  /** Create an analytics report. */
  analyticsReportCreate?: Maybe<AnalyticsReportCreatePayload>;
  /** Ask to generate a custom report. */
  askPartnerReportFile?: Maybe<AskPartnerReportFilePayload>;
  /**
   *
   *   Authorize a device using a user code and consent.
   *
   */
  authorizeDevice?: Maybe<AuthorizeDevicePayload>;
  /** Request to change the email address of the connected user. */
  changeEmail?: Maybe<ChangeEmailPayload>;
  /** Create a channel. */
  channelCreate?: Maybe<ChannelCreatePayload>;
  /** Clear (remove all saves from) a collection. */
  clearCollection?: Maybe<CollectionPayload>;
  /**
   * Remove all medias from a collection.
   * @deprecated Use mutation `clearCollection`.
   */
  clearCollectionMedias?: Maybe<ClearCollectionMediasPayload>;
  /** Remove all `Favorites` from the favorites list of the connected user. */
  clearFavorites?: Maybe<FavoritePayload>;
  /** Removes all the videos the connected user has liked. */
  clearLikedVideos?: Maybe<ClearLikedVideosPayload>;
  /**
   * Removes all the videos from the `WatchLater` list of the connected user.
   * @deprecated Use mutation `clearFavorites`.
   */
  clearWatchLaterVideos?: Maybe<ClearWatchLaterVideosPayload>;
  /** Remove all `Watched` from the watched list of the connected user. */
  clearWatched?: Maybe<WatchedPayload>;
  /**
   * Removes all the videos from the user `Watched`.
   * @deprecated Use mutation `clearWatched`.
   */
  clearWatchedVideos?: Maybe<ClearWatchedVideosPayload>;
  /** Confirm the new email address of the connected user. */
  confirmEmail?: Maybe<ConfirmEmailPayload>;
  /** Confirm the submission of the report via email. */
  confirmReport: ConfirmReportPayload;
  /** Create a new rule for feature flipping or AB experiments. */
  createBehaviorRule?: Maybe<CreateBehaviorRulePayload>;
  /** Create a chatroom. */
  createChatroom?: Maybe<CreateChatroomPayload>;
  /** Create a collection. */
  createCollection?: Maybe<CreateCollectionPayload>;
  /** Create a comment. */
  createComment?: Maybe<CreateCommentPayload>;
  /** Create a reaction in a recording format to respond to a story. */
  createReaction?: Maybe<ReactionPayload>;
  /** Creates a user. */
  createUser?: Maybe<CreateUserPayload>;
  /** Create a video. */
  createVideo?: Maybe<CreateVideoPayload>;
  /** Delete a rule used for feature flipping or AB experiments. */
  deleteBehaviorRule?: Maybe<DeleteBehaviorRulePayload>;
  /** Delete a collection. */
  deleteCollection?: Maybe<CollectionPayload>;
  /** Delete a comment. */
  deleteComment?: Maybe<DeleteCommentPayload>;
  /** Delete a reaction. */
  deleteReaction?: Maybe<DeleteReactionPayload>;
  /** Delete a user. */
  deleteUser?: Maybe<DeleteUserPayload>;
  /** Delete a video. */
  deleteVideo?: Maybe<DeleteVideoPayload>;
  /**
   * Follow a channel for the connected user.
   * @deprecated Use mutation `addFollowing`.
   */
  followChannel?: Maybe<FollowChannelPayload>;
  /**
   * Follow multiple channels for the connected user.
   * @deprecated No longer supported.
   */
  followChannels?: Maybe<FollowChannelsPayload>;
  /**
   * The topic the user wants to follow.
   * @deprecated No longer supported.
   */
  followTopic?: Maybe<FollowTopicPayload>;
  /**
   * Follow multiple topics for the connected user.
   * @deprecated No longer supported.
   */
  followTopics?: Maybe<FollowTopicsPayload>;
  /**
   * Follow a user for the connected user.
   * @deprecated No longer supported.
   */
  followedUserAdd?: Maybe<FollowUserPayload>;
  /**
   * Unfollow a user for the connected user.
   * @deprecated No longer supported.
   */
  followedUserRemove?: Maybe<UnfollowUserPayload>;
  /** Generate a new username for the channel. */
  generateChannelUsername?: Maybe<GenerateChannelUsernamePayload>;
  /** Generate an access token for a chatroom. */
  generateChatroomToken: ChatroomTokenPayload;
  /** Generate a URL to upload a file. */
  generateFileUploadUrl?: Maybe<GenerateFileUploadUrlPayload>;
  /** Generate a token to request a code to verify the email. */
  generateVerifyEmailToken?: Maybe<GenerateVerifyEmailTokenPayload>;
  /**
   * Like a video for the connected user.
   * @deprecated Use mutation `addLike`.
   */
  likeVideo?: Maybe<LikeVideoPayload>;
  /**
   *
   *   Submit an appeal.
   *   It may raise following GraphQL errors:
   *   - token is invalid (type=bad_request, reason=invalid_token)
   *   - token is expired (type=bad_reqeust, reason=token_expired)
   *   - appeal already exists (type=bad_reqeust, reason=appeal_already_exists)
   *   - appeal is invalid (type=bad_reqeust, reason=invalid_appeal)
   *   - appeal in review (type=bad_reqeust, reason=appeal_in_review)
   *
   */
  moderationActionAppeal?: Maybe<ModerationActionAppealPayload>;
  /** Update the push notification settings on a followed channel of the connected user. */
  notificationFollowedChannelUpdate?: Maybe<NotificationFollowedChannelUpdatePayload>;
  /** Manage poll answer for the connected user. */
  pollAnswer?: Maybe<PollAnswerPayload>;
  /** Rate a recommendation. */
  rateRecommendation?: Maybe<RateRecommendationPayload>;
  /**
   * Respond to a video by creating a reaction video.
   * @deprecated Use mutation `createReaction`.
   */
  reactionVideoCreate?: Maybe<ReactionVideoPayload>;
  /**
   * Delete a reaction video.
   * @deprecated Use mutation `deleteReaction`.
   */
  reactionVideoDelete?: Maybe<ReactionVideoDeletePayload>;
  /**
   * Update information about a reaction video.
   * @deprecated Use mutation `updateReaction`.
   */
  reactionVideoUpdate?: Maybe<ReactionVideoPayload>;
  /** Request to recover the password of a user. */
  recoverPassword?: Maybe<RecoverPasswordPayload>;
  /** Remove a creator from the blocklist. */
  removeBlocked?: Maybe<RemoveBlockedPayload>;
  /**
   * Delete a collection.
   * @deprecated Use mutation `deleteCollection`.
   */
  removeCollection?: Maybe<RemoveCollectionPayload>;
  /**
   * Remove a video from a collection.
   * @deprecated Use mutation `removeSave`.
   */
  removeCollectionVideo?: Maybe<RemoveCollectionVideoPayload>;
  /** Remove a `Favorite` from the favorites list of the connected user. */
  removeFavorite?: Maybe<FavoritePayload>;
  /** Unfollow a story for the connected creator. */
  removeFollowing?: Maybe<FollowingPayload>;
  /** Remove a post from the likes list of the connected user. */
  removeLike?: Maybe<LikePayload>;
  /** Remove a recording from a collection of the authenticated creator. */
  removeSave?: Maybe<SavePayload>;
  /**
   * Removes a video from the `WatchLater` list of the connected user.
   * @deprecated Use mutation `removeFavorite`.
   */
  removeWatchLaterVideo?: Maybe<RemoveWatchLaterVideoPayload>;
  /** Remove a `Watched` from the watched list of the connected user. */
  removeWatched?: Maybe<WatchedPayload>;
  /**
   * Removes a video from the `Watched` list of the connected user.
   * @deprecated Use mutation `removeWatched`.
   */
  removeWatchedVideo?: Maybe<RemoveWatchedVideoPayload>;
  /**
   * Reorder a media in a collection.
   * @deprecated Use mutation `reorderSave`.
   */
  reorderCollectionMedia?: Maybe<ReorderCollectionMediaPayload>;
  /** Remove a recording from a collection of the authenticated creator. */
  reorderSave?: Maybe<SavePayload>;
  /** Report a comment for violating the community guidelines. */
  reportComment: ReportCommentPayload;
  /** Report content that is violating the community guidelines. */
  reportContent?: Maybe<ReportStoryPayload>;
  /** Report a creator for violating the community guidelines. */
  reportCreator: ReportCreatorPayload;
  /** Report a Recording (a Video, a Live, or a Reaction) that is violating the community guidelines. */
  reportRecording?: Maybe<ReportRecordingPayload>;
  /**
   * Report an inappropriate video.
   * @deprecated Use mutation `reportRecording`.
   */
  reportVideo?: Maybe<ReportVideoPayload>;
  /**
   * Verify the email of the reporter, if the reporter is not connected.
   * @deprecated Use `confirmReport`.
   */
  reporterEmailVerify: ReporterEmailVerifyPayload;
  /**
   * Generate an activation code by a validation token.
   * @deprecated Use `sendVerifyEmailCode`.
   */
  requestActivationCode?: Maybe<RequestActivationCodePayload>;
  /** Change the password of the user after requesting recover password. */
  resetPassword?: Maybe<ResetPasswordPayload>;
  /** Request a new email confirmation code. */
  sendConfirmEmailCode?: Maybe<SendConfirmEmailCodePayload>;
  /** Send a message. */
  sendMessage?: Maybe<SendMessagePayload>;
  /** Send a transactional email using an email provider. */
  sendTransactionalEmail?: Maybe<SendTransactionalEmailPayload>;
  /** Request a code to be sent to verify the email. */
  sendVerifyEmailCode?: Maybe<SendVerifyEmailCodePayload>;
  /**
   * Unfollow a channel for the connected user.
   * @deprecated Use mutation `removeFollowing`.
   */
  unfollowChannel?: Maybe<UnfollowChannelPayload>;
  /**
   * Unfollow a topic for the connected user.
   * @deprecated No longer supported.
   */
  unfollowTopic?: Maybe<UnfollowTopicPayload>;
  /**
   * Unlike a video for the connected user.
   * @deprecated Use mutation `removeLike`.
   */
  unlikeVideo?: Maybe<UnlikeVideoPayload>;
  /** Update a rule used for feature flipping or AB experiments. */
  updateBehaviorRule?: Maybe<UpdateBehaviorRulePayload>;
  /** Update a channel. */
  updateChannel?: Maybe<UpdateChannelPayload>;
  /** Update the settings of the connected Channel. */
  updateChannelSettings?: Maybe<ChannelSettingsPayload>;
  /** Update a collection. */
  updateCollection?: Maybe<UpdateCollectionPayload>;
  /**
   * Update the email notification settings of the connected user.
   * @deprecated Use mutation `updateChannelSettings` and input arg `notifications`.
   */
  updateNotificationSettingsEmail?: Maybe<UpdateNotificationSettingsEmailPayload>;
  /**
   * Update the push notification settings of the connected user.
   * @deprecated Use mutation `updateChannelSettings` and input arg `notifications`.
   */
  updateNotificationSettingsPush?: Maybe<UpdateNotificationSettingsPushPayload>;
  /** Update a reaction. */
  updateReaction?: Maybe<ReactionPayload>;
  /** Update information about the current user connected. */
  updateUser?: Maybe<UpdateUserPayload>;
  /** Update a video. */
  updateVideo?: Maybe<UpdateVideoPayload>;
  /**
   * Confirm the new email address of the connected user.
   * @deprecated Use mutation `confirmEmail`.
   */
  userEmailChangeConfirm?: Maybe<UserEmailChangeConfirmPayload>;
  /**
   * Request to change the email address of the connected user.
   * @deprecated Use mutation `changeEmail`.
   */
  userEmailChangeRequest?: Maybe<UserEmailChangeRequestPayload>;
  /**
   * Request a new email confirmation code.
   * @deprecated Use mutation `sendConfirmEmailCode`.
   */
  userEmailConfirmationCodeReset?: Maybe<UserEmailConfirmationCodeResetPayload>;
  /**
   * Generate an email validation token to request an activation code.
   * @deprecated Use `generateVerifyEmailToken`.
   */
  userEmailValidationTokenRequest?: Maybe<UserEmailValidationTokenPayload>;
  /**
   * Add an interest to user.
   * @deprecated No longer supported.
   */
  userInterestAdd?: Maybe<UserInterestAddPayload>;
  /**
   * Remove an interest from a user.
   * @deprecated No longer supported.
   */
  userInterestRemove?: Maybe<UserInterestRemovePayload>;
  /**
   * Replaces the interests of a user with the ids provided.
   * @deprecated No longer supported.
   */
  userInterestsUpdate?: Maybe<UserInterestsUpdatePayload>;
  /**
   * Request a code B from OpenWeb.
   * @deprecated No longer supported.
   */
  userOpenWebCodeBRequest?: Maybe<UserOpenWebCodeBRequestPayload>;
  /**
   * Add a video to the `Watched` list of the connected user.
   * @deprecated Use mutation `addWatched`.
   */
  watchedVideoAdd?: Maybe<WatchedVideoAddPayload>;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationActivateUserArgs = {
  input: ActivateUserInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAddBlockedArgs = {
  input: AddBlockedInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAddBoostArgs = {
  input: AddBoostInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAddCollectionVideoArgs = {
  input: AddCollectionVideoInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAddFavoriteArgs = {
  input: FavoriteInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAddFollowingArgs = {
  input: FollowingInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAddLikeArgs = {
  input: AddLikeInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAddSaveArgs = {
  input: SaveInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAddWatchLaterVideoArgs = {
  input: AddWatchLaterVideoInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAddWatchedArgs = {
  input: AddWatchedInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAnalyticsReportCreateArgs = {
  input: AnalyticsReportCreateInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAskPartnerReportFileArgs = {
  input: AskPartnerReportFileInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationAuthorizeDeviceArgs = {
  input: AuthorizeDeviceInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationChangeEmailArgs = {
  input: ChangeEmailInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationChannelCreateArgs = {
  input: ChannelCreateInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationClearCollectionArgs = {
  input: CollectionInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationClearCollectionMediasArgs = {
  input: ClearCollectionMediasInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationClearLikedVideosArgs = {
  input: ClearLikedVideosInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationClearWatchLaterVideosArgs = {
  input: ClearWatchLaterVideosInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationClearWatchedVideosArgs = {
  input: ClearWatchedVideosInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationConfirmEmailArgs = {
  input: ConfirmEmailInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationConfirmReportArgs = {
  input: ConfirmReportInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationCreateBehaviorRuleArgs = {
  input: CreateBehaviorRuleInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationCreateChatroomArgs = {
  input: CreateChatroomInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationCreateCollectionArgs = {
  input: CreateCollectionInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationCreateCommentArgs = {
  input: CreateCommentInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationCreateReactionArgs = {
  input: CreateReactionInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationCreateUserArgs = {
  input: CreateUserInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationCreateVideoArgs = {
  input: CreateVideoInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationDeleteBehaviorRuleArgs = {
  input: DeleteBehaviorRuleInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationDeleteCollectionArgs = {
  input: CollectionInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationDeleteCommentArgs = {
  input: DeleteCommentInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationDeleteReactionArgs = {
  input: DeleteReactionInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationDeleteUserArgs = {
  input: DeleteUserInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationDeleteVideoArgs = {
  input: DeleteVideoInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationFollowChannelArgs = {
  input: FollowChannelInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationFollowChannelsArgs = {
  input: FollowChannelsInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationFollowTopicArgs = {
  input: FollowTopicInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationFollowTopicsArgs = {
  input: FollowTopicsInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationFollowedUserAddArgs = {
  input: FollowUserInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationFollowedUserRemoveArgs = {
  input: UnfollowUserInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationGenerateChannelUsernameArgs = {
  input: GenerateChannelUsernameInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationGenerateFileUploadUrlArgs = {
  input: GenerateFileUploadUrlInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationGenerateVerifyEmailTokenArgs = {
  input: GenerateVerifyEmailTokenInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationLikeVideoArgs = {
  input: LikeVideoInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationModerationActionAppealArgs = {
  input: ModerationActionAppealInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationNotificationFollowedChannelUpdateArgs = {
  input: NotificationFollowedChannelUpdateInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationPollAnswerArgs = {
  input: PollAnswerInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRateRecommendationArgs = {
  input: RateRecommendationInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationReactionVideoCreateArgs = {
  input: ReactionVideoCreateInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationReactionVideoDeleteArgs = {
  input: ReactionVideoDeleteInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationReactionVideoUpdateArgs = {
  input: ReactionVideoUpdateInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRecoverPasswordArgs = {
  input: RecoverPasswordInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRemoveBlockedArgs = {
  input: RemoveBlockedInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRemoveCollectionArgs = {
  input: RemoveCollectionInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRemoveCollectionVideoArgs = {
  input: RemoveCollectionVideoInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRemoveFavoriteArgs = {
  input: FavoriteInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRemoveFollowingArgs = {
  input: FollowingInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRemoveLikeArgs = {
  input: RemoveLikeInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRemoveSaveArgs = {
  input: SaveInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRemoveWatchLaterVideoArgs = {
  input: RemoveWatchLaterVideoInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRemoveWatchedArgs = {
  input: RemoveWatchedInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRemoveWatchedVideoArgs = {
  input: RemoveWatchedVideoInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationReorderCollectionMediaArgs = {
  input: ReorderCollectionMediaInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationReorderSaveArgs = {
  input: ReorderSaveInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationReportCommentArgs = {
  input: ReportCommentInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationReportContentArgs = {
  input: ReportContentInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationReportCreatorArgs = {
  input: ReportCreatorInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationReportRecordingArgs = {
  input: ReportRecordingInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationReportVideoArgs = {
  input: ReportVideoInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationReporterEmailVerifyArgs = {
  input: ReporterEmailVerifyInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationRequestActivationCodeArgs = {
  input: RequestActivationCodeInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationResetPasswordArgs = {
  input: ResetPasswordInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationSendMessageArgs = {
  input: SendMessageInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationSendTransactionalEmailArgs = {
  input: SendTransactionalEmailInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationSendVerifyEmailCodeArgs = {
  input: SendVerifyEmailCodeInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUnfollowChannelArgs = {
  input: UnfollowChannelInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUnfollowTopicArgs = {
  input: UnfollowTopicInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUnlikeVideoArgs = {
  input: UnlikeVideoInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUpdateBehaviorRuleArgs = {
  input: UpdateBehaviorRuleInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUpdateChannelArgs = {
  input: UpdateChannelInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUpdateChannelSettingsArgs = {
  input: ChannelSettingsInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUpdateCollectionArgs = {
  input: UpdateCollectionInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUpdateNotificationSettingsEmailArgs = {
  input: UpdateNotificationSettingsEmailInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUpdateNotificationSettingsPushArgs = {
  input: UpdateNotificationSettingsPushInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUpdateReactionArgs = {
  input: UpdateReactionInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUpdateUserArgs = {
  input: UpdateUserInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUpdateVideoArgs = {
  input: UpdateVideoInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUserEmailChangeConfirmArgs = {
  input: UserEmailChangeConfirmInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUserEmailChangeRequestArgs = {
  input: UserEmailChangeRequestInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUserEmailConfirmationCodeResetArgs = {
  input: UserEmailConfirmationCodeResetInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUserEmailValidationTokenRequestArgs = {
  input: UserEmailValidationTokenInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUserInterestAddArgs = {
  input: UserInterestAddInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUserInterestRemoveArgs = {
  input: UserInterestRemoveInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUserInterestsUpdateArgs = {
  input: UserInterestsUpdateInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationUserOpenWebCodeBRequestArgs = {
  input: UserOpenWebCodeBRequestInput;
};


/** The mutation root of Dailymotion's GraphQL API. */
export type MutationWatchedVideoAddArgs = {
  input: WatchedVideoAddInput;
};

/** The neon object represents the view of NEON apps. */
export type Neon = Node & {
  __typename?: 'Neon';
  android?: Maybe<Android>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  ios?: Maybe<Ios>;
  /** The sections in the neon view. */
  sections?: Maybe<SectionConnection>;
  /** Information about the URI passed as argument. */
  web?: Maybe<Web>;
};


/** The neon object represents the view of NEON apps. */
export type NeonAndroidArgs = {
  version: Scalars['String']['input'];
};


/** The neon object represents the view of NEON apps. */
export type NeonIosArgs = {
  name: Scalars['String']['input'];
  version: Scalars['String']['input'];
};


/** The neon object represents the view of NEON apps. */
export type NeonSectionsArgs = {
  context?: InputMaybe<SectionContextArgument>;
  device?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  followingChannelXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  followingTopicXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  page?: InputMaybe<Scalars['Int']['input']>;
  space: Scalars['String']['input'];
  watchedVideoXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


/** The neon object represents the view of NEON apps. */
export type NeonWebArgs = {
  uri: Scalars['String']['input'];
};

/** The possible values which network channel connections can be sorted by. */
export enum NetworkChannelsSort {
  /** Sort network channels by number of followers. */
  Popular = 'POPULAR',
  /** Sort network channels by most recent. */
  Recent = 'RECENT'
}

/** Represents a node with an ID. */
export type Node = {
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The input fields to update the push notification settings on a followed channel of the connected user. */
export type NotificationFollowedChannelUpdateInput = {
  /** The Dailymotion ID of the channel. */
  channelXid: Scalars['String']['input'];
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** Indicate whether to enable the notification of the channel. */
  isEnabled: Scalars['Boolean']['input'];
};

/** The return fields from updating the push notification settings on a followed channel of the connected user. */
export type NotificationFollowedChannelUpdatePayload = {
  __typename?: 'NotificationFollowedChannelUpdatePayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The notification settings. */
export type NotificationSettings = Node & {
  __typename?: 'NotificationSettings';
  /** The settings to receive email notifications. */
  email?: Maybe<EmailNotificationSettings>;
  /**
   * The notification settings to receive when a channel the connected user follows starts a live.
   * @deprecated Use `me.channel.settings.notifications.email.activity.followingChannelStartsLive`.
   */
  followingChannelStartsLive?: Maybe<FollowingChannelStartsLive>;
  /**
   * The notification settings to receive when a channel the connected user follows uploads a new video.
   * @deprecated Use `me.channel.settings.notifications.email.activity.followingChannelUploadsVideo`.
   */
  followingChannelUploadsVideo?: Maybe<FollowingChannelUploadsVideo>;
  /**
   * The notification settings to receive when a channel or topic the connected user follows starts a live.
   * @deprecated Use `me.channel.settings.notifications.push.activity.followingChannelStartsLive`.
   */
  followingStartsLive?: Maybe<FollowingStartsLive>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicate whether to receive occasionally about monetization insights. */
  monetizationInsights?: Maybe<MonetizationInsights>;
  /**
   * The notification settings to receive when there are new feature and product updates.
   * @deprecated Use `me.channel.settings.notifications.email.announcements.updates`.
   */
  productUpdates?: Maybe<ProductUpdates>;
  /** The settings to receive push notifications. */
  push?: Maybe<PushNotificationSettings>;
  /**
   * The notification settings to receive when the connected user has unwatched vidoes in the `WatchLater` list.
   * @deprecated Use `me.channel.settings.notifications.< format >.recommendations.bookmarkReminders`.
   */
  remindUnwatchedVideos?: Maybe<RemindUnwatchedVideos>;
  /**
   * The notification settings to receive occasionally about `tips and tricks`.
   * @deprecated Use `me.channel.settings.notifications.< format >.announcement.tips`.
   */
  tips?: Maybe<Tips>;
  /**
   * The notification settings to receive occasionally about `curated videos for you`.
   * @deprecated Use `me.channel.settings.notifications.< format >.recommendations.personalization`.
   */
  videoDigest?: Maybe<VideoDigest>;
};

/** Update the notification settings to receive. */
export type NotificationSettingsInput = {
  /** The notification settings to receive via email. */
  email?: InputMaybe<EmailNotificationSettingsInput>;
  /** The notification settings to receive via push. */
  push?: InputMaybe<PushNotificationSettingsInput>;
};

/** The possible order direction that a `order by` sql can use. */
export enum OrderDirection {
  /** Order ascending. */
  Asc = 'ASC',
  /** Order descending. */
  Desc = 'DESC'
}

/** An organization manages users and channels. */
export type Organization = Node & {
  __typename?: 'Organization';
  /** The analytics of the organization. */
  analysis?: Maybe<OrganizationAnalysis>;
  /** The analytics of the organization. */
  analytics?: Maybe<Analytics>;
  /** The category of the organization. */
  category?: Maybe<OrganizationCategory>;
  /** The channels of the organization. */
  channels?: Maybe<ChannelConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the organization. */
  name?: Maybe<Scalars['String']['output']>;
  /** The owner of this organization. */
  owner?: Maybe<User>;
  /** The stats of the organization. */
  stats?: Maybe<OrganizationStats>;
  /** The maximum number of users allowed to manage the organization (not including the owner). */
  userLimit?: Maybe<Scalars['Int']['output']>;
  /** The Dailymotion ID of an organization. */
  xid: Scalars['String']['output'];
};


/** An organization manages users and channels. */
export type OrganizationChannelsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  name?: InputMaybe<Scalars['String']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  xids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};

/** The analytics of the organization. */
export type OrganizationAnalysis = Node & {
  __typename?: 'OrganizationAnalysis';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Retrieve previously created reports. */
  reports: AnalyticsReportConnection;
};


/** The analytics of the organization. */
export type OrganizationAnalysisReportsArgs = {
  channelXid?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The possible categories for an organization. */
export enum OrganizationCategory {
  /** A family category. */
  Family = 'FAMILY',
  /** An mcn category. */
  Mcn = 'MCN',
  /** A standalone category. */
  Standalone = 'STANDALONE'
}

/** The connection type for Organization. */
export type OrganizationConnection = {
  __typename?: 'OrganizationConnection';
  /** A list of edges. */
  edges: Array<Maybe<OrganizationEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type OrganizationEdge = {
  __typename?: 'OrganizationEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Organization>;
  /** The permission of the user for the organization. */
  permission?: Maybe<OrganizationPermission>;
};

/** The permission of the user for the organization. */
export type OrganizationPermission = {
  __typename?: 'OrganizationPermission';
  /** The permission level of the user for the organization. */
  level?: Maybe<OrganizationRole>;
};

/** The possible values of a role in an Organization. */
export enum OrganizationRole {
  /** The organization role that represents an admin. */
  Admin = 'ADMIN',
  /** The organization role that represents an editor. */
  Editor = 'EDITOR',
  /** The organization role that represents an owner. */
  Owner = 'OWNER'
}

/** Information about the organization stats. */
export type OrganizationStats = Node & {
  __typename?: 'OrganizationStats';
  /** The channel stats of the organization. */
  channels?: Maybe<OrganizationStatsChannels>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The channel stats of the organization. */
export type OrganizationStatsChannels = Node & {
  __typename?: 'OrganizationStatsChannels';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of channels in the organization. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** Information to aid in pagination. */
export type PageInfo = {
  __typename?: 'PageInfo';
  /** The cursor after the last element, for cursor-based pagination. */
  endCursor?: Maybe<Scalars['String']['output']>;
  /** Indicates whether there are more items in the next page. */
  hasNextPage: Scalars['Boolean']['output'];
  /** Indicates whether there are more items in the previous page. */
  hasPreviousPage: Scalars['Boolean']['output'];
  /** The next page number, if hasNextPage is True. */
  nextPage?: Maybe<Scalars['Int']['output']>;
  /** The cursor at the first element, for cursor-based pagination. */
  startCursor?: Maybe<Scalars['String']['output']>;
};

/** Information about a partner. */
export type Partner = Node & {
  __typename?: 'Partner';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The organizations of the partner.
   * @deprecated Use `user.organizations`.
   */
  organizations?: Maybe<OrganizationConnection>;
};


/** Information about a partner. */
export type PartnerOrganizationsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The possible values for a dimension in a partner report. */
export enum PartnerReportDimension {
  /** Aggregate data report by account id dimension. */
  AccountId = 'ACCOUNT_ID',
  /** Aggregate data report by account username dimension. */
  AccountUsername = 'ACCOUNT_USERNAME',
  /** Aggregate data report by action dimension. */
  Action = 'ACTION',
  /** Aggregate data report by ads txt status dimension. */
  AdsTxtStatus = 'ADS_TXT_STATUS',
  /** Aggregate data report by ad error code. */
  AdErrorCode = 'AD_ERROR_CODE',
  /** Aggregate data report by ad error human readable strings. */
  AdErrorReadable = 'AD_ERROR_READABLE',
  /** Aggregate data report by ad format dimension. */
  AdFormat = 'AD_FORMAT',
  /** Aggregate data report by ai feature name dimension. */
  AiFeatureName = 'AI_FEATURE_NAME',
  /** Aggregate data report by buyers. */
  Buyer = 'BUYER',
  /** Aggregate data report by buyertypes. */
  Buyertype = 'BUYERTYPE',
  /** Aggregate data report by channel slug dimension. */
  ChannelSlug = 'CHANNEL_SLUG',
  /** Aggregate data report by claimer channel dimension. */
  ClaimerChannel = 'CLAIMER_CHANNEL',
  /** Aggregate data report by claimer or parent username dimension. */
  ClaimerOrParentUsername = 'CLAIMER_OR_PARENT_USERNAME',
  /** Aggregate data report by content tag dimension. */
  ContentTag = 'CONTENT_TAG',
  /** Aggregate data report by content tag list dimension. */
  ContentTagList = 'CONTENT_TAG_LIST',
  /** Aggregate data report by content type dimension. */
  ContentType = 'CONTENT_TYPE',
  /** Aggregate data report by day dimension. */
  Day = 'DAY',
  /** Aggregate data report by hour dimension. */
  Hour = 'HOUR',
  /** Aggregate data report by one of these three positions  Pre-roll, Mid-roll, Post-roll. */
  InventoryPosition = 'INVENTORY_POSITION',
  /** Aggregate data report by media type dimension. */
  MediaType = 'MEDIA_TYPE',
  /** Aggregate data report by minute dimension. */
  Minute = 'MINUTE',
  /** Aggregate data report by monetization product dimension. */
  MonetizationProduct = 'MONETIZATION_PRODUCT',
  /** Aggregate data report by monetization type dimension. */
  MonetizationType = 'MONETIZATION_TYPE',
  /** Aggregate data report by month dimension. */
  Month = 'MONTH',
  /** Aggregate data report by no ad reason human readable. */
  NoadReasonReadable = 'NOAD_REASON_READABLE',
  /** Aggregate data report by outcome (ad errors, no fills and timeout). */
  Outcome = 'OUTCOME',
  /** Aggregate data report by owner or parent username dimension. */
  OwnerOrParentUsername = 'OWNER_OR_PARENT_USERNAME',
  /** Aggregate data report by parent account id bucket dimension. */
  ParentAccountId = 'PARENT_ACCOUNT_ID',
  /** Aggregate data report by parent account username dimension. */
  ParentAccountUsername = 'PARENT_ACCOUNT_USERNAME',
  /** Aggregate data report by player id dimension. */
  PlayerId = 'PLAYER_ID',
  /** Aggregate data report by player size dimension. */
  PlayerSize = 'PLAYER_SIZE',
  /** Aggregate data report by player size bucket dimension. */
  PlayerSizeBucket = 'PLAYER_SIZE_BUCKET',
  /** Aggregate data report by player title dimension. */
  PlayerTitle = 'PLAYER_TITLE',
  /** Aggregate data report by player type dimension. */
  PlayerType = 'PLAYER_TYPE',
  /** Aggregate data report by playlist id dimension. */
  PlaylistId = 'PLAYLIST_ID',
  /** Aggregate data report by playlist title dimension. */
  PlaylistTitle = 'PLAYLIST_TITLE',
  /** Aggregate data report by playlist type dimension. */
  PlaylistType = 'PLAYLIST_TYPE',
  /** Aggregate data report by publisher channel dimension. */
  PublisherChannel = 'PUBLISHER_CHANNEL',
  /** Aggregate data report by publisher id dimension. */
  PublisherId = 'PUBLISHER_ID',
  /** Aggregate data report by publisher or parent username dimension. */
  PublisherOrParentUsername = 'PUBLISHER_OR_PARENT_USERNAME',
  /** Aggregate data report by video rendition format dimension. */
  RenditionFormat = 'RENDITION_FORMAT',
  /** Aggregate data report by video rendition fps (Frames Per Second) dimension. */
  RenditionFps = 'RENDITION_FPS',
  /** Aggregate data report by video rendition resolution dimension. */
  RenditionResolution = 'RENDITION_RESOLUTION',
  /** Aggregate data report by video id dimension. */
  VideoId = 'VIDEO_ID',
  /** Aggregate data report by video owner channel slug dimension. */
  VideoOwnerChannelSlug = 'VIDEO_OWNER_CHANNEL_SLUG',
  /** Aggregate data report by video owner id dimension. */
  VideoOwnerId = 'VIDEO_OWNER_ID',
  /** Aggregate data report by video owner username dimension. */
  VideoOwnerUsername = 'VIDEO_OWNER_USERNAME',
  /** Aggregate data report by video position dimension. */
  VideoPosition = 'VIDEO_POSITION',
  /** Aggregate data report by video title dimension. */
  VideoTitle = 'VIDEO_TITLE',
  /** Aggregate data report by visibility. */
  Visibility = 'VISIBILITY',
  /** Aggregate data report by visitor country dimension. */
  VisitorCountry = 'VISITOR_COUNTRY',
  /** Aggregate data report by visitor device type dimension. */
  VisitorDeviceType = 'VISITOR_DEVICE_TYPE',
  /** Aggregate data report by visitor domain group dimension. */
  VisitorDomainGroup = 'VISITOR_DOMAIN_GROUP',
  /** Aggregate data report by visitor page url dimension. */
  VisitorPageUrl = 'VISITOR_PAGE_URL',
  /** Aggregate data report by visitor subdomain dimension. */
  VisitorSubdomain = 'VISITOR_SUBDOMAIN'
}

/** A partner report file. */
export type PartnerReportFile = Node & {
  __typename?: 'PartnerReportFile';
  /** The date and time (ISO 8601 format) when the report was created. */
  createDate: Scalars['DateTime']['output'];
  /**
   * The creation date of the report.
   * @deprecated Use `createDate` field.
   */
  createdAt?: Maybe<Scalars['DateTime']['output']>;
  /** The download links of the report. */
  downloadLinks?: Maybe<ReportFileDownloadLinkConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The token identifying the report. */
  reportToken?: Maybe<Scalars['String']['output']>;
  /** The status of the report generation. */
  status?: Maybe<PartnerReportStatus>;
};


/** A partner report file. */
export type PartnerReportFileDownloadLinksArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The possible media type filters for a partner report. */
export enum PartnerReportFilterMediaType {
  /** A media that represents a `Live`. */
  Live = 'LIVE',
  /** A media that represents a `Video`. */
  Video = 'VIDEO'
}

/** The possible monetization filter types for a partner report. */
export enum PartnerReportFilterMonetizationType {
  /** Claim. */
  Claim = 'CLAIM',
  /** Video monetization. */
  VideoMonetization = 'VIDEO_MONETIZATION',
  /** Website monetization. */
  WebsiteMonetization = 'WEBSITE_MONETIZATION'
}

/** The input fields of a partner report filter. */
export type PartnerReportFilters = {
  /** Filter analytics reports by a channel slug. */
  channelSlug?: InputMaybe<Scalars['String']['input']>;
  /** Filter analytics reports by a media type. */
  mediaType?: InputMaybe<PartnerReportFilterMediaType>;
  /** Filter analytics reports by a monetization type. */
  monetizationType?: InputMaybe<PartnerReportFilterMonetizationType>;
  /** Filter analytics reports by a video owner channel slug. */
  videoOwnerChannelSlug?: InputMaybe<Scalars['String']['input']>;
  /** Filter analytics reports by a visitor domain group. */
  visitorDomainGroup?: InputMaybe<Scalars['String']['input']>;
};

/** The possible metrics in a partner report. */
export enum PartnerReportMetric {
  /** Use ad errors metric as report measurement. */
  AdErrors = 'AD_ERRORS',
  /** Use ad view completed as report measurement. */
  AdViewCompleted = 'AD_VIEW_COMPLETED',
  /** Use ad view not completed metric as report measurement. */
  AdViewNotCompleted = 'AD_VIEW_NOT_COMPLETED',
  /** Use AI used credits metric as report measurement. */
  AiUsedCredits = 'AI_USED_CREDITS',
  /** Use bandwidth used live bytes metric as report mesurement. */
  BandwidthUsedLiveBytes = 'BANDWIDTH_USED_LIVE_BYTES',
  /** Use bandwidth used live seconds metric as report mesurement. */
  BandwidthUsedLiveSeconds = 'BANDWIDTH_USED_LIVE_SECONDS',
  /** Use bandwidth used media count metric as report measurement. */
  BandwidthUsedMediaCount = 'BANDWIDTH_USED_MEDIA_COUNT',
  /** Use bandwidth used bytes metric as report measurement. */
  BandwidthUsedTotalBytes = 'BANDWIDTH_USED_TOTAL_BYTES',
  /** Use bandwidth used vod bytes metric as report measurement. */
  BandwidthUsedVodBytes = 'BANDWIDTH_USED_VOD_BYTES',
  /** Use bandwidth used vod seconds metric as report measurement. */
  BandwidthUsedVodSeconds = 'BANDWIDTH_USED_VOD_SECONDS',
  /** Use ecpm eur metric as report measurement. */
  EcpmEur = 'ECPM_EUR',
  /** Use ecpm usd metric as report measurement. */
  EcpmUsd = 'ECPM_USD',
  /** Use erpm eur metric as report measurement. */
  ErpmEur = 'ERPM_EUR',
  /** Use erpm usd metric as report measurement. */
  ErpmUsd = 'ERPM_USD',
  /** Use estimated earnings eur metric as report measurement. */
  EstimatedEarningsEur = 'ESTIMATED_EARNINGS_EUR',
  /** Use estimated earnings eur old metric as report measurement. */
  EstimatedEarningsEurOld = 'ESTIMATED_EARNINGS_EUR_OLD',
  /** Use estimated earnings usd metric as report measurement. */
  EstimatedEarningsUsd = 'ESTIMATED_EARNINGS_USD',
  /** Use estimated earnings usd old metric as report measurement. */
  EstimatedEarningsUsdOld = 'ESTIMATED_EARNINGS_USD_OLD',
  /** Use fill rate metric as report measurement. */
  FillRate = 'FILL_RATE',
  /** Use gdpr empty consent inventory metric as report measurement. */
  GdprEmptyConsentInventory = 'GDPR_EMPTY_CONSENT_INVENTORY',
  /** Use gdpr error consent inventory metric as report measurement. */
  GdprErrorConsentInventory = 'GDPR_ERROR_CONSENT_INVENTORY',
  /** Use gdpr full consent inventory metric as report measurement. */
  GdprFullConsentInventory = 'GDPR_FULL_CONSENT_INVENTORY',
  /** Use gdpr full consent score metric as report measurement. */
  GdprFullConsentScore = 'GDPR_FULL_CONSENT_SCORE',
  /** Use gdpr other consent inventory metric as report measurement. */
  GdprOtherConsentInventory = 'GDPR_OTHER_CONSENT_INVENTORY',
  /** Use gdpr refuse consent inventory metric as report measurement. */
  GdprRefuseConsentInventory = 'GDPR_REFUSE_CONSENT_INVENTORY',
  /** Use high viewable impressions as report measurement. */
  HighViewableImpressions = 'HIGH_VIEWABLE_IMPRESSIONS',
  /** Use impressions metric as report measurement. */
  Impressions = 'IMPRESSIONS',
  /** Use invalid traffic inventory metric as report measurement. */
  InvalidTrafficInventory = 'INVALID_TRAFFIC_INVENTORY',
  /** Use IVT score metric as report measurement. */
  IvtScore = 'IVT_SCORE',
  /** Use live time watched (seconds) metric as report measurement. */
  LiveTimeWatchedSeconds = 'LIVE_TIME_WATCHED_SECONDS',
  /** Use live viewers metric as report measurement. */
  LiveViewers = 'LIVE_VIEWERS',
  /** Use low viewable impressions as report measurement. */
  LowViewableImpressions = 'LOW_VIEWABLE_IMPRESSIONS',
  /** Use impressions metric as report measurement. */
  NbImpression = 'NB_IMPRESSION',
  /** Use nb inventory gdpr missing full consent metric as report measurement. */
  NbInventoryGdprMissingFullConsent = 'NB_INVENTORY_GDPR_MISSING_FULL_CONSENT',
  /** Use number of missed impression metric as report measurement. */
  NbMissedImpression = 'NB_MISSED_IMPRESSION',
  /** Use P1 impressions metric as report measurement. */
  NbP1Impression = 'NB_P1_IMPRESSION',
  /** Use no_ads metric as report measurement. */
  NoAds = 'NO_ADS',
  /** Use no ads txt inventory metric as report measurement. */
  NoAdsTxtInventory = 'NO_ADS_TXT_INVENTORY',
  /** Use no_ad_rate metric as report measurement. */
  NoAdRate = 'NO_AD_RATE',
  /** Use no brand safe inventory metric as report measurement. */
  NoBrandsafeInventory = 'NO_BRANDSAFE_INVENTORY',
  /** Use no_fills metric as report measurement. */
  NoFills = 'NO_FILLS',
  /** Use no monetized inventory metric as report measurement. */
  NoMonetizedInventory = 'NO_MONETIZED_INVENTORY',
  /** Use sellable inventory metric as report measurement. */
  SellableInventory = 'SELLABLE_INVENTORY',
  /** Use storage lifetime used bytes metric as report measurement. */
  StorageLifetimeUsedBytes = 'STORAGE_LIFETIME_USED_BYTES',
  /** Use storage lifetime used media count metric as report measurement. */
  StorageLifetimeUsedMediaCount = 'STORAGE_LIFETIME_USED_MEDIA_COUNT',
  /** Use storage lifetime used rendition count metric as report measurement. */
  StorageLifetimeUsedRenditionCount = 'STORAGE_LIFETIME_USED_RENDITION_COUNT',
  /** Use storage lifetime used seconds metric as report measurement. */
  StorageLifetimeUsedSeconds = 'STORAGE_LIFETIME_USED_SECONDS',
  /** Use storage used bytes metric as report measurement. */
  StorageUsedBytes = 'STORAGE_USED_BYTES',
  /** Use storage used media count metric as report measurement. */
  StorageUsedMediaCount = 'STORAGE_USED_MEDIA_COUNT',
  /** Use storage used rendition count metric as report measurement. */
  StorageUsedRenditionCount = 'STORAGE_USED_RENDITION_COUNT',
  /** Use storage used seconds metric as report measurement. */
  StorageUsedSeconds = 'STORAGE_USED_SECONDS',
  /** Use timeouts metric as report measurement. */
  Timeouts = 'TIMEOUTS',
  /** Use time watched seconds metric as report measurement. */
  TimeWatchedSeconds = 'TIME_WATCHED_SECONDS',
  /** Use total inventory metric as report measurement. */
  TotalInventory = 'TOTAL_INVENTORY',
  /** Use transcoding used live media count metric as report measurement. */
  TranscodingUsedLiveMediaCount = 'TRANSCODING_USED_LIVE_MEDIA_COUNT',
  /** Use transcoding used live seconds metric as report measurement. */
  TranscodingUsedLiveSeconds = 'TRANSCODING_USED_LIVE_SECONDS',
  /** Use transcoding used seconds metric as report measurement. */
  TranscodingUsedTotalSeconds = 'TRANSCODING_USED_TOTAL_SECONDS',
  /** Use transcoding used vod media count metric as report measurement. */
  TranscodingUsedVodMediaCount = 'TRANSCODING_USED_VOD_MEDIA_COUNT',
  /** Use transcoding used vod seconds metric as report measurement. */
  TranscodingUsedVodSeconds = 'TRANSCODING_USED_VOD_SECONDS',
  /** Use uploads metric as report measurement. */
  Uploads = 'UPLOADS',
  /** Use valid traffic inventory metric as report measurement. */
  ValidTrafficInventory = 'VALID_TRAFFIC_INVENTORY',
  /** Use viewability score metric as report measurement. */
  ViewabilityScore = 'VIEWABILITY_SCORE',
  /** Use views metric as report measurement. */
  Views = 'VIEWS',
  /** Use views_through_rate metric as report measurement. */
  ViewThroughRate = 'VIEW_THROUGH_RATE',
  /** Use vtr score metric as report measurement. */
  VtrScore = 'VTR_SCORE'
}

/** The possible values for a product in a partner report. */
export enum PartnerReportProduct {
  /** Every product available. */
  All = 'ALL',
  /** The product claim. */
  Claim = 'CLAIM',
  /** The product content. */
  Content = 'CONTENT',
  /** The product embed. */
  Embed = 'EMBED'
}

/** The possible values of a partner report status. */
export enum PartnerReportStatus {
  /** Report generation has expired. */
  Expired = 'EXPIRED',
  /** Report generation has failed. */
  Failed = 'FAILED',
  /** Report generation is finished. */
  Finished = 'FINISHED',
  /** Report generation is in progress. */
  InProgress = 'IN_PROGRESS'
}

/** Advanced partner features. */
export type PartnerSpace = Node & {
  __typename?: 'PartnerSpace';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** A generated report file of the partner. */
  reportFile?: Maybe<PartnerReportFile>;
};


/** Advanced partner features. */
export type PartnerSpaceReportFileArgs = {
  reportToken: Scalars['String']['input'];
};

/** Represents the payload items used by analytics KPIForList. */
export type PayloadItemsInput = {
  field: Scalars['String']['input'];
  values: Array<Scalars['String']['input']>;
};

/** Information about a player. */
export type Player = Node & {
  __typename?: 'Player';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Information about the player queue. */
  queue?: Maybe<PlayerQueue>;
};


/** Information about a player. */
export type PlayerQueueArgs = {
  algorithm?: InputMaybe<PlayerQueueAlgorithmName>;
  context?: InputMaybe<PlayerQueueContextArgument>;
};

/** The possible values for an algorithm's name. */
export enum PlayerAlgorithmName {
  /** An algorithm based on the creator catalog. */
  Creator = 'CREATOR',
  /** An algorithm based on the organization catalog. */
  Organization = 'ORGANIZATION'
}

/** Information aboot the player queue. */
export type PlayerQueue = Node & {
  __typename?: 'PlayerQueue';
  /** Indicates whether the player queue has auto play next. */
  hasAutoPlayNext?: Maybe<Scalars['Boolean']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the player queue. */
  name?: Maybe<Scalars['String']['output']>;
  /** The recordings of the player queue. */
  recordings?: Maybe<RecommendedRecordingConnection>;
  /**
   * The videos of the player queue.
   * @deprecated Use `recordings` field.
   */
  videos?: Maybe<VideoConnection>;
};


/** Information aboot the player queue. */
export type PlayerQueueRecordingsArgs = {
  algorithm?: InputMaybe<RecommendedRecordingAlgorithmName>;
  fallback?: InputMaybe<Scalars['Boolean']['input']>;
  filter?: InputMaybe<RecommendedRecordingFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information aboot the player queue. */
export type PlayerQueueVideosArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The possible values for a algorithm's name. */
export enum PlayerQueueAlgorithmName {
  /** An engagement algorithm. */
  Engagement = 'ENGAGEMENT',
  /** A monetization algorithm. */
  Monetization = 'MONETIZATION',
  /** A views algorithm. */
  Views = 'VIEWS'
}

/** The input fields of a player queue context argument. */
export type PlayerQueueContextArgument = {
  /** The ID of the view. */
  viewId?: InputMaybe<Scalars['String']['input']>;
};

/** The node at the end of a PointMetricEdge. */
export type PointMetric = Metric & Node & {
  __typename?: 'PointMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The sum of the point metrics. */
  total: Scalars['Int']['output'];
};

/** The connection type for a Point Metric. */
export type PointMetricConnection = {
  __typename?: 'PointMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<PointMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type PointMetricEdge = {
  __typename?: 'PointMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<PointMetric>;
};

/** Represents a poll. */
export type Poll = Node & Thread & {
  __typename?: 'Poll';
  /**
   * The component attached to the poll.
   * @deprecated Use `post` field.
   */
  component?: Maybe<Component>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The vote on the poll from the user.
   * @deprecated Use `voterEngagement`.
   */
  me?: Maybe<UserPollAnswer>;
  /** The story that elicited a poll. */
  opener?: Maybe<Story>;
  /** The answer options available for the poll. */
  options: Array<PollOption>;
  /**
   * The post attached to the poll.
   * @deprecated Use `opener` field.
   */
  post?: Maybe<Post>;
  /** The poll question. */
  question: Scalars['String']['output'];
  /** The share urls of the poll. */
  shareUrls?: Maybe<PollShareUrls>;
  /**
   * The URL of the poll.
   * @deprecated Use `shareUrls.permalink` field.
   */
  url: Scalars['String']['output'];
  /** The total number of votes for the poll. */
  voterCount: Scalars['Int']['output'];
  /** The voter engagement information of the Poll. */
  voterEngagement?: Maybe<VoterEngagement>;
};

/** The possible actions to a poll answer. */
export enum PollAnswerAction {
  /** Remove the answer from the poll. */
  Remove = 'REMOVE',
  /** Answer the poll. */
  Select = 'SELECT'
}

/** The input fields to manage poll answer for the connected user. */
export type PollAnswerInput = {
  /** The action to perform on the poll answer. */
  action: PollAnswerAction;
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the poll option. */
  optionId: Scalars['ID']['input'];
  /** The ID of the poll. */
  pollId: Scalars['ID']['input'];
};

/** The return fields from a poll answer. */
export type PollAnswerPayload = {
  __typename?: 'PollAnswerPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The connection type for Poll. */
export type PollConnection = {
  __typename?: 'PollConnection';
  /** A list of edges. */
  edges: Array<Maybe<PollEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type PollEdge = {
  __typename?: 'PollEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Poll>;
};

/** The available input fields of a Poll filter. */
export type PollFilter = {
  id?: InputMaybe<IdOperator>;
};

/** An answer option that can be attached to a poll. */
export type PollOption = Node & {
  __typename?: 'PollOption';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The textual representation of the answer option. */
  text: Scalars['String']['output'];
  /** The total number of votes for the option. */
  voterCount: Scalars['Int']['output'];
};

/** Information about the share urls of a Poll. */
export type PollShareUrls = Node & ShareUrls & {
  __typename?: 'PollShareUrls';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The permalink share url of the poll. */
  permalink: Scalars['String']['output'];
};

/** Types that can be a Post. */
export type Post = Collection | Live | Reaction | ReactionVideo | Video;

/** The connection type for Post. */
export type PostConnection = {
  __typename?: 'PostConnection';
  /** A list of edges. */
  edges: Array<Maybe<PostEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type PostEdge = {
  __typename?: 'PostEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Post>;
};

/** Represents the engagement metrics of a Post. */
export type PostEngagementMetrics = {
  /** The bookmark metrics of the post. */
  bookmarks?: Maybe<BookmarkMetricConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The like metrics of the post. */
  likes?: Maybe<LikeMetricConnection>;
  /** The reaction metrics of the post. */
  reactions?: Maybe<ReactionMetricConnection>;
};


/** Represents the engagement metrics of a Post. */
export type PostEngagementMetricsBookmarksArgs = {
  filter?: InputMaybe<BookmarkFilter>;
};


/** Represents the engagement metrics of a Post. */
export type PostEngagementMetricsLikesArgs = {
  filter?: InputMaybe<LikeMetricFilter>;
};

/** Types that can be a PostMetric. */
export type PostMetric = CollectionMetric | LiveMetric | ReactionMetric | VideoMetric;

/** The connection type for Post. */
export type PostMetricConnection = {
  __typename?: 'PostMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<PostMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type PostMetricEdge = {
  __typename?: 'PostMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<PostMetric>;
};

/** Represents the metrics of a Post. */
export type PostMetrics = {
  /** The engagement metrics of the post. */
  engagement?: Maybe<PostEngagementMetrics>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The available input fields of a post operator. */
export type PostOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<PostTypename>;
  /** Short for in array, must be an element of the array. */
  in?: InputMaybe<Array<PostTypename>>;
};

/** The possible values for a PostStatus. */
export enum PostStatus {
  /** A pending_review post. */
  PendingReview = 'PENDING_REVIEW',
  /** A published post. */
  Published = 'PUBLISHED'
}

/** The available input fields of a  operator. */
export type PostStatusOperator = {
  /** Short for not equal, must be different from the given data. */
  eq?: InputMaybe<PostStatus>;
};

/** The possible values for a `PostTypename`. */
export enum PostTypename {
  /** The typename of a collection post. */
  Collection = 'COLLECTION',
  /** The typename of a live post. */
  Live = 'LIVE',
  /** The typename of a reaction_video post. */
  Reaction = 'REACTION',
  /**
   * The typename of a reaction_video post.
   * @deprecated Use `REACTION`.
   */
  ReactionVideo = 'REACTION_VIDEO',
  /** The typename of a video post. */
  Video = 'VIDEO'
}

/** Product updates notification settings. */
export type ProductUpdates = Node & {
  __typename?: 'ProductUpdates';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the email notification setting is enabled. */
  isEmailEnabled?: Maybe<Scalars['Boolean']['output']>;
};

/** The possible values for a Promotion. */
export enum Promotion {
  /** A spotlight promotion. */
  Spotlight = 'SPOTLIGHT'
}

/** Information about a Prompt. */
export type Prompt = Node & Thread & {
  __typename?: 'Prompt';
  /** The chatroom associated with the Prompt. */
  chatroom?: Maybe<Chatroom>;
  /** The creation date (DateTime ISO8601) of the Prompt. */
  createDate: Scalars['DateTime']['output'];
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The story that elicited the Prompt. */
  opener?: Maybe<Story>;
  /** The content of the Prompt. */
  text: Scalars['String']['output'];
  /** The viewer engagement information of the Prompt. */
  viewerEngagement?: Maybe<PromptViewerEngagement>;
};

/** Information about the viewer engagement of a Prompt. */
export type PromptViewerEngagement = Node & ViewerEngagement & {
  __typename?: 'PromptViewerEngagement';
  /** Indicates the heart rating the viewer has given to the Prompt. */
  hearts?: Maybe<HeartRating>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates the like rating of the post from the viewer. */
  likeRating?: Maybe<LikeRating>;
  /** Indicates whether the viewer has liked the thread. Returns False if the viewer is not connected. */
  liked?: Maybe<Scalars['Boolean']['output']>;
};

/** The settings to receive push notifications. */
export type PushNotificationSettings = Node & {
  __typename?: 'PushNotificationSettings';
  /** The notifications on activities to receive. */
  activity?: Maybe<ActivityNotificationSettings>;
  /** The notifications on announcements to receive. */
  announcements?: Maybe<AnnouncementNotificationSettings>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The notifications on insights to receive. */
  insights?: Maybe<InsightNotificationSettings>;
  /** The notifications on recommendations to receive. */
  recommendations?: Maybe<RecommendationNotificationSettings>;
};

/** The notification settings to receive via push. */
export type PushNotificationSettingsInput = {
  /** The notifications on activities to receive. */
  activity?: InputMaybe<ActivityNotificationSettingsInput>;
  /** The notifications on announcements to receive. */
  announcements?: InputMaybe<AnnouncementNotificationSettingsInput>;
  /** The notifications on insights to receive. */
  insights?: InputMaybe<InsightNotificationsSettingsInput>;
  /** The notifications on recommendations to receive. */
  recommendations?: InputMaybe<RecommendationNotificationSettingsInput>;
};

/** Represents the quality in a recording. */
export type Quality = Node & {
  __typename?: 'Quality';
  /** The frames per second of the recording. */
  frameRate?: Maybe<Scalars['Int']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The resolution of the recording. */
  resolution?: Maybe<Resolution>;
};

/** The query root of Dailymotion's GraphQL API. */
export type Query = {
  __typename?: 'Query';
  /**
   *
   *   Get appeal informations based on token.
   *   Tokens are generated by internaltools-moderation-decisions service
   *   and sent to clients.
   *   It may raise following GraphQL errors:
   *     - token is invalid (type=bad_request, reason=invalid_token)
   *     - token is expired (type=bad_reqeust, reason=token_expired)
   *     - appeal already exists (type=bad_reqeust, reason=appeal_already_exists)
   *     - appeal is invalid (type=bad_reqeust, reason=invalid_appeal)
   *     - appeal in review (type=bad_reqeust, reason=appeal_in_review)
   *
   */
  appealApplication: AppealApplication;
  /** Information about behavior: feature flipping and A/B testing experiments. */
  behavior?: Maybe<Behavior>;
  /** A list of categories. */
  categories?: Maybe<CategoryConnection>;
  /** A channel manages collections and videos. */
  channel?: Maybe<Channel>;
  /** A list of channels. */
  channels?: Maybe<ChannelConnection>;
  /** A collection manages medias. */
  collection?: Maybe<Collection>;
  /** A list of collections. */
  collections?: Maybe<CollectionConnection>;
  /**
   * A content feed manages Posts.
   * @deprecated Use conversations with filter algorithm - HASHTAG or PERSPECTIVE.
   */
  contentFeed?: Maybe<FeedPostConnection>;
  /** A list of conversations. */
  conversations?: Maybe<ConversationConnection>;
  /**
   * Content featured by Dailymotion.
   * @deprecated Use `conversations(filter: { algorithm: { eq: FEATURED }})`.
   */
  featuredContent?: Maybe<FeaturedContent>;
  /**
   * A feed manages Posts.
   * @deprecated Use conversations with filter algorithm - HASHTAG or PERSPECTIVE.
   */
  feed?: Maybe<PostConnection>;
  /** A hashtag. */
  hashtag?: Maybe<Hashtag>;
  /**
   * A list of interests.
   * @deprecated No longer supported.
   */
  interests?: Maybe<InterestConnection>;
  /** A live represents a media that is streamed. */
  live?: Maybe<Live>;
  /**
   * The stream urls of lives.
   * @deprecated Use `mediaStreams`.
   */
  liveStreams?: Maybe<LiveStreamsConnection>;
  /** A list of lives. */
  lives?: Maybe<LiveConnection>;
  /** Allows to access to supported countries and user country location. */
  localization?: Maybe<Localization>;
  /** Information about the connected user. */
  me?: Maybe<User>;
  /**
   * A media represents a video or a live.
   * @deprecated Use `recording`.
   */
  media?: Maybe<Media>;
  /**
   * A list of media streams.
   * @deprecated Use `recording.streamUrls`.
   */
  mediaStreams?: Maybe<MediaStreamsConnection>;
  /** Access to MojoKit AI-powered content enhancement features. */
  mojoKit: MojoKitQueries;
  /** Represents a node with an ID. */
  node?: Maybe<Node>;
  /** Access to advanced partner features. */
  partner?: Maybe<PartnerSpace>;
  /** Information about the player. */
  player?: Maybe<Player>;
  /** Retrieve a poll specified by its ID. */
  poll?: Maybe<Poll>;
  /** The list of available polls. */
  polls?: Maybe<PollConnection>;
  /** A reaction to a story in a recording format. */
  reaction?: Maybe<Reaction>;
  /**
   * A reaction to a Dailymotion video in the form of a video.
   * @deprecated Use `reaction`.
   */
  reactionVideo?: Maybe<ReactionVideo>;
  /** Represents a recording. */
  recording?: Maybe<Recording>;
  /** Perform a search. */
  search?: Maybe<Search>;
  /**
   * The list of countries that are supported.
   * @deprecated Use `localization.supportedCountries`.
   */
  supportedCountries?: Maybe<Array<Maybe<Country>>>;
  /** The threads the story has elicited. */
  threads?: Maybe<ThreadConnection>;
  /**
   * A topic represents a keyword that is associated to a video.
   * @deprecated No longer supported.
   */
  topic?: Maybe<Topic>;
  /**
   * A list of topics.
   * @deprecated No longer supported.
   */
  topics?: Maybe<TopicConnection>;
  /** Information about the user. */
  user?: Maybe<User>;
  /** A video represents a Dailymotion media. */
  video?: Maybe<Video>;
  /**
   * Represents either a Dailymotion video or live.
   * @deprecated Use `media`.
   */
  videoOrLive?: Maybe<VideoOrLive>;
  /**
   * A list of video streams.
   * @deprecated Use `mediaStreams`.
   */
  videoStreams?: Maybe<VideoStreamsConnection>;
  /** A list of videos. */
  videos?: Maybe<VideoConnection>;
  /**
   * The views of NEON.
   * @deprecated Use `conversations` with filter algorithm.
   */
  views?: Maybe<Views>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryAppealApplicationArgs = {
  token: Scalars['String']['input'];
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryCategoriesArgs = {
  filter?: CategoryFilter;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryChannelArgs = {
  name?: InputMaybe<Scalars['String']['input']>;
  xid?: InputMaybe<Scalars['String']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryChannelsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<ChannelsSort>;
  xids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryCollectionArgs = {
  xid: Scalars['String']['input'];
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryCollectionsArgs = {
  channelXid?: InputMaybe<Scalars['String']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  verifiedChannelOnly?: InputMaybe<Scalars['Boolean']['input']>;
  videoXid?: InputMaybe<Scalars['String']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryContentFeedArgs = {
  filter?: InputMaybe<FeedFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  name: FeedName;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<FeedSort>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryConversationsArgs = {
  context?: InputMaybe<ConversationContext>;
  filter?: InputMaybe<ConversationFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  orderBy?: InputMaybe<ConversationSort>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryFeedArgs = {
  filter?: InputMaybe<FeedFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  id?: InputMaybe<Scalars['ID']['input']>;
  name: FeedName;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<FeedSort>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryHashtagArgs = {
  id?: InputMaybe<Scalars['ID']['input']>;
  slug?: InputMaybe<Scalars['String']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryInterestsArgs = {
  enabledOnly?: InputMaybe<Scalars['Boolean']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryLiveArgs = {
  password?: InputMaybe<Scalars['String']['input']>;
  xid: Scalars['String']['input'];
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryLiveStreamsArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  liveXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryLivesArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  channelXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  first?: InputMaybe<Scalars['Int']['input']>;
  isOnAir?: InputMaybe<Scalars['Boolean']['input']>;
  languages?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  page?: InputMaybe<Scalars['Int']['input']>;
  startIn?: InputMaybe<Scalars['Int']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryMediaArgs = {
  password?: InputMaybe<Scalars['String']['input']>;
  xid: Scalars['String']['input'];
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryMediaStreamsArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  mediaXids: Array<InputMaybe<Scalars['String']['input']>>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryNodeArgs = {
  id: Scalars['ID']['input'];
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryPlayerArgs = {
  algorithm?: InputMaybe<PlayerAlgorithmName>;
  creatorXid?: InputMaybe<Scalars['String']['input']>;
  embed?: EmbedFormat;
  page?: InputMaybe<HtmlPage>;
  recordingXid?: InputMaybe<Scalars['String']['input']>;
  videoXid?: InputMaybe<Scalars['String']['input']>;
  viewId?: InputMaybe<Scalars['String']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryPollArgs = {
  id: Scalars['ID']['input'];
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryPollsArgs = {
  filter?: InputMaybe<PollFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryReactionArgs = {
  xid: Scalars['String']['input'];
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryReactionVideoArgs = {
  xid: Scalars['String']['input'];
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryRecordingArgs = {
  id: Scalars['ID']['input'];
  password?: InputMaybe<Scalars['String']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QuerySearchArgs = {
  token?: InputMaybe<Scalars['String']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryThreadsArgs = {
  after?: InputMaybe<Scalars['String']['input']>;
  filter?: InputMaybe<ThreadFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryTopicArgs = {
  xid?: InputMaybe<Scalars['String']['input']>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryTopicsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<Scalars['String']['input']>;
  whitelistedOnly?: InputMaybe<Scalars['Boolean']['input']>;
  xids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryUserArgs = {
  xid: Scalars['String']['input'];
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryVideoArgs = {
  password?: InputMaybe<Scalars['String']['input']>;
  xid: Scalars['String']['input'];
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryVideoOrLiveArgs = {
  password?: InputMaybe<Scalars['String']['input']>;
  xid: Scalars['String']['input'];
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryVideoStreamsArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  videoXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


/** The query root of Dailymotion's GraphQL API. */
export type QueryVideosArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  channelXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  createdAfter?: InputMaybe<Scalars['DateTime']['input']>;
  createdBefore?: InputMaybe<Scalars['DateTime']['input']>;
  filter?: InputMaybe<VideoFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  isFeatured?: InputMaybe<Scalars['Boolean']['input']>;
  languages?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<Scalars['String']['input']>;
  topicXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  videoXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};

/** The input fields to rate a recommendation. */
export type RateRecommendationInput = {
  /** The algorithm used for the recommendation. */
  algorithm: AlgorithmName;
  /** The rating percentage of the recommendation. */
  percentage: Scalars['Int']['input'];
  /** The source used for the recommendation. */
  source?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the story that the recommendation is created for. */
  storyId: Scalars['ID']['input'];
};

/** The return fields from rating a recommendation. */
export type RateRecommendationPayload = {
  __typename?: 'RateRecommendationPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Represents a Reaction in a recording format. */
export type Reaction = Content & Node & Recording & Thread & {
  __typename?: 'Reaction';
  /** The chatroom associated with the reaction. */
  chatroom?: Maybe<Chatroom>;
  /** The date and time (ISO 8601 format) when the reaction was created. */
  createDate: Scalars['DateTime']['output'];
  /**
   * The creation date (DateTime ISO8601) of the reaction.
   * @deprecated Use `createDate` field.
   */
  createdAt?: Maybe<Scalars['DateTime']['output']>;
  /** The channel who created the reaction. */
  creator?: Maybe<Channel>;
  /** The duration of the reaction in seconds. */
  duration?: Maybe<Scalars['Int']['output']>;
  /** The hashtags of the reaction. */
  hashtags?: Maybe<HashtagConnection>;
  /**
   * The URL of the adaptive bitrate manifest using the Apple HTTP Live Streaming
   *   protocol. Without an access token this field contains null, the Dailymotion
   *   user associated with the access token must be the owner of the video. This
   *   field is rate limited. The returned url is secured: it can only be consumed by
   *   the user who made the query and it expires after a certain time.
   * @deprecated Use `hlsUrl` field.
   */
  hlsURL?: Maybe<Scalars['String']['output']>;
  /**
   * The URL of the adaptive bitrate manifest using the Apple HTTP Live Streaming
   *   protocol. Without an access token this field contains null, the Dailymotion
   *   user associated with the access token must be the owner of the video. This
   *   field is rate limited. The returned url is secured: it can only be consumed by
   *   the user who made the query and it expires after a certain time.
   * @deprecated Use `streamUrls.hls` field.
   */
  hlsUrl?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * Indicates whether the reaction allows comments to be posted.
   * @deprecated No longer supported.
   */
  isCommentsEnabled?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the reaction allows reactions to created.
   * @deprecated No longer supported.
   */
  isReactionsEnabled?: Maybe<Scalars['Boolean']['output']>;
  /** The metrics of the reaction. */
  metrics?: Maybe<ReactionMetrics>;
  /** The story that elicited the reaction to be created. */
  opener?: Maybe<Story>;
  /** Indicates whether the creator of the story has liked the reaction. */
  openerCreatorLiked: Scalars['Boolean']['output'];
  /**
   * The reactions created on the reaction.
   * @deprecated No longer supported.
   */
  reactions?: Maybe<ReactionConnection>;
  /** The share urls of the reaction. */
  shareUrls?: Maybe<ReactionShareUrls>;
  /** The slug of the reaction. */
  slug: Scalars['String']['output'];
  /** The stream urls of the reaction. */
  streamUrls?: Maybe<ReactionStreamUrls>;
  /** The subtitles of the reaction. */
  subtitles?: Maybe<SubtitleConnection>;
  /** The URL of the thumbnail image. */
  thumbnail?: Maybe<Image>;
  /** The title of the reaction. */
  title?: Maybe<Scalars['String']['output']>;
  /** The transcript of the reaction video. */
  transcript?: Maybe<CaptionConnection>;
  /**
   * The URL of the reaction.
   * @deprecated Use `shareUrls.permalink` field.
   */
  url?: Maybe<Scalars['String']['output']>;
  /** The viewer engagement information of the reaction. */
  viewerEngagement?: Maybe<ReactionViewerEngagement>;
  /** The Dailymotion ID of the reaction. */
  xid: Scalars['String']['output'];
};


/** Represents a Reaction in a recording format. */
export type ReactionHashtagsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Represents a Reaction in a recording format. */
export type ReactionReactionsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Represents a Reaction in a recording format. */
export type ReactionSubtitlesArgs = {
  auto?: Scalars['Boolean']['input'];
  autoGenerated?: InputMaybe<Scalars['Boolean']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Represents a Reaction in a recording format. */
export type ReactionThumbnailArgs = {
  height: ThumbnailHeight;
};


/** Represents a Reaction in a recording format. */
export type ReactionTranscriptArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The connection type for ReactionVideo. */
export type ReactionConnection = {
  __typename?: 'ReactionConnection';
  /** A list of edges. */
  edges: Array<Maybe<ReactionEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type ReactionEdge = {
  __typename?: 'ReactionEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Reaction>;
};

/** The engagement metrics of a Reaction. */
export type ReactionEngagementMetrics = Node & PostEngagementMetrics & {
  __typename?: 'ReactionEngagementMetrics';
  /** The bookmark metrics of the reaction. */
  bookmarks?: Maybe<BookmarkMetricConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The like metrics of the reaction. */
  likes?: Maybe<LikeMetricConnection>;
  /** The reaction metrics of the reaction. */
  reactions?: Maybe<ReactionMetricConnection>;
};


/** The engagement metrics of a Reaction. */
export type ReactionEngagementMetricsBookmarksArgs = {
  filter?: InputMaybe<BookmarkFilter>;
};


/** The engagement metrics of a Reaction. */
export type ReactionEngagementMetricsLikesArgs = {
  filter?: InputMaybe<LikeMetricFilter>;
};

/** The node at the end of a ReactionMetricEdge. */
export type ReactionMetric = Metric & Node & {
  __typename?: 'ReactionMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the reaction metric. A null value indicates that it is hidden or not available. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a ReactionMetric. */
export type ReactionMetricConnection = {
  __typename?: 'ReactionMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<ReactionMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type ReactionMetricEdge = {
  __typename?: 'ReactionMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<ReactionMetric>;
};

/** The metrics of a Reaction. */
export type ReactionMetrics = Node & PostMetrics & {
  __typename?: 'ReactionMetrics';
  /** The engagement metrics of the reaction. */
  engagement?: Maybe<VideoEngagementMetrics>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The return fields from creating/updating a reaction. */
export type ReactionPayload = {
  __typename?: 'ReactionPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The created/updated reaction. */
  reaction?: Maybe<Reaction>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Information about the share urls of a Reaction. */
export type ReactionShareUrls = Node & ShareUrls & {
  __typename?: 'ReactionShareUrls';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The permalink share url of the reaction. */
  permalink: Scalars['String']['output'];
};

/** Information about the stream urls of a Reaction. */
export type ReactionStreamUrls = Node & StreamUrls & {
  __typename?: 'ReactionStreamUrls';
  /** The hls url of the video stream. */
  hls: Scalars['String']['output'];
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** Information about a reaction video. */
export type ReactionVideo = Node & {
  __typename?: 'ReactionVideo';
  /** The creation date (DateTime ISO8601) of the reaction video. */
  createdAt?: Maybe<Scalars['DateTime']['output']>;
  /** The duration of the reaction video in seconds. */
  duration?: Maybe<Scalars['Int']['output']>;
  /** The hashtags of the reaction video. */
  hashtags?: Maybe<HashtagConnection>;
  /**
   * The URL of the adaptive bitrate manifest using the Apple HTTP Live Streaming
   *   protocol. Without an access token this field contains null, the Dailymotion
   *   user associated with the access token must be the owner of the video. This
   *   field is rate limited. The returned url is secured: it can only be consumed by
   *   the user who made the query and it expires after a certain time.
   */
  hlsURL?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * Indicates whether the reaction video is bookmarked by the connected user.
   *   Returns False if the user is not connected.
   * @deprecated Use `viewerEngagement.bookmarked` field.
   */
  isBookmarked?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether posting comments on this reaction video is allowed.
   *   Returns False if posting comments is not allowed.
   */
  isCommentsEnabled?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the reaction video is in the watch later list of the connected user.
   *   Returns False if the user is not connected.
   * @deprecated Use `viewerEngagement.favorited` field.
   */
  isInWatchLater?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the connected user has liked the reaction video.
   *   Returns False if the user is not connected.
   * @deprecated Use `viewerEngagement.liked` field.
   */
  isLiked?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the connected user has reacted to the reaction video.
   *   Returns False if the user is not connected.
   * @deprecated Use `viewerEngagement.reacted` field.
   */
  isReacted?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether reaction videos are allowed on the reaction video. */
  isReactionVideosEnabled?: Maybe<Scalars['Boolean']['output']>;
  /** The stats of the reaction video. */
  stats?: Maybe<ReactionVideoStats>;
  /** The subtitles of the reaction video. */
  subtitles?: Maybe<SubtitleConnection>;
  /** The URL of the thumbnail image. */
  thumbnail?: Maybe<Image>;
  /**
   * The URL of the thumbnail image.
   * @deprecated Use `thumbnail` field.
   */
  thumbnailURL?: Maybe<Scalars['String']['output']>;
  /** The title of the reaction video. */
  title?: Maybe<Scalars['String']['output']>;
  /** The URL of the reaction video. */
  url?: Maybe<Scalars['String']['output']>;
  /** The user who created the reaction video. */
  user?: Maybe<User>;
  /** The video that the reaction video was created for. */
  video?: Maybe<Video>;
  /** The viewer engagement information of the reaction video. */
  viewerEngagement?: Maybe<ReactionViewerEngagement>;
  /** The Dailymotion ID of the reaction video. */
  xid: Scalars['String']['output'];
};


/** Information about a reaction video. */
export type ReactionVideoHashtagsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a reaction video. */
export type ReactionVideoSubtitlesArgs = {
  autoGenerated?: InputMaybe<Scalars['Boolean']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a reaction video. */
export type ReactionVideoThumbnailArgs = {
  height: ThumbnailHeight;
};


/** Information about a reaction video. */
export type ReactionVideoThumbnailUrlArgs = {
  size: Scalars['String']['input'];
};

/** The connection type for ReactionVideo. */
export type ReactionVideoConnection = {
  __typename?: 'ReactionVideoConnection';
  /** A list of edges. */
  edges: Array<Maybe<ReactionVideoEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** The input fields to create a reaction video. */
export type ReactionVideoCreateInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The URL of the thumbnail image. */
  thumbnailURL?: InputMaybe<Scalars['String']['input']>;
  /** The title of the reaction video. */
  title?: InputMaybe<Scalars['String']['input']>;
  /** The URL of the reaction video to get the upload file from. */
  url: Scalars['String']['input'];
  /** The xid of the video that the reaction video is created for. */
  videoXid: Scalars['String']['input'];
};

/** The input fields to delete a reaction video. */
export type ReactionVideoDeleteInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The xid of the reaction video to delete. */
  xid: Scalars['String']['input'];
};

/** The return fields from deleting a reaction video. */
export type ReactionVideoDeletePayload = {
  __typename?: 'ReactionVideoDeletePayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** An edge in a connection. */
export type ReactionVideoEdge = {
  __typename?: 'ReactionVideoEdge';
  /** The item at the end of the edge. */
  node?: Maybe<ReactionVideo>;
};

/** The return fields from creating/updating a reaction video. */
export type ReactionVideoPayload = {
  __typename?: 'ReactionVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The new/updated reaction video. */
  reactionVideo?: Maybe<ReactionVideo>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Information about the reaction_video stats. */
export type ReactionVideoStats = Node & {
  __typename?: 'ReactionVideoStats';
  /** The bookmark stats of the reaction_video. */
  bookmarks?: Maybe<ReactionVideoStatsBookmarks>;
  /** The favorite stats of the reaction_video. */
  favorites?: Maybe<ReactionVideoStatsFavorites>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The like stats of the reaction_video. */
  likes?: Maybe<ReactionVideoStatsLikes>;
  /** The reaction stats of the reaction_video. */
  reactionVideos?: Maybe<ReactionVideoStatsReactionVideos>;
  /** The saves stats of the reaction_video. */
  saves?: Maybe<ReactionVideoStatsSaves>;
};

/** The bookmark stats of the video. */
export type ReactionVideoStatsBookmarks = Node & {
  __typename?: 'ReactionVideoStatsBookmarks';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of bookmarks of the video. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The favorite stats of the video. */
export type ReactionVideoStatsFavorites = Node & {
  __typename?: 'ReactionVideoStatsFavorites';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of favorites of the video. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The like stats of the reaction_video. */
export type ReactionVideoStatsLikes = Node & {
  __typename?: 'ReactionVideoStatsLikes';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of likes of the reaction_video. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The reaction stats of the reaction_video. */
export type ReactionVideoStatsReactionVideos = Node & {
  __typename?: 'ReactionVideoStatsReactionVideos';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of reaction reaction_videos of the reaction_video. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The saves stats of the reaction_video. */
export type ReactionVideoStatsSaves = Node & {
  __typename?: 'ReactionVideoStatsSaves';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of playlists and watchlater added of the reaction_video. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The input fields to update a reaction video. */
export type ReactionVideoUpdateInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** the hashtags of the reaction video */
  hashtags?: InputMaybe<Array<Scalars['String']['input']>>;
  /** The title of the reaction video. */
  title?: InputMaybe<Scalars['String']['input']>;
  /** The xid of the reaction video to update. */
  xid: Scalars['String']['input'];
};

/** Information about the viewer engagement of a Reaction. */
export type ReactionViewerEngagement = Node & ViewerEngagement & {
  __typename?: 'ReactionViewerEngagement';
  /** Indicates whether the reaction is bookmarked by the viewer. Returns False if the viewer is not connected. */
  bookmarked?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the viewer has the reaction in its watch later list. Returns False if the viewer is not connected. */
  favorited?: Maybe<Scalars['Boolean']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates the like rating of the reaction from the viewer. */
  likeRating?: Maybe<LikeRating>;
  /** Indicates whether the viewer has liked the comment. Returns False if the viewer is not connected. */
  liked?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the viewer has reacted to the reaction. Returns False if the viewer is not connected. */
  reacted?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the viewer has added the reaction to one of its collections. Returns False if the viewer is not connected. */
  saved?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the viewer has completed watching the reaction. Returns False if the viewer is not connected. */
  watchCompleted?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the viewer has started watching the reaction. Returns False if the viewer is not connected. */
  watchStarted?: Maybe<Scalars['Boolean']['output']>;
};

/** The notification settings on recommendations to receive. */
export type RecommendationNotificationSettings = Node & {
  __typename?: 'RecommendationNotificationSettings';
  /** Receive notifications to watch unwatched posts in your bookmarks. */
  bookmarkReminders?: Maybe<Scalars['Boolean']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Receive notifications on personalized posts you might like. */
  personalization?: Maybe<Scalars['Boolean']['output']>;
};

/** The notifications settings on recommendations to receive. */
export type RecommendationNotificationSettingsInput = {
  /** Indicate whether to notifications to watch unwatched posts in your bookmarks. */
  bookmarkReminders?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to notifications on personalized posts you might like. */
  personalization?: InputMaybe<Scalars['Boolean']['input']>;
};

/** Information about a recommended recording. */
export type RecommendedRecording = Node & {
  __typename?: 'RecommendedRecording';
  /** The algorithm. */
  algorithm?: Maybe<RecommendedRecordingAlgorithm>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The item at the end of the edge. */
  recording?: Maybe<Recording>;
};

/** Information about the recommendation algorithm. */
export type RecommendedRecordingAlgorithm = Algorithm & {
  __typename?: 'RecommendedRecordingAlgorithm';
  /** The name of the algorithm. */
  name?: Maybe<RecommendedRecordingAlgorithmName>;
  /** The version. */
  version?: Maybe<Scalars['String']['output']>;
};

/** The possible names for a recommended recording algorithm. */
export enum RecommendedRecordingAlgorithmName {
  /** An algorithm to suggest recommendations that encourages engagement. */
  Engagement = 'ENGAGEMENT',
  /** An algorithm to suggest recommendations that generate the most revenue. */
  Monetization = 'MONETIZATION',
  /** An algorithm to suggest recommendations that attract views. */
  Views = 'VIEWS'
}

/** The connection type for Recording. */
export type RecommendedRecordingConnection = {
  __typename?: 'RecommendedRecordingConnection';
  /** A list of edges. */
  edges: Array<Maybe<RecommendedRecordingEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type RecommendedRecordingEdge = {
  __typename?: 'RecommendedRecordingEdge';
  /** The item at the end of the edge. */
  node?: Maybe<RecommendedRecording>;
};

/** The available input fields of a Recommended Recording filter. */
export type RecommendedRecordingFilter = {
  /** Filter recommended recordings by create date. */
  createDate?: InputMaybe<DateTimeOperator>;
  /** Filter recommended recordings by creator. */
  creatorXid?: InputMaybe<StringOperator>;
  /** Filter recommended recordings by organization. */
  organizationXid?: InputMaybe<StringOperator>;
};

/** Represents a node with a Recording. */
export type Recording = {
  /** The date and time (ISO 8601 format) when the recording was created. */
  createDate: Scalars['DateTime']['output'];
  /**
   * The creation date (DateTime ISO8601) of the recording.
   * @deprecated Use `createDate` field.
   */
  createdAt?: Maybe<Scalars['DateTime']['output']>;
  /** The channel that created the recording. */
  creator?: Maybe<Channel>;
  /**
   * The URL of the adaptative bitrate manifest using the Apple HTTP Live Streaming
   *   protocol. Without an access token this field contains null, the Dailymotion
   *   user associated with the access token must be the owner of the video. This
   *   field is rate limited. The returned url is secured: it can only be consumed by
   *   the user who made the query and it expires after a certain time.
   * @deprecated Use `recording.streamUrls.hls`.
   */
  hlsUrl?: Maybe<Scalars['String']['output']>;
  /** The reactions created on the recording. */
  reactions?: Maybe<ReactionConnection>;
  /** The share urls of the recording. */
  shareUrls?: Maybe<ShareUrls>;
  /** The stream urls of the recording. */
  streamUrls?: Maybe<StreamUrls>;
  /** The URL of the recording thumbnail image. */
  thumbnail?: Maybe<Image>;
  /** The title of the recording. */
  title?: Maybe<Scalars['String']['output']>;
  /**
   * The URL of the recording.
   * @deprecated Use `shareUrls.permalink` field.
   */
  url?: Maybe<Scalars['String']['output']>;
  /** The Dailymotion ID of the recording. */
  xid: Scalars['String']['output'];
};


/** Represents a node with a Recording. */
export type RecordingReactionsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Represents a node with a Recording. */
export type RecordingThumbnailArgs = {
  height: ThumbnailHeight;
};

/**  possibility to be used for live, reactionVideo, episode */
export enum RecordingPrivacy {
  /** Displays only recordings that are private */
  Private = 'PRIVATE',
  /** Displays only recordings that are public */
  Public = 'PUBLIC'
}

/** The violation reason to report the content. */
export enum RecordingViolation {
  /** Content that contains child abuse. */
  ChildAbuse = 'CHILD_ABUSE',
  /** Content that is copyrighted. */
  CopyrightInfringement = 'COPYRIGHT_INFRINGEMENT',
  /** Content that misrepresents the owner. */
  CopyrightOwner = 'COPYRIGHT_OWNER',
  /** Content that is against humanity, such as genocide. */
  CrimesAgainstHumanity = 'CRIMES_AGAINST_HUMANITY',
  /** Content that contains child sexual abuse material. */
  Csam = 'CSAM',
  /** Content that contains false information or is misleading on purpose. */
  Disinformation = 'DISINFORMATION',
  /** Content that is harmful for children. */
  HarmfulContent = 'HARMFUL_CONTENT',
  /** Content that is hateful. */
  HatefulContent = 'HATEFUL_CONTENT',
  /** Content that contains personal or confidential information. */
  Privacy = 'PRIVACY',
  /** Content that contains nudity. */
  SexualContent = 'SEXUAL_CONTENT',
  /** Content that contains spam. */
  Spam = 'SPAM',
  /** Content that contains terrorism. */
  Terrorism = 'TERRORISM',
  /** Content that contains violence. */
  Violence = 'VIOLENCE'
}

/** The input fields to recover a password. */
export type RecoverPasswordInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The email address of the user who wants to recover its password. */
  email: Scalars['String']['input'];
};

/** The return fields from recovering a password. */
export type RecoverPasswordPayload = {
  __typename?: 'RecoverPasswordPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields of a related video context. */
export type RelatedVideoContext = {
  /** The ID of the view. */
  viewId?: InputMaybe<Scalars['String']['input']>;
};

/** Remind unwatched videos notification settings. */
export type RemindUnwatchedVideos = Node & {
  __typename?: 'RemindUnwatchedVideos';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the email notification setting is enabled. */
  isEmailEnabled?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the push notification setting is enabled. */
  isPushEnabled?: Maybe<Scalars['Boolean']['output']>;
};

/** The input fields to remove a creator from the blocklist. */
export type RemoveBlockedInput = {
  /** The ID of the creator to remove from the blocklist. */
  id: Scalars['String']['input'];
};

/** The return fields from removing a creator from the blocklist. */
export type RemoveBlockedPayload = {
  __typename?: 'RemoveBlockedPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to remove a collection. */
export type RemoveCollectionInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the collection. */
  collectionXid: Scalars['String']['input'];
};

/** The return fields from deleting a collection. */
export type RemoveCollectionPayload = {
  __typename?: 'RemoveCollectionPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to remove a video from a collection. */
export type RemoveCollectionVideoInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the collection. */
  collectionXid: Scalars['String']['input'];
  /** The Dailymotion ID of the video. */
  videoXid: Scalars['String']['input'];
};

/** The return fields from removing a video from a collection. */
export type RemoveCollectionVideoPayload = {
  __typename?: 'RemoveCollectionVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to remove a video from the `WatchLater` list of the connected user. */
export type RemoveWatchLaterVideoInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the video. */
  videoXid: Scalars['String']['input'];
};

/** The return fields from removing a video from the `WatchLater` list of the connected user. */
export type RemoveWatchLaterVideoPayload = {
  __typename?: 'RemoveWatchLaterVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to remove a `Watched` from the watched list of the connected user. */
export type RemoveWatchedInput = {
  /** "The Dailymotion ID of the `Watched` to remove. */
  id: Scalars['ID']['input'];
};

/** The input fields to remove a video from the `Watched` list of the connected user. */
export type RemoveWatchedVideoInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the video. */
  videoXid: Scalars['String']['input'];
};

/** The return fields from removing a video from the `Watched` list of the connected user. */
export type RemoveWatchedVideoPayload = {
  __typename?: 'RemoveWatchedVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to reorder a media in a collection. */
export type ReorderCollectionMediaInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the collection. */
  collectionXid: Scalars['String']['input'];
  /** The Dailymotion ID of the media. */
  mediaXid: Scalars['String']['input'];
  /** The Dailymotion ID of the target media to switch order with. */
  targetMediaXid: Scalars['String']['input'];
};

/** The return fields from reordering a media in a collection. */
export type ReorderCollectionMediaPayload = {
  __typename?: 'ReorderCollectionMediaPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to reorder a save in a collection. */
export type ReorderSaveInput = {
  /** The ID of the collection. */
  collectionId: Scalars['ID']['input'];
  /** The ID of the save to move. */
  id: Scalars['ID']['input'];
  /** The target ID of the save to swap with. */
  targetId: Scalars['ID']['input'];
};

/** The available input fields to report a `Comment`. */
export type ReportCommentInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "Use `id` input arg.") - The ID of the comment to report. */
  commentId?: InputMaybe<Scalars['ID']['input']>;
  /** The email address of the user making the report. Required when the user is not connected. */
  email?: InputMaybe<Scalars['String']['input']>;
  /** The first name of the user making the report. */
  firstName?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the Comment to report. */
  id?: InputMaybe<Scalars['ID']['input']>;
  /** Language code used to communicate with the reporter. if null will guess from request header */
  languageCode?: InputMaybe<Scalars['String']['input']>;
  /** The last name of the user making the report. */
  lastName?: InputMaybe<Scalars['String']['input']>;
  /** Message body of the report. */
  message?: InputMaybe<Scalars['String']['input']>;
  /** The violation reason to report the comment. */
  violation?: CommentViolation;
};

/** The return fields from reporting a comment. */
export type ReportCommentPayload = {
  __typename?: 'ReportCommentPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to report content. */
export type ReportContentInput = {
  /** The consent of acknowledgments for submitting the report. */
  acknowledgements?: InputMaybe<ReportingAcknowledgements>;
  /** The action request of the report. */
  action?: InputMaybe<ReportingAction>;
  /** The message body of the report. */
  message?: InputMaybe<Scalars['String']['input']>;
  /** The original work that is copyrighted. */
  originalWork?: InputMaybe<CopyrightedContent>;
  /** The information of the reporter submitting the report. */
  reporter: ReporterInput;
  /** The urls of the stories containing the content to report. */
  urls: Array<Scalars['String']['input']>;
  /** The violation reason to report the content. */
  violation: ContentViolation;
};

/** The available input fields to report a `Creator`. */
export type ReportCreatorInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the creator to report. */
  creatorXid: Scalars['String']['input'];
  /** The violation reason to report the creator. */
  violation?: CreatorViolation;
};

/** The return fields from reporting a creator. */
export type ReportCreatorPayload = {
  __typename?: 'ReportCreatorPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The download link of a report file. */
export type ReportFileDownloadLink = Node & {
  __typename?: 'ReportFileDownloadLink';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The link of a report file. */
  link?: Maybe<Scalars['String']['output']>;
};

/** The connection type for ReportFileDownloadLink. */
export type ReportFileDownloadLinkConnection = {
  __typename?: 'ReportFileDownloadLinkConnection';
  /** A list of edges. */
  edges: Array<Maybe<ReportFileDownloadLinkEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type ReportFileDownloadLinkEdge = {
  __typename?: 'ReportFileDownloadLinkEdge';
  /** The item at the end of the edge. */
  node?: Maybe<ReportFileDownloadLink>;
};

/** The input fields to report a Recording. */
export type ReportRecordingInput = {
  /** The email address of the user making the report. Required when the user is not connected. */
  email?: InputMaybe<Scalars['String']['input']>;
  /** The first name of the user making the report. */
  firstName?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the recording to report. */
  id: Scalars['ID']['input'];
  /** The language code to communicate with the reporter. */
  languageCode?: InputMaybe<Scalars['String']['input']>;
  /** The last name of the user making the report. */
  lastName?: InputMaybe<Scalars['String']['input']>;
  /** The message body of the report. */
  message?: InputMaybe<Scalars['String']['input']>;
  /**
   * The timecode where the violation of the recording happens in format "hh:mm:ss" or "mm:ss".
   *   If omitted, indicates whole recording is reported.
   */
  timecode?: InputMaybe<Scalars['String']['input']>;
  /** The violation the recording is violating. */
  violation: RecordingViolation;
};

/** The return fields from reporting a recording. */
export type ReportRecordingPayload = {
  __typename?: 'ReportRecordingPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The return fields from reporting a recording. */
export type ReportStoryPayload = {
  __typename?: 'ReportStoryPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to report a video. */
export type ReportVideoInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The email address of the user making the report. Required when the user is not connected. */
  email?: InputMaybe<Scalars['String']['input']>;
  /** The first name of the user making the report. Required for notifications. (Temporary optional for retro-compat) */
  firstName?: InputMaybe<Scalars['String']['input']>;
  /** Language code used to communicate with the reporter. if null will guess from request header */
  languageCode?: InputMaybe<Scalars['String']['input']>;
  /** The last name of the user making the report. Required for notifications. (Temporary optional for retro-compat) */
  lastName?: InputMaybe<Scalars['String']['input']>;
  /** Message body of the report. */
  message?: InputMaybe<Scalars['String']['input']>;
  /**
   * Video specific time position format "hh:mm:ss" or "mm:ss" where abuse happens.
   *   If omitted, indicates whole video is reported.
   */
  timecode?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "Use `violation` input field.") - The type of report. Valid types are (child_abuse, copyrightowner, crime_apology, disinformation, harmful_for_children, hateful_content, porn, spam, terrorism, violent). */
  type?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the video to report. */
  videoXid: Scalars['String']['input'];
  /** The type of the reported violation. */
  violation?: InputMaybe<RecordingViolation>;
};

/** The return fields from reporting a video. */
export type ReportVideoPayload = {
  __typename?: 'ReportVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Represents the relationship to the owner of the copyrighted work. */
export enum ReporterClaimant {
  /** Represents other -- company, entity, or client. */
  Other = 'OTHER',
  /** Represents self. */
  Self = 'SELF'
}

/** The input to verify user report reporter email */
export type ReporterEmailVerifyInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** Id of the abuse report */
  reportId: Scalars['ID']['input'];
  /** Email verificaiton token */
  verificationToken: Scalars['String']['input'];
};

/** Payload for mutation reporterEmailVerify */
export type ReporterEmailVerifyPayload = {
  __typename?: 'ReporterEmailVerifyPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status: Status;
};

/** The input fields of the reporter submitting the report. */
export type ReporterInput = {
  /** The company legal status of the reporter. */
  companyLegalStatus?: InputMaybe<Scalars['String']['input']>;
  /** The company name of the reporter. */
  companyName?: InputMaybe<Scalars['String']['input']>;
  /** The electronic signature of the reporter. */
  electronicSignature: Scalars['String']['input'];
  /** The email address of the reporter. */
  email: Scalars['String']['input'];
  /** The first name of the reporter. */
  firstName?: InputMaybe<Scalars['String']['input']>;
  /** The last name of the reporter. */
  lastName?: InputMaybe<Scalars['String']['input']>;
  /** The legal name of the reporter. */
  legalName?: InputMaybe<Scalars['String']['input']>;
  /** The role of the reporter. */
  role: ReportingRole;
};

/** The input fields of the acknowledgements for submitting the report. */
export type ReportingAcknowledgements = {
  /** I accept service of process from the person who provided notification or an agent of such person. */
  acceptService?: InputMaybe<Scalars['Boolean']['input']>;
  /** I certify the accuracy of the report. */
  accurate: Scalars['Boolean']['input'];
  /** I state in good faith that the use of the content is unauthorized. */
  copyrightUnauthorized: Scalars['Boolean']['input'];
  /** I acknowledge and agree that the report will be processed in accordance to LCEN. */
  lawConfidenceDigitalEconomy: Scalars['Boolean']['input'];
  /** I acknowledge that the report may result in civil or criminal penalties. */
  legalConsequences: Scalars['Boolean']['input'];
  /** I acknowledge that the report may be used for statistical purposes (including those required by law). */
  statisticalUsage: Scalars['Boolean']['input'];
};

/** The possible actions for submitting a report. */
export enum ReportingAction {
  /** The action to appeal a report. */
  Appeal = 'APPEAL',
  /** The action to submit a report. */
  Report = 'REPORT'
}

/** The possible roles of a reporter submitting a report. */
export enum ReportingRole {
  /** The representation as a company of the reporter. */
  Company = 'COMPANY',
  /** The representation as an individual of the reporter. */
  Individual = 'INDIVIDUAL',
  /** The representation as a legal entity of the reporter. */
  Legal = 'LEGAL'
}

/** The input fields to request an activation code. */
export type RequestActivationCodeInput = {
  /** @deprecated(reason: "No longer required.") The account type of the user. */
  accountType?: InputMaybe<UserActivationCodeAccountType>;
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The validation token received during the sign in request. */
  validationToken: Scalars['String']['input'];
};

/** The return fields from requesting an activation code. */
export type RequestActivationCodePayload = {
  __typename?: 'RequestActivationCodePayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to reset a password. */
export type ResetPasswordInput = {
  /** The activation key received in the email from a recover password request. */
  activationKey: Scalars['String']['input'];
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The email of the user to reset its password. */
  email: Scalars['String']['input'];
  /** The new password for the user. */
  newPassword: Scalars['String']['input'];
  /** The mutation version. */
  version?: InputMaybe<Scalars['Int']['input']>;
};

/** The return fields from resetting a password. */
export type ResetPasswordPayload = {
  __typename?: 'ResetPasswordPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The possible values for the resolution in a Recording. */
export enum Resolution {
  /** Resolution in 1080p (Full HD). */
  Fhd_1080 = 'FHD_1080',
  /** Resolution in 720p (High Definition). */
  Hd_720 = 'HD_720',
  /** Resolution in 1440p (Quad HD). */
  Qhd_1440 = 'QHD_1440',
  /** Resolution in 144p (Low Definition). */
  Sd_144 = 'SD_144',
  /** Resolution in 240p (Low Definition). */
  Sd_240 = 'SD_240',
  /** Resolution in 360p (Standard Definition). */
  Sd_360 = 'SD_360',
  /** Resolution in 384p (Standard Definition). */
  Sd_384 = 'SD_384',
  /** Resolution in 480p (Standard Definition). */
  Sd_480 = 'SD_480',
  /** Resolution in 540p (Standard Definition). */
  Sd_540 = 'SD_540',
  /** Resolution in 2160p (Ultra HD). */
  Uhd_2160 = 'UHD_2160'
}

/** Information about the restriction of a recording. */
export type Restriction = Node & {
  __typename?: 'Restriction';
  /** The code indicates the error type that occurred. */
  code: RestrictionCode;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The summary of the restriction. */
  title?: Maybe<Scalars['String']['output']>;
};

/** The possible values for a restriction code. */
export enum RestrictionCode {
  /** The content is geo blocked */
  GeoRestrictedContent = 'GEO_RESTRICTED_CONTENT',
  /** The content is private */
  PrivateContent = 'PRIVATE_CONTENT',
  /** The content is sensitive */
  SensitiveContent = 'SENSITIVE_CONTENT',
  /** The content is unavailable */
  UnavailableContent = 'UNAVAILABLE_CONTENT'
}

/** The possible values for an Authorization Role. */
export enum Role {
  /** An authorization role that represents an admin. */
  Admin = 'ADMIN',
  /** An authorization role that represents an editor. */
  Editor = 'EDITOR',
  /** An authorization role that represents an owner. */
  Owner = 'OWNER'
}

/** The possible values for a permission on a Role. */
export enum RolePermission {
  /** A permission on the role to create creators. */
  CreateCreators = 'CREATE_CREATORS',
  /** A permission on the role to manage partner reports. */
  ManageAnalyticReports = 'MANAGE_ANALYTIC_REPORTS',
  /** A permission on the role to manage behavior rules. */
  ManageBehaviorRules = 'MANAGE_BEHAVIOR_RULES',
  /** An authorization role that represents an owner. */
  SendTransactionalMail = 'SEND_TRANSACTIONAL_MAIL'
}

/** Represents a rule. */
export type Rule = Node & {
  __typename?: 'Rule';
  /** Indicates if the rule has a complex (customized) condition. */
  complexCondition?: Maybe<Scalars['Boolean']['output']>;
  /** The detailed logic of the rule. */
  condition?: Maybe<Scalars['String']['output']>;
  /** The date and time (ISO 8601 format) when the rule was created. */
  createDate: Scalars['DateTime']['output'];
  /**
   * The creation date and time (DateTime ISO8601) of the feature.
   * @deprecated Use `createDate` field.
   */
  createdAt?: Maybe<Scalars['DateTime']['output']>;
  /** The unique id of the user that created the rule. */
  creatorXid?: Maybe<Scalars['String']['output']>;
  /** A human-readable description of the rule. */
  description?: Maybe<Scalars['String']['output']>;
  /** Indicated whether the feature is enabled. */
  enabled?: Maybe<Scalars['Boolean']['output']>;
  /**
   * End date and time (DateTime ISO8601) of the feature if enabled.
   * @deprecated Use `endDate` field
   */
  endAt?: Maybe<Scalars['DateTime']['output']>;
  /** The date and time (ISO 8601 format) when the rule ends. */
  endDate?: Maybe<Scalars['DateTime']['output']>;
  /** The A/B experiment logic and configuration. */
  experiment?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the rule. */
  name?: Maybe<Scalars['String']['output']>;
  /**
   * Start date and time (DateTime ISO8601) of the feature if enabled.
   * @deprecated Use `starDate` field
   */
  startAt?: Maybe<Scalars['DateTime']['output']>;
  /** The date and time (ISO 8601 format) when the rule starts. */
  startDate?: Maybe<Scalars['DateTime']['output']>;
  /** The tags associated with the rule. Useful for filtering. */
  tags?: Maybe<BehaviorRuleTagConnection>;
  /** The date and time (ISO 8601 format) when the rule was updated. */
  updateDate: Scalars['DateTime']['output'];
  /**
   * The last update date-time of the feature.
   * @deprecated Use `updateDate` field.
   */
  updatedAt?: Maybe<Scalars['DateTime']['output']>;
  /** A unique immutable uuid for the rule (used by experiment). */
  uuid?: Maybe<Scalars['String']['output']>;
};


/** Represents a rule. */
export type RuleTagsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The connection type for Rule. */
export type RuleConnection = {
  __typename?: 'RuleConnection';
  /** A list of edges. */
  edges: Array<Maybe<RuleEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type RuleEdge = {
  __typename?: 'RuleEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Rule>;
};

/** The input fields to add/remove a save to/from a collection. */
export type SaveInput = {
  /** The ID of the collection. */
  collectionId: Scalars['ID']['input'];
  /** The ID to save to the collection. */
  id: Scalars['ID']['input'];
};

/** The return fields from modifying a collection. */
export type SavePayload = {
  __typename?: 'SavePayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Perform a search across resources. */
export type Search = Node & {
  __typename?: 'Search';
  /** The suggestions matched against the search query. */
  autosuggestions?: Maybe<SuggestionConnection>;
  /**
   * The channels matched against the search query.
   * @deprecated Use `search.stories(filter: {story: {eq: CHANNEL}})`
   */
  channels?: Maybe<ChannelConnection>;
  /**
   * The collections that matched against the search query.
   * @deprecated Use `search.stories(filter: {story: {eq: COLLECTION}})`.
   */
  collections?: Maybe<CollectionConnection>;
  /**
   * The hashtags that matched against the search query.
   * @deprecated Use `search.stories(filter: {story: {eq: HASHTAG}})`.
   */
  hashtags?: Maybe<HashtagConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The lives that matched against the search query.
   * @deprecated Use `search.stories(filter: {story: {eq: LIVE}})`.
   */
  lives?: Maybe<LiveConnection>;
  /** The stories that matched against the search query. */
  stories?: Maybe<StoryConnection>;
  /**
   * The topics that matched against the search query.
   * @deprecated No longer supported.
   */
  topics?: Maybe<TopicConnection>;
  /**
   * The videos that matched against the search query.
   * @deprecated Use `search.stories(filter: {story: {eq: VIDEO}})`.
   */
  videos?: Maybe<VideoConnection>;
};


/** Perform a search across resources. */
export type SearchAutosuggestionsArgs = {
  filter: AutoSuggestionFilter;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  query: StringOperator;
};


/** Perform a search across resources. */
export type SearchChannelsArgs = {
  accountType?: InputMaybe<AccountType>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  query: Scalars['String']['input'];
};


/** Perform a search across resources. */
export type SearchCollectionsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  query: Scalars['String']['input'];
};


/** Perform a search across resources. */
export type SearchHashtagsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  query: Scalars['String']['input'];
};


/** Perform a search across resources. */
export type SearchLivesArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  query: Scalars['String']['input'];
};


/** Perform a search across resources. */
export type SearchStoriesArgs = {
  filter?: InputMaybe<StoryFilter>;
  first?: InputMaybe<Scalars['Int']['input']>;
  orderBy?: InputMaybe<StorySort>;
  page?: InputMaybe<Scalars['Int']['input']>;
  query: Scalars['String']['input'];
};


/** Perform a search across resources. */
export type SearchTopicsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  query: Scalars['String']['input'];
};


/** Perform a search across resources. */
export type SearchVideosArgs = {
  createdAfter?: InputMaybe<Scalars['DateTime']['input']>;
  createdBefore?: InputMaybe<Scalars['DateTime']['input']>;
  durationMax?: InputMaybe<Scalars['Int']['input']>;
  durationMin?: InputMaybe<Scalars['Int']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  query: Scalars['String']['input'];
  sort?: InputMaybe<SearchVideoSort>;
};

/** The possible sorts that video search results can be ordered. */
export enum SearchVideoSort {
  /** Sort videos by recently uploaded. */
  Recent = 'RECENT',
  /** Sort videos by relevance. This is the default value. */
  Relevance = 'RELEVANCE',
  /** Sort videos by view count. */
  ViewCount = 'VIEW_COUNT'
}

/** A section is a combination of components. */
export type Section = Node & {
  __typename?: 'Section';
  /** The components associated with the section. */
  components?: Maybe<ComponentConnection>;
  /** The description of the section. */
  description?: Maybe<Scalars['String']['output']>;
  /** The grouping type of the section. */
  groupingType?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the section. */
  name?: Maybe<Scalars['String']['output']>;
  /** The related component of the section (e.g. a topic, a channel). */
  relatedComponent?: Maybe<Component>;
  /** The title of the section. */
  title?: Maybe<Scalars['String']['output']>;
  /** The type of the section. */
  type?: Maybe<Scalars['String']['output']>;
};


/** A section is a combination of components. */
export type SectionComponentsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The connection type for Section. */
export type SectionConnection = {
  __typename?: 'SectionConnection';
  /** A list of edges. */
  edges: Array<Maybe<SectionEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** The input fields of a section context argument. */
export type SectionContextArgument = {
  /** The action gesture performed by the user. */
  actionGesture?: InputMaybe<ActionGesture>;
  /** The list of category IDs. */
  categoryIds?: InputMaybe<Array<InputMaybe<Scalars['Int']['input']>>>;
  /** The Dailymotion ID of the collection. */
  collectionXid?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the media. */
  mediaXid?: InputMaybe<Scalars['String']['input']>;
  /** Indicates whether to opt out of personalized content. Defaults to true. */
  personalizationOptOut?: InputMaybe<Scalars['Boolean']['input']>;
  /** The Dailymotion ID of the topic. */
  topicXid?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the view. */
  viewId?: InputMaybe<Scalars['String']['input']>;
};

/** An edge in a connection. */
export type SectionEdge = {
  __typename?: 'SectionEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Section>;
};

/** The return fields from requesting a new email confirmation code. */
export type SendConfirmEmailCodePayload = {
  __typename?: 'SendConfirmEmailCodePayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to send a message. */
export type SendMessageInput = {
  /** Body of the message. */
  body: MessageBody;
  /** Subject of the message. */
  subject: MessageSubject;
};

/** The return fields from sending a message. */
export type SendMessagePayload = {
  __typename?: 'SendMessagePayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to send a transactional email. */
export type SendTransactionalEmailInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The ID associated with the given email campaign. */
  emailCampaignId: Scalars['String']['input'];
  /** The email service provider where the campaign is stored. */
  emailServiceProvider: Scalars['String']['input'];
  /** The Dailymotion ID of the user to be emailed. */
  userXid: Scalars['String']['input'];
};

/** The return fields from sending a transactional email. */
export type SendTransactionalEmailPayload = {
  __typename?: 'SendTransactionalEmailPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to send a code to verify the email. */
export type SendVerifyEmailCodeInput = {
  /** The token to request a code to verify the email. */
  token: Scalars['String']['input'];
};

/** The return fields to send a code to verify the email. */
export type SendVerifyEmailCodePayload = {
  __typename?: 'SendVerifyEmailCodePayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Represents a sentence with enriched elements */
export type SentenceWithEnrichedElements = {
  __typename?: 'SentenceWithEnrichedElements';
  /** The elements */
  elements: EnrichedElements;
  /** The sentence */
  sentence: Scalars['String']['output'];
};

/** Represents a sentence with segments in the context of a speech to text audio */
export type SentenceWithSegments = {
  __typename?: 'SentenceWithSegments';
  /** The matching emoji that represents the best the sentiment/topic of the sentence */
  emoji?: Maybe<Scalars['String']['output']>;
  /** The end time of the sentence in the audio */
  end: Scalars['Float']['output'];
  /** The important word in the sentence */
  highlightedWord?: Maybe<Scalars['String']['output']>;
  /** The start time of the sentence in the audio */
  start: Scalars['Float']['output'];
  /** The text of the sentence */
  text: Scalars['String']['output'];
  /** The words of the sentence */
  words: Array<Maybe<TextWithTimings>>;
};

/** Information about the share urls. */
export type ShareUrls = {
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The permalink share url of the Story. */
  permalink: Scalars['String']['output'];
};

/** Information about the sharing URL of a media. */
export type SharingUrl = Node & {
  __typename?: 'SharingURL';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The service name of the sharing URL. */
  serviceName?: Maybe<Scalars['String']['output']>;
  /** URL of the sharing URL. */
  url?: Maybe<Scalars['String']['output']>;
};

/** The connection type for Sharing URL. */
export type SharingUrlConnection = {
  __typename?: 'SharingURLConnection';
  /** A list of edges. */
  edges: Array<Maybe<SharingUrlEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type SharingUrlEdge = {
  __typename?: 'SharingURLEdge';
  /** The item at the end of the edge. */
  node?: Maybe<SharingUrl>;
};

/** Information about the social urls of a Channel. */
export type SocialUrls = Node & {
  __typename?: 'SocialUrls';
  /** The facebook url of the channel. */
  facebook?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The instagram url of the channel. */
  instagram?: Maybe<Scalars['String']['output']>;
  /** The pinterest url of the channel. */
  pinterest?: Maybe<Scalars['String']['output']>;
  /** The twitter url of the channel. */
  twitter?: Maybe<Scalars['String']['output']>;
  /** The website url of the channel. */
  website?: Maybe<Scalars['String']['output']>;
};

/** The input fields to update the social urls of a channel. */
export type SocialUrlsInput = {
  /** The facebook url of the channel. */
  facebook?: InputMaybe<Scalars['String']['input']>;
  /** The instagram url of the channel. */
  instagram?: InputMaybe<Scalars['String']['input']>;
  /** The pinterest url of the channel. */
  pinterest?: InputMaybe<Scalars['String']['input']>;
  /** The twitter url of the channel. */
  twitter?: InputMaybe<Scalars['String']['input']>;
  /** The website url of the channel. */
  website?: InputMaybe<Scalars['String']['input']>;
};

/** Represents a sound effect element */
export type SoundEffectElement = {
  __typename?: 'SoundEffectElement';
  /** The highlighted word */
  highlightedWord?: Maybe<Scalars['String']['output']>;
  /** The url */
  url: Scalars['String']['output'];
};

/**
 *
 * The input fields for speech audio conversion.
 *
 */
export type SpeechAudioInput = {
  /**
   *
   *   The URL of the audio file to convert to text.
   *
   */
  audioUrl: Scalars['String']['input'];
  /**
   *
   *   Whether to include emojis in the converted text.
   *
   */
  includesEmojis?: InputMaybe<Scalars['Boolean']['input']>;
  /**
   *
   *   The language code for the audio. Use 'auto' for automatic detection.
   *
   */
  languageCode?: Scalars['String']['input'];
};

/** The possible values for a provider used for speech to text. */
export enum SpeechToTextProvider {
  /** A provider that represents Assembly AI. */
  AssemblyAi = 'ASSEMBLY_AI',
  /** A provider that represents Gladia. */
  Gladia = 'GLADIA',
  /** A provider that represents Speechmatics. */
  Speechmatics = 'SPEECHMATICS'
}

/** The possible values for a mutation status. */
export enum Status {
  /** Returned when the mutation is done, and effective. */
  Success = 'SUCCESS'
}

/** Types that can be a Story. */
export type Story = Channel | Collection | ContentCategory | Hashtag | Live | Poll | Reaction | ReactionVideo | Topic | Video;

/** The connection type for a Story. */
export type StoryConnection = {
  __typename?: 'StoryConnection';
  /** A list of edges. */
  edges: Array<Maybe<StoryEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type StoryEdge = {
  __typename?: 'StoryEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Story>;
};

/** The available input fields of a StoryFilter filter. */
export type StoryFilter = {
  /** Filter stories by the account of the creator. */
  account?: InputMaybe<AccountOperator>;
  /** Filter stories by create date. */
  createDate?: InputMaybe<DateTimeOperator>;
  /** Filter stories by the duration (in seconds) of its recordings. */
  duration?: InputMaybe<IntOperator>;
  /** Filter stories by story type. */
  story?: InputMaybe<StoryOperator>;
};

/** The available input fields of a story operator. */
export type StoryOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<StoryTypename>;
  /** Short for in array, must be an element of the array. */
  in?: InputMaybe<Array<StoryTypename>>;
};

/** Sort stories by the available values. */
export type StorySort = {
  /** Sort stories by when they were created. */
  createDate?: InputMaybe<OrderDirection>;
  /** Sort stories by its relevance to the search query. */
  relevance?: InputMaybe<OrderDirection>;
  /** Sort stories by number of views. */
  views?: InputMaybe<OrderDirection>;
};

/** The possible types for a story. */
export enum StoryTypename {
  /** A story that represents a `channel`. */
  Channel = 'CHANNEL',
  /** A story that represents a `collection`. */
  Collection = 'COLLECTION',
  /** A story that represents a `content_category`. */
  ContentCategory = 'CONTENT_CATEGORY',
  /** A story that represents a `hashtag`. */
  Hashtag = 'HASHTAG',
  /** A story that represents a `live`. */
  Live = 'LIVE',
  /** A story that represents a `poll`. */
  Poll = 'POLL',
  /** A story that represents a `reaction`. */
  Reaction = 'REACTION',
  /**
   * A story that represents a `reaction_video`.
   * @deprecated Use `REACTION`.
   */
  ReactionVideo = 'REACTION_VIDEO',
  /** A story that represents a `topic`. */
  Topic = 'TOPIC',
  /** A story that represents a `video`. */
  Video = 'VIDEO'
}

/** Information about the stream urls of a Recording. */
export type StreamUrls = {
  /** The hls url of the recording. */
  hls: Scalars['String']['output'];
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The available input fields of for a String operator. */
export type StringOperator = {
  /** Short for equal, must match the given data exactly. */
  eq?: InputMaybe<Scalars['String']['input']>;
  /** Short for in array, must be an element of the array. */
  in?: InputMaybe<Array<Scalars['String']['input']>>;
};

/** Information about a Subdivision. */
export type Subdivision = Node & {
  __typename?: 'Subdivision';
  /** The ISO 3166-2 subdivision code. */
  codeAlpha2?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** Information about the subtitle of a media. */
export type Subtitle = Node & {
  __typename?: 'Subtitle';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The language of the subtitle. */
  language?: Maybe<Language>;
  /** The URL of the subtitle. */
  url?: Maybe<Scalars['String']['output']>;
  /** The Dailymotion ID of the subtitle. */
  xid: Scalars['String']['output'];
};

/** The connection type for Subtitle. */
export type SubtitleConnection = {
  __typename?: 'SubtitleConnection';
  /** A list of edges. */
  edges: Array<Maybe<SubtitleEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type SubtitleEdge = {
  __typename?: 'SubtitleEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Subtitle>;
};

/** Information about the suggestion. */
export type Suggestion = Node & {
  __typename?: 'Suggestion';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The name of the suggestion. */
  name: Scalars['String']['output'];
};

/** The connection type for Suggestion. */
export type SuggestionConnection = {
  __typename?: 'SuggestionConnection';
  /** A list of edges. */
  edges: Array<Maybe<SuggestionEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type SuggestionEdge = {
  __typename?: 'SuggestionEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Suggestion>;
};

/** Information about a supported country. */
export type SupportedCountry = Node & {
  __typename?: 'SupportedCountry';
  /** The supported country. */
  country?: Maybe<Country>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The language of the supported country. */
  language?: Maybe<Language>;
};

/** The connection type for SupportedCountry. */
export type SupportedCountryConnection = {
  __typename?: 'SupportedCountryConnection';
  /** A list of edges. */
  edges: Array<Maybe<SupportedCountryEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type SupportedCountryEdge = {
  __typename?: 'SupportedCountryEdge';
  /** The item at the end of the edge. */
  node?: Maybe<SupportedCountry>;
};

/** Information about a supported language. */
export type SupportedLanguage = Node & {
  __typename?: 'SupportedLanguage';
  /** The countries supported by the supported language. */
  countries?: Maybe<CountryConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The supported language. */
  language?: Maybe<Language>;
};


/** Information about a supported language. */
export type SupportedLanguageCountriesArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The connection type for SupportedLanguage. */
export type SupportedLanguageConnection = {
  __typename?: 'SupportedLanguageConnection';
  /** A list of edges. */
  edges: Array<Maybe<SupportedLanguageEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type SupportedLanguageEdge = {
  __typename?: 'SupportedLanguageEdge';
  /** The item at the end of the edge. */
  node?: Maybe<SupportedLanguage>;
};

/** Represents a text with timings in the context of a speech to text audio */
export type TextWithTimings = {
  __typename?: 'TextWithTimings';
  /** The end time of the text in the audio */
  end: Scalars['Float']['output'];
  /** The start time of the text in the audio */
  start: Scalars['Float']['output'];
  /** The text */
  text: Scalars['String']['output'];
};

/** Represents a Thread. */
export type Thread = {
  /** The story that elicited a response. */
  opener?: Maybe<Story>;
};

/** The connection type for Thread. */
export type ThreadConnection = {
  __typename?: 'ThreadConnection';
  /** A list of edges. */
  edges: Array<Maybe<ThreadEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type ThreadEdge = {
  __typename?: 'ThreadEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Thread>;
};

/** The available input fields of a Thread filter. */
export type ThreadFilter = {
  /** Filter threads by the id. */
  id?: InputMaybe<IdOperator>;
  /** Filter threads by the openerId. */
  openerId?: InputMaybe<IdOperator>;
  /** Filter threads by thread typename. */
  thread?: InputMaybe<ThreadOperator>;
};

/** The node at the end of a ThreadMetricEdge. */
export type ThreadMetric = Metric & Node & {
  __typename?: 'ThreadMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the thread metric. A null value indicates that it is hidden or not available. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a ThreadMetric. */
export type ThreadMetricConnection = {
  __typename?: 'ThreadMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<ThreadMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type ThreadMetricEdge = {
  __typename?: 'ThreadMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<ThreadMetric>;
};

/** The available input fields of a story operator. */
export type ThreadOperator = {
  /** Short for in array, must be an element of the array. */
  in?: InputMaybe<Array<ThreadTypename>>;
};

/** The possible types for a thread. */
export enum ThreadTypename {
  /** A thread that represents a `comment`. */
  Comment = 'COMMENT',
  /** A thread that represents a `poll`. */
  Poll = 'POLL',
  /** A thread that represents a `Prompt`. */
  Prompt = 'PROMPT',
  /** A thread that represents a `reaction`. */
  Reaction = 'REACTION',
  /** A thread that represents a `Title`. */
  Title = 'TITLE'
}

/** The available height sizes for an Thumbnail. */
export enum ThumbnailHeight {
  /** A portrait image with 60px */
  Portrait_60 = 'PORTRAIT_60',
  /** A portrait image with 120px */
  Portrait_120 = 'PORTRAIT_120',
  /** A portrait image with 180px */
  Portrait_180 = 'PORTRAIT_180',
  /** A portrait image with 240px */
  Portrait_240 = 'PORTRAIT_240',
  /** A portrait image with 360px */
  Portrait_360 = 'PORTRAIT_360',
  /** A portrait image with 480px */
  Portrait_480 = 'PORTRAIT_480',
  /** A portrait image with 720px */
  Portrait_720 = 'PORTRAIT_720',
  /** A portrait image with 1080px */
  Portrait_1080 = 'PORTRAIT_1080'
}

/** The thumbnail image URLs of an object. */
export type Thumbnails = Node & {
  __typename?: 'Thumbnails';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The URL of the thumbnail image in height 60px. */
  x60?: Maybe<Scalars['String']['output']>;
  /** The URL of the thumbnail image in height 240px. */
  x240?: Maybe<Scalars['String']['output']>;
};

/** Tips and Tricks notification settings. */
export type Tips = Node & {
  __typename?: 'Tips';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the email notification setting is enabled. */
  isEmailEnabled?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the push notification setting is enabled. */
  isPushEnabled?: Maybe<Scalars['Boolean']['output']>;
};

/** Information about a Title. */
export type Title = Node & Thread & {
  __typename?: 'Title';
  /** The chatroom associated with the Title. */
  chatroom?: Maybe<Chatroom>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The metrics of the Title. */
  metrics?: Maybe<TitleMetrics>;
  /** The story that elicited the Title. */
  opener?: Maybe<Story>;
  /** Indicates whether the creator of the story has liked the Title. */
  openerCreatorLiked: Scalars['Boolean']['output'];
  /** The content of the Title. */
  text: Scalars['String']['output'];
  /** The viewer engagement information of the Title. */
  viewerEngagement?: Maybe<TitleViewerEngagement>;
};

/** The engagement metrics of a Title. */
export type TitleEngagementMetrics = Node & {
  __typename?: 'TitleEngagementMetrics';
  /** The bookmark metrics of the Title. */
  bookmarks?: Maybe<BookmarkMetricConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};


/** The engagement metrics of a Title. */
export type TitleEngagementMetricsBookmarksArgs = {
  filter?: InputMaybe<BookmarkFilter>;
};

/** The metrics of a Title. */
export type TitleMetrics = Node & {
  __typename?: 'TitleMetrics';
  /** The engagement metrics of the Title. */
  engagement?: Maybe<TitleEngagementMetrics>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** Information about the viewer engagement of a Title. */
export type TitleViewerEngagement = Node & ViewerEngagement & {
  __typename?: 'TitleViewerEngagement';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates the like rating of the post from the viewer. */
  likeRating?: Maybe<LikeRating>;
  /** Indicates whether the viewer has liked the Title. Returns False if the viewer is not connected. */
  liked?: Maybe<Scalars['Boolean']['output']>;
};

/** A topic represents a keyword that is associated to a media. */
export type Topic = Node & {
  __typename?: 'Topic';
  /** The collection of the topic. */
  collection?: Maybe<Collection>;
  /** The URL of the cover image. */
  coverURL?: Maybe<Scalars['String']['output']>;
  /** The date and time (ISO 8601 format) when the topic was created. */
  createDate: Scalars['DateTime']['output'];
  /**
   * The creation date (DateTime ISO8601) of the topic.
   * @deprecated Use `createDate` field.
   */
  createdAt?: Maybe<Scalars['DateTime']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether topic is followed by the connected user. Returns `False` if not connected. */
  isFollowed?: Maybe<Scalars['Boolean']['output']>;
  /** The name of the topic. */
  name?: Maybe<Scalars['String']['output']>;
  /** Other names of the topic. */
  names?: Maybe<TopicLabelConnection>;
  /** The share urls of the topic. */
  shareUrls?: Maybe<TopicShareUrls>;
  /** The stats of the topic. */
  stats?: Maybe<TopicStats>;
  /**
   * The thumbnails associated to the topic.
   * @deprecated Use `coverURL` field.
   */
  thumbnails?: Maybe<Thumbnails>;
  /** The date and time (ISO 8601 format) when the topic was updated. */
  updateDate: Scalars['DateTime']['output'];
  /**
   * The update date (DateTime ISO8601) of the topic.
   * @deprecated Use `updateDate` field.
   */
  updatedAt?: Maybe<Scalars['DateTime']['output']>;
  /**
   * The URL of the topic.
   * @deprecated Use `shareUrls.permalink` field.
   */
  url?: Maybe<Scalars['String']['output']>;
  /** The videos of the topic. */
  videos?: Maybe<VideoConnection>;
  /** The whitelist status of the topic. */
  whitelistStatus?: Maybe<TopicWhitelistStatus>;
  /** The Dailymotion ID of the topic. */
  xid: Scalars['String']['output'];
};


/** A topic represents a keyword that is associated to a media. */
export type TopicCollectionArgs = {
  country?: InputMaybe<Scalars['String']['input']>;
};


/** A topic represents a keyword that is associated to a media. */
export type TopicCoverUrlArgs = {
  size: Scalars['String']['input'];
};


/** A topic represents a keyword that is associated to a media. */
export type TopicNamesArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A topic represents a keyword that is associated to a media. */
export type TopicVideosArgs = {
  allowExplicit?: InputMaybe<Scalars['Boolean']['input']>;
  createdAfter?: InputMaybe<Scalars['Date']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  isHD?: InputMaybe<Scalars['Boolean']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<Scalars['String']['input']>;
};

/** The connection type for Topic. */
export type TopicConnection = {
  __typename?: 'TopicConnection';
  /** A list of edges. */
  edges: Array<Maybe<TopicEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type TopicEdge = {
  __typename?: 'TopicEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Topic>;
};

/** The label of a topic. */
export type TopicLabel = Node & {
  __typename?: 'TopicLabel';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The language of the topic label. */
  language?: Maybe<Language>;
  /** The name of the topic label. */
  name?: Maybe<Scalars['String']['output']>;
};

/** The connection type for TopicLabel. */
export type TopicLabelConnection = {
  __typename?: 'TopicLabelConnection';
  /** A list of edges. */
  edges: Array<Maybe<TopicLabelEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type TopicLabelEdge = {
  __typename?: 'TopicLabelEdge';
  /** The item at the end of the edge. */
  node?: Maybe<TopicLabel>;
};

/** Information about the share urls of a Topic. */
export type TopicShareUrls = Node & ShareUrls & {
  __typename?: 'TopicShareUrls';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The permalink share url of the topic. */
  permalink: Scalars['String']['output'];
};

/** Information about the topic stats. */
export type TopicStats = Node & {
  __typename?: 'TopicStats';
  /** The follower stats of the topic. */
  followers?: Maybe<TopicStatsFollowers>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The video stats of the video. */
  videos?: Maybe<TopicStatsVideos>;
};

/** The follower stats of the topic. */
export type TopicStatsFollowers = Node & {
  __typename?: 'TopicStatsFollowers';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of users following the topic. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The video stats of the video. */
export type TopicStatsVideos = Node & {
  __typename?: 'TopicStatsVideos';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of videos of the topic. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** Information about the whitelist of a topic. */
export type TopicWhitelistStatus = Node & {
  __typename?: 'TopicWhitelistStatus';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the topic is whitelisted. */
  isWhitelisted?: Maybe<Scalars['Boolean']['output']>;
  /** The mode used to whitelist the topic. */
  mode?: Maybe<TopicWhitelistStatusMode>;
  /** The date (DateTime ISO8601) the topic was whitelisted. */
  whitelistedAt?: Maybe<Scalars['DateTime']['output']>;
};

/** The mode used to whitelist a topic. */
export enum TopicWhitelistStatusMode {
  /** The topic has been whitelisted automatically. */
  Auto = 'AUTO',
  /** The topic has been whitelisted manually. */
  Manual = 'MANUAL'
}

/** The input fields to unfollow a channel for the connected user. */
export type UnfollowChannelInput = {
  /** The Dailymotion ID of the channel to unfollow. */
  channelXid: Scalars['String']['input'];
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
};

/** The return fields from unfollowing a channel for the connected user. */
export type UnfollowChannelPayload = {
  __typename?: 'UnfollowChannelPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to unfollow a topic for the connected user. */
export type UnfollowTopicInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the topic to unfollow. */
  topicXid: Scalars['String']['input'];
};

/** The return fields from unfollowing a topic for the connected user. */
export type UnfollowTopicPayload = {
  __typename?: 'UnfollowTopicPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to unfollow a user for the connected user. */
export type UnfollowUserInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the user. */
  xid: Scalars['String']['input'];
};

/** The return fields from unfollowing a chanusernel for the connected user. */
export type UnfollowUserPayload = {
  __typename?: 'UnfollowUserPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to unlike a video for the connected user. */
export type UnlikeVideoInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the video to unlike. */
  videoXid: Scalars['String']['input'];
};

/** The return fields from unliking a video for the connected user. */
export type UnlikeVideoPayload = {
  __typename?: 'UnlikeVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to update a behavior rule. */
export type UpdateBehaviorRuleInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** Condition when to apply the rule (contains JSON). */
  condition?: InputMaybe<Scalars['String']['input']>;
  /** Description of the rule. */
  description?: InputMaybe<Scalars['String']['input']>;
  /** Indicate whether the rule/condition is enabled. */
  enabled?: InputMaybe<Scalars['Boolean']['input']>;
  /** @deprecated(reason: "Use `endDate` input field.") - End date and time (DateTime ISO8601) of the rule if enabled. */
  endAt?: InputMaybe<Scalars['DateTime']['input']>;
  /** The date and time (ISO 8601 format) when the rule ends. */
  endDate?: InputMaybe<Scalars['DateTime']['input']>;
  /** Experiment configuration. If set, this rule will be an experiment (contains JSON). */
  experiment?: InputMaybe<Scalars['String']['input']>;
  /** The name of the selected rule to update. */
  name: Scalars['String']['input'];
  /** Change the rule name. */
  newName?: InputMaybe<Scalars['String']['input']>;
  /** Start date and time (DateTime ISO8601) of the rule if enabled. */
  startAt?: InputMaybe<Scalars['DateTime']['input']>;
  /** @deprecated(reason: "Use `startDate` input field.") - The date and time (ISO 8601 format) when the rule starts. */
  startDate?: InputMaybe<Scalars['DateTime']['input']>;
  /** The tags associated with the rule. Useful for filtering. */
  tags?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};

/** The return fields from updating a rule used for feature flipping or AB experiments. */
export type UpdateBehaviorRulePayload = {
  __typename?: 'UpdateBehaviorRulePayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The updated rule. */
  rule?: Maybe<Rule>;
};

/** The input fields to update a channel. */
export type UpdateChannelInput = {
  /** The url of the avatar of the channel. Send `null` to remove the avatar url. */
  avatarUrl?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The country of the channel. */
  country?: InputMaybe<Scalars['String']['input']>;
  /** The description of the channel. */
  description?: InputMaybe<Scalars['String']['input']>;
  /** The display name of the channel. */
  displayName?: InputMaybe<Scalars['String']['input']>;
  /** @deprecate(reason: "Use `socialUrls`.") - The external links of the channel. */
  externalLinks?: InputMaybe<ChannelExternalLinksInput>;
  /** The language of the channel. */
  language?: InputMaybe<Scalars['String']['input']>;
  /** The username of the channel. */
  name?: InputMaybe<Scalars['String']['input']>;
  /** @deprecate(reason: "Use `updateChannelSettings`.") - The settings on a channel. */
  settings?: InputMaybe<ChannelSettingsInput>;
  /** The social urls of the channel. */
  socialUrls?: InputMaybe<SocialUrlsInput>;
  /** The username of the channel. */
  username?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the channel to update. */
  xid: Scalars['String']['input'];
};

/** The return fields from updating a channel. */
export type UpdateChannelPayload = {
  __typename?: 'UpdateChannelPayload';
  /** The updated channel. */
  channel?: Maybe<Channel>;
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to update a collection. */
export type UpdateCollectionInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The Dailymotion ID of the collection to update. */
  collectionXid: Scalars['String']['input'];
  /** The description of the collection. */
  description?: InputMaybe<Scalars['String']['input']>;
  /** The name of the collection. */
  name?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "settings.visibility` input arg.") - Indicate whether the collection is private. */
  private?: InputMaybe<Scalars['Boolean']['input']>;
  /** The settings when updating a collection. */
  settings?: InputMaybe<CollectionSettingsInput>;
};

/** The return fields from updating a collection. */
export type UpdateCollectionPayload = {
  __typename?: 'UpdateCollectionPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The updated collection. */
  collection?: Maybe<Collection>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to update the email notification settings of the connected user. */
export type UpdateNotificationSettingsEmailInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** Indicate whether to receive notification when a channel you follow starts a live. */
  followingChannelStartsLive?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to receive when a channel you follow uploads a new video. */
  followingChannelUploadsVideo?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to receive occasionally about monetization insigths. */
  monetizationInsights?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to receive occasionally about product updates. */
  productUpdates?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to receive notification occasionally to remind unwatched videos; videos from `WatchLater`. */
  remindUnwatchedVideos?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to receive `tips & tricks`. */
  tips?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to receive `curated videos for you` occasionally. */
  videoDigest?: InputMaybe<Scalars['Boolean']['input']>;
};

/** The return fields from updating the email notification settings of the connected user. */
export type UpdateNotificationSettingsEmailPayload = {
  __typename?: 'UpdateNotificationSettingsEmailPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The udpated email notification settngs. */
  notificationSettings?: Maybe<NotificationSettings>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to update the push notification settings of the connected user. */
export type UpdateNotificationSettingsPushInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** Indicate whether to receive notification when a channel you follow starts a live. */
  followingStartsLive?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to receive notification occasionally to remind unwatched videos; videos from `WatchLater`. */
  remindUnwatchedVideos?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to receive `tips & tricks`. */
  tips?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicate whether to receive `curated videos for you` occasionally. */
  videoDigest?: InputMaybe<Scalars['Boolean']['input']>;
};

/** The return fields from updating the email notification settings of the connected user. */
export type UpdateNotificationSettingsPushPayload = {
  __typename?: 'UpdateNotificationSettingsPushPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The updated push notification settings. */
  notificationSettings?: Maybe<NotificationSettings>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to update a reaction. */
export type UpdateReactionInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** the hashtags of the reaction */
  hashtags?: InputMaybe<Array<Scalars['String']['input']>>;
  /** The ID of the reaction to update. */
  id: Scalars['ID']['input'];
  /** The title of the reaction. */
  title?: InputMaybe<Scalars['String']['input']>;
};

/** The input fields to update a user. */
export type UpdateUserInput = {
  /** The Apple ID of the user. */
  appleID?: InputMaybe<Scalars['String']['input']>;
  /** The URL of the avatar image of the user. */
  avatarURL?: InputMaybe<Scalars['String']['input']>;
  /** The birthday (DateTime ISO8601) of the user. */
  birthday?: InputMaybe<Scalars['DateTime']['input']>;
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "Use mutation `emailChangeRequest`.") - The email address of the user. */
  email?: InputMaybe<Scalars['String']['input']>;
  /** The Facebook ID of the user. */
  facebookID?: InputMaybe<Scalars['String']['input']>;
  /** The first name of the user. */
  firstName?: InputMaybe<Scalars['String']['input']>;
  /** The gender of the user. */
  gender?: InputMaybe<Gender>;
  /** The Googleplus ID of the user. */
  googleplusID?: InputMaybe<Scalars['String']['input']>;
  lastName?: InputMaybe<Scalars['String']['input']>;
  /** The Microsoft ID of the user. */
  microsoftID?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "Use `firstName` and `lastName` respectively.") - The name of the user. */
  name?: InputMaybe<Scalars['String']['input']>;
  /** The new password for the user. */
  newPassword?: InputMaybe<Scalars['String']['input']>;
  /**
   * @deprecated(reason: "Use mutation `updateChannel` and input arg `name`.") - The nickname of the user.
   *   Must be between 3 and 29 characters, can contain any [a-zA-Z0-9._-] characters,
   *   and start with alphanumeric and numbers, and have at least one [a-zA-Z_-] character.
   */
  nickname?: InputMaybe<Scalars['String']['input']>;
  /** The old password of the user. */
  oldPassword?: InputMaybe<Scalars['String']['input']>;
  /**
   * @deprecated(reason: "Use mutation `updateChannel` and input arg `name`.") - The username of the user.
   *   Must be between 3 and 29 characters, can contain any [a-zA-Z0-9._-] characters,
   *   and start with alphanumeric and numbers, and have at least one [a-zA-Z_-] character.
   */
  username?: InputMaybe<Scalars['String']['input']>;
  /** The mutation version. */
  version?: InputMaybe<Scalars['Int']['input']>;
};

/** The return fields to updating a user. */
export type UpdateUserPayload = {
  __typename?: 'UpdateUserPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
  /** The updated Dailymotion user. */
  user?: Maybe<User>;
};

/** The input fields to update a video. */
export type UpdateVideoInput = {
  /** Whether the video is AI-altered content. */
  aiAltered?: InputMaybe<Scalars['Boolean']['input']>;
  /** @deprecated(reason: "Use `settings.audience` input arg.") - Indicates the target audience the video is created for. */
  audience?: InputMaybe<AudienceGuide>;
  /** The category of the video. */
  category?: InputMaybe<MediaCategory>;
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The description of the video. */
  description?: InputMaybe<Scalars['String']['input']>;
  /** Indicates whether the video is exclusive to Dailymotion. */
  exclusive?: InputMaybe<Scalars['Boolean']['input']>;
  /** the hashtags of the video */
  hashtags?: InputMaybe<Array<Scalars['String']['input']>>;
  /** @deprecated(reason: "Use `audience` input arg.") - Indicates whether the video is created for kids. */
  isCreatedForKids?: InputMaybe<Scalars['Boolean']['input']>;
  /** The language of the video. */
  language?: InputMaybe<Scalars['String']['input']>;
  /** Indicate whether the video has paid partnership. */
  paidPartnership?: InputMaybe<Scalars['Boolean']['input']>;
  /** The password of the video. When setting a value on this field, the video visibility changes to `password protected`. */
  password?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "Use `settings.visibility` input arg.") - Indicates whether the video is private. */
  private?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicates whether the video is published. */
  published?: InputMaybe<Scalars['Boolean']['input']>;
  /** The settings of the video. */
  settings?: InputMaybe<VideoSettingsInput>;
  /** The list of tags to associate to the video. */
  tags?: InputMaybe<Array<Scalars['String']['input']>>;
  /** The title of the video. */
  title?: InputMaybe<Scalars['String']['input']>;
  /** The URL of the video. */
  url?: InputMaybe<Scalars['String']['input']>;
  /** @deprecated(reason: "Use `settings.visibility` input arg.")  - The visibility of the Video. */
  visibility?: InputMaybe<Visibility>;
  /** The Dailymotion ID of the video to update. */
  xid: Scalars['String']['input'];
};

/** The return fields from updating a video. */
export type UpdateVideoPayload = {
  __typename?: 'UpdateVideoPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
  /** The updated video. */
  video?: Maybe<Video>;
};

/** The possible values which uploaded video connections can be sorted by. */
export enum UploadedVideoSort {
  /** Sort uploaded video by most recent. */
  Recent = 'RECENT',
  /** Sort uploaded video by most viewed. */
  Visited = 'VISITED'
}

/** A user object contains information about a Dailymotion user. */
export type User = Node & {
  __typename?: 'User';
  /** The account status of the user. */
  accountStatus?: Maybe<Scalars['String']['output']>;
  /**
   * The account type of the user (viewer, partner, partner-verified).
   * @deprecated Use `channel.account`.
   */
  accountType?: Maybe<Scalars['String']['output']>;
  /** The Apple ID of the user. */
  appleID?: Maybe<Scalars['String']['output']>;
  /**
   * The URL of the avatar image.
   * @deprecated Use `channel.avatar`.
   */
  avatarURL?: Maybe<Scalars['String']['output']>;
  /** The user's birthday in (DateTime ISO8601). */
  birthday?: Maybe<Scalars['DateTime']['output']>;
  /**
   * Indicates whether the user can access to partner HQ.
   * @deprecated Use `organizations`.
   */
  canAccessPartnerHQ: Scalars['Boolean']['output'];
  /**
   * Indicates whether the user can change its nickname.
   * @deprecated Use `channel.canChangeName`.
   */
  canChangeNickname: Scalars['Boolean']['output'];
  /**
   * Indicates whether the user can change its username.
   * @deprecated Use `channel.canChangeName`.
   */
  canChangeUsername: Scalars['Boolean']['output'];
  /** The channel of the Partner. */
  channel?: Maybe<Channel>;
  /**
   * The collections created by the user.
   * @deprecated Use `channel.collections`.
   */
  collections?: Maybe<CollectionConnection>;
  /** The country of the user. */
  country?: Maybe<Country>;
  /**
   * The URL of the cover image.
   * @deprecated Use `channel.banner`.
   */
  coverURL?: Maybe<Scalars['String']['output']>;
  /** The date and time (ISO 8601 format) when the user was created. */
  createDate: Scalars['DateTime']['output'];
  /**
   * The creation date (DateTime ISO8601) of the user.
   * @deprecated Use `createDate` field.
   */
  createdAt?: Maybe<Scalars['DateTime']['output']>;
  /** The email address of the user. */
  email?: Maybe<Scalars['String']['output']>;
  /** Information about the email change request of the user. */
  emailChangeRequest?: Maybe<EmailChangeRequest>;
  /** Indicates whether the email of the user is verified. */
  emailVerified: Scalars['Boolean']['output'];
  /** The Facebook ID of the user. */
  facebookID?: Maybe<Scalars['String']['output']>;
  /** The first name of the user. */
  firstName?: Maybe<Scalars['String']['output']>;
  /**
   * The channels the user is following.
   * @deprecated Use `channel.followings` field.
   */
  followedChannels?: Maybe<FollowedChannelConnection>;
  /**
   * The topics the user is following.
   * @deprecated No longer supported.
   */
  followedTopics?: Maybe<FollowedTopicConnection>;
  /**
   * The users that are following the user.
   * @deprecated Use `channel.followers` field.
   */
  followers?: Maybe<FollowerConnection>;
  /**
   * The list of users the requested user is following.
   * @deprecated Use `channel.followings` field.
   */
  following?: Maybe<FollowingConnection>;
  /**
   * The channels the user is following.
   * @deprecated Use `followedChannels` field.
   */
  followingChannels?: Maybe<ChannelConnection>;
  /**
   * The topics the user is following.
   * @deprecated Use `followedTopics` field.
   */
  followingTopics?: Maybe<TopicConnection>;
  /** The first and last name of the user. */
  fullName?: Maybe<Scalars['String']['output']>;
  /** The gender of the user. */
  gender?: Maybe<Gender>;
  /** The Googleplus ID of the user. */
  googleplusID?: Maybe<Scalars['String']['output']>;
  /**
   * Indicates whether the user has a channel.
   * @deprecated Use `channel` field, if not null, then true.
   */
  hasChannel: Scalars['Boolean']['output'];
  /** Indicates whether the user has any linked social accounts. */
  hasLinkedSocialAccounts: Scalars['Boolean']['output'];
  /** Indicates whether the user has any (pending or active) organization memberships. */
  hasOrganizationMemberships: Scalars['Boolean']['output'];
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The interests of the user.
   * @deprecated No longer supported.
   */
  interests?: Maybe<UserInterestConnection>;
  /** Indicates whether the user is an admin. */
  isAdmin: Scalars['Boolean']['output'];
  /**
   * Indicates whether the user is confirmed.
   * @deprecated Use `emailVerified`.
   */
  isConfirmed: Scalars['Boolean']['output'];
  /** Indicates whether the user can mass report videos as copyright owner. */
  isCopyrightOwnerMassReport: Scalars['Boolean']['output'];
  /**
   * Indicates whether the user is followed by the connected user. Returns `False` if no user is connected.
   * @deprecated Use `channel.followerEngagement` field.
   */
  isFollowed: Scalars['Boolean']['output'];
  /** The language of the user. */
  language?: Maybe<Language>;
  /** The last name of the user. */
  lastName?: Maybe<Scalars['String']['output']>;
  /**
   * The medias the user has liked.
   * @deprecated Use `channel.bookmarks(filter: { bookmark: { eq: LIKE }})`.
   */
  likedMedias?: Maybe<MediaConnection>;
  /**
   * The videos the user has liked.
   * @deprecated Use `likedMedias` field.
   */
  likedVideos?: Maybe<VideoConnection>;
  /** The Microsoft ID of the user. */
  microsoftID?: Maybe<Scalars['String']['output']>;
  /**
   * The name of the user.
   * @deprecated Use `fullname`.
   */
  name?: Maybe<Scalars['String']['output']>;
  /**
   * The nickname of the user.
   * @deprecated Use `channel.name`
   */
  nickname?: Maybe<Scalars['String']['output']>;
  /**
   * The notification settings of the user.
   * @deprecated Use `me.channel.settings.notifications`.
   */
  notificationSettings?: Maybe<NotificationSettings>;
  /** The organizations created by the user. */
  organizations?: Maybe<OrganizationConnection>;
  /**
   * The advanced data available if the user is a partner.
   * @deprecated No longer supported.
   */
  partner?: Maybe<Partner>;
  /**
   * The reaction videos created by the user.
   * @deprecated Use `channel.reactions`.
   */
  reactionVideos?: Maybe<ReactionVideoConnection>;
  /**
   * The sharingURL of the user.
   * @deprecated Use `channel.shareUrls.permalink`.
   */
  sharingURL?: Maybe<Scalars['String']['output']>;
  /**
   * The stats of the user.
   * @deprecated Use `channel.metrics`.
   */
  stats?: Maybe<UserStats>;
  /** The videos from the subscriptions of channels and topics followed by the user. */
  subscriptions?: Maybe<VideoConnection>;
  /**
   * The videos the user has uploaded.
   * @deprecated Use `channel.videos`.
   */
  uploadedVideos?: Maybe<VideoConnection>;
  /**
   * The username of the user. Must be between 3 and 29 characters, can contain any [a-zA-Z0-9._-] characters,
   *   and start with alphanumeric and numbers, and have at least one [a-zA-Z_-] character.
   * @deprecated Use `channel.name`
   */
  username?: Maybe<Scalars['String']['output']>;
  /**
   * The videos the user has saved to watch later.
   * @deprecated Use `watchLaterMedias` field.
   */
  watchLater?: Maybe<VideoConnection>;
  /**
   * The medias the user has saved to watch later.
   * @deprecated Use `channel.bookmarks` with `filter: { bookmark: { eq: FAVORITE }}`.
   */
  watchLaterMedias?: Maybe<MediaConnection>;
  /**
   * The medias the user has watched.
   * @deprecated Use `channel.history` with `filter: { activity: { eq: WATCHED }}`.
   */
  watchedMedias?: Maybe<MediaConnection>;
  /**
   * The videos the user has watched.
   * @deprecated Use `watchedMedias` field.
   */
  watchedVideos?: Maybe<VideoConnection>;
  /** The Dailymotion ID of the user. */
  xid: Scalars['String']['output'];
};


/** A user object contains information about a Dailymotion user. */
export type UserAvatarUrlArgs = {
  size: Scalars['String']['input'];
};


/** A user object contains information about a Dailymotion user. */
export type UserCollectionsArgs = {
  collectionXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  createdAfter?: InputMaybe<Scalars['Date']['input']>;
  createdBefore?: InputMaybe<Scalars['Date']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  hasPublicVideos?: InputMaybe<Scalars['Boolean']['input']>;
  isPrivate?: InputMaybe<Scalars['Boolean']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  search?: InputMaybe<Scalars['String']['input']>;
  sort?: InputMaybe<UserCollectionsSort>;
  videoXid?: InputMaybe<Scalars['String']['input']>;
};


/** A user object contains information about a Dailymotion user. */
export type UserCoverUrlArgs = {
  size: Scalars['String']['input'];
};


/** A user object contains information about a Dailymotion user. */
export type UserFollowedChannelsArgs = {
  channelXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<FollowedChannelsSort>;
};


/** A user object contains information about a Dailymotion user. */
export type UserFollowedTopicsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<FollowedTopicsSort>;
  topicXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


/** A user object contains information about a Dailymotion user. */
export type UserFollowersArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A user object contains information about a Dailymotion user. */
export type UserFollowingArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A user object contains information about a Dailymotion user. */
export type UserFollowingChannelsArgs = {
  channelXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<UserFollowingChannelsSort>;
};


/** A user object contains information about a Dailymotion user. */
export type UserFollowingTopicsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<UserFollowingTopicsSort>;
  topicXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


/** A user object contains information about a Dailymotion user. */
export type UserInterestsArgs = {
  enabledOnly?: InputMaybe<Scalars['Boolean']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A user object contains information about a Dailymotion user. */
export type UserLikedMediasArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  mediaXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<LikedMediaSort>;
  types?: InputMaybe<Array<InputMaybe<MediaType>>>;
};


/** A user object contains information about a Dailymotion user. */
export type UserLikedVideosArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A user object contains information about a Dailymotion user. */
export type UserOrganizationsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  xids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


/** A user object contains information about a Dailymotion user. */
export type UserReactionVideosArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** A user object contains information about a Dailymotion user. */
export type UserSubscriptionsArgs = {
  createdAfter?: InputMaybe<Scalars['DateTime']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  type: UserSubscriptionsType;
};


/** A user object contains information about a Dailymotion user. */
export type UserUploadedVideosArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  privacy?: InputMaybe<RecordingPrivacy>;
  sort?: InputMaybe<UploadedVideoSort>;
  videoXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
};


/** A user object contains information about a Dailymotion user. */
export type UserWatchLaterArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<Scalars['String']['input']>;
};


/** A user object contains information about a Dailymotion user. */
export type UserWatchLaterMediasArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  mediaXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  page?: InputMaybe<Scalars['Int']['input']>;
  sort?: InputMaybe<Scalars['String']['input']>;
  types?: InputMaybe<Array<InputMaybe<MediaType>>>;
};


/** A user object contains information about a Dailymotion user. */
export type UserWatchedMediasArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  mediaXids?: InputMaybe<Array<InputMaybe<Scalars['String']['input']>>>;
  page?: InputMaybe<Scalars['Int']['input']>;
  types?: InputMaybe<Array<InputMaybe<MediaType>>>;
};


/** A user object contains information about a Dailymotion user. */
export type UserWatchedVideosArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The possible values for a user account. */
export enum UserActivationCodeAccountType {
  /** Partner account type. */
  Partner = 'PARTNER',
  /** Viewer account type. */
  Viewer = 'VIEWER'
}

/** The possible sort values to order the collections belonging to a user. */
export enum UserCollectionsSort {
  /** Sort collections alphabetically. */
  Alphaaz = 'ALPHAAZ',
  /** Sort collections by changed date. */
  Changed = 'CHANGED',
  /** Sort collections by most recent. */
  Recent = 'RECENT'
}

/** The input fields to confirm an email change. */
export type UserEmailChangeConfirmInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The confirmation code received from the email change request. */
  code: Scalars['String']['input'];
};

/** The return fields from confirming an email change. */
export type UserEmailChangeConfirmPayload = {
  __typename?: 'UserEmailChangeConfirmPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to request an email change. */
export type UserEmailChangeRequestInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The new email for the connected user. */
  email: Scalars['String']['input'];
  /** The password of the connected user. */
  password: Scalars['String']['input'];
};

/** The return fields from requesting an email change. */
export type UserEmailChangeRequestPayload = {
  __typename?: 'UserEmailChangeRequestPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to request a new email confirmation code. */
export type UserEmailConfirmationCodeResetInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
};

/** The return fields from requesting a new email confirmation code. */
export type UserEmailConfirmationCodeResetPayload = {
  __typename?: 'UserEmailConfirmationCodeResetPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to generate an email validation token. */
export type UserEmailValidationTokenInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The email address of the user. */
  email: Scalars['String']['input'];
};

/** The return fields from requesting an email validation token. */
export type UserEmailValidationTokenPayload = {
  __typename?: 'UserEmailValidationTokenPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The email validation token to request an activation code. */
  emailValidationToken: Scalars['String']['output'];
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The possible sort values to order the channels followed by a user. */
export enum UserFollowingChannelsSort {
  /** Sort by activity. */
  Activity = 'ACTIVITY',
  /** Sort alphabetically. */
  Alphaaz = 'ALPHAAZ',
  /** Sort by creation date. */
  Recent = 'RECENT'
}

/** The possible sort values to order the topics followed by a user. */
export enum UserFollowingTopicsSort {
  /** Sort by activity. */
  Activity = 'ACTIVITY',
  /** Sort alphabetically. */
  Alphaaz = 'ALPHAAZ',
  /** Sort by creation date. */
  Recent = 'RECENT'
}

/** Information about the user interests. */
export type UserInterest = Node & {
  __typename?: 'UserInterest';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The interest of the user. */
  interest?: Maybe<Interest>;
};

/** The input fields to add an interest to a user. */
export type UserInterestAddInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the interest. */
  interestId: Scalars['Int']['input'];
};

/** The return fields from adding an interest of the user. */
export type UserInterestAddPayload = {
  __typename?: 'UserInterestAddPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The connection type for UserInterest. */
export type UserInterestConnection = {
  __typename?: 'UserInterestConnection';
  /** A list of edges. */
  edges: Array<Maybe<UserInterestEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type UserInterestEdge = {
  __typename?: 'UserInterestEdge';
  /** The item at the end of the edge. */
  node?: Maybe<UserInterest>;
};

/** The input fields to remove an interest from a user. */
export type UserInterestRemoveInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the interest. */
  interestId: Scalars['Int']['input'];
};

/** The return fields from removing an interest from the user. */
export type UserInterestRemovePayload = {
  __typename?: 'UserInterestRemovePayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to replace the interests of a user. */
export type UserInterestsUpdateInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the interest. */
  interestIds: Array<Scalars['Int']['input']>;
};

/** The return fields from replacing the interests of the user. */
export type UserInterestsUpdatePayload = {
  __typename?: 'UserInterestsUpdatePayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to request a code B of OpenWeb service. */
export type UserOpenWebCodeBRequestInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The session ID received from client side. */
  codeA: Scalars['String']['input'];
};

/** The return fields from confirming an email change. */
export type UserOpenWebCodeBRequestPayload = {
  __typename?: 'UserOpenWebCodeBRequestPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The code B provided by OpenWeb service. */
  codeB: Scalars['String']['output'];
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** A user's voting information for a poll. */
export type UserPollAnswer = Node & {
  __typename?: 'UserPollAnswer';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The identifier of the answer option selected by the user. */
  optionId: Scalars['ID']['output'];
};

/** Information about the user stats. */
export type UserStats = Node & {
  __typename?: 'UserStats';
  /** The stats of the collections of the user. */
  collections?: Maybe<UserStatsCollections>;
  /** The stats of the followers of the user. */
  followers?: Maybe<UserStatsFollowers>;
  /** The stats of the channel followed by the user. */
  followingChannels?: Maybe<UserStatsFollowingChannels>;
  /** The stats of the topics followed by the user. */
  followingTopics?: Maybe<UserStatsFollowingTopics>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The stats of the liked videos of the user. */
  likedVideos?: Maybe<UserStatsLikedVideos>;
  /** The stats of the reaction videos of the user. */
  reactionVideos?: Maybe<UserStatsReactionVideos>;
  /** The stats of the uploaded videos (not including lives) of the user. */
  uploadedVideos?: Maybe<UserStatsUploadedVideos>;
  /** The stats of the videos of the user. */
  videos?: Maybe<UserStatsVideos>;
  /** The stats of the videos to watch later for the user. */
  watchLater?: Maybe<UserStatsWatchLater>;
  /** The stats of the watched videos of the user. */
  watchedVideos?: Maybe<UserStatsWatchedVideos>;
};

/** The stats of the collections of the user. */
export type UserStatsCollections = Node & {
  __typename?: 'UserStatsCollections';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of collections of the user. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The stats of the followers of the user. */
export type UserStatsFollowers = Node & {
  __typename?: 'UserStatsFollowers';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of followers of the user. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The stats of the channel followed by the user. */
export type UserStatsFollowingChannels = Node & {
  __typename?: 'UserStatsFollowingChannels';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of channels followed by the user. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The stats of the topics followed by the user. */
export type UserStatsFollowingTopics = Node & {
  __typename?: 'UserStatsFollowingTopics';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of followed topics count. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The stats of the liked videos of the user. */
export type UserStatsLikedVideos = Node & {
  __typename?: 'UserStatsLikedVideos';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of videos the user has liked. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The stats of the reaction videos of the user. */
export type UserStatsReactionVideos = Node & {
  __typename?: 'UserStatsReactionVideos';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of reaction videos the user has created. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The stats of the uploaded videos (not including lives) of the user. */
export type UserStatsUploadedVideos = Node & {
  __typename?: 'UserStatsUploadedVideos';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of videos the user has uploaded. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The stats of the videos of the user. */
export type UserStatsVideos = Node & {
  __typename?: 'UserStatsVideos';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of videos of the user. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The stats of the videos to watch later for the user. */
export type UserStatsWatchLater = Node & {
  __typename?: 'UserStatsWatchLater';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of videos the user has saved to watch later. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The stats of the watched videos of the user. */
export type UserStatsWatchedVideos = Node & {
  __typename?: 'UserStatsWatchedVideos';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of videos the user has watched. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The possible subscription types for the user. */
export enum UserSubscriptionsType {
  /** Get subscriptions from followed channels. */
  Channel = 'CHANNEL',
  /** Get subscriptions from followed topics. */
  Topic = 'TOPIC'
}

/** Information about a video. */
export type Video = Content & Node & Recording & {
  __typename?: 'Video';
  /** Indicates whether the video is AI-altered content. */
  aiAltered?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the video can be embedded outside of Dailymotion.
   * @deprecated Use `settings.embeddable` field.
   */
  allowEmbed?: Maybe<Scalars['Boolean']['output']>;
  /** The aspect ratio of the video (e.g. 1.33333 for 4/3, 1.77777 for 16/9). */
  aspectRatio?: Maybe<Scalars['Float']['output']>;
  /** Indicates the target audience the video is created for. */
  audience?: Maybe<AudienceGuide>;
  /**
   * The best available quality of the video.
   * @deprecated Use `quality` field.
   */
  bestAvailableQuality?: Maybe<MediaQuality>;
  /** Indicates whether advertisements are allowed on the video. */
  canDisplayAds?: Maybe<Scalars['Boolean']['output']>;
  /** The categories of the video. */
  categories?: Maybe<CategoryConnection>;
  /**
   * The category of the video.
   * @deprecated Use `categories` field.
   */
  category?: Maybe<MediaCategory>;
  /**
   * The channel that created the video.
   * @deprecated Use `creator` field.
   */
  channel?: Maybe<Channel>;
  /** The chapters of the video. */
  chapters?: Maybe<ChapterConnection>;
  /** The channel claiming revenue sharing on the video. */
  claimer?: Maybe<Channel>;
  /**
   * The collections where the video is saved.
   * @deprecated Use `me.collections` with argument `videoXid`.
   */
  collections?: Maybe<CollectionConnection>;
  /** The comments of the video. */
  comments?: Maybe<CommentConnection>;
  /** The date and time (ISO 8601 format) when the video was created. */
  createDate: Scalars['DateTime']['output'];
  /**
   * The creation date (DateTime ISO8601) of the video.
   * @deprecated Use `createDate` field.
   */
  createdAt?: Maybe<Scalars['DateTime']['output']>;
  /** The creator that created the video. */
  creator?: Maybe<Channel>;
  /**
   * The curated categories associated to the video.
   * @deprecated Use `interests` field.
   */
  curatedCategories?: Maybe<CuratedCategoryConnection>;
  /**
   * The description of the video in utf8.
   *   Clients are expected to handle '<br/>' tags and detect 'http(s)://' links.
   *   No other HTML tag should be present.
   */
  description?: Maybe<Scalars['String']['output']>;
  /** The duration of the video in seconds. */
  duration?: Maybe<Scalars['Int']['output']>;
  /** The embed details of the video. */
  embed?: Maybe<Embed>;
  /**
   * The HTML embedding code to embed the video outside of Dailymotion.
   * @deprecated Use `embed.html` field.
   */
  embedHtml?: Maybe<Scalars['String']['output']>;
  /**
   * The URL to embed the video outside of Dailymotion.
   * @deprecated Use `embed.url` field.
   */
  embedURL?: Maybe<Scalars['String']['output']>;
  /** Indicates whether the video is exclusive to Dailymotion. */
  exclusive?: Maybe<Scalars['Boolean']['output']>;
  /** The URL of the first frame. */
  firstFrame?: Maybe<Image>;
  /** The geoblocked countries of the video. */
  geoblockedCountries?: Maybe<GeoblockedCountries>;
  /** The country codes (ISO 3166-1 alpha-2) that are allowed or denied by the video. */
  geoblocking?: Maybe<GeoblockingConnection>;
  /** Indicates whether the video has a fingerprint. */
  hasFingerprint?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the video has perspective videos associated with it. */
  hasPerspective?: Maybe<Scalars['Boolean']['output']>;
  /** The hashtags of the video. */
  hashtags?: Maybe<HashtagConnection>;
  /** The height of the video (px). */
  height?: Maybe<Scalars['Int']['output']>;
  /**
   * The URL of the adaptative bitrate manifest using the Apple HTTP Live Streaming
   *   protocol. Without an access token this field contains null, the Dailymotion
   *   user associated with the access token must be the owner of the video. This
   *   field is rate limited. The returned url is secured: it can only be consumed by
   *   the user who made the query and it expires after a certain time.
   * @deprecated Use `hlsUrl` field.
   */
  hlsURL?: Maybe<Scalars['String']['output']>;
  /**
   * The URL of the adaptative bitrate manifest using the Apple HTTP Live Streaming
   *   protocol. Without an access token this field contains null, the Dailymotion
   *   user associated with the access token must be the owner of the video. This
   *   field is rate limited. The returned url is secured: it can only be consumed by
   *   the user who made the query and it expires after a certain time.
   * @deprecated Use `streamUrls.hls`.
   */
  hlsUrl?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The interests associated to the video.
   * @deprecated No longer supported.
   */
  interests?: Maybe<InterestConnection>;
  /** Indicates whether the video is 360°. */
  is360?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether advertising is blocked on the video.
   * @deprecated Use `settings.adsStreamable` field.
   */
  isAdvertisingBlocked?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the video is bookmarked by the connected user.
   *   Returns False if the user is not connected.
   * @deprecated Use `viewerEngagement.bookmarked` field.
   */
  isBookmarked?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether comments is enabled on the video.
   * @deprecated Use `settings.threadsDisabled` field.
   */
  isCommentsEnabled?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the video is "Created for Kids" (intends to target an audience of age 16 and under).
   * @deprecated Use `audience` field.
   */
  isCreatedForKids?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the video can be downloaded.
   * @deprecated Use `settings.downloadable` field.
   */
  isDownloadable?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the video is explicit.
   * @deprecated Use `audience` field.
   */
  isExplicit?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the video is in the specified collection. */
  isInCollection?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the video is in the watch later list of the connected user.
   *   Returns False if the user is not connected.
   * @deprecated Use `viewerEngagement.favorited` field.
   */
  isInWatchLater?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the connected user has liked the video.
   * @deprecated Use `viewerEngagement.liked` field.
   */
  isLiked?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the video is password-protected. */
  isPasswordProtected?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the video is private.
   * @deprecated Use `visibility` field.
   */
  isPrivate?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the video is published. */
  isPublished?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the connected user has reacted to the video.
   *   Returns False if the user is not connected.
   * @deprecated Use `viewerEngagement.reacted` field.
   */
  isReacted?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether reaction videos are allowed on the video.
   * @deprecated Use `settings.threadsDisabled` field.
   */
  isReactionVideosEnabled?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the current user has started watching the video.
   * @deprecated Use `viewerEngagement.watchStarted` field.
   */
  isWatched?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates whether the current user has completely watched video.
   * @deprecated Use `viewerEngagement.watchCompleted` field.
   */
  isWatchedComplete?: Maybe<Scalars['Boolean']['output']>;
  /** The language of the video. */
  language?: Maybe<Language>;
  /** The metrics of the video. */
  metrics?: Maybe<VideoMetrics>;
  /** The moderation information of the video. */
  moderation?: Maybe<MediaModeration>;
  /**
   * The next set of videos after the video.
   * @deprecated No longer supported.
   */
  nextVideos?: Maybe<VideoConnection>;
  /** Indicates whether the video has paid partnership. */
  paidPartnership?: Maybe<Scalars['Boolean']['output']>;
  /** The resolution quality of the the video. */
  quality?: Maybe<Quality>;
  /**
   * The reaction videos created on the video.
   * @deprecated Use `reactions` field.
   */
  reactionVideos?: Maybe<ReactionVideoConnection>;
  /** The reactions created on the video. */
  reactions?: Maybe<ReactionConnection>;
  /**
   * The related videos to the video.
   * @deprecated Use `conversations(filter: { algorithm: { eq: DISCOVER } } )`.
   */
  relatedVideos?: Maybe<VideoConnection>;
  /** The restriction information of the video. */
  restriction?: Maybe<Restriction>;
  /** The settings of the video. */
  settings?: Maybe<VideoSettings>;
  /** The share urls of the video. */
  shareUrls?: Maybe<VideoShareUrls>;
  /**
   * The sharing URLs of the video.
   * @deprecated Use `shareUrls` field.
   */
  sharingURLs?: Maybe<SharingUrlConnection>;
  /** The spritesheet details of the video. */
  spritesheet?: Maybe<Image>;
  /** The spritesheet seeker details of the video. */
  spritesheetSeeker?: Maybe<Image>;
  /**
   * The stats of the video.
   * @deprecated Use `metrics` field.
   */
  stats?: Maybe<VideoStats>;
  /** The current status of the video. */
  status?: Maybe<VideoStatus>;
  /** The stream urls of the video. */
  streamUrls?: Maybe<VideoStreamUrls>;
  /** The subtitles of the video. */
  subtitles?: Maybe<SubtitleConnection>;
  /** The tags of the video. */
  tags?: Maybe<MediaTagConnection>;
  /** The URL of the thumbnail image. */
  thumbnail?: Maybe<Image>;
  /**
   * The URL of the thumbnail image.
   * @deprecated Use `thumbnail` field.
   */
  thumbnailURL?: Maybe<Scalars['String']['output']>;
  /**
   * The thumbnails of the video.
   * @deprecated Use `thumbnailURL` field.
   */
  thumbnails?: Maybe<Thumbnails>;
  /** The title of the video. */
  title?: Maybe<Scalars['String']['output']>;
  /**
   * The list of topics related to the media.
   * @deprecated No longer supported.
   */
  topics?: Maybe<TopicConnection>;
  /** The transcript of the video. */
  transcript?: Maybe<CaptionConnection>;
  /** The date and time (ISO 8601 format) when the video was updated. */
  updateDate: Scalars['DateTime']['output'];
  /**
   * The last update date (DateTime ISO8601) of the video.
   * @deprecated Use `updateDate` field.
   */
  updatedAt?: Maybe<Scalars['DateTime']['output']>;
  /** The upload info of the video, read-only for the owner of the video. */
  uploadInfo?: Maybe<MediaUploadInfo>;
  /**
   * The URL of the video.
   * @deprecated Use `shareUrls.permalink` field.
   */
  url?: Maybe<Scalars['String']['output']>;
  /**
   * The total number of views of the video.
   * @deprecated Use `stats.views.total` field.
   */
  viewCount?: Maybe<Scalars['Int']['output']>;
  /** The viewer engagement information of the video. */
  viewerEngagement?: Maybe<VideoViewerEngagement>;
  /** The visibility of the Video. */
  visibility?: Maybe<Visibility>;
  /** The width of the video (px). */
  width?: Maybe<Scalars['Int']['output']>;
  /** The Dailymotion ID of the video. */
  xid: Scalars['String']['output'];
};


/** Information about a video. */
export type VideoCategoriesArgs = {
  filter: CategoryFilter;
};


/** Information about a video. */
export type VideoChaptersArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoCollectionsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoCommentsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  orderBy?: InputMaybe<CommentSort>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoCuratedCategoriesArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoFirstFrameArgs = {
  height: ThumbnailHeight;
};


/** Information about a video. */
export type VideoGeoblockingArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  isAllowed?: InputMaybe<Scalars['Boolean']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoHashtagsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoInterestsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoIsInCollectionArgs = {
  collectionXid: Scalars['String']['input'];
};


/** Information about a video. */
export type VideoLanguageArgs = {
  auto?: Scalars['Boolean']['input'];
  source?: InputMaybe<LanguageSource>;
};


/** Information about a video. */
export type VideoNextVideosArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoQualityArgs = {
  auto?: InputMaybe<Scalars['Boolean']['input']>;
};


/** Information about a video. */
export type VideoReactionVideosArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoReactionsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoRelatedVideosArgs = {
  algorithm?: InputMaybe<VideoRelatedAlgo>;
  context?: InputMaybe<RelatedVideoContext>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoSharingUrLsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoSubtitlesArgs = {
  auto?: Scalars['Boolean']['input'];
  autoGenerated?: InputMaybe<Scalars['Boolean']['input']>;
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoTagsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};


/** Information about a video. */
export type VideoThumbnailArgs = {
  height: ThumbnailHeight;
};


/** Information about a video. */
export type VideoThumbnailUrlArgs = {
  size: Scalars['String']['input'];
};


/** Information about a video. */
export type VideoTopicsArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
  whitelistedOnly?: InputMaybe<Scalars['Boolean']['input']>;
};


/** Information about a video. */
export type VideoTranscriptArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The connection type for Video. */
export type VideoConnection = {
  __typename?: 'VideoConnection';
  /** A list of edges. */
  edges: Array<Maybe<VideoEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** Curated videos for you notification settings. */
export type VideoDigest = Node & {
  __typename?: 'VideoDigest';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the email notification setting is enabled. */
  isEmailEnabled?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the push notification setting is enabled. */
  isPushEnabled?: Maybe<Scalars['Boolean']['output']>;
};

/** An edge in a connection. */
export type VideoEdge = {
  __typename?: 'VideoEdge';
  /** The item at the end of the edge. */
  node?: Maybe<Video>;
};

/** The engagement metrics of a Video. */
export type VideoEngagementMetrics = Node & PostEngagementMetrics & {
  __typename?: 'VideoEngagementMetrics';
  /** The bookmark metrics of the video. */
  bookmarks?: Maybe<BookmarkMetricConnection>;
  /** The comment metrics of the video. */
  comments?: Maybe<CommentMetricConnection>;
  /** The heart metrics of the video. */
  hearts?: Maybe<HeartMetricConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The like metrics of the video.
   * @deprecated No longer supported.
   */
  likes?: Maybe<LikeMetricConnection>;
  /**
   * The point metrics of the video.
   * @deprecated Use `metrics.engagement.hearts(filter: { emoji: { eq: PINK_HEART }})`.
   */
  points?: Maybe<PointMetricConnection>;
  /** The reaction metrics of the video. */
  reactions?: Maybe<ReactionMetricConnection>;
  /** The thread metrics of the video. */
  threads?: Maybe<ThreadMetricConnection>;
};


/** The engagement metrics of a Video. */
export type VideoEngagementMetricsBookmarksArgs = {
  filter?: InputMaybe<BookmarkFilter>;
};


/** The engagement metrics of a Video. */
export type VideoEngagementMetricsHeartsArgs = {
  filter?: InputMaybe<HeartFilter>;
};


/** The engagement metrics of a Video. */
export type VideoEngagementMetricsLikesArgs = {
  filter?: InputMaybe<LikeMetricFilter>;
};

/** The available input fields of a Video filter. */
export type VideoFilter = {
  /** Filter videos by its target audience. */
  audience?: InputMaybe<AudienceGuideOperator>;
  /** Filter videos by categoryId. */
  categoryId?: InputMaybe<IdOperator>;
  /** Filter videos by visibility. */
  visibility?: InputMaybe<VisibilityOperator>;
};

/** The node at the end of a VideoMetricEdge. */
export type VideoMetric = Metric & Node & {
  __typename?: 'VideoMetric';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total count of the video metric.  A null value indicates that it is hidden or not available.. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The connection type for a VideoMetric. */
export type VideoMetricConnection = {
  __typename?: 'VideoMetricConnection';
  /** A list of edges. */
  edges: Array<Maybe<VideoMetricEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type VideoMetricEdge = {
  __typename?: 'VideoMetricEdge';
  /** The item at the end of the edge. */
  node?: Maybe<VideoMetric>;
};

/** The metrics of a Video. */
export type VideoMetrics = Node & PostMetrics & {
  __typename?: 'VideoMetrics';
  /** The engagement metrics of the video. */
  engagement?: Maybe<VideoEngagementMetrics>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The view metrics of the video. */
  views?: Maybe<VideoViewMetrics>;
};

/** Types that can be a VideoOrLive. */
export type VideoOrLive = Live | Video;

/** The possible values for video related algorithms. */
export enum VideoRelatedAlgo {
  /** Only uploader. */
  UploaderOnly = 'UPLOADER_ONLY',
  /** Uploader with children channels. */
  UploaderWithChildren = 'UPLOADER_WITH_CHILDREN',
  /** Uploader with parent channel. */
  UploaderWithParent = 'UPLOADER_WITH_PARENT',
  /** Uploader with siblong channels. */
  UploaderWithSiblings = 'UPLOADER_WITH_SIBLINGS'
}

/** Information about the settings of a Video. */
export type VideoSettings = Node & {
  __typename?: 'VideoSettings';
  /** Indicates whether ads can be streamed while the video plays. */
  adsStreamable?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the video can be downloaded. */
  downloadable?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the video can be embedded. */
  embeddable?: Maybe<Scalars['Boolean']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether threads (comments and reactions) are disabled. */
  threadsDisabled?: Maybe<Scalars['Boolean']['output']>;
};

/** The default settings when creating a video. */
export type VideoSettingsInput = {
  /** Indicates the target audience the video is created for. */
  audience?: InputMaybe<AudienceGuide>;
  /** Indicate whether responses (comments or reactions) are disabled by default when creating a video. */
  threadsDisabled?: InputMaybe<Scalars['Boolean']['input']>;
  /** Indicates the visibility of the video. */
  visibility?: InputMaybe<Visibility>;
};

/** Information about the share urls of a Video. */
export type VideoShareUrls = Node & ShareUrls & {
  __typename?: 'VideoShareUrls';
  /** The facebook share url of the video. */
  facebook?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The permalink share url of the video. */
  permalink: Scalars['String']['output'];
  /** The twitter share url of the video. */
  twitter?: Maybe<Scalars['String']['output']>;
};

/** Information about the video stats. */
export type VideoStats = Node & {
  __typename?: 'VideoStats';
  /**
   * The bookmark stats of the video.
   * @deprecated Use `video.metrics.engagement.bookmarks`.
   */
  bookmarks?: Maybe<VideoStatsBookmarks>;
  /**
   * The favorite stats of the video.
   * @deprecated Use `video.metrics.engagement.bookmarks(filter: {bookmark: {eq: FAVORITE}})`.
   */
  favorites?: Maybe<VideoStatsFavorites>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * The like stats of the video.
   * @deprecated Use `video.metrics.engagement.likes`.
   */
  likes?: Maybe<VideoStatsLikes>;
  /**
   * The reaction stats of the video.
   * @deprecated Use `video.metrics.engagement.reactions`.
   */
  reactionVideos?: Maybe<VideoStatsReactionVideos>;
  /**
   * The saves stats of the video.
   * @deprecated Use `video.metrics.engagement.bookmarks(filter: {bookmark: {eq: SAVE}})`.
   */
  saves?: Maybe<VideoStatsSaves>;
  /**
   * The view stats of the video.
   * @deprecated Use `video.metrics.views.visits`.
   */
  views?: Maybe<VideoStatsViews>;
};

/** The bookmark stats of the video. */
export type VideoStatsBookmarks = Node & {
  __typename?: 'VideoStatsBookmarks';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of bookmarks of the video. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The favorite stats of the video. */
export type VideoStatsFavorites = Node & {
  __typename?: 'VideoStatsFavorites';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of favorites of the video. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The like stats of the video. */
export type VideoStatsLikes = Node & {
  __typename?: 'VideoStatsLikes';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of likes of the video. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The reaction video stats of the video. */
export type VideoStatsReactionVideos = Node & {
  __typename?: 'VideoStatsReactionVideos';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of reaction videos of the video. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The saves stats of the video. */
export type VideoStatsSaves = Node & {
  __typename?: 'VideoStatsSaves';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of playlists and watchlater added of the video. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The view stats of the video. */
export type VideoStatsViews = Node & {
  __typename?: 'VideoStatsViews';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The total number of views of the video. */
  total?: Maybe<Scalars['Int']['output']>;
};

/** The possible values for a video status. */
export enum VideoStatus {
  /** The video has been deleted. */
  Deleted = 'DELETED',
  /**
   * The video has an encoding error.
   * @deprecated Use `ERROR`.
   */
  EncodingError = 'ENCODING_ERROR',
  /** The video has an error. */
  Error = 'ERROR',
  /** The video is processing. */
  Processing = 'PROCESSING',
  /** The video is published. */
  Published = 'PUBLISHED',
  /** The video is ready. */
  Ready = 'READY',
  /** The video has been rejected. */
  Rejected = 'REJECTED',
  /** The video is unknown. */
  Unknown = 'UNKNOWN'
}

/** Information about the stream urls of a Video. */
export type VideoStreamUrls = Node & StreamUrls & {
  __typename?: 'VideoStreamUrls';
  /** The audio url of the video stream. */
  audio?: Maybe<Scalars['String']['output']>;
  /** The chromecast url of the video stream. */
  chromecast?: Maybe<Scalars['String']['output']>;
  /** The h264 URL of the video stream. */
  h264?: Maybe<Scalars['String']['output']>;
  /** The hls url of the video stream. */
  hls: Scalars['String']['output'];
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The preview url of the video stream. */
  preview?: Maybe<Scalars['String']['output']>;
};


/** Information about the stream urls of a Video. */
export type VideoStreamUrlsH264Args = {
  resolution: Resolution;
};


/** Information about the stream urls of a Video. */
export type VideoStreamUrlsPreviewArgs = {
  resolution: Resolution;
};

/** Contains the different streams available for a video. */
export type VideoStreams = Node & {
  __typename?: 'VideoStreams';
  /** The audio URL of the video stream. */
  audioURL?: Maybe<Scalars['String']['output']>;
  /** The chromecast URL of the video stream. */
  chromecastURL?: Maybe<Scalars['String']['output']>;
  /** The h264 URL of the video stream. */
  h264URL?: Maybe<Scalars['String']['output']>;
  /**
   * The URL of the adaptative bitrate manifest using the Apple HTTP Live Streaming
   *   protocol. Without an access token this field contains null, the Dailymotion
   *   user associated with the access token must be the owner of the video. This
   *   field is rate limited. The returned url is secured: it can only be consumed by
   *   the user who made the query and it expires after a certain time.
   */
  hlsURL?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The preview URL of the video stream. */
  previewURL?: Maybe<Scalars['String']['output']>;
  /**
   * The restriction information of the video stream.
   * @deprecated Use `video.restriction`.
   */
  restriction?: Maybe<Restriction>;
  /** The Dailymotion ID of the video. */
  xid: Scalars['String']['output'];
};


/** Contains the different streams available for a video. */
export type VideoStreamsH264UrlArgs = {
  quality: Scalars['String']['input'];
};


/** Contains the different streams available for a video. */
export type VideoStreamsPreviewUrlArgs = {
  quality: Scalars['String']['input'];
};

/** The connection type for Video Streams. */
export type VideoStreamsConnection = {
  __typename?: 'VideoStreamsConnection';
  /** A list of edges. */
  edges: Array<Maybe<VideoStreamsEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type VideoStreamsEdge = {
  __typename?: 'VideoStreamsEdge';
  /** The item at the end of the edge. */
  node?: Maybe<VideoStreams>;
};

/** The view metrics of a Video. */
export type VideoViewMetrics = Node & {
  __typename?: 'VideoViewMetrics';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The view metrics of the video. */
  visits?: Maybe<ChannelMetricConnection>;
};

/** Information about the viewer engagement of a Video. */
export type VideoViewerEngagement = Node & ViewerEngagement & {
  __typename?: 'VideoViewerEngagement';
  /**
   * Indicates whether the video is bookmarked by the viewer. Returns False if the viewer is not connected.
   * @deprecated Use `favorited`, `liked`, or `saved`.
   */
  bookmarked?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the post is commented by the connected user. Returns False if the user is not connected.  */
  commented?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the viewer has the video in its watch later list. Returns False if the viewer is not connected. */
  favorited?: Maybe<Scalars['Boolean']['output']>;
  /**
   * Indicates the heart likeness the viewer has given to the Video.
   * @deprecated Use `hearts.emoji`.
   */
  hearted?: Maybe<Hearted>;
  /** Indicates the heart rating the viewer has given to the Video. */
  hearts?: Maybe<HeartRating>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /**
   * Indicates the like rating of the video from the viewer.
   * @deprecated No longer supported. Use `hearted`.
   */
  likeRating?: Maybe<LikeRating>;
  /**
   * Indicates whether the viewer has liked the video. Returns False if the viewer is not connected.
   * @deprecated No longer supported. Use `hearted`.
   */
  liked?: Maybe<Scalars['Boolean']['output']>;
  /**
   * The amount of points given from the viewer to the Video.
   * @deprecated Use `hearts.amount`.
   */
  points?: Maybe<Scalars['Int']['output']>;
  /** Indicates whether the viewer has reacted to the video. Returns False if the viewer is not connected. */
  reacted?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the viewer has added the video to one of its collections. Returns False if the viewer is not connected. */
  saved?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the viewer has completed watching the video. Returns False if the viewer is not connected. */
  watchCompleted?: Maybe<Scalars['Boolean']['output']>;
  /** Indicates whether the viewer has started watching the video. Returns False if the viewer is not connected. */
  watchStarted?: Maybe<Scalars['Boolean']['output']>;
};

/** The context of the viewer. */
export type ViewerContext = {
  /** The following context of the viewer. */
  following?: InputMaybe<FollowingContext>;
  /** The history context of the viewer. */
  history?: InputMaybe<HistoryContext>;
};

/** Information about the viewer engagement of a Post. */
export type ViewerEngagement = {
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates the like rating of the post from the viewer. */
  likeRating?: Maybe<LikeRating>;
  /** Indicates whether the viewer has liked the post. Returns False if the viewer is not connected. */
  liked?: Maybe<Scalars['Boolean']['output']>;
};

/** Information of the views to build efficient UIs. */
export type Views = Node & {
  __typename?: 'Views';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The neon app. */
  neon?: Maybe<Neon>;
};

/** The visibility of a content. */
export enum Visibility {
  /** Offsite only - content that is not viewable on dailymotion but on other sites. */
  Hidden = 'HIDDEN',
  /** Limited access - content that is viewable by those whom the creator has shared the link with. */
  Private = 'PRIVATE',
  /** Accessible everywhere - content that is viewable and shared by anyone. */
  Public = 'PUBLIC'
}

/** The available input fields of for a Visibility operator. */
export type VisibilityOperator = {
  /** Short for equal, must match the given data exactly. */
  eq: Visibility;
};

/** Information about the engagement of the voter on a Poll. */
export type VoterEngagement = Node & {
  __typename?: 'VoterEngagement';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates the option the channel has voted on the poll. */
  option?: Maybe<PollOption>;
};

/** Represents a Watch (an activity). */
export type Watch = History & Node & {
  __typename?: 'Watch';
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** The post watched by the channel. */
  post: Post;
};

/** The return fields from performing an action on the watched list of the connected user. */
export type WatchedPayload = {
  __typename?: 'WatchedPayload';
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** The input fields to add a video to the `Watched` list of the connected user. */
export type WatchedVideoAddInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** Indicates whether the video is watched completely. */
  completed?: InputMaybe<Scalars['Boolean']['input']>;
  /** The Dailymotion ID of the video. */
  videoXid: Scalars['String']['input'];
};

/** The return fields from adding a video to the `Watched` list of the connected user. */
export type WatchedVideoAddPayload = {
  __typename?: 'WatchedVideoAddPayload';
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: Maybe<Scalars['String']['output']>;
  /** The status of the mutation. */
  status?: Maybe<Status>;
};

/** Information about a dailymotion page. */
export type Web = Node & {
  __typename?: 'Web';
  /** The author of the page. */
  author?: Maybe<Scalars['String']['output']>;
  /** The country of the page. It is only available when detected as a bot. Otherwise, a null value will be returned. */
  country?: Maybe<Country>;
  /** The description of the page. */
  description?: Maybe<Scalars['String']['output']>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
  /** Indicates whether the description link can be followed. */
  isFollowable?: Maybe<Scalars['Boolean']['output']>;
  /** The language of the page. It is only available when detected as a bot. Otherwise, a null value will be returned. */
  language?: Maybe<Language>;
  /** The metadatas of the page. */
  metadata?: Maybe<WebMetadataConnectionConnection>;
  /**
   * The metadatas of the page.
   * @deprecated Use `metadata` field.
   */
  metadatas?: Maybe<Array<Maybe<WebMetadata>>>;
  /** The title of the page. */
  title?: Maybe<Scalars['String']['output']>;
};


/** Information about a dailymotion page. */
export type WebMetadataArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The HTML meta tag of the web. */
export type WebMetadata = Node & {
  __typename?: 'WebMetadata';
  /** The attributes of the metadata. */
  attributes?: Maybe<Array<Maybe<Attribute>>>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};

/** The connection type for WebMetadata. */
export type WebMetadataConnection = Node & {
  __typename?: 'WebMetadataConnection';
  /** The attributes of the web metadata. */
  attributes?: Maybe<AttributeConnection>;
  /** The ID of the object. */
  id: Scalars['ID']['output'];
};


/** The connection type for WebMetadata. */
export type WebMetadataConnectionAttributesArgs = {
  first?: InputMaybe<Scalars['Int']['input']>;
  page?: InputMaybe<Scalars['Int']['input']>;
};

/** The connection type for WebMetadataConnection. */
export type WebMetadataConnectionConnection = {
  __typename?: 'WebMetadataConnectionConnection';
  /** A list of edges. */
  edges: Array<Maybe<WebMetadataConnectionEdge>>;
  /** The metadata of the connection. */
  metadata: Metadata;
  /** Information to aid in pagination. */
  pageInfo: PageInfo;
  /** The total number of items. A null value indicates that the information is unavailable for the connection. */
  totalCount?: Maybe<Scalars['Int']['output']>;
};

/** An edge in a connection. */
export type WebMetadataConnectionEdge = {
  __typename?: 'WebMetadataConnectionEdge';
  /** The item at the end of the edge */
  node?: Maybe<WebMetadataConnection>;
};

/** The input fields to add a like for the connected user. */
export type AddLikeInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The heart rating to give to the post. If not provided, `rating` will be used. */
  hearts?: InputMaybe<HeartRatingInput>;
  /** The ID of the story to like. */
  id?: InputMaybe<Scalars['ID']['input']>;
  /** The number of points to add to the post. If not provided, defaults to 0. */
  points?: InputMaybe<Scalars['Int']['input']>;
  /** @deprecated(reason: "Use `id`".) - The ID of the post. */
  postId?: InputMaybe<Scalars['ID']['input']>;
  /** The rating to add to the post. If not provided, defaults to STAR_STRUCK */
  rating?: InputMaybe<LikeRating>;
};

/** The input fields to remove a post for the connected user. */
export type RemoveLikeInput = {
  /** @deprecated(reason: "No longer supported.") - The ID generated for the client performing the mutation. */
  clientMutationId?: InputMaybe<Scalars['String']['input']>;
  /** The ID of the story to like. */
  id?: InputMaybe<Scalars['ID']['input']>;
  /** @deprecated(reason: "Use `id`".) - The ID of the post. */
  postId?: InputMaybe<Scalars['ID']['input']>;
};
