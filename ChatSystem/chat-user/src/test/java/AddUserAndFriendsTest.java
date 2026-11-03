import com.fayu.common.utils.UserContext;
import com.fayu.user.ChatUserApplication;
import com.fayu.user.domain.dto.ApplyFormDTO;
import com.fayu.user.domain.dto.RegisterFormDTO;
import com.fayu.user.domain.vo.UserRegisterVO;
import com.fayu.user.server.IUserFriendServer;
import com.fayu.user.server.IUserServer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SpringBootTest(classes = ChatUserApplication.class)
@ActiveProfiles("local")
public class AddUserAndFriendsTest {

    @Autowired
    private IUserServer userServer;
    @Autowired
    private IUserFriendServer userFriendServer;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void start(){
        int a = 2;
        UserContext.setUser(4L);
        for (int i = 1 ; i < 1000 ; i++) {
            log.info("正在注册第{}个用户",i);
            RegisterFormDTO registerFormDTO = new RegisterFormDTO();
            registerFormDTO.setNickname(UUID.randomUUID().toString());
            long phone = 10000000000L+i;
            registerFormDTO.setPhone(Long.toString(phone));
            registerFormDTO.setPassword("123456");
            UserRegisterVO register = userServer.register(registerFormDTO);
            ApplyFormDTO applyFormDTO = new ApplyFormDTO();
            applyFormDTO.setAccount(register.getAccount());
            userFriendServer.apply(applyFormDTO);
        }
    }

    @Test
    public void testAddRedisCacheOfUserFriend(){
        System.out.println("===== 测试添加用户好友缓存 =====");
        String keyPrefix = "user:friend:";
        int startId = 1008;
        int endId = 1107;
        for (int i = startId ; i <= endId ; i++) {
            // 1. 获取当前用户的 Redis Key
            String currentUserKey = keyPrefix + i;
            // 2. 清空一下旧数据，防止你重复跑测试导致数据无限叠加
            stringRedisTemplate.delete(currentUserKey);
            stringRedisTemplate.opsForSet().add(currentUserKey, String.valueOf(i+100));
        }
    }

    /**
     * 为 JMeter 准备 100 对好友：按 ID 排序，第 i 个用户与第 i+100 个用户配对。
     * 与 pairs.csv 的生成规则一致；运行后保留数据库关系和 Redis 缓存。
     */
    @Test
    public void testAdd100PairsOfFriends() {
        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE id > 1000 ORDER BY id LIMIT 200",
                Long.class);
        assertEquals(200, userIds.size(), "至少需要 200 个 ID 大于 1000 的用户");

        // 先提交数据库中的 200 条双向关系，已有关系恢复为正常、未拉黑状态。
        // 不使用测试方法级 @Transactional，避免测试结束时回滚准备的数据。
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int i = 0; i < 100; i++) {
                Long userId = userIds.get(i);
                Long friendId = userIds.get(i + 100);
                upsertTestFriend(userId, friendId);
                upsertTestFriend(friendId, userId);
            }
        });

        // 数据库提交后补齐双向缓存，SADD 支持重复执行，保留其他好友。
        for (int i = 0; i < 100; i++) {
            Long userId = userIds.get(i);
            Long friendId = userIds.get(i + 100);
            stringRedisTemplate.opsForSet().add("user:friend:" + userId, friendId.toString());
            stringRedisTemplate.opsForSet().add("user:friend:" + friendId, userId.toString());
            log.info("第 {} 对压测好友：{} <-> {}", i + 1, userId, friendId);
        }
        log.info("100 对压测好友准备完成，已写入双向数据库关系和 Redis 缓存");
    }

    private void upsertTestFriend(Long userId, Long friendId) {
        jdbcTemplate.update("""
                INSERT INTO user_friend (user_id, friend_id, remark, status, is_blocked, created_at)
                VALUES (?, ?, ?, 1, 0, NOW())
                ON DUPLICATE KEY UPDATE status = 1, is_blocked = 0
                """, userId, friendId, "压测好友-" + friendId);
    }

}
