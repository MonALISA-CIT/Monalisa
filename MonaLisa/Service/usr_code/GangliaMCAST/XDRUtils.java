/***************************************************************
COPYRIGHT_BEGIN
  Copyright (C) DSTC Pty Ltd (ABN 48 052 372 577) 1995-2001.
  All Rights Reserved.

  The software contained on this media is the property of the DSTC Pty
  Ltd. Use of this software is strictly in accordance with the license
  agreement in the accompanying COPYING file.  If your distribution of
  this software does not contain a COPYING file then you have no
  rights to use this software in any manner and should contact DSTC at
  the address below to determine an appropriate licensing arrangement.
  
     DSTC
     Level 7, General Purpose South
     University of Queensland, 4072
     Australia
     Tel: +61 7 3365 4310
     Fax: +61 7 3365 4311
     Email: elvin@dstc.com

     Web: http://elvin.dstc.com

  This software is being provided "AS IS" without warranty of any
  kind.  In no event shall DSTC Pty Ltd be liable for damage of any
  kind arising out of or in connection with the use or performance of
  this software.
COPYRIGHT_END
****************************************************************/
//package org.elvin.util;

/**
 * Util functions to decode/encode XDR base types.
 * Based on RFC 1832.<p>
 * 
 * Understands the following formats:
 *
 * <pre>
           (MSB)                   (LSB)
         +-------+-------+-------+-------+
         | zero  | zero  | zero  | byte  |                        BYTE
         +-------+-------+-------+-------+
         <------------32 bits------------>

<p>
           (MSB)                   (LSB)
         +-------+-------+-------+-------+
         |byte 0 |byte 1 |byte 2 |byte 3 |                      INTEGER
         +-------+-------+-------+-------+
         <------------32 bits------------>

<p>
        (MSB)                                                   (LSB)
      +-------+-------+-------+-------+-------+-------+-------+-------+
      |byte 0 |byte 1 |byte 2 |byte 3 |byte 4 |byte 5 |byte 6 |byte 7 |
      +-------+-------+-------+-------+-------+-------+-------+-------+
      <----------------------------64 bits---------------------------->
                                                          HYPER INTEGER
<p>
         +------+------+------+------+------+------+------+------+
         |byte 0|byte 1|byte 2|byte 3|byte 4|byte 5|byte 6|byte 7|
         S|    E   |                    F                        |
         +------+------+------+------+------+------+------+------+
         1|<--11-->|<-----------------52 bits------------------->|
         <-----------------------64 bits------------------------->
                                        DOUBLE-PRECISION FLOATING-POINT
<p>
            0     1     2     3     4     5   ...
         +-----+-----+-----+-----+-----+-----+...+-----+-----+...+-----+
         |        length n       |byte0|byte1|...| n-1 |  0  |...|  0  |
         +-----+-----+-----+-----+-----+-----+...+-----+-----+...+-----+
         |<-------4 bytes------->|<------n bytes------>|<---r bytes--->|
                                 |<----n+r (where (n+r) mod 4 = 0)---->|
                                                  VARIABLE-LENGTH OPAQUE
<p>
            0     1     2     3     4     5   ...
         +-----+-----+-----+-----+-----+-----+...+-----+-----+...+-----+
         |        length n       |byte0|byte1|...| n-1 |  0  |...|  0  |
         +-----+-----+-----+-----+-----+-----+...+-----+-----+...+-----+
         |<-------4 bytes------->|<------n bytes------>|<---r bytes--->|
                                 |<----n+r (where (n+r) mod 4 = 0)---->|
                                                                  STRING
<p>

 * </pre>
 * enum is the same as integer, boolean is the same as
 * <code>enum { FALSE = 0, TRUE = 1 }</code>.
 *
 * @version $Id: XDRUtils.java,v 1.1.1.1 2003-10-20 12:28:26 catac Exp $
 */
public class XDRUtils
{
    /**
     * Length in bytes of a signed int32.
     */
    public static final int UINT8_LEN = 4;

    /**
     * Length in bytes of a signed int32.
     */
    public static final int INT32_LEN = 4;

   /**
     * Length in bytes of a signed int64.
     */
    public static final int INT64_LEN = 8;

    /**
     * Length in bytes of an enum or type.
     */
    public static final int ENUM_LEN = 4;

    /**
     * Length in bytes of a boolean.
     */
    public static final int BOOL_LEN = 4;

    /**
     * Length in bytes of a double precision float.
     */
    public static final int REAL64_LEN = 8;

