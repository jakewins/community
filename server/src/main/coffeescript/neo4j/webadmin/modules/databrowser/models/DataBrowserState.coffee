###
Copyright (c) 2002-2012 "Neo Technology,"
Network Engine for Objects in Lund AB [http://neotechnology.com]

This file is part of Neo4j.

Neo4j is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
###

define(
  ['neo4j/webadmin/modules/databrowser/search/QueuedSearch',
   './NodeProxy'
   './NodeList'
   './RelationshipProxy'
   './RelationshipList'
   'ribcage/Model'], 
  (QueuedSearch, NodeProxy, NodeList, RelationshipProxy, RelationshipList, Model) ->
  
    class DataBrowserState extends Model

      @State :
        ERROR               : -1
        EMPTY               : 0
        NOT_EXECUTED        : 1
        SINGLE_NODE         : 2
        SINGLE_RELATIONSHIP : 3
        NODE_LIST           : 4
        RELATIONSHIP_LIST   : 5
        CYPHER_RESULT       : 6
        
      
      defaults :
        data : null
        query : null
        queryOutOfSyncWithData : true
        state : DataBrowserState.State.NOT_EXECUTED

      initialize : (options) =>
        @searcher = new QueuedSearch(options.server)

      getQuery : =>
        @get "query"

      getData : =>
        @get "data"
      
      getState : =>
        @get "state"

      setQuery : (val, isForCurrentData=false, opts={}) =>
        if @getQuery() != val or opts.force is true
          if not isForCurrentData
            state = DataBrowserState.State.NOT_EXECUTED
          else
            state = @getState()
          
          @set {"query":val, "state":state, "queryOutOfSyncWithData": not isForCurrentData }, opts
          if state is DataBrowserState.State.NOT_EXECUTED
            @set {"data":null}, opts

      executeCurrentQuery : =>
        @searcher.exec(@getQuery()).then(@setData,@setData)

      setData : (result, basedOnCurrentQuery=true, opts={}) =>

        # These two to be determined below
        state = null
        data = null

        if result instanceof neo4j.models.Node
          state = DataBrowserState.State.SINGLE_NODE
          data = new NodeProxy(result)

        else if result instanceof neo4j.models.Relationship
          state = DataBrowserState.State.SINGLE_RELATIONSHIP
          data = new RelationshipProxy(result)

        else if _(result).isArray() and result.length is 0 
          state = DataBrowserState.State.EMPTY

        else if _(result).isArray() and result.length is 1
          # If only showing one item, show it in single-item view
          return @setData(result[0], basedOnCurrentQuery, opts)

        else if _(result).isArray()
          if result[0] instanceof neo4j.models.Relationship
            state = DataBrowserState.State.RELATIONSHIP_LIST
            data = new RelationshipList(result)

          else if result[0] instanceof neo4j.models.Node
            state = DataBrowserState.State.NODE_LIST
            data = new NodeList(result)
        
        else if result instanceof neo4j.cypher.QueryResult and result.size() is 0
          state = DataBrowserState.State.EMPTY

        else if result instanceof neo4j.cypher.QueryResult
          state = DataBrowserState.State.CYPHER_RESULT
          data = result
      
        else if result instanceof neo4j.exceptions.NotFoundException
          state = DataBrowserState.State.EMPTY

        else
          state = DataBrowserState.State.ERROR
          data = result

        @set({"state":state, "data":data, "queryOutOfSyncWithData" : not basedOnCurrentQuery }, opts)

)
