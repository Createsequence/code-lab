package io.github.createsequence.nio.chat;

/**
 * @author huangchengxing
 */
public class Chat2 {

    public static void main(String[] args) {
        SimpleChatClient client = new SimpleChatClient("localhost", 8888);
        client.start();
    }
}
