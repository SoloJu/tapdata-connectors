package io.tapdata.connector.hudi.write;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.tapdata.connector.hive.HiveJdbcContext;
import io.tapdata.connector.hudi.config.HudiConfig;
import io.tapdata.connector.hudi.util.FileUtil;
import io.tapdata.connector.hudi.util.Krb5Util;
import io.tapdata.connector.hudi.write.generic.GenericDeleteRecord;
import io.tapdata.connector.hudi.write.generic.HoodieRecordGenericStage;
import io.tapdata.connector.hudi.write.generic.entity.NormalEntity;
import io.tapdata.entity.event.dml.TapDeleteRecordEvent;
import io.tapdata.entity.event.dml.TapInsertRecordEvent;
import io.tapdata.entity.event.dml.TapRecordEvent;
import io.tapdata.entity.event.dml.TapUpdateRecordEvent;
import io.tapdata.entity.logger.Log;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.kit.ErrorKit;
import io.tapdata.pdk.apis.context.TapConnectorContext;
import io.tapdata.pdk.apis.entity.WriteListResult;
import org.apache.hadoop.conf.Configuration;
import org.apache.hudi.client.HoodieJavaWriteClient;
import org.apache.hudi.client.WriteStatus;
import org.apache.hudi.common.model.*;
import org.apache.hudi.exception.HoodieRollbackException;


public class HuDiWriteBySparkClient extends HudiWrite {
    private final ScheduledExecutorService cleanService = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledFuture<?> cleanFuture;
    private static final String DESCRIBE_EXTENDED_SQL = "describe Extended `%s`.`%s`";
    Configuration hadoopConf;

    String insertDmlPolicy;
    String updateDmlPolicy;
    WriteOperationType appendType;
    Log log;

    /**
     * @apiNote Use function getClientEntity(TableId) to get ClientEntity
     * */
    private ConcurrentHashMap<String, ClientPerformer> tablePathMap;

    private ClientPerformer getClientEntity(TapTable tapTable) {
        final String tableId = tapTable.getId();
        ClientPerformer clientPerformer;
        synchronized (clientEntityLock) {
            if (tablePathMap.containsKey(tableId)
                    && null != tablePathMap.get(tableId)
                    && null != (clientPerformer = tablePathMap.get(tableId))) {
                clientPerformer.updateTimestamp();
                return clientPerformer;
            }
        }
        String tablePath = clientHandler.getTablePath(tableId);
        clientPerformer = new ClientPerformer(
            ClientPerformer.Param.witStart()
                    .withHadoopConf(hadoopConf)
                    .withDatabase(config.getDatabase())
                    .withTableId(tableId)
                    .withTablePath(tablePath)
                    .withTapTable(tapTable)
                    .withOperationType(appendType)
                    .withConfig(config)
                    .withLog(log));
        synchronized (clientEntityLock) {
            tablePathMap.put(tableId, clientPerformer);
        }
        return clientPerformer;
    }

