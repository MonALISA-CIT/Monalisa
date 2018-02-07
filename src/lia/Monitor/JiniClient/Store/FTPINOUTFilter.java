package lia.Monitor.JiniClient.Store;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import lia.Monitor.monitor.Result;

/**
 * @author costing
 *
 */
public final class FTPINOUTFilter implements Filter {

	@Override
	public Object filterData(final Object data) {
		if (data == null)
			return null;

		final Vector<Object> vReturn = new Vector<Object>();

		if (data instanceof Result) {
			final Object o = filterData((Result) data);

			if (o != null)
				vReturn.add(o);
		}
		else if (data instanceof Collection) {
			final Iterator<?> it = ((Collection<?>) data).iterator();
			Object o;

			while (it.hasNext()) {
				o = it.next();

				if (o instanceof Result) {
					Object oTemp = filterData((Result) o);

					if (oTemp != null)
						vReturn.add(oTemp);
				}
			}
		}

		return vReturn;
	}

	private Object filterData(final Result result) {
		if (result == null || result.ClusterName == null || !result.ClusterName.equals("osgVO_IO"))
			return null;

		double ftp_in = -1;
		double ftp_out = -1;

		for (int i = 0; i < result.param.length && i < result.param_name.length; i++) {
			if (result.param_name[i].equals("ftpRateIn")) {
				ftp_in = result.param[i];
			}
			else if (result.param_name[i].equals("ftpRateOut")) {
				ftp_out = result.param[i];
			}
		}

		if (ftp_in >= 0 && ftp_out >= 0) {
			final Result r = new Result();

			r.FarmName = result.FarmName;
			r.ClusterName = result.ClusterName;
			r.NodeName = result.NodeName;
			r.time = result.time;

			r.addSet("ftpRateInOut", ftp_in + ftp_out);

			return r;
		}
		return null;
	}

}
