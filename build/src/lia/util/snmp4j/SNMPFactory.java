package lia.util.snmp4j;

import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.CommunityTarget;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.UserTarget;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.CounterSupport;
import org.snmp4j.mp.DefaultCounterListener;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.mp.StateReference;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivAES192;
import org.snmp4j.security.PrivAES256;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.UnsignedInteger32;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.AbstractTransportMapping;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.PDUFactory;
import org.snmp4j.util.TableListener;
import org.snmp4j.util.TableUtils;
import org.snmp4j.util.ThreadPool;

/** Utility class to be used in order to send and receive SNMP messages using SNMP4J package
 * 
 "Usage:  [options] [transport:]address [OID[={type}value] ...]",
 "",
 "  -a  authProtocol      Sets the authentication protocol used to",
 "                        authenticate SNMPv3 messages. Valid values are",
 "                        MD5 and SHA.",
 "  -A  authPassphrase    Sets the authentication pass phrase for authenticated",
 "                        SNMPv3 messages.",
 "  -c  community         Sets the community for SNMPv1/v2c messages.",
 "  -Ca agentAddress      Sets the agent address field of a V1TRAP PDU.",
 "                        The default value is '0.0.0.0'.",
 "  -Cg genericID         Sets the generic ID for SNMPv1 TRAPs (V1TRAP).",
 "                        The default is 1 (coldStart).",
 "  -Ce enterpriseOID     Sets the enterprise OID field of a V1TRAP PDU.",
 "  -Cil lowerBoundIndex  Sets the lower bound index for TABLE operations.",
 "  -Ciu upperBoundIndex  Sets the upper bound index for TABLE operations.",
 "  -Cn non-repeaters     Sets  the  non-repeaters field for GETBULK PDUs.",
 "                        It specifies the number of supplied variables that",
 "                        should not be iterated over. The default is 0.",
 "  -Cr max-repetitions   Sets the max-repetitions field for GETBULK PDUs.",
 "                        This specifies the maximum number of iterations",
 "                        over the repeating variables. The default is 10.",
 "  -Cs specificID        Sets the specific ID for V1TRAP PDU. The default is 0.",
 "  -Ct trapOID           Sets the trapOID (1.3.6.1.6.3.1.1.4.1.0) of an INFORM",
 "                        or TRAP PDU. The default is 1.3.6.1.6.3.1.1.5.1.",
 "  -Cu upTime            Sets the sysUpTime field of an INFORM, TRAP, or",
 "                        V1TRAP PDU.",
 "  -e  engineID          Sets the authoritative engine ID of the command",
 "                        responder used for SNMPv3 request messages. If not",
 "                        supplied, the engine ID will be discovered.",
 "  -E  contextEngineID   Sets the context engine ID used for the SNMPv3 scoped",
 "                        PDU. The authoritative engine ID will be used for the",
 "                        context engine ID, if the latter is not specified.",
 "  -n  contextName       Sets the target context name for SNMPv3 messages. ",
 "                        Default is the empty string.",
 "  -Ol                   Activates listen operation mode. In this mode, the",
 "                        application will listen for incoming TRAPs and INFORMs",
 "                        on the supplied address. Received request will be",
 "                        sent to a SNMPListener until the application is stopped.",
 "  -Ot                   Activates table operation mode. In this mode, the",
 "                        application receives tabular data from the column",
 "                        OIDs specified as parameters. The retrieved rows will",
 "                        be dumped to the TableListener ordered by their index values.",
 "  -Otd                  Activates dense table operation mode. In this mode, the",
 "                        application receives tabular data from the column",
 "                        OIDs specified as parameters. The retrieved rows will",
 "                        be dumped to the TableListener ordered by their index values.",
 "                        In contrast to -Ot this option must not be used with",
 "                        sparse tables. ",
 "  -Ow                   Activates walk operation mode for GETNEXT and GETBULK",
 "                        PDUs. If activated, the GETNEXT and GETBULK operations",
 "                        will be repeated until all instances within the",
 "                        OID subtree of the supplied OID have been retrieved",
 "                        successfully or until an error occurred.",
 "  -p  pduType           Specifies the PDU type to be used for the message.",
 "                        Valid types are GET, GETNEXT, GETBULK (SNMPv2c/v3),",
 "                        SET, INFORM, TRAP, and V1TRAP (SNMPv1).",
 "  -r  retries           Sets the number of retries used for requests. A zero",
 "                        value will send out a request exactly once.",
 "                        Default is 1.",
 "  -t  timeout           Sets the timeout in milliseconds between retries.",
 "                        Default is 1000 milliseconds.",
 "  -u  securityName      Sets the security name for authenticated v3 messages.",
 "  -v  1|2c|3            Sets the SNMP protocol version to be used.",
 "                        Default is 3.",
 "  -x  privacyProtocol   Sets the privacy protocol to be used to encrypt",
 "                        SNMPv3 messages. Valid values are DES, AES (AES128),",
 "                        AES192, and AES256.",
 "  -X  privacyPassphrase Sets the privacy pass phrase for encrypted",
 "                        SNMPv3 messages.",
 "",
 "The address of the target SNMP engine is parsed according to the",
 "specified <transport> selector (default selector is udp):",
 "",
 "  udp | tcp             hostname[/port]",
 "                        ipv4Address[/port]",
 "                        ipv6Address[/port]",
 "",
 "The OIDs have to be specified in numerical form, for example:",
 "  1.3.6.1.2.1.1.5.0  (which will return the sysName.0 instance with a GET)",
 "To request multiple instances, add additional OIDs with a space as",
 "separator. For the last sub-identifier of a plain OID (without an assigned",
 "value) a range can be specified, for example '1.3.6.1.2.1.2.2.1-10' will",
 "has the same effect as enumerating all OIDs from '1.3.6.1.2.1.2.2.1' to",
 "'1.3.6.1.2.1.2.2.10'.",
 "For SET and INFORM request, you can specify a value for each OID by",
 "using the following form: OID={type}value where <type> is one of",
 "the following single characters enclosed by '{' and '}':",
 "  i                     Integer32",
 "  u                     UnsingedInteger32, Gauge32",
 "  s                     OCTET STRING",
 "  x                     OCTET STRING specified as hex string where",
 "                        bytes separated by colons (':').",
 "  d                     OCTET STRING specified as decimal string",
 "                        where bytes are separated by dots ('.').",
 "  n                     Null",
 "  o                     OBJECT IDENTIFIER",
 "  t                     TimeTicks",
 "  a                     IpAddress",
 "  b                     OCTET STRING specified as binary string where",
 "                        bytes are separated by spaces.",
 "",
 "An example for a complete SNMPv2c SET request to set sysName:",
 " -c private -v 2c -p SET udp:localhost/161 \"1.3.6.1.2.1.1.5.0={s}SNMP4J\"",
 "",
 "To walk the whole MIB tree with GETBULK and using SNMPv3 MD5 authentication:",
 " -a MD5 -A MD5UserAuthPassword -u MD5User -p GETBULK -Ow 127.0.0.1/161",
 "",
 "Listen for unauthenticated SNMPv3 INFORMs and TRAPs and all v1/v2c TRAPs:",
 " -u aSecurityName -Ol 0.0.0.0/162",
 "",
 "Send an unauthenticated SNMPv3 notification (trap):",
 " -p TRAP -v 3 -u aSecurityName 127.0.0.1/162 \"1.3.6.1.2.1.1.3.0={t}0\" \\",
 "  \"1.3.6.1.6.3.1.1.4.1.0={o}1.3.6.1.6.3.1.1.5.1\" \\",
 "  \"1.3.6.1.2.1.1.1.0={s}System XYZ, Version N.M\"",
 "Retrieve rows of the columnar objects ifDescr to ifInOctets and ifOutOctets:",
 " -c public -v 2c -Ot localhost 1.3.6.1.2.1.2.2.1.2-10\\",
 "  1.3.6.1.2.1.2.2.1.16",
 ""
 };
 */