    /**
     * Encode an 8bit unsigned integer into a byte array 
     * starting at an offset into the array.
     *
     * @param value value to encode into the array.
     * @param buf 4 bytes to store the encoded value.
     * @param offset position in buf to place the encoded value.
     */
    public static final void encodeUint8(byte value, byte[] buf, int offset)
    {
	buf[offset++] = (byte)0;
	buf[offset++] = (byte)0;
	buf[offset++] = (byte)0;
        buf[offset] = value;
    }

    /**
     * Decode an 8bit unsigned integer from a byte array
     * starting at an offset into the array.
     *
     * @param data 4 bytes to be converted into an uint8.
     * @param offset position in data to start decoding from.
     * @return decoded uint8.
     */
    public static final byte decodeUint8(byte[] data, int offset)
    {
	// We check the top bytes being zero....
	if( (data[offset] | data[offset+1] | data[offset+2] ) !=0 )
	    throw new java.lang.NumberFormatException("Invalid hi-bytes");

	return data[offset+3];
    }

    /**
     * Encode a 32bit integer into a byte array 
     * starting at an offset into the array.
     *
     * @param value value to encode into the array.
     * @param buf 4 bytes to store the encoded value.
     * @param offset position in buf to place the encoded value.
     */
    public static final void encodeInt32(int value, byte[] buf, int offset)
    {
	encodeFourBytes(value, buf, offset);
    }
    
    /**
     * Decode a 32bit integer from a byte array
     * starting at an offset into the array.
     *
     * @param data 4 bytes to be converted into an int32.
     * @param offset position in data to start decoding from.
     * @return decoded int32.
     */
    public static final int decodeInt32(byte[] data, int offset)
    {
	/*
	 * Because Java only has signed types, a normal cast from
	 * a byte to an int won't work.  If the high-bit is set, the
	 * runtime cast will sign extend the value.  So, after
	 * the cast to an int, we mask off any ones.
         *
         * Also, on most decent JVMs, especially with a JIT, the loop
         * is actually faster than inline because it can be optimised
         * at runtime.
	 */
	int result = 0;
	int radix = 0;
	int i = INT32_LEN;

	do {
	    result |= ( (((int)data[offset + (--i)]) & 0xFF) << radix);
	    radix += 8;
	} while (i > 0);

	return result;
    }


    /**
     * Encode a 64bit integer into a byte array
     * starting at an offset into the array.
     *
     * @param value value to encode into the array.
     * @param buf 8 bytes to store the encoded value.
     * @param offset position in buf to place encoded value.
     */
    public static final void encodeInt64(long value, byte[] buf, int offset)
    {
	encodeEightBytes(value, buf, offset);
    }

    /**
     * Decode a 64bit integer from a byte array
     * starting at an offset into the array.
     *
     * @param data 8 bytes to be converted into an int64.
     * @param offset position in data to start decoding from.
     * @return decoded int64.
     */
    public static final long decodeInt64(byte[] data, int offset)
    {
	long result = 0;
	long radix = 0;
	int i = INT64_LEN;

	do {
	    result |= ( (((long)data[offset + (--i)]) & 0xFF) << radix );
	    radix += 8;
	} while (i > 0);

	return result;
    }

    /**
     * Encode an enum into a byte array
     * starting at an offset into the array.
     * Packet types are encoded as XDR enums in Elvin4.
     *
     * @param value value to encode into the array.
     * @param buf 4 bytes to store the encoded value.
     * @param offset position in buf to place encoded value.
     */
    public static final void encodeEnum(int value, byte[] buf, int offset)
    {
	// Coded as ints
	encodeFourBytes(value, buf, offset);
    }

    /**
     * Decode an enum from a byte array
     * starting at an offset into the array.
     * Packet types are encoded as XDR enums in Elvin4.
     *
     * @param data 4 bytes to be converted into an enum.
     * @param offset position in data to start decoding from.
     * @return decoded enum/type.
     */
    public static final int decodeEnum(byte[] data, int offset)
    {
	// Coded as ints
	return decodeInt32(data, offset);
    }

    /**
     * Encode a boolean into a byte array
     * starting at an offset into the array.
     *
     * @param value value to encode into the array.
     * @param buf 4 bytes to store the encoded value.
     * @param offset position in buf to place encoded value.
     */
    public static final void encodeBool(boolean value, byte[] buf, int offset)
    {
	// same as enum { FALSE = 0, TRUE = 1 }
	buf[offset++] = (byte)0;
	buf[offset++] = (byte)0;
	buf[offset++] = (byte)0;
        buf[offset] = value ? (byte)1 : (byte)0;
    }

