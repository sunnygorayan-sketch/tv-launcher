package com.laxmi.tvlauncher

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(
    val pkg: String, val label: String, val intent: Intent,
    val image: ImageBitmap, val banner: Boolean,
    val bg: ImageBitmap, val accent: Color)

val DEFAULT_FAVS = listOf(
    "com.netflix.ninja", "com.google.android.youtube.tv", "in.startv.hotstar",
    "com.amazon.amazonvideo.livingroom", "app.netmirror.newtv", "com.android.vending")
val DEFAULT_HIDDEN = setOf("com.android.deskclock", "com.homwee.mmp.imageplayer")

class MainActivity : ComponentActivity() {
    private val apps = mutableStateOf<List<AppItem>>(emptyList())
    private val pinned = mutableStateOf<Set<String>>(emptySet())
    private val hidden = mutableStateOf<Set<String>>(emptySet())
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler {}
            Home(apps.value, pinned.value, hidden.value,
                onLaunch = { runCatching { startActivity(it.intent) } },
                onLong = ::cycle,
                onReset = { prefs.edit().clear().apply(); load() },
                onSettings = { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } })
        }
    }

    override fun onResume() { super.onResume(); load() }

    private fun load() {
        if (!prefs.contains("init")) {
            prefs.edit().putBoolean("init", true)
                .putStringSet("pinned", DEFAULT_FAVS.toSet())
                .putStringSet("hidden", DEFAULT_HIDDEN).apply()
        }
        pinned.value = prefs.getStringSet("pinned", emptySet())!!.toSet()
        hidden.value = prefs.getStringSet("hidden", emptySet())!!.toSet()
        val pm = packageManager
        fun query(cat: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
        val list = (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER))
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
        apps.value = list.map { ri ->
            val ai = ri.activityInfo
            val banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
            val d = banner ?: ri.loadIcon(pm)
            val bmp = d.toBitmap(if (banner != null) 320 else 128, if (banner != null) 180 else 128)
            // tiny bitmap stretched to full screen = cheap blur (API 30 has no real blur)
            val tiny = Bitmap.createScaledBitmap(bmp, 32, 18, true)
            val px = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
            val accent = lerp(Color(px).copy(alpha = 1f), Color.Black, 0.45f)
            AppItem(
                ai.packageName, ri.loadLabel(pm).toString(),
                pm.getLeanbackLaunchIntentForPackage(ai.packageName)
                    ?: pm.getLaunchIntentForPackage(ai.packageName) ?: Intent(),
                bmp.asImageBitmap(), banner != null, tiny.asImageBitmap(), accent)
        }
    }

    // long press: normal -> favorite -> hidden -> normal
    private fun cycle(pkg: String) {
        val p = pinned.value.toMutableSet(); val h = hidden.value.toMutableSet()
        when {
            pkg in h -> h.remove(pkg)
            pkg in p -> { p.remove(pkg); h.add(pkg) }
            else -> p.add(pkg)
        }
        prefs.edit().putStringSet("pinned", p).putStringSet("hidden", h).apply()
        pinned.value = p; hidden.value = h
    }
}

@Composable
fun Home(
    apps: List<AppItem>, pinned: Set<String>, hidden: Set<String>,
    onLaunch: (AppItem) -> Unit, onLong: (String) -> Unit, onReset: () -> Unit, onSettings: () -> Unit
) {
    val shown = apps.filter { it.pkg !in hidden }
    val favs = shown.filter { it.pkg in pinned }.sortedWith(
        compareBy<AppItem> { val i = DEFAULT_FAVS.indexOf(it.pkg); if (i < 0) 100 else i }.thenBy { it.label.lowercase() })
    val rest = shown.filter { it.pkg !in pinned }.sortedBy { it.label.lowercase() }
    var focused by remember { mutableStateOf<AppItem?>(null) }
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val tf = SimpleDateFormat("hh:mm", Locale.getDefault())
        val df = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        while (true) {
            val now = Date()
            time = tf.format(now); date = df.format(now)
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            greeting = when { h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
            delay(15_000)
        }
    }
    LaunchedEffect(favs.isNotEmpty()) { if (favs.isNotEmpty()) { delay(300); runCatching { first.requestFocus() } } }
    val accent by animateColorAsState(focused?.accent ?: Color(0xFF07070B), tween(600), label = "accent")

    Box(Modifier.fillMaxSize().background(Color(0xFF07070B))) {
        Crossfade(targetState = focused, animationSpec = tween(600), label = "bg") { a ->
            if (a != null) Image(a.bg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.85f)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.30f), Color(0x55000000), Color(0xF207070B)))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(Color(0xAA000000), Color.Transparent))))

        Column(Modifier.fillMaxSize().padding(vertical = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 56.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(greeting, color = Color(0xB3FFFFFF), fontSize = 16.sp)
                    Text(time, color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Light)
                    Text(date, color = Color(0xB3FFFFFF), fontSize = 16.sp)
                }
                Pill("Settings", onSettings)
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 56.dp)) {
                Text(focused?.label ?: "", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
                Text(if (focused?.pkg in pinned) "Favorite" else "App", color = Color(0xB3FFFFFF), fontSize = 16.sp)
                Text("Hold OK to favorite or hide", color = Color(0x80FFFFFF), fontSize = 13.sp)
            }
            Section("Favorites")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 26.dp)) {
                items(favs, key = { it.pkg }) { app ->
                    Tile(app, 232.dp, 130.dp, true, { focused = app }, { onLaunch(app) }, { onLong(app.pkg) },
                        if (app == favs.first()) Modifier.focusRequester(first) else Modifier)
                }
            }
            Section("All Apps")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 22.dp)) {
                items(rest, key = { it.pkg }) { app ->
                    Tile(app, 168.dp, 94.dp, false, { focused = app }, { onLaunch(app) }, { onLong(app.pkg) })
                }
                item { Pill("Reset", onReset) }
            }
        }
    }
}

@Composable
fun Section(t: String) = Text(t, color = Color(0xCCFFFFFF), fontSize = 17.sp, fontWeight = FontWeight.Medium,
    modifier = Modifier.padding(start = 56.dp, top = 8.dp))

@Composable
fun Pill(text: String, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(Modifier.clip(RoundedCornerShape(50)).background(if (f) Color.White else Color(0x33FFFFFF))
        .onFocusChanged { f = it.isFocused }.clickable { onClick() }.padding(horizontal = 24.dp, vertical = 10.dp)) {
        Text(text, color = if (f) Color.Black else Color.White, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(app: AppItem, w: Dp, h: Dp, star: Boolean, onFocus: () -> Unit, onClick: () -> Unit,
         onLong: () -> Unit, extra: Modifier = Modifier) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.12f else 1f, tween(180), label = "scale")
    val shape = RoundedCornerShape(16.dp)
    Box(
        extra.size(width = w, height = h).scale(s)
            .shadow(if (f) 22.dp else 0.dp, shape)
            .clip(shape).background(Color(0xFF22222C))
            .border(if (f) 2.5.dp else 0.dp, Color(0xE6FFFFFF), shape)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center
    ) {
        val a = if (f) 1f else 0.85f
        if (app.banner) Image(app.image, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = a)
        else Image(app.image, app.label, Modifier.size(h * 0.55f), alpha = a)
        if (star) Text("\u2605", color = Color(0xFFFFD54F), fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}
