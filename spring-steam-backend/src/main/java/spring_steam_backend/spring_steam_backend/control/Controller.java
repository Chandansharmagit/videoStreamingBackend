package spring_steam_backend.spring_steam_backend.control;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.springframework.util.StringUtils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.elasticsearch.ResourceNotFoundException;
import org.hibernate.Cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.CacheProperties.Caffeine;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import spring_steam_backend.spring_steam_backend.Appcontant;
import spring_steam_backend.spring_steam_backend.control.payload.CustomMessage;
import spring_steam_backend.spring_steam_backend.presentation.Video;
import spring_steam_backend.spring_steam_backend.presentation.Notifications.Notification;
import spring_steam_backend.spring_steam_backend.repo.videoRepo;
import spring_steam_backend.spring_steam_backend.service.Video_service;
import spring_steam_backend.spring_steam_backend.service.Implementations.VideoImpl;
import spring_steam_backend.spring_steam_backend.service.NotificationsService.WebSocketNotificationService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.nio.charset.StandardCharsets;

@RestController
@CrossOrigin(origins = "http://localhost:3000") // Adjust the origin to match your frontend URL
public class Controller {

    @Autowired
    private videoRepo videoRepo;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private Video_service video_service;

    @Autowired
    private WebSocketNotificationService websocketNotificationService;

    @Value("${file.video.hsl}")
    private String HSL_DIR;

