package me.thetealviper.commandshop.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Pure rolling-window evaluator. Persistence and player enforcement remain in
 * the Bukkit-facing core, while the threshold math stays independently
 * testable.
 */
public final class SalesAbuseMonitor {
    private static final double EPSILON = 0.0000001D;

    public Evaluation evaluate(List<String> persistedEntries, long now,
            long saleAmount, double saleRevenue, double saleRatio,
            Thresholds thresholds) {
        long cutoff = now - TimeUnit.MINUTES.toMillis(thresholds.windowMinutes());
        List<SaleEntry> entries = new ArrayList<>();
        for (String encoded : persistedEntries) {
            SaleEntry parsed = SaleEntry.parse(encoded);
            if (parsed != null && parsed.timestamp() >= cutoff
                    && parsed.timestamp() <= now) {
                entries.add(parsed);
            }
        }
        entries.add(new SaleEntry(now, saleAmount, saleRevenue));

        List<String> encoded = new ArrayList<>();
        long totalAmount = 0L;
        double totalRevenue = 0.0D;
        for (SaleEntry entry : entries) {
            encoded.add(entry.encode());
            totalAmount += entry.amount();
            totalRevenue += entry.revenue();
        }
        boolean flag = totalRevenue + EPSILON >= thresholds.minimumRevenue()
                && totalAmount >= thresholds.minimumItems()
                && saleRatio + EPSILON >= thresholds.minimumSaleRatio();
        return new Evaluation(
                List.copyOf(encoded), totalAmount, totalRevenue, saleRatio, flag);
    }

    public record Thresholds(
            int windowMinutes,
            double minimumRevenue,
            long minimumItems,
            double minimumSaleRatio) {

        public Thresholds {
            windowMinutes = Math.max(1, windowMinutes);
            minimumRevenue = Math.max(0.0D, minimumRevenue);
            minimumItems = Math.max(1L, minimumItems);
            minimumSaleRatio = Math.max(1.0D, minimumSaleRatio);
        }
    }

    public record Evaluation(
            List<String> persistedEntries,
            long totalAmount,
            double totalRevenue,
            double saleRatio,
            boolean shouldFlag) {
    }

    private record SaleEntry(long timestamp, long amount, double revenue) {
        private String encode() {
            return timestamp + "," + amount + "," + Double.toString(revenue);
        }

        private static SaleEntry parse(String encoded) {
            try {
                String[] parts = encoded.split(",", 3);
                if (parts.length != 3) {
                    return null;
                }
                long timestamp = Long.parseLong(parts[0]);
                long amount = Long.parseLong(parts[1]);
                double revenue = Double.parseDouble(parts[2]);
                if (timestamp < 0L || amount <= 0L
                        || !Double.isFinite(revenue) || revenue < 0.0D) {
                    return null;
                }
                return new SaleEntry(timestamp, amount, revenue);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
    }
}
