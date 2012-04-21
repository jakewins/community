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

package org.neo4j.kernel.impl.nioneo.xa;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.helpers.Exceptions;
import org.neo4j.helpers.Format;
import org.neo4j.helpers.Pair;
import org.neo4j.helpers.UTF8;
import org.neo4j.helpers.collection.ClosableIterable;
import org.neo4j.kernel.AbstractGraphDatabase;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.core.LockReleaser;
import org.neo4j.kernel.impl.core.PropertyIndex;
import org.neo4j.kernel.impl.index.IndexStore;
import org.neo4j.kernel.impl.nioneo.store.FileSystemAbstraction;
import org.neo4j.kernel.impl.nioneo.store.NeoStore;
import org.neo4j.kernel.impl.nioneo.store.PropertyStore;
import org.neo4j.kernel.impl.nioneo.store.Store;
import org.neo4j.kernel.impl.nioneo.store.StoreFactory;
import org.neo4j.kernel.impl.nioneo.store.StoreId;
import org.neo4j.kernel.impl.nioneo.store.WindowPoolStats;
import org.neo4j.kernel.impl.persistence.IdGenerationFailedException;
import org.neo4j.kernel.impl.transaction.LockManager;
import org.neo4j.kernel.impl.transaction.xaframework.LogBackedXaDataSource;
import org.neo4j.kernel.impl.transaction.xaframework.TransactionInterceptor;
import org.neo4j.kernel.impl.transaction.xaframework.TransactionInterceptorProvider;
import org.neo4j.kernel.impl.transaction.xaframework.XaCommand;
import org.neo4j.kernel.impl.transaction.xaframework.XaCommandFactory;
import org.neo4j.kernel.impl.transaction.xaframework.XaConnection;
import org.neo4j.kernel.impl.transaction.xaframework.XaContainer;
import org.neo4j.kernel.impl.transaction.xaframework.XaFactory;
import org.neo4j.kernel.impl.transaction.xaframework.XaResource;
import org.neo4j.kernel.impl.transaction.xaframework.XaTransaction;
import org.neo4j.kernel.impl.transaction.xaframework.XaTransactionFactory;
import org.neo4j.kernel.impl.util.ArrayMap;
import org.neo4j.kernel.impl.util.StringLogger;
import org.neo4j.kernel.info.DiagnosticsExtractor;
import org.neo4j.kernel.info.DiagnosticsManager;
import org.neo4j.kernel.info.DiagnosticsPhase;
import org.neo4j.kernel.lifecycle.Lifecycle;

/**
 * A <CODE>NeoStoreXaDataSource</CODE> is a factory for
 * {@link NeoStoreXaConnection NeoStoreXaConnections}.
 * <p>
 * The {@link NioNeoDbPersistenceSource} will create a <CODE>NeoStoreXaDataSoruce</CODE>
 * and then Neo4j kernel will use it to create {@link XaConnection XaConnections} and
 * {@link XaResource XaResources} when running transactions and performing
 * operations on the node space.
 */
public class NeoStoreXaDataSource extends LogBackedXaDataSource implements Lifecycle
{
    public static abstract class Configuration
        extends LogBackedXaDataSource.Configuration
    {
        public static final GraphDatabaseSetting.BooleanSetting read_only = GraphDatabaseSettings.read_only;
        public static final GraphDatabaseSetting.StringSetting store_dir = AbstractGraphDatabase.Configuration.store_dir;
        public static final GraphDatabaseSetting.StringSetting neo_store = AbstractGraphDatabase.Configuration.neo_store;
        public static final GraphDatabaseSetting.StringSetting logical_log = AbstractGraphDatabase.Configuration.logical_log;
        public static final GraphDatabaseSetting.BooleanSetting intercept_committing_transactions = GraphDatabaseSettings.intercept_committing_transactions;
    }

    public static final byte BRANCH_ID[] = UTF8.encode( "414141" );
    public static final String LOGICAL_LOG_DEFAULT_NAME = "nioneo_logical.log";

    private static Logger logger = Logger.getLogger(
        NeoStoreXaDataSource.class.getName() );

    private final LockManager lockManager;
    private final LockReleaser lockReleaser;
    private final Config config;
    private final StoreFactory storeFactory;
    private final FileSystemAbstraction fileSystemAbstraction;
    private final XaFactory xaFactory;
    private final List<Pair<TransactionInterceptorProvider, Object>> providers;
    private final StringLogger msgLog;
    private final DependencyResolver dependencyResolver;

