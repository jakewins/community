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

import static java.lang.Long.parseLong;
import static org.neo4j.index.base.EntityId.entityId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.index.base.AbstractIndex;
import org.neo4j.index.base.AbstractIndexImplementation;
import org.neo4j.index.base.CombinedIndexHits;
import org.neo4j.index.base.EntityId;
import org.neo4j.index.base.IdToEntityIterator;
import org.neo4j.index.base.IndexBaseXaConnection;
import org.neo4j.index.base.IndexIdentifier;
import org.neo4j.index.lucene.QueryContext;

public abstract class LuceneIndex<T extends PropertyContainer> extends AbstractIndex<T>
{
    static final String KEY_DOC_ID = "_id_";
    static final String KEY_START_NODE_ID = "_start_node_id_";
    static final String KEY_END_NODE_ID = "_end_node_id_";
    private static Set<String> FORBIDDEN_KEYS = new HashSet<String>( Arrays.asList( null, KEY_DOC_ID, KEY_START_NODE_ID, KEY_END_NODE_ID ) );

    final IndexType type;

    // Will contain ids which were found to be missing from the graph when doing queries
    // Write transactions can fetch from this list and add to their transactions to
    // allow for self-healing properties.
    final Collection<Long> abandonedIds = new CopyOnWriteArraySet<Long>();

    LuceneIndex( AbstractIndexImplementation<LuceneDataSource> service, IndexIdentifier identifier )
    {
        super( service, identifier );
        this.type = service.dataSource().getType( identifier );
    }

    private void assertValidKey( String key )
    {
        if ( FORBIDDEN_KEYS.contains( key ) )
        {
            throw new IllegalArgumentException( "Key " + key + " forbidden" );
        }
    }
    
    @Override
    public void add(T entity, String key, Object value)
    {
        assertValidKey( key );
        super.add( entity, key, value );
    }

    public void remove( T entity, String key )
    {
        IndexBaseXaConnection<LuceneTransaction> connection = getConnection();
        assertValidKey( key );
        connection.remove( this, entity, key, null );
    }

    public void remove( T entity )
    {
        IndexBaseXaConnection<LuceneTransaction> connection = getConnection();
        connection.remove( this, entity, null, null );
    }

    public void delete()
    {
        getConnection().deleteIndex( this );
    }

    public IndexHits<T> get( String key, Object value )
    {
        return query( type.get( key, value ), key, value, null );
    }

    /**
     * {@inheritDoc}
     *
     * {@code queryOrQueryObject} can be a {@link String} containing the query
     * in Lucene syntax format, http://lucene.apache.org/java/3_0_2/queryparsersyntax.html.
     * Or it can be a {@link Query} object. If can even be a {@link QueryContext}
     * object which can contain a query ({@link String} or {@link Query}) and
     * additional parameters, such as {@link Sort}.
     *
     * Because of performance issues, including uncommitted transaction modifications
     * in the result is disabled by default, but can be enabled using
     * {@link QueryContext#tradeCorrectnessForSpeed()}.
     */
    public IndexHits<T> query( String key, Object queryOrQueryObject )
    {
        QueryContext context = queryOrQueryObject instanceof QueryContext ?
                (QueryContext) queryOrQueryObject : null;
        return query( type.query( key, context != null ?
                context.getQueryOrQueryObject() : queryOrQueryObject, context ), null, null, context );
    }

    /**
     * {@inheritDoc}
     *
     * @see #query(String, Object)
     */
    public IndexHits<T> query( Object queryOrQueryObject )
    {
        return query( null, queryOrQueryObject );
    }
    
