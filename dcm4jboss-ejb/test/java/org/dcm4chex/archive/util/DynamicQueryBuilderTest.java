package org.dcm4chex.archive.util;

import java.util.Iterator;

import junit.framework.TestCase;

import org.dcm4chex.archive.util.DynamicQueryBuilder;

public class DynamicQueryBuilderTest extends TestCase {

	public void test_getDynamicQueries_shouldReturnCorrectNumberOfDynamicQueries_whenCountIsGreaterThanDefaultSplitSize() {
		int numIuids = DynamicQueryBuilder.DEFAULT_IN_LIST_SIZE + 1;
		int expected = (int) Math.ceil(numIuids * 1.0 / DynamicQueryBuilder.DEFAULT_IN_LIST_SIZE);
		int actual = returnNumberOfDynamicQueries(numIuids);
		assertEquals("Number of dynamic queries does not match number of iuids", expected, actual);
	}
	
	public void test_getDynamicQueries_shouldReturnCorrectNumberOfDynamicQueries_whenCountIsLessThanDefaultSplitSize() {
		int numIuids = DynamicQueryBuilder.DEFAULT_IN_LIST_SIZE - 1;
		int expected = (int) Math.ceil(numIuids * 1.0 / DynamicQueryBuilder.DEFAULT_IN_LIST_SIZE);
		int actual = returnNumberOfDynamicQueries(numIuids);
        assertEquals("Number of dynamic queries does not match number of iuids", expected, actual);
	}
	
	private int returnNumberOfDynamicQueries(int numIuids) {
		String[] iuids = new String[numIuids];
		for ( int i = 0; i < iuids.length; i++ ) {
			iuids[i] = String.valueOf(i);
		}
		DynamicQueryBuilder dynamicQueryBuilder = new DynamicQueryBuilder();
		Iterator<DynamicQueryBuilder.DynamicQuery> dynamicQueries = 
			dynamicQueryBuilder.getDynamicQueries("SELECT OBJECT(i) FROM Instance i WHERE i.sopIuid", iuids, 1);
		
		int count = 0;
        while (dynamicQueries.hasNext()) {
        	dynamicQueries.next();
        	count++;
        }
        return count;
	}
}
