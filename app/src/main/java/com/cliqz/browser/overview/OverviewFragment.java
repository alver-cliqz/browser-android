package com.cliqz.browser.overview;

import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.cliqz.browser.R;
import com.cliqz.browser.app.BrowserApp;
import com.cliqz.browser.main.MainActivityComponent;
import com.cliqz.browser.main.Messages;
import com.cliqz.browser.main.TabFragment;
import com.cliqz.browser.main.TabsManager;
import com.cliqz.browser.telemetry.Telemetry;
import com.cliqz.browser.telemetry.TelemetryKeys;
import com.cliqz.browser.webview.CliqzMessages;
import com.cliqz.nove.Bus;
import com.cliqz.nove.Subscribe;
import com.readystatesoftware.systembartint.SystemBarTintManager;

import javax.inject.Inject;

public class OverviewFragment extends Fragment {

    @Inject
    Bus bus;

    @Inject
    TabsManager tabsManager;

    @Inject
    Telemetry telemetry;

    private ViewPager mViewPager;
    private OverviewTabsEnum mSelectedTab = OverviewTabsEnum.UNDEFINED;
    private OverviewPagerAdapter mPageAdapter;
    private OnPageChangeListener mOnPageChangeListener;
    public View contextualToolBar;
    private int mCurrentPageIndex = -1;

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.activity_overview, container, false);
        contextualToolBar = view.findViewById(R.id.history_contextual_menu);
        final View cancelButton = view.findViewById(R.id.action_cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bus.post(new Messages.OnContextualBarCancelPressed());
            }
        });
        final View deleteButton = view.findViewById(R.id.action_delete);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bus.post(new Messages.OnContextualBarDeletePressed());
            }
        });
        final int themeResId = R.style.Theme_Cliqz_Overview;
        final TypedArray typedArray = getActivity().getTheme()
                .obtainStyledAttributes(themeResId, new int[]{R.attr.colorPrimaryDark});
        final int resourceId = typedArray.getResourceId(0, R.color.normal_tab_primary_color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getActivity().getWindow()
                    .setStatusBarColor(ContextCompat.getColor(getContext(), resourceId));
            typedArray.recycle();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            SystemBarTintManager tintManager = new SystemBarTintManager(getActivity());
            tintManager.setStatusBarTintEnabled(true);
            tintManager.setNavigationBarTintEnabled(true);
            tintManager.setTintColor(resourceId);
        }

        mPageAdapter = new OverviewPagerAdapter(getChildFragmentManager());
        mViewPager = view.findViewById(R.id.viewpager);
        mViewPager.setOffscreenPageLimit(5);
        mViewPager.setAdapter(mPageAdapter);
        final Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(getContext().getString(R.string.overview));
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getContext().getString(R.string.overview));
        }
        setHasOptionsMenu(true);
        TabLayout tabLayout = view.findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
        return view;
    }

    private void sendCurrentPageHideSignal() {
        if (telemetry != null && mCurrentPageIndex != -1) {
            final String previousName = resolvePageName(mCurrentPageIndex);
            final long now = System.currentTimeMillis();
            final long duration =
                    now - mPageAdapter.getLastShownTime(mCurrentPageIndex);
            telemetry.sendOverviewPageVisibilitySignal(previousName, duration, false);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_overview_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // All the options close the current visible page
        sendCurrentPageHideSignal();
        final int id = item.getItemId();
        switch (id) {
            case R.id.action_new_tab:
                telemetry.sendMainMenuSignal(TelemetryKeys.NEW_TAB, false, TelemetryKeys.OVERVIEW);
                tabsManager.buildTab().show();
                return true;
            case R.id.action_new_forget_tab:
                telemetry.sendMainMenuSignal(TelemetryKeys.NEW_FORGET_TAB, false,
                        TelemetryKeys.OVERVIEW);
                tabsManager.buildTab().setForgetMode(true).show();
                return true;
            case R.id.action_settings:
                telemetry.sendMainMenuSignal(TelemetryKeys.SETTINGS, false, TelemetryKeys.OVERVIEW);
                if (bus != null) {
                    bus.post(new Messages.GoToSettings());
                }
                return true;
            case R.id.action_close_all_tabs:
                telemetry.sendMainMenuSignal(TelemetryKeys.CLOSE_ALL_TABS, false,
                        TelemetryKeys.OVERVIEW);
                tabsManager.deleteAllTabs();
                return true;
            default:
                return false;
        }
    }

    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onBackPressed(Messages.BackPressed event) {
        sendCurrentPageHideSignal();
        tabsManager.showTab(tabsManager.getCurrentTabPosition());
    }

    @Subscribe
    public void openLink(CliqzMessages.OpenLink event) {
        tabsManager.showTab(tabsManager.getCurrentTabPosition());
        final TabFragment tab = tabsManager.getCurrentTab();
        if (tab != null) {
            if (tab.isResumed()) {
                tab.openLink(event.url, false, true);
            } else {
                tab.openFromOverview(event.url);
            }
        }
    }

    @Subscribe
    public void openQuery(Messages.OpenQuery event) {
        tabsManager.showTab(tabsManager.getCurrentTabPosition());
        final TabFragment tab = tabsManager.getCurrentTab();
        if (tab != null) {
            tab.searchQuery(event.query);
        }
    }

    @Subscribe
    public void onOrientationChanged(Configuration newConfig) {
        telemetry.sendOrientationSignal(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ?
        TelemetryKeys.LANDSCAPE : TelemetryKeys.PORTRAIT, TelemetryKeys.OVERVIEW);
    }

    @Override
    public void onStart() {
        super.onStart();
        final MainActivityComponent component = BrowserApp.getActivityComponent(getActivity());
        if (component != null) {
            component.inject(this);
            bus.register(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (bus != null) {
            bus.unregister(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final OverviewTabsEnum selected = mSelectedTab != OverviewTabsEnum.UNDEFINED ?
                mSelectedTab : OverviewTabsEnum.TABS;
        final int position = selected.getFragmentIndex();
        mCurrentPageIndex = position;
        mViewPager.setCurrentItem(position);
        mPageAdapter.setShownTime(position);
        if (telemetry != null) {
            telemetry.sendOverviewPageVisibilitySignal(resolvePageName(position), 0, true);
        }
        mSelectedTab = OverviewTabsEnum.UNDEFINED;
        mOnPageChangeListener = new OnPageChangeListener();
        mViewPager.addOnPageChangeListener(mOnPageChangeListener);
    }

    @Override
    public void onPause() {
        mViewPager.removeOnPageChangeListener(mOnPageChangeListener);
        super.onPause();
    }

    public void setDisplayFavorites() {
        mSelectedTab = OverviewTabsEnum.FAVORITES;
    }

    public void setDisplayHistory() {
        mSelectedTab = OverviewTabsEnum.HISTORY;
    }

    public void setDisplayOffrz() {
        mSelectedTab = OverviewTabsEnum.OFFRZ;
    }

    private String resolvePageName(int position) {
        if (position == OverviewTabsEnum.TABS.getFragmentIndex()) {
            return TelemetryKeys.OPEN_TABS;
        } else if (position == OverviewTabsEnum.HISTORY.getFragmentIndex()) {
            return  TelemetryKeys.HISTORY;
        } else if (position == OverviewTabsEnum.OFFRZ.getFragmentIndex()) {
            return TelemetryKeys.OFFRZ;
        } else {
            return TelemetryKeys.FAVORITES;
        }
    }

    public int getCurrentPageIndex() {
        return mCurrentPageIndex;
    }

    private final class OnPageChangeListener implements ViewPager.OnPageChangeListener {

        @Override
        public void onPageScrolled(int position, float positionOffset,
        int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            if (telemetry != null) {
                // Apparently it happens that telemetry here is null
                final String nextPage = resolvePageName(position);
                telemetry.sendOverviewPageChangedSignal(nextPage);
                telemetry.sendOverviewPageVisibilitySignal(nextPage, 0L, true);
                sendCurrentPageHideSignal();
            }
            bus.post(new Messages.OnOverviewTabSwitched(position));
            mCurrentPageIndex = position;
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    }
}
