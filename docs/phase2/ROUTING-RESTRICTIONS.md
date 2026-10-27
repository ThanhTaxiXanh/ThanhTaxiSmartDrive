# Phase 2 — Khung hạn chế loại xe và giờ trong định tuyến offline (ALPHA)

**Đây là bộ lọc trên dữ liệu khảo sát do người dùng nhập, KHÔNG phải xác nhận một đường hoặc lượt rẽ có thể đi hợp pháp.** Kiểm thử Android Emulator/JVM chỉ chứng minh việc lọc thuật toán.

## Đã triển khai

- Cạnh có hướng được khai báo sẵn; đi ngược muốn hợp lệ phải có **một cạnh reverse riêng**, không suy diễn đường hai chiều.
- `routing.edges[].restrictions` và `routing.allowedTurns[].restrictions` có thể khai báo `allowedVehicles` (TAXI, CAR, MOTORBIKE, BUS, TRUCK, OTHER) hoặc `blockedWindows`. Mỗi record có hạn chế **bắt buộc** có `reviewed=true` và `sourceRef` do người nhập cung cấp. Các thông tin đó chưa phải chứng cứ pháp lý.
- `blockedWindows` dùng **ngày ISO 1=Thứ Hai … 7=Chủ Nhật** và số phút trong ngày 0–1439 tại **Asia/Ho_Chi_Minh**; điểm đầu bao gồm, điểm cuối không bao gồm. `startMinute > endMinute` nghĩa là qua nửa đêm, tính cả ngày kế tiếp.
- Bộ Dijkstra xét cả cạnh và từng cặp lượt rẽ; nếu thiếu ngữ cảnh loại xe hoặc thời gian cho tuyến có hạn chế, phần đó bị loại (**fail closed**). Không có dữ liệu rẽ không có nghĩa là đường thật bị cấm.
- Android màn hình chọn tuyến khởi tạo ngữ cảnh **TAXI, ngày/giờ Việt Nam hiện tại**. Dashboard đọc lại ngữ cảnh thời gian tại lúc khởi hành; bộ máy kiểm tra lại khi mỗi mẫu GPS tới. Nếu hạn chế khảo sát thay đổi hiệu lực, hướng dẫn và ETA bị tạm ngưng; không tự đổi tuyến.
- Mô tả tìm tên điểm cho phép nhập có dấu hoặc không dấu, trên **displayName thuộc gói khảo sát**, không có chỉ mục địa chỉ/POI quốc gia.

## Ví dụ JSON cho một cạnh (tất cả chỉ là dữ liệu giả lập)

```json
{
  "id": "bc", "segmentId": "bc", "fromNode": "B", "toNode": "C",
  "direction": "forward", "distanceMeters": 104.0,
  "reviewed": true, "sourceRef": "TEST-NOT-LEGAL",
  "restrictions": {
    "allowedVehicles": ["TAXI"],
    "blockedWindows": [
      { "days": [1, 2, 3, 4, 5], "startMinute": 420, "endMinute": 540 }
    ]
  }
}
```

Ví dụ là khung khảo sát tự khai: tuyến bị **lọc trong đồ thị** lúc 07:00–09:00 các ngày Thứ Hai–Thứ Sáu. **Không có nghĩa luật giao thông tại tọa độ này cấm taxi.** Nếu không có `restrictions`, đồ thị không có hạn chế được ghi nhận — KHÔNG đồng nghĩa tuyến thật được phép.

Định dạng đầy đủ có sẵn ở [synthetic-restricted-routing.json](../../tools/fixtures/synthetic-restricted-routing.json). Có thể nhập vào bản alpha để xem thuật toán trên dữ liệu giả lập, nhưng **không dùng đi đường thực tế**. Tên và tọa độ trong fixture hoàn toàn do bộ kiểm thử tạo ra.

## Tiêu chí tự động

| Test | Phải chứng minh |
|---|---|
| `RoutingRestrictionsTest` | Lọc theo TAXI/CAR, khung thứ/giờ, qua nửa đêm, không mặc nhiên đi ngược |
| `RoutingRestrictionsTest` / `ActiveNavigationEngine` | Chưa đủ ngày/giờ thì không chọn cạnh có điều kiện; thời điểm chuyển từ mở→cấm làm treo hướng dẫn |
| `test_survey_routing_qa.py` | Sai schema, giới hạn, nguồn, loại xe/giờ, duplicate bị từ chối |
| `NavigationEmulatorTest` | Import gói JSON tổng hợp và thử lọc theo loại xe/giờ trong Android runtime |

## Những gì CHƯA làm

