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

import static org.neo4j.server.JAXRSHelper.listFrom;

import java.net.URI;

import org.neo4j.kernel.logging.StringLogger;
import org.neo4j.server.NeoServerWithEmbeddedWebServer;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.logging.Logger;
import org.neo4j.server.plugins.PluginManager;

public class RESTApiModule implements ServerModule
{
    private static final Logger log = Logger.getLogger( RESTApiModule.class );
    private PluginManager plugins;

    private static final String REST_API_PACKAGE = "org.neo4j.server.rest.web";

    public void start( NeoServerWithEmbeddedWebServer neoServer, StringLogger logger )
    {
        URI restApiUri = restApiUri( neoServer );

        neoServer.getWebServer()
                .addJAXRSPackages( listFrom( new String[] { REST_API_PACKAGE } ),
                        restApiUri.toString() );
        loadPlugins( neoServer, logger );

        logger.info( "Mounted REST API at: " + restApiUri.toString() );
    }

    public void stop()
    {
        // Do nothing.
    }

    private URI restApiUri( NeoServerWithEmbeddedWebServer neoServer )
    {
        return neoServer.getConfig().get( ServerSettings.rest_api_path);
    }

    private void loadPlugins( NeoServerWithEmbeddedWebServer neoServer, StringLogger logger )
    {
        plugins = new PluginManager( neoServer.getConfiguration(), logger );
    }

    public PluginManager getPlugins()
    {
        return plugins;
    }
}
