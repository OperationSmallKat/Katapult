
import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase
import com.neuronrobotics.bowlerstudio.creature.EngineeringUnitsSliderWidget
import com.neuronrobotics.bowlerstudio.creature.TransformWidget
import com.neuronrobotics.sdk.addons.gamepad.BowlerJInputDevice
import com.neuronrobotics.sdk.addons.gamepad.IGameControlEvent
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR
import com.neuronrobotics.sdk.common.DeviceManager
import com.neuronrobotics.sdk.util.ThreadUtil

import javafx.event.ActionEvent

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
	while(!Thread.interrupted() && run ){
		Thread.sleep(100)
		double threshhold = 0.3
		getCamerFrameGetRotation = BowlerStudio.getCamerFrame().getRotation()
		currentRotZ = Math.toDegrees(getCamerFrameGetRotation.getRotationAzimuth());
		if(Math.abs(rlr)>0||Math.abs(rud)>0) {
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
			//println "Current rotation = "+currentRotZ+" ele = "+currentEle+" elSet "+ elSet
			//println quad
			RotationNR rot = new RotationNR(elSet*stepRotation,rlr*stepRotation,0);
			TransformNR tf =new TransformNR(0,0,0,rot)

			BowlerStudio.moveCamera(tf)
		}
		if(Math.abs(zoom)>0) {
			BowlerStudio.zoomCamera(zoom*50)
		}
		TransformNR stateUnitVectorTmp = new TransformNR();
		
		if(lud>threshhold)
			stateUnitVectorTmp.translateX(1);
		if(lud<-threshhold)
			stateUnitVectorTmp.translateX(-1);
		if(lrl>threshhold)
			stateUnitVectorTmp.translateY(1);
		if(lrl<-threshhold)
			stateUnitVectorTmp.translateY(-1);
		if(trig>threshhold)
			stateUnitVectorTmp.translateZ(1);
		if(trig<-threshhold)
			stateUnitVectorTmp.translateZ(-1);
		TransformNR orentationOffset = new TransformNR(0,0,0,new RotationNR(0,currentRotZ-90,0))
		TransformNR frame = BowlerStudio. getTargetFrame() ;
		TransformNR frameOffset = new TransformNR(0,0,0,frame.getRotation())
		TransformNR stateUnitVector = new TransformNR();
		
		double bound =0.5;
		if(rotation) {
			stateUnitVector= orentationOffset.times(stateUnitVectorTmp)
			double incement = widget.rotationIncrement;
			double eleUpdate = 0;
			double tiltUpdate = 0;
			double azUpdate = 0;
			boolean updateTrig = false;
			if(stateUnitVector.getY()<-bound) {
				tiltUpdate+=incement
				updateTrig=true
			}
			if(stateUnitVector.getY()>bound) {
				tiltUpdate-=incement
				updateTrig=true
				
			}
			if(stateUnitVector.getX()<-bound) {
				eleUpdate-=incement;
				updateTrig=true
				
			}
			if(stateUnitVector.getX()>bound) {
				eleUpdate+=incement
				updateTrig=true
				
			}
			if(stateUnitVector.getZ()<-bound) { 
				azUpdate+=incement
				updateTrig=true
				
			}
			if(stateUnitVector.getZ()>bound) {
				azUpdate-=incement
				updateTrig=true
				
			}
			if(!updateTrig)
				continue;
			TransformNR update = new TransformNR(0,0,0,new RotationNR(0,0,eleUpdate))
			update = update.times(new TransformNR(0,0,0,new RotationNR(tiltUpdate,0,0)))
			update = update.times(new TransformNR(0,0,0,new RotationNR(0,azUpdate,0)))
			TransformNR current = widget.getCurrent();
			TransformNR currentRotation = new TransformNR(0,0,0,current.getRotation())
			TransformNR updatedRotation = current.times( 
				currentRotation.inverse().times( 
						frameOffset.inverse().times(update).times(frameOffset)
						.times(currentRotation)
					)
				)
			double azUpdate2 = Math.toDegrees(updatedRotation.getRotation().getRotationAzimuth())
			double  tiltUpdate2 = Math.toDegrees(updatedRotation.getRotation().getRotationTilt())
			double eleUpdate2 = Math.toDegrees(updatedRotation.getRotation().getRotationElevation())
			azUpdate2 = roundToNearist(azUpdate2,incement)
			tiltUpdate2 = roundToNearist(tiltUpdate2,incement)
			eleUpdate2 = roundToNearist(eleUpdate2,incement)
			RotationNR bounded = new RotationNR(tiltUpdate2,azUpdate2,eleUpdate2)
			current.setRotation(bounded)
			//println"\n\n"
			//println update.toSimpleString()
			//println current.toSimpleString()
			widget.updatePose(current)
			widget.handle(null);
		}else {
			double incement = widget.linearIncrement;
			stateUnitVector= orentationOffset.times(stateUnitVectorTmp)
			stateUnitVector.setRotation(new RotationNR())
			boolean updateTrig = false;
			if(stateUnitVector.getX()>bound)
				updateTrig=true
			if(stateUnitVector.getX()<-bound)
				updateTrig=true
			if(stateUnitVector.getY()>bound)
				updateTrig=true
			if(stateUnitVector.getY()<-bound)
				updateTrig=true
			if(stateUnitVector.getZ()>bound)
				updateTrig=true
			if(stateUnitVector.getZ()<-bound)
				updateTrig=true
			if(!updateTrig)
				continue;
			stateUnitVector=new TransformNR(
				roundToNearist(stateUnitVector.getX(),incement),
				roundToNearist(stateUnitVector.getY(),incement),
				roundToNearist(stateUnitVector.getZ(),incement))
			TransformNR current = widget.getCurrent();
			TransformNR currentRotation = new TransformNR(0,0,0,current.getRotation())
			TransformNR tf= current.times(	frameOffset.inverse().times(stateUnitVector).times(frameOffset))
			//println "\n\n"
			//println tf.toSimpleString()
			widget.updatePose(tf)
			widget.handle(null);
		}
	}
}catch(Throwable t){
	//if(!InterruptedException.class.isInstance(t))
		t.printStackTrace()
}
double roundToNearist(double incoiming, double modulo) {
	return modulo*(Math.round(incoiming/modulo))
}
void wrapintPlusOne(EngineeringUnitsSliderWidget wid,double increment) {
	if(wid.getValue()+increment>wid.setpoint.getMax()) {
		wid.setValue(wid.setpoint.getMin());
	}else
		wid.jogPlusOne()
}
void wrapintMinusOne(EngineeringUnitsSliderWidget wid,double increment) {
	if(wid.getValue()-increment<wid.setpoint.getMin()) {
		wid.setValue(wid.setpoint.getMax());
	}else
		wid.jogMinusOne()
}
println "Clean exit from jogWidget.groovy in Katapult contains="+g.listeners.contains(listener)+" run="+run
//remove listener and exit
g.removeListeners(listener);


