/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 * IBM Corporation - initial implementation and ideas 
 ******************************************************************************/
package org.eclipse.equinox.p2.cudf;

import java.io.*;
import java.net.URL;
import java.util.*;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.equinox.internal.p2.engine.Profile;
import org.eclipse.equinox.internal.p2.metadata.*;
import org.eclipse.equinox.internal.provisional.p2.director.ProfileChangeRequest;
import org.eclipse.equinox.internal.provisional.p2.metadata.*;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.CapabilityQuery;
import org.eclipse.equinox.internal.provisional.p2.metadata.query.Collector;

/**
 * TODO - if we have 2 versions in a row for the same bundle, convert it to a version range (e>2,e<4 should be e (2,4))
 * TODO - are the keys in the stanzas always lower-case?
 */
public class Main implements IApplication {

	private static final boolean DEBUG = true;
	private static InstallableUnit currentIU = null;
	private static ProfileChangeRequest currentRequest = null;
	private static List allIUs = new ArrayList();
	private static QueryableArray query = null;
	
	public static void main(String[] args) {
		String filename = null;
		if (args.length > 0)
			filename = args[0];
		if (filename == null)
			filename = "sample-data/sample1.cudf";
		//		if (filename == null)
		//			filename ="sample-data/rand2fa1f8.cudf";

		BufferedReader reader = null;
		try {
			URL url = Activator.getFile(filename);
			if (url == null)
				throw new IllegalArgumentException("Unable to find input file: " + filename);
			reader = new BufferedReader(new InputStreamReader(url.openStream()));
			String next = reader.readLine();
			while (true) {

				// look-ahead to check for line continuation
				String line = next;
				for (next = reader.readLine(); next != null && next.length() > 0 && next.charAt(0) == ' '; next = reader.readLine()) {
					line = line + next.substring(1);
				}

				// terminating condition of the loop... reached the end of the file
				if (line == null) {
					validateAndAddIU();
					break;
				}

				// end of stanza
				if (line.length() == 0) {
					validateAndAddIU();
					continue;
				}

				// what type of line are we dealing with?
				int c = line.charAt(0);
				switch (c) {
					case -1 :
						return;
					case '#' :
						// ignore comments
						break;
					case 'p' :
						handleP(line);
						break;
					case 'v' :
						handleVersion(line);
						break;
					case 'd' :
						handleDepends(line);
						break;
					case 'c' :
						handleConflicts(line);
						break;
					case 'i' :
						handleI(line);
						break;
					case 'r' :
						handleR(line);
						break;
					case 'u' :
						handleU(line);
						break;
					default :
						break;
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null)
				try {
					reader.close();
				} catch (IOException e) {
					// ignore
				}
		}
		for (Iterator iter = allIUs.iterator(); iter.hasNext();)
			debug((InstallableUnit) iter.next());
		debug(currentRequest);
	}

	/*
	 * Ensure that the current IU that we have been building is validate and if so, then
	 * add it to our collected list of all converted IUs from the file.
	 */
	private static void validateAndAddIU() {
		if (currentIU == null)
			return;
		// For a package stanze, the id and version are the only mandatory elements
		if (currentIU.getId() == null)
			throw new IllegalStateException("Malformed \'package\' stanza. No package element found.");
		if (currentIU.getVersion() == null)
			throw new IllegalStateException("Malformed \'package\' stanza. Package " + currentIU.getId() + " does not have a version.");
		allIUs.add(currentIU);
		// reset to be ready for the next stanza
		currentIU = null;
	}

	private static void handleI(String line) {
		if (line.startsWith("installed: ")) {
			String value = line.substring("installed: ".length());
			currentIU.setProperty("installed", value);
			return;
		}

		if (line.startsWith("install: ")) {
			line = line.substring("install: ".length());
			currentRequest.addInstallableUnits(new IInstallableUnit[] {findIUToInstall(line)});
			return;
		}
	}

	private static IInstallableUnit findIUToInstall(String line) {
		Collector c = query.query(new CapabilityQuery(new RequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, line, VersionRange.emptyRange, null, false, false)), new Collector(), null);
		for (Iterator iterator = c.iterator(); iterator.hasNext();) {
			IInstallableUnit packageToInstall = (IInstallableUnit) iterator.next();
			if(! "true".equals(packageToInstall.getProperty("installed")))
				return packageToInstall;
		}
		throw new IllegalStateException("Can't find an IU to install: " + line );
	}
	
