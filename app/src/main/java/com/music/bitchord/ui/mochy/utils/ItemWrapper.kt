// Ported from Mochy Music (GPL-3.0): ui/utils/ItemWrapper.kt
// ItemWrapper.kt

package com.music.bitchord.ui.mochy.utils

import androidx.compose.runtime.mutableStateOf

class ItemWrapper<T>(
    val item: T,
) {
    private val _isSelected = mutableStateOf(true)

    var isSelected: Boolean
        get() = _isSelected.value
        set(value) {
            _isSelected.value = value
        }
}
