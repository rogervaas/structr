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
package org.structr.web.servlet;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.RetryException;
import org.structr.api.config.Settings;
import org.structr.common.AccessMode;
import org.structr.common.PathHelper;
import org.structr.common.Permission;
import org.structr.common.SecurityContext;
import org.structr.common.ThreadLocalMatcher;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.StructrApp;
import org.structr.core.auth.exception.AuthenticationException;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyMap;
import org.structr.rest.service.HttpServiceServlet;
import org.structr.rest.service.StructrHttpServiceConfig;
import org.structr.schema.SchemaHelper;
import org.structr.web.auth.UiAuthenticator;
import org.structr.web.common.FileHelper;
import org.structr.web.entity.FileBase;
import org.structr.web.entity.Folder;

//~--- classes ----------------------------------------------------------------
/**
 * Simple upload servlet.
 *
 *
 */
public class UploadServlet extends HttpServlet implements HttpServiceServlet {

	private static final Logger logger                             = LoggerFactory.getLogger(UploadServlet.class.getName());
	private static final ThreadLocalMatcher threadLocalUUIDMatcher = new ThreadLocalMatcher("[a-fA-F0-9]{32}");
	private static final String REDIRECT_AFTER_UPLOAD_PARAMETER    = "redirectOnSuccess";
	private static final String APPEND_UUID_ON_REDIRECT_PARAMETER  = "appendUuidOnRedirect";
	private static final String UPLOAD_FOLDER_PATH_PARAMETER       = "uploadFolderPath";
	private static final int MEGABYTE                              = 1024 * 1024;
	private static final int MEMORY_THRESHOLD                      = 10 * MEGABYTE;  // above 10 MB, files are stored on disk

	// non-static fields
	private ServletFileUpload uploader = null;
	private File filesDir = null;
	private final StructrHttpServiceConfig config = new StructrHttpServiceConfig();

	public UploadServlet() {
	}

	//~--- methods --------------------------------------------------------
	@Override
	public StructrHttpServiceConfig getConfig() {
		return config;
	}

	@Override
	public String getModuleName() {
		return "ui";
	}

	@Override
	public void init() {

		try (final Tx tx = StructrApp.getInstance().tx()) {

			DiskFileItemFactory fileFactory = new DiskFileItemFactory();
			fileFactory.setSizeThreshold(MEMORY_THRESHOLD);

			filesDir = new File(Settings.TmpPath.getValue()); // new File(Services.getInstance().getTmpPath());
			if (!filesDir.exists()) {
				filesDir.mkdir();
			}

			fileFactory.setRepository(filesDir);
			uploader = new ServletFileUpload(fileFactory);

			tx.success();

		} catch (FrameworkException t) {

			logger.warn("", t);
		}
	}

