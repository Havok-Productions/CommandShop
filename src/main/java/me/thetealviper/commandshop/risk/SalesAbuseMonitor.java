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
            long saleAmount, double saleRevenue,
            double configuredSellUnitPrice, double saleToAcquisitionRatio,
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
        double revenueToSalePriceRatio =
                Double.isFinite(configuredSellUnitPrice)
                && configuredSellUnitPrice > 0.0D
                ? totalRevenue / configuredSellUnitPrice : 0.0D;
        boolean baseThresholdsMet =
                totalRevenue + EPSILON >= thresholds.minimumRevenue()
                && revenueToSalePriceRatio + EPSILON
                >= thresholds.minimumRevenueToSalePriceRatio();
        boolean profitablePath = saleToAcquisitionRatio + EPSILON
                >= thresholds.minimumProfitRatio();
        boolean extremeVolume = revenueToSalePriceRatio + EPSILON
                >= thresholds.minimumExtremeVolumeRatio();
        TriggerReason triggerReason = null;
        if (baseThresholdsMet && profitablePath && extremeVolume) {
            triggerReason = TriggerReason.PROFITABLE_PATH_AND_EXTREME_VOLUME;
        } else if (baseThresholdsMet && profitablePath) {
            triggerReason = TriggerReason.PROFITABLE_PATH;
        } else if (baseThresholdsMet && extremeVolume) {
            triggerReason = TriggerReason.EXTREME_VOLUME;
        }
        return new Evaluation(
                List.copyOf(encoded), totalAmount, totalRevenue,
                revenueToSalePriceRatio, saleToAcquisitionRatio, triggerReason);
    }

    public record Thresholds(
            int windowMinutes,
            double minimumRevenue,
            double minimumRevenueToSalePriceRatio,
            double minimumProfitRatio,
            double minimumExtremeVolumeRatio) {

        public Thresholds {
            windowMinutes = Math.max(1, windowMinutes);
            minimumRevenue = Math.max(0.0D, minimumRevenue);
            minimumRevenueToSalePriceRatio =
                    Math.max(1.0D, minimumRevenueToSalePriceRatio);
            minimumProfitRatio = Math.max(1.0D, minimumProfitRatio);
            minimumExtremeVolumeRatio =
                    Math.max(minimumRevenueToSalePriceRatio, minimumExtremeVolumeRatio);
        }
    }

    public record Evaluation(
            List<String> persistedEntries,
            long totalAmount,
            double totalRevenue,
            double revenueToSalePriceRatio,
            double saleToAcquisitionRatio,
            TriggerReason triggerReason) {

        public boolean shouldFlag() {
            return triggerReason != null;
        }
    }

    public enum TriggerReason {
        PROFITABLE_PATH,
        EXTREME_VOLUME,
        PROFITABLE_PATH_AND_EXTREME_VOLUME
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
