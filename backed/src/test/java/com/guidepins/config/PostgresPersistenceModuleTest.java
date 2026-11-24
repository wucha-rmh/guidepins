package com.guidepins.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.UUID;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** PostgreSQL UUID 映射的单元测试，不依赖远程数据库。 */
class PostgresPersistenceModuleTest {
    private final PostgresUuidTypeHandler handler = new PostgresUuidTypeHandler();

    @Test
    void bindsUuidAsPostgresOtherType() throws Exception {
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        UUID id = UUID.randomUUID();

        handler.setNonNullParameter(statement, 2, id, JdbcType.OTHER);

        verify(statement).setObject(2, id, Types.OTHER);
    }

    @Test
    void readsNativeUuidFromResultAndCallStatements() throws Exception {
        UUID id = UUID.randomUUID();
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        CallableStatement call = Mockito.mock(CallableStatement.class);
        when(resultSet.getObject("id", UUID.class)).thenReturn(id);
        when(resultSet.getObject(1, UUID.class)).thenReturn(id);
        when(call.getObject(1, UUID.class)).thenReturn(id);

        assertEquals(id, handler.getNullableResult(resultSet, "id"));
        assertEquals(id, handler.getNullableResult(resultSet, 1));
        assertEquals(id, handler.getNullableResult(call, 1));
    }
}
