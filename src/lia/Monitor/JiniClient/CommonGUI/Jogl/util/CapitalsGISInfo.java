/*
 * Created on Jun 29, 2005 12:45:44 AM
 * Filename: CapitalsGISInfo.java
 *
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class CapitalsGISInfo {
    private int MAX = 200;
    private int INCREMENT = 10;
    public String sNames[];
    public String sCountries[];
    public float fLat[];
    public float fLong[];
    public String sHour[];
    public CapitalsGISInfo(String fileName)
    {
        sNames = new String[MAX];
        sCountries = new String[MAX];
        sHour = new String[MAX];
        fLat = new float[MAX];
        fLong = new float[MAX];
        ClassLoader myClassLoader = vcf.class.getClassLoader();
        BufferedReader br;
        try {
            br = new BufferedReader(new InputStreamReader(myClassLoader.getResource(fileName).openStream()));
            String line;
            String values[];
            int counter=0;
            int max_len=sNames.length;
            while ( (line=br.readLine()) != null ){
                values = line.split(",");
                if ( values.length==0 )
                    continue; 
                if ( counter==max_len ) {
                    //increase size of arrays
                    String []sAux;
                    sAux= new String[max_len+INCREMENT];
                    System.arraycopy(sNames, 0, sAux, 0, max_len);
                    sNames = sAux;
                    sAux= new String[max_len+INCREMENT];
                    System.arraycopy(sCountries, 0, sAux, 0, max_len);
                    sCountries = sAux;
                    sAux= new String[max_len+INCREMENT];
                    System.arraycopy(sHour, 0, sAux, 0, max_len);
                    sHour = sAux;
                    float []fAux;
                    fAux = new float[max_len+INCREMENT];
                    System.arraycopy(fLat, 0, fAux, 0, max_len);
                    fLat = fAux;
                    fAux = new float[max_len+INCREMENT];
                    System.arraycopy(fLong, 0, fAux, 0, max_len);
                    fLong = fAux;
                    max_len += INCREMENT;
                }
                for ( int i=0; i<values.length; i++) {
                    switch(i) {
                        case 0:
                            sNames[counter] = values[i];
                            break;
                        case 1:
                            sCountries[counter] = values[i];
                            break;
                        case 4:
                            sHour[counter] = values[i];
                            break;
                        case 2:
                            fLat[counter] = Float.parseFloat(values[i]);
                            break;
                        case 3:
                            fLong[counter] = Float.parseFloat(values[i]);
                            break;
                    }
                }
                counter++;
            }
            br.close();
            //redimensionate arrays to counter size
            //decrease size
            String []sAux;
            sAux= new String[counter];
            System.arraycopy(sNames, 0, sAux, 0, counter);
            sNames = sAux;
            sAux= new String[counter];
            System.arraycopy(sCountries, 0, sAux, 0, counter);
            sCountries = sAux;
            sAux= new String[counter];
            System.arraycopy(sHour, 0, sAux, 0, counter);
            sHour = sAux;
            float []fAux;
            fAux = new float[counter];
            System.arraycopy(fLat, 0, fAux, 0, counter);
            fLat = fAux;
            fAux = new float[counter];
            System.arraycopy(fLong, 0, fAux, 0, counter);
            fLong = fAux;
        } catch (Exception e) {
            e.printStackTrace();
        }
        //System.out.println("CapitalsGISInfo read "+getTotalNumber()+" valid entries.");
    }
    public int getTotalNumber() 
    {
        return sNames.length;
    }
}