    protected IndexHits<T> query( Query query, String keyForDirectLookup,
            Object valueForDirectLookup, QueryContext additionalParametersOrNull )
    {
        List<EntityId> ids = new ArrayList<EntityId>();
        IndexBaseXaConnection<LuceneTransaction> con = getReadOnlyConnection();
        LuceneTransaction tx = con != null ? con.getTx() : null;
        Collection<EntityId> removedIds = Collections.emptySet();
        IndexSearcher additionsSearcher = null;
        if ( tx != null )
        {
            if ( keyForDirectLookup != null )
            {
                ids.addAll( tx.getAddedIds( this, keyForDirectLookup, valueForDirectLookup ) );
            }
            else
            {
                additionsSearcher = tx.getAdditionsAsSearcher( this, additionalParametersOrNull );
            }
            removedIds = keyForDirectLookup != null ?
                    tx.getRemovedIds( this, keyForDirectLookup, valueForDirectLookup ) :
                    tx.getRemovedIds( this, query );
        }
        LuceneDataSource dataSource = (LuceneDataSource) getProvider().dataSource();
        dataSource.getReadLock();
        IndexHits<EntityId> idIterator = null;
        IndexSearcherRef searcher = null;
        try
        {
            searcher = dataSource.getIndexSearcher( getIdentifier(), true );
            if ( searcher != null )
            {
                boolean foundInCache = false;
//                LruCache<String, Collection<Long>> cachedIdsMap = null;
//                if ( keyForDirectLookup != null )
//                {
//                    cachedIdsMap = dataSource.getFromCache( getIdentifier(), keyForDirectLookup );
//                    foundInCache = fillFromCache( cachedIdsMap, ids,
//                            keyForDirectLookup, valueForDirectLookup.toString(), removedIds );
//                }

                if ( !foundInCache )
                {
                    DocToIdIterator searchedIds = new DocToIdIterator( search( searcher,
                            query, additionalParametersOrNull, additionsSearcher, removedIds ), removedIds, searcher );
                    if ( ids.isEmpty() )
                    {
                        idIterator = searchedIds;
                    }
                    else
                    {
                        Collection<IndexHits<EntityId>> iterators = new ArrayList<IndexHits<EntityId>>();
                        iterators.add( searchedIds );
                        iterators.add( new ConstantScoreIterator<EntityId>( ids, Float.NaN ) );
                        idIterator = new CombinedIndexHits<EntityId>( iterators );
                    }
                }
            }
        }
        finally
        {
            // The DocToIdIterator closes the IndexSearchRef instance anyways,
            // or the LazyIterator if it's a lazy one. So no need here.
            dataSource.releaseReadLock();
        }

        idIterator = idIterator == null ? new ConstantScoreIterator<EntityId>( ids, 0 ) : idIterator;
        return newEntityIterator( idIterator );
    }

    @Override
    public boolean isWriteable()
    {
        return true;
    }

    private IndexHits<T> newEntityIterator( IndexHits<EntityId> idIterator )
    {
        return new IdToEntityIterator<T>( idIterator )
        {
            @Override
            protected T underlyingObjectToObject( EntityId id )
            {
                return idToEntity( id );
            }

            @Override
            protected void itemDodged( EntityId item )
            {
                abandonedIds.add( item.getId() );
            }
        };
    }

//    private boolean fillFromCache(
//            LruCache<String, Collection<Long>> cachedNodesMap,
//            List<Long> ids, String key, String valueAsString,
//            Collection<Long> deletedNodes )
//    {
//        boolean found = false;
//        if ( cachedNodesMap != null )
//        {
//            Collection<Long> cachedNodes = cachedNodesMap.get( valueAsString );
//            if ( cachedNodes != null )
//            {
//                found = true;
//                for ( Long cachedNodeId : cachedNodes )
//                {
//                    if ( !deletedNodes.contains( cachedNodeId ) )
//                    {
//                        ids.add( cachedNodeId );
//                    }
//                }
//            }
//        }
//        return found;
//    }

    private IndexHits<Document> search( IndexSearcherRef searcherRef, Query query,
            QueryContext additionalParametersOrNull, IndexSearcher additionsSearcher, Collection<EntityId> removed )
    {
        try
        {
            if ( additionsSearcher != null && !removed.isEmpty() )
            {
                letThroughAdditions( additionsSearcher, query, removed );
            }

            IndexSearcher searcher = additionsSearcher == null ? searcherRef.getSearcher() :
                    new IndexSearcher( new MultiReader( searcherRef.getSearcher().getIndexReader(),
                            additionsSearcher.getIndexReader() ) );
            IndexHits<Document> result = null;
            if ( additionalParametersOrNull != null && additionalParametersOrNull.getTop() > 0 )
            {
                result = new TopDocsIterator( query, additionalParametersOrNull, searcher );
            }
            else
            {
                Sort sorting = additionalParametersOrNull != null ?
                        additionalParametersOrNull.getSorting() : null;
                boolean forceScore = additionalParametersOrNull == null ||
                        !additionalParametersOrNull.getTradeCorrectnessForSpeed();
                Hits hits = new Hits( searcher, query, null, sorting, forceScore );
                result = new HitsIterator( hits );
            }
            return result;
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Unable to query " + this + " with "
                                        + query, e );
        }
    }

    private void letThroughAdditions( IndexSearcher additionsSearcher, Query query, Collection<EntityId> removed )
            throws IOException
    {
        Hits hits = new Hits( additionsSearcher, query, null );
        HitsIterator iterator = new HitsIterator( hits );
        while ( iterator.hasNext() )
        {
            String idString = iterator.next().getField( KEY_DOC_ID ).stringValue();
            removed.remove( entityId( parseLong( idString ) ) );
        }
    }

