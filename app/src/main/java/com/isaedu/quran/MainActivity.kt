@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.isaedu.quran

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlin.math.cos
import kotlin.math.sin

private val Green = Color(0xFF14806A)
private val GreenDark = Color(0xFF0F5E4D)
private val Gold = Color(0xFFC9A24E)
private val Cream = Color(0xFFF7F3E8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alarms.channels(this)
        Alarms.scheduleAll(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Gold, background = Cream, surface = Color.White)) {
                App()
            }
        }
    }
}

/** phông chữ Ả Rập Amiri Quran (assets), nếu thiếu dùng phông mặc định */
@Composable
private fun arabicFont(): FontFamily {
    val ctx = LocalContext.current
    return remember { runCatching { FontFamily(Typeface.createFromAsset(ctx.assets, "AmiriQuran.ttf")) }.getOrDefault(FontFamily.Serif) }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(QuranData.loaded) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { QuranData.load(ctx) }; ready = true }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Alarms.scheduleAll(ctx) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("🕌" to "Solah", "📖" to "Quran", "🧠" to "Học thuộc", "🧭" to "Qibla").forEachIndexed { i, (ic, lb) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(ic, fontSize = 22.sp) }, label = { Text(lb) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else when (tab) {
                0 -> SolahScreen()
                1 -> QuranScreen()
                2 -> HifzScreen()
                else -> QiblaScreen()
            }
        }
    }
}

// ───────────────────────── Solah ─────────────────────────

private val HIJRI = listOf("Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
    "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah")

private fun hijriToday(): String = runCatching {
    val h = HijrahDate.from(LocalDate.now())
    "${h.get(ChronoField.DAY_OF_MONTH)} ${HIJRI[h.get(ChronoField.MONTH_OF_YEAR) - 1]} ${h.get(ChronoField.YEAR)} AH"
}.getOrDefault("")

@Composable
fun SolahScreen() {
    val ctx = LocalContext.current
    val st = remember { Store(ctx) }
    var rev by remember { mutableIntStateOf(0) }          // tăng để vẽ lại sau khi đổi cài đặt
    var settings by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(20_000) } }
    if (settings) { BackHandler { settings = false; rev++ }; SettingsScreen(st) { settings = false; rev++; Alarms.scheduleAll(ctx) }; return }

    val today = LocalDate.now()
    val times = remember(rev, today) { Alarms.timesFor(st, today) }
    val tomorrowFajr = remember(rev, today) { Alarms.timesFor(st, today.plusDays(1))[Prayer.FAJR]!! }
    val nowMin = now.hour * 60 + now.minute
    val next = Prayer.values().filter { it.isSolah }.firstOrNull { PrayerTimes.minutes(times[it]!!) > nowMin }
    val nextMin = if (next != null) PrayerTimes.minutes(times[next]!!) else PrayerTimes.minutes(tomorrowFajr) + 1440
    val left = nextMin - nowMin

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = GreenDark), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("📍 ${st.place}", color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                Text(hijriToday(), color = Gold, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text("Sắp đến: ${(next ?: Prayer.FAJR).vi}", color = Color.White, fontSize = 18.sp)
                Text("%d giờ %02d phút nữa".format(left / 60, left % 60), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("${st.method.label} · Asr ${st.asr.label}", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
            }
        }
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(vertical = 6.dp)) {
                for (pr in Prayer.values()) {
                    var on by remember(rev, pr) { mutableStateOf(st.notify(pr)) }
                    val isNext = pr == next
                    Row(Modifier.fillMaxWidth().background(if (isNext) Green.copy(alpha = .08f) else Color.Transparent).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pr.vi, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal, fontSize = 17.sp)
                            Text(pr.ar, color = Color.Gray, fontSize = 13.sp)
                        }
                        Text(PrayerTimes.hhmm(times[pr]!!), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = if (isNext) Green else Color.Unspecified)
                        Spacer(Modifier.width(10.dp))
                        Switch(checked = on, onCheckedChange = { on = it; st.setNotify(pr, it); Alarms.scheduleAll(ctx) })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LocationButton(st) { rev++; Alarms.scheduleAll(ctx) }
            OutlinedButton(onClick = { settings = true }) { Text("⚙️ Cài đặt") }
        }
        Text("🔔 Bật công tắc để nhận thông báo đúng giờ. Giờ tính theo vị trí và phương pháp trong Cài đặt – có thể chỉnh ±phút cho khớp lịch thánh đường nơi bạn ở.",
            fontSize = 12.sp, color = Color.Gray)
    }
}

