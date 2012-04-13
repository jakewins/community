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
package org.neo4j.index.lucene;

import static org.neo4j.helpers.collection.MapUtil.stringMap;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.index.base.AbstractIndexImplementation;
import org.neo4j.index.base.AbstractIndexProvider;
import org.neo4j.index.base.IndexDataSource;
import org.neo4j.index.impl.lucene.LuceneDataSource;
import org.neo4j.index.impl.lucene.LuceneIndexImplementation;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.index.IndexStore;
import org.neo4j.kernel.impl.nioneo.store.FileSystemAbstraction;
import org.neo4j.kernel.impl.transaction.xaframework.XaFactory;

public class LuceneIndexProvider extends AbstractIndexProvider
{
    private static List<WeakReference<LuceneIndexImplementation>> previousProviders = new ArrayList<WeakReference<LuceneIndexImplementation>>();
    
    public static abstract class Configuration
    {
        public static final GraphDatabaseSetting.BooleanSetting read_only = GraphDatabaseSettings.read_only;
    }

    public LuceneIndexProvider( )
    {
        super( LuceneIndexImplementation.SERVICE_NAME );
    }

    @Override
    protected IndexDataSource newDataSource( Map<String, String> params, IndexStore indexStore,
            FileSystemAbstraction fileSystemAbstraction, XaFactory xaFactory )
    {
        params = overrideLogAndProviderStore( params );
        return new LuceneDataSource( new Config( params ), indexStore, fileSystemAbstraction, xaFactory );
    }

    private Map<String, String> overrideLogAndProviderStore( Map<String, String> params )
    {
        String storeDir = params.get( "store_dir" );
        return stringMap( new HashMap<String, String>( params ),
                // Overridden for legacy purposes. If left out then good defaults are used instead
                "index_logical_log", new File( new File( storeDir, "index" ), "lucene.log" ).getAbsolutePath(),
                "index_provider_db", new File( new File( storeDir, "index" ), "lucene-store.db" ).getAbsolutePath(),
                "index_dir_name", "lucene" );
    }
//=======
//        Config config = dependencyResolver.resolveDependency(Config.class);
//        AbstractGraphDatabase gdb = dependencyResolver.resolveDependency(AbstractGraphDatabase.class);
//        TransactionManager txManager = dependencyResolver.resolveDependency(TransactionManager.class);
//        IndexStore indexStore = dependencyResolver.resolveDependency(IndexStore.class);
//        XaFactory xaFactory = dependencyResolver.resolveDependency(XaFactory.class);
//        FileSystemAbstraction fileSystemAbstraction = dependencyResolver.resolveDependency(FileSystemAbstraction.class);
//        XaDataSourceManager xaDataSourceManager = dependencyResolver.resolveDependency( XaDataSourceManager.class );
//
//        LuceneDataSource luceneDataSource = new LuceneDataSource(config, indexStore, fileSystemAbstraction, xaFactory);
//
//        xaDataSourceManager.registerDataSource(luceneDataSource);
//
//        IndexConnectionBroker<LuceneXaConnection> broker = config.getBoolean( Configuration.read_only ) ? new ReadOnlyIndexConnectionBroker<LuceneXaConnection>( txManager )
//                : new ConnectionBroker( txManager, luceneDataSource );
//>>>>>>> master

    @Override
    protected AbstractIndexImplementation newIndexImplementation( GraphDatabaseAPI db,
            IndexDataSource dataSource, Map<String, String> params )
    {
        return new LuceneIndexImplementation( db, new Config( params ), (LuceneDataSource)dataSource );
    }

}
