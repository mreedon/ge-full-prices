# GE Full Prices

Since the September 30, 2026 max cash update, the Grand Exchange offer slots abbreviate the price: `791m coins`, `93.7m coins`, `123.4k coins`. This plugin puts the exact number back: `791,005,403 coins`.

It only touches the price line on the eight offer slots. Prices under 100,000 were never abbreviated and are left alone. From 10 billion the label ends in `gp` instead of `coins` so it still fits the slot, and prices of 1 trillion or more keep the game's short form, since they cannot fit with commas. The plugin also leaves alone the offer details and history screens, which still show the exact price.

## How it works

The game draws each slot with the `ge_offers_index_drawslot` script (797) and, when the interface opens, with `ge_offers_init` (803). After either one runs, the plugin finds the slot's abbreviated price text and rewrites it with the offer's exact price per item. There is no config and no network access.
