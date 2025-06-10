package dumb.jaider.ui;

import dumb.jaider.app.App; // Updated import
import dumb.jaider.model.JaiderModel; // Updated import
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface UI {
    void init(App app) throws IOException; // App type is now dumb.jaider.app.App
    void redraw(JaiderModel model); // Model type is now dumb.jaider.model.JaiderModel
    CompletableFuture<Boolean> requestConfirmation(String title, String text);
    CompletableFuture<DiffInteractionResult> requestDiffInteraction(String diff);
    CompletableFuture<String> requestConfigEdit(String currentConfig);
    void close() throws IOException;
}
