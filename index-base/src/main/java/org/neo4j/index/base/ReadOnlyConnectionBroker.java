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

import javax.transaction.TransactionManager;

import org.neo4j.kernel.impl.core.ReadOnlyDbException;

public class ReadOnlyConnectionBroker<T extends IndexBaseXaConnection> extends IndexConnectionBroker<T>
{
    public ReadOnlyConnectionBroker( TransactionManager transactionManager )
    {
        super( transactionManager );
    }
    
    @Override
    public T acquireResourceConnection()
    {
        throw new ReadOnlyDbException();
    }
    
    @Override
    public T acquireReadOnlyResourceConnection()
    {
        return null;
    }
    
    @Override
    protected T newConnection()
    {
        throw new ReadOnlyDbException();
    }
}
