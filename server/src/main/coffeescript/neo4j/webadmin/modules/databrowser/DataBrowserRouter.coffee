
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
        "s" : "focusOnSearchField"
        "v" : "switchDataView"

      init : (appState) =>
        # Because we need to be able to match newlines, we need to define
        # this route with our own regex
        @route(/data\/search\/([\s\S]*)/i, 'search', @search)

        @appState = appState

        @dataModel = new DataBrowserState( server : @server )

        @dataModel.bind "change:query", @queryChanged

      base : =>
        @queryChanged()

      search : (query) =>
        @saveLocation()
        query = decodeURIComponent query
        while query.charAt(query.length-1) == "/"
          query = query.substr(0, query.length - 1)

        @dataModel.setQuery query
        console.log "CHANGED QUERY TO: ", query
        @appState.set( mainView : @getDataBrowserView() )

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

      focusOnSearchField : (ev) =>
        @base()
        setTimeout( (-> $("#data-console").focus()), 1)

      switchDataView : (ev) =>
        @getDataBrowserView().switchView()

      #
      # Internals
      #

      queryChanged : =>
        query = @dataModel.get "query"
        if query == null
          return @search("0")

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
)
