package net.peregrine.client.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the player has put the main menu's buttons: each one can be hidden, or moved to a
 * spot of the player's choosing (kept as a fraction of the screen, so it survives a window
 * resize). Saved as title-layout.txt beside the client's config, one line per button:
 * {@code id hidden fx fy}, with fx = -1 for "not moved".
 */
final class TitleLayout {

    static final class Entry {
        boolean hidden;
        float fx = -1f, fy = -1f;

        boolean moved() {
            return fx >= 0f && fy >= 0f;
        }
    }

    private final Map<String, Entry> entries = new HashMap<String, Entry>();
    private Path file;

    Entry get(String id) {
        Entry e = entries.get(id);
        if (e == null) {
            e = new Entry();
            entries.put(id, e);
        }
        return e;
    }

    boolean hidden(String id) {
        Entry e = entries.get(id);
        return e != null && e.hidden;
    }

    boolean moved(String id) {
        Entry e = entries.get(id);
        return e != null && e.moved();
    }

    /** Reads the saved layout from the file next to configFile (once). */
    void load(Path configFile) {
        if (file != null || configFile == null || configFile.getParent() == null) {
            return;
        }
        file = configFile.getParent().resolve("title-layout.txt");
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] t = line.trim().split("\\s+");
                if (t.length < 4) {
                    continue;
                }
                try {
                    Entry e = get(t[0]);
                    e.hidden = "1".equals(t[1]);
                    e.fx = Float.parseFloat(t[2]);
                    e.fy = Float.parseFloat(t[3]);
                } catch (NumberFormatException ignored) {
                    // a damaged line: keep the default for that button
                }
            }
        } catch (IOException e) {
            System.out.println("[light-client] couldn't read the menu layout: " + e.getMessage());
        }
    }

    void save() {
        if (file == null) {
            return;
        }
        try {
            List<String> lines = new ArrayList<String>();
            for (Map.Entry<String, Entry> en : entries.entrySet()) {
                Entry e = en.getValue();
                if (e.hidden || e.moved()) {
                    lines.add(en.getKey() + " " + (e.hidden ? 1 : 0) + " " + e.fx + " " + e.fy);
                }
            }
            if (lines.isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                Files.write(file, lines, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.out.println("[light-client] couldn't save the menu layout: " + e.getMessage());
        }
    }

    /** Back to the default layout. */
    void reset() {
        entries.clear();
        save();
    }

    boolean customised() {
        for (Entry e : entries.values()) {
            if (e.hidden || e.moved()) {
                return true;
            }
        }
        return false;
    }
}
