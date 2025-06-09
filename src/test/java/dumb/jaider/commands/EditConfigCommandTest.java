package dumb.jaider.commands;

import dumb.jaider.AppContext;
import dumb.jaider.JaiderModel;
import dumb.jaider.app.App;
import dumb.jaider.config.Config;
import dumb.jaider.models.AiMessage;
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
        when(appContext.model()).thenReturn(model);
        when(appContext.config()).thenReturn(config);
        when(appContext.ui()).thenReturn(ui);
        when(appContext.app()).thenReturn(app);
    }

    @Test
    void execute_successfulEdit_shouldUpdateConfigAndFinishTurn() throws IOException {
        when(config.readForEditing()).thenReturn(sampleConfigJson);
        when(ui.requestConfigEdit(sampleConfigJson)).thenReturn(CompletableFuture.completedFuture(newConfigJson));

        editConfigCommand.execute(null);

        verify(app).setStatePublic(App.State.WAITING_USER_CONFIRMATION);
        verify(config).readForEditing();
        verify(ui).requestConfigEdit(sampleConfigJson);
        verify(app).updateAppConfigPublic(newConfigJson);
        verify(app).finishTurnPublic(null);
        verify(model, never()).logUser(anyString()); // No error messages logged
        verify(model, never()).logError(anyString());
    }

    @Test
    void execute_userCancelsEdit_shouldNotUpdateConfigAndFinishTurn() throws IOException {
        when(config.readForEditing()).thenReturn(sampleConfigJson);
        when(ui.requestConfigEdit(sampleConfigJson)).thenReturn(CompletableFuture.completedFuture(null)); // User cancels

        editConfigCommand.execute(null);

        verify(app).setStatePublic(App.State.WAITING_USER_CONFIRMATION);
        verify(config).readForEditing();
        verify(ui).requestConfigEdit(sampleConfigJson);
        verify(app, never()).updateAppConfigPublic(anyString()); // Not called
        verify(app).finishTurnPublic(null);
        verify(model).logUser("Config edit cancelled.");
    }

    @Test
    void execute_readForEditingThrowsIOException_shouldLogErrorAndFinishTurnWithError() throws IOException {
        when(config.readForEditing()).thenThrow(new IOException("Failed to read config"));

        editConfigCommand.execute(null);

        verify(app).setStatePublic(App.State.WAITING_USER_CONFIRMATION);
        verify(config).readForEditing();
        verify(ui, never()).requestConfigEdit(anyString());
        verify(app, never()).updateAppConfigPublic(anyString());

        ArgumentCaptor<AiMessage> messageCaptor = ArgumentCaptor.forClass(AiMessage.class);
        verify(app).finishTurnPublic(messageCaptor.capture());
        assertNotNull(messageCaptor.getValue());
        assertTrue(messageCaptor.getValue().message().contains("Error reading config for editing: Failed to read config"));
        verify(model).logError("Error preparing config for editing: Failed to read config");
    }

    @Test
    void execute_updateAppConfigThrowsIOException_shouldLogErrorAndFinishTurn() throws IOException {
        when(config.readForEditing()).thenReturn(sampleConfigJson);
        when(ui.requestConfigEdit(sampleConfigJson)).thenReturn(CompletableFuture.completedFuture(newConfigJson));
        doThrow(new IOException("Failed to save config")).when(app).updateAppConfigPublic(newConfigJson);

        editConfigCommand.execute(null);

        verify(app).setStatePublic(App.State.WAITING_USER_CONFIRMATION);
        verify(config).readForEditing();
        verify(ui).requestConfigEdit(sampleConfigJson);
        verify(app).updateAppConfigPublic(newConfigJson);

        verify(app).finishTurnPublic(null); // finishTurn is called with null even if updateAppConfigPublic fails
                                            // because the error is handled and logged within the command's completable future chain.
        verify(model).logError("Error saving new config: Failed to save config");
    }
}
