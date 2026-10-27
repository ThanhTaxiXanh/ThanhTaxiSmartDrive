package com.thanhtaxi.smartdrive.offline

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.widget.*
import android.graphics.Color
import java.util.concurrent.Executors
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * SAF importer + bounded raster preview. A loading style callback is NOT
 * evidence that every map tile renders correctly offline.
 */
class PmtilesManagerActivity : Activity() {
    private val requestFile = 501
    private val requestExport = 502
    private lateinit var store: PmtilesArchiveStore
    private lateinit var message: TextView
    private lateinit var sourceInput: EditText
    private lateinit var licenseInput: EditText
    private lateinit var attributionInput: EditText
    private lateinit var hashInput: EditText
    private lateinit var consent: CheckBox
    private lateinit var choose: Button
    private lateinit var preview: Button
    private lateinit var applyDashboard: Button
    private lateinit var mapView: MapView
    private lateinit var visibleTiles: CheckBox
    private lateinit var multipleZooms: CheckBox
    private lateinit var restarted: CheckBox
    private lateinit var exportReport: Button
    private var styleReady = false
    private var loaded: PmtilesArchive? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!intent.getBooleanExtra("verifiedStopped",false)) {
            Toast.makeText(this,"Chỉ nhập và xem bản đồ khi xe đỗ",Toast.LENGTH_LONG).show()
            finish()
            return
        }
        store = PmtilesArchiveStore(this)
        MapLibre.getInstance(this)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(18,12,18,12) }
        val scroll = ScrollView(this)
        val controls = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(controls)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        fun text(value:String) { controls.addView(TextView(this).apply { text=value;textSize=15f }) }
        fun field(hintText:String): EditText = EditText(this).apply {
            hint=hintText;setSingleLine(true);controls.addView(this)
        }
        text("PMTiles v3 cục bộ — chỉ xem thử bản đồ raster")
        text("Chọn tệp PNG/JPEG/WebP PMTiles từ bộ nhớ. Gói vector có thể lưu nhưng cần style riêng để hiển thị. Tệp không được kiểm định pháp lý.")
        sourceInput=field("Nhà cung cấp và ID bộ dữ liệu")
        licenseInput=field("Giấy phép cho phép sử dụng và lưu offline")
        attributionInput=field("Attribution / ghi công nhà cung cấp")
        hashInput=field("SHA-256 bên phát hành cung cấp (nếu có)")
        consent=CheckBox(this).apply {
            text="Tôi có quyền lưu offline và đã xem điều kiện giấy phép (không phải chứng nhận của ứng dụng)"
            controls.addView(this)
        }
        message=TextView(this).apply { text="Đang kiểm tra gói hiện tại…";textSize=16f }
        controls.addView(message)
        choose=Button(this).apply {
            text="Chọn và nhập PMTiles"
            setOnClickListener {
                if (!consent.isChecked) {
                    message.text="Bạn cần xác nhận quyền sử dụng và lưu dữ liệu."
                    return@setOnClickListener
                }
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type="*/*";addCategory(Intent.CATEGORY_OPENABLE)
                },requestFile)
            }
        }
        controls.addView(choose)
        preview=Button(this).apply {
            text="Xem thử raster đã nhập"
            isEnabled=false
            setOnClickListener { showRaster() }
        }
        controls.addView(preview)
        applyDashboard=Button(this).apply {
            text="Dùng PMTiles raster này trên Dashboard"
            isEnabled=false
            setOnClickListener {
                val archive=loaded
                if(archive==null || !archive.header.isRaster) {
                    message.text="Chỉ gói raster PNG/JPEG/WebP mới đặt làm bản đồ mặc định."
                } else {
                    getSharedPreferences("taxi",MODE_PRIVATE).edit()
                        .putString("mapSourceMode","pmtiles_raster")
                        .putString("pmtilesActiveSha",archive.sha256).apply()
                    message.text="Đã chọn PMTiles raster làm nền Dashboard. Quay lại Dashboard để xem."
                }
            }
        }
        controls.addView(applyDashboard)
        text("Phiếu thử offline (người dùng tự quan sát; không phải chứng nhận tự động)")
        visibleTiles=CheckBox(this).apply {
            text="Tôi đã nhìn thấy tile đúng vị trí khi không có mạng"
            controls.addView(this)
        }
        multipleZooms=CheckBox(this).apply {
            text="Tôi đã pan và thử ít nhất hai mức zoom"
            controls.addView(this)
        }
        restarted=CheckBox(this).apply {
            text="Tôi đã đóng rồi mở lại ứng dụng và thử lại khi offline"
            controls.addView(this)
        }
        controls.addView(Button(this).apply {
            text="Ghi báo cáo thử offline vào bộ nhớ riêng"
            setOnClickListener { recordManualAudit() }
        })
        exportReport=Button(this).apply {
            text="Xuất báo cáo kiểm thử JSON"
            isEnabled=reportFile().isFile
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type="application/json"
                    putExtra(Intent.EXTRA_TITLE,"pmtiles_offline_trial.json")
                },requestExport)
            }
        }
        controls.addView(exportReport)
        controls.addView(Button(this).apply {
            text="Xóa gói PMTiles đang chọn khỏi bộ nhớ riêng"
            setOnClickListener {
                AlertDialog.Builder(this@PmtilesManagerActivity)
                    .setTitle("Xóa archive PMTiles đã chọn?")
                    .setMessage("Chỉ xóa bản sao trong ứng dụng, không xóa tệp gốc của bạn.")
                    .setPositiveButton("Xóa") { _,_ ->
                        preview.isEnabled=false
                        applyDashboard.isEnabled=false
                        getSharedPreferences("taxi",MODE_PRIVATE).edit()
                            .putString("mapSourceMode","online")
                            .remove("pmtilesActiveSha").apply()
                        map?.setStyle(Style.Builder().fromJson(
                            """{"version":8,"sources":{},"layers":[]}"""))
                        executor.execute {
                            val result=runCatching { store.deleteActiveArchive() }
                            runOnUiThread {
                                if(isFinishing||isDestroyed)return@runOnUiThread
                                loaded=null
                                message.text=when {
                                    result.isFailure -> "Xóa thất bại: "+result.exceptionOrNull()?.message
                                    result.getOrNull()==true -> "Đã xóa bản sao archive trong ứng dụng."
                                    else -> "Không tìm thấy gói đang được chọn."
                                }
                            }
                        }
                    }.setNegativeButton("Hủy",null).show()
            }
        })
        root.addView(TextView(this).apply {
            text="Preview chỉ xác nhận thử tải style. Kiểm tra Airplane Mode, zoom và khởi động lại trên thiết bị thật."
            textSize=13f
        })
        mapView=MapView(this)
        root.addView(mapView,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        mapView.onCreate(state)
        mapView.getMapAsync { m ->
            map=m
        }
        inspectStored()
    }
    private var map: MapLibreMap?=null
    private fun inspectStored() {
        executor.execute {
            val archive=store.loadVerified()
            runOnUiThread {
                if(isFinishing||isDestroyed) return@runOnUiThread
                loaded=archive
                styleReady=false
                preview.isEnabled=archive?.header?.isRaster==true
                applyDashboard.isEnabled=archive?.header?.isRaster==true
                message.text=if(archive==null) "Chưa có archive hợp lệ, hoặc checksum không khớp."
                    else "SHA-256: " + archive.sha256.take(16) + "… · " +
                        archive.header.formatName + " · z" + archive.header.minZoom + "–" +
                        archive.header.maxZoom +
                        ". Đã kiểm tra checksum và header; chưa kiểm thử hiển thị tile."
            }
        }
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK) return
        if(requestCode==requestExport) {
            val uri=data?.data ?:return
            try {
                val bytes=reportFile().readBytes()
                checkNotNull(contentResolver.openOutputStream(uri)) { "Cannot create export" }.use {
                    it.write(bytes)
                }
                message.text="Đã xuất báo cáo tự khai; chưa phải kết quả xác minh độc lập."
            } catch(e:Exception) { message.text="Xuất báo cáo thất bại: "+e.message }
            return
        }
        if(requestCode!=requestFile) return
        val uri:Uri=data?.data ?:return
        choose.isEnabled=false;preview.isEnabled=false
        message.text="Đang sao chép và kiểm tra SHA-256; không đóng màn hình."
        val source=sourceInput.text.toString()
        val license=licenseInput.text.toString()
        val attribution=attributionInput.text.toString()
        val hash=hashInput.text.toString()
        executor.execute {
            val result=runCatching { store.importArchive(contentResolver,uri,source,license,attribution,hash) }
            runOnUiThread {
                if(isFinishing||isDestroyed) return@runOnUiThread
                choose.isEnabled=true
                result.onSuccess { archive ->
                    loaded=archive
                    styleReady=false
                    preview.isEnabled=archive.header.isRaster
                    applyDashboard.isEnabled=archive.header.isRaster
                    message.text="Đã sao chép và xác minh cấu trúc: "+archive.header.formatName+
                        " · "+archive.file.length()/1024/1024+" MiB. SHA-256: "+archive.sha256+
                        ". Chưa kiểm tra chất lượng hoặc quyền pháp lý."
                }.onFailure { error ->
                    message.text="Không thể nhập PMTiles: "+(error.message ?: "Lỗi đọc tệp")
                }
            }
        }
    }
    private fun showRaster() {
        val a=loaded ?:return
        if (!a.header.isRaster) return
        val m=map ?:run {
            message.text="Bản đồ chưa sẵn sàng";return
        }
        try {
            styleReady=false
            m.setStyle(Style.Builder().fromJson(PmtilesRasterStyle.make(a))) {
                styleReady=true
                m.cameraPosition=CameraPosition.Builder()
                    .target(LatLng(a.header.centerLat,a.header.centerLon))
                    .zoom(a.header.centerZoom.toDouble().coerceIn(
                        a.header.minZoom.toDouble(),a.header.maxZoom.toDouble()))
                    .build()
                message.text="Đã tải cấu hình style PMTiles cục bộ. Phải kiểm tra tile thực trên thiết bị ở chế độ máy bay."
            }
        } catch(e:Exception) { message.text="Không mở được style: "+e.message }
    }
    private fun reportFile(): File = File(filesDir,"pmtiles_offline_trial.json")
    private fun recordManualAudit() {
        val archive=loaded
        val connected=(getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
            .activeNetwork != null
        val observations=OfflineSmokeObservations(
            archiveChecksumKnown=archive!=null,
            mapStyleLoaded=styleReady,
            noActiveNetwork=!connected,
            userObservedMapTiles=visibleTiles.isChecked,
            userTriedMultipleZoomLevels=multipleZooms.isChecked,
            userRestartedApplication=restarted.isChecked
        )
        val status=OfflineSmokePolicy.assess(observations)
        if(status!=OfflineSmokeStatus.READY_FOR_HUMAN_REVIEW) {
            message.text="Chưa đủ điều kiện ghi phiếu: cần mở style, không có mạng hoạt động " +
                "và tự xác nhận ba bước nhìn bằng mắt, zoom và khởi động lại."
            return
        }
        val report=JSONObject()
            .put("archiveSha256",archive!!.sha256)
            .put("archiveBytes",archive.file.length())
            .put("timestampUtcMs",System.currentTimeMillis())
            .put("noActiveNetworkObserved",true)
            .put("styleLoadCallbackObserved",styleReady)
            .put("userReportedTilesVisible",visibleTiles.isChecked)
            .put("userReportedZoomTest",multipleZooms.isChecked)
            .put("userReportedRestartTest",restarted.isChecked)
            .put("result",status.name)
            .put("independentVerification",false)
            .put("disclaimer","USER SELF-REPORT ONLY: no tile-level automated attestation")
        try {
            reportFile().writeText(report.toString(2),Charsets.UTF_8)
            exportReport.isEnabled=true
            message.text="Đã lưu phiếu thử do người dùng tự khai; không chứng nhận ảnh bản đồ chính xác."
        } catch(e:Exception) { message.text="Không ghi được báo cáo: "+e.message }
    }
    override fun onStart(){super.onStart();if(::mapView.isInitialized)mapView.onStart()}
    override fun onResume(){super.onResume();if(::mapView.isInitialized)mapView.onResume()}
    override fun onPause(){if(::mapView.isInitialized)mapView.onPause();super.onPause()}
    override fun onStop(){if(::mapView.isInitialized)mapView.onStop();super.onStop()}
    override fun onDestroy(){executor.shutdownNow();if(::mapView.isInitialized)mapView.onDestroy();super.onDestroy()}
    override fun onLowMemory(){super.onLowMemory();if(::mapView.isInitialized)mapView.onLowMemory()}
    override fun onSaveInstanceState(b:Bundle){if(::mapView.isInitialized)mapView.onSaveInstanceState(b);super.onSaveInstanceState(b)}
}
