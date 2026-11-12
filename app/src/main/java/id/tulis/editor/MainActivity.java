package id.tulis.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends Activity {
    static final int BLUE = 0xFF2B579A, GRAY = 0xFFF3F3F3, INC = Spanned.SPAN_EXCLUSIVE_INCLUSIVE;
    static final int[] PT = {8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72};

    EditText ed;
    TextView title, status;
    LinearLayout ribbon;
    ScrollView page;
    TextView[] tabs = new TextView[3];
    File cur;
    boolean dirty, loading, dark;
    float d;

    int dp(int v) { return Math.round(v * d); }
    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    File dir() { File f = new File(Environment.getExternalStorageDirectory(), "Dokumen"); f.mkdirs(); return f; }
    File draft() { return new File(getFilesDir(), "draft.docx"); }
    int lo() { return Math.min(ed.getSelectionStart(), ed.getSelectionEnd()); }
    int hi() { return Math.max(ed.getSelectionStart(), ed.getSelectionEnd()); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        title = new TextView(this);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        title.setSingleLine();
        title.setGravity(Gravity.CENTER);
        title.setBackgroundColor(BLUE);
        title.setPadding(dp(12), dp(8), dp(12), dp(4));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout tr = new LinearLayout(this);
        tr.setBackgroundColor(BLUE);
        String[] names = {"Berkas", "Beranda", "Tampilan"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView t = new TextView(this);
            t.setText(names[i]);
            t.setTextSize(14);
            t.setPadding(dp(18), dp(8), dp(18), dp(8));
            t.setOnClickListener(v -> showTab(k));
            tabs[i] = t;
            tr.addView(t);
        }
        root.addView(tr, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setBackgroundColor(GRAY);
        hs.setHorizontalScrollBarEnabled(false);
        ribbon = new LinearLayout(this);
        hs.addView(ribbon, new FrameLayout.LayoutParams(-2, -1));
        root.addView(hs, new LinearLayout.LayoutParams(-1, dp(50)));

        View dv = new View(this);
        dv.setBackgroundColor(0xFFC8C8C8);
        root.addView(dv, new LinearLayout.LayoutParams(-1, 1));

        page = new ScrollView(this);
        page.setFillViewport(true);
        ed = new EditText(this);
        ed.setHint("Mulai mengetik di sini");
        ed.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        ed.setGravity(Gravity.TOP | Gravity.LEFT);
        ed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        ed.setMinHeight(Math.max(dm.widthPixels, dm.heightPixels));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(10), dp(10), dp(10), dp(10));
        page.addView(ed, lp);
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1f));
        applyTheme();

        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(12);
        status.setBackgroundColor(BLUE);
        status.setPadding(dp(12), dp(4), dp(12), dp(4));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        ed.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { if (!loading) dirty = true; refresh(); }
        });

        if (draft().exists() && load(draft())) {
            cur = null;
            dirty = true;
            toast("Draf terakhir dipulihkan");
        }
        showTab(1);
        refresh();
    }

    void applyTheme() {
        page.setBackgroundColor(dark ? 0xFF121212 : 0xFFE6E6E6);
        ed.setBackgroundColor(dark ? 0xFF1E1E1E : 0xFFFFFFFF);
        ed.setTextColor(dark ? 0xFFE8E8E8 : 0xFF000000);
        ed.setHintTextColor(0xFF888888);
        ed.setPadding(dp(22), dp(20), dp(22), dp(20));
    }

    void refresh() {
        title.setText((cur == null ? "Tanpa judul" : cur.getName()) + (dirty ? " \u2022" : "") + "  -  TulisDoc");
        String t = ed.getText().toString().trim();
        int words = t.isEmpty() ? 0 : t.split("\\s+").length;
        status.setText("Kata: " + words + "     Karakter: " + ed.getText().length());
    }

    void mark() { dirty = true; refresh(); }

    // ---------- Ribbon ----------
    void showTab(int k) {
        for (int i = 0; i < 3; i++) {
            tabs[i].setTextColor(i == k ? BLUE : 0xFFFFFFFF);
            tabs[i].setBackgroundColor(i == k ? GRAY : BLUE);
        }
        ribbon.removeAllViews();
        if (k == 0) {
            add("Baru", 0, v -> newDoc());
            add("Buka", 0, v -> openDlg());
            add("Simpan", 0, v -> save());
            add("Simpan Sebagai", 0, v -> saveAs());
        } else if (k == 1) {
            add("B", Typeface.BOLD, v -> toggle(Typeface.BOLD));
            add("I", Typeface.ITALIC, v -> toggle(Typeface.ITALIC));
            add("U", 0, v -> toggle(0));
            sep();
            add("A\u2212", 0, v -> size(-1));
            add("A+", 0, v -> size(1));
            sep();
            add("Kiri", 0, v -> align(Layout.Alignment.ALIGN_NORMAL));
            add("Tengah", 0, v -> align(Layout.Alignment.ALIGN_CENTER));
            add("Kanan", 0, v -> align(Layout.Alignment.ALIGN_OPPOSITE));
            sep();
            add("\u2022 Poin", 0, v -> bullets());
        } else {
            add("Cari & Ganti", 0, v -> findDlg());
            add(dark ? "Mode Terang" : "Mode Gelap", 0, v -> { dark = !dark; applyTheme(); showTab(2); });
        }
    }

    void add(String t, int style, View.OnClickListener l) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(14);
        v.setTextColor(0xFF222222);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(14), 0, dp(14), 0);
        v.setMinWidth(dp(44));
        v.setTypeface(null, style);
        v.setFocusable(false);
        StateListDrawable sd = new StateListDrawable();
        sd.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(0xFFCFE0F5));
        sd.addState(new int[]{}, new ColorDrawable(0));
        v.setBackground(sd);
        v.setOnClickListener(l);
        ribbon.addView(v, new LinearLayout.LayoutParams(-2, -1));
    }

    void sep() {
        View v = new View(this);
        v.setBackgroundColor(0xFFC8C8C8);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(1, -1);
        p.setMargins(dp(6), dp(10), dp(6), dp(10));
        ribbon.addView(v, p);
    }

    // ---------- Format ----------
    /** style: Typeface.BOLD, Typeface.ITALIC, atau 0 untuk garis bawah. */
    void toggle(int style) {
        int a = lo(), b = hi();
        if (a < 0 || a == b) { toast("Blok teks dulu"); return; }
        Editable e = ed.getText();
        boolean all = true;
        for (int i = a; i < b && all; i++) if (!has(e, i, style)) all = false;
        clear(e, a, b, style);
        if (!all) e.setSpan(style == 0 ? new UnderlineSpan() : new StyleSpan(style), a, b, INC);
        mark();
    }

    boolean has(Editable e, int i, int style) {
        if (style == 0) return e.getSpans(i, i + 1, UnderlineSpan.class).length > 0;
        for (StyleSpan sp : e.getSpans(i, i + 1, StyleSpan.class)) if ((sp.getStyle() & style) != 0) return true;
        return false;
    }

    void clear(Editable e, int a, int b, int style) {
        if (style == 0) {
            for (UnderlineSpan sp : e.getSpans(a, b, UnderlineSpan.class)) {
                int x = e.getSpanStart(sp), y = e.getSpanEnd(sp);
                e.removeSpan(sp);
                if (x < a) e.setSpan(new UnderlineSpan(), x, a, INC);
                if (y > b) e.setSpan(new UnderlineSpan(), b, y, INC);
            }
            return;
        }
        for (StyleSpan sp : e.getSpans(a, b, StyleSpan.class)) {
            int st = sp.getStyle();
            if ((st & style) == 0) continue;
            int x = e.getSpanStart(sp), y = e.getSpanEnd(sp);
            e.removeSpan(sp);
            if (x < a) e.setSpan(new StyleSpan(st), x, a, INC);
            if (y > b) e.setSpan(new StyleSpan(st), b, y, INC);
            int rest = st & ~style;
            if (rest != 0) e.setSpan(new StyleSpan(rest), Math.max(x, a), Math.min(y, b), INC);
        }
    }

    void size(int dir) {
        int a = lo(), b = hi();
        if (a < 0 || a == b) { toast("Blok teks dulu"); return; }
        Editable e = ed.getText();
        AbsoluteSizeSpan[] z = e.getSpans(a, a + 1, AbsoluteSizeSpan.class);
        int c = z.length == 0 ? 11 : Math.round(Docx.dipToPt(z[0].getSize()));
        int k = 0;
        for (int i = 0; i < PT.length; i++) if (PT[i] <= c) k = i;
        k = Math.max(0, Math.min(PT.length - 1, k + dir));
        for (AbsoluteSizeSpan sp : e.getSpans(a, b, AbsoluteSizeSpan.class)) {
            int x = e.getSpanStart(sp), y = e.getSpanEnd(sp), sz = sp.getSize();
            e.removeSpan(sp);
            if (x < a) e.setSpan(new AbsoluteSizeSpan(sz, true), x, a, INC);
            if (y > b) e.setSpan(new AbsoluteSizeSpan(sz, true), b, y, INC);
        }
        if (PT[k] != 11) e.setSpan(new AbsoluteSizeSpan(Docx.ptToDip(PT[k]), true), a, b, INC);
        toast("Ukuran " + PT[k] + " pt");
        mark();
    }

    void align(Layout.Alignment al) {
        Editable e = ed.getText();
        String txt = e.toString();
        int s = Math.max(lo(), 0), t = Math.max(hi(), 0);
        int ps = s == 0 ? 0 : txt.lastIndexOf('\n', s - 1) + 1;
        int q = t > s ? t - 1 : t;
        int nl = txt.indexOf('\n', q);
        int pe = nl < 0 ? txt.length() : nl + 1;
        for (AlignmentSpan sp : e.getSpans(ps, pe, AlignmentSpan.class)) {
            int x = e.getSpanStart(sp), y = e.getSpanEnd(sp);
            Layout.Alignment old = sp.getAlignment();
            e.removeSpan(sp);
            if (x < ps) e.setSpan(new AlignmentSpan.Standard(old), x, ps, Spanned.SPAN_PARAGRAPH);
            if (y > pe) e.setSpan(new AlignmentSpan.Standard(old), pe, y, Spanned.SPAN_PARAGRAPH);
        }
        if (al != Layout.Alignment.ALIGN_NORMAL)
            e.setSpan(new AlignmentSpan.Standard(al), ps, pe, Spanned.SPAN_PARAGRAPH);
        mark();
    }

    void bullets() {
        Editable e = ed.getText();
        String txt = e.toString();
        int s = Math.max(lo(), 0), t = Math.max(hi(), 0);
        int ps = s == 0 ? 0 : txt.lastIndexOf('\n', s - 1) + 1;
        int q = t > s ? t - 1 : t;
        ArrayList<Integer> starts = new ArrayList<Integer>();
        int i = ps;
        while (true) {
            starts.add(i);
            int nl = txt.indexOf('\n', i);
            if (nl < 0 || nl >= q) break;
            i = nl + 1;
        }
        boolean all = true;
        for (int p : starts) if (!txt.startsWith("\u2022 ", p)) all = false;
        for (int k = starts.size() - 1; k >= 0; k--) {
            int p = starts.get(k);
            if (all) e.delete(p, p + 2); else e.insert(p, "\u2022 ");
        }
        mark();
    }

    // ---------- Berkas ----------
    boolean load(File f) {
        try {
            SpannableStringBuilder sb = Docx.read(f);
            loading = true;
            ed.setText(sb);
            loading = false;
            dirty = false;
            return true;
        } catch (Exception x) {
            loading = false;
            toast("Gagal membuka: " + x.getMessage());
            return false;
        }
    }

    void confirmDiscard(final Runnable r) {
        if (!dirty) { r.run(); return; }
        new AlertDialog.Builder(this)
            .setMessage("Ada perubahan yang belum disimpan. Lanjutkan tanpa menyimpan?")
            .setPositiveButton("Lanjutkan", (a, b) -> r.run())
            .setNegativeButton("Batal", null)
            .show();
    }

    void newDoc() {
        confirmDiscard(() -> {
            loading = true;
            ed.setText("");
            loading = false;
            cur = null;
            dirty = false;
            draft().delete();
            refresh();
        });
    }

    void openDlg() {
        confirmDiscard(() -> {
            final File[] fs = dir().listFiles(new FilenameFilter() {
                public boolean accept(File x, String n) { return n.toLowerCase().endsWith(".docx"); }
            });
            if (fs == null || fs.length == 0) { toast("Belum ada file .docx di folder Dokumen"); return; }
            Arrays.sort(fs);
            String[] nm = new String[fs.length];
            for (int i = 0; i < fs.length; i++) nm[i] = fs[i].getName();
            new AlertDialog.Builder(this).setTitle("Buka dokumen")
                .setItems(nm, (dl, w) -> {
                    if (load(fs[w])) { cur = fs[w]; draft().delete(); refresh(); }
                }).show();
        });
    }

    void save() { if (cur == null) saveAs(); else doSave(cur); }

    void saveAs() {
        final EditText n = new EditText(this);
        n.setText(cur == null ? "Dokumen1" : cur.getName().replace(".docx", ""));
        new AlertDialog.Builder(this).setTitle("Simpan sebagai (.docx)").setView(n)
            .setPositiveButton("Simpan", (a, b) -> {
                String nm = n.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]", "_");
                if (nm.isEmpty()) nm = "Dokumen1";
                doSave(new File(dir(), nm + ".docx"));
            })
            .setNegativeButton("Batal", null).show();
    }

    void doSave(File f) {
        try {
            Docx.write(ed.getText(), f);
            cur = f;
            dirty = false;
            draft().delete();
            refresh();
            toast("Tersimpan: Dokumen/" + f.getName());
        } catch (Exception x) {
            toast("Gagal menyimpan: " + x.getMessage());
        }
    }

    void findDlg() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(8), dp(16), 0);
        final EditText a = new EditText(this);
        a.setHint("Cari");
        a.setSingleLine();
        final EditText b = new EditText(this);
        b.setHint("Ganti dengan");
        b.setSingleLine();
        l.addView(a);
        l.addView(b);
        new AlertDialog.Builder(this).setTitle("Cari & Ganti").setView(l)
            .setNeutralButton("Cari", (x, y) -> {
                String f = a.getText().toString();
                if (f.isEmpty()) return;
                String txt = ed.getText().toString();
                int i = txt.indexOf(f, Math.max(hi(), 0));
                if (i < 0) i = txt.indexOf(f);
                if (i < 0) { toast("Tidak ditemukan"); return; }
                ed.requestFocus();
                ed.setSelection(i, i + f.length());
            })
            .setPositiveButton("Ganti semua", (x, y) -> {
                String f = a.getText().toString();
                if (f.isEmpty()) return;
                Editable e = ed.getText();
                String txt = e.toString();
                int c = 0;
                int i = txt.lastIndexOf(f);
                while (i >= 0) {
                    e.replace(i, i + f.length(), b.getText());
                    c++;
                    i = i - f.length() >= 0 ? txt.lastIndexOf(f, i - f.length()) : -1;
                }
                toast(c + " penggantian");
            })
            .setNegativeButton("Tutup", null).show();
    }

    // ---------- Keyboard & siklus hidup ----------
    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.isCtrlPressed()) {
            switch (ev.getKeyCode()) {
                case KeyEvent.KEYCODE_S: if (ev.isShiftPressed()) saveAs(); else save(); return true;
                case KeyEvent.KEYCODE_B: toggle(Typeface.BOLD); return true;
                case KeyEvent.KEYCODE_I: toggle(Typeface.ITALIC); return true;
                case KeyEvent.KEYCODE_U: toggle(0); return true;
                case KeyEvent.KEYCODE_O: openDlg(); return true;
                case KeyEvent.KEYCODE_N: newDoc(); return true;
                case KeyEvent.KEYCODE_F: findDlg(); return true;
                case KeyEvent.KEYCODE_L: align(Layout.Alignment.ALIGN_NORMAL); return true;
                case KeyEvent.KEYCODE_E: align(Layout.Alignment.ALIGN_CENTER); return true;
                case KeyEvent.KEYCODE_R: align(Layout.Alignment.ALIGN_OPPOSITE); return true;
                default: break;
            }
        }
        return super.dispatchKeyEvent(ev);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dirty) {
            try {
                if (cur != null) { Docx.write(ed.getText(), cur); dirty = false; refresh(); }
                else Docx.write(ed.getText(), draft());
            } catch (Exception ignore) { }
        }
    }
}
