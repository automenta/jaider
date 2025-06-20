package dumb.jaider.app;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.UI;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;


public class SessionManager {

    private final JaiderModel model;
    private final ChatMemory chatMemory;
    private final UI ui;

    public SessionManager(JaiderModel model, ChatMemory chatMemory, UI ui) {
        this.model = model;
        this.chatMemory = chatMemory;
        this.ui = ui;
    }

    public void saveSession() {
        try {
            var sessionDir = model.dir.resolve(".jaider");
            Files.createDirectories(sessionDir);
            var session = new JSONObject();
            session.put("filesInContext", new JSONArray(model.files.stream().map(p -> model.dir.relativize(p).toString()).collect(Collectors.toList())));
            session.put("chatMemory", new JSONArray(chatMemory.messages().stream()
                .map(m -> {
                    var text = dumb.jaider.utils.Util.chatMessageToText(m);
                    return new JSONObject().put("type", m.type().name()).put("text", text == null ? "" : text);
                })
                .collect(Collectors.toList())));
            Files.writeString(sessionDir.resolve("session.json"), session.toString());
        } catch (IOException e) {
            model.addLog(AiMessage.from("[Jaider] ERROR: Failed to save session: " + e.getMessage()));
        }
    }

    public void loadSession() {
        var sessionFile = model.dir.resolve(".jaider/session.json");
        if (Files.exists(sessionFile)) {
            ui.confirm("Session Found", "Restore previous session?").thenAccept(restore -> {
                if (restore) {
                    try {
                        var content = Files.readString(sessionFile);
                        if (content.trim().isEmpty()) {
                            model.addLog(AiMessage.from("[Warning] Session file is empty. Starting a new session."));
                            return;
                        }
                        var sessionData = new JSONObject(content);

                        var filesInContextArray = sessionData.optJSONArray("filesInContext");
                        if (filesInContextArray != null) {
                            filesInContextArray.forEach(f -> model.files.add(model.dir.resolve(f.toString())));
                        }

                        var chatMemoryArray = sessionData.optJSONArray("chatMemory");
                        if (chatMemoryArray != null) {
                           chatMemory.clear(); // Clear existing messages before loading
                            for (var m : chatMemoryArray) {
                                var msg = (JSONObject) m;
                                var text = msg.optString("text", ""); // Default to empty string if null
                                var type = msg.optString("type", "USER"); // Default to USER if type is missing
                                if ("USER".equals(type)) {
                                    chatMemory.add(UserMessage.from(text));
                                } else {
                                    chatMemory.add(AiMessage.from(text));
                                }
                            }
                        }
                        model.addLog(AiMessage.from("[Jaider] Session restored."));
                    } catch (IOException e) {
                        model.addLog(AiMessage.from("[Error] Failed to read session file: " + e.getMessage()));
                    } catch (org.json.JSONException e) {
                        model.addLog(AiMessage.from("[Error] Failed to parse session data: " + e.getMessage() + ". Starting a new session."));
                    }
                }
                ui.redraw(model);
            });
        }
    }
}
