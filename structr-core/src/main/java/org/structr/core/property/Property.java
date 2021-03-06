/**
 * Copyright (C) 2010-2017 Structr GmbH
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
package org.structr.core.property;

import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.Predicate;
import org.structr.api.graph.Node;
import org.structr.api.graph.Relationship;
import org.structr.api.index.Index;
import org.structr.api.search.Occurrence;
import org.structr.bolt.index.AbstractCypherIndex;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Services;
import org.structr.core.app.Query;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.NodeService;
import org.structr.core.graph.search.PropertySearchAttribute;
import org.structr.core.graph.search.SearchAttribute;

/**
 * Abstract base class for all property types.
 *
 *
 */
public abstract class Property<T> implements PropertyKey<T> {

	private static final Logger logger             = LoggerFactory.getLogger(Property.class.getName());
	private static final Pattern rangeQueryPattern = Pattern.compile("\\[(.+) TO (.+)\\]");

	protected Class<? extends GraphObject> declaringClass  = null;
	protected T defaultValue                               = null;
	protected boolean readOnly                             = false;
	protected boolean systemInternal                       = false;
	protected boolean writeOnce                            = false;
	protected boolean unvalidated                          = false;
	protected boolean indexed                              = false;
	protected boolean indexedPassively                     = false;
	protected boolean indexedWhenEmpty                     = false;
	protected boolean compound                             = false;
	protected boolean unique                               = false;
	protected boolean notNull                              = false;
	protected boolean dynamic                              = false;
	protected boolean isCMISProperty                       = false;
	protected String dbName                                = null;
	protected String jsonName                              = null;
	protected String format                                = null;
	protected String readFunction                          = null;
	protected String writeFunction                         = null;

	private boolean requiresSynchronization                = false;

	protected Property(final String name) {
		this(name, name);
	}

	protected Property(final String jsonName, final String dbName) {
		this(jsonName, dbName, null);
	}

	protected Property(final String jsonName, final String dbName, final T defaultValue) {
		this.defaultValue = defaultValue;
		this.jsonName = jsonName;
		this.dbName = dbName;
	}

	@Override
	public abstract Object fixDatabaseProperty(final Object value);

	/**
	 * Use this method to mark a property as being unvalidated. This
	 * method will cause no callbacks to be executed when only
	 * unvalidated properties are modified.
	 *
	 * @return  the Property to satisfy the builder pattern
	 */
	public Property<T> unvalidated() {
		this.unvalidated = true;
		return this;
	}

	/**
	 * Use this method to mark a property as being read-only.
	 *
	 * @return the Property to satisfy the builder pattern
	 */
	public Property<T> readOnly() {
		this.readOnly = true;
		return this;
	}

	/**
	 * Use this method to mark a property as being system-internal.
	 *
	 * @return the Property to satisfy the builder pattern
	 */
	public Property<T> systemInternal() {
		this.systemInternal = true;
		return this;
	}

	/**
	 * Use this method to mark a property as being write-once.
	 *
	 * @return the Property to satisfy the builder pattern
	 */
	public Property<T> writeOnce() {
		this.writeOnce = true;
		return this;
	}

	/**
	 * Use this method to mark a property as being unique. Please note that
	 * using this method will not actually cause a uniqueness check, just
	 * notify the system that this property should be treated as having a
	 * unique value.
	 *
	 * @return the Property to satisfy the builder pattern
	 */
	public Property<T> unique() {
		this.unique                  = true;
		this.requiresSynchronization = true;
		return this;
	}

	/**
	 * Use this method to mark a property as being unique. Please note that
	 * using this method will not actually cause a uniqueness check, just
	 * notify the system that this property should be treated as having a
	 * unique value.
	 *
	 * @return the Property to satisfy the builder pattern
	 */
	public Property<T> compound() {
		this.compound                = true;
		this.requiresSynchronization = true;
		return this;
	}

	/**
	 * Use this method to mark a property as being not-null. Please note that
	 * using this method will not actually cause a not-null check, just
	 * notify the system that this property should be treated as such.
	 *
	 * @return the Property to satisfy the builder pattern
	 */
	public Property<T> notNull() {
		this.notNull = true;
		return this;
	}

	@Override
	public Property<T> indexed() {

		this.indexed = true;

		return this;
	}

	@Override
	public Property<T> passivelyIndexed() {

		this.indexedPassively = true;
		this.indexed = true;

		return this;
	}

	@Override
	public Property<T> indexedWhenEmpty() {

		passivelyIndexed();
		this.indexedWhenEmpty = true;

		return this;
	}

	@Override
	public Property<T> cmis() {

		this.isCMISProperty = true;

		return this;
	}

	@Override
	public boolean requiresSynchronization() {
		return requiresSynchronization;
	}

	@Override
	public String getSynchronizationKey() {

		if (declaringClass != null) {

			return declaringClass.getSimpleName() + "." + dbName;
		}

		return "GraphObject." + dbName;
	}

