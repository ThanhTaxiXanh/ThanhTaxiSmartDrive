package com.thanhtaxi.smartdrive.offline

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.widget.*
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.*

/** Downloads only user-authorized style resources while safely parked over unmetered Wi-Fi. */
class OfflineMapsActivity : Activity() {
    private lateinit var manager: OfflineManager
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val watching = mutableMapOf<Long, OfflineRegion>()
    private val prefs by lazy { getSharedPreferences("taxi", MODE_PRIVATE) }
    private val parked by lazy { intent.getBooleanExtra("verifiedStopped", false) }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: android.net.Network) { runOnUiThread { pauseIfMetered() } }
        override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) {
            runOnUiThread { pauseIfMetered() }
        }
    }
    private var monitoring = false
    private fun pauseIfMetered() {
        if (!unmeteredWifi()) {
            watching.values.forEach { it.setDownloadState(OfflineRegion.STATE_INACTIVE) }
            if (watching.isNotEmpty()) status.text = "Wi-Fi mất hoặc bị tính dung lượng: đã yêu cầu tạm dừng."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        manager = OfflineManager.getInstance(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24,24,24,24)
        }
        root.addView(TextView(this).apply { text = "BẢN ĐỒ OFFLINE — BẢN THỬ"; textSize = 23f })
        root.addView(TextView(this).apply {
            text = "Chỉ dùng khi xe đỗ và style/tile server cho phép tải offline. Hai vùng " +
                "chỉ rộng khoảng 4 km, KHÔNG phủ toàn tỉnh. Không chứa dữ liệu dẫn đường."
        })
        status = TextView(this).apply { text = "Đang đọc danh sách..." }
        root.addView(status)
        root.addView(Button(this).apply { text = "Làm mới trạng thái"; setOnClickListener { reload() } })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) },
            LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        reload()
    }
    private fun unmeteredWifi(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
    private fun reload() {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                list.removeAllViews()
                val existing = regions?.toList().orEmpty()
                val keys = existing.mapNotNull {
                    runCatching { JSONObject(String(it.metadata, Charsets.UTF_8)).optString("areaId") }.getOrNull()
                }.toSet()
                OfflineAreaCatalog.PILOT.forEach { area ->
                    list.addView(Button(this@OfflineMapsActivity).apply {
                        text = "Tải " + area.name + " / zoom 11–15"
                        isEnabled = parked && area.id !in keys
                        setOnClickListener { confirm(area) }
                    })
                }
                status.text = "Đã tạo " + existing.size + " gói MapLibre; chỉ gói hoàn tất mới đáng tin cậy khi offline."
                existing.forEach { region ->
                    val line = TextView(this@OfflineMapsActivity)
                    line.text = "Đang tải trạng thái gói #" + region.id
                    list.addView(line)
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(s: OfflineRegionStatus?) {
                            line.text = if (s == null) "Gói #" + region.id + ": chưa có trạng thái"
                            else "Gói #" + region.id + " — " +
                                (if (s.isComplete) "HOÀN TẤT" else "CHƯA HOÀN TẤT") +
                                " — " + s.completedResourceCount + "/" + s.requiredResourceCount
                        }
                        override fun onError(error: String?) { line.text = "Gói #" + region.id + ": " + error }
                    })
                    val actions = LinearLayout(this@OfflineMapsActivity)
                    fun button(textValue: String, operation: () -> Unit) {
                        actions.addView(Button(this@OfflineMapsActivity).apply {
                            text = textValue
                            isEnabled = parked
                            setOnClickListener { operation() }
                        }, LinearLayout.LayoutParams(0,-2,1f))
                    }
                    button("Tải tiếp") {
                        if (!unmeteredWifi()) { status.text = "Cần Wi-Fi không tính dung lượng"; return@button }
                        observe(region,line)
                        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                    }
                    button("Dừng") {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        status.text = "Đã yêu cầu dừng tải gói #" + region.id
                    }
                    button("Xóa") {
                        AlertDialog.Builder(this@OfflineMapsActivity).setTitle("Xóa dữ liệu gói này?")
                            .setNegativeButton("Hủy",null).setPositiveButton("Xóa") { _, _ ->
                                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                                    override fun onDelete() { watching.remove(region.id); reload() }
                                    override fun onError(error: String) { status.text = error }
                                })
                            }.show()
                    }
                    list.addView(actions)
                }
            }
            override fun onError(error: String) { status.text = "Không đọc được gói: " + error }
        })
    }
    private fun confirm(area: OfflineArea) {
        val style = prefs.getString("style","") ?: ""
        if (!parked || !OfflineAreaCatalog.validStyleUri(style) || !unmeteredWifi()) {
            status.text = "Cần xe đỗ, Wi-Fi không tính dung lượng và style HTTPS hợp lệ."
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Quyền lưu tile bản đồ")
            .setMessage("Chỉ tải khi bạn được phép cache tile/font/sprite từ nhà cung cấp style. " +
                "Bản thử giới hạn zoom 11–15 và KHÔNG phải bản đồ toàn tỉnh.")
            .setNegativeButton("Hủy",null)
            .setPositiveButton("Tôi được phép") { _, _ -> download(area,style) }
            .show()
    }
    private fun download(area: OfflineArea, style: String) {
        val bounds = LatLngBounds.Builder().include(LatLng(area.south,area.west))
            .include(LatLng(area.north,area.east)).build()
        val definition = OfflineTilePyramidRegionDefinition(style,bounds,
            OfflineAreaCatalog.MIN_ZOOM, OfflineAreaCatalog.MAX_ZOOM,1f)
        val meta = JSONObject().put("areaId",area.id).put("name",area.name)
            .put("style",style).toString().toByteArray(Charsets.UTF_8)
        manager.createOfflineRegion(definition,meta,object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(region: OfflineRegion) {
                status.text = "Đang tải vùng " + area.name
                observe(region,status)
                region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                reload()
            }
            override fun onError(error: String) { status.text = "Không tạo được vùng: " + error }
        })
    }
    private fun observe(region: OfflineRegion, output: TextView) {
        watching[region.id] = region
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(s: OfflineRegionStatus) {
                if (s.isComplete) region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                output.text = "Gói #" + region.id + ": " + s.completedResourceCount +
                    "/" + s.requiredResourceCount + if (s.isComplete) " — HOÀN TẤT" else " — đang tải"
            }
            override fun onError(error: OfflineRegionError) {
                status.text = "Tải thất bại hoặc mất mạng: " + error.message
            }
            override fun mapboxTileCountLimitExceeded(limit: Long) {
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                status.text = "Giới hạn tile: " + limit
            }
        })
    }
    override fun onStart() {
        super.onStart()
        try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .registerNetworkCallback(NetworkRequest.Builder().addTransportType(
                    NetworkCapabilities.TRANSPORT_WIFI).build(), networkCallback)
            monitoring = true
        } catch (_: Exception) { monitoring = false }
    }
    override fun onStop() {
        if (monitoring) {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(networkCallback)
            monitoring = false
        }
        // Downloads initiated by this screen must never continue unnoticed on mobile data.
        watching.values.forEach { region ->
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region.setObserver(null)
        }
        watching.clear()
        super.onStop()
    }
    override fun onDestroy() {
        watching.values.forEach { it.setObserver(null) }
        watching.clear()
        super.onDestroy()
    }
}
