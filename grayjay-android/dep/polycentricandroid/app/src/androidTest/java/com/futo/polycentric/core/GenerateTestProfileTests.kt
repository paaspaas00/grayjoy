package com.futo.polycentric.core

import android.util.Log
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import userpackage.Protocol

class GenerateTestProfileTests {
    @Test
    fun scenarioKoenFuto() = runTest {
        Store.initializeMemoryStore()

        val s1p1 = ProcessHandle.create()
        s1p1.addServer(TestConstants.SERVER)
        s1p1.setUsername("scenarioKoenFuto subject")
        s1p1.setStore("https://amazon.com/")

        val claims = listOf(
            s1p1.claim(Claims.youtube("UCR7KMD7jkSefYYWgSwNPEBA", "@koen-futo")),
            s1p1.claim(Claims.rumbleChannel("c-3366838")),
            s1p1.claim(Claims.rumbleUser("koenfuto")),
            s1p1.claim(Claims.bitcoin("bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"))
        )

        Synchronization.fullyBackFillServers(s1p1, s1p1.system)

        val s2p1 = ProcessHandle.create()
        s2p1.addServer(TestConstants.SERVER)
        s2p1.setUsername("scenarioKoenFuto authority")

        val vouches = arrayListOf<Pointer>()
        for (claim in claims) {
            vouches.add(s2p1.vouch(claim))
        }

        Synchronization.fullyBackFillServers(s2p1, s2p1.system)

        //Verify the bundle
        for (i in 0 until vouches.size) {
            val claimPointer = claims[i]
            val vouchPointer = vouches[i]
            val queryReferences = ApiMethods.getQueryReferences(TestConstants.SERVER, claimPointer.toReference(), null,
                Protocol.QueryReferencesRequestEvents.newBuilder()
                    .setFromType(ContentType.VOUCH.value)
                    .build())

            assertEquals(1, queryReferences.itemsCount)
            val item = queryReferences.getItems(0)
            val vouchEvent = SignedEvent.fromProto(item.event)
            assertEquals(vouchPointer, vouchEvent.toPointer())
            assertEquals(1, vouchEvent.event.references.size)
            assertEquals(claimPointer.toProto().toByteString(), vouchEvent.event.references[0].reference)
        }

        //Verify resolve claim
        for (pair in listOf(
            Pair(ClaimType.YOUTUBE.value, Pair(1L, "UCR7KMD7jkSefYYWgSwNPEBA")),
            Pair(ClaimType.YOUTUBE.value, Pair(0L, "@koen-futo")),
            Pair(ClaimType.RUMBLE.value, Pair(0L, "koenfuto")),
            Pair(ClaimType.RUMBLE.value, Pair(1L, "c-3366838"))
        )) {
            val resolveClaimAnyField = ApiMethods.getResolveClaim(TestConstants.SERVER, s2p1.system.toProto(), pair.first, pair.second.second)
            assertEquals(1, resolveClaimAnyField.matchesCount)
            assertEquals(s1p1.system, SignedEvent.fromProto(resolveClaimAnyField.getMatches(0).claim).event.system)

            val resolveClaimSpecificField = ApiMethods.getResolveClaim(TestConstants.SERVER, s2p1.system.toProto(), pair.first, pair.second.first, pair.second.second)
            assertEquals(1, resolveClaimSpecificField.matchesCount)
            assertEquals(s1p1.system, SignedEvent.fromProto(resolveClaimSpecificField.getMatches(0).claim).event.system)
        }

        Log.i("EXPORT_BUNDLE s1p1", createExportBundle(s1p1))
        Log.i("EXPORT_BUNDLE s2p1", createExportBundle(s2p1))
        Log.i("verifier pub key", s2p1.system.key.toBase64())
    }

