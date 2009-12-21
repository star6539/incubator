/*******************************************************************************
 *  Copyright (c) 2007, 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *      IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.p2.cudf.solver;

import java.util.*;
import org.eclipse.core.runtime.*;
import org.eclipse.equinox.p2.cudf.Main;
import org.eclipse.equinox.p2.cudf.metadata.IRequiredCapability;
import org.eclipse.equinox.p2.cudf.metadata.InstallableUnit;
import org.eclipse.equinox.p2.cudf.query.*;
import org.eclipse.osgi.util.NLS;

public class Slicer {
	private static boolean TIMING = true; //SET THIS TO FALSE FOR THE COMPETITION
	private QueryableArray possibilites;

	private LinkedList toProcess;
	private Set considered; //IUs to add to the slice
	private TwoTierMap slice; //The IUs that have been considered to be part of the problem

	private MultiStatus result;

	public Slicer(QueryableArray input) {
		possibilites = input;
		slice = new TwoTierMap();
		result = new MultiStatus(Main.PLUGIN_ID, IStatus.OK, Messages.Planner_Problems_resolving_plan, null);
	}

	public QueryableArray slice(InstallableUnit ius) {
		try {
			IProgressMonitor monitor = new NullProgressMonitor();
			long start = 0;
			if (TIMING) {
				start = System.currentTimeMillis();
			}

			considered = new HashSet(possibilites.getSize());
			considered.add(ius);
			toProcess = new LinkedList(considered);
			while (!toProcess.isEmpty()) {
				if (monitor.isCanceled()) {
					result.merge(Status.CANCEL_STATUS);
					throw new OperationCanceledException();
				}
				processIU((InstallableUnit) toProcess.removeFirst());
			}
			if (TIMING) {
				long stop = System.currentTimeMillis();
				System.out.println("# Slicing complete: " + (stop - start)); //$NON-NLS-1$
			}
		} catch (IllegalStateException e) {
			result.add(new Status(IStatus.ERROR, Main.PLUGIN_ID, e.getMessage(), e));
		}
		//		if (Tracing.DEBUG && result.getSeverity() != IStatus.OK)
		//			LogHelper.log(result);
		if (result.getSeverity() == IStatus.ERROR)
			return null;
		return new QueryableArray((InstallableUnit[]) considered.toArray(new InstallableUnit[considered.size()]));
	}

	public MultiStatus getStatus() {
		return result;
	}

	protected void processIU(InstallableUnit iu) {
		slice.put(iu.getId(), iu.getVersion(), iu);

		IRequiredCapability[] reqs = getRequiredCapabilities(iu);
		if (reqs.length == 0) {
			return;
		}
		for (int i = 0; i < reqs.length; i++) {
			expandRequirement(iu, reqs[i]);
		}
	}

	private IRequiredCapability[] getRequiredCapabilities(InstallableUnit iu) {
		return iu.getRequiredCapabilities();
	}

	private void expandRequirement(InstallableUnit iu, IRequiredCapability req) {
		if (req.isNegation())
			return;
		Collector matches = possibilites.query(new CapabilityQuery(req), new Collector(), null);
		int validMatches = 0;
		for (Iterator iterator = matches.iterator(); iterator.hasNext();) {
			InstallableUnit match = (InstallableUnit) iterator.next();
			validMatches++;
			if (!slice.containsKey(match.getId(), match.getVersion()))
				consider(match);
		}

		if (validMatches == 0) {
			if (req.isOptional()) {
				if (TIMING)
					System.out.println("No IU found to satisfy optional dependency of " + iu + " on req " + req); //$NON-NLS-1$//$NON-NLS-2$
			} else {
				result.add(new Status(IStatus.WARNING, Main.PLUGIN_ID, NLS.bind(Messages.Planner_Unsatisfied_dependency, iu, req)));
			}
		}
	}

	private void consider(InstallableUnit match) {
		if (considered.add(match))
			toProcess.addLast(match);
	}
}
