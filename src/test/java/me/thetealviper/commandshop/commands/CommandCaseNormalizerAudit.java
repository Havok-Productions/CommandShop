package me.thetealviper.commandshop.commands;

/**
 * Regression audit for mixed-case player, console, namespaced, and unrelated
 * command roots.
 */
public final class CommandCaseNormalizerAudit {
    private CommandCaseNormalizerAudit() {
    }

    public static void main(String[] args) {
        expect("/shop", "/shop");
        expect("/Shop", "/shop");
        expect("/sHoP remove IRON_INGOT", "/shop remove IRON_INGOT");
        expect("/BUY Iron_Ingot 16", "/buy Iron_Ingot 16");
        expect("/SeLl-Gui", "/sell-gui");
        expect("/CommandShop:ShOp inspect SomePlayer",
                "/commandshop:shop inspect SomePlayer");
        expect("SeTpRiCe BUY hand 35", "setprice BUY hand 35");
        expect("/shopkeeper open", "/shopkeeper open");
        expect("/minecraft:give Player stone", "/minecraft:give Player stone");
        System.out.println(
                "Command case audit passed for player, console, and namespaced roots.");
    }

    private static void expect(String input, String expected) {
        String actual = CommandCaseNormalizer.normalize(input);
        if (!actual.equals(expected)) {
            throw new AssertionError(
                    "Expected '" + expected + "' but was '" + actual + "'.");
        }
    }
}
