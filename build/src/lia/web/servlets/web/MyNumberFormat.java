package lia.web.servlets.web;

import java.text.FieldPosition;
import java.text.ParsePosition;

import lia.web.utils.DoubleFormat;

/**
 * @author costing
 *
 */
public class MyNumberFormat extends java.text.NumberFormat {
	private static final long serialVersionUID = 4361303265954156701L;

	private boolean isSize;

	private String sizeIn;

	private String suffix;

	private boolean bBit;

	/**
	 * @param bSize
	 * @param sSizeIn
	 * @param sSuffix
	 */
	public MyNumberFormat(final boolean bSize, final String sSizeIn, final String sSuffix) {
		this(bSize, sSizeIn, sSuffix, sSuffix != null && sSuffix.trim().toLowerCase().indexOf("bps") >= 0);
	}

	/**
	 * @param bSize
	 * @param sSizeIn
	 * @param sSuffix
	 * @param inBits 
	 */
	public MyNumberFormat(final boolean bSize, final String sSizeIn, final String sSuffix, final boolean inBits) {
		isSize = bSize;
		sizeIn = sSizeIn;
		suffix = sSuffix;
		bBit = inBits;
	}

	@Override
	public StringBuffer format(final double number, final StringBuffer buffer, final FieldPosition pos) {
		String sVal = isSize ? (bBit ? DoubleFormat.size_bit(number, sizeIn) : DoubleFormat.size(number, sizeIn)) : DoubleFormat.point(number);

		final StringBuffer toAppendTo = buffer == null ? new StringBuffer() : buffer;

		if (sVal.toLowerCase().endsWith("b") && (suffix.toLowerCase().startsWith("b") || suffix.length() == 0))
			sVal = sVal.substring(0, sVal.length() - 1);

		toAppendTo.append(sVal + suffix);

		return toAppendTo;
	}

	@Override
	public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
		return format((double) number, toAppendTo, pos);
	}

	@Override
	public Number parse(String source, ParsePosition parsePosition) {
		return null;
	}

}
