/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.flink.action.cdc.postgresql;

import org.apache.paimon.flink.action.cdc.CdcActionITCaseBase;

import org.apache.flink.api.java.utils.MultipleParameterTool;
import org.junit.jupiter.api.AfterAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.lifecycle.Startables;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Base test class for {@link org.apache.paimon.flink.action.Action}s related to PsotgreSQL. */
public class PostgreSqlActionITCaseBase extends CdcActionITCaseBase {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlActionITCaseBase.class);

    protected static final PostgreSqlContainer POSTGRE_SQL_CONTAINER =
            createPostgreSqlContainer(PostgreSqlVersion.V9_6);

    private static final String USER = "postgres";
    private static final String PASSWORD = "postgres";

    protected static void startContainers() {
        LOG.info("Starting containers...");
        Startables.deepStart(Stream.of(POSTGRE_SQL_CONTAINER)).join();
        LOG.info("Containers are started.");
    }

    @AfterAll
    public static void stopContainers() {
        LOG.info("Stopping containers...");
        POSTGRE_SQL_CONTAINER.stop();
        LOG.info("Containers are stopped.");
    }

    protected Statement getStatement() throws SQLException {
        Connection conn =
                DriverManager.getConnection(
                        POSTGRE_SQL_CONTAINER.getJdbcUrl(),
                        POSTGRE_SQL_CONTAINER.getUsername(),
                        POSTGRE_SQL_CONTAINER.getPassword());
        return conn.createStatement();
    }

    private static PostgreSqlContainer createPostgreSqlContainer(
            PostgreSqlVersion postgreSqlVersion) {
        PostgreSqlContainer postgresContainer =
                (PostgreSqlContainer)
                        new PostgreSqlContainer(postgreSqlVersion)
                                .withPostgresConf("postgresql/postgresql.conf")
                                .withUsername(USER)
                                .withPassword(PASSWORD)
                                .withEnv("TZ", "America/Los_Angeles")
                                .withLogConsumer(new Slf4jLogConsumer(LOG));

        return postgresContainer;
    }

    protected Map<String, String> getBasicPostgreSqlConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("hostname", POSTGRE_SQL_CONTAINER.getHost());
        config.put("port", String.valueOf(POSTGRE_SQL_CONTAINER.getDatabasePort()));
        config.put("username", USER);
        config.put("password", PASSWORD);
        return config;
    }

    protected PostgreSqlSyncDatabaseActionBuilder syncDatabaseActionBuilder(
            Map<String, String> postgreSqlConfig) {
        return new PostgreSqlSyncDatabaseActionBuilder(postgreSqlConfig);
    }

    /** Builder to build {@link PostgreSqlSyncDatabaseAction} from action arguments. */
    protected class PostgreSqlSyncDatabaseActionBuilder
            extends SyncDatabaseActionBuilder<PostgreSqlSyncDatabaseAction> {

        public PostgreSqlSyncDatabaseActionBuilder(Map<String, String> postgreSqlConfig) {
            super(postgreSqlConfig);
        }

        @Override
        public PostgreSqlSyncDatabaseAction build() {
            List<String> args =
                    new ArrayList<>(
                            Arrays.asList("--warehouse", warehouse, "--database", database));

            args.addAll(mapToArgs("--postgresql-conf", sourceConfig));
            args.addAll(mapToArgs("--catalog-conf", catalogConfig));
            args.addAll(mapToArgs("--table-conf", tableConfig));

            args.addAll(nullableToArgs("--ignore-incompatible", ignoreIncompatible));
            args.addAll(nullableToArgs("--table-prefix", tablePrefix));
            args.addAll(nullableToArgs("--table-suffix", tableSuffix));
            args.addAll(nullableToArgs("--including-tables", includingTables));
            args.addAll(nullableToArgs("--excluding-tables", excludingTables));

            MultipleParameterTool params =
                    MultipleParameterTool.fromArgs(args.toArray(args.toArray(new String[0])));
            return (PostgreSqlSyncDatabaseAction)
                    new PostgreSqlSyncDatabaseActionFactory()
                            .create(params)
                            .orElseThrow(RuntimeException::new);
        }
    }
}
