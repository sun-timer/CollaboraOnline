package org.libreoffice.androidlib.template;

import android.content.Context;
import android.net.Uri;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;

/** Reads the source selected in the native new-presentation form, off the UI thread. */
public final class ImpressSourceTextReader {
    private static final int MAX_CHARS = 150000;
    private static final int MAX_BYTES = 120 * 1024 * 1024;
    private ImpressSourceTextReader() { }

    public static String read(Context context, Uri uri, String filename) throws Exception {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String mime = context.getContentResolver().getType(uri);
        String text;
        try (InputStream raw = context.getContentResolver().openInputStream(uri)) {
            if (raw == null) throw new IOException("无法读取来源文档");
            InputStream stream = new FilterInputStream(raw) {
                private int total;
                @Override public int read() throws IOException {
                    int value = super.read();
                    if (value >= 0 && ++total > MAX_BYTES) throw new IOException("文档超过120M，暂不支持");
                    return value;
                }
                @Override public int read(byte[] bytes, int offset, int count) throws IOException {
                    int read = in.read(bytes, offset, count);
                    if (read > 0 && (total += read) > MAX_BYTES) throw new IOException("文档超过120M，暂不支持");
                    return read;
                }
            };
            if (name.endsWith(".pdf") || "application/pdf".equals(mime)) {
                PDFBoxResourceLoader.init(context.getApplicationContext());
                try (PDDocument pdf = PDDocument.load(stream)) { text = new PDFTextStripper().getText(pdf); }
            } else if (name.endsWith(".docx") || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime)) {
                StringBuilder result = new StringBuilder();
                try (ZipInputStream zip = new ZipInputStream(stream)) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (!"word/document.xml".equals(entry.getName())) continue;
                        XmlPullParser parser = Xml.newPullParser();
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
                        parser.setInput(zip, "UTF-8");
                        int event;
                        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT && result.length() < MAX_CHARS) {
                            if (event == XmlPullParser.START_TAG && "t".equals(parser.getName())) result.append(parser.nextText());
                            if (event == XmlPullParser.END_TAG && "p".equals(parser.getName())) result.append('\n');
                        }
                        break;
                    }
                }
                text = result.toString();
            } else if (name.endsWith(".txt") || name.endsWith(".md") || (mime != null && mime.startsWith("text/"))) {
                StringBuilder result = new StringBuilder();
                Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                char[] buffer = new char[4096];
                int read;
                while (result.length() < MAX_CHARS && (read = reader.read(buffer, 0, Math.min(buffer.length, MAX_CHARS - result.length()))) > 0) result.append(buffer, 0, read);
                text = result.toString();
            } else throw new IOException("请选择 TXT、DOCX 或文字型 PDF 文档");
        }
        if (text == null || text.trim().isEmpty()) throw new IOException("未能提取正文；扫描件需先识别文字");
        return text.substring(0, Math.min(text.length(), MAX_CHARS));
    }
}
