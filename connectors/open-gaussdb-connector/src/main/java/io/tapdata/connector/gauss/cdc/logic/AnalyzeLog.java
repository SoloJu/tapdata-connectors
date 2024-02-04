package io.tapdata.connector.gauss.cdc.logic;

import io.debezium.connector.postgresql.TypeRegistry;
import io.tapdata.connector.gauss.cdc.logic.event.Event;
import io.tapdata.entity.event.dml.TapRecordEvent;

import java.nio.ByteBuffer;

public interface AnalyzeLog<T> {
    Event.EventEntity<T> analyze(ByteBuffer logEvent, AnalyzeParam param);

    public static class AnalyzeParam {
        TypeRegistry registry;
        public AnalyzeParam(TypeRegistry registry) {
            this.registry = registry;
        }

        public TypeRegistry getRegistry() {
            return registry;
        }

        public void setRegistry(TypeRegistry registry) {
            this.registry = registry;
        }
    }
}
