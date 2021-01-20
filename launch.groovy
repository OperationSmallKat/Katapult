import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics
import com.neuronrobotics.sdk.addons.kinematics.MobileBase
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR
import com.neuronrobotics.sdk.common.DeviceManager
import com.neuronrobotics.sdk.util.ThreadUtil
import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine
import com.neuronrobotics.sdk.addons.gamepad.BowlerJInputDevice;
import com.neuronrobotics.sdk.addons.gamepad.IGameControlEvent

import net.java.games.input.Component;
import net.java.games.input.Event;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;

ScriptingEngine.pull("https://github.com/CommonWealthRobotics/DeviceProviders.git");
ScriptingEngine.gitScriptRun("https://github.com/CommonWealthRobotics/DeviceProviders.git",
		"loadAll.groovy", null);

MobileBase cat =DeviceManager.getSpecificDevice("Luna", {
		return ScriptingEngine.gitScriptRun(	"https://github.com/OperationSmallKat/Luna.git",
	"MediumKat.xml",null);
	})
//Check if the device already exists in the device Manager
def g=DeviceManager.getSpecificDevice("gamepad",{
	def t = new BowlerJInputDevice("Dragon","X-Box","Game"); //
	t.connect(); // Connect to it.
	return t
})

HashMap<DHParameterKinematics,TransformNR > getTipLocations(def base){
	def legs = base.getLegs()
	def tipList = new HashMap<DHParameterKinematics,TransformNR >()
	for(DHParameterKinematics leg:legs){
		// Read the location of the foot before moving the body
		def home =leg.getCurrentTaskSpaceTransform()
		tipList.put(leg,home)
	}
	return tipList
}

void pose(def newAbsolutePose, MobileBase base, def tipList){
	def legs = base.getLegs()
	try{

		def imuCenter = base.getIMUFromCentroid()
		def newPoseTransformedToIMUCenter = newAbsolutePose.times(imuCenter.inverse())
		def newPoseAdjustedBacktoRobotCenterFrame = imuCenter.times(newPoseTransformedToIMUCenter)
		def previous = base.getFiducialToGlobalTransform();
		// Perform a pose opperation
		base.setGlobalToFiducialTransform(newPoseAdjustedBacktoRobotCenterFrame)
		
		for(def leg:legs){
			def pose =tipList.get(leg)
			if(leg.checkTaskSpaceTransform(pose))// move the leg only is the pose of hte limb is possible
				leg.setDesiredTaskSpaceTransform(pose, 0);// set leg to the location of where the foot was
			else{
				base.setGlobalToFiducialTransform(previous)
				for(def l:legs){
					def p =tipList.get(l)
					l.setDesiredTaskSpaceTransform(p, 0);// set leg to the location of where the foot was
				}
				return;
			}
		}
	}catch (Throwable t){
		BowlerStudio.printStackTrace(t)
	}
}


def x =0;

def straif=0;
def rz=0;
def ljud =0;
def walkMode = true
def startPose
def tips
IGameControlEvent listener = new IGameControlEvent() {
	@Override public void onEvent(String name,float value) {
		
		if(name.contentEquals("l-joy-left-right")){
			straif=-value;
		}
		else if(name.contentEquals("r-joy-up-down")){
			x=-value;
		}
		else if(name.contentEquals("l-joy-up-down")){
			ljud=value;
		}
		else if(name.contentEquals("r-joy-left-right")){
			rz=value;
		}
		else if(name.contentEquals("x-mode")){
			if(value>0) {
				if(!walkMode) {
					pose(new TransformNR(),cat,tips)
				}
				walkMode=true;
			}
		}
		else if(name.contentEquals("y-mode")){
			if(value>0) {
				walkMode=false;
				startPose = cat.getFiducialToGlobalTransform()
				
				tips = getTipLocations( cat)
			}
		}
		//	System.out.println(name+" is value= "+value);
		
	}
}
g.clearListeners()
// gamepad is a BowlerJInputDevice
g.addListeners(listener);
// wait while the application is not stopped
try{
	while(!Thread.interrupted() ){
		ThreadUtil.wait(30)
		if(Math.abs(x)>0.001 || Math.abs(straif)>0.001 || Math.abs(rz)>0.001 || Math.abs(ljud)>0.001) {
			if(walkMode) {
				def newPose = new TransformNR(x*0.1,straif*0.1,0,new RotationNR(0, rz*0.05, 0))
				//println newPose
				cat.DriveArc(newPose, 0.0020);
			}else {
				// pose mode
				pose(new TransformNR(0,-10*rz,10*x,new RotationNR(0,10*straif,5*ljud)),cat,tips)
			}
		}
	}
}catch(Throwable t){
	t.printStackTrace()
}
//remove listener and exit
g.removeListeners(listener);


