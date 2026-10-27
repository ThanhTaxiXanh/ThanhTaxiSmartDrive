# PMTiles v3 — importer và pilot raster preview

## Đã có mã trên Android

MapLibre Native 11.11.0 hỗ trợ URI `pmtiles://file:///...` đọc dữ liệu
từ `filesDir` nội bộ. PMTiles không phải cùng cơ chế với OfflineManager
cache tile mạng. <https://maplibre.org/maplibre-native/android/examples/data/PMTiles/>

Bản alpha có:
- SAF `ACTION_OPEN_DOCUMENT`: chọn PMTiles đã có trên máy, không yêu cầu quyền
  truy cập toàn bộ bộ nhớ và không tải tệp không rõ giấy phép
- yêu cầu khai báo nhà cung cấp, điều kiện giấy phép, attribution và
  xác nhận quyền sử dụng; đây là thông tin do người dùng tự khai
- đọc header 127 byte chuẩn PMTiles v3, đối soát magic, version,
  offsets/lengths, dạng tile, zoom và bounding box; giới hạn 1 GiB
- sao chép vào kho app theo tên SHA-256; có thể nhập expected SHA-256
  nhà phát hành cung cấp và kiểm tra trước khi nhận
- có thao tác xóa bản sao PMTiles đang chọn khỏi thư mục riêng
- đọc lại SHA-256 khi mở gói; phát hiện dữ liệu bị hỏng/sửa đổi
- với PMTiles raster PNG/JPEG/WebP: dựng style JSON local qua
  `Style.Builder().fromJson`, nguồn `pmtiles://file:///...`,
  preview MapLibre với camera theo center trong header

Gói vector có thể được nhập và giữ nhưng *không được render tự động* do
cần có `source-layer`, style đã kiểm duyệt, glyph/sprite và metadata riêng.
Gói AVIF/MLT cũng chưa có preview. MBTiles chưa có importer.

## Giới hạn quan trọng

1. Thành công nhập = header và checksum hợp lệ; không đủ để kết luận
   mọi tile, metadata và directory của archive đều tốt.
2. Callback style load thành công không chứng minh mọi tile hiện trên
   màn hình. Cần kiểm tra offline trên Android thật.
3. Giấy phép và attribution đều do người nhập khai, không xác thực nhà
   phát hành, quyền sử dụng dữ liệu hoặc hiệu lực pháp luật.
4. Tile raster không phải dữ liệu định tuyến, địa chỉ, biển báo hay
   giới hạn tốc độ. Road graph là nguồn riêng được nhập trong JSON.
5. Archive lưu nội bộ có thể chiếm gần 1 GiB, cần tự quản lý dung lượng.
   Gói cũ có thể vẫn tồn tại sau khi thay thế trong bản thử.
6. App không truyền GPS hay dữ liệu PMTiles lên máy chủ.

## Checklist thử offline trên thiết bị

- [ ] Nhập PMTiles từ nguồn được phép và đối chiếu SHA công bố độc lập
- [ ] Mở preview raster, nhìn thực tế thấy tile tại đúng tọa độ
- [ ] Bật chế độ máy bay và khởi động lại ứng dụng; tile vẫn hiển thị
- [ ] Pan/zoom qua toàn vùng trong min/max zoom của archive, không trắng map
- [ ] Kiểm tra bản đồ, attribution, font, glyph và nguồn tile đã cấp phép
- [ ] Test archive hỏng header, dữ liệu bị cắt, SHA sai, hết bộ nhớ, tệp lớn
- [ ] Xác nhận MapLibre style không gọi glyph/sprite/tile HTTPS khi offline
- [ ] Thử bản đồ vector với style được duyệt (chưa hỗ trợ trong UI)
- [ ] Kiểm thử Android head unit thật và nhiệt/CPU/bộ nhớ

Thông tin kỹ thuật: <https://github.com/protomaps/PMTiles/blob/main/spec/v3/spec.md>

## Phiếu kiểm thử offline trong ứng dụng

Màn hình nhập PMTiles có thêm phiếu kiểm tra dùng khi xe đỗ. Sau khi mở raster
và nhìn bằng mắt, người dùng cần tự xác nhận: tile hiển thị khi không có
mạng hoạt động, đã thử ít nhất hai mức zoom và đã thử sau khi khởi động
lại ứng dụng. Ứng dụng yêu cầu tại thời điểm ghi phiếu **không có active
network**. Phiếu JSON được lưu riêng và có thể xuất qua Android
Storage Access Framework.

Trạng thái `READY_FOR_HUMAN_REVIEW` chỉ là **báo cáo người dùng tự khai**,
không phải kiểm chứng tile-level tự động, không chứng minh đã bật chế độ
máy bay hay ảnh bản đồ/biển báo chính xác.

## Vào Dashboard với PMTiles cục bộ (bản sửa giao diện)

1. Chỉ khi xe đỗ: Dashboard → Cài đặt → Nhập PMTiles cục bộ.
2. Điền tên bộ dữ liệu, giấy phép, attribution; chọn gói raster PNG/JPEG/WebP.
3. Nhập, bấm **Xem thử raster**, sau đó **Dùng PMTiles raster này trên Dashboard**.
4. Quay lại Dashboard; màn hình kiểm tra checksum ở luồng nền rồi load
   style nguồn `pmtiles://file:///...` từ bộ nhớ riêng.
5. Nếu GPS nằm ngoài bounding box, bản đồ sẽ hiển thị tâm vùng gói
   và thông báo GPS nằm ngoài vùng; không tự kéo camera ra vùng tile trống.
