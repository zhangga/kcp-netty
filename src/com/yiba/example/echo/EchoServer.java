package com.yiba.example.echo;

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
 * Echoes back any received data from a client.
 * {@link UkcpServerChannel}
 * {@link EchoServerHandler}
 * 
 * @author U-Demon
 * @date 2019年1月29日 下午2:10:59
 */
public final class EchoServer {
	
	public static final int CONV = Integer.parseInt(System.getProperty("conv", "10"));
	
	private static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));
	
	public static void main(String[] args) throws Exception {
        // Configure the server.
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            UkcpServerBootstrap b = new UkcpServerBootstrap();
            b.group(group)
                    .channel(UkcpServerChannel.class)
                    .childHandler(new ChannelInitializer<UkcpChannel>() {
                        @Override
                        public void initChannel(UkcpChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new EchoServerHandler());
                        }
                    });
            ChannelOptionHelper.nodelay(b, true, 20, 2, true)
                    .childOption(UkcpChannelOption.UKCP_MTU, 512);

            // Start the server.
            System.out.println("netty startup!");
            ChannelFuture f = b.bind(PORT).sync();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            group.shutdownGracefully();
        }
    }

}
