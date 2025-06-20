package dumb.jaider.app;

import dumb.jaider.app.exceptions.CircularDependencyException;
import dumb.jaider.app.exceptions.ComponentInstantiationException;
import dumb.jaider.app.exceptions.ComponentNotFoundException;
import dumb.jaider.app.exceptions.InvalidComponentDefinitionException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Manages the instantiation and retrieval of components (services, tools, etc.)
 * for the Jaider application. It functions as a simple dependency injection (DI) container.
 * <p>
 * Components are defined by a map of {@link JSONObject}s, where each key is a unique
 * component ID and the value is a JSON object describing how to create the component.
 * Supported instantiation strategies include:
 * <ul>
 *     <li>Constructor injection: Using the component's public constructor.</li>
 *     <li>Static factory method: Calling a public static method on the component's class.</li>
 *     <li>Instance factory method: Calling a public method on another managed component (factory bean).</li>
 * </ul>
 * The injector resolves constructor/method arguments, which can be:
 * <ul>
 *     <li>References to other components (using {@code "ref": "componentId"}).</li>
 *     <li>Literal values (e.g., strings, numbers, booleans, using {@code "value": ..., "type": ...}).</li>
 *     <li>Lists of values or references.</li>
 * </ul>
 * It caches created singleton instances and includes basic circular dependency detection
 * to prevent infinite loops during component creation.
 */
public class DependencyInjector {

    private static final Logger logger = LoggerFactory.getLogger(DependencyInjector.class);
    private final Map<String, JSONObject> componentDefinitions;
    private final Map<String, Object> singletonInstances = new HashMap<>();
    private final Set<String> currentlyInCreation = new HashSet<>();

    /**
     * Constructs a new DependencyInjector.
     *
     * @param componentDefinitions A map where keys are component IDs and values are
     *                             JSONObjects defining how to instantiate the component.
     *                             A defensive copy of this map is used internally.
     */
    public DependencyInjector(Map<String, JSONObject> componentDefinitions) {
        this.componentDefinitions = new HashMap<>(componentDefinitions); // Use a copy
        logger.debug("DependencyInjector initialized with {} component definitions.", componentDefinitions.size());
    }

    /**
     * Registers an already created instance as a singleton component.
     * If a component with the same ID is already registered, it will be overwritten.
     * If no JSON definition exists for this ID and the instance is not null,
     * a minimal definition (based on the instance's class) is added to allow
     * it to be potentially used as a dependency by other JSON-defined components.
     *
     * @param id The unique ID for the component.
     * @param instance The pre-existing instance to register.
     */
    public void registerSingleton(String id, Object instance) {
        if (singletonInstances.containsKey(id)) {
            logger.warn("Overwriting existing singleton instance for id: {}", id);
        }
        singletonInstances.put(id, instance);
        logger.debug("Registered pre-existing singleton instance for id: {}", id);

        // If a component is registered programmatically, and no JSON definition exists,
        // create a minimal one so it can be referenced by other components if needed.
        if (!componentDefinitions.containsKey(id) && instance != null) {
            var definition = new JSONObject();
            definition.put("class", instance.getClass().getName());
            // Note: This minimal definition doesn't include constructor/factory args.
            // It's primarily useful if this manually registered instance is a simple bean
            // or if other components only need its type for injection.
            componentDefinitions.put(id, definition);
            logger.debug("Added minimal component definition for pre-registered singleton: {}", id);
        }
    }

    /**
     * Clears the cache of singleton instances and the set tracking components
     * currently under construction. This forces re-creation of components on subsequent
     * {@code getComponent(String)} calls, based on their JSON definitions.
     * Manually registered singletons (via {@code registerSingleton}) that do not have
     * a corresponding JSON definition might not be available after clearing if they
     * were only in the cache.
     */
    public void clearCache() {
        singletonInstances.clear();
        currentlyInCreation.clear(); // Important for re-creation attempts after errors
        logger.debug("Singleton cache and creation tracking cleared.");
    }

