package io.tapdata.connector.tidb;

import io.tapdata.common.CommonDbConnector;
import io.tapdata.common.CommonSqlMaker;
import io.tapdata.common.SqlExecuteCommandFunction;
import io.tapdata.connector.mysql.bean.MysqlColumn;
import io.tapdata.connector.mysql.entity.MysqlBinlogPosition;
import io.tapdata.connector.tidb.cdc.process.thread.ProcessHandler;
import io.tapdata.connector.tidb.cdc.process.thread.TiCDCShellManager;
import io.tapdata.connector.tidb.cdc.util.ProcessLauncher;
import io.tapdata.connector.tidb.cdc.util.ProcessSearch;
import io.tapdata.connector.tidb.config.TidbConfig;
import io.tapdata.connector.tidb.ddl.TidbDDLSqlGenerator;
import io.tapdata.connector.tidb.dml.TidbReader;
import io.tapdata.connector.tidb.dml.TidbRecordWriter;
import io.tapdata.connector.tidb.util.HttpUtil;
import io.tapdata.entity.codec.TapCodecsRegistry;
import io.tapdata.entity.event.ddl.table.TapAlterFieldAttributesEvent;
import io.tapdata.entity.event.ddl.table.TapAlterFieldNameEvent;
import io.tapdata.entity.event.ddl.table.TapDropFieldEvent;
import io.tapdata.entity.event.ddl.table.TapNewFieldEvent;
import io.tapdata.entity.event.dml.TapRecordEvent;
import io.tapdata.entity.logger.Log;
import io.tapdata.entity.schema.TapField;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.entity.schema.value.TapArrayValue;
import io.tapdata.entity.schema.value.TapBooleanValue;
import io.tapdata.entity.schema.value.TapDateTimeValue;
import io.tapdata.entity.schema.value.TapDateValue;
import io.tapdata.entity.schema.value.TapMapValue;
import io.tapdata.entity.schema.value.TapTimeValue;
import io.tapdata.entity.schema.value.TapValue;
import io.tapdata.entity.schema.value.TapYearValue;
import io.tapdata.entity.simplify.pretty.BiClassHandlers;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.kit.EmptyKit;
import io.tapdata.kit.ErrorKit;
import io.tapdata.pdk.apis.annotations.TapConnectorClass;
import io.tapdata.pdk.apis.consumer.StreamReadConsumer;
import io.tapdata.pdk.apis.context.TapConnectionContext;
import io.tapdata.pdk.apis.context.TapConnectorContext;
import io.tapdata.pdk.apis.entity.ConnectionOptions;
import io.tapdata.pdk.apis.entity.FilterResults;
import io.tapdata.pdk.apis.entity.TapAdvanceFilter;
import io.tapdata.pdk.apis.entity.TestItem;
import io.tapdata.pdk.apis.entity.WriteListResult;
import io.tapdata.pdk.apis.functions.ConnectorFunctions;
import io.tapdata.pdk.apis.functions.connection.TableInfo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;


@TapConnectorClass("spec_tidb.json")
public class TidbConnector extends CommonDbConnector {
    private static final String TAG = TidbConnector.class.getSimpleName();
    private TidbConfig tidbConfig;
    private TidbJdbcContext tidbJdbcContext;
    private TimeZone timezone;
    private TidbReader tidbReader;
    AtomicReference<Throwable> throwableCatch = new AtomicReference<>();
    String filePath;

