package com.thanhtaxi.smartdrive

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import com.thanhtaxi.smartdrive.data.RoadMatcher
import com.thanhtaxi.smartdrive.data.RoadPackRepository
import com.thanhtaxi.smartdrive.domain.*
import com.thanhtaxi.smartdrive.media.MediaAccessService
import org.maplibre.android.MapLibre
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
    private val matcher = RoadMatcher()
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
        requestGps()
    }
    private fun cardBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(26, 31, 39)); cornerRadius = 14f
    }
    private fun label(text: String, size: Float = 18f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.WHITE); setPadding(14, 10, 14, 8)
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
            m.setStyle(prefs.getString("style", "https://demotiles.maplibre.org/style.json")!!)
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
        limitLabel = label("GH: --", 28f).apply {
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(255, 208, 66))
        }
        speedRow.addView(speedLabel); speedRow.addView(limitLabel)
        info.addView(speedRow)
        gpsLabel = label("Đang chờ tín hiệu GPS", 14f); info.addView(gpsLabel)
        signLabel = label("Giới hạn: Chưa xác minh", 13f); info.addView(signLabel)
        mapPane.addView(info, FrameLayout.LayoutParams(-2,-2, Gravity.TOP or Gravity.START))
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
        val loc = lastLocation ?: return true
        return !loc.hasSpeed() || loc.speed < 0.8f
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
        lastLocation = location
        lastFixElapsedMs = SystemClock.elapsedRealtime()
        follow(location)
        refreshData()
    }
    private fun follow(location: Location) {
        map?.cameraPosition = CameraPosition.Builder()
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
        val pack = RoadPackRepository(this).load()
        val segment = if (accurate && loc != null && pack != null) matcher.match(loc, pack.segments) else null
        val context = RoadContext(segment?.id, segment?.direction, null,
            segment?.zone ?: PopulationZone.UNKNOWN, segment?.kind ?: RoadKind.UNKNOWN,
            matchedConfidently = segment != null, gpsReliable = accurate)
        val limit = engine.resolve(context, pack?.signs.orEmpty(), System.currentTimeMillis())
        val level = compliance.grade(SpeedObservation(speed, speedReliable), limit, prefs.getInt("tolerance",3))
        speedLabel.text = if (speed == null) "-- km/h" else "${speed.toInt()} km/h"
        limitLabel.text = "GH: ${limit.kph?.toString() ?: "--"}"
        signLabel.text = if (limit.kph == null) "Giới hạn: Chưa xác minh (${limit.note})"
            else "${limit.status.name}: ${limit.note}"
        gpsLabel.text = when {
            !fresh -> "Mất GPS / tín hiệu cũ"
            !accurate -> "GPS yếu: ${loc?.accuracy?.toInt()}m"
            segment == null -> "GPS tốt • Chưa có đoạn đường được xác minh"
            else -> "GPS: ${loc!!.accuracy.toInt()}m • Đoạn: ${segment.id}"
        }
        limitLabel.setTextColor(if (level == SpeedSeverity.OVER || level == SpeedSeverity.SERIOUS)
            Color.RED else Color.rgb(255, 208, 66))
        refreshMedia()
        refreshQr()
    }
    private fun refreshQr() {
        val uri = prefs.getString("paymentImage", null)
        if (uri == null) { qrNotice.visibility = View.VISIBLE; return }
        try {
            qrImage.setImageURI(Uri.parse(uri))
            qrNotice.visibility = View.GONE
        } catch (_: Exception) { qrNotice.text = "Không thể đọc ảnh, hãy chọn lại"; qrNotice.visibility = View.VISIBLE }
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
        startGps(); handler.removeCallbacks(tick); handler.post(tick)
    }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onPause() {
        handler.removeCallbacks(tick); locationManager.removeUpdates(this)
        mapView.onPause(); super.onPause()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { mapView.onSaveInstanceState(outState); super.onSaveInstanceState(outState) }
}
