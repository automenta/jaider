package dumb.jaider.ui;

import dumb.jaider.app.App;
import dumb.jaider.model.JaiderModel;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface UI {
    void init(App app) throws IOException; // App type is now dumb.jaider.app.App
    void redraw(JaiderModel model); // Model type is now dumb.jaider.model.JaiderModel

    CompletableFuture<Boolean> confirm(String title, String text);

    CompletableFuture<DiffInteractionResult> diffInteraction(String diff);

    CompletableFuture<String> configEdit(String currentConfig);

    CompletableFuture<Boolean> confirmPlan(String title, String planText, dev.langchain4j.data.message.AiMessage agentMessage);
    void setInputText(String text); // Added method
    CompletableFuture<Void> showScrollableText(String title, String textContent);
    void close() throws IOException;
}
