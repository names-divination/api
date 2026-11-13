package example.videoapi;

import org.schabi.newpipe.extractor.stream.AudioStream;
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
    public ResponseEntity> health() {
        return ResponseEntity.ok(Map.of("success", true, "message", "API is running"));
    }

    @GetMapping("/api/video/{id}")
    public ResponseEntity> getVideo(@PathVariable String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) {
            return error(HttpStatus.BAD_REQUEST, "動画IDの形式が正しくありません");
        }

        try {
            String youtubeUrl = "https://www.youtube.com/watch?v=" + id;
            StreamInfo info = StreamInfo.getInfo(youtubeUrl);

            List<VideoStream> videoOnlyStreams = info.getVideoOnlyStreams();
            List<AudioStream> audioStreams = info.getAudioStreams();

            // 1. 720pの「映像のみ」ストリームを優先して探す
            VideoStream selectedVideo = null;
            for (VideoStream s : videoOnlyStreams) {
                if (s.isUrl() && s.getHeight() == 720 && s.getContent() != null && s.getContent().startsWith("https://")) {
                    selectedVideo = s;
                    break;
                }
            }

            // 2. なければ別の解像度の中で一番高画質なものを代替として選択
            if (selectedVideo == null && !videoOnlyStreams.isEmpty()) {
                selectedVideo = videoOnlyStreams.stream()
                        .filter(VideoStream::isUrl)
                        .filter(s -> s.getContent() != null && s.getContent().startsWith("https://"))
                        .max(Comparator.comparingInt(VideoStream::getHeight))
                        .orElse(null);
            }

            // 3. 音声ストリームを取得
            AudioStream selectedAudio = null;
            if (!audioStreams.isEmpty()) {
                selectedAudio = audioStreams.stream()
                        .filter(AudioStream::isUrl)
                        .filter(s -> s.getContent() != null && s.getContent().startsWith("https://"))
                        .findFirst()
                        .orElse(audioStreams.get(0));
            }

            if (selectedVideo == null) {
                return error(HttpStatus.NOT_FOUND, "適切な映像ストリームが見つかりませんでした");
            }

            // 4. URL長すぎ問題対策：videoとaudioの情報を分離してレスポンスを軽量化
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("id", id);
            result.put("title", info.getName());
            result.put("uploader", info.getUploaderName());
            
            // 720p（または代替）の映像のみ情報
            Map<String, Object> videoInfo = new LinkedHashMap<>();
            videoInfo.put("quality", selectedVideo.getResolution());
            videoInfo.put("height", selectedVideo.getHeight());
            videoInfo.put("url", selectedVideo.getContent());
            result.put("video", videoInfo);

            // 音声情報
            if (selectedAudio != null) {
                Map<String, Object> audioInfo = new LinkedHashMap<>();
                audioInfo.put("itag", selectedAudio.getItag());
                audioInfo.put("url", selectedAudio.getContent());
                result.put("audio", audioInfo);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("Extraction failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "動画情報を取得できませんでした");
        }
    }

    private ResponseEntity> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("success", false, "error", message));
    }
}
