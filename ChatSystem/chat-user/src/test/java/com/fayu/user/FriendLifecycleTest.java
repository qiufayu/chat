package com.fayu.user;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fayu.api.domain.dto.VerifyMsgDTO;
import com.fayu.common.exception.CommonException;
import com.fayu.common.handler.GlobalExceptionHandler;
import com.fayu.common.interceptor.UserInfoInterceptor;
import com.fayu.common.utils.UserContext;
import com.fayu.user.controller.FriendController;
import com.fayu.user.domain.dto.ApplyFormDTO;
import com.fayu.user.domain.vo.FriendRequestVO;
import com.fayu.user.mepper.FriendRequestMapper;
import com.fayu.user.mepper.UserMapper;
import com.fayu.user.server.IUserFriendServer;
import com.fayu.user.server.impl.UserFriendServerImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real mapper/transaction tests on a private H2 database; no local profile or live middleware. */
@SpringJUnitConfig(FriendLifecycleTest.Config.class)
class FriendLifecycleTest {
    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.fayu.user.mepper")
    static class Config {
        @Bean
        DataSource dataSource() throws Exception {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:friend_lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
            // Exercise the actual initial user schema and incremental migration, rather than a mock schema.
            String schema = Files.readString(Path.of("../../database/database.sql"));
            schema = schema.substring(schema.indexOf("CREATE TABLE `users`"), schema.indexOf("CREATE DATABASE group_server;"));
            try (var connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(schema.getBytes(StandardCharsets.UTF_8)));
                String migration = Files.readString(Path.of("../../database/migrations/20261124_friend_request.sql"));
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8)));
            }
            return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
            bean.setDataSource(source);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            bean.setConfiguration(configuration);
            return bean.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        StringRedisTemplate redisTemplate() { return mock(StringRedisTemplate.class); }

        @Bean
        UserFriendServerImpl friendServer(UserMapper users, FriendRequestMapper requests, StringRedisTemplate redis) {
            return new UserFriendServerImpl(users, requests, redis);
        }
    }

    @Autowired IUserFriendServer friends;
    @Autowired UserMapper userMapper;
    @Autowired DataSource source;
    @Autowired StringRedisTemplate redis;
    @Autowired PlatformTransactionManager transactions;
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(source);
        jdbc.update("DELETE FROM friend_request");
        jdbc.update("DELETE FROM user_friend");
        jdbc.update("DELETE FROM users");
        for (long id = 1; id <= 3; id++) {
            jdbc.update("INSERT INTO users (id, account, phone, nickname, password, status) VALUES (?, ?, ?, ?, 'hash', 1)",
                    id, "account" + id, "phone" + id, "name" + id);
        }
        reset(redis);
        UserContext.setUser(1L);
    }

    @AfterEach
    void clearIdentity() { UserContext.removeUser(); }

    @Test
    void newlyRegisteredNormalAccountsCanLoginAndEnterFriendFlow() {
        var encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        var tokens = mock(com.fayu.user.utils.JwtTool.class);
        var ids = mock(com.fayu.common.utils.RedisIdWorker.class);
        var properties = new com.fayu.user.config.JwtProperties();
        @SuppressWarnings("unchecked")
        var values = (org.springframework.data.redis.core.ValueOperations<String, String>) mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(ids.generateId(anyString())).thenReturn("new-account");
        when(encoder.encode(anyString())).thenReturn("hash");
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokens.createToken(anyLong(), any())).thenReturn("token");
        var users = new com.fayu.user.server.impl.UserServerImpl(encoder, tokens, properties, ids, redis);
        org.springframework.test.util.ReflectionTestUtils.setField(users, "baseMapper", userMapper);
        var registration = new com.fayu.user.domain.dto.RegisterFormDTO();
        registration.setPhone("new-phone");
        registration.setNickname("new-user");
        registration.setPassword("password");
        assertEquals("new-account", users.register(registration).getAccount());
        Long newId = jdbc.queryForObject("SELECT id FROM users WHERE account = 'new-account'", Long.class);
        assertEquals(1, jdbc.queryForObject("SELECT status FROM users WHERE id = ?", Integer.class, newId));
        var login = new com.fayu.user.domain.dto.LoginFormDTO();
        login.setAccount("new-account");
        login.setPassword("password");
        assertNotNull(users.login(login));
        UserContext.setUser(newId);
        assertEquals("PENDING", apply(1).getStatus());
        jdbc.update("UPDATE users SET status = 0 WHERE id = ?", newId);
        assertCode(403, () -> users.login(login));
    }

    @Test
    void fullLifecycleAndReapplyKeepExistingRelationshipRowsAndRemarks() {
        FriendRequestVO request = apply(2);
        assertEquals("PENDING", request.getStatus());
        assertEquals(request.getId(), apply(2).getId());
        assertFalse(verify(2));
        assertEquals(0, count("user_friend"));
        assertEquals(1, friends.requests("outgoing", null, 50).size());
        UserContext.setUser(2L);
        assertEquals(request.getId(), friends.requests("incoming", null, 50).get(0).getId());
        assertEquals("ACCEPTED", friends.accept(request.getId()).getStatus());
        assertTrue(verify(1));
        assertEquals(2, count("user_friend"));
        assertEquals("account1", friends.friends(0, 50).get(0).getAccount());
        assertEquals("ACCEPTED", friends.accept(request.getId()).getStatus());
        jdbc.update("UPDATE user_friend SET remark = 'old remark' WHERE user_id = 1");
        UserContext.setUser(1L);
        assertTrue(verify(2));
        friends.deleteFriend(2L);
        friends.deleteFriend(2L);
        assertFalse(verify(2));
        assertTrue(friends.friends(0, 50).isEmpty());
        UserContext.setUser(2L);
        assertFalse(verify(1));
        // A retry from another device reports the terminal result without recreating the relationship.
        assertEquals("ACCEPTED", friends.accept(request.getId()).getStatus());
        assertFalse(verify(1));
        FriendRequestVO next = apply(1);
        assertNotEquals(request.getId(), next.getId());
        UserContext.setUser(1L);
        friends.accept(next.getId());
        assertTrue(verify(2));
        assertEquals(2, count("user_friend"));
        assertEquals("old remark", friends.friends(0, 50).get(0).getRemark());
        org.mockito.Mockito.verify(redis, atLeastOnce()).delete(List.of("user:friend:1", "user:friend:2"));
    }

    @Test
    void crossedRequestsRequireConsentAndCannotResurrectAfterDelete() {
        FriendRequestVO first = apply(2);
        UserContext.setUser(2L);
        FriendRequestVO second = apply(1);
        assertFalse(verify(1));
        friends.accept(first.getId());
        assertEquals("ACCEPTED", friends.requests("outgoing", null, 50).get(0).getStatus());
        friends.deleteFriend(1L);
        UserContext.setUser(1L);
        assertEquals("ACCEPTED", friends.accept(second.getId()).getStatus());
        assertEquals("ACCEPTED", friends.reject(second.getId()).getStatus());
        assertFalse(verify(2));
    }

    @Test
    void rejectionExpiryAndDeletionEndOldRequests() {
        FriendRequestVO first = apply(2);
        UserContext.setUser(2L);
        assertEquals("REJECTED", friends.reject(first.getId()).getStatus());
        assertEquals("REJECTED", friends.accept(first.getId()).getStatus());
        UserContext.setUser(1L);
        FriendRequestVO next = apply(2);
        jdbc.update("UPDATE friend_request SET expires_at = DATEADD('DAY', -1, CURRENT_TIMESTAMP) WHERE id = ?", next.getId());
        assertEquals("EXPIRED", friends.requests("outgoing", null, 50).get(0).getStatus());
        UserContext.setUser(2L);
        assertEquals("EXPIRED", friends.accept(next.getId()).getStatus());
        UserContext.setUser(1L);
        FriendRequestVO cancelled = apply(2);
        friends.deleteFriend(2L);
        UserContext.setUser(2L);
        assertEquals("CANCELLED", friends.accept(cancelled.getId()).getStatus());
        assertFalse(verify(1));
    }

    @Test
    void expiredDuplicateGetsANewIdAndSelfMissingOrInactiveUsersAreRejected() {
        FriendRequestVO first = apply(2);
        jdbc.update("UPDATE friend_request SET expires_at = DATEADD('DAY', -1, CURRENT_TIMESTAMP) WHERE id = ?", first.getId());
        assertNotEquals(first.getId(), apply(2).getId());
        assertEquals("EXPIRED", jdbc.queryForObject("SELECT status FROM friend_request WHERE id = ?", String.class, first.getId()));
        assertCode(400, () -> apply(1));
        assertCode(404, () -> apply(99));
        jdbc.update("UPDATE users SET status = 2 WHERE id = 3");
        assertCode(403, () -> apply(3));
        assertCode(400, () -> friends.requests("all", null, 50));
        assertCode(400, () -> friends.friends(0, 101));
        assertEquals("account2", friends.search(null, 2L).getAccount());
    }

    @Test
    void onlyReceiverMayDecideAndSenderIdentityCannotBeForged() {
        FriendRequestVO request = apply(2);
        assertCode(403, () -> friends.accept(request.getId()));
        UserContext.setUser(3L);
        assertCode(403, () -> friends.reject(request.getId()));
        VerifyMsgDTO forged = new VerifyMsgDTO();
        forged.setSenderId(1L);
        forged.setToId(2L);
        assertCode(403, () -> friends.verify(forged));
        UserContext.removeUser();
        assertCode(401, () -> apply(2));
        assertCode(401, () -> friends.friends(0, 50));
    }

    @Test
    void blockedOrOneSidedRelationshipsNeverAuthorizeChat() {
        FriendRequestVO request = apply(2);
        UserContext.setUser(2L);
        friends.accept(request.getId());
        jdbc.update("UPDATE user_friend SET is_blocked = 1 WHERE user_id = 1");
        assertFalse(verify(1));
        UserContext.setUser(1L);
        assertFalse(verify(2));
        friends.deleteFriend(2L);
        assertCode(403, () -> apply(2));
        jdbc.update("UPDATE user_friend SET is_blocked = 0, status = 1 WHERE user_id = 1");
        assertFalse(verify(2));
    }

    @Test
    void failedSecondWriteRollsBackBothRelationshipAndDecision() {
        FriendRequestVO request = apply(2);
        UserContext.setUser(2L);
        jdbc.execute("ALTER TABLE user_friend ADD CONSTRAINT fail_second CHECK (user_id <> 2)");
        try {
            assertThrows(RuntimeException.class, () -> friends.accept(request.getId()));
            assertEquals(0, count("user_friend"));
            assertEquals("PENDING", friends.requests("incoming", null, 50).get(0).getStatus());
            verifyNoInteractions(redis);
        } finally {
            jdbc.execute("ALTER TABLE user_friend DROP CONSTRAINT fail_second");
        }
    }

    @Test
    void redisFailureCannotUndoCommittedFriendshipOrPermitDeletedChat() {
        when(redis.delete(anyCollection())).thenThrow(new IllegalStateException("simulated Redis outage"));
        FriendRequestVO request = apply(2);
        UserContext.setUser(2L);
        assertEquals("ACCEPTED", friends.accept(request.getId()).getStatus());
        assertTrue(verify(1));
        friends.deleteFriend(1L);
        assertFalse(verify(1));
    }

    @Test
    void cacheInvalidationRunsOnlyAfterCommit() {
        FriendRequestVO request = apply(2);
        UserContext.setUser(2L);
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.executeWithoutResult(status -> {
            friends.accept(request.getId());
            verifyNoInteractions(redis);
            status.setRollbackOnly();
        });
        assertEquals(0, count("user_friend"));
        assertEquals("PENDING", friends.requests("incoming", null, 50).get(0).getStatus());
        verifyNoInteractions(redis);
    }

    @Test
    void simultaneousDuplicateApplicationsShareOnePendingRequest() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                results.add(pool.submit(() -> {
                    UserContext.setUser(1L);
                    try { start.await(); return apply(2).getId(); }
                    finally { UserContext.removeUser(); }
                }));
            }
            start.countDown();
            Long id = results.get(0).get(15, TimeUnit.SECONDS);
            for (Future<Long> result : results) assertEquals(id, result.get(15, TimeUnit.SECONDS));
            assertEquals(1, count("friend_request"));
            assertEquals(0, count("user_friend"));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void simultaneousAcceptAndDeleteLeaveNoUsableRelationship() throws Exception {
        FriendRequestVO request = apply(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> accept = pool.submit(() -> {
                UserContext.setUser(2L);
                try { start.await(); friends.accept(request.getId()); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                finally { UserContext.removeUser(); }
            });
            Future<?> delete = pool.submit(() -> {
                UserContext.setUser(1L);
                try { start.await(); friends.deleteFriend(2L); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                finally { UserContext.removeUser(); }
            });
            start.countDown();
            accept.get(15, TimeUnit.SECONDS);
            delete.get(15, TimeUnit.SECONDS);
            assertFalse(verify(2));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_friend WHERE status = 1", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void httpLifecycleUsesBoundIdentityAndSharedErrorHandler() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FriendController(friends))
                .defaultRequest(get("/").accept("application/json"))
                .setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(new UserInfoInterceptor()).build();
        mvc.perform(post("/friend/apply").header("user-info", "1").contentType("application/json")
                        .content("{\"account\":\"account2\",\"message\":\"hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        Long id = jdbc.queryForObject("SELECT id FROM friend_request", Long.class);
        mvc.perform(post("/friend/requests/" + id + "/accept").header("user-info", "3"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        mvc.perform(post("/friend/requests/" + id + "/accept").header("user-info", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(post("/friend/verify").header("user-info", "1").contentType("application/json").content("{\"toId\":2}"))
                .andExpect(content().string("true"));
        mvc.perform(delete("/friend/2").header("user-info", "1")).andExpect(status().isNoContent());
        mvc.perform(post("/friend/verify").header("user-info", "2").contentType("application/json").content("{\"toId\":1}"))
                .andExpect(content().string("false"));
        mvc.perform(get("/friend/list")).andExpect(status().isUnauthorized());
        mvc.perform(post("/friend/apply").header("user-info", "1").contentType("application/json")
                        .content("{\"account\":\"account2\",\"message\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        assertNull(UserContext.getUser());
    }

    private FriendRequestVO apply(long id) {
        ApplyFormDTO dto = new ApplyFormDTO();
        dto.setFriendId(id);
        return friends.apply(dto);
    }

    private boolean verify(long id) {
        VerifyMsgDTO dto = new VerifyMsgDTO();
        dto.setToId(id);
        return friends.verify(dto);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void assertCode(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(CommonException.class, action).getCode());
    }
}
