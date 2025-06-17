# Jaider Demos Overview

The demos in this directory and related packages are designed to showcase the features of Jaider and to serve as an educational resource on how to interact with it effectively. They simulate interactions with Jaider, which in turn would typically interact with a Large LanguageModel (LLM) to perform tasks like code generation, explanation, and modification.

These demos are invaluable for:
- Understanding Jaider's capabilities.
- Learning how to write effective prompts.
- Seeing examples of iterative development workflows with an AI coding assistant.
- Providing a testbed for new Jaider features and improvements.

# Running the Demos

There are several ways to run and experience the demos:

## Individual Demo Execution

### Standalone Demos (like `CalculatorDemo.java`)
Some demos, like `CalculatorDemo.java`, are self-contained Java applications with a `main` method.
- **Direct Execution**: You can run its `main` method directly from your IDE or using `java` if compiled.
  Example:
  ```bash
  # Assuming you have compiled CalculatorDemo.java and are in the correct directory
  # (Actual command might vary based on your project setup and classpath)
  java com.example.math.CalculatorDemo
  ```
- **Via JUnit Test**: `DemoExecutionIntegrationTest.java` includes a test method (`testCalculatorDemo_RunsSuccessfully`) that invokes `CalculatorDemo.main()`. Running this test will execute the demo.

### Orchestrated Demos (via `DemoExecutionIntegrationTest.java`)
Demos like "hello_world", "missile_command", and "contextual_qa_demo" are orchestrated by `dumb.jaider.commands.DemoCommand.java`.
- **JUnit Tests**: These are primarily run as JUnit tests within `DemoExecutionIntegrationTest.java`. These tests use mocked components (like a mock LLM and ProjectManager) to simulate Jaider's behavior without actual LLM calls or extensive file system operations. This allows for fast, repeatable testing of the demo logic and Jaider's command flow.

### Demos with Actual Compilation/Execution (via `DemoRunner`)
The `testDemoRunner_HelloWorld` method in `DemoExecutionIntegrationTest.java` demonstrates the use of `DemoRunner`.
- **JUnit Test with `DemoRunner`**: This type of test uses `DemoRunner` to perform real code generation (writing files), compilation (invoking `javac`), and execution (invoking `java`). It provides a more end-to-end validation of code generation capabilities.

## Understanding Demo Output

When you run a demo, you should typically expect to see:
- **Console Output**:
    - Narration explaining each step of the demo, what Jaider is (simulated to be) doing, and why.
    - Simulated prompts that would be given to Jaider.
    - Generated code snippets or full files.
    - Results of actions like compilation or Q&A.
- **TUI Interaction (for `DemoCommand` based demos, when run in the actual application)**:
    - If these demos were run within the full Jaider TUI application, they would present messages and code in interactive modals. The JUnit tests for these simulate these interactions.

# Available Demos

Here's a summary of the key demos available:

### `CalculatorDemo.java`
-   **Purpose**: Illustrates a step-by-step, narrated development process of a `Calculator` class using a simulated Jaider. It's designed to be highly educational for end-users.
-   **Showcases**:
    *   Initial code generation from a prompt.
    *   Adding new functionality (e.g., `multiply` and `divide` methods, including error handling for division by zero).
    *   Prompting Jaider to generate JUnit unit tests for the `Calculator` class, covering various cases.
    *   Asking Jaider to generate Javadoc comments for the class and its methods.
    *   Embedded advice on best practices for prompting and verifying AI-generated code.
-   **How it works**: Executed by running its `main` method (e.g., via the `testCalculatorDemo_RunsSuccessfully` test in `DemoExecutionIntegrationTest`). It prints a detailed script of simulated interactions and generated code to the console.

### "Hello, World" Demo (via `DemoExecutionIntegrationTest` & `DemoCommand`)
-   **Purpose**: A very basic introduction to Jaider's code generation capability.
-   **Showcases**: Generating a simple "Hello, World!" Java application from a prompt.
-   **How it works**: Defined by `dumb.jaider.demo.demos.HelloWorldDemo.java` and orchestrated by `DemoCommand`. It's tested in `DemoExecutionIntegrationTest` using mocked LLM responses and project interactions. The focus is on demonstrating the fundamental flow of a code generation command.

### "Missile Command" Demo (via `DemoExecutionIntegrationTest` & `DemoCommand`)
-   **Purpose**: Demonstrates a more complex, multi-turn interaction for scaffolding and iteratively enhancing a simple game application.
-   **Showcases**:
    *   Initial project generation for a Swing-based game.
    *   Using Jaider's Q&A feature to understand the generated code.
    *   Requesting Jaider to enhance the existing code with new features (e.g., sound effect placeholders).
    *   Compilation checks after each generation/enhancement step.
-   **How it works**: Defined by `dumb.jaider.demo.demos.MissileCommandDemo.java`, orchestrated by `DemoCommand`, and tested with mocks in `DemoExecutionIntegrationTest`. It highlights iterative development and Jaider's ability to work with existing code.

### "Contextual Q&A" Demo (via `DemoExecutionIntegrationTest` & `DemoCommand`)
-   **Purpose**: Specifically shows Jaider's ability to understand the context of previously generated or existing code to answer questions accurately.
-   **Showcases**:
    *   Generating an initial `Calculator` class.
    *   Asking specific questions about the methods and structure of that generated class.
    *   Jaider providing answers based on the actual code context.
-   **How it works**: Defined by `dumb.jaider.demo.demos.ContextualQADemo.java`, orchestrated by `DemoCommand`, and tested with mocks. Emphasizes Jaider's code comprehension and contextual awareness.

