package com.mreedon.gefullprices;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Since the 2026-09-30 max cash update the Grand Exchange offer slots print the
 * price per item abbreviated ("791m coins"). The slot is drawn by the game script
 * ge_offers_index_drawslot, which formats the price with a short-number proc. This
 * plugin waits for that script to finish and rewrites the price line with the
 * exact value from the offer ("791,005,403 coins").
 */
@Slf4j
@PluginDescriptor(
	name = "GE Full Prices",
	description = "Shows the exact price on Grand Exchange offer slots instead of the abbreviated 791m",
	tags = {"grand exchange", "ge", "price", "coins", "exact", "abbreviated", "offer"}
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

	@Override
	protected void startUp()
	{
		clientThread.invokeLater(this::rewriteSlots);
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

	private void rewriteSlots()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		for (int slot = 0; slot < SLOTS; slot++)
		{
			Widget slotWidget = client.getWidget(InterfaceID.GeOffers.INDEX_0 + slot);
			if (slotWidget == null || slotWidget.isHidden())
			{
				continue;
			}

			long price = slotPrice(offers, slot);
			String full = price > 0 ? fullPrice(price) : null;
			if (full == null)
			{
				continue;
			}

			Widget[] children = slotWidget.getDynamicChildren();
			if (children == null)
			{
				continue;
			}
			for (Widget child : children)
			{
				String text = child.getText();
				if (text != null && ABBREVIATED.matcher(text).matches())
				{
					child.setText(full);
				}
			}
		}
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
