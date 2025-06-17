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
        return "Generates a simple Hello World application.";
    }

    @Override
    public List<DemoStep> getSteps() {
        List<DemoStep> steps = new ArrayList<>();
        steps.add(new DisplayMessageStep("Hello, World! Demo", "Welcome to the Interactive Hello, World! Java Application Demo.\nThis demo will generate and compile a simple Hello World program.\nPress OK to start."));
        steps.add(new InitialProjectGenerationStep(
                "a simple Hello World application in Java. The main class should be named 'HelloWorld' in package 'com.example.hello', and it should print 'Hello, World!' to the console.",
                "com.example.hello",
                "HelloWorld",
                getInitialPomContent(),
                "initialCode",
                "HelloWorld", "System.out.println", "Hello, World!"
        ));
        steps.add(new DisplayMessageStep("Project Generation Explained",
        "Jaider has generated a new Java project for the 'Hello, World!' application.\n\n" +
        "This included:\n" +
        "- Creating a 'pom.xml' for the project's build configuration.\n" +
        "- Generating the main 'HelloWorld.java' class in the 'com.example.hello' package, which prints 'Hello, World!' to the console.\n\n" +
        "This was done by Jaider's CodeGenerationWorkflow using the provided description."));
        steps.add(new DisplayMessageStep("Compilation Time",
        "Next, Jaider will attempt to compile the generated 'HelloWorld.java' code using Maven.\n\n" +
        "This step checks if the Language Model produced valid, compilable Java code."));
        steps.add(new CompileProjectStep("Hello, World! Compilation"));
        steps.add(new DisplayMessageStep("Compilation Successful!",
        "The 'Hello, World!' application code has been successfully compiled!\n\n" +
        "This demonstrates Jaider's ability to generate correct and runnable basic Java applications from a simple prompt."));
        steps.add(new DisplayMessageStep("Demo Complete", "You've reached the end of the Hello, World! Interactive Demo!\nPress OK to exit."));
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
