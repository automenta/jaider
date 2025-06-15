package dumb.jaider.app;

import dumb.jaider.app.exceptions.CircularDependencyException;
import dumb.jaider.app.exceptions.ComponentNotFoundException;
import dumb.jaider.app.exceptions.ComponentInstantiationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
// import org.mockito.Mockito; // Not used in these tests

import java.util.List;
import java.util.HashMap;
import java.util.Map;
// import java.util.ArrayList; // Not directly used

import static org.junit.jupiter.api.Assertions.*;

public class DependencyInjectorTest {

    private DependencyInjector injector;
    private Map<String, JSONObject> componentDefinitions;

    // --- Dummy Classes for Testing ---

    static class ComponentA {
        public String name = "ComponentA_DefaultName"; // Default to distinguish from manually set names
        public ComponentA() {}
    }

    static class ComponentBWithDep {
        public ComponentA depA;
        public String id; // To check instance identity for B
        public ComponentBWithDep(ComponentA depA) {
            this.depA = depA;
            this.id = "B-" + System.nanoTime();
        }
    }

    static class ComponentWithValCon {
        public String text;
        public int number;
        public boolean flag;
        public ComponentWithValCon(String text, int number, boolean flag) {
            this.text = text;
            this.number = number;
            this.flag = flag;
        }
    }

    static class ComponentWithMixedCon {
        public String textVal;
        public ComponentA compARef;
        public int intVal;
        public ComponentWithMixedCon(String textVal, ComponentA compARef, int intVal) {
            this.textVal = textVal;
            this.compARef = compARef;
            this.intVal = intVal;
        }
    }

    static class ComponentWithListVal {
        public List<String> namesList;
        public ComponentWithListVal(List<String> namesList) {
            this.namesList = namesList;
        }
    }

    static class ComponentWithListRef {
        public List<ComponentA> componentAList;
        public ComponentWithListRef(List<ComponentA> componentAList) {
            this.componentAList = componentAList;
        }
    }

    static class ComponentWithListMixed {
        public List<Object> mixedItemsList;
        public ComponentWithListMixed(List<Object> mixedItemsList){
            this.mixedItemsList = mixedItemsList;
        }
    }

    static class ComponentWithStaticFactoryNoArgs {
        public String identifier = "FactoryNoArgsInstance";
        private ComponentWithStaticFactoryNoArgs() {}
        public static ComponentWithStaticFactoryNoArgs createInstance() { // Renamed to avoid conflict if another 'create' exists
            return new ComponentWithStaticFactoryNoArgs();
        }
    }

    static class ComponentWithStaticFactoryWithArgs {
        public String stringArg;
        public ComponentA componentArg;
        public String identifier = "FactoryWithArgsInstance";
        private ComponentWithStaticFactoryWithArgs(String stringArg, ComponentA componentArg) {
            this.stringArg = stringArg;
            this.componentArg = componentArg;
        }
        public static ComponentWithStaticFactoryWithArgs createInstance(String stringArg, ComponentA componentArg) { // Renamed
            return new ComponentWithStaticFactoryWithArgs(stringArg, componentArg);
        }
    }

    static class ComponentWithStaticFactoryWithListArgs {
        public List<String> stringListArg;
        public List<ComponentA> componentListArg;
        private ComponentWithStaticFactoryWithListArgs(List<String> stringListArg, List<ComponentA> componentListArg) {
            this.stringListArg = stringListArg;
            this.componentListArg = componentListArg;
        }
        public static ComponentWithStaticFactoryWithListArgs createInstance(List<String> stringListArg, List<ComponentA> componentListArg) { // Renamed
            return new ComponentWithStaticFactoryWithListArgs(stringListArg, componentListArg);
        }
    }

