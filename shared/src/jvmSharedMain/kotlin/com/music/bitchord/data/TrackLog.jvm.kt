package com.music.bitchord.data

import kotlinx.coroutines.asContextElement
import kotlin.coroutines.CoroutineContext

private val working = ThreadLocal<String?>()

internal actual fun trackLogAbout(id: String?): CoroutineContext = working.asContextElement(id)
internal actual fun currentWorkingTrack(): String? = working.get()
