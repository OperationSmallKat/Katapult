import com.neuronrobotics.bowlerstudio.SplashManager
@GrabResolver(name='nr', root='https://oss.sonatype.org/service/local/repositories/releases/content/')
@Grab(group='com.neuronrobotics', module='SimplePacketComsJava', version='0.12.0')


import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine
import com.neuronrobotics.sdk.addons.gamepad.BowlerJInputDevice
import com.neuronrobotics.sdk.addons.gamepad.IJInputEventListener
import com.neuronrobotics.sdk.addons.kinematics.MobileBase
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR
import com.neuronrobotics.sdk.common.DeviceManager
import com.neuronrobotics.sdk.util.ThreadUtil

import edu.wpi.SimplePacketComs.phy.UDPSimplePacketComs;
import net.java.games.input.ControllerEnvironment
import net.java.games.input.Controller;
import net.java.games.input.Component;
import net.java.games.input.Event;

SplashManager.closeSplash();
def HWName="kevkat"
HashSet<InetAddress> addresses = UDPSimplePacketComs.getAllAddresses(HWName);
BowlerJInputDevice g=DeviceManager.getSpecificDevice(  "gamepad",{
	Controller [] controllerOptions = ControllerEnvironment.getDefaultEnvironment().getControllers()
	def possible=[]
	for(Controller c:controllerOptions){
		if(!c.getName().contains("Wacom")){
			possible.add(c)
		}
	}

	println possible
	if(possible.size()>=1) {
		def d = new BowlerJInputDevice(controllerOptions[0]); // This is the DyIO to talk to.
		d.connect(); // Connect to it.
		return d
	}
	throw new RuntimeException("Game controller not found!")
})


if(addresses.size()>=1){
	MobileBase cat =ScriptingEngine.gitScriptRun(	"https://github.com/OperationSmallKat/SmallKat_V2.git",
			"loadRobot.groovy",
			[
				"https://github.com/OperationSmallKat/greycat.git",
				"MediumKat.xml",
				"GameController_22",
				HWName
			]);

	def dev = DeviceManager.getSpecificDevice( HWName)
	def x =0;
	
	def straif=0;
	def rz=0;
	
	IJInputEventListener listener = new IJInputEventListener() {
		@Override public void onEvent(Component comp, Event event1,float value, String eventString) {
			if(comp.getName().contentEquals("x")){
				straif=-value;System.out.println(comp.getName()+" is value= "+value);
			}
			else if(comp.getName().contentEquals("y")){
				x=-value;System.out.println(comp.getName()+" is value= "+value);
			}
			else if(comp.getName().contentEquals("rz")){
				//ignore
			}
			else if(comp.getName().contentEquals("rx")){
				rz=value;System.out.println(comp.getName()+" is value= "+value);
			}
			else
				System.out.println(comp.getName()+" is value= "+value);
			
		}
	}
	g.clearListeners()
	// gamepad is a BowlerJInputDevice
	g.addListeners(listener);
	// wait while the application is not stopped
	while(!Thread.interrupted() ){
		ThreadUtil.wait(20)
		if(Math.abs(x)>0.01 || Math.abs(straif)>0.01 || Math.abs(rz)>0.01) {
			def newPose = new TransformNR(x*0.2,straif*0.5,0,new RotationNR(0, rz*0.1, 0))
			//println newPose
			cat.DriveArc(newPose, 0.0020);
		}
	}
	//remove listener and exit
	g.removeListeners(listener);
}else{

	println "Error Cant find the SmallKat! "+addresses
}


