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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.kernel.logging.ClassicLoggingService;
import org.neo4j.kernel.logging.LogbackService;
import org.neo4j.kernel.logging.Logging;
import org.neo4j.kernel.logging.StringLogger;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.configuration.ConfiguratorWrappedConfig;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.database.Database;
import org.neo4j.server.logging.Loggers;
import org.neo4j.server.modules.PluginInitializer;
import org.neo4j.server.modules.RESTApiModule;
import org.neo4j.server.modules.ServerModule;
import org.neo4j.server.plugins.Injectable;
import org.neo4j.server.plugins.PluginManager;
import org.neo4j.server.rest.security.SecurityRule;
import org.neo4j.server.rrd.StatisticsStore;
import org.neo4j.server.security.KeyStoreFactory;
import org.neo4j.server.security.KeyStoreInformation;
import org.neo4j.server.security.SslCertificateFactory;
import org.neo4j.server.startup.healthcheck.HTTPLoggingPreparednessRule;
import org.neo4j.server.startup.healthcheck.StartupHealthCheck;
import org.neo4j.server.startup.healthcheck.StartupHealthCheckFailedException;
import org.neo4j.server.startup.healthcheck.StartupHealthCheckRule;
import org.neo4j.server.statistic.StatisticCollector;
import org.neo4j.server.web.Jetty6WebServer;
import org.neo4j.server.web.SimpleUriBuilder;
import org.neo4j.server.web.WebServer;
import org.rrd4j.core.RrdDb;

public abstract class AbstractNeoServer implements NeoServer
{

    
    private class ServerDependencyResolver implements DependencyResolver 
    {

        @Override
        @SuppressWarnings("unchecked")
        public <T> T resolveDependency(Class<T> type)
        {
            if(type.isAssignableFrom(Config.class))
            {
                return (T)configurator;
            } else if(type.isAssignableFrom(Logging.class))
            {
                return (T)logging;
            } else if(type.isAssignableFrom(Database.class))
            {
                return (T)database;
            } else if(type.isAssignableFrom(GraphDatabaseService.class))
            {
                return (T)database.getGraph();
            } else if(type.isAssignableFrom(StatisticCollector.class))
            {
                return (T)requestStatistics;
            } else if(type.isAssignableFrom(StatisticsStore.class))
            {
                return (T)statisticsStore;
            } else if(type.isAssignableFrom(RrdDb.class))
            {
                return (T)statisticsStore.getRrdDb();
            } else if(type.isAssignableFrom(WebServer.class))
            {
                return (T)webServer;
            } else if(type.isAssignableFrom(AbstractNeoServer.class))
            {
                return (T)this;
            }
            throw new IllegalArgumentException("Unable to satisfy dependency for '" + type.getCanonicalName() + "', no such component registered.");
        }
    }
    
    protected Database database;
    protected Config configurator;
    protected WebServer webServer;
    protected StatisticCollector requestStatistics;
    protected StatisticsStore statisticsStore;
    protected Logging logging;
    protected StringLogger log;
    protected DependencyResolver dependencyResolver = new ServerDependencyResolver();

    private SimpleUriBuilder uriBuilder = new SimpleUriBuilder();
    private LifeSupport life = new LifeSupport();
    private boolean initialized = false;
    private StartupHealthCheck startupHealthCheck;

    private List<ServerModule> serverModules;
    private PluginInitializer pluginInitializer;

    public AbstractNeoServer( Config configurator)
    {
        this.configurator = configurator;
    }
    
    public void init() throws Throwable
    {
        this.logging = life.add(createLogging());
        
        this.log = logging.getLogger(Loggers.SERVER);
        
        this.requestStatistics = new StatisticCollector();
        
        this.database = life.add(createDatabase());
        
        this.statisticsStore = life.add(new StatisticsStore(database, configurator));
        
        this.webServer = new Jetty6WebServer(dependencyResolver, logging.getLogger(Loggers.WEBSERVER));

        this.pluginInitializer = new PluginInitializer( this );
        
        // TODO: Figure out why this cyclic dependency is necessary
        webServer.setNeoServer( this );
        
        this.startupHealthCheck = createStartupHealthCheck();
        
        serverModules = createServerModules();
    }

