/**
 * Helper class for JEP expressions.
 */
package lia.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.web.utils.Formatare;

/**
 * @author costing
 * @since 2006-07-18
 * @version 1.1 2006-01-31 : Added avg() and count() 
 */
public final class JEPHelper {

    private static final Logger logger = Logger.getLogger(JEPHelper.class.getName());

    /**
     * Debug method
     * @param args
     */
    public static void main(String args[]) {
        System.err.println("Evaluate : " + evaluateExpression("round(if(exists(),zero_if_null()==0,-1))"));
        System.err.println("Evaluate : " + evaluateExpression("if(2==2,\"x\", \"y\")"));
        System.err.println("Evaluate : " + evaluateExpression("if(connected(),3,4)"));
        System.err.println("Evaluate : " + evaluateExpression("zero_if_null(avg(15,16))"));
        System.err.println("Rounding : " + evaluateExpression("(round((22414417920/10737418240)*100))/100"));
        System.err.println("Date : " + evaluateExpression("datetime(\"yyyy-MM-dd HH:mm:ss\", now())"));
        System.err.println("Complex : " + evaluateExpression("2241441792<12147483648 && 2241441792/82414417920<0.8"));
    }

    /**
     * Evaluate a JEP expression, where the standard JEP is extended with the following functions:<br>
     * <ul>
     * <li>strlen(String) : integer</li>
     * <li>now() : double, current epoch time, in milliseconds</li>
     * <li>datetime(String, integer) : String, the epoch time (seconds or milliseconds, autodetected) converted to String by the first argument as SimpleDateFormat string</li>
     * <li>exists(list) : 0/1, 0 if the list is empty, 1 if the list is not empty</li>
     * <li>exists_strict(list) : 0/1, 0 if the list is empty or there is no single value &gt;0, 1 if there is at least one value &gt;0</li>
     * <li>zero_if_null(something) : returns the argument if this exists, or 0 if it didn't receive any argument</li>
     * <li>round(number) : String</li>
     * <li>connected() : 0/1, 0 if the repository is not connected to the proxy, 1 if it is connected</li>
     * <li>avg(list) : average of the numbers in the list</li>
     * <li>count(list) : number of parameters in the list<li>
     * <li>striphtml(String) : remove the HTML markups from the given string</li>
     * <li>indexOf(String, String) : position of second string in the first one</li>  
     * </ul>
     * 
     * @param sExpr expression to evaluate
     * @return the result of the function
     */
    public static String evaluateExpression(final String sExpr) {
        final org.nfunk.jep.JEP jep = new org.nfunk.jep.JEP();

        jep.addStandardFunctions();
        jep.addStandardConstants();

        jep.addFunction("strlen", getLengthInstance());
        jep.addFunction("now", getNowInstance());
        jep.addFunction("datetime", getDateTimeInstance());
        jep.addFunction("exists", getExistsInstance());
        jep.addFunction("exists_strict", getExistsStrictInstance());
        jep.addFunction("zero_if_null", getZeroIfNullInstance());
        jep.addFunction("round", getRoundInstance());
        jep.addFunction("connected", getConnectedInstance());
        jep.addFunction("avg", getAverageInstance());
        jep.addFunction("average", getAverageInstance());
        jep.addFunction("count", getCountInstance());
        jep.addFunction("striphtml", getStripHTMLInstance());
        jep.addFunction("indexOf", getIndexOfInstance());

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Expression is : '" + sExpr + "'");
        }

        jep.parseExpression(sExpr);

        final Object o = jep.getValueAsObject();

        if ((o == null) || ((o instanceof Double) && Double.isNaN(((Double) o).doubleValue()))
                || (jep.getErrorInfo() != null)) {
            if (logger.isLoggable(Level.FINE)) {
                try {
                    throw new Exception(sExpr);
                } catch (Exception e) {
                    logger.log(Level.FINE, "Error evaluating '" + sExpr + "' : " + jep.getErrorInfo() + " "
                            + Thread.currentThread().getName(), e);
                }
            } else {
                logger.log(Level.INFO, "Error evaluating '' : " + jep.getErrorInfo()
                        + " (put lia.util.JEPHelper.level=FINE for more details)");
            }

            return "";
        }