    // For circular dependency tests
    static class CompX { // Renamed to avoid conflict with test method names
        CompY yDep;
        public CompX(CompY yDep) { this.yDep = yDep; }
    }
    static class CompY {
        CompZ zDep;
        CompX xDep;
        public CompY(CompX xDep) { this.xDep = xDep; } // Constructor for Direct circular: Y -> X
        public CompY(CompZ zDep) { this.zDep = zDep; } // Constructor for Indirect circular: Y -> Z
    }
    static class CompZ {
        CompX xDep;
        public CompZ(CompX xDep) { this.xDep = xDep; }
    }


    @BeforeEach
    void setUp() {
        componentDefinitions = new HashMap<>();
        // Pass a mutable copy of definitions to the injector
        injector = new DependencyInjector(new HashMap<>(componentDefinitions));
    }

    // Helper to add a component definition to the map used by the injector for current test
    private JSONObject defineComponent(String id, String className) {
        JSONObject def = new JSONObject();
        def.put("id", id);
        def.put("class", className);
        componentDefinitions.put(id, def); // Add to the map injector will use (via constructor)
        injector = new DependencyInjector(new HashMap<>(componentDefinitions)); // Re-initialize injector with new definitions
        return def;
    }

    private JSONObject defineComponentInMap(String id, String className, Map<String, JSONObject> definitionsMap) {
        JSONObject def = new JSONObject();
        def.put("id", id);
        def.put("class", className);
        definitionsMap.put(id, def);
        return def;
    }


    private JSONObject defineComponentWithFactory(String id, String className, String factoryMethod) {
        JSONObject def = defineComponent(id, className); // This re-initializes injector
        def.put("staticFactoryMethod", factoryMethod);
        // No need to re-init injector if def is modified in place and was already from componentDefinitions
        return def;
    }

    private JSONArray createArgsArray() { // Renamed for clarity
        return new JSONArray();
    }

    private JSONObject createValueArg(Object value, String type) {
        return new JSONObject().put("value", value).put("type", type);
    }

    private JSONObject createRefArg(String refId) {
        return new JSONObject().put("ref", refId);
    }

    private JSONObject createListArg(JSONArray listElementDefs) { // Renamed for clarity
        return new JSONObject().put("list", listElementDefs);
    }

    // --- Test Cases ---

    @Test
    void testRegisterAndGetComponent_simpleSingleton() {
        defineComponent("compA_id", "dumb.jaider.app.DependencyInjectorTest$ComponentA");

        ComponentA instance1 = (ComponentA) injector.getComponent("compA_id");
        assertNotNull(instance1);
        assertEquals("ComponentA_DefaultName", instance1.name);

        ComponentA instance2 = (ComponentA) injector.getComponent("compA_id");
        assertSame(instance1, instance2, "Singleton instance should be the same");
    }

    @Test
    void testGetComponent_notFound_throwsException() {
        // No components defined yet.
        assertThrows(ComponentNotFoundException.class, () -> {
            injector.getComponent("nonExistentId");
        });
    }

    @Test
    void testGetComponent_constructorWithDirectValues() {
        JSONObject definition = defineComponent("compVal_id", "dumb.jaider.app.DependencyInjectorTest$ComponentWithValCon");
        JSONArray ctorArgs = createArgsArray()
            .put(createValueArg("text value", "java.lang.String"))
            .put(createValueArg(99, "int"))
            .put(createValueArg(false, "boolean"));
        definition.put("constructorArgs", ctorArgs);
        injector = new DependencyInjector(new HashMap<>(componentDefinitions)); // re-init with updated def

        ComponentWithValCon instance = (ComponentWithValCon) injector.getComponent("compVal_id");
        assertNotNull(instance);
        assertEquals("text value", instance.text);
        assertEquals(99, instance.number);
        assertFalse(instance.flag);
    }

