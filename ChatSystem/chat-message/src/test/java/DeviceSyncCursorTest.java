import com.fayu.message.ChatMessageApplication;
import com.fayu.message.mapper.DeviceSyncCursorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@SpringBootTest(classes = ChatMessageApplication.class)
@ActiveProfiles("local")
public class DeviceSyncCursorTest {

    @Autowired
    private DeviceSyncCursorMapper deviceSyncCursorMapper;



}
