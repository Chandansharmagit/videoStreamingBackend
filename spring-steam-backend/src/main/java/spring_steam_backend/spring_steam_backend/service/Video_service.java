package spring_steam_backend.spring_steam_backend.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import spring_steam_backend.spring_steam_backend.presentation.Video;

@Service
public interface Video_service {
    // saving the videos

    Video save(Video video, MultipartFile file);

    // getting the videos by id

    Video get(String videoId);

    // getting videos by title search

    // getting all videos

    List<Video> getall();

    List<Video> searchByTitle(String title);

    void deleteVideo(String videoId); // Method to delete a video by its ID

    // making the video processing

    String videoProcessing(String videoId);


    //



}
