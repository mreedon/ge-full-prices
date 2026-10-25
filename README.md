# GE Full Prices

Since the September 30, 2026 max cash update, the Grand Exchange offer slots abbreviate the price: `791m coins`, `93.7m coins`, `123.4k coins`. This plugin puts the exact number back: `791,005,403 coins`.

It only touches the price line on the eight offer slots. Prices under 100,000 were never abbreviated and are left alone. From 10 billion the label ends in `gp` instead of `coins` so it still fits the slot, and prices of 1 trillion or more keep the game's short form, since they cannot fit with commas. The plugin also leaves alone the offer details and history screens, which still show the exact price.

## Offer timers (off by default)

Turn on **Offer timers** in the plugin's settings to show how long each offer has been up, next to Buy or Sell at the top of the slot: `Buy 12:34`. The time is white while the offer is filling, grey after 30 minutes without a fill, and green, stopped, once the offer completes or is cancelled. Changing an offer's price restarts its timer.

Set **Timer counts from** to **Last fill** to see how long it has been since a partly filled offer last bought or sold anything, instead of how long it has been up. A slow offer that just started filling then reads `0:00` rather than two hours. Until the first fill it counts from when the offer was placed, and a completed offer still shows its total time. If the last fill happened while you were logged out, that slot shows no timer until its next fill.

The game never says when an offer was placed, so the plugin has to see it happen. Offers placed on mobile, on another computer, or while the plugin was off show no timer until that slot gets a new offer. The same goes for an offer that completes while you are logged out. Start times are saved with your RuneLite profile, so timers survive logging out and restarting.

The slot timer idea comes from [Flipping Utilities](https://github.com/Flipping-Utilities/rl-plugin), which has one alongside many other flipping tools. This plugin is for players who only want the timer.

## Settings

| Setting | Default | |
|---|---|---|
| Exact prices | On | The exact price on each slot. Turning it off takes effect the next time the Grand Exchange redraws the slot. |
| Offer timers | Off | The timer described above. |
| Timer counts from | Offer placed | Offer placed, or Last fill to count from the most recent partial fill. |

## How it works

The game draws each slot with the `ge_offers_index_drawslot` script (797) and, when the interface opens, with `ge_offers_init` (803). After either one runs, the plugin finds the slot's abbreviated price text and rewrites it with the offer's exact price per item. The timer is written into the slot's header text after the same scripts and on each game tick while the Grand Exchange is open. The plugin makes no network requests and reads or writes no files.