    @Test
    void testGetComponent_constructorWithMixedArgsRefAndValue() {
        defineComponent("compA_ref_id", "dumb.jaider.app.DependencyInjectorTest$ComponentA"); // Dependency
        JSONObject defMixed = defineComponent("compMixed_id", "dumb.jaider.app.DependencyInjectorTest$ComponentWithMixedCon");
        JSONArray ctorArgsMixed = createArgsArray()
            .put(createValueArg("mixed string", "java.lang.String"))
            .put(createRefArg("compA_ref_id"))
            .put(createValueArg(77, "int"));
        defMixed.put("constructorArgs", ctorArgsMixed);
        injector = new DependencyInjector(new HashMap<>(componentDefinitions));


        ComponentWithMixedCon instanceMixed = (ComponentWithMixedCon) injector.getComponent("compMixed_id");
        assertNotNull(instanceMixed);
        assertEquals("mixed string", instanceMixed.textVal);
        assertNotNull(instanceMixed.compARef);
        assertEquals("ComponentA_DefaultName", instanceMixed.compARef.name);
        assertEquals(77, instanceMixed.intVal);

        ComponentA instanceA = (ComponentA) injector.getComponent("compA_ref_id");
        assertSame(instanceA, instanceMixed.compARef, "Referenced dependency should be the same singleton instance");
    }

    @Test
    void testGetComponent_constructorWithRefDependencies() {
        defineComponent("compA_dep_id", "dumb.jaider.app.DependencyInjectorTest$ComponentA");
        JSONObject defB = defineComponent("compB_with_dep_id", "dumb.jaider.app.DependencyInjectorTest$ComponentBWithDep");
        JSONArray ctorArgsB = createArgsArray().put(createRefArg("compA_dep_id"));
        defB.put("constructorArgs", ctorArgsB);
        injector = new DependencyInjector(new HashMap<>(componentDefinitions));


        ComponentBWithDep instanceB = (ComponentBWithDep) injector.getComponent("compB_with_dep_id");
        assertNotNull(instanceB);
        assertNotNull(instanceB.depA);
        assertEquals("ComponentA_DefaultName", instanceB.depA.name);

        ComponentA instanceA = (ComponentA) injector.getComponent("compA_dep_id");
        assertSame(instanceA, instanceB.depA);
    }

    @Test
    void testGetComponent_constructorWithListOfValues() {
        JSONObject def = defineComponent("listValComp_id", "dumb.jaider.app.DependencyInjectorTest$ComponentWithListVal");
        JSONArray stringListJson = createArgsArray() // This is the list of JSONObjects for strings
            .put(createValueArg("Xyz", "java.lang.String"))
            .put(createValueArg("Abc", "java.lang.String"));
        JSONArray ctorArgs = createArgsArray().put(createListArg(stringListJson)); // The constructor takes one arg: the list itself
        def.put("constructorArgs", ctorArgs);
        injector = new DependencyInjector(new HashMap<>(componentDefinitions));

        ComponentWithListVal instance = (ComponentWithListVal) injector.getComponent("listValComp_id");
        assertNotNull(instance);
        assertNotNull(instance.namesList);
        assertEquals(2, instance.namesList.size());
        assertEquals("Xyz", instance.namesList.get(0));
        assertEquals("Abc", instance.namesList.get(1));
    }

    @Test
    void testGetComponent_constructorWithListOfRefs() {
        defineComponent("compA_for_list1", "dumb.jaider.app.DependencyInjectorTest$ComponentA");
        defineComponent("compA_for_list2", "dumb.jaider.app.DependencyInjectorTest$ComponentA");

        JSONObject def = defineComponent("listRefComp_id", "dumb.jaider.app.DependencyInjectorTest$ComponentWithListRef");
        JSONArray refListJson = createArgsArray() // List of refs
            .put(createRefArg("compA_for_list1"))
            .put(createRefArg("compA_for_list2"));
        JSONArray ctorArgs = createArgsArray().put(createListArg(refListJson)); // The constructor takes one arg: the list
        def.put("constructorArgs", ctorArgs);
        injector = new DependencyInjector(new HashMap<>(componentDefinitions));

        ComponentWithListRef instance = (ComponentWithListRef) injector.getComponent("listRefComp_id");
        assertNotNull(instance);
        assertNotNull(instance.componentAList);
        assertEquals(2, instance.componentAList.size());

        ComponentA compA1FromInjector = (ComponentA) injector.getComponent("compA_for_list1");
        ComponentA compA2FromInjector = (ComponentA) injector.getComponent("compA_for_list2");

        assertSame(compA1FromInjector, instance.componentAList.get(0));
        assertSame(compA2FromInjector, instance.componentAList.get(1));
        assertNotSame(instance.componentAList.get(0), instance.componentAList.get(1));
    }

