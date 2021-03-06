/*******************************************************************************
 * Copyright (c) 2009, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sonatype, Inc. - ongoing development
 *******************************************************************************/
package org.eclipse.equinox.internal.p2.afterthefact;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.internal.p2.artifact.repository.simple.SimpleArtifactRepository;
import org.eclipse.equinox.internal.p2.director.SimplePlanner;
import org.eclipse.equinox.internal.provisional.p2.director.ProfileChangeRequest;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.engine.*;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.planner.IPlanner;
import org.eclipse.equinox.p2.planner.ProfileInclusionRules;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.osgi.service.resolver.PlatformAdmin;
import org.osgi.service.packageadmin.PackageAdmin;

public class Install {
	private IArtifactRepository repo;

	public void doInstall() {
		//Acquire services that are necessary for the rest of the installation
		PlatformAdmin platformAdmin = (PlatformAdmin) ServiceHelper.getService(Activator.getContext(), PlatformAdmin.class.getName());
		IProvisioningAgent agent = (IProvisioningAgent) ServiceHelper.getService(Activator.getContext(), IProvisioningAgent.class.getName());
		IProfileRegistry registry = (IProfileRegistry) agent.getService(IProfileRegistry.class.getName());
		IEngine engine = (IEngine) agent.getService(IEngine.class.getName());
		IPlanner planner = (IPlanner) agent.getService(IPlanner.class.getName());
		IMetadataRepositoryManager repoMgr = (IMetadataRepositoryManager) agent.getService(IMetadataRepositoryManager.class.getName());
		IArtifactRepositoryManager artifactRepoMgr = (IArtifactRepositoryManager) agent.getService(IArtifactRepositoryManager.class.getName());

		//Create a bundle pool. This does not have to be done all the time
		repo = createBundlePool(artifactRepoMgr);

		//Get the IU to install
		Collection<IInstallableUnit> iusToInstall = getRandomIUToInstall(repoMgr, artifactRepoMgr);

		//Create a representation of what is already installed into the running system
		Collection<IInstallableUnit> ius = new Reify().reify(platformAdmin);
		IProfile profile = null;
		try {
			profile = spoofUpProfile(agent, registry, engine, planner, ius);
		} catch (ProvisionException e) {
			e.printStackTrace();
		}
		if (profile == null)
			return;

		//Create a request to install the IUs and compute a plan to install those
		ProfileChangeRequest request = new ProfileChangeRequest(profile);
		request.addAll(iusToInstall);
		IProvisioningPlan plan = planner.getProvisioningPlan(request, new ProvisioningContext(agent), null);

		//Execute the plan. This causes the files to be downloaded and the bundles to be installed
		System.out.println(engine.perform(plan, null));

		//Refresh the framework to get the bundles installed
		PackageAdmin packageAdmin = (PackageAdmin) ServiceHelper.getService(Activator.getContext(), PackageAdmin.class.getName());
		packageAdmin.refreshPackages(null);
	}

	//Helper method to get IUs to install
	private Collection<IInstallableUnit> getRandomIUToInstall(IMetadataRepositoryManager repoMgr, IArtifactRepositoryManager artifactMgr) {
		try {
			repoMgr.addRepository(new URI("http://download.eclipse.org/releases/galileo"));
			artifactMgr.addRepository(new URI("http://download.eclipse.org/releases/galileo"));
		} catch (URISyntaxException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		IQueryResult<IInstallableUnit> c = repoMgr.query(QueryUtil.createIUQuery("org.eclipse.emf"), new NullProgressMonitor());
		Collection<IInstallableUnit> result = new ArrayList<IInstallableUnit>(2);
		result.add(c.iterator().next());
		result.add(Reify.createDefaultBundleConfigurationUnit());
		return result;
	}

	private IProfile spoofUpProfile(IProvisioningAgent agent, IProfileRegistry registry, IEngine engine, IPlanner planner, Collection<IInstallableUnit> ius) throws ProvisionException {
		Map<String, String> prop = new HashMap<String, String>();
		// prop.setProperty("org.eclipse.bund, value)
		// set the bundle pool
		// create the artifact repository
		prop.put("org.eclipse.equinox.p2.bundlepool", repo.getLocation().toString());
		IProfile profile = registry.addProfile("foobar" + System.currentTimeMillis(), prop);
		IProvisioningPlan plan = engine.createPlan(profile, new ProvisioningContext(agent));
		for (Iterator<IInstallableUnit> iter = ius.iterator(); iter.hasNext();) {
			IInstallableUnit iu = (IInstallableUnit) iter.next();
			plan.addInstallableUnit(iu);
			plan.setInstallableUnitProfileProperty(iu, SimplePlanner.INCLUSION_RULES, ProfileInclusionRules.createOptionalInclusionRule(iu));
		}
		IPhaseSet phaseSet = PhaseSetFactory.createDefaultPhaseSetExcluding(new String[] {PhaseSetFactory.PHASE_CHECK_TRUST, PhaseSetFactory.PHASE_COLLECT, PhaseSetFactory.PHASE_CONFIGURE, PhaseSetFactory.PHASE_UNCONFIGURE, PhaseSetFactory.PHASE_UNINSTALL});
		IStatus status = engine.perform(plan, phaseSet, null);
		if (!status.isOK())
			return null;
		return profile;
	}

	private IArtifactRepository createBundlePool(IArtifactRepositoryManager mgr) {
		final String[][] DEFAULT_MAPPING_RULES = {{"(& (classifier=osgi.bundle))", "${repoUrl}/lib/${id}_${version}.jar"}};//$NON-NLS-1$
		IArtifactRepository repo;
		try {
			try {
				repo = mgr.loadRepository(new URI("file:/Users/Pascal/tmp/bundlepool"), null);
				return repo;
			} catch (ProvisionException e) {
				repo = mgr.createRepository(new URI("file:/Users/Pascal/tmp/bundlepool"), "bundle pool", IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY, (Map) null);
			}
		} catch (ProvisionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		// TODO This is a major hack because the rules can't be set when creating the repo. 
		((SimpleArtifactRepository) repo).setRules(DEFAULT_MAPPING_RULES);
		((SimpleArtifactRepository) repo).save();
		//((SimpleArtifactRepository) repo).initializeMapper();
		Method initializeMapper;
		try {
			initializeMapper = SimpleArtifactRepository.class.getDeclaredMethod("initializeMapper", null);
			initializeMapper.setAccessible(true);
			initializeMapper.invoke(repo, null);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		return repo;
	}

}
