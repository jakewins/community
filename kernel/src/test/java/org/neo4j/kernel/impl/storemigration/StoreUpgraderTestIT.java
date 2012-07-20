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
package org.neo4j.kernel.impl.storemigration;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.neo4j.kernel.CommonFactories.defaultFileSystemAbstraction;
import static org.neo4j.kernel.CommonFactories.defaultIdGeneratorFactory;
import static org.neo4j.kernel.impl.nioneo.store.CommonAbstractStore.ALL_STORES_VERSION;
import static org.neo4j.kernel.impl.storemigration.MigrationTestUtils.allStoreFilesHaveVersion;
import static org.neo4j.kernel.impl.storemigration.MigrationTestUtils.alwaysAllowed;
import static org.neo4j.kernel.impl.storemigration.MigrationTestUtils.changeVersionNumber;
import static org.neo4j.kernel.impl.storemigration.MigrationTestUtils.defaultConfig;
import static org.neo4j.kernel.impl.storemigration.MigrationTestUtils.truncateFile;
import static org.neo4j.kernel.impl.storemigration.MigrationTestUtils.verifyFilesHaveSameContent;
import static org.neo4j.kernel.impl.util.FileUtils.copyRecursively;
import static org.neo4j.kernel.impl.util.FileUtils.deleteRecursively;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.neo4j.kernel.impl.nioneo.store.NeoStore;
import org.neo4j.kernel.impl.storemigration.monitoring.SilentMigrationProgressMonitor;
import org.neo4j.kernel.impl.util.StringLogger;
import org.neo4j.test.TargetDirectory;

public class StoreUpgraderTestIT
{

    private final TargetDirectory target = TargetDirectory.forTest( StoreUpgraderTestIT.class );
    @Rule
    public final TargetDirectory.TestDirectory testDir = target.testDirectory();

    private StoreUpgrader newUpgrader( UpgradeConfiguration config, StoreMigrator migrator, DatabaseFiles files )
    {
        return new StoreUpgrader( defaultConfig(), StringLogger.DEV_NULL, config, new UpgradableDatabase(), migrator,
                files, defaultIdGeneratorFactory(), defaultFileSystemAbstraction() );        
    }
    
    @Test
    public void shouldUpgradeAnOldFormatStore() throws IOException
    {
        MigrationTestUtils.prepareSampleLegacyDatabase(testDir.directory());

        assertTrue( allStoreFilesHaveVersion( testDir.directory(), "v0.9.9" ) );

        newUpgrader( alwaysAllowed(), new StoreMigrator( new SilentMigrationProgressMonitor() ) , new DatabaseFiles() ).attemptUpgrade( new File( testDir.directory(), NeoStore.DEFAULT_NAME ).getPath() );

        assertTrue( allStoreFilesHaveVersion( testDir.directory(), ALL_STORES_VERSION ) );
    }

    @Test
    public void shouldLeaveACopyOfOriginalStoreFilesInBackupDirectory() throws IOException
    {
        MigrationTestUtils.prepareSampleLegacyDatabase(testDir.directory());

        newUpgrader( alwaysAllowed(), new StoreMigrator( new SilentMigrationProgressMonitor() ) , new DatabaseFiles() ).attemptUpgrade( new File( testDir.directory(), NeoStore.DEFAULT_NAME ).getPath() );

        verifyFilesHaveSameContent( MigrationTestUtils.findOldFormatStoreDirectory(), new File( testDir.directory(), "upgrade_backup" ) );
    }

    @Test
    public void shouldHaltUpgradeIfUpgradeConfigurationVetoesTheProcess() throws IOException
    {
        MigrationTestUtils.prepareSampleLegacyDatabase(testDir.directory());

        UpgradeConfiguration vetoingUpgradeConfiguration = new UpgradeConfiguration()
        {
            public void checkConfigurationAllowsAutomaticUpgrade()
            {
                throw new UpgradeNotAllowedByConfigurationException( "vetoed" );
            }
        };

        try
        {
            newUpgrader( vetoingUpgradeConfiguration, new StoreMigrator( new SilentMigrationProgressMonitor() ), new DatabaseFiles() ).attemptUpgrade( new File( testDir.directory(), NeoStore.DEFAULT_NAME ).getPath() );
            fail( "Should throw exception" );
        } catch ( UpgradeNotAllowedByConfigurationException e )
        {
            // expected
        }
    }

