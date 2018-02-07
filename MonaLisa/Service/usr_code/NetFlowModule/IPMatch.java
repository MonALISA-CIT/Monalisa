/**
 * 
 * Internal helper class. Verifies if an IP is from a given class or not.
 * 
 */
public class IPMatch {

	private String ipAddress;

	public IPMatch(String ipAddress) {
		this.ipAddress = ipAddress;
	} // IPMatch

	public String getIpAddress() {
		return ipAddress;
	}

	public boolean equals(Object obj) {

		boolean r = false;

		if (obj == null || ipAddress == null || !(obj instanceof IPMatch))
			return false;

		String otherIP = (((IPMatch) obj).getIpAddress()).trim();

		int index1 = ipAddress.indexOf(".0");
		if (index1 == -1)
			index1 = ipAddress.length();

		int index2 = otherIP.indexOf(".0");
		if (index2 == -1)
			index2 = otherIP.length();

		int index = (index1 < index2) ? index1 : index2;

		if (index == 0)
			return false;

		return (ipAddress.substring(0, index)).equals(otherIP.substring(0,
				index));

	} // equals

	public int hashCode() {
		return 0;
	} // HashCode

	public String toString() {
		return ipAddress;
	} // toString

} // IPMatch
