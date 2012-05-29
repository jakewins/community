package org.neo4j.kernel;

import java.util.HashMap;
import java.util.Map;

import org.neo4j.graphdb.DependencyResolver;

public class MapBackedDependencyResolver implements DependencyResolver {
    
    private Map<Class<?>, Object> components = new HashMap<Class<?>, Object>();
    
    public void register(Object component)
    {
        components.put(component.getClass(), component);
    }
    
    public void unregister(Class<?> type)
    {
        components.remove(type);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T resolveDependency(Class<T> type)
            throws IllegalArgumentException
    {
        if(components.containsKey(type)) {
            return (T)components.get(type);
        } else {
            throw new IllegalArgumentException("Sorry, no one has registered a component with the class '" + type.getCanonicalName() + "' with this dependency resolver.");
        }
    }

}
