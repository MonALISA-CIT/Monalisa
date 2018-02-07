/*
 * $Id: TL1Response.java 7049 2011-02-18 18:46:46Z ramiro $
 * Created on Dec 7, 2007
 */
package lia.Monitor.ciena.tl1;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author ramiro
 */
public class TL1Response implements Serializable, Comparable<TL1Response> {

    static final long serialVersionUID = -478074418274196442L;

    public final transient List<String> singleParams;

    public final transient Map<String, String> paramsMap;

    // /
    // This param should be the only one which should be sent over the wire
    // The parsing should be done at the other end
    // /
    private final String line;

    public static final String START_END_DELIMITER = "\"";

    public static final String EQUAL_SIGN = "=";

    private TL1Response(final List<String> singleParams, final Map<String, String> paramsMap, final String line) {
        this.singleParams = singleParams;
        this.paramsMap = paramsMap;
        this.line = line;
    }

    public final String getLine() {
        return line;
    }

    public String toString() {
        return new StringBuilder(1024).append("TL1Response [").append(line).append("] - singleParams").append(singleParams).append(" - paramsMap: ").append(paramsMap).toString();
    }

    public static final TL1Response parseLine(final String line) throws Exception {
        if (line == null) {
            throw new NullPointerException("Line is null");
        }

        String pLine = line.trim();
        if (pLine.length() <= 2) {
            throw new Exception("Line should have at least 3 characters: " + line);
        }

        if (!pLine.startsWith(START_END_DELIMITER) || !pLine.endsWith(START_END_DELIMITER)) {
            throw new Exception("Invalid START_END delimiter for line: " + line);
        }

        pLine = pLine.substring(1, pLine.length() - 1);

        StringTokenizer st = new StringTokenizer(pLine, ",:");

        final ArrayList singleParamsList = new ArrayList();
        final Map paramsMap = new HashMap();

        while (st.hasMoreTokens()) {
            final String tk = st.nextToken().trim();
            if (tk.length() > 0) {
                final int eqIdx = tk.indexOf(EQUAL_SIGN);
                if (eqIdx >= 0) {
                    paramsMap.put(tk.substring(0, eqIdx), tk.substring(eqIdx + 1));
                } else {
                    singleParamsList.add(tk);
                }
            }
        }

        return new TL1Response(singleParamsList, paramsMap, line);
    }

    public boolean equals(Object o) {
        if (o instanceof TL1Response) {
            final TL1Response other = (TL1Response) o;
            return other.line.equals(this.line);
        }
        return false;
    }

    Object readResolve() throws ObjectStreamException {
        try {
            return parseLine(this.line);
        } catch (Exception ex) {
            throw new InvalidObjectException("Unable to parse line: " + this.line + " Exception: " + ex.getCause());
        }
    }

    public int compareTo(TL1Response other) {
        return this.line.compareTo(other.line);
    }

    @Override
    public int hashCode() {
        return this.line.hashCode();
    }
}
