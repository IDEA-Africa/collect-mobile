package org.openforis.collect.android.gui.input;

import android.content.Context;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.fragment.app.FragmentActivity;

import org.apache.commons.lang3.StringUtils;
import org.openforis.collect.R;
import org.openforis.collect.android.CodeListService;
import org.openforis.collect.android.SurveyService;
import org.openforis.collect.android.gui.ServiceLocator;
import org.openforis.collect.android.gui.detail.CodeListDescriptionDialogFragment;
import org.openforis.collect.android.viewmodel.UiAttribute;
import org.openforis.collect.android.viewmodel.UiCode;
import org.openforis.collect.android.viewmodel.UiCodeAttribute;
import org.openforis.collect.android.viewmodel.UiCodeList;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static org.apache.commons.lang3.ObjectUtils.notEqual;

/**
 * @author Daniel Wiell
 */
public abstract class CodeAttributeComponent extends AttributeComponent<UiCodeAttribute> {
    static final int RADIO_GROUP_MAX_SIZE = 100;
    static final String DESCRIPTION_BUTTON_TAG = "descriptionButton";
    private UiCode parentCode;
    final CodeListService codeListService;
    final boolean enumerator;
    protected UiCodeList codeList;
    private boolean codeListRefreshForced;

    CodeAttributeComponent(UiCodeAttribute attribute, CodeListService codeListService, SurveyService surveyService, FragmentActivity context) {
        super(attribute, surveyService, context);
        this.codeListService = codeListService;
        this.enumerator = attribute.getDefinition().isEnumerator();
    }

    public static CodeAttributeComponent create(UiCodeAttribute attribute, SurveyService surveyService, FragmentActivity context) {
        CodeListService codeListService = ServiceLocator.codeListService();
        int maxCodeListSize = codeListService.getMaxCodeListSize(attribute);
        boolean enumerator = attribute.getDefinition().isEnumerator();
        if (maxCodeListSize <= RADIO_GROUP_MAX_SIZE && ! enumerator)
            return new RadioCodeAttributeComponent(attribute, codeListService, surveyService, context);
        return new AutoCompleteCodeAttributeComponent(attribute, codeListService, surveyService, context);
    }

    protected abstract UiCode selectedCode();

    public final void onAttributeChange(UiAttribute changedAttribute) {
        if (changedAttribute != attribute && codeListService.isParentCodeAttribute(changedAttribute, attribute)) {
            UiCode newParentCode = ((UiCodeAttribute) changedAttribute).getCode();
            if (newParentCode == parentCode) return;
            if (newParentCode == null || !newParentCode.equals(parentCode)) {
                parentCode = newParentCode;
                setCodeListRefreshForced(true);
                initOptions();
            }
        }
    }

    private synchronized boolean isCodeListRefreshForced() {
        return codeListRefreshForced;
    }

    private synchronized void setCodeListRefreshForced(boolean codeListRefreshForced) {
        this.codeListRefreshForced = codeListRefreshForced;
    }

    protected final boolean updateAttributeIfChanged() {
        if (codeList == null)
            return false;
        UiCode newCode = selectedCode();
        String newQualifier = qualifier(newCode);
        if (StringUtils.isNotEmpty(newQualifier) && newCode == null)
            newCode = codeList.getQualifiableCode();
        if (hasChanged(newCode, newQualifier)) {
            attribute.setCode(newCode);
            attribute.setQualifier(newQualifier);
            return true;
        }
        return false;
    }

    private boolean containsDescription() {
        for (UiCode code : codeList.getCodes())
            if (StringUtils.isNotEmpty(code.getDescription()))
                return true;
        return false;
    }

    protected void initCodeList() {
        if (codeList == null || isCodeListRefreshForced()) {
            setCodeListRefreshForced(false);
            codeList = codeListService.codeList(attribute);
            uiHandler.post(new Runnable() {
                public void run() {
                    if (containsDescription())
                        includeDescriptionsButton();
                }
            });
        }
    }

    private void includeDescriptionsButton() {
        View inputView = toInputView();
        ViewGroup parent = (ViewGroup) inputView.getParent();
        if (parent == null)
            return;
        if (parent.findViewWithTag(DESCRIPTION_BUTTON_TAG) == null) {
            Button button = new AppCompatButton(context);
            button.setTextAppearance(context, android.R.style.TextAppearance_Small);
            button.setTag(DESCRIPTION_BUTTON_TAG);
            button.setLayoutParams(new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            button.setText(context.getResources().getString(R.string.label_show_code_descriptions));
            button.setBackgroundDrawable(null);
            button.setPaintFlags(button.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    CodeListDescriptionDialogFragment.show(context.getSupportFragmentManager());
                }
            });
            int linkColor = new TextView(context).getLinkTextColors().getDefaultColor();
            button.setTextColor(linkColor);
            parent.addView(button);
        }
    }

    private boolean hasChanged(UiCode newCode, String qualifier) {
        String oldQualifier = attribute.getQualifier() == null ? "" : attribute.getQualifier();
        String newQualifier = qualifier == null ? "" : qualifier;
        return notEqual(attribute.getCode(), newCode)
                || notEqual(oldQualifier, newQualifier);
    }

    protected abstract void initOptions();

    protected abstract String qualifier(UiCode selectedCode);

    final boolean isAttributeCode(UiCode code) {
        return code.equals(attribute.getCode());
    }

    protected static EditText createQualifierInput(Context context, String text, final Runnable onChangeHandler) {
        final EditText editText = new AppCompatEditText(context);
        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus)
                    onChangeHandler.run();
            }
        });
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT)
                    onChangeHandler.run();
                return false;
            }
        });
        editText.setText(text);
        editText.setSingleLine();
        editText.setHint(R.string.hint_code_qualifier_specify);
        return editText;
    }
}


