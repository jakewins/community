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
   'ribcage/View'
   'neo4j/webadmin/utils/Keys'
   './consoleTemplate'
   'lib/amd/jQuery.putCursorAtEnd'], 
  (QueuedSearch, View, Keys, template, $) ->

    class ConsoleView extends View
      
      template : template

      events : 
        "keypress #data-console"      : "onKeyPress"
        "keyup #data-console"         : "onKeyUp"
        "paste #data-console"         : "onPaste"
        "click #data-execute-console" : "onSearchClicked"

      initialize : (options)->
        @dataModel = options.dataModel
        
        @searcher = new QueuedSearch(options.state.getServer())
        @dataModel.bind("change:query", @onDataModelQueryChanged)

      render : =>
        $(@el).html template( query : @dataModel.getQuery() )
        @_adjustConsoleHeightToNumberOfNewlines()
        @el

      _executeQuery : (query) ->
        @dataModel.setQuery(query, false, { force:true, silent:true})
        @dataModel.trigger("change:query")
        
        setResultData = (result) => @dataModel.setData(result)

        @searcher.exec(query).then(setResultData,setResultData)

      _setConsoleLines : (numberOfLines) ->
        height = 2 + 18 * numberOfLines
        @_getConsoleElement().css("height",height)

      _adjustConsoleHeightToNumberOfNewlines : =>
        @_setConsoleLines @_newlinesIn(@_getConsoleValue()) + 1

      _getConsoleValue : ()  -> @_getConsoleElement().val()
      _setConsoleValue : (v) -> @_getConsoleElement().val(v)
      _getConsoleElement : () -> $("#data-console",@el)

      _newlinesIn : (string) ->
        if string.match(/\n/g) then string.match(/\n/g).length else 0

      # Event handling

      onSearchClicked : (ev) =>
        @_executeQuery @_getConsoleValue()

      onKeyPress : (ev) =>

        if ev.which is Keys.ENTER and ev.ctrlKey or ev.which is 10 # WebKit
          ev.stopPropagation()
          @_executeQuery @_getConsoleValue()

        # Pre-emptively set the height here, because if we
        # only do it after this event (eg. onKeyUp), then
        # there is a visual jump as the browser renders a 
        # scroll bar for a split second. We still re-do this
        # onKeyUp, to correct the height in case the user has
        # pasted something or has removed newlines
        else if ev.which is Keys.ENTER
          @_setConsoleLines(@_newlinesIn(@_getConsoleValue()) + 2)

      onKeyUp : (ev) =>
        @_adjustConsoleHeightToNumberOfNewlines()

      onPaste : (ev) =>
        # We don't have an API to access the text being pasted,
        # so we work around it by adding this little job to the
        # end of the js work queue.
        setTimeout( @_adjustConsoleHeightToNumberOfNewlines, 0)

      onDataModelQueryChanged : (ev) =>
        if @dataModel.getQuery() != @_getConsoleValue()
          @render()

)
