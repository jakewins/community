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
package org.neo4j.server.modules;

import java.net.URI;
import java.util.Arrays;

import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.logging.StringLogger;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.plugins.PluginManager;
import org.neo4j.server.web.WebServer;

public class RESTApiModule implements ServerModule
{
    
    private PluginManager plugins;

    private WebServer webServer;

    private StringLogger log;

    private Config config;

    private static final String REST_API_PACKAGE = "org.neo4j.server.rest.web";

    public RESTApiModule(Config config, StringLogger log, WebServer server)
    {
        this.config = config;
        this.log = log;
        this.webServer = server;
    }
    
    @Override
    public void start( )
    {
        URI restApiUri = config.get( ServerSettings.rest_api_path);

        webServer.addJAXRSPackages( Arrays.asList( new String[] { REST_API_PACKAGE } ),
                        restApiUri.toString() );
        
        // TODO: Transition this from loading plugins at construction time to
        // implementing lifecycle.
        // TODO: What is the difference between this manager and PluginInitializer?
        // And who is actually responsible for managing plugins, this module or the
        // NeoServer?
        plugins = new PluginManager( config, log );

        log.info( "Mounted REST API at: " + restApiUri.toString() );
    }

    public void stop()
    {
        // Do nothing.
    }

    public PluginManager getPlugins()
    {
        return plugins;
    }
}