private val CITIES = listOf(
    Triple("TP. Hồ Chí Minh", 10.7769, 106.7009), Triple("Hà Nội", 21.0285, 105.8542), Triple("Châu Đốc – An Giang", 10.7090, 105.1170),
    Triple("Phan Rang – Ninh Thuận", 11.5650, 108.9886), Triple("Tây Ninh", 11.3100, 106.0983), Triple("Đà Nẵng", 16.0544, 108.2022),
    Triple("Cần Thơ", 10.0452, 105.7469), Triple("Kuala Lumpur", 3.1390, 101.6869), Triple("Makkah", 21.4225, 39.8262))

@SuppressLint("MissingPermission")
@Composable
fun LocationButton(st: Store, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun use(loc: Location?) {
        busy = false
        if (loc == null) return
        st.lat = loc.latitude; st.lng = loc.longitude
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching { @Suppress("DEPRECATION") Geocoder(ctx).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let { it.subAdminArea ?: it.locality ?: it.adminArea } }.getOrNull()
            }
            st.place = name ?: "%.3f, %.3f".format(loc.latitude, loc.longitude); onDone()
        }
    }
    fun fetch() {
        val lm = ctx.getSystemService(LocationManager::class.java)
        busy = true
        val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (Build.VERSION.SDK_INT >= 30) {
            val prov = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
            runCatching { lm.getCurrentLocation(prov, null, ctx.mainExecutor) { use(it ?: last) } }.onFailure { use(last) }
        } else use(last)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r.values.any { it }) fetch() }
    Button(onClick = {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetch()
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }, enabled = !busy) { Text(if (busy) "Đang lấy…" else "📍 Vị trí hiện tại") }
}

@Composable
fun SettingsScreen(st: Store, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var method by remember { mutableStateOf(st.method) }
    var asr by remember { mutableStateOf(st.asr) }
    var pre by remember { mutableIntStateOf(st.preMinutes) }
    var hifzOn by remember { mutableStateOf(st.hifzReminder) }
    var hifzTime by remember { mutableIntStateOf(st.hifzTime) }
    var adj by remember { mutableStateOf(st.adjustments()) }
    var place by remember { mutableStateOf(st.place) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("← Xong") }
            Text("Cài đặt", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Section("Phương pháp tính") {
            Method.values().forEach { m -> RadioRow(m.label + "  (Fajr ${m.fajr}°, " + (if (m.ishaMinutes > 0) "Isha +${m.ishaMinutes}′" else "Isha ${m.isha}°") + ")", method == m) { method = m; st.method = m } }
        }
        Section("Asr theo madhhab") {
            AsrMadhhab.values().forEach { a -> RadioRow(a.label, asr == a) { asr = a; st.asr = a } }
        }
        Section("Chỉnh giờ cho khớp thánh đường (± phút)") {
            Prayer.values().forEach { pr ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pr.vi, Modifier.weight(1f))
                    TextButton(onClick = { st.setAdjust(pr, st.adjust(pr) - 1); adj = st.adjustments() }) { Text("−", fontSize = 20.sp) }
                    Text("%+d".format(adj[pr] ?: 0), Modifier.width(36.dp), textAlign = TextAlign.Center)
                    TextButton(onClick = { st.setAdjust(pr, st.adjust(pr) + 1); adj = st.adjustments() }) { Text("+", fontSize = 20.sp) }
                }
            }
        }
        Section("Nhắc trước giờ Solah") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10, 15, 30).forEach { m -> FilterChip(selected = pre == m, onClick = { pre = m; st.preMinutes = m }, label = { Text(if (m == 0) "Tắt" else "$m′") }) }
            }
        }
        Section("Nhắc ôn học thuộc mỗi ngày") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = hifzOn, onCheckedChange = { hifzOn = it; st.hifzReminder = it })
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { hifzTime = (hifzTime - 30 + 1440) % 1440; st.hifzTime = hifzTime }) { Text("−30′") }
                Text("%02d:%02d".format(hifzTime / 60, hifzTime % 60), fontSize = 20.sp)
                TextButton(onClick = { hifzTime = (hifzTime + 30) % 1440; st.hifzTime = hifzTime }) { Text("+30′") }
            }
        }
        Section("Chọn nhanh thành phố") {
            Text("Đang dùng: $place", fontSize = 13.sp, color = Color.Gray)
            CITIES.forEach { (n, la, lo) -> RadioRow(n, place == n) { place = n; st.place = n; st.lat = la; st.lng = lo } }
        }
        if (Build.VERSION.SDK_INT >= 31 && !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            OutlinedButton(onClick = {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))) }
            }) { Text("⏰ Cho phép báo thức đúng giờ") }
        }
        Section("Nguồn dữ liệu") {
            Text("• Văn bản Quran (Uthmani): Tanzil.net – CC BY 3.0, giữ nguyên văn bản.\n• Bản dịch tiếng Việt: Rowwad Translation Center – QuranEnc.com.\n• Giọng đọc: Mahmoud Khalil Al-Husary (Muallim) – EveryAyah.com.\n• Phông chữ: Amiri Quran (SIL OFL).\n• Giờ Solah: thuật toán thiên văn chuẩn (PrayTimes).",
                fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = GreenDark); content()
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick); Text(label, fontSize = 14.sp)
    }
}

