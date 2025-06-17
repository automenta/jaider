package dumb.jaider.demo.demos;

import dumb.jaider.demo.DemoProvider;
import dumb.jaider.demo.DemoStep;
import dumb.jaider.demo.steps.common.CompileProjectStep;
import dumb.jaider.demo.steps.common.DisplayMessageStep;
import dumb.jaider.demo.steps.common.InitialProjectGenerationStep;

import java.util.ArrayList;
import java.util.List;

public class HelloWorldDemo implements DemoProvider {

    @Override
    public String getName() {
        return "hello_world";
    }

    @Override
    public String getDescription() {
        return "Generates a simple Hello World application, showcasing basic code generation and compilation.";
    }

    @Override
    public List<DemoStep> getSteps() {
        List<DemoStep> steps = new ArrayList<>();
        steps.add(new DisplayMessageStep("Hello, World! Demo - Introduction",
                "Welcome to the Interactive 'Hello, World!' Java Application Demo.\n\n" +
                "This demo will walk you through using Jaider to:\n" +
                "1. Generate a simple 'Hello, World!' Java program based on a prompt.\n" +
                "2. Compile the generated code using Maven.\n\n" +
                "This showcases Jaider's basic code generation and verification capabilities.\n" +
                "Press OK to start."));

        steps.add(new InitialProjectGenerationStep(
                "Create a simple Hello World application in Java. The main class should be named 'HelloWorld' in the package 'com.example.hello', and it must print the exact string 'Hello, World!' to the console.",
                "com.example.hello",
                "HelloWorld",
                getInitialPomContent(),
                "initialCode",
                "HelloWorld", "System.out.println", "Hello, World!"
        ));

        steps.add(new DisplayMessageStep("Project Generation Explained",
                "Jaider has now processed the prompt and generated a new Java project.\n\n" +
                "What happened behind the scenes?\n" +
                "- Jaider's `CodeGenerationWorkflow` took your text prompt.\n" +
                "- It instructed the underlying Language Model (LLM) to generate the necessary Java code.\n" +
                "- A 'pom.xml' file for Maven build configuration was created.\n" +
                "- The main 'HelloWorld.java' class was generated in the 'com.example.hello' package, designed to print 'Hello, World!' to the console.\n\n" +
                "This step demonstrates how Jaider translates natural language requirements into functional code structure."));

        steps.add(new DisplayMessageStep("Compilation - Verifying the Code",
                "Next, Jaider will attempt to compile the generated 'HelloWorld.java' code using Maven.\n\n" +
                "Why is this important?\n" +
                "- This step automatically verifies if the Language Model produced syntactically correct and compilable Java code.\n" +
                "- It's a crucial part of the iterative development process. If compilation fails, you'd typically refine the prompt or manually correct the code and try again.\n\n" +
                "Press OK to proceed with compilation."));

        steps.add(new CompileProjectStep("Hello, World! Compilation"));

        steps.add(new DisplayMessageStep("Compilation Successful!",
                "The 'Hello, World!' application code has been successfully compiled by Maven!\n\n" +
                "This confirms that Jaider, given a clear prompt, generated valid Java code for this basic scenario.\n" +
                "In more complex projects, you would proceed with further testing and refinement."));

        steps.add(new DisplayMessageStep("Demo Complete - Hello, World!",
                "You've reached the end of the 'Hello, World!' Interactive Demo!\n\n" +
                "Key takeaways:\n" +
                "- Jaider can generate code from natural language prompts.\n" +
                "- Jaider can integrate with build tools like Maven to compile code.\n" +
                "- The process is iterative: generate, review, compile, (refine if necessary).\n\n" +
                "Press OK to exit."));
        return steps;
    }

    @Override
    public String getInitialPomContent() {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
               "    <modelVersion>4.0.0</modelVersion>\n" +
               "    <groupId>com.example.helloworld</groupId>\n" +
               "    <artifactId>hello-world-app</artifactId>\n" +
               "    <version>1.0-SNAPSHOT</version>\n" +
               "    <properties>\n" +
               "        <maven.compiler.source>11</maven.compiler.source>\n" +
               "        <maven.compiler.target>11</maven.compiler.target>\n" +
               "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
               "    </properties>\n" +
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
