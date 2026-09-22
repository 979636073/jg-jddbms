package com.jd.spi;


import com.jd.spi.model.Command;
import com.jd.spi.model.ExecuteResult;

import javax.validation.constraints.NotEmpty;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Command executor
 * <p>
 * The command executor is used to execute the command.
 * <br>
 */
public interface CommandExecutor {

    /**
     * Execute command
     */
    List<ExecuteResult> execute(Command command);

    /**
     * 删除会话
     * @param command
     * @return
     */
    boolean delSession(Command command);

    /**
     * 提交会话
     * @param command
     * @return
     */
    boolean commitSession(Command command);


    /**
     * Execute command
     */
    ExecuteResult executeUpdate(String sql, Connection connection, int n)throws SQLException;



    ExecuteResult executeBlob(Connection connection, String sql, @NotEmpty List<String> blobValues);


    /**
     * Execute command
     */
    List<ExecuteResult> executeSelectTable(Command command);


    /**
     *
     *
     */
     ExecuteResult execute(final String sql, Connection connection, boolean limitRowSize, Integer offset,
                                 Integer count, ValueHandler valueHandler)
            throws SQLException;

    ExecuteResult JDBCExecute(Command command);

    boolean rollbackSession(Command command);

    /**
     * Cancel a running SQL execution when supported by the driver.
     */
    default boolean cancel(String executionId) {
        return false;
    }
}
