package com.fayu.group.listener;

import cn.hutool.core.bean.BeanUtil;
import com.fayu.common.exception.BaseException;
import com.fayu.group.domain.dto.UpdateGroupDTO;
import com.fayu.group.server.IGroupInfoServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.fayu.common.constant.RabbitmqConstant.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AddMemberListener {

    private final IGroupInfoServer groupInfoServer;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = GROUP_ADD_MEMBER_QUEUE,declare = "true",durable = "true"),
            exchange = @Exchange(name = GROUP_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = {KEY_ADD_MEMBER}
        )
    )
    public void addMemberListener(UpdateGroupDTO updateGroupDTO){
        log.debug("收到消息:{}", updateGroupDTO);
        try {
            groupInfoServer.updateGroupInfo(updateGroupDTO);
        } catch (Exception e) {
            throw new BaseException("群信息跟新失败",500);
        }
    }

}
