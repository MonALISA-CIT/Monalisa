package com.telescent.afox.utils;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

import com.telescent.afox.global.SM_InOrOut_CurAndPending;

public class FlatSerialize {
	public byte[] serializedData;
	public boolean swap = false;
	public byte[] toAdd;
	public int nextPos = 0;

	public byte[] getSerializedData() {
		return serializedData;
	}
	public void setSerializedData(byte[] serializedData) {
		this.serializedData = serializedData;
	}
	public boolean isSwap() {
		return swap;
	}
	public void setSwap(boolean swap) {
		this.swap = swap;
	}
	public byte[] getToAdd() {
		return toAdd;
	}
	public void setToAdd(byte[] toAdd) {
		this.toAdd = toAdd;
	}
	public int getNextPos() {
		return nextPos;
	}
	public void setNextPos(int nextPos) {
		this.nextPos = nextPos;
	}


	public FlatSerialize(boolean swap)
	{
		serializedData = new byte[] { };
		this.swap = swap;
	}

	public FlatSerialize() 
	{
		this(false);
	}

	public byte [] ByteSwap (byte[] buffer)
	{
		Collections.reverse(Arrays.asList(buffer));
		return buffer;
	}

	void AddBytes()  //This form will swap bytes according to setting
	{
		AddBytes(swap);
	}

	void AddBytes(boolean swap) { //make sure swap is false when calling with a string
		int len = toAdd.length;
//		byte [] toAddAux = toAdd;
		if (swap) {
			reverse(toAdd);
//			toAdd = ByteSwap(toAddAux);
		}
		serializedData = Utils.Redim(serializedData, serializedData.length + len);
		for (int i = 0; i < len; i++) {
			serializedData[nextPos] = toAdd[i];
			nextPos++;
		}
	}
	public static byte[] intToByteArray(int value) {
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            int offset = (b.length - 1 - i) * 8;
            b[i] = (byte) ((value >>> offset) & 0xFF);
        }
        reverse(b);
        return b;
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
	
    public static int intFromByteArray(byte[] b) {
//    	reverse(b);
        return ByteBuffer.wrap(b).getInt();
    }
    
	public void AddItems(Object[] objArr)
	{
		Object [] length;
		for (Object obj : objArr)
		{
			String typeName = obj.getClass().toString();

			if (typeName.equals("class java.lang.String")) {
                 toAdd = Conversion.convertToByteArray((String)obj);
//                 int idx = toAdd.length;
//                 toAdd = Utils.Redim(toAdd, idx+1);
//                 toAdd[idx] = 0;  //null terminating the string
                 AddBytes(false);  //Do not allow swapping
                 continue;
             }
			else if (typeName.equals("class [B")) {
				int len = ((byte[])obj).length;
				length = new Object[1];
				length[0] = len;
				AddItems(length);  //a little weird. we are calling this routine right now, but since this is an int we won't collide here.
				serializedData = (byte[])Utils.Redim(serializedData, serializedData.length + len);
				for (int i = 0; i < len; i++)
				{
					serializedData[nextPos] = ((byte[])obj)[i];
					nextPos++;
				}
			}
			else if (typeName.equals("class [I")) {
				int len = ((int[])obj).length;
				length = new Object[1];
				length[0] = len;
				AddItems(length);  //a little weird. we are calling this routine right now, but since this is an int we won't collide here.
				for (int i = 0; i < len; i++)
				{
					length = new Object[1];
					length[0] = ((int[])obj)[i];
					AddItems(length);
				}
			}
			else if (typeName.equals("class java.lang.Character"))
			{
				char val = (Character)obj;
				toAdd = Conversion.convertToByteArray(val);
			}
			else if (typeName.equals("class java.lang.Boolean"))
			{
				boolean val = (Boolean)obj;
				toAdd = Conversion.convertToByteArray(val);
			}
			else if (typeName.equals("class java.lang.Byte"))   
			{
				byte val = (Byte)obj;
				toAdd = Conversion.convertToByteArray(val);				
			}

			else if (typeName.equals("class java.lang.Integer"))
			{
				int val = (Integer)obj;
				toAdd = Conversion.convertToByteArray(val);
			}
			
			else if (typeName.equals("class java.lang.Short"))   
			{
				short val = (Short)obj;
				toAdd = Conversion.convertToByteArray(val);
			}
			else if (typeName.equals( "class java.lang.Long"))
			{
				long val = (Long)obj;
				toAdd = Conversion.convertToByteArray(val);
			}
			else if (typeName.equals( "class java.lang.Double"))
			{
				double val = (Double)obj;
				toAdd = Conversion.convertToByteArray(val);
			}
			else if (typeName.equals( "class java.lang.Float"))           
			{
				float val = (Float)obj;
				toAdd = Conversion.convertToByteArray(val);
			} else {
				 System.err.println("FlatSerialize - AddItems - Programming error case not found: " + typeName);
                 break;
			}

			//The setup is done. Now add the bytes
			AddBytes();
		}
	}
	
    public void AddItem(SM_InOrOut_CurAndPending[][] SMarr) {
        int i_len = SMarr.length;
        int j_len = SMarr[0].length;
        Object [] items = new Object [2];
        items[0] = i_len;
        items[1] = j_len;
        AddItems(items);
        for (int i = 0; i < i_len; i++) {
            for (int j = 0; j < j_len; j++) {
            	items = new Object [1];
            	items[0] = SMarr[i][j].ToFlatSer();
                AddItems(items);
            }
        }
    }


    //AddSerData just adds bytes without adding a length designator at the beginning.  This is used to copy
    //over data that has been already serialized.  This can be used to take the output of bytes
    //from a serializer source such as a class serializing an array of its own objects into an array of bytes.
    public void AddSerData(byte[] bArr) {
        serializedData = Utils.Redim(serializedData, serializedData.length + bArr.length);
        for (int i = 0; i < bArr.length; i++) {
            serializedData[nextPos] = bArr[i];
            nextPos++;
        }
    }
    
    public static String NoNull(String str) {
        if (str == null) str = "";
        return str;
    }
}

