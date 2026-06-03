package com.demo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class LoadGenerator {

    static final String HOST = "127.0.0.1";
    static final int PORT = 8080;

    static final int CLIENT_THREADS = 2000;
    static final int DURATION_SECONDS = 60;
    static final int REQUEST_SIZE = 32;

    public static void main(String[] args) throws Exception {

        System.out.println("==============================================");
        System.out.println("Load Generator starting");
        System.out.println("Target host:      " + HOST);
        System.out.println("Target port:      " + PORT);
        System.out.println("Client threads:   " + CLIENT_THREADS);
        System.out.println("Duration seconds: " + DURATION_SECONDS);
        System.out.println("Request size:     " + REQUEST_SIZE);
        System.out.println("==============================================");

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicLong totalRequests = new AtomicLong(0);
        AtomicLong totalErrors = new AtomicLong(0);

        CountDownLatch latch = new CountDownLatch(CLIENT_THREADS);

        for (int i = 0; i < CLIENT_THREADS; i++) {
            final int id = i;

            Thread t = new Thread(() -> {
                byte[] request = new byte[REQUEST_SIZE];
                Arrays.fill(request, (byte) ('A' + (id % 26)));

                try (Socket socket = new Socket(HOST, PORT)) {
                    socket.setTcpNoDelay(true);

                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream());

                    while (!stop.get()) {
                        out.writeInt(request.length);
                        out.write(request);
                        out.flush();

                        int replyLength = in.readInt();
                        byte[] reply = new byte[replyLength];
                        in.readFully(reply);

                        totalRequests.incrementAndGet();

                    }

                } catch (Exception e) {
                    totalErrors.incrementAndGet();
                    System.out.println("Client-" + id + " error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });

            t.setName("loadgen-" + i);
            t.start();
        }

        long start = System.nanoTime();
        long lastCount = 0;

        for (int sec = 1; sec <= DURATION_SECONDS; sec++) {
            Thread.sleep(1000);

            long current = totalRequests.get();
            long delta = current - lastCount;
            lastCount = current;

            System.out.println("second=" + sec +
                    " totalRequests=" + current +
                    " rps=" + delta +
                    " totalErrors=" + totalErrors.get());
        }

        stop.set(true);
        latch.await();

        long elapsedNanos = System.nanoTime() - start;
        double elapsedSec = elapsedNanos / 1_000_000_000.0;
        long total = totalRequests.get();

        System.out.println("==============================================");
        System.out.println("Load Generator finished");
        System.out.println("Total requests: " + total);
        System.out.println("Total errors:   " + totalErrors.get());
        System.out.println("Elapsed sec:    " + String.format("%.2f", elapsedSec));
        System.out.println("Avg RPS:        " + String.format("%.2f", total / elapsedSec));
        System.out.println("==============================================");
    }
}