    protected final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public void onStart(TapConnectionContext tapConnectionContext) throws Throwable {
        this.tidbConfig = new TidbConfig().load(tapConnectionContext.getConnectionConfig());
        tidbJdbcContext = new TidbJdbcContext(tidbConfig);
        commonDbConfig = tidbConfig;
        jdbcContext = tidbJdbcContext;
        if (EmptyKit.isBlank(tidbConfig.getTimezone())) {
            timezone = tidbJdbcContext.queryTimeZone();
        }
        tapLogger = tapConnectionContext.getLog();
        started.set(true);

        commonSqlMaker = new CommonSqlMaker('`');
        tidbReader = new TidbReader(tidbJdbcContext);
        ddlSqlGenerator = new TidbDDLSqlGenerator();
        fieldDDLHandlers = new BiClassHandlers<>();
        fieldDDLHandlers.register(TapNewFieldEvent.class, this::newField);
        fieldDDLHandlers.register(TapAlterFieldAttributesEvent.class, this::alterFieldAttr);
        fieldDDLHandlers.register(TapAlterFieldNameEvent.class, this::alterFieldName);
        fieldDDLHandlers.register(TapDropFieldEvent.class, this::dropField);
    }

    @Override
    public void registerCapabilities(ConnectorFunctions connectorFunctions, TapCodecsRegistry codecRegistry) {
        connectorFunctions.supportErrorHandleFunction(this::errorHandle);
        connectorFunctions.supportReleaseExternalFunction(this::onDestroy);
        // target functions
        connectorFunctions.supportCreateTableV2(this::createTableV3);
        connectorFunctions.supportClearTable(this::clearTable);
        connectorFunctions.supportDropTable(this::dropTable);
        connectorFunctions.supportCreateIndex(this::createIndex);
        connectorFunctions.supportGetTableNamesFunction(this::getTableNames);
        connectorFunctions.supportWriteRecord(this::writeRecord);
        connectorFunctions.supportQueryByAdvanceFilter(this::queryByAdvanceFilter);
        connectorFunctions.supportCountByPartitionFilterFunction(this::countByAdvanceFilter);

        connectorFunctions.supportNewFieldFunction(this::fieldDDLHandler);
        connectorFunctions.supportAlterFieldNameFunction(this::fieldDDLHandler);
        connectorFunctions.supportAlterFieldAttributesFunction(this::fieldDDLHandler);
        connectorFunctions.supportDropFieldFunction(this::fieldDDLHandler);
        connectorFunctions.supportExecuteCommandFunction((a, b, c) -> SqlExecuteCommandFunction.executeCommand(a, b, () -> tidbJdbcContext.getConnection(), c));

        // source functions
        connectorFunctions.supportBatchCount(this::batchCount);
        connectorFunctions.supportBatchRead(this::batchReadV3);
        connectorFunctions.supportStreamRead(this::streamRead);
        connectorFunctions.supportTimestampToStreamOffset(this::timestampToStreamOffset);
        connectorFunctions.supportGetTableInfoFunction(this::getTableInfo);
        connectorFunctions.supportRunRawCommandFunction(this::runRawCommand);

        codecRegistry.registerFromTapValue(TapDateTimeValue.class, tapDateTimeValue -> {
            if (tapDateTimeValue.getValue() != null && tapDateTimeValue.getValue().getTimeZone() == null) {
                tapDateTimeValue.getValue().setTimeZone(timezone);
            }
            return formatTapDateTime(tapDateTimeValue.getValue(), "yyyy-MM-dd HH:mm:ss.SSSSSS");
        });
        codecRegistry.registerFromTapValue(TapDateValue.class, tapDateValue -> {
            if (tapDateValue.getValue() != null && tapDateValue.getValue().getTimeZone() == null) {
                tapDateValue.getValue().setTimeZone(timezone);
            }
            return formatTapDateTime(tapDateValue.getValue(), "yyyy-MM-dd");
        });
        codecRegistry.registerFromTapValue(TapTimeValue.class, tapTimeValue -> tapTimeValue.getValue().toTimeStr());
        codecRegistry.registerFromTapValue(TapYearValue.class, tapYearValue -> {
            if (tapYearValue.getValue() != null && tapYearValue.getValue().getTimeZone() == null) {
                tapYearValue.getValue().setTimeZone(timezone);
            }
            return formatTapDateTime(tapYearValue.getValue(), "yyyy");
        });
        codecRegistry.registerFromTapValue(TapBooleanValue.class, "tinyint(1)", TapValue::getValue);

        codecRegistry.registerFromTapValue(TapMapValue.class, "longtext", tapMapValue -> {
            if (tapMapValue != null && tapMapValue.getValue() != null) return toJson(tapMapValue.getValue());
            return "null";
        });
        codecRegistry.registerFromTapValue(TapArrayValue.class, "longtext", tapValue -> {
            if (tapValue != null && tapValue.getValue() != null) return toJson(tapValue.getValue());
            return "null";
        });

    }

