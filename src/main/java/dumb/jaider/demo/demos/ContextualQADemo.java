package dumb.jaider.demo.demos;

import dumb.jaider.demo.DemoProvider;
import dumb.jaider.demo.DemoStep;
import dumb.jaider.demo.steps.common.AskQuestionStep;
import dumb.jaider.demo.steps.common.ExplainCodeStep;
import dumb.jaider.demo.steps.common.InitialProjectGenerationStep;
import dumb.jaider.demo.steps.common.ShowMessageStep;

import java.util.Arrays;
import java.util.List;

public class ContextualQADemo implements DemoProvider {

    @Override
    public String getName() {
        return "contextual_qa_demo";
    }

    @Override
    public String getDescription() {
        return "Demonstrates contextual question answering based on generated code.";
    }

    @Override
    public List<DemoStep> getSteps() {
        String initialPrompt = "Generate a simple Java class named 'Calculator' in package 'com.example.math' " +
                               "with two public static methods: 'add(int a, int b)' that returns their sum, " +
                               "and 'subtract(int a, int b)' that returns their difference. The class should not have a public constructor.";

        String packageName = "com.example.math";
        String className = "Calculator";

        return Arrays.asList(
            new ShowMessageStep("Welcome to the Contextual Q&A Demo!"),
            new ShowMessageStep("This demo will generate a simple Java class and then answer questions about it, showing contextual understanding."),

            new InitialProjectGenerationStep(
                initialPrompt,
                packageName,
                className,
                getInitialPomContent(), // Use the POM content from this provider
                Arrays.asList("Calculator", "add", "subtract", "static", "int a", "int b") // Keywords for verification
            ),
            new ExplainCodeStep(packageName, className), // Assumes this step can find the generated code

            new AskQuestionStep(
                "What operations does the Calculator class provide?",
                Arrays.asList("add", "subtract") // Keywords for answer verification
            ),
            new AskQuestionStep(
                "Can you show me the implementation of the 'add' operation?",
                Arrays.asList("return a + b", "public static int add") // Keywords for answer verification
            ),

            new ShowMessageStep("Contextual Q&A Demo finished. This showed how Jaider can generate code and then answer follow-up questions maintaining context.")
        );
    }

    @Override
    public String getInitialPomContent() {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
               "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
               "    <modelVersion>4.0.0</modelVersion>\n" +
               "    <groupId>com.example</groupId>\n" +
               "    <artifactId>contextual-qa-demo-project</artifactId>\n" +
               "    <version>1.0-SNAPSHOT</version>\n" +
               "    <properties>\n" +
               "        <maven.compiler.source>11</maven.compiler.source>\n" +
               "        <maven.compiler.target>11</maven.compiler.target>\n" +
               "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
               "    </properties>\n" +
               "    <dependencies>\n" +
               "        <!-- No specific dependencies needed for a simple calculator -->\n" +
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
