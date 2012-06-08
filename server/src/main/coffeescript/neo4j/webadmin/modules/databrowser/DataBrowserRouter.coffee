
define(
  ['./search/QueuedSearch',
   './views/DataBrowserView',
   './visualization/views/VisualizationSettingsView',
   './visualization/views/VisualizationProfileView',
   './models/DataBrowserState', 
   './DataBrowserSettings', 
   'ribcage/Router'], 
  (QueuedSearch, DataBrowserView, VisualizationSettingsView, VisualizationProfileView, DataBrowserState, DataBrowserSettings, Router) ->

    class DataBrowserRouter extends Router

      routes : 
        "/data/" : "base"
        "/data/visualization/settings/" : "visualizationSettings"
        "/data/visualization/settings/profile/" : "createVisualizationProfile"
        "/data/visualization/settings/profile/:id/" : "editVisualizationProfile"

      shortcuts : 
        "s" : "focusOnEditor"
        "v" : "switchDataView"

      init : (appState) =>
        # Because we need to be able to match newlines, we need to define
        # this route with our own regex
        @route(/data\/search\/([\s\S]*)/i, 'search', @search)

        @appState = appState

        @dataModel = new DataBrowserState( server : @appState.getServer() )

        @dataModel.bind "change:query", @queryChanged

      base : =>
        @queryChanged()

      search : (query) =>
        @saveLocation()
        query = decodeURIComponent query
        while query.charAt(query.length-1) == "/"
          query = query.substr(0, query.length - 1)

        @dataModel.setQuery query
        @appState.set( mainView : @getDataBrowserView() )

        if @_looksLikeReadOnlyQuery(query)
          @dataModel.executeCurrentQuery()

      visualizationSettings : () =>
        @saveLocation()
        @visualizationSettingsView ?= new VisualizationSettingsView
          dataBrowserSettings : @getDataBrowserSettings()
        @appState.set mainView : @visualizationSettingsView
        
      createVisualizationProfile : () =>
        @saveLocation()
        v = @getVisualizationProfileView()
        v.setIsCreateMode(true)
        @appState.set mainView : v
        
      editVisualizationProfile : (id) =>
        @saveLocation()
        profiles = @getDataBrowserSettings().getVisualizationProfiles()
        profile = profiles.get id
        
        v = @getVisualizationProfileView()
        v.setProfileToManage profile
        @appState.set mainView : v

      # 
      # Keyboard shortcuts
      # 

      focusOnEditor : (ev) =>
        @base()
        setTimeout( (=> @getDataBrowserView().focusOnEditor()), 1)

      switchDataView : (ev) =>
        @getDataBrowserView().switchView()

      #
      # Internals
      #

      queryChanged : =>
        query = @dataModel.getQuery()
        if query == null
          return @search("START n=node(0) RETURN n")

        url = "#/data/search/#{encodeURIComponent(query)}/"

        if location.hash != url
          location.hash = url

      showResult : (result) =>
        @dataModel.setData(result)

      getDataBrowserView : =>
        @view ?= new DataBrowserView
          state:@appState
          dataModel:@dataModel

      getVisualizationProfileView : =>
        @visualizationProfileView ?= new VisualizationProfileView 
          dataBrowserSettings:@getDataBrowserSettings()
          
      getDataBrowserSettings : ->
        @dataBrowserSettings ?= new DataBrowserSettings @appState.getSettings()


      # We only auto-execute read-only queries,
      # and we determine if a query is read-only here.
      # Note: Since we execute queries from the current URL,
      # this is a very real security issue. If modifying queries
      # slip through here, attackers can redirect an adminstrator
      # to a webadmin URL with a malicious Cypher query. Please
      # opt for better-safe-than-sorry when updating this regex.
      _looksLikeReadOnlyQuery : (query) ->
        pattern = ///^(
                    # Super basic cypher queries
                    (start 
                     \s+ 
                     n=node\(\d+\)
                     \s+
                     return\s+n)           | # or
 
                    # Direct node id lookups
                    ((node:)?\d+)          | # or

                    # Direct rel id lookups 
                    (rel:\d+)              | # or

                    # Direct rel id lookups
                    (rels:\d+)
                     )$
                  ///i

        pattern.test(query)
)
