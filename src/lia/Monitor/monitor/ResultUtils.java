/**
 * 
 */
package lia.Monitor.monitor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import lia.Monitor.DataCache.DataSelect;

/**
 * Various result-related utilities
 * 
 * @author costing
 * @since 2007-03-02
 */
public final class ResultUtils {
	
	/**
	 * This method takes an object (Result, eResult, Collection of one of these) through a series of accept and
	 * reject filters. The value is first taken through the accept filters, where only what matches goes further.
	 * Then the result is taken through the reject filters, where whatever matches is discarded. If the accept
	 * list is null or empty, the original object will be kept. If the reject list is null or empty, the value
	 * that makes it to the reject list is by default accepted.  
	 * 
	 * @param o object to filter. Can be null.
	 * @param accept Collection of monPredicate objects. Can be null to accept everything by default.
	 * @param reject Collection of monPredicate objects. Can be null to accept everything by default.
	 * @return the filtered value, of the same type as the original object.
	 */
	public static Object valuesFirewall(final Object o, final Collection<monPredicate> accept, final Collection<monPredicate> reject) {
		if ( o==null || ((accept == null || accept.size()==0) && (reject == null || reject.size()==0)) )
			return o;
		
		if (o instanceof Collection){
			final Vector<Object> vRet = new Vector<Object>();
			
			final Iterator<?> it = ((Collection<?>) o).iterator();
			
			while (it.hasNext()){
				Object oTemp = it.next();
				
				if (oTemp != null){
					oTemp = valuesFirewall(oTemp, accept, reject);
					
					if (oTemp != null)
						vRet.add(oTemp);
				}
			}
		}
		else
		if (o instanceof Result){
			Result r = (Result) o;
			
			return firewallResult(r, accept, reject);
		}
		else
		if (o instanceof eResult){
			eResult r = (eResult) o;
			
			return firewalleResult(r, accept, reject);
		}
		else
		if (o instanceof ExtResult){
			ExtResult r = (ExtResult) o;
			
			return firewallExtResult(r, accept, reject);
		}
		else
		if (o instanceof AccountingResult)
			return o;
		
		return null;
	}
	
	/**
	 * Create a clone of the given Result
	 * 
	 * @param r
	 * @param bCopyParams
	 * @return clone
	 */
	public static Result copyResult(final Result r, final boolean bCopyParams){
		if (r==null)
			return r;
		
		final Result rRet = r instanceof ExtendedResult ? (Result) new ExtendedResult() : new Result();
					
		rRet.FarmName = r.FarmName;
		rRet.ClusterName = r.ClusterName;
		rRet.NodeName = r.NodeName;
		rRet.Module = r.Module;
		rRet.time = r.time;

		if (rRet instanceof ExtendedResult) {
			((ExtendedResult) rRet).max = ((ExtendedResult)r).max;
			((ExtendedResult) rRet).min = ((ExtendedResult)r).min;
		}
		
		if (bCopyParams && r.param_name!=null){
			rRet.param_name = new String[r.param_name.length];
			System.arraycopy(r.param_name, 0, rRet.param_name, 0, r.param_name.length);
			
			if (r.param!=null){
				rRet.param = new double[r.param.length];
				System.arraycopy(r.param, 0, rRet.param, 0, r.param.length);
			}
		}
		
		return rRet;
	}
	
	/**
	 * Create a clone of the given ExtResult
	 * 
	 * @param r
	 * @param bCopyParams
	 * @return clone
	 */
	public static ExtResult copyResult(final ExtResult r, final boolean bCopyParams){
		if (r==null)
			return r;
		
		final ExtResult rRet = new ExtResult();
					
		rRet.FarmName = r.FarmName;
		rRet.ClusterName = r.ClusterName;
		rRet.NodeName = r.NodeName;
		rRet.Module = r.Module;
		rRet.time = r.time;
		rRet.extra = r.extra;
	
		if (bCopyParams){
			if (r.param!=null){
				rRet.param = new double[r.param.length];
				for (int i=0; i<r.param.length; i++)
					rRet.param[i] = r.param[i];
			}
			
			if (r.param_name!=null){
				rRet.param_name = new String[r.param_name.length];
				for (int i=0; i<r.param_name.length; i++)
					rRet.param_name[i] = r.param_name[i];
			}
		}
		
		return rRet;
	}
	
