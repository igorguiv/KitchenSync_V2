package com.kitchensync.app

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.util.UUID

data class Viewing(val dish: Dish, val day: Int = -1, val idx: Int = -1)
data class CookAsk(val dish: Dish, val persons: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("ks", 0)
    private val repo = Repo()

    val uid: String = prefs.getString("uid", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("uid", it).apply() }
    var name by mutableStateOf(prefs.getString("name", "") ?: "")
    var theme by mutableIntStateOf(prefs.getInt("theme", 0))
    var bg by mutableIntStateOf(prefs.getInt("bg", 3))
    var error by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    private val _code = MutableStateFlow(prefs.getString("code", "") ?: "")
    val code: StateFlow<String> = _code

    var syncError by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var pendingAction by mutableStateOf<String?>(null)
    var showAdd by mutableStateOf(false)
    var showNewDish by mutableStateOf(false)
    var cookAsk by mutableStateOf<CookAsk?>(null)

    private fun <T : Any> stream(n: String, c: Class<T>): StateFlow<List<T>> =
        _code.flatMapLatest { if (it.isEmpty()) flowOf(emptyList<T>()) else repo.listen(it, n, c) { st -> syncError = st } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<T>())

    val me = _code.flatMapLatest { if (it.isEmpty()) flowOf(null) else repo.member(it, uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val members = stream("members", Member::class.java)
    val items = stream("items", Item::class.java)
    val shop = stream("shopping", Shop::class.java)
    val cooked = stream("cooked", Cooked::class.java)
    val recipes = stream("recipes", Dish::class.java)
    val menus = stream("menus", SavedMenu::class.java)

    init {
        viewModelScope.launch { shop.collect { runCatching { Widgets.pushShop(getApplication<Application>(), it) } } }
    }

    var persons by mutableIntStateOf(2)
    var days by mutableIntStateOf(1)
    val slots = mutableStateListOf("")
    var plan by mutableStateOf<List<DayPlan>>(emptyList())
    var viewing by mutableStateOf<Viewing?>(null)

    private fun saveCode(c: String) { prefs.edit().putString("code", c).putString("name", name).apply(); _code.value = c }
    fun chooseTheme(i: Int) { theme = i; prefs.edit().putInt("theme", i).apply() }
    fun chooseBg(i: Int) { bg = i; prefs.edit().putInt("bg", i).apply() }

    private fun friendly(t: Throwable) =
        if (t is TimeoutCancellationException) "Немає відповіді від хмари. Перевірте інтернет і що в Firebase створено базу Firestore та опубліковано правила (README, кроки 2–3)."
        else (t.message ?: "Невідома помилка")

    fun createFridge() = viewModelScope.launch {
        busy = true; error = null
        val c = "KITCHEN-" + (1000..9999).random()
        runCatching { withTimeout(10_000) { repo.create(c, uid, name) } }
            .onSuccess { saveCode(c) }.onFailure { error = friendly(it) }
        busy = false
    }
    fun joinFridge(input: String) = viewModelScope.launch {
        busy = true; error = null
        val c = input.trim().uppercase()
        runCatching { withTimeout(10_000) { repo.join(c, uid, name) } }
            .onSuccess { ok -> if (ok) saveCode(c) else error = "Холодильник не знайдено" }
            .onFailure { error = friendly(it) }
        busy = false
    }
    fun leave() = viewModelScope.launch {
        val c = _code.value
        if (c.isNotEmpty()) runCatching { repo.leave(c, uid) }
        plan = emptyList(); viewing = null
        saveCode("")
    }
    fun decide(m: Member, ok: Boolean) = viewModelScope.launch { repo.setApproved(_code.value, m.uid, ok) }

    // fridge
    fun saveItem(i: Item) = viewModelScope.launch {
        val id = i.id.ifEmpty { UUID.randomUUID().toString() }; repo.put(_code.value, "items", id, i.copy(id = id))
    }
    fun changeQty(i: Item, delta: Double, onEmpty: (Item) -> Unit) = viewModelScope.launch {
        val q = (i.qty + delta).coerceAtLeast(0.0)
        repo.put(_code.value, "items", i.id, i.copy(qty = q))
        if (q <= 0.0 && !i.noQty) onEmpty(i)
    }
    fun deleteItem(i: Item) = viewModelScope.launch { repo.del(_code.value, "items", i.id) }

    // shopping
    fun addShop(name: String, forDish: String = "", amount: Double = 0.0, unit: String = "") = viewModelScope.launch {
        if (shop.value.any { it.name.equals(name, true) && it.forDish == forDish }) return@launch
        val id = UUID.randomUUID().toString()
        repo.put(_code.value, "shopping", id, Shop(id, name, forDish, false, amount, unit))
    }
    fun renameShop(s: Shop, n: String) = viewModelScope.launch { repo.put(_code.value, "shopping", s.id, s.copy(name = n)) }
    fun toggleShop(s: Shop) = viewModelScope.launch { repo.put(_code.value, "shopping", s.id, s.copy(done = !s.done)) }
    fun delShop(s: Shop) = viewModelScope.launch { repo.del(_code.value, "shopping", s.id) }
    fun deleteShopList(forDish: String) { shop.value.filter { it.forDish == forDish }.forEach { delShop(it) } }
    fun suggest() {
        val need = items.value.filter { !it.noQty && (it.qty <= it.low || (it.expiry != 0L && it.expiry < today())) }
            .filter { i -> shop.value.none { it.name.equals(i.name, true) && it.forDish.isEmpty() } }
        need.forEach { addShop(it.name) }
        toast = if (need.isEmpty()) "Нічого додавати" else "Додано до списку: ${need.size}"
    }
    fun moveBought() {
        val done = shop.value.filter { it.done }
        if (done.isEmpty()) { toast = "Відмітьте куплені продукти галочкою"; return }
        done.groupBy { it.name.trim().lowercase() }.values.forEach { g ->
            val s = g.first()
            val u = s.unit.ifBlank { "шт" }
            val a = g.sumOf { if (it.amount > 0.0) it.amount else 1.0 }
            val have = items.value.firstOrNull { it.name.trim().equals(s.name.trim(), true) }
            if (have == null) {
                saveItem(Item(name = s.name, category = "Інше", qty = a, unit = u, low = if (u == "г" || u == "мл") 100.0 else 1.0))
            } else if (!have.noQty) {
                val add = if (unitGroup(have.unit) == unitGroup(u)) {
                    val b = baseQty(a, u); if (have.unit == "кг" || have.unit == "л") b / 1000 else b
                } else a
                saveItem(have.copy(qty = have.qty + add))
            }
            g.forEach { delShop(it) }
        }
        toast = "Куплене перенесено в холодильник"
    }

    // menu
    fun pool(): List<Dish> {
        val own = recipes.value.map { it.name.lowercase() }.toSet()
        return DISHES.filter { it.name.lowercase() !in own } + recipes.value
    }
    fun forPersons(d: Dish) = if (d.persons > 0) d.persons else persons
    fun checkDish(d: Dish, p: Int) {
        d.ings.forEach { ing ->
            val a = shortage(items.value, ing, p)
            if (a > 0.0) addShop(ing.name, d.name, Math.round(a * 100) / 100.0, ing.unit)
        }
    }
    fun removeDishShop(dish: String) { shop.value.filter { it.forDish == dish }.forEach { delShop(it) } }
    fun suggestions(): List<Pair<Dish, Int>> = pool().map { d ->
        val have = d.ings.count { !isMissing(items.value, it, persons) }
        d to (if (d.ings.isEmpty()) 0 else have * 100 / d.ings.size)
    }.filter { it.second >= 50 }.sortedByDescending { it.second }.take(5)

    fun generate() {
        val p = pool(); val cats = slots.filter { it.isNotBlank() }
        plan = List(days) { DayPlan(cats.mapNotNull { c -> p.filter { it.category == c }.randomOrNull() }) }
        toast = "Позначте страви «Готуватиму», щоб додати відсутні продукти до покупок"
    }
    private fun setDish(day: Int, idx: Int, d: Dish) {
        plan = plan.mapIndexed { i, dp -> if (i != day) dp else DayPlan(dp.dishes.mapIndexed { j, x -> if (j == idx) d else x }) }
    }
    fun replace(day: Int, idx: Int, d: Dish) {
        setDish(day, idx, d)
        if (d.cook) checkDish(d, forPersons(d))
    }
    fun toggleCook(day: Int, idx: Int) {
        val n = plan[day].dishes[idx].let { it.copy(cook = !it.cook) }
        setDish(day, idx, n)
        if (n.cook) { checkDish(n, forPersons(n)); toast = "Відсутні продукти додано до покупок" }
        else { removeDishShop(n.name); toast = "Продукти страви прибрано зі списку покупок" }
    }
    fun reroll(day: Int, idx: Int) {
        val old = plan[day].dishes[idx]
        val alt = pool().filter { it.category == old.category && it.name != old.name }.randomOrNull() ?: return
        if (old.cook) removeDishShop(old.name)
        setDish(day, idx, alt.copy(cook = false))
    }
    fun saveDish(d: Dish, quiet: Boolean = false) = viewModelScope.launch {
        val ex = recipes.value.firstOrNull { it.name.equals(d.name, true) }
        val id = ex?.id ?: d.id.ifEmpty { UUID.randomUUID().toString() }
        repo.put(_code.value, "recipes", id, d.copy(id = id, cook = false))
        if (!quiet) toast = "Страву «${d.name}» збережено"
    }
    fun deleteDish(d: Dish) = viewModelScope.launch { if (d.id.isNotEmpty()) repo.del(_code.value, "recipes", d.id) }
    fun updateViewing(d: Dish) {
        val v = viewing ?: return
        if (v.day >= 0) replace(v.day, v.idx, d)
        else if (recipes.value.any { it.name.equals(d.name, true) }) saveDish(d, quiet = true)
        viewing = v.copy(dish = d)
    }
    fun saveMenu(n: String) = viewModelScope.launch {
        val id = UUID.randomUUID().toString()
        repo.put(_code.value, "menus", id, SavedMenu(id, n, persons, plan)); toast = "Меню збережено"
    }
    fun loadMenu(m: SavedMenu) {
        plan = m.days; persons = m.persons; days = m.days.size.coerceAtLeast(1)
        plan.forEach { dp -> dp.dishes.filter { it.cook }.forEach { checkDish(it, forPersons(it)) } }
        toast = "Меню «${m.name}» завантажено"
    }
    fun deleteMenu(m: SavedMenu) = viewModelScope.launch { repo.del(_code.value, "menus", m.id) }

    // cooking
    fun askCook(d: Dish, p: Int) { cookAsk = CookAsk(d, p) }
    fun finishCook(deduct: Boolean) {
        val a = cookAsk ?: return
        cookAsk = null
        if (deduct) deductIngredients(a.dish, a.persons)
        markCooked(a.dish.name)
    }
    private fun deductIngredients(d: Dish, p: Int) {
        d.ings.forEach { ing ->
            val have = items.value.firstOrNull { it.name.trim().equals(ing.name.trim(), true) }
            if (have != null && !have.noQty && unitGroup(have.unit) == unitGroup(ing.unit)) {
                val left = (baseQty(have.qty, have.unit) - baseQty(ing.perPerson * p, ing.unit)).coerceAtLeast(0.0)
                val q = if (have.unit == "кг" || have.unit == "л") left / 1000 else left
                saveItem(have.copy(qty = q))
            }
        }
    }
    private fun markCooked(dish: String) = viewModelScope.launch {
        val id = UUID.randomUUID().toString()
        repo.put(_code.value, "cooked", id, Cooked(id, dish, uid, name, System.currentTimeMillis()))
        toast = "Родина отримає запит на оцінку"
    }
    fun rate(c: Cooked, stars: Int) = viewModelScope.launch { repo.rate(_code.value, c.id, uid, stars.toLong()) }

    companion object { fun today() = LocalDate.now().toEpochDay() }
}