	@Override
	public void destroy() {
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException {

		try {

			if (!ServletFileUpload.isMultipartContent(request)) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.getOutputStream().write("ERROR (400): Request does not contain multipart content.\n".getBytes("UTF-8"));
				return;
			}

		} catch (IOException ioex) {
			logger.warn("Unable to send response", ioex);
		}

		SecurityContext securityContext = null;
		String redirectUrl              = null;
		boolean appendUuidOnRedirect    = false;
		String path                     = null;

		// isolate request authentication in a transaction
		try (final Tx tx = StructrApp.getInstance().tx()) {

			try {
				securityContext = getConfig().getAuthenticator().initializeAndExamineRequest(request, response);

			} catch (AuthenticationException ae) {

				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.getOutputStream().write("ERROR (401): Invalid user or password.\n".getBytes("UTF-8"));
				return;
			}

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("Unable to examine request", fex);
		} catch (IOException ioex) {
			logger.warn("Unable to send response", ioex);
		}

		// something went wrong, but we don't know what...
		if (securityContext == null) {
			logger.warn("No SecurityContext, aborting.");
			return;
		}

		try {

			if (securityContext.getUser(false) == null && !Settings.UploadAllowAnonymous.getValue()) {

				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				response.getOutputStream().write("ERROR (401): Anonymous uploads forbidden.\n".getBytes("UTF-8"));

				return;
			}

			// Ensure access mode is frontend
			securityContext.setAccessMode(AccessMode.Frontend);

			request.setCharacterEncoding("UTF-8");

			// Important: Set character encoding before calling response.getWriter() !!, see Servlet Spec 5.4
			response.setCharacterEncoding("UTF-8");

			// don't continue on redirects
			if (response.getStatus() == 302) {
				return;
			}

			final String pathInfo = request.getPathInfo();
			String type = null;

			if (StringUtils.isNotBlank(pathInfo)) {

				type = SchemaHelper.normalizeEntityName(StringUtils.stripStart(pathInfo.trim(), "/"));

			}

			uploader.setFileSizeMax(MEGABYTE * Settings.UploadMaxFileSize.getValue());
			uploader.setSizeMax(MEGABYTE * Settings.UploadMaxRequestSize.getValue());

			response.setContentType("text/html");

			List<FileItem> fileItemsList = uploader.parseRequest(request);
			Iterator<FileItem> fileItemsIterator = fileItemsList.iterator();

			final Map<String, Object> params = new HashMap<>();

			while (fileItemsIterator.hasNext()) {

				final FileItem item = fileItemsIterator.next();

				if (item.isFormField()) {

					final String fieldName = item.getFieldName();

					if (REDIRECT_AFTER_UPLOAD_PARAMETER.equals(fieldName)) {

						redirectUrl = item.getString();

					} else if (APPEND_UUID_ON_REDIRECT_PARAMETER.equals(fieldName)) {

						appendUuidOnRedirect = "true".equalsIgnoreCase(item.getString());

					} else if (UPLOAD_FOLDER_PATH_PARAMETER.equals(fieldName)) {

						path = item.getString();
						
					} else {

						params.put(item.getFieldName(), item.getString());
					}

				} else {

					try {

						final String contentType = item.getContentType();
						boolean isImage = (contentType != null && contentType.startsWith("image"));
						boolean isVideo = (contentType != null && contentType.startsWith("video"));

						// Override type from path info
						if (params.containsKey(NodeInterface.type.jsonName())) {
							type = (String) params.get(NodeInterface.type.jsonName());
						}

						Class cls = null;
						if (type != null) {

							cls = SchemaHelper.getEntityClassForRawType(type);

						}
						
						if (cls == null) {

							if (isImage) {

								cls = org.structr.dynamic.Image.class;

							} else if (isVideo) {

								cls = SchemaHelper.getEntityClassForRawType("VideoFile");
								if (cls == null) {

									logger.warn("Unable to create entity of type VideoFile, class is not defined.");
								}

							} else {

								cls = org.structr.dynamic.File.class;
							}
						}
						
						if (cls != null) {
							type = cls.getSimpleName();
						}

						final String name = item.getName().replaceAll("\\\\", "/");
						FileBase newFile  = null;
						String uuid       = null;
						boolean retry     = true;

						while (retry) {

							retry = false;

							try (final Tx tx = StructrApp.getInstance().tx()) {

								newFile = FileHelper.createFile(securityContext, IOUtils.toByteArray(item.getInputStream()), contentType, cls);

								final PropertyMap changedProperties = new PropertyMap();

								changedProperties.put(AbstractNode.name, PathHelper.getName(name));
								changedProperties.putAll(PropertyMap.inputTypeToJavaType(securityContext, cls, params));
								
								// Update type as it could have changed
								changedProperties.put(AbstractNode.type, type);

								Folder uploadFolder = null;
								final String defaultUploadFolderConfigValue = Settings.DefaultUploadFolder.getValue();
								
								// If a path attribute was sent, create all folders on the fly
								if (path != null) {
									
									uploadFolder = FileHelper.createFolderPath(securityContext, path);
								
								} else if (StringUtils.isNotBlank(defaultUploadFolderConfigValue)) {

									uploadFolder = FileHelper.createFolderPath(SecurityContext.getSuperUserInstance(), defaultUploadFolderConfigValue);

								}
								
								// can only happen if the configuration value is invalid or maps to the root folder
								if (uploadFolder != null) {

									changedProperties.put(FileBase.hasParent, true);
									changedProperties.put(FileBase.parent, uploadFolder);
								}

								newFile.setProperties(securityContext, changedProperties);

								uuid = newFile.getUuid();

								tx.success();

							} catch (RetryException rex) {
								retry = true;
							}
						}

						// since the transaction can be repeated, we need to make sure that
						// only the actual existing file creates a UUID output
						if (newFile != null) {

							// upload trigger
							newFile.notifyUploadCompletion();

							// send redirect to allow form-based file upload without JavaScript..
							if (StringUtils.isNotBlank(redirectUrl)) {

								if (appendUuidOnRedirect) {

									response.sendRedirect(redirectUrl + uuid);

								} else {

									response.sendRedirect(redirectUrl);
								}

							} else {

								// Just write out the uuids of the new files
								response.getWriter().write(uuid);
							}

						}

					} catch (IOException ex) {
						logger.warn("Could not upload file", ex);
					}
				}
			}

		} catch (Throwable t) {

			final String content;

			if (t instanceof FrameworkException) {

				final FrameworkException fex = (FrameworkException) t;
				logger.error(fex.toString());
				content = errorPage(fex);

			} else {

				logger.error("Exception while processing upload request", t);
				content = errorPage(t);
			}

			try {
				final ServletOutputStream out = response.getOutputStream();
				IOUtils.write(content, out);
			} catch (IOException ex) {
				logger.error("Could not write to response", ex);
		}
	}
	}

