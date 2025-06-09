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
class ExitCommandTest {

    @Mock
    private AppContext appContext;

    @Mock
    private App app;

    @InjectMocks
    private ExitCommand exitCommand;

    @BeforeEach
    void setUp() {
        when(appContext.app()).thenReturn(app);
    }

    @Test
    void execute_shouldCallAppExit() {
        exitCommand.execute(null); // Argument is not used by ExitCommand

        verify(app).exitAppInternalPublic();
    }
}
