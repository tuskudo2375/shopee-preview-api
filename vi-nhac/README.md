# Trợ lý chi tiêu

Ứng dụng Android quản lý ngân sách tháng và nhập chi tiêu bằng câu tiếng Việt.

## MVP 0.1

- Đặt ngân sách riêng cho từng tháng.
- Nhập `ăn trưa 30k`, `trà sữa 50k`, `đổ xăng 100k`, `mua thuốc 80k`.
- Tự đọc số tiền và phân loại theo bảng mục tiêu: Tiền chuyển đi, Ăn uống, Mua sắm, Siêu thị, Hóa đơn, Giải trí, Khách sạn, Di chuyển, Giáo dục, Y tế, Du lịch, Chưa gắn thẻ.
- Home tự phân loại mục tiêu và nguồn tiền bằng Gemini (hoặc bộ lọc offline), không cần chọn thủ công trước khi lưu; nếu phân loại sai, chạm khoản chi để đổi mục tiêu hoặc nguồn sau khi lưu.
- Home và Lịch sử là hai tab riêng; Home chỉ hiện giao dịch trong ngày, còn Lịch sử nhóm theo ngày và lọc được theo khoảng ngày, nhiều nguồn chi và mục tiêu.
- Tính số tiền còn lại và chia đều cho số ngày còn lại (tính cả hôm nay).
- Notification foreground thường trực hiển thị số dư tháng, chi hôm nay và action nhập nhanh.
- Nhắc nhập chi tiêu lúc 10:00, 13:00 và 21:30; chuyển đỏ nếu vượt mức gợi ý trong ngày.
- Dữ liệu nằm offline trong máy, tự tách theo tháng.
- Lịch sử giữ toàn bộ khoản chi của tháng, nhóm theo từng ngày và hiện tổng tiền mỗi ngày.
- Lịch sử tự giữ 6 tháng gần nhất, có bộ lọc chọn ngày bằng lịch hệ thống của thiết bị.
- Mỗi khoản chi có nguồn tiền trong danh sách cố định: Tiền Mặt, Chuyển khoản, Thẻ Tech, Thẻ TP, Thẻ VIB.
- Gemini tự phân loại nguồn tiền theo: Tiền Mặt, Chuyển khoản, Thẻ Tech, Thẻ TP, Thẻ VIB; có thể sửa nguồn sau khi lưu.
- API key Gemini nằm trong Tùy chỉnh; ngôi sao ở góc Home màu cam khi chưa kết nối, xanh khi đã có key. Tùy chỉnh cũng có theme Theo hệ thống/Sáng/Tối.

## Build

Mở thư mục bằng Android Studio Ladybug hoặc mới hơn, dùng JDK 17, rồi chạy `assembleDebug`.

APK debug nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

## Lưu ý Android/Oppo

Cho phép thông báo, báo thức chính xác và tự khởi động để nhắc đúng giờ. Android vẫn cho chủ máy tắt notification channel trong Settings; app không thể hợp lệ khóa quyền này.
