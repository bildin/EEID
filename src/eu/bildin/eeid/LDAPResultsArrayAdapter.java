package eu.bildin.eeid;

import android.content.*;
import android.util.*;
import android.view.*;
import android.widget.*;

import java.util.*;
import java.util.zip.*;


public class LDAPResultsArrayAdapter extends ArrayAdapter<String> implements Filterable
{
	private final List<String> oValues;
	private String[] values;
	private Filter mFilter;
	private LayoutInflater mInflater;

	public LDAPResultsArrayAdapter(Context context, List<String> values)
	{
	    super(context, R.layout.row_ldap_results, values);
	    this.oValues = values;
		this.values = values.toArray(new String[0]);
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	public static Comparator<String> getComp(final int type) {
		return new Comparator<String>()
		{
			public int compare(String p1, String p2)
			{
				int back = -1;
				switch(type)
				{
					case 0:
					case 'S':
						back = 1;
					case 1:
					case 's':
						return back*p1.compareToIgnoreCase(p2);
					case 2:
					case 'N':
						back = 1;
					case 3:
					case 'n':
						return back*p1.substring(p1.indexOf(",")+1).compareToIgnoreCase(p2.substring(p2.indexOf(",")+1));
					case 4:
					case 'I':
						back = 1;
					case 5:
					case 'i':
						return back*p1.substring(p1.lastIndexOf(",")+1).compareToIgnoreCase(p2.substring(p2.lastIndexOf(",")+1));
					default:
						return p1.compareToIgnoreCase(p2);
				}
			}
		};
	};
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		ViewHolder holder;
		if(convertView==null)
		{
		convertView = mInflater.inflate(R.layout.row_ldap_results, null);
		holder = new ViewHolder();
	    holder.textView1 = (TextView) convertView.findViewById(R.id.textView1);
	    holder.textView2 = (TextView) convertView.findViewById(R.id.textView2);
	    holder.textView3 = (TextView) convertView.findViewById(R.id.textView3);
		convertView.setTag(holder);
		}
		else
		{
			holder = (ViewHolder) convertView.getTag();
		}
		String[] value = (values[position]+", , , ").split(",");
	    holder.textView1.setText(value[0]);
	    holder.textView2.setText(value[1]);
	    holder.textView3.setText(value[2]);
	    int color = (value[2].charAt(0) % 2 == 1) ?0xFF0000FF: 0xFFFF0000;
	    holder.textView1.setTextColor(color);
	    holder.textView2.setTextColor(color);
	    holder.textView3.setTextColor(color);
	    return convertView;
	}

	@Override
	public int getCount()
	{
		return values.length;
	}

	@Override
	public String getItem(int position)
	{
		return values[position];
	}
	
	public Filter getFilter()
	{
		if (mFilter == null)
		{
			mFilter = new CustomFilter();
		}
		return mFilter;
	}

	private class CustomFilter extends Filter
	{
		@Override
		protected FilterResults performFiltering(CharSequence constraint)
		{
			FilterResults results = new FilterResults();
			if (constraint == null || constraint.length() == 0)
			{
				results.values = oValues.toArray(new String[0]);
				results.count = oValues.size();
			}
			else
			{
				ArrayList<String> nValues = new ArrayList<String>();
				for (String item : oValues)
					if (item.toUpperCase().contains(constraint.toString().toUpperCase()))
						nValues.add(item);
				results.values = nValues.toArray(new String[0]);
				results.count = nValues.size();
			}
			return results;
		}

		@SuppressWarnings("unchecked")
		@Override protected void publishResults(CharSequence constraint, FilterResults results)
		{
			values = (String[]) results.values;
			notifyDataSetChanged();
		}
	}
	
	public static class ViewHolder
	{
		public TextView textView1, textView2, textView3;
	}
}
