package com.yiba.example.rtt;

import com.yiba.kcp.netty.ChannelOptionHelper;
import com.yiba.kcp.netty.UkcpChannel;
import com.yiba.kcp.netty.UkcpChannelOption;
import com.yiba.kcp.netty.UkcpServerChannel;

import io.netty.bootstrap.UkcpServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

/**
 * KCP服务的例子
 * Measures RTT(Round-trip time) for KCP.
 * <p>
 * Receives a message from client and sends a response.
 * 
 * @author U-Demon
 * @date 2019年1月17日 下午4:27:02
 */
public class KcpRttServer {
	
	public static final int CONV = Integer.parseInt(System.getProperty("conv", "10"));
	
	private static final int PORT = Integer.parseInt(System.getProperty("port", "8009"));
	
	public static void main(String[] args) throws Exception {
		// Configure the server.
		EventLoopGroup group = new NioEventLoopGroup();
		try {
			// 配置netty
			UkcpServerBootstrap b = new UkcpServerBootstrap();
			b.group(group)
					.channel(UkcpServerChannel.class)
					.childHandler(new ChannelInitializer<UkcpChannel>() {
						@Override
						public void initChannel(UkcpChannel ch) throws Exception {
							ChannelPipeline p = ch.pipeline();
							p.addLast(new KcpRttServerHandler());
						}
					});
			ChannelOptionHelper.nodelay(b, true, 20, 2, true)
					.childOption(UkcpChannelOption.UKCP_MTU, 512);
			
			// 启动netty服务
			ChannelFuture f = b.bind(PORT).sync();
			f.channel().closeFuture().sync();
		} finally {
			// Shut down all event loops to terminate all threads.
            group.shutdownGracefully();
		}
	}

}
