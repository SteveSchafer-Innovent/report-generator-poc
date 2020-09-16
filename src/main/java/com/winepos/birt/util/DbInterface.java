package com.winepos.birt.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.naming.InitialContext;
import javax.sql.DataSource;

public class DbInterface {
	final Configuration configuration;

	public DbInterface(final Configuration configuration) {
		this.configuration = configuration;
	}

	public Connection getConnection() throws SQLException {
		final Logger logger = configuration.getLogger();
		final String jndiName = configuration.getJndiName();
		if (jndiName != null) {
			try {
				final InitialContext initialContext = new InitialContext();
				final DataSource datasource = (DataSource) initialContext.lookup(jndiName);
				SQLException lastException = null;
				int i = 0;
				while (i < 4) {
					try {
						return datasource.getConnection();
					}
					catch (final SQLException e) {
						logger.log("Failed to get connection, " + (3 - i) + " retrys left", e);
						lastException = e;
						try {
							Thread.sleep(5000L);
						}
						catch (final InterruptedException ex) {
						}
						++i;
					}
				}
				if (lastException != null) {
					throw lastException;
				}
			}
			catch (final Exception e2) {
				logger.log("Unable to connect to JNDI: " + e2);
			}
		}
		return configuration.getPool().getConnection(8, false, true);
	}

	public void update(final String resourceName, final Object[] parameters)
			throws IOException, SQLException {
		final File resourcesDir = new File(configuration.getResourcesDirName());
		final File file = new File(resourcesDir, resourceName);
		final String query = fileToString(file);
		execute(query, parameters);
	}

	public void execute(final String query, final Object[] parameters)
			throws IOException, SQLException {
		final Connection connection = getConnection();
		try {
			final PreparedStatement statement = connection.prepareStatement(query);
			try {
				if (parameters != null) {
					for (int i = 0; i < parameters.length; i++) {
						final Object value = parameters[i];
						statement.setObject(i + 1, value);
					}
				}
				statement.execute();
			}
			finally {
				statement.close();
			}
		}
		finally {
			connection.close();
		}
	}

	public static String fileToString(final File file) throws IOException {
		final StringBuilder sb = new StringBuilder();
		final FileReader fr = new FileReader(file);
		try {
			final char[] buffer = new char[0x2000];
			int charsRead = fr.read(buffer);
			while (charsRead >= 0) {
				sb.append(buffer, 0, charsRead);
				charsRead = fr.read(buffer);
			}
			return sb.toString();
		}
		finally {
			fr.close();
		}
	}

	public static String quote(final String string) {
		return "'" + string.replaceAll("'", "''") + "'";
	}

	public static String getQueryReplacementKey(final String key) {
		final StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append(key);
		sb.append("}");
		return sb.toString();
	}

	public static String sanitize(final String value) {
		return value.replaceAll(";", "");
	}
}
