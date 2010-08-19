/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 *  Copyright (c) 2010, Red Hat, Inc. and/or its affiliates or third-party contributors as
 *  indicated by the @author tags or express copyright attribution
 *  statements applied by the authors.  All third-party contributions are
 *  distributed under license by Red Hat, Inc.
 *
 *  This copyrighted material is made available to anyone wishing to use, modify,
 *  copy, or redistribute it subject to the terms and conditions of the GNU
 *  Lesser General Public License, as published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this distribution; if not, write to:
 *  Free Software Foundation, Inc.
 *  51 Franklin Street, Fifth Floor
 *  Boston, MA  02110-1301  USA
 */
package org.hibernate.search.test.batchindexing;

import java.util.List;

import junit.framework.TestCase;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

import org.hibernate.Transaction;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.backend.impl.batchlucene.LuceneBatchBackend;
import org.hibernate.search.test.util.FullTextSessionBuilder;
import org.hibernate.search.test.util.textbuilder.SentenceInventor;

/**
 * Tests the fullTextSession.createIndexer() API
 * for basic functionality.
 *
 * @author Sanne Grinovero
 */
public class IndexingGeneratedCorpusTest extends TestCase {

	private final int BOOK_NUM = 140;
	private final int ANCIENTBOOK_NUM = 120;
	private final int DVD_NUM = 200;

	private SentenceInventor sentenceInventor = new SentenceInventor( 7L, 4000 );
	private FullTextSessionBuilder builder;
	private int totalEntitiesInDB = 0;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		builder = new FullTextSessionBuilder();
		builder
				.addAnnotatedClass( Book.class )
				.addAnnotatedClass( Dvd.class )
				.addAnnotatedClass( AncientBook.class )
				.addAnnotatedClass( Nation.class )
				.setProperty( "hibernate.show_sql", "false" ) // too verbose for this test
				.setProperty( LuceneBatchBackend.CONCURRENT_WRITERS, "4" )
				.build();
		createMany( Book.class, BOOK_NUM );
		createMany( Dvd.class, DVD_NUM );
		createMany( AncientBook.class, ANCIENTBOOK_NUM );
		storeAllBooksInNation();
	}

	@Override
	protected void tearDown() {
		builder.close();
	}

	private void createMany(Class<? extends TitleAble> entityType, int amount)
			throws InstantiationException, IllegalAccessException {
		FullTextSession fullTextSession = builder.openFullTextSession();
		try {
			Transaction tx = fullTextSession.beginTransaction();
			fullTextSession.persist( new Nation( "Italy", "IT" ) );
			tx.commit();
			tx = fullTextSession.beginTransaction();
			for ( int i = 0; i < amount; i++ ) {
				TitleAble instance = entityType.newInstance();
				instance.setTitle( sentenceInventor.nextSentence() );
				//to test for HSEARCH-512 we make all entities share some proxy
				Nation country = ( Nation ) fullTextSession.load( Nation.class, 1 );
				instance.setFirstPublishedIn( country );
				fullTextSession.persist( instance );
				totalEntitiesInDB++;
				if ( i % 250 == 249 ) {
					tx.commit();
					fullTextSession.clear();
					System.out.println( "Test preparation: " + totalEntitiesInDB + " entities persisted" );
					tx = fullTextSession.beginTransaction();
				}
			}
			tx.commit();
		}
		finally {
			fullTextSession.close();
		}
	}

	/**
	 * Adds all stored books to the Nation.
	 * Needed to test for HSEARCH-534 and makes the dataset to index quite bigger.
	 */
	private void storeAllBooksInNation() {
		FullTextSession fullTextSession = builder.openFullTextSession();
		try {
			Transaction tx = fullTextSession.beginTransaction();
			List<Book> allBooks = fullTextSession.createCriteria( Book.class ).list();
			Nation italy = ( Nation ) fullTextSession.load( Nation.class, 1 );
			italy.getLibrariesHave().addAll( allBooks );
			tx.commit();
		}
		finally {
			fullTextSession.close();
		}
	}

	public void testBatchIndexing() throws InterruptedException {
		verifyResultNumbers(); //initial count of entities should match expectations
		purgeAll(); // empty indexes
		verifyIsEmpty();
		reindexAll(); // rebuild the indexes
		verifyResultNumbers(); // verify the count match again
		reindexAll(); //tests that purgeAll is automatic:
		verifyResultNumbers(); //..same numbers again
	}

	private void reindexAll() throws InterruptedException {
		FullTextSession fullTextSession = builder.openFullTextSession();
		try {
			fullTextSession.createIndexer( Object.class )
					.threadsForSubsequentFetching( 8 )
					.threadsToLoadObjects( 4 )
					.batchSizeToLoadObjects( 30 )
					.startAndWait();
		}
		finally {
			fullTextSession.close();
		}
	}

	private void purgeAll() {
		FullTextSession fullTextSession = builder.openFullTextSession();
		try {
			Transaction tx = fullTextSession.beginTransaction();
			fullTextSession.purgeAll( Object.class );
			tx.commit();
		}
		finally {
			fullTextSession.close();
		}
	}

	@SuppressWarnings("unchecked")
	private void verifyResultNumbers() {
		assertEquals(
				DVD_NUM,
				countByFT( Dvd.class )
		);
		assertEquals(
				ANCIENTBOOK_NUM + BOOK_NUM,
				countByFT( Book.class )
		);
		assertEquals(
				ANCIENTBOOK_NUM,
				countByFT( AncientBook.class )
		);
		assertEquals(
				DVD_NUM + ANCIENTBOOK_NUM + BOOK_NUM,
				countByFT( AncientBook.class, Book.class, Dvd.class )
		);
		assertEquals(
				DVD_NUM + ANCIENTBOOK_NUM,
				countByFT( AncientBook.class, Dvd.class )
		);
	}

	@SuppressWarnings("unchecked")
	private void verifyIsEmpty() {
		assertEquals( 0, countByFT( Dvd.class ) );
		assertEquals( 0, countByFT( Book.class ) );
		assertEquals( 0, countByFT( AncientBook.class ) );
		assertEquals( 0, countByFT( AncientBook.class, Book.class, Dvd.class ) );
	}

	private int countByFT(Class<? extends TitleAble>... types) {
		Query findAll = new MatchAllDocsQuery();
		int bySize = 0;
		int byResultSize = 0;
		FullTextSession fullTextSession = builder.openFullTextSession();
		try {
			Transaction tx = fullTextSession.beginTransaction();
			FullTextQuery fullTextQuery = fullTextSession.createFullTextQuery( findAll, types );
			bySize = fullTextQuery.list().size();
			byResultSize = fullTextQuery.getResultSize();
			tx.commit();
		}
		finally {
			fullTextSession.close();
		}
		assertEquals( bySize, byResultSize );
		return bySize;
	}

}
