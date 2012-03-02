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
package org.neo4j.index.impl.lucene;

import static org.neo4j.index.impl.lucene.MultipleBackupDeletionPolicy.SNAPSHOT_ID;
import static org.neo4j.kernel.impl.nioneo.store.NeoStore.versionStringToLong;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.helpers.Pair;
import org.neo4j.helpers.UTF8;
import org.neo4j.helpers.collection.ClosableIterable;
import org.neo4j.index.base.AbstractIndexImplementation;
import org.neo4j.index.base.EntityType;
import org.neo4j.index.base.IndexDataSource;
import org.neo4j.index.base.IndexIdentifier;
import org.neo4j.kernel.impl.index.IndexStore;
import org.neo4j.kernel.impl.nioneo.store.FileSystemAbstraction;
import org.neo4j.kernel.impl.transaction.xaframework.XaDataSource;
import org.neo4j.kernel.impl.transaction.xaframework.XaFactory;
import org.neo4j.kernel.impl.transaction.xaframework.XaLogicalLog;
import org.neo4j.kernel.impl.transaction.xaframework.XaTransaction;

/**
 * An {@link XaDataSource} optimized for the {@link LuceneIndexImplementation}.
 * This class is public because the XA framework requires it.
 */
public class LuceneDataSource extends IndexDataSource
{
    public interface Configuration extends IndexDataSource.Configuration
    {
        int lucene_searcher_cache_size(int def);

        int lucene_writer_cache_size(int def);

        boolean ephemeral(boolean def);

        boolean read_only(boolean def);

        String store_dir();

        boolean allow_store_upgrade( boolean def );
    }
    
    public static final Version LUCENE_VERSION = Version.LUCENE_35;
    public static final String DATA_SOURCE_NAME = "lucene";
    public static final byte[] BRANCH_ID = UTF8.encode( "162374" );
    public static final long INDEX_VERSION = versionStringToLong( "3.5" );

    /**
     * Default {@link Analyzer} for fulltext parsing.
     */
    public static final Analyzer LOWER_CASE_WHITESPACE_ANALYZER =
        new Analyzer()
    {
        @Override
        public TokenStream tokenStream( String fieldName, Reader reader )
        {
            return new LowerCaseFilter( LUCENE_VERSION, new WhitespaceTokenizer( LUCENE_VERSION, reader ) );
        }

        @Override
        public String toString()
        {
            return "LOWER_CASE_WHITESPACE_ANALYZER";
        }
    };

    public static final Analyzer WHITESPACE_ANALYZER = new Analyzer()
    {
        @Override
        public TokenStream tokenStream( String fieldName, Reader reader )
        {
            return new WhitespaceTokenizer( LUCENE_VERSION, reader );
        }

        @Override
        public String toString()
        {
            return "WHITESPACE_ANALYZER";
        }
    };

    public static final Analyzer KEYWORD_ANALYZER = new KeywordAnalyzer();

    private IndexWriterLruCache indexWriters;
    private IndexSearcherLruCache indexSearchers;

    private IndexTypeCache typeCache;
//    private final Cache caching;
    Map<IndexIdentifier, LuceneIndex<? extends PropertyContainer>> indexes;
    private DirectoryGetter directoryGetter;

    /**
     * Constructs this data source.
     *
     * @throws InstantiationException if the data source couldn't be
     * instantiated
     */
    public LuceneDataSource( Configuration config, IndexStore indexStore, FileSystemAbstraction fileSystemAbstraction,
            XaFactory xaFactory )
    {
        super( BRANCH_ID, DATA_SOURCE_NAME, config, indexStore, fileSystemAbstraction, xaFactory );
    }
    
    @Override
    protected void initializeBeforeLogicalLog( org.neo4j.index.base.IndexDataSource.Configuration configuration )
    {
        // TODO Hmm, casting Configuration here... not very nice perhaps
        Configuration config = (Configuration) configuration;
        indexSearchers = new IndexSearcherLruCache( config.lucene_searcher_cache_size(Integer.MAX_VALUE) );
        indexWriters = new IndexWriterLruCache( config.lucene_writer_cache_size(Integer.MAX_VALUE) );
        indexes = new HashMap<IndexIdentifier, LuceneIndex<? extends PropertyContainer>>();
//        caching = new Cache();
        cleanWriteLocks( getStoreDir() );
        this.typeCache = new IndexTypeCache( getIndexStore() );
        this.directoryGetter = config.ephemeral(false) ? DirectoryGetter.MEMORY : DirectoryGetter.FS;
    }
    
    protected long getVersion()
    {
        return INDEX_VERSION;
    }

    IndexType getType( IndexIdentifier identifier )
    {
        return typeCache.getIndexType( identifier );
    }

