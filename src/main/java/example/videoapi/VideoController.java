
package example.videoapi;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class VideoController {

    private static final String GAS_URL =
            System.getenv("GAS_WEB_APP_URL");

    private static final String GAS_TOKEN =
            System.getenv("GAS_API_TOKEN");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(
                Map.of("success", true, "message", "API is running")
        );
    }

    @GetMapping("/api/video/{id}")
    public ResponseEntity<?> getVideo(@PathVariable String id) {

        if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    "動画IDの形式が正しくありません"
            );
        }

        try {
            String youtubeUrl =
                    "https://www.youtube.com/watch?v=" + id;

            StreamInfo info = StreamInfo.getInfo(youtubeUrl);

            List<VideoStream> videoOnlyStreams =
                    info.getVideoOnlyStreams();

            List<AudioStream> audioStreams =
                    info.getAudioStreams();

            // 720pの映像を優先
            VideoStream selectedVideo = null;

            for (VideoStream s : videoOnlyStreams) {
                if (s.isUrl()
                        && s.getHeight() == 720
                        && s.getContent() != null
                        && s.getContent().startsWith("https://")) {

                    selectedVideo = s;
                    break;
                }
            }

            // 720pがなければ利用可能な最高画質
            if (selectedVideo == null) {
                selectedVideo = videoOnlyStreams.stream()
                        .filter(VideoStream::isUrl)
                        .filter(s -> s.getContent() != null
                                && s.getContent().startsWith("https://"))
                        .max(Comparator.comparingInt(VideoStream::getHeight))
                        .orElse(null);
            }

            // 音声ストリームを選択
            AudioStream selectedAudio = audioStreams.stream()
                    .filter(AudioStream::isUrl)
                    .filter(s -> s.getContent() != null
                            && s.getContent().startsWith("https://"))
                    .findFirst()
                    .orElse(null);

            if (selectedVideo == null) {
                return error(
                        HttpStatus.NOT_FOUND,
                        "適切な映像ストリームが見つかりませんでした"
                );
            }

            String videoUrl = selectedVideo.getContent();

            String audioUrl = selectedAudio == null
                    ? ""
                    : selectedAudio.getContent();

            String videoQuality = selectedVideo.getResolution();

            String audioFormat = selectedAudio == null
                    ? ""
                    : String.valueOf(selectedAudio.getFormat());

            // GASへ記録を送信
            boolean gasRecorded = sendToGas(
                    id,
                    info.getName(),
                    videoUrl,
                    audioUrl,
                    videoQuality,
                    audioFormat
            );

            // APIのレスポンスを作成
            Map<String, Object> result = new LinkedHashMap<>();

            result.put("success", true);
            result.put("id", id);
            result.put("title", info.getName());
            result.put("uploader", info.getUploaderName());
            result.put("gasRecorded", gasRecorded);

            Map<String, Object> videoInfo = new LinkedHashMap<>();

            videoInfo.put("quality", videoQuality);
            videoInfo.put("height", selectedVideo.getHeight());
            videoInfo.put("url", videoUrl);

            result.put("video", videoInfo);

            if (selectedAudio != null) {
                Map<String, Object> audioInfo = new LinkedHashMap<>();

                audioInfo.put("itag", selectedAudio.getItag());
                audioInfo.put("format", audioFormat);
                audioInfo.put("url", audioUrl);

                result.put("audio", audioInfo);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println(
                    "Extraction failed: "
                    + e.getClass().getSimpleName()
                    + ": "
                    + e.getMessage()
            );

            return error(
                    HttpStatus.BAD_GATEWAY,
                    "動画情報を取得できませんでした"
            );
        }
    }

    /**
     * GASに動画情報を送信する
     */
    private boolean sendToGas(
            String videoId,
            String title,
            String videoUrl,
            String audioUrl,
            String videoQuality,
            String audioFormat
    ) {

        if (GAS_URL == null || GAS_URL.isBlank()
                || GAS_TOKEN == null || GAS_TOKEN.isBlank()) {

            System.err.println("GASの環境変数が設定されていません");
            return false;
        }

        try {
            Map<String, Object> data = new LinkedHashMap<>();

            data.put("token", GAS_TOKEN);
            data.put("videoId", videoId);
            data.put("title", title);
            data.put("videoUrl", videoUrl);
            data.put("audioUrl", audioUrl);
            data.put("videoQuality", videoQuality);
            data.put("audioFormat", audioFormat);
            data.put("expiresAt", "");

            String json = objectMapper.writeValueAsString(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GAS_URL))
                    .timeout(Duration.ofSeconds(40))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            System.out.println(
                    "GAS HTTP status: " + response.statusCode()
            );

            String body = response.body();

            if (body != null) {
                System.out.println(
                        "GAS response: "
                        + body.substring(0, Math.min(body.length(), 500))
                );
            }

            if (response.statusCode() >= 200
                    && response.statusCode() < 300) {

                System.out.println("GASへの送信が完了しました");
                return true;
            }

            System.err.println("GASへの送信に失敗しました");
            return false;

        } catch (Exception e) {
            System.err.println(
                    "GAS送信エラー: " + e.getMessage()
            );
            return false;
        }
    }

    private ResponseEntity<?> error(
            HttpStatus status,
            String message
    ) {
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "success", false,
                        "error", message
                ));
    }
}
