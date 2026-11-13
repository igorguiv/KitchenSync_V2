package com.kitchensync.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Online recipes from TheMealDB (https://www.themealdb.com), translated to Ukrainian on the device with ML Kit. */
object Online {
    data class Raw(val name: String, val category: String, val area: String, val instructions: String, val ings: List<Pair<String, String>>)

    private const val BASE = "https://www.themealdb.com/api/json/v1/1/"
    private var enUk: Translator? = null
    private var ukEn: Translator? = null

    private val AREAS = mapOf(
        "American" to "США", "British" to "Великобританія", "Canadian" to "Канада", "Chinese" to "Китай", "Croatian" to "Хорватія",
        "Dutch" to "Нідерланди", "Egyptian" to "Єгипет", "Filipino" to "Філіппіни", "French" to "Франція", "Greek" to "Греція",
        "Indian" to "Індія", "Irish" to "Ірландія", "Italian" to "Італія", "Jamaican" to "Ямайка", "Japanese" to "Японія",
        "Kenyan" to "Кенія", "Malaysian" to "Малайзія", "Mexican" to "Мексика", "Moroccan" to "Марокко", "Polish" to "Польща",
        "Portuguese" to "Португалія", "Russian" to "Росія", "Spanish" to "Іспанія", "Thai" to "Таїланд", "Tunisian" to "Туніс",
        "Turkish" to "Туреччина", "Ukrainian" to "Україна", "Vietnamese" to "В'єтнам"
    )

    private suspend fun translator(toUk: Boolean): Translator {
        val cached = if (toUk) enUk else ukEn
        if (cached != null) return cached
        val t = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(if (toUk) TranslateLanguage.ENGLISH else TranslateLanguage.UKRAINIAN)
                .setTargetLanguage(if (toUk) TranslateLanguage.UKRAINIAN else TranslateLanguage.ENGLISH)
                .build()
        )
        t.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        if (toUk) enUk = t else ukEn = t
        return t
    }

    fun areaUk(a: String): String = AREAS[a] ?: ""

    suspend fun toUk(s: String): String = if (s.isBlank()) s else runCatching { translator(true).translate(s).await() }.getOrDefault(s)
    private suspend fun toEn(s: String): String = runCatching { translator(false).translate(s).await() }.getOrDefault(s)

    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        JSONObject(c.inputStream.bufferedReader().readText())
    }

    private fun parse(j: JSONObject): List<Raw> {
        val arr = j.optJSONArray("meals") ?: return emptyList()
        val out = ArrayList<Raw>()
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            val ings = ArrayList<Pair<String, String>>()
            for (i in 1..20) {
                val n = o.optString("strIngredient$i").trim()
                val m = o.optString("strMeasure$i").trim()
                if (n.isNotBlank() && !n.equals("null", true)) ings.add(n to (if (m.equals("null", true)) "" else m))
            }
            out.add(Raw(o.optString("strMeal"), o.optString("strCategory"), o.optString("strArea"), o.optString("strInstructions"), ings))
        }
        return out
    }

    suspend fun search(q: String): List<Raw> {
        val en = if (q.any { it.code in 0x0400..0x04FF }) toEn(q) else q
        return parse(get("search.php?s=" + URLEncoder.encode(en.trim(), "UTF-8")))
    }
    suspend fun random(): List<Raw> = parse(get("random.php"))

    private fun num(s: String): Double? {
        val t = s.trim()
        if (t.contains("/")) {
            val p = t.split("/")
            val a = p[0].toDoubleOrNull(); val b = p.getOrNull(1)?.toDoubleOrNull()
            return if (a != null && b != null && b != 0.0) a / b else null
        }
        return t.toDoubleOrNull()
    }

    /** Recipes are written for about 4 servings; amounts in g/ml are converted to "per person". */
    private fun makeIng(name: String, measure: String): Ing {
        val m = Regex("^\\s*([\\d./]+)\\s*(kg|g|ml|l|oz|lb)\\b", RegexOption.IGNORE_CASE).find(measure)
        if (m != null) {
            val v = num(m.groupValues[1])
            if (v != null) {
                val qty = when (m.groupValues[2].lowercase()) { "kg" -> v * 1000; "g" -> v; "ml" -> v; "l" -> v * 1000; "oz" -> v * 28.35; else -> v * 453.6 }
                val unit = when (m.groupValues[2].lowercase()) { "ml", "l" -> "мл"; else -> "г" }
                return Ing(name, Math.round(qty / 4 * 10) / 10.0, unit)
            }
        }
        val note = measure.replace("tbsp", "ст. л.", true).replace("tsp", "ч. л.", true).replace("cups", "склянки", true)
            .replace("cup", "склянка", true).replace("pinch", "дрібка", true).replace("handful", "жменя", true)
            .replace("slices", "скибочки", true).replace("slice", "скибочка", true)
        return Ing(name, 0.0, "", note)
    }

    private fun splitSteps(t: String): List<String> {
        val lines = t.split(Regex("[\\r\\n]+")).map { it.trim() }
            .filter { it.isNotBlank() && !Regex("(?i)^(step\\s*)?\\d+[.)]?$").matches(it) }
        val parts = if (lines.size >= 3) lines else t.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        return parts.map { it.replace(Regex("(?i)^step\\s*\\d+[.:]?\\s*"), "") }.filter { it.isNotBlank() }.take(25)
    }

    suspend fun toDish(r: Raw): Dish {
        val cat = when (r.category) { "Dessert" -> "Десерт"; "Side" -> "Гарнір"; "Starter" -> "Салат"; else -> "Друге" }
        val ings = r.ings.map { (n, m) -> makeIng(toUk(n).replaceFirstChar { it.uppercase() }, m) }
        val steps = splitSteps(r.instructions).map { toUk(it) }
        return Dish(name = toUk(r.name), category = cat, ings = ings, steps = steps, origin = AREAS[r.area] ?: "")
    }
}

