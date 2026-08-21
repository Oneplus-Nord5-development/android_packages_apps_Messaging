/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2024-2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.messaging.ui.conversationlist;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewPropertyAnimator;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewGroupCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.data.ConversationListData;
import com.android.messaging.datamodel.data.ConversationListData.ConversationListDataListener;
import com.android.messaging.datamodel.data.ConversationListItemData;
import com.android.messaging.ui.ListEmptyView;
import com.android.messaging.ui.SnackBarInteraction;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.AccessibilityUtil;
import com.android.messaging.util.ImeUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.UiUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Shows a list of conversations styled like Google Messages.
 */
public class ConversationListFragment extends Fragment implements ConversationListDataListener,
        ConversationListItemView.HostInterface {
    private static final String BUNDLE_ARCHIVED_MODE = "archived_mode";
    private static final String BUNDLE_FORWARD_MESSAGE_MODE = "forward_message_mode";

    private boolean mArchiveMode;
    private boolean mBlockedAvailable;
    private boolean mForwardMessageMode;
    private boolean mIsSearchOpen;

    public interface ConversationListFragmentHost {
        void onConversationClick(final ConversationListData listData,
                                        final ConversationListItemData conversationListItemData,
                                        final boolean isLongClick,
                                        final ConversationListItemView conversationView);
        void onCreateConversationClick();
        boolean isConversationSelected(final String conversationId);
        boolean isSwipeAnimatable();
        boolean isSelectionMode();
        boolean hasWindowFocus();
    }

    private ConversationListFragmentHost mHost;
    private RecyclerView mRecyclerView;
    private FloatingActionButton mStartNewConversationButton;
    private ListEmptyView mEmptyListMessageView;
    private ConversationListAdapter mAdapter;

    // Google Messages Top Header Views
    private FrameLayout mHeaderContainer;
    private LinearLayout mNormalHeader;
    private TextView mAppTitleText;
    private ImageView mSearchIconButton;
    private ImageView mOptionsIconButton;

    private LinearLayout mSearchHeader;
    private ImageView mSearchBackButton;
    private EditText mSearchInputView;
    private ImageView mSearchClearButton;

    private LinearLayout mSelectionHeader;
    private ImageView mSelectionCloseButton;
    private TextView mSelectionCountText;
    private ImageView mSelectionArchiveButton;
    private ImageView mSelectionDeleteButton;
    private ImageView mSelectionBlockButton;

    // Saved Instance State Data
    private static final String SAVED_INSTANCE_STATE_LIST_VIEW_STATE_KEY =
            "conversationListViewState";
    private static final String SAVED_INSTANCE_STATE_SEARCH_QUERY_KEY =
            "conversationListSearchQuery";
    private Parcelable mListState;
    private String mSearchQuery = "";

    final Binding<ConversationListData> mListBinding = BindingBase.createBinding(this);

    public static ConversationListFragment createArchivedConversationListFragment() {
        return createConversationListFragment(BUNDLE_ARCHIVED_MODE);
    }

    public static ConversationListFragment createForwardMessageConversationListFragment() {
        return createConversationListFragment(BUNDLE_FORWARD_MESSAGE_MODE);
    }

    public static ConversationListFragment createConversationListFragment(String modeKeyName) {
        final ConversationListFragment fragment = new ConversationListFragment();
        if (modeKeyName != null) {
            final Bundle bundle = new Bundle();
            bundle.putBoolean(modeKeyName, true);
            fragment.setArguments(bundle);
        }
        return fragment;
    }

    @Override
    public void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        mListBinding.getData().init(LoaderManager.getInstance(this), mListBinding);
        mAdapter = new ConversationListAdapter(getActivity(), null, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mHost = (ConversationListFragmentHost) getActivity();
        setScrolledToNewestConversationIfNeeded();
        updateUi();
    }

    public void setScrolledToNewestConversationIfNeeded() {
        if (mRecyclerView == null || mHost == null) {
            return;
        }
        if (!mArchiveMode
                && !mForwardMessageMode
                && isScrolledToFirstConversation()
                && mHost.hasWindowFocus()) {
            mListBinding.getData().setScrolledToNewestConversation(true);
        }
    }

    private boolean isScrolledToFirstConversation() {
        if (mRecyclerView == null || mRecyclerView.getLayoutManager() == null) {
            return false;
        }
        int firstItemPosition = ((LinearLayoutManager) mRecyclerView.getLayoutManager())
                .findFirstCompletelyVisibleItemPosition();
        return firstItemPosition == 0;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mListBinding.unbind();
        mHost = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mRecyclerView != null) {
            mRecyclerView.setAdapter(null);
            mRecyclerView = null;
        }
        mEmptyListMessageView = null;
        mStartNewConversationButton = null;
        mHeaderContainer = null;
        mNormalHeader = null;
        mSearchHeader = null;
        mSelectionHeader = null;
        mSearchInputView = null;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.conversation_list_fragment,
                container, false);

        mHeaderContainer = rootView.findViewById(R.id.header_container);
        mNormalHeader = rootView.findViewById(R.id.normal_header);
        mAppTitleText = rootView.findViewById(R.id.app_title_text);
        mSearchIconButton = rootView.findViewById(R.id.search_icon_button);
        mOptionsIconButton = rootView.findViewById(R.id.options_icon_button);

        mSearchHeader = rootView.findViewById(R.id.search_header);
        mSearchBackButton = rootView.findViewById(R.id.search_back_button);
        mSearchInputView = rootView.findViewById(R.id.search_input_view);
        mSearchClearButton = rootView.findViewById(R.id.search_clear_button);

        mSelectionHeader = rootView.findViewById(R.id.selection_header);
        mSelectionCloseButton = rootView.findViewById(R.id.selection_close_button);
        mSelectionCountText = rootView.findViewById(R.id.selection_count_text);
        mSelectionArchiveButton = rootView.findViewById(R.id.selection_archive_button);
        mSelectionDeleteButton = rootView.findViewById(R.id.selection_delete_button);
        mSelectionBlockButton = rootView.findViewById(R.id.selection_block_button);

        mRecyclerView = rootView.findViewById(android.R.id.list);
        mEmptyListMessageView = rootView.findViewById(R.id.no_conversations_view);
        mEmptyListMessageView.setImageHint(R.drawable.ic_oobe_conv_list);

        // Apply Android 16 Edge-to-Edge Window Insets
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

            if (mHeaderContainer != null) {
                mHeaderContainer.setPadding(0, statusBarInsets.top, 0, 0);
            }

            if (mRecyclerView != null) {
                int bottomPadding = navBarInsets.bottom + (int) (88 * getResources().getDisplayMetrics().density);
                mRecyclerView.setPadding(0, 0, 0, bottomPadding);
            }

            if (mStartNewConversationButton != null) {
                MarginLayoutParams lp = (MarginLayoutParams) mStartNewConversationButton.getLayoutParams();
                lp.bottomMargin = navBarInsets.bottom + (int) (16 * getResources().getDisplayMetrics().density);
                mStartNewConversationButton.setLayoutParams(lp);
            }

            return WindowInsetsCompat.CONSUMED;
        });

        // Set title for archived mode
        if (mArchiveMode) {
            mAppTitleText.setText(R.string.archived_activity_title);
            if (mSelectionArchiveButton != null) {
                mSelectionArchiveButton.setImageResource(R.drawable.ic_archive_undo_small_dark);
                mSelectionArchiveButton.setContentDescription(getString(R.string.action_unarchive));
            }
        }

        setupHeaders();

        final Activity activity = getActivity();
        final LinearLayoutManager manager = new LinearLayoutManager(activity) {
            @Override
            public RecyclerView.LayoutParams generateDefaultLayoutParams() {
                return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        };
        mRecyclerView.setLayoutManager(manager);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);

        // Scroll listener for Ime hide and FAB shrink/extend
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            int mCurrentState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE;

            @Override
            public void onScrolled(@NonNull final RecyclerView recyclerView, final int dx,
                                   final int dy) {
                if (mCurrentState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL
                        || mCurrentState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    ImeUtil.get().hideImeKeyboard(getActivity(), mRecyclerView);
                }

                if (isScrolledToFirstConversation()) {
                    setScrolledToNewestConversationIfNeeded();
                } else {
                    mListBinding.getData().setScrolledToNewestConversation(false);
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull final RecyclerView recyclerView,
                                             final int newState) {
                mCurrentState = newState;
            }
        });
        mRecyclerView.addOnItemTouchListener(new ConversationListSwipeHelper(mRecyclerView));

        if (savedInstanceState != null) {
            mListState = savedInstanceState.getParcelable(SAVED_INSTANCE_STATE_LIST_VIEW_STATE_KEY,
                    Parcelable.class);
            mSearchQuery = savedInstanceState.getString(SAVED_INSTANCE_STATE_SEARCH_QUERY_KEY, "");
        }

        mStartNewConversationButton = rootView.findViewById(R.id.start_new_conversation_button);
        if (mArchiveMode || mForwardMessageMode) {
            mStartNewConversationButton.setVisibility(View.GONE);
        } else {
            mStartNewConversationButton.setVisibility(View.VISIBLE);
            mStartNewConversationButton.setOnClickListener(clickView ->
                    mHost.onCreateConversationClick());
        }

        ViewGroupCompat.setTransitionGroup(rootView, false);
        return rootView;
    }

    private void setupHeaders() {
        // Search Button click -> show search header
        mSearchIconButton.setOnClickListener(v -> openSearch());

        // Options Button click -> show PopupMenu (Archived, Blocked, Settings)
        mOptionsIconButton.setOnClickListener(v -> showOptionsMenu(v));

        // Search Back button click -> close search
        mSearchBackButton.setOnClickListener(v -> closeSearch());

        // Search input text change listener
        mSearchInputView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String query = s == null ? "" : s.toString();
                mSearchClearButton.setVisibility(TextUtils.isEmpty(query) ? View.GONE : View.VISIBLE);
                setSearchQuery(query);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Search Clear button click
        mSearchClearButton.setOnClickListener(v -> {
            mSearchInputView.setText("");
            setSearchQuery("");
        });

        // Selection Header Actions
        mSelectionCloseButton.setOnClickListener(v -> {
            if (getActivity() instanceof AbstractConversationListActivity) {
                ((AbstractConversationListActivity) getActivity()).onActionBarHome();
            }
        });

        mSelectionArchiveButton.setOnClickListener(v -> {
            if (getActivity() instanceof AbstractConversationListActivity) {
                final AbstractConversationListActivity act = (AbstractConversationListActivity) getActivity();
                if (act.getActionModeCallback() instanceof MultiSelectActionModeCallback) {
                    final MultiSelectActionModeCallback cb = (MultiSelectActionModeCallback) act.getActionModeCallback();
                    act.onActionBarArchive(cb.getSelectedConversations(), !mArchiveMode);
                }
            }
        });

        mSelectionDeleteButton.setOnClickListener(v -> {
            if (getActivity() instanceof AbstractConversationListActivity) {
                final AbstractConversationListActivity act = (AbstractConversationListActivity) getActivity();
                if (act.getActionModeCallback() instanceof MultiSelectActionModeCallback) {
                    final MultiSelectActionModeCallback cb = (MultiSelectActionModeCallback) act.getActionModeCallback();
                    act.onActionBarDelete(cb.getSelectedConversations());
                }
            }
        });

        mSelectionBlockButton.setOnClickListener(v -> {
            if (getActivity() instanceof AbstractConversationListActivity) {
                final AbstractConversationListActivity act = (AbstractConversationListActivity) getActivity();
                if (act.getActionModeCallback() instanceof MultiSelectActionModeCallback) {
                    final MultiSelectActionModeCallback cb = (MultiSelectActionModeCallback) act.getActionModeCallback();
                    final Collection<MultiSelectActionModeCallback.SelectedConversation> selected = cb.getSelectedConversations();
                    if (!selected.isEmpty()) {
                        act.onActionBarBlock(selected.iterator().next());
                    }
                }
            }
        });
    }

    private void openSearch() {
        mIsSearchOpen = true;
        mNormalHeader.setVisibility(View.GONE);
        mSelectionHeader.setVisibility(View.GONE);
        mSearchHeader.setVisibility(View.VISIBLE);
        mSearchInputView.requestFocus();
        ImeUtil.get().showImeKeyboard(getActivity(), mSearchInputView);
    }

    private void closeSearch() {
        mIsSearchOpen = false;
        mSearchInputView.setText("");
        setSearchQuery("");
        ImeUtil.get().hideImeKeyboard(getActivity(), mSearchInputView);
        mSearchHeader.setVisibility(View.GONE);
        mNormalHeader.setVisibility(View.VISIBLE);
    }

    public boolean onBackPressed() {
        if (mIsSearchOpen) {
            closeSearch();
            return true;
        }
        return false;
    }

    private void showOptionsMenu(View anchor) {
        final PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.conversation_list_fragment_menu, popup.getMenu());

        final MenuItem blockedItem = popup.getMenu().findItem(R.id.action_show_blocked_contacts);
        if (blockedItem != null) {
            blockedItem.setVisible(mBlockedAvailable);
        }

        final MenuItem archiveItem = popup.getMenu().findItem(R.id.action_show_archived);
        if (archiveItem != null) {
            archiveItem.setVisible(!mArchiveMode);
        }

        popup.setOnMenuItemClickListener(item -> {
            final int id = item.getItemId();
            final Activity activity = getActivity();
            if (activity instanceof ConversationListActivity) {
                final ConversationListActivity mainAct = (ConversationListActivity) activity;
                if (id == R.id.action_settings) {
                    mainAct.onActionBarSettings();
                    return true;
                } else if (id == R.id.action_show_archived) {
                    mainAct.onActionBarArchived();
                    return true;
                } else if (id == R.id.action_show_blocked_contacts) {
                    mainAct.onActionBarBlockedParticipants();
                    return true;
                }
            }
            return false;
        });
        popup.show();
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        LogUtil.v(LogUtil.BUGLE_TAG, "Attaching List");
        final Bundle arguments = getArguments();
        if (arguments != null) {
            mArchiveMode = arguments.getBoolean(BUNDLE_ARCHIVED_MODE, false);
            mForwardMessageMode = arguments.getBoolean(BUNDLE_FORWARD_MESSAGE_MODE, false);
        }
        mListBinding.bind(DataModel.get().createConversationListData(context, this, mArchiveMode));
        mListBinding.getData().setSearchQuery(mSearchQuery);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mListState != null) {
            outState.putParcelable(SAVED_INSTANCE_STATE_LIST_VIEW_STATE_KEY, mListState);
        }
        outState.putString(SAVED_INSTANCE_STATE_SEARCH_QUERY_KEY, mSearchQuery);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mRecyclerView != null && mRecyclerView.getLayoutManager() != null) {
            mListState = mRecyclerView.getLayoutManager().onSaveInstanceState();
        }
        mListBinding.getData().setScrolledToNewestConversation(false);
    }

    @Override
    public void onConversationListCursorUpdated(final ConversationListData data,
            final Cursor cursor) {
        mListBinding.ensureBound(data);
        final Cursor oldCursor = mAdapter.swapCursor(cursor);
        if (mEmptyListMessageView != null) {
            updateEmptyListUi(cursor == null || cursor.getCount() == 0);
        }
        if (mRecyclerView != null && mListState != null && cursor != null && oldCursor == null
                && mRecyclerView.getLayoutManager() != null) {
            mRecyclerView.getLayoutManager().onRestoreInstanceState(mListState);
        }
    }

    @Override
    public void setBlockedParticipantsAvailable(final boolean blockedAvailable) {
        mBlockedAvailable = blockedAvailable;
    }

    public void updateUi() {
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        updateHeaderMode();
    }

    private void updateHeaderMode() {
        if (mHost != null && mHost.isSelectionMode()) {
            if (mNormalHeader != null) mNormalHeader.setVisibility(View.GONE);
            if (mSearchHeader != null) mSearchHeader.setVisibility(View.GONE);
            if (mSelectionHeader != null) {
                mSelectionHeader.setVisibility(View.VISIBLE);
                if (getActivity() instanceof AbstractConversationListActivity) {
                    final AbstractConversationListActivity act = (AbstractConversationListActivity) getActivity();
                    if (act.getActionModeCallback() instanceof MultiSelectActionModeCallback) {
                        final MultiSelectActionModeCallback cb = (MultiSelectActionModeCallback) act.getActionModeCallback();
                        int count = cb.getSelectedCount();
                        mSelectionCountText.setText(String.valueOf(count));
                        if (mSelectionBlockButton != null) {
                            mSelectionBlockButton.setVisibility(count == 1 ? View.VISIBLE : View.GONE);
                        }
                    }
                }
            }
            if (mStartNewConversationButton != null) {
                mStartNewConversationButton.setVisibility(View.GONE);
            }
        } else if (mIsSearchOpen) {
            if (mNormalHeader != null) mNormalHeader.setVisibility(View.GONE);
            if (mSelectionHeader != null) mSelectionHeader.setVisibility(View.GONE);
            if (mSearchHeader != null) mSearchHeader.setVisibility(View.VISIBLE);
            if (mStartNewConversationButton != null && !mArchiveMode && !mForwardMessageMode) {
                mStartNewConversationButton.setVisibility(View.VISIBLE);
            }
        } else {
            if (mSelectionHeader != null) mSelectionHeader.setVisibility(View.GONE);
            if (mSearchHeader != null) mSearchHeader.setVisibility(View.GONE);
            if (mNormalHeader != null) mNormalHeader.setVisibility(View.VISIBLE);
            if (mStartNewConversationButton != null && !mArchiveMode && !mForwardMessageMode) {
                mStartNewConversationButton.setVisibility(View.VISIBLE);
            }
        }
    }

    public void setSearchQuery(final String searchQuery) {
        final String normalizedQuery = searchQuery == null ? "" : searchQuery.trim();
        if (TextUtils.equals(mSearchQuery, normalizedQuery)) {
            return;
        }

        mSearchQuery = normalizedQuery;
        if (mListBinding.isBound()) {
            mListBinding.getData().setSearchQuery(mSearchQuery);
        }

        if (mAdapter != null && mEmptyListMessageView != null) {
            final Cursor cursor = mAdapter.getCursor();
            updateEmptyListUi(cursor == null || cursor.getCount() == 0);
        }
    }

    @Override
    public void onConversationClicked(final ConversationListItemData conversationListItemData,
            final boolean isLongClick, final ConversationListItemView conversationView) {
        final ConversationListData listData = mListBinding.getData();
        mHost.onConversationClick(listData, conversationListItemData, isLongClick,
                conversationView);
    }

    @Override
    public boolean isConversationSelected(final String conversationId) {
        return mHost != null && mHost.isConversationSelected(conversationId);
    }

    @Override
    public boolean isSwipeAnimatable() {
        return mHost != null && mHost.isSwipeAnimatable();
    }

    private void updateEmptyListUi(final boolean isEmpty) {
        if (mEmptyListMessageView == null) {
            return;
        }
        if (isEmpty) {
            int emptyListText;
            if (!mListBinding.getData().getHasFirstSyncCompleted()) {
                emptyListText = R.string.conversation_list_first_sync_text;
            } else if (!TextUtils.isEmpty(mSearchQuery)) {
                emptyListText = R.string.conversation_list_search_empty_text;
            } else if (mArchiveMode) {
                emptyListText = R.string.archived_conversation_list_empty_text;
            } else {
                emptyListText = R.string.conversation_list_empty_text;
            }
            mEmptyListMessageView.setTextHint(emptyListText);
            mEmptyListMessageView.setVisibility(View.VISIBLE);
            mEmptyListMessageView.setIsImageVisible(true);
            mEmptyListMessageView.setIsVerticallyCentered(true);
        } else {
            mEmptyListMessageView.setVisibility(View.GONE);
        }
    }

    @Override
    public List<SnackBarInteraction> getSnackBarInteractions() {
        final List<SnackBarInteraction> interactions = new ArrayList<>(1);
        if (mStartNewConversationButton != null) {
            final SnackBarInteraction fabInteraction =
                    new SnackBarInteraction.BasicSnackBarInteraction(mStartNewConversationButton);
            interactions.add(fabInteraction);
        }
        return interactions;
    }

    private ViewPropertyAnimator getNormalizedFabAnimator() {
        return mStartNewConversationButton.animate()
                .setInterpolator(UiUtils.DEFAULT_INTERPOLATOR)
                .setDuration(getActivity().getResources().getInteger(
                        R.integer.fab_animation_duration_ms));
    }

    public void dismissFab() {
        if (mStartNewConversationButton == null) return;
        mStartNewConversationButton.setEnabled(false);
        final MarginLayoutParams lp =
                (MarginLayoutParams) mStartNewConversationButton.getLayoutParams();
        final float fabWidthWithLeftRightMargin = mStartNewConversationButton.getWidth()
                + lp.leftMargin + lp.rightMargin;
        final int direction = AccessibilityUtil.isLayoutRtl(mStartNewConversationButton) ? -1 : 1;
        getNormalizedFabAnimator().translationX(direction * fabWidthWithLeftRightMargin);
    }

    public void showFab() {
        if (mStartNewConversationButton == null) return;
        getNormalizedFabAnimator().translationX(0).withEndAction(() -> {
            if (mStartNewConversationButton != null) {
                mStartNewConversationButton.setEnabled(true);
            }
        });
    }

    @Override
    public void startFullScreenPhotoViewer(
            final Uri initialPhoto, final Rect initialPhotoBounds, final Uri photosUri) {
        UIIntents.get().launchFullScreenPhotoViewer(
                getActivity(), initialPhoto, initialPhotoBounds, photosUri);
    }

    @Override
    public void startFullScreenVideoViewer(final Uri videoUri) {
        UIIntents.get().launchFullScreenVideoViewer(getActivity(), videoUri);
    }

    @Override
    public boolean isSelectionMode() {
        return mHost != null && mHost.isSelectionMode();
    }
}
