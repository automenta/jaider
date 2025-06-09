package dumb.jaider.commands;

import dumb.jaider.model.JaiderModel;
import dumb.jaider.config.Config;
import dumb.jaider.ui.UI;
import dumb.jaider.app.App; // Updated import

public class AppContext {
    public final JaiderModel model;
    public final Config config;
    public final UI ui;
    public final App appInstance; // Instance of the App class

    public AppContext(JaiderModel model, Config config, UI ui, App appInstance) {
        this.model = model;
        this.config = config;
        this.ui = ui;
        this.appInstance = appInstance;
    }

    // Convenience getters
    public JaiderModel getModel() {
        return model;
    }

    public Config getConfig() {
        return config;
    }

    public UI getUi() {
        return ui;
    }

    public App getAppInstance() { // Updated return type
        return appInstance;
    }
}
