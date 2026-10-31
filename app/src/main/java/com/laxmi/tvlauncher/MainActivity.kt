package com.laxmi.tvlauncher

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(val pkg: String, val label: String, val intent: Intent, val image: ImageBitmap, val banner: Boolean)

class MainActivity : ComponentActivity() {
    private val apps = mutableStateOf<List<AppItem>>(emptyList())
    private val pinned = mutableStateOf<Set<String>>(emptySet())
    private val hidden = mutableStateOf<Set<String>>(emptySet())
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler {} // Home pe Back kuch na kare
            Home(apps.value, pinned.value, hidden.value,
                onLaunch = { startActivity(it.intent) },
                onLong = ::cycle,
                onReset = { prefs.edit().clear().apply(); load() })
        }
    }

    override fun onResume() { super.onResume(); load() }

    private fun load() {
        pinned.value = prefs.getStringSet("pinned", emptySet())!!.toSet()
        hidden.value = prefs.getStringSet("hidden", emptySet())!!.toSet()
        val pm = packageManager
        fun query(cat: String) = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
        val list = (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER))
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
        apps.value = list.map { ri ->
            val ai = ri.activityInfo
            val banner = ri.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
            val d = banner ?: ri.loadIcon(pm)
            AppItem(
                ai.packageName, ri.loadLabel(pm).toString(),
                pm.getLeanbackLaunchIntentForPackage(ai.packageName)
                    ?: pm.getLaunchIntentForPackage(ai.packageName) ?: Intent(),
                d.toBitmap(if (banner != null) 320 else 128, if (banner != null) 180 else 128).asImageBitmap(),
                banner != null)
        }
    }

    // long press: normal -> pinned -> hidden -> normal
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Home(
    apps: List<AppItem>, pinned: Set<String>, hidden: Set<String>,
    onLaunch: (AppItem) -> Unit, onLong: (String) -> Unit, onReset: () -> Unit
) {
    val visible = apps.filter { it.pkg !in hidden }
        .sortedWith(compareByDescending<AppItem> { it.pkg in pinned }.thenBy { it.label.lowercase() })
    var focused by remember { mutableStateOf<AppItem?>(null) }
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val f = SimpleDateFormat("hh:mm a  |  EEE, d MMM", Locale.getDefault())
        while (true) { clock = f.format(Date()); delay(15_000) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        focused?.let {
            Image(it.image, null, Modifier.fillMaxSize().alpha(0.28f), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color(0x99000000), Color(0x33000000), Color(0xEE0B0B10)))))

        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Home", color = Color.White, fontSize = 22.sp)
                Text(clock, color = Color(0xCCFFFFFF), fontSize = 20.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(focused?.label ?: "", color = Color.White, fontSize = 44.sp)
            Text("Press & hold OK: pin / hide", color = Color(0x99FFFFFF), fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 20.dp, horizontal = 8.dp)) {
                items(visible, key = { it.pkg }) { app ->
                    Tile(app, app.pkg in pinned, { focused = app }, { onLaunch(app) }, { onLong(app.pkg) })
                }
                item {
                    Box(Modifier.size(width = 140.dp, height = 112.dp)
                        .clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C24))
                        .clickable { onReset() }, contentAlignment = Alignment.Center) {
                        Text("Reset", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(app: AppItem, pinned: Boolean, onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.14f else 1f, label = "scale")
    Box(
        Modifier.size(width = 200.dp, height = 112.dp).scale(s)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1C1C24))
            .border(if (f) 3.dp else 0.dp, Color.White, RoundedCornerShape(14.dp))
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center
    ) {
        if (app.banner) Image(app.image, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Image(app.image, app.label, Modifier.size(64.dp))
        if (pinned) Text("\u2605", color = Color(0xFFFFD54F), fontSize = 16.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}
