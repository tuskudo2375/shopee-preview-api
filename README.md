# Đồng hồ Việt – Widget 4×2

Widget Android native dành cho màn hình chính:

- Hiển thị giờ 24h, thứ, tháng và ngày dương lịch bằng tiếng Việt.
- Tính và hiển thị lịch âm Việt Nam trực tiếp trên máy.
- Lấy vị trí hiện tại bằng LocationManager, không phụ thuộc Google Play Services.
- Ưu tiên đọc vị trí, nhiệt độ, biểu tượng và cảnh báo từ dịch vụ thời tiết ColorOS
  (`com.coloros.weather2`); chỉ dùng Open-Meteo khi máy không cho đọc dữ liệu hệ thống.
- Tự làm mới giờ mỗi phút và thời tiết theo chu kỳ 30 phút.
- Nhấn vùng thời tiết để làm mới, nhấn vùng còn lại để mở phần cấp quyền.

## Cách dùng

1. Cài APK sau khi build project.
2. Mở **Đồng hồ Việt** một lần và cho phép quyền vị trí.
3. Thêm widget **Đồng hồ Việt** kích cỡ 4×2 vào màn hình chính.

## Build

Mở thư mục này bằng Android Studio và build biến thể `debug`. Project dùng Android Gradle Plugin 8.7.3, compile SDK 35, min SDK 26 và không có thư viện ngoài.

Widget cần quyền Internet và vị trí cho đường dự phòng. Trên OPPO/ColorOS, dữ liệu hệ thống
được ưu tiên và widget cũng lắng nghe các broadcast cập nhật thời tiết/cảnh báo của dịch vụ.
Khi chưa có dữ liệu, widget vẫn hiện giờ và lịch âm nhưng phần thời tiết sẽ chờ lần cập nhật sau.
