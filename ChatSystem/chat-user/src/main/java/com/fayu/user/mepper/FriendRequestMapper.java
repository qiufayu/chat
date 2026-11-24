package com.fayu.user.mepper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fayu.user.domain.po.FriendRequest;
import com.fayu.user.domain.vo.FriendRequestVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

public interface FriendRequestMapper extends BaseMapper<FriendRequest> {
    @Select("""
            SELECT * FROM friend_request
            WHERE applicant_id = #{applicantId} AND receiver_id = #{receiverId} AND status = 'PENDING'
            ORDER BY id DESC LIMIT 1 FOR UPDATE
            """)
    FriendRequest findPending(@Param("applicantId") Long applicantId, @Param("receiverId") Long receiverId);

    @Select("""
            SELECT r.*, a.account AS applicant_account, a.nickname AS applicant_nickname,
                   b.account AS receiver_account, b.nickname AS receiver_nickname
            FROM friend_request r
            JOIN users a ON a.id = r.applicant_id JOIN users b ON b.id = r.receiver_id
            WHERE r.id = #{id}
            """)
    FriendRequestVO findView(@Param("id") Long id);

    @Select("""
            <script>
            SELECT r.*, a.account AS applicant_account, a.nickname AS applicant_nickname,
                   b.account AS receiver_account, b.nickname AS receiver_nickname
            FROM friend_request r
            JOIN users a ON a.id = r.applicant_id JOIN users b ON b.id = r.receiver_id
            WHERE <choose><when test="incoming">r.receiver_id</when><otherwise>r.applicant_id</otherwise></choose> = #{userId}
            <if test="beforeId != null">AND r.id &lt; #{beforeId}</if>
            ORDER BY r.id DESC LIMIT #{limit}
            </script>
            """)
    List<FriendRequestVO> listRequests(@Param("userId") Long userId, @Param("incoming") boolean incoming,
                                      @Param("beforeId") Long beforeId, @Param("limit") int limit);

    @Update("""
            UPDATE friend_request SET status =
              CASE WHEN expires_at <= #{now} THEN 'EXPIRED' ELSE #{status} END, handled_at = #{now}
            WHERE status = 'PENDING'
              AND ((applicant_id = #{first} AND receiver_id = #{second})
                OR (applicant_id = #{second} AND receiver_id = #{first}))
            """)
    int finishPending(@Param("first") Long first, @Param("second") Long second,
                      @Param("status") String status, @Param("now") LocalDateTime now);
}
