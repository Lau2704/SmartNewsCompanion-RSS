package my.mmu.Kaixuanrssnewsreader.ui.setting;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;

import my.mmu.Kaixuanrssnewsreader.R;

public class SliderPreference extends Preference {

    private int mValue;
    private int mMin = 0;
    private int mMax = 100;
    private int mStep = 1;
    private Slider mSlider;
    private TextView mValueTextView;

    public SliderPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public SliderPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public SliderPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SliderPreference(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    private void init(Context context, AttributeSet attrs) {
        setLayoutResource(R.layout.preference_slider);

        if (attrs != null) {
            String androidNs = "http://schemas.android.com/apk/res/android";
            String appNs = "http://schemas.android.com/apk/res-auto";

            mMin = attrs.getAttributeIntValue(appNs, "min", mMin);
            if (mMin == 0) mMin = attrs.getAttributeIntValue(androidNs, "min", 0);

            int maxFromAndroid = attrs.getAttributeIntValue(androidNs, "max", 0);
            if (maxFromAndroid != 0) {
                mMax = maxFromAndroid;
            } else {
                mMax = attrs.getAttributeIntValue(appNs, "max", mMax);
            }

            mStep = attrs.getAttributeIntValue(appNs, "seekBarIncrement", 1);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        mSlider = (Slider) holder.findViewById(R.id.slider);
        mValueTextView = (TextView) holder.findViewById(R.id.slider_value);

        if (mSlider != null) {
            mSlider.setValueFrom(mMin);
            mSlider.setValueTo(mMax);
            mSlider.setStepSize(mStep);

            if (mValue < mMin) {
                mValue = mMin;
                persistInt(mValue);
            } else if (mValue > mMax) {
                mValue = mMax;
                persistInt(mValue);
            }

            mSlider.setValue(mValue);
            
            mSlider.addOnChangeListener((slider, value, fromUser) -> {
                if (fromUser) {
                    int val = (int) value;
                    if (val != mValue) {
                        mValue = val;
                        updateValueText();
                        callChangeListener(val);
                        persistInt(val);
                    }
                }
            });
        }
        
        updateValueText();
    }

    private void updateValueText() {
        if (mValueTextView != null) {
            mValueTextView.setText(String.valueOf(mValue));
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        if (defaultValue == null) {
            defaultValue = 0;
        }
        setValue(getPersistedInt((Integer) defaultValue));
    }

    public void setValue(int value) {
        if (value < mMin) {
            mValue = mMin;
        } else if (value > mMax) {
            mValue = mMax;
        } else {
            mValue = value;
        }
        if (mSlider != null) {
            mSlider.setValue(mValue);
        }
        updateValueText();
    }
}
