package com.group3;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.group3.parser.Query;
import com.group3.parser.SQLParser;

class ParserTest {
    SQLParser parser = new SQLParser();
    
    @Test
    void testValidParse() {
        String[] validQueries = {
            "SELECT * FROM my_table WHERE id = 42",
            "SELECT id FROM my_table WHERE id = 42",
            "SELECT *, id FROM my_table"
        };
        for (String validQuery: validQueries) {
            Query query = parser.parse(validQuery);
            assertNotNull(query);
        }  
    }

    @Test
    void testInvalidParse() {
        String[] invalidQueries = {
            "SELECT FROM my_table",
        };
        for (String invalidQuery: invalidQueries) {
            assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(invalidQuery)
            );
        }    
    }
    
}
