package com.thanhtaxi.smartdrive

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.*
import com.thanhtaxi.smartdrive.data.RoadPack
import com.thanhtaxi.smartdrive.data.RoadPackRepository
import com.thanhtaxi.smartdrive.domain.*
import com.thanhtaxi.smartdrive.media.MediaAccessService
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A real Android head-unit / phone Activity, NOT an Android Auto host surface.
 * On unknown road data, it does not claim any legal speed limit.
 */
class MainActivity : Activity(), LocationListener {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private lateinit var speedLabel: TextView
    private lateinit var limitLabel: TextView
    private lateinit var gpsLabel: TextView
    private lateinit var signLabel: TextView
    private lateinit var mediaLabel: TextView
    private lateinit var qrImage: ImageView
    private lateinit var qrNotice: TextView
    private var lastLocation: Location? = null
    private var lastFixElapsedMs = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val engine = SpeedLimitEngine()
    private val compliance = SpeedCompliance()
    private val alertDebouncer = SpeedAlertDebouncer()
    private var tone: ToneGenerator? = null
    private var displayedImageUri: String? = null
    private val matcher = GeoMatcher()
    private val signEngine = TrafficSignEngine()
    private var roadPack: RoadPack? = null
    private var roadLines: List<RoadLine> = emptyList()
    private var roadIndex = RoadSpatialIndex(emptyList())
    private val signMarkers = mutableListOf<Marker>()
    private var vehicleMarker: Marker? = null
    private var vehicleIcon: Icon? = null
    private var lastMarkerLocation: Location? = null
    private var mediaController: MediaController? = null
    private val prefs by lazy { getSharedPreferences("taxi", MODE_PRIVATE) }
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val tick = object : Runnable {
        override fun run() { refreshData(); handler.postDelayed(this, 2500) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        buildDashboard(savedInstanceState)
    }
    private fun cardBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(26, 31, 39)); cornerRadius = 14f
    }
    private fun label(text: String, size: Float = 18f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.WHITE); setPadding(14, 10, 14, 8)
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun limitCircle(hasValue: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (hasValue) Color.WHITE else Color.rgb(42, 50, 57))
        setStroke(dp(5), if (hasValue) Color.RED else Color.GRAY)
    }
    private fun positionIcon(): Icon {
        vehicleIcon?.let { return it }
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(24f, 24f, 18f, paint)
        paint.color = Color.rgb(26, 120, 245)
        canvas.drawCircle(24f, 24f, 14f, paint)
        vehicleIcon = IconFactory.getInstance(this).fromBitmap(bitmap)
        return vehicleIcon!!
    }
    private fun buildDashboard(state: Bundle?) {
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val root = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 13, 18))
            setPadding(6, 6, 6, 6)
        }
        val mapPane = FrameLayout(this).apply { background = cardBackground() }
        mapView = MapView(this)
        mapPane.addView(mapView, FrameLayout.LayoutParams(-1,-1))
        mapView.onCreate(state)
        mapView.getMapAsync { m ->
            map = m
            m.setStyle(prefs.getString("style", "https://demotiles.maplibre.org/style.json")!!) {
                lastMarkerLocation = null
                vehicleMarker = null
                renderNearbySigns(lastLocation)
                lastLocation?.let { follow(it) }
            }
            m.cameraPosition = CameraPosition.Builder().target(LatLng(21.033, 105.85)).zoom(12.0).build()
            lastLocation?.let { follow(it) }
        }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(0xC9121720.toInt())
        }
        val speedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        speedLabel = label("-- km/h", 28f).apply { setTypeface(null, Typeface.BOLD) }
        limitLabel = label("--", 28f).apply {
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = limitCircle(false)
            setPadding(0,0,0,0)
        }
        speedRow.addView(speedLabel)
        speedRow.addView(limitLabel, LinearLayout.LayoutParams(dp(78),dp(78)).apply {
            leftMargin = dp(10)
        })
        info.addView(speedRow)
        gpsLabel = label("Đang chờ tín hiệu GPS", 14f); info.addView(gpsLabel)
        signLabel = label("Giới hạn: Chưa xác minh", 13f).apply {
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        info.addView(signLabel)
        mapPane.addView(info, FrameLayout.LayoutParams(-2,-2, Gravity.TOP or Gravity.START))
        if (landscape) {
            val settings = Button(this).apply {
                text = "⚙"
                contentDescription = "Cài đặt khi xe đã đỗ"
                setOnClickListener {
                    if (isStopped()) startActivity(Intent(this@MainActivity, SettingsActivity::class.java).putExtra("verifiedStopped", true))
                    else Toast.makeText(this@MainActivity, "Chỉ cài đặt khi xe dừng", Toast.LENGTH_SHORT).show()
                }
            }
            val width = (68 * resources.displayMetrics.density).toInt()
            val height = (55 * resources.displayMetrics.density).toInt()
            mapPane.addView(settings, FrameLayout.LayoutParams(width,height,Gravity.TOP or Gravity.RIGHT))
        }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val calendarCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = cardBackground(); setPadding(6,6,6,6)
        }
        calendarCard.addView(label("LỊCH VIỆT NAM", 19f))
        val calendarText = label("Đang tải ngày...", 17f)
        calendarCard.addView(calendarText)
        val tz = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        val fmt = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN")).apply { timeZone = tz }
        val cal = java.util.Calendar.getInstance(tz)
        val ld = com.thanhtaxi.smartdrive.calendar.LunarCalendar.solarToLunar(
            cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.MONTH)+1, cal.get(java.util.Calendar.YEAR))
        calendarText.text = "${fmt.format(Date())}\nÂm lịch: ${ld.day}/${ld.month}/${ld.year}${if(ld.leap) " nhuận" else ""}"
        val qrCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = cardBackground(); setPadding(6,6,6,6) }
        qrCard.addView(label("THANH TOÁN / QR", 18f))
        qrImage = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        qrNotice = label("Chọn ảnh QR trong Cài đặt (khi xe đỗ).", 13f)
        qrCard.addView(qrImage, LinearLayout.LayoutParams(-1, 0, 1f)); qrCard.addView(qrNotice)
        val mediaCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = cardBackground(); setPadding(6,6,6,6) }
        mediaLabel = label("Media: chưa được cấp quyền / không có phiên phát", 14f)
        mediaCard.addView(mediaLabel)
        val mediaBtns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun mediaButton(text: String, op: (MediaController) -> Unit) {
            mediaBtns.addView(Button(this).apply {
                this.text = text
                setOnClickListener { mediaController?.let(op) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        mediaButton("◀◀") { it.transportControls.skipToPrevious() }
        mediaButton("▶/Ⅱ") {
            if (it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING)
                it.transportControls.pause() else it.transportControls.play()
        }
        mediaButton("▶▶") { it.transportControls.skipToNext() }
        mediaCard.addView(mediaBtns)
        right.addView(calendarCard, LinearLayout.LayoutParams(-1,0,0.25f).apply { bottomMargin=5 })
        right.addView(qrCard, LinearLayout.LayoutParams(-1,0,0.55f).apply { bottomMargin=5 })
        right.addView(mediaCard, LinearLayout.LayoutParams(-1,0,0.20f))
        if (landscape) {
            root.addView(mapPane, LinearLayout.LayoutParams(0,-1,0.70f).apply { rightMargin=5 })
            root.addView(right, LinearLayout.LayoutParams(0,-1,0.30f))
        } else {
            root.addView(mapPane, LinearLayout.LayoutParams(-1,0,0.62f))
            val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            section.addView(right, LinearLayout.LayoutParams(-1,0,1f))
            val settings = Button(this).apply {
                text = "Cài đặt / nhập dữ liệu (chỉ khi xe đỗ)"
                setOnClickListener { if (isStopped()) startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                else Toast.makeText(this@MainActivity,"Dừng xe trước khi cấu hình",Toast.LENGTH_SHORT).show() }
            }
            section.addView(settings)
            root.addView(section, LinearLayout.LayoutParams(-1,0,0.38f))
        }
        setContentView(root)
    }
    private fun isStopped(): Boolean {
        val loc = lastLocation ?: return false
        return SystemClock.elapsedRealtime() - lastFixElapsedMs < 5000L &&
            loc.hasAccuracy() && loc.accuracy <= 15f && loc.hasSpeed() && loc.speed < 0.8f
    }
    private fun requestGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 9)
        } else startGps()
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 9 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startGps()
    }
    private fun startGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this) }
        catch (e: Exception) { gpsLabel.text = "GPS không sẵn sàng: ${e.message}" }
    }
    override fun onLocationChanged(location: Location) {
        // Android can deliver old cached GPS fixes; never refresh their age to "now".
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        if (ageNanos < -1_000_000_000L || ageNanos > 8_000_000_000L) return
        lastLocation = location
        lastFixElapsedMs = location.elapsedRealtimeNanos / 1_000_000L
        follow(location)
        renderNearbySigns(location)
        refreshData()
    }
    private fun follow(location: Location) {
        val m = map ?: return
        if (m.style == null) return
        if (location.hasAccuracy() && location.accuracy <= 40f) {
            vehicleMarker?.let { m.removeMarker(it) }
            vehicleMarker = m.addMarker(MarkerOptions()
                .position(LatLng(location.latitude, location.longitude))
                .title("GPS: vị trí ước tính của xe")
                .icon(positionIcon()))
        }
        m.cameraPosition = CameraPosition.Builder()
            .target(LatLng(location.latitude, location.longitude))
            .zoom(16.0).bearing(if (location.hasBearing()) location.bearing.toDouble() else 0.0)
            .build()
    }
    private fun refreshData() {
        val loc = lastLocation
        val fresh = loc != null && SystemClock.elapsedRealtime()-lastFixElapsedMs < 8000L
        val accurate = fresh && loc != null && loc.hasAccuracy() && loc.accuracy <= 20f
        val speedReliable = accurate && loc != null && loc.hasSpeed() &&
            (!loc.hasSpeedAccuracy() || loc.speedAccuracyMetersPerSecond <= 2.5f)
        val speed = if (speedReliable && loc != null) loc.speed.toDouble() * 3.6 else null
        val pack = roadPack
        val match = if (accurate && loc != null && pack != null) matcher.match(
            GeoFix(loc.latitude, loc.longitude, loc.accuracy.toDouble(),
                if (loc.hasBearing()) loc.bearing.toDouble() else null,
                if (loc.hasSpeed()) loc.speed.toDouble() else null),
            roadIndex
        ) else null
        val segment = pack?.segments?.firstOrNull { it.id == match?.segmentId }
        val baseContext = RoadContext(segment?.id, segment?.direction, null,
            segment?.zone ?: PopulationZone.UNKNOWN, segment?.kind ?: RoadKind.UNKNOWN,
            matchedConfidently = match != null && segment != null, gpsReliable = accurate)
        val now = System.currentTimeMillis()
        val decision = signEngine.evaluate(baseContext, match?.progressMeters ?: 0.0,
            loc?.accuracy?.toDouble() ?: 100.0, pack?.trafficSigns.orEmpty(), now)
        val context = baseContext.copy(zone = decision.zone)
        val limit = if (decision.conflict)
            LimitResult(null, LimitStatus.CONFLICT, "traffic-signs", "Biển báo cùng vị trí mâu thuẫn")
        else engine.resolve(context, pack?.signs.orEmpty() + listOfNotNull(decision.speedEvidence), now)
        val level = compliance.grade(SpeedObservation(speed, speedReliable), limit, prefs.getInt("tolerance",3))
        speedLabel.text = if (speed == null) "-- km/h" else "${speed.toInt()} km/h"
        limitLabel.text = limit.kph?.toString() ?: "--"
        limitLabel.background = limitCircle(limit.kph != null)
        val next = decision.upcoming.joinToString(" • ") {
            val code = when(it.kind) { SignKind.R_420 -> "R.420"; SignKind.R_421 -> "R.421";
                SignKind.P_127 -> "P.127 ${it.kph ?: "--"}"; SignKind.END_SPEED -> "Hết hạn chế" }
            "${it.metres}m: $code"
        }
        val evidenceLabel = when (limit.status) {
            LimitStatus.VERIFIED -> "Theo gói khảo sát (CHƯA kiểm chứng độc lập)"
            LimitStatus.ESTIMATED_FROM_LAW -> "Ước tính mặc định; xem biển thực tế"
            LimitStatus.CONFLICT -> "Dữ liệu mâu thuẫn, không được suy đoán"
            LimitStatus.UNKNOWN -> "Chưa đủ cơ sở xác định"
        }
        signLabel.text = (if (limit.kph == null) "Giới hạn: -- ($evidenceLabel)"
            else "$evidenceLabel: ${limit.note}") +
            if (next.isNotBlank()) "\nSắp tới: $next" else ""
        gpsLabel.text = when {
            !fresh -> "Mất GPS / tín hiệu cũ"
            !accurate -> "GPS yếu: ${loc?.accuracy?.toInt()}m"
            segment == null -> "GPS tốt • Chưa có đoạn đường được xác minh"
            else -> "GPS: ${loc!!.accuracy.toInt()}m • Đoạn: ${segment.id}"
        }
        limitLabel.setTextColor(if (limit.kph != null) Color.BLACK else Color.WHITE)
        speedLabel.setTextColor(if (level == SpeedSeverity.OVER || level == SpeedSeverity.SERIOUS)
            Color.RED else Color.WHITE)
        if (prefs.getBoolean("soundAlert", false) &&
            alertDebouncer.shouldAlert(level, SystemClock.elapsedRealtime())) {
            try {
                if (tone == null) tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            } catch (_: RuntimeException) { /* Some head units do not expose a notification stream */ }
        }
        refreshMedia()
        refreshQr()
    }
    private fun renderNearbySigns(loc: Location?) {
        val m = map ?: return
        if (loc == null) return
        val previous = lastMarkerLocation
        if (previous != null && previous.distanceTo(loc) < 120f) return
        if (m.style == null) return
        m.removeAnnotations(signMarkers)
        signMarkers.clear()
        val nearby = roadPack?.trafficSigns.orEmpty().asSequence()
            .filter { it.reviewed && it.confidence >= 0.9 &&
                kotlin.math.abs(it.latitude - loc.latitude) < 0.015 &&
                kotlin.math.abs(it.longitude - loc.longitude) < 0.020 }
            .mapNotNull { sign ->
                val d = FloatArray(1)
                Location.distanceBetween(loc.latitude, loc.longitude, sign.latitude, sign.longitude, d)
                if (d[0] <= 800f) sign to d[0] else null
            }.sortedBy { it.second }.take(25).toList()
        nearby.forEach { (sign, _) ->
            val title = when(sign.kind) {
                SignKind.R_420 -> "R.420 – Bắt đầu khu đông dân cư"
                SignKind.R_421 -> "R.421 – Hết khu đông dân cư"
                SignKind.P_127 -> "P.127 – Giới hạn ${sign.kph} km/h"
                SignKind.END_SPEED -> "Kết thúc hạn chế (đã khảo sát)"
            }
            signMarkers.add(m.addMarker(MarkerOptions()
                .position(LatLng(sign.latitude,sign.longitude))
                .title(title)
                .snippet("Nguồn: gói khảo sát người dùng; cần kiểm chứng tại hiện trường")))
        }
        lastMarkerLocation = Location(loc)
    }
    private fun refreshQr() {
        val uri = prefs.getString("paymentImage", null)
        if (uri == null) {
            if (displayedImageUri != null) qrImage.setImageDrawable(null)
            displayedImageUri = null
            qrNotice.visibility = View.VISIBLE
            return
        }
        if (uri == displayedImageUri) return
        try {
            qrImage.setImageURI(Uri.parse(uri))
            displayedImageUri = uri
            qrNotice.visibility = View.GONE
        } catch (_: Exception) {
            displayedImageUri = null
            qrNotice.text = "Không thể đọc ảnh, hãy chọn lại"
            qrNotice.visibility = View.VISIBLE
        }
    }
    private fun refreshMedia() {
        try {
            val mgr = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = mgr.getActiveSessions(ComponentName(this, MediaAccessService::class.java))
            mediaController = controllers.firstOrNull()
            val c = mediaController
            mediaLabel.text = if (c == null) "Media: chưa cấp quyền hoặc chưa có phiên đang phát"
                else "${c.packageName}: ${c.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Đang phát"}"
        } catch (_: SecurityException) {
            mediaController = null; mediaLabel.text = "Media: cần cấp quyền Notification Listener"
        }
    }
    override fun onResume() {
        super.onResume(); mapView.onResume()
        roadPack = RoadPackRepository(this).load()
        roadLines = roadPack?.segments.orEmpty().map {
            RoadLine(it.id,it.startLat,it.startLon,it.endLat,it.endLon,it.reviewed)
        }
        roadIndex = RoadSpatialIndex(roadLines)
        lastMarkerLocation = null
        renderNearbySigns(lastLocation)
        requestGps(); handler.removeCallbacks(tick); handler.post(tick)
    }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onPause() {
        handler.removeCallbacks(tick); locationManager.removeUpdates(this)
        mapView.onPause(); super.onPause()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onDestroy() {
        map?.removeAnnotations(signMarkers)
        signMarkers.clear()
        vehicleMarker?.let { map?.removeMarker(it) }
        vehicleMarker = null
        tone?.release()
        tone = null
        mapView.onDestroy()
        super.onDestroy()
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { mapView.onSaveInstanceState(outState); super.onSaveInstanceState(outState) }
}