	@Override
	protected void doPut(final HttpServletRequest request, final HttpServletResponse response) throws ServletException {

		try (final Tx tx = StructrApp.getInstance().tx(true, false, false)) {

			final String uuid = PathHelper.getName(request.getPathInfo());

			if (uuid == null) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.getOutputStream().write("URL path doesn't end with UUID.\n".getBytes("UTF-8"));
				return;
			}

			Matcher matcher = threadLocalUUIDMatcher.get();
			matcher.reset(uuid);

			if (!matcher.matches()) {
				response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				response.getOutputStream().write("ERROR (400): URL path doesn't end with UUID.\n".getBytes("UTF-8"));
				return;
			}

			final SecurityContext securityContext = getConfig().getAuthenticator().initializeAndExamineRequest(request, response);

			// Ensure access mode is frontend
			securityContext.setAccessMode(AccessMode.Frontend);

			request.setCharacterEncoding("UTF-8");

			// Important: Set character encoding before calling response.getWriter() !!, see Servlet Spec 5.4
			response.setCharacterEncoding("UTF-8");

			// don't continue on redirects
			if (response.getStatus() == 302) {
				return;
			}

			uploader.setFileSizeMax(MEGABYTE * Settings.UploadMaxFileSize.getValue());
			uploader.setSizeMax(MEGABYTE * Settings.UploadMaxRequestSize.getValue());

			List<FileItem> fileItemsList = uploader.parseRequest(request);
			Iterator<FileItem> fileItemsIterator = fileItemsList.iterator();

			while (fileItemsIterator.hasNext()) {

				final FileItem fileItem = fileItemsIterator.next();

				try {

					final GraphObject node = StructrApp.getInstance().getNodeById(uuid);

					if (node == null) {

						response.setStatus(HttpServletResponse.SC_NOT_FOUND);
						response.getOutputStream().write("ERROR (404): File not found.\n".getBytes("UTF-8"));

					}

					if (node instanceof org.structr.web.entity.AbstractFile) {

						final org.structr.dynamic.File file = (org.structr.dynamic.File) node;
						if (file.isGranted(Permission.write, securityContext)) {

							FileHelper.writeToFile(file, fileItem.getInputStream());
							file.increaseVersion();

							// upload trigger
							file.notifyUploadCompletion();

						} else {

							response.setStatus(HttpServletResponse.SC_FORBIDDEN);
							response.getOutputStream().write("ERROR (403): Write access forbidden.\n".getBytes("UTF-8"));

						}
					}

				} catch (IOException ex) {
					logger.warn("Could not write to file", ex);
				}

			}

			tx.success();

		} catch (FrameworkException | IOException | FileUploadException t) {

			logger.error("Exception while processing request", t);
			UiAuthenticator.writeInternalServerError(response);
		}
	}

	private String errorPage(final Throwable t) {
		return "<html><head><title>Error in Upload</title></head><body><h1>Error in Upload</h1><p>" + t.toString() + "</p>\n<!--" + ExceptionUtils.getStackTrace(t) + "--></body></html>";
	}
}
