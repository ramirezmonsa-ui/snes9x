package com.snes9x.mobile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The save states of one game: an automatic one, kept when leaving the game, and {@link #COUNT}
 * slots the player saves to by hand. Each has a small picture of the moment it was saved.
 */
final class SaveSlots {
    static final int AUTO = 0;
    static final int COUNT = 5;

    private static final int THUMBNAIL_WIDTH = 240;

    private final File dir;
    private final String game;

    SaveSlots(File dir, String game) {
        this.dir = dir;
        this.game = game;
        // Before there were slots, each game had a single "<game>.state".
        File old = new File(dir, game + ".state");
        if (old.isFile() && !stateFile(1).exists()) {
            old.renameTo(stateFile(1));
        }
    }

    private File stateFile(int slot) {
        return new File(dir, game + (slot == AUTO ? ".auto" : ".slot" + slot) + ".state");
    }

    private File thumbnailFile(int slot) {
        return new File(dir, game + (slot == AUTO ? ".auto" : ".slot" + slot) + ".png");
    }

    boolean exists(int slot) {
        return stateFile(slot).isFile();
    }

    /** When the slot was saved, in milliseconds, or 0 if it is empty. */
    long time(int slot) {
        return exists(slot) ? stateFile(slot).lastModified() : 0;
    }

    byte[] load(int slot) {
        return readFile(stateFile(slot));
    }

    Bitmap thumbnail(int slot) {
        File file = thumbnailFile(slot);
        return file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
    }

    boolean save(int slot, byte[] state, Bitmap picture) {
        if (state == null || !writeFile(stateFile(slot), state)) {
            return false;
        }
        File thumbnail = thumbnailFile(slot);
        if (picture == null) {
            thumbnail.delete();
            return true;
        }
        int height = Math.max(1, Math.round(
                (float) picture.getHeight() * THUMBNAIL_WIDTH / picture.getWidth()));
        Bitmap small = Bitmap.createScaledBitmap(picture, THUMBNAIL_WIDTH, height, true);
        File temp = new File(thumbnail.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            small.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            return true;
        }
        temp.renameTo(thumbnail);
        return true;
    }

    static boolean writeFile(File file, byte[] data) {
        File temp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(data);
        } catch (IOException e) {
            return false;
        }
        return temp.renameTo(file);
    }

    static byte[] readFile(File file) {
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int n = in.read(data, offset, data.length - offset);
                if (n < 0) {
                    return null;
                }
                offset += n;
            }
            return data;
        } catch (IOException e) {
            return null;
        }
    }
}
