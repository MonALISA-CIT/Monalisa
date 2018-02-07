package lia.web.servlets.web;

import java.io.Serializable;

/**
 * @author costing
 *
 */
public class MyStringComparator implements java.util.Comparator<String>,Serializable {
	private static final long	serialVersionUID	= 1L;

	private String	delimiters;

	private boolean	isInsensitive;

	/**
	 * 
	 */
	public MyStringComparator() {
		this(" ._-");
	}

	/**
	 * @param sDelimiters
	 */
	public MyStringComparator(final String sDelimiters) {
		this(sDelimiters, false);
	}

	/**
	 * @param sDelimiters
	 * @param bInsensitive
	 */
	public MyStringComparator(final String sDelimiters, final boolean bInsensitive) {
		delimiters = sDelimiters;
		isInsensitive = bInsensitive;
	}

	@Override
	public final int compare(final String _s1, final String _s2) {
			String s1 = _s1;
			String s2 = _s2;
		
			if (isInsensitive) {
				s1 = s1.toLowerCase();
				s2 = s2.toLowerCase();
			}

			if (delimiters != null && delimiters.length() > 0) {
				s1 = lia.web.utils.ServletExtension.getStringSuffix(s1, delimiters);
				s2 = lia.web.utils.ServletExtension.getStringSuffix(s2, delimiters);

				if (s1.equals(s2))
					return lia.web.utils.ServletExtension.getStringPrefix(s1, delimiters).compareTo(lia.web.utils.ServletExtension.getStringPrefix(s2, delimiters));
			}

			return s1.compareTo(s2);
	}

	@Override
	public final boolean equals(Object o) {
		return o!=null && (o instanceof MyStringComparator);
	}

	@Override
	public int hashCode() {
		return 42; 
	}
}
