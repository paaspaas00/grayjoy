package com.futo.polycentric.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import userpackage.Protocol
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Cache
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class PolycentricProfile(
    val system: PublicKey, val systemState: SystemState, val ownedClaims: List<OwnedClaim>
) {
    fun getHarborUrl(): String{
        val systemState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(system));
        val url = system.systemToURLInfoSystemLinkUrl(systemState.servers.asIterable());
        return "https://harbor.social/" + url.substring("polycentric://".length);
    }
}

suspend fun ProcessHandle.fullyBackfillServersAnnounceExceptions() {
    val exceptions = fullyBackfillServers()
    for (pair in exceptions) {
        val server = pair.key
        val exception = pair.value
        Log.e("Backfill", "Failed to backfill server $server. ${exception.message}")
    }
}

/**
 * Ensures that the profile includes [ApiMethods.SERVER] in its server list and, if it was
 * newly added, back-fills any missing events to the server.  If the server is already present
 * this becomes a no-op other than a normal back-fill (which will quickly return when there is
 * nothing to sync).
 *
 * This helper is useful when the constant [ApiMethods.SERVER] changes (e.g. migrating from an
 * old server to a moderated one). It performs three steps:
 * 1. Inspect the current `SystemState` to see if the server is listed.
 * 2. If missing – publish a `ContentType.SERVER` ADD event via [ProcessHandle.addServer].
 * 3. Call [fullyBackfillServersAnnounceExceptions] so any local events absent from the new
 *    server are uploaded.
 */
suspend fun ProcessHandle.ensureServerAndBackfill(server: String = ApiMethods.SERVER) =
    withContext(Dispatchers.IO) {
        // Check current servers in SystemState
        val storageState = Store.instance.getSystemState(this@ensureServerAndBackfill.system)
        val systemState = SystemState.fromStorageTypeSystemState(storageState)

        if (!systemState.servers.contains(server)) {
            // Add the new server to the CRDT set
            Log.i("Backfill", "Adding missing server $server to profile and syncing …")
            addServer(server)
        }

        // Backfill (if nothing is missing this is fast)
        fullyBackfillServersAnnounceExceptions()
    }

