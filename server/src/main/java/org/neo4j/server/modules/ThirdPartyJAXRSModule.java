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

import java.util.Arrays;

import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.logging.StringLogger;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.configuration.ThirdPartyJaxRsPackage;
import org.neo4j.server.web.WebServer;

public class ThirdPartyJAXRSModule implements ServerModule
{
    private WebServer webServer;
    private StringLogger log;
    private Config config;

    public ThirdPartyJAXRSModule(Config config, StringLogger log, WebServer webServer)
    {
        this.config = config;
        this.log = log;
        this.webServer = webServer;
    }
    
    @Override
    public void start( )
    {
        for ( ThirdPartyJaxRsPackage tpp : config.get(ServerSettings.third_party_packages) )
        {
            webServer.addJAXRSPackages( Arrays.asList( new String[] { tpp.getPackageName() } ), tpp.getMountPoint() );
            log.info( "Mounted third-party JAX-RS package [%s] at [%s]", tpp.getPackageName(), tpp.getMountPoint() );
        }
    }

    public void stop()
    {
        // Do nothing.
    }
}
