package com.demo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/*
 * Persistent blocking client.
 * Opens ONE socket at construction time.
 * Reuses it for every call().
 * call() BLOCKS on socket read waiting for reply.
 *
 * This means the calling thread truly sleeps during the delay,
 * which is what we want to see TIMED_WAITING in jstack.
 *
 * One instance per business thread (created in MainServer).
 * No synchronization needed because each thread has its own instance.
 */
public class BlockingDownstreamClient {

    private final DataOutputStream out;
    private final DataInputStream  in;
    private final Socket           socket;

    public BlockingDownstreamClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.socket.setTcpNoDelay(true);
        this.out    = new DataOutputStream(socket.getOutputStream());
        this.in     = new DataInputStream(socket.getInputStream());
    }

    /*
     * Sends request and BLOCKS until reply arrives.
     * Thread state during wait: TIMED_WAITING or BLOCKED
     * depending on JVM socket implementation.
     */
    public byte[] call(byte[] requestBytes) throws IOException {
        out.writeInt(requestBytes.length);
        out.write(requestBytes);
        out.flush();

        int    replyLen = in.readInt();
        byte[] reply    = new byte[replyLen];
        in.readFully(reply);
        return reply;
    }

    public void close() throws IOException {
        socket.close();
    }
}
