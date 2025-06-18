package com.example.math;

/**
 * This class demonstrates a simulated interaction with Jaider (an AI coding assistant)
 * to build and enhance a simple Calculator class.
 */
public class CalculatorDemo {

    // Inner class to simulate the Calculator.java file content at different stages
    // This is purely for demonstration within this single file.
    // In a real scenario, these would be actual .java files.
    public static class Calculator {
        // Initial version (as if created by a developer or a previous Jaider session)
        public static int add(int a, int b) {
            return a + b;
        }

        public static int subtract(int a, int b) {
            return a - b;
        }

        // Methods to be "added by Jaider" later in the demo
        // These are initially commented out or absent in a real workflow
        public static int multiply(int a, int b) {
            return a * b; // Added in step 1
        }

        public static int divide(int a, int b) { // Added in step 1
            if (b == 0) {
                throw new IllegalArgumentException("Cannot divide by zero.");
            }
            return a / b;
        }
    }

    public static void main(String[] args) {
        System.out.println("Welcome to the Jaider Calculator Demo!");
        System.out.println("This demo simulates how you might interact with Jaider to develop a Java class.");
        System.out.println("This demo focuses on teaching effective strategies for prompting and interacting with an AI coding assistant like Jaider.");
        System.out.println("Note: The 'Jaider' in this demo is simulated to illustrate ideal interactions and outputs. Real AI responses may vary and might require further refinement.");
        System.out.println("--------------------------------------------------------------------------\n");

        showInitialCalculatorState();
        narrateAddMultiplyAndDivide();
        narrateAddUnitTests();
        narrateGenerateDocumentation();

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("Calculator Demo finished. Key takeaways for effective Jaider interaction:");
        System.out.println("1. Be Specific and Clear in Your Prompts: Provide detailed requirements, including context (like existing code), desired features, libraries, and specific behaviors. The more precise your prompt, the better Jaider's output.");
        System.out.println("2. Review and Verify All Generated Code: Always treat AI-generated code as a starting point. Thoroughly review it for correctness, security, performance, and adherence to your project's standards.");
        System.out.println("3. Iterate and Refine: Don't expect perfection on the first try. Use an iterative process. Provide feedback on Jaider's output and ask for modifications or improvements.");
        System.out.println("4. Ask 'How-to' and 'Explain' Questions: Use Jaider not just to generate code, but also to learn. Ask it to explain concepts, how to implement specific patterns, or the reasoning behind its suggestions.");
        System.out.println("5. Guide Jaider on Best Practices: Explicitly ask for tests, documentation, error handling, and adherence to coding conventions.");
        System.out.println("6. Combine AI Assistance with Your Expertise: You are the developer. Use Jaider as a powerful assistant, but apply your judgment and knowledge to guide it and validate its output.");
        System.out.println("--------------------------------------------------------------------------");
    }

