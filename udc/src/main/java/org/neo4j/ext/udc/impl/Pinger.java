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
package org.neo4j.ext.udc.impl;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

public class Pinger {

    private final String address;
    private final Map<String, String> usageDataMap;
    private int pingCount = 0;

    public Pinger( String address, Map<String, String> usageDataMap, boolean crashPing )
    {
        this.address = address;
        this.usageDataMap = usageDataMap;
        if ( crashPing ) pingCount = -1;
    }


    public void ping() throws IOException {
        pingCount++;

        StringBuffer uri = new StringBuffer("http://" + address + "/" + "?");

        for (String key : usageDataMap.keySet()) {
            uri.append(key);
            uri.append("=");
            uri.append(usageDataMap.get(key));
            uri.append("+");
        }

        // append counts
        if ( pingCount == 0 )
        {
            uri.append( "p=-1" );
            pingCount++;
        }
        else
        {
            uri.append( "p=" ).append( pingCount );
        }

        URL url = new URL(uri.toString());
        URLConnection con = url.openConnection();

        con.setDoInput(true);
        con.setDoOutput(false);
        con.setUseCaches(false);
        con.connect();

        con.getInputStream();
    }

   public Integer getPingCount() {
        return pingCount;
    }
}