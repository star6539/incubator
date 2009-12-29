/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.equinox.p2.osgi.actions;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.internal.provisional.p2.core.ProvisionException;
import org.eclipse.equinox.internal.provisional.p2.metadata.ITouchpointData;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.InstallableUnitOperand;
import org.eclipse.equinox.p2.engine.spi.ProvisioningAction;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRequest;
import org.eclipse.equinox.p2.touchpoint.osgi.Activator;
import org.eclipse.equinox.p2.touchpoint.osgi.GenericOSGiTouchpoint;
import org.eclipse.equinox.p2.touchpoint.osgi.ServiceHelper;

public class CollectAction extends ProvisioningAction {
	public static final String ID = "collect"; //$NON-NLS-1$
	public static final String ARTIFACT_FOLDER = "artifact.folder"; //$NON-NLS-1$

	public IStatus execute(Map<String,Object> parameters) {
		IProfile profile = (IProfile) parameters.get(GenericOSGiTouchpoint.PARM_PROFILE);
		InstallableUnitOperand operand = (InstallableUnitOperand) parameters.get(GenericOSGiTouchpoint.PARM_OPERAND);
		IArtifactRequest[] requests;
		try {
			requests = CollectAction.collect(operand.second(), profile);
		} catch (ProvisionException e) {
			return e.getStatus();
		}

		@SuppressWarnings("unchecked")
		Collection<IArtifactRequest[]> artifactRequests = (Collection<IArtifactRequest[]>) parameters.get(GenericOSGiTouchpoint.PARM_ARTIFACT_REQUESTS);
		artifactRequests.add(requests);
		return Status.OK_STATUS;
	}

	public IStatus undo(Map<String,Object> parameters) {
		// nothing to do the GC usually takes care of that
		return Status.OK_STATUS;
	}

	public static boolean isZipped(List<ITouchpointData> data) {
		if (data == null || data.size() == 0)
			return false;
		for (int i = 0; i < data.size(); i++) {
			if (data.get(i).getInstruction("zipped") != null) //$NON-NLS-1$
				return true;
		}
		return false;
	}

	public static Map<String,String> createArtifactDescriptorProperties(IInstallableUnit installableUnit) {
		Map<String,String> descriptorProperties = null;
		if (CollectAction.isZipped(installableUnit.getTouchpointData())) {
			descriptorProperties = new HashMap<String,String>();
			descriptorProperties.put(CollectAction.ARTIFACT_FOLDER, Boolean.TRUE.toString());
		}
		return descriptorProperties;
	}

	public static IArtifactRequest[] collect(IInstallableUnit installableUnit, IProfile profile) throws ProvisionException {
		List<IArtifactKey> toDownload = installableUnit.getArtifacts();
		if (toDownload == null || toDownload.isEmpty())
			return IArtifactRepositoryManager.NO_ARTIFACT_REQUEST;

		IArtifactRepository bundlePool = getRepo(profile);
		
		if (bundlePool == null)
			throw new ProvisionException("no bundle pool");

		List<IArtifactRequest> requests = new ArrayList<IArtifactRequest>();
		for (int i = 0; i < toDownload.size(); i++) {
			IArtifactKey key = toDownload.get(i);
			if (!bundlePool.contains(key)) {
				Map<String,String> repositoryProperties = CollectAction.createArtifactDescriptorProperties(installableUnit);
				requests.add(getArtifactRepositoryManager().createMirrorRequest(key, bundlePool, null, repositoryProperties));
			}
		}

		if (requests.isEmpty())
			return IArtifactRepositoryManager.NO_ARTIFACT_REQUEST;

		IArtifactRequest[] result = (IArtifactRequest[]) requests.toArray(new IArtifactRequest[requests.size()]);
		return result;
	}
	
	public static IArtifactRepositoryManager getArtifactRepositoryManager() {
		return (IArtifactRepositoryManager) org.eclipse.equinox.p2.touchpoint.osgi.ServiceHelper.getService(org.eclipse.equinox.p2.touchpoint.osgi.Activator.ctx, IArtifactRepositoryManager.class.getName());
	}
	
	private static IArtifactRepository getRepo(IProfile profile) {
		IArtifactRepositoryManager artifactRepoMgr = (IArtifactRepositoryManager) ServiceHelper.getService(Activator.ctx, IArtifactRepositoryManager.class.getName());
		try {
			return artifactRepoMgr.loadRepository(new URI(profile.getProperty("org.eclipse.equinox.p2.bundlepool")), null);
		} catch (ProvisionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}