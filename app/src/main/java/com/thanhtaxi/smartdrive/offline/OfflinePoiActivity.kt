package com.thanhtaxi.smartdrive.offline

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import java.util.concurrent.Executors

/** Parked-only import/search UI, deliberately outside the moving-car dashboard. */
class OfflinePoiActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var sha: EditText
    private lateinit var query: EditText
    private lateinit var importButton: Button
    private lateinit var searchButton: Button
    private val store by lazy { OfflinePoiRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!intent.getBooleanExtra("verifiedStopped", false)) {
            Toast.makeText(this, "Chỉ thao tác khi xe đã đỗ", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        root.addView(TextView(this).apply {
            text = "ĐỊA ĐIỂM OFFLINE — CHỈ KHI XE ĐỖ"
            textSize = 20f
        })
        root.addView(TextView(this).apply {
            text = "Dữ liệu do bạn nhập và chịu trách nhiệm kiểm tra nguồn/giấy phép. " +
                "Điểm POI không tự tạo tuyến đường hay chứng minh đường đi hợp pháp."
        })
        sha = EditText(this).apply {
            hint = "SHA-256 trong manifest .db.manifest.json (64 ký tự)"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        root.addView(sha)
        importButton = Button(this).apply {
            text = "Nhập tệp .db từ bộ nhớ (tối đa 250 MiB)"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }, PICK_DB)
            }
        }
        root.addView(importButton)
        query = EditText(this).apply {
            hint = "Tên nơi, địa chỉ, tỉnh/thành (có dấu hoặc không dấu)"
            setSingleLine(true)
        }
        root.addView(query)
        searchButton = Button(this).apply {
            text = "Tìm trong dữ liệu offline"
            setOnClickListener { search(query.text.toString()) }
        }
        root.addView(searchButton)
        status = TextView(this).apply {
            text = if (store.available()) "Đã có chỉ mục POI cục bộ" else "Chưa nhập gói POI"
        }
        root.addView(status)
        val listScroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listScroll.addView(results)
        root.addView(listScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun busy(value: Boolean) {
        importButton.isEnabled = !value
        searchButton.isEnabled = !value
    }
    private fun search(text: String) {
        if (text.isBlank()) { status.text = "Nhập từ khóa để tìm"; return }
        busy(true)
        status.text = "Đang tìm trên thiết bị…"
        worker.execute {
            val response = runCatching { store.search(text) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy(false)
                results.removeAllViews()
                response.fold(onSuccess = { found ->
                    status.text = "${found.size} kết quả trong cơ sở dữ liệu đã nhập"
                    found.forEach { place ->
                        results.addView(Button(this).apply {
                            isAllCaps = false
                            text = "${place.name} • ${place.category}\n${place.address}, ${place.province}\n" +
                                "${place.lat}, ${place.lon} • Nguồn: ${place.source} " +
                                "(${if (place.verifiedBySupplier) "Bên cấp đánh dấu đã xem xét" else "Chưa xác minh"})"
                            setOnClickListener {
                                val clip = "${place.lat},${place.lon}"
                                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("POI offline", clip))
                                Toast.makeText(this@OfflinePoiActivity,
                                    "Đã sao chép tọa độ; cần tuyến khảo sát để định tuyến.",
                                    Toast.LENGTH_LONG).show()
                            }
                        })
                    }
                }, onFailure = {
                    status.text = "Không thể tìm: ${it.message ?: "gói chưa hỗ trợ FTS5"}"
                })
            }
        }
    }

    @Deprecated("Android SAF callback for API 26+ compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_DB || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val expected = sha.text.toString().trim()
        busy(true)
        status.text = "Đang kiểm tra SHA-256, schema SQLite và FTS5…"
        worker.execute {
            val response = runCatching { store.importDb(uri, expected) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy(false)
                status.text = response.fold(
                    { "Đã cài đặt chỉ mục offline: $it địa điểm. Dữ liệu chưa được xác nhận thực địa." },
                    { "Nhập thất bại: ${it.message}. Gói trước được giữ nếu còn hợp lệ." }
                )
            }
        }
    }
    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
    companion object { private const val PICK_DB = 418 }
}
