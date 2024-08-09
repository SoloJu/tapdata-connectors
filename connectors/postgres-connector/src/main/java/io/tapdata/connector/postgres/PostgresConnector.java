package io.tapdata.connector.postgres;

import io.tapdata.common.CommonDbConnector;
import io.tapdata.common.SqlExecuteCommandFunction;
import io.tapdata.connector.postgres.bean.PostgresColumn;
import io.tapdata.connector.postgres.cdc.PostgresCdcRunner;
import io.tapdata.connector.postgres.cdc.offset.PostgresOffset;
import io.tapdata.connector.postgres.config.PostgresConfig;
import io.tapdata.connector.postgres.ddl.PostgresDDLSqlGenerator;
import io.tapdata.connector.postgres.dml.PostgresRecordWriter;
import io.tapdata.connector.postgres.exception.PostgresExceptionCollector;
import io.tapdata.connector.postgres.partition.wrappper.PGPartitionWrapper;
import io.tapdata.entity.codec.TapCodecsRegistry;
import io.tapdata.entity.error.CoreException;
import io.tapdata.entity.event.ddl.table.TapAlterFieldAttributesEvent;
import io.tapdata.entity.event.ddl.table.TapAlterFieldNameEvent;
import io.tapdata.entity.event.ddl.table.TapDropFieldEvent;
import io.tapdata.entity.event.ddl.table.TapNewFieldEvent;
import io.tapdata.entity.event.dml.TapRecordEvent;
import io.tapdata.entity.schema.TapField;
import io.tapdata.entity.schema.TapIndex;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.entity.schema.partition.TapPartition;
import io.tapdata.entity.schema.partition.TapPartitionInfo;
import io.tapdata.entity.schema.type.TapType;
import io.tapdata.entity.schema.value.TapArrayValue;
import io.tapdata.entity.schema.value.TapDateTimeValue;
import io.tapdata.entity.schema.value.TapDateValue;
import io.tapdata.entity.schema.value.TapMapValue;
import io.tapdata.entity.schema.value.TapRawValue;
import io.tapdata.entity.schema.value.TapStringValue;
import io.tapdata.entity.schema.value.TapTimeValue;
import io.tapdata.entity.schema.value.TapValue;
import io.tapdata.entity.schema.value.TapYearValue;
import io.tapdata.entity.simplify.pretty.BiClassHandlers;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.entity.utils.cache.Entry;
import io.tapdata.entity.utils.cache.Iterator;
import io.tapdata.entity.utils.cache.KVReadOnlyMap;
import io.tapdata.kit.DbKit;
import io.tapdata.kit.EmptyKit;
import io.tapdata.kit.ErrorKit;
import io.tapdata.kit.StringKit;
import io.tapdata.pdk.apis.annotations.TapConnectorClass;
import io.tapdata.pdk.apis.consumer.StreamReadConsumer;
import io.tapdata.pdk.apis.context.TapConnectionContext;
import io.tapdata.pdk.apis.context.TapConnectorContext;
import io.tapdata.pdk.apis.entity.ConnectionOptions;
import io.tapdata.pdk.apis.entity.TapAdvanceFilter;
import io.tapdata.pdk.apis.entity.TestItem;
import io.tapdata.pdk.apis.entity.WriteListResult;
import io.tapdata.pdk.apis.functions.ConnectorFunctions;
import io.tapdata.pdk.apis.functions.connection.TableInfo;
import io.tapdata.pdk.apis.functions.connector.common.vo.TapHashResult;
import org.postgresql.geometric.PGbox;
import org.postgresql.geometric.PGcircle;
import org.postgresql.geometric.PGline;
import org.postgresql.geometric.PGlseg;
import org.postgresql.geometric.PGpath;
import org.postgresql.geometric.PGpoint;
import org.postgresql.geometric.PGpolygon;
import org.postgresql.jdbc.PgArray;
import org.postgresql.jdbc.PgSQLXML;
import org.postgresql.util.PGInterval;
import org.postgresql.util.PGobject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * PDK for Postgresql
 *
 * @author Jarad
 * @date 2022/4/18
 */
@TapConnectorClass("spec_postgres.json")
public class PostgresConnector extends CommonDbConnector {
    protected PostgresConfig postgresConfig;
    protected PostgresJdbcContext postgresJdbcContext;
    private PostgresTest postgresTest;
    private PostgresCdcRunner cdcRunner; //only when task start-pause this variable can be shared
    private Object slotName; //must be stored in stateMap
    protected String postgresVersion;
    protected Map<String, Boolean> writtenTableMap = new ConcurrentHashMap<>();

    @Override
    public void onStart(TapConnectionContext connectorContext) {
        initConnection(connectorContext);
    }

    protected TapField makeTapField(DataMap dataMap) {
        return new PostgresColumn(dataMap).getTapField();
    }

    @Override
    public ConnectionOptions connectionTest(TapConnectionContext connectionContext, Consumer<TestItem> consumer) {
        postgresConfig = (PostgresConfig) new PostgresConfig().load(connectionContext.getConnectionConfig());
        ConnectionOptions connectionOptions = ConnectionOptions.create();
        connectionOptions.connectionString(postgresConfig.getConnectionString());
        try (
                PostgresTest postgresTest = new PostgresTest(postgresConfig, consumer, connectionOptions).initContext()
        ) {
            postgresTest.testOneByOne();
            return connectionOptions;
        }
    }

