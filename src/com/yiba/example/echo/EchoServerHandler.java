package com.yiba.example.echo;

import com.yiba.kcp.netty.UkcpChannel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Handler implementation for the echo server.
 * 
 * @author U-Demon
 * @date 2019年1月29日 下午2:06:42
 */
public class EchoServerHandler extends ChannelInboundHandlerAdapter {

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		UkcpChannel kcpCh = (UkcpChannel) ctx.channel();
		kcpCh.conv(EchoServer.CONV);
		System.out.println("channel active. has client connected.");
	}
	
	@Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ctx.write(msg);
        System.out.println("channel read. msg=" + msg + ", and write msg to client");
    }
	
	@Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
        System.out.println("channelReadComplete. flush.");
    }
	
	@Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }

}
