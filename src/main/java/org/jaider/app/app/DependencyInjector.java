package org.jaider.app.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Manages the instantiation and retrieval of components based on JSON definitions.
 * Supports instantiation via public constructors, public static factory methods,
 * and public instance factory methods on other managed components.
 * Handles resolution of constructor/method arguments, including references to other
 * components and literal values with type conversion.
 * Includes basic circular dependency detection and caching of singleton instances.
 */
public class DependencyInjector {

    private final Map<String, JSONObject> componentDefinitions;
    private final Map<String, Object> singletonInstances = new HashMap<>();
    private final Set<String> currentlyInCreation = new HashSet<>();

    /**
     * Constructs a new DependencyInjector with the given component definitions.
     *
     * @param componentDefinitions A map where keys are component IDs and values are
     *                             JSONObject definitions for each component.
     */
    public DependencyInjector(Map<String, JSONObject> componentDefinitions) {
        // Store a mutable copy, as registerSingleton might add to it.
        this.componentDefinitions = new HashMap<>(componentDefinitions);
    }

    /**
     * Registers an already existing instance as a singleton in the injector.
     * This is useful for objects created outside the injector's control but needed as dependencies.
     *
     * @param id       The unique identifier for the singleton instance.
     * @param instance The instance to register.
     */
    public void registerSingleton(String id, Object instance) {
        if (singletonInstances.containsKey(id)) {
            // Potentially log a warning or throw an error if overwriting is not desired.
            // For now, allow overwrite to match the general behavior of putting into a map.
        }
        singletonInstances.put(id, instance);

        // Add a minimal definition if not already present, to allow `getComponent` to work
        // and to provide class information if needed later (though it's less critical for pre-resolved singletons).
        if (!componentDefinitions.containsKey(id) && instance != null) {
            JSONObject definition = new JSONObject();
            definition.put("class", instance.getClass().getName());
            // No need to put "id" into the definition itself, as it's the key in the map.
            // This definition is minimal, as the instance is already created.
            // It primarily helps if something introspects all definitions or if getComponent is called.
            componentDefinitions.put(id, definition);
        }
        // Note: Pre-resolved singletons don't go through the currentlyInCreation cycle.
    }

    /**
     * Clears the cache of singleton instances and the set tracking components currently in creation.
     * This allows the injector to be reused or refreshed, for example, after configuration changes.
     */
    public void clearCache() {
        singletonInstances.clear();
        currentlyInCreation.clear();
    }

    /**
     * Retrieves or creates a component instance by its ID.
     * Handles singleton caching and circular dependency detection.
     *
     * @param id The unique identifier of the component to retrieve.
     * @return The component instance.
     * @throws RuntimeException if the component definition is not found,
     *                          if a circular dependency is detected, or if any error occurs during instantiation.
     */
    public Object getComponent(String id) {
        if (singletonInstances.containsKey(id)) {
            return singletonInstances.get(id);
        }

        if (currentlyInCreation.contains(id)) {
            throw new RuntimeException("Circular dependency detected for component: " + id);
        }

        JSONObject definition = componentDefinitions.get(id);
        if (definition == null) {
            throw new RuntimeException("Component definition not found for id: " + id + ". Available definitions: " + componentDefinitions.keySet());
        }

        currentlyInCreation.add(id);

        try {
            Object instance = createComponentInstance(id, definition);
            // If not already put by registerSingleton, cache it.
            // registerSingleton would have already put it if it was pre-registered.
            if (!singletonInstances.containsKey(id)) {
                 singletonInstances.put(id, instance);
            }
            return instance;
        } finally {
            currentlyInCreation.remove(id);
        }
    }

