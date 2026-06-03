package com.demo;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.ExecutorService;

@ChannelHandler.Sharable
public class BadHandler extends ChannelInboundHandlerAdapter {

    private final ExecutorService businessPool;
    private final ThreadLocal<BlockingDownstreamClient> clientLocal;

    public BadHandler(ExecutorService businessPool,
                      ThreadLocal<BlockingDownstreamClient> clientLocal) {
        this.businessPool = businessPool;
        this.clientLocal  = clientLocal;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        ByteBuf in = (ByteBuf) msg;
        byte[] requestBytes = new byte[in.readableBytes()];
        in.readBytes(requestBytes);
        in.release();

        // HANDOFF #1: netty worker -> business thread
        businessPool.submit(() -> {

            byte[] computeReply;
            try {
                computeReply = clientLocal.get().call(requestBytes);
            } catch (Exception e) {
                ctx.close();
                return;
            }

            byte[] replyBytes = ("DONE-" + computeReply.length).getBytes();
            ByteBuf response  = ctx.alloc().buffer(replyBytes.length);
            response.writeBytes(replyBytes);

            // HANDOFF #2: business thread -> netty worker (via writeAndFlush)
            ctx.writeAndFlush(response);
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
