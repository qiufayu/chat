package com.fayu.user.server.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fayu.common.exception.UnauthorizedException;
import com.fayu.common.utils.UserContext;
import com.fayu.user.config.JwtProperties;
import com.fayu.user.domain.dto.LoginFormDTO;
import com.fayu.user.domain.dto.RegisterFormDTO;
import com.fayu.user.domain.po.User;
import com.fayu.user.domain.vo.TokenResponseVO;
import com.fayu.user.domain.vo.UserInfoVo;
import com.fayu.user.domain.vo.UserLoginVO;
import com.fayu.user.domain.vo.UserRegisterVO;
import com.fayu.user.enums.UserStatus;
import com.fayu.common.exception.BadRequestException;
import com.fayu.common.exception.ForbiddenException;
import com.fayu.user.mepper.UserMapper;
import com.fayu.user.server.IUserServer;
import com.fayu.common.utils.RedisIdWorker;
import com.fayu.user.utils.JwtTool;
import com.github.xiaoymin.knife4j.core.util.Assert;
import com.github.xiaoymin.knife4j.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.fayu.common.constant.RedisConstant.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServerImpl extends ServiceImpl<UserMapper, User> implements IUserServer {

    private final PasswordEncoder passwordEncoder;
    private final JwtTool jwtTool;
    private final JwtProperties jwtProperties;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate redisTemplate;
    @Override
    public UserLoginVO login(LoginFormDTO loginDTO) {
        String account = loginDTO.getAccount();
        String password = loginDTO.getPassword();
        if (account == null ||password == null){
            throw new BadRequestException("参数错误");
        }
        User user = lambdaQuery().eq(User::getAccount, account).one();
        Assert.notNull(user,"用户名错误");
        if (user.getStatus() != UserStatus.FROZEN.getValue()){
            throw new ForbiddenException("用户被冻结");
        }
        // 4.校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("用户名或密码错误");
        }
        String accessToken = jwtTool.createToken(user.getId(),jwtProperties.getTokenTTL());
        String refreshToken = jwtTool.createToken(user.getId(),jwtProperties.getRefreshTTL());

        redisTemplate.opsForValue().set(LOGIN_REFRESH_TOKEN + user.getId(), refreshToken , jwtProperties.getRefreshTTL());

        UserLoginVO userLoginVO = new UserLoginVO();
        userLoginVO.setAccessToken(accessToken);
        userLoginVO.setRefreshToken(refreshToken);
        return userLoginVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserRegisterVO register(RegisterFormDTO registerDTO) {
        String password = registerDTO.getPassword();
        String nickname = registerDTO.getNickname();
        String phone = registerDTO.getPhone();

        if (StrUtil.isBlank(password) || StrUtil.isBlank(nickname) || StrUtil.isBlank(phone)){
            throw new BadRequestException("参数错误");
        }
        User user = lambdaQuery().eq(User::getPhone, phone).one();
        if (!ObjectUtil.isEmpty(user)){
            throw new ForbiddenException("注册失败");
        }
        String account = redisIdWorker.generateId(USER_REG_ID);

        User regUser = new User();
        regUser.setAccount(account);
        regUser.setPassword(passwordEncoder.encode(password));
        regUser.setNickname(nickname);
        regUser.setPhone(phone);
        regUser.setStatus(UserStatus.FROZEN.getValue());
        regUser.setCreatedAt(LocalDateTime.now());
        regUser.setUpdatedAt(LocalDateTime.now());
        regUser.setAvatar(defaultAvatar());

        boolean success  = save(regUser);
        if (!success){
            throw new BadRequestException("注册失败，请稍后重试");
        }

        String accessToken = jwtTool.createToken(regUser.getId(),jwtProperties.getTokenTTL());
        String refreshToken = jwtTool.createToken(regUser.getId(),jwtProperties.getRefreshTTL());

        redisTemplate.opsForValue().set(LOGIN_REFRESH_TOKEN + regUser.getId(), refreshToken , jwtProperties.getRefreshTTL());

        UserRegisterVO vo = new UserRegisterVO();
        vo.setAccount(account);
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        return vo;
    }

    @Override
    public UserInfoVo info() {
        Long userId = UserContext.getUser();
        User userInfo = lambdaQuery().eq(User::getId, userId).one();
        return BeanUtil.copyProperties(userInfo,UserInfoVo.class);
    }

    @Override
    public TokenResponseVO refreshToken(String refreshToken) {
        Long userId = jwtTool.parseToken(refreshToken);

        String token = redisTemplate.opsForValue().get(LOGIN_REFRESH_TOKEN + userId);
        if (token == null || !token.equals(refreshToken)){
            throw new UnauthorizedException("登录已过期，请重新登录");
        }

        String newAcToken = jwtTool.createToken(userId, jwtProperties.getTokenTTL());
        String newReToken = jwtTool.createToken(userId,jwtProperties.getRefreshTTL());

        redisTemplate.opsForValue().set(LOGIN_REFRESH_TOKEN + userId, refreshToken , jwtProperties.getRefreshTTL());
        log.info("用户 {} 刷新 Token 成功，已续期 7 天", userId);

        return new TokenResponseVO(newAcToken,newReToken);
    }



    private String defaultAvatar(){
        return "avatar";
    }
}
