package com.kitchensync.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        if (savedInstanceState == null) vm.pendingAction = intent?.getStringExtra("go")
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.pendingAction = intent.getStringExtra("go")
    }
}

@Composable
fun App() {
    val vm: MainViewModel = viewModel()
    val ctx = LocalContext.current
    val family = remember { loadFont(ctx) }
    val pal = PALS[vm.theme.coerceIn(0, PALS.size - 1)]
    val bg by animateColorAsState(pal.bg, tween(400), label = "bg")
    val scheme = lightColorScheme(primary = pal.accent, background = bg, surface = pal.card,
        secondaryContainer = pal.accent.copy(alpha = 0.28f), surfaceVariant = pal.accent.copy(alpha = 0.12f))
    MaterialTheme(colorScheme = scheme, typography = typo(family),
        shapes = Shapes(small = RoundedCornerShape(14.dp), medium = Bubble, large = Bubble)) {
        val code by vm.code.collectAsState()
        val me by vm.me.collectAsState()
        Box(Modifier.fillMaxSize().background(bg)) {
            Pattern(vm.bg, pal.accent)
            when {
                code.isEmpty() -> Onboarding(vm)
                me?.approved != true -> Waiting(vm)
                else -> Main(vm)
            }
        }
    }
}

@Composable
fun Onboarding(vm: MainViewModel) {
    var join by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Мій холодильник 🍽", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Розумний холодильник і сімейне меню")
        Spacer(Modifier.height(24.dp))
        BubbleCard {
            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("Ваше ім'я") }, shape = Bubble, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            PButton({ vm.createFridge() }, Modifier.fillMaxWidth(), enabled = vm.name.isNotBlank() && !vm.busy) { Text(if (vm.busy) "Зачекайте…" else "Створити новий холодильник") }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(join, { join = it }, label = { Text("Код, напр. KITCHEN-7749") }, shape = Bubble, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            POutlined({ vm.joinFridge(join) }, Modifier.fillMaxWidth(), enabled = vm.name.isNotBlank() && join.isNotBlank() && !vm.busy) { Text("Приєднатися до існуючого") }
            vm.error?.let { Text(it, color = Red, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
fun Waiting(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Очікуємо підтвердження власника холодильника…")
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { vm.leave() }) { Text("Скасувати") }
    }
}

@Composable
fun Main(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val cooked by vm.cooked.collectAsState()
    val members by vm.members.collectAsState()
    val inv by vm.items.collectAsState()
    val snack = remember { SnackbarHostState() }
    var last by remember { mutableStateOf<Viewing?>(null) }
    if (vm.viewing != null) last = vm.viewing
    BackHandler(vm.viewing != null) { vm.viewing = null }

    val pending = cooked.filter { it.byUid != vm.uid && !it.ratings.containsKey(vm.uid) }.maxByOrNull { it.ts }
    val pend = members.filter { !it.approved }.joinToString { it.name }
    val exp = inv.filter { it.expiry != 0L && it.expiry - MainViewModel.today() <= 2 }
    val expKey = exp.joinToString { it.name }
    LaunchedEffect(pend) { if (pend.isNotEmpty()) Notify.show(ctx, 1, "Новий користувач", "$pend хоче приєднатися до холодильника") }
    LaunchedEffect(pending?.id) { pending?.let { Notify.show(ctx, 2, "${it.byName} приготував(ла): ${it.dish}", "Оцініть страву ⭐") } }
    LaunchedEffect(expKey) { if (expKey.isNotEmpty()) Notify.show(ctx, 3, "Скоро закінчується термін", expKey) }
    LaunchedEffect(vm.toast) { vm.toast?.let { snack.showSnackbar(it); vm.toast = null } }
    LaunchedEffect(vm.pendingAction) {
        when (vm.pendingAction) {
            "add" -> { tab = 0; vm.showAdd = true }
            "shop" -> { tab = 1 }
            "menu" -> { tab = 2 }
            "newdish" -> { tab = 2; vm.showNewDish = true }
        }
        vm.pendingAction = null
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
            vm.syncError?.let { e ->
                Text("⚠ Немає синхронізації з хмарою: $e", color = Red, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().background(Red.copy(alpha = 0.12f)).statusBarsPadding().padding(8.dp))
            }
        }, snackbarHost = { SnackbarHost(snack) }, bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                listOf("Холодильник" to Icons.Default.Kitchen, "Покупки" to Icons.Default.ShoppingCart,
                    "Меню" to Icons.Default.RestaurantMenu, "Про мене" to Icons.Default.Person).forEachIndexed { i, (l, ic) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, l) }, label = { Text(l, style = MaterialTheme.typography.labelSmall) })
                }
            }
        }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                AnimatedContent(tab, label = "tabs", transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 8 * dir } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 8 * dir } + fadeOut(tween(150)))
                }) { t -> when (t) { 0 -> FridgeTab(vm); 1 -> ShopTab(vm); 2 -> MenuTab(vm); else -> ProfileTab(vm) } }
            }
        }
        AnimatedVisibility(vm.viewing != null,
            enter = slideInVertically(tween(300)) { it / 6 } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(250)) { it / 6 } + fadeOut(tween(200))) {
            last?.let { RecipeScreen(vm, it.copy(dish = vm.viewing?.dish ?: it.dish)) }
        }
    }

    vm.cookAsk?.let { a ->
        AlertDialog(onDismissRequest = { vm.cookAsk = null }, title = { Text("Приготовано: ${a.dish.name}") },
            text = { Text("Списати використані продукти з холодильника?") },
            confirmButton = { PButton({ vm.finishCook(true) }) { Text("Так, списати") } },
            dismissButton = { TextButton({ vm.finishCook(false) }) { Text("Ні, лише відмітити") } })
    }

    if (pending != null) {
        var stars by remember(pending.id) { mutableIntStateOf(5) }
        AlertDialog(onDismissRequest = {}, title = { Text("${pending.byName} приготував(ла): ${pending.dish}") },
            text = {
                Column {
                    Text("Оцініть страву:")
                    Row {
                        (1..5).forEach { s ->
                            val sc by animateFloatAsState(if (s <= stars) 1.2f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "star")
                            IconButton({ stars = s }) {
                                Icon(if (s <= stars) Icons.Default.Star else Icons.Default.StarBorder, null, tint = Yellow,
                                    modifier = Modifier.graphicsLayer { scaleX = sc; scaleY = sc })
                            }
                        }
                    }
                }
            },
            confirmButton = { PButton({ vm.rate(pending, stars) }) { Text("Оцінити") } })
    }
}

