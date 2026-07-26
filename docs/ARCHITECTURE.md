# CommandShop architecture

CommandShop keeps its Bukkit entry point stable at
`me.thetealviper.commandshop.CommandShop`. Implementation code is grouped by
responsibility:

- `api` defines the stable contract consumed by Bukkit-facing adapters.
- `core` owns plugin lifecycle, service wiring, configuration, and implements
  the application contract.
- `commands` owns command completion and command-facing adapters.
- `gui` owns inventories, holders, navigation, and inventory event handling.
- `shop` owns domain rules such as automatic item classification.
- `model` contains immutable values shared between layers.
- `integrations` owns optional third-party adapters such as PlaceholderAPI.

Adapters depend on `api` and `model`, never on the concrete lifecycle class.
Optional integrations and GUI code must not own persistence. The root plugin
class remains intentionally empty so future internal refactors do not change
the Bukkit main-class name.
