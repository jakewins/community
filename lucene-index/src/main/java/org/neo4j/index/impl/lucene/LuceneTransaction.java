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

import static org.neo4j.index.base.EntityType.entityType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.index.base.AbstractIndex;
import org.neo4j.index.base.EntityId;
import org.neo4j.index.base.EntityType;
import org.neo4j.index.base.IndexCommand;
import org.neo4j.index.base.IndexCommand.AddCommand;
import org.neo4j.index.base.IndexCommand.AddRelationshipCommand;
import org.neo4j.index.base.IndexCommand.CreateCommand;
import org.neo4j.index.base.IndexCommand.DeleteCommand;
import org.neo4j.index.base.IndexCommand.RemoveCommand;
import org.neo4j.index.base.IndexDefininitionsCommand;
import org.neo4j.index.base.IndexIdentifier;
import org.neo4j.index.base.IndexTransaction;
import org.neo4j.index.base.TxData;
import org.neo4j.index.impl.lucene.CommitContext.DocumentContext;
import org.neo4j.index.lucene.QueryContext;
import org.neo4j.index.lucene.ValueContext;
import org.neo4j.kernel.impl.transaction.xaframework.XaLogicalLog;

class LuceneTransaction extends IndexTransaction
{
    LuceneTransaction( int identifier, XaLogicalLog xaLog,
        LuceneDataSource luceneDs )
    {
        super( identifier, xaLog, luceneDs );
    }
    
    @Override
    protected TxData newTxData( AbstractIndex index, TxDataType txDataType )
    {
        return ((LuceneIndex)index).type.newTxData( (LuceneIndex)index );
    }

    @Override
    protected <T extends PropertyContainer> void add( AbstractIndex<T> index, T entity,
            String key, Object value )
    {
        value = value instanceof ValueContext ? ((ValueContext) value).getCorrectValue() : value.toString();
        TxDataBoth data = getTxData( index, true );
        insert( index, entity, key, value, data.added( true ), data.removed( false ) );
        queueCommand( index.getIdentifier(), getDefinitions( true ).add( index.getName(), index.getEntityTypeEnum(),
                getEntityId( entity ), key, value ) );
    }

    @Override
    protected <T extends PropertyContainer> void remove( AbstractIndex<T> index, T entity,
            String key, Object value )
    {
        TxDataBoth data = getTxData( index, true );
        if ( value != null )
        {
            value = value instanceof ValueContext ? ((ValueContext) value).getCorrectValue() : value.toString();
        }
        insert( index, entity, key, value, data.removed( true ), data.added( false ) );
        queueCommand( index.getIdentifier(), getDefinitions( true ).remove( index.getName(), index.getEntityTypeEnum(),
                getEntityId( entity ), key, value ) );
    }
    
    <T extends PropertyContainer> Collection<EntityId> getRemovedIds( LuceneIndex<T> index, Query query )
    {
        LuceneTxData removed = (LuceneTxData) removedTxDataOrNull( index );
        if ( removed == null )
        {
            return Collections.emptySet();
        }
        Collection<EntityId> ids = removed.query( query, null );
        return ids != null ? ids : Collections.<EntityId>emptySet();
    }
    
    <T extends PropertyContainer> Collection<EntityId> getAddedIds( LuceneIndex<T> index,
            Query query, QueryContext contextOrNull )
    {
        LuceneTxData added = (LuceneTxData) addedTxDataOrNull( index );
        if ( added == null )
        {
            return Collections.emptySet();
        }
        Collection<EntityId> ids = added.query( query, contextOrNull );
        return ids != null ? ids : Collections.<EntityId>emptySet();
    }
    
