package com.yiba.kcp.netty;

import io.netty.buffer.ByteBuf;

public interface KcpOutput {
	
	void out(ByteBuf data, Kcp kcp);

}
