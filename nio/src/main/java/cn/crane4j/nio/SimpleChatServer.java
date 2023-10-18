package cn.crane4j.nio;

import cn.hutool.core.lang.Console;
import lombok.Cleanup;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 聊天服务器，具备以下基本功能：
 * <ul>
 *     <li>当建立客户端链接时，输出上线信息；</li>
 *     <li>当中断客户端链接时，输出下线信息；</li>
 *     <li>当客户端输出信息时，广播到其他的客户端；</li>
 * <ul>
 * 
 * @author huangchengxing
 */
@Accessors(chain = true)
public class SimpleChatServer {

    private static final int CONNECTION_STATUS_LOGIN_OUT = 0;
    private static final int CONNECTION_STATUS_LOGIN = 1;

    @Setter
    private String hostname = "localhost";
    @Setter
    private int port = 8888;
    @Setter
    private long eventLoopTimeout = 500L;
    @Setter
    private ExecutorService executorService = null;
    private boolean started = false;
    private final Map<SocketChannel, Connection> connectionMap = new ConcurrentHashMap<>(8);

    /**
     * 启动服务器
     */
    @SuppressWarnings("all")
    @SneakyThrows
    public void start() {
        @Cleanup Selector selector = Selector.open();
        @Cleanup ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // 绑定指定端口
        ServerSocket serverSocket = serverSocketChannel.socket();
        InetSocketAddress address = new InetSocketAddress(hostname, port);
        serverSocket.bind(address);
        Console.log("启动服务器，绑定端口: {}:{}", hostname, port);

        // 启动事件循环
        Console.log("启动事件循环超时时间: {}ms", eventLoopTimeout);
        this.started = true;
        startEvenLoop(selector);
    }

    private void startEvenLoop(Selector selector) throws IOException {
        while (this.started) {
            if (selector.select(eventLoopTimeout) < 1) {
                continue;
            }
            // 响应事件，若有线程池，则交给线程池处理
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            if (Objects.isNull(executorService)) {
                selectionKeys.forEach(k -> doProcess(selector, k));
            } else {
                selectionKeys.forEach(k -> executorService.execute(() -> doProcess(selector, k)));
            }
        }
    }

    @SneakyThrows
    private void doProcess(Selector selector, SelectionKey selectionKey) {
        try {
            if (selectionKey.isAcceptable()) {
                doAccept(selector, selectionKey);
            }
            else if (selectionKey.isReadable()) {
                Console.log("read from client: {}", doRead(selectionKey));
            }
        } catch (Exception ex) {
            Console.log("事件处理异常，错误信息: {}", ex.getMessage());
            ex.printStackTrace();
        }
    }



    /**
     * 选择器中存在已经准备建立链接的通道，
     * 令其与客户端建立链接，并将连接放入选择器
     *
     * @param selector selector
     * @param key key
     */
    protected void doAccept(Selector selector, SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        Console.log("chanel {} acceptable!", serverSocketChannel);
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        // 注册链接
        socketChannel.register(selector, SelectionKey.OP_READ);
        connectionMap.computeIfAbsent(socketChannel, k -> new Connection());
    }

    /**
     * 选择器中存在已经准备读取的通道（即通过{@link #doAccept}创建的），
     * 从通道读取数据，然后将其关闭。
     *
     * @param key key
     * @return 通道的数据
     */
    protected String doRead(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        Console.log("chanel {} readable!", socketChannel.toString());
        String result = readDataFromSocketChannel(socketChannel);
        socketChannel.close();
        return result;
    }

    /**
     * 从通道中读取数据
     *
     * @param socketChannel
     * @return java.lang.String
     */
    @SuppressWarnings("all")
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

    /**
     * 关闭服务器
     */
    public void stop() {
        started = false;
    }

    @Getter
    @Setter
    @Accessors(chain = true)
    @RequiredArgsConstructor
    private static class Connection {
        private int status = CONNECTION_STATUS_LOGIN_OUT;
        private String username = "匿名";
    }
}