// ───────────────────────── Quran ─────────────────────────

@Composable
private fun ArabicText(text: String, size: Int = 28, color: Color = Color(0xFF1B2B26), modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(text, modifier.fillMaxWidth(), style = TextStyle(fontFamily = arabicFont(), fontSize = size.sp, lineHeight = (size * 1.9).sp,
            textDirection = TextDirection.Rtl, textAlign = TextAlign.Start, color = color))
    }
}

@Composable
fun QuranScreen() {
    val ctx = LocalContext.current
    val st = remember { Store(ctx) }
    var sura by rememberSaveable { mutableIntStateOf(0) }
    var startAya by remember { mutableIntStateOf(1) }
    if (sura > 0) { BackHandler { Audio.stop(); sura = 0 }; SuraView(sura, startAya, st) { Audio.stop(); sura = 0 }; return }
    var q by remember { mutableStateOf("") }
    val list = QuranData.suras.filter { q.isBlank() || it.name.contains(q, true) || it.en.contains(q, true) || it.index.toString() == q.trim() }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Tìm sura (tên hoặc số)") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        val ls = QuranData.sura(st.lastSura)
        Button(onClick = { startAya = st.lastAya; sura = st.lastSura }, Modifier.fillMaxWidth()) { Text("▶ Đọc tiếp: ${ls.index}. ${ls.name} – āyah ${st.lastAya}") }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(list, key = { it.index }) { s ->
                Row(Modifier.fillMaxWidth().clickable { startAya = 1; sura = s.index }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(Green.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("${s.index}", color = GreenDark, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("${s.en} · ${s.ayas} āyah · ${if (s.meccan) "Makkah" else "Madinah"}", fontSize = 12.sp, color = Color.Gray)
                    }
                    Text(s.ar, fontFamily = arabicFont(), fontSize = 22.sp, color = GreenDark)
                }
                HorizontalDivider(color = Color(0x11000000))
            }
        }
    }
}

