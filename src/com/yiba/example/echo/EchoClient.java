package com.yiba.example.echo;

import com.yiba.kcp.netty.ChannelOptionHelper;
import com.yiba.kcp.netty.UkcpChannel;
import com.yiba.kcp.netty.UkcpChannelOption;
import com.yiba.kcp.netty.UkcpClientChannel;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

/**
 * Sends one message when a connection is open and echoes back any received
 * data to the server.
 * {@link UkcpClientChannel}
 * {@link EchoClientHandler}
 * 
 * @author U-Demon
 * @date 2019年1月29日 下午2:20:24
 */
public final class EchoClient {
	
	public static final int CONV = Integer.parseInt(System.getProperty("conv", "10"));
	public static final String HOST = System.getProperty("host", "127.0.0.1");
	public static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));
	public static final int SIZE = Integer.parseInt(System.getProperty("size", "768"));
	
	public static void main(String[] args) throws Exception {
        // Configure the client.
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(UkcpClientChannel.class)
                    .handler(new ChannelInitializer<UkcpChannel>() {
                        @Override
                        public void initChannel(UkcpChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new EchoClientHandler());
                        }
                    });
            ChannelOptionHelper.nodelay(b, true, 20, 2, true)
                    .option(UkcpChannelOption.UKCP_MTU, 512);

            // Start the client.
            ChannelFuture f = b.connect(HOST, PORT).sync();

            // Wait until the connection is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down the event loop to terminate all threads.
            group.shutdownGracefully();
        }
    }

}
