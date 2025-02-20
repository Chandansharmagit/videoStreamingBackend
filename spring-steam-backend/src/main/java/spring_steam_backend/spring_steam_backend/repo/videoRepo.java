package spring_steam_backend.spring_steam_backend.repo;

import java.util.List;
import java.util.Optional;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import spring_steam_backend.spring_steam_backend.presentation.Video;

@Repository
public interface videoRepo extends MongoRepository<Video, String> {

    List<Video> findByTitleContainingIgnoreCase(String title);

    void deleteById(String id);
    
    List<Video> findByUserId(String userId);

    Optional<Video> findByVideoId(String id); // MongoDB uses `id`, not `videoId`
}
