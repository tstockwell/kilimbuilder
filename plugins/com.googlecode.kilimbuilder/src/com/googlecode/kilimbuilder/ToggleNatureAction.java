package com.googlecode.kilimbuilder;

import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import com.googlecode.kilimbuilder.utils.JDTUtils;
import com.googlecode.kilimbuilder.utils.LogUtils;

public class ToggleNatureAction implements IObjectActionDelegate {

	private ISelection selection;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
	 */
	public void run(IAction action) {
		if (selection instanceof IStructuredSelection) {
			for (Iterator<?> it = ((IStructuredSelection) selection).iterator(); it.hasNext();) {
				Object element = it.next();
				IProject project = null;
				if (element instanceof IProject) {
					project = (IProject) element;
				} else if (element instanceof IAdaptable) {
					project = (IProject) ((IAdaptable) element).getAdapter(IProject.class);
				}
				if (project != null) {
					try {
						JDTUtils.toggleNature(project, KilimNature.KILIM_NATURE_ID);
					} catch (CoreException e) {
						LogUtils.logError(e);
					}
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.IAction,
	 *      org.eclipse.jface.viewers.ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		try {
			this.selection = selection;
			ArrayList<IProject> projects= new ArrayList<IProject>();
			if (selection instanceof IStructuredSelection) {
				for (Iterator<?> it = ((IStructuredSelection) selection).iterator(); it.hasNext();) {
					Object element = it.next();
					IProject project = null;
					if (element instanceof IProject) {
						project = (IProject) element;
					} else if (element instanceof IAdaptable) {
						project = (IProject) ((IAdaptable) element).getAdapter(IProject.class);
					}
					if (project != null) {
						projects.add(project);
					}
				}
			}
			if (projects.isEmpty()) {
				action.setEnabled(false);
			}
			else {
				action.setEnabled(true);
				boolean containsNature= JDTUtils.projectContainsNature(projects.get(0), KilimNature.KILIM_NATURE_ID);
				boolean projectsAreConsistent= true;
				for (int i= 1; i < projects.size(); i++) {
					if (containsNature != JDTUtils.projectContainsNature(projects.get(i), KilimNature.KILIM_NATURE_ID)) {
						projectsAreConsistent= false;
						break;
					}
				}
				if (!projectsAreConsistent) {
					action.setEnabled(false);
				}
				else {
					action.setEnabled(true);
					if (containsNature) {
						action.setText("Remove Kilim Nature");
					}
					else
						action.setText("Add Kilim Nature");
				}
			}
		} catch (CoreException e) {
			action.setEnabled(false);
			LogUtils.logError(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IObjectActionDelegate#setActivePart(org.eclipse.jface.action.IAction,
	 *      org.eclipse.ui.IWorkbenchPart)
	 */
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
	}


}
