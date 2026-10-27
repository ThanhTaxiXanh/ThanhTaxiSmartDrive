# Phase 4 alpha — Tìm địa điểm offline bằng SQLite FTS5

## Đã kết nối vào ứng dụng Android

Khi xe đã đỗ, mở **Cài đặt → Tìm điểm đến offline**. Công cụ nhập tệp
`.db` được tạo bằng `tools/build_offline_pois.py` từ CSV do chủ dự án cung cấp
và xác minh giấy phép. Android kiểm tra SHA-256 từ manifest, phiên bản schema
(1), integrity_check, FTS5 và RTree trước khi thay thế cơ sở dữ liệu đang dùng.
Tệp được lưu dưới `filesDir`, không yêu cầu Internet.

### Biên dịch gói dữ liệu

CSV UTF-8 (có header) cần các trường theo đúng thứ tự sau:

```text
id,name,address,province,category,lat,lon,aliases,source,verified
```

Ví dụ chạy với **CSV của bạn có quyền sử dụng**:

```bash
python3 tools/build_offline_pois.py --csv /path/to/licensed.csv \
  --out /path/to/pois-region.db --license "Nguồn và giấy phép thực tế" \
  --name "Khu vực khảo sát"
```

Kết quả: `pois-region.db` và `pois-region.db.manifest.json`.
Trên điện thoại, mở file manifest và sao chép `sha256` (64 ký tự hex),
dán vào màn hình POI; sau đó chọn tệp `.db` bằng Android File Picker.
Hash chỉ đảm bảo trùng bytes với manifest người dùng chọn, **không**
chứng nhận xuất xứ, độ chính xác tọa độ hay tính hợp pháp của đường.

## Tìm kiếm và chuyển sang tuyến khảo sát

- Nhập tên, địa chỉ hoặc tỉnh/thành với dấu hoặc không dấu.
  FTS5 dùng token-normalization `đ → d` và dấu tiếng Việt → ASCII,
  tìm prefix trên các từ; kết quả tối đa 25 mỗi truy vấn.
- Hiện tên, nguồn, tọa độ và cờ `verified` **do bên cấp tự khai**.
- Chạm địa điểm để sao chép tọa độ hoặc mở *xem trước tuyến khảo sát khi xe đỗ*.
- Màn hình định tuyến cũ chỉ liên kết điểm đến tới **nút khảo sát trong bán kính
  cho phép**. Nếu không có nút khớp hoặc thiếu allowedTurns, không tìm được tuyến;
  POI không tự tạo cạnh đường, chiều hoặc quyền đi qua.

## Ranh giới an toàn, kiểm thử và phát hành

- Android head-unit / Phone Activity; **không phải Android Auto template**.
  Công cụ chỉ mở qua kiểm tra trạng thái đỗ xe hiện có của Settings.
- Tùy phiên bản SQLite đi kèm Android, FTS5 có thể không sẵn có. Bộ nhập sẽ
  báo lỗi và không kích hoạt gói đó; cần kiểm tra trên API 26+ và thiết bị thật.
- Giới hạn file 250 MiB, tối đa 1 triệu POI ở builder. Truy vấn thực hiện ngoài
  UI thread; thay thế gói có backup/rollback nhưng **chưa** có tự động khôi phục
  sau mất điện đột ngột, chữ ký phát hành hay cập nhật delta.
- Gói này **không kèm dữ liệu địa chỉ/POI toàn quốc**, không có mạng đường
  thực tế được xác minh. Chưa có geocoding đầy đủ số nhà, tìm lân cận GPS,
  phân loại địa điểm động hay dẫn đường thương mại.
- Tests: `python3 -m unittest discover -s tools/tests -v`,
  `gradle :app:testDebugUnitTest :app:assembleDebug`.
  Bài kiểm thử SQLite Python dùng điểm tổng hợp, không phải điểm thực tế.
  Build APK trên CI là mốc kiểm tra mã, không thay thế road test.

Dữ liệu OpenStreetMap hoặc bất kỳ nguồn thứ ba nào phải được kiểm tra
giấy phép/attribution trước khi đóng gói. Bản alpha không tự tải dữ liệu Internet.
