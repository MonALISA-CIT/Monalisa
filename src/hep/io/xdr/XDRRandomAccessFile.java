package hep.io.xdr;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A random access file for use with XDR.
 * @author Tony Johnson (tonyj@slac.stanford.edu)
 * @version $Id: XDRRandomAccessFile.java 7266 2012-06-25 23:18:35Z ramiro $
 */
public class XDRRandomAccessFile extends RandomAccessFile implements XDRDataInput, XDRDataOutput
{
   public XDRRandomAccessFile(String name, String mode) throws IOException
   {
      super(name, mode);
   }

   @Override
public void pad() throws IOException
   {
      int offset = (int) (getFilePointer() % 4);
      if (offset != 0)
         skipBytes(4 - offset);
   }

   @Override
public double[] readDoubleArray(double[] buffer) throws IOException
   {
      int l = readInt();
      if (l > 32767)
         throw new IOException("String too long: " + l);

      double[] result = buffer;
      if ((buffer == null) || (l > buffer.length))
         result = new double[l];
      for (int i = 0; i < l; i++)
         result[i] = readDouble();
      return result;
   }

   @Override
public float[] readFloatArray(float[] buffer) throws IOException
   {
      int l = readInt();
      if (l > 32767)
         throw new IOException("String too long: " + l);

      float[] result = buffer;
      if ((buffer == null) || (l > buffer.length))
         result = new float[l];
      for (int i = 0; i < l; i++)
         result[i] = readFloat();
      return result;
   }

   @Override
public int[] readIntArray(int[] buffer) throws IOException
   {
      int l = readInt();
      if (l > 32767)
         throw new IOException("String too long: " + l);

      int[] result = buffer;
      if ((buffer == null) || (l > buffer.length))
         result = new int[l];
      for (int i = 0; i < l; i++)
         result[i] = readInt();
      return result;
   }

   @Override
public String readString(int l) throws IOException
   {
      byte[] ascii = new byte[l];
      readFully(ascii);
      pad();
      return new String(ascii); //BUG: what is default locale is not US-ASCII
   }

   @Override
public String readString() throws IOException
   {
      int l = readInt();
      if (l > 32767)
         throw new IOException("String too long: " + l);
      return readString(l);
   }

   @Override
public void writeDoubleArray(double[] array) throws IOException
   {
      writeInt(array.length);
      for (int i = 0; i < array.length; i++)
         writeDouble(array[i]);
   }

   @Override
public void writeDoubleArray(double[] array, int start, int n) throws IOException
   {
      writeInt(n);
      for (int i = start; i < n; i++)
         writeDouble(array[i]);
   }

   @Override
public void writeFloatArray(float[] array) throws IOException
   {
      writeInt(array.length);
      for (int i = 0; i < array.length; i++)
         writeFloat(array[i]);
   }

   @Override
public void writeFloatArray(float[] array, int start, int n) throws IOException
   {
      writeInt(n);
      for (int i = start; i < n; i++)
         writeFloat(array[i]);
   }

   @Override
public void writeIntArray(int[] array) throws IOException
   {
      writeInt(array.length);
      for (int i = 0; i < array.length; i++)
         writeInt(array[i]);
   }

   @Override
public void writeIntArray(int[] array, int start, int n) throws IOException
   {
      writeInt(n);
      for (int i = start; i < n; i++)
         writeInt(array[i]);
   }

   @Override
public void writeString(String s) throws IOException
   {
      writeInt(s.length());

      byte[] ascii = s.getBytes();
      write(ascii);
      pad();
   }

   @Override
public void writeStringChars(String s) throws IOException
   {
      byte[] ascii = s.getBytes();
      write(ascii);
      pad();
   }
}