@Composable
fun ProfileTab(vm: MainViewModel) {
    val code by vm.code.collectAsState()
    val members by vm.members.collectAsState()
    val ctx = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Про мене", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            BubbleCard {
                Text(vm.name, style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Холодильник: $code", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    IconButton({
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", code))
                        vm.toast = "Код скопійовано"
                    }) { Icon(Icons.Default.ContentCopy, "Копіювати") }
                }
                val ok = members.filter { it.approved }
                if (ok.isNotEmpty()) Text("Родина: " + ok.joinToString { it.name }, style = MaterialTheme.typography.bodySmall)
            }
        }
        val pending = members.filter { !it.approved }
        if (pending.isNotEmpty()) item { Text("Запити на приєднання", fontWeight = FontWeight.Bold) }
        items(pending, key = { it.uid }) { m ->
            BubbleCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${m.name} хоче приєднатися", Modifier.weight(1f))
                    IconButton({ vm.decide(m, true) }) { Icon(Icons.Default.Check, "Підтвердити", tint = Green) }
                    IconButton({ vm.decide(m, false) }) { Icon(Icons.Default.Close, "Відхилити", tint = Red) }
                }
            }
        }
        item {
            BubbleCard {
                Text("Колірна тема", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PALS.forEachIndexed { i, p -> PChip(vm.theme == i, p.name) { vm.chooseTheme(i) } }
                }
                Text("Фонові малюнки", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BG_NAMES.forEachIndexed { i, n -> PChip(vm.bg == i, n) { vm.chooseBg(i) } }
                }
            }
        }
        item {
            BubbleCard {
                Text("Віджети", fontWeight = FontWeight.Bold)
                Text("Утримуйте палець на головному екрані → Віджети → «Мій холодильник»: швидкі кнопки (додати продукт, покупки, меню, нова страва) і список покупок.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { POutlined({ vm.leave() }, Modifier.fillMaxWidth()) { Text("Вийти / змінити ідентифікатор") } }
    }
}
