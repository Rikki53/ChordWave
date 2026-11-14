// SetupRerun — coordinator-written (15 Nov 2026, Phase 4 merge). "Repeat
// initial setup" (Experimental settings) asks the Modern host to show the
// setup wizard again. The row also writes SetupCompletedKey=false (Mochy's
// own mechanism), but that alone cannot re-show the wizard: the host hides
// it for anyone whose classic onboarding is done, so a Classic user who
// switches to Modern is never thrown into it. The counter is the explicit
// re-run request; MochyApp sets forceShowSetup when it goes up, and the
// wizard's completion handler clears it again.
package com.music.bitchord.ui.mochy.screens.setup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SetupRerun {

    private val _requests = MutableStateFlow(0)

    /** How many re-run requests have been made. MochyApp watches this. */
    val requests: StateFlow<Int> = _requests.asStateFlow()

    fun request() {
        _requests.value += 1
    }
}
