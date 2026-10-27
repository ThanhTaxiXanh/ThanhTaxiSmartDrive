# Định dạng dữ liệu đường và biển báo (JSON schema 1)

Dự án **không** cung cấp cơ sở dữ liệu giới hạn tốc độ toàn Việt Nam. Người dùng phải cung cấp một gói
có nguồn, giấy phép, dữ liệu khảo sát và kiểm tra thực địa. Đánh dấu `reviewed=true` là khai báo của
bên tạo gói, **không** phải chứng nhận của cơ quan quản lý.

## Ví dụ JSON thuần minh họa; tọa độ tổng hợp, không dùng khi lái xe

```json
{
  "schema": 1,
  "title": "TEST ONLY / KHÔNG DÙNG KHI LÁI XE",
  "source": "SYNTHETIC_TEST_NO_ROAD_SURVEY",
  "license": "Demo synthetic data",
  "segments": [{
    "id": "demo-northbound",
    "from": [21.0, 106.0],
    "to": [21.001, 106.0],
    "roadKind": "TWO_WAY_OR_SINGLE_LANE_ONEWAY",
    "populationZone": "UNKNOWN",
    "direction": "north",
    "reviewed": false
  }],
  "speedSigns": [],
  "trafficSigns": [{
    "id": "demo-r420",
    "segmentId": "demo-northbound",
    "kind": "R_420",
    "lat": 21.0005,
    "lon": 106.0,
    "direction": "north",
    "reviewed": false,
    "confidence": 0.0
  }]
}
```

Cấu trúc `trafficSigns` mới: `id` duy nhất, `segmentId`, `kind` thuộc `R_420`, `R_421`,
`P_127`, `END_SPEED`; `lat`/`lon` nằm trên đoạn đường; `kph` bắt buộc nếu `P_127`.
Trường tùy chọn: `direction`, `lane` (từ 1), `vehicle`, `validFrom`, `validUntil`
(timestamp Unix milliseconds), `confidence` (0..1), `reviewed`. Tọa độ ngoài đường hơn 12 m,
ID trùng, số tốc độ không hợp lệ và đoạn dài hơn 2,5 km bị từ chối.

`END_SPEED` là sự kiện logic đã khảo sát xác định hết hiệu lực hạn chế; không mặc định tương đương
với bất kỳ mã biển cụ thể nào nếu chưa được kiểm tra theo QCVN 41 và quy định hiện hành.

`speedSigns` (tên tương thích từ gói cũ) chỉ chấp nhận `source=VERIFIED_MAP` hoặc
`UNVERIFIED_MAP` cho thuộc tính tốc độ *toàn đoạn*, không chấp nhận `VERIFIED_SIGN`
thiếu tọa độ. Dữ liệu gắn `VERIFIED_MAP` vẫn yêu cầu `reviewed=true`, phạm vi
phương tiện/làn/chiều/thời gian khớp, và không mâu thuẫn với nguồn khác.

## Cơ chế Free Drive

1. Đọc fix GPS thật, từ chối GPS cũ, accuracy trên 20 m và hướng lệch khi xe di chuyển.
2. Xét các đoạn đường đã đánh dấu khảo sát. Nếu đường song hành gần nhau không phân biệt
   được, **không** tự đoán đoạn đường.
3. Chỉ xét biển có ID đoạn, chiều, thời gian, làn và phương tiện áp dụng, `reviewed=true`
   và confidence ít nhất 0.9.
4. Chỉ áp dụng R.420/R.421/P.127 sau khi xe **đã vượt** vị trí biển (cộng biên GPS).
5. Hết hiệu lực P.127 chỉ khi có sự kiện `END_SPEED` được xác minh trên đoạn. Không truyền
   trạng thái qua đoạn kế tiếp khi chưa có topology đã xác minh.
6. Khi nguồn mâu thuẫn hoặc đối sánh không chắc: hiển thị `--`, không khẳng định giới hạn.

Các đoạn trên cầu/vượt chồng tầng vẫn yêu cầu thông tin cao độ và topology tốt hơn; thuật toán
hiện tại không thay thế đối sánh bản đồ dẫn đường đa tầng. Biển được hiển thị bằng MapLibre markers
cho phạm vi tối đa 800 m, tối đa 25 marker gần xe nhằm tránh rối mắt.

## Phát triển gói toàn quốc

Giai đoạn tiếp theo phải có nhập dữ liệu OSM/khảo sát, đối soát hướng biển với road geometry, QA
làn đường, kiểm tra giấy phép ODbL/nguồn riêng, phát hành gói versioned có chữ ký, kiểm tra hash
và cơ chế rollback. Không dùng dữ liệu thử như dữ liệu pháp lý thật.
