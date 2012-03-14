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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.transaction.xa.XAException;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.kernel.impl.transaction.xaframework.XaCommand;
import org.neo4j.kernel.impl.transaction.xaframework.XaLogicalLog;
import org.neo4j.kernel.impl.transaction.xaframework.XaTransaction;

public abstract class IndexTransaction extends XaTransaction
{
    private final Map<IndexIdentifier, TxDataBoth> txData =
            new HashMap<IndexIdentifier, TxDataBoth>();
    
    private IndexDefininitionsCommand definitions;
    private final Map<IndexIdentifier,Collection<IndexCommand>> commandMap = 
            new HashMap<IndexIdentifier,Collection<IndexCommand>>();
    private final IndexDataSource dataSource;

    public IndexTransaction( int identifier, XaLogicalLog xaLog, IndexDataSource dataSource )
    {
        super( identifier, xaLog );
        this.dataSource = dataSource;
    }

    protected abstract <T extends PropertyContainer> void add( AbstractIndex<T> index, T entity,
            String key, Object value );
    
    protected abstract <T extends PropertyContainer> void remove( AbstractIndex<T> index, T entity,
            String key, Object value );
    
    protected abstract void createIndex( EntityType entityType,
            String indexName, Map<String, String> config );
    
    protected <T extends PropertyContainer> void deleteIndex( AbstractIndex<T> index )
    {
        txData.put( index.getIdentifier(), new DeletedTxDataBoth( index ) );
        queueCommand( index.getIdentifier(), getDefinitions( true ).delete( index.getName(), index.getEntityTypeEnum() ) );
    }
    
    protected IndexDataSource getDataSource()
    {
        return this.dataSource;
    }
    
    protected EntityId getEntityId( PropertyContainer entity )
    {
        return entity instanceof Node ? EntityId.entityId( (Node) entity ) :
                EntityId.entityId( (Relationship) entity );
    }
    
    protected Map<IndexIdentifier, TxDataBoth> getTxData()
    {
        return this.txData;
    }
    
    protected Map<IndexIdentifier, Collection<IndexCommand>> getCommands()
    {
        return this.commandMap;
    }
    
    protected <T extends PropertyContainer> TxDataBoth getTxData( IndexIdentifier identifier )
    {
        return txData.get( identifier );
    }
    
    protected <T extends PropertyContainer> TxDataBoth getTxData( AbstractIndex<T> index,
            boolean createIfNotExists )
    {
        IndexIdentifier identifier = index.getIdentifier();
        TxDataBoth data = txData.get( identifier );
        if ( data == null && createIfNotExists )
        {
            data = new TxDataBoth( index );
            txData.put( identifier, data );
        }
        return data;
    }
    
    protected IndexDefininitionsCommand getDefinitions( boolean create )
    {
        if ( definitions == null && create )
        {
            definitions = new IndexDefininitionsCommand();
        }
        return definitions;
    }
    
    protected void queueCommand( IndexIdentifier identifier, XaCommand command )
    {
        Collection<IndexCommand> commands = commandMap.get( identifier );
        if ( commands == null )
        {
            commands = new ArrayList<IndexCommand>();
            commandMap.put( identifier, commands );
        }
        else if ( command instanceof IndexCommand.DeleteCommand )
        {
            commands.clear();
        }
        commands.add( (IndexCommand) command );
    }
    
    protected <T extends PropertyContainer> void insert( AbstractIndex<T> index,
            T entity, String key, Object value, TxData insertInto, TxData removeFrom )
    {
        EntityId id = getEntityId( entity );
        if ( removeFrom != null )
        {
            removeFrom.remove( id, key, value );
        }
        insertInto.add( id, key, value );
    }

    public static Collection<EntityId> merge( Collection<EntityId> c1, Collection<EntityId> c2 )
    {
        boolean c1Empty = c1 == null || c1.isEmpty();
        boolean c2Empty = c2 == null || c2.isEmpty();
        if ( c1Empty && c2Empty )
        {
            return Collections.<EntityId>emptySet();
        }
        else if ( !c1Empty && !c2Empty )
        {
            Collection<EntityId> result = new HashSet<EntityId>( c1 );
            result.addAll( c2 );
            return result;
        }
        else
        {
            return !c1Empty ? c1 : c2;
        }
    }
    
    public <T extends PropertyContainer> Collection<EntityId> getRemovedIds( AbstractIndex<T> index,
            String key, Object value )
    {
        TxData removed = removedTxDataOrNull( index );
        if ( removed == null ) return Collections.emptySet();
        return merge( removed.get( key, value ), removed.getOrphans( key ) );
    }
    
    public <T extends PropertyContainer> EntityId getSingleRemovedId( AbstractIndex<T> index,
            String key, Object value )
    {
        TxData removed = removedTxDataOrNull( index );
        return removed != null ? removed.getSingle( key, value ) : null;
    }
    
