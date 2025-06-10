package dumb.jaider.utils;

import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import dev.langchain4j.data.message.*;

import java.io.IOException;
import java.util.Arrays;

public class Util {

    private Util() {
        // Prevent instantiation
    }

    // Renamed from text to chatMessageToText as per subtask description
    public static String chatMessageToText(ChatMessage chatMessage) {
        return switch (chatMessage) {
            case null -> "[Null ChatMessage]";
            case AiMessage message -> message.text();
            case UserMessage message ->
                // For Langchain4j 1.0.0-beta3, UserMessage should have .text() if it's simple,
                // but .singleText() is safer if we know/expect it to be a single text content.
                    message.singleText();
            case SystemMessage message -> message.text();
            case ToolExecutionResultMessage message -> message.text();
            default ->
                // Fallback for other ChatMessage types.
                // For 1.0.0-beta3, ChatMessage itself does not have a .text() method.
                // Subclasses like AiMessage, SystemMessage, UserMessage (via singleText) do.
                // If it's a custom ChatMessage or a type not handled above, direct text extraction is not guaranteed.
                    "[Unsupported ChatMessage type for direct text extraction: " + chatMessage.getClass().getName() + "]";
        };
    }

    public static Patch<String> diffReader(String diff) throws IOException { // Changed return type
        if (diff == null) {
            // Or throw a more specific exception like IllegalArgumentException
            throw new IOException("Input diff string cannot be null.");
        }
        // parseUnifiedDiff expects List<String>, so we split the diff string
        return UnifiedDiffUtils.parseUnifiedDiff(Arrays.asList(diff.split("\n")));
    }

}
