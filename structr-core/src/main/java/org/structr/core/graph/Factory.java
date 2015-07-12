/**
 * Copyright (C) 2010-2015 Morgner UG (haftungsbeschränkt)
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
package org.structr.core.graph;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.neo4j.function.Function;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.helpers.collection.Iterables;
import org.structr.common.FactoryDefinition;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.IdNotFoundToken;
import org.structr.core.Adapter;
import org.structr.core.GraphObject;
import org.structr.core.Result;
import org.structr.core.app.StructrApp;
import org.structr.schema.SchemaHelper;

/**
 *
 * @author Christian Morgner
 */

public abstract class Factory<S, T extends GraphObject> implements Adapter<S, T>, Function<S, T> {

	private static final Logger logger = Logger.getLogger(Factory.class.getName());

	public static final int DEFAULT_PAGE_SIZE	= Integer.MAX_VALUE;
	public static final int DEFAULT_PAGE		= 1;

	/**
	 * This limit is the number of objects up to which the overall count
	 * will be accurate.
	 */
	public static final int RESULT_COUNT_ACCURATE_LIMIT	= 5000;

	// encapsulates all criteria for node creation
	protected FactoryDefinition factoryDefinition = StructrApp.getConfiguration().getFactoryDefinition();
	protected FactoryProfile factoryProfile       = null;

	public Factory(final SecurityContext securityContext) {

		factoryProfile = new FactoryProfile(securityContext);
	}

	public Factory(final SecurityContext securityContext, final boolean includeDeletedAndHidden, final boolean publicOnly) {

		factoryProfile = new FactoryProfile(securityContext, includeDeletedAndHidden, publicOnly);
	}

	public Factory(final SecurityContext securityContext, final int pageSize, final int page, final String offsetId) {

		factoryProfile = new FactoryProfile(securityContext);

		factoryProfile.setPageSize(pageSize);
		factoryProfile.setPage(page);
		factoryProfile.setOffsetId(offsetId);
	}

	public Factory(final SecurityContext securityContext, final boolean includeDeletedAndHidden, final boolean publicOnly, final int pageSize, final int page, final String offsetId) {
		factoryProfile = new FactoryProfile(securityContext, includeDeletedAndHidden, publicOnly, pageSize, page, offsetId);
	}

	public abstract T instantiate(final S obj) throws FrameworkException;

	public abstract T instantiateWithType(final S obj, final Class<T> type, boolean isCreation) throws FrameworkException;

	public abstract T instantiate(final S obj, final boolean includeDeletedAndHidden, final boolean publicOnly) throws FrameworkException;

	public abstract T instantiateDummy(final S entity, final String entityType) throws FrameworkException;

	/**
	 * Create structr nodes from all given underlying database nodes
	 * No paging, but security check
	 *
	 * @param input
	 * @return result
	 * @throws org.structr.common.error.FrameworkException
	 */
	public Result instantiateAll(final Iterable<S> input) throws FrameworkException {

		List<T> objects = bulkInstantiate(input);

		return new Result(objects, objects.size(), true, false);
	}

	/**
	 * Create structr nodes from the underlying database nodes
	 *
	 * Include only nodes which are readable in the given security context.
	 * If includeDeletedAndHidden is true, include nodes with 'deleted' flag
	 * If publicOnly is true, filter by 'visibleToPublicUsers' flag
	 *
	 * @param input
	 * @return result
	 * @throws org.structr.common.error.FrameworkException
	 */
	public Result<T> instantiate(final ResourceIterable<S> input) throws FrameworkException {


		if (input != null) {

			if (factoryProfile.getOffsetId() != null) {

				return resultWithOffsetId(input);

			} else {

				return resultWithoutOffsetId(input);
			}
		}

		return Result.EMPTY_RESULT;

	}

