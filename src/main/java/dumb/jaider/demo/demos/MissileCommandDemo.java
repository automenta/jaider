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
        return "Generates and enhances a Missile Command game, showcasing code generation, Q&A, and enhancement.";
    }

    @Override
    public List<DemoStep> getSteps() {
        List<DemoStep> steps = new ArrayList<>();
        steps.add(new DisplayMessageStep("Missile Command Demo - Introduction",
                "Welcome to the Interactive Missile Command Generation Demo!\n\n" +
                "This demo will guide you through using Jaider to:\n" +
                "1. Generate a basic Missile Command game in Java using Swing from a text prompt.\n" +
                "2. Compile the generated game.\n" +
                "3. Use Jaider's Q&A feature to understand the generated code.\n" +
                "4. Enhance the game by requesting Jaider to add sound effect placeholders.\n" +
                "5. Re-compile the enhanced game.\n\n" +
                "This demonstrates a more complex workflow involving initial generation, code comprehension, and iterative enhancement.\n" +
                "Press OK to start."));

        steps.add(new InitialProjectGenerationStep(
                "Create a complete, runnable, single-file Missile Command game in Java using Swing. The main class must be named 'MissileCommandGame' and be in the package 'com.example.game'. Ensure basic game elements like cities, falling missiles, and a player-controlled cursor/crosshair to shoot interceptors are present. The game should be playable, even if simple.",
                "com.example.game",
                "MissileCommandGame",
                getInitialPomContent(),
                "initialCode",
                "MissileCommandGame", "JFrame", "Swing", "java.awt", "paintComponent" // Added paintComponent as a keyword
        ));

        steps.add(new DisplayMessageStep("Project Generation Explained",
                "Jaider has processed your request and generated the initial Missile Command game project.\n\n" +
                "What happened?\n" +
                "- Jaider's `CodeGenerationWorkflow` translated your prompt into a set of instructions for the LLM.\n" +
                "- The LLM generated the Java code for 'MissileCommandGame.java' within the 'com.example.game' package, including basic Swing UI elements.\n" +
                "- A 'pom.xml' file was created to define the project structure and any dependencies (though Swing is standard).\n\n" +
                "The detail in your prompt (e.g., 'single-file', 'Swing', class/package names, game elements) helps Jaider generate more accurate starting code."));

        steps.add(new DisplayMessageStep("Compilation - First Checkpoint",
                "Next, Jaider will compile the generated 'MissileCommandGame.java' using Maven.\n\n" +
                "This is a critical validation step. It checks if the LLM produced syntactically correct Java code that can be built.\n" +
                "If compilation failed, you would typically refine the initial prompt, or even ask Jaider to fix compilation errors."));

        steps.add(new CompileProjectStep("Initial Missile Command Compilation"));

        steps.add(new DisplayMessageStep("Compilation Successful!",
                "The initial Missile Command game code has been successfully compiled!\n\n" +
                "This is a good sign! It means Jaider generated a structurally valid Java application based on your description.\n" +
                "Now, let's explore the generated code using Jaider's Q&A capabilities."));

        steps.add(new DisplayMessageStep("Code Understanding with Q&A",
                "Jaider can help you understand the codebase by answering questions about it.\n\n" +
                "How it works:\n" +
                "- You provide a question and specify which part of the code (a 'code bundle' like 'initialCode') to focus on.\n" +
                "- Jaider sends your question and the relevant code context to the LLM.\n" +
                "- The LLM analyzes the code to provide an answer.\n\n" +
                "This is especially useful for quickly grasping the structure or specific details of newly generated or unfamiliar code."));

        steps.add(new AskQuestionStep(
                "What is the main class of this game as specified in the initial prompt, and in which package is it located?",
                "initialCode",
                "MissileCommandGame", "com.example.game" // Expected keywords in answer
        ));

        steps.add(new AskQuestionStep(
                "How is the game window (JFrame) initialized in the MissileCommandGame class? Show the relevant code snippet.",
                "initialCode",
                "JFrame", "setTitle", "setSize", "setDefaultCloseOperation" // Expected keywords
        ));

        steps.add(new DisplayMessageStep("Code Enhancement - Adding Features",
                "Now, let's ask Jaider to enhance the game by adding placeholders for sound effects.\n\n" +
                "This demonstrates how Jaider can modify existing code. You provide instructions on what to change or add, and Jaider attempts to implement it.\n" +
                "It's important to be clear and specific in your enhancement requests."));

        steps.add(new EnhanceProjectStep(
                "Enhance the 'MissileCommandGame.java' class in package 'com.example.game'. Add sound effects for two events: when a missile is fired and when an explosion occurs. Use javax.sound.sampled.Clip for playing sounds. Create public placeholder methods playFireSound() and playExplosionSound() that currently do nothing but include comments indicating where sound logic would go. Ensure necessary imports for javax.sound.sampled.* are added if not present.",
                "com.example.game",
                "MissileCommandGame",
                "initialCode", // Use the code from the first generation step
                "enhancedCode", // New bundle name for the modified code
                "javax.sound.sampled.Clip", "playFireSound()", "playExplosionSound()" // Keywords for verification
        ));

        steps.add(new DisplayMessageStep("Enhancement Process Explained",
                "Jaider has now attempted to modify the 'MissileCommandGame.java' file.\n\n" +
                "What happened?\n" +
                "- The `CodeGenerationWorkflow` took your enhancement request and the existing 'initialCode'.\n" +
                "- It prompted the LLM to make the specified changes (adding sound methods and imports).\n" +
                "- The modified code is now available as 'enhancedCode'.\n\n" +
                "Reviewing the diff (if available) or the full code is crucial to ensure Jaider understood and implemented the changes correctly."));

        steps.add(new DisplayMessageStep("Compile Enhanced Code - Second Checkpoint",
                "Jaider will now attempt to compile the modified Java code (with sound effect placeholders).\n\n" +
                "This ensures that the AI's changes are syntactically correct and integrate properly with the existing codebase, preventing regressions."));

        steps.add(new CompileProjectStep("Enhanced Missile Command Compilation"));

        steps.add(new DisplayMessageStep("Enhanced Code Compiled!",
                "The enhanced Missile Command game code (with sound placeholders) has also been successfully compiled!\n\n" +
                "This demonstrates Jaider's ability to iteratively develop and refactor code while maintaining its integrity and adhering to new requirements.\n" +
                "Further steps could involve asking Jaider to implement the actual sound loading logic within the placeholder methods."));

        steps.add(new DisplayMessageStep("Demo Complete - Missile Command",
                "You've reached the end of the Missile Command Interactive Demo!\n\n" +
                "Key takeaways:\n" +
                "- Jaider can generate more complex applications like a basic game.\n" +
                "- The Q&A feature helps in understanding code generated by Jaider or even existing code.\n" +
                "- Jaider can modify and enhance code based on iterative feedback.\n" +
                "- Compilation checks at each stage are vital for verifying AI-generated changes.\n\n" +
                "Press OK to exit."));
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