    @Override
    protected void doCommit()
    {
        LuceneDataSource dataSource = (LuceneDataSource) getDataSource();
        IndexDefininitionsCommand def = getDefinitions( false );
        dataSource.getWriteLock();
        try
        {
            for ( Map.Entry<IndexIdentifier, Collection<IndexCommand>> entry : getCommands().entrySet() )
            {
                if ( entry.getValue().isEmpty() ) continue;
                IndexIdentifier identifier = entry.getKey();
                IndexCommand firstCommand = entry.getValue().iterator().next();
                if ( firstCommand instanceof CreateCommand )
                {
                    CreateCommand createCommand = (CreateCommand) firstCommand;
                    dataSource.getIndexStore().setIfNecessary( entityType( createCommand.getEntityType() ).getType(),
                            def.getIndexName( createCommand.getIndexNameId() ), createCommand.getConfig() );
                    continue;
                }
                else if ( firstCommand instanceof DeleteCommand )
                {
                    dataSource.deleteIndex( identifier, isRecovered() );
                    continue;
                }
                
                IndexType type = dataSource.getType( identifier );
                CommitContext context = new CommitContext( dataSource, identifier, type );
                for ( IndexCommand command : entry.getValue() )
                {
                    if ( command instanceof AddCommand || command instanceof AddRelationshipCommand )
                    {
                        context.ensureWriterInstantiated();
                        String key = def.getKey( command.getKeyId() );
                        context.indexType.addToDocument( context.getDocument( command.getEntityId(), true ).document, key, command.getValue() );
//                        context.dataSource.invalidateCache( context.identifier, key, command.getValue() );
                    }
                    else if ( command instanceof RemoveCommand )
                    {
                        context.ensureWriterInstantiated();
                        DocumentContext document = context.getDocument( command.getEntityId(), false );
                        if ( document != null )
                        {
                            String key = def.getKey( command.getKeyId() );
                            context.indexType.removeFromDocument( document.document, key, command.getValue() );
//                            context.dataSource.invalidateCache( context.identifier, key, value );
                        }
                    }
                    else if ( command instanceof DeleteCommand )
                    {
                        context.documents.clear();
                        context.dataSource.deleteIndex( context.identifier, isRecovered() );
                    }
                    else
                    {
                        throw new IllegalArgumentException( command + ", " + command.getClass().getName() );
                    }
                }
                
                applyDocuments( context.writer, type, context.documents );
                if ( context.writer != null )
                {
                    dataSource.invalidateIndexSearcher( identifier );
                }
            }
            
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

    private void applyDocuments( IndexWriter writer, IndexType type,
            Map<Long, DocumentContext> documents ) throws IOException
    {
        for ( Map.Entry<Long, DocumentContext> entry : documents.entrySet() )
        {
            DocumentContext context = entry.getValue();
            if ( context.exists )
            {
                if ( LuceneDataSource.documentIsEmpty( context.document ) )
                {
                    writer.deleteDocuments( type.idTerm( context.entityId ) );
                }
                else
                {
                    writer.updateDocument( type.idTerm( context.entityId ), context.document );
                }
            }
            else
            {
                writer.addDocument( context.document );
            }
        }
    }

    // TODO this is all for the abandoned ids
//    @Override
//    protected void doPrepare()
//    {
//        boolean containsDeleteCommand = false;
//        for ( CommandList list : commandMap.values() )
//        {
//            for ( IndexCommand command : list.commands )
//            {
//                if ( command instanceof DeleteCommand )
//                {
//                    containsDeleteCommand = true;
//                }
//                addCommand( command );
//            }
//        }
//        if ( !containsDeleteCommand )
//        { // unless the entire index is deleted
//            addAbandonedEntitiesToTheTx();
//        } // else: the DeleteCommand will clear abandonedIds
//    }
//
//    private void addAbandonedEntitiesToTheTx()
//    {
//        for ( Map.Entry<IndexIdentifier, TxDataBoth> entry : txData.entrySet() )
//        {
//            Collection<Long> abandonedIds = entry.getValue().index.abandonedIds;
//            if ( !abandonedIds.isEmpty() )
//            {
//                CommandList commands = commandMap.get( entry.getKey() );
//                for ( Long id : abandonedIds )
//                {
//                    RemoveCommand command = new RemoveCommand( entry.getKey(), entry.getKey().entityTypeByte, id, null, null );
//                    addCommand( command );
//                    commands.add( command );
//                }
//                abandonedIds.clear();
//            }
//        }
//    }

    static class CommandList
    {
        private final List<IndexCommand> commands = new ArrayList<IndexCommand>();
        private boolean containsWrites;
        
        void add( IndexCommand command )
        {
            this.commands.add( command );
        }
        
        boolean containsWrites()
        {
            return containsWrites;
        }
        
        void clear()
        {
            commands.clear();
            containsWrites = false;
        }

        void incCounter( IndexCommand command )
        {
            if ( command.isConsideredNormalWriteCommand() )
            {
                containsWrites = true;
            }
        }
        
        boolean isEmpty()
        {
            return commands.isEmpty();
        }
        
        boolean isRecovery()
        {
            return commands.get( 0 ).isRecovered();
        }
    }

    @Override
    protected void createIndex( EntityType entityType, String indexName, Map<String, String> config )
    {
        queueCommand( new IndexIdentifier( entityType, indexName ),
                getDefinitions( true ).create( indexName, entityType, config ) );
    }

    <T extends PropertyContainer> IndexSearcher getAdditionsAsSearcher( LuceneIndex<T> index,
            QueryContext context )
    {
        TxData data = addedTxDataOrNull( index );
        return data != null ? ((LuceneTxData)data).asSearcher( context ) : null;
    }
}
