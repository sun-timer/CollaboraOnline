package org.libreoffice.androidlib.typeset;

/**
 * Represents an image extracted from the source docx during typeset.
 * Each instance corresponds to one {@code [图N]} marker in the extracted text.
 */
public class TypesetImageEntry {
    /** Marker name without brackets, e.g. "图1" */
    public final String marker;
    /** Raw image bytes (PNG, JPEG, GIF, etc.) */
    public final byte[] imageData;
    /** MIME type like "image/png" or "image/jpeg" */
    public final String mimeType;
    /** File extension without dot, e.g. "png" */
    public final String extension;
    /** Original width in EMU (1 inch = 914400 EMU). 0 if unknown. */
    public final long cx;
    /** Original height in EMU. 0 if unknown. */
    public final long cy;

    public TypesetImageEntry(String marker, byte[] imageData,
                              String mimeType, String extension,
                              long cx, long cy) {
        this.marker = marker;
        this.imageData = imageData;
        this.mimeType = mimeType;
        this.extension = extension;
        this.cx = cx;
        this.cy = cy;
    }
}
