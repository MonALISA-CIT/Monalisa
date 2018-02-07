/**
 * 
 */
package lia.util.actions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.monitor.monPredicate;
import lia.util.DateFileWatchdog;
import lia.util.MLProperties;
import lia.util.ntp.NTPDate;

/**
 * @author costing
 * @since 2006
 */
public final class Action implements Observer {

    /** package protected for better access from inside class */
    private static final Logger logger = Logger.getLogger(Action.class.getName());

    private ArrayList<SeriesState> alSeries;

    private ArrayList<ActionTaker> alActions;

    private long period;

    private long lLastCheck = 0;

    private long lSeriesReloadPeriod;

    private long lLastSeriesReload = 0;

    private monPredicate trigger;

    /**
     * After how many consecutive errors an action will be taken
     */
    int iErrorThreshold = 0;

    /**
     * After how many consecutive success states an action will be triggered
     */
    int iSuccessThreshold = 0;

    /**
     * After how many alternating states an flip-flop action will be taken
     */
    int iFlipFlopThreshold = 0;

    /**
     * How long is the state history
     */
    int iHistoryLength = 0;

    /**
     * After how many consecutive errors should we rearm and try again?
     */
    int iRearmIterations = 0;

    /**
     * Configuration properties
     */
    MLProperties mlp;

    /**
     * File that backes the properties
     */
    final String sFile;

    private boolean bIgnoreMissingData = false;

    private DecisionTaker decisionClass = null;

    /**
     * This internal object holds the history for one of the series
     * 
     * @author costing
     * @since 2006
     */
    public final class SeriesState implements Comparable<SeriesState> {

        /**
         * All the names that compose this series
         */
        final ArrayList<String> alSeriesNames;

        /**
         * History of the states
         */
        final LinkedList<Boolean> llHistory;

        /**
         * History of the actual values
         */
        final LinkedList<String> llValuesHist;

        /**
         * Current state for this series
         */
        int iState = ActionUtils.STATE_OK;

        /**
         * Last value that was the base for a decision
         */
        String sValue;

        /**
         * How many consecutive times this series was in error. Used to check if {@link #rearm()} has to be called.
         */
        long lConsecutiveErrors = 0;

        /**
         * Flag to indicate whether or not this is a rearmed action or not
         */
        boolean bRearmed = false;

        /**
         * The constructor
         * 
         * @param _alSeriesNames
         */
        SeriesState(final ArrayList<String> _alSeriesNames) {
            this.alSeriesNames = _alSeriesNames;

            this.llHistory = new LinkedList<Boolean>();
            this.llValuesHist = new LinkedList<String>();
        }

        /**
         * Get the current status of this series.
         * 
         * @return one of the {@link ActionUtils#STATE_OK}, {@link ActionUtils#STATE_ERR}, {@link ActionUtils#STATE_FLIPFLOP}
         */
        public int getState() {
            return this.iState;
        }

        /**
         * Go back to a OK state to re-trigger the error condition. 
         */
        void rearm() {
            this.iState = ActionUtils.STATE_OK;
            this.bRearmed = true;
        }

        /**
         * Clear the rearm flag, after taking the action 
         */
        void clearRearm() {
            this.bRearmed = false;
        }

        /**
         * Get the rearming status. This way an action can check if it's the first time when the situation
         * occurs or a subsequent one.
         * 
         * @return false if it's the first action, true if it's a subsequent one.
         */
        public boolean isRearmedAction() {
            return this.bRearmed;
        }

        /**
         * Get the number of consecutive errors.
         * 
         * @see #rearm()
         * 
         * @return number of consecutive errors
         */
        long getConsecutiveErrors() {
            return this.lConsecutiveErrors;
        }

        /**
         * Create a copy of the series names. Used to create all the possible combinations
         * between multiple levels of series names.
         * 
         * @param cSubSeriesNames
         * @return an ArrayList that has the same values as the original collection
         */
        ArrayList<SeriesState> clone(final Collection<String> cSubSeriesNames) {
            final ArrayList<SeriesState> alSubSeries = new ArrayList<SeriesState>();

            if ((cSubSeriesNames == null) || (cSubSeriesNames.size() == 0)) {
                this.alSeriesNames.add(null);
                alSubSeries.add(this);
            } else {
                final Iterator<String> it = cSubSeriesNames.iterator();
                while (it.hasNext()) {
                    final String sName = it.next();

                    final ArrayList<String> alTemp = new ArrayList<String>(this.alSeriesNames);
                    alTemp.add(sName);

                    alSubSeries.add(new SeriesState(alTemp));
                }
            }

            return alSubSeries;
        }

        @Override
        public String toString() {
            return "FILE: " + Action.this.sFile + ", NAMES: " + this.alSeriesNames + ", STATE: " + this.iState
                    + ", HISTORY: " + this.llHistory;
        }

