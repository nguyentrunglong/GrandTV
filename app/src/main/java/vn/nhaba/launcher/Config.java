package vn.nhaba.launcher;

/**
 * ======= CHỈNH Ở ĐÂY =======
 */
public final class Config {

    /** Tên hiện ở lời chào: "Chào buổi sáng" + dòng dưới là TEN */
    public static final String TEN = "bà Ngọ";

    /** Cách xưng hô ngắn, dùng trong lời nhắc thời tiết ("bà nhớ mang áo mưa nhé") */
    public static final String XUNG_HO = "bà";

    /**
     * Vị trí lấy thời tiết.
     * Để VI_DO = 0 và KINH_DO = 0 thì app tự đoán vị trí theo mạng internet (có thể lệch).
     * Muốn chính xác: mở Google Maps, nhấn giữ vào nhà bà, chép 2 số toạ độ vào đây.
     * Ví dụ Long Khánh: VI_DO = 10.9333, KINH_DO = 107.2417, THANH_PHO = "Long Khánh"
     */
    public static final double VI_DO = 21.0285;
    public static final double KINH_DO = 105.8542;
    public static final String THANH_PHO = "Hà Nội";

    private Config() {}
}