Không có dữ liệu hạn chế hợp pháp/giấy phép đã xác minh, độ cao cầu hầm, lane-level restrictions, biển phụ, chặn tuyến tạm thời, dự báo lưu lượng, ETA theo tình trạng đường, hay tính được chính xác thời điểm tới **mỗi** đoạn hạn chế. Bộ lọc dùng đồng hồ hiện tại, không thay thế kiểm tra hạn chế lúc xe đến đoạn sau. Kiểm thử trên xe thật được hoãn theo yêu cầu của người dùng.

## Đánh giá an toàn

Không suy ra quyền đi đường từ nhãn `reviewed`; nếu bản đồ thiếu kết nối hoặc điều kiện, hiển thị thiếu tuyến khảo sát thay vì tạo lượt rẽ. Android Auto `CarAppService` riêng vẫn chưa có. Các giới hạn đã ghi không được dùng như thông tin tốc độ hợp pháp hoặc hướng dẫn lái tự động.

## Phase 2 — ETA vào từng đoạn, loại xe và làn (bản nâng cấp)

Màn hình xem trước tuyến (**khi đã đỗ**) nay có lựa chọn **TAXI/CAR/MOTORBIKE/BUS/TRUCK/OTHER**, làn **chưa rõ** hoặc làn 1–6 (giả lập). Bộ tìm đường mới `routeTimed` dự báo thời điểm *vào/ra từng cạnh* từ đồng hồ Việt Nam ở lúc tính tuyến và mô hình vận tốc. Trong **Xem các điểm đổi hướng** có bảng giờ `HH:mm` cho từng cạnh; việc khởi hành trên Dashboard kiểm tra lại tuyến để từ chối lựa chọn hết hạn. Khi đang theo tuyến GPS, bản dự báo còn lại được kiểm tra lại theo thời gian; nếu gặp khung cấm khảo sát, hướng dẫn bị tạm ngưng.

Giá trị `routing.edges[].modelSpeedKph` (5–110, không bắt buộc) chỉ là **giả định vận tốc hành trình**. Khi không khai báo, mô hình dùng **30 km/h**. Đây **không phải giới hạn tốc độ hợp pháp, không được ưu tiên hơn biển báo** và không tính kẹt xe, đèn tín hiệu, dừng đón khách hay việc chờ tới khi hết giờ cấm. Nếu một cạnh chỉ đi được sau khi chờ, thuật toán không coi chờ xe là hợp lệ; nó có thể chọn đường khác hoặc không có đường.

Các điều kiện tùy chọn trong `restrictions`:

```json
{
  "allowedVehicles": ["TAXI", "CAR"],
  "blockedVehicles": ["TRUCK"],
  "allowedLanes": [1, 2],
  "blockedWindows": [
    {"days":[1,2,3,4,5], "startMinute":420, "endMinute":540}
  ]
}
```

Khối điều kiện có thể nằm ở cạnh hoặc lượt rẽ. Thiếu loại xe/giờ/làn thì không dùng được điều kiện tương ứng. Nếu chọn làn bằng tay để **mô phỏng**, ứng dụng *không khẳng định GPS biết làn nào*: tuyến có hạn chế theo làn chỉ cho xem thử lúc đỗ, không cho phát hướng dẫn chủ động. Người nhập phải cung cấp `reviewed=true` và `sourceRef`; đây chỉ là nguồn **tự khai**, không phải chứng thực pháp luật.

Có thể bổ sung `routing.nodes[].aliases` (tối đa 8 tên), và `routing.destinations[]` để tra điểm có tên/biệt danh, loại **PICKUP/DROPOFF/PARKING/REST/OTHER** và `nodeId` dẫn đến nút khảo sát thật sự tồn tại. Ví dụ:

```json
{
  "id": "demo-pickup-01",
  "title": "Điểm đón thử nghiệm",
  "nodeId": "B",
  "category": "PICKUP",
  "aliases": ["Diem don", "Dón khách"],
  "sourceRef": "TEST-NOT-REAL-POI"
}
```

Tìm kiếm dùng dữ liệu trong **gói offline đã nhập**, hỗ trợ không dấu và tìm theo nhiều từ; trùng tên buộc người dùng chọn gợi ý có ID. **Không có bộ geocoder địa chỉ toàn quốc, không tự tạo tọa độ của cửa hàng hay nhà dân.**

Fixture [synthetic-restricted-routing.json](../../tools/fixtures/synthetic-restricted-routing.json) đã bổ sung nút và điểm đón/đỗ, làn 2, xe TRUCK bị loại và vận tốc mô hình 24 km/h trên cạnh `bc`. Đây chỉ là bản đồ giả lập — KHÔNG dùng để lái xe thực.
