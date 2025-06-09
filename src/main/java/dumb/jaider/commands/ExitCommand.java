package dumb.jaider.commands;

public class ExitCommand implements Command {
    @Override
    public void execute(String args, AppContext context) {
        context.getAppInstance().exitAppInternalPublic(); // Needs public access
    }
}
