package io.github.createsequence.nio;

import cn.hutool.core.lang.Console;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @author huangchengxing
 */
@Accessors(chain = true)
public class NIOServer implements Closeable {

    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    private boolean started = false;

    public static void main(String[] args) throws IOException {
        try (NIOServer server = new NIOServer()) {
            server.start("localhost", 8888);
        }
    }

    @SneakyThrows
    @SuppressWarnings("all")
    public void start(String hostname, int port) {
        // 配置选择器与服务器通道
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // 绑定服务器端口8888
        ServerSocket serverSocket = serverSocketChannel.socket();
        InetSocketAddress address = new InetSocketAddress(hostname, port);
        serverSocket.bind(address);

        this.started = true;
        Console.log("server started on {}:{}", hostname, port);

        // 开启事件循环，在这个循环中，将会反复的响应选择器中的事件，直到关闭为止
        while (this.started) {
            starEvenLoop(selector);
        }
        Console.log("server stopped!");
    }

    @SneakyThrows
    public void stop() {
        Console.log("server stopping...");
        this.started = false;
        close();
        this.selector = null;
        this.serverSocketChannel = null;
    }

    @SneakyThrows
    private void starEvenLoop(Selector selector) {
        // 阻塞1s,检查是否有就绪的通道，没有就结束，并等待下次调用
        if (selector.select(1000L) < 1) {
            return;
        }

        // 获取就绪通道的 SelectionKey 集合
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> keyIterator = keys.iterator();
        while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            if (key.isAcceptable()) {
                doAccept(selector, key);
            }
            else if (key.isReadable()) {
                Console.log("read from client: {}", doRead(key));
            }
            keyIterator.remove();
        }
    }

    /**
     * 选择器中存在已经准备建立链接的通道，
     * 令其与客户端建立链接，并将连接放入选择器
     *
     * @param selector selector
     * @param key key
     */
    private static void doAccept(Selector selector, SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        Console.log("chanel {} acceptable!", serverSocketChannel);
        // 服务器会为每个新连接创建一个 SocketChannel
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        // 这个新连接主要用于从客户端读取数据
        socketChannel.register(selector, SelectionKey.OP_READ);
    }

    /**
     * 选择器中存在已经准备读取的通道（即通过{@link #doAccept}创建的），
     * 从通道读取数据，然后将其关闭。
     *
     * @param key key
     * @return 通道的数据
     */
    private static String doRead(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Console.log("chanel {} readable!", socketChannel.toString());
        String result = readDataFromSocketChannel(socketChannel);
        socketChannel.close();
        return result;
    }

    private static String readDataFromSocketChannel(SocketChannel socketChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        StringBuilder data = new StringBuilder();
        while (socketChannel.read(buffer) > -1) {
            buffer.flip();
            int limit = buffer.limit();
            char[] dst = new char[limit];
            for (int i = 0; i < limit; i++) {
                dst[i] = (char) buffer.get(i);
            }
            data.append(dst);
            buffer.clear();
        }
        return data.toString();
    }

    @Override
    public void close() throws IOException {
        selector.close();
        serverSocketChannel.close();
    }
}
