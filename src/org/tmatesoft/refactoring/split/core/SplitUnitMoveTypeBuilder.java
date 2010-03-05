package org.tmatesoft.refactoring.split.core;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;

class SplitUnitMoveTypeBuilder extends ASTVisitor {

	private final SplitRefactoringModel model;
	private final SplitUnitModel unitModel;

	@Override
	public boolean visit(SimpleName node) {
		moveSimpleName(node);
		return super.visit(node);
	}

	public SplitUnitMoveTypeBuilder(final SplitUnitModel splitModel) throws JavaModelException {
		this.unitModel = splitModel;
		this.model = splitModel.getModel();
	}

	/**
	 * @return the splitModel
	 */
	public SplitUnitModel getSplitModel() {
		return unitModel;
	}

	public void moveTypes() throws JavaModelException {
		for (final IField field : unitModel.getUsedFields()) {
			moveRange(field.getSourceRange());
		}
		for (final IMethod method : unitModel.getAddMethods().keySet()) {
			moveRange(method.getSourceRange());
		}
		for (final IType nestedType : unitModel.getNestedTypes()) {
			moveRange(nestedType.getSourceRange());
		}
	}

	private void moveRange(final ISourceRange sourceRange) {
		final CompilationUnit sourceAst = unitModel.getSourceAst();
		final ASTNode node = NodeFinder.perform(sourceAst, sourceRange);
		if (node != null) {
			node.accept(this);
		}
	}

	private void moveSimpleName(final SimpleName simpleName) {
		try {
			final IBinding binding = simpleName.resolveBinding();
			if (binding != null) {
				final int kind = binding.getKind();
				if (kind == IBinding.TYPE) {
					if (isTypeToMove((ITypeBinding) binding)) {
						simpleName.setIdentifier(model.addTargetSuffix(simpleName.getIdentifier()));
					}
				}
			}
		} catch (Exception e) {
			SplitRefactoring.log(e);
		}
	}

	private boolean isTypeToMove(final ITypeBinding typeBinding) throws JavaModelException {
		if (typeBinding != null && !typeBinding.isAnonymous() && typeBinding.getDeclaringClass() == null
				&& typeBinding.getDeclaringMethod() == null) {
			final IType type = (IType) typeBinding.getJavaElement();
			if (type != null) {
				final ICompilationUnit unit = type.getCompilationUnit();
				if (unit != null) {
					return model.getUnits().containsKey(unit);
				}
			}
		}
		return false;
	}

}