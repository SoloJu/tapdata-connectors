package io.tapdata.connector.tidb.cdc.process.analyse.csv;

import java.util.List;

public interface CdcAcceptNormal<T> extends CdcAccepter {
    default void acceptObject(List<T> compileLines){}
}
