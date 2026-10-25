package com.mreedon.gefullprices;

import com.google.inject.Provides;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Since the 2026-09-30 max cash update the Grand Exchange offer slots print the
 * price per item abbreviated ("791m coins"). The slot is drawn by the game script
 * ge_offers_index_drawslot, which formats the price with a short-number proc. This
 * plugin waits for that script to finish and rewrites the price line with the
 * exact value from the offer ("791,005,403 coins"). Optionally it also adds a
 * timer to each slot's Buy/Sell header, see {@link SlotTimer}.
 */
@Slf4j
@PluginDescriptor(
	name = "GE Full Prices",
	description = "Shows the exact price on Grand Exchange offer slots instead of the abbreviated 791m, and optional offer timers",
	tags = {"grand exchange", "ge", "price", "coins", "exact", "abbreviated", "offer", "timer", "slot"}
)
public class GeFullPricesPlugin extends Plugin
{
	// [clientscript,ge_offers_index_drawslot], the per-slot redraw on offer/inv/var change
	static final int GE_OFFERS_INDEX_DRAWSLOT = 797;
	// [clientscript,ge_offers_init], which draws every slot on open via the proc, not 797
	static final int GE_OFFERS_INIT = 803;

	private static final int SLOTS = 8;

	// What the game's short-number proc produces: 123.4k, 12m, 1.2b, 3t, with commas above 999
	static final Pattern ABBREVIATED = Pattern.compile("^\\d{1,3}(,\\d{3})*(\\.\\d)?[kmbt] coins$");

	private static final int[] ITEMSINK_PRICE = {
		VarPlayerID.GE_ITEMSINK_PRICE_LONG_0, VarPlayerID.GE_ITEMSINK_PRICE_LONG_1,
		VarPlayerID.GE_ITEMSINK_PRICE_LONG_2, VarPlayerID.GE_ITEMSINK_PRICE_LONG_3,
		VarPlayerID.GE_ITEMSINK_PRICE_LONG_4, VarPlayerID.GE_ITEMSINK_PRICE_LONG_5,
		VarPlayerID.GE_ITEMSINK_PRICE_LONG_6, VarPlayerID.GE_ITEMSINK_PRICE_LONG_7,
	};

