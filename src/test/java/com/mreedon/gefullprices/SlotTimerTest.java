package com.mreedon.gefullprices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class SlotTimerTest
{
	private static final int WHIP = 4151;

	private static SlotTimer place(long now)
	{
		return SlotTimer.update(null, WHIP, true, 1_500_000, 10, 0, false, true, now);
	}

	@Test
	public void countsFromWhenTheOfferWasPlaced()
	{
		SlotTimer t = place(1000);
		assertEquals("0:00", t.elapsed(1000));
		assertEquals("12:34", t.elapsed(1000 + 754));
		assertEquals(SlotTimer.WHITE, t.colour(1000 + 754));
	}

	@Test
	public void formatsHoursAndDays()
	{
		assertEquals("59:59", SlotTimer.duration(3599));
		assertEquals("1:00:00", SlotTimer.duration(3600));
		assertEquals("23:59:59", SlotTimer.duration(86399));
		assertEquals("1d 0h", SlotTimer.duration(86400));
		assertEquals("3d 4h", SlotTimer.duration(3 * 86400 + 4 * 3600 + 59));
	}

	@Test
	public void goesGreyWithoutAFillAndAFillResetsIt()
	{
		SlotTimer t = place(0);
		assertEquals(SlotTimer.GREY, t.colour(SlotTimer.STALE_AFTER));
		t = SlotTimer.update(t, WHIP, true, 1_500_000, 10, 3, false, true, 1700);
		assertEquals(SlotTimer.WHITE, t.colour(1700 + SlotTimer.STALE_AFTER - 1));
		assertEquals("28:20", t.elapsed(1700));
	}

	@Test
	public void freezesGreenWhenComplete()
	{
		SlotTimer t = place(0);
		t = SlotTimer.update(t, WHIP, true, 1_500_000, 10, 10, true, true, 90);
		assertEquals("1:30", t.elapsed(5000));
		assertEquals(SlotTimer.GREEN, t.colour(5000));
		// the duplicate completion event the client sends must not move the end
		assertEquals(t, SlotTimer.update(t, WHIP, true, 1_500_000, 10, 10, true, true, 200));
	}

	@Test
	public void offersFirstSeenAtLoginHaveNoTimer()
	{
		SlotTimer t = SlotTimer.update(null, WHIP, true, 1_500_000, 10, 4, false, false, 1000);
		assertNull(t.elapsed(2000));
		// and a later live fill does not invent a start
		t = SlotTimer.update(t, WHIP, true, 1_500_000, 10, 6, false, true, 3000);
		assertNull(t.elapsed(4000));
	}

	@Test
	public void aRecordedOfferSurvivesTheLoginReplay()
	{
		SlotTimer t = place(1000);
		t = SlotTimer.update(t, WHIP, true, 1_500_000, 10, 0, false, false, 9000);
		assertEquals("2:13:20", t.elapsed(9000));
	}

	@Test
	public void completedWhileLoggedOutHasNoTimer()
	{
		SlotTimer t = place(1000);
		t = SlotTimer.update(t, WHIP, true, 1_500_000, 10, 10, true, false, 9000);
		assertNull(t.elapsed(9000));
	}

	@Test
	public void modifyingThePriceRestartsTheTimer()
	{
		SlotTimer t = place(1000);
		t = SlotTimer.update(t, WHIP, true, 1_600_000, 10, 2, false, true, 5000);
		assertEquals("0:10", t.elapsed(5010));
	}

	@Test
	public void roundTripsThroughConfig()
	{
		SlotTimer t = SlotTimer.update(place(1000), WHIP, true, 1_500_000, 10, 10, true, true, 1090);
		assertEquals(t, SlotTimer.parse(t.serialize()));
		assertNull(SlotTimer.parse("garbage"));
		assertNull(SlotTimer.parse(null));
	}
}
