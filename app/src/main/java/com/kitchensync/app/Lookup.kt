package com.kitchensync.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Found(val name: String, val category: String, val qty: Double?, val unit: String?)

/** Open Food Facts lookup (ODbL, https://world.openfoodfacts.org). */
suspend fun lookup(code: String): Found? = withContext(Dispatchers.IO) {
    runCatching {
        val c = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,product_name_uk,categories,quantity")
            .openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "KitchenSync/1.0 (Android)")
        c.connectTimeout = 8000; c.readTimeout = 8000
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        if (j.optInt("status") != 1) null else {
            val p = j.getJSONObject("product")
            val name = p.optString("product_name_uk").ifBlank { p.optString("product_name") }
            val m = Regex("([\\d.,]+)\\s*(kg|кг|ml|мл|g|г|l|л)", RegexOption.IGNORE_CASE).find(p.optString("quantity"))
            val unit = m?.let { when (it.groupValues[2].lowercase()) { "kg", "кг" -> "кг"; "g", "г" -> "г"; "ml", "мл" -> "мл"; else -> "л" } }
            Found(name, p.optString("categories"), m?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull(), unit)
        }
    }.getOrNull()
}
