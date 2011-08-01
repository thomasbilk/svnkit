package org.tmatesoft.refactoring.split2.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreateCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreatePackageChange;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

public class Split2Refactoring extends Refactoring {

	public static final String TITLE = "Split2 refactoring";

	private Split2RefactoringModel model = new Split2RefactoringModel();

	public Split2Refactoring() {
		super();
		model = new Split2RefactoringModel();
		initModel(model);
	}

	private static void initModel(Split2RefactoringModel model) {
		model.getSourcePackageNames().add("org.tmatesoft.svn.core.wc");
		model.getSourcePackageNames().add("org.tmatesoft.svn.core.wc.admin");
		model.setSourceClassNamePattern("SVN[\\w]*Client");
		model.getTargetNamesMap().put("SVNBasicClient", "SVNBasicDelegate");
		model.getSourcesNamesMap().put("SVNCopyDriver", "SVNBasicClient");
		model.getSourcesNamesMap().put("SVNMergeDriver", "SVNBasicClient");
		model.setTargetMovePackageName("org.tmatesoft.svn.core.internal.wc16");
		model.setTargetMoveSuffix("16");
		model.setTargetStubPackageName("org.tmatesoft.svn.core.internal.wc17");
		model.setTargetStubSuffix("17");

		model.getSourceMoveClassesNames().add("org.tmatesoft.svn.core.internal.wc.SVNCopyDriver");
		model.getSourceMoveClassesNames().add("org.tmatesoft.svn.core.internal.wc.SVNMergeDriver");
		model.getSourceMoveClassesNames().add("org.tmatesoft.svn.core.internal.wc.SVNCommitUtil");

		model.getSourceSplitPackages().add("org.tmatesoft.svn.core.wc.admin");
		model.getSplitNamesMap().put("SVNBasicClient", "SVNAdminBasicClient");
	}

	@Override
	public String getName() {
		return TITLE;
	}

	public void setSelection(IStructuredSelection selection) {
		model.setSelection(selection);
	}

	static public void log(Exception exception) {
		Split2RefactoringUtils.log(exception);
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException,
			OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			pm.beginTask("Checking preconditions...", 1);

			if (!searchProject(status)) {
				return status;
			}
			pm.worked(1);

		} finally {
			pm.done();
		}