    @Override
    public void start()
    {
        try
        {

            if(!initialized )
                init();

            log.info( "--- SERVER STARTUP START ---" );
            
            startupHealthCheck();

            initWebServer();
            
            life.start();

            startModules( );

            startWebServer();
            
            log.info( "--- SERVER STARTUP END ---" );
        }
        catch ( TransactionFailureException tfe )
        {
            log.error( "", tfe);
            log.error( String.format( "Failed to start Neo Server on port [%d], because ", getWebServerPort() )
                       + tfe + ". Another process may be using database location " + getDatabase()
                               .getLocation() );
            throw tfe;
        }
        catch ( Throwable e )
        {
            log.error( "", e);
            log.error( "Failed to start Neo Server on port [%s]", getWebServerPort() );
            throw new RuntimeException(e);
        }
    }



    @Override
    public void stop()
    {
        
        log.info( "Neo4j Server stop initiated" );
        try {

            webServer.stop();
            
            stopModules();
            
            pluginInitializer.stop();
            
            life.stop();
            
            log.info( "Successfully stopped Neo Server on port [%d], database [%s]", getWebServerPort(),
                    configurator.get(ServerSettings.database_location) );
        } catch(Throwable e) {
            log.error( "Failed to cleanly stop Neo Server on port [%d], database [%s]. Reason [%s] ",
                    getWebServerPort(), configurator.get(ServerSettings.database_location), e.getMessage() );
            throw new RuntimeException(e);
        }
    }
    
    private void startModules( )
    {
        for ( ServerModule module : serverModules )
        {
            module.start();
        }
    }

    private void stopModules()
    {
        for ( ServerModule module : serverModules )
        {

            try
            {
                module.stop();
            }
            catch ( Exception e )
            {
                log.error("Could not stop modules", e);
            }
        }
    }

    private void startupHealthCheck()
    {
        if ( !startupHealthCheck.run() )
        {
            throw new StartupHealthCheckFailedException( startupHealthCheck.failedRule() );
        }
    }

    @Override
    public Configuration getConfiguration()
    {
        return new ConfiguratorWrappedConfig(getConfig());
    }
    

    @Override
    public Configurator getConfigurator() 
    {
        return new ConfiguratorWrappedConfig(getConfig());
    }
    
    @Override
    public Config getConfig()
    {
        return configurator;
    }

    private void initWebServer()
    {
        int webServerPort = getWebServerPort();
        String webServerAddr = getWebServerAddress();

        int maxThreads = getMaxThreads();

        int sslPort = getHttpsPort();
        boolean sslEnabled = getHttpsEnabled();

        log.info( "Starting Neo Server on port [%s] with [%d] threads available", webServerPort, maxThreads );
        webServer.setPort( webServerPort );
        webServer.setAddress( webServerAddr );
        webServer.setMaxThreads( maxThreads );
        
        webServer.setEnableHttps(sslEnabled);
        webServer.setHttpsPort(sslPort);
        if(sslEnabled) {
            log.info( "Enabling HTTPS on port [%s]", sslPort );
            webServer.setHttpsCertificateInformation(initHttpsKeyStore());
        }

        webServer.init();
    }

    private SecurityRule[] createSecurityRulesFrom( Config config )
    {
        ArrayList<SecurityRule> rules = new ArrayList<SecurityRule>();
        
        for (String classname : config.get( ServerSettings.rest_security_rules))
        {
            try
            {
                SecurityRule rule = (SecurityRule) Class.forName( classname ).newInstance();
                if(rule instanceof HasDependencies)
                {
                    ((HasDependencies)rule).resolveDependencies(dependencyResolver);
                }
                rules.add( rule );
            }
            catch ( Exception e )
            {
                log.error( "Could not load server security rule [%s], exception details: ", classname, e.getMessage() );
            }
        }

        return rules.toArray(new SecurityRule[rules.size()]);
    }

    private int getMaxThreads()
    {
        return configurator.get(ServerSettings.webserver_max_threads);
    }

    private void startWebServer(  )
    {
        SecurityRule[] securityRules = createSecurityRulesFrom( configurator );
        webServer.addSecurityRules( securityRules );

        Integer limit = configurator.get(ServerSettings.webserver_limit_execution_time);
        if ( limit != null )
        {
            webServer.addExecutionLimitFilter( limit );
        }


        if ( httpLoggingProperlyConfigured() )
        {
            webServer.enableHTTPLoggingForWebadmin(
                new File( configurator.get( ServerSettings.http_logging_configuration_location )) );
        }

        webServer.start();
        log.info("Server started on: " + baseUri());
    }


