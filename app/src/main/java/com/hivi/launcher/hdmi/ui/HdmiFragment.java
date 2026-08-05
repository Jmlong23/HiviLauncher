package com.hivi.launcher.hdmi.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.databinding.FragmentHdmiBinding;
import com.hivi.launcher.hdmi.presenter.HdmiPresenter;
import com.hivi.launcher.base.BaseFragment;

public final class HdmiFragment extends BaseFragment<HdmiPresenter>
        implements HdmiView {
    private FragmentHdmiBinding mBinding;

    @Override
    protected HdmiPresenter createPresenter() {
        return new HdmiPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_hdmi;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_hdmi;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding = FragmentHdmiBinding.bind(view);
        HdmiPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.init();
        }
    }

    @Override
    public void onDestroyView() {
        mBinding = null;
        super.onDestroyView();
    }

    @Override
    public void renderHdmiFftData(byte[] fftData) {
        if (mBinding != null) {
            mBinding.hdmiVisualizerPanel.setFftData(fftData);
        }
    }
}
