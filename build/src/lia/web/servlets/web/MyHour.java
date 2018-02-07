package lia.web.servlets.web;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.jfree.data.time.Day;
import org.jfree.data.time.Hour;
import org.jfree.data.time.RegularTimePeriod;

/**
 * @author costing
 *
 */
public class MyHour extends Hour {
	private static final long	serialVersionUID	= 1L;

	private Day					day;

	private int					hour;

	private int					increment;

	/**
	 * @param iHour
	 * @param iDay
	 * @param iIncrement
	 */
	public MyHour(final int iHour, final Day iDay, int iIncrement) {
		hour = iHour;
		day = iDay;

		increment = iIncrement;
	}

	/**
	 * @param time
	 * @param zone
	 * @param iIncrement
	 */
	@SuppressWarnings("deprecation")
	public MyHour(final Date time, final TimeZone zone, int iIncrement) {
		final Calendar calendar = Calendar.getInstance(zone);
		calendar.setTime(time);
		final int iHour = calendar.get(Calendar.HOUR_OF_DAY);
		increment = iIncrement;

		hour = iHour - (iIncrement > 1 ? iHour % iIncrement : 0);
		day = new Day(time, zone);
	}

	/**
	 * @return first hour
	 */
	protected int getFirstHour() {
		return 0;
	}

	/**
	 * @return last hour
	 */
	protected int getLastHour() {
		return 24 - increment;
	}

	@Override
	public RegularTimePeriod previous() {
		final MyHour result;
		if (hour != getFirstHour()) {
			result = new MyHour(hour - increment, day, increment);
		} else {
			final Day prevDay = (Day) day.previous();
			if (prevDay != null) {
				result = new MyHour(getLastHour(), prevDay, increment);
			} else {
				result = null;
			}
		}
		return result;
	}

	@Override
	public RegularTimePeriod next() {
		final MyHour result;
		if (hour != getLastHour()) {
			result = new MyHour(hour + increment, day, increment);
		} else {
			final Day nextDay = (Day) day.next();
			if (nextDay != null) {
				result = new MyHour(getFirstHour(), nextDay, increment);
			} else {
				result = null;
			}
		}
		return result;
	}

	@Override
	public long getFirstMillisecond(final Calendar calendar) {

		final int year = day.getYear();
		final int month = day.getMonth() - 1;
		final int iDay = day.getDayOfMonth();

		calendar.set(year, month, iDay, hour, 0, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		return calendar.getTime().getTime();

	}

	@Override
	public long getLastMillisecond(final Calendar calendar) {

		final int year = day.getYear();
		final int month = day.getMonth() - 1;
		final int iDay = day.getDayOfMonth();

		calendar.set(year, month, iDay, hour + increment - 1, 59, 59);
		calendar.set(Calendar.MILLISECOND, 999);

		return calendar.getTime().getTime();

	}

	@Override
	public String toString() {
		return day.toString() + " " + hour + ":00 (" + increment + ")";
	}

	@Override
	public int getHour() {
		return hour;
	}

	@Override
	public Day getDay() {
		return day;
	}

	@Override
	public int getYear() {
		return day.getYear();
	}

	@Override
	public int getMonth() {
		return day.getMonth();
	}

	@Override
	public int getDayOfMonth() {
		return day.getDayOfMonth();
	}

	@Override
	public long getSerialIndex() {
		return day.getSerialIndex() * 24L + hour;
	}

	@Override
	public boolean equals(final Object object) {
		if (object instanceof Hour) {
			final Hour h = (Hour) object;
			return ((hour == h.getHour()) && (day.equals(h.getDay())));
		}
		return false;
	}

	@Override
	public int hashCode() {
		int result = 17;
		result = 37 * result + hour;
		result = 37 * result + day.hashCode();
		return result;
	}

	@Override
	public int compareTo(final Object o1) {
		int result;

		if (o1 instanceof Hour) {
			final Hour h = (Hour) o1;
			result = getDay().compareTo(h.getDay());
			if (result == 0) {
				result = hour - h.getHour();
			}
		} else if (o1 instanceof RegularTimePeriod) {
			result = 0;
		} else {
			result = 1;
		}

		return result;

	}

}
