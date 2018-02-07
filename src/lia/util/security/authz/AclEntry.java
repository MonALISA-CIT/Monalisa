package lia.util.security.authz;

/**
 * This is the interface used for representing one entry in an Access
 * Control List (ACL).<p>
 *
 * An ACL can be thought of as a data structure with multiple ACL entry
 * objects. Each ACL entry object contains a set of permissions associated
 * with a particular principal. (A principal represents an entity such as
 * an individual user or a group). Additionally, each ACL entry is specified
 * as being either positive or negative. If positive, the permissions are
 * to be granted to the associated principal. If negative, the permissions
 * are to be denied. Each principal can have at most one positive ACL entry
 * and one negative entry; that is, multiple positive or negative ACL
 * entries are not allowed for any principal.
 *
 * Note: ACL entries are positive in this implementation
 */

public class AclEntry {
    String subject;
    
    AclEntry(String subject) {
        this.subject = subject;
        
    }
    
    public String getSubject() {
        return this.subject;
    }
    
    @Override
    public String toString() {
        return this.subject + "::: Authorized";
    }
    
}
