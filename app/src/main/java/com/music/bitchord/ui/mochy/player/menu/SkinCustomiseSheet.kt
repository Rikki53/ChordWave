// Ported from Mochy Music (GPL-3.0): ui/player/menu/SkinCustomiseSheet.kt
// SHEET UI ONLY — the skin-customisation engine (module system: ActiveSkin,
// ModulesViewModel, ModuleOptions) is out of scope. The sheet renders
// faithfully in its empty state ("Turn the module on to change these."),
// which is what Mochy itself showed with no skin module active. The fields
// list can only ever be filled by the module system, so there is no
// half-wired state to fake. See NEEDS.md §(b) S1.
//
// Unreachable for now: its only caller was ModuleMenuRows' customise row,
// and ModuleMenuRows is skipped (modules system out of scope). Kept staged
// so a future caller (e.g. Settings) can open it. NEEDS.md §(c) D3.

package com.music.bitchord.ui.mochy.player.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import timber.log.Timber

@Composable
fun SkinCustomiseSheet(onDismiss: () -> Unit) {
    // TODO(phase3-adapter): skin-customisation engine (NEEDS.md §(b) S1).
    // Mochy read the active skin module (ActiveSkin.current), its player
    // customise keys, and the module's fields from ModulesViewModel. CW has
    // no module system, so there is no skin and no fields — the sheet shows
    // its empty state, exactly as Mochy did with no skin active.
    Timber.w("SkinCustomiseSheet: no skin module system in ChordWave; showing empty state")

    MenuSheetSurface(onDismiss = onDismiss, wrapHeight = true) { _ ->
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.skin_customise_title),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.skin_customise_empty),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(18.dp))
            // Mochy rendered ModuleOptions here when a skin module had fields.
            // No module system in CW, so there is nothing to render.
        }
    }
}