    @Test
    void testGetComponent_constructorWithListOfMixedValuesAndRefs() {
        defineComponent("compA_for_mixed_list", "dumb.jaider.app.DependencyInjectorTest$ComponentA");
        JSONObject def = defineComponent("listMixedComp_id", "dumb.jaider.app.DependencyInjectorTest$ComponentWithListMixed");

        JSONArray mixedJsonElArray = createArgsArray()
            .put(createValueArg("First String", "java.lang.String"))
            .put(createRefArg("compA_for_mixed_list"))
            .put(createValueArg(2024, "int"));

        JSONArray ctorArgs = createArgsArray().put(createListArg(mixedJsonElArray));
        def.put("constructorArgs", ctorArgs);
        injector = new DependencyInjector(new HashMap<>(componentDefinitions));

        ComponentWithListMixed instance = (ComponentWithListMixed) injector.getComponent("listMixedComp_id");
        assertNotNull(instance);
        assertNotNull(instance.mixedItemsList);
        assertEquals(3, instance.mixedItemsList.size());
        assertEquals("First String", instance.mixedItemsList.get(0));
        assertTrue(instance.mixedItemsList.get(1) instanceof ComponentA);
        assertSame(injector.getComponent("compA_for_mixed_list"), instance.mixedItemsList.get(1));
        assertEquals(2024, instance.mixedItemsList.get(2));
    }

    @Test
    void testGetComponent_staticFactoryMethod_noArgs() {
        defineComponentWithFactory("factoryNoArgs_id", "dumb.jaider.app.DependencyInjectorTest$ComponentWithStaticFactoryNoArgs", "createInstance");
        // injector re-init is handled by defineComponentWithFactory -> defineComponent
        ComponentWithStaticFactoryNoArgs instance = (ComponentWithStaticFactoryNoArgs) injector.getComponent("factoryNoArgs_id");
        assertNotNull(instance);
        assertEquals("FactoryNoArgsInstance", instance.identifier);
    }

    @Test
    void testGetComponent_staticFactoryMethod_withArgs() {
        defineComponent("compA_for_factory_id", "dumb.jaider.app.DependencyInjectorTest$ComponentA");
        JSONObject def = defineComponentWithFactory("factoryWithArgs_id", "dumb.jaider.app.DependencyInjectorTest$ComponentWithStaticFactoryWithArgs", "createInstance");
        JSONArray factoryMethodArgs = createArgsArray()
            .put(createValueArg("ValueForFactory", "java.lang.String"))
            .put(createRefArg("compA_for_factory_id"));
        def.put("staticFactoryArgs", factoryMethodArgs);
        injector = new DependencyInjector(new HashMap<>(componentDefinitions));


        ComponentWithStaticFactoryWithArgs instance = (ComponentWithStaticFactoryWithArgs) injector.getComponent("factoryWithArgs_id");
        assertNotNull(instance);
        assertEquals("ValueForFactory", instance.stringArg);
        assertNotNull(instance.componentArg);
        assertSame(injector.getComponent("compA_for_factory_id"), instance.componentArg);
        assertEquals("FactoryWithArgsInstance", instance.identifier);
    }

