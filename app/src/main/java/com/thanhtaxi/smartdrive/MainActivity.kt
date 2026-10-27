package com.thanhtaxi.smartdrive

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import com.thanhtaxi.smartdrive.navigation.ActiveNavigationEngine
import com.thanhtaxi.smartdrive.navigation.GuidancePromptPolicy
import com.thanhtaxi.smartdrive.navigation.LocalVietnameseGuidance
import com.thanhtaxi.smartdrive.navigation.NavigationFix
import com.thanhtaxi.smartdrive.navigation.NavigationPhase
import com.thanhtaxi.smartdrive.navigation.NavigationUpdate
import com.thanhtaxi.smartdrive.navigation.RoutePlan
import com.thanhtaxi.smartdrive.navigation.RouteAccessContext
import com.thanhtaxi.smartdrive.navigation.RoutingVehicle
import com.thanhtaxi.smartdrive.navigation.RoutePreviewInstructions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import com.thanhtaxi.smartdrive.offline.MapStyleDefaults
import com.thanhtaxi.smartdrive.offline.PmtilesArchiveStore
import com.thanhtaxi.smartdrive.offline.PmtilesHeader
import com.thanhtaxi.smartdrive.offline.PmtilesRasterStyle
import com.thanhtaxi.smartdrive.offline.PmtilesVectorStyle
import com.thanhtaxi.smartdrive.offline.OfflineRoadLabelOverlay
import com.thanhtaxi.smartdrive.offline.OfflineRoadLabelSampler
import org.maplibre.android.maps.Style
import java.util.concurrent.Executors
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
    private lateinit var pipTrustLabel: TextView
    private lateinit var gpsLabel: TextView
    private lateinit var signLabel: TextView
    private lateinit var mediaLabel: TextView
    private lateinit var qrImage: ImageView
    private lateinit var qrNotice: TextView
    private lateinit var mapStatusLabel: TextView
    private val mapWorker = Executors.newSingleThreadExecutor()
    private var selectedStyleKey: String? = null
    private var currentStyleEpoch = 0
    private var offlineHeader: PmtilesHeader? = null
    private val followCamera = FreeDriveCameraState()
    private lateinit var followButton: Button
    private lateinit var roadLabels: OfflineRoadLabelOverlay
    private var labelSampler: OfflineRoadLabelSampler?=null

    private var lastLocation: Location? = null
    private var lastFixElapsedMs = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val engine = SpeedLimitEngine()
    private val compliance = SpeedCompliance()
    private val alertDebouncer = SpeedAlertDebouncer()
    private var tone: ToneGenerator? = null
    private var displayedImageUri: String? = null
    private val matcher = GeoMatcher()
    private val stableMatcher = StableRoadMatcher()
    private val topologyGate = RoadTopologyGate()
    private var stableMatch: RoadMatch? = null
    private val navWorker=Executors.newSingleThreadExecutor()
    private var navGeneration=0
    private var activeNavigation: ActiveNavigationEngine?=null
    private var navRoute: RoutePlan?=null
    private var navUpdate=NavigationUpdate(NavigationPhase.IDLE,"Chưa chọn hành trình")
    private var navPolyline: Polyline?=null
    private var voiceRequested=false
    private var offlineVoice: LocalVietnameseGuidance?=null
    private val promptPolicy=GuidancePromptPolicy()
    private lateinit var navPanel: LinearLayout
    private lateinit var navText: TextView
    private lateinit var navStop: Button
    private lateinit var navAccept: Button
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
    private fun limitCircle(trust: SpeedPipTrust) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (trust == SpeedPipTrust.AUTHENTICATED) Color.WHITE else Color.rgb(42, 50, 57))
        setStroke(dp(5), when(trust) {
            SpeedPipTrust.AUTHENTICATED -> Color.RED
            SpeedPipTrust.SURVEY_ONLY, SpeedPipTrust.ESTIMATED -> Color.rgb(255,183,75)
            SpeedPipTrust.CONFLICT -> Color.rgb(220,91,66)
            SpeedPipTrust.UNKNOWN -> Color.GRAY
        })
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
        roadLabels=OfflineRoadLabelOverlay(this)
        mapPane.addView(roadLabels,FrameLayout.LayoutParams(-1,-1))
        mapStatusLabel = label("Bản đồ: đang tải cấu hình…", 12f).apply {
            setBackgroundColor(0xC9121720.toInt())
            maxLines = 2
        }
        mapPane.addView(mapStatusLabel, FrameLayout.LayoutParams(-2,-2,
            Gravity.BOTTOM or Gravity.RIGHT).apply {
            bottomMargin=dp(36)
            rightMargin=dp(6)
            width=dp(if(landscape) 310 else 285)
        })
        mapView.onCreate(state)
        mapView.addOnDidFailLoadingMapListener { reason ->
            android.util.Log.w("ThanhTaxiMap", "Map style/tile failed: " + reason)
            mapStatusLabel.text = "Lỗi tải bản đồ: kiểm tra Internet, style URL và tile server. PMTiles cần gói bao phủ vị trí."
        }
        mapView.getMapAsync { readyMap ->
            map = readyMap
            labelSampler=OfflineRoadLabelSampler(readyMap,roadLabels)
            readyMap.addOnCameraIdleListener {
                labelSampler?.schedule()
            }
            readyMap.addOnMapClickListener { coordinate ->
                // Road name inspection is only active when parked; tile properties
                // are unverified OSM labels and never become speed-limit evidence.
                if(!isStopped() || offlineHeader?.tileType != 1) {
                    false
                } else {
                    val hits=runCatching {
                        readyMap.queryRenderedFeatures(
                            readyMap.projection.toScreenLocation(coordinate),"road-lines")
                    }.getOrDefault(emptyList())
                    val props=hits.firstOrNull()?.properties()?.entrySet()?.associate { entry ->
                        entry.key to (if(entry.value.isJsonPrimitive) entry.value.asString else null)
                    }.orEmpty()
                    val msg=if(hits.isEmpty()) "Chạm sát nét đường để xem thông tin OSM."
                        else com.thanhtaxi.smartdrive.offline.RoadFeatureSummary.bestDisplay(props)
                    Toast.makeText(this,msg,Toast.LENGTH_LONG).show()
                    true
                }
            }
            readyMap.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    followCamera.userPanned()
                    updateFollowButton()
                }
            }
            loadSelectedMap()
        }
        followButton = Button(this).apply {
            text = "◎ Theo xe"
            contentDescription = "Bật hoặc tắt bám vị trí xe trên bản đồ"
            setOnClickListener {
                if (followCamera.following) {
                    followCamera.userPanned()
                } else {
                    followCamera.resume()
                    lastLocation?.let { follow(it) }
                }
                updateFollowButton()
            }
        }
        mapPane.addView(followButton, FrameLayout.LayoutParams(dp(118),dp(50),
            Gravity.BOTTOM or Gravity.LEFT).apply {
            bottomMargin = dp(38)
            leftMargin = dp(5)
        })
        navPanel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(0xDF172330.toInt())
            setPadding(dp(8),dp(6),dp(8),dp(6))
            visibility=View.GONE
        }
        navText=label("Chưa chọn tuyến khảo sát thử.",14f).apply {
            maxLines=3
            setTextColor(Color.WHITE)
        }
        navPanel.addView(navText)
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        navStop=Button(this).apply {
            text="Dừng tuyến"
            contentDescription="Dừng theo dõi hành trình thử"
            setOnClickListener { stopActiveNavigation() }
        }
        navAccept=Button(this).apply {
            text="Chọn tuyến khác (khi đỗ)"
            visibility=View.GONE
            setOnClickListener { acceptSuggestedRoute() }
        }
        actions.addView(navStop,LinearLayout.LayoutParams(0,dp(44),1f))
        actions.addView(navAccept,LinearLayout.LayoutParams(0,dp(44),1.5f))
        navPanel.addView(actions)
        mapPane.addView(navPanel,FrameLayout.LayoutParams(-1,-2,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin=dp(94)
            leftMargin=dp(6)
            rightMargin=dp(6)
        })
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
            background = limitCircle(SpeedPipTrust.UNKNOWN)
            setPadding(0,0,0,0)
        }
        speedRow.addView(speedLabel)
        speedRow.addView(limitLabel, LinearLayout.LayoutParams(dp(78),dp(78)).apply {
            leftMargin = dp(10)
        })
        info.addView(speedRow)
        pipTrustLabel = label("KHÔNG ĐỦ DỮ LIỆU", 13f).apply {
            setTypeface(null,Typeface.BOLD)
            setTextColor(Color.LTGRAY)
            maxLines=2
        }
        info.addView(pipTrustLabel)
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
                setOnClickListener { openSettingsWhenParked() }
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
        qrCard.addView(qrImage, if(landscape)
            LinearLayout.LayoutParams(-1,0,1f) else
            LinearLayout.LayoutParams(-1,dp(155)))
        qrCard.addView(qrNotice)
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
        if(landscape) {
            right.addView(calendarCard, LinearLayout.LayoutParams(-1,0,0.25f).apply { bottomMargin=5 })
            right.addView(qrCard, LinearLayout.LayoutParams(-1,0,0.55f).apply { bottomMargin=5 })
            right.addView(mediaCard, LinearLayout.LayoutParams(-1,0,0.20f))
        } else {
            right.addView(calendarCard,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(5) })
            right.addView(qrCard,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(5) })
            right.addView(mediaCard,LinearLayout.LayoutParams(-1,-2))
        }
        if (landscape) {
            root.addView(mapPane, LinearLayout.LayoutParams(0,-1,0.70f).apply { rightMargin=5 })
            root.addView(right, LinearLayout.LayoutParams(0,-1,0.30f))
        } else {
            root.addView(mapPane, LinearLayout.LayoutParams(-1,0,0.62f))
            val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            section.addView(ScrollView(this).apply { addView(right) },
                LinearLayout.LayoutParams(-1,0,1f))
            val settings = Button(this).apply {
                text = "Cài đặt / nhập dữ liệu (chỉ khi xe đỗ)"
                setOnClickListener { openSettingsWhenParked() }
            }
            section.addView(settings)
            root.addView(section, LinearLayout.LayoutParams(-1,0,0.38f))
        }
        setContentView(root)
    }
    private fun updateFollowButton() {
        if (::followButton.isInitialized)
            followButton.text = if (followCamera.following) "◎ Theo xe" else "◎ Tới xe"
    }
    private fun loadSelectedMap() {
        val m = map ?: return
        val mode = prefs.getString("mapSourceMode","online") ?: "online"
        val sha = prefs.getString("pmtilesActiveSha","") ?: ""
        val url = MapStyleDefaults.effective(prefs.getString("style", null))
        val vectorLayer = prefs.getString("pmtilesVectorLayer","") ?: ""
        val key = when(mode) {
            "pmtiles_raster" -> "pmtiles:raster:$sha"
            "pmtiles_vector" -> "pmtiles:vector:$sha:$vectorLayer"
            else -> "online:$url"
        }
        if(key==selectedStyleKey) return
        selectedStyleKey=key
        navPolyline=null
        val epoch=++currentStyleEpoch
        offlineHeader=null
        labelSampler?.setEnabled(false)
        if(mode!="pmtiles_raster" && mode!="pmtiles_vector") {
            mapStatusLabel.text = "Bản đồ online: cần Internet và tile server đang hoạt động."
            m.setStyle(url) {
                if(epoch!=currentStyleEpoch) return@setStyle
                lastMarkerLocation=null
                vehicleMarker=null
                renderNearbySigns(lastLocation)
                drawNavigationRoute()
                lastLocation?.let { follow(it) } ?: run {
                    m.cameraPosition=CameraPosition.Builder().target(LatLng(21.28,106.19)).zoom(12.0).build()
                }
            }
            return
        }
        mapStatusLabel.text = "Đang kiểm tra SHA-256 gói PMTiles cục bộ…"
        mapWorker.execute {
            val archive=PmtilesArchiveStore(applicationContext).loadVerified()
            runOnUiThread {
                if(isFinishing || isDestroyed || epoch!=currentStyleEpoch) return@runOnUiThread
                val correctArchive=archive!=null && archive.sha256==sha
                val validRaster=mode=="pmtiles_raster" && archive?.header?.isRaster==true
                val validVector=mode=="pmtiles_vector" && archive?.header?.tileType==1 &&
                    archive.vectorInfo?.layers?.contains(vectorLayer)==true
                if(!correctArchive || !(validRaster || validVector)) {
                    mapStatusLabel.text = "PMTiles không hợp lệ, hoặc source-layer vector chưa tồn tại. Hãy chọn lại trong Cài đặt."
                    m.setStyle(Style.Builder().fromJson(
                        """{"version":8,"sources":{},"layers":[]}"""))
                    return@runOnUiThread
                }
                try {
                    val selected=archive.header
                    val generatedStyle = if(validVector)
                        PmtilesVectorStyle.make(archive!!.tileUri,vectorLayer,archive.attribution,
                            archive.header.minZoom.coerceAtMost(22),
                            archive.header.maxZoom.coerceAtMost(22))
                        else PmtilesRasterStyle.make(archive!!)
                    m.setStyle(Style.Builder().fromJson(generatedStyle)) {
                        if(epoch!=currentStyleEpoch) return@setStyle
                        offlineHeader=selected
                        drawNavigationRoute()
                        labelSampler?.setEnabled(validVector &&
                            prefs.getBoolean("showOfflineRoadLabels",true))
                        handler.postDelayed({ labelSampler?.schedule() },2200L)
                        vehicleMarker=null
                        lastMarkerLocation=null
                        val fix=lastLocation
                        val inside=fix!=null &&
                            fix.latitude in selected.minLat..selected.maxLat &&
                            fix.longitude in selected.minLon..selected.maxLon
                        m.cameraPosition=CameraPosition.Builder()
                            .target(if(inside) LatLng(fix!!.latitude,fix.longitude)
                                else LatLng(selected.centerLat,selected.centerLon))
                            .zoom(selected.centerZoom.toDouble().coerceIn(
                                selected.minZoom.toDouble(),selected.maxZoom.toDouble())).build()
                        val typeLabel=if(validVector) "vector OSM (chưa xác minh)" else "raster"
                        mapStatusLabel.text=if(inside)
                            "PMTiles $typeLabel cục bộ: kiểm tra tile thực trên thiết bị."
                        else "GPS ngoài vùng PMTiles $typeLabel. Đang xem tâm vùng lưu."
                        renderNearbySigns(lastLocation)
                        if(inside && fix!=null) follow(fix)
                    }
                } catch(e:Exception) {
                    mapStatusLabel.text="Không mở được PMTiles: "+(e.message ?: "Sai cấu hình style")
                }
            }
        }
    }
    private fun openSettingsWhenParked() {
        val launch = {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra("verifiedStopped", true))
        }
        if (isStopped()) {
            launch()
            return
        }
        // If fresh GPS explicitly indicates substantial movement, disallow manual
        // override. Otherwise the fix may be missing/stale/inaccurate while parked.
        val loc = lastLocation
        val fresh = SystemClock.elapsedRealtime() - lastFixElapsedMs < 8000L
        if (fresh && loc != null && loc.hasSpeed() && loc.speed >= 2f) {
            Toast.makeText(this, "Xe đang di chuyển: chỉ cài đặt sau khi đỗ xe.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xe đã đỗ")
            .setMessage("GPS không xác nhận được xe đang dừng. " +
                "Chỉ tiếp tục nếu xe đã đỗ an toàn. Không thao tác khi đang lái.")
            .setNegativeButton("Hủy",null)
            .setPositiveButton("Tôi đã đỗ xe") { _, _ -> launch() }
            .show()
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
        val observedMatch = if(location.hasAccuracy() && location.accuracy <= 20f)
            matcher.match(GeoFix(location.latitude,location.longitude,location.accuracy.toDouble(),
                if(location.hasBearing()) location.bearing.toDouble() else null,
                if(location.hasSpeed()) location.speed.toDouble() else null),roadIndex)
            else null
        stableMatch = topologyGate.accept(stableMatcher.observe(observedMatch,lastFixElapsedMs))
        updateActiveNavigation(location)
        follow(location)
        renderNearbySigns(location)
        refreshData()
    }
    private fun follow(location: Location) {
        val m = map ?: return
        if (m.style == null) return
        val local=offlineHeader
        if(local!=null && (location.latitude !in local.minLat..local.maxLat ||
                location.longitude !in local.minLon..local.maxLon)) {
            mapStatusLabel.text="GPS ngoài phạm vi PMTiles: xem tâm vùng bản đồ đã nhập."
            return
        }
        val gpsGood = location.hasAccuracy() && location.accuracy <= 40f
        if (gpsGood) {
            vehicleMarker?.let { m.removeMarker(it) }
            vehicleMarker = m.addMarker(MarkerOptions()
                .position(LatLng(location.latitude, location.longitude))
                .title("GPS: vị trí ước tính của xe")
                .icon(positionIcon()))
        }
        if (!followCamera.shouldCenter(gpsGood, true)) return
        m.cameraPosition = CameraPosition.Builder()
            .target(LatLng(location.latitude, location.longitude))
            .zoom(if(local==null) 16.0 else 16.0.coerceIn(
                local.minZoom.toDouble(),local.maxZoom.toDouble()))
            .bearing(if (location.hasBearing()) location.bearing.toDouble() else 0.0)
            .build()
    }
    private fun refreshData() {
        val loc = lastLocation
        val fresh = loc != null && SystemClock.elapsedRealtime()-lastFixElapsedMs < 8000L
        if(!fresh && activeNavigation!=null &&
            navUpdate.phase!=NavigationPhase.GPS_UNCERTAIN &&
            navUpdate.phase!=NavigationPhase.ARRIVED) {
            offlineVoice?.stop()
            promptPolicy.reset()
            val engine=activeNavigation
            val epoch=navGeneration
            if(engine!=null) navWorker.execute {
                val result=engine.suspendForUncertainGps()
                runOnUiThread {
                    if(epoch==navGeneration && activeNavigation===engine && !isDestroyed) {
                        navUpdate=result
                        showNavigationState()
                    }
                }
            }
        }
        val accurate = fresh && loc != null && loc.hasAccuracy() && loc.accuracy <= 20f
        val speedReliable = accurate && loc != null && loc.hasSpeed() &&
            (!loc.hasSpeedAccuracy() || loc.speedAccuracyMetersPerSecond <= 2.5f)
        val speed = if (speedReliable && loc != null) loc.speed.toDouble() * 3.6 else null
        val pack = roadPack
        // UI ticks cannot manufacture new fix evidence.
        val match = if(accurate && pack!=null) stableMatch else null
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
        val pip = SpeedPipPolicy.present(limit,level)
        speedLabel.text = if (speed == null) "-- km/h" else "${speed.toInt()} km/h"
        limitLabel.text = pip.value
        limitLabel.background = limitCircle(pip.trust)
        pipTrustLabel.text = pip.badge
        pipTrustLabel.setTextColor(when(pip.trust) {
            SpeedPipTrust.AUTHENTICATED -> Color.GREEN
            SpeedPipTrust.SURVEY_ONLY,SpeedPipTrust.ESTIMATED -> Color.rgb(255,196,85)
            SpeedPipTrust.CONFLICT -> Color.rgb(255,124,103)
            SpeedPipTrust.UNKNOWN -> Color.LTGRAY
        })
        limitLabel.contentDescription = "Giới hạn: ${pip.value} km/h, ${pip.badge}"
        pipTrustLabel.contentDescription = "${pip.badge}. ${pip.detail}"
        val next = decision.upcoming.joinToString(" • ") {
            val code = when(it.kind) { SignKind.R_420 -> "R.420"; SignKind.R_421 -> "R.421";
                SignKind.P_127 -> "P.127 ${it.kph ?: "--"}"; SignKind.END_SPEED -> "Hết hạn chế" }
            "${it.metres}m: $code"
        }
        val evidenceLabel = when (limit.status) {
            LimitStatus.VERIFIED -> "Theo nguồn đã xác thực"
            LimitStatus.SURVEY_REPORTED -> "Dữ liệu khảo sát người dùng, CHƯA kiểm chứng độc lập"
            LimitStatus.ESTIMATED_FROM_LAW -> "Ước tính mặc định; xem biển thực tế"
            LimitStatus.CONFLICT -> "Dữ liệu mâu thuẫn, không được suy đoán"
            LimitStatus.UNKNOWN -> "Chưa đủ cơ sở xác định"
        }
        signLabel.text = (if (pip.value == "--") "Giới hạn: -- ($evidenceLabel)"
            else "$evidenceLabel: ${pip.detail}") +
            if (next.isNotBlank()) "\nBiển khảo sát sắp tới (CHƯA xác thực): $next" else ""
        gpsLabel.text = when {
            !fresh -> "Mất GPS / tín hiệu cũ"
            !accurate -> "GPS yếu: ${loc?.accuracy?.toInt()}m"
            segment == null && topologyGate.rejectedTransition ->
                "GPS: hai đoạn không có lượt rẽ khảo sát được xác nhận"
            segment == null && stableMatcher.isConfirming ->
                "GPS: đang xác nhận đoạn đường qua nhiều mẫu"
            segment == null -> "GPS tốt • Chưa có đoạn đường được xác minh"
            else -> "GPS: ${loc!!.accuracy.toInt()}m • Đoạn: ${segment.id}"
        }
        limitLabel.setTextColor(if (pip.trust == SpeedPipTrust.AUTHENTICATED)
            Color.BLACK else Color.WHITE)
        val over = level == SpeedSeverity.OVER || level == SpeedSeverity.SERIOUS
        speedLabel.setTextColor(when {
            !over -> Color.WHITE
            pip.trust == SpeedPipTrust.AUTHENTICATED -> Color.RED
            pip.trust == SpeedPipTrust.SURVEY_ONLY ||
                pip.trust == SpeedPipTrust.ESTIMATED -> Color.rgb(255,196,85)
            else -> Color.WHITE
        })
        val toneGrade = if (pip.soundEligible && prefs.getBoolean("soundAlert", false))
            level else SpeedSeverity.UNKNOWN
        if (alertDebouncer.shouldAlert(toneGrade, SystemClock.elapsedRealtime())) {
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
            .filter { it.reviewed && !it.sourceRef.isNullOrBlank() &&
                it.confidence >= 0.9 &&
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
                .snippet("Nguồn khảo sát: ${sign.sourceRef ?: "chưa xác định"}; cần kiểm chứng tại hiện trường")))
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
    private fun receiveActiveNavigationIntent(source: Intent?) {
        val routeNodes=source?.getStringArrayListExtra("navigationNodes")
        val routeEdges=source?.getStringArrayListExtra("navigationEdges")
        if(routeNodes==null || routeEdges==null) return
        val metres=source.getDoubleExtra("navigationMeters",Double.NaN)
        val routeVehicle=source.getStringExtra("routeVehicle")
        val routeDay=source.getIntExtra("routeDay",-1)
        val routeMinute=source.getIntExtra("routeMinute",-1)
        val routeContext=runCatching {
            if(routeVehicle==null) null else RouteAccessContext(
                RoutingVehicle.valueOf(routeVehicle),routeDay,routeMinute)
        }.getOrNull()
        source.removeExtra("routeVehicle")
        source.removeExtra("routeDay")
        source.removeExtra("routeMinute")
        val userOptedIn=source.getBooleanExtra("navigationVoiceOptIn",false)
        source.removeExtra("navigationVoiceOptIn")
        source.removeExtra("navigationNodes")
        source.removeExtra("navigationEdges")
        source.removeExtra("navigationMeters")
        // Never trust extras from an exported Activity without graph validation.
        val graph=roadPack?.routeGraph
        if(graph==null || routeEdges.isEmpty() || routeEdges.size>20_000 ||
            routeNodes.size!=routeEdges.size+1 || !metres.isFinite() ||
            metres<=0.0 || metres>1.0e8) {
            stopActiveNavigation()
            Toast.makeText(this,"Không có gói tuyến khảo sát hợp lệ.",Toast.LENGTH_LONG).show()
            return
        }
        val generation=++navGeneration
        voiceRequested=false
        offlineVoice?.close()
        offlineVoice=null
        promptPolicy.reset()
        navPanel.visibility=View.VISIBLE
        navText.text="Đang xác thực và nạp tuyến có hướng…"
        val refreshedContext=routeContext?.let {
            RouteAccessContext.atVietnamTime(it.vehicle,System.currentTimeMillis())
        }
        val copy=RoutePlan(routeNodes.toList(),routeEdges.toList(),
            metres,refreshedContext)
        navWorker.execute {
            val nav=runCatching {
                val total=copy.edgeIds.sumOf { id ->
                    val e=requireNotNull(graph.edge(id))
                    require(graph.isEdgeAllowed(id,copy.accessContext))
                    e.metres
                }
                require(kotlin.math.abs(total-copy.metres)<0.25)
                RoutePreviewInstructions.generate(graph,copy)
                ActiveNavigationEngine(graph).also { it.start(copy) }
            }
            runOnUiThread {
                if(isFinishing || isDestroyed || generation!=navGeneration) return@runOnUiThread
                if(nav.isFailure) {
                    navPanel.visibility=View.GONE
                    Toast.makeText(this,"Tuyến bị từ chối: ${nav.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG).show()
                } else {
                    activeNavigation=nav.getOrThrow()
                    navRoute=copy
                    navUpdate=activeNavigation!!.current
                    voiceRequested=userOptedIn
                    if(voiceRequested) offlineVoice=LocalVietnameseGuidance(this)
                    showNavigationState()
                    drawNavigationRoute()
                    Toast.makeText(this,"Đang theo tuyến thử; tuân thủ biển và đường thực tế.",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateActiveNavigation(fix: Location) {
        val engine=activeNavigation ?:return
        val generation=navGeneration
        val sample=NavigationFix(fix.latitude,fix.longitude,
            fix.accuracy.toDouble().takeIf { fix.hasAccuracy() } ?: Double.NaN,
            fix.elapsedRealtimeNanos/1_000_000L,
            fix.bearing.toDouble().takeIf { fix.hasBearing() },
            fix.speed.toDouble().takeIf { fix.hasSpeed() },
            fix.isFromMockProvider)
        navWorker.execute {
            val next=engine.observe(sample,SystemClock.elapsedRealtime(),
                System.currentTimeMillis())
            runOnUiThread {
                if(isFinishing || isDestroyed || generation!=navGeneration ||
                    activeNavigation!==engine) return@runOnUiThread
                navUpdate=next
                showNavigationState()
                if(voiceRequested && offlineVoice?.available==true &&
                    next.phase==NavigationPhase.ACTIVE) {
                    val routeId=navRoute?.edgeIds?.joinToString("|").orEmpty()
                    val prompt=promptPolicy.next(next,routeId)
                    if(prompt!=null) offlineVoice?.speak(prompt)
                } else if(next.phase!=NavigationPhase.ACTIVE) {
                    offlineVoice?.stop()
                }
            }
        }
    }

    private fun showNavigationState() {
        if(!::navPanel.isInitialized) return
        navPanel.visibility=if(activeNavigation==null)View.GONE else View.VISIBLE
        val state=navUpdate
        val distance=state.remainingMeters?.let {
            if(it>=1000.0) String.format(Locale.ROOT,"%.1f km",it/1000.0)
            else "${it.toInt()} m"
        } ?: "—"
        val next=state.nextManeuver?.take(100) ?: state.message
        val time=state.etaSeconds?.let {
            if(it>=3600) "${it/3600}h ${(it%3600)/60}p"
            else "${(it+59)/60} phút"
        } ?: "—"
        val stage=if(state.phase==NavigationPhase.ACTIVE &&
            state.nextManeuverIndex!=null)
            " · Bước ${state.nextManeuverIndex+1}/${state.totalManeuvers}"
            else ""
        val voiceState=if(voiceRequested)
            if(offlineVoice?.available==true) " · Loa thử: BẬT"
            else " · Loa offline: không khả dụng"
            else " · Loa: TẮT"
        navText.text="ĐIỀU HƯỚNG THỬ • ${state.phase.name} · Còn $distance" +
            " · ETA ≈ $time$stage\n$next$voiceState"
        navText.setTextColor(when(state.phase) {
            NavigationPhase.ACTIVE -> Color.WHITE
            NavigationPhase.ARRIVED -> Color.rgb(156,240,170)
            else -> Color.rgb(255,197,115)
        })
        // Never offer a route-changing interaction while GPS indicates movement.
        navAccept.visibility=if(state.alternative!=null && isStopped())
            View.VISIBLE else View.GONE
    }

    private fun drawNavigationRoute() {
        val m=map ?:return
        if(m.style==null)return
        navPolyline?.let { runCatching { m.removePolyline(it) } }
        navPolyline=null
        val plan=navRoute ?:return
        val graph=roadPack?.routeGraph ?:return
        val coords=plan.nodeIds.mapNotNull { graph.nodes[it] }
            .map { LatLng(it.lat,it.lon) }
        if(coords.size!=plan.nodeIds.size || coords.size<2)return
        navPolyline=m.addPolyline(PolylineOptions().addAll(coords)
            .color(Color.rgb(255,203,59)).width(6f))
    }

    private fun stopActiveNavigation() {
        ++navGeneration
        offlineVoice?.close()
        offlineVoice=null
        voiceRequested=false
        promptPolicy.reset()
        activeNavigation=null
        navRoute=null
        navUpdate=NavigationUpdate(NavigationPhase.IDLE,"Đã dừng hành trình")
        if(::navPanel.isInitialized)navPanel.visibility=View.GONE
        drawNavigationRoute()
    }

    private fun acceptSuggestedRoute() {
        if(!isStopped()) {
            Toast.makeText(this,"Chỉ chấp nhận tuyến thay thế khi xe đã đỗ.",
                Toast.LENGTH_SHORT).show()
            return
        }
        val engine=activeNavigation ?:return
        val generation=navGeneration
        navWorker.execute {
            val updated=engine.acceptProposedRoute(parkedConfirmed=true)
            val plan=engine.activeRoute
            runOnUiThread {
                if(generation!=navGeneration || activeNavigation!==engine ||
                    isFinishing || isDestroyed) return@runOnUiThread
                navRoute=plan
                navUpdate=updated
                offlineVoice?.stop()
                promptPolicy.reset()
                showNavigationState()
                drawNavigationRoute()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        roadPack=RoadPackRepository(this).load()
        receiveActiveNavigationIntent(intent)
    }

    override fun onResume() {
        super.onResume(); mapView.onResume()
        roadPack = RoadPackRepository(this).load()
        roadLines = roadPack?.segments.orEmpty().map {
            RoadLine(it.id,it.startLat,it.startLon,it.endLat,it.endLon,it.reviewed)
        }
        roadIndex = RoadSpatialIndex(roadLines)
        receiveActiveNavigationIntent(intent)
        stableMatcher.reset()
        topologyGate.reset(roadPack?.routeGraph)
        stableMatch=null
        if(offlineHeader?.tileType==1) labelSampler?.setEnabled(
            prefs.getBoolean("showOfflineRoadLabels",true))
        loadSelectedMap()
        lastMarkerLocation = null
        renderNearbySigns(lastLocation)
        requestGps(); handler.removeCallbacks(tick); handler.post(tick)
    }
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onPause() {
        offlineVoice?.stop()
        promptPolicy.reset()
        // Clear location confidence when leaving the foreground.
        val nav=activeNavigation
        val generation=navGeneration
        if(nav!=null) navWorker.execute {
            val result=nav.suspendForUncertainGps()
            runOnUiThread {
                if(generation==navGeneration && activeNavigation===nav &&
                    !isDestroyed) {
                    navUpdate=result
                    showNavigationState()
                }
            }
        }
        labelSampler?.stop()
        handler.removeCallbacks(tick); locationManager.removeUpdates(this)
        stableMatcher.reset()
        topologyGate.reset()
        stableMatch=null
        mapView.onPause(); super.onPause()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onDestroy() {
        offlineVoice?.close()
        offlineVoice=null
        labelSampler?.stop()
        currentStyleEpoch++
        navWorker.shutdownNow()
        mapWorker.shutdownNow()
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
