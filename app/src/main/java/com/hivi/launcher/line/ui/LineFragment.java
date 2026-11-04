package com.hivi.launcher.line.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.databinding.FragmentLineBinding;
import com.hivi.launcher.line.presenter.LinePresenter;

public final class LineFragment extends BaseFragment<LinePresenter>
        implements LineView {
    private FragmentLineBinding mBinding;

    @Override
    protected LinePresenter createPresenter() {
        return new LinePresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_line;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_line;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding = FragmentLineBinding.bind(view);
        LinePresenter presenter = getPresenter();
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
    public void renderLineFftData(byte[] fftData) {
        if (mBinding != null) {
            mBinding.lineVisualizerPanel.setFftData(fftData);
        }
    }
}
