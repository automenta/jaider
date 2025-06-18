package dumb.jaider.demo;

import java.util.List;
import java.util.ArrayList; // For legacy code example

public class LegacyRefactoringDemo {

    // Simple User POJO/Record for context within the legacy code
    // In a real scenario, this would be a separate file.
    private static final String USER_CLASS_DEFINITION = """
        class User {
            private final int id;
            private final String name;
            private final String email;

            public User(int id, String name, String email) {
                this.id = id;
                this.name = name;
                this.email = email;
            }

            public int getId() { return id; }
            public String getName() { return name; }
            public String getEmail() { return email; }

            @Override
            public String toString() {
                return "User{id=" + id + ", name='" + name + "', email='" + email + "'}";
            }
        }
    """;

    private static final String LEGACY_USER_PROCESSOR_CODE = USER_CLASS_DEFINITION + """

    class LegacyUserProcessor {
        public List<User> processUsers(List<String> rawUserData) {
            List<User> validUsers = new ArrayList<>();
            if (rawUserData == null) {
                return validUsers; // Return empty list if input is null
            }

            for (String userData : rawUserData) {
                if (userData == null || userData.isEmpty()) {
                    continue; // Skip null or empty strings
                }

                String[] parts = userData.split(",");
                if (parts.length != 3) {
                    System.out.println("Skipping invalid data (wrong number of parts): " + userData);
                    continue; // Expecting id,name,email
                }

                int id = -1;
                try {
                    id = Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException e) {
                    System.out.println("Skipping invalid data (ID not a number): " + userData);
                    continue;
                }

                String name = parts[1].trim();
                String email = parts[2].trim();

                if (name.isEmpty()) {
                    System.out.println("Skipping invalid data (name is empty): " + userData);
                    continue;
                }

                if (email.isEmpty() || !email.contains("@")) {
                    System.out.println("Skipping invalid data (email invalid): " + userData);
                    continue;
                }

                // Simulate some additional complex validation or transformation
                if (name.equalsIgnoreCase("admin")) {
                    System.out.println("Skipping admin user as per legacy rule: " + userData);
                    continue;
                }

                User user = new User(id, name, email);
                validUsers.add(user);
            }
            return validUsers;
        }
    }
    """;

    public static void main(String[] args) {
        System.out.println("Welcome to the Legacy Code Refactoring Demo!");
        System.out.println("This demo will walk through modernizing a piece of legacy Java code with Jaider's help.");

        showLegacyCode();
        narrateModernizationToStreamsAndLambdas();
        narrateIntroducingOptional();
        narrateFurtherImprovements(); // e.g., externalizing User class, error handling strategies
        summarizeRefactoringLearnings();

        System.out.println("\nDemo finished.");
    }

    private static void showLegacyCode() {
        System.out.println("\n--- STAGE 0: The Legacy Code ---");
        System.out.println("Here's the 'LegacyUserProcessor' code we'll be asking Jaider to refactor:");
        System.out.println("--------------------------------------------------------------------------------");
        // We'll print the LEGACY_USER_PROCESSOR_CODE here in the actual implementation
        System.out.println(LEGACY_USER_PROCESSOR_CODE);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Key characteristics: manual string parsing, for-loops, null/empty checks, mutable list.");
    }

