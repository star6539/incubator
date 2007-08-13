/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.security.ssl;

import javax.net.ssl.TrustManagerFactorySpi;

/**
 * A factory interface for instantiating implementations of the
 * <code>javax.net.ssl.TrustManagerFactorySpi</code> JSEE SPI. Implementations
 * of this interface should be registered with OSGI, and will be returned
 * when the <code>org.eclipse.equinox.security.ServiceProvider</code> security 
 * provider is installed and configured in java.security.
 */
public interface TrustManagerFactorySpiService {

	/**
	 * The constant for the current provider as set in java.security.
	 */
	static final String SECURITY_PROPERTY_PROVIDER = "ssl.TrustManagerFactory.ServiceProxyProvider";

	/**
	 * Return a new instance of <code>TrustManagerFactorySpi</code>
	 * 
	 * @param arg - the argument passed from the provider.
	 * 
	 * @return TrustManagerFactorySpi
	 */
	TrustManagerFactorySpi newInstance( Object arg);
	
}
