/**
 * Copyright (C) 2010-2016 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.common;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.NotInTransactionException;
import org.structr.api.graph.RelationshipType;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.entity.DynamicResourceAccess;
import org.structr.core.entity.GenericNode;
import org.structr.core.entity.GenericRelationship;
import org.structr.core.entity.Localization;
import org.structr.core.entity.Location;
import org.structr.core.entity.MailTemplate;
import org.structr.core.entity.Person;
import org.structr.core.entity.PropertyAccess;
import org.structr.core.entity.ResourceAccess;
import org.structr.core.entity.SchemaNode;
import org.structr.core.entity.SchemaRelationshipNode;
import org.structr.core.entity.TestNine;
import org.structr.core.entity.TestOne;
import org.structr.core.entity.TestSeven;
import org.structr.core.entity.TestTwo;
import org.structr.core.entity.relationship.NodeHasLocation;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyMap;

//~--- classes ----------------------------------------------------------------

/**
 * Test basic create operations with graph objects (nodes, relationships)
 *
 * All tests are executed in superuser context
 *
 *
 */
public class CreateGraphObjectsTest extends StructrTest {

	private static final Logger logger = LoggerFactory.getLogger(CreateGraphObjectsTest.class.getName());

	public void test01CreateNode() {

		try {

			try {

				// Create node out of transaction => should give a NotInTransactionException
				app.create(TestOne.class);
				fail("Should have raised a NotInTransactionException");
			} catch (NotInTransactionException e) {
			}

			try {

				// Try to create node without parameters => should fail
				app.create(TestOne.class);
				fail("Should have raised a NotInTransactionException");
			} catch (NotInTransactionException e) {}

			AbstractNode node = null;

			try (final Tx tx = app.tx()) {

				node = app.create(TestOne.class);
				tx.success();
			}

			assertTrue(node != null);
			assertTrue(node instanceof TestOne);

		} catch (FrameworkException ex) {

			ex.printStackTrace();
			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}
	public void test02CreateNodeWithExistingUuid() {

		try {

			final PropertyMap props = new PropertyMap();
			TestOne node            = null;

			final String uuid = StringUtils.replace(UUID.randomUUID().toString(), "-", "");

			props.put(GraphObject.id, uuid);

			try (final Tx tx = app.tx()) {

				node = app.create(TestOne.class, props);
				tx.success();
			}

			try (final Tx tx = app.tx()) {

				assertTrue(node != null);
				assertTrue(node instanceof TestOne);
				assertEquals(node.getUuid(), uuid);
			}

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}

	public void test03CreateRelationship() {

		try {

			final List<GenericNode> nodes = createTestNodes(GenericNode.class, 2);
			final NodeInterface startNode = nodes.get(0);
			final NodeInterface endNode   = nodes.get(1);
			NodeHasLocation rel           = null;

			assertTrue(startNode != null);
			assertTrue(endNode != null);

			try (final Tx tx = app.tx()) {

				rel = app.create(startNode, endNode, NodeHasLocation.class);
				tx.success();
			}

			try (final Tx tx = app.tx()) {

				assertEquals(startNode.getUuid(), rel.getSourceNodeId());
				assertEquals(endNode.getUuid(), rel.getTargetNodeId());
				assertEquals(RelType.IS_AT.name(), rel.getType());
				assertEquals(NodeHasLocation.class, rel.getClass());
			}

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}

	/**
	 * Create a node for each configured entity class and check the type
	 */
	public void test04CheckNodeEntities() {

		AccessControlTest.clearResourceAccess();

		final PropertyMap props = new PropertyMap();

		try (final Tx tx = app.tx()) {

			List<Class> entityList = Collections.EMPTY_LIST;

			try {

				entityList = getClasses("org.structr.core.entity");

			} catch (IOException | ClassNotFoundException ex) {

				logger.error("", ex);
			}

			assertTrue(entityList.contains(AbstractNode.class));
			assertTrue(entityList.contains(GenericNode.class));
			assertTrue(entityList.contains(Location.class));
			assertTrue(entityList.contains(Person.class));
			assertTrue(entityList.contains(ResourceAccess.class));
			assertTrue(entityList.contains(PropertyAccess.class));

			// Don't test these, it would fail due to violated constraints
			entityList.remove(TestTwo.class);
			entityList.remove(TestNine.class);
			entityList.remove(MailTemplate.class);
			entityList.remove(SchemaNode.class);
			entityList.remove(SchemaRelationshipNode.class);

			for (Class type : entityList) {

				// for (Entry<String, Class> entity : entities.entrySet()) {
				// Class entityClass = entity.getValue();
				if (AbstractNode.class.isAssignableFrom(type)) {

					// For TestSeven, fill mandatory fields
					if (type.equals(TestSeven.class)) {

						props.put(TestSeven.name, "TestSeven-0");

					}

					// For ResourceAccess, fill mandatory fields
					if (type.equals(ResourceAccess.class)) {

						props.put(ResourceAccess.signature, "/X");
						props.put(ResourceAccess.flags, 6L);

					}

					// For DynamicResourceAccess, fill mandatory fields
					if (type.equals(DynamicResourceAccess.class)) {

						props.put(DynamicResourceAccess.signature, "/Y");
						props.put(DynamicResourceAccess.flags, 6L);

					}

					// For PropertyAccess, fill mandatory fields
					if (type.equals(PropertyAccess.class)) {

						props.put(PropertyAccess.flags, 6L);

					}

					// For Localization, fill mandatory fields
					if (type.equals(Localization.class)) {

						props.put(Localization.name, "localizationKey");
						props.put(Localization.locale, "de_DE");

					}

					// For Location, set coordinates
					if (type.equals(Location.class)) {

						props.put(Location.latitude, 12.34);
						props.put(Location.longitude, 56.78);

					}

					logger.info("Creating node of type {}", type);

					NodeInterface node = app.create(type, props);

					assertTrue(type.getSimpleName().equals(node.getProperty(AbstractNode.type)));

					// Remove mandatory fields for ResourceAccess from props map
					if (type.equals(ResourceAccess.class)) {

						props.remove(ResourceAccess.signature);
						props.remove(ResourceAccess.flags);

					}

				}
			}

			tx.success();

		} catch (FrameworkException ex) {

			logger.error(ex.toString());

			logger.warn("", ex);

			fail("Unexpected exception");
		}
	}

	/**
	 * Create a node for each configured entity class and check the type
	 */
	public void test05CheckRelationshipEntities() {

		try (final Tx tx = app.tx()) {

			List<Class> entityList = null;

			try {

				entityList = getClasses("org.structr.core.entity");

			} catch (IOException | ClassNotFoundException ex) {

				LoggerFactory.getLogger(CreateGraphObjectsTest.class.getName()).error("", ex);
			}

			assertTrue(entityList.contains(AbstractRelationship.class));
			assertTrue(entityList.contains(GenericRelationship.class));

			for (Class entityClass : entityList) {

				// for (Entry<String, Class> entity : entities.entrySet()) {
				// Class entityClass = entity.getValue();
				if (AbstractRelationship.class.isAssignableFrom(entityClass)) {

					String type = entityClass.getSimpleName();

					logger.info("Creating relationship of type {}", type);

					List<GenericNode> nodes        = createTestNodes(GenericNode.class, 2);
					final NodeInterface startNode  = nodes.get(0);
					final NodeInterface endNode    = nodes.get(1);
					final RelationshipType relType = RelType.IS_AT;
					NodeHasLocation rel       = app.create(startNode, endNode, NodeHasLocation.class);

					assertTrue(rel != null);
					assertTrue(rel.getType().equals(relType.name()));

				}
			}

			tx.success();

		} catch (Throwable ex) {

			logger.warn("", ex);

			logger.error(ex.toString());
			fail("Unexpected exception");
		}
	}

	/**
	 * FIXME: this test is disabled, to be discussed!
	 *
	 * Creation of duplicate relationships is blocked.
	 *
	 * A relationship is considered duplicate if all of the following criteria are met:
	 *
	 * - same start node
	 * - same end node
	 * - same relationship type
	 * - same set of property keys and values
	 *
	public void test06DuplicateRelationships() {

		try {

			List<NodeInterface> nodes      = createTestNodes(GenericNode.class, 2);
			final NodeInterface startNode  = nodes.get(0);
			final NodeInterface endNode    = nodes.get(1);
			final PropertyMap props        = new PropertyMap();

			props.put(new StringProperty("foo"), "bar");
			props.put(new IntProperty("bar"), 123);
			transactionCommand.execute(new StructrTransaction() {

				@Override
				public Object execute() throws FrameworkException {

					LocationRelationship rel1 = createRelationshipCommand.execute(startNode, endNode, LocationRelationship.class, props);

					assertTrue(rel1 != null);

					createRelationshipCommand.execute(startNode, endNode, LocationRelationship.class, props);

					return null;

				}

			});

			fail("Creating a duplicate relationship should throw an exception.");


		} catch (FrameworkException ex) {
		}

	}
	 */

}
