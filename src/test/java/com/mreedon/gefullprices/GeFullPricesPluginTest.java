package com.mreedon.gefullprices;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GeFullPricesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GeFullPricesPlugin.class);
		RuneLite.main(args);
	}
}
