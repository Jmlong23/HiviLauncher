package com.hivi.launcher.optical.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.databinding.FragmentOpticalBinding;
import com.hivi.launcher.optical.presenter.OpticalPresenter;

public final class OpticalFragment extends BaseFragment<OpticalPresenter>
        implements OpticalView {
    private FragmentOpticalBinding mBinding;

    @Override
    protected OpticalPresenter createPresenter() {
        return new OpticalPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_optical;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_optical;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding = FragmentOpticalBinding.bind(view);
        OpticalPresenter presenter = getPresenter();
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
    public void renderOpticalFftData(byte[] fftData) {
        if (mBinding != null) {
            mBinding.opticalVisualizerPanel.setFftData(fftData);
        }
    }
}