public class SNMPFactory implements PDUFactory {

    private static final Logger logger = Logger.getLogger(SNMPFactory.class.getName());

    public static final int DEFAULT = 0;
    public static final int WALK = 1;
    public static final int LISTEN = 2;
    public static final int TABLE = 3;

    Target target;
    Address address;
    OID authProtocol;
    OID privProtocol;
    OctetString privPassphrase;
    OctetString authPassphrase;
    OctetString community = new OctetString("public");
    OctetString authoritativeEngineID;
    OctetString contextEngineID;
    OctetString contextName = new OctetString();
    OctetString securityName = new OctetString();

    TimeTicks sysUpTime = new TimeTicks(0);
    OID trapOID = SnmpConstants.coldStart;

    PDUv1 v1TrapPDU = new PDUv1();

    int version = SnmpConstants.version3;
    int retries = 1;
    int timeout = 1000;
    int pduType = PDU.GETNEXT;
    int maxRepetitions = 10;
    int nonRepeaters = 0;
    Vector vbs = new Vector();

    protected int operation = DEFAULT;

    int numDispatcherThreads = 2;

    boolean useDenseTableOperation = false;

    // table options
    OID lowerBoundIndex, upperBoundIndex;

    public SNMPFactory() {
    }

    /**
     * Run a specific command... see parameters at top of the source code.
     * If the command is set to listen than a SNMPListener must be suplied
     * If the command is set to table of dense table a TableListener must be suplied
     * @param command The command to execute
     * @param listener
     * @param tableListener
     * @return In case of send returns the response PDUs
     */
    public PDU[] run(String command, SNMPListener listener, TableListener tableListener) {

        if ((command == null) || (command.length() == 0)) {
            return null; // nothing to process here
        }
        String args[] = command.split(" ");
        if ((args == null) || (args.length == 0)) {
            return null; // again nothing to process
        }
        CounterSupport.getInstance().addCounterListener(new DefaultCounterListener());
        int paramStart = parseArgs(args);
        if (paramStart >= args.length) {
            logger.warning("Incorrect snmp string " + command);
            return null;
        }
        address = getAddress(args[paramStart++]);
        vbs = getVariableBindings(args, paramStart);
        checkTrapVariables(vbs);
        checkOptions();
        if (vbs.size() <= 0) {
            logger.warning("Incorrect arguments....");
            return null;
        }
        try {
            if (operation == LISTEN) {
                if (listener != null) {
                    listen(new SNMPCommandResponder(listener));
                } else {
                    logger.warning("No SNMPListener specified");
                }
            } else if (operation == TABLE) {
                table(tableListener);
            } else {
                PDU response[] = send();
                if ((getPduType() == PDU.TRAP) || (getPduType() == PDU.REPORT) || (getPduType() == PDU.V1TRAP)
                        || (getPduType() == PDU.RESPONSE)) {
                    if (logger.isLoggable(Level.FINEST)) {
                        logger.log(Level.FINE, PDU.getTypeString(getPduType()) + " sent successfully");
                    }
                } else if (response == null) {
                    logger.warning("Request timed out.");
                }
                return response;
            }
        } catch (IOException ex) {
            System.err.println("Error while trying to send request: " + ex.getMessage());
            ex.printStackTrace();
        }
        return null;
    }

