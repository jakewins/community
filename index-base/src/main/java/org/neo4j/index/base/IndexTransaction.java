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

import java.io.IOException;
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
import org.neo4j.helpers.Pair;
import org.neo4j.index.base.IndexCommand.CreateCommand;
import org.neo4j.index.base.IndexCommand.DeleteCommand;
import org.neo4j.index.base.keyvalue.KeyValueTxData;
import org.neo4j.kernel.impl.transaction.xaframework.XaCommand;
import org.neo4j.kernel.impl.transaction.xaframework.XaLogicalLog;
import org.neo4j.kernel.impl.transaction.xaframework.XaTransaction;

public class IndexTransaction extends XaTransaction
{
    protected final Map<IndexIdentifier, TxDataBoth> txData =
            new HashMap<IndexIdentifier, TxDataBoth>();
    
    private IndexDefininitionsCommand definitions;
    protected final Map<IndexIdentifier,Collection<IndexCommand>> commandMap = 
            new HashMap<IndexIdentifier,Collection<IndexCommand>>();
    private final IndexDataSource dataSource;

    public IndexTransaction( int identifier, XaLogicalLog xaLog, IndexDataSource dataSource )
    {
        super( identifier, xaLog );
        this.dataSource = dataSource;
    }

    protected <T extends PropertyContainer> void add( AbstractIndex<T> index, T entity,
            String key, Object value )
    {
        TxDataBoth data = getTxData( index, true );
        insert( index, entity, key, value, data.added( true ), data.removed( false ) );
        queueCommand( index.getIdentifier(), getDefinitions( true ).add( index.getName(), index.getEntityTypeEnum(),
                getEntityId( entity ), key, value ) );
    }
    
    protected <T extends PropertyContainer> void remove( AbstractIndex<T> index, T entity,
            String key, Object value )
    {
        TxDataBoth data = getTxData( index, true );
        insert( index, entity, key, value, data.removed( true ), data.added( false ) );
        queueCommand( index.getIdentifier(), getDefinitions( true ).remove( index.getName(), index.getEntityTypeEnum(),
                getEntityId( entity ), key, value ) );
    }
    
    protected void createIndex( EntityType entityType,
            String indexName, Map<String, String> config )
    {
        queueCommand( new IndexIdentifier( entityType, indexName ),
                getDefinitions( true ).create( indexName, entityType, config ) );
    }
    
    protected <T extends PropertyContainer> void deleteIndex( AbstractIndex<T> index )
    {
        txData.put( index.getIdentifier(), new DeletedTxData( index ) );
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
    
    protected IndexDefininitionsCommand getDefinitions( boolean allowCreate )
    {
        if ( definitions == null && allowCreate )
            definitions = new IndexDefininitionsCommand();
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
        if ( removed == null )
            return Collections.emptySet();
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
        if ( added == null )
            return Collections.emptySet();
        return added.get( key, value );
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
            return null;
        return data.added( false );
    }
    
    protected <T extends PropertyContainer> TxData removedTxDataOrNull( AbstractIndex<T> index )
    {
        TxDataBoth data = getTxData( index, false );
        if ( data == null )
            return null;
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
        commandMap.clear();
        closeTxData();
    }
    
    @Override
    protected void doCommit() throws XAException
    {
        IndexDefininitionsCommand def = getDefinitions( false );
        dataSource.getWriteLock();
        try
        {
            Map<IndexIdentifier, Pair<ChangeSet, Collection<IndexCommand>>> changeset =
                    new HashMap<IndexIdentifier, Pair<ChangeSet, Collection<IndexCommand>>>();
            for ( Map.Entry<IndexIdentifier, Collection<IndexCommand>> entry : getCommands().entrySet() )
            {
                if ( entry.getValue().isEmpty() )
                    continue;
                IndexIdentifier identifier = entry.getKey();
                IndexCommand firstCommand = entry.getValue().iterator().next();
                if ( firstCommand instanceof CreateCommand )
                {
                    dataSource.createIndex( identifier, ((CreateCommand) firstCommand).getConfig() );
                    continue;
                }
                else if ( firstCommand instanceof DeleteCommand )
                {
                    dataSource.deleteIndex( identifier, isRecovered() );
                    continue;
                }
                
                changeset.put( identifier, Pair.of( (ChangeSet)txData.get( identifier ), entry.getValue() ) );
            }
            
            dataSource.applyChangeSet( def, changeset, isRecovered() );
            dataSource.setLastCommittedTxId( getCommitTxId() );
            closeTxData();
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
        finally
        {
            dataSource.releaseWriteLock();
        }
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
    
    protected TxData newTxData( AbstractIndex index, TxDataType txDataType )
    {
        return new KeyValueTxData();
    }
    
    public static enum TxDataType
    {
        ADD,
        REMOVE;
    }
    
    public class TxDataBoth implements ChangeSet
    {
        private TxData add;
        private TxData remove;
        private final AbstractIndex index;
        
        public TxDataBoth( AbstractIndex index )
        {
            this.index = index;
        }
        
        public AbstractIndex getIndex()
        {
            return index;
        }
        
        @Override
        public TxData added()
        {
            return this.add;
        }
        
        @Override
        public TxData removed()
        {
            return this.remove;
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

    private class DeletedTxData extends TxDataBoth
    {
        public DeletedTxData( AbstractIndex index )
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
