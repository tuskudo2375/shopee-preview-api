# Đồng hồ Việt – Widget 4×2

Widget Android native dành cho màn hình chính:

- Hiển thị giờ 24h bằng chữ số ảnh Montserrat Black lớn; riêng mọi chữ số `1` trong phần giờ có màu đỏ.
- Hiển thị thứ, ngày dương lịch dạng `DD/MM/YYYY` và lịch âm bằng tiếng Việt.
- Tính và hiển thị lịch âm Việt Nam trực tiếp trên máy.
- Lấy tọa độ mới bằng LocationManager, không phụ thuộc Google Play Services; dùng Geocoder để hiện đúng quận/huyện hoặc phường/xã.
- Đọc cảnh báo và dữ liệu vị trí từ dịch vụ thời tiết ColorOS (`com.coloros.weather2`) khi được hệ thống cho phép.
- Dùng dự báo theo đúng tọa độ hiện tại để tránh rơi về dữ liệu tổng thể của tỉnh; nếu ColorOS chỉ trả dữ liệu thô, cảnh báo hệ thống vẫn được giữ lại.
- Icon thời tiết là ảnh PNG sticker có viền đậm, không dùng bộ vector cũ.
- Tự làm mới giờ mỗi phút và thời tiết theo chu kỳ 30 phút.
- Nhấn vùng thời tiết để làm mới, nhấn vùng còn lại để mở phần cấp quyền.

## Cách dùng

1. Cài APK sau khi build project.
2. Mở **Đồng hồ Việt** một lần và cho phép quyền vị trí; trong ColorOS nên chọn **Vị trí chính xác**.
3. Thêm widget **Đồng hồ Việt** kích cỡ 4×2 vào màn hình chính.

## Build

Mở thư mục này bằng Android Studio và build biến thể `debug`. Project dùng Android Gradle Plugin 8.7.3, compile SDK 35, min SDK 26 và không có thư viện ngoài.

Widget cần quyền Internet và vị trí cho đường dự phòng. Trên OPPO/ColorOS, dữ liệu hệ thống
được ưu tiên và widget cũng lắng nghe các broadcast cập nhật thời tiết/cảnh báo của dịch vụ.
Khi chưa có dữ liệu, widget vẫn hiện giờ và lịch âm nhưng phần thời tiết sẽ chờ lần cập nhật sau.
