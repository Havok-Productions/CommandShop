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

## Reloading and PlugManX

For routine `config.yml`, `messages.yml`, and `prices.db` changes, use:

```
/commandshop reload
```

CommandShop cleans up and restores its Bukkit command registrations during
dynamic disable/enable cycles. It can reclaim labels left by a disabled
`ScoreboardChatShop` or `CommandShop` instance without taking commands from an
enabled plugin.

Before `/plugman reload CommandShop`, make sure no player has a shop GUI open.
Folia cannot synchronously close inventories owned by players in other regions
during plugin disable. A full restart is still required after changing
dependencies, changing the plugin name, or replacing the legacy
`ScoreboardChatShop` jar.

## Official Discord 

https://discord.gg/aT9z7q7hX8

### Folia inquisitors

[<img src="https://github.com/Folia-Inquisitors.png" width=80 alt="Folia-Inquisitors">](https://github.com/orgs/Folia-Inquisitors/repositories)
[<img src="https://github.com/HSGamer.png" width=80 alt="HSGamer">](https://github.com/HSGamer)
