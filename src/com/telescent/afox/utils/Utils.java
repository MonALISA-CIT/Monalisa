package com.telescent.afox.utils;

public class Utils {
	
	public static  byte [] Redim (byte [] byteArray, int newLength)
	{
		int currentLength = byteArray.length;
		byte [] result = new byte [newLength];
			if (byteArray != null && currentLength != 0) {
				for (int i = 0; i < currentLength; i++)
					result[i] = byteArray[i];
			}
		
		return result;
	}

}
