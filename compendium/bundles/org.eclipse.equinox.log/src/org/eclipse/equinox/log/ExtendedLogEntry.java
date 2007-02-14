/*******************************************************************************
 * Copyright (c) 2006 Cognos Incorporated.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.equinox.log;

import org.osgi.service.log.LogEntry;

public interface ExtendedLogEntry extends LogEntry {
	String getLoggerName();

	Object getContext();

	long getThreadID();

	long getSequenceNumber();
}