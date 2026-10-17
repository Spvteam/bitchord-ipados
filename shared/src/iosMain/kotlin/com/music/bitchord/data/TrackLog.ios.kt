package com.music.bitchord.data

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal actual fun trackLogAbout(id: String?): CoroutineContext = EmptyCoroutineContext
internal actual fun currentWorkingTrack(): String? = null
