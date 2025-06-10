package dumb.jaider.commands;

import dumb.jaider.ui.CommandSender; // Added import

public interface Command {
    String getCommandName(); // Added
    String getHelp(); // Added
    void execute(String[] args, CommandSender sender); // Modified signature
}
