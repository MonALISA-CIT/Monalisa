/* $Id: SawToothTiming.java,v 1.1 2007-07-20 09:34:28 ramiro Exp $
 *
 * ProGuard -- shrinking, optimization, and obfuscation of Java class files.
 *
 * Copyright (c) 2002-2007 Eric Lafortune (eric@graphics.cornell.edu)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package proguard.gui.splash;

/**
 * This Timing ramps up linearly from 0 to 1 in a given repeated time interval.
 *
 * @author Eric Lafortune
 */
public class SawToothTiming implements Timing
{
    private long period;
    private long phase;


    /**
     * Creates a new SawToothTiming.
     * @param period the time period for a full cycle.
     * @param phase  the phase of the cycle, which is added to the actual time.
     */
    public SawToothTiming(long period, long phase)
    {
        this.period = period;
        this.phase  = phase;
    }


    // Implementation for Timing.

    public double getTiming(long time)
    {
        // Compute the translated and scaled saw-tooth function.
        return (double)((time + phase) % period) / (double)period;
    }
}
