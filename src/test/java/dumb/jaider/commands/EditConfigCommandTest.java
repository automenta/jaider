package dumb.jaider.commands;

import dumb.jaider.commands.AppContext;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.app.App;
import dumb.jaider.config.Config;
import dev.langchain4j.data.message.AiMessage; // Corrected import
import dumb.jaider.ui.UI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EditConfigCommandTest {

    @Mock
    private AppContext appContext;
    @Mock
    private JaiderModel model;
    @Mock
    private Config config;
    @Mock
    private UI ui;
    @Mock
    private App app;

    @InjectMocks
    private EditConfigCommand editConfigCommand;

    private final String sampleConfigJson = "{\"llmProvider\":\"ollama\"}";
    private final String newConfigJson = "{\"llmProvider\":\"openai\"}";

    @BeforeEach
    void setUp() {
        lenient().when(appContext.getModel()).thenReturn(model); // Made lenient
        lenient().when(appContext.getConfig()).thenReturn(config); // Made lenient
        // when(appContext.getUi()).thenReturn(ui); // Moved to specific tests
        lenient().when(appContext.getAppInstance()).thenReturn(app); // Made lenient
    }

    @Test
    void execute_successfulEdit_shouldUpdateConfigAndFinishTurn() throws IOException {
        when(appContext.getUi()).thenReturn(ui); // Added here
        when(config.readForEditing()).thenReturn(sampleConfigJson);
        when(ui.requestConfigEdit(sampleConfigJson)).thenReturn(CompletableFuture.completedFuture(newConfigJson));

        editConfigCommand.execute(null, appContext); // Corrected signature

        verify(app).setStatePublic(App.State.WAITING_USER_CONFIRMATION);
        verify(config).readForEditing();
        verify(ui).requestConfigEdit(sampleConfigJson);
        verify(app).updateAppConfigPublic(newConfigJson);
        verify(app).finishTurnPublic(null);
        verify(model, never()).addLog(any(AiMessage.class)); // Corrected verification
    }

    @Test
    void execute_userCancelsEdit_shouldNotUpdateConfigAndFinishTurn() throws IOException {
        when(appContext.getUi()).thenReturn(ui); // Added here
        when(config.readForEditing()).thenReturn(sampleConfigJson);
        when(ui.requestConfigEdit(sampleConfigJson)).thenReturn(CompletableFuture.completedFuture(null)); // User cancels

        editConfigCommand.execute(null, appContext); // Corrected signature

        verify(app).setStatePublic(App.State.WAITING_USER_CONFIRMATION);
        verify(config).readForEditing();
        verify(ui).requestConfigEdit(sampleConfigJson);
        verify(app, never()).updateAppConfigPublic(anyString()); // Not called
        verify(app).finishTurnPublic(null);
        // EditConfigCommand's current implementation does not directly log "Config edit cancelled."
        // It calls finishTurnPublic(null), and App might log it, or UI might handle it.
        // For this unit test, we verify what EditConfigCommand itself does.
        // If a log message is crucial, it should be part of EditConfigCommand's responsibility.
        // verify(model).addLog(AiMessage.from("Config edit cancelled.")); // This would require EditConfigCommand to log
    }

    @Test
    void execute_readForEditingThrowsIOException_shouldLogErrorAndFinishTurnWithError() throws IOException {
        when(config.readForEditing()).thenThrow(new IOException("Failed to read config"));

        editConfigCommand.execute(null, appContext); // Corrected signature

        verify(app).setStatePublic(App.State.WAITING_USER_CONFIRMATION);
        verify(config).readForEditing();
        verify(ui, never()).requestConfigEdit(anyString());
        verify(app, never()).updateAppConfigPublic(anyString());

        ArgumentCaptor<AiMessage> messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
        verify(app).finishTurnPublic(messageCaptor.capture());
        assertNotNull(messageCaptor.getValue());
        assertTrue(messageCaptor.getValue().text().contains("Could not read config file: Failed to read config")); // Corrected: AiMessage.text() and message
        // verify(model).logError("Error preparing config for editing: Failed to read config"); // EditConfigCommand itself doesn't call model.logError here
    }

    @Test
    void execute_updateAppConfigThrowsIOException_shouldLogErrorAndFinishTurn() throws IOException {
        when(appContext.getUi()).thenReturn(ui); // Added here as requestConfigEdit is called
        when(config.readForEditing()).thenReturn(sampleConfigJson);
        when(ui.requestConfigEdit(sampleConfigJson)).thenReturn(CompletableFuture.completedFuture(newConfigJson));
        doThrow(new IOException("Failed to save config")).when(app).updateAppConfigPublic(newConfigJson);

        editConfigCommand.execute(null, appContext);

        verify(app).setStatePublic(App.State.WAITING_USER_CONFIRMATION);
        verify(config).readForEditing();
        verify(ui).requestConfigEdit(sampleConfigJson);
        verify(app).updateAppConfigPublic(newConfigJson); // This call will throw the mocked exception

        ArgumentCaptor<AiMessage> logCaptor = ArgumentCaptor.forClass(AiMessage.class);
        // The log message is added by EditConfigCommand's catch block
        verify(model).addLog(logCaptor.capture());
        assertTrue(logCaptor.getValue().text().contains("[Error] Failed to save config: Failed to save config"));

        // finishTurnPublic should still be called once by the thenAccept block in EditConfigCommand
        verify(app, times(1)).finishTurnPublic(null);
    }
}
