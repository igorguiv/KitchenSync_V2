package com.kitchensync.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun rank(c: androidx.compose.ui.graphics.Color) = if (c == Red) 2 else if (c == Yellow) 1 else 0
fun expText(i: Item): String {
    if (i.expiry == 0L) return ""
    val d = i.expiry - MainViewModel.today()
    return " · до " + LocalDate.ofEpochDay(i.expiry).format(DateTimeFormatter.ofPattern("dd.MM")) + if (d < 0) " (прострочено)" else " ($d дн.)"
}

@Composable
fun FridgeTab(vm: MainViewModel) {
    val inv by vm.items.collectAsState()
    var edit by remember { mutableStateOf<Item?>(null) }
    var emptyAsk by remember { mutableStateOf<Item?>(null) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }
    val today = MainViewModel.today()
    val expired = inv.filter { it.expiry != 0L && it.expiry < today }
    val soon = inv.filter { val d = it.expiry - today; it.expiry != 0L && d >= 0 && d <= 3 }
    val shown = inv.filter { (cat.isEmpty() || it.category == cat) && it.name.contains(query, true) }
        .sortedWith(compareByDescending<Item> { maxOf(rank(qtyColor(it)), rank(expColor(it))) }.thenBy { it.name.lowercase() })
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Холодильник", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            if (expired.isNotEmpty() || soon.isNotEmpty()) item {
                BubbleCard {
                    if (expired.isNotEmpty()) Text("⛔ Прострочено: " + expired.joinToString { it.name })
                    if (soon.isNotEmpty()) Text("⏳ Скоро закінчиться термін: " + soon.joinToString { it.name })
                }
            }
            item {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Пошук продукту") }, singleLine = true, shape = Bubble,
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) })
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PChip(cat.isEmpty(), "Усі") { cat = "" }
                    ITEM_CATS.forEach { c -> PChip(cat == c, c) { cat = if (cat == c) "" else c } }
                }
            }
            if (shown.isEmpty()) item { Text("Поки порожньо — додайте продукт кнопкою + або скануванням штрихкоду.") }
            items(shown, key = { it.id }) { i ->
                BubbleCard(onClick = { edit = i }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(i.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text(i.category + (if (i.noQty) "" else " · ${fmt(i.qty)} ${i.unit}") + expText(i), style = MaterialTheme.typography.bodySmall)
                        }
                        Light(qtyColor(i)); Spacer(Modifier.width(6.dp)); Light(expColor(i))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!i.noQty) {
                            IconButton({ vm.changeQty(i, -stepFor(i.unit)) { emptyAsk = it } }) { Icon(Icons.Default.Remove, "Менше") }
                            IconButton({ vm.changeQty(i, stepFor(i.unit)) {} }) { Icon(Icons.Default.Add, "Більше") }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton({ edit = i }) { Icon(Icons.Default.Edit, "Редагувати") }
                        IconButton({ ask = Ask("Видалити «${i.name}» з холодильника?") { vm.deleteItem(i) } }) { Icon(Icons.Default.Close, "Видалити") }
                    }
                }
            }
        }
        FloatingActionButton({ vm.showAdd = true }, Modifier.align(Alignment.BottomEnd).padding(16.dp), shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)) { Icon(Icons.Default.Add, "Додати") }
    }
    if (vm.showAdd) AddItemDialog(null, { vm.showAdd = false }) { vm.saveItem(it); vm.showAdd = false }
    edit?.let { AddItemDialog(it, { edit = null }) { n -> vm.saveItem(n); edit = null } }
    ask?.let { a -> ConfirmDialog(a.text, { ask = null }) { a.action(); ask = null } }
    emptyAsk?.let { i ->
        AlertDialog(onDismissRequest = { emptyAsk = null }, title = { Text("${i.name} закінчився") }, text = { Text("Додати до списку покупок?") },
            confirmButton = { PButton({ vm.addShop(i.name); emptyAsk = null }) { Text("Так") } },
            dismissButton = { TextButton({ emptyAsk = null }) { Text("Ні") } })
    }
}