	/**
	 * Create structr nodes from all given underlying database nodes
	 * No paging, but security check
	 *
	 * @param input
	 * @return nodes
	 * @throws org.structr.common.error.FrameworkException
	 */
	public List<T> bulkInstantiate(final Iterable<S> input) throws FrameworkException {

		final List<T> nodes = new LinkedList<>();

		if ((input != null) && input.iterator().hasNext()) {

			for (final S node : input) {

				T n = instantiate(node);
				if (n != null) {

					nodes.add(n);
				}
			}
		}

		return nodes;
	}

	@Override
	public T adapt(S s) {

		try {
			return instantiate(s);

		} catch (FrameworkException fex) {

			logger.log(Level.WARNING, "Unable to adapt", fex);
		}

		return null;
	}

	@Override
	public T apply(final S from) {
		return adapt(from);
	}

	public void setPage(final int page) {
		factoryProfile.setPage(page);
	}

	public void setPageSize(final int pageSize) {
		factoryProfile.setPageSize(pageSize);
	}

	protected Class<T> getClassForName(final String rawType) {
		return SchemaHelper.getEntityClassForRawType(rawType);
	}

	// <editor-fold defaultstate="collapsed" desc="private methods">
	protected List<S> read(final Iterable<S> it) {

		final List<S> nodes = new LinkedList();

		while (it.iterator().hasNext()) {

			nodes.add(it.iterator().next());
		}

		return nodes;

	}

	protected Result resultWithOffsetId(final ResourceIterable<S> input) throws FrameworkException {

		final List<S> source     = Iterables.toList(input);
		final int size           = source.size();
		final int pageSize       = Math.min(size, factoryProfile.getPageSize());
		final int page           = factoryProfile.getPage();
		final String offsetId    = factoryProfile.getOffsetId();
		final List<T> elements   = new LinkedList<>();
		int position             = 0;
		int count                = 0;
		int offset               = 0;

		// We have an offsetId, so first we need to
		// find the node with this uuid to get the offset
		final List<T> nodesUpToOffset = new LinkedList();
		final Iterator<S> iterator    = source.iterator();
		int i                   = 0;
		boolean gotOffset       = false;

		while (iterator.hasNext()) {

			final T n = instantiate(iterator.next());

			if (n == null) {

				continue;

			}

			nodesUpToOffset.add(n);

			if (!gotOffset) {

				if (!offsetId.equals(n.getUuid())) {

					i++;

					continue;

				}

				gotOffset = true;
				offset    = page > 0
					    ? i
					    : i + (page * pageSize);

				break;

			}

		}

		if (!nodesUpToOffset.isEmpty() && !gotOffset) {

			throw new FrameworkException("offsetId", new IdNotFoundToken(offsetId));
		}

		if (offset < 0) {

			// Remove last item
			nodesUpToOffset.remove(nodesUpToOffset.size()-1);

				return new Result(nodesUpToOffset, size, true, false);
		}

		for (final T node : nodesUpToOffset) {

			if (node != null) {

				if (++position > offset) {

					// stop if we got enough nodes
					if (++count > pageSize) {

						return new Result(elements, size, true, false);
					}

					elements.add(node);
				}
			}
		}

		// If we get here, the result was not complete, so we need to iterate
		// through the index result (input) to get more items.
		while (iterator.hasNext()) {

			final T n = instantiate(iterator.next());
			if (n != null) {

				if (++position > offset) {

					// stop if we got enough nodes
					if (++count > pageSize) {

						return new Result(elements, size, true, false);
					}

					elements.add(n);
				}
			}
		}

		return new Result(elements, size, true, false);

	}

	protected Result resultWithoutOffsetId(final ResourceIterable<S> input) throws FrameworkException {

		final int pageSize = factoryProfile.getPageSize();
		final int page     = factoryProfile.getPage();
		int fromIndex;

		if (page < 0) {

			final List<S> rawNodes = read(input);
			final int size         = rawNodes.size();

			fromIndex = Math.max(0, size + (page * pageSize));

			final List<T> nodes = new LinkedList<>();
			final int toIndex   = Math.min(size, fromIndex + pageSize);

			for (final S n : rawNodes.subList(fromIndex, toIndex)) {

				nodes.add(instantiate(n));
			}

			// We've run completely through the iterator,
			// so the overall count from here is accurate.
			return new Result(nodes, size, true, false);

		} else {

			fromIndex = pageSize == Integer.MAX_VALUE ? 0 : (page - 1) * pageSize;
			return page(input, fromIndex, pageSize);
		}

	}

