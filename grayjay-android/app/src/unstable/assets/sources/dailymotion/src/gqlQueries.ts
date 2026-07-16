export const AUTOCOMPLETE_QUERY = `
query AUTOCOMPLETE_QUERY($query: String!) {
  search {
    suggestedVideos: autosuggestions(
      query: { eq: $query }
      filter: { story: { eq: VIDEO } }
    ) {
      edges {
        node {
          name
        }
      }
    }
  }
}`;

export const CHANNEL_QUERY_DESKTOP = `
query CHANNEL_QUERY_DESKTOP(
	$channel_name: String!
	$avatar_size: AvatarHeight!
) {
	channel(name: $channel_name) {
		id
		xid
		name
		displayName
		description
		avatar(height:$avatar_size) {
			url
		}
		banner(width:LANDSCAPE_1920) {
			url
		}
		tagline
		metrics {
			engagement {
				followers {
					edges {
						node {
							total
						}
					}
				}
				followings {
					edges {
						node {
							total
						}
					}
				}
			}
		}
		stats {
			views {
				total
			}
			videos {
				total
			}
		}
		externalLinks {
			facebookURL
			twitterURL
			websiteURL
			instagramURL
			pinterestURL
		}
	}
}`;

export const SEACH_DISCOVERY_QUERY = `	
fragment SEARCH_DISCOVERY_VIDEO_FRAGMENT on Video {
	id
	xid
	title
	thumbnail(height:$thumbnail_resolution) {
		url
	}
	createDate
	createdAt
	creator {
		id
		xid
		name
		displayName
		avatar(height:$avatar_size) {
			url
		}
	}
	duration
	viewCount
	stats {
		views {
			total
		}
	}
}

query SEACH_DISCOVERY_QUERY($avatar_size: AvatarHeight!, $thumbnail_resolution: ThumbnailHeight!) {
	home: views {
		neon {
			sections(space: "home") {
				edges {
					node {
						id
						name
						title
						description
						components {
							pageInfo {
								hasNextPage
							}
							edges {
								node {
									... on Media {
										...SEARCH_DISCOVERY_VIDEO_FRAGMENT
									}
								}
							}
						}
					}
				}
			}
		}
	}
}`;

export const CHANNEL_VIDEOS_QUERY = `
query CHANNEL_VIDEOS_QUERY(
  $channel_name: String!
  $first: Int!
  $sort: String
  $page: Int!
  $allowExplicit: Boolean
  $avatar_size: AvatarHeight!
  $thumbnail_resolution: ThumbnailHeight!
  $shouldLoadLives: Boolean!
  $shouldLoadVideos: Boolean!
) {
  channel(name: $channel_name) {
    id
    xid
    lives(
      page: $page
      first: $first
      allowExplicit: $allowExplicit
    ) @include(if: $shouldLoadLives) {
      pageInfo {
        hasNextPage
        nextPage
      }
      totalCount
      edges {
        node {
          id
          xid
          title
          thumbnail(height: $thumbnail_resolution) {
            url
          }
          description
          metrics {
            engagement {
              audience {
                totalCount
              }
            }
          }
          audienceCount
          isOnAir
          stats {
            views {
              total
            }
          }
          creator {
            id
            xid
            name
            displayName
            avatar(height: $avatar_size) {
              url
            }
          }
        }
      }
    }
    videos(
      page: $page
      first: $first
      allowExplicit: $allowExplicit
      sort: $sort
    ) @include(if: $shouldLoadVideos) {
      pageInfo {
        hasNextPage
        nextPage
      }
      edges {
        node {
          id
          xid
          title
          thumbnail(height: $thumbnail_resolution) {
            url
          }
          duration
          createDate
          createdAt
          creator {
            id
            name
            displayName
            avatar(height: $avatar_size) {
              url
            }
          }
          metrics {
            engagement {
              likes {
                totalCount
              }
            }
          }
          viewCount
          stats {
            views {
              total
            }
          }
        }
      }
    }
  }
}`;