    /**
     * Retrieves a component instance by its ID.
     * <p>
     * If the component has already been created and cached as a singleton, the cached
     * instance is returned. Otherwise, the injector attempts to create the component
     * based on its JSON definition. This process includes:
     * <ol>
     *     <li>Checking for circular dependencies.</li>
     *     <li>Instantiating the component using its constructor or a factory method.</li>
     *     <li>Resolving and injecting any required arguments (other components or literal values).</li>
     *     <li>Caching the newly created instance as a singleton.</li>
     * </ol>
     *
     * @param id The unique ID of the component to retrieve.
     * @return The component instance.
     * @throws ComponentNotFoundException if no definition is found for the given ID.
     * @throws CircularDependencyException if a circular dependency is detected during instantiation.
     * @throws ComponentInstantiationException if any error occurs during component creation
     *         (e.g., class not found, method not found, constructor/factory invocation error,
     *         argument type mismatch).
     * @throws InvalidComponentDefinitionException if the JSON definition for the component is malformed.
     */
    public Object getComponent(String id) {
        if (singletonInstances.containsKey(id)) {
            logger.debug("Returning cached singleton instance for id: {}", id);
            return singletonInstances.get(id);
        }

        if (currentlyInCreation.contains(id)) {
            // Add current component ID to the path for a more complete error message
            List<String> path = new ArrayList<>(currentlyInCreation);
            path.add(id);
            throw new CircularDependencyException(id, new LinkedHashSet<>(path)); // Use LinkedHashSet to preserve order
        }

        var definition = componentDefinitions.get(id);
        if (definition == null) {
            throw new ComponentNotFoundException(id, "Available definitions: " + componentDefinitions.keySet());
        }

        currentlyInCreation.add(id);
        logger.debug("Starting creation of component: {}", id);

        try {
            var instance = createComponentInstance(id, definition);
            if (!singletonInstances.containsKey(id)) { // Could have been added by a recursive call if it's a singleton
                singletonInstances.put(id, instance);
                logger.debug("Cached new singleton instance for id: {}", id);
            }
            logger.info("Successfully created and cached component: {}", id);
            return instance;
        } catch (CircularDependencyException | ComponentNotFoundException | ComponentInstantiationException | InvalidComponentDefinitionException e) {
            // These are specific custom exceptions, re-throw them directly.
            throw e;
        } catch (Exception e) { // Catch any other unexpected error during creation
            logger.error("Unexpected error during component creation for id '{}': {}", id, e.getMessage(), e);
            throw new ComponentInstantiationException(id, "Unexpected error during component creation.", e);
        } finally {
            currentlyInCreation.remove(id);
            logger.debug("Finished creation attempt for component: {}", id);
        }
    }

    private Object createComponentInstance(String id, JSONObject definition) {
        logger.debug("Creating instance for component '{}' with definition: {}", id, definition.toString(2));
        try {
            if (definition.has("factoryBean") && definition.has("factoryMethod")) {
                return createWithInstanceFactory(id, definition);
            } else if (definition.has("staticFactoryMethod")) {
                return createWithStaticFactory(id, definition);
            } else {
                return createWithConstructor(id, definition);
            }
        } catch (ClassNotFoundException e) {
            throw new ComponentInstantiationException(id, "Class '" + definition.optString("class", "N/A") + "' not found.", e);
        } catch (InvocationTargetException e) {
            throw new ComponentInstantiationException(id, "Constructor or factory method threw an exception.", e.getCause() != null ? e.getCause() : e);
        } catch (InstantiationException e) {
            throw new ComponentInstantiationException(id, "Class '" + definition.optString("class", "N/A") + "' is abstract, an interface, or lacks a suitable constructor.", e);
        } catch (IllegalAccessException e) {
            throw new ComponentInstantiationException(id, "Constructor or factory method is not accessible.", e);
        }
    }

