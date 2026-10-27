package com.thanhtaxi.smartdrive.navigation

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.thanhtaxi.smartdrive.data.RoadPackRepository
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Parked-only route preview. Never issue driving instructions based on
 * untrusted road packs. Graph may work without Internet; raster/vector
 * basemap only works offline if correctly licensed tiles were downloaded.
 */
class RoutePreviewActivity : Activity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var previewLine: Polyline? = null
    private var latestPlan: RoutePlan? = null
    private val graph by lazy { RoadPackRepository(this).load()?.routeGraph }
    private val prefs by lazy { getSharedPreferences("taxi", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parked = intent.getBooleanExtra("verifiedStopped",false)
        if (!parked) {
            Toast.makeText(this,"Chỉ xem trước tuyến trong Cài đặt khi xe đã đỗ",Toast.LENGTH_LONG).show()
            finish()
            return
        }
        MapLibre.getInstance(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12,12,12,12)
        }
        root.addView(TextView(this).apply {
            text = "Tuyến đường thử nghiệm (không dùng để lái xe)"
            textSize = 20f
        })
        root.addView(TextView(this).apply {
            text = "Tuyến được tính từ các đoạn và lượt rẽ đã khai báo trong gói JSON; " +
                "chưa đối chiếu biển cấm, giao thông thời gian thực hay tổ chức đường thực địa."
        })
        val g = graph
        if (g == null || g.nodes.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Chưa có đồ thị định tuyến hợp lệ. Hãy nhập road pack có routing.nodes, " +
                    "routing.edges và routing.allowedTurns tại Cài đặt."
                textSize = 17f
            })
            setContentView(root)
            return
        }
        val ids = g.nodes.keys.sorted()
        fun edit(hintText: String): AutoCompleteTextView = AutoCompleteTextView(this).apply {
            hint = hintText
            threshold = 1
            setSingleLine(true)
            setAdapter(ArrayAdapter(this@RoutePreviewActivity,
                android.R.layout.simple_dropdown_item_1line,ids))
        }
        val start = edit("ID nút xuất phát")
        val finish = edit("ID nút đích")
        root.addView(start)
        root.addView(finish)
        val status = TextView(this).apply {
            text = "Đã nạp " + g.nodes.size + " nút. Chọn đúng ID nút trong dữ liệu khảo sát."
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(status)
        val button = Button(this).apply { text = "Tính và xem trước tuyến" }
        root.addView(button)
        mapView = MapView(this)
        root.addView(mapView,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            val style = prefs.getString("style","https://demotiles.maplibre.org/style.json")!!
            m.setStyle(style) { showRoute(latestPlan) }
        }
        button.setOnClickListener {
            val route = g.route(start.text.toString().trim(),finish.text.toString().trim())
            latestPlan = route
            status.text = if(route == null)
                "Không tìm được tuyến trên đồ thị đã nhập; không có nghĩa đường thực tế bị chặn."
            else "Đường xem trước: " + route.edgeIds.size + " đoạn, " +
                String.format(java.util.Locale.ROOT,"%.2f km",route.metres / 1000.0) +
                " — CHƯA xác minh pháp lý/biển báo."
            showRoute(route)
        }
    }

    private fun showRoute(plan: RoutePlan?) {
        val m = map ?: return
        if (m.style == null) return
        previewLine?.let { m.removePolyline(it) }
        previewLine = null
        if (plan == null) return
        val g = graph ?: return
        val coordinates = plan.nodeIds.mapNotNull { g.nodes[it] }.map { LatLng(it.lat,it.lon) }
        if (coordinates.isEmpty()) return
        if(coordinates.size > 1) {
            previewLine = m.addPolyline(PolylineOptions().addAll(coordinates)
                .color(Color.rgb(255,203,59)).width(6f))
            val bounds = LatLngBounds.Builder()
            coordinates.forEach { bounds.include(it) }
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),60))
        } else m.animateCamera(CameraUpdateFactory.newLatLngZoom(coordinates[0],14.0))
    }

    override fun onStart() { super.onStart(); if(::mapView.isInitialized) mapView.onStart() }
    override fun onResume() { super.onResume(); if(::mapView.isInitialized) mapView.onResume() }
    override fun onPause() { if(::mapView.isInitialized) mapView.onPause(); super.onPause() }
    override fun onStop() { if(::mapView.isInitialized) mapView.onStop(); super.onStop() }
    override fun onDestroy() { if(::mapView.isInitialized) mapView.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); if(::mapView.isInitialized) mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        if(::mapView.isInitialized) mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
}
