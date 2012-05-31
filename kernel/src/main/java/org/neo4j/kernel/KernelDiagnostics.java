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
package org.neo4j.kernel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.helpers.Format;
import org.neo4j.helpers.Service;
import org.neo4j.kernel.impl.nioneo.store.StoreId;
import org.neo4j.kernel.impl.nioneo.xa.NeoStoreXaDataSource;
import org.neo4j.kernel.logging.StringLogger;
import org.neo4j.kernel.info.DiagnosticsManager;
import org.neo4j.kernel.info.DiagnosticsPhase;
import org.neo4j.kernel.info.DiagnosticsProvider;

abstract class KernelDiagnostics implements DiagnosticsProvider
{
    static void register( DiagnosticsManager manager, AbstractGraphDatabase graphdb, NeoStoreXaDataSource ds )
    {
        manager.prependProvider( new Versions( graphdb.getClass(), ds ) );
        ds.registerDiagnosticsWith( manager );
        manager.appendProvider( new StoreFiles( graphdb.getStoreDir() ) );
    }

    private static class Versions extends KernelDiagnostics
    {
        private final Class<? extends GraphDatabaseService> graphDb;
        private final StoreId storeId;

        public Versions( Class<? extends GraphDatabaseService> graphDb, NeoStoreXaDataSource ds )
        {
            this.graphDb = graphDb;
            this.storeId = ds.getStoreId();
        }

        @Override
        void dump( StringLogger logger )
        {
            logger.info( "Graph Database: " + graphDb.getName() + " " + storeId );
            logger.info( "Kernel version: " + Version.getKernel() );
            logger.info( "Neo4j component versions:" );
            for ( Version componentVersion : Service.load( Version.class ) )
            {
                logger.info( "  " + componentVersion );
            }
        }
    }

    private static class StoreFiles extends KernelDiagnostics
    {
        private final File storeDir;

        private StoreFiles( String storeDir )
        {
            this.storeDir = new File(storeDir);
        }

        @Override
        void dump( StringLogger logger )
        {
            List<String> lines = new ArrayList<String>(  );
            logStoreFiles( lines, "", storeDir );

            logger.debug( Format.logLongMessage("Storage files:", lines ));
        }

        private static long logStoreFiles( List<String> lines, String prefix, File dir )
        {
            if ( !dir.isDirectory() ) return 0;
            File[] files = dir.listFiles();
            if ( files == null )
            {
                lines.add( prefix + "<INACCESSIBLE>" );
                return 0;
            }
            long total = 0;
            for ( File file : files )
            {
                long size;
                String filename = file.getName();
                if ( file.isDirectory() )
                {
                    lines.add( prefix + filename + ":" );
                    size = logStoreFiles( lines, prefix + "  ", file );
                    filename = "- Total";
                }
                else
                {
                    size = file.length();
                }
                lines.add( prefix + filename + ": " + Format.bytes( size ) );
                total += size;
            }
            return total;
        }
    }

    @Override
    public String getDiagnosticsIdentifier()
    {
        return getClass().getDeclaringClass().getSimpleName() + ":" + getClass().getSimpleName();
    }

    @Override
    public void acceptDiagnosticsVisitor( Object visitor )
    {
        // nothing visits ConfigurationLogging
    }

    @Override
    public void dump( DiagnosticsPhase phase, StringLogger log )
    {
        if ( phase.isInitialization() || phase.isExplicitlyRequested() )
        {
            dump( log );
        }
    }

    abstract void dump( StringLogger logger );
}