    private Object createWithConstructor(String id, JSONObject definition) throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException {
        var className = definition.optString("class", null);
        if (className == null) {
             throw new InvalidComponentDefinitionException(id, "Definition must contain a 'class' if not using a factory method.");
        }
        logger.debug("Attempting constructor injection for component '{}', class '{}'", id, className);
        var clazz = Class.forName(className);
        var argsArray = definition.optJSONArray("constructorArgs");
        var args = resolveArguments(argsArray, id, "constructor");
        var argTypes = getArgumentTypes(args, argsArray, id, "constructor");
        var constructor = findConstructor(clazz, argTypes);
        if (constructor == null) {
            throw new ComponentInstantiationException(id, "Suitable constructor not found for class " + clazz.getName() + " with argument types " + Arrays.toString(argTypes));
        }
        logger.debug("Invoking constructor for {} with arguments: {}", id, Arrays.toString(args));
        return constructor.newInstance(args);
    }

    private Object createWithStaticFactory(String id, JSONObject definition) throws ClassNotFoundException, InvocationTargetException, IllegalAccessException {
        var className = definition.optString("class", null);
        if (className == null) {
            throw new InvalidComponentDefinitionException(id, "Definition with staticFactoryMethod must also specify a 'class'.");
        }
        var staticFactoryMethodName = definition.getString("staticFactoryMethod");
        logger.debug("Attempting static factory method injection for component '{}', class '{}', method '{}'", id, className, staticFactoryMethodName);
        var clazz = Class.forName(className);
        var argsArray = definition.optJSONArray("staticFactoryArgs");
        var args = resolveArguments(argsArray, id, "static factory");
        var argTypes = getArgumentTypes(args, argsArray, id, "static factory");
        var factoryMethod = findMethod(clazz, staticFactoryMethodName, argTypes);
        if (factoryMethod == null) {
            throw new ComponentInstantiationException(id, "Static factory method '" + staticFactoryMethodName + "' with matching arguments not found in class " + clazz.getName() + ". Argument types searched: " + Arrays.toString(argTypes));
        }
        if (!Modifier.isStatic(factoryMethod.getModifiers())) {
            throw new ComponentInstantiationException(id, "Factory method '" + staticFactoryMethodName + "' in class " + clazz.getName() + " is not static.");
        }
        logger.debug("Invoking static factory method {} for {} with arguments: {}", staticFactoryMethodName, id, Arrays.toString(args));
        return factoryMethod.invoke(null, args);
    }

    private Object createWithInstanceFactory(String id, JSONObject definition) throws InvocationTargetException, IllegalAccessException {
        var factoryBeanId = definition.getString("factoryBean");
        var factoryMethodName = definition.getString("factoryMethod");
        logger.debug("Attempting instance factory method injection for component '{}', factoryBean '{}', method '{}'", id, factoryBeanId, factoryMethodName);
        var factoryBeanInstance = getComponent(factoryBeanId); // Resolve factory bean dependency
        var factoryBeanClass = factoryBeanInstance.getClass();
        var argsArray = definition.optJSONArray("factoryArgs");
        var args = resolveArguments(argsArray, id, "instance factory");
        var argTypes = getArgumentTypes(args, argsArray, id, "instance factory");
        var factoryMethod = findMethod(factoryBeanClass, factoryMethodName, argTypes);
        if (factoryMethod == null) {
            throw new ComponentInstantiationException(id, "Instance factory method '" + factoryMethodName + "' with matching arguments not found in class " + factoryBeanClass.getName() + ".");
        }
        logger.debug("Invoking instance factory method {} on bean {} for component {} with arguments: {}", factoryMethodName, factoryBeanId, id, Arrays.toString(args));
        return factoryMethod.invoke(factoryBeanInstance, args);
    }

