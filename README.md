# GrandTV

> Minimal Android TV launcher for elderly users: a greeting, a clock, the weather, and a few big app tiles. No ads, no clutter, no way to get lost.

GrandTV là màn hình chính (launcher) tối giản cho Android TV box, viết cho ông bà lớn tuổi. Trên màn hình chỉ có:

- Lời chào theo buổi (sáng / chiều / tối) kèm tên người dùng
- Đồng hồ và ngày tháng cỡ lớn
- Thời tiết hiện tại và lời nhắc khi mưa, bão, nóng, lạnh
- Ba ô app lớn: **YouTube**, **VTV Go**, **YouTube Kids**
- Hàng phim mới trên VTV Go, bấm là xem ngay

Mọi thứ hiện bằng tiếng Việt. Điều khiển hoàn toàn bằng D-pad trên remote. Nút Back ở màn hình chính không làm gì, nên người dùng không thể lỡ tay thoát ra ngoài.

## Yêu cầu

- Android TV box chạy Android 5.0 trở lên (đã dùng thực tế trên box Amlogic Android 9, màn hình 1280x720)
- Kết nối Internet cho thời tiết và danh sách phim
- Máy tính có ADB để cài và đặt làm màn hình chính

Ứng dụng chỉ dùng API của Android, không có thư viện ngoài. Thời tiết lấy từ [Open-Meteo](https://open-meteo.com/) (miễn phí, không cần API key).

## Cài nhanh

1. Tải file APK ở mục **Releases**, hoặc tự build (xem bên dưới).
2. Bật **ADB qua mạng** trên box, rồi từ máy tính:

```bash
adb connect <IP_box>:5555
adb install -r app-debug.apk
adb shell cmd package set-home-activity vn.nhaba.launcher/.MainActivity
```

3. Bấm nút Home trên remote. Nếu vẫn ra màn hình cũ, tắt launcher gốc:

```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Muốn quay lại như cũ: `adb shell pm enable com.google.android.tvlauncher`.

Hướng dẫn chi tiết từng bước có trong [HUONG-DAN.md](HUONG-DAN.md).

## Tuỳ chỉnh

Sửa file `app/src/main/java/vn/nhaba/launcher/Config.java` rồi build lại:

| Hằng số | Ý nghĩa |
|---|---|
| `TEN` | Tên hiện dưới lời chào, ví dụ `"bà Ngọ"` |
| `XUNG_HO` | Cách xưng hô trong lời nhắc thời tiết, ví dụ `"bà"` hoặc `"ông"` |
| `VI_DO`, `KINH_DO` | Toạ độ lấy thời tiết. Để cả hai bằng 0 thì app tự đoán theo mạng |
| `THANH_PHO` | Tên thành phố hiện cạnh thời tiết |

## Tự build

Cần JDK 17 và Android SDK (mở project bằng Android Studio một lần là đủ).

```bash
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

Mỗi lần push, GitHub Actions tự build APK và lưu ở tab **Actions**. Gắn tag dạng `v1.0` rồi push tag là có bản **Release** kèm APK để tải:

```bash
git tag v1.0 && git push origin v1.0
```

## Dùng hằng ngày

- Mũi tên chọn ô, **OK** để mở.
- **Nhấn giữ OK** trên một ô, hoặc bấm nút **Menu**, để vào Cài đặt TV hoặc Cửa hàng Play.
- Ô nào mờ và có dấu "?" là app chưa cài; bấm vào sẽ mở Cửa hàng Play để cài.
- Thời tiết tự cập nhật 30 phút một lần, phim mới cập nhật mỗi giờ.

## Lưu ý

- Hàng phim mới đọc từ API công khai của VTV Go. VTV có thể thay đổi API bất cứ lúc nào; khi đó hàng phim sẽ trống nhưng launcher vẫn hoạt động bình thường.
- Bấm vào phim mở trực tiếp trong VTV Go cần bản **VTV Go 12.10 trở lên**. Bản cũ hơn sẽ chỉ mở app VTV Go.
- Nếu để `VI_DO` và `KINH_DO` bằng 0, app gửi địa chỉ IP của box tới [ipwho.is](https://ipwho.is/) một lần để đoán vị trí. Điền toạ độ cố định thì không có bước này.

## Giấy phép

[MIT](LICENSE)
