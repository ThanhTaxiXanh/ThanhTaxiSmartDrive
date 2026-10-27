package com.thanhtaxi.smartdrive.navigation

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
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
    private val background=Executors.newSingleThreadExecutor()
    private val graph by lazy { RoadPackRepository(this).load()?.routeGraph }
    private val prefs by lazy { getSharedPreferences("taxi",MODE_PRIVATE) }
    private lateinit var routeStatus: TextView
    private lateinit var mapStatus: TextView
    private lateinit var detailButton: Button
    private var destroyed=false

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
        root.addView(TextView(this).apply {
            text="XEM TRƯỚC TUYẾN OFFLINE — KHÔNG DÙNG ĐỂ LÁI XE"
            textSize=19f
        })
        root.addView(TextView(this).apply {
            text="Tuyến lấy từ đồ thị khảo sát do người dùng nhập. Chưa đối chiếu cấm rẽ, " +
                "làn, chiều đường thực tế, cầu vượt hoặc hiệu lực quy định giao thông."
        })
        val g=graph
        if (g==null || g.nodes.isEmpty()) {
            root.addView(TextView(this).apply {
                text="Chưa có graph trong road pack. Nhập routing.nodes, routing.edges, " +
                    "routing.allowedTurns có sourceRef để tính tuyến thử."
                textSize=16f
            })
            setContentView(root)
            return
        }
        val ids=g.nodes.keys.sorted()
        fun choose(label:String): AutoCompleteTextView =
            AutoCompleteTextView(this).apply {
                hint=label
                threshold=1
                setSingleLine(true)
                setAdapter(ArrayAdapter(this@RoutePreviewActivity,
                    android.R.layout.simple_dropdown_item_1line,ids))
            }
        val start=choose("ID nút xuất phát")
        val finish=choose("ID nút đến")
        root.addView(start)
        root.addView(finish)
        routeStatus=TextView(this).apply {
            text="Đã nạp ${ids.size} nút khảo sát. Chọn ID nguồn và đích."
            textSize=14f
            maxLines=3
        }
        root.addView(routeStatus)
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
        root.addView(buttons)
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
            val origin=start.text.toString().trim()
            val destination=finish.text.toString().trim()
            if(origin !in g.nodes || destination !in g.nodes) {
                routeStatus.text="ID nút không tồn tại trong dữ liệu khảo sát."
                return@setOnClickListener
            }
            run.isEnabled=false
            detailButton.isEnabled=false
            routeStatus.text="Đang tính đồ thị có hướng cục bộ…"
            background.execute {
                val result=runCatching {
                    val route=g.route(origin,destination)
                    if (route != null) RoutePreviewInstructions.generate(g,route)
                    route
                }
                runOnUiThread {
                    if(destroyed || isFinishing)return@runOnUiThread
                    run.isEnabled=true
                    latestPlan=result.getOrNull()
                    val p=latestPlan
                    detailButton.isEnabled=p!=null
                    routeStatus.text=when {
                        result.isFailure -> "Tuyến không hợp lệ: ${result.exceptionOrNull()?.message}"
                        p==null -> "Không có tuyến trên đồ thị đã nhập. Không kết luận đường thật bị chặn."
                        else -> "Xem trước: ${p.edgeIds.size} cạnh khảo sát, " +
                            String.format(Locale.ROOT,"%.2f km",p.metres/1000.0) +
                            ". Không phải hướng dẫn lái."
                    }
                    showRoute(p)
                }
            }
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
    override fun onPause() { if(::mapView.isInitialized)mapView.onPause();super.onPause() }
    override fun onStop() { if(::mapView.isInitialized)mapView.onStop();super.onStop() }
    override fun onDestroy() {
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
