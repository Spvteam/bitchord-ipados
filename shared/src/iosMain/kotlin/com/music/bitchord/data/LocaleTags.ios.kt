package com.music.bitchord.data

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

internal actual fun systemLanguageTag(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"