    @Test
    public void shouldLeaveAllFilesUntouchedIfWrongVersionNumberFound() throws IOException
    {
        File comparisonDirectory = new File(
                "target/"
                        + StoreUpgraderTestIT.class.getSimpleName()
                        + "shouldLeaveAllFilesUntouchedIfWrongVersionNumberFound-comparison" );
        MigrationTestUtils.prepareSampleLegacyDatabase(testDir.directory());

        changeVersionNumber( new File( testDir.directory(), "neostore.nodestore.db" ), "v0.9.5" );
        deleteRecursively( comparisonDirectory );
        copyRecursively( testDir.directory(), comparisonDirectory );

        try
        {
            newUpgrader( alwaysAllowed(), new StoreMigrator( new SilentMigrationProgressMonitor() ), new DatabaseFiles() ).attemptUpgrade( new File( testDir.directory(), NeoStore.DEFAULT_NAME ).getPath() );
            fail( "Should throw exception" );
        } catch ( StoreUpgrader.UnableToUpgradeException e )
        {
            // expected
        }

        verifyFilesHaveSameContent( comparisonDirectory, testDir.directory() );
    }

    @Test
    public void shouldRefuseToUpgradeIfAnyOfTheStoresWeNotShutDownCleanly() throws IOException
    {
        File comparisonDirectory = new File(
                "target/"
                        + StoreUpgraderTestIT.class.getSimpleName()
                        + "shouldRefuseToUpgradeIfAnyOfTheStoresWeNotShutDownCleanly-comparison" );
        MigrationTestUtils.prepareSampleLegacyDatabase(testDir.directory());

        truncateFile( new File( testDir.directory(),
                "neostore.propertystore.db.index.keys" ),
                "StringPropertyStore v0.9.9" );
        deleteRecursively( comparisonDirectory );
        copyRecursively( testDir.directory(), comparisonDirectory );

        try
        {
            newUpgrader( alwaysAllowed(), new StoreMigrator( new SilentMigrationProgressMonitor() ), new DatabaseFiles() ).attemptUpgrade( new File( testDir.directory(), NeoStore.DEFAULT_NAME ).getPath() );
            fail( "Should throw exception" );
        } catch ( StoreUpgrader.UnableToUpgradeException e )
        {
            // expected
        }

        verifyFilesHaveSameContent( comparisonDirectory, testDir.directory() );
    }

    @Test
    public void shouldRefuseToUpgradeIfAllOfTheStoresWeNotShutDownCleanly()
            throws IOException
    {
        File comparisonDirectory = new File(
                "target/" + StoreUpgraderTestIT.class.getSimpleName()
                        + "shouldRefuseToUpgradeIfAllOfTheStoresWeNotShutDownCleanly-comparison" );
        MigrationTestUtils.prepareSampleLegacyDatabase(testDir.directory());

        truncateAllFiles( testDir.directory() );
        deleteRecursively( comparisonDirectory );
        copyRecursively( testDir.directory(), comparisonDirectory );

        try
        {
            newUpgrader( alwaysAllowed(), new StoreMigrator( new SilentMigrationProgressMonitor() ),
                    new DatabaseFiles() ).attemptUpgrade( new File( testDir.directory(), NeoStore.DEFAULT_NAME ).getPath() );
            fail( "Should throw exception" );
        }
        catch ( StoreUpgrader.UnableToUpgradeException e )
        {
            // expected
        }

        verifyFilesHaveSameContent( comparisonDirectory, testDir.directory() );
    }

    public static void truncateAllFiles( File workingDir )
            throws IOException
    {
        for ( Map.Entry<String, String> legacyFile : UpgradableDatabase.fileNamesToExpectedVersions.entrySet() )
        {
            truncateFile( new File( workingDir, legacyFile.getKey()),
                    legacyFile.getValue() );
        }
    }

}
