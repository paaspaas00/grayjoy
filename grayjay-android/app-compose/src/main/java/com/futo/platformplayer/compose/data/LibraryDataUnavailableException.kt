package com.futo.platformplayer.compose.data

internal class LibraryDataUnavailableException(cause: Exception) :
    IllegalStateException("Existing library data cannot be read; refusing to overwrite it", cause)
