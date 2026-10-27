package com.thanhtaxi.smartdrive

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import android.net.Uri
import com.thanhtaxi.smartdrive.data.RoadPackRepository
import com.thanhtaxi.smartdrive.offline.OfflineAreaCatalog
import com.thanhtaxi.smartdrive.offline.OfflineMapsActivity
import com.thanhtaxi.smartdrive.offline.OfflinePoiActivity
import com.thanhtaxi.smartdrive.offline.PmtilesManagerActivity
import com.thanhtaxi.smartdrive.offline.MapStyleDefaults
import com.thanhtaxi.smartdrive.navigation.RoutePreviewActivity

class SettingsActivity : Activity() {
    companion object { private const val PHOTO = 102; private const val PACK = 103 }
    private val prefs by lazy { getSharedPreferences("taxi", MODE_PRIVATE) }

    /**
     * Fix: Settings can be restored/launched without the transient GPS "verifiedStopped"
     * extra. No GPS reading is treated as proof the car is moving. Require an explicit
     * stationary confirmation before opening interactive import/download tools.
     */
    private fun openParkedTool(target: Class<out Activity>) {
        val launch = {
            startActivity(Intent(this, target).putExtra("verifiedStopped", true))
        }
        if (intent.getBooleanExtra("verifiedStopped", false)) {
            launch()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Xác nhận xe đang đỗ")
                .setMessage("Hệ thống chưa xác nhận trạng thái xe dừng. " +
                    "Chỉ mở công cụ khi đã đỗ xe ở vị trí an toàn, không thao tác khi đang lái.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Tôi đã đỗ xe") { _, _ -> launch() }
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 24, 30, 24) }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        fun heading(title: String) {
            content.addView(TextView(this).apply { text = title; textSize = 22f; setPadding(0, 16, 0, 8) })
        }
        fun action(title: String, click: () -> Unit) {
            content.addView(Button(this).apply { text = title; setOnClickListener { click() } })
        }
        heading("Cài đặt Thanh Taxi Smart Drive")
        content.addView(TextView(this).apply {
            text = "Các chức năng chỉnh sửa dùng khi xe đang đỗ. Dữ liệu biển báo phải được xác minh thực địa."
            textSize = 15f
        })
        heading("Bản đồ MapLibre")
        val styleInput = EditText(this).apply {
            setSingleLine(true); hint = "HTTPS URL style.json"
            setText(MapStyleDefaults.effective(prefs.getString("style", null)))
        }
        content.addView(styleInput)
        action("Dùng bản đồ đường phố OpenFreeMap (online)") {
            styleInput.setText(MapStyleDefaults.STREET_STYLE)
            prefs.edit().putString("style", MapStyleDefaults.STREET_STYLE)
                .putString("mapSourceMode", "online").apply()
            Toast.makeText(this, "Đã chọn bản đồ đường phố, trở lại Dashboard.", Toast.LENGTH_LONG).show()
        }
        action("Lưu đường dẫn bản đồ") {
            val url = styleInput.text.toString().trim()
            if (OfflineAreaCatalog.validStyleUri(url)) {
                prefs.edit().putString("style", url)
                    .putString("mapSourceMode","online").apply()
                Toast.makeText(this, "Đã lưu. Mở lại dashboard để áp dụng", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "Cần URL HTTPS", Toast.LENGTH_SHORT).show()
        }
        action("Chuyển Dashboard về bản đồ online") {
            prefs.edit().putString("mapSourceMode","online").apply()
            Toast.makeText(this,"Đã chuyển về nguồn online; quay lại Dashboard để thử.",Toast.LENGTH_LONG).show()
        }
        content.addView(TextView(this).apply {
            text="Nếu bản đồ xanh/trống: kiểm tra 4G/Wi-Fi, quyền Internet, style URL và tile server. " +
                "GPS hoạt động không chứng minh tile đã tải. Tên biển số tốc độ -- là bình thường khi chưa nhập dữ liệu."
        })
        content.addView(CheckBox(this).apply {
            text="Hiển thị tên/mã tuyến offline từ PMTiles (nếu có)"
            isChecked=prefs.getBoolean("showOfflineRoadLabels",true)
            setOnCheckedChangeListener { _,enabled ->
                prefs.edit().putBoolean("showOfflineRoadLabels",enabled).apply()
            }
        })
        heading("Bản đồ offline (khi xe đã đỗ)")
        action("Quản lý vùng tải offline Bắc Giang / Bắc Ninh") {
            openParkedTool(OfflineMapsActivity::class.java)
        }
        action("Nhập và xem thử tệp PMTiles cục bộ (khi xe đỗ)") {
            openParkedTool(PmtilesManagerActivity::class.java)
        }
        heading("Tìm điểm đến offline (khi xe đỗ)")
        action("Nhập gói POI SQLite và tìm địa chỉ không cần Internet") {
            openParkedTool(OfflinePoiActivity::class.java)
        }
        heading("Dẫn đường offline — xem trước khi xe đỗ")
        action("Tính thử tuyến trên dữ liệu road pack đã nhập") {
            openParkedTool(RoutePreviewActivity::class.java)
        }
        heading("Cảnh báo tốc độ")
        val tolerance = SeekBar(this).apply { max = 10; progress = prefs.getInt("tolerance", 3) }
        val info = TextView(this).apply { text = "Ngưỡng cảnh báo: +${tolerance.progress} km/h (không thay đổi giới hạn hợp pháp)" }
        content.addView(info); content.addView(tolerance)
        tolerance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, user: Boolean) {
                info.text = "Ngưỡng cảnh báo: +$p km/h (không thay đổi giới hạn hợp pháp)"
                if (user) prefs.edit().putInt("tolerance", p).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        content.addView(CheckBox(this).apply {
            text = "Phát tiếng bíp nhẹ khi vượt ngưỡng (mặc định tắt)"
            isChecked = prefs.getBoolean("soundAlert", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("soundAlert", checked).apply()
            }
        })
        heading("Ảnh / QR thanh toán")
        action("Chọn ảnh từ thiết bị") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, PHOTO)
        }
        heading("Gói dữ liệu đường, biển báo (JSON, schema v1)")
        action("Nhập gói dữ liệu đã kiểm tra nguồn") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE)
            }, PACK)
        }
        action("Xem thông tin gói đang lưu") {
            val pack = RoadPackRepository(this).load()
            Toast.makeText(this, if (pack == null) "Chưa có dữ liệu hợp lệ hoặc checksum lỗi" else
                "${pack.title}, bộ ${pack.series} v${pack.version}: ${pack.segments.size} đoạn, " +
                "${pack.trafficSigns.size} biển gắn tọa độ; nguồn: ${pack.source}", Toast.LENGTH_LONG).show()
        }
        action("Khôi phục bản dữ liệu trước (nếu bản mới sai)") {
            try {
                val pack = RoadPackRepository(this).restoreBackup()
                Toast.makeText(this, "Đã khôi phục: ${pack.series} v${pack.version}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this,"Không thể khôi phục: ${e.message}",Toast.LENGTH_LONG).show()
            }
        }
        heading("Media")
        action("Cấp quyền đọc phiên Media đang phát") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        action("Quay lại Dashboard") { finish() }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            when (requestCode) {
                PHOTO -> {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    prefs.edit().putString("paymentImage", uri.toString()).apply()
                    Toast.makeText(this, "Đã lưu ảnh QR", Toast.LENGTH_SHORT).show()
                }
                PACK -> {
                    val json = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                    require(json.length <= 10_000_000) { "Dung lượng gói quá lớn" }
                    val pack = RoadPackRepository(this).import(json)
                    Toast.makeText(this, "Đã nhập ${pack.segments.size} đoạn; SHA-256 lưu trên thiết bị. Không xác thực nguồn pháp lý.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể nhập: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
