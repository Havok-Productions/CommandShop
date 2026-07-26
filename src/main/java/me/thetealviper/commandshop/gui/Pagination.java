package me.thetealviper.commandshop.gui;

/**
 * Shared inventory-page calculations for category and variant menus.
 */
public final class Pagination {
    private Pagination() {
    }

    public static int maxPage(int entryCount, int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        return Math.max(0, (Math.max(0, entryCount) - 1) / pageSize);
    }

    public static int clampPage(int requestedPage, int entryCount, int pageSize) {
        return Math.max(0, Math.min(requestedPage, maxPage(entryCount, pageSize)));
    }

    public static int index(int page, int slot, int pageSize) {
        if (page < 0 || slot < 0 || slot >= pageSize || pageSize <= 0) {
            return -1;
        }
        return page * pageSize + slot;
    }
}
