# ThanhTaxiSmartDrive

Dự án mã nguồn **Thanh Taxi Smart Drive** dành cho tài xế Việt Nam.

**Trạng thái:** bản alpha nền tảng Kotlin/Android. Đã có mã giao diện Dashboard thực, MapLibre, cập nhật tốc độ GPS, tính lịch Âm Việt Nam offline, chọn ảnh/QR cục bộ, điều khiển MediaSession khi được cấp quyền, cơ chế nhập dữ liệu đoạn đường có nguồn và engine giới hạn tốc độ với kiểm thử.

## Trạng thái Phase theo Master Prompt

- **Phase 1 — Technical Feasibility & Architecture: đã hoàn thiện hồ sơ thiết kế,**
  xác minh SDK/giới hạn Android Auto và tự động kiểm tra bộ tài liệu trong CI.
  Xem [Hồ sơ nghiệm thu Phase 1](docs/phase1/README.md),
  [PRD](docs/phase1/PRD.md),
  [Kiến trúc](docs/phase1/ARCHITECTURE.md),
  [Phân loại tính khả thi](docs/phase1/FEASIBILITY.md),
  [SDK và giấy phép](docs/phase1/SDK-AND-LICENSES.md),
  [Wireframes](docs/phase1/WIREFRAMES.md) và
  [44 yêu cầu truy vết](docs/phase1/REQUIREMENTS.csv).
- **Phase 2 đang triển khai.** Phase 3/4 có mã thử nghiệm nhưng chưa nghiệm thu.
  Phase 5 Android Auto chưa triển khai; Phase 6 mới có CI/debug build.

Phase 1 là *cổng thiết kế*, không chứng nhận tính pháp lý của tốc độ, bản đồ
offline trên xe thật hoặc xuất hiện trong Android Auto.

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

## Cập nhật PMTiles MVT Vector (alpha)

Trong Cài đặt → Nhập và xem thử tệp PMTiles, ứng dụng mới có thể đọc
metadata `vector_layers` từ tệp MVT v3, cho chọn `source-layer`
và vẽ các đường dưới dạng lớp Line trên nền sáng, không cần tile online.
Dùng nút **Xem thử đường vector đã nhập** trước khi đặt làm nguồn
Dashboard. Chức năng này chỉ là hiển thị đường, chưa phải điều hướng.
Nguồn dữ liệu và giấy phép phải do người sử dụng kiểm tra.

## Cập nhật tiếp theo: Free Drive và chất lượng PMTiles

- MapLibre **Theo xe / Tới xe**: thao tác kéo bản đồ bằng tay tạm ngừng
  bám camera vào GPS; bấm Tới xe để bật lại. Dấu vị trí GPS vẫn được cập nhật.
- PMTiles vector tuân thủ đúng `minzoom`/`maxzoom` của archive khi thiết lập
  vector tile source; MapLibre có thể overzoom sau maxzoom thay vì truy
  vấn các tile không tồn tại.
- Bộ nhập kiểm tra PMTiles v3 **root directory** khi dùng nội bộ không nén
  hoặc GZIP: số mục, phạm vi offset/length tới dữ liệu tile/leaf và varint.
  Nếu chỉ mục nén Brotli/Zstd, hiển thị chưa kiểm tra được, không tự nhận đã
  xác nhận. Kiểm tra root không thay thế kiểm thử tile thực tế.
- Nếu MapLibre phát lỗi load tile/style trong màn hình PMTiles, lỗi sẽ hiển
  thị ngay tại phần trạng thái thay vì chỉ có nền màu trống.

Bản đồ mẫu chỉ để kiểm thử; ứng dụng vẫn chưa phải Android Auto chính thức
hoặc hệ thống chỉ dẫn/giới hạn tốc độ đã xác nhận.

## Truy vấn tên đường PMTiles và xác nhận GPS (alpha)

- Với PMTiles **MVT Vector**, màn hình Cài đặt → Nhập PMTiles hiển thị tên các
  `source-layer` và tên trường thuộc tính được khai báo trong metadata. Khi
  đang đỗ xe, chạm trực tiếp vào nét đường trên preview để xem tên (ưu tiên
  `name:vi` / `name`) hoặc mã tuyến (ví dụ `ref`) được chứa trong tile.
- Khi đỗ xe, tính năng tương tự có ở Dashboard đang sử dụng PMTiles vector.
  Nếu không có tên/mã trong dữ liệu OSM, ứng dụng không tự dựng tên đường.
- Không tải font/glyph online chỉ để dán nhãn lên bản đồ PMTiles offline;
  việc hiển thị trực tiếp nhãn dọc đường trên bản đồ vẫn chưa hoàn thiện.
