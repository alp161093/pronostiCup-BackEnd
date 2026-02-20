package com.pronosticup.backend.documents.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "test_docs")
public class JsonDocument {

    @Id
    private String id;

    private String message;

    public JsonDocument() {}

    public JsonDocument(String message) {
        this.message = message;
    }

    public String getId() { return id; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
