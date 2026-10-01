package com.group3.parser;

import java.util.ArrayList;
import java.util.List;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;

public class SQLParser {

    public Query parse(String sql) {
        final Statement statement;

        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new IllegalArgumentException("Invalid SQL syntax", e);
        }

        if (!(statement instanceof PlainSelect)) {
            throw new UnsupportedOperationException(
                "Only SELECT queries are supported"
            );
        }

        PlainSelect select = (PlainSelect) statement;

        if (!(select.getFromItem() instanceof Table)) {
            throw new UnsupportedOperationException(
                "FROM must specify a table"
            );
        }

        Table table = (Table) select.getFromItem();
        List<String> columns = new ArrayList<>();

        for (SelectItem<?> item : select.getSelectItems()) {
            if (item.getExpression() instanceof AllColumns) {
                columns.add("*");
            } else if (item.getExpression() instanceof Column) {
                Column column = (Column) item.getExpression();
                columns.add(column.getColumnName());
            } else {
                throw new UnsupportedOperationException(
                    "SELECT supports only column names or *"
                );
            }
        }

        return new Query(table.getFullyQualifiedName(), columns);
    }
}