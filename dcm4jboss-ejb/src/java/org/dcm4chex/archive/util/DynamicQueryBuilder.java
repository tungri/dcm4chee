package org.dcm4chex.archive.util;

import static java.lang.Math.min;
import static java.util.Arrays.copyOfRange;

import java.util.Iterator;

public class DynamicQueryBuilder {
	final static int DEFAULT_IN_LIST_SIZE = 200;

	public static class DynamicQuery {
		private final String jbossQl;
		private final Object[] args;

		DynamicQuery(String dynamicQl, Object[] args) {
			this.args = args;
			this.jbossQl = dynamicQl;
		}

		public String getJbossQl() {
			return jbossQl;
		}

		public Object[] getArgs() {
			return args;
		}
	}

	public Iterator<DynamicQuery> getDynamicQueries(
			final String staticJbossQlPrefix, final Object[] args,
			final int startIndex) {
		return new Iterator<DynamicQueryBuilder.DynamicQuery>() {
			int i;

			public boolean hasNext() {
				return i < args.length;
			}

			public DynamicQuery next() {
				int increment = min(args.length - i, DEFAULT_IN_LIST_SIZE);

				DynamicQuery inClause = new DynamicQuery(getJbossQl(
						staticJbossQlPrefix, startIndex, increment), getArgs(
						args, i, increment + i));

				i += increment;

				return inClause;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	private Object[] getArgs(final Object[] args, int startIndex, int endIndex) {
		return copyOfRange(args, startIndex, endIndex);
	}

	private String getJbossQl(String sql, int startIndex, int size) {
		StringBuffer sb = new StringBuffer(sql);
		appendJbossQl(sb, startIndex, size);
		return sb.toString();
	}

	public int appendJbossQl(StringBuffer jbossQl, int idx, int len) {
		if (len > 1) {
			jbossQl.append(" IN ( ");
			for (int i = 1; i < len; i++) {
				jbossQl.append("?").append(idx++).append(", ");
			}
			jbossQl.append("?").append(idx++).append(")");
		} else {
			jbossQl.append(" = ?").append(idx++);
		}
		return idx;
	}
}
