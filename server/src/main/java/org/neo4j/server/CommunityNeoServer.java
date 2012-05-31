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

public class CommunityNeoServer extends AbstractNeoServer {

    public CommunityNeoServer(Config configurator)
    {
        super(configurator);
    }
    
    protected Database createDatabase( )
    {
        return new CommunityDatabase(configurator, log, logging);
    }

    @Override
    protected List<ServerModule> createServerModules()
    {
        return Arrays.asList( 
                new DiscoveryModule(log, webServer), 
                new RESTApiModule(configurator, log, webServer), 
                new ManagementApiModule(configurator, log, webServer),
                new ThirdPartyJAXRSModule(configurator, log, webServer), 
                new WebAdminModule(log, database, webServer), 
                new StatisticModule(requestStatistics, webServer) );
    }

}
