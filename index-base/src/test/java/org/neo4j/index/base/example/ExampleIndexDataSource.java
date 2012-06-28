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

import java.util.Map;

import org.neo4j.index.base.IndexDataSource;
import org.neo4j.index.base.IndexIdentifier;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.index.IndexStore;
import org.neo4j.kernel.impl.nioneo.store.FileSystemAbstraction;
import org.neo4j.kernel.impl.transaction.xaframework.XaFactory;
import org.neo4j.kernel.impl.transaction.xaframework.XaLogicalLog;
import org.neo4j.kernel.impl.transaction.xaframework.XaTransaction;

public class ExampleIndexDataSource extends IndexDataSource
{
    public ExampleIndexDataSource( byte[] branchId, String dataSourceName, Config config, IndexStore indexStore,
            FileSystemAbstraction fileSystem, XaFactory xaFactory )
    {
        super( branchId, dataSourceName, config, indexStore, fileSystem, xaFactory, 1 );
    }

    @Override
    protected void actualClose()
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void flushAll()
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected XaTransaction createTransaction( int identifier, XaLogicalLog logicalLog )
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void deleteIndex( IndexIdentifier identifier, boolean recovered )
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void createIndex( IndexIdentifier identifier, Map<String, String> config )
    {
        // TODO Auto-generated method stub
        
    }
}
