package spring_steam_backend.spring_steam_backend.service.Implementations;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import ch.qos.logback.core.util.StringUtil;
import jakarta.annotation.PostConstruct;
import spring_steam_backend.spring_steam_backend.presentation.Video;
import spring_steam_backend.spring_steam_backend.repo.videoRepo;
import spring_steam_backend.spring_steam_backend.service.Video_service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class VideoImpl implements Video_service {

    @Autowired
    private videoRepo videoRepo;

    @Value("${files.video}")
    String dir;

    @Value("${file.video.hsl}")
    String HSL_DIR;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public Video getVideoById(String videoId) {
        return videoRepo.findById(videoId).orElse(null);  // Return video if found, else return null
    }




    @PostConstruct
    public void init() {
        try {
            // Get the directory paths for storing files
            Path videoDirectoryPath = Paths.get(dir);
            Path hslDirectoryPath = Paths.get(HSL_DIR);
    
            // Create directories if they don't exist
            if (!Files.exists(videoDirectoryPath)) {
                Files.createDirectories(videoDirectoryPath);
                System.out.println("Video directory initialized: " + videoDirectoryPath);
            }
    
            if (!Files.exists(hslDirectoryPath)) {
                Files.createDirectories(hslDirectoryPath);
                System.out.println("HSL directory initialized: " + hslDirectoryPath);
            }
        } catch (IOException e) {
            // Log an error message if directory creation fails
            System.err.println("Failed to initialize directories: " + dir + ", " + HSL_DIR);
            e.printStackTrace();
        }
    }

    @Override
    public Video save(Video video, MultipartFile file) {
        try {
            // Extract metadata
            String filename = StringUtils.cleanPath(file.getOriginalFilename());
            String contentType = file.getContentType();
            InputStream inputStream = file.getInputStream();

            Path path = Paths.get(StringUtils.cleanPath(dir), filename);
            Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);

            // Set metadata
            video.setContentType(contentType);
            video.setFilepath(path.toString());

            // Save video to DB
            Video savedVideo = videoRepo.save(video);

            // ✅ Immediately update cache for this video
            updateVideoCache(savedVideo);

            // ✅ Update all videos cache
            updateAllVideosCache();

            return savedVideo;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Video get(String videoId) {
        return videoRepo.findById(videoId)
                .orElseThrow(() -> new RuntimeException("❌ Video not found: " + videoId));
    }

    // making helper methods for cache

    private void updateVideoCache(Video video) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String cacheKey = "video:" + video.getVideoId();
        try {
            ops.set(cacheKey, new ObjectMapper().writeValueAsString(video));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void updateAllVideosCache() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String cacheKey = "videos:all";
        List<Video> videos = videoRepo.findAll();
        try {
            ops.set(cacheKey, new ObjectMapper().writeValueAsString(videos));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Video> getall() {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String cacheKey = "videos:all";

        // Fetch from DB first, then update cache
        List<Video> videos = videoRepo.findAll();

        try {
            // Update cache with latest data
            ops.set(cacheKey, new ObjectMapper().writeValueAsString(videos));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return videos;
    }

    @Override
    public String videoProcessing(String videoId) {
        try {
            // Get video details
            Video video = this.get(videoId);
            String filepath = video.getFilepath();
            Path videoPath = Paths.get(filepath).toAbsolutePath();

            // Log the full path
            System.out.println("Checking video file path: " + videoPath);

            // Ensure the input video file exists
            if (!Files.exists(videoPath)) {
                throw new RuntimeException("Input video file does not exist: " + videoPath.toString());
            }

            // Output directory path
            Path outputPath = Paths.get(HSL_DIR, videoId);
            Files.createDirectories(outputPath); // Ensure output directory exists

            // Build the FFmpeg command
            String ffmpegCommand = String.format(
                    "ffmpeg -i \"%s\" -c:v libx264 -c:a aac -strict -2 -f hls -hls_time 10 -hls_list_size 0 -hls_segment_filename \"%s\\segment_%%03d.ts\" \"%s\\master.m3u8\"",
                    videoPath.toString(), outputPath.toString(), outputPath.toString());

            // Log the FFmpeg command
            System.out.println("Executing FFmpeg Command: " + ffmpegCommand);

            // Run FFmpeg process
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", ffmpegCommand);
            processBuilder.redirectErrorStream(true); // Merge stderr and stdout
            Process process = processBuilder.start();

            // Capture and log output from FFmpeg process
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[FFmpeg Output] " + line);
                }
            }

            // Wait for the process to complete and check exit code
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Video processing failed with exit code: " + exitCode);
            }

            // Return success message
            System.out.println("Video processing completed successfully!");
            return videoId;

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException("Error occurred during video processing: " + e.getMessage());
        }
    }

    // Method to get videos by title and description
    @Override
    public List<Video> searchByTitle(String searchTerm) {
        // If search term is empty, return all videos
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return videoRepo.findAll();
        }

        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String cacheKey = "search:" + searchTerm.toLowerCase().trim();

        // Try to get search results from cache
        String cachedResults = ops.get(cacheKey);
        if (cachedResults != null) {
            try {
                return new ObjectMapper().readValue(cachedResults,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Video>>() {
                        });
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        // If not in cache, perform the search
        List<Video> allVideos = videoRepo.findAll();
        Map<Video, Double> scoreMap = new HashMap<>();

        String searchTermLower = searchTerm.toLowerCase().trim();
        String[] searchWords = searchTermLower.split("\\s+");

        for (Video video : allVideos) {
            double score = calculateRelevanceScore(video, searchWords);
            // Store all videos with their scores, even if score is 0
            scoreMap.put(video, score);
        }

        // Sort by score (highest first) and convert to list
        List<Video> results = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Video, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        // Store results in cache
        try {
            ops.set(cacheKey, new ObjectMapper().writeValueAsString(results));
            redisTemplate.expire(cacheKey, 1, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return results;
    }

    private double calculateRelevanceScore(Video video, String[] searchWords) {
        double score = 0.0;
        String titleLower = video.getTitle().toLowerCase();
        String descriptionLower = video.getDescription() != null ? video.getDescription().toLowerCase() : "";

        for (String word : searchWords) {
            // Title matches (highest weight)
            if (titleLower.equals(word)) {
                score += 100; // Exact title match
            } else if (titleLower.contains(word)) {
                score += 50; // Partial title match
            }

            // More lenient character matching in title
            for (String titleWord : titleLower.split("\\s+")) {
                if (titleWord.startsWith(word) || word.startsWith(titleWord)) {
                    score += 40; // Prefix match
                }
                // Check if any characters match
                if (shareAnyCharacters(titleWord, word)) {
                    score += 10; // Some characters match
                }
            }

            // Description matches (lower weight)
            if (descriptionLower.contains(word)) {
                score += 20;
            }

            // Levenshtein distance for fuzzy matching in title
            int levenshteinDistance = calculateLevenshteinDistance(titleLower, word);
            if (levenshteinDistance <= 3) { // Increased tolerance to 3 characters
                score += (20.0 / (levenshteinDistance + 1));
            }
        }

        // Give a minimum score to ensure all videos are included
        return Math.max(score, 0.1);
    }

    // New helper method to check if strings share any characters
    private boolean shareAnyCharacters(String str1, String str2) {
        return str1.chars().anyMatch(ch -> str2.indexOf(ch) >= 0);
    }

    private int calculateLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                            Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    public void deleteVideo(String videoId) {
        videoRepo.deleteById(videoId); // Directly delete the video by its ID
    }

    private final Map<String, Map<String, Long>> userViews = new ConcurrentHashMap<>();
    // Add a new map to store view counts
    private final Map<String, Integer> viewCounts = new ConcurrentHashMap<>();

    public boolean hasUserViewed(String videoId, String userIp) {
        Map<String, Long> views = userViews.getOrDefault(videoId, new HashMap<>());
        Long lastViewTime = views.get(userIp);

        if (lastViewTime == null || (System.currentTimeMillis() - lastViewTime) >= 3600000) {
            // If user hasn't viewed or last view was more than 1 hour ago
            // Increment view count
            viewCounts.merge(videoId, 1, Integer::sum);
            return false;
        }
        return true;
    }

    // Add new method to get views for a video using Redis

    public void storeUserView(String videoId, String userIp) {
        userViews.computeIfAbsent(videoId, k -> new HashMap<>()).put(userIp, System.currentTimeMillis());
    }


    // Method to get videos by userId
    public List<Video> getVideosByUserId(String userId) {
        return videoRepo.findByUserId(userId);  // Return videos that belong to the user
    }

}
