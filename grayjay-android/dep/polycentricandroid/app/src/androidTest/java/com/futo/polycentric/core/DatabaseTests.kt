package com.futo.polycentric.core

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import userpackage.Protocol

class DatabaseTests {
    private lateinit var _db: SqlLiteDbHelper

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().context
        _db = SqlLiteDbHelper(context)
    }

    @Test
    fun saveLoadTestSQLite() {
        _db.recreate()

        Store.initializeSqlLiteStore(_db)

        val handle = ProcessHandle.create()
        Store.instance.addProcessSecret(handle.processSecret)

        handle.setDescription("hello")
        val loadedHandle = ProcessHandle(Store.instance.getProcessSecret(handle.system)!!, handle.system)
        val loadedState1 = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(handle.system))
        Assert.assertEquals("hello", loadedState1.description)

        loadedHandle.setDescription("hello2")
        val loadedState2 = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(handle.system))
        Assert.assertEquals("hello2", loadedState2.description)

        val secrets = Store.instance.getProcessSecrets()
        Assert.assertEquals(1, secrets.size)
        Assert.assertEquals(handle.system, secrets[0].system.publicKey)
        Assert.assertEquals(handle.processSecret.system.publicKey, secrets[0].system.publicKey)
        Assert.assertEquals(handle.processSecret.system.privateKey, secrets[0].system.privateKey)
        Assert.assertArrayEquals(handle.processSecret.system.secretKey, secrets[0].system.secretKey)
    }

    @Test
    fun deletePost() {
        _db.recreate()

        Store.initializeSqlLiteStore(_db)

        //Create handle and post
        val processHandle = ProcessHandle.create()
        processHandle.addServer(TestConstants.SERVER)

        val ev = processHandle.post("TEST")
        runBlocking {
            processHandle.fullyBackfillServers()
        }

        //Query local for event existing
        val sev = Store.instance.getSignedEvent(ev)!!
        Assert.assertEquals(ContentType.POST.value, sev.event.contentType)

        var count = 0
        Store.instance.enumerateSignedEvents(ev.system, ContentType.POST) {
            count++
        }
        Assert.assertEquals(1, count)

        //Query remote for event existing
        runBlocking {
            val events = ApiMethods.getEvents(TestConstants.SERVER, processHandle.system.toProto(), Protocol.RangesForSystem.newBuilder()
                .addRangesForProcesses(
                    Protocol.RangesForProcess.newBuilder()
                    .setProcess(processHandle.processSecret.process.toProto())
                    .addRanges(
                        Protocol.Range.newBuilder()
                        .setLow(ev.logicalClock)
                        .setHigh(ev.logicalClock)
                        .build())
                    .build())
                .build())

            Assert.assertEquals(1, events.eventsList.size)
            val sevRemote = SignedEvent.fromProto(events.eventsList.first())
            Assert.assertEquals(ContentType.POST.value, sevRemote.event.contentType)
        }

        //Delete the event
        processHandle.delete(ev.process, ev.logicalClock)
        runBlocking {
            processHandle.fullyBackfillServers()
        }

        //Query local for non existence
        Assert.assertNull(null, Store.instance.getSignedEvent(ev))

        count = 0
        Store.instance.enumerateSignedEvents(ev.system, ContentType.POST) {
            count++
        }
        Assert.assertEquals(0, count)

        //Query remotely for non existence
        runBlocking {
            val events = ApiMethods.getEvents(TestConstants.SERVER, processHandle.system.toProto(), Protocol.RangesForSystem.newBuilder()
                .addRangesForProcesses(
                    Protocol.RangesForProcess.newBuilder()
                    .setProcess(processHandle.processSecret.process.toProto())
                    .addRanges(
                        Protocol.Range.newBuilder()
                        .setLow(ev.logicalClock)
                        .setHigh(ev.logicalClock)
                        .build())
                    .build())
                .build())

            Assert.assertEquals(0, events.eventsList.size)
        }
    }

    @After
    fun cleanup() {
        _db.close()
    }
}