    /**
     * Creates a component instance based on its definition.
     * This method orchestrates the actual instantiation logic using constructors or factory methods.
     *
     * @param id         The ID of the component being created.
     * @param definition The JSON definition of the component.
     * @return The created component instance.
     * @throws RuntimeException if instantiation fails for any reason (class not found, method not found, etc.).
     */
    private Object createComponentInstance(String id, JSONObject definition) {
        // If the instance was pre-registered (e.g. via registerSingleton),
        // and then getComponent is called for it, it might already be in singletonInstances.
        // However, the initial check in getComponent handles this. This method is for *creation*.
        // But if a pre-registered instance's definition *also* existed and led here,
        // we should ensure we don't try to re-create it if it's already been resolved.
        // The check in getComponent `if (singletonInstances.containsKey(id))` handles this mostly.

        try {
            // String className = definition.optString("class", null); // Ensure className is appropriately handled or fetched inside each block if needed.
            // Class<?> clazz = null;
            // if (className != null) { // className might be null for instance factory methods
            //     clazz = Class.forName(className);
            // }


            if (definition.has("factoryBean") && definition.has("factoryMethod")) {
                // INSTANCE FACTORY METHOD LOGIC
                String factoryBeanId = definition.getString("factoryBean");
                String factoryMethodName = definition.getString("factoryMethod");
                JSONArray argsArray = definition.optJSONArray("factoryArgs"); // Use "factoryArgs"

                Object factoryBeanInstance = getComponent(factoryBeanId);
                Class<?> factoryBeanClass = factoryBeanInstance.getClass();

                Object[] args = resolveArguments(argsArray, id);
                Class<?>[] argTypes = getArgumentTypes(args, argsArray, id);

                Method factoryMethod = findMethod(factoryBeanClass, factoryMethodName, argTypes);
                if (factoryMethod == null) {
                    throw new RuntimeException("Instance factory method '" + factoryMethodName + "' with matching arguments not found in class " + factoryBeanClass.getName() + " for component '" + id + "'.");
                }
                // Modifier.isStatic(factoryMethod.getModifiers()) check might be needed if we want to ensure it's not static
                return factoryMethod.invoke(factoryBeanInstance, args);

            } else if (definition.has("staticFactoryMethod")) {
                // STATIC FACTORY METHOD LOGIC
                String className = definition.optString("class", null);
                if (className == null) {
                    throw new RuntimeException("Component definition for '" + id + "' with staticFactoryMethod must also specify a 'class'.");
                }
                Class<?> clazz = Class.forName(className);

                String staticFactoryMethodName = definition.getString("staticFactoryMethod");
                JSONArray argsArray = definition.optJSONArray("staticFactoryArgs"); // Use "staticFactoryArgs"

                Object[] args = resolveArguments(argsArray, id);
                Class<?>[] argTypes = getArgumentTypes(args, argsArray, id);

                Method factoryMethod = findMethod(clazz, staticFactoryMethodName, argTypes);
                if (factoryMethod == null) {
                    throw new RuntimeException("Static factory method '" + staticFactoryMethodName + "' with matching arguments not found in class " + clazz.getName() + " for component '" + id + "'. Argument types searched: " + Arrays.toString(argTypes));
                }
                if (!Modifier.isStatic(factoryMethod.getModifiers())) {
                    throw new RuntimeException("Factory method '" + staticFactoryMethodName + "' in class " + clazz.getName() + " for component '" + id + "' is not static.");
                }
                return factoryMethod.invoke(null, args);

            } else {
                // CONSTRUCTOR LOGIC
                String className = definition.optString("class", null);
                if (className == null) {
                     throw new RuntimeException("Component definition for '" + id + "' must contain a 'class' if not using a factory method.");
                }
                Class<?> clazz = Class.forName(className);

                JSONArray argsArray = definition.optJSONArray("constructorArgs"); // Use "constructorArgs"

                Object[] args = resolveArguments(argsArray, id);
                Class<?>[] argTypes = getArgumentTypes(args, argsArray, id);

                Constructor<?> constructor = findConstructor(clazz, argTypes);
                if (constructor == null) {
                    throw new RuntimeException("Suitable constructor not found for class " + clazz.getName() + " for component '" + id + "' with argument types " + Arrays.toString(argTypes));
                }
                return constructor.newInstance(args);
            }
        } catch (ClassNotFoundException e) {
            String actualClassName = definition.optString("class", "N/A"); // Re-fetch in case className was null initially
            throw new RuntimeException("Class '" + actualClassName + "' not found for component '" + id + "'.", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Error creating component '" + id + "'. Constructor or factory method threw an exception: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e.getCause() != null ? e.getCause() : e);
        } catch (InstantiationException e) {
             String actualClassName = definition.optString("class", "N/A");
            throw new RuntimeException("Error creating component '" + id + "'. Class '" + actualClassName + "' is abstract, an interface, or lacks a suitable constructor.", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error creating component '" + id + "'. Constructor or factory method is not accessible.", e);
        }
    }

    /**
     * Resolves argument values based on their JSON definitions.
     * Supports references to other components and literal values with type conversion.
     *
     * @param argsArray The JSONArray of argument definitions.
     * @param componentId The ID of the component whose arguments are being resolved (for error reporting).
     * @return An array of resolved argument objects.
     * @throws RuntimeException if an argument definition is invalid or a referenced component cannot be resolved.
     */
    private Object[] resolveArguments(JSONArray argsArray, String componentId) {
        if (argsArray == null) {
            return new Object[0];
        }
        List<Object> resolvedArgs = new ArrayList<>();
        for (int i = 0; i < argsArray.length(); i++) {
            JSONObject argDef = argsArray.getJSONObject(i);
            if (argDef.has("ref")) {
                String refId = argDef.getString("ref");
                resolvedArgs.add(getComponent(refId)); // Recursive call to get dependency
            } else if (argDef.has("value")) {
                Object value = argDef.get("value"); // Value can be String, Boolean, Integer, Double, etc.
                String type = argDef.optString("type", "String").toLowerCase();
                try {
                    switch (type) {
                        case "int":
                            resolvedArgs.add(value instanceof Integer ? value : Integer.parseInt(value.toString()));
                            break;
                        case "string":
                            resolvedArgs.add(value.toString());
                            break;
                        case "boolean":
                            resolvedArgs.add(value instanceof Boolean ? value : Boolean.parseBoolean(value.toString()));
                            break;
                        case "float":
                            // org.json uses BigDecimal for fractional numbers, convert accordingly
                            resolvedArgs.add(value instanceof Float ? value : Float.parseFloat(value.toString()));
                            break;
                        case "double":
                            // org.json uses BigDecimal for fractional numbers, convert accordingly
                            resolvedArgs.add(value instanceof Double ? value : Double.parseDouble(value.toString()));
                            break;
                        case "long":
                            resolvedArgs.add(value instanceof Long ? value : Long.parseLong(value.toString()));
                            break;
                        default:
                            resolvedArgs.add(value.toString()); // Default to string if type is unknown
                    }
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Failed to parse value '" + value + "' to type '" + type + "' for component '" + componentId + "', argument " + i, e);
                }
            } else {
                throw new RuntimeException("Invalid argument definition for component '" + componentId + "': must have 'ref' or 'value'. Arg: " + argDef);
            }
        }
        return resolvedArgs.toArray();
    }

    /**
     * Determines the Class types of resolved arguments.
     * This is used to find matching constructors or factory methods.
     *
     * @param args      The array of resolved argument objects.
     * @param argsArray The JSONArray of argument definitions (used to get explicit type info).
     * @param componentId The ID of the component (for error reporting).
     * @return An array of Class objects representing the argument types.
     */
    private Class<?>[] getArgumentTypes(Object[] args, JSONArray argsArray, String componentId) {
        if (argsArray == null) { // No arguments defined
            return new Class<?>[0];
        }
        if (args.length != argsArray.length()) {
            // This should ideally not happen if resolveArguments is correct
            throw new IllegalStateException("Mismatch between resolved args count and definition count for component '" + componentId + "'.");
        }

        Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            JSONObject argDef = argsArray.getJSONObject(i);
            if (argDef.has("ref")) {
                // For 'ref' types, we need to determine the type that the constructor/method
                // parameter expects. Using args[i].getClass() can be problematic if the parameter
                // is an interface or superclass. A more robust way is to get the class from
                // the definition of the referenced component if possible, or rely on reflection
                // to find a method/constructor that can accept the runtime type of args[i].
                // The current findConstructor/findMethod uses isAssignableFrom, which helps.
                if (args[i] == null) {
                    // If the resolved argument is null, try to get its type from its definition.
                    // This can happen if a dependency failed to create.
                    String refId = argDef.getString("ref");
                    JSONObject refDefinition = componentDefinitions.get(refId);
                    if (refDefinition == null || !refDefinition.has("class")) {
                        throw new RuntimeException("Cannot determine type of null argument for ref '" + refId + "' in component '" + componentId + "' as its definition or class is missing.");
                    }
                    try {
                        argTypes[i] = Class.forName(refDefinition.getString("class"));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Class not found for referenced component '" + refId + "' while determining argument types for '" + componentId + "'.", e);
                    }
                } else {
                    argTypes[i] = args[i].getClass();
                }
            } else if (argDef.has("value")) {
                String type = argDef.optString("type", "String").toLowerCase();
                switch (type) {
                    case "int":
                        argTypes[i] = int.class;
                        break;
                    case "string":
                        argTypes[i] = String.class;
                        break;
                    case "boolean":
                        argTypes[i] = boolean.class;
                        break;
                    case "float":
                        argTypes[i] = float.class;
                        break;
                    case "double":
                        argTypes[i] = double.class;
                        break;
                    case "long":
                        argTypes[i] = long.class;
                        break;
                    default:
                        argTypes[i] = String.class; // Default to String for unknown literal types
                }
            } else {
                // This case should not be reached if resolveArguments has validated the structure.
                throw new RuntimeException("Invalid argument definition in getArgumentTypes for component '" + componentId + "'. Arg: " + argDef);
            }
        }
        return argTypes;
    }

