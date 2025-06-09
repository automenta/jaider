package dumb.jaider.commands;

public interface Command {
    void execute(String args, AppContext context);
}
