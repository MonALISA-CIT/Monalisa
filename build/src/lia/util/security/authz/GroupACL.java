package lia.util.security.authz;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class GroupACL {
    private String sName;
    private String credentials_type = "X509";
    public CopyOnWriteArrayList<AclEntry> lAclEntries;

    public GroupACL(String sName) {
        this.sName = sName;
        this.lAclEntries = new CopyOnWriteArrayList<AclEntry>();

    }

    public void addEntry(AclEntry entry) {
        this.lAclEntries.add(entry);
    }

    // used for bulk refresh
    public void setEntries(CopyOnWriteArrayList<AclEntry> newRules) {
        lAclEntries = newRules;
    }

    public String toString() {
        StringBuilder sbGroup = new StringBuilder();
        sbGroup.append("\n\t").append(sName);
        for (AclEntry e : lAclEntries) {
            sbGroup.append("\n\t").append(e.toString());
        }
        return sbGroup.toString();
    }

    // get only the specified subject permission (chain of permission)
    public Boolean getSubjectPermissions(String subject) {
        Iterator<AclEntry> entries = this.lAclEntries.iterator();

        while (entries.hasNext()) {
            AclEntry r = entries.next();

            if (Pattern.matches(r.subject.replaceAll("\\*", ".*"), subject)) {
                return Boolean.TRUE;
            }
            // r.regExpDestination
        }

        return Boolean.FALSE;

    }

    public String getSName() {
        return this.sName;
    }

    public static void main(String[] args) throws Exception {
    }

}
