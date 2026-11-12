package example.videoapi;

import org.schabi.newpipe.extractor.NewPipe;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiApplication {
    public static void main(String[] args) {
        NewPipe.init(new JdkDownloader());
        SpringApplication.run(ApiApplication.class, args);
    }
}
