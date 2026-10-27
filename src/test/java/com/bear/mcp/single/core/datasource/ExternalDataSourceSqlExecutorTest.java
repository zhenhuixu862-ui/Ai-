package com.bear.mcp.single.core.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalDataSourceSqlExecutorTest {

    @Test
    void marksConnectionReadOnlyAndAppliesQueryTimeout() throws Exception {
        ExternalDataSourcePoolManager poolManager = mock(ExternalDataSourcePoolManager.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);

        when(poolManager.getPool(7L)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(0);
        when(resultSet.next()).thenReturn(false);

        Map<String, Object> result = new ExternalDataSourceSqlExecutor(poolManager).query(7L, "SELECT 1");

        var order = inOrder(connection, statement);
        order.verify(connection).setReadOnly(true);
        order.verify(connection).createStatement();
        order.verify(statement).setQueryTimeout(10);
        order.verify(statement).executeQuery("SELECT 1");
        assertEquals(0, result.get("row_count"));
    }
}
