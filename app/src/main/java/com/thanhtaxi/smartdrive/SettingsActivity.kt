package com.thanhtaxi.smartdrive

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import android.net.Uri
import com.thanhtaxi.smartdrive.data.RoadPackRepository
import com.thanhtaxi.smartdrive.offline.OfflineMapsActivity

class SettingsActivity : Activity() {
    companion object { private const val PHOTO = 102; private const val PACK = 103 }
    private val prefs by lazy { getSharedPreferences("taxi", MODE_PRIVATE) }
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
            setText(prefs.getString("style", "https://demotiles.maplibre.org/style.json"))
        }
        content.addView(styleInput)
        action("Lưu đường dẫn bản đồ") {
            val url = styleInput.text.toString().trim()
            if (url.startsWith("https://")) {
                prefs.edit().putString("style", url).apply()
                Toast.makeText(this, "Đã lưu. Mở lại dashboard để áp dụng", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "Cần URL HTTPS", Toast.LENGTH_SHORT).show()
        }
        heading("Bản đồ offline (khi xe đã đỗ)")
        action("Quản lý vùng tải offline Bắc Giang / Bắc Ninh") {
            val parked = intent.getBooleanExtra("verifiedStopped", false)
            if (!parked) Toast.makeText(this,"Chỉ tải hoặc sửa gói khi xe dừng, vào từ Dashboard",Toast.LENGTH_LONG).show()
            else startActivity(Intent(this, OfflineMapsActivity::class.java).putExtra("verifiedStopped", true))
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
            Toast.makeText(this, if (pack == null) "Chưa có dữ liệu được nhập" else
                "${pack.title}: ${pack.segments.size} đoạn, ${pack.signs.size} biển; nguồn: ${pack.source}", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, "Đã nhập ${pack.segments.size} đoạn. Chưa có xác minh pháp lý tự động.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể nhập: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
