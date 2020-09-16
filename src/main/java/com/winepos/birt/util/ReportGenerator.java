package com.winepos.birt.util;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.birt.core.framework.Platform;
import org.eclipse.birt.report.model.api.CellHandle;
import org.eclipse.birt.report.model.api.ColumnHandle;
import org.eclipse.birt.report.model.api.DataItemHandle;
import org.eclipse.birt.report.model.api.DesignConfig;
import org.eclipse.birt.report.model.api.ElementFactory;
import org.eclipse.birt.report.model.api.IDesignEngine;
import org.eclipse.birt.report.model.api.IDesignEngineFactory;
import org.eclipse.birt.report.model.api.LabelHandle;
import org.eclipse.birt.report.model.api.OdaDataSetHandle;
import org.eclipse.birt.report.model.api.OdaDataSourceHandle;
import org.eclipse.birt.report.model.api.ReportDesignHandle;
import org.eclipse.birt.report.model.api.RowHandle;
import org.eclipse.birt.report.model.api.SessionHandle;
import org.eclipse.birt.report.model.api.SimpleMasterPageHandle;
import org.eclipse.birt.report.model.api.StructureFactory;
import org.eclipse.birt.report.model.api.TableHandle;
import org.eclipse.birt.report.model.api.activity.SemanticException;
import org.eclipse.birt.report.model.api.elements.structures.ComputedColumn;

import com.ibm.icu.util.ULocale;

public class ReportGenerator {
	public static void main(final String[] args) throws ClassNotFoundException, SQLException {
		try {
			buildReport(args[0]);
		}
		catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (final SemanticException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// This function shows how to build a very simple BIRT report with a
	// minimal set of content: a simple grid with an image and a label.
	static void buildReport(final String tableName)
			throws IOException, SemanticException, ClassNotFoundException, SQLException {
		final Configuration configuration = Configuration.load();
		final DbInterface db = new DbInterface(configuration);
		final Connection connection = db.getConnection();
		final String query = "select * from " + tableName;
		final List<String> colNames = new ArrayList<>();
		int colCount;
		try {
			final PreparedStatement statement = connection.prepareStatement(query);
			try {
				final ResultSet resultSet = statement.executeQuery();
				try {
					final ResultSetMetaData metaData = resultSet.getMetaData();
					colCount = metaData.getColumnCount();
					for (int i = 0; i < colCount; i++) {
						final String colName = metaData.getColumnName(i + 1);
						colNames.add(colName);
					}
				}
				finally {
					resultSet.close();
				}
			}
			finally {
				statement.close();
			}
		}
		finally {
			connection.close();
		}
		// Create a session handle. This is used to manage all open designs.
		// Your app need create the session only once.
		//Configure the Engine and start the Platform
		final DesignConfig config = new DesignConfig();
		config.setProperty("BIRT_HOME", "/disk1/eclipse/birt-runtime-4_8_0/ReportEngine");
		IDesignEngine engine = null;
		try {
			Platform.startup(config);
			final IDesignEngineFactory factory = (IDesignEngineFactory) Platform.createFactoryObject(
				IDesignEngineFactory.EXTENSION_DESIGN_ENGINE_FACTORY);
			engine = factory.createDesignEngine(config);
		}
		catch (final Exception ex) {
			ex.printStackTrace();
		}
		final SessionHandle session = engine.newSessionHandle(ULocale.ENGLISH);
		// Create a new report design.
		final ReportDesignHandle design = session.createDesign();
		// The element factory creates instances of the various BIRT elements.
		final ElementFactory factory = design.getElementFactory();
		// Create a simple master page that describes how the report will
		// appear when printed.
		//
		// Note: The report will fail to load in the BIRT designer
		// unless you create a master page.
		final SimpleMasterPageHandle masterPage = factory.newSimpleMasterPage("Page Master"); //$NON-NLS-1$
		design.getMasterPages().add(masterPage);
		masterPage.setProperty("type", "custom");
		masterPage.setProperty("height", "11in");
		masterPage.setProperty("width", colCount + "in");
		//
		// begin POC code for Winepos
		//
		final OdaDataSourceHandle dataSource = factory.newOdaDataSource("Data Source",
			"org.eclipse.birt.report.data.oda.jdbc");
		design.getDataSources().add(dataSource);
		// dataSource.setPrivateDriverProperty("metadataBidiFormatStr", "ILYNN");
		// dataSource.setPrivateDriverProperty("disabledMetadataBidiFormatStr", null);
		// dataSource.setPrivateDriverProperty("contentBidiFormatStr", "ILYNN");
		// dataSource.setPrivateDriverProperty("disabledContentBidiFormatStr", null);
		dataSource.setProperty("odaDriverClass", "org.postgresql.Driver");
		dataSource.setProperty("odaURL", "jdbc:postgresql://localhost:5432/postgres");
		dataSource.setProperty("odaUser", "postgres");
		dataSource.setEncryption("odaPassword", "base64");
		dataSource.setProperty("odaPassword", "Zm9vYmFy");
		//
		final OdaDataSetHandle dataSet = factory.newOdaDataSet("Data Set",
			"org.eclipse.birt.report.data.oda.jdbc.JdbcSelectDataSet");
		design.getDataSets().add(dataSet);
		dataSet.setDataSource("Data Source");
		dataSet.setQueryText(query);
		//
		final TableHandle table = factory.newTableItem("Table", colCount, 1, 1, 1);
		design.getBody().add(table);
		table.setWidth("100%");
		table.setDataSet(design.findDataSet("Data Set"));
		for (int i = 0; i < colCount; i++) {
			final ColumnHandle column = table.findColumn(i + 1);
			column.setProperty("width", "1in");
		}
		for (int i = 0; i < colCount; i++) {
			final ComputedColumn cs1 = StructureFactory.createComputedColumn();
			final String colName = colNames.get(i);
			cs1.setName(colName);
			cs1.setExpression("dataSetRow[\"" + colName + "\"]");
			cs1.setDataType("string");
			table.addColumnBinding(cs1, false);
		}
		// table header
		final RowHandle tableHeader = (RowHandle) table.getHeader().get(0);
		for (int i = 0; i < colCount; i++) {
			final CellHandle cell = (CellHandle) tableHeader.getCells().get(i);
			cell.setColumnSpan(1);
			cell.setRowSpan(1);
			final LabelHandle label1 = factory.newLabel("label");
			label1.setProperty("fontWeight", "bold");
			label1.setText(colNames.get(i));
			cell.getContent().add(label1);
		}
		// table detail
		final RowHandle tableDetail = (RowHandle) table.getDetail().get(0);
		for (int i = 0; i < colCount; i++) {
			final CellHandle cell = (CellHandle) tableDetail.getCells().get(i);
			final DataItemHandle data = factory.newDataItem("data");
			data.setResultSetColumn(colNames.get(i));
			cell.getContent().add(data);
		}
		// table footer
		final RowHandle tableFooter = (RowHandle) table.getFooter().get(0);
		for (int i = 0; i < colCount; i++) {
			final CellHandle cell = (CellHandle) tableFooter.getCells().get(i);
			cell.setColumnSpan(1);
			cell.setRowSpan(1);
		}
		//
		// Save the design and close it.
		design.saveAs("/disk1/home/winepos/projects/reports/reports/sample.rptdesign"); //$NON-NLS-1$
		design.close();
		System.out.println("Finished");
		// We're done!
	}
}
