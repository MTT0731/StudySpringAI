package com.example.springaiembedding.controller;

import com.example.springaiembedding.model.EmbeddingResponse;
import com.example.springaiembedding.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/embedding")
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    @Autowired
    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @GetMapping("/embed")
    public EmbeddingResponse embed(@RequestParam("message") String message) {
        return embeddingService.embed(message);
    }

    @GetMapping("/search")
    public String search(@RequestParam("query") String query) {
        return embeddingService.searchMostSimilarText(query);
    }
}
