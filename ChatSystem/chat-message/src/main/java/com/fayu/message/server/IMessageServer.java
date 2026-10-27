package com.fayu.message.server;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fayu.message.domain.dto.SaveMsgDTO;
import com.fayu.message.domain.po.Message;
import com.fayu.message.domain.vo.SaveMsgVO;

public interface IMessageServer extends IService<Message> {

    SaveMsgVO saveMsg(SaveMsgDTO saveMsgDTO);
}