    private void streamRead(TapConnectorContext nodeContext, List<String> tableList, Object offsetState, int recordSize, StreamReadConsumer consumer) throws Throwable {
        String feedId = (String) nodeContext.getStateMap().get("feed-id");
        if (null == feedId) {
            feedId = UUID.randomUUID().toString().replaceAll("-", "");
            nodeContext.getStateMap().put("feed-id", feedId);
        }
        String cdcServer = String.valueOf(Optional.ofNullable(nodeContext.getStateMap().get("cdc-server")).orElse("127.0.0.1:8300"));
        ProcessHandler.ProcessInfo info = new ProcessHandler.ProcessInfo()
                .withCdcServer(cdcServer)
                .withFeedId(feedId)
                .withAlive(this::isAlive)
                .withTapConnectorContext(nodeContext)
                .withCdcTable(tableList)
                .withThrowableCollector(throwableCatch)
                .withCdcOffset(offsetState)
                .withTiDBConfig(tidbConfig)
                .withGcTtl(86400)
                .withDatabase(tidbConfig.getDatabase());
        try (ProcessHandler handler = new ProcessHandler(info, consumer)) {
                handler.doActivity();
                while (isAlive()) {
                    if (null != throwableCatch.get()) {
                        throw throwableCatch.get();
                    }
                    handler.aliveCheck();
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
        }
    }

    private Object timestampToStreamOffset(TapConnectorContext connectorContext, Long offsetStartTime) throws Throwable {
        if (null == offsetStartTime) {
            MysqlBinlogPosition mysqlBinlogPosition = tidbJdbcContext.readBinlogPosition();
            return mysqlBinlogPosition.getPosition();
        }
        return offsetStartTime;
    }

    @Override
    public void onStop(TapConnectionContext connectionContext) throws Exception {
        started.set(false);
        EmptyKit.closeQuietly(tidbJdbcContext);
    }

    private void onDestroy(TapConnectorContext connectorContext) throws Throwable {
        Log log = connectorContext.getLog();
        String feedId = (String) connectorContext.getStateMap().get("feed-id");
        String cdcServer = (String) connectorContext.getStateMap().get("cdc-server");
        String filePath = (String) connectorContext.getStateMap().get("cdc-file-path");
        if (EmptyKit.isNotNull(feedId)) {
            try (HttpUtil httpUtil = new HttpUtil(connectorContext.getLog())) {
                log.debug("Start to delete change feed: {}", feedId);
                ErrorKit.ignoreAnyError(() -> httpUtil.deleteChangeFeed(feedId, cdcServer));
                log.debug("Start to clean cdc data dir: {}", filePath);
                ErrorKit.ignoreAnyError(() -> FileUtils.deleteDirectory(new File(filePath)));
                log.debug("Start to check cdc server heath: {}", cdcServer);
                ErrorKit.ignoreAnyError(() -> {
                    if (!httpUtil.checkAlive(cdcServer)) {
                        return;
                    }
                    log.debug("Cdc server is alive, will check change feed list");
                    if (httpUtil.queryChangeFeedsList(cdcServer) <= 0) {
                        log.debug("There is not any change feed with cdc server: {}, will stop cdc server", cdcServer);
                        String killCmd = "kill -9 ${pid}";
                        TiCDCShellManager.ShellConfig config = new TiCDCShellManager.ShellConfig();
                        config.withPdIpPorts(connectorContext.getConnectionConfig().getString("pdServer"));
                        String pdServer = config.getPdIpPorts();
                        List<String> processes = ProcessSearch.getProcesses(log, TiCDCShellManager.getCdcPsGrepFilter(pdServer, cdcServer));
                        if (!processes.isEmpty()) {
                            StringJoiner joiner = new StringJoiner(" ");
                            processes.forEach(joiner::add);
                            ProcessLauncher.execCmdWaitResult(
                                    TiCDCShellManager.setProperties(killCmd, "pid", joiner.toString()),
                                    "stop cdc server failed, message: {}",
                                    log);
                        }
                    }
                });
            } catch (Exception e) {
                connectorContext.getLog().warn("Clean cdc resources failed, message: {}", e.getMessage(), e);
            }
        }
    }

    private void writeRecord(TapConnectorContext tapConnectorContext, List<TapRecordEvent> tapRecordEvents, TapTable tapTable, Consumer<WriteListResult<TapRecordEvent>> consumer) throws Throwable {
        String insertDmlPolicy = tapConnectorContext.getConnectorCapabilities().getCapabilityAlternative(ConnectionOptions.DML_INSERT_POLICY);
        if (insertDmlPolicy == null) {
            insertDmlPolicy = ConnectionOptions.DML_INSERT_POLICY_UPDATE_ON_EXISTS;
        }
        String updateDmlPolicy = tapConnectorContext.getConnectorCapabilities().getCapabilityAlternative(ConnectionOptions.DML_UPDATE_POLICY);
        if (updateDmlPolicy == null) {
            updateDmlPolicy = ConnectionOptions.DML_UPDATE_POLICY_IGNORE_ON_NON_EXISTS;
        }
        new TidbRecordWriter(tidbJdbcContext, tapTable)
                .setInsertPolicy(insertDmlPolicy)
                .setUpdatePolicy(updateDmlPolicy)
                .write(tapRecordEvents, consumer, this::isAlive);
    }

    @Override
    public ConnectionOptions connectionTest(TapConnectionContext databaseContext, Consumer<TestItem> consumer) {
        tidbConfig = new TidbConfig().load(databaseContext.getConnectionConfig());
        ConnectionOptions connectionOptions = ConnectionOptions.create();
        connectionOptions.connectionString(tidbConfig.getConnectionString());
        try (
                TidbConnectionTest connectionTest = new TidbConnectionTest(tidbConfig, consumer, connectionOptions)
        ) {
            connectionTest.testOneByOne();
        }
        return connectionOptions;
    }

    private void queryByAdvanceFilter(TapConnectorContext tapConnectorContext, TapAdvanceFilter tapAdvanceFilter, TapTable tapTable, Consumer<FilterResults> consumer) {
        FilterResults filterResults = new FilterResults();
        filterResults.setFilter(tapAdvanceFilter);
        try {
            tidbReader.readWithFilter(tapConnectorContext, tapTable, tapAdvanceFilter, n -> !isAlive(), data -> {
                filterResults.add(data);
                if (filterResults.getResults().size() == BATCH_ADVANCE_READ_LIMIT) {
                    consumer.accept(filterResults);
                    filterResults.getResults().clear();
                }
            });
            if (CollectionUtils.isNotEmpty(filterResults.getResults())) {
                consumer.accept(filterResults);
                filterResults.getResults().clear();
            }
        } catch (Throwable e) {
            filterResults.setError(e);
            consumer.accept(filterResults);
        }
    }

    protected TapField makeTapField(DataMap dataMap) {
        return new MysqlColumn(dataMap).getTapField();
    }

    private TableInfo getTableInfo(TapConnectionContext tapConnectorContext, String tableName) throws Throwable {
        DataMap dataMap = tidbJdbcContext.getTableInfo(tableName);
        TableInfo tableInfo = TableInfo.create();
        tableInfo.setNumOfRows(Long.valueOf(dataMap.getString("TABLE_ROWS")));
        tableInfo.setStorageSize(Long.valueOf(dataMap.getString("DATA_LENGTH")));
        return tableInfo;
    }
}