    private Object[] resolveArguments(JSONArray argsArray, String componentId, String creationType) {
        if (argsArray == null) {
            return new Object[0];
        }
        logger.debug("Resolving arguments for component '{}' ({} creation)", componentId, creationType);
        List<Object> resolvedArgs = new ArrayList<>();
        for (var i = 0; i < argsArray.length(); i++) {
            var argDef = argsArray.getJSONObject(i);
            if (argDef.has("ref")) {
                var refId = argDef.getString("ref");
                logger.debug("Resolving 'ref' argument: {}", refId);
                resolvedArgs.add(getComponent(refId));
            } else if (argDef.has("value")) {
                var value = argDef.get("value");
                var type = argDef.optString("type", "String").toLowerCase();
                logger.debug("Resolving 'value' argument: type '{}', value '{}'", type, value);
                try {
                    resolvedArgs.add(convertLiteralValue(value, type));
                } catch (NumberFormatException e) {
                    throw new InvalidComponentDefinitionException(componentId, "Failed to parse value '" + value + "' to type '" + type + "' for argument " + i + " during " + creationType + " creation.");
                }
            } else if (argDef.has("list")) {
                var listArray = argDef.getJSONArray("list");
                var listType = argDef.optString("listType", "String").toLowerCase(); // Type of elements in the list
                logger.debug("Resolving 'list' argument: element type '{}', size '{}'", listType, listArray.length());
                List<Object> listValues = new ArrayList<>();
                for (var j = 0; j < listArray.length(); j++) {
                    var listItem = listArray.get(j);
                    if (listItem instanceof JSONObject listItemJson) {
                        if (listItemJson.has("ref")) {
                            var refId = listItemJson.getString("ref");
                            logger.debug("Resolving 'ref' list element: {}", refId);
                            listValues.add(getComponent(refId));
                        } else if (listItemJson.has("value")) {
                            var value = listItemJson.get("value");
                            var itemType = listItemJson.optString("type", listType); // Use element's type if specified, else listType
                            logger.debug("Resolving 'value' list element: type '{}', value '{}'", itemType, value);
                            listValues.add(convertLiteralValue(value, itemType));
                        } else {
                            // Pass JSONObject as is, if it's neither a ref nor a value type.
                            // This might be for complex objects not managed by DI but configured in-place.
                            // Or, it could be an error if such structures are not expected.
                            // For now, let's assume it could be a valid, non-DI-managed map-like structure.
                            logger.warn("List element at index {} for component '{}' is a JSONObject but not a 'ref' or 'value'. Passing as raw JSONObject. Element: {}", j, componentId, listItemJson.toString(2));
                            listValues.add(listItemJson); // Or convert to Map<String, Object> if preferred
                        }
                    } else {
                        // Literal value directly in the list
                        logger.debug("Resolving literal list element: type '{}', value '{}'", listType, listItem);
                        try {
                            listValues.add(convertLiteralValue(listItem, listType));
                        } catch (NumberFormatException e) {
                            throw new InvalidComponentDefinitionException(componentId, "Failed to parse list value '" + listItem + "' to type '" + listType + "' for argument " + i + ", list element " + j + " during " + creationType + " creation.");
                        }
                    }
                }
                if (listValues.isEmpty()) {
                    logger.warn("Resolved 'list' argument for component '{}' is empty.", componentId);
                }
                resolvedArgs.add(listValues);
            }
            else {
                throw new InvalidComponentDefinitionException(componentId, "Argument definition " + i + " must have 'ref', 'value', or 'list'. Arg: " + argDef);
            }
        }
        return resolvedArgs.toArray();
    }

    private Object convertLiteralValue(Object value, String type) {
        return switch (type) {
            case "int", "java.lang.integer" -> value instanceof Integer ? value : Integer.parseInt(value.toString());
            case "string", "java.lang.string" -> value.toString();
            case "boolean", "java.lang.boolean" ->
                    value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
            case "float", "java.lang.float" -> value instanceof Float ? value : Float.parseFloat(value.toString());
            case "double", "java.lang.double" -> value instanceof Double ? value : Double.parseDouble(value.toString());
            case "long", "java.lang.long" -> value instanceof Long ? value : Long.parseLong(value.toString());
            default -> {
                logger.warn("Unsupported literal type '{}', treating as String.", type);
                yield value.toString();
            }
        };
    }

