# Android Auto — chẩn đoán không xuất hiện trên đầu xe

Tại commit này **chưa có Android Auto CarAppService và không có giao
diện Android Auto chính thức**. Cài đặt APK trên điện thoại không làm
Android Auto hiển thị bất cứ Android Activity nào trong launcher.

## Vì sao không xuất hiện

Trong AndroidManifest.xml, ứng dụng có `MainActivity` Android,
`SettingsActivity` và các màn hình offline thông thường; không khai báo
`<service android:name="...CarAppService">` cùng category được hỗ trợ
và `com.google.android.gms.car.application` metadata.

Phần nhập PMTiles, lịch, QR và media trong Activity tùy biến không thể
chuyển nguyên bố cục bốn ô sang Android Auto. Android Auto cần sử dụng
các mẫu hạn chế sao nhãng và danh mục app hợp lệ. Navigation category
đòi hỏi trải nghiệm navigation/turn-by-turn thực sự. Hiện dự án chỉ có
offline route graph preview khi xe đỗ; chưa có navigation engine hoàn chỉnh.
Không được đăng ký category NAVIGATION chỉ để đưa app vào launcher.

## Tiêu chí để bắt đầu phát triển Android Auto

1. Hoàn thiện điều hướng an toàn: graph đầy đủ, rerouting, cấm rẽ,
   street names, GPS map matching thực tế, thoại và các trường hợp mất GPS.
2. Thêm thư viện `androidx.car.app`, viết `CarAppService` và `Session`
   dùng Car App templates (NavigationTemplate/Map templates khi đáp ứng category).
3. Manifest với category `androidx.car.app.category.NAVIGATION` chỉ
   khi sản phẩm thực sự thuộc danh mục này; khai báo metadata cho host.
4. Thử trên Desktop Head Unit và thiết bị thực theo tiêu chuẩn
   chất lượng Android for Cars.
5. Phân phối qua Google Play internal test hoặc Internal App Sharing phù
   hợp; kiểm tra quy định review và công bố Google Play.

**Bật Android Auto Developer Mode/Unknown Sources không thay thế được
CarAppService hoặc quy trình tin cậy đối với Car App Library templates.**

Tham khảo:
- https://developer.android.com/training/cars/apps/library/set-up-project
- https://developer.android.com/training/cars/apps/navigation
- https://developer.android.com/training/cars/testing