    private NeoStore neoStore;
    private XaContainer xaContainer;
    private ArrayMap<Class<?>,Store> idGenerators;

    private String storeDir;
    private boolean readOnly;

    private boolean logApplied = false;


    private enum Diagnostics implements DiagnosticsExtractor<NeoStoreXaDataSource>
    {
        NEO_STORE_VERSIONS( "Store versions:" )
        {
            @Override
            void dump( NeoStoreXaDataSource source, List<String> log )
            {
                source.neoStore.logVersions( log );
            }
        },
        NEO_STORE_ID_USAGE( "Id usage:" )
        {
            @Override
            void dump( NeoStoreXaDataSource source, List<String> log )
            {
                source.neoStore.logIdUsage( log );
            }
        };

        private final String message;

        private Diagnostics( String message )
        {
            this.message = message;
        }

        @Override
        public void dumpDiagnostics( final NeoStoreXaDataSource source, DiagnosticsPhase phase, StringLogger log )
        {
            if ( phase.isInitialization() || phase.isExplicitlyRequested() )
            {
                log.logMessage( Format.logLongMessage(message, new Iterable<String>()
                {
                    @Override
                    public Iterator<String> iterator()
                    {
                        List<String> lines = new ArrayList<String>(  );
                        dump( source, lines );
                        return lines.iterator();
                    }
                }));
            }
        }

        abstract void dump( NeoStoreXaDataSource source, List<String> log );
    }

    /**
     * Creates a <CODE>NeoStoreXaDataSource</CODE> using configuration from
     * <CODE>params</CODE>. First the map is checked for the parameter
     * <CODE>config</CODE>.
     * If that parameter exists a config file with that value is loaded (via
     * {@link Properties#load}). Any parameter that exist in the config file
     * and in the map passed into this constructor will take the value from the
     * map.
     * <p>
     */
    public NeoStoreXaDataSource( Config conf,
                                 StoreFactory storeFactory,
                                 FileSystemAbstraction fileSystemAbstraction,
                                 LockManager lockManager,
                                 LockReleaser lockReleaser,
                                 StringLogger stringLogger,
                                 XaFactory xaFactory,
                                 List<Pair<TransactionInterceptorProvider, Object>> providers,
                                 DependencyResolver dependencyResolver
    )
    {
        super( BRANCH_ID, Config.DEFAULT_DATA_SOURCE_NAME );
        config = conf;
        this.storeFactory = storeFactory;
        this.fileSystemAbstraction = fileSystemAbstraction;
        this.xaFactory = xaFactory;
        this.providers = providers;

        this.lockManager = lockManager;
        this.lockReleaser = lockReleaser;
        msgLog = stringLogger;
        this.dependencyResolver = dependencyResolver;
    }

    @Override
    public void init()
        throws Throwable
    {
    }

    @Override
    public void start()
        throws Throwable
    {
        readOnly = config.getBoolean( Configuration.read_only );
        storeDir = config.get( Configuration.store_dir );
        String store = config.get( Configuration.neo_store );
        if ( !readOnly && !fileSystemAbstraction.fileExists( store ))
        {
            msgLog.logMessage( "Creating new db @ " + store, true );
            autoCreatePath( store );
            storeFactory.createNeoStore(store).close();
        }

        final TransactionFactory tf;
        boolean shouldIntercept = config.getBoolean( Configuration.intercept_committing_transactions );
        if ( shouldIntercept && !providers.isEmpty() )
        {
            tf = new InterceptingTransactionFactory( dependencyResolver );
        }
        else
        {
            tf = new TransactionFactory();
        }
        neoStore = storeFactory.newNeoStore(store);

        xaContainer = xaFactory.newXaContainer(this, config.get( Configuration.logical_log ), new CommandFactory( neoStore ), tf,
                shouldIntercept && !providers.isEmpty() ? providers : null, dependencyResolver );

        try
        {
            if ( !readOnly )
            {
                neoStore.setRecoveredStatus( true );
                try
                {
                    xaContainer.openLogicalLog();
                }
                finally
                {
                    neoStore.setRecoveredStatus( false );
                }
            }
            if ( !xaContainer.getResourceManager().hasRecoveredTransactions() )
            {
                neoStore.makeStoreOk();
            }
            else
            {
                logger.fine( "Waiting for TM to take care of recovered " +
                    "transactions." );
            }
            idGenerators = new ArrayMap<Class<?>,Store>( (byte)5, false, false );
            this.idGenerators.put( Node.class, neoStore.getNodeStore() );
            this.idGenerators.put( Relationship.class,
                neoStore.getRelationshipStore() );
            this.idGenerators.put( RelationshipType.class,
                neoStore.getRelationshipTypeStore() );
            this.idGenerators.put( PropertyStore.class,
                neoStore.getPropertyStore() );
            this.idGenerators.put( PropertyIndex.class,
                neoStore.getPropertyStore().getIndexStore() );
            setKeepLogicalLogsIfSpecified( config.getBoolean( new GraphDatabaseSetting.BooleanSetting( "online_backup_enabled")) ? "true" : config.get( Configuration.keep_logical_logs ), Config.DEFAULT_DATA_SOURCE_NAME );
            setLogicalLogAtCreationTime( xaContainer.getLogicalLog() );
        }
        catch ( Throwable e )
        {   // Something unexpected happened during startup
            try
            {   // Close the neostore, so that locks are released properly
                neoStore.close();
            }
            catch ( Exception closeException )
            {
                msgLog.logMessage( "Couldn't close neostore after startup failure" );
            }
            throw Exceptions.launderedException( e );
        }
    }

