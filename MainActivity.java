package com.example.kaydedilenler;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String PREFS = "kayitlar";
    private static final String KEY = "liste";
    private static final String ALL = "Tümü";

    private final List<JSONObject> all = new ArrayList<>();
    private final List<JSONObject> shown = new ArrayList<>();
    private ArrayAdapter<JSONObject> adapter;
    private EditText search;
    private Spinner filter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int p = dp(12);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, dp(32), p, p);

        TextView title = new TextView(this);
        title.setText("Kaydedilenler");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Bir gönderide Paylaş → Kaydedilenler seçin.");
        hint.setPadding(0, dp(4), 0, dp(8));
        root.addView(hint);

        Button paste = new Button(this);
        paste.setText("Panodan bağlantı ekle");
        paste.setOnClickListener(v -> addFromClipboard());
        root.addView(paste);

        search = new EditText(this);
        search.setHint("Ara…");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            public void onTextChanged(CharSequence s, int a, int c, int d) {}
            public void afterTextChanged(Editable s) { applyFilter(); }
        });
        root.addView(search);

        filter = new Spinner(this);
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v, int pos, long id) { applyFilter(); }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        root.addView(filter);

        empty = new TextView(this);
        empty.setText("Henüz kayıt yok.");
        empty.setPadding(0, dp(24), 0, 0);
        root.addView(empty);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<JSONObject>(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, shown) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                View v = super.getView(pos, cv, parent);
                JSONObject o = getItem(pos);
                ((TextView) v.findViewById(android.R.id.text1))
                        .setText(o.optString("platform") + " • " + o.optString("date"));
                ((TextView) v.findViewById(android.R.id.text2)).setText(o.optString("url"));
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((a, v, pos, id) -> openUrl(shown.get(pos).optString("url")));
        list.setOnItemLongClickListener((a, v, pos, id) -> {
            showOptions(shown.get(pos));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        load();
        rebuildSpinner();
        applyFilter();
        if (b == null) handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
        String text = i.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) return;
        Matcher m = Pattern.compile("https?://\\S+").matcher(text);
        if (m.find()) {
            add(m.group());
        } else {
            Toast.makeText(this, "Bağlantı bulunamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void addFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData d = cm.getPrimaryClip();
        if (d == null || d.getItemCount() == 0) {
            Toast.makeText(this, "Pano boş", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence t = d.getItemAt(0).coerceToText(this);
        Matcher m = Pattern.compile("https?://\\S+").matcher(t);
        if (m.find()) add(m.group());
        else Toast.makeText(this, "Panoda bağlantı yok", Toast.LENGTH_SHORT).show();
    }

    private void add(String url) {
        for (JSONObject o : all) {
            if (o.optString("url").equals(url)) {
                Toast.makeText(this, "Zaten kayıtlı", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        try {
            JSONObject o = new JSONObject();
            o.put("url", url);
            o.put("platform", detectPlatform(url));
            o.put("date", new SimpleDateFormat("dd.MM.yyyy HH:mm", new Locale("tr")).format(new Date()));
            all.add(0, o);
            save();
            rebuildSpinner();
            applyFilter();
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Kaydedilemedi", Toast.LENGTH_SHORT).show();
        }
    }

    private String detectPlatform(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains("instagram.com")) return "Instagram";
        if (u.contains("tiktok.com")) return "TikTok";
        if (u.contains("twitter.com") || u.contains("//x.com") || u.contains(".x.com")) return "X";
        if (u.contains("youtube.com") || u.contains("youtu.be")) return "YouTube";
        if (u.contains("facebook.com") || u.contains("fb.watch") || u.contains("fb.com")) return "Facebook";
        if (u.contains("reddit.com") || u.contains("redd.it")) return "Reddit";
        if (u.contains("pinterest.")|| u.contains("pin.it")) return "Pinterest";
        if (u.contains("linkedin.com")) return "LinkedIn";
        return "Diğer";
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOptions(JSONObject o) {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"Bağlantıyı kopyala", "Sil"}, (d, which) -> {
                    if (which == 0) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("url", o.optString("url")));
                        Toast.makeText(this, "Kopyalandı", Toast.LENGTH_SHORT).show();
                    } else {
                        all.remove(o);
                        save();
                        rebuildSpinner();
                        applyFilter();
                    }
                })
                .show();
    }

    private void rebuildSpinner() {
        Object cur = filter.getSelectedItem();
        TreeSet<String> set = new TreeSet<>();
        for (JSONObject o : all) set.add(o.optString("platform"));
        List<String> items = new ArrayList<>();
        items.add(ALL);
        items.addAll(set);
        filter.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, items));
        int idx = cur == null ? -1 : items.indexOf(cur.toString());
        if (idx >= 0) filter.setSelection(idx);
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        Object selObj = filter.getSelectedItem();
        String sel = selObj == null ? ALL : selObj.toString();
        shown.clear();
        for (JSONObject o : all) {
            String plat = o.optString("platform");
            if (!sel.equals(ALL) && !sel.equals(plat)) continue;
            if (!q.isEmpty() && !(o.optString("url").toLowerCase(Locale.ROOT).contains(q)
                    || plat.toLowerCase(Locale.ROOT).contains(q))) continue;
            shown.add(o);
        }
        adapter.notifyDataSetChanged();
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void load() {
        all.clear();
        try {
            JSONArray a = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) all.add(a.getJSONObject(i));
        } catch (Exception ignored) {}
    }

    private void save() {
        JSONArray a = new JSONArray();
        for (JSONObject o : all) a.put(o);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, a.toString()).apply();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
