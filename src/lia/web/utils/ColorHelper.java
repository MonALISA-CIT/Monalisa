package lia.web.utils;

import java.awt.Paint;

/**
 * @author costing
 *
 */
public final class ColorHelper{
        private static int _seriesCount = 1;
        private static Paint[] _theColors= { 
            ColorFactory.getColor(16711680),
            ColorFactory.getColor(255),
            ColorFactory.getColor(9419919),
            ColorFactory.getColor(11674146),
            ColorFactory.getColor(32768),
            ColorFactory.getColor(139),
            ColorFactory.getColor(32896),
            ColorFactory.getColor(8421504)
        };
        
        /**
         * @param seriesCount
         */
        public static void setSeriesCount( int seriesCount ) {
            _seriesCount = seriesCount;
        }
        
        /**
         * @param index
         * @return paint
         */
        public static final Paint getColorByIndex(int index) {
            if ( index < 0 ) return null;
            return _theColors[index%_theColors.length];
        }

        /**
         * @return paint array
         */
        public static final Paint[] getColors() {
            if ( _seriesCount > _theColors.length ) {
        	System.out.println(" PLEASE ADD MORE COLORS !!!! "
                                        +" seriesCount = " + _seriesCount 
                                        + " colorsCount = " + _theColors.length
                                        );
            } 
            
            Paint[] _newPaint = new Paint[_seriesCount];
            for ( int i = 0; i < _seriesCount; i++)
                _newPaint[i] = _theColors[i % _theColors.length ];

            return _newPaint;
        }
} 
