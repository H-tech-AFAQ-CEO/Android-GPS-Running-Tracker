package com.trackergps.myapplication

// ============================================================================
//  RunTracker  -  single-file GPS running tracker (Kotlin + Jetpack Compose + OSM)
// ============================================================================

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline as OsmPolyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

// ============================================================================
// 1. THEME
// ============================================================================

private val Accent = Color(0xFFD7FF3F)

private val AppType = Typography(
    displaySmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 12.sp),
    bodySmall = TextStyle(fontSize = 11.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp),
    labelSmall = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            background = Color(0xFF0A0A0B), surface = Color(0xFF141416),
            onBackground = Color(0xFFF2F2F3), onSurface = Color(0xFFF2F2F3),
            primary = Accent, onPrimary = Color.Black, outline = Color(0xFF2A2A2E),
        )
    } else {
        lightColorScheme(
            background = Color(0xFFF7F7F5), surface = Color.White,
            onBackground = Color(0xFF111111), onSurface = Color(0xFF111111),
            primary = Color(0xFF111111), onPrimary = Color.White, outline = Color(0xFFDDDDD8),
        )
    }
    MaterialTheme(colorScheme = colors, typography = AppType, content = content)
}

@Composable
private fun muted(): Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)

// ============================================================================
// 2. FORMATTING HELPERS & CALORIES & GPX
// ============================================================================

private fun f(fmt: String, vararg a: Any): String = String.format(Locale.US, fmt, *a)

fun fmtTime(s: Long): String =
    if (s >= 3600) f("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60) else f("%02d:%02d", s / 60, s % 60)

fun fmtKm(meters: Double): String = f("%.2f", meters / 1000.0)

fun fmtPace(secPerKm: Double?): String =
    if (secPerKm == null || secPerKm.isNaN() || secPerKm <= 0 || secPerKm > 3600) "--:--"
    else f("%d:%02d", (secPerKm / 60).toInt(), (secPerKm % 60).toInt())

fun avgPace(durationSec: Long, distanceM: Double): Double? =
    if (distanceM < 10) null else durationSec / (distanceM / 1000.0)

private fun fmtDate(ms: Long): String =
    SimpleDateFormat("EEE, d MMM  ·  HH:mm", Locale.getDefault()).format(Date(ms))

fun calcCalories(distanceM: Double, weightKg: Double = 70.0): Int =
    ((distanceM / 1000.0) * weightKg * 1.036).toInt()

private fun encodeRoute(p: List<GeoPoint>) = p.joinToString(";") { f("%.6f,%.6f", it.latitude, it.longitude) }

private fun decodeRoute(s: String): List<GeoPoint> =
    if (s.isBlank()) emptyList() else s.split(";").mapNotNull {
        val t = it.split(",")
        if (t.size == 2) GeoPoint(t[0].toDouble(), t[1].toDouble()) else null
    }

fun shareGpx(context: Context, run: RunEntity) {
    val routePoints = decodeRoute(run.route)
    val gpxContent = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"RunTracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <trk>\n")
        append("    <name>Run on ${fmtDate(run.startedAt)}</name>\n")
        append("    <trkseg>\n")
        for (pt in routePoints) {
            append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\" />\n")
        }
        append("    </trkseg>\n")
        append("  </trk>\n")
        append("</gpx>")
    }
    try {
        val file = File(context.cacheDir, "run_${run.id}.gpx")
        file.writeText(gpxContent)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export GPX"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// ============================================================================
// 3. DATA LAYER (Room)
// ============================================================================

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val durationSec: Long,
    val distanceM: Double,
    val route: String,
)

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY startedAt DESC")
    fun all(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun get(id: Long): RunEntity?

    @Insert
    suspend fun insert(run: RunEntity): Long

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [RunEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): RunDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            Room.databaseBuilder(c.applicationContext, AppDb::class.java, "runs.db").build().also { inst = it }
        }
    }
}

// ============================================================================
// 4. TRACKER STATE + FOREGROUND SERVICE
// ============================================================================

data class TrackerState(
    val running: Boolean = false,
    val elapsedSec: Long = 0,
    val distanceM: Double = 0.0,
    val paceSecPerKm: Double? = null,
    val points: List<GeoPoint> = emptyList(),
)

data class FinishedRun(val startedAt: Long, val durationSec: Long, val distanceM: Double, val points: List<GeoPoint>)

