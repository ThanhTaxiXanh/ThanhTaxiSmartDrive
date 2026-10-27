package com.thanhtaxi.smartdrive.offline

import android.app.Activity
import android.app.AlertDialog
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
    private lateinit var store: PmtilesArchiveStore
    private lateinit var message: TextView
    private lateinit var sourceInput: EditText
    private lateinit var licenseInput: EditText
    private lateinit var attributionInput: EditText
    private lateinit var hashInput: EditText
    private lateinit var consent: CheckBox
    private lateinit var choose: Button
    private lateinit var preview: Button
    private lateinit var mapView: MapView
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
        controls.addView(Button(this).apply {
            text="Xóa gói đang chọn khỏi danh sách (không xóa tệp lưu trữ)"
            setOnClickListener {
                AlertDialog.Builder(this@PmtilesManagerActivity)
                    .setTitle("Ngừng sử dụng archive đã chọn?")
                    .setPositiveButton("Bỏ chọn") { _,_ ->
                        store.clearActive();loaded=null;preview.isEnabled=false
                        message.text="Đã bỏ chọn gói. Tệp vẫn trong bộ nhớ riêng cho đến khi có công cụ dọn dẹp."
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
                preview.isEnabled=archive?.header?.isRaster==true
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
        if(requestCode!=requestFile||resultCode!=RESULT_OK) return
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
                    preview.isEnabled=archive.header.isRaster
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
            m.setStyle(Style.Builder().fromJson(PmtilesRasterStyle.make(a))) {
                m.cameraPosition=CameraPosition.Builder()
                    .target(LatLng(a.header.centerLat,a.header.centerLon))
                    .zoom(a.header.centerZoom.toDouble().coerceIn(
                        a.header.minZoom.toDouble(),a.header.maxZoom.toDouble()))
                    .build()
                message.text="Đã tải cấu hình style PMTiles cục bộ. Phải kiểm tra tile thực trên thiết bị ở chế độ máy bay."
            }
        } catch(e:Exception) { message.text="Không mở được style: "+e.message }
    }
    override fun onStart(){super.onStart();if(::mapView.isInitialized)mapView.onStart()}
    override fun onResume(){super.onResume();if(::mapView.isInitialized)mapView.onResume()}
    override fun onPause(){if(::mapView.isInitialized)mapView.onPause();super.onPause()}
    override fun onStop(){if(::mapView.isInitialized)mapView.onStop();super.onStop()}
    override fun onDestroy(){executor.shutdownNow();if(::mapView.isInitialized)mapView.onDestroy();super.onDestroy()}
    override fun onLowMemory(){super.onLowMemory();if(::mapView.isInitialized)mapView.onLowMemory()}
    override fun onSaveInstanceState(b:Bundle){if(::mapView.isInitialized)mapView.onSaveInstanceState(b);super.onSaveInstanceState(b)}
}
