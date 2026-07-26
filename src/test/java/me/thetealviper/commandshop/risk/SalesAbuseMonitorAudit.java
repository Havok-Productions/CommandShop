package me.thetealviper.commandshop.risk;

import java.util.List;
import java.util.concurrent.TimeUnit;

public final class SalesAbuseMonitorAudit {
    private SalesAbuseMonitorAudit() {
    }

    public static void main(String[] args) {
        SalesAbuseMonitor monitor = new SalesAbuseMonitor();
        SalesAbuseMonitor.Thresholds thresholds =
                new SalesAbuseMonitor.Thresholds(30, 5000.0D, 64L, 1.10D);
        long now = 1_800_000_000_000L;

        SalesAbuseMonitor.Evaluation lowRatio = monitor.evaluate(
                List.of(), now, 128L, 6000.0D, 1.05D, thresholds);
        require(!lowRatio.shouldFlag(),
                "High legitimate revenue must not flag below the sale-ratio threshold.");

        SalesAbuseMonitor.Evaluation lowRevenue = monitor.evaluate(
                List.of(), now, 128L, 4999.0D, 3.0D, thresholds);
        require(!lowRevenue.shouldFlag(),
                "A suspicious ratio must not flag below the revenue threshold.");

        SalesAbuseMonitor.Evaluation concentrated = monitor.evaluate(
                List.of(), now, 128L, 6000.0D, 1.25D, thresholds);
        require(concentrated.shouldFlag(),
                "All configured thresholds should produce a flag.");

        String expired = (now - TimeUnit.MINUTES.toMillis(31))
                + ",1000,100000.0";
        SalesAbuseMonitor.Evaluation pruned = monitor.evaluate(
                List.of(expired), now, 1L, 1.0D, 10.0D, thresholds);
        require(pruned.totalAmount() == 1L && pruned.totalRevenue() == 1.0D,
                "Sales older than the rolling window must be pruned.");
        require(pruned.persistedEntries().size() == 1,
                "Expired entries must not be persisted again.");

        String recent = (now - TimeUnit.MINUTES.toMillis(10))
                + ",32,2500.0";
        SalesAbuseMonitor.Evaluation accumulated = monitor.evaluate(
                List.of(recent), now, 32L, 2500.0D, 1.20D, thresholds);
        require(accumulated.shouldFlag(),
                "Recent sales of the same item must accumulate inside the window.");
        require(accumulated.totalAmount() == 64L
                        && accumulated.totalRevenue() == 5000.0D,
                "Accumulated evidence must match persisted and current sales.");

        System.out.println("Sales abuse audit passed for ratio, revenue, item, and rolling-window thresholds.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