    @Override
    public void registerCapabilities(ConnectorFunctions connectorFunctions, TapCodecsRegistry codecRegistry) {
        //test
        connectorFunctions.supportErrorHandleFunction(this::errorHandle);
        //need to clear resource outer
        connectorFunctions.supportReleaseExternalFunction(this::onDestroy);
        // target
        connectorFunctions.supportWriteRecord(this::writeRecord);
        connectorFunctions.supportCreateTableV2(this::createTableV2);
        connectorFunctions.supportClearTable(this::clearTable);
        connectorFunctions.supportDropTable(this::dropTable);
        connectorFunctions.supportCreateIndex(this::createIndex);
//        connectorFunctions.supportQueryIndexes(this::queryIndexes);
//        connectorFunctions.supportDeleteIndex(this::dropIndexes);
        // source
        connectorFunctions.supportBatchCount(this::batchCount);
        connectorFunctions.supportBatchRead(this::batchReadWithoutOffset);
        connectorFunctions.supportStreamRead(this::streamRead);
        connectorFunctions.supportTimestampToStreamOffset(this::timestampToStreamOffset);
        // query
        connectorFunctions.supportQueryByFilter(this::queryByFilter);
        connectorFunctions.supportQueryByAdvanceFilter(this::queryByAdvanceFilterWithOffset);
        // ddl
        connectorFunctions.supportNewFieldFunction(this::fieldDDLHandler);
        connectorFunctions.supportAlterFieldNameFunction(this::fieldDDLHandler);
        connectorFunctions.supportAlterFieldAttributesFunction(this::fieldDDLHandler);
        connectorFunctions.supportDropFieldFunction(this::fieldDDLHandler);
        connectorFunctions.supportGetTableNamesFunction(this::getTableNames);
        connectorFunctions.supportExecuteCommandFunction((a, b, c) -> SqlExecuteCommandFunction.executeCommand(a, b, () -> postgresJdbcContext.getConnection(), this::isAlive, c));
        connectorFunctions.supportRunRawCommandFunction(this::runRawCommand);
        connectorFunctions.supportCountRawCommandFunction(this::countRawCommand);
        connectorFunctions.supportCountByPartitionFilterFunction(this::countByAdvanceFilter);

        codecRegistry.registerFromTapValue(TapRawValue.class, "text", tapRawValue -> {
            if (tapRawValue != null && tapRawValue.getValue() != null) return toJson(tapRawValue.getValue());
            return "null";
        });
        codecRegistry.registerFromTapValue(TapMapValue.class, "text", tapMapValue -> {
            if (tapMapValue != null && tapMapValue.getValue() != null) return toJson(tapMapValue.getValue());
            return "null";
        });
        codecRegistry.registerFromTapValue(TapArrayValue.class, "text", tapValue -> {
            if (tapValue != null && tapValue.getValue() != null) return toJson(tapValue.getValue());
            return "null";
        });

        codecRegistry.registerToTapValue(PgArray.class, (value, tapType) -> {
            PgArray pgArray = (PgArray) value;
            try (
                    ResultSet resultSet = pgArray.getResultSet()
            ) {
                return new TapArrayValue(DbKit.getDataArrayByColumnName(resultSet, "VALUE"));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        codecRegistry.registerToTapValue(PgSQLXML.class, (value, tapType) -> {
            try {
                return new TapStringValue(((PgSQLXML) value).getString());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        codecRegistry.registerToTapValue(PGbox.class, (value, tapType) -> new TapStringValue(value.toString()));
        codecRegistry.registerToTapValue(PGcircle.class, (value, tapType) -> new TapStringValue(value.toString()));
        codecRegistry.registerToTapValue(PGline.class, (value, tapType) -> new TapStringValue(value.toString()));
        codecRegistry.registerToTapValue(PGlseg.class, (value, tapType) -> new TapStringValue(value.toString()));
        codecRegistry.registerToTapValue(PGpath.class, (value, tapType) -> new TapStringValue(value.toString()));
        codecRegistry.registerToTapValue(PGobject.class, (value, tapType) -> new TapStringValue(value.toString()));
        codecRegistry.registerToTapValue(PGpoint.class, (value, tapType) -> new TapStringValue(value.toString()));
        codecRegistry.registerToTapValue(PGpolygon.class, (value, tapType) -> new TapStringValue(value.toString()));
        codecRegistry.registerToTapValue(UUID.class, (value, tapType) -> new TapStringValue(value.toString()));
        codecRegistry.registerToTapValue(PGInterval.class, (value, tapType) -> {
            //P1Y1M1DT12H12M12.312312S
            PGInterval pgInterval = (PGInterval) value;
            String interval = "P" + pgInterval.getYears() + "Y" +
                    pgInterval.getMonths() + "M" +
                    pgInterval.getDays() + "DT" +
                    pgInterval.getHours() + "H" +
                    pgInterval.getMinutes() + "M" +
                    pgInterval.getSeconds() + "S";
            return new TapStringValue(interval);
        });
        //TapTimeValue, TapDateTimeValue and TapDateValue's value is DateTime, need convert into Date object.
        codecRegistry.registerFromTapValue(TapTimeValue.class, tapTimeValue -> {
            if (postgresConfig.getOldVersionTimezone()) {
                return tapTimeValue.getValue().toTime();
            } else if (EmptyKit.isNotNull(tapTimeValue.getValue().getTimeZone())) {
                return tapTimeValue.getValue().toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
            } else {
                return tapTimeValue.getValue().toInstant().atZone(postgresConfig.getZoneId()).toLocalTime();
            }
        });
        codecRegistry.registerFromTapValue(TapDateTimeValue.class, tapDateTimeValue -> {
            if (postgresConfig.getOldVersionTimezone() || EmptyKit.isNotNull(tapDateTimeValue.getValue().getTimeZone())) {
                return tapDateTimeValue.getValue().toTimestamp();
            } else {
                return tapDateTimeValue.getValue().toInstant().atZone(postgresConfig.getZoneId()).toLocalDateTime();
            }
        });
        codecRegistry.registerFromTapValue(TapDateValue.class, tapDateValue -> tapDateValue.getValue().toSqlDate());
        codecRegistry.registerFromTapValue(TapYearValue.class, "character(4)", TapValue::getOriginValue);
        connectorFunctions.supportGetTableInfoFunction(this::getTableInfo);
        connectorFunctions.supportTransactionBeginFunction(this::beginTransaction);
        connectorFunctions.supportTransactionCommitFunction(this::commitTransaction);
        connectorFunctions.supportTransactionRollbackFunction(this::rollbackTransaction);
        connectorFunctions.supportQueryHashByAdvanceFilterFunction(this::queryTableHash);
        connectorFunctions.supportQueryPartitionTablesByParentName(this::discoverPartitionInfoByParentName);

    }

    //clear resource outer and jdbc context
    private void onDestroy(TapConnectorContext connectorContext) throws Throwable {
        try {
            onStart(connectorContext);
            if (EmptyKit.isNotNull(cdcRunner)) {
                cdcRunner.closeCdcRunner();
                cdcRunner = null;
            }
            if (EmptyKit.isNotNull(slotName)) {
                clearSlot();
            }
        } finally {
            onStop(connectorContext);
        }
    }

    //clear postgres slot
    private void clearSlot() throws Throwable {
        postgresJdbcContext.queryWithNext("SELECT COUNT(*) FROM pg_replication_slots WHERE slot_name='" + slotName + "' AND active='false'", resultSet -> {
            if (resultSet.getInt(1) > 0) {
                postgresJdbcContext.execute("SELECT pg_drop_replication_slot('" + slotName + "')");
            }
        });
    }

    private void buildSlot(TapConnectorContext connectorContext, Boolean needCheck) throws Throwable {
        if (EmptyKit.isNull(slotName)) {
            slotName = "tapdata_cdc_" + UUID.randomUUID().toString().replaceAll("-", "_");
            postgresJdbcContext.execute("SELECT pg_create_logical_replication_slot('" + slotName + "','" + postgresConfig.getLogPluginName() + "')");
            tapLogger.info("new logical replication slot created, slotName:{}", slotName);
            connectorContext.getStateMap().put("tapdata_pg_slot", slotName);
        } else if (needCheck) {
            AtomicBoolean existSlot = new AtomicBoolean(true);
            postgresJdbcContext.queryWithNext("SELECT COUNT(*) FROM pg_replication_slots WHERE slot_name='" + slotName + "'", resultSet -> {
                if (resultSet.getInt(1) <= 0) {
                    existSlot.set(false);
                }
            });
            if (existSlot.get()) {
                tapLogger.info("Using an existing logical replication slot, slotName:{}", slotName);
            } else {
                tapLogger.warn("The previous logical replication slot no longer exists. Although it has been rebuilt, there is a possibility of data loss. Please check");
            }
        }
    }

    private static final String PG_REPLICATE_IDENTITY = "select relname, relreplident from pg_class " +
            "where relnamespace=(select oid from pg_namespace where nspname='%s') and relname in (%s)";

    private void testReplicateIdentity(KVReadOnlyMap<TapTable> tableMap) {
        if ("pgoutput".equals(postgresConfig.getLogPluginName())) {
            tapLogger.warn("The pgoutput plugin may cause before of data loss, if you need, please use another plugin instead, such as wal2json");
            return;
        }
        if (EmptyKit.isNull(tableMap)) {
            return;
        }
        List<String> tableList = new ArrayList<>();
        List<String> hasPrimary = new ArrayList<>();
        Iterator<Entry<TapTable>> iterator = tableMap.iterator();
        while (iterator.hasNext()) {
            Entry<TapTable> entry = iterator.next();
            tableList.add(entry.getKey());
            if (EmptyKit.isNotEmpty(entry.getValue().primaryKeys())) {
                hasPrimary.add(entry.getKey());
            }
        }
        List<String> noPrimaryOrFull = new ArrayList<>(); //无主键表且identity不为full
        List<String> primaryNotDefaultOrFull = new ArrayList<>(); //有主键表但identity不为full也不为default
        try {
            postgresJdbcContext.query(String.format(PG_REPLICATE_IDENTITY, postgresConfig.getSchema(), StringKit.joinString(tableList, "'", ",")), resultSet -> {
                while (resultSet.next()) {
                    if (!hasPrimary.contains(resultSet.getString("relname")) && !"f".equals(resultSet.getString("relreplident"))) {
                        noPrimaryOrFull.add(resultSet.getString("relname"));
                    }
                    if (hasPrimary.contains(resultSet.getString("relname")) && !"f".equals(resultSet.getString("relreplident")) && !"d".equals(resultSet.getString("relreplident"))) {
                        primaryNotDefaultOrFull.add(resultSet.getString("relname"));
                    }
                }
            });
        } catch (Exception e) {
            return;
        }
        if (EmptyKit.isNotEmpty(noPrimaryOrFull)) {
            tapLogger.warn("The following tables do not have a primary key and the identity is not full, which may cause before of data loss: {}", String.join(",", noPrimaryOrFull));
        }
        if (EmptyKit.isNotEmpty(primaryNotDefaultOrFull)) {
            tapLogger.warn("The following tables have a primary key, but the identity is not full or default, which may cause before of data loss: {}", String.join(",", primaryNotDefaultOrFull));
        }
    }

    @Override
    public void onStop(TapConnectionContext connectionContext) {
        ErrorKit.ignoreAnyError(() -> {
            if (EmptyKit.isNotNull(cdcRunner)) {
                cdcRunner.closeCdcRunner();
            }
        });
        EmptyKit.closeQuietly(postgresTest);
        EmptyKit.closeQuietly(postgresJdbcContext);
    }

    //initialize jdbc context, slot name, version
    private void initConnection(TapConnectionContext connectionContext) {
        postgresConfig = (PostgresConfig) new PostgresConfig().load(connectionContext.getConnectionConfig());
        postgresTest = new PostgresTest(postgresConfig, testItem -> {
        },null).initContext();
        postgresJdbcContext = new PostgresJdbcContext(postgresConfig);
        commonDbConfig = postgresConfig;
        jdbcContext = postgresJdbcContext;
        isConnectorStarted(connectionContext, tapConnectorContext -> {
            slotName = tapConnectorContext.getStateMap().get("tapdata_pg_slot");
            postgresConfig.load(tapConnectorContext.getNodeConfig());
        });
        commonSqlMaker = new PostgresSqlMaker().closeNotNull(postgresConfig.getCloseNotNull());
        postgresVersion = postgresJdbcContext.queryVersion();
        ddlSqlGenerator = new PostgresDDLSqlGenerator();
        tapLogger = connectionContext.getLog();
        fieldDDLHandlers = new BiClassHandlers<>();
        fieldDDLHandlers.register(TapNewFieldEvent.class, this::newField);
        fieldDDLHandlers.register(TapAlterFieldAttributesEvent.class, this::alterFieldAttr);
        fieldDDLHandlers.register(TapAlterFieldNameEvent.class, this::alterFieldName);
        fieldDDLHandlers.register(TapDropFieldEvent.class, this::dropField);
        exceptionCollector = new PostgresExceptionCollector();
    }

    private void openIdentity(TapTable tapTable) throws SQLException {
        if (EmptyKit.isEmpty(tapTable.primaryKeys())
                && (EmptyKit.isEmpty(tapTable.getIndexList()) || tapTable.getIndexList().stream().noneMatch(TapIndex::isUnique))) {
            jdbcContext.execute("ALTER TABLE \"" + jdbcContext.getConfig().getSchema() + "\".\"" + tapTable.getId() + "\" REPLICA IDENTITY FULL");
        }
    }

    protected boolean makeSureHasUnique(TapTable tapTable) throws SQLException {
        return jdbcContext.queryAllIndexes(Collections.singletonList(tapTable.getId())).stream().anyMatch(v -> "1".equals(v.getString("isUnique")));
    }

    //write records as all events, prepared
    protected void writeRecord(TapConnectorContext connectorContext, List<TapRecordEvent> tapRecordEvents, TapTable tapTable, Consumer<WriteListResult<TapRecordEvent>> writeListResultConsumer) throws SQLException {
        boolean hasUniqueIndex;
        if (EmptyKit.isNull(writtenTableMap.get(tapTable.getId()))) {
            openIdentity(tapTable);
            hasUniqueIndex = makeSureHasUnique(tapTable);
            writtenTableMap.put(tapTable.getId(), hasUniqueIndex);
        } else {
            hasUniqueIndex = writtenTableMap.get(tapTable.getId());
        }
        String insertDmlPolicy = connectorContext.getConnectorCapabilities().getCapabilityAlternative(ConnectionOptions.DML_INSERT_POLICY);
        if (insertDmlPolicy == null) {
            insertDmlPolicy = ConnectionOptions.DML_INSERT_POLICY_UPDATE_ON_EXISTS;
        }
        String updateDmlPolicy = connectorContext.getConnectorCapabilities().getCapabilityAlternative(ConnectionOptions.DML_UPDATE_POLICY);
        if (updateDmlPolicy == null) {
            updateDmlPolicy = ConnectionOptions.DML_UPDATE_POLICY_IGNORE_ON_NON_EXISTS;
        }
        if (isTransaction) {
            String threadName = Thread.currentThread().getName();
            Connection connection;
            if (transactionConnectionMap.containsKey(threadName)) {
                connection = transactionConnectionMap.get(threadName);
            } else {
                connection = postgresJdbcContext.getConnection();
                transactionConnectionMap.put(threadName, connection);
            }
            new PostgresRecordWriter(postgresJdbcContext, connection, tapTable, hasUniqueIndex ? postgresVersion : "90500")
                    .setInsertPolicy(insertDmlPolicy)
                    .setUpdatePolicy(updateDmlPolicy)
                    .setTapLogger(tapLogger)
                    .write(tapRecordEvents, writeListResultConsumer, this::isAlive);

        } else {
            new PostgresRecordWriter(postgresJdbcContext, tapTable, hasUniqueIndex ? postgresVersion : "90500")
                    .setInsertPolicy(insertDmlPolicy)
                    .setUpdatePolicy(updateDmlPolicy)
                    .setTapLogger(tapLogger)
                    .write(tapRecordEvents, writeListResultConsumer, this::isAlive);
        }
    }

    private void streamRead(TapConnectorContext nodeContext, List<String> tableList, Object offsetState, int recordSize, StreamReadConsumer consumer) throws Throwable {
        cdcRunner = new PostgresCdcRunner(postgresJdbcContext);
        testReplicateIdentity(nodeContext.getTableMap());
        buildSlot(nodeContext, true);
        cdcRunner.useSlot(slotName.toString()).watch(tableList).offset(offsetState).registerConsumer(consumer, recordSize);
        cdcRunner.startCdcRunner();
        if (EmptyKit.isNotNull(cdcRunner) && EmptyKit.isNotNull(cdcRunner.getThrowable().get())) {
            Throwable throwable = ErrorKit.getLastCause(cdcRunner.getThrowable().get());
            if (throwable instanceof SQLException) {
                exceptionCollector.collectTerminateByServer(throwable);
                exceptionCollector.collectCdcConfigInvalid(throwable);
                exceptionCollector.revealException(throwable);
            }
            throw throwable;
        }
    }

    private Object timestampToStreamOffset(TapConnectorContext connectorContext, Long offsetStartTime) throws Throwable {
        if (EmptyKit.isNotNull(offsetStartTime)) {
            tapLogger.warn("Postgres specified time start increment is not supported, use the current time as the start increment");
        }
        //test streamRead log plugin
        boolean canCdc = Boolean.TRUE.equals(postgresTest.testStreamRead());
        if (canCdc) {
            if ("pgoutput".equals(postgresConfig.getLogPluginName()) && postgresVersion.compareTo("100000") > 0) {
                createPublicationIfNotExist();
            }
            testReplicateIdentity(connectorContext.getTableMap());
            buildSlot(connectorContext, false);
        }
        return new PostgresOffset();
    }

    private void createPublicationIfNotExist() throws SQLException {
        String publicationName = postgresConfig.getPartitionRoot() ? "dbz_publication_root" : "dbz_publication";
        AtomicBoolean needCreate = new AtomicBoolean(false);
        postgresJdbcContext.queryWithNext(String.format("SELECT COUNT(1) FROM pg_publication WHERE pubname = '%s'", publicationName), resultSet -> {
            if (resultSet.getInt(1) <= 0) {
                needCreate.set(true);
            }
        });
        if (needCreate.get()) {
            postgresJdbcContext.execute(String.format("CREATE PUBLICATION %s FOR ALL TABLES %s", publicationName, postgresConfig.getPartitionRoot() ? "WITH (publish_via_partition_root = true)" : ""));
        }
    }

    protected TableInfo getTableInfo(TapConnectionContext tapConnectorContext, String tableName) {
        DataMap dataMap = postgresJdbcContext.getTableInfo(tableName);
        TableInfo tableInfo = TableInfo.create();
        tableInfo.setNumOfRows(Long.valueOf(dataMap.getString("size")));
        tableInfo.setStorageSize(new BigDecimal(dataMap.getString("rowcount")).longValue());
        return tableInfo;
    }


    private String buildHashSql(TapAdvanceFilter filter, TapTable table) {
        StringBuilder sql = new StringBuilder("select SUM(MOD(" +
                " (select n.md5 from (" +
                "  select case when t.num < 0 then t.num + 18446744073709551616 when t.num > 0 then t.num end as md5" +
                "  from (select (cast(");
        sql.append("CAST(( 'x' || SUBSTRING(MD5(CONCAT_WS('', ");
        LinkedHashMap<String, TapField> nameFieldMap = table.getNameFieldMap();
        java.util.Iterator<Map.Entry<String, TapField>> entryIterator = nameFieldMap.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<String, TapField> next = entryIterator.next();
            String fieldName = next.getKey();
            TapField field = nameFieldMap.get(next.getKey());
            byte type = next.getValue().getTapType().getType();
            if (type == TapType.TYPE_NUMBER && (field.getDataType().toLowerCase().contains("real") ||
                    field.getDataType().toLowerCase().contains("double") ||
                    field.getDataType().toLowerCase().contains("numeric") ||
                    field.getDataType().toLowerCase().contains("float"))) {
                sql.append(String.format("trunc(\"%s\")", fieldName)).append(",");
                continue;
            }

            if (type == TapType.TYPE_STRING && field.getDataType().toLowerCase().contains("character(")) {
                sql.append(String.format("TRIM( \"%s\" )", fieldName)).append(",");
                continue;
            }

            if (type == TapType.TYPE_BOOLEAN && field.getDataType().toLowerCase().contains("boolean")) {
                sql.append(String.format("CAST( \"%s\" as int )", fieldName)).append(",");
                continue;
            }

            if (type == TapType.TYPE_TIME && field.getDataType().toLowerCase().contains("with time zone")) {
                sql.append(String.format("SUBSTRING(cast(\"%s\" as varchar) FROM 1 FOR 8)", fieldName)).append(",");
                continue;
            }

            switch (type) {
                case TapType.TYPE_DATETIME:
                    sql.append(String.format("EXTRACT(epoch FROM CAST(date_trunc('second',\"%s\" ) AS TIMESTAMP))", fieldName)).append(",");
                    break;
                case TapType.TYPE_BINARY:
                    break;
                default:
                    sql.append(String.format("\"%s\"", fieldName)).append(",");
                    break;
            }
        }
        sql = new StringBuilder(sql.substring(0, sql.length() - 1));
        sql.append(" )) FROM 1 FOR 16)) AS bit(64)) as BIGINT)) AS num " +
                "  FROM ").append("\"" + table.getName() + "\"  ");
        sql.append(commonSqlMaker.buildCommandWhereSql(filter, ""));
        sql.append(") t) n),64))");
        return sql.toString();
    }

    protected void queryTableHash(TapConnectorContext connectorContext, TapAdvanceFilter filter, TapTable table, Consumer<TapHashResult<String>> consumer) throws SQLException {
        String sql = buildHashSql(filter, table);
        jdbcContext.query(sql, resultSet -> {
            if (isAlive() && resultSet.next()) {
                consumer.accept(TapHashResult.create().withHash(resultSet.getString(1)));
            }
        });
    }

    @Override
    protected void processDataMap(DataMap dataMap, TapTable tapTable) {
        if (!postgresConfig.getOldVersionTimezone()) {
            for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Timestamp) {
                    if (!tapTable.getNameFieldMap().containsKey(entry.getKey())) {
                        continue;
                    }
                    if (!tapTable.getNameFieldMap().get(entry.getKey()).getDataType().endsWith("with time zone")) {
                        entry.setValue(((Timestamp) value).toLocalDateTime().minusHours(postgresConfig.getZoneOffsetHour()));
                    } else {
                        entry.setValue(((Timestamp) value).toLocalDateTime().minusHours(TimeZone.getDefault().getRawOffset() / 3600000).atZone(ZoneOffset.UTC));
                    }
                } else if (value instanceof Date) {
                    entry.setValue(Instant.ofEpochMilli(((Date) value).getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime());
                } else if (value instanceof Time) {
                    if (!tapTable.getNameFieldMap().containsKey(entry.getKey())) {
                        continue;
                    }
                    if (!tapTable.getNameFieldMap().get(entry.getKey()).getDataType().endsWith("with time zone")) {
                        entry.setValue(Instant.ofEpochMilli(((Time) value).getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime().minusHours(postgresConfig.getZoneOffsetHour()));
                    } else {
                        entry.setValue(Instant.ofEpochMilli(((Time) value).getTime()).atZone(ZoneOffset.UTC));
                    }
                }
            }
        }
    }

    @Override
    protected String getHashSplitStringSql(TapTable tapTable) {
        Collection<String> pks = tapTable.primaryKeys();
        if (pks.isEmpty()) throw new CoreException("No primary keys found for table: " + tapTable.getName());

        return "abs(('x' || MD5(CONCAT_WS(',', \"" + String.join("\", \"", pks) + "\")))::bit(64)::bigint)";
    }

    public void discoverPartitionInfoByParentName(TapConnectorContext connectorContext, List<TapTable> table, Consumer<List<TapTable>> consumer) throws SQLException {
        List<String> tables = table.stream()
                .map(TapTable::getId)
                .collect(Collectors.toList());
        Set<String> queryTable = new HashSet<>(tables);
        discoverSchema(connectorContext, tables, 100, tableList -> {
            if (null == tableList || tableList.isEmpty()) return;
            tableList.stream()
                    .filter(Objects::nonNull)
                    .filter(tapTable -> Objects.nonNull(tapTable.getPartitionInfo()))
                    .filter(tapTable -> Objects.nonNull(tapTable.getPartitionInfo().getPartitionSchemas()))
                    .filter(tapTable -> !tapTable.getPartitionInfo().getPartitionSchemas().isEmpty())
                    .forEach(tapTable -> queryTable.addAll(tapTable.getPartitionInfo()
                            .getPartitionSchemas()
                            .stream()
                            .filter(Objects::nonNull)
                            .filter(i -> Objects.nonNull(i.getTableName()))
                            .map(TapPartitionInfo::getTableName)
                            .collect(Collectors.toList()))
                    );
        });
        discoverSchema(connectorContext, new ArrayList<>(queryTable), 100, consumer);
    }

    @Override
    public List<TapTable> discoverPartitionInfo(List<TapTable> tapTableList) {
        if (null == tapTableList || tapTableList.isEmpty()) return tapTableList;
        Map<String, TapTable> collect = tapTableList.stream().filter(Objects::nonNull).collect(Collectors.toMap(TapTable::getId, t -> t, (t1, t2) -> t1));
        if (collect.isEmpty()) return tapTableList;
        try {
            Set<String> removeTableIds = discoverPartitionInfo(collect);
            //移除子表
            Optional.ofNullable(removeTableIds).ifPresent(ids -> ids.forEach(id -> tapTableList.removeIf(t -> id.equals(t.getId()))));
        } catch (SQLException e) {
            tapLogger.error(e.getMessage(), e);
        }
        return tapTableList;
    }

    protected Set<String> discoverPartitionInfo(Map<String, TapTable> tapTableList) throws SQLException {
        if (null == tapTableList || tapTableList.isEmpty()) return null;
        if (postgresVersion.compareTo("100000") < 0 ) {
            // 6.3以下版本不支持分区 | 10.0版本 只支持继承方式创建表 ---> 不属于分区表，暂不支持
            tapLogger.info("Not support partition table when {} version pg", postgresVersion);
            return null;
        }

        Set<String> removeTableIds = new HashSet<>();
        Map<String, TapPartition> partitionMap = new HashMap<>();
        //10.0及以上版本 支持支持声明式分区和  继承式分区表(继承方式创建表 ---> 不属于分区表)
        StringJoiner tables = new StringJoiner(",");
        tapTableList.keySet().forEach(name -> tables.add(String.format("'%s'", name)));
        String tableStr = tables.toString();
        jdbcContext.query(String.format(SQL_MORE_10_VERSION, postgresConfig.getSchema(), tableStr, tableStr), r -> {
            while (r.next()) {
                String tableType = String.valueOf(r.getString(TableType.KEY_TABLE_TYPE)).trim();
                String parent = r.getString(TableType.KEY_PARENT_TABLE);
                String partitionTable = r.getString(TableType.KEY_PARTITION_TABLE);

                if (null == parent || "".equals(parent.trim())) {
                    parent = partitionTable;
                }

                String tableName = r.getString(TableType.KEY_TABLE_NAME);
                String checkOrPartitionRule = r.getString(TableType.KEY_CHECK_OR_PARTITION_RULE);
                String partitionType = String.valueOf(r.getString(TableType.KEY_PARTITION_TYPE)).trim();
                if (TableType.INHERIT.equalsIgnoreCase(partitionType)) {
                    //移除继承方式创建的子表
                    if (TableType.CHILD_TABLE.equalsIgnoreCase(tableType)) {
                        removeTableIds.add(tableName);
                    }
                    continue;
                }
                String partitionBound  = String.valueOf(r.getString(TableType.KEY_PARTITION_BOUND));
                TapPartition tapPartition = partitionMap.computeIfAbsent(parent, k -> TapPartition.create());
                switch (tableType) {
                    case TableType.PARTITIONED_TABLE:
                        tapPartition.originPartitionStageSQL(checkOrPartitionRule);
                        Optional.ofNullable(PGPartitionWrapper.partitionFields(tapTableList.get(parent), partitionType, checkOrPartitionRule, tableName, tapLogger))
                                .ifPresent(tapPartition::addAllPartitionFields);
                    case TableType.PARENT_TABLE:
                        Optional.ofNullable(PGPartitionWrapper.type(partitionType, tableName, tapLogger))
                                .ifPresent(tapPartition::type);
                        break;
                    case TableType.CHILD_TABLE:
                        TapTable tapTable = tapTableList.get(parent).partitionMasterTableId(parent);
                        TapPartitionInfo partitionSchema = TapPartitionInfo.create()
                                .originPartitionBoundSQL(partitionBound)
                                .tableName(tableName);
                        Optional.ofNullable(PGPartitionWrapper.warp(tapTable, partitionType, checkOrPartitionRule, partitionBound, tapLogger))
                                .ifPresent(partitionSchema::setPartitionType);
                        tapPartition.appendPartitionSchemas(partitionSchema);
                        break;
                    default:
                }
            }
        });
        partitionMap.forEach((key, partition) -> Optional.ofNullable(tapTableList.get(key)).ifPresent(tab -> tab.setPartitionInfo(partition)));
        return removeTableIds;
    }

    public static class TableType {
        public static final String PARTITIONED_TABLE = "Partitioned Table";
        public static final String CHILD_TABLE = "Child Table";
        public static final String PARENT_TABLE = "Parent Table";
        public static final String REGULAR_TABLE = "Regular Table";

        public static final String INHERIT = "Inherit";
        public static final String RANGE = "Range";
        public static final String HASH = "Hash";
        public static final String LIST = "List";
        public static final String UN_KNOW = "Unknow";

        public static final String KEY_TABLE_TYPE = "table_type";
        public static final String KEY_PARENT_TABLE = "parent_table";
        public static final String KEY_PARTITION_TABLE = "partition_table";
        public static final String KEY_TABLE_NAME = "table_name";
        public static final String KEY_CHECK_OR_PARTITION_RULE = "check_or_partition_rule";
        public static final String KEY_PARTITION_TYPE = "partition_type";
        public static final String KEY_PARTITION_BOUND = "partition_bound";

    }

    public static final String SQL_MORE_10_VERSION = "WITH  " +
            "all_tables AS (  " +
            "    SELECT  " +
            "        c.oid AS table_oid,  " +
            "        c.relfilenode as relfilenode,  " +
            "        c.relname AS table_name,  " +
            "        n.nspname AS schema_name,  " +
            "        i.inhparent    as inhparent,  " +
            "        CASE  " +
            "            WHEN p.partrelid IS NOT NULL THEN '" + TableType.PARTITIONED_TABLE + "'  " +
            "            WHEN i.inhrelid IS NOT NULL THEN '" + TableType.CHILD_TABLE + "'  " +
            "            WHEN c.relhassubclass = true THEN '" + TableType.PARENT_TABLE + "'  " +
            "            ELSE '" + TableType.REGULAR_TABLE + "'  " +
            "            END AS table_type  " +
            "    FROM  " +
            "        pg_class c  " +
            "            JOIN pg_namespace n ON c.relnamespace = n.oid  " +
            "            LEFT JOIN pg_partitioned_table p ON c.oid = p.partrelid  " +
            "            LEFT JOIN pg_inherits i ON c.oid = i.inhrelid  " +
            "    WHERE  " +
            "            n.nspname = '%s' and (c.relname in (%s) OR inhparent in (select oid from pg_class where relname in (%s))) " +
            "),  " +
            "  " +
            "inherits AS (  " +
            "    SELECT  " +
            "        parent.relname AS parent_table,  " +
            "        child.relname AS child_table,  " +
            "        pg_inherits.inhrelid as inhrel_id  " +
            "    FROM  " +
            "        pg_inherits  " +
            "            JOIN pg_class parent ON pg_inherits.inhparent = parent.oid  " +
            "            JOIN pg_class child ON pg_inherits.inhrelid = child.oid  " +
            "),  " +
            "  " +
            "inherits_check AS (  " +
            "    SELECT  " +
            "          parent.relname AS parent_table,  " +
            "          child.relname AS partition_table,  " +
            "          conname AS constraint_name,  " +
            "          pg_catalog.pg_get_constraintdef(con.oid) AS constraint_definition  " +
            "      FROM  " +
            "          pg_class parent  " +
            "              JOIN  " +
            "          pg_inherits i ON parent.oid = i.inhparent  " +
            "              JOIN  " +
            "          pg_class child ON i.inhrelid = child.oid  " +
            "              JOIN  " +
            "          pg_constraint con ON child.oid = con.conrelid  " +
            "),  " +
            "  " +
            "partition_columns AS (  " +
            "    SELECT  " +
            "        a.attrelid AS partitioned_table_oid,  " +
            "        a.attname AS partition_column  " +
            "    FROM  " +
            "        pg_attribute a  " +
            "            JOIN pg_partitioned_table pt ON a.attrelid = pt.partrelid  " +
            "    WHERE a.attnum = ANY(pt.partattrs)  " +
            "),  " +
            "  " +
            "partitions AS (  " +
            "    SELECT  " +
            "        pt.partrelid AS partitioned_table_oid,  " +
            "        c.relname AS partition_table,  " +
            "        pt.partstrat AS partition_strategy  " +
            "    FROM  " +
            "        pg_partitioned_table pt  " +
            "            JOIN pg_class c ON pt.partrelid = c.oid  " +
            "),  " +
            "  " +
            "partition_bounds AS (  " +
            "    SELECT  " +
            "        pt.partstrat AS partition_strategy,  " +
            "        inhrelid AS table_oid,  " +
            "        c.relname AS child_table,  " +
            "        pg_get_expr(c.relpartbound, c.oid) AS partition_bound  " +
            "    FROM  " +
            "        pg_inherits i  " +
            "            JOIN pg_class c ON i.inhrelid = c.oid  " +
            "            LEFT JOIN pg_partitioned_table pt ON i.inhparent = pt.partrelid  " +
            ")  " +
            "  " +
            "SELECT  " +
            "    a.table_name AS " + TableType.KEY_TABLE_NAME + ",  " +
            "    a.table_type AS " + TableType.KEY_TABLE_TYPE + " ,  " +
            "    CASE  " +
            "        WHEN a.table_type = '" + TableType.PARENT_TABLE + "' THEN '"+ TableType.INHERIT + "'  " +
            "        ELSE COALESCE(  " +
            "                CASE  " +
            "                    WHEN p.partition_strategy = 'r' OR pb.partition_strategy = 'r' THEN '"+ TableType.RANGE + "'  " +
            "                    WHEN p.partition_strategy = 'l' OR pb.partition_strategy = 'l' THEN '"+ TableType.LIST + "'  " +
            "                    WHEN p.partition_strategy = 'h' OR pb.partition_strategy = 'h' THEN '"+ TableType.HASH + "'  " +
            "                    WHEN a.table_type = '" + TableType.CHILD_TABLE + "' THEN '"+ TableType.INHERIT + "'  " +
            "                    ELSE '"+ TableType.UN_KNOW + "'  " +
            "                    END,  " +
            "                '')  " +
            "        END AS " + TableType.KEY_PARTITION_TYPE + ",  " +
            "    CASE  " +
            "        WHEN a.table_type = 'Partitioned Table' THEN pg_get_partkeydef(concat(a.schema_name, '.', a.table_name)::REGCLASS)  " +
            "        WHEN i.parent_table IS NOT NULL THEN  " +
            "            COALESCE(pg_get_partkeydef(concat(a.schema_name, '.', i.parent_table)::REGCLASS), COALESCE(ic.constraint_definition, ''))  " +
            "        ELSE '' END AS " + TableType.KEY_CHECK_OR_PARTITION_RULE + ",  " +
            "    COALESCE(pb.partition_bound, '') AS " + TableType.KEY_PARTITION_BOUND + ",  " +
            "    COALESCE(i.parent_table, '') AS " + TableType.KEY_PARENT_TABLE + ",  " +
            "    COALESCE(p.partition_table, '') AS " + TableType.KEY_PARTITION_TABLE + "  " +
            "FROM  " +
            "    all_tables a  " +
            "        LEFT JOIN inherits i ON a.table_name = i.child_table  " +
            "        LEFT JOIN partitions p ON a.table_oid = p.partitioned_table_oid  " +
            "        LEFT JOIN inherits_check ic ON a.table_name = ic.partition_table  " +
            "        LEFT JOIN partition_bounds pb ON a.table_oid = pb.table_oid  " +
            "WHERE a.table_type <> '" + TableType.REGULAR_TABLE + "'  " +
            "ORDER BY  " +
            "    a.schema_name,  " +
            "    a.table_type DESC ";
}
