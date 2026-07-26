# CommandShop

## Description 

A shop plugin that allows commands to be used, and supports an organized GUI.

## Building instructions

mvn clean install

## Architecture

See `docs/ARCHITECTURE.md` for package ownership and dependency rules.

## Automatic item categories

The buy GUI classifies every available Bukkit material. Food, crops, seeds,
ores, mineral resources, and mineral storage blocks have dedicated categories.
All other placeable blocks (including mud bricks and cut copper variants) and
common crafting ingredients are Materials. Tools, armor, vehicles, utility
items, and other functional leftovers appear in Other. `Category_Overrides` in
`config.yml` always takes priority over automatic classification.

## Removing shop prices

Administrators can remove one or both price directions:

```
/buy remove <item>
/sell remove <item>
/shop remove <item>
/commandshop delete <item>
```

`/shop remove <item>` and `/commandshop delete <item>` always clear both the
buy and sell entries and confirm that the item can no longer be bought or sold.

## Reloading and PlugManX

For routine `config.yml`, `messages.yml`, and `prices.db` changes, use:

```
/commandshop reload
```

CommandShop supports PlugManX's Bukkit-plugin unload/load sequence. During a
PlugManX unload, PlugManX disables CommandShop, removes its Bukkit command
registrations, and then synchronizes Paper's command dispatcher. During a load,
PlugManX registers the new commands and performs a delayed dispatcher sync on
Folia. CommandShop deliberately does not reflect into the command map or call
`syncCommands()` from `onEnable()` or `onDisable()`, preventing concurrent
command-tree modification while Paper builds player command suggestions.

The supported dynamic commands are:

```
/plugman unload CommandShop
/plugman load CommandShop
/plugman reload CommandShop
```

Before `/plugman reload CommandShop`, make sure no player has a shop GUI open.
Folia cannot synchronously close inventories owned by players in other regions
during plugin disable. Use CommandShop `27.1.3-folia` or newer; older builds
performed their own command-map cleanup and could race Paper's asynchronous
command builder. A full restart is still required after changing dependencies,
changing the plugin name, or replacing the legacy `ScoreboardChatShop` jar.

## Official Discord 

https://discord.gg/aT9z7q7hX8

### Folia inquisitors

[<img src="https://github.com/Folia-Inquisitors.png" width=80 alt="Folia-Inquisitors">](https://github.com/orgs/Folia-Inquisitors/repositories)
[<img src="https://github.com/HSGamer.png" width=80 alt="HSGamer">](https://github.com/HSGamer)
