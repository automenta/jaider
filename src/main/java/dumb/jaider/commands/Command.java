package dumb.jaider.commands;

@FunctionalInterface
public interface Command {
    void execute(String args, AppContext context);
}