	/**
	 * Create a clone of the given eResult
	 * 
	 * @param r
	 * @param bCopyParams
	 * @return clone
	 */
	public static eResult copyResult(final eResult r, final boolean bCopyParams){
		if (r==null)
			return r;
		
		final eResult rRet = new eResult();
					
		rRet.FarmName = r.FarmName;
		rRet.ClusterName = r.ClusterName;
		rRet.NodeName = r.NodeName;
		rRet.Module = r.Module;
		rRet.time = r.time;
	
		if (bCopyParams){
			if (r.param!=null){
				rRet.param = new Object[r.param.length];
				for (int i=0; i<r.param.length; i++)
					rRet.param[i] = r.param[i];
			}
			
			if (r.param_name!=null){
				rRet.param_name = new String[r.param_name.length];
				for (int i=0; i<r.param_name.length; i++)
					rRet.param_name[i] = r.param_name[i];
			}
		}
		
		return rRet;
	}
	
	/**
	 * Take a Result object through a list of accept and reject predicates
	 * 
	 * @param r
	 * @param accept
	 * @param reject
	 * @return a Result (or null) that matches the given conditions
	 * @see #valuesFirewall(Object, Collection, Collection)
	 */
	public static Result firewallResult(final Result r, final Collection<monPredicate> accept, final Collection<monPredicate> reject){
		if (r==null)
			return r;
		
		final Result rRet = copyResult(r, false);
		final Result rLeft = copyResult(r, true);
		
		int iDeleted = 0;
		
		if (accept!=null && accept.size()>0){
			final Iterator<monPredicate> itAccept = accept.iterator();
			
			while (itAccept.hasNext()){
				final monPredicate pred = itAccept.next();
				
				final Result rTemp = DataSelect.matchResult(rLeft, pred);
				
				if (rTemp!=null){
					if (rTemp.param_name.length == r.param_name.length)
						return r;
					
					for (int i=0; i<rTemp.param_name.length; i++){
						rRet.addSet(rTemp.param_name[i], rTemp.param[i]);
						
						for (int j=0; j<rLeft.param_name.length; j++){
							if (rTemp.param_name[i]!=null && rTemp.param_name[i].equals(rLeft.param_name[j])){
								rLeft.param_name[j] = null;
								iDeleted ++;
							}
						}
					}
		
					// show's over, everything matched already
					if (rRet.param_name.length==r.param_name.length)
						return r;
				}
			}
		}
		
		if (reject!=null && reject.size()>0){
			final Iterator<monPredicate> itReject = reject.iterator();
			
			while (itReject.hasNext()){
				final monPredicate pred = itReject.next();
				
				final Result rTemp = DataSelect.matchResult(rLeft, pred);

				if (rTemp!=null){
					for (int i=0; i<rTemp.param_name.length; i++){
						for (int j=0; j<rLeft.param_name.length; j++){
							if (rTemp.param_name[i]!=null && rTemp.param_name[i].equals(rLeft.param_name[j])){
								rLeft.param_name[j] = null;
								iDeleted++;
								
								// nothing left
								if (iDeleted==r.param_name.length)
									return rRet.param_name!=null && rRet.param_name.length>0 ? rRet : null;
							}
						}
					}
				}
			}
		}
		
		if (rLeft!=null && rLeft.param_name!=null){
			for (int i=0; i<rLeft.param_name.length; i++){
				if (rLeft.param_name[i]!=null)
					rRet.addSet(rLeft.param_name[i], rLeft.param[i]);
			}
		}
		
		return rRet.param_name!=null && rRet.param_name.length>0 ? rRet : null;
	}
	
	
	/**
	 * Take a Result object through a list of accept and reject predicates
	 * 
	 * @param r
	 * @param accept
	 * @param reject
	 * @return a Result (or null) that matches the given conditions
	 * @see #valuesFirewall(Object, Collection, Collection)
	 */
	public static eResult firewalleResult(final eResult r, final Collection<monPredicate> accept, final Collection<monPredicate> reject){
		if (r==null)
			return r;
		
		final eResult rRet = copyResult(r, false);
		final eResult rLeft = copyResult(r, true);
		
		int iDeleted = 0;
		
		if (accept!=null && accept.size()>0){
			final Iterator<monPredicate> itAccept = accept.iterator();
			
			while (itAccept.hasNext()){
				final monPredicate pred = itAccept.next();
				
				final eResult rTemp = DataSelect.matchResult(rLeft, pred);
				
				if (rTemp!=null){
					if (rTemp.param_name.length == r.param_name.length)
						return r;
					
					for (int i=0; i<rTemp.param_name.length; i++){
						rRet.addSet(rTemp.param_name[i], rTemp.param[i]);
						
						for (int j=0; j<rLeft.param_name.length; j++){
							if (rTemp.param_name[i]!=null && rTemp.param_name[i].equals(rLeft.param_name[j])){
								rLeft.param_name[j] = null;
								iDeleted ++;
							}
						}
					}
		
					// show's over, everything matched already
					if (rRet.param_name.length==r.param_name.length)
						return r;
				}
			}
		}
		
		if (reject!=null && reject.size()>0){
			final Iterator<monPredicate> itReject = reject.iterator();
			
			while (itReject.hasNext()){
				final monPredicate pred = itReject.next();
				
				final eResult rTemp = DataSelect.matchResult(rLeft, pred);

				if (rTemp!=null){
					for (int i=0; i<rTemp.param_name.length; i++){
						for (int j=0; j<rLeft.param_name.length; j++){
							if (rTemp.param_name[i]!=null && rTemp.param_name[i].equals(rLeft.param_name[j])){
								rLeft.param_name[j] = null;
								iDeleted++;
								
								// nothing left
								if (iDeleted==r.param_name.length)
									return rRet.param_name!=null && rRet.param_name.length>0 ? rRet : null;
							}
						}
					}
				}
			}
		}
		
		for (int i=0; i<rLeft.param_name.length; i++){
			if (rLeft.param_name[i]!=null)
				rRet.addSet(rLeft.param_name[i], rLeft.param[i]);
		}
		
		return rRet.param_name!=null && rRet.param_name.length>0 ? rRet : null;
	}
	
