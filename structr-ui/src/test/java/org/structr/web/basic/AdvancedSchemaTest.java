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
package org.structr.web.basic;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import java.util.LinkedList;
import java.util.List;
import static junit.framework.TestCase.fail;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.SchemaNode;
import org.structr.core.entity.SchemaProperty;
import org.structr.core.entity.SchemaView;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.web.auth.UiAuthenticator;
import static org.structr.web.basic.ResourceAccessTest.createResourceAccess;



public class AdvancedSchemaTest extends FrontendTest {

	private static final Logger logger = LoggerFactory.getLogger(AdvancedSchemaTest.class.getName());

	@Test
	public void test01InheritanceOfFileAttributesToImage() {


		try (final Tx tx = app.tx()) {

			createAdminUser();
			createResourceAccess("_schema", UiAuthenticator.AUTH_USER_GET);
			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			// Add String property "testFile" to built-in File class
			SchemaNode fileNodeDef = app.nodeQuery(SchemaNode.class).andName("File").getFirst();

			SchemaProperty testFileProperty = app.create(SchemaProperty.class);

			final PropertyMap testFileProperties = new PropertyMap();
			testFileProperties.put(SchemaProperty.name, "testFile");
			testFileProperties.put(SchemaProperty.propertyType, "String");
			testFileProperties.put(SchemaProperty.schemaNode, fileNodeDef);
			testFileProperty.setProperties(testFileProperty.getSecurityContext(), testFileProperties);

			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			RestAssured

				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.headers("X-User", ADMIN_USERNAME , "X-Password", ADMIN_PASSWORD)

				.expect()
					.statusCode(200)

					.body("result",	                   hasSize(34))
					.body("result[33].jsonName",       equalTo("testFile"))
					.body("result[33].declaringClass", equalTo("_FileHelper"))

				.when()
					.get("/_schema/File/ui");

			tx.success();

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");
		}

		try (final Tx tx = app.tx()) {

			RestAssured

				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.headers("X-User", ADMIN_USERNAME , "X-Password", ADMIN_PASSWORD)

				.expect()
					.statusCode(200)

					.body("result",	                   hasSize(44))
					.body("result[43].jsonName",       equalTo("testFile"))
					.body("result[43].declaringClass", equalTo("_FileHelper"))

				.when()
					.get("/_schema/Image/ui");

			tx.success();

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");
		}
	}

	@Test
	public void test02InheritanceOfFileAttributesToSubclass() {

		try (final Tx tx = app.tx()) {

			createAdminUser();
			createResourceAccess("_schema", UiAuthenticator.AUTH_USER_GET);
			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			SchemaNode fileNodeDef = app.nodeQuery(SchemaNode.class).andName("File").getFirst();

			SchemaProperty testFileProperty = app.create(SchemaProperty.class);

			final PropertyMap changedProperties = new PropertyMap();
			changedProperties.put(SchemaProperty.name, "testFile");
			changedProperties.put(SchemaProperty.propertyType, "String");
			changedProperties.put(SchemaProperty.schemaNode, fileNodeDef);
			testFileProperty.setProperties(testFileProperty.getSecurityContext(), changedProperties);

			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			// Create new schema node for dynamic class SubFile which extends File
			SchemaNode subFile = app.create(SchemaNode.class);

			final PropertyMap subFileProperties = new PropertyMap();
			subFileProperties.put(SchemaNode.name, "SubFile");
			subFileProperties.put(SchemaNode.extendsClass, "File");
			subFile.setProperties(subFile.getSecurityContext(), subFileProperties);


			// Add String property "testSubFile" to new dynamic class
			SchemaProperty testFileProperty = app.create(SchemaProperty.class);

			final PropertyMap testFileProperties = new PropertyMap();
			testFileProperties.put(SchemaProperty.name, "testSubFile");
			testFileProperties.put(SchemaProperty.propertyType, "String");
			testFileProperties.put(SchemaProperty.schemaNode, subFile);
			testFileProperty.setProperties(testFileProperty.getSecurityContext(), testFileProperties);

			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}


		try (final Tx tx = app.tx()) {

			RestAssured

				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.headers("X-User", ADMIN_USERNAME , "X-Password", ADMIN_PASSWORD)

				.expect()
					.statusCode(200)

					.body("result",	                   hasSize(35))
					.body("result[33].jsonName",       equalTo("testSubFile"))
					.body("result[33].declaringClass", equalTo("SubFile"))
					.body("result[34].jsonName",       equalTo("testFile"))
					.body("result[34].declaringClass", equalTo("_FileHelper"))

				.when()
					.get("/_schema/SubFile/ui");

			tx.success();

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");
		}

	}

