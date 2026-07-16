package com.futo.platformplayer.logging

abstract class FileLogConsumer : ILogConsumer {
    open fun submitLogs() = Unit
    open fun submitLogsAsync() = Unit
    open fun flushBlocking() = Unit
}
