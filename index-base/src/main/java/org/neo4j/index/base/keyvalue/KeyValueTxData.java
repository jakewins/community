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

import static org.neo4j.index.base.IndexTransaction.merge;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.neo4j.index.base.EntityId;
import org.neo4j.index.base.TxData;

public class KeyValueTxData implements TxData
{
    private Map<String, Map<Object, Set<EntityId>>> data;
    
    // Just act as an optimization in that you don't have to do the double hashmap
    // lookup for orphans if no such entities have been added to this state
    private boolean hasOrphans;
    
    public void add( EntityId entityId, String key, Object value )
    {
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

    public void close()
    {
    }

    public void remove( EntityId entityId, String key, Object value )
    {
        if ( data == null ) return;
        Collection<EntityId> ids = idCollection( key, value, false );
        if ( ids != null ) ids.remove( entityId );
    }

    public Set<EntityId> get( String key, Object value )
    {
        Set<EntityId> ids = idCollection( key, value, false );
        return ids == null ? Collections.<EntityId>emptySet() : ids;
    }
    
    public EntityId getSingle( String key, Object value )
    {
        throw new UnsupportedOperationException();
    }
    
    public Collection<EntityId> getOrphans( String keyOrNull )
    {
        return !hasOrphans ? null :
                merge( idCollection( null, null, false ), idCollection( keyOrNull, null, false ) );
    }
    
    public Map<String, Map<Object, Set<EntityId>>> raw()
    {
        return this.data;
    }
}
