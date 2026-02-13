package com.pronosticup.backend.documents.repo;

import com.pronosticup.backend.documents.model.JsonDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JsonDocumentRepository extends MongoRepository<JsonDocument, String> {
}

