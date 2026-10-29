package com.fayu.message.listener;

import cn.hutool.core.bean.BeanUtil;
import com.fayu.message.domain.dto.SaveOffMessageBoxDTO;
import com.fayu.message.domain.po.OffMessageBox;
import com.fayu.message.server.IOffMessageBoxServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.fayu.common.constant.RabbitmqConstant.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageListener {

    private final IOffMessageBoxServer offMessageBoxServer;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = OFF_MESSAGE_QUEUE,durable = "true"),
                    exchange = @Exchange(name = MESSAGE_EXCHANGE,type = "topic"),
                    key = {KEY_SAVE_OFF_MESSAGE}
            )
    )
    public void saveOffMessageListener(SaveOffMessageBoxDTO offMessageBoxDTO){
        OffMessageBox offMessageBox = BeanUtil.copyProperties(offMessageBoxDTO, OffMessageBox.class);
        offMessageBoxServer.save(offMessageBox);
        log.debug("添加离线消息成功 messageId={},userId={}",offMessageBoxDTO.getMessageId(),offMessageBoxDTO.getUserId());
    }

}
