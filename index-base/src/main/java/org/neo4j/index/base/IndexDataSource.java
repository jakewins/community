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

import static org.neo4j.index.base.AbstractIndexImplementation.getIndexStoreDir;
import static org.neo4j.index.base.AbstractIndexImplementation.getProviderStoreDb;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSetting.StringSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexProvider;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.index.IndexStore;
import org.neo4j.kernel.impl.nioneo.store.FileSystemAbstraction;
import org.neo4j.kernel.impl.transaction.xaframework.LogBackedXaDataSource;
import org.neo4j.kernel.impl.transaction.xaframework.XaCommand;
import org.neo4j.kernel.impl.transaction.xaframework.XaCommandFactory;
import org.neo4j.kernel.impl.transaction.xaframework.XaConnection;
import org.neo4j.kernel.impl.transaction.xaframework.XaContainer;
import org.neo4j.kernel.impl.transaction.xaframework.XaDataSource;
import org.neo4j.kernel.impl.transaction.xaframework.XaFactory;
import org.neo4j.kernel.impl.transaction.xaframework.XaLogicalLog;
import org.neo4j.kernel.impl.transaction.xaframework.XaTransaction;
import org.neo4j.kernel.impl.transaction.xaframework.XaTransactionFactory;

/**
 * An base class for a {@link XaDataSource} for an {@link Index},
 * or {@link IndexProvider} rather.
 * This class is public because the XA framework requires it.
 */
public abstract class IndexDataSource extends LogBackedXaDataSource
{
    public static abstract class Configuration
        extends LogBackedXaDataSource.Configuration
    {
        public static final GraphDatabaseSetting.StringSetting index_dir_name = new StringSetting( "index_dir_name", ".*", "" );
        public static final GraphDatabaseSetting.StringSetting index_logical_log = new StringSetting( "index_logical_log", ".*", "" );
        public static final GraphDatabaseSetting.StringSetting index_provider_db = new StringSetting( "index_provider_db", ".*", "" );
        public static final GraphDatabaseSetting.BooleanSetting ephemeral = GraphDatabaseSettings.ephemeral;
    }
    
    private final XaContainer xaContainer;
    private final String storeDir;
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock(); 
    final IndexStore indexStore;
    final IndexProviderStore store;
    private boolean closed;

    /**
     * Constructs this data source.
     * 
     * @param params XA parameters.
     * @throws InstantiationException if the data source couldn't be
     * instantiated
     */
    public IndexDataSource( byte[] branchId, String dataSourceName, Config config, IndexStore indexStore,
            FileSystemAbstraction fileSystem, XaFactory xaFactory ) 
    {
        super( branchId, dataSourceName );
        try
        {
            String indexDirName = config.get( Configuration.index_dir_name );
            String dbStoreDir = config.get( Configuration.store_dir );
            this.storeDir = getIndexStoreDir( dbStoreDir, indexDirName != null ? indexDirName : dataSourceName );
            this.indexStore = indexStore;
            ensureDirectoryCreated( this.storeDir );
            boolean allowUpgrade = config.getBoolean( GraphDatabaseSettings.allow_store_upgrade );
            String providerDb = config.get( Configuration.index_provider_db );
            this.store = new IndexProviderStore( new File( providerDb != null ? providerDb : getProviderStoreDb( dbStoreDir, dataSourceName ) ),
                    fileSystem, getVersion(), allowUpgrade );
            boolean isReadOnly = config.getBoolean( Configuration.read_only );
            initializeBeforeLogicalLog( config );
            
            if ( !isReadOnly )
            {
                XaCommandFactory cf = new IndexCommandFactory();
                XaTransactionFactory tf = new IndexTransactionFactory();
                String logicalLog = config.get( Configuration.index_logical_log );
                xaContainer = xaFactory.newXaContainer( this, logicalLog != null ? logicalLog : storeDir + "/logical.log", cf, tf, null, null );
                try
                {
                    xaContainer.openLogicalLog();
                }
                catch ( IOException e )
                {
                    throw new RuntimeException( "Unable to open logical log in " + this.storeDir, e );
                }
                
                boolean backupEnabled = config.isSet( Configuration.online_backup_enabled ) ? config.getBoolean( Configuration.online_backup_enabled ) : false;
                setKeepLogicalLogsIfSpecified( backupEnabled ? "true" : config.get( Configuration.keep_logical_logs ), dataSourceName );
                setLogicalLogAtCreationTime( xaContainer.getLogicalLog() );
            }
            else
            {
                xaContainer = null;
            }
        }
        catch ( Throwable e )
        {
            e.printStackTrace();
            throw new RuntimeException( e );
        }
    }
    
