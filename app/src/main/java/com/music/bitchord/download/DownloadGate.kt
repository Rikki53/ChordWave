package com.music.bitchord.download

/**
 * Where the listener stands on downloads spending mobile data.
 *
 * One setting with three positions, replacing what used to be a single
 * Wi-Fi-only switch that defaulted on. That switch's failure mode was
 * silence: on mobile data a tap was refused by a row most people never
 * read, with no moment at which the app asked the question it was
 * answering on their behalf. So the default now is to ask — once, at the
 * tap, in a dialog with a "don't ask again" on it — and the two settled
 * positions are the ones a listener can only arrive at deliberately:
 * by answering that dialog with the box ticked, or by going to Settings
 * and turning the hard block on themselves.
 */
enum class CellularDownloadPolicy {

    /** Ask at the tap, every time, until told otherwise. The default. */
    ASK_FIRST,

    /** Mobile data is fair game; never ask. Reached only via "don't ask again". */
    ALWAYS_ALLOW,

    /** No downloads on a metered connection, ever. The Settings switch, on. */
    WIFI_ONLY,
}

/** Who is asking for a download, which is what decides whether asking back is possible. */
enum class DownloadOrigin {

    /** A tap in the UI: there is a window, and a person in front of it. */
    MANUAL,

    /**
     * Work nobody is watching — the downloaded-playlist sync. There is no
     * window to ask from, and a background job spending mobile data nobody
     * agreed to spend is worse than the same job waiting, so this origin
     * never gets [DownloadDecision.ASK_USER] and never proceeds on a
     * metered connection whatever the policy says.
     */
    BACKGROUND,
}

/** What a download request does next. */
enum class DownloadDecision {

    /** Fetch it on the connection in hand. */
    PROCEED,

    /** Stop and ask the listener first. Only ever handed to [DownloadOrigin.MANUAL]. */
    ASK_USER,

    /** Accept the request and hold it for an unmetered connection. */
    WAIT_FOR_WIFI,
}

/**
 * The one decision every download goes through, kept pure so the whole
 * matrix — policy × connection × origin — can be tested without a
 * network, a settings store or a window.
 *
 * The inputs are deliberately the raw facts rather than answers read out
 * of [com.music.bitchord.data.settings.AppSettings]: [metered] is the
 * active network's own report, where null means there is no active
 * network at all. Offline is let through, as it always has been: a
 * download with nothing to download over fails on the network and says
 * so, which is true, where refusing it here would blame a data setting
 * for an outage.
 */
object DownloadGate {

    fun decide(
        policy: CellularDownloadPolicy,
        metered: Boolean?,
        origin: DownloadOrigin,
        servableFromCache: Boolean = false,
        userApproved: Boolean = false,
    ): DownloadDecision {
        // A copy straight out of the song cache spends no data at all, so
        // no position of the policy has anything to say about it.
        if (servableFromCache) return DownloadDecision.PROCEED
        // Unmetered, or no network to meter: the connection is not the
        // problem either way, and on Wi-Fi no state ever asks.
        if (metered != true) return DownloadDecision.PROCEED
        return when (origin) {
            DownloadOrigin.BACKGROUND -> DownloadDecision.WAIT_FOR_WIFI
            DownloadOrigin.MANUAL -> when (policy) {
                CellularDownloadPolicy.ALWAYS_ALLOW -> DownloadDecision.PROCEED
                CellularDownloadPolicy.WIFI_ONLY -> DownloadDecision.WAIT_FOR_WIFI
                CellularDownloadPolicy.ASK_FIRST ->
                    if (userApproved) DownloadDecision.PROCEED else DownloadDecision.ASK_USER
            }
        }
    }
}
