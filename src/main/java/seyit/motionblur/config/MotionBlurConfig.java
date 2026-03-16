package seyit.motionblur.config;

import seyit.motionblur.MotionBlurMod;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MotionBlurConfig {

    private static final String KEY = "motionblur.amount";
    private static final int DEFAULT_AMOUNT = 50;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(MotionBlurMod.ID + ".properties");

    private static int motionBlurAmount = DEFAULT_AMOUNT;

    public static void load() {
        motionBlurAmount = readAmount();
        save();
    }

    public static int getMotionBlurAmount() {
        return motionBlurAmount;
    }

    public static void setMotionBlurAmount(int value) {
        motionBlurAmount = clamp(value);
        save();
    }

    private static int readAmount() {
        if (!Files.exists(FILE)) {
            return DEFAULT_AMOUNT;
        }

        try {
            List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith(KEY + "=")) {
                    return clamp(Integer.parseInt(line.substring(KEY.length() + 1).trim()));
                }
            }
        } catch (IOException | NumberFormatException e) {
            MotionBlurMod.LOGGER.error("Failed to read motion blur config", e);
        }

        return DEFAULT_AMOUNT;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, KEY + "=" + motionBlurAmount + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MotionBlurMod.LOGGER.error("Failed to save motion blur config", e);
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
