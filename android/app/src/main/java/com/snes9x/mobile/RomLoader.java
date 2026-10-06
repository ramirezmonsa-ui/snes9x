package com.snes9x.mobile;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads a ROM picked by the user, unpacking it if it is inside a .zip. */
final class RomLoader {
    private static final String[] ROM_EXTENSIONS = {".sfc", ".smc", ".swc", ".fig", ".bs"};
    private static final int MAX_ROM_SIZE = 16 * 1024 * 1024;

    private RomLoader() {
    }

    static byte[] read(ContentResolver resolver, Uri uri) throws IOException {
        byte[] data;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("No se pudo abrir el archivo");
            }
            data = readAll(in);
        }

        // Zip files start with "PK\3\4".
        if (data.length >= 4 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4) {
            data = unzip(data);
        }
        return data;
    }

    /** Display name of the file without its extension, used to name saves. */
    static String displayName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor cursor = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (RuntimeException e) {
            // Some providers don't support queries, fall back to the path.
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        if (name == null) {
            name = "juego";
        }
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name;
    }

    private static byte[] unzip(byte[] zip) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (!entry.isDirectory() && isRom(entry.getName())) {
                    return readAll(in);
                }
            }
        }
        throw new IOException("El .zip no contiene ninguna ROM de SNES");
    }

    private static boolean isRom(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : ROM_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
            if (out.size() > MAX_ROM_SIZE) {
                throw new IOException("El archivo es demasiado grande para ser una ROM de SNES");
            }
        }
        return out.toByteArray();
    }
}
