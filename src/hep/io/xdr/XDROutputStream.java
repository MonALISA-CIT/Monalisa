package hep.io.xdr;

import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A class for writing XDR files. Not too hard to do in Java since the XDR format is very
 * similar to the Java native DataStream format, except for String and the fact that elements
 * (ro an array of elements) are always padded to a multiple of 4 bytes.
 *
 * This class requires the user to call the pad method, to skip to the next
 * 4-byte boundary after writing an element or array of elements that may not
 * span a multiple of 4 bytes.
 * @author Tony Johnson (tonyj@slac.stanford.edu)
 * @version $Id: XDROutputStream.java 7266 2012-06-25 23:18:35Z ramiro $
 */
public class XDROutputStream extends DataOutputStream implements XDRDataOutput
{
   public XDROutputStream(OutputStream out)
   {
      super(new CountedOutputStream(out));
      cout = (CountedOutputStream) this.out;
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
   @Override
public void writeIntArray(int[] array) throws IOException
   {
      writeInt(array.length);
      for (int i=0; i<array.length; i++) writeInt(array[i]);
   }
   @Override
public void writeIntArray(int[] array, int start, int n) throws IOException
   {
      writeInt(n);
      for (int i=start; i<n; i++) writeInt(array[i]);
   }
   @Override
public void writeDoubleArray(double[] array) throws IOException
   {
      writeInt(array.length);
      for (int i=0; i<array.length; i++) writeDouble(array[i]);
   }
   @Override
public void writeDoubleArray(double[] array, int start, int n) throws IOException
   {
      writeInt(n);
      for (int i=start; i<n; i++) writeDouble(array[i]);
   }
   @Override
public void writeFloatArray(float[] array) throws IOException
   {
      writeInt(array.length);
      for (int i=0; i<array.length; i++) writeFloat(array[i]);
   }
   @Override
public void writeFloatArray(float[] array, int start, int n) throws IOException
   {
      writeInt(n);
      for (int i=start; i<n; i++) writeFloat(array[i]);
   }	/**
    * Skips appropriate amount to bring stream to 4-byte boundary.
    */
   @Override
public void pad() throws IOException
   {
      int offset = (int) (getBytesWritten() % 4);
      if (offset != 0) write(padding,0,4 - offset);
   }
   public long getBytesWritten()
   {
      return cout.getBytesWritten();
   }
   private CountedOutputStream cout;
   private final static byte[] padding =
   { 0, 0, 0, 0 };
   
   private static final class CountedOutputStream extends FilterOutputStream
   {
      CountedOutputStream(OutputStream out)
      {
         super(out);
      }
      @Override
    public void write(int b) throws IOException
      {
         out.write(b);
         count++;
      }
      @Override
    public void write(byte[] data) throws IOException
      {
         out.write(data);
         count += data.length;
      }
      @Override
    public void write(byte[] data, int off, int len) throws IOException
      {
         out.write(data,off,len);
         count += len;
      }
      public long getBytesWritten()
      {
         return count;
      }
      private long count = 0;
   }
}