		return status;
	}

	private boolean searchProject(final RefactoringStatus status) {

		if (model.getSelection() != null) {
			final Object element = model.getSelection().getFirstElement();
			if (element != null && element instanceof IProject) {
				model.setProject((IProject) element);
			}
		}

		if (model.getProject() == null) {
			status.merge(RefactoringStatus.createFatalErrorStatus("Please select project"));
			return false;
		}

		model.setJavaProject(JavaCore.create(model.getProject()));

		if (model.getJavaProject() == null || !model.getJavaProject().exists()) {
			status.merge(RefactoringStatus.createFatalErrorStatus("Please select Java project"));
			return false;
		}

		return true;

	}

	private boolean searchSourceClasses(final RefactoringStatus status, IProgressMonitor pm) throws CoreException {

		final IJavaSearchScope scope = Split2RefactoringUtils.createSearchScope(new IJavaElement[] { model
				.getJavaProject() });

		for (final String sourcePackageName : model.getSourcePackageNames()) {
			final SearchPattern pattern = SearchPattern.createPattern(sourcePackageName, IJavaSearchConstants.PACKAGE,
					IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);

			final IPackageFragment sourcePackage = Split2RefactoringUtils.searchOneElement(IPackageFragment.class,
					pattern, scope, pm);

			if (sourcePackage == null) {
				status.merge(RefactoringStatus.createFatalErrorStatus(String.format(
						"Package '%s' has not been found in selected project", sourcePackageName)));
				return false;
			}

			model.getSourcePackages().add(sourcePackage);
			model.setPackageRoot((IPackageFragmentRoot) sourcePackage.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT));

			final Pattern classNamePattern = Pattern.compile(model.getSourceClassNamePattern());
			final ICompilationUnit[] compilationUnits = sourcePackage.getCompilationUnits();
			for (final ICompilationUnit compilationUnit : compilationUnits) {
				final String typeQualifiedName = compilationUnit.findPrimaryType().getTypeQualifiedName();
				if (classNamePattern.matcher(typeQualifiedName).matches()) {
					model.getSourceCompilationUnits().add(compilationUnit);
				}
			}
			pm.worked(3);

			if (model.getSourceCompilationUnits().size() == 0) {
				status.merge(RefactoringStatus.createFatalErrorStatus(String.format(
						"Not found classes with pattern '%s' in package '%s'", model.getSourceClassNamePattern(),
						sourcePackageName)));
				return false;
			}
		}

		for (final String sourceMoveClassName : model.getSourceMoveClassesNames()) {

			final SearchPattern sourceMoveClassPattern = SearchPattern.createPattern(sourceMoveClassName,
					IJavaSearchConstants.CLASS, IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH);

			final IType sourceMoveClass = Split2RefactoringUtils.searchOneElement(IType.class, sourceMoveClassPattern,
					scope, pm);

			if (sourceMoveClass != null && sourceMoveClass.exists()) {
				model.getSourceMoveClassesUnits().add(sourceMoveClass.getCompilationUnit());
			}

		}

		return true;
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {

		final RefactoringStatus status = new RefactoringStatus();

		try {
			pm.beginTask("Checking preconditions...", 3);
			if (!searchSourceClasses(status, pm)) {
				return status;
			}

			parseSourceClasses(pm);

		} finally {
			pm.done();
		}

		return status;
	}

	private void parseSourceClasses(IProgressMonitor pm) {

		final ASTRequestor requestor = new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				try {
					model.getParsedUnits().put(source, ast);
				} catch (Exception exception) {
					log(exception);
				}
			}
		};

		{
			final ASTParser parser = ASTParser.newParser(AST.JLS3);
			parser.setProject(model.getJavaProject());
			parser.setResolveBindings(true);
			final ICompilationUnit[] units = model.getSourceCompilationUnits().toArray(
					new ICompilationUnit[model.getSourceCompilationUnits().size()]);
			parser.createASTs(units, new String[0], requestor, new SubProgressMonitor(pm, 1));
		}

		{
			final ASTParser parser = ASTParser.newParser(AST.JLS3);
			parser.setProject(model.getJavaProject());
			parser.setResolveBindings(true);
			final ICompilationUnit[] moveUnits = model.getSourceMoveClassesUnits().toArray(
					new ICompilationUnit[model.getSourceMoveClassesUnits().size()]);
			parser.createASTs(moveUnits, new String[0], requestor, new SubProgressMonitor(pm, 1));
		}

	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {

		pm.beginTask("Creating change...", 1);
		final CompositeChange changes = new CompositeChange(TITLE);

		try {
			changes.add(buildChangesTargetMoveClasses(pm));
			changes.add(buildChangesTargetStubClasses(pm));
			changes.add(buildChangesSourceMoveClasses(pm));
			changes.add(buildChangesSourceSplitClasses(pm));
			changes.add(buildChangesSourceStubClasses(pm));
			return changes;
		} catch (MalformedTreeException e) {
			log(e);
			return changes;
		} catch (BadLocationException e) {
			log(e);
			return changes;
		} finally {
			pm.done();
		}

	}

	// Move classes

	private Change buildChangesTargetMoveClasses(IProgressMonitor pm) throws CoreException, MalformedTreeException,
			BadLocationException {

		final CompositeChange changes = new CompositeChange(String.format("Move classes to package '%s'", model
				.getTargetMovePackageName()));

		final IPackageFragment targetPackage = model.getPackageRoot().getPackageFragment(
				model.getTargetMovePackageName());

		if (!targetPackage.exists()) {
			changes.add(new CreatePackageChange(targetPackage));
		}

		final Set<ICompilationUnit> sourceCompilationUnits = model.getSourceCompilationUnits();
		for (final ICompilationUnit sourceUnit : sourceCompilationUnits) {

			final IType sourcePrimaryType = sourceUnit.findPrimaryType();

			final String sourcePackageName = sourcePrimaryType.getPackageFragment().getElementName();
			if (model.getSourceSplitPackages().contains(sourcePackageName)) {
				continue;
			}

			final String sourceTypeName = sourcePrimaryType.getTypeQualifiedName();
			final String targetTypeName = model.getTargetNamesMap().containsKey(sourceTypeName) ? model
					.getTargetNamesMap().get(sourceTypeName) : sourceTypeName + model.getTargetMoveSuffix();

			final ICompilationUnit targetUnit = targetPackage.getCompilationUnit(targetTypeName + ".java");

			final CompilationUnit sourceParsedUnit = model.getParsedUnits().get(sourceUnit);
			final TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceParsedUnit,
					sourcePrimaryType.getSourceRange());

			final AST targetAST = AST.newAST(AST.JLS3);
			final CompilationUnit targetUnitNode = targetAST.newCompilationUnit();

			final PackageDeclaration packageDeclaration = targetAST.newPackageDeclaration();
			packageDeclaration.setName(targetAST.newName(model.getTargetMovePackageName()));
			targetUnitNode.setPackage(packageDeclaration);

			final List<ImportDeclaration> targetImports = targetUnitNode.imports();

			final List<ImportDeclaration> sourceImports = sourceParsedUnit.imports();
			for (final ImportDeclaration sourceImportDeclaration : sourceImports) {
				ImportDeclaration targetImportDeclaration = (ImportDeclaration) ASTNode.copySubtree(targetAST,
						sourceImportDeclaration);
				targetImports.add(targetImportDeclaration);
			}

			final ITypeBinding sourceTypeBinding = sourceTypeDeclaration.resolveBinding();
			final IPackageBinding sourcePackageBinding = sourceTypeBinding.getPackage();

			final ImportDeclaration targetImportDeclaration = targetAST.newImportDeclaration();
			targetImportDeclaration.setOnDemand(true);
			targetImportDeclaration.setName(targetAST.newName(sourcePackageBinding.getName()));
			targetImports.add(targetImportDeclaration);

			final TypeDeclaration targetTypeDeclaration = (TypeDeclaration) ASTNode.copySubtree(targetAST,
					sourceTypeDeclaration);
			targetUnitNode.types().add(targetTypeDeclaration);

			final Type targetSuperclassType = targetTypeDeclaration.getSuperclassType();
			if (targetSuperclassType != null) {
				if (targetSuperclassType.isSimpleType()) {
					final SimpleType targetSuperclassSimpleType = (SimpleType) targetSuperclassType;
					final Name targetSuperclassName = targetSuperclassSimpleType.getName();
					if (targetSuperclassName.isSimpleName()) {
						final SimpleName targetSuperclassSimpleName = (SimpleName) targetSuperclassName;
						final String targetSuperclassIdentifier = targetSuperclassSimpleName.getIdentifier();
						if (model.getTargetNamesMap().containsKey(targetSuperclassIdentifier)) {
							targetSuperclassSimpleName.setIdentifier(model.getTargetNamesMap().get(
									targetSuperclassIdentifier));
						}
					}
				}
			}

			final FieldDeclaration[] fields = targetTypeDeclaration.getFields();
			final MethodDeclaration[] methods = targetTypeDeclaration.getMethods();
			for (final MethodDeclaration methodDeclaration : methods) {
				final String methodName = methodDeclaration.getName().getIdentifier();
				final boolean isGet = methodName.startsWith("get");
				final boolean isSet = methodName.startsWith("set");
				if (isGet || isSet) {
					for (final FieldDeclaration fieldDeclaration : fields) {
						final List<VariableDeclarationFragment> fragments = fieldDeclaration.fragments();
						for (final VariableDeclarationFragment fragment : fragments) {
							final String variableName = fragment.getName().getIdentifier();
							final String accessorName = (isGet ? "get" : "set")
									+ (variableName.startsWith("my") ? variableName.substring(2) : variableName);
							if (methodName.equalsIgnoreCase(accessorName)) {
								final List<Statement> bodyStatements = methodDeclaration.getBody().statements();
								bodyStatements.clear();
								final FieldAccess fieldAccess = targetAST.newFieldAccess();
								fieldAccess.setExpression(targetAST.newThisExpression());
								fieldAccess.setName(targetAST.newSimpleName(variableName));
								if (isGet) {
									final ReturnStatement returnStatement = targetAST.newReturnStatement();
									returnStatement.setExpression(fieldAccess);
									bodyStatements.add(returnStatement);
								} else {
									final List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
									if (parameters.size() == 1) {
										final SingleVariableDeclaration param = parameters.get(0);
										final Assignment assignment = targetAST.newAssignment();
										assignment.setLeftHandSide(fieldAccess);
										assignment.setRightHandSide(targetAST.newName(param.getName().getIdentifier()));
										bodyStatements.add(targetAST.newExpressionStatement(assignment));
									}
								}
							}
						}
					}
				}
			}

			targetUnitNode.accept(new ASTVisitor() {

				@Override
				public boolean visit(SimpleName node) {
					if (sourceTypeName.equals(node.getIdentifier())) {
						node.setIdentifier(targetTypeName);
					}
					return super.visit(node);
				}
			});

			targetUnitNode.accept(new MoveToTargetVisitor());

			final String targetUnitText = targetUnitNode.toString();
			final Document targetUnitDocument = new Document(targetUnitText);
			final TextEdit formatEdit = model.getCodeFormatter().format(CodeFormatter.K_COMPILATION_UNIT,
					targetUnitText, 0, targetUnitText.length(), 0,
					model.getJavaProject().findRecommendedLineSeparator());
			formatEdit.apply(targetUnitDocument);

			changes.add(new CreateCompilationUnitChange(targetUnit, targetUnitDocument.get(), null));

		}

		return changes;
	}

	// Stub classes

	private Change buildChangesTargetStubClasses(IProgressMonitor pm) throws JavaModelException,
			MalformedTreeException, BadLocationException {

		final CompositeChange changes = new CompositeChange(String.format("Create stub classes in package '%s'", model
				.getTargetStubPackageName()));

		final IPackageFragment targetPackage = model.getPackageRoot().getPackageFragment(
				model.getTargetStubPackageName());

		if (!targetPackage.exists()) {
			changes.add(new CreatePackageChange(targetPackage));
		}

		final Set<ICompilationUnit> sourceCompilationUnits = model.getSourceCompilationUnits();
		for (final ICompilationUnit sourceUnit : sourceCompilationUnits) {

			final IType sourcePrimaryType = sourceUnit.findPrimaryType();
			final String sourceTypeName = sourcePrimaryType.getTypeQualifiedName();

			if (model.getTargetNamesMap().containsKey(sourceTypeName)) {
				continue;
			}

			final String sourcePackageName = sourcePrimaryType.getPackageFragment().getElementName();
			if (model.getSourceSplitPackages().contains(sourcePackageName)) {
				continue;
			}

			final String targetTypeName = sourceTypeName + model.getTargetStubSuffix();

			final ICompilationUnit targetUnit = targetPackage.getCompilationUnit(targetTypeName + ".java");

			final CompilationUnit sourceParsedUnit = model.getParsedUnits().get(sourceUnit);
			final TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceParsedUnit,
					sourcePrimaryType.getSourceRange());

			final AST targetAST = AST.newAST(AST.JLS3);
			final CompilationUnit targetUnitNode = targetAST.newCompilationUnit();

			final PackageDeclaration packageDeclaration = targetAST.newPackageDeclaration();
			packageDeclaration.setName(targetAST.newName(model.getTargetStubPackageName()));
			targetUnitNode.setPackage(packageDeclaration);

			final List<ImportDeclaration> targetImports = targetUnitNode.imports();

			final List<ImportDeclaration> sourceImports = sourceParsedUnit.imports();
			for (final ImportDeclaration sourceImportDeclaration : sourceImports) {
				ImportDeclaration targetImportDeclaration = (ImportDeclaration) ASTNode.copySubtree(targetAST,
						sourceImportDeclaration);
				targetImports.add(targetImportDeclaration);
			}

			{
				final ITypeBinding sourceTypeBinding = sourceTypeDeclaration.resolveBinding();
				final IPackageBinding sourcePackageBinding = sourceTypeBinding.getPackage();

				final ImportDeclaration targetImportDeclaration = targetAST.newImportDeclaration();
				targetImportDeclaration.setOnDemand(true);
				targetImportDeclaration.setName(targetAST.newName(sourcePackageBinding.getName()));
				targetImports.add(targetImportDeclaration);
			}

			{
				final ImportDeclaration targetImportDeclaration = targetAST.newImportDeclaration();
				targetImportDeclaration.setOnDemand(true);
				targetImportDeclaration.setName(targetAST.newName(model.getTargetMovePackageName()));
				targetImports.add(targetImportDeclaration);
			}

			addErrorsImports(targetAST, targetUnitNode);

			final TypeDeclaration targetTypeDeclaration = (TypeDeclaration) ASTNode.copySubtree(targetAST,
					sourceTypeDeclaration);
			targetUnitNode.types().add(targetTypeDeclaration);

			final Type targetSuperclassType = targetTypeDeclaration.getSuperclassType();
			if (targetSuperclassType != null) {
				if (targetSuperclassType.isSimpleType()) {
					final SimpleType targetSuperclassSimpleType = (SimpleType) targetSuperclassType;
					final Name targetSuperclassName = targetSuperclassSimpleType.getName();
					if (targetSuperclassName.isSimpleName()) {
						final SimpleName targetSuperclassSimpleName = (SimpleName) targetSuperclassName;
						final String targetSuperclassIdentifier = targetSuperclassSimpleName.getIdentifier();
						if (model.getTargetNamesMap().containsKey(targetSuperclassIdentifier)) {
							targetSuperclassSimpleName.setIdentifier(model.getTargetNamesMap().get(
									targetSuperclassIdentifier));
						}
					}
				}
			}

			final MethodDeclaration[] targetMethods = targetTypeDeclaration.getMethods();
			for (final MethodDeclaration targetMethodDeclaration : targetMethods) {

				if (targetMethodDeclaration.isConstructor()) {
					continue;
				}

				boolean isPublic = false;
				final List<IExtendedModifier> modifiers = targetMethodDeclaration.modifiers();
				for (final IExtendedModifier extendedModifier : modifiers) {
					if (extendedModifier.isModifier()) {
						final Modifier modifier = (Modifier) extendedModifier;
						if (modifier.isPublic()) {
							isPublic = true;
							break;
						}
					}
				}
				if (!isPublic) {
					targetMethodDeclaration.delete();
				} else {
					final SimpleName targetMethodName = targetMethodDeclaration.getName();
					final String targetMethodIdentifier = targetMethodName.getIdentifier();
					if (targetMethodIdentifier.startsWith("do") || targetMethodIdentifier.startsWith("undo")) {
						final List<Statement> statements = targetMethodDeclaration.getBody().statements();
						if (statements != null) {
							final List<Statement> emptyBody = new ArrayList<Statement>();
							insertErrorVersionMismatch(targetAST, targetMethodDeclaration, emptyBody);
							insertDefaultReturn(targetAST, targetMethodDeclaration, emptyBody);
							statements.clear();
							if (!emptyBody.isEmpty()) {
								statements.addAll(emptyBody);
							}
						}
					}
				}
			}

			final TypeDeclaration[] types = targetTypeDeclaration.getTypes();
			for (final TypeDeclaration typeDeclaration : types) {
				boolean isPublic = false;
				final List<IExtendedModifier> modifiers = typeDeclaration.modifiers();
				for (final IExtendedModifier extendedModifier : modifiers) {
					if (extendedModifier.isModifier()) {
						final Modifier modifier = (Modifier) extendedModifier;
						if (modifier.isPublic()) {
							isPublic = true;
							break;
						}
					}
				}
				if (!isPublic) {
					typeDeclaration.delete();
				}
			}

			targetUnitNode.accept(new ASTVisitor() {
				@Override
				public boolean visit(SimpleName node) {
					if (sourceTypeName.equals(node.getIdentifier())) {
						node.setIdentifier(targetTypeName);
					}
					return super.visit(node);
				}
			});

			targetUnitNode.accept(new MoveToTargetVisitor(model.getTargetStubSuffix()));

			final String targetUnitText = targetUnitNode.toString();
			final Document targetUnitDocument = new Document(targetUnitText);
			final TextEdit formatEdit = model.getCodeFormatter().format(CodeFormatter.K_COMPILATION_UNIT,
					targetUnitText, 0, targetUnitText.length(), 0,
					model.getJavaProject().findRecommendedLineSeparator());
			formatEdit.apply(targetUnitDocument);

			changes.add(new CreateCompilationUnitChange(targetUnit, targetUnitDocument.get(), null));

		}

		return changes;
	}

	private void addErrorsImports(final AST ast, final CompilationUnit node) {

		boolean foundSVNLogType = false;
		boolean foundSVNException = false;
		boolean foundSVNErrorCode = false;
		boolean foundSVNErrorMessage = false;
		boolean foundSVNErrorManager = false;

		final List<ImportDeclaration> imports = node.imports();
		for (final ImportDeclaration importDeclaration : imports) {

			final String name = importDeclaration.getName().getFullyQualifiedName();

			if (!foundSVNLogType) {
				if ("org.tmatesoft.svn.util.SVNLogType".equals(name)) {
					foundSVNLogType = true;
					continue;
				}
			}

			if (!foundSVNException) {
				if ("org.tmatesoft.svn.core.SVNException".equals(name)) {
					foundSVNException = true;
					continue;
				}
			}

			if (!foundSVNErrorCode) {
				if ("org.tmatesoft.svn.core.SVNErrorCode".equals(name)) {
					foundSVNErrorCode = true;
					continue;
				}
			}

			if (!foundSVNErrorMessage) {
				if ("org.tmatesoft.svn.core.SVNErrorMessage".equals(name)) {
					foundSVNErrorMessage = true;
					continue;
				}
			}

			if (!foundSVNErrorManager) {
				if ("org.tmatesoft.svn.core.internal.wc.SVNErrorManager".equals(name)) {
					foundSVNErrorManager = true;
					continue;
				}
			}

		}

		if (!foundSVNLogType) {
			addImport(ast, imports, ast.newName("org.tmatesoft.svn.util.SVNLogType"));
		}

		if (!foundSVNException) {
			addImport(ast, imports, ast.newName("org.tmatesoft.svn.core.SVNException"));
		}

		if (!foundSVNErrorCode) {
			addImport(ast, imports, ast.newName("org.tmatesoft.svn.core.SVNErrorCode"));
		}

		if (!foundSVNErrorMessage) {
			addImport(ast, imports, ast.newName("org.tmatesoft.svn.core.SVNErrorMessage"));
		}

		if (!foundSVNErrorManager) {
			addImport(ast, imports, ast.newName("org.tmatesoft.svn.core.internal.wc.SVNErrorManager"));
		}

	}

	private void addImport(final AST ast, final List<ImportDeclaration> imports, final Name name) {
		final ImportDeclaration importDeclaration = ast.newImportDeclaration();
		importDeclaration.setOnDemand(false);
		importDeclaration.setName(name);
		imports.add(importDeclaration);
	}

	private void insertErrorVersionMismatch(final AST ast, final MethodDeclaration methodCopy,
			final List<Statement> emptyBody) {

		boolean found = false;
		final List<Name> thrownExceptions = methodCopy.thrownExceptions();
		for (final Name name : thrownExceptions) {
			if (name.getFullyQualifiedName().endsWith("SVNException")) {
				found = true;
				break;
			}
		}
		if (!found) {
			return;
		}

		final MethodInvocation invoc1 = ast.newMethodInvocation();
		invoc1.setExpression(ast.newSimpleName("SVNErrorMessage"));
		invoc1.setName(ast.newSimpleName("create"));
		final List<Expression> args1 = invoc1.arguments();
		args1.add(ast.newQualifiedName(ast.newSimpleName("SVNErrorCode"), ast.newSimpleName("WC_UNSUPPORTED_FORMAT")));

		final VariableDeclarationFragment varF = ast.newVariableDeclarationFragment();
		varF.setName(ast.newSimpleName("err"));
		varF.setInitializer(invoc1);
		final VariableDeclarationStatement varS = ast.newVariableDeclarationStatement(varF);
		varS.setType(ast.newSimpleType(ast.newSimpleName("SVNErrorMessage")));
		emptyBody.add(varS);

		final MethodInvocation invoc2 = ast.newMethodInvocation();
		invoc2.setExpression(ast.newSimpleName("SVNErrorManager"));
		invoc2.setName(ast.newSimpleName("error"));
		final List<Expression> args2 = invoc2.arguments();
		args2.add(ast.newSimpleName("err"));
		args2.add(ast.newQualifiedName(ast.newSimpleName("SVNLogType"), ast.newSimpleName("CLIENT")));
		emptyBody.add(ast.newExpressionStatement(invoc2));

	}

	private void insertDefaultReturn(final AST ast, final MethodDeclaration methodCopy, final List<Statement> emptyBody) {
		final Type returnType = methodCopy.getReturnType2();
		final ReturnStatement newReturnStatement = ast.newReturnStatement();
		if (returnType instanceof PrimitiveType) {
			final PrimitiveType returnPrimitive = (PrimitiveType) returnType;
			final Code code = returnPrimitive.getPrimitiveTypeCode();
			if (code != PrimitiveType.VOID) {
				if (code == PrimitiveType.BYTE || code == PrimitiveType.CHAR || code == PrimitiveType.SHORT
						|| code == PrimitiveType.INT || code == PrimitiveType.LONG) {
					newReturnStatement.setExpression(ast.newNumberLiteral("0"));
				} else if (code == PrimitiveType.FLOAT || code == PrimitiveType.DOUBLE) {
					newReturnStatement.setExpression(ast.newNumberLiteral("0.0"));
				} else if (code == PrimitiveType.BOOLEAN) {
					newReturnStatement.setExpression(ast.newBooleanLiteral(false));
				}
				emptyBody.add(newReturnStatement);
			}
		} else {
			newReturnStatement.setExpression(ast.newNullLiteral());
			emptyBody.add(newReturnStatement);
		}
	}

	// Dispatcher classes

	private Change buildChangesSourceMoveClasses(IProgressMonitor pm) throws JavaModelException,
			MalformedTreeException, BadLocationException {

		final CompositeChange changes = new CompositeChange(
				"Modify dependent classes to inherits from moved base delegate");

		final Set<ICompilationUnit> sourceMoveUnits = model.getSourceMoveClassesUnits();
		for (final ICompilationUnit sourceUnit : sourceMoveUnits) {

			boolean changed = false;

			final IType sourcePrimaryType = sourceUnit.findPrimaryType();
			final String sourceTypeName = sourcePrimaryType.getTypeQualifiedName();

			final String sourcePackageName = sourcePrimaryType.getPackageFragment().getElementName();
			if (model.getSourceSplitPackages().contains(sourcePackageName)) {
				continue;
			}

			final String sourceUnitText = sourceUnit.getSource();
			final Document sourceUnitDocument = new Document(sourceUnitText);

			final CompilationUnit sourceParsedUnit = model.getParsedUnits().get(sourceUnit);
			final AST sourceAst = sourceParsedUnit.getAST();

			final TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceParsedUnit,
					sourcePrimaryType.getSourceRange());

			sourceParsedUnit.recordModifications();

			final Type sourceSuperclassType = sourceTypeDeclaration.getSuperclassType();
			if (sourceSuperclassType != null) {
				if (sourceSuperclassType.isSimpleType()) {
					final SimpleType sourceSuperclassSimpleType = (SimpleType) sourceSuperclassType;
					final Name sourceSuperclassName = sourceSuperclassSimpleType.getName();
					if (sourceSuperclassName.isSimpleName()) {
						final SimpleName sourceSuperclassSimpleName = (SimpleName) sourceSuperclassName;
						final String sourceSuperclassIdentifier = sourceSuperclassSimpleName.getIdentifier();
						if (model.getTargetNamesMap().containsKey(sourceSuperclassIdentifier)) {

							final String targetTypeName = model.getTargetNamesMap().get(sourceSuperclassIdentifier);
							sourceSuperclassSimpleName.setIdentifier(targetTypeName);

							changed = true;

						}
					}
				}
			}

			MoveToTargetVisitor visitor = new MoveToTargetVisitor();
			sourceParsedUnit.accept(visitor);
			if (visitor.changed) {
				changed = true;
			}

			if (changed) {

				final List<ImportDeclaration> sourceImports = sourceParsedUnit.imports();
				final List<ImportDeclaration> importsToDelete = new ArrayList<ImportDeclaration>();
				for (final ImportDeclaration importDeclaration : sourceImports) {
					final Name name = importDeclaration.getName();
					final Set<ICompilationUnit> sourceCompilationUnits = model.getSourceCompilationUnits();
					for (ICompilationUnit unit : sourceCompilationUnits) {
						final IType type = unit.findPrimaryType();
						if (type.getFullyQualifiedName().equals(name.getFullyQualifiedName())) {
							importsToDelete.add(importDeclaration);
							break;
						}
					}
				}
				for (final ImportDeclaration importDeclaration : importsToDelete) {
					sourceImports.remove(importDeclaration);
				}
				{
					final ImportDeclaration importDeclaration = sourceAst.newImportDeclaration();
					importDeclaration.setOnDemand(true);
					importDeclaration.setName(sourceAst.newName(model.getTargetMovePackageName()));
					sourceImports.add(importDeclaration);
				}

				final TextEdit sourceUnitEdits = sourceParsedUnit.rewrite(sourceUnitDocument, model.getJavaProject()
						.getOptions(true));
				final TextFileChange sourceTextFileChange = new TextFileChange(sourceUnit.getElementName(),
						(IFile) sourceUnit.getResource());
				sourceTextFileChange.setEdit(sourceUnitEdits);
				changes.add(sourceTextFileChange);
			}

		}

		return changes;
	}

	private Change buildChangesSourceStubClasses(IProgressMonitor pm) throws JavaModelException,
			MalformedTreeException, BadLocationException {

		final CompositeChange changes = new CompositeChange(String.format("Modify API classes to work as dispatchers"));

		final Set<ICompilationUnit> sourceCompilationUnits = model.getSourceCompilationUnits();
		for (final ICompilationUnit sourceUnit : sourceCompilationUnits) {

			final IType sourcePrimaryType = sourceUnit.findPrimaryType();
			final String sourceTypeName = sourcePrimaryType.getTypeQualifiedName();

			final String sourcePackageName = sourcePrimaryType.getPackageFragment().getElementName();
			if (model.getSourceSplitPackages().contains(sourcePackageName)) {
				continue;
			}

			final String sourceUnitText = sourceUnit.getSource();
			final Document sourceUnitDocument = new Document(sourceUnitText);

			final CompilationUnit sourceParsedUnit = model.getParsedUnits().get(sourceUnit);
			final AST sourceAst = sourceParsedUnit.getAST();

			sourceParsedUnit.recordModifications();

			final List<ImportDeclaration> sourceImports = sourceParsedUnit.imports();
			{
				final ImportDeclaration importDeclaration = sourceAst.newImportDeclaration();
				importDeclaration.setOnDemand(true);
				importDeclaration.setName(sourceAst.newName(model.getTargetMovePackageName()));
				sourceImports.add(importDeclaration);
			}

			{
				final ImportDeclaration importDeclaration = sourceAst.newImportDeclaration();
				importDeclaration.setOnDemand(true);
				importDeclaration.setName(sourceAst.newName(model.getTargetStubPackageName()));
				sourceImports.add(importDeclaration);
			}

			final TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceParsedUnit,
					sourcePrimaryType.getSourceRange());

			final FieldDeclaration[] sourceFields = sourceTypeDeclaration.getFields();
			for (final FieldDeclaration sourceFieldDeclaration : sourceFields) {
				boolean isPublic = false;
				final List<IExtendedModifier> modifiers = sourceFieldDeclaration.modifiers();
				for (final IExtendedModifier extendedModifier : modifiers) {
					if (extendedModifier.isModifier()) {
						final Modifier modifier = (Modifier) extendedModifier;
						if (modifier.isPublic()) {
							isPublic = true;
							break;
						}
					}
				}
				if (!isPublic) {
					sourceFieldDeclaration.delete();
				}
			}

			final Set<MethodDeclaration> delegateMethods = new HashSet<MethodDeclaration>();
			final MethodDeclaration[] sourceMethods = sourceTypeDeclaration.getMethods();
			for (final MethodDeclaration sourceMethodDeclaration : sourceMethods) {
				boolean isPublic = false;
				final List<IExtendedModifier> modifiers = sourceMethodDeclaration.modifiers();
				for (final IExtendedModifier extendedModifier : modifiers) {
					if (extendedModifier.isModifier()) {
						final Modifier modifier = (Modifier) extendedModifier;
						if (modifier.isPublic()) {
							isPublic = true;
							break;
						}
					}
				}
				if (!isPublic) {
					sourceMethodDeclaration.delete();
				} else {
					delegateMethods.add(sourceMethodDeclaration);
				}
			}

			final Map<String, Expression> initializators = new HashMap<String, Expression>();
			final Set<String> setters = new HashSet<String>();
			for (final MethodDeclaration sourceMethodDeclaration : delegateMethods) {
				prepareDelegateMethod(sourceAst, sourceMethodDeclaration, sourceTypeName, initializators, setters);
			}

			for (final MethodDeclaration sourceMethodDeclaration : delegateMethods) {
				delegateMethod(sourceAst, sourceMethodDeclaration, sourceTypeName, initializators, setters);
			}

			final TypeDeclaration[] types = sourceTypeDeclaration.getTypes();
			for (final TypeDeclaration typeDeclaration : types) {
				boolean isPublic = false;
				final List<IExtendedModifier> modifiers = typeDeclaration.modifiers();
				for (final IExtendedModifier extendedModifier : modifiers) {
					if (extendedModifier.isModifier()) {
						final Modifier modifier = (Modifier) extendedModifier;
						if (modifier.isPublic()) {
							isPublic = true;
							break;
						}
					}
				}
				if (!isPublic) {
					typeDeclaration.delete();
				}
			}

			if (model.getTargetNamesMap().containsKey(sourceTypeName)) {
				addBasicDelegatesConstructor(sourceAst, sourceTypeDeclaration, sourceTypeName, setters);
			} else {
				final Type sourceSuperclassType = sourceTypeDeclaration.getSuperclassType();
				if (sourceSuperclassType != null) {
					if (sourceSuperclassType.isSimpleType()) {
						final SimpleType sourceSuperclassSimpleType = (SimpleType) sourceSuperclassType;
						final Name sourceSuperclassName = sourceSuperclassSimpleType.getName();
						if (sourceSuperclassName.isSimpleName()) {
							final SimpleName sourceSuperclassSimpleName = (SimpleName) sourceSuperclassName;
							final String sourceSuperclassIdentifier = sourceSuperclassSimpleName.getIdentifier();
							if (model.getSourcesNamesMap().containsKey(sourceSuperclassIdentifier)) {
								sourceSuperclassSimpleName.setIdentifier(model.getSourcesNamesMap().get(
										sourceSuperclassIdentifier));
							}
						}
					}
				}
				addDelegateAccessors(sourceAst, sourceTypeDeclaration, sourceTypeName);
			}

			final TextEdit sourceUnitEdits = sourceParsedUnit.rewrite(sourceUnitDocument, model.getJavaProject()
					.getOptions(true));
			final TextFileChange sourceTextFileChange = new TextFileChange(sourceUnit.getElementName(),
					(IFile) sourceUnit.getResource());
			sourceTextFileChange.setEdit(sourceUnitEdits);
			changes.add(sourceTextFileChange);

		}

		return changes;
	}

	private void addDelegateAccessors(AST sourceAst, TypeDeclaration sourceTypeDeclaration, String sourceTypeName) {

		final List<BodyDeclaration> bodyDeclarations = sourceTypeDeclaration.bodyDeclarations();

		{
			final String suffix = model.getTargetMoveSuffix();
			final MethodDeclaration getter = sourceAst.newMethodDeclaration();
			bodyDeclarations.add(0, getter);
			getter.setName(sourceAst.newSimpleName("get" + sourceTypeName + suffix));
			getter.setReturnType2(sourceAst.newSimpleType(sourceAst.newName(sourceTypeName + suffix)));
			getter.modifiers().add(sourceAst.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

			final ReturnStatement newReturnStatement = sourceAst.newReturnStatement();
			final Block getterBody = sourceAst.newBlock();
			getter.setBody(getterBody);
			final ReturnStatement returnStatement = sourceAst.newReturnStatement();

			final MethodInvocation invocation = sourceAst.newMethodInvocation();
			invocation.setName(sourceAst.newSimpleName("getDelegate" + suffix));

			final CastExpression cast = sourceAst.newCastExpression();
			cast.setExpression(invocation);
			cast.setType(sourceAst.newSimpleType(sourceAst.newSimpleName(sourceTypeName + suffix)));

			returnStatement.setExpression(cast);
			getterBody.statements().add(returnStatement);
		}

		{
			final String suffix = model.getTargetStubSuffix();
			final MethodDeclaration getter = sourceAst.newMethodDeclaration();
			bodyDeclarations.add(1, getter);
			getter.setName(sourceAst.newSimpleName("get" + sourceTypeName + suffix));
			getter.setReturnType2(sourceAst.newSimpleType(sourceAst.newName(sourceTypeName + suffix)));
			getter.modifiers().add(sourceAst.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

			final ReturnStatement newReturnStatement = sourceAst.newReturnStatement();
			final Block getterBody = sourceAst.newBlock();
			getter.setBody(getterBody);
			final ReturnStatement returnStatement = sourceAst.newReturnStatement();

			final MethodInvocation invocation = sourceAst.newMethodInvocation();
			invocation.setName(sourceAst.newSimpleName("getDelegate" + suffix));

			final CastExpression cast = sourceAst.newCastExpression();
			cast.setExpression(invocation);
			cast.setType(sourceAst.newSimpleType(sourceAst.newSimpleName(sourceTypeName + suffix)));

			returnStatement.setExpression(cast);
			getterBody.statements().add(returnStatement);
		}
	}

	private void addBasicDelegatesConstructor(AST sourceAst, TypeDeclaration sourceTypeDeclaration,
			String sourceTypeName, Set<String> setters) {

		final String targetTypeName = model.getTargetNamesMap().get(sourceTypeName);

		final List<BodyDeclaration> bodyDeclarations = sourceTypeDeclaration.bodyDeclarations();

		{
			final String suffix = model.getTargetMoveSuffix();
			final VariableDeclarationFragment varF = sourceAst.newVariableDeclarationFragment();
			varF.setName(sourceAst.newSimpleName("delegate" + suffix));
			final FieldDeclaration fieldDecl = sourceAst.newFieldDeclaration(varF);
			fieldDecl.modifiers().add(sourceAst.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
			fieldDecl.setType(sourceAst.newSimpleType(sourceAst.newSimpleName(targetTypeName)));
			bodyDeclarations.add(0, fieldDecl);
		}

		{
			final String suffix = model.getTargetStubSuffix();
			final VariableDeclarationFragment varF = sourceAst.newVariableDeclarationFragment();
			varF.setName(sourceAst.newSimpleName("delegate" + suffix));
			final FieldDeclaration fieldDecl = sourceAst.newFieldDeclaration(varF);
			fieldDecl.modifiers().add(sourceAst.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
			fieldDecl.setType(sourceAst.newSimpleType(sourceAst.newSimpleName(targetTypeName)));
			bodyDeclarations.add(1, fieldDecl);
		}

		{
			final MethodDeclaration constructorDecl = sourceAst.newMethodDeclaration();
			bodyDeclarations.add(2, constructorDecl);
			constructorDecl.setConstructor(true);
			constructorDecl.setName(sourceAst.newSimpleName(sourceTypeName));
			constructorDecl.modifiers().add(sourceAst.newModifier(Modifier.ModifierKeyword.PROTECTED_KEYWORD));

			{
				final String suffix = model.getTargetMoveSuffix();
				final SingleVariableDeclaration constructorParam = sourceAst.newSingleVariableDeclaration();
				constructorParam.setType(sourceAst.newSimpleType(sourceAst.newSimpleName(targetTypeName)));
				constructorParam.setName(sourceAst.newSimpleName("delegate" + suffix));
				constructorDecl.parameters().add(constructorParam);
			}
			{
				final String suffix = model.getTargetStubSuffix();
				final SingleVariableDeclaration constructorParam = sourceAst.newSingleVariableDeclaration();
				constructorParam.setType(sourceAst.newSimpleType(sourceAst.newSimpleName(targetTypeName)));
				constructorParam.setName(sourceAst.newSimpleName("delegate" + suffix));
				constructorDecl.parameters().add(constructorParam);
			}

			final Block constructorBody = sourceAst.newBlock();
			constructorDecl.setBody(constructorBody);

			{
				final String suffix = model.getTargetMoveSuffix();
				final Assignment assign = sourceAst.newAssignment();
				constructorBody.statements().add(sourceAst.newExpressionStatement(assign));
				final FieldAccess fieldAccess = sourceAst.newFieldAccess();
				fieldAccess.setExpression(sourceAst.newThisExpression());
				fieldAccess.setName(sourceAst.newSimpleName("delegate" + suffix));
				assign.setLeftHandSide(fieldAccess);
				assign.setRightHandSide(sourceAst.newName("delegate" + suffix));
			}

			{
				final String suffix = model.getTargetStubSuffix();
				final Assignment assign = sourceAst.newAssignment();
				constructorBody.statements().add(sourceAst.newExpressionStatement(assign));
				final FieldAccess fieldAccess = sourceAst.newFieldAccess();
				fieldAccess.setExpression(sourceAst.newThisExpression());
				fieldAccess.setName(sourceAst.newSimpleName("delegate" + suffix));
				assign.setLeftHandSide(fieldAccess);
				assign.setRightHandSide(sourceAst.newName("delegate" + suffix));
			}

			for (String setter : setters) {
				final MethodInvocation invoc = sourceAst.newMethodInvocation();
				invoc.setName(sourceAst.newSimpleName(setter));
				invoc.arguments().add(sourceAst.newNullLiteral());
				constructorBody.statements().add(sourceAst.newExpressionStatement(invoc));
			}

		}

		{
			final String suffix = model.getTargetMoveSuffix();
			final MethodDeclaration getter = sourceAst.newMethodDeclaration();
			bodyDeclarations.add(3, getter);
			getter.setName(sourceAst.newSimpleName("getDelegate" + suffix));
			getter.setReturnType2(sourceAst.newSimpleType(sourceAst.newName(targetTypeName)));
			getter.modifiers().add(sourceAst.newModifier(Modifier.ModifierKeyword.PROTECTED_KEYWORD));
			final ReturnStatement newReturnStatement = sourceAst.newReturnStatement();
			final Block getterBody = sourceAst.newBlock();
			getter.setBody(getterBody);
			final ReturnStatement returnStatement = sourceAst.newReturnStatement();
			final FieldAccess fieldAccess = sourceAst.newFieldAccess();
			fieldAccess.setExpression(sourceAst.newThisExpression());
			fieldAccess.setName(sourceAst.newSimpleName("delegate" + suffix));
			returnStatement.setExpression(fieldAccess);
			getterBody.statements().add(returnStatement);
		}

		{
			final String suffix = model.getTargetStubSuffix();
			final MethodDeclaration getter = sourceAst.newMethodDeclaration();
			bodyDeclarations.add(3, getter);
			getter.setName(sourceAst.newSimpleName("getDelegate" + suffix));
			getter.setReturnType2(sourceAst.newSimpleType(sourceAst.newName(targetTypeName)));
			getter.modifiers().add(sourceAst.newModifier(Modifier.ModifierKeyword.PROTECTED_KEYWORD));
			final ReturnStatement newReturnStatement = sourceAst.newReturnStatement();
			final Block getterBody = sourceAst.newBlock();
			getter.setBody(getterBody);
			final ReturnStatement returnStatement = sourceAst.newReturnStatement();
			final FieldAccess fieldAccess = sourceAst.newFieldAccess();
			fieldAccess.setExpression(sourceAst.newThisExpression());
			fieldAccess.setName(sourceAst.newSimpleName("delegate" + suffix));
			returnStatement.setExpression(fieldAccess);
			getterBody.statements().add(returnStatement);
		}

	}

	private void prepareDelegateMethod(final AST sourceAst, final MethodDeclaration sourceMethodDeclaration,
			final String sourceTypeName, Map<String, Expression> initializators, Set<String> setters) {

		boolean isSVNExceptionThrown = false;
		final List<Name> thrownExceptions = sourceMethodDeclaration.thrownExceptions();
		for (final Name name : thrownExceptions) {
			if (name.isSimpleName()) {
				final SimpleName simpleName = (SimpleName) name;
				if (simpleName.getIdentifier().equals("SVNException")) {
					isSVNExceptionThrown = true;
					break;
				}
			}
		}
		final SimpleName sourceMethodName = sourceMethodDeclaration.getName();
		final String sourceMethodIdentifier = sourceMethodName.getIdentifier();
		final List<Statement> statements = sourceMethodDeclaration.getBody().statements();
		if (statements != null) {
			if (sourceMethodDeclaration.isConstructor()) {

			} else if (sourceMethodIdentifier.startsWith("do") || sourceMethodIdentifier.startsWith("undo")
					|| isSVNExceptionThrown) {

			} else if (sourceMethodIdentifier.startsWith("get") || sourceMethodIdentifier.startsWith("is")) {
				prepareDispatchGetMethod(sourceAst, sourceMethodDeclaration, sourceTypeName, initializators);
			} else if (sourceMethodIdentifier.startsWith("set")) {
				prepareDispatchSetMethod(sourceAst, sourceMethodDeclaration, sourceTypeName, setters);
			} else {
				return;
			}
		}
	}

	private void delegateMethod(final AST sourceAst, final MethodDeclaration sourceMethodDeclaration,
			final String sourceTypeName, Map<String, Expression> initializators, Set<String> setters) {

		boolean isSVNExceptionThrown = false;
		final List<Name> thrownExceptions = sourceMethodDeclaration.thrownExceptions();
		for (final Name name : thrownExceptions) {
			if (name.isSimpleName()) {
				final SimpleName simpleName = (SimpleName) name;
				if (simpleName.getIdentifier().equals("SVNException")) {
					isSVNExceptionThrown = true;
					break;
				}
			}
		}
		final SimpleName sourceMethodName = sourceMethodDeclaration.getName();
		final String sourceMethodIdentifier = sourceMethodName.getIdentifier();
		final List<Statement> statements = sourceMethodDeclaration.getBody().statements();
		if (statements != null) {
			final List<Statement> emptyBody = new ArrayList<Statement>();
			if (sourceMethodDeclaration.isConstructor()) {
				dispatchConstructor(sourceAst, sourceMethodDeclaration, emptyBody, setters);
			} else if (sourceMethodIdentifier.startsWith("do") || sourceMethodIdentifier.startsWith("undo")
					|| isSVNExceptionThrown) {
				dispatchDoMethod(sourceAst, sourceMethodDeclaration, emptyBody, sourceTypeName);
			} else if (sourceMethodIdentifier.startsWith("get") || sourceMethodIdentifier.startsWith("is")) {
				dispatchGetMethod(sourceAst, sourceMethodDeclaration, emptyBody, sourceTypeName);
			} else if (sourceMethodIdentifier.startsWith("set")) {
				dispatchSetMethod(sourceAst, sourceMethodDeclaration, emptyBody, sourceTypeName, initializators);
			} else {
				return;
			}
			if (!emptyBody.isEmpty()) {
				statements.clear();
				statements.addAll(emptyBody);
			}
		}
	}

	private void dispatchConstructor(AST sourceAst, MethodDeclaration sourceMethodDeclaration,
			List<Statement> emptyBody, Set<String> setters) {

		final String identifier = sourceMethodDeclaration.getName().getIdentifier();

		final List<SingleVariableDeclaration> parameters = sourceMethodDeclaration.parameters();

		final ClassInstanceCreation constructor16 = sourceAst.newClassInstanceCreation();
		constructor16.setType(sourceAst
				.newSimpleType(sourceAst.newSimpleName(identifier + model.getTargetMoveSuffix())));
		for (SingleVariableDeclaration parameter : parameters) {
			constructor16.arguments().add(sourceAst.newSimpleName(parameter.getName().getIdentifier()));
		}

		final ClassInstanceCreation constructor17 = sourceAst.newClassInstanceCreation();
		constructor17.setType(sourceAst
				.newSimpleType(sourceAst.newSimpleName(identifier + model.getTargetStubSuffix())));
		for (SingleVariableDeclaration parameter : parameters) {
			constructor17.arguments().add(sourceAst.newSimpleName(parameter.getName().getIdentifier()));
		}

		final SuperConstructorInvocation superInvoke = sourceAst.newSuperConstructorInvocation();
		final List arguments = superInvoke.arguments();
		arguments.add(constructor16);
		arguments.add(constructor17);
		emptyBody.add(superInvoke);

		for (String setter : setters) {
			final MethodInvocation invoc = sourceAst.newMethodInvocation();
			invoc.setName(sourceAst.newSimpleName(setter));
			invoc.arguments().add(sourceAst.newNullLiteral());
			emptyBody.add(sourceAst.newExpressionStatement(invoc));
		}

	}

	private void dispatchDoMethod(AST sourceAst, MethodDeclaration sourceMethodDeclaration, List<Statement> emptyBody,
			String sourceTypeName) {

		boolean isReturn = true;
		final Type returnType = sourceMethodDeclaration.getReturnType2();
		if (returnType.isPrimitiveType()) {
			final PrimitiveType primitiveType = (PrimitiveType) returnType;
			final Code code = primitiveType.getPrimitiveTypeCode();
			if (PrimitiveType.VOID.equals(code)) {
				isReturn = false;
			}
		}

		final String dispatchTypeName = model.getTargetNamesMap().containsKey(sourceTypeName) ? "Delegate"
				: sourceTypeName;

		final TryStatement tryStatement = sourceAst.newTryStatement();
		final List<CatchClause> catchClauses = tryStatement.catchClauses();
		final CatchClause catchClause = sourceAst.newCatchClause();
		final SingleVariableDeclaration exception = sourceAst.newSingleVariableDeclaration();
		exception.setType(sourceAst.newSimpleType(sourceAst.newSimpleName("SVNException")));
		exception.setName(sourceAst.newSimpleName("e"));
		catchClause.setException(exception);
		catchClauses.add(catchClause);
		emptyBody.add(tryStatement);

		{
			final Block tryBody = tryStatement.getBody();
			final List<Statement> tryStatements = tryBody.statements();

			final MethodInvocation invoc1 = sourceAst.newMethodInvocation();
			final MethodInvocation invoc2 = sourceAst.newMethodInvocation();
			invoc2.setName(sourceAst.newSimpleName("get" + dispatchTypeName + model.getTargetStubSuffix()));
			invoc1.setExpression(invoc2);

			invoc1.setName(sourceAst.newSimpleName(sourceMethodDeclaration.getName().getIdentifier()));
			final List<Expression> arguments = invoc1.arguments();
			final List<SingleVariableDeclaration> parameters = sourceMethodDeclaration.parameters();
			for (SingleVariableDeclaration parameter : parameters) {
				arguments.add(sourceAst.newSimpleName(parameter.getName().getIdentifier()));
			}

			if (isReturn) {
				final ReturnStatement returnStatement = sourceAst.newReturnStatement();
				returnStatement.setExpression(invoc1);
				tryStatements.add(returnStatement);
			} else {
				tryStatements.add(sourceAst.newExpressionStatement(invoc1));
			}
		}

		{
			final Block catchBody = catchClause.getBody();
			final List<Statement> catchStatements = catchBody.statements();

			final IfStatement ifStatement = sourceAst.newIfStatement();
			catchStatements.add(ifStatement);

			final InfixExpression infix = sourceAst.newInfixExpression();
			ifStatement.setExpression(infix);

			final MethodInvocation invocMessage = sourceAst.newMethodInvocation();
			invocMessage.setExpression(sourceAst.newSimpleName("e"));
			invocMessage.setName(sourceAst.newSimpleName("getErrorMessage"));
			final MethodInvocation invocCode = sourceAst.newMethodInvocation();
			invocCode.setExpression(invocMessage);
			invocCode.setName(sourceAst.newSimpleName("getErrorCode"));

			infix.setLeftOperand(invocCode);
			infix.setOperator(InfixExpression.Operator.EQUALS);

			final FieldAccess fieldAccess = sourceAst.newFieldAccess();
			infix.setRightOperand(fieldAccess);
			fieldAccess.setExpression(sourceAst.newName("SVNErrorCode"));
			fieldAccess.setName(sourceAst.newSimpleName("WC_UNSUPPORTED_FORMAT"));

			final Block thenBlock = sourceAst.newBlock();

			ifStatement.setThenStatement(thenBlock);

			final MethodInvocation invoc1 = sourceAst.newMethodInvocation();
			final MethodInvocation invoc2 = sourceAst.newMethodInvocation();
			invoc2.setName(sourceAst.newSimpleName("get" + dispatchTypeName + model.getTargetMoveSuffix()));
			invoc1.setExpression(invoc2);

			invoc1.setName(sourceAst.newSimpleName(sourceMethodDeclaration.getName().getIdentifier()));
			final List<Expression> arguments = invoc1.arguments();
			final List<SingleVariableDeclaration> parameters = sourceMethodDeclaration.parameters();
			for (SingleVariableDeclaration parameter : parameters) {
				arguments.add(sourceAst.newSimpleName(parameter.getName().getIdentifier()));
			}

			if (isReturn) {
				final ReturnStatement returnStatement = sourceAst.newReturnStatement();
				returnStatement.setExpression(invoc1);
				thenBlock.statements().add(returnStatement);
			} else {
				thenBlock.statements().add(sourceAst.newExpressionStatement(invoc1));
				thenBlock.statements().add(sourceAst.newReturnStatement());
			}

			final ThrowStatement throwStatement = sourceAst.newThrowStatement();
			throwStatement.setExpression(sourceAst.newSimpleName("e"));
			catchStatements.add(throwStatement);

		}

	}

	private void prepareDispatchGetMethod(AST sourceAst, MethodDeclaration sourceMethodDeclaration,
			String sourceTypeName, Map<String, Expression> initializators) {

		final Type sourceReturnType = sourceMethodDeclaration.getReturnType2();

		if (sourceReturnType.isPrimitiveType()) {
			return;
		}

		if (!sourceReturnType.isSimpleType()) {
			return;
		}

		final SimpleType sourceReturnSimpleType = (SimpleType) sourceReturnType;
		final SimpleName sourceReturnTypeName = (SimpleName) sourceReturnSimpleType.getName();

		Expression initializer = null;

		if (!"ISVNOptions".equals(sourceReturnTypeName.getIdentifier())
				&& !"ISVNDebugLog".equals(sourceReturnTypeName.getIdentifier())) {

			class Visitor extends ASTVisitor {
				public Expression expression = null;

				@Override
				public boolean visit(ClassInstanceCreation node) {
					if (expression == null) {
						expression = node;
					}
					return false;
				}

				public boolean visit(Assignment node) {
					if (expression == null) {
						expression = node.getRightHandSide();
					}
					return false;
				};
			}

			Visitor visitor = new Visitor();
			sourceMethodDeclaration.accept(visitor);
			initializer = visitor.expression;
		}

		{

			if ("ISVNOptions".equals(sourceReturnTypeName.getIdentifier())) {
				final MethodInvocation invoke = sourceAst.newMethodInvocation();
				invoke.setExpression(sourceAst.newSimpleName("SVNWCUtil"));
				invoke.setName(sourceAst.newSimpleName("createDefaultOptions"));
				invoke.arguments().add(sourceAst.newBooleanLiteral(true));
				initializators.put(sourceReturnTypeName.getIdentifier(), invoke);
			} else if ("ISVNDebugLog".equals(sourceReturnTypeName.getIdentifier())) {
				final MethodInvocation invoke = sourceAst.newMethodInvocation();
				invoke.setExpression(sourceAst.newSimpleName("SVNDebugLog"));
				invoke.setName(sourceAst.newSimpleName("getDefaultLog"));
				initializators.put(sourceReturnTypeName.getIdentifier(), invoke);
			} else if (initializer != null) {
				initializators.put(sourceReturnTypeName.getIdentifier(), (Expression) ASTNode.copySubtree(sourceAst,
						initializer));
			} else {
				final ClassInstanceCreation create = sourceAst.newClassInstanceCreation();
				create.setType(sourceAst.newSimpleType(sourceAst.newSimpleName(sourceReturnTypeName.getIdentifier())));
				initializators.put(sourceReturnTypeName.getIdentifier(), create);
			}

		}

	}

	private void prepareDispatchSetMethod(AST sourceAst, MethodDeclaration sourceMethodDeclaration,
			String sourceTypeName, Set<String> setters) {

		final Type paramType = ((SingleVariableDeclaration) sourceMethodDeclaration.parameters().get(0)).getType();

		if (!paramType.isPrimitiveType()) {
			setters.add(sourceMethodDeclaration.getName().getIdentifier());
		}

	}

	private void dispatchGetMethod(AST sourceAst, MethodDeclaration sourceMethodDeclaration, List<Statement> emptyBody,
			String sourceTypeName) {

		final String sourceMethodIdentifier = sourceMethodDeclaration.getName().getIdentifier();

		final String dispatchTypeName = model.getTargetNamesMap().containsKey(sourceTypeName) ? "Delegate"
				: sourceTypeName;

		final MethodInvocation invoc1 = sourceAst.newMethodInvocation();
		final MethodInvocation invoc2 = sourceAst.newMethodInvocation();
		invoc2.setName(sourceAst.newSimpleName("get" + dispatchTypeName + model.getTargetMoveSuffix()));
		invoc1.setExpression(invoc2);
		invoc1.setName(sourceAst.newSimpleName(sourceMethodIdentifier));

		final ReturnStatement returnStatement = sourceAst.newReturnStatement();
		returnStatement.setExpression(invoc1);
		emptyBody.add(returnStatement);

	}

	private void dispatchSetMethod(AST sourceAst, MethodDeclaration sourceMethodDeclaration, List<Statement> emptyBody,
			String sourceTypeName, Map<String, Expression> initializators) {

		final String dispatchTypeName = model.getTargetNamesMap().containsKey(sourceTypeName) ? "Delegate"
				: sourceTypeName;

		{
			final List<SingleVariableDeclaration> parameters = sourceMethodDeclaration.parameters();
			for (SingleVariableDeclaration parameter : parameters) {

				final Type paramType = parameter.getType();
				if (paramType.isSimpleType()) {
					final SimpleType paramSimpleType = (SimpleType) paramType;
					final String identifier = paramSimpleType.getName().getFullyQualifiedName();
					final Expression initializator = initializators.get(identifier);
					if (initializator != null) {
						final IfStatement ifStatement = sourceAst.newIfStatement();
						emptyBody.add(ifStatement);
						final InfixExpression infixExpression = sourceAst.newInfixExpression();
						ifStatement.setExpression(infixExpression);
						infixExpression.setLeftOperand(sourceAst.newSimpleName(parameter.getName().getIdentifier()));
						infixExpression.setOperator(InfixExpression.Operator.EQUALS);
						infixExpression.setRightOperand(sourceAst.newNullLiteral());
						final Block thenBlock = sourceAst.newBlock();
						ifStatement.setThenStatement(thenBlock);
						final Assignment assignment = sourceAst.newAssignment();
						thenBlock.statements().add(sourceAst.newExpressionStatement(assignment));
						assignment.setLeftHandSide(sourceAst.newSimpleName(parameter.getName().getIdentifier()));
						assignment.setRightHandSide((Expression) ASTNode.copySubtree(sourceAst, initializator));
					}
				}

			}
		}
		{
			final MethodInvocation invoc1 = sourceAst.newMethodInvocation();
			final MethodInvocation invoc2 = sourceAst.newMethodInvocation();
			invoc2.setName(sourceAst.newSimpleName("get" + dispatchTypeName + model.getTargetMoveSuffix()));
			invoc1.setExpression(invoc2);

			invoc1.setName(sourceAst.newSimpleName(sourceMethodDeclaration.getName().getIdentifier()));
			final List<Expression> arguments = invoc1.arguments();
			final List<SingleVariableDeclaration> parameters = sourceMethodDeclaration.parameters();
			for (SingleVariableDeclaration parameter : parameters) {
				arguments.add(sourceAst.newSimpleName(parameter.getName().getIdentifier()));
			}
			emptyBody.add(sourceAst.newExpressionStatement(invoc1));
		}

		{
			final MethodInvocation invoc1 = sourceAst.newMethodInvocation();
			final MethodInvocation invoc2 = sourceAst.newMethodInvocation();
			invoc2.setName(sourceAst.newSimpleName("get" + dispatchTypeName + model.getTargetStubSuffix()));
			invoc1.setExpression(invoc2);

			invoc1.setName(sourceAst.newSimpleName(sourceMethodDeclaration.getName().getIdentifier()));
			final List<Expression> arguments = invoc1.arguments();
			final List<SingleVariableDeclaration> parameters = sourceMethodDeclaration.parameters();
			for (SingleVariableDeclaration parameter : parameters) {
				arguments.add(sourceAst.newSimpleName(parameter.getName().getIdentifier()));
			}
			emptyBody.add(sourceAst.newExpressionStatement(invoc1));
		}

	}

	class MoveToTargetVisitor extends ASTVisitor {
		boolean changed = false;
		String suffix;

		public MoveToTargetVisitor() {
			suffix = model.getTargetMoveSuffix();
		}

		public MoveToTargetVisitor(String suffix) {
			this.suffix = suffix;
		}

		public boolean visit(SimpleType node) {
			final Name name = node.getName();
			if (name.isSimpleName()) {
				SimpleName simpleName = (SimpleName) name;
				final String identifier = simpleName.getIdentifier();
				final Set<ICompilationUnit> units = model.getSourceCompilationUnits();
				for (ICompilationUnit unit : units) {
					final IType type = unit.findPrimaryType();
					if (type.getTypeQualifiedName().equals(identifier)) {
						node.setName(node.getAST().newName(identifier + suffix));
						changed = true;
						break;
					}
				}
			}
			return super.visit(node);
		};
	}

	private Change buildChangesSourceSplitClasses(IProgressMonitor pm) throws CoreException, MalformedTreeException,
			BadLocationException {

		final CompositeChange changesList = new CompositeChange("Split packages");

		for (final String splitPackageName : model.getSourceSplitPackages()) {

			final Map<ICompilationUnit, CompilationUnit> unitsSplitUses = new HashMap<ICompilationUnit, CompilationUnit>();
			final Map<ICompilationUnit, CompilationUnit> unitsSplitUsed = new HashMap<ICompilationUnit, CompilationUnit>();
			final Map<ICompilationUnit, Set<IMethod>> splitUsedMethods = new HashMap<ICompilationUnit, Set<IMethod>>();

			{
				final Set<ICompilationUnit> sourceCompilationUnits = model.getSourceCompilationUnits();
				for (final ICompilationUnit sourceUnit : sourceCompilationUnits) {

					final IType sourcePrimaryType = sourceUnit.findPrimaryType();
					final String sourceTypeName = sourcePrimaryType.getTypeQualifiedName();
					final String sourcePackageName = sourcePrimaryType.getPackageFragment().getElementName();
					final CompilationUnit sourceParsedUnit = model.getParsedUnits().get(sourceUnit);

					if (model.getSourceSplitPackages().contains(sourcePackageName)) {
						unitsSplitUses.put(sourceUnit, sourceParsedUnit);
					} else if (model.getSplitNamesMap().keySet().contains(sourceTypeName)) {
						unitsSplitUsed.put(sourceUnit, sourceParsedUnit);
					}
				}
			}

			if (unitsSplitUses.isEmpty() || unitsSplitUsed.isEmpty()) {
				continue;
			}

			final CompositeChange changes = new CompositeChange(splitPackageName);
			changesList.add(changes);

			final IPackageFragment sourcePackage = model.getPackageRoot().getPackageFragment(splitPackageName);

			if (!sourcePackage.exists()) {
				changes.add(new CreatePackageChange(sourcePackage));
			}

			for (final ICompilationUnit sourceUnit : unitsSplitUses.keySet()) {

				final IType sourcePrimaryType = sourceUnit.findPrimaryType();
				final String sourceTypeName = sourcePrimaryType.getTypeQualifiedName();

				final String sourceUnitText = sourceUnit.getSource();
				final Document sourceUnitDocument = new Document(sourceUnitText);

				final CompilationUnit sourceParsedUnit = model.getParsedUnits().get(sourceUnit);
				final AST sourceAst = sourceParsedUnit.getAST();

				final TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceParsedUnit,
						sourcePrimaryType.getSourceRange());

				sourceParsedUnit.recordModifications();

				final Type sourceSuperclassType = sourceTypeDeclaration.getSuperclassType();
				if (sourceSuperclassType != null) {
					if (sourceSuperclassType.isSimpleType()) {
						final SimpleType sourceSuperclassSimpleType = (SimpleType) sourceSuperclassType;
						final Name sourceSuperclassName = sourceSuperclassSimpleType.getName();
						if (sourceSuperclassName.isSimpleName()) {
							final SimpleName sourceSuperclassSimpleName = (SimpleName) sourceSuperclassName;
							final String sourceSuperclassIdentifier = sourceSuperclassSimpleName.getIdentifier();
							if (model.getSplitNamesMap().containsKey(sourceSuperclassIdentifier)) {

								{
									final SearchPattern pattern = SearchPattern.createPattern(sourcePrimaryType,
											IJavaSearchConstants.REFERENCES | IJavaSearchConstants.METHOD);
									final IJavaSearchScope scope = SearchEngine.createJavaSearchScope(
											new IJavaElement[] { model.getJavaProject() }, IJavaSearchScope.SOURCES);
									final List<IMethod> foundMethods = Split2RefactoringUtils.searchManyElements(
											IMethod.class, pattern, scope, pm);
									if (!foundMethods.isEmpty()) {
										for (IMethod foundMethod : foundMethods) {
											final SearchEngine searchEngine = Split2RefactoringUtils.getSearchEngine();
											class Requestor extends SearchRequestor {
												@Override
												public void acceptSearchMatch(SearchMatch match) throws CoreException {
													if (match.getAccuracy() == SearchMatch.A_ACCURATE
															&& !match.isInsideDocComment()) {
														final Object element = match.getElement();
														if (element != null && element instanceof IMethod) {
															IMethod method = (IMethod) element;
															final IType declaringType = method.getDeclaringType();
															if (declaringType != null) {
																final ICompilationUnit compilationUnit = declaringType
																		.getCompilationUnit();
																if (unitsSplitUsed.containsKey(compilationUnit)) {
																	if (splitUsedMethods.get(compilationUnit) == null) {
																		splitUsedMethods.put(compilationUnit,
																				new HashSet<IMethod>());
																	}
																	splitUsedMethods.get(compilationUnit).add(method);
																}
															}
														}
													}
												}
											}
											Requestor requestor = new Requestor();
											searchEngine.searchDeclarationsOfSentMessages(foundMethod, requestor, pm);
										}
									}
								}

								final String targetTypeName = model.getSplitNamesMap().get(sourceSuperclassIdentifier);
								sourceSuperclassSimpleName.setIdentifier(targetTypeName);
								class Visitor extends ASTVisitor {
									public boolean visit(SimpleType node) {
										final Name name = node.getName();
										if (name.isSimpleName()) {
											SimpleName simpleName = (SimpleName) name;
											final String identifier = simpleName.getIdentifier();
											if (model.getSplitNamesMap().containsKey(identifier)) {
												node.setName(node.getAST().newName(
														model.getSplitNamesMap().get(identifier)));
											}
										}
										return super.visit(node);
									};

									@Override
									public boolean visit(MethodInvocation node) {
										final IMethodBinding methodBinding = node.resolveMethodBinding();
										addUsedMethod(methodBinding);
										return super.visit(node);
									}

									private void addUsedMethod(final IMethodBinding methodBinding) {
										final ITypeBinding declaringClass = methodBinding.getDeclaringClass();
										final IType type = (IType) declaringClass.getJavaElement();
										final ICompilationUnit compilationUnit = type.getCompilationUnit();
										if (unitsSplitUsed.keySet().contains(compilationUnit)) {
											if (splitUsedMethods.get(compilationUnit) == null) {
												splitUsedMethods.put(compilationUnit, new HashSet<IMethod>());
											}
											splitUsedMethods.get(compilationUnit).add(
													(IMethod) methodBinding.getJavaElement());
										}
									}

									@Override
									public boolean visit(SuperConstructorInvocation node) {
										final IMethodBinding methodBinding = node.resolveConstructorBinding();
										addUsedMethod(methodBinding);
										return super.visit(node);
									}

								}
								Visitor visitor = new Visitor();
								sourceParsedUnit.accept(visitor);

								final List<ImportDeclaration> sourceImports = sourceParsedUnit.imports();
								final List<ImportDeclaration> importsToDelete = new ArrayList<ImportDeclaration>();
								for (final ImportDeclaration importDeclaration : sourceImports) {
									final Name name = importDeclaration.getName();
									final Set<ICompilationUnit> sourceCompilationUnits = model
											.getSourceCompilationUnits();
									for (ICompilationUnit unit : sourceCompilationUnits) {
										final IType type = unit.findPrimaryType();
										if (type.getFullyQualifiedName().equals(name.getFullyQualifiedName())) {
											importsToDelete.add(importDeclaration);
											break;
										}
									}
								}
								for (final ImportDeclaration importDeclaration : importsToDelete) {
									sourceImports.remove(importDeclaration);
								}
								{
									final ImportDeclaration importDeclaration = sourceAst.newImportDeclaration();
									importDeclaration.setOnDemand(true);
									importDeclaration.setName(sourceAst.newName(model.getTargetMovePackageName()));
									sourceImports.add(importDeclaration);
								}

								final TextEdit sourceUnitEdits = sourceParsedUnit.rewrite(sourceUnitDocument, model
										.getJavaProject().getOptions(true));
								final TextFileChange sourceTextFileChange = new TextFileChange(sourceUnit
										.getElementName(), (IFile) sourceUnit.getResource());
								sourceTextFileChange.setEdit(sourceUnitEdits);
								changes.add(sourceTextFileChange);

							}
						}
					}
				}

			}

			for (final ICompilationUnit sourceUnit : unitsSplitUsed.keySet()) {

				final IType sourcePrimaryType = sourceUnit.findPrimaryType();
				final String sourceTypeName = sourcePrimaryType.getTypeQualifiedName();
				final String sourcePackageName = sourcePrimaryType.getPackageFragment().getElementName();

				final String targetTypeName = model.getSplitNamesMap().get(sourceTypeName);

				final ICompilationUnit targetUnit = sourcePackage.getCompilationUnit(targetTypeName + ".java");

				final CompilationUnit sourceParsedUnit = unitsSplitUsed.get(sourceUnit);
				final TypeDeclaration sourceTypeDeclaration = (TypeDeclaration) NodeFinder.perform(sourceParsedUnit,
						sourcePrimaryType.getSourceRange());

				final AST targetAST = AST.newAST(AST.JLS3);
				final CompilationUnit targetUnitNode = targetAST.newCompilationUnit();

				final PackageDeclaration packageDeclaration = targetAST.newPackageDeclaration();
				packageDeclaration.setName(targetAST.newName(splitPackageName));
				targetUnitNode.setPackage(packageDeclaration);

				final List<ImportDeclaration> targetImports = targetUnitNode.imports();

				final List<ImportDeclaration> sourceImports = sourceParsedUnit.imports();
				for (final ImportDeclaration sourceImportDeclaration : sourceImports) {
					ImportDeclaration targetImportDeclaration = (ImportDeclaration) ASTNode.copySubtree(targetAST,
							sourceImportDeclaration);
					targetImports.add(targetImportDeclaration);
				}

				final ITypeBinding sourceTypeBinding = sourceTypeDeclaration.resolveBinding();
				final IPackageBinding sourcePackageBinding = sourceTypeBinding.getPackage();

				final ImportDeclaration targetImportDeclaration = targetAST.newImportDeclaration();
				targetImportDeclaration.setOnDemand(true);
				targetImportDeclaration.setName(targetAST.newName(sourcePackageBinding.getName()));
				targetImports.add(targetImportDeclaration);

				final TypeDeclaration targetTypeDeclaration = (TypeDeclaration) ASTNode.copySubtree(targetAST,
						sourceTypeDeclaration);
				targetUnitNode.types().add(targetTypeDeclaration);

				final Set<IMethod> usedMethods = splitUsedMethods.get(sourceUnit);

				final ITypeBinding[] sourceInterfaces = sourceTypeBinding.getInterfaces();
				final IMethodBinding[] sourceDeclaredMethods = sourceTypeBinding.getDeclaredMethods();
				for (final ITypeBinding sourceInterface : sourceInterfaces) {
					final IMethodBinding[] interfaceDeclaredMethods = sourceInterface.getDeclaredMethods();
					for (IMethodBinding interfaceDeclaredMethod : interfaceDeclaredMethods) {
						for (IMethodBinding sourceDeclaredMethod : sourceDeclaredMethods) {
							if (sourceDeclaredMethod.overrides(interfaceDeclaredMethod)) {
								final IMethod method = (IMethod) sourceDeclaredMethod.getJavaElement();
								usedMethods.add(method);
								break;
							}
						}
					}
				}

				final Set<MethodDeclaration> methodsToCopy = new HashSet<MethodDeclaration>();
				for (final IMethod usedMethod : usedMethods) {
					final MethodDeclaration methodDeclaration = (MethodDeclaration) NodeFinder.perform(
							sourceParsedUnit, usedMethod.getSourceRange());
					methodsToCopy.add(methodDeclaration);

					class Visitor extends ASTVisitor {
						@Override
						public boolean visit(MethodInvocation node) {
							final IMethodBinding methodBinding = node.resolveMethodBinding();
							final ITypeBinding declaringClass = methodBinding.getDeclaringClass();
							final IType type = (IType) declaringClass.getJavaElement();
							if (type.equals(sourcePrimaryType)) {
								final IMethod method = (IMethod) methodBinding.getJavaElement();
								try {
									MethodDeclaration methodDeclaration = (MethodDeclaration) NodeFinder.perform(
											sourceParsedUnit, method.getSourceRange());
									if (!methodsToCopy.contains(methodDeclaration)) {
										methodsToCopy.add(methodDeclaration);
										methodDeclaration.accept(new Visitor());
									}
								} catch (JavaModelException e) {
									Split2RefactoringUtils.log(e);
								}
							}
							return super.visit(node);
						}
					}

					methodDeclaration.accept(new Visitor());

				}

				final MethodDeclaration[] targetMethods = targetTypeDeclaration.getMethods();
				for (MethodDeclaration methodDeclaration : targetMethods) {
					methodDeclaration.delete();
				}

				for (MethodDeclaration methodDeclaration : methodsToCopy) {
					targetTypeDeclaration.bodyDeclarations().add(ASTNode.copySubtree(targetAST, methodDeclaration));
				}

				final TypeDeclaration[] types = targetTypeDeclaration.getTypes();
				for (TypeDeclaration typeDeclaration : types) {
					typeDeclaration.delete();
				}

				targetUnitNode.accept(new ASTVisitor() {

					@Override
					public boolean visit(SimpleName node) {
						if (sourceTypeName.equals(node.getIdentifier())) {
							node.setIdentifier(targetTypeName);
						}
						return super.visit(node);
					}
				});

				final String targetUnitText = targetUnitNode.toString();
				final Document targetUnitDocument = new Document(targetUnitText);
				final TextEdit formatEdit = model.getCodeFormatter().format(CodeFormatter.K_COMPILATION_UNIT,
						targetUnitText, 0, targetUnitText.length(), 0,
						model.getJavaProject().findRecommendedLineSeparator());
				formatEdit.apply(targetUnitDocument);

				changes.add(new CreateCompilationUnitChange(targetUnit, targetUnitDocument.get(), null));

			}
		}

		return changesList;
	}
}