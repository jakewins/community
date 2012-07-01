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
package org.neo4j.index.base.example;

import java.util.Collection;
import java.util.List;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.index.base.AbstractIndex;
import org.neo4j.index.base.AbstractIndexImplementation;
import org.neo4j.index.base.EntityId;
import org.neo4j.index.base.IndexIdentifier;
import org.neo4j.index.base.ReadCallback;

public abstract class ExampleIndex<T extends PropertyContainer> extends AbstractIndex<T>
{
    protected ExampleIndex( AbstractIndexImplementation provider, IndexIdentifier identifier )
    {
        super( provider, identifier );
    }

    @Override
    public IndexHits<T> get( String key, Object value )
    {
        return read( key, value, new ReadCallback()
        {
            @Override
            public void read( String key, Object value, List<EntityId> ids, Collection<EntityId> removed )
            {
                // TODO search this index and add hits to 'ids'
            }
        } );
    }
    
    public class NodeIndex extends ExampleIndex<Node>
    {
        protected NodeIndex( AbstractIndexImplementation provider, IndexIdentifier identifier )
        {
            super( provider, identifier );
        }

        @Override
        protected Node idToEntity( EntityId id )
        {
            return getProvider().graphDb().getNodeById( id.getId() );
        }
    }
    
    public class RelationshipIndex extends ExampleIndex<Relationship>
    {
        protected RelationshipIndex( AbstractIndexImplementation provider, IndexIdentifier identifier )
        {
            super( provider, identifier );
        }

        @Override
        protected Relationship idToEntity( EntityId id )
        {
            return getProvider().graphDb().getRelationshipById( id.getId() );
        }
    }
}
