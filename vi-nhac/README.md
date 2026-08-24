# Trợ lý chi tiêu

Ứng dụng Android quản lý ngân sách tháng và nhập chi tiêu bằng câu tiếng Việt.

## MVP 0.1

- Đặt ngân sách riêng cho từng tháng.
- Nhập `ăn trưa 30k`, `trà sữa 50k`, `đổ xăng 100k`, `mua thuốc 80k`.
- Tự đọc số tiền và phân loại: Nhu cầu, Ăn vặt, Đi lại, Hóa đơn, Mua sắm, Sức khỏe, Giải trí, Khác.
- Tính số tiền còn lại và chia đều cho số ngày còn lại (tính cả hôm nay).
- Notification foreground thường trực hiển thị số dư tháng, chi hôm nay và action nhập nhanh.
- Nhắc nhập chi tiêu lúc 10:00, 13:00 và 21:30; chuyển đỏ nếu vượt mức gợi ý trong ngày.
- Dữ liệu nằm offline trong máy, tự tách theo tháng.
- Lịch sử giữ toàn bộ khoản chi của tháng, nhóm theo từng ngày và hiện tổng tiền mỗi ngày.
- Có thể nhập Gemini API key trong app để phân loại thông minh; key được mã hóa bằng Android Keystore. Khi API lỗi hoặc mất mạng, app tự dùng bộ lọc offline.

## Build

Mở thư mục bằng Android Studio Ladybug hoặc mới hơn, dùng JDK 17, rồi chạy `assembleDebug`.

APK debug nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

## Lưu ý Android/Oppo

Cho phép thông báo, báo thức chính xác và tự khởi động để nhắc đúng giờ. Android vẫn cho chủ máy tắt notification channel trong Settings; app không thể hợp lệ khóa quyền này.
