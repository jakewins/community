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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.neo4j.index.base.EntityId;
import org.neo4j.index.base.TxData;

public class OneToOneTxData implements TxData
{
    private final Map<String, Map<Object, EntityId>> data = new HashMap<String, Map<Object,EntityId>>();
    
    @Override
    public void add( EntityId entityId, String key, Object value )
    {
        keyMap( key, true ).put( value, entityId );
    }

    private Map<Object, EntityId> keyMap( String key, boolean create )
    {
        Map<Object, EntityId> inner = data.get( key );
        if ( inner == null && create )
        {
            inner = new HashMap<Object, EntityId>();
            data.put( key, inner );
        }
        return inner;
    }

    @Override
    public void close()
    {
    }

    @Override
    public void remove( EntityId entityId, String key, Object value )
    {
        Map<Object, EntityId> keyMap = keyMap( key, false );
        if ( keyMap != null )
        {
            keyMap.remove( value );
        }
    }

    @Override
    public Set<EntityId> get( String key, Object value )
    {
        // TODO Hmm, shouldn't be called for performance
        EntityId result = getSingle( key, value );
        return result != null ? new HashSet<EntityId>( Arrays.asList( result ) ) : null;
    }
    
    @Override
    public Collection<EntityId> getOrphans( String key )
    {
        return Collections.emptyList();
    }
    
    @Override
    public EntityId getSingle( String key, Object value )
    {
        Map<Object, EntityId> keyMap = keyMap( key, false );
        return keyMap != null ? keyMap.get( value ) : null;
    }
    
    @Override
    public Map<String, Map<Object, Set<EntityId>>> raw()
    {
        throw new UnsupportedOperationException();
    }
}
