package io.github.createsequence.nio.chat;

import cn.hutool.core.lang.Console;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

/**
 * @author huangchengxing
 */
public class SimpleChatClient {

    private SocketChannel socketChannel;
    private InputThread inputThread;
    private ReceiveThread receiveThread;
    private boolean started = false;

    public SimpleChatClient(String serverHost, int serverPort) {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(new java.net.InetSocketAddress(serverHost, serverPort));
            Console.log("连接到服务器: {}:{}", serverHost, serverPort);
            inputThread = new InputThread();
            receiveThread = new ReceiveThread();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        new InputThread().start();
        new ReceiveThread().start();
        started = true;
    }

    public void stop() {
        inputThread.interrupt();
        receiveThread.interrupt();
        started = false;
        Console.log("客户端已停止");
    }

    private class InputThread extends Thread {
        @Override
        public void run() {
            try {
                Scanner scanner = new Scanner(System.in);
                while (started) {
                    Console.log("请输入要发送的消息: ");
                    String message = scanner.nextLine();
                    ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
                    socketChannel.write(buffer);
                }
                Console.log("停止发送消息");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ReceiveThread extends Thread {
        @Override
        public void run() {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                while (started) {
                    int bytesRead = 0;
                    if (!socketChannel.isConnected()
                        || (bytesRead = socketChannel.read(buffer)) == -1) {
                        Console.log("服务器已断开连接");
                        break;
                    }
                    buffer.flip();
                    String receivedMessage = new String(buffer.array(), 0, bytesRead);
                    Console.log(receivedMessage);
                    buffer.clear();
                }
                Console.log("停止接收消息");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
