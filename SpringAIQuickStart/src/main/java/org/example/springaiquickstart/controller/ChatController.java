package org.example.springaiquickstart.controller;

import org.example.springaiquickstart.model.Choice;
import org.example.springaiquickstart.model.DeepSeekResponse;
import org.example.springaiquickstart.model.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RestTemplate restTemplate = new RestTemplate();

    private final String deepseekBaseUrl;
    private final String deepseekApiKey;
    private final String deepseekModel;

    public ChatController(
            @Value("${spring.ai.deepseek.base-url}") String deepseekBaseUrl,
            @Value("${spring.ai.deepseek.api-key}") String deepseekApiKey,
            @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}") String deepseekModel
    ) {
        this.deepseekBaseUrl = deepseekBaseUrl;
        this.deepseekApiKey = deepseekApiKey;
        this.deepseekModel = deepseekModel;
    }

    @GetMapping({"/message", "/message/"})
    public String getMessage(@RequestParam(value = "message", defaultValue = "你是谁") String message) {
        try {
            String url = deepseekBaseUrl;
            if (!url.endsWith("/")) url += "/";
            url += "v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (deepseekApiKey != null && !deepseekApiKey.isBlank()) {
                headers.setBearerAuth(deepseekApiKey);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model", deepseekModel);
            List<Map<String, String>> messages = List.of(Map.of("role", "user", "content", message));
            body.put("messages", messages);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // First try to map the response to our DTO (if Jackson / message converters available)
            try {
                ResponseEntity<DeepSeekResponse> respDto = restTemplate.postForEntity(url, entity, DeepSeekResponse.class);
                DeepSeekResponse ds = respDto.getBody();
                if (ds != null && ds.getChoices() != null && !ds.getChoices().isEmpty()) {
                    Choice c = ds.getChoices().get(0);
                    if (c != null) {
                        Message msg = c.getMessage();
                        if (msg != null && msg.getContent() != null && !msg.getContent().isBlank()) {
                            return msg.getContent();
                        }
                        if (c.getText() != null && !c.getText().isBlank()) {
                            return c.getText();
                        }
                    }
                }
            } catch (Exception ignore) {
                // ignore, will try raw string below
            }

            // Fallback to string response and extract
            try {
                ResponseEntity<String> resp2 = restTemplate.postForEntity(url, entity, String.class);
                String rawBody = resp2.getBody();
                if (rawBody != null && !rawBody.isBlank()) {
                    try {
                        Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
                        Matcher m = p.matcher(rawBody);
                        if (m.find()) return unescapeSimpleJsonString(m.group(1));
                        Pattern p2 = Pattern.compile("\"text\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
                        Matcher m2 = p2.matcher(rawBody);
                        if (m2.find()) return unescapeSimpleJsonString(m2.group(1));
                    } catch (Exception e) {
                        // ignore
                    }
                    return rawBody;
                }
            } catch (Exception ex2) {
                // ignore and report original exception below
            }

            return "Empty response from DeepSeek";
        } catch (Exception ex) {
            return "调用模型出错: " + ex.getMessage();
        }
    }

    @GetMapping("/stream")
    public SseEmitter streamMessage(@RequestParam(value = "message", defaultValue = "你是谁") String message) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        // Run in background
        Thread t = new Thread(() -> {
            try {
                // Get the full reply (reuses getMessage logic)
                String reply = getMessage(message);
                if (reply == null) reply = "";
                // Stream reply in chunks
                final int chunkSize = 40;
                for (int i = 0; i < reply.length(); i += chunkSize) {
                    int end = Math.min(reply.length(), i + chunkSize);
                    String part = reply.substring(i, end);
                    try {
                        emitter.send(SseEmitter.event().data(part));
                    } catch (Exception sendEx) {
                        // client gone
                        break;
                    }
                    try { Thread.sleep(40); } catch (InterruptedException ignored) {}
                }
                // send done event
                try { emitter.send(SseEmitter.event().name("done").data("")); } catch (Exception ignored) {}
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });
        t.setDaemon(true);
        t.start();
        return emitter;
    }

    @GetMapping("/message/{message}")
    public String getMessagePath(@PathVariable String message) {
        return getMessage(message);
    }

    @GetMapping({"/", ""})
    public String root() {
        return "Chat API endpoints: GET /api/chat/message?message=...  or /api/chat/message/{message}";
    }

    private static String unescapeSimpleJsonString(String s) {
        if (s == null) return null;
        String result = s.replaceAll("\\\\n", "\n");
        result = result.replaceAll("\\\\r", "\r");
        result = result.replaceAll("\\\\t", "\t");
        result = result.replaceAll("\\\\\\\\", "\\\\");
        result = result.replaceAll("\\\\\"", "\"");
        return result;
    }

}
