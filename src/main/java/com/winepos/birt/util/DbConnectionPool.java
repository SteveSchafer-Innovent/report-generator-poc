package com.winepos.birt.util;

import java.io.Serializable;
import java.net.SocketException;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DbConnectionPool implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER;
	private final String uRL;
	private final String username;
	private final String password;
	private final String driver;
	private final Queue<PooledConnection> connections;
	private long openConnectionIndex;
	private final Map<Long, OpenConnectionInfo> openConnections;
	private long timeout;
	static {
		LOGGER = Logger.getLogger(DbConnectionPool.class.getName());
	}

	public DbConnectionPool(final String dbDriver, final String dbName, final String dbUsername,
			final String dbPassword) throws ClassNotFoundException, SQLException {
		this.connections = new LinkedList<PooledConnection>();
		this.openConnectionIndex = 0L;
		this.openConnections = new HashMap<Long, OpenConnectionInfo>();
		this.timeout = 900000L;
		this.driver = dbDriver;
		this.uRL = dbName;
		this.username = dbUsername;
		this.password = dbPassword;
		this.validateConnection();
	}

	public final void validateConnection() throws ClassNotFoundException, SQLException {
		DbConnectionPool.LOGGER.log(Level.FINEST, "Testing connection pool");
		DbConnectionPool.LOGGER.log(Level.FINEST, "Instantiating " + this.driver + "\n");
		Class.forName(this.driver);
		DbConnectionPool.LOGGER.log(Level.FINEST, "Connecting");
		final Connection connection = DriverManager.getConnection(this.uRL, this.username,
			this.password);
		try {
			final DatabaseMetaData dbmd = connection.getMetaData();
			DbConnectionPool.LOGGER.log(Level.FINEST,
				"Connection to " + dbmd.getDatabaseProductName() + " "
					+ dbmd.getDatabaseProductVersion() + " successful.");
		}
		finally {
			connection.close();
		}
		connection.close();
	}

	public final void clear() throws SQLException {
		synchronized (this) {
			while (!this.connections.isEmpty()) {
				final PooledConnection connection = this.connections.remove();
				connection.reallyClose();
			}
		}
	}

	public final PooledConnection getConnection(final int transactionIsolation,
			final boolean readOnly, final boolean autoCommit) throws SQLException {
		while (true) {
			PooledConnection oldConnection;
			final long timeout;
			synchronized (this) {
				if (this.connections.isEmpty()) {
					oldConnection = null;
				}
				else {
					oldConnection = this.connections.remove();
				}
				timeout = this.timeout;
			}
			if (oldConnection == null) {
				DbConnectionPool.LOGGER.log(Level.FINEST, "Connecting");
				SQLException exception = null;
				int retryCount = 0;
				while (retryCount < 10) {
					Connection newConnection;
					try {
						newConnection = DriverManager.getConnection(this.uRL, this.username,
							this.password);
					}
					catch (final SQLException e) {
						if (!(e.getCause() instanceof SocketException)) {
							throw e;
						}
						exception = e;
						DbConnectionPool.LOGGER.log(Level.FINEST, "Retrying", e);
						try {
							Thread.sleep(1000L);
						}
						catch (final InterruptedException ex) {
						}
						++retryCount;
						newConnection = null;
					}
					if (newConnection != null) {
						newConnection.setAutoCommit(true);
						newConnection.setTransactionIsolation(transactionIsolation);
						newConnection.setReadOnly(readOnly);
						newConnection.setAutoCommit(autoCommit);
						final long index = this.incrementOpenConnectionCount();
						return new PooledConnection(newConnection, index);
					}
				}
				if (exception != null) {
					throw exception;
				}
			}
			if (oldConnection != null) {
				if (oldConnection.isClosed()) {
					DbConnectionPool.LOGGER.log(Level.WARNING,
						"Pooled connection was already closed");
				}
				else {
					Label_0430: {
						Label_0418: {
							if (timeout != 0L) {
								if (System.currentTimeMillis()
									- oldConnection.getLastAccess() >= timeout) {
									break Label_0418;
								}
							}
							try {
								oldConnection.setAutoCommit(true);
								oldConnection.setTransactionIsolation(transactionIsolation);
								oldConnection.setReadOnly(readOnly);
								oldConnection.setAutoCommit(autoCommit);
								synchronized (this) {
									final Long key = Long.valueOf(oldConnection.getIndex());
									final OpenConnectionInfo info = this.openConnections.get(key);
									if (info != null) {
										DbConnectionPool.LOGGER.log(Level.WARNING,
											"Overwriting open connection info: " + key + " "
												+ info);
									}
									this.openConnections.put(key, new OpenConnectionInfo());
								}
								return oldConnection;
							}
							catch (final Exception e2) {
								DbConnectionPool.LOGGER.log(Level.SEVERE,
									"Unable to reuse DB connection", e2);
								break Label_0430;
							}
						}
						DbConnectionPool.LOGGER.log(Level.FINEST, "DB connection timed out");
						try {
							oldConnection.reallyClose();
						}
						catch (final Exception e2) {
							DbConnectionPool.LOGGER.log(Level.SEVERE,
								"Unable to really close DB connection", e2);
						}
					}
				}
			}
		}
	}

	private long incrementOpenConnectionCount() {
		synchronized (this) {
			final long index = this.openConnectionIndex++;
			final Long key = Long.valueOf(index);
			final OpenConnectionInfo info = this.openConnections.get(key);
			if (info != null) {
				DbConnectionPool.LOGGER.log(Level.WARNING,
					"Overwriting open connection info: " + key + " " + info);
			}
			this.openConnections.put(key, new OpenConnectionInfo());
			return index;
		}
	}

	protected final void add(final PooledConnection connection) {
		synchronized (this) {
			final OpenConnectionInfo info = this.openConnections.remove(
				Long.valueOf(connection.getIndex()));
			if (info == null) {
				DbConnectionPool.LOGGER.log(Level.WARNING,
					"adding orphaned connection: " + connection.getIndex());
			}
			else {
				this.connections.offer(connection);
			}
		}
	}

	public final long getOpenConnectionIndex() {
		synchronized (this) {
			return this.openConnectionIndex;
		}
	}

	public final OpenConnectionInfo getOpenConnectionInfo(final long index) {
		synchronized (this) {
			return this.openConnections.get(Long.valueOf(index));
		}
	}

	public final int getOpenConnectionCount() {
		synchronized (this) {
			return this.openConnections.size();
		}
	}

	public final int getPooledConnectionCount() {
		synchronized (this) {
			return this.connections.size();
		}
	}

	@Override
	public final String toString() {
		final StringBuilder buf = new StringBuilder();
		buf.append("DB ");
		buf.append(this.uRL);
		buf.append(" ");
		synchronized (this) {
			buf.append("open: ");
			buf.append(this.openConnections.size());
			buf.append(", pooled: ");
			buf.append(this.connections.size());
			buf.append(", next: ");
			buf.append(this.openConnectionIndex);
		}
		return buf.toString();
	}

	public final long getTimeout() {
		synchronized (this) {
			return this.timeout;
		}
	}

	public final void setTimeout(final long timeout) {
		synchronized (this) {
			this.timeout = timeout;
		}
	}

	public final String getDriver() {
		return this.driver;
	}

	public final String getURL() {
		return this.uRL;
	}

	public final String getPassword() {
		return this.password;
	}

	public final String getUsername() {
		return this.username;
	}

	public synchronized Map<Long, Long> getOpenConnections() {
		final Map<Long, Long> map = new HashMap<Long, Long>();
		for (final Long index : this.openConnections.keySet()) {
			final OpenConnectionInfo info = this.openConnections.get(index);
			map.put(index, Long.valueOf(info.timestamp));
		}
		return map;
	}

	public static final class OpenConnectionInfo {
		public final long timestamp;
		private final List<StackTraceElement> stackTrace;

		public OpenConnectionInfo() {
			this.timestamp = System.currentTimeMillis();
			final StackTraceElement[] steArray = Thread.currentThread().getStackTrace();
			final List<StackTraceElement> stackTrace = new ArrayList<StackTraceElement>(
					steArray.length);
			StackTraceElement[] array;
			for (int length = (array = steArray).length, i = 0; i < length; ++i) {
				final StackTraceElement element = array[i];
				stackTrace.add(element);
			}
			this.stackTrace = Collections.unmodifiableList(stackTrace);
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append("Timestamp: ");
			sb.append(new Date(this.timestamp));
			sb.append("\n");
			for (final StackTraceElement element : this.stackTrace) {
				sb.append(element);
				sb.append("\n");
			}
			return sb.toString();
		}
	}

	public class PooledConnection implements Connection {
		private final Connection connection;
		private long lastAccess;
		private boolean autoCommit;
		private boolean readOnly;
		private final long index;

		public PooledConnection(final Connection connection, final long index) {
			this.lastAccess = System.currentTimeMillis();
			this.autoCommit = false;
			this.readOnly = false;
			this.index = index;
			this.connection = connection;
			try {
				this.autoCommit = connection.getAutoCommit();
			}
			catch (final SQLException e) {
				DbConnectionPool.LOGGER.log(Level.WARNING, "Unable to get auto commit", e);
			}
			try {
				this.readOnly = connection.isReadOnly();
			}
			catch (final SQLException e) {
				DbConnectionPool.LOGGER.log(Level.WARNING, "Unable to get read only", e);
			}
		}

		public void clearPool() throws SQLException {
			DbConnectionPool.this.clear();
		}

		@Override
		public String nativeSQL(final String sql) throws SQLException {
			return this.connection.nativeSQL(sql);
		}

		@Override
		public int hashCode() {
			return this.connection.hashCode();
		}

		@Override
		public Map<String, Class<?>> getTypeMap() throws SQLException {
			return this.connection.getTypeMap();
		}

		@Override
		public PreparedStatement prepareStatement(final String sql) throws SQLException {
			final PreparedStatement stmt = this.connection.prepareStatement(sql);
			if (this.connection.getAutoCommit() != this.autoCommit
				|| this.connection.isReadOnly() != this.readOnly) {
				final StringBuilder sb = new StringBuilder();
				String sep = "";
				if (this.connection.getAutoCommit() != this.autoCommit) {
					sb.append(sep);
					sep = " and ";
					sb.append("autoCommit has changed from " + this.autoCommit);
				}
				if (this.connection.isReadOnly() != this.readOnly) {
					sb.append(sep);
					sep = " and ";
					sb.append("readOnly has changed from " + this.readOnly);
				}
				sb.append(" in ");
				sb.append(stmt);
				DbConnectionPool.LOGGER.log(Level.WARNING, sb.toString());
			}
			return stmt;
		}

		@Override
		public void setTransactionIsolation(final int level) throws SQLException {
			this.connection.setTransactionIsolation(level);
		}

		@Override
		public String getCatalog() throws SQLException {
			return this.connection.getCatalog();
		}

		@Override
		public int getTransactionIsolation() throws SQLException {
			return this.connection.getTransactionIsolation();
		}

		@Override
		public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
			this.connection.releaseSavepoint(savepoint);
		}

		@Override
		public int getHoldability() throws SQLException {
			return this.connection.getHoldability();
		}

		@Override
		public CallableStatement prepareCall(final String sql, final int resultSetType,
				final int resultSetConcurrency, final int resultSetHoldability)
				throws SQLException {
			return this.connection.prepareCall(sql, resultSetType, resultSetConcurrency,
				resultSetHoldability);
		}

		@Override
		public boolean getAutoCommit() throws SQLException {
			return this.connection.getAutoCommit();
		}

		@Override
		public Statement createStatement() throws SQLException {
			return this.connection.createStatement();
		}

		@Override
		public CallableStatement prepareCall(final String sql) throws SQLException {
			return this.connection.prepareCall(sql);
		}

		@Override
		public void setAutoCommit(final boolean autoCommit) throws SQLException {
			this.autoCommit = autoCommit;
			this.connection.setAutoCommit(autoCommit);
		}

		@Override
		public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys)
				throws SQLException {
			return this.connection.prepareStatement(sql, autoGeneratedKeys);
		}

		@Override
		public void setReadOnly(final boolean readOnly) throws SQLException {
			this.readOnly = readOnly;
			this.connection.setReadOnly(readOnly);
		}

		@Override
		public CallableStatement prepareCall(final String sql, final int resultSetType,
				final int resultSetConcurrency) throws SQLException {
			return this.connection.prepareCall(sql, resultSetType, resultSetConcurrency);
		}

		@Override
		public SQLWarning getWarnings() throws SQLException {
			return this.connection.getWarnings();
		}

		@Override
		public PreparedStatement prepareStatement(final String sql, final int resultSetType,
				final int resultSetConcurrency) throws SQLException {
			return this.connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
		}

		@Override
		public boolean equals(final Object obj) {
			return this.connection.equals(obj);
		}

		@Override
		public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes)
				throws SQLException {
			return this.connection.prepareStatement(sql, columnIndexes);
		}

		@Override
		public boolean isClosed() throws SQLException {
			return this.connection.isClosed();
		}

		@Override
		public PreparedStatement prepareStatement(final String sql, final int resultSetType,
				final int resultSetConcurrency, final int resultSetHoldability)
				throws SQLException {
			return this.connection.prepareStatement(sql, resultSetType, resultSetConcurrency,
				resultSetHoldability);
		}

		@Override
		public void commit() throws SQLException {
			this.connection.commit();
		}

		@Override
		public void clearWarnings() throws SQLException {
			this.connection.clearWarnings();
		}

		@Override
		public void setCatalog(final String catalog) throws SQLException {
			this.connection.setCatalog(catalog);
		}

		@Override
		public void close() {
			DbConnectionPool.this.add(this);
			this.lastAccess = System.currentTimeMillis();
		}

		public void reallyClose() throws SQLException {
			this.connection.close();
		}

		@Override
		public String toString() {
			return this.connection.toString();
		}

		@Override
		public DatabaseMetaData getMetaData() throws SQLException {
			return this.connection.getMetaData();
		}

		@Override
		public void rollback() throws SQLException {
			this.connection.rollback();
		}

		@Override
		public Savepoint setSavepoint(final String name) throws SQLException {
			return this.connection.setSavepoint(name);
		}

		@Override
		public boolean isReadOnly() throws SQLException {
			return this.connection.isReadOnly();
		}

		@Override
		public Statement createStatement(final int resultSetType, final int resultSetConcurrency)
				throws SQLException {
			return this.connection.createStatement(resultSetType, resultSetConcurrency);
		}

		@Override
		public void rollback(final Savepoint savepoint) throws SQLException {
			this.connection.rollback(savepoint);
		}

		@Override
		public PreparedStatement prepareStatement(final String sql, final String[] columnNames)
				throws SQLException {
			return this.connection.prepareStatement(sql, columnNames);
		}

		@Override
		public Savepoint setSavepoint() throws SQLException {
			return this.connection.setSavepoint();
		}

		@Override
		public Statement createStatement(final int resultSetType, final int resultSetConcurrency,
				final int resultSetHoldability) throws SQLException {
			return this.connection.createStatement(resultSetType, resultSetConcurrency,
				resultSetHoldability);
		}

		@Override
		public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
			this.connection.setTypeMap(map);
		}

		@Override
		public void setHoldability(final int holdability) throws SQLException {
			this.connection.setHoldability(holdability);
		}

		public long getLastAccess() {
			return this.lastAccess;
		}

		@Override
		public Array createArrayOf(final String arg0, final Object[] arg1) throws SQLException {
			return this.connection.createArrayOf(arg0, arg1);
		}

		@Override
		public Blob createBlob() throws SQLException {
			return this.connection.createBlob();
		}

		@Override
		public Clob createClob() throws SQLException {
			return this.connection.createClob();
		}

		@Override
		public NClob createNClob() throws SQLException {
			return this.connection.createNClob();
		}

		@Override
		public SQLXML createSQLXML() throws SQLException {
			return this.connection.createSQLXML();
		}

		@Override
		public Struct createStruct(final String arg0, final Object[] arg1) throws SQLException {
			return this.connection.createStruct(arg0, arg1);
		}

		@Override
		public Properties getClientInfo() throws SQLException {
			return this.connection.getClientInfo();
		}

		@Override
		public String getClientInfo(final String arg0) throws SQLException {
			return this.connection.getClientInfo(arg0);
		}

		@Override
		public boolean isValid(final int arg0) throws SQLException {
			return this.connection.isValid(arg0);
		}

		@Override
		public void setClientInfo(final Properties arg0) throws SQLClientInfoException {
			this.connection.setClientInfo(arg0);
		}

		@Override
		public void setClientInfo(final String arg0, final String arg1)
				throws SQLClientInfoException {
			this.connection.setClientInfo(arg0, arg1);
		}

		@Override
		public boolean isWrapperFor(final Class<?> arg0) throws SQLException {
			return this.connection.isWrapperFor(arg0);
		}

		@Override
		public <T> T unwrap(final Class<T> arg0) throws SQLException {
			return this.connection.unwrap(arg0);
		}

		public long getIndex() {
			return this.index;
		}

		@Override
		public void setSchema(final String schema) throws SQLException {
			this.connection.setSchema(schema);
		}

		@Override
		public String getSchema() throws SQLException {
			return this.connection.getSchema();
		}

		@Override
		public void abort(final Executor executor) throws SQLException {
			this.connection.abort(executor);
		}

		@Override
		public void setNetworkTimeout(final Executor executor, final int milliseconds)
				throws SQLException {
			this.connection.setNetworkTimeout(executor, milliseconds);
		}

		@Override
		public int getNetworkTimeout() throws SQLException {
			return this.connection.getNetworkTimeout();
		}
	}
}
