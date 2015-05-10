package com.jetbrains.ther.psi.references;

import com.intellij.openapi.module.impl.scopes.LibraryScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScopeImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.ther.TheRPsiUtils;
import com.jetbrains.ther.interpreter.TheRInterpreterConfigurable;
import com.jetbrains.ther.psi.api.*;
import com.jetbrains.ther.psi.stubs.TheRAssignmentNameIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class TheRResolver {

  public static void addFromSkeletonsAndRLibrary(@NotNull final PsiElement element,
                                                 @NotNull final List<ResolveResult> result,
                                                 @NotNull final String... names) {
    for (String name : names) {
      TheRResolver.addFromProject(name, element.getProject(), result);
      TheRResolver.addFromLibrary(element, result, name, TheRInterpreterConfigurable.The_R_USER_SKELETONS);
      TheRResolver.addFromLibrary(element, result, name, TheRInterpreterConfigurable.THE_R_SKELETONS);
      TheRResolver.addFromLibrary(element, result, name, TheRInterpreterConfigurable.THE_R_LIBRARY);
    }
  }

  public static void addFromLibrary(@NotNull final PsiElement element,
                                    @NotNull final List<ResolveResult> result,
                                    @NotNull final String name,
                                    @NotNull final String libraryName) {
    Project project = element.getProject();
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    final Library library = libraryTable.getLibraryByName(libraryName);
    if (library != null) {
      final Collection<TheRAssignmentStatement> assignmentStatements = TheRAssignmentNameIndex.find(name, project,
                                                                                                    new LibraryScope(
                                                                                                      project, library));
      for (TheRAssignmentStatement statement : assignmentStatements) {
        final PsiFile containingFile = statement.getContainingFile();
        final PsiElement assignee = statement.getAssignee();
        if (assignee == null || containingFile.getName() == null) continue;
        if (FileUtil.getNameWithoutExtension(containingFile.getName()).equalsIgnoreCase(name) &&
            TheRInterpreterConfigurable.THE_R_LIBRARY.equals(libraryName)) {
          result.add(0, new PsiElementResolveResult(statement));
        }
        else {
          result.add(new PsiElementResolveResult(statement));
        }
      }
    }
  }

  public static void addFromProject(String name,
                                    @NotNull final Project project,
                                    @NotNull final List<ResolveResult> results) {
    Collection<TheRAssignmentStatement> statements =
      TheRAssignmentNameIndex.find(name, project,
                                   new ProjectScopeImpl(project, FileIndexFacade
                                     .getInstance(project)));
    for (TheRAssignmentStatement statement : statements) {
      final PsiElement assignee = statement.getAssignee();
      if (assignee == null) continue;
      results.add(new PsiElementResolveResult(statement));
    }
  }

  //TODO: should we search in other libraries too?
  public static void resolveWithNamespace(@NotNull final Project project,
                                          String name,
                                          String namespace,
                                          @NotNull final List<ResolveResult> result) {
    final ModifiableModelsProvider modelsProvider = ModifiableModelsProvider.SERVICE.getInstance();
    final LibraryTable.ModifiableModel model = modelsProvider.getLibraryTableModifiableModel(project);
    final Library library = model.getLibraryByName(TheRInterpreterConfigurable.THE_R_LIBRARY);
    if (library != null) {
      final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
      for (VirtualFile child : files) {
        if (namespace.equals(child.getParent().getName())) {
          final VirtualFile file = child.findChild(name + ".R");
          if (file != null) {
            final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            final TheRAssignmentStatement[] statements = PsiTreeUtil.getChildrenOfType(psiFile, TheRAssignmentStatement.class);
            if (statements != null) {
              for (TheRAssignmentStatement statement : statements) {
                final PsiElement assignee = statement.getAssignee();
                if (assignee != null && assignee.getText().equals(name)) {
                  result.add(new PsiElementResolveResult(assignee));
                }
              }
            }
          }
        }
      }
    }
  }

  public static void resolveWithoutNamespaceInFile(@NotNull final PsiElement element,
                                                   @NotNull final List<ResolveResult> result,
                                                   String... names) {
    for (String name : names) {
      resolveWithoutNamespaceInFile(element, name, result);
    }

  }

    public static void resolveWithoutNamespaceInFile(@NotNull final PsiElement element,
                                                   String name,
                                                   @NotNull final List<ResolveResult> result) {
    TheRBlockExpression rBlock = PsiTreeUtil.getParentOfType(element, TheRBlockExpression.class);
    while (rBlock != null) {
      final TheRAssignmentStatement[] statements = PsiTreeUtil.getChildrenOfType(rBlock, TheRAssignmentStatement.class);
      if (statements != null) {
        for (TheRAssignmentStatement statement : statements) {
          final PsiElement assignee = statement.getAssignee();
          if (assignee != null && assignee.getText().equals(name)) {
            result.add(new PsiElementResolveResult(statement));
          }
        }
      }
      rBlock = PsiTreeUtil.getParentOfType(rBlock, TheRBlockExpression.class);
    }
    TheRForStatement rLoop = PsiTreeUtil.getParentOfType(element, TheRForStatement.class);
    while (rLoop != null) {
      final TheRExpression target = rLoop.getTarget();
      if (name.equals(target.getName())) {
        result.add(new PsiElementResolveResult(target));
      }
      rLoop = PsiTreeUtil.getParentOfType(rLoop, TheRForStatement.class);
    }
    final TheRFunctionExpression rFunction = PsiTreeUtil.getParentOfType(element, TheRFunctionExpression.class);
    if (rFunction != null) {
      final TheRParameterList list = rFunction.getParameterList();
      for (TheRParameter parameter : list.getParameterList()) {
        if (name.equals(parameter.getName())) {
          result.add(new PsiElementResolveResult(parameter));
        }
      }
    }
    final PsiFile file = element.getContainingFile();
    final TheRAssignmentStatement[] statements = PsiTreeUtil.getChildrenOfType(file, TheRAssignmentStatement.class);
    if (statements != null) {
      for (TheRAssignmentStatement statement : statements) {
        final PsiElement assignee = statement.getAssignee();
        if (assignee != null && assignee.getText().equals(name)) {
          result.add(new PsiElementResolveResult(statement));
        }
      }
    }
  }

  public static void resolveNameArgument(@NotNull final PsiElement element,
                                         String elementName,
                                         @NotNull final List<ResolveResult> result) {
    TheRCallExpression callExpression = PsiTreeUtil.getParentOfType(element, TheRCallExpression.class);
    if (callExpression != null) {
      TheRFunctionExpression functionExpression = TheRPsiUtils.getFunction(callExpression);
      TheRParameterList parameterList = PsiTreeUtil.getChildOfType(functionExpression, TheRParameterList.class);
      if (parameterList != null) {
        for (TheRParameter parameter : parameterList.getParameterList()) {
          String name = parameter.getName();
          if (name != null && name.equals(elementName)) {
            result.add(0, new PsiElementResolveResult(parameter));
            return;
          }
        }
      }
    }
  }
}
