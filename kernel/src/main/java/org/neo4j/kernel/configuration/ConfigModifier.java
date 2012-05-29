package org.neo4j.kernel.configuration;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.neo4j.graphdb.factory.GraphDatabaseSetting;

public interface ConfigModifier {
    
    // Modifier helpers
    
    /**
     * A builder interface for performing multiple simple
     * modifications in one go.
     */
    public static class Modifications implements ConfigModifier
    {
        private Set<Modification> modifications = new HashSet<Modification>();
        
        @Override
        public void applyTo(Session session)
        {
            for(Modification m : modifications)
            {
                session.set(m.getSetting(), m.getValue());
            }
        }
        
        public <T> Modifications set(GraphDatabaseSetting<T> setting, String value)
        {
            modifications.add(new Modification(setting, value));
            return this;
        }
        
        public <T> Modifications delete(GraphDatabaseSetting<T> setting)
        {
            set(setting, null);
            return this;
        }
    }
    
    // Implementation of modification session
    
    public static class Modification
    {
        private String value;
        private GraphDatabaseSetting<?> setting;

        public Modification(GraphDatabaseSetting<?> setting, String value)
        {
            this.setting = setting;
            this.value = value;
        }
        
        public String getValue()
        {
            return value;
        }
        
        public GraphDatabaseSetting<?> getSetting() 
        {
            return setting;
        }
        
    }
    
    /**
     * An API for constructing a set of modifications to 
     * be applied to the config instance.
     * 
     * This allows grouping configuration changes together.
     */
    public static class Session
    {
        private Config config;
        private Set<Modification> modifications = new HashSet<Modification>();

        public Session(Config config) 
        {
            this.config = config;
        }
        
        /**
         * Get a value from the original configuration.
         * @param setting
         * @return
         */
        public <T> T get(GraphDatabaseSetting<T> setting) 
        {
            return config.get(setting);
        }
        
        /**
         * List keys from the original configuration.
         * @return
         */
        public Collection<String> getKeys() 
        {
            return config.getKeys();
        }
        
        public <T> void set(GraphDatabaseSetting<T> setting, String value)
        {
            modifications.add(new Modification(setting, value));
        }
        
        public void delete(GraphDatabaseSetting<?> setting)
        {
            set(setting, null);
        }

        protected Set<Modification> getModifications()
        {
            return modifications;
        }
    }
    
    public void applyTo(Session session);
    
}