	private static IInstallableUnit findIUToUninstall(String line) {
		Collector c = query.query(new CapabilityQuery(new RequiredCapability(IInstallableUnit.NAMESPACE_IU_ID, line, VersionRange.emptyRange, null, false, false)), new Collector(), null);
		for (Iterator iterator = c.iterator(); iterator.hasNext();) {
			IInstallableUnit packageToInstall = (IInstallableUnit) iterator.next();
			if("true".equals(packageToInstall.getProperty("installed")))
				return packageToInstall;
		}
		throw new IllegalStateException("Can't find an IU to install: " + line );
	}
	
	
	private static void handleRequest(String line) {
		currentRequest = new ProfileChangeRequest(new Profile("cudf-parser", null, null));
	}

	private static void handleR(String line) {
		if (line.startsWith("request: ")) {
			handleRequest(line);
			initializeQueryableArray();
			return;
		}

		if (line.startsWith("remove: ")) {
			line = line.substring("remove: ".length());
			currentRequest.removeInstallableUnits(new IInstallableUnit[] {findIUToUninstall(line)});
			return;
		}
	}

	private static void initializeQueryableArray() {
		query = new QueryableArray((IInstallableUnit[]) allIUs.toArray(new IInstallableUnit[allIUs.size()]));
	}

	private static void handleU(String line) {
		if (!line.startsWith("upgrade: "))
			return;
		line = line.substring("upgrade: ".length());
		// todo
	}

	/*
	 * Convert the version string to a version object and set it on the IU
	 */
	private static void handleVersion(String line) {
		currentIU.setVersion(new Version(line.substring("Version: ".length())));
	}

	// VPkg ::= PkgName (Sp + VConstr)?
	// VConstr ::= RelOp Sp + Ver
	// RelOp ::= "=" | "!=" | ">=" | ">" | "<=" | "<"
	// Sp ::= U+0020 (i.e. space)
	// | U+0009 (i.e. tab)
	// Ver ::= PosInt
	private static void handleDepends(String line) {
		mergeRequirements(createRequires(line.substring("depends: ".length())));
	}

	/*
	 * Conflicts are like depends except NOT'd.
	 */
	private static void handleConflicts(String line) {
		List reqs = createRequires(line.substring("conflicts: ".length()));
		List conflicts = new ArrayList();
		for (Iterator iter = reqs.iterator(); iter.hasNext();)
			conflicts.add(new NotRequirement((IRequiredCapability) iter.next()));
		mergeRequirements(conflicts);
	}

	/*
	 * Set the given list of requirements on teh current IU. Merge if necessary.
	 */
	private static void mergeRequirements(List requirements) {
		if (currentIU.getRequiredCapabilities() != null) {
			IRequiredCapability[] current = currentIU.getRequiredCapabilities();
			for (int i = 0; i < current.length; i++)
				requirements.add(current[i]);
		}
		currentIU.setRequiredCapabilities((IRequiredCapability[]) requirements.toArray(new IRequiredCapability[requirements.size()]));
	}

	/*
	 * Create and return a generic list of required capabilities. This list can be from a depends or conflicts entry.
	 */
	private static List createRequires(String line) {
		// break the string into per-package instructions
		List ANDs = new ArrayList();
		for (StringTokenizer outer = new StringTokenizer(line, ","); outer.hasMoreTokens();) {
			String andStmt = outer.nextToken();

			List ORs = new ArrayList();
			for (StringTokenizer inner = new StringTokenizer(andStmt, "|"); inner.hasMoreTokens();) {
				String orStmt = inner.nextToken();
				String[] tuple = new String[3];
				int i = 0;
				for (StringTokenizer operationIterator = new StringTokenizer(orStmt, " \t"); operationIterator.hasMoreTokens(); i++)
					tuple[i] = operationIterator.nextToken();
				String name = tuple[0];
				String operator = tuple[1];
				String number = tuple[2];

				// special code to handle not equals
				if (operator != null && "!=".equals(operator)) {
					// TODO Pascal to get an explanation on this but if you have "depends: a != 1" does that mean
					// you require at least one version of "a" and it can't be 1? Or is it ok to not have a requirement on "a"?
					ORs.add(new ORRequirement(new IRequiredCapability[] {createRequiredCapability(name, "<", number), createRequiredCapability(name, ">", number)}));
				} else
					ORs.add(createRequiredCapability(name, operator, number));
			}
			if (ORs.size() == 1)
				ANDs.add(ORs.get(0));
			else
				ANDs.add(new ORRequirement((IRequiredCapability[]) ORs.toArray(new IRequiredCapability[ORs.size()])));
		}
		return ANDs;
	}

