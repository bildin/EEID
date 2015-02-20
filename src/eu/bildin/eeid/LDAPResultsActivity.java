package eu.bildin.eeid;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.*;
import android.util.*;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

import org.apache.http.*;
import org.apache.http.client.entity.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.message.*;
import org.apache.http.params.*;

public class LDAPResultsActivity extends ListActivity
{
	private String item;
	private String csrf = "";
	private String idCode;
	private String fName;
	private String lName;
	public static String[] urls = new String[]{
		"https://www.politsei.ee/et/teenused/e-paringud/elamisluba/",
		"https://www.politsei.ee/et/teenused/e-paringud/elamisloa-taotlus/",
		"https://www.politsei.ee/et/teenused/e-paringud/id-kaardi-ja-passi-taotlus/",
		//"https://www.politsei.ee/et/teenused/e-paringud/tooluba/",
		"https://www.politsei.ee/et/teenused/e-paringud/elamisoigus/",
		"https://www.politsei.ee/et/teenused/e-paringud/elamisoigusetaotlus/"	        	
    };
	private List<AsyncTask> tasks;
	private String[] webresults = new String[urls.length];
	private int webdone;
	private ProgressDialog progress;
	private ArrayAdapter<String> adapter;
	private List<String> results;
	private EditText filterText;
	private Executor executor;


    @Override
    public void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ldap_results);
		

		progress = new ProgressDialog(this);
		tasks = new ArrayList<AsyncTask>();
		
		executor = AsyncTask.THREAD_POOL_EXECUTOR;
		
        Intent intent = getIntent();
        results = intent.getStringArrayListExtra("results");
		Collections.sort(results);
        adapter = new LDAPResultsArrayAdapter(this, results);
        setListAdapter(adapter);
		
        setTitle(getString(R.string.title_ldap)+": "+results.size());
		
		filterText = (EditText) findViewById(R.id.search_box);
		filterText.addTextChangedListener(mTextWatcher);
   
	}
	
	private TextWatcher mTextWatcher = new TextWatcher()
	{ 
		public void afterTextChanged(Editable s){}
		public void beforeTextChanged(CharSequence s, int start, int count, int after){}
		public void onTextChanged(CharSequence s, int start, int before, int count) 
		{
			if(s.toString().contains(":"))
			{
				if(s.length()==2)
				{
					adapter.sort(LDAPResultsArrayAdapter.getComp((int)s.toString().charAt(1)));
					filterText.setText("");
				}
			}
			else{
				adapter.getFilter().filter(s,
					new Filter.FilterListener() { 
						public void onFilterComplete(int count) { 
							setTitle(getString(R.string.title_ldap)+": "+ count + "/" + results.size());
							}
					}
				);
			}
		}
	};
	
	private void newTask(String... p)
	{
		HTTPRequestTask task = new HTTPRequestTask();
		tasks.add(task);
		if(Build.VERSION.SDK_INT>=11)
			task.executeOnExecutor(executor,p);
		else
			task.execute(p);
	}
	
    @Override
    public void onListItemClick(ListView l, View v, int position, long id)
	{
		if(item==(item=(String) getListAdapter().getItem(position))) 
		{
			HTTPRequestCallback();
			return;
		}
		String[] values = item.split(",");
		lName = values[0];
		fName = values[1];
		idCode = values[2];
		webdone = 0;
		tasks.clear();
		
		for (int i = 0; i < urls.length; i++)
		{
			newTask(String.valueOf(i));
		}

		progress.setIcon(R.drawable.ic_action_search);
		progress.setTitle(item);
		progress.setMessage("Prepairing...");
		progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progress.setMax(tasks.size());
		progress.setCanceledOnTouchOutside(false);
		progress.setOnCancelListener(new DialogInterface.OnCancelListener(){
				public void onCancel(DialogInterface dialog){
					cancelTasks(tasks);
				}
			});
		progress.setButton(progress.BUTTON_POSITIVE,getString(R.string.button_finish),new DialogInterface.OnClickListener(){
				public void onClick(DialogInterface dialog, int which){
					cancelTasks(tasks);
					HTTPRequestCallback();
				}
			});
		progress.setButton(progress.BUTTON_NEGATIVE,getString(R.string.button_cancel),new DialogInterface.OnClickListener(){
				public void onClick(DialogInterface dialog, int which){
					cancelTasks(tasks);
				}
			});
		progress.show();
    }

    public void HTTPRequestCallback()
	{
		progress.dismiss();
		
    	Intent intent = new Intent(this, HTTPResultsActivity.class);
       	intent.putExtra("results", webresults);
		intent.putExtra("item", item);
        startActivity(intent);
    }

	public void cancelTasks(List<AsyncTask> tasks)
	{
		for(AsyncTask task : tasks)
		{
			task.cancel(true);
		}
	}
	
    private class HTTPRequestTask extends AsyncTask<String, String, String>
	{
        @Override
        protected String doInBackground(String... value)
		{
			int index = Integer.valueOf(value[0]);
			String response = "";
			String url = urls[index];

Log.d("mfa", "preCli" + index);
			DefaultHttpClient client = new DefaultHttpClient();
			client.getParams().setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, Boolean.FALSE);
Log.d("mfa", "postCli" + index);

			if (csrf == "")
			{
				publishProgress("1", value[0], "Getting CSRF...");
Log.d("mfa", "preCSRF" + index);
				HttpGet httpGet = new HttpGet(url);

				try
				{
					HttpResponse execute = client.execute(httpGet);
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					execute.getEntity().writeTo(out);
					out.close();
					response = out.toString();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
				publishProgress("2", value[0], "Parsing CSRF...");
Log.d("mfa", "preParseCSRF" + index);

				Pattern p = Pattern.compile("name=\"csrf\" value=\"([^\"]+)");
				Matcher m = p.matcher(response);
				while (m.find())
				{
					csrf = m.group(1);
				}
				publishProgress("3", value[0], "CSRF: " + csrf);
Log.d("mfa", "postParseCSRF" + index);
			}
			
			
Log.d("mfa", "preReq" + index);
			HttpPost httpPost = new HttpPost(url);

			publishProgress("" + (1 + (index + 1) * 3), value[0], "Requesting...");

			try
			{
                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
                nameValuePairs.add(new BasicNameValuePair("csrf", csrf));
                nameValuePairs.add(new BasicNameValuePair("cmd", "request"));
                nameValuePairs.add(new BasicNameValuePair("idCode", idCode));
                nameValuePairs.add(new BasicNameValuePair("fName", fName));
                nameValuePairs.add(new BasicNameValuePair("lName", lName));
                nameValuePairs.add(new BasicNameValuePair("subButton", "Esita päring"));
                httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));
                httpPost.setHeader("Content-type", "application/x-www-form-urlencoded");

				HttpResponse execute = client.execute(httpPost);

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				execute.getEntity().writeTo(out);
				out.close();
				response = out.toString();
            }
			catch (Exception e)
			{
				e.printStackTrace();
				response =  e.getMessage();
            }
            String msg = "<no data>";
            msg = response;
			publishProgress("" + (2 + (index + 1) * 3), value[0], "Parsing...");
