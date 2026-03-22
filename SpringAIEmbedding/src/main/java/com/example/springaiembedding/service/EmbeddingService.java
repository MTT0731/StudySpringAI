package com.example.springaiembedding.service;

import com.example.springaiembedding.model.EmbeddingResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final WebClient webClient;
    private final String model;
    private final String apiKey;

    private final List<String> knowledgeBase = List.of(
            "Spring AI 可以帮助开发者快速接入大语言模型能力。",
            "Embedding 的作用是把文本转换成向量，便于做相似度检索。",
            "余弦相似度越接近 1，说明两段文本在向量空间里越相似。",
            "RAG 的核心流程通常是检索、拼接上下文、再交给大模型生成。",
            "向量数据库适合存储知识片段，并支持高效的语义搜索。",
            "Spring Boot 可以帮助我们快速搭建 Web 服务和接口。",
            "Controller 负责接收请求，Service 负责承载核心业务逻辑。",
            "前端页面可以调用后端接口来展示 embedding 和检索结果。"
    );

    private final Map<String, List<Double>> knowledgeBaseEmbeddings = new LinkedHashMap<>();

    public EmbeddingService(WebClient.Builder webClientBuilder,
                            @Value("${embedding.base-url}") String baseUrl,
                            @Value("${embedding.model}") String model,
                            @Value("${embedding.api-key:}") String apiKey) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        if (model != null && !model.isBlank() && !model.startsWith("nvidia/")) {
            this.model = "nvidia/" + model;
        } else {
            this.model = model;
        }
        this.apiKey = apiKey;
    }

    @PostConstruct
    public void initKnowledgeBaseEmbeddings() {
        log.info("开始初始化知识库向量，知识条数: {}", knowledgeBase.size());
        knowledgeBaseEmbeddings.clear();

        for (String text : knowledgeBase) {
            EmbeddingResponse response = embedPassage(text);
            List<Double> vector = response.getEmbedding();
            if (vector != null && !vector.isEmpty()) {
                knowledgeBaseEmbeddings.put(text, vector);
            } else {
                log.warn("知识库文本向量化失败: {} error={}", text, response.getError());
            }
        }

        log.info("知识库向量初始化完成，可用向量条数: {}", knowledgeBaseEmbeddings.size());
    }

    public EmbeddingResponse embed(String message) {
        return embedPassage(message);
    }

    public String searchMostSimilarText(String query) {
        if (knowledgeBaseEmbeddings.isEmpty()) {
            return "知识库向量还没有初始化完成，请稍后再试。";
        }

        EmbeddingResponse queryEmbeddingResponse = embedQuery(query);
        List<Double> queryVector = queryEmbeddingResponse.getEmbedding();
        if (queryVector == null || queryVector.isEmpty()) {
            return "查询文本向量化失败：" + queryEmbeddingResponse.getError();
        }

        String bestMatch = "未找到相似文本";
        double bestScore = -1D;

        for (Map.Entry<String, List<Double>> entry : knowledgeBaseEmbeddings.entrySet()) {
            double score = cosineSimilarity(queryVector, entry.getValue());
            log.info("相似度比较 query='{}' candidate='{}' score={}", query, entry.getKey(), score);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = entry.getKey();
            }
        }

        return bestMatch;
    }

    private EmbeddingResponse embedPassage(String text) {
        return embedByInputType(text, "passage");
    }

    private EmbeddingResponse embedQuery(String text) {
        return embedByInputType(text, "query");
    }

    private EmbeddingResponse embedByInputType(String text, String inputType) {
        EmbeddingResponse out = new EmbeddingResponse();
        out.setInput(text);
        out.setModel(model);

        Map<String, Object> requestBody = Map.of(
                "input", List.of(text),
                "model", model,
                "input_type", inputType,
                "encoding_format", "float"
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (apiKey != null && !apiKey.isBlank()) {
                            headers.setBearerAuth(apiKey);
                        }
                    })
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            List<Double> embedding = extractEmbedding(response);
            out.setEmbedding(embedding);
            if (embedding == null || embedding.isEmpty()) {
                out.setError("未能从响应中解析出 embedding");
            }
        } catch (WebClientResponseException e) {
            log.error("Embedding API 响应异常, status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            out.setError("Embedding API 调用失败: HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            log.error("Embedding API 调用异常", e);
            out.setError("Embedding API 调用异常: " + e.getMessage());
        }

        return out;
    }

    private List<Double> extractEmbedding(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return Collections.emptyList();
        }

        Object dataObj = response.get("data");
        if (dataObj instanceof List<?> dataList && !dataList.isEmpty()) {
            Object first = dataList.get(0);
            if (first instanceof Map<?, ?> firstMap) {
                Object embeddingObj = firstMap.get("embedding");
                return toDoubleList(embeddingObj);
            }
        }

        Object embeddingObj = response.get("embedding");
        return toDoubleList(embeddingObj);
    }

    private List<Double> toDoubleList(Object embeddingObj) {
        if (!(embeddingObj instanceof List<?> rawList)) {
            return Collections.emptyList();
        }

        List<Double> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Number number) {
                result.add(number.doubleValue());
            }
        }
        return result;
    }

    private double cosineSimilarity(List<Double> vector1, List<Double> vector2) {
        if (vector1 == null || vector2 == null || vector1.isEmpty() || vector2.isEmpty()) {
            return -1D;
        }

        int size = Math.min(vector1.size(), vector2.size());
        double dotProduct = 0D;
        double normA = 0D;
        double normB = 0D;

        for (int i = 0; i < size; i++) {
            double a = vector1.get(i);
            double b = vector2.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA == 0D || normB == 0D) {
            return -1D;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
