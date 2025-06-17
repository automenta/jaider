package dumb.jaider.app;

import dev.langchain4j.data.message.AiMessage;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.service.BuildManagerService;
import dumb.jaider.service.GitService;
import dumb.jaider.service.RestartService;
import dumb.jaider.service.SelfUpdateOrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelfUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(SelfUpdateService.class);

    private final Config config;
    private final JaiderModel model;
    private final App app; // Forcing exit if necessary

    private BuildManagerService buildManagerService;
    private GitService gitService;
    private RestartService restartService;
    private StartupService startupService;
    private SelfUpdateOrchestratorService selfUpdateOrchestratorService;

    public SelfUpdateService(Config config, JaiderModel model, App app) {
        this.config = config;
        this.model = model;
        this.app = app;
        initializeServices();
    }

    private void initializeServices() {
        var injector = config.getInjector();
        if (injector == null) {
            logger.error("DependencyInjector not available. Self-update services cannot be initialized.");
            model.addLog(AiMessage.from("[Jaider] CRITICAL ERROR: DI not available for SelfUpdateService. Self-update features disabled."));
            return;
        }

        try {
            this.buildManagerService = (BuildManagerService) injector.getComponent("buildManagerService");
            this.gitService = (GitService) injector.getComponent("gitService");
            this.restartService = (RestartService) injector.getComponent("restartService");

            // StartupService is instantiated here as it depends on the services above
            if (this.buildManagerService != null && this.gitService != null && this.restartService != null) {
                this.startupService = new StartupService(this.model, this.config, this.buildManagerService, this.gitService, this.restartService);
                logger.info("StartupService initialized successfully within SelfUpdateService.");
            } else {
                logger.error("CRITICAL: Could not initialize StartupService due to missing core dependencies (BuildManager, Git, or Restart services). Post-update validation will be skipped.");
                this.model.addLog(AiMessage.from("[Jaider] CRITICAL ERROR: StartupService not initialized in SelfUpdateService due to missing dependencies."));
                this.startupService = null;
            }

            // SelfUpdateOrchestratorService might depend on UI or other app components for confirmation
            // For now, assuming it's fetched like others. If it needs UI, App's UI instance could be passed.
            // Or, SelfUpdateOrchestratorService itself gets UI from DI.
            // Let's assume it might need UI passed via App or DI.
            // The existing SelfUpdateOrchestratorService in App.update did not show explicit UI passing,
            // but its triggerUserConfirmationProcess() implies UI interaction.
            this.selfUpdateOrchestratorService = (SelfUpdateOrchestratorService) injector.getComponent("selfUpdateOrchestratorService"); // Corrected call
            // If selfUpdateOrchestratorService needs UI, and it's not DI-injected into it,
            // it might need a setter or for App to mediate UI interactions.
            // For now, this matches how it was fetched in App.java.

        } catch (Exception e) {
            logger.error("Error initializing self-update related services via DI: {}", e.getMessage(), e);
            model.addLog(AiMessage.from("[Jaider] CRITICAL ERROR: Failed to initialize self-update services. Features may be unavailable. " + e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    public boolean performStartupValidation() {
        // Proceed, but with logged error
        if (this.startupService != null) {
            var proceedNormalStartup = this.startupService.performStartupValidationChecks();
            if (!proceedNormalStartup) {
                logger.error("CRITICAL: StartupService indicated a restart was (or should have been) triggered, but execution continued. Forcing exit.");
                this.model.addLog(AiMessage.from("[SelfUpdateService] CRITICAL: Post-validation restart did not terminate instance. Forcing exit."));
                // App.exitAppInternalPublic() might be too gentle or have other side effects.
                // A more direct System.exit might be needed if app reference is problematic.
                System.exit(1); // Force exit
                return false; // Should be unreachable
            }
        } else {
            logger.error("CRITICAL: StartupService is not initialized. Self-update validation checks will be skipped.");
            this.model.addLog(AiMessage.from("[SelfUpdateService] CRITICAL: StartupService not initialized. Validation skipped."));
        }
        return true; // Proceed with normal startup
    }

    public void checkAndTriggerSelfUpdateConfirmation() {
        // Check app state via App instance if needed, e.g. app.getState() == App.State.IDLE
        // For now, assuming direct check is fine as it was in App.java
        if (this.selfUpdateOrchestratorService != null &&
            this.selfUpdateOrchestratorService.getPendingUpdate() != null &&
            !this.selfUpdateOrchestratorService.isUpdateInProgress() &&
            app.getState() == App.State.IDLE) { // Accessing App's state
            logger.info("Triggering self-update confirmation process via orchestrator...");
            this.selfUpdateOrchestratorService.triggerUserConfirmationProcess();
        }
    }
}
