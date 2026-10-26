package vn.nhaba.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    // ---------- 3 ô ứng dụng ----------
    private static class AppTile {
        final String label;
        final String[] packages;   // thử lần lượt các tên gói này trước
        final String[] keywords;   // nếu không thấy thì dò theo tên app
        final boolean skipKids;
        Intent launch;
        ImageView image;
        View view;

        AppTile(String label, String[] packages, String[] keywords, boolean skipKids) {
            this.label = label;
            this.packages = packages;
            this.keywords = keywords;
            this.skipKids = skipKids;
        }
    }

    private final List<AppTile> tiles = new ArrayList<>();
    private AppTile vtvGo;

    // ---------- Phim mới trên VTV Go ----------
    private static class Film {
        final String id, name, episode, image;
        Bitmap bitmap;

        Film(String id, String name, String episode, String image) {
            this.id = id;
            this.name = name;
            this.episode = episode;
            this.image = image;
        }
    }

    // Mục "PHIM MỚI TRÊN VTVGO" (trang Phim Truyện của VTV Go), mới nhất trước.
    // Máy chủ trả tối đa 20 tập/trang nên phải lật vài trang mới đủ 8 phim khác nhau.
    private static final String FILMS_URL =
            "https://api.vtvdigital.org/content/v21.0/vod/list-vod?nodeId=533566&limit=20&sortBy=NEWEST&page=";
    private static final int FILMS_PAGES = 10;
    private static final int FILMS_PER_ROW = 4;
    private static final int FILMS_MAX = 2 * FILMS_PER_ROW;   // 2 hàng
    private static final long FILMS_EVERY = 60 * 60 * 1000L;   // 1 tiếng
    private static final long FILMS_RETRY = 5 * 60 * 1000L;    // lỗi thì thử lại sau 5 phút

    private LinearLayout[] filmRows;
    private String shownFilms = "";
    private volatile boolean fetchingFilms = false;
    private volatile long lastFilmsOk = 0, lastFilmsTry = 0;

    private View root;
    private TextView greeting, name, clock, date, wIcon, wMain, wDetail, wNote;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int lastPeriod = -1;
    private String lastClock = "";

    private volatile boolean fetching = false;
    private volatile long lastWeatherOk = 0, lastWeatherTry = 0;
    private volatile double ipLat = 0, ipLon = 0;
    private volatile String ipCity = "";

    private static final long WEATHER_EVERY = 30 * 60 * 1000L;   // 30 phút
    private static final long WEATHER_RETRY = 2 * 60 * 1000L;    // lỗi thì thử lại sau 2 phút

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        greeting = (TextView) findViewById(R.id.greeting);
        name = (TextView) findViewById(R.id.name);
        clock = (TextView) findViewById(R.id.clock);
        date = (TextView) findViewById(R.id.date);
        wIcon = (TextView) findViewById(R.id.w_icon);
        wMain = (TextView) findViewById(R.id.w_main);
        wDetail = (TextView) findViewById(R.id.w_detail);
        wNote = (TextView) findViewById(R.id.w_note);
        filmRows = new LinearLayout[]{
                (LinearLayout) findViewById(R.id.films_row1), (LinearLayout) findViewById(R.id.films_row2)};

        for (TextView t : new TextView[]{greeting, name, clock, date, wMain, wDetail, wNote}) {
            t.setShadowLayer(8f, 0f, 2f, 0x80000000);
        }
        name.setText(Config.TEN);

        tiles.add(new AppTile("YouTube",
                new String[]{"com.google.android.youtube.tv", "com.google.android.youtube.googletv", "com.google.android.youtube"},
                new String[]{"youtube"}, true));
        vtvGo = new AppTile("VTV Go",
                new String[]{"vn.vtv.vtvgotv", "vn.vtv.vtvgo", "com.vtvgo.tv"},
                new String[]{"vtv go", "vtvgo"}, false);
        tiles.add(vtvGo);
        tiles.add(new AppTile("YouTube Kids",
                new String[]{"com.google.android.youtube.tvkids", "com.google.android.apps.youtube.kids"},
                new String[]{"youtube kids", "kids"}, false));

        buildTiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (AppTile t : tiles) resolveTile(t);   // phòng khi vừa cài/gỡ app
        handler.removeCallbacks(tick);
        handler.post(tick);
        if (getCurrentFocus() == null && !tiles.isEmpty()) tiles.get(0).view.requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    // Màn hình chính: nút Back không làm gì cả
    @Override
    public void onBackPressed() { }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------- Đồng hồ + lời chào ----------
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            long now = System.currentTimeMillis();
            boolean due = (lastWeatherOk == 0 || now - lastWeatherOk > WEATHER_EVERY)
                    && now - lastWeatherTry > (lastWeatherOk == 0 && lastWeatherTry == 0 ? 0 : WEATHER_RETRY);
            if (due && !fetching) fetchWeather();
            boolean filmsDue = (lastFilmsOk == 0 || now - lastFilmsOk > FILMS_EVERY)
                    && now - lastFilmsTry > (lastFilmsOk == 0 && lastFilmsTry == 0 ? 0 : FILMS_RETRY);
            if (filmsDue && !fetchingFilms) fetchFilms();
            handler.postDelayed(this, 1000);
        }
    };

    private static final String[] THU = {"", "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};

    private void updateClock() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        String hm = String.format(Locale.US, "%02d:%02d", h, c.get(Calendar.MINUTE));
        if (!hm.equals(lastClock)) {
            lastClock = hm;
            clock.setText(hm);
            date.setText(String.format(Locale.US, "%s, ngày %d/%d/%d",
                    THU[c.get(Calendar.DAY_OF_WEEK)], c.get(Calendar.DAY_OF_MONTH),
                    c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR)));
        }

        int period = (h >= 4 && h < 12) ? 0 : (h >= 12 && h < 18) ? 1 : 2;
        if (period != lastPeriod) {
            lastPeriod = period;
            String[] chao = {"Chào buổi sáng", "Chào buổi chiều", "Chào buổi tối"};
            greeting.setText(chao[period]);
            int[][] colors = {
                    {0xFF1B4F72, 0xFF2E86AB, 0xFFE29C5B},   // sáng: bình minh
                    {0xFF0D47A1, 0xFF1E88E5, 0xFF4FB3D9},   // chiều: trời xanh
                    {0xFF070B1F, 0xFF161B4A, 0xFF2E2A6E},   // tối: đêm
            };
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors[period]);
            root.setBackground(g);
        }
    }

    // ---------- Ô ứng dụng ----------
    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // Khung chung cho ô ứng dụng và ô phim: viền vàng + phóng to khi được chọn,
    // nhấn giữ OK thì mở menu Tuỳ chọn
    private LinearLayout newCard(int pad) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackgroundResource(R.drawable.tile_bg);
        box.setPadding(dp(pad), dp(pad), dp(pad), dp(pad - 2));
        box.setFocusable(true);
        box.setClickable(true);
        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                float s = has ? 1.08f : 1f;
                v.animate().scaleX(s).scaleY(s).translationZ(has ? dp(6) : 0).setDuration(150).start();   // nổi lên trên ô bên cạnh
                View marquee = v.findViewWithTag("marquee");   // tên phim dài thì chạy chữ khi được chọn
                if (marquee != null) marquee.setSelected(has);
            }
        });
        box.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) { showMenu(); return true; }
        });
        return box;
    }

    private void buildTiles() {
        LinearLayout row = (LinearLayout) findViewById(R.id.tiles);
        for (final AppTile t : tiles) {
            // Ô nhỏ nằm ngang: ảnh app bên trái, tên bên phải
            LinearLayout box = newCard(8);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setPadding(dp(8), dp(8), dp(20), dp(8));

            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(img, new LinearLayout.LayoutParams(dp(96), dp(54)));

            TextView label = new TextView(this);
            label.setText(t.label);
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
            label.setTypeface(label.getTypeface(), Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(12);
            box.addView(label, lp);

            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { openTile(t); }
            });

            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.leftMargin = dp(18);
            bp.rightMargin = dp(18);
            row.addView(box, bp);

            t.view = box;
            t.image = img;
        }
    }

    private void resolveTile(AppTile t) {
        PackageManager pm = getPackageManager();
        t.launch = null;

        for (String p : t.packages) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(p);
            if (i == null) i = pm.getLaunchIntentForPackage(p);
            if (i != null) { t.launch = i; break; }
        }

        if (t.launch == null) {
            outer:
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                    String label = String.valueOf(ri.loadLabel(pm)).toLowerCase(Locale.ROOT);
                    String pkg = ri.activityInfo.packageName.toLowerCase(Locale.ROOT);
                    if (pkg.equals(getPackageName())) continue;
                    if (t.skipKids && (label.contains("kids") || pkg.contains("kids"))) continue;
                    for (String k : t.keywords) {
                        if (label.contains(k) || pkg.contains(k.replace(" ", ""))) {
                            t.launch = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                                    .setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                            break outer;
                        }
                    }
                }
            }
        }

        Drawable d = null;
        if (t.launch != null) {
            t.launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            ComponentName cn = t.launch.getComponent();
            try {
                if (cn != null) d = pm.getActivityBanner(cn);
                if (d == null && cn != null) d = pm.getApplicationBanner(cn.getPackageName());
                if (d == null && cn != null) d = pm.getActivityIcon(cn);
            } catch (Exception ignored) { }
        }
        if (d != null) {
            t.image.setImageDrawable(d);
            t.view.setAlpha(1f);
        } else {
            t.image.setImageResource(android.R.drawable.ic_menu_help);
            t.view.setAlpha(0.6f);
        }
    }

    private void openTile(AppTile t) {
        if (t.launch != null) {
            try {
                startActivity(t.launch);
                return;
            } catch (Exception ignored) { }
        }
        Toast.makeText(this, "Chưa cài " + t.label + ". Đang mở Cửa hàng Play…", Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + t.packages[0]))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    // Nhấn giữ nút OK trên 1 ô (hoặc nút Menu) để vào Cài đặt
    private void showMenu() {
        final String[] items = {"Cài đặt TV", "Cửa hàng Google Play", "Đóng"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Tuỳ chọn")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(android.content.DialogInterface dialog, int which) {
                    try {
                        if (which == 0) {
                            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } else if (which == 1) {
                            PackageManager pm = getPackageManager();
                            Intent i = pm.getLeanbackLaunchIntentForPackage("com.android.vending");
                            if (i == null) i = pm.getLaunchIntentForPackage("com.android.vending");
                            if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Không mở được", Toast.LENGTH_SHORT).show();
                    }
                  }
                })
                .show();
    }

    // ---------- Phim mới (API công khai của VTV Go, không cần đăng nhập) ----------
    private void fetchFilms() {
        fetchingFilms = true;
        lastFilmsTry = System.currentTimeMillis();
        new Thread(new Runnable() {
          @Override
          public void run() {
            try {
                final List<Film> list = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (int page = 1; page <= FILMS_PAGES && list.size() < FILMS_MAX; page++) {
                    JSONObject res = new JSONObject(httpGet(FILMS_URL + page));
                    JSONArray arr = res.getJSONArray("data");
                    for (int i = 0; i < arr.length() && list.size() < FILMS_MAX; i++) {
                        JSONObject x = arr.getJSONObject(i);
                        String series = x.isNull("seriesID") ? "" : x.optString("seriesID", "");
                        // bỏ trailer / giới thiệu; mỗi phim chỉ lấy tập mới nhất
                        if (series.isEmpty() || x.optInt("episodeNumber", 0) <= 0 || !seen.add(series)) continue;

                        String title = x.optString("title", "");          // "Đội bóng nữ làng Xuân | Tập 15"
                        int bar = title.indexOf(" | ");
                        String filmName = bar > 0 ? title.substring(0, bar) : title;
                        String episode = bar > 0 ? title.substring(bar + 3) : "Tập " + x.optInt("episodeNumber");
                        JSONObject images = x.optJSONObject("images");
                        String image = images == null ? "" : images.optString("horizontal", "");
                        list.add(new Film(x.getString("id"), filmName, episode, image));
                    }
                    JSONObject meta = res.optJSONObject("meta");
                    if (meta == null || meta.isNull("next")) break;   // hết trang
                }
                if (list.isEmpty()) throw new Exception("no films");

                for (Film f : list) {
                    if (f.image.isEmpty()) continue;
                    try {
                        f.bitmap = httpGetBitmap(f.image + (f.image.contains("?") ? "&" : "?") + "width=320");
                    } catch (Exception ignored) { }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() { showFilms(list); }
                });
                lastFilmsOk = System.currentTimeMillis();
            } catch (Exception ignored) {
                // giữ nguyên danh sách đang hiện, lần sau thử lại
            } finally {
                fetchingFilms = false;
            }
          }
        }).start();
    }

    private void showFilms(List<Film> list) {
        StringBuilder key = new StringBuilder();
        for (Film f : list) key.append(f.id).append(f.bitmap != null).append(',');
        if (key.toString().equals(shownFilms)) return;   // không đổi thì để nguyên, khỏi mất ô đang chọn
        shownFilms = key.toString();

        View focused = getCurrentFocus();
        boolean hadFocus = focused != null
                && (focused.getParent() == filmRows[0] || focused.getParent() == filmRows[1]);
        for (LinearLayout row : filmRows) row.removeAllViews();

        for (int n = 0; n < list.size(); n++) {
            final Film f = list.get(n);
            LinearLayout box = newCard(8);

            FrameLayout frame = new FrameLayout(this);
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (f.bitmap != null) img.setImageBitmap(f.bitmap);
            else img.setBackgroundColor(0x33000000);
            frame.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            TextView badge = new TextView(this);
            badge.setText(f.episode);
            badge.setTextColor(0xFF1A1A1A);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setBackgroundResource(R.drawable.badge_bg);
            badge.setPadding(dp(8), dp(1), dp(8), dp(1));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
            blp.leftMargin = dp(6);
            blp.topMargin = dp(6);
            frame.addView(badge, blp);
            box.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(94)));

            TextView label = new TextView(this);
            label.setText(f.name);
            label.setTag("marquee");
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            label.setTypeface(label.getTypeface(), Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            label.setMarqueeRepeatLimit(-1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(4);
            box.addView(label, lp);

            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { openFilm(f); }
            });

            // Chia đều chiều ngang: mỗi hàng FILMS_PER_ROW ô
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            bp.leftMargin = dp(6);
            bp.rightMargin = dp(6);
            filmRows[n / FILMS_PER_ROW].addView(box, bp);
        }
        // Hàng chưa đủ ô thì chèn chỗ trống để các ô vẫn giữ cùng kích thước
        for (LinearLayout row : filmRows) {
            int c = row.getChildCount();
            row.setVisibility(c == 0 ? View.GONE : View.VISIBLE);
            for (int k = c; c > 0 && k < FILMS_PER_ROW; k++) {
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 0, 1f);
                sp.leftMargin = dp(6);
                sp.rightMargin = dp(6);
                row.addView(new View(this), sp);
            }
        }

        findViewById(R.id.films_box).setVisibility(View.VISIBLE);
        if (hadFocus) filmRows[0].getChildAt(0).requestFocus();
    }

    // Mở thẳng tập phim trong VTV Go; không được thì mở VTV Go như ô bình thường
    private void openFilm(Film f) {
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("vtvgo://play?mediaType=MOVIE&contentId=" + Uri.encode(f.id)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (vtvGo.launch != null && vtvGo.launch.getComponent() != null) {
            i.setPackage(vtvGo.launch.getComponent().getPackageName());
        }
        try {
            startActivity(i);
        } catch (Exception e) {
            openTile(vtvGo);
        }
    }

    // ---------- Thời tiết (Open-Meteo, miễn phí, không cần khoá API) ----------
    private void fetchWeather() {
        fetching = true;
        lastWeatherTry = System.currentTimeMillis();
        new Thread(new Runnable() {
          @Override
          public void run() {
            try {
                double lat = Config.VI_DO, lon = Config.KINH_DO;
                String city = Config.THANH_PHO;
                if (lat == 0 && lon == 0) {
                    if (ipLat == 0 && ipLon == 0) {
                        JSONObject ip = new JSONObject(httpGet("https://ipwho.is/?fields=success,city,latitude,longitude"));
                        if (ip.optBoolean("success")) {
                            ipLat = ip.getDouble("latitude");
                            ipLon = ip.getDouble("longitude");
                            ipCity = ip.optString("city", "");
                        }
                    }
                    lat = ipLat;
                    lon = ipLon;
                    if (city.isEmpty()) city = ipCity;
                }
                if (lat == 0 && lon == 0) throw new Exception("no location");

                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                + "&current=temperature_2m,weather_code,is_day"
                                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                                + "&timezone=auto&forecast_days=1", lat, lon);
                JSONObject j = new JSONObject(httpGet(url));
                JSONObject cur = j.getJSONObject("current");
                JSONObject day = j.getJSONObject("daily");

                final int temp = (int) Math.round(cur.getDouble("temperature_2m"));
                final int code = cur.getInt("weather_code");
                final boolean isDay = cur.optInt("is_day", 1) == 1;
                final int max = (int) Math.round(day.getJSONArray("temperature_2m_max").getDouble(0));
                final int min = (int) Math.round(day.getJSONArray("temperature_2m_min").getDouble(0));
                final int rain = day.getJSONArray("precipitation_probability_max").optInt(0, -1);
                final String place = city;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() { showWeather(temp, code, isDay, max, min, rain, place); }
                });
                lastWeatherOk = System.currentTimeMillis();
            } catch (Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (lastWeatherOk == 0) {
                            wIcon.setText("📡");
                            wMain.setText("Chưa lấy được thời tiết");
                            wDetail.setText("Kiểm tra mạng internet, sẽ tự thử lại");
                            wNote.setVisibility(View.GONE);
                        }
                    }
                });
            } finally {
                fetching = false;
            }
          }
        }).start();
    }

    private void showWeather(int temp, int code, boolean isDay, int max, int min, int rain, String city) {
        String[] w = describe(code, isDay);
        wIcon.setText(w[0]);
        wMain.setText(temp + "°  " + w[1]);

        StringBuilder sb = new StringBuilder();
        if (city != null && !city.isEmpty()) sb.append(city).append(" · ");
        sb.append(min).append("–").append(max).append("°");
        if (rain >= 0) sb.append(" · Khả năng mưa ").append(rain).append("%");
        wDetail.setText(sb.toString());

        String note = null;
        String b = Config.XUNG_HO;
        boolean rainy = (code >= 51 && code <= 67) || (code >= 80 && code <= 99);
        if (code >= 95) note = "Có dông, " + b + " nhớ rút phích điện và đóng cửa sổ nhé";
        else if (rainy || rain >= 60) note = "Trời có thể mưa, " + b + " nhớ cất đồ phơi nhé";
        else if (max >= 35) note = "Trời nóng, " + b + " nhớ uống nhiều nước nhé";
        else if (min <= 18) note = "Trời lạnh, " + b + " nhớ mặc thêm áo ấm nhé";

        if (note != null) {
            wNote.setText(note);
            wNote.setVisibility(View.VISIBLE);
        } else {
            wNote.setVisibility(View.GONE);
        }
    }

    // Mã thời tiết WMO -> biểu tượng + chữ tiếng Việt
    private static String[] describe(int c, boolean day) {
        if (c == 0) return new String[]{day ? "☀️" : "🌙", "Trời quang"};
        if (c == 1) return new String[]{day ? "🌤️" : "🌙", "Ít mây"};
        if (c == 2) return new String[]{"⛅", "Có mây"};
        if (c == 3) return new String[]{"☁️", "Nhiều mây"};
        if (c == 45 || c == 48) return new String[]{"🌫️", "Sương mù"};
        if (c >= 51 && c <= 57) return new String[]{"🌦️", "Mưa phùn"};
        if (c == 61 || c == 66) return new String[]{"🌧️", "Mưa nhỏ"};
        if (c == 63) return new String[]{"🌧️", "Mưa vừa"};
        if (c == 65 || c == 67) return new String[]{"🌧️", "Mưa to"};
        if (c >= 71 && c <= 77) return new String[]{"❄️", "Tuyết"};
        if (c == 80) return new String[]{"🌦️", "Mưa rào nhẹ"};
        if (c == 81) return new String[]{"🌧️", "Mưa rào"};
        if (c == 82) return new String[]{"⛈️", "Mưa rào rất to"};
        if (c == 85 || c == 86) return new String[]{"❄️", "Mưa tuyết"};
        if (c == 95) return new String[]{"⛈️", "Có dông"};
        if (c == 96 || c == 99) return new String[]{"⛈️", "Dông, mưa đá"};
        return new String[]{"🌡️", "Thời tiết"};
    }

    private static String httpGet(String u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        con.setRequestProperty("User-Agent", "NhaCuaBa/1.0");
        try {
            if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
            BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }

    private static Bitmap httpGetBitmap(String u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        con.setRequestProperty("User-Agent", "NhaCuaBa/1.0");
        try {
            if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
            InputStream in = con.getInputStream();
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            if (b == null) throw new Exception("bad image");
            return b;
        } finally {
            con.disconnect();
        }
    }
}
