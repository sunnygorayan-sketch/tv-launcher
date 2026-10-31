package com.laxmi.tvlauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(val pkg: String, val label: String, val intent: Intent, val image: ImageBitmap, val banner: Boolean)

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
            AppItem(
                ai.packageName, ri.loadLabel(pm).toString(),
                pm.getLeanbackLaunchIntentForPackage(ai.packageName)
                    ?: pm.getLaunchIntentForPackage(ai.packageName) ?: Intent(),
                d.toBitmap(if (banner != null) 320 else 128, if (banner != null) 180 else 128).asImageBitmap(),
                banner != null)
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
    var clock by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val f = SimpleDateFormat("hh:mm a  |  EEE, d MMM", Locale.getDefault())
        while (true) { clock = f.format(Date()); delay(15_000) }
    }
    LaunchedEffect(favs.isNotEmpty()) { if (favs.isNotEmpty()) { delay(300); runCatching { first.requestFocus() } } }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        focused?.let { Image(it.image, null, Modifier.fillMaxSize().alpha(0.28f), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0x99000000), Color(0x33000000), Color(0xEE0B0B10)))))

        Column(Modifier.fillMaxSize().padding(vertical = 28.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 56.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Home", color = Color.White, fontSize = 22.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(clock, color = Color(0xCCFFFFFF), fontSize = 20.sp)
                    Pill("Settings", onSettings)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 56.dp)) {
                Text(focused?.label ?: "", color = Color.White, fontSize = 44.sp)
                Text("Press & hold OK: favorite / hide", color = Color(0x99FFFFFF), fontSize = 14.sp)
            }
            Section("Favorites")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 22.dp)) {
                items(favs, key = { it.pkg }) { app ->
                    Tile(app, 210.dp, 118.dp, true, { focused = app }, { onLaunch(app) }, { onLong(app.pkg) },
                        if (app == favs.first()) Modifier.focusRequester(first) else Modifier)
                }
            }
            Section("All Apps")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 18.dp)) {
                items(rest, key = { it.pkg }) { app ->
                    Tile(app, 160.dp, 90.dp, app.pkg in pinned, { focused = app }, { onLaunch(app) }, { onLong(app.pkg) })
                }
                item { Pill("Reset", onReset) }
            }
        }
    }
}

@Composable
fun Section(t: String) = Text(t, color = Color(0xCCFFFFFF), fontSize = 16.sp,
    modifier = Modifier.padding(start = 56.dp, top = 6.dp))

@Composable
fun Pill(text: String, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(Modifier.clip(RoundedCornerShape(50)).background(if (f) Color.White else Color(0x33FFFFFF))
        .onFocusChanged { f = it.isFocused }.clickable { onClick() }.padding(horizontal = 22.dp, vertical = 10.dp)) {
        Text(text, color = if (f) Color.Black else Color.White, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(app: AppItem, w: Dp, h: Dp, star: Boolean, onFocus: () -> Unit, onClick: () -> Unit,
         onLong: () -> Unit, extra: Modifier = Modifier) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.1f else 1f, label = "scale")
    Box(
        extra.size(width = w, height = h).scale(s)
            .clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C24))
            .border(if (f) 3.dp else 0.dp, Color.White, RoundedCornerShape(14.dp))
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center
    ) {
        if (app.banner) Image(app.image, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Image(app.image, app.label, Modifier.size(h * 0.55f))
        if (star) Text("\u2605", color = Color(0xFFFFD54F), fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}
