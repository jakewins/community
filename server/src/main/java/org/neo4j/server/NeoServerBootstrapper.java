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
import java.util.List;

import org.neo4j.kernel.configuration.Config;
import org.neo4j.server.database.CommunityDatabase;
import org.neo4j.server.database.Database;
import org.neo4j.server.modules.DiscoveryModule;
import org.neo4j.server.modules.ManagementApiModule;
import org.neo4j.server.modules.RESTApiModule;
import org.neo4j.server.modules.ServerModule;
import org.neo4j.server.modules.StatisticModule;
import org.neo4j.server.modules.ThirdPartyJAXRSModule;
import org.neo4j.server.modules.WebAdminModule;
import org.neo4j.server.startup.healthcheck.HTTPLoggingPreparednessRule;
import org.neo4j.server.startup.healthcheck.StartupHealthCheckRule;

public class NeoServerBootstrapper extends Bootstrapper
{
    public NeoServerBootstrapper()
    {
        super();
    }
    
    public NeoServerBootstrapper(Config config)
    {
        super(config);
    }

    @Override
    public List<StartupHealthCheckRule> createHealthCheckRules()
    {
        return Arrays.asList( new StartupHealthCheckRule[]{ new HTTPLoggingPreparednessRule()} );
    }

    @Override
    public List<ServerModule> createServerModules()
    {
        return Arrays.asList( 
                new DiscoveryModule(log, webServer), 
                new RESTApiModule(config, log, webServer), 
                new ManagementApiModule(config, log, webServer),
                new ThirdPartyJAXRSModule(config, log, webServer), 
                new WebAdminModule(log, database, webServer), 
                new StatisticModule(requestStatistics, webServer) );
    }

    @Override
    protected Database createDatabase( )
    {
        return new CommunityDatabase(config, log, logging);
    }
}
