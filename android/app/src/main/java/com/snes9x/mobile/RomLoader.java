package com.snes9x.mobile;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Reads a ROM picked by the user, unpacking it if it is inside a .zip. */
final class RomLoader {
    // Game Boy Advance games go up to 32MB.
    private static final int MAX_ROM_SIZE = 32 * 1024 * 1024;

    /** A ROM's contents and the console it is for. */
    static final class Rom {
        final byte[] data;
        final Console console;

        Rom(byte[] data, Console console) {
            this.data = data;
            this.console = console;
        }
    }

    private RomLoader() {
    }

    static Rom read(ContentResolver resolver, Uri uri) throws IOException {
        byte[] data;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("No se pudo abrir el archivo");
            }
            data = readAll(in);
        }

        String name = fileName(resolver, uri);
        if (isZip(data)) {
            try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(data))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.isDirectory() && Console.fromFileName(entry.getName()) != null) {
                        name = entry.getName();
                        data = readAll(zip);
                        break;
                    }
                }
            }
            if (isZip(data)) {
                throw new IOException("El .zip no contiene ningún juego compatible");
            }
        }

        Console console = Console.fromFileName(name);
        return new Rom(data, console != null ? console : Console.fromRom(data));
    }

    /** The console of a file, looking only at names when possible. */
    static Console detect(ContentResolver resolver, Uri uri) {
        Console console = Console.fromFileName(fileName(resolver, uri));
        if (console != null) {
            return console;
        }
        try {
            return read(resolver, uri).console;
        } catch (IOException | RuntimeException e) {
            return Console.SNES;
        }
    }

    private static boolean isZip(byte[] data) {
        // Zip files start with "PK\3\4".
        return data.length >= 4 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4;
    }

    /** Display name of the file without its extension, used to name saves. */
    static String displayName(ContentResolver resolver, Uri uri) {
        String name = fileName(resolver, uri);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** File name with its extension. */
    static String fileName(ContentResolver resolver, Uri uri) {
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
        return name;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
            if (out.size() > MAX_ROM_SIZE) {
                throw new IOException("El archivo es demasiado grande para ser un juego");
            }
        }
        return out.toByteArray();
    }
}
