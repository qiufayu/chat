import com.fayu.connection.ChatConnectionApplication;
import com.fayu.connection.task.RetryScheduleTask;
import feign.Client;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URISyntaxException;

@SpringBootTest(classes = ChatConnectionApplication.class)
@ActiveProfiles("local") // 强行加载 local 配置（不带后缀名）
public class test {
    @Autowired
    private Client feignClient;
    @Autowired
    private RetryScheduleTask retryScheduleTask;
    @Test
    public void test() throws URISyntaxException {
        System.out.println("===== 当前 Feign 客户端是: " + feignClient.getClass().getName() + " =====");
    }

    @Test
    public void testOfScheduleTask() throws InterruptedException {
           retryScheduleTask.retryScheduleTask();
    }

}

