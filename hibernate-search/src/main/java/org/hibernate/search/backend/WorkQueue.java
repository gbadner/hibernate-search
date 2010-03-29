/* $Id$
 * 
 * Hibernate, Relational Persistence for Idiomatic Java
 * 
 * Copyright (c) 2009, Red Hat, Inc. and/or its affiliates or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat, Inc.
 * 
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.search.backend;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import org.hibernate.annotations.common.AssertionFailure;
import org.hibernate.search.util.LoggerFactory;
import org.slf4j.Logger;

/**
 * @author Emmanuel Bernard
 */
public class WorkQueue {
	
	private static final Logger log = LoggerFactory.make();
	
	private List<Work> queue;

	private List<LuceneWork> sealedQueue;

	public WorkQueue(int size) {
		queue = new ArrayList<Work>(size);
	}

	private WorkQueue(List<Work> queue) {
		this.queue = queue;
	}

	public WorkQueue() {
		this(10);
	}


	public void add(Work work) {
		queue.add(work);
	}


	public List<Work> getQueue() {
		return queue;
	}

	public WorkQueue splitQueue() {
		WorkQueue subQueue = new WorkQueue( queue );
		this.queue = new ArrayList<Work>( queue.size() );
		return subQueue;
	}

	public List<LuceneWork> getSealedQueue() {
		if (sealedQueue == null) throw new AssertionFailure("Access a Sealed WorkQueue which has not been sealed");
		if (log.isTraceEnabled()) {
			StringBuilder sb = new StringBuilder();
			for (LuceneWork lw : sealedQueue) {
				sb.append( lw.toString() );
				sb.append( "\n" );
			}
			log.trace( "sending sealedQueue to backend: \n" + sb.toString() );
		}
		return sealedQueue;
	}

	public void setSealedQueue(List<LuceneWork> sealedQueue) {
		//invalidate the working queue for serializability
		queue = Collections.EMPTY_LIST;
		this.sealedQueue = sealedQueue;
	}

	public void clear() {
		queue.clear();
		if (sealedQueue != null) sealedQueue.clear();
	}

	public int size() {
		return queue.size();
	}
}
