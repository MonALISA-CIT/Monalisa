/* $Id: SyntheticAttrInfo.java,v 1.1 2007-07-20 09:34:29 ramiro Exp $
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
 * Representation of a synthetic attribute.
 *
 * @author Mark Welsh
 * @author Eric Lafortune
 */
public class SyntheticAttrInfo extends AttrInfo
{
    private static final int CONSTANT_FIELD_SIZE = 0;


    protected SyntheticAttrInfo()
    {
    }


    // Implementations for AttrInfo.

    protected int getLength()
    {
        return CONSTANT_FIELD_SIZE;
    }

    protected void readInfo(DataInput din, ClassFile classFile) throws IOException
    {
    }

    protected void writeInfo(DataOutput dout) throws IOException
    {
    }

    public void accept(ClassFile classFile, AttrInfoVisitor attrInfoVisitor)
    {
        attrInfoVisitor.visitSyntheticAttrInfo(classFile, this);
    }
}