	@Test
	public void test03InheritanceOfFileAttributesToSubclassOfImage() {

		try (final Tx tx = app.tx()) {

			createAdminUser();
			createResourceAccess("_schema", UiAuthenticator.AUTH_USER_GET);
			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			SchemaNode fileNodeDef = app.nodeQuery(SchemaNode.class).andName("File").getFirst();

			SchemaProperty testFileProperty = app.create(SchemaProperty.class);

			final PropertyMap testFileProperties = new PropertyMap();
			testFileProperties.put(SchemaProperty.name, "testFile");
			testFileProperties.put(SchemaProperty.propertyType, "String");
			testFileProperties.put(SchemaProperty.schemaNode, fileNodeDef);
			testFileProperty.setProperties(testFileProperty.getSecurityContext(), testFileProperties);

			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		try (final Tx tx = app.tx()) {

			// Create new schema node for dynamic class SubFile which extends File
			SchemaNode subFile = app.create(SchemaNode.class);

			final PropertyMap subFileProperties = new PropertyMap();
			subFileProperties.put(SchemaNode.name, "SubFile");
			subFileProperties.put(SchemaNode.extendsClass, "Image");
			subFile.setProperties(subFile.getSecurityContext(), subFileProperties);


			// Add String property "testSubFile" to new dynamic class
			SchemaProperty testFileProperty = app.create(SchemaProperty.class);

			final PropertyMap testFileProperties = new PropertyMap();
			testFileProperties.put(SchemaProperty.name, "testSubFile");
			testFileProperties.put(SchemaProperty.propertyType, "String");
			testFileProperties.put(SchemaProperty.schemaNode, subFile);
			testFileProperty.setProperties(testFileProperty.getSecurityContext(), testFileProperties);

			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}


		try (final Tx tx = app.tx()) {

			RestAssured

				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
					.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
					.headers("X-User", ADMIN_USERNAME , "X-Password", ADMIN_PASSWORD)

				.expect()
					.statusCode(200)

					.body("result",	                   hasSize(45))
					.body("result[43].jsonName",       equalTo("testSubFile"))
					.body("result[43].declaringClass", equalTo("SubFile"))
					.body("result[44].jsonName",       equalTo("testFile"))
					.body("result[44].declaringClass", equalTo("_FileHelper"))

				.when()
					.get("/_schema/SubFile/ui");

			tx.success();

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");
		}
	}

	@Test
	public void test04SchemaPropertyOrder() {

		SchemaView testView = null;

		try (final Tx tx = app.tx()) {

			// create test type
			final SchemaNode test = app.create(SchemaNode.class, "Test");

			// create view with sort order
			testView = app.create(SchemaView.class,
				new NodeAttribute<>(SchemaView.name, "test"),
				new NodeAttribute<>(SchemaView.schemaNode, test),
				new NodeAttribute<>(SchemaView.sortOrder, "one, two, three, four, id, type, name"),
				new NodeAttribute<>(SchemaView.nonGraphProperties, "id, type, name")
			);

			final List<SchemaView> list = new LinkedList<>();
			list.add(testView);

			// create properties
			app.create(SchemaProperty.class,
				new NodeAttribute<>(SchemaProperty.schemaNode, test),
				new NodeAttribute<>(SchemaProperty.schemaViews, list),
				new NodeAttribute<>(SchemaProperty.propertyType, "String"),
				new NodeAttribute<>(SchemaProperty.name, "one")
			);

			app.create(SchemaProperty.class,
				new NodeAttribute<>(SchemaProperty.schemaNode, test),
				new NodeAttribute<>(SchemaProperty.schemaViews, list),
				new NodeAttribute<>(SchemaProperty.propertyType, "String"),
				new NodeAttribute<>(SchemaProperty.name, "two")
			);

			app.create(SchemaProperty.class,
				new NodeAttribute<>(SchemaProperty.schemaNode, test),
				new NodeAttribute<>(SchemaProperty.schemaViews, list),
				new NodeAttribute<>(SchemaProperty.propertyType, "String"),
				new NodeAttribute<>(SchemaProperty.name, "three")
			);

			app.create(SchemaProperty.class,
				new NodeAttribute<>(SchemaProperty.schemaNode, test),
				new NodeAttribute<>(SchemaProperty.schemaViews, list),
				new NodeAttribute<>(SchemaProperty.propertyType, "String"),
				new NodeAttribute<>(SchemaProperty.name, "four")
			);

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
		}

		final Class type            = StructrApp.getConfiguration().getNodeEntityClass("Test");
		final List<PropertyKey> list = new LinkedList<>(StructrApp.getConfiguration().getPropertySet(type, "test"));

		Assert.assertEquals("Invalid number of properties in sorted view", 7, list.size());
		Assert.assertEquals("one",   list.get(0).dbName());
		Assert.assertEquals("two",   list.get(1).dbName());
		Assert.assertEquals("three", list.get(2).dbName());
		Assert.assertEquals("four",  list.get(3).dbName());
		Assert.assertEquals("id",    list.get(4).dbName());
		Assert.assertEquals("type",  list.get(5).dbName());
		Assert.assertEquals("name",  list.get(6).dbName());

		try (final Tx tx = app.tx()) {

			// modify sort order
			testView.setProperty(SchemaView.sortOrder, "type, one, id, two, three, four, name");

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
		}

		final List<PropertyKey> list2 = new LinkedList<>(StructrApp.getConfiguration().getPropertySet(type, "test"));

		Assert.assertEquals("Invalid number of properties in sorted view", 7, list2.size());
		Assert.assertEquals("type",  list2.get(0).dbName());
		Assert.assertEquals("one",   list2.get(1).dbName());
		Assert.assertEquals("id",    list2.get(2).dbName());
		Assert.assertEquals("two",   list2.get(3).dbName());
		Assert.assertEquals("three", list2.get(4).dbName());
		Assert.assertEquals("four",  list2.get(5).dbName());
		Assert.assertEquals("name",  list2.get(6).dbName());

	}
}
