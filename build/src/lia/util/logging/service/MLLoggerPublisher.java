package lia.util.logging.service;

import lia.util.logging.comm.MLLogMsg;

public interface MLLoggerPublisher {
    public void publish(MLLogMsg mlle);
    public void finish();
}