    @Test
    fun scenarioEveryClaimType() = runTest {
        Store.initializeMemoryStore()

        val s1p1 = ProcessHandle.create()
        s1p1.addServer(TestConstants.SERVER)
        s1p1.setUsername("scenarioEveryClaimType subject")

        val claims = listOf(
            s1p1.claim(Claims.hackerNews("eron_wolf")),
            s1p1.claim(Claims.youtube("UCR7KMD7jkSefYYWgSwNPEBA", "@koen-futo")),
            s1p1.claim(Claims.odysee("@FUTO", "ef3c158d01613a6d60aa32e4b6c6fdf00a3b64f5")),
            s1p1.claim(Claims.rumbleChannel("rossmanngroup")),
            s1p1.claim(Claims.discord("thekinocorner")),
            s1p1.claim(Claims.instagram("@the_kino_corner")),
            s1p1.claim(Claims.twitch("thekinocorner")),
            s1p1.claim(Claims.url("https://futo.org/grants"))
        )

        Synchronization.fullyBackFillServers(s1p1, s1p1.system)

        val s2p1 = ProcessHandle.create()
        s2p1.addServer(TestConstants.SERVER)
        s2p1.setUsername("scenarioEveryClaimType authority")

        val vouches = arrayListOf<Pointer>()
        for (claim in claims) {
            vouches.add(s2p1.vouch(claim))
        }

        Synchronization.fullyBackFillServers(s2p1, s2p1.system)

        //Verify the bundle
        for (i in 0 until vouches.size) {
            val claimPointer = claims[i]
            val vouchPointer = vouches[i]
            val queryReferences = ApiMethods.getQueryReferences(TestConstants.SERVER, claimPointer.toReference(), null,
                Protocol.QueryReferencesRequestEvents.newBuilder()
                    .setFromType(ContentType.VOUCH.value)
                    .build())

            assertEquals(1, queryReferences.itemsCount)
            val item = queryReferences.getItems(0)
            val vouchEvent = SignedEvent.fromProto(item.event)
            assertEquals(vouchPointer, vouchEvent.toPointer())
            assertEquals(1, vouchEvent.event.references.size)
            assertEquals(claimPointer.toProto().toByteString(), vouchEvent.event.references[0].reference)
        }

        Log.i("EXPORT_BUNDLE s1p1", createExportBundle(s1p1))
        Log.i("EXPORT_BUNDLE s2p1", createExportBundle(s2p1))
    }

    private fun createExportBundle(processHandle: ProcessHandle): String {
        val relevantContentTypes = listOf(ContentType.SERVER.value, ContentType.AVATAR.value, ContentType.USERNAME.value)
        val crdtSetItems = arrayListOf<Pair<SignedEvent, StorageTypeCRDTSetItem>>()
        val crdtItems = arrayListOf<Pair<SignedEvent, StorageTypeCRDTItem>>()

        Store.instance.enumerateSignedEvents(processHandle.system) { signedEvent ->
            if (!relevantContentTypes.contains(signedEvent.event.contentType)) {
                return@enumerateSignedEvents
            }

            val event = signedEvent.event
            event.lwwElementSet?.let { lwwElementSet ->
                val foundIndex = crdtSetItems.indexOfFirst { pair ->
                    pair.second.contentType == event.contentType && pair.second.value.contentEquals(lwwElementSet.value)
                }

                var found = false
                if (foundIndex != -1) {
                    val foundPair = crdtSetItems[foundIndex]
                    if (foundPair.second.unixMilliseconds < lwwElementSet.unixMilliseconds) {
                        foundPair.second.operation = lwwElementSet.operation
                        foundPair.second.unixMilliseconds = lwwElementSet.unixMilliseconds
                        found = true
                    }
                }

                if (!found) {
                    crdtSetItems.add(Pair(signedEvent, StorageTypeCRDTSetItem(event.contentType, lwwElementSet.value, lwwElementSet.unixMilliseconds, lwwElementSet.operation)))
                }
            }

            event.lwwElement?.let { lwwElement ->
                val foundIndex = crdtItems.indexOfFirst { pair ->
                    pair.second.contentType == event.contentType
                }

                var found = false
                if (foundIndex != -1) {
                    val foundPair = crdtItems[foundIndex]
                    if (foundPair.second.unixMilliseconds < lwwElement.unixMilliseconds) {
                        foundPair.second.value = lwwElement.value
                        foundPair.second.unixMilliseconds = lwwElement.unixMilliseconds
                        found = true
                    }
                }

                if (!found) {
                    crdtItems.add(Pair(signedEvent, StorageTypeCRDTItem(event.contentType, lwwElement.value, lwwElement.unixMilliseconds)))
                }
            }
        }

        val relevantEvents = arrayListOf<SignedEvent>()
        for (pair in crdtSetItems) {
            relevantEvents.add(pair.first)
        }

        for (pair in crdtItems) {
            relevantEvents.add(pair.first)
        }

        val exportBundle = Protocol.ExportBundle.newBuilder()
            .setKeyPair(processHandle.processSecret.system.toProto())
            .setEvents(
                Protocol.Events.newBuilder()
                .addAllEvents(relevantEvents.map { it.toProto() })
                .build())
            .build()

        val urlInfo = Protocol.URLInfo.newBuilder()
            .setUrlType(3)
            .setBody(exportBundle.toByteString())
            .build()

        return "polycentric://" + urlInfo.toByteArray().toBase64Url()
    }
}