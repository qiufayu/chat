package com.fayu.user.mepper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fayu.user.domain.po.UserFriend;
import com.fayu.user.domain.vo.FriendVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import java.util.List;

public interface UserFriendMapper extends BaseMapper<UserFriend> {
    @Select("""
            SELECT COUNT(*) FROM user_friend a
            JOIN user_friend b ON b.user_id = a.friend_id AND b.friend_id = a.user_id
            JOIN users u ON u.id = a.user_id JOIN users v ON v.id = a.friend_id
            WHERE a.user_id = #{userId} AND a.friend_id = #{friendId}
              AND a.status = 1 AND b.status = 1 AND a.is_blocked = 0 AND b.is_blocked = 0
              AND u.status = 1 AND v.status = 1
            """)
    int countUsablePair(@Param("userId") Long userId, @Param("friendId") Long friendId);

    @Select("""
            SELECT COUNT(*) FROM user_friend
            WHERE ((user_id = #{first} AND friend_id = #{second})
                OR (user_id = #{second} AND friend_id = #{first})) AND is_blocked = 1
            """)
    int countBlockedPair(@Param("first") Long first, @Param("second") Long second);

    @Insert("""
            INSERT INTO user_friend (user_id, friend_id, status, is_blocked, created_at)
            VALUES (#{userId}, #{friendId}, 1, 0, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE status = 1
            """)
    int restoreFriend(@Param("userId") Long userId, @Param("friendId") Long friendId);

    @Update("""
            UPDATE user_friend SET status = 0
            WHERE (user_id = #{first} AND friend_id = #{second})
               OR (user_id = #{second} AND friend_id = #{first})
            """)
    int deletePair(@Param("first") Long first, @Param("second") Long second);

    @Select("""
            SELECT u.id AS friend_id, u.account, u.nickname, u.avatar, f.remark
            FROM user_friend f JOIN users u ON u.id = f.friend_id
            JOIN user_friend reverse_friend ON reverse_friend.user_id = f.friend_id
              AND reverse_friend.friend_id = f.user_id
            WHERE f.user_id = #{userId} AND f.status = 1 AND reverse_friend.status = 1
              AND f.friend_id > #{afterId}
            ORDER BY f.friend_id LIMIT #{limit}
            """)
    List<FriendVO> listFriends(@Param("userId") Long userId, @Param("afterId") long afterId,
                               @Param("limit") int limit);

}
