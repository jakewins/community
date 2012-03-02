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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.transaction.TransactionManager;

import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.graphdb.index.IndexImplementation;
import org.neo4j.graphdb.index.IndexProvider;
import org.neo4j.kernel.AbstractGraphDatabase;
import org.neo4j.kernel.GraphDatabaseSPI;
import org.neo4j.kernel.impl.index.IndexStore;
import org.neo4j.kernel.impl.nioneo.store.FileSystemAbstraction;
import org.neo4j.kernel.impl.transaction.XaDataSourceManager;
import org.neo4j.kernel.impl.transaction.xaframework.XaFactory;

public abstract class AbstractIndexProvider extends IndexProvider
{
    private static List<WeakReference<AbstractIndexImplementation>> previousProviders = new ArrayList<WeakReference<AbstractIndexImplementation>>();
    
    public AbstractIndexProvider( String identifier )
    {
        super( identifier );
    }

    @Override
    public IndexImplementation load( DependencyResolver dependencyResolver ) throws Exception
    {
        Map<String, String> params = (Map<String, String>) dependencyResolver.resolveDependency(Map.class);
        GraphDatabaseSPI gdb = dependencyResolver.resolveDependency(AbstractGraphDatabase.class);
        TransactionManager txManager = dependencyResolver.resolveDependency(TransactionManager.class);
        IndexStore indexStore = dependencyResolver.resolveDependency(IndexStore.class);
        XaFactory xaFactory = dependencyResolver.resolveDependency(XaFactory.class);
        FileSystemAbstraction fileSystemAbstraction = dependencyResolver.resolveDependency(FileSystemAbstraction.class);
        XaDataSourceManager xaDataSourceManager = dependencyResolver.resolveDependency(XaDataSourceManager.class);
//        Configuration conf = ConfigProxy.config(params, Configuration.class);

        IndexDataSource dataSource = newDataSource( params, indexStore, fileSystemAbstraction, xaFactory );
        xaDataSourceManager.registerDataSource( dataSource );

        // TODO This is a hack to support reload of HA instances. Remove if HA supports start/stop of single instance instead
        for( Iterator<WeakReference<AbstractIndexImplementation>> iterator = previousProviders.iterator(); iterator.hasNext(); )
        {
            WeakReference<AbstractIndexImplementation> previousProvider = iterator.next();
            AbstractIndexImplementation indexImplementation = previousProvider.get();
            if (indexImplementation == null)
                iterator.remove();
            else if ( indexImplementation.matches( gdb ) )
                indexImplementation.reset( dataSource );
        }
        
        AbstractIndexImplementation indexImplementation = newIndexImplementation( gdb, dataSource, params );
        previousProviders.add( new WeakReference<AbstractIndexImplementation>( indexImplementation ) );
        return indexImplementation;
    }

    protected abstract IndexDataSource newDataSource( Map<String, String> params, IndexStore indexStore,
            FileSystemAbstraction fileSystemAbstraction, XaFactory xaFactory );
    
    protected abstract AbstractIndexImplementation newIndexImplementation( GraphDatabaseSPI db,
            IndexDataSource dataSource, Map<String, String> params );
}
