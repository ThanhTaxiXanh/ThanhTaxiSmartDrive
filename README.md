# ThanhTaxiSmartDrive

Dự án mã nguồn **Thanh Taxi Smart Drive** dành cho tài xế Việt Nam.

**Trạng thái:** bản alpha nền tảng Kotlin/Android. Đã có mã giao diện Dashboard thực, MapLibre, cập nhật tốc độ GPS, tính lịch Âm Việt Nam offline, chọn ảnh/QR cục bộ, điều khiển MediaSession khi được cấp quyền, cơ chế nhập dữ liệu đoạn đường có nguồn và engine giới hạn tốc độ với kiểm thử.

## Build / APK
1. Mở dự án bằng Android Studio có Android SDK 35 và JDK 17, hoặc cài Gradle 8.9.
2. Chạy `gradle :app:testDebugUnitTest :app:assembleDebug`.
3. Sau một GitHub Actions run thành công, vào **Actions → Android APK CI → Artifacts → ThanhTaxiSmartDrive-debug-apk** để lấy APK debug.
4. Nếu build chưa chạy hoặc thất bại, không có APK được xác nhận.

## Chạy ứng dụng
- Cấp quyền GPS chính xác. MapLibre demo style mặc định cần Internet; chuyển tới tile endpoint riêng có quyền sử dụng tại Cài đặt.
- Giới hạn tốc độ mặc định là **--** cho đến khi có dữ liệu đoạn đường được xác minh và GPS/đối sánh đủ tin cậy.
- Vào **Cài đặt** (khi dừng xe) để nhập gói JSON; xem [ROAD-PACK](docs/ROAD-PACK.md).
- Ảnh QR được người dùng tự chọn và lưu URI cục bộ. Media cần cấp Notification Listener.
- Ở ngang màn hình: bản đồ ~70%, lịch/QR/Media ~30%; đây là Android Activity dành cho head-unit, **không phải giao diện Android Auto chính thức**.
- Nhập PMTiles: Cài đặt → Nhập PMTiles (chỉ khi xe đỗ), kiểm tra header/SHA-256, bấm **Xem thử raster**, sau đó bấm **Dùng PMTiles raster này trên Dashboard**. PNG/JPEG/WebP có preview raster trên MapLibre; vector chưa có style/layer hiển thị tự động. PMTiles chưa bao phủ vùng GPS sẽ giữ camera ở tâm gói, không đi theo GPS ra ngoài vùng.
- Nếu thấy bản đồ xanh/trống: kiểm tra Internet/URL style nếu dùng map online; nếu dùng PMTiles kiểm tra định dạng raster, bounding box, tile thực tế, và dùng nút **Chuyển Dashboard về bản đồ online** để thử lại.
- Quản lý offline: vào Cài đặt → Quản lý vùng Bắc Giang/Bắc Ninh (khi xe đỗ), dùng Wi-Fi, chỉ với nguồn style/tile cho phép cache. Gói giới hạn quanh khu vực trung tâm (không phủ toàn tỉnh), zoom 11–15; kiểm tra trạng thái **HOÀN TẤT** trước khi tắt mạng.
- Dữ liệu đường/biển báo JSON có `series`/`version`, checksum SHA-256 cục bộ và khôi phục bản trước qua Cài đặt. Đây chỉ là kiểm tra toàn vẹn, không xác thực nguồn pháp lý.

## Tài liệu
- [Kiến trúc và giới hạn nền tảng](docs/ARCHITECTURE.md)
- [Lộ trình và trạng thái](docs/ROADMAP.md)
- [Định dạng dữ liệu đường](docs/ROAD-PACK.md)
- [Đồ thị và xem trước định tuyến offline](docs/ROUTING.md)
- [Nhập CSV khảo sát, kiểm tra nguồn](docs/SURVEY-PIPELINE.md)
- [Báo cáo lỗi/biển báo mâu thuẫn](docs/SURVEY-QA.md)
- [Nhập PMTiles v3 và checklist kiểm thử offline](docs/PMTILES-DESIGN.md)

## Cảnh báo
Ứng dụng alpha không phải công cụ xác định pháp lý giới hạn tốc độ; không dùng thay thế quan sát biển báo thực tế. Chưa có camera AI, điều hướng turn-by-turn/offline rerouting, dữ liệu biển báo Việt Nam đã kiểm duyệt hoặc Android Auto template chính thức. Đã có đồ thị tìm đường theo cạnh có cho phép rẽ và bản xem trước khi xe đỗ, nhưng **không có mạng tuyến thực tế**. Tải tile offline cần thử trên thiết bị thật với style được cấp phép, không khẳng định đã hoàn thành gói dữ liệu toàn tỉnh.

Mã nguồn do dự án phát triển; tham khảo bộ SKILL người dùng cung cấp và MapLibre/Android documentation. Không sao chép cơ chế vượt hạn chế nền tảng Android Auto.

## Android Auto

APK này là **Android Activity thường**; chưa khai báo `androidx.car.app.CarAppService`,
chưa có navigation templates/turn-by-turn Android Auto và chưa qua Google
Play testing/review. Vì vậy không được Android Auto liệt kê là ứng dụng
trên màn hình ô tô. Cho phép *Unknown sources* không giúp Car App Library
templated apps xuất hiện khi chưa có dịch vụ, danh mục app và phân phối
thử nghiệm phù hợp. Xem [Trạng thái Android Auto](docs/ANDROID-AUTO.md).

## Bản đồ đường phố mặc định

Bản alpha trước đây dùng `demotiles.maplibre.org/style.json`, là bản
MapLibre World trình diễn quốc gia, **không chứa đầy đủ đường sá ở zoom cao**.
Mặc định từ bản này dùng OpenFreeMap Liberty, bản đồ OSM street map trực tuyến,
không cần API key. Khi mất Internet muốn hiển thị đường cần nhập PMTiles raster
phù hợp hoặc tải gói offline có đủ tile theo điều kiện giấy phép.

Style: https://tiles.openfreemap.org/styles/liberty

Nguồn/tài liệu: https://openfreemap.org/quick_start/
