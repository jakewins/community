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
package org.neo4j.index.base.example;

import java.io.IOException;

import org.neo4j.index.base.AbstractIndex;
import org.neo4j.index.base.CommitContext;
import org.neo4j.index.base.EntityId;
import org.neo4j.index.base.IndexDataSource;
import org.neo4j.index.base.IndexIdentifier;
import org.neo4j.index.base.IndexTransaction;
import org.neo4j.index.base.TxData;
import org.neo4j.index.base.keyvalue.KeyValueTxData;
import org.neo4j.kernel.impl.transaction.xaframework.XaLogicalLog;

public class ExampleIndexTransaction extends IndexTransaction
{
    public ExampleIndexTransaction( int identifier, XaLogicalLog xaLog, IndexDataSource dataSource )
    {
        super( identifier, xaLog, dataSource );
    }

    @Override
    protected CommitContext newCommitContext( IndexIdentifier identifier )
    {
        return new CommitContext()
        {
            @Override
            public void remove( EntityId id ) throws IOException
            {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void remove( EntityId id, String key ) throws IOException
            {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void remove( EntityId id, String key, Object value ) throws IOException
            {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void close() throws IOException
            {
            }
            
            @Override
            public void add( EntityId id, String key, Object value ) throws IOException
            {
                // TODO Auto-generated method stub
//                getDataSource().
                
            }
        };
    }

    @Override
    protected TxData newTxData( AbstractIndex index, TxDataType txDataType )
    {
        return new KeyValueTxData();
    }
}