//    public void setCacheCapacity( String key, int capacity )
//    {
//        service.dataSource().setCacheCapacity( identifier, key, capacity );
//    }
//
//    public Integer getCacheCapacity( String key )
//    {
//        return service.dataSource().getCacheCapacity( identifier, key );
//    }

    protected abstract long getEntityId( T entity );

    static class NodeIndex extends LuceneIndex<Node>
    {
        private GraphDatabaseService gdb;

        NodeIndex( AbstractIndexImplementation<LuceneDataSource> service,
                   GraphDatabaseService gdb, IndexIdentifier identifier )
        {
            super( service, identifier );
            this.gdb = gdb;
        }

        @Override
        protected Node idToEntity( EntityId id )
        {
            return gdb.getNodeById(id.getId());
        }

        @Override
        protected long getEntityId( Node entity )
        {
            return entity.getId();
        }

        public Class<Node> getEntityType()
        {
            return Node.class;
        }
    }

    static class RelationshipIndex extends LuceneIndex<Relationship>
            implements org.neo4j.graphdb.index.RelationshipIndex
    {
        private GraphDatabaseService gdb;

        RelationshipIndex( AbstractIndexImplementation<LuceneDataSource> service,
                           GraphDatabaseService gdb,
                IndexIdentifier identifier )
        {
            super( service, identifier );
            this.gdb = gdb;
        }

        @Override
        protected Relationship idToEntity( EntityId id )
        {
            return gdb.getRelationshipById(id.getId());
        }

        @Override
        protected long getEntityId( Relationship entity )
        {
            return entity.getId();
        }

        public IndexHits<Relationship> get( String key, Object valueOrNull, Node startNodeOrNull,
                Node endNodeOrNull )
        {
            BooleanQuery query = new BooleanQuery();
            if ( key != null && valueOrNull != null )
            {
                query.add( type.get( key, valueOrNull ), Occur.MUST );
            }
            addIfNotNull( query, startNodeOrNull, KEY_START_NODE_ID );
            addIfNotNull( query, endNodeOrNull, KEY_END_NODE_ID );
            return query( query, (String) null, null, null );
        }

        public IndexHits<Relationship> query( String key, Object queryOrQueryObjectOrNull,
                Node startNodeOrNull, Node endNodeOrNull )
        {
            QueryContext context = queryOrQueryObjectOrNull != null &&
                    queryOrQueryObjectOrNull instanceof QueryContext ?
                            (QueryContext) queryOrQueryObjectOrNull : null;

            BooleanQuery query = new BooleanQuery();
            if ( (context != null && context.getQueryOrQueryObject() != null) ||
                    (context == null && queryOrQueryObjectOrNull != null ) )
            {
                query.add( type.query( key, context != null ?
                        context.getQueryOrQueryObject() : queryOrQueryObjectOrNull, context ), Occur.MUST );
            }
            addIfNotNull( query, startNodeOrNull, KEY_START_NODE_ID );
            addIfNotNull( query, endNodeOrNull, KEY_END_NODE_ID );
            return query( query, (String) null, null, context );
        }

        private static void addIfNotNull( BooleanQuery query, Node nodeOrNull, String field )
        {
            if ( nodeOrNull != null )
            {
                query.add( new TermQuery( new Term( field, "" + nodeOrNull.getId() ) ),
                        Occur.MUST );
            }
        }

        public IndexHits<Relationship> query( Object queryOrQueryObjectOrNull,
                Node startNodeOrNull, Node endNodeOrNull )
        {
            return query( null, queryOrQueryObjectOrNull, startNodeOrNull, endNodeOrNull );
        }

        public Class<Relationship> getEntityType()
        {
            return Relationship.class;
        }
    }
}