object TrackerStore {
    val state = MutableStateFlow(TrackerState())
    val finished = MutableStateFlow<FinishedRun?>(null)
}

const val ACTION_START = "runtracker.START"
const val ACTION_STOP = "runtracker.STOP"

fun sendTracker(ctx: Context, action: String) {
    val i = Intent(ctx, TrackingService::class.java).setAction(action)
    if (action == ACTION_START) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
}

class TrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var client: FusedLocationProviderClient
    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null

    private var running = false
    private var startedAtWall = 0L
    private var startedAtElapsed = 0L
    private var lastFix: Location? = null
    private var distance = 0.0
    private val points = mutableListOf<GeoPoint>()
    private val samples = ArrayDeque<Pair<Long, Double>>()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::handleFix)
            publish()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRun()
            ACTION_STOP -> stopRun()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startRun() {
        if (running) return
        running = true
        startedAtWall = System.currentTimeMillis()
        startedAtElapsed = SystemClock.elapsedRealtime()
        lastFix = null; distance = 0.0; points.clear(); samples.clear()
        TrackerStore.finished.value = null

        val n = buildNotification("00:00  ·  0.00 km")
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIF_ID, n)

        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "runtracker:tracking")
            .apply { acquire(6 * 60 * 60 * 1000L) }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setWaitForAccurateLocation(false)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        publish()
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                publish()
                val sec = elapsedSec()
                if (sec % 5 == 0L) updateNotification("${fmtTime(sec)}  ·  ${fmtKm(distance)} km")
            }
        }
    }

    private fun handleFix(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > 25f) return
        val prev = lastFix
        if (prev != null) {
            val d = prev.distanceTo(loc).toDouble()
            val dt = (loc.time - prev.time) / 1000.0
            if (dt <= 0) return
            if (d / dt > 12.0) return
            if (d < 2.0) return
            distance += d
        }
        lastFix = loc
        points += GeoPoint(loc.latitude, loc.longitude)
        samples.addLast(SystemClock.elapsedRealtime() to distance)
    }

    private fun currentPace(): Double? {
        val now = SystemClock.elapsedRealtime()
        while (samples.size > 1 && now - samples.first().first > 15_000) samples.removeFirst()
        if (samples.size < 2) return null
        val dd = samples.last().second - samples.first().second
        val dt = (samples.last().first - samples.first().first) / 1000.0
        if (dd < 8 || dt <= 0) return null
        return dt / (dd / 1000.0)
    }

    private fun elapsedSec() = (SystemClock.elapsedRealtime() - startedAtElapsed) / 1000

    private fun publish() {
        TrackerStore.state.value = TrackerState(true, elapsedSec(), distance, currentPace(), points.toList())
    }

    private fun stopRun() {
        if (!running) { stopSelf(); return }
        running = false
        timerJob?.cancel()
        client.removeLocationUpdates(callback)
        TrackerStore.finished.value = FinishedRun(startedAtWall, elapsedSec(), distance, points.toList())
        TrackerStore.state.value = TrackerState()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseWakeLock() { wakeLock?.takeIf { it.isHeld }?.release(); wakeLock = null }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Run tracking", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_menu_mylocation)
        .setContentTitle("Run in progress")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .build()

    private fun updateNotification(text: String) =
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification(text))

    companion object {
        private const val CHANNEL_ID = "tracking"
        private const val NOTIF_ID = 42
    }
}

// ============================================================================
// 5. VIEWMODEL
// ============================================================================

class RunVm(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()

    val runs = dao.all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val savedId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            TrackerStore.finished.filterNotNull().collect { r ->
                if (r.distanceM >= 10) {
                    savedId.value = dao.insert(
                        RunEntity(startedAt = r.startedAt, durationSec = r.durationSec,
                            distanceM = r.distanceM, route = encodeRoute(r.points))
                    )
                }
                TrackerStore.finished.value = null
            }
        }
    }

    suspend fun get(id: Long) = dao.get(id)
    fun delete(id: Long) { viewModelScope.launch { dao.delete(id) } }
}

