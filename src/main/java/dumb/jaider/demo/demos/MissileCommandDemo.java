package dumb.jaider.demo.demos;

import dumb.jaider.demo.DemoProvider;
import dumb.jaider.demo.DemoStep;
import dumb.jaider.demo.steps.common.AskQuestionStep;
import dumb.jaider.demo.steps.common.CompileProjectStep;
import dumb.jaider.demo.steps.common.DisplayMessageStep;
import dumb.jaider.demo.steps.common.EnhanceProjectStep;
import dumb.jaider.demo.steps.common.InitialProjectGenerationStep;

import java.util.ArrayList;
import java.util.List;

public class MissileCommandDemo implements DemoProvider {

    @Override
    public String getName() {
        return "missile_command";
    }

    @Override
    public String getDescription() {
        return "Generates and enhances a Missile Command game.";
    }

    @Override
    public List<DemoStep> getSteps() {
        List<DemoStep> steps = new ArrayList<>();
        steps.add(new DisplayMessageStep("Welcome", "Welcome to the Interactive Missile Command Generation Demo!\nPress OK to start."));
        steps.add(new InitialProjectGenerationStep(
                "a complete, runnable, single-file Missile Command game in Java using Swing. The main class should be named 'MissileCommandGame' and be in package 'com.example.game'.",
                "com.example.game",
                "MissileCommandGame",
                getInitialPomContent(),
                "initialCode",
                "MissileCommandGame", "JFrame", "Swing", "java.awt"
        ));
        steps.add(new DisplayMessageStep("Project Generation Explained",
        "Jaider has now generated a new Java project based on the description provided.\n\n" +
        "This included:\n" +
        "- Creating a 'pom.xml' for project dependencies and build configuration.\n" +
        "- Generating the main Java class ('MissileCommandGame.java') in the 'com.example.game' package.\n\n" +
        "This was achieved using Jaider's CodeGenerationWorkflow, which interacts with the configured Language Model."));
        steps.add(new DisplayMessageStep("Compilation Time",
        "Next, Jaider will attempt to compile the generated Java code using Maven.\n\n" +
        "This step verifies if the Language Model produced syntactically correct and compilable code."));
        steps.add(new CompileProjectStep("Initial Missile Command Compilation"));
        steps.add(new DisplayMessageStep("Compilation Successful!",
        "The initial Missile Command game code has been successfully compiled!\n\n" +
        "This demonstrates that the AI was able to generate valid Java code that meets the project's build requirements."));
        steps.add(new DisplayMessageStep("Q&A Time", "Now let's ask the LLM some questions about the generated code."));
        steps.add(new DisplayMessageStep("Understanding Code with Jaider",
        "Jaider's 'Ask' feature allows you to query information about the codebase.\n\n" +
        "The AI will analyze the relevant code (in this case, the 'initialCode' we just generated) to answer your questions. This is useful for understanding complex code or quickly finding specific details."));
        steps.add(new AskQuestionStep(
                "What is the main class of this game as specified in the initial prompt?",
                "initialCode",
                "missilecommandgame"
        ));
        steps.add(new AskQuestionStep(
                "How is the game window (JFrame) initialized?",
                "initialCode"
        ));
        steps.add(new DisplayMessageStep("Code Enhancement", "Next, we'll ask the LLM to enhance the game by adding sound effects."));
        steps.add(new DisplayMessageStep("Modifying Code with Jaider",
        "Jaider can not only generate new code but also modify existing code based on new requirements or instructions.\n\n" +
        "In this step, Jaider will attempt to add sound effect placeholders to the 'MissileCommandGame.java' class."));
        steps.add(new EnhanceProjectStep(
                "Add sound effects for two events: when a missile is fired and when an explosion occurs. Use javax.sound.sampled.Clip for playing sounds. Create placeholder methods playFireSound() and playExplosionSound() if actual sound file loading is complex, but include necessary imports for javax.sound.sampled.*.",
                "com.example.game",
                "MissileCommandGame",
                "initialCode",
                "enhancedCode",
                "javax.sound.sampled", "playFireSound", "playExplosionSound"
        ));
        steps.add(new DisplayMessageStep("Enhancement Explained",
        "Jaider has now modified the 'MissileCommandGame.java' file according to the enhancement request.\n\n" +
        "The CodeGenerationWorkflow sent the existing code and the new instructions to the Language Model, which produced the updated version you just saw."));
        steps.add(new DisplayMessageStep("Compile Enhanced Code",
        "Now, Jaider will attempt to compile the modified Java code (with sound effect placeholders).\n\n" +
        "This ensures that the AI's changes are syntactically correct and integrate properly with the existing code."));
        steps.add(new CompileProjectStep("Enhanced Missile Command Compilation"));
        steps.add(new DisplayMessageStep("Enhanced Code Compiled!",
        "The enhanced Missile Command game code has also been successfully compiled!\n\n" +
        "This shows Jaider's ability to iteratively develop and refactor code while maintaining its integrity."));
        steps.add(new DisplayMessageStep("Demo Complete", "You've reached the end of the Missile Command Interactive Demo!\nPress OK to exit."));
        return steps;
    }

    @Override
    public String getInitialPomContent() {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
               "    <modelVersion>4.0.0</modelVersion>\n" +
               "    <groupId>com.example.missilecommand</groupId>\n" +
               "    <artifactId>missile-command</artifactId>\n" +
               "    <version>1.0-SNAPSHOT</version>\n" +
               "    <properties>\n" +
               "        <maven.compiler.source>11</maven.compiler.source>\n" +
               "        <maven.compiler.target>11</maven.compiler.target>\n" +
               "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
               "    </properties>\n" +
               "    <dependencies>\n" +
               "        <!-- Swing is part of standard Java SE -->\n" +
               "    </dependencies>\n" +
               "    <build>\n" +
               "        <plugins>\n" +
               "            <plugin>\n" +
               "                <groupId>org.apache.maven.plugins</groupId>\n" +
               "                <artifactId>maven-compiler-plugin</artifactId>\n" +
               "                <version>3.8.1</version>\n" +
               "            </plugin>\n" +
               "        </plugins>\n" +
               "    </build>\n" +
               "</project>";
    }
}
