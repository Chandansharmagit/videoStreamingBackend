package spring_steam_backend.spring_steam_backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import spring_steam_backend.spring_steam_backend.service.Video_service;

@SpringBootTest
class SpringSteamBackendApplicationTests {

	@Autowired
	Video_service video_service;

	@Test
	void contextLoads() {
		video_service.videoProcessing("d2174518-d96e-4318-bb1e-343169a0613a");
	}

}