    /** Utility method to print a PDU response */
    public static void printReport(PDU response) {
        if (response.size() < 1) {
            System.out.println("REPORT PDU does not contain a variable binding.");
            return;
        }

        VariableBinding vb = response.get(0);
        OID oid = vb.getOid();
        if (SnmpConstants.usmStatsUnsupportedSecLevels.equals(oid)) {
            System.out.print("REPORT: Unsupported Security Level.");
        } else if (SnmpConstants.usmStatsNotInTimeWindows.equals(oid)) {
            System.out.print("REPORT: Message not within time window.");
        } else if (SnmpConstants.usmStatsUnknownUserNames.equals(oid)) {
            System.out.print("REPORT: Unknown user name.");
        } else if (SnmpConstants.usmStatsUnknownEngineIDs.equals(oid)) {
            System.out.print("REPORT: Unknown engine id.");
        } else if (SnmpConstants.usmStatsWrongDigests.equals(oid)) {
            System.out.print("REPORT: Wrong digest.");
        } else if (SnmpConstants.usmStatsDecryptionErrors.equals(oid)) {
            System.out.print("REPORT: Decryption error.");
        } else if (SnmpConstants.snmpUnknownSecurityModels.equals(oid)) {
            System.out.print("REPORT: Unknown security model.");
        } else if (SnmpConstants.snmpInvalidMsgs.equals(oid)) {
            System.out.print("REPORT: Invalid message.");
        } else if (SnmpConstants.snmpUnknownPDUHandlers.equals(oid)) {
            System.out.print("REPORT: Unknown PDU handler.");
        } else if (SnmpConstants.snmpUnavailableContexts.equals(oid)) {
            System.out.print("REPORT: Unavailable context.");
        } else if (SnmpConstants.snmpUnknownContexts.equals(oid)) {
            System.out.print("REPORT: Unknown context.");
        } else {
            System.out.print("REPORT contains unknown OID (" + oid.toString() + ").");
        }
        System.out.println(" Current counter value is " + vb.getVariable().toString() + ".");
    }

