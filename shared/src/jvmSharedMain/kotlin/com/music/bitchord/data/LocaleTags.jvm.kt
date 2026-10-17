package com.music.bitchord.data

import java.util.Locale

internal actual fun systemLanguageTag(): String = Locale.getDefault().toLanguageTag()