    /**
     * Decode a boolean from a byte array
     * starting at an offset into the array.
     *
     * @param data 4 bytes to be converted into a boolean.
     * @param offset position in data to start decoding from.
     * @return decoded boolean.
     */
    public static final boolean decodeBool(byte[] data, int offset)
    {
	// same as enum { FALSE = 0, TRUE = 1 }
	// We only care about the LSB, the rest should all be zero with
	// the LSB giving the value.

	// We check the top bytes being zero....
	if( (data[offset] | data[offset+1] | data[offset+2] ) !=0 )
	    throw new java.lang.NumberFormatException("Invalid bytes for boolean");

	return ((int)(data[offset+3]) == 1 ) ? true : false;
    }

    /**
     * Encode a single precision float into a byte array
     * starting at an offset into the array.
     * java.lang.Double has methods for converting from a long to
     * IEEE 754 floats as used in XDR.
     *
     * @param value value to encode into the array.
     * @param buf 8 bytes to store the encoded value.
     * @param offset position in buf to place encoded value.
     */
    public static final void encodeReal64(double value, byte[] buf, int offset)
    {
	encodeEightBytes(Double.doubleToLongBits(value), buf, offset);
    }

    /**
     * Decode a single precision float from a byte array
     * starting at an offset into the array.
     * java.lang.Double has methods for converting from a long to IEEE 754 floats
     * as used in XDR.
     *
     * @param data 8 bytes to be converted into a single precision float.
     * @param offset position in data to start decoding from.
     * @return decoded float.
     */
    public static final double decodeReal64(byte[] data, int offset)
    {
	return Double.longBitsToDouble(decodeInt64(data,offset));
    }

    public static final float decodeReal32(byte[] data, int offset)
    {
	return Float.intBitsToFloat(decodeInt32(data,offset));
    }

    /**
     * Encode an opaque sequence of bytes
     * starting at an offset into the array.
     *
     * @param value value to encode into the array.
     * @param buf 8 bytes to store the encoded value.
     * @param offset position in buf to place encoded value.
     * @return the total length of the marshalled data.
     */
    public static final int encodeOpaque(byte[] value, byte[] buf, int offset)
    {
	encodeFourBytes(value.length, buf, offset);
	offset += INT32_LEN;
	System.arraycopy(value, 0, buf, offset, value.length);
	offset += value.length;
	return zeroPad(buf, offset) + INT32_LEN + value.length;
    }

    /**
     * Decode an opaque.
     * Data must begin with a 4-byte int indicating length to decode.
     *
     * @param data bytes to be converted into a byte array.
     * @return decoded array.
     */
    public static final byte[] decodeOpaque(byte[] data, int offset)
    {
	byte[] result;

	int resultLen = decodeInt32(data, offset);
	offset += INT32_LEN;
	result = new byte[resultLen];
	System.arraycopy(data, offset, result, 0, resultLen);

	return result;
    }

    /**
     * Calculate the length of a string when encoded.
     *
     * @param value value to calculate the encoded length.
     * @return the length of string as UTF-8.  No padding, no length prepended.
     */
    public static final int stringLength(String value)
    {
	int valueLen = value.length();
	int utfLen = 0;
	int ch, i;

	// Calculate the length of the encoded string.  This is >= the actual
	// length as some char will map to 2 or 3 bytes.
        utfLen = valueLen;
	for (i = 0 ; i < valueLen ; i++) {
	    ch = value.charAt(i);
            if (ch > 0x07FF) {
		utfLen += 2;
	    } else if (ch > 0x007F){
		utfLen += 1;
	    }
	}
	return utfLen;
    }

    /**
     * Encode a Java String into a UTF-8 sequence
     * starting at an offset into the array.
     *
     * 0x0000 - 0x007F is one byte
     * 0x0080 - 0x07FF is two bytes
     * 0x0800 - 0xFFFF is three bytes
     *
     * @param value value to encode into the array.
     * @param buf a buffer to store the encoded value.
     * @param offset position in buf to place encoded value.
     * @return the total length of the marshalled data.
     */
    public static final int encodeString(String value, byte[] buf, int offset)
    {
	int valueLen = value.length();
        int utfLen = 0;
	int i, ch, index;

        // leave space for the length of the screen which we calculate
        // as we go.
	index = offset + INT32_LEN;

        // encode with the extended chars
        for (i = 0 ; i < valueLen ; i++) {
            ch = value.charAt(i);

            if (ch <= 0x007F) {
                buf[index++] = (byte) (ch);
            } else if(ch <= 0x077F) {
                buf[index++] = (byte) (0xC0 | ((ch >>  6) & 0x1F));
                buf[index++] = (byte) (0x80 | ((ch >>  0) & 0x3F));
            } else {
                buf[index++] = (byte) (0xE0 | ((ch >> 12) & 0x0F));
                buf[index++] = (byte) (0x80 | ((ch >>  6) & 0x3F));
                buf[index++] = (byte) (0x80 | ((ch >>  0) & 0x3F));
            }
        }
        // back patch the length
        utfLen = index - offset - INT32_LEN;
	encodeFourBytes(utfLen, buf, offset);
	return zeroPad(buf, index - offset) + INT32_LEN + utfLen;
    }

