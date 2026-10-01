package com.group3.engine;

import com.group3.parser.Query;

public class QueryEngine {

    public QueryResult processQuery(Query query) {
        /* 
        Steps:
        1. Retrieve query.getTableName() from the catalog.
        2. Scan its rows.
        2. Select all columns using query.getColumns() (* means take all columns of the table).
        3. Return the result.
        */         
        return null;
    }
}