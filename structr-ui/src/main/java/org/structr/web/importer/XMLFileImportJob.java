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
package org.structr.web.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.AccessMode;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Principal;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyMap;
import org.structr.module.StructrModule;
import org.structr.module.xml.XMLModule;
import org.structr.rest.common.XMLHandler;
import org.structr.web.entity.FileBase;

public class XMLFileImportJob extends ImportJob {

	private static final Logger logger = LoggerFactory.getLogger(XMLFileImportJob.class.getName());

	private String contentType;

	public XMLFileImportJob(FileBase file, Principal user, Map<String, Object> configuration) throws FrameworkException {
		super(file, user, configuration);

		contentType = file.getContentType();
	}

	@Override
	boolean runInitialChecks() throws FrameworkException {

		if ( !("text/xml".equals(contentType) || "application/xml".equals(contentType)) ) {

			throw new FrameworkException(400, "Cannot import XML from file with content type " + contentType);

		} else {

			final StructrModule module = StructrApp.getConfiguration().getModules().get("xml");

			if (module == null || !(module instanceof XMLModule) ) {

				throw new FrameworkException(400, "Cannot import XML, XML module is not available.");

			}

		}

		return true;
	}

	@Override
	public Runnable getRunnable() {

		return () -> {

			logger.info("Importing XML from {} ({})..", filePath, fileUuid);

			final SecurityContext threadContext = SecurityContext.getInstance(user, AccessMode.Backend);
			threadContext.setDoTransactionNotifications(false);
			final App app                       = StructrApp.getInstance(threadContext);
			int overallCount                    = 0;

			try (final InputStream is = getFileInputStream(threadContext)) {

				try (final Reader reader = new InputStreamReader(is)) {

					reportBegin();

					final Iterator<Map<String, Object>> iterator = new XMLHandler(configuration, reader);
					final int batchSize                          = 100;
					int chunks                                   = 0;

					final long startTime = System.currentTimeMillis();

					while (iterator.hasNext()) {

						int count = 0;

						try (final Tx tx = app.tx()) {

							while (iterator.hasNext() && ++count <= batchSize) {

								final PropertyMap map = PropertyMap.inputTypeToJavaType(threadContext, iterator.next());

								app.create(AbstractNode.class, map);

								overallCount++;
							}

							tx.success();

							chunks++;

							reportChunk(chunks);

							logger.info("XML: Imported {} objects, commiting batch.", overallCount);
						}

						// do this outside of the transaction!
						shouldPause();
						if (shouldAbort()) {
							return;
						}

					}

					final long endTime = System.currentTimeMillis();
					DecimalFormat decimalFormat  = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
					final String duration = decimalFormat.format(((endTime - startTime) / 1000.0)) + "s";

					logger.info("XML: Finished importing XML data from '{}' (Time: {})", filePath, duration);

					reportEnd(duration);

				} catch (XMLStreamException | FrameworkException ex) {
					reportException(ex);
				}

			} catch (IOException ex) {
				reportException(ex);
			}

			jobFinished();
		};
	}

	@Override
	public String getImportType() {
		return "XML";
	}

	@Override
	public String getImportStatusType() {
		return "FILE_IMPORT_STATUS";
	}

	@Override
	public String getImportExceptionMessageType() {
		return "FILE_IMPORT_EXCEPTION";
	}
}
