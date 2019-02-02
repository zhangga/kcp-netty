package com.yiba.kcp.netty;

import io.netty.util.internal.SystemPropertyUtil;

public class Consts {
	
	/**
	 * UDP接收缓冲池分配大小
	 */
	public static final int FIXED_RECV_BYTEBUF_ALLOCATE_SIZE = SystemPropertyUtil.getInt("com.yiba.kcp.netty" +
            ".udpRecvAllocateSize", 2048);

    public static final int CLOSE_WAIT_TIME = 5 * 1000;

}