    @Override
    public void stop()
        throws Throwable
    {
        if ( !readOnly )
        {
            neoStore.flushAll();
        }
        xaContainer.close();
        if ( logApplied )
        {
            neoStore.rebuildIdGenerators();
            logApplied = false;
        }
        neoStore.close();
        logger.fine( "NeoStore closed" );
        msgLog.logMessage( "NeoStore closed", true );
    }

    @Override
    public void shutdown()
        throws Throwable
    {
    }

    private void autoCreatePath( String store ) throws IOException
    {
        String fileSeparator = System.getProperty( "file.separator" );
        int index = store.lastIndexOf( fileSeparator );
        String dirs = store.substring( 0, index );
        File directories = new File( dirs );
        if ( !directories.exists() )
        {
            if ( !directories.mkdirs() )
            {
                throw new IOException( "Unable to create directory path["
                    + dirs + "] for Neo4j store." );
            }
        }
    }

    public NeoStore getNeoStore()
    {
        return neoStore;
    }

    @Override
    public void close()
    {
    }

    public StoreId getStoreId()
    {
        return neoStore.getStoreId();
    }

    @Override
    public NeoStoreXaConnection getXaConnection()
    {
        return new NeoStoreXaConnection( neoStore,
            xaContainer.getResourceManager(), getBranchId() );
    }

    private static class CommandFactory extends XaCommandFactory
    {
        private NeoStore neoStore = null;

        CommandFactory( NeoStore neoStore )
        {
            this.neoStore = neoStore;
        }

        @Override
        public XaCommand readCommand( ReadableByteChannel byteChannel,
            ByteBuffer buffer ) throws IOException
        {
            Command command = Command.readCommand( neoStore, byteChannel,
                buffer );
            if ( command != null )
            {
                command.setRecovered();
            }
            return command;
        }
    }

    private class InterceptingTransactionFactory extends TransactionFactory
    {
        private final DependencyResolver dependencyResolver;

        public InterceptingTransactionFactory( DependencyResolver dependencyResolver )
        {
            this.dependencyResolver = dependencyResolver;
        }

        @Override
        public XaTransaction create( int identifier )
        {

            TransactionInterceptor first = TransactionInterceptorProvider.resolveChain(
                    providers, NeoStoreXaDataSource.this, dependencyResolver );
            return new InterceptingWriteTransaction( identifier,
                    getLogicalLog(), neoStore, lockReleaser, lockManager, first );
        }
    }

    private class TransactionFactory extends XaTransactionFactory
    {
        @Override
        public XaTransaction create( int identifier )
        {
            return new WriteTransaction( identifier, getLogicalLog(), neoStore,
                lockReleaser, lockManager );
        }

        @Override
        public void recoveryComplete()
        {
            logger.fine( "Recovery complete, "
                + "all transactions have been resolved" );
            logger.fine( "Rebuilding id generators as needed. "
                + "This can take a while for large stores..." );
            neoStore.flushAll();
            neoStore.makeStoreOk();
            neoStore.setVersion( xaContainer.getLogicalLog().getHighestLogVersion() );
            logger.fine( "Rebuild of id generators complete." );
        }

        @Override
        public long getCurrentVersion()
        {
            if ( getLogicalLog().scanIsComplete() )
            {
                return neoStore.getVersion();
            }
//            neoStore.setRecoveredStatus( true );
//            try
//            {
                return neoStore.getVersion();
//            }
//            finally
//            {
//                neoStore.setRecoveredStatus( false );
//            }
        }

