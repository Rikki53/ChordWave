package com.music.bitchord.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * A QR scanner door for Compose screens: returns a `launch` lambda; the
 * decoded text (or null, when the scan was cancelled or the camera
 * permission was refused) arrives at [onScanned].
 *
 * The scanner itself is ZXing's embedded capture activity — the project's
 * [QrCode] only ever *drew* codes, and joining by camera needs the other
 * half. The activity asks for the camera permission itself; a refusal
 * simply ends the scan, and every caller keeps its manual path, which is
 * the polite failure mode: nothing here is the only way in.
 */
@Composable
fun rememberQrCodeScanner(onScanned: (String?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        onScanned(result.contents)
    }
    return {
        launcher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("")
                setBeepEnabled(false)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
            },
        )
    }
}
