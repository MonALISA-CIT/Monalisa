package lia.Monitor.Agents.OpticalPath.Lease;

public interface LeaseRenewal {
    public boolean renew(Lease lease);
    public boolean renewAll(String sessionID);
}