### `DemoRunner` Example (in `DemoExecutionIntegrationTest.testDemoRunner_HelloWorld`)
-   **Purpose**: To demonstrate the `DemoRunner` utility, which allows tests or other applications to perform real code generation, compilation, and execution cycles.
-   **Showcases**:
    *   Programmatic setup of a temporary Java project.
    *   Writing a source file to the project.
    *   Compiling the project using the system's `javac`.
    *   Running the compiled Java application using `java`.
    *   Capturing and asserting the application's output and exit code.
-   **How it works**: This is a JUnit test method within `DemoExecutionIntegrationTest.java` that directly uses `DemoRunner` and the associated `ProjectManager` to build and run a "Hello, World!" application from scratch, providing an end-to-end test of these utilities.

# Demo Infrastructure Components

Several classes work together to make these demos possible:

-   **`dumb.jaider.commands.DemoCommand.java`**: This class is responsible for discovering and launching interactive demos that are structured as sequences of `DemoStep`s (e.g., `HelloWorldDemo`, `MissileCommandDemo`). It's typically invoked in the main application via a command like `/demo <demoname>`.
-   **Demo Sequence Providers (e.g., `dumb.jaider.demo.demos.HelloWorldDemo.java`)**: These classes implement `dumb.jaider.demo.DemoProvider` and define the specific title, description, and sequence of `DemoStep`s for a particular interactive demo. They provide the content and narration for the `DemoCommand`-based demos.
-   **`DemoExecutionIntegrationTest.java`**: This JUnit test class is crucial for the demo ecosystem. It:
    *   Tests the execution flow of demos managed by `DemoCommand` (using mocked LLM and file system interactions).
    *   Provides a way to run standalone, self-narrating demos like `CalculatorDemo.java` by invoking their `main` methods.
    *   Includes tests like `testDemoRunner_HelloWorld` that use `DemoRunner` to validate actual code compilation and execution, providing a deeper level of integration testing.
-   **`DemoRunner.java`**: A utility class located in `src/test/java/dumb/jaider/demo/`. Its primary role is to facilitate demos and tests that require actual compilation and execution of Java code. It manages a temporary project, allows adding source files, and handles the compilation and execution process using `ProjectManager`.
-   **`dumb.jaider.integration.ProjectManager.java`**: This class (created as part of developing `DemoRunner`) is responsible for the low-level management of temporary Java projects for demos. This includes creating the project directory, writing files, invoking the Java compiler (`javac`), running the compiled code (`java`), and cleaning up the project.
-   **`dumb.jaider.demo.InteractiveDemo.java`**: While less central for understanding the *content* of the demos, this class and its associated test (`InteractiveDemoTest.java`) are involved in managing temporary directories for some interactive demo functionalities, ensuring that demos have a clean workspace.

# Creating New Demos (Guidance for Developers)

When developing new demos, consider the following approaches:

### Option 1: Standalone Narrated Demo (like `CalculatorDemo`)
This is best for highly educational demos where a detailed, step-by-step narration is key.
1.  Create a new Java class (e.g., `MyNewFeatureDemo.java`) in `src/test/java/dumb/jaider/demo/` or a suitable sub-package.
2.  Implement a `public static void main(String[] args)` method.
3.  Inside `main`, use `System.out.println()` to:
    *   Narrate the purpose of each step.
    *   Show example prompts you would give to Jaider.
    *   Display the code Jaider (simulated) would generate or modify.
    *   Explain best practices related to the feature being demonstrated.
4.  Add a new test method to `DemoExecutionIntegrationTest.java` that simply calls `MyNewFeatureDemo.main(new String[0])` and perhaps asserts that it runs without exceptions.

### Option 2: Demo Integrated with `DemoCommand`
This is suitable for demos that should be part of the interactive TUI flow of the main Jaider application and follow a sequence of pre-defined steps.
1.  Create a new demo provider class in `src/main/java/dumb/jaider/demo/demos/` (e.g., `MyTUIDemo.java`) implementing `dumb.jaider.demo.DemoProvider`.
2.  Define the demo's name, description, and a list of `DemoStep` objects (e.g., `DisplayMessageStep`, `InitialProjectGenerationStep`, `AskQuestionStep`).
3.  Register your new demo provider in `dumb.jaider.commands.DemoCommand`'s `discoverDemos()` method.
4.  Add a new test method in `DemoExecutionIntegrationTest.java` using the standard mock setup (mocking `OllamaService`, `ProjectManager`, etc.) to test the flow of your new demo sequence.

### Option 3: Demo using `DemoRunner`
This is ideal for integration tests or demos that need to verify that code generated (even if the "generation" is just providing a string in the test) can actually compile and run correctly.
1.  Create a new test method in `DemoExecutionIntegrationTest.java` or a dedicated new test class.
2.  Instantiate `DemoRunner demoRunner = new DemoRunner("MyExecutionDemo");`.
3.  Use `demoRunner.setupProject()` to create a temporary project.
4.  Use `demoRunner.addSourceFile("path/to/MyClass.java", "java code content");` to add your Java files.
5.  Use `demoRunner.compileProject()` and assert it returns `true`.
6.  Use `demoRunner.runProject("fully.qualified.MainClass")` to execute the code.
7.  Assert the `ExecutionResult` (stdout, stderr, exit code) is as expected.
8.  Ensure `demoRunner.cleanupProject()` is called, typically in a `finally` block.

**General Guidance for New Demos**:
-   **Clarity is Key**: The primary goal of a demo is to be educational. Ensure your explanations are clear, concise, and easy to understand.
-   **Show, Don't Just Tell**: Display example prompts, generated code, and expected outputs.
-   **Simulate Real Interactions**: Even if using mocks, the simulated interaction should reflect how a user would realistically use Jaider.
-   **Keep it Focused**: Each demo should ideally focus on a specific feature or a small set of related features.

By following these patterns, you can create effective and informative demos that enhance the Jaider user experience.
