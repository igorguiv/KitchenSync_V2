package com.kitchensync.app

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        IconButton({ if (value > min) onChange(value - 1) }) { Icon(Icons.Default.Remove, "Менше") }
        AnimatedContent(value, label = "step") { Text("$it", fontWeight = FontWeight.Bold) }
        IconButton({ if (value < max) onChange(value + 1) }) { Icon(Icons.Default.Add, "Більше") }
    }
}

@Composable
fun MenuTab(vm: MainViewModel) {
    val recipes by vm.recipes.collectAsState()
    val menus by vm.menus.collectAsState()
    val cooked by vm.cooked.collectAsState()
    val inv by vm.items.collectAsState()
    var editDish by remember { mutableStateOf<Dish?>(null) }
    var saveMenu by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    val sugg = remember(inv, recipes, vm.persons) { vm.suggestions() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Шеф-генератор", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item {
                BubbleCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Stepper("Персон:", vm.persons, 1, 20) { vm.persons = it }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Stepper("Днів:", vm.days, 1, 14) { vm.days = it }
                        Spacer(Modifier.weight(1f))
                        PChip(vm.days == 7, "Тиждень") { vm.days = 7 }
                    }
                    Text("Категорії страв", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    vm.slots.forEachIndexed { idx, s ->
                        var m by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                POutlined({ m = true }) { Text(if (s.isBlank()) "Оберіть категорію ▾" else "$s ▾") }
                                DropdownMenu(m, { m = false }) { CATEGORIES.forEach { c -> DropdownMenuItem({ Text(c) }, { vm.slots[idx] = c; m = false }) } }
                            }
                            if (vm.slots.size > 1) IconButton({ vm.slots.removeAt(idx) }) { Icon(Icons.Default.Close, "Прибрати") }
                        }
                    }
                    AnimatedVisibility(vm.slots.last().isNotBlank() && vm.slots.size < CATEGORIES.size,
                        enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                        FilledTonalIconButton({ vm.slots.add("") }) { Icon(Icons.Default.Add, "Додати категорію") }
                    }
                    PButton({ vm.generate() }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = vm.slots.any { it.isNotBlank() }) { Text("Згенерувати меню") }
                }
            }
            vm.plan.forEachIndexed { day, dp ->
                item { if (vm.plan.size > 1) Text("День ${day + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                itemsIndexed(dp.dishes) { idx, d ->
                    val p = vm.forPersons(d)
                    BubbleCard(onClick = { vm.viewing = Viewing(d, day, idx) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Text(metaOf(d) + " · $p ${pers(p)}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ vm.reroll(day, idx) }) { Icon(Icons.Default.Refresh, "Інша страва") }
                            IconButton({ vm.saveDish(d) }) { Icon(Icons.Default.BookmarkAdd, "Зберегти") }
                            IconButton({ vm.askCook(d, p) }) { Icon(Icons.Default.Check, "Приготовано") }
                        }
                        PChip(d.cook, "Готуватиму 🍳") { vm.toggleCook(day, idx) }
                        d.ings.forEach { ing ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Light(if (isMissing(inv, ing, p)) Red else Green); Spacer(Modifier.width(8.dp))
                                Text(ingText(ing, p), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (vm.plan.isNotEmpty()) item { PButton({ saveMenu = true }, Modifier.fillMaxWidth()) { Text("💾 Зберегти меню") } }

            if (sugg.isNotEmpty()) {
                item { Text("Що приготувати з наявного", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
                items(sugg, key = { "sg" + it.first.name }) { (d, pct) ->
                    BubbleCard(onClick = { vm.viewing = Viewing(d) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(d.name, fontWeight = FontWeight.SemiBold); Text(metaOf(d), style = MaterialTheme.typography.bodySmall) }
                            Text("є $pct% продуктів")
                        }
                    }
                }
            }

            item { Text("Збережені меню", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
            if (menus.isEmpty()) item { Text("Поки немає збережених меню.") }
            items(menus, key = { "m" + it.id }) { m ->
                BubbleCard(onClick = { vm.loadMenu(m) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(m.name, fontWeight = FontWeight.SemiBold); Text("${m.days.size} дн. · ${m.persons} ${pers(m.persons)}", style = MaterialTheme.typography.bodySmall) }
                        IconButton({ vm.loadMenu(m) }) { Icon(Icons.Default.FolderOpen, "Відкрити") }
                        IconButton({ ask = Ask("Видалити меню «${m.name}»?") { vm.deleteMenu(m) } }) { Icon(Icons.Default.Delete, "Видалити") }
                    }
                }
            }

            item { Text("Мої страви", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
            items(recipes, key = { "r" + it.id }) { d ->
                BubbleCard(onClick = { vm.viewing = Viewing(d) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(d.name, fontWeight = FontWeight.SemiBold); Text(metaOf(d), style = MaterialTheme.typography.bodySmall) }
                        IconButton({ editDish = d }) { Icon(Icons.Default.Edit, "Редагувати") }
                        IconButton({ ask = Ask("Видалити рецепт «${d.name}»?") { vm.deleteDish(d) } }) { Icon(Icons.Default.Delete, "Видалити") }
                    }
                }
            }
            item { POutlined({ online = true }, Modifier.fillMaxWidth()) { Text("🌐 Знайти рецепт онлайн") } }
            item { POutlined({ vm.showNewDish = true }, Modifier.fillMaxWidth()) { Text("＋ Свій сімейний рецепт") } }

            item { Text("Приготовано й оцінки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
            if (cooked.isEmpty()) item { Text("Тут з'являться приготовані страви з оцінками родини.") }
            items(cooked.sortedByDescending { it.ts }.take(15), key = { "c" + it.id }) { c ->
                BubbleCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(c.dish, fontWeight = FontWeight.SemiBold); Text("готував(ла): ${c.byName}", style = MaterialTheme.typography.bodySmall) }
                        Text(if (c.ratings.isEmpty()) "чекає оцінок" else "★ ${fmt(c.ratings.values.average())} (${c.ratings.size})")
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (online) OnlineDialog(vm) { online = false }
    if (vm.showNewDish) RecipeDialog(null, { vm.showNewDish = false }) { vm.saveDish(it); vm.showNewDish = false }
    editDish?.let { d -> RecipeDialog(d, { editDish = null }) { vm.saveDish(it); editDish = null } }
    if (saveMenu) NameDialog("Назва меню", "Меню на ${vm.days} дн.", { saveMenu = false }) { vm.saveMenu(it); saveMenu = false }
    ask?.let { a -> ConfirmDialog(a.text, { ask = null }) { a.action(); ask = null } }
}

@Composable
fun RecipeScreen(vm: MainViewModel, v: Viewing) {
    val d = v.dish
    val p = vm.forPersons(d)
    val inv by vm.items.collectAsState()
    var edit by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Pattern(vm.bg, MaterialTheme.colorScheme.primary)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton({ vm.viewing = null }) { Icon(Icons.Default.ArrowBack, "Назад") }
                Text(d.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton({ edit = true }) { Icon(Icons.Default.Edit, "Редагувати") }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(metaOf(d), style = MaterialTheme.typography.bodyMedium) }
                item { BubbleCard { Stepper("Порцій на цю страву:", p, 1, 20) { vm.updateViewing(d.copy(persons = it)) } } }
                item {
                    BubbleCard {
                        Text("Продукти", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        d.ings.forEach { ing ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Light(if (isMissing(inv, ing, p)) Red else Green); Spacer(Modifier.width(8.dp))
                                Text(ingText(ing, p))
                            }
                        }
                    }
                }
                item {
                    BubbleCard {
                        Text("Приготування", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (d.steps.isEmpty()) Text("Кроків ще немає — додайте їх через ✏️.")
                        d.steps.forEachIndexed { n, s -> Text("${n + 1}. $s", modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PButton({ vm.askCook(d, p) }, Modifier.weight(1f)) { Text("Приготовано!") }
                            POutlined({ vm.saveDish(d) }, Modifier.weight(1f)) { Text("Зберегти") }
                        }
                        POutlined({ vm.checkDish(d, p); vm.toast = "Відсутні продукти додано до покупок" }, Modifier.fillMaxWidth()) { Text("🛒 Додати відсутні в покупки") }
                    }
                }
            }
        }
    }
    if (edit) RecipeDialog(d, { edit = false }) { vm.updateViewing(it); edit = false }
}
