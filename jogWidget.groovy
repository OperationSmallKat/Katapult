import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase
import com.neuronrobotics.bowlerstudio.creature.TransformWidget
import com.neuronrobotics.sdk.addons.gamepad.BowlerJInputDevice
import com.neuronrobotics.sdk.addons.gamepad.IGameControlEvent
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR
import com.neuronrobotics.sdk.common.DeviceManager
import com.neuronrobotics.sdk.util.ThreadUtil

TransformWidget widget = args[0]

println widget
def gameControllerNames = ConfigurationDatabase.getObject("katapult", "gameControllerNames", [
	"Dragon",
	"X-Box",
	"Game",
	"XBOX",
	"Microsoft",
	"GPD"
])

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

println "Starting Jog loop with \n"+g

double lud=0;
double lrl=0;
double rud=0;
double rlr=0;
boolean rotation=false;
boolean run=true;
long timeOfLast = System.currentTimeMillis()
widget.setMode("X for Translation, Y for Rotation");
IGameControlEvent listener = { name, value->
	switch(name) {
		case "l-joy-up-down":
			lud=-value;
			break;
		case "l-joy-left-right":
			lrl=-value;
			break;
		case "r-joy-up-down":
			rud=-value;
			break;
		case "r-joy-left-right":
			rlr=-value;
			break;
		case "y-mode":
			if(value>0.1) {
				rotation=true
				widget.setMode("Rotation");
			}
			break;
		case "x-mode":
			if(value>0.1) {
				rotation=false
				widget.setMode("Translation");
			}
			break;
		case "a-mode":
			if(value>0.5) {
				println "Exiting because A button pressed"
				run=false
			}
			break;
	}
}
g.clearListeners()
// gamepad is a BowlerJInputDevice
g.addListeners(listener);

try{
	while(!Thread.interrupted() && run){
		ThreadUtil.wait(100)
		double bound = 0.3
		if(rotation) {
			if(lud>bound)
				widget.elevation.jogPlusOne()
			if(lud<-bound)
				widget.elevation.jogMinusOne()
			if(lrl>bound)
				widget.tilt.jogMinusOne()
			if(lrl<-bound)
				widget.tilt.jogPlusOne()
			if(rlr>bound)
				widget.azimuth.jogPlusOne()
			if(rlr<-bound)
				widget.azimuth.jogMinusOne()
		}else {
			if(lud>bound)
				widget.tx.jogPlusOne()
			if(lud<-bound)
				widget.tx.jogMinusOne()
			if(lrl>bound)
				widget.ty.jogPlusOne()
			if(lrl<-bound)
				widget.ty.jogMinusOne()
			if(rud>bound)
				widget.tz.jogPlusOne()
			if(rud<-bound)
				widget.tz.jogMinusOne()
		}
	}
}catch(Throwable t){
	if(RuntimeException.class.isInstance(t))
		return;
	t.printStackTrace()
}
//remove listener and exit
g.removeListeners(listener);


