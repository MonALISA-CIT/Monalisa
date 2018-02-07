package lia.web.servlets.web;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.Second;

/**
 * @author costing
 *
 */
public class MySecond extends RegularTimePeriod implements Serializable {
	private static final long	serialVersionUID	= 1L;

	private final long			lSecond;

	private final long			compactInterval;

	private final TimeZone		tz;

	private final Second		sec;

	/**
	 * @param second
	 * @param zone
	 * @param lCompactInterval
	 */
	@SuppressWarnings("deprecation")
	public MySecond(final long second, final TimeZone zone, final long lCompactInterval) {
		lSecond = second;
		compactInterval = lCompactInterval > 0 ? lCompactInterval : 1;

		sec = new Second(new Date(second * 1000), zone);

		tz = zone;
	}

	/**
	 * @param time
	 * @param zone
	 * @param lCompactInterval
	 */
	@SuppressWarnings("deprecation")
	public MySecond(final Date time, final TimeZone zone, final long lCompactInterval) {
		compactInterval = lCompactInterval > 0 ? lCompactInterval : 1;

		final Calendar calendar = Calendar.getInstance(zone);
		calendar.setTime(time);

		lSecond = ((calendar.getTimeInMillis() / (compactInterval)) * compactInterval) / 1000;

		sec = new Second(new Date(lSecond * 1000), zone);

		tz = zone;
	}

	@Override
	public final RegularTimePeriod previous() {
		return new MySecond(lSecond - compactInterval, tz, compactInterval);
	}

	@Override
	public final RegularTimePeriod next() {
		return new MySecond(lSecond + compactInterval, tz, compactInterval);
	}

	@Override
	public final long getSerialIndex() {
		return lSecond / compactInterval;
	}

	@Override
	public long getFirstMillisecond(final Calendar calendar) {
		return sec.getFirstMillisecond(calendar);
	}

	@Override
	public long getLastMillisecond(final Calendar calendar) {
		return sec.getFirstMillisecond(calendar) + compactInterval - 1;
	}

	@Override
	public String toString() {
		return lSecond + "s (" + compactInterval + ")";
	}

	@Override
	public boolean equals(final Object object) {
		final MySecond ms = (MySecond) object;
	
		return ms!=null && (lSecond == ms.lSecond);
	}

	@Override
	public int hashCode() {
		int result = 17;
		result = 37 * result + (int) (lSecond / compactInterval);
		return result;
	}

	@Override
	public int compareTo(final Object o1) {
		final RegularTimePeriod rtp = (RegularTimePeriod) o1;

		long lDiff = getMiddleMillisecond() - rtp.getMiddleMillisecond();

		if (lDiff < 0)
			return -1;
		if (lDiff > 0)
			return 1;
		return 0;
	}

	@Override
	public long getFirstMillisecond() {
		return sec.getFirstMillisecond();
	}

	@Override
	public long getLastMillisecond() {
		return sec.getFirstMillisecond() + compactInterval - 1;
	}

	@Override
	public void peg(Calendar arg0) {
		sec.peg(arg0);
	}

}
