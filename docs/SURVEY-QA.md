# Kiểm định dữ liệu khảo sát — local QA

Đã có CLI `tools/survey_qa.py` kiểm tra cấu trúc một gói JSON.
Công cụ này **không** xác minh tính hợp pháp của quy định biển báo và không
biến `reviewed=true` thành thông tin chính thức.

Chạy:
```bash
python3 -m tools.survey_qa --pack /path/to/pilot.json --report /path/to/qa.json
```

Công cụ phát hiện:
- thiếu `sourceRef` riêng cho từng đoạn đường hoặc biển báo;
- ID trùng, biển trỏ đến đoạn không tồn tại, giá trị P.127 ngoài khoảng;
- hai giới hạn P.127 khác nhau trong vòng 8 mét trên cùng đoạn/cùng chiều;
- cặp R.420/R.421 ở cùng vị trí/cùng chiều;
- các biển có thể bị lặp gần nhau;
- số lượng bản ghi vượt khả năng của ứng dụng.

Báo cáo lưu `passed`, `stats` và `issues` (error/warning).
Lỗi cấu trúc khiến CLI thoát mã 2; cảnh báo không đồng nghĩa với
cấm sử dụng theo pháp luật.

Giới hạn: so sánh trên tọa độ phẳng khoảng cách Haversine; chưa có
dữ liệu camera thực địa, luật thời điểm, biển phụ, phân làn, đường chồng tầng,
biển tạm hay bản đồ quốc gia. QA đã qua kiểm tra cấu trúc vẫn cần
thẩm định giấy phép và hiệu lực bởi con người.

## QA đồ thị có hướng

`tools/survey_routing_qa.py` được tích hợp vào công cụ
`tools/survey_qa.py`: nó kiểm tra nút, ID cạnh, trùng ID, endpoint của
cạnh với `segmentId`, hướng forward/reverse, khoảng cách, sự liên thông
đúng node giữa hai cạnh trong `allowedTurns` và yêu cầu `sourceRef`
đối với cạnh/lượt rẽ được đánh dấu reviewed. Cạnh reviewed trỏ đến
segment chưa reviewed sẽ bị từ chối. Đây chỉ là kiểm tra cấu trúc và
dữ liệu tự khai, **không** xác nhận lượt rẽ hợp pháp hay dữ liệu quốc gia.