	/**
	 * Take an ExtResult object through a list of accept and reject predicates
	 * 
	 * @param r
	 * @param accept
	 * @param reject
	 * @return an ExtResult (or null) that matches the given conditions
	 * @see #valuesFirewall(Object, Collection, Collection)
	 */
	public static ExtResult firewallExtResult(final ExtResult r, final Collection<monPredicate> accept, final Collection<monPredicate> reject){
		if (r==null)
			return r;

		final Result rTemp = firewallResult(r.getResult(), accept, reject);
		
		if (rTemp==null)
			return null;
		
		return resultToExtResult(rTemp, r.extra); 
	}
	
	/**
	 * Create a ExtResult from an equivalent Result plus an additional Object
	 * 
	 * @param r
	 * @param obj
	 * @return the equivalent Result
	 */
	public static final ExtResult resultToExtResult(final Result r, final Object obj){
		final ExtResult ret = new ExtResult();
		
		ret.FarmName = r.FarmName;
		ret.ClusterName = r.ClusterName;
		ret.NodeName = r.NodeName;
		ret.Module = r.Module;
		ret.time = r.time;
		ret.param = new double[r.param.length];
		ret.param_name = new String[r.param_name.length];
		
		for (int i=r.param.length-1; i>=0; i--)
			ret.param[i] = r.param[i];
		
		for (int i=r.param_name.length-1; i>=0; i--)
			ret.param_name[i] = r.param_name[i];
		
		ret.extra = obj;
		
		return ret;
	}
	
	/**
	 * Simple test case
	 * 
	 * @param args
	 */
	public static void main(final String args[]){
		final Result r = new Result("F1", "C1", "N1", "Module", new String[]{"p1", "p2", "p3"}, new double[]{5, 10, 15});
		
		final monPredicate p1 = new monPredicate("F1", "C1", "N%", -1, -1, new String[]{"p1"}, null);
		final monPredicate p2 = new monPredicate("F1", "C1", "N%", -1, -1, new String[]{"p2"}, null);
		final monPredicate p3 = new monPredicate("F1", "C1", "N%", -1, -1, new String[]{"p3"}, null);
		
		final monPredicate pstar = new monPredicate("F1", "*", "N1", -1, -1, new String[]{"*"}, null);
		
		final Vector<monPredicate> v1 = new Vector<monPredicate>();
		final Vector<monPredicate> v2 = new Vector<monPredicate>();
		
		v1.add(p1);
		v1.add(p3);
		v2.add(p2);
		v2.add(p3);
		v2.add(pstar);
		System.err.println(valuesFirewall(r, v1, v2));
	}
}
