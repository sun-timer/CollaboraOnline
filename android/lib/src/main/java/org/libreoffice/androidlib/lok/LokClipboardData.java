/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*- */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.libreoffice.androidlib.lok;

import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class LokClipboardData implements Serializable {
    public ArrayList<LokClipboardEntry> clipboardEntries = new ArrayList<LokClipboardEntry>();
    /** Identifies the exact Android system clip which owns this serialized rich payload. */
    public String clipToken;

    public void removeEmptyEntries() {
        if (clipboardEntries == null) {
            clipboardEntries = new ArrayList<>();
            return;
        }
        Iterator<LokClipboardEntry> iterator = clipboardEntries.iterator();
        while (iterator.hasNext()) {
            LokClipboardEntry entry = iterator.next();
            if (entry == null || entry.mime == null || entry.mime.trim().isEmpty()
                    || entry.data == null || entry.data.length == 0) {
                iterator.remove();
            }
        }
    }

    public String ensureClipToken() {
        if (clipToken == null || clipToken.trim().isEmpty()) {
            clipToken = UUID.randomUUID().toString();
        }
        return clipToken;
    }

    public String getText() {
        if (clipboardEntries == null) return null;
        for (LokClipboardEntry aEntry : clipboardEntries) {
            if (aEntry != null && aEntry.mime != null && aEntry.data != null
                    && aEntry.data.length > 0 && aEntry.mime.startsWith("text/plain")) {
                return new String(aEntry.data, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    public String getHtml() {
        if (clipboardEntries == null) return null;
        for (LokClipboardEntry aEntry : clipboardEntries) {
            if (aEntry != null && aEntry.mime != null && aEntry.data != null
                    && aEntry.data.length > 0 && aEntry.mime.startsWith("text/html")) {
                return new String(aEntry.data, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    public boolean writeToFile(File file) {
        try {
            FileOutputStream fileStream = new FileOutputStream(file.getAbsoluteFile());
            ObjectOutputStream oos = new ObjectOutputStream(fileStream);
            oos.writeObject(this);
            oos.close();
            fileStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static LokClipboardData createFromFile(File file) {
        try {
            FileInputStream fileStream = new FileInputStream(file.getAbsoluteFile());
            ObjectInputStream ois = new ObjectInputStream(fileStream);
            LokClipboardData data = (LokClipboardData)ois.readObject();
            ois.close();
            fileStream.close();
            return data;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public LokClipboardEntry getBest() {
        if (clipboardEntries != null) {
            for (LokClipboardEntry entry : clipboardEntries) {
                if (entry != null && entry.mime != null && !entry.mime.trim().isEmpty()
                        && entry.data != null && entry.data.length > 0) {
                    return entry;
                }
            }
        }
        return null;
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab cinoptions=b1,g0,N-s cinkeys+=0=break: */
