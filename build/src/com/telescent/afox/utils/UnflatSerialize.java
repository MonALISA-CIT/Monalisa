package com.telescent.afox.utils;

import java.nio.ByteBuffer;
import java.util.Arrays;


public class UnflatSerialize {
	private byte[] serializedData;
	private boolean swap = false;
	private byte[] toAdd;
	private int nextPos = 0;
	//am inlocuit byte[] din codul original cu ByteBuffer
	private ByteBuffer byteArr;
	private int numBytesLeft = 0;
	private boolean operationFailed = false;
	
	private boolean currentOperationStatus;
	
	//variabile global folosite pentru a seta tipurile de date deserializate
	
	public String stringVal;
	public byte byteVal;
	public short shortVal;
	public int intVal;
	public long longVal;
	public float floatVal;
	public double doubleVal;
	public boolean boolVal;
	public char charVal;
	public byte[] byteArray;
	public int[] intArray;

	/* Getters and setters for private members*/

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

	public ByteBuffer getByteArr() {
		return byteArr;
	}

	public void setByteArr(ByteBuffer byteArr) {
		this.byteArr = byteArr;
	}

	public int getNumBytesLeft() {
		return numBytesLeft;
	}

	public void setNumBytesLeft(int numBytesLeft) {
		this.numBytesLeft = numBytesLeft;
	}

	public boolean isOperationFailed() {
		return operationFailed;
	}

	public void setOperationFailed(boolean operationFailed) {
		this.operationFailed = operationFailed;
	}

	public boolean isCurrentOperationStatus() {
		return currentOperationStatus;
	}

	public void setCurrentOperationStatus(boolean currentOperationStatus) {
		this.currentOperationStatus = currentOperationStatus;
	}

	/* Constructors */
	public UnflatSerialize(byte[] SerializedData, boolean swap)
	{
		serializedData = SerializedData;
		numBytesLeft = SerializedData.length;
		this.swap = swap;
	}

	public UnflatSerialize(byte[] SerializedData)
	{
		this(SerializedData, true);
	}

