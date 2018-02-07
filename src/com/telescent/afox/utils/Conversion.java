package com.telescent.afox.utils;

public class Conversion {
	/**
	 * Convert a string into a byte array.
	 */
	public static byte[] convertToByteArray( String s ) {
//		try {
//			// see the following page for character encoding
//			// http://java.sun.com/products/jdk/1.1/docs/guide/intl/encoding.doc.html
//			return s.getBytes( "UTF8" );
//		} catch ( java.io.UnsupportedEncodingException uee ) {
//			uee.printStackTrace();
//			throw new Error( "Platform doesn't support UTF8 encoding" );
//		}
		byte res[] = new byte[s.length()+1];
		for (int i=0; i<res.length - 1; i++) {
			res[i] = (byte) s.charAt(i);
		}
		res[s.length()] = 0;
		return res;
	}


	/**
	 * Convert a byte into a byte array.
	 */
	public static byte[] convertToByteArray( byte n )
	{
		n = (byte)( n ^ ( (byte) 0x80 ) ); // flip MSB because "byte" is signed
		return new byte[] { n };
	}


	/**
	 * Convert a short into a byte array.
	 */
	public static byte[] convertToByteArray( short n )
	{
		n = (short) ( n ^ ( (short) 0x8000 ) ); // flip MSB because "short" is signed
		byte[] key = new byte[ 2 ];
		pack2( key, 0, n );
		return key;
	}


	private final static void reverse(byte b[]) {
		byte t;
		if (b==null) return;
		int l = b.length;
		int m = l/2;
		for (int i=0; i<m; i++) {
			t = b[i];
			b[i] = b[l-i-1];
			b[l-i-1] = t;
		}
	}
	
	/**
	 * Convert an int into a byte array.
	 */
	public static byte[] convertToByteArray( int n ) {
//		n = (n ^ 0x80000000); // flip MSB because "int" is signed
		byte[] key = new byte[4];
		pack4(key, 0, n);
		reverse(key);
		return key;
	}

	//Convert a float into a byte array
	public static byte[] convertToByteArray(float data) {
		return convertToByteArray(Float.floatToRawIntBits(data));
	}

	//convert a char into a byte array

	public static byte[] convertToByteArray(char data) {   
		return new byte[] {    
				(byte)((data >> 8) & 0xff),    
				(byte)((data >> 0) & 0xff),    
		};    
	}
	
	//convert double to byte array
	public static byte[] convertToByteArray(double data) {
		return convertToByteArray(Double.doubleToRawLongBits(data));
	}
	
	//convert bool to byte array
	public static byte[] convertToByteArray(boolean data) {
		return new byte[]{(byte)(data ? 0x01 : 0x00)}; // bool -> {1 byte}
	}
	

	/**
	 * Convert a long into a byte array.
	 */
	public static byte[] convertToByteArray( long n )
	{
		n = (n ^ 0x8000000000000000L); // flip MSB because "long" is signed
		byte[] key = new byte[8];
		pack8( key, 0, n );
		return key;
	}


	/**
	 * Convert a byte array (encoded as UTF-8) into a String
	 */
	public static String convertToString( byte[] buf )
	{
		try {
			// see the following page for character encoding
			// http://java.sun.com/products/jdk/1.1/docs/guide/intl/encoding.doc.html
			return new String( buf, "UTF8" );
		} catch ( java.io.UnsupportedEncodingException uee ) {
			uee.printStackTrace();
			throw new Error( "Platform doesn't support UTF8 encoding" );
		}
	}


	/**
	 * Convert a byte array into an integer (signed 32-bit) value.
	 */
	public static int convertToInt( byte[] buf )
	{
		int value = unpack4( buf, 0 );
		value = ( value ^ 0x80000000 ); // flip MSB because "int" is signed
		return value;
	}


	/**
	 * Convert a byte array into a long (signed 64-bit) value.
	 */
	public static long convertToLong( byte[] buf )
	{
		long value = ( (long) unpack4( buf, 0 ) << 32  )
		+ ( unpack4( buf, 4 ) & 0xFFFFFFFFL );
		value = ( value ^ 0x8000000000000000L ); // flip MSB because "long" is signed
		return value;
	}




	static int unpack4( byte[] buf, int offset )
	{
		int value = ( buf[ offset ] << 24 )
		| ( ( buf[ offset+1 ] << 16 ) & 0x00FF0000 )
		| ( ( buf[ offset+2 ] << 8 ) & 0x0000FF00 )
		| ( ( buf[ offset+3 ] << 0 ) & 0x000000FF );

		return value;
	}


	static final void pack2( byte[] data, int offs, int val )
	{
		data[offs++] = (byte) ( val >> 8 );
		data[offs++] = (byte) val;
	}


	static final void pack4( byte[] data, int offs, int val )
	{
		data[offs++] = (byte) ( val >> 24 );
		data[offs++] = (byte) ( val >> 16 );
		data[offs++] = (byte) ( val >> 8 );
		data[offs++] = (byte) val;
	}


	static final void pack8( byte[] data, int offs, long val )
	{
		pack4( data, 0, (int) ( val >> 32 ) );
		pack4( data, 4, (int) val );
	}
	
	//convert a byte array to string
	public static String toString(byte[] data) {
	return (data == null) ? null : new String(data);
	}

}
