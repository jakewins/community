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
package org.neo4j.server.configuration;

import static org.neo4j.server.ServerTestUtils.createTempPropertyFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.server.ServerTestUtils;

public class PropertyFileBuilder
{

    private String portNo = "7474";
    private String webAdminUri = "http://localhost:7474/db/manage/";
    private String webAdminDataUri = "http://localhost:7474/db/data/";
    private String dbTuningPropertyFile = null;
    private ArrayList<Tuple> nameValuePairs = new ArrayList<Tuple>();

    private static class Tuple
    {
        public Tuple( String name, String value )
        {
            this.name = name;
            this.value = value;
        }

        public String name;
        public String value;
    }

    public static PropertyFileBuilder builder()
    {
        return new PropertyFileBuilder();
    }

    private PropertyFileBuilder()
    {
    }

    public File build() throws IOException
    {
        File temporaryConfigFile = createTempPropertyFile();

        String dbDir = ServerTestUtils.createTempDir().getAbsolutePath();
        String rrdbDir = ServerTestUtils.createTempDir().getAbsolutePath();
        Map<String, String> properties = MapUtil.stringMap(
                ServerSettings.database_location.name(), dbDir,
                ServerSettings.rrdb_location.name(), rrdbDir,
                ServerSettings.management_path.name(), webAdminUri,
                ServerSettings.rest_api_path.name(), webAdminDataUri );
        if ( portNo != null ) properties.put( ServerSettings.webserver_port.name(), portNo );
        if ( dbTuningPropertyFile != null ) properties.put( ServerSettings.db_tuning_property_file.name(), dbTuningPropertyFile );
        for ( Tuple t : nameValuePairs ) properties.put( t.name, t.value );
        ServerTestUtils.writePropertiesToFile( properties, temporaryConfigFile );
        return temporaryConfigFile;
    }

    public PropertyFileBuilder withDbTuningPropertyFile( File f )
    {
        dbTuningPropertyFile = f.getAbsolutePath();
        return this;
    }

    public PropertyFileBuilder withNameValue( String name, String value )
    {
        nameValuePairs.add( new Tuple( name, value ) );
        return this;
    }

    public PropertyFileBuilder withDbTuningPropertyFile( String propertyFileLocation )
    {
        dbTuningPropertyFile = propertyFileLocation;
        return this;
    }
}
