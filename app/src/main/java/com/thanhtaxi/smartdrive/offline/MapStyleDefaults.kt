package com.thanhtaxi.smartdrive.offline

/**
 * Migrate the original country-only MapLibre demo style without clearing
 * user preferences or replacing other custom URL choices.
 */
object MapStyleDefaults {
    const val STREET_STYLE = "https://tiles.openfreemap.org/styles/liberty"
    private const val LEGACY_WORLD_DEMO = "https://demotiles.maplibre.org/style.json"

    fun effective(savedStyle: String?): String =
        if (savedStyle.isNullOrBlank() || savedStyle == LEGACY_WORLD_DEMO) STREET_STYLE
        else savedStyle
}
