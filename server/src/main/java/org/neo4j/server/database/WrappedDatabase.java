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

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.kernel.GraphDatabaseAPI;

public class WrappedDatabase implements Database
{
    private GraphDatabaseAPI db;

    public WrappedDatabase( GraphDatabaseAPI db )
    {
        this.db = db;
    }


    @Override
    public void init() throws Throwable
    {
        
    }

    @Override
    public void start() throws Throwable
    {
        
    }

    @Override
    public void stop() throws Throwable
    {
        
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
    
}
