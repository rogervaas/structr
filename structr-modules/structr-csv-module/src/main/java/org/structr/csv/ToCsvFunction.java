/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.csv;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Result;
import org.structr.core.app.StructrApp;
import org.structr.core.function.LocalizeFunction;
import org.structr.core.property.DateProperty;
import org.structr.core.property.PropertyKey;
import org.structr.schema.action.ActionContext;
import org.structr.schema.parser.DatePropertyParser;
import org.structr.web.function.UiFunction;

/**
 *
 */
public class ToCsvFunction extends UiFunction {
	public static final String ERROR_MESSAGE_TO_CSV    = "Usage: ${to_csv(nodes, propertiesOrView[, delimiterChar[, quoteChar[, recordSeparator[, includeHeader[, localizeHeader[, headerLocalizationDomain]]]])}. Example: ${to_csv(find('Page'), 'ui')}";
	public static final String ERROR_MESSAGE_TO_CSV_JS = "Usage: ${{Structr.to_csv(nodes, propertiesOrView[, delimiterChar[, quoteChar[, recordSeparator[, includeHeader[, localizeHeader[, headerLocalizationDomain]]]])}}. Example: ${{Structr.to_csv(Structr.find('Page'), 'ui'))}}";

	@Override
	public String getName() {
		return "to_csv()";
	}
	@Override
	public Object apply(ActionContext ctx, Object caller, Object[] sources) throws FrameworkException {

		try {

			if (arrayHasMinLengthAndMaxLengthAndAllElementsNotNull(sources, 2, 8)) {

				if ( !(sources[0] instanceof List) ) {
					logParameterError(caller, sources, ctx.isJavaScriptContext());
					return "ERROR: First parameter must be a collection!".concat(usage(ctx.isJavaScriptContext()));
				}

				final List<GraphObject> nodes           = (List)sources[0];
				String delimiterChar                    = ";";
				String quoteChar                        = "\"";
				String recordSeparator                  = "\n";
				boolean includeHeader                   = true;
				boolean localizeHeader                  = false;
				String headerLocalizationDomain         = null;
				String propertyView                     = null;
				List<String> properties                 = null;

				// we are using size() instead of isEmpty() because NativeArray.isEmpty() always returns true
				if (nodes.size() == 0) {
					logger.warn("to_csv(): Can not create CSV if no nodes are given!");
					logParameterError(caller, sources, ctx.isJavaScriptContext());
					return "";
				}

				switch (sources.length) {
					case 8: headerLocalizationDomain = (String)sources[7];
					case 7: localizeHeader = (Boolean)sources[6];
					case 6: includeHeader = (Boolean)sources[5];
					case 5: recordSeparator = (String)sources[4];
					case 4: quoteChar = (String)sources[3];
					case 3: delimiterChar = (String)sources[2];
					case 2: {
						if (sources[1] instanceof String) {
							// view is given
							propertyView = (String)sources[1];

						} else if (sources[1] instanceof List) {
							// named properties are given
							properties = (List)sources[1];

							// we are using size() instead of isEmpty() because NativeArray.isEmpty() always returns true
							if (properties.size() == 0) {
								logger.warn("to_csv(): Can not create CSV if list of properties is empty!");
								logParameterError(caller, sources, ctx.isJavaScriptContext());
								return "";
							}

						} else {
							logParameterError(caller, sources, ctx.isJavaScriptContext());
							return "ERROR: Second parameter must be a collection of property names or a single property view!".concat(usage(ctx.isJavaScriptContext()));
						}
					}
				}
				try {

					final StringWriter writer = new StringWriter();
					writeCsv(nodes, writer, propertyView, properties, quoteChar.charAt(0), delimiterChar.charAt(0), recordSeparator, includeHeader, localizeHeader, headerLocalizationDomain, ctx.getLocale());
					return writer.toString();

				} catch (Throwable t) {
					logger.warn("to_csv(): Exception occurred", t);
					return "";
				}

			} else {

				logParameterError(caller, sources, ctx.isJavaScriptContext());
				return usage(ctx.isJavaScriptContext());
			}

		} catch (final IllegalArgumentException e) {

			logParameterError(caller, sources, ctx.isJavaScriptContext());

			return usage(ctx.isJavaScriptContext());

		}

	}

	@Override
	public String usage(boolean inJavaScriptContext) {
		return (inJavaScriptContext ? ERROR_MESSAGE_TO_CSV_JS : ERROR_MESSAGE_TO_CSV);
	}

	@Override
	public String shortDescription() {
		return "Returns a CSV representation of the given nodes";
	}

	public static void writeCsv(
			final Result result,
			final Writer out,
			final String propertyView,
			final List<String> properties,
			final char quoteChar,
			final char delimiterChar,
			final String recordSeparator,
			final boolean includeHeader,
			final boolean localizeHeader,
			final String headerLocalizationDomain,
			final Locale locale
	) throws IOException {

		final List<GraphObject> list = result.getResults();

		writeCsv(list, out, propertyView, properties, quoteChar, delimiterChar, recordSeparator, includeHeader, localizeHeader, headerLocalizationDomain, locale);
	}

