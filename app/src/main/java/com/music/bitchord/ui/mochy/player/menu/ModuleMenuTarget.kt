// Ported from Mochy Music (GPL-3.0): ui/player/menu/ModuleMenuRows.kt (openModuleTarget
// + fill) + ui/screens/settings/modules/ModulePageScreen.kt (modulePageRoute) +
// ui/screens/settings/devspace/DevStyle.kt (openDevUrl) — the three helpers a module
// button needs to open its target. ModuleMenuRows itself was NOT ported (the modules
// system is out of scope), but ModulePlayerStyle calls openModuleTarget, so the
// function lives here. With no module system no skin can ever be active, so this
// never fires in practice; it exists so the module style compiles verbatim.

package com.music.bitchord.ui.mochy.player.menu

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.music.bitchord.ui.mochy.models.MediaMetadata
import com.music.bitchord.ui.mochy.modules.skin.ActionTarget
import com.music.bitchord.ui.mochy.screens.search.searchRoute
import java.net.URLEncoder

/** runs a module's link, page or search with the song filled in; pages and searches go through [openRoute] */
internal fun openModuleTarget(context: Context, target: ActionTarget, song: MediaMetadata?, moduleId: String, openRoute: (String) -> Unit) {
    when (target) {
        is ActionTarget.Link -> context.openDevUrl(fill(target.template, song, encode = true))
        is ActionTarget.Page -> openRoute(modulePageRoute(moduleId, target.id))
        is ActionTarget.Search -> openRoute(searchRoute(fill(target.template, song, encode = false)))
    }
}

internal fun modulePageRoute(id: String, page: String) = "module_page/${Uri.encode(id)}/${Uri.encode(page)}"

internal fun Context.openDevUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// {title}, {artist}, {album} and {id} swapped for the song's own, made safe for a link when it goes into one; empty with no song
private fun fill(template: String, song: MediaMetadata?, encode: Boolean): String {
    fun part(value: String?) = value.orEmpty().let { if (encode) URLEncoder.encode(it, "UTF-8").replace("+", "%20") else it }
    return template
        .replace("{title}", part(song?.title))
        .replace("{artist}", part(song?.artists?.joinToString(", ") { it.name }))
        .replace("{album}", part(song?.album?.title))
        .replace("{id}", part(song?.id))
}
