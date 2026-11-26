package org.libreoffice.androidlib;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/** A document snapshot and text range captured before opening an AI surface. */
final class ImpressTextTarget {
    final byte[] snapshot;
    final int view;
    final int part;
    final int objectIndex;
    final long generation;
    final String fullText;
    final String text;
    private final JSONArray selection;
    private final int start;
    private final int end;

    private ImpressTextTarget(byte[] snapshot, int view, int part, int objectIndex,
            long generation, JSONArray selection, String fullText, int start, int end) {
        this.snapshot = snapshot;
        this.view = view;
        this.part = part;
        this.objectIndex = objectIndex;
        this.generation = generation;
        this.selection = selection;
        this.fullText = fullText;
        this.start = start;
        this.end = end;
        this.text = fullText.substring(start, end);
    }

    static ImpressTextTarget read(byte[] snapshot, int view, int part, long generation) throws JSONException {
        JSONObject slide = slide(snapshot, part);
        JSONArray selected = slide.optJSONArray("SelectedObjects");
        if (selected == null || selected.length() != 1) return null;
        int index = selected.getInt(0);
        JSONObject object = slide.getJSONObject("Objects").getJSONObject("Objects " + index);
        if (object.optInt("TextCount") != 1) return null;
        JSONArray paragraphs = object.getJSONObject("Texts").getJSONObject("Text 0").getJSONArray("Paragraphs");
        String fullText = joinParagraphs(paragraphs);
        JSONArray selection = object.optJSONArray("ActiveSelection");
        int start = 0;
        int end = fullText.length();
        if (selection != null && selection.length() == 4) {
            start = offset(paragraphs, selection.getInt(0), selection.getInt(1));
            end = offset(paragraphs, selection.getInt(2), selection.getInt(3));
            if (start > end) {
                selection = new JSONArray().put(selection.getInt(2)).put(selection.getInt(3))
                        .put(selection.getInt(0)).put(selection.getInt(1));
                int previousStart = start;
                start = end;
                end = previousStart;
            }
        } else {
            selection = new JSONArray();
        }
        return new ImpressTextTarget(snapshot, view, part, index, generation, selection, fullText, start, end);
    }

    String expectedText(String replacement, boolean append) {
        int insertionStart = append ? end : start;
        return fullText.substring(0, insertionStart) + replacement + fullText.substring(end);
    }

    String arguments(String replacement, boolean append, String requestId) throws JSONException {
        JSONArray range = new JSONArray(selection.toString());
        if (append) {
            JSONArray paragraphs = slide(snapshot, part).getJSONObject("Objects")
                    .getJSONObject("Objects " + objectIndex).getJSONObject("Texts")
                    .getJSONObject("Text 0").getJSONArray("Paragraphs");
            int paragraph = selection.length() == 4 ? selection.getInt(2) : paragraphs.length() - 1;
            int character = selection.length() == 4 ? selection.getInt(3) : paragraphs.getString(paragraph).length();
            range = new JSONArray().put(paragraph).put(character);
        }
        JSONArray edits = new JSONArray().put(new JSONObject().put("SelectText", range))
                .put(new JSONObject().put("InsertText", replacement));
        JSONArray commands = new JSONArray().put(new JSONObject().put("JumpToSlide", part))
                .put(new JSONObject().put("EditTextObject." + objectIndex, edits));
        JSONObject transform = new JSONObject().put("TypesetTransaction", true)
                .put("TypesetRequestId", requestId)
                .put("Transforms", new JSONObject().put("SlideCommands", commands));
        return new JSONObject().put("DataJson", new JSONObject().put("type", "string")
                .put("value", android.net.Uri.encode(transform.toString())))
                .put("SynchronMode", new JSONObject().put("type", "boolean").put("value", true)).toString();
    }

    boolean matchesResult(byte[] result, String expected) throws JSONException {
        JSONObject object = slide(result, part).getJSONObject("Objects").getJSONObject("Objects " + objectIndex);
        JSONArray paragraphs = object.getJSONObject("Texts").getJSONObject("Text 0").getJSONArray("Paragraphs");
        return expected.replace("\r\n", "\n").replace('\r', '\n').equals(joinParagraphs(paragraphs));
    }

    private static JSONObject slide(byte[] snapshot, int part) throws JSONException {
        return new JSONObject(new String(snapshot, StandardCharsets.UTF_8)).getJSONObject("DocStructure")
                .getJSONObject("Slides").getJSONObject("Slide " + part);
    }

    private static String joinParagraphs(JSONArray paragraphs) throws JSONException {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < paragraphs.length(); i++) {
            if (i > 0) text.append('\n');
            text.append(paragraphs.getString(i));
        }
        return text.toString();
    }

    private static int offset(JSONArray paragraphs, int paragraph, int character) throws JSONException {
        if (paragraph < 0 || paragraph >= paragraphs.length() || character < 0
                || character > paragraphs.getString(paragraph).length()) throw new JSONException("无效的文本选区");
        int offset = character;
        for (int i = 0; i < paragraph; i++) offset += paragraphs.getString(i).length() + 1;
        return offset;
    }
}
