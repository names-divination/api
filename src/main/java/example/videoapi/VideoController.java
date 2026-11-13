
package example.videoapi;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class VideoController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("success", true, "message", "API is running");
    }

    @GetMapping("/api/video/{id}")
    public ResponseEntity<Map<String, Object>> getVideo(
            @PathVariable String id) {

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

            List<VideoStream> allStreams = info.getVideoStreams();

            System.err.println("========== ストリーム調査 ==========");
            System.err.println("動画タイトル: " + info.getName());
            System.err.println("動画ストリーム数: " + allStreams.size());

            for (VideoStream stream : allStreams) {
                System.err.println(
                        "画質: " + stream.getResolution()
                        + " / 高さ: " + stream.getHeight()
                        + " / URL形式: " + stream.isUrl()
                        + " / 映像のみ: " + stream.isVideoOnly()
                );
            }

            System.err.println("====================================");

            List<VideoStream> candidates = allStreams.stream()
                    .filter(VideoStream::isUrl)
                    .filter(stream -> !stream.isVideoOnly())
                    .filter(stream -> stream.getHeight() > 0
                            && stream.getHeight() <= 720)
                    .filter(stream -> stream.getContent()
                            .startsWith("https://"))
                    .sorted(
                            Comparator.comparingInt(
                                    VideoStream::getHeight
                            ).reversed()
                    )
                    .toList();

            if (candidates.isEmpty()) {
                return error(
                        HttpStatus.NOT_FOUND,
                        "720p以下の映像・音声一体型ストリームが見つかりませんでした"
                );
            }

            VideoStream selected = candidates.get(0);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("id", id);
            result.put("title", info.getName());
            result.put("uploader", info.getUploaderName());
            result.put("quality", selected.getResolution());
            result.put("height", selected.getHeight());
            result.put("url", selected.getContent());
            result.put("note", "再生URLは期限切れになる場合があります");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println(
                    "Extraction failed: "
                    + e.getClass().getSimpleName()
                    + ": " + e.getMessage()
            );

            return error(
                    HttpStatus.BAD_GATEWAY,
                    "動画情報を取得できませんでした"
            );
        }
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String message) {

        return ResponseEntity.status(status).body(
                Map.of(
                        "success", false,
                        "error", message
                )
        );
    }
}
