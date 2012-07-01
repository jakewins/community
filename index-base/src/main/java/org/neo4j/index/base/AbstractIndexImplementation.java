/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.index.base;

import java.io.File;
import java.util.Map;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexImplementation;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.configuration.Config;

public class AbstractIndexImplementation<DS extends IndexDataSource> implements IndexImplementation
{
    public interface Configuration
    {
        boolean read_only( boolean def );
    }
    
    private GraphDatabaseAPI graphDb;
    private IndexConnectionBroker<IndexBaseXaConnection> broker;
    private DS dataSource;
    private final Config config;
    
    public AbstractIndexImplementation( GraphDatabaseAPI db, Config config, DS dataSource )
    {
        this.graphDb = db;
        this.dataSource = dataSource;
        this.config = config;
        this.broker = newBroker( db, dataSource );
    }

    private IndexConnectionBroker<IndexBaseXaConnection> newBroker( GraphDatabaseAPI db, DS dataSource )
    {
        return config.get( GraphDatabaseSettings.read_only ) ?
                new ReadOnlyConnectionBroker<IndexBaseXaConnection>( db.getTxManager() ) :
                new ConnectionBroker( db.getTxManager(), dataSource );
    }
    
    public IndexConnectionBroker<IndexBaseXaConnection> broker()
    {
        return this.broker;
    }
    
    public GraphDatabaseAPI graphDb()
    {
        return this.graphDb;
    }
    
    public DS dataSource()
    {
        return this.dataSource;
    }
    
    public static String getIndexStoreDir( String dbStoreDir, String dataSourceName )
    {
        return new File( new File( dbStoreDir, "index" ), dataSourceName ).getAbsolutePath();
    }

    public static String getProviderStoreDb( String dbStoreDir, String dataSourceName )
    {
        return new File( getIndexStoreDir( dbStoreDir, dataSourceName ), "store.db" ).getAbsolutePath();
    }
    
    public void reset( DS dataSource )
    {
        this.dataSource = dataSource;
        this.broker = newBroker( graphDb, dataSource );
    }

    public boolean matches( GraphDatabaseService gdb )
    {
        return this.graphDb().equals(gdb);
    }
    
    @Override
    public String getDataSourceName()
    {
        return dataSource.getName();
    }

    @Override
    public Index<Node> nodeIndex( String indexName, Map<String, String> config )
    {
        return dataSource().nodeIndex( indexName, graphDb(), this );
    }

    @Override
    public RelationshipIndex relationshipIndex( String indexName, Map<String, String> config )
    {
        return dataSource().relationshipIndex( indexName, graphDb(), this );
    }
    
    protected boolean matchConfig( Map<String, String> storedConfig, Map<String, String> config, String key, String defaultValue )
    {
        String value1 = storedConfig.get( key );
        String value2 = config.get( key );
        if ( value1 == null || value2 == null )
        {
            if ( value1 == value2 )
            {
                return true;
            }
            if ( defaultValue != null )
            {
                value1 = value1 != null ? value1 : defaultValue;
                value2 = value2 != null ? value2 : defaultValue;
                return value1.equals( value2 );
            }
        }
        else
        {
            return value1.equals( value2 );
        }
        return false;
    }
    
    @Override
    public Map<String, String> fillInDefaults( Map<String, String> config )
    {
        return config;
    }
    
    @Override
    public boolean configMatches( Map<String, String> storedConfig, Map<String, String> config )
    {
        return storedConfig.equals( config );
    }
}
