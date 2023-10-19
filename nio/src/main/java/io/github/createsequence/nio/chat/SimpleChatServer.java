package io.github.createsequence.nio.chat;

import cn.hutool.core.lang.Console;
import cn.hutool.core.text.CharSequenceUtil;
import lombok.Cleanup;
import lombok.Getter;
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
import java.util.Collection;
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

    public static void main(String[] args) {
        SimpleChatServer server = new SimpleChatServer();
        server.start();
    }

    @Setter
    private String hostname = "localhost";
    @Setter
    private int port = 8888;
    @Setter
    private long eventLoopTimeout = 500L;
    @Setter
    private ExecutorService executorService = null;
    private boolean started = false;
    private final Map<SocketChannel, ClientConnection> connectionMap = new ConcurrentHashMap<>(8);

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
        Console.log("服务器已关闭！");
    }

    /**
     * 关闭服务器
     */
    public void stop() {
        Console.log("关闭服务器...");
        started = false;
    }

    private void startEvenLoop(Selector selector) throws IOException {
        while (this.started) {
            if (selector.select() < 1) {
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
                doRead(selector, selectionKey);
            }
        } catch (Exception ex) {
            Console.log("事件处理异常，错误信息: {}", ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 将链接放入选择器，并注册对应的{@link ClientConnection}
     *
     * @param selector selector
     * @param key key
     */
    protected void doAccept(Selector selector, SelectionKey key) throws IOException {
        @Cleanup ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        // 注册链接
        socketChannel.register(selector, SelectionKey.OP_READ);
        registerConnection(socketChannel);
    }

    /**
     * 从通道中读取数据，然后广播给其他客户端
     *
     * @param selector selector
     * @param key key
     */
    protected void doRead(Selector selector, SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        if (!socketChannel.isConnected()) {
            unregisterConnection(key, socketChannel);
        }

        // 如果当前用户未在线，则标记为在线
        try {
            // TODO 支持用户名、与基于用户名的单播放、多播等功能
            // 收到消息后，广播给其他客户端
            ClientConnection clientConnection = connectionMap.get(socketChannel);
            String result = readDataFromSocketChannel(socketChannel);
            Set<SelectionKey> targets = selector.keys();
            targets.remove(key);
            if (!targets.isEmpty()) {
                Console.log("{} 向 {} 个在线客户端广播消息: {}", clientConnection.getUsername(), targets.size(), result);
                result = CharSequenceUtil.format("{} 说: {}", clientConnection.getUsername(), result);
                broadcast(result, targets);
            }

        } catch (IOException ex) {
            unregisterConnection(key, socketChannel);
        }
    }

    private void registerConnection(SocketChannel socketChannel) {
        connectionMap.computeIfAbsent(socketChannel, k -> {
            try {
                ClientConnection connection = new ClientConnection(k.getRemoteAddress().toString());
                Console.log("客户端 {} 已上线！", connection.getUsername());
                return connection;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @SneakyThrows
    private void unregisterConnection(SelectionKey key, SocketChannel socketChannel) {
        Console.log("客户端 {} 已下线！", socketChannel);
        connectionMap.remove(socketChannel);
        key.cancel();
        socketChannel.close();
    }

    @SuppressWarnings("all")
    private void broadcast(String msg, Collection<SelectionKey> targets) {
        for (SelectionKey target : targets) {
            // 跳过非 SocketChannel 或已断开连接的通道
            if (!(target.channel() instanceof SocketChannel channel) || !channel.isConnected()) {
                continue;
            }
            try {
                channel.write(ByteBuffer.wrap(msg.getBytes()));
            } catch (IOException ex) {
                unregisterConnection(target, channel);
            }
        }
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

    @Getter
    @Setter
    @Accessors(chain = true)
    private static class ClientConnection {
        private String username;
        public ClientConnection(String address) {
            this.username = address + "@" + System.currentTimeMillis();
        }
    }
}
