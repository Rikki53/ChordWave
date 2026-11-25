// The photo every avatar slot shows: the signed-in Google account's picture
// first — the same source the Profile ID Card wears (MainViewModel.account)
// — then the locally picked profile picture, then nothing (the caller shows
// its generic person icon). Signed out, or with no picture anywhere, the
// slots fall back exactly as they did before.

package com.music.bitchord.ui.mochy.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.mochy.constants.ProfilePicUriKey
import com.music.bitchord.ui.mochy.utils.rememberPreference

/** The Google account photo when signed in, else the local profile picture, else null. */
@Composable
fun rememberProfilePhotoUrl(): String? {
    val mainViewModel: MainViewModel = viewModel()
    val account by mainViewModel.account.collectAsState()
    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")
    return account?.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: profilePicUri.takeIf { it.isNotBlank() }
}
