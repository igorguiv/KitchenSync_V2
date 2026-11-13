package com.kitchensync.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

fun guessUnit(n: String): String? {
    val s = n.lowercase()
    return when {
        listOf("молок", "сік", "олі", "кефір", "йогурт", "вода").any { s.contains(it) } -> "л"
        listOf("яйц", "хліб", "цибул", "огір", "помідор", "лимон", "яблук").any { s.contains(it) } -> "шт"
        listOf("борошн", "цукор", "сіль", "перець", "спец").any { s.contains(it) } -> "г"
        listOf("м'яс", "курк", "сир", "карто", "морк", "буряк", "капуст", "рис", "гречк").any { s.contains(it) } -> "кг"
        else -> null
    }
}

fun guessCat(c: String): String {
    val s = c.lowercase()
    return when {
        listOf("milk", "dairy", "cheese", "молоч", "сир").any { s.contains(it) } -> "Молочне"
        listOf("meat", "м'яс", "poultry", "sausage").any { s.contains(it) } -> "М'ясо"
        listOf("vegetable", "овоч").any { s.contains(it) } -> "Овочі"
        listOf("fruit", "фрукт").any { s.contains(it) } -> "Фрукти"
        listOf("beverage", "drink", "water", "juice", "напо").any { s.contains(it) } -> "Напої"
        listOf("cereal", "rice", "pasta", "flour", "крупи").any { s.contains(it) } -> "Крупи й борошно"
        else -> "Інше"
    }
}

@Composable
fun NameDialog(title: String, initial: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onClose, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true, shape = Bubble) },
        confirmButton = { PButton({ onSave(t.trim()) }, enabled = t.isNotBlank()) { Text("Зберегти") } },
        dismissButton = { TextButton(onClose) { Text("Скасувати") } })
}

@Composable
fun AddItemDialog(initial: Item?, onClose: () -> Unit, onSave: (Item) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var cat by remember { mutableStateOf(initial?.category?.ifBlank { null } ?: ITEM_CATS.last()) }
    var qty by remember { mutableStateOf(initial?.let { fmt(it.qty) } ?: "1") }
    var days by remember { mutableStateOf(initial?.takeIf { it.expiry != 0L }?.let { (it.expiry - MainViewModel.today()).toString() } ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "шт") }
    var noQty by remember { mutableStateOf(initial?.noQty ?: false) }
    var scan by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var uMenu by remember { mutableStateOf(false) }
    var cMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onClose, title = { Text(if (initial == null) "Новий продукт" else "Редагувати продукт") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                POutlined({ scan = true }, modifier = Modifier.fillMaxWidth()) { Text("📷 Сканувати штрихкод") }
                msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(name, { name = it; if (initial == null) guessUnit(it)?.let { u -> unit = u } }, label = { Text("Назва") }, singleLine = true, shape = Bubble)
                Box { POutlined({ cMenu = true }) { Text("Категорія: $cat") }
                    DropdownMenu(cMenu, { cMenu = false }) { ITEM_CATS.forEach { c -> DropdownMenuItem({ Text(c) }, { cat = c; cMenu = false }) } } }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(noQty, { noQty = it }); Spacer(Modifier.width(8.dp)); Text("Без обліку кількості") }
                if (!noQty) Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(qty, { qty = it }, label = { Text("К-сть") }, singleLine = true, shape = Bubble, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Box { POutlined({ uMenu = true }) { Text(unit) }
                        DropdownMenu(uMenu, { uMenu = false }) { UNITS.forEach { u -> DropdownMenuItem({ Text(u) }, { unit = u; uMenu = false }) } } }
                }
                OutlinedTextField(days, { days = it }, label = { Text("Придатний ще, днів (необов'язково)") }, singleLine = true, shape = Bubble)
            }
        },
        confirmButton = {
            PButton(enabled = name.isNotBlank(), onClick = {
                onSave(Item(id = initial?.id ?: "", name = name.trim(), category = cat, qty = qty.replace(',', '.').toDoubleOrNull() ?: 1.0, unit = unit, noQty = noQty,
                    expiry = days.toLongOrNull()?.let { MainViewModel.today() + it } ?: 0L,
                    low = if (unit == "г" || unit == "мл") 100.0 else 1.0))
            }) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClose) { Text("Скасувати") } })
    if (scan) Dialog({ scan = false }, DialogProperties(usePlatformDefaultWidth = false)) {
        BarcodeScanner(onCode = { code ->
            scan = false; msg = "Шукаю $code…"
            scope.launch {
                val f = lookup(code)
                if (f == null) msg = "Не знайдено в базі ($code) — введіть вручну" else {
                    if (f.name.isNotBlank()) name = f.name
                    f.qty?.let { qty = fmt(it) }
                    f.unit?.let { unit = it }
                    cat = guessCat(f.category); msg = "Знайдено: ${f.name}"
                }
            }
        }, onClose = { scan = false })
    }
}

@Composable
fun RecipeDialog(initial: Dish?, onClose: () -> Unit, onSave: (Dish) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: "Сімейні") }
    var mins by remember { mutableStateOf(initial?.let { timeOf(it) }?.takeIf { it > 0 }?.toString() ?: "") }
    var ings by remember {
        mutableStateOf(initial?.ings?.joinToString("\n") {
            if (it.perPerson > 0.0) "${it.name}; ${fmt(it.perPerson)}; ${it.unit}" else "${it.name}; ${it.note}"
        } ?: "")
    }
    var steps by remember { mutableStateOf(initial?.steps?.joinToString("\n") ?: "") }
    var m by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, title = { Text(if (initial == null) "Свій рецепт" else "Редагувати страву") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Назва") }, shape = Bubble)
                Box { POutlined({ m = true }) { Text("Категорія: $cat") }
                    DropdownMenu(m, { m = false }) { CATEGORIES.forEach { c -> DropdownMenuItem({ Text(c) }, { cat = c; m = false }) } } }
                OutlinedTextField(mins, { mins = it }, label = { Text("Час приготування, хв") }, singleLine = true, shape = Bubble)
                OutlinedTextField(ings, { ings = it }, minLines = 3, shape = Bubble, label = { Text("Продукти на 1 особу: назва; к-сть; од. (кожен з нового рядка)") })
                OutlinedTextField(steps, { steps = it }, minLines = 3, shape = Bubble, label = { Text("Кроки (кожен з нового рядка)") })
            }
        },
        confirmButton = {
            PButton(enabled = name.isNotBlank(), onClick = {
                val list = ings.lines().mapNotNull { l ->
                    val p = l.split(";").map { it.trim() }
                    if (p[0].isBlank()) null else {
                        val num = p.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
                        Ing(p[0], num ?: 0.0, if (num == null) "" else (p.getOrNull(2)?.ifBlank { null } ?: "шт"), if (num == null) p.getOrNull(1).orEmpty() else "")
                    }
                }
                onSave((initial ?: Dish()).copy(name = name.trim(), category = cat, ings = list,
                    steps = steps.lines().filter { it.isNotBlank() }, minutes = mins.toIntOrNull() ?: 0))
            }) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClose) { Text("Скасувати") } })
}
