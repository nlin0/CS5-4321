package com.group3;
import com.group3.parser.Query;
import com.group3.parser.SQLParser;

public class Main {
    public static void main(String[] args) throws Exception {
        SQLParser parser = new SQLParser();

        Query query = parser.parse("SELECT name, age FROM students");

        System.out.println("Table: " + query.getTableName());
        System.out.println("Columns: " + query.getColumns());
    }
}