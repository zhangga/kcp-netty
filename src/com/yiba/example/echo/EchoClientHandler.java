package com.yiba.example.echo;

import com.yiba.kcp.netty.UkcpChannel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Handler implementation for the echo client.
 * 
 * @author U-Demon
 * @date 2019年1月29日 下午2:22:05
 */
public class EchoClientHandler extends ChannelInboundHandlerAdapter {
	
	private final ByteBuf firstMessage;
	
	/**
     * Creates a client-side handler.
     */
    public EchoClientHandler() {
        firstMessage = Unpooled.buffer(EchoClient.SIZE);
        for (int i = 0; i < firstMessage.capacity(); i++) {
            firstMessage.writeByte((byte) i);
        }
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        UkcpChannel kcpCh = (UkcpChannel) ctx.channel();
        kcpCh.conv(EchoClient.CONV); // set conv

        ctx.writeAndFlush(firstMessage);
        System.out.println("channel active and flush firstMessage");
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
    	System.out.println("channel read. --> " + msg);
    	
    	// 一秒之后再发送
    	try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
        ctx.write(msg);
    }
    
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
    	System.out.println("channelReadComplete. flush. ");
        ctx.flush();
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }

}
