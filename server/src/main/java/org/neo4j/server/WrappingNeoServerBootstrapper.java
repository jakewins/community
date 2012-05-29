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
package org.neo4j.server;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.configuration.SystemPropertiesConfiguration;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.configuration.ConfiguratorWrappedConfig;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.logging.Logger;
import org.neo4j.server.modules.DiscoveryModule;
import org.neo4j.server.modules.ManagementApiModule;
import org.neo4j.server.modules.RESTApiModule;
import org.neo4j.server.modules.ServerModule;
import org.neo4j.server.modules.ThirdPartyJAXRSModule;
import org.neo4j.server.modules.WebAdminModule;
import org.neo4j.server.startup.healthcheck.StartupHealthCheckRule;

/**
 * A bootstrapper for the Neo4j Server that takes an already instantiated
 * {@link org.neo4j.kernel.GraphDatabaseAPI}, and optional configuration, and launches a
 * server using that database.
 * <p>
 * Use this to start up a full Neo4j server from within an application that
 * already uses the {@link EmbeddedGraphDatabase} or the
 * {@link HighlyAvailableGraphDatabase}. This gives your application the full
 * benefits of the server's REST API, the Web administration interface and
 * statistics tracking.
 * <p>
 * Example:
 * 
 * <pre>
 * {
 *     &#064;code WrappingNeoServerBootstrapper srv = new WrappingNeoServerBootstrapper( myDatabase );
 *     srv.start(); // Launches the server at default URL, http://localhost:7474
 * 
 *     // Run your application as long as you please
 * 
 *     srv.stop();
 * }
 * </pre>
 * 
 * If you want to change configuration, pass in the optional Configurator arg to
 * the constructor. You can write your own implementation or use
 * {@link org.neo4j.server.configuration.ServerConfigurator}.
 */
public class WrappingNeoServerBootstrapper extends Bootstrapper
{
    private final GraphDatabaseAPI db;
    private static Logger log = Logger.getLogger( WrappingNeoServerBootstrapper.class );

    /**
     * Create an instance with default settings.
     * 
     * @param db
     */
    public WrappingNeoServerBootstrapper( GraphDatabaseAPI db )
    {
        this( db, new HashMap<String,String>());
    }
    
    /**
     * Create an instance with custom documentation.
     * 
     * @param db
     * @param config
     */
    public WrappingNeoServerBootstrapper( GraphDatabaseAPI db, Map<String,String> config )
    {
        this( db, new Config(new SystemPropertiesConfiguration(ServerSettings.class).apply(config),ServerSettings.class));
    }
    
    /**
     * Create an instance with custom documentation.
     * 
     * @param db
     * @param config
     */
    public WrappingNeoServerBootstrapper( GraphDatabaseAPI db, Config config )
    {
        super(config);
        this.db = db;
    }

    /**
     * Create an instance with custom documentation.
     * {@link org.neo4j.server.configuration.ServerConfigurator} is written to fit well here, see its'
     * documentation.
     * 
     * @param db
     * @param configurator
     */
    @Deprecated
    public WrappingNeoServerBootstrapper( GraphDatabaseAPI db, Configurator configurator )
    {
        super(ConfiguratorWrappedConfig.configFromConfigurator(configurator, ServerSettings.class));
        this.db = db;
    }

    @Override
    public Iterable<StartupHealthCheckRule> getHealthCheckRules()
    {
        return Arrays.asList();
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public Iterable<Class<? extends ServerModule>> getServerModules()
    {
        return Arrays.asList( DiscoveryModule.class, RESTApiModule.class, ManagementApiModule.class,
                ThirdPartyJAXRSModule.class, WebAdminModule.class );
    }

    @Override
    public int stop( int stopArg )
    {
        try
        {
            if ( server != null )
            {
                server.stopServerOnly();
                server.getDatabase()
                        .rrdDb()
                        .close();
            }
            return 0;
        }
        catch ( Exception e )
        {
            log.error( "Failed to cleanly shutdown Neo Server on port [%d]. Reason [%s] ", server.getWebServerPort(),
                    e.getMessage() );
            return 1;
        }
    }

    @Override
    protected void addShutdownHook()
    {
        // No-op
    }

    @Override
    protected GraphDatabaseFactory getGraphDatabaseFactory( Config configuration )
    {
        return new GraphDatabaseFactory()
        {
            @Override
            public GraphDatabaseService newEmbeddedDatabase( String path )
            {
                return super.newEmbeddedDatabase( path );
            }

            @Override
            public GraphDatabaseBuilder newEmbeddedDatabaseBuilder( String path )
            {
                return new GraphDatabaseBuilder( new GraphDatabaseBuilder.DatabaseCreator()
                {
                    @Override
                    public GraphDatabaseService newDatabase( Map<String, String> config )
                    {
                        return db;
                    }
                } );
            }
        };
    }
}