export const SEARCH_QUERY = ` 
fragment VIDEO_BASE_FRAGMENT on Video {
	id
	xid
	title
	createDate
	createdAt
	metrics {
		engagement {
			likes {
				edges {
					node {
						rating
						total
					}
				}
			}
		}
	}
	stats {
		views {
			total
		}
	}
	creator {
		id
		xid
		name
		displayName
		description
		avatar(height:$avatar_size) {
			url
		}
	}
	duration
	thumbnail(height:$thumbnail_resolution) {
		url
	}
}

fragment PLAYLIST_BASE_FRAG on Collection {
	id
	xid
	name
	description
	thumbnail(height:$thumbnail_resolution) {
		url
	}
	creator {
		id
		xid
		name
		displayName
		avatar(height:$avatar_size) {
			url
		}
	}
	description
	stats {
		videos {
			total
		}
	}
	metrics {
		engagement {
			videos {
				edges {
					node {
						total
					}
				}
			}
		}
	}
}

query SEARCH_QUERY(
	$query: String!
	$shouldIncludeVideos: Boolean!
	$shouldIncludePlaylists: Boolean!
	$shouldIncludeLives: Boolean!
	$page: Int
	$limit: Int
	$sortByVideos: SearchVideoSort
	$durationMinVideos: Int
	$durationMaxVideos: Int
	$createdAfterVideos: DateTime
	$avatar_size: AvatarHeight!
	$thumbnail_resolution: ThumbnailHeight!
) {
	search {
		videos(
			query: $query
			first: $limit
			page: $page
			sort: $sortByVideos
			durationMin: $durationMinVideos
			durationMax: $durationMaxVideos
			createdAfter: $createdAfterVideos
		) @include(if: $shouldIncludeVideos) {
			pageInfo {
				hasNextPage
				nextPage
			}
			totalCount
			edges {
				node {
					id
					...VIDEO_BASE_FRAGMENT
				}
			}
		}
		lives(query: $query, first: $limit, page: $page)
			@include(if: $shouldIncludeLives) {
			pageInfo {
				hasNextPage
				nextPage
			}
			totalCount
			edges {
				node {
					id
					xid
					title
					thumbnail(height:$thumbnail_resolution) {
						url
					}
					description
					metrics {
						engagement {
							audience {
								totalCount
							}
						}
					}
					audienceCount
					isOnAir
					creator {
						id
						xid
						name
						displayName
						avatar(height:$avatar_size){
							url
						}
					}
				}
			}
		}
		playlists: collections(query: $query, first: $limit, page: $page)
			@include(if: $shouldIncludePlaylists) {
			pageInfo {
				hasNextPage
				nextPage
			}
			totalCount
			edges {
				node {
					id
					...PLAYLIST_BASE_FRAG
				}
			}
		}
	}
}`;

export const WATCHING_VIDEO = `
fragment VIDEO_FRAGMENT on Video {
	id
	xid
	duration
	title
	description
	thumbnail(height:$thumbnail_resolution) {
		url
	}
	createDate
	createdAt
	metrics {
		engagement {
			likes {
				totalCount
				edges {
					node {
						rating
						total
					}
				}
			}
		}
	}
	stats {
		views {
			total
		}
	}
	creator {
		id
		xid
		name
		displayName
		avatar(height:$avatar_size) {
			url
			height
			width
		}
		metrics {
			engagement {
				followers {
					totalCount
					edges {
						node {
							total
						}
					}
				}
			}
		}
		stats {
			views {
				total
			}
			followers {
				total
			}
			videos {
				total
			}
		}
	}
}

fragment LIVE_FRAGMENT on Live {
	id
	xid
	startAt
	endAt
	title
	description
	audienceCount
	isOnAir
	thumbnail(height:$thumbnail_resolution){
		url
	}
	createDate
	createdAt
	videoWidth: width
	videoHeight: height
	metrics {
		engagement {
			likes {
				edges {
					node {
						rating
						total
					}
				}
			}
		}
	}
	stats {
		views {
			total
		}
	}
	creator {
		id
		xid
		name
		displayName
		avatar(height:$avatar_size) {
			url
			height
			width
		}
		stats {
			views {
				total
			}
			followers {
				total
			}
			videos {
				total
			}
		}
	}
}

query WATCHING_VIDEO(
	$xid: String!
	$avatar_size: AvatarHeight!
	$thumbnail_resolution: ThumbnailHeight!
) {
	video: media(xid: $xid) {
		... on Video {
			id
			...VIDEO_FRAGMENT
		}
		... on Live {
			id
			...LIVE_FRAGMENT
		}
	}
}`;

