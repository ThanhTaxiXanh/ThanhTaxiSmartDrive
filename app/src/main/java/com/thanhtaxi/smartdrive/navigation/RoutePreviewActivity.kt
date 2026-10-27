package com.thanhtaxi.smartdrive.navigation

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.content.Intent
import com.thanhtaxi.smartdrive.MainActivity
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock
import android.os.Bundle
import android.widget.*
import com.thanhtaxi.smartdrive.data.RoadPackRepository
import com.thanhtaxi.smartdrive.offline.MapStyleDefaults
import com.thanhtaxi.smartdrive.offline.PmtilesArchiveStore
import com.thanhtaxi.smartdrive.offline.PmtilesRasterStyle
import com.thanhtaxi.smartdrive.offline.PmtilesVectorStyle
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale
import java.util.concurrent.Executors

/**
 * PARKED ONLY. Displays surveyed edge geometry, never issues commands to a
 * moving vehicle. Data provenance is self-declared, not legally authenticated.
 */
class RoutePreviewActivity : Activity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap?=null
    private var previewLine: Polyline?=null
    private var latestPlan: RoutePlan?=null
    private var selectedDestinationId: String?=null
    private var pendingAlternative: RoutePlan?=null
    private val deviationMonitor=RouteDeviationMonitor()
    private val background=Executors.newSingleThreadExecutor()
    private val graph by lazy { RoadPackRepository(this).load()?.routeGraph }
    private val prefs by lazy { getSharedPreferences("taxi",MODE_PRIVATE) }
    private lateinit var routeStatus: TextView
    private lateinit var mapStatus: TextView
    private lateinit var detailButton: Button
    private var destroyed=false
    private val locationManager by lazy {
        getSystemService(LOCATION_SERVICE) as LocationManager
    }
    private var liveGpsListener: LocationListener?=null
    private var lastValidParkedFix: ParkedGpsSample?=null
    private lateinit var liveGpsButton: Button
    private lateinit var originFromGpsButton: Button
    private lateinit var liveGpsStatus: TextView
    private lateinit var simulationButton: Button
    private lateinit var startInput: AutoCompleteTextView
    private val liveDeviation=RouteDeviationMonitor()
    private var lastRealFixTimeMs: Long?=null
    private val GPS_PERMISSION_REQUEST = 1412

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!intent.getBooleanExtra("verifiedStopped",false)) {
            Toast.makeText(this,"Chỉ xem trước khi xe đã đỗ",Toast.LENGTH_LONG).show()
            finish()
            return
        }
        MapLibre.getInstance(this)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(12,12,12,12)
        }
        // Keep the map visible even on a compact phone with many debug controls.
        // Controls remain available in an independently scrollable top pane.
        val controls=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
        }
        val controlsScroll=ScrollView(this).apply {
            isFillViewport=false
            addView(controls)
        }
        val controlsFraction=if(resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE) 0.48 else 0.54
        root.addView(controlsScroll,LinearLayout.LayoutParams(-1,
            (resources.displayMetrics.heightPixels*controlsFraction).toInt()))
        controls.addView(TextView(this).apply {
            text="XEM TRƯỚC TUYẾN OFFLINE — KHÔNG DÙNG ĐỂ LÁI XE"
            textSize=19f
        })
        root.addView(TextView(this).apply {
            text="Tuyến lấy từ đồ thị khảo sát do người dùng nhập. Chưa đối chiếu cấm rẽ, " +
                "làn, chiều đường thực tế, cầu vượt hoặc hiệu lực quy định giao thông."
        })
        val g=graph
        if (g==null || g.nodes.isEmpty()) {
            controls.addView(TextView(this).apply {
                text="Chưa có graph trong road pack. Nhập routing.nodes, routing.edges, " +
                    "routing.allowedTurns có sourceRef để tính tuyến thử."
                textSize=16f
            })
            setContentView(root)
            return
        }
        val search=OfflineDestinationIndex(g)
        fun choose(label:String): AutoCompleteTextView {
            val adapter=object:ArrayAdapter<String>(
                this,android.R.layout.simple_dropdown_item_1line,
                mutableListOf<String>()) {
                fun showResults(found:List<String>) {
                    clear()
                    addAll(found)
                    notifyDataSetChanged()
                }
                private val offlineFilter=object:android.widget.Filter() {
                    override fun performFiltering(query:CharSequence?):FilterResults {
                        val matches=search.suggestions(query?.toString().orEmpty(),12)
                        return FilterResults().apply {
                            values=matches
                            count=matches.size
                        }
                    }
                    override fun publishResults(query:CharSequence?,results:FilterResults?) {
                        @Suppress("UNCHECKED_CAST")
                        val found=results?.values as? List<String> ?: emptyList()
                        showResults(found)
                    }
                }
                override fun getFilter():android.widget.Filter=offlineFilter
            }
            return AutoCompleteTextView(this).apply {
                hint=label
                threshold=1
                setSingleLine(true)
                setAdapter(adapter)
            }
        }
        val start=choose("ID/tên điểm khảo sát hoặc tọa độ xuất phát")
        startInput=start
        val finish=choose("ID/tên điểm khảo sát hoặc tọa độ đến")
        // POI may only seed a destination coordinate for PARKED survey preview.
        // Resolving it still requires an existing node within the graph tolerance.
        intent.getStringExtra("previewDestinationCoordinate")?.let { coordinate ->
            val pair = coordinate.split(',')
            if (pair.size == 2) {
                val lat = pair[0].toDoubleOrNull()
                val lon = pair[1].toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0)
                    finish.setText("$lat,$lon")
            }
        }
        controls.addView(start)
        controls.addView(finish)
        val vehicleChoices=RoutingVehicle.entries.map { it.name }
        val vehiclePicker=Spinner(this).apply {
            adapter=ArrayAdapter(this@RoutePreviewActivity,
                android.R.layout.simple_spinner_dropdown_item,vehicleChoices)
            setSelection(vehicleChoices.indexOf(RoutingVehicle.TAXI.name))
        }
        controls.addView(TextView(this).apply {
            text="Loại phương tiện khảo sát giả lập (không phải xác minh đăng ký xe):"
        })
        controls.addView(vehiclePicker)
        val laneLabels=listOf("Làn chưa rõ (mặc định)")+
            (1..6).map { "Làn $it (giả lập)" }
        val lanePicker=Spinner(this).apply {
            adapter=ArrayAdapter(this@RoutePreviewActivity,
                android.R.layout.simple_spinner_dropdown_item,laneLabels)
        }
        controls.addView(TextView(this).apply {
            text="Làn khảo sát cho phép thử lọc tuyến; GPS không thể xác minh làn:"
        })
        controls.addView(lanePicker)
        routeStatus=TextView(this).apply {
            text="Offline: ${g.nodes.size} nút + ${g.destinations.size} điểm tự khai. " +
                "Tìm ID, tên/biệt danh có/không dấu, hoặc WGS84 ≤75m. " +
                "Chưa có dữ liệu địa chỉ quốc gia."
            textSize=14f
            maxLines=3
        }
        controls.addView(routeStatus)
        val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val run=Button(this).apply { text="Tính tuyến thử" }
        detailButton=Button(this).apply {
            text="Xem các điểm đổi hướng"
            isEnabled=false
            setOnClickListener {
                val plan=latestPlan ?:return@setOnClickListener
                val summary=runCatching { RoutePreviewInstructions.asParkedText(g,plan) }
                    .getOrElse { "Không thể đọc các nút khảo sát: "+it.message }
                val text=TextView(this@RoutePreviewActivity).apply {
                    this.text=summary
                    setPadding(24,20,24,20)
                    textSize=16f
                }
                val scroll=ScrollView(this@RoutePreviewActivity).apply { addView(text) }
                AlertDialog.Builder(this@RoutePreviewActivity)
                    .setTitle("Mô tả HÌNH HỌC — không phải chỉ dẫn lái")
                    .setView(scroll).setPositiveButton("Đóng",null).show()
            }
        }
        buttons.addView(run,LinearLayout.LayoutParams(0,-2,1f))
        buttons.addView(detailButton,LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(buttons)
        val navigateButton=Button(this).apply {
            text="Bắt đầu theo tuyến thử (GPS thật)"
            isEnabled=false
        }
        controls.addView(navigateButton)
        val optionalSpeech=CheckBox(this).apply {
            text="Phát mô tả điểm đổi hướng bằng tiếng Việt offline (thử nghiệm)"
            isChecked=false
        }
        controls.addView(optionalSpeech)
        controls.addView(TextView(this).apply {
            text="Mặc định TẮT. Chỉ phát mô tả hình học, không phải chỉ dẫn rẽ hợp pháp. " +
                "Nếu máy không có giọng tiếng Việt cục bộ, sẽ im lặng."
            textSize=12f
        })
        navigateButton.setOnClickListener {
            val p=latestPlan ?:return@setOnClickListener
            if(p.edgeIds.isEmpty()) return@setOnClickListener
            if(g.hasLaneScopedRules(p) || p.accessContext?.vehicle!=RoutingVehicle.TAXI) {
                Toast.makeText(this,
                    "Tuyến có giới hạn theo làn chưa kiểm chứng hoặc không phải taxi: " +
                    "chỉ được xem mô phỏng khi xe đỗ.",Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            // The prototype's GPS speed reading is a guard, not vehicle gear state.
            val fix=lastValidParkedFix
            if(fix!=null && ParkedGpsQuality.check(fix,SystemClock.elapsedRealtime())==
                ParkedGpsDecision.MOVING_STOP) {
                Toast.makeText(this,"Xe có dấu hiệu di chuyển: không thao tác khởi hành.",Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Bắt đầu theo dõi tuyến THỬ NGHIỆM?")
                .setMessage("Chỉ khởi tạo khi đã đỗ xe an toàn. Tên đường, lượt rẽ, " +
                    "chiều đường và hạn chế phương tiện chưa được chứng nhận. " +
                    "Màn hình sẽ theo dõi tiến độ GPS, tạm ẩn hướng dẫn khi không chắc chắn; " +
                    "không tự chuyển sang đường khác. Nếu bật giọng nói, app chỉ " +
                    "mô tả hình học khảo sát, không ra lệnh rẽ hợp pháp. " +
                    "Không thao tác khi đang lái.")
                .setNegativeButton("Hủy",null)
                .setPositiveButton("Tôi đang đỗ, bắt đầu") { _,_ ->
                    stopParkedGps("Đã chuyển sang Dashboard.")
                    startActivity(Intent(this,MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putStringArrayListExtra("navigationNodes",ArrayList(p.nodeIds))
                        putStringArrayListExtra("navigationEdges",ArrayList(p.edgeIds))
                        putExtra("navigationMeters",p.metres)
                        putExtra("routeDepartureEpochMs",
                            p.schedule.firstOrNull()?.enterEpochMs ?: -1L)
                        putExtra("routeLane",p.accessContext?.lane?:-1)
                        p.accessContext?.let { context ->
                            putExtra("routeVehicle",context.vehicle.name)
                            putExtra("routeDay",context.dayOfWeek)
                            putExtra("routeMinute",context.minuteOfDay)
                        }
                        putExtra("navigationVoiceOptIn",optionalSpeech.isChecked)
                    })
                    finish()
                }.show()
        }
        val testCoordinates=EditText(this).apply {
            hint="Tọa độ mô phỏng GPS: vĩ độ,kinh độ (ví dụ 21.0,106.0)"
            setSingleLine(true)
            inputType=android.text.InputType.TYPE_CLASS_TEXT
        }
        controls.addView(testCoordinates)
        val simulatorButtons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val simulate=Button(this).apply {
            text="Mô phỏng 3 GPS"
            isEnabled=false
        }
        simulationButton=simulate
        val acceptAlternative=Button(this).apply {
            text="Xem tuyến khác"
            isEnabled=false
        }
        simulatorButtons.addView(simulate,LinearLayout.LayoutParams(0,-2,1f))
        simulatorButtons.addView(acceptAlternative,LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(simulatorButtons)
        val simulatedStatus=TextView(this).apply {
            text="Mô phỏng lệch tuyến hoàn toàn offline khi xe đỗ. Không đọc GPS thật, không phát chỉ dẫn."
            textSize=13f
            maxLines=4
        }
        controls.addView(simulatedStatus)
        val liveControls=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        liveGpsButton=Button(this).apply {
            text="Bật GPS thật (khi đỗ)"
            setOnClickListener {
                if(liveGpsListener!=null) stopParkedGps("Đã dừng đọc GPS thật.")
                else startParkedGps()
            }
        }
        originFromGpsButton=Button(this).apply {
            text="Điểm đi = GPS"
            isEnabled=false
            setOnClickListener {
                val fix=lastValidParkedFix ?:return@setOnClickListener
                if(ParkedGpsQuality.check(fix,SystemClock.elapsedRealtime()) !=
                    ParkedGpsDecision.VALID_STATIONARY) {
                    liveGpsStatus.text="Vị trí GPS đã cũ hoặc không còn đủ tin cậy."
                    originFromGpsButton.isEnabled=false
                    return@setOnClickListener
                }
                val source=SurveyPointSearch.resolve(g,
                    "${fix.latitude},${fix.longitude}",75.0)
                if(source.nodeId==null) {
                    liveGpsStatus.text="Không thể chọn điểm đi: ${source.detail}"
                } else {
                    startInput.setText(source.nodeId)
                    liveGpsStatus.text="Đã chọn nút ${source.nodeId} gần GPS đang đỗ. " +
                        "Hãy xác nhận thủ công trước khi tính tuyến."
                }
            }
        }
        liveControls.addView(liveGpsButton,LinearLayout.LayoutParams(0,-2,1f))
        liveControls.addView(originFromGpsButton,LinearLayout.LayoutParams(0,-2,1f))
        controls.addView(liveControls)
        liveGpsStatus=TextView(this).apply {
            text="GPS thật: mặc định TẮT. Chỉ dùng khi xe đỗ an toàn. " +
                "Đọc vị trí tại chỗ, không ghi lịch sử, không tự dẫn đường."
            textSize=13f
            maxLines=5
        }
        controls.addView(liveGpsStatus)
        acceptAlternative.setOnClickListener {
            val alternative=pendingAlternative ?:return@setOnClickListener
            latestPlan=alternative
            deviationMonitor.reset()
            pendingAlternative=null
            acceptAlternative.isEnabled=false
            routeStatus.text="Đang xem tuyến khảo sát thay thế theo dữ liệu nhập. Chưa xác minh đường thực."
            showRoute(alternative)
        }
        simulate.setOnClickListener {
            if(liveGpsListener!=null) {
                simulatedStatus.text="Tắt GPS thật trước khi dùng dữ liệu mô phỏng."
                return@setOnClickListener
            }
            val plan=latestPlan ?:return@setOnClickListener
            val destination=selectedDestinationId ?:return@setOnClickListener
            val tokens=testCoordinates.text.toString().split(",")
            val lat=tokens.getOrNull(0)?.trim()?.toDoubleOrNull()
            val lon=tokens.getOrNull(1)?.trim()?.toDoubleOrNull()
            if(tokens.size!=2 || lat==null || lon==null || !lat.isFinite() || !lon.isFinite() ||
                lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                simulatedStatus.text="Chỉ nhập tọa độ WGS84 'vĩ độ,kinh độ' hợp lệ."
                return@setOnClickListener
            }
            deviationMonitor.reset()
            // Three DISTINCT timestamps, but generated solely from the parked
            // simulation button. This NEVER counts as live GPS evidence.
            var result=RouteDeviationResult(RouteDeviationState.UNKNOWN,null)
            for(i in 0..2) {
                result=deviationMonitor.observe(g,plan,lat,lon,3.0,1000L+i*1000L)
            }
            pendingAlternative=null
            acceptAlternative.isEnabled=false
            val distance=if(result.distanceMeters==null) "?" else
                String.format(Locale.ROOT,"%.0f",result.distanceMeters)
            simulatedStatus.text=when(result.state) {
                RouteDeviationState.ON_ROUTE ->
                    "MÔ PHỎNG: tọa độ gần tuyến ($distance m); không phải xác nhận GPS thật."
                RouteDeviationState.SUSPECTED ->
                    "MÔ PHỎNG: nghi lệch tuyến ($distance m), cần thêm mẫu nhất quán."
                RouteDeviationState.UNKNOWN ->
                    "MÔ PHỎNG: dữ liệu không hợp lệ hoặc thiếu cạnh được duyệt."
                RouteDeviationState.OFF_ROUTE -> {
                    val (lookup, alternative)=SurveyReroutePreview.candidate(
                        g,lat,lon,destination,100.0,plan.accessContext)
                    pendingAlternative=alternative?.takeIf { it.edgeIds.isNotEmpty() }
                    acceptAlternative.isEnabled=pendingAlternative!=null
                    if(pendingAlternative!=null)
                        "MÔ PHỎNG: lệch tuyến $distance m; có tuyến tham khảo từ nút " +
                            "${lookup.nodeId} (chỉ xem khi xe đỗ)."
                    else "MÔ PHỎNG: lệch tuyến $distance m; không có đường thay thế " +
                        "được khai báo từ nút gần đó. ${lookup.detail}"
                }
            }
        }
        mapStatus=TextView(this).apply {
            text="Đang nạp nền bản đồ đã chọn…"
            textSize=13f
            maxLines=2
        }
        root.addView(mapStatus)
        mapView=MapView(this)
        root.addView(mapView,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        mapView.onCreate(savedInstanceState)
        mapView.addOnDidFailLoadingMapListener { reason ->
            mapStatus.text="Không tải được style hoặc tile: ${reason.take(160)}"
        }
        mapView.getMapAsync { loaded ->
            map=loaded
            loadBackgroundMap(loaded)
        }
        run.setOnClickListener {
            if(liveGpsListener!=null) stopParkedGps("Đã dừng GPS để chọn tuyến mới.")
            val fromResult=search.resolve(start.text.toString())
            val toResult=search.resolve(finish.text.toString())
            val origin=fromResult.nodeId
            val destination=toResult.nodeId
            if(origin==null || destination==null) {
                routeStatus.text="Không nhận diện được nút: ${fromResult.detail} / ${toResult.detail}"
                return@setOnClickListener
            }
            deviationMonitor.reset()
            pendingAlternative=null
            selectedDestinationId=destination
            acceptAlternative.isEnabled=false
            simulate.isEnabled=false
            simulatedStatus.text="Chỉ thử mô phỏng sau khi tính được tuyến khảo sát."
            run.isEnabled=false
            detailButton.isEnabled=false
            navigateButton.isEnabled=false
            routeStatus.text="Đang tính đồ thị có hướng cục bộ…"
            val selectedVehicle=RoutingVehicle.valueOf(
                vehicleChoices[vehiclePicker.selectedItemPosition])
            val selectedLane=lanePicker.selectedItemPosition.takeIf { it>0 }
            val departureMs=System.currentTimeMillis()
            background.execute {
                val result=runCatching {
                    val route=g.routeTimed(origin,destination,selectedVehicle,
                        departureMs,selectedLane)
                    if (route != null) RoutePreviewInstructions.generate(g,route)
                    route
                }
                runOnUiThread {
                    if(destroyed || isFinishing)return@runOnUiThread
                    run.isEnabled=true
                    latestPlan=result.getOrNull()
                    val p=latestPlan
                    detailButton.isEnabled=p!=null
                    simulate.isEnabled=p!=null && p.edgeIds.isNotEmpty() &&
                        liveGpsListener==null
                    navigateButton.isEnabled=p!=null && p.edgeIds.isNotEmpty() &&
                        p.accessContext?.vehicle==RoutingVehicle.TAXI &&
                        !g.hasLaneScopedRules(p)
                    routeStatus.text=when {
                        result.isFailure -> "Tuyến không hợp lệ: ${result.exceptionOrNull()?.message}"
                        p==null -> "Không có tuyến trên đồ thị đã nhập. Không kết luận đường thật bị chặn."
                        else -> {
                            val eta=p.schedule.lastOrNull()?.let { item ->
                                val seconds=((item.exitEpochMs-departureMs)/1000)
                                    .coerceAtLeast(0)
                                " · ETA mô hình ≈ ${(seconds+59)/60} phút"
                            } ?: ""
                            "Xem trước: ${p.edgeIds.size} cạnh, " +
                                String.format(Locale.ROOT,"%.2f km",p.metres/1000.0) +
                                "$eta. Mở 'Xem các điểm đổi hướng' để xem giờ vào " +
                                "từng đoạn. KHÔNG phải chỉ dẫn giao thông."
                        }
                    }
                    showRoute(p)
                }
            }
        }
    }

    /**
     * Foreground-only stationary diagnostic. No tracking history or navigation.
     * The operator must separately confirm the car is parked; low GPS speed
     * does not prove the vehicle is parked or authorize driver interaction.
     */
    private fun startParkedGps() {
        if(destroyed || isFinishing || !::liveGpsStatus.isInitialized) return
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            liveGpsStatus.text="Cần cấp quyền vị trí chính xác để xem GPS thật khi xe đỗ."
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),GPS_PERMISSION_REQUEST)
            return
        }
        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            liveGpsStatus.text="GPS chưa bật. Hãy bật vị trí chính xác trong Cài đặt Android."
            return
        }
        lastValidParkedFix=null
        lastRealFixTimeMs=null
        liveDeviation.reset()
        originFromGpsButton.isEnabled=false
        val listener=object: LocationListener {
            override fun onLocationChanged(location: Location) {
                if(destroyed || liveGpsListener!==this) return
                val fix=ParkedGpsSample(location.latitude,location.longitude,
                    location.accuracy.toDouble().takeIf { location.hasAccuracy() },
                    location.speed.toDouble().takeIf { location.hasSpeed() },
                    location.elapsedRealtimeNanos / 1_000_000L,
                    location.isFromMockProvider)
                val decision=ParkedGpsQuality.check(fix,SystemClock.elapsedRealtime())
                if(decision==ParkedGpsDecision.MOVING_STOP) {
                    stopParkedGps("Đã phát hiện chuyển động (≥2,9 km/h). " +
                        "Dừng công cụ để tránh thao tác khi lái xe.")
                    return
                }
                if(decision!=ParkedGpsDecision.VALID_STATIONARY) {
                    lastValidParkedFix=null
                    originFromGpsButton.isEnabled=false
                    liveDeviation.reset()
                    lastRealFixTimeMs=null
                    liveGpsStatus.text="GPS thật: chưa đủ điều kiện (${decision.name}). " +
                        "Cần mẫu mới, chính xác, tốc độ thấp. Không tính lệch tuyến."
                    return
                }
                if(lastRealFixTimeMs!=null && fix.elapsedRealtimeMs<=lastRealFixTimeMs!!) {
                    liveGpsStatus.text="GPS thật: mẫu trùng/không tiến thời gian; bỏ qua."
                    return
                }
                lastRealFixTimeMs=fix.elapsedRealtimeMs
                lastValidParkedFix=fix
                originFromGpsButton.isEnabled=true
                val plan=latestPlan
                val g=graph
                if(plan==null || g==null || plan.edgeIds.isEmpty()) {
                    liveGpsStatus.text="GPS thật đủ điều kiện khi xe đỗ (±${fix.accuracyMeters?.toInt()}m). " +
                        "Chưa có tuyến khảo sát để so sánh. Không lưu tọa độ."
                    return
                }
                val result=liveDeviation.observe(g,plan,fix.latitude,fix.longitude,
                    fix.accuracyMeters!!,fix.elapsedRealtimeMs)
                val offset=if(result.distanceMeters==null) "?" else
                    String.format(Locale.ROOT,"%.0f",result.distanceMeters)
                liveGpsStatus.text="GPS thật (xe đỗ): ${result.state.name}, " +
                    "cách hình học tuyến khoảng $offset m. " +
                    "Chỉ là chẩn đoán, không tự tính lại tuyến hay phát chỉ dẫn."
            }
            override fun onProviderDisabled(provider: String) {
                stopParkedGps("GPS đã bị tắt; dừng phiên đọc vị trí.")
            }
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                1000L,0f,listener)
            liveGpsListener=listener
            liveGpsButton.text="Tắt GPS thật"
            if(::simulationButton.isInitialized) simulationButton.isEnabled=false
            liveGpsStatus.text="Đang đợi GPS thực tế, tốc độ và độ chính xác; " +
                "dữ liệu chỉ tồn tại trong RAM của màn hình đang mở."
        } catch(e:Exception) {
            liveGpsListener=null
            liveGpsStatus.text="Không bắt đầu được GPS thật: ${e.message ?: "thiếu quyền"}"
        }
    }

    private fun stopParkedGps(reason: String) {
        liveGpsListener?.let { listener ->
            runCatching { locationManager.removeUpdates(listener) }
        }
        liveGpsListener=null
        lastValidParkedFix=null
        lastRealFixTimeMs=null
        liveDeviation.reset()
        if(::liveGpsButton.isInitialized) liveGpsButton.text="Bật GPS thật (khi đỗ)"
        if(::originFromGpsButton.isInitialized) originFromGpsButton.isEnabled=false
        if(::simulationButton.isInitialized)
            simulationButton.isEnabled=latestPlan?.edgeIds?.isNotEmpty()==true
        if(::liveGpsStatus.isInitialized) liveGpsStatus.text=reason
    }

    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>,
                                            grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==GPS_PERMISSION_REQUEST && !destroyed && !isFinishing) {
            if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)
                startParkedGps()
            else if(::liveGpsStatus.isInitialized)
                liveGpsStatus.text="Không được cấp quyền GPS; giữ trạng thái chưa xác định."
        }
    }

    private fun loadBackgroundMap(m: MapLibreMap) {
        val mode=prefs.getString("mapSourceMode","online")
        if(mode!="pmtiles_vector" && mode!="pmtiles_raster") {
            val style=MapStyleDefaults.effective(prefs.getString("style",null))
            mapStatus.text="Nền bản đồ online: cần kết nối mạng."
            m.setStyle(style) {
                if(!destroyed)showRoute(latestPlan)
            }
            return
        }
        mapStatus.text="Đang kiểm tra SHA-256 và nạp PMTiles cục bộ…"
        val expected=prefs.getString("pmtilesActiveSha","") ?: ""
        val layer=prefs.getString("pmtilesVectorLayer","") ?: ""
        background.execute {
            val archive=PmtilesArchiveStore(applicationContext).loadVerified()
            val style=runCatching {
                require(archive!=null && archive.sha256==expected)
                when(mode) {
                    "pmtiles_vector" -> {
                        require(archive.header.tileType==1 &&
                            archive.vectorInfo?.layers?.contains(layer)==true)
                        PmtilesVectorStyle.make(archive.tileUri,layer,archive.attribution,
                            archive.header.minZoom.coerceAtMost(22),
                            archive.header.maxZoom.coerceAtMost(22))
                    }
                    "pmtiles_raster" -> PmtilesRasterStyle.make(archive)
                    else -> error("unknown source")
                }
            }
            runOnUiThread {
                if(destroyed || isFinishing)return@runOnUiThread
                if(style.isFailure) {
                    mapStatus.text="Gói PMTiles không hợp lệ: ${style.exceptionOrNull()?.message}. " +
                        "Không tự chuyển sang tile online."
                    return@runOnUiThread
                }
                m.setStyle(Style.Builder().fromJson(style.getOrThrow())) {
                    if(destroyed)return@setStyle
                    mapStatus.text="Đã nạp style PMTiles cục bộ; cần kiểm tra tile thật trên thiết bị."
                    showRoute(latestPlan)
                }
            }
        }
    }

    private fun showRoute(plan: RoutePlan?) {
        val m=map ?:return
        if(m.style==null)return
        previewLine?.let { m.removePolyline(it) }
        previewLine=null
        if(plan==null)return
        val g=graph ?:return
        val coords=plan.nodeIds.mapNotNull { g.nodes[it] }
            .map { LatLng(it.lat,it.lon) }
        if(coords.size>1) {
            previewLine=m.addPolyline(PolylineOptions().addAll(coords)
                .color(Color.rgb(255,203,59)).width(6f))
            val bounds=LatLngBounds.Builder()
            coords.forEach { bounds.include(it) }
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),60))
        } else if(coords.size==1) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(coords[0],14.0))
        }
    }

    override fun onStart() { super.onStart();if(::mapView.isInitialized)mapView.onStart() }
    override fun onResume() { super.onResume();if(::mapView.isInitialized)mapView.onResume() }
    override fun onPause() {
        stopParkedGps("Màn hình tạm dừng: GPS thật đã tự tắt. Bật lại khi xe vẫn đang đỗ.")
        if(::mapView.isInitialized)mapView.onPause()
        super.onPause()
    }
    override fun onStop() { if(::mapView.isInitialized)mapView.onStop();super.onStop() }
    override fun onDestroy() {
        stopParkedGps("Đã đóng màn hình.")
        destroyed=true
        background.shutdownNow()
        if(::mapView.isInitialized)mapView.onDestroy()
        super.onDestroy()
    }
    override fun onLowMemory() { super.onLowMemory();if(::mapView.isInitialized)mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        if(::mapView.isInitialized)mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
}
