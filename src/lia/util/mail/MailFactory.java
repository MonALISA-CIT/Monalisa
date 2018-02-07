package lia.util.mail;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;

public class MailFactory {

    private static final Logger logger = Logger.getLogger(MailFactory.class.getName());

    private MailSender mailSender;

    private final Lock mailSenderReadLock;

    private final Lock mailSenderWriteLock;

    private static MailFactory _thisInstance;

    private static Object _thisInstanceLock = new Object();

    private MailFactory() {
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

        mailSenderReadLock = rwLock.readLock();
        mailSenderWriteLock = rwLock.writeLock();

        reloadConfig();
        AppConfig.addNotifier(new AppConfigChangeListener() {

            @Override
            public void notifyAppConfigChanged() {
                reloadConfig();
            }
        });
    }

    void reloadConfig() {

        String sMailerSender = null;
        try {
            sMailerSender = AppConfig.getProperty("lia.util.mail.MailFactory.mailer", "PMSender");
        } catch (Throwable t) {
            sMailerSender = "PMSender";
        }
        mailSenderWriteLock.lock();
        try {
            if (mailSender == null) {
                if ((sMailerSender == null) || sMailerSender.equals("PMSender")) {
                    mailSender = PMSender.getInstance();
                } else {
                    mailSender = DirectMailSender.getInstance();
                }
            } else {
                if (mailSender instanceof PMSender) {
                    if ((sMailerSender != null) && !sMailerSender.equals("PMSender")) {
                        mailSender = DirectMailSender.getInstance();
                    }
                } else {
                    if ((sMailerSender == null) || sMailerSender.equals("PMSender")) {
                        mailSender = PMSender.getInstance();
                    }
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, " Cannot instantiate MailSender ... will try to use DirectMailSender ... ");
            }
            try {
                mailSender = DirectMailSender.getInstance();
            } catch (Throwable severeError) {
                logger.log(Level.FINER, " [ SEVERE ] Cannot instantiate MailSender ...  ");
            }
        } finally {
            mailSenderWriteLock.unlock();
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " [ MailFactory ] Reloaded Config Parameters");
        }
    }

    public static final MailSender getMailSender() {
        synchronized (_thisInstanceLock) {
            if (_thisInstance == null) {
                _thisInstance = new MailFactory();
            }
        }

        _thisInstance.mailSenderReadLock.lock();
        try {
            return _thisInstance.mailSender;
        } finally {
            _thisInstance.mailSenderReadLock.unlock();
        }
    }

}
