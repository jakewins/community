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

import static org.neo4j.index.base.IndexTransaction.merge;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.neo4j.index.base.EntityId;
import org.neo4j.index.lucene.QueryContext;
import org.neo4j.index.lucene.ValueContext;

public class ExactTxData extends LuceneTxData
{
    private Map<String, Map<Object, Set<EntityId>>> data;
    private boolean hasOrphans;
    private FullTxData fullTxData;

    ExactTxData( LuceneIndex index )
    {
        super( index );
    }

    @Override
    public void add( EntityId entityId, String key, Object value )
    {
        if ( fullTxData != null ) fullTxData.add( entityId, key, value );
        idCollection( key, value, true ).add( entityId );
    }

    private Set<EntityId> idCollection( String key, Object value, boolean create )
    {
        Map<Object, Set<EntityId>> keyMap = keyMap( key, create );
        if ( keyMap == null ) return null;
        Set<EntityId> ids = keyMap.get( value );
        if ( ids == null && create )
        {
            ids = new HashSet<EntityId>();
            keyMap.put( value, ids );
            if ( value == null ) hasOrphans = true;
        }
        return ids;
    }

    private Map<Object, Set<EntityId>> keyMap( String key, boolean create )
    {
        if ( data == null )
        {
            if ( create ) data = new HashMap<String, Map<Object,Set<EntityId>>>();
            else return null;
        }

        Map<Object, Set<EntityId>> inner = data.get( key );
        if ( inner == null && create )
        {
            inner = new HashMap<Object, Set<EntityId>>();
            data.put( key, inner );
            if ( key == null ) hasOrphans = true;
        }
        return inner;
    }

    private FullTxData toFullTxData()
    {
        FullTxData data = new FullTxData( index );
        if ( this.data != null )
        {
            for ( Map.Entry<String, Map<Object, Set<EntityId>>> entry : this.data.entrySet() )
            {
                String key = entry.getKey();
                for ( Map.Entry<Object, Set<EntityId>> valueEntry : entry.getValue().entrySet() )
                {
                    Object value = valueEntry.getKey();
                    for ( EntityId id : valueEntry.getValue() )
                    {
                        data.add( id, key, value );
                    }
                }
            }
        }
        return data;
    }

    @Override
    public void close()
    {
    }

    @Override
    Collection<EntityId> query( Query query, QueryContext contextOrNull )
    {
        if ( contextOrNull != null && contextOrNull.getTradeCorrectnessForSpeed() ) return Collections.<EntityId>emptyList();
        return getFullTxData().query( query, contextOrNull );
    }

    private FullTxData getFullTxData()
    {
        if ( fullTxData == null ) fullTxData = toFullTxData();
        return fullTxData;
    }

    @Override
    public void remove( EntityId entityId, String key, Object value )
    {
        if ( data == null ) return;
        if ( key == null || value == null )
        {
            getFullTxData().remove( entityId, key, value );
        }
        else
        {
            if ( fullTxData != null ) fullTxData.remove( entityId, key, value );
            Collection<EntityId> ids = idCollection( key, value, false );
            if ( ids != null ) ids.remove( entityId );
        }
    }

    @Override
    public Collection<EntityId> get( String key, Object value )
    {
        if ( fullTxData != null ) return fullTxData.get( key, value );
        value = value instanceof ValueContext ? ((ValueContext) value).getCorrectValue() : value.toString();
        Set<EntityId> ids = idCollection( key, value, false );
        return ids == null || ids.isEmpty() ? Collections.<EntityId>emptySet() : ids;
    }
    
    @Override
    public Collection<EntityId> getOrphans( String key )
    {
        if ( fullTxData != null ) return fullTxData.getOrphans( key );
        if ( !hasOrphans ) return null;
        Set<EntityId> orphans = idCollection( null, null, false );
        Set<EntityId> keyOrphans = idCollection( key, null, false );
        return merge( orphans, keyOrphans );
    }
    
    @Override
    IndexSearcher asSearcher( QueryContext context )
    {
        if ( context != null && context.getTradeCorrectnessForSpeed() ) return null;
        return getFullTxData().asSearcher( context );
    }
    
    @Override
    public Map<String, Map<Object, Set<EntityId>>> raw()
    {
        return data;
    }
}
