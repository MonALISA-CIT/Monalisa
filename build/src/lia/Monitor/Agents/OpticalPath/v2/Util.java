package lia.Monitor.Agents.OpticalPath.v2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Agents.OpticalPath.OpticalLink;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwLink;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;

/**
 * Utility class used by OpticalPathAgent_v2...
 * 
 */
public final class Util {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Util.class.getName());

    private static final int GENERAL_INFO = 1;
    private static final int CONNMAP_PARSE = 2;
    private static final int CROSS_PARSE = 3;

    /**
     * 
     * @param osi - OpticalSwitchInfo
     * @return A String representation ... same format used in the confFile
     */
    public static final String translateOpticalSwitchInfo(OSwConfig oswConfig) {
        StringBuilder sb = new StringBuilder(16384);
        sb.append("\n\n TO BE DONE !! \n\n");
        return sb.toString();
        //        sb.append("SwitchName = ").append(oswConfig.name).append("\n");
        //        sb.append("SwitchType = ");
        //        
        //        switch(oswConfig.type) {
        //            case OSwConfig.CALIENT: {
        //                sb.append("Calient");
        //                break;
        //            }
        //            case OSwConfig.GLIMMERGLASS: {
        //                sb.append("Glimmerglass");
        //                break;
        //            }
        //            default:{
        //                sb.append("Undefined");
        //                break;
        //            }
        //        }
        //        
        //        sb.append("\n\n#Connection Map");
        //        sb.append("\n#SourcePortName\t\t#LinkType(switch|host)\t#Destination\t#LinkQuality\t#[Destination Port Name]\n");
        //        for (int i=0; i<oswConfig.osPorts.length; i++) {
        //            OSwPort oswPort = oswConfig.osPorts[i];
        //            if(oswPort.oswLink != null) {
        //                
        //            }
        //            OpticalLink ol = (OpticalLink) osi.map.get(key);
        //            sb.append("\n").append(ol.port.name).append("\t");
        //            sb.append("\t").append(ol.getDecodedType()).append("\t");
        //            sb.append("\t").append(ol.destination).append("\t");
        //            sb.append("\t").append(ol.quality);
        //            if(ol.type.shortValue() == OpticalLink.TYPE_SWITCH) {
        //                sb.append("\t").append("\t").append(ol.destinationPortName);
        //            }
        //        }
        //        
        //        sb.append("\n#Cross-Connect Links");
        //        sb.append("\n#SourcePortName\tDestinationPortName\n");
        //        for (Iterator it = osi.crossConnects.entrySet().iterator(); it.hasNext();) {
        //            OpticalCrossConnectLink link = (OpticalCrossConnectLink)it.next();
        //            sb.append("\n").append(link.sPort.name).append("\t").append(link.dPort.name);
        //        }
        //        sb.append("\n");
        //        return sb.toString();
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

    private static final void addOSPortToConfig(OSwPort port, OSwConfig oswConfig, final int lineNo)
            throws ConfigurationParsingException {
        if ((oswConfig.osPorts == null) || (oswConfig.osPorts.length == 0)) {
            oswConfig.osPorts = new OSwPort[1];
            oswConfig.osPorts[0] = port;
            return;
        }

        for (OSwPort osPort : oswConfig.osPorts) {
            if (port.equals(osPort)) {
                throw new ConfigurationParsingException("The OSPort [ " + port + " ] defined at line " + lineNo
                        + " is already defined");
            }
        }

        OSwPort[] tmp = oswConfig.osPorts;
        oswConfig.osPorts = new OSwPort[tmp.length + 1];
        System.arraycopy(tmp, 0, oswConfig.osPorts, 0, tmp.length);
        oswConfig.osPorts[tmp.length] = port;
    }

    private static final void parseConnMap(String line, OSwConfig oswConfig, final int lineNo)
            throws ConfigurationParsingException {
        //the line should have the following format 
        // PortName Type(host or another peer)  Hostname(or swhitch name)
        String[] fields = line.split("(\\s)+");

        if (fields.length == 2) {
            String portNameS = fields[0];
            if (fields[1].equalsIgnoreCase("DISCONNECTED_IN")) {
                OSwPort pi = new OSwPort(portNameS, OSwPort.INPUT_PORT);
                addOSPortToConfig(pi, oswConfig, lineNo);
            } else if (fields[1].equalsIgnoreCase("DISCONNECTED_OUT")) {
                OSwPort po = new OSwPort(portNameS, OSwPort.OUTPUT_PORT);
                addOSPortToConfig(po, oswConfig, lineNo);
            } else if (fields[1].equalsIgnoreCase("DISCONNECTED")) {
                OSwPort pi = new OSwPort(portNameS, OSwPort.INPUT_PORT);
                OSwPort po = new OSwPort(portNameS, OSwPort.OUTPUT_PORT);
                addOSPortToConfig(pi, oswConfig, lineNo);
                addOSPortToConfig(po, oswConfig, lineNo);
            } else {
                throw new ConfigurationParsingException(
                        "[ Building Connection Map ] Parsing exception at 2-field line " + lineNo);
            }
            return;
        }

        if (fields.length < 4) {
            throw new ConfigurationParsingException("[ Building Connection Map ] Expecting at least 4 fields at line "
                    + lineNo);
        }

        String portNameS = fields[0];
        if ((portNameS == null) || (portNameS.length() == 0)) {
            throw new ConfigurationParsingException(
                    "[ Building Connection Map ] The Source Port Name is null or not defined at line " + lineNo);
        }

        String typeS = fields[1];
        if ((typeS == null) || (typeS.length() == 0)) {
            throw new ConfigurationParsingException("[ Building Connection Map ] The type for the port " + portNameS
                    + " is null or not defined at line " + lineNo);
        }

        String endPointName = fields[2];
        if ((endPointName == null) || (endPointName.length() == 0)) {
            throw new ConfigurationParsingException(
                    "[ Building Connection Map ] The End Point name is null or not defined for port " + portNameS
                            + " at line " + lineNo);
        }

        double quality = OpticalLink.MIN_QUAL;

        try {
            quality = Double.parseDouble(fields[3]);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ Building Connection Map ] got exception parsing quality at line " + lineNo
                    + ".", t);
            throw new ConfigurationParsingException(t);
        }

        if ((quality < OpticalLink.MIN_QUAL) || (quality > OpticalLink.MAX_QUAL)) {
            throw new ConfigurationParsingException("[ Building Connection Map ] Wrong quality parameter at line "
                    + lineNo);
        }

        String dPortNameS = (fields.length >= 5) ? fields[4] : null;

        OSwPort pi = null;
        OSwPort po = null;
        if (typeS.equalsIgnoreCase("host")) {
            pi = new OSwPort(portNameS, OSwPort.INPUT_PORT);
            po = new OSwPort(portNameS, OSwPort.OUTPUT_PORT);
            po.oswLink = new OSwLink(endPointName, dPortNameS, quality, OSwLink.TYPE_HOST);

            OSwLink[] links = (OSwLink[]) oswConfig.localHosts.get(endPointName);
            if (links == null) {
                links = new OSwLink[1];
            } else {
                OSwLink[] tLinks = new OSwLink[links.length + 1];
                System.arraycopy(links, 0, tLinks, 0, links.length);
                links = tLinks;
            }
            links[links.length - 1] = new OSwLink(oswConfig.name, portNameS, quality, OSwLink.TYPE_SWITCH);
            oswConfig.localHosts.put(endPointName, links);
        } else if (typeS.equalsIgnoreCase("switch")) {
            pi = new OSwPort(portNameS, OSwPort.INPUT_PORT);
            po = new OSwPort(portNameS, OSwPort.OUTPUT_PORT);
            po.oswLink = new OSwLink(endPointName, dPortNameS, quality, OSwLink.TYPE_SWITCH);
        } else if (typeS.equalsIgnoreCase("host_in")) {
            pi = new OSwPort(portNameS, OSwPort.INPUT_PORT);
            OSwLink[] links = (OSwLink[]) oswConfig.localHosts.get(endPointName);
            if (links == null) {
                links = new OSwLink[1];
            } else {
                OSwLink[] tLinks = new OSwLink[links.length + 1];
                System.arraycopy(links, 0, tLinks, 0, links.length);
                links = tLinks;
            }
            links[links.length - 1] = new OSwLink(oswConfig.name, portNameS, quality, OSwLink.TYPE_SWITCH);
            oswConfig.localHosts.put(endPointName, links);
        } else if (typeS.equalsIgnoreCase("host_out")) {
            po = new OSwPort(portNameS, OSwPort.OUTPUT_PORT);
            po.oswLink = new OSwLink(endPointName, dPortNameS, quality, OSwLink.TYPE_HOST);
        } else if (typeS.equalsIgnoreCase("switch_in")) {
            pi = new OSwPort(portNameS, OSwPort.INPUT_PORT);
        } else if (typeS.equalsIgnoreCase("switch_out")) {
            po = new OSwPort(portNameS, OSwPort.OUTPUT_PORT);
            po.oswLink = new OSwLink(endPointName, dPortNameS, quality, OSwLink.TYPE_SWITCH);
        } else {
            throw new ConfigurationParsingException(
                    "[ Building Connection Map ] The type parameter cannot be understand at line " + lineNo);
        }

        if (pi != null) {
            pi.fiberState = OSwPort.FIBER;
            pi.powerState = OSwPort.UNKLIGHT;
            addOSPortToConfig(pi, oswConfig, lineNo);
        }

        if (po != null) {
            po.fiberState = OSwPort.FIBER;
            po.powerState = OSwPort.UNKLIGHT;
            addOSPortToConfig(po, oswConfig, lineNo);
        }
    }

    private static final void parseCrossConnect(String line, OSwConfig oswConfig, final int lineNo) {
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

        OSwPort pi = new OSwPort(sPort, OSwPort.INPUT_PORT);
        OSwPort po = new OSwPort(dPort, OSwPort.OUTPUT_PORT);

    }

    /**
     * Helper function to read a configuration from a Reader 
     * @param r - The reader
     * @return The OpticalSwitchInfo parsed from the reader 
     */
    public static final OSwConfig getOpticalSwitchConfig(Reader r) throws ConfigurationParsingException {
        OSwConfig oswConfig = new OSwConfig();
        BufferedReader br = null;
        int state = GENERAL_INFO;

        int lineNo = 0;
        try {
            br = new BufferedReader(r);

            for (String line = br.readLine(); line != null; line = br.readLine()) {
                lineNo++;
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }

                if (line.startsWith("SwitchName")) {
                    String tokens[] = line.split("((\\s)*=(\\s)*)|((\\s)+)");
                    if ((tokens == null) || (tokens.length != 2)) {
                        throw new ConfigurationParsingException(
                                "SwitchName parameter is not ( correctly ) defined at line " + lineNo + ".");
                    }

                    oswConfig.name = tokens[1].trim();
                    continue;
                }

                if (line.startsWith("SwitchType")) {
                    String tokens[] = line.split("((\\s)*=(\\s)*)|((\\s)+)");
                    if ((tokens == null) || (tokens.length != 2)) {
                        throw new ConfigurationParsingException(
                                "SwitchType parameter is not ( correctly ) defined at line " + lineNo + ".");
                    }

                    String swType = tokens[1].trim();
                    if (swType.equalsIgnoreCase("glimmerglass")) {
                        oswConfig.type = OSwConfig.GLIMMERGLASS;
                    } else if (swType.equalsIgnoreCase("calient")) {
                        oswConfig.type = OSwConfig.CALIENT;
                    } else {
                        throw new ConfigurationParsingException(
                                "SwitchType parameter is not ( correctly ) defined at line " + lineNo + ".");
                    }

                    continue;
                }

                if (line.startsWith("#")) {//comment line
                    if (line.startsWith("#Connection Map")) {//next line will define the connection map
                        if (state == GENERAL_INFO) {
                            if ((oswConfig.type == OSwConfig.GLIMMERGLASS) || (oswConfig.type == OSwConfig.CALIENT)) {
                                if ((oswConfig.name != null) && (oswConfig.name.length() != 0)) {
                                    //OK
                                    state = CONNMAP_PARSE;
                                } else {
                                    throw new ConfigurationParsingException(
                                            "State Parsing Exception: The \"#Connection Map\" section started but the switch name is not defined!");
                                }
                            } else {
                                throw new ConfigurationParsingException(
                                        "State Parsing Exception: The \"#Connection Map\" section started but the switch type is not defined or is not a known switch type!");
                            }
                        } else {
                            throw new ConfigurationParsingException(
                                    "State Parsing Exception: The \"#Connection Map\" section must be defined after the switch name and switch type");
                        }
                    } else if (line.startsWith("#Cross-Connect Links")) {//next line will define the cross connects
                        if (state == CONNMAP_PARSE) {
                            //OK
                            state = CROSS_PARSE;
                        } else {
                            throw new ConfigurationParsingException(
                                    "State Parsing Exception: The \"#Cross-Connect Links\" section must be defined after the \"#Connection Map\" section");
                        }
                    }
                    continue;
                }//if - comment

                switch (state) {
                case CONNMAP_PARSE: {
                    parseConnMap(line, oswConfig, lineNo);
                    break;
                }
                case CROSS_PARSE: {
                    parseCrossConnect(line, oswConfig, lineNo);
                    break;
                }
                }
            }

        } catch (IOException ioe) {
            logger.log(Level.WARNING, " Got IOException parsing configuration ", ioe);
            throw new ConfigurationParsingException(ioe);
        } catch (ConfigurationParsingException cpe) {
            throw cpe;
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got general exception parsing configuration ", t);
            throw new ConfigurationParsingException(t);
        }

        return oswConfig;
    }

}
