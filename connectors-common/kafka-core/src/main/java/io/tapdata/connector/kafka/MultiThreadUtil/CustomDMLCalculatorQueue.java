package io.tapdata.connector.kafka.MultiThreadUtil;

import io.tapdata.common.constant.MqOp;
import io.tapdata.connector.kafka.util.ObjectUtils;
import io.tapdata.entity.event.dml.TapDeleteRecordEvent;
import io.tapdata.entity.event.dml.TapInsertRecordEvent;
import io.tapdata.entity.event.dml.TapRecordEvent;
import io.tapdata.entity.event.dml.TapUpdateRecordEvent;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.entity.utils.InstanceFactory;
import io.tapdata.entity.utils.JsonParser;
import io.tapdata.kit.EmptyKit;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import javax.script.Invocable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import static io.tapdata.common.constant.MqOp.*;
import static io.tapdata.connector.kafka.KafkaService.executeScript;

public class CustomDMLCalculatorQueue<P,V> extends ConcurrentCalculatorQueue<DMLRecordEventConvertDto, DMLRecordEventConvertDto>{
	private static final JsonParser jsonParser = InstanceFactory.instance(JsonParser.class);
	private Concurrents<Invocable> customDmlConcurrents;
	private ProduceCustomDmlRecordInfo produceCustomDmlRecordInfo;

	public CustomDMLCalculatorQueue(int threadSize, int queueSize, Concurrents<Invocable> customDmlConcurrents) {
		super(threadSize, queueSize);
		this.customDmlConcurrents=customDmlConcurrents;
	}

	public void setProduceInfo(ProduceCustomDmlRecordInfo produceCustomDmlRecordInfo) {
		this.produceCustomDmlRecordInfo = produceCustomDmlRecordInfo;
	}

	@Override
	protected void distributingData(DMLRecordEventConvertDto dmlRecordEventConvertDto) {
		if (null == dmlRecordEventConvertDto.getJsConvertResultMap()) {
			return;
		}
		TapRecordEvent event = dmlRecordEventConvertDto.getRecordEvent();
		Map<String, Object> jsConvertResultMap = dmlRecordEventConvertDto.getJsConvertResultMap();
		byte[] body = {};
		RecordHeaders recordHeaders = new RecordHeaders();
		if (null == jsConvertResultMap.get("data")) {
			throw new RuntimeException("data cannot be null");
		} else {
			Object jsConvertData = jsConvertResultMap.get("data");
			if (jsConvertData instanceof Map) {
				Map<String, Map<String, Object>> jsConvertDataMap = (Map<String, Map<String, Object>>) jsConvertResultMap.get("data");
				removeIfEmptyInMap(jsConvertDataMap, "before");
				removeIfEmptyInMap(jsConvertDataMap, "after");
				body = jsonParser.toJsonBytes(jsConvertDataMap, JsonParser.ToJsonFeature.WriteMapNullValue);
			} else {
				body = jsConvertData.toString().getBytes();
			}
		}
		String mqOp = MapUtils.getString(jsConvertResultMap, "op");
		if (jsConvertResultMap.containsKey("header")) {
			Object obj = jsConvertResultMap.get("header");
			if (obj instanceof Map) {
				Map<String, Object> head = (Map<String, Object>) jsConvertResultMap.get("header");
				for (String s : head.keySet()) {
					recordHeaders.add(s, head.get(s).toString().getBytes());
				}
			} else {
				throw new RuntimeException("header must be a collection type");
			}
		} else {
			recordHeaders.add("mqOp", mqOp.toString().getBytes());
		}
		MqOp finalMqOp = MqOp.fromValue(mqOp);

		Callback callback = (metadata, exception) -> {
			try {
				if (EmptyKit.isNotNull(exception)) {
					this.produceCustomDmlRecordInfo.getListResult().addError(event, exception);
				}
				switch (finalMqOp) {
					case INSERT:
						produceCustomDmlRecordInfo.getInsert().incrementAndGet();
						break;
					case UPDATE:
						produceCustomDmlRecordInfo.getUpdate().incrementAndGet();
						break;
					case DELETE:
						produceCustomDmlRecordInfo.getDelete().incrementAndGet();
						break;
				}
			} finally {
				produceCustomDmlRecordInfo.getCountDownLatch().countDown();
			}
		};
		ProducerRecord<byte[], byte[]> producerRecord = new ProducerRecord<>(dmlRecordEventConvertDto.getTapTable().getId(),
			null, null, dmlRecordEventConvertDto.getKafkaMessageKey(), body,
			recordHeaders);
		produceCustomDmlRecordInfo.getKafkaProducer().send(producerRecord, callback);
	}

