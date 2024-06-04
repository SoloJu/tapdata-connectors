package io.tapdata.connector.tidb.cdc.process.thread;

import io.tapdata.common.util.FileUtil;
import io.tapdata.entity.logger.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;

public interface Activity extends AutoCloseable {
    //"/Users/xiao/Documents/GitHub/kit/tidb/%s"
    String BASE_CDC_SOURCE_DIR = "run-resources/ti-db";
    String BASE_CDC_TOOL_DIR = "run-resources/ti-db/tool";
    String BASE_CDC_CACHE_DATA_DIR = "run-resources/ti-db/data";
    String BASE_CDC_LOG_DIR = "run-resources/ti-db/log";
    String BASE_CDC_DATA_DIR = "run-resources/ti-db/cdc/%s";

    void init();

    void doActivity();

    default void cancelSchedule(ScheduledFuture<?> future, Log log) {
        if (Objects.nonNull(future)) {
            try {
                future.cancel(true);
            } catch (Exception e1){
                log.warn("Scheduled cancel failed: {}", e1.getMessage());
            }
        }
    }

    default List<File> scanAllCdcTableDir(List<String> cdcTable, File databaseDir, Supplier<Boolean> alive) {
        List<File> tableDirs = new ArrayList<>();
        if (org.apache.commons.collections.CollectionUtils.isEmpty(cdcTable)) {
            File[] tableFiles = databaseDir.listFiles(File::isDirectory);
            if (null != tableFiles && tableFiles.length > 0) {
                tableDirs.addAll(new ArrayList<>(Arrays.asList(tableFiles)));
            }
        } else {
            for (String tableName : cdcTable) {
                if (!alive.get()) {
                    break;
                }
                File file = new File(FileUtil.paths(databaseDir.getAbsolutePath(), tableName));
                if (file.exists() && file.isDirectory()) {
                    tableDirs.add(file);
                }
            }
        }
        return tableDirs;
    }

    default long getTOSTime() {
        return getTOSTime(System.currentTimeMillis());
    }
    default long getTOSTime(Long time) {
        if (null == time) return getTOSTime();
        return time >> 18;
    }
}
