package com.group3;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

public class Main {
    public static void main(String[] args) throws Exception {
        Statement statement = CCJSqlParserUtil.parse("SELECT FROM my_table WHERE id = 42");

        // print it back
        String sql = statement.toString();
        System.out.println("our sql statement is " + sql);
    }
}