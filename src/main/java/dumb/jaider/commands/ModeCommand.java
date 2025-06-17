package dumb.jaider.commands;

public class ModeCommand implements Command {
    @Override
    public void execute(String args, AppContext context) {
        var modeToSet = (args == null || args.isBlank()) ? "" : args.trim();
        context.app().setAgentInternalPublic(modeToSet); // Needs public access
    }
}
