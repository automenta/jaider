package dumb.jaider.commands;

import org.jaider.app.app.App;
import dumb.jaider.config.Config;
import dumb.jaider.model.JaiderModel;
import dumb.jaider.ui.UI;

/**
 * @param app  Instance of the App class */
public record AppContext(JaiderModel model, Config config, UI ui, App app) {
}