    private static void narrateModernizationToStreamsAndLambdas() {
        System.out.println("\n--- STAGE 1: Modernizing with Streams and Lambdas ---");
        System.out.println("Goal: Ask Jaider to refactor `processUsers` to use Java Streams and Lambdas for improved clarity and conciseness, while preserving the original logic.");

        System.out.println("\nPrompt to Jaider:");
        System.out.println("================================================================================");
        System.out.println("Please refactor the `processUsers` method within the `LegacyUserProcessor` class (shown in STAGE 0) " +
                "to utilize modern Java features. Specifically, I want you to replace the traditional for-loops and " +
                "manual conditional logic with Java Streams and lambda expressions. The goal is to make the data " +
                "processing pipeline more concise and readable while preserving the original logic: parsing " +
                "comma-separated user data strings, filtering out invalid entries (e.g., incorrect number of parts, " +
                "invalid ID, missing name or email, or admin users), and mapping valid data to `User` objects. " +
                "The `User` class structure is also provided in the context.");
        System.out.println("================================================================================");

        System.out.println("\nJaider analyzes the code and (simulated) suggests the following refactoring using Streams and Lambdas:");
        System.out.println("--------------------------------------------------------------------------------");
        // Note: The User class definition is assumed to be part of the context provided to Jaider,
        // as it was in LEGACY_USER_PROCESSOR_CODE. For this print, we only show the refactored class.
        System.out.println("""
                // Assuming User class is defined as shown in STAGE 0's LEGACY_USER_PROCESSOR_CODE
                // import java.util.Collections;
                // import java.util.List;
                // import java.util.stream.Collectors;

                class RefactoredUserProcessor { // Renamed for clarity in demo
                    public List<User> processUsers(List<String> rawUserData) {
                        if (rawUserData == null) {
                            return java.util.Collections.emptyList();
                        }
                        return rawUserData.stream() // Convert the list to a stream
                            .filter(line -> line != null && !line.trim().isEmpty()) // Filter out null or effectively empty lines
                            .map(line -> { // Map each valid line to a potential User object or null
                                String[] parts = line.split(",");
                                if (parts.length != 3) {
                                    System.out.println("Skipping invalid data (wrong number of parts): " + line);
                                    return null; // Invalid format
                                }
                                try {
                                    int id = Integer.parseInt(parts[0].trim());
                                    String name = parts[1].trim();
                                    String email = parts[2].trim();

                                    // Validate name and email (basic checks)
                                    if (name.isEmpty() || email.isEmpty() || !email.contains("@")) {
                                        System.out.println("Skipping invalid data (name/email issue): " + line);
                                        return null;
                                    }

                                    // Filter out admin users as per legacy rule
                                    if (name.equalsIgnoreCase("admin")) {
                                         System.out.println("Skipping admin user as per legacy rule: " + line);
                                         return null;
                                    }

                                    return new User(id, name, email); // Create User object
                                } catch (NumberFormatException e) {
                                    System.out.println("Skipping invalid data (ID not a number): " + line);
                                    return null; // ID parsing failed
                                }
                            })
                            .filter(user -> user != null) // Filter out any nulls that resulted from failed mapping/validation
                            .collect(java.util.stream.Collectors.toList()); // Collect valid User objects into a new list
                    }

                    // Inner User class for context (as provided in the legacy code)
                    static class User {
                        private final int id;
                        private final String name;
                        private final String email;

                        public User(int id, String name, String email) {
                            this.id = id;
                            this.name = name;
                            this.email = email;
                        }
                        public int getId() { return id; }
                        public String getName() { return name; }
                        public String getEmail() { return email; }
                        @Override
                        public String toString() {
                            return "User{id=" + id + ", name='" + name + "', email='" + email + "'}";
                        }
                    }
                }
                """);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Explanation of Changes:");
        System.out.println("  - `.stream()`: Converts the list of raw user data strings into a stream for processing.");
        System.out.println("  - `.filter(line -> ...)`: Used multiple times. First to remove null/empty lines. Later to remove `null` User objects that failed validation/mapping.");
        System.out.println("  - `.map(line -> ...)`: Transforms each valid line. The lambda expression contains the logic to split the string, parse parts, validate, and create a `User` object. If any step fails, it returns `null`.");
        System.out.println("    - Inside the map: String splitting, `Integer.parseInt`, and checks for name/email validity are performed.");
        System.out.println("    - The try-catch block for `NumberFormatException` is preserved within the lambda.");
        System.out.println("  - `.collect(Collectors.toList())`: Gathers the successfully created `User` objects into a new list.");
        System.out.println("This approach chains operations, making the code more declarative and often easier to follow once familiar with stream API.");

        System.out.println("\n✨ Best Practice: When requesting refactoring, clearly state your goals (e.g., 'use streams', 'improve readability', 'modernize to Java 17 features'). Provide the context of the class and any relevant data structures (like the `User` class here). Be prepared to iterate if the first result isn't perfect; you might need to clarify specific filtering conditions or error handling details.");
    }