	@Override
	public void setDeclaringClass(final Class declaringClass) {
		this.declaringClass = declaringClass;
	}

	@Override
	public void registrationCallback(final Class type) {
	}

	@Override
	public Class getDeclaringClass() {
		return declaringClass;
	}

	@Override
	public String toString() {
		return jsonName();
	}

	@Override
	public String dbName() {
		return dbName;
	}

	@Override
	public String jsonName() {
		return jsonName;
	}

	@Override
	public void dbName(final String dbName) {
		this.dbName = dbName;
	}

	@Override
	public void jsonName(final String jsonName) {
		this.jsonName = jsonName;
	}

	@Override
	public Property<T> defaultValue(final T defaultValue) {
		this.defaultValue = defaultValue;
		return this;
	}

	@Override
	public T defaultValue() {
		return defaultValue;
	}

	@Override
	public String format() {
		return format;
	}

	@Override
	public Property<T> format(final String format) {
		this.format = format;
		return this;
	}

	@Override
	public Property<T> unique(final boolean unique) {
		this.unique = unique;
		return this;
	}

	@Override
	public Property<T> notNull(final boolean notNull) {
		this.notNull = notNull;
		return this;
	}

	@Override
	public Property<T> dynamic() {
		this.dynamic = true;
		return this;
	}

	@Override
	public String readFunction() {
		return readFunction;
	}

	@Override
	public Property<T> readFunction(final String readFunction) {
		this.readFunction = readFunction;
		return this;
	}

	@Override
	public String writeFunction() {
		return writeFunction;
	}

	@Override
	public Property<T> writeFunction(final String writeFunction) {
		this.writeFunction = writeFunction;
		return this;
	}

	@Override
	public int hashCode() {

		// make hashCode funtion work for subtypes that override jsonName() etc. as well
		if (dbName() != null && jsonName() != null) {
			return (dbName().hashCode() * 31) + jsonName().hashCode();
		}

		if (dbName() != null) {
			return dbName().hashCode();
		}

		if (jsonName() != null) {
			return jsonName().hashCode();
		}

		// TODO: check if it's ok if null key is not unique
		return super.hashCode();
	}

	@Override
	public boolean equals(final Object o) {

		if (o instanceof PropertyKey) {

			return o.hashCode() == hashCode();
		}

		return false;
	}

	@Override
	public boolean isUnvalidated() {
		return unvalidated;
	}

	@Override
	public boolean isReadOnly() {
		return readOnly;
	}

	@Override
	public boolean isSystemInternal() {
		return systemInternal;
	}

	@Override
	public boolean isWriteOnce() {
		return writeOnce;
	}

	@Override
	public boolean isIndexed() {
		return indexed;
	}

	@Override
	public boolean isPassivelyIndexed() {
		return indexedPassively;
	}

	@Override
	public boolean isIndexedWhenEmpty() {
		return indexedWhenEmpty;
	}

	@Override
	public boolean isCompound() {
		return compound;
	}

	@Override
	public boolean isUnique() {
		return unique;
	}

	@Override
	public boolean isNotNull() {
		return notNull;
	}

	@Override
	public boolean isDynamic() {
		return dynamic;
	}
	
	/**
	 * Default implementation of the existing index() method.
	 * 
	 * Allows property classes to override the method and thus
	 * to decide about the value to index.
	 * 
	 * Default implementation
	 * 
	 * @param obj 
	 */
	@Override
	public void index(final GraphObject obj) {
		index(obj, obj.getProperty(this));
	}
	
	@Override
	public void index(final GraphObject entity, final Object value) {

		if (entity instanceof AbstractNode) {

			final NodeService nodeService = Services.getInstance().getService(NodeService.class);
			final AbstractNode node       = (AbstractNode)entity;
			final Node dbNode             = node.getNode();
			final Index<Node> index       = nodeService.getNodeIndex();

			if (index != null) {

				try {

					index.remove(dbNode, dbName);

					if (value != null || isIndexedWhenEmpty()) {
						index.add(dbNode, dbName, value, valueType());
					}

				} catch (Throwable t) {

					logger.info("Unable to index property with dbName {} and value {} of type {} on {}: {}", new Object[] { dbName, value, this.getClass().getSimpleName(), entity, t } );
					logger.warn("", t);
				}
			}

		} else if (entity instanceof AbstractRelationship) {

			final NodeService nodeService   = Services.getInstance().getService(NodeService.class);
			final AbstractRelationship rel  = (AbstractRelationship)entity;
			final Relationship dbRel        = rel.getRelationship();
			final Index<Relationship> index = nodeService.getRelationshipIndex();

			if (index != null) {

				try {

					index.remove(dbRel, dbName);

					if (value != null || isIndexedWhenEmpty()) {
						index.add(dbRel, dbName, value, valueType());
					}

				} catch (Throwable t) {

					logger.info("Unable to index property with dbName {} and value {} of type {} on {}: {}", new Object[] { dbName, value, this.getClass().getSimpleName(), entity, t } );
				}
			}
		}
	}

