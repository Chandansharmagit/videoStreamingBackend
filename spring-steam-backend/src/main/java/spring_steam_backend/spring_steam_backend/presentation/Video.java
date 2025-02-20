package spring_steam_backend.spring_steam_backend.presentation;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "yt_videos") // MongoDB collection name
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video {
    @Id
    private String videoId;

    private String title;
    private String description;
    private String contentType; // e.g., "video/mp4", "image/jpeg"
    private String filepath; // Path where video file is stored
    private String tags; // Comma-separated tags
    private String ageRestriction; // e.g., "General", "13+", "18+"
    private String privacy; // "Public", "Private", "Unlisted"
    private String category; // e.g., "Entertainment", "Education"
    private String thumbnail; // Path to the thumbnail image
    private Map<String, String> transcodedVersions; // Quality/format -> file path
    private long views = 0L; // Video view count
    private String userId;
    private String username;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdDate;
}