    Map<String, String> getConfig( IndexIdentifier identifier )
    {
        return getIndexStore().get( identifier.getEntityType().getType(), identifier.getIndexName() );
    }

    private void cleanWriteLocks( String directory )
    {
        File dir = new File( directory );
        if ( !dir.isDirectory() )
        {
            return;
        }
        for ( File file : dir.listFiles() )
        {
            if ( file.isDirectory() )
            {
                cleanWriteLocks( file.getAbsolutePath() );
            }
            else if ( file.getName().equals( "write.lock" ) )
            {
                boolean success = file.delete();
                assert success;
            }
        }
    }

    @Override
    protected void actualClose()
    {
        for ( Pair<IndexSearcherRef, AtomicBoolean> searcher : indexSearchers.values() )
        {
            try
            {
                searcher.first().dispose();
            }
            catch ( IOException e )
            {
                e.printStackTrace();
            }
        }
        indexSearchers.clear();

        for ( Map.Entry<IndexIdentifier, IndexWriter> entry : indexWriters.entrySet() )
        {
            try
            {
                entry.getValue().close( true );
            }
            catch ( IOException e )
            {
                throw new RuntimeException( "Unable to close index writer " + entry.getKey(), e );
            }
        }
        indexWriters.clear();
    }

    public Index<Node> nodeIndex( String indexName, GraphDatabaseService graphDb, AbstractIndexImplementation<LuceneDataSource> luceneIndexImplementation )
    {
        IndexIdentifier identifier = new IndexIdentifier( EntityType.NODE, indexName );
        synchronized ( indexes )
        {
            LuceneIndex index = indexes.get( identifier );
            if ( index == null )
            {
                index = new LuceneIndex.NodeIndex( luceneIndexImplementation, graphDb, identifier );
                indexes.put( identifier, index );
            }
            return index;
        }
    }

    public RelationshipIndex relationshipIndex( String indexName, GraphDatabaseService gdb, AbstractIndexImplementation<LuceneDataSource> luceneIndexImplementation )
    {
        IndexIdentifier identifier = new IndexIdentifier( EntityType.RELATIONSHIP, indexName );
        synchronized ( indexes )
        {
            LuceneIndex index = indexes.get( identifier );
            if ( index == null )
            {
                index = new LuceneIndex.RelationshipIndex( luceneIndexImplementation, gdb, identifier );
                indexes.put( identifier, index );
            }
            return (RelationshipIndex) index;
        }
    }

    @Override
    protected void flushAll()
    {
        for ( Map.Entry<IndexIdentifier, IndexWriter> entry : getAllIndexWriters() )
        {
            try
            {
                entry.getValue().commit();
            }
            catch ( IOException e )
            {
                throw new RuntimeException( "unable to commit changes to " + entry.getKey(), e );
            }
        }
    }

    @SuppressWarnings( "rawtypes" )
    private synchronized Map.Entry[] getAllIndexWriters()
    {
        return indexWriters.entrySet().toArray( new Map.Entry[indexWriters.size()] );
    }

