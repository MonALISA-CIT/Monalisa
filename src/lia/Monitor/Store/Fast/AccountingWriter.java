package lia.Monitor.Store.Fast;

import java.util.Vector;

import lia.Monitor.Store.Fast.IDGenerator.KeySplit;
import lia.Monitor.monitor.AccountingResult;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.monPredicate;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 *
 */
public class AccountingWriter extends Writer {

	private long							lastCleanupTime	= 0;

	private long							lTotalTime;

	private long							lCleanupTime;

	private int								writeMode;

	private String							tableName;

	private String							driverString	= AppConfig.getProperty("lia.Monitor.jdbcDriverString", "org.postgresql.Driver").trim();

	/**
	 * @param _sTableName
	 * @param _lTotalTime
	 * @param _iWriteMode
	 */
	public AccountingWriter(String _sTableName, long _lTotalTime, int _iWriteMode) {
		this.lTotalTime = _lTotalTime;
		this.writeMode = _iWriteMode;
		this.tableName = _sTableName;

		lCleanupTime = _lTotalTime / 100;

		if (lCleanupTime < 1000 * 60 * 60)
			lCleanupTime = 1000 * 60 * 60;

		if (_iWriteMode == 9)
			initDBStructure_9();
	}

	@Override
	public void storeData(Object o) {
		if (o instanceof AccountingResult) {
			AccountingResult ar = (AccountingResult) o;

			if (ar.lEndTime - ar.lStartTime < 1000)
				return;

			DB db = new DB();

			for (int i = 0; i < ar.vsParams.size(); i++) {
				Integer id = IDGenerator.getId("#Accounting/" + ar.sGroup + "/" + ar.sUser + "/" + ar.vsParams.get(i));

				if (id == null)
					continue;

				if (writeMode == 9) {
					db.query("INSERT INTO " + tableName + " (id, jobid, value, start_time, end_time) VALUES (" + id + "," + "'" + esc(ar.sJobID) + "', " + ar.vnValues.get(i) + ", " + (ar.lStartTime / 1000) + ", " + (ar.lEndTime / 1000) + ");");
				} else {
					String q = "INSERT INTO " + tableName + "_" + id + " (jobid, value, start_time, end_time) VALUES (" + "'" + esc(ar.sJobID) + "', " + ar.vnValues.get(i) + ", " + (ar.lStartTime / 1000) + ", " + (ar.lEndTime / 1000) + ");";

					if (!db.query(q, true)) {
						initDBStructure_10(id);
						db.query(q);
					}
				}
			}

		}
	}

	@Override
	public long getTotalTime() {
		return lTotalTime;
	}

	@Override
	public boolean cleanup(boolean bCleanHash) {
		long now = NTPDate.currentTimeMillis();

		if (lastCleanupTime + lCleanupTime < now) {
			DB db = new DB();

			if (writeMode == 9) {
				db.query("DELETE FROM " + tableName + " WHERE end_time<" + ((now - lTotalTime) / 1000) + ";");
			}

			lastCleanupTime = now;
		}

		return true;
	}


	@Override
	public int save() {
		return 0;
	}

	private final void initDBStructure_9() {
		DB db = new DB();

		if (driverString.indexOf("mckoi") == -1) {
			boolean bNew = false;

			if (driverString.indexOf("postgres") >= 0)
				bNew = db.syncUpdateQuery("CREATE TABLE " + tableName + " (" + "id integer," + "jobid text," + "value real," + "start_time integer," + "end_time integer" + ") WITHOUT OIDS;", true);
			else
				bNew = db.syncUpdateQuery("CREATE TABLE " + tableName + " (" + "id int," + "jobid varchar(255)," + "value float," + "start_time int," + "end_time int" + ") TYPE=InnoDB;", true);

			if (bNew)
				db.syncUpdateQuery("CREATE INDEX " + tableName + "_idx ON " + tableName + " (id, start_time, end_time);", true);
		} else
			db.syncUpdateQuery("CREATE TABLE " + tableName + " (" + "id int," + "jobid varchar(255)," + "value DOUBLE INDEX_NONE," + "start_time int," + "end_time int" + ") TYPE=InnoDB;", true);
	}

	private final void initDBStructure_10(Integer id) {
		DB db = new DB();

		if (driverString.indexOf("mckoi") == -1) {
			boolean bNew = false;

			if (driverString.indexOf("postgres") >= 0)
				bNew = db.syncUpdateQuery("CREATE TABLE " + tableName + "_" + id + " (" + "jobid text," + "value real," + "start_time integer," + "end_time integer" + ") WITHOUT OIDS;", true);
			else
				bNew = db.syncUpdateQuery("CREATE TABLE " + tableName + "_" + id + " (" + "jobid varchar(255)," + "value float," + "start_time int," + "end_time int" + ") TYPE=InnoDB;", true);

			if (bNew)
				db.syncUpdateQuery("CREATE INDEX " + tableName + "_" + id + "_idx ON " + tableName + "_" + id + " (start_time, end_time);", true);
		} else
			db.syncUpdateQuery("CREATE TABLE " + tableName + "_" + id + " (" + "jobid varchar(255) INDEX_NONE," + "value DOUBLE INDEX_NONE," + "start_time int," + "end_time int" + ") TYPE=InnoDB;", true);
	}

