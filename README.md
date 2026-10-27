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

## Tài liệu
- [Kiến trúc và giới hạn nền tảng](docs/ARCHITECTURE.md)
- [Lộ trình và trạng thái](docs/ROADMAP.md)
- [Định dạng dữ liệu đường](docs/ROAD-PACK.md)

## Cảnh báo
Ứng dụng alpha không phải công cụ xác định pháp lý giới hạn tốc độ; không dùng thay thế quan sát biển báo thực tế. Chưa có camera AI, routing, offline map tiles, cơ sở dữ liệu biển báo Việt Nam hoặc Android Auto template chính thức.

Mã nguồn do dự án phát triển; tham khảo bộ SKILL người dùng cung cấp và MapLibre/Android documentation. Không sao chép cơ chế vượt hạn chế nền tảng Android Auto.
