package com.example.springaiembedding.model;

import java.util.List;

public class EmbeddingResponse {

    private String model;
    private String input;
    private List<Double> embedding;
    private String error;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

