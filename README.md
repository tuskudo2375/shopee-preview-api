# Đồng hồ Việt – Widget 4×2

Widget Android native dành cho màn hình chính:

- Hiển thị giờ 24h, thứ, tháng và ngày dương lịch bằng tiếng Việt.
- Tính và hiển thị lịch âm Việt Nam trực tiếp trên máy.
- Lấy vị trí hiện tại bằng LocationManager, không phụ thuộc Google Play Services.
- Lấy nhiệt độ thấp/cao và trạng thái thời tiết theo vị trí qua Open-Meteo.
- Tự làm mới giờ mỗi phút và thời tiết theo chu kỳ 30 phút.
- Nhấn vùng thời tiết để làm mới, nhấn vùng còn lại để mở phần cấp quyền.

## Cách dùng

1. Cài APK sau khi build project.
2. Mở **Đồng hồ Việt** một lần và cho phép quyền vị trí.
3. Thêm widget **Đồng hồ Việt** kích cỡ 4×2 vào màn hình chính.

## Build

Mở thư mục này bằng Android Studio và build biến thể `debug`. Project dùng Android Gradle Plugin 8.7.3, compile SDK 35, min SDK 26 và không có thư viện ngoài.

Widget cần quyền Internet và vị trí. Khi chưa có vị trí cuối cùng, widget vẫn hiện giờ và lịch âm nhưng phần thời tiết sẽ chờ lần cập nhật sau.
