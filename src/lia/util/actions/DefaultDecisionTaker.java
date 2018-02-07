package lia.util.actions;

import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.MLProperties;
import lia.util.actions.Action.SeriesState;
import lia.web.utils.Formatare;

/**
 * @author costing
 * @since May 22, 2007
 */
class DefaultDecisionTaker implements DecisionTaker {
    private static final Logger logger = Logger.getLogger(DefaultDecisionTaker.class.getName());

    private MLProperties mlp;

    private String sRule;

    private String sValue;

    @Override
    public void init(final MLProperties prop) throws Exception {
        this.mlp = prop;

        this.sRule = prop.gets("rule", "", false).trim();

        this.sValue = prop.gets("value", null, false);

        if (this.sRule.length() == 0) {
            throw new Exception("'rule' cannot be empty");
        }
    }

    @Override
    public DecisionResult getValue(final SeriesState ss) {
        String sVal = this.sValue != null ? ActionUtils.apply(ss, null, this.sValue, this.mlp) : null;

        String sTempRule = Formatare.replace(this.sRule, "#VALUE", sVal != null ? sVal : "");

        final String sRuleValue = ActionUtils.apply(ss, null, sTempRule, this.mlp);

        if (sVal == null) {
            sVal = sRuleValue;
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, ss.toString() + " : Value is : " + sVal + ", rule value is : " + sRuleValue);
        }

        boolean bValue = false;

        boolean bMissing = false;

        if (sRuleValue.length() > 0) {
            final char c = sRuleValue.charAt(0);

            if ((c == 't') || (c == 'T') || (c == 'y') || (c == 'Y')) {
                bValue = true;
            } else if ((c == 'f') || (c == 'F') || (c == 'n') || (c == 'N')) {
                bValue = false;
            } else {
                try {
                    final double d = Double.parseDouble(sRuleValue);

                    if (d > 0.7) {
                        bValue = true;
                    } else if (d < 0.3) {
                        bValue = false;
                    }

                    if (Double.isNaN(d) || (d < -0.1)) {
                        bMissing = true;
                    }
                } catch (Exception e) {
                    bMissing = true;
                }
            }
        } else {
            bMissing = true;
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "Determined value is : '" + sRuleValue + "' -> " + bValue + " (missing=" + bMissing
                    + ")");
        }

        final DecisionResult dr = new DecisionResult();
        dr.bOk = bValue;
        dr.bData = !bMissing;
        dr.sValue = sVal;

        return dr;
    }

}