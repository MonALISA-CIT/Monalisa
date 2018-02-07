package lia.Monitor.Agents.OpticalPath;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class used by MLCopyAgent...
 * 
 */
public final class Util {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Util.class.getName());

    private static final int UNDEFINED = 1;
    private static final int CONNMAP_PARSE = 2;
    private static final int CROSS_PARSE = 3;

    /**
     * 
     * @param osi - OpticalSwitchInfo
     * @return A String representation ... same format used in the confFile
     */
    public static final String translateOpticalSwitchInfo(OpticalSwitchInfo osi) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("SwitchName = ").append(osi.name).append("\n");
        sb.append("SwitchType = ");

        switch (osi.type.shortValue()) {
        case OpticalSwitchInfo.CALIENT: {
            sb.append("Calient");
            break;
        }
        case OpticalSwitchInfo.GLIMMERGLASS: {
            sb.append("Glimmerglass");
            break;
        }
        default: {
            sb.append("Undefined");
            break;
        }
        }

        sb.append("\n\n#Connection Map");
        sb.append("\n#SourcePortName\t\t#LinkType(switch|host)\t#Destination\t#LinkQuality\t#[Destination Port Name]\n");
        for (Object key : osi.map.keySet()) {
            OpticalLink ol = osi.map.get(key);
            sb.append("\n").append(ol.port.name).append("\t");
            sb.append("\t").append(ol.getDecodedType()).append("\t");
            sb.append("\t").append(ol.destination).append("\t");
            sb.append("\t").append(ol.quality);
            if (ol.type.shortValue() == OpticalLink.TYPE_SWITCH) {
                sb.append("\t").append("\t").append(ol.destinationPortName);
            }
        }

        sb.append("\n#Cross-Connect Links");
        sb.append("\n#SourcePortName\tDestinationPortName\n");
        for (Object element : osi.crossConnects.entrySet()) {
            OpticalCrossConnectLink link = (OpticalCrossConnectLink) element;
            sb.append("\n").append(link.sPort.name).append("\t").append(link.dPort.name);
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Returns a hashmap with "raw" configurations
     *      k - switchName
     *      v - "raw" configuration 
     * @param data
     * @return - a hashmap with "raw" configurations or null if no conf detected
     */
    public static final HashMap splitConfigurations(Reader stream) throws Exception {
        HashMap confHash = new HashMap();
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder(1024);
        boolean firstConf = true;

        br = new BufferedReader(stream);
        String currentSwitchName = null;
        String oldSwitchName = null;
        for (String line = br.readLine(); line != null; line = br.readLine()) {
            if (line.trim().startsWith("SwitchName")) {
                oldSwitchName = currentSwitchName;
                currentSwitchName = null;
                String tokens[] = line.split("((\\s)*=(\\s)*)|((\\s)+)");
                if ((tokens == null) || (tokens.length != 2)) {//line will be ignored
                    continue;
                }
                currentSwitchName = tokens[1].trim();
                if (firstConf) {
                    firstConf = false;
                } else {
                    if (oldSwitchName != null) {
                        confHash.put(oldSwitchName, sb.toString());
                    }
                    sb = new StringBuilder(1024);
                }
            }
            sb.append(line).append("\n");
        }

        String lastConf = sb.toString();
        if ((lastConf != null) && (currentSwitchName != null)) {
            confHash.put(currentSwitchName, lastConf);
        }

        if (confHash.size() == 0) {
            return null;
        }

        return confHash;
    }

    private static final void parseConnMap(String line, OpticalSwitchInfo newOSI) {
        //the line should have the following format 
        // PortName Type(host or another peer)  Hostname(or swhitch name)
        String[] fields = line.split("(\\s)+");

        if (fields.length == 2) {
            String portNameS = fields[0];
            if (fields[1].equalsIgnoreCase("DISCONNECTED_IN")) {
                OSPort pi = new OSPort(portNameS, OSPort.INPUT_PORT);
                //                newOSI.map.put(pi, new OpticalLink(pi, null, null, Double.valueOf(OpticalLink.MIN_QUAL), new Short(OpticalLink.TYPE_HOST), Integer.valueOf(OpticalLink.DISCONNECTED)));
                newOSI.map.put(pi, new OpticalLink(pi, null, null, Double.valueOf(OpticalLink.MIN_QUAL), new Short(
                        OpticalLink.TYPE_HOST), Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
            } else if (fields[1].equalsIgnoreCase("DISCONNECTED_OUT")) {
                OSPort po = new OSPort(portNameS, OSPort.OUTPUT_PORT);
                //                newOSI.map.put(po, new OpticalLink(po, null, null, Double.valueOf(OpticalLink.MIN_QUAL), new Short(OpticalLink.TYPE_HOST), Integer.valueOf(OpticalLink.DISCONNECTED)));
                newOSI.map.put(po, new OpticalLink(po, null, null, Double.valueOf(OpticalLink.MIN_QUAL), new Short(
                        OpticalLink.TYPE_HOST), Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
            } else if (fields[1].equalsIgnoreCase("DISCONNECTED")) {
                OSPort pi = new OSPort(portNameS, OSPort.INPUT_PORT);
                OSPort po = new OSPort(portNameS, OSPort.OUTPUT_PORT);
                //                newOSI.map.put(pi, new OpticalLink(pi, null, null, Double.valueOf(OpticalLink.MIN_QUAL), new Short(OpticalLink.TYPE_HOST), Integer.valueOf(OpticalLink.DISCONNECTED)));
                //                newOSI.map.put(po, new OpticalLink(po, null, null, Double.valueOf(OpticalLink.MIN_QUAL), new Short(OpticalLink.TYPE_HOST), Integer.valueOf(OpticalLink.DISCONNECTED)));
                newOSI.map.put(pi, new OpticalLink(pi, null, null, Double.valueOf(OpticalLink.MIN_QUAL), new Short(
                        OpticalLink.TYPE_HOST), Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
                newOSI.map.put(po, new OpticalLink(po, null, null, Double.valueOf(OpticalLink.MIN_QUAL), new Short(
                        OpticalLink.TYPE_HOST), Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
            } else {
                logger.log(Level.WARNING, "[ Building Connection Map ] Ignoring 2-fields line: \n" + line);
            }

            return;
        }

        if ((fields == null) || (fields.length < 4)) {
            logger.log(Level.WARNING, "[ Building Connection Map ] Ignoring line: \n" + line);
            return;
        }

        if (fields.length < 4) {
            logger.log(Level.WARNING, "[ Building Connection Map ] Ignoring line:\n" + line
                    + "\n Expecting a line with at least 4 fields");
            return;
        }

        String portNameS = fields[0];
        String typeS = fields[1];
        String hostname = fields[2];
        String dPortNameS = (fields.length >= 5) ? fields[4] : null;
        Double quality = null;

        try {
            quality = Double.valueOf(fields[3]);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ Building Connection Map ] Setting MAX_QUAL for line: " + line);
            quality = Double.valueOf(OpticalLink.MAX_QUAL);
        }

        if (quality.doubleValue() < OpticalLink.MIN_QUAL) {
            logger.log(Level.WARNING, "[ Building Connection Map ] Setting MIN_QUAL for line: " + line);
            quality = Double.valueOf(OpticalLink.MIN_QUAL);
        } else if (quality.doubleValue() > OpticalLink.MAX_QUAL) {
            logger.log(Level.WARNING, "[ Building Connection Map ] Setting MAX_QUAL for line: " + line);
            quality = Double.valueOf(OpticalLink.MAX_QUAL);
        }

        if ((portNameS == null) || (portNameS.length() == 0) || (typeS == null) || (typeS.length() == 0)
                || (hostname == null) || (hostname.length() == 0)) {
            logger.log(Level.WARNING, "[ Building Connection Map ] Ignoring line " + line);
            return;
        }

        if (typeS.equalsIgnoreCase("host")) {
            OSPort pi = new OSPort(portNameS, OSPort.INPUT_PORT);
            OSPort po = new OSPort(portNameS, OSPort.OUTPUT_PORT);
            newOSI.map.put(pi, new OpticalLink(pi, hostname, dPortNameS, quality, new Short(OpticalLink.TYPE_HOST),
                    Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
            newOSI.map.put(po, new OpticalLink(po, hostname, dPortNameS, quality, new Short(OpticalLink.TYPE_HOST),
                    Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
        } else if (typeS.equalsIgnoreCase("switch")) {
            OSPort pi = new OSPort(portNameS, OSPort.INPUT_PORT);
            OSPort po = new OSPort(portNameS, OSPort.OUTPUT_PORT);
            newOSI.map.put(pi, new OpticalLink(pi, hostname, dPortNameS, quality, new Short(OpticalLink.TYPE_SWITCH),
                    Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
            newOSI.map.put(po, new OpticalLink(po, hostname, dPortNameS, quality, new Short(OpticalLink.TYPE_SWITCH),
                    Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
        } else if (typeS.equalsIgnoreCase("host_in")) {
            OSPort pi = new OSPort(portNameS, OSPort.INPUT_PORT);
            newOSI.map.put(pi, new OpticalLink(pi, hostname, dPortNameS, quality, new Short(OpticalLink.TYPE_HOST),
                    Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
        } else if (typeS.equalsIgnoreCase("host_out")) {
            OSPort po = new OSPort(portNameS, OSPort.OUTPUT_PORT);
            newOSI.map.put(po, new OpticalLink(po, hostname, dPortNameS, quality, new Short(OpticalLink.TYPE_HOST),
                    Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
        } else if (typeS.equalsIgnoreCase("switch_in")) {
            OSPort pi = new OSPort(portNameS, OSPort.INPUT_PORT);
            newOSI.map.put(pi, new OpticalLink(pi, hostname, dPortNameS, quality, new Short(OpticalLink.TYPE_SWITCH),
                    Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
        } else if (typeS.equalsIgnoreCase("switch_out")) {
            OSPort po = new OSPort(portNameS, OSPort.OUTPUT_PORT);
            newOSI.map.put(po, new OpticalLink(po, hostname, dPortNameS, quality, new Short(OpticalLink.TYPE_SWITCH),
                    Integer.valueOf(OpticalLink.CONNECTED | OpticalLink.FREE)));
        } else {
            logger.log(Level.WARNING, "[ Building Connection Map ] Ignoring line " + line);
            return;
        }
    }

    private static final void parseCrossConnect(String line, OpticalSwitchInfo newOSI) {
        String[] fields = line.split("(\\s)+");
        if ((fields == null) || (fields.length < 2)) {
            logger.log(Level.WARNING, "[ Building CrossConnect Map ] Ignoring line " + line);
            return;
        }

        String sPort = fields[0];
        String dPort = fields[1];

        if ((sPort == null) || (dPort == null)) {
            logger.log(Level.WARNING, "[ Building CrossConnect Map ] Ignoring line " + line);
            return;
        }

        sPort = sPort.trim();
        dPort = dPort.trim();

        OSPort pi = new OSPort(sPort, OSPort.INPUT_PORT);
        OSPort po = new OSPort(dPort, OSPort.OUTPUT_PORT);

        newOSI.crossConnects.put(pi, new OpticalCrossConnectLink(pi, po, Integer.valueOf(OpticalCrossConnectLink.OK)));
    }

    /**
     * Helper function to read a configuration from a Reader 
     * @param r - The reader
     * @return The OpticalSwitchInfo parsed from the reader 
     */
    public static final OpticalSwitchInfo getOpticalSwitchInfo(Reader r) {
        OpticalSwitchInfo newOSI = new OpticalSwitchInfo();
        BufferedReader br = null;
        int state = UNDEFINED;

        try {
            br = new BufferedReader(r);

            for (String line = br.readLine(); line != null; line = br.readLine()) {
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }

                if (line.startsWith("SwitchName")) {
                    String tokens[] = line.split("((\\s)*=(\\s)*)|((\\s)+)");
                    if ((tokens == null) || (tokens.length != 2)) {
                        newOSI.name = "UnKnown";
                    } else {
                        newOSI.name = tokens[1].trim();
                    }
                    continue;
                }

                if (line.startsWith("SwitchType")) {
                    String tokens[] = line.split("((\\s)*=(\\s)*)|((\\s)+)");
                    if ((tokens == null) || (tokens.length != 2)) {
                        newOSI.type = new Short(OpticalSwitchInfo.UNKNOWN);
                    } else {
                        String swType = tokens[1].trim().toLowerCase();
                        if (swType.equals("glimmerglass")) {
                            newOSI.type = new Short(OpticalSwitchInfo.GLIMMERGLASS);
                        } else if (swType.equals("calient")) {
                            newOSI.type = new Short(OpticalSwitchInfo.CALIENT);
                        } else {
                            newOSI.type = new Short(OpticalSwitchInfo.UNKNOWN);
                        }
                    }
                    continue;
                }

                if (line.startsWith("#")) {//comment line
                    if (line.startsWith("#Connection Map")) {//next line will define the connection map
                        state = CONNMAP_PARSE;
                    } else if (line.startsWith("#Cross-Connect Links")) {//next line will define the cross connects
                        state = CROSS_PARSE;
                    }
                    continue;
                }//if - comment

                switch (state) {
                case CONNMAP_PARSE: {
                    parseConnMap(line, newOSI);
                    break;
                }
                case CROSS_PARSE: {
                    parseCrossConnect(line, newOSI);
                    break;
                }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            newOSI = null;
        }

        return newOSI;
    }

}
