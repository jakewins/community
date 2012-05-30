/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.configuration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.Configuration;
import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.configuration.SystemPropertiesConfiguration;

/**
 * Wraps a Neo4j Config instance such that it adheres to 
 * the configurator and/or configuration interfaces previously
 * used by the server.
 * 
 * This is here for backwards compatibility, will be removed in version 1.10
 */
@Deprecated
public class ConfiguratorWrappedConfig extends AbstractConfiguration implements Configurator {

    @Deprecated
    public static Config configFromConfigurator(Configurator configurator, Class<?> ... settingsClasses) 
    {
        Map<String, String> params = new HashMap<String,String>();
        @SuppressWarnings("unchecked")
        Iterator<String> keys = configurator.configuration().getKeys();
        while(keys.hasNext())
        {
            String key = keys.next();
            params.put(key, configurator.configuration().getString(key));
        }
        return new Config(new SystemPropertiesConfiguration(settingsClasses).apply(params), settingsClasses);
    }
    
    private Config config;

    public ConfiguratorWrappedConfig(Config config)
    {
        this.config = config;
    }
    
    @Override
    public Configuration configuration()
    {
        return this; // In your codes, unseparating your concerns.
    }

    @Override
    public Map<String, String> getDatabaseTuningProperties()
    {
        File neo4jProperties = new File(config.get(ServerSettings.db_tuning_property_file));
        try
        {
            return MapUtil.load(neo4jProperties);
        } catch (IOException e)
        {
            return new HashMap<String, String>();
        }
    }

    @Override
    public Set<ThirdPartyJaxRsPackage> getThirdpartyJaxRsClasses()
    {
        return new HashSet<ThirdPartyJaxRsPackage>(config.get(ServerSettings.third_party_packages));
    }

    // Configuration methods
    
    @Override
    public boolean isEmpty()
    {
        return config.getKeys().size() == 0;
    }

    @Override
    public boolean containsKey(String key)
    {
        return config.isSet(new GraphDatabaseSetting.StringSetting(key,"",""));
    }

    @Override
    public Object getProperty(String key)
    {
        return config.get(new GraphDatabaseSetting.StringSetting(key,"",""));
    }

    @Override
    public Iterator<String> getKeys()
    {
        return config.getKeys().iterator();
    }

    @Override
    protected void addPropertyDirect(String key, Object value)
    {
        config.set(new GraphDatabaseSetting.StringSetting(key,"","") , value.toString());
    }
}
