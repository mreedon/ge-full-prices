package com.mreedon.gefullprices;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * When the offer in one GE slot started, kept per slot in the RuneScape profile
 * config so it survives logouts and restarts. The game never says when an offer
 * was placed, so the plugin has to see it happen: an offer first seen at login,
 * or already up when the plugin starts, gets an unknown start and no timer.
 * Times are epoch seconds.
 */
@AllArgsConstructor
@EqualsAndHashCode
@ToString
class SlotTimer
{
	static final long UNKNOWN = -1;
	static final long RUNNING = 0;

	// No fill for this long and the timer goes grey
	static final long STALE_AFTER = 30 * 60;

	static final int WHITE = 0xffffff;
	static final int GREY = 0x9f9f9f;
	static final int GREEN = 0x00b400;

	final int itemId;
	final boolean buy;
	final long price;
	final int total;
	final int sold;
	final long start;
	final long lastFill;
	final long end;

	/**
	 * @param live false while the client is replaying offers at login or hop, or
	 *             when the plugin snapshots offers it did not see placed; a
	 *             change seen then happened at an unknown time
	 */
	static SlotTimer update(SlotTimer prev, int itemId, boolean buy, long price, int total, int sold,
		boolean complete, boolean live, long now)
	{
		long at = live ? now : UNKNOWN;
		if (prev != null && prev.itemId == itemId && prev.buy == buy && prev.price == price && prev.total == total)
		{
			// A fill seen only in the login replay happened at an unknown time
			long lastFill = sold > prev.sold ? at : prev.lastFill;
			long end = complete && prev.end == RUNNING ? at : prev.end;
			return new SlotTimer(itemId, buy, price, total, Math.max(sold, prev.sold), prev.start, lastFill, end);
		}
		// A new offer, or a modified one: the timer restarts with the new price
		return new SlotTimer(itemId, buy, price, total, sold, at, at, complete ? at : RUNNING);
	}

	/**
	 * @param sinceLastFill count a running offer from its last fill instead of from
	 *                      when it was placed; a complete one shows its total time
	 * @return the elapsed time, or null when the start or the finish is unknown
	 */
	String elapsed(long now, boolean sinceLastFill)
	{
		if (start == UNKNOWN || end == UNKNOWN)
		{
			return null;
		}
		if (end != RUNNING)
		{
			return duration(end - start);
		}
		long from = sinceLastFill ? lastFill : start;
		return from == UNKNOWN ? null : duration(now - from);
	}

	int colour(long now)
	{
		if (end != RUNNING)
		{
			return GREEN;
		}
		return now - lastFill >= STALE_AFTER ? GREY : WHITE;
	}

	static String duration(long seconds)
	{
		long s = Math.max(0, seconds);
		if (s < 3600)
		{
			return String.format("%d:%02d", s / 60, s % 60);
		}
		if (s < 86400)
		{
			return String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
		}
		return String.format("%dd %dh", s / 86400, s / 3600 % 24);
	}

	String serialize()
	{
		return itemId + ":" + (buy ? 1 : 0) + ":" + price + ":" + total + ":" + sold + ":"
			+ start + ":" + lastFill + ":" + end;
	}

	static SlotTimer parse(String s)
	{
		if (s == null)
		{
			return null;
		}
		String[] f = s.split(":");
		if (f.length != 8)
		{
			return null;
		}
		try
		{
			return new SlotTimer(Integer.parseInt(f[0]), "1".equals(f[1]), Long.parseLong(f[2]),
				Integer.parseInt(f[3]), Integer.parseInt(f[4]), Long.parseLong(f[5]), Long.parseLong(f[6]),
				Long.parseLong(f[7]));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
