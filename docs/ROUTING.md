# Định tuyến offline (giai đoạn thử nghiệm)

Bộ giải `OfflineRouteGraph` hoạt động hoàn toàn trên thiết bị, không cần API dẫn đường.
Nó **chỉ** duyệt các cạnh đường có `reviewed=true`, đúng chiều, và chỉ chuyển tiếp
khi cặp cạnh có `allowedTurns` cũng được đánh dấu `reviewed=true`.

Điều này ngăn thuật toán tự nối đường song hành, đường cầu vượt giao cắt trên hình vẽ,
và tự phỏng đoán việc được phép rẽ ở một ngã ba/ngã tư. Khi thiếu dữ liệu, trả về
"không có tuyến trên đồ thị" chứ không kết luận đường thực tế không đi được.

## Định dạng JSON bổ sung cho road pack

Thêm đối tượng `routing` bên cạnh `segments`, `trafficSigns`:

```json
{
  "routing": {
    "nodes": [
      {"id":"A","lat":21.0,"lon":106.0},
      {"id":"B","lat":21.001,"lon":106.0},
      {"id":"C","lat":21.002,"lon":106.0}
    ],
    "edges": [
      {
        "id":"AB","fromNode":"A","toNode":"B",
        "segmentId":"surveyed-AB","direction":"forward",
        "distanceMeters":111.2,"reviewed":false,"sourceRef":"TEST-ONLY"
      },
      {
        "id":"BC","fromNode":"B","toNode":"C",
        "segmentId":"surveyed-BC","direction":"forward",
        "distanceMeters":111.2,"reviewed":false,"sourceRef":"TEST-ONLY"
      }
    ],
    "allowedTurns": [
      {
        "fromEdge":"AB","viaNode":"B","toEdge":"BC",
        "reviewed":false,"sourceRef":"TEST-ONLY"
      }
    ]
  }
}
```

Các segment ID được tham chiếu phải có trong `segments` ở cùng gói JSON,
với tọa độ hai đầu trùng các node theo hướng đã khai báo. Ví dụ là **cấu trúc
minh họa không hợp lệ để sử dụng lái xe**; tất cả cờ reviewed cố ý là false.

* `direction` bắt buộc là `forward` hoặc `reverse` xét theo hai đầu của segment.
  Không tự giả định đường là hai chiều; đi ngược cần cạnh riêng cùng bằng chứng.
* Mỗi node và edge phải có ID duy nhất; node đầu/cuối cách đầu mút segment không quá 8 m.
* Chiều dài khai báo phải hợp lý so với khoảng cách địa lý (90%–150%).
* Cạnh đánh dấu `reviewed=true` cần `sourceRef`; lượt rẽ như vậy cũng cần `sourceRef`.
* Một giao cắt hình học trên hai đoạn không tạo thành node/lượt rẽ được phép.
* Tài xế chưa thể điều hướng theo giọng nói hoặc nhận dẫn đường rẽ từ thuật toán này.

## Dùng giao diện

Trên màn hình điện thoại hoặc Android head unit, khi xe đã đỗ: **Cài đặt →
Dẫn đường offline → Tính thử tuyến**. Nhập ID node bắt đầu và kết thúc,
bấm tính, xem chiều dài và polyline trên MapLibre.

Lõi tìm đường vẫn chạy không cần mạng nếu road pack được lưu trên máy;
nền bản đồ có thể trống khi offline nếu style và tile chưa được tải hợp lệ.

## Chưa giải quyết

* Không có đồ thị quốc gia hoặc dữ liệu tuyến Bắc Giang/Bắc Ninh thực tế
  trong repository.
* Không có turn-by-turn, rerouting khi di chuyển, hướng dẫn giọng nói,
  phân luồng theo loại xe, giờ cấm, tuyến một chiều mới đổi, cao độ, cầu/hầm
  hoặc ETA giao thông.
* Gói do người dùng nhập tự đánh dấu reviewed không phải xác minh pháp lý;
  luôn quan sát biển báo thật và không sử dụng bản thử nghiệm làm chỉ dẫn lái xe.

## Xem trước các điểm đổi hướng — bản thử nghiệm

Tại **Cài đặt → Dẫn đường offline → Tính thử tuyến**, chọn ID nút xuất
phát/đích thuộc bộ đồ thị khảo sát, bấm **Tính tuyến thử**. Ứng dụng dùng
Dijkstra trên dữ liệu cục bộ, chạy trên luồng nền. Nút **Xem các điểm đổi
hướng** mô tả sự đổi hướng *hình học* tại từng nút khảo sát cùng khoảng
cách tích lũy. Các nhãn "trái/phải/gần thẳng" được tính từ tọa độ WGS84,
**không phải chỉ dẫn tài xế rẽ**; ứng dụng không thực hiện đọc giọng nói hay
điều hướng theo vị trí xe.

Từng lượt chuyển tiếp giữa directed edges phải có chính xác
`routing.allowedTurns` phù hợp đã đánh dấu `reviewed`, kể cả khi
hai cạnh có cùng `segmentId`. Không được lấy một intersection hình
học hoặc dữ liệu OSM chưa duyệt làm bằng chứng được phép đi qua.

Màn hình xem trước hiện tải nguồn nền theo lựa chọn Dashboard:
PMTiles raster/vector có SHA-256 hợp lệ từ bộ nhớ riêng, hoặc style online
được chỉ định. Nếu chọn PMTiles lỗi, màn hình báo lỗi chứ không âm thầm
chuyển sang tile Internet. Thành công tải style **chưa chứng minh tile
đã hiển thị trên thiết bị**.

Dữ liệu thực địa/Bắc Giang–Bắc Ninh chưa được phát hành dưới dạng đồ
thị tuyến đáng tin cậy, do vậy chức năng chỉ dùng để kiểm thử bộ dữ liệu
do người dùng nhập khi xe đỗ.
