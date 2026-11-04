package com.hivi.launcher.coax.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.coax.presenter.CoaxPresenter;
import com.hivi.launcher.databinding.FragmentCoaxBinding;

public final class CoaxFragment extends BaseFragment<CoaxPresenter>
        implements CoaxView {
    private FragmentCoaxBinding mBinding;

    @Override
    protected CoaxPresenter createPresenter() {
        return new CoaxPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_coax;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_coax;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding = FragmentCoaxBinding.bind(view);
        CoaxPresenter presenter = getPresenter();
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
    public void renderCoaxFftData(byte[] fftData) {
        if (mBinding != null) {
            mBinding.coaxVisualizerPanel.setFftData(fftData);
        }
    }
}
