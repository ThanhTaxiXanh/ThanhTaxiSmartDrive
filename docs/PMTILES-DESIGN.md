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
