package io.github.jgjoe.byh.support;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * DataSource wrapper that counts every executed statement and delegates everything else.
 * See {@link StatementCountConfiguration} for how it is installed.
 */
public final class CountingDataSource implements DataSource {

    private final DataSource delegate;

    public CountingDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return countingConnection(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return countingConnection(delegate.getConnection(username, password));
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }

    private static Connection countingConnection(Connection connection) {
        return (Connection) Proxy.newProxyInstance(CountingDataSource.class.getClassLoader(),
                new Class<?>[] {Connection.class}, new ConnectionHandler(connection));
    }

    private static final class ConnectionHandler implements InvocationHandler {

        private final Connection target;

        ConnectionHandler(Connection target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = invokeTarget(target, method, args);
            if (result instanceof Statement statement) {
                return countingStatement(statement, method.getReturnType());
            }
            return result;
        }
    }

    private static Statement countingStatement(Statement statement, Class<?> statementType) {
        Class<?> type = statementType == CallableStatement.class ? CallableStatement.class
                : statementType == PreparedStatement.class ? PreparedStatement.class
                : Statement.class;
        return (Statement) Proxy.newProxyInstance(CountingDataSource.class.getClassLoader(),
                new Class<?>[] {type}, new StatementHandler(statement));
    }

    private static final class StatementHandler implements InvocationHandler {

        private final Statement target;

        StatementHandler(Statement target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (isExecution(method.getName())) {
                StatementCounter.recordExecution();
            }
            return invokeTarget(target, method, args);
        }

        private static boolean isExecution(String methodName) {
            return switch (methodName) {
                case "execute", "executeQuery", "executeUpdate", "executeLargeUpdate",
                     "executeBatch", "executeLargeBatch" -> true;
                default -> false;
            };
        }
    }

    private static Object invokeTarget(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }
}