@Composable
fun OnlineDialog(vm: MainViewModel, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Pair<Online.Raw, String>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val load: (suspend () -> List<Online.Raw>) -> Unit = { block ->
        scope.launch {
            busy = true; status = "Шукаю… (вперше завантажується словник перекладу, потрібен інтернет)"
            try {
                val raws = block()
                results = raws.map { it to Online.toUk(it.name) }
                status = if (raws.isEmpty()) "Нічого не знайдено" else null
            } catch (e: Exception) {
                status = "Помилка: ${e.message ?: "немає інтернету"}"
            }
            busy = false
        }
    }
    val openRecipe: (Online.Raw) -> Unit = { raw ->
        scope.launch {
            busy = true; status = "Перекладаю рецепт…"
            try {
                val d = Online.toDish(raw)
                vm.viewing = Viewing(d)
                onClose()
            } catch (e: Exception) {
                status = "Не вдалося: ${e.message ?: "помилка"}"; busy = false
            }
        }
    }
    val presets = listOf("Курка" to "chicken", "Яловичина" to "beef", "Паста" to "pasta", "Риба" to "fish",
        "Суп" to "soup", "Торт" to "cake", "Вегетаріанське" to "vegetarian")
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClose) { Icon(Icons.Default.Close, "Закрити") }
                    Text("Рецепти онлайн", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(q, { q = it }, label = { Text("Що шукаєте? (українською або англійською)") }, singleLine = true,
                    shape = Bubble, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PButton({ load { Online.search(q) } }, Modifier.weight(1f), enabled = !busy && q.isNotBlank()) { Text("Знайти") }
                    POutlined({ load { Online.random() } }, Modifier.weight(1f), enabled = !busy) { Text("🎲 Випадкова") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { (label, en) -> PChip(false, label) { q = label; load { Online.search(en) } } }
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.first.name + it.second }) { (raw, uk) ->
                        BubbleCard(onClick = { if (!busy) openRecipe(raw) }) {
                            Text(uk, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text((Online.areaUk(raw.area).takeIf { it.isNotBlank() }?.let { "🌍 $it · " } ?: "") + raw.category, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
