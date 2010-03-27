package org.tmatesoft.refactoring.split2.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.viewers.IStructuredSelection;

public class Split2RefactoringModel {

	final CodeFormatter codeFormatter = ToolFactory.createCodeFormatter(null);

	private IStructuredSelection selection;
	private String sourcePackageName;
	private String targetMovePackageName;
	private String targetMoveSuffix;
	private String sourceClassNamePattern;
	private IProject project;
	private IJavaProject javaProject;
	private IPackageFragment sourcePackage;
	private IPackageFragmentRoot packageRoot;
	private String targetStubPackageName;
	private String targetStubSuffix;

	private final List<ICompilationUnit> sourceCompilationUnits = new ArrayList<ICompilationUnit>();
	private final Map<ICompilationUnit, CompilationUnit> parsedUnits = new HashMap<ICompilationUnit, CompilationUnit>();
	private final Map<String, String> targetNamesMap = new HashMap<String, String>();
	private final List<String> sourceMoveClassesNames = new ArrayList<String>();
	private final List<ICompilationUnit> sourceMoveClassesUnits = new ArrayList<ICompilationUnit>();

	public IStructuredSelection getSelection() {
		return selection;
	}

	public void setSelection(IStructuredSelection selection) {
		this.selection = selection;
	}

	public void setSourcePackageName(String sourcePackageName) {
		this.sourcePackageName = sourcePackageName;
	}

	public String getSourcePackageName() {
		return sourcePackageName;
	}

	public void setTargetMovePackageName(String targetMovePackageName) {
		this.targetMovePackageName = targetMovePackageName;
	}

	public String getTargetMovePackageName() {
		return targetMovePackageName;
	}

	public void setTargetMoveSuffix(String targetMoveSuffix) {
		this.targetMoveSuffix = targetMoveSuffix;
	}

	public String getTargetMoveSuffix() {
		return targetMoveSuffix;
	}

	public void setSourceClassNamePattern(String sourceClassNamePattern) {
		this.sourceClassNamePattern = sourceClassNamePattern;
	}

	public String getSourceClassNamePattern() {
		return sourceClassNamePattern;
	}

	public void setProject(IProject project) {
		this.project = project;
	}

	public IProject getProject() {
		return project;
	}

	public void setJavaProject(IJavaProject javaProject) {
		this.javaProject = javaProject;
	}

	public IJavaProject getJavaProject() {
		return javaProject;
	}

	public void setSourcePackage(IPackageFragment sourcePackage) {
		this.sourcePackage = sourcePackage;
	}

	public IPackageFragment getSourcePackage() {
		return sourcePackage;
	}

	public List<ICompilationUnit> getSourceCompilationUnits() {
		return sourceCompilationUnits;
	}

	public void setPackageRoot(IPackageFragmentRoot packageRoot) {
		this.packageRoot = packageRoot;
	}

	public IPackageFragmentRoot getPackageRoot() {
		return packageRoot;
	}

	public Map<ICompilationUnit, CompilationUnit> getParsedUnits() {
		return parsedUnits;
	}

	public CodeFormatter getCodeFormatter() {
		return codeFormatter;
	}

	public Map<String, String> getTargetNamesMap() {
		return targetNamesMap;
	}

	public void setTargetStubPackageName(String targetStubPackageName) {
		this.targetStubPackageName = targetStubPackageName;
	}

	public String getTargetStubPackageName() {
		return targetStubPackageName;
	}

	public void setTargetStubSuffix(String targetStubSuffix) {
		this.targetStubSuffix = targetStubSuffix;
	}

	public String getTargetStubSuffix() {
		return targetStubSuffix;
	}

	public List<String> getSourceMoveClassesNames() {
		return sourceMoveClassesNames;
	}

	public List<ICompilationUnit> getSourceMoveClassesUnits() {
		return sourceMoveClassesUnits;
	}

}