    private boolean httpLoggingProperlyConfigured()
    {
        return loggingEnabled() && configLocated();
    }

    // TODO: The setting type verification method should do this check,
    // expand FileSetting to optionally validate that the file exists.
    private boolean configLocated()
    {
        final String property = configurator.get( ServerSettings.http_logging_configuration_location );
        if ( property == null )
        {
            return false;
        }

        return new File( property ).exists();
    }

    private boolean loggingEnabled()
    {
        return configurator.get(ServerSettings.http_logging_enabled);
    }

    protected int getWebServerPort()
    {
        return configurator.get(ServerSettings.webserver_http_port);
    }

    protected boolean getHttpsEnabled()
    {
        return configurator.get(ServerSettings.webserver_https_enabled);
    }

    protected int getHttpsPort()
    {
        return configurator.get(ServerSettings.webserver_https_port);
    }

    protected String getWebServerAddress()
    {
        return configurator.get(ServerSettings.webserver_address);
    }

    /**
     * Jetty wants certificates stored in a key store, which is nice, but
     * to make it easier for non-java savvy users, we let them put
     * their certificates directly on the file system (advicing apropriate
     * permissions etc), like you do with Apache Web Server. On each startup
     * we set up a key store for them with their certificate in it.
     */
    protected KeyStoreInformation initHttpsKeyStore()
    {
        File keystorePath = new File( configurator.get( ServerSettings.webserver_https_keystore_path ));

        File privateKeyPath = new File( configurator.get( ServerSettings.webserver_https_key_path ));

        File certificatePath = new File( configurator.get( ServerSettings.webserver_https_certificate_path ));

        if(!certificatePath.exists()) {
            log.info( "No SSL certificate found, generating a self-signed certificate.." );
            SslCertificateFactory certFactory = new SslCertificateFactory();
            certFactory.createSelfSignedCertificate( certificatePath, privateKeyPath, getWebServerAddress() );
        }

        KeyStoreFactory keyStoreFactory = new KeyStoreFactory();
        return keyStoreFactory.createKeyStore( keystorePath, privateKeyPath, certificatePath );
    }

    @Override
    public Database getDatabase()
    {
        return database;
    }

    @Override
    public URI baseUri()
    {
        return uriBuilder.buildURI( getWebServerAddress(), getWebServerPort(), false );
    }

    public URI httpsUri()
    {
        return uriBuilder.buildURI( getWebServerAddress(), getHttpsPort(), true );
    }

    public WebServer getWebServer()
    {
        return webServer;
    }

    @Override
    public PluginManager getExtensionManager()
    {
        if ( hasModule( RESTApiModule.class ) )
        {
            return getModule( RESTApiModule.class ).getPlugins();
        }
        else
        {
            return null;
        }
    }

    @Override
    public Collection<Injectable<?>> getInjectables( List<String> packageNames )
    {
        return pluginInitializer.initializePackages( packageNames );
    }

    private boolean hasModule( Class<? extends ServerModule> clazz )
    {
        for ( ServerModule sm : serverModules )
        {
            if ( sm.getClass() == clazz )
            {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private <T extends ServerModule> T getModule( Class<T> clazz )
    {
        for ( ServerModule sm : serverModules )
        {
            if ( sm.getClass() == clazz )
            {
                return (T) sm;
            }
        }

        return null;
    }
    
    protected Logging createLogging()
    {
        try
        {
            getClass().getClassLoader().loadClass("ch.qos.logback.classic.LoggerContext");
            return new LogbackService( configurator );
        }
        catch( ClassNotFoundException e )
        {
            return new ClassicLoggingService(configurator);
        }
    }
    
    protected abstract Database createDatabase( );

    protected abstract List<ServerModule> createServerModules();

    protected StartupHealthCheck createStartupHealthCheck()
    {
        return new StartupHealthCheck( logging.getLogger(Loggers.HEALTHCHECK), configurator, createHealthCheckRules() );
    }
    
    protected StartupHealthCheckRule[] createHealthCheckRules()
    {
        return new StartupHealthCheckRule[]{ new HTTPLoggingPreparednessRule()};
    }
}