6. Muốn dùng online trở lại: Cài đặt → **Chuyển Dashboard về bản đồ online**.

Bản map có thể vẫn trắng vì archive không có tile hợp lệ, zoom không phù
hợp, dữ liệu không bao phủ vị trí hoặc thiết bị không hỗ trợ. Phải quan sát
màn hình thiết bị thật để kết luận bản đồ offline hoạt động.

## Sửa lỗi bị chặn khi nhập PMTiles (10/2026)

Một phiên bản trước đã khóa công cụ PMTiles chỉ dựa vào intent extra
`verifiedStopped`, thường không có khi Settings được mở qua màn hình dọc
hoặc sau khi Android khôi phục activity. Vì vậy màn hình hiện
"Cần dừng xe trước khi mở công cụ" dù xe đang đỗ, và chưa từng mở picker.

Bản sửa cho phép màn hình Settings hỏi trực tiếp người dùng xác nhận
đã đỗ xe khi GPS không đủ dữ liệu, thay vì mặc định coi là đang lái.
Nếu GPS mới phát hiện xe đang di chuyển rõ rệt (trong Dashboard), việc
mở Settings vẫn bị chặn. Không được sử dụng tính năng trong lúc lái.

MapLibre demo world style được tự chuyển *khi sử dụng* sang OpenFreeMap
Liberty street style; URL tùy chỉnh khác được giữ nguyên.

**Không có PMTiles sẵn trong APK.** Phải có file nguồn hợp lệ và có
giấy phép offline. Nhập PMTiles có hai bước riêng: chọn file và xác nhận
quyền dùng, rồi xem thử raster và chọn làm nền Dashboard. MVT vector chưa
có style tự động.


## MVT vector Roads OSM (bản alpha tiếp theo)

Bộ nhập **không giả định** lớp đường tên `roads`. Theo chuẩn PMTiles v3,
khi Tile Type là MVT (1), metadata JSON của archive phải có `vector_layers`
với các `id` nguồn. Android đọc metadata trực tiếp từ file đã sao chép
theo byte offset của header, giải nén `none` hoặc `gzip`, giới hạn kích
thước nén 256 KiB và dữ liệu giải nén 1 MiB, sau đó điền danh sách chọn
source-layer ngay trong màn hình nhập PMTiles.

Cách dùng:

1. Chọn tệp `.pmtiles` MVT được phép lưu offline từ thiết bị và nhập thành công.
2. Dưới **Xem thử raster**, xem các lớp do metadata khai báo; chọn lớp chứa
   đường thực tế (tên có thể là `roads`, `transportation`, hoặc tên khác).
3. Bấm **Xem thử đường vector đã nhập**. Style tối giản nền sáng và hai nét
   đường, hoàn toàn lấy dữ liệu cục bộ; không cần glyph/sprite Internet.
4. Nếu đường hiện đúng khu vực: bấm **Dùng PMTiles raster/vector này trên Dashboard**,
   rồi quay lại Dashboard. Vị trí GPS nằm ngoài giới hạn file không được
   dùng làm vị trí camera; Dashboard giữ camera ở tâm vùng đã tải.

Dữ liệu **Roads (OSM) Vector Tiles** từ HOT/HDX là dữ liệu hình học
không phải dữ liệu dẫn đường đã kiểm chứng, không có hướng dẫn rẽ/biển báo/tốc
độ. Ứng dụng không tự suy diễn `reviewed=true` hay điều kiện pháp lý từ OSM.

Giới hạn: với metadata nội bộ nén Brotli/Zstd, ứng dụng có thể nhập PMTiles
nhưng chưa trích xuất source-layer bằng bản parser thử này. Tệp lớn hơn 1 GiB
bị từ chối. Dữ liệu AVIF/MLT chưa hỗ trợ preview. Chưa thử thực tế trên
điện thoại/đầu xe với chính archive HDX nêu trên.

Tham khảo:
- https://github.com/protomaps/PMTiles/blob/main/spec/v3/spec.md
- https://maplibre.org/maplibre-native/android/examples/data/PMTiles/

## Chất lượng tile archive và zoom (bản mới)

- Với PMTiles vector, nguồn MapLibre khai báo minzoom/maxzoom từ chính header
  (tối đa 22 theo MapLibre Style Spec). Nếu người dùng zoom lớn hơn maxzoom,
  MapLibre được phép overzoom từ tile mức cao nhất thay vì truy vấn mức cao
  không tồn tại.
- Với root directory dùng `none` hoặc `gzip`, importer giải mã có giới
  hạn, kiểm tra các trường varint, số entries và offset/length tới vùng tile
  hoặc leaf directory. Archive có root lỗi sẽ bị từ chối sớm.
- Với Brotli/Zstd nội bộ, bản kiểm tra root bằng Kotlin này chưa giải mã nên
  hiển thị trạng thái **chưa kiểm chứng root**; MapLibre Native vẫn có thể
  đọc archive. Không đồng nghĩa xác minh tất cả các tile/leaf.
- Màn hình nhập hiện lỗi MapLibre (style/tile failed). Sau khi nhập nên
  pan/zoom, bật Airplane Mode và kiểm tra thực tế xem các đường có vẽ đúng
  hay không.

Dữ liệu OSM đường phố không tự xác nhận giới hạn tốc độ, biển cấm, một chiều
hoặc tính hợp lệ của tuyến.
