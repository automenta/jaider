package dumb.jaider.commands;

import dumb.jaider.commands.AppContext;
import dumb.jaider.app.App;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModeCommandTest {

    @Mock
    private AppContext appContext;

    @Mock
    private App app;

    @InjectMocks
    private ModeCommand modeCommand;

    @BeforeEach
    void setUp() {
        when(appContext.app()).thenReturn(app);
    }

    @Test
    void execute_withValidModeString_shouldCallAppSetAgent() {
        String mode = "testMode";
        modeCommand.execute(mode);
        verify(app).setAgentInternalPublic(mode);
    }

    @Test
    void execute_withNullArgs_shouldCallAppSetAgentWithEmptyString() {
        modeCommand.execute(null);
        verify(app).setAgentInternalPublic("");
    }

    @Test
    void execute_withBlankArgs_shouldCallAppSetAgentWithEmptyString() {
        modeCommand.execute("   ");
        verify(app).setAgentInternalPublic("");
    }

    @Test
    void execute_withModeStringContainingSpaces_shouldTrimAndCallAppSetAgent() {
        String modeWithSpaces = "  testModeWithSpaces  ";
        modeCommand.execute(modeWithSpaces);
        // Command implementation trims the argument.
        verify(app).setAgentInternalPublic("testModeWithSpaces");
    }
}
