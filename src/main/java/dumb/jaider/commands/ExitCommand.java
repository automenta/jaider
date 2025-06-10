package dumb.jaider.commands;

public class ExitCommand implements Command {
    @Override
    public void execute(String args, AppContext context) {
        context.app().exitAppInternalPublic(); // Needs public access
    }
}
