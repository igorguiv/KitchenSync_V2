package com.kitchensync.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

fun shopLine(s: Shop): String = "• " + s.name + (if (s.amount > 0.0) " — ${fmt(s.amount)} ${s.unit}" else "")

@Composable
fun ShopTab(vm: MainViewModel) {
    val shop by vm.shop.collectAsState()
    var newName by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<Shop?>(null) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    val ctx = LocalContext.current
    val groups = shop.groupBy { it.forDish }
    val keys = groups.keys.sortedWith(compareBy<String> { it.isNotEmpty() }.thenBy { it })
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("🛒 Покупки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                POutlined({ vm.suggest() }, Modifier.weight(1f)) { Text("Запропонувати") }
                POutlined({
                    val text = keys.joinToString("\n\n") { k ->
                        (if (k.isEmpty()) "Покупки:" else "Для страви $k:") + "\n" +
                            groups[k].orEmpty().filter { !it.done }.joinToString("\n") { shopLine(it) }
                    }
                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                    ctx.startActivity(Intent.createChooser(i, "Поділитися списком"))
                }, Modifier.weight(1f)) { Text("Поділитися") }
            }
        }
        item { PButton({ vm.moveBought() }, Modifier.fillMaxWidth()) { Text("✔ Куплене — у холодильник") } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newName, { newName = it }, placeholder = { Text("Додати в список") }, singleLine = true, shape = Bubble,
                    colors = fieldColors(), modifier = Modifier.weight(1f))
                IconButton({ if (newName.isNotBlank()) { vm.addShop(newName.trim()); newName = "" } }) { Icon(Icons.Default.AddCircle, "Додати") }
            }
        }
        if (shop.isEmpty()) item { Text("Список порожній. Додайте позицію, натисніть «Запропонувати» або позначте страву «Готуватиму» у меню.") }
        keys.forEach { k ->
            item(key = "g$k") {
                BubbleCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (k.isEmpty()) "Поповнення холодильника" else "Для страви: $k", Modifier.weight(1f),
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        IconButton({ ask = Ask("Видалити список «${if (k.isEmpty()) "Поповнення" else k}» повністю?") { vm.deleteShopList(k) } }) {
                            Icon(Icons.Default.Delete, "Видалити список")
                        }
                    }
                    groups[k].orEmpty().forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(s.done, { vm.toggleShop(s) })
                            Text(shopLine(s).removePrefix("• "), Modifier.weight(1f), textDecoration = if (s.done) TextDecoration.LineThrough else null)
                            IconButton({ edit = s }) { Icon(Icons.Default.Edit, "Редагувати") }
                            IconButton({ vm.delShop(s) }) { Icon(Icons.Default.Close, "Прибрати") }
                        }
                    }
                }
            }
        }
    }
    edit?.let { s -> NameDialog("Редагувати позицію", s.name, { edit = null }) { vm.renameShop(s, it); edit = null } }
    ask?.let { a -> ConfirmDialog(a.text, { ask = null }) { a.action(); ask = null } }
}