	/*
	 * Create and return a required capability for the given info. operator and number can be null which means any version. (0.0.0)
	 */
	private static IRequiredCapability createRequiredCapability(String name, String operator, String number) {
		VersionRange range = createVersionRange(operator, number);
		String filter = null;
		boolean optional = false;
		boolean multiple = false;
		return new RequiredCapability("osgi.bundle", name, range, filter, optional, multiple);
	}

	/*
	 * Create and return a version range based on the given operator and number. Note that != is
	 * handled elsewhere.
	 */
	private static VersionRange createVersionRange(String operator, String number) {
		if (operator == null || number == null)
			return VersionRange.emptyRange;
		if ("=".equals(operator))
			return new VersionRange('[' + number + ',' + number + ']');
		if ("<".equals(operator))
			return new VersionRange("[0," + number + ')');
		if (">".equals(operator))
			return new VersionRange('(' + number + ',' + Integer.MAX_VALUE + ']');
		if ("<=".equals(operator))
			return new VersionRange("[0," + number + ']');
		if (">=".equals(operator))
			return new VersionRange('[' + number + ',' + Integer.MAX_VALUE + ']');
		return VersionRange.emptyRange;
	}

	// package name matches: "^[a-zA-Z0-9+./@()%-]+$"
	private static void handleP(String readLine) {
		if (readLine.startsWith("package: ")) {
			// beginning of "package" stanza
			currentIU = new InstallableUnit();
			currentIU.setId(readLine.substring("package: ".length()));
			return;
		}
	}

	// copied from ProfileSynchronizer
	private static void debug(ProfileChangeRequest request) {
		if (!DEBUG || request == null)
			return;
		System.out.println("\nProfile Change Request:");
		IInstallableUnit[] toAdd = request.getAddedInstallableUnits();
		if (toAdd == null || toAdd.length == 0) {
			System.out.println("No installable units to add.");
		} else {
			for (int i = 0; i < toAdd.length; i++)
				System.out.println("Adding IU: " + toAdd[i].getId() + ' ' + toAdd[i].getVersion());
		}
		Map propsToAdd = request.getInstallableUnitProfilePropertiesToAdd();
		if (propsToAdd == null || propsToAdd.isEmpty()) {
			System.out.println("No IU properties to add.");
		} else {
			for (Iterator iter = propsToAdd.keySet().iterator(); iter.hasNext();) {
				Object key = iter.next();
				System.out.println("Adding IU property: " + key + "->" + propsToAdd.get(key));
			}
		}

		IInstallableUnit[] toRemove = request.getRemovedInstallableUnits();
		if (toRemove == null || toRemove.length == 0) {
			System.out.println("No installable units to remove.");
		} else {
			for (int i = 0; i < toRemove.length; i++)
				System.out.println("Removing IU: " + toRemove[i].getId() + ' ' + toRemove[i].getVersion());
		}
		Map propsToRemove = request.getInstallableUnitProfilePropertiesToRemove();
		if (propsToRemove == null || propsToRemove.isEmpty()) {
			System.out.println("No IU properties to remove.");
		} else {
			for (Iterator iter = propsToRemove.keySet().iterator(); iter.hasNext();) {
				Object key = iter.next();
				System.out.println("Removing IU property: " + key + "->" + propsToRemove.get(key));
			}
		}
	}

	// dump info to console
	private static void debug(InstallableUnit unit) {
		if (!DEBUG)
			return;
		System.out.println("\nInstallableUnit: " + unit.getId());
		System.out.println("Version: " + unit.getVersion());
		Map properties = unit.getProperties();
		for (Iterator iter = properties.keySet().iterator(); iter.hasNext();) {
			Object key = iter.next();
			Object value = properties.get(key);
			System.out.println("Property: " + key + '=' + value);
		}
		IRequiredCapability[] reqs = unit.getRequiredCapabilities();
		for (int i = 0; i < reqs.length; i++) {
			System.out.println("Requirement: " + reqs[i]);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication#start(org.eclipse.equinox.app.IApplicationContext)
	 */
	public Object start(IApplicationContext context) throws Exception {
		Activator.startPrereqs();
		// hack to call the main method here instead of making it a real Eclipse app because we know we
		// want to convert it to command-line eventually
		Main.main((String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS));
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication#stop()
	 */
	public void stop() {
		// nothing to do
	}
}