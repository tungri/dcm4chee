package org.dcm4chex.archive.mbean;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

public class JndiHelper {
	/**
	 * Retrieves an object via JNDI.
	 * 
	 * @param jndiName
	 *            the name of the object to look up
	 * @return an object via JNDI
	 * @throws NamingException
	 */
	public Object jndiLookup(String jndiName) throws NamingException {
		Context jndiCtx = null;
		try {
			jndiCtx = new InitialContext();
			return jndiLookup(jndiCtx, jndiName);
		} finally {
			if (jndiCtx != null) {
				try {
					jndiCtx.close();
				} catch (NamingException ignore) {
				}
			}
		}
	}

	/**
	 * Retrieves an object via JNDI.
	 * 
	 * @param jndiCtx
	 *            the context to perform the lookup against
	 * @param jndiName
	 *            the name of the object to look up
	 * @return an object via JNDI
	 * @throws NamingException
	 */
	public Object jndiLookup(Context jndiCtx, String jndiName)
			throws NamingException {
		return jndiCtx.lookup(jndiName);
	}
}
