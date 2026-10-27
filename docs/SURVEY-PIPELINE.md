# Pipeline CSV → Road Pack cho khảo sát thực địa

Mục đích: chuẩn hóa dữ liệu **được khảo sát và được phép sử dụng**, không tạo
tuyên bố bao phủ biển báo toàn quốc. Công cụ chỉ tạo các bản ghi
`reviewed=false`, `confidence=0`. Người kiểm duyệt vẫn cần xác minh từng
bản ghi tại hiện trường, kiểm tra điều kiện pháp lý hiện hành và phê duyệt
lượt rẽ được phép trước khi sử dụng cho hệ thống hỗ trợ lái xe.

## Đầu vào

CSV thứ nhất `segments.csv` có cột:

`id,from_lat,from_lon,to_lat,to_lon,road_kind,population_zone,direction,source_ref`

CSV thứ hai `signs.csv` có cột:

`id,segment_id,kind,lat,lon,kph,direction,source_ref`

Giá trị `kind`: R_420, R_421, P_127, END_SPEED. Chỉ P_127 mang
`kph`. `source_ref` là khóa tham chiếu tới hồ sơ khảo sát hợp pháp và
cần được quản lý ngoài ứng dụng (ví dụ ID ảnh khảo sát, ngày/giờ, vị trí,
chiều đường và người duyệt). Không ghi danh tính hoặc ảnh cá nhân vào
repository công khai nếu chưa có quyền.

## Chạy (Python 3, không cần gói ngoài)

```bash
python3 tools/road_pack_builder.py \
  --segments /path/to/segments.csv \
  --signs /path/to/signs.csv \
  --output /path/to/pilot.json \
  --series region-pilot-v1 --version 1 \
  --title "Pilot survey — not legally verified" \
  --source "Survey operator / dataset identifier" \
  --license "Documented and permitted data license"
```

Công cụ kiểm tra ID trùng, mã đường/biển, tọa độ và vị trí biển
sai lệch hơn 12m khỏi đoạn đường, tốc độ P.127 bất thường, nguồn trống
và kích thước dữ liệu. Kết quả tạo `pilot.json` cùng `pilot.json.sha256`.
Checksum chỉ kiểm tra toàn vẹn nội dung; **không** chứng minh đã thẩm định
nguồn dữ liệu hoặc hiệu lực pháp luật.

## Kiểm thử

```bash
python3 -m unittest discover -s tools/tests -v
```

CI chạy lệnh này trước khi unit test Kotlin và build APK.

## Các cổng QA còn phải xây dựng

1. Nhập nguồn khảo sát có giấy phép và dữ liệu công bố chính thức; lưu
   provenance theo từng biển, kiểm tra quyền sử dụng dữ liệu.
2. Đối chiếu ảnh biển/vị trí GPS/hướng/làn/thời gian, dedup theo không gian,
   kiểm tra đường trên cao, chiều đường song hành và giao lộ.
3. Duyệt pháp lý theo quy chuẩn Việt Nam hiện hành, không chỉ dựa vào bảng
   tốc độ chung hoặc một kết quả AI.
4. Ký số gói đã duyệt bằng khóa riêng của nhà phát hành, ghim public key
   tin cậy trong app; phát hành tăng dần có rollback kiểm soát.
5. Thử nghiệm đường thực tại Bắc Giang–Bắc Ninh, lưu biên bản QA.

Hiện tại **chưa có ký số hoặc nhà phát hành dữ liệu được ủy quyền**.