        final String sResult = o.toString();

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Result is : '" + sResult + "'");
        }

        return sResult;
    }

    private static Round roundInstance = null;

    private static synchronized Round getRoundInstance() {
        if (roundInstance == null) {
            roundInstance = new Round();
        }

        return roundInstance;
    }

    private static final class Round extends org.nfunk.jep.function.PostfixMathCommand {

        /**
         * This JEP function implements number rounding 
         */
        public Round() {
            numberOfParameters = 1; // exactly one parameter
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run(@SuppressWarnings("rawtypes") final Stack inStack) throws org.nfunk.jep.ParseException {

            checkStack(inStack);

            final Object param = inStack.pop();

            if (param instanceof Number) {
                inStack.push(Long.valueOf(Math.round(((Number) param).doubleValue())));
            } else {
                throw new org.nfunk.jep.ParseException("Invalid parameter type");
            }
        }

    }

    private static StripHTML stripHTMLInstance = null;

    private static synchronized StripHTML getStripHTMLInstance() {
        if (stripHTMLInstance == null) {
            stripHTMLInstance = new StripHTML();
        }

        return stripHTMLInstance;
    }

    private static final class StripHTML extends org.nfunk.jep.function.PostfixMathCommand {

        /**
         * This function gets a single parameter, the string to process, and returns the string stripped of 
         * HTML code.
         */
        public StripHTML() {
            numberOfParameters = 1;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run(@SuppressWarnings("rawtypes") final Stack inStack) throws org.nfunk.jep.ParseException {

            checkStack(inStack);

            final Object param = inStack.pop();

            if (param instanceof String) {
                inStack.push(Formatare.stripHTML((String) param));
            } else {
                throw new org.nfunk.jep.ParseException("Invalid parameter type. Expected String but got "
                        + param.getClass().getName());
            }
        }

    }

    private static Length lengthInstance = null;

    private static synchronized Length getLengthInstance() {
        if (lengthInstance == null) {
            lengthInstance = new Length();
        }

        return lengthInstance;
    }

    private static final class Length extends org.nfunk.jep.function.PostfixMathCommand {

        /**
         * This JEP function implements the length of a string
         */
        public Length() {
            numberOfParameters = 1; // exactly one parameter
        }

        @Override
        @SuppressWarnings("unchecked")
        public void run(@SuppressWarnings("rawtypes") final Stack inStack) throws org.nfunk.jep.ParseException {

            checkStack(inStack);

            final Object param = inStack.pop();

            if (param instanceof String) {
                final int length = ((String) param).length();

                inStack.push(Integer.valueOf(length));
            } else {
                throw new org.nfunk.jep.ParseException("Invalid parameter type. Expected String but got "
                        + param.getClass().getName());
            }
        }

    }

    private static Now nowInstance = null;

    private static synchronized Now getNowInstance() {
        if (nowInstance == null) {
            nowInstance = new Now();
        }

        return nowInstance;
    }

    private static final class Now extends org.nfunk.jep.function.PostfixMathCommand {

        /**
         * This JEP function returns the current epoch time, in millis
         */
        public Now() {
            numberOfParameters = 0; // function with no arguments
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run(@SuppressWarnings("rawtypes") final Stack inStack) throws org.nfunk.jep.ParseException {

            checkStack(inStack);

            inStack.push(Long.valueOf(System.currentTimeMillis()));

        }

    }

    private static Connected connectedInstance = null;

    private static synchronized Connected getConnectedInstance() {
        if (connectedInstance == null) {
            connectedInstance = new Connected();
        }

        return connectedInstance;
    }

    private static final class Connected extends org.nfunk.jep.function.PostfixMathCommand {

        /**
         * This JEP function returns 1 if the repository is connected with the proxy, 0 if not. 
         */
        public Connected() {
            numberOfParameters = 0; // function without any arguments
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run(@SuppressWarnings("rawtypes") final Stack inStack) throws org.nfunk.jep.ParseException {

            checkStack(inStack);

            try {
                final String sProxyIP = lia.Monitor.JiniClient.CommonJini.JiniClient.getProxyIP();

                final boolean bConnected = lia.Monitor.JiniClient.Store.Main.getInstance().verifyProxyConnection();

                inStack.push(Integer.valueOf((sProxyIP != null) && bConnected ? 1 : 0));
            } catch (Throwable t) {
                // this is not a repository
                inStack.push(Integer.valueOf(-1));
            }

        }

    }

    private static IndexOf indexofInstance = null;

    private static synchronized IndexOf getIndexOfInstance() {
        if (indexofInstance == null) {
            indexofInstance = new IndexOf();
        }

        return indexofInstance;
    }

    private static final class IndexOf extends org.nfunk.jep.function.PostfixMathCommand {
        /**
         * Position of substring
         */
        public IndexOf() {
            numberOfParameters = 2;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run(@SuppressWarnings("rawtypes") final Stack inStack) throws org.nfunk.jep.ParseException {
            checkStack(inStack);

            final Object o1 = inStack.pop();

            final Object o2 = inStack.pop();

            if ((o1 == null) || (o2 == null) || !(o1 instanceof String) || !(o2 instanceof String)) {
                throw new org.nfunk.jep.ParseException(
                        "First parameter of datetime() must be the date formatting string");
            }

            final String s1 = (String) o1;
            final String s2 = (String) o2;

            inStack.push(Integer.valueOf(s2.indexOf(s1)));
        }
    }

    private static DateTime dateTimeInstance = null;

    private static synchronized DateTime getDateTimeInstance() {
        if (dateTimeInstance == null) {
            dateTimeInstance = new DateTime();
        }

        return dateTimeInstance;
    }

    private static final class DateTime extends org.nfunk.jep.function.PostfixMathCommand {

        /**
         * This JEP function formats a epoch time with a given formatting string
         */
        public DateTime() {
            numberOfParameters = 2; // exactly 2 arguments. Types will be checked at run time.
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run(@SuppressWarnings("rawtypes") final Stack inStack) throws org.nfunk.jep.ParseException {

            checkStack(inStack);

            final Object param = inStack.pop();

            final Object type = inStack.pop();

            if ((type == null) || !(type instanceof String)) {
                throw new org.nfunk.jep.ParseException(
                        "First parameter of datetime() must be the date formatting string");
            }

            if ((param != null) && (param instanceof Number)) {
                long lTime = ((Number) param).longValue();

                if (lTime < 2000000000) {
                    lTime *= 1000;
                }

                final SimpleDateFormat sdf = new SimpleDateFormat((String) type);

                inStack.push(sdf.format(new Date(lTime)));
            } else {
                throw new org.nfunk.jep.ParseException("The second parameter of datetime() must be the epoch time");
            }
        }
    }

    private static Exists existsInstance = null;

    private static synchronized Exists getExistsInstance() {
        if (existsInstance == null) {
            existsInstance = new Exists(false);
        }

        return existsInstance;
    }

    private static Exists existsStrictInstance = null;

    private static synchronized Exists getExistsStrictInstance() {
        if (existsStrictInstance == null) {
            existsStrictInstance = new Exists(true);
        }

        return existsStrictInstance;
    }

    private static final class Exists extends org.nfunk.jep.function.PostfixMathCommand {

        private final boolean bStrictPositive;

        /**
         * This JEP function returns 1 if either:
         * (bStringPositive==false && number of arguments >= 1) OR
         * (bStringPositive==true && number of arguments >= 1 && any(arguments) > 0) 
         * 
         * @param strictPositive
         */
        public Exists(final boolean strictPositive) {
            numberOfParameters = -1; // any number of arguments

            this.bStrictPositive = strictPositive;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run(@SuppressWarnings("rawtypes") final Stack inStack) throws org.nfunk.jep.ParseException {
            checkStack(inStack);

            int iResult = 0;

            if (curNumberOfParameters >= 1) {
                final Object param = inStack.pop();

                if (bStrictPositive) {
                    if ((param instanceof Number) && (((Number) param).doubleValue() > 1E-20)) {
                        iResult = 1;
                    } else {
                        iResult = 0;
                    }
                } else {
                    iResult = 1;
                }

                for (int i = 0; i < (curNumberOfParameters - 1); i++) {
                    inStack.pop();
                }
            }

            inStack.push(Integer.valueOf(iResult));
        }
    }

    private static ZeroIfNull zeroIfNullInstance = null;

    private static synchronized ZeroIfNull getZeroIfNullInstance() {
        if (zeroIfNullInstance == null) {
            zeroIfNullInstance = new ZeroIfNull();
        }

        return zeroIfNullInstance;
    }

    private static final class ZeroIfNull extends org.nfunk.jep.function.PostfixMathCommand {

        /**
         * This JEP function returns the first argument if it exists, or a "0" if no argument was provided.
         */
        public ZeroIfNull() {
            numberOfParameters = -1; // any number of arguments
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run(@SuppressWarnings("rawtypes") final Stack inStack) throws org.nfunk.jep.ParseException {
            checkStack(inStack);

            double dResult = 0;

            if (curNumberOfParameters >= 1) {
                final Object param = inStack.pop();

                if (param instanceof Number) {
                    dResult = ((Number) param).doubleValue();
                }

                for (int i = 0; i < (curNumberOfParameters - 1); i++) {
                    inStack.pop();
                }
            }

            inStack.push(Double.valueOf(dResult));
        }
    }

    private static Count countInstance = null;

    private static synchronized Count getCountInstance() {
        if (countInstance == null) {
            countInstance = new Count();
        }

        return countInstance;
    }

    private static final class Count extends org.nfunk.jep.function.PostfixMathCommand {

        /**
         * This JEP function returns the number of arguments
         */
        public Count() {
            numberOfParameters = -1; // any number of arguments
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run(@SuppressWarnings("rawtypes") final Stack inStack) throws org.nfunk.jep.ParseException {
            checkStack(inStack);

            for (int i = 0; i < curNumberOfParameters; i++) {
                inStack.pop();
            }

            inStack.push(Long.valueOf(curNumberOfParameters));
        }
    }

    private static Average averageInstance = null;

    private static synchronized Average getAverageInstance() {
        if (averageInstance == null) {
            averageInstance = new Average();
        }

        return averageInstance;
    }

    private static final class Average extends org.nfunk.jep.function.PostfixMathCommand {

        /**
         * This JEP function returns the average value of the arguments
         */
        public Average() {
            numberOfParameters = -1; // any number of arguments
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run(@SuppressWarnings("rawtypes") final Stack inStack) throws org.nfunk.jep.ParseException {
            checkStack(inStack);

            double d = 0;
            int count = 0;

            if (curNumberOfParameters >= 1) {
                for (int i = 0; i < curNumberOfParameters; i++) {
                    final Object param = inStack.pop();

                    if ((param != null) && (param instanceof Number)) {
                        d += ((Number) param).doubleValue();
                        count++;
                    }
                }
            }

            if (count > 0) {
                inStack.push(Double.valueOf(d / count));
            } else {
                inStack.push("");
            }
        }
    }

}
