package com.fayu.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fayu.message.domain.po.UserConversation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserConversationMapper extends BaseMapper<UserConversation> {

    @Select("""
            SELECT id, user_id, conversation_id, is_pinned, pinned_at,
                   is_muted, last_read_seq, last_read_at, settings_version,
                   created_at, updated_at
            FROM user_conversation
            WHERE user_id = #{userId}
              AND conversation_id = #{conversationId}
            LIMIT 1
            """)
    UserConversation findOne(@Param("userId") Long userId,
                             @Param("conversationId") Long conversationId);

    @Update("""
            UPDATE user_conversation
            SET last_read_seq = GREATEST(last_read_seq, #{readSeq}),
                last_read_at = CASE
                    WHEN last_read_seq < #{readSeq} THEN NOW(3)
                    ELSE last_read_at
                END,
                updated_at = NOW(3)
            WHERE user_id = #{userId}
              AND conversation_id = #{conversationId}
            """)
    int advanceReadSeq(@Param("userId") Long userId,
                       @Param("conversationId") Long conversationId,
                       @Param("readSeq") Long readSeq);
}
