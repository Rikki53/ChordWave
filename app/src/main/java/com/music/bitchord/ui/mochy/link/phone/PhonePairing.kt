// Ported from Mochy Music (GPL-3.0): link/phone/PhonePairing.kt
// PhonePairing.kt

package com.music.bitchord.ui.mochy.link.phone

import android.content.Context
import com.music.bitchord.ui.mochy.link.DeviceKind
import com.music.bitchord.ui.mochy.link.LinkIdentity
import com.music.bitchord.ui.mochy.link.LinkPairingDoor
import com.music.bitchord.ui.mochy.link.LinkResponse
import com.music.bitchord.ui.mochy.link.PairingCode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

// while the add device sheet is open, this phone shows a code too, so another phone or tablet can pair with it
class PhonePairing(
    private val context: Context,
    private val host: PhoneLinkHost,
) {
    private val door = LinkPairingDoor(context, "PhonePair", ::accept)

    val code: StateFlow<PairingCode?> = door.code

    private val _paired = MutableSharedFlow<PairedDevice>(extraBufferCapacity = 1)

    // someone typed this phone's code and is now paired with it
    val paired: SharedFlow<PairedDevice> = _paired.asSharedFlow()

    fun open() = door.open()

    fun close() = door.close()

    fun advertiseAgain() = door.advertiseAgain()

    private fun accept(body: JSONObject, remoteHost: String, code: String): LinkResponse {
        val accepted = host.acceptPairing(body, remoteHost, code).getOrElse {
            return when ((it as? PairException)?.reason) {
                PairException.Reason.Full -> LinkResponse.status(409, PhoneLinkHost.FullReply)
                PairException.Reason.Outdated -> LinkResponse.status(426, "update ChordWave on the other device")
                else -> LinkResponse.status(400)
            }
        }
        _paired.tryEmit(accepted.device)
        return LinkResponse.json(
            JSONObject()
                .put("tvId", LinkIdentity.id(context))
                .put("tvName", LinkIdentity.name(context))
                .put("kind", DeviceKind.of(context).wire)
                .put("port", host.port)
                .put("pub", accepted.publicKey)
                .toString(),
        )
    }
}
