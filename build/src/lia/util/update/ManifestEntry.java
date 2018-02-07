/*
 * Created on Sep 19, 2010
 */
package lia.util.update;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ramiro
 */
public class ManifestEntry {

    public final String name;

    public final long size;

    public final long lastModified;

    public final String digest;

    public ManifestEntry(final String name, long size, long lastModified, String digest) {
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
        this.digest = digest;
    }

    /**
     * The update line is in the following format:</br>
     * 
     * <pre>
     * ManifestEntry [name=Service/lib/FarmMonitor.jar, size=1595764, lastModified=1140686627000, digest=0a1436f49b41239390c6cc4eb169db32208e457a]
     * </pre>
     * 
     * @param updateLine
     * @return the manifest entry
     * @throws IOException
     */
    public static final ManifestEntry newInstance(final String updateLine) throws IOException {
        final String propStr = updateLine.trim().substring(updateLine.indexOf('[') + 1, updateLine.trim().lastIndexOf("]"));
        final String[] tokens = propStr.split("(\\s)*,(\\s)");
        final Map<String, String> props = new HashMap<String, String>();
        for (final String token : tokens) {
            final String[] keyVal = token.split("(\\s)*=(\\s)*");
            props.put(keyVal[0], keyVal[1]);
        }

        return new ManifestEntry(props.get("name").trim(), Long.parseLong(props.get("size").trim()), Long.parseLong(props.get("lastModified").trim()), props.get("digest").trim());
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "ManifestEntry [name=" + name + ", size=" + size + ", lastModified=" + lastModified + ", digest=" + digest + "]";
    }

}