    @Test
    void testGetComponent_staticFactoryMethod_withListArgs() {
        defineComponent("compA_factory_list1", "dumb.jaider.app.DependencyInjectorTest$ComponentA");
        defineComponent("compA_factory_list2", "dumb.jaider.app.DependencyInjectorTest$ComponentA");

        JSONObject def = defineComponentWithFactory("factoryListArgs_id",
            "dumb.jaider.app.DependencyInjectorTest$ComponentWithStaticFactoryWithListArgs", "createInstance");

        JSONArray factoryStringListJson = createArgsArray()
            .put(createValueArg("str1", "java.lang.String"))
            .put(createValueArg("str2", "java.lang.String"));

        JSONArray factoryRefListJson = createArgsArray()
            .put(createRefArg("compA_factory_list1"))
            .put(createRefArg("compA_factory_list2"));

        JSONArray factoryMethodArgs = createArgsArray()
            .put(createListArg(factoryStringListJson))
            .put(createListArg(factoryRefListJson));
        def.put("staticFactoryArgs", factoryMethodArgs);
        injector = new DependencyInjector(new HashMap<>(componentDefinitions));

        ComponentWithStaticFactoryWithListArgs instance = (ComponentWithStaticFactoryWithListArgs) injector.getComponent("factoryListArgs_id");
        assertNotNull(instance);
        assertEquals(2, instance.stringListArg.size());
        assertEquals("str1", instance.stringListArg.get(0));
        assertEquals(2, instance.componentListArg.size());
        assertSame(injector.getComponent("compA_factory_list1"), instance.componentListArg.get(0));
        assertSame(injector.getComponent("compA_factory_list2"), instance.componentListArg.get(1));
    }

    @Test
    void testGetComponent_invalidConfiguration_classNotFound() {
        defineComponent("invalidClass_id", "dumb.jaider.app.NonExistentClassName");
        assertThrows(ComponentInstantiationException.class, () -> {
            injector.getComponent("invalidClass_id");
        });
    }

    @Test
    void testGetComponent_invalidConfiguration_methodNotFound() {
        defineComponentWithFactory("invalidMethod_id", "dumb.jaider.app.DependencyInjectorTest$ComponentA", "nonExistentFactoryName");
         assertThrows(ComponentInstantiationException.class, () -> {
            injector.getComponent("invalidMethod_id");
        });
    }

    @Test
    void testGetComponent_invalidConfiguration_staticFactoryMethodArgMismatch_type() {
        defineComponent("compA_type_mismatch", "dumb.jaider.app.DependencyInjectorTest$ComponentA");
        JSONObject def = defineComponentWithFactory("factoryTypeMismatch_id", "dumb.jaider.app.DependencyInjectorTest$ComponentWithStaticFactoryWithArgs", "createInstance");
        JSONArray factoryArgs = createArgsArray()
            .put(createValueArg(789, "int")) // Factory expects String, ComponentA
            .put(createRefArg("compA_type_mismatch"));
        def.put("staticFactoryArgs", factoryArgs);
        injector = new DependencyInjector(new HashMap<>(componentDefinitions));

        Exception ex = assertThrows(ComponentInstantiationException.class, () -> {
            injector.getComponent("factoryTypeMismatch_id");
        });
        assertTrue(ex.getMessage().toLowerCase().contains("argument type mismatch") || ex.getMessage().toLowerCase().contains("no suitable method found for id: factorytypemismatch_id with arguments: [java.lang.integer, dumb.jaider.app.dependencyinjectortest$componenta]"));
    }