- Với dữ liệu khảo sát đã đánh dấu `reviewed` và có nguồn, hệ thống chỉ
  sử dụng một kết quả đối sánh GPS sau **ba mẫu GPS mới liên tiếp** khớp
  cùng đoạn đường, không tính các lần refresh giao diện là GPS mới. Nếu
  GPS mất, nhảy xa hoặc đổi đột ngột đường thì hủy kết quả cho tới khi
  xác nhận lại. Giới hạn tốc độ vẫn không được xem là chứng nhận pháp lý.

## Nhãn tên đường offline (Android native overlay)

Từ bản cập nhật này, trên PMTiles MVT vector đã chọn lớp chứa đường, ứng dụng
có thể hiển thị tối đa **9 nhãn tên/mã tuyến trong khung bản đồ**, lấy trực
tiếp từ thuộc tính feature của các line đã render (ưu tiên `name:vi`,
`name`, sau đó `ref`). Nhãn được Android Canvas vẽ bằng font hệ thống,
**không cần tải glyph từ mạng và không chứa file font tải ngoài**.
Lớp nhãn sẽ bị bỏ trống khi PMTiles không có tên hoặc lớp vừa chọn chưa
hiển thị line. Khi xe đang đỗ, vẫn có thể chạm đường để tra tên chi tiết.

Bạn có thể bật/tắt tính năng ở **Cài đặt → Hiển thị tên/mã tuyến offline**.
Đây là nhãn hiển thị thử nghiệm lấy mẫu điểm trên màn hình, không phải
cartography label dọc toàn đường và không bảo đảm tên đường chính xác.
Nhãn và thẻ OSM **không** làm bằng chứng xác minh biển báo hay tốc độ.

Khi có road pack khảo sát và `routing.allowedTurns`, sự chuyển đoạn GPS
cũng phải khớp với một lượt rẽ được đánh dấu `reviewed` trong đồ thị.
Ứng dụng không tự coi hai đoạn có hình học giao nhau là nối được.
Thiếu dữ liệu topology sẽ hiện `--` hoặc chưa xác định, không suy đoán.

## Xem trước tuyến khảo sát offline khi xe đỗ

Cài đặt → **Tính thử tuyến trên dữ liệu road pack đã nhập**; chọn hai
node ID thuộc bộ dữ liệu `routing`, bấm Tính tuyến thử rồi
**Xem các điểm đổi hướng**. Mỗi mô tả chỉ là đổi hướng hình học trên
đồ thị do người dùng tự khai và không có ý nghĩa chỉ dẫn rẽ hợp pháp
trên đường thật. Nền bản đồ thử tương thích cả PMTiles cục bộ đã nhập
và style online đã chọn. Chưa có bản đồ đường được kiểm định cho toàn
Bắc Giang–Bắc Ninh; cũng chưa có Android Auto CarAppService.

## Phase 2 — Thử tìm nút khảo sát và lệch tuyến (khi đỗ xe)

Màn hình Cài đặt → **Tính thử tuyến trên dữ liệu road pack đã nhập** nay
nhận ID nút hoặc tọa độ dạng `21.12345,106.54321` gần một nút có trong
graph. Đây **không phải tìm kiếm địa chỉ trên toàn Việt Nam**.
Sau khi tính tuyến thử, có thể nhập tọa độ *giả lập*, bấm
**Mô phỏng 3 GPS** và, nếu có tuyến khảo sát hợp lệ khác, bấm
**Xem tuyến khác**. Không kết nối với GPS thật để tự điều hướng, không có
mệnh lệnh rẽ hay ETA tin cậy. Xem
[giới hạn Phase 2](docs/phase2/README.md).

## Phase 2 — GPS thật chỉ để kiểm tra lúc đỗ xe

Trong **Cài đặt → Tính thử tuyến**, sau khi nhập `road pack` chứa
`routing.nodes` và tính tuyến, bạn có thể chọn **Bật GPS thật (khi đỗ)**.
Ứng dụng hỏi quyền vị trí chính xác, không ghi lịch sử, tự tắt khi rời
màn hình hoặc GPS phát hiện tốc độ ≥0,8 m/s. Chỉ khi mẫu GPS đủ mới,
chính xác và có tốc độ thấp, nút **Điểm đi = GPS** mới hoạt động.

Lưu ý đây là **chẩn đoán khi xe đã đỗ**, không phải chế độ theo dõi rẽ
đường khi đang lái. Nếu GPS không cung cấp tốc độ, ứng dụng giữ
trạng thái chưa xác định; bạn có thể dùng chế độ mô phỏng riêng.
Có thể tìm `displayName` nếu nó được người dùng đưa vào gói khảo sát
có nguồn và giấy phép; chưa có tìm địa chỉ toàn quốc.

Đọc [giới hạn GPS thật](docs/phase2/REAL-GPS-DIAGNOSTIC.md) và
[hợp đồng dữ liệu tên điểm](docs/phase2/LOCAL-SEARCH-SCHEMA.md).

## Phase 2 — Điều hướng chủ động trên Dashboard (alpha)

