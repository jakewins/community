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
  ['neo4j/webadmin/utils/ItemUrlResolver'
   './TabularView'
   './VisualizedView'
   './ConsoleView'
   './CreateRelationshipDialog'
   'neo4j/webadmin/modules/databrowser/models/DataBrowserState'
   'ribcage/View'
   './base'
   './queryMetadataTemplate'
   './notExecutedTemplate'
   './errorTemplate'
   'lib/amd/jQuery'], 
  (ItemUrlResolver, TabularView, VisualizedView, ConsoleView, CreateRelationshipDialog, DataBrowserState, View, template, queryMetadataTemplate, notExecutedTemplate, errorTemplate, $) ->

    State = DataBrowserState.State

    class DataBrowserView extends View
      
      template : template

      events : 
        "click #data-create-node" : "createNode"
        "click #data-create-relationship" : "createRelationship"
        "click #data-switch-view" : "switchView"

      initialize : (options)->
        @dataModel = options.dataModel
        @appState = options.state
        @server = options.state.getServer()

        @urlResolver = new ItemUrlResolver(@server)
        @consoleView = new ConsoleView(options)        

        @dataModel.bind("change:querymeta", @renderQueryMetadataView)
        @dataModel.bind("change:state",     @renderQueryMetadataView)

        @switchToTabularView()

      focusOnEditor : =>
        if @consoleView?
          @consoleView.focusOnEditor()

      render : =>
        $(@el).html @template( 
          viewType : @viewType)
        @renderConsoleView()
        @renderDataView()
        @renderQueryMetadataView()

      renderConsoleView : =>
        @consoleView.attach($("#data-console-area", @el).empty())
        @consoleView.render()
        return this

      renderQueryMetadataView : =>
        metaBar = $("#data-query-metadata", @el)
        switch @dataModel.getState() 
          when State.NOT_EXECUTED
            metaBar.html(notExecutedTemplate())
            return this
          when State.ERROR
            @renderError(@dataModel.getData())
            return this
          else 
            metaBar.html queryMetadataTemplate(
              meta : @dataModel.getQueryMetadata())
        return this

      renderDataView : =>
        @dataView.attach($("#data-area", @el).empty())
        @dataView.render()
        return this

      renderError : (error)->
        title = "Unknown error"
        description = "An unknown error occurred, was unable to retrieve a result for you."
        monospaceDescription = null

        if error instanceof neo4j.exceptions.HttpException
          if error.data.exception = "SyntaxException"
            title = "Invalid query"
            description = null
            monospaceDescription = error.data.message
        
        $("#data-query-metadata", @el).html(errorTemplate(
          "title":title
          "description":description
          "monospaceDescription":monospaceDescription
        ))

      createNode : =>
        @server.node({}).then (node) =>
          id = @urlResolver.extractNodeId(node.getSelf())
          @dataModel.setData( node, true ) 
          @dataModel.setQuery( id, true) 

      createRelationship : =>
        if @createRelationshipDialog?
          @hideCreateRelationshipDialog()
        else
          button = $("#data-create-relationship")
          button.addClass("selected")
          @createRelationshipDialog = new CreateRelationshipDialog(
            baseElement : button
            dataModel : @dataModel
            server : @server
            closeCallback : @hideCreateRelationshipDialog)

      hideCreateRelationshipDialog : =>
        if @createRelationshipDialog?
          @createRelationshipDialog.remove()
          delete(@createRelationshipDialog)
          $("#data-create-relationship").removeClass("selected")

      switchView : (ev) =>
        if @viewType == "visualized"
          $(ev.target).removeClass("tabular") if ev?
          @switchToTabularView()
        else
          $(ev.target).addClass("tabular") if ev? 
          @switchToVisualizedView()
        
        @renderDataView()
          

      switchToVisualizedView : =>
        if @dataView?
          @dataView.detach()
        
        @visualizedView ?= new VisualizedView(dataModel:@dataModel, appState:@appState, server:@server)
        @viewType = "visualized"
        @dataView = @visualizedView 

      switchToTabularView : =>
        if @dataView?
          @dataView.detach()
      
        @tabularView ?= new TabularView(dataModel:@dataModel, appState:@appState, server:@server)
        @viewType = "tabular"
        @dataView = @tabularView
        
      detach : ->
        @hideCreateRelationshipDialog()
        if @dataView? then @dataView.detach()
        if @consoleView? then @consoleView.detach()
        super()

      remove : =>
        @hideCreateRelationshipDialog()
        @dataView.remove()

)
