# PMTiles / MBTiles — trạng thái tính khả thi

MapLibre Native Android từ 11.7.0 nhận URL `pmtiles://file:///...` để
đọc PMTiles cục bộ. SDK dự án hiện ghim 11.11.0.
Theo tài liệu MapLibre, PMTiles **không** dùng chức năng tải/cache offline
region của `OfflineManager`; đây là hai cơ chế độc lập.

Nguồn tham khảo kỹ thuật:
https://maplibre.org/maplibre-native/android/examples/data/PMTiles/

Muốn triển khai cần:
- chọn PMTiles được cấp phép bởi nhà cung cấp (không tự tạo gói chứa
  dữ liệu biển báo/đường chưa kiểm chứng);
- sao chép tệp qua Android Storage Access Framework vào thư mục app,
  kiểm tra header, dung lượng, checksum, quyền đọc và khả năng xóa;
- cung cấp style JSON sử dụng `pmtiles://file://` đến archive đã chọn;
  khai báo đúng vector source layer IDs, glyphs, sprites, attribution;
- thực nghiệm trên thiết bị thật khi bật Airplane Mode.

**Chưa có bộ nhập archive PMTiles/MBTiles trong APK.** Không nên hiển thị
khu vực "đã có bản đồ offline" chỉ vì người dùng chọn một file chưa
thử render. MBTiles không tự động trở thành nguồn MapLibre Native hợp lệ;
phải có adapter/tile source hoặc chuyển đổi được cấp phép.
