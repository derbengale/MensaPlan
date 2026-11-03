package com.watchyourself.mensaplan.data

import android.content.Context

/**
 * Kleine Einstellungs-Ablage: Preisgruppe (Student/Bedienstet/Gast) und
 * die zuletzt im Tile angezeigte Position.
 */
class MensaSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mensa_settings", Context.MODE_PRIVATE)

    var priceCategory: PriceCategory
        get() = PriceCategory.fromName(prefs.getString(KEY_PRICE_CATEGORY, null))
        set(value) = prefs.edit().putString(KEY_PRICE_CATEGORY, value.name).apply()

    var tileDishIndex: Int
        get() = prefs.getInt(KEY_TILE_DISH, 0)
        set(value) = prefs.edit().putInt(KEY_TILE_DISH, value).apply()

    private companion object {
        const val KEY_PRICE_CATEGORY = "price_category"
        const val KEY_TILE_DISH = "tile_dish_index"
    }
}
