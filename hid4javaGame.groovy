@GrabResolver(name='nr', root='https://oss.sonatype.org/service/local/repositories/releases/content/')
@GrabResolver(name='mvnRepository', root='https://repo1.maven.org/maven2/')
@Grab(group='net.java.dev.jna', module='jna', version='4.2.2')
@Grab(group='org.hid4java', module='hid4java', version='0.5.0')

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;

class manager{

	HidServices hidServices = null;
	HidDevice hidDevice = null;
	String name ="gamepad";
	boolean connected = false;

	void setName(String n){
		name=n
	}
	String getName(){
		return name
	}
	void connect() {
		if(connected)
			return;
		println "Connecting Game Controller"
		if (hidServices == null)
			hidServices = HidManager.getHidServices();
		hidDevice = null;
		int foundInterface = Integer.MAX_VALUE;
		for (HidDevice h : hidServices.getAttachedHidDevices()) {
			println  h
		}
		connected=true
	}

	void disconnect() {
		println "disconnecting Game controller"
	}
}
def m = new manager()
m.connect()

return m