    /**
     * If nothing has changed underneath (since the searcher was last created
     * or refreshed) {@code searcher} is returned. But if something has changed a
     * refreshed searcher is returned. It makes use if the
     * {@link IndexReader#openIfChanged(IndexReader, IndexWriter, boolean)} which faster than opening an index from
     * scratch.
     *
     * @param searcher the {@link IndexSearcher} to refresh.
     * @param writer 
     * @return a refreshed version of the searcher or, if nothing has changed,
     * {@code null}.
     * @throws IOException if there's a problem with the index.
     */
    private Pair<IndexSearcherRef, AtomicBoolean> refreshSearcher( Pair<IndexSearcherRef, AtomicBoolean> searcher, IndexWriter writer )
    {
        try
        {
            IndexReader reader = searcher.first().getSearcher().getIndexReader();
            IndexReader reopened = IndexReader.openIfChanged( reader, writer, true );
            if ( reopened != null )
            {
                IndexSearcher newSearcher = new IndexSearcher( reopened );
                searcher.first().detachOrClose();
                return Pair.of( new IndexSearcherRef( searcher.first().getIdentifier(), newSearcher ), new AtomicBoolean() );
            }
            return searcher;
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    static File getFileDirectory( String storeDir, EntityType entityType )
    {
        return new File( storeDir, entityType.name().toLowerCase() );
    }

    static File getFileDirectory( String storeDir, IndexIdentifier identifier )
    {
        return new File( getFileDirectory( storeDir, identifier.getEntityType() ), identifier.getIndexName() );
    }

    static Directory getDirectory( String storeDir,
            IndexIdentifier identifier ) throws IOException
    {
        return FSDirectory.open( getFileDirectory( storeDir, identifier) );
    }

    static TopFieldCollector scoringCollector( Sort sorting, int n ) throws IOException
    {
        return TopFieldCollector.create( sorting, n, false, true, false, true );
    }

    synchronized IndexSearcherRef getIndexSearcher( IndexIdentifier identifier, boolean incRef )
    {
        try
        {
            Pair<IndexSearcherRef, AtomicBoolean> searcher = indexSearchers.get( identifier );
            IndexWriter writer = getIndexWriter( identifier );
            if ( searcher == null )
            {
                IndexReader reader = IndexReader.open( writer, true );
                IndexSearcher indexSearcher = new IndexSearcher( reader );
                searcher = Pair.of( new IndexSearcherRef( identifier, indexSearcher ), new AtomicBoolean() );
                indexSearchers.put( identifier, searcher );
            }
            else
            {
                if ( searcher.other().compareAndSet( true, false ) )
                {
                    searcher = refreshSearcher( searcher, writer );
                    if ( searcher != null )
                    {
                        indexSearchers.put( identifier, searcher );
                    }
                }
            }
            if ( incRef )
            {
                searcher.first().incRef();
            }
            return searcher.first();
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    @Override
    protected XaTransaction createTransaction( int identifier,
        XaLogicalLog logicalLog )
    {
        return new LuceneTransaction( identifier, logicalLog, this );
    }

    synchronized void invalidateIndexSearcher( IndexIdentifier identifier )
    {
        Pair<IndexSearcherRef, AtomicBoolean> searcher = indexSearchers.get( identifier );
        if ( searcher != null )
        {
            searcher.other().set( true );
        }
    }

    void deleteIndex( IndexIdentifier identifier, boolean recovery )
    {
        closeWriter( identifier );
        deleteFileOrDirectory( getFileDirectory( getStoreDir(), identifier ) );
//        invalidateCache( identifier );
        boolean removeFromIndexStore = !recovery || (recovery &&
                getIndexStore().has( identifier.getEntityType().getType(), identifier.getIndexName() ));
        if ( removeFromIndexStore )
        {
            getIndexStore().remove( identifier.getEntityType().getType(), identifier.getIndexName() );
        }
        typeCache.invalidate( identifier );
        synchronized ( indexes )
        {
            LuceneIndex<? extends PropertyContainer> index = indexes.remove( identifier );
            if ( index != null )
            {
                index.markAsDeleted();
            }
        }
    }

    private static void deleteFileOrDirectory( File file )
    {
        if ( file.exists() )
        {
            if ( file.isDirectory() )
            {
                for ( File child : file.listFiles() )
                {
                    deleteFileOrDirectory( child );
                }
            }
            file.delete();
        }
    }

    synchronized IndexWriter getIndexWriter( IndexIdentifier identifier )
    {
        if ( isClosed() ) throw new IllegalStateException( "Index has been shut down" );

        IndexWriter writer = indexWriters.get( identifier );
        if ( writer != null )
        {
            return writer;
        }

        try
        {
            Directory dir = directoryGetter.getDirectory( getStoreDir(), identifier ); //getDirectory( baseStorePath, identifier );
            directoryExists( dir );
            IndexType type = getType( identifier );
            IndexWriterConfig writerConfig = new IndexWriterConfig( LUCENE_VERSION, type.analyzer );
            writerConfig.setIndexDeletionPolicy( new MultipleBackupDeletionPolicy() );
            Similarity similarity = type.getSimilarity();
            if ( similarity != null )
            {
                writerConfig.setSimilarity( similarity );
            }
            IndexWriter indexWriter = new IndexWriter( dir, writerConfig );

            // TODO We should tamper with this value and see how it affects the
            // general performance. Lucene docs says rather <10 for mixed
            // reads/writes
//            writer.setMergeFactor( 8 );

            indexWriters.put( identifier, indexWriter );
            return indexWriter;
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    private boolean directoryExists( Directory dir )
    {
        try
        {
            String[] files = dir.listAll();
            return files != null && files.length > 0;
        }
        catch ( IOException e )
        {
            return false;
        }
    }

    static Document findDocument( IndexType type, IndexSearcher searcher, long entityId )
    {
        try
        {
            TopDocs docs = searcher.search( type.idTermQuery( entityId ), 1 );
            if ( docs.scoreDocs.length > 0 )
            {
                return searcher.doc( docs.scoreDocs[0].doc );
            }
            return null;
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    static boolean documentIsEmpty( Document document )
    {
        List<Fieldable> fields = document.getFields();
        for ( Fieldable field : fields )
        {
            if ( !LuceneIndex.KEY_DOC_ID.equals( field.name() ) )
            {
                return false;
            }
        }
        return true;
    }

    static void remove( IndexWriter writer, Query query )
    {
        try
        {
            // TODO
            writer.deleteDocuments( query );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Unable to delete for " + query + " using" + writer, e );
        }
    }

    private synchronized void closeWriter( IndexIdentifier identifier )
    {
        try
        {
            Pair<IndexSearcherRef, AtomicBoolean> searcher = indexSearchers.remove( identifier );
            IndexWriter writer = indexWriters.remove( identifier );
            if ( searcher != null )
            {
                searcher.first().dispose();
            }
            if ( writer != null )
            {
                writer.close();
            }
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Unable to close lucene writer " + identifier, e );
        }
    }

//    LruCache<String,Collection<Long>> getFromCache( IndexIdentifier identifier, String key )
//    {
//        return caching.get( identifier, key );
//    }
//
//    void setCacheCapacity( IndexIdentifier identifier, String key, int maxNumberOfCachedEntries )
//    {
//        this.caching.setCapacity( identifier, key, maxNumberOfCachedEntries );
//    }
//
//    Integer getCacheCapacity( IndexIdentifier identifier, String key )
//    {
//        LruCache<String,Collection<Long>> cache = this.caching.get( identifier, key );
//        return cache != null ? cache.maxSize() : null;
//    }
//
//    void invalidateCache( IndexIdentifier identifier, String key, Object value )
//    {
//        LruCache<String, Collection<Long>> cache = caching.get( identifier, key );
//        if ( cache != null )
//        {
//            cache.remove( value.toString() );
//        }
//    }
//
//    void invalidateCache( IndexIdentifier identifier )
//    {
//        this.caching.disable( identifier );
//    }

    @SuppressWarnings( "unchecked" )
    @Override
    public ClosableIterable<File> listStoreFiles( boolean includeLogicalLogs ) throws IOException
    {   // Never include logical logs since they are of little importance
        final Collection<File> files = new ArrayList<File>();
        final Collection<SnapshotDeletionPolicy> snapshots = new ArrayList<SnapshotDeletionPolicy>();
        makeSureAllIndexesAreInstantiated();
        for ( Map.Entry<IndexIdentifier, IndexWriter> writer : getAllIndexWriters() )
        {
            SnapshotDeletionPolicy deletionPolicy = (SnapshotDeletionPolicy)
                    writer.getValue().getConfig().getIndexDeletionPolicy();
            File indexDirectory = getFileDirectory( getStoreDir(), writer.getKey() );
            try
            {
                // Throws IllegalStateException if no commits yet
                IndexCommit commit = deletionPolicy.snapshot( SNAPSHOT_ID );
                for ( String fileName : commit.getFileNames() )
                {
                    files.add( new File( indexDirectory, fileName ) );
                }
                snapshots.add( deletionPolicy );
            }
            catch ( IllegalStateException e )
            {
                // TODO Review this
                /*
                 * This is insane but happens if we try to snapshot an existing index
                 * that has no commits. This is a bad API design - it should return null
                 * or something. This is not exceptional.
                 */
            }
        }
        files.add( getIndexProviderStore().getFile() );
        return new ClosableIterable<File>()
        {
            public Iterator<File> iterator()
            {
                return files.iterator();
            }

            public void close()
            {
                for ( SnapshotDeletionPolicy deletionPolicy : snapshots )
                {
                    try
                    {
                        deletionPolicy.release( SNAPSHOT_ID );
                    }
                    catch ( IOException e )
                    {
                        // TODO What to do?
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    private void makeSureAllIndexesAreInstantiated()
    {
        IndexStore indexStore = getIndexStore();
        for ( String name : indexStore.getNames( Node.class ) )
        {
            Map<String, String> config = indexStore.get( Node.class, name );
            if ( config.get( IndexManager.PROVIDER ).equals( LuceneIndexImplementation.SERVICE_NAME ) )
            {
                getIndexWriter( new IndexIdentifier( org.neo4j.index.base.EntityType.NODE, name ) );
            }
        }
        for ( String name : indexStore.getNames( Relationship.class ) )
        {
            Map<String, String> config = indexStore.get( Relationship.class, name );
            if ( config.get( IndexManager.PROVIDER ).equals( LuceneIndexImplementation.SERVICE_NAME ) )
            {
                getIndexWriter( new IndexIdentifier( org.neo4j.index.base.EntityType.RELATIONSHIP, name ) );
            }
        }
    }
    
    private static enum DirectoryGetter
    {
        FS
        {
            @Override
            Directory getDirectory( String baseStorePath, IndexIdentifier identifier ) throws IOException
            {
                return FSDirectory.open( getFileDirectory( baseStorePath, identifier) );
            }
        },
        MEMORY
        {
            @Override
            Directory getDirectory( String baseStorePath, IndexIdentifier identifier )
            {
                return new RAMDirectory();
            }
        };
        
        abstract Directory getDirectory( String baseStorePath, IndexIdentifier identifier ) throws IOException;
    }
}
