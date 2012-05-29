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

import java.io.IOException;

import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.Service;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.kernel.lifecycle.LifecycleException;
import org.neo4j.kernel.logging.ClassicLoggingService;
import org.neo4j.kernel.logging.LogbackService;
import org.neo4j.kernel.logging.Logging;
import org.neo4j.server.configuration.ConfiguratorWrappedConfig;
import org.neo4j.server.configuration.ServerConfig;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.logging.Logger;
import org.neo4j.server.modules.ServerModule;
import org.neo4j.server.startup.healthcheck.StartupHealthCheck;
import org.neo4j.server.startup.healthcheck.StartupHealthCheckRule;
import org.neo4j.server.web.Jetty6WebServer;

public abstract class Bootstrapper
    implements Lifecycle
{
    public static final Integer OK = 0;
    public static final Integer WEB_SERVER_STARTUP_ERROR_CODE = 1;
    public static final Integer GRAPH_DATABASE_STARTUP_ERROR_CODE = 2;

    private static Logger log = Logger.getLogger( NeoServerBootstrapper.class );

    LifeSupport life = new LifeSupport();

    protected NeoServerWithEmbeddedWebServer server;
    protected final Config config;
    
    public Bootstrapper()
    {
        this.config = createConfig();
    }
    
    public Bootstrapper(Config config)
    {
        this.config = config;
    }

    public static void main( String[] args )
    {
        LifeSupport life = new LifeSupport();

        life.add( loadMostDerivedBootstrapper() );

        try
        {
            life.start();
        }
        catch( LifecycleException e )
        {
            if (e.getCause() instanceof TransactionFailureException)
            {
                System.exit( GRAPH_DATABASE_STARTUP_ERROR_CODE );
            } else
            {
                System.exit(WEB_SERVER_STARTUP_ERROR_CODE);
            }
        }
    }

    public static Bootstrapper loadMostDerivedBootstrapper()
    {
        Bootstrapper winner = new NeoServerBootstrapper();
        for ( Bootstrapper candidate : Service.load( Bootstrapper.class ) )
        {
            if ( candidate.isMoreDerivedThan( winner ) ) winner = candidate;
        }
        return winner;
    }

    public void controlEvent( int arg )
    {
        // Do nothing, required by the WrapperListener interface
    }
    
    @Override
    public void init() throws Throwable
    {
        
    }

    @Override
    public void start() throws Throwable
    {
        try
        {
            StartupHealthCheck startupHealthCheck = new StartupHealthCheck( config, rules() );
            Jetty6WebServer webServer = life.add( new Jetty6WebServer());
            server = life.add( new NeoServerWithEmbeddedWebServer( this, startupHealthCheck,
                    config, webServer, getServerModules() ));

            life.start();

            addShutdownHook();
        }
        catch ( TransactionFailureException tfe )
        {
            log.error(tfe);
            log.error( String.format( "Failed to start Neo Server on port [%d], because ", server.getWebServerPort() )
                       + tfe + ". Another process may be using database location " + server.getDatabase()
                               .getLocation() );
            throw tfe;
        }
        catch ( Exception e )
        {
            log.error(e);
            log.error( "Failed to start Neo Server on port [%s]", server.getWebServerPort() );
            throw e;
        }
    }

    @Override
    public void stop()
    {
        //stop( 0 );
        life.stop();
    }

    public int stop( int stopArg )
    {
        String location = "unknown location";
        try
        {
            if ( server != null )
            {
                server.stop();
            }
            log.info( "Successfully shutdown Neo Server on port [%d], database [%s]", server.getWebServerPort(),
                    location );
            return 0;
        }
        catch ( Exception e )
        {
            log.error( "Failed to cleanly shutdown Neo Server on port [%d], database [%s]. Reason [%s] ",
                    server.getWebServerPort(), location, e.getMessage() );
            return 1;
        }
    }
    
    @Override
    public void shutdown() throws Throwable
    {
        life.shutdown();
    }

    public NeoServerWithEmbeddedWebServer getServer()
    {
        return server;
    }
    
    protected abstract GraphDatabaseFactory getGraphDatabaseFactory( Config configuration );

    protected abstract Iterable<StartupHealthCheckRule> getHealthCheckRules();

    protected abstract Iterable<Class<? extends ServerModule>> getServerModules();

    protected void addShutdownHook()
    {
        Runtime.getRuntime()
                .addShutdownHook( new Thread()
                {
                    @Override
                    public void run()
                    {
                        log.info( "Neo4j Server shutdown initiated by kill signal" );
                        
                    }
                } );
    }

    @Deprecated
    protected ConfiguratorWrappedConfig getConfigurator()
    {
        return new ConfiguratorWrappedConfig(config);
    }

    protected Config createConfig()
    {
        try {
            return ServerConfig.fromFile(System.getProperty( ServerSettings.neo_server_config_file.name()));
        } catch(IOException e)
        {
            throw new RuntimeException("Unable to read server configuration, see nested exception.", e);
        }
    }

    protected boolean isMoreDerivedThan( Bootstrapper other )
    {
        // Default implementation just checks if this is a subclass of other
        return other.getClass()
                .isAssignableFrom( getClass() );
    }

    private StartupHealthCheckRule[] rules()
    {
        return IteratorUtil.asCollection( getHealthCheckRules() )
                .toArray( new StartupHealthCheckRule[0] );
    }

    protected Logging createLogging()
    {
        try
        {
            getClass().getClassLoader().loadClass("ch.qos.logback.classic.LoggerContext");
            return life.add( new LogbackService( config ));
        }
        catch( ClassNotFoundException e )
        {
            return life.add( new ClassicLoggingService(config));
        }
    }
}
