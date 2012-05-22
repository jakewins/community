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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.apache.commons.configuration.Configuration;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.Service;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.configuration.ConfigurationDefaults;
import org.neo4j.kernel.configuration.SystemPropertiesConfiguration;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.kernel.lifecycle.LifecycleException;
import org.neo4j.kernel.logging.ClassicLoggingService;
import org.neo4j.kernel.logging.LogbackService;
import org.neo4j.kernel.logging.Loggers;
import org.neo4j.kernel.logging.Logging;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.configuration.PropertyFileConfigurator;
import org.neo4j.server.configuration.ServerConfigurationMigrator;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.configuration.validation.DatabaseLocationMustBeSpecifiedRule;
import org.neo4j.server.configuration.validation.Validator;
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

    public static void main( String[] args )
    {
        LifeSupport life = new LifeSupport();

        Bootstrapper bootstrapper = life.add( loadMostDerivedBootstrapper() );

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

    public void start()
    {
        try
        {
            StartupHealthCheck startupHealthCheck = new StartupHealthCheck( rules() );
            Jetty6WebServer webServer = life.add( new Jetty6WebServer());
            server = life.add( new NeoServerWithEmbeddedWebServer( this, startupHealthCheck,
                    getConfigurator(), webServer, getServerModules() ));

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

    public void stop()
    {
        stop( 0 );
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

    public NeoServerWithEmbeddedWebServer getServer()
    {
        return server;
    }

    protected abstract GraphDatabaseFactory getGraphDatabaseFactory( Configuration configuration );

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
                        if ( server != null )
                        {
                            server.stop();
                        }
                    }
                } );
    }

    protected Configurator getConfigurator()
    {
        File configFile = new File( System.getProperty( ServerSettings.neo_server_config_file.name()) );
        return new PropertyFileConfigurator( new Validator( new DatabaseLocationMustBeSpecifiedRule() ), configFile );
    }

    protected Config getConfig()
        throws IOException
    {
        File configFile = new File( System.getProperty( ServerSettings.neo_server_config_file.name()) );

        Map<String,String> props = MapUtil.load( configFile );

        ServerConfigurationMigrator configurationMigrator = new ServerConfigurationMigrator( logging.getLogger( Loggers.CONFIG ) );
        ConfigurationDefaults configurationDefaults = new ConfigurationDefaults( ServerSettings.class );
        Map<String,String> configParams = configurationDefaults.apply(configurationMigrator.migrateConfiguration( new SystemPropertiesConfiguration(ServerSettings.class).apply( props )));

        props = new ConfigurationDefaults( ServerSettings.class ).apply( props );

        return new Config( props );
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
