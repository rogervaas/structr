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
package org.structr.websocket.command;

import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.WebSocketMessage;

//~--- classes ----------------------------------------------------------------

/**
 * Websocket command to retrieve a set of properties for a single graph object by id.
 *
 */
public class GetPropertiesCommand extends AbstractCommand {

	private static final Logger logger = LoggerFactory.getLogger(GetPropertiesCommand.class.getName());

	static {

		StructrWebSocket.addCommand(GetPropertiesCommand.class);

	}

	@Override
	public void processMessage(final WebSocketMessage webSocketData) {

		final SecurityContext securityContext  = getWebSocket().getSecurityContext();
		final String properties                = (String) webSocketData.getNodeData().get("properties");

		if (properties != null) {
			securityContext.setCustomView(StringUtils.split(properties, ","));
		}

		final App app = StructrApp.getInstance(securityContext);

		try {

			final GraphObject graphObject = (GraphObject) app.get(webSocketData.getId());

			webSocketData.setResult(Arrays.asList(graphObject));

			// send only over local connection (no broadcast)
			getWebSocket().send(webSocketData, true);

		} catch (FrameworkException fex) {
			logger.warn("Unable to get graph object", fex);
		}

	}

	//~--- get methods ----------------------------------------------------

	@Override
	public String getCommand() {
		return "GET_PROPERTIES";
	}
}