class ApiMethods {
    companion object {
        // Moderation server: "https://grayjay-srv1-test.polycentric.io"
        // Old Server: "https://grayjay-srv1.polycentric.io"
        const val SERVER = "https://grayjay-srv1-gj.polycentric.io"
        val FUTO_TRUST_ROOT = Protocol.PublicKey.newBuilder()
            .setKeyType(1)
            .setKey(ByteString.copyFrom("gX0eCWctTm6WHVGot4sMAh7NDAIwWsIM5tRsOz9dX04=".base64ToByteArray())) //Production key
            //.setKey(ByteString.copyFrom("LeQkzn1j625YZcZHayfCmTX+6ptrzsA+CdAyq+BcEdQ".base64ToByteArray())) //Test key koen-futo
            .build();

        var UserAgent = "Grayjay Android";

        private const val TAG = "ApiMethods"
        private var client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .build()

        private var moderationLevelProvider: (() -> Map<String, Int>?)? = null
        private var moderationExemptSystemProvider: (() -> String?)? = null

        fun setModerationLevelProvider(provider: () -> Map<String, Int>?) {
            moderationLevelProvider = provider
        }

        fun setModerationExemptSystemProvider(provider: () -> String?) {
            moderationExemptSystemProvider = provider
        }

        private fun getModerationLevels(): Map<String, Int>? {
            return try {
                moderationLevelProvider?.invoke()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get moderation levels from provider: "+e.message)
                null
            }
        }
        
        private fun getExemptSystem(): String? {
            return try {
                moderationExemptSystemProvider?.invoke()
            } catch (e: Exception) {
                null
            }
        }

        fun initCache(cacheDir: File) {
            client = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .cache(Cache(
                    directory = File(cacheDir, "http_cache"),
                    maxSize = 25L * 1024L * 1024L // 25 MiB
                ))
                .build()
        }

        private val MEDIA_TYPE_OCTET_STREAM = "application/octet-stream".toMediaType()

        fun getRequestBuilder(url: String): Request.Builder = Request.Builder().url(url).header("x-polycentric-user-agent", UserAgent)

        private fun encodeModerationLevels(moderationLevels: Map<String, Int>?): String? {
            if (moderationLevels == null) return null
            
            val filters = moderationLevels.map { (key, value) ->
                """{"name":"$key","max_level":$value,"strict_mode":false}"""
            }
            
            return "[${filters.joinToString(",")}]"
        }

        suspend fun postEvents(server: String, events: Protocol.Events): Unit = withContext(Dispatchers.IO) {
            val body = events.toByteArray().toRequestBody(MEDIA_TYPE_OCTET_STREAM)
            val request = getRequestBuilder("$server/events").post(body).build()
            executeCall<Unit>(client.newCall(request)) { response ->
                if (!response.isSuccessful) {
                    val formattedMessage = "postEvents to $server failed with status code ${response.code}. Response body: ${response.body?.string().orEmpty()}"
                    throw Exception(formattedMessage)
                }
                Unit
            }
        }

        suspend fun getEvents(
            server: String, 
            system: Protocol.PublicKey, 
            ranges: Protocol.RangesForSystem,
            moderationLevels: Map<String, Int>? = null
        ): Protocol.Events = withContext(Dispatchers.IO) {
            val systemQuery = system.toByteArray().toBase64Url()
            val rangesQuery = ranges.toByteArray().toBase64Url()

            val baseUrl = "$server/events?system=$systemQuery&ranges=$rangesQuery" // Compatible with legacy servers

            // Prepare optional moderation parameters (only for modern servers)
            val levelsToUse = moderationLevels ?: getModerationLevels()
            val moderationParam = encodeModerationLevels(levelsToUse)?.let { "&moderation_filters=${Uri.encode(it)}" }
            val exemptSystemParam = getExemptSystem()?.let { "&exclude_system=$it" }

            val modernUrl = buildString {
                append(baseUrl)
                moderationParam?.let { append(it) }
                exemptSystemParam?.let { append(it) }
            }

            suspend fun fetch(url: String): Protocol.Events {
                val request = getRequestBuilder(url).get().build()
                return executeCall(client.newCall(request)) { response ->
                    if (!response.isSuccessful) {
                        val formattedMessage = "getEvents to $server failed with status code ${response.code}. Response body: ${response.body?.string().orEmpty()}"
                        throw Exception(formattedMessage)
                    }
                    Protocol.Events.parseFrom(response.body?.byteStream())
                }
            }

            // Try modern endpoint first, fall back to legacy format if that fails
            return@withContext try {
                fetch(modernUrl)
            } catch (e: Exception) {
                if (moderationParam != null || exemptSystemParam != null) {
                    fetch(baseUrl)
                } else {
                    throw e
                }
            }
        }

        suspend fun getResolveClaim(server: String, trustRoot: Protocol.PublicKey, claimType: Long, matchAnyField: String): Protocol.QueryClaimToSystemResponse = withContext(Dispatchers.IO) {
            val query = Protocol.QueryClaimToSystemRequest.newBuilder()
                .setClaimType(claimType)
                .setTrustRoot(trustRoot)
                .setMatchAnyField(matchAnyField)
                .build().toByteArray().toBase64Url()
            val url = "$server/resolve_claim?query=$query"
            val request = getRequestBuilder(url).get().build()
            executeCall(client.newCall(request)) { response ->
                if (!response.isSuccessful) {
                    val formattedMessage = "getResolveClaim to $server failed with status code ${response.code}. Response body: ${response.body?.string().orEmpty()}"
                    throw Exception(formattedMessage)
                }
                Protocol.QueryClaimToSystemResponse.parseFrom(response.body?.byteStream())
            }
        }

        suspend fun getResolveClaim(server: String, trustRoot: Protocol.PublicKey, claimType: Long, claimFieldType: Long, value: String): Protocol.QueryClaimToSystemResponse = withContext(Dispatchers.IO) {
            val query = Protocol.QueryClaimToSystemRequest.newBuilder()
                .setClaimType(claimType)
                .setTrustRoot(trustRoot)
                .setMatchAllFields(Protocol.QueryClaimToSystemRequestMatchAll.newBuilder()
                    .addFields(Protocol.ClaimFieldEntry.newBuilder()
                        .setKey(claimFieldType.toLong())
                        .setValue(value)
                        .build())
                    .build())
                .build().toByteArray().toBase64Url()
            val url = "$server/resolve_claim?query=$query"
            val request = getRequestBuilder(url).get().build()
            executeCall(client.newCall(request)) { response ->
                if (!response.isSuccessful) {
                    val formattedMessage = "getResolveClaim to $server failed with status code ${response.code}. Response body: ${response.body?.string().orEmpty()}"
                    throw Exception(formattedMessage)
                }
                Protocol.QueryClaimToSystemResponse.parseFrom(response.body?.byteStream())
            }
        }

        suspend fun getRanges(server: String, system: Protocol.PublicKey): Protocol.RangesForSystem = withContext(Dispatchers.IO) {
            val systemQuery = system.toByteArray().toBase64Url()
            val request = getRequestBuilder("$server/ranges?system=$systemQuery").get().build()
            executeCall(client.newCall(request)) { response ->
                if (!response.isSuccessful) {
                    val formattedMessage = "getRanges to $server failed with status code ${response.code}. Response body: ${response.body?.string().orEmpty()}"
                    throw Exception(formattedMessage)
                }
                Protocol.RangesForSystem.parseFrom(response.body?.byteStream())
            }
        }

        suspend fun getQueryIndex(server: String, system: Protocol.PublicKey, contentType: Long, after: Int? = null, limit: Int? = null): Protocol.QueryIndexResponse = withContext(Dispatchers.IO) {
            val systemQuery = system.toByteArray().toBase64Url()
            val path = when {
                after != null && limit != null -> "/query_index?system=$systemQuery&content_type=$contentType&after=$after&limit=$limit"
                limit != null -> "/query_index?system=$systemQuery&content_type=$contentType&limit=$limit"
                after != null -> "/query_index?system=$systemQuery&content_type=$contentType&after=$after"
                else -> "/query_index?system=$systemQuery&content_type=$contentType"
            }
            val request = getRequestBuilder("$server$path").get().build()
            executeCall(client.newCall(request)) { response ->
                if (!response.isSuccessful) {
                    val formattedMessage = "getQueryIndex to $server failed with status code ${response.code}. Response body: ${response.body?.string().orEmpty()}"
                    throw Exception(formattedMessage)
                }
                Protocol.QueryIndexResponse.parseFrom(response.body?.byteStream())
            }
        }

        suspend fun getQueryLatest(server: String, system: Protocol.PublicKey, eventTypes: List<Long>): Protocol.Events = withContext(Dispatchers.IO) {
            val systemQuery = system.toByteArray().toBase64Url()
            val eventTypesQuery = Protocol.RepeatedUInt64.newBuilder()
                .addAllNumbers(eventTypes).build().toByteArray().toBase64Url()
            val path = "/query_latest?system=$systemQuery&event_types=$eventTypesQuery"
            val request = getRequestBuilder("$server$path").get().build()
            executeCall(client.newCall(request)) { response ->
                if (!response.isSuccessful) {
                    val formattedMessage = "getQueryLatest to $server failed with status code ${response.code}. Response body: ${response.body?.string().orEmpty()}"
                    throw Exception(formattedMessage)
                }
                Protocol.Events.parseFrom(response.body?.byteStream())
            }
        }

        suspend fun getQueryReferences(
            server: String,
            reference: Protocol.Reference,
            cursor: ByteArray? = null,
            requestEvents: Protocol.QueryReferencesRequestEvents? = null,
            countLwwElementReferences: List<Protocol.QueryReferencesRequestCountLWWElementReferences>? = null,
            countReferences: List<Protocol.QueryReferencesRequestCountReferences>? = null,
            extraByteReferences: List<ByteArray>? = null,
            moderationLevels: Map<String, Int>? = null
        ): Protocol.QueryReferencesResponse = withContext(Dispatchers.IO) {
            val builder = Protocol.QueryReferencesRequest.newBuilder().setReference(reference)
            cursor?.let { builder.cursor = ByteString.copyFrom(it) }
            requestEvents?.let { builder.requestEvents = it }
            countLwwElementReferences?.let { builder.addAllCountLwwElementReferences(it) }
            countReferences?.let { builder.addAllCountReferences(it) }
            extraByteReferences?.let { builder.addAllExtraByteReferences(it.map { x -> ByteString.copyFrom(x) }) }

            val query = builder.build()
            val encodedQuery = query.toByteArray().toBase64Url()
            
            val baseUrl = "$server/query_references?query=$encodedQuery" // Legacy compatible URL

            // Optional moderation parameters
            val levelsToUse = moderationLevels ?: getModerationLevels()
            val moderationParam = encodeModerationLevels(levelsToUse)?.let { "&moderation_filters=${Uri.encode(it)}" }
            val exemptSystemParam = getExemptSystem()?.let { "&exclude_system=$it" }

            val modernUrl = buildString {
                append(baseUrl)
                moderationParam?.let { append(it) }
                exemptSystemParam?.let { append(it) }
            }

            suspend fun fetch(url: String): Protocol.QueryReferencesResponse {
                val request = getRequestBuilder(url).get().build()
                return executeCall(client.newCall(request)) { response ->
                    if (!response.isSuccessful) {
                        val formattedMessage = "getQueryReferences to $server failed with status code ${response.code}. Response body: ${response.body?.string().orEmpty()}"
                        throw Exception(formattedMessage)
                    }
                    Protocol.QueryReferencesResponse.parseFrom(response.body?.byteStream())
                }
            }

            return@withContext try {
                fetch(modernUrl)
            } catch (e: Exception) {
                if (moderationParam != null || exemptSystemParam != null) {
                    fetch(baseUrl)
                } else {
                    throw e
                }
            }
        }

        /**
         * A convenience method for querying comments with pre-configured parameters for likes, dislikes and post references.
         * This method encapsulates the common pattern of querying comments with their associated opinion counts.
         *
         * @param server The server to query from
         * @param reference The reference to query for
         * @param cursor Optional cursor for pagination
         * @param extraByteReferences Optional additional byte references to include
         * @return QueryReferencesResponse containing the comments and their associated data
         */
        suspend fun getCommentReferences(
            server: String,
            reference: Protocol.Reference,
            cursor: ByteArray? = null,
            extraByteReferences: List<ByteArray>? = null
        ): Protocol.QueryReferencesResponse = withContext(Dispatchers.IO) {
            val requestEvents = Protocol.QueryReferencesRequestEvents.newBuilder()
                .setFromType(ContentType.POST.value)
                .addAllCountLwwElementReferences(arrayListOf(
                    Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                        .setFromType(ContentType.OPINION.value)
                        .setValue(ByteString.copyFrom(Opinion.like.data))
                        .build(),
                    Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                        .setFromType(ContentType.OPINION.value)
                        .setValue(ByteString.copyFrom(Opinion.dislike.data))
                        .build()
                ))
                .addCountReferences(
                    Protocol.QueryReferencesRequestCountReferences.newBuilder()
                        .setFromType(ContentType.POST.value)
                        .build()
                )
                .build()

            return@withContext getQueryReferences(
                server = server,
                reference = reference,
                cursor = cursor,
                requestEvents = requestEvents,
                extraByteReferences = extraByteReferences
            )
        }

        /**
         * A convenience method for retrieving basic profile information.
         * Gets only the essential profile data (username, avatar) for a system.
         *
         * @param server The server to query from
         * @param system The system (public key) of the channel
         * @return Events containing the profile information
         */
        suspend fun getBasicProfile(
            server: String,
            system: Protocol.PublicKey
        ): Protocol.Events = withContext(Dispatchers.IO) {
            return@withContext getQueryLatest(
                server = server,
                system = system,
                eventTypes = listOf(
                    ContentType.USERNAME.value,
                    ContentType.AVATAR.value
                )
            )
        }

        /**
         * A convenience method for retrieving complete profile information.
         * Gets all profile data including description and claims.
         *
         * @param server The server to query from
         * @param system The system (public key) of the channel
         * @return Events containing the complete profile information
         */
        suspend fun getFullProfile(
            server: String,
            system: Protocol.PublicKey
        ): Protocol.Events = withContext(Dispatchers.IO) {
            return@withContext getQueryLatest(
                server = server,
                system = system,
                eventTypes = listOf(
                    ContentType.USERNAME.value,
                    ContentType.DESCRIPTION.value,
                    ContentType.AVATAR.value,
                    ContentType.CLAIM.value
                )
            )
        }

        suspend fun getPolycentricProfileByClaim(server: String, system: Protocol.PublicKey, claimFieldType: Long, claimType: Long, claimValue: String): PolycentricProfile = withContext(Dispatchers.IO) {
            val resolved = if (claimFieldType == -1L) getResolveClaim(SERVER, system, claimType, claimValue)
                else getResolveClaim(SERVER, system, claimType, claimFieldType, claimValue)

            val protoEvents = resolved.matchesList.flatMap { arrayListOf(it.claim).apply { addAll(it.proofChainList) } }
            val resolvedEvents = protoEvents.map { i -> SignedEvent.fromProto(i) }
            val claims = resolvedEvents.getValidClaims()
            return@withContext getPolycentricProfile(server, claims.first().system)
        }

        suspend fun getPolycentricProfile(server: String, system: PublicKey): PolycentricProfile = withContext(Dispatchers.IO) {
            val signedEventsList = getQueryLatest(
                server,
                system.toProto(),
                listOf(
                    ContentType.BANNER.value,
                    ContentType.AVATAR.value,
                    ContentType.USERNAME.value,
                    ContentType.DESCRIPTION.value,
                    ContentType.STORE.value,
                    ContentType.SERVER.value,
                    ContentType.STORE_DATA.value,
                    ContentType.PROMOTION_BANNER.value,
                    ContentType.PROMOTION.value,
                    ContentType.MEMBERSHIP_URLS.value,
                    ContentType.DONATION_DESTINATIONS.value
                )
            ).eventsList.map { e -> SignedEvent.fromProto(e) };

            val signedProfileEvents = signedEventsList.groupBy { e -> e.event.contentType }
                .map { (_, events) -> events.maxBy { it.event.unixMilliseconds ?: 0 } }

            val storageSystemState = StorageTypeSystemState.create()
            for (signedEvent in signedProfileEvents) {
                storageSystemState.update(signedEvent.event)
            }

            val signedClaimEvents = ApiMethods.getQueryIndex(
                SERVER,
                system.toProto(),
                ContentType.CLAIM.value,
                limit = 200
            ).eventsList.map { e -> SignedEvent.fromProto(e) }

            val ownedClaims: ArrayList<OwnedClaim> = arrayListOf()
            for (signedEvent in signedClaimEvents) {
                if (signedEvent.event.contentType != ContentType.CLAIM.value) {
                    continue
                }

                val response = ApiMethods.getQueryReferences(
                    SERVER,
                    Protocol.Reference.newBuilder()
                        .setReference(signedEvent.toPointer().toProto().toByteString())
                        .setReferenceType(2)
                        .build(),
                    null,
                    Protocol.QueryReferencesRequestEvents.newBuilder()
                        .setFromType(ContentType.VOUCH.value)
                        .build()
                )

                val ownedClaim = response.itemsList.map { SignedEvent.fromProto(it.event) }.getClaimIfValid(signedEvent)
                if (ownedClaim != null) {
                    ownedClaims.add(ownedClaim)
                }
            }

            Log.i(TAG, "Retrieved profile (ownedClaims = $ownedClaims)")
            val systemState = SystemState.fromStorageTypeSystemState(storageSystemState)
            return@withContext PolycentricProfile(system, systemState, ownedClaims)
        }

        /**
         * A convenience method for retrieving a comment with its author profile information.
         *
         * @param server The server to query from
         * @param pointer The pointer to the comment
         * @return Pair of (comment event, author profile events)
         */
        suspend fun getCommentWithProfile(
            server: String,
            pointer: Protocol.Pointer
        ): Pair<Protocol.Events, Protocol.Events> = withContext(Dispatchers.IO) {
            // Get the comment
            val commentEvents = getEvents(
                server = server,
                system = pointer.system,
                ranges = Protocol.RangesForSystem.newBuilder()
                    .addRangesForProcesses(
                        Protocol.RangesForProcess.newBuilder()
                            .setProcess(pointer.process)
                            .addRanges(
                                Protocol.Range.newBuilder()
                                    .setLow(pointer.logicalClock)
                                    .setHigh(pointer.logicalClock)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )

            // Get the author's profile
            val profileEvents = getQueryLatest(
                server = server,
                system = pointer.system,
                eventTypes = listOf(
                    ContentType.AVATAR.value,
                    ContentType.USERNAME.value
                )
            )

            return@withContext Pair(commentEvents, profileEvents)
        }

        /**
         * A convenience method for validating a claim by checking its vouches.
         *
         * @param server The server to query from
         * @param pointer The pointer to the claim event
         * @return QueryReferencesResponse containing the vouch information
         */
        suspend fun validateClaim(
            server: String,
            pointer: Protocol.Pointer
        ): Protocol.QueryReferencesResponse = withContext(Dispatchers.IO) {
            return@withContext getQueryReferences(
                server = server,
                reference = Protocol.Reference.newBuilder()
                    .setReference(pointer.toByteString())
                    .setReferenceType(2)
                    .build(),
                requestEvents = Protocol.QueryReferencesRequestEvents.newBuilder()
                    .setFromType(ContentType.VOUCH.value)
                    .build()
            )
        }

        /**
         * A convenience method for setting an opinion (like/dislike) on a URL reference.
         * This method handles the complete flow of setting an opinion, including backfilling and state updates.
         *
         * @param processHandle The process handle to use for the opinion operation
         * @param reference The reference to set the opinion on
         * @param opinion The opinion to set (like, dislike, or neutral)
         */
        suspend fun setOpinion(
            processHandle: ProcessHandle,
            reference: Protocol.Reference,
            opinion: Opinion
        ) = withContext(Dispatchers.IO) {
            // Set the opinion
            processHandle.opinion(reference, opinion)

            // Backfill servers
            try {
                Log.i(TAG, "Started backfill for opinion change")
                processHandle.fullyBackfillServersAnnounceExceptions()
                Log.i(TAG, "Finished backfill for opinion change")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to backfill servers after opinion change: ${e.message}")
                throw e
            }
        }

        /**
         * A convenience method for getting the likes, dislikes, and reply counts for a reference.
         *
         * @param server The server to query from
         * @param reference The reference to get counts for
         * @return Triple of (likes count, dislikes count, reply count)
         */
        suspend fun getLikesDislikesReplyCounts(
            server: String,
            reference: Protocol.Reference
        ): Triple<Long, Long, Long> = withContext(Dispatchers.IO) {
            val response = getQueryReferences(
                server = server,
                reference = reference,
                countLwwElementReferences = listOf(
                    Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                        .setFromType(ContentType.OPINION.value)
                        .setValue(ByteString.copyFrom(Opinion.like.data))
                        .build(),
                    Protocol.QueryReferencesRequestCountLWWElementReferences.newBuilder()
                        .setFromType(ContentType.OPINION.value)
                        .setValue(ByteString.copyFrom(Opinion.dislike.data))
                        .build()
                ),
                countReferences = listOf(
                    Protocol.QueryReferencesRequestCountReferences.newBuilder()
                        .setFromType(ContentType.POST.value)
                        .build()
                )
            )

            Triple(
                response.countsList[0], // likes
                response.countsList[1], // dislikes
                response.countsList[2]  // reply count
            )
        }

        fun CoroutineScope.getDataFromServerAndReassemble(dataLink: Protocol.URLInfoDataLink): Deferred<ByteBuffer> =
            async(Dispatchers.IO) {
                val server = dataLink.serversList.first() // Select the first server (simplified for this example)
                val blobProcessRangesToGet = Protocol.RangesForProcess.newBuilder()
                    .addAllRanges(dataLink.sectionsList)
                    .setProcess(dataLink.process)
                    .build()
                val blobEvents = getEvents(
                    server,
                    dataLink.system,
                    Protocol.RangesForSystem.newBuilder()
                        .addRangesForProcesses(blobProcessRangesToGet)
                        .build()
                ).eventsList.map { SignedEvent.fromProto(it) }
                val reassembledData = blobEvents.reassembleSections(dataLink.byteCount.toInt(), dataLink.sectionsList)
                    ?: throw Exception("Failed to reassemble sections due to missing data")
                reassembledData // This is returned as the result of the Deferred
            }

        /**
         * A convenience method for getting the latest event of each type from a list of events.
         * This is commonly used to process profile events where we want the most recent value of each type.
         *
         * @param events The events to process
         * @return Map of content type to the most recent event of that type
         */
        fun getLatestEventsByType(events: List<SignedEvent>): Map<Long, SignedEvent> {
            return events
                .groupBy { it.event.contentType }
                .map { (_, events) -> events.maxBy { it.event.unixMilliseconds ?: 0 } }
                .associateBy { it.event.contentType }
        }

        /**
         * A convenience method for getting a specific range of events.
         * This is commonly used when retrieving a specific event by its logical clock.
         *
         * @param server The server to query from
         * @param system The system to get events for
         * @param process The process ID
         * @param logicalClock The logical clock value
         * @return Events containing the requested event
         */
        suspend fun getEventAtClock(
            server: String,
            system: Protocol.PublicKey,
            process: Protocol.Process,
            logicalClock: Long
        ): Protocol.Events = withContext(Dispatchers.IO) {
            return@withContext getEvents(
                server = server,
                system = system,
                ranges = Protocol.RangesForSystem.newBuilder()
                    .addRangesForProcesses(
                        Protocol.RangesForProcess.newBuilder()
                            .setProcess(process)
                            .addRanges(
                                Protocol.Range.newBuilder()
                                    .setLow(logicalClock)
                                    .setHigh(logicalClock)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
        }

        /**
         * A convenience method for getting system comments with their metadata.
         * This combines getting the comments and their associated likes/dislikes/replies.
         *
         * @param server The server to query from
         * @param system The system to get comments for
         * @return List of pairs of (comment event, metadata counts)
         */
        suspend fun getSystemComments(
            server: String,
            system: PublicKey
        ): List<Pair<SignedEvent, Triple<Long, Long, Long>>> = withContext(Dispatchers.IO) {
            val comments = mutableListOf<SignedEvent>()
            Store.instance.enumerateSignedEvents(system, ContentType.POST) { signedEvent ->
                comments.add(signedEvent)
            }
            
            return@withContext comments.map { signedEvent ->
                val reference = Protocol.Reference.newBuilder()
                    .setReference(signedEvent.toPointer().toProto().toByteString())
                    .setReferenceType(2)
                    .build()
                
                val (likes, dislikes, replies) = getLikesDislikesReplyCounts(server, reference)
                Pair(signedEvent, Triple(likes, dislikes, replies))
            }
        }

        suspend fun <T> executeCall(call: Call, handler: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        continuation.resume(handler(response))
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }

        /**
         * A convenience method for retrieving comments with their author profiles efficiently.
         * This method optimizes the process by batching profile requests for all unique authors.
         *
         * @param server The server to query from
         * @param reference The reference to get comments for
         * @param cursor Optional cursor for pagination
         * @param extraByteReferences Optional additional byte references
         * @return Triple of (comments, author profiles map, next cursor)
         */
        suspend fun getCommentsWithProfiles(
            server: String,
            reference: Protocol.Reference,
            cursor: ByteArray? = null,
            extraByteReferences: List<ByteArray>? = null
        ): Triple<List<SignedEvent>, Map<PublicKey, Protocol.Events>, ByteArray?> = withContext(Dispatchers.IO) {
            // Get comments with metadata
            val response = getCommentReferences(
                server = server,
                reference = reference,
                cursor = cursor,
                extraByteReferences = extraByteReferences
            )

            // Extract unique author systems
            val uniqueAuthors = response.itemsList
                .map { SignedEvent.fromProto(it.event) }
                .map { it.event.system }
                .distinct()

            // Batch fetch profiles for all authors
            val authorProfiles = uniqueAuthors.associateWith { system ->
                getQueryLatest(
                    server = server,
                    system = system.toProto(),
                    eventTypes = listOf(
                        ContentType.USERNAME.value,
                        ContentType.AVATAR.value
                    )
                )
            }

            // Return comments, profiles, and cursor
            Triple(
                response.itemsList.map { SignedEvent.fromProto(it.event) },
                authorProfiles,
                if (response.hasCursor()) response.cursor.toByteArray() else null
            )
        }
    }
}
