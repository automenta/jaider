package dumb.jaider.demo;

public class RestApiClientDemo {

    private static final String SAMPLE_OPENAPI_SPEC = """
            openapi: 3.0.0
            info:
              title: Todo API
              version: v1
            paths:
              /todos:
                get:
                  summary: List all todos
                  responses:
                    '200':
                      description: A list of todos
                      content:
                        application/json:
                          schema:
                            type: array
                            items:
                              $ref: '#/components/schemas/Todo'
                post:
                  summary: Create a new todo
                  requestBody:
                    required: true
                    content:
                      application/json:
                        schema:
                          $ref: '#/components/schemas/Todo'
                  responses:
                    '201':
                      description: Todo created
              /todos/{id}:
                get:
                  summary: Get a todo by ID
                  parameters:
                    - name: id
                      in: path
                      required: true
                      schema:
                        type: integer
                  responses:
                    '200':
                      description: A single todo
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Todo'
            components:
              schemas:
                Todo:
                  type: object
                  properties:
                    id:
                      type: integer
                      format: int64
                    title:
                      type: string
                    completed:
                      type: boolean
            """;

    public static void main(String[] args) {
        System.out.println("Welcome to the REST API Client Demo!");
        System.out.println("This demo will walk through generating a Java client from an OpenAPI spec, and then refining it.");

        showInitialSpec();
        narrateInitialClientGeneration();
        narrateAddingJavadoc();
        narrateAddingErrorHandling();
        narrateAddingCustomHeaders();
        summarizeLearnings();

        System.out.println("\nDemo finished.");
    }

    private static void showInitialSpec() {
        System.out.println("\n--- STAGE 0: The OpenAPI Specification ---");
        System.out.println("This is the OpenAPI v3 specification we are providing to Jaider (or an equivalent generation tool):");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(SAMPLE_OPENAPI_SPEC);
        System.out.println("--------------------------------------------------------------------------------");
    }

