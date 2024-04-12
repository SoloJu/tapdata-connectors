package io.tapdata.oceanbase.cdc;

import com.oceanbase.clogproxy.client.LogProxyClient;
import com.oceanbase.clogproxy.client.config.ObReaderConfig;
import com.oceanbase.clogproxy.client.exception.LogProxyClientException;
import com.oceanbase.clogproxy.client.listener.RecordListener;
import com.oceanbase.oms.logmessage.DataMessage;
import com.oceanbase.oms.logmessage.LogMessage;
import io.netty.util.BooleanSupplier;
import io.tapdata.entity.event.TapEvent;
import io.tapdata.entity.event.control.HeartbeatEvent;
import io.tapdata.entity.event.dml.TapDeleteRecordEvent;
import io.tapdata.entity.event.dml.TapInsertRecordEvent;
import io.tapdata.entity.event.dml.TapUpdateRecordEvent;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.entity.utils.cache.KVReadOnlyMap;
import io.tapdata.kit.EmptyKit;
import io.tapdata.oceanbase.bean.OceanbaseConfig;
import io.tapdata.pdk.apis.consumer.StreamReadConsumer;
import io.tapdata.util.DateUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class OceanbaseReader {

    private OceanbaseConfig oceanbaseConfig;
    private KVReadOnlyMap<TapTable> tableMap;
    private List<String> tableList;
    private StreamReadConsumer consumer;
    private Object offsetState;
    private int recordSize;
    private final Map<String, String> dataFormatMap = new HashMap<>();

    public OceanbaseReader(OceanbaseConfig oceanbaseConfig) {
        this.oceanbaseConfig = oceanbaseConfig;
    }

    public void init(List<String> tableList, KVReadOnlyMap<TapTable> tableMap, Object offsetState, int recordSize, StreamReadConsumer consumer) {
        this.tableList = tableList;
        this.tableMap = tableMap;
        this.offsetState = offsetState;
        this.recordSize = recordSize;
        this.consumer = consumer;
    }

    public void start(BooleanSupplier isAlive) throws Throwable {
        ObReaderConfig config = new ObReaderConfig();
        config.setRsList(oceanbaseConfig.getHost() + ":" + oceanbaseConfig.getRpcPort() + ":" + oceanbaseConfig.getPort());
        config.setUsername(oceanbaseConfig.getUser());
        config.setPassword(oceanbaseConfig.getPassword());
        config.setStartTimestamp((Long) offsetState);
        config.setTableWhiteList(tableList.stream().map(table -> oceanbaseConfig.getTenant() + "." + oceanbaseConfig.getDatabase() + "." + table).collect(Collectors.joining("|")));
        LogProxyClient client = new LogProxyClient(oceanbaseConfig.getHost(), oceanbaseConfig.getLogProxyPort(), config);
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        AtomicReference<List<TapEvent>> eventList = new AtomicReference<>(new ArrayList<>());
        AtomicInteger heartbeatCount = new AtomicInteger(0);
        client.addListener(new RecordListener() {
            @Override
            public void notify(LogMessage message) {
                try {
                    Map<String, Object> after = DataMap.create();
                    Map<String, Object> before = DataMap.create();
                    analyzeMessage(message, after, before);
                    switch (message.getOpt().name()) {
                        case "INSERT":
                            eventList.get().add(new TapInsertRecordEvent().init().table(message.getTableName()).after(after).referenceTime(Long.parseLong(message.getTimestamp()) * 1000));
                            break;
                        case "UPDATE":
                            eventList.get().add(new TapUpdateRecordEvent().init().table(message.getTableName()).after(after).before(before).referenceTime(Long.parseLong(message.getTimestamp()) * 1000));
                            break;
                        case "DELETE":
                            eventList.get().add(new TapDeleteRecordEvent().init().table(message.getTableName()).before(before).referenceTime(Long.parseLong(message.getTimestamp()) * 1000));
                            break;
                        case "HEARTBEAT":
                            if (heartbeatCount.incrementAndGet() >= 10) {
                                eventList.get().add(new HeartbeatEvent().init().referenceTime(Long.parseLong(message.getTimestamp()) * 1000));
                                heartbeatCount.set(0);
                                consumer.accept(eventList.get(), Long.valueOf(message.getTimestamp()));
                                eventList.set(new ArrayList<>());
                            }
                            break;
                        default:
                            break;
                    }
                    if (eventList.get().size() >= recordSize) {
                        consumer.accept(eventList.get(), Long.valueOf(message.getTimestamp()));
                        eventList.set(new ArrayList<>());
                    }
                } catch (Exception e) {
                    throwable.set(e);
                }
            }

            @Override
            public void onException(LogProxyClientException e) {
                if (e.needStop()) {
                    client.stop();
                }
            }
        });
        client.start();
        consumer.streamReadStarted();
        client.join();
        consumer.streamReadEnded();
        if (EmptyKit.isNotNull(throwable.get())) {
            throw throwable.get();
        }
        if (isAlive.get()) {
            throw new RuntimeException("Exception occurs in OceanBase Log Miner service");
        }
    }

    private void analyzeMessage(LogMessage message, Map<String, Object> after, Map<String, Object> before) {
        String table = message.getTableName();
        switch (message.getOpt().name()) {
            case "INSERT":
                message.getFieldList().forEach(k -> after.put(k.getFieldname(), parseField(table, k)));
                break;
            case "UPDATE": {
                int index = 0;
                for (DataMessage.Record.Field field : message.getFieldList()) {
                    if (index % 2 == 0) {
                        before.put(field.getFieldname(), parseField(table, field));
                    } else {
                        after.put(field.getFieldname(), parseField(table, field));
                    }
                    index++;
                }
                break;
            }
            case "DELETE":
                message.getFieldList().forEach(k -> before.put(k.getFieldname(), parseField(table, k)));
                break;
            default:
                break;
        }
    }

    private Object parseField(String table, DataMessage.Record.Field field) {
        if (EmptyKit.isNull(field.getValue())) {
            return null;
        }
        switch (field.getType().name()) {
            case "INT8":
            case "INT16":
            case "INT24":
            case "INT32":
            case "INT64":
            case "DECIMAL":
            case "DOUBLE":
            case "FLOAT":
                return new BigDecimal(field.getValue().toString());
            case "DATETIME":
            case "DATE":
            case "TIMESTAMP":
                String dataFormat = dataFormatMap.get(table + "." + field.getFieldname());
                if (EmptyKit.isNull(dataFormat)) {
                    dataFormat = DateUtil.determineDateFormat(field.getValue().toString());
                    dataFormatMap.put(table + "." + field.getFieldname(), dataFormat);
                }
                return DateUtil.parseInstant(field.getValue().toString(), dataFormat);
            case "YEAR":
                return Integer.parseInt(field.getValue().toString());
            case "BIT":
                if (field.getValue().getLen() > 1) {
                    return field.getValue().getBytes();
                } else {
                    return "1".equals(field.getValue().toString());
                }
            case "BLOB":
            case "BINARY":
                return field.getValue().getBytes();
            default:
                return field.getValue().toString();
        }
    }
}
