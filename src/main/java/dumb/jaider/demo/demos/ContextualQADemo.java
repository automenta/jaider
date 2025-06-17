package dumb.jaider.demo.demos;

import dumb.jaider.demo.DemoProvider;
import dumb.jaider.demo.DemoStep;
import dumb.jaider.demo.steps.common.AskQuestionStep;
import dumb.jaider.demo.steps.common.ExplainCodeStep;
import dumb.jaider.demo.steps.common.InitialProjectGenerationStep;
import dumb.jaider.demo.steps.common.DisplayMessageStep; // Changed from ShowMessageStep for consistency

import java.util.ArrayList;
import java.util.List;

public class ContextualQADemo implements DemoProvider {

    @Override
    public String getName() {
        return "contextual_qa_demo";
    }

    @Override
    public String getDescription() {
        return "Generates a simple Calculator class and then answers questions about it, demonstrating contextual understanding.";
    }

    @Override
    public List<DemoStep> getSteps() {
        List<DemoStep> steps = new ArrayList<>();

        String initialPrompt = "Generate a simple Java class named 'Calculator' in package 'com.example.math' " +
                               "with two public static methods: 'add(int a, int b)' that returns their sum, " +
                               "and 'subtract(int a, int b)' that returns their difference. The class should not have a public constructor, " +
                               "and ensure Javadoc comments are added to the class and its methods.";

        String packageName = "com.example.math";
        String className = "Calculator";
        // Keywords for InitialProjectGenerationStep verification
        List<String> projectKeywords = List.of("Calculator", "add", "subtract", "static", "int a", "int b", "return a + b;", "return a - b;");


        steps.add(new DisplayMessageStep("Contextual Q&A Demo - Introduction",
                "Welcome to the Contextual Q&A Demo!\n\n" +
                "This demo showcases Jaider's ability to:\n" +
                "1. Generate code based on a specific prompt (a simple Calculator class).\n" +
                "2. Understand the generated code to answer your questions about it accurately.\n" +
                "3. Explain parts of the generated code.\n\n" +
                "This highlights how Jaider maintains context for follow-up interactions, which is crucial for effective AI-assisted development.\n" +
                "Press OK to begin."));

        steps.add(new InitialProjectGenerationStep(
                initialPrompt,
                packageName,
                className,
                getInitialPomContent(),
                projectKeywords
        ));

        steps.add(new DisplayMessageStep("Code Generation Complete",
                "Jaider has generated the 'Calculator.java' class based on the prompt.\n\n" +
                "What happened:\n" +
                "- The `CodeGenerationWorkflow` used the LLM to create the Java code for the 'Calculator' class in the 'com.example.math' package.\n" +
                "- The class includes 'add' and 'subtract' methods as requested, along with Javadoc comments.\n" +
                "- A 'pom.xml' was also set up for potential future compilation or dependency management (though not strictly needed for this simple class).\n\n" +
                "Next, Jaider will explain the generated code."));

        // ExplainCodeStep is expected to provide its own UI interaction to show the code and an explanation.
        // So, a message before it might be redundant if ExplainCodeStep itself has a good intro.
        // However, adding a brief educational intro to ExplainCodeStep's purpose.
        steps.add(new DisplayMessageStep("Code Explanation",
                "Jaider will now provide an explanation of the generated 'Calculator.java' code.\n\n" +
                "The `ExplainCodeStep` sends the code to the LLM and asks for a summary. This can be helpful for quickly understanding new or complex code segments.\n" +
                "Press OK to see the explanation."));
        steps.add(new ExplainCodeStep(packageName, className)); // Assumes this step can find the generated code

        steps.add(new DisplayMessageStep("Contextual Question Answering",
                "Now, let's ask Jaider some questions about the 'Calculator' class it just generated and explained.\n\n" +
                "This demonstrates Jaider's ability to use the previously generated code as context for answering your questions. " +
                "Notice how the questions are specific to the 'Calculator' class.\n" +
                "Jaider's `AskQuestionStep` sends the question along with the relevant code context ('CURRENT_PROJECT_FILES') to the LLM."));

        steps.add(new AskQuestionStep(
                "What are the names of the public static methods available in the Calculator class?",
                // Keywords for answer verification. The LLM should list the methods.
                List.of("add", "subtract")
        ));

        steps.add(new AskQuestionStep(
                "Show me the exact code for the 'subtract' method in the Calculator class.",
                // Keywords to look for in the LLM's answer, expecting a code snippet.
                List.of("public static int subtract(int a, int b)", "return a - b;")
        ));

        steps.add(new AskQuestionStep(
                "Does the Calculator class have a public constructor, and why or why not, based on the initial prompt?",
                // Keywords for answer verification.
                List.of("no public constructor", "prevent instantiation", "utility class")
        ));

        steps.add(new DisplayMessageStep("Demo Complete - Contextual Q&A",
                "The Contextual Q&A Demo is now complete.\n\n" +
                "Key takeaways:\n" +
                "- Jaider can generate code and then use that code as context for subsequent interactions like Q&A and explanations.\n" +
                "- This contextual understanding is vital for a smooth and intelligent development workflow.\n" +
                "- Clear prompts for both generation and questions lead to better results from the LLM.\n\n" +
                "Press OK to exit."));

        return steps;
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
