package spring_steam_backend.spring_steam_backend.control.payload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.*;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomMessage {

    private String message;
    private boolean sucess = false;
}
