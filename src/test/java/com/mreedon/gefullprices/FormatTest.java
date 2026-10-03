package com.mreedon.gefullprices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FormatTest
{
	private static boolean abbreviated(String s)
	{
		return GeFullPricesPlugin.ABBREVIATED.matcher(s).matches();
	}

	@Test
	public void matchesEveryShapeTheGameProduces()
	{
		// from the 2026-09-30 slot screenshot, plus the k/b/t branches of the proc
		assertTrue(abbreviated("93.7m coins"));
		assertTrue(abbreviated("791m coins"));
		assertTrue(abbreviated("105m coins"));
		assertTrue(abbreviated("123.4k coins"));
		assertTrue(abbreviated("2.1b coins"));
		assertTrue(abbreviated("1,500t coins"));
	}

	@Test
	public void leavesExactAndOtherTextAlone()
	{
		assertFalse(abbreviated("7,809 coins"));
		assertFalse(abbreviated("1 coin"));
		assertFalse(abbreviated("791,005,403 coins"));
		assertFalse(abbreviated("Tumeken's shadow (uncharged)"));
		assertFalse(abbreviated("Empty"));
	}

	@Test
	public void formatsWithCommas()
	{
		assertEquals("791,005,403 coins", GeFullPricesPlugin.fullPrice(791_005_403L));
		assertEquals("3,000,000,000 coins", GeFullPricesPlugin.fullPrice(3_000_000_000L));
	}

	@Test
	public void shortensTheSuffixWhereTheSlotRunsOutOfRoom()
	{
		assertEquals("9,999,999,999 coins", GeFullPricesPlugin.fullPrice(9_999_999_999L));
		assertEquals("10,909,909,909 gp", GeFullPricesPlugin.fullPrice(10_909_909_909L));
		assertEquals("999,999,999,999 gp", GeFullPricesPlugin.fullPrice(999_999_999_999L));
		assertNull(GeFullPricesPlugin.fullPrice(1_000_000_000_000L));
	}
}
