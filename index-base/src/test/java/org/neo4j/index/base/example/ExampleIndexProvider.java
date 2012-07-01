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

import org.neo4j.index.base.AbstractIndexProvider;
import org.neo4j.index.base.IndexDataSource;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.index.IndexStore;
import org.neo4j.kernel.impl.nioneo.store.FileSystemAbstraction;
import org.neo4j.kernel.impl.transaction.xaframework.XaFactory;

public class ExampleIndexProvider extends AbstractIndexProvider
{
    public ExampleIndexProvider()
    {
        super( ExampleIndexDataSource.DATA_SOURCE_NAME );
    }

    @Override
    protected IndexDataSource newDataSource( Map<String, String> params, IndexStore indexStore,
            FileSystemAbstraction fileSystemAbstraction, XaFactory xaFactory )
    {
        return new ExampleIndexDataSource( new Config( params ), indexStore, fileSystemAbstraction, xaFactory );
    }
}
