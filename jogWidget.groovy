import org.apache.commons.math3.geometry.euclidean.threed.Rotation

import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase
import com.neuronrobotics.bowlerstudio.creature.TransformWidget
import com.neuronrobotics.sdk.addons.gamepad.BowlerJInputDevice
import com.neuronrobotics.sdk.addons.gamepad.IGameControlEvent
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR
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
double trig=0;
double zoom=0;
boolean rotation=false;
boolean run=true;
long timeOfLast = System.currentTimeMillis()
widget.setMode("X for Translation, Y for Rotation");
IGameControlEvent listener = { name, value->
	switch(name) {
		case "pov-up-down":
			zoom=value;
			break;
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
		case"r-trig-button":
			trig=value
			break;
		case"l-trig-button":
			trig=-value;
			break;
		default:
			println "Unmapped "+name+" "+value
	}
}
g.clearListeners()
// gamepad is a BowlerJInputDevice
g.addListeners(listener);

try{
	while(!Thread.interrupted() && run){
		ThreadUtil.wait(100)
		double bound = 0.3
		if(Math.abs(rlr)>0||Math.abs(rud)>0) {
			TransformNR current = BowlerStudio.getCamerFrame();
			double currentRotZ = Math.toDegrees(current.getRotation().getRotationAzimuth());
			println "Current rotation = "+currentRotZ
			RotationNR rot = new RotationNR(rud*widget.rotationIncrement*5,rlr*widget.rotationIncrement*5,0);
			TransformNR tf =new TransformNR(0,0,0,rot)

			BowlerStudio.moveCamera(tf)
		}
		if(Math.abs(zoom)>0) {
			BowlerStudio.zoomCamera(zoom*50)
		}
		if(rotation) {
			if(lud>bound)
				widget.tilt.jogPlusOne()
			if(lud<-bound)
				widget.tilt.jogMinusOne()
			if(lrl>bound)
				widget.elevation.jogPlusOne()
			if(lrl<-bound)
				widget.elevation.jogMinusOne()
			if(trig>bound)
				widget.azimuth.jogPlusOne()
			if(trig<-bound)
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
			if(trig>bound)
				widget.tz.jogPlusOne()
			if(trig<-bound)
				widget.tz.jogMinusOne()
		}
	}
}catch(Throwable t){
	if(!RuntimeException.class.isInstance(t))
		t.printStackTrace()
}
println "Clean exit from jogWidget.groovy in Katapult"
//remove listener and exit
g.removeListeners(listener);


