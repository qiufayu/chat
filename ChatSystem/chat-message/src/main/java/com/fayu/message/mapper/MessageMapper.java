package com.fayu.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fayu.message.domain.po.Message;
import com.fayu.message.domain.po.MessageInfoRow;
import com.fayu.common.domain.po.MessageOutbox;
import com.fayu.common.domain.vo.ChatResponseMessageVO;
import com.fayu.common.domain.vo.SyncMessageVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MessageMapper extends BaseMapper<Message> {
    List<MessageOutbox> queryRetryMessages();

    MessageInfoRow getMessageInfo(@Param("conversationId") Long conversationId,
                                  @Param("seq") Long seq);

    List<SyncMessageVO> listSyncMessages(@Param("userId") Long userId,
                                         @Param("conversationId") Long conversationId,
                                         @Param("afterSeq") Long afterSeq,
                                         @Param("limit") Integer limit);

    List<SyncMessageVO> intervalSyncMessages(@Param("userId")Long userId,
                                             @Param("conversationId") Long conversationId,
                                             @Param("start") Long start,
                                             @Param("end") Long end,
                                             @Param("pageSize")int pageSize
    );

    Long selectMaxSeqByConversationId(@Param("conversationId") Long conversationId);
}
