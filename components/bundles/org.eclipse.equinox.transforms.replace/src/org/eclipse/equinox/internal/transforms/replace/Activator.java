/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.equinox.internal.transforms.replace;

import java.util.Properties;
import org.eclipse.equinox.internal.transforms.replace.provisional.ReplaceTransformer;
import org.osgi.framework.*;

public class Activator implements BundleActivator {

	private ServiceRegistration registration;

	public void start(BundleContext context) throws Exception {
		Properties properties = new Properties();
		properties.put("equinox.transformerType", "replace"); //$NON-NLS-1$ //$NON-NLS-2$

		Object transformer = new ReplaceTransformer();
		registration = context.registerService(Object.class.getName(), transformer, properties);

	}

	public void stop(BundleContext context) throws Exception {
		if (registration != null)
			registration.unregister();
		context = null;
	}
}
