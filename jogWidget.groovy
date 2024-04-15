
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
public enum Quadrent {
	first,second,third,fourth
}
Quadrent getQuad(double angle) {
	if(angle>45 && angle<135)
		return Quadrent.first;
	if(angle>135 ||angle < (-135))
		return Quadrent.second;
	if(angle>-135&& angle<-45)
		return Quadrent.third;
	if(angle>-45&&angle<45)
		return Quadrent.fourth;
	throw new RuntimeException("Impossible nummber! "+angle);
}	
try{
	def getCamerFrameGetRotation = BowlerStudio.getCamerFrame().getRotation()
	double currentRotZ = Math.toDegrees(getCamerFrameGetRotation.getRotationAzimuth());
	
	Quadrent quad = getQuad(currentRotZ)
	while(!Thread.interrupted() && run){
		ThreadUtil.wait(100)
		double threshhold = 0.3
		if(Math.abs(rlr)>0||Math.abs(rud)>0) {
			getCamerFrameGetRotation = BowlerStudio.getCamerFrame().getRotation()
			currentRotZ = Math.toDegrees(getCamerFrameGetRotation.getRotationAzimuth());
			double currentEle = Math.toDegrees(getCamerFrameGetRotation.getRotationTilt());
			double elSet= rud
			def stepRotation = widget.rotationIncrement*5
			
			if((currentEle>(180) || currentEle<-90 )&& elSet>0) {
				elSet=0;
			}
			if((currentEle<0&& currentEle>-90)&& elSet<0) {
				elSet=0;
			}
			quad = getQuad(currentRotZ)
			println "Current rotation = "+currentRotZ+" ele = "+currentEle+" elSet "+ elSet
			println quad
			RotationNR rot = new RotationNR(elSet*stepRotation,rlr*stepRotation,0);
			TransformNR tf =new TransformNR(0,0,0,rot)

			BowlerStudio.moveCamera(tf)
		}
		if(Math.abs(zoom)>0) {
			BowlerStudio.zoomCamera(zoom*50)
		}
		TransformNR stateUnitVector = new TransformNR();
		if(lud>threshhold)
			stateUnitVector.translateX(1);
		if(lud<-threshhold)
			stateUnitVector.translateX(-1);
		if(lrl>threshhold)
			stateUnitVector.translateY(1);
		if(lrl<-threshhold)
			stateUnitVector.translateY(-1);
		if(trig>threshhold)
			stateUnitVector.translateZ(1);
		if(trig<-threshhold)
			stateUnitVector.translateZ(-1);
		TransformNR orentationOffset = new TransformNR(0,0,0,new RotationNR(0,currentRotZ-90,0))
		TransformNR frame = BowlerStudio. getTargetFrame() ;
		TransformNR frameOffset = new TransformNR(0,0,0,frame.getRotation())
		stateUnitVector=frameOffset.times( orentationOffset.times(stateUnitVector))
		double bound =0.5;
		if(rotation) {
			if(stateUnitVector.getX()>bound)
				widget.tilt.jogPlusOne()
			if(stateUnitVector.getX()<-bound)
				widget.tilt.jogMinusOne()
			if(stateUnitVector.getY()>bound)
				widget.elevation.jogPlusOne()
			if(stateUnitVector.getY()<-bound)
				widget.elevation.jogMinusOne()
			if(stateUnitVector.getZ()>bound)
				widget.azimuth.jogPlusOne()
			if(stateUnitVector.getZ()<-bound)
				widget.azimuth.jogMinusOne()
		}else {
			if(stateUnitVector.getX()>bound)
				widget.tx.jogPlusOne()
			if(stateUnitVector.getX()<-bound)
				widget.tx.jogMinusOne()
			if(stateUnitVector.getY()>bound)
				widget.ty.jogPlusOne()
			if(stateUnitVector.getY()<-bound)
				widget.ty.jogMinusOne()
			if(stateUnitVector.getZ()>bound)
				widget.tz.jogPlusOne()
			if(stateUnitVector.getZ()<-bound)
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