	/**
	 * @param lStart
	 * @param lEnd
	 * @param sParam
	 * @param sGroup
	 * @param sUser
	 * @param sJobID
	 * @return values that match
	 */
	public Vector<Object> getResults(final long lStart, final long lEnd, final String sParam, final String sGroup, final String sUser, final String sJobID) {
		monPredicate pred = new monPredicate("#Accounting", sGroup, sUser, lStart, lEnd, new String[] { sParam }, null);

		Vector<Integer> vIDs = IDGenerator.getIDs(pred);

		if (vIDs == null || vIDs.size() <= 0 || lEnd >= lStart)
			return new Vector<Object>();

		if (writeMode == 9)
			return getResults_9(vIDs, lStart/1000, lEnd/1000, sJobID);
		
		return getResults_10(vIDs, lStart/1000, lEnd/1000, sJobID);
	}

	private Vector<Object> getResults_9(Vector<Integer> vIDs, long lStart, long lEnd, String sJobID) {
		Vector<Object> v = new Vector<Object>();

		StringBuilder sb = new StringBuilder();

		sb.append(vIDs.get(0).toString());

		for (int i = 1; i < vIDs.size(); i++)
			sb.append("," + vIDs.get(i).toString());

		String q = "SELECT * FROM " + tableName + " WHERE " + "id IN (" + sb.toString() + ") AND " + "((start_time>=" + lStart + " AND start_time<=" + lEnd + ") OR " + " (end_time>=" + lStart + " AND end_time<=" + lEnd + "))";

		q += getExpr("jobid", sJobID);

		q += " ORDER BY start_time ASC, end_time ASC, id ASC;";

		DB db = new DB();
		
		db.setReadOnly(true);
		
		db.query(q);

		long l1, l2;

		while (db.moveNext()) {
			l1 = db.getl("start_time") * 1000L;
			l2 = db.getl("end_time") * 1000L;

			if (l1 < lStart)
				l1 = lStart;
			if (l2 > lEnd)
				l2 = lEnd;

			KeySplit split = IDGenerator.getKeySplit(db.geti("id"));

			if (split==null)
				continue;

			if (split.FARM.equals("#Accounting"))
				continue;

			AccountingResult ar = new AccountingResult(split.CLUSTER, split.NODE, db.gets("jobid"), l1, l2);

			ar.addParam(split.FUNCTION, Double.valueOf(db.getd("value")));

			v.add(ar);
		}

		return v;
	}

	private Vector<Object> getResults_10(Vector<Integer> vIDs, long lStart, long lEnd, String sJobID) {
		Vector<Object> v = new Vector<Object>();

		for (int i = 0; i < vIDs.size(); i++) {
			String q = "SELECT * FROM " + tableName + "_" + vIDs.get(i) + " WHERE " + "((start_time>=" + lStart + " AND start_time<=" + lEnd + ") OR " + " (end_time>=" + lStart + " AND end_time<=" + lEnd + "))";

			q += getExpr("jobid", sJobID);

			q += " ORDER BY start_time ASC, end_time ASC;";

			DB db = new DB();
			
			db.setReadOnly(true);
			
			db.query(q);

			long l1, l2;

			while (db.moveNext()) {
				l1 = db.getl("start_time") * 1000L;
				l2 = db.getl("end_time") * 1000L;

				if (l1 < lStart)
					l1 = lStart;
				if (l2 > lEnd)
					l2 = lEnd;

				KeySplit split = IDGenerator.getKeySplit(vIDs.get(i));

				if (split==null)
					continue;

				if (split.FARM.equals("#Accounting"))
					continue;

				AccountingResult ar = new AccountingResult(split.CLUSTER, split.NODE, db.gets("jobid"), l1, l2);

				ar.addParam(split.FUNCTION, Double.valueOf(db.getd("value")));

				v.add(ar);
			}
		}

		return v;
	}

	private static final String getExpr(String sCol, String sExpr) {
		if (sCol == null || sExpr == null || sExpr.length() <= 0 || sExpr.equals("*") || sExpr.equals("%"))
			return "";

		if (sExpr.indexOf("%") >= 0)
			return " AND " + sCol + " LIKE '" + esc(sExpr) + "'";
		
		return " AND " + sCol + "='" + esc(sExpr) + "'";
	}

	/**
	 * Testing
	 * 
	 * @param args
	 */
	public static void main(String args[]) {
		AccountingWriter aw = new AccountingWriter("monitor_accounting", 1000 * 60 * 60 * 24 * 30, 9);

		AccountingResult ar = new AccountingResult("Un grup", "je", "jobu meu", NTPDate.currentTimeMillis() - 1000 * 60 * 60, NTPDate.currentTimeMillis() - 1000 * 60 * 10);
		ar.addParam("o valoare", Integer.valueOf(134));

		aw.addSample(ar);

		Vector<Object> v = aw.getResults(0L, NTPDate.currentTimeMillis(), null, null, null, null);
		System.err.println("1. " + v);

		v = aw.getResults(0L, NTPDate.currentTimeMillis(), "%val%", "Un grup", "je", "%");
		System.err.println("2. " + v);
	}

	@Override
    public String toString(){
    	return "AcountingWriter("+tableName+", "+writeMode+")";
    }
}