        /**
         * @return the configuration file name
         */
        public String getFile() {
            return Action.this.sFile;
        }

        /**
         * Event trigger
         * 
         * @param bOk the new state
         * @param sNewValue the new value
         */
        void event(final boolean bOk, final String sNewValue) {
            this.llHistory.addFirst(Boolean.valueOf(bOk));
            this.llValuesHist.addFirst(sNewValue != null ? sNewValue : "");

            while (this.llHistory.size() > Action.this.iHistoryLength) {
                this.llHistory.removeLast();
                this.llValuesHist.removeLast();
            }

            if (!bOk && (this.iState == ActionUtils.STATE_ERR) && (Action.this.iRearmIterations > 0)) {
                this.lConsecutiveErrors++;
            } else {
                this.lConsecutiveErrors = 0;
            }

            if ((bOk && (this.iState == ActionUtils.STATE_OK)) || (!bOk && (this.iState == ActionUtils.STATE_ERR))) {
                // nothing has changed, don't go further
                return;
            }

            this.sValue = sNewValue;

            // check if the situation has stabilized in either OK or ERROR states
            Iterator<Boolean> it = this.llHistory.iterator();

            int iCount = 0;

            while (it.hasNext()) {
                final boolean b = it.next().booleanValue();

                if (b != bOk) {
                    break;
                }

                iCount++;

                if ((bOk && (iCount >= Action.this.iSuccessThreshold))
                        || (!bOk && (iCount >= Action.this.iErrorThreshold))) {
                    this.iState = bOk ? ActionUtils.STATE_OK : ActionUtils.STATE_ERR;
                    takeAction(this);
                    return;
                }
            }

            if (this.iState == ActionUtils.STATE_FLIPFLOP) {
                return;
            }

            it = this.llHistory.iterator();

            boolean bPrev = bOk;

            iCount = 0;

            while (it.hasNext()) {
                final boolean b = it.next().booleanValue();

                if (b != bPrev) {
                    iCount++;
                }

                if (iCount >= Action.this.iFlipFlopThreshold) {
                    break;
                }

                bPrev = b;
            }

            if ((iCount >= Action.this.iFlipFlopThreshold) && (Action.this.iFlipFlopThreshold > 0)) {
                this.iState = ActionUtils.STATE_FLIPFLOP;
                takeAction(this);
            }
        }

        /**
         * Initialize the state with the last known value
         */
        void initState() {
            this.iState = Action.this.mlp.geti(getKey() + ".state", this.iState);
        }

        /**
         * Construct a unique key from all the names that compose this series name
         * 
         * @return the key
         */
        public String getKey() {
            final StringBuilder sb = new StringBuilder();

            for (int i = 0; i < this.alSeriesNames.size(); i++) {
                if (i > 0) {
                    sb.append('.');
                }

                final String sName = this.alSeriesNames.get(i);

                if (sName != null) {
                    sb.append(sName);
                }
            }

            return sb.toString();
        }

        /**
         * Get the names count
         * 
         * @return names count
         * @see #get(int)
         */
        public int size() {
            return this.alSeriesNames.size();
        }

        /**
         * Get the name on a given position
         * 
         * @param i index
         * @return the name on this position
         * @see #size()
         */
        public String get(final int i) {
            return this.alSeriesNames.get(i);
        }

        @Override
        public int compareTo(final SeriesState o) {
            return getKey().compareTo(o.getKey());
        }

        @Override
        public boolean equals(final Object o) {
            return (o != null) && (o instanceof SeriesState) ? compareTo((SeriesState) o) == 0 : false;
        }

        @Override
        public int hashCode() {
            return getKey().hashCode();
        }

    }

    private ArrayList<SeriesState> getSeries() {
        ArrayList<SeriesState> alRet = new ArrayList<SeriesState>();

        alRet.add(new SeriesState(new ArrayList<String>()));

        final int iSeriesCount = this.mlp.geti("series.count", 0);

        for (int i = 0; i < iSeriesCount; i++) {
            final Vector<String> v = this.mlp.toVector("series." + i);

            final ArrayList<SeriesState> alSeriesTemp = new ArrayList<SeriesState>(alRet.size()
                    * ((v != null) && (v.size() > 0) ? v.size() : 1));

            for (int j = 0; j < alRet.size(); j++) {
                final SeriesState ss = alRet.get(j);

                alSeriesTemp.addAll(ss.clone(v));
            }

            alRet = alSeriesTemp;
        }

        if (alRet.size() == 1) {
            final SeriesState ss = alRet.get(0);

            if ((ss.alSeriesNames.size() == 0) || ((ss.alSeriesNames.size() == 1) && (ss.alSeriesNames.get(0) == null))) {
                alRet.clear();
            }
        }

        return alRet;
    }

    private DateFileWatchdog dfw = null;

