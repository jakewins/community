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

import static org.neo4j.graphdb.factory.GraphDatabaseSetting.ANY;
import static org.neo4j.graphdb.factory.GraphDatabaseSetting.FALSE;

import java.util.Locale;

import org.neo4j.graphdb.factory.Default;
import org.neo4j.graphdb.factory.Description;
import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSetting.BooleanSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSetting.DefaultValue;
import org.neo4j.graphdb.factory.GraphDatabaseSetting.DirectorySetting;
import org.neo4j.graphdb.factory.GraphDatabaseSetting.FileSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSetting.IntegerSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSetting.ListSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSetting.PortSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSetting.StringSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSetting.URISetting;
import org.neo4j.graphdb.factory.Migrator;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.configuration.ConfigurationMigrator;

/**
 * Settings for Neo4j Server
 */
@Description( "Server configuration" )
public abstract class ServerSettings
{
    @Migrator
    public static final ConfigurationMigrator migrator = new ServerConfigurationMigrator();
    
    @Description( "Location of the database directory" )
    @Default( "data/graph.db" )
    public static final DirectorySetting database_location = new DirectorySetting( "org.neo4j.server.database.location", true, true);
    
    @Description("Neo4j server settings file")
    @Default("conf/neo4j-server.properties")
    public static final FileSetting neo_server_config_file = new FileSetting( "org.neo4j.server.properties");

    @Description("Low-level graph engine tuning file")
    @Default("conf/neo4j.properties")
    public static final FileSetting db_tuning_property_file = new FileSetting( "org.neo4j.server.db.tuning.properties");

    @Description( "To run in High Availability mode, configure the coord.cfg file, and the " +
                  "neo4j.properties config file, then set this to HA" )
    @Default( DatabaseMode.SINGLE )
    public static final DatabaseMode database_mode = new DatabaseMode();

    @Description( "HTTP port (for all data, administrative, and UI access)" )
    @Default( "7474" )
    public static final PortSetting webserver_http_port = new PortSetting( "org.neo4j.server.webserver.port");

    @Description( "Let the webserver only listen on the specified IP. Default \n" +
                  "is localhost (only accept local connections). Change to 0.0.0.0 to allow \n" +
                  "any connection. Please see the security section in the Neo4j \n" +
                  "manual before modifying this." )
    @Default( "localhost" )
    public static final StringSetting webserver_address = new StringSetting( "org.neo4j.server.webserver.address", ANY, "Must be a valid host or IP" );

    @Description( "Number of threads for the webserver" )
    public static final WebserverMaxThreads webserver_max_threads = new WebserverMaxThreads(  );

    @Description( "Limit how long time in ms a request may take" )
    public static final IntegerSetting webserver_limit_execution_time = new IntegerSetting( "org.neo4j.server.webserver.limit.executiontime", "Must be a valid time", 1, null );

    @Description("Server authorization rules")
    @Default("")
    public static final ListSetting<String> rest_security_rules = new ListSetting<String>( "org.neo4j.server.rest.security_rules", new StringSetting());

    @Description( "REST endpoint for the data API. Note the / in the end is mandatory" )
    @Default( "/db/data" )
    public static final URISetting rest_api_path = new URISetting( "org.neo4j.server.webadmin.data.uri", true);

    @Description( "REST endpoint of the administration API (used by Webadmin)" )
    @Default( "/db/manage" )
    public static final URISetting management_path = new URISetting( "org.neo4j.server.webadmin.management.uri", true);

    @Description( "Mount point for the web administration interface")
    @Default("/webadmin")
    public static final URISetting webadmin_path = new URISetting( "org.neo4j.server.webadmin.uri", true);
    
    @Description( "Location of the servers round-robin database directory" )
    @Default( "rrd" )
    public static final DirectorySetting rrdb_location = new DirectorySetting( "org.neo4j.server.webadmin.rrdb.location", database_location, true, true);

    @Description( "Comma separated list of JAXRS packages containing JAXRS resources, with an equal sign after each to denote the mount point.\n" +
                  "For instance: org.neo4j.server.thirdparty_jaxrs_classes=my.extension.package=/examples/myextension" )
    @Default("")
    public static final ListSetting<ThirdPartyJaxRsPackage> third_party_packages = new ListSetting<ThirdPartyJaxRsPackage>( "org.neo4j.server.thirdparty_jaxrs_classes", new ThirdPartyJaxRsPackageSetting());

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

    @Description( "Enable http request logging" )
    @Default( GraphDatabaseSetting.FALSE )
    public static final BooleanSetting http_logging_enabled = new BooleanSetting("org.neo4j.server.http.log.enabled");
    
    @Description( "Location for http request logging configuration" )
    @Default( "conf/neo4j-http-logging.xml" )
    public static final FileSetting http_logging_configuration_location = new FileSetting("org.neo4j.server.http.log.config");
    
    @Description( "Enable WADL generation (this is not officially supported, the generated WADL may contain errors)" )
    @Default( GraphDatabaseSetting.FALSE )
    public static final BooleanSetting wadl_enabled = new BooleanSetting("unsupported_wadl_generation_enabled");
    
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
    
    public static class ThirdPartyJaxRsPackageSetting extends GraphDatabaseSetting<ThirdPartyJaxRsPackage>
    {

        protected ThirdPartyJaxRsPackageSetting()
        {
            super("", "Each entry must look like: 'my.package=/my/mount/point', got '%s'.");
        }

        @Override
        public void validate(Locale locale, String value)
        {
            if(value == null)
                illegalValue(locale);
            
            if(value.contains(" ") || !value.contains("="))
                illegalValue(locale, value);
            
            if(value.split("=").length != 2)
                illegalValue(locale, value);
        }

        @Override
        public ThirdPartyJaxRsPackage valueOf(String rawValue, Config config)
        {
            String[] parts = rawValue.split("=");
            if(parts.length != 2) {
                throw new IllegalArgumentException("'" + rawValue + "' is not a valid unmanaged extension configuration.");
            }
            return new ThirdPartyJaxRsPackage(parts[0], parts[1]);
        }
        
    }

}
