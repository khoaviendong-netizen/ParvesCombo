/*
 ============================================================================
 Name        : AppListActivity.java
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2025 xyz
 Description : App List Activity
 ============================================================================
 */

package hev.sockstun;

import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Comparator;
import java.util.Collections;
import java.util.ArrayList;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.content.res.ColorStateList;
import android.text.TextWatcher;
import android.text.Editable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;

public class AppListActivity extends Activity {
	private Preferences prefs;
	private AppArrayAdapter adapter;
	private ListView listView;
	private boolean isChanged = false;

	private class Package {
		public PackageInfo info;
		public boolean selected;
		public String label;

		public Package(PackageInfo info, boolean selected, String label) {
			this.info = info;
			this.selected = selected;
			this.label = label;
		}
	}

	private class AppArrayAdapter extends ArrayAdapter<Package> {
		private final List<Package> allPackages = new ArrayList<Package>();
		private final List<Package> filteredPackages = new ArrayList<Package>();
		private String lastFilter = "";

		public AppArrayAdapter(Context context) {
			super(context, R.layout.appitem);
		}

		@Override
		public void add(Package pkg) {
			allPackages.add(pkg);
			if (matchesFilter(pkg, lastFilter))
				filteredPackages.add(pkg);
			notifyDataSetChanged();
		}

		@Override
		public void clear() {
			allPackages.clear();
			filteredPackages.clear();
			notifyDataSetChanged();
		}

		@Override
		public void sort(Comparator<? super Package> cmp) {
			Collections.sort(allPackages, (Comparator) cmp);
			applyFilter(lastFilter);
		}

		@Override
		public int getCount() {
			return filteredPackages.size();
		}

		@Override
		public Package getItem(int position) {
			return filteredPackages.get(position);
		}

		public List<Package> getAllPackages() {
			return allPackages;
		}

		private boolean matchesFilter(Package pkg, String filter) {
			if (filter == null || filter.length() == 0)
				return true;
			return pkg.label.toLowerCase().contains(filter.toLowerCase());
		}

		public void applyFilter(String filter) {
			lastFilter = filter != null ? filter : "";
			filteredPackages.clear();
			if (lastFilter.length() == 0) {
				filteredPackages.addAll(allPackages);
			} else {
				String f = lastFilter.toLowerCase();
				for (Package p : allPackages) {
					if (p.label != null && p.label.toLowerCase().contains(f))
						filteredPackages.add(p);
				}
			}
			notifyDataSetChanged();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View rowView = inflater.inflate(R.layout.appitem, parent, false);
			ImageView imageView = (ImageView) rowView.findViewById(R.id.icon);
			TextView textView = (TextView) rowView.findViewById(R.id.name);
			TextView packageView = (TextView) rowView.findViewById(R.id.app_package_name);
			CheckBox checkBox = (CheckBox) rowView.findViewById(R.id.checked);

			Package pkg = getItem(position);
			PackageManager pm = getContext().getPackageManager();
			ApplicationInfo appinfo = pkg.info.applicationInfo;
			imageView.setImageDrawable(appinfo.loadIcon(pm));
			textView.setText(appinfo.loadLabel(pm).toString());
			packageView.setText(pkg.info.packageName);
			checkBox.setChecked(pkg.selected);
			checkBox.setButtonTintList(new ColorStateList(
				new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} },
				new int[] { PremiumUi.CYAN, PremiumUi.MUTED }));
			rowView.setBackground(PremiumUi.surface(getContext(), PremiumUi.CARD,
				pkg.selected ? PremiumUi.CYAN : PremiumUi.BORDER, 14));

			return rowView;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PremiumUi.setupWindow(this);
		setContentView(R.layout.applist);
		Button back = findViewById(R.id.back);
		PremiumUi.styleButton(back, "back", false, false);
		back.setText("");
		back.setPadding(PremiumUi.dp(this, 14), 0, PremiumUi.dp(this, 14), 0);
		back.setBackgroundColor(PremiumUi.BG);
		((LinearLayout) findViewById(R.id.apps_bottom_nav)).addView(PremiumUi.bottomNav(this, "Apps"));
		findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				finish();
			}
		});
		listView = (ListView) findViewById(R.id.list);
		listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		listView.setEmptyView(findViewById(R.id.apps_empty));

		prefs = new Preferences(this);
		Set<String> apps = prefs.getApps();
		PackageManager pm = getPackageManager();
		adapter = new AppArrayAdapter(this);

		for (PackageInfo info : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
			if (info.packageName.equals(getPackageName()))
			  continue;
			if (info.requestedPermissions == null)
			  continue;
			if (!Arrays.asList(info.requestedPermissions).contains(Manifest.permission.INTERNET))
			  continue;
			boolean selected = apps.contains(info.packageName);
			String label = info.applicationInfo.loadLabel(pm).toString();
			Package pkg = new Package(info, selected, label);
			adapter.add(pkg);
		}

		EditText searchBox = (EditText) findViewById(R.id.search);
		PremiumUi.styleInput(searchBox);

		adapter.sort(new Comparator<Package>() {
			public int compare(Package a, Package b) {
				if (a.selected != b.selected)
				  return a.selected ? -1 : 1;
				return a.label.compareTo(b.label);
			}
		});

		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Package pkg = adapter.getItem(position);
				pkg.selected = !pkg.selected;
				CheckBox checkbox = (CheckBox) view.findViewById(R.id.checked);
				if (checkbox != null)
					checkbox.setChecked(pkg.selected);
				view.setBackground(PremiumUi.surface(AppListActivity.this, PremiumUi.CARD,
					pkg.selected ? PremiumUi.CYAN : PremiumUi.BORDER, 14));
				isChanged = true;
			}
		});

		searchBox.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				adapter.applyFilter(s.toString());
			}

			@Override
			public void afterTextChanged(Editable s) { }
		});
	}

	@Override
	protected void onPause() {
		if (isChanged) {
			Set<String> apps = new HashSet<String>();

			for (Package pkg : adapter.getAllPackages()) {
				if (pkg.selected)
				  apps.add(pkg.info.packageName);
			}

			prefs.setApps(apps);
			isChanged = false;
		}
		super.onPause();
	}
}
