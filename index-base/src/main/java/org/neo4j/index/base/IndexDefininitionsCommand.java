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

import static org.neo4j.helpers.collection.MapUtil.reverse;
import static org.neo4j.kernel.impl.util.IoPrimitiveUtils.read2bLengthAndString;
import static org.neo4j.kernel.impl.util.IoPrimitiveUtils.readByte;
import static org.neo4j.kernel.impl.util.IoPrimitiveUtils.write2bLengthAndString;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.neo4j.kernel.impl.transaction.xaframework.LogBuffer;
import org.neo4j.kernel.impl.transaction.xaframework.XaCommand;

/**
 * A command which have to be added first in each transaction. It will map index names
 * and keys to ids so that all other commands in that transaction only refer
 * to space efficient ids instead of names. This reduced the number of bytes needed for
 * commands by roughly 50% for transaction with more than a couple of commands in it,
 * depending on the size of the value.
 * 
 * After this command has been created it will act as a factory for other
 * commands so that it can spit out correct index name and key ids.
 */
public class IndexDefininitionsCommand extends XaCommand
{
    private final AtomicInteger nextIndexNameId = new AtomicInteger( 1 );
    private final AtomicInteger nextKeyId = new AtomicInteger( 1 );
    private final Map<String, Byte> indexNameIdRange;
    private final Map<String, Byte> keyIdRange;
    private final Map<Byte, String> idToIndexName;
    private final Map<Byte, String> idToKey;
    
    public IndexDefininitionsCommand()
    {
        indexNameIdRange = new HashMap<String, Byte>();
        keyIdRange = new HashMap<String, Byte>();
        idToIndexName = new HashMap<Byte, String>();
        idToKey = new HashMap<Byte, String>();
    }
    
    public IndexDefininitionsCommand( Map<String, Byte> indexNames, Map<String, Byte> keys )
    {
        indexNameIdRange = indexNames;
        keyIdRange = keys;
        idToIndexName = reverse( indexNames );
        idToKey = reverse( keys );
    }
    
    private static String getFromMap( Map<? extends Number, String> map, byte id )
    {
        if ( id == 0 )
            return null;
        
        String result = map.get( id );
        if ( result == null )
            throw new IllegalArgumentException( "" + id );
        return result;
    }

    public IndexCommand create( String indexName, EntityType entityType, Map<String, String> config )
    {
        return new IndexCommand.CreateCommand( indexNameId( indexName ),
                entityType.byteValue(), config );
    }
    
    public IndexCommand add( String indexName, EntityType entityType, EntityId entityId, String key,
            Object value )
    {
        switch ( entityType )
        {
        case NODE: return new IndexCommand.AddCommand( indexNameId( indexName ), entityType.byteValue(),
                entityId, keyId( key ), value );
        case RELATIONSHIP: return new IndexCommand.AddRelationshipCommand( indexNameId( indexName ), entityType.byteValue(),
                entityId, keyId( key ), value );
        default: throw new IllegalArgumentException( entityType.toString() );
        }
    }
    
    public IndexCommand remove( String indexName, EntityType entityType, EntityId entityId,
            String key, Object value )
    {
        return new IndexCommand.RemoveCommand( indexNameId( indexName ), entityType.byteValue(),
                entityId, key != null ? keyId( key ) : 0, value );
    }
    
    public IndexCommand delete( String indexName, EntityType entityType )
    {
        return new IndexCommand.DeleteCommand( indexNameId( indexName ), entityType.byteValue() );
    }
    
    public String getIndexName( byte id )
    {
        return getFromMap( idToIndexName, id );
    }
    
    public String getKey( byte id )
    {
        return getFromMap( idToKey, id );
    }

    private byte indexNameId( String indexName )
    {
        return id( indexName, indexNameIdRange, nextIndexNameId, idToIndexName );
    }
    
    private byte keyId( String key )
    {
        return id( key, keyIdRange, nextKeyId, idToKey );
    }

    private byte id( String key, Map<String, Byte> idRange, AtomicInteger nextId,
            Map<Byte, String> reverse )
    {
        if ( key == null ) return 0;
        Byte id = idRange.get( key );
        if ( id == null )
        {
            id = Byte.valueOf( (byte) nextId.incrementAndGet() );
            idRange.put( key, id );
            reverse.put( id, key );
        }
        return id;
    }

    @Override
    public void execute()
    {
    }

    @Override
    public void writeToFile( LogBuffer buffer ) throws IOException
    {
        buffer.put( (byte)(IndexCommand.DEFINE_COMMAND << 5) );
        buffer.put( (byte)0 );
        buffer.put( (byte)0 );
        writeMap( indexNameIdRange, buffer );
        writeMap( keyIdRange, buffer );
    }
    
    static Map<String, Byte> readMap( ReadableByteChannel channel, ByteBuffer buffer )
            throws IOException
    {
        Byte size = readByte( channel, buffer );
        if ( size == null ) return null;
        Map<String, Byte> result = new HashMap<String, Byte>();
        for ( int i = 0; i < size; i++ )
        {
            String key = read2bLengthAndString( channel, buffer );
            Byte id = readByte( channel, buffer );
            if ( key == null || id == null ) return null;
            result.put( key, id );
        }
        return result;
    }

    private static void writeMap( Map<String, Byte> map, LogBuffer buffer ) throws IOException
    {
        buffer.put( (byte)map.size() );
        for ( Map.Entry<String, Byte> entry : map.entrySet() )
        {
            write2bLengthAndString( buffer, entry.getKey() );
            buffer.put( entry.getValue() );
        }
    }
    
    @Override
    public boolean equals( Object obj )
    {
        IndexDefininitionsCommand other = (IndexDefininitionsCommand) obj;
        return indexNameIdRange.equals( other.indexNameIdRange ) &&
                keyIdRange.equals( other.keyIdRange );
    }
}
