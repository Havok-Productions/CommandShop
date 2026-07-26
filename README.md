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

## Recipe and price auditing

CommandShop writes two audit files in `plugins/CommandShop`:

- `recipes.yml` documents every recipe registered with Bukkit, including its
  key, implementation type, output, shape when available, ingredient choices,
  and whether Bukkit exposes enough information for a price audit. It is
  refreshed after startup and by `/commandshop reload` so recipes registered
  by other plugins are included.
- `price-history.yml` records the actor, timestamp, direction, material,
  previous price, and new price for every price set or removal command.

The audit recursively calculates the cheapest known acquisition cost from
direct buy prices and registered recipes. It warns online players with
`commandshop.notify` and the server console when either:

- an item can be bought and immediately sold for more; or
- a recipe's sellable output is worth more than all fully priced ingredients.

These alerts are notification-only. CommandShop does not change or remove any
configured price.

## Ratio-aware abuse flags

Sales are tracked per player and per material in a configurable rolling window.
By default, a player is flagged only when one material produces at least
`$5,000` within 30 minutes, the money earned is at least `2.0x` that item's
configured sell unit price, **and** its sell unit price is at least `1.10x`
the cheapest known direct-buy or recursive crafting cost.

For example, earning `$5,000` from beacons configured to sell for `$2,500`
produces a `2.0x` revenue-to-sale-price ratio. There is no fixed item-count
requirement. If no complete acquisition price is known, that material cannot
automatically flag a player. This prevents a single ordinary high-value sale
or high revenue without a suspicious profit margin from triggering the
detector.

A newly flagged player receives an explanation, staff with
`commandshop.notify` receive the evidence, and the flag is saved in `stats.db`.
The player cannot buy, sell, or open shop GUIs until an administrator runs:

```
/commandshop unflag <username>
```

Operators have `commandshop.notify` and `commandshop.abuse.bypass` by default.
Thresholds are configured under `Abuse_Detection` in `config.yml`.

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