    /** Utility method to print a PDU response */
    public static String getReport(PDU response) {
        if (response.size() < 1) {
            return "REPORT PDU does not contain a variable binding.";
        }

        VariableBinding vb = response.get(0);
        OID oid = vb.getOid();
        if (SnmpConstants.usmStatsUnsupportedSecLevels.equals(oid)) {
            return "REPORT: Unsupported Security Level.";
        } else if (SnmpConstants.usmStatsNotInTimeWindows.equals(oid)) {
            return "REPORT: Message not within time window.";
        } else if (SnmpConstants.usmStatsUnknownUserNames.equals(oid)) {
            return "REPORT: Unknown user name.";
        } else if (SnmpConstants.usmStatsUnknownEngineIDs.equals(oid)) {
            return "REPORT: Unknown engine id.";
        } else if (SnmpConstants.usmStatsWrongDigests.equals(oid)) {
            return "REPORT: Wrong digest.";
        } else if (SnmpConstants.usmStatsDecryptionErrors.equals(oid)) {
            return "REPORT: Decryption error.";
        } else if (SnmpConstants.snmpUnknownSecurityModels.equals(oid)) {
            return "REPORT: Unknown security model.";
        } else if (SnmpConstants.snmpInvalidMsgs.equals(oid)) {
            return "REPORT: Invalid message.";
        } else if (SnmpConstants.snmpUnknownPDUHandlers.equals(oid)) {
            return "REPORT: Unknown PDU handler.";
        } else if (SnmpConstants.snmpUnavailableContexts.equals(oid)) {
            System.out.print("REPORT: Unavailable context.");
        } else if (SnmpConstants.snmpUnknownContexts.equals(oid)) {
            return "REPORT: Unknown context.";
        } else {
            return "REPORT contains unknown OID (" + oid.toString() + ").";
        }
        return " Current counter value is " + vb.getVariable().toString() + ".";
    }

    /** Utility method to print the variable bindings for a response */
    public static void printVariableBindings(PDU response) {
        for (int i = 0; i < response.size(); i++) {
            VariableBinding vb = response.get(i);
            System.out.println(vb.toString());
        }
    }

    private Snmp createSnmpSession() throws IOException {
        AbstractTransportMapping transport;
        if (address instanceof TcpAddress) {
            transport = new DefaultTcpTransportMapping();
        } else {
            transport = new DefaultUdpTransportMapping();
        }
        // Could save some CPU cycles:
        // transport.setAsyncMsgProcessingSupported(false);
        Snmp snmp = new Snmp(transport);

        if (version == SnmpConstants.version3) {
            USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
            SecurityModels.getInstance().addSecurityModel(usm);
            addUsmUser(snmp);
        }
        return snmp;
    }

    private Target createTarget() {
        if (version == SnmpConstants.version3) {
            UserTarget target = new UserTarget();
            if (authPassphrase != null) {
                if (privPassphrase != null) {
                    target.setSecurityLevel(SecurityLevel.AUTH_PRIV);
                } else {
                    target.setSecurityLevel(SecurityLevel.AUTH_NOPRIV);
                }
            } else {
                target.setSecurityLevel(SecurityLevel.NOAUTH_NOPRIV);
            }
            target.setSecurityName(securityName);
            return target;
        }
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(community);
        return target;
    }

    @Override
    public PDU createPDU(Target target) {
        PDU request;
        if (target.getVersion() == SnmpConstants.version3) {
            request = new ScopedPDU();
            ScopedPDU scopedPDU = (ScopedPDU) request;
            if (contextEngineID != null) {
                scopedPDU.setContextEngineID(contextEngineID);
            }
            if (contextName != null) {
                scopedPDU.setContextName(contextName);
            }
        } else {
            if (pduType == PDU.V1TRAP) {
                request = v1TrapPDU;
            } else {
                request = new PDU();
            }
        }
        request.setType(pduType);
        return request;
    }

    private PDU[] send() throws IOException {
        Snmp snmp = createSnmpSession();
        this.target = createTarget();
        target.setVersion(version);
        target.setAddress(address);
        target.setRetries(retries);
        target.setTimeout(timeout);
        snmp.listen();

        PDU request = createPDU(target);
        if (request.getType() == PDU.GETBULK) {
            request.setMaxRepetitions(maxRepetitions);
            request.setNonRepeaters(nonRepeaters);
        }
        for (int i = 0; i < vbs.size(); i++) {
            request.add((VariableBinding) vbs.get(i));
        }

        PDU response[] = null;
        if (operation == WALK) {
            response = walk(snmp, request, target);
        } else {
            ResponseEvent responseEvent;
            responseEvent = snmp.send(request, target);
            if (responseEvent != null) {
                response = new PDU[] { responseEvent.getResponse() };
            }
        }
        snmp.close();
        return response;
    }

