package lia.util.ciena;

import java.util.Calendar;

public class CienaUtils {

    public static final ParsedCienaTl1Alarm parseTL1ResponseLine(final String TL1Line) throws Exception {
        
        Calendar calendar = Calendar.getInstance();
        
        if(!TL1Line.startsWith("\"") || !TL1Line.endsWith("\"") || TL1Line.length() < 2) {
            throw new Exception("Unknown line format");
        }
        
        //"1-A-CM1,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \","
        
        //strip first " and last "
        //newLine = 1-A-CM1,EQPT:MN,PWR,NSA,2007-04-12,02:44:13,,:\"PowerInArmB=Failed \",
        
        String newLine = TL1Line.substring(1,TL1Line.length()-1);
        
        int firstIDX = newLine.indexOf(":");
        
        if(firstIDX >= 0) {

            ////////////////////////////////////////////////
            //
            //first token = 1-A-CM1,EQPT
            //
            //first token = aid,aidType
            //
            ////////////////////////////////////////////////
            String currentToken = newLine.substring(0, firstIDX);
            int idx = currentToken.indexOf(","); 
            
            //aid
            final String aid = currentToken.substring(0, idx);
            
            //aidType
            final String aidType = currentToken.substring(idx+1);
            
            ////////////////////////////////////////////////
            //
            //second token = MN,PWR,NSA,2007-04-12,02:44:13,,
            //
            //second token = aisnc,condType,[serviceEffect],date,time,[location],[direction]
            //
            ////////////////////////////////////////////////
            currentToken = newLine.substring(firstIDX + 1);

            
            //aisnc
            int cidx = currentToken.indexOf(",");
            if(cidx < 0) {
                throw new Exception("Cannot determine aisc field");
            }
            
            final String aisnc = currentToken.substring(0, cidx);
            currentToken = currentToken.substring(cidx + 1);

            
            //condType
            cidx = currentToken.indexOf(",");
            if(cidx < 0) {
                throw new Exception("Cannot determine condType field");
            }
            
            final String condType = currentToken.substring(0, cidx);
            currentToken = currentToken.substring(cidx + 1);
            
            
            //serviceEffect
            cidx = currentToken.indexOf(",");
            if(cidx < 0) {
                throw new Exception("Cannot determine serviceEffect field");
            }
            String serviceEffect = currentToken.substring(0, cidx);
            currentToken = currentToken.substring(cidx + 1);
            
            //date
            cidx = currentToken.indexOf(",");
            if(cidx < 0) {
                throw new Exception("Cannot determine date field");
            }
            
            final String date = currentToken.substring(0, cidx);
            currentToken = currentToken.substring(cidx + 1);
            
            //parse the date (2007-04-12)
            String[] dateTokens = date.split("-");
            if(dateTokens.length != 3) {
                throw new Exception("Parsing error for line: ["+TL1Line+"] for date field. Expected YYYY-MM-DD ... got " + date);
            }
            
            final int year = Integer.parseInt(dateTokens[0]);
            final int month = Integer.parseInt(dateTokens[1]);
            final int day = Integer.parseInt(dateTokens[2]);
            
            //time
            cidx = currentToken.indexOf(",");
            if(cidx < 0) {
                throw new Exception("Cannot determine time field");
            }
            
            final String time = currentToken.substring(0, cidx);
            currentToken = currentToken.substring(cidx + 1);
            
            //parse the time (02:44:13)

            String[] timeTokens = time.split(":");
            if(timeTokens.length != 3) {
                timeTokens = time.split("-");
                if(timeTokens.length != 3) {
                    throw new Exception("Parsing error for line: ["+TL1Line+"] for time field. Expected HH:MM:SS ... got " + time);
                }
            }
            
            final int hour = Integer.parseInt(timeTokens[0]);
            final int minutes = Integer.parseInt(timeTokens[1]);
            final int seconds = Integer.parseInt(timeTokens[2]);
            
            calendar.clear();
            calendar.set(year, month - 1, day, hour, minutes, seconds);
            
            //location
            cidx = currentToken.indexOf(",");
            if(cidx < 0) {
                throw new Exception("Cannot determine location field");
            }
            String location = currentToken.substring(0, cidx);
            currentToken = currentToken.substring(cidx + 1);
            
            //direction
            cidx = currentToken.indexOf(":");
            if(cidx < 0) {
                throw new Exception("Cannot determine direction field");
            }
            String direction = currentToken.substring(0, cidx);
            currentToken = currentToken.substring(cidx + 1);
            
            ////////////////////////////////////////////////
            //
            //last token = \"PowerInArmB=Failed \",
            //
            //last token = [desc],[aidDetection]
            //
            ////////////////////////////////////////////////
            idx = currentToken.indexOf(",");
            
            //desc
            final String desc = currentToken.substring(0, idx);
            
            //aidDetection
            final String aidDetection = currentToken.substring(idx+1);
            
            return new ParsedCienaTl1Alarm(aid,aidType,aisnc,condType,serviceEffect,
                    date,time,calendar.getTimeInMillis(),location, direction,
                    desc, aidDetection, TL1Line);
        }
        
        throw new Exception("Expected format: \"aid,aidType:aisnc,condType,[serviceEffect],date,time,[location],[direction]:[desc],[aidDetection]\"\n");
    }
}
