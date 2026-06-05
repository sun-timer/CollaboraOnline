package org.libreoffice.androidlib.ai;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiMarkdownRenderer {
    private AiMarkdownRenderer() {
    }

    public static void render(String rawText, TextView target, boolean plainText) {
        if (target == null) {
            return;
        }
        String clean = normalize(rawText);
        if (clean.isEmpty()) {
            target.setText("");
            return;
        }
        if (plainText) {
            target.setText(clean);
            return;
        }
        target.setLineSpacing(0f, 1.0f);
        Spanned spanned = HtmlCompat.fromHtml(markdownToHtml(clean), HtmlCompat.FROM_HTML_MODE_COMPACT);
        target.setText(compactRenderedLineBreaks(trimTrailingWhitespace(spanned)));
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim();
    }

    private static String markdownToHtml(String markdown) {
        String src = escapeHtml(markdown).replace("\r\n", "\n");

        Matcher codeBlockMatcher = Pattern.compile("(?s)```\\s*\\n?(.*?)\\n?```").matcher(src);
        StringBuffer codeBuffer = new StringBuffer();
        while (codeBlockMatcher.find()) {
            String code = codeBlockMatcher.group(1);
            codeBlockMatcher.appendReplacement(codeBuffer,
                    "<pre><code>" + Matcher.quoteReplacement(code) + "</code></pre>");
        }
        codeBlockMatcher.appendTail(codeBuffer);
        src = codeBuffer.toString();

        String[] lines = src.split("\n");
        StringBuilder html = new StringBuilder();
        boolean inUl = false;
        boolean inOl = false;
        boolean inBlockquote = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("<pre><code>")) {
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                if (inBlockquote) {
                    html.append("</blockquote>");
                    inBlockquote = false;
                }
                html.append(trimmed);
                continue;
            }

            if (trimmed.isEmpty()) {
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                if (inBlockquote) {
                    html.append("</blockquote>");
                    inBlockquote = false;
                }
                continue;
            }

            if (trimmed.matches("^#{1,6}\\s+.*")) {
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                if (inBlockquote) {
                    html.append("</blockquote>");
                    inBlockquote = false;
                }
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }
                level = Math.min(level, 6);
                html.append("<h").append(level).append(">")
                        .append(applyInlineMarkdown(trimmed.substring(level).trim()))
                        .append("</h").append(level).append(">");
                continue;
            }

            if (trimmed.matches("^[\\-\\*]\\s+.*")) {
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                if (!inUl) {
                    html.append("<ul>");
                    inUl = true;
                }
                html.append("<li>").append(applyInlineMarkdown(trimmed.substring(2).trim())).append("</li>");
                continue;
            }

            if (trimmed.matches("^\\d+\\.\\s+.*")) {
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (!inOl) {
                    html.append("<ol>");
                    inOl = true;
                }
                html.append("<li>").append(applyInlineMarkdown(trimmed.replaceFirst("^\\d+\\.\\s+", ""))).append("</li>");
                continue;
            }

            if (trimmed.startsWith("&gt;")) {
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                if (!inBlockquote) {
                    html.append("<blockquote>");
                    inBlockquote = true;
                }
                html.append("<div>").append(applyInlineMarkdown(trimmed.substring(4).trim())).append("</div>");
                continue;
            }

            if (inUl) {
                html.append("</ul>");
                inUl = false;
            }
            if (inOl) {
                html.append("</ol>");
                inOl = false;
            }
            if (inBlockquote) {
                html.append("</blockquote>");
                inBlockquote = false;
            }
            html.append("<div>").append(applyInlineMarkdown(trimmed)).append("</div>");
        }

        if (inUl) {
            html.append("</ul>");
        }
        if (inOl) {
            html.append("</ol>");
        }
        if (inBlockquote) {
            html.append("</blockquote>");
        }
        return html.toString();
    }

    private static CharSequence trimTrailingWhitespace(CharSequence text) {
        if (text == null) {
            return "";
        }
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end == text.length() ? text : text.subSequence(0, end);
    }

    private static CharSequence compactRenderedLineBreaks(CharSequence text) {
        if (text == null) {
            return "";
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        boolean previousWasNewline = false;
        for (int i = 0; i < builder.length(); i++) {
            char ch = builder.charAt(i);
            if (ch == '\n') {
                if (previousWasNewline) {
                    builder.delete(i, i + 1);
                    i--;
                    continue;
                }
                previousWasNewline = true;
                continue;
            }
            if (previousWasNewline && Character.isWhitespace(ch)) {
                builder.delete(i, i + 1);
                i--;
                continue;
            }
            previousWasNewline = false;
        }
        return trimTrailingWhitespace(builder);
    }

    private static String applyInlineMarkdown(String text) {
        String out = text;
        out = out.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        out = out.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "<i>$1</i>");
        out = out.replaceAll("`([^`]+)`", "<code>$1</code>");
        out = out.replaceAll("\\[(.+?)\\]\\((https?://[^\\s)]+)\\)", "<a href=\"$2\">$1</a>");
        return out;
    }

    private static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
