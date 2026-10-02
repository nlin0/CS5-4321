package com.group3.expression;

import com.group3.data.Row;
import com.group3.data.Schema;

public interface Expression {

    boolean evaluate(Schema schema, Row row);
}