    @Test
    void testGetComponent_invalidConfiguration_constructorArgMismatch_type() {
        JSONObject def = defineComponent("ctorTypeMismatch_id", "dumb.jaider.app.DependencyInjectorTest$ComponentWithValCon");
        JSONArray ctorArgs = createArgsArray() // Expects (String, int, boolean)
            .put(createValueArg(true, "boolean")) // Wrong type for first arg
            .put(createValueArg(321, "int"))
            .put(createValueArg(true, "boolean"));
        def.put("constructorArgs", ctorArgs);
        injector = new DependencyInjector(new HashMap<>(componentDefinitions));

        Exception ex = assertThrows(ComponentInstantiationException.class, () -> {
            injector.getComponent("ctorTypeMismatch_id");
        });
        assertTrue(ex.getMessage().toLowerCase().contains("argument type mismatch") || ex.getMessage().toLowerCase().contains("no suitable constructor found for id: ctortypemismatch_id with arguments: [java.lang.boolean, java.lang.integer, java.lang.boolean]"));
    }

    @Test
    void testGetComponent_invalidConfiguration_constructorArgMismatch_count() {
        JSONObject def = defineComponent("ctorArgCountMismatch_id", "dumb.jaider.app.DependencyInjectorTest$ComponentWithValCon");
        JSONArray ctorArgs = createArgsArray() // Expects 3 args
            .put(createValueArg("short string", "java.lang.String")); // Only one arg
        def.put("constructorArgs", ctorArgs);
        injector = new DependencyInjector(new HashMap<>(componentDefinitions));

        Exception ex = assertThrows(ComponentInstantiationException.class, () -> {
            injector.getComponent("ctorArgCountMismatch_id");
        });
        assertTrue(ex.getMessage().toLowerCase().contains("argument list differs in length") || ex.getMessage().toLowerCase().contains("no suitable constructor found"));
    }

    @Test
    void testCircularDependency_direct() {
        Map<String, JSONObject> circularDefs = new HashMap<>();
        JSONObject defX = defineComponentInMap("compX_circular_id", "dumb.jaider.app.DependencyInjectorTest$CompX", circularDefs);
        defX.put("constructorArgs", createArgsArray().put(createRefArg("compY_circular_id"))); // X -> Y

        JSONObject defY = defineComponentInMap("compY_circular_id", "dumb.jaider.app.DependencyInjectorTest$CompY", circularDefs);
        // This CompY constructor must be CompY(CompX x)
        defY.put("constructorArgs", createArgsArray().put(createRefArg("compX_circular_id"))); // Y -> X
        injector = new DependencyInjector(circularDefs);

        assertThrows(CircularDependencyException.class, () -> {
            injector.getComponent("compX_circular_id");
        });
         assertThrows(CircularDependencyException.class, () -> {
            injector.getComponent("compY_circular_id");
        });
    }

    @Test
    void testCircularDependency_indirect() {
        Map<String, JSONObject> indirectCircularDefs = new HashMap<>();
        JSONObject defX = defineComponentInMap("compX_indirect_id", "dumb.jaider.app.DependencyInjectorTest$CompX", indirectCircularDefs);
        defX.put("constructorArgs", createArgsArray().put(createRefArg("compY_indirect_id"))); // X -> Y

        JSONObject defY = defineComponentInMap("compY_indirect_id", "dumb.jaider.app.DependencyInjectorTest$CompY", indirectCircularDefs);
        // This CompY constructor must be CompY(CompZ z) for indirect
        defY.put("constructorArgs", createArgsArray().put(createRefArg("compZ_indirect_id"))); // Y -> Z

        JSONObject defZ = defineComponentInMap("compZ_indirect_id", "dumb.jaider.app.DependencyInjectorTest$CompZ", indirectCircularDefs);
        defZ.put("constructorArgs", createArgsArray().put(createRefArg("compX_indirect_id"))); // Z -> X
        injector = new DependencyInjector(indirectCircularDefs);

        assertThrows(CircularDependencyException.class, () -> {
            injector.getComponent("compX_indirect_id");
        });
    }

