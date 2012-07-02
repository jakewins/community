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
import java.util.Collection;
import java.util.Map;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.helpers.Pair;
import org.neo4j.index.base.AbstractIndexImplementation;
import org.neo4j.index.base.ChangeSet;
import org.neo4j.index.base.IndexCommand;
import org.neo4j.index.base.IndexDataSource;
import org.neo4j.index.base.IndexDefininitionsCommand;
import org.neo4j.index.base.IndexIdentifier;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.index.IndexStore;
import org.neo4j.kernel.impl.nioneo.store.FileSystemAbstraction;
import org.neo4j.kernel.impl.transaction.xaframework.XaFactory;

public class ExampleIndexDataSource extends IndexDataSource
{
    public static final String DATA_SOURCE_NAME = "example";
    public static final byte[] BRANCH_ID = "example".getBytes();
    
    public ExampleIndexDataSource( Config config, IndexStore indexStore,
            FileSystemAbstraction fileSystem, XaFactory xaFactory )
    {
        super( BRANCH_ID, DATA_SOURCE_NAME, config, indexStore, fileSystem, xaFactory, 1 );
    }

    @Override
    protected void doClose()
    {
    }

    @Override
    protected void flushAll()
    {
    }

    @Override
    protected void doCreateIndex( IndexIdentifier identifier, Map<String, String> config )
    {
    }
    
    @Override
    protected void doDeleteIndex( IndexIdentifier identifier, boolean recovered )
    {
    }
    
    @Override
    public void applyChangeSet( IndexDefininitionsCommand definitions,
            Map<IndexIdentifier, Pair<ChangeSet, Collection<IndexCommand>>> changeset, boolean recovered )
            throws IOException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected Index<Node> instantiateNodeIndex( AbstractIndexImplementation<? extends IndexDataSource> implementation,
            GraphDatabaseService graphDb, IndexIdentifier identifier )
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected RelationshipIndex instantiateRelationshipIndex(
            AbstractIndexImplementation<? extends IndexDataSource> implementation, GraphDatabaseService graphDb,
            IndexIdentifier identifier )
    {
        // TODO Auto-generated method stub
        return null;
    }
}
