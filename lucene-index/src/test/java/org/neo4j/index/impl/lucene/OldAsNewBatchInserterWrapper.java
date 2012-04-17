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
package org.neo4j.index.impl.lucene;

import java.util.Map;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.helpers.collection.IterableWrapper;
import org.neo4j.kernel.impl.batchinsert.SimpleRelationship;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchRelationship;

public class OldAsNewBatchInserterWrapper implements BatchInserter
{
    private final org.neo4j.kernel.impl.batchinsert.BatchInserter delegate;
    
    public OldAsNewBatchInserterWrapper( org.neo4j.kernel.impl.batchinsert.BatchInserter old )
    {
        this.delegate = old;
    }

    public long createNode( Map<String, Object> properties )
    {
        return delegate.createNode( properties );
    }

    public boolean nodeExists( long nodeId )
    {
        return delegate.nodeExists( nodeId );
    }

    public void setNodeProperties( long node, Map<String, Object> properties )
    {
        delegate.setNodeProperties( node, properties );
    }

    public boolean nodeHasProperty( long node, String propertyName )
    {
        return delegate.nodeHasProperty( node, propertyName );
    }

    public boolean relationshipHasProperty( long relationship, String propertyName )
    {
        return delegate.relationshipHasProperty( relationship, propertyName );
    }

    public void setNodeProperty( long node, String propertyName, Object propertyValue )
    {
        delegate.setNodeProperty( node, propertyName, propertyValue );
    }

    public void setRelationshipProperty( long relationship, String propertyName, Object propertyValue )
    {
        delegate.setRelationshipProperty( relationship, propertyName, propertyValue );
    }

    public Map<String, Object> getNodeProperties( long nodeId )
    {
        return delegate.getNodeProperties( nodeId );
    }

    public Iterable<Long> getRelationshipIds( long nodeId )
    {
        return delegate.getRelationshipIds( nodeId );
    }

    public Iterable<BatchRelationship> getRelationships( long nodeId )
    {
        return new IterableWrapper<BatchRelationship, SimpleRelationship>( delegate.getRelationships( nodeId ) )
        {
            @Override
            protected BatchRelationship underlyingObjectToObject( SimpleRelationship object )
            {
                return asNew( object );
            }
        };
    }

    public void createNode( long id, Map<String, Object> properties )
    {
        delegate.createNode( id, properties );
    }

    public long createRelationship( long node1, long node2, RelationshipType type, Map<String, Object> properties )
    {
        return delegate.createRelationship( node1, node2, type, properties );
    }

    public BatchRelationship getRelationshipById( long relId )
    {
        SimpleRelationship rel = delegate.getRelationshipById( relId );
        return asNew( rel );
    }

    private BatchRelationship asNew( SimpleRelationship rel )
    {
        return new BatchRelationship( rel.getId(), rel.getStartNode(), rel.getEndNode(), rel.getType() );
    }

    public void setRelationshipProperties( long rel, Map<String, Object> properties )
    {
        delegate.setRelationshipProperties( rel, properties );
    }

    public Map<String, Object> getRelationshipProperties( long relId )
    {
        return delegate.getRelationshipProperties( relId );
    }

    public void removeNodeProperty( long node, String property )
    {
        delegate.removeNodeProperty( node, property );
    }

    public void removeRelationshipProperty( long relationship, String property )
    {
        delegate.removeRelationshipProperty( relationship, property );
    }

    public void shutdown()
    {
        delegate.shutdown();
    }

    public String getStore()
    {
        return delegate.getStore();
    }

    public long getReferenceNode()
    {
        return delegate.getReferenceNode();
    }

    public GraphDatabaseService getGraphDbService()
    {
        return delegate.getGraphDbService();
    }

    @Override
    public String getStoreDir()
    {
        return delegate.getStore();
    }
}