    public <T extends PropertyContainer> Collection<EntityId> getAddedIds( AbstractIndex<T> index,
            String key, Object value )
    {
        TxData added = addedTxDataOrNull( index );
        if ( added == null ) return Collections.emptySet();
        Collection<EntityId> ids = added.get( key, value );
        
        return ids;
    }
    
    public <T extends PropertyContainer> EntityId getSingleAddedId( AbstractIndex<T> index,
            String key, Object value )
    {
        TxData added = addedTxDataOrNull( index );
        return added != null ? added.getSingle( key, value ) : null;
    }
    
    protected <T extends PropertyContainer> TxData addedTxDataOrNull( AbstractIndex<T> index )
    {
        TxDataBoth data = getTxData( index, false );
        if ( data == null )
        {
            return null;
        }
        return data.added( false );
    }
    
    protected <T extends PropertyContainer> TxData removedTxDataOrNull( AbstractIndex<T> index )
    {
        TxDataBoth data = getTxData( index, false );
        if ( data == null )
        {
            return null;
        }
        return data.removed( false );
    }
    
    @Override
    protected void doPrepare() throws XAException
    {
        addCommand( definitions );
        for ( Collection<IndexCommand> list : commandMap.values() )
        {
            for ( XaCommand command : list )
            {
                addCommand( command );
            }
        }
    }
    
    @Override
    protected void doAddCommand( XaCommand command )
    { // we override inject command and manage our own in memory command list
    }
    
    @Override
    protected void injectCommand( XaCommand command )
    {
        if ( command instanceof IndexDefininitionsCommand )
        {
            setDefinitions( (IndexDefininitionsCommand) command );
        }
        else
        {
            IndexCommand indexCommand = (IndexCommand) command;
            IndexIdentifier identifier = new IndexIdentifier(
                    indexCommand.getEntityTypeClass(),
                    definitions.getIndexName( indexCommand.getIndexNameId() ) );
            queueCommand( identifier, command );
        }
    }

    private void setDefinitions( IndexDefininitionsCommand command )
    {
        if ( definitions != null )
        {
            throw new IllegalStateException();
        }
        definitions = command;
    }

    protected void closeTxData()
    {
        for ( TxDataBoth data : this.txData.values() )
        {
            data.close();
        }
        this.txData.clear();
    }

    @Override
    protected void doRollback()
    {
        // TODO Auto-generated method stub
        commandMap.clear();
        closeTxData();
    }

    @Override
    public boolean isReadOnly()
    {
        if ( !commandMap.isEmpty() )
        {
            return false;
        }
        
        for ( TxDataBoth data : txData.values() )
        {
            if ( data.isDeleted() || data.add != null || data.remove != null )
            {
                return false;
            }
        }
        return true;
    }
    
    protected abstract TxData newTxData( AbstractIndex index, TxDataType txDataType );
    
    public static enum TxDataType
    {
        ADD,
        REMOVE;
    }
    
    // Bad name
    public class TxDataBoth
    {
        private TxData add;
        private TxData remove;
        @SuppressWarnings("unchecked")
        private final AbstractIndex index;
        
        @SuppressWarnings("unchecked")
        public TxDataBoth( AbstractIndex index )
        {
            this.index = index;
        }
        
        public AbstractIndex getIndex()
        {
            return index;
        }
        
        public TxData added( boolean createIfNotExists )
        {
            if ( this.add == null && createIfNotExists )
            {
                this.add = newTxData( index, TxDataType.ADD );
            }
            return this.add;
        }
        
        public TxData removed( boolean createIfNotExists )
        {
            if ( this.remove == null && createIfNotExists )
            {
                this.remove = newTxData( index, TxDataType.REMOVE );
            }
            return this.remove;
        }
        
        void close()
        {
            safeClose( add );
            safeClose( remove );
        }

        private void safeClose( TxData data )
        {
            if ( data != null )
            {
                data.close();
            }
        }
        
        public boolean isDeleted()
        {
            return false;
        }
    }

    private class DeletedTxDataBoth extends TxDataBoth
    {
        public DeletedTxDataBoth( AbstractIndex index )
        {
            super( index );
        }

        @Override
        public TxData added( boolean createIfNotExists )
        {
            throw illegalStateException();
        }
        
        @Override
        public TxData removed( boolean createIfNotExists )
        {
            throw illegalStateException();
        }

        private IllegalStateException illegalStateException()
        {
            throw new IllegalStateException( "This index (" + getIndex().getIdentifier() + 
                    ") has been marked as deleted in this transaction" );
        }
        
        @Override
        public boolean isDeleted()
        {
            return true;
        }
    }
}
