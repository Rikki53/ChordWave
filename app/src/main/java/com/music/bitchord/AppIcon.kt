package com.music.bitchord

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The home-screen icon. It is an `<activity-alias>` in the manifest carrying
 * the launcher intent filter (see AndroidManifest).
 *
 * Batch 23 rebuilt the alternative icons in Mochy's style with ChordWave's
 * own ribbon: two families (Mark = the mint ribbon, Mono = the white ribbon,
 * dark on pale backgrounds), each with ready-made backgrounds (white, black,
 * material, transparent) and twelve custom colours. The Default icon is
 * unchanged: the mint ribbon on #101116.
 */
enum class AppIcon(val alias: String, val iconRes: Int, val labelRes: Int, val previewRes: Int? = null) {
    Default("LauncherDefault", R.mipmap.ic_launcher, R.string.app_icon_default),
    MarkWhite("LauncherMarkWhite", R.mipmap.app_icon_mark_white, R.string.app_icon_white, R.drawable.app_icon_preview_mark_white),
    MarkBlack("LauncherMarkBlack", R.mipmap.app_icon_mark_black, R.string.app_icon_black, R.drawable.app_icon_preview_mark_black),
    MarkMaterial("LauncherMarkMaterial", R.mipmap.app_icon_mark_material, R.string.app_icon_material, R.drawable.app_icon_preview_mark_material),
    MarkTransparent("LauncherMarkTransparent", R.mipmap.app_icon_mark_transparent, R.string.app_icon_transparent, R.drawable.app_icon_preview_mark_transparent),
    MarkRed("LauncherMarkRed", R.mipmap.app_icon_mark_red, R.string.app_icon_color_red, R.drawable.app_icon_preview_mark_red),
    MarkOrange("LauncherMarkOrange", R.mipmap.app_icon_mark_orange, R.string.app_icon_color_orange, R.drawable.app_icon_preview_mark_orange),
    MarkYellow("LauncherMarkYellow", R.mipmap.app_icon_mark_yellow, R.string.app_icon_color_yellow, R.drawable.app_icon_preview_mark_yellow),
    MarkGreen("LauncherMarkGreen", R.mipmap.app_icon_mark_green, R.string.app_icon_color_green, R.drawable.app_icon_preview_mark_green),
    MarkTeal("LauncherMarkTeal", R.mipmap.app_icon_mark_teal, R.string.app_icon_color_teal, R.drawable.app_icon_preview_mark_teal),
    MarkSky("LauncherMarkSky", R.mipmap.app_icon_mark_sky, R.string.app_icon_color_sky, R.drawable.app_icon_preview_mark_sky),
    MarkBlue("LauncherMarkBlue", R.mipmap.app_icon_mark_blue, R.string.app_icon_color_blue, R.drawable.app_icon_preview_mark_blue),
    MarkIndigo("LauncherMarkIndigo", R.mipmap.app_icon_mark_indigo, R.string.app_icon_color_indigo, R.drawable.app_icon_preview_mark_indigo),
    MarkPurple("LauncherMarkPurple", R.mipmap.app_icon_mark_purple, R.string.app_icon_color_purple, R.drawable.app_icon_preview_mark_purple),
    MarkPink("LauncherMarkPink", R.mipmap.app_icon_mark_pink, R.string.app_icon_color_pink, R.drawable.app_icon_preview_mark_pink),
    MarkGraphite("LauncherMarkGraphite", R.mipmap.app_icon_mark_graphite, R.string.app_icon_color_graphite, R.drawable.app_icon_preview_mark_graphite),
    MarkCream("LauncherMarkCream", R.mipmap.app_icon_mark_cream, R.string.app_icon_color_cream, R.drawable.app_icon_preview_mark_cream),
    MonoDark("LauncherMonoDark", R.mipmap.app_icon_mono_dark, R.string.app_icon_dark, R.drawable.app_icon_preview_mono_dark),
    MonoWhite("LauncherMonoWhite", R.mipmap.app_icon_mono_white, R.string.app_icon_white, R.drawable.app_icon_preview_mono_white),
    MonoBlack("LauncherMonoBlack", R.mipmap.app_icon_mono_black, R.string.app_icon_black, R.drawable.app_icon_preview_mono_black),
    MonoMaterial("LauncherMonoMaterial", R.mipmap.app_icon_mono_material, R.string.app_icon_material, R.drawable.app_icon_preview_mono_material),
    MonoTransparent("LauncherMonoTransparent", R.mipmap.app_icon_mono_transparent, R.string.app_icon_transparent, R.drawable.app_icon_preview_mono_transparent),
    MonoRed("LauncherMonoRed", R.mipmap.app_icon_mono_red, R.string.app_icon_color_red, R.drawable.app_icon_preview_mono_red),
    MonoOrange("LauncherMonoOrange", R.mipmap.app_icon_mono_orange, R.string.app_icon_color_orange, R.drawable.app_icon_preview_mono_orange),
    MonoYellow("LauncherMonoYellow", R.mipmap.app_icon_mono_yellow, R.string.app_icon_color_yellow, R.drawable.app_icon_preview_mono_yellow),
    MonoGreen("LauncherMonoGreen", R.mipmap.app_icon_mono_green, R.string.app_icon_color_green, R.drawable.app_icon_preview_mono_green),
    MonoTeal("LauncherMonoTeal", R.mipmap.app_icon_mono_teal, R.string.app_icon_color_teal, R.drawable.app_icon_preview_mono_teal),
    MonoSky("LauncherMonoSky", R.mipmap.app_icon_mono_sky, R.string.app_icon_color_sky, R.drawable.app_icon_preview_mono_sky),
    MonoBlue("LauncherMonoBlue", R.mipmap.app_icon_mono_blue, R.string.app_icon_color_blue, R.drawable.app_icon_preview_mono_blue),
    MonoIndigo("LauncherMonoIndigo", R.mipmap.app_icon_mono_indigo, R.string.app_icon_color_indigo, R.drawable.app_icon_preview_mono_indigo),
    MonoPurple("LauncherMonoPurple", R.mipmap.app_icon_mono_purple, R.string.app_icon_color_purple, R.drawable.app_icon_preview_mono_purple),
    MonoPink("LauncherMonoPink", R.mipmap.app_icon_mono_pink, R.string.app_icon_color_pink, R.drawable.app_icon_preview_mono_pink),
    MonoGraphite("LauncherMonoGraphite", R.mipmap.app_icon_mono_graphite, R.string.app_icon_color_graphite, R.drawable.app_icon_preview_mono_graphite),
    MonoCream("LauncherMonoCream", R.mipmap.app_icon_mono_cream, R.string.app_icon_color_cream, R.drawable.app_icon_preview_mono_cream),;

    private fun component(context: Context) =
        ComponentName(context.packageName, "com.music.bitchord.$alias")

    companion object {
        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull {
                pm.getComponentEnabledSetting(it.component(context)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: Default
        }

        /** Enables [icon] before disabling the rest so there is never a moment with no launcher entry. */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
