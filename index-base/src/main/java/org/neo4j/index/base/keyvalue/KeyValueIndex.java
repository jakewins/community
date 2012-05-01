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
package org.neo4j.index.base.keyvalue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.helpers.collection.CatchingIteratorWrapper;
import org.neo4j.index.base.AbstractIndex;
import org.neo4j.index.base.AbstractIndexImplementation;
import org.neo4j.index.base.EntityId;
import org.neo4j.index.base.IndexBaseXaConnection;
import org.neo4j.index.base.IndexHitsImpl;
import org.neo4j.index.base.IndexIdentifier;
import org.neo4j.index.base.IndexTransaction;

public abstract class KeyValueIndex<T extends PropertyContainer> extends AbstractIndex<T>
{
    protected KeyValueIndex( AbstractIndexImplementation implementation, IndexIdentifier identifier )
    {
        super( implementation, identifier );
    }

    // template method to read data from a key/value store. The actual work is delegated to the input ReadCallback
    protected final IndexHits<T> read( String key, Object value, ReadCallback callback )
    {
        IndexBaseXaConnection connection = getReadOnlyConnection();
        IndexTransaction tx = connection != null ? connection.getTx() : null;
        Collection<EntityId> added = tx != null ? tx.getAddedIds( this, key, value ) :
                Collections.<EntityId>emptyList();
        Collection<EntityId> removed = tx != null ? tx.getRemovedIds( this, key, value ) :
                Collections.<EntityId>emptyList();
        List<EntityId> ids = new ArrayList<EntityId>( added );
        getProvider().dataSource().getReadLock();
        try
        {
            callback.update( key, value, ids, removed );
        }
        finally
        {
            getProvider().dataSource().releaseReadLock();
        }
        Iterator<T> entities = new CatchingIteratorWrapper<T, EntityId>( ids.iterator() )
        {
            @Override
            protected boolean exceptionOk( Throwable t )
            {
                return t instanceof NotFoundException;
            }

            @Override
            protected T underlyingObjectToObject( EntityId id )
            {
                return idToEntity( id );
            }
        };
        return new IndexHitsImpl<T>( entities, ids.size() );
    }

    public IndexHits<T> query( String key, Object queryOrQueryObject )
    {
        throw new UnsupportedOperationException();
    }

    public IndexHits<T> query( Object queryOrQueryObject )
    {
        throw new UnsupportedOperationException();
    }
    
    public void remove(T entity, String key)
    {
        throw new UnsupportedOperationException();
    }
    
    public void remove(T entity)
    {
        throw new UnsupportedOperationException();
    }

    public static interface ReadCallback
    {
        void update( String key, Object value, List<EntityId> ids, Collection<EntityId> removed );
    }
}
