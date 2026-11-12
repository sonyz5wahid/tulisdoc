package id.tulis.editor;

import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Xml;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.xmlpull.v1.XmlPullParser;

/** Baca/tulis .docx dasar: teks, tebal, miring, garis bawah, ukuran, perataan. */
final class Docx {
    static final int BASE = 16; // 16 dip di layar = 11 pt di dokumen
    private static final int INC = Spanned.SPAN_EXCLUSIVE_INCLUSIVE;
    private static final String DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";

    static int ptToDip(float pt) { return Math.round(pt * BASE / 11f); }
    static float dipToPt(int dip) { return dip * 11f / BASE; }

    // ---------- TULIS ----------
    static void write(Spanned t, File f) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));
        try {
            put(z, "[Content_Types].xml", DECL
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>");
            put(z, "_rels/.rels", DECL
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "</Relationships>");
            put(z, "word/document.xml", body(t));
        } finally {
            z.close();
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            if (!tmp.renameTo(f)) throw new IOException("Tidak bisa menulis file");
        }
    }

    private static void put(ZipOutputStream z, String name, String s) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(s.getBytes("UTF-8"));
        z.closeEntry();
    }

    private static String body(Spanned t) {
        StringBuilder x = new StringBuilder(DECL);
        x.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        String s = t.toString();
        AlignmentSpan[] als = t.getSpans(0, t.length(), AlignmentSpan.class);
        int ps = 0;
        while (true) {
            int pe = s.indexOf('\n', ps);
            boolean last = pe < 0;
            if (last) pe = s.length();
            x.append("<w:p>");
            String jc = jc(t, als, ps, pe);
            if (jc != null) x.append("<w:pPr><w:jc w:val=\"").append(jc).append("\"/></w:pPr>");
            int i = ps;
            while (i < pe) {
                int n = t.nextSpanTransition(i, pe, CharacterStyle.class);
                if (n <= i) n = pe;
                run(x, t, i, n);
                i = n;
            }
            x.append("</w:p>");
            if (last) break;
            ps = pe + 1;
        }
        x.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
            + "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/>"
            + "</w:sectPr></w:body></w:document>");
        return x.toString();
    }

    private static String jc(Spanned t, AlignmentSpan[] als, int ps, int pe) {
        for (AlignmentSpan a : als) {
            int s = t.getSpanStart(a), e = t.getSpanEnd(a);
            if (s <= ps && (e > ps || (e == ps && pe == ps))) {
                Layout.Alignment al = a.getAlignment();
                if (al == Layout.Alignment.ALIGN_CENTER) return "center";
                if (al == Layout.Alignment.ALIGN_OPPOSITE) return "right";
            }
        }
        return null;
    }

    private static void run(StringBuilder x, Spanned t, int a, int b) {
        boolean bo = false, it = false, un = false;
        int dip = 0;
        for (Object o : t.getSpans(a, b, Object.class)) {
            if (o instanceof StyleSpan) {
                int st = ((StyleSpan) o).getStyle();
                if ((st & Typeface.BOLD) != 0) bo = true;
                if ((st & Typeface.ITALIC) != 0) it = true;
            } else if (o instanceof UnderlineSpan) {
                un = true;
            } else if (o instanceof AbsoluteSizeSpan) {
                dip = ((AbsoluteSizeSpan) o).getSize();
            }
        }
        x.append("<w:r>");
        if (bo || it || un || (dip > 0 && dip != BASE)) {
            x.append("<w:rPr>");
            if (bo) x.append("<w:b/>");
            if (it) x.append("<w:i/>");
            if (dip > 0 && dip != BASE) x.append("<w:sz w:val=\"").append(Math.round(dipToPt(dip) * 2)).append("\"/>");
            if (un) x.append("<w:u w:val=\"single\"/>");
            x.append("</w:rPr>");
        }
        String s = t.subSequence(a, b).toString();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t') {
                flush(x, buf);
                x.append("<w:tab/>");
            } else if (c == '&') buf.append("&amp;");
            else if (c == '<') buf.append("&lt;");
            else if (c == '>') buf.append("&gt;");
            else if (c >= 0x20) buf.append(c);
        }
        flush(x, buf);
        x.append("</w:r>");
    }

    private static void flush(StringBuilder x, StringBuilder buf) {
        if (buf.length() > 0) {
            x.append("<w:t xml:space=\"preserve\">").append(buf).append("</w:t>");
            buf.setLength(0);
        }
    }

    // ---------- BACA ----------
    static SpannableStringBuilder read(File f) throws Exception {
        ZipInputStream z = new ZipInputStream(new BufferedInputStream(new FileInputStream(f)));
        try {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().equals("word/document.xml")) return parse(z);
            }
        } finally {
            z.close();
        }
        throw new IOException("Bukan file .docx yang valid");
    }

    private static boolean on(XmlPullParser p) {
        String v = p.getAttributeValue(null, "w:val");
        return v == null || !(v.equals("0") || v.equals("false") || v.equals("none"));
    }

    private static SpannableStringBuilder parse(InputStream in) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setInput(in, "UTF-8");
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int ps = 0, dip = 0;
        String jc = null;
        boolean inPPr = false, inRPr = false, bo = false, it = false, un = false, sawP = false;
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
            if (ev == XmlPullParser.START_TAG) {
                String n = p.getName();
                if (n.equals("w:p")) { ps = sb.length(); jc = null; sawP = true; }
                else if (n.equals("w:pPr")) inPPr = true;
                else if (n.equals("w:r")) { bo = false; it = false; un = false; dip = 0; }
                else if (n.equals("w:rPr")) inRPr = true;
                else if (n.equals("w:jc")) { if (inPPr) jc = p.getAttributeValue(null, "w:val"); }
                else if (inRPr && !inPPr) {
                    if (n.equals("w:b")) bo = on(p);
                    else if (n.equals("w:i")) it = on(p);
                    else if (n.equals("w:u")) un = on(p);
                    else if (n.equals("w:sz")) {
                        try { dip = ptToDip(Integer.parseInt(p.getAttributeValue(null, "w:val")) / 2f); }
                        catch (Exception ignore) { }
                    }
                }
                else if (n.equals("w:tab") && !inPPr) sb.append('\t');
                else if (n.equals("w:br")) sb.append('\n');
                else if (n.equals("w:t")) add(sb, p.nextText(), bo, it, un, dip);
            } else if (ev == XmlPullParser.END_TAG) {
                String n = p.getName();
                if (n.equals("w:pPr")) inPPr = false;
                else if (n.equals("w:rPr")) inRPr = false;
                else if (n.equals("w:p")) {
                    sb.append('\n');
                    try {
                        if ("center".equals(jc))
                            sb.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), ps, sb.length(), Spanned.SPAN_PARAGRAPH);
                        else if ("right".equals(jc) || "end".equals(jc))
                            sb.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE), ps, sb.length(), Spanned.SPAN_PARAGRAPH);
                    } catch (RuntimeException ignore) { }
                }
            }
        }
        if (sawP && sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.delete(sb.length() - 1, sb.length());
        return sb;
    }

    private static void add(SpannableStringBuilder sb, String t, boolean bo, boolean it, boolean un, int dip) {
        int s = sb.length();
        sb.append(t);
        int e = sb.length();
        if (e == s) return;
        if (bo || it) sb.setSpan(new StyleSpan(bo && it ? Typeface.BOLD_ITALIC : bo ? Typeface.BOLD : Typeface.ITALIC), s, e, INC);
        if (un) sb.setSpan(new UnderlineSpan(), s, e, INC);
        if (dip > 0 && dip != BASE) sb.setSpan(new AbsoluteSizeSpan(dip, true), s, e, INC);
    }
}
