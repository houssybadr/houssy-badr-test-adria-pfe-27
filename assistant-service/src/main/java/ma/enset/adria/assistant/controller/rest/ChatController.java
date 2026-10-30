package ma.enset.adria.assistant.controller.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.enset.adria.assistant.service.AssistantService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final AssistantService service;
    public ChatController(AssistantService service) { this.service = service; }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        try {
            String answer = service.ask(request.message());
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("Empty model response");
            }
            return new ChatResponse(answer);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Appel Gemini ou MCP impossible. Verifiez la connexion, la cle et le quota.");
        }
    }

    @GetMapping("/tools")
    public List<String> tools() { return service.tools(); }

    public record ChatRequest(@NotBlank @Size(max = 4000) String message) {}
    public record ChatResponse(String answer) {}
}
