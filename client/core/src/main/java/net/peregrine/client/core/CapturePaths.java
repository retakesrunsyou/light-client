package net.peregrine.client.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class CapturePaths {
    private CapturePaths() {}

    /** Minecraft adds its own screenshots directory and keeps its normal F2 feedback. */
    public static File screenshotRoot(File gameDir) {
        String configured = System.getProperty("lightclient.screenshotRoot");
        if (configured == null || configured.isBlank()) {
            return gameDir;
        }
        try {
            Path root = Path.of(configured);
            Path screenshots = Files.createDirectories(root.resolve("screenshots"));
            if (Files.isWritable(screenshots)) {
                return root.toFile();
            }
        } catch (IOException | InvalidPathException e) {
            System.err.println("[Light Client] Cannot use the Pictures folder for screenshots: " + e.getMessage());
        }
        return gameDir;
    }
}
