package lia.util.DynamicThreadPoll;

public interface  ResultNotification {
    public void notifyResult ( SchJobInt job, Object result, Throwable t );
}