    protected void initializeBeforeLogicalLog( Config config )
    {
    }
    
    protected abstract long getVersion();

    protected String getStoreDir()
    {
        return storeDir;
    }
    
    public IndexStore getIndexStore()
    {
        return indexStore;
    }
    
    public IndexProviderStore getIndexProviderStore()
    {
        return store;
    }
    
    private void ensureDirectoryCreated( String path )
    {
        File dir = new File( path );
        if ( !dir.exists() )
        {
            if ( !dir.mkdirs() )
            {
                throw new RuntimeException( "Unable to create directory path["
                    + dir.getAbsolutePath() + "] for Neo4j store." );
            }
        }
    }

    protected static String getStoreDir( Map<?, ?> params )
    {
        String dbStoreDir = (String) params.get( "store_dir" );
        String dataSourceName = (String) params.get( "data_source_name" );
        File dir = new File( new File( dbStoreDir, "index" ), dataSourceName );
        if ( !dir.exists() )
        {
            if ( !dir.mkdirs() )
            {
                throw new RuntimeException( "Unable to create directory path["
                    + dir.getAbsolutePath() + "] for Neo4j store." );
            }
        }
        return dir.getAbsolutePath();
    }
    
    @Override
    public final void close()
    {
        if ( closed )
        {
            return;
        }
        synchronized ( this )
        {
            xaContainer.close();
            store.close();
            actualClose();
            closed = true;
        }
    }
    
    public boolean isClosed()
    {
        return closed;
    }

    protected abstract void actualClose();

    @Override
    public XaConnection getXaConnection()
    {
        return new IndexBaseXaConnection( storeDir, xaContainer.getResourceManager(), getBranchId() );
    }
    
    protected XaCommand readCommand( ReadableByteChannel channel, ByteBuffer buffer ) throws IOException
    {
        return IndexCommand.readCommand( channel, buffer );
    }
    
    protected abstract void flushAll();
    
    private class IndexCommandFactory extends XaCommandFactory
    {
        IndexCommandFactory()
        {
            super();
        }

        @Override
        public XaCommand readCommand( ReadableByteChannel channel, 
            ByteBuffer buffer ) throws IOException
        {
            return IndexDataSource.this.readCommand( channel, buffer );
        }
    }
    
    private class IndexTransactionFactory extends XaTransactionFactory
    {
        @Override
        public XaTransaction create( int identifier )
        {
            return createTransaction( identifier, this.getLogicalLog() );
        }

        @Override
        public void flushAll()
        {
            IndexDataSource.this.flushAll();
        }

        @Override
        public long getCurrentVersion()
        {
            return store.getVersion();
        }
        
        @Override
        public long getAndSetNewVersion()
        {
            return store.incrementVersion();
        }

        @Override
        public long getLastCommittedTx()
        {
            return store.getLastCommittedTx();
        }
    }
    
    public void getReadLock()
    {
        lock.readLock().lock();
    }
    
    public void releaseReadLock()
    {
        lock.readLock().unlock();
    }
    
    public void getWriteLock()
    {
        lock.writeLock().lock();
    }
    
    public void releaseWriteLock()
    {
        lock.writeLock().unlock();
    }
    
    protected abstract XaTransaction createTransaction( int identifier,
        XaLogicalLog logicalLog );

    @Override
    public long getCreationTime()
    {
        return store.getCreationTime();
    }
    
    @Override
    public long getRandomIdentifier()
    {
        return store.getRandomNumber();
    }
    
    @Override
    public long getCurrentLogVersion()
    {
        return store.getVersion();
    }
    
    public long getLastCommittedTxId()
    {
        return this.store.getLastCommittedTx();
    }
    
    @Override
    public void setLastCommittedTxId( long txId )
    {
        this.store.setLastCommittedTx( txId );
    }
    
    @Override
    public XaContainer getXaContainer()
    {
        return xaContainer;
    }
}