	public static void writeCsv(
			final List list,
			final Writer out,
			final String propertyView,
			final List<String> properties,
			final char quoteChar,
			final char delimiterChar,
			final String recordSeparator,
			final boolean includeHeader,
			final boolean localizeHeader,
			final String headerLocalizationDomain,
			final Locale locale
	) throws IOException {

		final StringBuilder row = new StringBuilder();

		if (includeHeader) {

			row.setLength(0);

			boolean isFirstCol = true;

			if (propertyView != null) {

				final Object obj = list.get(0);

				if (obj instanceof GraphObject) {
					for (PropertyKey key : ((GraphObject)obj).getPropertyKeys(propertyView)) {
						String value = key.dbName();
						if (localizeHeader) {
							try {
								value = LocalizeFunction.getLocalization(locale, value, headerLocalizationDomain);
							} catch (FrameworkException fex) {
								logger.warn("to_csv(): Exception", fex);
							}
						}

						isFirstCol = appendColumnString(row, value, isFirstCol, quoteChar, delimiterChar);
					}
				} else {
					row.append("Error: Object is not of type GraphObject, can not determine properties of view for header row");
				}

			} else if (properties != null) {

				for (final String colName : properties) {
					String value = colName;
					if (localizeHeader) {
						try {
							value = LocalizeFunction.getLocalization(locale, value, headerLocalizationDomain);
						} catch (FrameworkException fex) {
							logger.warn("to_csv(): Exception", fex);
						}
					}

					isFirstCol = appendColumnString(row, value, isFirstCol, quoteChar, delimiterChar);
				}
			}

			out.append(row).append(recordSeparator).flush();

		}

		for (final Object obj : list) {

			row.setLength(0);

			boolean isFirstCol = true;

			if (propertyView != null) {

				if (obj instanceof GraphObject) {

					for (PropertyKey key : ((GraphObject)obj).getPropertyKeys(propertyView)) {

						final Object value = ((GraphObject)obj).getProperty(key);
						isFirstCol = appendColumnString(row, value, isFirstCol, quoteChar, delimiterChar);
					}
				} else {
					row.append("Error: Object is not of type GraphObject, can not determine properties of object");
				}

			} else if (properties != null) {

				if (obj instanceof GraphObject) {

					final GraphObject castedObj = (GraphObject)obj;

					for (final String colName : properties) {
						final PropertyKey key = StructrApp.getConfiguration().getPropertyKeyForJSONName(obj.getClass(), colName);
						final Object value = castedObj.getProperty(key);
						isFirstCol = appendColumnString(row, value, isFirstCol, quoteChar, delimiterChar);
					}
				} else if (obj instanceof Map) {

					final Map castedObj = (Map)obj;

					for (final String colName : properties) {
						final Object value = castedObj.get(colName);
						isFirstCol = appendColumnString(row, value, isFirstCol, quoteChar, delimiterChar);
					}

				}
			}

			// Replace \r and \n so we dont get multi-line CSV (needs to be four backslashes because regex)
			final String rowWithoutRecordSeparator = row.toString().replaceAll("\n", "\\\\n").replaceAll("\r", "\\\\r");

			out.append(rowWithoutRecordSeparator).append(recordSeparator).flush();
		}

	}

	private static boolean appendColumnString (final StringBuilder row, final Object value, boolean isFirstColumn, final char quoteChar, final char delimiter) {
		if (!isFirstColumn) {
			row.append(delimiter);
		}
		row.append(escapeForCsv(value, quoteChar));

		return false;
	}

	private static String escapeForCsv(final Object value, final char quoteChar) {

		String result;

		if (value == null) {

			result = "";

		} else if (value instanceof String[]) {

			// Special handling for StringArrays
			ArrayList<String> quotedStrings = new ArrayList();
			for (final String str : Arrays.asList((String[])value)) {
				// The strings can contain quotes - these need to be escaped with 3 slashes in the output
				quotedStrings.add("\\" + quoteChar + StringUtils.replace(str, ""+quoteChar, "\\\\\\" + quoteChar) + "\\" + quoteChar);
			}

			result = quotedStrings.toString();

		} else if (value instanceof Collection) {

			// Special handling for collections of nodes
			ArrayList<String> quotedStrings = new ArrayList();
			for (final Object obj : (Collection)value) {
				quotedStrings.add("\\" + quoteChar + obj.toString() + "\\" + quoteChar);
			}

			result = quotedStrings.toString();

		} else if (value instanceof Date) {

			result = DatePropertyParser.format((Date) value, DateProperty.getDefaultFormat());

		} else {

			result = StringUtils.replace(value.toString(), ""+quoteChar, "\\" + quoteChar);

		}

		return "".concat(""+quoteChar).concat(StringUtils.replace(StringUtils.replace(result, "\r\n", "\\n"), "\r", "\\n")).concat(""+quoteChar);
	}

}
