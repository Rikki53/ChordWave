// StringExt.kt — only toEnum from Mochy's extensions/StringExt.kt.
// (Mochy's toSQLiteQuery is db-layer; ChordWave has its own db.)

package com.music.bitchord.ui.mochy.extensions

inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T =
    if (this == null) {
        defaultValue
    } else {
        try {
            enumValueOf(this)
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }
