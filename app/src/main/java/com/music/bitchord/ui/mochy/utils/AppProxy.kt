// Ported from Mochy Music (GPL-3.0): utils/AppProxy.kt — TRIMMED.
// Only the two pure address helpers the App Behaviour page needs to show the
// proxy status line. The full proxy engine (ProxyConfig, configFrom, apply,
// check, the OkHttp wiring) belongs to the settings/proxy worker: when their
// full AppProxy.kt lands at this same path it REPLACES this file — the
// coordinator must drop this trimmed copy, not merge both.

package com.music.bitchord.ui.mochy.utils

import java.net.InetSocketAddress

object AppProxy {
    // one pool and dispatcher for the integration clients (Discord, cloud
    // backup), so they don't each spin up their own thread pools
    internal val connectionPool = okhttp3.ConnectionPool()
    internal val dispatcher = okhttp3.Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 10
    }

    /** reads "1.2.3.4:8080", "proxy.example.com:1080" or "[2001:db8::1]:3128", skipping a pasted scheme or login in front */
    fun parseAddress(text: String): InetSocketAddress? {
        val hostPort = text.trim().substringAfter("://").substringAfterLast('@').trimEnd('/')
        val port = hostPort.substringAfterLast(':', "").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringAfter('[').substringBefore(']')
        } else {
            hostPort.substringBeforeLast(':')
        }
        if (host.isBlank()) return null
        // unresolved: the proxy's name is looked up when connecting, never here on the main thread
        return InetSocketAddress.createUnresolved(host, port)
    }

    /** the address as people write it, an ipv6 host in brackets so its port stays readable */
    fun addressLabel(address: InetSocketAddress): String =
        if (':' in address.hostString) "[${address.hostString}]:${address.port}" else "${address.hostString}:${address.port}"
}
