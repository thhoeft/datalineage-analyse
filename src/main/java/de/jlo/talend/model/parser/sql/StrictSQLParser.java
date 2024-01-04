package de.jlo.talend.model.parser.sql;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;

public class StrictSQLParser {
	
	private List<String> listInputTables = new ArrayList<>();
	private List<String> listOutputTables = new ArrayList<>();
	private List<String> listCreateTables = new ArrayList<>();
	private List<String> listFunctions = new ArrayList<>();
	private String defaultSchema = null;
	
	public StrictSQLParser() {}
	
	private String getFullQualifiedName(String name) {
		if (defaultSchema != null) {
			int pos = name.indexOf(".");
			if (pos == -1) {
				return cleanupEnclosures(defaultSchema + "." + name);
			} else {
				return cleanupEnclosures(name);
			}
		} else {
			return cleanupEnclosures(name);
		}
	}
	
	private String cleanupEnclosures(String name) {
		if (name != null) {
			return name.replace("`", "").replace("[", "").replace("]", "").replace("\"", "");
		} else {
			return null;
		}
	}
	
	public static String cleanupEmptyLines(String text) throws IOException {
		BufferedReader r = new BufferedReader(new StringReader(text));
		String line = null;
		StringBuilder sb = new StringBuilder();
		while ((line = r.readLine()) != null) {
			if (line.trim().isEmpty() == false) {
				sb.append(line);
				sb.append("\n");
			}
		}
		return sb.toString();
	}
	
	public void reset() {
		listInputTables = new ArrayList<>();
		listOutputTables = new ArrayList<>();
		listCreateTables = new ArrayList<>();
		listFunctions = new ArrayList<>();
	}
	
	public void parseScriptFromFile(String scriptPath) throws Exception {
		String code = readContentfromFile(scriptPath, null);
		parseScriptFromCode(code);
	}
	
	public void parseScriptFromCode(String sqlScriptCode) throws Exception {
		if (sqlScriptCode == null || sqlScriptCode.trim().isEmpty()) {
			throw new IllegalArgumentException("SQL script cannot be null or empty");
		}
		List<Statement> listStatements = CCJSqlParserUtil.parseStatements(cleanupEmptyLines(sqlScriptCode));
		int index = 0;
		for (Statement stmt : listStatements) {
			index++;
			try {
				if (stmt instanceof Insert || stmt instanceof Update || stmt instanceof Delete || stmt instanceof Truncate) {
					TableAndProcedureNameFinder tablesNamesFinder = new TableAndProcedureNameFinder();
					tablesNamesFinder.analyse(stmt);
					List<String> tableList = tablesNamesFinder.getListTableNamesOutput();
					for (String tableName : tableList) {
						String name = getFullQualifiedName(tableName);
						if (listOutputTables.contains(name) == false) {
							listOutputTables.add(name);
						}
					}
					tableList = tablesNamesFinder.getListTableNamesInput();
					for (String tableName : tableList) {
						String name = getFullQualifiedName(tableName);
						if (listInputTables.contains(name) == false) {
							listInputTables.add(name);
						}
					}
				} else if (stmt instanceof Select) {
					TableAndProcedureNameFinder tablesNamesFinder = new TableAndProcedureNameFinder();
					tablesNamesFinder.analyse(stmt);
					List<String> tableList = tablesNamesFinder.getListTableNamesInput();
					for (String tableName : tableList) {
						String name = getFullQualifiedName(tableName);
						if (listInputTables.contains(name) == false) {
							listInputTables.add(name);
						}
					}
					List<String> functionList = tablesNamesFinder.getListFunctionSignatures();
					for (String name : functionList) {
						if (listFunctions.contains(name) == false) {
							listFunctions.add(name);
						}
					}
				} else if (stmt instanceof Function) {
					TableAndProcedureNameFinder tablesNamesFinder = new TableAndProcedureNameFinder();
					tablesNamesFinder.analyse(stmt);
					List<String> functionList = tablesNamesFinder.getListFunctionSignatures();
					for (String name : functionList) {
						if (listFunctions.contains(name) == false) {
							listFunctions.add(name);
						}
					}
					List<String> tableList = tablesNamesFinder.getListTableNamesInput();
					for (String tableName : tableList) {
						String name = getFullQualifiedName(tableName);
						if (listInputTables.contains(name) == false) {
							listInputTables.add(name);
						}
					}
				} else if (stmt instanceof CreateTable) {
					TableAndProcedureNameFinder tablesNamesFinder = new TableAndProcedureNameFinder();
					tablesNamesFinder.analyse(stmt);
					List<String> tableList = tablesNamesFinder.getListTableNamesCreate();
					for (String tableName : tableList) {
						String name = getFullQualifiedName(tableName);
						if (listCreateTables.contains(name) == false) {
							listCreateTables.add(name);
						}
					}
					tableList = tablesNamesFinder.getListTableNamesInput();
					for (String tableName : tableList) {
						String name = getFullQualifiedName(tableName);
						if (listInputTables.contains(name) == false) {
							listInputTables.add(name);
						}
					}
				} else if (stmt instanceof CreateView) {
					TableAndProcedureNameFinder tablesNamesFinder = new TableAndProcedureNameFinder();
					tablesNamesFinder.analyse(stmt);
					List<String> tableList = tablesNamesFinder.getListTableNamesCreate();
					for (String tableName : tableList) {
						String name = getFullQualifiedName(tableName);
						if (listCreateTables.contains(name) == false) {
							listCreateTables.add(name);
						}
					}
					tableList = tablesNamesFinder.getListTableNamesInput();
					for (String tableName : tableList) {
						String name = getFullQualifiedName(tableName);
						if (listInputTables.contains(name) == false) {
							listInputTables.add(name);
						}
					}
				}
			} catch (Throwable e) {
				throw new Exception("Analysing " + index + ". statement (type: " + stmt.getClass() + ") failed: " + e.getMessage(), e);
			}
		}
	}
	
	public List<String> getTablesRead() {
		return listInputTables;
	}
	
	public List<String> getTablesWritten() {
		return listOutputTables;
	}
	
	public List<String> getTablesCreated() {
		return listCreateTables;
	}

	public String getDefaultSchema() {
		return defaultSchema;
	}

	public void setDefaultSchema(String defaultSchema) {
		if (defaultSchema != null && defaultSchema.trim().isEmpty() == false) {
			this.defaultSchema = defaultSchema;
		}
	}
	
	private static String readContentfromFile(String filePath, String charset) throws Exception {
		if (filePath == null) {
			return null;
		}
		File f = new File(filePath);
		if (f.exists() == false) {
			throw new Exception("File: " + filePath + " does not exist.");
		}
		if (charset == null || charset.trim().isEmpty()) {
			charset = "UTF-8";
		}
		Path p = java.nio.file.Paths.get(filePath);
		byte[] bytes = Files.readAllBytes(p);
		if (bytes != null && bytes.length > 0) {
			return new String(bytes, charset);
		} else {
			return null;
		}
	}

}
