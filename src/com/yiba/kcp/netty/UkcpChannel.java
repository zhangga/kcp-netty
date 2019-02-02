package com.yiba.kcp.netty;

import java.net.InetSocketAddress;

import io.netty.channel.Channel;

public interface UkcpChannel extends Channel {
	
	int conv();

    @Override
    UkcpChannelConfig config();

    UkcpChannel conv(int conv);

    @Override
    InetSocketAddress localAddress();

    @Override
    InetSocketAddress remoteAddress();

}
