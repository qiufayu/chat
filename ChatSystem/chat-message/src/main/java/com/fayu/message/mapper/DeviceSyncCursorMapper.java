package com.fayu.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fayu.message.domain.po.DeviceSyncCursor;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DeviceSyncCursorMapper extends BaseMapper<DeviceSyncCursor> {

    @Select("""
            SELECT id, user_id, device_id, conversation_id, last_synced_seq,
                   created_at, updated_at
            FROM device_sync_cursor
            WHERE user_id = #{userId}
              AND device_id = #{deviceId}
              AND conversation_id = #{conversationId}
            LIMIT 1
            """)
    DeviceSyncCursor findOne(@Param("userId") Long userId,
                             @Param("deviceId") String deviceId,
                             @Param("conversationId") Long conversationId);

    @Insert("""
            INSERT INTO device_sync_cursor
                (user_id, device_id, conversation_id, last_synced_seq, created_at, updated_at)
            VALUES
                (#{userId}, #{deviceId}, #{conversationId}, #{seq}, NOW(3), NOW(3))
            ON DUPLICATE KEY UPDATE
                last_synced_seq = GREATEST(last_synced_seq, VALUES(last_synced_seq)),
                updated_at = NOW(3)
            """)
    int advance(@Param("userId") Long userId,
                @Param("deviceId") String deviceId,
                @Param("conversationId") Long conversationId,
                @Param("seq") Long seq);
}
