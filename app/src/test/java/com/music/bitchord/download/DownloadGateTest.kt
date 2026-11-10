package com.music.bitchord.download

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mobile-data download matrix: policy × connection × origin.
 *
 * The cells that matter most are the two asymmetries. A background job
 * waits for Wi-Fi even when the listener has said mobile data is always
 * fine — the agreement was about taps, not about work nobody is
 * watching. And a tap on Wi-Fi is never asked anything, in any state.
 */
class DownloadGateTest {

    private fun decide(
        policy: CellularDownloadPolicy,
        metered: Boolean?,
        origin: DownloadOrigin,
        servableFromCache: Boolean = false,
        userApproved: Boolean = false,
    ) = DownloadGate.decide(policy, metered, origin, servableFromCache, userApproved)

    @Test
    fun `an unmetered connection proceeds in every state, from either origin`() {
        for (policy in CellularDownloadPolicy.entries) {
            for (origin in DownloadOrigin.entries) {
                assertEquals(
                    "$policy / $origin on Wi-Fi",
                    DownloadDecision.PROCEED,
                    decide(policy, metered = false, origin),
                )
            }
        }
    }

    @Test
    fun `offline proceeds in every state, to fail on the network and say so`() {
        for (policy in CellularDownloadPolicy.entries) {
            for (origin in DownloadOrigin.entries) {
                assertEquals(
                    "$policy / $origin offline",
                    DownloadDecision.PROCEED,
                    decide(policy, metered = null, origin),
                )
            }
        }
    }

    @Test
    fun `a tap on mobile data follows the policy`() {
        assertEquals(
            DownloadDecision.ASK_USER,
            decide(CellularDownloadPolicy.ASK_FIRST, metered = true, DownloadOrigin.MANUAL),
        )
        assertEquals(
            DownloadDecision.PROCEED,
            decide(CellularDownloadPolicy.ALWAYS_ALLOW, metered = true, DownloadOrigin.MANUAL),
        )
        assertEquals(
            DownloadDecision.WAIT_FOR_WIFI,
            decide(CellularDownloadPolicy.WIFI_ONLY, metered = true, DownloadOrigin.MANUAL),
        )
    }

    @Test
    fun `an answered question proceeds without changing the state it was asked under`() {
        assertEquals(
            DownloadDecision.PROCEED,
            decide(
                CellularDownloadPolicy.ASK_FIRST,
                metered = true,
                DownloadOrigin.MANUAL,
                userApproved = true,
            ),
        )
        // Approval is not a policy override: it cannot lift the hard block.
        assertEquals(
            DownloadDecision.WAIT_FOR_WIFI,
            decide(
                CellularDownloadPolicy.WIFI_ONLY,
                metered = true,
                DownloadOrigin.MANUAL,
                userApproved = true,
            ),
        )
    }

    @Test
    fun `background work waits for Wi-Fi on mobile data whatever the policy says`() {
        for (policy in CellularDownloadPolicy.entries) {
            assertEquals(
                "$policy in the background",
                DownloadDecision.WAIT_FOR_WIFI,
                decide(policy, metered = true, DownloadOrigin.BACKGROUND),
            )
        }
        // And it is never handed a question it has no window to ask.
        assertEquals(
            DownloadDecision.WAIT_FOR_WIFI,
            decide(
                CellularDownloadPolicy.ASK_FIRST,
                metered = true,
                DownloadOrigin.BACKGROUND,
                userApproved = true,
            ),
        )
    }

    @Test
    fun `a copy out of the cache spends no data and is never gated`() {
        for (policy in CellularDownloadPolicy.entries) {
            for (origin in DownloadOrigin.entries) {
                assertEquals(
                    "$policy / $origin from cache",
                    DownloadDecision.PROCEED,
                    decide(policy, metered = true, origin, servableFromCache = true),
                )
            }
        }
    }
}
