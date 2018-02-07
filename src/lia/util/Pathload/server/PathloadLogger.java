/**
 * 
 */
package lia.util.Pathload.server;

import java.util.Date;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * This is a custom logger, aware of Pathload Rounds
 * and XML conversion
 * (CODE REVIEW)
 * 
 * @author heri
 *
 */
public class PathloadLogger implements XMLWritable {

    /**
     * Define how many Pathload Rounds the logger keeps in mind.
     */
    public static int PATHLOADLOGGER_CAPACITY = 10;

    /**
     * Priority level over which the messages are logged or not.
     */
    public static int PRIORITY_LEVEL = 500;

    /**
     * Logging component
     */
    private static final Logger logger = Logger.getLogger(PathloadLogger.class.getName());

    private static PathloadLogger miniMe = new PathloadLogger();
    private final Vector data;
    private Round currentRound;
    private final Object lock;
    private long id = 1;

    /**
     * 
     * @author heri
     *
     */
    public class Round implements XMLWritable {

        private final long roundId;
        private final Vector messages;

        private class Message implements XMLWritable {
            private final Level level;
            private final String message;

            /**
             * Default constructor
             * 
             * @param type		Type may be MESSAGE_OK or MESSAGE_ERROR
             * @param message	Reason of failure or success message
             */
            protected Message(Level level, String message) {
                this.level = level;
                this.message = message;
            }

            /**
             * XML Representation of the Message
             */
            @Override
            public Element getXML(Document document) {
                Element messageElement = null;

                if (level.intValue() >= PathloadLogger.PRIORITY_LEVEL) {
                    messageElement = document.createElement("message");
                    messageElement.setAttribute("prio", "" + level.intValue());
                    messageElement.setAttribute("level", level.getName());
                    messageElement.appendChild(document.createTextNode(message));
                }

                return messageElement;
            }
        }

        protected Round(long roundId) {
            this.roundId = roundId;
            this.messages = new Vector();
        }

        /**
         * Log the message into the system
         * 
         * @param logLevel		Logger level of the message. See
         * 						java.util.logging.Logger;
         * @param message		Message to be logged.
         */
        public void log(Level logLevel, String message) {
            if (message == null) {
                return;
            }

            String logMessage = (new Date().toString()) + " " + message;
            Message m = new Message(logLevel, logMessage);
            messages.add(0, m);
        }

        /**
         * Turn the LogRound into XML
         */
        @Override
        public Element getXML(Document document) {
            Element logRoundElement = document.createElement("LogRound");
            logRoundElement.setAttribute("id", "" + roundId);

            int index = messages.size();
            Element temp = null;
            for (Iterator it = messages.iterator(); it.hasNext();) {
                Message m = (Message) it.next();
                temp = m.getXML(document);
                if (temp != null) {
                    temp.setAttribute("index", "" + index);
                    logRoundElement.appendChild(temp);
                }
                index--;
            }

            return logRoundElement;
        }
    }

    /**
     * Default Constructor
     *
     */
    private PathloadLogger() {
        data = new Vector(PATHLOADLOGGER_CAPACITY);
        lock = new Object();
        currentRound = createRound();
    }

    /**
     * PathloadLogger is a singleton instance, get or 
     * create that instance.
     * 
     * @return		The sole PathloadLogger Instance
     */
    public static PathloadLogger getInstance() {
        return miniMe;
    }

    /**
     * Log the message into the system
     * 
     * @param logLevel		Logger level of the message. See
     * 						java.util.logging.Level;
     * 						
     * @param message
     */
    public void log(Level logLevel, String message) {
        if ((logLevel == null) || (message == null)) {
            return;
        }

        logger.log(logLevel, message);
        if (logLevel.intValue() < PathloadLogger.PRIORITY_LEVEL) {
            return;
        }

        synchronized (lock) {
            currentRound.log(logLevel, message);
        }
    }

    /**
     * Notify the logger that a new round will begin
     * 
     * @return	The new round created
     */
    public Round createRound() {
        synchronized (lock) {
            currentRound = new Round(id++);
            data.add(0, currentRound);
            if (data.size() > PATHLOADLOGGER_CAPACITY) {
                data.remove(data.size() - 1);
            }
        }
        return currentRound;
    }

    /**
     * Returns the last logged round. Warning, this function may
     * return null if no elements are present in the log.
     * 
     * @param 	document	Main document to attach to
     * @return	XML formatted LastRound Element 
     */
    public Element getXMLLastRound(Document document) {
        Round r = null;
        Element logElement = null;
        if (data.isEmpty()) {
            return null;
        }

        logElement = document.createElement("Logs");
        logElement.setAttribute("LogLevel", "" + PRIORITY_LEVEL);

        synchronized (lock) {
            r = (Round) data.firstElement();
            logElement.appendChild(r.getXML(document));
        }

        return logElement;
    }

    /**
     * Turn the logs into XML Elements. Warning, this function may
     * return null if no elements are present in the log.
     */
    @Override
    public Element getXML(Document document) {
        Element logElement = null;
        if (data.isEmpty()) {
            return null;
        }

        synchronized (lock) {
            logElement = document.createElement("Logs");
            logElement.setAttribute("LogLevel", "" + PathloadLogger.PRIORITY_LEVEL);
            for (Iterator it = data.iterator(); it.hasNext();) {
                Round r = (Round) it.next();
                Element temp = r.getXML(document);
                logElement.appendChild(temp);
            }
        }

        return logElement;
    }
}
