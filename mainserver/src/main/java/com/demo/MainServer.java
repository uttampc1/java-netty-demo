package com.demo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/*
 * BAD Main Server
 *
 * Two separate thread pools (the anti-pattern):
 *
 *   bossGroup    (1 thread)    accepts TCP connections
 *   workerGroup  (2x cores)    reads/writes sockets        OVER-PROVISIONED
 *   businessPool (512 threads) calls compute server        SEPARATE POOL
 *
 * Every request crosses 2 pool boundaries:
 *   netty worker -> business thread  (HANDOFF #1)
 *   business thread -> netty worker  (HANDOFF #2 via writeAndFlush)
 *
 * At 100K RPS x 2 handoffs = 200K context switches/sec target.
 */
public class MainServer {

    static final int    COMPUTE_PORT     = 9090;
    static final String COMPUTE_HOST     = "127.0.0.1";
    static final int    SERVER_PORT      = 8080;
    static final int    BUSINESS_THREADS = 1000;

    public static void main(String[] args) throws Exception {

        int cores         = Runtime.getRuntime().availableProcessors();
        int workerThreads = cores * 2;

        System.out.println("==============================================");
        System.out.println("  BAD Main Server on port " + SERVER_PORT);
        System.out.println("  Cores:                " + cores);
        System.out.println("  Netty worker threads: " + workerThreads
                           + "  (BAD: should be " + cores/2 + ")");
        System.out.println("  Business threads:     " + BUSINESS_THREADS
                           + "  (BAD: separate pool)");
        System.out.println("  Handoffs per request: 2");
        System.out.println("==============================================");

        System.out.println("Creating " + BUSINESS_THREADS + " compute connections...");
        BlockingDownstreamClient[] computeClients =
            new BlockingDownstreamClient[BUSINESS_THREADS];
        for (int i = 0; i < BUSINESS_THREADS; i++) {
            computeClients[i] =
                new BlockingDownstreamClient(COMPUTE_HOST, COMPUTE_PORT);
        }
        System.out.println("All compute connections established.");

        ThreadLocal<BlockingDownstreamClient> threadLocalClient =
            ThreadLocal.withInitial(() -> {
                String name = Thread.currentThread().getName();
                try {
                    int idx = Integer.parseInt(name.split("-")[1])
                              % BUSINESS_THREADS;
                    return computeClients[idx];
                } catch (Exception e) {
                    return computeClients[0];
                }
            });

        ExecutorService businessPool = new ThreadPoolExecutor(
            BUSINESS_THREADS, BUSINESS_THREADS,
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(50_000),
            r -> {
                Thread t = new Thread(r);
                t.setName("business-" + t.getId());
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        EventLoopGroup bossGroup   = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(workerThreads);

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
                         new BadHandler(businessPool, threadLocalClient)
                     );
                 }
             });

            ChannelFuture f = b.bind(SERVER_PORT).sync();
            System.out.println("BAD Main Server READY on port " + SERVER_PORT);
            System.out.println("Press Ctrl+C to stop.");
            f.channel().closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            businessPool.shutdown();
        }
    }
}
