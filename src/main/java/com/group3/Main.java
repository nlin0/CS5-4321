package com.group3;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;

public class Main {
    public static void main(String[] args) throws Exception {
        Statement statement = CCJSqlParserUtil.parse("SELECT a FROM my_table WHERE id = 42");

        // inspect it
        PlainSelect select = (PlainSelect) statement;
        Table table = (Table) select.getFromItem();      // my_table

        // print it back
        String sql = statement.toString();
        System.out.println("our sql statement is " + sql);
    }
}