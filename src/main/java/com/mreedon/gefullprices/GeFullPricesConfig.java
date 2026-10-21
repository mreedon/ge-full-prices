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
}
