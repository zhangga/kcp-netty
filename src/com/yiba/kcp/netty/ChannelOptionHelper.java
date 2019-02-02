package com.yiba.kcp.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.UkcpServerBootstrap;

/**
 * ChannelOption帮助类
 * @author U-Demon
 * @date 2019年1月29日 下午12:01:09
 */
public class ChannelOptionHelper {
	
	public static Bootstrap nodelay(Bootstrap b, boolean nodelay, int interval, int fastResend, boolean nocwnd) {
		b.option(UkcpChannelOption.UKCP_NODELAY, nodelay)
        	.option(UkcpChannelOption.UKCP_INTERVAL, interval)
        	.option(UkcpChannelOption.UKCP_FAST_RESEND, fastResend)
        	.option(UkcpChannelOption.UKCP_NOCWND, nocwnd);
		return b;
	}
	
	public static UkcpServerBootstrap nodelay(UkcpServerBootstrap b, boolean nodelay, int interval, 
			int fastResend, boolean nocwnd) {
		b.childOption(UkcpChannelOption.UKCP_NODELAY, nodelay)
			.childOption(UkcpChannelOption.UKCP_INTERVAL, interval)
			.childOption(UkcpChannelOption.UKCP_FAST_RESEND, fastResend)
			.childOption(UkcpChannelOption.UKCP_NOCWND, nocwnd);
		return b;
	}

}