    private Class<?>[] getArgumentTypes(Object[] args, JSONArray argsArray, String componentId, String creationType) {
        if (argsArray == null) return new Class<?>[0];
        if (args.length != argsArray.length()) {
            throw new ComponentInstantiationException(componentId, "Mismatch between resolved args count and definition count for " + creationType + " creation.");
        }

        var argTypes = new Class<?>[args.length];
        for (var i = 0; i < args.length; i++) {
            var argDef = argsArray.getJSONObject(i);
            if (argDef.has("ref")) {
                if (args[i] == null) {
                    var refId = argDef.getString("ref");
                    var refDefinition = componentDefinitions.get(refId);
                    if (refDefinition == null || !refDefinition.has("class")) {
                        throw new InvalidComponentDefinitionException(componentId, "Cannot determine type of null argument for ref '" + refId + "' as its definition or class is missing.");
                    }
                    try {
                        argTypes[i] = Class.forName(refDefinition.getString("class"));
                    } catch (ClassNotFoundException e) {
                        throw new ComponentInstantiationException(refId, "Class not found for referenced component '" + refId + "' while determining argument types for '" + componentId + "'.", e);
                    }
                } else {
                    argTypes[i] = args[i].getClass();
                }
            } else if (argDef.has("value")) {
                var type = argDef.optString("type", "String").toLowerCase();
                argTypes[i] = getClassForLiteralType(type);
            } else if (argDef.has("list")) {
                 argTypes[i] = List.class; // The parameter type must be List or Collection
            } else {
                throw new InvalidComponentDefinitionException(componentId, "Invalid argument definition in getArgumentTypes for " + creationType + " creation. Arg: " + argDef);
            }
        }
        return argTypes;
    }

    private Class<?> getClassForLiteralType(String typeName) {
        return switch (typeName.toLowerCase()) {
            case "int", "java.lang.integer" -> int.class;
            case "string", "java.lang.string" -> String.class;
            case "boolean", "java.lang.boolean" -> boolean.class;
            case "float", "java.lang.float" -> float.class;
            case "double", "java.lang.double" -> double.class;
            case "long", "java.lang.long" -> long.class;
            default -> {
                logger.warn("Unknown literal type '{}' specified, defaulting to String.class for type resolution.", typeName);
                yield String.class;
            }
        };
    }

    private Constructor<?> findConstructor(Class<?> clazz, Class<?>[] argTypes) {
        try {
            return clazz.getConstructor(argTypes);
        } catch (NoSuchMethodException e) {
            for (var ctor : clazz.getConstructors()) {
                if (areTypesAssignable(argTypes, ctor.getParameterTypes())) {
                    return ctor;
                }
            }
            return null;
        }
    }

    private Method findMethod(Class<?> clazz, String methodName, Class<?>[] argTypes) {
        try {
            return clazz.getMethod(methodName, argTypes);
        } catch (NoSuchMethodException e) {
            for (var method : clazz.getMethods()) {
                if (method.getName().equals(methodName) && areTypesAssignable(argTypes, method.getParameterTypes())) {
                    return method;
                }
            }
            return null;
        }
    }

    private boolean areTypesAssignable(Class<?>[] fromTypes, Class<?>[] toTypes) {
        if (fromTypes.length != toTypes.length) {
            return false;
        }
        for (var i = 0; i < fromTypes.length; i++) {
            var targetType = toTypes[i];
            var sourceType = fromTypes[i];

            if (sourceType == null) { // A null argument can be assigned to any non-primitive reference type
                if (targetType.isPrimitive()) return false;
                continue;
            }
            if (targetType.isAssignableFrom(sourceType)) {
                continue;
            }
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
            return false;
        }
        return true;
    }
}