// ============================================================================
// 6. OPENSTREETMAP COMPOSABLE VIEW (osmdroid)
// ============================================================================

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    center: GeoPoint,
    zoom: Double = 16.0,
    points: List<GeoPoint>
) {
    val context = LocalContext.current
    remember {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = context.packageName
        true
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setMultiTouchControls(true)
                setTileSource(TileSourceFactory.MAPNIK)
                controller.setZoom(zoom)
                controller.setCenter(center)
            }
        },
        update = { mapView ->
            mapView.controller.setCenter(center)
            mapView.overlays.clear()
            if (points.isNotEmpty()) {
                val polyline = OsmPolyline().apply {
                    setPoints(points)
                    outlinePaint.color = android.graphics.Color.parseColor("#D7FF3F")
                    outlinePaint.strokeWidth = 14f
                }
                mapView.overlays.add(polyline)
            }
            mapView.invalidate()
        },
        modifier = modifier
    )
}

// ============================================================================
// 7. ACTIVITY + NAVIGATION
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { Root() } }
    }
}

sealed interface Screen {
    data object Run : Screen
    data object History : Screen
    data class Detail(val id: Long) : Screen
}

@Composable
fun Root(vm: RunVm = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Run) }
    val savedId by vm.savedId.collectAsState()

    LaunchedEffect(savedId) {
        savedId?.let { screen = Screen.Detail(it); vm.savedId.value = null }
    }
    BackHandler(enabled = screen !is Screen.Run) {
        screen = if (screen is Screen.Detail) Screen.History else Screen.Run
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (screen !is Screen.Detail) BottomTabs(screen) { screen = it }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (val s = screen) {
            Screen.Run -> RunScreen(m)
            Screen.History -> HistoryScreen(vm, m) { screen = Screen.Detail(it) }
            is Screen.Detail -> DetailScreen(vm, s.id, m) { screen = Screen.History }
        }
    }
}

@Composable
private fun BottomTabs(current: Screen, onSelect: (Screen) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            .navigationBarsPadding().height(52.dp),
    ) {
        Tab("RUN", Icons.Filled.PlayArrow, current is Screen.Run, Modifier.weight(1f)) { onSelect(Screen.Run) }
        Tab("HISTORY", Icons.AutoMirrored.Filled.List, current is Screen.History, Modifier.weight(1f)) { onSelect(Screen.History) }
    }
}

@Composable
private fun Tab(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.onBackground else muted().copy(alpha = 0.35f)
    Column(modifier.fillMaxSize().clickable(onClick = onClick), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(15.dp), tint = tint)
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

// ============================================================================
// 8. SCREENS
// ============================================================================

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = true) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = muted())
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private fun hasLocationPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private fun centerOnMe(ctx: Context, onFix: (GeoPoint) -> Unit) {
    if (!hasLocationPermission(ctx)) return
    LocationServices.getFusedLocationProviderClient(ctx).lastLocation
        .addOnSuccessListener { l -> if (l != null) onFix(GeoPoint(l.latitude, l.longitude)) }
}

// ---- RUN -------------------------------------------------------------------

@Composable
fun RunScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val state by TrackerStore.state.collectAsState()
    var centerPoint by remember { mutableStateOf(GeoPoint(37.7749, -122.4194)) }
    var hasPerm by remember { mutableStateOf(hasLocationPermission(ctx)) }
    var askBattery by remember { mutableStateOf(false) }
    val prefs = remember { ctx.getSharedPreferences("rt", Context.MODE_PRIVATE) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPerm = hasLocationPermission(ctx)
        if (hasPerm) {
            sendTracker(ctx, ACTION_START)
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName) && !prefs.getBoolean("asked", false)) askBattery = true
        }
    }

    LaunchedEffect(hasPerm) {
        if (!state.running) centerOnMe(ctx) { centerPoint = it }
    }
    LaunchedEffect(state.points.size) {
        state.points.lastOrNull()?.let { centerPoint = it }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Text("RUN", style = MaterialTheme.typography.labelMedium, color = muted())
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("TIME", fmtTime(state.elapsedSec), Modifier.weight(1f))
            Stat("KM", fmtKm(state.distanceM), Modifier.weight(1f))
            Stat("PACE", fmtPace(state.paceSecPerKm), Modifier.weight(1f))
            Stat("KCAL", "${calcCalories(state.distanceM)}", Modifier.weight(0.9f))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
            OsmMapView(
                modifier = Modifier.fillMaxSize(),
                center = state.points.lastOrNull() ?: centerPoint,
                points = state.points
            )
        }
        Spacer(Modifier.height(14.dp))
        ControlButton(state.running) {
            if (state.running) sendTracker(ctx, ACTION_STOP)
            else {
                val perms = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                launcher.launch(perms.toTypedArray())
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    if (askBattery) {
        AlertDialog(
            onDismissRequest = { askBattery = false; prefs.edit().putBoolean("asked", true).apply() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Keep tracking reliable", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "Some phones stop background apps to save battery. Allow RunTracker to run unrestricted so your route is never cut short.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askBattery = false; prefs.edit().putBoolean("asked", true).apply()
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }) { Text("OPEN SETTINGS", style = MaterialTheme.typography.labelSmall) }
            },
            dismissButton = {
                TextButton(onClick = { askBattery = false; prefs.edit().putBoolean("asked", true).apply() }) {
                    Text("LATER", style = MaterialTheme.typography.labelSmall, color = muted())
                }
            },
        )
    }
}

