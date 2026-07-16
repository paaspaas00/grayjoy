package com.futo.polycentric.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import userpackage.Protocol

class Synchronization {
    companion object {
        const val TAG = "Synchronization"

        private fun loadRanges(system: PublicKey, process: Process, ranges: List<Range>): List<SignedEvent> {
            val result = mutableListOf<SignedEvent>()

            for (range in ranges) {
                var i = range.low
                while (i <= range.high) {
                    val event = Store.instance.getSignedEvent(system, process, i)

                    if (event != null) {
                        result.add(event)
                    }

                    i++
                }
            }

            return result
        }

        private fun ingest(processHandle: ProcessHandle, events: Protocol.Events) {
            synchronized(processHandle) {
                for (rawEvent in events.eventsList) {
                    processHandle.ingest(SignedEvent.fromProto(rawEvent))
                }
            }
        }

        suspend fun fullyBackFillClient(processHandle: ProcessHandle, system: PublicKey, server: String) {
            while (backfillClient(processHandle, system, server)) {}
        }

        suspend fun backfillClient(processHandle: ProcessHandle, system: PublicKey, server: String): Boolean = withContext(Dispatchers.IO) {
            val systemProto = system.toProto()
            val rangesForSystem = RangesForSystem.fromProto(ApiMethods.getRanges(server, systemProto))
            var progress = false

            for (item in rangesForSystem.rangesForProcesses) {
                val processState = Store.instance.getProcessState(system, item.process)
                val clientNeeds = Ranges.subtract(item.ranges, processState.ranges)
                if (clientNeeds.isEmpty()) {
                    Log.i(TAG, "There is nothing to backfill client")
                    continue
                }

                for (range in clientNeeds) {
                    Log.i(TAG, "Backfilling client range ${range.low}-${range.high}")
                }

                val batch = Ranges.takeRangesMaxItems(clientNeeds, 10L)
                val events = ApiMethods.getEvents(server, systemProto, Protocol.RangesForSystem.newBuilder()
                    .addRangesForProcesses(
                        Protocol.RangesForProcess.newBuilder()
                            .setProcess(item.process.toProto())
                            .addAllRanges(batch.map { Protocol.Range.newBuilder()
                                .setLow(it.low)
                                .setHigh(it.high)
                                .build()
                            })
                            .build())
                    .build())

                if (events.eventsCount > 0) {
                    progress = true
                }

                ingest(processHandle, events)
            }

            return@withContext progress
        }

        fun saveBatch(processHandle: ProcessHandle, events: Protocol.Events) {
            synchronized(processHandle) {
                for (e in events.eventsList) {
                    processHandle.ingest(SignedEvent.fromProto(e))
                }
            }
        }

        suspend fun fullyBackFillServers(processHandle: ProcessHandle, system: PublicKey): Map<String, Throwable> = withContext(Dispatchers.IO) {
            val systemState = SystemState.fromStorageTypeSystemState(Store.instance.getSystemState(processHandle.system))
            val systemProto = system.toProto()

            val exceptions = hashMapOf<String, Throwable>()
            coroutineScope {
                systemState.servers.map { server ->
                    async {
                        try {
                            var progress: Boolean
                            // Because of CDN TTLs, we can't wait to check if the server got the events so we have to
                            // rely on the strong consistency and the monotonic increases of the event index to determine what we need to backfill
                            // For the first event that results in a backfill error, the server is guaranteed to have received all the events up to that index
                            val rangesForSystem = RangesForSystem.fromProto(ApiMethods.getRanges(server, systemProto))
                            for (process in systemState.processes) {
                                var rangesForProcess: List<Range> = emptyList()

                                for (item in rangesForSystem.rangesForProcesses) {
                                    if (item.process == process) {
                                        rangesForProcess = item.ranges
                                        break
                                    }
                                }

                                val processState = Store.instance.getProcessState(system, process)
                                val serverNeeds = Ranges.subtract(processState.ranges, rangesForProcess)
                                if (serverNeeds.isEmpty()) {
                                    Log.i(TAG, "There is nothing to backfill")
                                    continue
                                }

                                for (range in serverNeeds) {
                                    Log.i(TAG, "Backfilling range ${range.low}-${range.high}")
                                }

                                var serverNeedsBatch = serverNeeds
                                while (serverNeedsBatch.isNotEmpty()) {
                                    val batch = Ranges.takeRangesMaxItems(serverNeedsBatch, 10L)
                                    val events = loadRanges(system, process, batch)
                                    ApiMethods.postEvents(server, Protocol.Events.newBuilder().addAllEvents(events.map { it.toProto() }).build())
                                    serverNeedsBatch = Ranges.subtract(serverNeedsBatch, batch)
                                }
                            }
                        } catch (err: Throwable) {
                            Log.e(TAG, "An error occurred while backfilling server", err)
                            exceptions[server] = err
                        }
                    }
                }.awaitAll()
            }

            return@withContext exceptions
        }
    }
}