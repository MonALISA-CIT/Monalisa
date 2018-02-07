package com.telescent.afox.utils;

public class AFOXMsgs {
	public static final int TCP_jnkReconfigMsg = 1;
	public static final int TCP_REQUEST_STATUS = 2;
	public static final int TCP_AFOXCmdMsg = 3;
	public static final int TCP_AFOXCmdRetMsg = 4;
	public static final int TCP_AFOXCmdAckMsg = 5;
	public static final int TCP_MutexMsg = 6;
	public static final int TCP_SMCurAndPendingMsg = 7;
	public static final int TCP_AFOXConfigCmdMsg = 8;
	public static final int TCP_AFOXSystemControlMsg = 10;
	public static final int TCP_AFOXFullUpdateRequestMsg = 11;
	public static final int TCP_AFOXFullUpdateReturnMsg = 12;
	public static final int TCP_AFOXSinglePendingStatusRequestMsg = 13;
	public static final int TCP_AFOXSinglePendingStatusReturnMsg = 14;
	public static final int TCP_AFOXMoveSinglePendingCmd = 15;
	public static final int TCP_AFOXMultiCmdMsg = 16;
	public static final int TCP_AFOXMultiCmdRetMsg = 17;
	public static final int TCP_AFOXGetInputRFIDMsg = 18;
	public static final int TCP_AFOXGetInputRFIDRetMsg = 19;

	public static final int TCP_AFOXAckMsg = 100;
	public static final int TCP_TEST_MESSAGE_1 = 99;

	public static final boolean TCP_AquireMutex = true;
	public static final boolean TCP_ReleaseMutex = false;

	public static final int REQUEST_CMD_Q_TO_PAUSE = 1;
	public static final int REQUEST_CMD_Q_TO_RUN = 2;
	public static final int REQUEST_AFOX_TO_SHUTDOWN = 3;
	public static final int REQUEST_SYSTEM_STATUS = 4;

	public static final String SHUTDOWN_PASSWORD = "TeleShutdown";
	public static final String Q_CONTROL_PASSWORD = "TeleQ";

	public static final int ACK_ID_GENERAL = 1;    //returns system status.
	public static final int ACK_ID_BAD_MESSAGE_HEADER = 2;
	public static final int ACK_ID_BAD_MESSAGE = 3;
	public static final boolean ACK_STATUS_GOOD = true;
	public static final boolean ACK_STATUS_BAD = false;

	//Move Pending Command(s) constants
	public static final int MOVE_PENDING_BEFORE = 1;
	public static final int MOVE_PENDING_AFTER = 2;
	public static final int MOVE_PENDING_TO_FRONT = 3;
	public static final int MOVE_PENDING_TO_END = 4;
	public static final int DELETE_PENDING = 5;
	public static final int DELETE_ALL_PENDING = 6;
}


