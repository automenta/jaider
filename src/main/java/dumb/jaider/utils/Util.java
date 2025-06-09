package dumb.jaider.utils;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import com.github.difflib.unifieddiff.UnifiedDiff;
import com.github.difflib.unifieddiff.UnifiedDiffReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class Util {

    private Util() {
        // Prevent instantiation
    }

    public static String text(ChatMessage msg) {
        // Handle potential null messages or messages with null text gracefully
        if (msg == null) {
            return "";
        }
        if (msg instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) msg;
            return userMessage.singleText() == null ? "" : userMessage.singleText();
        } else if (msg instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) msg;
            return aiMessage.text() == null ? "" : aiMessage.text();
        }
        // Fallback for other ChatMessage types, if any, that might not have a direct text() method
        // or if their text() can be null.
        String textContent = msg.text();
        return textContent == null ? "" : textContent;
    }

    public static UnifiedDiff diffReader(String diff) throws IOException {
        if (diff == null) {
            // Or throw a more specific exception like IllegalArgumentException
            throw new IOException("Input diff string cannot be null.");
        }
        return UnifiedDiffReader.parseUnifiedDiff(new ByteArrayInputStream(diff.getBytes()));
    }
}
