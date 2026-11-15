// Ported from Mochy Music (GPL-3.0): eq/data/FilterType.kt (verbatim, package only).
package com.music.bitchord.ui.mochy.eq.data

import kotlinx.serialization.Serializable

@Serializable
enum class FilterType {

    PK,

    LSC,

    HSC,

    LPQ,

    HPQ
}
