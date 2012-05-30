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
package org.neo4j.server.modules;

import org.mortbay.jetty.Server;
import org.neo4j.server.statistic.StatisticCollector;
import org.neo4j.server.statistic.StatisticFilter;
import org.neo4j.server.statistic.StatisticStartupListener;
import org.neo4j.server.web.WebServer;

public class StatisticModule implements ServerModule
{
    private StatisticStartupListener listener;
    private WebServer webServer;
    private StatisticCollector collector;

    public StatisticModule(StatisticCollector collector, WebServer webServer) 
    {
        this.webServer = webServer;
        this.collector = collector;
    }
    
    @Override
    public void start( )
    {
        // TODO: Create our own filter abstraction, such that
        // we don't have to depend on using jetty here
        Server jetty = webServer.getJetty();

        listener = new StatisticStartupListener( jetty,
                new StatisticFilter( collector ) );
        jetty.addLifeCycleListener( listener );
    }

    public void stop()
    {
        listener.stop();
    }
}
