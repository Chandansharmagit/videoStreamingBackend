package spring_steam_backend.spring_steam_backend.service.NotificationsService;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import spring_steam_backend.spring_steam_backend.presentation.Notifications.Notification;

@Service
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendNotification(String title, String body) {
        Notification notification = new Notification();
        notification.setTitle(title); // Set the title passed to the method
        notification.setBody(body); // Set the body passed to the method

        // Send the notification via WebSocket
        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    public void sendNotification(Notification notification) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'sendNotification'");
    }
}