	protected Result page(final ResourceIterable<S> input, final int offset, final int pageSize) throws FrameworkException {

		final List<T> nodes = new LinkedList<>();
		int position	    = 0;
		int count	    = 0;
		int overallCount    = 0;
		boolean pageFull    = false;

		for (final S s : input) {

			T node = instantiate(s);

			if (node != null) {

				overallCount++;

				if (++position > offset) {

					if (++count > pageSize) {

						pageFull = true;

					}

					if (!pageFull) {

						nodes.add(node);

					}

				}
			}

		}

		return new Result(nodes, overallCount, true, false);

	}

	//~--- inner classes --------------------------------------------------

	protected class FactoryProfile {

		private boolean includeDeletedAndHidden = true;
		private String offsetId                 = null;
		private boolean publicOnly              = false;
		private int pageSize                    = DEFAULT_PAGE_SIZE;
		private int page                        = DEFAULT_PAGE;
		private SecurityContext securityContext = null;

		//~--- constructors -------------------------------------------

		public FactoryProfile(final SecurityContext securityContext) {

			this.securityContext = securityContext;

		}

		public FactoryProfile(final SecurityContext securityContext, final boolean includeDeletedAndHidden, final boolean publicOnly) {

			this.securityContext         = securityContext;
			this.includeDeletedAndHidden = includeDeletedAndHidden;
			this.publicOnly              = publicOnly;

		}

		public FactoryProfile(final SecurityContext securityContext, final boolean includeDeletedAndHidden, final boolean publicOnly, final int pageSize, final int page,
				      final String offsetId) {

			this.securityContext         = securityContext;
			this.includeDeletedAndHidden = includeDeletedAndHidden;
			this.publicOnly              = publicOnly;
			this.pageSize                = pageSize;
			this.page                    = page;
			this.offsetId                = offsetId;

		}

		//~--- methods ------------------------------------------------

		/**
		 * @return the includeDeletedAndHidden
		 */
		public boolean includeDeletedAndHidden() {

			return includeDeletedAndHidden;

		}

		/**
		 * @return the publicOnly
		 */
		public boolean publicOnly() {

			return publicOnly;

		}

		//~--- get methods --------------------------------------------

		/**
		 * @return the offsetId
		 */
		public String getOffsetId() {

			return offsetId;

		}

		/**
		 * @return the pageSize
		 */
		public int getPageSize() {

			return pageSize;

		}

		/**
		 * @return the page
		 */
		public int getPage() {

			return page;

		}

		/**
		 * @return the securityContext
		 */
		public SecurityContext getSecurityContext() {

			return securityContext;

		}

		//~--- set methods --------------------------------------------

		/**
		 * @param includeDeletedAndHidden the includeDeletedAndHidden to set
		 */
		public void setIncludeDeletedAndHidden(boolean includeDeletedAndHidden) {

			this.includeDeletedAndHidden = includeDeletedAndHidden;

		}

		/**
		 * @param offsetId the offsetId to set
		 */
		public void setOffsetId(String offsetId) {

			this.offsetId = offsetId;

		}

		/**
		 * @param publicOnly the publicOnly to set
		 */
		public void setPublicOnly(boolean publicOnly) {

			this.publicOnly = publicOnly;

		}

		/**
		 * @param pageSize the pageSize to set
		 */
		public void setPageSize(int pageSize) {

			this.pageSize = pageSize;

		}

		/**
		 * @param page the page to set
		 */
		public void setPage(int page) {

			this.page = page;

		}

		/**
		 * @param securityContext the securityContext to set
		 */
		public void setSecurityContext(SecurityContext securityContext) {

			this.securityContext = securityContext;

		}

	}

	// </editor-fold>
}
