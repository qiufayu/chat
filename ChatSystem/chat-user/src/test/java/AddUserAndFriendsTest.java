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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@SpringBootTest(classes = ChatUserApplication.class)
@ActiveProfiles("test")
public class AddUserAndFriendsTest {

    @Autowired
    private IUserServer userServer;
    @Autowired
    private IUserFriendServer userFriendServer;

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

}
