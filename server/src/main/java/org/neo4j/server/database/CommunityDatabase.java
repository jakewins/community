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
package org.neo4j.server.database;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.neo4j.ext.udc.UdcSettings;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.info.DiagnosticsManager;
import org.neo4j.kernel.logging.Logging;
import org.neo4j.kernel.logging.StringLogger;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.shell.ShellSettings;

public class CommunityDatabase implements Database
{
    
    private GraphDatabaseAPI db;

    private StringLogger log;

    private Config serverConfig;

    private org.neo4j.graphdb.factory.GraphDatabaseFactory dbFactory;
    
    public CommunityDatabase( Config serverConfig, StringLogger log, Logging logging)
    {
        this.serverConfig = serverConfig;
        this.log = log;
        
        this.dbFactory = new org.neo4j.graphdb.factory.GraphDatabaseFactory();
        this.dbFactory.setLogging(logging);
    }

    @Override
    public void init() throws Throwable
    {
        
    }

    @Override
    public void start() throws Throwable
    {
        Map<String, String> dbConfig = loadNeo4jProperties();
        
        db = (GraphDatabaseAPI) dbFactory
                .newEmbeddedDatabaseBuilder(serverConfig.get(ServerSettings.database_location))
                .setConfig(dbConfig)
                .newGraphDatabase();
        
        db.getDependencyResolver().resolveDependency(DiagnosticsManager.class).appendProvider(serverConfig);

        log.info( "Started database at " + serverConfig.get(ServerSettings.database_location) );
    }

    @Override
    public void stop() throws Throwable
    {
        try
        {
            if ( db != null )
            {
                db.shutdown();
                db = null;
            }
            log.info( "Successfully shutdown database at " + serverConfig.get(ServerSettings.database_location)  );
        }
        catch ( Exception e )
        {
            log.error( "Database did not shut down cleanly. Reason [%s]", e.getMessage() );
            throw new RuntimeException( e );
        }
    }

    @Override
    public void shutdown() throws Throwable
    {
        
    }

    @Override
    public String getLocation()
    {
        return db.getStoreDir();
    }

    @Override
    public GraphDatabaseAPI getGraph()
    {
        return db;
    }

    @Override
    public org.neo4j.graphdb.index.Index<Relationship> getRelationshipIndex( String name )
    {
        RelationshipIndex index = db.index()
                .forRelationships( name );
        if ( index == null )
        {
            throw new RuntimeException( "No index for [" + name + "]" );
        }
        return index;
    }

    @Override
    public org.neo4j.graphdb.index.Index<Node> getNodeIndex( String name )
    {
        org.neo4j.graphdb.index.Index<Node> index = db.index()
                .forNodes( name );
        if ( index == null )
        {
            throw new RuntimeException( "No index for [" + name + "]" );
        }
        return index;
    }

    @Override
    public IndexManager getIndexManager()
    {
        return db.index();
    }


    protected Map<String, String> loadNeo4jProperties()
    {
        String configLocation = serverConfig.get(ServerSettings.db_tuning_property_file);
        Map<String, String> dbConfig;
        try {
            dbConfig = MapUtil.load(new File(configLocation));
        } catch(IOException e) 
        {
            log.warn("Unable to find or open database tuning properties at '" + configLocation + "', using default settings.");
            dbConfig = new HashMap<String,String>();
        }
        
        putIfAbsent( dbConfig, ShellSettings.remote_shell_enabled, GraphDatabaseSetting.TRUE );
        putIfAbsent( dbConfig, GraphDatabaseSettings.keep_logical_logs, GraphDatabaseSetting.TRUE );
        dbConfig.put( UdcSettings.udc_source.name(), "server" );
        
        return dbConfig;
    }

    protected void putIfAbsent(Map<String, String> config,
            GraphDatabaseSetting<?> setting, String value)
    {
        if(!config.containsKey(setting.name()))
            config.put(setting.name(), value);
    }
}