    /**
     * Finds a public constructor in the given class that matches the argument types.
     *
     * @param clazz    The class to search for a constructor.
     * @param argTypes The desired argument types for the constructor.
     * @return The matching Constructor object, or null if not found.
     */
    private Constructor<?> findConstructor(Class<?> clazz, Class<?>[] argTypes) {
        try {
            return clazz.getConstructor(argTypes);
        } catch (NoSuchMethodException e) {
            // Fallback: Check all public constructors for assignable types
            for (Constructor<?> ctor : clazz.getConstructors()) {
                if (areTypesAssignable(argTypes, ctor.getParameterTypes())) {
                    return ctor;
                }
            }
            return null;
        }
    }

    /**
     * Finds a public method (static or instance) in the given class that matches the name and argument types.
     *
     * @param clazz      The class to search for a method.
     * @param methodName The name of the method.
     * @param argTypes   The desired argument types for the method.
     * @return The matching Method object, or null if not found.
     */
    private Method findMethod(Class<?> clazz, String methodName, Class<?>[] argTypes) {
        try {
            return clazz.getMethod(methodName, argTypes);
        } catch (NoSuchMethodException e) {
            // Fallback: Check all public methods for assignable types
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName) && areTypesAssignable(argTypes, method.getParameterTypes())) {
                    return method;
                }
            }
            return null;
        }
    }

    /**
     * Checks if an array of source types can be assigned to an array of target types.
     * This is useful for matching method/constructor signatures, allowing for subtypes
     * and automatic boxing/unboxing between primitives and their wrapper classes.
     *
     * @param fromTypes The source types (e.g., actual argument types).
     * @param toTypes   The target types (e.g., formal parameter types).
     * @return true if all source types are assignable to their corresponding target types, false otherwise.
     */
    private boolean areTypesAssignable(Class<?>[] fromTypes, Class<?>[] toTypes) {
        if (fromTypes.length != toTypes.length) {
            return false;
        }
        for (int i = 0; i < fromTypes.length; i++) {
            // Target type is toTypes[i], source type is fromTypes[i]
            Class<?> targetType = toTypes[i];
            Class<?> sourceType = fromTypes[i];

            if (targetType.isAssignableFrom(sourceType)) {
                continue;
            }

            // Handle primitive vs wrapper type compatibility
            if (targetType.isPrimitive()) {
                if (sourceType == Integer.class && targetType == int.class) continue;
                if (sourceType == Long.class && targetType == long.class) continue;
                if (sourceType == Double.class && targetType == double.class) continue;
                if (sourceType == Float.class && targetType == float.class) continue;
                if (sourceType == Boolean.class && targetType == boolean.class) continue;
                if (sourceType == Character.class && targetType == char.class) continue;
                if (sourceType == Byte.class && targetType == byte.class) continue;
                if (sourceType == Short.class && targetType == short.class) continue;
            } else if (sourceType.isPrimitive()) {
                if (targetType == Integer.class && sourceType == int.class) continue;
                if (targetType == Long.class && sourceType == long.class) continue;
                if (targetType == Double.class && sourceType == double.class) continue;
                if (targetType == Float.class && sourceType == float.class) continue;
                if (targetType == Boolean.class && sourceType == boolean.class) continue;
                if (targetType == Character.class && sourceType == char.class) continue;
                if (targetType == Byte.class && sourceType == byte.class) continue;
                if (targetType == Short.class && sourceType == short.class) continue;
            }
            return false; // Types are not assignable
        }
        return true;
    }
}