Khi đã đỗ xe, nhập road pack có `routing.nodes/edges/allowedTurns`,
vào **Cài đặt → Tính thử tuyến**, chọn điểm xuất phát/đến và bấm
**Bắt đầu theo tuyến thử (GPS thật)**. Dashboard sẽ hiện tuyến màu vàng,
trạng thái theo GPS, khoảng cách còn lại và hình học điểm đổi hướng.
**Dừng tuyến** kết thúc phiên; khi lệch tuyến, gợi ý đường thay thế chỉ
được chấp nhận sau khi xe đỗ. GPS thiếu tin cậy sẽ tạm ẩn hướng dẫn.

**Đây là alpha điều hướng**, chưa có tìm địa chỉ quốc gia, luật giao thông
đã xác thực, ETA/giọng nói, tự tính lại tuyến khi đang chạy, Android Auto
hay nghiệm thu thực địa. Không thay thế biển báo và quyết định của tài xế.

- [Thiết kế và hướng dẫn active navigation](docs/phase2/ACTIVE-NAVIGATION.md)
- [Android Emulator API35 workflow](.github/workflows/android-emulator.yml)
- [Test và điều kiện nghiệm thu Phase2](docs/phase2/FIELD-TRIAL-PROTOCOL.md)

## Phase 2 — ETA và mô tả tuyến bằng giọng Việt offline (thử nghiệm)

Dashboard hiện tính **ETA ước lượng** từ khoảng cách đồ thị còn lại và vài
mẫu tốc độ GPS hợp lệ. Nếu dữ liệu không tin cậy, ETA hiển thị `—`.
Nhãn **Bước n/m** chỉ mô tả điểm đổi hướng trên đồ thị khảo sát.

Khi xe đỗ, trên màn hình **Tính thử tuyến**, bạn có thể tích chọn
**Phát mô tả điểm đổi hướng bằng tiếng Việt offline** trước khi bấm bắt
đầu; mặc định tắt. Nếu thiết bị không có giọng tiếng Việt hoạt động
offline, app không đọc tiếng và không tự chuyển sang TTS qua mạng.
Giọng nói chỉ mô tả hình học thử nghiệm, không ra lệnh rẽ hợp pháp.

Chi tiết: [ETA & giọng nói](docs/phase2/ETA-VOICE-TESTING.md).

## Phase 2 — Lọc hạn chế loại xe và giờ (đồ thị thử nghiệm)

Road pack có thể khai báo `routing.edges[].restrictions` hoặc
`routing.allowedTurns[].restrictions` để lọc theo loại xe
`TAXI/CAR/MOTORBIKE/BUS/TRUCK/OTHER` và khung giờ Việt Nam.
Nếu thiếu ngữ cảnh, bộ giải từ chối cạnh/lượt rẽ có điều kiện;
nếu giờ cấm thay đổi trong khi theo tuyến, app tạm ẩn hướng dẫn.
Tìm tên điểm khảo sát cũng đã hỗ trợ bỏ dấu tiếng Việt.

Toàn bộ dữ liệu hạn chế do người dùng tự khai **chưa được xác minh pháp lý**.
[Chi tiết và gói kiểm thử giả lập](docs/phase2/ROUTING-RESTRICTIONS.md).

## Phase 2 — Giờ vào từng đoạn, làn xe và điểm đến offline

Tại **Cài đặt → Tính thử tuyến** khi đã đỗ: chọn loại xe (mặc định TAXI),
làn giả định (**chưa rõ** mặc định), nhập ID/biệt danh có hoặc không dấu hay điểm
`routing.destinations` trong road pack. Bấm **Tính tuyến thử**; xem ETA mô hình
và bấm **Xem các điểm đổi hướng** để xem giờ vào/ra từng cạnh đường. Khi
khởi hành, Dashboard kiểm tra lại thời gian/các hạn chế; tuyến có hạn chế làn
tự khai không được dùng làm hướng dẫn GPS chắc chắn.

Không coi `modelSpeedKph` là giới hạn pháp lý. Dữ liệu POI/giao thông cần nguồn,
giấy phép và kiểm tra độc lập trước khi dùng ngoài đường.

[Schema, ví dụ JSON và giới hạn an toàn](docs/phase2/ROUTING-RESTRICTIONS.md).

## Tìm điểm đến offline bằng SQLite FTS5 (Phase 4 alpha)

Cài đặt → Tìm điểm đến offline (chỉ khi xe đỗ): nhập gói SQLite FTS5/RTree
do người dùng tạo từ CSV có giấy phép, kiểm SHA-256, tìm địa chỉ/tên có dấu
hoặc không dấu, sao chép tọa độ hay chuyển sang xem trước tuyến khảo sát.
Không kèm dữ liệu POI toàn quốc và không chứng nhận tuyến hợp pháp.
Chi tiết: [Tìm POI offline](docs/phase4/OFFLINE-POI-SEARCH.md).
