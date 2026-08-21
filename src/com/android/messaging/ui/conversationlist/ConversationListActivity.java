/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2024-2025 The LineageOS Project
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

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;

import com.android.messaging.R;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.Trace;

public class ConversationListActivity extends AbstractConversationListActivity {
    private static final String SAVED_INSTANCE_STATE_SEARCH_QUERY = "search_query";
    private static final String SAVED_INSTANCE_STATE_SEARCH_EXPANDED = "search_expanded";

    @Nullable
    private MenuItem mSearchMenuItem;
    @Nullable
    private SearchView mSearchView;
    private String mSearchQuery = "";
    private boolean mShouldExpandSearch;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Trace.beginSection("ConversationListActivity.onCreate");
        setTheme(R.style.BugleTheme_ConversationListActivity);
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        mConversationListFragment = ConversationListFragment.createConversationListFragment(null);
        getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, mConversationListFragment)
                .commit();
        if (savedInstanceState != null) {
            mSearchQuery = savedInstanceState.getString(SAVED_INSTANCE_STATE_SEARCH_QUERY, "");
            mShouldExpandSearch = savedInstanceState.getBoolean(
                    SAVED_INSTANCE_STATE_SEARCH_EXPANDED, !TextUtils.isEmpty(mSearchQuery));
            mConversationListFragment.setSearchQuery(mSearchQuery);
        }
        Trace.endSection();
        invalidateActionBar();
    }

    @Override
    protected void onDestroy() {
        if (mSearchView != null) {
            mSearchView.setOnQueryTextListener(null);
        }
        if (mSearchMenuItem != null) {
            mSearchMenuItem.setOnActionExpandListener(null);
        }
        mSearchView = null;
        mSearchMenuItem = null;
        super.onDestroy();
    }

    @Override
    protected void updateActionBar(final ActionBar actionBar) {
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        supportInvalidateOptionsMenu();
    }

    @Override
    public void onBackPressed() {
        if (isInConversationListSelectMode()) {
            exitMultiSelectState();
        } else if (mConversationListFragment != null && mConversationListFragment.onBackPressed()) {
            return;
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (super.onCreateOptionsMenu(menu)) {
            return true;
        }
        getMenuInflater().inflate(R.menu.conversation_list_fragment_menu, menu);
        configureSearch(menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SAVED_INSTANCE_STATE_SEARCH_QUERY, mSearchQuery);
        outState.putBoolean(SAVED_INSTANCE_STATE_SEARCH_EXPANDED,
                mSearchMenuItem != null && mSearchMenuItem.isActionViewExpanded());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.action_start_new_conversation) {
            onActionBarStartNewConversation();
            return true;
        } else if (itemId == R.id.action_settings) {
            onActionBarSettings();
            return true;
        } else if (itemId == R.id.action_show_archived) {
            onActionBarArchived();
            return true;
        } else if (itemId == R.id.action_show_blocked_contacts) {
            onActionBarBlockedParticipants();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void configureSearch(@NonNull final Menu menu) {
        mSearchMenuItem = menu.findItem(R.id.action_search);
        if (mSearchMenuItem == null) {
            return;
        }

        final SearchView searchView = (SearchView) mSearchMenuItem.getActionView();
        mSearchView = searchView;
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setQueryHint(getString(R.string.conversation_list_search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(final String query) {
                updateSearchQuery(query);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(final String newText) {
                updateSearchQuery(newText);
                return true;
            }
        });
        mSearchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(final MenuItem item) {
                mShouldExpandSearch = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(final MenuItem item) {
                mShouldExpandSearch = false;
                searchView.setQuery("", false);
                searchView.clearFocus();
                updateSearchQuery("");
                return true;
            }
        });

        if (mShouldExpandSearch) {
            mSearchMenuItem.expandActionView();
            searchView.setQuery(mSearchQuery, false);
            searchView.clearFocus();
        }
    }

    private void updateSearchQuery(@Nullable final String searchQuery) {
        final String normalizedQuery = searchQuery == null ? "" : searchQuery.trim();
        if (TextUtils.equals(mSearchQuery, normalizedQuery)) {
            return;
        }

        mSearchQuery = normalizedQuery;
        if (mConversationListFragment != null) {
            mConversationListFragment.setSearchQuery(mSearchQuery);
        }
    }

    private boolean collapseSearchIfExpanded() {
        if (mSearchMenuItem != null && mSearchMenuItem.isActionViewExpanded()) {
            mSearchMenuItem.collapseActionView();
            return true;
        }
        return false;
    }

    @Override
    public void onActionBarHome() {
        exitMultiSelectState();
    }

    public void onActionBarStartNewConversation() {
        UIIntents.get().launchCreateNewConversationActivity(this, null);
    }

    public void onActionBarSettings() {
        UIIntents.get().launchSettingsActivity(this);
    }

    public void onActionBarBlockedParticipants() {
        UIIntents.get().launchBlockedParticipantsActivity(this);
    }

    public void onActionBarArchived() {
        UIIntents.get().launchArchivedConversationsActivity(this);
    }

    @Override
    public boolean isSwipeAnimatable() {
        return !isInConversationListSelectMode();
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // When the screen is turned on, the last used activity gets resumed, but it gets
        // window focus only after the lock screen is unlocked.
        if (hasFocus && mConversationListFragment != null) {
            mConversationListFragment.setScrolledToNewestConversationIfNeeded();
        }
    }
}