    @Value("${file.video.transcoded}")
    private String TRANSCODED_DIR;

    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("tags") String tags,
            @RequestParam("ageRestriction") String ageRestriction,
            @RequestParam("privacy") String privacy,
            @RequestParam("category") String category,
            @RequestParam("userId") String user_id,
            @RequestParam("username") String username,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail) {

        try {
            if (thumbnail != null && !thumbnail.isEmpty()) {
                System.out.println("Thumbnail file received: " + thumbnail.getOriginalFilename());
            } else {
                System.out.println("No thumbnail received.");
            }

            // Create and populate the video object
            Video newVideo = new Video();
            newVideo.setTitle(title);
            newVideo.setDescription(description);
            newVideo.setVideoId(UUID.randomUUID().toString());
            newVideo.setTags(tags);
            newVideo.setAgeRestriction(ageRestriction);
            newVideo.setPrivacy(privacy);
            newVideo.setCategory(category);
            newVideo.setUserId(user_id);
            newVideo.setUsername(username);

            // Set the thumbnail only if it's provided
            if (thumbnail != null && !thumbnail.isEmpty()) {
                String thumbnailPath = saveThumbnail(thumbnail);
                newVideo.setThumbnail(thumbnailPath);
            }

            System.out.println("received file: " + file.getOriginalFilename());

            // Save the video and file
            video_service.save(newVideo, file);

            return ResponseEntity.status(HttpStatus.CREATED).body("Video uploaded successfully!");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error uploading video: " + e.getMessage());
        }
    }

    private String saveThumbnail(MultipartFile thumbnail) {
        try {
            // Get the original filename and clean it
            String filename = StringUtils.cleanPath(thumbnail.getOriginalFilename());

            // Define the full path where the thumbnail will be saved
            Path thumbnailPath = Paths.get(HSL_DIR, filename);

            // Save the file at the specified path
            Files.copy(thumbnail.getInputStream(), thumbnailPath, StandardCopyOption.REPLACE_EXISTING);

            // Return the relative file path (not full system path)
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save thumbnail: " + e.getMessage());
        }
    }

    @GetMapping("/thumbnail/{filename}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable String filename) {
        try {
            // Construct the full path using HSL_DIR
            Path filePath = Paths.get(HSL_DIR, filename);
            Resource resource = new FileSystemResource(filePath);

            if (!resource.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Dynamically determine content type
            String contentType = Files.probeContentType(filePath);
            return ResponseEntity.ok()
                    .contentType(
                            MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"))
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // making the method to conunt the viws

    @Autowired
    private VideoImpl videoImpl;

    @PutMapping("/videos/increment-views/{videoId}")
    public ResponseEntity<String> incrementViews(@PathVariable String videoId) {
        Optional<Video> videoOptional = videoRepo.findByVideoId(videoId);
        if (videoOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Video not found");
        }

        Video video = videoOptional.get();

        // Increment views in Redis
        redisTemplate.opsForValue().increment("video:views:" + videoId, 1);

        // Increment views in the database immediately (optional but can ensure
        // consistency)
        video.setViews(video.getViews() + 1);
        videoRepo.save(video);

        return ResponseEntity.ok("View count updated successfully");
    }

    @Scheduled(fixedRate = 60000) // Sync every minute
    public void syncViewsToDatabase() {
        Set<String> keys = redisTemplate.keys("video:views:*");

        // Loop over all the keys representing video views in Redis
        for (String key : keys) {
            String videoId = key.split(":")[2]; // Extract videoId from Redis key
            Object redisViews = redisTemplate.opsForValue().get(key); // Get the current view count from Redis

            // Check if Redis has a view count
            if (redisViews != null) {
                Optional<Video> videoOptional = videoRepo.findByVideoId(videoId);
                if (videoOptional.isPresent()) {
                    Video video = videoOptional.get();
                    // Update the database with the value from Redis
                    video.setViews(video.getViews() + ((Number) redisViews).intValue()); // Assuming we are aggregating
                                                                                         // views
                    videoRepo.save(video);

                    // Optionally delete the Redis key after syncing
                    redisTemplate.delete(key); // You can choose to delete or keep for the next sync
                }
            }
        }
    }

    // getting the views of the videos accordin to their video id that selected by
    // user

    @GetMapping("/videos/views/{videoId}")
    public ResponseEntity<?> getVideoViews(@PathVariable String videoId) {
        // First check Redis for real-time view count
        Object redisViews = redisTemplate.opsForValue().get("video:views:" + videoId);

        // Get the video from database
        Optional<Video> videoOptional = videoRepo.findByVideoId(videoId);
        if (videoOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Video not found");
        }

        Video video = videoOptional.get();
        Long totalViews = video.getViews();

        // Add Redis views if they exist
        if (redisViews != null) {
            totalViews += ((Number) redisViews).intValue();
        }

        return ResponseEntity.ok().body(Map.of("views", totalViews));
    }

    // Helper method to get MIME type from file extension if `probeContentType`
    // fails

    // Helper method for file type validation

    // making the video for streaming of videos into the frontent

    @GetMapping("/stream/{videoId}")
    public ResponseEntity<Video> getVideoById(@PathVariable String videoId) {
        try {
            // You can verify token here if needed, and then fetch the video
            Video video = videoImpl.getVideoById(videoId);
            if (video == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(video);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }


    // Endpoint to fetch videos by userId
    @GetMapping("/videos/user/{userId}")
    public ResponseEntity<List<Video>> getVideosByUserId(@PathVariable String userId) {
        try {
            // You can verify token here if needed, and then fetch the videos for the user
            List<Video> videos = videoImpl.getVideosByUserId(userId);
            if (videos.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(videos);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    // getting all videos

    @GetMapping("/getall/videos")
    public List<Video> getall() {
        return video_service.getall();
    }

    // GETTING the videos chunk datas

    // A simple cache (you can use a more sophisticated approach if needed)
    // Cache for video metadata using Caffeine (in-memory cache)

    // A simple cache (you can use a more sophisticated approach if needed)

    private Map<String, Video> videoMetadataCache = new HashMap<>();

    @GetMapping("/stream/range/{videoId}")
    public ResponseEntity<Resource> streamVideoRange(
            @PathVariable String videoId,
            @RequestHeader(value = "Range", required = false) String range) {

        Video video = video_service.get(videoId);
        if (video == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ByteArrayResource("Video not found".getBytes()));
        }

        Path path = Paths.get(video.getFilepath());
        if (!Files.exists(path)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ByteArrayResource("Video file not found".getBytes()));
        }

        String contentType = video.getContentType();
        if (contentType == null) {
            contentType = "video/mp4";
        }

        long fileLength;
        try {
            fileLength = Files.size(path);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ByteArrayResource("Could not determine file size".getBytes()));
        }

        // Parse range header
        long rangeStart = 0;
        long rangeEnd = fileLength - 1;
        boolean isPartialRequest = false;

        if (range != null && range.startsWith("bytes=")) {
            isPartialRequest = true;
            try {
                String[] ranges = range.substring(6).trim().split("-");
                if (ranges.length == 2) {
                    rangeStart = ranges[0].isEmpty() ? 0 : Long.parseLong(ranges[0]);
                    rangeEnd = ranges[1].isEmpty() ? fileLength - 1 : Long.parseLong(ranges[1]);
                } else if (ranges.length == 1) {
                    rangeStart = Long.parseLong(ranges[0]);
                }
            } catch (NumberFormatException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ByteArrayResource("Invalid range header".getBytes()));
            }
        }

        // Validate ranges
        if (rangeStart >= fileLength || rangeEnd >= fileLength || rangeStart > rangeEnd) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileLength)
                    .body(new ByteArrayResource("Invalid range".getBytes()));
        }

        // Calculate content length
        long contentLength = rangeEnd - rangeStart + 1;

        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Range", String.format("bytes %d-%d/%d", rangeStart, rangeEnd, fileLength));
        headers.add("Accept-Ranges", "bytes");
        headers.setContentLength(contentLength);
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");
        headers.setExpires(0L);
        headers.add("X-Content-Type-Options", "nosniff");

        // Create and return response
        try {
            InputStream inputStream = Files.newInputStream(path);
            if (rangeStart > 0) {
                inputStream.skip(rangeStart);
            }

            return ResponseEntity.status(isPartialRequest ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new InputStreamResource(inputStream) {
                        @Override
                        public void finalize() throws Throwable {
                            try {
                                inputStream.close();
                            } finally {
                                super.finalize();
                            }
                        }
                    });
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ByteArrayResource("Error reading video file".getBytes()));
        }
    }

    @MessageMapping("/send")
    @SendTo("/topic/notifications")
    public Notification sendNotification(Notification notification) {
        return notification; // Broadcast to all subscribers
    }

    // getting videos by titile

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/videos/search/title")
    public List<Video> searchVideosByTitle(@RequestParam String title) {
        return video_service.searchByTitle(title);
    }

    // @Autowired
    // private VideoImpl videoImpl;

    @DeleteMapping("/videos/{videoId}")
    public ResponseEntity<String> deleteVideo(@PathVariable String videoId) {
        try {
            video_service.deleteVideo(videoId); // Call the service method to delete the video
            return ResponseEntity.ok("Video deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage()); // Return error message if video is not found
        }
    }

    @PostMapping("/transcode/{videoId}")
    public ResponseEntity<?> transcodeVideo(
            @PathVariable String videoId,
            @RequestParam(required = false, defaultValue = "1080") String resolution,
            @RequestParam(required = false, defaultValue = "2000k") String bitrateParam) {

        // Remove 'p' suffix if present in resolution
        resolution = resolution.replace("p", "");

        // Validate resolution first
        if (!isValidResolution(resolution)) {
            return ResponseEntity.badRequest()
                    .body("Invalid resolution. Supported resolutions: 360p, 480p, 720p, 1080p, 1440p, 2160p");
        }

        // Validate bitrate format if custom bitrate provided
        if (!bitrateParam.equals("2000k") && !isValidBitrate(bitrateParam)) {
            return ResponseEntity.badRequest()
                    .body("Invalid bitrate format. Use format like '2000k'");
        }

        // Get recommended bitrate if default is specified
        final String bitrate = bitrateParam.equals("2000k") ? getRecommendedBitrate(resolution) : bitrateParam;

        try {
            Video video = videoRepo.findByVideoId(videoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Video not found"));

            Path inputPath = Paths.get(video.getFilepath());
            if (!Files.exists(inputPath)) {
                throw new ResourceNotFoundException("Video file not found on disk");
            }

            String outputFileName = String.format("%s_%sp.mp4", videoId, resolution);
            Path outputPath = Paths.get(TRANSCODED_DIR, outputFileName);

            // Ensure output directory exists
            Files.createDirectories(Paths.get(TRANSCODED_DIR));

            // Run FFmpeg in async mode with proper error handling
            final String finalResolution = resolution;
            CompletableFuture.runAsync(() -> {
                try {
                    transcodeWithFFmpeg(inputPath.toString(), outputPath.toString(), finalResolution, bitrate);
                    System.out.println("Transcoding completed for " + outputFileName);
                } catch (Exception e) {
                    System.err.println("Transcoding failed for " + videoId + ": " + e.getMessage());
                }
            });

            return ResponseEntity.accepted()
                    .body(Map.of(
                            "message", "Transcoding started",
                            "resolution", resolution + "p",
                            "bitrate", bitrate,
                            "outputFile", outputFileName));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("Error in transcoding request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error during transcoding setup"));
        }
    }

    private void transcodeWithFFmpeg(String inputPath, String outputPath, String resolution, String bitrate) {
        try {
            List<String> command = new ArrayList<>(Arrays.asList(
                    "ffmpeg", "-i", inputPath,
                    "-c:v", "libx264",
                    "-b:v", bitrate,
                    "-maxrate", calculateMaxRate(bitrate),
                    "-bufsize", calculateBufSize(bitrate),
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-vf", String.format("scale=-2:%s", resolution),
                    "-preset", "medium",
                    "-profile:v", "high",
                    "-level:v", "4.1",
                    "-movflags", "+faststart",
                    "-y", outputPath));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Handle process output in a separate thread
            CompletableFuture.runAsync(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("FFmpeg: " + line);
                    }
                } catch (IOException e) {
                    System.err.println("Error reading FFmpeg output: " + e.getMessage());
                }
            });

            // Add timeout to prevent hanging
            if (!process.waitFor(30, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new TimeoutException("FFmpeg process timed out after 30 minutes");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("FFmpeg process failed with exit code: " + process.exitValue());
            }

            System.out.println("Transcoding completed successfully: " + outputPath);

        } catch (Exception e) {
            System.err.println("Transcoding failed: " + e.getMessage());
            throw new RuntimeException("Transcoding failed: " + e.getMessage(), e);
        }
    }

    private String calculateMaxRate(String bitrate) {
        long bitrateValue = Long.parseLong(bitrate.replace("k", ""));
        return (bitrateValue * 3 / 2) + "k";
    }

    private String calculateBufSize(String bitrate) {
        long bitrateValue = Long.parseLong(bitrate.replace("k", ""));
        return (bitrateValue * 2) + "k";
    }

    private boolean isValidResolution(String resolution) {
        Set<String> allowedResolutions = Set.of("360", "480", "720", "1080", "1440", "2160");
        return allowedResolutions.contains(resolution);
    }

    private String getRecommendedBitrate(String resolution) {
        return switch (resolution) {
            case "360" -> "800k"; // 360p
            case "480" -> "1500k"; // 480p
            case "720" -> "2500k"; // 720p
            case "1080" -> "4000k"; // 1080p
            case "1440" -> "8000k"; // 1440p (2K)
            case "2160" -> "16000k"; // 2160p (4K)
            default -> "2000k"; // Default fallback
        };
    }

    private boolean isValidBitrate(String bitrate) {
        return bitrate.matches("\\d+k"); // Ensures format like "1500k"
    }

}
