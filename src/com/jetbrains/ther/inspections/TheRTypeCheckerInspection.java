package com.jetbrains.ther.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.ther.psi.api.*;
import com.jetbrains.ther.typing.MatchingException;
import com.jetbrains.ther.typing.TheRTypeChecker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TheRTypeCheckerInspection extends TheRLocalInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "R Type Checker";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder);
  }

  private class Visitor extends TheRVisitor {
    private final ProblemsHolder myProblemHolder;

    public Visitor(ProblemsHolder holder) {
      myProblemHolder = holder;
    }

    @Override
    public void visitCallExpression(@NotNull TheRCallExpression callExpression) {
      PsiReference referenceToFunction = callExpression.getExpression().getReference();
      if (referenceToFunction != null) {
        PsiElement assignmentStatement = referenceToFunction.resolve();
        if (assignmentStatement != null && assignmentStatement instanceof TheRAssignmentStatement) {
          TheRAssignmentStatement assignment = (TheRAssignmentStatement)assignmentStatement;
          TheRPsiElement assignedValue = assignment.getAssignedValue();
          if (assignedValue != null && assignedValue instanceof TheRFunctionExpression) {
            TheRFunctionExpression function = (TheRFunctionExpression)assignedValue;
            List<TheRExpression> arguments = callExpression.getArgumentList().getExpressionList();
            try {
              TheRTypeChecker.checkTypes(arguments, function);
            }
            catch (MatchingException e) {
              registerProblem(myProblemHolder, callExpression, e.getMessage());
            }
          }
        }
      }
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
