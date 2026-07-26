package me.thetealviper.commandshop.risk;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class SalesAbuseMonitorAudit {
    private SalesAbuseMonitorAudit() {
    }

    public static void main(String[] args) {
        SalesAbuseMonitor monitor = new SalesAbuseMonitor();
        SalesAbuseMonitor.Thresholds thresholds =
                new SalesAbuseMonitor.Thresholds(
                        30, 5000.0D, 2.0D, 1.10D, 100.0D);
        long now = 1_800_000_000_000L;

        SalesAbuseMonitor.Evaluation lowRatio = monitor.evaluate(
                List.of(), now, 128L, 6000.0D, 2500.0D, 1.05D, thresholds);
        require(!lowRatio.shouldFlag(),
                "High legitimate revenue must not flag below the profit-ratio threshold.");

        SalesAbuseMonitor.Evaluation lowRevenue = monitor.evaluate(
                List.of(), now, 128L, 4999.0D, 1000.0D, 3.0D, thresholds);
        require(!lowRevenue.shouldFlag(),
                "A suspicious ratio must not flag below the revenue threshold.");

        SalesAbuseMonitor.Evaluation oneUnitSale = monitor.evaluate(
                List.of(), now, 1L, 5000.0D, 5000.0D, 1.25D, thresholds);
        require(!oneUnitSale.shouldFlag(),
                "One expensive unit sale must not meet a 2x revenue-to-sale-price threshold.");

        SalesAbuseMonitor.Evaluation beaconExample = monitor.evaluate(
                List.of(), now, 2L, 5000.0D, 2500.0D, 1.25D, thresholds);
        require(beaconExample.shouldFlag(),
                "Two $2,500 beacon sales earning $5,000 should meet a 2x monetary threshold.");
        require(beaconExample.triggerReason()
                        == SalesAbuseMonitor.TriggerReason.PROFITABLE_PATH,
                "The beacon case should require known profitability evidence.");
        require(beaconExample.revenueToSalePriceRatio() == 2.0D,
                "The beacon monetary sale ratio should be exactly 2x.");

        SalesAbuseMonitor.Evaluation ironExploit = monitor.evaluate(
                List.of(), now, 640L, 10000.0D, 35.0D, 0.0D, thresholds);
        require(ironExploit.shouldFlag(),
                "$10,000 earned from a $35 item must trigger extreme-volume detection.");
        require(ironExploit.triggerReason()
                        == SalesAbuseMonitor.TriggerReason.EXTREME_VOLUME,
                "Unknown acquisition cost should use the extreme-volume trigger.");
        require(Math.abs(ironExploit.revenueToSalePriceRatio()
                        - (10000.0D / 35.0D)) < 0.000001D,
                "The iron monetary ratio must be earnings divided by unit sell price.");

        SalesAbuseMonitor.Evaluation legitimateUnknownCost = monitor.evaluate(
                List.of(), now, 10L, 5000.0D, 100.0D, 0.0D, thresholds);
        require(!legitimateUnknownCost.shouldFlag(),
                "Unknown acquisition cost below the 100x extreme threshold must not flag.");

        String expired = (now - TimeUnit.MINUTES.toMillis(31))
                + ",1000,100000.0";
        SalesAbuseMonitor.Evaluation pruned = monitor.evaluate(
                List.of(expired), now, 1L, 1.0D, 1.0D, 10.0D, thresholds);
        require(pruned.totalAmount() == 1L && pruned.totalRevenue() == 1.0D,
                "Sales older than the rolling window must be pruned.");
        require(pruned.persistedEntries().size() == 1,
                "Expired entries must not be persisted again.");

        String recent = (now - TimeUnit.MINUTES.toMillis(10))
                + ",1,2500.0";
        SalesAbuseMonitor.Evaluation accumulated = monitor.evaluate(
                List.of(recent), now, 1L, 2500.0D, 2500.0D, 1.20D, thresholds);
        require(accumulated.shouldFlag(),
                "Recent sales of the same item must accumulate inside the window.");
        require(accumulated.totalAmount() == 2L
                        && accumulated.totalRevenue() == 5000.0D,
                "Accumulated evidence must match persisted and current sales.");

        System.out.println("Sales abuse audit passed for monetary sale multiple, profit ratio, revenue, and rolling-window thresholds.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
