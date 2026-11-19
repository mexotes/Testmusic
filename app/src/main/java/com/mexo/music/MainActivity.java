package com.mexo.music;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    /* ===== API KA URL - sirf yahi ek jagah hai. API badle to bas ye line badalna. ===== */
    static final String BASE_URL = "https://mexomusic.netlify.app";

    static class Song {
        String id, title, artists, image;
        int duration;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("title", title); o.put("artists", artists);
            o.put("image", image); o.put("duration", duration);
            return o;
        }

        static Song fromJson(JSONObject o) {
            Song s = new Song();
            s.id = o.optString("id"); s.title = o.optString("title");
            s.artists = o.optString("artists"); s.image = o.optString("image");
            s.duration = o.optInt("duration");
            return s;
        }
    }

    /* List ki ek row - ya to section ka header, ya gaana */
    static class Row {
        boolean header;
        String headerText;
        Song song;
        static Row h(String t) { Row r = new Row(); r.header = true; r.headerText = t; return r; }
        static Row s(Song s) { Row r = new Row(); r.song = s; return r; }
    }

    private static final int MODE_HOME = 0, MODE_SEARCH = 1, MODE_HISTORY = 2, MODE_PLAYLIST = 3;

    private EditText searchBox;
    private ListView listView;
    private Button tabHome, tabHistory, tabPlaylist, clearBtn;
    private ImageView pArt;
    private TextView pTitle, pArtist, curTime, durTime;
    private SeekBar seek;
    private Button playBtn;

    private final List<Row> rows = new ArrayList<>();
    private RowAdapter adapter;
    private List<Song> history = new ArrayList<>();
    private List<Song> playlist = new ArrayList<>();
    private List<Song> queue = new ArrayList<>();
    private int queuePos = -1;
    private Song nowPlaying;
    private int mode = MODE_HOME;

    private MediaPlayer mp;
    private int playToken = 0;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private final LruCache<String, Bitmap> imgCache = new LruCache<>(60);

    private final Runnable updater = new Runnable() {
        @Override public void run() {
            if (mp != null && nowPlaying != null) {
                try {
                    seek.setProgress(mp.getCurrentPosition() / 1000);
                    curTime.setText(fmt(mp.getCurrentPosition() / 1000));
                } catch (Exception ignored) {}
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("mexo", MODE_PRIVATE);
        history = loadList("history");
        playlist = loadList("playlist");

        searchBox = findViewById(R.id.searchBox);
        listView = findViewById(R.id.list);
        tabHome = findViewById(R.id.tabHome);
        tabHistory = findViewById(R.id.tabHistory);
        tabPlaylist = findViewById(R.id.tabPlaylist);
        clearBtn = findViewById(R.id.clearBtn);
        pArt = findViewById(R.id.pArt);
        pTitle = findViewById(R.id.pTitle);
        pArtist = findViewById(R.id.pArtist);
        curTime = findViewById(R.id.curTime);
        durTime = findViewById(R.id.durTime);
        seek = findViewById(R.id.seek);
        playBtn = findViewById(R.id.playBtn);
        Button searchBtn = findViewById(R.id.searchBtn);
        Button prevBtn = findViewById(R.id.prevBtn);
        Button nextBtn = findViewById(R.id.nextBtn);

        adapter = new RowAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Row r = rows.get(position);
            if (!r.header) onSongTapped(r.song);
        });

        View.OnClickListener doSearch = v -> {
            String q = searchBox.getText().toString().trim();
            if (!q.isEmpty()) doSearch(q);
        };
        searchBtn.setOnClickListener(doSearch);
        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                doSearch.onClick(v);
                return true;
            }
            return false;
        });

        tabHome.setOnClickListener(v -> showHome());
        tabHistory.setOnClickListener(v -> showHistory());
        tabPlaylist.setOnClickListener(v -> showPlaylist());
        clearBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("History saaf kar dein?")
                .setMessage("Saare sune hue gaano ki history mit jayegi.")
                .setPositiveButton("Haan, saaf karo", (d, w) -> {
                    history.clear();
                    saveList("history", history);
                    showHistory();
                    toast("History saaf ho gayi 🧹");
                })
                .setNegativeButton("Nahi", null)
                .show());

        playBtn.setOnClickListener(v -> togglePlay());
        nextBtn.setOnClickListener(v -> next());
        prevBtn.setOnClickListener(v -> prev());

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && mp != null && nowPlaying != null) mp.seekTo(progress * 1000);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        mp = new MediaPlayer();
        mp.setOnPreparedListener(m -> {
            seek.setMax(m.getDuration() / 1000);
            durTime.setText(fmt(m.getDuration() / 1000));
            m.start();
            playBtn.setText("⏸");
        });
        mp.setOnCompletionListener(m -> next());
        mp.setOnErrorListener((m, what, extra) -> {
            /* Loading (⏳) hamesha clear hoga - hourglass atakna nahi chahiye */
            playFailed();
            return true;
        });

        handler.post(updater);
        showHome();
    }

    /* ================= Screens ================= */

    private void setMode(int m) {
        mode = m;
        tabHome.setBackgroundResource(m == MODE_HOME || m == MODE_SEARCH ? R.drawable.btn_green : R.drawable.tab_off);
        tabHome.setTextColor(m == MODE_HOME || m == MODE_SEARCH ? 0xFF000000 : 0xFFFFFFFF);
        tabHistory.setBackgroundResource(m == MODE_HISTORY ? R.drawable.btn_green : R.drawable.tab_off);
        tabHistory.setTextColor(m == MODE_HISTORY ? 0xFF000000 : 0xFFFFFFFF);
        tabPlaylist.setBackgroundResource(m == MODE_PLAYLIST ? R.drawable.btn_green : R.drawable.tab_off);
        tabPlaylist.setTextColor(m == MODE_PLAYLIST ? 0xFF000000 : 0xFFFFFFFF);
        clearBtn.setVisibility(m == MODE_HISTORY && !history.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setRows(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        adapter.notifyDataSetChanged();
        listView.setSelection(0);
    }

    private void showHome() {
        setMode(MODE_HOME);
        List<Row> loading = new ArrayList<>();
        loading.add(Row.h("🏠 Tumhare liye gaane la raha hai..."));
        setRows(loading);
        exec.execute(() -> {
            List<Row> out = new ArrayList<>();
            List<String> seeds = new ArrayList<>();
            Set<String> seenArtists = new HashSet<>();
            for (Song s : history) {
                String a = firstArtist(s.artists);
                if (!a.isEmpty() && seenArtists.add(a.toLowerCase())) seeds.add(a);
                if (seeds.size() >= 3) break;
            }
            if (seeds.isEmpty()) {
                seeds.add("Arijit Singh");
                seeds.add("Atif Aslam");
                seeds.add("Pritam");
                out.add(Row.h("✨ Tumhare liye — kuch suno, phir app tumhari pasand seekh jayega"));
            } else {
                out.add(Row.h("✨ Tumhari pasand ke hisaab se"));
            }
            Set<String> seenIds = new HashSet<>();
            for (String seed : seeds) {
                try {
                    List<Song> found = apiSearch(seed, 8);
                    List<Song> fresh = new ArrayList<>();
                    for (Song s : found) if (seenIds.add(s.id)) fresh.add(s);
                    if (!fresh.isEmpty()) {
                        out.add(Row.h("🎤 " + seed + " ke gaane"));
                        for (Song s : fresh) out.add(Row.s(s));
                    }
                } catch (Exception ignored) {}
            }
            if (out.size() <= 1) out.add(Row.h("Net nahi chal raha lagta hai — search karke try karo"));
            runOnUiThread(() -> { if (mode == MODE_HOME) setRows(out); });
        });
    }

    private void doSearch(String query) {
        setMode(MODE_SEARCH);
        toast("Dhundh raha hai: " + query);
        exec.execute(() -> {
            try {
                List<Song> results = apiSearch(query, 30);
                List<Row> out = new ArrayList<>();
                out.add(Row.h("🔍 \"" + query + "\" ke results"));
                Set<String> seenIds = new HashSet<>();
                for (Song s : results) { seenIds.add(s.id); out.add(Row.s(s)); }
                if (results.isEmpty()) out.add(Row.h("Koi gaana nahi mila 😕 naam check karo"));
                /* Isi type ke aur gaane - pehle result ke singer ke aur gaane */
                if (!results.isEmpty()) {
                    String artist = firstArtist(results.get(0).artists);
                    if (!artist.isEmpty()) {
                        try {
                            List<Song> more = apiSearch(artist, 15);
                            List<Song> fresh = new ArrayList<>();
                            for (Song s : more) if (seenIds.add(s.id)) fresh.add(s);
                            if (!fresh.isEmpty()) {
                                out.add(Row.h("✨ Isi type ke aur gaane — " + artist));
                                for (Song s : fresh) out.add(Row.s(s));
                            }
                        } catch (Exception ignored) {}
                    }
                }
                runOnUiThread(() -> { if (mode == MODE_SEARCH) setRows(out); });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Search fail — net check karo"));
            }
        });
    }

    private void showHistory() {
        setMode(MODE_HISTORY);
        List<Row> out = new ArrayList<>();
        if (history.isEmpty()) {
            out.add(Row.h("🕘 Abhi koi history nahi hai"));
            out.add(Row.h("Jo gaana sunoge, wo yahan apne aap save hota jayega"));
        } else {
            out.add(Row.h("🕘 Tumhari History (" + history.size() + ")"));
            for (Song s : history) out.add(Row.s(s));
        }
        setRows(out);
    }

    private void showPlaylist() {
        setMode(MODE_PLAYLIST);
        List<Row> out = new ArrayList<>();
        if (playlist.isEmpty()) {
            out.add(Row.h("💚 Meri Playlist abhi khali hai"));
            out.add(Row.h("Kisi gaane ke aage ＋ dabao, wo yahan save ho jayega"));
        } else {
            out.add(Row.h("💚 Meri Playlist (" + playlist.size() + ")"));
            for (Song s : playlist) out.add(Row.s(s));
        }
        setRows(out);
    }

    /* ================= Player ================= */

    private void onSongTapped(Song s) {
        List<Song> q = new ArrayList<>();
        int pos = -1;
        for (Row row : rows) {
            if (!row.header) {
                if (row.song.id.equals(s.id) && pos < 0) pos = q.size();
                q.add(row.song);
            }
        }
        queue = q;
        queuePos = pos;
        play(s);
    }

    private void play(Song s) {
        nowPlaying = s;
        addHistory(s);
        pTitle.setText(s.title);
        pArtist.setText(s.artists);
        loadImage(s.image, pArt);
        playBtn.setText("⏳");
        seek.setProgress(0);
        curTime.setText("0:00");
        if (s.duration > 0) {
            seek.setMax(s.duration);
            durTime.setText(fmt(s.duration));
        } else {
            durTime.setText("0:00");
        }
        adapter.notifyDataSetChanged();
        /* v3 - JioSaavn wala play tareeka (/api/song + /api/stream) pura hata diya.
           Search Netlify API se aata hai, lekin gaane ka stream link YouTube
           kisi hosted server ko nahi deta - isliye link app ke andar hi
           YtStreamResolver (NewPipeExtractor) se nikalta hai, phone ke apne
           net se. Link mile to wahi MediaPlayer ko dete hain; na mile to
           hourglass clear karke saaf message - atakna bilkul nahi. */
        final int token = ++playToken;
        exec.execute(() -> {
            String url = null;
            try { url = YtStreamResolver.resolve(s.id); } catch (Exception ignored) {}
            final String streamUrl = url;
            runOnUiThread(() -> {
                if (token != playToken || nowPlaying == null || !nowPlaying.id.equals(s.id)) return;
                if (streamUrl == null || streamUrl.isEmpty()) {
                    playFailed();
                    return;
                }
                try {
                    mp.reset();
                    mp.setDataSource(streamUrl);
                    mp.prepareAsync();
                } catch (Exception e) {
                    playFailed();
                }
            });
        });
    }

    /* Bajna fail ho jaye to loading wala hourglass hamesha clear + saaf message */
    private void playFailed() {
        playBtn.setText("▶");
        toast("Ye gaana nahi baj paya, dusra try karo");
    }

    private void togglePlay() {
        if (mp == null || nowPlaying == null) return;
        try {
            if (mp.isPlaying()) {
                mp.pause();
                playBtn.setText("▶");
            } else {
                mp.start();
                playBtn.setText("⏸");
            }
        } catch (Exception e) {
            /* Player taiyaar hi nahi hai (pichhla gaana fail hua tha) - button reset */
            playBtn.setText("▶");
        }
    }

    private void next() {
        if (queue.isEmpty() || queuePos < 0) return;
        queuePos = (queuePos + 1) % queue.size();
        play(queue.get(queuePos));
    }

    private void prev() {
        if (queue.isEmpty() || queuePos < 0) return;
        queuePos = (queuePos - 1 + queue.size()) % queue.size();
        play(queue.get(queuePos));
    }

    /* ================= History / Playlist ================= */

    private void addHistory(Song s) {
        for (int i = history.size() - 1; i >= 0; i--)
            if (history.get(i).id.equals(s.id)) history.remove(i);
        history.add(0, s);
        while (history.size() > 50) history.remove(history.size() - 1);
        saveList("history", history);
    }

    private boolean isSaved(String id) {
        for (Song s : playlist) if (s.id.equals(id)) return true;
        return false;
    }

    private void toggleSave(Song s) {
        if (isSaved(s.id)) {
            for (int i = playlist.size() - 1; i >= 0; i--)
                if (playlist.get(i).id.equals(s.id)) playlist.remove(i);
            toast("Playlist se hata diya");
        } else {
            playlist.add(0, s);
            toast("Meri Playlist me save ho gaya 💚");
        }
        saveList("playlist", playlist);
        if (mode == MODE_PLAYLIST) showPlaylist();
        else adapter.notifyDataSetChanged();
    }

    private List<Song> loadList(String key) {
        List<Song> out = new ArrayList<>();
        try {
            String raw = prefs.getString(key, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) out.add(Song.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    private void saveList(String key, List<Song> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Song s : list) arr.put(s.toJson());
            prefs.edit().putString(key, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    /* ================= API / helpers ================= */

    private List<Song> apiSearch(String query, int limit) throws Exception {
        String url = BASE_URL + "/api/search?query=" + URLEncoder.encode(query, "UTF-8") + "&limit=" + limit;
        JSONObject root = getJson(url);
        JSONArray arr = root.getJSONArray("data");
        List<Song> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) out.add(Song.fromJson(arr.getJSONObject(i)));
        return out;
    }

    private JSONObject getJson(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "MexoMusic/2.0");
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return new JSONObject(sb.toString());
    }

    private String firstArtist(String artists) {
        if (artists == null) return "";
        int i = artists.indexOf(',');
        return (i > 0 ? artists.substring(0, i) : artists).trim();
    }

    private void loadImage(String url, ImageView iv) {
        if (url == null || url.isEmpty()) { iv.setImageBitmap(null); return; }
        iv.setTag(url);
        Bitmap cached = imgCache.get(url);
        if (cached != null) { iv.setImageBitmap(cached); return; }
        iv.setImageBitmap(null);
        exec.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                Bitmap bmp = BitmapFactory.decodeStream(c.getInputStream());
                if (bmp != null) {
                    imgCache.put(url, bmp);
                    runOnUiThread(() -> { if (url.equals(iv.getTag())) iv.setImageBitmap(bmp); });
                }
            } catch (Exception ignored) {}
        });
    }

    private String fmt(int sec) {
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(updater);
        if (mp != null) { mp.release(); mp = null; }
        exec.shutdownNow();
        super.onDestroy();
    }

    /* ================= List adapter ================= */

    class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return rows.get(position).header ? 0 : 1; }
        @Override public boolean isEnabled(int position) { return !rows.get(position).header; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row r = rows.get(position);
            if (r.header) {
                View v = convertView;
                if (v == null || v.findViewById(R.id.headerTv) == null)
                    v = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_header, parent, false);
                TextView tv = v.findViewById(R.id.headerTv);
                tv.setText(r.headerText);
                return v;
            }
            View v = convertView;
            if (v == null || v.findViewById(R.id.rowTitle) == null)
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_song, parent, false);
            Song s = r.song;
            ImageView img = v.findViewById(R.id.rowImg);
            TextView t = v.findViewById(R.id.rowTitle);
            TextView sub = v.findViewById(R.id.rowSub);
            Button saveBtn = v.findViewById(R.id.saveBtn);
            boolean playing = nowPlaying != null && nowPlaying.id.equals(s.id);
            t.setText((playing ? "▶ " : "") + s.title);
            t.setTextColor(playing ? 0xFF1DB954 : 0xFFFFFFFF);
            sub.setText(s.artists + "  •  " + fmt(s.duration));
            boolean saved = isSaved(s.id);
            saveBtn.setText(saved ? "✓" : "＋");
            saveBtn.setTextColor(saved ? 0xFF1DB954 : 0xFFFFFFFF);
            saveBtn.setOnClickListener(vv -> toggleSave(s));
            /* Row pe seedha click bhi - list ke click par bharosa nahi,
               kyunki row ke andar ka button kabhi-kabhi list ka tap rok deta hai */
            v.setOnClickListener(vv -> onSongTapped(s));
            loadImage(s.image, img);
            return v;
        }
    }
}
