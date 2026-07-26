package me.thetealviper.commandshop.platform;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import me.thetealviper.commandshop.api.CommandShopApi;

/**
 * Repairs command-map entries left behind by dynamic plugin managers.
 *
 * Bukkit does not expose the complete command registry on every supported
 * version, so this class uses guarded reflection and fails closed. It only
 * replaces commands owned by a disabled CommandShop/ScoreboardChatShop
 * instance and will never take a command from an enabled plugin.
 */
public final class DynamicReloadSupport {
    private DynamicReloadSupport() {
    }

    public static void restoreCommands(CommandShopApi plugin) {
        CommandMap commandMap = commandMap(plugin);
        Map<String, Command> knownCommands = knownCommands(plugin, commandMap);
        if (commandMap == null || knownCommands == null) {
            plugin.getLogger().warning("Dynamic command-map access is unavailable; "
                    + "PlugMan command recovery was skipped.");
            return;
        }

        int restored = 0;
        for (String commandName : plugin.getDescription().getCommands().keySet()) {
            PluginCommand ownCommand = plugin.commandForReload(commandName);
            if (ownCommand == null) {
                continue;
            }

            Command activeCommand = knownCommands.get(commandName.toLowerCase(Locale.ROOT));
            if (activeCommand == ownCommand) {
                continue;
            }
            if (activeCommand != null && !isDisabledShopCommand(activeCommand)) {
                plugin.getLogger().warning("Command /" + commandName + " remains owned by "
                        + activeCommand.getClass().getSimpleName()
                        + "; use /commandshop:" + commandName + " or resolve the conflict.");
                continue;
            }

            if (activeCommand != null) {
                removeMappings(knownCommands, activeCommand);
                activeCommand.unregister(commandMap);
            }
            removeMappings(knownCommands, ownCommand);
            ownCommand.unregister(commandMap);
            if (commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), ownCommand)) {
                restored++;
            }
        }

        if (restored > 0) {
            syncCommands(plugin);
            plugin.getLogger().info("Recovered " + restored
                    + " command registration(s) after a dynamic reload.");
        }
    }

    public static void unregisterCommands(CommandShopApi plugin) {
        CommandMap commandMap = commandMap(plugin);
        Map<String, Command> knownCommands = knownCommands(plugin, commandMap);
        if (commandMap == null || knownCommands == null) {
            return;
        }

        List<Command> ownedCommands = new ArrayList<>(plugin.rememberedCommands());
        for (Command command : knownCommands.values()) {
            if (command instanceof PluginCommand
                    && ((PluginCommand) command).getPlugin() == plugin
                    && !ownedCommands.contains(command)) {
                ownedCommands.add(command);
            }
        }
        for (Command command : ownedCommands) {
            removeMappings(knownCommands, command);
            command.unregister(commandMap);
        }
        if (!ownedCommands.isEmpty()) {
            syncCommands(plugin);
        }
    }

    private static boolean isDisabledShopCommand(Command command) {
        if (!(command instanceof PluginCommand)) {
            return false;
        }
        Plugin owner = ((PluginCommand) command).getPlugin();
        String ownerName = owner.getName();
        return !owner.isEnabled()
                && (ownerName.equalsIgnoreCase("CommandShop")
                || ownerName.equalsIgnoreCase("ScoreboardChatShop"));
    }

    private static void removeMappings(Map<String, Command> knownCommands, Command target) {
        knownCommands.entrySet().removeIf(entry -> entry.getValue() == target);
    }

    private static CommandMap commandMap(CommandShopApi plugin) {
        try {
            Method method = plugin.getServer().getClass().getMethod("getCommandMap");
            Object value = method.invoke(plugin.getServer());
            return value instanceof CommandMap ? (CommandMap) value : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Could not access the server command map: "
                    + exception.getClass().getSimpleName());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> knownCommands(CommandShopApi plugin, CommandMap commandMap) {
        if (commandMap == null) {
            return null;
        }
        try {
            Method method = commandMap.getClass().getMethod("getKnownCommands");
            Object value = method.invoke(commandMap);
            if (value instanceof Map) {
                return (Map<String, Command>) value;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall through to older Bukkit implementations.
        }

        Class<?> type = commandMap.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("knownCommands");
                field.setAccessible(true);
                Object value = field.get(commandMap);
                return value instanceof Map ? (Map<String, Command>) value : null;
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().warning("Could not access registered commands: "
                        + exception.getClass().getSimpleName());
                return null;
            }
        }
        return null;
    }

    private static void syncCommands(CommandShopApi plugin) {
        try {
            Method method = plugin.getServer().getClass().getMethod("syncCommands");
            method.invoke(plugin.getServer());
        } catch (NoSuchMethodException ignored) {
            // Older servers update their command map without a dispatcher sync.
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Could not synchronize the client command tree: "
                    + exception.getClass().getSimpleName());
        }
    }
}
