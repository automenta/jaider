package dumb.jaider.utils;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
// Potentially add: import dev.langchain4j.data.message.TextContent; // Not needed for 1.0.0-beta3 as .text() should be available

import com.github.difflib.UnifiedDiffUtils; // Changed import
import com.github.difflib.patch.Patch; // Added import
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays; // Added import

public class Util {

    private Util() {
        // Prevent instantiation
    }

    // Renamed from text to chatMessageToText as per subtask description
    public static String chatMessageToText(ChatMessage chatMessage) {
        if (chatMessage == null) {
            return "[Null ChatMessage]";
        }
        if (chatMessage instanceof AiMessage) {
            return ((AiMessage) chatMessage).text();
        } else if (chatMessage instanceof UserMessage) {
            // For Langchain4j 1.0.0-beta3, UserMessage should have .text() if it's simple,
            // but .singleText() is safer if we know/expect it to be a single text content.
            return ((UserMessage) chatMessage).singleText();
        } else if (chatMessage instanceof SystemMessage) {
            return ((SystemMessage) chatMessage).text();
        } else if (chatMessage instanceof ToolExecutionResultMessage) {
            return ((ToolExecutionResultMessage) chatMessage).text();
        }
        // Fallback for other ChatMessage types.
        // For 1.0.0-beta3, ChatMessage itself does not have a .text() method.
        // Subclasses like AiMessage, SystemMessage, UserMessage (via singleText) do.
        // If it's a custom ChatMessage or a type not handled above, direct text extraction is not guaranteed.
        return "[Unsupported ChatMessage type for direct text extraction: " + chatMessage.getClass().getName() + "]";
    }

    public static Patch<String> diffReader(String diff) throws IOException { // Changed return type
        if (diff == null) {
            // Or throw a more specific exception like IllegalArgumentException
            throw new IOException("Input diff string cannot be null.");
        }
        // parseUnifiedDiff expects List<String>, so we split the diff string
        return UnifiedDiffUtils.parseUnifiedDiff(Arrays.asList(diff.split("\n")));
    }


    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