    HudiConfig config;
    ClientHandler clientHandler;
    final Object clientEntityLock = new Object();
    public HuDiWriteBySparkClient(HiveJdbcContext hiveJdbcContext, HudiConfig hudiConfig) {
        super(hiveJdbcContext, hudiConfig);
        this.config = hudiConfig;
        init();
        this.cleanFuture = this.cleanService.scheduleWithFixedDelay(() -> {
            ConcurrentHashMap.KeySetView<String, ClientPerformer> tableIds = tablePathMap.keySet();
            long timestamp = new Date().getTime();
            for (String tableId : tableIds) {
                if (!isAlive()) {
                    break;
                }
                ClientPerformer clientPerformer = tablePathMap.get(tableId);
                if (null == clientPerformer || timestamp - ClientPerformer.CACHE_TIME >= clientPerformer.getTimestamp()) {
                    synchronized (clientEntityLock) {
                        if (null != clientPerformer) {
                            clientPerformer.doClose();
                        }
                        tablePathMap.remove(tableId);
                    }
                }
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    public HuDiWriteBySparkClient log(Log log) {
        this.log = log;
        return this;
    }

    protected void init() {
        tablePathMap = new ConcurrentHashMap<>();
        this.clientHandler = new ClientHandler(config, hiveJdbcContext);
        String confPath = FileUtil.paths(config.getKrb5Path(), Krb5Util.KRB5_NAME);
        String krb5Path = confPath.replaceAll("\\\\","/");
        System.setProperty("HADOOP_USER_NAME","tapdata_test");
        System.setProperty("KERBEROS_USER_KEYTAB", krb5Path);
        this.hadoopConf = this.clientHandler.getHadoopConf();
    }

    private void cleanTableClientMap() {
        tablePathMap.values().stream().filter(Objects::nonNull).forEach(ClientPerformer::doClose);
        tablePathMap.clear();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Optional.ofNullable(cleanFuture).ifPresent(e -> ErrorKit.ignoreAnyError(() -> e.cancel(true)));
        ErrorKit.ignoreAnyError(cleanService::shutdown);
        cleanTableClientMap();
    }

    private WriteListResult<TapRecordEvent> afterCommit(final AtomicLong insert,  final AtomicLong update,  final AtomicLong delete, Consumer<WriteListResult<TapRecordEvent>> consumer) {
        WriteListResult<TapRecordEvent> writeListResult = new WriteListResult<>(0L, 0L, 0L, new HashMap<>());
        writeListResult.incrementInserted(insert.get());
        writeListResult.incrementModified(update.get());
        writeListResult.incrementRemove(delete.get());
        consumer.accept(writeListResult);
        delete.set(0);
        update.set(0);
        insert.set(0);
        return writeListResult;
    }

    private WriteListResult<TapRecordEvent> groupRecordsByEventType(TapTable tapTable, List<TapRecordEvent> events, Consumer<WriteListResult<TapRecordEvent>> consumer) {
        WriteListResult<TapRecordEvent> writeListResult = new WriteListResult<>(0L, 0L, 0L, new HashMap<>());
        TapRecordEvent errorRecord = null;
        List<HoodieRecord<HoodieRecordPayload>> recordsOneBatch = new ArrayList<>();
        List<HoodieKey> deleteEventsKeys = new ArrayList<>();
        TapRecordEvent batchFirstRecord = null;
        int tag = -1;
        AtomicLong insert = new AtomicLong(0);
        AtomicLong update = new AtomicLong(0);
        AtomicLong delete = new AtomicLong(0);
        final ClientPerformer clientPerformer = getClientEntity(tapTable);
        final NormalEntity entity = new NormalEntity().withClientEntity(clientPerformer).withTapTable(tapTable);
        try {
            for (TapRecordEvent e : events) {
                if (!isAlive()) break;
                if (-1 == tag) {
                    batchFirstRecord = e;
                }
                HoodieRecord<HoodieRecordPayload> hoodieRecord = null;
                int tempTag = tag;
                try {
                    if (e instanceof TapInsertRecordEvent) {
                        tag = 1;
                        insert.incrementAndGet();
                        TapInsertRecordEvent insertRecord = (TapInsertRecordEvent) e;
                        hoodieRecord = HoodieRecordGenericStage.singleton().generic(insertRecord.getAfter(), entity);
                    } else if (e instanceof TapUpdateRecordEvent) {
                        tag = 1;
                        update.incrementAndGet();
                        TapUpdateRecordEvent updateRecord = (TapUpdateRecordEvent) e;
                        hoodieRecord = HoodieRecordGenericStage.singleton().generic(updateRecord.getAfter(), entity);
                    } else if (e instanceof TapDeleteRecordEvent) {
                        tag = 3;
                        delete.incrementAndGet();
                        TapDeleteRecordEvent deleteRecord = (TapDeleteRecordEvent) e;
                        deleteEventsKeys.add(GenericDeleteRecord.singleton().generic(deleteRecord.getBefore(), entity));
                    }

                    if ((-1 != tempTag && tempTag != tag)) {
                        commitButch(clientPerformer, tempTag, recordsOneBatch, deleteEventsKeys);
                        batchFirstRecord = e;
                        afterCommit(insert, update, delete, consumer);
                    }
                    if (tag > 0 && null != hoodieRecord) {
                        recordsOneBatch.add(hoodieRecord);
                    }
                } catch (Exception fail) {
                    log.error("target database process message failed", "table name:{}, record: {}, error msg:{}", tapTable.getId(), e, fail.getMessage(), fail);
                    errorRecord = batchFirstRecord;
                    throw fail;
                }
            }
        } catch (Throwable e) {
            if (null != errorRecord) writeListResult.addError(errorRecord, e);
            throw e;
        } finally {
            if (!recordsOneBatch.isEmpty()) {
                try {
                    commitButch(clientPerformer, 1, recordsOneBatch, deleteEventsKeys);
                    afterCommit(insert, update, delete, consumer);
                } catch (Exception fail) {
                    log.error("target database process message failed", "table name:{},error msg:{}", tapTable.getId(), fail.getMessage(), fail);
                    errorRecord = batchFirstRecord;
                    throw fail;
                }
            }
            if (!deleteEventsKeys.isEmpty()) {
                try {
                    commitButch(clientPerformer, 3, recordsOneBatch, deleteEventsKeys);
                    afterCommit(insert, update, delete, consumer);
                } catch (Exception fail) {
                    log.error("target database process message failed", "table name:{},error msg:{}", tapTable.getId(), fail.getMessage(), fail);
                    errorRecord = batchFirstRecord;
                    throw fail;
                }
            }
        }
        return writeListResult;
    }

    private void commitButch(ClientPerformer clientPerformer, int batchType, List<HoodieRecord<HoodieRecordPayload>> batch, List<HoodieKey> deleteEventsKeys) {
        if (batchType != 1 && batchType != 2 && batchType != 3) return;
       //long s = System.currentTimeMillis();
        HoodieJavaWriteClient<HoodieRecordPayload> client = clientPerformer.getClient();
        String startCommit;
        client.setOperationType(appendType);
        startCommit = startCommitAndGetCommitTime(clientPerformer);
        ErrorKit.ignoreAnyError(()->Thread.sleep(500));

        try {
            switch (batchType) {
                case 1:
                case 2:
                    List<WriteStatus> insert;
                    if (WriteOperationType.INSERT.equals(this.appendType)) {
                        insert = client.insert(batch, startCommit);
                    } else {
                        insert = client.upsert(batch, startCommit);
                    }
                    client.commit(startCommit, insert);
                    batch.clear();
                    break;
                case 3:
                    if (!WriteOperationType.INSERT.equals(this.appendType)) {
                        List<WriteStatus> delete = client.delete(deleteEventsKeys, startCommit);
                        client.commit(startCommit, delete);
                        deleteEventsKeys.clear();
                    } else {
                        log.debug("Append mode: INSERT, ignore delete event: {}", deleteEventsKeys);
                    }
                    break;
            }
        } catch (HoodieRollbackException e) {
            client.rollback(startCommit);
            throw e;
        }
        //System.out.println("[TAP_QPS] one commit cost: " + (System.currentTimeMillis() - s));
    }

    public WriteListResult<TapRecordEvent> writeByClient(TapConnectorContext tapConnectorContext, TapTable tapTable, List<TapRecordEvent> tapRecordEvents, Consumer<WriteListResult<TapRecordEvent>> consumer) throws Throwable {
        this.insertDmlPolicy = dmlInsertPolicy(tapConnectorContext);
        this.updateDmlPolicy = dmlUpdatePolicy(tapConnectorContext);
        this.appendType = appendType(tapConnectorContext, tapTable);
        return groupRecordsByEventType(tapTable, tapRecordEvents, consumer);
    }

    public WriteListResult<TapRecordEvent> writeRecord(TapConnectorContext tapConnectorContext, TapTable tapTable, List<TapRecordEvent> tapRecordEvents, Consumer<WriteListResult<TapRecordEvent>> consumer) throws Throwable {
        return this.writeByClient(tapConnectorContext, tapTable, tapRecordEvents, consumer);
    }

    @Override
    public WriteListResult<TapRecordEvent> writeJdbcRecord(TapConnectorContext tapConnectorContext, TapTable tapTable, List<TapRecordEvent> tapRecordEvents) throws Throwable {
        throw new UnsupportedOperationException("UnSupport JDBC operator");
    }

    public WriteListResult<TapRecordEvent> notExistsInsert(TapConnectorContext tapConnectorContext, TapTable tapTable, List<TapRecordEvent> tapRecordEventList) throws Throwable {
        throw new UnsupportedOperationException("UnSupport JDBC operator");
    }

    @Override
    public WriteListResult<TapRecordEvent> writeOne(TapConnectorContext tapConnectorContext, TapTable tapTable, List<TapRecordEvent> tapRecordEvents) throws Throwable {
        throw new UnsupportedOperationException("UnSupport JDBC operator");
    }

    @Override
    protected int doInsertOne(TapConnectorContext tapConnectorContext, TapTable tapTable, TapRecordEvent tapRecordEvent) throws Throwable {
        throw new UnsupportedOperationException("UnSupport JDBC operator");
    }

    @Override
    protected int doUpdateOne(TapConnectorContext tapConnectorContext, TapTable tapTable, TapRecordEvent tapRecordEvent) throws Throwable {
        throw new UnsupportedOperationException("UnSupport JDBC operator");
    }

    private String startCommitAndGetCommitTime(ClientPerformer clientPerformer) {
        return clientPerformer.getClient().startCommit();
    }

    /**
     * @deprecated engine not support send writeStrategy to connector
     * hudi only support updateOrInsert or appendWrite now,
     * the stage which name is Append-Only supported by table has not any one primary keys
     * */
    protected WriteOperationType appendType(TapConnectorContext tapConnectorContext, TapTable tapTable) {
        Collection<String> primaryKeys = tapTable.primaryKeys(true);
        DataMap nodeConfig = tapConnectorContext.getNodeConfig();

        //无主键表，并且开启了noPkAutoInsert开关，做仅插入操作
//        if (null == primaryKeys || primaryKeys.isEmpty()) return WriteOperationType.INSERT;
        if ((null == primaryKeys || primaryKeys.isEmpty()) && null != nodeConfig) {
            Object noPkAutoInsert = nodeConfig.getObject("noPkAutoInsert");
            if (noPkAutoInsert instanceof Boolean && (Boolean)noPkAutoInsert) {
                return WriteOperationType.INSERT;
            }
        }

        DataMap configOptions = tapConnectorContext.getSpecification().getConfigOptions();
        String writeStrategy = String.valueOf(configOptions.get("writeStrategy"));
        switch (writeStrategy) {
            case "appendWrite": return WriteOperationType.INSERT;
            default: return WriteOperationType.UPSERT;
        }
    }
}
