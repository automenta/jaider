package dumb.jaider.llm;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.stream.Collectors;

public class NoOpEmbeddingModel implements EmbeddingModel {

    private final int dimension;

    public NoOpEmbeddingModel() {
        this(384); // A common dimension, like AllMiniLML6V2
    }

    public NoOpEmbeddingModel(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        var embeddings = textSegments.stream()
                .map(segment -> {
                    var vector = new float[dimension]; // Array of zeros
                    return new Embedding(vector);
                })
                .collect(Collectors.toList());
        return Response.from(embeddings);
    }

    // Optional: Override dimension() if it's part of a newer interface version,
    // otherwise, it might not be needed or available in 1.0.0-beta3's EmbeddingModel interface.
    // For now, assuming it's not strictly needed for a basic no-op.
    // public int dimension() {
    //     return dimension;
    // }
}