	private static final int[] ITEMSINK_OBJ = {
		VarPlayerID.GE_ITEMSINK_OBJ_0, VarPlayerID.GE_ITEMSINK_OBJ_1,
		VarPlayerID.GE_ITEMSINK_OBJ_2, VarPlayerID.GE_ITEMSINK_OBJ_3,
		VarPlayerID.GE_ITEMSINK_OBJ_4, VarPlayerID.GE_ITEMSINK_OBJ_5,
		VarPlayerID.GE_ITEMSINK_OBJ_6, VarPlayerID.GE_ITEMSINK_OBJ_7,
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private GeFullPricesConfig config;

	// Offers the client replays at login or hop changed at an unknown time. That
	// lasts until the game state is LOGGED_IN plus a couple of ticks.
	private boolean replaying;
	private int loggedInTick = -1;
	private static final int REPLAY_TICKS = 2;

	@Provides
	GeFullPricesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GeFullPricesConfig.class);
	}

	@Override
	protected void startUp()
	{
		replaying = false;
		loggedInTick = -1;
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				snapshotOffers();
			}
			else
			{
				replaying = true;
			}
			rewriteSlots();
		});
	}

	@Override
	protected void shutDown()
	{
		clientThread.invokeLater(this::clearTimers);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		int id = event.getScriptId();
		if (id == GE_OFFERS_INDEX_DRAWSLOT || id == GE_OFFERS_INIT)
		{
			rewriteSlots();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGING_IN:
			case HOPPING:
			case CONNECTION_LOST:
				replaying = true;
				loggedInTick = -1;
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (replaying && client.getGameState() == GameState.LOGGED_IN)
		{
			if (loggedInTick < 0)
			{
				loggedInTick = client.getTickCount();
			}
			else if (client.getTickCount() - loggedInTick >= REPLAY_TICKS)
			{
				replaying = false;
			}
		}
		if (config.offerTimers())
		{
			drawTimers();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!GeFullPricesConfig.GROUP.equals(event.getGroup()) || !"offerTimers".equals(event.getKey()))
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (config.offerTimers())
			{
				drawTimers();
			}
			else
			{
				clearTimers();
			}
		});
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		if (offer.getState() == GrandExchangeOfferState.EMPTY)
		{
			// The client clears every slot while logging in or hopping; only a
			// logged-in empty is a real collect
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				saveTimer(event.getSlot(), null);
			}
			return;
		}
		boolean live = !replaying && client.getGameState() == GameState.LOGGED_IN;
		trackOffer(event.getSlot(), offer, live);
	}

	// Offers already up when the plugin starts were not seen placed: keep a record
	// that still matches, otherwise remember them with an unknown start
	private void snapshotOffers()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}
		for (int slot = 0; slot < SLOTS && slot < offers.length; slot++)
		{
			GrandExchangeOffer offer = offers[slot];
			if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
			{
				saveTimer(slot, null);
			}
			else
			{
				trackOffer(slot, offer, false);
			}
		}
	}

	private void trackOffer(int slot, GrandExchangeOffer offer, boolean live)
	{
		GrandExchangeOfferState state = offer.getState();
		boolean complete = state != GrandExchangeOfferState.BUYING && state != GrandExchangeOfferState.SELLING;
		SlotTimer prev = loadTimer(slot);
		SlotTimer next = SlotTimer.update(prev, offer.getItemId(), isBuy(state), offer.getPrice(),
			offer.getTotalQuantity(), offer.getQuantitySold(), complete, live, Instant.now().getEpochSecond());
		if (!next.equals(prev))
		{
			log.debug("slot {} timer {} -> {}", slot, prev, next);
			saveTimer(slot, next);
		}
	}

	private SlotTimer loadTimer(int slot)
	{
		return SlotTimer.parse(configManager.getRSProfileConfiguration(GeFullPricesConfig.GROUP, timerKey(slot)));
	}

	private void saveTimer(int slot, SlotTimer timer)
	{
		if (timer == null)
		{
			configManager.unsetRSProfileConfiguration(GeFullPricesConfig.GROUP, timerKey(slot));
		}
		else
		{
			configManager.setRSProfileConfiguration(GeFullPricesConfig.GROUP, timerKey(slot), timer.serialize());
		}
	}

	private static String timerKey(int slot)
	{
		return "timer." + slot;
	}

	private static boolean isBuy(GrandExchangeOfferState state)
	{
		return state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.CANCELLED_BUY;
	}

	private void rewriteSlots()
	{
		if (config.exactPrices())
		{
			rewritePrices();
		}
		if (config.offerTimers())
		{
			drawTimers();
		}
	}

	private void rewritePrices()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		for (int slot = 0; slot < SLOTS; slot++)
		{
			Widget slotWidget = visibleSlot(slot);
			if (slotWidget == null)
			{
				continue;
			}

			long price = slotPrice(offers, slot);
			String full = price > 0 ? fullPrice(price) : null;
			if (full == null)
			{
				continue;
			}

			for (Widget child : slotWidget.getDynamicChildren())
			{
				String text = child.getText();
				if (text != null && ABBREVIATED.matcher(text).matches())
				{
					child.setText(full);
				}
			}
		}
	}

	// Writes "Buy 12:34" into the slot header for every live offer with a known start
	private void drawTimers()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}
		long now = Instant.now().getEpochSecond();
		boolean sinceLastFill = config.timerStart() == GeFullPricesConfig.TimerStart.LAST_FILL;
		for (int slot = 0; slot < SLOTS && slot < offers.length; slot++)
		{
			Widget header = header(slot);
			GrandExchangeOffer offer = offers[slot];
			if (header == null || offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
			{
				continue;
			}
			String label = isBuy(offer.getState()) ? "Buy" : "Sell";
			SlotTimer timer = loadTimer(slot);
			String elapsed = timer != null && timer.itemId == offer.getItemId() ? timer.elapsed(now, sinceLastFill) : null;
			String text = elapsed == null ? label
				: String.format("%s <col=%06x>%s</col>", label, timer.colour(now), elapsed);
			if (!text.equals(header.getText()))
			{
				header.setText(text);
			}
		}
	}

	// Puts the plain Buy/Sell header back when timers are turned off
	private void clearTimers()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		for (int slot = 0; offers != null && slot < SLOTS && slot < offers.length; slot++)
		{
			Widget header = header(slot);
			GrandExchangeOffer offer = offers[slot];
			if (header != null && offer != null && offer.getState() != GrandExchangeOfferState.EMPTY)
			{
				header.setText(isBuy(offer.getState()) ? "Buy" : "Sell");
			}
		}
	}

	private Widget visibleSlot(int slot)
	{
		Widget slotWidget = client.getWidget(InterfaceID.GeOffers.INDEX_0 + slot);
		if (slotWidget == null || slotWidget.isHidden() || slotWidget.getDynamicChildren() == null)
		{
			return null;
		}
		return slotWidget;
	}

	// ge_offers_index_initslot creates the header ("Buy", "Sell", "Empty") as the
	// slot's first text child, ahead of the item name and price
	private Widget header(int slot)
	{
		Widget slotWidget = visibleSlot(slot);
		if (slotWidget == null)
		{
			return null;
		}
		for (Widget child : slotWidget.getDynamicChildren())
		{
			if (child != null && child.getType() == WidgetType.TEXT)
			{
				return child;
			}
		}
		return null;
	}

	// Mirrors the script: a live offer uses its own price; an empty slot holding an
	// item-sink sale reads the per-slot sink price varp.
	private long slotPrice(GrandExchangeOffer[] offers, int slot)
	{
		if (offers != null && slot < offers.length && offers[slot] != null
			&& offers[slot].getState() != GrandExchangeOfferState.EMPTY)
		{
			return offers[slot].getPrice();
		}
		if (client.getVarpValue(ITEMSINK_OBJ[slot]) <= 0)
		{
			return 0;
		}
		// The script reads these as long, but the client may type them as int varps
		try
		{
			return client.getVarpLongValue(ITEMSINK_PRICE[slot]);
		}
		catch (IllegalArgumentException e)
		{
			return client.getVarpValue(ITEMSINK_PRICE[slot]);
		}
	}

	// The slot is about 20 characters wide: "10,909,909,909 coins" already clips, so
	// 10b+ says "gp" instead, and 1t+ ("1,000,000,000,000 gp") keeps the game's text
	private static final long GP_SUFFIX_FROM = 10_000_000_000L;
	private static final long FULL_PRICE_BELOW = 1_000_000_000_000L;

	/**
	 * @return the slot's full price text, or null to leave the game's abbreviation
	 */
	static String fullPrice(long price)
	{
		if (price >= FULL_PRICE_BELOW)
		{
			return null;
		}
		String digits = NumberFormat.getIntegerInstance(Locale.US).format(price);
		return digits + (price >= GP_SUFFIX_FROM ? " gp" : " coins");
	}
}