@Composable
private fun ControlButton(running: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(shape)
            .then(if (running) Modifier.border(1.dp, cs.outline, shape) else Modifier.background(cs.primary))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running) Box(Modifier.size(9.dp).background(cs.onBackground, RoundedCornerShape(2.dp)))
        else Icon(Icons.Filled.PlayArrow, null, Modifier.size(15.dp), tint = cs.onPrimary)
        Spacer(Modifier.width(8.dp))
        Text(
            if (running) "STOP" else "START",
            style = MaterialTheme.typography.labelMedium,
            color = if (running) cs.onBackground else cs.onPrimary,
        )
    }
}

// ---- HISTORY ---------------------------------------------------------------

@Composable
fun HistoryScreen(vm: RunVm, modifier: Modifier = Modifier, onOpen: (Long) -> Unit) {
    val runs by vm.runs.collectAsState()
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(10.dp))
        Text("HISTORY", style = MaterialTheme.typography.labelMedium, color = muted())
        Spacer(Modifier.height(14.dp))
        if (runs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No runs yet. Your first one will appear here.", style = MaterialTheme.typography.bodyMedium, color = muted())
            }
        } else {
            LazyColumn {
                items(runs, key = { it.id }) { r ->
                    Column(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(vertical = 14.dp)) {
                        Text(fmtDate(r.startedAt), style = MaterialTheme.typography.bodySmall, color = muted())
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(fmtKm(r.distanceM), style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.width(4.dp))
                            Text("km", style = MaterialTheme.typography.bodySmall, color = muted(), modifier = Modifier.padding(bottom = 5.dp))
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${fmtTime(r.durationSec)}   ·   ${fmtPace(avgPace(r.durationSec, r.distanceM))} /km   ·   ${calcCalories(r.distanceM)} kcal",
                                style = MaterialTheme.typography.bodySmall, color = muted(),
                                modifier = Modifier.padding(bottom = 5.dp),
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
                }
            }
        }
    }
}

// ---- DETAIL ----------------------------------------------------------------

@Composable
fun DetailScreen(vm: RunVm, id: Long, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val run by produceState<RunEntity?>(null, id) { value = vm.get(id) }
    val r = run

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onBack), Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.size(16.dp))
            }
            Spacer(Modifier.weight(1f))
            if (r != null) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable { shareGpx(ctx, r) },
                    Alignment.Center,
                ) { Icon(Icons.Filled.Share, "Export GPX", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onBackground) }
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable { vm.delete(id); onBack() },
                Alignment.Center,
            ) { Icon(Icons.Filled.Delete, "Delete", Modifier.size(15.dp), tint = muted()) }
        }
        if (r == null) return@Column

        val route = remember(r.route) { decodeRoute(r.route) }
        val center = route.firstOrNull() ?: GeoPoint(37.7749, -122.4194)

        Text(fmtDate(r.startedAt), style = MaterialTheme.typography.bodySmall, color = muted())
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("KM", fmtKm(r.distanceM), Modifier.weight(1f))
            Stat("TIME", fmtTime(r.durationSec), Modifier.weight(1f))
            Stat("AVG PACE", fmtPace(avgPace(r.durationSec, r.distanceM)), Modifier.weight(1f))
            Stat("CALORIES", "${calcCalories(r.distanceM)} kcal", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
            OsmMapView(
                modifier = Modifier.fillMaxSize(),
                center = center,
                points = route
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}
