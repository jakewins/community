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
package org.neo4j.kernel.configuration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.neo4j.graphdb.factory.Migrator;
import org.neo4j.kernel.logging.BufferingLogger;
import org.neo4j.kernel.logging.StringLogger;


public class TestAnnotationBasedConfigurationMigrator {

    public static class ASettingsClass
    {
        @Migrator
        public static ConfigurationMigrator migrator = new ConfigurationMigrator() 
        {    
            @Override
            public Map<String, String> apply(Map<String, String> rawConfiguration,
                    StringLogger log)
            {
                rawConfiguration.put("HOH", "HAH");
                return rawConfiguration;
            }
        };
    }
    
    public static class AnotherSettingsClass
    {
        @Migrator
        public static ConfigurationMigrator migrator = new ConfigurationMigrator() 
        {    
            @Override
            public Map<String, String> apply(Map<String, String> rawConfiguration,
                    StringLogger log)
            {
                rawConfiguration.put("HAH", "HOH");
                return rawConfiguration;
            }
        };
    }
    
    @Test
    public void shouldApplyAllMigratorsFound() 
    {
        ConfigurationMigrator m = new AnnotationBasedConfigurationMigrator(new ArrayList<Class<?>>(){{
            add(ASettingsClass.class);
            add(AnotherSettingsClass.class);
        }});
        
        Map<String,String> migrated = m.apply(new HashMap<String,String>(), new BufferingLogger());
        
        assertThat(migrated.containsKey("HOH"), is(true));
        assertThat(migrated.containsKey("HAH"), is(true));
        
        assertThat(migrated.get("HOH"), equalTo("HAH"));
        assertThat(migrated.get("HAH"), equalTo("HOH"));
    }
    
}
