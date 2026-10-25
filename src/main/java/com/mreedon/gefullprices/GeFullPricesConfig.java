package com.mreedon.gefullprices;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GeFullPricesConfig.GROUP)
public interface GeFullPricesConfig extends Config
{
	String GROUP = "gefullprices";

	@ConfigItem(
		keyName = "exactPrices",
		name = "Exact prices",
		description = "Show the exact price on offer slots (791,005,403 coins) instead of the abbreviated 791m",
		position = 0
	)
	default boolean exactPrices()
	{
		return true;
	}

	@ConfigItem(
		keyName = "offerTimers",
		name = "Offer timers",
		description = "Show how long each offer has been up next to Buy/Sell on its slot. Grey after 30 minutes"
			+ " without a fill, green once complete. Offers placed on another device or while the plugin was"
			+ " off show no timer",
		position = 1
	)
	default boolean offerTimers()
	{
		return false;
	}

	enum TimerStart
	{
		OFFER_PLACED("Offer placed"),
		LAST_FILL("Last fill");

		private final String name;

		TimerStart(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	@ConfigItem(
		keyName = "timerStart",
		name = "Timer counts from",
		description = "Offer placed: how long the offer has been up. Last fill: how long since the offer last bought"
			+ " or sold any items, counting from when it was placed until the first fill. A fill that happened while"
			+ " you were logged out hides the timer until the next one. Completed offers show their total time either"
			+ " way",
		position = 2
	)
	default TimerStart timerStart()
	{
		return TimerStart.OFFER_PLACED;
	}
}