    private static void showInitialCalculatorState() {
        System.out.println("STAGE 0: Initial State of Calculator.java");
        System.out.println("Let's assume we have a basic Calculator.java file with add and subtract methods:\n");
        System.out.println("```java");
        System.out.println("package com.example.math;");
        System.out.println("");
        System.out.println("public class Calculator {");
        System.out.println("    public static int add(int a, int b) {");
        System.out.println("        return a + b;");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    public static int subtract(int a, int b) {");
        System.out.println("        return a - b;");
        System.out.println("    }");
        System.out.println("}");
        System.out.println("```\n");

        // Demonstrate current functionality
        System.out.println("Current functionality:");
        System.out.println("Calculator.add(10, 5) = " + Calculator.add(10, 5));
        System.out.println("Calculator.subtract(10, 5) = " + Calculator.subtract(10, 5));
        System.out.println("\n--------------------------------------------------------------------------\n");
    }

    private static void narrateAddMultiplyAndDivide() {
        System.out.println("STAGE 1: Adding Multiply and Divide Methods");
        System.out.println("Now, we want to ask Jaider to add `multiply` and `divide` methods.");
        System.out.println("Our prompt needs to be clear about method visibility, return types, names, parameters, and specific behaviors like error handling.");
        // The Best Practice tip is enhanced below
        System.out.println("\nPrompt to Jaider:");
        System.out.println("\"Please add two public static methods to the `com.example.math.Calculator` class:");
        System.out.println("1. `multiply(int a, int b)` which returns the product of a and b.");
        System.out.println("2. `divide(int a, int b)` which returns the result of a divided by b.");
        System.out.println("   Ensure that the `divide` method throws an `IllegalArgumentException` if b is zero, with the message 'Cannot divide by zero.'\"");
        System.out.println("\nJaider processes this and (simulated) updates `Calculator.java` as follows:\n");

        // Display generated code
        System.out.println("```java");
        System.out.println("package com.example.math;");
        System.out.println("");
        System.out.println("public class Calculator {");
        System.out.println("    public static int add(int a, int b) {");
        System.out.println("        return a + b;");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    public static int subtract(int a, int b) {");
        System.out.println("        return a - b;");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    // Added by Jaider");
        System.out.println("    public static int multiply(int a, int b) {");
        System.out.println("        return a * b;");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    // Added by Jaider");
        System.out.println("    public static int divide(int a, int b) {");
        System.out.println("        if (b == 0) {");
        System.out.println("            throw new IllegalArgumentException(\"Cannot divide by zero.\");");
        System.out.println("        }");
        System.out.println("        return a / b;");
        System.out.println("    }");
        System.out.println("}");
        System.out.println("```\n");

        System.out.println("Best Practice: Be specific in your prompt. Mention requirements like method signatures, visibility, return types, and crucial behaviors (e.g., error handling for division by zero). The more detail you provide, the better Jaider can meet your requirements.");
        System.out.println("If Jaider missed a requirement (e.g., if it didn't add the exception for division by zero), you would provide feedback and ask for a correction, like: 'You missed adding the `IllegalArgumentException` for division by zero in the `divide` method. Please add it.'");

        System.out.println("\nLet's test the new methods (using the methods from our inner Calculator class for this demo):");
        System.out.println("Calculator.multiply(10, 5) = " + Calculator.multiply(10, 5));
        System.out.println("Calculator.divide(10, 2) = " + Calculator.divide(10, 2)); // Corrected to avoid self-division error
        try {
            System.out.print("Calculator.divide(10, 0) = ");
            Calculator.divide(10, 0);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
        System.out.println("\n--------------------------------------------------------------------------\n");
    }

    private static void narrateAddUnitTests() {
        System.out.println("STAGE 2: Generating Unit Tests");
        System.out.println("Next, we'll ask Jaider to generate unit tests for the `Calculator` class.");
        System.out.println("We'll assume we're using JUnit 5.");
        System.out.println("When asking for tests, it's important to specify the testing framework and the scope of tests (e.g., specific methods, edge cases).");
        // The Best Practice tip is enhanced below
        System.out.println("\nPrompt to Jaider:");
        System.out.println("\"Please generate JUnit 5 tests for the `com.example.math.Calculator` class.");
        System.out.println("Create a test class named `CalculatorTest` in the same package.");
        System.out.println("Include tests for all methods: `add`, `subtract`, `multiply`, and `divide`.");
        System.out.println("Ensure to test edge cases, especially for `divide`, including division by zero and division of zero.\"");
        System.out.println("\nJaider (simulated) generates `CalculatorTest.java`:\n");

        // Display generated test code
        System.out.println("```java");
        System.out.println("package com.example.math;");
        System.out.println("");
        System.out.println("import org.junit.jupiter.api.Test;");
        System.out.println("import static org.junit.jupiter.api.Assertions.*;");
        System.out.println("");
        System.out.println("class CalculatorTest {");
        System.out.println("");
        System.out.println("    @Test");
        System.out.println("    void testAdd() {");
        System.out.println("        assertEquals(5, Calculator.add(2, 3));");
        System.out.println("        assertEquals(-1, Calculator.add(2, -3));");
        System.out.println("        assertEquals(0, Calculator.add(0, 0));");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    @Test");
        System.out.println("    void testSubtract() {");
        System.out.println("        assertEquals(-1, Calculator.subtract(2, 3));");
        System.out.println("        assertEquals(5, Calculator.subtract(2, -3));");
        System.out.println("        assertEquals(0, Calculator.subtract(0, 0));");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    @Test");
        System.out.println("    void testMultiply() {");
        System.out.println("        assertEquals(6, Calculator.multiply(2, 3));");
        System.out.println("        assertEquals(-6, Calculator.multiply(2, -3));");
        System.out.println("        assertEquals(0, Calculator.multiply(2, 0));");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    @Test");
        System.out.println("    void testDivide() {");
        System.out.println("        assertEquals(2, Calculator.divide(6, 3));");
        System.out.println("        assertEquals(-2, Calculator.divide(6, -3));");
        System.out.println("        assertEquals(0, Calculator.divide(0, 5));");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    @Test");
        System.out.println("    void testDivideByZero() {");
        System.out.println("        Exception exception = assertThrows(IllegalArgumentException.class, () -> {");
        System.out.println("            Calculator.divide(5, 0);");
        System.out.println("        });");
        System.out.println("        assertEquals(\"Cannot divide by zero.\", exception.getMessage());");
        System.out.println("    }");
        System.out.println("}");
        System.out.println("```\n");
        System.out.println("Best Practice: Specify the testing framework (e.g., JUnit 5, TestNG). Clearly list the methods or classes to test and any particular scenarios or edge cases you want covered (like division by zero, null inputs, empty strings, etc.).");
        System.out.println("(In a real scenario, you would compile and run these tests using your build tool and JUnit.)");
        System.out.println("\n--------------------------------------------------------------------------\n");
    }

    private static void narrateGenerateDocumentation() {
        System.out.println("STAGE 3: Generating Javadoc Documentation");
        System.out.println("Finally, let's ask Jaider to add Javadoc comments to our `Calculator` class.");
        System.out.println("For documentation, specify the scope (class, methods), the elements to document (parameters, returns, exceptions), and any specific formatting or conventions (like a private constructor for utility classes).");
        // The Best Practice tip is enhanced below
        System.out.println("\nPrompt to Jaider:");
        System.out.println("\"Please add Javadoc comments to the `com.example.math.Calculator` class and all its public methods.");
        System.out.println("Include descriptions for the class, each method, its parameters, and return values.");
        System.out.println("Also, add a private constructor to prevent instantiation since it's a utility class.\"");
        System.out.println("\nJaider (simulated) updates `Calculator.java` with Javadoc comments:\n");

        // Display Javadoc-enhanced code
        System.out.println("```java");
        System.out.println("package com.example.math;");
        System.out.println("");
        System.out.println("/**");
        System.out.println(" * Provides basic arithmetic operations.");
        System.out.println(" * This class demonstrates how Jaider can be used to generate and enhance code.");
        System.out.println(" */");
        System.out.println("public class Calculator {");
        System.out.println("");
        System.out.println("    /**");
        System.out.println("     * Private constructor to prevent instantiation.");
        System.out.println("     */");
        System.out.println("    private Calculator() {");
        System.out.println("        // Utility class");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    /**");
        System.out.println("     * Adds two integers.");
        System.out.println("     *");
        System.out.println("     * @param a the first integer");
        System.out.println("     * @param b the second integer");
        System.out.println("     * @return the sum of a and b");
        System.out.println("     */");
        System.out.println("    public static int add(int a, int b) {");
        System.out.println("        return a + b;");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    /**");
        System.out.println("     * Subtracts the second integer from the first.");
        System.out.println("     *");
        System.out.println("     * @param a the first integer");
        System.out.println("     * @param b the second integer");
        System.out.println("     * @return the result of subtracting b from a");
        System.out.println("     */");
        System.out.println("    public static int subtract(int a, int b) {");
        System.out.println("        return a - b;");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    /**");
        System.out.println("     * Multiplies two integers.");
        System.out.println("     *");
        System.out.println("     * @param a the first integer");
        System.out.println("     * @param b the second integer");
        System.out.println("     * @return the product of a and b");
        System.out.println("     */");
        System.out.println("    public static int multiply(int a, int b) {");
        System.out.println("        return a * b;");
        System.out.println("    }");
        System.out.println("");
        System.out.println("    /**");
        System.out.println("     * Divides the first integer by the second.");
        System.out.println("     *");
        System.out.println("     * @param a the dividend");
        System.out.println("     * @param b the divisor");
        System.out.println("     * @return the result of dividing a by b");
        System.out.println("     * @throws IllegalArgumentException if the divisor b is zero");
        System.out.println("     */");
        System.out.println("    public static int divide(int a, int b) {");
        System.out.println("        if (b == 0) {");
        System.out.println("            throw new IllegalArgumentException(\"Cannot divide by zero.\");");
        System.out.println("        }");
        System.out.println("        return a / b;");
        System.out.println("    }");
        System.out.println("}");
        System.out.println("```\n");
        System.out.println("Best Practice: Request documentation for all relevant public elements. Specify if you need class-level comments, method descriptions, and details for parameters, return values, and thrown exceptions. Mentioning coding conventions (like a private constructor for utility classes) is also helpful.");
        System.out.println("You can then generate HTML documentation using the Javadoc tool.");
    }
}
