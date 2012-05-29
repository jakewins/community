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
