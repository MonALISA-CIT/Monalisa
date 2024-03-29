/* $Id: ExceptionsAttrInfo.java,v 1.1 2007-07-20 09:34:29 ramiro Exp $
 *
 * ProGuard -- shrinking, optimization, and obfuscation of Java class files.
 *
 * Copyright (c) 1999      Mark Welsh (markw@retrologic.com)
 * Copyright (c) 2002-2007 Eric Lafortune (eric@graphics.cornell.edu)
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package proguard.classfile.attribute;

import proguard.classfile.visitor.*;
import proguard.classfile.attribute.*;
import proguard.classfile.*;

import java.io.*;

/**
 * Representation of an exceptions attribute.
 *
 * @author Mark Welsh
 * @author Eric Lafortune
 */
public class ExceptionsAttrInfo extends AttrInfo
{
    private static final int CONSTANT_FIELD_SIZE = 2;


    public int   u2numberOfExceptions;
    public int[] u2exceptionIndexTable;


    protected ExceptionsAttrInfo()
    {
    }


    // Implementations for AttrInfo.

    protected int getLength()
    {
        return CONSTANT_FIELD_SIZE + 2 * u2numberOfExceptions;
    }

    protected void readInfo(DataInput din, ClassFile classFile) throws IOException
    {
        u2numberOfExceptions = din.readUnsignedShort();
        u2exceptionIndexTable = new int[u2numberOfExceptions];
        for (int i = 0; i < u2numberOfExceptions; i++)
        {
            u2exceptionIndexTable[i] = din.readUnsignedShort();
        }
    }

    protected void writeInfo(DataOutput dout) throws IOException
    {
        dout.writeShort(u2numberOfExceptions);
        for (int i = 0; i < u2numberOfExceptions; i++)
        {
            dout.writeShort(u2exceptionIndexTable[i]);
        }
    }

    public void accept(ClassFile classFile, AttrInfoVisitor attrInfoVisitor)
    {
        // We'll just ignore Exception attributes that do not belong to a method.
    }

    public void accept(ClassFile classFile, MethodInfo methodInfo, AttrInfoVisitor attrInfoVisitor)
    {
        attrInfoVisitor.visitExceptionsAttrInfo(classFile, methodInfo, this);
    }


    /**
     * Applies the given constant pool visitor to all exception class pool info
     * entries.
     */
    public void exceptionEntriesAccept(ProgramClassFile programClassFile, CpInfoVisitor cpInfoVisitor)
    {
        for (int i = 0; i < u2numberOfExceptions; i++)
        {
            programClassFile.constantPoolEntryAccept(u2exceptionIndexTable[i],
                                                     cpInfoVisitor);
        }
    }
}
