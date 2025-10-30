package crc6459c103e6af800a80;


public class GPSService_GnssCallback
	extends android.location.GnssStatus.Callback
	implements
		mono.android.IGCUserPeer
{
/** @hide */
	public static final String __md_methods;
	static {
		__md_methods = 
			"n_onSatelliteStatusChanged:(Landroid/location/GnssStatus;)V:GetOnSatelliteStatusChanged_Landroid_location_GnssStatus_Handler\n" +
			"n_onStarted:()V:GetOnStartedHandler\n" +
			"n_onFirstFix:(I)V:GetOnFirstFix_IHandler\n" +
			"n_onStopped:()V:GetOnStoppedHandler\n" +
			"";
		mono.android.Runtime.register ("DataCollector.Services.GPSService+GnssCallback, DataCollector", GPSService_GnssCallback.class, __md_methods);
	}


	public GPSService_GnssCallback ()
	{
		super ();
		if (getClass () == GPSService_GnssCallback.class) {
			mono.android.TypeManager.Activate ("DataCollector.Services.GPSService+GnssCallback, DataCollector", "", this, new java.lang.Object[] {  });
		}
	}


	public void onSatelliteStatusChanged (android.location.GnssStatus p0)
	{
		n_onSatelliteStatusChanged (p0);
	}

	private native void n_onSatelliteStatusChanged (android.location.GnssStatus p0);


	public void onStarted ()
	{
		n_onStarted ();
	}

	private native void n_onStarted ();


	public void onFirstFix (int p0)
	{
		n_onFirstFix (p0);
	}

	private native void n_onFirstFix (int p0);


	public void onStopped ()
	{
		n_onStopped ();
	}

	private native void n_onStopped ();

	private java.util.ArrayList refList;
	public void monodroidAddReference (java.lang.Object obj)
	{
		if (refList == null)
			refList = new java.util.ArrayList ();
		refList.add (obj);
	}

	public void monodroidClearReferences ()
	{
		if (refList != null)
			refList.clear ();
	}
}
