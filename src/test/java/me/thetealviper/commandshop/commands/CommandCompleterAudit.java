package me.thetealviper.commandshop.commands;

import java.lang.reflect.Proxy;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import me.thetealviper.commandshop.api.CommandShopApi;

/**
 * Regression audit for unresolved item names in amount tab completion.
 */
public final class CommandCompleterAudit {
    private CommandCompleterAudit() {
    }

    public static void main(String[] args) {
        CommandShopApi api = (CommandShopApi) Proxy.newProxyInstance(
                CommandShopApi.class.getClassLoader(),
                new Class<?>[] {CommandShopApi.class},
                (proxy, method, methodArgs) -> {
                    if (method.getName().equals("resolveMaterial")) {
                        return null;
                    }
                    if (method.getName().equals("getBuyPrice")
                            || method.getName().equals("getSellPrice")) {
                        throw new AssertionError(
                                "An unresolved material must not reach a price lookup.");
                    }
                    return defaultValue(method.getReturnType());
                });
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[] {CommandSender.class},
                (proxy, method, methodArgs) -> defaultValue(method.getReturnType()));
        Command buy = new Command("buy") {
            @Override
            public boolean execute(CommandSender commandSender, String label,
                    String[] commandArgs) {
                return false;
            }
        };

        List<String> suggestions = new CommandCompleter(api).onTabComplete(
                sender, buy, "buy", new String[] {"iron", ""});
        if (!suggestions.isEmpty()) {
            throw new AssertionError(
                    "An unresolved /buy item must return no amount suggestions.");
        }
        System.out.println(
                "Command completer audit passed for unresolved /buy iron amount completion.");
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }
}