    private static void narrateInitialClientGeneration() {
        System.out.println("\n--- STAGE 1: Initial Client Code Generation ---");
        System.out.println("Goal: Ask Jaider to generate basic client code: models and a Retrofit interface.");

        System.out.println("\nPrompt to Jaider:");
        System.out.println("================================================================================");
        System.out.println("Please generate a Java client for the following OpenAPI v3 specification. " +
                "Use Retrofit 2 for the client interface and Jackson for JSON serialization. " +
                "The client should include model classes for the schemas defined and a Retrofit interface for the API paths.");
        System.out.println("(The SAMPLE_OPENAPI_SPEC shown in STAGE 0 would be provided alongside this prompt.)");
        System.out.println("================================================================================");

        System.out.println("\nJaider processes this and (simulated) first generates the model class `Todo.java`:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("""
                package com.example.todo.model;

                import com.fasterxml.jackson.annotation.JsonProperty;

                // Using Java Record for conciseness
                public record Todo(
                    @JsonProperty("id") Integer id,
                    @JsonProperty("title") String title,
                    @JsonProperty("completed") Boolean completed
                ) {}

                /*
                // If using a POJO:
                public class Todo {
                    @JsonProperty("id")
                    private Integer id;
                    @JsonProperty("title")
                    private String title;
                    @JsonProperty("completed")
                    private Boolean completed;

                    // Default constructor (needed by Jackson if other constructors exist)
                    public Todo() {}

                    // Constructor with all fields
                    public Todo(Integer id, String title, Boolean completed) {
                        this.id = id;
                        this.title = title;
                        this.completed = completed;
                    }

                    // Getters
                    public Integer getId() { return id; }
                    public String getTitle() { return title; }
                    public Boolean isCompleted() { return completed; }

                    // Setters
                    public void setId(Integer id) { this.id = id; }
                    public void setTitle(String title) { this.title = title; }
                    public void setCompleted(Boolean completed) { this.completed = completed; }

                    @Override
                    public String toString() {
                        return "Todo{id=" + id + ", title='" + title + "', completed=" + completed + "}";
                    }
                }
                */
                """);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("✨ Best Practice: Review generated models. Ensure they accurately reflect the schema and include necessary annotations for your JSON library (e.g., `@JsonProperty` if field names differ or using a different naming strategy).");

        System.out.println("\nNext, Jaider (simulated) generates the Retrofit API interface `TodoApiService.java`:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("""
                package com.example.todo.api;

                import com.example.todo.model.Todo; // Assuming Todo model is in this package
                import retrofit2.Call;
                import retrofit2.http.*;

                import java.util.List;

                public interface TodoApiService {
                    @GET("todos")
                    Call<List<Todo>> listTodos();

                    @POST("todos")
                    Call<Todo> createTodo(@Body Todo todo);

                    @GET("todos/{id}")
                    Call<Todo> getTodoById(@Path("id") int id);
                }
                """);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("✨ Best Practice: Verify the generated interface. Check that HTTP methods, paths, path/query parameters, and request/response bodies match the OpenAPI spec. Ensure return types (e.g., `Call<T>`) are appropriate for your HTTP client library (Retrofit in this case).");

        System.out.println("\nInitial generation complete. Next, we'll (narrate) how to add Javadoc, then use this client, and then refine it for error handling and custom headers.");
    }

    private static void narrateAddingJavadoc() {
        System.out.println("\n--- STAGE 2: Adding Javadoc Documentation ---");
        System.out.println("Goal: Ask Jaider to add Javadoc to the generated code.");

        System.out.println("\nPrompt to Jaider:");
        System.out.println("================================================================================");
        System.out.println("Please add Javadoc comments to the `Todo.java` model and the `TodoApiService.java` interface. " +
                "Document all classes, records, methods, parameters, and return values.");
        System.out.println("================================================================================");

        System.out.println("\nJaider (simulated) updates `Todo.java` with Javadoc:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("""
                package com.example.todo.model;

                import com.fasterxml.jackson.annotation.JsonProperty;

                /**
                 * Represents a single Todo item.
                 *
                 * @param id The unique identifier for the todo item.
                 * @param title The title or description of the todo item.
                 * @param completed Whether the todo item is completed.
                 */
                public record Todo(
                    @JsonProperty("id") Integer id,
                    @JsonProperty("title") String title,
                    @JsonProperty("completed") Boolean completed
                ) {}
                """);
        System.out.println("--------------------------------------------------------------------------------");

        System.out.println("\nJaider (simulated) also updates `TodoApiService.java` with Javadoc:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("""
                package com.example.todo.api;

                import com.example.todo.model.Todo;
                import retrofit2.Call;
                import retrofit2.http.*;

                import java.util.List;

                /**
                 * Retrofit service interface for interacting with the Todo API.
                 */
                public interface TodoApiService {
                    /**
                     * Retrieves a list of all Todo items.
                     * @return A Call object for a list of Todos.
                     */
                    @GET("todos")
                    Call<List<Todo>> listTodos();

                    /**
                     * Creates a new Todo item.
                     * @param todo The Todo item to create.
                     * @return A Call object for the created Todo.
                     */
                    @POST("todos")
                    Call<Todo> createTodo(@Body Todo todo);

                    /**
                     * Retrieves a specific Todo item by its ID.
                     * @param id The ID of the Todo item to retrieve.
                     * @return A Call object for the requested Todo.
                     */
                    @GET("todos/{id}")
                    Call<Todo> getTodoById(@Path("id") int id);
                }
                """);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("✨ Best Practice: Clearly request documentation for all relevant elements (classes, methods, parameters, return values). If you have a preferred Javadoc style or specific tags you need, include that in your prompt.");
    }

    private static void narrateAddingErrorHandling() {
        System.out.println("\n--- STAGE 3: Enhancing Error Handling ---");
        System.out.println("Importance: Robust error handling is crucial for client resilience and a good user experience. APIs can fail for many reasons.");

        System.out.println("\nPrompt to Jaider:");
        System.out.println("================================================================================");
        System.out.println("How can I modify the client code to better handle potential API errors when using the `TodoApiService`? " +
                "Specifically, when calling `getTodoById(int id)`, how can I distinguish between a successful response, " +
                "a 404 Not Found error, and other errors like a 500 Internal Server Error? " +
                "Show an example of how I might call the method and process these different outcomes.");
        System.out.println("================================================================================");

        System.out.println("\nJaider (simulated) suggests the following approach for handling responses using Retrofit's Response<T> object:");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("""
                // Required imports for this example:
                // import com.example.todo.api.TodoApiService; // Your generated service
                // import com.example.todo.model.Todo;        // Your generated model
                // import retrofit2.Response;
                // import retrofit2.Retrofit;
                // import retrofit2.converter.jackson.JacksonConverterFactory;
                // import java.io.IOException;

                // Assume 'service' is an instance of TodoApiService
                // TodoApiService service = new Retrofit.Builder()...build().create(TodoApiService.class);

                System.out.println("Jaider's suggested usage for getTodoById(1):");
                System.out.println("try {");
                System.out.println("    // Make the API call (synchronous for this demo snippet)");
                System.out.println("    Response<Todo> response = service.getTodoById(1).execute();");
                System.out.println();
                System.out.println("    if (response.isSuccessful()) {");
                System.out.println("        Todo todo = response.body();");
                System.out.println("        System.out.println("Successfully fetched Todo: \" + (todo != null ? todo.toString() : \"null\"));");
                System.out.println("    } else {");
                System.out.println("        System.out.println("API Error - HTTP Status Code: \" + response.code());");
                System.out.println("        if (response.code() == 404) {");
                System.out.println("            System.out.println("Specific Handling: Todo item not found (404).\");");
                System.out.println("        } else if (response.code() >= 500) {");
                System.out.println("            System.out.println("Specific Handling: Server error (\" + response.code() + \").\");");
                System.out.println("        }");
                System.out.println();
                System.out.println("        // Attempt to read the error body, if present");
                System.out.println("        String errorBodyString = \"\";");
                System.out.println("        if (response.errorBody() != null) {");
                System.out.println("            try {");
                System.out.println("                errorBodyString = response.errorBody().string(); // .string() consumes the stream");
                System.out.println("            } catch (IOException e) {");
                System.out.println("                System.err.println("Error reading error body: \" + e.getMessage());");
                System.out.println("            }");
                System.out.println("        }");
                System.out.println("        System.out.println("Error Body (if any): \" + (errorBodyString.isEmpty() ? \"[empty or unreadable]\" : errorBodyString));");
                System.out.println("    }");
                System.out.println("} catch (IOException e) { // For network issues or Retrofit/OkHttp-level problems");
                System.out.println("    System.err.println("Network or I/O Exception occurred: \" + e.getMessage());");
                System.out.println("    // This typically means the request didn't even reach the server or a response couldn't be processed.");
                System.out.println("}");
                """);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("✨ Best Practice: Ask Jaider for specific error handling patterns for your chosen HTTP client library. Mention the types of errors you're concerned about (e.g., network errors, specific HTTP status codes, timeout exceptions). For asynchronous calls, error handling will be in callbacks or chained stages.");
    }

    private static void narrateAddingCustomHeaders() {
        System.out.println("\n--- STAGE 4: Adding Custom Headers (e.g., Authorization) ---");
        System.out.println("Scenario: Many APIs require custom headers for things like authentication (API keys, JWT Bearer tokens), specifying content types, or multi-tenancy.");

        System.out.println("\nPrompt to Jaider:");
        System.out.println("================================================================================");
        System.out.println("Show me how to add a dynamic `Authorization` header (e.g., a Bearer token that might be " +
                "retrieved from a preferences store or an auth service) to all requests made through `TodoApiService`. I'm using Retrofit.");
        System.out.println("================================================================================");

        System.out.println("\nJaider (simulated) suggests using an OkHttp Interceptor (as Retrofit uses OkHttp underneath):");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("""
                // Required imports for this example:
                // import okhttp3.Interceptor;
                // import okhttp3.OkHttpClient;
                // import okhttp3.Request;
                // import okhttp3.Response; // This is okhttp3.Response
                // import retrofit2.Retrofit;
                // import retrofit2.converter.jackson.JacksonConverterFactory;
                // import java.io.IOException;
                // import com.example.todo.api.TodoApiService; // Your generated service

                System.out.println("Jaider's suggested Interceptor for adding an Auth header:");
                System.out.println("class AuthInterceptor implements Interceptor {");
                System.out.println("    private volatile String authToken; // Volatile if it can be updated by another thread");
                System.out.println();
                System.out.println("    public AuthInterceptor(String initialToken) {");
                System.out.println("        this.authToken = initialToken;");
                System.out.println("    }");
                System.out.println();
                System.out.println("    // Call this method to update the token if it expires or changes");
                System.out.println("    public void updateToken(String newToken) {");
                System.out.println("        this.authToken = newToken;");
                System.out.println("        System.out.println("AuthInterceptor: Token updated.\");");
                System.out.println("    }");
                System.out.println();
                System.out.println("    @Override");
                System.out.println("    public okhttp3.Response intercept(Chain chain) throws IOException {");
                System.out.println("        Request originalRequest = chain.request();");
                System.out.println("        Request.Builder builder = originalRequest.newBuilder();");
                System.out.println();
                System.out.println("        String currentToken = this.authToken; // Use a local copy for thread safety");
                System.out.println("        if (currentToken != null && !currentToken.isEmpty()) {");
                System.out.println("            System.out.println("AuthInterceptor: Adding Authorization header.\");");
                System.out.println("            builder.header("Authorization", "Bearer " + currentToken);");
                System.out.println("        } else {");
                System.out.println("            System.out.println("AuthInterceptor: No auth token present, proceeding without Authorization header.\");");
                System.out.println("        }");
                System.out.println();
                System.out.println("        Request newRequest = builder.build();");
                System.out.println("        return chain.proceed(newRequest);");
                System.out.println("    }");
                System.out.println("}");

                System.out.println("\\nJaider's advice on integrating it with Retrofit:");
                System.out.println("// 1. Create an instance of the interceptor");
                System.out.println("// AuthInterceptor authInterceptor = new AuthInterceptor(\\"your_initial_bearer_token_here\\");");
                System.out.println();
                System.out.println("// 2. Build OkHttpClient and add the interceptor");
                System.out.println("// OkHttpClient okHttpClient = new OkHttpClient.Builder()");
                System.out.println("//        .addInterceptor(authInterceptor)");
                System.out.println("//        // ... other configurations like timeouts ... ");
                System.out.println("//        .build();");
                System.out.println();
                System.out.println("// 3. Build Retrofit with this OkHttpClient");
                System.out.println("// Retrofit retrofit = new Retrofit.Builder()");
                System.out.println("//        .baseUrl(\\"https://your.api.base.url/\\") // IMPORTANT: Replace with actual base URL");
                System.out.println("//        .client(okHttpClient)");
                System.out.println("//        .addConverterFactory(JacksonConverterFactory.create()) // Or your preferred converter");
                System.out.println("//        .build();");
                System.out.println();
                System.out.println("// 4. Create your service instance");
                System.out.println("// TodoApiService serviceWithAuth = retrofit.create(TodoApiService.class);");
                System.out.println();
                System.out.println("// Now, calls made with 'serviceWithAuth' will include the Authorization header.");
                System.out.println("// If the token needs to be updated (e.g., after login or refresh):");
                System.out.println("// authInterceptor.updateToken(\\"new_refreshed_token\\");");
                """);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("✨ Best Practice: When asking for ways to add headers, specify if they are static or dynamic. If dynamic, ask for strategies to update the header value (e.g., from a variable, a method call, or a secure store). Interceptors are powerful for this in OkHttp/Retrofit. Consider logging and analytics interceptors too.");
    }

    private static void summarizeLearnings() {
        System.out.println("\n--- STAGE 5: Key Learnings & Jaider Interaction Summary ---");
        System.out.println("This demo illustrated a path to generating and refining a REST API client with Jaider's assistance.");
        System.out.println("Effective interaction with Jaider (or similar AI coding assistants) involves several key strategies:");
        System.out.println();
        System.out.println("1.  Provide Clear Specifications:");
        System.out.println("    - Start by giving Jaider the complete OpenAPI specification (as shown in STAGE 0). The more context, the better the initial output.");
        System.out.println();
        System.out.println("2.  Be Specific in Your Prompts:");
        System.out.println("    - Clearly state your requirements. For example:");
        System.out.println("        - Stage 1: Specified Retrofit for the interface and Jackson for serialization.");
        System.out.println("        - Stage 2: Explicitly asked for Javadoc for all relevant code elements.");
        System.out.println("        - Stage 3: Described the types of errors to handle (404, 500) and asked for an example.");
        System.out.println("        - Stage 4: Requested a solution for *dynamic* Authorization headers.");
        System.out.println("    - Vague prompts lead to generic or less useful code.");
        System.out.println();
        System.out.println("3.  Embrace Iterative Refinement:");
        System.out.println("    - Don't expect a perfect, production-ready client in one shot.");
        System.out.println("    - We started with basic client generation (STAGE 1).");
        System.out.println("    - Then, we iteratively asked Jaider to add features: Javadoc (STAGE 2), detailed error handling (STAGE 3), and custom headers (STAGE 4).");
        System.out.println("    - This step-by-step approach makes complex tasks manageable.");
        System.out.println();
        System.out.println("4.  Always Review and Verify Generated Code:");
        System.out.println("    - Jaider generates code based on patterns it has learned. It's a powerful tool, but not infallible.");
        System.out.println("    - Critically review the generated models, API interfaces, and helper code (like interceptors).");
        System.out.println("    - Ensure it aligns with your project's standards, correctly implements the API contract, and handles edge cases appropriately.");
        System.out.println("    - The 'Best Practice' tips throughout the demo highlight areas for careful review.");
        System.out.println();
        System.out.println("5.  Ask \"How-to\" and \"Show-me-an-example\" Questions:");
        System.out.println("    - Jaider excels at providing specific implementation patterns.");
        System.out.println("    - Prompts like \"How can I handle X?\" or \"Show me an example of Y using Z library\" (e.g., error handling with Retrofit's Response<T> in STAGE 3) are very effective.");
        System.out.println();
        System.out.println("6.  Leverage and Internalize Best Practices:");
        System.out.println("    - Pay attention to the 'Best Practice' tips. These are derived from common software engineering principles and experience.");
        System.out.println("    - Incorporate these into your prompting strategy and code review process.");
        System.out.println();
        System.out.println("7.  Understand Jaider's Role - Your Coding Co-pilot:");
        System.out.println("    - Jaider is a powerful assistant that can significantly accelerate development by handling boilerplate and providing solutions to common problems.");
        System.out.println("    - However, you are still the pilot. Guide Jaider with clear, specific instructions, and always verify its work.");
        System.out.println("    - Effective use of Jaider involves a partnership: your domain knowledge and critical eye combined with Jaider's code generation capabilities.");
        System.out.println();
        System.out.println("By following these principles, you can leverage Jaider to become a more productive and effective developer.");
        System.out.println("This demo covered client generation, documentation, error handling, and headers – all essential for robust API client development.");
    }
}
