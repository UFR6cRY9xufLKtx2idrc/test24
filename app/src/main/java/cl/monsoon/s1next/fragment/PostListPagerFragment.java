package cl.monsoon.s1next.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import cl.monsoon.s1next.Api;
import cl.monsoon.s1next.R;
import cl.monsoon.s1next.adapter.PostListRecyclerAdapter;
import cl.monsoon.s1next.model.list.PostList;
import cl.monsoon.s1next.model.mapper.PostListWrapper;
import cl.monsoon.s1next.util.ToastHelper;
import cl.monsoon.s1next.widget.AsyncResult;
import cl.monsoon.s1next.widget.MyRecyclerView;

/**
 * A Fragment representing one of the pages of posts.
 * All activities containing this Fragment must
 * implement {@link cl.monsoon.s1next.fragment.PostListPagerFragment.OnPagerInteractionCallback}.
 * Similar to {@see ThreadListPagerFragment}
 */
public final class PostListPagerFragment extends BaseFragment<PostListWrapper> {

    private static final String ARG_THREAD_ID = "thread_id";
    private static final String ARG_PAGE_NUM = "page_num";

    /**
     * The serialization (saved instance state) Bundle key representing whether
     * {@link cl.monsoon.s1next.widget.MyRecyclerView} is endless loading when configuration changed.
     */
    private static final String STATE_IS_ENDLESS_LOADING = "is_endless_loading";

    private CharSequence mThreadId;
    private int mPageNum;

    private PostListRecyclerAdapter mRecyclerAdapter;

    private boolean mIsEndlessLoading;

    private OnPagerInteractionCallback mOnPagerInteractionCallback;

    public static PostListPagerFragment newInstance(CharSequence threadId, int page) {
        PostListPagerFragment fragment = new PostListPagerFragment();

        Bundle args = new Bundle();
        args.putCharSequence(ARG_THREAD_ID, threadId);
        args.putInt(ARG_PAGE_NUM, page);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mThreadId = getArguments().getCharSequence(ARG_THREAD_ID);
        mPageNum = getArguments().getInt(ARG_PAGE_NUM);

        if (savedInstanceState != null) {
            mIsEndlessLoading = savedInstanceState.getBoolean(STATE_IS_ENDLESS_LOADING);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_base, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MyRecyclerView recyclerView = (MyRecyclerView) view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerAdapter = new PostListRecyclerAdapter(getActivity());
        recyclerView.setAdapter(mRecyclerAdapter);

        setupRecyclerViewPadding(
                recyclerView,
                getResources().getDimensionPixelSize(R.dimen.recycler_view_card_padding),
                true);
        enableToolbarAndFabAutoHideEffect(recyclerView, new RecyclerView.OnScrollListener() {

            private final LinearLayoutManager mLinearLayoutManager =
                    (LinearLayoutManager) recyclerView.getLayoutManager();

            /**
             * Endless Scrolling with RecyclerView.
             */
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy < 0) {
                    return;
                }

                int lastCompletelyVisibleItem = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
                int itemCount = mLinearLayoutManager.getItemCount();

                if (!mIsEndlessLoading
                        && lastCompletelyVisibleItem == itemCount - 1
                        && mPageNum == mOnPagerInteractionCallback.getTotalPages()
                        && !isLoading()) {

                    mIsEndlessLoading = true;
                    setSwipeRefreshLayoutEnabled(false);
                    mRecyclerAdapter.showFooterProgress();
                    onRefresh();
                }
            }
        });
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (activity instanceof OnPagerInteractionCallback) {
            mOnPagerInteractionCallback = ((OnPagerInteractionCallback) getActivity());
        } else {
            throw new ClassCastException(
                    getActivity()
                            + " must implement OnPagerInteractionListener.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        mOnPagerInteractionCallback = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.fragment_post, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_browser:
                String url = Api.getUrlBrowserPostList(mThreadId, mPageNum);

                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));

                startActivity(intent);

                return true;
            case R.id.menu_share:
                String value =
                        getThreadTitle() + "  " + Api.getUrlBrowserPostList(mThreadId, 1);

                intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, value);
                intent.setType("text/plain");

                startActivity(intent);

                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(STATE_IS_ENDLESS_LOADING, mIsEndlessLoading);
    }

    private CharSequence getThreadTitle() {
        CharSequence title = getActivity().getTitle();
        // remove two space and page number's length

        return title.subSequence(0, title.length() - 2 - String.valueOf(mPageNum).length());
    }

    @Override
    public void onRefresh() {
        execute(Api.getUrlPostList(mThreadId, mPageNum), PostListWrapper.class);
    }

    @Override
    public void onPostExecute(AsyncResult<PostListWrapper> asyncResult) {
        super.onPostExecute(asyncResult);

        if (mIsEndlessLoading) {
            // mRecyclerAdapter.getItemCount() = 0 when configuration changes (like orientation changes)
            if (mRecyclerAdapter.getItemCount() == 0) {
                mIsEndlessLoading = true;
                setSwipeRefreshLayoutEnabled(false);
                mRecyclerAdapter.showFooterProgress();
            } else {
                mRecyclerAdapter.hideFooterProgress();
                mIsEndlessLoading = false;
            }
        }

        if (asyncResult.exception != null) {
            if (getUserVisibleHint()) {
                AsyncResult.handleException(asyncResult.exception);
            }
        } else {
            try {
                PostList postList = asyncResult.data.unwrap();
                mRecyclerAdapter.setDataSet(postList.getPostList());

                mOnPagerInteractionCallback.setTotalPages(postList.getPostListInfo().getReplies() + 1);
            } catch (NullPointerException e) {
                ToastHelper.showByResId(R.string.message_server_error);
            }
        }
    }

    /**
     * A callback interface that all activities containing this Fragment must implement.
     */
    public static interface OnPagerInteractionCallback {

        public int getTotalPages();

        /**
         * Callback to set actual total pages which used for {@link android.support.v4.view.PagerAdapter}
         */
        public void setTotalPages(int i);
    }
}
