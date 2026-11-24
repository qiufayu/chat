package com.fayu.connection;

import com.fayu.api.client.MsgClient;
import com.fayu.api.client.UserClient;
import com.fayu.common.domain.dto.ChatRequestMessageDTO;
import com.fayu.common.domain.vo.SaveMsgVO;
import com.fayu.common.utils.UserContext;
import com.fayu.connection.domain.po.Message;
import com.fayu.connection.executor.ChatBusinessExecutor;
import com.fayu.connection.factory.SingleSessionFactory;
import com.fayu.connection.handler.ChatMessageHandler;
import com.fayu.connection.utils.MessageUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FriendMessageAuthorizationTest {
    MsgClient messages;
    UserClient users;
    EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        messages = mock(MsgClient.class);
        users = mock(UserClient.class);
        ChatBusinessExecutor executor = mock(ChatBusinessExecutor.class);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(executor).execute(any(Runnable.class));
        channel = new EmbeddedChannel(new ChatMessageHandler(messages, executor, new MessageUtil(), mock(RabbitTemplate.class), users));
        var session = SingleSessionFactory.getSession();
        session.setAttr(channel, "userId", 1L);
        session.setAttr(channel, "deviceId", "device-one");
        session.bind(channel, 1L, "device-one");
        UserContext.removeUser();
    }

    @AfterEach
    void tearDown() {
        SingleSessionFactory.getSession().unBind(channel, 1L);
        channel.finishAndReleaseAll();
        UserContext.removeUser();
    }

    @Test
    void databaseDenialRejectsSendBeforeOutboxPersistence() {
        when(users.verifyFriend(any(), eq(1L))).thenReturn(false);
        channel.writeInbound(chat());
        assertEquals("SEND_ERROR", responseType());
        verifyNoInteractions(messages);
    }

    @Test
    void friendServiceOutageFailsClosed() {
        when(users.verifyFriend(any(), eq(1L))).thenThrow(new IllegalStateException("user service unavailable"));
        channel.writeInbound(chat());
        assertEquals("SEND_ERROR", responseType());
        verifyNoInteractions(messages);
    }

    @Test
    void acceptedFriendSendsWithoutRedisAndUsesChannelIdentity() {
        when(users.verifyFriend(any(), eq(1L))).thenReturn(true);
        when(messages.saveOutbox(any())).thenReturn(SaveMsgVO.builder().messageId(10L).conversationId(20L)
                .seq(1L).timeAt(1000L).reuseMessage(true).build());
        channel.writeInbound(chat());
        assertEquals("SAVED_ACK", responseType());
        verify(users).verifyFriend(argThat(dto -> dto.getSenderId().equals(1L) && dto.getToId().equals(2L)), eq(1L));
        verify(messages).saveOutbox(argThat(dto -> dto.getSenderId().equals(1L) && dto.getToId().equals(2L)));
        assertNull(UserContext.getUser());
    }

    private Message<ChatRequestMessageDTO> chat() {
        ChatRequestMessageDTO payload = new ChatRequestMessageDTO();
        payload.setMessageType(1);
        payload.setContent("hello");
        return Message.<ChatRequestMessageDTO>builder().type("CHAT").senderId(999L).to(2L)
                .clientMessageId("test").payload(payload).build();
    }

    private String responseType() {
        TextWebSocketFrame frame = channel.readOutbound();
        assertNotNull(frame);
        try { return new MessageUtil().toJsonNode(frame.text()).get("type").asText(); }
        finally { frame.release(); }
    }
}
