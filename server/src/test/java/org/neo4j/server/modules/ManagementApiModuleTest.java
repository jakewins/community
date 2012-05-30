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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.junit.Test;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.logging.StringLogger;
import org.neo4j.server.NeoServerWithEmbeddedWebServer;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.web.WebServer;

public class ManagementApiModuleTest
{
    @Test
    public void shouldRegisterASingleUri() throws Exception
    {
        WebServer webServer = mock( WebServer.class );

        NeoServerWithEmbeddedWebServer neoServer = mock( NeoServerWithEmbeddedWebServer.class );
        when( neoServer.baseUri() ).thenReturn( new URI( "http://localhost:7575" ) );
        when( neoServer.getWebServer() ).thenReturn( webServer );

        Config config = new Config();
        config.set( ServerSettings.management_path, "/db/manage" );

        when( neoServer.getConfig() ).thenReturn( config );

        ManagementApiModule module = new ManagementApiModule(config, StringLogger.DEV_NULL, webServer);
        module.start();

        verify( webServer ).addJAXRSPackages( any( List.class ), anyString() );
    }
}
