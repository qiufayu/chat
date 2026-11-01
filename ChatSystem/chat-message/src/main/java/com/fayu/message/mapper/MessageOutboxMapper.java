package com.fayu.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fayu.common.domain.po.MessageOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息可靠投递事件表 Mapper 接口
 */
@Mapper
public interface MessageOutboxMapper extends BaseMapper<MessageOutbox> {

    @Select("""
            SELECT id,
                   message_id AS messageId,
                   conversation_id AS conversationId,
                   seq,
                   event_type AS eventType,
                   event_key AS eventKey,
                   payload,
                   status,
                   retry_count AS retryCount,
                   next_retry_at AS nextRetryAt,
                   last_error AS lastError,
                   locked_at AS lockedAt,
                   locked_by AS lockedBy,
                   published_at AS publishedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM message_outbox
            WHERE (
                status IN (0, 1)
                OR (status = 3 AND next_retry_at IS NOT NULL AND next_retry_at <= #{now})
            )
            AND (locked_at IS NULL OR locked_at < #{staleBefore})
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<MessageOutbox> findDispatchCandidates(@Param("now") LocalDateTime now,
                                               @Param("staleBefore") LocalDateTime staleBefore,
                                               @Param("limit") int limit);

    @Update("""
            UPDATE message_outbox
            SET locked_at = NOW(3),
                locked_by = #{lockedBy},
                updated_at = NOW(3)
            WHERE id = #{outboxId}
              AND status IN (0, 1, 3)
              AND (locked_at IS NULL OR locked_at < #{staleBefore})
            """)
    int claim(@Param("outboxId") Long outboxId,
              @Param("lockedBy") String lockedBy,
              @Param("staleBefore") LocalDateTime staleBefore);
}
