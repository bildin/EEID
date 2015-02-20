package eu.bildin.eeid;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.view.*;
import android.widget.*;

public class HTTPResultsActivity extends ListActivity
{
	private String item;
	private String lName;
	private String fName;
	private String idCode;
	
    @Override
    public void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
		item = intent.getStringExtra("item");
		String[] values = item.split(",");
		lName = values[0];
		fName = values[1];
		idCode = values[2];
		setTitle(item);
        String[] results = intent.getStringArrayExtra("results");
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, results);
        setListAdapter(adapter);
    }
	
    @Override
    public void onListItemClick(ListView l, View v, int position, long id)
	{
		//setTitle(LDAPResultsActivity.urls[position]);
	}
	
}
