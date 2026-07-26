package me.thetealviper.commandshop.gui;

/**
 * Dependency-free regression audit for inventories containing over 45 entries.
 */
public final class PaginationAudit {
    private PaginationAudit() {
    }

    public static void main(String[] args) {
        expect(Pagination.maxPage(0, 45), 0);
        expect(Pagination.maxPage(45, 45), 0);
        expect(Pagination.maxPage(46, 45), 1);
        expect(Pagination.maxPage(91, 45), 2);
        expect(Pagination.clampPage(-4, 91, 45), 0);
        expect(Pagination.clampPage(50, 91, 45), 2);
        expect(Pagination.index(0, 44, 45), 44);
        expect(Pagination.index(1, 0, 45), 45);
        expect(Pagination.index(2, 0, 45), 90);
        expect(Pagination.index(2, 1, 45), 91);
        expect(Pagination.index(0, 45, 45), -1);
        System.out.println("Pagination audit passed for grouped catalogs over 45 items.");
    }

    private static void expect(int actual, int expected) {
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }
}