        @Override
        public long getAndSetNewVersion()
        {
            return neoStore.incrementVersion();
        }

        @Override
        public void flushAll()
        {
            neoStore.flushAll();
        }

        @Override
        public long getLastCommittedTx()
        {
            return neoStore.getLastCommittedTx();
        }
    }

    public long nextId( Class<?> clazz )
    {
        Store store = idGenerators.get( clazz );

        if ( store == null )
        {
            throw new IdGenerationFailedException( "No IdGenerator for: "
                + clazz );
        }
        return store.nextId();
    }

    public long getHighestPossibleIdInUse( Class<?> clazz )
    {
        Store store = idGenerators.get( clazz );
        if ( store == null )
        {
            throw new IdGenerationFailedException( "No IdGenerator for: "
                + clazz );
        }
        return store.getHighestPossibleIdInUse();
    }

    public long getNumberOfIdsInUse( Class<?> clazz )
    {
        Store store = idGenerators.get( clazz );
        if ( store == null )
        {
            throw new IdGenerationFailedException( "No IdGenerator for: "
                + clazz );
        }
        return store.getNumberOfIdsInUse();
    }

    public String getStoreDir()
    {
        return storeDir;
    }

    @Override
    public long getCreationTime()
    {
        return neoStore.getCreationTime();
    }

    @Override
    public long getRandomIdentifier()
    {
        return neoStore.getRandomNumber();
    }

    @Override
    public long getCurrentLogVersion()
    {
        return neoStore.getVersion();
    }

    public long incrementAndGetLogVersion()
    {
        return neoStore.incrementVersion();
    }

    public void setCurrentLogVersion( long version )
    {
        neoStore.setVersion(version);
    }

    // used for testing, do not use.
    @Override
    public void setLastCommittedTxId( long txId )
    {
        neoStore.setRecoveredStatus( true );
        try
        {
            neoStore.setLastCommittedTx( txId );
        }
        finally
        {
            neoStore.setRecoveredStatus( false );
        }
    }

    ReadTransaction getReadOnlyTransaction()
    {
        return new ReadTransaction( neoStore );
    }

    public boolean isReadOnly()
    {
        return readOnly;
    }

    public List<WindowPoolStats> getWindowPoolStats()
    {
        return neoStore.getAllWindowPoolStats();
    }

    @Override
    public long getLastCommittedTxId()
    {
        return neoStore.getLastCommittedTx();
    }

    @Override
    public XaContainer getXaContainer()
    {
        return xaContainer;
    }

    @Override
    public boolean setRecovered( boolean recovered )
    {
        boolean currentValue = neoStore.isInRecoveryMode();
        neoStore.setRecoveredStatus( true );
        return currentValue;
    }

    @Override
    public ClosableIterable<File> listStoreFiles( boolean includeLogicalLogs )
    {
        final Collection<File> files = new ArrayList<File>();
        File neostoreFile = null;
        Pattern logFilePattern = getXaContainer().getLogicalLog().getHistoryFileNamePattern();
        for ( File dbFile : new File( storeDir ).listFiles() )
        {
            String name = dbFile.getName();
            // To filter for "neostore" is quite future proof, but the "index.db" file
            // maybe should be
            if ( dbFile.isFile() )
            {
                if ( name.equals( NeoStore.DEFAULT_NAME ) )
                {
                    neostoreFile = dbFile;
                }
                else if ( (name.startsWith( NeoStore.DEFAULT_NAME ) ||
                        name.equals( IndexStore.INDEX_DB_FILE_NAME )) && !name.endsWith( ".id" ) )
                {   // Store files
                    files.add( dbFile );
                }
                else if ( includeLogicalLogs && logFilePattern.matcher( dbFile.getName() ).matches() )
                {   // Logs
                    files.add( dbFile );
                }
            }
        }
        files.add( neostoreFile );

        return new ClosableIterable<File>()
        {

            public Iterator<File> iterator()
            {
                return files.iterator();
            }

            public void close()
            {
            }
        };
    }

    public void logStoreVersions()
    {
 // TODO This needs to be reconciled with new Diagnostics       neoStore.logVersions();
    }

    public void logIdUsage()
    {
 // TODO This needs to be reconciled with new Diagnostics       neoStore.logIdUsage();
    }

    public void registerDiagnosticsWith( DiagnosticsManager manager )
     {
         manager.registerAll( Diagnostics.class, this );
     }
}
