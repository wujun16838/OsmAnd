package net.osmand.plus.search;

import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;

import net.osmand.IndexConstants;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.OsmAndListFragment;
import net.osmand.plus.helpers.GpxUiHelper.GPXInfo;
import net.osmand.plus.helpers.SearchHistoryHelper;
import net.osmand.plus.search.QuickSearchDialogFragment.QuickSearchType;
import net.osmand.plus.search.listitems.QuickSearchBottomShadowListItem;
import net.osmand.plus.search.listitems.QuickSearchButtonListItem;
import net.osmand.plus.search.listitems.QuickSearchListItem;
import net.osmand.plus.search.listitems.QuickSearchListItemType;
import net.osmand.plus.search.listitems.QuickSearchTopShadowListItem;
import net.osmand.plus.track.fragments.TrackMenuFragment;
import net.osmand.search.core.ObjectType;
import net.osmand.search.core.SearchResult;
import net.osmand.util.Algorithms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class QuickSearchListFragment extends OsmAndListFragment {

	private QuickSearchDialogFragment dialogFragment;
	private QuickSearchListAdapter listAdapter;
	private boolean touching;
	private boolean scrolling;
	private boolean showResult;

	enum SearchListFragmentType {
		HISTORY,
		CATEGORIES,
		ADDRESS,
		MAIN
	}

	public abstract SearchListFragmentType getType();

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.search_dialog_list_layout, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		ListView listView = getListView();
		if (listView != null) {
			listView.setOnScrollListener(new AbsListView.OnScrollListener() {
				public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				}

				public void onScrollStateChanged(AbsListView view, int scrollState) {
					scrolling = (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
					if (scrolling) {
						dialogFragment.hideKeyboard();
					}
				}
			});
		}
	}

	@Override
	public void onListItemClick(ListView l, View view, int position, long id) {
		int index = position - l.getHeaderViewsCount();
		if (index < listAdapter.getCount()) {
			QuickSearchListItem item = listAdapter.getItem(index);
			if (item != null) {
				if (item.getType() == QuickSearchListItemType.BUTTON) {
					((QuickSearchButtonListItem) item).getOnClickListener().onClick(view);
				} else if (item.getType() == QuickSearchListItemType.SEARCH_RESULT) {
					SearchResult sr = item.getSearchResult();

					if (sr.objectType == ObjectType.POI
							|| sr.objectType == ObjectType.LOCATION
							|| sr.objectType == ObjectType.HOUSE
							|| sr.objectType == ObjectType.FAVORITE
							|| sr.objectType == ObjectType.RECENT_OBJ
							|| sr.objectType == ObjectType.WPT
							|| sr.objectType == ObjectType.STREET_INTERSECTION
							|| sr.objectType == ObjectType.GPX_TRACK) {

						showResult(sr);
					} else {
						if (sr.objectType == ObjectType.CITY || sr.objectType == ObjectType.VILLAGE || sr.objectType == ObjectType.STREET) {
							showResult = true;
						}
						dialogFragment.completeQueryWithObject(sr);
					}
				}
			}
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		OsmandApplication app = getMyApplication();
		boolean nightMode = !app.getSettings().isLightContent();
		dialogFragment = (QuickSearchDialogFragment) getParentFragment();
		listAdapter = new QuickSearchListAdapter(getMyApplication(), getActivity());
		listAdapter.setAccessibilityAssistant(dialogFragment.getAccessibilityAssistant());
		listAdapter.setUseMapCenter(dialogFragment.isUseMapCenter());
		setListAdapter(listAdapter);
		ListView listView = getListView();
		listView.setBackgroundColor(ColorUtilities.getActivityBgColor(app, nightMode));
		listView.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
					case MotionEvent.ACTION_POINTER_DOWN:
						touching = true;
						break;
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_POINTER_UP:
					case MotionEvent.ACTION_CANCEL:
						touching = false;
						break;
				}
				return false;
			}
		});
	}

	@Override
	public QuickSearchListAdapter getListAdapter() {
		return listAdapter;
	}

	public ArrayAdapter<?> getAdapter() {
		return listAdapter;
	}

	public QuickSearchDialogFragment getDialogFragment() {
		return dialogFragment;
	}

	@Override
	public void onResume() {
		super.onResume();
		dialogFragment.onSearchListFragmentResume(this);
	}

	public boolean isShowResult() {
		return showResult;
	}

	public void showResult(SearchResult searchResult) {
		showResult = false;
		if (searchResult.objectType == ObjectType.GPX_TRACK) {
			showTrackMenuFragment((GPXInfo) searchResult.relatedObject);
		} else if (searchResult.location != null) {
			Pair<PointDescription, Object> pointDescriptionObject =
					QuickSearchListItem.getPointDescriptionObject(getMyApplication(), searchResult);

			dialogFragment.hideToolbar();
			dialogFragment.hide();

			showOnMap(getMapActivity(), dialogFragment,
					searchResult.location.getLatitude(), searchResult.location.getLongitude(),
					searchResult.preferredZoom, pointDescriptionObject.first, pointDescriptionObject.second);
		}
	}

	public static void showOnMap(MapActivity mapActivity, QuickSearchDialogFragment dialogFragment,
								 double latitude, double longitude, int zoom,
								 @Nullable PointDescription pointDescription, Object object) {
		if (mapActivity != null) {
			OsmandApplication app = mapActivity.getMyApplication();
			QuickSearchType searchType = dialogFragment.getSearchType();
			if (searchType.isTargetPoint()) {
				String name = null;
				if (pointDescription != null) {
					String typeName = pointDescription.getTypeName();
					if (!Algorithms.isEmpty(typeName)) {
						name = mapActivity.getString(R.string.ltr_or_rtl_combine_via_comma, pointDescription.getName(), typeName);
					} else {
						name = pointDescription.getName();
					}
				}
				mapActivity.getMapLayers().getMapControlsLayer().selectAddress(name, latitude, longitude, searchType);

				dialogFragment.dismiss();
				mapActivity.getMapLayers().getMapControlsLayer().showRouteInfoMenu();
			} else {
				app.getSettings().setMapLocationToShow(latitude, longitude, zoom, pointDescription, true, object);
				MapActivity.launchMapActivityMoveToTop(mapActivity);
				dialogFragment.reloadHistory();
			}
		}
	}

	private void showTrackMenuFragment(GPXInfo gpxInfo) {
		OsmandApplication app = getMyApplication();
		MapActivity mapActivity = getMapActivity();
		SearchHistoryHelper.getInstance(app).addNewItemToHistory(gpxInfo);
		File file = new File(app.getAppPath(IndexConstants.GPX_INDEX_DIR), gpxInfo.getFileName());
		String path = file.getAbsolutePath();
		TrackMenuFragment.showInstance(mapActivity, path, false, null, QuickSearchDialogFragment.TAG, null);
		dialogFragment.dismiss();
	}

	public MapActivity getMapActivity() {
		return (MapActivity) getActivity();
	}

	public void updateLocation(LatLon latLon, Float heading) {
		if (listAdapter != null && !touching && !scrolling) {
			dialogFragment.getAccessibilityAssistant().lockEvents();
			listAdapter.notifyDataSetChanged();
			dialogFragment.getAccessibilityAssistant().unlockEvents();
			final View selected = dialogFragment.getAccessibilityAssistant().getFocusedView();
			if (selected != null) {
				try {
					int position = getListView().getPositionForView(selected);
					if ((position != AdapterView.INVALID_POSITION) && (position >= getListView().getHeaderViewsCount())) {
						dialogFragment.getNavigationInfo().updateTargetDirection(
								listAdapter.getItem(position - getListView().getHeaderViewsCount()).getSearchResult().location,
								heading.floatValue());
					}
				} catch (Exception e) {
					return;
				}
			}
		}
	}

	public void updateListAdapter(List<QuickSearchListItem> listItems, boolean append) {
		updateListAdapter(listItems, append, true);
	}

	public void updateListAdapter(List<QuickSearchListItem> listItems, boolean append, boolean addShadows) {
		if (listAdapter != null) {
			List<QuickSearchListItem> list = new ArrayList<>(listItems);
			if (list.size() > 0) {
				showResult = false;
				if (addShadows) {
					list.add(0, new QuickSearchTopShadowListItem(getMyApplication()));
					list.add(new QuickSearchBottomShadowListItem(getMyApplication()));
				}
			}
			listAdapter.setListItems(list);
			if (!append) {
				getListView().setSelection(0);
			}
		}
	}

	public void addListItem(QuickSearchListItem listItem) {
		if (listItem != null) {
			if (listAdapter.getCount() == 0) {
				List<QuickSearchListItem> list = new ArrayList<>();
				list.add(new QuickSearchTopShadowListItem(getMyApplication()));
				list.add(listItem);
				list.add(new QuickSearchBottomShadowListItem(getMyApplication()));
				listAdapter.setListItems(list);
			} else {
				QuickSearchListItem lastItem = listAdapter.getItem(listAdapter.getCount() - 1);
				if (lastItem.getType() == QuickSearchListItemType.BOTTOM_SHADOW) {
					listAdapter.insertListItem(listItem, listAdapter.getCount() - 1);
				} else {
					listAdapter.addListItem(listItem);
				}
			}
		}
	}
}