    private static void narrateIntroducingOptional() {
        System.out.println("\n--- STAGE 2: Improving Null Safety with java.util.Optional ---");
        System.out.println("Goal: Ask Jaider to refactor the stream-based `processUsers` method to use `Optional<User>` " +
                "for the part of the pipeline that parses and validates user data lines, enhancing null safety and clarity.");

        System.out.println("\nPrompt to Jaider:");
        System.out.println("================================================================================");
        System.out.println("Let's refine the stream-based `processUsers` method (from STAGE 1). Instead of returning `null` " +
                "from the mapping function when a user data string is invalid, can you modify it to return `java.util.Optional<User>`? " +
                "Show how the rest of the stream pipeline would then process these `Optional`s to achieve the same result " +
                "of collecting only valid `User` objects. This should make the handling of potentially absent users more explicit.");
        System.out.println("================================================================================");

        System.out.println("\nJaider suggests the following modification to use `Optional`:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("""
                // Assuming User class is defined as shown in STAGE 0's LEGACY_USER_PROCESSOR_CODE
                // import java.util.Collections;
                // import java.util.List;
                // import java.util.Optional;
                // import java.util.stream.Collectors;

                class RefactoredUserProcessorWithOptional { // Renamed for clarity
                    public List<User> processUsers(List<String> rawUserData) {
                        if (rawUserData == null) {
                            return java.util.Collections.emptyList();
                        }
                        return rawUserData.stream()
                            .filter(line -> line != null && !line.trim().isEmpty())
                            .map(line -> { // This map operation now returns Optional<User>
                                String[] parts = line.split(",");
                                if (parts.length != 3) {
                                    System.out.println("Skipping invalid data (wrong number of parts): " + line);
                                    return java.util.Optional.<User>empty(); // Invalid format
                                }
                                try {
                                    int id = Integer.parseInt(parts[0].trim());
                                    String name = parts[1].trim();
                                    String email = parts[2].trim();

                                    // Validate name and email
                                    if (name.isEmpty() || email.isEmpty() || !email.contains("@")) {
                                        System.out.println("Skipping invalid data (name/email issue): " + line);
                                        return java.util.Optional.<User>empty();
                                    }

                                    // Filter out admin users as per legacy rule
                                    if (name.equalsIgnoreCase("admin")) {
                                         System.out.println("Skipping admin user as per legacy rule: " + line);
                                         return java.util.Optional.<User>empty();
                                    }

                                    return java.util.Optional.of(new User(id, name, email)); // Valid user
                                } catch (NumberFormatException e) {
                                    System.out.println("Skipping invalid data (ID not a number): " + line);
                                    return java.util.Optional.<User>empty(); // ID parsing failed
                                }
                            })
                            // To process Stream<Optional<User>>:
                            // Option 1: Java 9+ .flatMap(Optional::stream)
                            // .flatMap(java.util.Optional::stream)
                            // Option 2: Java 8 compatible .filter(Optional::isPresent).map(Optional::get)
                            .filter(java.util.Optional::isPresent)
                            .map(java.util.Optional::get)
                            .collect(java.util.stream.Collectors.toList());
                    }

                    // Inner User class for context (as provided in the legacy code)
                    static class User {
                        private final int id;
                        private final String name;
                        private final String email;

                        public User(int id, String name, String email) {
                            this.id = id;
                            this.name = name;
                            this.email = email;
                        }
                        public int getId() { return id; }
                        public String getName() { return name; }
                        public String getEmail() { return email; }
                        @Override
                        public String toString() {
                            return "User{id=" + id + ", name='" + name + "', email='" + email + "'}";
                        }
                    }
                }
                """);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Explanation of Changes:");
        System.out.println("  - The lambda expression inside `.map(line -> ...)` now returns `Optional<User>`.");
        System.out.println("    - `Optional.of(new User(...))` is used when a `User` is successfully created.");
        System.out.println("    - `Optional.empty()` is returned if the data is invalid, parsing fails, or if the user is 'admin'.");
        System.out.println("  - To convert the `Stream<Optional<User>>` back to a `Stream<User>` (containing only present values):");
        System.out.println("    - For Java 8 compatibility: `.filter(Optional::isPresent)` keeps only non-empty Optionals, and then `.map(Optional::get)` extracts the `User` objects from them.");
        System.out.println("    - For Java 9+: `.flatMap(Optional::stream)` is a more concise way to achieve the same, as `Optional.stream()` returns a stream of one element if present, or an empty stream if not.");
        System.out.println("This makes the potential absence of a `User` object at the mapping stage explicit, improving code clarity and null safety.");

        System.out.println("\n✨ Best Practice: For functions or stream operations that might not produce a value (e.g., parsing, lookups), ask Jaider to use `Optional` to clearly signal this possibility. This helps prevent `NullPointerExceptions` and makes code more robust and expressive about potentially missing values.");
    }

    private static void narrateFurtherImprovements() {
        System.out.println("\n--- STAGE 3: Further Improvements - Extracting Logic to Helper Methods ---");
        System.out.println("Goal: Complex lambda expressions within streams can harm readability. We'll ask Jaider to extract the user parsing and validation logic into a private helper method.");

        System.out.println("\nPrompt to Jaider:");
        System.out.println("================================================================================");
        System.out.println("The lambda expression used in the `.map()` stage of the `processUsers` method " +
                "(in `RefactoredUserProcessorWithOptional` from STAGE 2) is becoming a bit complex. " +
                "Can you refactor it by extracting the logic for parsing a single raw data line and " +
                "converting it into an `Optional<User>` into a private static helper method within the " +
                "`RefactoredUserProcessorWithOptional` class? The main `processUsers` method should then " +
                "call this helper method using a method reference.");
        System.out.println("================================================================================");

        System.out.println("\nJaider suggests extracting the mapping logic into a helper method for clarity:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("""
                // Assuming User class is defined as shown in STAGE 0's LEGACY_USER_PROCESSOR_CODE
                // import java.util.Collections;
                // import java.util.List;
                // import java.util.Optional;
                // import java.util.stream.Collectors;

                class RefactoredUserProcessorWithHelper { // Renamed for clarity

                    // Inner User class for context (as provided in the legacy code)
                    // In a real scenario, this would likely be a top-level class.
                    static class User {
                        private final int id;
                        private final String name;
                        private final String email;

                        public User(int id, String name, String email) {
                            this.id = id;
                            this.name = name;
                            this.email = email;
                        }
                        public int getId() { return id; }
                        public String getName() { return name; }
                        public String getEmail() { return email; }
                        @Override
                        public String toString() {
                            return "User{id=" + id + ", name='" + name + "', email='" + email + "'}";
                        }
                    }

                    private static Optional<User> parseUserLine(String line) {
                        // The initial filter in processUsers handles null/empty lines,
                        // but a defensive check here is also fine.
                        if (line == null || line.trim().isEmpty()) {
                             return Optional.empty();
                        }

                        String[] parts = line.split(",");
                        if (parts.length != 3) {
                            System.out.println("Skipping invalid data (wrong number of parts): " + line);
                            return Optional.empty(); // Invalid format
                        }
                        try {
                            int id = Integer.parseInt(parts[0].trim());
                            String name = parts[1].trim();
                            String email = parts[2].trim();

                            // Validate name and email
                            if (name.isEmpty() || email.isEmpty() || !email.contains("@")) {
                                System.out.println("Skipping invalid data (name/email issue): " + line);
                                return Optional.empty();
                            }

                            // Filter out admin users as per legacy rule
                            if (name.equalsIgnoreCase("admin")) {
                                 System.out.println("Skipping admin user as per legacy rule: " + line);
                                 return Optional.empty();
                            }

                            return Optional.of(new User(id, name, email)); // Valid user
                        } catch (NumberFormatException e) {
                            System.out.println("Skipping invalid data (ID not a number): " + line);
                            // Consider logging e.getMessage() or the full exception for debug purposes
                            return Optional.empty(); // ID parsing failed
                        }
                    }

                    public List<User> processUsers(List<String> rawUserData) {
                        if (rawUserData == null) {
                            return java.util.Collections.emptyList();
                        }
                        return rawUserData.stream()
                            .filter(line -> line != null && !line.trim().isEmpty()) // Initial filter for raw input
                            .map(RefactoredUserProcessorWithHelper::parseUserLine) // Use method reference
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(java.util.stream.Collectors.toList());
                    }
                }
                """);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Explanation of Changes:");
        System.out.println("  - The core parsing and validation logic for a single line is now encapsulated in the `private static Optional<User> parseUserLine(String line)` method.");
        System.out.println("  - The `processUsers` method's `.map()` operation is now much cleaner: `.map(RefactoredUserProcessorWithHelper::parseUserLine)`.");
        System.out.println("    This method reference clearly communicates the intent of mapping each line using the `parseUserLine` function.");
        System.out.println("  - Benefits: ");
        System.out.println("    - Readability: The main stream pipeline in `processUsers` is shorter and easier to understand at a glance.");
        System.out.println("    - Maintainability: The parsing logic is centralized in one place, making it easier to modify or debug.");
        System.out.println("    - Testability: The `parseUserLine` method can be unit tested independently with various string inputs.");
        System.out.println("  - The `User` class is shown as a static inner class here for consistency with previous stages, though in a real application, it would typically be a top-level class.");

        System.out.println("\n✨ Best Practice: If lambda expressions in your streams become too long or complex (e.g., more than a few lines or with multiple levels of nesting), ask Jaider to extract them into well-named private (often static) helper methods. This significantly improves code readability, maintainability, and testability. Using method references (like `ClassName::methodName`) makes the stream pipeline very expressive.");
        System.out.println("\nOther points that could be discussed with Jaider for further improvement (but not shown in this step):");
        System.out.println("  - Externalizing the `User` class into its own `.java` file (standard practice).");
        System.out.println("  - Implementing more sophisticated error reporting (e.g., collecting errors instead of just printing to console).");
        System.out.println("  - For very complex CSV, using a dedicated CSV parsing library (like Apache Commons CSV or OpenCSV) instead of `String.split()` for robustness.");
    }

    private static void summarizeRefactoringLearnings() {
        System.out.println("\n--- STAGE 4: Key Learnings & Jaider Refactoring Summary ---");
        System.out.println("This demo showcased how Jaider can assist in iteratively refactoring legacy Java code.");
        System.out.println("Key strategies for effectively using Jaider for refactoring include:");
        System.out.println();
        System.out.println("1.  Present Code Clearly:");
        System.out.println("    - Always provide Jaider with the exact code snippet you want to refactor (as shown in STAGE 0). Context is crucial.");
        System.out.println();
        System.out.println("2.  Specify Clear Refactoring Goals:");
        System.out.println("    - Be explicit about what you want to achieve with each refactoring step. Examples from the demo:");
        System.out.println("        - STAGE 1: \"Replace for-loops and manual conditional logic with Java Streams and lambda expressions.\"");
        System.out.println("        - STAGE 2: \"Modify the mapping function to return java.util.Optional<User>... show how to process these Optionals.\"");
        System.out.println("        - STAGE 3: \"Extract the logic for parsing... into a private static helper method... call this helper method.\"");
        System.out.println("    - Vague goals like \"make it better\" are less effective.");
        System.out.println();
        System.out.println("3.  Embrace an Iterative Approach:");
        System.out.println("    - Refactoring complex code is often best done in stages, not all at once.");
        System.out.println("    - We started with streams (STAGE 1), then introduced `Optional` (STAGE 2), and finally extracted a helper method (STAGE 3).");
        System.out.println("    - This makes the changes more manageable and easier to verify at each step.");
        System.out.println();
        System.out.println("4.  Understand the 'Why' - Seek Explanations:");
        System.out.println("    - If Jaider suggests a change you don't fully understand, ask for an explanation of its benefits (e.g., improved readability, null safety, maintainability, testability).");
        System.out.println("    - Our demo narration included these explanations at each stage, which you can also request from Jaider.");
        System.out.println();
        System.out.println("5.  Critical Verification and Testing:");
        System.out.println("    - This is paramount. While Jaider can automate much of the refactoring, it might not understand all business nuances or edge cases.");
        System.out.println("    - Always thoroughly review the refactored code. More importantly, have a robust suite of unit tests to ensure the refactored code behaves identically to the original (or as intended if behavior change is part of the refactor).");
        System.out.println("    - *Never assume AI-generated refactorings are perfect without testing.*");
        System.out.println();
        System.out.println("6.  Leverage Jaider to Learn Modern Idioms:");
        System.out.println("    - Jaider is a great tool for seeing how to apply modern Java features (like Streams, `Optional`, method references) to older code patterns.");
        System.out.println("    - Use it as a learning aid to modernize your own coding style.");
        System.out.println();
        System.out.println("7.  Focus on Readability and Maintainability:");
        System.out.println("    - The primary goals of refactoring are often to make code easier to understand, modify, and debug.");
        System.out.println("    - Guide Jaider towards changes that improve these aspects, not just use new features for their own sake (e.g., extracting the helper method in STAGE 3 improved readability).");
        System.out.println();
        System.out.println("8.  Combine AI Suggestions with Developer Judgment:");
        System.out.println("    - Jaider provides suggestions and automates transformations. You, the developer, provide the domain knowledge, architectural understanding, and final verification.");
        System.out.println("    - It's a collaborative process. You might accept, reject, or ask for modifications to Jaider's suggestions.");
        System.out.println();
        System.out.println("By applying these strategies, Jaider becomes a powerful ally in the often challenging task of refactoring and modernizing legacy code, helping you improve code quality and your own skills.");
    }
}
