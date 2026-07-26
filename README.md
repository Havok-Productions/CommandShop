# CommandShop

CommandShop is a Vault economy shop plugin for Folia and Paper servers running
Java 17 or newer.

Author: `yomamaeatstoes`

## GUI commands

- `/shop-gui` opens the Buy/Sell landing menu.
- `/buy-gui` opens Food & Crops, Materials, Ores, Other, and Recent Purchases.
- `/sell-gui` opens the drag-to-sell tray and Sell All Inventory option.
- `/buy`, `/sell`, `/shop`, and `/commandshop` open the corresponding GUI when
  used without arguments.

Material groups are generated from `Material_Groups` in `config.yml`. A group is
only shown when at least one matching item has an active buy price. Any active
buy item that is not categorized is shown under Other.

## Price administration

```
/setprice buy oak_planks 5 16
/setprice sell oak_planks 2.50 16
/setprice buy oak_planks 0
```

The first command makes 16 oak planks cost $5.00. Setting a price to zero removes
that side of the price.

Legacy text commands remain available:

```
/buy oak_planks 32
/sell oak_planks max
/price oak_planks 16
```

## Inspection

Operators with `commandshop.inspect` can use:

```
/commandshop inspect <username>
```

The result ranks the five sale items that earned the most money and the five
purchase items that cost the most money.

## Upgrade behavior

On the first start under the new `CommandShop` plugin name, the plugin copies
`prices.db`, `stats.db`, `config.yml`, and `messages.yml` from an existing
`plugins/ScoreboardChatShop` folder. The original folder remains untouched.

Both `%commandshop_...%` and legacy `%scoreboardchatshop_...%` PlaceholderAPI
identifiers are registered when PlaceholderAPI is installed.