	private static final void reverse(byte b[]) {
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
	
	/* auxiliary stuff*/
	//copiaza in byteArr un numar de bytes din serializedData corespunzator unui anumit tip de date
	 private boolean GetBytes(int numberOfBytesToGet, boolean swap)
	    {
	        byteArr = ByteBuffer.allocate(numberOfBytesToGet);
	        byte [] byteArray = new byte [numberOfBytesToGet];
	        if (operationFailed) return false;
	        if (numBytesLeft >= numberOfBytesToGet)
	        {
	            byteArray = Arrays.copyOfRange(serializedData, nextPos, nextPos + numberOfBytesToGet);
	            //(m_SerializedData, m_NextPos, byteArray, 0, NumberOfBytesToGet);
	            nextPos += numberOfBytesToGet;
	            numBytesLeft -= numberOfBytesToGet;

	            if (swap) 
	            	reverse(byteArray);	   
	            byteArr = ByteBuffer.wrap(byteArray);
	            return (true);
	        }
	        else
	        {
	            numberOfBytesToGet = 0;
	            operationFailed = true;
	            return (false);
	        }
	    }
	 
	 private boolean GetBytes(int numberOfBytesToGet)
	    {
	        return (GetBytes(numberOfBytesToGet, swap));
	    }
	
	/* deserializing methods */
	
		
	public boolean getBoolean()
	{
		boolVal = false;
		if (!operationFailed)
		{
	        boolean success = GetBytes(1); //sizeof(System.Double)
	        if (success)
	        {
	            boolVal = 
	            	(byteArr.array() == null || byteArr.array().length == 0) ? 
	            			false : byteArr.array()[0] != 0x00;
	        }	 
	        return success;
		}
		return false;
	}
	
	public boolean getChar()
	{
		charVal = 'a';
		if (!operationFailed)
		{
	        boolean success = GetBytes(2); //sizeof(System.Double)
	        if (success)
	        {
	            charVal = byteArr.getChar();
	        }	 
	        return success;
		}
		return false;
	}
	
	public boolean getDouble()
	{
		doubleVal = 0.0;
		if (!operationFailed)
		{
	        boolean success = GetBytes(8); //sizeof(System.Double)
	        if (success)
	        {
	            doubleVal = byteArr.getDouble();
	        }	 
	        return success;
		}
		return false;
	}
	
	public boolean getByte()
	{
		byteVal = 0;
		if (!operationFailed)
		{
	        boolean success = GetBytes(1); //sizeof(System.Double)
	        if (success)
	        {
	            byteVal = byteArr.get(0);
	        }	 
	        return success;
		}
		return false;
	}
	
	public boolean getShort()
	{	
		shortVal = 0;
		if (!operationFailed)
		{
	        boolean success = GetBytes(2); //sizeof(System.Double)
	        if (success)
	        {
	            shortVal = byteArr.getShort();
	        }	 
	        return success;
		}
		return false;
	}
	
	public boolean getInt()
	{	
		intVal = 0;
		if (!operationFailed)
		{
	        boolean success = GetBytes(4); //sizeof(System.Double)
	        if (success)
	        {
	            intVal = byteArr.getInt();	        }	 
	        return success;
		}
		return false;
	}
	
	public boolean getLong()
	{
		longVal = 0;
		if (!operationFailed)
		{
	        boolean success = GetBytes(8); //sizeof(System.Double)
	        if (success)
	        {
	            longVal = byteArr.getLong();
	        }	 
	        return success;
		}
		return false;
	}
	public boolean getFloat()
	{
		floatVal = 0.0f;
		if (!operationFailed)
		{
	        boolean success = GetBytes(4); //sizeof(System.Single)
	        if (success)
	        {
	            floatVal = byteArr.getFloat();
	        }	 
	        return success;
		}
		return false;
	}
	
	public boolean getByteArray()
	{
		int i_len;
        byteArray = null; //init to null
        boolean success;
        //byte byteVal;

        success = getInt();
            if (!success) return success;
            i_len = intVal;
            byteArray = new byte[i_len];
            for (int i = 0; i < i_len; i++)
            {
                success = getByte();
                if (!success) return success;
                byteArray[i] = byteVal;
            }
            return success;  //should be successful if here        
	}
	
	public boolean getIntArray()
	{
		int i_len;
        intArray = null; //init to null
        boolean success;
        success = getInt();
        if (!success) return success;
        i_len = intVal;
        intArray = new int[i_len];
        for (int i = 0; i < i_len; i++)
        {
            success = getInt();
            if (!success) return success;
            intArray[i] = intVal;
        }
        return success; 

	}
	
	public boolean getString() {
		byteArray = new byte[0];
        int idx = 0;
        boolean success = false;
        stringVal = "";  //preset to this in case of error

        //Never do anything if we already have had a failure
        if (operationFailed) return false;

        boolean FoundTerminator = false;
        
        while (serializedData.length > nextPos) {
            if (serializedData[nextPos] == 0) {
                FoundTerminator = true;
                nextPos++; numBytesLeft--; //inc past terminator
                break;
            }

           byteArray =  Utils.Redim(byteArray, byteArray.length + 1);
           idx = byteArray.length-1;
           byteArray[idx] = serializedData[nextPos];
           nextPos++; numBytesLeft--;
        }

        //If we ran out of data before finding the terminator we have an error, otherwise we are good
        success = FoundTerminator;

        if (success)
        {
            if (byteArray.length == 0) //the string is empty so just assing it ""
            {
                stringVal = "";
            }
            else
            {
                stringVal = Conversion.toString(byteArray);
            }
        }
        else
        {
            operationFailed = true;
        }

        //If it wasnt succesful we have already set val = ""
        return success;
	}
	

}
