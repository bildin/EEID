package eu.bildin.eeid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;

import android.widget.*;
import android.text.*;
import android.content.*;
import android.util.*;
import android.os.*;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.Dialog;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.DialogFragment;
import android.view.*;

import java.util.*;
import java.util.concurrent.*;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.*;
import com.unboundid.util.args.*;


public class MainActivity extends FragmentActivity
{
	public final static String RESULTS = "LDAP_RESULTS";
	public static Integer LDAP_TASK_SIZE_LIMIT = 2000;
	public static Integer LDAP_SUBTASK_COUNT =2;
	public static String LDAP_SERVER = "ldap.sk.ee";
	public static Integer LDAP_PORT = 389;
	public static String LDAP_DN = "ou=Authentication, o=ESTEID, c=EE";
	
	private ProgressDialog progress;
	private ArrayList<String> results;
	private List<AsyncTask> tasks;
	private List<String> ids;
	private Calendar now;
	private ArrayAdapter<String> adapter;
	private AutoCompleteTextView mAC;
	private View button;
	private ArrayBlockingQueue<Runnable> queue;
	private Executor executor;
	
	private int ids_done;
	private int century;
	//private int sex;
	//delete this line


    @Override
    public void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
		
		progress = new ProgressDialog(this);
		results = new ArrayList<String>();
		tasks = new ArrayList<AsyncTask>();
		ids = new ArrayList<String>();
		now = Calendar.getInstance();
		adapter = new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line, new String[0]);
		mAC = (AutoCompleteTextView) findViewById(R.id.autoCompleteTextView1);
		button = findViewById(R.id.buttonView1);

		int cpus = Runtime.getRuntime().availableProcessors();
		int maxThreads = cpus * 20;
		maxThreads = (maxThreads > 0) ? maxThreads : 1;
		queue = new ArrayBlockingQueue<Runnable>(maxThreads);

		executor = new ThreadPoolExecutor(
			maxThreads,
			maxThreads*2,
			30,
			TimeUnit.SECONDS,
			queue,
			new ThreadPoolExecutor.CallerRunsPolicy()
		);

		mAC.addTextChangedListener(mTextWatcher);
		mAC.setAdapter(adapter);
		
		mAC.setTextColor(0xFF000000);
		button.setEnabled(false);
		
		findViewById(R.id.buttonView3).setEnabled(false);
    }
	
	private TextWatcher mTextWatcher = new TextWatcher()
	{
		public void beforeTextChanged(CharSequence p1, int p2, int p3, int p4)	{	}
		public void onTextChanged(CharSequence p1, int p2, int p3, int p4)	{	}
		public void afterTextChanged(Editable s)
		{
			String v = mAC.getText().toString();
			int l = v.length();
			if(l < 7)
			{
				button.setEnabled(false);
				mAC.setBackgroundColor((l==0)?0xFFFFFFFF:0xFFFF0000);
			}
			else if(l < 11)
			{
				button.setEnabled(true);
				mAC.setBackgroundColor(0xFFFFFF00);
			}
			if(l == 8)
			{
				adapter.clear();
				for(int i=0;i<=10;i++)
					for(int j=0;j<=10;j++)
						adapter.add(addcNum(mAC.getText().toString()+(char)(48+i)+(char)(48+j)));
			}
			if(l == 11)
			{
				boolean isvalid = v.equals(addcNum(v.substring(0,10)));
				if(isvalid)
				{
					button.setEnabled(true);
					mAC.setBackgroundColor(0xFF00FF00);
				}
				else
				{
					button.setEnabled(false);
					mAC.setBackgroundColor(0xFFFF0000);
				}
			}
		}
	};
	
	private void newTask(Integer... p)
	{
		LDAPRequestTask task = new LDAPRequestTask();
		tasks.add(task);
		if(Build.VERSION.SDK_INT>=11)
			task.executeOnExecutor(executor,p);
		else
			task.execute(p);
	}

    public void LDAPSearch(View view)
	{
		tasks.clear();

		progress.setIcon(R.drawable.ic_action_search);
		progress.setTitle(getString(R.string.progress_ldap));
		progress.setMessage("");
		progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progress.setMax(1);
		progress.setProgress(0);
		progress.setSecondaryProgress(0);
		progress.setCanceledOnTouchOutside(false);
		progress.setOnCancelListener(new DialogInterface.OnCancelListener(){
			public void onCancel(DialogInterface dialog){
				cancelTasks(tasks);
			}
		});
		progress.setButton(progress.BUTTON_POSITIVE,getString(R.string.button_finish),new DialogInterface.OnClickListener(){
			public void onClick(DialogInterface dialog, int which){
				cancelTasks(tasks);
				forward();
			}
		});
		progress.setButton(progress.BUTTON_NEGATIVE,getString(R.string.button_cancel),new DialogInterface.OnClickListener(){
			public void onClick(DialogInterface dialog, int which){
				cancelTasks(tasks);
			}
		});
		progress.show();
		progress.getButton(progress.BUTTON_POSITIVE).setEnabled(false);

		MakeListTask task = new MakeListTask();
		tasks.add(task);
		task.execute(mAC.getText().toString());
    }
	

	private String addcNum(String id)
	{
		if(id.contains(":")) return id+":";
		
		int cNum=0;
		int iNum;
		for (int i=1;i<=id.length();i++)
		{
			iNum=id.charAt(i-1)-48;
			cNum+=iNum*((i==10)?1:i);
		}
		cNum%=11;
		if ((cNum%10)==0)
		{
			cNum = 0;
			for (int i=1;i<=id.length();i++)
			{
				iNum=id.charAt(i-1)-48;
				cNum+=iNum*((i>7)?(i-7):(i+2));
			}
			cNum %= 11;
			cNum %= 10;
		}
		return id+cNum;
	}

	private class MakeListTask extends AsyncTask<String, Integer, Integer>
	{
		@Override
		protected Integer doInBackground(String... p1)
		{
			String pattern = p1[0];
			if(pattern.length() < 6) return 0;

			for(int l = pattern.length(); l < 11; l++) pattern+=":";

			List<List<Integer>> edges = new ArrayList<List<Integer>>();
			for(int i = 0; i < 5; i++)
				edges.add(new ArrayList<Integer>());

			int c1, c2, c3;

			//c&g
			c1=pattern.charAt(0)-48;
			for(int i = (c1==10)?((century!=0)?(century*2-1):1):c1;i<=((c1==10)?((century!=0)?(century*2):6):c1);i++)
				edges.get(0).add(i);
			//yy
			c1=pattern.charAt(1)-48;
			c2=pattern.charAt(2)-48;
			for(int i = (c1==10)?0:c1;i<=((c1==10)?9:c1);i++)
				for(int j = (c2==10)?0:c2;j<=((c2==10)?9:c2);j++)
					edges.get(1).add(i*10+j);
			//mm
			c1=pattern.charAt(3)-48;
			c2=pattern.charAt(4)-48;
			for(int i = (c1==10)?0:c1;i<=((c1==10)?1:c1);i++)
				for(int j = (c2==10)?0:c2;j<=((c2==10)?9:c2);j++)
				{
					int mm = i*10+j;
					if(mm>=1 && mm<=12)
						edges.get(2).add(mm);
				}
			//dd
			c1=pattern.charAt(5)-48;
			c2=pattern.charAt(6)-48;
			for(int i = (c1==10)?0:c1;i<=((c1==10)?3:c1);i++)
				for(int j = (c2==10)?0:c2;j<=((c2==10)?9:c2);j++)
				{
					int dd = i*10+j;
					if(dd>=1 && dd<=31)
						edges.get(3).add(dd);
				}
			//ppp
			c1=pattern.charAt(7)-48;
			c2=pattern.charAt(8)-48;
			c3=pattern.charAt(9)-48;
			for(int i=(c1==10)?0:c1;i<=((c1==10)?9:c1);i++)
				for(int j=(c2==10)?0:c2;j<=((c2==10)?9:c2);j++)
					for(int k=(c3==10)?0:c3;k<=((c3==10)?9:c3);k++)
						edges.get(4).add(i*100+j*10+k);
						
			int total = 1;
			int current = 0;
			int update = 0;
			for(List<Integer> edge : edges)
				total *= edge.size();

			ids.clear();
			publishProgress(0,0,total);
			update = total/10;

			for(int g : edges.get(0))
				for(int yy : edges.get(1))
					for(int mm : edges.get(2))
						for(int dd : edges.get(3))
							for(int ppp : edges.get(4))
							{
								if(isCancelled()) return null;
								current++;
								if(validateID(g,yy,mm,dd,ppp))
								{
									ids.add(addcNum(String.format("%d%02d%02d%02d%03d",g,yy,mm,dd,ppp)));
								}
								if((--update)==0)
								{
									publishProgress(ids.size(),current);
									update = total/10;
								}
							}
							
			return ids.size();
		}

		@Override
		protected void onProgressUpdate(Integer... p1)
		{
			if(p1.length > 2) progress.setMax(p1[2]);
			progress.setProgress(p1[1]);
			progress.setMessage(String.format("%s %d/%d",getString(R.string.progress_verified),p1[0],p1[1]));
		}

        @Override 
        protected void onPostExecute(Integer done)
		{
			tasks.remove(this);
			if(done!=0)
			{
				results.clear();
				ids_done=0;
				newTask(0);
				progress.setMax(done);
				progress.setProgress(0);
				progress.setMessage("");
				progress.getButton(progress.BUTTON_POSITIVE).setEnabled(true);
			}
			else
			{
				progress.dismiss();
			}
		}
	}
	
    private class LDAPRequestTask extends AsyncTask<Integer, Integer, Integer>
	{
        @Override
        protected Integer doInBackground(Integer... fs)
		{
			int start=fs[0];
			int stop = (fs.length > 1) ?fs[1]: ids.size();
			int pool = stop - start;
			boolean needsubtask = (pool > LDAP_TASK_SIZE_LIMIT);
			int res_size = 0;

			if (!needsubtask)
			{
				publishProgress(pool);
				ArrayList<String> locresults = new ArrayList<String>();
				StringBuilder sb = new StringBuilder("(|");
				for (int i = start; i < stop; i++)
				{
					sb.append("(serialnumber=");
					sb.append(ids.get(i));
					sb.append(")");
				}
				sb.append(")");
				try
				{
					LDAPConnection ldapConnection = new LDAPConnection(LDAP_SERVER, LDAP_PORT);
					SearchResult searchResult = ldapConnection.search(
						LDAP_DN,
						SearchScope.SUB,
						sb.toString(),
						"cn"
					);
					res_size = searchResult.getSearchEntries().size();
					if (res_size > 0)
						for (int i = 0; i < res_size; i++)
							locresults.add(searchResult.getSearchEntries().get(i).getAttributeValue("cn"));
					ldapConnection.close();
					results.addAll(locresults);
				}
				catch (LDAPException e)
				{
					log(e.getExceptionMessage());
					needsubtask = (e.getResultCode() == ResultCode.SIZE_LIMIT_EXCEEDED);
					publishProgress(-pool);
				}
			}
			if (needsubtask)
			{
				for (int i = 0; i < LDAP_SUBTASK_COUNT; i++)
				{
					newTask(start + pool * i / LDAP_SUBTASK_COUNT, start + pool * (i + 1) / LDAP_SUBTASK_COUNT);
				}
				return 0;
			}
			return pool;
        }

		@Override
		protected void onProgressUpdate(Integer... p1)
		{
			progress.incrementSecondaryProgressBy(p1[0]);
		}

        @Override 
        protected void onPostExecute(Integer done)
		{
			tasks.remove(this);
			ids_done+=done;
			progress.setProgress(ids_done);
			progress.setMessage(String.format("%s %d/%d [%d<%d(%d)<%d]",
				getString(R.string.progress_done),
				results.size(),
				ids_done,
				tasks.size()-queue.size(),
				queue.size(),
				queue.remainingCapacity(),
				tasks.size()
			));
			if(ids_done == ids.size())
			{
				progress.dismiss();
				forward();
				findViewById(R.id.buttonView3).setEnabled(true);
			}
        }
    }
	
	public void pickDate(View view)
	{
		DialogFragment newFragment = new DatePickerFragment();
		newFragment.show(getSupportFragmentManager(), "datePicker");
	}
	
	public class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener
	{
		@Override public Dialog onCreateDialog(Bundle savedInstanceState) 
		{ 
		int year = 1985;//now.get(Calendar.YEAR); 
		int month = now.get(Calendar.MONTH); 
		int day = now.get(Calendar.DAY_OF_MONTH); 
		return new DatePickerDialog(getActivity(), this, year, month, day); 
		}
		
		public void onDateSet(DatePicker view, int y, int m, int d) 
		{
			Calendar ymd = new GregorianCalendar(y,m,d);
			boolean before = ymd.before(now);
			if(before)
			{
				century=(int)Math.floor((double)y/100)-17;
				mAC.setText(String.format(":%02d%02d%02d", (y-y/100*100),m+1,d));
			}
		} 
	}
	

	public void forward(View view){forward();}
	
	public void forward()
	{
    	Intent intent = new Intent(this, LDAPResultsActivity.class);
    	intent.putStringArrayListExtra("results", results);
        startActivity(intent);
	}

	
	public void cancelTasks(List<AsyncTask> tasks)
	{
		for(AsyncTask task : tasks)
		{
			task.cancel(true);
		}
	}

	public boolean validateID(int g, int yy, int mm, int dd, int ppp)
	{
		boolean isvalid = false;
		if (mm>0&&mm<=12&&dd>0&&ppp>0)
		{
			int yyyy = (17+(g+g%2)/2)*100+yy;
			
			int ny = now.get(Calendar.YEAR);
			int nm = now.get(Calendar.MONTH)+1;
			int nd = now.get(Calendar.DATE);
			
			boolean before = (yyyy!=ny)?(yyyy<ny):((mm!=nm)?(mm<nm):(dd<nd));
			
			if (before)
			{
				int days = ((mm%2==0)^(mm<8))?31:((mm!=2)?30:(((yyyy%4==0)&&(yyyy%400!=0)))?29:28);
				isvalid = (dd<=days);
			}
		}
		return isvalid;
	}
	
	public void log(Object obj)
	{
		Log.d("mfa",String.valueOf(obj));
	}
	//*
	private Menu optmenu;
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.activity_main, menu);
		this.optmenu=menu;
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		item.setChecked(!item.isChecked());
		return true;
	}
	//*/
}
