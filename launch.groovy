import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics
import com.neuronrobotics.sdk.addons.kinematics.MobileBase
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR
import com.neuronrobotics.sdk.common.DeviceManager
import com.neuronrobotics.sdk.util.ThreadUtil
import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine
import com.neuronrobotics.sdk.addons.gamepad.BowlerJInputDevice;
import com.neuronrobotics.sdk.addons.gamepad.IGameControlEvent

//ScriptingEngine.pull("https://github.com/CommonWealthRobotics/DeviceProviders.git");
//ScriptingEngine.gitScriptRun("https://github.com/CommonWealthRobotics/DeviceProviders.git",
//		"loadAll.groovy", null);

String robotName = ConfigurationDatabase.getObject("katapult", "robotName", "Luna")
String robotGit = ConfigurationDatabase.getObject("katapult", "robotGit", "https://github.com/OperationSmallKat/Luna.git")
String robotGitFile = ConfigurationDatabase.getObject("katapult", "robotGitFile", "MediumKat.xml")
String linkDeviceName = ConfigurationDatabase.getObject("katapult", "linkDeviceName", "midnight")
def gameControllerNames = ConfigurationDatabase.getObject("katapult", "gameControllerNames", [
	"Dragon",
	"X-Box",
	"Game",
	"XBOX",
	"Microsoft"
])

MobileBase cat =DeviceManager.getSpecificDevice(robotName, {
	return ScriptingEngine.gitScriptRun(	robotGit,
			robotGitFile,null);
})
def device = DeviceManager.getSpecificDevice(linkDeviceName)
if(device.simple.isVirtual()) {
	println "SmallKat Device is virtual"
	//return;
}
BowlerJInputDevice g
List<String> alreadyConnected = DeviceManager.listConnectedDevice(BowlerJInputDevice.class)
if(alreadyConnected.size()==0) {
	try {
		//Check if the device already exists in the device Manager
		g=DeviceManager.getSpecificDevice("gamepad",{
			def t = new BowlerJInputDevice(gameControllerNames); //
			t.connect(); // Connect to it.
			return t
		})
	}catch(Throwable t) {
		println "No game controllers found, Searched:\n"+gameControllerNames
		return;
	}
}else {
	def name =alreadyConnected.get(0)
	g=DeviceManager.getSpecificDevice(name)
	if(!gameControllerNames.contains(name)) {
		def newList=[]
		newList.addAll(gameControllerNames)
		newList.add(name)
		ConfigurationDatabase.setObject("katapult", "gameControllerNames", newList)
		ConfigurationDatabase.save();
	}
}



def x =0;

def straif=0;
def rz=0;
def ljud =0;
def walkMode = true
def startPose
def tips
long timeOfLast = System.currentTimeMillis()

IGameControlEvent listener = new IGameControlEvent() {
			@Override public void onEvent(String name,float value) {
				timeOfLast = System.currentTimeMillis()

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
							cat.pose(new TransformNR(),cat.getIMUFromCentroid(),tips)
						}
						walkMode=true;
					}
				}
				else if(name.contentEquals("y-mode")){
					if(value>0) {
						walkMode=false;
						startPose = cat.getFiducialToGlobalTransform()

						tips = cat.getTipLocations()
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
	while(!Thread.interrupted() && cat.isAvailable()){
		ThreadUtil.wait(30)
		if(walkMode) {
			if(Math.abs(x)>0.001 || Math.abs(straif)>0.001 || Math.abs(rz)>0.001 || Math.abs(ljud)>0.001) {

				def newPose = new TransformNR(x*0.4,straif*0.2,0,new RotationNR(0, rz*0.2, 0))
				//println newPose
				cat.DriveArc(newPose, 0.0020);

			}
		}else {
			// pose mode
			def newPose = new TransformNR(0,-10*rz,10*x,new RotationNR(0,10*straif,5*ljud))
			def around = cat.getIMUFromCentroid()
			cat.pose(newPose,around,tips)
		}
	}
}catch(Throwable t){
	t.printStackTrace()
}
//remove listener and exit
g.removeListeners(listener);


