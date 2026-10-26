# Nhà của bà — launcher cho TV box

Màn hình chính chỉ có: lời chào theo giờ (sáng / chiều / tối) cho bà Ngọ, đồng hồ, ngày, thời tiết, và 3 ô: YouTube, VTV Go, YouTube Kids.

## 1. Chỉnh thông tin (tuỳ chọn)
Mở `app/src/main/java/vn/nhaba/launcher/Config.java`:
- `TEN` — tên hiện trên màn hình (mặc định "bà Ngọ").
- `VI_DO`, `KINH_DO`, `THANH_PHO` — vị trí lấy thời tiết. Để 0 thì app tự đoán theo mạng (có thể lệch sang tỉnh khác). Muốn chính xác: Google Maps → nhấn giữ vào nhà bà → chép 2 số toạ độ.

## 2. Build ra file APK (Ubuntu)
1. Giải nén, mở Android Studio → **Open** → chọn thư mục `GrandTV`. Đợi Gradle sync xong (lần đầu tải khá lâu).
2. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. File nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

Hoặc bằng Terminal (sau khi đã mở bằng Android Studio 1 lần):
```
cd GrandTV
./gradlew assembleDebug
```

## 3. Cài lên TV box
```
adb connect <IP_box>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. Đặt làm màn hình chính
```
adb shell cmd package set-home-activity vn.nhaba.launcher/.MainActivity
```
Bấm nút Home trên remote. Nếu vẫn ra màn hình cũ thì tắt launcher gốc:
```
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```
Muốn quay lại như cũ:
```
adb shell pm enable com.google.android.tvlauncher
```
(Nếu box dùng launcher khác, xem tên bằng: `adb shell cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN`)

## Dùng hằng ngày
- Mũi tên trái/phải chọn ô, OK để mở.
- **Nhấn giữ OK** trên một ô (hoặc nút Menu) → mở Cài đặt TV hoặc Cửa hàng Play.
- Nút Back ở màn hình chính không làm gì (để bà không lỡ tay thoát).
- Ô nào mờ và có dấu "?" là app đó chưa cài; bấm vào sẽ mở Cửa hàng Play.
- Thời tiết tự cập nhật 30 phút/lần (nguồn Open-Meteo, miễn phí).
