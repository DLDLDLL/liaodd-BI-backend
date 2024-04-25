package com.yupi.springbootinit.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@ServerEndpoint("/websocket/{userId}")
public class WebSocketServer {
    /**
     * 静态成员，存放所有websocket连接对象
     */
    private static CopyOnWriteArraySet<WebSocketServer> websockets = new CopyOnWriteArraySet<>();
    /**
     * 静态成员，在线人数
     */
    private static AtomicInteger onlineCount = new AtomicInteger(0);
    /**
     * 当前用户id
     */
    private String userId = "";
    /**
     * 当前连接会话
     */
    private Session session;

    /**
     * 连接建立成功调用的方法
     *
     * @param session
     * @param userid
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userid) {
        this.session = session;
        this.userId = userid;
        websockets.add(this);
        addOnlineCount();
        log.info("有新客户端开始监听,userId=" + userid + ",当前在线人数为:" + getOnlineCount());
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        websockets.remove(this);
        subOnlineCount();
        log.info("释放的userId=" + userId + "的客户端");
        releaseResource();
    }

    public void releaseResource() {
        log.info("有一连接关闭！当前在线人数为" + getOnlineCount());
    }

    /**
     * 发生错误回调的方法
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error(session.getBasicRemote() + "客户端发生错误");
        error.printStackTrace();
    }

    /**
     * 收到客户端消息后的方法
     *
     * @param message
     * @param session
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("收到来自客户端 userId=" + userId + " 的信息:" + message);
        // 群发消息
        HashSet<String> userIds = new HashSet<>();
        for (WebSocketServer item : websockets) {
            userIds.add(item.userId);
        }
        try {
            sendMessage("客户端 " + this.userId + "发布消息：" + message, userIds);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 群发自定义消息
     *
     * @param message
     * @param toSids
     */
    public void sendMessage(String message, HashSet<String> toSids) throws IOException {
        log.info("推送消息到客户端 " + toSids + "，推送内容:" + message);

        for (WebSocketServer item : websockets) {
            try {
                //这里可以设定只推送给传入的userId，为null则全部推送
                if (toSids.size() == 0 || toSids.contains(item.userId)) {
                    item.sendMessage(message);
                }
            } catch (IOException e) {
                continue;
            }
        }
    }

    /**
     * 服务端向客户端发送消息
     *
     * @param message
     * @throws IOException
     */
    private void sendMessage(String message) throws IOException {
        this.session.getAsyncRemote().sendText(message);
    }


    /**
     * 获取在线人数
     *
     * @return
     */
    public static int getOnlineCount() {
        return onlineCount.get();
    }

    /**
     * 增加在线人数
     */
    public static void addOnlineCount() {
        onlineCount.getAndIncrement();
    }

    /**
     * 减少在线人数
     */
    public static void subOnlineCount() {
        onlineCount.getAndDecrement();
    }

    /**
     * 获取websocket对象集合
     *
     * @return
     */
    public static CopyOnWriteArraySet<WebSocketServer> getWebsockets() {
        return websockets;
    }

}
