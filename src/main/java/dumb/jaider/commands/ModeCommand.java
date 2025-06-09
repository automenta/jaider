package dumb.jaider.commands;

public class ModeCommand implements Command {
    @Override
    public void execute(String args, AppContext context) {
        String modeToSet = (args == null || args.isBlank()) ? "" : args.trim();
        context.getAppInstance().setAgentInternalPublic(modeToSet); // Needs public access
    }
}
