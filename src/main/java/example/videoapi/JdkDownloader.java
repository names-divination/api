package example.videoapi;

import okhttp3.CompressionInterceptor;
import okhttp3.Gzip;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.brotli.Brotli;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class JdkDownloader extends Downloader {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) "
            + "Gecko/20100101 Firefox/153.0";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(new CompressionInterceptor(
                    Brotli.INSTANCE,
                    Gzip.INSTANCE
            ))
            .build();

    @Override
    public Response execute(Request request)
            throws IOException, ReCaptchaException {

        byte[] data = request.dataToSend();

        RequestBody body = data == null
                ? null
                : RequestBody.create(data);

        okhttp3.Request.Builder builder =
                new okhttp3.Request.Builder()
                        .url(request.url())
                        .method(request.httpMethod(), body)
                        .header("User-Agent", USER_AGENT);

        Map<String, List<String>> headers = request.headers();

        if (headers != null) {
            headers.forEach((name, values) -> {
                builder.removeHeader(name);

                if (values != null) {
                    for (String value : values) {
                        builder.addHeader(name, value);
                    }
                }
            });
        }

        try (okhttp3.Response response =
                     client.newCall(builder.build()).execute()) {

            if (response.code() == 429) {
                throw new ReCaptchaException(
                        "reCaptcha Challenge requested",
                        request.url()
                );
            }

            ResponseBody responseBody = response.body();

            String responseText = responseBody == null
                    ? ""
                    : responseBody.string();

            if (request.url().contains("youtubei.googleapis.com")) {

                String contentType = response.header(
                        "Content-Type",
                        "(なし)"
                );

                String preview = responseText.substring(
                        0,
                        Math.min(responseText.length(), 3000)
                ).replace("\n", " ").replace("\r", " ");

                System.err.println("========== YouTube通信調査 ==========");
                System.err.println("応答ステータス: " + response.code());
                System.err.println("応答形式: " + contentType);
                System.err.println("応答内容:");
                System.err.println(preview);
                System.err.println("=====================================");
            }

            return new Response(
                    response.code(),
                    response.message(),
                    response.headers().toMultimap(),
                    responseText,
                    response.request().url().toString()
            );
        }
    }
}
