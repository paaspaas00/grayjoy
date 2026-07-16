package com.futo.polycentric.core

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.google.protobuf.ByteString
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import userpackage.Protocol
import userpackage.Protocol.QueryReferencesRequestCountReferences
import java.io.ByteArrayOutputStream


@RunWith(AndroidJUnit4::class)
class ProcessHandleTests {
    @Test
    fun cryptoTest() {
        Store.initializeMemoryStore()

        val processHandle = ProcessHandle.create()
        val keyPair = processHandle.processSecret.system
        val message = "test"
        val data = message.toByteArray()
        val signature = keyPair.sign(data)
        assertEquals(true, keyPair.verify(signature, data))
    }

    @Test
    fun basicPost() {
        Store.initializeMemoryStore()

        val processHandle = ProcessHandle.create()
        val events: ArrayList<SignedEvent> = arrayListOf()
        processHandle.setListener { e -> events.add(e) }

        processHandle.post("jej")
        processHandle.post("hello world")

        assertEquals(2, events.size)
        assertEquals(1L, events[0].event.logicalClock)
        assertEquals(2L, events[1].event.logicalClock)
    }

    @Test
    fun addAndRemoveServer() {
        Store.initializeMemoryStore()

        val processHandle = ProcessHandle.create()
        processHandle.addServer("127.0.0.1")
        processHandle.addServer("127.0.0.2")
        processHandle.addServer("127.0.0.3")
        processHandle.removeServer("127.0.0.1")

        val serverState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(processHandle.system))
        assertEquals(2, serverState.servers.size)
        assertArrayEquals(arrayOf("127.0.0.2", "127.0.0.3"), serverState.servers)
    }

    @Test
    fun username() {
        Store.initializeMemoryStore()

        val processHandle = ProcessHandle.create()
        processHandle.setUsername("alice")
        processHandle.setUsername("bob")

        val serverState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(processHandle.system))
        assertEquals("bob", serverState.username)
    }

    @Test
    fun description() {
        Store.initializeMemoryStore()

        val processHandle = ProcessHandle.create()
        processHandle.setDescription("test")

        val serverState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(processHandle.system))
        assertEquals("test", serverState.description)
    }

    @Test
    fun avatar() = runTest {
        Store.initializeMemoryStore()

        val processHandle = ProcessHandle.create()
        processHandle.addServer(TestConstants.SERVER)

        //Load image
        val imageBytes: ByteArray
        run {
            val context = getInstrumentation().targetContext
            val imageId = context.resources.getIdentifier("image", "drawable", context.packageName)
            val bitmap = BitmapFactory.decodeResource(context.resources, imageId)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            imageBytes = stream.toByteArray()
            stream.close()
        }

        //Set avatar
        val eventLogicalClock: Long
        run {
            val imageBundleBuilder = Protocol.ImageBundle.newBuilder()
            val imageRanges = processHandle.publishBlob(imageBytes)
            val imageManifest = Protocol.ImageManifest.newBuilder()
                .setMime("image/jpeg")
                .setWidth(2560)
                .setHeight(424)
                .setByteCount(imageBytes.size.toLong())
                .setProcess(processHandle.processSecret.process.toProto())
                .addAllSections(imageRanges.map { it.toProto() })
                .build()

            imageBundleBuilder.addImageManifests(imageManifest)
            eventLogicalClock = processHandle.setAvatar(imageBundleBuilder.build()).logicalClock
        }

        processHandle.fullyBackfillServers()

        //Read avatar and compare
        run {
            val avatarProcessRangesToGet = Protocol.RangesForProcess.newBuilder()
                .addRanges(Protocol.Range.newBuilder().setLow(eventLogicalClock).setHigh(eventLogicalClock).build())
                .setProcess(processHandle.processSecret.process.toProto())
                .build()
            val avatarEvents = ApiMethods.getEvents(TestConstants.SERVER, processHandle.system.toProto(), Protocol.RangesForSystem.newBuilder()
                .addRangesForProcesses(avatarProcessRangesToGet).build())

            val avatarSignedEvent = avatarEvents.eventsList.first()
            val avatarEvent = SignedEvent.fromProto(avatarSignedEvent).event
            assertEquals(ContentType.AVATAR.value, avatarEvent.contentType)
            assertNotNull(avatarEvent.lwwElement)

            val imageBundle = Protocol.ImageBundle.parseFrom(avatarEvent.lwwElement!!.value)
            assertEquals(1, imageBundle.imageManifestsList.size)

            val imageManifest = imageBundle.imageManifestsList.first()
            assertEquals("image/jpeg", imageManifest.mime)
            assertEquals(2560, imageManifest.width)
            assertEquals(424, imageManifest.height)
            assertEquals(imageBytes.size.toLong(), imageManifest.byteCount)
            assertEquals(processHandle.processSecret.process.toProto(), imageManifest.process)

            val bytes = ApiMethods.getDataFromServerAndReassemble(imageManifest.toURLInfoDataLink(processHandle.system.toProto(), processHandle.processSecret.process.toProto(), listOf(TestConstants.SERVER)))
            val imageData = ImageData(imageManifest.mime, imageManifest.width.toInt(), imageManifest.height.toInt(), bytes.array())
            assertEquals(imageManifest.byteCount.toInt(), imageData.data.size)
            assertArrayEquals(imageBytes, imageData.data)
        }
    }

    @Test
    fun sync() = runTest {
        Store.initializeMemoryStore()

        val s1p1 = ProcessHandle.create()
        s1p1.addServer(TestConstants.SERVER)
        s1p1.setDescription("hello")

        val claim = Claims.hackerNews("pg")
        val claimPointer = s1p1.claim(claim)
        s1p1.vouch(claimPointer)

        Synchronization.fullyBackFillServers(s1p1, s1p1.system)

        val s2p1 = ProcessHandle.create()

        Synchronization.fullyBackFillClient(s2p1, s1p1.system, TestConstants.SERVER)

        val s1State = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(s1p1.system))

        assertEquals("hello", s1State.description)

        val resolved = ApiMethods.getResolveClaim(TestConstants.SERVER, s1p1.system.toProto(), ClaimType.HACKER_NEWS.value, "pg")
        val events = resolved.matchesList.flatMap { arrayListOf(it.claim).apply { addAll(it.proofChainList) } }
        assertEquals(1, resolved.matchesList.size)
        assertEquals(2, events.size)
    }

    @Test
    fun validateClaim() = runTest {
        Store.initializeMemoryStore()

        val s1p1 = ProcessHandle.create()
        s1p1.addServer(TestConstants.SERVER)
        Log.i("ProcessHandleTests", s1p1.processSecret.toProto().toByteArray().toBase64())

        val claim = Claims.hackerNews("pg")
        val claimPointer = s1p1.claim(claim)
        s1p1.vouch(claimPointer)

        Synchronization.fullyBackFillServers(s1p1, s1p1.system)

        val resolved = ApiMethods.getResolveClaim(TestConstants.SERVER, s1p1.system.toProto(), ClaimType.HACKER_NEWS.value, "pg")
        val events = resolved.matchesList.flatMap { arrayListOf(it.claim).apply { addAll(it.proofChainList) } }
        assertEquals(1, resolved.matchesList.size)
        assertEquals(2, events.size)

        val resolvedEvents = events.map { i -> SignedEvent.fromProto(i) }
        val resolvedClaimEvent = resolvedEvents.first { e -> e.event.contentType == ContentType.CLAIM.value }.event
        val resolvedClaim = Protocol.Claim.parseFrom(resolvedClaimEvent.content)
        val resolvedVouchEvent = resolvedEvents.first { e -> e.event.contentType == ContentType.VOUCH.value }.event
        val reference = resolvedVouchEvent.references.first()
        val referencePointer = Pointer.fromProto(Protocol.Pointer.parseFrom(reference.reference))

        assertEquals(ClaimType.HACKER_NEWS.value, resolvedClaim.claimType)
        assertEquals(2, reference.referenceType)

        assertEquals(resolvedClaimEvent.system, referencePointer.system)
        assertEquals(resolvedClaimEvent.process, referencePointer.process)
        assertEquals(resolvedClaimEvent.logicalClock, referencePointer.logicalClock)
    }

    @Test
    fun delete() {
        Store.initializeMemoryStore()

        val processHandle = ProcessHandle.create()
        processHandle.setUsername("alice")
        val setNameBob = processHandle.setUsername("bob")

        val serverState1 = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(processHandle.system))
        assertEquals("bob", serverState1.username)

        processHandle.delete(setNameBob.process, setNameBob.logicalClock)

        val serverState2 = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(processHandle.system))
        assertEquals("alice", serverState2.username)
    }

    @Test
    fun deletePost() {
        Store.initializeMemoryStore()

        //Create handle and post
        val processHandle = ProcessHandle.create()
        processHandle.addServer(TestConstants.SERVER)

        val ev = processHandle.post("TEST")
        runBlocking {
            processHandle.fullyBackfillServers()
        }

        //Query local for event existing
        val sev = Store.instance.getSignedEvent(ev)!!
        assertEquals(ContentType.POST.value, sev.event.contentType)

        var count = 0
        Store.instance.enumerateSignedEvents(ev.system, ContentType.POST) {
            count++
        }
        assertEquals(1, count)

        //Query remote for event existing
        runBlocking {
            val events = ApiMethods.getEvents(TestConstants.SERVER, processHandle.system.toProto(), Protocol.RangesForSystem.newBuilder()
                .addRangesForProcesses(Protocol.RangesForProcess.newBuilder()
                    .setProcess(processHandle.processSecret.process.toProto())
                    .addRanges(Protocol.Range.newBuilder()
                        .setLow(ev.logicalClock)
                        .setHigh(ev.logicalClock)
                        .build())
                    .build())
                .build())

            assertEquals(1, events.eventsList.size)
            val sevRemote = SignedEvent.fromProto(events.eventsList.first())
            assertEquals(ContentType.POST.value, sevRemote.event.contentType)
        }

        //Delete the event
        processHandle.delete(ev.process, ev.logicalClock)
        runBlocking {
            processHandle.fullyBackfillServers()
        }

        //Query local for non existence
        count = 0
        Store.instance.enumerateSignedEvents(ev.system, ContentType.POST) {
            count++
        }
        assertEquals(0, count)

        assertNull(null, Store.instance.getSignedEvent(ev))

        //Query remotely for non existence
        runBlocking {
            val events = ApiMethods.getEvents(TestConstants.SERVER, processHandle.system.toProto(), Protocol.RangesForSystem.newBuilder()
                .addRangesForProcesses(Protocol.RangesForProcess.newBuilder()
                    .setProcess(processHandle.processSecret.process.toProto())
                    .addRanges(Protocol.Range.newBuilder()
                        .setLow(ev.logicalClock)
                        .setHigh(ev.logicalClock)
                        .build())
                    .build())
                .build())

            assertEquals(0, events.eventsList.size)
        }
    }

    @Test
    fun comment() = runTest {
        Store.initializeMemoryStore()

        val subject = Models.referenceFromBuffer(("https://fake.com/" + Math.random().toString()).toByteArray())
        val createHandle: suspend (String) -> ProcessHandle = { username: String ->
            val handle = ProcessHandle.create()
            handle.addServer(TestConstants.SERVER)
            handle.setUsername(username)
            Synchronization.fullyBackFillServers(handle, handle.system)
            handle
        }

        val vonNeumann = createHandle("Von Neumann")
        val godel = createHandle("Godel")
        val babbage = createHandle("Babbage")
        val turing = createHandle("Turing")

        val rootPosts = mutableListOf<Pointer>()

        // vonNeumann posts 5 times
        for (i in 0 until 5) {
            rootPosts.add(vonNeumann.post(i.toString(), subject))
        }

        // godel comments 10 times
        for (i in 0 until 10) {
            rootPosts.add(godel.post(i.toString(), subject))
        }

        // babbage likes the first three comments
        for (i in 0 until 3) {
            babbage.opinion(rootPosts[i].toReference(), Opinion.like)
        }

        // babbage dislikes the last two comments
        for (i in rootPosts.size - 2 until rootPosts.size) {
            babbage.opinion(rootPosts[i].toReference(), Opinion.dislike)
        }

        // godel likes the first two comments
        for (i in 0 until 2) {
            godel.opinion(rootPosts[i].toReference(), Opinion.like)
        }

        // godel dislikes the third comment
        godel.opinion(rootPosts[2].toReference(), Opinion.dislike)

        // turing replies vonNeumann's comment three times
        for (i in 0 until 3) {
            turing.post(i.toString(), rootPosts[1].toReference())
        }

        vonNeumann.fullyBackfillServers()
        godel.fullyBackfillServers()
        babbage.fullyBackfillServers()
        turing.fullyBackfillServers()

        val queryReferences = ApiMethods.getQueryReferences(TestConstants.SERVER, subject, null,
            Protocol.QueryReferencesRequestEvents.newBuilder()
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
                .addCountReferences(QueryReferencesRequestCountReferences.newBuilder()
                    .setFromType(ContentType.POST.value)
                    .build())
                .build()
        )

        assertEquals(rootPosts.count(), queryReferences.itemsCount)

        val pointerToString = { pointer: Pointer -> pointer.toProto().toByteArray().toBase64() }
        val pointerToItem: HashMap<String, Protocol.QueryReferencesResponseEventItem> = hashMapOf()

        for (item in queryReferences.itemsList) {
            assertTrue(item.hasEvent())

            val signedEvent = SignedEvent.fromProto(item.event)
            val pointer = signedEvent.toPointer()
            pointerToItem[pointerToString(pointer)] = item
        }

        val checkResult = { i: Int, likes: Long, dislikes: Long, replies: Long ->
            val item = pointerToItem[pointerToString(rootPosts[i])]
            assertNotNull(item)

            assertEquals(likes, item!!.getCounts(0))
            assertEquals(dislikes, item!!.getCounts(1))
            assertEquals(replies, item!!.getCounts(2))
        }

        // Ensure the API has the expected counts and events
        checkResult(0, 2, 0, 0)
        checkResult(1, 2, 0, 3)
        checkResult(2, 1, 1, 0)
        for (i in 3..12) {
            checkResult(i, 0, 0, 0)
        }
        checkResult(13, 0, 1, 0)
        checkResult(14, 0, 1, 0)
    }

    @Test
    fun saveLoadTest() {
        Store.initializeMemoryStore()

        val handle = ProcessHandle.create()
        Store.instance.addProcessSecret(handle.processSecret)

        handle.setDescription("hello")
        val loadedHandle = ProcessHandle(Store.instance.getProcessSecret(handle.system)!!, handle.system)
        val loadedState1 = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(handle.system))
        assertEquals("hello", loadedState1.description)

        loadedHandle.setDescription("hello2")
        val loadedState2 = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(handle.system))
        assertEquals("hello2", loadedState2.description)
    }

    @Test
    fun usernameChange() = runTest {
        Store.initializeMemoryStore()

        val s1p1 = ProcessHandle.create()
        s1p1.addServer(TestConstants.SERVER)
        s1p1.setUsername("test1")
        Synchronization.fullyBackFillServers(s1p1, s1p1.system)

        run {
            val events = ApiMethods.getQueryLatest(
                TestConstants.SERVER,
                s1p1.system.toProto(),
                listOf(ContentType.USERNAME.value)
            )

            val storageSystemState = StorageTypeSystemState.create()
            for (e in events.eventsList) {
                val signedEvent = SignedEvent.fromProto(e)
                storageSystemState.update(signedEvent.event)
            }

            val systemState = SystemState.fromStorageTypeSystemState(storageSystemState)
            assertEquals("test1", systemState.username)
        }

        s1p1.setUsername("test2")
        Synchronization.fullyBackFillServers(s1p1, s1p1.system)

        run {
            val events = ApiMethods.getQueryLatest(
                TestConstants.SERVER,
                s1p1.system.toProto(),
                listOf(ContentType.USERNAME.value)
            )

            val storageSystemState = StorageTypeSystemState.create()
            for (e in events.eventsList) {
                val signedEvent = SignedEvent.fromProto(e)
                storageSystemState.update(signedEvent.event)
            }

            val systemState = SystemState.fromStorageTypeSystemState(storageSystemState)
            assertEquals("test2", systemState.username)
        }
    }

    @Test
    fun resolveAndQuery() = runTest {
        Store.initializeMemoryStore()

        val s1p1 = ProcessHandle.create()
        s1p1.addServer(TestConstants.SERVER)
        s1p1.setDescription("howdy")

        val claim = Claims.hackerNews("fake_user")
        val claimPointer = s1p1.claim(claim)
        val vouchPointer = s1p1.vouch(claimPointer)

        Synchronization.fullyBackFillServers(s1p1, s1p1.system)

        val resolved = ApiMethods.getResolveClaim(
            TestConstants.SERVER,
            s1p1.system.toProto(),
            ClaimType.HACKER_NEWS.value,
            "fake_user"
        )

        val protoEvents = resolved.matchesList.flatMap { arrayListOf(it.claim).apply { addAll(it.proofChainList) } }
        val validClaims = protoEvents.map { e -> SignedEvent.fromProto(e) }.getValidClaims()
        assertEquals(1, validClaims.size)

        val events = ApiMethods.getQueryIndex(
            TestConstants.SERVER,
            validClaims[0].system.toProto(),
            ContentType.DESCRIPTION.value
        )

        val storageSystemState = StorageTypeSystemState.create()
        for (e in events.eventsList) {
            val signedEvent = SignedEvent.fromProto(e)
            storageSystemState.update(signedEvent.event)
        }

        val systemState = SystemState.fromStorageTypeSystemState(storageSystemState)
        assertEquals("howdy", systemState.description)

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

    private fun loadImageAsByteArray(drawableId: Int): ByteArray {
        val context: Context = ApplicationProvider.getApplicationContext()
        val resources: Resources = context.resources

        resources.openRawResource(drawableId).use { inputStream ->
            ByteArrayOutputStream().use { outputStream ->
                var bytesRead: Int
                val buffer = ByteArray(1024)
                while (inputStream.read(buffer, 0, buffer.size).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                return outputStream.toByteArray()
            }
        }
    }
}