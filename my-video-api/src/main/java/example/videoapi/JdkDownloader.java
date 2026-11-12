package example.videoapi;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JdkDownloader extends Downloader {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    @Override
    public Response execute(Request request) throws IOException {
        try {
            java.net.http.HttpRequest.Builder builder =
                    java.net.http.HttpRequest.newBuilder()
                            .uri(URI.create(request.url()))
                            .timeout(Duration.ofSeconds(30));

            Map<String, List<String>> headers = request.headers();
            if (headers != null) {
                headers.forEach((name, values) -> {
                    if (values != null) {
                        for (String value : values) builder.header(name, value);
                    }
                });
            }

            if (headers == null || headers.keySet().stream()
                    .noneMatch(name -> name.equalsIgnoreCase("User-Agent"))) {
                builder.header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
            }

            byte[] data = request.dataToSend();
            BodyPublisher body = data == null
                    ? BodyPublishers.noBody()
                    : BodyPublishers.ofByteArray(data);
            builder.method(request.httpMethod().toUpperCase(Locale.ROOT), body);

            HttpResponse<String> response = client.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());

            return new Response(response.statusCode(), "",
                    response.headers().map(), response.body(),
                    response.uri().toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid HTTP request", e);
        }
    }
}
