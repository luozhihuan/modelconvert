import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.CollectionListModel;

import java.util.List;

/**
 * Created by luozhihuan on 16/6/26.
 */
public class ModelConvertAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        PsiMethod psiMethod = getPsiMethodFromContext(e);
        generateO2OMethod(psiMethod);
    }


    private void generateO2OMethod(final PsiMethod psiMethod) {
        new WriteCommandAction.Simple(psiMethod.getProject(), psiMethod.getContainingFile()) {
            @Override
            protected void run() throws Throwable {
                createO2O(psiMethod);
            }
        }.execute();
    }


    private void createO2O(PsiMethod psiMethod) {
        String methodName = psiMethod.getName();
        String methodText = psiMethod.getText();
        int firstBrac = methodText.indexOf("{");
        methodText = methodText.substring(0, firstBrac + 1);
        String content = "{content}";
        methodText += "\n  "+content + "\n}\n";
        System.out.println(methodText);
        PsiType returnType = psiMethod.getReturnType();
        if (returnType == null) {
            return;
        }
        String packageReturnName = returnType.getCanonicalText();
        String returnClassName = returnType.getPresentableText();
        PsiParameter psiParameter = psiMethod.getParameterList().getParameters()[0];
        //带package的class名称
        String parameterClassWithPackage = psiParameter.getType().getInternalCanonicalText();
        //为了解析字段，这里需要加载参数的class
        JavaPsiFacade facade = JavaPsiFacade.getInstance(psiMethod.getProject());
        PsiClass paramentClass = facade.findClass(parameterClassWithPackage, GlobalSearchScope.allScope(psiMethod.getProject()));
        if (paramentClass == null) {
            return;
        }
        List<PsiField> paramentFields = new CollectionListModel<PsiField>(paramentClass.getFields()).getItems();
        String methodContent = getMethodText(methodName, returnClassName, psiParameter, paramentFields);
        methodText = methodText.replace(content, methodContent);
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiMethod.getProject());
        PsiMethod toMethod = elementFactory.createMethodFromText(methodText, psiMethod);
        psiMethod.replace(toMethod);
    }


    /**
     *
     * @param methodName  方法名称
     * @param returnClassName 返回的值的class名称
     * @param psiParameter 方法参数第一个值
     * @param paramentFields 方法参数的class里field 列表
     * @return   方法体的字符串
     */
    private String getMethodText(String methodName, String returnClassName,PsiParameter psiParameter,
                                 List<PsiField> paramentFields) {
        String returnObjName = returnClassName.substring(0, 1).toLowerCase() + returnClassName.substring(1);
        String parameterClass = psiParameter.getText();
        String parameterName = psiParameter.getName();
        StringBuilder builder = new StringBuilder("");
        builder.append("if ( " + parameterName + "== null ){\n").append("return null;\n}").append(returnClassName + " " + returnObjName + "= new " + returnClassName + "();\n");
        for (PsiField field : paramentFields) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null || modifierList.hasModifierProperty(PsiModifier.STATIC) || modifierList.hasModifierProperty(PsiModifier.FINAL) || modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                continue;
            }
            builder.append(returnObjName + ".set" + getFirstUpperCase(field.getName()) + "(" + parameterName + ".get" + getFirstUpperCase(field.getName()) + "());\n");
        }
        builder.append("return " + returnObjName + ";\n");
        return builder.toString();
    }

    private String getFirstUpperCase(String oldStr) {
        return oldStr.substring(0, 1).toUpperCase() + oldStr.substring(1);
    }


    private PsiMethod getPsiMethodFromContext(AnActionEvent e) {
        PsiElement elementAt = getPsiElement(e);
        if (elementAt == null) {
            return null;
        }
        return PsiTreeUtil.getParentOfType(elementAt, PsiMethod.class);
    }

    private PsiElement getPsiElement(AnActionEvent e) {
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (psiFile == null || editor == null) {
            e.getPresentation().setEnabled(false);
            return null;
        }
        //用来获取当前光标处的PsiElement
        int offset = editor.getCaretModel().getOffset();
        return psiFile.findElementAt(offset);
    }

}