export const SEARCH_CHANNEL = `		
query SEARCH_QUERY($query: String!, $page: Int, $limit: Int, $avatar_size: AvatarHeight!) {
	search {
		channels(query: $query, first: $limit, page: $page) {
			pageInfo {
				hasNextPage
				nextPage
			}
			totalCount
			edges {
				node {
					id
					xid
					name
					displayName
					description
					avatar(height:$avatar_size) {
						url
					}
					metrics {
						engagement {
							followers {
								edges {
									node {
										total
									}
								}
							}
						}
					}
				}
			}
		}
	}
}`;

export const PLAYLIST_DETAILS_QUERY = `
query PLAYLIST_VIDEO_QUERY($xid: String!, $numberOfVideos: Int = 100, $avatar_size: AvatarHeight!, $thumbnail_resolution: ThumbnailHeight!) {
	collection(xid: $xid) {
		id
		xid
		name
		thumbnail(height:$thumbnail_resolution) {
			url
		}
		creator {
			id
			name
			displayName
			xid
			avatar(height:$avatar_size) {
				url
			}
			metrics {
				engagement {
					followers {
						edges {
							node {
								total
							}
						}
					}
				}
			}
		}
		metrics {
			engagement {
				videos {
					edges {
						node {
							total
						}
					}
				}
			}
		}
		videos(first: $numberOfVideos) {
			edges {
				node {
					id
					xid
					duration
					title
					description
					url
					createDate
					createdAt
					thumbnail(height:$thumbnail_resolution) {
						url
					}
					creator {
						id
						name
						displayName
						xid
						avatar(height:$avatar_size) {
							url
						}
						metrics {
							engagement {
								followers {
									edges {
										node {
											total
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}`;

export const GET_USER_SUBSCRIPTIONS = `
query SUBSCRIPTIONS_QUERY($first: Int, $page: Int) {
	me {
		channel {
			followings(first: $first, page: $page) {
				totalCount
				edges {
					node {
						creator {
							name
						}
					}
				}
			}
		}
	}
}`;

export const GET_CHANNEL_PLAYLISTS_XID = `
query CHANNEL_PLAYLISTS_QUERY(
	$channel_name: String!
	$sort: String
	$page: Int!
	$first: Int!
) {
	channel(name: $channel_name) {
		collections(
			sort: $sort
			page: $page
			first: $first
		) {
			pageInfo {
				hasNextPage
				nextPage
			}
			edges {
				node {
					xid
					isPrivate
						}
					}
				}
			}
}`;

export const SUBSCRIPTIONS_QUERY = `
query SUBSCRIPTIONS_QUERY {
	me {
		xid
		channel {
			name
		}
	}
}
`;

export const CHANNEL_PLAYLISTS_QUERY = `
query CHANNEL_PLAYLISTS_QUERY(
	$channel_name: String!
	$sort: String
	$page: Int!
	$first: Int!
	$avatar_size: AvatarHeight!, 
	$thumbnail_resolution: ThumbnailHeight!
) {
	channel(name: $channel_name) {
		id
		xid
		collections(sort: $sort, page: $page, first: $first) {
			pageInfo {
				hasNextPage
				nextPage
			}
			edges {
				node {
					id
					xid
					createDate
					createdAt
					name
					description
					metrics {
						engagement {
							videos {
								edges {
									node {
										total
									}
								}
								totalCount
							}
						}
					}
					thumbnail(height:$thumbnail_resolution) {
						url
					}
					stats {
						videos {
							total
						}
					}
					videos {
						edges {
							node {
								createDate
								createdAt
								creator {
									id
									name
									xid
									avatar(height:$avatar_size) {
										url
									}
									displayName
								}
							}
						}
					}
				}
			}
		}
	}
}

`;

export const USER_LIKED_VIDEOS_QUERY = `
query USER_LIKED_VIDEOS_QUERY($page: Int!, $thumbnail_resolution: ThumbnailHeight!) {
	me {
		likedMedias(first: 100, page: $page) {
			edges {
				node {
					... on Video {
						id
						xid
						title
						duration
						thumbnail(height:$thumbnail_resolution) {
							url
						}
						channel {
							displayName
						}
					}
					... on Live {
						
						id
						xid
						title
						isOnAir
						thumbnail(height:$thumbnail_resolution) {
							url
						}
						channel {
							displayName
						}
					}
				}
			}
			pageInfo {
				hasNextPage
				nextPage
			}
		}
	}
}`;

