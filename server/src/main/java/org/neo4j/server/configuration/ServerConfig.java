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
import java.util.Map;

import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.configuration.SystemPropertiesConfiguration;

/**
 * Create server configuration
 * with defaults set from the ServerSettings classes, and
 * loading settings from system properties as well as 
 * provided properties.
 */
public class ServerConfig {

    public static Config fromFile(String path) throws IOException
    {
        return fromFile(new File(path));
    }
    
    public static Config fromFile(File path) throws IOException
    {
        return fromMap(MapUtil.load(path));
    }
    
    public static Config fromMap(Map<String,String> props)
    {
        props = new SystemPropertiesConfiguration(ServerSettings.class).apply( props );
        return new Config( props, ServerSettings.class );
    }

}
