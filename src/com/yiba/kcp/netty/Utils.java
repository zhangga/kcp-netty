package com.yiba.kcp.netty;

import com.yiba.kcp.internal.CodecOutputList;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

public class Utils {
	
	static void fireExceptionAndClose(Channel channel, Throwable t, boolean close) {
        channel.pipeline().fireExceptionCaught(t);
        if (channel.isActive()) {
            Channel.Unsafe unsafe = channel.unsafe();
            unsafe.close(unsafe.voidPromise());
        }
    }
	
	static void fireChannelRead(Channel ch, CodecOutputList<ByteBuf> bufList) {
        ChannelPipeline pipeline = ch.pipeline();
        int size = bufList.size();
        if (size <= 0) {
            return;
        }
        for (int i = 0; i < size; i++) {
            ByteBuf msg = bufList.getUnsafe(i);
            pipeline.fireChannelRead(msg);
        }
        pipeline.fireChannelReadComplete();
    }

}