    /**
     * Initialize an Action based on a properties object
     * 
     * @param prop
     */
    public Action(final MLProperties prop) {
        this.mlp = prop;

        this.sFile = prop.gets("properties_file");

        reload();
    }

    private void reloadSeries() {
        this.mlp.clearCache();

        final ArrayList<SeriesState> alTemp = getSeries();

        final TreeSet<SeriesState> tsOld = new TreeSet<SeriesState>(this.alSeries);

        tsOld.removeAll(alTemp);

        if (tsOld.size() > 0) {
            final Iterator<SeriesState> it = tsOld.iterator();

            while (it.hasNext()) {
                final SeriesState ss = it.next();

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Removing : " + ss);
                }

                this.alSeries.remove(ss);
            }
        }

        final TreeSet<SeriesState> tsNew = new TreeSet<SeriesState>(alTemp);

        tsNew.removeAll(this.alSeries);

        if (tsNew.size() > 0) {
            final Iterator<SeriesState> it = tsNew.iterator();

            while (it.hasNext()) {
                final SeriesState ss = it.next();

                ss.initState();

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Adding : " + ss);
                }

                this.alSeries.add(ss);
            }
        }
    }

    private void reload() {
        setStatus(this.mlp);

        final long lReload = this.mlp.getl("reload_properties", 10 * 1000);

        if (this.dfw != null) {
            this.dfw.stopIt();
            this.dfw = null;
        }

        if ((lReload > 0) && (this.mlp.getConfigFileName() != null)) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Watching " + this.mlp.getConfigFileName() + " each " + lReload + " ms");
            }

            try {
                this.dfw = DateFileWatchdog.getInstance(this.mlp.getConfigFileName(), lReload);

                this.dfw.addObserver(this);
            } catch (Exception e) {
                this.dfw = null;
            }
        }

        // check if there is a decision taking class defined
        String sDecisionClass = this.mlp.gets("rule.class");

        try {
            if ((sDecisionClass != null) && (sDecisionClass.length() > 0)) {
                this.decisionClass = (DecisionTaker) Class.forName(sDecisionClass).newInstance();
            } else {
                this.decisionClass = new DefaultDecisionTaker();
            }

            this.decisionClass.init(this.mlp);

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Decision class is : " + this.decisionClass.getClass().getName());
            }
        } catch (Throwable t) {
            logger.log(Level.INFO, "Disabing " + this.mlp.getConfigFileName()
                    + " because a rule could not be instantiated (class=" + sDecisionClass + ")", t);
        }

        this.alSeries = getSeries();

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Series: " + this.alSeries);
        }

        final int iActionsCount = this.mlp.geti("actions.count", 0);

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Actions count: " + iActionsCount);
        }

        this.alActions = new ArrayList<ActionTaker>(iActionsCount);

        for (int i = 0; i < iActionsCount; i++) {
            final String sClass = this.mlp.gets("action." + i + ".class");

            if (sClass.length() > 0) {
                try {
                    final ActionTaker at = (ActionTaker) Class.forName(sClass).newInstance();

                    at.initAction(i, this.mlp);

                    this.alActions.add(at);

                    if (logger.isLoggable(Level.FINER)) {
                        logger.log(Level.FINER, "Action class: " + sClass);
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Cannot instantiate action class: " + sClass, t);
                }

                continue;
            }

            try {
                final StandardActions ae = new StandardActions();

                ae.initAction(i, this.mlp);

                this.alActions.add(ae);

                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "Action: " + ae.toString());
                }
            } catch (Throwable e) {
                logger.log(Level.WARNING,
                        "Could not parse action " + i + " (" + this.sFile + ") : " + e + " (" + e.getMessage() + ")");
            }
        }

        if (((this.alSeries.size() == 1) && (this.alSeries.get(0).alSeriesNames.size() == 0))
                || (this.alActions.size() == 0)) {
            logger.log(Level.INFO, "Disabing " + this.mlp.getConfigFileName() + " because series count="
                    + this.alSeries.size() + ", actions count=" + this.alActions.size());

            return;
        }

        this.period = (long) (this.mlp.getd("period", 0d) * 1000d);

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Period: " + this.period);
        }

        this.trigger = MLProperties.toPred(this.mlp.gets("trigger", null));

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Trigger: " + this.trigger);
        }

        if (this.period > 0) {
            this.lLastCheck = NTPDate.currentTimeMillis();
        }

        this.iSuccessThreshold = this.mlp.geti("threshold.success", 3);
        this.iErrorThreshold = this.mlp.geti("threshold.error", 3);
        this.iFlipFlopThreshold = this.mlp.geti("threshold.flip_flop", 0);

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Thresholds: success=" + this.iSuccessThreshold + ", error=" + this.iErrorThreshold
                    + ", flipflop=" + this.iFlipFlopThreshold);
        }

        if (this.iFlipFlopThreshold > 0) {
            this.iHistoryLength = (this.iFlipFlopThreshold * ((this.iSuccessThreshold + this.iErrorThreshold) - 2)) + 1;
        } else {
            this.iHistoryLength = Math.max(this.iSuccessThreshold, this.iErrorThreshold) + 1;
        }

        for (int i = this.alSeries.size() - 1; i >= 0; i--) {
            final SeriesState ss = this.alSeries.get(i);

            ss.initState();

            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Series: " + ss.toString());
            }
        }

        this.lSeriesReloadPeriod = this.mlp.getl("reload_series", 15 * 60) * 1000;

        if (this.lSeriesReloadPeriod > 0) {
            this.lLastSeriesReload = NTPDate.currentTimeMillis();
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Series reload time: " + this.lSeriesReloadPeriod);
        }

        this.bIgnoreMissingData = this.mlp.getb("ignore_missing_data", false);

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Ignore missing data: " + this.bIgnoreMissingData);
        }

        this.iRearmIterations = this.mlp.geti("rearm.iterations", 0);

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Rearm after: " + this.iRearmIterations + " consecutive errors");
        }
    }

    /**
     * Find out whether or not this is a periodic action
     * 
     * @return true if the series names list is not empty and the period is strictly positive
     */
    public boolean isPeriodicAction() {
        return (this.alSeries.size() > 0) && (this.period > 0);
    }

    /**
     * Find out whether or not this is a event-based action
     * 
     * @return true if the series names list is not empty and the trigger predicate is defined (not null) 
     */
    public boolean isEventAction() {
        return (this.alSeries.size() > 0) && (this.trigger != null);
    }

    /**
     * Find out the predicate that triggers the event action.
     * 
     * @return the predicate
     */
    public monPredicate getTrigger() {
        return this.trigger;
    }

    /**
     * Periodic check
     */
    public void check() {
        final long lNow = NTPDate.currentTimeMillis();

        if ((this.lSeriesReloadPeriod > 0) && ((lNow - this.lLastSeriesReload) > this.lSeriesReloadPeriod)) {
            reloadSeries();

            this.lLastSeriesReload = lNow;
        }

        if (this.period > 0) {
            if ((lNow - this.lLastCheck) < this.period) {
                return;
            }

            this.lLastCheck = lNow;
        }

        for (int i = this.alSeries.size() - 1; i >= 0; i--) {
            final SeriesState ss = this.alSeries.get(i);

            Thread.currentThread().setName("(ML) ActionsManager - processing " + ss);

            final DecisionResult dr = this.decisionClass.getValue(ss);

            if (dr.bData || !this.bIgnoreMissingData) {
                if (!dr.bOk && (this.iRearmIterations > 0) && (ss.getConsecutiveErrors() >= this.iRearmIterations)) {
                    ss.rearm();
                }

                ss.event(dr.bOk, dr.sValue);

                ss.clearRearm();
            }
        }

        Thread.currentThread().setName("(ML) ActionsManager");
    }

    private final AtomicBoolean bActionTaken = new AtomicBoolean(false);

    /**
     * @param ss
     */
    void takeAction(final SeriesState ss) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "ACTION: " + ss + " - " + this.alActions.size());
        }

        for (int i = 0; i < this.alActions.size(); i++) {
            final ActionTaker at = this.alActions.get(i);

            at.takeAction(ss);
        }

        this.bActionTaken.set(true);
    }

    /**
     * Find out all the series that this action checks
     * 
     * @return the list of all the series
     */
    public ArrayList<SeriesState> getSeriesList() {
        return new ArrayList<SeriesState>(this.alSeries);
    }

    /**
     * Atomic method to find out if the action was taken
     * 
     * @return the previous value of the flag
     */
    public boolean getAndResetActionTaken() {
        return this.bActionTaken.getAndSet(false);
    }

    /**
     * This method is called when the underlying configuration file is changed
     */
    @Override
    public void update(final Observable o, final Object arg) {
        logger.log(Level.FINE, "Reloading '" + this.sFile + "' because the file has changed");

        try {
            this.mlp.reload();
        } catch (Exception e) {
            // ignore
        }

        reload();
    }

    /**
     * Package protected method to initialize the status of a set of series with the last known value from the database
     * 
     * @param mlp where to put the status for the series
     */
    private static void setStatus(final MLProperties mlp) {
        if (!TransparentStoreFactory.isMemoryStoreOnly()) {
            final DB db = new DB();
            
            db.setReadOnly(true);

            if (db.query("SELECT as_key,as_value FROM action_states WHERE as_file='" + mlp.gets("properties_file")
                    + "';")) {
                while (db.moveNext()) {
                    mlp.set(db.gets(1) + ".state", db.gets(2));
                }
            }
        }
    }
}