	@Override
	public boolean indexable(final Object value) {

		if (value != null) {

			final Class valueType = valueType();
			if (valueType != null) {

				// indexable indicated by value type
				if (AbstractCypherIndex.INDEXABLE.contains(valueType)) {

					return true;
				}

				if (valueType.equals(Date.class)) {
					return true;
				}

				if (valueType.isEnum()) {
					return true;
				}

				if (valueType.isArray()) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public SearchAttribute getSearchAttribute(final SecurityContext securityContext, final Occurrence occur, final T searchValue, final boolean exactMatch, final Query query) {
		return new PropertySearchAttribute(this, searchValue, occur, exactMatch);
	}

	@Override
	public void extractSearchableAttribute(final SecurityContext securityContext, final HttpServletRequest request, final boolean exactMatch, final Query query) throws FrameworkException {

		final String[] searchValues = request.getParameterValues(jsonName());
		if (searchValues != null) {

			for (String searchValue : searchValues) {

				determineSearchType(securityContext, searchValue, exactMatch, query);
			}
		}
	}

	@Override
	public T convertSearchValue(final SecurityContext securityContext, final String requestParameter) throws FrameworkException {

		PropertyConverter inputConverter = inputConverter(securityContext);
		Object convertedSearchValue      = requestParameter;

		if (inputConverter != null) {

			convertedSearchValue = inputConverter.convert(convertedSearchValue);
		}

		return (T)convertedSearchValue;
	}

	@Override
	public int getProcessingOrderPosition() {
		return 0;
	}

	// ----- CMIS support -----
	@Override
	public PropertyType getDataType() {
		return null;
	}

	@Override
	public boolean isCMISProperty() {
		return isCMISProperty;
	}

	// ----- interface Comparable -----
	@Override
	public int compareTo(final PropertyKey other) {
		return dbName().compareTo(other.dbName());
	}

	// ----- protected methods -----
	protected boolean multiValueSplitAllowed() {
		return true;
	}

	protected final String removeQuotes(final String searchValue) {
		String resultStr = searchValue;

		if (resultStr.contains("\"")) {
			resultStr = resultStr.replaceAll("[\"]+", "");
		}

		if (resultStr.contains("'")) {
			resultStr = resultStr.replaceAll("[']+", "");
		}

		return resultStr;
	}

	protected void determineSearchType(final SecurityContext securityContext, final String requestParameter, final boolean exactMatch, final Query query) throws FrameworkException {

		if (StringUtils.startsWith(requestParameter, "[") && StringUtils.endsWith(requestParameter, "]")) {

			// check for existance of range query string
			Matcher matcher = rangeQueryPattern.matcher(requestParameter);
			if (matcher.matches()) {

				if (matcher.groupCount() == 2) {

					String rangeStart = matcher.group(1);
					String rangeEnd = matcher.group(2);

					PropertyConverter inputConverter = inputConverter(securityContext);
					Object rangeStartConverted = rangeStart;
					Object rangeEndConverted = rangeEnd;

					if (inputConverter != null) {

						rangeStartConverted = inputConverter.convert(rangeStartConverted);
						rangeEndConverted = inputConverter.convert(rangeEndConverted);
					}

					query.andRange(this, rangeStartConverted, rangeEndConverted);

					return;
				}

				logger.warn("Unable to determine range query bounds for {}", requestParameter);

			} else {

				if ("[]".equals(requestParameter)) {

					if (isIndexedWhenEmpty()) {

						// requestParameter contains only [],
						// which we use as a "not-blank" selector
						query.notBlank(this);

						return;

					} else {

						throw new FrameworkException(400, "PropertyKey " + jsonName() + " must be indexedWhenEmpty() to be used in not-blank search query.");
					}

				} else {

					throw new FrameworkException(422, "Invalid range pattern.");
				}
			}
 		}

		if (requestParameter.contains(",") && requestParameter.contains(";")) {
			throw new FrameworkException(422, "Mixing of AND and OR not allowed in request parameters");
		}

		if (requestParameter.contains(";")) {

			if (multiValueSplitAllowed()) {

				// descend into a new group
				query.and();

				for (final String part : requestParameter.split("[;]+")) {

					query.or(this, convertSearchValue(securityContext, part), exactMatch);
				}

				// ascend to the last group
				query.parent();

			} else {

				query.or(this, convertSearchValue(securityContext, requestParameter), exactMatch);
			}

		} else {

			query.and(this, convertSearchValue(securityContext, requestParameter), exactMatch);
		}
	}

	protected <T extends NodeInterface> Set<T> getRelatedNodesReverse(final SecurityContext securityContext, final NodeInterface obj, final Class destinationType, final Predicate<GraphObject> predicate) {
		// this is the default implementation
		return Collections.emptySet();
	}
}
