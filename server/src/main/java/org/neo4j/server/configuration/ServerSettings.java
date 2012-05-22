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

import org.neo4j.graphdb.factory.Default;
import org.neo4j.graphdb.factory.Description;
import org.neo4j.graphdb.factory.GraphDatabaseSetting;

import static org.neo4j.graphdb.factory.GraphDatabaseSetting.*;

/**
 * Settings for Neo4j Server
 */
@Description( "Server configuration" )
public abstract class ServerSettings
{
    @Description("Neo4j server settings file")
    public static final FileSetting neo_server_config_file = new FileSetting( "org.neo4j.server.properties");

    @Description("Low-level graph engine tuning file")
    @Default("conf/neo4j.properties")
    public static final FileSetting db_tuning_property_file = new FileSetting( "org.neo4j.server.db.tuning.properties");

    @Description( "Location of the database directory" )
    @Default( "data/graph.db" )
    public static final DirectorySetting database_location = new DirectorySetting( "org.neo4j.server.database.location");

    @Description( "To run in High Availability mode, configure the coord.cfg file, and the " +
                  "neo4j.properties config file, then set this to HA" )
    @Default( DatabaseMode.SINGLE )
    public static final DatabaseMode database_mode = new DatabaseMode();

    @Description( "HTTP port (for all data, administrative, and UI access)" )
    @Default( "7474" )
    public static final PortSetting webserver_port = new PortSetting( "org.neo4j.server.webserver.port");

    @Description( "Let the webserver only listen on the specified IP. Default \n" +
                  "is localhost (only accept local connections). Change to 0.0.0.0 to allow \n" +
                  "any connection. Please see the security section in the Neo4j \n" +
                  "manual before modifying this." )
    @Default( "localhost" )
    public static final StringSetting webserver_address = new StringSetting( "org.neo4j.server.webserver.address", ANY, "Must be a valid host or IP" );

    @Description( "Nr of threads for the webserver" )
    public static final WebserverMaxThreads webserver_max_threads = new WebserverMaxThreads(  );

    @Description( "Limit how long time in ms a request may take" )
    public static final IntegerSetting webserver_limit_execution_time = new IntegerSetting( "org.neo4j.server.webserver.limit.executiontime", "Must be a valid time", 1, null );

    @Description("Server authorization rules")
    public static final StringSetting rest_security_rules = new StringSetting( "org.neo4j.server.rest.security_rules", GraphDatabaseSetting.CSV, "Must be comma separated list of Java class names of security rules" );

    @Description( "REST endpoint for the data API. Note the / in the end is mandatory" )
    @Default( "/db/data/" )
    public static final StringSetting rest_api_path = new StringSetting( "org.neo4j.server.webadmin.data.uri", ANY, "Must be a valid URI path" );

    @Description( "REST endpoint of the administration API (used by Webadmin)" )
    @Default( "/db/manage/" )
    public static final StringSetting management_path = new StringSetting( "org.neo4j.server.webadmin.management.uri", ANY, "Must be a valid URI path" );

    @Description( "Location of the servers round-robin database directory" )
    @Default( "data/rrd" )
    public static final DirectorySetting rrdb_location = new DirectorySetting( "org.neo4j.server.webadmin.rrdb.location");

    @Description( "Comma separated list of JAXRS packages contains JAXRS Resoruce, one package name for each mountpoint.\n" +
                  "The listed package names will be loaded under the mountpoints specified, uncomment this line \n" +
                  "to mount the org.neo4j.examples.server.unmanaged.HelloWorldResource.java from neo4j-examples \n" +
                  "under /examples/unmanaged, resulting in a final URL of http://localhost:7474/examples/unmanaged/helloworld/{nodeId}" )
    public static final StringSetting third_party_packages = new StringSetting( "org.neo4j.server.thirdparty_jaxrs_classes", CSV, "Must be comma separated list of Java class names of JAX RS Resources");

    @Description( "Turn https-support on/off" )
    @Default( FALSE )
    public static final BooleanSetting webserver_https_enabled = new BooleanSetting( "org.neo4j.server.webserver.https.enabled" );

    @Description( "HTTPS port (for all data, administrative, and UI access)" )
    @Default( "7473" )
    public static final PortSetting webserver_https_port = new PortSetting( "org.neo4j.server.webserver.https.port" );

    @Description( "Internally generated keystore (don't try to put your own " +
                  "keystore there, it will get deleted when the server starts)" )
    @Default( "system/keystore" )
    public static final DirectorySetting webserver_https_keystore_path = new DirectorySetting( "org.neo4j.server.webserver.https.keystore.location");

    @Description( "Certificate location (auto generated if the file does not exist)" )
    @Default( "conf/ssl/snakeoil.cert" )
    public static final FileSetting webserver_https_certificate_path = new FileSetting( "org.neo4j.server.webserver.https.cert.location");

    @Description( "Private key location (auto generated if the file does not exist)" )
    @Default( "conf/ssl/snakeoil.key" )
    public static final FileSetting webserver_https_key_path = new FileSetting( "org.neo4j.server.webserver.https.key.location");

    public static class DatabaseMode
        extends GraphDatabaseSetting.OptionsSetting
    {
        public static final String SINGLE = "SINGLE";
        public static final String HA = "HA";

        public DatabaseMode( )
        {
            super( "org.neo4j.server.database.mode", SINGLE, HA );
        }
    }

    public static class WebserverMaxThreads
        extends IntegerSetting
        implements DefaultValue
    {
        public WebserverMaxThreads( )
        {
            super( "org.neo4j.server.webserver.maxthreads", "Must be a valid number", 1, null );
        }

        @Override
        public String getDefaultValue()
        {
            return ""+10 * Runtime.getRuntime().availableProcessors();
        }
    }

}
