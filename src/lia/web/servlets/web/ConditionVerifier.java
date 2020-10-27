package lia.web.servlets.web;

import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author costing
 * @since 2010-09-30
 */
public class ConditionVerifier implements Condition {
	
	private static final class NumericInterval implements Condition {
		private final double min;
		private final double max;
		
		NumericInterval(final double minValue, final double maxValue){
			min = minValue;
			max = maxValue;
		}
		
		@Override
		public boolean matches(final double d){
			if (d>=min && d<=max)
				return true;
			
			return false;
		}
		
		@Override
		public boolean matches(final String s){
			if (s==null)
				return false;
			
			try{
				double d = Double.parseDouble(s.trim());
				
				return matches(d);
			}
			catch (@SuppressWarnings("unused") NumberFormatException nfe){
				return false;
			}
		}
		
		@Override
		public boolean matches(final Object o){
			if (o==null)
				return false;
			
			if (o instanceof String)
				return matches((String) o);
			
			if (o instanceof Number)
				return matches(((Number) o).doubleValue());
			
			return false;
		}
		
		@Override
		public String toString(){
			return "Interval("+min+","+max+")";
		}
	}
	
	private static final class StringMatcher implements Condition{
		private final String expression;
		
		private final Pattern p;
		
		StringMatcher(final String regexp){
			expression = regexp;
			
			p = Pattern.compile(expression);
		}
		
		@Override
		public boolean matches(final double d) {
			try{
				return d == Double.parseDouble(expression);
			}
			catch (@SuppressWarnings("unused") Exception e){
				// return false
			}
			
			return false;
		}

		@Override
		public boolean matches(final String s) {
			if (s==null)
				return false;
			
			final Matcher m = p.matcher(s);
			
			if (m.find())
				return true;
	
			return false;
		}

		@Override
		public boolean matches(final Object o) {
			if (o==null)
				return false;
			
			return matches(o.toString());
		}
		
		@Override
		public String toString(){
			return "String('"+expression+"')";
		}
	}
	
	private static final class Empty implements Condition {	
		Empty(){
			// empty
		}

		@Override
		public boolean matches(final double d) {
			return false;
		}

		@Override
		public boolean matches(final String s) {
			if (s==null || s.length()==0 || s.trim().length()==0)
				return true;
			
			return false;
		}

		@Override
		public boolean matches(final Object o) {
			if (o==null)
				return true;
			
			if (o instanceof String)
				return matches((String) o);
			
			if (o instanceof Number)
				return matches(((Number) o).doubleValue());
			
			return false;
		}
		
		@Override
		public String toString() {
			return "Empty";
		}
	}
	
	private final String originalString;
	
	private final List<Condition> positiveConditions;
	private final List<Condition> negativeConditions;
	
	/**
	 * @param expression expression to verify
	 */
	public ConditionVerifier(final String expression){
		originalString = expression;
		
		positiveConditions = new LinkedList<>();
		negativeConditions = new LinkedList<>();
		
		if (originalString.length()==0)
			return;
		
		final StringTokenizer st = new StringTokenizer(originalString, ";,");
		
		while (st.hasMoreTokens()){
			String sTok = st.nextToken().trim();

			if (sTok.length()==0)
				continue;
			
			boolean bNegate = false;
			
			if (sTok.equals("!")){
				positiveConditions.add(new Empty());
				continue;
			}
			
			if (sTok.equals("*")){
				negativeConditions.add(new Empty());
				continue;
			}
			
			if (sTok.startsWith("!")){
				bNegate = true;
				
				sTok = sTok.substring(1).trim();
			}
			
			// try to match as string
			if (bNegate)
				negativeConditions.add(new StringMatcher(sTok));
			else
				positiveConditions.add(new StringMatcher(sTok));
			
			boolean ok = false;
			
			double d1 = Double.NEGATIVE_INFINITY;
			double d2 = Double.POSITIVE_INFINITY;
			
			int idx = sTok.indexOf(':');

			String s1 = sTok;
			String s2 = sTok;
			
			if (idx>=0){
				s1 = sTok.substring(0, idx).trim();
				s2 = sTok.substring(idx+1).trim();
			}
			
			try{
				d1 = Double.parseDouble(s1);
				ok = true;
			}
			catch (@SuppressWarnings("unused") NumberFormatException nfe){
				// ignore
			}
			
			try{
				d2 = Double.parseDouble(s2);
				ok = true;
			}
			catch (@SuppressWarnings("unused") NumberFormatException nfe){
				// ignore
			}
			
			if (!ok)
				continue;
			
			if (d2<d1){	// swap the min and max
				double dTemp = d1;
				d1 = d2;
				d2 = dTemp;
			}
			
			if (bNegate)
				negativeConditions.add(new NumericInterval(d1, d2));
			else
				positiveConditions.add(new NumericInterval(d1, d2));
		}
	}

	@Override
	public boolean matches(final double d) {
		if (positiveConditions.size()==0 && negativeConditions.size()==0)
			return true;
		
		boolean ok = true;
		
		if (positiveConditions.size()>0){
			ok = false;
			
			for (final Condition c: positiveConditions)
				if (c.matches(d)){
					ok = true;
					continue;
				}
		}
		
		if (!ok)
			return false;
		
		if (negativeConditions.size()>0){
			for (final Condition c: negativeConditions)
				if (c.matches(d))
					return false;
		}
		
		return true;
	}

	@Override
	public boolean matches(final String s) {
		if (positiveConditions.size()==0 && negativeConditions.size()==0)
			return true;
		
		boolean ok = true;
		
		if (positiveConditions.size()>0){
			ok = false;
			
			for (final Condition c: positiveConditions)
				if (c.matches(s)){
					ok = true;
					continue;
				}
		}
		
		if (!ok)
			return false;
		
		if (negativeConditions.size()>0){
			for (final Condition c: negativeConditions)
				if (c.matches(s))
					return false;
		}
		
		return true;
	}

	@Override
	public boolean matches(final Object o) {
		if (positiveConditions.size()==0 && negativeConditions.size()==0)
			return true;
		
		boolean ok = true;
		
		if (positiveConditions.size()>0){
			ok = false;
			
			for (final Condition c: positiveConditions)
				if (c.matches(o)){
					ok = true;
					continue;
				}
		}
		
		if (!ok)
			return false;
		
		if (negativeConditions.size()>0){
			for (final Condition c: negativeConditions)
				if (c.matches(o))
					return false;
		}
		
		return true;
	}
		
	@Override
	public String toString() {
		return "("+positiveConditions.toString()+") && !("+negativeConditions+")";
	}
	
	/**
	 * debugging method
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		ConditionVerifier cv = new ConditionVerifier("1.0");
		
		System.err.println(cv);
		
		System.err.println(cv.matches(0));
		System.err.println(cv.matches("1"));
		System.err.println(cv.matches(1));
		System.err.println(cv.matches("bubu"));
		System.err.println(cv.matches(null));
	}
	
}
