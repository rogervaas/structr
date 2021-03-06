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
package org.structr.bolt;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.exceptions.NoSuchRecordException;
import org.neo4j.driver.v1.exceptions.TransientException;
import org.neo4j.driver.v1.types.Entity;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;
import org.structr.api.NativeResult;
import org.structr.api.NotFoundException;
import org.structr.api.QueryResult;
import org.structr.api.RetryException;
import org.structr.api.util.QueryUtils;
import org.structr.bolt.mapper.RecordLongMapper;
import org.structr.bolt.mapper.RecordNodeMapper;
import org.structr.bolt.mapper.RecordRelationshipMapper;
import org.structr.bolt.wrapper.EntityWrapper;
import org.structr.bolt.wrapper.StatementResultWrapper;

/**
 *
 */
public class SessionTransaction implements org.structr.api.Transaction {

	private final Set<EntityWrapper> modifiedEntities = new HashSet<>();
	private BoltDatabaseService db                    = null;
	private Session session                           = null;
	private Transaction tx                            = null;
	private boolean closed                            = false;
	private boolean success                           = false;

	public SessionTransaction(final BoltDatabaseService db, final Session session) {

		this.session = session;
		this.tx      = session.beginTransaction();
		this.db      = db;
	}

	@Override
	public void failure() {
		tx.failure();
	}

	@Override
	public void success() {

		tx.success();

		// transaction must be marked successfull explicitely
		success = true;
	}

	@Override
	public void close() {

		if (!success) {

			// We need to invalidate all existing references because we cannot
			// be sure that they contain the correct values after a rollback.
			for (final EntityWrapper entity : modifiedEntities) {
				entity.stale();
			}

		} else {

			if (!modifiedEntities.isEmpty()) {

				// data was written, invalidate query cache
				db.invalidateQueryCache();
			}

			// Notify all nodes that are modified in this transaction
			// so that the relationship caches are rebuilt.
			for (final EntityWrapper entity : modifiedEntities) {
				entity.clearCaches();
			}
		}

		// mark this transaction as closed BEFORE trying to actually close it
		// so that it is closed in case of a failure
		closed = true;

		try {

			tx.close();
			session.close();

		} catch (TransientException tex) {

			// transient exceptions can be retried
			throw new RetryException(tex);

		} finally {

			// make sure that the resources are freed
			if (session.isOpen()) {
				session.close();
			}
		}
	}

	public boolean isClosed() {
		return closed;
	}

	public void setClosed(final boolean closed) {
		this.closed = closed;
	}

	public long getLong(final String statement) {

		try {

			if (db.logQueries()) {
				System.out.println(statement);
			}

			return getLong(statement, Collections.EMPTY_MAP);

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}
	}

	public long getLong(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			return tx.run(statement, map).next().get(0).asLong();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}
	}

	public Object getObject(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			final StatementResult result = tx.run(statement, map);
			if (result.hasNext()) {

				return result.next().get(0).asObject();
			}

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}

		return null;
	}

	public Entity getEntity(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			return tx.run(statement, map).next().get(0).asEntity();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}
	}

	public Node getNode(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			return tx.run(statement, map).next().get(0).asNode();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}
	}

	public Relationship getRelationship(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			return tx.run(statement, map).next().get(0).asRelationship();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}
	}

	public QueryResult<Node> getNodes(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			return QueryUtils.map(new RecordNodeMapper(), new StatementIterable(tx.run(statement, map)));

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}
	}

	public QueryResult<Relationship> getRelationships(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			return QueryUtils.map(new RecordRelationshipMapper(), new StatementIterable(tx.run(statement, map)));

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}
	}

	public QueryResult<Long> getIds(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			return QueryUtils.map(new RecordLongMapper(), new StatementIterable(tx.run(statement, map)));

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}
	}

	public QueryResult<String> getStrings(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			final StatementResult result = tx.run(statement, map);
			final Record record = result.next();
			final Value value = record.get(0);

			return new QueryResult<String>() {

				@Override
				public void close() {
					result.consume();
				}

				@Override
				public Iterator<String> iterator() {
					return value.asList(Values.ofString()).iterator();
				}
			};

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}
	}

	public NativeResult run(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			return new StatementResultWrapper(db, tx.run(statement, map));

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}
	}

	public void set(final String statement, final Map<String, Object> map) {

		try {

			logQuery(statement, map);

			tx.run(statement, map).consume();

		} catch (TransientException tex) {
			closed = true;
			throw new RetryException(tex);
		} catch (NoSuchRecordException nex) {
			throw new NotFoundException(nex);
		}
	}

	public void logQuery(final String statement) {

		logQuery(statement, null);

	}

	public void logQuery(final String statement, final Map<String, Object> map) {

		if (db.logQueries()) {

			if (map != null && map.size() > 0) {

				System.out.println(statement + "\t\t Parameters: " + map.toString());

			} else {

				System.out.println(statement);

			}

		}

	}

	public void modified(final EntityWrapper wrapper) {

		// data was written, invalidate query cache
		db.invalidateQueryCache();

		modifiedEntities.add(wrapper);
	}

	private class StatementIterable implements QueryResult<Record> {

		private StatementResult result = null;

		public StatementIterable(final StatementResult result) {
			this.result = result;
		}

		@Override
		public void close() {
			result.consume();
		}

		@Override
		public Iterator<Record> iterator() {

			return new Iterator<Record>() {

				@Override
				public boolean hasNext() {
					return result.hasNext();
				}

				@Override
				public Record next() {

					try {

						return result.next();

					} catch (TransientException tex) {
						closed = true;
						throw new RetryException(tex);
					}
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException("Removal not supported.");
				}
			};
		}

	}
}