@Composable
fun SuraView(s: Int, start: Int, st: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sura = QuranData.sura(s)
    val ayas = remember(s) { QuranData.ayas(s) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 1).coerceIn(0, (ayas.size - 1).coerceAtLeast(0)))
    var playing by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var showVi by remember { mutableStateOf(st.showVi) }
    fun playFrom(a: Int) {
        playing = a; st.lastSura = s; st.lastAya = a
        scope.launch {
            val f = Audio.fetch(ctx, s, a)
            if (f == null) { status = "⚠️ Không tải được âm thanh (cần mạng)"; playing = 0; return@launch }
            status = ""
            Audio.play(f, st.speed) { if (playing == a && a < ayas.size) { playFrom(a + 1); scope.launch { state.animateScrollToItem(a) } } else playing = 0 }
        }
    }
    DisposableEffect(Unit) { onDispose { Audio.stop() } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←") }
            Column(Modifier.weight(1f)) { Text("${sura.index}. ${sura.name}", fontWeight = FontWeight.Bold); Text(sura.en, fontSize = 12.sp, color = Color.Gray) }
            FilterChip(selected = showVi, onClick = { showVi = !showVi; st.showVi = showVi }, label = { Text("Nghĩa") })
            Spacer(Modifier.width(6.dp))
            if (playing > 0) OutlinedButton(onClick = { Audio.stop(); playing = 0 }) { Text("⏸") }
        }
        if (status.isNotEmpty()) Text(status, Modifier.padding(8.dp), color = Color(0xFFB00020), fontSize = 13.sp)
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ayas, key = { it.aya }) { a ->
                val (bis, body) = QuranData.splitBismillah(a)
                Card(Modifier.fillMaxWidth().clickable { playFrom(a.aya) }, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (playing == a.aya) Color(0xFFE6F4EF) else Color.White)) {
                    Column(Modifier.padding(14.dp)) {
                        if (bis != null) { ArabicText(bis, 24, GreenDark, Modifier.padding(bottom = 6.dp)) }
                        ArabicText("$body ﴿${a.aya}﴾")
                        if (showVi && a.vi.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("${a.aya}. ${a.vi}", fontSize = 15.sp, color = Color(0xFF444444)) }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Học thuộc ─────────────────────────

private fun hint(text: String): String { val w = text.split(" "); return w.first() + "  " + "▁▁ ".repeat((w.size - 1).coerceAtMost(12)) }

@Composable
fun HifzScreen() {
    val ctx = LocalContext.current
    val st = remember { Store(ctx) }
    var reviewMode by remember { mutableStateOf(false) }
    if (reviewMode) { BackHandler { Audio.stop(); reviewMode = false }; ReviewView(st) { Audio.stop(); reviewMode = false }; return }
    val scope = rememberCoroutineScope()
    var s by remember { mutableIntStateOf(st.hifzSura) }
    var from by remember { mutableIntStateOf(st.hifzFrom) }
    var to by remember { mutableIntStateOf(st.hifzTo) }
    var cur by remember { mutableIntStateOf(st.hifzFrom) }
    var rep by remember { mutableIntStateOf(0) }
    var repeat by remember { mutableIntStateOf(st.repeat) }
    var speed by remember { mutableFloatStateOf(st.speed) }
    var hide by remember { mutableStateOf(st.hideText) }
    var reveal by remember { mutableStateOf(false) }
    var showVi by remember { mutableStateOf(st.showVi) }
    var playing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf(false) }
    var memRev by remember { mutableIntStateOf(0) }
    val sura = QuranData.sura(s)
    fun save() { st.hifzSura = s; st.hifzFrom = from; st.hifzTo = to }
    fun play() {
        playing = true
        scope.launch {
            val f = Audio.fetch(ctx, s, cur)
            if (f == null) { status = "⚠️ Không tải được âm thanh (cần mạng lần đầu)"; playing = false; return@launch }
            status = ""
            if (cur < to) launch { Audio.fetch(ctx, s, cur + 1) }      // tải trước āyah kế
            Audio.play(f, speed) {
                if (playing) {
                    rep++
                    if (rep < repeat) play()
                    else { rep = 0; if (cur < to) { cur++; reveal = false; play() } else { playing = false; cur = from } }
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { Audio.stop() } }
    if (pick) {
        AlertDialog(onDismissRequest = { pick = false }, confirmButton = { TextButton(onClick = { pick = false }) { Text("Đóng") } },
            title = { Text("Chọn sura") },
            text = {
                LazyColumn(Modifier.height(420.dp)) {
                    items(QuranData.suras) { x ->
                        Text("${x.index}. ${x.name} (${x.ayas})  ·  đã thuộc ${st.memorizedCount(x.index)}", Modifier.fillMaxWidth().clickable {
                            Audio.stop(); playing = false; s = x.index; from = 1; to = minOf(x.ayas, 5); cur = 1; rep = 0; save(); pick = false
                        }.padding(vertical = 8.dp))
                    }
                }
            })
    }
    val due = remember(memRev) { st.due().size }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { Audio.stop(); playing = false; reviewMode = true }, Modifier.fillMaxWidth(), enabled = due > 0,
            colors = ButtonDefaults.buttonColors(containerColor = Gold)) { Text(if (due > 0) "🔁 Ôn hôm nay ($due āyah)" else "✅ Hôm nay không có āyah cần ôn") }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${sura.index}. ${sura.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Đã thuộc ${remember(memRev, s) { st.memorizedCount(s) }}/${sura.ayas} āyah", fontSize = 12.sp, color = Color.Gray)
                    }
                    OutlinedButton(onClick = { pick = true }) { Text("Đổi sura") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Āyah", Modifier.width(48.dp))
                    Stepper(from, 1, to) { from = it; if (cur < from) cur = from; save() }
                    Text(" → ")
                    Stepper(to, from, sura.ayas) { to = it; if (cur > to) cur = to; save() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Lặp", Modifier.width(48.dp))
                    listOf(1, 3, 5, 7, 10).forEach { n -> FilterChip(selected = repeat == n, onClick = { repeat = n; st.repeat = n }, label = { Text("$n") }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tốc độ", Modifier.width(48.dp), fontSize = 13.sp)
                    listOf(0.75f, 1f, 1.25f).forEach { v -> FilterChip(selected = speed == v, onClick = { speed = v; st.speed = v }, label = { Text("${v}x") }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = hide, onClick = { hide = !hide; st.hideText = hide; reveal = false }, label = { Text("🙈 Ẩn chữ") })
                    FilterChip(selected = showVi, onClick = { showVi = !showVi; st.showVi = showVi }, label = { Text("Nghĩa") })
                }
            }
        }
        val a = QuranData.aya(s, cur)
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp)) {
                Text("Āyah $cur / ${sura.ayas}" + if (playing) "  ·  lần ${rep + 1}/$repeat" else "", color = Green, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val body = QuranData.splitBismillah(a).second
                if (hide && !reveal) {
                    ArabicText(hint(body), 30, Color(0xFF9AA5A1))
                    TextButton(onClick = { reveal = true }) { Text("👁 Hiện chữ để kiểm tra") }
                } else ArabicText("$body ﴿$cur﴾", 30)
                if (showVi && a.vi.isNotBlank() && (!hide || reveal)) Text(a.vi, fontSize = 15.sp, color = Color(0xFF444444))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { Audio.stop(); playing = false; rep = 0; reveal = false; if (cur > from) cur-- }) { Text("◀") }
                    Button(onClick = { if (playing) { Audio.stop(); playing = false } else { rep = 0; play() } }) { Text(if (playing) "⏸ Dừng" else "▶ Nghe") }
                    OutlinedButton(onClick = { Audio.stop(); playing = false; rep = 0; reveal = false; if (cur < to) cur++ }) { Text("▶") }
                }
                val mem = remember(memRev, s, cur) { st.isMemorized(s, cur) }
                FilledTonalButton(onClick = { st.markMemorized(s, cur, !mem); memRev++ }, Modifier.fillMaxWidth()) {
                    Text(if (mem) "✓ Đã thuộc (bấm để bỏ)" else "☆ Đánh dấu đã thuộc āyah này")
                }
                if (status.isNotEmpty()) Text(status, color = Color(0xFFB00020), fontSize = 13.sp)
            }
        }
        Text("Cách học: nghe lặp từng āyah → bật 🙈 Ẩn chữ để tự đọc lại → đánh dấu đã thuộc. App sẽ nhắc ôn lại sau 1, 3, 7, 14, 30… ngày để nhớ lâu.",
            fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun Stepper(v: Int, min: Int, max: Int, on: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { if (v > min) on(v - 1) }, contentPadding = PaddingValues(0.dp)) { Text("−", fontSize = 20.sp) }
        Text("$v", Modifier.width(34.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        TextButton(onClick = { if (v < max) on(v + 1) }, contentPadding = PaddingValues(0.dp)) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
fun ReviewView(st: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val queue = remember { st.due() }
    var i by remember { mutableIntStateOf(0) }
    var reveal by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { Audio.stop() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("← Quay lại") }; Text("Ôn hôm nay", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        if (i >= queue.size) {
            Text("🎉 Đã ôn xong ${queue.size} āyah hôm nay. Alhamdulillah!", fontSize = 18.sp)
            Button(onClick = onBack) { Text("Xong") }
            return@Column
        }
        val (s, a) = queue[i]
        val aya = QuranData.aya(s, a); val body = QuranData.splitBismillah(aya).second
        Text("${i + 1}/${queue.size} · ${QuranData.sura(s).name}, āyah $a", color = Green)
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(18.dp)) {
                if (reveal) ArabicText("$body ﴿$a﴾", 30) else ArabicText(hint(body), 30, Color(0xFF9AA5A1))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch { val f = Audio.fetch(ctx, s, a); if (f == null) status = "⚠️ Cần mạng để tải âm thanh" else Audio.play(f, st.speed) {} }
                    }) { Text("🔊 Nghe") }
                    OutlinedButton(onClick = { reveal = true }) { Text("👁 Hiện chữ") }
                }
                if (status.isNotEmpty()) Text(status, color = Color(0xFFB00020), fontSize = 13.sp)
            }
        }
        Text("Tự đọc thuộc lòng trước, rồi hiện chữ để kiểm tra:", fontSize = 13.sp, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { st.review(s, a, true); Audio.stop(); reveal = false; i++ }, Modifier.weight(1f)) { Text("✓ Nhớ") }
            OutlinedButton(onClick = { st.review(s, a, false); Audio.stop(); reveal = false; i++ }, Modifier.weight(1f)) { Text("✗ Quên – ôn lại mai") }
        }
    }
}