	@Override
	protected DMLRecordEventConvertDto performComputation(DMLRecordEventConvertDto dmlRecordEventConvertDto) {
		DMLRecordEventConvertDto result = customDmlConcurrents.process(scriptEngine -> {
			Collection<String> primaryKeys = dmlRecordEventConvertDto.getTapTable().primaryKeys(true);
			TapRecordEvent event = dmlRecordEventConvertDto.getRecordEvent();
			Map<String, Object> jsProcessParam = new HashMap<>();
			Map<String, Map<String, Object>> allData = new HashMap();
			MqOp mqOp = INSERT;
			Map<String, Object> eventInfo = new HashMap<>();
			String xid = MapUtils.getString(event.getInfo(), "XID");
			String rowId = MapUtils.getString(event.getInfo(), "rowId");
			if(StringUtils.isNotEmpty(xid)){
				eventInfo.put("XID", xid);
			}
			if(StringUtils.isNotEmpty(rowId)){
				eventInfo.put("rowId",rowId);
			}
			eventInfo.put("tableId", event.getTableId());
			eventInfo.put("referenceTime", event.getReferenceTime());
			jsProcessParam.put("eventInfo", eventInfo);
			Map<String, Object> data;
			if (event instanceof TapInsertRecordEvent) {
				data = ((TapInsertRecordEvent) event).getAfter();
				allData.put("before", new HashMap<String, Object>());
				allData.put("after", data);
			} else if (event instanceof TapUpdateRecordEvent) {
				data = ((TapUpdateRecordEvent) event).getAfter();
				Map<String, Object> before = ((TapUpdateRecordEvent) event).getBefore();
				allData.put("before", null == before ? new HashMap<>() : before);
				allData.put("after", data);
				mqOp = UPDATE;
			} else if (event instanceof TapDeleteRecordEvent) {
				data = ((TapDeleteRecordEvent) event).getBefore();
				allData.put("before", data);
				allData.put("after", new HashMap<String, Object>());
				mqOp = DELETE;
			} else {
				data = new HashMap<>();
			}
			byte[] kafkaMessageKey = getKafkaMessageKey(data, dmlRecordEventConvertDto.getTapTable());
			dmlRecordEventConvertDto.setKafkaMessageKey(kafkaMessageKey);
			jsProcessParam.put("data", allData);
			String op = mqOp.getOp();
			Map<String, Object> header = new HashMap();
			header.put("mqOp", op);
			jsProcessParam.put("header", header);
			Object jsConvertResult = ObjectUtils.covertData(executeScript(scriptEngine, "process", jsProcessParam, op, primaryKeys));
			if (jsConvertResult != null) {
				Map<String, Object> jsConvertResultMap = (Map<String, Object>) jsConvertResult;
				jsConvertResultMap.put("op", op);
				dmlRecordEventConvertDto.setJsConvertResultMap(jsConvertResultMap);
			}
			return dmlRecordEventConvertDto;
		});
		return result;
	}

	@Override
	protected void handleError(Exception e) {
		if(getHasException().compareAndSet(false, true)){
			getException().set(e);
		}
	}

	private byte[] getKafkaMessageKey(Map<String, Object> data, TapTable tapTable) {
		if (EmptyKit.isEmpty(tapTable.primaryKeys(true))) {
			return null;
		} else {
			return jsonParser.toJsonBytes(tapTable.primaryKeys(true).stream().map(key -> String.valueOf(data.get(key))).collect(Collectors.joining("_")));
		}
	}

	private void removeIfEmptyInMap(Map<String, Map<String, Object>> map, String key) {
		if (!map.containsKey(key)) return;
		Map<String, Object> o = map.get(key);
		if (null == o || o.isEmpty()) {
			map.remove(key);
		}
	}
}
