// Ported from Mochy Music (GPL-3.0): ui/utils/NavControllerUtils.kt
// NavControllerUtils.kt

package com.music.bitchord.ui.mochy.utils

import androidx.navigation.NavController
import com.music.bitchord.ui.mochy.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}