    private PDU[] walk(Snmp snmp, PDU request, Target target) throws IOException {
        request.setNonRepeaters(0);
        OID rootOID = request.get(0).getOid();
        PDU response = null;
        Vector responses = new Vector();
        do {
            ResponseEvent responseEvent = snmp.send(request, target);
            response = responseEvent.getResponse();
            if (response != null) {
                responses.add(response);
            }
        } while (!processWalk(response, request, rootOID));
        PDU ret[] = new PDU[responses.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = (PDU) responses.get(i);
        }
        return ret;
    }

    private boolean processWalk(PDU response, PDU request, OID rootOID) {
        if ((response == null) || (response.getErrorStatus() != 0) || (response.getType() == PDU.REPORT)) {
            return true;
        }
        boolean finished = false;
        OID lastOID = request.get(0).getOid();
        for (int i = 0; (!finished) && (i < response.size()); i++) {
            VariableBinding vb = response.get(i);
            if ((vb.getOid() == null) || (vb.getOid().size() < rootOID.size())
                    || (rootOID.leftMostCompare(rootOID.size(), vb.getOid()) != 0)) {
                finished = true;
            } else if (Null.isExceptionSyntax(vb.getVariable().getSyntax())) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.warning(vb.toString());
                }
                finished = true;
            } else if (vb.getOid().compareTo(lastOID) <= 0) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.warning("Variable received is not lexicographic successor of requested one: "
                            + vb.toString() + " <= " + lastOID);
                }
                finished = true;
            } else {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINE, vb.toString());
                }
                lastOID = vb.getOid();
            }
        }
        if (response.size() == 0) {
            finished = true;
        }
        if (!finished) {
            VariableBinding next = response.get(response.size() - 1);
            next.setVariable(new Null());
            request.set(0, next);
            request.setRequestID(new Integer32(0));
        }
        return finished;
    }

    private void table(TableListener listener) throws IOException {
        Snmp snmp = createSnmpSession();
        this.target = createTarget();
        target.setVersion(version);
        target.setAddress(address);
        target.setRetries(retries);
        target.setTimeout(timeout);
        snmp.listen();

        TableUtils tableUtils = new TableUtils(snmp, this);
        tableUtils.setMaxNumRowsPerPDU(maxRepetitions);

        OID[] columns = new OID[vbs.size()];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = ((VariableBinding) vbs.get(i)).getOid();
        }
        if (useDenseTableOperation) {
            tableUtils.getDenseTable(target, columns, listener, listener, lowerBoundIndex, upperBoundIndex);
        } else {
            tableUtils.getTable(target, columns, listener, listener, lowerBoundIndex, upperBoundIndex);
        }
        snmp.close();
    }

    private void listen(CommandResponder responder) throws IOException {
        AbstractTransportMapping transport;
        if (address instanceof TcpAddress) {
            transport = new DefaultTcpTransportMapping((TcpAddress) address);
        } else {
            transport = new DefaultUdpTransportMapping((UdpAddress) address);
        }
        ThreadPool threadPool = ThreadPool.create("DispatcherPool", numDispatcherThreads);
        MessageDispatcher mtDispatcher = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());

        // add message processing models
        mtDispatcher.addMessageProcessingModel(new MPv1());
        mtDispatcher.addMessageProcessingModel(new MPv2c());
        mtDispatcher.addMessageProcessingModel(new MPv3());

        // add all security protocols
        SecurityProtocols.getInstance().addDefaultProtocols();

        Snmp snmp = new Snmp(mtDispatcher, transport);
        if (version == SnmpConstants.version3) {
            USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
            SecurityModels.getInstance().addSecurityModel(usm);
            if (authoritativeEngineID != null) {
                snmp.setLocalEngine(authoritativeEngineID.getValue(), 0, 0);
            }
            // Add the configured user to the USM
            addUsmUser(snmp);
        } else {
            CommunityTarget target = new CommunityTarget();
            target.setCommunity(community);
            this.target = target;
        }

        snmp.addCommandResponder(responder);

        transport.listen();
        logger.info("SNMP Listening on " + address);
    }

    private void addUsmUser(Snmp snmp) {
        snmp.getUSM().addUser(securityName,
                new UsmUser(securityName, authProtocol, authPassphrase, privProtocol, privPassphrase));
    }

    private String nextOption(String[] args, int position) {
        if ((position + 1) >= args.length) {
            throw new IllegalArgumentException("Missing option value for " + args[position]);
        }
        return args[position + 1];
    }

    private OctetString createOctetString(String s) {
        OctetString octetString;
        if (s.startsWith("0x")) {
            octetString = OctetString.fromHexString(s.substring(2), ':');
        } else {
            octetString = new OctetString(s);
        }
        return octetString;
    }

    private int parseArgs(String[] args) {

        operation = DEFAULT;
        community = new OctetString("public");
        contextName = new OctetString();
        securityName = new OctetString();
        sysUpTime = new TimeTicks(0);
        trapOID = SnmpConstants.coldStart;
        v1TrapPDU = new PDUv1();
        version = SnmpConstants.version3;
        retries = 1;
        timeout = 1000;
        pduType = PDU.GETNEXT;
        maxRepetitions = 10;
        nonRepeaters = 0;
        vbs.clear();
        numDispatcherThreads = 2;
        useDenseTableOperation = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-a")) {
                String s = nextOption(args, i++);
                if (s.equals("MD5")) {
                    authProtocol = AuthMD5.ID;
                } else if (s.equals("SHA")) {
                    authProtocol = AuthSHA.ID;
                } else {
                    throw new IllegalArgumentException("Authentication protocol unsupported: " + s);
                }
            } else if (args[i].equals("-A")) {
                authPassphrase = createOctetString(nextOption(args, i++));
            } else if (args[i].equals("-X") || args[i].equals("-P")) {
                privPassphrase = createOctetString(nextOption(args, i++));
            } else if (args[i].equals("-c")) {
                community = createOctetString(nextOption(args, i++));
            } else if (args[i].equals("-e")) {
                authoritativeEngineID = createOctetString(nextOption(args, i++));
            } else if (args[i].equals("-E")) {
                contextEngineID = createOctetString(nextOption(args, i++));
            } else if (args[i].equals("-n")) {
                contextName = createOctetString(nextOption(args, i++));
            } else if (args[i].equals("-r")) {
                retries = Integer.parseInt(nextOption(args, i++));
            } else if (args[i].equals("-t")) {
                timeout = Integer.parseInt(nextOption(args, i++));
            } else if (args[i].equals("-u")) {
                securityName = createOctetString(nextOption(args, i++));
            } else if (args[i].equals("-Cr")) {
                maxRepetitions = Integer.parseInt(nextOption(args, i++));
            } else if (args[i].equals("-Cn")) {
                nonRepeaters = Integer.parseInt(nextOption(args, i++));
            } else if (args[i].equals("-Ce")) {
                v1TrapPDU.setEnterprise(new OID(nextOption(args, i++)));
            } else if (args[i].equals("-Ct")) {
                trapOID = new OID(nextOption(args, i++));
            } else if (args[i].equals("-Cg")) {
                v1TrapPDU.setGenericTrap(Integer.parseInt(nextOption(args, i++)));
            } else if (args[i].equals("-Cs")) {
                v1TrapPDU.setSpecificTrap(Integer.parseInt(nextOption(args, i++)));
            } else if (args[i].equals("-Ca")) {
                v1TrapPDU.setAgentAddress(new IpAddress(nextOption(args, i++)));
            } else if (args[i].equals("-Cu")) {
                String upTime = nextOption(args, i++);
                v1TrapPDU.setTimestamp(Long.parseLong(upTime));
                sysUpTime.setValue(Long.parseLong(upTime));
            } else if (args[i].equals("-Ow")) {
                operation = WALK;
            } else if (args[i].equals("-Ol")) {
                operation = LISTEN;
            } else if (args[i].equals("-Ot")) {
                operation = TABLE;
            } else if (args[i].equals("-Otd")) {
                operation = TABLE;
                useDenseTableOperation = true;
            } else if (args[i].equals("-Cil")) {
                lowerBoundIndex = new OID(nextOption(args, i++));
            } else if (args[i].equals("-Ciu")) {
                upperBoundIndex = new OID(nextOption(args, i++));
            } else if (args[i].equals("-v")) {
                String v = nextOption(args, i++);
                if (v.equals("1")) {
                    version = SnmpConstants.version1;
                } else if (v.equals("2c")) {
                    version = SnmpConstants.version2c;
                } else if (v.equals("3")) {
                    version = SnmpConstants.version3;
                } else {
                    throw new IllegalArgumentException("Version " + v + " not supported");
                }
            } else if (args[i].equals("-x")) {
                String s = nextOption(args, i++);
                if (s.equals("DES")) {
                    privProtocol = PrivDES.ID;
                } else if ((s.equals("AES128")) || (s.equals("AES"))) {
                    privProtocol = PrivAES128.ID;
                } else if (s.equals("AES192")) {
                    privProtocol = PrivAES192.ID;
                } else if (s.equals("AES256")) {
                    privProtocol = PrivAES256.ID;
                } else {
                    throw new IllegalArgumentException("Privacy protocol " + s + " not supported");
                }
            } else if (args[i].equals("-p")) {
                String s = nextOption(args, i++);
                pduType = PDU.getTypeFromString(s);
                if (pduType == Integer.MIN_VALUE) {
                    throw new IllegalArgumentException("Unknown PDU type " + s);
                }
            } else if (!args[i].startsWith("-")) {
                return i;
            } else {
                throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        return 0;
    }

    private void checkOptions() {
        if ((operation == WALK) && ((pduType != PDU.GETBULK) && (pduType != PDU.GETNEXT))) {
            throw new IllegalArgumentException("Walk operation is not supported for PDU type: "
                    + PDU.getTypeString(pduType));
        } else if ((operation == WALK) && (vbs.size() != 1)) {
            throw new IllegalArgumentException("There must be exactly one OID supplied for walk operations "
                    + vbs.size());
        }
        if ((pduType == PDU.V1TRAP) && (version != SnmpConstants.version1)) {
            throw new IllegalArgumentException("V1TRAP PDU type is only available for SNMP version 1");
        }
    }

    private Address getAddress(String transportAddress) {
        String transport = "udp";
        int colon = transportAddress.indexOf(':');
        if (colon > 0) {
            transport = transportAddress.substring(0, colon);
            transportAddress = transportAddress.substring(colon + 1);
        }
        // set default port
        if (transportAddress.indexOf('/') < 0) {
            transportAddress += "/161";
        }
        if (transport.equalsIgnoreCase("udp")) {
            return new UdpAddress(transportAddress);
        } else if (transport.equalsIgnoreCase("tcp")) {
            return new TcpAddress(transportAddress);
        }
        throw new IllegalArgumentException("Unknown transport " + transport);
    }

    private Vector getVariableBindings(String[] args, int position) {
        Vector v = new Vector((args.length - position) + 1);
        for (int i = position; i < args.length; i++) {
            String oid = args[i];
            char type = 'i';
            String value = null;
            int equal = oid.indexOf("={");
            if (equal > 0) {
                oid = args[i].substring(0, equal);
                type = args[i].charAt(equal + 2);
                value = args[i].substring(args[i].indexOf('}') + 1);
            } else if (oid.indexOf('-') > 0) {
                StringTokenizer st = new StringTokenizer(oid, "-");
                if (st.countTokens() != 2) {
                    throw new IllegalArgumentException("Illegal OID range specified: '" + oid);
                }
                oid = st.nextToken();
                VariableBinding vbLower = new VariableBinding(new OID(oid));
                v.add(vbLower);
                long last = Long.parseLong(st.nextToken());
                long first = vbLower.getOid().lastUnsigned();
                for (long k = first + 1; k <= last; k++) {
                    OID nextOID = new OID(vbLower.getOid().getValue(), 0, vbLower.getOid().size() - 1);
                    nextOID.appendUnsigned(k);
                    VariableBinding next = new VariableBinding(nextOID);
                    v.add(next);
                }
                continue;
            }
            VariableBinding vb = new VariableBinding(new OID(oid));
            if (value != null) {
                Variable variable;
                switch (type) {
                case 'i':
                    variable = new Integer32(Integer.parseInt(value));
                    break;
                case 'u':
                    variable = new UnsignedInteger32(Long.parseLong(value));
                    break;
                case 's':
                    variable = new OctetString(value);
                    break;
                case 'x':
                    variable = OctetString.fromString(value, ':', 16);
                    break;
                case 'd':
                    variable = OctetString.fromString(value, '.', 10);
                    break;
                case 'b':
                    variable = OctetString.fromString(value, ' ', 2);
                    break;
                case 'n':
                    variable = new Null();
                    break;
                case 'o':
                    variable = new OID(value);
                    break;
                case 't':
                    variable = new TimeTicks(Long.parseLong(value));
                    break;
                case 'a':
                    variable = new IpAddress(value);
                    break;
                default:
                    throw new IllegalArgumentException("Variable type " + type + " not supported");
                }
                vb.setVariable(variable);
            }
            v.add(vb);
        }
        return v;
    }

    private void checkTrapVariables(Vector vbs) {
        if ((pduType == PDU.INFORM) || (pduType == PDU.TRAP)) {
            if ((vbs.size() == 0)
                    || ((vbs.size() > 1) && (!((VariableBinding) vbs.get(0)).getOid().equals(SnmpConstants.sysUpTime)))) {
                vbs.add(0, new VariableBinding(SnmpConstants.sysUpTime, sysUpTime));
            }
            if ((vbs.size() == 1)
                    || ((vbs.size() > 2) && (!((VariableBinding) vbs.get(1)).getOid().equals(SnmpConstants.snmpTrapOID)))) {
                vbs.add(1, new VariableBinding(SnmpConstants.snmpTrapOID, trapOID));
            }
        }
    }

    public int getPduType() {
        return pduType;
    }

    public int getVersion() {
        return version;
    }

    public Target getTarget() {
        return target;
    }

    public OctetString getSecurityName() {
        return securityName;
    }

    public int getRetries() {
        return retries;
    }

    public OID getPrivProtocol() {
        return privProtocol;
    }

    public OctetString getPrivPassphrase() {
        return privPassphrase;
    }

    public int getOperation() {
        return operation;
    }

    public OctetString getContextName() {
        return contextName;
    }

    public OctetString getContextEngineID() {
        return contextEngineID;
    }

    public OctetString getCommunity() {
        return community;
    }

    public OctetString getAuthoritativeEngineID() {
        return authoritativeEngineID;
    }

    public OID getAuthProtocol() {
        return authProtocol;
    }

    public OctetString getAuthPassphrase() {
        return authPassphrase;
    }

    class SNMPCommandResponder implements CommandResponder {

        SNMPListener listener;

        public SNMPCommandResponder(SNMPListener listener) {
            this.listener = listener;
        }

        @Override
        public void processPdu(CommandResponderEvent e) {
            PDU command = e.getPDU();
            Address address = e.getPeerAddress();
            if (command != null) {
                listener.newPDU(address, command);
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINE, "New SNMP pdu received: " + command.toString());
                }
                if ((command.getType() != PDU.TRAP) && (command.getType() != PDU.V1TRAP)
                        && (command.getType() != PDU.REPORT) && (command.getType() != PDU.RESPONSE)) {
                    command.setErrorIndex(0);
                    command.setErrorStatus(0);
                    command.setType(PDU.RESPONSE);
                    StatusInformation statusInformation = new StatusInformation();
                    StateReference ref = e.getStateReference();
                    try {
                        e.getMessageDispatcher().returnResponsePdu(e.getMessageProcessingModel(), e.getSecurityModel(),
                                e.getSecurityName(), e.getSecurityLevel(), command, e.getMaxSizeResponsePDU(), ref,
                                statusInformation);
                    } catch (MessageException ex) {
                        logger.warning("Error while sending response: " + ex.getMessage());
                    }
                }
            }
        }
    }

    /** For testing purposes */
    public static void main(String args[]) {

        SNMPFactory factory = new SNMPFactory();
        PDU[] pdu = factory.run("-c private -v 2c -p GETBULK -Ow udp:141.85.99.136/161 1.3.6.1.2.1.2", null, null);
        if (pdu != null) {
            for (PDU element : pdu) {
                System.out.println(element.getErrorIndex() + " " + element.getErrorStatus() + " "
                        + element.getErrorStatusText());
                if (element.getType() == PDU.REPORT) {
                    printReport(element);
                } else {
                    printVariableBindings(element);
                }
                //				System.out.println(pdu[i].toString());
                for (int k = 0; k < element.size(); k++) {
                    VariableBinding vb = element.get(k);
                    if (vb.isException()) {
                        System.out.println("Got a variable exception such as noSuchObject, noSuchInstance or"
                                + "endofMibView");
                        //					System.out.println(vb.getOid().toString());
                    }
                }
            }
        }
        //		System.out.println("Setting");
        //		PDU[] pdu = factory.run("-c public -v 2c -p SET udp:141.85.99.136/161 1.3.6.1.2.1.4.21.1.1.141.85.99.169={s}141.85.99.169", null, null);
        //		if (pdu != null) {
        //			for (int i=0; i<pdu.length; i++) {
        //				System.out.println(pdu[i].getErrorIndex()+" "+pdu[i].getErrorStatus()+" "+pdu[i].getErrorStatusText());
        //				if (pdu[i].getType() == PDU.REPORT)
        //					printReport(pdu[i]);
        //				else
        //					printVariableBindings(pdu[i]);
        ////				System.out.println(pdu[i].toString());
        //				for (int k=0; k<pdu[i].size(); k++) {
        //					VariableBinding vb = pdu[i].get(k);
        //					if (vb.isException()) 
        //							System.out.println("Got a variable exception such as noSuchObject, noSuchInstance or" +
        //									"endofMibView");
        //				}
        //			}
        //		}
    }

} // end of SNMPFactory

