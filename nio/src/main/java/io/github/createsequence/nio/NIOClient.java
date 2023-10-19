package io.github.createsequence.nio;

import lombok.Cleanup;
import lombok.SneakyThrows;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * @author huangchengxing
 */
public class NIOClient implements Closeable {

    private final SocketChannel socketChannel;

    @SneakyThrows
    public NIOClient(String hostname, int port) {
        socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress(hostname, port));
    }

    @SneakyThrows
    @SuppressWarnings("all")
    public static void main(String[] args) {
        @Cleanup NIOClient client = new NIOClient("localhost", 8888);
        long time = System.currentTimeMillis();
        while (true) {
            Thread.sleep(1000L);
            client.send("client send msg at " + time);
        }
    }

    @Override
    public void close() throws IOException {
        socketChannel.close();
    }

    @SneakyThrows
    public void send(String msg) {
        ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes());
        while (buffer.hasRemaining()) {
            socketChannel.write(buffer);
        }
    }
}
