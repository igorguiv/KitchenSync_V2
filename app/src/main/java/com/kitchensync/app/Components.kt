package com.kitchensync.app

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp

val Bubble = RoundedCornerShape(22.dp)
val Green = Color(0xFF5CC98A); val Yellow = Color(0xFFF2C94C); val Red = Color(0xFFEB5757)

data class Pal(val bg: Color, val card: Color, val accent: Color, val name: String)
val PALS = listOf(
    Pal(Color(0xFFFFF4E0), Color(0xFFFFFFFF), Color(0xFFE59A6E), "Кремовий"),
    Pal(Color(0xFFE4F6EC), Color(0xFFFFFFFF), Color(0xFF4FB38A), "М'ятний"),
    Pal(Color(0xFFEFE8FB), Color(0xFFFFFFFF), Color(0xFF8E77D1), "Лавандовий"),
    Pal(Color(0xFFE5F1FB), Color(0xFFFFFFFF), Color(0xFF6BA8E0), "Блакитний"),
    Pal(Color(0xFFFCE9EF), Color(0xFFFFFFFF), Color(0xFFD9809F), "Рожевий")
)
val BG_NAMES = listOf("Чисто", "Крапки", "Лінії", "Кухонні малюнки", "Плюсики", "Хвилі")
val DOODLES = listOf("🍎", "🥕", "🍴", "🥛", "🍞", "☕", "🧀", "🍅")

fun loadFont(ctx: Context): FontFamily = runCatching {
    FontFamily(android.graphics.Typeface.createFromAsset(ctx.assets, "fonts/Nunito.ttf"))
}.getOrDefault(FontFamily.Default)

fun typo(f: FontFamily): Typography {
    val t = Typography()
    return Typography(
        headlineLarge = t.headlineLarge.copy(fontFamily = f), headlineMedium = t.headlineMedium.copy(fontFamily = f),
        headlineSmall = t.headlineSmall.copy(fontFamily = f), titleLarge = t.titleLarge.copy(fontFamily = f),
        titleMedium = t.titleMedium.copy(fontFamily = f), titleSmall = t.titleSmall.copy(fontFamily = f),
        bodyLarge = t.bodyLarge.copy(fontFamily = f), bodyMedium = t.bodyMedium.copy(fontFamily = f),
        bodySmall = t.bodySmall.copy(fontFamily = f), labelLarge = t.labelLarge.copy(fontFamily = f),
        labelMedium = t.labelMedium.copy(fontFamily = f), labelSmall = t.labelSmall.copy(fontFamily = f)
    )
}

fun qtyColor(i: Item) = if (i.noQty) Green else if (i.qty <= 0) Red else if (i.qty <= i.low) Yellow else Green
fun expColor(i: Item): Color {
    if (i.expiry == 0L) return Green
    val d = i.expiry - MainViewModel.today()
    return if (d < 0) Red else if (d <= 3) Yellow else Green
}

@Composable
fun Light(color: Color) {
    val c by animateColorAsState(color, tween(400), label = "light")
    Box(Modifier.size(14.dp).background(c, CircleShape))
}

@Composable
fun Appear(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 8 }) { content() }
}

fun Modifier.bounce(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "bounce")
    this.graphicsLayer { scaleX = s; scaleY = s }.clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun BubbleCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Appear {
        val base = Modifier.fillMaxWidth().shadow(3.dp, Bubble).background(MaterialTheme.colorScheme.surface, Bubble)
        Column(modifier.then(if (onClick != null) base.bounce(onClick) else base).animateContentSize().padding(14.dp), content = content)
    }
}

@Composable
fun PButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = Bubble, content: @Composable RowScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, label = "pb")
    Button(onClick, modifier.graphicsLayer { scaleX = s; scaleY = s }, enabled, shape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)),
        interactionSource = src, content = content)
}

@Composable
fun POutlined(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shape: Shape = Bubble, content: @Composable RowScope.() -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, label = "po")
    OutlinedButton(onClick, modifier.graphicsLayer { scaleX = s; scaleY = s }, enabled, shape,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        interactionSource = src, content = content)
}

@Composable
fun PChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected, onClick, { Text(label) }, colors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))

data class Ask(val text: String, val action: () -> Unit)

@Composable
fun ConfirmDialog(text: String, onClose: () -> Unit, onYes: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Підтвердіть") }, text = { Text(text) },
        confirmButton = { PButton(onYes) { Text("Так, видалити") } },
        dismissButton = { TextButton(onClose) { Text("Скасувати") } })
}

@Composable
fun Pattern(kind: Int, c: Color) {
    Crossfade(kind, animationSpec = tween(500), label = "pattern") { k ->
        if (k != 0) Canvas(Modifier.fillMaxSize()) {
            val col = c.copy(alpha = 0.16f)
            when (k) {
                1 -> { var y = 20f; while (y < size.height) { var x = 20f; while (x < size.width) { drawCircle(col, 3f, Offset(x, y)); x += 48f }; y += 48f } }
                2 -> { var y = 0f; while (y < size.height) { drawLine(col, Offset(0f, y), Offset(size.width, y), 2f); y += 36f } }
                3 -> drawIntoCanvas { cv ->
                    val paint = android.graphics.Paint().apply { textSize = 44f; alpha = 45 }
                    var y = 70f; var r = 0
                    while (y < size.height) {
                        var x = if (r % 2 == 0) 30f else 100f; var i = r
                        while (x < size.width) {
                            cv.nativeCanvas.save(); cv.nativeCanvas.rotate(((i * 37) % 40 - 20).toFloat(), x, y)
                            cv.nativeCanvas.drawText(DOODLES[i % DOODLES.size], x, y, paint); cv.nativeCanvas.restore()
                            x += 140f; i++
                        }
                        y += 110f; r++
                    }
                }
                4 -> { var y = 40f; var r = 0; while (y < size.height) { var x = if (r % 2 == 0) 30f else 70f; while (x < size.width) {
                    drawLine(col, Offset(x - 8, y), Offset(x + 8, y), 3f); drawLine(col, Offset(x, y - 8), Offset(x, y + 8), 3f); x += 80f }; y += 60f; r++ } }
                else -> { var y = 40f; while (y < size.height) { val p = Path(); p.moveTo(0f, y); var x = 0f
                    while (x < size.width) { p.relativeQuadraticBezierTo(20f, -18f, 40f, 0f); p.relativeQuadraticBezierTo(20f, 18f, 40f, 0f); x += 80f }
                    drawPath(p, col, style = Stroke(3f)); y += 70f } }
            }
        }
    }
}