// ───────────────────────── Qibla ─────────────────────────

@Composable
fun QiblaScreen() {
    val ctx = LocalContext.current
    val st = remember { Store(ctx) }
    val qibla = remember { PrayerTimes.qibla(st.lat, st.lng) }
    var azimuth by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val decl = GeomagneticField(st.lat.toFloat(), st.lng.toFloat(), 0f, System.currentTimeMillis()).declination
        val l = object : SensorEventListener {
            val m = FloatArray(9); val o = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(m, e.values); SensorManager.getOrientation(m, o)
                val az = (Math.toDegrees(o[0].toDouble()).toFloat() + decl + 360f) % 360f
                azimuth = azimuth + (((az - azimuth + 540f) % 360f) - 180f) * 0.15f          // làm mượt
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (rv == null) hasSensor = false else sm.registerListener(l, rv, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }
    val diff = (((qibla - azimuth) % 360 + 540) % 360) - 180
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Hướng Qibla", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("%.0f° tính từ hướng Bắc · %s".format(qibla, st.place), color = Color.Gray)
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2; val c = center
                drawCircle(Color.White, r); drawCircle(Green.copy(alpha = .25f), r, style = androidx.compose.ui.graphics.drawscope.Stroke(6f))
                rotate(-azimuth, c) {
                    for (k in 0 until 72) {
                        val a = Math.toRadians(k * 5.0); val len = if (k % 18 == 0) 26f else if (k % 2 == 0) 14f else 8f
                        drawLine(Color(0xFF9AA5A1), Offset(c.x + (r - 6) * sin(a).toFloat(), c.y - (r - 6) * cos(a).toFloat()),
                            Offset(c.x + (r - 6 - len) * sin(a).toFloat(), c.y - (r - 6 - len) * cos(a).toFloat()), 3f)
                    }
                    drawCircle(Color(0xFFD64545), 10f, Offset(c.x, c.y - r + 44))          // chấm đỏ = Bắc
                    rotate(qibla.toFloat(), c) {
                        drawLine(Gold, c, Offset(c.x, c.y - r + 60), 12f)
                        drawRect(Color(0xFF1B1B1B), Offset(c.x - 22, c.y - r + 34), androidx.compose.ui.geometry.Size(44f, 44f))
                        drawRect(Gold, Offset(c.x - 22, c.y - r + 44), androidx.compose.ui.geometry.Size(44f, 6f))
                    }
                }
                drawCircle(GreenDark, 12f, c)
                drawLine(Green, Offset(c.x, 0f), Offset(c.x, 30f), 8f)                    // vạch chỉ hướng đầu điện thoại
            }
        }
        Text(if (!hasSensor) "Máy không có cảm biến la bàn." else if (kotlin.math.abs(diff) < 3) "✅ Đúng hướng Qibla" else if (diff > 0) "Xoay sang phải %.0f°".format(diff) else "Xoay sang trái %.0f°".format(-diff),
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (kotlin.math.abs(diff) < 3) Green else Color.Unspecified)
        Text("Đặt điện thoại nằm phẳng, tránh xa đồ kim loại/nam châm. Nếu lệch, vẽ hình số 8 trên không để hiệu chỉnh la bàn. Vị trí lấy theo tab Solah.",
            fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
    }
}
