// Ported from Mochy Music (GPL-3.0): viewmodels/TransferViewModel.kt
// STUB (Phase 4C): the TransferStep sealed interface is VERBATIM from Mochy
// (SetupWizardScreen + RestoringPage read it). The ViewModel itself is an
// honest stub: ChordWave has no transfer engine — it cannot read Mochy's
// export zips (TransferExporter/TransferImporter/TransferRestore are Mochy
// database machinery with no ChordWave equivalent). check() fails with a
// message the ImportMode page shows, importFrom() reports Failed the same
// way, and exportTo() fails too. When a real transfer engine lands, it
// REPLACES this file (same path, same public API).

package com.music.bitchord.ui.mochy.viewmodels

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import com.music.bitchord.ui.mochy.importer.transfer.TransferManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/** where an export or import stands, progress is null while there's no number to show yet */
sealed interface TransferStep {
    data object Idle : TransferStep
    data class Working(val progress: Float?) : TransferStep
    data object Done : TransferStep
    data class Failed(val message: String) : TransferStep
}

/** exporting everything to one zip and bringing a zip back in, for onboarding and the storage page */
@HiltViewModel
@Suppress("UNUSED_PARAMETER")
class TransferViewModel @Inject constructor(
    @ApplicationContext @Suppress("unused") private val context: Context,
) : ViewModel() {
    private val _export = MutableStateFlow<TransferStep>(TransferStep.Idle)
    val export: StateFlow<TransferStep> = _export.asStateFlow()

    private val _import = MutableStateFlow<TransferStep>(TransferStep.Idle)
    val import: StateFlow<TransferStep> = _import.asStateFlow()

    private val _manifest = MutableStateFlow<TransferManifest?>(null)

    /** what the zip being imported holds, for the progress page to show */
    val manifest: StateFlow<TransferManifest?> = _manifest.asStateFlow()

    /** the name the export suggests, so it's easy to spot in downloads later */
    fun exportFileName(): String = "chordwave-export-${LocalDate.now()}.zip"

    fun exportTo(target: Uri) {
        if (_export.value is TransferStep.Working) return
        Timber.w("TransferViewModel stub: exportTo ignored (no transfer engine yet)")
        _export.value = TransferStep.Failed("Export isn't available in ChordWave yet.")
    }

    /** checks a picked zip before anything is unpacked, the failure message is ready to show */
    suspend fun check(source: Uri): Result<TransferManifest> {
        Timber.w("TransferViewModel stub: check ignored (no transfer engine yet)")
        return Result.failure(Exception("ChordWave can't read that export yet."))
    }

    /** true when the zip has local songs and chordwave can't see the phone's music yet, so it's worth asking first */
    fun wantsAudioPermission(manifest: TransferManifest): Boolean = false

    /** unpacks the zip, then restarts the app so the next start swaps it in */
    fun importFrom(source: Uri) {
        if (_import.value is TransferStep.Working) return
        Timber.w("TransferViewModel stub: importFrom ignored (no transfer engine yet)")
        _import.value = TransferStep.Failed("ChordWave can't read that export yet.")
    }

    /** back to the start after a failed import, so the page can be tried again */
    fun clearImport() {
        if (_import.value !is TransferStep.Working) _import.value = TransferStep.Idle
    }

    companion object {
        /** the permission that lets chordwave find local songs again, it changed name in android 13 */
        val AudioPermission: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
