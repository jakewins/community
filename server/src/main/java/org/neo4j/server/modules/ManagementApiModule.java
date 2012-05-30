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
import org.neo4j.server.web.WebServer;

public class ManagementApiModule implements ServerModule
{
    private final static String MANAGEMENT_API_PACKAGE = "org.neo4j.server.webadmin.rest";
    private WebServer webServer;
    private StringLogger log;
    private Config config;
    
    public ManagementApiModule(Config config, StringLogger log, WebServer server)
    {
        this.config = config;
        this.log = log;
        this.webServer = server;
    }
    
    @Override
    public void start( )
    {
        webServer.addJAXRSPackages( Arrays.asList(new String[] { MANAGEMENT_API_PACKAGE } ),
                        managementApiUri( ).toString() );
        log.info( "Mounted management API at: " + managementApiUri( ).toString() );
    }

    public void stop()
    {
        // Do nothing.
    }

    private URI managementApiUri()
    {
        return config.get(ServerSettings.management_path );
    }
}
