package org.openforis.collect.android.gui.input;

import android.content.Context;
import android.graphics.Paint;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatEditText;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import org.apache.commons.lang3.StringUtils;
import org.openforis.collect.R;
import org.openforis.collect.android.CodeListService;
import org.openforis.collect.android.SurveyService;
import org.openforis.collect.android.gui.ServiceLocator;
import org.openforis.collect.android.gui.detail.CodeListDescriptionDialogFragment;
import org.openforis.collect.android.gui.util.Views;
import org.openforis.collect.android.viewmodel.UiAttribute;
import org.openforis.collect.android.viewmodel.UiCode;
import org.openforis.collect.android.viewmodel.UiCodeAttribute;
import org.openforis.collect.android.viewmodel.UiCodeList;
import org.openforis.idm.model.Code;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
            return new NewCodeAttributeRadioComponent(attribute, codeListService, surveyService, context);
        else
            return new AutoCompleteCodeAttributeComponent(attribute, codeListService, surveyService, context);
    }

    protected UiCode selectedCode() {
        return null;
    }

    protected Code selectedCode2() {
        return null;
    }

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
        Code newCode = selectedCode2();
        UiCode newUiCode = newCode == null ? null : codeList.getCode(newCode.getCode());
        String newQualifier = newCode == null ? null : newCode.getQualifier();
        if (hasChanged(newUiCode, newQualifier)) {
            attribute.setCode(newUiCode);
            attribute.setQualifier(newCode.getQualifier());
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

    static class CodesAdapter extends BaseAdapter {

        private UiCodeList codeList;
        private boolean valueShown;
        private LayoutInflater inflater;
        private Map<String, Code> selectedCodesByValue = new HashMap<String, Code>();
        private boolean singleSelection = true;

        CodesAdapter(Context applicationContext, Set<Code> selectedCodes, UiCodeList codeList, boolean valueShown) {
            this.codeList = codeList;
            this.valueShown = valueShown;

            inflater = (LayoutInflater.from(applicationContext));

            for (Code code : selectedCodes) {
                selectedCodesByValue.put(code.getCode(), code);
            }
        }

        @Override
        public int getCount() {
            return codeList.getCodes().size();
        }

        @Override
        public Object getItem(int i) {
            return codeList.getCodes().get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(final int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            if (view == null) {
                view = inflater.inflate(R.layout.code_item, null);

                viewHolder = new ViewHolder();

                viewHolder.radioButton = view.findViewById(R.id.item_radiobutton);
                viewHolder.radioButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                        UiCode code = codeList.getCodes().get(i);
                        if (singleSelection) {
                            selectedCodesByValue.clear();
                        }
                        if (checked) {
                            selectedCodesByValue.put(code.getValue(), new Code(code.getValue()));
                        }
                        notifyDataSetChanged();
                    }
                });
                viewHolder.label = view.findViewById(R.id.item_label);
                viewHolder.qualifier = view.findViewById(R.id.item_specify_field);

                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            UiCode code = codeList.getCodes().get(i);
            fillView(viewHolder, code);

            return view;
        }

        public Collection<Code> getSelectedCodes() {
            return selectedCodesByValue.values();
        }

        private void fillView(ViewHolder viewHolder, UiCode uiCode) {
            viewHolder.label.setText(
                    valueShown
                            ? String.format("%s - %s", uiCode.getValue(), uiCode.getLabel())
                            : uiCode.getLabel()
            );

            Code code = selectedCodesByValue.get(uiCode.getValue());
            boolean selected = code != null;
            boolean showQualifier = codeList.isQualifiable(uiCode) && selected;

            viewHolder.radioButton.setSelected(selected);
            Views.toggleVisibility(viewHolder.qualifier, showQualifier);
            if (showQualifier) {
                viewHolder.qualifier.setText(code.getQualifier());
            }
        }

        private static class ViewHolder {
            RadioButton radioButton;
            TextView label;
            TextView qualifier;
        }
    }
}


