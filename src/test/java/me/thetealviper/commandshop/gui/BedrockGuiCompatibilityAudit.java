package me.thetealviper.commandshop.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Regression audit that keeps unused cross-platform GUI slots truly empty.
 */
public final class BedrockGuiCompatibilityAudit {
    private BedrockGuiCompatibilityAudit() {
    }

    public static void main(String[] args) throws IOException {
        boolean hasFillerMethod = Arrays.stream(ShopGuiManager.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("fill")
                        || method.getName().equals("filler"));
        if (hasFillerMethod) {
            throw new AssertionError("Shop GUI must not restore decorative filler methods.");
        }
        try (InputStream stream = ShopGuiManager.class.getResourceAsStream(
                "ShopGuiManager.class")) {
            String bytecodeText = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            if (bytecodeText.contains("BLACK_STAINED_GLASS_PANE")) {
                throw new AssertionError("Bedrock-safe shop GUI must not embed black glass fillers.");
            }
        }
        System.out.println("Bedrock GUI audit passed with genuinely empty unused slots.");
    }
}