    /**
     * Decode a string in UTF-8 format
     * starting at an offset into the array.
     * Data must begin with a 4-byte int indicating length to decode.
     *
     * @param data bytes to be converted into a single precision float.
     * @param offset position in data to start decoding from.
     * @return decoded string.
     */
    public static final String decodeString(byte[] data, int offset)
    {
	int ch1, ch2, ch3;
	int count = 0;
	int resultLen = 0;

        int utf8len = decodeInt32(data, offset);
	offset += INT32_LEN;
        //
        char result[] = new char[utf8len];

	while (count < utf8len) {
	    ch1 = ((int)data[offset++]) & 0xFF;
	    // Check the hi half of the byte
	    switch (ch1 >> 4) {
	        case 0x0: case 0x1: case 0x2: case 0x3:
		case 0x4: case 0x5: case 0x6: case 0x7:
		    // Standard one byte char
		    count++;
		    result[resultLen++] = (char)ch1;
		    break;
	        case 0xC: case 0xD:
		    // Extended two byte char
		    count += 2;

		    ch2 = ((int)data[offset++]) & 0xFF;
		    if ((ch2 & 0xC0) != 0x80) {
			throw new IllegalArgumentException
                            ("Invalid hi-byte for 2-byte char");
                    }
		    // Standard UTF8 conversion
		    result[resultLen++] = (char)(((ch1 & 0x1F) << 6) | (ch2 & 0x3F));
		    break;
	        case 0xE:
		    // Extended three byte char
		    count += 3;

		    ch2 = ((int)data[offset++]) & 0xFF;
		    ch3 = ((int)data[offset++]) & 0xFF;
		    if (((ch2 & 0xC0) != 0x80) || ((ch3 & 0xC0) != 0x80)) {
			throw new IllegalArgumentException
                            ("Invalid hi-byte for 3-byte char: 1="
                             +ch1+" 2="+ch2+" 3="+ch3);
                    }
		    // Standard UTF8 conversion
		    result[resultLen++] = (char)(((ch1 & 0x0F) << 12) |
						    ((ch2 & 0x3F) << 6) |
						    ((ch3 & 0x3F) << 0));
		    break;
	        default:
                    // Not valid UTF8
                    throw new IllegalArgumentException("Invalid low-byte for UTF8");
	    }
	}

	return new String(result, 0, resultLen);
    }

    /**
     * Do the work for a 64 bit type.<p>
     *
     * For each byte, starting at the LSB, mask it off, shift to the
     * LSB position and cast it to a byte.  Iteration increments the
     * mask and the shift value a byte each time.
     */
    private static final void encodeEightBytes(long value,
                                               byte[] buf,
                                               int offset)
    {
	int i = INT64_LEN;
	// A common case  check for zero
	if(value == (long)0) {
	    while(i > 0 ) {
		buf[--i] = (byte)0x00;
	    }
	    return;
	}

	long mask = 0xFF;
	int radix = 0x00;

	do {
	    buf[offset + (--i)] = (byte) ((value & mask) >>> radix);
	    mask <<= 8;
	    radix += 8;
	} while (i > 0);
    }

    /**
     * Although not strictly needed, this version that only does ints executes
     * about twice as fast as the 64bit version on certain platforms.
     */
    private static final void encodeFourBytes(int value,
                                              byte[] buf,
                                              int offset)
    {
	int i = INT32_LEN;
	// A common case  check for zero
	if(value == 0) {
	    while(i > 0 ) {
		buf[--i] = (byte)0x00;
	    }
	    return;
	}

	int mask = 0xFF;
	int radix = 0x00;

	do {
	    buf[offset + (--i)] = (byte) ((value & mask) >>> radix);
	    mask <<= 8;
	    radix += 8;
	} while (i > 0);
    }

    /**
     * Some data types do not end on the 4-byte boundary required.  This
     * checks the offset and if it is not a factor of four, adds
     * the required zeros.
     */
    private static final int zeroPad(byte[] buf, int offset)
    {
	int over = (offset % 4);

	if(over == 0) {
	    return 0;
        }

	int padding = 4 - over;

	for(int i=0; i < padding; i++) {
	    buf[offset++] = 0x00;
	}

	return padding;
    }

}
