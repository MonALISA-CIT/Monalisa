package lia.Monitor.Store.Sql.Pool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionPool implements Runnable {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ConnectionPool.class.getName());

    private final String driver, url, username, password;

    private final int maxConnections;

    private final boolean waitIfBusy;

    private Vector availableConnections, busyConnections;

    private boolean connectionPending = false;

    public ConnectionPool(String driver, String url, String username, String password, int initialConnections,
            int maxConnections, boolean waitIfBusy) throws SQLException {
        this.driver = driver;
        this.url = url;
        this.username = username;
        this.password = password;
        this.maxConnections = maxConnections;
        this.waitIfBusy = waitIfBusy;
        if (initialConnections > maxConnections) {
            initialConnections = maxConnections;
        }
        availableConnections = new Vector(initialConnections);
        busyConnections = new Vector();
        for (int i = 0; i < initialConnections; i++) {
            availableConnections.addElement(makeNewConnection());
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        if (!availableConnections.isEmpty()) {
            Connection existingConnection = (Connection) availableConnections.lastElement();
            int lastIndex = availableConnections.size() - 1;
            availableConnections.removeElementAt(lastIndex);
            // If connection on available list is closed (e.g.,
            // it timed out), then remove it from available list
            // and repeat the process of obtaining a connection.
            // Also wake up threads that were waiting for a
            // connection because maxConnection limit was reached.
            if (existingConnection.isClosed()) {
                notifyAll(); // Freed up a spot for anybody waiting
                return getConnection();
            }
            busyConnections.addElement(existingConnection);
            return existingConnection;
        }

        // Three possible cases:
        // 1) You haven't reached maxConnections limit. So
        //    establish one in the background if there isn't
        //    already one pending, then wait for
        //    the next available connection (whether or not
        //    it was the newly established one).
        // 2) You reached maxConnections limit and waitIfBusy
        //    flag is false. Throw SQLException in such a case.
        // 3) You reached maxConnections limit and waitIfBusy
        //    flag is true. Then do the same thing as in second
        //    part of step 1: wait for next available connection.

        if ((totalConnections() < maxConnections) && !connectionPending) {
            makeBackgroundConnection();
        } else if (!waitIfBusy) {
            throw new SQLException("Connection limit reached");
        }
        // Wait for either a new connection to be established
        // (if you called makeBackgroundConnection) or for
        // an existing connection to be freed up.
        try {
            wait();
        } catch (InterruptedException ie) {

        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got general exception in ConnectionPool", t);
        }
        // Someone freed up a connection, so try again.
        return getConnection();
    }

    // You can't just make a new connection in the foreground
    // when none are available, since this can take several
    // seconds with a slow network connection. Instead,
    // start a thread that establishes a new connection,
    // then wait. You get woken up either when the new connection
    // is established or if someone finishes with an existing
    // connection.

    private void makeBackgroundConnection() {
        connectionPending = true;
        try {
            Thread connectThread = new Thread(this, "(ML) ConnectionPool");
            try {
                connectThread.setDaemon(true);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Cannot setDaemon", t);
            }
            connectThread.start();
        } catch (OutOfMemoryError oom) {
            // Give up on new connection
            logger.log(Level.WARNING, "Got OOM in ConnectionPool makeBackgroundConnection()", oom);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got general exception in ConnectionPool", t);
        }
    }

    @Override
    public void run() {
        try {
            Connection connection = makeNewConnection();
            synchronized (this) {
                availableConnections.addElement(connection);
                connectionPending = false;
                notifyAll();
            }
        } catch (Throwable t) { // SQLException or OutOfMemory
            // Give up on new connection and wait for existing one
            // to free up.
            logger.log(Level.WARNING, "Got general exception in ConnectionPool main loop", t);
        }
    }

    // This explicitly makes a new connection. Called in
    // the foreground when initializing the ConnectionPool,
    // and called in the background when running.

    private Connection makeNewConnection() throws SQLException {
        try {
            // Load database driver if not already loaded
            Class.forName(driver);
            // Establish network connection to database
            Connection connection = DriverManager.getConnection(url, username, password);
            return connection;
        } catch (ClassNotFoundException cnfe) {
            // Simplify try/catch blocks of people using this by
            // throwing only one exception type.
            logger.log(Level.WARNING, "Got ClassNotFoundException exception in ConnectionPool makeNewConnection()",
                    cnfe);
            throw new SQLException("Can't find class for driver: " + driver);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got general Exception exception in ConnectionPool makeNewConnection()", t);
            throw new SQLException(t.getMessage());
        }
    }

    public synchronized void free(Connection connection) {
        if (connection == null) {
            return;
        }
        busyConnections.removeElement(connection);
        availableConnections.addElement(connection);
        // Wake up threads that are waiting for a connection
        notifyAll();
    }

    public synchronized int totalConnections() {
        return (availableConnections.size() + busyConnections.size());
    }

    /** Close all the connections. Use with caution:
     *  be sure no connections are in use before
     *  calling. Note that you are not <I>required</I> to
     *  call this when done with a ConnectionPool, since
     *  connections are guaranteed to be closed when
     *  garbage collected. But this method gives more control
     *  regarding when the connections are closed.
     */

    public synchronized void closeAllConnections() {
        closeConnections(availableConnections);
        availableConnections = new Vector();
        closeConnections(busyConnections);
        busyConnections = new Vector();
    }

    private void closeConnections(Vector connections) {
        if ((connections == null) || (connections.size() == 0)) {
            return;
        }
        for (int i = 0; i < connections.size(); i++) {
            try {
                Connection connection = (Connection) connections.elementAt(i);
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (Throwable t) {
                // Ignore errors; garbage collect anyhow
            }
        }//for
    }

    @Override
    public synchronized String toString() {
        StringBuilder info = new StringBuilder();
        info.append("ConnectionPool(");
        info.append(url);
        info.append(",");
        info.append(username);
        info.append(")");
        info.append(", available=");
        info.append(availableConnections.size());
        info.append(", busy=");
        info.append(busyConnections.size());
        info.append(", max=");
        info.append(maxConnections);
        return info.toString();
    }

}