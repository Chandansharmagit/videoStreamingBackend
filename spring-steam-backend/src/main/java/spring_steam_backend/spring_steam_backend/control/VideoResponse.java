package spring_steam_backend.spring_steam_backend.control;

import lombok.Builder;

public class VideoResponse {
    private String message;
    private String videoId;
    private String title;
    private String description;
    private String tags;  // Added tags
    private String ageRestriction; // Added ageRestriction
    private String privacy;  // Added privacy
    private String category; // Added category
    private String thumbnail; // Added thumbnail

    @Builder
    public VideoResponse(String message, String videoId, String title, String description,
                         String tags, String ageRestriction, String privacy, String category, String thumbnail) {
        this.message = message;
        this.videoId = videoId;
        this.title = title;
        this.description = description;
        this.tags = tags;
        this.ageRestriction = ageRestriction;
        this.privacy = privacy;
        this.category = category;
        this.thumbnail = thumbnail;
    }

    // Getters and Setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getAgeRestriction() {
        return ageRestriction;
    }

    public void setAgeRestriction(String ageRestriction) {
        this.ageRestriction = ageRestriction;
    }

    public String getPrivacy() {
        return privacy;
    }

    public void setPrivacy(String privacy) {
        this.privacy = privacy;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }
}

