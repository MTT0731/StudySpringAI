package org.example.springaiquickstart.controller;

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

    // 支持查询参数形式: /api/chat/message?message=hello
    // 也同时支持末尾带斜杠的请求 /api/chat/message/
    @GetMapping({"/message", "/message/"})
    public String getMessage(@RequestParam(value = "message", defaultValue = "你是谁") String message) {
        try {
            String url = deepseekBaseUrl;
            if (!url.endsWith("/")) {
                url += "/";
            }
            url += "v1/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (deepseekApiKey != null && !deepseekApiKey.isEmpty()) {
                headers.setBearerAuth(deepseekApiKey);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model", deepseekModel);
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", message)
            );
            body.put("messages", messages);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            String respBody = resp.getBody();
            return respBody != null ? respBody : "Empty response from DeepSeek";
        } catch (Exception ex) {
            return "调用模型出错: " + ex.getMessage();
        }
    }

    // 支持路径参数形式: /api/chat/message/hello
    @GetMapping("/message/{message}")
    public String getMessagePath(@PathVariable String message) {
        return getMessage(message);
    }

    // 支持访问控制器根路径，避免访问 /api/chat/ 时出现 Whitelabel 404
    @GetMapping({"/", ""})
    public String root() {
        return "Chat API endpoints: GET /api/chat/message?message=...  or /api/chat/message/{message}";
    }

}
