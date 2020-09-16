package com.winepos.birt.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import javax.naming.InitialContext;

public class Configuration {
	private String jndiName;
	private DbConnectionPool pool;
	private final String resourcesDirName;
	private final String logFilename;
	private final boolean logDebug;

	// null,java.lang.String,java.lang.String,java.lang.String,java.lang.String,string,org.mozilla.javascript.ConsString,boolean
	public Configuration(String jndiName, final String driver, final String url,
			final String username, final String password, final String resourcesDirName,
			final String logFilename, final boolean logDebug)
			throws ClassNotFoundException, SQLException {
		this.logFilename = logFilename;
		this.logDebug = logDebug;
		final Logger logger = getLogger();
		logger.log("********************************* Starting");
		if (jndiName != null) {
			try {
				new InitialContext();
			}
			catch (final Exception e2) {
				logger.log("Unable to connect to JNDI: " + e2);
				jndiName = null;
			}
		}
		this.jndiName = jndiName;
		if (jndiName == null) {
			this.pool = new DbConnectionPool(driver, url, username, password);
		}
		if (resourcesDirName == null) {
			throw new RuntimeException("resources property not found");
		}
		this.resourcesDirName = resourcesDirName;
		logger.log("Configuration loaded, jndiName = " + this.jndiName + ", pool = " + this.pool
			+ ", resourcesDirName = " + this.resourcesDirName);
	}

	public static Configuration load() throws IOException, ClassNotFoundException, SQLException {
		final Properties systemProps = System.getProperties();
		final String userHome = systemProps.getProperty("user.home");
		String propertiesFileName = System.getenv("BIRT_REPORT_POJO_PROPERTIES");
		if (propertiesFileName == null) {
			propertiesFileName = System.getProperty("BIRT_REPORT_POJO_PROPERTIES");
		}
		if (propertiesFileName == null) {
			if ("/disk1/home/winepos".equals(userHome)) {
				propertiesFileName = "/disk1/home/winepos/report-support.properties";
			}
		}
		if (propertiesFileName == null) {
			throw new RuntimeException("BIRT_REPORT_POJO_PROPERTIES environment variable not set");
		}
		return load(propertiesFileName);
	}

	public static Configuration load(final String propertiesFileName)
			throws IOException, ClassNotFoundException, SQLException {
		final File propertiesFile = new File(propertiesFileName);
		final Properties properties = new Properties();
		final FileInputStream fis = new FileInputStream(propertiesFile);
		try {
			properties.load(fis);
		}
		finally {
			fis.close();
		}
		fis.close();
		return new Configuration(properties.getProperty("db.jndi"),
				properties.getProperty("db.driver"), properties.getProperty("db.url"),
				properties.getProperty("db.username"), properties.getProperty("db.password"),
				properties.getProperty("resources"), properties.getProperty("log.filename"),
				"true".equalsIgnoreCase(properties.getProperty("log.debug")));
	}

	public String getJndiName() {
		return this.jndiName;
	}

	public void setJndiName(final String jndiName) {
		this.jndiName = jndiName;
	}

	public DbConnectionPool getPool() {
		return this.pool;
	}

	public void setPool(final DbConnectionPool pool) {
		this.pool = pool;
	}

	public String getResourcesDirName() {
		return resourcesDirName;
	}

	public Logger getLogger() {
		return new Logger(logFilename, logDebug, null);
	}
}
