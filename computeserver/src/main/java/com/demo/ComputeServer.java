package com.demo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * ComputeServer - simulates a downstream service with processing delay.
 *
 * KEY FIX: Uses a separate ScheduledExecutorService for the delay
 * instead of Thread.sleep() inside the Netty event loop thread.
 *
 * Thread.sleep() inside a Netty handler BLOCKS the event loop thread,
 * preventing it from serving other connections.
 * With 512 connections and 128 event loop threads, each thread
 * serves ~4 connections. If one sleep blocks a thread, 4 connections stall.
 *
 * With ScheduledExecutorService, the event loop thread is released
 * immediately after scheduling the delayed reply. It can serve other
 * connections while the delay runs in the background.
 */
public class ComputeServer {

    static final int PORT         = 9090;
    static final int DELAY_MS     = 3;    // simulated processing delay
    static final int WORKER_THREADS = 128;

    public static void main(String[] args) throws Exception {

        System.out.println("==============================================");
        System.out.println("  Compute Server starting on port " + PORT);
        System.out.println("  Simulated delay: " + DELAY_MS + "ms");
        System.out.println("  Worker threads:  " + WORKER_THREADS);
        System.out.println("==============================================");

        // Dedicated pool just for scheduling delayed replies
        // This pool does NOT block Netty event loops
        ScheduledExecutorService delayScheduler =
            Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("delay-scheduler-" + t.getId());
                    return t;
                }
            );

        EventLoopGroup bossGroup   = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(WORKER_THREADS);

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 4096)
             .childOption(ChannelOption.TCP_NODELAY, true)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(
                         new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4),
                         new LengthFieldPrepender(4),
                         new DelayedReplyHandler(delayScheduler)
                     );
                 }
             });

            ChannelFuture f = b.bind(PORT).sync();
            System.out.println("Compute Server READY on port " + PORT);
            System.out.println("Press Ctrl+C to stop.");
            f.channel().closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            delayScheduler.shutdown();
        }
    }

    static class DelayedReplyHandler extends ChannelInboundHandlerAdapter {

        static final byte[] OK = new byte[]{'O','K'};
        private final ScheduledExecutorService scheduler;

        DelayedReplyHandler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // Release request buffer immediately - we don't need its content
            ((ByteBuf) msg).release();

            /*
             * Schedule reply after DELAY_MS milliseconds.
             *
             * The event loop thread is NOT blocked here.
             * It returns immediately and can handle other connections.
             *
             * After DELAY_MS, a scheduler thread calls ctx.writeAndFlush()
             * which queues the write back onto the event loop thread.
             *
             * This correctly simulates a downstream service that takes
             * time to compute a result without blocking server threads.
             */
            scheduler.schedule(() -> {
                if (ctx.channel().isActive()) {
                    ByteBuf reply = ctx.alloc().buffer(OK.length);
                    reply.writeBytes(OK);
                    ctx.writeAndFlush(reply);
                }
            }, DELAY_MS, TimeUnit.MILLISECONDS);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