Log.d("mfa", "preParse" + index);
			Pattern p = Pattern.compile("(([^>]*" + fName + " " + lName + "[^<]*)|(Puudu[^.]+.)|(Isik on[^.]+.)|(Isikul[^.]+.))(?s)");
        	Matcher m = p.matcher(response);
       	 	if (m.find())
			{
				msg = Html.fromHtml(m.group().trim()).toString();
       	 	}
			else
			{
				msg = "No matches in: " + msg.substring(0,100);
			}
			publishProgress("" + (3 + (index + 1) * 3), value[0], "Info: " + msg.substring(0, 10) + "...");
Log.d("mfa", "postParse" + index);

			webresults[index] = msg;
			return msg;
        }

		@Override
		protected void onPreExecute()
		{
		}

		@Override
		protected void onProgressUpdate(String... pro)
		{
			progress.setMessage("Process[" + pro[1] + "]: " + pro[2]);
		}


        @Override
        protected void onPostExecute(String result)
		{
			progress.setProgress(++webdone);
Log.d("mfa", "webdone: " + webdone + "/" + tasks.size());
        	if (webdone == tasks.size()) HTTPRequestCallback();
        }
    }
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		//adapter.sort(LDAPResultsArrayAdapter.getComp(item.getItemId()));
		item.setChecked(!item.isChecked());
		return true;
	}
	
}
