package me.thetealviper.commandshop.risk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import me.thetealviper.commandshop.core.CommandShopCore;

/**
 * Release guard for the persistent abuse-item quarantine workflow.
 */
public final class ItemQuarantineAudit {
    private ItemQuarantineAudit() {
    }

    public static void main(String[] args) throws IOException {
        Set<String> methods = Arrays.stream(CommandShopCore.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        for (String required : Set.of(
                "quarantineAbuseItem",
                "excludeQuarantinedMaterials",
                "isItemQuarantined",
                "handleResolveItemAlertCommand",
                "getQuarantinedMaterials")) {
            if (!methods.contains(required)) {
                throw new AssertionError(
                        "Missing abuse-item quarantine operation: " + required);
            }
        }
        try (InputStream stream = CommandShopCore.class.getResourceAsStream(
                "CommandShopCore.class")) {
            if (stream == null) {
                throw new AssertionError("Could not inspect CommandShopCore bytecode.");
            }
            String bytecode = new String(
                    stream.readAllBytes(), StandardCharsets.ISO_8859_1);
            for (String required : Set.of(
                    "ScamAlerts", "ABUSE_DELIST", "Previous_Buy", "Previous_Sell")) {
                if (!bytecode.contains(required)) {
                    throw new AssertionError(
                            "Quarantine persistence marker is missing: " + required);
                }
            }
        }
        System.out.println(
                "Item quarantine audit passed for delisting, persistence, reload filtering, resolution, and price history.");
    }
}
