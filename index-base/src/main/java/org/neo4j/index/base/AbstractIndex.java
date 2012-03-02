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

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.kernel.impl.core.ReadOnlyDbException;
import org.neo4j.kernel.impl.util.IoPrimitiveUtils;

public abstract class AbstractIndex<T extends PropertyContainer> implements Index<T>
{
    private final AbstractIndexImplementation provider;
    private final IndexIdentifier identifier;
    private volatile boolean deleted;
    
    protected AbstractIndex( AbstractIndexImplementation provider, IndexIdentifier identifier )
    {
        this.provider = provider;
        this.identifier = identifier;
    }
    
    protected AbstractIndexImplementation getProvider()
    {
        return provider;
    }
    
    protected IndexBaseXaConnection getConnection()
    {
        assertNotDeleted();
        if ( provider.broker() == null )
        {
            throw new ReadOnlyDbException();
        }
        return provider.broker().acquireResourceConnection();
    }
    
    protected IndexBaseXaConnection getReadOnlyConnection()
    {
        assertNotDeleted();
        return provider.broker() == null ? null : provider.broker().acquireReadOnlyResourceConnection();
    }
    
    public IndexIdentifier getIdentifier()
    {
        return identifier;
    }
    
    public String getName()
    {
        return identifier.getIndexName();
    }
    
    public EntityType getEntityTypeEnum()
    {
        return identifier.getEntityType();
    }
    
    @Override
    public GraphDatabaseService getGraphDatabase()
    {
        return provider.graphDb();
    }
    
    public Class<T> getEntityType()
    {
        return (Class<T>) identifier.getEntityType().getType();
    }
    
    protected abstract T idToEntity( EntityId id );
    
    /**
     * See {@link Index#add(PropertyContainer, String, Object)} for more generic
     * documentation.
     *
     * Adds key/value to the {@code entity} in this index. Added values are
     * searchable withing the transaction, but composite {@code AND}
     * queries aren't guaranteed to return added values correctly within that
     * transaction. When the transaction has been committed all such queries
     * are guaranteed to return correct results.
     *
     * @param entity the entity (i.e {@link Node} or {@link Relationship})
     * to associate the key/value pair with.
     * @param key the key in the key/value pair to associate with the entity.
     * @param value the value in the key/value pair to associate with the
     * entity.
     */
    public void add( T entity, String key, Object value )
    {
        assertKeyNotNull( key );
        IndexBaseXaConnection connection = getConnection();
        for ( Object oneValue : IoPrimitiveUtils.asArray( value ) )
        {
            connection.add( this, entity, key, oneValue );
        }
    }
    
    @Override
    public T putIfAbsent( T entity, String key, Object value )
    {
        return provider.graphDb().getNodeManager().indexPutIfAbsent( this, entity, key, value );
    }
    
    /**
     * See {@link Index#remove(PropertyContainer, String, Object)} for more
     * generic documentation.
     *
     * Removes key/value to the {@code entity} in this index. Removed values
     * are excluded withing the transaction, but composite {@code AND}
     * queries aren't guaranteed to exclude removed values correctly within
     * that transaction. When the transaction has been committed all such
     * queries are guaranteed to return correct results.
     *
     * @param entity the entity (i.e {@link Node} or {@link Relationship})
     * to dissociate the key/value pair from.
     * @param key the key in the key/value pair to dissociate from the entity.
     * @param value the value in the key/value pair to dissociate from the
     * entity.
     */
    public void remove( T entity, String key, Object value )
    {
        assertKeyNotNull( key );
        assertValueNotNull( value );
        IndexBaseXaConnection connection = getConnection();
        for ( Object oneValue : IoPrimitiveUtils.asArray( value ) )
        {
            connection.remove( this, entity, key, oneValue );
        }
    }
    
    public void delete()
    {
        getConnection().deleteIndex( this );
    }
    
    private void assertNotDeleted()
    {
        if ( deleted )
        {
            throw new IllegalStateException( "This index (" + identifier + ") has been deleted" );
        }
    }

    public void markAsDeleted()
    {
        this.deleted = true;
//        this.abandonedIds.clear();
    }

    protected void assertKeyNotNull( String key )
    {
        if ( key == null ) throw new IllegalArgumentException( "Key can't be null" );
    }

    private void assertValueNotNull( Object value )
    {
        if ( value == null ) throw new IllegalArgumentException( "Value can't be null" );
    }
}
