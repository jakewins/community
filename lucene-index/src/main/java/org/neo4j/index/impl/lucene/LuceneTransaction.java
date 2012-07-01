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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.index.base.AbstractIndex;
import org.neo4j.index.base.CommitContext;
import org.neo4j.index.base.EntityId;
import org.neo4j.index.base.IndexCommand;
import org.neo4j.index.base.IndexCommand.DeleteCommand;
import org.neo4j.index.base.IndexDefininitionsCommand;
import org.neo4j.index.base.IndexIdentifier;
import org.neo4j.index.base.IndexTransaction;
import org.neo4j.index.base.TxData;
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
        super.add( index, entity, key, value );
    }

    @Override
    protected <T extends PropertyContainer> void remove( AbstractIndex<T> index, T entity,
            String key, Object value )
    {
        if ( value != null )
            value = value instanceof ValueContext ? ((ValueContext) value).getCorrectValue() : value.toString();
        super.remove( index, entity, key, value );
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
    protected CommitContext newCommitContext( IndexIdentifier identifier )
    {
        LuceneDataSource dataSource = (LuceneDataSource) getDataSource();
        return new LuceneCommitContext( dataSource, identifier, dataSource.getType( identifier ), isRecovered() );
    }

    // This is all for the abandoned ids
    @Override
    protected void doPrepare()
    {
        addCommand( getDefinitions( false ) );
        boolean containsDeleteCommand = false;
        for ( Collection<IndexCommand> list : commandMap.values() )
        {
            for ( IndexCommand command : list )
            {
                if ( command instanceof DeleteCommand )
                    containsDeleteCommand = true;
                addCommand( command );
            }
        }
        if ( !containsDeleteCommand )
        { // unless the entire index is deleted
            addAbandonedEntitiesToTheTx();
        } // else: the DeleteCommand will clear abandonedIds
    }

    private void addAbandonedEntitiesToTheTx()
    {
        IndexDefininitionsCommand def = getDefinitions( true );
        for ( Map.Entry<IndexIdentifier, TransactionChangeSet> entry : txData.entrySet() )
        {
            Collection<Long> abandonedIds = ((LuceneIndex)entry.getValue().getIndex()).abandonedIds;
            if ( !abandonedIds.isEmpty() )
            {
                Collection<IndexCommand> commands = commandMap.get( entry.getKey() );
                for ( Long id : abandonedIds )
                {
                    IndexCommand command = def.remove( entry.getKey().getIndexName(),
                            entry.getKey().getEntityType(), EntityId.entityId( id ), null, null );
                    addCommand( command );
                    commands.add( command );
                }
                abandonedIds.clear();
            }
        }
    }

    <T extends PropertyContainer> IndexSearcher getAdditionsAsSearcher( LuceneIndex<T> index,
            QueryContext context )
    {
        TxData data = addedTxDataOrNull( index );
        return data != null ? ((LuceneTxData)data).asSearcher( context ) : null;
    }
}
