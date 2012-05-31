package org.neo4j.server;

import java.util.List;
import java.util.Map;

import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.server.configuration.ServerConfig;
import org.neo4j.server.database.Database;
import org.neo4j.server.database.WrappedDatabase;
import org.neo4j.server.modules.ServerModule;

public class WrappingNeoServer extends AbstractNeoServer {

    private GraphDatabaseAPI db;

    public WrappingNeoServer( GraphDatabaseAPI db, Map<String,String> config )
    {
        this( db, ServerConfig.fromMap(config));
    }
    
    public WrappingNeoServer(GraphDatabaseAPI db, Config configurator)
    {
        super(configurator);
        this.db = db;
    }

    @Override
    protected Database createDatabase()
    {
        return new WrappedDatabase(db);
    }

    @Override
    protected List<ServerModule> createServerModules()
    {
        // TODO Auto-generated method stub
        return null;
    }

}
