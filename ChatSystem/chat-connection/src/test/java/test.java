import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

public class test {
    @Test
    public void test() throws URISyntaxException {
        System.out.println(new URI("/ws?userId=1").getPath());
    }

}
