package com.winepos.birt.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
	public final String logFilename;
	public final boolean debugLogging;
	public final String suffix;

	public Logger(final String logFilename, final boolean debugLogging, final String suffix) {
		this.logFilename = logFilename;
		this.debugLogging = debugLogging;
		this.suffix = suffix;
	}

	public void debugLog(final Object message, final Object details) {
		if (debugLogging) {
			log(message, details);
		}
	}

	public void debugLog(final Object message) {
		if (debugLogging) {
			log(message, null);
		}
	}

	public void debugLogNoSuffix(final Object message, final Object details) {
		if (debugLogging) {
			logNoSuffix(message, details);
		}
	}

	public void debugLogNoSuffix(final Object message) {
		if (debugLogging) {
			logNoSuffix(message, null);
		}
	}

	public void debugLog(final Object message, final Object details, final Throwable t) {
		if (debugLogging) {
			log(message, details, t, true);
		}
	}

	public void debugLog(final Object message, final Throwable t) {
		if (debugLogging) {
			log(message, null, t, true);
		}
	}

	public void debugLogNoSuffix(final Object message, final Object details, final Throwable t) {
		if (debugLogging) {
			log(message, details, t, false);
		}
	}

	public void debugLogNoSuffix(final Object message, final Throwable t) {
		if (debugLogging) {
			log(message, null, t, false);
		}
	}

	public void log(final Object message, final Object details) {
		log(message, details, null, true);
	}

	public void log(final Object message) {
		log(message, null, null, true);
	}

	public void logNoSuffix(final Object message, final Object details) {
		log(message, details, null, false);
	}

	public void logNoSuffix(final Object message) {
		log(message, null, null, false);
	}

	public void log(final Object message, final Object details, final Throwable t) {
		log(message, details, t, true);
	}

	public void log(final Object message, final Throwable t) {
		log(message, null, t, true);
	}

	public void log(final Object message, final Object details, final Throwable t,
			final boolean includeSuffix) {
		final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ");
		try {
			final String filename = this.logFilename;
			if (filename == null) {
				final PrintStream out = System.out;
				out.print(df.format(new Date()));
				out.print(" ");
				out.print(message);
				if (suffix != null && includeSuffix) {
					out.print(" ");
					out.print(suffix);
				}
				out.println();
				if (details != null) {
					out.println(details);
				}
				if (t != null) {
					t.printStackTrace(out);
				}
			}
			else {
				final File file = new File(filename);
				final FileWriter fw = new FileWriter(file, true);
				final PrintWriter pw = new PrintWriter(fw);
				try {
					pw.print(df.format(new Date()));
					pw.print(" ");
					pw.println(message);
					if (suffix != null && includeSuffix) {
						pw.print(" ");
						pw.print(suffix);
						pw.println();
					}
					if (details != null) {
						pw.println(details);
					}
					if (t != null) {
						t.printStackTrace(pw);
					}
				}
				finally {
					pw.close();
				}
				pw.close();
			}
		}
		catch (final IOException e) {
			System.out.println("Logger: Unable to log " + message);
			if (t != null) {
				t.printStackTrace();
			}
			System.out.println("Log failure caused by:");
			e.printStackTrace();
		}
	}
}
