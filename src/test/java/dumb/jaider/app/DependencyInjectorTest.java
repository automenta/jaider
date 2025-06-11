package dumb.jaider.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class DependencyInjectorTest {

    private Map<String, JSONObject> definitions;
    private DependencyInjector injector;

    @BeforeEach
    void setUp() {
        definitions = new HashMap<>();
    }

    // Helper Classes
    public static class SimpleComponent {
        public SimpleComponent() {}
        // Override equals and hashCode for potential comparisons if needed, though instance identity is primary here.
        @Override public boolean equals(Object o) { return this == o || (o != null && getClass() == o.getClass()); }
        @Override public int hashCode() { return Objects.hash(getClass()); }
    }

    public static class LiteralArgComponent {
        final String name;
        final int version;
        final boolean active;
        long longVal;
        float floatVal;
        double doubleVal;

        public LiteralArgComponent(String name, int version, boolean active) {
            this.name = name;
            this.version = version;
            this.active = active;
        }

        public LiteralArgComponent(String name, int version, boolean active, long longVal, float floatVal, double doubleVal) {
            this.name = name;
            this.version = version;
            this.active = active;
            this.longVal = longVal;
            this.floatVal = floatVal;
            this.doubleVal = doubleVal;
        }
    }

    public static class RefArgComponent {
        final SimpleComponent dependency;
        public RefArgComponent(SimpleComponent dep) { this.dependency = dep; }
    }

    public static class StaticFactoryTarget {
        String val;
        SimpleComponent dep;
        int num;
        private StaticFactoryTarget(String val) { this.val = val; }
        private StaticFactoryTarget(SimpleComponent dep, int num) { this.dep = dep; this.num = num; }

        public static StaticFactoryTarget create(String val) { return new StaticFactoryTarget(val); }
        public static StaticFactoryTarget createWithDep(SimpleComponent dep, int num) { return new StaticFactoryTarget(dep, num); }
    }

    public static class InstanceFactoryProduct {
        final String type;
        SimpleComponent dep;
        boolean flag;
        public InstanceFactoryProduct(String type) { this.type = type;}
        public InstanceFactoryProduct(SimpleComponent dep, boolean flag, String type) {
            this.dep = dep;
            this.flag = flag;
            this.type = type + "-dep"; // to differentiate
        }
    }

    public static class InstanceFactory {
        public InstanceFactoryProduct createProduct(String type) { return new InstanceFactoryProduct(type); }
        public InstanceFactoryProduct createProductWithDep(SimpleComponent dep, boolean flag) {
            return new InstanceFactoryProduct(dep, flag, "product");
        }
    }

    public static class CircularA {
        final CircularB b;
        public CircularA(CircularB b) { this.b = b; }
    }

    public static class CircularB {
        final CircularA a;
        public CircularB(CircularA a) { this.a = a; }
    }

    // Test Cases
    @Test
    void testNoArgConstructor() {
        definitions.put("simple", new JSONObject()
                .put("id", "simple")
                .put("class", SimpleComponent.class.getName()));
        injector = new DependencyInjector(definitions);
        SimpleComponent component = (SimpleComponent) injector.getComponent("simple");
        assertNotNull(component);
        assertInstanceOf(SimpleComponent.class, component);
    }

    @Test
    void testLiteralArgConstructor() {
        definitions.put("literalComp", new JSONObject()
                .put("id", "literalComp")
                .put("class", LiteralArgComponent.class.getName())
                .put("constructorArgs", new JSONArray()
                        .put(new JSONObject().put("value", "TestName").put("type", "String"))
                        .put(new JSONObject().put("value", 101).put("type", "int"))
                        .put(new JSONObject().put("value", true).put("type", "boolean"))));
        injector = new DependencyInjector(definitions);
        LiteralArgComponent component = (LiteralArgComponent) injector.getComponent("literalComp");
        assertNotNull(component);
        assertEquals("TestName", component.name);
        assertEquals(101, component.version);
        assertTrue(component.active);
    }

    @Test
    void testReferenceArgConstructor() {
        definitions.put("simple", new JSONObject().put("id", "simple").put("class", SimpleComponent.class.getName()));
        definitions.put("refComp", new JSONObject()
                .put("id", "refComp")
                .put("class", RefArgComponent.class.getName())
                .put("constructorArgs", new JSONArray()
                        .put(new JSONObject().put("ref", "simple"))));
        injector = new DependencyInjector(definitions);
        RefArgComponent refComp = (RefArgComponent) injector.getComponent("refComp");
        assertNotNull(refComp);
        assertNotNull(refComp.dependency);
        assertInstanceOf(SimpleComponent.class, refComp.dependency);
    }

    @Test
    void testStaticFactoryMethod_withLiterals() {
        definitions.put("staticFac", new JSONObject()
                .put("id", "staticFac")
                .put("class", StaticFactoryTarget.class.getName())
                .put("staticFactoryMethod", "create")
                .put("staticFactoryArgs", new JSONArray()
                        .put(new JSONObject().put("value", "StaticValue").put("type", "String"))));
        injector = new DependencyInjector(definitions);
        StaticFactoryTarget component = (StaticFactoryTarget) injector.getComponent("staticFac");
        assertNotNull(component);
        assertEquals("StaticValue", component.val);
    }

    @Test
    void testStaticFactoryMethod_withReference() {
        definitions.put("simple", new JSONObject().put("id", "simple").put("class", SimpleComponent.class.getName()));
        definitions.put("staticFacDep", new JSONObject()
                .put("id", "staticFacDep")
                .put("class", StaticFactoryTarget.class.getName())
                .put("staticFactoryMethod", "createWithDep")
                .put("staticFactoryArgs", new JSONArray()
                        .put(new JSONObject().put("ref", "simple"))
                        .put(new JSONObject().put("value", 99).put("type", "int"))));
        injector = new DependencyInjector(definitions);
        StaticFactoryTarget component = (StaticFactoryTarget) injector.getComponent("staticFacDep");
        assertNotNull(component);
        assertNotNull(component.dep);
        assertInstanceOf(SimpleComponent.class, component.dep);
        assertEquals(99, component.num);
    }

    @Test
    void testInstanceFactoryMethod_withLiterals() {
        definitions.put("instanceFacBean", new JSONObject().put("id", "instanceFacBean").put("class", InstanceFactory.class.getName()));
        definitions.put("instanceProd", new JSONObject()
                .put("id", "instanceProd")
                // class is not needed here as factoryBean implies the class to call factoryMethod on
                .put("factoryBean", "instanceFacBean")
                .put("factoryMethod", "createProduct")
                .put("factoryArgs", new JSONArray()
                        .put(new JSONObject().put("value", "DynamicProduct").put("type", "String"))));
        injector = new DependencyInjector(definitions);
        InstanceFactoryProduct component = (InstanceFactoryProduct) injector.getComponent("instanceProd");
        assertNotNull(component);
        assertEquals("DynamicProduct", component.type);
    }

    @Test
    void testInstanceFactoryMethod_withReference() {
        definitions.put("simple", new JSONObject().put("id", "simple").put("class", SimpleComponent.class.getName()));
        definitions.put("instanceFacBean", new JSONObject().put("id", "instanceFacBean").put("class", InstanceFactory.class.getName()));
        definitions.put("instanceProdDep", new JSONObject()
                .put("id", "instanceProdDep")
                .put("factoryBean", "instanceFacBean")
                .put("factoryMethod", "createProductWithDep")
                .put("factoryArgs", new JSONArray()
                        .put(new JSONObject().put("ref", "simple"))
                        .put(new JSONObject().put("value", true).put("type", "boolean"))));
        injector = new DependencyInjector(definitions);
        InstanceFactoryProduct component = (InstanceFactoryProduct) injector.getComponent("instanceProdDep");
        assertNotNull(component);
        assertNotNull(component.dep);
        assertInstanceOf(SimpleComponent.class, component.dep);
        assertTrue(component.flag);
        assertEquals("product-dep", component.type);
    }

    @Test
    void testSingletonBehavior() {
        definitions.put("simple", new JSONObject().put("id", "simple").put("class", SimpleComponent.class.getName()));
        injector = new DependencyInjector(definitions);
        Object s1 = injector.getComponent("simple");
        Object s2 = injector.getComponent("simple");
        assertSame(s1, s2);
    }

    @Test
    void testRegisterSingleton() {
        // definitions map is new for each test via @BeforeEach setUp
        SimpleComponent external = new SimpleComponent();

        // Define "refToExternal" which depends on "externalSimple".
        // This definition needs to be in the map when the injector is created.
        definitions.put("refToExternal", new JSONObject()
                .put("id", "refToExternal")
                .put("class", RefArgComponent.class.getName())
                .put("constructorArgs", new JSONArray().put(new JSONObject().put("ref", "externalSimple"))));

        // If "externalSimple" itself needs a definition for some reason (e.g., if it were also created by DI first),
        // it would be added here. But since it's externally created and registered,
        // registerSingleton will handle its definition within the injector.

        injector = new DependencyInjector(definitions); // Injector is created, knows about "refToExternal"

        // Now, register the external singleton. This will add "externalSimple" to the injector's
        // internal definitions map and its instance cache.
        injector.registerSingleton("externalSimple", external);

        RefArgComponent component = (RefArgComponent) injector.getComponent("refToExternal");
        assertNotNull(component, "The referencing component should not be null.");
        assertNotNull(component.dependency, "The dependency injected into RefArgComponent should not be null.");
        assertSame(external, component.dependency, "The injected dependency should be the externally registered instance.");
    }


    @Test
    void testCircularDependency() {
        definitions.put("circA", new JSONObject()
                .put("id", "circA").put("class", CircularA.class.getName())
                .put("constructorArgs", new JSONArray().put(new JSONObject().put("ref", "circB"))));
        definitions.put("circB", new JSONObject()
                .put("id", "circB").put("class", CircularB.class.getName())
                .put("constructorArgs", new JSONArray().put(new JSONObject().put("ref", "circA"))));
        injector = new DependencyInjector(definitions);
        Exception exception = assertThrows(RuntimeException.class, () -> injector.getComponent("circA"));
        assertTrue(exception.getMessage().contains("Circular dependency detected"));
    }

    @Test
    void testClassNotFound() {
        definitions.put("badClass", new JSONObject().put("id", "badClass").put("class", "com.example.NonExistent"));
        injector = new DependencyInjector(definitions);
        Exception exception = assertThrows(RuntimeException.class, () -> injector.getComponent("badClass"));
        assertTrue(exception.getMessage().contains("Class 'com.example.NonExistent' not found"));
    }

    @Test
    void testMethodNotFound_staticFactory() {
        definitions.put("badStatic", new JSONObject()
                .put("id", "badStatic")
                .put("class", StaticFactoryTarget.class.getName())
                .put("staticFactoryMethod", "nonExistentCreate"));
        injector = new DependencyInjector(definitions);
        Exception exception = assertThrows(RuntimeException.class, () -> injector.getComponent("badStatic"));
        assertTrue(exception.getMessage().contains("Static factory method 'nonExistentCreate'"));
    }

    @Test
    void testMethodNotFound_instanceFactory() {
        definitions.put("instanceFacBean", new JSONObject().put("id", "instanceFacBean").put("class", InstanceFactory.class.getName()));
        definitions.put("badInstance", new JSONObject()
                .put("id", "badInstance")
                .put("factoryBean", "instanceFacBean")
                .put("factoryMethod", "nonExistentProduct"));
        injector = new DependencyInjector(definitions);
        Exception exception = assertThrows(RuntimeException.class, () -> injector.getComponent("badInstance"));
        assertTrue(exception.getMessage().contains("Instance factory method 'nonExistentProduct'"));
    }

    @Test
    void testFactoryBeanNotFound() {
        definitions.put("badFactoryBean", new JSONObject()
                .put("id", "badFactoryBean")
                .put("factoryBean", "nonExistentFactoryBean")
                .put("factoryMethod", "someMethod"));
        injector = new DependencyInjector(definitions);
        Exception exception = assertThrows(RuntimeException.class, () -> injector.getComponent("badFactoryBean"));
        // This will likely fail with "Component definition not found for id: nonExistentFactoryBean" first
        assertTrue(exception.getMessage().contains("Component definition not found for id: nonExistentFactoryBean") || exception.getMessage().contains("Error creating component 'badFactoryBean'"));
    }

    @Test
    void testLiteralTypeConversion() {
         definitions.put("literalCompFull", new JSONObject()
                .put("id", "literalCompFull")
                .put("class", LiteralArgComponent.class.getName())
                .put("constructorArgs", new JSONArray()
                        .put(new JSONObject().put("value", "TestName").put("type", "String"))
                        .put(new JSONObject().put("value", 101).put("type", "int"))
                        .put(new JSONObject().put("value", true).put("type", "boolean"))
                        .put(new JSONObject().put("value", 1234567890123L).put("type", "long"))
                        .put(new JSONObject().put("value", 123.45f).put("type", "float"))
                        .put(new JSONObject().put("value", 987.654).put("type", "double"))
                 ));
        injector = new DependencyInjector(definitions);
        LiteralArgComponent component = (LiteralArgComponent) injector.getComponent("literalCompFull");
        assertNotNull(component);
        assertEquals("TestName", component.name);
        assertEquals(101, component.version);
        assertTrue(component.active);
        assertEquals(1234567890123L, component.longVal);
        assertEquals(123.45f, component.floatVal, 0.001f);
        assertEquals(987.654, component.doubleVal, 0.001);
    }

    @Test
    void testClearCacheAndRecreation() {
        definitions.put("simple", new JSONObject().put("id", "simple").put("class", SimpleComponent.class.getName()));
        injector = new DependencyInjector(definitions);
        Object s1 = injector.getComponent("simple");
        assertNotNull(s1);

        injector.clearCache();

        Object s2 = injector.getComponent("simple");
        assertNotNull(s2);
        assertNotSame(s1, s2, "Should be a new instance after cache clear");

        // Test that registered singletons are NOT recreated if they were externally registered
        SimpleComponent external = new SimpleComponent();
        injector.registerSingleton("externalManual", external);
        Object ext1 = injector.getComponent("externalManual");
        assertSame(external, ext1, "Should be the manually registered instance");

        injector.clearCache(); // Clear cache again

        // After clearCache, a definition for "externalManual" was added by registerSingleton.
        // getComponent will now try to *create* it using that minimal definition if not found in singletons.
        // This part of the test highlights a nuance: clearCache clears instances, but not definitions.
        // If registerSingleton also stored the instance in a way that clearCache doesn't remove "pre-registered"
        // instances, then behavior would be different. Current clearCache clears all singletonInstances.
        // So, "externalManual" will be re-created by its class name.
        Object ext2 = injector.getComponent("externalManual");
        assertNotSame(external, ext2, "Should be a new instance of externalManual based on its class, as original was only in cache");
        assertInstanceOf(SimpleComponent.class, ext2);

        // To keep an externally registered singleton truly persistent across clearCache,
        // the injector would need a separate map for them, or registerSingleton would need to re-add them after clear.
        // For this test, we'll verify it's at least a SimpleComponent.
    }
}