    @Test
    void testClearCache_componentsRecreated() {
        defineComponent("compA_cache_test_id", "dumb.jaider.app.DependencyInjectorTest$ComponentA");

        ComponentA instanceOne = (ComponentA) injector.getComponent("compA_cache_test_id");
        injector.clearCache();
        ComponentA instanceTwo = (ComponentA) injector.getComponent("compA_cache_test_id");

        assertNotNull(instanceOne);
        assertNotNull(instanceTwo);
        assertNotSame(instanceOne, instanceTwo, "Instances should be different after cache clear if re-fetched from definitions.");
    }

    @Test
    void testRegisterSingleton_instanceAlreadyCreated() {
        ComponentA manualExternalInstance = new ComponentA();
        manualExternalInstance.name = "ManuallyRegisteredExternalInstance";

        // Injector starts empty for this test of registerSingleton
        injector = new DependencyInjector(new HashMap<>());
        injector.registerSingleton("manualCompA_id", manualExternalInstance);

        ComponentA retrievedInst = (ComponentA) injector.getComponent("manualCompA_id");
        assertSame(manualExternalInstance, retrievedInst);
        assertEquals("ManuallyRegisteredExternalInstance", retrievedInst.name);

        ComponentA retrievedInst2 = (ComponentA) injector.getComponent("manualCompA_id");
        assertSame(manualExternalInstance, retrievedInst2); // Should still be the same (singleton)

        injector.clearCache();
        // Manually registered singletons (not from JSON defs) should persist if clearCache only affects JSON-defined components.
        // However, the current implementation of clearCache simply clears the 'cache' map.
        // If registerSingleton adds to this same cache, then it would be cleared.
        // Let's test the behavior:
        ComponentA retrievedAfterClear = (ComponentA) injector.getComponent("manualCompA_id");
        assertSame(manualExternalInstance, retrievedAfterClear, "Manually registered singleton should ideally survive a simple cache clear if it's not re-instantiable from definitions.");
        // The above assertion depends on how registerSingleton and clearCache interact.
        // If registerSingleton relies on the same 'cache' that is cleared, then this might fail.
        // A robust DI might have separate storage or handling for programmatically registered singletons vs definition-derived ones.
        // Given the current DI code, it's likely cleared. Let's adjust the expectation:
        // If clearCache wipes all, then it should throw ComponentNotFoundException if not also in definitions.
        // The current DependencyInjector.clearCache() just clears the 'cache' map.
        // So, a manually registered instance, if not also in definitions, will be gone.
        assertThrows(ComponentNotFoundException.class, () -> {
            injector = new DependencyInjector(new HashMap<>()); // Fresh injector without the manual registration
            injector.getComponent("manualCompA_id");
        }, "Component should not be found in a fresh injector.");


        // Test interaction: define, then registerSingleton (override), then clearCache
        Map<String, JSONObject> defsForOverride = new HashMap<>();
        defineComponentInMap("override_id", "dumb.jaider.app.DependencyInjectorTest$ComponentA", defsForOverride);
        injector = new DependencyInjector(defsForOverride);

        ComponentA jsonDefinedInstance = (ComponentA) injector.getComponent("override_id"); // Created from JSON
        jsonDefinedInstance.name = "JSON_Version";

        ComponentA manualOverrideInstance = new ComponentA();
        manualOverrideInstance.name = "ManualOverride_Version";
        injector.registerSingleton("override_id", manualOverrideInstance); // Override with manual instance

        ComponentA currentInstance = (ComponentA) injector.getComponent("override_id");
        assertSame(manualOverrideInstance, currentInstance, "Should be the manually overridden instance.");
        assertEquals("ManualOverride_Version", currentInstance.name);

        injector.clearCache();
        ComponentA afterClearInstance = (ComponentA) injector.getComponent("override_id"); // Should recreate from JSON
        assertNotSame(manualOverrideInstance, afterClearInstance, "After clear, should be a new instance from JSON def.");
        assertEquals("ComponentA_DefaultName", afterClearInstance.name, "Name should be from default constructor of new instance.");
    }
}