export const USER_WATCH_LATER_VIDEOS_QUERY = `
	query USER_WATCH_LATER_VIDEOS_QUERY($page: Int!, $thumbnail_resolution: ThumbnailHeight!) {
	me {
		id
		watchLaterMedias(first: 100, page: $page) {
			edges {
				node {
					... on Video {
						id
						xid
						title
						duration
						thumbnail(height:$thumbnail_resolution) {
							url
						}
						channel {
							displayName
						}
					}
					... on Live {
						id
						xid
						title
						isOnAir
						thumbnail(height:$thumbnail_resolution) {
							url
						}
						channel {
							displayName
						}
					}
				}
			}
			pageInfo {
				hasNextPage
				nextPage
			}
		}
	}
}`;

export const USER_WATCHED_VIDEOS_QUERY = `
	query USER_WATCHED_VIDEOS_QUERY($page: Int!, $thumbnail_resolution: ThumbnailHeight!) {
	me {
		id
		watchedVideos(first: 100, page: $page) {
			edges {
				node {
					id
					xid
					title
					duration
					thumbnail(height:$thumbnail_resolution) {
						url
					}
					channel {
						displayName
					}
				}
			}
			pageInfo {
				hasNextPage
				nextPage
			}
		}
	}
}`;

export const DISCOVERY_QUEUE_QUERY = `
query DISCOVERY_QUEUE_QUERY($videoXid: String!, $videoCountPerSection: Int) {
  views {
    neon {
      sections(
        space: "watching"
        context: {mediaXid: $videoXid}
        first: 20
      ) {
        edges {
          node {
            name
            components(first: $videoCountPerSection) {
              edges {
                node {
                  ... on Video {
                    xid
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

`

export const playerVideosDataQuery = `
query playerVideosDataQuery(
	$videoXids: [String!],
	$first: Int,
	$avatar_size: AvatarHeight!,
	$thumbnail_resolution: ThumbnailHeight!
) {
  videos(videoXids: $videoXids, first: $first) {
    edges {
      node {
        ...VideoFields
      }
    }
  }
}
fragment VideoFields on Video {
  	id
	xid
	title
	createDate
	createdAt
	metrics {
		engagement {
			likes {
				edges {
					node {
						rating
						total
					}
				}
			}
		}
	}
	stats {
		views {
			total
		}
	}
	creator {
		id
		xid
		name
		displayName
		description
		avatar(height:$avatar_size) {
			url
		}
	}
	duration
	thumbnail(height:$thumbnail_resolution) {
		url
	}
}

`

export const GET_SHORTS_FEED_QUERY = `
fragment FeedVideoFields on Video {
	xid
	title
	thumbnail(height: $thumbnailHeight) {
		url
	}
	duration
	shareUrls {
		permalink
	}
	createDate
	aspectRatio
	claimer {
		xid
		displayName
	}
	creator {
		__typename
		xid
		displayName
		name
		avatar(height: $channelLogoSize) {
			url
		}
		account
		followerEngagement {
			notifications {
				uploads
			}
		}
		followerEngagement {
			followDate
		}
	}
	paidPartnership
	metrics {
		views {
			visits {
				totalCount
			}
		}
		engagement {
			hearts {
				edges {
					node {
						emoji
						total
					}
				}
			}
			threads {
				edges {
					node {
						total
					}
				}
			}
		}
	}
	audience
	visibility
	status
	streamUrls {
		hls
	}
	hasPerspective
	id
	hashtags {
		edges {
			node {
				id
				name
			}
		}
	}
	subtitles(autoGenerated: true) {
		edges {
			node {
				id
			}
		}
	}
	audience
	settings {
		threadsDisabled
	}
	restriction {
		code
	}
	spritesheet {
		url
	}
	spritesheetSeeker {
		url
	}
	viewerEngagement {
		hearts {
			emoji
			amount
		}
		favorited
		reacted
	}
	visibility
	audience
	height
	width
}

query GetHomeFeed(
	$thumbnailHeight: ThumbnailHeight!
	$channelLogoSize: AvatarHeight!
	$watchedVideoIds: [ID!]
	$first: Int!
	$personalizationOptOut: Boolean!
) {
	conversations(
		first: $first
		context: {
			personalizationOptOut: $personalizationOptOut
			viewer: { history: { watched: { in: $watchedVideoIds } } }
		}
		filter: { story: { in: [VIDEO] }, algorithm: { eq: PERSONALIZED } }
	) {
		metadata {
			algorithm {
				uuid
			}
		}
		edges {
			node {
				algorithm {
					version
					name
					source
					percentage
				}
				story {
					__typename
					...FeedVideoFields
				}
			}
		}
	}
}
`